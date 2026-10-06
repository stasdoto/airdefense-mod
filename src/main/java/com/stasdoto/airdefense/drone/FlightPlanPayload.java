package com.stasdoto.airdefense.drone;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Tablet → server: launch from this vehicle at (x, z) with this flight plan. */
public record FlightPlanPayload(int vehicleId, int x, int z, FlightPlan plan) implements CustomPacketPayload {
	public static final Type<FlightPlanPayload> TYPE = new Type<>(AirDefense.id("flight_plan"));
	public static final StreamCodec<ByteBuf, FlightPlanPayload> CODEC = StreamCodec.of(FlightPlanPayload::write, FlightPlanPayload::read);

	private static void write(ByteBuf buf, FlightPlanPayload p) {
		buf.writeInt(p.vehicleId);
		buf.writeInt(p.x);
		buf.writeInt(p.z);
		buf.writeShort(p.plan.altitude());
		buf.writeByte(p.plan.speedPercent());
		buf.writeByte(p.plan.maneuver());
		buf.writeByte(p.plan.count());
		buf.writeBoolean(p.plan.camera());
	}

	private static FlightPlanPayload read(ByteBuf buf) {
		int id = buf.readInt();
		int x = buf.readInt();
		int z = buf.readInt();
		return new FlightPlanPayload(id, x, z, new FlightPlan(buf.readShort(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean()));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
