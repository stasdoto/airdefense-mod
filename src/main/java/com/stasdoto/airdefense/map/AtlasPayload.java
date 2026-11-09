package com.stasdoto.airdefense.map;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** The atlas for the tablet map (1.25): the ground picture, packed (see LandAtlas). */
public record AtlasPayload(byte[] data) implements CustomPacketPayload {
	public static final Type<AtlasPayload> TYPE = new Type<>(AirDefense.id("atlas"));
	public static final StreamCodec<ByteBuf, AtlasPayload> CODEC = ByteBufCodecs.byteArray(4 * 1024 * 1024).map(AtlasPayload::new, AtlasPayload::data);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
