package com.stasdoto.airdefense.client.map;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;

import com.stasdoto.airdefense.AirDefense;

/**
 * The tablet's terrain picture: every chunk the player has seen, coloured like a paper map (relief shading, water
 * depth), kept in 512x512-block regions. Each region is a texture for the map screen and a PNG on disk
 * ({@code .minecraft/airdefense/maps/<world>/<dimension>/}), so the explored map stays between sessions.
 * Chunks are re-read every so often, so new craters and buildings show up.
 */
public final class MapCache {
	public static final int REGION = 512;
	/** Chunks read per tick (each is 256 columns). */
	private static final int CHUNKS_PER_TICK = 10;
	private static final int SCAN_RADIUS = 32;
	private static final int MAX_LOADED_REGIONS = 72;
	private static final int[][] SPIRAL = spiral(SCAN_RADIUS);

	private static final Map<Long, Region> REGIONS = new HashMap<>();
	private static final Long2LongOpenHashMap SCANNED = new Long2LongOpenHashMap();
	private static String worldKey;
	private static Path folder;
	private static int cursor;
	private static int saveTimer;
	private static long clock;

	private MapCache() {
	}

	/** One 512x512 piece of the map. Pixel = one block, ARGB, fully transparent = never seen. */
	public static final class Region {
		final int rx;
		final int rz;
		final NativeImage image;
		DynamicTexture texture;
		Identifier textureId;
		boolean dirty;
		boolean textureDirty = true;
		long lastUsed;

		Region(int rx, int rz, NativeImage image) {
			this.rx = rx;
			this.rz = rz;
			this.image = image;
		}

		/** The texture to draw (uploaded again if the picture changed since). */
		public Identifier texture() {
			lastUsed = clock;
			if (texture == null) {
				texture = new DynamicTexture(() -> "airdefense map " + rx + "," + rz, image);
				textureId = AirDefense.id("map/" + Integer.toHexString(System.identityHashCode(this)));
				Minecraft.getInstance().getTextureManager().register(textureId, texture);
				textureDirty = false;
			} else if (textureDirty) {
				texture.upload();
				textureDirty = false;
			}
			return textureId;
		}

		void close() {
			if (texture != null) {
				Minecraft.getInstance().getTextureManager().release(textureId);
				texture = null;
			} else {
				image.close();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Access for the screen

	public static int regionIndex(int block) {
		return Math.floorDiv(block, REGION);
	}

	/** The region if it has ever been seen (loaded from disk on first use), else null. */
	public static Region region(int rx, int rz) {
		long key = ChunkPos.pack(rx, rz);
		Region r = REGIONS.get(key);
		if (r == null && folder != null) {
			Path file = file(rx, rz);
			if (Files.isRegularFile(file)) {
				try (InputStream in = Files.newInputStream(file)) {
					NativeImage img = NativeImage.read(in);
					if (img.getWidth() == REGION && img.getHeight() == REGION) {
						r = new Region(rx, rz, img);
						REGIONS.put(key, r);
					} else {
						img.close();
					}
				} catch (IOException e) {
					AirDefense.LOGGER.warn("Could not read map region {}", file, e);
				}
			}
		}
		return r;
	}

	/** Map colour of a block column, 0 if unknown. */
	public static int colorAt(int x, int z) {
		Region r = region(regionIndex(x), regionIndex(z));
		return r == null ? 0 : r.image.getPixel(Math.floorMod(x, REGION), Math.floorMod(z, REGION));
	}

	private static Region regionForWrite(int rx, int rz) {
		Region r = region(rx, rz);
		if (r == null) {
			r = new Region(rx, rz, new NativeImage(REGION, REGION, true));
			r.image.fillRect(0, 0, REGION, REGION, 0);
			REGIONS.put(ChunkPos.pack(rx, rz), r);
		}
		return r;
	}

	// ------------------------------------------------------------------------------------------------
	// Scanning

	public static void tick(Minecraft mc) {
		clock++;
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			if (worldKey != null) {
				reset();
			}
			return;
		}
		String key = worldKey(mc, level);
		if (!key.equals(worldKey)) {
			reset();
			worldKey = key;
			folder = mc.gameDirectory.toPath().resolve("airdefense").resolve("maps").resolve(key);
		}
		ChunkPos center = mc.player.chunkPosition();
		int done = 0;
		for (int tries = 0; tries < 400 && done < CHUNKS_PER_TICK; tries++) {
			int[] off = SPIRAL[cursor];
			cursor = (cursor + 1) % SPIRAL.length;
			int cx = center.x() + off[0];
			int cz = center.z() + off[1];
			long ck = ChunkPos.pack(cx, cz);
			long last = SCANNED.getOrDefault(ck, Long.MIN_VALUE);
			// Close to the player the picture is refreshed every 5 s, further away every 30 s.
			long maxAge = off[2] <= 4 ? 100 : 600;
			if (last != Long.MIN_VALUE && clock - last < maxAge) {
				continue;
			}
			LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			if (chunk == null || chunk.isEmpty()) {
				continue;
			}
			scan(level, chunk);
			SCANNED.put(ck, clock);
			done++;
		}
		if (++saveTimer >= 600) {
			saveTimer = 0;
			save();
			unloadOld();
		}
	}

	/** Paints one chunk: the top block's map colour, lit from the north-west like a paper map, water darker when deep. */
	static void scan(ClientLevel level, LevelChunk chunk) {
		ChunkPos cp = chunk.getPos();
		int bx = cp.getMinBlockX();
		int bz = cp.getMinBlockZ();
		Region region = regionForWrite(regionIndex(bx), regionIndex(bz));
		int ox = Math.floorMod(bx, REGION);
		int oz = Math.floorMod(bz, REGION);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int[] northRow = new int[16];
		LevelChunk north = level.getChunkSource().getChunk(cp.x(), cp.z() - 1, ChunkStatus.FULL, false);
		for (int x = 0; x < 16; x++) {
			northRow[x] = north != null && !north.isEmpty() ? surfaceY(level, north, x, 15, m, minY) : Integer.MIN_VALUE;
		}
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int y = surfaceY(level, chunk, x, z, m, minY);
				m.set(bx + x, y, bz + z);
				BlockState state = chunk.getBlockState(m);
				MapColor color = state.getMapColor(level, m);
				int argb;
				if (color == MapColor.NONE) {
					argb = 0;
				} else if (!state.getFluidState().isEmpty() && color == MapColor.WATER) {
					int depth = 0;
					while (depth < 12 && y - depth - 1 > minY && !chunk.getBlockState(m.set(bx + x, y - depth - 1, bz + z)).getFluidState().isEmpty()) {
						depth++;
					}
					MapColor.Brightness b = depth > 7 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
					argb = color.calculateARGBColor(b);
				} else {
					int n = northRow[x];
					MapColor.Brightness b = n == Integer.MIN_VALUE || n == y ? MapColor.Brightness.NORMAL
							: y > n ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
					if (n != Integer.MIN_VALUE && y < n - 3) {
						b = MapColor.Brightness.LOWEST;
					}
					argb = color.calculateARGBColor(b);
				}
				northRow[x] = y;
				region.image.setPixel(ox + x, oz + z, argb);
			}
		}
		region.dirty = true;
		region.textureDirty = true;
	}

	/** Height of the top block that shows on a map (skips glass, air and other invisible blocks). */
	private static int surfaceY(ClientLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos m, int minY) {
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		int bx = chunk.getPos().getMinBlockX() + x;
		int bz = chunk.getPos().getMinBlockZ() + z;
		for (int i = 0; i < 24 && y > minY; i++, y--) {
			BlockState s = chunk.getBlockState(m.set(bx, y, bz));
			if (!s.isAir() && s.getMapColor(level, m) != MapColor.NONE) {
				return y;
			}
		}
		return Math.max(y, minY);
	}

	// ------------------------------------------------------------------------------------------------
	// World identity and saving

	private static String worldKey(Minecraft mc, ClientLevel level) {
		String world;
		IntegratedServer sp = mc.getSingleplayerServer();
		ServerData server = mc.getCurrentServer();
		if (sp != null) {
			Path root = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			world = "sp_" + (root.getFileName() != null ? root.getFileName().toString() : "world");
		} else if (server != null) {
			world = "mp_" + server.ip;
		} else {
			world = "unknown";
		}
		String dim = level.dimension().identifier().toString();
		return sanitize(world) + "/" + sanitize(dim);
	}

	private static String sanitize(String s) {
		return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static Path file(int rx, int rz) {
		return folder.resolve("r." + rx + "." + rz + ".png");
	}

	/** Writes the changed regions to disk (called every 30 s, when leaving the world and when the map closes). */
	public static void save() {
		if (folder == null) {
			return;
		}
		for (Region r : REGIONS.values()) {
			if (!r.dirty) {
				continue;
			}
			try {
				Files.createDirectories(folder);
				r.image.writeToFile(file(r.rx, r.rz));
				r.dirty = false;
			} catch (IOException e) {
				AirDefense.LOGGER.warn("Could not save map region {} {}", r.rx, r.rz, e);
			}
		}
	}

	/** Keeps memory in check: the least recently drawn regions beyond the limit are saved and dropped. */
	private static void unloadOld() {
		if (REGIONS.size() <= MAX_LOADED_REGIONS) {
			return;
		}
		List<Map.Entry<Long, Region>> all = new ArrayList<>(REGIONS.entrySet());
		all.sort(Comparator.comparingLong(e -> e.getValue().lastUsed));
		int drop = REGIONS.size() - MAX_LOADED_REGIONS;
		ChunkPos here = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.chunkPosition() : null;
		for (Map.Entry<Long, Region> e : all) {
			if (drop <= 0) {
				break;
			}
			Region r = e.getValue();
			if (here != null && r.rx == regionIndex(here.getMinBlockX()) && r.rz == regionIndex(here.getMinBlockZ())) {
				continue;
			}
			if (r.dirty) {
				continue;
			}
			r.close();
			REGIONS.remove(e.getKey());
			drop--;
		}
	}

	public static void reset() {
		save();
		for (Region r : REGIONS.values()) {
			r.close();
		}
		REGIONS.clear();
		SCANNED.clear();
		worldKey = null;
		folder = null;
		cursor = 0;
	}

	/** Chunk offsets around the player, nearest first: {dx, dz, ring}. */
	private static int[][] spiral(int radius) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				list.add(new int[]{dx, dz, Math.max(Math.abs(dx), Math.abs(dz))});
			}
		}
		list.sort(Comparator.comparingInt((int[] a) -> a[0] * a[0] + a[1] * a[1]));
		return list.toArray(new int[0][]);
	}
}
