package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Server to client, with every map refresh: villages (with their owners' colours) and soldiers around the player. */
public record NationMapPayload(List<Village> villages, List<Man> men) implements CustomPacketPayload {
	/**
	 * @param buildings two numbers per building: (type << 4) | (facing << 1) | done, and its door's offset from the
	 *                  village square as (dx << 16) | (dz & 0xFFFF)
	 */
	public record Village(int id, String name, int x, int y, int z, int color, String country, boolean mine, int population, int guards,
			int soldiers, int fx, int fz, boolean war, List<Integer> buildings, int radius, int half, boolean capital) {
	}

	public record Man(int id, int x, int z, int role, int color, boolean mine, int home) {
	}

	public static final Type<NationMapPayload> TYPE = new Type<>(AirDefense.id("nation_map"));
	private static final StreamCodec<ByteBuf, List<Integer>> INTS = ByteBufCodecs.INT.apply(ByteBufCodecs.list(1024));
	public static final StreamCodec<ByteBuf, NationMapPayload> CODEC = new StreamCodec<>() {
		@Override
		public NationMapPayload decode(ByteBuf b) {
			int n = ByteBufCodecs.VAR_INT.decode(b);
			List<Village> v = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				v.add(new Village(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), b.readInt(), b.readInt(), b.readInt(),
						b.readInt(), ByteBufCodecs.STRING_UTF8.decode(b), b.readBoolean(), ByteBufCodecs.VAR_INT.decode(b),
						ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), b.readInt(), b.readInt(), b.readBoolean(), INTS.decode(b), ByteBufCodecs.VAR_INT.decode(b),
						ByteBufCodecs.VAR_INT.decode(b), b.readBoolean()));
			}
			int m = ByteBufCodecs.VAR_INT.decode(b);
			List<Man> men = new ArrayList<>();
			for (int i = 0; i < m; i++) {
				men.add(new Man(ByteBufCodecs.VAR_INT.decode(b), b.readInt(), b.readInt(), b.readByte(), b.readInt(), b.readBoolean(),
						ByteBufCodecs.VAR_INT.decode(b) - 1));
			}
			return new NationMapPayload(v, men);
		}

		@Override
		public void encode(ByteBuf b, NationMapPayload p) {
			ByteBufCodecs.VAR_INT.encode(b, p.villages.size());
			for (Village v : p.villages) {
				ByteBufCodecs.VAR_INT.encode(b, v.id);
				ByteBufCodecs.STRING_UTF8.encode(b, v.name);
				b.writeInt(v.x);
				b.writeInt(v.y);
				b.writeInt(v.z);
				b.writeInt(v.color);
				ByteBufCodecs.STRING_UTF8.encode(b, v.country);
				b.writeBoolean(v.mine);
				ByteBufCodecs.VAR_INT.encode(b, v.population);
				ByteBufCodecs.VAR_INT.encode(b, v.guards);
				ByteBufCodecs.VAR_INT.encode(b, v.soldiers);
				b.writeInt(v.fx);
				b.writeInt(v.fz);
				b.writeBoolean(v.war);
				INTS.encode(b, v.buildings);
				ByteBufCodecs.VAR_INT.encode(b, v.radius);
				ByteBufCodecs.VAR_INT.encode(b, v.half);
				b.writeBoolean(v.capital);
			}
			ByteBufCodecs.VAR_INT.encode(b, p.men.size());
			for (Man m : p.men) {
				ByteBufCodecs.VAR_INT.encode(b, m.id);
				b.writeInt(m.x);
				b.writeInt(m.z);
				b.writeByte(m.role);
				b.writeInt(m.color);
				b.writeBoolean(m.mine);
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, m.home + 1));
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
