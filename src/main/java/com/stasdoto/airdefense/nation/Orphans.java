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

	/** For the tests: blocks removed (by the world generator, by the sweep of older towns), and orphans counted. */
	public static int removedAtGeneration;
	public static int removedLater;
	/** For the tests: what the last counts found (logs, leaves and caps, vines/plants/snow) and a few of them. */
	public static final int[] FOUND = new int[3];
	public static final java.util.List<String> SAMPLES = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
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
		boolean any = false;
		for (int y = 0; y < h; y++) {
			for (int z = 0; z < w; z++) {
				for (int x = 0; x < w; x++) {
					BlockState s = a.get(p.set(ax + x, yMin + y, az + z));
					byte k;
					if (s.isAir()) {
						k = OTHER;
					} else if (s.is(BlockTags.LOGS) || s.is(Blocks.MUSHROOM_STEM)) {
						k = LOG;
						any = true;
					} else if (s.is(BlockTags.LEAVES)) {
						k = s.hasProperty(BlockStateProperties.PERSISTENT) && s.getValue(BlockStateProperties.PERSISTENT) ? SOLID : LEAF;
						any |= k == LEAF;
					} else if (s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
						k = CAP;
						any = true;
					} else if (s.canBeReplaced() || !s.getFluidState().isEmpty() || s.is(Blocks.SNOW) || s.is(Blocks.BEE_NEST) || s.is(Blocks.COCOA)) {
						k = OTHER;
					} else {
						k = SOLID;
					}
					kind[(y * w + z) * w + x] = k;
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
					seed = kind[i - w * w] == SOLID || kind[i + w * w] == SOLID || kind[i - 1] == SOLID || kind[i + 1] == SOLID
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
						kind[i] = OTHER;
						removed++;
						if (!dryRun) {
							a.set(p.set(ax + x, yMin + i / (w * w), az + z), Blocks.AIR.defaultBlockState());
						} else {
							note(0, a, p.set(ax + x, yMin + i / (w * w), az + z), yMin, h);
						}
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
					if (j < 0) {
						continue;
					}
					if (kind[j] == LEAF && d < 6 && dist[j] > d + 1) {
						dist[j] = (byte) (d + 1);
						queue[tail++] = j;
					} else if (kind[j] == CAP && dist[j] == Byte.MAX_VALUE && (kind[i] == CAP || kind[i] == LOG)) {
						dist[j] = 1;
						queue[tail++] = j;
					}
				}
			}
			for (int i = 0; i < kind.length; i++) {
				if ((kind[i] == LEAF || kind[i] == CAP) && dist[i] == Byte.MAX_VALUE) {
					int x = i % w;
					int z = (i / w) % w;
					if (x >= in0 && x <= in1 && z >= in0 && z <= in1) {
						kind[i] = OTHER;
						removed++;
						if (!dryRun) {
							a.set(p.set(ax + x, yMin + i / (w * w), az + z), Blocks.AIR.defaultBlockState());
						} else {
							note(1, a, p.set(ax + x, yMin + i / (w * w), az + z), yMin, h);
						}
					}
				}
			}
		}
		// Vines, cocoa and bee nests that hang on nothing (from the top down, so a hanging chain goes too); then plants and
		// snow on air (from the bottom up, so a tall plant's top half goes with its bottom half).
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 1; k <= h - 2; k++) {
				int y = pass == 0 ? h - 1 - k : k;
				for (int z = in0; z <= in1; z++) {
					for (int x = in0; x <= in1; x++) {
						int i = (y * w + z) * w + x;
						if (kind[i] != OTHER) {
							continue;
						}
						BlockState s = a.get(p.set(ax + x, yMin + y, az + z));
						if (s.isAir() || !s.getFluidState().isEmpty()) {
							continue;
						}
						boolean drop = false;
						if (pass == 0 && s.getBlock() instanceof VineBlock) {
							boolean hold = kind[i + w * w] == SOLID || kind[i + w * w] == LEAF || kind[i + w * w] == LOG
									|| a.get(p.set(ax + x, yMin + y + 1, az + z)).getBlock() instanceof VineBlock;
							for (int j : new int[]{i - 1, i + 1, i - w, i + w}) {
								hold |= kind[j] == SOLID || kind[j] == LEAF || kind[j] == LOG;
							}
							drop = !hold;
						} else if (pass == 0 && (s.is(Blocks.COCOA) || s.is(Blocks.BEE_NEST))) {
							boolean log = false;
							for (int j : new int[]{i - 1, i + 1, i - w, i + w, i + w * w, i - w * w}) {
								log |= kind[j] == LOG;
							}
							drop = !log;
						} else if (pass == 1 && !(s.getBlock() instanceof VineBlock)
								&& (s.is(Blocks.SNOW) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS))) {
							drop = a.get(p.set(ax + x, yMin + y - 1, az + z)).isAir();
						}
						if (drop) {
							removed++;
							if (!dryRun) {
								a.set(p.set(ax + x, yMin + y, az + z), Blocks.AIR.defaultBlockState());
							} else {
								note(2, a, p.set(ax + x, yMin + y, az + z), yMin, h);
							}
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
			if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && pending() > 0) {
				long t0 = System.nanoTime();
				// Up to a millisecond and a half a tick, at most four chunks.
				boolean worked = false;
				for (int n = 0; n < 4 && pending() > 0 && System.nanoTime() - t0 < 1_500_000; n++) {
					worked |= step(level);
				}
				idleTicks = worked ? 0 : idleTicks + 1;
			} else {
				idleTicks++;
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			synchronized (QUEUE) {
				DONE.clear();
				QUEUE.clear();
				QUEUED.clear();
			}
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
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		int yMin = Integer.MAX_VALUE;
		int yMax = Integer.MIN_VALUE;
		for (int x = x0; x < x0 + 16; x += 2) {
			for (int z = z0; z < z0 + 16; z += 2) {
				yMin = Math.min(yMin, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z));
			}
		}
		for (int x = x0 - REACH; x < x0 + 16 + REACH; x++) {
			for (int z = z0 - REACH; z < z0 + 16 + REACH; z++) {
				yMax = Math.max(yMax, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z));
			}
		}
		yMin = Math.max(level.getMinY() + 1, yMin - 3);
		yMax = Math.min(yMax + 1, yMin + 160);
		removedLater += sweep(new Access() {
			@Override
			public BlockState get(BlockPos p) {
				return level.getBlockState(p);
			}

			@Override
			public void set(BlockPos p, BlockState s) {
				level.setBlock(p, s, LIVE_FLAGS);
			}
		}, x0, z0, yMin, yMax, CityGen.ownTrees(level, cp), false);
		long dt = System.nanoTime() - t0;
		stepNanos += dt;
		stepMax = Math.max(stepMax, dt);
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
