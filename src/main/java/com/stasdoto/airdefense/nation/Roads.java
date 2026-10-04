package com.stasdoto.airdefense.nation;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import com.stasdoto.airdefense.registry.ModTickets;

/**
 * Roads three blocks wide along straight stretches, following the ground: a beaten path over earth, cobbles over
 * sand and rock, a plank bridge over water. Grass, flowers and bushes are cleared off it; anything somebody built is
 * left alone (the road goes round it - well, past it).
 */
public final class Roads {
	private Roads() {
	}

	/** Road blocks along one stretch (one per step along the longer axis). */
	static int steps(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ())) + 1;
	}

	public static int total(List<BlockPos> points) {
		int n = 0;
		for (int i = 0; i + 1 < points.size(); i += 2) {
			n += steps(points.get(i), points.get(i + 1));
		}
		return n;
	}

	/** Where step {@code index} of the road is (x, z), and which way the stretch runs. */
	static int[] at(List<BlockPos> points, int index) {
		int k = index;
		for (int i = 0; i + 1 < points.size(); i += 2) {
			BlockPos a = points.get(i);
			BlockPos b = points.get(i + 1);
			int n = steps(a, b);
			if (k < n) {
				double t = n <= 1 ? 0 : (double) k / (n - 1);
				int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
				int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
				boolean alongX = Math.abs(b.getX() - a.getX()) >= Math.abs(b.getZ() - a.getZ());
				return new int[]{x, z, alongX ? 1 : 0};
			}
			k -= n;
		}
		return null;
	}

	/**
	 * Lays one step of the road (three blocks across). Returns false if the ground there is not loaded yet (it is
	 * being loaded - try again in a moment).
	 */
	public static boolean step(ServerLevel level, Building road) {
		int[] at = at(road.points, road.index);
		if (at == null) {
			return true;
		}
		BlockPos probe = new BlockPos(at[0], 64, at[1]);
		if (!level.isLoaded(probe)) {
			level.getChunkSource().addTicketWithRadius(ModTickets.VEHICLE, ChunkPos.containing(probe), 1);
			return false;
		}
		for (int o = -1; o <= 1; o++) {
			int x = at[0] + (at[2] == 1 ? 0 : o);
			int z = at[1] + (at[2] == 1 ? o : 0);
			pave(level, x, z);
		}
		return true;
	}

	/** One road block where the ground is at (x, z). */
	public static void pave(ServerLevel level, int x, int z) {
		BlockPos probe = new BlockPos(x, 64, z);
		if (!level.isLoaded(probe)) {
			return;
		}
		int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos g = new BlockPos(x, gy, z);
		BlockState top = level.getBlockState(g);
		// Already a road (or one of ours).
		if (top.is(Blocks.DIRT_PATH) || top.is(Blocks.COBBLESTONE) || top.is(Blocks.OAK_PLANKS) && level.getFluidState(g.below()).is(Fluids.WATER)) {
			clearAbove(level, g);
			return;
		}
		BlockState road;
		if (top.getFluidState().is(Fluids.WATER) && top.getBlock() == Blocks.WATER) {
			road = Blocks.OAK_PLANKS.defaultBlockState();
		} else if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.COARSE_DIRT) || top.is(Blocks.PODZOL)
				|| top.is(Blocks.MYCELIUM) || top.is(Blocks.ROOTED_DIRT)) {
			road = Blocks.DIRT_PATH.defaultBlockState();
		} else if (Sites.naturalGround(top) && !top.is(Blocks.FARMLAND)) {
			road = Blocks.COBBLESTONE.defaultBlockState();
		} else {
			// Something built, a field, lava: leave it.
			return;
		}
		if (!clearAbove(level, g)) {
			return;
		}
		level.setBlock(g, road, Block.UPDATE_ALL);
	}

	/** Clears grass, flowers, snow and leaves off the road (up to 3 blocks up); false if something solid is in the way. */
	private static boolean clearAbove(ServerLevel level, BlockPos g) {
		for (int up = 1; up <= 3; up++) {
			BlockPos q = g.above(up);
			BlockState s = level.getBlockState(q);
			if (s.isAir()) {
				continue;
			}
			if (s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
				return up > 1;
			}
			if (s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SNOW)
					|| s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(Blocks.SWEET_BERRY_BUSH)) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			} else {
				return up > 1;
			}
		}
		return true;
	}

	/** A short path from a building's door to the village square, laid at once when the building is finished. */
	public static void doorPath(ServerLevel level, Building b, BlockPos square) {
		BlockPos from = b.doorstep();
		double d = Math.sqrt(from.distSqr(square));
		if (d < 3 || d > 80) {
			return;
		}
		// Stop a few blocks short of the bell.
		double k = (d - 3) / d;
		BlockPos to = new BlockPos((int) Math.round(from.getX() + (square.getX() - from.getX()) * k), square.getY(),
				(int) Math.round(from.getZ() + (square.getZ() - from.getZ()) * k));
		int n = steps(from, to);
		boolean alongX = Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ());
		for (int i = 0; i < n; i++) {
			double t = n <= 1 ? 0 : (double) i / (n - 1);
			int x = (int) Math.round(from.getX() + (to.getX() - from.getX()) * t);
			int z = (int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
			pave(level, x, z);
			if (i < 3) {
				// The doorstep itself is wider.
				pave(level, x + (alongX ? 0 : 1), z + (alongX ? 1 : 0));
				pave(level, x - (alongX ? 0 : 1), z - (alongX ? 1 : 0));
			}
		}
	}
}
