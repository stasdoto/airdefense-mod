package com.stasdoto.airdefense.client.vehicle;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
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
			new KeyMapping("key.airdefense.deploy", InputConstants.KEY_R, CATEGORY));
	public static final KeyMapping MODE = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.airdefense.mode", InputConstants.KEY_V, CATEGORY));
	public static final KeyMapping SEAT = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.airdefense.seat", InputConstants.KEY_G, CATEGORY));

	private VehicleClient() {
	}

	public static void init() {
		GunnerSight.init();
		for (VehicleType type : VehicleType.values()) {
			EntityRenderers.register(ModEntities.vehicle(type), ctx -> new VehicleRenderer(ctx, type));
		}
		VehicleEntity.stowRequester = v -> send(v, VehicleEntity.ACTION_STOW_FOR_MARCH);
		ClientTickEvents.START_CLIENT_TICK.register(VehicleClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(VehicleSounds::tick);
		HudElementRegistry.addLast(AirDefense.id("vehicle_hud"), VehicleClient::hud);
		// Vanilla draws the mount's hearts over the hotbar; a vehicle has armour (shown in our panel), not hearts.
		HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, original -> (g, delta) -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null || !(player.getVehicle() instanceof VehicleEntity)) {
				original.extractRenderState(g, delta);
			}
		});
	}

	private static void send(VehicleEntity v, int action) {
		if (ClientPlayNetworking.canSend(VehicleActionPayload.TYPE)) {
			ClientPlayNetworking.send(new VehicleActionPayload(v.getId(), action));
		}
	}

	private static void tick(Minecraft mc) {
		GunnerSight.tick(mc);
		RotorWash.tick(mc);
		LocalPlayer player = mc.player;
		if (player != null) {
			// On foot with a gun in hand, R reloads (it is the same key as the launcher's).
			com.stasdoto.airdefense.client.weapon.GunClient.tick(mc);
		}
		if (player == null || !(player.getVehicle() instanceof VehicleEntity v)) {
			// Drop presses made outside a vehicle so they don't fire later.
			while (DEPLOY.consumeClick()) {
			}
			while (MODE.consumeClick()) {
			}
			while (SEAT.consumeClick()) {
			}
			while (GunnerSight.SMOKE.consumeClick()) {
			}
			return;
		}
		v.setClientInput(v.isDriver(player) ? player.input.keyPresses : Input.EMPTY);
		VehicleType vt = v.getVehicleType();
		boolean armedShooter = vt.isAir() ? v.isDriver(player) : vt.isArmed() && v.shooter() == player;
		if (GunnerHud.isManualGunner(player, v) || armedShooter) {
			// Manual mode: the left button is the trigger (and must not hit the vehicle or break blocks).
			boolean click = false;
			while (mc.options.keyAttack.consumeClick()) {
				click = true;
			}
			mc.options.keyAttack.setDown(false);
			if (mc.gui.screen() == null && (click || mc.mouseHandler.isLeftPressed()) && v.getState() == VehicleEntity.DEPLOYED) {
				send(v, VehicleEntity.ACTION_FIRE);
			}
		}
		while (DEPLOY.consumeClick()) {
			send(v, VehicleEntity.ACTION_DEPLOY);
		}
		while (MODE.consumeClick()) {
			send(v, VehicleEntity.ACTION_MODE);
		}
		while (SEAT.consumeClick()) {
			send(v, VehicleEntity.ACTION_SEAT);
		}
		while (GunnerSight.SMOKE.consumeClick()) {
			send(v, VehicleEntity.ACTION_SMOKE);
		}
	}

	private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !(player.getVehicle() instanceof VehicleEntity v)) {
			return;
		}
		Font font = mc.font;
		VehicleType type = v.getVehicleType();
		boolean driver = v.isDriver(player);
		GunnerSight.Kind sight = GunnerSight.active();
		if (sight != GunnerSight.Kind.NONE) {
			// 1.26: the eyepiece of the gun sight or the periscope fills the screen.
			SightHud.draw(g, delta, v, player, sight);
			return;
		}
		double perTick = driver ? Math.abs(v.getSpeed()) : Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
		int kmh = (int) Math.round(perTick * 20 * 3.6);
		int hp = (int) Math.ceil(v.getHealth() / v.getMaxHealth() * 100);
		Component l1 = Component.translatable("hud.airdefense.vehicle.line1", v.getType().getDescription(), hp, kmh);
		Component l2;
		if (type.isAir()) {
			int ground = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, v.getBlockX(), v.getBlockZ());
			Component weapons = Component.translatable("hud.airdefense.vehicle.ordnance." + type.ordnance.name().toLowerCase(java.util.Locale.ROOT), v.getOrdnance());
			if (type.weapon != null) {
				weapons = Component.translatable("hud.airdefense.vehicle.gun_and", type.weapon.caliber, Math.max(0, v.getAmmo()), weapons);
			}
			l2 = Component.translatable(type.air == VehicleType.PLANE ? "hud.airdefense.vehicle.plane" : "hud.airdefense.vehicle.heli",
					Math.max(0, (int) (v.getY() - ground)), (int) (v.throttle * 100), weapons);
		} else if (type.isService()) {
			l2 = Component.translatable(v.beaconsOn() ? "hud.airdefense.vehicle.service_call" : "hud.airdefense.vehicle.service");
		} else if (type.isTruck()) {
			l2 = Component.translatable("hud.airdefense.vehicle.cargo_goods", Component.translatable("nation.airdefense.goods." + v.getCargoKind()),
					v.getCargo(), type.cargoCapacity, Component.translatable("hud.airdefense.truck." + v.truckModeKey()));
		} else if (type.isShip()) {
			// 1.33: the gun (never runs dry), the cruise missiles in the cells.
			l2 = Component.translatable("hud.airdefense.vehicle.ship", type.weapon.caliber,
					Component.translatable("item.airdefense." + type.launcher.missile.itemId), Math.max(0, v.getAmmo()), type.strikeLoad());
		} else if (type.isArmed()) {
			l2 = Component.translatable("hud.airdefense.vehicle.weapon", type.weapon.caliber, Math.max(0, v.getAmmo()));
		} else if (type.isJammer()) {
			l2 = Component.translatable(v.getMode() == VehicleEntity.MODE_OFF ? "hud.airdefense.vehicle.ew_off"
					: v.radarWorking() ? "hud.airdefense.vehicle.ew_on" : "hud.airdefense.vehicle.ew_deploying", (int) type.radar.range);
		} else if (type.isRadar()) {
			l2 = Component.translatable(v.getMode() == VehicleEntity.MODE_OFF ? "hud.airdefense.vehicle.radar_off"
					: v.radarWorking() ? "hud.airdefense.vehicle.radar_on" : "hud.airdefense.vehicle.radar_deploying", (int) type.radar.range);
		} else if (type.isArtillery()) {
			l2 = Component.translatable(v.getMode() == VehicleEntity.MODE_AUTO ? "hud.airdefense.vehicle.arty_cb" : "hud.airdefense.vehicle.arty",
					Math.max(0, v.getAmmo()), type.strikeLoad());
		} else if (type.isLauncher()) {
			l2 = Component.translatable(v.getState() == VehicleEntity.DEPLOYED ? "hud.airdefense.vehicle.deployed" : "hud.airdefense.vehicle.stowed",
					Integer.bitCount(v.getLoadedMask()), type.rails());
		} else {
			String mode = switch (v.getMode()) {
				case VehicleEntity.MODE_AUTO -> "hud.airdefense.vehicle.ad_auto";
				case VehicleEntity.MODE_MANUAL -> "hud.airdefense.vehicle.ad_manual";
				default -> "hud.airdefense.vehicle.ad_off";
			};
			l2 = Component.translatable(mode, v.getAmmo(), type.magazine());
		}
		if (!type.isRadar() && !type.isTruck() && !type.isService()) {
			l2 = Component.empty().append(l2).append(v.reserveText());
		}
		if (!v.infiniteFuel()) {
			l2 = Component.empty().append(l2).append(Component.translatable(v.outOfFuel() ? "hud.airdefense.vehicle.fuel_empty"
					: "hud.airdefense.vehicle.fuel", (int) v.getFuel(), type.fuelCapacity()));
		}
		String keys = type.isAir() ? (driver ? (type.air == VehicleType.PLANE ? "hud.airdefense.vehicle.keys_plane" : "hud.airdefense.vehicle.keys_heli")
				: "hud.airdefense.vehicle.keys_passenger")
				: type.isArmed() && v.shooter() == player ? (driver ? "hud.airdefense.vehicle.keys_armed_driver" : "hud.airdefense.vehicle.keys_armed")
				: type.isTruck() ? "hud.airdefense.vehicle.keys_truck"
				: driver ? "hud.airdefense.vehicle.keys_driver"
				: GunnerHud.isManualGunner(player, v) ? "hud.airdefense.vehicle.keys_manual" : "hud.airdefense.vehicle.keys_gunner";
		Component l3 = Component.translatable(keys, DEPLOY.getTranslatedKeyMessage(), MODE.getTranslatedKeyMessage(), SEAT.getTranslatedKeyMessage());
		// Top left corner: the bottom of the screen belongs to the chat, the hotbar and the hearts.
		int x = 6;
		int y = 6;
		int w = Math.max(font.width(l1), Math.max(font.width(l2), font.width(l3))) + 8;
		com.stasdoto.airdefense.client.ui.Ui.round(g, x - 4, y - 4, x + w + 1, y + 33, 3, 0xA0101114);
		g.fill(x - 4, y - 4, x - 2, y + 33, com.stasdoto.airdefense.client.ui.Ui.ACCENT);
		g.text(font, l1, x + 1, y, com.stasdoto.airdefense.client.ui.Ui.TEXT, false);
		g.text(font, l2, x + 1, y + 10, com.stasdoto.airdefense.client.ui.Ui.DIM, false);
		g.text(font, l3, x + 1, y + 20, com.stasdoto.airdefense.client.ui.Ui.FAINT, false);
		GunnerHud.draw(g, delta, v, player);
		if (type.isAir() && driver) {
			// 1.26: the pilot's head-up display (gun cross, flight path, ladder, tapes, bomb sight, warnings).
			FlightHud.draw(g, delta, v, player);
			return;
		}
		if (type.isArmed() && v.shooter() == player) {
			// Where the gun points: a ring in the middle of the screen.
			int cx = mc.getWindow().getGuiScaledWidth() / 2;
			int cy = mc.getWindow().getGuiScaledHeight() / 2;
			int c = 0xC0FFE08A;
			g.fill(cx - 6, cy, cx - 2, cy + 1, c);
			g.fill(cx + 3, cy, cx + 7, cy + 1, c);
			g.fill(cx, cy - 6, cx + 1, cy - 2, c);
			g.fill(cx, cy + 3, cx + 1, cy + 7, c);
		}
	}
}
