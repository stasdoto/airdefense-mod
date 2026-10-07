package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The hamlets round a city (1.22): two to four small villages 100-220 blocks out of town, each a handful of timber
 * and stone houses round a little square with a well and a bell, a farm (barn and silo), fields of wheat, carrots,
 * potatoes and beets, a paddock with animals, dirt paths between the houses and a dirt road into town. Unlike the city,
 * a hamlet does not level the land: each house stands on its own pad at the height of the ground under it. All of it is
 * worked out from the seed, like the cities.
 */
public final class Hamlets {
	/** What a field grows (or a paddock with animals). */
	public static final int WHEAT = 0;
	public static final int CARROTS = 1;
	public static final int POTATOES = 2;
	public static final int BEETS = 3;
	public static final int PADDOCK = 4;

	/** A rectangle of ground levelled to one height. */
	public record Pad(int x0, int z0, int x1, int z1, int y) {
		/** How far (x, z) is outside the rectangle (0 inside). */
		public int out(int x, int z) {
			return Math.max(0, Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1)));
		}
	}

	/** A field: its ground, what grows on it, which way the water channel runs (true = along x). */
	public record Field(Pad pad, int crop, boolean alongX, Direction gate) {
	}

	public static final class Hamlet {
		public final Cities.City city;
		public final int index;
		public final int x;
		public final int z;
		public final int base;
		public final List<Building> buildings = new ArrayList<>();
		/** Ground levelled for each building (same order). */
		public final List<Pad> pads = new ArrayList<>();
		public final List<Field> fields = new ArrayList<>();
		/** Columns of the dirt paths between the houses. */
		public final Set<Long> paths = new HashSet<>();
		public Cities.Road road;
		public int minX;
		public int maxX;
		public int minZ;
		public int maxZ;

		Hamlet(Cities.City city, int index, int x, int z, int base) {
			this.city = city;
			this.index = index;
			this.x = x;
			this.z = z;
			this.base = base;
		}

		/** The settlement key (unique among hamlets). */
		public long key() {
			return city.key() * 8 + index;
		}

		public BlockPos bell() {
			return new BlockPos(x + 3, base + 1, z);
		}

		/** The little square round the well. */
		public static final int SQUARE = 6;

		public boolean near(int px, int pz, int margin) {
			return px >= minX - margin && px <= maxX + margin && pz >= minZ - margin && pz <= maxZ + margin;
		}

		/** Seeded per hamlet. */
		public long seed() {
			return city.seed ^ (index + 1) * 0x9E3779B97F4A7C15L;
		}
	}

	/** For the tests: sites turned down for water, slope, a road, another hamlet. */
	public static final int[] REJECTED = new int[4];

	private Hamlets() {
	}

	static List<Hamlet> plan(long seed, Cities.Terrain t, Cities.City c) {
		Random r = new Random(c.seed ^ 0x4A4D4C3EL);
		int count = c.size == Cities.Size.LARGE ? 3 + r.nextInt(2) : 2 + r.nextInt(2);
		List<Cities.Road> main = Cities.mainRoadsNear(seed, t, c.x, c.z);
		List<Hamlet> out = new ArrayList<>();
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int k = 0; k < count; k++) {
			for (int tries = 0; tries < 16; tries++) {
				double a = a0 + k * Math.PI * 2 / count + (r.nextDouble() - 0.5) * (0.9 + tries * 0.1);
				int dist = c.half() + 90 + r.nextInt(130);
				int hx = c.x + (int) Math.round(Math.cos(a) * dist);
				int hz = c.z + (int) Math.round(Math.sin(a) * dist);
				int y = t.top(hx, hz);
				if (y <= t.sea()) {
					REJECTED[0]++;
					continue;
				}
				int lo = y;
				int hi = y;
				int wet = 0;
				for (int[] d : new int[][]{{-28, 0}, {28, 0}, {0, -28}, {0, 28}, {-20, -20}, {20, 20}, {-20, 20}, {20, -20}}) {
					int yy = t.top(hx + d[0], hz + d[1]);
					lo = Math.min(lo, yy);
					hi = Math.max(hi, yy);
					if (yy < t.sea()) {
						wet++;
					}
				}
				if (hi - lo > 22 + tries || wet > 2) {
					REJECTED[1]++;
					continue;
				}
				boolean clash = false;
				for (Cities.Road road : main) {
					double along = road.along(hx, hz);
					if (along > -60 && along < road.length + 60 && Math.abs(road.across(hx, hz)) < 70) {
						clash = true;
						REJECTED[2]++;
					}
				}
				for (Hamlet h : out) {
					if (Math.hypot(h.x - hx, h.z - hz) < 130) {
						clash = true;
						REJECTED[3]++;
					}
				}
				if (clash) {
					continue;
				}
				Hamlet h = new Hamlet(c, out.size(), hx, hz, Math.max(t.sea() + 1, y));
				layout(t, h, new Random(h.seed()));
				out.add(h);
				break;
			}
		}
		return out;
	}

	private static Direction towards(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The footprint of a building with its yard round it (and the doorstep in front). */
	private static Pad footprint(Building b, int margin, int y) {
		int hw = b.type.halfWidth() + margin;
		BlockPos a = b.at(-hw, 0, -2 - margin);
		BlockPos bb = b.at(hw, 0, b.type.depth - 1 + margin);
		return new Pad(Math.min(a.getX(), bb.getX()), Math.min(a.getZ(), bb.getZ()), Math.max(a.getX(), bb.getX()), Math.max(a.getZ(), bb.getZ()), y);
	}

	private static boolean overlaps(Pad a, Pad b, int gap) {
		return a.x0 <= b.x1 + gap && b.x0 <= a.x1 + gap && a.z0 <= b.z1 + gap && b.z0 <= a.z1 + gap;
	}

	private static boolean onRoad(Cities.Road road, Pad p, int gap) {
		for (int x = p.x0; x <= p.x1; x += 2) {
			for (int z = p.z0; z <= p.z1; z += 2) {
				double along = road.along(x + 0.5, z + 0.5);
				if (along >= -2 && along <= road.length + 2 && Math.abs(road.across(x + 0.5, z + 0.5)) <= road.half + gap) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean free(Hamlet h, Pad p, List<Pad> taken, int gap) {
		Pad square = new Pad(h.x - Hamlet.SQUARE, h.z - Hamlet.SQUARE, h.x + Hamlet.SQUARE, h.z + Hamlet.SQUARE, h.base);
		if (overlaps(p, square, gap)) {
			return false;
		}
		for (Pad o : taken) {
			if (overlaps(p, o, gap)) {
				return false;
			}
		}
		return !onRoad(h.road, p, 3);
	}

	private static int ground(Cities.Terrain t, Hamlet h, int x, int z) {
		int y = t.top(x, z);
		return Math.max(t.sea() + 1, Math.max(h.base - 5, Math.min(h.base + 5, y)));
	}

	private static void layout(Cities.Terrain t, Hamlet h, Random r) {
		Cities.City c = h.city;
		// The road into town from the edge of the square.
		double dx = c.x - h.x;
		double dz = c.z - h.z;
		double len = Math.hypot(dx, dz);
		int sx = h.x + (int) Math.round(dx / len * (Hamlet.SQUARE + 1));
		int sz = h.z + (int) Math.round(dz / len * (Hamlet.SQUARE + 1));
		int[] e = Cities.edge(c, h.x, h.z);
		double[] out = Cities.outward(c, e);
		// A narrow asphalt country road, winding over the land into town.
		h.road = Cities.between(t, h.seed(), sx, sz, h.base, dx / len, dz / len, e[0], e[1], c.base, out[0], out[1], Cities.COUNTRY_HALF, false);
		List<Pad> taken = new ArrayList<>();
		// Houses in a loose ring, doors to the square.
		int houses = 5 + r.nextInt(5);
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < houses; i++) {
			for (int tries = 0; tries < 6; tries++) {
				double a = a0 + i * Math.PI * 2 / houses + (r.nextDouble() - 0.5) * 0.7;
				int dist = 13 + r.nextInt(11) + tries * 2;
				int px = h.x + (int) Math.round(Math.cos(a) * dist);
				int pz = h.z + (int) Math.round(Math.sin(a) * dist);
				Direction facing = towards(px - h.x, pz - h.z);
				BuildingType type = r.nextInt(5) < 3 ? BuildingType.SMALL_HOUSE : BuildingType.HOUSE;
				if (tryPlace(t, h, r, type, px, pz, facing, taken, 2)) {
					break;
				}
			}
		}
		// The farm, a bit further out.
		for (int tries = 0; tries < 10; tries++) {
			double a = r.nextDouble() * Math.PI * 2;
			int dist = 26 + r.nextInt(8) + tries * 2;
			int px = h.x + (int) Math.round(Math.cos(a) * dist);
			int pz = h.z + (int) Math.round(Math.sin(a) * dist);
			if (tryPlace(t, h, r, BuildingType.FARM, px, pz, towards(px - h.x, pz - h.z), taken, 3)) {
				break;
			}
		}
		// Fields round it all, and a paddock.
		int fields = 3 + r.nextInt(3);
		for (int i = 0; i < fields; i++) {
			for (int tries = 0; tries < 8; tries++) {
				double a = r.nextDouble() * Math.PI * 2;
				int dist = 34 + r.nextInt(14) + tries * 3;
				int cx = h.x + (int) Math.round(Math.cos(a) * dist);
				int cz = h.z + (int) Math.round(Math.sin(a) * dist);
				boolean alongX = r.nextBoolean();
				int w = i == 0 ? 9 : 9;
				int l = i == 0 ? 11 : 11 + r.nextInt(8);
				int hx = alongX ? l / 2 : w / 2;
				int hz = alongX ? w / 2 : l / 2;
				Pad p = new Pad(cx - hx, cz - hz, cx + hx, cz + hz, ground(t, h, cx, cz));
				if (!free(h, p, taken, 3)) {
					continue;
				}
				taken.add(p);
				int crop = i == 0 ? PADDOCK : r.nextInt(4);
				h.fields.add(new Field(p, crop, alongX, towards(h.x - cx, h.z - cz)));
				break;
			}
		}
		// Paths from every door to the square.
		for (Building b : h.buildings) {
			BlockPos d = b.at(0, 0, -2);
			line(h.paths, d.getX(), d.getZ(), h.x, h.z);
		}
		for (Field f : h.fields) {
			Pad p = f.pad;
			int gx = f.gate.getAxis() == Direction.Axis.X ? (f.gate == Direction.EAST ? p.x1 + 1 : p.x0 - 1) : (p.x0 + p.x1) / 2;
			int gz = f.gate.getAxis() == Direction.Axis.Z ? (f.gate == Direction.SOUTH ? p.z1 + 1 : p.z0 - 1) : (p.z0 + p.z1) / 2;
			line(h.paths, gx + f.gate.getStepX(), gz + f.gate.getStepZ(), h.x, h.z);
		}
		int ax = h.x - Hamlet.SQUARE;
		int bx = h.x + Hamlet.SQUARE;
		int az = h.z - Hamlet.SQUARE;
		int bz = h.z + Hamlet.SQUARE;
		for (Pad p : taken) {
			ax = Math.min(ax, p.x0);
			bx = Math.max(bx, p.x1);
			az = Math.min(az, p.z0);
			bz = Math.max(bz, p.z1);
		}
		h.minX = ax - 6;
		h.maxX = bx + 6;
		h.minZ = az - 6;
		h.maxZ = bz + 6;
	}

	private static boolean tryPlace(Cities.Terrain t, Hamlet h, Random r, BuildingType type, int px, int pz, Direction facing, List<Pad> taken,
			int margin) {
		Building probe = new Building(0, type, new BlockPos(px, 0, pz), facing, true);
		BlockPos mid = probe.at(0, 0, type.depth / 2);
		int y = ground(t, h, mid.getX(), mid.getZ());
		Building b = new Building(h.buildings.size(), type, new BlockPos(px, y, pz), facing, true);
		Pad p = footprint(b, margin, y);
		if (!free(h, p, taken, 1)) {
			return false;
		}
		b.variant = type == BuildingType.FARM ? r.nextInt(97) : ModernDesigns.RUSTIC + r.nextInt(97);
		h.buildings.add(b);
		h.pads.add(p);
		taken.add(p);
		return true;
	}

	/** A one-block path along a straight line, stopping at the square. */
	private static void line(Set<Long> out, int x0, int z0, int x1, int z1) {
		int dx = Math.abs(x1 - x0);
		int dz = Math.abs(z1 - z0);
		int sx = x0 < x1 ? 1 : -1;
		int sz = z0 < z1 ? 1 : -1;
		int err = dx - dz;
		int x = x0;
		int z = z0;
		for (int i = 0; i < 200; i++) {
			if (Math.abs(x - x1) <= Hamlet.SQUARE && Math.abs(z - z1) <= Hamlet.SQUARE) {
				return;
			}
			out.add(BlockPos.asLong(x, 0, z));
			int e2 = 2 * err;
			if (e2 > -dz) {
				err -= dz;
				x += sx;
			} else if (e2 < dx) {
				err += dx;
				z += sz;
			}
		}
	}
}
