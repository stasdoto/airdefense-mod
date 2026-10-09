package com.stasdoto.airdefense.client.gear;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.registry.ModItems;

/**
 * 1.27: looking through the hand-held thermal imager: the white-hot picture (the same screen effect as a vehicle's
 * thermal sight), 2.5x or 6x (Z), a round eyepiece with a reticle, the range to what you look at.
 */
public final class MonocularView {
	private static final float[] ZOOMS = {0.4f, 1f / 6f};
	private static final int DARK = 0xFF060707;
	private static final int RETICLE = 0xFFB8FFB8;
	private static int zoom;
	/** For the automated test: ticks spent looking through it. */
	public static int ticksOn;

	private MonocularView() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (active()) {
				ticksOn++;
			}
		});
		HudElementRegistry.addLast(AirDefense.id("thermal_monocular"), MonocularView::hud);
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		return p != null && p.isUsingItem() && p.getUseItem().is(ModItems.THERMAL_MONOCULAR) && mc.options.getCameraType().isFirstPerson();
	}

	public static void cycleZoom() {
		zoom = (zoom + 1) % ZOOMS.length;
	}

	public static float fovMultiplier() {
		return active() ? ZOOMS[zoom] : 1f;
	}

	private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!active()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		int cy = h / 2;
		int r = (int) (Math.min(w, h) * 0.47);
		g.fill(0, 0, w, Math.max(0, cy - r), DARK);
		g.fill(0, Math.min(h, cy + r), w, h, DARK);
		for (int y = Math.max(0, cy - r); y < Math.min(h, cy + r); y++) {
			int dy = y - cy;
			int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
			g.fill(0, y, cx - dx, y + 1, DARK);
			g.fill(cx + dx, y, w, y + 1, DARK);
			g.fill(cx - dx, y, cx - dx + 4, y + 1, 0xA0060707);
			g.fill(cx + dx - 4, y, cx + dx, y + 1, 0xA0060707);
		}
		// A thin cross with a gap and a small box in the middle.
		g.fill(cx - r + 10, cy, cx - 8, cy + 1, RETICLE);
		g.fill(cx + 8, cy, cx + r - 10, cy + 1, RETICLE);
		g.fill(cx, cy - r + 10, cx + 1, cy - 8, RETICLE);
		g.fill(cx, cy + 8, cx + 1, cy + r - 10, RETICLE);
		g.fill(cx - 3, cy - 3, cx + 4, cy - 2, RETICLE);
		g.fill(cx - 3, cy + 3, cx + 4, cy + 4, RETICLE);
		g.fill(cx - 3, cy - 3, cx - 2, cy + 4, RETICLE);
		g.fill(cx + 3, cy - 3, cx + 4, cy + 4, RETICLE);
		// The range to what is under the cross (a laser rangefinder, up to 1.5 km).
		LocalPlayer p = mc.player;
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(1500));
		HitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
		String range = hit.getType() == HitResult.Type.MISS ? "----" : Integer.toString((int) Math.round(hit.getLocation().distanceTo(eye)));
		String mag = zoom == 0 ? "2.5×" : "6×";
		g.text(font, Component.translatable("hud.airdefense.monocular.line", mag, range), cx - r / 2, cy + r - 28, RETICLE, false);
		g.text(font, Component.translatable("hud.airdefense.monocular.keys", com.stasdoto.airdefense.client.vehicle.GunnerSight.ZOOM.getTranslatedKeyMessage()),
				cx - r / 2, cy + r - 16, 0xFF8FB88F, false);
	}
}
