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

	/** 2.0: the player takes a town car from its parking bay (the client draws the parked cars; see {@link CivilCars}). */
	public record TakeCar(BlockPos bay, String model, double x, double y, double z, float yaw) implements CustomPacketPayload {
		public static final Type<TakeCar> TYPE = new Type<>(AirDefense.id("take_car"));
		public static final StreamCodec<ByteBuf, TakeCar> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, TakeCar::bay,
				ByteBufCodecs.stringUtf8(48), TakeCar::model,
				ByteBufCodecs.DOUBLE, TakeCar::x,
				ByteBufCodecs.DOUBLE, TakeCar::y,
				ByteBufCodecs.DOUBLE, TakeCar::z,
				ByteBufCodecs.FLOAT, TakeCar::yaw,
				TakeCar::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TakeCar.TYPE, TakeCar.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			var player = context.player();
			if (player.level().getEntity(payload.vehicleId()) instanceof VehicleEntity vehicle) {
				vehicle.applyDriveReport(player, payload.a(), payload.b(), payload.impact(), payload.hit());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(TakeCar.TYPE, (p, context) ->
				CivilCars.take(context.player(), p.bay(), p.model(), p.x(), p.y(), p.z(), p.yaw()));
	}
}
