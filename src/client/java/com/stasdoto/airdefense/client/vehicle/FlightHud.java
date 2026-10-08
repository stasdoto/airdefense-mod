package com.stasdoto.airdefense.client.vehicle;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.vehicle.Ordnance;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * The pilot's head-up display (1.26): heading tape, speed and altitude boxes, the pitch ladder and the bank scale, the
 * gun cross where the nose points and the flight path marker where the aircraft is really going, the throttle, the
 * bomb's impact point (computed by dropping a bomb in thought), a box round an air target the missile could lock,
 * and the warnings: stall, ground. For helicopters also the climb rate and the rotor.
 */
public final class FlightHud {
	private static final int C = 0xE070FF8A;
	private static final int C_DIM = 0x9070FF8A;
	private static final int C_WARN = 0xF0FF5040;

	private FlightHud() {
	}

	static void draw(GuiGraphicsExtractor g, DeltaTracker delta, VehicleEntity v, LocalPlayer p) {
		Minecraft mc = Minecraft.getInstance();
		VehicleType t = v.getVehicleType();
		boolean plane = t.air == VehicleType.PLANE;
		float pt = delta.getGameTimeDeltaPartialTick(true);
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		int cy = h / 2;
		Camera cam = mc.gameRenderer.mainCamera();
		Proj proj = new Proj(cam, w, h);
		Vec3 pos = v.getPosition(pt);
		Vec3 vel = v.getDeltaMovement();
		double speed = Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
		double vspeed = v.getY() - v.yo;
		double air = Math.hypot(speed, vspeed);
		int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, v.getBlockX(), v.getBlockZ());
		double agl = v.getY() - ground;

		// Heading tape across the top.
		float heading = Mth.wrapDegrees(cam.yRot() + 180);
		int tapeY = 52;
		g.fill(cx - 90, tapeY + 9, cx + 91, tapeY + 10, C_DIM);
		for (int d = -40; d <= 40; d += 5) {
			float hdg = heading + d;
			int a = Math.floorMod(Math.round(hdg / 5) * 5, 360);
			int x = cx + Math.round((a - heading + (a - heading > 180 ? -360 : a - heading < -180 ? 360 : 0)) * 2.2f);
			if (x < cx - 90 || x > cx + 90) {
				continue;
			}
			boolean major = a % 30 == 0;
			g.fill(x, tapeY + (major ? 3 : 6), x + 1, tapeY + 9, C);
			if (major) {
				String label = switch (a) {
					case 0 -> "N";
					case 90 -> "E";
					case 180 -> "S";
					case 270 -> "W";
					default -> Integer.toString(a / 10);
				};
				g.centeredText(font, label, x, tapeY - 7, C);
			}
		}
		g.fill(cx, tapeY + 10, cx + 1, tapeY + 14, C);
		g.centeredText(font, String.format("%03d", Math.floorMod(Math.round(heading), 360)), cx, tapeY + 15, C);

		// Speed (left) and altitude (right) boxes.
		int kmh = (int) Math.round(air * 20 * 3.6);
		box(g, font, cx - 120, cy - 6, Integer.toString(kmh), Component.translatable("hud.airdefense.flight.kmh").getString(), true);
		box(g, font, cx + 84, cy - 6, Integer.toString((int) Math.round(v.getY())), Component.translatable("hud.airdefense.flight.m").getString(), false);
		g.text(font, Component.translatable("hud.airdefense.flight.agl", (int) Math.max(0, Math.round(agl))), cx + 84, cy + 9, C_DIM);
		int vs = (int) Math.round(vspeed * 20);
		g.text(font, (vs > 0 ? "+" : "") + vs + " " + Component.translatable("hud.airdefense.flight.ms").getString(), cx + 84, cy - 18, C_DIM);

		// Pitch ladder: lines every 10 degrees about the horizon, in the camera's view.
		for (int a = -60; a <= 60; a += 10) {
			Vec3 dir = Vec3.directionFromRotation(-a, cam.yRot());
			float[] sp = proj.dir(dir);
			if (sp == null || Math.abs(sp[1] - cy) > h * 0.36) {
				continue;
			}
			int y = Math.round(sp[1]);
			int half = a == 0 ? 70 : 28;
			int gap = a == 0 ? 18 : 12;
			int c = a == 0 ? C : C_DIM;
			if (a < 0) {
				// Below the horizon: dashed.
				for (int x = cx - half; x < cx - gap; x += 4) {
					g.fill(x, y, x + 2, y + 1, c);
				}
				for (int x = cx + gap; x < cx + half; x += 4) {
					g.fill(x, y, x + 2, y + 1, c);
				}
			} else {
				g.fill(cx - half, y, cx - gap, y + 1, c);
				g.fill(cx + gap, y, cx + half, y + 1, c);
			}
			if (a != 0) {
				g.text(font, Integer.toString(Math.abs(a)), cx + half + 3, y - 4, c);
			}
		}

		// Bank scale: an arc of ticks at the bottom and the pointer at the aircraft's bank.
		float bank = plane ? v.planeRoll : v.heliRoll;
		int by = h - 70;
		for (int a : new int[]{-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60}) {
			double r = Math.toRadians(a);
			int x = cx + (int) Math.round(Math.sin(r) * 50);
			int y = by + (int) Math.round(Math.cos(r) * 18);
			g.fill(x, y, x + 1, y + (a % 30 == 0 ? 5 : 3), C_DIM);
		}
		double br = Math.toRadians(Mth.clamp(bank, -70, 70));
		int bx = cx + (int) Math.round(Math.sin(br) * 50);
		int byy = by + (int) Math.round(Math.cos(br) * 18) - 4;
		g.fill(bx - 2, byy, bx + 3, byy + 2, C);
		g.fill(bx, byy - 2, bx + 1, byy, C);

		// The gun cross: where the nose (and the gun, the rockets) points.
		Vec3 nose = plane ? Vec3.directionFromRotation(v.getXRot(), v.getYRot()) : p.getLookAngle();
		float[] ns = proj.dir(nose);
		if (ns != null) {
			int x = Math.round(ns[0]);
			int y = Math.round(ns[1]);
			g.fill(x - 8, y, x - 2, y + 1, C);
			g.fill(x + 3, y, x + 9, y + 1, C);
			g.fill(x, y - 8, x + 1, y - 2, C);
			g.fill(x, y + 3, x + 1, y + 9, C);
		}
		// The flight path marker: a circle with wings where it is actually going.
		if (air > 0.15) {
			float[] fp = proj.dir(vel.normalize());
			if (fp != null) {
				int x = Math.round(fp[0]);
				int y = Math.round(fp[1]);
				ring(g, x, y, 4, C);
				g.fill(x - 11, y, x - 5, y + 1, C);
				g.fill(x + 6, y, x + 12, y + 1, C);
				g.fill(x, y - 8, x + 1, y - 5, C);
			}
		}
		// The aim: where the pilot looks (the plane turns there).
		if (plane) {
			ring(g, cx, cy, 9, C_DIM);
		}

		// Throttle / rotor (bottom left).
		float thr = plane ? v.throttle : v.engine;
		int tx = cx - 150;
		int ty = h - 110;
		g.fill(tx, ty, tx + 6, ty + 60, 0x50000000);
		int fill = Math.round(Mth.clamp(thr, 0, 1) * 58);
		g.fill(tx + 1, ty + 59 - fill, tx + 5, ty + 59, C);
		g.text(font, Component.translatable(plane ? "hud.airdefense.flight.throttle" : "hud.airdefense.flight.rotor", Math.round(thr * 100)),
				tx + 10, ty + 52, C);

		// Bombs: where one dropped now would land.
		if (t.ordnance == Ordnance.FAB250 && v.getOrdnance() > 0) {
			Vec3 hit = bombImpact(mc, pos.add(0, -0.3, 0), vel.add(0, -0.1, 0));
			if (hit != null) {
				float[] hp = proj.point(hit);
				if (hp != null) {
					int x = Math.round(hp[0]);
					int y = Math.round(hp[1]);
					ring(g, x, y, 6, C);
					g.fill(x, y, x + 1, y + 1, C);
					// The bomb fall line up to the flight path marker.
					float[] fp = air > 0.15 ? proj.dir(vel.normalize()) : null;
					if (fp != null) {
						int n = 12;
						for (int i = 1; i < n; i++) {
							int lx = Math.round(Mth.lerp(i / (float) n, fp[0], hp[0]));
							int ly = Math.round(Mth.lerp(i / (float) n, fp[1], hp[1]));
							g.fill(lx, ly, lx + 1, ly + 2, C_DIM);
						}
					}
					g.centeredText(font, Component.translatable("hud.airdefense.flight.ccip"), x, y + 9, C);
				}
			}
		}
		// Air-to-air missile: a box round the target it would lock.
		if (t.ordnance == Ordnance.AIM9) {
			MissileEntity target = airTarget(mc, p);
			if (target != null) {
				float[] tp = proj.point(target.getPosition(pt));
				if (tp != null) {
					int x = Math.round(tp[0]);
					int y = Math.round(tp[1]);
					frame(g, x - 8, y - 8, 17, 17, C);
					g.centeredText(font, Component.translatable("hud.airdefense.flight.lock"), x, y + 11, C);
				}
			}
		}

		// Warnings.
		Component warn = null;
		if (plane && !v.onGround() && air < t.maxSpeed * 0.32) {
			warn = Component.translatable("hud.airdefense.flight.stall");
		} else if (!v.onGround() && vspeed < -0.25 && agl / Math.max(0.01, -vspeed) < 60) {
			warn = Component.translatable("hud.airdefense.flight.pull_up");
		}
		if (warn != null && (System.currentTimeMillis() / 300) % 2 == 0) {
			g.centeredText(font, warn, cx, cy + 40, C_WARN);
		}
	}

	/** Where a bomb let go at {@code from} with {@code vel} lands (the server's own bomb physics, run ahead). */
	@Nullable
	static Vec3 bombImpact(Minecraft mc, Vec3 from, Vec3 vel) {
		Vec3 p = from;
		Vec3 v = vel;
		for (int i = 0; i < 600; i++) {
			v = v.add(0, -0.06, 0).scale(0.997);
			Vec3 n = p.add(v);
			int x = Mth.floor(n.x);
			int z = Mth.floor(n.z);
			if (!mc.level.hasChunk(x >> 4, z >> 4)) {
				return null;
			}
			int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (n.y <= top) {
				return new Vec3(n.x, top, n.z);
			}
			p = n;
		}
		return null;
	}

	/** The drone or missile nearest the middle of the pilot's view (within 15 degrees, 300 blocks) - as the server picks it. */
	@Nullable
	private static MissileEntity airTarget(Minecraft mc, LocalPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		MissileEntity best = null;
		double bestCos = Math.cos(Math.toRadians(15));
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof MissileEntity m) || !m.isAlive() || !m.getMissileType().threat) {
				continue;
			}
			Vec3 to = m.position().subtract(eye);
			double d = to.length();
			if (d < 5 || d > 300) {
				continue;
			}
			double c = to.scale(1 / d).dot(look);
			if (c > bestCos) {
				bestCos = c;
				best = m;
			}
		}
		return best;
	}

	private static void box(GuiGraphicsExtractor g, Font font, int x, int y, String value, String unit, boolean left) {
		int w = 36;
		g.fill(x, y, x + w, y + 12, 0x50000000);
		frame(g, x, y, w, 12, C);
		g.text(font, value, x + 3, y + 2, C);
		g.text(font, unit, left ? x - font.width(unit) - 3 : x + w + 3, y + 2, C_DIM);
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		g.fill(x, y, x + w, y + 1, c);
		g.fill(x, y + h - 1, x + w, y + h, c);
		g.fill(x, y, x + 1, y + h, c);
		g.fill(x + w - 1, y, x + w, y + h, c);
	}

	private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int c) {
		int n = Math.max(12, r * 4);
		for (int i = 0; i < n; i++) {
			double a = Mth.TWO_PI * i / n;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(x, y, x + 1, y + 1, c);
		}
	}

	/** World to screen through the camera (GUI pixels). */
	static final class Proj {
		private final Vec3 eye;
		private final Vec3 fwd;
		private final Vec3 up;
		private final Vec3 left;
		private final float focal;
		private final float cx;
		private final float cy;

		Proj(Camera cam, int w, int h) {
			eye = cam.position();
			fwd = vec(cam.forwardVector());
			up = vec(cam.upVector());
			left = vec(cam.leftVector());
			focal = (float) ((h / 2.0) / Math.tan(Math.toRadians(cam.getFov()) / 2));
			cx = w / 2f;
			cy = h / 2f;
		}

		private static Vec3 vec(Vector3fc v) {
			return new Vec3(v.x(), v.y(), v.z());
		}

		@Nullable
		float[] dir(Vec3 d) {
			double z = d.dot(fwd);
			if (z < 0.05) {
				return null;
			}
			return new float[]{(float) (cx - d.dot(left) / z * focal), (float) (cy - d.dot(up) / z * focal)};
		}

		@Nullable
		float[] point(Vec3 p) {
			return dir(p.subtract(eye));
		}
	}
}
