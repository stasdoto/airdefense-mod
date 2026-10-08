package com.stasdoto.airdefense.client.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.nation.AtlasPoliticsPayload;
import com.stasdoto.airdefense.nation.Territory;

/**
 * The atlas' lines and shapes as pictures (1.25.1): the countries' land and borders, the towns, depots, hamlets and
 * roads are painted once into tiles of 256x256 pixels (on a background thread, at the detail the zoom needs) and the
 * map just lays the tiles down - instead of drawing tens of thousands of little rectangles every frame, which made
 * the map stutter. A tile is painted again only when who holds what changes.
 */
final class AtlasTiles {
	private static final int TILE = 256;
	private static final int MIN_LOD = -2;
	private static final int MAX_LOD = 5;
	private static final int MAX_TILES = 96;
	/** Uploads of finished tiles per frame (each is a texture upload). */
	private static final int UPLOADS_PER_FRAME = 3;

	private record Tile(Identifier id, DynamicTexture texture, int version) {
	}

	private record Done(long key, int version, int generation, int[] pixels) {
	}

	private static final Map<Long, Tile> TILES = new LinkedHashMap<>(64, 0.75f, true);
	private static final Set<Long> PENDING = new HashSet<>();
	private static final ConcurrentLinkedQueue<Done> DONE = new ConcurrentLinkedQueue<>();
	private static ExecutorService worker;
	/** Bumped when the atlas is loaded or cleared: work for an older one is thrown away. */
	private static int generation;
	private static int lastAtlasVersion = -1;
	/** For the tests: tiles painted, the longest frame of the map (ns). */
	static int painted;

	private AtlasTiles() {
	}

	private static long key(int lod, int tx, int tz) {
		return ((long) (lod + 8) << 56) ^ (((long) tx & 0xFFFFFFFL) << 28) ^ ((long) tz & 0xFFFFFFFL);
	}

	/** Blocks per texel at a detail level (a power of two; below 1 when zoomed right in). */
	private static double bpp(int lod) {
		return Math.pow(2, lod);
	}

	/** The detail for a zoom: a texel about half a GUI pixel. */
	static int lodFor(float pxPerBlock) {
		double bpp = 1.0 / (pxPerBlock * 2.0);
		int lod = (int) Math.round(Math.log(bpp) / Math.log(2));
		return Mth.clamp(lod, MIN_LOD, MAX_LOD);
	}

	/** Forgets every tile (the atlas was cleared or replaced). */
	static void clear() {
		generation++;
		PENDING.clear();
		DONE.clear();
		Minecraft mc = Minecraft.getInstance();
		for (Tile t : TILES.values()) {
			mc.getTextureManager().release(t.id());
		}
		TILES.clear();
	}

	/**
	 * Lays the tiles over the map rectangle: {@code toSx/toSy} turn world coordinates into the screen's. Tiles not
	 * painted yet are asked for; meanwhile the coarser tile under them (if any) stands in.
	 */
	static void draw(GuiGraphicsExtractor g, float scale, double centerX, double centerZ, int mx0, int my0, int mx1, int my1) {
		if (!AtlasClient.loaded) {
			return;
		}
		if (AtlasClient.version != lastAtlasVersion) {
			lastAtlasVersion = AtlasClient.version;
		}
		uploadFinished();
		int lod = lodFor(scale);
		double world = TILE * bpp(lod);
		double cx = (mx0 + mx1) / 2.0;
		double cy = (my0 + my1) / 2.0;
		double wx0 = centerX + (mx0 - cx) / scale;
		double wx1 = centerX + (mx1 - cx) / scale;
		double wz0 = centerZ + (my0 - cy) / scale;
		double wz1 = centerZ + (my1 - cy) / scale;
		// Only the atlas' own square.
		int ax0 = AtlasClient.x0;
		int az0 = AtlasClient.z0;
		int span = AtlasClient.size * AtlasClient.res;
		int tx0 = (int) Math.floor(Math.max(wx0, ax0 - 600) / world);
		int tx1 = (int) Math.floor(Math.min(wx1, ax0 + span + 600) / world);
		int tz0 = (int) Math.floor(Math.max(wz0, az0 - 600) / world);
		int tz1 = (int) Math.floor(Math.min(wz1, az0 + span + 600) / world);
		for (int tx = tx0; tx <= tx1; tx++) {
			for (int tz = tz0; tz <= tz1; tz++) {
				int sx0 = (int) Math.floor(cx + (tx * world - centerX) * scale);
				int sy0 = (int) Math.floor(cy + (tz * world - centerZ) * scale);
				int sx1 = (int) Math.floor(cx + ((tx + 1) * world - centerX) * scale);
				int sy1 = (int) Math.floor(cy + ((tz + 1) * world - centerZ) * scale);
				long k = key(lod, tx, tz);
				Tile t = TILES.get(k);
				if (t == null || t.version() != AtlasClient.version) {
					request(lod, tx, tz);
				}
				if (t != null) {
					g.blit(RenderPipelines.GUI_TEXTURED, t.id(), sx0, sy0, 0f, 0f, sx1 - sx0, sy1 - sy0, TILE, TILE, TILE, TILE);
					continue;
				}
				// The coarser tile covering this one, its quarter stretched over it.
				if (lod < MAX_LOD) {
					Tile parent = TILES.get(key(lod + 1, Math.floorDiv(tx, 2), Math.floorDiv(tz, 2)));
					if (parent != null) {
						int u = Math.floorMod(tx, 2) * TILE / 2;
						int v = Math.floorMod(tz, 2) * TILE / 2;
						g.blit(RenderPipelines.GUI_TEXTURED, parent.id(), sx0, sy0, u, v, sx1 - sx0, sy1 - sy0, TILE / 2, TILE / 2, TILE, TILE);
					}
				}
			}
		}
	}

	private static void request(int lod, int tx, int tz) {
		long k = key(lod, tx, tz);
		if (!PENDING.add(k)) {
			return;
		}
		if (worker == null) {
			worker = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "airdefense-map-tiles");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY + 1);
				return t;
			});
		}
		int version = AtlasClient.version;
		int gen = generation;
		Snapshot snap = Snapshot.take();
		worker.execute(() -> {
			try {
				int[] px = paint(snap, lod, tx, tz);
				DONE.add(new Done(k, version, gen, px));
			} catch (RuntimeException e) {
				AirDefense.LOGGER.warn("[airdefense] map tile failed", e);
				DONE.add(new Done(k, version, gen, null));
			}
		});
	}

	private static void uploadFinished() {
		Minecraft mc = Minecraft.getInstance();
		for (int n = 0; n < UPLOADS_PER_FRAME; n++) {
			Done d = DONE.poll();
			if (d == null) {
				return;
			}
			PENDING.remove(d.key());
			if (d.generation() != generation || d.pixels() == null) {
				continue;
			}
			NativeImage img = new NativeImage(TILE, TILE, true);
			int[] px = d.pixels();
			for (int y = 0; y < TILE; y++) {
				for (int x = 0; x < TILE; x++) {
					img.setPixel(x, y, px[y * TILE + x]);
				}
			}
			Tile old = TILES.remove(d.key());
			if (old != null) {
				mc.getTextureManager().release(old.id());
			}
			Identifier id = AirDefense.id("map/tile_" + Long.toHexString(d.key()) + "_" + d.version());
			DynamicTexture tex = new DynamicTexture(() -> "airdefense map tile", img);
			mc.getTextureManager().register(id, tex);
			TILES.put(d.key(), new Tile(id, tex, d.version()));
			painted++;
			// Too many: the ones not looked at for longest go.
			while (TILES.size() > MAX_TILES) {
				Iterator<Map.Entry<Long, Tile>> it = TILES.entrySet().iterator();
				Map.Entry<Long, Tile> e = it.next();
				mc.getTextureManager().release(e.getValue().id());
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// What a tile needs, copied on the render thread (the painting runs on its own)

	private record Snapshot(List<AtlasClient.City> cities, List<AtlasClient.Hamlet> hamlets, List<AtlasClient.Depot> depots,
			List<AtlasClient.Road> roads, int[] seatX, int[] seatZ, float[] seatW, long warp, int[] regionCountry, int[] cityColor,
			int[] hamletColor, boolean[] cityWar, boolean[] hamletWar, Map<Integer, Integer> countryRgb, Set<Integer> warCountries,
			int ax0, int az0, int span) {
		static Snapshot take() {
			List<AtlasClient.City> cities = List.copyOf(AtlasClient.CITIES);
			List<AtlasClient.Hamlet> hamlets = List.copyOf(AtlasClient.HAMLETS);
			int[] regionCountry = new int[cities.size()];
			int[] cityColor = new int[cities.size()];
			boolean[] cityWar = new boolean[cities.size()];
			for (int i = 0; i < cities.size(); i++) {
				regionCountry[i] = AtlasClient.countryOfCity(i);
				AtlasPoliticsPayload.Town t = AtlasClient.cityTown(cities.get(i).key());
				cityColor[i] = t == null ? 0xFFB0B8C0 : countryColor(t.country());
				cityWar[i] = t != null && war(t.country());
			}
			int[] hamletColor = new int[hamlets.size()];
			boolean[] hamletWar = new boolean[hamlets.size()];
			for (int i = 0; i < hamlets.size(); i++) {
				AtlasPoliticsPayload.Town t = AtlasClient.hamletTown(hamlets.get(i).key());
				hamletColor[i] = t == null ? 0xFFB0B8C0 : countryColor(t.country());
				hamletWar[i] = t != null && war(t.country());
			}
			Map<Integer, Integer> rgb = new java.util.HashMap<>();
			Set<Integer> wars = new HashSet<>();
			for (int c : regionCountry) {
				if (c >= 0) {
					rgb.put(c, countryColor(c));
					if (war(c)) {
						wars.add(c);
					}
				}
			}
			return new Snapshot(cities, hamlets, List.copyOf(AtlasClient.DEPOTS), List.copyOf(AtlasClient.ROADS), AtlasClient.seats(0),
					AtlasClient.seats(1), AtlasClient.seatWeights(), AtlasClient.warp, regionCountry, cityColor, hamletColor, cityWar, hamletWar,
					rgb, wars, AtlasClient.x0, AtlasClient.z0, AtlasClient.size * AtlasClient.res);
		}

		int countryAt(double x, double z) {
			if (x < ax0 - 600 || z < az0 - 600 || x > ax0 + span + 600 || z > az0 + span + 600) {
				return -2;
			}
			int r = Territory.owner(seatX, seatZ, seatW, warp, x, z);
			return r < 0 ? -1 : regionCountry[r];
		}
	}

	/** A country's colour on the map (a little different for each country, so two of one dye still look apart). */
	static int countryColor(int country) {
		var c = AtlasClient.country(country);
		if (c == null) {
			return 0xFF9AA4AE;
		}
		int rgb = c.argb() & 0xFFFFFF;
		int shift = Math.floorMod(country * 37, 31) - 15;
		int r = Mth.clamp(((rgb >> 16) & 255) + shift, 0, 255);
		int gr = Mth.clamp(((rgb >> 8) & 255) - shift / 2, 0, 255);
		int b = Mth.clamp((rgb & 255) + shift / 2, 0, 255);
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}

	private static boolean war(int country) {
		var c = AtlasClient.country(country);
		return c != null && c.war();
	}

	// ------------------------------------------------------------------------------------------------
	// Painting (background thread)

	private static int[] paint(Snapshot s, int lod, int tx, int tz) {
		int[] px = new int[TILE * TILE];
		double bpp = bpp(lod);
		double wx0 = tx * TILE * bpp;
		double wz0 = tz * TILE * bpp;
		Canvas c = new Canvas(px, wx0, wz0, bpp);
		territory(c, s, lod);
		towns(c, s, lod);
		roads(c, s, lod);
		return px;
	}

	/** The countries' land lightly tinted, their borders bold (red against a country at war with you). */
	private static void territory(Canvas c, Snapshot s, int lod) {
		int[] owner = new int[TILE * TILE];
		int[] region = new int[TILE * TILE];
		for (int j = 0; j < TILE; j++) {
			double z = c.wz0 + (j + 0.5) * c.bpp;
			for (int i = 0; i < TILE; i++) {
				double x = c.wx0 + (i + 0.5) * c.bpp;
				if (x < s.ax0() - 600 || z < s.az0() - 600 || x > s.ax0() + s.span() + 600 || z > s.az0() + s.span() + 600) {
					owner[j * TILE + i] = -2;
					region[j * TILE + i] = -2;
					continue;
				}
				int r = Territory.owner(s.seatX(), s.seatZ(), s.seatW(), s.warp(), x, z);
				region[j * TILE + i] = r;
				owner[j * TILE + i] = r < 0 ? -1 : s.regionCountry()[r];
			}
		}
		// The border lines about a GUI pixel and a half wide whatever the zoom (a texel is about half a GUI pixel).
		int w = 3;
		for (int j = 0; j < TILE; j++) {
			for (int i = 0; i < TILE; i++) {
				int o = owner[j * TILE + i];
				if (o >= 0) {
					c.blendPx(i, j, (s.countryRgb().getOrDefault(o, 0xFF9AA4AE) & 0xFFFFFF) | 0x26000000);
				}
			}
		}
		for (int j = 0; j < TILE; j++) {
			for (int i = 0; i < TILE; i++) {
				int o = owner[j * TILE + i];
				int r = region[j * TILE + i];
				boolean border = false;
				boolean regionLine = false;
				boolean war = false;
				for (int d = 1; d <= w && !border; d++) {
					if (i + d < TILE) {
						int b = owner[j * TILE + i + d];
						if (b != o && b != -2 && o != -2) {
							border = true;
							war = s.warCountries().contains(o) || s.warCountries().contains(b);
						} else if (d == 1 && region[j * TILE + i + 1] != r) {
							regionLine = true;
						}
					}
					if (j + d < TILE) {
						int b = owner[(j + d) * TILE + i];
						if (b != o && b != -2 && o != -2) {
							border = true;
							war = s.warCountries().contains(o) || s.warCountries().contains(b);
						} else if (d == 1 && region[(j + 1) * TILE + i] != r) {
							regionLine = true;
						}
					}
				}
				if (border) {
					c.setPx(i, j, war ? 0xE0FF4030 : 0xD0202428);
				} else if (regionLine && ((i + j) & 7) < 4) {
					c.blendPx(i, j, 0x70FFFFFF);
				}
			}
		}
	}

	/** Cities (the blocks of their street plan in the country's colour, the edge drawn), depots, hamlets. */
	private static void towns(Canvas c, Snapshot s, int lod) {
		boolean far = lod >= 3;
		for (int k = 0; k < s.cities().size(); k++) {
			AtlasClient.City city = s.cities().get(k);
			int n = city.n();
			if (!c.overlaps(city.gx()[0], city.gz()[0], city.gx()[n], city.gz()[n])) {
				continue;
			}
			int fill = (s.cityColor()[k] & 0xFFFFFF) | (far ? 0xC0000000 : 0x55000000);
			int edge = s.cityWar()[k] ? 0xFFFF3A2A : 0xFF14181C;
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (!city.cellOn(i, j)) {
						continue;
					}
					c.fillWorld(city.gx()[i], city.gz()[j], city.gx()[i + 1], city.gz()[j + 1], fill);
				}
			}
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (!city.cellOn(i, j)) {
						continue;
					}
					int x0 = city.gx()[i];
					int x1 = city.gx()[i + 1];
					int z0 = city.gz()[j];
					int z1 = city.gz()[j + 1];
					if (!city.cellOn(i - 1, j)) {
						c.lineWorld(x0, z0, x0, z1, 1.2, edge);
					}
					if (!city.cellOn(i + 1, j)) {
						c.lineWorld(x1, z0, x1, z1, 1.2, edge);
					}
					if (!city.cellOn(i, j - 1)) {
						c.lineWorld(x0, z0, x1, z0, 1.2, edge);
					}
					if (!city.cellOn(i, j + 1)) {
						c.lineWorld(x0, z1, x1, z1, 1.2, edge);
					}
				}
			}
		}
		for (AtlasClient.Depot d : s.depots()) {
			if (!c.overlaps(d.x0(), d.z0(), d.x1() + 1, d.z1() + 1)) {
				continue;
			}
			c.fillWorld(d.x0(), d.z0(), d.x1() + 1, d.z1() + 1, 0xC05A5E62);
			for (int[] w : d.warehouses()) {
				c.fillWorld(w[0], w[1], w[2] + 1, w[3] + 1, 0xFFC8CCD0);
				c.lineWorld(w[0], w[1], w[2] + 1, w[1], 1.0, 0xFF3A3E42);
			}
		}
		for (int k = 0; k < s.hamlets().size(); k++) {
			AtlasClient.Hamlet h = s.hamlets().get(k);
			if (!c.overlaps(h.x() - 120, h.z() - 120, h.x() + 120, h.z() + 120)) {
				continue;
			}
			c.polygonWorld(h.xs(), h.zs(), (s.hamletColor()[k] & 0xFFFFFF) | (far ? 0xB0000000 : 0x50000000),
					s.hamletWar()[k] ? 0xFFFF3A2A : 0xFF14181C);
		}
	}

	/** Roads: highways wide and yellow with a dark edge, country roads thin and white (left out far out). */
	private static void roads(Canvas c, Snapshot s, int lod) {
		for (int pass = 0; pass < 2; pass++) {
			for (AtlasClient.Road r : s.roads()) {
				if (!c.overlaps(r.minX() - 20, r.minZ() - 20, r.maxX() + 20, r.maxZ() + 20)) {
					continue;
				}
				if (!r.highway() && lod >= 4) {
					continue;
				}
				// Width in texels: the real width, but never thinner than a line you can see.
				double width = r.highway() ? Math.max(3, 15 / c.bpp) : Math.max(1.6, 5 / c.bpp);
				int color;
				if (pass == 0) {
					width += 2;
					color = 0xFF2A2420;
				} else {
					color = r.highway() ? 0xFFF2C94A : r.dirt() ? 0xFFD8CCA8 : 0xFFE8E8E0;
				}
				int[] xs = r.xs();
				int[] zs = r.zs();
				for (int i = 0; i + 1 < xs.length; i++) {
					c.lineTexels((xs[i] - c.wx0) / c.bpp, (zs[i] - c.wz0) / c.bpp, (xs[i + 1] - c.wx0) / c.bpp, (zs[i + 1] - c.wz0) / c.bpp, width,
							color);
				}
			}
		}
	}

	/** A tile's pixels and its place in the world. */
	private static final class Canvas {
		final int[] px;
		final double wx0;
		final double wz0;
		final double bpp;

		Canvas(int[] px, double wx0, double wz0, double bpp) {
			this.px = px;
			this.wx0 = wx0;
			this.wz0 = wz0;
			this.bpp = bpp;
		}

		boolean overlaps(double ax, double az, double bx, double bz) {
			double size = TILE * bpp;
			return bx >= wx0 && ax <= wx0 + size && bz >= wz0 && az <= wz0 + size;
		}

		void setPx(int i, int j, int argb) {
			if (i >= 0 && j >= 0 && i < TILE && j < TILE) {
				px[j * TILE + i] = argb;
			}
		}

		void blendPx(int i, int j, int argb) {
			if (i < 0 || j < 0 || i >= TILE || j >= TILE) {
				return;
			}
			int a = argb >>> 24;
			if (a == 255) {
				px[j * TILE + i] = argb;
				return;
			}
			int dst = px[j * TILE + i];
			int da = dst >>> 24;
			float sa = a / 255f;
			float oa = sa + da / 255f * (1 - sa);
			if (oa <= 0) {
				return;
			}
			int r = (int) ((((argb >> 16) & 255) * sa + ((dst >> 16) & 255) * (da / 255f) * (1 - sa)) / oa);
			int g = (int) ((((argb >> 8) & 255) * sa + ((dst >> 8) & 255) * (da / 255f) * (1 - sa)) / oa);
			int b = (int) (((argb & 255) * sa + (dst & 255) * (da / 255f) * (1 - sa)) / oa);
			px[j * TILE + i] = (int) (oa * 255) << 24 | Mth.clamp(r, 0, 255) << 16 | Mth.clamp(g, 0, 255) << 8 | Mth.clamp(b, 0, 255);
		}

		void fillWorld(double x0, double z0, double x1, double z1, int argb) {
			int i0 = (int) Math.floor((x0 - wx0) / bpp);
			int i1 = (int) Math.ceil((x1 - wx0) / bpp);
			int j0 = (int) Math.floor((z0 - wz0) / bpp);
			int j1 = (int) Math.ceil((z1 - wz0) / bpp);
			for (int j = Math.max(0, j0); j < Math.min(TILE, j1); j++) {
				for (int i = Math.max(0, i0); i < Math.min(TILE, i1); i++) {
					blendPx(i, j, argb);
				}
			}
		}

		void lineWorld(double x0, double z0, double x1, double z1, double widthTexels, int argb) {
			lineTexels((x0 - wx0) / bpp, (z0 - wz0) / bpp, (x1 - wx0) / bpp, (z1 - wz0) / bpp, widthTexels, argb);
		}

		/** A thick line between two points in texel coordinates (every texel within half the width of it). */
		void lineTexels(double x0, double y0, double x1, double y1, double width, int argb) {
			double hw = width / 2;
			int i0 = (int) Math.floor(Math.min(x0, x1) - hw - 1);
			int i1 = (int) Math.ceil(Math.max(x0, x1) + hw + 1);
			int j0 = (int) Math.floor(Math.min(y0, y1) - hw - 1);
			int j1 = (int) Math.ceil(Math.max(y0, y1) + hw + 1);
			if (i1 < 0 || j1 < 0 || i0 >= TILE || j0 >= TILE) {
				return;
			}
			double dx = x1 - x0;
			double dy = y1 - y0;
			double len2 = dx * dx + dy * dy;
			for (int j = Math.max(0, j0); j <= Math.min(TILE - 1, j1); j++) {
				for (int i = Math.max(0, i0); i <= Math.min(TILE - 1, i1); i++) {
					double cx = i + 0.5;
					double cy = j + 0.5;
					double t = len2 < 1e-9 ? 0 : Mth.clamp(((cx - x0) * dx + (cy - y0) * dy) / len2, 0, 1);
					double ex = x0 + dx * t - cx;
					double ey = y0 + dy * t - cy;
					if (ex * ex + ey * ey <= hw * hw) {
						setOver(i, j, argb);
					}
				}
			}
		}

		/** Opaque colours are written, see-through ones blended. */
		private void setOver(int i, int j, int argb) {
			if (argb >>> 24 == 255) {
				px[j * TILE + i] = argb;
			} else {
				blendPx(i, j, argb);
			}
		}

		/** A filled polygon (by rows) with its outline. */
		void polygonWorld(int[] xs, int[] zs, int fill, int edge) {
			int n = xs.length;
			if (n < 3) {
				return;
			}
			double[] tx = new double[n];
			double[] ty = new double[n];
			double top = Double.MAX_VALUE;
			double bottom = -Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				tx[i] = (xs[i] - wx0) / bpp;
				ty[i] = (zs[i] - wz0) / bpp;
				top = Math.min(top, ty[i]);
				bottom = Math.max(bottom, ty[i]);
			}
			double[] at = new double[n];
			for (int j = Math.max(0, (int) Math.floor(top)); j <= Math.min(TILE - 1, (int) Math.ceil(bottom)); j++) {
				double yc = j + 0.5;
				int k = 0;
				for (int i = 0; i < n; i++) {
					int m = (i + 1) % n;
					if (ty[i] <= yc && ty[m] > yc || ty[m] <= yc && ty[i] > yc) {
						at[k++] = tx[i] + (yc - ty[i]) / (ty[m] - ty[i]) * (tx[m] - tx[i]);
					}
				}
				java.util.Arrays.sort(at, 0, k);
				for (int i = 0; i + 1 < k; i += 2) {
					for (int x = Math.max(0, (int) Math.round(at[i])); x < Math.min(TILE, (int) Math.round(at[i + 1])); x++) {
						blendPx(x, j, fill);
					}
				}
			}
			for (int i = 0; i < n; i++) {
				int m = (i + 1) % n;
				lineTexels(tx[i], ty[i], tx[m], ty[m], 1.2, edge);
			}
		}
	}

	/** For the tests. */
	static List<String> stats() {
		List<String> out = new ArrayList<>();
		out.add("tiles " + TILES.size() + " painted " + painted + " waiting " + PENDING.size());
		return out;
	}
}
