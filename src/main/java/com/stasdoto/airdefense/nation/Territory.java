package com.stasdoto.airdefense.nation;

/**
 * Whose land is where (1.25). Every city has its region - the land nearer to it than to any other city, the capitals
 * and bigger towns reaching further - with the lines between them bent by a smooth "warp" so the borders wind like
 * real ones instead of running straight. A country's land is the regions of its cities; when a city changes hands,
 * its region goes with it. The same function runs on the server and on the client (the map draws the borders from it).
 */
public final class Territory {
	/** How far (blocks) the borders bend out of the straight line, and over what distance they wander. */
	private static final double WARP = 170;
	private static final double SCALE = 560;
	private static final double WARP2 = 55;
	private static final double SCALE2 = 150;

	private Territory() {
	}

	/** How far a city's region reaches compared with others: capitals furthest. */
	public static float weight(int size, boolean capital) {
		return capital ? 1.35f : size == 1 ? 1.0f : 0.85f;
	}

	/**
	 * The index of the city whose region holds (x, z): xs, zs = the cities' centres, w = their weights, warp = the
	 * world's border seed. -1 if there are none.
	 */
	public static int owner(int[] xs, int[] zs, float[] w, long warp, double x, double z) {
		double wx = x + WARP * (2 * noise(warp, x / SCALE, z / SCALE) - 1) + WARP2 * (2 * noise(warp ^ 0x5A5AL, x / SCALE2, z / SCALE2) - 1);
		double wz = z + WARP * (2 * noise(warp ^ 0x3C3CL, x / SCALE, z / SCALE) - 1) + WARP2 * (2 * noise(warp ^ 0x7E7EL, x / SCALE2, z / SCALE2) - 1);
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < xs.length; i++) {
			double dx = wx - xs[i];
			double dz = wz - zs[i];
			double d = (dx * dx + dz * dz) / (w[i] * w[i]);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	/** Smooth value noise 0..1. */
	static double noise(long seed, double fx, double fz) {
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
}
