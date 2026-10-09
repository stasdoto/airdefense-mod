package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.stasdoto.airdefense.street.StreetBlock;
import com.stasdoto.airdefense.street.StreetBlocks;
import com.stasdoto.airdefense.street.StreetPoleBlock;

/**
 * Everything in a city's streets and yards that is not a building, worked out once per city from its plan: street
 * lamps every few metres, traffic lights at the crossings, trees and benches, bins, bus stops, manholes; in the yards
 * parking places with cars, playgrounds, basketball courts, flower beds, kiosks, ice cream carts, billboards; fences,
 * driveways and cars at the villas, a few cats; a fountain and flags behind the city hall. Blocks are grouped by chunk,
 * so {@link CityGen} puts in a chunk's share as the chunk generates.
 */
final class CityDecor {
	/** One block; {@code force} = replace what is there (ground surfaces), otherwise only into air. */
	record D(int x, int y, int z, BlockState s, boolean force) {
	}

	/** A creature to put in the yard: 0 = cat. */
	record Spawn(int x, int y, int z, int kind) {
	}

	static final class Result {
		final Map<Long, List<D>> blocks = new HashMap<>();
		final Map<Long, List<Spawn>> spawns = new HashMap<>();
		/** Trunks and leaves of the trees put here (the clean-up of wild trees leaves them alone). */
		final Set<Long> trees = new HashSet<>();
	}

	private static final int FREE = 0;
	private static final int BUILDING = 1;
	private static final int FRONT = 2;
	private static final int DOOR = 3;

	private final Cities.City c;
	private final Random r;
	private final int base;
	private final Result out = new Result();
	private final Set<Long> used = new HashSet<>();

	/** 1.35: for the new street furniture's own choices (the old {@link #r} keeps its sequence: old towns stay the same). */
	private final Random r2;

	private CityDecor(Cities.City c) {
		this.c = c;
		this.r = new Random(c.seed ^ 0xDEC0L);
		this.r2 = new Random(c.seed ^ 0x57EE7_F00DL);
		this.base = c.base;
	}

	// ------------------------------------------------------------------------------------------------
	// 1.35: the street furniture of the town's style

	private static BlockState st(Block b, Direction f) {
		return b.defaultBlockState().setValue(StreetBlock.FACING, f);
	}

	private static BlockState pole(Block b, boolean bottom) {
		return b.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, bottom);
	}

	/** A pole of {@code h} blocks from the ground up, with {@code top} (facing {@code f}) on it. */
	private void onPole(int x, int z, Block pole, int h, Block top, Direction f) {
		for (int y = 1; y <= h; y++) {
			put(x, base + y, z, pole(pole, y == 1));
		}
		put(x, base + h + 1, z, st(top, f));
	}

	private CityStyle look() {
		return c.style == CityStyle.CLASSIC ? CityStyle.AMERICAN : c.style;
	}

	private Block lampPole() {
		return switch (look()) {
			case SOVIET -> StreetBlocks.POLE_CONCRETE;
			case EUROPEAN -> StreetBlocks.POLE_GREEN;
			case DESERT -> StreetBlocks.POLE_BLACK;
			default -> StreetBlocks.POLE_STEEL;
		};
	}

	private Block lampHead() {
		return switch (look()) {
			case SOVIET -> StreetBlocks.LAMP_COBRA;
			case EUROPEAN, DESERT -> StreetBlocks.LAMP_LANTERN;
			default -> StreetBlocks.LAMP_MODERN;
		};
	}

	private int lampHeight() {
		return switch (look()) {
			case EUROPEAN, DESERT -> 3;
			default -> 4;
		};
	}

	private Block bench() {
		return switch (look()) {
			case SOVIET -> StreetBlocks.BENCH_SOVIET;
			case AMERICAN -> StreetBlocks.BENCH_MODERN;
			default -> StreetBlocks.BENCH_PARK;
		};
	}

	private Block bin() {
		return switch (look()) {
			case SOVIET -> StreetBlocks.BIN_SOVIET;
			case EUROPEAN -> StreetBlocks.BIN_EURO;
			default -> StreetBlocks.BIN_MODERN;
		};
	}

	private Block mailbox() {
		return switch (look()) {
			case SOVIET -> StreetBlocks.MAILBOX_SOVIET;
			case AMERICAN -> StreetBlocks.MAILBOX_US;
			default -> StreetBlocks.MAILBOX_EURO;
		};
	}

	static Result build(Cities.City c) {
		CityDecor d = new CityDecor(c);
		d.all();
		return d.out;
	}

	// ------------------------------------------------------------------------------------------------
	// Output

	private void put(int x, int y, int z, BlockState s) {
		add(x, y, z, s, false);
	}

	private void force(int x, int y, int z, BlockState s) {
		add(x, y, z, s, true);
	}

	private void add(int x, int y, int z, BlockState s, boolean force) {
		out.blocks.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new ArrayList<>()).add(new D(x, y, z, s, force));
	}

	private void tree(int x, int y, int z, BlockState s) {
		put(x, y, z, s);
		out.trees.add(BlockPos.asLong(x, y, z));
	}

	private void spawn(int x, int z, int kind) {
		out.spawns.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new ArrayList<>()).add(new Spawn(x, base + 1, z, kind));
	}

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState c(DyeColor d) {
		return Blocks.CONCRETE.pick(d).defaultBlockState();
	}

	private static BlockState stairs(Block block, Direction back) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, back).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}

	private static BlockState wallThing(Block block, Direction out) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out);
	}

	private static BlockState slabTop(Block block) {
		return block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	// ------------------------------------------------------------------------------------------------
	// The lots

	/** A lot with what stands on each of its cells (its inside plus the pavement round it). */
	private final class View {
		final CityShape.Lot lot;
		final int[][] g;
		final List<Building> here = new ArrayList<>();

		View(CityShape.Lot lot, List<Building> all) {
			this.lot = lot;
			this.g = new int[lot.width() + 2][lot.depth() + 2];
			for (Building b : all) {
				int reach = Math.max(b.type.width, b.type.depth) + 4;
				if (b.origin.getX() >= lot.x0 - reach && b.origin.getX() <= lot.x1 + reach && b.origin.getZ() >= lot.z0 - reach
						&& b.origin.getZ() <= lot.z1 + reach) {
					here.add(b);
				}
			}
			for (Building b : here) {
				int hw = b.type.halfWidth();
				Direction right = b.facing.getClockWise();
				int front = b.type == BuildingType.CITY_HALL ? 4 : 2;
				List<Integer> doors = doors(b);
				for (int x = lot.x0 - 1; x <= lot.x1 + 1; x++) {
					for (int z = lot.z0 - 1; z <= lot.z1 + 1; z++) {
						int dx = x - b.origin.getX();
						int dz = z - b.origin.getZ();
						int lx = dx * right.getStepX() + dz * right.getStepZ();
						int lz = dx * b.facing.getStepX() + dz * b.facing.getStepZ();
						int k = 0;
						if (Math.abs(lx) <= hw + 1 && lz >= 0 && lz <= b.type.depth) {
							k = BUILDING;
						} else if (Math.abs(lx) <= hw + 1 && lz >= -front && lz < 0) {
							k = FRONT;
							for (int dxs : doors) {
								if (Math.abs(lx - dxs) <= 2) {
									k = DOOR;
								}
							}
						}
						int gi = x - lot.x0 + 1;
						int gj = z - lot.z0 + 1;
						g[gi][gj] = Math.max(g[gi][gj], k);
					}
				}
			}
		}

		int at(int x, int z) {
			int gi = x - lot.x0 + 1;
			int gj = z - lot.z0 + 1;
			if (gi < 0 || gj < 0 || gi >= g.length || gj >= g[0].length) {
				return BUILDING;
			}
			return g[gi][gj];
		}

		boolean free(int x, int z) {
			return at(x, z) == FREE && !used.contains(BlockPos.asLong(x, 0, z));
		}

		/** A free w x h rectangle inside the lot (off its garden edge), or null. */
		int[] rect(int w, int h) {
			int ax = lot.x0 + 1;
			int az = lot.z0 + 1;
			int sx = lot.x1 - 1 - w + 2 - ax;
			int sz = lot.z1 - 1 - h + 2 - az;
			if (sx <= 0 || sz <= 0) {
				return null;
			}
			int total = sx * sz;
			int start = r.nextInt(total);
			for (int k = 0; k < total; k++) {
				int idx = (start + k) % total;
				int x0 = ax + idx % sx;
				int z0 = az + idx / sx;
				boolean ok = true;
				for (int x = x0; x < x0 + w && ok; x++) {
					for (int z = z0; z < z0 + h && ok; z++) {
						ok = free(x, z);
					}
				}
				if (ok) {
					use(x0, z0, x0 + w - 1, z0 + h - 1);
					return new int[]{x0, z0};
				}
			}
			return null;
		}
	}

	/** Local x of a building's doors (all of the front for those that vehicles drive into). */
	private static List<Integer> doors(Building b) {
		List<Integer> d = new ArrayList<>();
		int hw = b.type.halfWidth();
		switch (b.type) {
			case PANEL5, PANEL9, APARTMENTS -> {
				for (int e : Architecture.panelEntrances(hw)) {
					d.add(e);
					d.add(e + 1);
				}
			}
			case TOWER, SHOP -> {
				d.add(0);
				d.add(1);
			}
			case OFFICE -> d.add(0);
			case COTTAGE, HOUSE, SMALL_HOUSE -> d.add(1);
			case SCHOOL, HOSPITAL -> {
				for (int x = -4; x <= 4; x++) {
					d.add(x);
				}
			}
			default -> {
				for (int x = -hw; x <= hw; x++) {
					d.add(x);
				}
			}
		}
		return d;
	}

	private void use(int x0, int z0, int x1, int z1) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				used.add(BlockPos.asLong(x, 0, z));
			}
		}
	}

	private void all() {
		CityShape sh = c.shape();
		List<Building> buildings = c.buildings();
		for (CityShape.Lot lot : sh.lots) {
			View v = new View(lot, buildings);
			streets(sh, v);
			switch (lot.district) {
				case CityShape.HALL -> square(v);
				case CityShape.PARK -> parkEdge(v);
				case CityShape.VACANT -> vacant(v);
				case CityShape.INDUSTRY -> industry(v);
				case CityShape.OUTER -> {
					villas(v);
					yard(v);
				}
				default -> yard(v);
			}
		}
		manholes(sh);
	}

	// ------------------------------------------------------------------------------------------------
	// Streets: lamps, traffic lights, trees and benches along the pavement, bus stops

	private void streets(CityShape sh, View v) {
		CityShape.Lot l = v.lot;
		boolean lights = c.size != Cities.Size.SMALL;
		boolean villas = l.district == CityShape.OUTER;
		for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
			Direction along = side.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
			int len = side.getAxis() == Direction.Axis.Z ? l.width() : l.depth();
			int sx0 = side.getAxis() == Direction.Axis.Z ? l.x0 : (side == Direction.WEST ? l.x0 : l.x1);
			int sz0 = side.getAxis() == Direction.Axis.Z ? (side == Direction.NORTH ? l.z0 : l.z1) : l.z0;
			// A bus stop on the main streets (the ones round the central square), once per side.
			int line = side == Direction.NORTH ? sh.gz[l.j0] : side == Direction.SOUTH ? sh.gz[l.j1 + 1] : side == Direction.WEST ? sh.gx[l.i0]
					: sh.gx[l.i1 + 1];
			boolean main = side.getAxis() == Direction.Axis.Z ? line == sh.gz[sh.cj] || line == sh.gz[sh.cj + 1]
					: line == sh.gx[sh.ci] || line == sh.gx[sh.ci + 1];
			int busAt = main && l.district != CityShape.HALL ? len / 2 - 2 : -100;
			for (int s = 0; s < len; s++) {
				int gx = sx0 + along.getStepX() * s;
				int gz = sz0 + along.getStepZ() * s;
				int kx = gx + side.getStepX();
				int kz = gz + side.getStepZ();
				int inner = v.at(gx, gz);
				if (s == busAt && inner != DOOR && inner != BUILDING && v.at(gx + along.getStepX() * 3, gz + along.getStepZ() * 3) != DOOR) {
					busStop(kx, kz, side, along);
					s += 4;
					continue;
				}
				if (s < 2 || s > len - 3) {
					continue;
				}
				if (Math.floorMod(s, 8) == 4 && inner != DOOR) {
					lamp(kx, kz, side);
				} else if (Math.floorMod(s, 8) == 0 && !villas && (inner == FREE || inner == FRONT) && !used.contains(BlockPos.asLong(gx, 0, gz))) {
					streetTree(gx, gz, inner == FRONT);
					if (r.nextInt(3) == 0 && v.at(gx + along.getStepX() * 3, gz + along.getStepZ() * 3) != DOOR) {
						// 1.35: a bench facing the street, a bin beside it.
						put(gx + along.getStepX() * 2, base + 1, gz + along.getStepZ() * 2, st(bench(), side));
						put(gx + along.getStepX() * 3, base + 1, gz + along.getStepZ() * 3, st(bin(), side));
						for (int k = 1; k <= 3; k++) {
							used.add(BlockPos.asLong(gx + along.getStepX() * k, 0, gz + along.getStepZ() * k));
						}
					}
				} else if (Math.floorMod(s, 16) == 10 && inner != DOOR && !used.contains(BlockPos.asLong(kx, 1, kz))) {
					// 1.35: now and then by the kerb a hydrant, a letter box, an advertising column (the European downtown), a bollard.
					int roll = r2.nextInt(12);
					if (roll < 2 && look() != CityStyle.SOVIET) {
						put(kx, base + 1, kz, st(StreetBlocks.HYDRANT, side));
					} else if (roll == 2 && (l.district == CityShape.DOWNTOWN || l.district == CityShape.MID)) {
						put(kx, base + 1, kz, st(mailbox(), side));
					} else if (roll == 3 && look() == CityStyle.EUROPEAN && l.district == CityShape.DOWNTOWN) {
						put(kx, base + 1, kz, st(StreetBlocks.ADVERT_COLUMN, side));
					} else if (roll == 4 && look() != CityStyle.SOVIET) {
						put(kx, base + 1, kz, st(StreetBlocks.BOLLARD, side));
					}
					used.add(BlockPos.asLong(kx, 1, kz));
				}
			}
		}
		// The corners: traffic lights at two of each crossing's four, lamps at the others.
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int x = sx < 0 ? l.x0 - 1 : l.x1 + 1;
				int z = sz < 0 ? l.z0 - 1 : l.z1 + 1;
				if (lights && sx == sz) {
					trafficLight(x, z, sx, sz);
				} else if (r2.nextInt(3) == 0) {
					// 1.35: a road sign at the corner: the pedestrian crossing (big towns) or who gives way (small ones).
					Block sign = lights ? StreetBlocks.SIGN_CROSSING : ((x / 7 + z / 5) & 1) == 0 ? StreetBlocks.SIGN_GIVE_WAY : StreetBlocks.SIGN_MAIN_ROAD;
					onPole(x, z, StreetBlocks.POLE_STEEL, 2, sign, sx < 0 ? Direction.WEST : Direction.EAST);
				} else {
					lamp(x, z, sz < 0 ? Direction.NORTH : Direction.SOUTH);
				}
			}
		}
	}

	/** Manhole covers in the middle of the lanes every so often. */
	private void manholes(CityShape sh) {
		BlockState cover = StreetBlocks.MANHOLE.defaultBlockState();
		for (int k = 0; k <= sh.n; k++) {
			for (int j = 0; j < sh.n; j++) {
				if (sh.segV(k, j)) {
					for (int z = sh.gz[j] + 6; z <= sh.gz[j + 1] - 6; z += 11) {
						force(sh.gx[k] + 1, base, z, cover);
					}
				}
				if (sh.segH(k, j)) {
					for (int x = sh.gx[j] + 6; x <= sh.gx[j + 1] - 6; x += 11) {
						force(x, base, sh.gz[k] - 1, cover);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Yards

	private void yard(View v) {
		int[] q;
		if ((q = v.rect(12, 6)) != null) {
			parking(q[0], q[1], 0);
		} else if ((q = v.rect(6, 12)) != null) {
			parking(q[0], q[1], 1);
		}
		if (r.nextInt(3) > 0 && (q = v.rect(7, 7)) != null) {
			playground(q[0], q[1]);
		}
		if (c.size == Cities.Size.LARGE && r.nextInt(3) == 0 && (q = v.rect(7, 11)) != null) {
			court(q[0], q[1]);
		}
		int fun = 1 + r.nextInt(3);
		for (int k = 0; k < fun; k++) {
			switch (r.nextInt(5)) {
				case 0 -> {
					if ((q = v.rect(3, 3)) != null) {
						kiosk(q[0], q[1]);
					}
				}
				case 1 -> {
					if ((q = v.rect(3, 3)) != null) {
						iceCream(q[0] + 1, q[1] + 1);
					}
				}
				case 2 -> {
					if ((q = v.rect(5, 2)) != null) {
						billboard(q[0], q[1]);
					}
				}
				case 3 -> {
					if ((q = v.rect(2, 2)) != null) {
						vending(q[0], q[1]);
					}
				}
				default -> {
					if ((q = v.rect(4, 1)) != null) {
						bikeRack(q[0], q[1]);
					}
				}
			}
		}
		for (int k = 0; k < 4; k++) {
			if ((q = v.rect(3, 2)) != null) {
				flowerBed(q[0], q[1]);
			}
		}
		for (int k = 0; k < 3; k++) {
			if ((q = v.rect(3, 3)) != null) {
				streetTree(q[0] + 1, q[1] + 1, false);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Villas: a fence round each house's garden with a gate at the door, a driveway with a car, a cat

	private void villas(View v) {
		Block[] fences = {Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE, Blocks.DARK_OAK_FENCE, Blocks.IRON_BARS, Blocks.AZALEA_LEAVES};
		for (Building b : v.here) {
			if (b.type != BuildingType.COTTAGE && b.type != BuildingType.HOUSE && b.type != BuildingType.SMALL_HOUSE) {
				continue;
			}
			if (!(b.origin.getX() >= v.lot.x0 && b.origin.getX() <= v.lot.x1 && b.origin.getZ() >= v.lot.z0 && b.origin.getZ() <= v.lot.z1)) {
				continue;
			}
			Block fb = fences[r.nextInt(fences.length)];
			BlockState fence = fb == Blocks.AZALEA_LEAVES ? fb.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true) : fb.defaultBlockState();
			int hw = b.type.halfWidth() + 2;
			int back = b.type.depth + 1;
			for (int lx = -hw; lx <= hw; lx++) {
				for (int lz = -1; lz <= back; lz++) {
					boolean edge = Math.abs(lx) == hw || lz == -1 || lz == back;
					if (!edge) {
						continue;
					}
					BlockPos at = b.at(lx, 0, lz);
					if (v.at(at.getX(), at.getZ()) == BUILDING || at.getX() < v.lot.x0 || at.getX() > v.lot.x1 || at.getZ() < v.lot.z0
							|| at.getZ() > v.lot.z1) {
						continue;
					}
					if (lz == -1 && lx >= -1 && lx <= 3) {
						force(at.getX(), base, at.getZ(), b(Blocks.POLISHED_ANDESITE));
						continue;
					}
					put(at.getX(), base + 1, at.getZ(), fence);
				}
			}
			// A car beside the house, a cat now and then.
			BlockPos side = b.at(b.type.halfWidth() + 1 + 1, 0, 1);
			boolean alongZ = b.facing.getAxis() == Direction.Axis.Z;
			int cx = side.getX() - (alongZ ? 0 : 0);
			int cz = side.getZ();
			if (r.nextInt(3) > 0 && v.free(cx, cz) && v.free(alongZ ? cx + 1 : cx + 3, alongZ ? cz + 3 : cz + 1)) {
				car(Math.min(cx, cx), Math.min(cz, cz), alongZ, CAR_COLORS[r.nextInt(CAR_COLORS.length)]);
				use(cx, cz, alongZ ? cx + 1 : cx + 3, alongZ ? cz + 3 : cz + 1);
			}
			if (r.nextInt(4) == 0) {
				BlockPos p = b.at(-b.type.halfWidth() - 1, 0, b.type.depth / 2);
				spawn(p.getX(), p.getZ(), 0);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The central square behind the city hall: a lit fountain, flags, benches, lamps

	private void square(View v) {
		int[] q = v.rect(9, 5);
		if (q == null) {
			return;
		}
		int x0 = q[0];
		int z0 = q[1];
		for (int x = x0; x < x0 + 9; x++) {
			for (int z = z0; z < z0 + 5; z++) {
				force(x, base, z, (x + z) % 2 == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.POLISHED_DIORITE));
			}
		}
		int fx = x0 + 4;
		int fz = z0 + 2;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				if (rim) {
					put(fx + dx, base + 1, fz + dz, b(Blocks.STONE_BRICK_SLAB));
				} else {
					force(fx + dx, base - 1, fz + dz, b(Blocks.SEA_LANTERN));
					force(fx + dx, base, fz + dz, Blocks.WATER.defaultBlockState());
				}
			}
		}
		put(fx, base + 1, fz, b(Blocks.STONE_BRICK_WALL));
		put(fx, base + 2, fz, b(Blocks.STONE_BRICK_WALL));
		put(fx, base + 3, fz, b(Blocks.SEA_LANTERN));
		for (int x : new int[]{x0, x0 + 8}) {
			for (int y = 1; y <= 5; y++) {
				put(x, base + y, z0, b(Blocks.IRON_BARS));
			}
			put(x, base + 6, z0, Blocks.BANNER.pick(DyeColor.byId(c.color)).defaultBlockState());
			// 1.35: benches facing the fountain, globe lamps, planters at the corners.
			put(x, base + 1, z0 + 4, st(bench(), x == x0 ? Direction.EAST : Direction.WEST));
			onPole(x, z0 + 2, StreetBlocks.POLE_BLACK, 2, StreetBlocks.LAMP_GLOBE, Direction.SOUTH);
			put(x == x0 ? x - 1 : x + 1, base + 1, z0 - 1, st(StreetBlocks.PLANTER, Direction.SOUTH));
			put(x == x0 ? x - 1 : x + 1, base + 1, z0 + 5, st(StreetBlocks.PLANTER, Direction.SOUTH));
		}
		put(x0 + 4, base + 1, z0 + 5, st(look() == CityStyle.EUROPEAN ? StreetBlocks.BOOTH_RED : StreetBlocks.BOOTH_SOVIET, Direction.NORTH));
	}

	/** Round a park: more trees, benches. */
	private void parkEdge(View v) {
		int[] q;
		for (int k = 0; k < 4; k++) {
			if ((q = v.rect(3, 3)) != null) {
				streetTree(q[0] + 1, q[1] + 1, false);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Industry: a fence round the yard, stacked shipping containers, tanks, pipes, chimneys, lorries

	private void industry(View v) {
		CityShape.Lot l = v.lot;
		// A steel fence along the garden edge, gaps at the gates.
		for (int x = l.x0; x <= l.x1; x++) {
			for (int z : new int[]{l.z0, l.z1}) {
				if (v.at(x, z) == FREE) {
					put(x, base + 1, z, b(Blocks.IRON_BARS));
					put(x, base + 2, z, b(Blocks.IRON_BARS));
				}
			}
		}
		for (int z = l.z0; z <= l.z1; z++) {
			for (int x : new int[]{l.x0, l.x1}) {
				if (v.at(x, z) == FREE) {
					put(x, base + 1, z, b(Blocks.IRON_BARS));
					put(x, base + 2, z, b(Blocks.IRON_BARS));
				}
			}
		}
		// The yard is concrete.
		for (int x = l.x0 + 1; x < l.x1; x++) {
			for (int z = l.z0 + 1; z < l.z1; z++) {
				if (v.at(x, z) == FREE) {
					force(x, base, z, r.nextInt(9) == 0 ? b(Blocks.GRAVEL) : c(DyeColor.LIGHT_GRAY));
				}
			}
		}
		int[] q;
		for (int k = 0; k < 2 + r.nextInt(3); k++) {
			if ((q = v.rect(6, 3)) != null) {
				containers(q[0], q[1]);
			}
		}
		for (int k = 0; k < 1 + r.nextInt(2); k++) {
			if ((q = v.rect(4, 4)) != null) {
				tank(q[0] + 1, q[1] + 1);
			}
		}
		if ((q = v.rect(3, 3)) != null) {
			chimney(q[0] + 1, q[1] + 1, 14 + r.nextInt(10));
		}
		for (int k = 0; k < 2; k++) {
			if ((q = v.rect(3, 7)) != null) {
				lorry(q[0] + 1, q[1] + 1);
			}
		}
		if ((q = v.rect(10, 1)) != null) {
			pipe(q[0], q[1], 10);
		}
	}

	private static final DyeColor[] BOX_COLORS = {DyeColor.RED, DyeColor.BLUE, DyeColor.ORANGE, DyeColor.GREEN, DyeColor.LIGHT_BLUE, DyeColor.BROWN,
			DyeColor.GRAY, DyeColor.WHITE};

	/** Shipping containers two high: 6 long, coloured, ribbed with trapdoors. */
	private void containers(int x0, int z0) {
		for (int row = 0; row < 2; row++) {
			for (int level = 0; level < (row == 0 ? 2 : 1 + r.nextInt(2)); level++) {
				BlockState box = Blocks.DYED_TERRACOTTA.pick(BOX_COLORS[r.nextInt(BOX_COLORS.length)]).defaultBlockState();
				for (int x = x0; x < x0 + 6; x++) {
					put(x, base + 1 + level * 2, z0 + row * 2 - (row), box);
					put(x, base + 2 + level * 2, z0 + row * 2 - (row), box);
				}
			}
		}
	}

	/** A round tank on legs with a ladder. */
	private void tank(int x, int z) {
		for (int y = 1; y <= 2; y++) {
			put(x - 1, base + y, z - 1, b(Blocks.IRON_BARS));
			put(x + 1, base + y, z + 1, b(Blocks.IRON_BARS));
			put(x - 1, base + y, z + 1, b(Blocks.IRON_BARS));
			put(x + 1, base + y, z - 1, b(Blocks.IRON_BARS));
		}
		for (int y = 3; y <= 7; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					put(x + dx, base + y, z + dz, y == 5 ? c(DyeColor.RED) : c(DyeColor.WHITE));
				}
			}
		}
		put(x, base + 8, z, b(Blocks.SMOOTH_STONE_SLAB));
	}

	/** A factory chimney, smoking, with red and white bands and a light on top. */
	private void chimney(int x, int z, int h) {
		for (int y = 1; y <= h; y++) {
			BlockState s = (y / 3) % 2 == 0 ? c(DyeColor.RED) : c(DyeColor.WHITE);
			put(x, base + y, z, s);
		}
		put(x, base + h + 1, z, b(Blocks.CAMPFIRE));
		put(x + 1, base + h, z, b(Blocks.SEA_LANTERN));
	}

	/** A lorry: cab and box on six wheels. */
	private void lorry(int x, int z) {
		DyeColor col = BOX_COLORS[r.nextInt(BOX_COLORS.length)];
		for (int dz = 0; dz < 6; dz++) {
			for (int dx = 0; dx <= 1; dx++) {
				put(x + dx, base + 1, z + dz, c(DyeColor.GRAY));
				if (dz < 2) {
					put(x + dx, base + 2, z + dz, dz == 0 ? Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState() : c(col));
				} else {
					put(x + dx, base + 2, z + dz, c(DyeColor.WHITE));
					put(x + dx, base + 3, z + dz, c(DyeColor.WHITE));
				}
			}
		}
	}

	/** A pipe on supports. */
	private void pipe(int x0, int z, int len) {
		for (int x = x0; x < x0 + len; x++) {
			put(x, base + 3, z, b(Blocks.IRON_BLOCK));
			if ((x - x0) % 4 == 0) {
				put(x, base + 1, z, b(Blocks.IRON_BARS));
				put(x, base + 2, z, b(Blocks.IRON_BARS));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Wasteland and building sites

	private void vacant(View v) {
		CityShape.Lot l = v.lot;
		boolean site = r.nextBoolean();
		if (site) {
			// A building site: a fence, the frame of a block going up, a tower crane, sand and bricks.
			for (int x = l.x0; x <= l.x1; x++) {
				put(x, base + 1, l.z0, b(Blocks.BIRCH_FENCE));
				put(x, base + 1, l.z1, b(Blocks.BIRCH_FENCE));
			}
			for (int z = l.z0; z <= l.z1; z++) {
				put(l.x0, base + 1, z, b(Blocks.BIRCH_FENCE));
				put(l.x1, base + 1, z, b(Blocks.BIRCH_FENCE));
			}
			int fx0 = l.x0 + 3;
			int fz0 = l.z0 + 3;
			int fw = Math.min(13, l.width() - 8);
			int fd = Math.min(9, l.depth() - 8);
			int floors = 2 + r.nextInt(3);
			for (int f = 0; f <= floors; f++) {
				for (int x = fx0; x <= fx0 + fw; x++) {
					for (int z = fz0; z <= fz0 + fd; z++) {
						boolean column = (x - fx0) % 4 == 0 && (z - fz0) % 4 == 0;
						if (f < floors && column) {
							put(x, base + f * 3 + 1, z, c(DyeColor.GRAY));
							put(x, base + f * 3 + 2, z, c(DyeColor.GRAY));
						}
						if (f > 0 && (f < floors || r.nextInt(2) == 0)) {
							put(x, base + f * 3, z, c(DyeColor.LIGHT_GRAY));
						}
					}
				}
			}
			// The crane: a lattice mast, the jib, the counterweight, the hook.
			int mx = Math.min(fx0 + fw + 2, l.x1 - 2);
			int mz = Math.min(fz0 + fd + 2, l.z1 - 2);
			int h = 22 + r.nextInt(8);
			for (int y = 1; y <= h; y++) {
				put(mx, base + y, mz, y % 2 == 0 ? c(DyeColor.YELLOW) : b(Blocks.IRON_BARS));
			}
			for (int k = -4; k <= 12; k++) {
				put(mx - k, base + h + 1, mz, c(DyeColor.YELLOW));
			}
			put(mx + 4, base + h, mz, c(DyeColor.GRAY));
			put(mx + 3, base + h, mz, c(DyeColor.GRAY));
			put(mx, base + h + 2, mz, c(DyeColor.YELLOW));
			put(mx, base + h + 3, mz, b(Blocks.REDSTONE_LAMP).setValue(BlockStateProperties.LIT, true));
			for (int y = h; y >= h - 8; y--) {
				put(mx - 9, base + y, mz, b(Blocks.IRON_CHAIN));
			}
			int[] q;
			if ((q = v.rect(3, 3)) != null) {
				for (int dx = 0; dx < 3; dx++) {
					for (int dz = 0; dz < 3; dz++) {
						put(q[0] + dx, base + 1, q[1] + dz, b(Blocks.SAND));
					}
				}
				put(q[0] + 1, base + 2, q[1] + 1, b(Blocks.SAND));
			}
			if ((q = v.rect(2, 2)) != null) {
				put(q[0], base + 1, q[1], b(Blocks.BRICKS));
				put(q[0] + 1, base + 1, q[1], b(Blocks.BRICKS));
				put(q[0], base + 2, q[1], b(Blocks.BRICKS));
			}
		} else {
			// Wasteland: rough ground, bushes, an abandoned car.
			for (int x = l.x0; x <= l.x1; x++) {
				for (int z = l.z0; z <= l.z1; z++) {
					int k = r.nextInt(10);
					if (k < 3) {
						force(x, base, z, b(Blocks.COARSE_DIRT));
					} else if (k < 5) {
						put(x, base + 1, z, b(Blocks.SHORT_GRASS));
					} else if (k == 5) {
						put(x, base + 1, z, b(Blocks.AZALEA));
					}
				}
			}
			int[] q;
			if ((q = v.rect(3, 5)) != null) {
				car(q[0], q[1], true, DyeColor.BROWN);
			}
			for (int k = 0; k < 2; k++) {
				if ((q = v.rect(3, 3)) != null) {
					streetTree(q[0] + 1, q[1] + 1, false);
				}
			}
		}
	}

	private static final DyeColor[] CAR_COLORS = {DyeColor.WHITE, DyeColor.BLACK, DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.RED, DyeColor.BLUE,
			DyeColor.YELLOW, DyeColor.GREEN, DyeColor.ORANGE, DyeColor.LIGHT_BLUE};


	/** 1.35: a street lamp of the town's style - its arm (or lantern) towards the street, {@code out}. */
	private void lamp(int x, int z, Direction out) {
		onPole(x, z, lampPole(), lampHeight(), lampHead(), out);
	}

	/**
	 * 1.35: a traffic light at a crossing's corner: a black pole with two heads, one for each street (the one along x
	 * shows one phase, the one along z the other: they change in turn, all over the town at once).
	 */
	private void trafficLight(int x, int z, int sx, int sz) {
		onPole(x, z, StreetBlocks.POLE_BLACK, 2, StreetBlocks.TRAFFIC_LIGHT, sx > 0 ? Direction.EAST : Direction.WEST);
	}

	/** 1.28: a date palm for the desert town: a tall trunk, fronds drooping out in eight directions. */
	private void palm(int x, int z, boolean small) {
		int h = small ? 4 : 5 + r.nextInt(2);
		for (int y = 1; y <= h; y++) {
			tree(x, base + y, z, b(Blocks.JUNGLE_LOG));
		}
		BlockState l = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		tree(x, base + h + 1, z, l);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int dx = d.getStepX();
			int dz = d.getStepZ();
			tree(x + dx, base + h + 1, z + dz, l);
			tree(x + 2 * dx, base + h, z + 2 * dz, l);
			if (!small) {
				tree(x + 3 * dx, base + h - 1, z + 3 * dz, l);
			}
			// The diagonal fronds, one step lower.
			int ex = dx - dz;
			int ez = dz + dx;
			tree(x + ex, base + h, z + ez, l);
		}
		used.add(BlockPos.asLong(x, 0, z));
	}

	private void streetTree(int x, int z, boolean small) {
		if (c.style == CityStyle.DESERT) {
			palm(x, z, small);
			return;
		}
		int kind = r.nextInt(4);
		// 1.28: birches in the Soviet town, limes and oaks in the European one (no cherries in either).
		if (kind == 1 && c.style == CityStyle.SOVIET) {
			kind = 0;
		} else if (kind == 1 && c.style == CityStyle.EUROPEAN) {
			kind = 3;
		}
		Block log = switch (kind) {
			case 0 -> Blocks.BIRCH_LOG;
			case 1 -> Blocks.CHERRY_LOG;
			case 2 -> Blocks.SPRUCE_LOG;
			default -> Blocks.OAK_LOG;
		};
		Block leaves = switch (kind) {
			case 0 -> Blocks.BIRCH_LEAVES;
			case 1 -> Blocks.CHERRY_LEAVES;
			case 2 -> Blocks.SPRUCE_LEAVES;
			default -> Blocks.OAK_LEAVES;
		};
		BlockState l = leaves.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		int h = small ? 3 : 4;
		for (int y = 1; y <= h; y++) {
			tree(x, base + y, z, b(log));
		}
		int rad = small ? 1 : 2;
		for (int y = h - 1; y <= h + 2; y++) {
			int rr = kind == 2 ? Math.max(0, rad - (y - h + 1) / 2) : (y == h + 2 ? rad - 1 : rad);
			for (int dx = -rr; dx <= rr; dx++) {
				for (int dz = -rr; dz <= rr; dz++) {
					if (Math.abs(dx) + Math.abs(dz) <= rr + 1 && !(dx == 0 && dz == 0 && y <= h)) {
						tree(x + dx, base + y, z + dz, l);
					}
				}
			}
		}
		// A ring of flowers round the trunk.
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.OXEYE_DAISY};
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (r.nextInt(2) == 0) {
				put(x + d.getStepX(), base + 1, z + d.getStepZ(), b(flowers[r.nextInt(flowers.length)]));
			}
		}
		used.add(BlockPos.asLong(x, 0, z));
	}

	/** A bus stop on the pavement: glass back, roof with a light, a bench, the sign, an advert. */
	private void busStop(int kx, int kz, Direction side, Direction along) {
		Direction in = side.getOpposite();
		for (int s = 0; s <= 3; s++) {
			used.add(BlockPos.asLong(kx + along.getStepX() * s + in.getStepX(), 0, kz + along.getStepZ() * s + in.getStepZ()));
		}
		r.nextInt(16);
		// 1.35: the shelter (glass, or the Soviet concrete one with its mosaic) three blocks long, open to the street; the
		// stop's sign beyond it, a bin.
		put(kx + along.getStepX() * 2, base + 1, kz + along.getStepZ() * 2,
				st(look() == CityStyle.SOVIET ? StreetBlocks.BUS_STOP_SOVIET : StreetBlocks.BUS_STOP_MODERN, side));
		onPole(kx + along.getStepX() * 4, kz + along.getStepZ() * 4, StreetBlocks.POLE_STEEL, 2, StreetBlocks.SIGN_BUS, along);
		put(kx - along.getStepX(), base + 1, kz - along.getStepZ(), st(bin(), side));
	}

	/** Four places with white lines, most of them taken. */
	private void parking(int x0, int z0, int dir) {
		boolean alongX = dir == 0;
		int w = alongX ? 12 : 6;
		int h = alongX ? 6 : 12;
		for (int x = x0; x < x0 + w; x++) {
			for (int z = z0; z < z0 + h; z++) {
				int k = alongX ? x - x0 : z - z0;
				force(x, base, z, k % 3 == 0 ? c(DyeColor.WHITE) : c(DyeColor.GRAY));
			}
		}
		for (int s = 0; s < 4; s++) {
			if (r.nextInt(4) == 0) {
				continue;
			}
			int cx = alongX ? x0 + s * 3 + 1 : x0 + 1;
			int cz = alongX ? z0 + 1 : z0 + s * 3 + 1;
			car(cx, cz, alongX, CAR_COLORS[r.nextInt(CAR_COLORS.length)]);
		}
		put(alongX ? x0 + w : x0 - 1, base + 1, alongX ? z0 : z0 + h, b(Blocks.IRON_BARS));
	}

	/**
	 * A car, 2 wide and 4 long: the body, windows in the middle, bonnet and boot lower; wheels on its sides.
	 * {@code alongX} = the car stands across x (its 4 blocks run along z).
	 */
	private void car(int x0, int z0, boolean alongX, DyeColor color) {
		BlockState body = c(color);
		BlockState low = Blocks.CONCRETE_SLAB.pick(color).defaultBlockState();
		BlockState window = Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState();
		for (int u = 0; u <= 1; u++) {
			for (int v = 0; v <= 3; v++) {
				int x = alongX ? x0 + u : x0 + v;
				int z = alongX ? z0 + v : z0 + u;
				put(x, base + 1, z, body);
				put(x, base + 2, z, v == 1 || v == 2 ? window : low);
			}
		}
		Direction side = alongX ? Direction.WEST : Direction.NORTH;
		for (int v : new int[]{0, 3}) {
			for (int u : new int[]{-1, 2}) {
				int x = alongX ? x0 + u : x0 + v;
				int z = alongX ? z0 + v : z0 + u;
				Direction out = u < 0 ? side : side.getOpposite();
				put(x, base + 1, z, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
						.setValue(BlockStateProperties.HORIZONTAL_FACING, out));
			}
		}
	}

	private void playground(int x0, int z0) {
		for (int x = x0; x < x0 + 7; x++) {
			for (int z = z0; z < z0 + 7; z++) {
				boolean edge = x == x0 || z == z0 || x == x0 + 6 || z == z0 + 6;
				force(x, base, z, c(edge ? DyeColor.RED : DyeColor.LIME));
			}
		}
		// The slide.
		put(x0 + 1, base + 1, z0 + 1, b(Blocks.OAK_FENCE));
		put(x0 + 2, base + 1, z0 + 1, b(Blocks.OAK_FENCE));
		put(x0 + 1, base + 2, z0 + 1, c(DyeColor.YELLOW));
		put(x0 + 2, base + 2, z0 + 1, c(DyeColor.YELLOW));
		put(x0 + 1, base + 1, z0 + 2, stairs(Blocks.OAK_STAIRS, Direction.NORTH));
		put(x0 + 3, base + 2, z0 + 1, stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.WEST));
		put(x0 + 4, base + 1, z0 + 1, stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.WEST));
		// The swings.
		for (int y = 1; y <= 3; y++) {
			put(x0 + 1, base + y, z0 + 5, b(Blocks.SPRUCE_FENCE));
			put(x0 + 5, base + y, z0 + 5, b(Blocks.SPRUCE_FENCE));
		}
		for (int x = x0 + 2; x <= x0 + 4; x++) {
			put(x, base + 3, z0 + 5, b(Blocks.SPRUCE_FENCE));
		}
		put(x0 + 2, base + 2, z0 + 5, b(Blocks.IRON_CHAIN));
		put(x0 + 4, base + 2, z0 + 5, b(Blocks.IRON_CHAIN));
		put(x0 + 2, base + 1, z0 + 5, slabTop(Blocks.DARK_OAK_SLAB));
		put(x0 + 4, base + 1, z0 + 5, slabTop(Blocks.DARK_OAK_SLAB));
		// The sandbox and a bench, a lamp.
		force(x0 + 4, base, z0 + 2, b(Blocks.SAND));
		force(x0 + 5, base, z0 + 2, b(Blocks.SAND));
		force(x0 + 4, base, z0 + 3, b(Blocks.SAND));
		force(x0 + 5, base, z0 + 3, b(Blocks.SAND));
		put(x0, base + 1, z0 + 3, stairs(Blocks.OAK_STAIRS, Direction.WEST));
		put(x0, base + 1, z0 + 4, stairs(Blocks.OAK_STAIRS, Direction.WEST));
		put(x0 + 6, base + 1, z0 + 6, b(Blocks.OAK_FENCE));
		put(x0 + 6, base + 2, z0 + 6, b(Blocks.LANTERN));
	}

	private void court(int x0, int z0) {
		for (int x = x0; x < x0 + 7; x++) {
			for (int z = z0; z < z0 + 11; z++) {
				boolean line = x == x0 || x == x0 + 6 || z == z0 || z == z0 + 10 || z == z0 + 5;
				force(x, base, z, line ? c(DyeColor.WHITE) : Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState());
			}
		}
		int xc = x0 + 3;
		for (int zz : new int[]{z0, z0 + 10}) {
			for (int y = 1; y <= 3; y++) {
				put(xc, base + y, zz, b(Blocks.IRON_BARS));
			}
			put(xc - 1, base + 4, zz, c(DyeColor.WHITE));
			put(xc, base + 4, zz, c(DyeColor.WHITE));
			put(xc + 1, base + 4, zz, c(DyeColor.WHITE));
			put(xc, base + 5, zz, c(DyeColor.WHITE));
			int dz = zz == z0 ? 1 : -1;
			put(xc, base + 4, zz + dz, Blocks.STAINED_GLASS_PANE.pick(DyeColor.RED).defaultBlockState());
		}
		for (int zz : new int[]{z0 + 1, z0 + 9}) {
			put(x0 + 6, base + 1, zz, b(Blocks.OAK_FENCE));
			put(x0 + 6, base + 2, zz, b(Blocks.LANTERN));
		}
	}

	private void flowerBed(int x0, int z0) {
		Block[] small = {Blocks.RED_TULIP, Blocks.WHITE_TULIP, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY};
		for (int x = x0; x < x0 + 3; x++) {
			for (int z = z0; z < z0 + 2; z++) {
				force(x, base, z, b(Blocks.COARSE_DIRT));
				put(x, base + 1, z, b(small[r.nextInt(small.length)]));
			}
		}
		if (r.nextInt(3) == 0) {
			put(x0 + 1, base + 1, z0, b(Blocks.FLOWERING_AZALEA));
		}
	}

	/** A kiosk (shawarma, newspapers, coffee): a small box with a counter window and a glowing sign. */
	private void kiosk(int x0, int z0) {
		r.nextInt(4);
		// 1.35: the newspaper kiosk (its counter to the south); a telephone booth beside it in the Soviet and European towns.
		put(x0 + 1, base + 1, z0 + 1, st(StreetBlocks.KIOSK, Direction.SOUTH));
		if (look() == CityStyle.SOVIET || look() == CityStyle.EUROPEAN) {
			put(x0 + 3, base + 1, z0 + 1, st(look() == CityStyle.SOVIET ? StreetBlocks.BOOTH_SOVIET : StreetBlocks.BOOTH_RED, Direction.SOUTH));
		}
	}

	/** An ice cream cart under a striped umbrella. */
	private void iceCream(int x, int z) {
		put(x, base + 1, z, c(DyeColor.WHITE));
		put(x + 1, base + 1, z, c(DyeColor.PINK));
		put(x, base + 2, z, b(Blocks.OAK_FENCE));
		put(x, base + 3, z, b(Blocks.OAK_FENCE));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, base + 4, z + dz, Blocks.WOOL_SLAB.pick((dx + dz) % 2 == 0 ? DyeColor.PINK : DyeColor.WHITE).defaultBlockState());
			}
		}
	}

	/** A billboard on two legs, lit from above. */
	private void billboard(int x0, int z0) {
		r.nextInt(16);
		r.nextInt(16);
		// 1.35: a lit billboard three blocks wide on two legs (the block is up in the air, the legs reach down).
		put(x0 + 2, base + 2, z0, st(StreetBlocks.BILLBOARD, Direction.SOUTH));
	}

	/** A drinks machine and a cash machine side by side. */
	private void vending(int x0, int z0) {
		// 1.35: a drinks machine and a bin.
		put(x0, base + 1, z0, st(StreetBlocks.VENDING, Direction.SOUTH));
		put(x0 + 1, base + 1, z0, st(bin(), Direction.SOUTH));
	}

	private void bikeRack(int x0, int z0) {
		put(x0 + 1, base + 1, z0, st(StreetBlocks.BIKE_RACK, Direction.SOUTH));
		put(x0 + 2, base + 1, z0, st(StreetBlocks.BIKE_RACK, Direction.SOUTH));
	}
}
