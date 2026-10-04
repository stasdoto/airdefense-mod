package com.stasdoto.airdefense.fx;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server side: broadcast an effect to everyone who could see it (explosions are visible from far away). */
public final class Fx {
	private static final double VIEW_RANGE = 1024;

	private Fx() {
	}

	public static void send(ServerLevel level, int kind, Vec3 at, float power, Vec3 extra) {
		FxPayload payload = new FxPayload(kind, at.x, at.y, at.z, power, extra.x, extra.y, extra.z);
		for (ServerPlayer player : PlayerLookup.around(level, at, VIEW_RANGE)) {
			if (ServerPlayNetworking.canSend(player, FxPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
