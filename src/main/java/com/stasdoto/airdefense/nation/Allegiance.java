package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.43 "Choosing a country": when a player first comes into a world he chooses the country he plays for - one of the
 * world's own (he becomes its ruler; a friend who chooses the same one plays for it with him) or none at all (the old
 * way: his own country comes with his first town). He can go to its capital at once. Later the key opens the choice
 * again (to change sides).
 */
public final class Allegiance {
	/** Players the screen was sent to this session (so it is not sent again each second). */
	private static final Set<UUID> ASKED = new HashSet<>();
	/** For the tests: choices made. */
	public static int joined;
	/** For the tests: do not open the screen by itself. */
	public static volatile boolean quiet;

	private Allegiance() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(CountryListPayload.TYPE, CountryListPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CountryActionPayload.TYPE, CountryActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CountryActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ASKED.clear());
		ServerTickEvents.END_SERVER_TICK.register(Allegiance::tick);
	}

	/** Once a second: whoever has not chosen yet gets the choice, as soon as the world's countries are all there. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 7 || quiet) {
			return;
		}
		ServerLevel level = server.overworld();
		Politics p = Politics.get(server);
		for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
			UUID u = pl.getUUID();
			if (p.chosen.contains(u) || ASKED.contains(u)) {
				continue;
			}
			// A player of an older world who has a country already, or a world without towns: nothing to choose.
			if (p.countryOwnedBy(u) != null || !CityFeature.enabled) {
				p.chosen.add(u);
				p.setDirty();
				continue;
			}
			if (!Atlas.settled() || pl.tickCount < 40 || !ServerPlayNetworking.canSend(pl, CountryListPayload.TYPE)) {
				continue;
			}
			ASKED.add(u);
			send(level, pl, true);
		}
	}

	/** The countries to choose from: the world's own near the spawn (not the one-town states), and the players'. */
	public static List<Country> choices(ServerLevel level, Politics p) {
		BlockPos spawn = level.getRespawnData().pos();
		List<Country> out = new ArrayList<>();
		for (Country c : p.countries.values()) {
			Settlement cap = p.settlements.get(c.capital);
			if (c.cityState || cap == null) {
				continue;
			}
			out.add(c);
		}
		out.sort(Comparator.comparingDouble(c -> p.settlements.get(c.capital).center.distSqr(spawn)));
		return out.size() > 24 ? new ArrayList<>(out.subList(0, 24)) : out;
	}

	public static void send(ServerLevel level, ServerPlayer player, boolean first) {
		Politics p = Politics.get(level.getServer());
		Diplomacy d = Diplomacy.get(level.getServer());
		Country own = p.countryOwnedBy(player.getUUID());
		List<CountryListPayload.Entry> list = new ArrayList<>();
		for (Country c : choices(level, p)) {
			Settlement cap = p.settlements.get(c.capital);
			int towns = 0;
			int people = 0;
			for (Settlement s : p.settlements.values()) {
				if (s.country == c.id) {
					towns++;
					people += s.people();
				}
			}
			Diplomacy.Ruler r = c.owner == null ? d.ruler(level, c) : null;
			List<String> wars = new ArrayList<>();
			for (int w : c.wars) {
				Country e = p.country(w);
				if (e != null) {
					wars.add(e.name);
				}
			}
			list.add(new CountryListPayload.Entry(c.id, c.name, c.argb(), cap.name, cap.center.getX(), cap.center.getZ(), towns, people, cap.style,
					r == null ? -1 : r.title(), r == null ? 0 : r.name(), r == null ? 0 : r.number(), r == null ? 0 : r.trait(), c.everyoneNames(), wars,
					d.allies(c.id)));
		}
		BlockPos spawn = level.getRespawnData().pos();
		// The borders on the map behind the list.
		Atlas.sendPolitics(level, player, true);
		ServerPlayNetworking.send(player, new CountryListPayload(first, own == null ? -1 : own.id, spawn.getX(), spawn.getZ(), list));
	}

	private static void handle(ServerPlayer player, CountryActionPayload a) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Politics p = Politics.get(level.getServer());
		switch (a.action()) {
			case CountryActionPayload.LIST -> send(level.getServer().overworld(), player, false);
			case CountryActionPayload.LATER -> {
				// Asked again next time he comes in.
			}
			case CountryActionPayload.ALONE -> {
				leave(level, p, player);
				p.chosen.add(player.getUUID());
				p.setDirty();
				player.sendSystemMessage(Component.translatable("nation.airdefense.choice.alone"));
			}
			case CountryActionPayload.JOIN -> {
				Country c = p.country(a.country());
				if (c == null || c.cityState || p.settlements.get(c.capital) == null) {
					return;
				}
				join(level.getServer().overworld(), p, player, c, a.travel());
			}
			default -> {
			}
		}
	}

	/** He plays for this country from now on: its ruler if nobody plays for it yet, else one of its people. */
	public static void join(ServerLevel level, Politics p, ServerPlayer player, Country c, boolean travel) {
		UUID u = player.getUUID();
		String name = player.getName().getString();
		if (!c.isMember(u)) {
			leave(level, p, player);
			List<ServerPlayer> before = Politics.online(level.getServer(), c);
			if (c.owner == null) {
				c.owner = u;
				c.ownerName = name;
			} else {
				c.members.put(u, name);
			}
			// His country's guards no longer remember him as an enemy.
			c.wanted.remove(u);
			for (ServerPlayer other : before) {
				other.sendSystemMessage(Component.translatable("nation.airdefense.choice.friend_joined", name, c.name));
			}
			AirDefense.LOGGER.info("[airdefense] {} plays for {} ({})", name, c.name, c.owner.equals(u) ? "its ruler" : "with " + c.ownerName);
		}
		p.chosen.add(u);
		p.setDirty();
		joined++;
		player.sendSystemMessage(Component.translatable(u.equals(c.owner) ? "nation.airdefense.choice.ruler" : "nation.airdefense.choice.member",
				c.name, c.ownerName));
		Settlement cap = p.settlements.get(c.capital);
		if (travel && cap != null) {
			BlockPos at = landing(level, cap.center);
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, at, 0, 0), true), false);
			player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
			player.sendSystemMessage(Component.translatable("nation.airdefense.choice.arrived", cap.name));
		}
		Atlas.sendPolitics(level, player, true);
	}

	/** He stops playing for his country: if he led it, the next of its players does, or the country goes back to the world. */
	public static void leave(ServerLevel level, Politics p, ServerPlayer player) {
		UUID u = player.getUUID();
		Country c = p.countryOwnedBy(u);
		if (c == null) {
			return;
		}
		c.members.remove(u);
		if (u.equals(c.owner)) {
			if (c.members.isEmpty()) {
				c.owner = null;
				c.ownerName = "";
			} else {
				var first = c.members.entrySet().iterator().next();
				c.owner = first.getKey();
				c.ownerName = first.getValue();
				c.members.remove(first.getKey());
			}
		}
		for (ServerPlayer other : Politics.online(level.getServer(), c)) {
			other.sendSystemMessage(Component.translatable("nation.airdefense.choice.friend_left", player.getName().getString(), c.name));
		}
		p.setDirty();
	}

	/** A dry spot on the ground a few steps from the town's square (the chunk is made if it is not yet). */
	static BlockPos landing(ServerLevel level, BlockPos center) {
		int[][] tries = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}, {7, 0}, {0, 7}, {-7, 0}, {0, -7}, {10, 10}, {0, 0}};
		for (int[] t : tries) {
			int x = center.getX() + t[0];
			int z = center.getZ() + t[1];
			level.getChunk(x >> 4, z >> 4);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);
			if (level.getFluidState(ground).isEmpty() && level.getFluidState(ground.above()).isEmpty() && y > level.getMinY() + 1) {
				return new BlockPos(x, y, z);
			}
		}
		return center.above(2);
	}
}
