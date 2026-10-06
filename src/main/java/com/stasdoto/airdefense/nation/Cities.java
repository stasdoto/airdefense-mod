package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The world's cities (stage R8), planned from the seed: the world is cut into country cells of {@link #CELL} blocks;
 * each cell has a capital (a large city) and two more towns 650-850 blocks from it, all joined by asphalt roads, and
 * the capitals are joined to the capitals of the neighbouring cells. A city is a square grid of blocks 32 metres
 * apart: streets between them, buildings on the blocks - towers and offices in the middle, panel blocks around them,
 * cottages and garages at the edge, a city hall on the central square. Everything here is worked out from the seed
 * alone (no chunks needed), the same on every thread and every time, so {@link CityGen} can build each chunk on its
 * own while the world generates.
 */
public final class Cities {
	public static final int CELL = 2048;
	public static final int PITCH = 32;
	/** Streets: 5 blocks of asphalt centred on each grid line; then a pavement. */
	public static final int STREET_HALF = 2;
	/** Ground blended from the city's level back to nature over this many blocks. */
	public static final int MARGIN = 14;
	public static final int ROAD_HALF = 3;

	public enum Size {
		SMALL(3, 100, 260, 10), MEDIUM(5, 300, 620, 18), LARGE(7, 650, 1000, 28);

		/** Blocks per side. */
		public final int n;
		public final int popMin;
		public final int popMax;
		/** Villagers you meet in the streets (the rest of the people are only a number). */
		public final int villagers;

		Size(int n, int popMin, int popMax, int villagers) {
			this.n = n;
			this.popMin = popMin;
			this.popMax = popMax;
			this.villagers = villagers;
		}

		public int half() {
			return n * PITCH / 2;
		}
	}

	/** Where the terrain is, before anything is built: the top block of each column, and the sea. */
	public interface Terrain {
		int top(int x, int z);

		int sea();
	}

	public static Terrain terrain(ServerLevel level) {
		var src = level.getChunkSource();
		var gen = src.getGenerator();
		var rs = src.randomState();
		int sea = gen.getSeaLevel();
		return new Terrain() {
			@Override
			public int top(int x, int z) {
				return gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - 1;
			}

			@Override
			public int sea() {
				return sea;
			}
		};
	}

	/** One city of the plan. */
	public static final class City {
		public final int cx;
		public final int cz;
		/** 0 = the capital. */
		public final int index;
		public final int x;
		public final int z;
		/** The ground level (the top block: streets and floors are at this height). */
		public final int base;
		public final Size size;
		public final int citizens;
		/** The country's colour (a dye id). */
		public final int color;
		public final long seed;
		private volatile List<Building> buildings;

		City(int cx, int cz, int index, int x, int z, int base, Size size, int citizens, int color, long seed) {
			this.cx = cx;
			this.cz = cz;
			this.index = index;
			this.x = x;
			this.z = z;
			this.base = base;
			this.size = size;
			this.citizens = citizens;
			this.color = color;
			this.seed = seed;
		}

		public boolean capital() {
			return index == 0;
		}

		public long key() {
			return cellKey(cx, cz) * 4 + index;
		}

		public int half() {
			return size.half();
		}

		/** Inside the city square, streets on its edge included. */
		public boolean inside(int px, int pz) {
			int h = half() + STREET_HALF;
			return Math.abs(px - x) <= h && Math.abs(pz - z) <= h;
		}

		/** Distance outside the city square (0 inside). */
		public int outside(int px, int pz) {
			int h = half() + STREET_HALF;
			return Math.max(0, Math.max(Math.abs(px - x), Math.abs(pz - z)) - h);
		}

		/** The bell of the central square: the settlement's centre. */
		public BlockPos bell() {
			return new BlockPos(x + 12, base + 1, z + 11);
		}

		/** The settlement's reach (a circle round the square). */
		public int radius() {
			return (int) (half() * 1.2) + 4;
		}

		/** Every building of the city as planned (ids are only their order). */
		public List<Building> buildings() {
			List<Building> b = buildings;
			if (b == null) {
				b = Collections.unmodifiableList(layout(this));
				buildings = b;
			}
			return b;
		}
	}

	/** A road from {@code (x0, z0)} to {@code (x1, z1)} with its height every {@link #STEP} blocks. */
	public static final class Road {
		public static final int STEP = 16;
		public final int x0;
		public final int z0;
		public final int x1;
		public final int z1;
		public final double length;
		public final double ux;
		public final double uz;
		public final float[] heights;
		public final int minX;
		public final int maxX;
		public final int minZ;
		public final int maxZ;

		Road(int x0, int z0, int x1, int z1, float[] heights) {
			this.x0 = x0;
			this.z0 = z0;
			this.x1 = x1;
			this.z1 = z1;
			this.length = Math.max(1, Math.hypot(x1 - x0, z1 - z0));
			this.ux = (x1 - x0) / length;
			this.uz = (z1 - z0) / length;
			this.heights = heights;
			int pad = ROAD_HALF + 2;
			minX = Math.min(x0, x1) - pad;
			maxX = Math.max(x0, x1) + pad;
			minZ = Math.min(z0, z1) - pad;
			maxZ = Math.max(z0, z1) + pad;
		}

		/** Distance along the road of the point nearest to (px, pz). */
		public double along(double px, double pz) {
			return (px - x0) * ux + (pz - z0) * uz;
		}

		/** Distance from the centre line (signed). */
		public double across(double px, double pz) {
			return (px - x0) * -uz + (pz - z0) * ux;
		}

		public float height(double t) {
			double i = Math.max(0, Math.min(heights.length - 1, t / STEP));
			int a = (int) Math.floor(i);
			int b = Math.min(heights.length - 1, a + 1);
			double f = i - a;
			return (float) (heights[a] * (1 - f) + heights[b] * f);
		}
	}

	private Cities() {
	}

	// ------------------------------------------------------------------------------------------------
	// The plan, cached per world

	private static volatile long cacheSeed = Long.MIN_VALUE;
	private static final ConcurrentHashMap<Long, List<City>> CITIES = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Long, List<Road>> ROADS = new ConcurrentHashMap<>();

	public static long cellKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private static void checkSeed(long seed) {
		if (cacheSeed != seed) {
			synchronized (Cities.class) {
				if (cacheSeed != seed) {
					CITIES.clear();
					ROADS.clear();
					CityGen.clearCache();
					cacheSeed = seed;
				}
			}
		}
	}

	public static List<City> cities(long seed, Terrain t, int cx, int cz) {
		checkSeed(seed);
		return CITIES.computeIfAbsent(cellKey(cx, cz), k -> planCell(seed, t, cx, cz));
	}

	public static List<City> citiesAround(long seed, Terrain t, int x, int z) {
		return cities(seed, t, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
	}

	/** The city whose square (with its margin) holds this point, or null. */
	@Nullable
	public static City cityAt(long seed, Terrain t, int x, int z, int margin) {
		for (City c : citiesAround(seed, t, x, z)) {
			if (c.outside(x, z) <= margin) {
				return c;
			}
		}
		return null;
	}

	/** Roads that start in this cell (towns to their capital, the capital to the capitals east and south). */
	public static List<Road> roads(long seed, Terrain t, int cx, int cz) {
		checkSeed(seed);
		return ROADS.computeIfAbsent(cellKey(cx, cz), k -> planRoads(seed, t, cx, cz));
	}

	/** Every road that may pass through the cell of (x, z): its own, and those coming in from the west and the north. */
	public static List<Road> roadsNear(long seed, Terrain t, int x, int z) {
		int cx = Math.floorDiv(x, CELL);
		int cz = Math.floorDiv(z, CELL);
		List<Road> out = new ArrayList<>(roads(seed, t, cx, cz));
		out.addAll(roads(seed, t, cx - 1, cz));
		out.addAll(roads(seed, t, cx, cz - 1));
		return out;
	}

	// ------------------------------------------------------------------------------------------------
	// Planning a cell

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	private static List<City> planCell(long seed, Terrain t, int cx, int cz) {
		long cellSeed = mix(seed ^ 0x43495459L ^ cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL);
		Random r = new Random(cellSeed);
		int color = 1 + r.nextInt(15);
		int x0 = cx * CELL;
		int z0 = cz * CELL;
		List<City> out = new ArrayList<>();
		// The capital somewhere in the middle part of the cell.
		int[] cap = bestSite(t, r, Size.LARGE, x0 + 560, z0 + 560, x0 + CELL - 560, z0 + CELL - 560, 10);
		if (cap == null) {
			return out;
		}
		out.add(new City(cx, cz, 0, cap[0], cap[1], cap[2], Size.LARGE, Size.LARGE.popMin + 100 + r.nextInt(Size.LARGE.popMax - Size.LARGE.popMin - 99),
				color, r.nextLong()));
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int k = 1; k <= 2; k++) {
			Size size = r.nextInt(100) < 55 ? Size.MEDIUM : Size.SMALL;
			int[] best = null;
			for (int tries = 0; tries < 4 && best == null; tries++) {
				double a = a0 + (k == 1 ? 0 : Math.PI * (0.65 + r.nextDouble() * 0.7)) + (r.nextDouble() - 0.5) * 0.5;
				double d = 650 + r.nextDouble() * 200;
				int tx = cap[0] + (int) (Math.cos(a) * d);
				int tz = cap[1] + (int) (Math.sin(a) * d);
				int lim = size.half() + MARGIN + 60;
				tx = Math.max(x0 + lim, Math.min(x0 + CELL - lim, tx));
				tz = Math.max(z0 + lim, Math.min(z0 + CELL - lim, tz));
				if (Math.hypot(tx - cap[0], tz - cap[1]) < 450) {
					continue;
				}
				best = bestSite(t, r, size, tx - 80, tz - 80, tx + 80, tz + 80, 6);
			}
			if (best != null) {
				boolean clash = false;
				for (City c : out) {
					if (Math.max(Math.abs(c.x - best[0]), Math.abs(c.z - best[1])) < c.half() + size.half() + 120) {
						clash = true;
					}
				}
				if (!clash) {
					out.add(new City(cx, cz, k, best[0], best[1], best[2], size, size.popMin + r.nextInt(size.popMax - size.popMin + 1), color,
							r.nextLong()));
				}
			}
		}
		return out;
	}

	/** The flattest dry spot among a few tries in the rectangle: {x, z, ground level}, or null if all are under water. */
	@Nullable
	private static int[] bestSite(Terrain t, Random r, Size size, int ax, int az, int bx, int bz, int tries) {
		int h = size.half() + 4;
		int[] best = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < tries; i++) {
			int x = ax + r.nextInt(Math.max(1, bx - ax));
			int z = az + r.nextInt(Math.max(1, bz - az));
			int[] hs = new int[25];
			int wet = 0;
			for (int gx = 0; gx < 5; gx++) {
				for (int gz = 0; gz < 5; gz++) {
					int y = t.top(x - h + gx * h / 2, z - h + gz * h / 2);
					hs[gx * 5 + gz] = y;
					if (y < t.sea()) {
						wet++;
					}
				}
			}
			if (wet > 4) {
				continue;
			}
			int[] sorted = hs.clone();
			Arrays.sort(sorted);
			int median = sorted[12];
			double spread = 0;
			for (int y : hs) {
				spread += Math.abs(y - median);
			}
			double score = spread + wet * 30 + Math.max(0, median - t.sea() - 40) * 2;
			if (score < bestScore) {
				bestScore = score;
				best = new int[]{x, z, Math.max(t.sea(), median)};
			}
		}
		return best;
	}

	private static List<Road> planRoads(long seed, Terrain t, int cx, int cz) {
		List<Road> out = new ArrayList<>();
		List<City> here = cities(seed, t, cx, cz);
		if (here.isEmpty() || here.getFirst().index != 0) {
			return out;
		}
		City cap = here.getFirst();
		for (City c : here) {
			if (c != cap) {
				out.add(road(t, cap, c));
			}
		}
		for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
			List<City> next = cities(seed, t, cx + d[0], cz + d[1]);
			if (!next.isEmpty() && next.getFirst().index == 0) {
				out.add(road(t, cap, next.getFirst()));
			}
		}
		return out;
	}

	/** From the edge of one city square to the edge of the other, following the land, smoothed. */
	private static Road road(Terrain t, City a, City b) {
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		double len = Math.hypot(dx, dz);
		double ux = dx / len;
		double uz = dz / len;
		double m = Math.max(Math.abs(ux), Math.abs(uz));
		double ta = (a.half() + STREET_HALF) / m;
		double tb = (b.half() + STREET_HALF) / m;
		int x0 = (int) Math.round(a.x + ux * ta);
		int z0 = (int) Math.round(a.z + uz * ta);
		int x1 = (int) Math.round(b.x - ux * tb);
		int z1 = (int) Math.round(b.z - uz * tb);
		double l = Math.hypot(x1 - x0, z1 - z0);
		int n = Math.max(2, (int) Math.ceil(l / Road.STEP) + 1);
		float[] hs = new float[n];
		for (int i = 0; i < n; i++) {
			double s = Math.min(l, i * Road.STEP) / Math.max(1, l);
			int px = (int) Math.round(x0 + (x1 - x0) * s);
			int pz = (int) Math.round(z0 + (z1 - z0) * s);
			hs[i] = Math.max(t.sea() + 1, t.top(px, pz));
		}
		// Smooth it out, then ease into each city's level.
		for (int pass = 0; pass < 4; pass++) {
			float[] o = hs.clone();
			for (int i = 1; i < n - 1; i++) {
				hs[i] = (o[i - 1] + 2 * o[i] + o[i + 1]) / 4f;
			}
		}
		for (int i = 0; i < n; i++) {
			double s = i * (double) Road.STEP;
			double fa = Math.max(0, 1 - s / 64.0);
			double fb = Math.max(0, 1 - (l - s) / 64.0);
			hs[i] = (float) (hs[i] * (1 - fa - fb) + a.base * fa + b.base * fb);
			if (fa + fb > 1) {
				hs[i] = (float) ((a.base * fa + b.base * fb) / (fa + fb));
			}
		}
		hs[0] = a.base;
		hs[n - 1] = b.base;
		return new Road(x0, z0, x1, z1, hs);
	}

	// ------------------------------------------------------------------------------------------------
	// The buildings of a city

	/** One way to fill a city block. */
	private record Lot(BuildingType a, BuildingType b, boolean quad) {
		static Lot one(BuildingType t) {
			return new Lot(t, null, false);
		}

		static Lot two(BuildingType a, BuildingType b) {
			return new Lot(a, b, false);
		}

		static Lot cottages() {
			return new Lot(BuildingType.COTTAGE, null, true);
		}
	}

	private static Lot pick(Random r, Object... weighted) {
		int total = 0;
		for (int i = 1; i < weighted.length; i += 2) {
			total += (Integer) weighted[i];
		}
		int roll = r.nextInt(total);
		for (int i = 0; i < weighted.length; i += 2) {
			roll -= (Integer) weighted[i + 1];
			if (roll < 0) {
				return (Lot) weighted[i];
			}
		}
		return (Lot) weighted[0];
	}

	private static Lot fill(Size size, int ring, Random r) {
		BuildingType P5 = BuildingType.PANEL5;
		BuildingType P9 = BuildingType.PANEL9;
		BuildingType SH = BuildingType.SHOP;
		BuildingType GA = BuildingType.GARAGES;
		return switch (size) {
			case LARGE -> switch (ring) {
				case 1 -> pick(r, Lot.one(BuildingType.TOWER), 4, Lot.one(BuildingType.OFFICE), 3, Lot.one(P9), 3, Lot.two(P5, SH), 1);
				case 2 -> pick(r, Lot.one(P9), 4, Lot.two(P5, P5), 2, Lot.two(P5, SH), 2, Lot.one(BuildingType.OFFICE), 1, Lot.one(BuildingType.PARK), 1);
				default -> pick(r, Lot.cottages(), 7, Lot.two(GA, GA), 1, Lot.two(P5, P5), 2, Lot.two(GA, P5), 1);
			};
			case MEDIUM -> ring == 1
					? pick(r, Lot.one(P9), 3, Lot.two(P5, SH), 2, Lot.two(P5, P5), 2, Lot.one(BuildingType.OFFICE), 1, Lot.one(BuildingType.PARK), 1)
					: pick(r, Lot.cottages(), 7, Lot.two(GA, P5), 1, Lot.two(P5, SH), 1);
			case SMALL -> pick(r, Lot.cottages(), 6, Lot.two(P5, SH), 1, Lot.two(GA, P5), 1);
		};
	}

	private static List<Building> layout(City c) {
		Random r = new Random(c.seed);
		int n = c.size.n;
		int mid = n / 2;
		List<int[]> blocks = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i != mid || j != mid) {
					blocks.add(new int[]{i, j, Math.max(Math.abs(i - mid), Math.abs(j - mid))});
				}
			}
		}
		Collections.shuffle(blocks, r);
		Lot[] lots = new Lot[n * n];
		// What every town has: a gas station and a logistics hub at the edge; bigger towns a school, a hospital, an
		// army base (barracks and a hangar).
		List<Lot> needOuter = new ArrayList<>(List.of(Lot.one(BuildingType.GAS_STATION), Lot.one(BuildingType.LOGISTICS_HUB)));
		List<Lot> needAny = new ArrayList<>();
		if (c.size != Size.SMALL) {
			needOuter.add(Lot.one(BuildingType.HANGAR));
			needOuter.add(Lot.two(BuildingType.BARRACKS, BuildingType.WAREHOUSE));
			needAny.add(Lot.one(BuildingType.SCHOOL));
			needAny.add(Lot.two(BuildingType.HOSPITAL, BuildingType.SHOP));
		} else {
			needAny.add(Lot.two(BuildingType.SCHOOL, BuildingType.GARAGES));
		}
		if (c.size == Size.LARGE) {
			needAny.add(Lot.one(BuildingType.PARK));
			needAny.add(Lot.one(BuildingType.SCHOOL));
			needOuter.add(Lot.two(BuildingType.HOSPITAL, BuildingType.WAREHOUSE));
		}
		for (int[] b : blocks) {
			if (b[2] == mid && !needOuter.isEmpty()) {
				lots[b[0] * n + b[1]] = needOuter.removeFirst();
			}
		}
		for (int[] b : blocks) {
			if (lots[b[0] * n + b[1]] == null && b[2] < mid && !needAny.isEmpty()) {
				lots[b[0] * n + b[1]] = needAny.removeFirst();
			}
		}
		for (int[] b : blocks) {
			if (lots[b[0] * n + b[1]] == null && !needAny.isEmpty()) {
				lots[b[0] * n + b[1]] = needAny.removeFirst();
			}
		}
		List<Building> out = new ArrayList<>();
		int y = c.base;
		// The city hall on the central square, its front to the south.
		Building hall = new Building(out.size(), BuildingType.CITY_HALL, new BlockPos(c.x, y, c.z + 12), Direction.NORTH, true);
		hall.variant = r.nextInt(97);
		out.add(hall);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == mid && j == mid) {
					continue;
				}
				int ring = Math.max(Math.abs(i - mid), Math.abs(j - mid));
				Lot lot = lots[i * n + j] != null ? lots[i * n + j] : fill(c.size, ring, r);
				int bx = c.x + (i - mid) * PITCH;
				int bz = c.z + (j - mid) * PITCH;
				if (lot.quad) {
					BuildingType[] small = {BuildingType.COTTAGE, BuildingType.COTTAGE, BuildingType.HOUSE, BuildingType.SMALL_HOUSE};
					for (int q = 0; q < 4; q++) {
						int sx = q % 2 == 0 ? -7 : 7;
						boolean south = q < 2;
						add(out, r, small[r.nextInt(small.length)], bx + sx, y, bz + (south ? 12 : -12), south ? Direction.NORTH : Direction.SOUTH);
					}
				} else if (lot.b != null) {
					add(out, r, lot.a, bx, y, bz + 12, Direction.NORTH);
					add(out, r, lot.b, bx, y, bz - 12, Direction.SOUTH);
				} else {
					Direction f = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(r.nextLong()));
					add(out, r, lot.a, bx - f.getStepX() * 12, y, bz - f.getStepZ() * 12, f);
				}
			}
		}
		return out;
	}

	private static void add(List<Building> out, Random r, BuildingType type, int x, int y, int z, Direction facing) {
		Building b = new Building(out.size(), type, new BlockPos(x, y, z), facing, true);
		b.variant = r.nextInt(97);
		out.add(b);
	}
}
