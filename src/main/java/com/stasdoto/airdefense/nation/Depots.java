package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The towns' depots (1.25): out of town by a highway, a fenced yard with one to three huge warehouses where the town
 * keeps its stores - and its missiles and drones. Planned from the seed like the hamlets; built by CityGen.
 */
public final class Depots {
	/** Yard in front of the warehouses (lorries turn here) and the margin round everything. */
	private static final int YARD = 24;
	private static final int MARGIN = 6;
	private static final int GAP = 8;

	public static final class Depot {
		public final Cities.City city;
		/** The levelled, fenced rectangle. */
		public final int x0;
		public final int z0;
		public final int x1;
		public final int z1;
		public final int y;
		/** The warehouses' fronts look this way (onto the yard and the road). */
		public final Direction front;
		public final List<Building> buildings = new ArrayList<>();
		public final int gateX;
		public final int gateZ;
		public Cities.Road access;

		Depot(Cities.City city, int x0, int z0, int x1, int z1, int y, Direction front, int gateX, int gateZ) {
			this.city = city;
			this.x0 = x0;
			this.z0 = z0;
			this.x1 = x1;
			this.z1 = z1;
			this.y = y;
			this.front = front;
			this.gateX = gateX;
			this.gateZ = gateZ;
		}

		public long key() {
			return city.key() * 8 + 7;
		}

		/** How far (x, z) is outside the yard (0 inside). */
		public int out(int x, int z) {
			return Math.max(0, Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1)));
		}

		public boolean near(int x, int z, int margin) {
			return out(x, z) <= margin;
		}
	}

	private Depots() {
	}

	/** The city's depot: by the first highway out of it, on the flattest dry ground found. Null if there is none. */
	@Nullable
	static Depot plan(long seed, Cities.Terrain t, Cities.City c) {
		Random r = new Random(c.seed ^ 0xDE9075L);
		int n = c.capital() ? 3 : c.size == Cities.Size.MEDIUM ? 2 : 1;
		BuildingType type = BuildingType.DEPOT;
		int along = n * type.width + (n + 1) * GAP;
		int across = type.depth + YARD + MARGIN;
		List<Cities.Road> highways = new ArrayList<>();
		for (Cities.Road road : Cities.mainRoadsNear(seed, t, c.x, c.z)) {
			if (road.highway && (c.outside(road.x0, road.z0) <= 12 || c.outside(road.x1, road.z1) <= 12)) {
				highways.add(road);
			}
		}
		Depot best = null;
		double bestScore = Double.MAX_VALUE;
		for (Cities.Road road : highways) {
			boolean fromStart = c.outside(road.x0, road.z0) <= 12;
			for (int s : new int[]{80, 120, 160, 200}) {
				double at = fromStart ? s : road.length - s;
				if (at < 0 || at > road.length) {
					continue;
				}
				double[] p = road.pointAt(at);
				double[] q = road.pointAt(Math.min(road.length, at + 8));
				double dx = q[0] - p[0];
				double dz = q[1] - p[1];
				double len = Math.max(1e-6, Math.hypot(dx, dz));
				dx /= len;
				dz /= len;
				for (int side = -1; side <= 1; side += 2) {
					// The yard's near edge a little way off the carriageway; the warehouses behind it.
					double nx = -dz * side;
					double nz = dx * side;
					double off = road.half + 10 + across / 2.0;
					double cx = p[0] + nx * off;
					double cz = p[1] + nz * off;
					// Axis-aligned: the warehouses' fronts face the road (the nearest of the four directions).
					Direction front = Math.abs(nx) >= Math.abs(nz) ? (nx > 0 ? Direction.WEST : Direction.EAST) : (nz > 0 ? Direction.NORTH : Direction.SOUTH);
					boolean alongX = front.getAxis() == Direction.Axis.Z;
					int w = alongX ? along : across;
					int d = alongX ? across : along;
					int x0 = (int) Math.round(cx - w / 2.0);
					int z0 = (int) Math.round(cz - d / 2.0);
					int x1 = x0 + w - 1;
					int z1 = z0 + d - 1;
					double score = site(t, c, road, x0, z0, x1, z1);
					if (Railways.close(seed, t, (x0 + x1) / 2, (z0 + z1) / 2, Math.max(x1 - x0, z1 - z0) / 2 + 16)
							|| Airports.inside(seed, t, (x0 + x1) / 2, (z0 + z1) / 2, Math.max(x1 - x0, z1 - z0) / 2 + 16)) {
						// 1.39: clear of the railways (1.40: and the airports).
						score += 10000;
					}
					if (score < bestScore) {
						bestScore = score;
						int y = level(t, x0, z0, x1, z1);
						// The gate in the middle of the side facing the road.
						int gx = (x0 + x1) / 2;
						int gz = (z0 + z1) / 2;
						switch (front) {
							case NORTH -> gz = z0;
							case SOUTH -> gz = z1;
							case WEST -> gx = x0;
							default -> gx = x1;
						}
						best = new Depot(c, x0, z0, x1, z1, y, front, gx, gz);
						best.access = access(t, road, at, gx, gz, y, front);
					}
				}
			}
		}
		if (best == null || bestScore > 400) {
			return null;
		}
		Railways.raiseRoad(seed, t, best.access);
		// The warehouses side by side along the back of the yard.
		Direction facing = best.front.getOpposite();
		boolean alongX = best.front.getAxis() == Direction.Axis.Z;
		for (int k = 0; k < n; k++) {
			int offset = GAP + k * (type.width + GAP) + type.width / 2;
			int bx;
			int bz;
			if (alongX) {
				bx = best.x0 + offset;
				bz = best.front == Direction.NORTH ? best.z0 + MARGIN / 2 + YARD : best.z1 - MARGIN / 2 - YARD;
			} else {
				bz = best.z0 + offset;
				bx = best.front == Direction.WEST ? best.x0 + MARGIN / 2 + YARD : best.x1 - MARGIN / 2 - YARD;
			}
			Building b = new Building(k, type, new BlockPos(bx, best.y, bz), facing, true);
			b.variant = r.nextInt(4);
			best.buildings.add(b);
		}
		return best;
	}

	/** How good a site is (lower is better): the ground's unevenness, water, being on top of a town or a road. */
	private static double site(Cities.Terrain t, Cities.City c, Cities.Road road, int x0, int z0, int x1, int z1) {
		int[] hs = new int[25];
		int wet = 0;
		int k = 0;
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				int x = x0 + (x1 - x0) * i / 4;
				int z = z0 + (z1 - z0) * j / 4;
				int y = t.top(x, z);
				hs[k++] = y;
				if (y < t.sea()) {
					wet++;
				}
				if (c.outside(x, z) <= 16) {
					return Double.MAX_VALUE;
				}
				if (road.locate(x, z, road.half + 5, new Cities.Road.Spot())) {
					return Double.MAX_VALUE;
				}
			}
		}
		java.util.Arrays.sort(hs);
		int median = hs[12];
		double spread = 0;
		for (int y : hs) {
			spread += Math.abs(y - median);
		}
		return spread + wet * 60;
	}

	private static int level(Cities.Terrain t, int x0, int z0, int x1, int z1) {
		int[] hs = new int[9];
		int k = 0;
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				hs[k++] = t.top(x0 + (x1 - x0) * i / 2, z0 + (z1 - z0) * j / 2);
			}
		}
		java.util.Arrays.sort(hs);
		return Math.max(t.sea() + 1, hs[4]);
	}

	/** A short straight road from the highway to the gate. */
	private static Cities.Road access(Cities.Terrain t, Cities.Road road, double at, int gx, int gz, int y, Direction front) {
		int ox = gx + front.getStepX() * 2;
		int oz = gz + front.getStepZ() * 2;
		Cities.Road.Spot spot = new Cities.Road.Spot();
		double[] p = road.locate(ox, oz, 500, spot) ? road.pointAt(spot.along) : road.pointAt(at);
		float hy = road.height(spot.along);
		double len = Math.hypot(p[0] - ox, p[1] - oz);
		int steps = Math.max(2, (int) Math.ceil(len / Cities.Road.STEP) + 1);
		float[] hs = new float[steps];
		for (int i = 0; i < steps; i++) {
			double f = (double) i / (steps - 1);
			hs[i] = (float) (y + (hy - y) * f);
		}
		return new Cities.Road(new float[]{ox, (float) p[0]}, new float[]{oz, (float) p[1]}, hs, 3, false, false, 3);
	}
}
