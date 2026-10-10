package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * 1.40: the capitals' airports - a little way out of town, on the flattest open ground on a side no highway leaves by:
 * a runway (markings, edge lights, a threshold at each end), a taxiway beside it, the apron with its stands, the
 * terminal, the control tower, a hangar, a car park, a fence round it all and a road into town. Planned from the seed
 * like the depots; built by CityGen as the land is made. The airliners that land, park and take off are the player's
 * own client's (client.nation.AirTraffic): it finds the airport by its threshold and stand markers and lays out the
 * ways about it from the same plan ({@link #local}, {@link #world}).
 *
 * The plan, in the airport's own frame: u along the runway (0 at the threshold the planes land over, {@link #RUNWAY} at
 * the far one), v across it (0 on the centre line, the apron side positive).
 */
public final class Airports {
	public static final int RUNWAY = 480;
	public static final int RW_HALF = 14;
	public static final int TAXI_V = 50;
	public static final int TAXI_HALF = 6;
	/** The connectors from the runway to the taxiway (their centre lines). */
	public static final int LINK_A = 16;
	public static final int LINK_B = RUNWAY - 16;
	public static final int APRON_V0 = TAXI_V + TAXI_HALF;
	public static final int APRON_V1 = 120;
	public static final int MID = RUNWAY / 2;
	public static final int APRON_U0 = MID - 120;
	public static final int APRON_U1 = MID + 120;
	/** The stands: where a parked plane's nose stops (it parks nose in, towards the terminal). */
	public static final int STAND_V = 108;
	public static final int STANDS = 5;
	public static final int TERMINAL_V0 = APRON_V1 + 2;
	public static final int TERMINAL_V1 = TERMINAL_V0 + 18;
	public static final int V0 = -RW_HALF - 12;
	public static final int V1 = 164;
	public static final int U0 = -24;
	public static final int U1 = RUNWAY + 24;
	/** The ground round the fence blends back into the land over this many blocks. */
	public static final int BLEND = 10;

	public static int standU(int k) {
		return APRON_U0 + 24 + 48 * k;
	}

	public static final class Airport {
		public final Cities.City city;
		/** The threshold the planes land over (u = 0, v = 0). */
		public final int sx;
		public final int sz;
		/** Unit along the runway, and across it towards the apron (axis-aligned). */
		public final int ux;
		public final int uz;
		public final int vx;
		public final int vz;
		public final int y;
		public final int minX;
		public final int maxX;
		public final int minZ;
		public final int maxZ;
		volatile Cities.Road access;

		Airport(Cities.City city, int sx, int sz, int ux, int uz, int vx, int vz, int y) {
			this.city = city;
			this.sx = sx;
			this.sz = sz;
			this.ux = ux;
			this.uz = uz;
			this.vx = vx;
			this.vz = vz;
			this.y = y;
			int[] a = world(U0, V0);
			int[] b = world(U1, V1);
			minX = Math.min(a[0], b[0]);
			maxX = Math.max(a[0], b[0]);
			minZ = Math.min(a[1], b[1]);
			maxZ = Math.max(a[1], b[1]);
		}

		public long key() {
			return city.key() * 8 + 6;
		}

		public int[] local(int x, int z) {
			return Airports.local(sx, sz, ux, uz, vx, vz, x, z);
		}

		public int[] world(int u, int v) {
			return Airports.world(sx, sz, ux, uz, vx, vz, u, v);
		}

		/** How far (x, z) is outside the fence (0 inside). */
		public int out(int x, int z) {
			return Math.max(0, Math.max(Math.max(minX - x, x - maxX), Math.max(minZ - z, z - maxZ)));
		}

		public boolean near(int x, int z, int margin) {
			return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
		}

		/** Does the straight way from (x0, z0) to (x1, z1) pass over the airport (with a margin)? */
		public boolean crosses(double x0, double z0, double x1, double z1, int margin) {
			int steps = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) / 8) + 1;
			for (int i = 0; i <= steps; i++) {
				double f = (double) i / steps;
				if (near((int) (x0 + (x1 - x0) * f), (int) (z0 + (z1 - z0) * f), margin)) {
					return true;
				}
			}
			return false;
		}
	}

	public static int[] local(int sx, int sz, int ux, int uz, int vx, int vz, int x, int z) {
		int dx = x - sx;
		int dz = z - sz;
		return new int[]{dx * ux + dz * uz, dx * vx + dz * vz};
	}

	public static int[] world(int sx, int sz, int ux, int uz, int vx, int vz, int u, int v) {
		return new int[]{sx + u * ux + v * vx, sz + u * uz + v * vz};
	}

	private static final Map<Long, Airport> AIRPORTS = new ConcurrentHashMap<>();
	private static final Airport NONE = new Airport(null, 0, 0, 1, 0, 0, 1, 0);
	private static volatile long cacheSeed;
	/** For the tests: airports planned, capitals that found no room for one. */
	public static volatile int planned;
	public static volatile int refused;

	private Airports() {
	}

	/** The airport of the capital of cell (cx, cz), or null (none there, or an older world). */
	@Nullable
	public static Airport of(long seed, Cities.Terrain t, int cx, int cz) {
		if (!Cities.airports) {
			return null;
		}
		if (seed != cacheSeed) {
			AIRPORTS.clear();
			cacheSeed = seed;
		}
		Airport a = AIRPORTS.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), k -> {
			List<Cities.City> list = Cities.cities(seed, t, cx, cz);
			Airport p = list.isEmpty() || list.getFirst().index != 0 ? null : plan(seed, t, list.getFirst());
			return p == null ? NONE : p;
		});
		return a == NONE ? null : a;
	}

	/** The airports that may reach (x, z): those of the capitals of its cell and the eight round it. */
	public static List<Airport> near(long seed, Cities.Terrain t, int x, int z, int margin) {
		List<Airport> out = new ArrayList<>(1);
		if (!Cities.airports) {
			return out;
		}
		int cx = Math.floorDiv(x, Cities.CELL);
		int cz = Math.floorDiv(z, Cities.CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Airport a = of(seed, t, cx + dx, cz + dz);
				if (a != null && a.near(x, z, margin)) {
					out.add(a);
				}
			}
		}
		return out;
	}

	/** Is (x, z) within {@code margin} of an airport's fence? */
	public static boolean inside(long seed, Cities.Terrain t, int x, int z, int margin) {
		return !near(seed, t, x, z, margin).isEmpty();
	}

	/** Does the straight way between two points pass over an airport (with a margin)? */
	public static boolean crosses(long seed, Cities.Terrain t, double x0, double z0, double x1, double z1, int margin) {
		List<Airport> list = near(seed, t, (int) x0, (int) z0, 1200);
		for (Airport a : list) {
			if (a.crosses(x0, z0, x1, z1, margin)) {
				return true;
			}
		}
		return false;
	}

	private static Airport plan(long seed, Cities.Terrain t, Cities.City c) {
		// The ways out of town to stay clear of: towards the next capitals, towards the town's own satellites.
		List<double[]> ways = new ArrayList<>();
		for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			List<Cities.City> n = Cities.cities(seed, t, c.cx + d[0], c.cz + d[1]);
			if (!n.isEmpty()) {
				ways.add(new double[]{n.getFirst().x - c.x, n.getFirst().z - c.z});
			}
		}
		for (Cities.City o : Cities.cities(seed, t, c.cx, c.cz)) {
			if (o != c) {
				ways.add(new double[]{o.x - c.x, o.z - c.z});
			}
		}
		int sea = t.sea();
		// Every candidate site first roughly (six samples), then the three likeliest carefully (thirty-six).
		List<double[]> rough = new ArrayList<>();
		for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			// A highway leaves this way (to the next capital, to a satellite): the airport only off to one side of it, clear
			// of the way straight out.
			boolean blocked = false;
			for (double[] w : ways) {
				double cos = (w[0] * d[0] + w[1] * d[1]) / Math.max(1, Math.hypot(w[0], w[1]));
				if (cos > Math.cos(Math.toRadians(50))) {
					blocked = true;
				}
			}
			int[] e = Cities.edge(c, c.x + d[0] * 2000, c.z + d[1] * 2000);
			// v points back towards the town; u along the town's side.
			int vx = -d[0];
			int vz = -d[1];
			int ux = -d[1];
			int uz = d[0];
			for (int gap : new int[]{50, 110}) {
				for (int shift : blocked ? new int[]{-(MID + 60), MID + 60} : new int[]{0, -120, 120, -(MID + 60), MID + 60}) {
					// The fence on the town side is gap blocks out from the edge; the threshold is MID (+ shift) along.
					int sx = e[0] + d[0] * (gap + V1) - ux * (MID + shift);
					int sz = e[1] + d[1] * (gap + V1) - uz * (MID + shift);
					double score = sample(seed, t, c, sx, sz, ux, uz, vx, vz, 2, 1, null);
					if (score < Double.MAX_VALUE) {
						rough.add(new double[]{score + gap * 0.2 + Math.abs(shift) * 0.1, sx, sz, ux, uz, vx, vz});
					}
				}
			}
		}
		rough.sort((p, q) -> Double.compare(p[0], q[0]));
		Airport best = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < Math.min(3, rough.size()); i++) {
			double[] r = rough.get(i);
			int[] level = new int[1];
			double score = sample(seed, t, c, (int) r[1], (int) r[2], (int) r[3], (int) r[4], (int) r[5], (int) r[6], 8, 3, level);
			if (score < bestScore) {
				bestScore = score;
				best = new Airport(c, (int) r[1], (int) r[2], (int) r[3], (int) r[4], (int) r[5], (int) r[6], Math.max(sea + 2, level[0]));
			}
		}
		if (best == null) {
			refused++;
			return null;
		}
		planned++;
		return best;
	}

	/**
	 * How good a site is (lower is better: the ground's unevenness), sampled on a grid of (nu + 1) x (nv + 1); MAX_VALUE
	 * if it will not do (water, a town, too steep). {@code level}: where to put its median height.
	 */
	private static double sample(long seed, Cities.Terrain t, Cities.City c, int sx, int sz, int ux, int uz, int vx, int vz, int nu, int nv,
			@Nullable int[] level) {
		int sea = t.sea();
		int[] hs = new int[(nu + 1) * (nv + 1)];
		int k = 0;
		int wet = 0;
		for (int i = 0; i <= nu; i++) {
			for (int j = 0; j <= nv; j++) {
				int[] w = world(sx, sz, ux, uz, vx, vz, U0 + (U1 - U0) * i / nu, V0 + (V1 - V0) * j / nv);
				int y = t.top(w[0], w[1]);
				if (y <= sea && ++wet > Math.max(0, hs.length / 12)) {
					return Double.MAX_VALUE;
				}
				for (Cities.City o : Cities.citiesAround(seed, t, w[0], w[1])) {
					if (o.outside(w[0], w[1]) < (o == c ? 30 : 60)) {
						return Double.MAX_VALUE;
					}
				}
				hs[k++] = y;
			}
		}
		int[] sorted = hs.clone();
		java.util.Arrays.sort(sorted);
		int median = sorted[k / 2];
		if (sorted[k - 1] - sorted[0] > 18) {
			return Double.MAX_VALUE;
		}
		double score = wet * 40;
		for (int y : hs) {
			score += Math.abs(y - median);
		}
		if (level != null) {
			level[0] = median;
		}
		return score / k;
	}

	/** The road from the car park into town (planned on first use: it is not needed to plan the rest round the airport). */
	public static Cities.Road access(long seed, Cities.Terrain t, Airport a) {
		Cities.Road r = a.access;
		if (r == null) {
			synchronized (a) {
				r = a.access;
				if (r == null) {
					Cities.City c = a.city;
					int[] gate = a.world(MID, V1 + 1);
					int[] e = Cities.edge(c, gate[0], gate[1]);
					double[] out = Cities.outward(c, e);
					r = Cities.between(t, c.seed ^ 0xA1590A7L, gate[0], gate[1], a.y, a.vx, a.vz, e[0], e[1], c.base, out[0], out[1], 3, false);
					Railways.raiseRoad(seed, t, r);
					a.access = r;
				}
			}
		}
		return r;
	}
}
