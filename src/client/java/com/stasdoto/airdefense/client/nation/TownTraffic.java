package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.client.fx.CityAmbience;
import com.stasdoto.airdefense.client.map.AtlasClient;
import com.stasdoto.airdefense.nation.PedestrianEntity;
import com.stasdoto.airdefense.nation.PropEntity;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.street.FacadeBlocks;
import com.stasdoto.airdefense.street.Weathered;

/**
 * 1.50 "A living town": the traffic of the town round the player, on his own client. Cars of many kinds and colours
 * drive the streets on the right, keep their distance, stop for people crossing, turn at the crossings; buses stop at
 * the bus stops and take the people waiting there; trolleybuses run under the wires with their poles up, trams on
 * their rails down the main street. Busy in the rush hours, quiet at night, gone when the sirens sound. And cars stand
 * in the car parks' bays and on the drives (a model and colour of its own for each bay).
 */
public final class TownTraffic {
	public static final String[] CARS = {"car_saloon_white", "car_saloon_red", "car_saloon_blue", "car_saloon_green", "car_saloon_beige",
			"car_saloon_cherry", "car_saloon_black", "car_saloon_silver", "car_hatch_white", "car_hatch_red", "car_hatch_blue", "car_hatch_green",
			"car_hatch_silver", "car_boxy_beige", "car_boxy_cherry", "car_boxy_white", "car_boxy_blue", "car_boxy_green", "car_suv_black",
			"car_suv_silver", "car_suv_white"};
	public static final String[] OTHERS = {"car_taxi", "town_bus", "trolleybus", "tram"};
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<PropEntity> ALL = new ArrayList<>();
	private static final Map<Long, PropEntity> PARKED = new HashMap<>();
	private static int nextId = -6_000_000;
	private static ClientLevel lastLevel;
	private static int ticks;
	/** For the tests: vehicles made by kind (car, taxi, bus, trolleybus, tram), parked, stops made at bus stops, turns. */
	public static final int[] MADE = new int[5];
	public static int parked;
	public static int busStops;
	public static int turns;
	public static int waits;
	/** For the tests: horns sounded at crossings. */
	public static int horns;
	/** For the tests: as many at once as this (0: by the hour). */
	public static volatile int force;

	/** What a moving vehicle is doing: along which street, which way, where on it, turning. */
	private static final class Drive {
		int kind;
		/** 0: along z (on the line x = line); 1: along x (on the line z = line). */
		int axis;
		int line;
		int dir;
		double along;
		double y;
		double max;
		double length;
		/** A turn in progress: its centre, start point, the heading at the start, the turn's sign, how far (0..1). */
		boolean turning;
		double cx;
		double cz;
		double sx;
		double sz;
		double fx;
		double fz;
		double radius;
		int turnSign;
		double turned;
		int newAxis;
		int newLine;
		int newDir;
		/** The cross street last decided about (no second decision at the same one), the stop last stopped at. */
		int lastCross = Integer.MIN_VALUE;
		long lastStop = Long.MIN_VALUE;
		int stopped;
		boolean honked;
	}

	private static final Map<PropEntity, Drive> DRIVES = new HashMap<>();
	private static final com.stasdoto.airdefense.client.vehicle.TrafficSounds.Info SOUND_INFO = new com.stasdoto.airdefense.client.vehicle.TrafficSounds.Info() {
		@Override
		public int kind(Entity vehicle) {
			Drive d = vehicle instanceof PropEntity p ? DRIVES.get(p) : null;
			return d == null ? 0 : d.kind;
		}

		@Override
		public double speed(Entity vehicle) {
			return vehicle instanceof PropEntity p ? p.speed : 0;
		}
	};

	private TownTraffic() {
	}

	public static List<String> models() {
		List<String> out = new ArrayList<>(List.of(CARS));
		out.addAll(List.of(OTHERS));
		return out;
	}

	public static int count() {
		return ALL.size();
	}

	public static int parkedCount() {
		return PARKED.size();
	}

	// ------------------------------------------------------------------------------------------------
	// 2.0: parked cars can be taken (driven away as real cars) and shoved by vehicles

	/** Bays whose car has been taken (no new one drawn there while this world is open). */
	private static final java.util.Set<Long> TAKEN = new java.util.HashSet<>();

	/** A parked car the player looks at within reach: {bay key, the prop}, or null. */
	@org.jetbrains.annotations.Nullable
	public static Map.Entry<Long, PropEntity> parkedInSight(Vec3 eye, Vec3 look, double reach) {
		Map.Entry<Long, PropEntity> best = null;
		double bestT = reach;
		for (Map.Entry<Long, PropEntity> e : PARKED.entrySet()) {
			PropEntity p = e.getValue();
			if (p.isRemoved() || p.position().distanceToSqr(eye) > (reach + 4) * (reach + 4)) {
				continue;
			}
			for (double t = 0.5; t < bestT; t += 0.2) {
				Vec3 q = eye.add(look.scale(t));
				if (inside(p, q.x, q.y, q.z, 0.1)) {
					bestT = t;
					best = e;
					break;
				}
			}
		}
		return best;
	}

	/** Is this point inside the parked car (its footprint turned by its yaw, about 1.6 m high)? */
	private static boolean inside(PropEntity p, double x, double y, double z, double margin) {
		double yaw = Math.toRadians(p.pose[3]);
		double dx = x - p.pose[0];
		double dz = z - p.pose[2];
		double along = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
		double across = -dx * Math.cos(yaw) - dz * Math.sin(yaw);
		return Math.abs(along) < 2.3 + margin && Math.abs(across) < 0.95 + margin && y > p.pose[1] - margin && y < p.pose[1] + 1.6 + margin;
	}

	/** The car of this bay was taken (the server put a real one there): it is no longer drawn. */
	public static void taken(ClientLevel level, long bay) {
		PropEntity p = PARKED.remove(bay);
		TAKEN.add(bay);
		if (p != null) {
			level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
			p.discard();
		}
	}

	/**
	 * A vehicle driven by the local player shoves the parked cars it runs into (they are only drawn here): they slide
	 * and turn away. Returns the share of the vehicle's speed it keeps (momentum shared with a car of ~1.3 t).
	 */
	public static double shove(com.stasdoto.airdefense.vehicle.VehicleEntity v, double vx, double vz, double mass) {
		double sp = Math.hypot(vx, vz);
		if (sp < 0.02 || PARKED.isEmpty()) {
			return 1;
		}
		double yaw = Math.toRadians(v.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double half = v.getVehicleType().geometry.length() / 2;
		double keep = 1;
		for (PropEntity p : PARKED.values()) {
			if (p.isRemoved() || p.position().distanceToSqr(v.position()) > (half + 4) * (half + 4)) {
				continue;
			}
			// The vehicle's nose (and its corners) in the car?
			double nx = v.getX() + fx * (half + sp);
			double nz = v.getZ() + fz * (half + sp);
			if (!inside(p, nx, v.getY() + 0.5, nz, 0.2) && !inside(p, v.getX(), v.getY() + 0.5, v.getZ(), 0.2)) {
				continue;
			}
			double share = mass / (mass + 1300);
			double push = sp * share * 1.25;
			double dx = p.pose[0] - v.getX();
			double dz = p.pose[2] - v.getZ();
			double side = Math.signum(dx * -fz + dz * fx);
			double spinDeg = side * Math.min(25, sp * 40);
			p.move(p.pose[0] + vx / sp * push, p.pose[1], p.pose[2] + vz / sp * push, p.pose[3] + spinDeg, 0);
			keep = Math.min(keep, share);
			shoved++;
		}
		return keep;
	}

	/** For the tests: parked cars shoved. */
	public static int shoved;

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			com.stasdoto.airdefense.client.vehicle.TrafficSounds.clear();
			ALL.clear();
			DRIVES.clear();
			PARKED.clear();
			TAKEN.clear();
			lastLevel = level;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		Vec3 me = mc.player.position();
		for (PropEntity p : new ArrayList<>(ALL)) {
			Drive d = DRIVES.get(p);
			if (d == null || !drive(level, p, d) || p.position().distanceTo(me) > 120) {
				remove(level, p);
			}
		}
		// 2.0: the nearest few heard with their engines.
		com.stasdoto.airdefense.client.vehicle.TrafficSounds.tick(ALL, SOUND_INFO, me);
		if (++ticks % 20 != 0) {
			return;
		}
		boolean alert = com.stasdoto.airdefense.client.siren.SirenClient.alertHeard();
		long hour = (Math.floorMod(level.getOverworldClockTime(), 24000L) / 1000 + 6) % 24;
		AtlasClient.City town = townAt(level, me.x, me.z, me.y);
		int want = force > 0 ? force : town == null || alert ? 0 : hour < 6 ? 2 : hour < 7 ? 6 : hour < 10 ? 16 : hour < 17 ? 11 : hour < 20 ? 16 : hour < 23 ? 8 : 3;
		for (int k = 0; k < 2 && ALL.size() < want && town != null; k++) {
			spawn(mc, level, me, town, hour);
		}
		if (ticks % 100 == 0 || PARKED.isEmpty() && ticks % 40 == 0) {
			park(level, me);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The streets

	private static AtlasClient.City local;
	private static Vec3 localAt;
	private static int localTick;

	/**
	 * The town's street grid at (x, z): the atlas's, or (beyond the atlas, far out in the world) worked out from the blocks
	 * round the player - the lines the streets' middle paint runs along.
	 */
	private static AtlasClient.City townAt(ClientLevel level, double x, double z, double y) {
		try {
			AtlasClient.City t = CityAmbience.townAt(x, z);
			if (t != null) {
				return t;
			}
		} catch (RuntimeException e) {
			// No atlas yet.
		}
		if (!CityAmbience.inTown) {
			return null;
		}
		if (local == null || localAt == null || ticks - localTick > 200 || localAt.distanceToSqr(x, localAt.y, z) > 50 * 50) {
			localTick = ticks;
			localAt = new Vec3(x, y, z);
			local = scanGrid(level, x, z, y);
		}
		return local;
	}

	private static AtlasClient.City scanGrid(ClientLevel level, double cx, double cz, double y) {
		int bx = Mth.floor(cx);
		int bz = Mth.floor(cz);
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		List<Integer> xs = new ArrayList<>();
		List<Integer> zs = new ArrayList<>();
		int yRoad = Integer.MIN_VALUE;
		double ry = roadY(level, cx, cz, y);
		for (int dy = -6; dy <= 6 && yRoad == Integer.MIN_VALUE; dy++) {
			for (int k = -40; k <= 40; k += 4) {
				if (isRoad(level.getBlockState(q.set(bx + k, (int) Math.floor(y) + dy - 1, bz)))) {
					yRoad = (int) Math.floor(y) + dy - 1;
					break;
				}
			}
		}
		if (!Double.isNaN(ry)) {
			yRoad = (int) ry - 1;
		}
		if (yRoad == Integer.MIN_VALUE) {
			return null;
		}
		for (int k = -80; k <= 80; k++) {
			// A street along z at x = bx + k: its middle line painted every few blocks, asphalt either side.
			int marks = 0;
			int marksH = 0;
			for (int j = -24; j <= 24; j++) {
				if (paint(level, q.set(bx + k, yRoad, bz + j)) && isRoad(level.getBlockState(q.set(bx + k + 2, yRoad, bz + j)))
						&& isRoad(level.getBlockState(q.set(bx + k - 2, yRoad, bz + j)))) {
					marks++;
				}
				if (paint(level, q.set(bx + j, yRoad, bz + k)) && isRoad(level.getBlockState(q.set(bx + j, yRoad, bz + k + 2)))
						&& isRoad(level.getBlockState(q.set(bx + j, yRoad, bz + k - 2)))) {
					marksH++;
				}
			}
			if (marks >= 5) {
				xs.add(bx + k);
			}
			if (marksH >= 5) {
				zs.add(bz + k);
			}
		}
		if (xs.isEmpty() && zs.isEmpty()) {
			return null;
		}
		int n = Math.max(1, Math.max(xs.size(), zs.size()) - 1);
		int[] gx = new int[n + 1];
		int[] gz = new int[n + 1];
		for (int i = 0; i <= n; i++) {
			gx[i] = xs.isEmpty() ? Integer.MIN_VALUE / 2 : xs.get(Math.min(i, xs.size() - 1));
			gz[i] = zs.isEmpty() ? Integer.MIN_VALUE / 2 : zs.get(Math.min(i, zs.size() - 1));
		}
		boolean[] on = new boolean[n * n];
		java.util.Arrays.fill(on, true);
		return new AtlasClient.City(0L, bx, bz, 0, 0, n, gx, gz, on);
	}

	private static boolean paint(ClientLevel level, BlockPos p) {
		Block b = level.getBlockState(p).getBlock();
		return b == Weathered.ROAD_MARK || b == Weathered.ROAD_MARK_WORN
				|| b == net.minecraft.world.level.block.Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE);
	}

	// ------------------------------------------------------------------------------------------------
	// Parked cars

	private static void park(ClientLevel level, Vec3 me) {
		PARKED.entrySet().removeIf(e -> {
			if (e.getValue().position().distanceTo(me) > 72 || e.getValue().isRemoved()) {
				level.removeEntity(e.getValue().getId(), Entity.RemovalReason.DISCARDED);
				e.getValue().discard();
				return true;
			}
			return false;
		});
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		int cx = Mth.floor(me.x);
		int cy = Mth.floor(me.y);
		int cz = Mth.floor(me.z);
		for (int x = cx - 48; x <= cx + 48; x++) {
			for (int z = cz - 48; z <= cz + 48; z++) {
				for (int y = cy - 8; y <= cy + 8; y++) {
					Block b = level.getBlockState(q.set(x, y, z)).getBlock();
					if (b != Weathered.PARKING_X && b != Weathered.PARKING_Z) {
						continue;
					}
					long key = q.asLong();
					if (PARKED.containsKey(key) || TAKEN.contains(key)) {
						continue;
					}
					if (!level.getEntitiesOfClass(com.stasdoto.airdefense.vehicle.VehicleEntity.class,
							new net.minecraft.world.phys.AABB(x - 2, y - 1, z - 2, x + 4, y + 3, z + 4)).isEmpty()) {
						// 2.0: a real car (taken by somebody, or put there) stands in the bay.
						continue;
					}
					long h = mix(key * 0x9E3779B97F4A7C15L);
					boolean alongZ = b == Weathered.PARKING_Z;
					PropEntity p = new PropEntity(ModEntities.PROP, level);
					p.setId(nextId--);
					p.model = (h & 15) == 0 ? "car_taxi" : CARS[(int) Math.floorMod(h >>> 8, (long) CARS.length)];
					double px = x + (alongZ ? 1.0 : 2.0);
					double pz = z + (alongZ ? 2.0 : 1.0);
					double yaw = alongZ ? ((h >>> 20 & 1) == 0 ? 0 : 180) : ((h >>> 20 & 1) == 0 ? 90 : -90);
					p.move(px, y + 1, pz, yaw, 0);
					p.poseO = p.pose;
					p.setOldPosAndRot();
					level.addEntity(p);
					PARKED.put(key, p);
					parked++;
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Moving traffic

	private static void spawn(Minecraft mc, ClientLevel level, Vec3 me, AtlasClient.City town, long hour) {
		int n = town.n();
		int[] gx = town.gx();
		int[] gz = town.gz();
		List<int[]> lines = new ArrayList<>();
		for (int k = 0; k <= n; k++) {
			if (Math.abs(gx[k] + 0.5 - me.x) < 70) {
				lines.add(new int[]{0, gx[k]});
			}
			if (Math.abs(gz[k] + 0.5 - me.z) < 70) {
				lines.add(new int[]{1, gz[k]});
			}
		}
		if (lines.isEmpty()) {
			return;
		}
		int[] line = lines.get(RANDOM.nextInt(lines.size()));
		int axis = line[0];
		int dir = RANDOM.nextBoolean() ? 1 : -1;
		double mine = axis == 0 ? me.z : me.x;
		// From out of sight up the street, driving the player's way (or off from beside him).
		double along = mine - dir * (36 + RANDOM.nextInt(30));
		Drive d = new Drive();
		d.axis = axis;
		d.line = line[1];
		d.dir = dir;
		d.along = along;
		double[] at = lanePoint(d, along);
		double y = roadY(level, at[0], at[1], me.y);
		if (Double.isNaN(y)) {
			return;
		}
		// What runs here: a tram on rails, a trolleybus under wires, a bus on the main streets, cars.
		int bx = Mth.floor(at[0]);
		int bz = Mth.floor(at[1]);
		int by = (int) Math.round(y);
		boolean rails = level.getBlockState(new BlockPos(bx, by - 1, bz)).getBlock() == Weathered.TRAM_TRACK;
		boolean wires = level.getBlockState(new BlockPos(bx, by + 6, bz)).getBlock() == FacadeBlocks.TROLLEY_WIRE;
		int roll = RANDOM.nextInt(100);
		String model;
		if (rails && roll < 45) {
			d.kind = 4;
			model = "tram";
			d.length = 14;
			d.max = 0.32;
		} else if (wires && roll < 35) {
			d.kind = 3;
			model = "trolleybus";
			d.length = 11.6;
			d.max = 0.36;
		} else if (roll < 12) {
			d.kind = 2;
			model = "town_bus";
			d.length = 11;
			d.max = 0.38;
		} else {
			d.kind = roll < 20 ? 1 : 0;
			model = d.kind == 1 ? "car_taxi" : CARS[RANDOM.nextInt(CARS.length)];
			d.length = model.contains("suv") ? 4.7 : model.contains("hatch") ? 3.9 : 4.5;
			d.max = 0.42 + RANDOM.nextDouble() * 0.16;
		}
		// Not into another one.
		for (PropEntity o : ALL) {
			Drive od = DRIVES.get(o);
			if (od != null && od.axis == d.axis && od.line == d.line && od.dir == d.dir && Math.abs(od.along - d.along) < (od.length + d.length) / 2 + 3) {
				return;
			}
		}
		d.y = y;
		PropEntity p = new PropEntity(ModEntities.PROP, level);
		p.setId(nextId--);
		p.model = model;
		p.speed = d.max;
		p.move(at[0], y, at[1], yaw(d.axis, d.dir), 0);
		p.poseO = p.pose;
		p.setOldPosAndRot();
		level.addEntity(p);
		ALL.add(p);
		DRIVES.put(p, d);
		MADE[d.kind]++;
		if (d.kind == 4) {
			level.playLocalSound(p.getX(), p.getY() + 2, p.getZ(), ModSounds.TRAM_BELL, SoundSource.AMBIENT, 1.0f, 1.0f, false);
		}
	}

	/** The point of the right-hand lane at {@code along} (the middle of the lane's block: two from the street's middle). */
	private static double[] lanePoint(Drive d, double along) {
		if (d.axis == 0) {
			return new double[]{d.line + 0.5 - d.dir * 2.0, along};
		}
		return new double[]{along, d.line + 0.5 + d.dir * 2.0};
	}

	private static double yaw(int axis, int dir) {
		// Minecraft's yaw: south (+z) 0, west (-x) 90, north 180, east (+x) -90.
		return axis == 0 ? (dir > 0 ? 0 : 180) : (dir > 0 ? -90 : 90);
	}

	private static boolean isRoad(BlockState s) {
		Block b = s.getBlock();
		return Weathered.ROADWAY.contains(b) || b == com.stasdoto.airdefense.street.StreetBlocks.MANHOLE
				|| b == net.minecraft.world.level.block.Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.GRAY)
				|| b == net.minecraft.world.level.block.Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE);
	}

	/** The top of the road at (x, z) near height {@code near}, or NaN when there is no road there. */
	private static double roadY(ClientLevel level, double x, double z, double near) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		BlockPos p = new BlockPos(bx, 0, bz);
		if (!level.hasChunkAt(p)) {
			return Double.NaN;
		}
		int from = (int) Math.floor(near) + 6;
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int y = from; y >= from - 16; y--) {
			if (isRoad(level.getBlockState(q.set(bx, y, bz))) && level.getBlockState(q.set(bx, y + 1, bz)).getCollisionShape(level, q).isEmpty()) {
				return y + 1;
			}
		}
		return Double.NaN;
	}

	/** One tick of a vehicle. False when it is to go (off the road, out of the loaded land). */
	private static boolean drive(ClientLevel level, PropEntity p, Drive d) {
		p.hold();
		if (d.stopped > 0) {
			d.stopped--;
			p.speed = 0;
			if (d.stopped == 0) {
				if (com.stasdoto.airdefense.client.vehicle.TrafficSounds.voiced(p)) {
					// 2.0: its engine is heard already - only the doors shutting.
					com.stasdoto.airdefense.client.vehicle.TrafficSounds.depart(p);
				} else {
					level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), ModSounds.BUS_GO, SoundSource.AMBIENT, 1.0f, 1.0f, false);
				}
			}
			return true;
		}
		// The sound of it going by (a tram's wheels and bell, a car's tyres) once it comes near.
		if (!d.honked && p.timer == 0 && Minecraft.getInstance().player != null && p.distanceTo(Minecraft.getInstance().player) < 24) {
			p.timer = 1;
			if (d.kind == 4) {
				level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), ModSounds.TRAM_PASS, SoundSource.AMBIENT, 1.2f, 1.0f, false);
				level.playLocalSound(p.getX(), p.getY() + 2, p.getZ(), ModSounds.TRAM_BELL, SoundSource.AMBIENT, 0.9f, 1.0f, false);
			} else if (!com.stasdoto.airdefense.client.vehicle.TrafficSounds.voiced(p)) {
				level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), ModSounds.CITY_CAR, SoundSource.AMBIENT, 0.8f, d.kind >= 2 ? 0.7f : 1.0f, false);
			}
		}
		// Keep the distance to the one ahead, stop for people crossing in front.
		double want = d.max;
		double gapAhead = Double.MAX_VALUE;
		for (PropEntity o : ALL) {
			Drive od = DRIVES.get(o);
			if (o == p || od == null || od.turning || od.axis != d.axis || od.line != d.line || od.dir != d.dir) {
				continue;
			}
			double gap = (od.along - d.along) * d.dir - (od.length + d.length) / 2;
			if (gap > -1 && gap < gapAhead) {
				gapAhead = gap;
			}
		}
		if (gapAhead < 10) {
			want = Math.min(want, Math.max(0, (gapAhead - 2.5) * 0.06));
		}
		if (!d.turning) {
			double[] nose = lanePoint(d, d.along + d.dir * (d.length / 2 + 2.5));
			for (PedestrianEntity w : Pedestrians.near(nose[0], nose[1], 3.0)) {
				if (w.crossing) {
					want = 0;
					if (!d.honked && RANDOM.nextInt(80) == 0) {
						d.honked = true;
						com.stasdoto.airdefense.client.vehicle.TrafficSounds.horn(p, d.kind);
					}
				}
			}
			if (want == 0) {
				waits++;
			}
		}
		// Speed up gently, brake harder.
		p.speed = p.speed < want ? Math.min(want, p.speed + 0.012) : Math.max(want, p.speed - 0.05);
		double sp = p.speed;
		if (d.turning) {
			return turnStep(level, p, d, sp);
		}
		double next = d.along + d.dir * sp;
		// The cross streets: a turn decided once at each; the bus stops: a stop at each for the buses.
		if (!crossing(level, p, d, next)) {
			return false;
		}
		if (d.turning) {
			return true;
		}
		if ((d.kind == 2 || d.kind == 3) && busStop(level, p, d)) {
			return true;
		}
		double[] at = lanePoint(d, next);
		double y = roadY(level, at[0], at[1], d.y);
		if (Double.isNaN(y)) {
			// The road ends here: away it goes (at the town's edge, out of the loaded land).
			return false;
		}
		d.along = next;
		d.y = d.y + Mth.clamp(y - d.y, -0.2, 0.2);
		p.move(at[0], d.y, at[1], yaw(d.axis, d.dir), 0);
		return true;
	}

	/** Passing a cross street: straight on, or a turn (if the street that way is a road) - trams always straight on. */
	private static boolean crossing(ClientLevel level, PropEntity p, Drive d, double next) {
		AtlasClient.City town = townAt(level, p.getX(), p.getZ(), d.y);
		if (town == null) {
			return true;
		}
		int[] cross = d.axis == 0 ? town.gz() : town.gx();
		for (int c : cross) {
			if (c == d.lastCross) {
				continue;
			}
			// The cross street's middle line is at c + 0.5; a right turn starts 3.5 before the lane it ends in, a left one 5.5.
			double rightStart = c + 0.5 - d.dir * 2.0 - d.dir * 3.5;
			double leftStart = c + 0.5 + d.dir * 2.0 - d.dir * 5.5;
			boolean atRight = (d.along - rightStart) * d.dir < 0 && (next - rightStart) * d.dir >= 0;
			boolean atLeft = (d.along - leftStart) * d.dir < 0 && (next - leftStart) * d.dir >= 0;
			if (!atRight && !atLeft) {
				continue;
			}
			int roll = RANDOM.nextInt(100);
			// 2.0: now and then a horn at the crossing (someone cut in, a friend on the pavement).
			if (atLeft && RANDOM.nextInt(30) == 0 && Minecraft.getInstance().player != null && p.distanceTo(Minecraft.getInstance().player) < 48) {
				com.stasdoto.airdefense.client.vehicle.TrafficSounds.horn(p, d.kind);
				horns++;
			}
			boolean right = atRight && roll < 22;
			boolean left = atLeft && roll >= 22 && roll < 38;
			if (atLeft) {
				d.lastCross = c;
			}
			if (d.kind == 4 || !right && !left) {
				continue;
			}
			// Where the turn ends: the right-hand lane of the cross street, heading right (or left).
			int newAxis = 1 - d.axis;
			int sign = right ? 1 : -1;
			// Heading after the turn: the car's right (or left) in the world.
			double fx = d.axis == 0 ? 0 : d.dir;
			double fz = d.axis == 0 ? d.dir : 0;
			double rx = -fz;
			double rz = fx;
			// (Right of heading +z is -x: right = (-fz, fx).)
			double hx = sign * rx;
			double hz = sign * rz;
			int newDir = (int) Math.round(newAxis == 0 ? hz : hx);
			Drive probe = new Drive();
			probe.axis = newAxis;
			probe.line = c;
			probe.dir = newDir;
			double[] ahead = lanePoint(probe, (newAxis == 0 ? p.getZ() : p.getX()) + newDir * 12);
			if (Double.isNaN(roadY(level, ahead[0], ahead[1], d.y))) {
				continue;
			}
			double radius = right ? 3.5 : 5.5;
			double[] at = lanePoint(d, right ? rightStart : leftStart);
			d.turning = true;
			d.sx = at[0];
			d.sz = at[1];
			d.fx = fx;
			d.fz = fz;
			d.radius = radius;
			d.turnSign = sign;
			d.cx = at[0] + hx * radius;
			d.cz = at[1] + hz * radius;
			d.turned = 0;
			d.newAxis = newAxis;
			d.newLine = c;
			d.newDir = newDir;
			d.lastCross = c;
			turns++;
			return true;
		}
		return true;
	}

	private static boolean turnStep(ClientLevel level, PropEntity p, Drive d, double sp) {
		double arc = Math.PI / 2 * d.radius;
		d.turned = Math.min(1, d.turned + sp / arc);
		double th = d.turned * Math.PI / 2;
		// P(th) = C + (S - C) cos th + f R sin th; heading turns from f towards the turn.
		double x = d.cx + (d.sx - d.cx) * Math.cos(th) + d.fx * d.radius * Math.sin(th);
		double z = d.cz + (d.sz - d.cz) * Math.cos(th) + d.fz * d.radius * Math.sin(th);
		double hx = d.fx * Math.cos(th) + (d.cx - d.sx) / d.radius * Math.sin(th);
		double hz = d.fz * Math.cos(th) + (d.cz - d.sz) / d.radius * Math.sin(th);
		double y = roadY(level, x, z, d.y);
		if (!Double.isNaN(y)) {
			d.y = d.y + Mth.clamp(y - d.y, -0.2, 0.2);
		}
		p.move(x, d.y, z, Math.toDegrees(Math.atan2(-hx, hz)), 0);
		if (d.turned >= 1) {
			d.turning = false;
			d.axis = d.newAxis;
			d.line = d.newLine;
			d.dir = d.newDir;
			d.along = d.axis == 0 ? z : x;
			d.lastCross = Integer.MIN_VALUE;
		}
		return true;
	}

	/** A bus at a stop on its side of the street: it stands a while, the people waiting get on. */
	private static boolean busStop(ClientLevel level, PropEntity p, Drive d) {
		for (int[] s : Pedestrians.stops()) {
			long key = BlockPos.asLong(s[0], s[1], s[2]);
			if (key == d.lastStop) {
				continue;
			}
			double sAlong = d.axis == 0 ? s[2] + 0.5 : s[0] + 0.5;
			double sAcross = d.axis == 0 ? s[0] + 0.5 : s[2] + 0.5;
			double lane = d.axis == 0 ? d.line + 0.5 - d.dir * 2.0 : d.line + 0.5 + d.dir * 2.0;
			// The stop stands at the kerb on this lane's side (2-3 blocks out from the lane), the bus's doors by it.
			double side = (sAcross - lane) * (d.axis == 0 ? -d.dir : d.dir);
			if (side < 1.0 || side > 4.0 || Math.abs(sAlong - d.along) > 1.0) {
				continue;
			}
			d.lastStop = key;
			d.stopped = 90 + RANDOM.nextInt(60);
			busStops++;
			com.stasdoto.airdefense.client.vehicle.TrafficSounds.arrive(p);
			Pedestrians.board(s[0] + 0.5, s[2] + 0.5);
			return true;
		}
		return false;
	}

	private static void remove(ClientLevel level, PropEntity p) {
		level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
		p.discard();
		ALL.remove(p);
		DRIVES.remove(p);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	/** For the tests: where the vehicles are and what they are. */
	public static List<String> where() {
		List<String> out = new ArrayList<>();
		for (PropEntity p : ALL) {
			out.add(String.format(java.util.Locale.ROOT, "%s %.1f %.1f %.1f", p.model, p.getX(), p.getY(), p.getZ()));
		}
		return out;
	}
}
