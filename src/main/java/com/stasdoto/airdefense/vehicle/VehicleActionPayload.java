package com.stasdoto.airdefense.vehicle;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** A vehicle key pressed by a crew member (deploy, mode, change seat, fold for driving, fire). */
public record VehicleActionPayload(int vehicleId, int action) implements CustomPacketPayload {
	public static final Type<VehicleActionPayload> TYPE = new Type<>(AirDefense.id("vehicle_action"));
	public static final StreamCodec<ByteBuf, VehicleActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, VehicleActionPayload::vehicleId,
			ByteBufCodecs.VAR_INT, VehicleActionPayload::action,
			VehicleActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			var player = context.player();
			if (player.level().getEntity(payload.vehicleId()) instanceof VehicleEntity vehicle) {
				vehicle.handleAction(player, payload.action());
			}
		});
	}
}
