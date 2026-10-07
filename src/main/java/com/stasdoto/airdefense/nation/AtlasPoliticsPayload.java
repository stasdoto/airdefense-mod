package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * Who holds what on the atlas (1.25): every country (name, colour, yours or at war with you) and every town in the
 * atlas with its country - the borders are drawn from this and the towns' places (see Territory).
 */
public record AtlasPoliticsPayload(List<Country> countries, List<Town> towns) implements CustomPacketPayload {
	public record Country(int id, String name, int argb, boolean mine, boolean war, int capital) {
	}

	/** kind: 0 = city, 1 = hamlet, 2 = village; key = the city's or hamlet's plan key (0 for a village). */
	public record Town(int id, String name, int country, int kind, long key, int x, int z, int people, boolean capital) {
	}

	public static final Type<AtlasPoliticsPayload> TYPE = new Type<>(AirDefense.id("atlas_politics"));
	public static final StreamCodec<ByteBuf, AtlasPoliticsPayload> CODEC = new StreamCodec<>() {
		@Override
		public AtlasPoliticsPayload decode(ByteBuf b) {
			int n = ByteBufCodecs.VAR_INT.decode(b);
			List<Country> cs = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				cs.add(new Country(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), b.readInt(), b.readBoolean(), b.readBoolean(),
						ByteBufCodecs.VAR_INT.decode(b)));
			}
			int m = ByteBufCodecs.VAR_INT.decode(b);
			List<Town> ts = new ArrayList<>();
			for (int i = 0; i < m; i++) {
				ts.add(new Town(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), b.readInt(), b.readByte(), b.readLong(), b.readInt(),
						b.readInt(), ByteBufCodecs.VAR_INT.decode(b), b.readBoolean()));
			}
			return new AtlasPoliticsPayload(cs, ts);
		}

		@Override
		public void encode(ByteBuf b, AtlasPoliticsPayload p) {
			ByteBufCodecs.VAR_INT.encode(b, p.countries.size());
			for (Country c : p.countries) {
				ByteBufCodecs.VAR_INT.encode(b, c.id);
				ByteBufCodecs.STRING_UTF8.encode(b, c.name);
				b.writeInt(c.argb);
				b.writeBoolean(c.mine);
				b.writeBoolean(c.war);
				ByteBufCodecs.VAR_INT.encode(b, c.capital);
			}
			ByteBufCodecs.VAR_INT.encode(b, p.towns.size());
			for (Town t : p.towns) {
				ByteBufCodecs.VAR_INT.encode(b, t.id);
				ByteBufCodecs.STRING_UTF8.encode(b, t.name);
				b.writeInt(t.country);
				b.writeByte(t.kind);
				b.writeLong(t.key);
				b.writeInt(t.x);
				b.writeInt(t.z);
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, t.people));
				b.writeBoolean(t.capital);
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
