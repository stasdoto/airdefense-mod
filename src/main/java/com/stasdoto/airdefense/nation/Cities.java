package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	public static final int PITCH = 36;
	/** Streets (1.25): 7 blocks of asphalt centred on each grid line - a lane each way - then a pavement two blocks wide. */
	public static final int STREET_HALF = 3;
	/** Ground blended from the city's level back to nature over this many blocks. */
	public static final int MARGIN = 14;
	public static final int ROAD_HALF = 3;
	/** Buildings stand this far from the middle of their block: a strip of front garden between them and the pavement. */
	public static final int FRONT = 11;

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

		/** About how far a city of this size reaches from its centre. */
		public int half() {
			return (n + 2) * PITCH / 2;
		}
	}

	/** Where the terrain is, before anything is built: the top block of each column, and the sea. */
	public interface Terrain {
		int top(int x, int z);

		int sea();

		/** 1.28: the floor under the water (the top of the ground where it is dry). */
		default int floor(int x, int z) {
			return top(x, z);
		}

		/** 1.28: the climate at a spot ({@link CityStyle#TEMPERATE}, COLD or DRY), from the biome there. */
		default int climate(int x, int z) {
			return CityStyle.TEMPERATE;
		}
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

			@Override
			public int floor(int x, int z) {
				return gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1;
			}

			@Override
			public int climate(int x, int z) {
				var b = gen.getBiomeSource().createUncachedResolver(rs).getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x),
						net.minecraft.core.QuartPos.fromBlock(Math.max(top(x, z), sea)), net.minecraft.core.QuartPos.fromBlock(z)).value();
				float temp = b.getBaseTemperature();
				if (temp >= 1.5f && !b.hasPrecipitation()) {
					return CityStyle.DRY;
				}
				return temp < 0.3f ? CityStyle.COLD : CityStyle.TEMPERATE;
			}
		};
	}

	/**
	 * 1.28: false in worlds started before the styles came in (see Politics): their towns keep the classic look.
	 * {@link #FORCE_STYLE}: for the automated test, the style of every country planned from now on.
	 */
	public static volatile boolean styles = true;
	@Nullable
	public static volatile CityStyle FORCE_STYLE;
	/**
	 * 1.35: false in worlds started before the town outlines came in (their roads and villages were planned round the
	 * old blobs); {@link #FORCE_FORM}: for the automated test, the outline of every town planned from now on.
	 */
	public static volatile boolean shapes = true;
	/** 1.36: parks may be playgrounds, sports grounds or a stadium (worlds started since; older parks are half built). */
	public static volatile boolean parks = true;
	/** 1.37: shelter entrances and signs in the towns (worlds started since). */
	public static volatile boolean shelters = true;
	/** 1.39: the railways between the capitals; roundabouts where the highways come into the towns, lamps on the bridges (worlds started since). */
	public static volatile boolean railways = true;
	/** 1.40: the capitals' airports (worlds started since). */
	public static volatile boolean airports = true;
	@Nullable
	public static volatile CityForm FORCE_FORM;

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
		/** 1.28: how the town looks. */
		public final CityStyle style;
		private volatile List<Building> buildings;

		City(int cx, int cz, int index, int x, int z, int base, Size size, int citizens, int color, long seed, CityStyle style) {
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
			this.style = style;
		}

		public boolean capital() {
			return index == 0;
		}

		/** What the city has none of (1.23): one of {@link #LACKS}, different for each city of a country. */
		public BuildingType lack() {
			int base = (int) Math.floorMod(mix(cellKey(cx, cz) ^ 0x1AC4L), (long) LACKS.length);
			return LACKS[(base + index) % LACKS.length];
		}

		public long key() {
			return cellKey(cx, cz) * 4 + index;
		}

		private volatile CityForm form;

		/** 1.35: the town's outline (round, square, a star...): from its own seed, the style and the size. */
		public CityForm form() {
			CityForm f = form;
			if (f == null) {
				f = !shapes ? CityForm.BLOB : FORCE_FORM != null ? FORCE_FORM
						: CityForm.pick((int) Math.floorMod(mix(seed ^ 0xF0F1_5EEDL), 10000L), size, capital(), style);
				form = f;
			}
			return f;
		}

		private volatile CityShape shape;
		private volatile List<Hamlets.Hamlet> hamlets;

		/** The hamlets round the city (planned on first use). */
		public List<Hamlets.Hamlet> hamlets(long seed, Terrain t) {
			List<Hamlets.Hamlet> h = hamlets;
			if (h == null) {
				h = Hamlets.plan(seed, t, this);
				hamlets = h;
			}
			return h;
		}

		private volatile Depots.Depot depot;
		private volatile boolean depotPlanned;
		private volatile Ports.Port port;
		private volatile boolean portPlanned;

		/** 1.28: the town's port on the nearest shore (planned on first use), or null. */
		@Nullable
		public Ports.Port port(long seed, Terrain t) {
			if (!portPlanned) {
				synchronized (this) {
					if (!portPlanned) {
						port = Ports.plan(seed, t, this);
						portPlanned = true;
					}
				}
			}
			return port;
		}

		/** The town's depot by the highway (planned on first use), or null where there is no room for one. */
		@Nullable
		public Depots.Depot depot(long seed, Terrain t) {
			if (!depotPlanned) {
				synchronized (this) {
					if (!depotPlanned) {
						depot = Depots.plan(seed, t, this);
						depotPlanned = true;
					}
				}
			}
			return depot;
		}

		/** The hamlets if they are planned already, else an empty list. */
		public List<Hamlets.Hamlet> plannedHamlets() {
			List<Hamlets.Hamlet> h = hamlets;
			return h == null ? List.of() : h;
		}

		/** The street plan. */
		public CityShape shape() {
			CityShape sh = shape;
			if (sh == null) {
				sh = new CityShape(this);
				shape = sh;
			}
			return sh;
		}

		/** How far the city reaches from its centre (the bigger way). */
		public int half() {
			CityShape sh = shape();
			return Math.max(Math.max(x - sh.minX, sh.maxX - x), Math.max(z - sh.minZ, sh.maxZ - z));
		}

		/** Part of the city: a street, a pavement or a lot. */
		public boolean inside(int px, int pz) {
			return shape().inside(px, pz);
		}

		/** On a street or its pavement. */
		public boolean isStreet(int px, int pz) {
			CityShape.Probe p = shape().probe(px, pz);
			return p.street || p.kerb;
		}

		/** Distance outside the city (0 inside). */
		public int outside(int px, int pz) {
			return shape().outside(px, pz);
		}

		/** The bell of the central square (next to the city hall): the settlement's centre. */
		public BlockPos bell() {
			CityShape.Lot hall = shape().hallLot();
			return new BlockPos(hall.cx() + 12, base + 1, hall.z1 - 1);
		}

		/** The settlement's reach (a circle round the city). */
		public int radius() {
			return (int) (half() * 1.05) + 4;
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

	/**
	 * A road (1.25): a winding line of points from {@code (x0, z0)} to {@code (x1, z1)} that follows the land - round
	 * the hills, along the valleys, over rivers on bridges - with its height every {@link #STEP} blocks along it.
	 * Highways between cities have two lanes each way; the roads to the hamlets are narrow country roads.
	 */
	public static final class Road {
		public static final int STEP = 16;
		/** Segments per bucket of the spatial index. */
		private static final int BUCKET = 12;
		public final int x0;
		public final int z0;
		public final int x1;
		public final int z1;
		final float[] px;
		final float[] pz;
		/** Distance along the road at each point. */
		final float[] cum;
		public final double length;
		public final float[] heights;
		public final int minX;
		public final int maxX;
		public final int minZ;
		public final int maxZ;
		/** Half the width of the carriageway; a country track (a hamlet's) is a narrow dirt road. */
		public final int half;
		public final boolean dirt;
		/** Two lanes each way (between cities). */
		public final boolean highway;
		/** Half width where the road leaves a city (it widens from the street to the highway over the first metres). */
		public final int startHalf;
		private final float[] bx0;
		private final float[] bx1;
		private final float[] bz0;
		private final float[] bz1;

		/** Where a column is relative to the road. */
		public static final class Spot {
			public double along;
			public double across;
			/** Unit direction of the road there. */
			public double ux;
			public double uz;
		}

		Road(float[] px, float[] pz, float[] heights, int half, boolean dirt, boolean highway, int startHalf) {
			this.px = px;
			this.pz = pz;
			this.half = half;
			this.dirt = dirt;
			this.highway = highway;
			this.startHalf = startHalf;
			int n = px.length;
			this.x0 = Math.round(px[0]);
			this.z0 = Math.round(pz[0]);
			this.x1 = Math.round(px[n - 1]);
			this.z1 = Math.round(pz[n - 1]);
			cum = new float[n];
			float ax = Float.MAX_VALUE;
			float bxx = -Float.MAX_VALUE;
			float az = Float.MAX_VALUE;
			float bzz = -Float.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				if (i > 0) {
					cum[i] = cum[i - 1] + (float) Math.hypot(px[i] - px[i - 1], pz[i] - pz[i - 1]);
				}
				ax = Math.min(ax, px[i]);
				bxx = Math.max(bxx, px[i]);
				az = Math.min(az, pz[i]);
				bzz = Math.max(bzz, pz[i]);
			}
			this.length = Math.max(1, cum[n - 1]);
			this.heights = heights;
			int pad = half + 4;
			minX = (int) Math.floor(ax) - pad;
			maxX = (int) Math.ceil(bxx) + pad;
			minZ = (int) Math.floor(az) - pad;
			maxZ = (int) Math.ceil(bzz) + pad;
			int segs = n - 1;
			int buckets = Math.max(1, (segs + BUCKET - 1) / BUCKET);
			bx0 = new float[buckets];
			bx1 = new float[buckets];
			bz0 = new float[buckets];
			bz1 = new float[buckets];
			for (int b = 0; b < buckets; b++) {
				float a0 = Float.MAX_VALUE;
				float a1 = -Float.MAX_VALUE;
				float c0 = Float.MAX_VALUE;
				float c1 = -Float.MAX_VALUE;
				for (int i = b * BUCKET; i <= Math.min(segs, (b + 1) * BUCKET); i++) {
					a0 = Math.min(a0, px[i]);
					a1 = Math.max(a1, px[i]);
					c0 = Math.min(c0, pz[i]);
					c1 = Math.max(c1, pz[i]);
				}
				bx0[b] = a0;
				bx1[b] = a1;
				bz0[b] = c0;
				bz1[b] = c1;
			}
		}

		/** A straight road (the old kind, still used for short links). */
		Road(int x0, int z0, int x1, int z1, float[] heights, int half, boolean dirt) {
			this(new float[]{x0, x1}, new float[]{z0, z1}, heights, half, dirt, false, half);
		}

		/**
		 * The nearest point of the road to (x, z), if it is within {@code reach} of the line: distance along (it runs
		 * on past the ends in a straight line), signed distance across, the direction. False if nothing is that close.
		 */
		public boolean locate(double x, double z, double reach, Spot out) {
			double best = reach * reach;
			boolean found = false;
			int segs = px.length - 1;
			for (int b = 0; b < bx0.length; b++) {
				if (x < bx0[b] - reach || x > bx1[b] + reach || z < bz0[b] - reach || z > bz1[b] + reach) {
					continue;
				}
				for (int i = b * BUCKET; i < Math.min(segs, (b + 1) * BUCKET); i++) {
					double sx = px[i + 1] - px[i];
					double sz = pz[i + 1] - pz[i];
					double sl = Math.sqrt(sx * sx + sz * sz);
					if (sl < 1e-6) {
						continue;
					}
					double ux = sx / sl;
					double uz = sz / sl;
					double rx = x - px[i];
					double rz = z - pz[i];
					double u = rx * ux + rz * uz;
					double uc = u;
					if (i > 0 && uc < 0) {
						uc = 0;
					}
					if (i < segs - 1 && uc > sl) {
						uc = sl;
					}
					double cx = px[i] + ux * uc;
					double cz = pz[i] + uz * uc;
					double d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
					if (d2 < best) {
						best = d2;
						found = true;
						out.along = cum[i] + uc;
						out.across = rx * -uz + rz * ux;
						out.ux = ux;
						out.uz = uz;
					}
				}
			}
			return found;
		}

		/** Distance along the road of the point nearest to (px, pz) (slow: for a few checks, not for every column). */
		public double along(double x, double z) {
			Spot s = new Spot();
			return locate(x, z, 1e6, s) ? s.along : -1e9;
		}

		/** Distance from the centre line (signed; slow, see {@link #along}). */
		public double across(double x, double z) {
			Spot s = new Spot();
			return locate(x, z, 1e6, s) ? s.across : 1e9;
		}

		/** Half the width at this distance along: it widens from the street it leaves a city by, narrows into the next. */
		public double halfAt(double t) {
			if (startHalf >= half) {
				return half;
			}
			double ramp = (half - startHalf) * 6.0;
			double k = Math.min(1, Math.min(Math.max(0, t), Math.max(0, length - t)) / ramp);
			return startHalf + (half - startHalf) * k;
		}

		public float height(double t) {
			double i = Math.max(0, Math.min(heights.length - 1, t / STEP));
			int a = (int) Math.floor(i);
			int b = Math.min(heights.length - 1, a + 1);
			double f = i - a;
			return (float) (heights[a] * (1 - f) + heights[b] * f);
		}

		/** The point at this distance along: {x, z}. */
		public double[] pointAt(double t) {
			t = Math.max(0, Math.min(length, t));
			int lo = 0;
			int hi = cum.length - 1;
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (cum[mid] <= t) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			double seg = Math.max(1e-6, cum[hi] - cum[lo]);
			double f = (t - cum[lo]) / seg;
			return new double[]{px[lo] + (px[hi] - px[lo]) * f, pz[lo] + (pz[hi] - pz[lo]) * f};
		}

		/** The points of the line (for the map). */
		public int points() {
			return px.length;
		}

		public float pointX(int i) {
			return px[i];
		}

		public float pointZ(int i) {
			return pz[i];
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
		// The plan also depends on whether the world has the 1.28 styles.
		long key = (styles ? seed : ~seed) ^ (shapes ? 0L : 0x5EED_F04DL);
		if (cacheSeed != key) {
			synchronized (Cities.class) {
				if (cacheSeed != key) {
					CITIES.clear();
					ROADS.clear();
					CityGen.clearCache();
					cacheSeed = key;
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

	/** Like {@link #cityAt}, but only from cells already planned (never plans one: for checks on the server's own tick). */
	@Nullable
	public static City plannedCityAt(long seed, int x, int z, int margin) {
		if (cacheSeed != seed) {
			return null;
		}
		List<City> list = CITIES.get(cellKey(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
		if (list == null) {
			return null;
		}
		for (City c : list) {
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

	/** A hamlet already planned whose square is within {@code reach} of (x, z), or null (never plans anything). */
	@Nullable
	public static Hamlets.Hamlet plannedHamletAt(long seed, int x, int z, int reach) {
		if (cacheSeed != seed) {
			return null;
		}
		List<City> list = CITIES.get(cellKey(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
		if (list == null) {
			return null;
		}
		for (City c : list) {
			for (Hamlets.Hamlet h : c.plannedHamlets()) {
				if (Math.abs(h.x - x) <= reach && Math.abs(h.z - z) <= reach) {
					return h;
				}
			}
		}
		return null;
	}

	/** Every road that may pass through the cell of (x, z), the hamlets' tracks too. */
	public static List<Road> roadsNear(long seed, Terrain t, int x, int z) {
		List<Road> out = mainRoadsNear(seed, t, x, z);
		for (City c : citiesAround(seed, t, x, z)) {
			for (Hamlets.Hamlet h : c.hamlets(seed, t)) {
				out.add(h.road);
			}
			Depots.Depot d = c.depot(seed, t);
			if (d != null && d.access != null) {
				out.add(d.access);
			}
			Ports.Port port = c.port(seed, t);
			if (port != null && port.access != null) {
				out.add(port.access);
			}
			if (c.capital()) {
				Airports.Airport a = Airports.of(seed, t, c.cx, c.cz);
				if (a != null) {
					out.add(Airports.access(seed, t, a));
				}
			}
		}
		return out;
	}

	/** The roads between cities that may pass through the cell of (x, z): its own, and those coming in from the west and the north. */
	static List<Road> mainRoadsNear(long seed, Terrain t, int x, int z) {
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

	private static CityStyle styleOf(Terrain t, int x, int z, int roll, @Nullable CityStyle forced) {
		if (forced != null) {
			return forced;
		}
		return styles ? CityStyle.pick(roll, t.climate(x, z)) : CityStyle.CLASSIC;
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
		// 1.28: one look for the whole country, picked apart from the plan's own random numbers (old plans stay the same).
		int styleRoll = (int) Math.floorMod(mix(seed ^ cellKey(cx, cz) ^ 0x5759_4C45L), 100L);
		CityStyle forced = FORCE_STYLE;
		out.add(new City(cx, cz, 0, cap[0], cap[1], cap[2], Size.LARGE, Size.LARGE.popMin + 100 + r.nextInt(Size.LARGE.popMax - Size.LARGE.popMin - 99),
				color, r.nextLong(), styleOf(t, cap[0], cap[1], styleRoll, forced)));
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int k = 1; k <= 2; k++) {
			Size size = r.nextInt(100) < 55 ? Size.MEDIUM : Size.SMALL;
			int[] best = null;
			for (int tries = 0; tries < 4 && best == null; tries++) {
				double a = a0 + (k == 1 ? 0 : Math.PI * (0.65 + r.nextDouble() * 0.7)) + (r.nextDouble() - 0.5) * 0.5;
				double d = 600 + r.nextDouble() * 250;
				int tx = cap[0] + (int) (Math.cos(a) * d);
				int tz = cap[1] + (int) (Math.sin(a) * d);
				int lim = size.half() + 260;
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
							r.nextLong(), styleOf(t, best[0], best[1], styleRoll, forced)));
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
				out.add(road(seed, t, cap, c));
			}
		}
		for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
			List<City> next = cities(seed, t, cx + d[0], cz + d[1]);
			if (!next.isEmpty() && next.getFirst().index == 0) {
				out.add(road(seed, t, cap, next.getFirst()));
			}
		}
		return out;
	}

	/** The last point of the city on the way from its centre towards (tx, tz): where a road out of it starts. */
	static int[] edge(City c, double tx, double tz) {
		double dx = tx - c.x;
		double dz = tz - c.z;
		double len = Math.hypot(dx, dz);
		double ux = dx / len;
		double uz = dz / len;
		int last = 0;
		int reach = c.half() + 10;
		for (int t = 0; t <= reach; t++) {
			if (c.inside((int) Math.round(c.x + ux * t), (int) Math.round(c.z + uz * t))) {
				last = t;
			}
		}
		return new int[]{(int) Math.round(c.x + ux * (last + 1)), (int) Math.round(c.z + uz * (last + 1))};
	}

	/** Highways (1.25): two lanes each way - 15 blocks of asphalt with the markings, hard shoulders and crash barriers. */
	public static final int HIGHWAY_HALF = 7;
	/** Country roads to the hamlets: one lane each way. */
	public static final int COUNTRY_HALF = 2;

	/** A highway from the edge of one city to the edge of the other, winding over the land between them. */
	private static Road road(long seed, Terrain t, City a, City b) {
		int[] ea = edge(a, b.x, b.z);
		int[] eb = edge(b, a.x, a.z);
		double[] da = outward(a, ea);
		double[] db = outward(b, eb);
		long rs = seed ^ a.key() * 31 ^ b.key() * 17;
		float[][] line = RoadPlanner.route(t, rs, ea[0], ea[1], da[0], da[1], eb[0], eb[1], db[0], db[1], 32, 28,
				(x, z) -> avoidTowns(seed, t, a, b, x, z));
		float[] hs = RoadPlanner.heights(t, line[0], line[1], a.base, b.base, 0.065);
		Road r = new Road(line[0], line[1], hs, HIGHWAY_HALF, false, true, STREET_HALF);
		// 1.39: over the railways it crosses on a bridge.
		Railways.raiseRoad(seed, t, r);
		return r;
	}

	/** The way out of a city at its edge point: along the street it leaves by (the axis nearest the direction out). */
	static double[] outward(City c, int[] e) {
		double dx = e[0] - c.x;
		double dz = e[1] - c.z;
		if (Math.abs(dx) >= Math.abs(dz)) {
			return new double[]{Math.signum(dx), 0};
		}
		return new double[]{0, Math.signum(dz)};
	}

	/** Other cities on the way cost a lot to pass through (roads go round them). */
	private static double avoidTowns(long seed, Terrain t, City a, City b, int x, int z) {
		if (Airports.inside(seed, t, x, z, 24)) {
			// 1.40: round the airports.
			return 1;
		}
		for (City c : citiesAround(seed, t, x, z)) {
			if (c == a || c == b) {
				continue;
			}
			if (c.outside(x, z) <= 40) {
				return 1;
			}
		}
		return 0;
	}

	/** A country road from (x0, z0) at level y0 to (x1, z1) at y1, winding over the land in between. */
	static Road between(Terrain t, long seed, int x0, int z0, int y0, double dx0, double dz0, int x1, int z1, int y1, double dx1, double dz1,
			int half, boolean dirt) {
		float[][] line = RoadPlanner.route(t, seed, x0, z0, dx0, dz0, x1, z1, dx1, dz1, 12, 10, null);
		float[] hs = RoadPlanner.heights(t, line[0], line[1], y0, y1, 0.1);
		return new Road(line[0], line[1], hs, half, dirt, false, half);
	}

	// ------------------------------------------------------------------------------------------------
	// The buildings of a city: rows along the streets round each lot, chosen by district

	private static final Object[] DOWNTOWN = {BuildingType.TOWER, 4, BuildingType.OFFICE, 3, BuildingType.SHOP, 3, BuildingType.PANEL9, 2};
	private static final Object[] MID = {BuildingType.PANEL9, 3, BuildingType.PANEL5, 4, BuildingType.SHOP, 2, BuildingType.APARTMENTS, 1};
	private static final Object[] OUTER = {BuildingType.COTTAGE, 5, BuildingType.HOUSE, 3, BuildingType.SMALL_HOUSE, 2, BuildingType.GARAGES, 1,
			BuildingType.SHOP, 1};
	private static final Object[] INDUSTRY = {BuildingType.WAREHOUSE, 3, BuildingType.GARAGES, 2, BuildingType.HANGAR, 1, BuildingType.LOGISTICS_HUB, 1};
	// 1.28: what the districts of each style are built of.
	private static final Object[] SOVIET_DOWNTOWN = {BuildingType.PANEL9, 4, BuildingType.OFFICE, 2, BuildingType.SHOP, 3, BuildingType.TOWER, 1,
			BuildingType.APARTMENTS, 2};
	private static final Object[] SOVIET_MID = {BuildingType.PANEL5, 5, BuildingType.PANEL9, 2, BuildingType.APARTMENTS, 1, BuildingType.SHOP, 1};
	private static final Object[] SOVIET_OUTER = {BuildingType.SMALL_HOUSE, 4, BuildingType.HOUSE, 3, BuildingType.GARAGES, 3, BuildingType.SHOP, 1,
			BuildingType.PANEL5, 1};
	private static final Object[] EURO_DOWNTOWN = {BuildingType.APARTMENTS, 4, BuildingType.PANEL5, 3, BuildingType.SHOP, 4, BuildingType.OFFICE, 1,
			BuildingType.TOWER, 1};
	private static final Object[] EURO_MID = {BuildingType.PANEL5, 4, BuildingType.APARTMENTS, 3, BuildingType.PANEL9, 1, BuildingType.SHOP, 2};
	private static final Object[] EURO_OUTER = {BuildingType.HOUSE, 4, BuildingType.COTTAGE, 3, BuildingType.SMALL_HOUSE, 3, BuildingType.SHOP, 1};
	private static final Object[] US_DOWNTOWN = {BuildingType.TOWER, 5, BuildingType.OFFICE, 4, BuildingType.SHOP, 2, BuildingType.PANEL9, 1};
	private static final Object[] US_MID = {BuildingType.APARTMENTS, 3, BuildingType.PANEL5, 3, BuildingType.SHOP, 3, BuildingType.PANEL9, 1};
	private static final Object[] US_OUTER = {BuildingType.COTTAGE, 5, BuildingType.HOUSE, 5, BuildingType.SMALL_HOUSE, 1, BuildingType.SHOP, 1};
	private static final Object[] DESERT_DOWNTOWN = {BuildingType.OFFICE, 3, BuildingType.TOWER, 2, BuildingType.PANEL9, 2, BuildingType.SHOP, 4};
	private static final Object[] DESERT_MID = {BuildingType.APARTMENTS, 4, BuildingType.PANEL5, 3, BuildingType.SHOP, 3};
	private static final Object[] DESERT_OUTER = {BuildingType.HOUSE, 4, BuildingType.SMALL_HOUSE, 4, BuildingType.COTTAGE, 2, BuildingType.SHOP, 1};

	private static Object[] mix(CityStyle style, int district) {
		return switch (style) {
			case SOVIET -> district == CityShape.DOWNTOWN ? SOVIET_DOWNTOWN : district == CityShape.MID ? SOVIET_MID : SOVIET_OUTER;
			case EUROPEAN -> district == CityShape.DOWNTOWN ? EURO_DOWNTOWN : district == CityShape.MID ? EURO_MID : EURO_OUTER;
			case AMERICAN -> district == CityShape.DOWNTOWN ? US_DOWNTOWN : district == CityShape.MID ? US_MID : US_OUTER;
			case DESERT -> district == CityShape.DOWNTOWN ? DESERT_DOWNTOWN : district == CityShape.MID ? DESERT_MID : DESERT_OUTER;
			default -> district == CityShape.DOWNTOWN ? DOWNTOWN : district == CityShape.MID ? MID : OUTER;
		};
	}

	/** Industries a city may lack: crude oil, the refinery, weapons, food. */
	static final BuildingType[] LACKS = {BuildingType.OIL_WELL, BuildingType.REFINERY, BuildingType.ARMS_FACTORY, BuildingType.FOOD_PLANT};

	/** A lot being filled: which of its cells are taken. */
	private static final class Filling {
		final CityShape.Lot lot;
		final boolean[][] taken;

		Filling(CityShape.Lot lot) {
			this.lot = lot;
			this.taken = new boolean[lot.width()][lot.depth()];
		}

		boolean free(int x, int z) {
			int a = x - lot.x0;
			int b = z - lot.z0;
			return a >= 0 && b >= 0 && a < taken.length && b < taken[0].length && !taken[a][b];
		}

		void take(int x, int z) {
			int a = x - lot.x0;
			int b = z - lot.z0;
			if (a >= 0 && b >= 0 && a < taken.length && b < taken[0].length) {
				taken[a][b] = true;
			}
		}
	}

	/** The building with its front on {@code side} of the lot, {@code pos} blocks along it, set back {@code back}; null if it won't fit. */
	private static Building fit(Filling f, BuildingType type, Direction side, int pos, int back, int y) {
		CityShape.Lot l = f.lot;
		Direction facing = side.getOpposite();
		int hw = type.halfWidth();
		int ox;
		int oz;
		switch (side) {
			case SOUTH -> {
				ox = l.x0 + pos + hw;
				oz = l.z1 - 1 - back;
			}
			case NORTH -> {
				ox = l.x1 - pos - hw;
				oz = l.z0 + 1 + back;
			}
			case WEST -> {
				ox = l.x0 + 1 + back;
				oz = l.z0 + pos + hw;
			}
			default -> {
				ox = l.x1 - 1 - back;
				oz = l.z1 - pos - hw;
			}
		}
		Building b = new Building(0, type, new BlockPos(ox, y, oz), facing, true);
		for (int lx = -hw - 1; lx <= hw + 1; lx++) {
			for (int lz = -1; lz <= type.depth; lz++) {
				BlockPos at = b.at(lx, 0, lz);
				boolean inner = Math.abs(lx) <= hw && lz >= 0 && lz < type.depth;
				if (inner && !f.free(at.getX(), at.getZ())) {
					return null;
				}
				if (!inner && lz >= 0 && f.free(at.getX(), at.getZ()) == false && inLot(l, at)) {
					return null;
				}
			}
		}
		return b;
	}

	private static boolean inLot(CityShape.Lot l, BlockPos p) {
		return p.getX() >= l.x0 && p.getX() <= l.x1 && p.getZ() >= l.z0 && p.getZ() <= l.z1;
	}

	private static void claim(Filling f, Building b) {
		int hw = b.type.halfWidth();
		for (int lx = -hw - 1; lx <= hw + 1; lx++) {
			for (int lz = -1; lz <= b.type.depth; lz++) {
				BlockPos at = b.at(lx, 0, lz);
				f.take(at.getX(), at.getZ());
			}
		}
	}

	private static BuildingType pickType(Random r, Object[] weighted) {
		int total = 0;
		for (int i = 1; i < weighted.length; i += 2) {
			total += (Integer) weighted[i];
		}
		int roll = r.nextInt(total);
		for (int i = 0; i < weighted.length; i += 2) {
			roll -= (Integer) weighted[i + 1];
			if (roll < 0) {
				return (BuildingType) weighted[i];
			}
		}
		return (BuildingType) weighted[0];
	}

	private static int sideLength(CityShape.Lot l, Direction side) {
		return side.getAxis() == Direction.Axis.Z ? l.width() : l.depth();
	}

	/** Tries to put one building of this type anywhere along the lot's sides. */
	private static Building placeAnywhere(Filling f, Random r, BuildingType type, int y) {
		List<Direction> sides = new ArrayList<>(List.of(Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST));
		Collections.shuffle(sides, r);
		for (Direction side : sides) {
			int len = sideLength(f.lot, side);
			for (int pos = 0; pos + type.width <= len; pos += 2) {
				Building b = fit(f, type, side, pos, 0, y);
				if (b != null) {
					claim(f, b);
					return b;
				}
			}
		}
		return null;
	}

	/** Rows of buildings along every side of the lot, picked from the district's mix, with gaps and set-backs. */
	private static void rows(Filling f, Random r, Object[] mix, int y, List<Building> out) {
		List<Direction> sides = new ArrayList<>(List.of(Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST));
		Collections.shuffle(sides, r);
		for (Direction side : sides) {
			int len = sideLength(f.lot, side);
			int pos = r.nextInt(3);
			while (pos < len - 6) {
				Building placed = null;
				for (int tries = 0; tries < 6 && placed == null; tries++) {
					BuildingType t = pickType(r, mix);
					if (pos + t.width > len) {
						continue;
					}
					placed = fit(f, t, side, pos, r.nextInt(4) == 0 ? 1 + r.nextInt(2) : 0, y);
				}
				if (placed == null) {
					pos += 2;
					continue;
				}
				claim(f, placed);
				placed.variant = r.nextInt(97);
				out.add(placed);
				pos += placed.type.width + 1 + r.nextInt(3);
			}
		}
	}

	private static List<Building> layout(City c) {
		Random r = new Random(c.seed);
		CityShape sh = c.shape();
		int y = c.base;
		List<Building> out = new ArrayList<>();
		Map<Integer, Filling> fills = new HashMap<>();
		for (CityShape.Lot l : sh.lots) {
			fills.put(l.id, new Filling(l));
		}
		// The city hall in the middle of the central square, facing south, its portico to the pavement.
		CityShape.Lot hallLot = sh.hallLot();
		Building hall = new Building(0, BuildingType.CITY_HALL, new BlockPos(hallLot.cx(), y, hallLot.z1 - 4), Direction.NORTH, true);
		hall.variant = r.nextInt(97);
		claim(fills.get(hallLot.id), hall);
		out.add(hall);
		// What every town has: a gas station and a logistics hub towards the edge; bigger towns a school, a hospital,
		// an army base (barracks and a hangar).
		List<BuildingType> needIndustry = new ArrayList<>(List.of(BuildingType.REFINERY, BuildingType.ARMS_FACTORY, BuildingType.FOOD_PLANT,
				BuildingType.OIL_WELL, BuildingType.OIL_WELL));
		needIndustry.removeIf(t -> t == c.lack());
		List<BuildingType> needOuter = new ArrayList<>(List.of(BuildingType.GAS_STATION, BuildingType.LOGISTICS_HUB, BuildingType.FARM));
		List<BuildingType> needAny = new ArrayList<>(List.of(BuildingType.MARKET, BuildingType.SCHOOL));
		if (c.size != Size.SMALL) {
			needOuter.add(BuildingType.HANGAR);
			needOuter.add(BuildingType.BARRACKS);
			needOuter.add(BuildingType.WAREHOUSE);
			needAny.add(BuildingType.HOSPITAL);
		}
		if (c.size == Size.LARGE) {
			needAny.add(BuildingType.SCHOOL);
			needAny.add(BuildingType.HOSPITAL);
		}
		List<CityShape.Lot> order = new ArrayList<>(sh.lots);
		Collections.shuffle(order, r);
		for (BuildingType t : needIndustry) {
			placeIn(order, fills, r, t, y, out, CityShape.INDUSTRY, CityShape.OUTER, CityShape.MID);
		}
		for (BuildingType t : needOuter) {
			placeIn(order, fills, r, t, y, out, t == BuildingType.FARM ? new int[]{CityShape.OUTER, CityShape.INDUSTRY, CityShape.MID}
					: new int[]{CityShape.INDUSTRY, CityShape.OUTER, CityShape.MID});
		}
		for (BuildingType t : needAny) {
			placeIn(order, fills, r, t, y, out, CityShape.MID, CityShape.OUTER, CityShape.DOWNTOWN);
		}
		for (CityShape.Lot l : sh.lots) {
			Filling f = fills.get(l.id);
			switch (l.district) {
				case CityShape.HALL, CityShape.VACANT -> {
				}
				case CityShape.PARK -> {
					Building park = new Building(0, BuildingType.PARK, new BlockPos(l.cx(), y, l.cz() + 10), Direction.NORTH, true);
					if (fit(f, BuildingType.PARK, Direction.SOUTH, (l.width() - 21) / 2, Math.max(0, l.z1 - 1 - (l.cz() + 10)), y) != null) {
						claim(f, park);
						park.variant = r.nextInt(97);
						out.add(park);
					}
				}
				case CityShape.DOWNTOWN -> rows(f, r, mix(c.style, CityShape.DOWNTOWN), y, out);
				case CityShape.MID -> rows(f, r, mix(c.style, CityShape.MID), y, out);
				case CityShape.INDUSTRY -> rows(f, r, INDUSTRY, y, out);
				default -> rows(f, r, mix(c.style, CityShape.OUTER), y, out);
			}
		}
		List<Building> numbered = new ArrayList<>();
		for (Building b : out) {
			Building nb = new Building(numbered.size(), b.type, b.origin, b.facing, true);
			nb.variant = b.variant;
			nb.style = c.style.ordinal();
			numbered.add(nb);
		}
		return numbered;
	}

	private static void placeIn(List<CityShape.Lot> order, Map<Integer, Filling> fills, Random r, BuildingType t, int y, List<Building> out,
			int... districts) {
		for (int d : districts) {
			for (CityShape.Lot l : order) {
				if (l.district != d) {
					continue;
				}
				Building b = placeAnywhere(fills.get(l.id), r, t, y);
				if (b != null) {
					b.variant = r.nextInt(97);
					out.add(b);
					return;
				}
			}
		}
	}
}
