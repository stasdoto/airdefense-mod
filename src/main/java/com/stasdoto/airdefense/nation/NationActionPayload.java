package com.stasdoto.airdefense.nation;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Client to server: what the player does with a village (from its screen or the tablet map). */
public record NationActionPayload(int action, int settlement, int a, int x, int y, int z) implements CustomPacketPayload {
	public static final int INFO = 0;
	public static final int BUY = 1;
	public static final int TAKE = 2;
	public static final int MOBILIZE = 3;
	public static final int DEMOBILIZE = 4;
	public static final int ORDER = 5;
	public static final int RECALL = 6;

	public static final Type<NationActionPayload> TYPE = new Type<>(AirDefense.id("nation_action"));
	public static final StreamCodec<ByteBuf, NationActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, NationActionPayload::action,
			ByteBufCodecs.VAR_INT, NationActionPayload::settlement,
			ByteBufCodecs.VAR_INT, NationActionPayload::a,
			ByteBufCodecs.VAR_INT, NationActionPayload::x,
			ByteBufCodecs.VAR_INT, NationActionPayload::y,
			ByteBufCodecs.VAR_INT, NationActionPayload::z,
			NationActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
