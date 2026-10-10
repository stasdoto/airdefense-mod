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

		/**
		 * What block i is: 0 on the ground (an embankment, a cutting), 1 on a bridge (water, a deep valley), 2 in a tunnel.
		 * From the terrain's estimate at every fourth block, worked out as the land is made (the samples are dear) and kept.
		 */
		public int kind(Cities.Terrain t, int i) {
			byte k = kind[i];
			if (k >= 0) {
				return k;
			}
			int e = Math.min(xs.length - 1, i & ~3);
			int top = t.top(xs[e], zs[e]);
			int sea = t.sea();
			int y = (int) Math.floor(hs[i]);
			if (top <= sea && t.floor(xs[e], zs[e]) < sea - 1 || top < y - 5) {
				k = 1;
			} else if (top > y + 12 && !station(i)) {
				k = 2;
			} else {
				k = 0;
			}
			kind[i] = k;
			return k;
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
	/** For the tests: why ways were given up (by reason), and the time spent planning. */
	public static final java.util.concurrent.ConcurrentHashMap<String, Integer> WHY = new java.util.concurrent.ConcurrentHashMap<>();
	public static volatile long planNanos;

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
		// Not through a town (the hamlets, depots, ports and roads are planned round the railways, not the other way: so a
		// railway needs only the towns planned, and planning one is cheap).
		for (int i = 0; i < n; i += 12) {
			int bx = blocks.get(i)[0];
			int bz = blocks.get(i)[1];
			for (Cities.City c : Cities.citiesAround(seed, t, bx, bz)) {
				if (c.outside(bx, bz) < (c == a || c == b ? 16 : 40)) {
					return no(c == a || c == b ? "own town" : "other town");
				}
			}
		}
		// The ground along it every 16 blocks (the terrain's estimate is dear: as few samples as will do), the water as a
		// floor a little above the sea; not across the sea.
		int sea = t.sea();
		int samples = (n + 15) / 16 + 1;
		double[] ground = new double[samples];
		int wet = 0;
		int high = 0;
		for (int k = 0; k < samples; k++) {
			int[] p = blocks.get(Math.min(n - 1, k * 16));
			int top = t.top(p[0], p[1]);
			if (top <= sea && t.floor(p[0], p[1]) < sea - 1) {
				wet++;
				if (wet * 16 > 400) {
					return no("sea");
				}
			}
			ground[k] = Math.max(top + 1, sea + 3);
		}
		// Smoothed (a running mean over ~100 blocks), the ends on their towns' ground, the grade kept gentle.
		double[] h = new double[samples];
		for (int k = 0; k < samples; k++) {
			double sum = 0;
			int cnt = 0;
			for (int j = Math.max(0, k - 3); j <= Math.min(samples - 1, k + 3); j++) {
				sum += ground[j];
				cnt++;
			}
			h[k] = sum / cnt;
		}
		// The stations level, on the ground where they stand (not down in a cutting, not up on a bank).
		int st = RUN / 16 + 1;
		double s0 = 0;
		double s1 = 0;
		int cnt = 0;
		for (int k = 0; k <= st && k < samples; k++) {
			s0 += ground[k];
			s1 += ground[samples - 1 - k];
			cnt++;
		}
		for (int k = 0; k <= st && k < samples; k++) {
			h[k] = Math.round(s0 / cnt);
			h[samples - 1 - k] = Math.round(s1 / cnt);
		}
		double g = 16 * GRADE;
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 1; k < samples; k++) {
				h[k] = Math.min(Math.max(h[k], h[k - 1] - g), h[k - 1] + g);
			}
			for (int k = samples - 2; k >= 0; k--) {
				h[k] = Math.min(Math.max(h[k], h[k + 1] - g), h[k + 1] + g);
			}
		}
		// Through the hills: how much of it would be in tunnels (too much, and it goes another way).
		for (int k = 0; k < samples; k++) {
			if (ground[k] > h[k] + 13) {
				high++;
			}
		}
		if (high * 16 > 900) {
			return no("tunnels");
		}
		int[] xs = new int[n];
		int[] zs = new int[n];
		double[] hs = new double[n];
		byte[] kind = new byte[n];
		java.util.Arrays.fill(kind, (byte) -1);
		byte[] dir = new byte[n];
		for (int i = 0; i < n; i++) {
			xs[i] = blocks.get(i)[0];
			zs[i] = blocks.get(i)[1];
			double f = i / 16.0;
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
		}
		return new Line(a.key() * 31 + b.key(), xs, zs, hs, kind, dir, side);
	}
	/** Is any railway within {@code reach} of (x, z)? (For the hamlets, depots and ports: they keep clear of the lines.) */
	public static boolean close(long seed, Cities.Terrain t, int x, int z, int reach) {
		Spot s = new Spot();
		for (Line l : near(seed, t, x, z)) {
			if (l.near(x, z, reach) && l.locate(x + 0.5, z + 0.5, 0, l.length(), reach, s)) {
				return true;
			}
		}
		return false;
	}

	/** For the tests: roads raised over a railway. */
	public static volatile int bridged;

	/**
	 * Raises a road where it crosses a railway: a bridge high enough over the track for the trains (with their
	 * pantographs), the ramps up to it as gentle as the road's own grades.
	 */
	public static void raiseRoad(long seed, Cities.Terrain t, Cities.Road r) {
		if (!Cities.railways || r == null || r.px.length < 2) {
			return;
		}
		Set<Line> lines = new HashSet<>(near(seed, t, r.x0, r.z0));
		lines.addAll(near(seed, t, r.x1, r.z1));
		double[] mid = r.pointAt(r.length / 2);
		lines.addAll(near(seed, t, (int) mid[0], (int) mid[1]));
		float[] hs = r.heights;
		int m = hs.length;
		double[] lb = new double[m];
		java.util.Arrays.fill(lb, -1e9);
		boolean any = false;
		for (Line l : lines) {
			if (r.maxX < l.minX - 4 || r.minX > l.maxX + 4 || r.maxZ < l.minZ - 4 || r.minZ > l.maxZ + 4) {
				continue;
			}
			int n = l.length();
			for (int i = 0; i + 1 < r.px.length; i++) {
				double ax = r.px[i];
				double az = r.pz[i];
				double bx = r.px[i + 1];
				double bz = r.pz[i + 1];
				double x0 = Math.min(ax, bx) - 1;
				double x1 = Math.max(ax, bx) + 1;
				double z0 = Math.min(az, bz) - 1;
				double z1 = Math.max(az, bz) + 1;
				if (x1 < l.minX || x0 > l.maxX + 1 || z1 < l.minZ || z0 > l.maxZ + 1) {
					continue;
				}
				for (int j0 = 0; j0 + 1 < n; j0 += 32) {
					int j1 = Math.min(n - 1, j0 + 32);
					int lx0 = Math.min(l.xs[j0], l.xs[j1]);
					int lx1 = Math.max(l.xs[j0], l.xs[j1]) + 1;
					int lz0 = Math.min(l.zs[j0], l.zs[j1]);
					int lz1 = Math.max(l.zs[j0], l.zs[j1]) + 1;
					if (lx1 < x0 || lx0 > x1 || lz1 < z0 || lz0 > z1) {
						continue;
					}
					for (int j = j0; j < j1; j++) {
						double cx = l.xs[j] + 0.5;
						double cz = l.zs[j] + 0.5;
						double dx = l.xs[j + 1] - l.xs[j];
						double dz = l.zs[j + 1] - l.zs[j];
						double ex = bx - ax;
						double ez = bz - az;
						double den = ex * dz - ez * dx;
						if (Math.abs(den) < 1e-9) {
							continue;
						}
						double s = ((cx - ax) * dz - (cz - az) * dx) / den;
						double u = ((cx - ax) * ez - (cz - az) * ex) / den;
						if (s < 0 || s > 1 || u < 0 || u > 1) {
							continue;
						}
						double along = r.cum[i] + s * Math.hypot(ex, ez);
						double rail = l.hs[j] + (l.hs[j + 1] - l.hs[j]) * u;
						int k0 = (int) Math.floor((along - r.half - 8) / Cities.Road.STEP);
						int k1 = (int) Math.ceil((along + r.half + 8) / Cities.Road.STEP);
						for (int k = Math.max(0, k0); k <= Math.min(m - 1, k1); k++) {
							lb[k] = Math.max(lb[k], rail + 7);
						}
						any = true;
					}
				}
			}
		}
		if (!any) {
			return;
		}
		double g = (r.highway ? 0.065 : 0.1) * Cities.Road.STEP;
		for (int k = 0; k < m; k++) {
			hs[k] = (float) Math.max(hs[k], lb[k]);
		}
		for (int k = 1; k < m; k++) {
			hs[k] = (float) Math.max(hs[k], hs[k - 1] - g);
		}
		for (int k = m - 2; k >= 0; k--) {
			hs[k] = (float) Math.max(hs[k], hs[k + 1] - g);
		}
		bridged++;
	}
}
