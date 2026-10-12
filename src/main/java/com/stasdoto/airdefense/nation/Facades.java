package com.stasdoto.airdefense.nation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.street.FacadeBlocks;
import com.stasdoto.airdefense.street.Weathered;

/**
 * 1.49 "A town with a past": a building as the people who live in it have made it - air conditioners by the windows,
 * satellite dishes, flower boxes, drainpipes down the corners, notices by the doors, graffiti low on the walls, a shop
 * in the ground floor of a block of flats with its sign over the windows, vents, aerials and the lift's housing on the
 * flat roof. Drawn into the design (in its own frame) after {@link Weathering}, in new worlds.
 */
public final class Facades {
	/** For the tests: things put up (conditioners, dishes, flower boxes, pipes, shops, graffiti, notices, roof things). */
	public static final long[] COUNTS = new long[8];

	private Facades() {
	}

	static void dress(Blueprints.Plan p) {
		if (!Cities.weathered || p.blocks.isEmpty()) {
			return;
		}
		Building b = p.b;
		double age = Weathering.age(b);
		long seed = CityGen.mix(b.origin.asLong() ^ 0xFACADEL);
		Map<Long, int[]> rows = new HashMap<>();
		Map<Long, int[]> cols = new HashMap<>();
		Map<Long, Integer> tops = new HashMap<>();
		for (Map.Entry<Long, BlockState> e : p.blocks.entrySet()) {
			if (e.getValue().isAir()) {
				continue;
			}
			long k = e.getKey();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			rows.merge(key(y, z), new int[]{x, x}, (a, n) -> new int[]{Math.min(a[0], n[0]), Math.max(a[1], n[1])});
			cols.merge(key(y, x), new int[]{z, z}, (a, n) -> new int[]{Math.min(a[0], n[0]), Math.max(a[1], n[1])});
			tops.merge(key(x, z), y, Math::max);
		}
		Set<Long> pairCells = new HashSet<>();
		for (Map.Entry<Long, Object[]> e : p.pairs.entrySet()) {
			pairCells.add(e.getKey());
			pairCells.add((Long) e.getValue()[0]);
		}
		Map<Long, BlockState> add = new HashMap<>();
		boolean housing = isHousing(b.type);
		// A shop in the ground floor of a block of flats, its sign over the shop windows.
		if (housing && u(seed, 1) < 0.45) {
			shopFront(p, b, seed, cols, pairCells, add);
		}
		// Drainpipes down the front and back corners.
		if (u(seed, 2) < 0.8) {
			for (Blueprints.L side : new Blueprints.L[]{Blueprints.L.FRONT, Blueprints.L.BACK}) {
				drainpipes(p, side, cols, pairCells, add);
			}
		}
		for (Map.Entry<Long, BlockState> e : p.blocks.entrySet()) {
			BlockState s = e.getValue();
			if (!Weathered.isWall(s.getBlock())) {
				continue;
			}
			long k = e.getKey();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			Blueprints.L out = outward(x, y, z, rows, cols);
			if (out == null || y < 1) {
				continue;
			}
			int ox = x + out.dx;
			int oz = z + out.dz;
			if (!free(p, add, pairCells, ox, y, oz)) {
				continue;
			}
			Direction w = p.w(out);
			double v = u(seed ^ k, 3);
			boolean byWindow = out.dx == 0 ? window(p.blocks.get(BlockPos.asLong(x - 1, y, z))) || window(p.blocks.get(BlockPos.asLong(x + 1, y, z)))
					: window(p.blocks.get(BlockPos.asLong(x, y, z - 1))) || window(p.blocks.get(BlockPos.asLong(x, y, z + 1)));
			boolean underWindow = window(p.blocks.get(BlockPos.asLong(x, y + 1, z)));
			boolean byDoor = pairCells.contains(BlockPos.asLong(x - 1, 1, z)) || pairCells.contains(BlockPos.asLong(x + 1, 1, z))
					|| pairCells.contains(BlockPos.asLong(x, 1, z - 1)) || pairCells.contains(BlockPos.asLong(x, 1, z + 1));
			if (y >= 3 && byWindow && housing && v < 0.06 + 0.04 * age) {
				add.put(BlockPos.asLong(ox, y, oz), FacadeBlocks.facing(FacadeBlocks.AC_UNIT, w));
				COUNTS[0]++;
			} else if (y >= 3 && byWindow && housing && v < 0.085 + 0.04 * age) {
				add.put(BlockPos.asLong(ox, y, oz), FacadeBlocks.facing(FacadeBlocks.SAT_DISH, w));
				COUNTS[1]++;
			} else if (y >= 2 && underWindow && housing && v > 0.95) {
				add.put(BlockPos.asLong(ox, y, oz), FacadeBlocks.facing(FacadeBlocks.FLOWER_BOX, w));
				COUNTS[2]++;
			} else if (y == 2 && byDoor && v < 0.6) {
				add.put(BlockPos.asLong(ox, y, oz), FacadeBlocks.kind(FacadeBlocks.NOTICE, (int) (v * 100) % FacadeBlocks.NOTICE_KINDS, w));
				COUNTS[6]++;
			} else if (y <= 2 && v > 1 - 0.05 * age) {
				add.put(BlockPos.asLong(ox, y, oz), FacadeBlocks.kind(FacadeBlocks.GRAFFITI, (int) (v * 1000) % FacadeBlocks.GRAFFITI_KINDS, w));
				COUNTS[5]++;
			}
		}
		// The roof: vents, aerials, the lift's housing on the tall blocks.
		roof(p, b, seed, tops, add);
		for (Map.Entry<Long, BlockState> e : add.entrySet()) {
			// (Outside the footprint's box is fine: the plan's own blocks are all placed; the box only says what is cleared.)
			p.blocks.put(e.getKey(), e.getValue());
			p.top = Math.max(p.top, BlockPos.getY(e.getKey()));
		}
	}

	static boolean isHousing(BuildingType t) {
		return switch (t) {
			case PANEL5, PANEL9, APARTMENTS, TOWER -> true;
			default -> false;
		};
	}

	private static void shopFront(Blueprints.Plan p, Building b, long seed, Map<Long, int[]> cols, Set<Long> pairCells, Map<Long, BlockState> add) {
		int run = 0;
		int start = 0;
		int z0 = Integer.MIN_VALUE;
		int lo = p.minX;
		int hi = p.maxX;
		boolean left = u(seed, 4) < 0.5;
		for (int i = 0; i <= hi - lo; i++) {
			int x = left ? lo + i : hi - i;
			int[] c1 = cols.get(key(1, x));
			int[] c2 = cols.get(key(2, x));
			boolean ok = c1 != null && c2 != null && c1[0] == c2[0];
			int zf = ok ? c1[0] : 0;
			if (ok) {
				ok = (run == 0 || zf == z0) && shopWall(p.blocks.get(BlockPos.asLong(x, 1, zf))) && shopWall(p.blocks.get(BlockPos.asLong(x, 2, zf)))
						&& !nearDoor(pairCells, x, zf) && free(p, add, pairCells, x, 1, zf - 1) && free(p, add, pairCells, x, 2, zf - 1)
						&& free(p, add, pairCells, x, 3, zf - 1) && zf <= 1;
			}
			if (!ok) {
				run = 0;
				continue;
			}
			if (run == 0) {
				start = x;
				z0 = zf;
			}
			run++;
			if (run == 5) {
				break;
			}
		}
		if (run < 4) {
			return;
		}
		int step = left ? 1 : -1;
		for (int k = 0; k < run; k++) {
			int x = start + k * step;
			p.blocks.put(BlockPos.asLong(x, 1, z0), Blocks.GLASS.defaultBlockState());
			p.blocks.put(BlockPos.asLong(x, 2, z0), Blocks.GLASS.defaultBlockState());
		}
		int mid = start + (run / 2) * step;
		boolean russian = b.style == CityStyle.SOVIET.ordinal() || b.style == CityStyle.DESERT.ordinal() || b.style == CityStyle.CLASSIC.ordinal();
		int kind = (int) (u(seed, 5) * (russian ? FacadeBlocks.SIGNS_RU : FacadeBlocks.SIGNS - FacadeBlocks.SIGNS_RU)) + (russian ? 0 : FacadeBlocks.SIGNS_RU);
		add.put(BlockPos.asLong(mid, 3, z0 - 1), FacadeBlocks.kind(FacadeBlocks.SHOP_SIGN, kind, p.w(Blueprints.L.FRONT)));
		COUNTS[4]++;
	}

	private static boolean shopWall(BlockState s) {
		return s != null && (Weathered.isWall(s.getBlock()) || window(s));
	}

	private static boolean nearDoor(Set<Long> pairCells, int x, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			if (pairCells.contains(BlockPos.asLong(x + dx, 1, z)) || pairCells.contains(BlockPos.asLong(x + dx, 1, z - 1))) {
				return true;
			}
		}
		return false;
	}

	private static void drainpipes(Blueprints.Plan p, Blueprints.L side, Map<Long, int[]> cols, Set<Long> pairCells, Map<Long, BlockState> add) {
		// The two ends of this face at the ground floor.
		int xa = Integer.MAX_VALUE;
		int xb = Integer.MIN_VALUE;
		for (int x = p.minX; x <= p.maxX; x++) {
			int[] c = cols.get(key(1, x));
			if (c != null && wall(p.blocks.get(BlockPos.asLong(x, 1, side == Blueprints.L.FRONT ? c[0] : c[1])))) {
				xa = Math.min(xa, x);
				xb = Math.max(xb, x);
			}
		}
		if (xa >= xb - 3) {
			return;
		}
		for (int x : new int[]{xa, xb}) {
			int[] c = cols.get(key(1, x));
			int z = side == Blueprints.L.FRONT ? c[0] : c[1];
			int oz = z + side.dz;
			int y = 1;
			while (wall(p.blocks.get(BlockPos.asLong(x, y, z))) && free(p, add, pairCells, x, y, oz) && y < 80) {
				y++;
			}
			int top = y - 1;
			if (top < 4) {
				continue;
			}
			Direction w = p.w(side);
			for (int yy = 1; yy <= top; yy++) {
				net.minecraft.world.level.block.Block piece = yy == 1 ? FacadeBlocks.DRAINPIPE_BOTTOM : yy == top ? FacadeBlocks.DRAINPIPE_TOP : FacadeBlocks.DRAINPIPE;
				add.put(BlockPos.asLong(x, yy, oz), FacadeBlocks.facing(piece, w));
			}
			COUNTS[3]++;
		}
	}

	private static void roof(Blueprints.Plan p, Building b, long seed, Map<Long, Integer> tops, Map<Long, BlockState> add) {
		BlockState tar = Weathered.ROOF_TAR.defaultBlockState();
		int roofY = Integer.MIN_VALUE;
		for (Map.Entry<Long, BlockState> e : p.blocks.entrySet()) {
			if (e.getValue() == tar) {
				long k = e.getKey();
				int x = BlockPos.getX(k);
				int y = BlockPos.getY(k);
				int z = BlockPos.getZ(k);
				roofY = Math.max(roofY, y);
				if (p.blocks.containsKey(BlockPos.asLong(x, y + 1, z)) || add.containsKey(BlockPos.asLong(x, y + 1, z))) {
					continue;
				}
				double v = u(seed ^ k, 6);
				if (v < 0.025) {
					add.put(BlockPos.asLong(x, y + 1, z), FacadeBlocks.facing(FacadeBlocks.ROOF_VENT, p.w(Blueprints.L.FRONT)));
					COUNTS[7]++;
				} else if (v < 0.035 && isHousing(b.type)) {
					add.put(BlockPos.asLong(x, y + 1, z), FacadeBlocks.facing(FacadeBlocks.TV_AERIAL, p.w(Blueprints.L.FRONT)));
					COUNTS[7]++;
				}
			}
		}
		// The lift's housing: a little box on the middle of a tall block's roof.
		if (roofY >= 12 && isHousing(b.type)) {
			int cx = 0;
			int cz = (p.maxZ + p.minZ) / 2;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (p.blocks.get(BlockPos.asLong(cx + dx, roofY, cz + dz)) != tar) {
						return;
					}
				}
			}
			Weathered.Material m = Weathered.of(Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY));
			for (int dy = 1; dy <= 3; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						long k = BlockPos.asLong(cx + dx, roofY + dy, cz + dz);
						boolean shell = dy == 3 || Math.abs(dx) == 1 || Math.abs(dz) == 1;
						add.put(k, shell ? m.state(dy == 1 ? Weathered.Look.GRIME : Weathered.Look.A, p.w(Blueprints.L.FRONT)) : Blocks.AIR.defaultBlockState());
					}
				}
			}
			add.put(BlockPos.asLong(cx, roofY + 4, cz), tar);
			add.put(BlockPos.asLong(cx, roofY + 1, cz - 1), Blocks.IRON_BLOCK.defaultBlockState());
			add.put(BlockPos.asLong(cx, roofY + 2, cz - 1), Blocks.IRON_BLOCK.defaultBlockState());
			COUNTS[7]++;
		}
	}

	private static Blueprints.L outward(int x, int y, int z, Map<Long, int[]> rows, Map<Long, int[]> cols) {
		int[] r = rows.get(key(y, z));
		int[] c = cols.get(key(y, x));
		if (c != null && c[0] == z) {
			return Blueprints.L.FRONT;
		}
		if (r != null && r[0] == x) {
			return Blueprints.L.LEFT;
		}
		if (r != null && r[1] == x) {
			return Blueprints.L.RIGHT;
		}
		if (c != null && c[1] == z) {
			return Blueprints.L.BACK;
		}
		return null;
	}

	private static boolean free(Blueprints.Plan p, Map<Long, BlockState> add, Set<Long> pairCells, int x, int y, int z) {
		long k = BlockPos.asLong(x, y, z);
		BlockState s = p.blocks.get(k);
		return (s == null || s.isAir()) && !add.containsKey(k) && !pairCells.contains(k);
	}

	private static boolean wall(BlockState s) {
		return s != null && Weathered.isWall(s.getBlock());
	}

	private static boolean window(BlockState s) {
		if (s == null) {
			return false;
		}
		Block b = s.getBlock();
		return b == Blocks.GLASS_PANE || b == Blocks.GLASS || b instanceof StainedGlassPaneBlock || b instanceof StainedGlassBlock;
	}

	private static double u(long seed, int salt) {
		return (CityGen.mix(seed * 0x9E3779B97F4A7C15L + salt) >>> 11) * 0x1.0p-53;
	}

	private static long key(int a, int b) {
		return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
	}
}
