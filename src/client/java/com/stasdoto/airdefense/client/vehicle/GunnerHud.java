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
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.defense.DefenseType;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * What the crew of an air defence vehicle sees on screen: a radar scope (every seat, every mode) and, for the gunner in
 * manual mode, the sight: boxes on the threats, the lock for missile batteries, Gepard's lead marker and where its
 * barrels point right now.
 */
public final class GunnerHud {
	private static final int C_THREAT = 0xFFFF4A3A;
	private static final int C_LOCK = 0xFF5CFF7A;
	private static final int C_LEAD = 0xFFFFD24A;

	private GunnerHud() {
	}

	/** Manual gunner of an air defence vehicle that is set up to fire. */
	static boolean isManualGunner(LocalPlayer player, VehicleEntity v) {
		return v.getVehicleType().isDefense() && v.getMode() == VehicleEntity.MODE_MANUAL && v.getGunner() == player;
	}

	static void draw(GuiGraphicsExtractor g, DeltaTracker delta, VehicleEntity v, LocalPlayer player) {
		VehicleType type = v.getVehicleType();
		if (type.defense == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float pt = delta.getGameTimeDeltaPartialTick(true);
		radar(g, mc, v, type.defense, pt);
		if (isManualGunner(player, v)) {
			sight(g, mc, v, type.defense, player, pt);
		}
	}

	// --- radar scope, top right: the vehicle's nose is up ---

	private static void radar(GuiGraphicsExtractor g, Minecraft mc, VehicleEntity v, DefenseType type, float pt) {
		int r = 36;
		int cx = g.guiWidth() - r - 8;
		int cy = r + 8;
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.sqrt(r * r - dy * dy);
			g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xA0081410);
		}
		ring(g, cx, cy, r, 0xFF2E8A50);
		ring(g, cx, cy, r / 2, 0x802E8A50);
		g.fill(cx - r, cy, cx + r + 1, cy + 1, 0x402E8A50);
		g.fill(cx, cy - r, cx + 1, cy + r + 1, 0x402E8A50);
		// The sweep.
		double sweep = (System.currentTimeMillis() % 2400) / 2400.0 * Mth.TWO_PI;
		for (int i = 0; i < r; i++) {
			g.fill(cx + (int) (Math.sin(sweep) * i), cy - (int) (Math.cos(sweep) * i), cx + (int) (Math.sin(sweep) * i) + 1,
					cy - (int) (Math.cos(sweep) * i) + 1, 0x9060FF90);
		}
		Vec3 f = v.forward();
		Vec3 right = v.right();
		double range = type.range;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof MissileEntity m)) {
				continue;
			}
			double rx = Mth.lerp(pt, m.xo, m.getX()) - v.getX();
			double rz = Mth.lerp(pt, m.zo, m.getZ()) - v.getZ();
			double side = rx * right.x + rz * right.z;
			double ahead = rx * f.x + rz * f.z;
			double d = Math.sqrt(side * side + ahead * ahead);
			if (d > range * 1.3) {
				continue;
			}
			double k = r / range;
			int bx = cx + (int) Math.round(side * k);
			int by = cy - (int) Math.round(ahead * k);
			if (Mth.square(bx - cx) + Mth.square(by - cy) > r * r) {
				continue;
			}
			boolean threat = m.getMissileType().threat;
			int c = threat ? C_THREAT : 0xFFF0F0F0;
			g.fill(bx - (threat ? 1 : 0), by - (threat ? 1 : 0), bx + (threat ? 2 : 1), by + (threat ? 2 : 1), c);
		}
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
		small(g, mc.font, (int) range + " " + Component.translatable("screen.airdefense.map.blocks").getString(), cx - r, cy + r + 3, 0xFF7CD69A);
	}

	// --- manual sight ---

	private static void sight(GuiGraphicsExtractor g, Minecraft mc, VehicleEntity v, DefenseType type, LocalPlayer player, float pt) {
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		int cy = h / 2;
		// Reticle.
		ring(g, cx, cy, 10, 0xC0FFFFFF);
		g.fill(cx - 22, cy, cx - 13, cy + 1, 0xC0FFFFFF);
		g.fill(cx + 14, cy, cx + 23, cy + 1, 0xC0FFFFFF);
		g.fill(cx, cy - 22, cx + 1, cy - 13, 0xC0FFFFFF);
		g.fill(cx, cy + 14, cx + 1, cy + 23, 0xC0FFFFFF);
		Camera cam = mc.gameRenderer.mainCamera();
		boolean gun = type.interceptor == null;
		Vec3 eye = player.getEyePosition(pt);
		Vec3 look = player.getViewVector(pt);
		MissileEntity lock = null;
		double lockCos = Math.cos(Math.toRadians(8));
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof MissileEntity m) || !m.getMissileType().threat) {
				continue;
			}
			Vec3 p = m.getPosition(pt);
			double d = p.distanceTo(eye);
			if (d > type.range * 1.2) {
				continue;
			}
			float[] s = project(mc, cam, p, w, h);
			if (s != null) {
				bracket(g, (int) s[0], (int) s[1], 5, d <= type.range ? C_THREAT : 0x80FF4A3A);
				small(g, font, (int) d + "", (int) s[0] + 7, (int) s[1] - 3, C_THREAT);
			}
			double c = p.subtract(eye).normalize().dot(look);
			if (d <= type.range && c > lockCos) {
				lockCos = c;
				lock = m;
			}
		}
		Component line;
		if (gun) {
			// Where the barrels point now (they lag behind the sight a little) and where to aim to hit.
			Vec3 muzzle = v.railWorld(0);
			float[] barrels = project(mc, cam, muzzle.add(v.railDirection(0).scale(60)), w, h);
			if (barrels != null) {
				ring(g, (int) barrels[0], (int) barrels[1], 3, 0xC0A0E0FF);
			}
			if (lock != null) {
				Vec3 p = lock.getPosition(pt);
				Vec3 vel = lock.position().subtract(lock.xo, lock.yo, lock.zo);
				Vec3 aim = p;
				for (int i = 0; i < 3; i++) {
					aim = p.add(vel.scale(aim.distanceTo(muzzle) / VehicleEntity.SHELL_SPEED));
				}
				float[] s = project(mc, cam, aim, w, h);
				if (s != null) {
					diamond(g, (int) s[0], (int) s[1], 5, C_LEAD);
				}
			}
			line = Component.translatable("hud.airdefense.sight.gun");
		} else {
			if (lock != null) {
				float[] s = project(mc, cam, lock.getPosition(pt), w, h);
				if (s != null) {
					bracket(g, (int) s[0], (int) s[1], 9, C_LOCK);
				}
			}
			line = Component.translatable(lock != null ? "hud.airdefense.sight.locked" : "hud.airdefense.sight.search");
		}
		int lw = font.width(line);
		g.fill(cx - lw / 2 - 3, cy + 30, cx + lw / 2 + 3, cy + 42, 0x90000000);
		g.text(font, line, cx - lw / 2, cy + 32, lock != null ? C_LOCK : 0xFFE0E0E0);
	}

	// --- helpers ---

	/** World point to GUI coordinates through the current camera, or null when it is behind or far off screen. */
	@Nullable
	static float[] project(Minecraft mc, Camera cam, Vec3 world, int w, int h) {
		Vec3 rel = world.subtract(cam.position());
		Vector3fc f = cam.forwardVector();
		Vector3fc u = cam.upVector();
		Vector3fc l = cam.leftVector();
		double z = rel.x * f.x() + rel.y * f.y() + rel.z * f.z();
		if (z < 0.2) {
			return null;
		}
		double x = -(rel.x * l.x() + rel.y * l.y() + rel.z * l.z());
		double y = rel.x * u.x() + rel.y * u.y() + rel.z * u.z();
		double t = Math.tan(Math.toRadians(cam.getFov()) / 2);
		double aspect = (double) w / h;
		double nx = x / (z * t * aspect);
		double ny = y / (z * t);
		if (Math.abs(nx) > 1.1 || Math.abs(ny) > 1.1) {
			return null;
		}
		return new float[]{(float) ((nx + 1) / 2 * w), (float) ((1 - ny) / 2 * h)};
	}

	private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		int n = Math.max(16, (int) (r * 6.3));
		for (int i = 0; i < n; i++) {
			double a = Mth.TWO_PI * i / n;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void bracket(GuiGraphicsExtractor g, int x, int y, int s, int c) {
		int k = Math.max(2, s / 2);
		g.fill(x - s, y - s, x - s + k, y - s + 1, c);
		g.fill(x - s, y - s, x - s + 1, y - s + k, c);
		g.fill(x + s - k + 1, y - s, x + s + 1, y - s + 1, c);
		g.fill(x + s, y - s, x + s + 1, y - s + k, c);
		g.fill(x - s, y + s, x - s + k, y + s + 1, c);
		g.fill(x - s, y + s - k + 1, x - s + 1, y + s + 1, c);
		g.fill(x + s - k + 1, y + s, x + s + 1, y + s + 1, c);
		g.fill(x + s, y + s - k + 1, x + s + 1, y + s + 1, c);
	}

	private static void diamond(GuiGraphicsExtractor g, int x, int y, int s, int c) {
		for (int i = 0; i <= s; i++) {
			g.fill(x - s + i, y - i, x - s + i + 1, y - i + 1, c);
			g.fill(x + s - i, y - i, x + s - i + 1, y - i + 1, c);
			g.fill(x - s + i, y + i, x - s + i + 1, y + i + 1, c);
			g.fill(x + s - i, y + i, x + s - i + 1, y + i + 1, c);
		}
	}

	private static void small(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}
}
