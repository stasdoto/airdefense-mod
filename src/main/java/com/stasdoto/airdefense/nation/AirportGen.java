package com.stasdoto.airdefense.nation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.stasdoto.airdefense.street.StreetBlock;
import com.stasdoto.airdefense.street.StreetBlocks;
import com.stasdoto.airdefense.street.StreetPoleBlock;

/**
 * 1.40: builds an airport ({@link Airports.Airport}) as the land is made: the levelled ground inside the fence, the
 * runway with its markings and edge lights, the taxiway and the links to it, the apron with its stands, the terminal,
 * the control tower, the hangar, the car park; the ground outside blended back into the land.
 */
final class AirportGen {
	private static final BlockState RUNWAY = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState ASPHALT = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState WHITE = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	private static final BlockState YELLOW = Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState();
	private static final BlockState APRON = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState FENCE = Blocks.IRON_BARS.defaultBlockState();
	private static final BlockState WALL = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	private static final BlockState FRAME = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
	private static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();
	private static final BlockState ROOF = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState METAL = Blocks.IRON_BLOCK.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** The hangar, beside the apron (off its end, where no plane parks), its doors towards the taxiway. */
	static final int HANGAR_U0 = Airports.APRON_U0 - 50;
	static final int HANGAR_U1 = Airports.APRON_U0 - 6;
	static final int HANGAR_V0 = Airports.APRON_V0 + 8;
	static final int HANGAR_V1 = Airports.APRON_V0 + 44;

	private AirportGen() {
	}

	/** The surface of the airport at (u, v) inside the fence. */
	private static BlockState surface(int u, int v) {
		int av = Math.abs(v);
		if (u >= 0 && u <= Airports.RUNWAY && av <= Airports.RW_HALF) {
			if (av == Airports.RW_HALF - 1) {
				return WHITE;
			}
			int fromEnd = Math.min(u, Airports.RUNWAY - u);
			// The threshold's piano keys, the touchdown zone, the aiming point, the dashed centre line.
			if (fromEnd >= 4 && fromEnd <= 16 && av <= Airports.RW_HALF - 3 && Math.floorMod(v, 4) < 2) {
				return WHITE;
			}
			if (fromEnd >= 60 && fromEnd <= 74 && av >= 5 && av <= 7) {
				return WHITE;
			}
			if (fromEnd >= 100 && fromEnd <= 124 && av >= 4 && av <= 8) {
				return WHITE;
			}
			if (av == 0 && fromEnd >= 26 && Math.floorMod(u, 24) < 12) {
				return WHITE;
			}
			return RUNWAY;
		}
		if (u >= Airports.U0 + 4 && u < 0 && av <= Airports.RW_HALF) {
			// The blast pad before the threshold, its yellow chevrons.
			return Math.floorMod(-u + av, 8) == 0 ? YELLOW : ASPHALT;
		}
		if (u > Airports.RUNWAY && u <= Airports.U1 - 4 && av <= Airports.RW_HALF) {
			return Math.floorMod(u - Airports.RUNWAY + av, 8) == 0 ? YELLOW : ASPHALT;
		}
		boolean taxi = Math.abs(v - Airports.TAXI_V) <= Airports.TAXI_HALF && u >= Airports.LINK_A - Airports.TAXI_HALF
				&& u <= Airports.LINK_B + Airports.TAXI_HALF;
		for (int link : new int[]{Airports.LINK_A, Airports.LINK_B}) {
			if (Math.abs(u - link) <= Airports.TAXI_HALF && v > Airports.RW_HALF && v <= Airports.TAXI_V) {
				return u == link ? YELLOW : ASPHALT;
			}
		}
		if (taxi) {
			return v == Airports.TAXI_V ? YELLOW : ASPHALT;
		}
		if (v > Airports.APRON_V0 && v <= Airports.APRON_V1 && u >= Airports.APRON_U0 && u <= Airports.APRON_U1) {
			// The stands' lead-in lines, nose in towards the terminal; a line along the front of the stands.
			for (int k = 0; k < Airports.STANDS; k++) {
				if (u == Airports.standU(k) && v <= Airports.STAND_V) {
					return YELLOW;
				}
			}
			if (v == Airports.APRON_V1 - 4) {
				return Math.floorMod(u, 4) < 2 ? YELLOW : APRON;
			}
			return APRON;
		}
		if (u >= HANGAR_U0 - 2 && u < Airports.APRON_U0 && v > Airports.APRON_V0 && v < HANGAR_V0) {
			// The hard standing in front of the hangar.
			return APRON;
		}
		if (v >= Airports.TERMINAL_V1 + 2 && v <= Airports.V1 - 2 && Math.abs(u - Airports.MID) <= 44) {
			// The car park: rows of bays.
			int du = Math.abs(u - Airports.MID);
			boolean lane = v >= Airports.TERMINAL_V1 + 9 && v <= Airports.TERMINAL_V1 + 14 || du <= 3;
			return !lane && Math.floorMod(u, 3) == 0 ? WHITE : ASPHALT;
		}
		if (Math.abs(u - Airports.MID) <= 3 && v > Airports.TERMINAL_V1 && v <= Airports.V1) {
			return ASPHALT;
		}
		return GRASS;
	}

	/** A column of the airport (inside the fence when {@code inside}, else the ground blended round it). */
	static boolean column(CityGen.Writer w, Airports.Airport a, int x, int z, boolean inside, BlockPos.MutableBlockPos pos) {
		int o = a.out(x, z);
		if (inside ? o > 0 : o == 0 || o > Airports.BLEND) {
			return false;
		}
		int[] g = CityGen.ground(w, x, z, pos);
		if (o == 0) {
			int[] l = a.local(x, z);
			int u = l[0];
			int v = l[1];
			CityGen.shape(w, x, z, a.y, surface(u, v), g, pos);
			boolean edge = u == Airports.U0 || u == Airports.U1 || v == Airports.V0 || v == Airports.V1;
			boolean gate = Math.abs(u - Airports.MID) <= 3 && v == Airports.V1;
			if (edge && !gate) {
				w.set(pos.set(x, a.y + 1, z), FENCE);
				w.set(pos.set(x, a.y + 2, z), FENCE);
			}
			// The runway's edge lights, every twenty blocks; the threshold lights across its ends (green).
			if (Math.abs(v) == Airports.RW_HALF + 2 && u >= 0 && u <= Airports.RUNWAY && u % 20 == 0) {
				w.set(pos.set(x, a.y + 1, z), StreetBlocks.RUNWAY_LIGHT.defaultBlockState());
			}
			if ((u == -2 || u == Airports.RUNWAY + 2) && Math.abs(v) <= Airports.RW_HALF && v % 4 == 0) {
				w.set(pos.set(x, a.y + 1, z), StreetBlocks.RUNWAY_LIGHT.defaultBlockState());
			}
			// The markers the planes find the airport by: the thresholds, the stands.
			Direction du = Direction.getApproximateNearest(a.ux, 0, a.uz);
			if (v == 0 && u == 0) {
				w.set(pos.set(x, a.y, z), StreetBlocks.THRESHOLD.defaultBlockState().setValue(StreetBlock.FACING, du));
			} else if (v == 0 && u == Airports.RUNWAY) {
				w.set(pos.set(x, a.y, z), StreetBlocks.THRESHOLD.defaultBlockState().setValue(StreetBlock.FACING, du.getOpposite()));
			}
			for (int k = 0; k < Airports.STANDS; k++) {
				if (u == Airports.standU(k) && v == Airports.STAND_V) {
					w.set(pos.set(x, a.y, z), StreetBlocks.STAND.defaultBlockState().setValue(StreetBlock.FACING,
							Direction.getApproximateNearest(a.vx, 0, a.vz)).setValue(com.stasdoto.airdefense.street.StandBlock.INDEX, k));
				}
			}
			return true;
		}
		if (g[2] == 1) {
			return false;
		}
		double f = o / (double) (Airports.BLEND + 1);
		f = f * f * (3 - 2 * f);
		int target = (int) Math.round(a.y * (1 - f) + g[0] * f);
		if (target != g[0]) {
			BlockState top = w.get(pos.set(x, g[0], z));
			BlockState surface = top.is(BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK) || top.is(BlockTags.DIRT) ? top : GRASS;
			CityGen.shape(w, x, z, target, surface, g, pos);
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------
	// The buildings, drawn in the airport's frame: (u, v, height above the ground) -> the world

	private static final class Frame {
		final CityGen.Writer w;
		final Airports.Airport a;
		final ChunkPos cp;
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Frame(CityGen.Writer w, Airports.Airport a, ChunkPos cp) {
			this.w = w;
			this.a = a;
			this.cp = cp;
		}

		void set(int u, int v, int h, BlockState s) {
			int[] p = a.world(u, v);
			if (p[0] < cp.getMinBlockX() || p[0] > cp.getMaxBlockX() || p[1] < cp.getMinBlockZ() || p[1] > cp.getMaxBlockZ()) {
				return;
			}
			w.set(pos.set(p[0], a.y + h, p[1]), s);
		}

		void fill(int u0, int v0, int h0, int u1, int v1, int h1, BlockState s) {
			for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
				for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++) {
					int[] p = a.world(u, v);
					if (p[0] < cp.getMinBlockX() || p[0] > cp.getMaxBlockX() || p[1] < cp.getMinBlockZ() || p[1] > cp.getMaxBlockZ()) {
						continue;
					}
					for (int h = h0; h <= h1; h++) {
						w.set(pos.set(p[0], a.y + h, p[1]), s);
					}
				}
			}
		}

		/** A direction in the airport's frame: +u, -u, +v, -v. */
		Direction dir(int du, int dv) {
			return Direction.getApproximateNearest(a.ux * du + a.vx * dv, 0, a.uz * du + a.vz * dv);
		}
	}

	/** The airport's buildings and things in this chunk. */
	static void build(CityGen.Writer w, Airports.Airport a, ChunkPos cp) {
		int[] lo = a.local(cp.getMinBlockX(), cp.getMinBlockZ());
		int[] hi = a.local(cp.getMaxBlockX(), cp.getMaxBlockZ());
		int u0 = Math.min(lo[0], hi[0]);
		int u1 = Math.max(lo[0], hi[0]);
		int v0 = Math.min(lo[1], hi[1]);
		int v1 = Math.max(lo[1], hi[1]);
		if (v1 < Airports.APRON_V1 - 40 && !(u0 <= 40 && u1 >= -10) && !(u0 <= Airports.RUNWAY + 10 && u1 >= Airports.RUNWAY - 40)) {
			return;
		}
		Frame f = new Frame(w, a, cp);
		terminal(f);
		tower(f);
		hangar(f);
		// Windsocks by both ends of the runway, on the far side from the apron.
		for (int u : new int[]{30, Airports.RUNWAY - 30}) {
			for (int h = 1; h <= 4; h++) {
				f.set(u, -Airports.RW_HALF - 7, h, StreetBlocks.POLE_STEEL.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, h == 1));
			}
			f.set(u, -Airports.RW_HALF - 7, 5, StreetBlocks.WINDSOCK.defaultBlockState().setValue(StreetBlock.FACING, f.dir(1, 0)));
		}
		// Floodlight masts along the back of the apron.
		for (int u = Airports.APRON_U0 + 10; u <= Airports.APRON_U1; u += 40) {
			for (int h = 1; h <= 9; h++) {
				f.set(u, Airports.APRON_V1 - 1, h, StreetBlocks.POLE_STEEL.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, h == 1));
			}
			f.set(u, Airports.APRON_V1 - 1, 10, StreetBlocks.FLOODLIGHT.defaultBlockState().setValue(StreetBlock.FACING, f.dir(0, -1)));
		}
	}

	/** The terminal: a long low hall of glass between white piers, its airside wall all glass, a flat roof, the sign. */
	private static void terminal(Frame f) {
		int ua = Airports.MID - 60;
		int ub = Airports.MID + 60;
		int va = Airports.TERMINAL_V0;
		int vb = Airports.TERMINAL_V1;
		int top = 11;
		f.fill(ua, va, 0, ub, vb, 0, Blocks.POLISHED_ANDESITE.defaultBlockState());
		for (int u = ua; u <= ub; u++) {
			for (int h = 1; h < top; h++) {
				boolean pier = Math.floorMod(u - ua, 8) == 0 || u == ua || u == ub;
				boolean floor = h == 6;
				// Airside: glass from end to end; landside: glass between the piers, a band at the floor.
				f.set(u, va, h, pier || floor ? FRAME : GLASS);
				f.set(u, vb, h, pier || floor ? WALL : GLASS);
			}
		}
		for (int v = va; v <= vb; v++) {
			for (int h = 1; h < top; h++) {
				f.set(ua, v, h, WALL);
				f.set(ub, v, h, WALL);
			}
		}
		// The first floor inside, the roof, a parapet.
		f.fill(ua + 1, va + 1, 6, ub - 1, vb - 1, 6, Blocks.SMOOTH_STONE.defaultBlockState());
		f.fill(ua - 1, va - 2, top, ub + 1, vb + 1, top, ROOF);
		// Inside: clear (the land may have been under it) - the hall's columns.
		f.fill(ua + 1, va + 1, 1, ub - 1, vb - 1, 5, AIR);
		f.fill(ua + 1, va + 1, 7, ub - 1, vb - 1, top - 1, AIR);
		for (int u = ua + 8; u < ub; u += 16) {
			f.fill(u, (va + vb) / 2, 1, u, (va + vb) / 2, top - 1, FRAME);
		}
		// Doors on the landside, the canopy over them, the sign on the roof.
		f.fill(Airports.MID - 3, vb, 1, Airports.MID + 3, vb, 4, AIR);
		f.fill(Airports.MID - 6, vb + 1, 5, Airports.MID + 6, vb + 4, 5, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		f.set(Airports.MID, vb, top + 1, StreetBlocks.AIRPORT_SIGN.defaultBlockState().setValue(StreetBlock.FACING, f.dir(0, 1)));
		f.set(Airports.MID, va, top + 1, StreetBlocks.AIRPORT_SIGN.defaultBlockState().setValue(StreetBlock.FACING, f.dir(0, -1)));
		// Benches and bins along the landside walk.
		for (int u = ua + 6; u <= ub - 6; u += 12) {
			if (Math.abs(u - Airports.MID) > 6) {
				f.set(u, vb + 2, 1, StreetBlocks.BENCH_MODERN.defaultBlockState().setValue(StreetBlock.FACING, f.dir(0, 1)));
			}
		}
	}

	/** The control tower at the end of the terminal: a base, a tall shaft, the glazed cab, the antenna. */
	private static void tower(Frame f) {
		int uc = Airports.MID + 76;
		int vc = Airports.TERMINAL_V0 + 8;
		f.fill(uc - 4, vc - 4, 0, uc + 4, vc + 4, 0, Blocks.POLISHED_ANDESITE.defaultBlockState());
		f.fill(uc - 4, vc - 4, 1, uc + 4, vc + 4, 5, WALL);
		f.fill(uc - 3, vc - 3, 1, uc + 3, vc + 3, 5, AIR);
		f.fill(uc - 4, vc - 1, 2, uc - 4, vc + 1, 4, GLASS);
		f.fill(uc - 2, vc - 2, 6, uc + 2, vc + 2, 26, WALL);
		for (int h = 8; h <= 24; h += 4) {
			f.set(uc - 2, vc, h, GLASS);
			f.set(uc + 2, vc, h, GLASS);
		}
		f.fill(uc - 4, vc - 4, 27, uc + 4, vc + 4, 27, FRAME);
		f.fill(uc - 4, vc - 4, 28, uc + 4, vc + 4, 31, GLASS);
		f.fill(uc - 3, vc - 3, 28, uc + 3, vc + 3, 31, AIR);
		f.fill(uc - 5, vc - 5, 32, uc + 5, vc + 5, 32, FRAME);
		for (int h = 33; h <= 37; h++) {
			f.set(uc, vc, h, FENCE);
		}
		f.set(uc, vc, 38, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
	}

	/** The hangar at the other end of the apron: a big shed with a curved roof, its doors open onto the apron. */
	private static void hangar(Frame f) {
		int ua = HANGAR_U0;
		int ub = HANGAR_U1;
		int va = HANGAR_V0;
		int vb = HANGAR_V1;
		int wall = 9;
		for (int v = va; v <= vb; v++) {
			for (int h = 1; h <= wall; h++) {
				f.set(ua, v, h, METAL);
				f.set(ub, v, h, METAL);
			}
		}
		for (int u = ua; u <= ub; u++) {
			for (int h = 1; h <= wall; h++) {
				f.set(u, vb, h, METAL);
			}
		}
		f.fill(ua + 1, va, 1, ub - 1, vb - 1, wall + 7, AIR);
		// The curved roof: rising in steps to the ridge along u.
		int half = (ub - ua) / 2;
		for (int u = ua; u <= ub; u++) {
			int d = Math.abs(u - (ua + half));
			double t = 1 - (double) d / half;
			int h = wall + 1 + (int) Math.round(Math.sqrt(Math.max(0, t)) * 6);
			BlockState s = Blocks.CONCRETE.pick(DyeColor.CYAN).defaultBlockState();
			for (int v = va; v <= vb; v++) {
				f.set(u, v, h, s);
			}
		}
		// The doors' frame along the apron side, its two halves slid open.
		for (int u = ua; u <= ub; u++) {
			f.set(u, va, wall + 1, FRAME);
		}
		f.fill(ua + 1, va, 1, ua + 3, va, wall, METAL);
		f.fill(ub - 3, va, 1, ub - 1, va, wall, METAL);
		f.fill(ua + 1, va + 1, 0, ub - 1, vb - 1, 0, APRON);
	}

	@SuppressWarnings("unused")
	private static BlockState stair(Direction d) {
		return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, Half.BOTTOM);
	}
}
