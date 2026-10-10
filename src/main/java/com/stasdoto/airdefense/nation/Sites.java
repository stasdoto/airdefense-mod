package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.factory.FactoryBlueprint;

/**
 * Where in the village a new building goes: the nearest free, roughly level patch of natural ground (no houses,
 * fields, paths or water on it, nothing anybody built), with its door looking towards the village square.
 */
public final class Sites {
	/** Debug: why the last search failed (for the log and the tests). */
	public static String lastReport = "";

	private Sites() {
	}

	/** What we know about one column of the ground (worked out once per search). */
	private record Column(int ground, boolean natural, int clear) {
	}

	/** A place for the building, or null if the village has no room for it. */
	@Nullable
	public static Building find(ServerLevel level, Politics p, Settlement s, BuildingType type, int id, boolean free) {
		long t0 = System.nanoTime();
		Map<Long, Column> columns = new HashMap<>();
		int[] reasons = new int[5];
		BlockPos c = s.center;
		if (type == BuildingType.OIL_WELL) {
			return oilWell(level, p, s, id, free, columns, reasons);
		}
		int start = 9 + Math.max(type.width, type.depth) / 2;
		int end = s.radius + 8;
		// Start each ring at an angle of its own for this village, so buildings spread around the square.
		double phase = (s.id * 2.3999632) % (Math.PI * 2);
		int tried = 0;
		for (int r = start; r <= end; r += 2) {
			int steps = Math.max(8, (int) (Math.PI * 2 * r / 5));
			for (int i = 0; i < steps; i++) {
				double a = phase + Math.PI * 2 * i / steps;
				int x = c.getX() + (int) Math.round(Math.cos(a) * r);
				int z = c.getZ() + (int) Math.round(Math.sin(a) * r);
				int dx = x - c.getX();
				int dz = z - c.getZ();
				Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
				tried++;
				Building b = check(level, p, type, id, new BlockPos(x, c.getY(), z), facing, free, columns, reasons);
				if (b != null) {
					// 1.28: in the town's own style.
					b.style = s.style;
				}
				if (b != null) {
					lastReport = String.format("%s: found after %d tries, %d ms", type.id, tried, (System.nanoTime() - t0) / 1_000_000);
					AirDefense.LOGGER.info("[airdefense] site {}", lastReport);
					return b;
				}
			}
		}
		lastReport = String.format("%s: none in %d tries, %d ms (overlap %d, unloaded %d, slope %d, ground %d, blocked %d)", type.id, tried,
				(System.nanoTime() - t0) / 1_000_000, reasons[0], reasons[1], reasons[2], reasons[3], reasons[4]);
		AirDefense.LOGGER.info("[airdefense] site {}", lastReport);
		return null;
	}

	/** An oil well goes on the nearest oil field within 220 blocks of the village. */
	@Nullable
	private static Building oilWell(ServerLevel level, Politics p, Settlement s, int id, boolean free, Map<Long, Column> columns, int[] reasons) {
		long salt = OilFields.salt(level.getSeed());
		List<OilFields.Field> fields = OilFields.near(salt, s.center.getX(), s.center.getZ(), 220);
		fields.sort(java.util.Comparator.comparingDouble(f -> Math.hypot(f.x() - s.center.getX(), f.z() - s.center.getZ())));
		for (OilFields.Field f : fields) {
			if (Math.hypot(f.x() - s.center.getX(), f.z() - s.center.getZ()) > 220) {
				continue;
			}
			for (int r = 0; r <= f.radius(); r += 3) {
				int steps = Math.max(1, (int) (Math.PI * 2 * r / 4));
				for (int i = 0; i < steps; i++) {
					double a = Math.PI * 2 * i / steps;
					int x = f.x() + (int) Math.round(Math.cos(a) * r);
					int z = f.z() + (int) Math.round(Math.sin(a) * r);
					Building b = check(level, p, BuildingType.OIL_WELL, id, new BlockPos(x, s.center.getY(), z), Direction.NORTH, free, columns, reasons);
					if (b != null) {
						lastReport = "oil_well on the field at " + f.x() + " " + f.z();
						return b;
					}
				}
			}
		}
		lastReport = "oil_well: no free spot on an oil field within 220 blocks";
		return null;
	}

	@Nullable
	private static Building check(ServerLevel level, Politics p, BuildingType type, int id, BlockPos at, Direction facing, boolean free,
			Map<Long, Column> columns, int[] reasons) {
		Building probe = new Building(id, type, at, facing, free);
		int hw = type.halfWidth();
		int front = type == BuildingType.FACTORY ? 4 : 2;
		int depth = type.depth;
		int height = type == BuildingType.FACTORY ? FactoryBlueprint.CHIMNEY_TOP + 2 : type.height + 2;
		// Not on anybody else's building.
		for (Settlement other : p.settlements.values()) {
			if (other.center.distSqr(at) > 160 * 160) {
				continue;
			}
			for (Building b : other.eco.buildings) {
				if (b.type == BuildingType.ROADS) {
					continue;
				}
				for (int lx = -hw - 2; lx <= hw + 2; lx += 2) {
					for (int lz = -front - 2; lz <= depth + 1; lz += 2) {
						BlockPos q = probe.at(lx, 0, lz);
						if (b.covers(q.getX(), q.getZ(), 2)) {
							reasons[0]++;
							return null;
						}
					}
				}
			}
		}
		// Level ground: the floor goes at the average height, nothing more than 3 blocks off.
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		int n = 0;
		Column[][] cols = new Column[2 * hw + 3][depth + front + 1];
		for (int lx = -hw - 1; lx <= hw + 1; lx++) {
			for (int lz = -front; lz <= depth; lz++) {
				BlockPos q = probe.at(lx, 0, lz);
				Column col = column(level, q.getX(), q.getZ(), columns);
				if (col == null) {
					reasons[1]++;
					return null;
				}
				cols[lx + hw + 1][lz + front] = col;
				if (lz >= 0) {
					min = Math.min(min, col.ground);
					max = Math.max(max, col.ground);
					sum += col.ground;
					n++;
				}
			}
		}
		if (max - min > 3) {
			reasons[2]++;
			return null;
		}
		int floor = (int) Math.round((double) sum / n);
		for (int lx = -hw - 1; lx <= hw + 1; lx++) {
			for (int lz = -front; lz <= depth; lz++) {
				Column col = cols[lx + hw + 1][lz + front];
				if (lz < 0 && Math.abs(col.ground - floor) > 2) {
					reasons[2]++;
					return null;
				}
				if (!col.natural) {
					reasons[3]++;
					return null;
				}
				if (col.ground + col.clear < floor + height) {
					reasons[4]++;
					return null;
				}
			}
		}
		Building found = new Building(id, type, new BlockPos(at.getX(), floor, at.getZ()), facing, free);
		found.variant = Math.floorMod((int) (at.asLong() * 31 + id), 97);
		return found;
	}

	/** The ground of one column: how high, whether it is natural, and how many free (or clearable) blocks are above it. */
	@Nullable
	private static Column column(ServerLevel level, int x, int z, Map<Long, Column> cache) {
		long key = BlockPos.asLong(x, 0, z);
		Column col = cache.get(key);
		if (col != null || cache.containsKey(key)) {
			return col;
		}
		BlockPos probe = new BlockPos(x, 64, z);
		if (!level.isLoaded(probe)) {
			cache.put(key, null);
			return null;
		}
		int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, gy, z);
		BlockState top = level.getBlockState(m);
		boolean natural = naturalGround(top) && level.getFluidState(m.setY(gy + 1)).isEmpty();
		int clear = 0;
		for (int y = gy + 1; y <= gy + 32; y++) {
			m.setY(y);
			if (!clearable(level, m, level.getBlockState(m))) {
				break;
			}
			clear++;
		}
		col = new Column(gy, natural, clear);
		cache.put(key, col);
		return col;
	}

	/** Ground the village may build on: soil, sand, rock - not paths, fields or anything laid by hand. */
	public static boolean naturalGround(BlockState s) {
		if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND) || s.hasBlockEntity()) {
			return false;
		}
		return s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
				|| s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
				|| s.is(Blocks.PACKED_ICE) || s.is(Blocks.MUD) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL)
				|| s.is(Blocks.COARSE_DIRT) || s.is(Blocks.MYCELIUM);
	}

	/** What may stand where the building goes: air, grass and flowers, snow, and trees (not timber anybody built with). */
	static boolean clearable(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir()) {
			return true;
		}
		if (s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
			return false;
		}
		if (s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.REPLACEABLE_BY_TREES)
				|| s.is(Blocks.SNOW) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)
				|| s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM)) {
			return true;
		}
		if (s.is(BlockTags.LOGS)) {
			return treeLog(level, pos);
		}
		// A bump of earth or rock is dug away.
		return naturalGround(s);
	}

	/** A log that is part of a tree: further up the trunk there are leaves, not a roof. */
	static boolean treeLog(ServerLevel level, BlockPos pos) {
		for (int up = 1; up <= 12; up++) {
			BlockState s = level.getBlockState(pos.above(up));
			if (s.is(BlockTags.LEAVES)) {
				return true;
			}
			if (!s.is(BlockTags.LOGS)) {
				return false;
			}
		}
		return false;
	}

	/** Every column a building stands on (for the map and the tests). */
	public static List<BlockPos> footprint(Building b) {
		List<BlockPos> out = new ArrayList<>();
		int hw = b.type.halfWidth();
		for (int lx = -hw; lx <= hw; lx++) {
			for (int lz = 0; lz < b.type.depth; lz++) {
				out.add(b.at(lx, 0, lz));
			}
		}
		return out;
	}
}
