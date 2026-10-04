package com.stasdoto.airdefense.weapon;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * Client to server: a shot (with the direction the round left in: the client adds the spread, the server checks it
 * stays close to where the player looks), a reload, or the night vision goggles switched on or off.
 */
public record GunActionPayload(int action, float dx, float dy, float dz) implements CustomPacketPayload {
	public static final int FIRE = 0;
	public static final int RELOAD = 1;
	public static final int NVG = 2;

	public static final Type<GunActionPayload> TYPE = new Type<>(AirDefense.id("gun_action"));
	public static final StreamCodec<ByteBuf, GunActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GunActionPayload::action,
			ByteBufCodecs.FLOAT, GunActionPayload::dx,
			ByteBufCodecs.FLOAT, GunActionPayload::dy,
			ByteBufCodecs.FLOAT, GunActionPayload::dz,
			GunActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
