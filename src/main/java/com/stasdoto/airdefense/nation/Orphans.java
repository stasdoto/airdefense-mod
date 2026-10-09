package com.stasdoto.airdefense.nation;

import java.util.function.LongPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 1.35: what is left hanging in the air where a town, a road or a village levelled the ground. A tree near the edge
 * of a chunk reaches over into the next one: when the town cut the ground away under the trunk, the half of the tree
 * (its crown, the other half of a thick trunk, a branch) that had already grown into the next chunk stayed there,
 * floating - and the same for the grass and flowers on ground that is gone. Here: the logs that touch nothing solid
 * (through other logs), the leaves more than six blocks from any log, the vines, plants and snow that hang on nothing.
 * Run on each chunk the world generator levels (and on the chunks of towns made before this, as they load).
 */
public final class Orphans {
	/** Where the blocks are read and written (the world generator's region, or the live world). */
	public interface Access {
		BlockState get(BlockPos p);

		void set(BlockPos p, BlockState s);
	}

	/** Around the chunk: how far the logs and leaves are looked at, and how far out from it orphans are removed. */
	static final int REACH = 13;
	static final int CLEAR = 7;

	private static final byte OTHER = 0;
	private static final byte LOG = 1;
	private static final byte LEAF = 2;
	private static final byte SOLID = 3;
	private static final byte CAP = 4;
	private static final byte AIR = 5;
	/** Vines (hang on what is above or beside them). */
	private static final byte VINE = 6;
	/** Cocoa pods and bee nests (hang on a trunk). */
	private static final byte HANGER = 7;
	/** Grass, flowers, saplings, snow: stand on what is below them. */
	private static final byte PLANT = 8;
	/** Natural ground (soil, sand, rock): holds a trunk standing on it, not one that only touches it from the side. */
	private static final byte TERRAIN = 9;

	/** What a block is to the sweep. */
	private static byte classify(BlockState s) {
		if (s.isAir()) {
			return AIR;
		}
		if (s.is(BlockTags.LOGS) || s.is(Blocks.MUSHROOM_STEM)) {
			return LOG;
		}
		if (s.is(BlockTags.LEAVES)) {
			return s.hasProperty(BlockStateProperties.PERSISTENT) && s.getValue(BlockStateProperties.PERSISTENT) ? SOLID : LEAF;
		}
		if (s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
			return CAP;
		}
		if (s.getBlock() instanceof VineBlock) {
			return VINE;
		}
		if (s.is(Blocks.COCOA) || s.is(Blocks.BEE_NEST)) {
			return HANGER;
		}
		if (!s.getFluidState().isEmpty()) {
			return OTHER;
		}
		if (s.is(Blocks.SNOW) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)) {
			return PLANT;
		}
		if (s.canBeReplaced()) {
			return OTHER;
		}
		return Sites.naturalGround(s) ? TERRAIN : SOLID;
	}

	/** For the tests: blocks removed (by the world generator, by the sweep of older towns), and orphans counted. */
	public static int removedAtGeneration;
	public static int removedLater;
	/** For the tests: what the last counts found (logs, leaves and caps, vines/plants/snow) and a few of them. */
	public static final int[] FOUND = new int[3];
	public static final java.util.List<String> SAMPLES = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
	/** For the tests: what the sweeps took away (trunks, crowns, caps), by block. */
	public static final java.util.concurrent.ConcurrentHashMap<String, Integer> REMOVED = new java.util.concurrent.ConcurrentHashMap<>();
	public static final java.util.List<Long> SAMPLE_POS = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

	private Orphans() {
	}

	/**
	 * Removes the orphans in the chunk at ({@code x0}, {@code z0}) and {@link #CLEAR} blocks round it, between
	 * {@code yMin} and {@code yMax}; {@code ours} = logs that belong to buildings (never removed). With {@code dryRun}
	 * nothing is changed: it only counts. Returns how many blocks it removed (or would).
	 */
	public static int sweep(Access a, int x0, int z0, int yMin, int yMax, LongPredicate ours, boolean dryRun) {
		if (yMax <= yMin) {
			return 0;
		}
		int ax = x0 - REACH;
		int az = z0 - REACH;
		int w = 16 + 2 * REACH;
		int h = yMax - yMin + 1;
		byte[] kind = new byte[w * w * h];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		// Each kind of block is sorted out once (a region holds a few dozen kinds, read some hundred thousand times).
		java.util.IdentityHashMap<BlockState, Byte> memo = new java.util.IdentityHashMap<>();
		BlockState last = null;
		byte lastKind = OTHER;
		boolean any = false;
		boolean loose = false;
		for (int y = 0; y < h; y++) {
			for (int z = 0; z < w; z++) {
				for (int x = 0; x < w; x++) {
					BlockState s = a.get(p.set(ax + x, yMin + y, az + z));
					byte k;
					if (s == last) {
						k = lastKind;
					} else {
						Byte m = memo.get(s);
						if (m == null) {
							m = classify(s);
							memo.put(s, m);
						}
						k = m;
						last = s;
						lastKind = k;
					}
					kind[(y * w + z) * w + x] = k;
					any |= k == LOG || k == LEAF || k == CAP;
					loose |= k == VINE || k == HANGER || k == PLANT;
				}
			}
		}
		int removed = 0;
		int in0 = REACH - CLEAR;
		int in1 = REACH + 16 + CLEAR - 1;
		if (any) {
			// Logs that hold on to something solid (or belong to a building, or reach out of the region or below it) and the
			// logs joined to them; the rest float.
			boolean[] held = new boolean[kind.length];
			int[] queue = new int[kind.length];
			int head = 0;
			int tail = 0;
			for (int i = 0; i < kind.length; i++) {
				if (kind[i] != LOG) {
					continue;
				}
				int x = i % w;
				int z = (i / w) % w;
				int y = i / (w * w);
				boolean seed = y == 0 || x == 0 || z == 0 || x == w - 1 || z == w - 1 || y == h - 1
						|| ours.test(BlockPos.asLong(ax + x, yMin + y, az + z));
				if (!seed) {
					// Standing on ground or on anything built; touching the ground only from the side (a trunk by the wall of a
					// cutting) is no hold.
					byte below = kind[i - w * w];
					seed = below == SOLID || below == TERRAIN || kind[i + w * w] == SOLID || kind[i - 1] == SOLID || kind[i + 1] == SOLID
							|| kind[i - w] == SOLID || kind[i + w] == SOLID;
				}
				if (seed) {
					held[i] = true;
					queue[tail++] = i;
				}
			}
			while (head < tail) {
				int i = queue[head++];
				int x = i % w;
				int z = (i / w) % w;
				int y = i / (w * w);
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dx = -1; dx <= 1; dx++) {
							int nx = x + dx;
							int ny = y + dy;
							int nz = z + dz;
							if (nx < 0 || nz < 0 || ny < 0 || nx >= w || nz >= w || ny >= h) {
								continue;
							}
							int j = (ny * w + nz) * w + nx;
							if (kind[j] == LOG && !held[j]) {
								held[j] = true;
								queue[tail++] = j;
							}
						}
					}
				}
			}
			for (int i = 0; i < kind.length; i++) {
				if (kind[i] == LOG && !held[i]) {
					int x = i % w;
					int z = (i / w) % w;
					if (x >= in0 && x <= in1 && z >= in0 && z <= in1) {
						if (dryRun) {
							note(0, a, p.set(ax + x, yMin + i / (w * w), az + z), yMin, h);
						} else {
							p.set(ax + x, yMin + i / (w * w), az + z);
							REMOVED.merge(a.get(p).getBlock().getDescriptionId().replace("block.minecraft.", ""), 1, Integer::sum);
							a.set(p, Blocks.AIR.defaultBlockState());
						}
						kind[i] = AIR;
						removed++;
						loose = true;
					} else {
						// Out of reach to remove: counts as held, so its leaves stay.
						held[i] = true;
					}
				}
			}
			// Leaves within six steps of a log stay (the game's own rule), mushroom caps joined to a stem stay.
			byte[] dist = new byte[kind.length];
			java.util.Arrays.fill(dist, Byte.MAX_VALUE);
			head = 0;
			tail = 0;
			for (int i = 0; i < kind.length; i++) {
				if (kind[i] == LOG) {
					dist[i] = 0;
					queue[tail++] = i;
				} else if (kind[i] == LEAF || kind[i] == CAP) {
					// On the bottom or the sides of the region: the crown of a tree standing lower down or further off (its trunk
					// out of sight here) - it stays.
					int x = i % w;
					int z = (i / w) % w;
					if (i < w * w || x == 0 || z == 0 || x == w - 1 || z == w - 1) {
						dist[i] = 0;
						queue[tail++] = i;
					}
				}
			}
			while (head < tail) {
				int i = queue[head++];
				int x = i % w;
				int z = (i / w) % w;
				int y = i / (w * w);
				int d = dist[i];
				int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, z > 0 ? i - w : -1, z < w - 1 ? i + w : -1, y > 0 ? i - w * w : -1,
						y < h - 1 ? i + w * w : -1};
				for (int j : nb) {
					if (j >= 0 && kind[j] == LEAF && d < 6 && dist[j] > d + 1) {
						dist[j] = (byte) (d + 1);
						queue[tail++] = j;
					}
				}
				if (kind[i] == CAP || kind[i] == LOG) {
					// A cap holds on to its stem and the caps next to it, corners and edges too (a red mushroom's sides meet its
					// top only edge to edge).
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							for (int dx = -1; dx <= 1; dx++) {
								int nx = x + dx;
								int ny = y + dy;
								int nz = z + dz;
								if (nx < 0 || nz < 0 || ny < 0 || nx >= w || nz >= w || ny >= h) {
									continue;
								}
								int j = (ny * w + nz) * w + nx;
								if (kind[j] == CAP && dist[j] == Byte.MAX_VALUE) {
									dist[j] = 1;
									queue[tail++] = j;
								}
							}
						}
					}
				}
			}
			for (int i = 0; i < kind.length; i++) {
				if ((kind[i] == LEAF || kind[i] == CAP) && dist[i] == Byte.MAX_VALUE) {
					int x = i % w;
					int z = (i / w) % w;
					if (x >= in0 && x <= in1 && z >= in0 && z <= in1) {
						if (dryRun) {
							note(1, a, p.set(ax + x, yMin + i / (w * w), az + z), yMin, h);
						} else {
							p.set(ax + x, yMin + i / (w * w), az + z);
							REMOVED.merge(a.get(p).getBlock().getDescriptionId().replace("block.minecraft.", ""), 1, Integer::sum);
							a.set(p, Blocks.AIR.defaultBlockState());
						}
						kind[i] = AIR;
						removed++;
					}
				}
			}
		}
		if (!loose) {
			return removed;
		}
		// Vines, cocoa and bee nests that hang on nothing (from the top down, so a hanging chain goes too); then plants and
		// snow on air (from the bottom up, so a tall plant's top half goes with its bottom half).
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 1; k <= h - 2; k++) {
				int y = pass == 0 ? h - 1 - k : k;
				for (int z = in0; z <= in1; z++) {
					for (int x = in0; x <= in1; x++) {
						int i = (y * w + z) * w + x;
						byte kd = kind[i];
						boolean drop = false;
						if (pass == 0 && kd == VINE) {
							byte up = kind[i + w * w];
							boolean hold = up == SOLID || up == TERRAIN || up == LEAF || up == LOG || up == VINE;
							for (int j : new int[]{i - 1, i + 1, i - w, i + w}) {
								hold |= kind[j] == SOLID || kind[j] == TERRAIN || kind[j] == LEAF || kind[j] == LOG;
							}
							drop = !hold;
						} else if (pass == 0 && kd == HANGER) {
							boolean log = false;
							for (int j : new int[]{i - 1, i + 1, i - w, i + w, i + w * w, i - w * w}) {
								log |= kind[j] == LOG;
							}
							drop = !log;
						} else if (pass == 1 && kd == PLANT) {
							drop = kind[i - w * w] == AIR;
						}
						if (drop) {
							if (dryRun) {
								note(2, a, p.set(ax + x, yMin + y, az + z), yMin, h);
							} else {
								a.set(p.set(ax + x, yMin + y, az + z), Blocks.AIR.defaultBlockState());
							}
							kind[i] = AIR;
							removed++;
						}
					}
				}
			}
		}
		return removed;
	}

	private static void note(int what, Access a, BlockPos p, int yMin, int h) {
		FOUND[what]++;
		if (SAMPLES.size() < 24 && (FOUND[what] % 97 == 1)) {
			SAMPLE_POS.add(p.asLong());
			SAMPLES.add(a.get(p).getBlock().getDescriptionId().replace("block.minecraft.", "") + " at " + p.toShortString() + " (" + (p.getY() - yMin) + " of " + h + ")");
		}
	}

	/** Flags for setting blocks in the live world: tell the players, no neighbour reactions (leaves would start to decay). */
	public static final int LIVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	// ------------------------------------------------------------------------------------------------
	// The towns made before 1.35: their chunks are looked over as they load (once a session each, one chunk a tick).

	private static final java.util.Set<Long> DONE = new java.util.HashSet<>();
	private static final java.util.ArrayDeque<Long> QUEUE = new java.util.ArrayDeque<>();
	private static final java.util.Set<Long> QUEUED = new java.util.HashSet<>();
	/** The generator clears what it leaves floating (off only to compare, in a test). */
	public static volatile boolean atGeneration = true;
	/** Off for the tests that count what the generator left. */
	public static volatile boolean liveSweep = true;
	public static int chunksLooked;
	/** For the tests: time spent looking chunks over (all, the longest). */
	public static long stepNanos;
	public static long stepMax;
	public static long stepCpu;
	private static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();

	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level, chunk, fresh) -> {
			if (!fresh && liveSweep && CityFeature.enabled && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
				long key = chunk.getPos().pack();
				synchronized (QUEUE) {
					if (!DONE.contains(key) && QUEUED.add(key)) {
						QUEUE.add(key);
					}
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
				return;
			}
			boolean worked = apply(level);
			if (pending() > 0) {
				long t0 = System.nanoTime();
				// Up to a millisecond a tick (the sweeping itself is done on its own thread), at most four chunks.
				for (int n = 0; n < 4 && pending() > 0 && IN_FLIGHT.get() < 6 && System.nanoTime() - t0 < 1_000_000; n++) {
					worked |= step(level);
				}
			}
			worked |= IN_FLIGHT.get() > 0;
			idleTicks = worked ? 0 : idleTicks + 1;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			synchronized (QUEUE) {
				DONE.clear();
				QUEUE.clear();
				QUEUED.clear();
			}
			// What the sweeping thread finds for this world is not to be applied to the next one.
			SESSION.incrementAndGet();
			RESULTS.clear();
		});
	}

	/** Chunks the generator cut back after they were made ({@link CityGen#tidy}): for the tests. */
	public static int rechecks;

	/** Looks this chunk over again soon (the generator has just cut back trees in it). */
	public static void recheck(net.minecraft.world.level.ChunkPos cp) {
		synchronized (QUEUE) {
			long key = cp.pack();
			DONE.remove(key);
			rechecks++;
			if (QUEUED.add(key)) {
				QUEUE.add(key);
			}
		}
	}

	/** For the tests: ticks since a chunk was last looked over (the ones still waiting wait for their neighbours). */
	public static volatile int idleTicks;

	/** For the tests: chunks waiting to be looked over. */
	public static int pending() {
		synchronized (QUEUE) {
			return QUEUE.size();
		}
	}

	/** What the sweeping thread found to take away (and what each block was when it looked). */
	private record Found(int session, long[] pos, BlockState[] was) {
	}

	private static final java.util.concurrent.ConcurrentLinkedQueue<Found> RESULTS = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT = new java.util.concurrent.atomic.AtomicInteger();
	private static final java.util.concurrent.atomic.AtomicInteger SESSION = new java.util.concurrent.atomic.AtomicInteger();
	private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "AirDefense orphan sweep");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	/** For the tests: blocks the sweeping thread found that had changed by the time they were to go. */
	public static int changedMeanwhile;

	/** For the tests: has this chunk been looked over once complete (this session)? */
	public static boolean looked(net.minecraft.world.level.ChunkPos cp) {
		synchronized (QUEUE) {
			return DONE.contains(cp.pack());
		}
	}

	/** For the tests: time the sweeping thread spent. */
	public static volatile long workerNanos;

	/** Takes away what the sweeping thread found, where the blocks are still as it saw them. True if there was any. */
	private static boolean apply(net.minecraft.server.level.ServerLevel level) {
		boolean any = false;
		Found f;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		while ((f = RESULTS.poll()) != null) {
			any = true;
			if (f.session != SESSION.get()) {
				continue;
			}
			for (int i = 0; i < f.pos.length; i++) {
				p.set(f.pos[i]);
				if (level.isLoaded(p) && level.getBlockState(p) == f.was[i]) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), LIVE_FLAGS);
					removedLater++;
				} else {
					changedMeanwhile++;
				}
			}
		}
		return any;
	}

	/** Trunks, crowns, mushrooms, vines: what can be left hanging (a section without any needs no looking at). */
	private static boolean treeish(BlockState s) {
		return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.RED_MUSHROOM_BLOCK)
				|| s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.getBlock() instanceof VineBlock || s.is(Blocks.COCOA) || s.is(Blocks.BEE_NEST);
	}

	/** Looks over the next chunk in the queue if its neighbours are there; true if it did. */
	private static boolean step(net.minecraft.server.level.ServerLevel level) {
		long key;
		synchronized (QUEUE) {
			key = QUEUE.poll();
			QUEUED.remove(key);
		}
		net.minecraft.world.level.ChunkPos cp = net.minecraft.world.level.ChunkPos.unpack(key);
		if (!level.hasChunk(cp.x(), cp.z())) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!level.hasChunk(cp.x() + dx, cp.z() + dz)) {
					// Its neighbours are not all there yet: later (it comes round again when they load... or now, at the back).
					synchronized (QUEUE) {
						if (QUEUED.add(key)) {
							QUEUE.add(key);
						}
					}
					return false;
				}
			}
		}
		synchronized (QUEUE) {
			DONE.add(key);
		}
		if (!CityGen.levelled(level, cp)) {
			return false;
		}
		chunksLooked++;
		long t0 = System.nanoTime();
		long c0 = CPU.getCurrentThreadCpuTime();
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		// The chunk and its neighbours, read straight from their sections; only the heights where any of them may hold
		// trees, mushrooms or vines are looked at (not the empty sky, not the top floors of the towers).
		net.minecraft.world.level.chunk.LevelChunk[] cs = new net.minecraft.world.level.chunk.LevelChunk[9];
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				net.minecraft.world.level.chunk.LevelChunk c = level.getChunk(cp.x() + dx, cp.z() + dz);
				cs[(dx + 1) * 3 + dz + 1] = c;
				net.minecraft.world.level.chunk.LevelChunkSection[] secs = c.getSections();
				for (int si = 0; si < secs.length; si++) {
					if (!secs[si].hasOnlyAir() && secs[si].maybeHas(Orphans::treeish)) {
						int sy = c.getSectionYFromSectionIndex(si) << 4;
						lo = Math.min(lo, sy);
						hi = Math.max(hi, sy + 15);
					}
				}
			}
		}
		if (lo > hi) {
			return true;
		}
		int yMin = Integer.MAX_VALUE;
		for (int x = x0; x < x0 + 16; x += 2) {
			for (int z = z0; z < z0 + 16; z += 2) {
				yMin = Math.min(yMin, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z));
			}
		}
		yMin = Math.max(Math.max(level.getMinY() + 1, yMin - 3), lo - 1);
		int yMax = Math.min(hi + 1, yMin + 160);
		// A copy of the sections in that height (cheap), swept on the sweeping thread; what it finds is taken away here, in
		// a later tick, where the block is still the same.
		int s0 = level.getSectionIndex(yMin);
		int s1 = level.getSectionIndex(yMax);
		@SuppressWarnings("unchecked")
		net.minecraft.world.level.chunk.PalettedContainer<BlockState>[] snap = new net.minecraft.world.level.chunk.PalettedContainer[9 * (s1 - s0 + 1)];
		for (int k = 0; k < 9; k++) {
			net.minecraft.world.level.chunk.LevelChunkSection[] secs = cs[k].getSections();
			for (int si = s0; si <= s1; si++) {
				if (si >= 0 && si < secs.length && !secs[si].hasOnlyAir()) {
					snap[k * (s1 - s0 + 1) + si - s0] = secs[si].getStates().copy();
				}
			}
		}
		int cx = cp.x();
		int cz = cp.z();
		int minSec = level.getMinSectionY();
		int span = s1 - s0 + 1;
		int fMin = yMin;
		int fMax = yMax;
		LongPredicate ours = CityGen.ownTrees(level, cp);
		int session = SESSION.get();
		IN_FLIGHT.incrementAndGet();
		WORKER.execute(() -> {
			try {
				long w0 = System.nanoTime();
				BlockState air = Blocks.AIR.defaultBlockState();
				long[] found = new long[64];
				BlockState[] was = new BlockState[64];
				int[] n = {0};
				long[][] foundRef = {found};
				BlockState[][] wasRef = {was};
				Access snapshot = new Access() {
					@Override
					public BlockState get(BlockPos q) {
						int k = ((q.getX() >> 4) - cx + 1) * 3 + (q.getZ() >> 4) - cz + 1;
						int si = (q.getY() >> 4) - minSec - s0;
						if (k < 0 || k >= 9 || si < 0 || si >= span) {
							return air;
						}
						net.minecraft.world.level.chunk.PalettedContainer<BlockState> c = snap[k * span + si];
						return c == null ? air : c.get(q.getX() & 15, q.getY() & 15, q.getZ() & 15);
					}

					@Override
					public void set(BlockPos q, BlockState st) {
						if (n[0] == foundRef[0].length) {
							foundRef[0] = java.util.Arrays.copyOf(foundRef[0], n[0] * 2);
							wasRef[0] = java.util.Arrays.copyOf(wasRef[0], n[0] * 2);
						}
						foundRef[0][n[0]] = q.asLong();
						wasRef[0][n[0]] = get(q);
						n[0]++;
					}
				};
				sweep(snapshot, cx << 4, cz << 4, fMin, fMax, ours, false);
				workerNanos += System.nanoTime() - w0;
				if (n[0] > 0) {
					RESULTS.add(new Found(session, java.util.Arrays.copyOf(foundRef[0], n[0]), java.util.Arrays.copyOf(wasRef[0], n[0])));
				}
			} catch (RuntimeException e) {
				com.stasdoto.airdefense.AirDefense.LOGGER.warn("[airdefense] orphan sweep failed at {}", cp, e);
			} finally {
				IN_FLIGHT.decrementAndGet();
			}
		});
		long dt = System.nanoTime() - t0;
		stepNanos += dt;
		stepMax = Math.max(stepMax, dt);
		stepCpu += CPU.getCurrentThreadCpuTime() - c0;
		return true;
	}

	/** For the tests: orphans left round this chunk now (counted, not removed). */
	public static int count(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.ChunkPos cp, int yMin, int yMax) {
		return sweep(new Access() {
			@Override
			public BlockState get(BlockPos p) {
				return level.getBlockState(p);
			}

			@Override
			public void set(BlockPos p, BlockState s) {
			}
		}, cp.getMinBlockX(), cp.getMinBlockZ(), yMin, yMax, CityGen.ownTrees(level, cp), true);
	}
}
