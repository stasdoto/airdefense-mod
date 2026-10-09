package com.stasdoto.airdefense.nation;

import static com.stasdoto.airdefense.nation.StyleKit.AIR;
import static com.stasdoto.airdefense.nation.StyleKit.COLD;
import static com.stasdoto.airdefense.nation.StyleKit.WARM;
import static com.stasdoto.airdefense.nation.StyleKit.b;
import static com.stasdoto.airdefense.nation.StyleKit.concrete;
import static com.stasdoto.airdefense.nation.StyleKit.glass;
import static com.stasdoto.airdefense.nation.StyleKit.pane;
import static com.stasdoto.airdefense.nation.StyleKit.slab;
import static com.stasdoto.airdefense.nation.StyleKit.terra;

import java.util.List;
import java.util.Random;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * 1.28, the Soviet town: five-storey "khrushchyovkas" of white brick or grey panels with a patchwork of glazed
 * balconies, nine-storey panel blocks, a sixteen-storey tower with a mosaic, Stalin-era houses with cornices in the
 * centre, a research institute with ribbon windows, glass grocery pavilions, wooden dachas with tin roofs and carved
 * window frames, a city hall with columns and a spire with a star.
 */
final class SovietStyle {
	private SovietStyle() {
	}

	static boolean design(Plan p, int variant, DyeColor flag) {
		switch (p.b.type) {
			case PANEL5 -> fiveStorey(p, variant);
			case PANEL9 -> panelBlock(p, 9, variant);
			case APARTMENTS -> stalinka(p, 3, variant);
			case TOWER -> tower(p, variant);
			case OFFICE -> institute(p, variant);
			case SHOP -> pavilion(p, variant);
			case HOUSE, COTTAGE -> dacha(p, 2, variant);
			case SMALL_HOUSE -> dacha(p, 1, variant);
			case CITY_HALL -> hall(p, flag);
			default -> {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------
	// Five storeys: white silicate brick or grey panels, balconies glazed each its own way

	private static void fiveStorey(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 31);
		boolean brick = Math.floorMod(variant, 2) == 0;
		// White silicate brick, or light grey panels with grout lines a shade darker.
		BlockState wall = brick ? b(Blocks.SMOOTH_QUARTZ) : b(Blocks.SMOOTH_STONE);
		BlockState seam = brick ? b(Blocks.SMOOTH_QUARTZ) : b(Blocks.STONE);
		BlockState plinth = b(Blocks.POLISHED_ANDESITE);
		int floors = 5;
		StyleKit.base(p, hw, d, concrete(DyeColor.GRAY), b(Blocks.SMOOTH_STONE));
		int top = StyleKit.shell(p, hw, d, floors, 3, plinth, wall, seam, b(Blocks.SMOOTH_STONE));
		// Panel seams: vertical lines every three blocks.
		if (!brick) {
			for (int x = -hw; x <= hw; x += 3) {
				for (int y = 4; y < top; y++) {
					p.set(x, y, 0, seam);
					p.set(x, y, d, seam);
				}
			}
		}
		List<Integer> entrances = Architecture.panelEntrances(hw);
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			for (int x = -hw + 1; x <= hw - 1; x++) {
				int o = Math.floorMod(x + hw, 6);
				boolean entrance = false;
				for (int e : entrances) {
					entrance |= x == e || x == e + 1;
				}
				if (o == 1 || o == 2 || o == 4) {
					p.set(x, y0 + 2, d, b(Blocks.GLASS_PANE));
					if (f > 0) {
						p.set(x, y0 + 1, d, b(Blocks.GLASS_PANE));
					}
				}
				if (entrance) {
					if (f > 0) {
						// The stairwell's narrow windows between the floors.
						p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
					}
					continue;
				}
				if (o == 1 || o == 2) {
					p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
					if (f > 0) {
						p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
					}
				}
			}
			// Balconies: every other bay, from the first floor up; each owner glazed it (or not) his own way.
			if (f == 0) {
				continue;
			}
			for (int bay = 0; bay * 6 + 4 < 2 * hw; bay++) {
				int x0 = -hw + bay * 6 + 4;
				int x1 = x0 + 1;
				boolean skip = false;
				for (int e : entrances) {
					skip |= Math.abs(x0 - e) <= 2;
				}
				if (skip || x1 > hw - 1) {
					continue;
				}
				p.set(x0, y0 + 1, 0, b(Blocks.GLASS_PANE));
				p.set(x0, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x1, y0 + 1, 0, AIR);
				p.set(x1, y0 + 2, 0, AIR);
				p.door(x1, y0 + 1, 0, Blocks.BIRCH_DOOR, L.BACK);
				p.set(x0, y0, -1, b(Blocks.SMOOTH_STONE));
				p.set(x1, y0, -1, b(Blocks.SMOOTH_STONE));
				int kind = r.nextInt(5);
				BlockState rail = switch (kind) {
					case 0 -> b(Blocks.IRON_BARS);
					case 1 -> concrete(DyeColor.WHITE);
					case 2 -> b(Blocks.SPRUCE_PLANKS);
					case 3 -> terra(DyeColor.LIGHT_GRAY);
					default -> b(Blocks.BIRCH_PLANKS);
				};
				p.set(x0, y0 + 1, -1, rail);
				p.set(x1, y0 + 1, -1, rail);
				if (kind >= 2) {
					// Glazed with whatever frames there were.
					BlockState win = kind == 3 ? pane(DyeColor.WHITE) : b(Blocks.GLASS_PANE);
					p.set(x0, y0 + 2, -1, win);
					p.set(x1, y0 + 2, -1, win);
					p.set(x0, y0 + 3, -1, slab(Blocks.SMOOTH_STONE_SLAB, false));
					p.set(x1, y0 + 3, -1, slab(Blocks.SMOOTH_STONE_SLAB, false));
				} else if (r.nextInt(3) == 0) {
					// Washing on a line.
					p.set(x0, y0 + 2, -1, StyleKit.wool(r.nextBoolean() ? DyeColor.WHITE : DyeColor.LIGHT_BLUE));
				}
				if (r.nextInt(4) == 0) {
					p.set(x1 + 1, y0 + 2, -1, StyleKit.shutter(p, Blocks.IRON_TRAPDOOR, L.FRONT));
				}
			}
		}
		// Entrances: a concrete canopy, a steel door, a bench and the sign with the flat numbers.
		for (int e : entrances) {
			p.door(e, 1, 0, Blocks.IRON_DOOR, L.BACK);
			p.set(e + 1, 1, 0, b(Blocks.SMOOTH_STONE));
			p.set(e + 1, 2, 0, b(Blocks.SMOOTH_STONE));
			StyleKit.canopy(p, e - 1, e + 2, 3, slab(Blocks.SMOOTH_STONE_SLAB, true), true);
			p.set(e - 2, 1, -2, p.stairs(Blocks.SPRUCE_STAIRS, L.BACK, false));
			p.set(e + 3, 1, -2, p.stairs(Blocks.SPRUCE_STAIRS, L.BACK, false));
			p.set(e + 1, 2, -1, StyleKit.shutter(p, Blocks.SPRUCE_TRAPDOOR, L.FRONT));
			p.ladder(e, 1, top - 1, d - 1, L.BACK);
			p.set(e, top, d - 1, b(Blocks.IRON_TRAPDOOR));
		}
		StyleKit.flats(p, r, hw, d, floors, 3, 6, wall, 0);
		// Flat roof, a parapet, the aerials everyone put up.
		StyleKit.flatRoof(p, hw, d, top, concrete(DyeColor.GRAY), seam, null);
		aerials(p, r, hw, d, top + 1, 5);
		p.top = top + 5;
	}

	/** TV aerials and a dish or two on a roof. */
	private static void aerials(Plan p, Random r, int hw, int d, int y, int n) {
		for (int i = 0; i < n; i++) {
			int x = -hw + 2 + r.nextInt(Math.max(1, 2 * hw - 3));
			int z = 2 + r.nextInt(Math.max(1, d - 3));
			if (p.has(x, y, z)) {
				continue;
			}
			p.set(x, y, z, b(Blocks.IRON_BARS));
			p.set(x, y + 1, z, b(Blocks.IRON_BARS));
			p.set(x, y + 2, z, b(Blocks.IRON_CHAIN));
			if (r.nextBoolean()) {
				p.set(x, y + 3, z, b(Blocks.IRON_BARS));
			}
		}
		p.set(hw - 2, y, 2, b(Blocks.DAYLIGHT_DETECTOR));
	}

	// ------------------------------------------------------------------------------------------------
	// Nine storeys of grey panels

	private static void panelBlock(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 32);
		BlockState wall = Math.floorMod(variant, 3) == 0 ? concrete(DyeColor.WHITE) : b(Blocks.SMOOTH_STONE);
		BlockState seam = b(Blocks.STONE);
		StyleKit.base(p, hw, d, seam, b(Blocks.SMOOTH_STONE));
		int top = StyleKit.shell(p, hw, d, floors, 3, b(Blocks.POLISHED_ANDESITE), wall, seam, b(Blocks.SMOOTH_STONE));
		for (int x = -hw; x <= hw; x += 3) {
			for (int y = 4; y < top; y++) {
				p.set(x, y, 0, seam);
				p.set(x, y, d, seam);
			}
		}
		List<Integer> entrances = Architecture.panelEntrances(hw);
		// A coloured stripe up each stairwell (the one bit of colour the plan allowed).
		DyeColor stripe = StyleKit.pick(variant, DyeColor.BLUE, DyeColor.RED, DyeColor.CYAN, DyeColor.ORANGE);
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			StyleKit.windowsAlong(p, hw, d, y0, 2, 3, 1, 2, b(Blocks.GLASS_PANE));
			for (int x = -hw + 1; x <= hw - 1; x++) {
				int o = Math.floorMod(x + hw, 3);
				boolean entrance = false;
				for (int e : entrances) {
					entrance |= x == e || x == e + 1;
				}
				if (entrance) {
					if (f > 0) {
						p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
						p.set(x, y0 + 1, 0, concrete(stripe));
					}
					continue;
				}
				if (o == 1 || o == 2) {
					p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
					if (f > 0) {
						p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
					}
				}
			}
			// A column of balconies at each end of the block.
			if (f > 0) {
				for (int side = -1; side <= 1; side += 2) {
					int x0 = side < 0 ? -hw + 1 : hw - 2;
					p.set(x0, y0, -1, b(Blocks.SMOOTH_STONE));
					p.set(x0 + 1, y0, -1, b(Blocks.SMOOTH_STONE));
					BlockState rail = r.nextInt(3) == 0 ? b(Blocks.IRON_BARS) : wall;
					p.set(x0, y0 + 1, -1, rail);
					p.set(x0 + 1, y0 + 1, -1, rail);
					if (r.nextBoolean()) {
						p.set(x0, y0 + 2, -1, b(Blocks.GLASS_PANE));
						p.set(x0 + 1, y0 + 2, -1, b(Blocks.GLASS_PANE));
					}
				}
			}
		}
		for (int e : entrances) {
			p.door(e, 1, 0, Blocks.IRON_DOOR, L.BACK);
			p.door(e + 1, 1, 0, Blocks.IRON_DOOR, L.BACK);
			StyleKit.canopy(p, e - 1, e + 2, 3, slab(Blocks.SMOOTH_STONE_SLAB, true), true);
			p.ladder(e, 1, top - 1, d - 1, L.BACK);
			p.set(e, top, d - 1, b(Blocks.IRON_TRAPDOOR));
			// The lift's machine room on the roof.
			p.fill(e - 1, top + 1, d - 4, e + 2, top + 3, d - 1, wall);
		}
		StyleKit.flats(p, r, hw, d, floors, 3, 6, wall, 0);
		StyleKit.flatRoof(p, hw, d, top, concrete(DyeColor.GRAY), seam, null);
		aerials(p, r, hw, d, top + 1, 7);
		p.top = top + 5;
	}

	// ------------------------------------------------------------------------------------------------
	// A Stalin-era house: warm plaster, a heavy cornice, tall windows with white surrounds, a shop below

	private static void stalinka(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 33);
		BlockState wall = terra(StyleKit.pick(variant, DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.PINK));
		BlockState white = b(Blocks.SMOOTH_QUARTZ);
		StyleKit.base(p, hw, d, b(Blocks.POLISHED_GRANITE), b(Blocks.SMOOTH_STONE));
		int h = 4;
		int top = StyleKit.shell(p, hw, d, floors, h, b(Blocks.POLISHED_GRANITE), wall, white, b(Blocks.SMOOTH_STONE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * h;
			for (int x = -hw + 2; x <= hw - 2; x += 2) {
				if (f == 0 && Math.abs(x) <= 1) {
					continue;
				}
				p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 3, -1, f == 0 ? slab(Blocks.SMOOTH_QUARTZ_SLAB, true) : p.stairs(Blocks.QUARTZ_STAIRS, L.BACK, true));
				p.set(x, y0 + 1, d, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, d, b(Blocks.GLASS_PANE));
			}
			StyleKit.windowsSides(p, hw, d, y0, 2, 3, b(Blocks.GLASS_PANE));
			// A small balcony with a stone balustrade in the middle of each upper floor.
			if (f >= 2) {
				for (int x = -1; x <= 1; x++) {
					p.set(x, y0, -1, white);
					p.set(x, y0 + 1, -2, b(Blocks.QUARTZ_PILLAR));
				}
				p.set(-1, y0 + 1, -1, b(Blocks.QUARTZ_PILLAR));
				p.set(1, y0 + 1, -1, b(Blocks.QUARTZ_PILLAR));
				p.set(0, y0 + 1, 0, AIR);
				p.set(0, y0 + 2, 0, AIR);
				p.door(0, y0 + 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
			}
		}
		// Corner rustication.
		for (int y = 1; y < top; y++) {
			if (y % 2 == 0) {
				p.set(-hw, y, 0, white);
				p.set(hw, y, 0, white);
			}
		}
		// The ground floor: a shop with big windows and a sign, the entrance in the middle.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, 0, b(Blocks.GLASS));
				p.set(x, 2, 0, b(Blocks.GLASS));
			}
		}
		StyleKit.sign(p, -hw + 2, -2, 3, DyeColor.RED);
		StyleKit.sign(p, 2, hw - 2, 3, DyeColor.BLUE);
		p.door(0, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		// The cornice and a pitched metal roof behind a balustrade.
		for (int x = -hw - 1; x <= hw + 1; x++) {
			p.set(x, top, -1, p.stairs(Blocks.QUARTZ_STAIRS, L.BACK, true));
			p.set(x, top + 1, -1, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		}
		p.roofAlongX(top + 1, Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), Blocks.CUT_COPPER.waxed().oxidized(), wall.getBlock());
		p.ladder(hw - 1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.SHOP, -hw + 1, hw - 2, 0, 1, d - 1, h);
		StyleKit.flats(p, r, hw, d, floors, h, 6, wall, 1);
		p.top = top + d / 2 + 3;
	}

	// ------------------------------------------------------------------------------------------------
	// A sixteen-storey tower: white panels, loggias, a mosaic of a rocket on its side

	private static void tower(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 34);
		int floors = 16;
		BlockState wall = concrete(DyeColor.WHITE);
		BlockState seam = concrete(DyeColor.LIGHT_GRAY);
		StyleKit.base(p, hw, d, seam, b(Blocks.SMOOTH_STONE));
		int top = StyleKit.shell(p, hw, d, floors, 3, b(Blocks.POLISHED_ANDESITE), wall, seam, b(Blocks.SMOOTH_STONE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			for (int z = 0; z <= d; z += d) {
				for (int x = -hw + 1; x <= hw - 1; x++) {
					int o = Math.floorMod(x + hw, 4);
					if (o == 1 || o == 2) {
						p.set(x, y0 + 2, z, b(Blocks.GLASS_PANE));
						if (f > 0) {
							p.set(x, y0 + 1, z, b(Blocks.GLASS_PANE));
						}
					}
				}
			}
			for (int z = 2; z < d - 1; z += 4) {
				p.set(hw, y0 + 2, z, b(Blocks.GLASS_PANE));
				p.set(hw, y0 + 2, z + 1, b(Blocks.GLASS_PANE));
			}
			if (f > 0 && f % 2 == 1) {
				// Loggias at the corners, their fronts in the stripe colour.
				for (int x : new int[]{-hw + 1, hw - 2}) {
					p.set(x, y0 + 1, -1, concrete(DyeColor.BLUE));
					p.set(x + 1, y0 + 1, -1, concrete(DyeColor.BLUE));
					p.set(x, y0, -1, seam);
					p.set(x + 1, y0, -1, seam);
				}
			}
		}
		// The mosaic on the left wall: a rocket climbing over a blue sky with a star.
		for (int y = 6; y < top - 3; y++) {
			for (int z = 1; z < d; z++) {
				double v = (y - 6.0) / (top - 9.0);
				DyeColor c = v > 0.85 && Math.abs(z - d / 2.0) < 1.5 ? DyeColor.RED
						: Math.abs(z - d / 2.0) < 1.0 && v > 0.25 ? DyeColor.WHITE
						: Math.abs(z - d / 2.0) < 2.0 && v < 0.3 && v > 0.15 ? DyeColor.ORANGE
						: v > 0.6 ? DyeColor.BLUE : v > 0.3 ? DyeColor.LIGHT_BLUE : DyeColor.CYAN;
				p.set(-hw, y, z, terra(c));
			}
		}
		p.door(-1, 1, 0, Blocks.IRON_DOOR, L.BACK);
		p.door(0, 1, 0, Blocks.IRON_DOOR, L.BACK);
		StyleKit.canopy(p, -2, 1, 3, slab(Blocks.SMOOTH_STONE_SLAB, true), true);
		p.ladder(hw - 2, 1, top - 1, d - 1, L.BACK);
		p.set(hw - 2, top, d - 1, b(Blocks.IRON_TRAPDOOR));
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 0) {
				Interiors.floor(p, r, -hw + 1, hw - 1, y0, 1, d - 1);
			}
			Interiors.wallX(p, 0, y0, 1, d - 1, d / 2, wall);
			Interiors.room(p, r, f == 0 ? Interiors.LOBBY : Interiors.flatRoom(r), -hw + 1, -1, y0, 1, d - 1);
			Interiors.room(p, r, f == 0 ? Interiors.LOBBY : Interiors.flatRoom(r), 1, hw - 3, y0, 1, d - 1);
		}
		StyleKit.flatRoof(p, hw, d, top, concrete(DyeColor.GRAY), seam, null);
		p.fill(-2, top + 1, d - 5, 2, top + 3, d - 1, wall);
		// The letters' frame on the roof (a slogan board) with red lights.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			p.set(x, top + 2, 1, b(Blocks.IRON_BARS));
			p.set(x, top + 3, 1, Math.floorMod(x, 2) == 0 ? glass(DyeColor.RED) : b(Blocks.IRON_BARS));
		}
		p.set(0, top + 4, 1, b(Blocks.REDSTONE_LAMP));
		aerials(p, r, hw, d, top + 1, 3);
		p.top = top + 6;
	}

	// ------------------------------------------------------------------------------------------------
	// A research institute: ribbon windows, a glass stair tower, the name in letters on the roof

	private static void institute(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 35);
		int floors = 7;
		BlockState wall = concrete(StyleKit.pick(variant, DyeColor.WHITE, DyeColor.LIGHT_GRAY));
		StyleKit.base(p, hw, d, concrete(DyeColor.GRAY), b(Blocks.SMOOTH_STONE));
		int top = StyleKit.shell(p, hw, d, floors, 3, b(Blocks.POLISHED_ANDESITE), wall, wall, b(Blocks.SMOOTH_STONE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			for (int x = -hw + 1; x <= hw - 1; x++) {
				p.set(x, y0 + 2, 0, glass(DyeColor.LIGHT_BLUE));
				p.set(x, y0 + 2, d, glass(DyeColor.LIGHT_BLUE));
			}
			for (int z = 1; z < d; z++) {
				p.set(-hw, y0 + 2, z, glass(DyeColor.LIGHT_BLUE));
				p.set(hw, y0 + 2, z, glass(DyeColor.LIGHT_BLUE));
			}
			if (f > 0) {
				Interiors.floor(p, r, -hw + 1, hw - 1, y0, 1, d - 1);
			}
			for (int x0 = -hw + 1; x0 < hw; x0 += 6) {
				Interiors.room(p, r, f == 0 ? Interiors.LOBBY : r.nextInt(3) == 0 ? Interiors.MEETING : Interiors.OFFICE, x0,
						Math.min(x0 + 4, hw - 1), y0, 1, d - 1);
			}
		}
		// The glass stair tower in front of the entrance.
		for (int y = 1; y < top + 2; y++) {
			for (int x = -2; x <= 2; x++) {
				p.set(x, y, -1, Math.abs(x) == 2 ? wall : glass(DyeColor.LIGHT_BLUE));
				p.set(x, y, -2, Math.abs(x) == 2 ? wall : glass(DyeColor.LIGHT_BLUE));
			}
		}
		p.set(-1, 1, -2, AIR);
		p.set(-1, 2, -2, AIR);
		p.door(-1, 1, -2, Blocks.IRON_DOOR, L.BACK);
		p.set(0, 1, -1, AIR);
		p.set(0, 2, -1, AIR);
		p.set(0, 1, 0, AIR);
		p.set(0, 2, 0, AIR);
		p.ladder(1, 1, top - 1, 1, L.FRONT);
		StyleKit.flatRoof(p, hw, d, top, concrete(DyeColor.GRAY), wall, null);
		// The letters on the roof: a lit red board on a frame.
		for (int x = -hw + 2; x <= hw - 2; x++) {
			p.set(x, top + 1, d / 2, b(Blocks.IRON_BARS));
			p.set(x, top + 2, d / 2, Math.floorMod(x, 3) == 2 ? b(Blocks.IRON_BARS) : glass(DyeColor.RED));
			p.set(x, top + 2, d / 2 + 1, COLD);
		}
		p.top = top + 4;
	}

	// ------------------------------------------------------------------------------------------------
	// A grocery pavilion: a glass box on a concrete slab, "PRODUKTY" in red letters

	private static void pavilion(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 36);
		BlockState frame = concrete(DyeColor.WHITE);
		StyleKit.base(p, hw, d, concrete(DyeColor.GRAY), b(Blocks.POLISHED_ANDESITE));
		for (int y = 1; y <= 4; y++) {
			p.ring(-hw, 0, hw, d, y, y == 4 ? frame : glass(DyeColor.LIGHT_BLUE));
		}
		for (int x = -hw; x <= hw; x += 4) {
			for (int y = 1; y <= 3; y++) {
				p.set(x, y, 0, frame);
				p.set(x, y, d, frame);
			}
		}
		p.fill(-hw - 1, 5, -1, hw + 1, 5, d + 1, frame);
		DyeColor letters = StyleKit.pick(variant, DyeColor.RED, DyeColor.BLUE, DyeColor.ORANGE);
		StyleKit.sign(p, -hw + 2, hw - 2, 4, letters);
		p.door(-1, 1, 0, Blocks.IRON_DOOR, L.BACK);
		p.door(0, 1, 0, Blocks.IRON_DOOR, L.BACK);
		Interiors.room(p, r, Interiors.SHOP, -hw + 1, hw - 1, 0, 1, d - 1, 4);
		p.top = 7;
	}

	// ------------------------------------------------------------------------------------------------
	// A dacha: logs or planks, white carved window frames, a tin roof, a porch, a little glasshouse

	private static void dacha(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 37);
		Block[] woods = {Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.BIRCH_PLANKS};
		BlockState wall = b(woods[Math.floorMod(variant, woods.length)]);
		BlockState logs = b(Math.floorMod(variant, 2) == 0 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG);
		Block[][] roofs = {{Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), Blocks.CUT_COPPER.waxed().oxidized()},
				{Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE}, {Blocks.CUT_COPPER_STAIRS.waxed().weathered(), Blocks.CUT_COPPER.waxed().weathered()},
				{Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICKS}};
		Block[] roof = roofs[Math.floorMod(variant / 2, roofs.length)];
		StyleKit.base(p, hw, d, b(Blocks.COBBLESTONE), b(Blocks.SPRUCE_PLANKS));
		int top = floors * 3;
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SPRUCE_PLANKS));
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, wall);
				p.set(-hw, y, 0, logs);
				p.set(hw, y, 0, logs);
				p.set(-hw, y, d, logs);
				p.set(hw, y, d, logs);
			}
			// Windows in white carved frames (birch trapdoors either side, a little lintel).
			for (int x : new int[]{-hw + 2, hw - 2}) {
				p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x - 1, y0 + 2, -1, StyleKit.shutter(p, Blocks.BIRCH_TRAPDOOR, L.FRONT));
				p.set(x + 1, y0 + 2, -1, StyleKit.shutter(p, Blocks.BIRCH_TRAPDOOR, L.FRONT));
				p.set(x, y0 + 3, -1, slab(Blocks.BIRCH_SLAB, false));
				p.set(x, y0 + 1, -1, StyleKit.ledge(p, Blocks.BIRCH_TRAPDOOR, L.FRONT));
			}
			for (int z = 2; z < d; z += 3) {
				p.set(-hw, y0 + 2, z, b(Blocks.GLASS_PANE));
				p.set(hw, y0 + 2, z, b(Blocks.GLASS_PANE));
			}
		}
		p.fill(-hw, top, 0, hw, top, d, wall);
		p.roofAlongZ(top + 1, roof[0], roof[1], wall.getBlock(), true, false);
		// The porch: steps, posts, a little roof.
		p.door(0, 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
		p.set(-1, 0, -1, b(Blocks.SPRUCE_PLANKS));
		p.set(0, 0, -1, b(Blocks.SPRUCE_PLANKS));
		p.set(1, 0, -1, b(Blocks.SPRUCE_PLANKS));
		p.set(0, 0, -2, p.stairs(Blocks.SPRUCE_STAIRS, L.BACK, false));
		p.set(-1, 1, -1, b(Blocks.SPRUCE_FENCE));
		p.set(1, 1, -1, b(Blocks.SPRUCE_FENCE));
		p.set(-1, 2, -1, b(Blocks.SPRUCE_FENCE));
		p.set(1, 2, -1, b(Blocks.SPRUCE_FENCE));
		for (int x = -1; x <= 1; x++) {
			p.set(x, 3, -1, slab(roof[1] == Blocks.POLISHED_ANDESITE ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.SPRUCE_SLAB, false));
		}
		p.set(0, 2, -1, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, true));
		// The stove's chimney.
		for (int y = top + 1; y <= top + hw + 2; y++) {
			p.set(hw - 2, y, d - 2, b(Blocks.BRICKS));
		}
		p.set(hw - 2, top + hw + 3, d - 2, b(Blocks.CAMPFIRE));
		// Inside: a stove, a table, the beds.
		p.ladder(-hw + 1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.KITCHEN, -hw + 2, hw - 1, 0, 1, d - 1);
		for (int f = 1; f < floors; f++) {
			Interiors.room(p, r, Interiors.BEDROOM, -hw + 2, hw - 1, f * 3, 1, d - 1);
		}
		if (floors == 1) {
			p.bed(hw - 1, 1, d - 2, L.BACK, DyeColor.RED);
			p.bed(hw - 2, 1, d - 2, L.BACK, DyeColor.RED);
		}
		p.top = Math.max(p.top, top + hw + 4);
	}

	// ------------------------------------------------------------------------------------------------
	// The city hall: Stalin-era classicism - columns, a pediment, a tower with a spire and a red star

	private static void hall(Plan p, DyeColor flag) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 38);
		BlockState wall = terra(DyeColor.YELLOW);
		BlockState white = b(Blocks.SMOOTH_QUARTZ);
		StyleKit.base(p, hw, d, b(Blocks.POLISHED_GRANITE), b(Blocks.POLISHED_DIORITE));
		int h = 4;
		int floors = 3;
		int top = StyleKit.shell(p, hw, d, floors, h, b(Blocks.POLISHED_GRANITE), wall, white, b(Blocks.SMOOTH_STONE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * h;
			for (int x = -hw + 2; x <= hw - 2; x += 2) {
				p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 1, d, b(Blocks.GLASS_PANE));
				p.set(x, y0 + 2, d, b(Blocks.GLASS_PANE));
			}
			StyleKit.windowsSides(p, hw, d, y0, 2, 2, b(Blocks.GLASS_PANE));
			if (f > 0) {
				Interiors.floor(p, r, -hw + 1, hw - 1, y0, 1, d - 1);
			}
			Interiors.room(p, r, f == 0 ? Interiors.LOBBY : Interiors.MEETING, -hw + 1, -1, y0, 1, d - 1, h);
			Interiors.room(p, r, Interiors.OFFICE, 1, hw - 1, y0, 1, d - 1, h);
		}
		// The portico: six columns up two floors, steps, a pediment.
		for (int x = -5; x <= 5; x += 2) {
			for (int y = 1; y <= 2 * h; y++) {
				p.set(x, y, -2, b(Blocks.QUARTZ_PILLAR));
			}
		}
		for (int x = -6; x <= 6; x++) {
			p.set(x, 0, -1, white);
			p.set(x, 0, -2, white);
			p.set(x, 0, -3, p.stairs(Blocks.QUARTZ_STAIRS, L.BACK, false));
			p.set(x, 2 * h + 1, -2, white);
			p.set(x, 2 * h + 1, -1, white);
			int k = 6 - Math.abs(x);
			for (int y = 2 * h + 2; y <= 2 * h + 1 + k / 2; y++) {
				p.set(x, y, -2, white);
			}
		}
		p.door(-1, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		p.door(0, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		p.door(1, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		// The cornice and the roof.
		for (int x = -hw - 1; x <= hw + 1; x++) {
			p.set(x, top, -1, p.stairs(Blocks.QUARTZ_STAIRS, L.BACK, true));
			p.set(x, top, d + 1, p.stairs(Blocks.QUARTZ_STAIRS, L.FRONT, true));
		}
		StyleKit.flatRoof(p, hw, d, top, b(Blocks.SMOOTH_STONE), white, null);
		// The tower in the middle: a square base, a lantern with a clock, the spire, the star.
		int cz = d / 2;
		for (int y = top + 1; y <= top + 6; y++) {
			p.ring(-3, cz - 3, 3, cz + 3, y, y == top + 6 ? white : wall);
		}
		p.set(0, top + 4, cz - 3, b(Blocks.TARGET));
		for (int y = top + 7; y <= top + 10; y++) {
			p.ring(-2, cz - 2, 2, cz + 2, y, y == top + 10 ? white : b(Blocks.QUARTZ_PILLAR));
			p.set(0, y, cz - 2, y == top + 10 ? white : AIR);
		}
		p.set(0, top + 8, cz, WARM);
		for (int y = top + 11; y <= top + 14; y++) {
			int s = y <= top + 12 ? 1 : 0;
			p.fill(-s, y, cz - s, s, y, cz + s, b(Blocks.GOLD_BLOCK));
		}
		p.set(0, top + 15, cz, b(Blocks.IRON_BARS));
		p.set(0, top + 16, cz, b(Blocks.REDSTONE_BLOCK));
		p.set(-1, top + 16, cz, glass(DyeColor.RED));
		p.set(1, top + 16, cz, glass(DyeColor.RED));
		p.set(0, top + 17, cz, glass(DyeColor.RED));
		// The flag.
		p.set(hw - 1, top + 2, 1, b(Blocks.IRON_BARS));
		p.set(hw - 1, top + 3, 1, b(Blocks.IRON_BARS));
		p.set(hw - 1, top + 4, 1, Blocks.BANNER.pick(flag).defaultBlockState());
		p.ladder(hw - 1, 1, top - 1, d - 1, L.BACK);
		p.top = top + 18;
	}
}
