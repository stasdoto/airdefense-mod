package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Lays out roads over the land like real ones (1.25): a search over a coarse grid finds the cheapest way from one end
 * to the other, where steep climbs, lakes and other towns cost a lot, and a gentle "wander" field keeps it from running
 * dead straight even over flat country; the path is then rounded off into curves and given its heights, smoothed so
 * the road climbs gently (embankments and cuttings take up the rest, rivers are crossed on bridges).
 */
final class RoadPlanner {
	/** Something the road should keep out of (another town). */
	interface Avoid {
		/** Extra cost factor for passing (x, z): 0 = nothing there. */
		double at(int x, int z);
	}

	private RoadPlanner() {
	}

	/**
	 * The road's line from (x0, z0) to (x1, z1): leaves each end straight for {@code lead} blocks along
	 * {@code (dx0, dz0)} / {@code (dx1, dz1)} (out of the towns' streets), the rest found over a grid of {@code grid}
	 * blocks. Returns {xs, zs} with a point every 4 blocks.
	 */
	static float[][] route(Cities.Terrain t, long seed, int x0, int z0, double dx0, double dz0, int x1, int z1, double dx1, double dz1, int grid,
			int lead, Avoid avoid) {
		double sx = x0 + dx0 * lead;
		double sz = z0 + dz0 * lead;
		double ex = x1 + dx1 * lead;
		double ez = z1 + dz1 * lead;
		double len = Math.hypot(ex - sx, ez - sz);
		List<double[]> pts = new ArrayList<>();
		pts.add(new double[]{x0, z0});
		pts.add(new double[]{sx, sz});
		if (len > grid * 2.5) {
			pts.addAll(search(t, seed, sx, sz, ex, ez, grid, avoid));
		}
		pts.add(new double[]{ex, ez});
		pts.add(new double[]{x1, z1});
		// Round the corners (Chaikin), keeping both ends and the straight lead-outs.
		for (int pass = 0; pass < 4; pass++) {
			List<double[]> o = new ArrayList<>();
			o.add(pts.get(0));
			o.add(pts.get(1));
			for (int i = 1; i < pts.size() - 2; i++) {
				double[] a = pts.get(i);
				double[] b = pts.get(i + 1);
				o.add(new double[]{a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25});
				o.add(new double[]{a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75});
			}
			o.add(pts.get(pts.size() - 2));
			o.add(pts.get(pts.size() - 1));
			pts = o;
		}
		return resample(pts, 4.0);
	}

	/** The cheapest grid path between two points (both included, as grid nodes snapped to the ends). */
	private static List<double[]> search(Cities.Terrain t, long seed, double sx, double sz, double ex, double ez, int g, Avoid avoid) {
		double len = Math.hypot(ex - sx, ez - sz);
		double corridor = Math.max(g * 5.0, len * 0.38);
		int ix0 = (int) Math.floor((Math.min(sx, ex) - corridor) / g);
		int iz0 = (int) Math.floor((Math.min(sz, ez) - corridor) / g);
		int ix1 = (int) Math.ceil((Math.max(sx, ex) + corridor) / g);
		int iz1 = (int) Math.ceil((Math.max(sz, ez) + corridor) / g);
		int w = ix1 - ix0 + 1;
		int h = iz1 - iz0 + 1;
		int n = w * h;
		float[] height = new float[n];
		Arrays.fill(height, Float.NaN);
		double[] cost = new double[n];
		Arrays.fill(cost, Double.MAX_VALUE);
		int[] from = new int[n];
		Arrays.fill(from, -1);
		boolean[] done = new boolean[n];
		int start = idx(sx, sz, g, ix0, iz0, w, h);
		int goal = idx(ex, ez, g, ix0, iz0, w, h);
		int[][] moves = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 1}, {1, 2}, {-1, 2}, {-2, 1}, {-2, -1},
				{-1, -2}, {1, -2}, {2, -1}};
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		cost[start] = 0;
		open.add(new double[]{0, start});
		double ux = (ex - sx) / Math.max(1, len);
		double uz = (ez - sz) / Math.max(1, len);
		int sea = t.sea();
		int expanded = 0;
		while (!open.isEmpty()) {
			double[] top = open.poll();
			int cur = (int) top[1];
			if (done[cur]) {
				continue;
			}
			done[cur] = true;
			if (cur == goal || ++expanded > 60000) {
				break;
			}
			int ci = cur % w;
			int cj = cur / w;
			double cx = (ci + ix0) * (double) g;
			double cz = (cj + iz0) * (double) g;
			float ch = heightAt(t, height, cur, cx, cz);
			for (int[] m : moves) {
				int ni = ci + m[0];
				int nj = cj + m[1];
				if (ni < 0 || nj < 0 || ni >= w || nj >= h) {
					continue;
				}
				int nb = nj * w + ni;
				if (done[nb]) {
					continue;
				}
				double nx = (ni + ix0) * (double) g;
				double nz = (nj + iz0) * (double) g;
				// Stay in the corridor round the straight line.
				double rx = nx - sx;
				double rz = nz - sz;
				double off = Math.abs(rx * -uz + rz * ux);
				if (off > corridor) {
					continue;
				}
				float nh = heightAt(t, height, nb, nx, nz);
				double d = g * Math.hypot(m[0], m[1]);
				double grade = Math.abs(nh - ch) / d;
				double c = d * (1 + Math.min(12, 1.3 * Math.pow(grade / 0.06, 2)));
				if (nh < sea || ch < sea) {
					// Water: a bridge, dear - better round a lake, straight over a river.
					c += d * 7;
				}
				double a = avoid == null ? 0 : avoid.at((int) nx, (int) nz);
				c += d * a * 25;
				c *= 1 + 0.3 * wander(seed, nx, nz);
				double nc = cost[cur] + c;
				if (nc < cost[nb]) {
					cost[nb] = nc;
					from[nb] = cur;
					open.add(new double[]{nc + Math.hypot(ex - nx, ez - nz), nb});
				}
			}
		}
		List<double[]> path = new ArrayList<>();
		if (from[goal] < 0 && goal != start) {
			// Nothing found (too much water): straight across.
			return path;
		}
		for (int c = goal; c >= 0; c = from[c]) {
			path.add(0, new double[]{(c % w + ix0) * (double) g, (c / w + iz0) * (double) g});
			if (c == start) {
				break;
			}
		}
		// The snapped grid ends are replaced by the exact lead-out points (no little doubling back).
		if (path.size() >= 2) {
			path.removeFirst();
			path.removeLast();
		}
		return path;
	}

	private static int idx(double x, double z, int g, int ix0, int iz0, int w, int h) {
		int i = Math.max(0, Math.min(w - 1, (int) Math.round(x / g) - ix0));
		int j = Math.max(0, Math.min(h - 1, (int) Math.round(z / g) - iz0));
		return j * w + i;
	}

	private static float heightAt(Cities.Terrain t, float[] cache, int i, double x, double z) {
		float v = cache[i];
		if (Float.isNaN(v)) {
			v = t.top((int) Math.round(x), (int) Math.round(z));
			cache[i] = v;
		}
		return v;
	}

	/** A smooth 0..1 field (about 220 blocks across) that makes roads wander over flat land. */
	static double wander(long seed, double x, double z) {
		double s = 220;
		double fx = x / s;
		double fz = z / s;
		int ix = (int) Math.floor(fx);
		int iz = (int) Math.floor(fz);
		double tx = fx - ix;
		double tz = fz - iz;
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		double a = cell(seed, ix, iz);
		double b = cell(seed, ix + 1, iz);
		double c = cell(seed, ix, iz + 1);
		double d = cell(seed, ix + 1, iz + 1);
		return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
	}

	private static double cell(long seed, int x, int z) {
		long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	/** Points every {@code step} blocks along a polyline. */
	private static float[][] resample(List<double[]> pts, double step) {
		double total = 0;
		for (int i = 1; i < pts.size(); i++) {
			total += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
		}
		int n = Math.max(2, (int) Math.ceil(total / step) + 1);
		float[] xs = new float[n];
		float[] zs = new float[n];
		int seg = 1;
		double segStart = 0;
		double segLen = Math.hypot(pts.get(1)[0] - pts.get(0)[0], pts.get(1)[1] - pts.get(0)[1]);
		for (int k = 0; k < n; k++) {
			double want = Math.min(total, k * total / (n - 1));
			while (seg < pts.size() - 1 && want > segStart + segLen) {
				segStart += segLen;
				seg++;
				segLen = Math.hypot(pts.get(seg)[0] - pts.get(seg - 1)[0], pts.get(seg)[1] - pts.get(seg - 1)[1]);
			}
			double f = segLen < 1e-9 ? 0 : (want - segStart) / segLen;
			f = Math.max(0, Math.min(1, f));
			double[] a = pts.get(seg - 1);
			double[] b = pts.get(seg);
			xs[k] = (float) (a[0] + (b[0] - a[0]) * f);
			zs[k] = (float) (a[1] + (b[1] - a[1]) * f);
		}
		return new float[][]{xs, zs};
	}

	/**
	 * Heights every {@link Cities.Road#STEP} blocks along the line: the land, smoothed, eased into each end's level,
	 * then held to a gentle climb ({@code maxGrade}) - the road cuts into hills and runs on embankments.
	 */
	static float[] heights(Cities.Terrain t, float[] xs, float[] zs, int y0, int y1, double maxGrade) {
		double[] cum = new double[xs.length];
		for (int i = 1; i < xs.length; i++) {
			cum[i] = cum[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
		}
		double l = cum[cum.length - 1];
		int n = Math.max(2, (int) Math.ceil(l / Cities.Road.STEP) + 1);
		float[] hs = new float[n];
		int seg = 0;
		for (int i = 0; i < n; i++) {
			double want = Math.min(l, i * (double) Cities.Road.STEP);
			while (seg < xs.length - 2 && cum[seg + 1] < want) {
				seg++;
			}
			double sl = Math.max(1e-6, cum[seg + 1] - cum[seg]);
			double f = Math.max(0, Math.min(1, (want - cum[seg]) / sl));
			int px = (int) Math.round(xs[seg] + (xs[seg + 1] - xs[seg]) * f);
			int pz = (int) Math.round(zs[seg] + (zs[seg + 1] - zs[seg]) * f);
			hs[i] = Math.max(t.sea() + 1, t.top(px, pz));
		}
		for (int pass = 0; pass < 6; pass++) {
			float[] o = hs.clone();
			for (int i = 1; i < n - 1; i++) {
				hs[i] = (o[i - 1] + 2 * o[i] + o[i + 1]) / 4f;
			}
		}
		for (int i = 0; i < n; i++) {
			double s = i * (double) Cities.Road.STEP;
			double fa = Math.max(0, 1 - s / 64.0);
			double fb = Math.max(0, 1 - (l - s) / 64.0);
			hs[i] = (float) (hs[i] * (1 - fa - fb) + y0 * fa + y1 * fb);
			if (fa + fb > 1) {
				hs[i] = (float) ((y0 * fa + y1 * fb) / (fa + fb));
			}
		}
		hs[0] = y0;
		hs[n - 1] = y1;
		// A gentle climb: forwards and backwards, no step steeper than the grade allows.
		float max = (float) (maxGrade * Cities.Road.STEP);
		for (int round = 0; round < 3; round++) {
			for (int i = 1; i < n - 1; i++) {
				hs[i] = Math.max(hs[i - 1] - max, Math.min(hs[i - 1] + max, hs[i]));
			}
			for (int i = n - 2; i >= 1; i--) {
				hs[i] = Math.max(hs[i + 1] - max, Math.min(hs[i + 1] + max, hs[i]));
			}
		}
		return hs;
	}
}
