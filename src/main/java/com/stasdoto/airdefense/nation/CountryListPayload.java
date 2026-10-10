package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.43: the countries a player can play for (the choice when he first comes into a world, and later from the key):
 * each with its capital, its towns and people, its ruler, who plays for it already and whom it fights.
 *
 * @param first     the first time (on coming into the world): the screen opens by itself
 * @param current   the country he plays for now (-1: none)
 * @param spawnX    where the world's spawn is (the map starts there)
 */
public record CountryListPayload(boolean first, int current, int spawnX, int spawnZ, List<Entry> countries) implements CustomPacketPayload {
	/**
	 * One country. ruler: title/name/number/trait as in {@link Diplomacy} (title -1: run by players); style: a
	 * {@link CityStyle} ordinal.
	 */
	public record Entry(int id, String name, int argb, String capital, int capX, int capZ, int towns, int people, int style, int rulerTitle,
			int rulerName, int rulerNumber, int rulerTrait, List<String> players, List<String> wars, int allies) {
	}

	public static final Type<CountryListPayload> TYPE = new Type<>(AirDefense.id("country_list"));
	public static final StreamCodec<ByteBuf, CountryListPayload> CODEC = new StreamCodec<>() {
		@Override
		public CountryListPayload decode(ByteBuf b) {
			boolean first = b.readBoolean();
			int current = b.readInt();
			int sx = b.readInt();
			int sz = b.readInt();
			int n = ByteBufCodecs.VAR_INT.decode(b);
			List<Entry> list = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				int id = ByteBufCodecs.VAR_INT.decode(b);
				String name = ByteBufCodecs.STRING_UTF8.decode(b);
				int argb = b.readInt();
				String cap = ByteBufCodecs.STRING_UTF8.decode(b);
				int cx = b.readInt();
				int cz = b.readInt();
				int towns = ByteBufCodecs.VAR_INT.decode(b);
				int people = ByteBufCodecs.VAR_INT.decode(b);
				int style = b.readByte();
				int rt = b.readByte();
				int rn = b.readByte();
				int rnum = b.readByte();
				int rtr = b.readByte();
				List<String> players = strings(b);
				List<String> wars = strings(b);
				int allies = b.readByte();
				list.add(new Entry(id, name, argb, cap, cx, cz, towns, people, style, rt, rn, rnum, rtr, players, wars, allies));
			}
			return new CountryListPayload(first, current, sx, sz, list);
		}

		@Override
		public void encode(ByteBuf b, CountryListPayload p) {
			b.writeBoolean(p.first);
			b.writeInt(p.current);
			b.writeInt(p.spawnX);
			b.writeInt(p.spawnZ);
			ByteBufCodecs.VAR_INT.encode(b, p.countries.size());
			for (Entry e : p.countries) {
				ByteBufCodecs.VAR_INT.encode(b, e.id);
				ByteBufCodecs.STRING_UTF8.encode(b, e.name);
				b.writeInt(e.argb);
				ByteBufCodecs.STRING_UTF8.encode(b, e.capital);
				b.writeInt(e.capX);
				b.writeInt(e.capZ);
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, e.towns));
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, e.people));
				b.writeByte(e.style);
				b.writeByte(e.rulerTitle);
				b.writeByte(e.rulerName);
				b.writeByte(e.rulerNumber);
				b.writeByte(e.rulerTrait);
				strings(b, e.players);
				strings(b, e.wars);
				b.writeByte(e.allies);
			}
		}
	};

	private static List<String> strings(ByteBuf b) {
		int n = ByteBufCodecs.VAR_INT.decode(b);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(ByteBufCodecs.STRING_UTF8.decode(b));
		}
		return out;
	}

	private static void strings(ByteBuf b, List<String> list) {
		ByteBufCodecs.VAR_INT.encode(b, list.size());
		for (String s : list) {
			ByteBufCodecs.STRING_UTF8.encode(b, s);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
