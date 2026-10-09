package com.stasdoto.airdefense.nation;

import java.util.Random;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * 1.28: the pieces the regional styles build with (see {@link StyleDesigns}): storeys, rows of windows, flats inside,
 * roofs flat and steep with dormers, balconies, canopies. Local coordinates as in {@link Blueprints}: x right of the
 * door, z into the building (the front wall at z = 0), y up from the floor.
 */
final class StyleKit {
	private StyleKit() {
	}

	static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	static BlockState concrete(DyeColor d) {
		return Blocks.CONCRETE.pick(d).defaultBlockState();
	}

	static BlockState terra(DyeColor d) {
		return Blocks.DYED_TERRACOTTA.pick(d).defaultBlockState();
	}

	static BlockState wool(DyeColor d) {
		return Blocks.WOOL.pick(d).defaultBlockState();
	}

	static BlockState glass(DyeColor d) {
		return Blocks.STAINED_GLASS.pick(d).defaultBlockState();
	}

	static BlockState pane(DyeColor d) {
		return Blocks.STAINED_GLASS_PANE.pick(d).defaultBlockState();
	}

	static BlockState slab(Block slab, boolean top) {
		return slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	/** Stairs whose high side is towards {@code up}; upside down for the arch over a window. */
	static BlockState stairs(Plan p, Block stairs, L up, boolean upsideDown) {
		return p.stairs(stairs, up, upsideDown);
	}

	/** A trapdoor flat against the wall on the {@code out} side (a shutter, a box), or lying flat (a little ledge). */
	static BlockState shutter(Plan p, Block trapdoor, L out) {
		return trapdoor.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.w(out))
				.setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HALF, Half.TOP);
	}

	static BlockState ledge(Plan p, Block trapdoor, L out) {
		return trapdoor.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.w(out))
				.setValue(BlockStateProperties.OPEN, false).setValue(BlockStateProperties.HALF, Half.TOP);
	}

	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final BlockState WARM = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
	static final BlockState COLD = Blocks.SEA_LANTERN.defaultBlockState();

	/** The foundation and the ground floor over the footprint. */
	static void base(Plan p, int hw, int d, BlockState foundation, BlockState floor) {
		p.fill(-hw, -1, 0, hw, -1, d, foundation);
		p.fill(-hw, 0, 0, hw, 0, d, floor);
	}

	/**
	 * The shell: {@code floors} storeys of {@code h} blocks (floor slab included), walls of {@code wall} (the ground
	 * floor of {@code plinth}), a band of {@code band} round each floor slab. Returns the height of the roof slab.
	 */
	static int shell(Plan p, int hw, int d, int floors, int h, BlockState plinth, BlockState wall, BlockState band, BlockState slab) {
		for (int f = 0; f < floors; f++) {
			int y0 = f * h;
			if (f > 0) {
				p.fill(-hw, y0, 0, hw, y0, d, slab);
				p.ring(-hw, 0, hw, d, y0, band);
			}
			for (int y = y0 + 1; y < y0 + h; y++) {
				p.ring(-hw, 0, hw, d, y, f == 0 ? plinth : wall);
			}
		}
		int top = floors * h;
		p.fill(-hw, top, 0, hw, top, d, slab);
		p.ring(-hw, 0, hw, d, top, band);
		return top;
	}

	/** Windows on one wall: along the front (z = 0) or the back (z = d), every {@code step} blocks from {@code from}, {@code rows} high. */
	static void windowsAlong(Plan p, int hw, int z, int y0, int rows, int step, int from, int width, BlockState win) {
		for (int x = -hw + 1; x <= hw - 1; x++) {
			int o = Math.floorMod(x + hw - from, step);
			if (o < width) {
				for (int y = y0 + 1; y <= y0 + rows; y++) {
					p.set(x, y, z, win);
				}
			}
		}
	}

	/** Windows on the side walls (x = -hw and x = hw). */
	static void windowsSides(Plan p, int hw, int d, int y0, int rows, int step, BlockState win) {
		for (int z = 2; z < d - 1; z++) {
			if (Math.floorMod(z, step) == 0) {
				for (int y = y0 + 1; y <= y0 + rows; y++) {
					p.set(-hw, y, z, win);
					p.set(hw, y, z, win);
				}
			}
		}
	}

	/**
	 * The flats: on every floor from {@code fromFloor}, rooms {@code roomW} wide across the building, furnished, with a
	 * ladder up the back for the stairwell; a parquet in each.
	 */
	static void flats(Plan p, Random r, int hw, int d, int floors, int h, int roomW, BlockState partition, int fromFloor) {
		for (int f = fromFloor; f < floors; f++) {
			int y0 = f * h;
			for (int x = -hw + roomW; x < hw; x += roomW) {
				Interiors.wallX(p, x, y0, 1, d - 1, d / 2, partition);
			}
			for (int x0 = -hw + 1; x0 < hw; x0 += roomW) {
				int x1 = Math.min(x0 + roomW - 2, hw - 1);
				if (f > 0) {
					Interiors.floor(p, r, x0, x1, y0, 1, d - 1);
				}
				Interiors.room(p, r, Interiors.flatRoom(r), x0, x1, y0, 1, d - 1, h);
			}
		}
	}

	/** A flat roof with a parapet one block high (and a cap of {@code cap} on it, if not null). */
	static void flatRoof(Plan p, int hw, int d, int y, BlockState roof, BlockState parapet, BlockState cap) {
		p.fill(-hw, y, 0, hw, y, d, roof);
		p.ring(-hw, 0, hw, d, y + 1, parapet);
		if (cap != null) {
			p.ring(-hw, 0, hw, d, y + 2, cap);
		}
	}

	/**
	 * A steep roof with its ridge from side to side (along the street), and dormer windows on its front slope every
	 * {@code dormerStep} blocks (0 = none).
	 */
	static void steepRoof(Plan p, int hw, int d, int y0, Block stairs, Block ridge, BlockState gable, int dormerStep, BlockState dormerWall) {
		p.roofAlongX(y0, stairs, ridge, gable.getBlock());
		// The gable ends in the wall's own material (roofAlongX builds them of the gable block).
		int hd = d / 2;
		for (int k = 0; k <= hd; k++) {
			for (int z = k; z <= d - k; z++) {
				p.set(-hw, y0 + k, z, gable);
				p.set(hw, y0 + k, z, gable);
			}
		}
		if (dormerStep <= 0 || hd < 3) {
			return;
		}
		for (int x = -hw + 2; x <= hw - 2; x += dormerStep) {
			// A small box standing on the wall's line out of the slope: a window in front, a flat little roof.
			for (int y = y0 + 1; y <= y0 + 2; y++) {
				p.set(x - 1, y, 0, dormerWall);
				p.set(x + 1, y, 0, dormerWall);
				p.set(x, y, 0, b(Blocks.GLASS_PANE));
				p.set(x, y, 1, AIR);
			}
			p.set(x - 1, y0 + 3, 0, stairs(p, stairs, L.RIGHT, false));
			p.set(x, y0 + 3, 0, b(ridge));
			p.set(x + 1, y0 + 3, 0, stairs(p, stairs, L.LEFT, false));
		}
	}

	/** A balcony slab in front of the wall from x0 to x1 at floor height y, with a railing. */
	static void balcony(Plan p, int x0, int x1, int y, BlockState floor, BlockState rail) {
		for (int x = x0; x <= x1; x++) {
			p.set(x, y, -1, floor);
			p.set(x, y + 1, -2, rail);
		}
		p.set(x0, y + 1, -1, rail);
		p.set(x1, y + 1, -1, rail);
	}

	/** A canopy over a door: a slab roof two blocks out, a lamp under it. */
	static void canopy(Plan p, int x0, int x1, int y, BlockState slab, boolean lamp) {
		for (int x = x0; x <= x1; x++) {
			p.set(x, y, -1, slab);
			p.set(x, y, -2, slab);
		}
		if (lamp) {
			p.set((x0 + x1) / 2, y - 1, -2, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
		}
	}

	/** An awning of wool over a shop window from x0 to x1 (sloping down and out), at height y. */
	static void awning(Plan p, int x0, int x1, int y, DyeColor c1, DyeColor c2) {
		for (int x = x0; x <= x1; x++) {
			DyeColor c = Math.floorMod(x, 2) == 0 ? c1 : c2;
			p.set(x, y, -1, wool(c));
			p.set(x, y - 1, -2, Blocks.CARPET.pick(c).defaultBlockState());
		}
	}

	/** Roof clutter: vents, antennas, dishes, a water tank (Architecture's kit). */
	static void roofKit(Plan p, Random r, int x0, int x1, int z0, int z1, int y, int count) {
		Architecture.roofKit(p, r, x0, x1, z0, z1, y, count);
	}

	/** A sign: lit glass letters' band on the front wall from x0 to x1 at height y (a light behind it). */
	static void sign(Plan p, int x0, int x1, int y, DyeColor color) {
		Architecture.lightbox(p, x0, x1, y, -1, color, true);
	}

	/** Pick by variant. */
	@SafeVarargs
	static <T> T pick(int variant, T... options) {
		return options[Math.floorMod(variant, options.length)];
	}
}
