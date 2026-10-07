package com.stasdoto.airdefense.client.map;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.nation.AtlasPayload;
import com.stasdoto.airdefense.nation.AtlasPoliticsPayload;
import com.stasdoto.airdefense.nation.Territory;

/**
 * The atlas on the client (1.25): the ground picture of the 10 km round the spawn, the towns' plans, the roads, and who
 * holds what - the tablet map draws the land, the borders of the countries and of each town's region, the outlines of
 * the cities and hamlets and the roads from it, even where nobody has been yet.
 */
public final class AtlasClient {
	public record City(long key, int x, int z, int size, int index, int n, int[] gx, int[] gz, boolean[] on) {
		public boolean cellOn(int i, int j) {
			return i >= 0 && j >= 0 && i < n && j < n && on[i * n + j];
		}
	}

	public record Hamlet(long key, int x, int z, int[] xs, int[] zs) {
	}

	public record Road(boolean highway, boolean dirt, int[] xs, int[] zs, int minX, int maxX, int minZ, int maxZ) {
	}

	public static boolean loaded;
	public static int x0;
	public static int z0;
	public static int res;
	public static int size;
	public static int sea;
	public static long warp;
	public static final List<City> CITIES = new ArrayList<>();
	public static final List<Hamlet> HAMLETS = new ArrayList<>();
	public static final List<Road> ROADS = new ArrayList<>();
	private static byte[] heights;
	/** The cities as seats of their regions (for the borders). */
	private static int[] seatX = new int[0];
	private static int[] seatZ = new int[0];
	private static float[] seatW = new float[0];
	private static long[] seatKey = new long[0];
	private static DynamicTexture texture;
	private static Identifier textureId;
	/** Pixels of the ground texture per atlas pixel. */
	public static final int UP = 4;

	public static AtlasPoliticsPayload politics;
	private static final Map<Long, AtlasPoliticsPayload.Town> BY_KEY = new HashMap<>();
	private static final Map<Integer, AtlasPoliticsPayload.Country> COUNTRIES = new HashMap<>();
	private static final Map<Integer, AtlasPoliticsPayload.Town> TOWNS = new HashMap<>();
	/** Bumped whenever who-holds-what changes (the map redraws its borders). */
	public static int version;

	private AtlasClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(AtlasPayload.TYPE, (payload, context) -> context.client().execute(() -> load(payload.data())));
		ClientPlayNetworking.registerGlobalReceiver(AtlasPoliticsPayload.TYPE, (payload, context) -> context.client().execute(() -> politics(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null && loaded) {
				clear();
			}
		});
	}

	private static void clear() {
		loaded = false;
		CITIES.clear();
		HAMLETS.clear();
		ROADS.clear();
		politics = null;
		BY_KEY.clear();
		COUNTRIES.clear();
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(textureId);
			texture = null;
		}
	}

	private static void politics(AtlasPoliticsPayload p) {
		politics = p;
		BY_KEY.clear();
		COUNTRIES.clear();
		TOWNS.clear();
		for (AtlasPoliticsPayload.Town t : p.towns()) {
			TOWNS.put(t.id(), t);
			if (t.kind() < 2) {
				BY_KEY.put(t.key() * 2 + t.kind(), t);
			}
		}
		for (AtlasPoliticsPayload.Country c : p.countries()) {
			COUNTRIES.put(c.id(), c);
		}
		version++;
	}

	public static AtlasPoliticsPayload.Town cityTown(long key) {
		return BY_KEY.get(key * 2);
	}

	public static AtlasPoliticsPayload.Town hamletTown(long key) {
		return BY_KEY.get(key * 2 + 1);
	}

	/** Whether the atlas draws this town's outline (a planned city or hamlet in it). */
	public static boolean outlines(int settlement) {
		AtlasPoliticsPayload.Town t = TOWNS.get(settlement);
		return loaded && t != null && t.kind() < 2;
	}

	public static AtlasPoliticsPayload.Country country(int id) {
		return COUNTRIES.get(id);
	}

	// ------------------------------------------------------------------------------------------------
	// Reading

	private static void load(byte[] data) {
		clear();
		try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
			in.readInt();
			x0 = in.readInt();
			z0 = in.readInt();
			res = in.readInt();
			size = in.readInt();
			sea = in.readInt();
			warp = in.readLong();
			heights = new byte[size * size];
			byte[] kinds = new byte[size * size];
			in.readFully(heights);
			in.readFully(kinds);
			int pal = in.readShort();
			int[] colors = new int[pal];
			for (int i = 0; i < pal; i++) {
				colors[i] = in.readInt();
			}
			int nc = in.readShort();
			for (int k = 0; k < nc; k++) {
				long key = in.readLong();
				int x = in.readInt();
				int z = in.readInt();
				int sz = in.readByte();
				int index = in.readByte();
				int n = in.readByte();
				int[] gx = new int[n + 1];
				int[] gz = new int[n + 1];
				for (int i = 0; i <= n; i++) {
					gx[i] = in.readInt();
				}
				for (int i = 0; i <= n; i++) {
					gz[i] = in.readInt();
				}
				boolean[] on = new boolean[n * n];
				for (int i = 0; i < n * n; i++) {
					on[i] = in.readBoolean();
				}
				CITIES.add(new City(key, x, z, sz, index, n, gx, gz, on));
			}
			int nh = in.readShort();
			for (int k = 0; k < nh; k++) {
				long key = in.readLong();
				int x = in.readInt();
				int z = in.readInt();
				int m = in.readShort();
				int[] xs = new int[m];
				int[] zs = new int[m];
				for (int i = 0; i < m; i++) {
					xs[i] = x + in.readShort();
					zs[i] = z + in.readShort();
				}
				HAMLETS.add(new Hamlet(key, x, z, xs, zs));
			}
			int nr = in.readShort();
			for (int k = 0; k < nr; k++) {
				int flags = in.readByte();
				int m = in.readShort();
				int[] xs = new int[m];
				int[] zs = new int[m];
				int ax = Integer.MAX_VALUE;
				int bx = Integer.MIN_VALUE;
				int az = Integer.MAX_VALUE;
				int bz = Integer.MIN_VALUE;
				for (int i = 0; i < m; i++) {
					xs[i] = in.readInt();
					zs[i] = in.readInt();
					ax = Math.min(ax, xs[i]);
					bx = Math.max(bx, xs[i]);
					az = Math.min(az, zs[i]);
					bz = Math.max(bz, zs[i]);
				}
				ROADS.add(new Road((flags & 1) != 0, (flags & 2) != 0, xs, zs, ax, bx, az, bz));
			}
			int n = CITIES.size();
			seatX = new int[n];
			seatZ = new int[n];
			seatW = new float[n];
			seatKey = new long[n];
			for (int i = 0; i < n; i++) {
				City c = CITIES.get(i);
				seatX[i] = c.x;
				seatZ[i] = c.z;
				seatW[i] = Territory.weight(c.size, c.index == 0);
				seatKey[i] = c.key;
			}
			bake(kinds, colors);
			loaded = true;
			version++;
			AirDefense.LOGGER.info("[airdefense] atlas received: {} cities, {} hamlets, {} roads", CITIES.size(), HAMLETS.size(), ROADS.size());
		} catch (IOException | RuntimeException e) {
			AirDefense.LOGGER.warn("Could not read the atlas", e);
			clear();
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The ground picture: the land's colours, shaded by its relief (light from the north-west), water by its depth

	private static int h(int i, int j) {
		i = Math.max(0, Math.min(size - 1, i));
		j = Math.max(0, Math.min(size - 1, j));
		return (heights[j * size + i] & 0xFF) - 128;
	}

	private static double hAt(double fi, double fj) {
		int i = (int) Math.floor(fi);
		int j = (int) Math.floor(fj);
		double tx = fi - i;
		double tz = fj - j;
		return (h(i, j) * (1 - tx) + h(i + 1, j) * tx) * (1 - tz) + (h(i, j + 1) * (1 - tx) + h(i + 1, j + 1) * tx) * tz;
	}

	/** The ground's height at a world point (blocks), from the atlas. */
	public static double heightAt(double x, double z) {
		if (!loaded) {
			return sea;
		}
		return hAt((x - x0) / res - 0.5, (z - z0) / res - 0.5);
	}

	private static void bake(byte[] kinds, int[] colors) {
		int w = size * UP;
		NativeImage img = new NativeImage(w, w, true);
		for (int py = 0; py < w; py++) {
			for (int px = 0; px < w; px++) {
				double fi = (px + 0.5) / UP - 0.5;
				double fj = (py + 0.5) / UP - 0.5;
				double y = hAt(fi, fj);
				int ki = Math.max(0, Math.min(size - 1, (int) Math.round(fi)));
				int kj = Math.max(0, Math.min(size - 1, (int) Math.round(fj)));
				int kind = kinds[kj * size + ki] & 0xFF;
				int rgb;
				if (y < sea - 0.3) {
					double depth = Math.min(1, (sea - y) / 24.0);
					rgb = lerp(0x6E9DDE, 0x22448E, depth);
				} else {
					rgb = kind < colors.length ? colors[kind] : 0x7FB238;
					// Higher ground: rockier and lighter, the tops white.
					if (y > 130) {
						rgb = lerp(rgb, 0x9A9C98, Math.min(1, (y - 130) / 50.0));
					}
					if (y > 185) {
						rgb = lerp(rgb, 0xF2F4F6, Math.min(1, (y - 185) / 25.0));
					}
					double dx = hAt(fi + 0.5, fj) - hAt(fi - 0.5, fj);
					double dz = hAt(fi, fj + 0.5) - hAt(fi, fj - 0.5);
					double shade = 1 - (dx + dz) * 0.035;
					shade = Math.max(0.6, Math.min(1.35, shade));
					rgb = scale(rgb, shade);
					if (y < sea + 1.5) {
						rgb = lerp(rgb, 0xD8CB94, 0.35);
					}
				}
				img.setPixel(px, py, 0xFF000000 | rgb);
			}
		}
		texture = new DynamicTexture(() -> "airdefense atlas", img);
		textureId = AirDefense.id("map/atlas");
		Minecraft.getInstance().getTextureManager().register(textureId, texture);
	}

	public static Identifier texture() {
		return textureId;
	}

	private static int lerp(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	private static int scale(int a, double k) {
		int r = (int) Math.min(255, ((a >> 16) & 255) * k);
		int g = (int) Math.min(255, ((a >> 8) & 255) * k);
		int b = (int) Math.min(255, (a & 255) * k);
		return r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------------------------------------
	// Borders

	/** The city whose region holds this point (its index in CITIES), or -1. */
	public static int regionAt(double x, double z) {
		return Territory.owner(seatX, seatZ, seatW, warp, x, z);
	}

	/** The country holding a city's region (-1: nobody yet, or unknown). */
	public static int countryOfCity(int index) {
		if (index < 0 || index >= seatKey.length) {
			return -1;
		}
		AtlasPoliticsPayload.Town t = cityTown(seatKey[index]);
		return t == null ? -1 : t.country();
	}
}
