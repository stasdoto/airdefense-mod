package com.stasdoto.airdefense.client.vehicle;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.client.ClientSettings;
import com.stasdoto.airdefense.vehicle.GenGeometry;
import com.stasdoto.airdefense.vehicle.VehicleGeometry;

/**
 * 2.0: a vehicle's lights - the headlights (and at night the beams they throw and the pool of light on the road ahead),
 * the tail lights, brighter when it brakes, the white reversing lights, the amber indicators blinking when it turns.
 * Drawn as glowing sprites (bright by night, faint by day), the same way as the service vehicles' beacons. Where the
 * lamps are comes from the vehicle's size (its generated geometry): low on a car, higher on a lorry or a bus, small
 * blackout lamps on the glacis of a tank.
 */
public final class VehicleLights {
	/** What is lit: the bits of {@code flags}. */
	public static final int HEAD = 1;
	public static final int BRAKE = 2;
	public static final int LEFT = 4;
	public static final int RIGHT = 8;
	public static final int REVERSE = 16;
	/** The tail lights (on with the headlights). */
	public static final int TAIL = 32;

	public static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(AirDefense.id("textures/misc/glow_dot.png"));

	/** Where a vehicle's lamps are (metres; x across from the middle, y up from the ground, z forward from the middle). */
	public record Lamps(float headX, float headY, float headZ, float tailX, float tailY, float tailZ, float lamp, float width, float length,
			boolean tracked, boolean big) {
	}

	private static final Map<String, Lamps> BY_ID = new HashMap<>();

	private VehicleLights() {
	}

	/** The lamps of a vehicle of this geometry (worked out once). */
	public static Lamps of(VehicleGeometry.Geometry g) {
		return BY_ID.computeIfAbsent(g.id(), k -> make(g));
	}

	/** The lamps of a prop's model (a town car, a bus, a lorry), from the generated geometry of that model; null if unknown. */
	public static Lamps ofModel(String model) {
		if (!GEN_SCANNED) {
			synchronized (BY_ID) {
				if (!GEN_SCANNED) {
					GEN_SCANNED = true;
					for (Field f : GenGeometry.class.getFields()) {
						if (Modifier.isStatic(f.getModifiers()) && f.getType() == VehicleGeometry.Geometry.class) {
							try {
								VehicleGeometry.Geometry g = (VehicleGeometry.Geometry) f.get(null);
								BY_ID.putIfAbsent(g.id(), make(g));
							} catch (IllegalAccessException e) {
								// Not one of ours.
							}
						}
					}
				}
			}
		}
		return BY_ID.get(model);
	}

	private static boolean GEN_SCANNED;

	private static Lamps make(VehicleGeometry.Geometry g) {
		float l = g.length();
		float w = g.width();
		float h = g.height();
		if (g.tracked()) {
			// Small blackout lamps on the front of the hull, a convoy light at the back.
			float y = Mth.clamp(h * 0.38f, 1.0f, 1.6f);
			return new Lamps(w * 0.36f, y, l * 0.48f, w * 0.38f, y, -l * 0.49f, 0.12f, w, l, true, false);
		}
		boolean car = h < 2.1f && l < 5.6f;
		boolean bus = g.id().contains("bus") || g.id().equals("tram");
		float hy = car ? 0.66f : bus ? 0.82f : Mth.clamp(h * 0.33f, 0.85f, 1.15f);
		float ty = car ? 0.86f : bus ? 1.0f : Mth.clamp(h * 0.3f, 0.8f, 1.1f);
		// Mirrors stick out beyond the body: the lamps sit inside the body's corners.
		float bodyW = car ? Math.min(w, 1.85f) : w;
		return new Lamps(bodyW * 0.36f, hy, l * 0.5f - 0.04f, bodyW * 0.41f, ty, -l * 0.5f + 0.04f, car ? 0.15f : 0.19f, bodyW, l, false, !car);
	}

	// ------------------------------------------------------------------------------------------------
	// Day and night

	private static long darkTick = Long.MIN_VALUE;
	private static float dark;

	/** How dark it is for the lights (0 bright day .. 1 night), dusk and rain counting; worked out once a tick. */
	public static float darkness(Level level) {
		long now = level.getGameTime();
		if (now == darkTick) {
			return dark;
		}
		darkTick = now;
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		// 0 = 6 am; dusk from 11800 (17:48) to 13200, dawn from 22600 to 23800.
		float d;
		if (t < 11800) {
			d = 0;
		} else if (t < 13200) {
			d = (t - 11800) / 1400f;
		} else if (t < 22600) {
			d = 1;
		} else if (t < 23800) {
			d = 1 - (t - 22600) / 1200f;
		} else {
			d = 0;
		}
		d = Math.max(d, level.getRainLevel(1f) * 0.65f + level.getThunderLevel(1f) * 0.25f);
		dark = Mth.clamp(d, 0, 1);
		return dark;
	}

	// ------------------------------------------------------------------------------------------------
	// Drawing

	private static final float[][] RING = new float[256][24 * 18];
	private static int ringAt;
	private static final Vector3f CAM_RIGHT = new Vector3f();
	private static final Vector3f CAM_UP = new Vector3f();

	/**
	 * Submits the lights of one vehicle, in its renderer's space (the pose at the vehicle's feet, not yet turned).
	 * {@code yaw} its heading (degrees, the game's), {@code lift} how far its body is raised, {@code flags} what is lit,
	 * {@code tick} the clock the indicators blink by.
	 */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Quaternionf camera, double distSq, float yaw, float lift, Lamps l,
			int flags, float dark, long tick) {
		if (!ClientSettings.vehicleLights() || flags == 0 || l == null) {
			return;
		}
		float dist = (float) Math.sqrt(distSq);
		if (dist > 160) {
			return;
		}
		float yr = yaw * Mth.DEG_TO_RAD;
		float fx = -Mth.sin(yr);
		float fz = Mth.cos(yr);
		float rx = -fz;
		float rz = fx;
		CAM_RIGHT.set(1, 0, 0);
		camera.transform(CAM_RIGHT);
		CAM_UP.set(0, 1, 0);
		camera.transform(CAM_UP);
		// One buffer of quads for the whole vehicle (each: 4 corners of x, y, z, u, v; then r, g, b, a).
		// (From a ring of buffers: a buffer comes round again only hundreds of vehicles later, frames after it was drawn.)
		float[] q = RING[ringAt = (ringAt + 1) % RING.length];
		int[] n = {0};
		boolean night = dark > 0.25f;
		// A lamp seen from afar stays visible: a little bigger with the distance.
		float grow = 1 + dist * 0.012f;
		boolean blink = (tick / 7) % 2 == 0;
		for (int side = -1; side <= 1; side += 2) {
			float hx = rx * l.headX() * side + fx * l.headZ();
			float hz = rz * l.headX() * side + fz * l.headZ();
			float hy = lift + l.headY();
			if ((flags & HEAD) != 0) {
				float a = night ? 0.95f : 0.55f;
				float s = l.lamp() * (night ? 1.9f : 1.1f) * grow;
				billboard(q, n, hx + fx * 0.05f, hy, hz + fz * 0.05f, s, 1f, 0.95f, 0.82f, a);
				if (night && dist < 96) {
					// The beam: a soft cone in the air ahead (lying flat and standing upright, crossed), fading out.
					beam(q, n, hx, hy, hz, fx, fz, rx, rz, l.big() ? 13f : 11f, 0.22f * dark);
				}
			}
			float tx = rx * l.tailX() * side + fx * l.tailZ();
			float tz = rz * l.tailX() * side + fz * l.tailZ();
			float ty = lift + l.tailY();
			boolean brake = (flags & BRAKE) != 0;
			if ((flags & TAIL) != 0 || brake) {
				float a = brake ? 1f : night ? 0.8f : 0.35f;
				float s = l.lamp() * (brake ? 1.7f : night ? 1.25f : 0.8f) * grow;
				billboard(q, n, tx - fx * 0.05f, ty, tz - fz * 0.05f, s, 1f, brake ? 0.08f : 0.12f, 0.06f, a);
			}
			if ((flags & REVERSE) != 0) {
				float ix = rx * l.tailX() * 0.55f * side + fx * l.tailZ();
				float iz = rz * l.tailX() * 0.55f * side + fz * l.tailZ();
				billboard(q, n, ix - fx * 0.06f, ty - 0.08f, iz - fz * 0.06f, l.lamp() * 1.2f * grow, 1f, 1f, 0.96f, 0.9f);
			}
			// The indicators at the corners: the right side (side +1) and the left (-1).
			boolean ind = side > 0 ? (flags & RIGHT) != 0 : (flags & LEFT) != 0;
			if (ind && blink) {
				float ox = rx * (l.width() * 0.48f) * side;
				float oz = rz * (l.width() * 0.48f) * side;
				float s = l.lamp() * 1.3f * grow;
				billboard(q, n, ox + fx * (l.headZ() - 0.1f), hy + 0.05f, oz + fz * (l.headZ() - 0.1f), s, 1f, 0.55f, 0.05f, 1f);
				billboard(q, n, ox + fx * (l.tailZ() + 0.1f), ty, oz + fz * (l.tailZ() + 0.1f), s, 1f, 0.55f, 0.05f, 1f);
			}
		}
		if ((flags & HEAD) != 0 && night && dist < 80) {
			// The pool of light on the road ahead (just above the ground under the vehicle).
			float c = l.headZ() + (l.big() ? 6.5f : 5.0f);
			ground(q, n, fx * c, 0.04f, fz * c, fx, fz, rx, rz, l.big() ? 3.4f : 2.8f, l.big() ? 6.5f : 5.2f, 1f, 0.93f, 0.75f, 0.42f * dark);
		}
		if ((flags & BRAKE) != 0 && night && dist < 48) {
			float c = l.tailZ() - 1.4f;
			ground(q, n, fx * c, 0.05f, fz * c, fx, fz, rx, rz, 1.8f, 1.6f, 1f, 0.1f, 0.05f, 0.3f * dark);
		}
		if (n[0] == 0) {
			return;
		}
		int count = n[0];
		collector.submitCustomGeometry(poseStack, GLOW, (pose, vc) -> {
			for (int k = 0; k < count; k++) {
				int o = k * 24;
				int r = (int) (q[o + 20] * 255);
				int g = (int) (q[o + 21] * 255);
				int b = (int) (q[o + 22] * 255);
				int a = (int) (q[o + 23] * 255);
				for (int pass = 0; pass < 2; pass++) {
					for (int i = 0; i < 4; i++) {
						int v = o + (pass == 0 ? i : 3 - i) * 5;
						vc.addVertex(pose, q[v], q[v + 1], q[v + 2]).setColor(r, g, b, a).setUv(q[v + 3], q[v + 4])
								.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
					}
				}
			}
		});
	}

	private static void put(float[] q, int[] n, float[] corners, float r, float g, float b, float a) {
		if ((n[0] + 1) * 24 > q.length) {
			return;
		}
		int o = n[0] * 24;
		System.arraycopy(corners, 0, q, o, 20);
		q[o + 20] = r;
		q[o + 21] = g;
		q[o + 22] = b;
		q[o + 23] = Mth.clamp(a, 0, 1);
		n[0]++;
	}

	private static final float[] C = new float[20];

	private static void corner(int i, float x, float y, float z, float u, float v) {
		C[i * 5] = x;
		C[i * 5 + 1] = y;
		C[i * 5 + 2] = z;
		C[i * 5 + 3] = u;
		C[i * 5 + 4] = v;
	}

	/** A glowing dot facing the camera. */
	private static void billboard(float[] q, int[] n, float x, float y, float z, float s, float r, float g, float b, float a) {
		float ax = CAM_RIGHT.x * s;
		float ay = CAM_RIGHT.y * s;
		float az = CAM_RIGHT.z * s;
		float bx = CAM_UP.x * s;
		float by = CAM_UP.y * s;
		float bz = CAM_UP.z * s;
		corner(0, x + ax - bx, y + ay - by, z + az - bz, 1, 1);
		corner(1, x + ax + bx, y + ay + by, z + az + bz, 1, 0);
		corner(2, x - ax + bx, y - ay + by, z - az + bz, 0, 0);
		corner(3, x - ax - bx, y - ay - by, z - az - bz, 0, 1);
		put(q, n, C, r, g, b, a);
	}

	/**
	 * A headlight's beam from (x, y, z) along (fx, fz): two crossed quads widening ahead, textured with the soft dot's
	 * lower half so that they fade from the lamp to the far end and to the edges.
	 */
	private static void beam(float[] q, int[] n, float x, float y, float z, float fx, float fz, float rx, float rz, float len, float a) {
		float near = 0.12f;
		float far = len * 0.32f;
		float ex = x + fx * len;
		float ez = z + fz * len;
		// Aimed a little down: the far end lower (the beams light the road, not the sky).
		float ey = Math.max(0.15f, y - len * 0.07f);
		corner(0, x - rx * near, y, z - rz * near, 0.42f, 0.5f);
		corner(1, x + rx * near, y, z + rz * near, 0.58f, 0.5f);
		corner(2, ex + rx * far, ey, ez + rz * far, 1f, 1f);
		corner(3, ex - rx * far, ey, ez - rz * far, 0f, 1f);
		put(q, n, C, 1f, 0.95f, 0.82f, a);
		corner(0, x, y - near, z, 0.42f, 0.5f);
		corner(1, x, y + near, z, 0.58f, 0.5f);
		corner(2, ex, ey + far * 0.55f, ez, 1f, 1f);
		corner(3, ex, ey - far * 0.3f, ez, 0f, 1f);
		put(q, n, C, 1f, 0.95f, 0.82f, a * 0.8f);
	}

	/** A soft patch of light lying flat (centre, along the heading half-length {@code hl}, across half-width {@code hw}). */
	private static void ground(float[] q, int[] n, float x, float y, float z, float fx, float fz, float rx, float rz, float hw, float hl,
			float r, float g, float b, float a) {
		corner(0, x + rx * hw + fx * hl, y, z + rz * hw + fz * hl, 1, 1);
		corner(1, x + rx * hw - fx * hl, y, z + rz * hw - fz * hl, 1, 0);
		corner(2, x - rx * hw - fx * hl, y, z - rz * hw - fz * hl, 0, 0);
		corner(3, x - rx * hw + fx * hl, y, z - rz * hw + fz * hl, 0, 1);
		put(q, n, C, r, g, b, a);
	}
}
