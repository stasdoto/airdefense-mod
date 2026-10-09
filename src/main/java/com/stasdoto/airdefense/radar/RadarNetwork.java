package com.stasdoto.airdefense.radar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.MissileEntity;

/**
 * Every radar that is switched on and standing, per dimension. Radars report in while they work; one that stops
 * reporting (driven off, switched off, destroyed) drops out after two seconds. Air defence in a radar's cover sees
 * further and is fooled by decoys less; the tablet's radar screen shows what the radars see.
 */
public final class RadarNetwork {
	public record Station(int id, Vec3 pos, float yaw, RadarType type, long seen) {
		/** The direction the radar looks (for sector radars), degrees, Minecraft yaw. */
		public float lookYaw() {
			return yaw + type.facing;
		}
	}

	private static final Map<ResourceKey<Level>, Map<Integer, Station>> STATIONS = new HashMap<>();
	private static final int STALE = 40;

	private RadarNetwork() {
	}

	public static void report(ServerLevel level, int id, Vec3 antenna, float yaw, RadarType type) {
		STATIONS.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>()).put(id, new Station(id, antenna, yaw, type, level.getGameTime()));
	}

	public static void remove(ServerLevel level, int id) {
		Map<Integer, Station> map = STATIONS.get(level.dimension());
		if (map != null) {
			map.remove(id);
		}
	}

	public static void clear() {
		STATIONS.clear();
	}

	/** The radars working right now. */
	public static List<Station> stations(ServerLevel level) {
		Map<Integer, Station> map = STATIONS.get(level.dimension());
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		long now = level.getGameTime();
		map.values().removeIf(s -> now - s.seen > STALE || now < s.seen);
		return new ArrayList<>(map.values());
	}

	/** Whether this station sees a target at {@code p}: in range, in its sector, and above its horizon. */
	public static boolean sees(ServerLevel level, Station s, Vec3 p) {
		double dx = p.x - s.pos.x;
		double dz = p.z - s.pos.z;
		double d2 = dx * dx + dz * dz + Mth.square(p.y - s.pos.y);
		if (d2 > s.type.range * s.type.range) {
			return false;
		}
		if (!s.type.rotates()) {
			float bearing = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
			if (Math.abs(Mth.wrapDegrees(bearing - s.lookYaw())) > s.type.sector / 2) {
				return false;
			}
		}
		BlockPos column = BlockPos.containing(p);
		if (level.hasChunkAt(column)) {
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
			// Close to the radar it sees lower: the horizon drops away with distance.
			double need = s.type.minAltitude * Math.min(1.0, Math.sqrt(d2) / (s.type.range * 0.35));
			return p.y - ground >= need;
		}
		return true;
	}

	/** The best radar (the one best at telling decoys apart) that sees a target at {@code p}, or null. */
	@Nullable
	public static Station coverage(ServerLevel level, Vec3 p) {
		Station best = null;
		for (Station s : stations(level)) {
			if (sees(level, s, p) && (best == null || s.type.discrimination > best.type.discrimination)) {
				best = s;
			}
		}
		return best;
	}

	/** Whether a battery at {@code at} gets target data from a radar (it stands inside some radar's range). */
	public static boolean linked(ServerLevel level, Vec3 at) {
		for (Station s : stations(level)) {
			if (s.pos.distanceToSqr(at) <= s.type.range * s.type.range) {
				return true;
			}
		}
		return false;
	}

	/** Everything in the air the radars see (missiles, drones, interceptors), without repeats. */
	public static List<MissileEntity> contacts(ServerLevel level, int max) {
		Map<Integer, MissileEntity> seen = new LinkedHashMap<>();
		for (Station s : stations(level)) {
			double r = s.type.range;
			AABB box = new AABB(s.pos.x - r, s.pos.y - r, s.pos.z - r, s.pos.x + r, s.pos.y + r, s.pos.z + r);
			// A howitzer shell is too small for a search radar (only a counter-battery radar picks it up).
			for (MissileEntity m : MissileEntity.find(level, box, m -> m.isAlive() && !m.getMissileType().shell() && !seen.containsKey(m.getId()))) {
				if (sees(level, s, m.position())) {
					seen.put(m.getId(), m);
					if (seen.size() >= max) {
						return new ArrayList<>(seen.values());
					}
				}
			}
		}
		return new ArrayList<>(seen.values());
	}
}
