package com.stasdoto.airdefense.client.vehicle;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.vehicle.VehicleActionPayload;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/** Client side of the vehicles: renderers, the crew's keys, passing the driver's keys to the vehicle, and the HUD. */
public final class VehicleClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(AirDefense.id("vehicles"));
	public static final KeyMapping DEPLOY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.airdefense.deploy", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY));
	public static final KeyMapping MODE = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.airdefense.mode", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY));
	public static final KeyMapping SEAT = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.airdefense.seat", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY));

	private VehicleClient() {
	}

	public static void init() {
		for (VehicleType type : VehicleType.values()) {
			EntityRenderers.register(ModEntities.vehicle(type), ctx -> new VehicleRenderer(ctx, type));
		}
		VehicleEntity.stowRequester = v -> send(v, VehicleEntity.ACTION_STOW_FOR_MARCH);
		ClientTickEvents.START_CLIENT_TICK.register(VehicleClient::tick);
		HudElementRegistry.addLast(AirDefense.id("vehicle_hud"), VehicleClient::hud);
	}

	private static void send(VehicleEntity v, int action) {
		if (ClientPlayNetworking.canSend(VehicleActionPayload.TYPE)) {
			ClientPlayNetworking.send(new VehicleActionPayload(v.getId(), action));
		}
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || !(player.getVehicle() instanceof VehicleEntity v)) {
			// Drop presses made outside a vehicle so they don't fire later.
			while (DEPLOY.consumeClick()) {
			}
			while (MODE.consumeClick()) {
			}
			while (SEAT.consumeClick()) {
			}
			return;
		}
		v.setClientInput(v.isDriver(player) ? player.input.keyPresses : Input.EMPTY);
		while (DEPLOY.consumeClick()) {
			send(v, VehicleEntity.ACTION_DEPLOY);
		}
		while (MODE.consumeClick()) {
			send(v, VehicleEntity.ACTION_MODE);
		}
		while (SEAT.consumeClick()) {
			send(v, VehicleEntity.ACTION_SEAT);
		}
	}

	private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !(player.getVehicle() instanceof VehicleEntity v) || mc.options.hideGui) {
			return;
		}
		Font font = mc.font;
		VehicleType type = v.getVehicleType();
		boolean driver = v.isDriver(player);
		double perTick = driver ? Math.abs(v.getSpeed()) : Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
		int kmh = (int) Math.round(perTick * 20 * 3.6);
		int hp = (int) Math.ceil(v.getHealth() / v.getMaxHealth() * 100);
		Component l1 = Component.translatable("hud.airdefense.vehicle.line1", v.getType().getDescription(), hp, kmh);
		Component l2;
		if (type.isLauncher()) {
			l2 = Component.translatable(v.getState() == VehicleEntity.DEPLOYED ? "hud.airdefense.vehicle.deployed" : "hud.airdefense.vehicle.stowed",
					Integer.bitCount(v.getLoadedMask()), type.rails());
		} else {
			l2 = Component.translatable(v.getMode() == VehicleEntity.MODE_AUTO ? "hud.airdefense.vehicle.ad_auto" : "hud.airdefense.vehicle.ad_off",
					v.getAmmo(), type.magazine());
		}
		Component l3 = Component.translatable(driver ? "hud.airdefense.vehicle.keys_driver" : "hud.airdefense.vehicle.keys_gunner",
				DEPLOY.getTranslatedKeyMessage(), MODE.getTranslatedKeyMessage(), SEAT.getTranslatedKeyMessage());
		int x = 6;
		int y = g.guiHeight() - 64;
		int w = Math.max(font.width(l1), Math.max(font.width(l2), font.width(l3))) + 8;
		g.fill(x - 3, y - 3, x + w, y + 32, 0x88000000);
		g.text(font, l1, x, y, 0xFFFFE08A);
		g.text(font, l2, x, y + 10, 0xFFE0E0E0);
		g.text(font, l3, x, y + 20, 0xFFA0A0A0);
	}
}
