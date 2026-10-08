package com.stasdoto.airdefense.client.vehicle;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * The eyepiece (1.26): the gunner's sight - a round field of view in the dark, the aiming chevron with the lead and
 * range marks, the laser rangefinder's distance, the round in the gun and the rounds left, the magnification, the
 * thermal channel, a little hull-and-turret indicator and a marker where the gun itself points (it follows the sight
 * at the turret's speed); the driver's periscope - a wide slit in the dark.
 */
final class SightHud {
	private static final int DARK = 0xFF050607;
	private static final int RETICLE = 0xFFE8E4D8;
	private static final int RETICLE_THERMAL = 0xFF9CFF9C;
	private static final int TEXT = 0xFFD8D2C0;

	private SightHud() {
	}

	static void draw(GuiGraphicsExtractor g, DeltaTracker delta, VehicleEntity v, LocalPlayer p, GunnerSight.Kind kind) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		int cy = h / 2;
		float pt = delta.getGameTimeDeltaPartialTick(true);
		if (kind == GunnerSight.Kind.PERISCOPE) {
			periscope(g, font, v, w, h);
			return;
		}
		int r = (int) (Math.min(w, h) * 0.46);
		// The dark round the eyepiece, a soft rim.
		g.fill(0, 0, w, Math.max(0, cy - r), DARK);
		g.fill(0, Math.min(h, cy + r), w, h, DARK);
		for (int y = Math.max(0, cy - r); y < Math.min(h, cy + r); y++) {
			int dy = y - cy;
			int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
			g.fill(0, y, cx - dx, y + 1, DARK);
			g.fill(cx + dx, y, w, y + 1, DARK);
			g.fill(cx - dx, y, cx - dx + 3, y + 1, 0x90050607);
			g.fill(cx + dx - 3, y, cx + dx, y + 1, 0x90050607);
		}
		int c = GunnerSight.thermal ? RETICLE_THERMAL : RETICLE;
		// The aiming chevron.
		for (int i = 0; i <= 7; i++) {
			g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, c);
			g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
		}
		// The horizontal line with the lead marks, broken round the middle.
		g.fill(cx - r + 14, cy, cx - 16, cy + 1, c);
		g.fill(cx + 16, cy, cx + r - 14, cy + 1, c);
		for (int k = 1; k * 14 < r - 20; k++) {
			int len = k % 2 == 0 ? 5 : 3;
			g.fill(cx - 16 - k * 14, cy - len, cx - 15 - k * 14, cy, c);
			g.fill(cx + 15 + k * 14, cy - len, cx + 16 + k * 14, cy, c);
		}
		// The vertical line under the chevron with the range marks (hundreds of metres).
		g.fill(cx, cy + 12, cx + 1, cy + r - 20, c);
		for (int k = 1; k <= 6; k++) {
			int y = cy + 10 + k * 12;
			if (y > cy + r - 24) {
				break;
			}
			g.fill(cx - 4, y, cx + 5, y + 1, c);
			g.text(font, Integer.toString(k * 4), cx + 7, y - 4, c, false);
		}
		// Where the gun points now (it lags while the turret turns).
		Vec3 gunDir = v.getVehicleType().geometry.rails().length > 0 ? v.railDirection(0) : null;
		if (gunDir != null) {
			FlightHud.Proj proj = new FlightHud.Proj(mc.gameRenderer.mainCamera(), w, h);
			float[] gp = proj.dir(gunDir);
			if (gp != null && Math.hypot(gp[0] - cx, gp[1] - cy) < r - 8) {
				int x = Math.round(gp[0]);
				int y = Math.round(gp[1]);
				g.fill(x - 3, y - 3, x + 4, y - 2, c);
				g.fill(x - 3, y + 3, x + 4, y + 4, c);
				g.fill(x - 3, y - 3, x - 2, y + 4, c);
				g.fill(x + 3, y - 3, x + 4, y + 4, c);
			}
		}
		// Rangefinder.
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		Vec3 look = p.getViewVector(pt);
		BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(look.scale(2500)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		String range = hit.getType() == HitResult.Type.MISS ? "----" : String.format("%04d", (int) Math.round(hit.getLocation().distanceTo(eye)));
		int by = cy + r - 34;
		g.centeredText(font, Component.translatable("hud.airdefense.sight.range", range), cx, by, c);
		// Bottom-left of the eyepiece: the gun's state; bottom-right: magnification and channel.
		VehicleType t = v.getVehicleType();
		int ammo = Math.max(0, v.getAmmo());
		Component gun = Component.translatable(ammo > 0 ? "hud.airdefense.sight.ready" : "hud.airdefense.sight.reloading");
		int lx = cx - (int) (r * 0.62);
		int ly = cy + (int) (r * 0.55);
		g.text(font, Component.translatable("hud.airdefense.sight.gun", t.weapon != null ? t.weapon.caliber : "", ammo), lx, ly, c, false);
		g.text(font, gun, lx, ly + 10, ammo > 0 ? c : 0xFFFF8060, false);
		int rx = cx + (int) (r * 0.36);
		g.text(font, GunnerSight.zoomName(), rx, ly, c, false);
		g.text(font, Component.translatable(GunnerSight.thermal ? "hud.airdefense.sight.thermal" : "hud.airdefense.sight.day"), rx, ly + 10, c, false);
		// Hull and turret indicator (top left of the eyepiece): the hull points up, the line is the gun.
		int ix = cx - (int) (r * 0.6);
		int iy = cy - (int) (r * 0.55);
		g.fill(ix - 6, iy - 9, ix + 7, iy - 8, c);
		g.fill(ix - 6, iy + 9, ix + 7, iy + 10, c);
		g.fill(ix - 6, iy - 9, ix - 5, iy + 10, c);
		g.fill(ix + 6, iy - 9, ix + 7, iy + 10, c);
		double ta = Math.toRadians(Mth.rotLerp(pt, v.turretYawO, v.turretYaw));
		for (int i = 0; i < 14; i++) {
			int x = ix + (int) Math.round(Math.sin(ta) * i);
			int y = iy - (int) Math.round(Math.cos(ta) * i);
			g.fill(x, y, x + 1, y + 1, c);
		}
		g.fill(ix - 2, iy - 2, ix + 3, iy + 3, c);
		// Outside the eyepiece: the vehicle and the keys.
		int hp = (int) Math.ceil(v.getHealth() / v.getMaxHealth() * 100);
		int kmh = (int) Math.round(Math.hypot(v.getX() - v.xo, v.getZ() - v.zo) * 20 * 3.6);
		g.text(font, Component.translatable("hud.airdefense.sight.status", v.getType().getDescription(), hp, kmh, v.getReserve()), 8, 8, TEXT);
		g.text(font, Component.translatable("hud.airdefense.sight.keys", GunnerSight.VIEW.getTranslatedKeyMessage(),
				GunnerSight.ZOOM.getTranslatedKeyMessage(), GunnerSight.THERMAL.getTranslatedKeyMessage()), 8, h - 14, 0xFFA09A8A);
	}

	private static void periscope(GuiGraphicsExtractor g, Font font, VehicleEntity v, int w, int h) {
		// A wide, low slit of glass (the driver's prism block), the rest dark.
		int sw = (int) (w * 0.78);
		int sh = (int) (h * 0.26);
		int x0 = (w - sw) / 2;
		int y0 = (int) (h * 0.3);
		g.fill(0, 0, w, y0, DARK);
		g.fill(0, y0 + sh, w, h, DARK);
		g.fill(0, y0, x0, y0 + sh, DARK);
		g.fill(x0 + sw, y0, w, y0 + sh, DARK);
		// The block's frame and the two posts between three prisms.
		int f = 0xFF22262A;
		g.fill(x0, y0, x0 + sw, y0 + 3, f);
		g.fill(x0, y0 + sh - 3, x0 + sw, y0 + sh, f);
		g.fill(x0 + sw / 3 - 2, y0, x0 + sw / 3 + 2, y0 + sh, f);
		g.fill(x0 + 2 * sw / 3 - 2, y0, x0 + 2 * sw / 3 + 2, y0 + sh, f);
		int kmh = (int) Math.round(Math.hypot(v.getX() - v.xo, v.getZ() - v.zo) * 20 * 3.6);
		int hp = (int) Math.ceil(v.getHealth() / v.getMaxHealth() * 100);
		g.centeredText(font, Component.translatable("hud.airdefense.sight.driver", kmh, hp), w / 2, y0 + sh + 12, TEXT);
		g.text(font, Component.translatable("hud.airdefense.sight.driver_keys", GunnerSight.VIEW.getTranslatedKeyMessage()), 8, h - 14,
				0xFFA09A8A);
	}
}
