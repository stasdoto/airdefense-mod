package com.stasdoto.airdefense.map;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import com.stasdoto.airdefense.AirDefense;

/**
 * Lite: the tablet's map of the land 10 km across round the world's spawn, made when a world starts - the height and
 * the kind of land, straight from the world generator (nothing needs to be loaded). No towns, roads or borders: the
 * packed format keeps their (empty) sections so the client reads it as before.
 */
public final class LandAtlas {
	public static final int HALF = 5120;
	/** Blocks per pixel of the ground picture. */
	public static final int RES = 32;
	public static final int SIZE = HALF * 2 / RES;
	private static final int VERSION = 3;

	private static volatile byte[] packed;
	private static volatile boolean busy;
	private static ExecutorService pool;
	/** For the tests and the log: rows of the picture done. */
	public static final AtomicInteger ROWS = new AtomicInteger();
	public static volatile boolean ready;

	private LandAtlas() {
	}

	/** Where the terrain is, before anything is built: the top block of each column, and the sea. */
	public interface Terrain {
		int top(int x, int z);

		int sea();
	}

	public static Terrain terrain(ServerLevel level) {
		var src = level.getChunkSource();
		var gen = src.getGenerator();
		var rs = src.randomState();
		int sea = gen.getSeaLevel();
		return new Terrain() {
			@Override
			public int top(int x, int z) {
				return gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - 1;
			}

			@Override
			public int sea() {
				return sea;
			}
		};
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(AtlasPayload.TYPE, AtlasPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(LandAtlas::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.getPlayer())));
	}

	private static void stop() {
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
		packed = null;
		ready = false;
		busy = false;
	}

	/** Where the atlas is centred: the world's spawn, on the 32-block grid. */
	public static int[] origin(ServerLevel level) {
		BlockPos spawn = level.getRespawnData().pos();
		int cx = Math.floorDiv(spawn.getX(), RES) * RES;
		int cz = Math.floorDiv(spawn.getZ(), RES) * RES;
		return new int[]{cx - HALF, cz - HALF};
	}

	private static void start(MinecraftServer server) {
		if (busy) {
			return;
		}
		ServerLevel level = server.overworld();
		int[] o = origin(level);
		long seed = level.getSeed();
		Terrain t = terrain(level);
		Path file = server.getWorldPath(LevelResource.DATA).resolve("airdefense_atlas.bin");
		busy = true;
		ROWS.set(0);
		int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
		pool = Executors.newFixedThreadPool(threads, r -> {
			Thread th = new Thread(r, "airdefense-atlas");
			th.setDaemon(true);
			th.setPriority(Thread.MIN_PRIORITY);
			return th;
		});
		ExecutorService p = pool;
		p.execute(() -> {
			try {
				long t0 = System.nanoTime();
				Ground g = loadGround(file, o, seed);
				if (g == null) {
					g = ground(level, t, o, p, threads);
					saveGround(file, o, seed, g);
				}
				byte[] bytes = pack(o, g, seed, t.sea());
				AirDefense.LOGGER.info("[airdefense] atlas ready (land only): {} KB, {} ms", bytes.length / 1024, (System.nanoTime() - t0) / 1_000_000);
				server.execute(() -> {
					packed = bytes;
					ready = true;
					for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
						send(pl);
					}
				});
			} catch (RuntimeException e) {
				AirDefense.LOGGER.error("[airdefense] atlas failed", e);
			}
		});
	}

	public static void send(ServerPlayer player) {
		byte[] b = packed;
		if (b != null && ServerPlayNetworking.canSend(player, AtlasPayload.TYPE)) {
			ServerPlayNetworking.send(player, new AtlasPayload(b));
		}
	}

	/** Packed for the clients (see AtlasClient): the ground, then empty towns, yards, hamlets and roads. */
	private static byte[] pack(int[] o, Ground g, long seed, int sea) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, new Deflater(6)))) {
			out.writeInt(VERSION);
			out.writeInt(o[0]);
			out.writeInt(o[1]);
			out.writeInt(RES);
			out.writeInt(SIZE);
			out.writeInt(sea);
			out.writeLong(warp(seed));
			out.write(g.heights);
			out.write(g.kinds);
			out.writeShort(g.palette.size());
			for (int c : g.colors) {
				out.writeInt(c);
			}
			out.writeShort(0);
			out.writeShort(0);
			out.writeShort(0);
			out.writeShort(0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return bytes.toByteArray();
	}

	/** Heights (y + 128, clamped to a byte) and land kinds (index into the palette) of every pixel. */
	record Ground(byte[] heights, byte[] kinds, List<String> palette, int[] colors) {
	}

	private static Ground ground(ServerLevel level, Terrain t, int[] o, ExecutorService p, int threads) {
		byte[] heights = new byte[SIZE * SIZE];
		byte[] kinds = new byte[SIZE * SIZE];
		Map<String, Integer> index = new HashMap<>();
		List<String> palette = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		// The rows shared out between the atlas threads (every n-th row each); the biome names are numbered under a lock.
		List<java.util.concurrent.Future<?>> parts = new ArrayList<>();
		for (int part = 1; part < threads; part++) {
			final int first = part;
			parts.add(p.submit(() -> groundRows(gen, rs, t, o, first, threads, heights, kinds, index, palette, colors)));
		}
		groundRows(gen, rs, t, o, 0, threads, heights, kinds, index, palette, colors);
		for (var f : parts) {
			try {
				f.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("atlas stopped");
			} catch (java.util.concurrent.ExecutionException e) {
				throw new IllegalStateException("atlas land failed", e.getCause());
			}
		}
		int[] c = new int[colors.size()];
		for (int i = 0; i < c.length; i++) {
			c[i] = colors.get(i);
		}
		return new Ground(heights, kinds, palette, c);
	}

	/** Every {@code step}-th row of the land from {@code first}: the height and the kind of land (biome) of each square. */
	private static void groundRows(net.minecraft.world.level.chunk.ChunkGenerator gen, net.minecraft.world.level.levelgen.RandomState rs, Terrain t,
			int[] o, int first, int step, byte[] heights, byte[] kinds, Map<String, Integer> index, List<String> palette, List<Integer> colors) {
		BiomeResolver biomes = gen.getBiomeSource().createUncachedResolver(rs);
		for (int j = first; j < SIZE; j += step) {
			if (Thread.currentThread().isInterrupted()) {
				throw new IllegalStateException("atlas stopped");
			}
			for (int i = 0; i < SIZE; i++) {
				int x = o[0] + i * RES + RES / 2;
				int z = o[1] + j * RES + RES / 2;
				int y = t.top(x, z);
				heights[j * SIZE + i] = (byte) Math.max(0, Math.min(255, y + 128));
				Holder<Biome> b = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(y, t.sea())), QuartPos.fromBlock(z));
				String id = b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
				int k;
				synchronized (index) {
					Integer known = index.get(id);
					if (known == null) {
						known = palette.size();
						index.put(id, known);
						palette.add(id);
						colors.add(colorOf(id, b.value()));
					}
					k = known;
				}
				kinds[j * SIZE + i] = (byte) k;
			}
			ROWS.incrementAndGet();
		}
	}

	/** The colour of a kind of land on the map (water is drawn by height). */
	private static int colorOf(String id, Biome b) {
		if (id.contains("desert") || id.contains("beach")) {
			return 0xD8CB94;
		}
		if (id.contains("badlands")) {
			return 0xC2703A;
		}
		if (id.contains("snowy") || id.contains("frozen") || id.contains("ice") || id.contains("peaks") && !id.contains("stony")) {
			return 0xE8EEF2;
		}
		if (id.contains("stony") || id.contains("windswept_gravelly") || id.contains("stone")) {
			return 0x8E9294;
		}
		if (id.contains("ocean") || id.contains("river")) {
			return 0x3F76E4;
		}
		int grass = b.getGrassColor(0, 0) & 0xFFFFFF;
		if (id.contains("forest") || id.contains("taiga") || id.contains("jungle") || id.contains("grove") || id.contains("swamp")) {
			int f = b.getFoliageColor() & 0xFFFFFF;
			return mix(f, 0x203018, 0.25);
		}
		return grass;
	}

	private static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	private static Ground loadGround(Path file, int[] o, long seed) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try (var in = new java.io.DataInputStream(new InflaterInputStream(Files.newInputStream(file)))) {
			if (in.readInt() != VERSION || in.readInt() != o[0] || in.readInt() != o[1] || in.readLong() != seed || in.readInt() != SIZE) {
				return null;
			}
			byte[] h = new byte[SIZE * SIZE];
			byte[] k = new byte[SIZE * SIZE];
			in.readFully(h);
			in.readFully(k);
			int n = in.readInt();
			List<String> pal = new ArrayList<>();
			int[] c = new int[n];
			for (int i = 0; i < n; i++) {
				pal.add(in.readUTF());
				c[i] = in.readInt();
			}
			ROWS.set(SIZE);
			return new Ground(h, k, pal, c);
		} catch (IOException e) {
			return null;
		}
	}

	private static void saveGround(Path file, int[] o, long seed, Ground g) {
		try {
			Files.createDirectories(file.getParent());
			try (var out = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(file)))) {
				out.writeInt(VERSION);
				out.writeInt(o[0]);
				out.writeInt(o[1]);
				out.writeLong(seed);
				out.writeInt(SIZE);
				out.write(g.heights);
				out.write(g.kinds);
				out.writeInt(g.palette.size());
				for (int i = 0; i < g.palette.size(); i++) {
					out.writeUTF(g.palette.get(i));
					out.writeInt(g.colors[i]);
				}
			}
		} catch (IOException e) {
			AirDefense.LOGGER.warn("Could not save the atlas", e);
		}
	}

	/** The world's border seed (not the world's seed itself). */
	public static long warp(long seed) {
		long h = seed * 0x9E3779B97F4A7C15L ^ 0x0B0DE5L;
		return (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
	}
}
