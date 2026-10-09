package com.stasdoto.airdefense.nation;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.29, server to client: a talk with a person - who he is ({@code title}: his work and his town), what he just said,
 * and what you can say or ask next ({@link Dialogue} options). {@code open}: show the talk (else only update it).
 */
public record DialoguePayload(int entity, String name, Component title, Component speech, List<Integer> options, boolean open)
		implements CustomPacketPayload {
	public static final Type<DialoguePayload> TYPE = new Type<>(AirDefense.id("dialogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialoguePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialoguePayload::entity,
			ByteBufCodecs.STRING_UTF8, DialoguePayload::name,
			ComponentSerialization.STREAM_CODEC, DialoguePayload::title,
			ComponentSerialization.STREAM_CODEC, DialoguePayload::speech,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), DialoguePayload::options,
			ByteBufCodecs.BOOL, DialoguePayload::open,
			DialoguePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
