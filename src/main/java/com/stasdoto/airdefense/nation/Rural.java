package com.stasdoto.airdefense.nation;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Country buildings (1.22): the farm - a red barn with a hay loft, a silo, a pen with a trough. */
final class Rural {
	private Rural() {
	}

	/** Barn on the left (x -7..3), silo on the right at the back, a pen in front of it. */
	static void farm(Plan p, int variant) {
		int xc = -2;
		int x0 = -7;
		int x1 = 3;
		int d = 10;
		boolean red = variant % 3 != 2;
		BlockState wall = red ? Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState trim = red ? Blocks.BIRCH_PLANKS.defaultBlockState() : Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		Block roofStairs = variant % 2 == 0 ? Blocks.DARK_OAK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
		Block roofSlab = variant % 2 == 0 ? Blocks.DARK_OAK_SLAB : Blocks.DEEPSLATE_TILE_SLAB;
		// Ground of the whole yard, foundation and floor of the barn.
		p.fill(-7, 0, 0, 7, 0, d, Blocks.GRASS_BLOCK);
		p.fill(x0, -1, 0, x1, -1, d, Blocks.COBBLESTONE);
		p.fill(x0, 0, 0, x1, 0, d, Blocks.COARSE_DIRT);
		p.fill(xc - 1, 0, 0, xc + 1, 0, d, Blocks.SPRUCE_PLANKS);
		// Walls with white (or dark) corners and a beam under the roof.
		for (int y = 1; y <= 4; y++) {
			p.ring(x0, 0, x1, d, y, wall);
		}
		for (int y = 0; y <= 4; y++) {
			for (int x : new int[]{x0, x1}) {
				p.set(x, y, 0, p.log(Blocks.DARK_OAK_LOG, 'y'));
				p.set(x, y, d, p.log(Blocks.DARK_OAK_LOG, 'y'));
			}
		}
		p.ring(x0, 0, x1, d, 4, trim);
		// The big doors: front and back, open, with the white cross-braced leaves folded back on the wall.
		for (int y = 1; y <= 3; y++) {
			for (int x = xc - 2; x <= xc + 2; x++) {
				p.set(x, y, 0, Blocks.AIR);
			}
			for (int x = xc - 1; x <= xc + 1; x++) {
				p.set(x, y, d, Blocks.AIR);
			}
		}
		for (int y = 1; y <= 3; y++) {
			p.set(xc - 3, y, -1, trim);
			p.set(xc + 3, y, -1, trim);
		}
		p.set(xc - 3, 4, -1, p.slab(Blocks.BIRCH_SLAB, false));
		p.set(xc + 3, 4, -1, p.slab(Blocks.BIRCH_SLAB, false));
		// Windows on the sides.
		for (int z : new int[]{3, 7}) {
			p.set(x0, 2, z, Blocks.GLASS_PANE);
			p.set(x1, 2, z, Blocks.GLASS_PANE);
			p.set(x0, 3, z, Blocks.GLASS_PANE);
			p.set(x1, 3, z, Blocks.GLASS_PANE);
		}
		// The hay loft over the back half.
		p.fill(x0 + 1, 5, 5, x1 - 1, 5, d - 1, Blocks.SPRUCE_PLANKS);
		p.ladder(x1 - 1, 1, 4, 4, L.RIGHT);
		for (int x = x0 + 1; x <= x1 - 2; x++) {
			for (int z = 7; z <= d - 1; z++) {
				if ((x + z) % 4 != 0) {
					p.set(x, 6, z, Blocks.HAY_BLOCK);
				}
			}
		}
		// The gambrel roof: steep lower slopes, a flatter top; gables in the wall colour with a loft hatch in front.
		int[] rise = {9, 9, 8, 8, 7, 6, 5};
		for (int dx = 0; dx <= 6; dx++) {
			for (int s : new int[]{-1, 1}) {
				int x = xc + s * dx;
				if (s == 1 && dx == 0) {
					continue;
				}
				for (int z = -1; z <= d + 1; z++) {
					BlockState roof;
					if (dx >= 4) {
						roof = p.stairs(roofStairs, s < 0 ? L.RIGHT : L.LEFT, false);
					} else if (dx == 3 || dx == 1 || dx == 0) {
						roof = p.slab(roofSlab, false);
					} else {
						roof = p.slab(roofSlab, true);
					}
					p.set(x, rise[dx], z, roof);
				}
				if (dx <= 5) {
					int topFill = rise[dx] - 1;
					for (int y = 5; y <= topFill; y++) {
						p.set(x, y, 0, dx == 5 ? trim : wall);
						p.set(x, y, d, dx == 5 ? trim : wall);
					}
				}
			}
		}
		p.set(xc, 6, 0, Blocks.AIR);
		p.set(xc, 7, 0, Blocks.AIR);
		p.set(xc, 6, 1, Blocks.HAY_BLOCK);
		p.set(xc, 8, -1, Blocks.SPRUCE_FENCE);
		p.set(xc, 8, -2, Blocks.SPRUCE_FENCE);
		p.set(xc, 7, -2, Blocks.IRON_CHAIN);
		// Inside: stalls of fences on the left, hay, a trough, the farmer's corner on the right.
		for (int z : new int[]{2, 5, 8}) {
			p.set(x0 + 1, 1, z, Blocks.SPRUCE_FENCE);
			p.set(x0 + 2, 1, z, Blocks.SPRUCE_FENCE);
		}
		p.set(x0 + 1, 1, 3, Blocks.HAY_BLOCK);
		p.set(x0 + 1, 1, 6, Blocks.HAY_BLOCK);
		p.set(x0 + 1, 1, 9, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		p.set(x1 - 1, 1, 9, Blocks.COMPOSTER);
		p.set(x1 - 1, 1, 8, Blocks.BARREL);
		p.set(x1 - 1, 1, 7, Blocks.CRAFTING_TABLE);
		p.set(x1 - 1, 1, 6, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.w(L.LEFT)));
		p.set(x1 - 1, 1, 1, Blocks.HAY_BLOCK);
		p.set(x1 - 1, 2, 1, Blocks.HAY_BLOCK);
		p.set(x1 - 2, 1, 1, Blocks.HAY_BLOCK);
		p.lantern(xc, 4, 6, true);
		p.lantern(xc, 4, 9, true);
		// The silo: a round tower of concrete rings with a copper cap and a ladder.
		int sx = 6;
		int sz = 6;
		BlockState body = Blocks.CONCRETE.pick(variant % 2 == 0 ? DyeColor.LIGHT_GRAY : DyeColor.WHITE).defaultBlockState();
		p.fill(sx - 1, -1, sz - 1, sx + 1, 0, sz + 1, Blocks.STONE_BRICKS);
		for (int y = 1; y <= 10; y++) {
			p.ring(sx - 1, sz - 1, sx + 1, sz + 1, y, y % 4 == 0 ? Blocks.IRON_BLOCK.defaultBlockState() : body);
			p.set(sx, y, sz, Blocks.HAY_BLOCK);
		}
		p.fill(sx - 1, 11, sz - 1, sx + 1, 11, sz + 1, p.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
		p.set(sx, 11, sz, Blocks.POLISHED_DEEPSLATE);
		p.set(sx, 12, sz, Blocks.IRON_BARS);
		p.set(sx, 13, sz, Blocks.IRON_BARS);
		p.ladder(sx - 2, 1, 10, sz + 1, L.RIGHT);
		// A chute from the silo to the barn.
		p.set(sx - 2, 7, sz - 1, Blocks.SPRUCE_SLAB.defaultBlockState());
		// The pen in front of the silo, its gate to the yard; a trough and hay inside.
		for (int x = 4; x <= 7; x++) {
			p.set(x, 1, 0, Blocks.OAK_FENCE);
		}
		for (int z = 0; z <= 4; z++) {
			p.set(7, 1, z, Blocks.OAK_FENCE);
		}
		for (int z = 1; z <= 4; z++) {
			p.set(4, 1, z, Blocks.OAK_FENCE);
		}
		for (int x = 5; x <= 6; x++) {
			p.set(x, 1, 4, Blocks.OAK_FENCE);
		}
		p.set(5, 1, 0, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.w(L.BACK)));
		p.set(6, 1, 3, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		p.set(5, 1, 3, Blocks.HAY_BLOCK);
		// Sacks and a cart by the doors.
		p.set(xc - 4, 1, -1, Blocks.BARREL);
		p.set(xc + 4, 1, -1, Blocks.HAY_BLOCK);
	}
}
