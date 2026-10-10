package com.stasdoto.airdefense.nation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.level.storage.LevelResource;

import com.stasdoto.airdefense.AirDefense;

/**
 * The atlas (1.25): a map of the land 10 km across round the world's spawn, made when a world starts - the ground
 * (height and kind of land, from the world generator, nothing needs to be loaded), every planned city with its
 * streets, every hamlet, every road, and where the countries' borders run. All the towns in it join the political
 * map at once, so the tablet shows every country, city and village from the first minute. The picture is kept in the
 * world folder; the plan is worked out from the seed again on each start.
 */
public final class Atlas {
	public static final int HALF = 5120;
	/** Blocks per pixel of the ground picture. */
	public static final int RES = 32;
	public static final int SIZE = HALF * 2 / RES;
	private static final int VERSION = 3;

	/** What the clients get: the picture and the plan, packed. */
	private static volatile byte[] packed;
	private static volatile boolean busy;
	private static ExecutorService pool;
	/** For the tests and the log: rows of the picture done, towns founded. */
	public static final AtomicInteger ROWS = new AtomicInteger();
	public static int founded;
	public static volatile boolean ready;
	/** Towns waiting to join the political map (a few per tick, so the server does not stall). */
	private static final Deque<Object> TO_FOUND = new ArrayDeque<>();

	private Atlas() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(AtlasPayload.TYPE, AtlasPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AtlasPoliticsPayload.TYPE, AtlasPoliticsPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(Atlas::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.getPlayer())));
		ServerTickEvents.END_SERVER_TICK.register(Atlas::tick);
	}

	private static void stop() {
		LAST_POLITICS.clear();
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
		packed = null;
		ready = false;
		busy = false;
		synchronized (TO_FOUND) {
			TO_FOUND.clear();
		}
	}

	/** Where the atlas is centred: the world's spawn, on the 32-block grid. */
	public static int[] origin(ServerLevel level) {
		BlockPos spawn = level.getRespawnData().pos();
		int cx = Math.floorDiv(spawn.getX(), RES) * RES;
		int cz = Math.floorDiv(spawn.getZ(), RES) * RES;
		return new int[]{cx - HALF, cz - HALF};
	}

	private static void start(MinecraftServer server) {
		if (!CityFeature.enabled || busy) {
			return;
		}
		ServerLevel level = server.overworld();
		int[] o = origin(level);
		long seed = level.getSeed();
		Cities.Terrain t = Cities.terrain(level);
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
				long t1 = System.nanoTime();
				Plan plan = plan(seed, t, o, p, threads);
				long t2 = System.nanoTime();
				byte[] bytes = pack(o, g, plan, seed, t, t.sea());
				long t3 = System.nanoTime();
				AirDefense.LOGGER.info("[airdefense] atlas ready: {} cities, {} hamlets, {} roads, {} KB, {} ms (land {}, plan {}, pack {}; {} threads)",
						plan.cities.size(), plan.hamlets.size(), plan.roads.size(), bytes.length / 1024, (t3 - t0) / 1_000_000, (t1 - t0) / 1_000_000,
						(t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, threads);
				server.execute(() -> {
					packed = bytes;
					ready = true;
					synchronized (TO_FOUND) {
						TO_FOUND.addAll(plan.cities);
						TO_FOUND.addAll(plan.hamlets);
					}
					for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
						send(pl);
					}
				});
			} catch (RuntimeException e) {
				AirDefense.LOGGER.error("[airdefense] atlas failed", e);
			}
		});
	}

	/** 1.43: the atlas is made and all its towns have joined the political map (the countries are all there). */
	public static boolean settled() {
		if (!ready) {
			return false;
		}
		synchronized (TO_FOUND) {
			return TO_FOUND.isEmpty();
		}
	}

	public static void send(ServerPlayer player) {
		byte[] b = packed;
		if (b != null && ServerPlayNetworking.canSend(player, AtlasPayload.TYPE)) {
			ServerPlayNetworking.send(player, new AtlasPayload(b));
		}
	}

	/** Founds the waiting towns, a few each tick (the political map: countries, names, stores). */
	private static void tick(MinecraftServer server) {
		if (!ready) {
			return;
		}
		long a0 = System.nanoTime();
		tickFounding(server);
		com.stasdoto.airdefense.util.Perf.add(com.stasdoto.airdefense.util.Perf.ATLAS, System.nanoTime() - a0);
	}

	private static void tickFounding(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Politics p = Politics.get(server);
		for (int k = 0; k < 3; k++) {
			Object next;
			synchronized (TO_FOUND) {
				next = TO_FOUND.poll();
			}
			if (next == null) {
				return;
			}
			if (next instanceof Cities.City c) {
				if (!Nations.isFounded(p, c)) {
					Nations.foundCity(level, p, c);
					founded++;
				}
			} else if (next instanceof Hamlets.Hamlet h) {
				if (!Nations.isFounded(p, h)) {
					Nations.foundHamlet(level, p, h);
					founded++;
				}
			}
		}
	}

	private static final Map<java.util.UUID, Long> LAST_POLITICS = new HashMap<>();

	/** Who holds what (for the borders on the map), at most every few seconds per player. */
	public static void sendPolitics(ServerLevel level, ServerPlayer player, boolean now) {
		long t = level.getGameTime();
		Long last = LAST_POLITICS.get(player.getUUID());
		// (A stored time from an earlier world can be ahead of this one's clock: that counts as long ago.)
		if (!now && last != null && t >= last && t - last < 100 || !ServerPlayNetworking.canSend(player, AtlasPoliticsPayload.TYPE)) {
			return;
		}
		LAST_POLITICS.put(player.getUUID(), t);
		Politics p = Politics.get(level.getServer());
		Country own = p.countryOwnedBy(player.getUUID());
		List<AtlasPoliticsPayload.Country> cs = new ArrayList<>();
		for (Country c : p.countries.values()) {
			cs.add(new AtlasPoliticsPayload.Country(c.id, c.name, c.argb(), own != null && own.id == c.id, own != null && own.atWarWith(c.id), c.capital));
		}
		List<AtlasPoliticsPayload.Town> ts = new ArrayList<>();
		for (Settlement s : p.settlements.values()) {
			int kind = s.isCity() ? 0 : s.isHamlet() ? 1 : 2;
			long key = s.isCity() ? s.city : s.isHamlet() ? s.hamlet : 0;
			Country c = p.country(s.country);
			ts.add(new AtlasPoliticsPayload.Town(s.id, s.name, s.country, kind, key, s.center.getX(), s.center.getZ(), s.people(),
					c != null && c.capital == s.id));
		}
		ServerPlayNetworking.send(player, new AtlasPoliticsPayload(cs, ts));
	}

	// ------------------------------------------------------------------------------------------------
	// The ground

	/** Heights (y + 128, clamped to a byte) and land kinds (index into the palette) of every pixel. */
	record Ground(byte[] heights, byte[] kinds, List<String> palette, int[] colors) {
	}

	private static Ground ground(ServerLevel level, Cities.Terrain t, int[] o, ExecutorService p, int threads) {
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
	private static void groundRows(net.minecraft.world.level.chunk.ChunkGenerator gen, net.minecraft.world.level.levelgen.RandomState rs, Cities.Terrain t,
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

	// ------------------------------------------------------------------------------------------------
	// The plan

	record Plan(List<Cities.City> cities, List<Hamlets.Hamlet> hamlets, List<Cities.Road> roads) {
	}

	private static Plan plan(long seed, Cities.Terrain t, int[] o, ExecutorService pool, int threads) {
		List<Cities.City> cities = new ArrayList<>();
		List<Hamlets.Hamlet> hamlets = new ArrayList<>();
		List<Cities.Road> roads = new ArrayList<>();
		Set<Cities.Road> seen = new HashSet<>();
		// The cells under the atlas and a strip round it (the next capitals shape the borders at the edge).
		int cx0 = Math.floorDiv(o[0] - 700, Cities.CELL);
		int cz0 = Math.floorDiv(o[1] - 700, Cities.CELL);
		int cx1 = Math.floorDiv(o[0] + SIZE * RES + 700, Cities.CELL);
		int cz1 = Math.floorDiv(o[1] + SIZE * RES + 700, Cities.CELL);
		// Planned on all the atlas threads first (the cells' towns, then their roads, then the towns' hamlets and
		// depots); what follows only collects it from the caches.
		List<int[]> cells = new ArrayList<>();
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				cells.add(new int[]{cx, cz});
			}
		}
		shared(pool, threads, cells, c -> Cities.cities(seed, t, c[0], c[1]));
		shared(pool, threads, cells, c -> Cities.roads(seed, t, c[0], c[1]));
		List<Cities.City> all = new ArrayList<>();
		for (int[] c : cells) {
			all.addAll(Cities.cities(seed, t, c[0], c[1]));
		}
		shared(pool, threads, all, c -> {
			if (in(o, c.x, c.z, 0)) {
				c.hamlets(seed, t);
				c.depot(seed, t);
				c.port(seed, t);
			}
		});
		// Capitals first (each founds its country), then the other towns, then the hamlets.
		List<Cities.City> others = new ArrayList<>();
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				for (Cities.City c : Cities.cities(seed, t, cx, cz)) {
					(c.capital() ? cities : others).add(c);
				}
			}
		}
		cities.addAll(others);
		for (Cities.City c : cities) {
			if (in(o, c.x, c.z, 0)) {
				hamlets.addAll(c.hamlets(seed, t));
			}
		}
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				for (Cities.Road r : Cities.roads(seed, t, cx, cz)) {
					if (seen.add(r)) {
						roads.add(r);
					}
				}
			}
		}
		for (Hamlets.Hamlet h : hamlets) {
			if (h.road != null) {
				roads.add(h.road);
			}
		}
		for (Cities.City c : cities) {
			Ports.Port pt = in(o, c.x, c.z, 0) ? c.port(seed, t) : null;
			if (pt != null && pt.access != null) {
				roads.add(pt.access);
			}
		}
		// Only what lies in the atlas (with a margin for the borders) is founded.
		cities.removeIf(c -> !in(o, c.x, c.z, 600));
		return new Plan(cities, hamlets, roads);
	}

	/** Does {@code job} for every item, the items shared out between the atlas threads (this one among them). */
	private static <T> void shared(ExecutorService pool, int threads, List<T> items, java.util.function.Consumer<T> job) {
		java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
		Runnable worker = () -> {
			for (int i = next.getAndIncrement(); i < items.size(); i = next.getAndIncrement()) {
				if (Thread.currentThread().isInterrupted()) {
					throw new IllegalStateException("atlas stopped");
				}
				job.accept(items.get(i));
			}
		};
		List<java.util.concurrent.Future<?>> parts = new ArrayList<>();
		for (int k = 1; k < threads; k++) {
			parts.add(pool.submit(worker));
		}
		worker.run();
		for (var f : parts) {
			try {
				f.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("atlas stopped");
			} catch (java.util.concurrent.ExecutionException e) {
				throw new IllegalStateException("atlas plan failed", e.getCause());
			}
		}
	}

	private static boolean in(int[] o, int x, int z, int margin) {
		return x >= o[0] - margin && z >= o[1] - margin && x < o[0] + SIZE * RES + margin && z < o[1] + SIZE * RES + margin;
	}

	// ------------------------------------------------------------------------------------------------
	// Packing for the clients (see AtlasClient for the reading side)

	private static byte[] pack(int[] o, Ground g, Plan plan, long seed, Cities.Terrain terrain, int sea) {
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
			// Cities: their place, size and street plan (which blocks of the grid are built on).
			out.writeShort(plan.cities.size());
			for (Cities.City c : plan.cities) {
				CityShape sh = c.shape();
				out.writeLong(c.key());
				out.writeInt(c.x);
				out.writeInt(c.z);
				out.writeByte(c.size.ordinal());
				out.writeByte(c.index);
				out.writeByte(sh.n);
				for (int v : sh.gx) {
					out.writeInt(v);
				}
				for (int v : sh.gz) {
					out.writeInt(v);
				}
				for (int i = 0; i < sh.n; i++) {
					for (int j = 0; j < sh.n; j++) {
						out.writeBoolean(sh.cellOn(i, j));
					}
				}
			}
			// Depots: the yard and its warehouses.
			List<Depots.Depot> depots = new ArrayList<>();
			for (Cities.City c : plan.cities) {
				Depots.Depot d = c.depot(seed, terrain);
				if (d != null) {
					depots.add(d);
				}
			}
			// 1.28: the ports go along as yards too (the quay, its warehouses).
			List<Ports.Port> ports = new ArrayList<>();
			for (Cities.City c : plan.cities) {
				Ports.Port pt = c.port(seed, terrain);
				if (pt != null) {
					ports.add(pt);
				}
			}
			out.writeShort(depots.size() + ports.size());
			for (Ports.Port pt : ports) {
				int[] a = pt.world(pt.back, pt.mid - Ports.HALF);
				int[] e = pt.world(pt.face, pt.mid + Ports.HALF);
				out.writeLong(pt.city.key());
				out.writeInt(Math.min(a[0], e[0]));
				out.writeInt(Math.min(a[1], e[1]));
				out.writeInt(Math.max(a[0], e[0]));
				out.writeInt(Math.max(a[1], e[1]));
				out.writeByte(pt.buildings.size());
				for (Building b : pt.buildings) {
					net.minecraft.core.BlockPos ba = b.at(-b.type.halfWidth(), 0, 0);
					net.minecraft.core.BlockPos be = b.at(b.type.halfWidth(), 0, b.type.depth - 1);
					out.writeInt(Math.min(ba.getX(), be.getX()));
					out.writeInt(Math.min(ba.getZ(), be.getZ()));
					out.writeInt(Math.max(ba.getX(), be.getX()));
					out.writeInt(Math.max(ba.getZ(), be.getZ()));
				}
			}
			for (Depots.Depot d : depots) {
				out.writeLong(d.city.key());
				out.writeInt(d.x0);
				out.writeInt(d.z0);
				out.writeInt(d.x1);
				out.writeInt(d.z1);
				out.writeByte(d.buildings.size());
				for (Building b : d.buildings) {
					net.minecraft.core.BlockPos a = b.at(-b.type.halfWidth(), 0, 0);
					net.minecraft.core.BlockPos e = b.at(b.type.halfWidth(), 0, b.type.depth - 1);
					out.writeInt(Math.min(a.getX(), e.getX()));
					out.writeInt(Math.min(a.getZ(), e.getZ()));
					out.writeInt(Math.max(a.getX(), e.getX()));
					out.writeInt(Math.max(a.getZ(), e.getZ()));
				}
			}
			// Hamlets: the outline round their houses and fields.
			out.writeShort(plan.hamlets.size());
			for (Hamlets.Hamlet h : plan.hamlets) {
				out.writeLong(h.key());
				out.writeInt(h.x);
				out.writeInt(h.z);
				List<int[]> hull = hull(h);
				out.writeShort(hull.size());
				for (int[] p : hull) {
					out.writeShort(p[0] - h.x);
					out.writeShort(p[1] - h.z);
				}
			}
			// Roads: a point every 12 blocks.
			out.writeShort(plan.roads.size());
			for (Cities.Road r : plan.roads) {
				out.writeByte((r.highway ? 1 : 0) | (r.dirt ? 2 : 0));
				int n = Math.max(2, (int) Math.ceil(r.length / 12.0) + 1);
				out.writeShort(n);
				for (int k = 0; k < n; k++) {
					double[] p = r.pointAt(r.length * k / (n - 1));
					out.writeInt((int) Math.round(p[0]));
					out.writeInt((int) Math.round(p[1]));
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return bytes.toByteArray();
	}

	/** The world's border seed (not the world's seed itself). */
	public static long warp(long seed) {
		long h = seed * 0x9E3779B97F4A7C15L ^ 0x0B0DE5L;
		return (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
	}

	/** The outline of a hamlet: round its houses, fields and square, a little out from them. */
	static List<int[]> hull(Hamlets.Hamlet h) {
		List<int[]> pts = new ArrayList<>();
		int m = 6;
		for (Hamlets.Pad p : h.pads) {
			pts.add(new int[]{p.x0() - m, p.z0() - m});
			pts.add(new int[]{p.x1() + m, p.z0() - m});
			pts.add(new int[]{p.x0() - m, p.z1() + m});
			pts.add(new int[]{p.x1() + m, p.z1() + m});
		}
		for (Hamlets.Field f : h.fields) {
			Hamlets.Pad p = f.pad();
			pts.add(new int[]{p.x0() - 3, p.z0() - 3});
			pts.add(new int[]{p.x1() + 3, p.z0() - 3});
			pts.add(new int[]{p.x0() - 3, p.z1() + 3});
			pts.add(new int[]{p.x1() + 3, p.z1() + 3});
		}
		int s = Hamlets.Hamlet.SQUARE + m;
		pts.add(new int[]{h.x - s, h.z - s});
		pts.add(new int[]{h.x + s, h.z + s});
		pts.add(new int[]{h.x - s, h.z + s});
		pts.add(new int[]{h.x + s, h.z - s});
		// Monotone chain.
		pts.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
		List<int[]> out = new ArrayList<>();
		for (int pass = 0; pass < 2; pass++) {
			int start = out.size();
			for (int i = 0; i < pts.size(); i++) {
				int[] p = pass == 0 ? pts.get(i) : pts.get(pts.size() - 1 - i);
				while (out.size() >= start + 2) {
					int[] a = out.get(out.size() - 2);
					int[] b = out.get(out.size() - 1);
					long cross = (long) (b[0] - a[0]) * (p[1] - a[1]) - (long) (b[1] - a[1]) * (p[0] - a[0]);
					if (cross > 0) {
						break;
					}
					out.removeLast();
				}
				out.add(p);
			}
			out.removeLast();
		}
		return out;
	}
}
