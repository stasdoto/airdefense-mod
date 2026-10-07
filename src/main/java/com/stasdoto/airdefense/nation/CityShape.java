package com.stasdoto.airdefense.nation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The street plan of one city (stage R8.2): a grid whose streets are not evenly spaced (blocks 26-38 metres across),
 * with an outline of its own - a blob stretched one way, with arms running out along a street or two, a few blocks
 * joined into big ones (factories, markets, parks). Each block (or joined pair) is a lot with a district: the city
 * hall in the middle, downtown, the panel-block belt, the villa outskirts, an industrial quarter on one side, parks
 * and wasteland. Worked out from the city's seed alone.
 */
public final class CityShape {
	public static final int SH = Cities.STREET_HALF;

	public static final int HALL = 0;
	public static final int DOWNTOWN = 1;
	public static final int MID = 2;
	public static final int OUTER = 3;
	public static final int INDUSTRY = 4;
	public static final int PARK = 5;
	public static final int VACANT = 6;

	/** A lot: one block or two joined; its inside (between the pavements) and its district. */
	public static final class Lot {
		public final int id;
		public final int i0;
		public final int j0;
		public final int i1;
		public final int j1;
		public final int x0;
		public final int z0;
		public final int x1;
		public final int z1;
		public int district;

		Lot(int id, int i0, int j0, int i1, int j1, int[] gx, int[] gz) {
			this.id = id;
			this.i0 = i0;
			this.j0 = j0;
			this.i1 = i1;
			this.j1 = j1;
			this.x0 = gx[i0] + SH + 2;
			this.x1 = gx[i1 + 1] - SH - 2;
			this.z0 = gz[j0] + SH + 2;
			this.z1 = gz[j1 + 1] - SH - 2;
		}

		public int width() {
			return x1 - x0 + 1;
		}

		public int depth() {
			return z1 - z0 + 1;
		}

		public boolean merged() {
			return i0 != i1 || j0 != j1;
		}

		public int cx() {
			return (x0 + x1) / 2;
		}

		public int cz() {
			return (z0 + z1) / 2;
		}
	}

	/** What a column is: the street (with how far from the nearest street lines), the pavement, a lot, or outside. */
	public static final class Probe {
		public boolean street;
		public boolean kerb;
		/** On the part of a street running north-south / east-west (both at a crossing). */
		public boolean onV;
		public boolean onH;
		public int dv;
		public int dh;
		/** There is a crossing at the nearest grid node. */
		public boolean node;
		public int lot = -1;
	}

	public final int n;
	public final int[] gx;
	public final int[] gz;
	private final boolean[] on;
	private final int[] lotOf;
	public final List<Lot> lots = new ArrayList<>();
	public final int ci;
	public final int cj;
	public final int minX;
	public final int maxX;
	public final int minZ;
	public final int maxZ;

	CityShape(Cities.City c) {
		Random r = new Random(c.seed ^ 0x5A4FE11L);
		n = c.size.n + 2;
		int mid = n / 2;
		ci = mid;
		cj = mid;
		gx = lines(r, c.x, mid);
		gz = lines(r, c.z, mid);
		on = new boolean[n * n];
		lotOf = new int[n * n];
		Arrays.fill(lotOf, -1);
		// The outline: a stretched, turned blob with ragged edges.
		double aspect = 0.7 + r.nextDouble() * 0.6;
		double ang = r.nextDouble() * Math.PI;
		double radius = c.size.n / 2.0 + 0.55;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double u = i - mid;
				double v = j - mid;
				double ur = u * Math.cos(ang) - v * Math.sin(ang);
				double vr = u * Math.sin(ang) + v * Math.cos(ang);
				double d = Math.sqrt(ur * ur / aspect + vr * vr * aspect) / radius;
				on[i * n + j] = d + 0.45 * (r.nextDouble() * 2 - 1) < 1.0;
			}
		}
		// Arms along a street or two, out to the edge of the canvas.
		int arms = 1 + r.nextInt(2);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int a = 0; a < arms; a++) {
			int[] d = dirs[r.nextInt(4)];
			int len = mid - r.nextInt(2);
			for (int k = 0; k <= len; k++) {
				set(mid + d[0] * k, mid + d[1] * k, true);
			}
		}
		set(mid, mid, true);
		keepConnected();
		int[] range = switch (c.size) {
			case SMALL -> new int[]{5, 10};
			case MEDIUM -> new int[]{12, 22};
			case LARGE -> new int[]{24, 42};
		};
		for (int guard = 0; guard < 200 && count() < range[0]; guard++) {
			grow(r);
		}
		for (int guard = 0; guard < 200 && count() > range[1]; guard++) {
			shrink(r);
		}
		// Joined blocks.
		int merges = switch (c.size) {
			case SMALL -> r.nextInt(2);
			case MEDIUM -> 1 + r.nextInt(2);
			case LARGE -> 3 + r.nextInt(3);
		};
		List<int[]> pairs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (isOn(i, j) && isOn(i + 1, j)) {
					pairs.add(new int[]{i, j, i + 1, j});
				}
				if (isOn(i, j) && isOn(i, j + 1)) {
					pairs.add(new int[]{i, j, i, j + 1});
				}
			}
		}
		java.util.Collections.shuffle(pairs, r);
		for (int[] p : pairs) {
			if (merges <= 0) {
				break;
			}
			boolean centre = p[0] == ci && p[1] == cj || p[2] == ci && p[3] == cj;
			if (centre || lotOf[p[0] * n + p[1]] >= 0 || lotOf[p[2] * n + p[3]] >= 0) {
				continue;
			}
			Lot lot = new Lot(lots.size(), Math.min(p[0], p[2]), Math.min(p[1], p[3]), Math.max(p[0], p[2]), Math.max(p[1], p[3]), gx, gz);
			lots.add(lot);
			lotOf[p[0] * n + p[1]] = lot.id;
			lotOf[p[2] * n + p[3]] = lot.id;
			merges--;
		}
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (isOn(i, j) && lotOf[i * n + j] < 0) {
					Lot lot = new Lot(lots.size(), i, j, i, j, gx, gz);
					lots.add(lot);
					lotOf[i * n + j] = lot.id;
				}
			}
		}
		districts(c, r);
		int ax = Integer.MAX_VALUE;
		int bx = Integer.MIN_VALUE;
		int az = Integer.MAX_VALUE;
		int bz = Integer.MIN_VALUE;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (isOn(i, j)) {
					ax = Math.min(ax, gx[i] - SH - 1);
					bx = Math.max(bx, gx[i + 1] + SH + 1);
					az = Math.min(az, gz[j] - SH - 1);
					bz = Math.max(bz, gz[j + 1] + SH + 1);
				}
			}
		}
		minX = ax;
		maxX = bx;
		minZ = az;
		maxZ = bz;
	}

	/** Street lines across the canvas: the middle block 34 wide (the city hall square), the others 26-38. */
	private int[] lines(Random r, int centre, int mid) {
		int[] w = new int[n];
		int before = 0;
		for (int i = 0; i < n; i++) {
			w[i] = i == mid ? 34 : 26 + r.nextInt(13);
			if (i < mid) {
				before += w[i];
			}
		}
		int[] g = new int[n + 1];
		g[0] = centre - before - w[mid] / 2;
		for (int i = 0; i < n; i++) {
			g[i + 1] = g[i] + w[i];
		}
		return g;
	}

	private void set(int i, int j, boolean v) {
		if (i >= 0 && j >= 0 && i < n && j < n) {
			on[i * n + j] = v;
		}
	}

	public boolean isOn(int i, int j) {
		return i >= 0 && j >= 0 && i < n && j < n && on[i * n + j];
	}

	private int count() {
		int k = 0;
		for (boolean b : on) {
			if (b) {
				k++;
			}
		}
		return k;
	}

	private void keepConnected() {
		boolean[] seen = new boolean[n * n];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[]{ci, cj});
		seen[ci * n + cj] = true;
		while (!q.isEmpty()) {
			int[] p = q.poll();
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int a = p[0] + d[0];
				int b = p[1] + d[1];
				if (isOn(a, b) && !seen[a * n + b]) {
					seen[a * n + b] = true;
					q.add(new int[]{a, b});
				}
			}
		}
		for (int k = 0; k < n * n; k++) {
			on[k] &= seen[k];
		}
	}

	private void grow(Random r) {
		List<int[]> edge = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (!isOn(i, j) && (isOn(i + 1, j) || isOn(i - 1, j) || isOn(i, j + 1) || isOn(i, j - 1))) {
					edge.add(new int[]{i, j});
				}
			}
		}
		if (!edge.isEmpty()) {
			int[] p = edge.get(r.nextInt(edge.size()));
			set(p[0], p[1], true);
		}
	}

	private void shrink(Random r) {
		List<int[]> leaves = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (isOn(i, j) && !(i == ci && j == cj)) {
					int k = (isOn(i + 1, j) ? 1 : 0) + (isOn(i - 1, j) ? 1 : 0) + (isOn(i, j + 1) ? 1 : 0) + (isOn(i, j - 1) ? 1 : 0);
					if (k <= 1) {
						leaves.add(new int[]{i, j});
					}
				}
			}
		}
		if (leaves.isEmpty()) {
			// A compact blob: nibble at the outermost cells with the fewest neighbours.
			int best = Integer.MIN_VALUE;
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (!isOn(i, j) || i == ci && j == cj) {
						continue;
					}
					int k = (isOn(i + 1, j) ? 1 : 0) + (isOn(i - 1, j) ? 1 : 0) + (isOn(i, j + 1) ? 1 : 0) + (isOn(i, j - 1) ? 1 : 0);
					int score = (Math.abs(i - ci) + Math.abs(j - cj)) * 4 - k;
					if (score > best) {
						best = score;
						leaves.clear();
					}
					if (score == best) {
						leaves.add(new int[]{i, j});
					}
				}
			}
		}
		if (!leaves.isEmpty()) {
			int[] p = leaves.get(r.nextInt(leaves.size()));
			set(p[0], p[1], false);
			keepConnected();
		}
	}

	private void districts(Cities.City c, Random r) {
		int maxRing = 1;
		for (Lot l : lots) {
			maxRing = Math.max(maxRing, ring(l));
		}
		double sector = r.nextDouble() * Math.PI * 2;
		List<Lot> industry = new ArrayList<>();
		for (Lot l : lots) {
			double norm = (double) ring(l) / maxRing;
			if (l.i0 == ci && l.j0 == cj) {
				l.district = HALL;
				continue;
			}
			double a = Math.atan2(l.cz() - c.z, l.cx() - c.x);
			double diff = Math.abs(Math.atan2(Math.sin(a - sector), Math.cos(a - sector)));
			if (diff < Math.toRadians(55) && norm >= 0.5) {
				l.district = INDUSTRY;
				industry.add(l);
				continue;
			}
			double downtown = c.size == Cities.Size.LARGE ? 0.34 : c.size == Cities.Size.MEDIUM ? 0.25 : 0;
			l.district = norm <= downtown ? DOWNTOWN : norm <= 0.67 ? MID : OUTER;
		}
		// Every town has some industry: the outermost lot on that side, if the sector caught none.
		int minIndustry = c.size == Cities.Size.LARGE ? 3 : c.size == Cities.Size.MEDIUM ? 2 : 1;
		while (industry.size() < Math.min(minIndustry, lots.size() / 3 + 1)) {
			Lot best = null;
			double bestScore = -1e9;
			for (Lot l : lots) {
				if (l.district == HALL || l.district == INDUSTRY) {
					continue;
				}
				double score = Math.cos(Math.atan2(l.cz() - c.z, l.cx() - c.x) - sector) * 10 + ring(l);
				if (score > bestScore) {
					bestScore = score;
					best = l;
				}
			}
			if (best == null) {
				break;
			}
			best.district = INDUSTRY;
			industry.add(best);
		}
		// Parks in the middle belt, a patch of wasteland or two at the edge.
		int parks = c.size == Cities.Size.LARGE ? 1 + r.nextInt(2) : c.size == Cities.Size.MEDIUM ? r.nextInt(2) : 0;
		int vacant = r.nextInt(c.size == Cities.Size.SMALL ? 2 : 3);
		List<Lot> shuffled = new ArrayList<>(lots);
		java.util.Collections.shuffle(shuffled, r);
		for (Lot l : shuffled) {
			if (parks > 0 && (l.district == MID || l.district == DOWNTOWN) && l.width() >= 21 && l.depth() >= 21) {
				l.district = PARK;
				parks--;
			} else if (vacant > 0 && l.district == OUTER) {
				l.district = VACANT;
				vacant--;
			}
		}
	}

	private int ring(Lot l) {
		return Math.max(Math.max(Math.abs(l.i0 - ci), Math.abs(l.i1 - ci)), Math.max(Math.abs(l.j0 - cj), Math.abs(l.j1 - cj)));
	}

	public int lotAt(int i, int j) {
		return i >= 0 && j >= 0 && i < n && j < n ? lotOf[i * n + j] : -1;
	}

	/** A north-south street on line k along row j. */
	public boolean segV(int k, int j) {
		if (j < 0 || j >= n || k < 0 || k > n) {
			return false;
		}
		boolean a = isOn(k - 1, j);
		boolean b = isOn(k, j);
		if (!a && !b) {
			return false;
		}
		return !(a && b && lotAt(k - 1, j) == lotAt(k, j));
	}

	/** An east-west street on line l along column i. */
	public boolean segH(int l, int i) {
		if (i < 0 || i >= n || l < 0 || l > n) {
			return false;
		}
		boolean a = isOn(i, l - 1);
		boolean b = isOn(i, l);
		if (!a && !b) {
			return false;
		}
		return !(a && b && lotAt(i, l - 1) == lotAt(i, l));
	}

	public boolean node(int k, int l) {
		return segV(k, l - 1) || segV(k, l) || segH(l, k - 1) || segH(l, k);
	}

	/** Index of the cell (between lines) holding v, or -1 / n outside. */
	private static int cell(int[] g, int v) {
		if (v < g[0]) {
			return -1;
		}
		if (v >= g[g.length - 1]) {
			return g.length - 1;
		}
		int lo = 0;
		int hi = g.length - 1;
		while (hi - lo > 1) {
			int m = (lo + hi) >>> 1;
			if (g[m] <= v) {
				lo = m;
			} else {
				hi = m;
			}
		}
		return lo;
	}

	private static int nearest(int[] g, int v) {
		int c = cell(g, v);
		if (c < 0) {
			return 0;
		}
		if (c >= g.length - 1) {
			return g.length - 1;
		}
		return v - g[c] <= g[c + 1] - v ? c : c + 1;
	}

	public Probe probe(int x, int z) {
		Probe p = new Probe();
		if (x < minX || x > maxX || z < minZ || z > maxZ) {
			return p;
		}
		int kv = nearest(gx, x);
		int kh = nearest(gz, z);
		int col = cell(gx, x);
		int row = cell(gz, z);
		p.dv = Math.abs(x - gx[kv]);
		p.dh = Math.abs(z - gz[kh]);
		p.node = node(kv, kh);
		boolean nearNode = p.dv <= SH && p.dh <= SH && p.node;
		p.onV = p.dv <= SH && (segV(kv, row) || nearNode);
		p.onH = p.dh <= SH && (segH(kh, col) || nearNode);
		p.street = p.onV || p.onH;
		if (!p.street) {
			boolean nearNodeK = p.dv <= SH + 1 && p.dh <= SH + 1 && p.node;
			p.kerb = p.dv == SH + 1 && (segV(kv, row) || nearNodeK) || p.dh == SH + 1 && (segH(kh, col) || nearNodeK);
			p.lot = lotAt(col, row);
		}
		return p;
	}

	/** Part of the city (street, pavement or lot). */
	public boolean inside(int x, int z) {
		Probe p = probe(x, z);
		return p.street || p.kerb || p.lot >= 0;
	}

	/** How far outside the city (0 inside). */
	public int outside(int x, int z) {
		if (inside(x, z)) {
			return 0;
		}
		int best = Integer.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (!isOn(i, j)) {
					continue;
				}
				int dx = Math.max(0, Math.max(gx[i] - SH - 1 - x, x - gx[i + 1] - SH - 1));
				int dz = Math.max(0, Math.max(gz[j] - SH - 1 - z, z - gz[j + 1] - SH - 1));
				best = Math.min(best, Math.max(dx, dz));
			}
		}
		return Math.max(1, best);
	}

	public Lot hallLot() {
		return lots.get(lotAt(ci, cj));
	}
}
