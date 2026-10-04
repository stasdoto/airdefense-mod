package com.stasdoto.airdefense.fx;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * "Draw this effect here": the server sends only what happened, every client builds the detailed effect itself.
 * {@code a*} is a direction or an end point depending on the kind.
 */
public record FxPayload(int kind, double x, double y, double z, float power, double ax, double ay, double az) implements CustomPacketPayload {
	public static final int GROUND_IMPACT = 0;
	public static final int AIR_BURST_THREAT = 1;
	public static final int AIR_BURST_INTERCEPTOR = 2;
	public static final int LAUNCH = 3;
	public static final int TRACER = 4;
	/** A gun burst (sound only, the tracers come separately). */
	public static final int GUN = 5;
	/** For LAUNCH, {@code ax}: which launch sound (see the constants below). */
	public static final int LAUNCH_SOUND_NONE = 0;
	public static final int LAUNCH_SOUND_HEAVY = 1;
	public static final int LAUNCH_SOUND_LIGHT = 2;
	public static final int LAUNCH_SOUND_MLRS = 3;

	public static final Type<FxPayload> TYPE = new Type<>(AirDefense.id("fx"));
	public static final StreamCodec<ByteBuf, FxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FxPayload::kind,
			ByteBufCodecs.DOUBLE, FxPayload::x,
			ByteBufCodecs.DOUBLE, FxPayload::y,
			ByteBufCodecs.DOUBLE, FxPayload::z,
			ByteBufCodecs.FLOAT, FxPayload::power,
			ByteBufCodecs.DOUBLE, FxPayload::ax,
			ByteBufCodecs.DOUBLE, FxPayload::ay,
			ByteBufCodecs.DOUBLE, FxPayload::az,
			FxPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
