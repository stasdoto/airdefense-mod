package com.stasdoto.airdefense.nation;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** 1.43: the player's answer on the country screen (or asking for it). */
public record CountryActionPayload(int action, int country, boolean travel) implements CustomPacketPayload {
	/** Send me the list (the key). */
	public static final int LIST = 0;
	/** Play for this country (travel: go to its capital). */
	public static final int JOIN = 1;
	/** Play on my own, with no country (the old way: your own country comes with your first town). */
	public static final int ALONE = 2;
	/** Not now: the screen was closed. */
	public static final int LATER = 3;

	public static final Type<CountryActionPayload> TYPE = new Type<>(AirDefense.id("country_action"));
	public static final StreamCodec<ByteBuf, CountryActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CountryActionPayload::action,
			ByteBufCodecs.INT, CountryActionPayload::country,
			ByteBufCodecs.BOOL, CountryActionPayload::travel,
			CountryActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
