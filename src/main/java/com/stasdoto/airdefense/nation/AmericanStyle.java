package com.stasdoto.airdefense.nation;

import static com.stasdoto.airdefense.nation.StyleKit.AIR;
import static com.stasdoto.airdefense.nation.StyleKit.b;
import static com.stasdoto.airdefense.nation.StyleKit.concrete;
import static com.stasdoto.airdefense.nation.StyleKit.glass;
import static com.stasdoto.airdefense.nation.StyleKit.slab;
import static com.stasdoto.airdefense.nation.StyleKit.terra;

import java.util.Random;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * 1.28, the American town: the glass downtown stays (the common towers and offices), round it brick walk-ups with
 * black fire escapes and wooden water tanks on the roofs, strip malls with a covered walk and lit signs, and suburbs of
 * siding houses with porches, shutters and a garage.
 */
final class AmericanStyle {
	private AmericanStyle() {
	}

	static boolean design(Plan p, int variant, DyeColor flag) {
		switch (p.b.type) {
			case PANEL5, APARTMENTS -> walkUp(p, 4, variant);
			case PANEL9 -> walkUp(p, 8, variant);
			case SHOP -> stripMall(p, variant);
			case HOUSE, COTTAGE -> suburban(p, 2, variant);
			case SMALL_HOUSE -> suburban(p, 1, variant);
			default -> {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------
	// A brick walk-up: stone lintels, a cornice, a fire escape down the front, a water tank on the roof

	private static void walkUp(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 51);
		BlockState brick = b(StyleKit.pick(variant, Blocks.BRICKS, Blocks.MUD_BRICKS, Blocks.BRICKS, Blocks.RESIN_BRICKS));
		BlockState stone = b(Blocks.SMOOTH_SANDSTONE);
		StyleKit.base(p, hw, d, b(Blocks.STONE_BRICKS), b(Blocks.POLISHED_ANDESITE));
		int top = StyleKit.shell(p, hw, d, floors, 3, b(Blocks.STONE_BRICKS), brick, brick, b(Blocks.SMOOTH_STONE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			for (int x = -hw + 2; x <= hw - 2; x += 2) {
				if (f == 0 && Math.abs(x) <= 1) {
					continue;
				}
				p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 3, -1, slab(Blocks.SMOOTH_SANDSTONE_SLAB, false));
				p.set(x, y0 + 1, d, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, d, b(Blocks.GLASS_PANE));
			}
		}
		// The ground floor: a deli and a laundromat behind big windows, the stoop to the door.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, 0, b(Blocks.GLASS));
				p.set(x, 2, 0, b(Blocks.GLASS));
			}
		}
		StyleKit.sign(p, -hw + 1, -2, 3, StyleKit.pick(variant, DyeColor.RED, DyeColor.GREEN, DyeColor.YELLOW));
		StyleKit.sign(p, 2, hw - 1, 3, StyleKit.pick(variant + 1, DyeColor.BLUE, DyeColor.WHITE, DyeColor.ORANGE));
		p.door(0, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		p.set(0, 0, -1, stone);
		p.set(0, 0, -2, p.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, L.BACK, false));
		p.set(-1, 1, -1, b(Blocks.IRON_BARS));
		p.set(1, 1, -1, b(Blocks.IRON_BARS));
		// The fire escape: a landing in front of two windows on every floor, railings, the ladder down.
		int fx0 = hw - 5;
		int fx1 = hw - 2;
		for (int f = 1; f < floors; f++) {
			int y0 = f * 3;
			for (int x = fx0; x <= fx1; x++) {
				p.set(x, y0, -1, StyleKit.ledge(p, Blocks.IRON_TRAPDOOR, L.FRONT).setValue(BlockStateProperties.HALF,
						net.minecraft.world.level.block.state.properties.Half.TOP));
				p.set(x, y0 + 1, -2, b(Blocks.IRON_BARS));
			}
			p.set(fx0, y0 + 1, -1, b(Blocks.IRON_BARS));
			p.set(fx1, y0 + 1, -1, b(Blocks.IRON_BARS));
			// The stair down to the next landing, drawn as a ladder of bars.
			p.set(fx0 + 1 + (f % 2), y0 - 1, -1, b(Blocks.IRON_BARS));
			p.set(fx0 + 1 + (f % 2), y0 - 2, -1, b(Blocks.IRON_BARS));
		}
		// The cornice.
		for (int x = -hw - 1; x <= hw + 1; x++) {
			p.set(x, top, -1, p.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, L.BACK, true));
			p.set(x, top + 1, -1, slab(Blocks.SMOOTH_SANDSTONE_SLAB, false));
		}
		StyleKit.flatRoof(p, hw, d, top, b(Blocks.BLACKSTONE), brick, null);
		waterTank(p, -hw + 3, d / 2, top + 1);
		StyleKit.roofKit(p, r, 0, hw - 2, 2, d - 2, top + 1, 3);
		p.ladder(-hw + 1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.SHOP, -hw + 1, -2, 0, 1, d - 1);
		Interiors.room(p, r, Interiors.CAFE, 2, hw - 1, 0, 1, d - 1);
		StyleKit.flats(p, r, hw, d, floors, 3, 6, brick, 1);
		p.top = top + 7;
	}

	/** A wooden water tank on steel legs with a cone roof. */
	static void waterTank(Plan p, int x, int z, int y) {
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				p.set(x + dx, y, z + dz, b(Blocks.IRON_BARS));
				p.set(x + dx, y + 1, z + dz, b(Blocks.IRON_BARS));
			}
		}
		for (int yy = y + 2; yy <= y + 4; yy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					p.set(x + dx, yy, z + dz, p.log(Blocks.STRIPPED_SPRUCE_LOG, 'y'));
				}
			}
		}
		p.set(x, y + 5, z, slab(Blocks.SPRUCE_SLAB, false));
		p.set(x - 1, y + 5, z, slab(Blocks.SPRUCE_SLAB, false));
		p.set(x + 1, y + 5, z, slab(Blocks.SPRUCE_SLAB, false));
		p.set(x, y + 5, z - 1, slab(Blocks.SPRUCE_SLAB, false));
		p.set(x, y + 5, z + 1, slab(Blocks.SPRUCE_SLAB, false));
	}

	// ------------------------------------------------------------------------------------------------
	// A strip mall: three stores under a covered walk, a lit sign over each

	private static void stripMall(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 52);
		BlockState stucco = StyleKit.pick(variant, b(Blocks.SMOOTH_SANDSTONE), concrete(DyeColor.WHITE), terra(DyeColor.WHITE));
		StyleKit.base(p, hw, d, concrete(DyeColor.GRAY), b(Blocks.POLISHED_DIORITE));
		for (int y = 1; y <= 4; y++) {
			p.ring(-hw, 0, hw, d, y, stucco);
		}
		DyeColor[] signs = {DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.ORANGE, DyeColor.PURPLE};
		int stores = 3;
		int w = (2 * hw + 1) / stores;
		for (int s = 0; s < stores; s++) {
			int x0 = -hw + s * w;
			int x1 = s == stores - 1 ? hw : x0 + w - 1;
			for (int x = x0 + 1; x < x1; x++) {
				p.set(x, 1, 0, b(Blocks.GLASS));
				p.set(x, 2, 0, b(Blocks.GLASS));
			}
			int door = (x0 + x1) / 2;
			p.door(door, 1, 0, Blocks.IRON_DOOR, L.BACK);
			StyleKit.sign(p, x0 + 1, x1 - 1, 3, signs[Math.floorMod(variant + s * 2 + r.nextInt(2), signs.length)]);
			if (x1 < hw) {
				Interiors.wallX(p, x1, 0, 1, d - 1, -99, stucco);
			}
			Interiors.room(p, r, r.nextInt(4) == 0 ? Interiors.CAFE : Interiors.SHOP, x0 + 1, x1 - 1, 0, 1, d - 1, 4);
		}
		// The covered walk: a flat roof on thin posts.
		for (int x = -hw; x <= hw; x++) {
			p.set(x, 5, -1, stucco);
			p.set(x, 5, -2, slab(Blocks.SMOOTH_STONE_SLAB, false));
			p.set(x, 0, -1, b(Blocks.SMOOTH_STONE));
			p.set(x, 0, -2, b(Blocks.SMOOTH_STONE));
		}
		for (int x = -hw; x <= hw; x += 4) {
			for (int y = 1; y <= 4; y++) {
				p.set(x, y, -2, b(Blocks.IRON_BARS));
			}
		}
		p.fill(-hw, 5, 0, hw, 5, d, b(Blocks.SMOOTH_STONE));
		p.ring(-hw, 0, hw, d, 6, stucco);
		// The pylon sign at the end.
		for (int y = 1; y <= 7; y++) {
			p.set(hw, y, -3, concrete(DyeColor.GRAY));
		}
		p.set(hw, 8, -3, glass(DyeColor.RED));
		p.set(hw, 8, -4, StyleKit.COLD);
		p.top = 9;
	}

	// ------------------------------------------------------------------------------------------------
	// A suburban house: siding, white trim, shutters, a porch with columns, a garage, grey shingles

	private static void suburban(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 53);
		BlockState[] sidings = {b(Blocks.BIRCH_PLANKS), terra(DyeColor.WHITE), terra(DyeColor.LIGHT_BLUE), b(Blocks.SMOOTH_SANDSTONE),
				terra(DyeColor.LIGHT_GRAY), terra(DyeColor.YELLOW)};
		BlockState siding = sidings[Math.floorMod(variant, sidings.length)];
		BlockState trim = b(Blocks.SMOOTH_QUARTZ);
		Block shutters = StyleKit.pick(variant / 2, Blocks.DARK_OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.WARPED_TRAPDOOR, Blocks.CRIMSON_TRAPDOOR);
		Block[] roof = StyleKit.pick(variant / 3, new Block[]{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES},
				new Block[]{Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE}, new Block[]{Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS});
		StyleKit.base(p, hw, d, concrete(DyeColor.GRAY), b(Blocks.OAK_PLANKS));
		int top = floors * 3;
		boolean garage = hw >= 4;
		int door = garage ? -1 : 0;
		int gx0 = hw - 3;
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.OAK_PLANKS));
				p.ring(-hw, 0, hw, d, y0, trim);
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, siding);
				p.set(-hw, y, 0, trim);
				p.set(hw, y, 0, trim);
				p.set(-hw, y, d, trim);
				p.set(hw, y, d, trim);
			}
			// Windows with shutters: beside the door below, evenly above.
			int[] wins = f == 0 ? (garage ? new int[]{-hw + 1} : new int[]{-2, 2}) : new int[]{-hw + 2, hw - 2};
			for (int x : wins) {
				p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x - 1, y0 + 2, -1, StyleKit.shutter(p, shutters, L.FRONT));
				p.set(x + 1, y0 + 2, -1, StyleKit.shutter(p, shutters, L.FRONT));
			}
			for (int z = 2; z < d; z += 3) {
				p.set(-hw, y0 + 2, z, b(Blocks.GLASS_PANE));
				p.set(0, y0 + 2, d, b(Blocks.GLASS_PANE));
			}
		}
		if (garage) {
			// The garage on the right: a wide white door, a wall inside, a lamp.
			for (int x = gx0; x <= hw - 1; x++) {
				p.set(x, 1, 0, concrete(DyeColor.WHITE));
				p.set(x, 2, 0, concrete(DyeColor.WHITE));
				p.set(x, 3, 0, trim);
			}
			for (int y = 1; y <= 2; y++) {
				for (int z = 1; z < d; z++) {
					p.set(gx0 - 1, y, z, siding);
				}
			}
			p.set(gx0 - 1, 1, d / 2, AIR);
			p.set(gx0 - 1, 2, d / 2, AIR);
			p.set(hw - 2, 3, -1, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, false));
		}
		// The front door with a porch: steps, two white columns, a little roof.
		p.door(door, 1, 0, Blocks.OAK_DOOR, L.BACK);
		for (int x = door - 2; x <= door + 2; x++) {
			if (garage && x >= gx0) {
				continue;
			}
			p.set(x, 0, -1, b(Blocks.OAK_PLANKS));
			p.set(x, 0, -2, b(Blocks.OAK_PLANKS));
			p.set(x, 3, -1, slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
			p.set(x, 3, -2, slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
		}
		p.set(door, 0, -3, p.stairs(Blocks.OAK_STAIRS, L.BACK, false));
		for (int y = 1; y <= 2; y++) {
			p.set(door - 2, y, -2, b(Blocks.QUARTZ_PILLAR));
			if (!garage || door + 2 < gx0) {
				p.set(door + 2, y, -2, b(Blocks.QUARTZ_PILLAR));
			}
		}
		p.set(door - 1, 1, -2, b(Blocks.BIRCH_FENCE));
		p.set(door, 2, -2, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
		p.fill(-hw, top, 0, hw, top, d, b(Blocks.OAK_PLANKS));
		p.roofAlongX(top + 1, roof[0], roof[1], siding.getBlock());
		for (int x = -hw - 1; x <= hw + 1; x++) {
			p.set(x, top, -1, trim);
			p.set(x, top, d + 1, trim);
		}
		// Inside.
		p.ladder(-hw + 1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.LIVING, -hw + 2, garage ? gx0 - 2 : hw - 1, 0, 1, d - 1);
		for (int f = 1; f < floors; f++) {
			Interiors.room(p, r, Interiors.BEDROOM, -hw + 2, 0, f * 3, 1, d - 1);
			Interiors.room(p, r, p.b.type.beds > 3 ? Interiors.KIDS : Interiors.BEDROOM, 1, hw - 1, f * 3, 1, d - 1);
		}
		if (floors == 1) {
			p.bed(-hw + 2, 1, d - 1, L.BACK, DyeColor.WHITE);
			p.bed(-hw + 3, 1, d - 1, L.BACK, DyeColor.WHITE);
		}
		p.top = Math.max(p.top, top + d / 2 + 2);
	}
}
