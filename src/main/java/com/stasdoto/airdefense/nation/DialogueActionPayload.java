package com.stasdoto.airdefense.nation;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** 1.29, client to server: what the player said to the person ({@link Dialogue} options). */
public record DialogueActionPayload(int entity, int action) implements CustomPacketPayload {
	public static final Type<DialogueActionPayload> TYPE = new Type<>(AirDefense.id("dialogue_action"));
	public static final StreamCodec<ByteBuf, DialogueActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueActionPayload::entity,
			ByteBufCodecs.VAR_INT, DialogueActionPayload::action,
			DialogueActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
