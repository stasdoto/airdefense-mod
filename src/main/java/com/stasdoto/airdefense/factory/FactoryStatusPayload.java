package com.stasdoto.airdefense.factory;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Server → factory menu: the state of the factory ({@code open} = open the menu now). */
public record FactoryStatusPayload(BlockPos pos, boolean open, int buildPercent, boolean creative, List<Integer> stock,
		List<Integer> queue, int progress) implements CustomPacketPayload {
	public static final Type<FactoryStatusPayload> TYPE = new Type<>(AirDefense.id("factory_status"));
	public static final StreamCodec<ByteBuf, FactoryStatusPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, FactoryStatusPayload::pos,
			ByteBufCodecs.BOOL, FactoryStatusPayload::open,
			ByteBufCodecs.VAR_INT, FactoryStatusPayload::buildPercent,
			ByteBufCodecs.BOOL, FactoryStatusPayload::creative,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), FactoryStatusPayload::stock,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), FactoryStatusPayload::queue,
			ByteBufCodecs.VAR_INT, FactoryStatusPayload::progress,
			FactoryStatusPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
