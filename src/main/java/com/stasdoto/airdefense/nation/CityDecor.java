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

	private CityDecor(Cities.City c) {
		this.c = c;
		this.r = new Random(c.seed ^ 0xDEC0L);
		this.base = c.base;
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

	private int n() {
		return c.size.n;
	}

	private int[] blockCentre(int i, int j) {
		int mid = n() / 2;
		return new int[]{c.x + (i - mid) * Cities.PITCH, c.z + (j - mid) * Cities.PITCH};
	}

	/** What stands on each cell of a lot (25 x 25 round its centre). */
	private int[][] occupancy(int bx, int bz, List<Building> here) {
		int[][] g = new int[25][25];
		for (Building b : here) {
			int hw = b.type.halfWidth();
			Direction right = b.facing.getClockWise();
			int front = b.type == BuildingType.CITY_HALL ? 4 : 2;
			List<Integer> doors = doors(b);
			for (int x = bx - 12; x <= bx + 12; x++) {
				for (int z = bz - 12; z <= bz + 12; z++) {
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
					int gi = x - bx + 12;
					int gj = z - bz + 12;
					g[gi][gj] = Math.max(g[gi][gj], k);
				}
			}
		}
		return g;
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

	private void all() {
		int n = n();
		int mid = n / 2;
		List<Building> buildings = c.buildings();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int[] bc = blockCentre(i, j);
				int bx = bc[0];
				int bz = bc[1];
				List<Building> here = new ArrayList<>();
				for (Building b : buildings) {
					if (Math.abs(b.origin.getX() - bx) <= 14 && Math.abs(b.origin.getZ() - bz) <= 14) {
						here.add(b);
					}
				}
				int[][] g = occupancy(bx, bz, here);
				boolean quad = false;
				boolean park = false;
				boolean hall = false;
				for (Building b : here) {
					quad |= b.type == BuildingType.COTTAGE || b.type == BuildingType.HOUSE || b.type == BuildingType.SMALL_HOUSE;
					park |= b.type == BuildingType.PARK;
					hall |= b.type == BuildingType.CITY_HALL;
				}
				streets(i, j, bx, bz, g, quad);
				if (hall) {
					square(bx, bz, g);
				} else if (quad) {
					villas(bx, bz, here, g);
				} else if (!park) {
					yard(i, j, bx, bz, g, Math.max(Math.abs(i - mid), Math.abs(j - mid)));
				}
			}
		}
		manholes();
	}

	private int at(int[][] g, int bx, int bz, int x, int z) {
		int gi = x - bx + 12;
		int gj = z - bz + 12;
		if (gi < 0 || gj < 0 || gi >= 25 || gj >= 25) {
			return BUILDING;
		}
		return g[gi][gj];
	}

	private boolean free(int[][] g, int bx, int bz, int x, int z) {
		return at(g, bx, bz, x, z) == FREE && !used.contains(BlockPos.asLong(x, 0, z));
	}

	private void use(int x0, int z0, int x1, int z1) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				used.add(BlockPos.asLong(x, 0, z));
			}
		}
	}

	/** A free w x h rectangle in the yard (inside the front gardens), or null. */
	private int[] findRect(int[][] g, int bx, int bz, int w, int h) {
		int span = 21;
		int start = r.nextInt(span * span);
		for (int k = 0; k < span * span; k++) {
			int idx = (start + k) % (span * span);
			int x0 = bx - 10 + idx % span;
			int z0 = bz - 10 + idx / span;
			if (x0 + w - 1 > bx + 10 || z0 + h - 1 > bz + 10) {
				continue;
			}
			boolean ok = true;
			for (int x = x0; x < x0 + w && ok; x++) {
				for (int z = z0; z < z0 + h && ok; z++) {
					ok = free(g, bx, bz, x, z);
				}
			}
			if (ok) {
				use(x0, z0, x0 + w - 1, z0 + h - 1);
				return new int[]{x0, z0};
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------
	// Streets: lamps, traffic lights, trees and benches along the pavement, bus stops

	private void streets(int i, int j, int bx, int bz, int[][] g, boolean quad) {
		int mid = n() / 2;
		boolean lights = c.size != Cities.Size.SMALL;
		Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		for (Direction side : sides) {
			Direction along = side.getClockWise();
			boolean busSide = (side.getAxis() == Direction.Axis.Z ? j == mid : i == mid) && (i + j) % 2 == 0;
			for (int s = -12; s <= 12; s++) {
				int kx = bx + side.getStepX() * 13 + along.getStepX() * s;
				int kz = bz + side.getStepZ() * 13 + along.getStepZ() * s;
				int gx = bx + side.getStepX() * 12 + along.getStepX() * s;
				int gz = bz + side.getStepZ() * 12 + along.getStepZ() * s;
				int inner = at(g, bx, bz, gx, gz);
				if (busSide && s == -2 && inner != DOOR && inner != BUILDING && at(g, bx, bz, gx + along.getStepX() * 3, gz + along.getStepZ() * 3) != DOOR) {
					busStop(kx, kz, side, along);
					s += 4;
					continue;
				}
				if (Math.floorMod(s, 8) == 4 && inner != DOOR) {
					lamp(kx, kz, side);
				} else if (Math.floorMod(s, 8) == 0 && s != 0 && !quad && (inner == FREE || inner == FRONT) && !used.contains(BlockPos.asLong(gx, 0, gz))) {
					streetTree(gx, gz, inner == FRONT);
					if (r.nextInt(3) == 0 && at(g, bx, bz, gx + along.getStepX(), gz + along.getStepZ()) != DOOR) {
						put(gx + along.getStepX(), base + 1, gz + along.getStepZ(), stairs(Blocks.SPRUCE_STAIRS, side.getOpposite()));
						put(gx + along.getStepX() * 2, base + 1, gz + along.getStepZ() * 2, stairs(Blocks.SPRUCE_STAIRS, side.getOpposite()));
						put(gx + along.getStepX() * 3, base + 1, gz + along.getStepZ() * 3, b(Blocks.COMPOSTER));
						used.add(BlockPos.asLong(gx + along.getStepX(), 0, gz + along.getStepZ()));
						used.add(BlockPos.asLong(gx + along.getStepX() * 2, 0, gz + along.getStepZ() * 2));
						used.add(BlockPos.asLong(gx + along.getStepX() * 3, 0, gz + along.getStepZ() * 3));
					}
				}
			}
		}
		// The corners: traffic lights at two of each crossing's four, lamps at the others.
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int x = bx + sx * 13;
				int z = bz + sz * 13;
				if (lights && sx == sz) {
					trafficLight(x, z, sx, sz);
				} else {
					lamp(x, z, sz < 0 ? Direction.NORTH : Direction.SOUTH);
				}
			}
		}
	}

	private void lamp(int x, int z, Direction out) {
		for (int y = 1; y <= 4; y++) {
			put(x, base + y, z, b(Blocks.IRON_BARS));
		}
		put(x, base + 5, z, b(Blocks.SMOOTH_STONE_SLAB));
		int ax = x + out.getStepX();
		int az = z + out.getStepZ();
		put(ax, base + 5, az, b(Blocks.SMOOTH_STONE_SLAB));
		if (c.size == Cities.Size.LARGE) {
			put(ax, base + 4, az, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
		} else {
			put(ax, base + 4, az, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		}
	}

	private void trafficLight(int x, int z, int sx, int sz) {
		for (int y = 1; y <= 3; y++) {
			put(x, base + y, z, b(Blocks.IRON_BARS));
		}
		for (int y = 4; y <= 6; y++) {
			put(x, base + y, z, c(DyeColor.BLACK));
		}
		put(x, base + 7, z, b(Blocks.SMOOTH_STONE_SLAB));
		Direction dx = sx > 0 ? Direction.EAST : Direction.WEST;
		Direction dz = sz > 0 ? Direction.SOUTH : Direction.NORTH;
		for (Direction d : new Direction[]{dx, dz}) {
			int fx = x + d.getStepX();
			int fz = z + d.getStepZ();
			put(fx, base + 6, fz, wallThing(Blocks.REDSTONE_WALL_TORCH, d));
			put(fx, base + 5, fz, wallThing(Blocks.WALL_TORCH, d));
			put(fx, base + 4, fz, wallThing(Blocks.SOUL_WALL_TORCH, d));
		}
		// The button for pedestrians.
		put(x - sx, base + 2, z, Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, sx > 0 ? Direction.WEST : Direction.EAST));
	}

	private void streetTree(int x, int z, boolean small) {
		int kind = r.nextInt(4);
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
			int x = kx + along.getStepX() * s;
			int z = kz + along.getStepZ() * s;
			int bx = x + in.getStepX();
			int bz = z + in.getStepZ();
			put(bx, base + 1, bz, Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
			put(bx, base + 2, bz, Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
			put(bx, base + 3, bz, s == 1 || s == 2 ? b(Blocks.SEA_LANTERN) : slabTop(Blocks.SMOOTH_STONE_SLAB));
			put(x, base + 3, z, slabTop(Blocks.SMOOTH_STONE_SLAB));
			if (s == 1 || s == 2) {
				put(x, base + 1, z, stairs(Blocks.DARK_OAK_STAIRS, in));
			}
			used.add(BlockPos.asLong(bx, 0, bz));
		}
		// The advert at one end (glowing), the sign pole at the other.
		int ex = kx - along.getStepX();
		int ez = kz - along.getStepZ();
		put(ex, base + 1, ez, b(Blocks.SEA_LANTERN));
		put(ex, base + 2, ez, Blocks.GLAZED_TERRACOTTA.pick(DyeColor.values()[r.nextInt(16)]).defaultBlockState());
		int px = kx + along.getStepX() * 4;
		int pz = kz + along.getStepZ() * 4;
		put(px, base + 1, pz, b(Blocks.IRON_BARS));
		put(px, base + 2, pz, b(Blocks.IRON_BARS));
		put(px, base + 3, pz, c(DyeColor.YELLOW));
	}

	/** Manhole covers in the middle of the lanes every so often. */
	private void manholes() {
		int half = c.half();
		for (int k = 0; k <= n(); k++) {
			int line = -half + k * Cities.PITCH;
			for (int s = -half + 7; s <= half - 7; s += 11) {
				if (Math.floorMod(s + half, Cities.PITCH) < 4 || Math.floorMod(s + half, Cities.PITCH) > Cities.PITCH - 4) {
					continue;
				}
				BlockState cover = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP);
				force(c.x + line + 1, base, c.z + s, cover);
				force(c.x + s, base, c.z + line - 1, cover);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Yards

	private void yard(int i, int j, int bx, int bz, int[][] g, int ring) {
		int[] q;
		if ((q = findRect(g, bx, bz, 12, 6)) != null) {
			parking(q[0], q[1], 0);
		} else if ((q = findRect(g, bx, bz, 6, 12)) != null) {
			parking(q[0], q[1], 1);
		}
		if (r.nextInt(3) > 0 && (q = findRect(g, bx, bz, 7, 7)) != null) {
			playground(q[0], q[1]);
		}
		if (c.size == Cities.Size.LARGE && ring <= 2 && r.nextInt(3) == 0 && (q = findRect(g, bx, bz, 7, 11)) != null) {
			court(q[0], q[1]);
		}
		int fun = 1 + r.nextInt(3);
		for (int k = 0; k < fun; k++) {
			switch (r.nextInt(5)) {
				case 0 -> {
					if ((q = findRect(g, bx, bz, 3, 3)) != null) {
						kiosk(q[0], q[1]);
					}
				}
				case 1 -> {
					if ((q = findRect(g, bx, bz, 3, 3)) != null) {
						iceCream(q[0] + 1, q[1] + 1);
					}
				}
				case 2 -> {
					if ((q = findRect(g, bx, bz, 5, 2)) != null) {
						billboard(q[0], q[1]);
					}
				}
				case 3 -> {
					if ((q = findRect(g, bx, bz, 2, 2)) != null) {
						vending(q[0], q[1]);
					}
				}
				default -> {
					if ((q = findRect(g, bx, bz, 4, 1)) != null) {
						bikeRack(q[0], q[1]);
					}
				}
			}
		}
		for (int k = 0; k < 4; k++) {
			if ((q = findRect(g, bx, bz, 3, 2)) != null) {
				flowerBed(q[0], q[1]);
			}
		}
		for (int k = 0; k < 3; k++) {
			if ((q = findRect(g, bx, bz, 3, 3)) != null) {
				streetTree(q[0] + 1, q[1] + 1, false);
			}
		}
	}

	private static final DyeColor[] CAR_COLORS = {DyeColor.WHITE, DyeColor.BLACK, DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.RED, DyeColor.BLUE,
			DyeColor.YELLOW, DyeColor.GREEN, DyeColor.ORANGE, DyeColor.LIGHT_BLUE};

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
		DyeColor col = new DyeColor[]{DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE, DyeColor.GREEN}[r.nextInt(4)];
		for (int x = x0; x < x0 + 3; x++) {
			for (int z = z0; z < z0 + 3; z++) {
				boolean wall = x == x0 || z == z0 || x == x0 + 2 || z == z0 + 2;
				if (wall) {
					put(x, base + 1, z, c(col));
					put(x, base + 2, z, z == z0 + 2 && x == x0 + 1 ? Blocks.GLASS_PANE.defaultBlockState() : c(DyeColor.WHITE));
				}
				put(x, base + 3, z, Blocks.CONCRETE_SLAB.pick(col).defaultBlockState());
			}
		}
		put(x0 + 1, base + 3, z0 + 2, Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState());
		put(x0 + 1, base + 2, z0 + 1, b(Blocks.OCHRE_FROGLIGHT));
		put(x0 + 1, base + 1, z0 + 3, slabTop(Blocks.SMOOTH_STONE_SLAB));
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
		for (int y = 1; y <= 3; y++) {
			put(x0, base + y, z0, b(Blocks.IRON_BARS));
			put(x0 + 4, base + y, z0, b(Blocks.IRON_BARS));
		}
		DyeColor a = DyeColor.values()[r.nextInt(16)];
		DyeColor bcol = DyeColor.values()[r.nextInt(16)];
		for (int x = x0; x <= x0 + 4; x++) {
			for (int y = 4; y <= 6; y++) {
				put(x, base + y, z0, Blocks.GLAZED_TERRACOTTA.pick((x + y) % 3 == 0 ? a : bcol).defaultBlockState());
			}
			put(x, base + 7, z0, b(Blocks.SMOOTH_STONE_SLAB));
		}
		put(x0 + 1, base + 6, z0 + 1, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
		put(x0 + 3, base + 6, z0 + 1, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
	}

	/** A drinks machine and a cash machine side by side. */
	private void vending(int x0, int z0) {
		put(x0, base + 1, z0, c(DyeColor.RED));
		put(x0, base + 2, z0, Blocks.STAINED_GLASS.pick(DyeColor.RED).defaultBlockState());
		put(x0 + 1, base + 1, z0, b(Blocks.POLISHED_ANDESITE));
		put(x0 + 1, base + 2, z0, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
		put(x0 + 1, base + 1, z0 + 1, Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
	}

	private void bikeRack(int x0, int z0) {
		for (int x = x0; x < x0 + 4; x++) {
			put(x, base + 1, z0, b(Blocks.IRON_BARS));
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Villas: fences round each garden, a driveway with a car, flowers, cats

	private void villas(int bx, int bz, List<Building> here, int[][] g) {
		Block[] fences = {Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE, Blocks.DARK_OAK_FENCE, Blocks.IRON_BARS, Blocks.AZALEA_LEAVES};
		for (int qx = -1; qx <= 1; qx += 2) {
			for (int qz = -1; qz <= 1; qz += 2) {
				Block fb = fences[r.nextInt(fences.length)];
				BlockState fence = fb == Blocks.AZALEA_LEAVES ? fb.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true)
						: fb.defaultBlockState();
				int x0 = qx < 0 ? bx - 12 : bx + 1;
				int x1 = qx < 0 ? bx - 1 : bx + 12;
				int z0 = qz < 0 ? bz - 12 : bz + 1;
				int z1 = qz < 0 ? bz - 1 : bz + 12;
				Building home = null;
				for (Building b : here) {
					if (b.origin.getX() >= x0 - 1 && b.origin.getX() <= x1 + 1 && b.origin.getZ() >= z0 - 1 && b.origin.getZ() <= z1 + 1) {
						home = b;
					}
				}
				// The fence along the street side, with the gate in front of the door.
				int edgeZ = qz < 0 ? bz - 12 : bz + 12;
				for (int x = x0; x <= x1; x++) {
					if (home != null && Math.abs(x - home.origin.getX()) <= 2) {
						force(x, base, edgeZ, b(Blocks.POLISHED_ANDESITE));
						continue;
					}
					put(x, base + 1, edgeZ, fence);
				}
				int edgeX = qx < 0 ? bx - 12 : bx + 12;
				for (int z = z0; z <= z1; z++) {
					put(edgeX, base + 1, z, fence);
				}
				// The driveway between the house and the middle of the block, a car on it.
				int dx = qx < 0 ? bx - 2 : bx + 1;
				int dz0 = qz < 0 ? bz - 6 : bz + 3;
				if (free(g, bx, bz, dx, dz0) && free(g, bx, bz, dx + 1, dz0 + 3)) {
					for (int z = dz0; z <= dz0 + 3; z++) {
						force(dx, base, z, c(DyeColor.GRAY));
						force(dx + 1, base, z, c(DyeColor.GRAY));
					}
					if (r.nextInt(3) > 0) {
						car(dx, dz0, true, CAR_COLORS[r.nextInt(CAR_COLORS.length)]);
					}
					use(dx, dz0, dx + 1, dz0 + 3);
				}
				// A hedge on the inner sides, flowers, maybe a cat.
				for (int z = z0; z <= z1; z++) {
					int x = qx < 0 ? bx - 1 : bx + 1;
					if (free(g, bx, bz, x, z) && r.nextInt(3) == 0) {
						put(x, base + 1, z, b(r.nextBoolean() ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA));
					}
				}
				if (r.nextInt(4) == 0) {
					spawn(qx < 0 ? bx - 3 : bx + 3, qz < 0 ? bz - 2 : bz + 2, 0);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The central square behind the city hall: a lit fountain, flags, benches, lamps

	private void square(int bx, int bz, int[][] g) {
		int[] q = findRect(g, bx, bz, 9, 5);
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
			put(x, base + 1, z0 + 4, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
			put(x, base + 1, z0 + 2, b(Blocks.OAK_FENCE));
			put(x, base + 2, z0 + 2, b(Blocks.LANTERN));
		}
	}
}
