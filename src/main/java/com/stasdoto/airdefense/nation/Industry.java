package com.stasdoto.airdefense.nation;

import static com.stasdoto.airdefense.nation.Architecture.COLD;
import static com.stasdoto.airdefense.nation.Architecture.WARM;
import static com.stasdoto.airdefense.nation.Architecture.b;
import static com.stasdoto.airdefense.nation.Architecture.c;
import static com.stasdoto.airdefense.nation.Architecture.hanging;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/** The town's industry and trade (1.23): the food plant, the arms factory, the market. */
final class Industry {
	private Industry() {
	}

	// ------------------------------------------------------------------------------------------------
	// Food plant: a brick hall with a sawtooth roof, two grain silos, a chimney

	static void foodPlant(Plan p, int variant) {
		int x0 = -9;
		int x1 = 4;
		int d = 12;
		BlockState wall = variant % 2 == 0 ? b(Blocks.BRICKS) : b(Blocks.MUD_BRICKS);
		BlockState trim = b(Blocks.STONE_BRICKS);
		p.fill(-9, -1, 0, 9, -1, d, Blocks.STONE_BRICKS);
		p.fill(-9, 0, 0, 9, 0, d, Blocks.POLISHED_ANDESITE);
		// Walls with stone pilasters and tall windows.
		for (int y = 1; y <= 6; y++) {
			p.ring(x0, 0, x1, d, y, wall);
		}
		for (int y = 1; y <= 6; y++) {
			for (int x = x0; x <= x1; x += 3) {
				p.set(x, y, 0, trim);
				p.set(x, y, d, trim);
			}
			for (int z = 0; z <= d; z += 4) {
				p.set(x0, y, z, trim);
				p.set(x1, y, z, trim);
			}
		}
		for (int y = 2; y <= 4; y++) {
			for (int x = x0 + 1; x < x1; x++) {
				if (Math.floorMod(x - x0, 3) != 0) {
					p.set(x, y, d, Blocks.GLASS_PANE);
				}
			}
			for (int z = 1; z < d; z++) {
				if (z % 4 != 0) {
					p.set(x0, y, z, Blocks.GLASS_PANE);
					p.set(x1, y, z, Blocks.GLASS_PANE);
				}
			}
		}
		// The sawtooth roof: three teeth rising to the back, glazed on their tall side.
		int[] starts = {0, 4, 8};
		for (int t = 0; t < starts.length; t++) {
			int zs = starts[t];
			int ze = t + 1 < starts.length ? starts[t + 1] - 1 : d;
			for (int z = zs; z <= ze; z++) {
				int k = z - zs;
				for (int x = x0 - 1; x <= x1 + 1; x++) {
					if (k <= 2) {
						p.set(x, 7 + k, z, p.stairs(Blocks.STONE_BRICK_STAIRS, L.BACK, false));
					} else {
						p.set(x, 9, z, b(Blocks.SMOOTH_STONE));
					}
				}
				int top = k <= 2 ? 7 + k : 9;
				for (int y = 7; y < top; y++) {
					p.set(x0, y, z, wall);
					p.set(x1, y, z, wall);
				}
			}
			if (t + 1 < starts.length) {
				for (int x = x0 + 1; x <= x1 - 1; x++) {
					p.set(x, 7, ze, Blocks.GLASS);
					p.set(x, 8, ze, Blocks.GLASS);
				}
			}
		}
		// The front: a loading gate, the office door, a glowing sign.
		for (int y = 1; y <= 4; y++) {
			for (int x = -7; x <= -4; x++) {
				p.set(x, y, 0, Blocks.AIR);
			}
		}
		for (int x = -8; x <= -3; x++) {
			p.set(x, 5, -1, p.slab(Blocks.SMOOTH_STONE_SLAB, false));
		}
		for (int x = -7; x <= -4; x += 3) {
			p.set(x, 5, 0, trim);
		}
		p.door(0, 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
		p.set(-1, 2, 0, Blocks.GLASS_PANE);
		p.set(1, 2, 0, Blocks.GLASS_PANE);
		Architecture.lightbox(p, -1, 2, 5, 0, variant % 3 == 0 ? DyeColor.YELLOW : DyeColor.ORANGE, true);
		p.set(2, 3, -1, Architecture.onWall(p, Blocks.SPRUCE_TRAPDOOR, L.FRONT));
		// Inside: a conveyor from the gate to the ovens, smokers and furnaces, sacks and barrels, lights.
		for (int x = -7; x <= 2; x++) {
			p.set(x, 1, 6, p.slab(Blocks.SMOOTH_STONE_SLAB, false));
		}
		for (int x = -7; x <= -4; x++) {
			p.set(x, 1, d - 1, p.facing(Blocks.SMOKER, L.FRONT));
		}
		p.set(-3, 1, d - 1, p.facing(Blocks.FURNACE, L.FRONT));
		p.set(-2, 1, d - 1, p.facing(Blocks.FURNACE, L.FRONT));
		p.set(0, 1, d - 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		p.set(1, 1, d - 1, Blocks.CRAFTING_TABLE);
		for (int z = 2; z <= 4; z++) {
			p.set(x0 + 1, 1, z, Blocks.HAY_BLOCK);
			p.set(x0 + 1, 2, z, z == 3 ? b(Blocks.HAY_BLOCK) : b(Blocks.AIR));
			p.set(x1 - 1, 1, z, Blocks.BARREL);
		}
		for (int z = 8; z <= 9; z++) {
			p.set(x1 - 1, 1, z, Blocks.BARREL);
			p.set(x1 - 1, 2, z, Blocks.BARREL);
		}
		p.set(x0 + 1, 1, 8, Blocks.COMPOSTER);
		p.set(x0 + 1, 1, 9, Blocks.COMPOSTER);
		for (int z : new int[]{2, 6, 10}) {
			for (int x : new int[]{-6, -1}) {
				int roof = 7 + Math.min(2, z % 4);
				p.set(x, roof - 1, z, hanging());
			}
		}
		// Grain silos with a bridge to the hall, and the chimney.
		for (int sz : new int[]{3, 9}) {
			p.fill(6, -1, sz - 1, 8, 0, sz + 1, Blocks.STONE_BRICKS);
			for (int y = 1; y <= 11; y++) {
				p.ring(6, sz - 1, 8, sz + 1, y, y % 5 == 0 ? b(Blocks.IRON_BLOCK) : c(DyeColor.WHITE));
				p.set(7, y, sz, Blocks.HAY_BLOCK);
			}
			p.fill(6, 12, sz - 1, 8, 12, sz + 1, p.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
			p.set(7, 12, sz, Blocks.POLISHED_DEEPSLATE);
			p.set(7, 13, sz, Blocks.IRON_BARS);
			p.set(5, 9, sz, p.slab(Blocks.SMOOTH_STONE_SLAB, false));
			p.set(5, 10, sz, Blocks.IRON_BARS);
		}
		p.ladder(9, 1, 11, 3, L.LEFT);
		p.set(7, 1, 6, Blocks.HAY_BLOCK);
		p.set(7, 1, 7, Blocks.BARREL);
		p.set(8, 1, 6, Blocks.BARREL);
		for (int y = 7; y <= 13; y++) {
			p.set(x0 + 1, y, d - 1, wall);
		}
		p.set(x0 + 1, 14, d - 1, Blocks.CAMPFIRE);
		p.top = Math.max(p.top, 15);
	}

	// ------------------------------------------------------------------------------------------------
	// Arms factory: a concrete hall in khaki with a vaulted roof, hazard-striped gate, two stacks

	static void armsFactory(Plan p, int variant) {
		int hw = 10;
		int d = 14;
		DyeColor band = variant % 2 == 0 ? DyeColor.GREEN : DyeColor.BROWN;
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.POLISHED_ANDESITE);
		for (int y = 1; y <= 8; y++) {
			p.ring(-hw, 0, hw, d, y, y == 4 || y == 5 ? c(band) : c(DyeColor.LIGHT_GRAY));
		}
		// Ribbon windows high up.
		for (int z = 1; z < d; z++) {
			if (z % 4 != 0) {
				p.set(-hw, 7, z, Architecture.pane(DyeColor.GRAY));
				p.set(hw, 7, z, Architecture.pane(DyeColor.GRAY));
			}
		}
		// The vault: low at the sides, high in the middle, gables closed; skylights along the top.
		for (int x = -hw - 1; x <= hw + 1; x++) {
			int ax = Math.abs(x);
			for (int z = -1; z <= d + 1; z++) {
				BlockState roof;
				int y;
				if (ax >= 8) {
					y = 9;
					roof = p.stairs(Blocks.DEEPSLATE_TILE_STAIRS, x < 0 ? L.RIGHT : L.LEFT, false);
				} else if (ax >= 4) {
					y = 10;
					roof = p.stairs(Blocks.DEEPSLATE_TILE_STAIRS, x < 0 ? L.RIGHT : L.LEFT, false);
				} else {
					y = 11;
					roof = ax <= 1 && z % 2 == 0 && z > 0 && z < d ? b(Blocks.GLASS) : b(Blocks.DEEPSLATE_TILES);
				}
				p.set(x, y, z, roof);
			}
			if (ax <= hw) {
				int top = ax >= 8 ? 9 : ax >= 4 ? 10 : 11;
				for (int y = 9; y < top; y++) {
					p.set(x, y, 0, c(DyeColor.LIGHT_GRAY));
					p.set(x, y, d, c(DyeColor.LIGHT_GRAY));
				}
			}
		}
		// The gate with hazard stripes and the shutter half up.
		for (int y = 1; y <= 6; y++) {
			for (int x = -4; x <= 4; x++) {
				p.set(x, y, 0, Blocks.AIR);
			}
			p.set(-5, y, 0, c(y % 2 == 0 ? DyeColor.YELLOW : DyeColor.BLACK));
			p.set(5, y, 0, c(y % 2 == 0 ? DyeColor.BLACK : DyeColor.YELLOW));
		}
		for (int x = -5; x <= 5; x++) {
			p.set(x, 7, 0, c(Math.floorMod(x, 2) == 0 ? DyeColor.YELLOW : DyeColor.BLACK));
			if (Math.abs(x) <= 4) {
				p.set(x, 6, 0, b(Blocks.IRON_BARS));
			}
		}
		p.door(-8, 1, 0, Blocks.IRON_DOOR, L.BACK);
		p.set(-7, 2, -1, Architecture.button(p, Blocks.STONE_BUTTON, L.FRONT));
		p.set(-9, 2, -1, Architecture.button(p, Blocks.STONE_BUTTON, L.FRONT));
		p.set(8, 3, -1, Architecture.onWall(p, Blocks.IRON_TRAPDOOR, L.FRONT));
		p.set(-6, 5, -1, b(Blocks.REDSTONE_LAMP).setValue(BlockStateProperties.LIT, true));
		p.set(6, 5, -1, b(Blocks.REDSTONE_LAMP).setValue(BlockStateProperties.LIT, true));
		// Inside: work benches down both sides, a test range at the back, crates, an armoury cage.
		Block[] benches = {Blocks.ANVIL, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.BLAST_FURNACE, Blocks.FLETCHING_TABLE, Blocks.STONECUTTER};
		for (int i = 0; i < 6; i++) {
			int z = 2 + i * 2;
			if (z >= d - 3) {
				break;
			}
			Block bl = benches[(i + variant) % benches.length];
			BlockState left = bl == Blocks.GRINDSTONE ? b(bl) : bl == Blocks.ANVIL || bl == Blocks.BLAST_FURNACE || bl == Blocks.STONECUTTER
					? p.facing(bl, L.RIGHT) : b(bl);
			p.set(-hw + 1, 1, z, left);
			Block br = benches[(i + variant + 3) % benches.length];
			BlockState right = br == Blocks.GRINDSTONE ? b(br) : br == Blocks.ANVIL || br == Blocks.BLAST_FURNACE || br == Blocks.STONECUTTER
					? p.facing(br, L.LEFT) : b(br);
			p.set(hw - 1, 1, z, right);
		}
		for (int x = -3; x <= 3; x += 3) {
			p.set(x, 1, d - 1, Blocks.TARGET);
			p.set(x, 2, d - 1, Blocks.TARGET);
		}
		for (int x = -hw + 1; x <= -hw + 3; x++) {
			p.set(x, 1, d - 2, Blocks.IRON_BARS);
			p.set(x, 2, d - 2, Blocks.IRON_BARS);
		}
		p.chest(-hw + 1, 1, d - 1, L.FRONT);
		p.chest(-hw + 2, 1, d - 1, L.FRONT);
		for (int[] crate : new int[][]{{5, 4}, {6, 4}, {5, 5}, {6, 5}, {-5, 9}, {-6, 9}, {-5, 10}}) {
			p.set(crate[0], 1, crate[1], Blocks.BARREL);
		}
		p.set(5, 2, 4, Blocks.BARREL);
		p.set(6, 2, 5, Blocks.TNT);
		for (int z = 2; z <= d - 2; z += 4) {
			for (int x : new int[]{-5, 0, 5}) {
				p.set(x, 8, z, COLD);
			}
		}
		// Two stacks at the back.
		for (int x : new int[]{-8, 8}) {
			for (int y = 10; y <= 15; y++) {
				p.set(x, y, d - 2, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
			}
			p.set(x, 16, d - 2, Blocks.CAMPFIRE);
		}
		p.top = Math.max(p.top, 17);
	}

	// ------------------------------------------------------------------------------------------------
	// Market: stalls under striped awnings either side of an aisle

	private static final DyeColor[][] AWNINGS = {{DyeColor.RED, DyeColor.WHITE}, {DyeColor.GREEN, DyeColor.WHITE}, {DyeColor.BLUE, DyeColor.WHITE},
			{DyeColor.ORANGE, DyeColor.YELLOW}, {DyeColor.PURPLE, DyeColor.PINK}, {DyeColor.CYAN, DyeColor.LIGHT_BLUE}};

	static void market(Plan p, int variant) {
		int hw = 8;
		int d = 12;
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.STONE_BRICKS);
		for (int x = -hw; x <= hw; x++) {
			for (int z = 0; z <= d; z++) {
				boolean aisle = Math.abs(x) <= 1;
				p.set(x, 0, z, aisle ? b(Blocks.POLISHED_ANDESITE) : (x + z) % 2 == 0 ? Architecture.terra(DyeColor.WHITE)
						: Architecture.terra(DyeColor.LIGHT_GRAY));
			}
		}
		// Goods for the counters.
		Block[][] goods = {
				{Blocks.MELON, Blocks.PUMPKIN, Blocks.HAY_BLOCK},
				{Blocks.HONEY_BLOCK, Blocks.HAY_BLOCK, Blocks.DRIED_KELP_BLOCK},
				{Blocks.IRON_BLOCK, Blocks.COAL_BLOCK, Blocks.BARREL},
				{Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.PUMPKIN},
				{Blocks.BOOKSHELF, Blocks.BARREL, Blocks.FLOWER_POT},
				{Blocks.CAKE, Blocks.HAY_BLOCK, Blocks.MELON},
		};
		int stall = 0;
		for (int side : new int[]{-1, 1}) {
			for (int zs = 0; zs <= 8; zs += 4) {
				DyeColor[] aw = AWNINGS[(stall + variant) % AWNINGS.length];
				Block[] g = goods[(stall + variant) % goods.length];
				int front = side * 3;
				int back = side * hw;
				// Corner posts, the awning (striped across), its edge over the aisle.
				for (int z : new int[]{zs, zs + 3}) {
					if (z > d) {
						continue;
					}
					for (int y = 1; y <= 3; y++) {
						p.set(front, y, z, b(Blocks.SPRUCE_FENCE));
						p.set(back, y, z, p.log(Blocks.STRIPPED_SPRUCE_LOG, 'y'));
					}
				}
				for (int z = zs; z <= Math.min(d, zs + 3); z++) {
					BlockState wool = Blocks.WOOL.pick(z % 2 == 0 ? aw[0] : aw[1]).defaultBlockState();
					for (int x = Math.min(front, back); x <= Math.max(front, back); x++) {
						p.set(x, 4, z, wool);
					}
					p.set(front + side * -1, 4, z, Blocks.WOOL_SLAB.pick(z % 2 == 0 ? aw[0] : aw[1]).defaultBlockState()
							.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
					p.set(back, 1, z, b(Blocks.SPRUCE_PLANKS));
					p.set(back, 2, z, b(Blocks.SPRUCE_PLANKS));
					p.set(back, 3, z, b(Blocks.SPRUCE_PLANKS));
				}
				// The counter towards the aisle, goods on it and behind it; a lantern under the awning.
				for (int k = 1; k <= 2; k++) {
					int z = zs + k;
					if (z > d) {
						continue;
					}
					p.set(front, 1, z, b(Blocks.BARREL));
					p.set(front, 2, z, b(g[k % g.length]));
					p.set(back - side, 1, z, b(g[(k + 1) % g.length]));
				}
				p.set(front + side, 3, zs + 1, hanging());
				p.set(back - side * 2, 1, zs + 2, b(Blocks.COMPOSTER));
				stall++;
			}
		}
		// Lamp posts down the aisle and a sign arch over the entrance.
		for (int z : new int[]{0, d}) {
			for (int x : new int[]{-2, 2}) {
				for (int y = 1; y <= 5; y++) {
					p.set(x, y, z, p.log(Blocks.STRIPPED_DARK_OAK_LOG, 'y'));
				}
			}
			for (int x = -2; x <= 2; x++) {
				p.set(x, 6, z, p.log(Blocks.STRIPPED_DARK_OAK_LOG, 'x'));
			}
			p.set(0, 5, z, WARM);
			p.set(-1, 5, z, hanging());
			p.set(1, 5, z, hanging());
		}
		p.set(-1, 7, 0, Blocks.WOOL.pick(DyeColor.YELLOW).defaultBlockState());
		p.set(0, 7, 0, Blocks.WOOL.pick(DyeColor.RED).defaultBlockState());
		p.set(1, 7, 0, Blocks.WOOL.pick(DyeColor.YELLOW).defaultBlockState());
		p.set(0, 1, 6, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		p.set(0, 2, 6, Blocks.FLOWER_POT);
		p.top = Math.max(p.top, 8);
	}
}
