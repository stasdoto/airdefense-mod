package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds the planned cities and roads ({@link Cities}) into the world, one chunk at a time, as the chunk generates:
 * the roads first (asphalt over the land, bridges over water), then each city: the ground levelled to the city's
 * height (and blended back into the land around it), streets with markings, pavements and street lamps, the
 * buildings, the bell of the central square, a few people in the streets. Only blocks of the chunk itself are touched,
 * so every chunk can be built on its own, in any order.
 */
public final class CityGen {
	/** For the tests: chunks built and the time it took. */
	public static volatile int chunks;
	public static volatile long nanos;

	private static final ConcurrentHashMap<Long, List<Blueprints.Placement>> PLANS = new ConcurrentHashMap<>();

	private static final BlockState ASPHALT = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState ASPHALT_SLAB = Blocks.CONCRETE_SLAB.pick(DyeColor.GRAY).defaultBlockState();
	/** Markings on the half-block steps of a road on a slope. */
	private static final BlockState MARK_SLAB = Blocks.CONCRETE_SLAB.pick(DyeColor.WHITE).defaultBlockState();
	private static final BlockState MARK = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	private static final BlockState KERB = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState WALKWAY = Blocks.POLISHED_ANDESITE.defaultBlockState();
	private static final BlockState PILLAR = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState RAIL = Blocks.IRON_BARS.defaultBlockState();
	/** A highway's crash barrier. */
	private static final BlockState BARRIER = Blocks.ANDESITE_WALL.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private CityGen() {
	}

	private static final ConcurrentHashMap<Long, CityDecor.Result> DECOR = new ConcurrentHashMap<>();

	static CityDecor.Result decor(Cities.City c) {
		CityDecor.Result d = DECOR.get(c.key());
		if (d == null) {
			if (DECOR.size() > 12) {
				DECOR.clear();
			}
			d = CityDecor.build(c);
			DECOR.put(c.key(), d);
		}
		return d;
	}

	static void clearCache() {
		DECOR.clear();
		PLANS.clear();
		HAMLET_DECOR.clear();
	}

	/** Builds whatever of the cities and roads falls into this chunk. */
	public static void generate(WorldGenLevel level, Cities.Terrain t, long seed, ChunkPos cp) {
		long t0 = System.nanoTime();
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		int mx = cp.getMiddleBlockX();
		int mz = cp.getMiddleBlockZ();
		List<Cities.City> cities = new ArrayList<>();
		for (Cities.City c : Cities.citiesAround(seed, t, mx, mz)) {
			int reach = c.half() + Cities.STREET_HALF + Cities.MARGIN + 8;
			if (Math.abs(mx - c.x) <= reach + 8 && Math.abs(mz - c.z) <= reach + 8) {
				cities.add(c);
			}
		}
		List<Cities.Road> roads = new ArrayList<>();
		for (Cities.Road r : Cities.roadsNear(seed, t, mx, mz)) {
			if (r.maxX >= x0 - 4 && r.minX <= x0 + 19 && r.maxZ >= z0 - 4 && r.minZ <= z0 + 19) {
				roads.add(r);
			}
		}
		List<Hamlets.Hamlet> hamlets = new ArrayList<>();
		for (Cities.City c : Cities.citiesAround(seed, t, mx, mz)) {
			for (Hamlets.Hamlet h : c.hamlets(seed, t)) {
				if (h.near(mx, mz, 10)) {
					hamlets.add(h);
				}
			}
		}
		List<Depots.Depot> depots = new ArrayList<>();
		for (Cities.City c : Cities.citiesAround(seed, t, mx, mz)) {
			Depots.Depot d = c.depot(seed, t);
			if (d != null && d.near(mx, mz, 30)) {
				depots.add(d);
			}
		}
		if (cities.isEmpty() && roads.isEmpty() && hamlets.isEmpty() && depots.isEmpty()) {
			return;
		}
		Writer w = new Writer(level);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Cities.Road.Spot probe = new Cities.Road.Spot();
		Cities.Road.Spot spot = new Cities.Road.Spot();
		for (int x = x0; x < x0 + 16; x++) {
			for (int z = z0; z < z0 + 16; z++) {
				Cities.City in = null;
				Cities.City near = null;
				for (Cities.City c : cities) {
					if (c.inside(x, z)) {
						in = c;
					} else if (c.outside(x, z) <= Cities.MARGIN) {
						near = c;
					}
				}
				if (in != null) {
					cityColumn(w, t, in, x, z, pos);
					continue;
				}
				Cities.Road road = null;
				double best = Double.MAX_VALUE;
				for (Cities.Road r : roads) {
					if (!r.locate(x + 0.5, z + 0.5, r.half + 8, probe)) {
						continue;
					}
					if (probe.along < -1 || probe.along > r.length + 1) {
						continue;
					}
					double d = Math.abs(probe.across) - r.halfAt(probe.along);
					if (d < best) {
						best = d;
						road = r;
						spot.along = probe.along;
						spot.across = probe.across;
						spot.ux = probe.ux;
						spot.uz = probe.uz;
					}
				}
				if (road != null && best <= 0.5) {
					roadColumn(w, t, road, spot, x, z, pos);
					continue;
				}
				boolean done = false;
				for (Depots.Depot d : depots) {
					if (depotColumn(w, d, x, z, pos)) {
						done = true;
						break;
					}
				}
				if (done) {
					continue;
				}
				for (Hamlets.Hamlet h : hamlets) {
					if (h.near(x, z, 0) && hamletColumn(w, t, h, x, z, pos)) {
						done = true;
						break;
					}
				}
				if (done) {
					continue;
				}
				// The embankment or cutting beside the road: wide enough out in the country that deep cuttings get slopes, not walls.
				if (road != null && (best <= 3.5 || near == null && best <= 7.5)) {
					roadColumn(w, t, road, spot, x, z, pos);
				}
				if (near != null && (road == null || best > 3.5)) {
					marginColumn(w, t, near, x, z, pos);
				}
			}
		}
		for (Cities.City c : cities) {
			buildings(w, c, cp);
			decor(w, c, cp);
			details(w, c, cp, seed);
		}
		for (Hamlets.Hamlet h : hamlets) {
			hamlet(w, h, cp);
		}
		for (Depots.Depot d : depots) {
			depot(w, d, cp);
		}
		w.finish();
		chunks++;
		nanos += System.nanoTime() - t0;
	}

	/**
	 * Once a chunk is complete (its neighbours have grown their trees too): leaves and trunks that reached over into
	 * the streets, the yards and the roads go (the trees of the parks stay).
	 */
	public static void tidy(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
		if (!CityFeature.enabled || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return;
		}
		ChunkPos cp = chunk.getPos();
		Cities.Terrain t = Cities.terrain(level);
		long seed = level.getSeed();
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		List<Cities.City> cities = new ArrayList<>();
		for (Cities.City c : Cities.citiesAround(seed, t, cp.getMiddleBlockX(), cp.getMiddleBlockZ())) {
			if (c.outside(x0 + 8, z0 + 8) <= 12) {
				cities.add(c);
			}
		}
		List<Cities.Road> roads = new ArrayList<>();
		for (Cities.Road r : Cities.roadsNear(seed, t, cp.getMiddleBlockX(), cp.getMiddleBlockZ())) {
			if (r.maxX >= x0 && r.minX <= x0 + 15 && r.maxZ >= z0 && r.minZ <= z0 + 15) {
				roads.add(r);
			}
		}
		List<Hamlets.Hamlet> hamlets = new ArrayList<>();
		for (Cities.City c : Cities.citiesAround(seed, t, cp.getMiddleBlockX(), cp.getMiddleBlockZ())) {
			for (Hamlets.Hamlet h : c.hamlets(seed, t)) {
				if (h.near(x0 + 8, z0 + 8, 10)) {
					hamlets.add(h);
				}
			}
		}
		List<Depots.Depot> depots = new ArrayList<>();
		for (Cities.City c : Cities.citiesAround(seed, t, cp.getMiddleBlockX(), cp.getMiddleBlockZ())) {
			Depots.Depot d = c.depot(seed, t);
			if (d != null && d.near(x0 + 8, z0 + 8, 12)) {
				depots.add(d);
			}
		}
		if (cities.isEmpty() && roads.isEmpty() && hamlets.isEmpty() && depots.isEmpty()) {
			return;
		}
		// The logs of our own buildings (timber frames, barns) stay.
		java.util.Set<Long> keep = new java.util.HashSet<>();
		for (Cities.City c : cities) {
			for (Building b : c.buildings()) {
				if (near(b, x0, z0)) {
					keepLogs(keep, plan(c, b), x0, z0);
				}
			}
		}
		for (Hamlets.Hamlet h : hamlets) {
			for (Building b : h.buildings) {
				if (near(b, x0, z0)) {
					keepLogs(keep, hamletPlan(h, b), x0, z0);
				}
			}
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Cities.Road.Spot probe = new Cities.Road.Spot();
		for (int x = x0; x < x0 + 16; x++) {
			for (int z = z0; z < z0 + 16; z++) {
				int from = Integer.MIN_VALUE;
				for (Hamlets.Hamlet h : hamlets) {
					for (Hamlets.Pad pd : h.pads) {
						if (pd.out(x, z) == 0) {
							from = pd.y() + 1;
						}
					}
				}
				for (Depots.Depot dp : depots) {
					if (dp.out(x, z) <= 2) {
						from = dp.y + 1;
					}
				}
				int span = 28;
				for (Cities.City c : cities) {
					if (c.inside(x, z)) {
						boolean park = false;
						for (Building b : c.buildings()) {
							if (b.type == BuildingType.PARK && Math.abs(b.origin.getX() - x) < 30 && Math.abs(b.origin.getZ() - z) < 30 && b.covers(x, z, 1)) {
								park = true;
								break;
							}
						}
						if (!park) {
							from = c.base + 1;
							span = 64;
						}
					}
				}
				if (from == Integer.MIN_VALUE) {
					for (Cities.Road r : roads) {
						if (r.locate(x + 0.5, z + 0.5, r.half + 1, probe) && probe.along >= 0 && probe.along <= r.length
								&& Math.abs(probe.across) <= r.halfAt(probe.along) + 0.5) {
							from = (int) Math.floor(r.height(probe.along)) + 1;
						}
					}
				}
				if (from == Integer.MIN_VALUE) {
					continue;
				}
				for (int y = from; y < from + span; y++) {
					BlockState st = chunk.getBlockState(pos.set(x, y, z));
					if (st.is(BlockTags.LEAVES) && st.hasProperty(BlockStateProperties.PERSISTENT) && st.getValue(BlockStateProperties.PERSISTENT)) {
						continue;
					}
					if (st.is(BlockTags.LOGS) && (keep.contains(pos.asLong()) || ours(cities, pos))) {
						continue;
					}
					if (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(Blocks.VINE) || st.is(Blocks.BEE_NEST) || st.is(Blocks.SNOW)
							|| st.is(Blocks.RED_MUSHROOM_BLOCK) || st.is(Blocks.BROWN_MUSHROOM_BLOCK) || st.is(Blocks.MUSHROOM_STEM)) {
						chunk.setBlockState(pos, AIR, 0);
					}
				}
			}
		}
	}

	private static boolean ours(List<Cities.City> cities, BlockPos pos) {
		for (Cities.City c : cities) {
			if (decor(c).trees.contains(pos.asLong())) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------
	// Columns

	/** The ground of a column: the top solid block under trees, plants, snow and water; and how high it all goes. */
	private static int[] ground(Writer w, int x, int z, BlockPos.MutableBlockPos pos) {
		int top = w.top(x, z);
		int y = top;
		int wet = 0;
		int min = w.level.getMinY();
		while (y > min) {
			BlockState s = w.get(pos.set(x, y, z));
			if (!s.getFluidState().isEmpty()) {
				wet = 1;
				y--;
				continue;
			}
			if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS)
					|| s.is(Blocks.SUGAR_CANE) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)) {
				y--;
				continue;
			}
			break;
		}
		return new int[]{y, Math.max(top, y), wet};
	}

	/** Raises or cuts the column to {@code target} with {@code surface} on top, clears everything above. */
	private static void shape(Writer w, int x, int z, int target, BlockState surface, int[] g, BlockPos.MutableBlockPos pos) {
		int ground = g[0];
		if (ground < target) {
			for (int y = ground + 1; y < target; y++) {
				w.set(pos.set(x, y, z), target - y > 3 ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState());
			}
		}
		w.set(pos.set(x, target, z), surface);
		for (int y = target + 1; y <= g[1] + 1; y++) {
			if (!w.get(pos.set(x, y, z)).isAir()) {
				w.set(pos, AIR);
			}
		}
	}

	private static void cityColumn(Writer w, Cities.Terrain t, Cities.City c, int x, int z, BlockPos.MutableBlockPos pos) {
		int[] g = ground(w, x, z, pos);
		CityShape.Probe p = c.shape().probe(x, z);
		int zebra = Cities.STREET_HALF + CityShape.WALK;
		BlockState surface;
		if (p.street) {
			surface = ASPHALT;
			if (p.onV && !p.onH) {
				if (p.dv == 0 && Math.floorMod(z, 6) < 3 || p.node && p.dh >= zebra && p.dh <= zebra + 1 && (x & 1) == 0) {
					surface = MARK;
				}
			} else if (p.onH && !p.onV) {
				if (p.dh == 0 && Math.floorMod(x, 6) < 3 || p.node && p.dv >= zebra && p.dv <= zebra + 1 && (z & 1) == 0) {
					surface = MARK;
				}
			}
		} else if (p.kerb) {
			// The kerb stones along the street, the walk behind them.
			surface = Math.min(p.dv, p.dh) == Cities.STREET_HALF + 1 ? KERB : WALKWAY;
		} else {
			surface = Blocks.GRASS_BLOCK.defaultBlockState();
		}
		shape(w, x, z, c.base, surface, g, pos);
	}

	private static void marginColumn(Writer w, Cities.Terrain t, Cities.City c, int x, int z, BlockPos.MutableBlockPos pos) {
		int[] g = ground(w, x, z, pos);
		double f = c.outside(x, z) / (double) (Cities.MARGIN + 1);
		f = f * f * (3 - 2 * f);
		int target = (int) Math.round(c.base * (1 - f) + g[0] * f);
		if (target == g[0] && g[2] == 0) {
			return;
		}
		BlockState top = w.get(pos.set(x, g[0], z));
		BlockState surface = top.is(BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK) || top.is(BlockTags.DIRT) ? top : Blocks.GRASS_BLOCK.defaultBlockState();
		if (g[2] == 1 && target <= t.sea()) {
			// Shore: leave the water where the land stays under it.
			if (target < g[0]) {
				return;
			}
			for (int y = g[0] + 1; y <= target; y++) {
				w.set(pos.set(x, y, z), Blocks.SAND.defaultBlockState());
			}
			return;
		}
		shape(w, x, z, target, surface, g, pos);
	}

	private static void roadColumn(Writer w, Cities.Terrain t, Cities.Road r, Cities.Road.Spot spot, int x, int z, BlockPos.MutableBlockPos pos) {
		double along = spot.along;
		double d = Math.abs(spot.across);
		double half = r.halfAt(along);
		float h = r.height(along);
		int y = (int) Math.floor(h);
		boolean slab = h - y >= 0.5f;
		int[] g = ground(w, x, z, pos);
		if (d > half + 0.5) {
			// The embankment or the cutting next to the road.
			double side = d - half - 0.5;
			if (g[2] == 1) {
				return;
			}
			if (g[0] < y - 1) {
				int top = y - (int) Math.ceil(side);
				if (top > g[0]) {
					shape(w, x, z, top, Blocks.GRASS_BLOCK.defaultBlockState(), g, pos);
				}
			} else if (g[0] > y + 1) {
				int top = y + (int) Math.ceil(side * 1.5);
				if (top < g[0]) {
					BlockState s = w.get(pos.set(x, g[0], z));
					shape(w, x, z, top, s.is(BlockTags.DIRT) ? Blocks.GRASS_BLOCK.defaultBlockState() : s, g, pos);
				}
			}
			return;
		}
		boolean bridge = g[2] == 1 || g[0] < y - 6;
		if (bridge) {
			int deck = Math.max(y, t.sea() + 2);
			w.set(pos.set(x, deck, z), r.dirt ? Blocks.SPRUCE_PLANKS.defaultBlockState() : surface(r, along, d, half, diagonal(spot)));
			for (int yy = deck + 1; yy <= Math.max(deck + 4, g[1] + 1); yy++) {
				if (!w.get(pos.set(x, yy, z)).isAir()) {
					w.set(pos, AIR);
				}
			}
			if (d > half - 0.5) {
				w.set(pos.set(x, deck + 1, z), r.dirt ? Blocks.SPRUCE_FENCE.defaultBlockState() : RAIL);
			}
			if (Math.floorMod((int) along, 24) < 2 && d <= half - 0.5 && (!r.highway || d < 1.5 || d > half - 2.5)) {
				for (int yy = deck - 1; yy > w.level.getMinY(); yy--) {
					BlockState s = w.get(pos.set(x, yy, z));
					if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) {
						break;
					}
					w.set(pos, r.dirt ? Blocks.SPRUCE_LOG.defaultBlockState() : PILLAR);
				}
			}
			return;
		}
		if (r.dirt) {
			int yy = (int) Math.round(h);
			long hsh = mix(x * 31L + z);
			BlockState dirt = Math.floorMod(hsh, 7) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Math.floorMod(hsh, 11) == 0
					? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
			shape(w, x, z, yy, dirt, g, pos);
			return;
		}
		BlockState top = surface(r, along, d, half, diagonal(spot));
		shape(w, x, z, y, slab ? ASPHALT : top, g, pos);
		if (slab) {
			// The markings go on the half-block step, so they run on unbroken up and down the slopes.
			w.set(pos.set(x, y + 1, z), (top == MARK ? MARK_SLAB : ASPHALT_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
		}
		// Crash barriers along a highway where it runs high on an embankment.
		if (r.highway && d > half - 0.5 && g[0] < y - 2 && !slab) {
			w.set(pos.set(x, y + 1, z), BARRIER);
		}
	}

	/** The road surface with its markings: a highway's centre line, lane lines and edge lines; a town road's dashes. */
	/** Running across the grid rather than along it: thin lines there come out as saw teeth of single blocks. */
	private static boolean diagonal(Cities.Road.Spot spot) {
		return Math.min(Math.abs(spot.ux), Math.abs(spot.uz)) > 0.3;
	}

	private static BlockState surface(Cities.Road r, double along, double d, double half, boolean diagonal) {
		if (r.highway) {
			boolean full = half > r.half - 0.6;
			if (d < 0.5) {
				return MARK;
			}
			if (full && d >= 3.0 && d < 4.0 && Math.floorMod((int) along, 12) < 6) {
				return MARK;
			}
			// The edge lines only where the road runs along the grid (diagonally they would be teeth, not a line).
			if (full && !diagonal && d >= 6.0 && d < 6.8) {
				return MARK;
			}
			return ASPHALT;
		}
		if (r.half <= Cities.COUNTRY_HALF) {
			return ASPHALT;
		}
		return d < 0.5 && Math.floorMod((int) along, 8) < 4 ? MARK : ASPHALT;
	}

	// ------------------------------------------------------------------------------------------------
	// Depots (1.25): the fenced yard of asphalt with its parking lines and light masts, blended into the land

	private static final int DEPOT_BLEND = 7;

	private static boolean depotColumn(Writer w, Depots.Depot d, int x, int z, BlockPos.MutableBlockPos pos) {
		int o = d.out(x, z);
		if (o > DEPOT_BLEND) {
			return false;
		}
		int[] g = ground(w, x, z, pos);
		if (o == 0) {
			boolean edge = x == d.x0 || x == d.x1 || z == d.z0 || z == d.z1;
			boolean gate = Math.abs(x - d.gateX) <= 4 && Math.abs(z - d.gateZ) <= 4;
			shape(w, x, z, d.y, yardSurface(d, x, z), g, pos);
			if (edge && !gate) {
				w.set(pos.set(x, d.y + 1, z), RAIL);
				w.set(pos.set(x, d.y + 2, z), RAIL);
			}
			// Light masts along the fence (inside it), every 24 blocks.
			boolean nearEdge = x == d.x0 + 1 || x == d.x1 - 1 || z == d.z0 + 1 || z == d.z1 - 1;
			if (nearEdge && Math.floorMod(x + z, 24) == 0 && !gate) {
				for (int y = 1; y <= 7; y++) {
					w.set(pos.set(x, d.y + y, z), RAIL);
				}
				w.set(pos.set(x, d.y + 8, z), Blocks.SEA_LANTERN.defaultBlockState());
				w.set(pos.set(x, d.y + 9, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
			}
			return true;
		}
		if (g[2] == 1) {
			return false;
		}
		double f = o / (double) (DEPOT_BLEND + 1);
		f = f * f * (3 - 2 * f);
		int target = (int) Math.round(d.y * (1 - f) + g[0] * f);
		if (target != g[0]) {
			BlockState top = w.get(pos.set(x, g[0], z));
			BlockState surface = top.is(BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK) || top.is(BlockTags.DIRT) ? top : GRASS;
			shape(w, x, z, target, surface, g, pos);
		}
		return true;
	}

	/** The yard: asphalt, white parking bays in front of the warehouses. */
	private static BlockState yardSurface(Depots.Depot d, int x, int z) {
		int distFront;
		int along;
		switch (d.front) {
			case NORTH -> {
				distFront = d.z0 + 3 + 24 - z;
				along = x;
			}
			case SOUTH -> {
				distFront = z - (d.z1 - 3 - 24);
				along = x;
			}
			case WEST -> {
				distFront = d.x0 + 3 + 24 - x;
				along = z;
			}
			default -> {
				distFront = x - (d.x1 - 3 - 24);
				along = z;
			}
		}
		if (distFront >= 2 && distFront <= 9 && Math.floorMod(along, 5) == 0) {
			return MARK;
		}
		if (distFront == 10 && Math.floorMod(along, 4) < 2) {
			return MARK;
		}
		return ASPHALT;
	}

	private static void depot(Writer w, Depots.Depot d, ChunkPos cp) {
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		for (Building b : d.buildings) {
			int reach = Math.max(b.type.width, b.type.depth) + 3;
			if (b.origin.getX() + reach < x0 || b.origin.getX() - reach > x0 + 15 || b.origin.getZ() + reach < z0 || b.origin.getZ() - reach > z0 + 15) {
				continue;
			}
			long key = -(d.key() * 64 + b.id) - 7_000_000L;
			List<Blueprints.Placement> plan = PLANS.get(key);
			if (plan == null) {
				if (PLANS.size() > 160) {
					PLANS.clear();
				}
				plan = Blueprints.placements(b, DyeColor.byId(d.city.color));
				PLANS.put(key, plan);
			}
			for (Blueprints.Placement pl : plan) {
				boolean mine = in(pl.pos(), x0, z0);
				if (pl.pair()) {
					if (mine || in(pl.pos2(), x0, z0)) {
						w.set(pl.pos(), pl.state());
						w.set(pl.pos2(), pl.state2());
					}
				} else if (mine) {
					w.set(pl.pos(), pl.state());
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Hamlets

	private static final BlockState FARMLAND = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();

	/** The ground of a hamlet: house pads, fields, the square, blended into the land; dirt paths. False if none of it is here. */
	private static boolean hamletColumn(Writer w, Cities.Terrain t, Hamlets.Hamlet h, int x, int z, BlockPos.MutableBlockPos pos) {
		int blend = Integer.MAX_VALUE;
		int blendY = 0;
		int reach = 1;
		for (Hamlets.Pad p : h.pads) {
			int o = p.out(x, z);
			if (o == 0) {
				int[] g = ground(w, x, z, pos);
				shape(w, x, z, p.y(), GRASS, g, pos);
				return true;
			}
			if (o <= 5 && o < blend) {
				blend = o;
				blendY = p.y();
				reach = 5;
			}
		}
		for (Hamlets.Field f : h.fields) {
			Hamlets.Pad p = f.pad();
			int o = p.out(x, z);
			if (o <= 1) {
				int[] g = ground(w, x, z, pos);
				BlockState surface = GRASS;
				if (o == 0 && f.crop() != Hamlets.PADDOCK) {
					boolean channel = f.alongX() ? z == (p.z0() + p.z1()) / 2 : x == (p.x0() + p.x1()) / 2;
					surface = channel ? Blocks.WATER.defaultBlockState() : FARMLAND;
				} else if (o == 1) {
					surface = Blocks.COARSE_DIRT.defaultBlockState();
				}
				shape(w, x, z, p.y(), surface, g, pos);
				if (surface.is(Blocks.WATER)) {
					w.set(pos.set(x, p.y() - 1, z), Blocks.DIRT.defaultBlockState());
				}
				return true;
			}
			if (o - 1 <= 3 && o - 1 < blend) {
				blend = o - 1;
				blendY = p.y();
				reach = 3;
			}
		}
		int sq = Math.max(Math.abs(x - h.x), Math.abs(z - h.z));
		if (sq <= Hamlets.Hamlet.SQUARE) {
			int[] g = ground(w, x, z, pos);
			long hsh = mix(x * 0x9E3779B1L ^ z * 0x85EBCA77L);
			BlockState s = switch ((int) Math.floorMod(hsh, 6)) {
				case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
				case 1 -> Blocks.GRAVEL.defaultBlockState();
				case 2 -> Blocks.COBBLESTONE.defaultBlockState();
				default -> Blocks.DIRT_PATH.defaultBlockState();
			};
			shape(w, x, z, h.base, s, g, pos);
			return true;
		}
		if (sq - Hamlets.Hamlet.SQUARE <= 4 && sq - Hamlets.Hamlet.SQUARE < blend) {
			blend = sq - Hamlets.Hamlet.SQUARE;
			blendY = h.base;
			reach = 4;
		}
		boolean path = h.paths.contains(BlockPos.asLong(x, 0, z));
		if (blend == Integer.MAX_VALUE && !path) {
			return false;
		}
		int[] g = ground(w, x, z, pos);
		if (g[2] == 1 && blend == Integer.MAX_VALUE) {
			return false;
		}
		int target = g[0];
		if (blend != Integer.MAX_VALUE) {
			double f = blend / (double) (reach + 1);
			f = f * f * (3 - 2 * f);
			target = (int) Math.round(blendY * (1 - f) + g[0] * f);
		}
		BlockState top = w.get(pos.set(x, g[0], z));
		BlockState surface = path ? Blocks.DIRT_PATH.defaultBlockState()
				: top.is(BlockTags.DIRT) || top.is(BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.GRAVEL) ? top : GRASS;
		if (target == g[0] && !path) {
			if (blend <= 3 && g[1] > g[0]) {
				// Right next to the houses and fields: no trees in the way.
				shape(w, x, z, target, top, g, pos);
			}
			return true;
		}
		if (target == g[0]) {
			w.set(pos.set(x, g[0], z), surface);
			BlockState above = w.get(pos.set(x, g[0] + 1, z));
			if (!above.isAir() && above.canBeReplaced()) {
				w.set(pos, AIR);
			}
			return true;
		}
		shape(w, x, z, target, surface, g, pos);
		return true;
	}

	private static final ConcurrentHashMap<Long, CityDecor.Result> HAMLET_DECOR = new ConcurrentHashMap<>();

	private static List<Blueprints.Placement> hamletPlan(Hamlets.Hamlet h, Building b) {
		long key = -(h.key() * 64 + b.id) - 1;
		List<Blueprints.Placement> plan = PLANS.get(key);
		if (plan == null) {
			if (PLANS.size() > 160) {
				PLANS.clear();
			}
			plan = Blueprints.placements(b, DyeColor.byId(h.city.color));
			PLANS.put(key, plan);
		}
		return plan;
	}

	/** Positions in this chunk where the building's plan puts a log. */
	private static boolean near(Building b, int x0, int z0) {
		int reach = Math.max(b.type.width, b.type.depth) + 3;
		return !(b.origin.getX() + reach < x0 || b.origin.getX() - reach > x0 + 15 || b.origin.getZ() + reach < z0 || b.origin.getZ() - reach > z0 + 15);
	}

	private static void keepLogs(java.util.Set<Long> keep, List<Blueprints.Placement> plan, int x0, int z0) {
		for (Blueprints.Placement pl : plan) {
			if (pl.state().is(BlockTags.LOGS) && in(pl.pos(), x0, z0)) {
				keep.add(pl.pos().asLong());
			}
		}
	}

	/** The hamlet's buildings, wells, fences, crops, people and animals that fall in this chunk. */
	private static void hamlet(Writer w, Hamlets.Hamlet h, ChunkPos cp) {
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		for (Building b : h.buildings) {
			int reach = Math.max(b.type.width, b.type.depth) + 3;
			if (b.origin.getX() + reach < x0 || b.origin.getX() - reach > x0 + 15 || b.origin.getZ() + reach < z0 || b.origin.getZ() - reach > z0 + 15) {
				continue;
			}
			for (Blueprints.Placement pl : hamletPlan(h, b)) {
				boolean mine = in(pl.pos(), x0, z0);
				if (pl.pair()) {
					if (mine || in(pl.pos2(), x0, z0)) {
						w.set(pl.pos(), pl.state());
						w.set(pl.pos2(), pl.state2());
					}
				} else if (mine) {
					w.set(pl.pos(), pl.state());
				}
			}
		}
		CityDecor.Result d = HAMLET_DECOR.get(h.key());
		if (d == null) {
			if (HAMLET_DECOR.size() > 16) {
				HAMLET_DECOR.clear();
			}
			d = HamletDecor.build(h);
			HAMLET_DECOR.put(h.key(), d);
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<CityDecor.D> list = d.blocks.get(cp.pack());
		if (list != null) {
			for (CityDecor.D b : list) {
				pos.set(b.x(), b.y(), b.z());
				BlockState cur = w.get(pos);
				if (b.force() || cur.isAir() || cur.canBeReplaced()) {
					w.set(pos, b.s());
				}
			}
		}
		List<CityDecor.Spawn> spawns = d.spawns.get(cp.pack());
		if (spawns != null) {
			for (CityDecor.Spawn s : spawns) {
				if (!w.get(pos.set(s.x(), s.y(), s.z())).isAir()) {
					continue;
				}
				var type = switch (s.kind()) {
					case 1 -> EntityTypes.VILLAGER;
					case 2 -> EntityTypes.COW;
					case 3 -> EntityTypes.SHEEP;
					case 4 -> EntityTypes.CHICKEN;
					case 5 -> EntityTypes.PIG;
					default -> EntityTypes.CAT;
				};
				var e = type.create(w.level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (e != null) {
					e.snapTo(s.x() + 0.5, s.y(), s.z() + 0.5, (float) Math.floorMod(s.x() * 37 + s.z() * 11, 360), 0);
					if (e instanceof net.minecraft.world.entity.Mob m) {
						m.setPersistenceRequired();
					}
					w.level.addFreshEntity(e);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Buildings and details

	private static List<Blueprints.Placement> plan(Cities.City c, Building b) {
		long key = c.key() * 4096 + b.id;
		List<Blueprints.Placement> p = PLANS.get(key);
		if (p == null) {
			if (PLANS.size() > 160) {
				PLANS.clear();
			}
			p = Blueprints.placements(b, DyeColor.byId(c.color));
			PLANS.put(key, p);
		}
		return p;
	}

	private static void buildings(Writer w, Cities.City c, ChunkPos cp) {
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		for (Building b : c.buildings()) {
			int reach = Math.max(b.type.width, b.type.depth) + 3;
			if (b.origin.getX() + reach < x0 || b.origin.getX() - reach > x0 + 15 || b.origin.getZ() + reach < z0 || b.origin.getZ() - reach > z0 + 15) {
				continue;
			}
			for (Blueprints.Placement pl : plan(c, b)) {
				boolean mine = in(pl.pos(), x0, z0);
				if (pl.pair()) {
					if (mine || in(pl.pos2(), x0, z0)) {
						w.set(pl.pos(), pl.state());
						w.set(pl.pos2(), pl.state2());
					}
				} else if (mine) {
					w.set(pl.pos(), pl.state());
				}
			}
		}
	}

	/** Lamps, trees, benches, cars... of the city that fall in this chunk; a cat or two. */
	private static void decor(Writer w, Cities.City c, ChunkPos cp) {
		CityDecor.Result d = decor(c);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<CityDecor.D> list = d.blocks.get(cp.pack());
		if (list != null) {
			for (CityDecor.D b : list) {
				pos.set(b.x(), b.y(), b.z());
				BlockState cur = w.get(pos);
				if (b.force() || cur.isAir() || cur.canBeReplaced()) {
					w.set(pos, b.s());
				}
			}
		}
		List<CityDecor.Spawn> spawns = d.spawns.get(cp.pack());
		if (spawns != null) {
			for (CityDecor.Spawn s : spawns) {
				var cat = EntityTypes.CAT.create(w.level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (cat != null && w.get(pos.set(s.x(), s.y(), s.z())).isAir()) {
					cat.snapTo(s.x() + 0.5, s.y(), s.z() + 0.5, 0, 0);
					cat.setPersistenceRequired();
					w.level.addFreshEntity(cat);
				}
			}
		}
	}

	private static boolean in(BlockPos p, int x0, int z0) {
		return p.getX() >= x0 && p.getX() < x0 + 16 && p.getZ() >= z0 && p.getZ() < z0 + 16;
	}

	/** The bell of the square, people in the streets. */
	private static void details(Writer w, Cities.City c, ChunkPos cp, long seed) {
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int half = c.half();
		CityShape sh = c.shape();
		BlockPos bell = c.bell();
		if (in(bell, x0, z0)) {
			w.set(bell.below(), Blocks.POLISHED_ANDESITE.defaultBlockState());
			w.set(bell, Blocks.BELL.defaultBlockState());
		}
		// People: about size.villagers in all, one in a chunk here and there, standing in a street.
		int chunksIn = Math.max(1, (sh.maxX - sh.minX) / 16 + 1) * Math.max(1, (sh.maxZ - sh.minZ) / 16 + 1);
		long h = mix(seed ^ c.key() * 31 ^ cp.pack());
		if (Math.floorMod(h, chunksIn) >= c.size.villagers || !c.inside(cp.getMiddleBlockX(), cp.getMiddleBlockZ())) {
			return;
		}
		for (int x = x0; x < x0 + 16; x++) {
			for (int z = z0; z < z0 + 16; z++) {
				CityShape.Probe p = sh.probe(x, z);
				if (p.street && (p.onV && p.dv == 1 || p.onH && p.dh == 1) &&w.get(pos.set(x, c.base + 1, z)).isAir() && w.get(pos.set(x, c.base + 2, z)).isAir()) {
					var v = EntityTypes.VILLAGER.create(w.level.getLevel(), EntitySpawnReason.STRUCTURE);
					if (v != null) {
						v.snapTo(x + 0.5, c.base + 1, z + 0.5, (float) Math.floorMod(h >>> 8, 360), 0);
						v.setPersistenceRequired();
						w.level.addFreshEntity(v);
					}
					return;
				}
			}
		}
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	// ------------------------------------------------------------------------------------------------

	/** Writes blocks; fences, panes, walls and stairs get their shapes fixed afterwards (by the game, or right here). */
	private static final class Writer {
		final WorldGenLevel level;
		final boolean live;
		final List<BlockPos> shapes = new ArrayList<>();

		Writer(WorldGenLevel level) {
			this.level = level;
			this.live = level instanceof ServerLevel;
		}

		int top(int x, int z) {
			return level.getHeight(live ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		}

		BlockState get(BlockPos p) {
			return level.getBlockState(p);
		}

		void set(BlockPos p, BlockState s) {
			// No onPlace while building (as in world generation): a pumpkin by an unloaded chunk would look for a golem there.
			level.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ON_PLACE);
			Block b = s.getBlock();
			if (b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock || b instanceof FenceGateBlock) {
				BlockPos q = p.immutable();
				if (live) {
					shapes.add(q);
				} else {
					level.getChunk(q).markPosForPostProcessing(q);
				}
			}
		}

		void finish() {
			for (BlockPos p : shapes) {
				BlockState s = level.getBlockState(p);
				BlockState u = Block.updateFromNeighbourShapes(s, level, p);
				if (u != s && !u.isAir()) {
					level.setBlock(p, u, Block.UPDATE_CLIENTS);
				}
			}
		}
	}
}
