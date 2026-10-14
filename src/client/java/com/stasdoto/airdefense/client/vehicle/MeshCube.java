package com.stasdoto.airdefense.client.vehicle;

import java.util.EnumSet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

/**
 * 2.0: one layer of a mesh part, drawn in place of a box: a cube with no faces of its own whose compile() puts out the
 * mesh's quads. It sits in an ordinary ModelPart tree, so the parts turn, hide and outline like the box models' parts.
 * Every corner is turned once per draw into scratch arrays, then the quads are put out by index (no allocation).
 */
public class MeshCube extends ModelPart.Cube {
	/** Parts smaller than this many blocks per block of distance are left out (well under a pixel). */
	private static final float LOD = 1f / 900f;

	/** -Dairdefense.meshFlip=true draws every quad the other way round (were the faces culled the wrong side). */
	private static final boolean FLIP = Boolean.getBoolean("airdefense.meshFlip");

	private static float[] px = new float[3 * 1024];
	private static float[] pn = new float[3 * 1024];
	private static float[] pv = new float[1024];
	private static int[] pc = new int[1024];
	/** Which corners are turned already in this draw (stamped with the draw's number). */
	private static int[] stamp = new int[1024];
	private static int draw;
	/** Quads smaller than about a pixel and a half are left out: (blocks per pixel at one block away x 1.5)^2. */
	private static final float PIXEL = 0.0013f * 1.5f;

	private final MeshLoader.Layer layer;
	private final MeshState state;
	private final int track;

	public MeshCube(MeshLoader.Layer layer, MeshState state, int track) {
		super(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, EnumSet.noneOf(Direction.class));
		this.layer = layer;
		this.state = state;
		this.track = track;
	}

	private static void ensure(int n) {
		if (pc.length < n) {
			int m = Math.max(n, pc.length * 2);
			px = new float[m * 3];
			pn = new float[m * 3];
			pv = new float[m];
			pc = new int[m];
			stamp = new int[m];
		}
	}

	private static int mul(int a, int b) {
		return (a * b + 127) / 255;
	}

	@Override
	public void compile(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color) {
		MeshLoader.Layer l = layer;
		MeshState s = state;
		float dist = s.distance;
		if (l.near > 0 && dist > l.near || dist * LOD > l.radius) {
			return;
		}
		int n = l.verts;
		ensure(n);
		float[] pos = l.pos;
		float[] nrm = l.nrm;
		float[] uv = l.uv;
		int[] col = l.col;
		byte[] flags = l.flags;
		float[] tx = px;
		float[] tn = pn;
		float[] tv = pv;
		int[] tc = pc;
		Matrix4f m = pose.pose();
		float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
		float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
		float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
		float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
		Matrix3f nm = pose.normal();
		float n00 = nm.m00(), n01 = nm.m01(), n02 = nm.m02();
		float n10 = nm.m10(), n11 = nm.m11(), n12 = nm.m12();
		float n20 = nm.m20(), n21 = nm.m21(), n22 = nm.m22();
		// The colour the whole layer is multiplied by (the model's colour and shade), and the paint's tint on top.
		int shade = s.shade;
		int ca = mul(color >>> 24, shade >>> 24);
		int cr = mul(color >> 16 & 255, shade >> 16 & 255);
		int cg = mul(color >> 8 & 255, shade >> 8 & 255);
		int cb = mul(color & 255, shade & 255);
		int tint = s.tint;
		int tr = mul(cr, tint >> 16 & 255);
		int tg = mul(cg, tint >> 8 & 255);
		int tb = mul(cb, tint & 255);
		boolean plain = ca == 255 && cr == 255 && cg == 255 && cb == 255;
		float shift = 0;
		if (l.anyTrack && l.period > 0) {
			float travel = track < 0 ? s.trackLeft : track > 0 ? s.trackRight : (s.trackLeft + s.trackRight) * 0.5f;
			float links = travel / MeshState.TRACK_PITCH;
			shift = (links - (float) Math.floor(links)) * l.period;
		}
		int[] q = l.quads;
		// Level of detail: the quads are sorted biggest first; far away the small ones (bolts, handles, thin edges) go.
		int count = q.length;
		float minArea = dist * PIXEL;
		minArea *= minArea;
		float[] areas = l.areas;
		if (minArea > 0 && areas.length > 0 && areas[areas.length - 1] < minArea) {
			int lo = 0;
			int hi = areas.length;
			while (lo < hi) {
				int mid = (lo + hi) >>> 1;
				if (areas[mid] >= minArea) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			count = lo * 4;
		}
		if (++draw == Integer.MAX_VALUE) {
			draw = 1;
			java.util.Arrays.fill(stamp, 0);
		}
		int[] st = stamp;
		int stampNow = draw;
		for (int k = 0; k < count; k++) {
			int i = q[k];
			if (st[i] == stampNow) {
				continue;
			}
			st[i] = stampNow;
			int i3 = i * 3;
			float x = pos[i3];
			float y = pos[i3 + 1];
			float z = pos[i3 + 2];
			tx[i3] = m00 * x + m10 * y + m20 * z + m30;
			tx[i3 + 1] = m01 * x + m11 * y + m21 * z + m31;
			tx[i3 + 2] = m02 * x + m12 * y + m22 * z + m32;
			float a = nrm[i3];
			float b = nrm[i3 + 1];
			float c = nrm[i3 + 2];
			float nx = n00 * a + n10 * b + n20 * c;
			float ny = n01 * a + n11 * b + n21 * c;
			float nz = n02 * a + n12 * b + n22 * c;
			float len = nx * nx + ny * ny + nz * nz;
			if (len > 1e-12f) {
				len = (float) (1 / Math.sqrt(len));
				nx *= len;
				ny *= len;
				nz *= len;
			}
			tn[i3] = nx;
			tn[i3 + 1] = ny;
			tn[i3 + 2] = nz;
			int f = flags[i];
			int v = col[i];
			if ((f & MeshLoader.FLAG_TINT) != 0) {
				v = mul(v >>> 24, ca) << 24 | mul(v >> 16 & 255, tr) << 16 | mul(v >> 8 & 255, tg) << 8 | mul(v & 255, tb);
			} else if (!plain) {
				v = mul(v >>> 24, ca) << 24 | mul(v >> 16 & 255, cr) << 16 | mul(v >> 8 & 255, cg) << 8 | mul(v & 255, cb);
			}
			tc[i] = v;
			tv[i] = (f & MeshLoader.FLAG_TRACK) != 0 ? uv[i * 2 + 1] + shift : uv[i * 2 + 1];
		}
		for (int k = 0; k < count; k++) {
			int i = q[FLIP ? k ^ 3 : k];
			int i3 = i * 3;
			vc.addVertex(tx[i3], tx[i3 + 1], tx[i3 + 2], tc[i], uv[i * 2], tv[i], overlay, light, tn[i3], tn[i3 + 1], tn[i3 + 2]);
		}
	}
}
