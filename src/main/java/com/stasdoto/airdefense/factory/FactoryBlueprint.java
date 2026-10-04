package com.stasdoto.airdefense.factory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The factory building, block by block, in the order it puts itself up: first the site is cleared, then the
 * foundation, then walls and pillars layer by layer, the saw-tooth glass roof, the chimney and the shop floor fittings.
 *
 * <p>Local frame: x to the right, z away from the gate (the way the player was facing), y up; (0, 0, 0) is the ground
 * block in the middle of the gate. Brick walls with stone pillars, 21 x 15 blocks, 7 high, a saw-tooth roof with
 * north-light glazing, a brick chimney with a signal fire, an assembly line with a gantry crane inside.
 */
public final class FactoryBlueprint {
	public static final int HALF_W = 10;
	public static final int DEPTH = 15;
	public static final int WALL_TOP = 7;
	/** Where the control desk stands (it is placed first and runs the construction). */
	public static final int CTRL_X = -4;
	public static final int CTRL_Y = 1;
	public static final int CTRL_Z = 2;
	public static final int CHIMNEY_X = 6;
	public static final int CHIMNEY_Z = 11;
	public static final int CHIMNEY_TOP = 18;

	private FactoryBlueprint() {
	}

	public record Placement(BlockPos pos, BlockState state) {
	}

	/** Local → world. */
	public static BlockPos toWorld(BlockPos origin, Direction facing, int x, int y, int z) {
		Direction right = facing.getClockWise();
		return origin.offset(right.getStepX() * x + facing.getStepX() * z, y, right.getStepZ() * x + facing.getStepZ() * z);
	}

	public static BlockPos controllerPos(BlockPos origin, Direction facing) {
		return toWorld(origin, facing, CTRL_X, CTRL_Y, CTRL_Z);
	}

	public static BlockPos chimneyTop(BlockPos origin, Direction facing) {
		return toWorld(origin, facing, CHIMNEY_X, CHIMNEY_TOP, CHIMNEY_Z);
	}

	/** Every block of the factory, in building order. Air entries clear the site. */
	public static List<Placement> build(BlockPos origin, Direction facing) {
		Map<Long, BlockState> shell = new LinkedHashMap<>();
		BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
		BlockState found = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState wall = Blocks.BRICKS.defaultBlockState();
		BlockState pillar = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState window = Blocks.GLASS_PANE.defaultBlockState();
		BlockState roof = Blocks.DEEPSLATE_TILES.defaultBlockState();
		BlockState glass = Blocks.GLASS.defaultBlockState();

		int w = HALF_W;
		int d = DEPTH - 1;
		// Foundation and floor.
		for (int x = -w; x <= w; x++) {
			for (int z = 0; z <= d; z++) {
				boolean edge = Math.abs(x) == w || z == 0 || z == d;
				put(shell, x, -1, z, found);
				put(shell, x, 0, z, edge ? found : floor);
			}
		}
		// Hazard stripes along the assembly line.
		for (int z = 2; z <= d - 2; z++) {
			put(shell, -2, 0, z, Blocks.CONCRETE.yellow().defaultBlockState());
			put(shell, 2, 0, z, Blocks.CONCRETE.yellow().defaultBlockState());
		}
		// Walls, layer by layer.
		for (int y = 1; y <= WALL_TOP; y++) {
			for (int x = -w; x <= w; x++) {
				for (int z = 0; z <= d; z++) {
					boolean side = Math.abs(x) == w;
					boolean front = z == 0;
					boolean back = z == d;
					if (!side && !front && !back) {
						continue;
					}
					boolean isPillar = (front || back) && x % 5 == 0 || side && (z == 0 || z == 7 || z == d);
					BlockState s;
					if (y == WALL_TOP || isPillar) {
						s = pillar;
					} else if (front && Math.abs(x) <= 3 && y <= 5) {
						s = Blocks.AIR.defaultBlockState(); // the gate
					} else if (y >= 3 && y <= 5 && (side && (z >= 2 && z <= 5 || z >= 9 && z <= 12) || (front || back) && Math.abs(x) >= 6 && Math.abs(x) <= 9
							|| back && Math.abs(x) >= 1 && Math.abs(x) <= 4)) {
						s = window;
					} else {
						s = wall;
					}
					put(shell, x, y, z, s);
				}
			}
		}
		// Saw-tooth roof: three teeth, each with its glass face towards the gate.
		for (int x = -w; x <= w; x++) {
			for (int z = 0; z <= d; z++) {
				int k = z % 5;
				int top = switch (k) {
					case 0, 1 -> 10;
					case 2, 3 -> 9;
					default -> 8;
				};
				for (int y = 8; y <= top; y++) {
					boolean glazing = k == 0 && Math.abs(x) < w && y >= 8;
					put(shell, x, y, z, glazing ? glass : Math.abs(x) == w ? pillar : roof);
				}
			}
		}
		// Chimney with a fire on top: smoke while the factory is working.
		for (int y = 8; y < CHIMNEY_TOP - 1; y++) {
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					put(shell, CHIMNEY_X + dx, y, CHIMNEY_Z + dz, wall);
				}
			}
		}
		put(shell, CHIMNEY_X, CHIMNEY_TOP - 1, CHIMNEY_Z, Blocks.HAY_BLOCK.defaultBlockState());
		put(shell, CHIMNEY_X, CHIMNEY_TOP, CHIMNEY_Z, Blocks.CAMPFIRE.defaultBlockState()
				.setValue(CampfireBlock.LIT, false).setValue(CampfireBlock.SIGNAL_FIRE, true));
		// Shop floor: the assembly line, machines along the walls, the gantry crane, lights.
		for (int z = 3; z <= d - 3; z++) {
			put(shell, -1, 1, z, Blocks.SMOOTH_STONE.defaultBlockState());
			put(shell, 0, 1, z, Blocks.SMOOTH_STONE.defaultBlockState());
			put(shell, 1, 1, z, Blocks.SMOOTH_STONE.defaultBlockState());
		}
		put(shell, 0, 2, 4, Blocks.IRON_BLOCK.defaultBlockState());
		put(shell, -1, 2, 7, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise()));
		put(shell, 1, 2, 10, Blocks.END_ROD.defaultBlockState());
		put(shell, 0, 2, 10, Blocks.TARGET.defaultBlockState());
		put(shell, -9, 1, 3, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise()));
		put(shell, -9, 1, 4, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise()));
		put(shell, -9, 1, 5, Blocks.SMITHING_TABLE.defaultBlockState());
		put(shell, -9, 1, 9, Blocks.CRAFTING_TABLE.defaultBlockState());
		put(shell, -9, 1, 10, Blocks.STONECUTTER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise()));
		put(shell, -9, 1, 11, Blocks.GRINDSTONE.defaultBlockState());
		for (int z = 2; z <= 5; z++) {
			put(shell, 9, 1, z, Blocks.BARREL.defaultBlockState());
			if (z % 2 == 0) {
				put(shell, 9, 2, z, Blocks.BARREL.defaultBlockState());
			}
		}
		for (int z = 9; z <= 12; z++) {
			put(shell, 9, 1, z, Blocks.IRON_BLOCK.defaultBlockState());
		}
		for (int x = -9; x <= 9; x++) {
			put(shell, x, 6, 6, Blocks.CONCRETE.yellow().defaultBlockState());
			put(shell, x, 6, 8, Blocks.CONCRETE.yellow().defaultBlockState());
		}
		put(shell, 0, 6, 7, Blocks.IRON_BLOCK.defaultBlockState());
		put(shell, 0, 5, 7, Blocks.IRON_CHAIN.defaultBlockState());
		put(shell, 0, 4, 7, Blocks.IRON_CHAIN.defaultBlockState());
		put(shell, 0, 3, 7, Blocks.HOPPER.defaultBlockState());
		for (int x : new int[]{-6, 6}) {
			for (int z : new int[]{3, 11}) {
				put(shell, x, 7, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			}
		}
		// A gravel apron in front of the gate and a stack of crates by it.
		for (int x = -3; x <= 3; x++) {
			for (int z = -4; z <= -1; z++) {
				put(shell, x, 0, z, Blocks.GRAVEL.defaultBlockState());
			}
		}
		put(shell, -6, 1, -2, Blocks.BARREL.defaultBlockState());
		put(shell, -5, 1, -2, Blocks.BARREL.defaultBlockState());
		put(shell, -6, 2, -2, Blocks.BARREL.defaultBlockState());
		put(shell, 5, 1, -1, Blocks.LANTERN.defaultBlockState());
		put(shell, -5, 1, -1, Blocks.LANTERN.defaultBlockState());
		// The control desk is placed before everything else.
		shell.remove(BlockPos.asLong(CTRL_X, CTRL_Y, CTRL_Z));

		// Clearing first (top down), then the building bottom-up, nearer blocks first within a layer.
		List<Placement> out = new ArrayList<>();
		for (int y = CHIMNEY_TOP + 2; y >= 1; y--) {
			for (int x = -w - 1; x <= w + 1; x++) {
				for (int z = -4; z <= d + 1; z++) {
					long key = BlockPos.asLong(x, y, z);
					if (x == CTRL_X && y == CTRL_Y && z == CTRL_Z) {
						continue;
					}
					BlockState planned = shell.get(key);
					boolean inside = Math.abs(x) <= w && z >= 0 && z <= d && y <= 16;
					boolean apron = Math.abs(x) <= 6 && z >= -4 && z <= -1 && y <= 4;
					boolean chimney = x >= CHIMNEY_X - 1 && x <= CHIMNEY_X + 2 && z >= CHIMNEY_Z - 1 && z <= CHIMNEY_Z + 2;
					if ((planned == null || planned.isAir()) && (inside || apron || chimney)) {
						out.add(new Placement(toWorld(origin, facing, x, y, z), Blocks.AIR.defaultBlockState()));
					}
				}
			}
		}
		List<Map.Entry<Long, BlockState>> solid = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : shell.entrySet()) {
			if (!e.getValue().isAir()) {
				solid.add(e);
			}
		}
		solid.sort(Comparator.comparingInt((Map.Entry<Long, BlockState> e) -> BlockPos.getY(e.getKey()))
				.thenComparingInt(e -> Math.abs(BlockPos.getX(e.getKey())) + BlockPos.getZ(e.getKey())));
		for (Map.Entry<Long, BlockState> e : solid) {
			out.add(new Placement(toWorld(origin, facing, BlockPos.getX(e.getKey()), BlockPos.getY(e.getKey()), BlockPos.getZ(e.getKey())), e.getValue()));
		}
		return out;
	}

	private static void put(Map<Long, BlockState> map, int x, int y, int z, BlockState state) {
		map.put(BlockPos.asLong(x, y, z), state);
	}

	/** Blocks in the way that construction will not touch: unbreakable ones, and anything holding contents (chests...). */
	public static boolean protectedBlock(BlockState state) {
		return state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.END_PORTAL_FRAME) || state.hasBlockEntity();
	}
}
