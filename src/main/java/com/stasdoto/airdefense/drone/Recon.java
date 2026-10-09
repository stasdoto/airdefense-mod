package com.stasdoto.airdefense.drone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.nation.Country;
import com.stasdoto.airdefense.nation.Politics;
import com.stasdoto.airdefense.nation.SoldierEntity;
import com.stasdoto.airdefense.radar.CounterBattery;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * 1.34: what the reconnaissance drones see. An Orlan-10 or a TB2 over the field marks the other side's vehicles and
 * soldiers under it: they show on its side's tablet map (for a minute after they were last seen) and, seen by the
 * player's drones, they glow (marked for everybody on his side). While a drone of a side circles over a point, that
 * side's guns firing there are corrected by it - the shells fall twice as close round the aim point.
 */
public final class Recon {
	/** Something a drone saw: the entity, where it was last seen, a vehicle (of which type) or soldiers, by whose side. */
	public static final class Seen {
		public final int id;
		public final boolean vehicle;
		public final int vtype;
		public final int side;
		public Vec3 pos;
		public long last;

		Seen(int id, boolean vehicle, int vtype, int side, Vec3 pos, long now) {
			this.id = id;
			this.vehicle = vehicle;
			this.vtype = vtype;
			this.side = side;
			this.pos = pos;
			this.last = now;
		}
	}

	/** A reconnaissance drone over the field: where it is, whose, how far it sees, its type, when it last looked. */
	public record Eye(int id, Vec3 pos, int side, double range, int type, long seen) {
	}

	private static final Map<ResourceKey<Level>, Map<Long, Seen>> SEEN = new HashMap<>();
	private static final Map<ResourceKey<Level>, Map<Integer, Eye>> EYES = new HashMap<>();
	/** How long something seen stays on the map (ticks). */
	public static final int KEEP = 1200;
	private static final int EYE_STALE = 60;
	private static final int MAX_SEEN = 400;

	/** For the tests: things spotted, artillery rounds corrected by a drone. */
	public static int spotted;
	public static int corrected;

	private Recon() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SEEN.clear();
			EYES.clear();
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 100 == 0) {
				long now = level.getGameTime();
				Map<Long, Seen> seen = SEEN.get(level.dimension());
				if (seen != null) {
					seen.values().removeIf(s -> now - s.last > KEEP || now < s.last);
				}
				Map<Integer, Eye> eyes = EYES.get(level.dimension());
				if (eyes != null) {
					eyes.values().removeIf(e -> now - e.seen > EYE_STALE || now < e.seen);
				}
			}
		});
	}

	/** One side for all of the player's own things (his vehicles and his country's). */
	private static int sideKey(Politics p, int side) {
		return CounterBattery.playerSide(p, side) ? -1 : side;
	}

	/** A drone looks round under it (every second while it is over the field). */
	public static void look(ServerLevel level, MissileEntity drone) {
		MissileType t = drone.getMissileType();
		long now = level.getGameTime();
		int side = drone.country();
		double r = t.sightRange();
		Vec3 at = drone.position();
		EYES.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>()).put(drone.getId(), new Eye(drone.getId(), at, side, r, t.ordinal(), now));
		Politics p = Politics.get(level.getServer());
		Map<Long, Seen> map = SEEN.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>());
		boolean player = CounterBattery.playerSide(p, side);
		long key0 = (long) sideKey(p, side) << 32;
		AABB box = new AABB(at.x - r, at.y - 180, at.z - r, at.x + r, at.y + 30, at.z + r);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && (e instanceof VehicleEntity || e instanceof SoldierEntity))) {
			double dx = e.getX() - at.x;
			double dz = e.getZ() - at.z;
			if (dx * dx + dz * dz > r * r || !enemy(p, side, e)) {
				continue;
			}
			long key = key0 | (e.getId() & 0xFFFFFFFFL);
			Seen s = map.get(key);
			if (s == null) {
				if (map.size() >= MAX_SEEN) {
					continue;
				}
				boolean vehicle = e instanceof VehicleEntity;
				s = new Seen(e.getId(), vehicle, vehicle ? ((VehicleEntity) e).getVehicleType().ordinal() : -1, side, e.position(), now);
				map.put(key, s);
				spotted++;
			}
			s.pos = e.position();
			s.last = now;
			if (player) {
				// (Vehicles take no potion effects: they are marked by hand.)
				if (e instanceof VehicleEntity v) {
					v.markSeen(now + 50);
				} else {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
				}
			}
		}
	}

	/**
	 * Is {@code e} on the other side for a drone of {@code side}: the player's drones go for anything that is not his; a
	 * country's for the vehicles and soldiers of the countries it is at war with (the player's when it is at war with
	 * him).
	 */
	public static boolean enemy(Politics p, int side, Entity e) {
		int other;
		if (e instanceof VehicleEntity v) {
			other = v.country;
		} else if (e instanceof SoldierEntity s) {
			other = s.country();
		} else {
			return false;
		}
		if (CounterBattery.sameSide(p, side, other)) {
			return false;
		}
		if (CounterBattery.playerSide(p, side)) {
			return true;
		}
		Country c = p.country(side);
		if (c == null) {
			return true;
		}
		if (CounterBattery.playerSide(p, other)) {
			return atWarWithPlayer(p, c);
		}
		return other >= 0 && c.atWarWith(other);
	}

	/** Is this country at war with a player's country. */
	public static boolean atWarWithPlayer(Politics p, Country c) {
		for (int w : c.wars) {
			if (CounterBattery.playerSide(p, w)) {
				return true;
			}
		}
		return false;
	}

	/** Is a drone of this side over {@code at} right now (within its sight). */
	public static boolean watched(ServerLevel level, Vec3 at, int side) {
		Map<Integer, Eye> eyes = EYES.get(level.dimension());
		if (eyes == null || eyes.isEmpty()) {
			return false;
		}
		Politics p = Politics.get(level.getServer());
		long now = level.getGameTime();
		for (Eye e : eyes.values()) {
			if (now - e.seen > EYE_STALE || !CounterBattery.sameSide(p, e.side, side)) {
				continue;
			}
			double dx = e.pos.x - at.x;
			double dz = e.pos.z - at.z;
			if (dx * dx + dz * dz <= e.range * e.range) {
				return true;
			}
		}
		return false;
	}

	/** What this side's drones have seen (still on the map). */
	public static List<Seen> seen(ServerLevel level, int side) {
		Map<Long, Seen> map = SEEN.get(level.dimension());
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		Politics p = Politics.get(level.getServer());
		long now = level.getGameTime();
		List<Seen> out = new ArrayList<>();
		for (Seen s : map.values()) {
			if (now - s.last <= KEEP && CounterBattery.sameSide(p, s.side, side)) {
				out.add(s);
			}
		}
		return out;
	}

	/** This side's reconnaissance drones over the field. */
	public static List<Eye> eyes(ServerLevel level, int side) {
		Map<Integer, Eye> map = EYES.get(level.dimension());
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		Politics p = Politics.get(level.getServer());
		long now = level.getGameTime();
		List<Eye> out = new ArrayList<>();
		for (Eye e : map.values()) {
			if (now - e.seen <= EYE_STALE && CounterBattery.sameSide(p, e.side, side)) {
				out.add(e);
			}
		}
		return out;
	}

	/** A drone that went down or home stops looking. */
	public static void gone(ServerLevel level, int id) {
		Map<Integer, Eye> map = EYES.get(level.dimension());
		if (map != null) {
			map.remove(id);
		}
	}
}
