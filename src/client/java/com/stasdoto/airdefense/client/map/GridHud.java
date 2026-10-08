package com.stasdoto.airdefense.client.map;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.map.Grid;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/** Top right while holding the tablet or sitting in a vehicle: the grid square you are in and your coordinates. */
public final class GridHud {
	private GridHud() {
	}

	public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (com.stasdoto.airdefense.client.vehicle.GunnerSight.active() != com.stasdoto.airdefense.client.vehicle.GunnerSight.Kind.NONE) {
			// The gun sight's eyepiece fills the screen.
			return;
		}
		LocalPlayer p = mc.player;
		if (p == null || mc.gui.screen() != null || !(DesignatorItem.held(p) != null || p.getVehicle() instanceof VehicleEntity)) {
			return;
		}
		if (mc.getCameraEntity() != p) {
			return;
		}
		Font font = mc.font;
		boolean cyr = MapClient.cyrillic();
		String square = Grid.square(p.getX(), p.getZ(), cyr) + "  ·  " + Grid.snail(p.getX(), p.getZ());
		String coords = String.format(Locale.ROOT, "X %d  Y %d  Z %d", p.getBlockX(), p.getBlockY(), p.getBlockZ());
		int w = mc.getWindow().getGuiScaledWidth();
		int bw = Math.max(font.width(square) * 2, font.width(coords)) + 10;
		int x = w - bw - 4;
		// Below the vehicle panel when driving.
		int y = p.getVehicle() instanceof VehicleEntity ? 42 : 4;
		g.fill(x, y, x + bw, y + 32, 0x88000000);
		g.pose().pushMatrix();
		g.pose().translate(x + 5, y + 4);
		g.pose().scale(2f, 2f);
		g.text(font, square, 0, 0, 0xFFFFD24A);
		g.pose().popMatrix();
		g.text(font, coords, x + 5, y + 22, 0xFFE0E0E0);
	}
}
