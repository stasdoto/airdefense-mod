package com.stasdoto.airdefense.nation;

import java.util.Arrays;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.49: the outline of a building's design, worked out once for the weathering and the facade passes: at each height
 * the outermost solid block of each row (along x) and column (along z), and the top of each column. Kept in flat arrays
 * (a plan has tens of thousands of blocks: maps of boxed keys made the towns' chunks half as slow again).
 */
final class Outline {
	static final int NONE = Integer.MIN_VALUE;
	final int minX;
	final int minY;
	final int minZ;
	final int nx;
	final int ny;
	final int nz;
	private final int[] rowMin;
	private final int[] rowMax;
	private final int[] colMin;
	private final int[] colMax;
	private final int[] top;

	Outline(Map<Long, BlockState> blocks) {
		int x0 = Integer.MAX_VALUE;
		int y0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int y1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
			if (e.getValue().isAir()) {
				continue;
			}
			long k = e.getKey();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			x0 = Math.min(x0, x);
			x1 = Math.max(x1, x);
			y0 = Math.min(y0, y);
			y1 = Math.max(y1, y);
			z0 = Math.min(z0, z);
			z1 = Math.max(z1, z);
		}
		if (x0 > x1) {
			x0 = x1 = y0 = y1 = z0 = z1 = 0;
		}
		minX = x0;
		minY = y0;
		minZ = z0;
		nx = x1 - x0 + 1;
		ny = y1 - y0 + 1;
		nz = z1 - z0 + 1;
		rowMin = new int[ny * nz];
		rowMax = new int[ny * nz];
		colMin = new int[ny * nx];
		colMax = new int[ny * nx];
		top = new int[nx * nz];
		Arrays.fill(rowMin, Integer.MAX_VALUE);
		Arrays.fill(rowMax, NONE);
		Arrays.fill(colMin, Integer.MAX_VALUE);
		Arrays.fill(colMax, NONE);
		Arrays.fill(top, NONE);
		for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
			if (e.getValue().isAir()) {
				continue;
			}
			long k = e.getKey();
			int x = BlockPos.getX(k) - minX;
			int y = BlockPos.getY(k) - minY;
			int z = BlockPos.getZ(k) - minZ;
			int r = y * nz + z;
			rowMin[r] = Math.min(rowMin[r], x);
			rowMax[r] = Math.max(rowMax[r], x);
			int c = y * nx + x;
			colMin[c] = Math.min(colMin[c], z);
			colMax[c] = Math.max(colMax[c], z);
			int t = x * nz + z;
			top[t] = Math.max(top[t], y);
		}
	}

	private boolean inY(int y) {
		return y >= minY && y < minY + ny;
	}

	int rowMin(int y, int z) {
		if (!inY(y) || z < minZ || z >= minZ + nz) {
			return NONE;
		}
		int v = rowMin[(y - minY) * nz + z - minZ];
		return v == Integer.MAX_VALUE ? NONE : v + minX;
	}

	int rowMax(int y, int z) {
		if (!inY(y) || z < minZ || z >= minZ + nz) {
			return NONE;
		}
		int v = rowMax[(y - minY) * nz + z - minZ];
		return v == NONE ? NONE : v + minX;
	}

	int colMin(int y, int x) {
		if (!inY(y) || x < minX || x >= minX + nx) {
			return NONE;
		}
		int v = colMin[(y - minY) * nx + x - minX];
		return v == Integer.MAX_VALUE ? NONE : v + minZ;
	}

	int colMax(int y, int x) {
		if (!inY(y) || x < minX || x >= minX + nx) {
			return NONE;
		}
		int v = colMax[(y - minY) * nx + x - minX];
		return v == NONE ? NONE : v + minZ;
	}

	int top(int x, int z) {
		if (x < minX || x >= minX + nx || z < minZ || z >= minZ + nz) {
			return NONE;
		}
		int v = top[(x - minX) * nz + z - minZ];
		return v == NONE ? NONE : v + minY;
	}

	/** Which way the block at (x, y, z) looks out, or null if it is inside. */
	Blueprints.L outward(int x, int y, int z) {
		if (colMin(y, x) == z) {
			return Blueprints.L.FRONT;
		}
		if (rowMin(y, z) == x) {
			return Blueprints.L.LEFT;
		}
		if (rowMax(y, z) == x) {
			return Blueprints.L.RIGHT;
		}
		if (colMax(y, x) == z) {
			return Blueprints.L.BACK;
		}
		return null;
	}
}
