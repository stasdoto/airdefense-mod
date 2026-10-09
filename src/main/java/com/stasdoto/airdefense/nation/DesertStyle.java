package com.stasdoto.airdefense.nation;

import static com.stasdoto.airdefense.nation.StyleKit.AIR;
import static com.stasdoto.airdefense.nation.StyleKit.WARM;
import static com.stasdoto.airdefense.nation.StyleKit.b;
import static com.stasdoto.airdefense.nation.StyleKit.concrete;
import static com.stasdoto.airdefense.nation.StyleKit.slab;

import java.util.Random;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * 1.28, the desert town: sandstone and whitewash, flat roofs with crenellated parapets, roof terraces under awnings,
 * water tanks and dishes, windows behind wooden lattices, pointed arches, blue tile bands, bazaars of awnings, little
 * domes on the houses and a palace with a great dome for a city hall.
 */
final class DesertStyle {
	private DesertStyle() {
	}

	static boolean design(Plan p, int variant, DyeColor flag) {
		switch (p.b.type) {
			case PANEL5, APARTMENTS -> flats(p, 4, variant);
			case PANEL9 -> flats(p, 7, variant);
			case SHOP -> bazaar(p, variant);
			case HOUSE, COTTAGE -> house(p, 2, variant);
			case SMALL_HOUSE -> house(p, 1, variant);
			case CITY_HALL -> palace(p, flag);
			default -> {
				return false;
			}
		}
		return true;
	}

	private static BlockState tile(DyeColor c) {
		return Blocks.GLAZED_TERRACOTTA.pick(c).defaultBlockState();
	}

	/** A dome over the circle of radius {@code r} round (cx, cz), its spring at height y0, a gold finial on top. */
	static void dome(Plan p, int cx, int cz, int y0, int r, BlockState shell) {
		for (int dy = 0; dy <= r; dy++) {
			double rr = Math.sqrt(Math.max(0, r * r - dy * dy)) + 0.35;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					double dist = Math.sqrt(x * x + z * z);
					if (dist <= rr && dist > rr - 1.6) {
						p.set(cx + x, y0 + dy, cz + z, shell);
					}
				}
			}
		}
		p.set(cx, y0 + r + 1, cz, b(Blocks.GOLD_BLOCK));
		p.set(cx, y0 + r + 2, cz, b(Blocks.LIGHTNING_ROD.waxed().unaffected()));
	}

	/** A window behind a wooden lattice (a trapdoor in front of the pane), an arch over it. */
	private static void latticeWindow(Plan p, int x, int y, int z, L out, Block lattice) {
		p.set(x, y, z, b(Blocks.GLASS_PANE));
		p.set(x, y + 1, z, b(Blocks.GLASS_PANE));
		int zz = out == L.FRONT ? z - 1 : z + 1;
		p.set(x, y, zz, StyleKit.shutter(p, lattice, out).setValue(BlockStateProperties.OPEN, false)
				.setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
	}

	// ------------------------------------------------------------------------------------------------
	// Blocks of flats: whitewash or sandstone, balconies with solid parapets, air conditioners, tanks on the roof

	private static void flats(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 61);
		BlockState wall = StyleKit.pick(variant, b(Blocks.SMOOTH_SANDSTONE), concrete(DyeColor.WHITE), b(Blocks.CUT_SANDSTONE), b(Blocks.TERRACOTTA));
		BlockState trim = b(Blocks.SMOOTH_SANDSTONE);
		StyleKit.base(p, hw, d, b(Blocks.SANDSTONE), b(Blocks.SMOOTH_SANDSTONE));
		int top = StyleKit.shell(p, hw, d, floors, 3, b(Blocks.CUT_SANDSTONE), wall, trim, b(Blocks.SMOOTH_STONE));
		Block lattice = StyleKit.pick(variant, Blocks.JUNGLE_TRAPDOOR, Blocks.ACACIA_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR);
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			for (int x = -hw + 2; x <= hw - 2; x += 3) {
				if (f == 0) {
					continue;
				}
				p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 1, d, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, d, b(Blocks.GLASS_PANE));
				if (r.nextInt(3) == 0) {
					p.set(x + 1, y0 + 2, -1, StyleKit.shutter(p, Blocks.IRON_TRAPDOOR, L.FRONT));
				}
			}
			// Balconies with solid parapets in the middle of the front.
			if (f > 0) {
				for (int x = -2; x <= 2; x++) {
					p.set(x, y0, -1, trim);
					p.set(x, y0 + 1, -1, Math.abs(x) == 2 || r.nextInt(4) > 0 ? wall : b(Blocks.IRON_BARS));
				}
				p.set(0, y0 + 1, 0, AIR);
				p.set(0, y0 + 2, 0, AIR);
				p.door(0, y0 + 1, 0, Blocks.JUNGLE_DOOR, L.BACK);
				if (r.nextInt(3) == 0) {
					StyleKit.awning(p, -2, 2, y0 + 3, DyeColor.WHITE, StyleKit.pick(f, DyeColor.RED, DyeColor.GREEN, DyeColor.BLUE));
				}
			}
			StyleKit.windowsSides(p, hw, d, y0, 1, 3, b(Blocks.GLASS_PANE));
		}
		// A blue tile band over the ground floor; shops with awnings below.
		for (int x = -hw; x <= hw; x++) {
			p.set(x, 3, -1, tile(DyeColor.LIGHT_BLUE));
		}
		for (int x = -hw + 1; x <= hw - 1; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, 0, b(Blocks.GLASS));
				p.set(x, 2, 0, b(Blocks.GLASS));
			}
		}
		StyleKit.awning(p, -hw + 1, -2, 3, DyeColor.ORANGE, DyeColor.WHITE);
		StyleKit.awning(p, 2, hw - 1, 3, DyeColor.GREEN, DyeColor.WHITE);
		p.door(0, 1, 0, Blocks.JUNGLE_DOOR, L.BACK);
		// The roof: crenellations, white water tanks, dishes, a stair hut.
		p.fill(-hw, top, 0, hw, top, d, trim);
		for (int x = -hw; x <= hw; x++) {
			p.set(x, top + 1, 0, wall);
			p.set(x, top + 1, d, wall);
			if (Math.floorMod(x, 2) == 0) {
				p.set(x, top + 2, 0, wall);
				p.set(x, top + 2, d, wall);
			}
		}
		for (int z = 0; z <= d; z++) {
			p.set(-hw, top + 1, z, wall);
			p.set(hw, top + 1, z, wall);
		}
		for (int i = 0; i < 2 + hw / 4; i++) {
			int x = -hw + 2 + r.nextInt(Math.max(1, 2 * hw - 3));
			int z = 2 + r.nextInt(Math.max(1, d - 3));
			p.set(x, top + 1, z, b(Blocks.IRON_BARS));
			p.set(x, top + 2, z, concrete(DyeColor.WHITE));
		}
		p.set(hw - 2, top + 1, 2, b(Blocks.DAYLIGHT_DETECTOR));
		p.fill(-2, top + 1, d - 3, 1, top + 3, d - 1, wall);
		p.ladder(-1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.SHOP, -hw + 1, -2, 0, 1, d - 1);
		Interiors.room(p, r, Interiors.CAFE, 2, hw - 1, 0, 1, d - 1);
		StyleKit.flats(p, r, hw, d, floors, 3, 6, wall, 1);
		p.top = top + 4;
	}

	// ------------------------------------------------------------------------------------------------
	// A bazaar: an arcade of pointed arches, stalls under striped awnings, carpets, pots, lanterns

	private static void bazaar(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 62);
		BlockState wall = b(Blocks.SMOOTH_SANDSTONE);
		StyleKit.base(p, hw, d, b(Blocks.SANDSTONE), b(Blocks.SMOOTH_SANDSTONE));
		for (int y = 1; y <= 5; y++) {
			p.ring(-hw, 0, hw, d, y, wall);
		}
		// The arches along the front.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			int o = Math.floorMod(x + hw, 4);
			if (o == 0) {
				continue;
			}
			p.set(x, 1, 0, AIR);
			p.set(x, 2, 0, AIR);
			p.set(x, 3, 0, o == 2 ? AIR : p.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, o == 1 ? L.RIGHT : L.LEFT, true));
			p.set(x, 4, 0, o == 2 ? p.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, L.BACK, true) : wall);
		}
		for (int x = -hw; x <= hw; x++) {
			p.set(x, 5, -1, tile(Math.floorMod(x, 2) == 0 ? DyeColor.BLUE : DyeColor.LIGHT_BLUE));
		}
		p.fill(-hw, 6, 0, hw, 6, d, wall);
		// The stalls inside: counters with goods, carpets, awnings over the back row.
		DyeColor[] cloth = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.PURPLE};
		for (int x = -hw + 1; x <= hw - 1; x++) {
			p.set(x, 1, d - 2, b(Blocks.BARREL));
			p.set(x, 2, d - 2, r.nextInt(3) == 0 ? b(Blocks.DECORATED_POT) : StyleKit.wool(cloth[r.nextInt(cloth.length)]));
			p.set(x, 4, d - 3, StyleKit.wool(cloth[Math.floorMod(x + variant, cloth.length)]));
			p.set(x, 1, 2, Blocks.CARPET.pick(cloth[Math.floorMod(x * 7 + variant, cloth.length)]).defaultBlockState());
		}
		for (int x = -hw + 2; x <= hw - 2; x += 4) {
			p.set(x, 5, d / 2, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
		}
		p.set(0, 7, d / 2, slab(Blocks.SMOOTH_SANDSTONE_SLAB, false));
		p.top = 8;
	}

	// ------------------------------------------------------------------------------------------------
	// A house: thick walls, an arched door, lattice windows, a roof terrace with an awning, a little dome

	private static void house(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 63);
		BlockState wall = StyleKit.pick(variant, b(Blocks.SMOOTH_SANDSTONE), concrete(DyeColor.WHITE), b(Blocks.TERRACOTTA), b(Blocks.CUT_SANDSTONE));
		Block lattice = StyleKit.pick(variant, Blocks.JUNGLE_TRAPDOOR, Blocks.ACACIA_TRAPDOOR);
		StyleKit.base(p, hw, d, b(Blocks.SANDSTONE), b(Blocks.SMOOTH_SANDSTONE));
		int top = floors * 3;
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SMOOTH_SANDSTONE));
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, wall);
			}
			for (int x : new int[]{-hw + 2, hw - 2}) {
				latticeWindow(p, x, y0 + 1, 0, L.FRONT, lattice);
			}
			p.set(-hw, y0 + 2, d / 2, b(Blocks.GLASS_PANE));
			p.set(hw, y0 + 2, d / 2, b(Blocks.GLASS_PANE));
			p.set(0, y0 + 2, d, b(Blocks.GLASS_PANE));
		}
		// The arched door with a tile frame.
		p.door(0, 1, 0, Blocks.JUNGLE_DOOR, L.BACK);
		p.set(-1, 3, -1, p.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, L.RIGHT, true));
		p.set(1, 3, -1, p.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, L.LEFT, true));
		p.set(0, 3, -1, tile(DyeColor.CYAN));
		// The roof terrace: a crenellated parapet, an awning on poles, a carpet, plants, a little dome on one corner.
		p.fill(-hw, top, 0, hw, top, d, b(Blocks.SMOOTH_SANDSTONE));
		for (int x = -hw; x <= hw; x++) {
			p.set(x, top + 1, 0, wall);
			p.set(x, top + 1, d, wall);
		}
		for (int z = 0; z <= d; z++) {
			p.set(-hw, top + 1, z, wall);
			p.set(hw, top + 1, z, wall);
		}
		for (int x = -hw; x <= hw; x += 2) {
			p.set(x, top + 2, 0, wall);
		}
		DyeColor a = StyleKit.pick(variant, DyeColor.RED, DyeColor.WHITE, DyeColor.ORANGE, DyeColor.LIGHT_BLUE);
		for (int x = -hw + 1; x <= 0; x++) {
			for (int z = 1; z <= 3; z++) {
				p.set(x, top + 4, z, StyleKit.wool(a));
			}
		}
		p.set(-hw + 1, top + 1, 1, b(Blocks.ACACIA_FENCE));
		p.set(-hw + 1, top + 2, 1, b(Blocks.ACACIA_FENCE));
		p.set(-hw + 1, top + 3, 1, b(Blocks.ACACIA_FENCE));
		p.set(0, top + 1, 3, b(Blocks.ACACIA_FENCE));
		p.set(0, top + 2, 3, b(Blocks.ACACIA_FENCE));
		p.set(0, top + 3, 3, b(Blocks.ACACIA_FENCE));
		p.set(-1, top + 1, 2, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
		p.set(-2, top + 1, 2, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
		p.set(hw - 1, top + 1, 1, b(Blocks.POTTED_CACTUS));
		if (hw >= 4) {
			dome(p, hw - 2, d - 2, top + 1, 2, b(Blocks.SMOOTH_QUARTZ));
		}
		p.set(1, top, d - 1, b(Blocks.SPRUCE_TRAPDOOR));
		p.ladder(1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.LIVING, -hw + 1, 0, 0, 1, d - 1);
		Interiors.room(p, r, Interiors.KITCHEN, 2, hw - 1, 0, 1, d - 2);
		for (int f = 1; f < floors; f++) {
			Interiors.room(p, r, Interiors.BEDROOM, -hw + 1, 0, f * 3, 1, d - 1);
			Interiors.room(p, r, p.b.type.beds > 3 ? Interiors.KIDS : Interiors.BEDROOM, 2, hw - 1, f * 3, 1, d - 2);
		}
		if (floors == 1) {
			p.bed(hw - 1, 1, d - 1, L.BACK, DyeColor.ORANGE);
			p.bed(hw - 2, 1, d - 1, L.BACK, DyeColor.ORANGE);
		}
		p.top = Math.max(p.top, top + 6);
	}

	// ------------------------------------------------------------------------------------------------
	// The palace: an arcade, blue tile bands, two slim towers with small domes, a great dome in the middle

	private static void palace(Plan p, DyeColor flag) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 64);
		BlockState wall = b(Blocks.SMOOTH_SANDSTONE);
		BlockState white = b(Blocks.SMOOTH_QUARTZ);
		StyleKit.base(p, hw, d, b(Blocks.SANDSTONE), b(Blocks.POLISHED_DIORITE));
		int h = 4;
		int floors = 2;
		int top = StyleKit.shell(p, hw, d, floors, h, wall, wall, tile(DyeColor.LIGHT_BLUE), b(Blocks.SMOOTH_STONE));
		// The arcade in front of the ground floor.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			int o = Math.floorMod(x + hw, 4);
			if (o != 0) {
				p.set(x, 1, 0, AIR);
				p.set(x, 2, 0, AIR);
				p.set(x, 3, 0, o == 2 ? AIR : p.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, o == 1 ? L.RIGHT : L.LEFT, true));
			}
			p.set(x, 1, 3, Math.abs(x) <= 1 ? AIR : wall);
			p.set(x, 2, 3, Math.abs(x) <= 1 ? AIR : wall);
			p.set(x, 3, 3, wall);
		}
		p.door(-1, 1, 3, Blocks.DARK_OAK_DOOR, L.BACK);
		p.door(0, 1, 3, Blocks.DARK_OAK_DOOR, L.BACK);
		p.door(1, 1, 3, Blocks.DARK_OAK_DOOR, L.BACK);
		for (int x = -hw + 2; x <= hw - 2; x += 2) {
			latticeWindow(p, x, h + 1, 0, L.FRONT, Blocks.DARK_OAK_TRAPDOOR);
			p.set(x, h + 1, d, b(Blocks.GLASS_PANE));
			p.set(x, h + 2, d, b(Blocks.GLASS_PANE));
		}
		Interiors.floor(p, r, -hw + 1, hw - 1, h, 1, d - 1);
		Interiors.room(p, r, Interiors.LOBBY, -hw + 1, hw - 1, 0, 4, d - 1, h);
		Interiors.room(p, r, Interiors.MEETING, -hw + 1, -1, h, 1, d - 1, h);
		Interiors.room(p, r, Interiors.OFFICE, 1, hw - 1, h, 1, d - 1, h);
		// The roof: a parapet, the drum and the great dome.
		StyleKit.flatRoof(p, hw, d, top, wall, wall, null);
		for (int x = -hw; x <= hw; x += 2) {
			p.set(x, top + 2, 0, wall);
		}
		int cz = d / 2;
		for (int y = top + 1; y <= top + 3; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					double dist = Math.sqrt(x * x + z * z);
					if (dist <= 4.4 && dist > 3.0) {
						p.set(x, y, cz + z, y == top + 2 && (x == 0 || z == 0) ? b(Blocks.GLASS) : tile(DyeColor.BLUE));
					}
				}
			}
		}
		dome(p, 0, cz, top + 4, 4, white);
		p.set(0, top + 3, cz, WARM);
		// Two slim towers at the front corners, each with a balcony and a small dome.
		for (int sx = -1; sx <= 1; sx += 2) {
			int tx = sx * (hw - 1);
			for (int y = 1; y <= top + 8; y++) {
				p.ring(tx - 1, 0, tx + 1, 2, y, y % 5 == 0 ? tile(DyeColor.CYAN) : wall);
			}
			for (int x = tx - 2; x <= tx + 2; x++) {
				for (int z = -1; z <= 3; z++) {
					p.set(x, top + 9, z, white);
				}
			}
			dome(p, tx, 1, top + 10, 1, white);
		}
		p.set(0, top + 1, 1, b(Blocks.IRON_BARS));
		p.set(0, top + 2, 1, b(Blocks.IRON_BARS));
		p.set(0, top + 3, 1, Blocks.BANNER.pick(flag).defaultBlockState());
		p.ladder(hw - 3, 1, top - 1, d - 1, L.BACK);
		p.top = top + 14;
	}
}
