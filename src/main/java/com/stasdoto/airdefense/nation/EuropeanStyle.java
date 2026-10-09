package com.stasdoto.airdefense.nation;

import static com.stasdoto.airdefense.nation.StyleKit.AIR;
import static com.stasdoto.airdefense.nation.StyleKit.WARM;
import static com.stasdoto.airdefense.nation.StyleKit.b;
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
 * 1.28, the European town: rows of narrow plastered town houses in pastel colours under steep tiled roofs with dormers
 * and chimneys, shutters and flower boxes, shops and cafes with awnings below; half-timbered houses and stone
 * cottages on the edge; a church with a tall spire; a town hall with an arcade and a clock tower.
 */
final class EuropeanStyle {
	private EuropeanStyle() {
	}

	private static final DyeColor[] PLASTER = {DyeColor.WHITE, DyeColor.YELLOW, DyeColor.PINK, DyeColor.LIGHT_BLUE, DyeColor.ORANGE,
			DyeColor.LIGHT_GRAY, DyeColor.LIME, DyeColor.MAGENTA};
	/** Roofs: orange clay tiles, red brick, grey slate. */
	private static final Block[][] ROOFS = {{Blocks.RESIN_BRICK_STAIRS, Blocks.RESIN_BRICKS}, {Blocks.BRICK_STAIRS, Blocks.BRICKS},
			{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES}};

	static boolean design(Plan p, int variant, DyeColor flag) {
		switch (p.b.type) {
			case PANEL5, APARTMENTS -> townhouses(p, 4, variant);
			case PANEL9 -> townhouses(p, 6, variant);
			case TOWER -> {
				// One tall building in three is the church; the others are the common glass towers.
				if (Math.floorMod(variant, 3) != 0) {
					return false;
				}
				church(p, variant);
			}
			case SHOP -> shopHouse(p, variant);
			case HOUSE, COTTAGE -> timbered(p, 2, variant);
			case SMALL_HOUSE -> cottage(p, variant);
			case CITY_HALL -> townHall(p, flag);
			default -> {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------
	// A row of town houses: each a few bays wide, its own colour; shops below, a steep roof with dormers above

	private static void townhouses(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 41);
		Block[] roof = ROOFS[Math.floorMod(variant, ROOFS.length)];
		BlockState stone = b(Blocks.SMOOTH_STONE);
		StyleKit.base(p, hw, d, b(Blocks.STONE_BRICKS), b(Blocks.SMOOTH_STONE));
		int h = 3;
		int top = floors * h;
		// Split the front into houses 5 to 7 blocks wide.
		int x = -hw;
		int house = 0;
		while (x < hw) {
			int w = Math.min(hw - x, 5 + r.nextInt(3));
			if (hw - (x + w) < 4) {
				w = hw - x;
			}
			int x0 = x;
			int x1 = x + w;
			BlockState wall = terra(PLASTER[Math.floorMod(variant + house * 3 + r.nextInt(2), PLASTER.length)]);
			BlockState trim = house % 2 == 0 ? b(Blocks.SMOOTH_QUARTZ) : b(Blocks.SMOOTH_SANDSTONE);
			Block shutters = StyleKit.pick(variant + house, Blocks.SPRUCE_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.OAK_TRAPDOOR,
					Blocks.BIRCH_TRAPDOOR);
			for (int f = 0; f < floors; f++) {
				int y0 = f * h;
				if (f > 0) {
					p.fill(x0, y0, 0, x1, y0, d, stone);
					// The floor's edge on the facades in the house's own colour (no grey stripe between the floors).
					for (int xx = x0; xx <= x1; xx++) {
						p.set(xx, y0, 0, f == 1 ? trim : wall);
						p.set(xx, y0, d, wall);
					}
				}
				for (int y = y0 + 1; y <= y0 + 2; y++) {
					for (int xx = x0; xx <= x1; xx++) {
						p.set(xx, y, 0, f == 0 ? b(Blocks.STONE_BRICKS) : wall);
						p.set(xx, y, d, wall);
					}
				}
				// A trim line at each floor.
				for (int xx = x0; xx <= x1; xx++) {
					p.set(xx, y0 + 3, 0, f == floors - 1 ? trim : wall);
				}
				// The windows: tall, with shutters and a flower box on the upper floors.
				for (int xx = x0 + 1; xx < x1; xx += 2) {
					if (f == 0) {
						continue;
					}
					p.set(xx, y0 + 1, 0, b(Blocks.GLASS_PANE));
					p.set(xx, y0 + 2, 0, b(Blocks.GLASS_PANE));
					p.set(xx, y0 + 1, d, b(Blocks.GLASS_PANE));
					p.set(xx, y0 + 2, d, b(Blocks.GLASS_PANE));
					if (xx - 1 > x0 || x0 == -hw) {
						p.set(xx - 1, y0 + 2, -1, StyleKit.shutter(p, shutters, L.FRONT));
					}
					p.set(xx + 1, y0 + 2, -1, StyleKit.shutter(p, shutters, L.FRONT));
					if (r.nextInt(3) > 0) {
						p.set(xx, y0 + 1, -1, StyleKit.ledge(p, Blocks.SPRUCE_TRAPDOOR, L.FRONT));
						p.set(xx, y0 + 2, -1, flowers(r));
					}
				}
			}
			// The ground floor: a shop window with an awning and a sign, or a door with a lamp.
			boolean shop = r.nextInt(3) > 0;
			int door = x0 + 1;
			p.door(door, 1, 0, StyleKit.pick(house, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_DOOR, Blocks.OAK_DOOR), L.BACK);
			if (shop) {
				for (int xx = door + 1; xx < x1; xx++) {
					p.set(xx, 1, 0, b(Blocks.GLASS));
					p.set(xx, 2, 0, b(Blocks.GLASS));
				}
				DyeColor a = PLASTER[r.nextInt(PLASTER.length)];
				StyleKit.awning(p, door + 1, x1 - 1, 3, a, DyeColor.WHITE);
				Interiors.room(p, r, r.nextBoolean() ? Interiors.CAFE : Interiors.SHOP, x0 + 1, x1 - 1, 0, 1, d - 1);
			} else {
				p.set(door + 1, 2, -1, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, false));
				Interiors.room(p, r, Interiors.LIVING, x0 + 1, x1 - 1, 0, 1, d - 1);
			}
			// Party walls between the houses, the flats above.
			if (x1 < hw) {
				for (int f = 0; f < floors; f++) {
					Interiors.wallX(p, x1, f * h, 1, d - 1, -99, wall);
				}
			}
			for (int f = 1; f < floors; f++) {
				Interiors.floor(p, r, x0 + 1, x1 - 1, f * h, 1, d - 1);
				Interiors.room(p, r, f == floors - 1 ? Interiors.BEDROOM : Interiors.flatRoom(r), x0 + 1, x1 - 1, f * h, 1, d - 1);
			}
			p.ladder(x1 - 1, 1, top - 1, d - 1, L.BACK);
			x = x1;
			house++;
		}
		// The side walls of the row.
		for (int y = 1; y < top; y++) {
			for (int z = 0; z <= d; z++) {
				if (!p.has(-hw, y, z)) {
					p.set(-hw, y, z, b(Blocks.STONE_BRICKS));
				}
				if (!p.has(hw, y, z)) {
					p.set(hw, y, z, b(Blocks.STONE_BRICKS));
				}
			}
		}
		p.fill(-hw, top, 0, hw, top, d, stone);
		// The cornice, the roof with dormers, chimneys.
		for (int xx = -hw - 1; xx <= hw + 1; xx++) {
			p.set(xx, top, -1, p.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, L.BACK, true));
		}
		StyleKit.steepRoof(p, hw, d, top + 1, roof[0], roof[1], b(Blocks.STONE_BRICKS), 4, terra(DyeColor.WHITE));
		for (int xx = -hw + 3; xx < hw - 1; xx += 6) {
			for (int y = top + 1; y <= top + d / 2 + 2; y++) {
				p.set(xx, y, d - 2, b(Blocks.BRICKS));
			}
		}
		p.top = top + d / 2 + 3;
	}

	private static BlockState flowers(Random r) {
		Block[] pots = {Blocks.POTTED_RED_TULIP, Blocks.POTTED_POPPY, Blocks.POTTED_ORANGE_TULIP, Blocks.POTTED_PINK_TULIP,
				Blocks.POTTED_CORNFLOWER, Blocks.POTTED_AZURE_BLUET};
		return b(pots[r.nextInt(pots.length)]);
	}

	// ------------------------------------------------------------------------------------------------
	// A church: a stone nave with tall coloured windows, a bell tower with a clock and a spire

	private static void church(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 42);
		BlockState stone = b(StyleKit.pick(variant, Blocks.STONE_BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.DEEPSLATE_BRICKS));
		BlockState accent = b(Blocks.CHISELED_STONE_BRICKS);
		int nw = 5;
		StyleKit.base(p, nw, d, stone, b(Blocks.POLISHED_ANDESITE));
		int wallTop = 9;
		for (int y = 1; y <= wallTop; y++) {
			p.ring(-nw, 0, nw, d, y, stone);
		}
		// Buttresses and tall pointed windows of coloured glass along the nave.
		DyeColor[] colors = {DyeColor.BLUE, DyeColor.RED, DyeColor.YELLOW, DyeColor.PURPLE};
		for (int z = 3; z < d - 1; z += 3) {
			for (int y = 2; y <= 7; y++) {
				p.set(-nw, y, z, glass(colors[(z + y) % colors.length]));
				p.set(nw, y, z, glass(colors[(z + y + 1) % colors.length]));
			}
			p.set(-nw, 8, z, accent);
			p.set(nw, 8, z, accent);
			for (int y = 1; y <= 6; y++) {
				p.set(-nw - 1, y, z + 1, stone);
				p.set(nw + 1, y, z + 1, stone);
			}
			p.set(-nw - 1, 7, z + 1, p.stairs(Blocks.STONE_BRICK_STAIRS, L.RIGHT, false));
			p.set(nw + 1, 7, z + 1, p.stairs(Blocks.STONE_BRICK_STAIRS, L.LEFT, false));
		}
		// The steep roof of the nave.
		for (int k = 0; k <= nw + 1; k++) {
			int y = wallTop + 1 + k;
			int xx = nw + 1 - k;
			for (int z = -1; z <= d + 1; z++) {
				p.set(-xx, y, z, p.stairs(Blocks.DEEPSLATE_TILE_STAIRS, L.RIGHT, false));
				p.set(xx, y, z, p.stairs(Blocks.DEEPSLATE_TILE_STAIRS, L.LEFT, false));
			}
			for (int gx = -(nw - k); gx <= nw - k; gx++) {
				p.set(gx, y, d, stone);
			}
		}
		for (int z = -1; z <= d + 1; z++) {
			p.set(0, wallTop + 2 + nw, z, b(Blocks.DEEPSLATE_TILES));
		}
		// The rose window over the back.
		p.set(0, 7, d, glass(DyeColor.RED));
		p.set(-1, 7, d, glass(DyeColor.BLUE));
		p.set(1, 7, d, glass(DyeColor.BLUE));
		p.set(0, 6, d, glass(DyeColor.YELLOW));
		p.set(0, 8, d, glass(DyeColor.YELLOW));
		// The tower in front: five wide, up past the roof; the bell, the clock, the spire.
		int tz0 = 0;
		int tz1 = 4;
		int towerTop = 30;
		for (int y = 1; y <= towerTop; y++) {
			p.ring(-2, tz0, 2, tz1, y, y % 8 == 0 ? accent : stone);
		}
		for (int y = 1; y <= towerTop; y++) {
			for (int z = tz0 + 1; z < tz1; z++) {
				for (int xx = -1; xx <= 1; xx++) {
					p.set(xx, y, z, AIR);
				}
			}
		}
		// The doorway in the tower: a tall arch, double doors.
		p.door(0, 1, tz0, Blocks.DARK_OAK_DOOR, L.BACK);
		p.set(0, 3, tz0, p.stairs(Blocks.STONE_BRICK_STAIRS, L.BACK, true));
		p.set(-1, 3, tz0 - 1, p.stairs(Blocks.STONE_BRICK_STAIRS, L.RIGHT, true));
		p.set(1, 3, tz0 - 1, p.stairs(Blocks.STONE_BRICK_STAIRS, L.LEFT, true));
		p.set(0, 4, tz0 - 1, accent);
		// The belfry: openings on every side, the bell.
		for (int y = 23; y <= 25; y++) {
			p.set(0, y, tz0, AIR);
			p.set(0, y, tz1, AIR);
			p.set(-2, y, 2, AIR);
			p.set(2, y, 2, AIR);
		}
		p.fill(-1, 22, tz0 + 1, 1, 22, tz1 - 1, b(Blocks.DARK_OAK_PLANKS));
		p.set(0, 25, 2, b(Blocks.BELL));
		// The clock faces.
		p.set(0, 18, tz0, b(Blocks.TARGET));
		p.set(-2, 18, 2, b(Blocks.TARGET));
		p.set(2, 18, 2, b(Blocks.TARGET));
		// The spire: green copper, narrowing up to a cross-less finial.
		Block copper = Blocks.CUT_COPPER.waxed().oxidized();
		for (int k = 0; k < 12; k++) {
			int y = towerTop + 1 + k;
			int s = k < 3 ? 2 : k < 7 ? 1 : 0;
			p.fill(-s, y, 2 - s, s, y, 2 + s, b(copper));
		}
		p.set(0, towerTop + 13, 2, b(Blocks.IRON_BARS));
		p.set(0, towerTop + 14, 2, b(Blocks.GOLD_BLOCK));
		// Inside: rows of pews, the altar table, candles, a carpet down the middle.
		for (int z = tz1 + 2; z < d - 3; z += 2) {
			for (int xx = -nw + 1; xx <= nw - 1; xx++) {
				if (Math.abs(xx) > 0) {
					p.set(xx, 1, z, p.stairs(Blocks.DARK_OAK_STAIRS, L.BACK, false));
				}
			}
		}
		for (int z = tz1 + 1; z < d - 1; z++) {
			p.set(0, 1, z, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
		}
		p.fill(-1, 1, d - 2, 1, 1, d - 2, b(Blocks.POLISHED_DIORITE));
		p.set(-1, 2, d - 2, b(Blocks.CANDLE));
		p.set(1, 2, d - 2, b(Blocks.CANDLE));
		for (int z = 4; z < d; z += 4) {
			p.set(0, wallTop, z, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
		}
		p.ladder(1, 1, 22, tz1 - 1, L.BACK);
		p.top = towerTop + 15;
	}

	// ------------------------------------------------------------------------------------------------
	// A shop with a flat above: a big window, an awning, a hanging sign; a steep little roof

	private static void shopHouse(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 43);
		BlockState wall = terra(PLASTER[Math.floorMod(variant, PLASTER.length)]);
		Block[] roof = ROOFS[Math.floorMod(variant / 3, ROOFS.length)];
		StyleKit.base(p, hw, d, b(Blocks.STONE_BRICKS), b(Blocks.POLISHED_ANDESITE));
		for (int y = 1; y <= 2; y++) {
			p.ring(-hw, 0, hw, d, y, b(Blocks.STONE_BRICKS));
		}
		p.fill(-hw, 3, 0, hw, 3, d, b(Blocks.SMOOTH_STONE));
		for (int y = 4; y <= 5; y++) {
			p.ring(-hw, 0, hw, d, y, wall);
		}
		for (int x = -hw + 1; x <= hw - 1; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, 0, b(Blocks.GLASS));
				p.set(x, 2, 0, b(Blocks.GLASS));
			}
			if (Math.floorMod(x, 3) == 0) {
				p.set(x, 4, 0, b(Blocks.GLASS_PANE));
				p.set(x, 5, 0, b(Blocks.GLASS_PANE));
				p.set(x, 4, -1, StyleKit.ledge(p, Blocks.SPRUCE_TRAPDOOR, L.FRONT));
				p.set(x, 5, -1, flowers(r));
			}
		}
		p.door(0, 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
		DyeColor a = StyleKit.pick(variant, DyeColor.GREEN, DyeColor.RED, DyeColor.BLUE, DyeColor.ORANGE, DyeColor.BLACK);
		StyleKit.awning(p, -hw + 1, -2, 3, a, DyeColor.WHITE);
		StyleKit.awning(p, 2, hw - 1, 3, a, DyeColor.WHITE);
		// The hanging sign on an iron bracket.
		p.set(-hw, 3, -1, b(Blocks.IRON_BARS));
		p.set(-hw, 2, -1, glass(a));
		p.fill(-hw, 6, 0, hw, 6, d, wall);
		p.roofAlongX(7, roof[0], roof[1], wall.getBlock());
		Interiors.room(p, r, r.nextInt(3) == 0 ? Interiors.CAFE : Interiors.SHOP, -hw + 1, hw - 1, 0, 1, d - 1);
		Interiors.room(p, r, Interiors.BEDROOM, -hw + 1, hw - 1, 3, 1, d - 1);
		p.ladder(hw - 1, 1, 5, d - 1, L.BACK);
		p.top = 7 + d / 2 + 1;
	}

	// ------------------------------------------------------------------------------------------------
	// A half-timbered house: white plaster between dark beams, a steep roof, flower boxes

	private static void timbered(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 44);
		BlockState plaster = terra(StyleKit.pick(variant, DyeColor.WHITE, DyeColor.WHITE, DyeColor.YELLOW, DyeColor.PINK));
		BlockState beam = b(Blocks.STRIPPED_DARK_OAK_LOG);
		BlockState beamX = p.log(Blocks.STRIPPED_DARK_OAK_LOG, 'x');
		BlockState beamZ = p.log(Blocks.STRIPPED_DARK_OAK_LOG, 'z');
		Block[] roof = ROOFS[Math.floorMod(variant / 2, ROOFS.length)];
		StyleKit.base(p, hw, d, b(Blocks.COBBLESTONE), b(Blocks.SPRUCE_PLANKS));
		int top = floors * 3;
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SPRUCE_PLANKS));
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, f == 0 && floors > 1 ? b(Blocks.STONE_BRICKS) : plaster);
			}
			// The frame: posts at the corners and every few blocks, a beam at each floor, braces.
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				for (int x = -hw; x <= hw; x += hw) {
					p.set(x, y, 0, beam);
					p.set(x, y, d, beam);
				}
				for (int z = 0; z <= d; z += d / 2) {
					p.set(-hw, y, z, beam);
					p.set(hw, y, z, beam);
				}
			}
			for (int x = -hw; x <= hw; x++) {
				p.set(x, y0 + 3, 0, beamX);
				p.set(x, y0 + 3, d, beamX);
			}
			for (int z = 0; z <= d; z++) {
				p.set(-hw, y0 + 3, z, beamZ);
				p.set(hw, y0 + 3, z, beamZ);
			}
			if (f > 0 || floors == 1) {
				// Braces: a pair of diagonals between the windows.
				p.set(-hw + 1, y0 + 1, 0, beamX);
				p.set(-hw + 2, y0 + 2, 0, beamX);
				p.set(hw - 1, y0 + 1, 0, beamX);
				p.set(hw - 2, y0 + 2, 0, beamX);
			}
			for (int x : new int[]{-2, 2}) {
				if (f == 0 && x == 2 && floors > 1) {
					continue;
				}
				p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 1, -1, StyleKit.ledge(p, Blocks.DARK_OAK_TRAPDOOR, L.FRONT));
				p.set(x, y0 + 2, -1, flowers(r));
			}
			for (int z = 2; z < d; z += 3) {
				p.set(-hw, y0 + 2, z, b(Blocks.GLASS_PANE));
				p.set(hw, y0 + 2, z, b(Blocks.GLASS_PANE));
				p.set(0, y0 + 2, d, b(Blocks.GLASS_PANE));
			}
		}
		p.door(0, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		p.set(0, 3, -1, slab(Blocks.DARK_OAK_SLAB, true));
		p.set(1, 2, -1, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, false));
		p.fill(-hw, top, 0, hw, top, d, b(Blocks.SPRUCE_PLANKS));
		p.roofAlongZ(top + 1, roof[0], roof[1], plaster.getBlock(), true, true);
		for (int y = top + 1; y <= top + hw + 2; y++) {
			p.set(-hw + 2, y, d - 2, b(Blocks.BRICKS));
		}
		p.ladder(hw - 1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.LIVING, -hw + 1, 0, 0, 1, d - 1);
		Interiors.room(p, r, Interiors.KITCHEN, 1, hw - 2, 0, 1, d - 2);
		for (int f = 1; f < floors; f++) {
			Interiors.room(p, r, Interiors.BEDROOM, -hw + 1, 0, f * 3, 1, d - 1);
			Interiors.room(p, r, f == 1 && p.b.type.beds > 3 ? Interiors.KIDS : Interiors.BEDROOM, 1, hw - 2, f * 3, 1, d - 2);
		}
		p.top = Math.max(p.top, top + hw + 3);
	}

	// ------------------------------------------------------------------------------------------------
	// A stone cottage: rough walls, a slate roof, a blue door, roses by it

	private static void cottage(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 45);
		BlockState wall = b(StyleKit.pick(variant, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS));
		StyleKit.base(p, hw, d, b(Blocks.COBBLESTONE), b(Blocks.SPRUCE_PLANKS));
		for (int y = 1; y <= 3; y++) {
			p.ring(-hw, 0, hw, d, y, wall);
		}
		p.set(-2, 2, 0, b(Blocks.GLASS_PANE));
		p.set(2, 2, 0, b(Blocks.GLASS_PANE));
		p.set(-2, 2, -1, flowers(r));
		p.set(2, 2, -1, flowers(r));
		p.set(-hw, 2, d / 2, b(Blocks.GLASS_PANE));
		p.set(hw, 2, d / 2, b(Blocks.GLASS_PANE));
		p.door(0, 1, 0, Blocks.WARPED_DOOR, L.BACK);
		p.set(-1, 1, -1, b(Blocks.ROSE_BUSH));
		p.fill(-hw, 4, 0, hw, 4, d, b(Blocks.SPRUCE_PLANKS));
		p.roofAlongZ(4, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, wall.getBlock(), true, false);
		for (int y = 4; y <= 4 + hw + 1; y++) {
			p.set(hw - 1, y, d - 1, b(Blocks.COBBLESTONE));
		}
		Interiors.room(p, r, Interiors.KITCHEN, -hw + 1, hw - 1, 0, 1, d / 2);
		p.bed(-hw + 1, 1, d - 1, L.BACK, DyeColor.BLUE);
		p.bed(-hw + 2, 1, d - 1, L.BACK, DyeColor.BLUE);
		p.top = Math.max(p.top, 4 + hw + 3);
	}

	// ------------------------------------------------------------------------------------------------
	// The town hall: an arcade below, brick above, a steep roof with dormers, a clock tower in the middle

	private static void townHall(Plan p, DyeColor flag) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 46);
		BlockState stone = b(Blocks.STONE_BRICKS);
		BlockState brick = b(Blocks.BRICKS);
		StyleKit.base(p, hw, d, stone, b(Blocks.POLISHED_ANDESITE));
		int h = 4;
		int floors = 3;
		int top = StyleKit.shell(p, hw, d, floors, h, stone, brick, b(Blocks.SMOOTH_QUARTZ), b(Blocks.SMOOTH_STONE));
		// The arcade: arches along the front of the ground floor.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			int o = Math.floorMod(x + hw, 4);
			if (o != 0) {
				p.set(x, 1, 0, AIR);
				p.set(x, 2, 0, AIR);
				p.set(x, 3, 0, o == 2 ? AIR : p.stairs(Blocks.STONE_BRICK_STAIRS, o == 1 ? L.RIGHT : L.LEFT, true));
			}
		}
		// A wall behind the arcade with the doors.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			for (int y = 1; y <= 3; y++) {
				p.set(x, y, 2, Math.abs(x) <= 1 ? AIR : (Math.floorMod(x, 3) == 0 && y == 2 ? b(Blocks.GLASS_PANE) : stone));
			}
		}
		p.door(-1, 1, 2, Blocks.DARK_OAK_DOOR, L.BACK);
		p.door(0, 1, 2, Blocks.DARK_OAK_DOOR, L.BACK);
		p.door(1, 1, 2, Blocks.DARK_OAK_DOOR, L.BACK);
		for (int f = 1; f < floors; f++) {
			int y0 = f * h;
			for (int x = -hw + 2; x <= hw - 2; x += 2) {
				for (int y = y0 + 1; y <= y0 + 3; y++) {
					p.set(x, y, 0, b(Blocks.GLASS_PANE));
					p.set(x, y, d, b(Blocks.GLASS_PANE));
				}
				p.set(x, y0 + 1, -1, StyleKit.ledge(p, Blocks.DARK_OAK_TRAPDOOR, L.FRONT));
			}
			StyleKit.windowsSides(p, hw, d, y0, 2, 3, b(Blocks.GLASS_PANE));
			Interiors.floor(p, r, -hw + 1, hw - 1, y0, 1, d - 1);
			Interiors.room(p, r, f == 1 ? Interiors.MEETING : Interiors.OFFICE, -hw + 1, -1, y0, 1, d - 1, h);
			Interiors.room(p, r, Interiors.OFFICE, 1, hw - 1, y0, 1, d - 1, h);
		}
		Interiors.room(p, r, Interiors.LOBBY, -hw + 1, hw - 1, 0, 3, d - 1, h);
		StyleKit.steepRoof(p, hw, d, top + 1, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, brick, 4, b(Blocks.SMOOTH_QUARTZ));
		// The clock tower out of the middle of the roof.
		int cz = d / 2;
		int ridge = top + 1 + d / 2;
		for (int y = top + 1; y <= ridge + 3; y++) {
			p.ring(-2, cz - 2, 2, cz + 2, y, y == ridge + 3 ? b(Blocks.SMOOTH_QUARTZ) : brick);
		}
		p.set(0, ridge + 1, cz - 2, b(Blocks.TARGET));
		p.set(0, ridge + 1, cz + 2, b(Blocks.TARGET));
		p.set(0, ridge + 2, cz, WARM);
		Block copper = Blocks.CUT_COPPER.waxed().oxidized();
		for (int k = 0; k < 4; k++) {
			int s = k < 2 ? 1 : 0;
			p.fill(-s, ridge + 4 + k, cz - s, s, ridge + 4 + k, cz + s, b(copper));
		}
		p.set(0, ridge + 8, cz, Blocks.BANNER.pick(flag).defaultBlockState());
		p.ladder(hw - 1, 1, top - 1, d - 1, L.BACK);
		p.top = ridge + 9;
	}
}
