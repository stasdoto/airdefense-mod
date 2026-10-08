package com.stasdoto.airdefense.fx;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke screens (1.26): a fighting vehicle's smoke grenades lay a wall of smoke some twenty metres ahead that hides it
 * for about twenty seconds - nobody's guns and nobody's eyes (the computer's soldiers and crews) see through it.
 */
public final class Smoke {
	/** One cloud: centre, radius, the game time it clears. */
	private record Cloud(ServerLevel level, Vec3 at, double r, long until) {
	}

	private static final List<Cloud> CLOUDS = new ArrayList<>();
	/** For the automated test. */
	public static int laid;
	public static int blocked;

	private Smoke() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!CLOUDS.isEmpty()) {
				CLOUDS.removeIf(c -> c.level.getGameTime() > c.until);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CLOUDS.clear());
	}

	/** A cloud at {@code at} of radius {@code r} for {@code ticks}; every client draws it. */
	public static void lay(ServerLevel level, Vec3 at, double r, int ticks) {
		CLOUDS.add(new Cloud(level, at, r, level.getGameTime() + ticks));
		Fx.send(level, FxPayload.SMOKE, at, (float) r, new Vec3(ticks, 0, 0));
		laid++;
	}

	/** Does smoke hang across the line from {@code a} to {@code b}? */
	public static boolean blocks(ServerLevel level, Vec3 a, Vec3 b) {
		if (CLOUDS.isEmpty()) {
			return false;
		}
		Vec3 d = b.subtract(a);
		double len2 = d.lengthSqr();
		for (Cloud c : CLOUDS) {
			if (c.level != level) {
				continue;
			}
			// Distance from the cloud's centre to the segment.
			double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, c.at.subtract(a).dot(d) / len2));
			Vec3 p = a.add(d.scale(t));
			if (p.distanceToSqr(c.at) < c.r * c.r) {
				blocked++;
				return true;
			}
		}
		return false;
	}
}
