package com.stasdoto.airdefense.client.vehicle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.stasdoto.airdefense.client.ui.Ui;
import com.stasdoto.airdefense.vehicle.DriveSpec;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * 2.0: the driver's instruments for a ground vehicle, bottom right: a rev counter (needle, red line), the speed in
 * km/h, the gear (R, N, 1..n), the fuel gauge and the lamps - handbrake, headlights, cruise control.
 */
public final class DriveHud {
	private static final int W = 132;
	private static final int H = 58;
	private static final int NEEDLE = 0xFFFF6A3D;
	private static final int TICK = 0xC0F0EEEA;
	private static final int RED = 0xFFE34A3C;
	/** Shown speed, eased so the digits don't flicker. */
	private static float shownKmh;

	private DriveHud() {
	}

	static void draw(GuiGraphicsExtractor g, VehicleEntity v, float pt) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		DriveSpec spec = v.driveSpec();
		int x1 = g.guiWidth() - 6;
		int y1 = g.guiHeight() - 6;
		int x0 = x1 - W;
		int y0 = y1 - H;
		Ui.round(g, x0, y0, x1, y1, 4, 0xB0101114);
		g.fill(x0, y1 - 1, x1, y1, Ui.ACCENT_LO);

		// Rev counter: a 240-degree dial on the left.
		int cx = x0 + 28;
		int cy = y0 + 30;
		int r = 22;
		float rpm = v.getEngineRpm();
		float maxRpm = (float) (spec.redRpm * 1.1);
		int steps = 22;
		for (int i = 0; i <= steps; i++) {
			float f = i / (float) steps;
			double a = Math.toRadians(150 + 240 * f);
			boolean red = f * maxRpm >= spec.redRpm;
			boolean major = i % 2 == 0;
			int rr = major ? r - 4 : r - 2;
			for (int k = rr; k <= r; k++) {
				int px = cx + (int) Math.round(Math.cos(a) * k);
				int py = cy + (int) Math.round(Math.sin(a) * k);
				g.fill(px, py, px + 1, py + 1, red ? RED : TICK);
			}
		}
		float share = Mth.clamp(rpm / maxRpm, 0, 1);
		double na = Math.toRadians(150 + 240 * share);
		for (int k = 0; k <= r - 3; k++) {
			int px = cx + (int) Math.round(Math.cos(na) * k);
			int py = cy + (int) Math.round(Math.sin(na) * k);
			g.fill(px, py, px + 1, py + 1, NEEDLE);
		}
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, Ui.TEXT);
		String rpmText = Integer.toString(Math.round(rpm / 100f) * 100);
		g.centeredText(font, rpmText, cx, cy + 9, rpm >= spec.redRpm * 0.97 ? RED : Ui.DIM);
		g.centeredText(font, "rpm", cx, cy + 18, Ui.FAINT);

		// Speed, big, in the middle.
		double perTick = v.simulatedHere() ? Math.hypot(v.driveState().vx, v.driveState().vz) : Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
		float kmh = (float) (perTick * 72);
		shownKmh += (kmh - shownKmh) * 0.35f;
		int sx = x0 + 82;
		Ui.big(g, font, Component.literal(Integer.toString(Math.round(shownKmh))), sx - font.width(Integer.toString(Math.round(shownKmh))), y0 + 8, 2f, Ui.TEXT);
		g.centeredText(font, Component.translatable("hud.airdefense.drive.kmh"), sx, y0 + 28, Ui.FAINT);

		// Gear.
		int gear = v.getGear();
		String gearText = gear < 0 ? "R" : gear == 0 ? "N" : Integer.toString(gear);
		int gx = x1 - 22;
		int gy = y0 + 7;
		Ui.round(g, gx, gy, gx + 16, gy + 18, 2, gear < 0 ? 0xC0603020 : 0xC0303238);
		Ui.big(g, font, Component.literal(gearText), gx + 8 - font.width(gearText), gy + 2, 2f, gear < 0 ? Ui.GOLD : Ui.TEXT);

		// Lamps.
		int ly = y0 + 40;
		lamp(g, font, "P", x0 + 58, ly, v.isHandbrake(), RED);
		lamp(g, font, "☀", x0 + 72, ly, v.lightsOn(), Ui.OK);
		lamp(g, font, "C", x0 + 86, ly, v.cruiseOn(), Ui.BLUE);
		lamp(g, font, "!", x0 + 100, ly, v.getSkid() > 0.35f, Ui.GOLD);

		// Fuel.
		if (!v.infiniteFuel()) {
			float fuel = v.getFuel() / Math.max(1, v.getVehicleType().fuelCapacity());
			int fx0 = x1 - 22;
			int fy0 = y0 + 30;
			int fy1 = y1 - 6;
			g.fill(fx0 + 5, fy0, fx0 + 11, fy1, 0x80000000);
			int fh = Math.round((fy1 - fy0) * Mth.clamp(fuel, 0, 1));
			g.fill(fx0 + 5, fy1 - fh, fx0 + 11, fy1, fuel < 0.15f ? RED : Ui.GOLD);
			g.text(font, "F", fx0 - 2, fy0, Ui.FAINT, false);
		}
	}

	private static void lamp(GuiGraphicsExtractor g, Font font, String s, int x, int y, boolean on, int color) {
		Ui.round(g, x, y, x + 12, y + 11, 2, on ? (color & 0x00FFFFFF) | 0x50000000 : 0x40000000);
		g.centeredText(font, s, x + 6, y + 2, on ? color : 0xFF4A4844);
	}
}
