package com.stasdoto.airdefense.map;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * Lite: no countries - just two sides, "ours" and "the enemy", kept in each vehicle's {@code country} field
 * ({@link #OURS} = -1, the value every vehicle had for the player before; {@link #ENEMY} = -2). A player plays one
 * side at a time, switched on the tablet: his tablet lists and orders that side's vehicles, and what he puts down
 * joins it. Air defence shoots down what the other side launches, so a strike on one's own batteries is a test of
 * one's own air defence.
 */
public final class Sides {
	public static final int OURS = -1;
	public static final int ENEMY = -2;

	private static final Map<UUID, Integer> PLAYING = new HashMap<>();

	private Sides() {
	}

	public static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYING.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PLAYING.clear());
	}

	/** Any value a vehicle may carry (worlds from the full mod included) as one of the two sides. */
	public static int of(int country) {
		return country == ENEMY ? ENEMY : OURS;
	}

	/** The side the player plays now. */
	public static int playing(@Nullable ServerPlayer player) {
		return player == null ? OURS : PLAYING.getOrDefault(player.getUUID(), OURS);
	}

	public static void play(ServerPlayer player, int side) {
		PLAYING.put(player.getUUID(), of(side));
	}

	public static boolean same(int a, int b) {
		return of(a) == of(b);
	}

	public static boolean hostile(int a, int b) {
		return of(a) != of(b);
	}

	/** A vehicle just put down joins the side its player plays. */
	public static void claimPlaced(@Nullable ServerPlayer player, VehicleEntity v) {
		v.country = playing(player);
		// A supply or fuel truck comes loaded.
		if (v.getVehicleType().isTruck()) {
			v.setCargo(v.getVehicleType().cargoCapacity);
		}
	}
}
