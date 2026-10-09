package com.stasdoto.airdefense.nation;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * Today's buildings: panel blocks of five and nine floors, glass towers and offices, cottages, shops, a school, the
 * city hall, parks, a gas station, the logistics hub, oil wells and a refinery, garages - and modern versions of the
 * old village buildings. Every design has variants (colours, details) picked by the building's {@code variant}.
 * Drawn like the other blueprints: x right of the door, z into the building, y up from the floor.
 */
final class ModernDesigns {
	private ModernDesigns() {
	}

	/** Wall colours of panel blocks: main, accent (balconies, bands), plinth. */
	private static final DyeColor[][] PANEL = {
			{DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY},
			{DyeColor.LIGHT_GRAY, DyeColor.WHITE, DyeColor.GRAY},
			{DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.LIGHT_GRAY},
			{DyeColor.WHITE, DyeColor.ORANGE, DyeColor.GRAY},
			{DyeColor.LIGHT_GRAY, DyeColor.YELLOW, DyeColor.GRAY},
			{DyeColor.WHITE, DyeColor.CYAN, DyeColor.GRAY},
			{DyeColor.WHITE, DyeColor.PINK, DyeColor.LIGHT_GRAY},
			{DyeColor.LIGHT_GRAY, DyeColor.GREEN, DyeColor.GRAY},
	};
	private static final DyeColor[] GLASS = {DyeColor.LIGHT_BLUE, DyeColor.CYAN, DyeColor.GRAY, DyeColor.BLUE, DyeColor.LIGHT_GRAY, DyeColor.BLACK};

	static BlockState concrete(DyeColor c) {
		return Blocks.CONCRETE.pick(c).defaultBlockState();
	}

	static BlockState glass(DyeColor c) {
		return Blocks.STAINED_GLASS.pick(c).defaultBlockState();
	}

	static BlockState pane(DyeColor c) {
		return Blocks.STAINED_GLASS_PANE.pick(c).defaultBlockState();
	}

	/** Variants from this up are the old country designs (timber and stone houses of the hamlets). */
	static final int RUSTIC = 1000;

	static boolean design(Plan p, int variant, DyeColor flag) {
		if (variant >= RUSTIC && (p.b.type == BuildingType.HOUSE || p.b.type == BuildingType.SMALL_HOUSE)) {
			return false;
		}
		// 1.28: the town's own style first.
		if (p.b.style != 0 && StyleDesigns.design(p, variant, flag)) {
			return true;
		}
		switch (p.b.type) {
			case PANEL5 -> Architecture.panel(p, 5, variant);
			case PANEL9 -> Architecture.panel(p, 9, variant);
			case APARTMENTS -> Architecture.panel(p, 4, variant);
			case TOWER -> Architecture.tower(p, 16, variant);
			case OFFICE -> Architecture.office(p, 7, variant);
			case COTTAGE -> Architecture.villa(p, 2, variant);
			case HOUSE -> Architecture.villa(p, 2, variant + 1);
			case SMALL_HOUSE -> Architecture.villa(p, 1, variant);
			case SHOP -> Architecture.shop(p, variant);
			case SCHOOL -> Architecture.school(p, variant);
			case CITY_HALL -> Architecture.cityHall(p, flag);
			case PARK -> park(p, variant);
			case GAS_STATION -> gasStation(p, variant);
			case LOGISTICS_HUB -> logisticsHub(p, variant);
			case OIL_WELL -> oilWell(p);
			case REFINERY -> refinery(p);
			case GARAGES -> garages(p, variant);
			case FARM -> Rural.farm(p, variant);
			case FOOD_PLANT -> Industry.foodPlant(p, variant);
			case ARMS_FACTORY -> Industry.armsFactory(p, variant);
			case MARKET -> Industry.market(p, variant);
			case HOSPITAL -> Architecture.hospital(p);
			case WAREHOUSE -> warehouse(p, variant);
			case BARRACKS -> barracks(p, flag);
			case HANGAR -> hangar(p);
			case DEPOT -> depot(p, variant, flag);
			default -> {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------
	// Helpers

	/** Foundation and floor slab over the whole footprint. */
	private static void base(Plan p, int hw, int d, BlockState foundation, BlockState floor) {
		p.fill(-hw, -1, 0, hw, -1, d, foundation);
		p.fill(-hw, 0, 0, hw, 0, d, floor);
	}

	/** Walls of one storey from y0 to y0 + 2 (y0 = the floor), windows in the middle row every {@code step} blocks. */
	private static void storey(Plan p, int hw, int d, int y0, BlockState wall, BlockState win, int step, boolean floorSlab, BlockState slab) {
		if (floorSlab) {
			p.fill(-hw, y0, 0, hw, y0, d, slab);
		}
		for (int y = y0 + 1; y <= y0 + 2; y++) {
			p.ring(-hw, 0, hw, d, y, wall);
		}
		int wy = y0 + 1;
		for (int x = -hw + 1; x < hw; x++) {
			if (Math.floorMod(x + hw, step) != 0) {
				p.set(x, wy, 0, win);
				p.set(x, wy, d, win);
				if (step <= 2) {
					p.set(x, wy + 1, 0, win);
					p.set(x, wy + 1, d, win);
				}
			}
		}
		for (int z = 1; z < d; z++) {
			if (Math.floorMod(z, step) != 0) {
				p.set(-hw, wy, z, win);
				p.set(hw, wy, z, win);
			}
		}
	}

	/** Flat roof with a parapet, at height {@code y}. */
	private static void flatRoof(Plan p, int hw, int d, int y, BlockState roof, BlockState parapet) {
		p.fill(-hw, y, 0, hw, y, d, roof);
		p.ring(-hw, 0, hw, d, y + 1, parapet);
		p.top = Math.max(p.top, y + 2);
	}

	private static void entrance(Plan p, int x, Block door, BlockState canopy, boolean lamp) {
		p.door(x, 1, 0, door, L.BACK);
		for (int dx = -1; dx <= 1; dx++) {
			p.set(x + dx, 3, -1, canopy);
		}
		if (lamp) {
			p.set(x, 3, 0, Blocks.SEA_LANTERN.defaultBlockState());
		}
	}

	private static BlockState slabTop(Block block) {
		return block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
	}

	/** A street lamp: a post with a lantern hanging from it. */
	private static void lamp(Plan p, int x, int z, int h) {
		for (int y = 1; y <= h; y++) {
			p.set(x, y, z, Blocks.IRON_BARS.defaultBlockState());
		}
		p.set(x, h + 1, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		p.lantern(x, h, z, true);
	}

	private static void tree(Plan p, int x, int z, Block log, Block leaves) {
		for (int y = 1; y <= 4; y++) {
			p.set(x, y, z, log.defaultBlockState());
		}
		BlockState l = leaves.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = 4; y <= 6; y++) {
					if (Math.abs(dx) + Math.abs(dz) + (y == 6 ? 2 : 0) <= 3 && !(dx == 0 && dz == 0 && y < 5)) {
						p.set(x + dx, y, z + dz, l);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Housing




	// ------------------------------------------------------------------------------------------------
	// Town




	/** A park: grass, paths, trees, benches, a fountain in the middle, lamps. */
	private static void park(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.DIRT.defaultBlockState());
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.GRASS_BLOCK.defaultBlockState());
		int cz = d / 2;
		for (int x = -hw; x <= hw; x++) {
			p.set(x, 0, cz, Blocks.POLISHED_ANDESITE.defaultBlockState());
		}
		for (int z = 0; z <= d; z++) {
			p.set(0, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
		}
		// Fountain.
		p.ring(-2, cz - 2, 2, cz + 2, 0, Blocks.STONE_BRICKS.defaultBlockState());
		p.ring(-2, cz - 2, 2, cz + 2, 1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		p.fill(-1, 0, cz - 1, 1, 0, cz + 1, Blocks.WATER.defaultBlockState());
		p.set(0, 1, cz, Blocks.STONE_BRICK_WALL.defaultBlockState());
		Block log = variant % 2 == 0 ? Blocks.BIRCH_LOG : Blocks.OAK_LOG;
		Block leaves = variant % 2 == 0 ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES;
		for (int[] t : new int[][]{{-hw + 3, 3}, {hw - 3, 3}, {-hw + 3, d - 3}, {hw - 3, d - 3}}) {
			tree(p, t[0], t[1], log, leaves);
		}
		for (int x : new int[]{-5, 5}) {
			p.set(x, 1, cz + 1, p.stairs(Blocks.OAK_STAIRS, L.BACK, false));
			p.set(x, 1, cz - 1, p.stairs(Blocks.OAK_STAIRS, L.FRONT, false));
		}
		lamp(p, -2, cz - 4, 3);
		lamp(p, 2, cz + 4, 3);
		for (int x = -hw; x <= hw; x += 2) {
			p.set(x, 1, 0, Blocks.FLOWERING_AZALEA.defaultBlockState());
		}
		p.top = 7;
	}

	// ------------------------------------------------------------------------------------------------
	// Fuel and logistics

	/** Gas station: a canopy on columns over two pumps, a small shop at the back, the price board. */
	private static void gasStation(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		DyeColor brand = new DyeColor[]{DyeColor.GREEN, DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE}[Math.floorMod(variant, 4)];
		p.fill(-hw, -1, 0, hw, -1, d, concrete(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, concrete(DyeColor.LIGHT_GRAY));
		// Canopy.
		for (int[] c : new int[][]{{-4, 2}, {4, 2}, {-4, 5}, {4, 5}}) {
			for (int y = 1; y <= 4; y++) {
				p.set(c[0], y, c[1], concrete(DyeColor.WHITE));
			}
		}
		p.fill(-6, 5, 0, 6, 5, 7, concrete(DyeColor.WHITE));
		p.ring(-6, 0, 6, 7, 5, concrete(brand));
		p.set(0, 4, 3, Blocks.SEA_LANTERN.defaultBlockState());
		p.set(0, 4, 5, Blocks.SEA_LANTERN.defaultBlockState());
		// Pumps.
		for (int x : new int[]{-2, 2}) {
			p.set(x, 1, 3, Blocks.IRON_BLOCK.defaultBlockState());
			p.set(x, 2, 3, concrete(brand));
			p.set(x, 1, 4, Blocks.IRON_BLOCK.defaultBlockState());
			p.set(x, 2, 4, concrete(brand));
			p.set(x, 3, 3, Blocks.IRON_CHAIN.defaultBlockState());
		}
		// The shop.
		p.fill(-hw, 1, 8, hw, 3, d, concrete(DyeColor.WHITE));
		p.fill(-hw + 1, 1, 9, hw - 1, 2, d - 1, Blocks.AIR.defaultBlockState());
		for (int x = -4; x <= 4; x++) {
			p.set(x, 2, 8, Blocks.GLASS.defaultBlockState());
		}
		p.fill(-hw, 4, 8, hw, 4, d, concrete(brand));
		p.door(0, 1, 8, Blocks.BIRCH_DOOR, L.BACK);
		p.set(-4, 1, 9, Blocks.BARREL.defaultBlockState());
		// Price board on a pole.
		for (int y = 1; y <= 5; y++) {
			p.set(-hw, y, 0, Blocks.IRON_BARS.defaultBlockState());
		}
		p.set(-hw, 6, 0, concrete(brand));
		p.set(-hw, 7, 0, concrete(DyeColor.WHITE));
		p.top = 8;
	}

	/** Logistics hub: a big warehouse with loading docks and containers in the yard. */
	private static void logisticsHub(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		p.fill(-hw, -1, 0, hw, -1, d, concrete(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, concrete(DyeColor.LIGHT_GRAY));
		int z0 = 6;
		for (int y = 1; y <= 7; y++) {
			p.ring(-hw, z0, hw, d, y, concrete(y <= 2 ? DyeColor.GRAY : DyeColor.LIGHT_GRAY));
		}
		for (int x = -hw; x <= hw; x++) {
			for (int z = z0; z <= d; z++) {
				p.set(x, 8, z, Blocks.IRON_BLOCK.defaultBlockState());
			}
		}
		// Loading docks: wide openings with shutters (iron trapdoors) above.
		for (int x : new int[]{-6, 0, 6}) {
			for (int dx = -1; dx <= 1; dx++) {
				p.remove(x + dx, 1, z0);
				p.remove(x + dx, 2, z0);
				p.remove(x + dx, 3, z0);
				p.set(x + dx, 4, z0, concrete(DyeColor.ORANGE));
			}
		}
		p.set(hw - 2, 5, z0, concrete(DyeColor.BLUE));
		// Containers in the yard.
		DyeColor[] cont = {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.ORANGE};
		for (int i = 0; i < 3; i++) {
			int x = -hw + 1 + i * 4;
			DyeColor c = cont[(i + variant) % cont.length];
			p.fill(x, 1, 1, x + 2, 2, 4, concrete(c));
		}
		p.fill(hw - 4, 1, 1, hw - 1, 1, 3, Blocks.BARREL.defaultBlockState());
		for (int x = -hw + 2; x <= hw - 2; x += 4) {
			p.set(x, 1, d - 1, Blocks.CHEST.defaultBlockState());
			p.set(x + 1, 1, d - 1, Blocks.BARREL.defaultBlockState());
		}
		p.set(0, 1, d - 2, Blocks.SMITHING_TABLE.defaultBlockState());
		lamp(p, -hw, 0, 5);
		lamp(p, hw, 0, 5);
		p.top = 9;
	}

	/** An oil well: a pump jack (walking beam on a frame), a small tank, a fence round it. */
	private static void oilWell(Plan p) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.GRAVEL.defaultBlockState());
		p.fill(-hw, 0, 0, hw, 0, d, concrete(DyeColor.GRAY));
		// A-frame and the walking beam with the horse head.
		for (int y = 1; y <= 4; y++) {
			p.set(-1, y, 4, Blocks.IRON_BARS.defaultBlockState());
			p.set(1, y, 4, Blocks.IRON_BARS.defaultBlockState());
		}
		p.set(0, 5, 4, Blocks.IRON_BLOCK.defaultBlockState());
		for (int z = 1; z <= 7; z++) {
			p.set(0, 5, z, concrete(DyeColor.BLACK));
		}
		p.set(0, 5, 1, concrete(DyeColor.YELLOW));
		p.set(0, 4, 1, concrete(DyeColor.YELLOW));
		for (int y = 1; y <= 3; y++) {
			p.set(0, y, 1, Blocks.IRON_CHAIN.defaultBlockState());
		}
		p.set(0, 1, 7, concrete(DyeColor.RED));
		p.set(0, 2, 7, concrete(DyeColor.RED));
		// Wellhead and a tank.
		p.set(0, 0, 1, concrete(DyeColor.BLACK));
		p.fill(hw - 1, 1, d - 1, hw, 3, d, concrete(DyeColor.WHITE));
		p.ring(-hw, 0, hw, d, 1, Blocks.IRON_BARS.defaultBlockState());
		p.remove(0, 1, 0);
		p.top = 7;
	}

	/** Refinery: storage tanks, a distillation column, pipes and the flare stack. */
	private static void refinery(Plan p) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		p.fill(-hw, -1, 0, hw, -1, d, concrete(DyeColor.GRAY));
		p.fill(-hw, 0, 0, hw, 0, d, concrete(DyeColor.LIGHT_GRAY));
		// Round-ish tanks (white, silver).
		for (int[] t : new int[][]{{-hw + 3, 4}, {-hw + 3, d - 3}, {hw - 3, d - 3}}) {
			for (int y = 1; y <= 5; y++) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.abs(dx) + Math.abs(dz) <= 3 && (Math.abs(dx) == 2 || Math.abs(dz) == 2 || Math.abs(dx) + Math.abs(dz) == 3 || y == 5)) {
							p.set(t[0] + dx, y, t[1] + dz, concrete(y == 5 ? DyeColor.LIGHT_GRAY : DyeColor.WHITE));
						}
					}
				}
			}
		}
		// Distillation column.
		for (int y = 1; y <= 16; y++) {
			p.set(2, y, 6, Blocks.IRON_BLOCK.defaultBlockState());
			p.set(3, y, 6, Blocks.IRON_BLOCK.defaultBlockState());
			if (y % 4 == 0) {
				p.ring(1, 5, 4, 7, y, Blocks.IRON_BARS.defaultBlockState());
			}
		}
		// Pipes.
		for (int x = -hw + 5; x <= hw - 2; x++) {
			p.set(x, 3, 9, concrete(DyeColor.YELLOW));
		}
		for (int z = 2; z <= d - 2; z++) {
			p.set(5, 2, z, concrete(DyeColor.GRAY));
		}
		// The flare stack with its flame.
		for (int y = 1; y <= 19; y++) {
			p.set(hw - 2, y, 2, Blocks.STONE_BRICK_WALL.defaultBlockState());
		}
		p.set(hw - 2, 20, 2, Blocks.NETHERRACK.defaultBlockState());
		p.set(hw - 2, 21, 2, Blocks.FIRE.defaultBlockState());
		// Control room.
		p.fill(-2, 1, 0, 2, 3, 2, concrete(DyeColor.WHITE));
		p.fill(-1, 1, 1, 1, 2, 1, Blocks.AIR.defaultBlockState());
		p.door(0, 1, 0, Blocks.IRON_DOOR, L.BACK);
		p.set(-1, 2, 0, Blocks.GLASS_PANE.defaultBlockState());
		p.set(1, 2, 0, Blocks.GLASS_PANE.defaultBlockState());
		p.set(0, 1, 2, Blocks.BLAST_FURNACE.defaultBlockState());
		p.top = 22;
	}

	private static void garages(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		base(p, hw, d, concrete(DyeColor.GRAY), concrete(DyeColor.GRAY));
		for (int y = 1; y <= 3; y++) {
			p.ring(-hw, 0, hw, d, y, concrete(DyeColor.LIGHT_GRAY));
		}
		for (int x = -hw + 3; x < hw; x += 3) {
			for (int y = 1; y <= 3; y++) {
				p.set(x, y, 0, concrete(DyeColor.LIGHT_GRAY));
				for (int z = 1; z < d; z++) {
					p.set(x, y, z, concrete(DyeColor.LIGHT_GRAY));
				}
			}
		}
		DyeColor[] doors = {DyeColor.GREEN, DyeColor.BROWN, DyeColor.BLUE, DyeColor.GRAY};
		for (int x = -hw + 1; x < hw - 1; x += 3) {
			p.set(x, 1, 0, concrete(doors[Math.floorMod(x + variant, doors.length)]));
			p.set(x + 1, 1, 0, concrete(doors[Math.floorMod(x + variant, doors.length)]));
			p.set(x, 2, 0, concrete(doors[Math.floorMod(x + variant, doors.length)]));
			p.set(x + 1, 2, 0, concrete(doors[Math.floorMod(x + variant, doors.length)]));
		}
		p.fill(-hw, 4, 0, hw, 4, d, concrete(DyeColor.GRAY));
		p.top = 5;
	}

	// ------------------------------------------------------------------------------------------------
	// The old village buildings, today's way


	private static void warehouse(Plan p, int variant) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		base(p, hw, d, concrete(DyeColor.GRAY), concrete(DyeColor.LIGHT_GRAY));
		for (int y = 1; y <= 5; y++) {
			p.ring(-hw, 0, hw, d, y, concrete(y <= 1 ? DyeColor.GRAY : variant % 2 == 0 ? DyeColor.LIGHT_GRAY : DyeColor.WHITE));
		}
		p.fill(-hw, 6, 0, hw, 6, d, Blocks.IRON_BLOCK.defaultBlockState());
		for (int x = -1; x <= 1; x++) {
			for (int y = 1; y <= 3; y++) {
				p.remove(x, y, 0);
			}
		}
		for (int z = 2; z <= d - 2; z += 2) {
			p.set(-hw + 1, 1, z, Blocks.BARREL.defaultBlockState());
			p.set(-hw + 1, 2, z, Blocks.BARREL.defaultBlockState());
			p.set(hw - 1, 1, z, Blocks.CHEST.defaultBlockState());
		}
		p.lantern(0, 5, d / 2, true);
		p.top = 7;
	}

	/**
	 * A distribution warehouse (1.25): 49 x 27 blocks and 12 high - sandwich-panel walls with a clerestory of
	 * windows, eight loading bays under a canopy along the front, the office in one corner, skylights in the flat
	 * roof, a band in the country's colour; inside, rows of racks loaded with crates and barrels.
	 */
	private static void depot(Plan p, int variant, DyeColor flag) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		int top = 11;
		BlockState panelA = concrete(DyeColor.LIGHT_GRAY);
		BlockState panelB = concrete(variant % 2 == 0 ? DyeColor.WHITE : DyeColor.LIGHT_GRAY);
		BlockState plinth = concrete(DyeColor.GRAY);
		p.fill(-hw, -1, 0, hw, -1, d, plinth);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.SMOOTH_STONE.defaultBlockState());
		for (int y = 1; y < top; y++) {
			for (int x = -hw; x <= hw; x++) {
				BlockState s = y <= 2 ? plinth : Math.floorMod(x, 2) == 0 ? panelA : panelB;
				p.set(x, y, 0, s);
				p.set(x, y, d, s);
			}
			for (int z = 0; z <= d; z++) {
				BlockState s = y <= 2 ? plinth : Math.floorMod(z, 2) == 0 ? panelA : panelB;
				p.set(-hw, y, z, s);
				p.set(hw, y, z, s);
			}
		}
		// Clerestory windows high up on every side.
		for (int x = -hw + 2; x <= hw - 2; x++) {
			if (Math.floorMod(x, 3) != 0) {
				p.set(x, 9, d, pane(DyeColor.LIGHT_BLUE));
			}
		}
		for (int z = 2; z <= d - 2; z++) {
			if (Math.floorMod(z, 3) != 0) {
				p.set(-hw, 9, z, pane(DyeColor.LIGHT_BLUE));
				p.set(hw, 9, z, pane(DyeColor.LIGHT_BLUE));
			}
		}
		// The band in the country's colour under the roof edge, all round the front.
		for (int x = -hw; x <= hw; x++) {
			p.set(x, top - 1, 0, concrete(flag));
		}
		// The office in the right front corner: three floors of windows.
		int ox = hw - 9;
		for (int x = ox; x <= hw - 1; x++) {
			for (int y : new int[]{3, 4, 6, 7}) {
				if (x != ox) {
					p.set(x, y, 0, pane(DyeColor.CYAN));
				}
			}
		}
		for (int y = 1; y < top; y++) {
			p.set(ox, y, 1, panelA);
			for (int z = 1; z <= 6; z++) {
				p.set(ox, y, z, panelA);
			}
			for (int x = ox; x <= hw - 1; x++) {
				p.set(x, y, 6, panelA);
			}
		}
		for (int x = ox + 1; x <= hw - 1; x++) {
			for (int z = 1; z <= 5; z++) {
				p.set(x, 4, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
						net.minecraft.world.level.block.state.properties.SlabType.TOP));
				p.set(x, 7, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
						net.minecraft.world.level.block.state.properties.SlabType.TOP));
			}
		}
		p.door(hw - 4, 1, 0, Blocks.IRON_DOOR, L.BACK);
		p.set(hw - 2, 1, 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		p.set(hw - 2, 5, 2, Blocks.LECTERN.defaultBlockState());
		// Loading bays: shutters down, two of them open; a canopy over all of them, bumpers below.
		int bay = 0;
		for (int x = -hw + 3; x + 2 < ox - 1; x += 5) {
			boolean open = bay == 2 || bay == 5;
			for (int dx = 0; dx < 3; dx++) {
				for (int y = 1; y <= 4; y++) {
					if (open) {
						p.remove(x + dx, y, 0);
					} else {
						p.set(x + dx, y, 0, y == 4 ? concrete(DyeColor.ORANGE) : concrete(DyeColor.GRAY));
					}
				}
				p.set(x + dx, 5, 0, concrete(DyeColor.YELLOW));
			}
			p.set(x - 1, 1, -1, concrete(DyeColor.BLACK));
			p.set(x + 3, 1, -1, concrete(DyeColor.BLACK));
			bay++;
		}
		for (int x = -hw; x < ox; x++) {
			p.set(x, 6, -1, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
			p.set(x, 6, -2, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		}
		// The roof: flat, a parapet, skylight strips, vents.
		p.fill(-hw, top, 0, hw, top, d, Blocks.SMOOTH_STONE.defaultBlockState());
		for (int x = -hw + 4; x <= hw - 4; x += 6) {
			for (int z = 3; z <= d - 3; z++) {
				p.set(x, top, z, glass(DyeColor.LIGHT_GRAY));
			}
		}
		p.ring(-hw, 0, hw, d, top + 1, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		for (int x = -hw + 7; x <= hw - 7; x += 12) {
			p.set(x, top + 1, d / 2, Blocks.IRON_BLOCK.defaultBlockState());
			p.set(x, top + 2, d / 2, Blocks.IRON_TRAPDOOR.defaultBlockState());
		}
		// Racks inside: uprights, shelves, crates and barrels; aisles between.
		for (int z = 9; z <= d - 3; z += 5) {
			for (int x = -hw + 3; x <= hw - 3; x++) {
				boolean post = Math.floorMod(x + hw, 6) == 3;
				for (int y = 1; y <= 7; y++) {
					if (post) {
						p.set(x, y, z, Blocks.IRON_BARS.defaultBlockState());
					} else if (y == 3 || y == 6) {
						p.set(x, y, z, Blocks.SPRUCE_SLAB.defaultBlockState());
					} else if (y == 1 || y == 4 || y == 7) {
						long h = (long) x * 31 + z * 17 + y;
						int k = (int) Math.floorMod(h * 0x9E3779B1L >>> 7, 5L);
						p.set(x, y, z, k == 0 ? Blocks.BARREL.defaultBlockState() : k == 1 ? Blocks.CHEST.defaultBlockState()
								: k == 2 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
					}
				}
			}
		}
		for (int x = -hw + 4; x <= hw - 4; x += 8) {
			for (int z = 6; z <= d - 2; z += 8) {
				p.lantern(x, top - 1, z, true);
			}
		}
		// A ladder up the side, drainpipes.
		p.ladder(-hw - 1, 1, top, d - 3, L.RIGHT);
		for (int x : new int[]{-hw, hw}) {
			for (int y = 1; y < top; y++) {
				p.set(x, y, -1, Blocks.IRON_BARS.defaultBlockState());
			}
		}
		p.top = top + 3;
	}

	private static void barracks(Plan p, DyeColor flag) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		base(p, hw, d, concrete(DyeColor.GRAY), concrete(DyeColor.LIGHT_GRAY));
		for (int y = 1; y <= 4; y++) {
			p.ring(-hw, 0, hw, d, y, concrete(DyeColor.GREEN));
		}
		for (int x = -hw + 2; x < hw; x += 3) {
			p.set(x, 2, 0, Blocks.IRON_BARS.defaultBlockState());
			p.set(x, 2, d, Blocks.IRON_BARS.defaultBlockState());
		}
		flatRoof(p, hw, d, 5, concrete(DyeColor.GRAY), concrete(DyeColor.GREEN));
		p.door(0, 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
		DyeColor[] beds = {DyeColor.GREEN, DyeColor.BROWN};
		int i = 0;
		for (int x = -hw + 1; x <= hw - 1 && i < 6; x += 2) {
			if (x != 0) {
				p.bed(x, 1, d - 2, L.BACK, beds[i % 2]);
				i++;
			}
		}
		for (int y = 1; y <= 7; y++) {
			p.set(-hw - 1, y, -2, Blocks.IRON_BARS.defaultBlockState());
		}
		p.set(-hw - 1, 8, -2, Blocks.BANNER.pick(flag).defaultBlockState().setValue(net.minecraft.world.level.block.BannerBlock.ROTATION,
				Blueprints.rotation16(p.w(L.FRONT))));
		p.minX = Math.min(p.minX, -hw - 1);
		p.minZ = Math.min(p.minZ, -2);
		p.top = 9;
	}

	/** A modern arched hangar: steel shell, a wide gate. */
	private static void hangar(Plan p) {
		int hw = p.b.type.halfWidth();
		int d = p.b.type.depth - 1;
		base(p, hw, d, concrete(DyeColor.GRAY), concrete(DyeColor.GRAY));
		int[] heights = {3, 6, 8, 9, 10, 10, 10, 9, 8, 6, 3};
		for (int x = -hw; x <= hw; x++) {
			int k = (int) Math.round((double) (x + hw) / (2 * hw) * (heights.length - 1));
			int h = heights[k];
			for (int z = 0; z <= d; z++) {
				p.set(x, h, z, Blocks.IRON_BLOCK.defaultBlockState());
				if (Math.abs(x) == hw) {
					for (int y = 1; y < h; y++) {
						p.set(x, y, z, concrete(DyeColor.LIGHT_GRAY));
					}
				}
			}
			for (int y = 1; y < h; y++) {
				p.set(x, y, d, concrete(DyeColor.LIGHT_GRAY));
				if (Math.abs(x) > 4) {
					p.set(x, y, 0, concrete(DyeColor.LIGHT_GRAY));
				}
			}
			// Fill the steps between neighbouring heights so the arch is closed.
			int prev = heights[Math.max(0, k - 1)];
			for (int y = Math.min(prev, h); y < Math.max(prev, h); y++) {
				for (int z = 0; z <= d; z++) {
					p.set(x, y, z, Blocks.IRON_BLOCK.defaultBlockState());
				}
			}
		}
		for (int x = -4; x <= 4; x++) {
			p.set(x, 7, 0, concrete(DyeColor.YELLOW));
		}
		p.lantern(0, 8, d / 2, true);
		p.top = 11;
	}
}
