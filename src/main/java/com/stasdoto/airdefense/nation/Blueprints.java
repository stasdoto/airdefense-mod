package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The village buildings, block by block. Each design is drawn in the building's own frame: x to the right of the
 * door, z into the building, y up from the floor (y = 0 is the floor laid over the ground, y = -1 the foundation).
 * {@link #placements} turns a design into the order the builders put it up in: the site cleared first (top down),
 * then the building from the foundation up.
 */
public final class Blueprints {
	private Blueprints() {
	}

	/** One block to set; doors and beds come as pairs (both halves at once, or one would break the other). */
	public record Placement(BlockPos pos, BlockState state, @Nullable BlockPos pos2, @Nullable BlockState state2) {
		public boolean pair() {
			return pos2 != null;
		}
	}

	/** Directions in the building's frame. */
	enum L {
		FRONT(0, -1), BACK(0, 1), RIGHT(1, 0), LEFT(-1, 0);

		final int dx;
		final int dz;

		L(int dx, int dz) {
			this.dx = dx;
			this.dz = dz;
		}

		Direction world(Direction facing) {
			return switch (this) {
				case FRONT -> facing.getOpposite();
				case BACK -> facing;
				case RIGHT -> facing.getClockWise();
				case LEFT -> facing.getCounterClockWise();
			};
		}
	}

	/** A design being drawn, in local coordinates. */
	static final class Plan {
		final Building b;
		final Map<Long, BlockState> blocks = new LinkedHashMap<>();
		/** Pairs: local position of the first half -> {second half's local position (as long), first state, second state}. */
		final Map<Long, Object[]> pairs = new LinkedHashMap<>();
		int minX;
		int maxX;
		int minZ;
		int maxZ;
		int top;

		Plan(Building b) {
			this.b = b;
			int hw = b.type.halfWidth();
			minX = -hw;
			maxX = hw;
			minZ = 0;
			maxZ = b.type.depth - 1;
			top = b.type.height;
		}

		Direction w(L l) {
			return l.world(b.facing);
		}

		void set(int x, int y, int z, BlockState s) {
			blocks.put(BlockPos.asLong(x, y, z), s);
		}

		void set(int x, int y, int z, Block block) {
			set(x, y, z, block.defaultBlockState());
		}

		void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						set(x, y, z, s);
					}
				}
			}
		}

		void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
			fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
		}

		/** The outline of a rectangle at one height. */
		void ring(int x0, int z0, int x1, int z1, int y, BlockState s) {
			for (int x = x0; x <= x1; x++) {
				set(x, y, z0, s);
				set(x, y, z1, s);
			}
			for (int z = z0; z <= z1; z++) {
				set(x0, y, z, s);
				set(x1, y, z, s);
			}
		}

		void remove(int x, int y, int z) {
			blocks.remove(BlockPos.asLong(x, y, z));
		}

		boolean has(int x, int y, int z) {
			return blocks.containsKey(BlockPos.asLong(x, y, z));
		}

		BlockState log(Block block, char axis) {
			Direction.Axis a = switch (axis) {
				case 'x' -> w(L.RIGHT).getAxis();
				case 'z' -> w(L.BACK).getAxis();
				default -> Direction.Axis.Y;
			};
			return block.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
		}

		BlockState stairs(Block block, L up, boolean upsideDown) {
			return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, w(up))
					.setValue(BlockStateProperties.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
		}

		BlockState slab(Block block, boolean upper) {
			return block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, upper ? SlabType.TOP : SlabType.BOTTOM);
		}

		BlockState facing(Block block, L l) {
			return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, w(l));
		}

		/** A door in the front wall, opening into the building. */
		void door(int x, int y, int z, Block block, L in) {
			BlockState lower = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, w(in))
					.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
					.setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT).setValue(BlockStateProperties.OPEN, false);
			BlockState upper = lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
			pairs.put(BlockPos.asLong(x, y, z), new Object[]{BlockPos.asLong(x, y + 1, z), lower, upper});
			blocks.remove(BlockPos.asLong(x, y, z));
			blocks.remove(BlockPos.asLong(x, y + 1, z));
		}

		/** A bed with its foot here and its head towards {@code head}. */
		void bed(int x, int y, int z, L head, DyeColor color) {
			Block block = Blocks.BED.pick(color);
			BlockState foot = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, w(head))
					.setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
			BlockState hd = foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
			pairs.put(BlockPos.asLong(x, y, z), new Object[]{BlockPos.asLong(x + head.dx, y, z + head.dz), foot, hd});
			blocks.remove(BlockPos.asLong(x, y, z));
			blocks.remove(BlockPos.asLong(x + head.dx, y, z + head.dz));
		}

		/** A ladder against the wall on the {@code wall} side. */
		void ladder(int x, int y0, int y1, int z, L wall) {
			for (int y = y0; y <= y1; y++) {
				set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, w(opposite(wall))));
			}
		}

		void wallTorch(int x, int y, int z, L wall) {
			set(x, y, z, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, w(opposite(wall))));
		}

		void lantern(int x, int y, int z, boolean hanging) {
			set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging));
		}

		void chest(int x, int y, int z, L front) {
			set(x, y, z, facing(Blocks.CHEST, front));
		}

		/** A gable roof of stairs with its ridge running front to back, overhanging by one block. */
		void roofAlongZ(int y0, Block stairs, Block ridge, Block gable, boolean gableWindowFront, boolean gableWindowBack) {
			int hw = b.type.halfWidth();
			int d = b.type.depth - 1;
			for (int k = 0; k <= hw; k++) {
				int y = y0 + k;
				int x = hw + 1 - k;
				for (int z = -1; z <= d + 1; z++) {
					set(-x, y, z, stairs(stairs, L.RIGHT, false));
					set(x, y, z, stairs(stairs, L.LEFT, false));
				}
				for (int gx = -(hw - k); gx <= hw - k; gx++) {
					set(gx, y, 0, gable.defaultBlockState());
					set(gx, y, d, gable.defaultBlockState());
				}
			}
			for (int z = -1; z <= d + 1; z++) {
				set(0, y0 + hw, z, ridge.defaultBlockState());
			}
			if (gableWindowFront && hw >= 3) {
				set(0, y0 + 1, 0, Blocks.GLASS_PANE);
			}
			if (gableWindowBack && hw >= 3) {
				set(0, y0 + 1, d, Blocks.GLASS_PANE);
			}
			top = Math.max(top, y0 + hw + 1);
		}

		/** A gable roof with its ridge running from side to side (for long buildings). */
		void roofAlongX(int y0, Block stairs, Block ridge, Block gable) {
			int hw = b.type.halfWidth();
			int d = b.type.depth - 1;
			int hd = d / 2;
			for (int k = 0; k <= hd; k++) {
				int y = y0 + k;
				for (int x = -hw - 1; x <= hw + 1; x++) {
					set(x, y, -1 + k, stairs(stairs, L.BACK, false));
					set(x, y, d + 1 - k, stairs(stairs, L.FRONT, false));
				}
				for (int z = k; z <= d - k; z++) {
					set(-hw, y, z, gable.defaultBlockState());
					set(hw, y, z, gable.defaultBlockState());
				}
			}
			for (int x = -hw - 1; x <= hw + 1; x++) {
				set(x, y0 + hd, hd, ridge.defaultBlockState());
			}
			top = Math.max(top, y0 + hd + 1);
		}
	}

	static L opposite(L l) {
		return switch (l) {
			case FRONT -> L.BACK;
			case BACK -> L.FRONT;
			case RIGHT -> L.LEFT;
			case LEFT -> L.RIGHT;
		};
	}

	// ------------------------------------------------------------------------------------------------
	// The designs

	static Plan design(Building b, DyeColor flag) {
		Plan p = new Plan(b);
		switch (b.type) {
			case SMALL_HOUSE -> smallHouse(p);
			case HOUSE -> house(p);
			case APARTMENTS -> apartments(p);
			case BARRACKS -> barracks(p, flag);
			case HANGAR -> hangar(p);
			case HOSPITAL -> hospital(p);
			case WAREHOUSE -> warehouse(p);
			default -> {
			}
		}
		return p;
	}

	/** Timber house, one room, two beds, a pitched roof. */
	private static void smallHouse(Plan p) {
		int hw = 3;
		int d = 6;
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.COBBLESTONE);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.OAK_PLANKS);
		p.ring(-hw, 0, hw, d, 0, Blocks.COBBLESTONE.defaultBlockState());
		for (int y = 1; y <= 3; y++) {
			p.ring(-hw, 0, hw, d, y, Blocks.OAK_PLANKS.defaultBlockState());
		}
		for (int y = 1; y <= 3; y++) {
			for (int x : new int[]{-hw, hw}) {
				p.set(x, y, 0, p.log(Blocks.OAK_LOG, 'y'));
				p.set(x, y, d, p.log(Blocks.OAK_LOG, 'y'));
			}
		}
		for (int x = -hw + 1; x <= hw - 1; x++) {
			p.set(x, 3, 0, p.log(Blocks.OAK_LOG, 'x'));
			p.set(x, 3, d, p.log(Blocks.OAK_LOG, 'x'));
		}
		for (int x : new int[]{-2, 2}) {
			p.set(x, 2, 0, Blocks.GLASS_PANE);
		}
		for (int x : new int[]{-1, 1}) {
			p.set(x, 2, d, Blocks.GLASS_PANE);
		}
		for (int z : new int[]{2, 4}) {
			p.set(-hw, 2, z, Blocks.GLASS_PANE);
			p.set(hw, 2, z, Blocks.GLASS_PANE);
		}
		p.roofAlongZ(4, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS, true, false);
		p.door(0, 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
		p.bed(-2, 1, 4, L.BACK, DyeColor.RED);
		p.bed(2, 1, 4, L.BACK, DyeColor.LIGHT_BLUE);
		p.chest(0, 1, 5, L.FRONT);
		p.wallTorch(0, 2, 5, L.BACK);
		p.wallTorch(-1, 2, -1, L.BACK);
		p.wallTorch(1, 2, -1, L.BACK);
		p.set(-2, 1, 1, Blocks.CRAFTING_TABLE);
		p.set(2, 1, 1, Blocks.POTTED_RED_TULIP);
	}

	/** Stone ground floor, timber upper floor, a ladder between them, four beds. */
	private static void house(Plan p) {
		int hw = 4;
		int d = 8;
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.STONE_BRICKS);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.SPRUCE_PLANKS);
		p.ring(-hw, 0, hw, d, 0, Blocks.STONE_BRICKS.defaultBlockState());
		for (int y = 1; y <= 3; y++) {
			p.ring(-hw, 0, hw, d, y, Blocks.STONE_BRICKS.defaultBlockState());
		}
		// The upper floor: planks, framed by dark oak beams.
		p.fill(-hw + 1, 4, 1, hw - 1, 4, d - 1, Blocks.SPRUCE_PLANKS);
		for (int x = -hw; x <= hw; x++) {
			p.set(x, 4, 0, p.log(Blocks.DARK_OAK_LOG, 'x'));
			p.set(x, 4, d, p.log(Blocks.DARK_OAK_LOG, 'x'));
		}
		for (int z = 1; z < d; z++) {
			p.set(-hw, 4, z, p.log(Blocks.DARK_OAK_LOG, 'z'));
			p.set(hw, 4, z, p.log(Blocks.DARK_OAK_LOG, 'z'));
		}
		for (int y = 5; y <= 7; y++) {
			p.ring(-hw, 0, hw, d, y, Blocks.SPRUCE_PLANKS.defaultBlockState());
			for (int x : new int[]{-hw, hw}) {
				p.set(x, y, 0, p.log(Blocks.DARK_OAK_LOG, 'y'));
				p.set(x, y, d, p.log(Blocks.DARK_OAK_LOG, 'y'));
			}
		}
		for (int f = 0; f < 2; f++) {
			int y = 2 + f * 4;
			for (int x : new int[]{-3, -2, 2, 3}) {
				p.set(x, y, 0, Blocks.GLASS_PANE);
			}
			for (int x : new int[]{-2, -1, 1, 2}) {
				p.set(x, y, d, Blocks.GLASS_PANE);
			}
			for (int z : new int[]{2, 3, 5, 6}) {
				p.set(-hw, y, z, Blocks.GLASS_PANE);
				p.set(hw, y, z, Blocks.GLASS_PANE);
			}
		}
		p.set(0, 6, 0, Blocks.GLASS_PANE);
		p.roofAlongZ(8, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, true, true);
		p.door(0, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		// Ladder up through a hole in the upper floor.
		p.remove(-3, 4, 7);
		p.ladder(-3, 1, 4, 7, L.LEFT);
		p.bed(2, 1, 6, L.BACK, DyeColor.GREEN);
		p.bed(3, 1, 6, L.BACK, DyeColor.YELLOW);
		p.bed(2, 5, 6, L.BACK, DyeColor.BLUE);
		p.bed(3, 5, 6, L.BACK, DyeColor.ORANGE);
		p.set(-3, 1, 1, Blocks.CRAFTING_TABLE);
		p.set(-3, 1, 2, p.facing(Blocks.FURNACE, L.RIGHT));
		p.chest(-3, 5, 1, L.RIGHT);
		p.lantern(0, 3, 4, true);
		p.wallTorch(0, 6, 7, L.BACK);
		p.wallTorch(-1, 2, -1, L.BACK);
		p.wallTorch(1, 2, -1, L.BACK);
	}

	/** Four floors of flats in white and grey concrete, a ladder shaft, lights in the ceilings, eight beds. */
	private static void apartments(Plan p) {
		int hw = 5;
		int d = 8;
		BlockState wall = Blocks.CONCRETE.white().defaultBlockState();
		BlockState band = Blocks.CONCRETE.lightGray().defaultBlockState();
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.STONE_BRICKS);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.POLISHED_ANDESITE);
		p.ring(-hw, 0, hw, d, 0, Blocks.STONE_BRICKS.defaultBlockState());
		for (int f = 0; f < 4; f++) {
			int base = f * 4;
			if (f > 0) {
				p.fill(-hw, base, 0, hw, base, d, Blocks.SMOOTH_STONE);
				p.ring(-hw, 0, hw, d, base, band);
			}
			for (int y = base + 1; y <= base + 3; y++) {
				p.ring(-hw, 0, hw, d, y, wall);
				for (int x : new int[]{-hw, hw}) {
					p.set(x, y, 0, band);
					p.set(x, y, d, band);
				}
			}
			for (int y = base + 1; y <= base + 2; y++) {
				for (int x : new int[]{-4, -3, -1, 1, 3, 4}) {
					p.set(x, y, 0, Blocks.GLASS_PANE);
					p.set(x, y, d, Blocks.GLASS_PANE);
				}
				for (int z : new int[]{2, 3, 5, 6}) {
					p.set(-hw, y, z, Blocks.GLASS_PANE);
					p.set(hw, y, z, Blocks.GLASS_PANE);
				}
			}
			p.bed(-4, base + 1, 6, L.BACK, DyeColor.WHITE);
			p.bed(-3, base + 1, 6, L.BACK, DyeColor.CYAN);
			p.chest(3, base + 1, 1, L.BACK);
			if (f < 3) {
				p.remove(4, base + 4, 7);
			}
		}
		// Flat roof with a parapet.
		p.fill(-hw, 16, 0, hw, 16, d, band);
		for (int f = 0; f < 4; f++) {
			p.set(-1, f * 4 + 4, 4, Blocks.SEA_LANTERN);
		}
		p.ring(-hw, 0, hw, d, 17, band);
		p.ladder(4, 1, 12, 7, L.RIGHT);
		// The entrance: a door with a canopy over it.
		p.door(0, 1, 0, Blocks.DARK_OAK_DOOR, L.BACK);
		p.set(0, 3, 0, wall);
		for (int x = -1; x <= 1; x++) {
			p.set(x, 3, -1, p.slab(Blocks.SMOOTH_STONE_SLAB, true));
		}
		p.top = 18;
	}

	/** A long stone barracks with a dark tiled roof, bunks for the men and a flagpole in front. */
	private static void barracks(Plan p, DyeColor flag) {
		int hw = 6;
		int d = 6;
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.STONE_BRICKS);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.SPRUCE_PLANKS);
		p.ring(-hw, 0, hw, d, 0, Blocks.STONE_BRICKS.defaultBlockState());
		for (int y = 1; y <= 3; y++) {
			p.ring(-hw, 0, hw, d, y, (y == 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
			for (int x : new int[]{-hw, -2, 2, hw}) {
				p.set(x, y, 0, p.log(Blocks.SPRUCE_LOG, 'y'));
				p.set(x, y, d, p.log(Blocks.SPRUCE_LOG, 'y'));
			}
		}
		for (int x : new int[]{-5, -4, -3, 3, 4, 5}) {
			p.set(x, 2, 0, Blocks.IRON_BARS);
			p.set(x, 2, d, Blocks.IRON_BARS);
		}
		for (int x : new int[]{-1, 0, 1}) {
			p.set(x, 2, d, Blocks.IRON_BARS);
		}
		for (int z : new int[]{2, 4}) {
			p.set(-hw, 2, z, Blocks.IRON_BARS);
			p.set(hw, 2, z, Blocks.IRON_BARS);
		}
		p.roofAlongX(4, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_PLANKS);
		p.door(0, 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
		DyeColor[] beds = {DyeColor.GREEN, DyeColor.BROWN, DyeColor.GREEN, DyeColor.BROWN, DyeColor.GREEN, DyeColor.BROWN};
		int i = 0;
		for (int x : new int[]{-5, -3, -1, 1, 3, 5}) {
			p.bed(x, 1, 4, L.BACK, beds[i++]);
		}
		for (int x : new int[]{-4, 4}) {
			p.wallTorch(x, 2, 5, L.BACK);
		}
		p.chest(4, 1, 1, L.BACK);
		p.chest(5, 1, 1, L.BACK);
		p.set(-5, 1, 1, p.facing(Blocks.ANVIL, L.RIGHT));
		p.set(-4, 1, 1, Blocks.TARGET);
		// Flagpole with the country's banner.
		for (int y = 1; y <= 6; y++) {
			p.set(-5, y, -2, Blocks.SPRUCE_FENCE);
		}
		p.set(-5, 7, -2, Blocks.BANNER.pick(flag).defaultBlockState().setValue(BannerBlock.ROTATION, rotation16(p.w(L.FRONT))));
		p.top = 8;
	}

	/** A big arched hangar: an open gate 11 wide and 6 high, room for any of the vehicles inside. */
	private static void hangar(Plan p) {
		int hw = 7;
		int d = 16;
		int[] h = {12, 12, 12, 11, 11, 10, 9, 7};
		BlockState skin = Blocks.CONCRETE.gray().defaultBlockState();
		BlockState wall = Blocks.CONCRETE.lightGray().defaultBlockState();
		BlockState rib = Blocks.CONCRETE.white().defaultBlockState();
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.STONE_BRICKS);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.SMOOTH_STONE);
		p.ring(-hw, 0, hw, d, 0, Blocks.STONE_BRICKS.defaultBlockState());
		for (int z = 1; z < d; z++) {
			p.set(-5, 0, z, Blocks.CONCRETE.yellow());
			p.set(5, 0, z, Blocks.CONCRETE.yellow());
		}
		for (int z = 0; z <= d; z++) {
			boolean ribZ = z % 4 == 0;
			for (int ax = 0; ax <= hw; ax++) {
				int from = ax == hw ? 1 : Math.min(h[ax], h[ax + 1] + 1);
				for (int y = from; y <= h[ax]; y++) {
					BlockState s = ax == hw ? (ribZ ? rib : wall) : ribZ ? rib : skin;
					p.set(-ax, y, z, s);
					p.set(ax, y, z, s);
				}
			}
			// Skylights along the ridge.
			if (z % 4 == 2) {
				p.set(0, 12, z, Blocks.SEA_LANTERN);
			} else if (!ribZ) {
				p.set(-1, 12, z, Blocks.GLASS);
				p.set(1, 12, z, Blocks.GLASS);
			}
		}
		// End walls: closed at the back, the gate at the front.
		for (int ax = 0; ax <= hw; ax++) {
			for (int y = 1; y <= h[ax]; y++) {
				for (int sx : new int[]{-ax, ax}) {
					p.set(sx, y, d, wall);
					if (ax <= 5 && y <= 6) {
						p.remove(sx, y, 0);
					} else {
						p.set(sx, y, 0, wall);
					}
				}
			}
		}
		for (int x = -5; x <= 5; x++) {
			p.set(x, 7, 0, (x & 1) == 0 ? Blocks.CONCRETE.yellow().defaultBlockState() : Blocks.CONCRETE.black().defaultBlockState());
		}
		// Windows in the walls.
		for (int z : new int[]{3, 4, 7, 8, 11, 12}) {
			p.set(-hw, 3, z, Blocks.GLASS);
			p.set(hw, 3, z, Blocks.GLASS);
			p.set(-hw, 4, z, Blocks.GLASS);
			p.set(hw, 4, z, Blocks.GLASS);
		}
		for (int x : new int[]{-4, -3, -2, 2, 3, 4}) {
			p.set(x, 3, d, Blocks.GLASS);
			p.set(x, 4, d, Blocks.GLASS);
		}
		// Workshop corner, a crane hook in the middle, lamps.
		p.set(-6, 1, 14, p.facing(Blocks.ANVIL, L.RIGHT));
		p.set(-6, 1, 13, Blocks.CRAFTING_TABLE);
		p.set(-6, 1, 12, Blocks.IRON_BLOCK);
		p.set(-6, 2, 12, Blocks.IRON_BLOCK);
		p.chest(6, 1, 14, L.LEFT);
		p.chest(6, 1, 13, L.LEFT);
		p.set(0, 11, 8, Blocks.IRON_CHAIN);
		p.set(0, 10, 8, Blocks.IRON_CHAIN);
		for (int z : new int[]{4, 12}) {
			p.lantern(-4, 10, z, true);
			p.lantern(4, 10, z, true);
		}
		p.top = 13;
	}

	/** Two white floors with a red cross on the front and on the roof; beds upstairs, a doctor's stand downstairs. */
	private static void hospital(Plan p) {
		int hw = 5;
		int d = 8;
		BlockState wall = Blocks.CONCRETE.white().defaultBlockState();
		BlockState band = Blocks.CONCRETE.lightBlue().defaultBlockState();
		BlockState red = Blocks.CONCRETE.red().defaultBlockState();
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.STONE_BRICKS);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.SMOOTH_QUARTZ);
		p.ring(-hw, 0, hw, d, 0, Blocks.STONE_BRICKS.defaultBlockState());
		for (int f = 0; f < 2; f++) {
			int base = f * 4;
			if (f > 0) {
				p.fill(-hw, base, 0, hw, base, d, Blocks.SMOOTH_QUARTZ);
				p.ring(-hw, 0, hw, d, base, band);
			}
			for (int y = base + 1; y <= base + 3; y++) {
				p.ring(-hw, 0, hw, d, y, wall);
			}
			int y = base + 2;
			for (int x : new int[]{-4, -3, 3, 4}) {
				p.set(x, y, 0, Blocks.GLASS_PANE);
			}
			for (int x : new int[]{-3, -1, 1, 3}) {
				p.set(x, y, d, Blocks.GLASS_PANE);
			}
			for (int z : new int[]{2, 3, 5, 6}) {
				p.set(-hw, y, z, Blocks.GLASS_PANE);
				p.set(hw, y, z, Blocks.GLASS_PANE);
			}
		}
		// Red cross on the front, upstairs.
		p.set(0, 5, 0, red);
		p.set(0, 6, 0, red);
		p.set(0, 7, 0, red);
		p.set(-1, 6, 0, red);
		p.set(1, 6, 0, red);
		// Flat roof, a red cross on it (seen from the air), a parapet.
		p.fill(-hw, 8, 0, hw, 8, d, wall);
		for (int z = 2; z <= 6; z++) {
			p.set(0, 8, z, red);
		}
		for (int x = -2; x <= 2; x++) {
			p.set(x, 8, 4, red);
		}
		for (int y : new int[]{4, 8}) {
			p.set(-3, y, 4, Blocks.SEA_LANTERN);
			p.set(3, y, 4, Blocks.SEA_LANTERN);
		}
		p.ring(-hw, 0, hw, d, 9, Blocks.CONCRETE.lightGray().defaultBlockState());
		p.remove(4, 4, 7);
		p.ladder(4, 1, 4, 7, L.RIGHT);
		p.door(0, 1, 0, Blocks.SPRUCE_DOOR, L.BACK);
		p.set(-4, 1, 1, Blocks.BREWING_STAND);
		p.chest(-4, 1, 2, L.RIGHT);
		p.set(-4, 1, 7, Blocks.POTTED_RED_TULIP);
		p.bed(-3, 5, 6, L.BACK, DyeColor.WHITE);
		p.bed(-1, 5, 6, L.BACK, DyeColor.WHITE);
		p.set(-4, 5, 2, Blocks.POTTED_RED_TULIP);
		p.top = 10;
	}

	/** Timber store with a wide gate: log piles, stone and iron, chests. */
	private static void warehouse(Plan p) {
		int hw = 5;
		int d = 8;
		p.fill(-hw, -1, 0, hw, -1, d, Blocks.COBBLESTONE);
		p.fill(-hw, 0, 0, hw, 0, d, Blocks.SPRUCE_PLANKS);
		p.ring(-hw, 0, hw, d, 0, Blocks.COBBLESTONE.defaultBlockState());
		for (int y = 1; y <= 4; y++) {
			p.ring(-hw, 0, hw, d, y, Blocks.SPRUCE_PLANKS.defaultBlockState());
			for (int x : new int[]{-hw, -2, 2, hw}) {
				p.set(x, y, 0, p.log(Blocks.SPRUCE_LOG, 'y'));
			}
			p.set(-hw, y, d, p.log(Blocks.SPRUCE_LOG, 'y'));
			p.set(hw, y, d, p.log(Blocks.SPRUCE_LOG, 'y'));
			p.set(-hw, y, 4, p.log(Blocks.SPRUCE_LOG, 'y'));
			p.set(hw, y, 4, p.log(Blocks.SPRUCE_LOG, 'y'));
		}
		for (int x = -hw + 1; x < hw; x++) {
			p.set(x, 4, 0, p.log(Blocks.SPRUCE_LOG, 'x'));
			p.set(x, 4, d, p.log(Blocks.SPRUCE_LOG, 'x'));
		}
		// The gate (open).
		for (int x = -1; x <= 1; x++) {
			for (int y = 1; y <= 3; y++) {
				p.remove(x, y, 0);
			}
		}
		for (int z : new int[]{2, 6}) {
			p.set(-hw, 3, z, Blocks.GLASS_PANE);
			p.set(hw, 3, z, Blocks.GLASS_PANE);
		}
		p.set(-3, 3, d, Blocks.GLASS_PANE);
		p.set(3, 3, d, Blocks.GLASS_PANE);
		p.roofAlongZ(5, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, false, false);
		// Stock: logs, stone, iron.
		for (int z = 2; z <= 6; z++) {
			for (int x : new int[]{-4, -3}) {
				p.set(x, 1, z, p.log(Blocks.OAK_LOG, 'z'));
				if (z <= 5) {
					p.set(x, 2, z, p.log(Blocks.SPRUCE_LOG, 'z'));
				}
			}
		}
		for (int z = 2; z <= 4; z++) {
			p.set(3, 1, z, Blocks.COBBLESTONE);
			p.set(4, 1, z, Blocks.COBBLESTONE);
			p.set(4, 2, z, Blocks.STONE);
		}
		p.set(3, 1, 6, Blocks.IRON_BLOCK);
		p.set(4, 1, 6, Blocks.RAW_IRON_BLOCK);
		p.set(4, 2, 6, Blocks.IRON_BLOCK);
		p.chest(-1, 1, 7, L.FRONT);
		p.chest(1, 1, 7, L.FRONT);
		p.set(0, 1, 7, Blocks.HAY_BLOCK);
		p.wallTorch(0, 3, 7, L.BACK);
		p.wallTorch(-3, 3, -1, L.BACK);
		p.wallTorch(3, 3, -1, L.BACK);
	}

	/** Banner rotation (0-15) for a banner whose face looks towards {@code dir}. */
	static int rotation16(Direction dir) {
		return switch (dir) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
	}

	// ------------------------------------------------------------------------------------------------
	// Building order

	/** The design as the builders put it up: the site cleared first (top down), then from the foundation up. */
	public static List<Placement> placements(Building b, DyeColor flag) {
		Plan p = design(b, flag);
		List<Placement> out = new ArrayList<>();
		for (int y = p.top + 1; y >= 1; y--) {
			for (int x = p.minX - 1; x <= p.maxX + 1; x++) {
				for (int z = p.minZ - 1; z <= p.maxZ + 1; z++) {
					long key = BlockPos.asLong(x, y, z);
					if (!p.blocks.containsKey(key) && !p.pairs.containsKey(key) && !pairSecond(p, key)) {
						out.add(new Placement(b.at(x, y, z), Blocks.AIR.defaultBlockState(), null, null));
					}
				}
			}
		}
		List<long[]> order = new ArrayList<>();
		for (long key : p.blocks.keySet()) {
			order.add(new long[]{key, 0});
		}
		for (long key : p.pairs.keySet()) {
			order.add(new long[]{key, 1});
		}
		order.sort(Comparator.comparingInt((long[] e) -> BlockPos.getY(e[0]))
				.thenComparingInt(e -> e[1] == 1 ? 1 : 0)
				.thenComparingInt(e -> Math.abs(BlockPos.getX(e[0])) + BlockPos.getZ(e[0])));
		for (long[] e : order) {
			int x = BlockPos.getX(e[0]);
			int y = BlockPos.getY(e[0]);
			int z = BlockPos.getZ(e[0]);
			if (e[1] == 0) {
				BlockState s = p.blocks.get(e[0]);
				if (!s.isAir()) {
					out.add(new Placement(b.at(x, y, z), s, null, null));
				}
			} else {
				Object[] pair = p.pairs.get(e[0]);
				long k2 = (Long) pair[0];
				out.add(new Placement(b.at(x, y, z), (BlockState) pair[1], b.at(BlockPos.getX(k2), BlockPos.getY(k2), BlockPos.getZ(k2)),
						(BlockState) pair[2]));
			}
		}
		return out;
	}

	private static boolean pairSecond(Plan p, long key) {
		for (Object[] pair : p.pairs.values()) {
			if ((Long) pair[0] == key) {
				return true;
			}
		}
		return false;
	}

	/** Blocks construction leaves alone: unbreakable ones and anything holding contents. */
	public static boolean protectedBlock(BlockState state) {
		return state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.END_PORTAL_FRAME) || state.hasBlockEntity()
				|| state.is(Blocks.BELL);
	}
}
