package com.stasdoto.airdefense.map;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Tablet map → server: "send me the situation", "this launcher fires there", "this battery: mode", "mark/clear target". */
public record MapActionPayload(int action, int vehicleId, int x, int y, int z) implements CustomPacketPayload {
	public static final int REFRESH = 0;
	public static final int STRIKE = 1;
	public static final int SET_MODE = 2;
	public static final int SET_TARGET = 3;
	public static final int CLEAR_TARGET = 4;
	/** The radar screen: the situation plus what the radars see. */
	public static final int RADAR = 5;
	/** Every launcher in reach fires everything at the tablet's target. */
	public static final int MASS_STRIKE = 6;
	/**
	 * 1.30: an artillery fire mission - like STRIKE, with the rounds asked for packed above the low byte
	 * ({@link #fireMission(int)}); the low byte is the action.
	 */
	public static final int FIRE_MISSION = 7;
	/** Height of a map point the client does not know: the server looks it up. */
	public static final int Y_UNKNOWN = Integer.MIN_VALUE;

	public static final Type<MapActionPayload> TYPE = new Type<>(AirDefense.id("map_action"));
	public static final StreamCodec<ByteBuf, MapActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MapActionPayload::action,
			ByteBufCodecs.VAR_INT, MapActionPayload::vehicleId,
			ByteBufCodecs.INT, MapActionPayload::x,
			ByteBufCodecs.INT, MapActionPayload::y,
			ByteBufCodecs.INT, MapActionPayload::z,
			MapActionPayload::new);

	public static int fireMission(int rounds) {
		return FIRE_MISSION | (Math.max(0, Math.min(99, rounds)) << 8);
	}

	/** The action without what is packed above it. */
	public int kind() {
		return action & 0xFF;
	}

	/** Rounds asked for in a fire mission (0 = the usual salvo). */
	public int rounds() {
		return action >> 8;
	}

	public static MapActionPayload radar() {
		return new MapActionPayload(RADAR, -1, 0, 0, 0);
	}

	public static MapActionPayload refresh() {
		return new MapActionPayload(REFRESH, -1, 0, 0, 0);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
