package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * 1.38: the towns' emergency services. When something blows up in a town with a player about, the town sends a fire
 * engine (it puts the fires out), an ambulance and a police car; they come along the streets with their lights flashing
 * and sirens going, work a while, and drive away. A police car also drives round the streets near a player in town
 * now and then. Only where a player can see it - nothing is sent to an empty town.
 */
public final class Services {
	/** Per town: when the last call and the last patrol were sent (game time). */
	private static final Map<Integer, Long> LAST_CALL = new HashMap<>();
	private static final Map<Integer, Long> LAST_PATROL = new HashMap<>();
	private record Later(long at, Runnable work) {
	}

	private static final List<Later> LATER = new ArrayList<>();

	private static void later(ServerLevel level, int ticks, Runnable work) {
		LATER.add(new Later(level.getServer().getTickCount() + ticks, work));
	}

	/** For the tests: vehicles sent (fire, ambulance, police), patrols. */
	public static final int[] SENT = new int[4];
	/** For the tests: a patrol as soon as the player is in town. */
	public static volatile boolean patrolNow;

	private Services() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!LATER.isEmpty()) {
				long now = server.getTickCount();
				LATER.removeIf(l -> {
					if (l.at() <= now) {
						l.work().run();
						return true;
					}
					return false;
				});
			}
			if (server.getTickCount() % 200 != 61) {
				return;
			}
			ServerLevel level = server.overworld();
			for (ServerPlayer pl : level.players()) {
				patrol(level, pl);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_CALL.clear();
			LAST_PATROL.clear();
			LATER.clear();
		});
	}

	/** The town's street plan (null for a village without one). */
	private static CityShape shapeOf(ServerLevel level, Settlement s) {
		if (s == null || !s.isCity()) {
			return null;
		}
		Cities.City c = Cities.plannedCityAt(level.getSeed(), s.center.getX(), s.center.getZ(), 40);
		return c == null ? null : c.shape();
	}

	/** Something blew up in town {@code town} (Repairs saw a new patch of damage): the services come. */
	public static void explosion(ServerLevel level, Vec3 at, int town) {
		Settlement s = Politics.get(level.getServer()).settlements.get(town);
		if (s == null || level.getNearestPlayer(at.x, at.y, at.z, 160, p -> true) == null) {
			return;
		}
		long now = level.getGameTime();
		if (now - LAST_CALL.getOrDefault(town, -100000L) < 1200) {
			return;
		}
		CityShape sh = shapeOf(level, s);
		if (sh == null) {
			return;
		}
		LAST_CALL.put(town, now);
		Vec3 player = level.getNearestPlayer(at.x, at.y, at.z, 160, p -> true).position();
		int[] from = startNode(sh, at, player, level);
		if (from == null) {
			return;
		}
		// The fire engine first, the ambulance a little after, then the police.
		send(level, s, sh, VehicleType.FIRE_TRUCK, 1, from, at, 0);
		later(level, 60, () -> send(level, s, sh, VehicleType.AMBULANCE, 2, from, at, 1));
		later(level, 140, () -> send(level, s, sh, VehicleType.POLICE_CAR, 3, from, at, 2));
	}

	/** A crossing 50-150 blocks from the call, out of the player's sight if it can (the far side from him). */
	private static int[] startNode(CityShape sh, Vec3 at, Vec3 player, ServerLevel level) {
		int[] best = null;
		double bestScore = -1;
		for (int[] n : sh.crossings()) {
			double d = Math.hypot(n[0] - at.x, n[1] - at.z);
			if (d < 50 || d > 150) {
				continue;
			}
			double score = Math.hypot(n[0] - player.x, n[1] - player.z) + level.getRandom().nextDouble() * 20;
			if (score > bestScore) {
				bestScore = score;
				best = n;
			}
		}
		return best;
	}

	private static List<Vec3> way(ServerLevel level, CityShape sh, int x0, int z0, int x1, int z1) {
		List<Vec3> out = new ArrayList<>();
		for (int[] n : sh.route(x0, z0, x1, z1)) {
			out.add(new Vec3(n[0] + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, n[0], n[1]), n[1] + 0.5));
		}
		return out;
	}

	/** One vehicle out to the call: along the streets from {@code from} to the crossing nearest it, then back. */
	private static void send(ServerLevel level, Settlement s, CityShape sh, VehicleType type, int kind, int[] from, Vec3 at, int lane) {
		List<Vec3> there = way(level, sh, from[0], from[1], (int) at.x, (int) at.z);
		if (there.isEmpty()) {
			return;
		}
		// The last stretch: off the crossing a little towards the blast (they park along the street, one behind the other).
		Vec3 last = there.getLast();
		Vec3 toward = new Vec3(at.x - last.x, 0, at.z - last.z);
		double d = toward.length();
		if (d > 4) {
			Vec3 park = last.add(toward.scale(Math.min(d - 3, 10 + lane * 8) / d));
			there.add(new Vec3(park.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) park.x, (int) park.z), park.z));
		}
		List<Vec3> back = new ArrayList<>(there);
		java.util.Collections.reverse(back);
		VehicleEntity v = spawnAt(level, s, type, there);
		if (v == null) {
			return;
		}
		v.service = kind;
		v.serviceAt = at;
		v.serviceBack = back.subList(1, back.size());
		v.setBeacons(true);
		v.drive(there.subList(1, there.size()), kind == 1 ? 0.75f : 0.85f);
		SENT[kind - 1]++;
	}

	private static VehicleEntity spawnAt(ServerLevel level, Settlement s, VehicleType type, List<Vec3> route) {
		if (route.size() < 2) {
			return null;
		}
		Vec3 start = route.getFirst();
		Vec3 next = route.get(1);
		if (!level.isLoaded(BlockPos.containing(start))) {
			return null;
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-(next.x - start.x), next.z - start.z));
		VehicleEntity v = VehicleEntity.spawn(level, type, start, yaw);
		v.country = s.country;
		v.home = s.id;
		v.garrison = true;
		v.setUnlimited(false);
		return v;
	}

	/** Now and then a police car drives round the streets near a player who is in a town. */
	private static void patrol(ServerLevel level, ServerPlayer pl) {
		Settlement s = Politics.get(level.getServer()).settlementAt(pl.blockPosition());
		CityShape sh = shapeOf(level, s);
		if (sh == null) {
			return;
		}
		long now = level.getGameTime();
		long wait = patrolNow ? 0 : 4800 + (s.id & 7) * 600;
		if (now - LAST_PATROL.getOrDefault(s.id, -100000L) < wait) {
			return;
		}
		LAST_PATROL.put(s.id, now);
		patrolNow = false;
		Vec3 me = pl.position();
		int[] from = startNode(sh, me, me, level);
		if (from == null) {
			return;
		}
		// Past the player and round a block or two, then away the way it came.
		List<int[]> near = new ArrayList<>();
		for (int[] n : sh.crossings()) {
			if (Math.hypot(n[0] - me.x, n[1] - me.z) < 70) {
				near.add(n);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		List<Vec3> loop = new ArrayList<>(way(level, sh, from[0], from[1], (int) me.x, (int) me.z));
		int[] via = near.get(level.getRandom().nextInt(near.size()));
		List<Vec3> round = way(level, sh, (int) me.x, (int) me.z, via[0], via[1]);
		if (round.size() > 1) {
			loop.addAll(round.subList(1, round.size()));
		}
		Vec3 end = loop.getLast();
		List<Vec3> home = way(level, sh, (int) end.x, (int) end.z, from[0], from[1]);
		if (home.size() > 1) {
			loop.addAll(home.subList(1, home.size()));
		}
		VehicleEntity v = spawnAt(level, s, VehicleType.POLICE_CAR, loop);
		if (v == null) {
			return;
		}
		v.service = 3;
		v.serviceAt = null;
		v.drive(loop.subList(1, loop.size()), 0.5f);
		SENT[3]++;
	}
}
