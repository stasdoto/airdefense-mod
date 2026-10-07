package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * The city's main buildings, drawn with depth: panel blocks renovated in bright colours (loggias, murals on the gable
 * walls, shops with glowing signs on the ground floor), residential towers with zigzag balconies and an LED crown,
 * glass business centres with a colonnade and a roof garden, modern villas (three styles), six kinds of shops, a brick
 * school, a city hall with a dome and a clock, a hospital with a helipad. Every one is furnished inside and lit, so
 * the city glows at night. Local coordinates as in {@link Blueprints}: x right of the door, z into the building, y up.
 */
final class Architecture {
	private Architecture() {
	}

	// ------------------------------------------------------------------------------------------------
	// Materials and small pieces

	static BlockState c(DyeColor d) {
		return Blocks.CONCRETE.pick(d).defaultBlockState();
	}

	static BlockState glass(DyeColor d) {
		return Blocks.STAINED_GLASS.pick(d).defaultBlockState();
	}

	static BlockState pane(DyeColor d) {
		return Blocks.STAINED_GLASS_PANE.pick(d).defaultBlockState();
	}

	static BlockState terra(DyeColor d) {
		return Blocks.DYED_TERRACOTTA.pick(d).defaultBlockState();
	}

	static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	static BlockState slabTop(Block slab) {
		return slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final BlockState WARM = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
	static final BlockState COLD = Blocks.SEA_LANTERN.defaultBlockState();

	/** A glowing sign: coloured glass with a light right behind it. */
	static void lightbox(Plan p, int x0, int x1, int y, int z, DyeColor color, boolean behindIsInside) {
		for (int x = x0; x <= x1; x++) {
			p.set(x, y, z, glass(color));
			p.set(x, y, z + (behindIsInside ? 1 : -1), COLD);
		}
	}

	/** A box on the wall: a trapdoor flat against it (air conditioner, shutter), facing out. */
	static BlockState onWall(Plan p, Block trapdoor, L out) {
		return trapdoor.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.w(out))
				.setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HALF, Half.TOP);
	}

	static BlockState button(Plan p, Block button, L out) {
		return button.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, p.w(out));
	}

	static BlockState hanging() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	/** Roof clutter: air conditioners, vents, antennas, a satellite dish. */
	static void roofKit(Plan p, Random r, int x0, int x1, int z0, int z1, int y, int count) {
		for (int i = 0; i < count; i++) {
			int x = x0 + r.nextInt(Math.max(1, x1 - x0 + 1));
			int z = z0 + r.nextInt(Math.max(1, z1 - z0 + 1));
			if (p.has(x, y, z)) {
				continue;
			}
			switch (r.nextInt(4)) {
				case 0 -> {
					p.set(x, y, z, b(Blocks.SMOOTH_STONE));
					p.set(x, y + 1, z, b(Blocks.IRON_TRAPDOOR));
				}
				case 1 -> {
					p.set(x, y, z, b(Blocks.IRON_BARS));
					p.set(x, y + 1, z, b(Blocks.IRON_BARS));
					p.set(x, y + 2, z, b(Blocks.IRON_BARS));
				}
				case 2 -> {
					p.set(x, y, z, b(Blocks.IRON_BARS));
					p.set(x, y + 1, z, b(Blocks.DAYLIGHT_DETECTOR));
				}
				default -> p.set(x, y, z, b(Blocks.CAULDRON));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Panel blocks: renovated, colourful

	private static final DyeColor[][] RENOVATION = {
			{DyeColor.ORANGE, DyeColor.YELLOW}, {DyeColor.LIGHT_BLUE, DyeColor.CYAN}, {DyeColor.LIME, DyeColor.GREEN},
			{DyeColor.PINK, DyeColor.MAGENTA}, {DyeColor.YELLOW, DyeColor.ORANGE}, {DyeColor.BLUE, DyeColor.LIGHT_BLUE},
			{DyeColor.RED, DyeColor.ORANGE}, {DyeColor.CYAN, DyeColor.LIGHT_BLUE},
	};
	private static final DyeColor[] SIGNS = {DyeColor.RED, DyeColor.LIME, DyeColor.ORANGE, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.YELLOW,
			DyeColor.CYAN, DyeColor.MAGENTA};

	/** Where a panel block's entrances are (the left door of each pair): the window bays at 1/3 and 2/3 of the front. */
	static List<Integer> panelEntrances(int hw) {
		List<Integer> entrances = new ArrayList<>();
		int bays = (2 * hw) / 6;
		if (bays >= 4) {
			entrances.add(-hw + 6 + 1);
			entrances.add(-hw + 6 * (bays - 2) + 1);
		} else {
			entrances.add(-hw + 6 * (bays / 2) + 1);
		}
		return entrances;
	}

	static void panel(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 1);
		DyeColor[] acc = RENOVATION[Math.floorMod(variant, RENOVATION.length)];
		BlockState wall = c(Math.floorMod(variant, 4) == 3 ? DyeColor.LIGHT_GRAY : DyeColor.WHITE);
		BlockState band = c(DyeColor.GRAY);
		BlockState plinth = b(Blocks.POLISHED_ANDESITE);
		BlockState win = b(Blocks.GLASS_PANE);
		boolean shops = floors >= 5 && Math.floorMod(variant, 2) == 0;
		boolean glazed = Math.floorMod(variant, 3) == 1;
		int top = floors * 3;
		List<Integer> entrances = panelEntrances(hw);
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, b(Blocks.SMOOTH_STONE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SMOOTH_STONE));
				p.ring(-hw, 0, hw, d, y0, band);
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, f == 0 ? plinth : wall);
			}
		}
		p.fill(-hw, top, 0, hw, top, d, c(DyeColor.GRAY));
		p.ring(-hw, 0, hw, d, top, band);
		p.ring(-hw, 0, hw, d, top + 1, wall);
		// Front and back: windows, loggias with coloured parapets.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			int o = Math.floorMod(x + hw, 6);
			int bay = (x + hw) / 6;
			boolean entrance = false;
			for (int e : entrances) {
				if (x == e || x == e + 1) {
					entrance = true;
				}
			}
			for (int f = 0; f < floors; f++) {
				int y0 = f * 3;
				boolean window = o == 1 || o == 2 || o == 4 || o == 5;
				// Back.
				if (window) {
					p.set(x, y0 + 1, d, win);
					p.set(x, y0 + 2, d, win);
				} else if (f > 0 && r.nextInt(6) == 0) {
					p.set(x, y0 + 2, d + 1, onWall(p, Blocks.IRON_TRAPDOOR, L.BACK));
				}
				// Front.
				if (entrance) {
					if (f > 0) {
						p.set(x, y0 + 2, 0, glass(DyeColor.WHITE));
					}
					continue;
				}
				if (f == 0) {
					if (shops && window) {
						p.set(x, 1, 0, b(Blocks.GLASS));
						p.set(x, 2, 0, b(Blocks.GLASS));
					} else if (window) {
						p.set(x, 2, 0, win);
					}
					continue;
				}
				if (o == 1 || o == 2) {
					p.set(x, y0 + 1, 0, win);
					p.set(x, y0 + 2, 0, win);
				} else if (o == 4 || o == 5) {
					p.set(x, y0, -1, band);
					p.set(x, y0 + 1, -1, c((f + bay) % 2 == 0 ? acc[0] : acc[1]));
					if (glazed) {
						p.set(x, y0 + 2, -1, pane(DyeColor.WHITE));
					} else if (r.nextInt(5) == 0) {
						p.set(x, y0 + 2, -1, Interiors.plant(r));
					}
					p.set(x, y0 + 1, 0, win);
					p.set(x, y0 + 2, 0, win);
				} else {
					// The piers between: the loggias' side walls stand out from the facade.
					for (int y = y0; y <= y0 + 2; y++) {
						p.set(x, y, -1, wall);
					}
				}
			}
			p.set(x, top, -1, band);
			p.set(x, top + 1, -1, slabTop(Blocks.SMOOTH_STONE_SLAB));
		}
		// Gable walls: small windows, or a mural.
		int mural = Math.floorMod(variant, 3) == 0 && floors >= 5 ? (variant / 3) % 4 : -1;
		for (int z = 1; z < d; z++) {
			for (int f = 1; f < floors; f++) {
				int y0 = f * 3;
				if (Math.floorMod(z, 4) == 2) {
					p.set(hw, y0 + 1, z, win);
					p.set(hw, y0 + 2, z, win);
					if (mural < 0) {
						p.set(-hw, y0 + 1, z, win);
						p.set(-hw, y0 + 2, z, win);
					}
				}
			}
		}
		if (mural >= 0) {
			mural(p, -hw, d, 4, top - 1, mural);
		}
		// Shops on the ground floor: a glowing sign band along the front, each shop its own colour.
		int shopIndex = 0;
		DyeColor sign = SIGNS[r.nextInt(SIGNS.length)];
		for (int x = -hw + 1; x <= hw - 1 && shops; x++) {
			boolean entrance = false;
			for (int e : entrances) {
				if (x >= e - 1 && x <= e + 2) {
					entrance = true;
				}
			}
			if (entrance) {
				sign = SIGNS[(++shopIndex + r.nextInt(SIGNS.length)) % SIGNS.length];
				continue;
			}
			p.set(x, 3, -1, glass(sign));
			p.set(x, 3, 0, COLD);
		}
		// Entrances: double door, canopy, lamps, the intercom; a ladder up the stairwell, the lift room on the roof.
		for (int e : entrances) {
			p.door(e, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
			p.door(e + 1, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
			for (int x = e - 1; x <= e + 2; x++) {
				p.set(x, 3, -1, slabTop(Blocks.SMOOTH_STONE_SLAB));
				p.set(x, 3, -2, slabTop(Blocks.SMOOTH_STONE_SLAB));
				p.set(x, 0, -1, b(Blocks.POLISHED_ANDESITE));
			}
			p.set(e - 1, 2, -1, hanging());
			p.set(e + 2, 2, -1, hanging());
			p.set(e - 1, 1, -1, button(p, Blocks.STONE_BUTTON, L.FRONT));
			p.ladder(e, 1, top - 1, d - 1, L.BACK);
			p.set(e, top, d - 1, b(Blocks.IRON_TRAPDOOR));
			p.fill(e - 1, top + 1, 1, e + 2, top + 3, 3, wall);
			p.set(e, top + 2, 0, glass(DyeColor.WHITE));
			p.set(e + 1, top + 2, 0, glass(DyeColor.WHITE));
		}
		// Inside: flats on every floor (shops on the ground floor), a hall at each stairwell.
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			for (int x = -hw + 6; x < hw; x += 6) {
				Interiors.wallX(p, x, y0, 1, d - 1, d / 2, wall);
			}
			for (int x0 = -hw + 1; x0 < hw; x0 += 6) {
				int x1 = Math.min(x0 + 4, hw - 1);
				boolean hall = false;
				for (int e : entrances) {
					if (e >= x0 - 1 && e <= x1 + 1) {
						hall = true;
					}
				}
				if (hall) {
					Interiors.ceilingLight(p, r, (x0 + x1) / 2, y0, d / 2, false, 1f);
					Interiors.put(p, x1, y0 + 1, 1, Interiors.plant(r));
					continue;
				}
				if (f > 0) {
					Interiors.floor(p, r, x0, x1, y0, 1, d - 1);
				}
				int kind = f == 0 ? (shops ? (r.nextInt(3) == 0 ? Interiors.CAFE : Interiors.SHOP) : Interiors.BEDROOM) : Interiors.flatRoom(r);
				Interiors.room(p, r, kind, x0, x1, y0, 1, d - 1);
			}
		}
		roofKit(p, r, -hw + 1, hw - 1, 4, d - 1, top + 1, 4 + hw / 3);
		p.top = top + 4;
	}

	/** A painting on a gable wall (the plane x = {@code x}), from y0 to y1 and across the whole depth. */
	static void mural(Plan p, int x, int d, int y0, int y1, int kind) {
		int h = y1 - y0 + 1;
		double cz = d / 2.0;
		double cy = y0 + h * 0.6;
		double R = Math.min(d / 2.0, h / 2.5);
		for (int y = y0; y <= y1; y++) {
			for (int z = 0; z <= d; z++) {
				double v = (double) (y - y0) / h;
				DyeColor col;
				switch (kind) {
					case 0 -> {
						// A sunflower in the blue sky over green.
						double dz = z - cz;
						double dy = y - cy;
						double dist = Math.sqrt(dz * dz + dy * dy);
						double ang = Math.atan2(dy, dz);
						if (dist < R * 0.4) {
							col = DyeColor.BROWN;
						} else if (dist < R * (0.75 + 0.25 * Math.cos(ang * 8))) {
							col = DyeColor.YELLOW;
						} else if (y < cy - R * 0.4 && Math.abs(dz) < 0.6) {
							col = DyeColor.GREEN;
						} else {
							col = v < 0.2 ? DyeColor.LIME : DyeColor.LIGHT_BLUE;
						}
					}
					case 1 -> col = v > 0.5 ? DyeColor.BLUE : DyeColor.YELLOW;
					case 2 -> col = new DyeColor[]{DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.CYAN, DyeColor.BLUE,
							DyeColor.PURPLE}[Math.floorMod((y - y0) + z, 7)];
					default -> {
						// Mountains under a setting sun.
						double sun = Math.hypot(z - cz * 1.3, y - (y0 + h * 0.72));
						double ridge = 0.35 + 0.12 * Math.sin(z * 0.9) + 0.06 * Math.sin(z * 2.3);
						col = v < ridge ? (v < ridge - 0.12 ? DyeColor.GREEN : DyeColor.LIME) : sun < R * 0.45 ? DyeColor.YELLOW
								: v > 0.75 ? DyeColor.PURPLE : v > 0.6 ? DyeColor.MAGENTA : DyeColor.ORANGE;
					}
				}
				p.set(x, y, z, c(col));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Residential tower: zigzag balconies, an LED stripe up the front, a glowing crown

	private static final DyeColor[][] TOWER_COLORS = {
			{DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.ORANGE}, {DyeColor.GRAY, DyeColor.CYAN, DyeColor.YELLOW},
			{DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.RED}, {DyeColor.WHITE, DyeColor.BLUE, DyeColor.LIME},
	};

	static void tower(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 2);
		DyeColor[] col = TOWER_COLORS[Math.floorMod(variant, TOWER_COLORS.length)];
		BlockState frame = c(col[0]);
		BlockState curtain = glass(col[1]);
		BlockState accent = c(col[2]);
		int top = floors * 3;
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, b(Blocks.POLISHED_DIORITE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 1) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SMOOTH_STONE));
			}
			if (f > 0) {
				p.ring(-hw, 0, hw, d, y0, frame);
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, curtain);
				for (int k = -hw; k <= hw; k += 3) {
					p.set(k, y, 0, frame);
					p.set(k, y, d, frame);
				}
				for (int k = 0; k <= d; k += 3) {
					p.set(-hw, y, k, frame);
					p.set(hw, y, k, frame);
				}
				p.set(-hw, y, d, frame);
				p.set(hw, y, d, frame);
			}
			// Balconies on alternate corners, floor by floor.
			if (f >= 2) {
				boolean right = f % 2 == 0;
				int xa = right ? hw - 4 : -hw + 1;
				for (int x = xa; x <= xa + 3; x++) {
					p.set(x, y0, -1, frame);
					p.set(x, y0 + 1, -1, pane(DyeColor.LIGHT_BLUE));
					p.set(-x, y0, d + 1, frame);
					p.set(-x, y0 + 1, d + 1, pane(DyeColor.LIGHT_BLUE));
				}
			}
		}
		// The lobby: two floors of glass, a wide canopy with lights.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			for (int y = 1; y <= 5; y++) {
				p.set(x, y, 0, b(Blocks.GLASS));
			}
		}
		p.door(0, 1, 0, Blocks.BIRCH_DOOR, L.BACK);
		p.door(1, 1, 0, Blocks.BIRCH_DOOR, L.BACK);
		for (int x = -3; x <= 4; x++) {
			for (int z = -2; z <= -1; z++) {
				p.set(x, 6, z, x == -2 || x == 3 ? COLD : slabTop(Blocks.SMOOTH_STONE_SLAB));
			}
		}
		Interiors.room(p, r, Interiors.LOBBY, -hw + 1, hw - 1, 0, 1, d - 1, 6);
		// The LED stripe up the middle of the front and the back.
		for (int y = 7; y <= top; y++) {
			boolean led = y % 3 == 0;
			p.set(-1, y, -1, led ? COLD : frame);
			p.set(1, y, d + 1, led ? COLD : frame);
		}
		// Flats: four to a floor round the stair; a ladder in the corner.
		p.ladder(hw - 1, 1, top - 1, d - 1, L.BACK);
		for (int f = 2; f < floors; f++) {
			int y0 = f * 3;
			BlockState in = c(DyeColor.WHITE);
			Interiors.wallX(p, 0, y0, 1, d - 1, d / 2, in);
			Interiors.wallZ(p, d / 2, y0, -hw + 1, hw - 1, -3, in);
			Interiors.wallZ(p, d / 2, y0, -hw + 1, hw - 1, 3, in);
			Interiors.floor(p, r, -hw + 1, -1, y0, 1, d / 2 - 1);
			Interiors.floor(p, r, 1, hw - 1, y0, 1, d / 2 - 1);
			Interiors.floor(p, r, -hw + 1, -1, y0, d / 2 + 1, d - 1);
			Interiors.floor(p, r, 1, hw - 1, y0, d / 2 + 1, d - 2);
			Interiors.room(p, r, Interiors.flatRoom(r), -hw + 1, -1, y0, 1, d / 2 - 1);
			Interiors.room(p, r, Interiors.flatRoom(r), 1, hw - 1, y0, 1, d / 2 - 1);
			Interiors.room(p, r, Interiors.flatRoom(r), -hw + 1, -1, y0, d / 2 + 1, d - 1);
			Interiors.room(p, r, Interiors.flatRoom(r), 1, hw - 2, y0, d / 2 + 1, d - 1);
		}
		// The crown: a glowing band on the parapet, the machine room, a mast with the red light.
		p.fill(-hw, top, 0, hw, top, d, frame);
		for (int x = -hw; x <= hw; x++) {
			p.set(x, top + 1, 0, Math.floorMod(x, 2) == 0 ? COLD : accent);
			p.set(x, top + 1, d, Math.floorMod(x, 2) == 0 ? COLD : accent);
		}
		for (int z = 1; z < d; z++) {
			p.set(-hw, top + 1, z, Math.floorMod(z, 2) == 0 ? COLD : accent);
			p.set(hw, top + 1, z, Math.floorMod(z, 2) == 0 ? COLD : accent);
		}
		p.fill(-3, top + 1, 4, 3, top + 3, d - 4, frame);
		for (int x = -3; x <= 3; x += 2) {
			p.set(x, top + 2, 4, b(Blocks.IRON_BARS));
		}
		for (int y = top + 4; y <= top + 10; y++) {
			p.set(0, y, d / 2, b(Blocks.IRON_BARS));
		}
		p.set(0, top + 11, d / 2, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true));
		p.top = top + 12;
	}

	// ------------------------------------------------------------------------------------------------
	// Business centre: glass curtain wall, a colonnade, the logo lit on the roof, a roof garden

	private static final DyeColor[][] OFFICE_COLORS = {
			{DyeColor.CYAN, DyeColor.BLACK, DyeColor.ORANGE}, {DyeColor.BLUE, DyeColor.GRAY, DyeColor.RED},
			{DyeColor.BLACK, DyeColor.WHITE, DyeColor.LIME}, {DyeColor.LIGHT_BLUE, DyeColor.GRAY, DyeColor.PURPLE},
	};

	static void office(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 3);
		DyeColor[] col = OFFICE_COLORS[Math.floorMod(variant, OFFICE_COLORS.length)];
		BlockState curtain = glass(col[0]);
		BlockState spandrel = c(col[1]);
		BlockState mullion = b(Blocks.SMOOTH_QUARTZ);
		int top = floors * 3;
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, b(Blocks.POLISHED_ANDESITE));
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SMOOTH_STONE));
				p.ring(-hw, 0, hw, d, y0, spandrel);
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, curtain);
				for (int k = -hw; k <= hw; k += 4) {
					p.set(k, y, 0, mullion);
					p.set(k, y, d, mullion);
				}
				for (int k = 0; k <= d; k += 4) {
					p.set(-hw, y, k, mullion);
					p.set(hw, y, k, mullion);
				}
			}
		}
		// The colonnade: the ground floor set back behind columns.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			p.remove(x, 1, 0);
			p.remove(x, 2, 0);
			p.set(x, 1, 2, b(Blocks.GLASS));
			p.set(x, 2, 2, b(Blocks.GLASS));
		}
		for (int x = -hw; x <= hw; x += 4) {
			p.set(x, 1, 0, b(Blocks.QUARTZ_PILLAR));
			p.set(x, 2, 0, b(Blocks.QUARTZ_PILLAR));
		}
		for (int x = -hw + 2; x <= hw - 2; x += 4) {
			p.set(x, 3, 0, COLD);
		}
		p.remove(0, 1, 2);
		p.remove(0, 2, 2);
		p.door(0, 1, 2, Blocks.BIRCH_DOOR, L.BACK);
		Interiors.room(p, r, Interiors.LOBBY, -hw + 1, hw - 1, 0, 3, d - 1);
		for (int f = 1; f < floors; f++) {
			int y0 = f * 3;
			Interiors.room(p, r, f % 3 == 0 ? Interiors.MEETING : Interiors.OFFICE, -hw + 1, hw - 1, y0, 1, d - 1);
		}
		p.ladder(hw - 1, 1, top - 1, d - 1, L.BACK);
		// Roof: the logo glowing over the front, a garden with benches and lamps.
		p.fill(-hw, top, 0, hw, top, d, spandrel);
		p.ring(-hw, 0, hw, d, top + 1, b(Blocks.SMOOTH_QUARTZ_SLAB));
		for (int x = -4; x <= 4; x++) {
			for (int y = top + 1; y <= top + 3; y++) {
				p.set(x, y, 0, glass(Math.abs(x) + Math.abs(y - top - 2) <= 3 ? col[2] : DyeColor.WHITE));
				p.set(x, y, 1, COLD);
			}
		}
		for (int x = -hw + 2; x <= hw - 2; x++) {
			for (int z = 4; z <= d - 2; z++) {
				p.set(x, top, z, (x + z) % 5 == 0 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK));
			}
		}
		for (int x = -hw + 3; x <= hw - 3; x += 4) {
			p.set(x, top + 1, 5, b(Blocks.FLOWERING_AZALEA));
			p.set(x + 1, top + 1, d - 3, p.stairs(Blocks.OAK_STAIRS, L.BACK, false));
			p.set(x + 2, top + 1, 7, b(Blocks.OAK_FENCE));
			p.set(x + 2, top + 2, 7, b(Blocks.LANTERN));
		}
		p.top = top + 4;
	}

	// ------------------------------------------------------------------------------------------------
	// Villas: modern cube, Scandinavian gable, brick

	static void villa(Plan p, int floors, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 4);
		int style = Math.floorMod(variant, 3);
		BlockState ground;
		BlockState upper;
		BlockState trim;
		switch (style) {
			case 0 -> {
				ground = b(Blocks.SMOOTH_QUARTZ);
				upper = variant % 2 == 0 ? b(Blocks.STRIPPED_SPRUCE_LOG) : b(Blocks.DARK_OAK_PLANKS);
				trim = c(DyeColor.GRAY);
			}
			case 1 -> {
				ground = variant % 2 == 0 ? c(DyeColor.WHITE) : b(Blocks.DEEPSLATE_TILES);
				upper = ground;
				trim = b(Blocks.SPRUCE_PLANKS);
			}
			default -> {
				ground = b(Blocks.BRICKS);
				upper = ground;
				trim = b(Blocks.SMOOTH_QUARTZ);
			}
		}
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, b(Blocks.OAK_PLANKS));
		int top = floors * 3;
		for (int f = 0; f < floors; f++) {
			int y0 = f * 3;
			BlockState w = f == 0 ? ground : upper;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SPRUCE_PLANKS));
				p.ring(-hw, 0, hw, d, y0, trim);
			}
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, w);
			}
			// Corner pilasters in the trim colour.
			if (style != 0) {
				for (int y = y0 + 1; y <= y0 + 2; y++) {
					p.set(-hw, y, 0, trim);
					p.set(hw, y, 0, trim);
					p.set(-hw, y, d, trim);
					p.set(hw, y, d, trim);
				}
			}
			// Windows: big glass in the modern house, framed with shutters in the others.
			for (int x = -hw + 1; x <= hw - 1; x++) {
				boolean big = style == 0 && (f == 0 ? x <= -1 : x >= 1);
				boolean small = style != 0 && Math.abs(x) >= 2 && Math.abs(x) <= hw - 1 && Math.floorMod(x, 2) == 0;
				if (big) {
					p.set(x, y0 + 1, 0, b(Blocks.GLASS));
					p.set(x, y0 + 2, 0, b(Blocks.GLASS));
				} else if (small) {
					p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
					p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
					p.set(x - 1, y0 + 2, -1, onWall(p, Blocks.SPRUCE_TRAPDOOR, L.FRONT));
					p.set(x + 1, y0 + 2, -1, onWall(p, Blocks.SPRUCE_TRAPDOOR, L.FRONT));
				}
				if (x % 2 == 0) {
					p.set(x, y0 + 1, d, b(Blocks.GLASS_PANE));
					p.set(x, y0 + 2, d, b(Blocks.GLASS_PANE));
				}
			}
			for (int z = 2; z < d; z += 3) {
				p.set(-hw, y0 + 2, z, b(Blocks.GLASS_PANE));
				p.set(hw, y0 + 2, z, b(Blocks.GLASS_PANE));
			}
		}
		// The door with a canopy and lamps, plants by the steps.
		p.door(1, 1, 0, style == 0 ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR, L.BACK);
		p.set(0, 3, -1, slabTop(Blocks.SMOOTH_STONE_SLAB));
		p.set(1, 3, -1, slabTop(Blocks.SMOOTH_STONE_SLAB));
		p.set(2, 3, -1, slabTop(Blocks.SMOOTH_STONE_SLAB));
		p.set(0, 2, -1, hanging());
		p.set(2, 1, -1, Interiors.plant(r));
		// Roof.
		if (style == 0) {
			p.fill(-hw, top, 0, hw, top, d, c(DyeColor.GRAY));
			for (int x = -hw - 1; x <= hw + 1; x++) {
				p.set(x, top, -1, slabTop(Blocks.SMOOTH_STONE_SLAB));
				p.set(x, top, d + 1, slabTop(Blocks.SMOOTH_STONE_SLAB));
			}
			// A roof terrace: glass rail, a sun lounger, lamps.
			for (int x = -hw; x <= hw; x++) {
				p.set(x, top + 1, 0, pane(DyeColor.LIGHT_BLUE));
				p.set(x, top + 1, d, pane(DyeColor.LIGHT_BLUE));
			}
			for (int z = 1; z < d; z++) {
				p.set(-hw, top + 1, z, pane(DyeColor.LIGHT_BLUE));
				p.set(hw, top + 1, z, pane(DyeColor.LIGHT_BLUE));
			}
			p.set(-1, top + 1, 2, p.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.WHITE), L.BACK, false));
			p.set(-1, top + 1, 3, Blocks.WOOL_SLAB.pick(DyeColor.WHITE).defaultBlockState());
			p.set(hw - 1, top + 1, d - 1, b(Blocks.OAK_FENCE));
			p.set(hw - 1, top + 2, d - 1, b(Blocks.LANTERN));
			p.set(-hw + 1, top + 1, d - 1, Interiors.plant(r));
			p.top = top + 3;
		} else {
			p.fill(-hw, top, 0, hw, top, d, trim);
			Block stairs = style == 1 ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.DARK_OAK_STAIRS;
			Block ridge = style == 1 ? Blocks.DEEPSLATE_TILES : Blocks.DARK_OAK_PLANKS;
			p.roofAlongZ(top + 1, stairs, ridge, upper.getBlock(), true, true);
			// The chimney, smoking.
			int cx = -hw + 1;
			for (int y = top + 1; y <= top + hw + 2; y++) {
				p.set(cx, y, d - 2, b(Blocks.BRICKS));
			}
			p.set(cx, top + hw + 3, d - 2, b(Blocks.CAMPFIRE));
			p.top = Math.max(p.top, top + hw + 4);
		}
		// Inside.
		p.ladder(hw - 1, 1, top - 1, d - 1, L.BACK);
		Interiors.room(p, r, Interiors.LIVING, -hw + 1, 0, 0, 1, d - 1);
		Interiors.room(p, r, Interiors.KITCHEN, 1, hw - 2, 0, 1, d - 2);
		for (int f = 1; f < floors; f++) {
			Interiors.room(p, r, Interiors.BEDROOM, -hw + 1, 0, f * 3, 1, d - 1);
			Interiors.room(p, r, f == 1 && p.b.type.beds > 3 ? Interiors.KIDS : Interiors.BEDROOM, 1, hw - 2, f * 3, 1, d - 2);
		}
		if (floors == 1) {
			p.bed(hw - 1, 1, 1, L.BACK, DyeColor.LIGHT_BLUE);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Shops: supermarket, cafe, pharmacy, bakery, electronics, flowers

	static void shop(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 5);
		int kind = Math.floorMod(variant, 6);
		BlockState wall = switch (kind) {
			case 1 -> b(Blocks.BRICKS);
			case 3 -> b(Blocks.SMOOTH_SANDSTONE);
			case 4 -> c(DyeColor.BLACK);
			case 5 -> c(DyeColor.LIME);
			default -> c(DyeColor.WHITE);
		};
		DyeColor sign = new DyeColor[]{DyeColor.RED, DyeColor.ORANGE, DyeColor.LIME, DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.PINK}[kind];
		DyeColor[] awning = switch (kind) {
			case 1 -> new DyeColor[]{DyeColor.BROWN, DyeColor.WHITE};
			case 3 -> new DyeColor[]{DyeColor.RED, DyeColor.WHITE};
			case 5 -> new DyeColor[]{DyeColor.GREEN, DyeColor.WHITE};
			default -> null;
		};
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, b(kind == 1 ? Blocks.DARK_OAK_PLANKS : Blocks.POLISHED_DIORITE));
		for (int y = 1; y <= 4; y++) {
			p.ring(-hw, 0, hw, d, y, wall);
		}
		// The shop window, the sign, the awning.
		for (int x = -hw + 1; x <= hw - 1; x++) {
			p.set(x, 1, 0, b(Blocks.GLASS));
			p.set(x, 2, 0, b(Blocks.GLASS));
			if (awning != null) {
				p.set(x, 3, -1, p.stairs(Blocks.WOOL_STAIRS.pick(awning[Math.floorMod(x, 2)]), L.BACK, false));
			}
		}
		lightbox(p, -hw + 1, hw - 1, 4, 0, sign, true);
		if (kind == 2) {
			// The pharmacy's green cross, glowing.
			for (int k = -1; k <= 1; k++) {
				p.set(hw - 2 + k, 3, -1, glass(DyeColor.LIME));
				p.set(hw - 2, 3 + k, -1, glass(DyeColor.LIME));
			}
			p.set(hw - 2, 3, 0, COLD);
		}
		p.door(0, 1, 0, kind == 1 ? Blocks.DARK_OAK_DOOR : Blocks.BIRCH_DOOR, L.BACK);
		p.door(1, 1, 0, kind == 1 ? Blocks.DARK_OAK_DOOR : Blocks.BIRCH_DOOR, L.BACK);
		p.fill(-hw, 5, 0, hw, 5, d, c(DyeColor.GRAY));
		p.ring(-hw, 0, hw, d, 6, wall);
		roofKit(p, r, -hw + 1, hw - 1, 2, d - 1, 6, 3);
		// Inside (the floor of the shop is the room y = 0, ceiling at 5 - the lights go into it from 3 up).
		int interior = switch (kind) {
			case 1, 3 -> Interiors.CAFE;
			default -> Interiors.SHOP;
		};
		Interiors.room(p, r, interior, -hw + 1, hw - 1, 0, 1, d - 1, 5);
		for (int x = -hw + 2; x <= hw - 2; x += 4) {
			p.set(x, 5, d / 2, kind == 1 || kind == 3 ? WARM : COLD);
		}
		if (kind == 4) {
			for (int x = -hw + 1; x <= hw - 1; x += 2) {
				p.set(x, 3, d - 1, c(DyeColor.BLACK));
				p.set(x, 2, d - 1, glass(DyeColor.BLUE));
			}
		}
		if (kind == 5) {
			for (int x = -hw + 1; x <= hw - 1; x++) {
				p.set(x, 1, -1, Interiors.plant(r));
			}
		}
		p.top = 8;
	}

	// ------------------------------------------------------------------------------------------------
	// School

	static void school(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 6);
		BlockState wall = variant % 2 == 0 ? b(Blocks.BRICKS) : terra(DyeColor.ORANGE);
		BlockState white = b(Blocks.SMOOTH_QUARTZ);
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, b(Blocks.POLISHED_ANDESITE));
		for (int f = 0; f < 3; f++) {
			int y0 = f * 4;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SMOOTH_STONE));
			}
			p.ring(-hw, 0, hw, d, y0, white);
			for (int y = y0 + 1; y <= y0 + 3; y++) {
				p.ring(-hw, 0, hw, d, y, wall);
			}
			for (int x = -hw + 1; x <= hw - 1; x++) {
				int o = Math.floorMod(x + hw, 4);
				if (o == 0) {
					for (int y = y0 + 1; y <= y0 + 3; y++) {
						p.set(x, y, 0, white);
					}
					continue;
				}
				for (int y = y0 + 1; y <= y0 + 2; y++) {
					p.set(x, y, 0, b(Blocks.GLASS_PANE));
					p.set(x, y, d, b(Blocks.GLASS_PANE));
				}
			}
			// Classrooms: walls every eight blocks, a corridor along the back.
			for (int x = -hw + 8; x < hw; x += 8) {
				Interiors.wallX(p, x, y0, 1, d - 4, d - 3, c(DyeColor.WHITE));
			}
			for (int x0 = -hw + 1; x0 < hw; x0 += 8) {
				int x1 = Math.min(x0 + 6, hw - 1);
				if (f == 0 && x0 <= 0 && x1 >= 0) {
					Interiors.room(p, r, Interiors.LOBBY, x0, x1, y0, 1, d - 3, 4);
					continue;
				}
				Interiors.room(p, r, Interiors.CLASSROOM, x0, x1, y0, 1, d - 4, 4);
			}
			for (int x = -hw + 2; x <= hw - 2; x += 5) {
				p.set(x, y0 + 4, d - 2, COLD);
			}
		}
		p.fill(-hw, 12, 0, hw, 12, d, c(DyeColor.GRAY));
		p.ring(-hw, 0, hw, d, 13, white);
		// The central porch: columns, a pediment, the glowing name board, steps.
		for (int x = -3; x <= 3; x += 2) {
			for (int y = 1; y <= 7; y++) {
				p.set(x, y, -2, b(Blocks.QUARTZ_PILLAR));
			}
		}
		p.fill(-4, 8, -2, 4, 8, -1, white);
		for (int k = 0; k <= 4; k++) {
			for (int x = -4 + k; x <= 4 - k; x++) {
				p.set(x, 9 + k, -1, white);
			}
		}
		lightbox(p, -2, 2, 7, -1, DyeColor.WHITE, true);
		p.fill(-4, 0, -2, 4, 0, -1, b(Blocks.POLISHED_ANDESITE));
		p.door(0, 1, 0, Blocks.OAK_DOOR, L.BACK);
		p.door(-1, 1, 0, Blocks.OAK_DOOR, L.BACK);
		p.set(-2, 6, -1, hanging());
		p.set(2, 6, -1, hanging());
		p.ladder(hw - 1, 1, 11, d - 1, L.BACK);
		p.set(0, 14, 2, b(Blocks.BELL));
		p.top = 15;
	}

	// ------------------------------------------------------------------------------------------------
	// City hall: columns, pediment, dome with a clock, the flag; floodlit at night

	static void cityHall(Plan p, DyeColor flag) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 7);
		BlockState wall = b(Blocks.SMOOTH_QUARTZ);
		BlockState stone = b(Blocks.POLISHED_ANDESITE);
		p.fill(-hw, -1, 0, hw, -1, d, stone);
		p.fill(-hw, 0, 0, hw, 0, d, b(Blocks.POLISHED_DIORITE));
		for (int f = 0; f < 3; f++) {
			int y0 = f * 4;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SMOOTH_STONE));
			}
			p.ring(-hw, 0, hw, d, y0, f == 0 ? stone : wall);
			for (int y = y0 + 1; y <= y0 + 3; y++) {
				p.ring(-hw, 0, hw, d, y, f == 0 ? stone : wall);
			}
			for (int x = -hw + 1; x <= hw - 1; x++) {
				if (Math.floorMod(x, 3) != 0) {
					p.set(x, y0 + 1, 0, b(Blocks.GLASS_PANE));
					p.set(x, y0 + 2, 0, b(Blocks.GLASS_PANE));
					p.set(x, y0 + 1, d, b(Blocks.GLASS_PANE));
					p.set(x, y0 + 2, d, b(Blocks.GLASS_PANE));
				} else if (f > 0) {
					for (int y = y0 + 1; y <= y0 + 3; y++) {
						p.set(x, y, -1, b(Blocks.QUARTZ_PILLAR));
					}
				}
			}
			for (int z = 2; z < d; z += 3) {
				p.set(-hw, y0 + 2, z, b(Blocks.GLASS_PANE));
				p.set(hw, y0 + 2, z, b(Blocks.GLASS_PANE));
			}
			// Halls: a red carpet runner, desks, chandeliers.
			for (int z = 1; z < d; z++) {
				Interiors.put(p, 0, y0 + 1, z, Interiors.carpet(DyeColor.RED));
			}
			Interiors.room(p, r, Interiors.MEETING, -hw + 1, -2, y0, 1, d - 1, 4);
			Interiors.room(p, r, Interiors.OFFICE, 2, hw - 1, y0, 1, d - 1, 4);
			for (int z = 3; z < d; z += 4) {
				p.set(0, y0 + 3, z, hanging());
			}
		}
		p.fill(-hw, 12, 0, hw, 12, d, b(Blocks.SMOOTH_STONE));
		for (int x = -hw - 1; x <= hw + 1; x++) {
			p.set(x, 12, -1, slabTop(Blocks.SMOOTH_QUARTZ_SLAB));
			p.set(x, 12, d + 1, slabTop(Blocks.SMOOTH_QUARTZ_SLAB));
		}
		p.ring(-hw, 0, hw, d, 13, b(Blocks.SMOOTH_QUARTZ_SLAB));
		// Portico with a pediment.
		for (int x = -6; x <= 6; x += 3) {
			for (int y = 1; y <= 8; y++) {
				p.set(x, y, -3, b(Blocks.QUARTZ_PILLAR));
			}
		}
		p.fill(-7, 9, -4, 7, 9, -1, wall);
		for (int k = 0; k <= 4; k++) {
			for (int x = -7 + k * 2; x <= 7 - k * 2; x++) {
				p.set(x, 10 + k, -3, wall);
			}
		}
		p.fill(-7, 0, -4, 7, 0, -1, b(Blocks.POLISHED_DIORITE));
		p.door(0, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		p.door(-1, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		for (int x = -5; x <= 5; x += 5) {
			p.set(x, 8, -2, hanging());
		}
		// Floodlights at the foot of the front.
		for (int x = -hw; x <= hw; x += 4) {
			p.set(x, 0, -1, COLD);
		}
		// The drum, the dome, the clock, the spire and the flag.
		int cz = d / 2;
		for (int y = 14; y <= 17; y++) {
			p.ring(-3, cz - 3, 3, cz + 3, y, wall);
		}
		p.set(0, 16, cz - 3, c(DyeColor.BLACK));
		p.set(0, 15, cz - 3, c(DyeColor.BLACK));
		p.set(1, 15, cz - 3, c(DyeColor.BLACK));
		for (int k = 0; k <= 3; k++) {
			BlockState dome = k < 3 ? b(Blocks.PRISMARINE_BRICKS) : b(Blocks.DARK_PRISMARINE);
			p.ring(-3 + k, cz - 3 + k, 3 - k, cz + 3 - k, 18 + k, dome);
		}
		p.set(0, 21, cz, b(Blocks.DARK_PRISMARINE));
		for (int y = 22; y <= 27; y++) {
			p.set(0, y, cz, b(Blocks.IRON_BARS));
		}
		p.set(0, 28, cz, Blocks.BANNER.pick(flag).defaultBlockState().setValue(net.minecraft.world.level.block.BannerBlock.ROTATION,
				Blueprints.rotation16(p.w(L.FRONT))));
		for (int x = -3; x <= 3; x += 6) {
			p.set(x, 18, cz - 4, COLD);
		}
		p.set(0, 1, 6, b(Blocks.BELL));
		p.ladder(hw - 1, 1, 11, d - 1, L.BACK);
		p.top = 29;
	}

	// ------------------------------------------------------------------------------------------------
	// Hospital: white with a blue window band, the red cross glowing, a helipad on the roof

	static void hospital(Plan p) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		Random r = Interiors.random(p, 8);
		p.fill(-hw, -1, 0, hw, -1, d, c(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, b(Blocks.SMOOTH_QUARTZ));
		for (int f = 0; f < 3; f++) {
			int y0 = f * 3;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, b(Blocks.SMOOTH_QUARTZ));
			}
			p.ring(-hw, 0, hw, d, y0, c(DyeColor.LIGHT_GRAY));
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.ring(-hw, 0, hw, d, y, c(DyeColor.WHITE));
			}
			for (int x = -hw + 1; x <= hw - 1; x++) {
				p.set(x, y0 + 2, 0, pane(DyeColor.LIGHT_BLUE));
				p.set(x, y0 + 2, d, pane(DyeColor.LIGHT_BLUE));
			}
			for (int z = 1; z < d; z++) {
				p.set(-hw, y0 + 2, z, pane(DyeColor.LIGHT_BLUE));
				p.set(hw, y0 + 2, z, pane(DyeColor.LIGHT_BLUE));
			}
			Interiors.room(p, r, f == 0 ? Interiors.LOBBY : Interiors.WARD, -hw + 1, hw - 2, y0, 1, d - 1);
		}
		p.fill(-hw, 9, 0, hw, 9, d, c(DyeColor.GRAY));
		// The red cross, glowing.
		for (int k = -1; k <= 1; k++) {
			p.set(k, 7, -1, glass(DyeColor.RED));
			p.set(0, 7 + k, -1, glass(DyeColor.RED));
		}
		p.set(0, 7, 0, COLD);
		// The entrance canopy (the ambulance stops under it).
		for (int x = -3; x <= 3; x++) {
			for (int z = -2; z <= -1; z++) {
				p.set(x, 3, z, x == 0 ? COLD : slabTop(Blocks.SMOOTH_QUARTZ_SLAB));
			}
		}
		p.door(0, 1, 0, Blocks.BIRCH_DOOR, L.BACK);
		p.door(-1, 1, 0, Blocks.BIRCH_DOOR, L.BACK);
		p.ladder(hw - 1, 1, 8, d - 1, L.BACK);
		// The helipad: an H in a circle of lights.
		for (int z = 1; z <= d - 1; z++) {
			for (int x = -hw + 1; x <= hw - 1; x++) {
				p.set(x, 9, z, c(DyeColor.GRAY));
			}
		}
		int cz = d / 2;
		for (int k = -2; k <= 2; k++) {
			p.set(-2, 9, cz + k, c(DyeColor.WHITE));
			p.set(2, 9, cz + k, c(DyeColor.WHITE));
		}
		p.set(-1, 9, cz, c(DyeColor.WHITE));
		p.set(0, 9, cz, c(DyeColor.WHITE));
		p.set(1, 9, cz, c(DyeColor.WHITE));
		for (int[] q : new int[][]{{-hw + 1, 1}, {hw - 1, 1}, {-hw + 1, d - 1}, {hw - 1, d - 1}}) {
			p.set(q[0], 9, q[1], COLD);
		}
		p.top = 11;
	}
}
