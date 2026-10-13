package com.stasdoto.airdefense.client.vehicle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import com.stasdoto.airdefense.AirDefense;

/**
 * 2.0: reads the baked meshes (tools/mesh/bake.py writes them: assets/airdefense/meshes/&lt;id&gt;.mesh, the texture at
 * textures/entity/mesh/&lt;id&gt;.png) into flat arrays, ready for {@link MeshCube}. Positions come out in the part's own
 * Minecraft space (blocks, y down, relative to the part's pivot), like the box models' cubes. A missing or broken file
 * gives null: the caller keeps the box model.
 */
public final class MeshLoader {
	public static final int SOLID = 0;
	public static final int GLASS = 1;
	public static final int CUTOUT = 2;
	public static final int FLAG_TINT = 1;
	public static final int FLAG_TRACK = 2;

	/** One draw layer of a part: its vertices (unique corners) and quads (indices, 4 each). */
	public static final class Layer {
		public final int type;
		/** Drawn only within this distance (blocks); 0 = always. */
		public final float near;
		/** A track belt's texture period (v units) its flagged corners scroll within; 0 = none. */
		public final float period;
		public final int verts;
		public final float[] pos;
		public final float[] nrm;
		public final float[] uv;
		public final int[] col;
		public final byte[] flags;
		public final int[] quads;
		/** How far the layer reaches from the pivot (blocks): small bits are left out far away. */
		public final float radius;
		public final boolean anyTint;
		public final boolean anyTrack;

		Layer(int type, float near, float period, float[] pos, float[] nrm, float[] uv, int[] col, byte[] flags, int[] quads) {
			this.type = type;
			this.near = near;
			this.period = period;
			this.verts = col.length;
			this.pos = pos;
			this.nrm = nrm;
			this.uv = uv;
			this.col = col;
			this.flags = flags;
			this.quads = quads;
			float r = 0;
			boolean t = false;
			boolean k = false;
			for (int i = 0; i < verts; i++) {
				float x = pos[i * 3];
				float y = pos[i * 3 + 1];
				float z = pos[i * 3 + 2];
				r = Math.max(r, x * x + y * y + z * z);
				t |= (flags[i] & FLAG_TINT) != 0;
				k |= (flags[i] & FLAG_TRACK) != 0;
			}
			this.radius = (float) Math.sqrt(r);
			this.anyTint = t;
			this.anyTrack = k;
		}
	}

	public static final class Part {
		public final String name;
		public final int parent;
		/** Vehicle space, metres (x right, y up, z forward). */
		public final float[] pivot;
		/** Degrees, Minecraft's sense (like the box models). */
		public final float[] rot;
		/** A rolling wheel's radius (metres), 0 = not rolling. */
		public final float spin;
		public final boolean steer;
		/** A track's belt: -1 left, 1 right, 0 none. */
		public final int track;
		public final Layer[] layers;

		Part(String name, int parent, float[] pivot, float[] rot, float spin, boolean steer, int track, Layer[] layers) {
			this.name = name;
			this.parent = parent;
			this.pivot = pivot;
			this.rot = rot;
			this.spin = spin;
			this.steer = steer;
			this.track = track;
			this.layers = layers;
		}

		public Layer layer(int type) {
			for (Layer l : layers) {
				if (l.type == type) {
					return l;
				}
			}
			return null;
		}
	}

	public static final class Mesh {
		public final String id;
		public final int texWidth;
		public final int texHeight;
		/** The paint is drawn white and coloured per car ({@link MeshState#tint}). */
		public final boolean tinted;
		public final Part[] parts;
		/** textures/entity/mesh/&lt;id&gt;.png */
		public Identifier texture;
		public final int vertices;
		public final int quads;

		Mesh(String id, int w, int h, boolean tinted, Part[] parts) {
			this.id = id;
			this.texWidth = w;
			this.texHeight = h;
			this.tinted = tinted;
			this.parts = parts;
			int v = 0;
			int q = 0;
			for (Part p : parts) {
				for (Layer l : p.layers) {
					v += l.verts;
					q += l.quads.length / 4;
				}
			}
			this.vertices = v;
			this.quads = q;
		}

		public boolean has(int type) {
			for (Part p : parts) {
				if (p.layer(type) != null) {
					return true;
				}
			}
			return false;
		}
	}

	private static final Map<String, Optional<Mesh>> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, String> ALIAS = new ConcurrentHashMap<>();
	private static volatile int generation;
	/** For the tests (comparing with the box models): no meshes at all while set. */
	private static volatile boolean disabled;

	private MeshLoader() {
	}

	/** Bumped whenever what {@link #get} returns may have changed: renderers look their meshes up again. */
	public static int generation() {
		return generation;
	}

	/** For the tests: draw {@code id} with another mesh (e.g. a vehicle with the test car). Null removes it. */
	public static void alias(String id, String meshId) {
		if (meshId == null) {
			ALIAS.remove(id);
		} else {
			ALIAS.put(id, meshId);
		}
		generation++;
	}

	/** For the tests: draws everything with the box models while {@code off}. */
	public static void disable(boolean off) {
		disabled = off;
		generation++;
	}

	/** Forgets what was read (after a resource reload). */
	public static void clear() {
		CACHE.clear();
		generation++;
	}

	/** The mesh for a vehicle type or prop id, or null (no such file, or it could not be read). */
	public static Mesh get(String id) {
		if (disabled) {
			return null;
		}
		String real = ALIAS.getOrDefault(id, id);
		return CACHE.computeIfAbsent(real, MeshLoader::load).orElse(null);
	}

	private static Optional<Mesh> load(String id) {
		String path = "assets/" + AirDefense.MOD_ID + "/meshes/" + id + ".mesh";
		byte[] data = null;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.getResourceManager() != null) {
				var res = mc.getResourceManager().getResource(AirDefense.id("meshes/" + id + ".mesh"));
				if (res.isPresent()) {
					try (InputStream in = res.get().open()) {
						data = in.readAllBytes();
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			data = null;
		}
		if (data == null) {
			try (InputStream in = MeshLoader.class.getClassLoader().getResourceAsStream(path)) {
				if (in != null) {
					data = in.readAllBytes();
				}
			} catch (IOException e) {
				data = null;
			}
		}
		if (data == null) {
			return Optional.empty();
		}
		try {
			long t = System.nanoTime();
			Mesh m = read(id, data);
			m.texture = AirDefense.id("textures/entity/mesh/" + id + ".png");
			AirDefense.LOGGER.info("Mesh {}: {} parts, {} vertices, {} quads ({} KB, {} ms)", id, m.parts.length, m.vertices, m.quads,
					data.length / 1024, (System.nanoTime() - t) / 1000000);
			return Optional.of(m);
		} catch (RuntimeException e) {
			AirDefense.LOGGER.error("Mesh {} could not be read: {}", id, e.toString());
			return Optional.empty();
		}
	}

	static Mesh read(String id, byte[] data) {
		ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
		if (b.get() != 'A' || b.get() != 'D' || b.get() != 'M' || b.get() != '2') {
			throw new IllegalArgumentException("not a mesh");
		}
		int version = b.getInt();
		if (version != 1) {
			throw new IllegalArgumentException("mesh version " + version);
		}
		int texW = b.getInt();
		int texH = b.getInt();
		int mflags = b.get();
		int nparts = b.getShort();
		Part[] parts = new Part[nparts];
		for (int pi = 0; pi < nparts; pi++) {
			int len = b.getShort() & 0xFFFF;
			byte[] nb = new byte[len];
			b.get(nb);
			String name = new String(nb, StandardCharsets.UTF_8);
			int parent = b.getShort();
			float[] pivot = {b.getFloat(), b.getFloat(), b.getFloat()};
			float[] rot = {b.getFloat(), b.getFloat(), b.getFloat()};
			float spin = b.getFloat();
			boolean steer = b.get() != 0;
			int track = b.get();
			int nl = b.get();
			Layer[] layers = new Layer[nl];
			for (int li = 0; li < nl; li++) {
				int type = b.get();
				float near = b.getFloat();
				float scale = b.getFloat();
				float reserved = b.getFloat();
				int nv = b.getInt();
				int nq = b.getInt();
				float[] pos = new float[nv * 3];
				float[] nrm = new float[nv * 3];
				float[] uv = new float[nv * 2];
				int[] col = new int[nv];
				byte[] fl = new byte[nv];
				for (int i = 0; i < nv; i++) {
					// Vehicle space (y up) -> the part's Minecraft space (y down); metres are blocks.
					pos[i * 3] = b.getShort() * scale;
					pos[i * 3 + 1] = -b.getShort() * scale;
					pos[i * 3 + 2] = b.getShort() * scale;
					nrm[i * 3] = b.get() / 127f;
					nrm[i * 3 + 1] = -b.get() / 127f;
					nrm[i * 3 + 2] = b.get() / 127f;
					uv[i * 2] = (b.getShort() & 0xFFFF) / 65535f;
					uv[i * 2 + 1] = (b.getShort() & 0xFFFF) / 65535f;
					col[i] = b.getInt();
					fl[i] = b.get();
				}
				int[] quads = new int[nq * 4];
				boolean big = nv > 65535;
				for (int i = 0; i < quads.length; i++) {
					int k = big ? b.getInt() : b.getShort() & 0xFFFF;
					if (k < 0 || k >= nv) {
						throw new IllegalArgumentException("index out of range in " + name);
					}
					quads[i] = k;
				}
				layers[li] = new Layer(type, near, Math.max(0, reserved), pos, nrm, uv, col, fl, quads);
			}
			if (parent >= pi) {
				throw new IllegalArgumentException("part " + name + " before its parent");
			}
			parts[pi] = new Part(name, parent, pivot, rot, spin, steer, track, layers);
		}
		return new Mesh(id, texW, texH, (mflags & 1) != 0, parts);
	}
}
