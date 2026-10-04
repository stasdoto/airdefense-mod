package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.stasdoto.airdefense.factory.FactoryBlueprint;

/**
 * Where in the village a new building goes: the nearest free, roughly level patch of natural ground (no houses,
 * fields, paths or water on it, nothing anybody built), with its door looking towards the village square.
 */
public final class Sites {
	private Sites() {
	}

	/** A place for the building, or null if the village has no room for it. */
	@Nullable
	public static Building find(ServerLevel level, Politics p, Settlement s, BuildingType type, int id, boolean free) {
		BlockPos c = s.center;
		int start = 9 + Math.max(type.width, type.depth) / 2;
		int end = Settlement.RADIUS + 8;
		// Start each ring at an angle of its own for this village, so buildings spread around the square.
		double phase = (s.id * 2.3999632) % (Math.PI * 2);
		for (int r = start; r <= end; r += 2) {
			int steps = Math.max(8, (int) (Math.PI * 2 * r / 5));
			for (int i = 0; i < steps; i++) {
				double a = phase + Math.PI * 2 * i / steps;
				int x = c.getX() + (int) Math.round(Math.cos(a) * r);
				int z = c.getZ() + (int) Math.round(Math.sin(a) * r);
				int dx = x - c.getX();
				int dz = z - c.getZ();
				Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
				Building b = check(level, p, s, type, id, new BlockPos(x, c.getY(), z), facing, free);
				if (b != null) {
					return b;
				}
			}
		}
		return null;
	}

	@Nullable
	private static Building check(ServerLevel level, Politics p, Settlement s, BuildingType type, int id, BlockPos at, Direction facing, boolean free) {
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
		int[][] ground = new int[2 * hw + 3][depth + front + 2];
		for (int lx = -hw - 1; lx <= hw + 1; lx++) {
			for (int lz = -front; lz <= depth; lz++) {
				BlockPos q = probe.at(lx, 0, lz);
				if (!level.isLoaded(q)) {
					return null;
				}
				int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ()) - 1;
				ground[lx + hw + 1][lz + front] = gy;
				if (lz >= 0) {
					min = Math.min(min, gy);
					max = Math.max(max, gy);
					sum += gy;
					n++;
				}
			}
		}
		if (max - min > 3) {
			return null;
		}
		int floor = (int) Math.round((double) sum / n);
		for (int lx = -hw - 1; lx <= hw + 1; lx++) {
			for (int lz = -front; lz <= depth; lz++) {
				int gy = ground[lx + hw + 1][lz + front];
				if (lz < 0 && Math.abs(gy - floor) > 2) {
					return null;
				}
				BlockPos q = probe.at(lx, 0, lz);
				BlockState top = level.getBlockState(new BlockPos(q.getX(), gy, q.getZ()));
				if (!naturalGround(top) || !level.getFluidState(new BlockPos(q.getX(), gy + 1, q.getZ())).isEmpty()) {
					return null;
				}
				for (int y = gy + 1; y <= floor + height; y++) {
					BlockPos b = new BlockPos(q.getX(), y, q.getZ());
					BlockState st = level.getBlockState(b);
					if (!clearable(level, b, st)) {
						return null;
					}
				}
			}
		}
		return new Building(id, type, new BlockPos(at.getX(), floor, at.getZ()), facing, free);
	}

	/** Ground the village may build on: soil, sand, rock - not paths, fields or anything laid by hand. */
	static boolean naturalGround(BlockState s) {
		if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND) || s.hasBlockEntity()) {
			return false;
		}
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
				|| s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
				|| s.is(Blocks.PACKED_ICE) || s.is(Blocks.MUD);
	}

	/** What may stand where the building goes: air, grass and flowers, snow, and trees (not timber anybody built with). */
	static boolean clearable(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || s.hasBlockEntity()) {
			return s.isAir();
		}
		if (!s.getFluidState().isEmpty()) {
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
