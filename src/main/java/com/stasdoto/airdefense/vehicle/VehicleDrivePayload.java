package com.stasdoto.airdefense.vehicle;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * 2.0: the driver's client tells the server what the driver is doing (revs, throttle, gear, brakes, lights, horn, skid,
 * steering - packed, see {@link VehicleEntity#getEngineRpm()} and the others) and any crash ({@code impact}: m/s of speed
 * lost, {@code hit}: the vehicle run into, or -1). The server passes it on to everyone round.
 */
public record VehicleDrivePayload(int vehicleId, int a, int b, float impact, int hit) implements CustomPacketPayload {
	public static final Type<VehicleDrivePayload> TYPE = new Type<>(AirDefense.id("vehicle_drive"));
	public static final StreamCodec<ByteBuf, VehicleDrivePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, VehicleDrivePayload::vehicleId,
			ByteBufCodecs.INT, VehicleDrivePayload::a,
			ByteBufCodecs.INT, VehicleDrivePayload::b,
			ByteBufCodecs.FLOAT, VehicleDrivePayload::impact,
			ByteBufCodecs.VAR_INT, VehicleDrivePayload::hit,
			VehicleDrivePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			var player = context.player();
			if (player.level().getEntity(payload.vehicleId()) instanceof VehicleEntity vehicle) {
				vehicle.applyDriveReport(player, payload.a(), payload.b(), payload.impact(), payload.hit());
			}
		});
	}
}
