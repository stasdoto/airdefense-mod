package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * 2.0: command mode for the map - may this player command other countries, which one he commands now (-1 = none), is
 * it free (creative), whom it is at war with, its towns' stores together and its units (type, where, rounds aboard,
 * in the world now).
 */
public record CommandStatePayload(boolean allowed, int country, boolean free, List<Integer> wars, int wood, int stone, int iron,
		List<Unit> units) implements CustomPacketPayload {
	public record Unit(int type, int x, int z, int ammo, boolean present) {
	}

	public static final Type<CommandStatePayload> TYPE = new Type<>(AirDefense.id("command_state"));
	public static final StreamCodec<ByteBuf, CommandStatePayload> CODEC = new StreamCodec<>() {
		@Override
		public CommandStatePayload decode(ByteBuf b) {
			boolean allowed = b.readBoolean();
			int country = b.readInt();
			boolean free = b.readBoolean();
			int n = ByteBufCodecs.VAR_INT.decode(b);
			List<Integer> wars = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				wars.add(b.readInt());
			}
			int wood = ByteBufCodecs.VAR_INT.decode(b);
			int stone = ByteBufCodecs.VAR_INT.decode(b);
			int iron = ByteBufCodecs.VAR_INT.decode(b);
			int m = ByteBufCodecs.VAR_INT.decode(b);
			List<Unit> units = new ArrayList<>();
			for (int i = 0; i < m; i++) {
				units.add(new Unit(ByteBufCodecs.VAR_INT.decode(b), b.readInt(), b.readInt(), ByteBufCodecs.VAR_INT.decode(b), b.readBoolean()));
			}
			return new CommandStatePayload(allowed, country, free, wars, wood, stone, iron, units);
		}

		@Override
		public void encode(ByteBuf b, CommandStatePayload p) {
			b.writeBoolean(p.allowed);
			b.writeInt(p.country);
			b.writeBoolean(p.free);
			ByteBufCodecs.VAR_INT.encode(b, p.wars.size());
			for (int w : p.wars) {
				b.writeInt(w);
			}
			ByteBufCodecs.VAR_INT.encode(b, Math.max(0, p.wood));
			ByteBufCodecs.VAR_INT.encode(b, Math.max(0, p.stone));
			ByteBufCodecs.VAR_INT.encode(b, Math.max(0, p.iron));
			ByteBufCodecs.VAR_INT.encode(b, p.units.size());
			for (Unit u : p.units) {
				ByteBufCodecs.VAR_INT.encode(b, u.type);
				b.writeInt(u.x);
				b.writeInt(u.z);
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, u.ammo));
				b.writeBoolean(u.present);
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
