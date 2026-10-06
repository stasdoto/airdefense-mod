package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

/**
 * Oil fields: fixed for each world. The world is cut into regions of 160 by 160 blocks; about one region in five has a
 * field somewhere in it (a circle 20-44 blocks across). Server and client work them out the same way from a salt the
 * server sends (taken from the seed, without giving the seed away).
 */
public final class OilFields {
	public static final int REGION = 160;

	/** One field: centre and radius. */
	public record Field(int x, int z, int radius) {
		public boolean contains(double px, double pz) {
			double dx = px - x;
			double dz = pz - z;
			return dx * dx + dz * dz <= (double) radius * radius;
		}
	}

	private OilFields() {
	}

	public static long salt(long seed) {
		return mix(seed ^ 0x6F696C6669656C64L);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	/** The field in region (rx, rz), or null. */
	public static Field field(long salt, int rx, int rz) {
		long h = mix(salt ^ (rx * 0x9E3779B97F4A7C15L) ^ (rz * 0xC2B2AE3D27D4EB4FL));
		if (Math.floorMod(h, 100) >= 30) {
			return null;
		}
		int ox = (int) Math.floorMod(h >>> 8, REGION - 60) + 30;
		int oz = (int) Math.floorMod(h >>> 20, REGION - 60) + 30;
		int r = 10 + (int) Math.floorMod(h >>> 32, 13);
		return new Field(rx * REGION + ox, rz * REGION + oz, r);
	}

	public static Field fieldAt(long salt, double x, double z) {
		int rx = Math.floorDiv((int) Math.floor(x), REGION);
		int rz = Math.floorDiv((int) Math.floor(z), REGION);
		Field f = field(salt, rx, rz);
		return f != null && f.contains(x, z) ? f : null;
	}

	/** Every field whose region touches the square around (x, z). */
	public static List<Field> near(long salt, double x, double z, double range) {
		List<Field> out = new ArrayList<>();
		int rx0 = Math.floorDiv((int) Math.floor(x - range), REGION);
		int rx1 = Math.floorDiv((int) Math.floor(x + range), REGION);
		int rz0 = Math.floorDiv((int) Math.floor(z - range), REGION);
		int rz1 = Math.floorDiv((int) Math.floor(z + range), REGION);
		for (int rx = rx0; rx <= rx1; rx++) {
			for (int rz = rz0; rz <= rz1; rz++) {
				Field f = field(salt, rx, rz);
				if (f != null) {
					out.add(f);
				}
			}
		}
		return out;
	}
}
