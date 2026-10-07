package com.stasdoto.airdefense.siren;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.nation.Politics;
import com.stasdoto.airdefense.nation.Settlement;

/** The tablet's air raid warning page: what the client is shown, and what it can switch. */
public final class SirenNet {
	private SirenNet() {
	}

	/** Client to server: switch something ({@code arg} = a town id or 0/1; {@code pos} = a siren). */
	public record Action(int action, int arg, long pos) implements CustomPacketPayload {
		public static final int REFRESH = 0;
		public static final int ALL = 1;
		public static final int SILENCE = 2;
		public static final int TOWN = 3;
		public static final int TOWN_CLEAR = 4;
		public static final int SIREN_MODE = 5;

		public static final Type<Action> TYPE = new Type<>(AirDefense.id("siren_action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Action::action,
				ByteBufCodecs.VAR_INT, Action::arg,
				ByteBufCodecs.VAR_LONG, Action::pos,
				Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A town on the page: its alert state, how many sirens it has, how far it is. */
	public record Town(int id, String name, int signal, int sirens, int distance, boolean capital) {
	}

	/** A siren on the page. */
	public record Siren(long pos, String town, int mode, int signal, int distance) {
	}

	/** Server to client: the page's contents. */
	public record State(boolean everywhere, List<Town> towns, List<Siren> sirens) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(AirDefense.id("siren_state"));
		public static final StreamCodec<ByteBuf, State> CODEC = new StreamCodec<>() {
			@Override
			public State decode(ByteBuf b) {
				boolean all = b.readBoolean();
				int n = ByteBufCodecs.VAR_INT.decode(b);
				List<Town> towns = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					towns.add(new Town(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), b.readByte(),
							ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), b.readBoolean()));
				}
				int m = ByteBufCodecs.VAR_INT.decode(b);
				List<Siren> sirens = new ArrayList<>();
				for (int i = 0; i < m; i++) {
					sirens.add(new Siren(b.readLong(), ByteBufCodecs.STRING_UTF8.decode(b), b.readByte(), b.readByte(), ByteBufCodecs.VAR_INT.decode(b)));
				}
				return new State(all, towns, sirens);
			}

			@Override
			public void encode(ByteBuf b, State s) {
				b.writeBoolean(s.everywhere);
				ByteBufCodecs.VAR_INT.encode(b, s.towns.size());
				for (Town t : s.towns) {
					ByteBufCodecs.VAR_INT.encode(b, t.id);
					ByteBufCodecs.STRING_UTF8.encode(b, t.name);
					b.writeByte(t.signal);
					ByteBufCodecs.VAR_INT.encode(b, t.sirens);
					ByteBufCodecs.VAR_INT.encode(b, t.distance);
					b.writeBoolean(t.capital);
				}
				ByteBufCodecs.VAR_INT.encode(b, s.sirens.size());
				for (Siren r : s.sirens) {
					b.writeLong(r.pos);
					ByteBufCodecs.STRING_UTF8.encode(b, r.town);
					b.writeByte(r.mode);
					b.writeByte(r.signal);
					ByteBufCodecs.VAR_INT.encode(b, r.distance);
				}
			}
		};

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				Sirens.get(server).tick(server.overworld().getGameTime());
			}
		});
	}

	private static void handle(ServerPlayer player, Action a) {
		if (DesignatorItem.held(player) == null && !player.getAbilities().instabuild) {
			return;
		}
		ServerLevel level = player.level();
		Sirens s = Sirens.get(level.getServer());
		long now = level.getGameTime();
		switch (a.action()) {
			case Action.ALL -> s.alertEverywhere(a.arg() != 0, now);
			case Action.SILENCE -> s.silence();
			case Action.TOWN -> s.alertTown(a.arg(), true, now);
			case Action.TOWN_CLEAR -> s.alertTown(a.arg(), false, now);
			case Action.SIREN_MODE -> {
				BlockPos pos = BlockPos.of(a.pos());
				if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SirenBlockEntity be) {
					be.setMode(Math.floorMod(a.arg(), 3));
				}
			}
			default -> {
			}
		}
		send(player);
	}

	/** The page for this player: every town (nearest first) and the sirens nearest to him. */
	public static void send(ServerPlayer player) {
		ServerLevel level = player.level();
		Sirens s = Sirens.get(level.getServer());
		Politics p = Politics.get(level.getServer());
		long now = level.getGameTime();
		BlockPos at = player.blockPosition();
		// Sirens that are gone (their chunk is loaded and the block is not a siren any more) are forgotten.
		List<Long> gone = new ArrayList<>();
		for (long l : s.known) {
			BlockPos pos = BlockPos.of(l);
			if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof SirenBlock)) {
				gone.add(l);
			}
		}
		gone.forEach(l -> s.forget(BlockPos.of(l)));
		List<Siren> sirens = new ArrayList<>();
		java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
		for (long l : s.known) {
			BlockPos pos = BlockPos.of(l);
			Settlement t = p.settlementAt(pos);
			if (t != null) {
				counts.merge(t.id, 1, Integer::sum);
			}
			int mode = Sirens.MODE_AUTO;
			int signal = s.signalFor(now, t == null ? -1 : t.id, Sirens.MODE_AUTO);
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SirenBlockEntity be) {
				mode = be.mode();
				signal = SirenBlock.Signal.valueOf(level.getBlockState(pos).getValue(SirenBlock.SIGNAL).name()).ordinal();
			}
			sirens.add(new Siren(l, t == null ? "" : t.name, mode, signal, (int) Math.sqrt(pos.distSqr(at))));
		}
		sirens.sort(Comparator.comparingInt(Siren::distance));
		if (sirens.size() > 60) {
			sirens = new ArrayList<>(sirens.subList(0, 60));
		}
		List<Town> towns = new ArrayList<>();
		for (Settlement t : p.settlements.values()) {
			towns.add(new Town(t.id, t.name, s.townSignal(now, t.id), counts.getOrDefault(t.id, 0), (int) Math.sqrt(t.center.distSqr(at)),
					t.isCity()));
		}
		towns.sort(Comparator.comparingInt(Town::distance));
		if (ServerPlayNetworking.canSend(player, State.TYPE)) {
			ServerPlayNetworking.send(player, new State(s.everywhere, towns, sirens));
		}
	}
}
