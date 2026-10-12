package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 1.50 "A living town": what a blast leaves in a town besides its crater - the windows round it cracked, soot blown up
 * the walls nearby. (Paper tape on the windows of a town at war, the panes mended in peacetime: see {@link WindowLights}.)
 */
public final class WarScars {
	/** For the tests: windows cracked and soot marks left. */
	public static int cracked;
	public static int sooted;

	private WarScars() {
	}

	public static void blast(ServerLevel level, Vec3 at, float power) {
		if (power < 1.5f) {
			return;
		}
		int r = (int) Math.min(10, 3 + power * 1.4);
		int soot = (int) Math.min(6, 1.5 + power * 0.6);
		BlockPos c = BlockPos.containing(at);
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		int marks = 0;
		var random = level.getRandom();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					int d2 = dx * dx + dy * dy + dz * dz;
					if (d2 > r * r) {
						continue;
					}
					q.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (!level.isLoaded(q)) {
						continue;
					}
					BlockState s = level.getBlockState(q);
					Block b = s.getBlock();
					if (b == StreetBlocks.CITY_WINDOW && s.getValue(WindowLights.LOOK) != 7 && random.nextFloat() < 0.75f) {
						level.setBlock(q, s.setValue(WindowLights.LOOK, 7), Block.UPDATE_CLIENTS);
						cracked++;
					} else if (marks < 24 && d2 <= soot * soot && s.isAir() && random.nextFloat() < 0.5f) {
						// Soot on a wall beside this air: facing away from the wall.
						for (Direction dir : Direction.Plane.HORIZONTAL) {
							BlockPos w = q.relative(dir);
							if (Weathered.isWall(level.getBlockState(w).getBlock())) {
								level.setBlock(q, FacadeBlocks.kind(FacadeBlocks.SOOT, random.nextInt(FacadeBlocks.SOOT_KINDS), dir.getOpposite()),
										Block.UPDATE_CLIENTS);
								marks++;
								sooted++;
								break;
							}
						}
					}
				}
			}
		}
	}
}
