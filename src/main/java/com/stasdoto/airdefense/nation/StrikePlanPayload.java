package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * 2.0: a massed strike planned on the map, launched at once: for each launcher of the commanded country (its town and
 * its place in the town's arsenal) the point it fires at and how many rounds (0 = its usual salvo).
 */
public record StrikePlanPayload(int country, List<Shot> shots) implements CustomPacketPayload {
	public record Shot(int town, int index, int x, int z, int salvo) {
	}

	/** Most launchers in one plan. */
	public static final int MAX_SHOTS = 64;

	public static final Type<StrikePlanPayload> TYPE = new Type<>(AirDefense.id("strike_plan"));
	public static final StreamCodec<ByteBuf, StrikePlanPayload> CODEC = new StreamCodec<>() {
		@Override
		public StrikePlanPayload decode(ByteBuf b) {
			int country = b.readInt();
			int n = Math.min(ByteBufCodecs.VAR_INT.decode(b), MAX_SHOTS);
			List<Shot> shots = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				shots.add(new Shot(b.readInt(), ByteBufCodecs.VAR_INT.decode(b), b.readInt(), b.readInt(), ByteBufCodecs.VAR_INT.decode(b)));
			}
			return new StrikePlanPayload(country, shots);
		}

		@Override
		public void encode(ByteBuf b, StrikePlanPayload p) {
			b.writeInt(p.country);
			int n = Math.min(p.shots.size(), MAX_SHOTS);
			ByteBufCodecs.VAR_INT.encode(b, n);
			for (int i = 0; i < n; i++) {
				Shot s = p.shots.get(i);
				b.writeInt(s.town);
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, s.index));
				b.writeInt(s.x);
				b.writeInt(s.z);
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, s.salvo));
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
