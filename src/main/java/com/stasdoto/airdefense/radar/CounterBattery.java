package com.stasdoto.airdefense.radar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.nation.Country;
import com.stasdoto.airdefense.nation.Politics;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * 1.30: counter-battery work. A counter-battery radar (Zoopark-1M, AN/TPQ-36) watches a sector ahead of it for shells
 * and rockets of the other side; after following one for a moment on the rising part of its arc it works out where it
 * was fired from. The firing position is marked on the tablet's map for its side, its owner hears of it, and that
 * side's artillery set to "counter-battery" answers by itself - so a battery that keeps firing from one spot gets hit
 * back ("shoot and scoot"). The towns of a country with a counter-battery radar answer the player's guns the same way.
 */
public final class CounterBattery {
	/** A radar working right now: where its antenna is, which way it looks, whose it is. */
	public record Station(int id, Vec3 pos, float yaw, RadarType type, int country, long seen) {
	}

	/** A firing position found: where, whose guns (country), found by which side, when, how many rounds were seen. */
	public static final class Fire {
		public final Vec3 pos;
		public final int shooter;
		public final int side;
		public final long first;
		public long last;
		public int rounds;
		public boolean answered;

		Fire(Vec3 pos, int shooter, int side, long now) {
			this.pos = pos;
			this.shooter = shooter;
			this.side = side;
			this.first = now;
			this.last = now;
			this.rounds = 1;
		}
	}

	private static final Map<ResourceKey<Level>, Map<Integer, Station>> STATIONS = new HashMap<>();
	private static final Map<ResourceKey<Level>, List<Fire>> FIRES = new HashMap<>();
	/** Shells being followed: entity id -> the tick it was first seen (it takes the radar a moment to work one out). */
	private static final Map<Integer, Long> WATCHING = new HashMap<>();
	private static final int STALE = 40;
	/** How long a firing position stays on the map (ticks). */
	public static final int KEEP = 6000;
	private static final int WORK_OUT = 12;
	/** Firing positions closer than this are one battery. */
	private static final double SAME = 30;

	/** For the tests: firing positions found, answers fired back, and the towns that answered the player. */
	public static int found;
	public static int answered;
	public static int townsAnswered;

	private CounterBattery() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 4 == 0) {
				tick(level);
			}
		});
	}

	public static void report(ServerLevel level, VehicleEntity v, Vec3 antenna, float yaw, RadarType type) {
		STATIONS.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>())
				.put(v.getId(), new Station(v.getId(), antenna, yaw, type, v.country, level.getGameTime()));
	}

	public static void remove(ServerLevel level, int id) {
		Map<Integer, Station> map = STATIONS.get(level.dimension());
		if (map != null) {
			map.remove(id);
		}
	}

	public static void clear() {
		STATIONS.clear();
		FIRES.clear();
		WATCHING.clear();
	}

	public static List<Station> stations(ServerLevel level) {
		Map<Integer, Station> map = STATIONS.get(level.dimension());
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		long now = level.getGameTime();
		map.values().removeIf(s -> now - s.seen > STALE || now < s.seen);
		return new ArrayList<>(map.values());
	}

	/** The firing positions found by this side's radars (still on the map). */
	public static List<Fire> fires(ServerLevel level, int side) {
		List<Fire> list = FIRES.get(level.dimension());
		if (list == null || list.isEmpty()) {
			return List.of();
		}
		Politics p = Politics.get(level.getServer());
		List<Fire> out = new ArrayList<>();
		for (Fire f : list) {
			if (sameSide(p, f.side, side)) {
				out.add(f);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------
	// Sides

	/** The player's side: his own vehicles (-1) and the country he runs. */
	public static boolean playerSide(Politics p, int country) {
		if (country == -1) {
			return true;
		}
		Country c = p.country(country);
		return c != null && c.owner != null;
	}

	public static boolean sameSide(Politics p, int a, int b) {
		return a == b || playerSide(p, a) && playerSide(p, b);
	}

	// ------------------------------------------------------------------------------------------------
	// Watching

	/** In range, in the array's sector, and up in the sky (not down among the hills). */
	static boolean sees(Station s, Vec3 at) {
		double dx = at.x - s.pos.x;
		double dz = at.z - s.pos.z;
		double r = s.type.range;
		if (dx * dx + dz * dz > r * r || at.y < s.pos.y + 6) {
			return false;
		}
		float bearing = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		return Math.abs(Mth.wrapDegrees(bearing - (s.yaw + s.type.facing))) <= s.type.sector / 2;
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		List<Fire> fires = FIRES.get(level.dimension());
		if (fires != null) {
			fires.removeIf(f -> now - f.last > KEEP);
		}
		if (!WATCHING.isEmpty()) {
			WATCHING.values().removeIf(t -> now - t > 1200);
		}
		List<Station> stations = stations(level);
		if (stations.isEmpty()) {
			return;
		}
		Politics p = Politics.get(level.getServer());
		for (Station s : stations) {
			double r = s.type.range;
			AABB box = new AABB(s.pos.x - r, s.pos.y - 64, s.pos.z - r, s.pos.x + r, s.pos.y + 700, s.pos.z + r);
			for (MissileEntity m : MissileEntity.find(level, box, m -> m.isAlive() && m.getMissileType().artillery())) {
				if (m.arcProgress() > 0.55 || sameSide(p, m.country(), s.country) || !sees(s, m.position())) {
					continue;
				}
				Long since = WATCHING.putIfAbsent(m.getId(), now);
				if (since == null || since < 0 || now - since < WORK_OUT) {
					continue;
				}
				// Worked out: the track goes back to the gun.
				WATCHING.put(m.getId(), -1L);
				located(level, p, s, m.origin(), m.country());
			}
		}
	}

	/** A battery found at {@code at}: on the map, told to the radar's side, answered by its guns. */
	private static void located(ServerLevel level, Politics p, Station s, Vec3 at, int shooter) {
		long now = level.getGameTime();
		List<Fire> fires = FIRES.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
		for (Fire f : fires) {
			if (f.side == s.country && f.pos.distanceToSqr(at) < SAME * SAME) {
				long before = f.last;
				f.last = now;
				f.rounds++;
				// Still firing from the same spot a while later: answered again.
				if (!f.answered || now - before > 400) {
					answer(level, p, s.country, f);
				}
				return;
			}
		}
		Fire f = new Fire(at, shooter, s.country, now);
		fires.add(f);
		found++;
		AirDefense.LOGGER.info("[airdefense] counter-battery radar found guns at {} {}", (int) at.x, (int) at.z);
		int dist = (int) Math.sqrt(Mth.square(at.x - s.pos.x) + Mth.square(at.z - s.pos.z));
		int bearing = (int) Mth.positiveModulo((float) (Mth.atan2(at.x - s.pos.x, -(at.z - s.pos.z)) * Mth.RAD_TO_DEG), 360f);
		Component msg = Component.translatable("message.airdefense.cb.found", dist, bearing, (int) at.x, (int) at.z).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer pl : level.players()) {
			if (sameSide(p, s.country, ownSide(p, pl)) && pl.distanceToSqr(s.pos) < 3000 * 3000) {
				pl.sendSystemMessage(msg);
			}
		}
		answer(level, p, s.country, f);
	}

	/** The side a player is on: the country he runs, or -1 (his own vehicles). */
	static int ownSide(Politics p, ServerPlayer pl) {
		Country c = p.countryOwnedBy(pl.getUUID());
		return c == null ? -1 : c.id;
	}

	/**
	 * The side's artillery in reach answers: the player's guns set to counter-battery fire, a country's guns always.
	 * Two guns at most for one battery found.
	 */
	private static void answer(ServerLevel level, Politics p, int side, Fire f) {
		f.answered = true;
		double reach = 1500;
		AABB box = new AABB(f.pos.x - reach, f.pos.y - 200, f.pos.z - reach, f.pos.x + reach, f.pos.y + 200, f.pos.z + reach);
		List<VehicleEntity> guns = level.getEntities(EntityTypeTest.forClass(VehicleEntity.class), box,
				v -> v.isAlive() && v.getVehicleType().isArtillery() && sameSide(p, v.country, side));
		guns.sort(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(f.pos)));
		int fired = 0;
		// At the ground under the muzzle the radar tracked the shells back to.
		BlockPos aim = com.stasdoto.airdefense.map.MapServer.ground(level, Mth.floor(f.pos.x), com.stasdoto.airdefense.map.MapActionPayload.Y_UNKNOWN,
				Mth.floor(f.pos.z));
		for (VehicleEntity v : guns) {
			if (fired >= 2) {
				break;
			}
			boolean auto = playerSide(p, v.country) ? v.getMode() == VehicleEntity.MODE_AUTO : true;
			if (!auto || v.striking() || v.loadedRounds() == 0) {
				continue;
			}
			double d = Math.sqrt(aim.distToCenterSqr(v.position()));
			if (d > v.getVehicleType().launcher.maxRange || d < v.getVehicleType().launcher.minRange) {
				continue;
			}
			if (v.commandFire(aim, null, v.getVehicleType().launcher.gun() ? 4 : 20)) {
				fired++;
				answered++;
				AirDefense.LOGGER.info("[airdefense] {} answers the guns at {} {}", v.getVehicleType().id, aim.getX(), aim.getZ());
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The towns answer

	/**
	 * A shell or rocket of the player's came down: if it fell on a town of a country that has a counter-battery radar
	 * and artillery, that town's guns answer at where it came from (after a while: the radar works it out, the guns
	 * turn round).
	 */
	public static void impact(ServerLevel level, MissileEntity m, Vec3 at) {
		Politics p = Politics.get(level.getServer());
		if (!playerSide(p, m.country())) {
			return;
		}
		Vec3 origin = m.origin();
		if (origin.lengthSqr() < 1) {
			return;
		}
		com.stasdoto.airdefense.nation.Settlement s = p.settlementAt(BlockPos.containing(at));
		if (s == null || s.country < 0 || playerSide(p, s.country)) {
			return;
		}
		if (com.stasdoto.airdefense.nation.Arsenals.answerGuns(level, p, s, origin)) {
			townsAnswered++;
		}
	}

	/** For the tests and the map: drops what is known (a new world). */
	public static void forget(ServerLevel level) {
		FIRES.remove(level.dimension());
		Map<Integer, Station> map = STATIONS.get(level.dimension());
		if (map != null) {
			map.clear();
		}
	}
}
