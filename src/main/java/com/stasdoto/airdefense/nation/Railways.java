package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.39: the railways - a line from each capital to the capitals east and south of it (as the highways). A railway runs
 * straight, as railways do: along the grid, a diagonal, along the grid again (so the rails lie block to block); with
 * gentle grades - on embankments over the dips, in cuttings through the rises, on bridges over the water, the valleys
 * and the roads, in tunnels through the hills. At each end a station a little way out of the town. Planned from the
 * seed like the roads; built by CityGen as the land is made. Trains run on them on the player's own client (see
 * client.nation.Trains).
 */
public final class Railways {
	/** Half the width of the bed (gravel under and beside the track). */
	public static final double BED = 2.5;
	/** How far round the line the land is shaped (embankment, cutting). */
	public static final int SIDE = 9;
	/** A station: the platform along this many blocks of the track before its end. */
	public static final int STATION = 30;
	/** The straight run at each end (the station and the approach to it). */
	private static final int RUN = STATION + 14;
	/** The steepest grade (blocks up per block along). */
	private static final double GRADE = 1.0 / 28;

	/** One line: the blocks of its track in order, the rail height at each, the kind of each (0 track, 1 bridge, 2 tunnel). */
	public static final class Line {
		public final int[] xs;
		public final int[] zs;
		/** The height of the track's bed at each block (the track block sits at floor(h), raised by the fraction). */
		public final double[] hs;
		public final byte[] kind;
		/** The direction at each block: 0 north-south, 1 north-east, 2 east-west, 3 south-east (for the track's model). */
		public final byte[] dir;
		/** Which side of the track the platforms are (1 left of the way from the start, -1 right). */
		public final int platform;
		public final int minX;
		public final int maxX;
		public final int minZ;
		public final int maxZ;
		public final long key;

		Line(long key, int[] xs, int[] zs, double[] hs, byte[] kind, byte[] dir, int platform) {
			this.key = key;
			this.xs = xs;
			this.zs = zs;
			this.hs = hs;
			this.kind = kind;
			this.dir = dir;
			this.platform = platform;
			int a = Integer.MAX_VALUE;
			int b = Integer.MIN_VALUE;
			int c = Integer.MAX_VALUE;
			int d = Integer.MIN_VALUE;
			for (int i = 0; i < xs.length; i++) {
				a = Math.min(a, xs[i]);
				b = Math.max(b, xs[i]);
				c = Math.min(c, zs[i]);
				d = Math.max(d, zs[i]);
			}
			minX = a;
			maxX = b;
			minZ = c;
			maxZ = d;
		}

		public int length() {
			return xs.length;
		}

		public boolean near(int x, int z, int reach) {
			return x >= minX - reach && x <= maxX + reach && z >= minZ - reach && z <= maxZ + reach;
		}

		/** The track block's own height (the block it is in) and its rise within that block (0..7, eighths). */
		public int y(int i) {
			return (int) Math.floor(hs[i]);
		}

		public int lift(int i) {
			return Math.min(7, (int) Math.floor((hs[i] - Math.floor(hs[i])) * 8));
		}

		/** Is block i in a station (the last stretch at either end)? */
		public boolean station(int i) {
			return i < STATION || i >= xs.length - STATION;
		}

		/**
		 * The nearest point of the line to (x, z) among blocks [from, to): the segment's first block, how far along it
		 * (0..1), the distance across (signed: + to the left of the way from the start). False if none within reach.
		 */
		public boolean locate(double x, double z, int from, int to, double reach, Spot out) {
			double best = reach * reach;
			boolean found = false;
			for (int i = Math.max(0, from); i < Math.min(xs.length - 1, to); i++) {
				double ax = xs[i] + 0.5;
				double az = zs[i] + 0.5;
				double sx = xs[i + 1] - xs[i];
				double sz = zs[i + 1] - zs[i];
				double l2 = sx * sx + sz * sz;
				double u = ((x - ax) * sx + (z - az) * sz) / l2;
				u = Math.max(0, Math.min(1, u));
				double cx = ax + sx * u;
				double cz = az + sz * u;
				double d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
				if (d2 < best) {
					best = d2;
					found = true;
					out.i = i;
					out.u = u;
					double l = Math.sqrt(l2);
					out.across = ((x - ax) * -sz + (z - az) * sx) / l;
					out.dist = Math.sqrt(d2);
				}
			}
			return found;
		}

		/** The bed height at a spot (between its two blocks). */
		public double height(Spot s) {
			int j = Math.min(xs.length - 1, s.i + 1);
			return hs[s.i] + (hs[j] - hs[s.i]) * s.u;
		}
	}

	/** Where a column is against a line. */
	public static final class Spot {
		public int i;
		public double u;
		public double across;
		public double dist;
	}

	private static final Map<Long, List<Line>> LINES = new ConcurrentHashMap<>();
	private static volatile long cacheSeed;

	/** For the tests: lines planned, and those given up (through a town, along a road, too much sea). */
	public static volatile int planned;
	public static volatile int refused;
	/** For the tests: why ways were given up (by reason), and the time spent planning (all, and on the roads near). */
	public static final java.util.concurrent.ConcurrentHashMap<String, Integer> WHY = new java.util.concurrent.ConcurrentHashMap<>();
	public static volatile long planNanos;
	public static volatile long roadNanos;

	private static Line no(String why) {
		WHY.merge(why, 1, Integer::sum);
		return null;
	}

	/** Lines already planned near (x, z) (never plans: for checks on the server's own thread). */
	public static List<Line> cachedNear(long seed, int x, int z) {
		if (!Cities.railways || seed != cacheSeed) {
			return List.of();
		}
		int cx = Math.floorDiv(x, Cities.CELL);
		int cz = Math.floorDiv(z, Cities.CELL);
		List<Line> out = new ArrayList<>();
		for (int[] d : new int[][]{{0, 0}, {-1, 0}, {0, -1}}) {
			List<Line> ls = LINES.get(((long) (cx + d[0]) << 32) ^ ((cz + d[1]) & 0xffffffffL));
			if (ls != null) {
				out.addAll(ls);
			}
		}
		return out;
	}

	private Railways() {
	}

	/** Lines that start in this cell (from its capital east and south). */
	public static List<Line> lines(long seed, Cities.Terrain t, int cx, int cz) {
		if (seed != cacheSeed) {
			LINES.clear();
			cacheSeed = seed;
		}
		return LINES.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), k -> plan(seed, t, cx, cz));
	}

	/** Lines that may pass through the cell of (x, z): its own, and those from the cells west and north. */
	public static List<Line> near(long seed, Cities.Terrain t, int x, int z) {
		if (!Cities.railways) {
			return List.of();
		}
		int cx = Math.floorDiv(x, Cities.CELL);
		int cz = Math.floorDiv(z, Cities.CELL);
		List<Line> out = new ArrayList<>(lines(seed, t, cx, cz));
		out.addAll(lines(seed, t, cx - 1, cz));
		out.addAll(lines(seed, t, cx, cz - 1));
		return out;
	}

	/** Lines already planned near (x, z) (never plans anything: for the client-side and test questions). */
	public static List<Line> plannedNear(int x, int z, int reach) {
		List<Line> out = new ArrayList<>();
		for (List<Line> ls : LINES.values()) {
			for (Line l : ls) {
				if (l.near(x, z, reach)) {
					out.add(l);
				}
			}
		}
		return out;
	}

	private static List<Line> plan(long seed, Cities.Terrain t, int cx, int cz) {
		long t0 = System.nanoTime();
		try {
			return planCell(seed, t, cx, cz);
		} finally {
			planNanos += System.nanoTime() - t0;
		}
	}

	private static List<Line> planCell(long seed, Cities.Terrain t, int cx, int cz) {
		List<Line> out = new ArrayList<>();
		List<Cities.City> here = Cities.cities(seed, t, cx, cz);
		if (here.isEmpty() || here.getFirst().index != 0) {
			return out;
		}
		Cities.City a = here.getFirst();
		for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
			List<Cities.City> next = Cities.cities(seed, t, cx + d[0], cz + d[1]);
			if (next.isEmpty() || next.getFirst().index != 0) {
				continue;
			}
			Line l = null;
			// A few ways to lay it: the stations on either side of the highway, the diagonal early or late.
			for (int v = 0; v < 6 && l == null; v++) {
				l = line(seed, t, a, next.getFirst(), v);
			}
			if (l != null) {
				out.add(l);
				planned++;
			} else {
				refused++;
			}
		}
		return out;
	}

	/** The station's end of the line: a little way out of the town, to one side of the highway that leaves it that way. */
	private static int[] end(Cities.City c, Cities.City other, int side) {
		int[] e = Cities.edge(c, other.x, other.z);
		double dx = other.x - c.x;
		double dz = other.z - c.z;
		double len = Math.hypot(dx, dz);
		double sx = -dz / len * side;
		double sz = dx / len * side;
		return new int[]{(int) Math.round(e[0] + dx / len * 40 + sx * 40), (int) Math.round(e[1] + dz / len * 40 + sz * 40)};
	}

	private static void step(List<int[]> blocks, int dx, int dz, int count) {
		int[] last = blocks.getLast();
		int x = last[0];
		int z = last[1];
		for (int i = 0; i < count; i++) {
			x += dx;
			z += dz;
			blocks.add(new int[]{x, z});
		}
	}

	private static Line line(long seed, Cities.Terrain t, Cities.City a, Cities.City b, int variant) {
		int side = variant % 2 == 0 ? 1 : -1;
		int[] s = end(a, b, side);
		int[] e = end(b, a, -side);
		int dx = e[0] - s[0];
		int dz = e[1] - s[1];
		boolean alongX = Math.abs(dx) >= Math.abs(dz);
		int mx = alongX ? Integer.signum(dx) : 0;
		int mz = alongX ? 0 : Integer.signum(dz);
		// Straight out of the first station, straight into the last; between them a straight and a diagonal.
		int ix = dx - mx * RUN * 2;
		int iz = dz - mz * RUN * 2;
		if (alongX ? Integer.signum(ix) != mx : Integer.signum(iz) != mz) {
			return no("short");
		}
		int diag = Math.min(Math.abs(ix), Math.abs(iz));
		int straight = Math.max(Math.abs(ix), Math.abs(iz)) - diag;
		boolean restAlongX = Math.abs(ix) >= Math.abs(iz);
		int rx = restAlongX ? Integer.signum(ix) : 0;
		int rz = restAlongX ? 0 : Integer.signum(iz);
		int first = switch (variant / 2) {
			case 0 -> straight / 2;
			case 1 -> straight / 5;
			default -> straight * 4 / 5;
		};
		List<int[]> blocks = new ArrayList<>();
		blocks.add(s);
		step(blocks, mx, mz, RUN);
		step(blocks, rx, rz, first);
		step(blocks, Integer.signum(ix), Integer.signum(iz), diag);
		step(blocks, rx, rz, straight - first);
		step(blocks, mx, mz, RUN);
		int n = blocks.size();
		if (n < RUN * 2 + 40) {
			return no("short");
		}
		// Not through another town, a hamlet, a depot or a port.
		for (int i = 0; i < n; i += 12) {
			int bx = blocks.get(i)[0];
			int bz = blocks.get(i)[1];
			for (Cities.City c : Cities.citiesAround(seed, t, bx, bz)) {
				if (c.outside(bx, bz) < (c == a || c == b ? 16 : 40)) {
					return no(c == a || c == b ? "own town" : "other town");
				}
				for (Hamlets.Hamlet h : c.hamlets(seed, t)) {
					if (h.near(bx, bz, 20)) {
						return no("hamlet");
					}
				}
				Depots.Depot dp = c.depot(seed, t);
				if (dp != null && dp.near(bx, bz, 24)) {
					return no("depot");
				}
				Ports.Port pt = c.port(seed, t);
				if (pt != null && pt.near(bx, bz, 24)) {
					return no("port");
				}
			}
		}
		// Not across the sea (sampled first: cheaper than the roads).
		int sea = t.sea();
		int wet = 0;
		for (int i = 0; i < n; i += 8) {
			if (t.floor(blocks.get(i)[0], blocks.get(i)[1]) < sea - 1) {
				wet++;
			}
		}
		if (wet * 8 > 400) {
			return no("sea");
		}
		// The roads it crosses: over them on a bridge (never along one, never at its level).
		double[] over = new double[n];
		java.util.Arrays.fill(over, -1e9);
		boolean[] crossing = new boolean[n];
		long r0 = System.nanoTime();
		Set<Cities.Road> roads = new HashSet<>();
		for (int i = 0; i < n; i += 256) {
			roads.addAll(Cities.roadsNear(seed, t, blocks.get(i)[0], blocks.get(i)[1]));
		}
		roads.addAll(Cities.roadsNear(seed, t, blocks.getLast()[0], blocks.getLast()[1]));
		roadNanos += System.nanoTime() - r0;
		Cities.Road.Spot spot = new Cities.Road.Spot();
		int run = 0;
		for (int i = 0; i < n; i++) {
			int bx = blocks.get(i)[0];
			int bz = blocks.get(i)[1];
			boolean on = false;
			for (Cities.Road r : roads) {
				if (bx < r.minX - 24 || bx > r.maxX + 24 || bz < r.minZ - 24 || bz > r.maxZ + 24) {
					continue;
				}
				if (r.locate(bx + 0.5, bz + 0.5, r.half + 12, spot) && spot.along > -2 && spot.along < r.length + 2) {
					if (Math.abs(spot.across) <= r.half + 5) {
						on = true;
						over[i] = Math.max(over[i], r.height(spot.along) + 6);
					}
				}
			}
			crossing[i] = on;
			run = on ? run + 1 : 0;
			if (run > 40) {
				return no("along a road");
			}
			if (on && (i < RUN || i >= n - RUN)) {
				return no("road at the station");
			}
		}
		// The ground along it (every 8 blocks), the water as a floor a little above the sea.
		int samples = (n + 7) / 8 + 1;
		double[] ground = new double[samples];
		double[] lb = new double[samples];
		java.util.Arrays.fill(lb, -1e9);
		for (int k = 0; k < samples; k++) {
			int[] p = blocks.get(Math.min(n - 1, k * 8));
			ground[k] = Math.max(t.top(p[0], p[1]) + 1, sea + 3);
		}
		for (int i = 0; i < n; i++) {
			if (over[i] > -1e8) {
				int k = i / 8;
				lb[k] = Math.max(lb[k], over[i]);
				lb[Math.min(samples - 1, k + 1)] = Math.max(lb[Math.min(samples - 1, k + 1)], over[i]);
			}
		}
		// Smoothed (a running mean over ~100 blocks), the ends on their towns' ground, the grade kept gentle.
		double[] h = new double[samples];
		for (int k = 0; k < samples; k++) {
			double sum = 0;
			int cnt = 0;
			for (int j = Math.max(0, k - 6); j <= Math.min(samples - 1, k + 6); j++) {
				sum += ground[j];
				cnt++;
			}
			h[k] = sum / cnt;
		}
		// The stations level, on their towns' ground.
		int st = RUN / 8 + 1;
		for (int k = 0; k <= st && k < samples; k++) {
			h[k] = a.base + 1;
			h[samples - 1 - k] = b.base + 1;
		}
		double g = 8 * GRADE;
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 1; k < samples; k++) {
				h[k] = Math.min(Math.max(h[k], h[k - 1] - g), h[k - 1] + g);
			}
			for (int k = samples - 2; k >= 0; k--) {
				h[k] = Math.min(Math.max(h[k], h[k + 1] - g), h[k + 1] + g);
			}
		}
		// Over the roads: raised there, and the ramps up to it as gentle as the rest (only ever raised).
		for (int k = 0; k < samples; k++) {
			h[k] = Math.max(h[k], lb[k]);
		}
		for (int k = 1; k < samples; k++) {
			h[k] = Math.max(h[k], h[k - 1] - g);
		}
		for (int k = samples - 2; k >= 0; k--) {
			h[k] = Math.max(h[k], h[k + 1] - g);
		}
		if (h[0] > a.base + 1.5 || h[samples - 1] > b.base + 1.5) {
			return no("road too near the station to climb over");
		}
		int[] xs = new int[n];
		int[] zs = new int[n];
		double[] hs = new double[n];
		byte[] kind = new byte[n];
		byte[] dir = new byte[n];
		int tunnels = 0;
		int top = 0;
		int floor = 0;
		for (int i = 0; i < n; i++) {
			xs[i] = blocks.get(i)[0];
			zs[i] = blocks.get(i)[1];
			double f = i / 8.0;
			int k = Math.min(samples - 2, (int) f);
			hs[i] = h[k] + (h[k + 1] - h[k]) * (f - k);
		}
		for (int i = 0; i < n; i++) {
			int j0 = Math.max(0, i - 1);
			int j1 = Math.min(n - 1, i + 1);
			int ddx = Integer.signum(xs[j1] - xs[j0]);
			int ddz = Integer.signum(zs[j1] - zs[j0]);
			if (ddx == 0) {
				dir[i] = 0;
			} else if (ddz == 0) {
				dir[i] = 2;
			} else {
				dir[i] = (byte) (ddx == ddz ? 3 : 1);
			}
			int y = (int) Math.floor(hs[i]);
			if ((i & 1) == 0 || i == n - 1) {
				top = t.top(xs[i], zs[i]);
				floor = t.floor(xs[i], zs[i]);
			}
			if (crossing[i] || floor < sea - 1 || top < y - 5) {
				kind[i] = 1;
			} else if (top > y + 12 && !(i < STATION || i >= n - STATION)) {
				kind[i] = 2;
				tunnels++;
			}
		}
		// A road bridge's whole width (and a little past it) is bridge: no ramp of earth onto the road.
		for (int i = 0; i < n; i++) {
			if (crossing[i]) {
				for (int j = Math.max(0, i - 4); j <= Math.min(n - 1, i + 4); j++) {
					kind[j] = 1;
				}
			}
		}
		if (tunnels > 900) {
			return no("tunnels");
		}
		return new Line(a.key() * 31 + b.key(), xs, zs, hs, kind, dir, side);
	}
}
