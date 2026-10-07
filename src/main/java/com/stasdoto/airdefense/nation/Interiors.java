package com.stasdoto.airdefense.nation;

import java.util.Random;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * Furniture for the modern buildings: flats (bedrooms, kitchens, living rooms, children's rooms), offices, classrooms,
 * wards, shops and cafes, lobbies; lights in the ceilings, so the windows glow at night. Rooms are given as an inner
 * box: x0..x1 across, z0..z1 from the front wall back, standing on the floor at height y (furniture goes on y + 1).
 * Furniture only goes into empty cells, so ladders and walls already drawn stay.
 */
final class Interiors {
	static final int BEDROOM = 0;
	static final int KITCHEN = 1;
	static final int LIVING = 2;
	static final int KIDS = 3;
	static final int OFFICE = 4;
	static final int CLASSROOM = 5;
	static final int WARD = 6;
	static final int SHOP = 7;
	static final int CAFE = 8;
	static final int LOBBY = 9;
	static final int MEETING = 10;

	private static final DyeColor[] SOFT = {DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BROWN, DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.GREEN,
			DyeColor.RED, DyeColor.WHITE, DyeColor.BLUE, DyeColor.PURPLE};
	private static final Block[] PLANTS = {Blocks.POTTED_FERN, Blocks.POTTED_BAMBOO, Blocks.POTTED_AZALEA, Blocks.POTTED_FLOWERING_AZALEA,
			Blocks.POTTED_SPRUCE_SAPLING, Blocks.POTTED_CACTUS, Blocks.POTTED_RED_TULIP, Blocks.POTTED_WHITE_TULIP};
	private static final Block[] FLOORS = {Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.PALE_OAK_PLANKS,
			Blocks.CHERRY_PLANKS};

	private Interiors() {
	}

	static Random random(Plan p, int salt) {
		return new Random(p.b.origin.asLong() * 31 + p.b.variant * 7919L + salt);
	}

	static void put(Plan p, int x, int y, int z, BlockState s) {
		if (!p.has(x, y, z)) {
			p.set(x, y, z, s);
		}
	}

	static BlockState plant(Random r) {
		return PLANTS[r.nextInt(PLANTS.length)].defaultBlockState();
	}

	static BlockState top(Block slab) {
		return slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	/** A wall across the building at x (from z0 to z1), with a doorway at {@code door}. */
	static void wallX(Plan p, int x, int y, int z0, int z1, int door, BlockState s) {
		for (int z = z0; z <= z1; z++) {
			for (int yy = y + 1; yy <= y + 2; yy++) {
				if (z != door) {
					put(p, x, yy, z, s);
				}
			}
		}
	}

	/** A wall along the building at z (from x0 to x1), with a doorway at {@code door}. */
	static void wallZ(Plan p, int z, int y, int x0, int x1, int door, BlockState s) {
		for (int x = x0; x <= x1; x++) {
			for (int yy = y + 1; yy <= y + 2; yy++) {
				if (x != door) {
					put(p, x, yy, z, s);
				}
			}
		}
	}

	/** A light set into the ceiling (the floor above), or none (some flats are dark at night). */
	static void ceilingLight(Plan p, Random r, int x, int y, int z, boolean warm, float chance) {
		if (r.nextFloat() < chance) {
			p.set(x, y + 3, z, (warm ? Blocks.OCHRE_FROGLIGHT : Blocks.SEA_LANTERN).defaultBlockState());
		}
	}

	/** A parquet floor for the room. */
	static void floor(Plan p, Random r, int x0, int x1, int y, int z0, int z1) {
		BlockState f = FLOORS[r.nextInt(FLOORS.length)].defaultBlockState();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				p.set(x, y, z, f);
			}
		}
	}

	/** Furnishes a room of the given kind. */
	static void room(Plan p, Random r, int kind, int x0, int x1, int y, int z0, int z1) {
		room(p, r, kind, x0, x1, y, z0, z1, 3);
	}

	/** Same, for a room {@code ceil} blocks from floor to ceiling (the lights go into the ceiling). */
	static void room(Plan p, Random r, int kind, int x0, int x1, int y, int z0, int z1, int ceil) {
		int w = x1 - x0 + 1;
		int dep = z1 - z0 + 1;
		if (w < 3 || dep < 4) {
			return;
		}
		int f = y + 1;
		int xc = (x0 + x1) / 2;
		int zc = (z0 + z1) / 2;
		switch (kind) {
			case BEDROOM -> {
				DyeColor c = SOFT[r.nextInt(SOFT.length)];
				if (w >= 4) {
					p.bed(x0 + 1, f, z1 - 1, L.BACK, c);
					p.bed(x0 + 2, f, z1 - 1, L.BACK, c);
					put(p, x0, f, z1, Blocks.BARREL.defaultBlockState());
					put(p, x0, f + 1, z1, Blocks.LANTERN.defaultBlockState());
					put(p, x0 + 3, f, z1, Blocks.BARREL.defaultBlockState());
					put(p, x0 + 1, f, z1 - 2, carpet(SOFT[r.nextInt(SOFT.length)]));
					put(p, x0 + 2, f, z1 - 2, carpet(SOFT[r.nextInt(SOFT.length)]));
				} else {
					p.bed(x0 + 1, f, z1 - 1, L.BACK, c);
					put(p, x0, f, z1, Blocks.BARREL.defaultBlockState());
				}
				// Wardrobe, a desk with a lamp, a plant.
				put(p, x1, f, z1, Blocks.BARREL.defaultBlockState());
				put(p, x1, f + 1, z1, Blocks.BARREL.defaultBlockState());
				put(p, x1, f, z0, top(Blocks.BIRCH_SLAB));
				put(p, x1, f + 1, z0, Blocks.LANTERN.defaultBlockState());
				put(p, x1 - 1, f, z0, p.stairs(Blocks.OAK_STAIRS, L.LEFT, false));
				put(p, x0, f, z0, plant(r));
				ceilingLight(p, r, xc, y + ceil - 3, zc, true, 0.7f);
			}
			case KITCHEN -> {
				// Counter along the back wall: stove, sink, worktop, fridge.
				put(p, x0, f, z1, Blocks.SMOKER.defaultBlockState());
				put(p, x0 + 1, f, z1, Blocks.CAULDRON.defaultBlockState());
				for (int x = x0 + 2; x < x1; x++) {
					put(p, x, f, z1, Blocks.POLISHED_ANDESITE.defaultBlockState());
					put(p, x, f + 1, z1, p.facing(Blocks.SPRUCE_TRAPDOOR, L.FRONT).setValue(BlockStateProperties.HALF,
							net.minecraft.world.level.block.state.properties.Half.TOP));
				}
				put(p, x1, f, z1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
				put(p, x1, f + 1, z1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
				// The table in the middle with four chairs, a fruit bowl (a cake!).
				put(p, xc, f, zc - 1, top(Blocks.DARK_OAK_SLAB));
				put(p, xc, f, zc, top(Blocks.DARK_OAK_SLAB));
				put(p, xc - 1, f, zc - 1, p.stairs(Blocks.OAK_STAIRS, L.LEFT, false));
				put(p, xc - 1, f, zc, p.stairs(Blocks.OAK_STAIRS, L.LEFT, false));
				put(p, xc + 1, f, zc - 1, p.stairs(Blocks.OAK_STAIRS, L.RIGHT, false));
				put(p, xc + 1, f, zc, p.stairs(Blocks.OAK_STAIRS, L.RIGHT, false));
				if (r.nextInt(3) == 0) {
					put(p, xc, f + 1, zc, Blocks.FLOWER_POT.defaultBlockState());
				}
				put(p, x0, f, z0, plant(r));
				ceilingLight(p, r, xc, y + ceil - 3, zc, true, 0.8f);
			}
			case LIVING -> {
				DyeColor c = SOFT[r.nextInt(SOFT.length)];
				Block sofa = Blocks.WOOL_STAIRS.pick(c);
				for (int x = x0 + 1; x <= Math.min(x1 - 1, x0 + 3); x++) {
					put(p, x, f, z1, p.stairs(sofa, L.BACK, false));
				}
				put(p, x0, f, z1, Blocks.WOOL_SLAB.pick(c).defaultBlockState());
				put(p, xc, f, z1 - 2, top(Blocks.SPRUCE_SLAB));
				put(p, xc - 1, f, z1 - 2, carpet(SOFT[r.nextInt(SOFT.length)]));
				put(p, xc + 1, f, z1 - 2, carpet(SOFT[r.nextInt(SOFT.length)]));
				// The TV on its stand, facing the sofa.
				put(p, xc, f, z0, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				put(p, xc, f + 1, z0, Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState());
				put(p, x1, f, z1, Blocks.BOOKSHELF.defaultBlockState());
				put(p, x1, f + 1, z1, Blocks.BOOKSHELF.defaultBlockState());
				put(p, x1, f, z0, Blocks.OAK_FENCE.defaultBlockState());
				put(p, x1, f + 1, z0, Blocks.LANTERN.defaultBlockState());
				put(p, x0, f, z0, plant(r));
				ceilingLight(p, r, xc, y + ceil - 3, zc, true, 0.75f);
			}
			case KIDS -> {
				DyeColor c = new DyeColor[]{DyeColor.PINK, DyeColor.LIGHT_BLUE, DyeColor.LIME, DyeColor.YELLOW}[r.nextInt(4)];
				p.bed(x0, f, z1 - 1, L.BACK, c);
				put(p, x0 + 1, f, z1, p.facing(Blocks.CHEST, L.FRONT));
				for (int x = x0 + 1; x < x1; x++) {
					for (int z = zc - 1; z <= zc; z++) {
						put(p, x, f, z, carpet(r.nextBoolean() ? c : DyeColor.WHITE));
					}
				}
				put(p, x1, f, z1, Blocks.BOOKSHELF.defaultBlockState());
				put(p, x1, f, z0, Blocks.NOTE_BLOCK.defaultBlockState());
				put(p, x0, f, z0, plant(r));
				ceilingLight(p, r, xc, y + ceil - 3, zc, true, 0.6f);
			}
			case OFFICE -> {
				// Rows of desks with screens and chairs, a plant and a water cooler.
				for (int z = z0 + 1; z <= z1 - 1; z += 3) {
					for (int x = x0 + 1; x <= x1 - 1; x += 2) {
						put(p, x, f, z, top(r.nextBoolean() ? Blocks.BIRCH_SLAB : Blocks.DARK_OAK_SLAB));
						put(p, x, f + 1, z, Blocks.STAINED_GLASS_PANE.pick(DyeColor.BLACK).defaultBlockState());
						put(p, x, f, z + 1, p.stairs(Blocks.DARK_OAK_STAIRS, L.BACK, false));
					}
				}
				put(p, x0, f, z0, plant(r));
				put(p, x1, f, z1, plant(r));
				put(p, x1, f, z0, Blocks.SMOOTH_QUARTZ.defaultBlockState());
				put(p, x1, f + 1, z0, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
				for (int x = x0 + 1; x <= x1; x += 3) {
					for (int z = z0 + 1; z <= z1; z += 3) {
						ceilingLight(p, r, x, y + ceil - 3, z, false, 0.85f);
					}
				}
			}
			case MEETING -> {
				for (int x = x0 + 1; x <= x1 - 1; x++) {
					put(p, x, f, zc, top(Blocks.DARK_OAK_SLAB));
					put(p, x, f, zc - 1, p.stairs(Blocks.SPRUCE_STAIRS, L.FRONT, false));
					put(p, x, f, zc + 1, p.stairs(Blocks.SPRUCE_STAIRS, L.BACK, false));
				}
				put(p, x0, f, z0, plant(r));
				put(p, x0, f, z1, plant(r));
				ceilingLight(p, r, xc, y + ceil - 3, zc, false, 0.9f);
			}
			case CLASSROOM -> {
				// The board on the side wall, the teacher's desk, rows of desks facing it.
				for (int z = z0 + 1; z <= z1 - 1; z++) {
					put(p, x0, f + 1, z, Blocks.DYED_TERRACOTTA.pick(DyeColor.GREEN).defaultBlockState());
				}
				put(p, x0 + 1, f, zc, Blocks.LECTERN.defaultBlockState());
				for (int x = x0 + 3; x <= x1 - 1; x += 2) {
					for (int z = z0 + 1; z <= z1 - 1; z += 2) {
						put(p, x, f, z, top(Blocks.BIRCH_SLAB));
						put(p, x + 1, f, z, p.stairs(Blocks.OAK_STAIRS, L.RIGHT, false));
					}
				}
				put(p, x1, f, z1, Blocks.BOOKSHELF.defaultBlockState());
				put(p, x1, f, z0, plant(r));
				for (int x = x0 + 2; x <= x1; x += 4) {
					ceilingLight(p, r, x, y + ceil - 3, zc, false, 1f);
				}
			}
			case WARD -> {
				for (int x = x0; x <= x1; x += 2) {
					p.bed(x, f, z1 - 1, L.BACK, DyeColor.WHITE);
					if (x + 1 <= x1) {
						put(p, x + 1, f, z1, Blocks.STAINED_GLASS_PANE.pick(DyeColor.WHITE).defaultBlockState());
						put(p, x + 1, f + 1, z1, Blocks.STAINED_GLASS_PANE.pick(DyeColor.WHITE).defaultBlockState());
					}
				}
				put(p, x0, f, z0, Blocks.BREWING_STAND.defaultBlockState());
				put(p, x1, f, z0, Blocks.SMOOTH_QUARTZ.defaultBlockState());
				put(p, x1, f + 1, z0, Blocks.STAINED_GLASS.pick(DyeColor.WHITE).defaultBlockState());
				ceilingLight(p, r, xc, y + ceil - 3, zc, false, 1f);
			}
			case SHOP -> {
				// Shelves in rows, the till by the door.
				for (int x = x0 + 1; x <= x1 - 1; x += 3) {
					for (int z = z0 + 2; z <= z1; z++) {
						put(p, x, f, z, Blocks.OAK_SHELF.defaultBlockState());
						put(p, x, f + 1, z, Blocks.BARREL.defaultBlockState());
					}
				}
				put(p, x0, f, z0, Blocks.POLISHED_ANDESITE.defaultBlockState());
				put(p, x0, f + 1, z0, Blocks.LECTERN.defaultBlockState());
				for (int x = x0 + 1; x <= x1; x += 3) {
					ceilingLight(p, r, x, y + ceil - 3, zc, false, 1f);
				}
			}
			case CAFE -> {
				// A counter with the coffee machine and cakes, tables for two.
				for (int x = x0; x <= x1; x++) {
					put(p, x, f, z1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				}
				put(p, x0, f + 1, z1, Blocks.BREWING_STAND.defaultBlockState());
				put(p, x0 + 1, f + 1, z1, Blocks.CAKE.defaultBlockState());
				put(p, x1, f + 1, z1, Blocks.CAKE.defaultBlockState());
				for (int x = x0 + 1; x <= x1 - 1; x += 3) {
					for (int z = z0 + 1; z <= z1 - 2; z += 3) {
						put(p, x, f, z, Blocks.OAK_FENCE.defaultBlockState());
						put(p, x, f + 1, z, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
						put(p, x - 1, f, z, p.stairs(Blocks.SPRUCE_STAIRS, L.LEFT, false));
						put(p, x + 1, f, z, p.stairs(Blocks.SPRUCE_STAIRS, L.RIGHT, false));
					}
				}
				for (int x = x0 + 1; x <= x1; x += 3) {
					ceilingLight(p, r, x, y + ceil - 3, zc, true, 1f);
				}
			}
			case LOBBY -> {
				for (int x = xc - 1; x <= xc + 1; x++) {
					put(p, x, f, z1 - 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
				}
				put(p, xc, f, z1, Blocks.LECTERN.defaultBlockState());
				put(p, x0, f, z0, plant(r));
				put(p, x1, f, z0, plant(r));
				Block sofa = Blocks.WOOL_STAIRS.pick(DyeColor.GRAY);
				put(p, x0, f, zc, p.stairs(sofa, L.LEFT, false));
				put(p, x0, f, zc + 1, p.stairs(sofa, L.LEFT, false));
				ceilingLight(p, r, xc, y + ceil - 3, zc, false, 1f);
			}
			default -> {
			}
		}
	}

	/** One of the rooms of a flat, picked at random (bedrooms more often). */
	static int flatRoom(Random r) {
		int k = r.nextInt(9);
		return k < 3 ? BEDROOM : k < 5 ? KITCHEN : k < 8 ? LIVING : KIDS;
	}
}
