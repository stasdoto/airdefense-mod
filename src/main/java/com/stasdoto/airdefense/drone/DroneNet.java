package com.stasdoto.airdefense.drone;

import java.util.List;
import java.util.Random;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.map.MapActionPayload;
import com.stasdoto.airdefense.map.MapServer;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/** Server side of flight tasks, the drone camera and raids. */
public final class DroneNet {
	private DroneNet() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(FlightPlanPayload.TYPE, FlightPlanPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DroneCam.CamPayload.TYPE, DroneCam.CamPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FlightPlanPayload.TYPE, (payload, context) -> launch(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DroneCam.tick(server);
			Raids.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DroneCam.end(handler.player, false));
	}

	private static void launch(ServerPlayer player, FlightPlanPayload p) {
		ItemStack tablet = DesignatorItem.held(player);
		if (tablet == null || DroneCam.watching(player)) {
			return;
		}
		ServerLevel level = player.level();
		if (!(level.getEntity(p.vehicleId()) instanceof VehicleEntity v) || !v.getVehicleType().isLauncher()) {
			return;
		}
		BlockPos target = MapServer.ground(level, p.x(), MapActionPayload.Y_UNKNOWN, p.z());
		DesignatorItem.setTarget(tablet, target);
		v.commandStrike(target, player, p.plan());
	}

	/**
	 * The tablet's "mass strike": every launcher that reaches the target fires everything it has at once; drones and
	 * cruise missiles take different routes and heights so they arrive from several sides.
	 */
	public static int massStrike(ServerPlayer player, BlockPos target) {
		ServerLevel level = player.level();
		List<? extends VehicleEntity> launchers = level.getEntities(EntityTypeTest.forClass(VehicleEntity.class),
				v -> v.isAlive() && v.getVehicleType().isLauncher() && v.distanceToSqr(player) < 6000.0 * 6000.0);
		Random r = new Random();
		int ordered = 0;
		for (VehicleEntity v : launchers) {
			MissileType.Kind kind = v.getVehicleType().launcher.missile.kind;
			FlightPlan plan = null;
			if (kind == MissileType.Kind.DRONE || kind == MissileType.Kind.CRUISE) {
				int maneuver = switch (r.nextInt(3)) {
					case 0 -> FlightPlan.FLANK;
					case 1 -> FlightPlan.LOW;
					default -> FlightPlan.WEAVE;
				};
				plan = new FlightPlan(55 + r.nextInt(50), 100, maneuver, 16, false);
			}
			if (v.commandStrike(target, null, plan)) {
				ordered++;
			}
		}
		player.sendOverlayMessage(Component.translatable(ordered > 0 ? "message.airdefense.mass_strike" : "message.airdefense.mass_strike_none", ordered));
		return ordered;
	}
}
