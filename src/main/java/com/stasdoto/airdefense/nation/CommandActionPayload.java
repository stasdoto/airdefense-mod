package com.stasdoto.airdefense.nation;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * 2.0: an order given from the map in command mode (see {@link Command}). {@code country} is the country being
 * commanded; the meaning of {@code a}, {@code b}, {@code x}, {@code z} depends on the action.
 */
public record CommandActionPayload(int action, int country, int a, int b, int x, int z) implements CustomPacketPayload {
	/** Send me where things stand (whether I may command, and the commanded country's forces). */
	public static final int STATE = 0;
	/** Take command of {@code country}. */
	public static final int TAKE = 1;
	/** Back to my own country. */
	public static final int RELEASE = 2;
	/** Put up a vehicle: a = its type; b = 1: at the point x, z; b = 0: by itself, near the borders. */
	public static final int PLACE = 3;
	/** Strike the point x, z: a = the launcher type (-1 = any), b = how many rounds. */
	public static final int STRIKE = 4;
	/** War on the country a. */
	public static final int WAR = 5;
	/** Peace with the country a. */
	public static final int PEACE = 6;

	/** Most rounds one order can send. */
	public static final int MAX_ROUNDS = 64;

	public static final Type<CommandActionPayload> TYPE = new Type<>(AirDefense.id("command_action"));
	public static final StreamCodec<ByteBuf, CommandActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CommandActionPayload::action,
			ByteBufCodecs.INT, CommandActionPayload::country,
			ByteBufCodecs.INT, CommandActionPayload::a,
			ByteBufCodecs.VAR_INT, CommandActionPayload::b,
			ByteBufCodecs.INT, CommandActionPayload::x,
			ByteBufCodecs.INT, CommandActionPayload::z,
			CommandActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
