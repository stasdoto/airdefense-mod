package com.stasdoto.airdefense.factory;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Factory menu → server: refresh, order N of a product, cancel the last order, take everything in stock. */
public record FactoryActionPayload(BlockPos pos, int action, int product, int count) implements CustomPacketPayload {
	public static final int REFRESH = 0;
	public static final int ORDER = 1;
	public static final int CANCEL = 2;
	public static final int TAKE = 3;

	public static final Type<FactoryActionPayload> TYPE = new Type<>(AirDefense.id("factory_action"));
	public static final StreamCodec<ByteBuf, FactoryActionPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, FactoryActionPayload::pos,
			ByteBufCodecs.VAR_INT, FactoryActionPayload::action,
			ByteBufCodecs.VAR_INT, FactoryActionPayload::product,
			ByteBufCodecs.VAR_INT, FactoryActionPayload::count,
			FactoryActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
