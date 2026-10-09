package com.stasdoto.airdefense.drone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.nation.Politics;
import com.stasdoto.airdefense.radar.CounterBattery;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * 1.34: electronic warfare. A Borisoglebsk-2 or a Bukovel-AD with its mast up jams round it: the other side's drones
 * that fly into its reach lose their link, the sooner the closer they come - an FPV's picture goes and it drops, a
 * loitering munition flies on blind and comes down somewhere, a reconnaissance drone gives up and flies home by itself;
 * a Shahed's satellite navigation is fooled and it strays off its aim point. Cruise and ballistic missiles fly on.
 */
public final class Jammers {
	/** A jammer working right now: where its antenna is, how far it reaches, whose it is, when it last reported. */
	public record Jammer(int id, Vec3 pos, double range, int country, long seen) {
	}

	private static final Map<ResourceKey<Level>, Map<Integer, Jammer>> ALL = new HashMap<>();
	private static final int STALE = 40;

	/** For the tests: links lost (FPV, loitering munitions, reconnaissance drones), Shaheds sent astray. */
	public static int linksLost;
	public static int drifted;

	private Jammers() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ALL.clear());
	}

	public static void report(ServerLevel level, VehicleEntity v, Vec3 antenna, double range) {
		ALL.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>())
				.put(v.getId(), new Jammer(v.getId(), antenna, range, v.country, level.getGameTime()));
	}

	public static void remove(ServerLevel level, int id) {
		Map<Integer, Jammer> map = ALL.get(level.dimension());
		if (map != null) {
			map.remove(id);
		}
	}

	public static List<Jammer> jammers(ServerLevel level) {
		Map<Integer, Jammer> map = ALL.get(level.dimension());
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		long now = level.getGameTime();
		map.values().removeIf(j -> now - j.seen > STALE || now < j.seen);
		return new ArrayList<>(map.values());
	}

	/**
	 * The chance, at one look (every half second), that a drone of side {@code side} at {@code at} loses its link: 0 out
	 * of the reach of every jammer of the other side; 15% at the edge of one's reach, 80% right by it.
	 */
	public static double chance(ServerLevel level, Vec3 at, int side) {
		List<Jammer> list = jammers(level);
		if (list.isEmpty()) {
			return 0;
		}
		Politics p = Politics.get(level.getServer());
		double best = 0;
		for (Jammer j : list) {
			if (CounterBattery.sameSide(p, j.country, side)) {
				continue;
			}
			double d = j.pos.distanceTo(at);
			if (d < j.range) {
				best = Math.max(best, 0.15 + 0.65 * (1 - d / j.range));
			}
		}
		return best;
	}
}
