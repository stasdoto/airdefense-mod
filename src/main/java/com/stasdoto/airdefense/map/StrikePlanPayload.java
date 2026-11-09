package com.stasdoto.airdefense.map;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * 2.0: a massed strike planned on the tablet's map, launched at once: for each launcher (lite: by its entity id) the
 * point it fires at and how many rounds (0 = its usual salvo).
 */
public record StrikePlanPayload(List<Shot> shots) implements CustomPacketPayload {
	public record Shot(int vehicle, int x, int z, int salvo) {
	}

	/** Most launchers in one plan. */
	public static final int MAX_SHOTS = 64;
	/** Most rounds one launcher is asked for. */
	public static final int MAX_ROUNDS = 32;

	public static final Type<StrikePlanPayload> TYPE = new Type<>(AirDefense.id("strike_plan"));
	public static final StreamCodec<ByteBuf, StrikePlanPayload> CODEC = new StreamCodec<>() {
		@Override
		public StrikePlanPayload decode(ByteBuf b) {
			int n = Math.min(ByteBufCodecs.VAR_INT.decode(b), MAX_SHOTS);
			List<Shot> shots = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				shots.add(new Shot(ByteBufCodecs.VAR_INT.decode(b), b.readInt(), b.readInt(), ByteBufCodecs.VAR_INT.decode(b)));
			}
			return new StrikePlanPayload(shots);
		}

		@Override
		public void encode(ByteBuf b, StrikePlanPayload p) {
			int n = Math.min(p.shots.size(), MAX_SHOTS);
			ByteBufCodecs.VAR_INT.encode(b, n);
			for (int i = 0; i < n; i++) {
				Shot s = p.shots.get(i);
				ByteBufCodecs.VAR_INT.encode(b, Math.max(0, s.vehicle));
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
