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
	/** One worker more or fewer: a = job, x = +1 / -1. */
	public static final int JOB = 7;
	/** Order a building: a = building type. */
	public static final int BUILD = 8;
	/** Take a building that has not been started out of the line: a = its place in the line. */
	public static final int CANCEL = 9;
	/** Hand over wood, stone and iron from the inventory. */
	public static final int DONATE = 10;
	/** Order a vehicle from the hangar: a = vehicle type. */
	public static final int VEHICLE = 11;
	/** All workers go home. */
	public static final int WORKERS_HOME = 12;
	/** Open the village screen (from the tablet map). */
	public static final int OPEN = 13;
	/** Gifts that calm the village (and end a riot). */
	public static final int CALM = 14;
	/** War on the country that owns this village. */
	public static final int DECLARE_WAR = 15;
	/** Ask that country for peace. */
	public static final int PEACE = 16;
	/** Pay what it asks for peace. */
	public static final int TRIBUTE = 17;

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
