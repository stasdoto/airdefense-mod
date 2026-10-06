package com.stasdoto.airdefense.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.item.DesignatorItem;
import net.minecraft.world.item.ItemStack;
import com.stasdoto.airdefense.missile.MissileStats;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * Plays every system in a real client and records screenshots + counters: the vehicle line-up (folded and deployed),
 * driving, an unopposed Iskander strike, close-up explosions by day and night, Patriot vs Iskander, Gepard vs a Shahed
 * swarm, NASAMS vs a HIMARS salvo at night and vs drones, IRIS-T vs cruise missiles and vs drones. Each air defence
 * scene logs a RESULT line (shot down / reached the ground).
 */
@SuppressWarnings("UnstableApiUsage")
public class AirDefenseClientGameTest implements FabricClientGameTest {
	private int ground;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		System.setProperty("airdefense.debug", "true");
		ctx.runOnClient(mc -> mc.options.renderDistance().set(12));
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("time set 1000");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			AirDefense.LOGGER.info("[airdefense-test] ground level {}", ground);
			int books = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().countItem(net.minecraft.world.item.Items.WRITTEN_BOOK));
			AirDefense.LOGGER.info("[airdefense-test] RESULT guide: books given on joining {}", books);
			if (scene("guide")) {
				// The guide book, a couple of its pages.
				ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
						net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess.fromItem(com.stasdoto.airdefense.guide.Guide.book()))));
				ctx.waitTicks(10);
				ctx.takeScreenshot("000_guide_1");
				ctx.runOnClient(mc -> {
					if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) {
						b.setPage(6);
					}
				});
				ctx.waitTicks(5);
				ctx.takeScreenshot("000_guide_7");
				ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
				ctx.waitTicks(5);
			}

			// AIRDEFENSE_SCENES (from the workflow) picks scenes for a quick run; empty = everything.
			if (scene("lineup")) {
				lineup(ctx, server);
			}
			if (scene("radar")) {
				radar(ctx, server);
			}
			if (scene("drones")) {
				drones(ctx, server);
			}
			if (scene("ad")) {
				newAirDefense(ctx, server);
			}
			if (scene("armor")) {
				armor(ctx, server);
			}
			if (scene("air")) {
				aircraft(ctx, server);
			}
			if (scene("logistics")) {
				logistics(ctx, server);
			}
			if (scene("drive")) {
				drive(ctx, server);
			}
			if (scene("map")) {
				tabletMap(ctx, server);
			}
			if (scene("factory")) {
				factory(ctx, server);
			}
			if (scene("sounds")) {
				sounds(ctx, server);
			}
			if (scene("smallArms")) {
				smallArms(ctx, server);
			}
			if (scene("nations")) {
				nations(ctx, server);
			}
			if (scene("economy")) {
				economy(ctx, server);
			}
			if (scene("breeding")) {
				breeding(ctx, server);
			}
			if (scene("unrest")) {
				unrest(ctx, server);
			}
			if (scene("war")) {
				war(ctx, server);
			}
			if (scene("strike")) {
				unopposedIskander(ctx, server);
				effectsCloseup(ctx, server, false);
				server.runCommand("time set 14500");
				effectsCloseup(ctx, server, true);
				server.runCommand("time set 1000");
			}
			if (scene("defense")) {
				patriotVsIskander(ctx, server);
				droneSwarm(ctx, server);
				server.runCommand("time set 15000");
				himarsVsNasams(ctx, server);
				server.runCommand("time set 1000");
				droneVsNasams(ctx, server);
				cruiseVsIrisT(ctx, server);
				droneVsIrisT(ctx, server);
			}
			if (scene("manual")) {
				manualDefense(ctx, server, VehicleType.GEPARD, 16500, "manual_gepard");
				manualDefense(ctx, server, VehicleType.IRIS_T, 18000, "manual_iris_t");
			}

			AirDefense.LOGGER.info("[airdefense-test] SUMMARY {}", MissileStats.summary());
		}
	}

	private static boolean scene(String name) {
		String only = System.getenv("AIRDEFENSE_SCENES");
		return only == null || only.isBlank() || java.util.Arrays.asList(only.trim().split("\\s+")).contains(name);
	}

	// --- scenes ------------------------------------------------------------------------------------

	private void lineup(ClientGameTestContext ctx, TestServerContext server) {
		VehicleType[] all = VehicleType.values();
		List<Integer> ids = new ArrayList<>();
		server.runOnServer(s -> {
			for (int i = 0; i < all.length; i++) {
				VehicleEntity v = VehicleEntity.spawn(s.overworld(), all[i], new Vec3(-31.5 + i * 9, ground, 0.5), 200);
				ids.add(v.getId());
			}
		});
		camera(server, 0, ground + 10, 34, 180, 18);
		ctx.waitTicks(60);
		ctx.takeScreenshot("01_lineup");
		camera(server, -44, ground + 4, 18, -125, 8);
		ctx.waitTicks(15);
		ctx.takeScreenshot("02_lineup_side");
		// Raise every launcher; the air defence ones deploy by themselves once they stand still.
		server.runOnServer(s -> forVehicles(s.overworld(), ids, VehicleEntity::raiseLauncher));
		ctx.waitTicks(220);
		camera(server, 0, ground + 12, 36, 180, 20);
		ctx.waitTicks(10);
		ctx.takeScreenshot("03_lineup_deployed");
		camera(server, 44, ground + 6, 20, 125, 10);
		ctx.waitTicks(10);
		ctx.takeScreenshot("04_lineup_deployed_side");
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));
	}

	/**
	 * Stage R1: the six radar stations (folded, then switched on and turning), then a TRML-4D with an IRIS-T against
	 * a Shahed salvo, watched on the tablet's radar screen; the drones' engine glow at night.
	 */
	private void radar(ClientGameTestContext ctx, TestServerContext server) {
		int x = 21000;
		VehicleType[] radars = {VehicleType.P18, VehicleType.ST68, VehicleType.KUPOL, VehicleType.TRML4D, VehicleType.SENTINEL, VehicleType.MPQ65};
		camera(server, x, ground + 9, 34, 180, 14);
		ctx.waitTicks(40);
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < radars.length; i++) {
			ids.add(spawnVehicle(server, radars[i], x - 40 + i * 16, 0, 0));
		}
		ctx.waitTicks(8);
		ctx.takeScreenshot("70_radars_folded");
		ctx.waitTicks(140);
		ctx.takeScreenshot("71_radars_working");
		camera(server, x + 52, ground + 7, 22, 120, 12);
		ctx.waitTicks(15);
		ctx.takeScreenshot("72_radars_side");
		camera(server, x - 56, ground + 6, -20, -60, 8);
		ctx.waitTicks(15);
		ctx.takeScreenshot("73_radars_back");
		server.runOnServer(s -> {
			StringBuilder b = new StringBuilder();
			forVehicles(s.overworld(), ids, v -> b.append(v.getVehicleType().id).append('=').append(v.radarWorking()).append(' '));
			AirDefense.LOGGER.info("[airdefense-test] RESULT radars working: {} network {}", b,
					com.stasdoto.airdefense.radar.RadarNetwork.stations(s.overworld()).size());
		});
		// Keep only the TRML-4D (IRIS-T's own radar) for the fight.
		server.runOnServer(s -> forVehicles(s.overworld(), ids, v -> {
			if (v.getVehicleType() != VehicleType.TRML4D) {
				v.discard();
			}
		}));
		int fx = x;
		BlockPos target = new BlockPos(fx, ground - 1, 70);
		prepareDefense(ctx, server, VehicleType.IRIS_T, fx + 12, 50, 180);
		int[] before = counters();
		launchFrom(ctx, server, VehicleType.SHAHED, fx, -230, target);
		server.runCommand("gamemode creative @a");
		camera(server, fx + 20, ground + 2, 40, 180, -15);
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.waitTicks(10);
		selectSlot(ctx, 0);
		ctx.waitTicks(60);
		ctx.takeScreenshot("74_shaheds_sky_day");
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(20);
		boolean toRadar = ctx.tryClickScreenButton("Radar");
		AirDefense.LOGGER.info("[airdefense-test] radar tab button: {}", toRadar);
		ctx.waitTicks(40);
		ctx.takeScreenshot("75_radar_screen");
		ctx.waitTicks(60);
		ctx.takeScreenshot("76_radar_screen_later");
		int[] seen = ctx.computeOnClient(mc -> new int[]{com.stasdoto.airdefense.client.map.RadarScreen.stationCount(),
				com.stasdoto.airdefense.client.map.RadarScreen.contactCount()});
		AirDefense.LOGGER.info("[airdefense-test] RESULT radar screen: stations {} contacts {}", seen[0], seen[1]);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		server.runCommand("time set 14500");
		ctx.waitTicks(40);
		ctx.takeScreenshot("77_shaheds_sky_night");
		ctx.waitTicks(200);
		server.runCommand("time set 1000");
		report("radar_trml_iris_vs_shahed", before);
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
	}

	/**
	 * Stage R2: the flight task window and the drone's camera (launch with the camera on, watch, signal lost when it
	 * hits), then a massed night raid on a defended spot: the radar raises the alert, the drones glow in the sky.
	 */
	private void drones(ClientGameTestContext ctx, TestServerContext server) {
		int x = 24000;
		// The engine glow, close up at night: a Shahed and a Kalibr crossing the view 50-70 blocks away.
		camera(server, x + 0.5, ground + 2, -300, 0, -20);
		ctx.waitTicks(40);
		server.runCommand("time set 15000");
		server.runOnServer(s -> {
			com.stasdoto.airdefense.missile.MissileEntity.launchStrike(s.overworld(), com.stasdoto.airdefense.missile.MissileType.SHAHED,
					new Vec3(x - 40, ground + 25, -240), new Vec3(x + 400, ground, -240), new Vec3(1, 0, 0));
			com.stasdoto.airdefense.missile.MissileEntity.launchStrike(s.overworld(), com.stasdoto.airdefense.missile.MissileType.KALIBR,
					new Vec3(x - 60, ground + 30, -220), new Vec3(x + 500, ground, -220), new Vec3(1, 0, 0));
		});
		ctx.waitTicks(45);
		ctx.takeScreenshot("79_glow_night");
		server.runCommand("time set 1000");
		ctx.waitTicks(5);
		ctx.takeScreenshot("79b_glow_day");
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, ground + 1, -4, 0, 5);
		ctx.waitTicks(40);
		int shahed = spawnVehicle(server, VehicleType.SHAHED, x, 6, 0);
		BlockPos target = new BlockPos(x + 20, ground, 250);
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.waitTicks(5);
		selectSlot(ctx, 0);
		server.runOnServer(s -> {
			ItemStack tablet = DesignatorItem.held(s.getPlayerList().getPlayers().getFirst());
			if (tablet != null) {
				DesignatorItem.setTarget(tablet, target);
			}
		});
		ctx.waitTicks(30);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.map.TacticalMapScreen m) {
				m.select(shahed);
			}
		});
		ctx.waitTicks(5);
		boolean plan = ctx.tryClickScreenButton("Flight plan");
		ctx.waitTicks(10);
		boolean cam = ctx.tryClickScreenButton("Camera: off");
		ctx.waitTicks(5);
		ctx.takeScreenshot("80_flight_window");
		int startedBefore = com.stasdoto.airdefense.drone.DroneCam.started;
		boolean launch = ctx.tryClickScreenButton("Launch");
		AirDefense.LOGGER.info("[airdefense-test] flight window buttons: plan {} camera {} launch {}", plan, cam, launch);
		int waited = waitUntil(ctx, () -> com.stasdoto.airdefense.drone.DroneCam.started > startedBefore, 400);
		ctx.waitTicks(40);
		ctx.takeScreenshot("81_drone_cam");
		ctx.waitTicks(120);
		ctx.takeScreenshot("82_drone_cam_later");
		waitUntil(ctx, () -> ctx.computeOnClient(mc -> !(mc.getCameraEntity() instanceof com.stasdoto.airdefense.missile.MissileEntity)), 700);
		ctx.waitTicks(4);
		ctx.takeScreenshot("83_signal_lost");
		ctx.waitTicks(30);
		String mode = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().gameMode().getName());
		double back = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position().distanceTo(new Vec3(x + 0.5, ground + 1, -4)));
		AirDefense.LOGGER.info("[airdefense-test] RESULT drone_cam: started after {} ticks, camera frames {}, mode after {}, back at origin within {} blocks",
				waited, com.stasdoto.airdefense.client.drone.DroneClient.camFrames, mode, String.format(java.util.Locale.ROOT, "%.1f", back));
		server.runOnServer(s -> {
			if (s.overworld().getEntity(shahed) != null) {
				s.overworld().getEntity(shahed).discard();
			}
		});

		// A massed raid at night on a spot with a radar, an IRIS-T and a Gepard.
		int z = 1200;
		camera(server, x + 0.5, ground + 2, z, 180, -18);
		ctx.waitTicks(40);
		List<Integer> ids = new ArrayList<>();
		ids.add(spawnVehicle(server, VehicleType.TRML4D, x + 14, z + 12, 180));
		ids.add(spawnVehicle(server, VehicleType.IRIS_T, x - 14, z - 6, 180));
		ids.add(spawnVehicle(server, VehicleType.GEPARD, x + 8, z - 14, 180));
		server.runCommand("time set 15000");
		ctx.waitTicks(100);
		int[] before = counters();
		int launchedBefore = com.stasdoto.airdefense.drone.Raids.launched;
		int alertsBefore = com.stasdoto.airdefense.drone.Raids.alerts;
		int loops = com.stasdoto.airdefense.client.drone.DroneClient.loopsStarted;
		server.runOnServer(s -> com.stasdoto.airdefense.drone.Raids.start(s.overworld(), new Vec3(x + 0.5, ground, z + 0.5), -Math.PI / 2, 12, 1, 0, true));
		ctx.waitTicks(260);
		ctx.takeScreenshot("84_raid_sky");
		waitUntil(ctx, () -> com.stasdoto.airdefense.drone.Raids.alerts > alertsBefore, 300);
		ctx.waitTicks(10);
		ctx.takeScreenshot("85_raid_alert");
		ctx.waitTicks(80);
		ctx.takeScreenshot("86_raid_fight");
		ctx.waitTicks(80);
		ctx.takeScreenshot("87_raid_fight_later");
		ctx.waitTicks(400);
		AirDefense.LOGGER.info("[airdefense-test] RESULT raid: launched {} alerts {} engine sounds {}",
				com.stasdoto.airdefense.drone.Raids.launched - launchedBefore, com.stasdoto.airdefense.drone.Raids.alerts - alertsBefore,
				com.stasdoto.airdefense.client.drone.DroneClient.loopsStarted - loops);
		report("raid_12_shahed_1_kalibr", before);
		server.runCommand("time set 1000");
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
	}

	/** Stage R4: the twelve new air defence vehicles, folded and deployed, then a few of them at work. */
	private void newAirDefense(ClientGameTestContext ctx, TestServerContext server) {
		int x = 27000;
		VehicleType[] ad = {VehicleType.PANTSIR, VehicleType.TOR, VehicleType.BUK, VehicleType.S300, VehicleType.OSA, VehicleType.STRELA10,
				VehicleType.SHILKA, VehicleType.TUNGUSKA, VehicleType.SAMPT, VehicleType.AVENGER, VehicleType.MFG, VehicleType.ZU23};
		camera(server, x, ground + 10, 46, 180, 16);
		ctx.waitTicks(40);
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < ad.length; i++) {
			ids.add(spawnVehicle(server, ad[i], x - 40 + (i % 6) * 16, i < 6 ? 0 : 22, 200));
		}
		ctx.waitTicks(8);
		ctx.takeScreenshot("90_ad_folded");
		ctx.waitTicks(200);
		ctx.takeScreenshot("91_ad_deployed");
		camera(server, x - 52, ground + 6, 34, -125, 10);
		ctx.waitTicks(15);
		ctx.takeScreenshot("92_ad_side");
		camera(server, x + 50, ground + 6, -14, 60, 10);
		ctx.waitTicks(15);
		ctx.takeScreenshot("93_ad_other_side");
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));

		// Pantsir against five Shaheds: missiles further out, the guns close in.
		int px = x + 3000;
		prepareDefense(ctx, server, VehicleType.PANTSIR, px + 12, 50, 180);
		int[] before = counters();
		launchFrom(ctx, server, VehicleType.SHAHED, px, -170, new BlockPos(px, ground - 1, 70));
		ctx.waitTicks(150);
		camera(server, px + 28, ground + 6, 74, 150, -10);
		ctx.waitTicks(90);
		ctx.takeScreenshot("94_pantsir_fight");
		ctx.waitTicks(260);
		report("pantsir_vs_5_shahed", before);

		// S-300 against an Iskander.
		int sx = x + 6000;
		prepareDefense(ctx, server, VehicleType.S300, sx + 15, 60, 180);
		before = counters();
		launchFrom(ctx, server, VehicleType.ISKANDER, sx, -320, new BlockPos(sx, ground - 1, 80));
		camera(server, sx + 40, ground + 8, 90, 145, -25);
		ctx.waitTicks(260);
		ctx.takeScreenshot("95_s300_fight");
		ctx.waitTicks(200);
		report("s300_vs_iskander", before);

		// A mobile fire group and a ZU-23 against Shaheds.
		int mx = x + 9000;
		prepareDefense(ctx, server, VehicleType.MFG, mx + 10, 40, 180);
		spawnVehicle(server, VehicleType.ZU23, mx - 12, 46, 180);
		before = counters();
		launchFrom(ctx, server, VehicleType.SHAHED, mx, -170, new BlockPos(mx, ground - 1, 60));
		ctx.waitTicks(220);
		camera(server, mx + 18, ground + 4, 64, 150, -12);
		ctx.waitTicks(40);
		ctx.takeScreenshot("96_mfg_fight");
		ctx.waitTicks(260);
		report("mfg_zu23_vs_5_shahed", before);
	}

	/** Stage R5: tanks, fighting vehicles and boats; a T-72 driven (burning petrol) and firing at a BTR. */
	private void armor(ClientGameTestContext ctx, TestServerContext server) {
		int x = 37000;
		VehicleType[] land = {VehicleType.T72, VehicleType.T90, VehicleType.LEOPARD2, VehicleType.ABRAMS, VehicleType.BMP2, VehicleType.BRADLEY,
				VehicleType.BTR82, VehicleType.BTR4, VehicleType.M113, VehicleType.MAXXPRO, VehicleType.KOZAK};
		camera(server, x, ground + 9, 40, 180, 16);
		ctx.waitTicks(40);
		// A lake for the boats, behind the land vehicles.
		server.runOnServer(s -> {
			for (int wx = x - 50; wx <= x + 50; wx++) {
				for (int wz = -60; wz <= -25; wz++) {
					for (int wy = ground - 3; wy <= ground - 1; wy++) {
						s.overworld().setBlock(new BlockPos(wx, wy, wz), Blocks.WATER.defaultBlockState(), 2);
					}
				}
			}
		});
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < land.length; i++) {
			ids.add(spawnVehicle(server, land[i], x - 45 + (i % 6) * 15, i < 6 ? 0 : 16, 200));
		}
		VehicleType[] boats = {VehicleType.GYURZA, VehicleType.RAPTOR, VehicleType.RHIB};
		for (int i = 0; i < boats.length; i++) {
			VehicleType b = boats[i];
			int bx = x - 30 + i * 28;
			ids.add(server.computeOnServer(s -> VehicleEntity.spawn(s.overworld(), b, new Vec3(bx + 0.5, ground - 0.1, -42.5), 90).getId()));
		}
		for (int k = 0; k < 6; k++) {
			ctx.waitTicks(10);
			String info = server.computeOnServer(s -> {
				Entity b = s.overworld().getEntity(ids.get(land.length));
				BlockPos p = b.blockPosition();
				return String.format(java.util.Locale.ROOT, "y=%.2f below=%s at=%s vy=%.3f", b.getY(), s.overworld().getBlockState(p.below()).getBlock(),
						s.overworld().getBlockState(p).getBlock(), b.getDeltaMovement().y);
			});
			AirDefense.LOGGER.info("[airdefense-test] boat {}", info);
		}
		ctx.takeScreenshot("a0_armor_lineup");
		camera(server, x - 56, ground + 7, 30, -125, 12);
		ctx.waitTicks(15);
		ctx.takeScreenshot("a1_armor_side");
		camera(server, x, ground + 10, -5, 180, 25);
		ctx.waitTicks(15);
		ctx.takeScreenshot("a2_boats");
		double boatY = server.computeOnServer(s -> s.overworld().getEntity(ids.get(land.length)).getY());
		AirDefense.LOGGER.info("[airdefense-test] RESULT boat floats at y {} (water surface {})", String.format(java.util.Locale.ROOT, "%.2f", boatY), ground - 1 + 0.9);
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));

		// Drive a T-72 on petrol, then shoot at a BTR 50 blocks ahead.
		int tx = x + 3000;
		camera(server, tx, ground + 2, -6, 0, 5);
		ctx.waitTicks(40);
		int tank = spawnVehicle(server, VehicleType.T72, tx, 0, 0);
		int btr = spawnVehicle(server, VehicleType.BTR82, tx, 75, 90);
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			if (s.overworld().getEntity(tank) instanceof VehicleEntity v) {
				v.setUnlimited(false);
				v.addReserve(10);
				s.getPlayerList().getPlayers().getFirst().startRiding(v);
			}
		});
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(20);
		float fuel0 = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getFuel() : -1f);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(60);
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.waitTicks(40);
		float fuel1 = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getFuel() : -1f);
		ctx.takeScreenshot("a3_tank_driving");
		float hp0 = server.computeOnServer(s -> s.overworld().getEntity(btr) instanceof VehicleEntity v ? v.getHealth() : -1f);
		float[] yp = ctx.computeOnClient(mc -> {
			Vec3 d = new Vec3(tx + 0.5, ground + 1.2, 75.5).subtract(mc.player.getEyePosition());
			return new float[]{(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))};
		});
		ctx.getInput().lookAt(yp[0], yp[1]);
		ctx.waitTicks(60);
		ctx.takeScreenshot("a4_tank_aiming");
		for (int i = 0; i < 3; i++) {
			ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(4);
			if (i == 0) {
				ctx.takeScreenshot("a5_tank_fire");
			}
			ctx.waitTicks(150);
		}
		ctx.takeScreenshot("a6_tank_after");
		float hp1 = server.computeOnServer(s -> s.overworld().getEntity(btr) instanceof VehicleEntity v ? v.getHealth() : 0f);
		int ammo = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getAmmo() : -1);
		AirDefense.LOGGER.info("[airdefense-test] RESULT tank: fuel {} -> {} l, BTR health {} -> {}, shells left {}",
				(int) fuel0, String.format(java.util.Locale.ROOT, "%.1f", fuel1), (int) hp0, (int) hp1, ammo);
		ctx.getInput().holdKey(o -> o.keyShift);
		ctx.waitTicks(5);
		ctx.getInput().releaseKey(o -> o.keyShift);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(tank, btr), Entity::discard));
	}

	/** Stage R6: helicopters and planes on the ground, then a Mi-24 flown up and firing rockets, and a Su-25 taking off. */
	private void aircraft(ClientGameTestContext ctx, TestServerContext server) {
		int x = 41000;
		VehicleType[] air = {VehicleType.MI8, VehicleType.MI24, VehicleType.KA52, VehicleType.SU25, VehicleType.F16};
		camera(server, x, ground + 12, 50, 180, 18);
		ctx.waitTicks(40);
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < air.length; i++) {
			ids.add(spawnVehicle(server, air[i], x - 44 + i * 22, 0, 200));
		}
		ctx.waitTicks(30);
		ctx.takeScreenshot("b0_aircraft");
		camera(server, x - 60, ground + 8, 30, -125, 12);
		ctx.waitTicks(15);
		ctx.takeScreenshot("b1_aircraft_side");
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));

		// Mi-24: climb, fly forward, fire rockets.
		int hx = x + 2000;
		camera(server, hx, ground + 2, -10, 0, 0);
		ctx.waitTicks(40);
		int heli = spawnVehicle(server, VehicleType.MI24, hx, 0, 0);
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			if (s.overworld().getEntity(heli) instanceof VehicleEntity v) {
				s.getPlayerList().getPlayers().getFirst().startRiding(v);
			}
		});
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(150);
		ctx.getInput().holdKey(o -> o.keyJump);
		ctx.waitTicks(80);
		ctx.getInput().releaseKey(o -> o.keyJump);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(60);
		ctx.takeScreenshot("b2_mi24_flying");
		ctx.getInput().releaseKey(o -> o.keyUp);
		double alt = server.computeOnServer(s -> s.overworld().getEntity(heli).getY() - ground);
		int ord0 = server.computeOnServer(s -> s.overworld().getEntity(heli) instanceof VehicleEntity v ? v.getOrdnance() : -1);
		ctx.getInput().lookAt(0, 25);
		ctx.waitTicks(10);
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.VehicleClient.DEPLOY);
		ctx.waitTicks(6);
		ctx.takeScreenshot("b3_mi24_rockets");
		ctx.waitTicks(40);
		int ord1 = server.computeOnServer(s -> s.overworld().getEntity(heli) instanceof VehicleEntity v ? v.getOrdnance() : -1);
		AirDefense.LOGGER.info("[airdefense-test] RESULT heli: altitude {} rockets {} -> {}", String.format(java.util.Locale.ROOT, "%.1f", alt), ord0, ord1);
		ctx.getInput().holdKey(o -> o.keyShift);
		ctx.waitTicks(5);
		ctx.getInput().releaseKey(o -> o.keyShift);

		// Su-25: full throttle down the field, nose up, take off.
		int px = x + 4000;
		camera(server, px, ground + 2, -10, 0, 0);
		ctx.waitTicks(40);
		int plane = spawnVehicle(server, VehicleType.SU25, px, 0, 0);
		server.runOnServer(s -> {
			if (s.overworld().getEntity(plane) instanceof VehicleEntity v) {
				s.getPlayerList().getPlayers().getFirst().startRiding(v);
			}
		});
		ctx.waitTicks(20);
		ctx.getInput().lookAt(0, 0);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(160);
		ctx.getInput().lookAt(0, -20);
		ctx.waitTicks(100);
		ctx.takeScreenshot("b4_su25_takeoff");
		ctx.getInput().lookAt(0, 0);
		ctx.waitTicks(60);
		ctx.takeScreenshot("b5_su25_flying");
		double palt = server.computeOnServer(s -> s.overworld().getEntity(plane).getY() - ground);
		double pdist = server.computeOnServer(s -> s.overworld().getEntity(plane).position().distanceTo(new Vec3(px + 0.5, ground, 0.5)));
		ctx.getInput().releaseKey(o -> o.keyUp);
		AirDefense.LOGGER.info("[airdefense-test] RESULT plane: altitude {} distance {}", String.format(java.util.Locale.ROOT, "%.1f", palt),
				String.format(java.util.Locale.ROOT, "%.1f", pdist));
		ctx.getInput().holdKey(o -> o.keyShift);
		ctx.waitTicks(5);
		ctx.getInput().releaseKey(o -> o.keyShift);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(heli, plane), Entity::discard));
	}

	/** Puts a whole building up at once (for the screenshots), shapes of fences and panes fixed in a second pass. */
	private static void stamp(ServerLevel l, com.stasdoto.airdefense.nation.Building b) {
		var plan = com.stasdoto.airdefense.nation.Blueprints.placements(b, net.minecraft.world.item.DyeColor.BLUE);
		for (var pl : plan) {
			l.setBlock(pl.pos(), pl.state(), 2);
			if (pl.pair()) {
				l.setBlock(pl.pos2(), pl.state2(), 2);
			}
		}
		for (var pl : plan) {
			if (!pl.pair() && !pl.state().isAir()) {
				net.minecraft.world.level.block.state.BlockState st = Block.updateFromNeighbourShapes(pl.state(), l, pl.pos());
				if (!st.isAir()) {
					l.setBlock(pl.pos(), st, 2);
				}
			}
		}
	}

	private void logistics(ClientGameTestContext ctx, TestServerContext server) {
		int x0 = 39000;
		int g = ground;
		server.runCommand("difficulty peaceful");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 6000");
		// The modern buildings, three at a time.
		com.stasdoto.airdefense.nation.BuildingType[] types = {
				com.stasdoto.airdefense.nation.BuildingType.PANEL5, com.stasdoto.airdefense.nation.BuildingType.PANEL9,
				com.stasdoto.airdefense.nation.BuildingType.TOWER, com.stasdoto.airdefense.nation.BuildingType.OFFICE,
				com.stasdoto.airdefense.nation.BuildingType.COTTAGE, com.stasdoto.airdefense.nation.BuildingType.SHOP,
				com.stasdoto.airdefense.nation.BuildingType.SCHOOL, com.stasdoto.airdefense.nation.BuildingType.CITY_HALL,
				com.stasdoto.airdefense.nation.BuildingType.PARK, com.stasdoto.airdefense.nation.BuildingType.GAS_STATION,
				com.stasdoto.airdefense.nation.BuildingType.LOGISTICS_HUB, com.stasdoto.airdefense.nation.BuildingType.OIL_WELL,
				com.stasdoto.airdefense.nation.BuildingType.REFINERY, com.stasdoto.airdefense.nation.BuildingType.GARAGES,
				com.stasdoto.airdefense.nation.BuildingType.HOSPITAL, com.stasdoto.airdefense.nation.BuildingType.WAREHOUSE,
				com.stasdoto.airdefense.nation.BuildingType.BARRACKS, com.stasdoto.airdefense.nation.BuildingType.HANGAR,
				com.stasdoto.airdefense.nation.BuildingType.APARTMENTS, com.stasdoto.airdefense.nation.BuildingType.HOUSE,
				com.stasdoto.airdefense.nation.BuildingType.SMALL_HOUSE};
		int cx = x0;
		for (int i = 0; i < types.length; i += 3) {
			int gx0 = cx;
			List<com.stasdoto.airdefense.nation.Building> group = new ArrayList<>();
			for (int j = i; j < Math.min(types.length, i + 3); j++) {
				var t = types[j];
				int bx = cx + t.width / 2;
				var b = new com.stasdoto.airdefense.nation.Building(90000 + j, t, new BlockPos(bx, g - 1, 0), net.minecraft.core.Direction.NORTH, true);
				b.variant = j * 7 + 3;
				group.add(b);
				cx += t.width + 6;
			}
			int mid = (gx0 + cx - 6) / 2;
			int span = cx - 6 - gx0;
			camera(server, mid + 0.5, g + 14 + span * 0.12, 18 + span * 0.62, 180, 18);
			ctx.waitTicks(30);
			long t0 = System.nanoTime();
			int blocks = server.computeOnServer(s -> {
				int n = 0;
				for (var b : group) {
					stamp(s.overworld(), b);
					n += com.stasdoto.airdefense.nation.Blueprints.placements(b, net.minecraft.world.item.DyeColor.BLUE).size();
				}
				return n;
			});
			StringBuilder names = new StringBuilder();
			for (var b : group) {
				names.append(b.type.id).append(' ');
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT city_design {}: {} blocks in {} ms", names.toString().trim(), blocks,
					(System.nanoTime() - t0) / 1_000_000);
			ctx.waitTicks(40);
			ctx.takeScreenshot(String.format("120_city_%02d", i / 3));
		}

		// A town with an oil well, a refinery, a gas station and a logistics hub.
		int vx = x0;
		int vz = 200;
		server.runCommand("gamemode creative @a");
		camera(server, vx + 0.5, g + 30, vz + 75, 180, 25);
		ctx.waitTicks(30);
		village(server, vx, vz, new String[]{"none", "none", "none", "farmer", "none", "mason", "none", "none"});
		waitUntil(ctx, () -> settlementAt(server, vx, vz) >= 0, 400);
		int id = settlementAt(server, vx, vz);
		AirDefense.LOGGER.info("[airdefense-test] RESULT logistics_town: settlement {}", id);
		if (id < 0) {
			return;
		}
		server.runOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(id);
			com.stasdoto.airdefense.nation.Nations.takeOver(s.overworld(), s.getPlayerList().getPlayers().getFirst(), st);
			Object[][] plan = {{com.stasdoto.airdefense.nation.BuildingType.GAS_STATION, -32}, {com.stasdoto.airdefense.nation.BuildingType.LOGISTICS_HUB, 0},
					{com.stasdoto.airdefense.nation.BuildingType.REFINERY, 32}, {com.stasdoto.airdefense.nation.BuildingType.OIL_WELL, 58}};
			for (Object[] e : plan) {
				var t = (com.stasdoto.airdefense.nation.BuildingType) e[0];
				var b = new com.stasdoto.airdefense.nation.Building(p.newId(), t, new BlockPos(vx + (Integer) e[1], g - 1, vz + 40), net.minecraft.core.Direction.SOUTH,
						true);
				b.variant = 5;
				b.done = true;
				st.eco.buildings.add(b);
				stamp(s.overworld(), b);
			}
			st.eco.stock[com.stasdoto.airdefense.nation.VillageEconomy.IRON] = 60;
			for (int k = 0; k < 4; k++) {
				com.stasdoto.airdefense.nation.Supply.produce(p, st);
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT supply_produce: oil {} fuel {} ammo {} (pumped {}, refined {}, caps {} / {})",
					st.eco.stock[com.stasdoto.airdefense.nation.VillageEconomy.OIL], st.eco.stock[com.stasdoto.airdefense.nation.VillageEconomy.FUEL],
					st.eco.stock[com.stasdoto.airdefense.nation.VillageEconomy.AMMO], com.stasdoto.airdefense.nation.Supply.pumped,
					com.stasdoto.airdefense.nation.Supply.refined, st.eco.liquidCap(), st.eco.ammoCap());
			// Enough fuel for the vehicle tests even if the refinery is slow.
			st.eco.stock[com.stasdoto.airdefense.nation.VillageEconomy.FUEL] = Math.max(st.eco.stock[com.stasdoto.airdefense.nation.VillageEconomy.FUEL], 3000);
		});
		// The buildings face south (towards the camera): their fronts at z = vz + 40, footprints to vz + 40 + depth.
		int tank = spawnVehicle(server, VehicleType.T72, vx - 32, vz + 36, 0);
		int fuelTruck = spawnVehicle(server, VehicleType.FUEL_TRUCK, vx + 4, vz + 34, 90);
		int ammoTruck = spawnVehicle(server, VehicleType.SUPPLY_TRUCK, vx - 6, vz + 34, 90);
		server.runOnServer(s -> {
			for (int vid : new int[]{tank, fuelTruck, ammoTruck}) {
				if (s.overworld().getEntity(vid) instanceof VehicleEntity v) {
					v.setUnlimited(false);
					v.setFuel(v == s.overworld().getEntity(tank) ? 5 : 60);
					v.setTruckMode(VehicleEntity.TRUCK_LOAD);
				}
			}
		});
		float tankBefore = 5;
		ctx.waitTicks(100);
		ctx.takeScreenshot("125_logistics_town");
		String loaded = server.computeOnServer(s -> {
			StringBuilder sb = new StringBuilder();
			for (int vid : new int[]{tank, fuelTruck, ammoTruck}) {
				if (s.overworld().getEntity(vid) instanceof VehicleEntity v) {
					sb.append(v.getVehicleType().id).append(" fuel ").append((int) v.getFuel()).append(" cargo ").append(v.getCargo()).append("; ");
				}
			}
			return sb.toString();
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT station_and_load: tank fuel was {} | {} (served {})", tankBefore, loaded,
				com.stasdoto.airdefense.nation.Supply.served);
		// The trucks drive off to the front line: a BTR and a tank with empty tanks wait there.
		int front = vx + 160;
		int btr = spawnVehicle(server, VehicleType.BTR82, front, vz + 4, 0);
		int bmp = spawnVehicle(server, VehicleType.BMP2, front + 8, vz + 4, 0);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int vid : new int[]{btr, bmp}) {
				if (l.getEntity(vid) instanceof VehicleEntity v) {
					v.setUnlimited(false);
					v.setFuel(2);
				}
			}
			if (l.getEntity(fuelTruck) instanceof VehicleEntity t) {
				t.teleportTo(front + 4.5, g, vz - 4.5);
				t.setTruckMode(VehicleEntity.TRUCK_SUPPLY);
			}
			if (l.getEntity(ammoTruck) instanceof VehicleEntity t) {
				t.teleportTo(front - 4.5, g, vz - 4.5);
				t.setTruckMode(VehicleEntity.TRUCK_SUPPLY);
			}
		});
		camera(server, front + 4.5, g + 9, vz + 22, 180, 22);
		ctx.waitTicks(80);
		ctx.takeScreenshot("126_front_supply");
		String served = server.computeOnServer(s -> {
			StringBuilder sb = new StringBuilder();
			for (int vid : new int[]{btr, bmp, fuelTruck, ammoTruck}) {
				if (s.overworld().getEntity(vid) instanceof VehicleEntity v) {
					sb.append(v.getVehicleType().id).append(" fuel ").append((int) v.getFuel()).append(" reserve ").append(v.getReserve())
							.append(" cargo ").append(v.getCargo()).append("; ");
				}
			}
			return sb.toString();
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT truck_supply: {}", served);
		// Jerrycans filled at the gas station.
		camera(server, vx - 32 + 0.5, g, vz + 36.5, 0, 10);
		ctx.waitTicks(10);
		String cans = server.computeOnServer(s -> {
			var pl = s.getPlayerList().getPlayers().getFirst();
			ItemStack empty = new ItemStack(com.stasdoto.airdefense.registry.ModItems.EMPTY_JERRYCAN, 4);
			pl.getInventory().add(empty);
			ItemStack held = pl.getInventory().getItem(pl.getInventory().findSlotMatchingItem(new ItemStack(com.stasdoto.airdefense.registry.ModItems.EMPTY_JERRYCAN)));
			boolean ok = com.stasdoto.airdefense.nation.Supply.fillCans(s.overworld(), pl, held);
			return ok + " full " + pl.getInventory().countItem(com.stasdoto.airdefense.registry.ModItems.JERRYCAN);
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT jerrycans: {}", cans);
		// The town's store with oil, fuel and ammunition.
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.WORK);
		ctx.takeScreenshot("127_store_oil_fuel");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		// A driver's view in the fuel truck.
		server.runOnServer(s -> {
			var pl = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getEntity(fuelTruck) instanceof VehicleEntity t) {
				pl.startRiding(t);
			}
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("128_fuel_truck_hud");
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().stopRiding());
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
	}

	private void drive(ClientGameTestContext ctx, TestServerContext server) {
		int x = 800;
		server.runCommand("gamemode creative @a");
		List<Integer> ids = new ArrayList<>();
		server.runOnServer(s -> {
			VehicleEntity v = VehicleEntity.spawn(s.overworld(), VehicleType.HIMARS, new Vec3(x + 0.5, ground, 0.5), 0);
			ids.add(v.getId());
		});
		camera(server, x + 0.5, ground + 1, -6, 0, 10);
		ctx.waitTicks(20);
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getEntity(ids.getFirst()) instanceof VehicleEntity v) {
				boolean ok = player.startRiding(v);
				AirDefense.LOGGER.info("[airdefense-test] mounted HIMARS: {}", ok);
			}
		});
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(20);
		ctx.takeScreenshot("05_drive_seated");
		Vec3 start = server.computeOnServer(s -> s.overworld().getEntity(ids.getFirst()).position());
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(70);
		ctx.takeScreenshot("06_drive_forward");
		ctx.getInput().holdKey(o -> o.keyLeft);
		ctx.waitTicks(50);
		ctx.takeScreenshot("07_drive_turn");
		ctx.getInput().releaseKey(o -> o.keyLeft);
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.getInput().holdKey(o -> o.keyJump);
		ctx.waitTicks(30);
		ctx.getInput().releaseKey(o -> o.keyJump);
		Vec3 end = server.computeOnServer(s -> s.overworld().getEntity(ids.getFirst()).position());
		AirDefense.LOGGER.info("[airdefense-test] drove HIMARS from {} to {} ({} blocks)", start, end, String.format("%.1f", start.distanceTo(end)));
		ctx.getInput().holdKey(o -> o.keyShift);
		ctx.waitTicks(5);
		ctx.getInput().releaseKey(o -> o.keyShift);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));
	}

	private void unopposedIskander(ClientGameTestContext ctx, TestServerContext server) {
		int x = 1500;
		BlockPos target = new BlockPos(x, ground - 1, 170);
		int id = spawnVehicle(server, VehicleType.ISKANDER, x, 0, 0);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			// A little "target" building so the crater and debris are visible.
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					for (int dy = 0; dy < 4; dy++) {
						if (Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == 3) {
							level.setBlock(target.offset(dx, dy + 1, dz), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
						}
					}
				}
			}
		});
		camera(server, x + 16, ground + 5, 14, 135, 18);
		ctx.waitTicks(40);
		int launched = MissileStats.STRIKES_LAUNCHED.get();
		int impacts = MissileStats.GROUND_IMPACTS.get();
		strike(server, id, target);
		ctx.waitTicks(45);
		ctx.takeScreenshot("10_iskander_roof_open");
		ctx.waitTicks(70);
		ctx.takeScreenshot("11_iskander_erecting");
		waitUntil(ctx, () -> MissileStats.STRIKES_LAUNCHED.get() > launched, 400);
		ctx.waitTicks(6);
		ctx.takeScreenshot("12_iskander_launch");
		ctx.waitTicks(14);
		ctx.takeScreenshot("13_iskander_boost");
		camera(server, x + 40, ground + 20, 150, 120, -10);
		waitUntil(ctx, () -> MissileStats.GROUND_IMPACTS.get() > impacts, 400);
		ctx.waitTicks(3);
		ctx.takeScreenshot("14_iskander_impact");
		ctx.waitTicks(30);
		ctx.takeScreenshot("15_iskander_smoke");
		// The second missile, then the launcher folds up again.
		waitUntil(ctx, () -> MissileStats.GROUND_IMPACTS.get() > impacts + 1, 600);
		camera(server, x + 16, ground + 5, 14, 135, 18);
		ctx.waitTicks(200);
		ctx.takeScreenshot("15b_iskander_folded");
		AirDefense.LOGGER.info("[airdefense-test] after unopposed Iskander: {}", MissileStats.summary());
	}

	/** One Iskander impact filmed from 40 blocks away, frame by frame: flash, fireball, mushroom, column, fires. */
	private void effectsCloseup(ClientGameTestContext ctx, TestServerContext server, boolean night) {
		int x = night ? 10500 : 9000;
		BlockPos target = new BlockPos(x, ground - 1, 140);
		// The launcher's chunk has to be loaded before the vehicle is put there, so the camera goes there first.
		camera(server, x + 10, ground + 5, -10, 150, 10);
		ctx.waitTicks(30);
		int id = spawnVehicle(server, VehicleType.ISKANDER, x, 0, 0);
		ctx.waitTicks(5);
		int impacts = MissileStats.GROUND_IMPACTS.get();
		strike(server, id, target);
		ctx.waitTicks(20);
		camera(server, x + 38, ground + 5, 150, 104.7f, -14);
		waitUntil(ctx, () -> MissileStats.GROUND_IMPACTS.get() > impacts, 600);
		String p = night ? "17n_" : "16_";
		int[] at = {1, 4, 10, 25, 60, 140};
		int done = 0;
		for (int i = 0; i < at.length; i++) {
			ctx.waitTicks(at[i] - done);
			done = at[i];
			ctx.takeScreenshot(p + i + "_fx_" + at[i] + "t");
		}
		ctx.waitTicks(60);
		int flames = com.stasdoto.airdefense.client.fx.FxClient.FLAMES_SPAWNED;
		AirDefense.LOGGER.info("[airdefense-test] crater flames spawned so far: {}", flames);
	}

	/**
	 * Puts an air defence vehicle down with the camera next to it and lets it deploy (it then keeps its own ground
	 * loaded while on duty), so it is ready when the attack comes.
	 */
	private int prepareDefense(ClientGameTestContext ctx, TestServerContext server, VehicleType type, int x, int z, float yaw) {
		camera(server, x + 8, ground + 5, z + 8, 135, 10);
		ctx.waitTicks(30);
		int id = spawnVehicle(server, type, x, z, yaw);
		ctx.waitTicks(90);
		return id;
	}

	/** Launcher spawned with the camera near it (its chunk must be loaded), then fires. */
	private int launchFrom(ClientGameTestContext ctx, TestServerContext server, VehicleType type, int x, int z, BlockPos target) {
		camera(server, x + 10, ground + 5, z - 10, 30, 10);
		ctx.waitTicks(30);
		int id = spawnVehicle(server, type, x, z, 0);
		ctx.waitTicks(5);
		strike(server, id, target);
		return id;
	}

	private static int[] counters() {
		return new int[]{MissileStats.STRIKES_LAUNCHED.get(), MissileStats.THREATS_SHOT_DOWN.get(), MissileStats.GROUND_IMPACTS.get(),
				MissileStats.INTERCEPTORS_LAUNCHED.get(), MissileStats.INTERCEPTOR_CRASHES.get(), MissileStats.DECOYS_LAUNCHED.get(),
				MissileStats.DECOYS_DOWN.get(), MissileStats.SEEKER_FAILURES.get()};
	}

	private static void report(String scene, int[] before) {
		int[] now = counters();
		AirDefense.LOGGER.info("[airdefense-test] RESULT {}: threats={} shotDown={} reachedGround={} interceptors={} interceptorCrashes={}"
						+ " decoys={} decoysShotAt={} seekerFailures={}",
				scene, now[0] - before[0], now[1] - before[1], now[2] - before[2], now[3] - before[3], now[4] - before[4],
				now[5] - before[5], now[6] - before[6], now[7] - before[7]);
	}

	private void patriotVsIskander(ClientGameTestContext ctx, TestServerContext server) {
		int x = 3000;
		BlockPos target = new BlockPos(x, ground - 1, 90);
		prepareDefense(ctx, server, VehicleType.PATRIOT, x + 14, 70, 180);
		camera(server, x + 30, ground + 4, 94, 155, -18);
		ctx.waitTicks(20);
		ctx.takeScreenshot("19_patriot_ready");
		int[] before = counters();
		int shot = MissileStats.THREATS_SHOT_DOWN.get();
		int impacts = MissileStats.GROUND_IMPACTS.get();
		int interceptors = MissileStats.INTERCEPTORS_LAUNCHED.get();
		launchFrom(ctx, server, VehicleType.ISKANDER, x, -150, target);
		ctx.waitTicks(60);
		camera(server, x + 30, ground + 4, 94, 155, -18);
		int t = waitUntil(ctx, () -> MissileStats.INTERCEPTORS_LAUNCHED.get() > interceptors, 600);
		ctx.waitTicks(4);
		ctx.takeScreenshot("20_patriot_launch");
		ctx.waitTicks(12);
		ctx.takeScreenshot("21_patriot_climb");
		waitUntil(ctx, () -> MissileStats.THREATS_SHOT_DOWN.get() > shot || MissileStats.GROUND_IMPACTS.get() > impacts, 400);
		ctx.waitTicks(2);
		ctx.takeScreenshot("22_patriot_intercept");
		ctx.waitTicks(25);
		ctx.takeScreenshot("23_patriot_after");
		ctx.waitTicks(300);
		ctx.takeScreenshot("24_patriot_second");
		AirDefense.LOGGER.info("[airdefense-test] first Patriot interceptor after {} ticks", t);
		report("patriot_vs_iskander", before);
	}

	private void droneSwarm(ClientGameTestContext ctx, TestServerContext server) {
		int x = 4500;
		BlockPos target = new BlockPos(x, ground - 1, 60);
		prepareDefense(ctx, server, VehicleType.GEPARD, x + 10, 40, 180);
		int[] before = counters();
		launchFrom(ctx, server, VehicleType.SHAHED, x, -180, target);
		ctx.waitTicks(30);
		camera(server, x + 6, ground + 6, -168, 160, 10);
		ctx.waitTicks(5);
		ctx.takeScreenshot("30_shahed_takeoff");
		ctx.waitTicks(60);
		camera(server, x + 20, ground + 4, 64, 160, -12);
		for (int i = 0; i < 6; i++) {
			ctx.waitTicks(i < 2 ? 70 : 25);
			ctx.takeScreenshot("3" + (i + 1) + "_shahed_defense");
		}
		ctx.waitTicks(250);
		report("gepard_vs_5_shahed", before);
	}

	private void himarsVsNasams(ClientGameTestContext ctx, TestServerContext server) {
		int x = 6000;
		BlockPos target = new BlockPos(x, ground - 1, 80);
		prepareDefense(ctx, server, VehicleType.NASAMS, x + 12, 64, 180);
		int[] before = counters();
		int launched = MissileStats.STRIKES_LAUNCHED.get();
		launchFrom(ctx, server, VehicleType.HIMARS, x, -140, target);
		camera(server, x + 12, ground + 4, -128, 150, 5);
		waitUntil(ctx, () -> MissileStats.STRIKES_LAUNCHED.get() > launched, 300);
		ctx.waitTicks(4);
		ctx.takeScreenshot("40_himars_salvo");
		ctx.waitTicks(20);
		ctx.takeScreenshot("41_himars_salvo2");
		camera(server, x + 32, ground + 6, 98, 140, -15);
		ctx.waitTicks(40);
		ctx.takeScreenshot("42_himars_nasams");
		ctx.waitTicks(40);
		ctx.takeScreenshot("43_himars_impacts");
		ctx.waitTicks(200);
		report("nasams_vs_himars_salvo", before);
	}

	private void droneVsNasams(ClientGameTestContext ctx, TestServerContext server) {
		int x = 12000;
		BlockPos target = new BlockPos(x, ground - 1, 70);
		prepareDefense(ctx, server, VehicleType.NASAMS, x + 12, 50, 180);
		int[] before = counters();
		launchFrom(ctx, server, VehicleType.SHAHED, x, -170, target);
		ctx.waitTicks(120);
		camera(server, x + 26, ground + 6, 70, 150, -12);
		ctx.waitTicks(120);
		ctx.takeScreenshot("45_nasams_drones");
		ctx.waitTicks(260);
		report("nasams_vs_5_shahed", before);
	}

	private void cruiseVsIrisT(ClientGameTestContext ctx, TestServerContext server) {
		int x = 7500;
		BlockPos target = new BlockPos(x, ground - 1, 80);
		prepareDefense(ctx, server, VehicleType.IRIS_T, x + 12, 50, 180);
		int[] before = counters();
		int launched = MissileStats.STRIKES_LAUNCHED.get();
		launchFrom(ctx, server, VehicleType.KALIBR, x, -160, target);
		camera(server, x + 16, ground + 5, -146, 150, 0);
		waitUntil(ctx, () -> MissileStats.STRIKES_LAUNCHED.get() > launched, 400);
		ctx.waitTicks(10);
		ctx.takeScreenshot("50_kalibr_launch");
		camera(server, x + 26, ground + 8, 72, 160, -8);
		ctx.waitTicks(80);
		ctx.takeScreenshot("51_kalibr_cruise");
		ctx.waitTicks(60);
		ctx.takeScreenshot("52_kalibr_iris");
		ctx.waitTicks(260);
		report("iris_t_vs_2_kalibr", before);
	}

	private void droneVsIrisT(ClientGameTestContext ctx, TestServerContext server) {
		int x = 13500;
		BlockPos target = new BlockPos(x, ground - 1, 70);
		prepareDefense(ctx, server, VehicleType.IRIS_T, x + 12, 50, 180);
		int[] before = counters();
		launchFrom(ctx, server, VehicleType.SHAHED, x, -170, target);
		ctx.waitTicks(120);
		camera(server, x + 26, ground + 6, 70, 150, -12);
		ctx.waitTicks(120);
		ctx.takeScreenshot("53_iris_drones");
		ctx.waitTicks(260);
		report("iris_t_vs_5_shahed", before);
	}

	/**
	 * Stage 3: the tablet map, used the way a player does: right-click with the tablet, click the HIMARS, zoom out with
	 * the wheel, click a target, press "Fire!"; then switch a Patriot off from the map.
	 */
	private void tabletMap(ClientGameTestContext ctx, TestServerContext server) {
		int x = 15000;
		int g = ground;
		server.runCommand("gamemode creative @a");
		camera(server, x, g + 1, 70, 180, 0);
		ctx.waitTicks(40);
		// (Once the area is loaded:) some landmarks so the map has something to show: a lake, a road, buildings, a wood.
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 60, g - 1, 20, x - 25, g - 1, 55));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:gravel", x - 150, g - 1, -3, x + 150, g - 1, 2));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks hollow", x + 40, g, 25, x + 52, g + 6, 37));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:bricks hollow", x + 60, g, 30, x + 66, g + 4, 50));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves", x - 90, g, -60, x - 50, g + 3, -25));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:sand", x + 80, g - 1, -80, x + 120, g - 1, -40));
		int himars = spawnVehicle(server, VehicleType.HIMARS, x, 10, 180);
		int iskander = spawnVehicle(server, VehicleType.ISKANDER, x + 22, 15, 180);
		int patriot = spawnVehicle(server, VehicleType.PATRIOT, x - 25, 60, 200);
		spawnVehicle(server, VehicleType.GEPARD, x + 30, 70, 160);
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(80);
		// Right-click with the tablet opens the map.
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(30);
		ctx.takeScreenshot("60_map_overview");
		int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		// Click the HIMARS icon.
		int[] pos = ctx.computeOnClient(mc -> {
			var s = (com.stasdoto.airdefense.client.map.TacticalMapScreen) mc.gui.screen();
			for (int[] v : s.vehicleScreenPositions()) {
				if (v[0] == himars) {
					return v;
				}
			}
			return null;
		});
		AirDefense.LOGGER.info("[airdefense-test] HIMARS on the map at gui {} (scale {})", pos == null ? "-" : pos[1] + "," + pos[2], scale);
		if (pos == null) {
			return;
		}
		ctx.getInput().setCursorPos(pos[1] * scale + 1, pos[2] * scale + 1);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		// Zoom out twice with the wheel (the target is 220 blocks away), bring both into view, then click the target.
		ctx.getInput().scroll(-1);
		ctx.waitTicks(2);
		ctx.getInput().scroll(-1);
		ctx.waitTicks(5);
		double[] t = ctx.computeOnClient(mc -> {
			var s = (com.stasdoto.airdefense.client.map.TacticalMapScreen) mc.gui.screen();
			s.centerOn(x, 120, 4);
			return new double[]{s.toScreenX(x + 6.5), s.toScreenY(230.5)};
		});
		AirDefense.LOGGER.info("[airdefense-test] target on the map at gui {},{}", (int) t[0], (int) t[1]);
		ctx.getInput().setCursorPos(t[0] * scale + 1, t[1] * scale + 1);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(25);
		ctx.takeScreenshot("61_map_target");
		int launched = MissileStats.STRIKES_LAUNCHED.get();
		boolean fired = ctx.tryClickScreenButton("Fire!");
		AirDefense.LOGGER.info("[airdefense-test] Fire! button pressed: {}", fired);
		int waited = waitUntil(ctx, () -> MissileStats.STRIKES_LAUNCHED.get() > launched, 400);
		ctx.waitTicks(30);
		ctx.takeScreenshot("62_map_firing");
		AirDefense.LOGGER.info("[airdefense-test] RESULT map_strike: launched={} after {} ticks", MissileStats.STRIKES_LAUNCHED.get() - launched, waited);
		// Switch the Patriot off from the map.
		int[] pp = ctx.computeOnClient(mc -> {
			var s = (com.stasdoto.airdefense.client.map.TacticalMapScreen) mc.gui.screen();
			for (int[] v : s.vehicleScreenPositions()) {
				if (v[0] == patriot) {
					return v;
				}
			}
			return null;
		});
		if (pp != null) {
			ctx.getInput().setCursorPos(pp[1] * scale + 1, pp[2] * scale + 1);
			ctx.waitTicks(2);
			ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(5);
			boolean off = ctx.tryClickScreenButton("AD mode: auto");
			AirDefense.LOGGER.info("[airdefense-test] AD mode button pressed: {}", off);
			ctx.waitTicks(20);
			int mode = server.computeOnServer(s -> s.overworld().getEntity(patriot) instanceof VehicleEntity v ? v.getMode() : -1);
			AirDefense.LOGGER.info("[airdefense-test] RESULT map_mode: patriot mode after the button = {} (2 = manual)", mode);
			ctx.takeScreenshot("63_map_patriot_off");
		}
		ctx.waitTicks(80);
		ctx.takeScreenshot("64_map_rockets");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(10);
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(himars, iskander, patriot), Entity::discard));
	}

	/**
	 * Stage 4: manual fire. The player sits at the sight of a Gepard (then an IRIS-T) in manual mode, keeps the sight on
	 * the incoming drones like a gunner would (lead for the guns) and holds the left mouse button.
	 */
	private void manualDefense(ClientGameTestContext ctx, TestServerContext server, VehicleType adType, int x, String scene) {
		BlockPos target = new BlockPos(x, ground - 1, 60);
		server.runCommand("gamemode creative @a");
		int ad = prepareDefense(ctx, server, adType, x + 10, 40, 180);
		// The launcher keeps its own chunk loaded once it has been placed, so it can be placed first and fired later.
		camera(server, x + 10, ground + 5, -180, 30, 10);
		ctx.waitTicks(30);
		int launcher = spawnVehicle(server, VehicleType.SHAHED, x, -170, 0);
		ctx.waitTicks(10);
		camera(server, x + 12, ground + 2, 44, 180, 0);
		ctx.waitTicks(20);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getEntity(ad) instanceof VehicleEntity v) {
				p.startRiding(v);
				v.switchSeat(p);
				v.setModeByOrder(VehicleEntity.MODE_MANUAL, p);
			}
		});
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		ctx.waitTicks(30);
		int[] before = counters();
		strike(server, launcher, target);
		boolean gun = adType == VehicleType.GEPARD;
		boolean holding = false;
		for (int i = 0; i < 640; i++) {
			// A gunner keeps the sight on the nearest threat and holds the trigger only while one is in reach.
			float[] look = ctx.computeOnClient(mc -> aimAtThreat(mc, gun ? 62 : 135, gun));
			if (look != null) {
				ctx.getInput().lookAt(look[0], look[1]);
			}
			if (look != null && !holding) {
				ctx.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
				holding = true;
			} else if (look == null && holding) {
				ctx.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
				holding = false;
			}
			ctx.waitTick();
			if (i == 300 || i == 360 || i == 420) {
				ctx.takeScreenshot("7" + (gun ? "0" : "5") + "_" + scene + "_" + i);
			}
		}
		if (holding) {
			ctx.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		}
		ctx.waitTicks(60);
		report(scene, before);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			p.stopRiding();
			forVehicles(s.overworld(), List.of(ad, launcher), Entity::discard);
		});
		server.runCommand("gamemode spectator @a");
	}

	/** Look angles at the nearest threat (at its lead point for guns), as a gunner keeping it in the sight would. */
	private static float[] aimAtThreat(net.minecraft.client.Minecraft mc, double range, boolean lead) {
		Vec3 eye = mc.player.getEyePosition();
		com.stasdoto.airdefense.missile.MissileEntity best = null;
		double bestD = range;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof com.stasdoto.airdefense.missile.MissileEntity m && m.getMissileType().threat) {
				double d = m.position().distanceTo(eye);
				if (d < bestD) {
					bestD = d;
					best = m;
				}
			}
		}
		if (best == null) {
			return null;
		}
		Vec3 p = best.position();
		if (lead) {
			Vec3 vel = best.position().subtract(best.xo, best.yo, best.zo);
			Vec3 aim = p;
			for (int i = 0; i < 3; i++) {
				aim = p.add(vel.scale(aim.distanceTo(eye) / VehicleEntity.SHELL_SPEED));
			}
			p = aim;
		}
		Vec3 d = p.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new float[]{yaw, pitch};
	}

	/**
	 * Stage 5: a factory puts itself up, takes a survival order (resources leave the inventory), makes it, shows its
	 * menu, and restocks a survival HIMARS that has fired everything.
	 */
	private void factory(ClientGameTestContext ctx, TestServerContext server) {
		int x = 19500;
		int g = ground;
		camera(server, x + 24, g + 14, -16, 46, 24);
		ctx.waitTicks(40);
		BlockPos origin = new BlockPos(x, g - 1, 0);
		BlockPos ctrl = com.stasdoto.airdefense.factory.FactoryBlueprint.controllerPos(origin, net.minecraft.core.Direction.SOUTH);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			level.setBlock(ctrl, com.stasdoto.airdefense.registry.ModBlocks.FACTORY_CONTROLLER.defaultBlockState()
					.setValue(com.stasdoto.airdefense.factory.FactoryControllerBlock.FACING, net.minecraft.core.Direction.NORTH), Block.UPDATE_ALL);
			if (level.getBlockEntity(ctrl) instanceof com.stasdoto.airdefense.factory.FactoryBlockEntity f) {
				f.startConstruction(origin, net.minecraft.core.Direction.SOUTH, false);
			}
		});
		int t30 = waitUntil(ctx, () -> factoryPercent(server, ctrl) >= 30, 900);
		ctx.takeScreenshot("80_factory_30");
		waitUntil(ctx, () -> factoryPercent(server, ctrl) >= 70, 900);
		ctx.takeScreenshot("81_factory_70");
		int tDone = waitUntil(ctx, () -> factoryPercent(server, ctrl) >= 100, 1500);
		ctx.waitTicks(10);
		ctx.takeScreenshot("82_factory_built");
		AirDefense.LOGGER.info("[airdefense-test] factory: 30% after {} ticks, finished {} ticks later", t30, tDone);
		camera(server, x + 7, g + 4, 13, 120, 12);
		ctx.waitTicks(20);
		ctx.takeScreenshot("83_factory_inside");
		// A survival order: the resources must leave the inventory.
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:copper_ingot 6");
		server.runCommand("give @a minecraft:gunpowder 4");
		camera(server, x + 4.5, g, 5.5, 180, 15);
		ctx.waitTicks(10);
		String answer = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getBlockEntity(ctrl) instanceof com.stasdoto.airdefense.factory.FactoryBlockEntity f) {
				return f.order(p, com.stasdoto.airdefense.factory.Product.GEPARD_AMMO, 1).getString()
						+ " / copper left " + p.getInventory().countItem(net.minecraft.world.item.Items.COPPER_INGOT);
			}
			return "no factory";
		});
		AirDefense.LOGGER.info("[airdefense-test] factory order: {}", answer);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getBlockEntity(ctrl) instanceof com.stasdoto.airdefense.factory.FactoryBlockEntity f) {
				com.stasdoto.airdefense.factory.FactoryNet.send(p, f, true);
			}
		});
		ctx.waitForScreen(com.stasdoto.airdefense.client.factory.FactoryScreen.class);
		ctx.waitTicks(20);
		ctx.takeScreenshot("84_factory_menu");
		int made = waitUntil(ctx, () -> server.computeOnServer(s -> s.overworld().getBlockEntity(ctrl)
				instanceof com.stasdoto.airdefense.factory.FactoryBlockEntity f ? f.stock()[com.stasdoto.airdefense.factory.Product.GEPARD_AMMO.ordinal()] : 0) > 0, 400);
		ctx.takeScreenshot("85_factory_menu_done");
		AirDefense.LOGGER.info("[airdefense-test] RESULT factory_production: ammo box made after {} ticks", made);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		// A survival HIMARS fires everything, then restocks from the factory's stock.
		server.runOnServer(s -> {
			if (s.overworld().getBlockEntity(ctrl) instanceof com.stasdoto.airdefense.factory.FactoryBlockEntity f) {
				f.stock()[com.stasdoto.airdefense.factory.Product.GMLRS.ordinal()] = 12;
			}
		});
		int himars = server.computeOnServer(s -> {
			VehicleEntity v = VehicleEntity.spawn(s.overworld(), VehicleType.HIMARS, new Vec3(x + 20.5, g, -30.5), 180);
			v.setUnlimited(false);
			return v.getId();
		});
		ctx.waitTicks(5);
		strike(server, himars, new BlockPos(x + 20, g - 1, 320));
		int restocked = waitUntil(ctx, () -> server.computeOnServer(s -> s.overworld().getEntity(himars) instanceof VehicleEntity v
				&& Integer.bitCount(v.getLoadedMask()) == 6 && v.getReserve() == 6), 1100);
		String state = server.computeOnServer(s -> s.overworld().getEntity(himars) instanceof VehicleEntity v
				? "loaded " + Integer.bitCount(v.getLoadedMask()) + "/6, reserve " + v.getReserve() + "/" + v.reserveCapacity() : "gone");
		AirDefense.LOGGER.info("[airdefense-test] RESULT factory_resupply: {} after {} ticks", state, restocked);
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(himars), Entity::discard));
	}

	private static int factoryPercent(TestServerContext server, BlockPos ctrl) {
		return server.computeOnServer(s -> s.overworld().getBlockEntity(ctrl)
				instanceof com.stasdoto.airdefense.factory.FactoryBlockEntity f ? f.buildPercent() : -1);
	}

	/** Stage 6: the right sound layer for each distance, the crack of a round passing close, vehicle engine loops. */
	private void sounds(ClientGameTestContext ctx, TestServerContext server) {
		int x = 21000;
		int g = ground;
		server.runCommand("gamemode creative @a");
		camera(server, x, g, 0, 180, 0);
		ctx.waitTicks(40);
		StringBuilder layers = new StringBuilder();
		for (int d : new int[]{40, 150, 450}) {
			int[] before = com.stasdoto.airdefense.client.fx.SquadAudio.PLAYED.clone();
			server.runOnServer(s -> com.stasdoto.airdefense.missile.Effects.groundImpact(s.overworld(), null, new Vec3(x + 0.5, g, -d),
					com.stasdoto.airdefense.missile.MissileType.GMLRS));
			ctx.waitTicks(15);
			int[] now = com.stasdoto.airdefense.client.fx.SquadAudio.PLAYED;
			layers.append(d).append(": near ").append(now[0] - before[0]).append(" mid ").append(now[1] - before[1])
					.append(" far ").append(now[2] - before[2]).append("; ");
		}
		int cracks = com.stasdoto.airdefense.client.fx.SquadAudio.CRACKS;
		server.runOnServer(s -> {
			for (int i = 0; i < 4; i++) {
				com.stasdoto.airdefense.missile.Effects.tracer(s.overworld(), new Vec3(x + 0.5, g + 3, 70), new Vec3(x + 1.5, g + 2.5, -70), 8f);
			}
		});
		ctx.waitTicks(25);
		cracks = com.stasdoto.airdefense.client.fx.SquadAudio.CRACKS - cracks;
		int himars = spawnVehicle(server, VehicleType.HIMARS, x + 4, 6, 180);
		ctx.waitTicks(5);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getEntity(himars) instanceof VehicleEntity v) {
				p.startRiding(v);
				v.raiseLauncher();
			}
		});
		ctx.waitTicks(30);
		int loops = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.vehicle.VehicleSounds.playing());
		AirDefense.LOGGER.info("[airdefense-test] RESULT sounds: layers by distance [{}] cracks={} vehicleLoops={}", layers, cracks, loops);
		server.runOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().stopRiding();
			forVehicles(s.overworld(), List.of(himars), Entity::discard);
		});
		server.runCommand("gamemode spectator @a");
	}

	/**
	 * Stage 7: small arms and gear, all through the real controls - firing with the left button (the AK at a row of
	 * husks, glass, the PKM in third person, the SVD through the scope at a husk with and one without body armour),
	 * reloading with R, the RPG at a vehicle, a grenade thrown at a group, helmet + vest, night vision at night, a
	 * medkit.
	 */
	private void smallArms(ClientGameTestContext ctx, TestServerContext server) {
		int x = 23000;
		int g = ground;
		final int left = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
		final int right = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
		server.runCommand("difficulty normal");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		camera(server, x + 0.5, g, 0.5, 180, 0);
		ctx.waitTicks(40);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			var inv = p.getInventory();
			inv.setItem(0, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.AK74));
			inv.setItem(1, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.PKM));
			inv.setItem(2, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.SVD));
			inv.setItem(3, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.PM));
			inv.setItem(4, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.RPG7));
			inv.setItem(5, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.F1_GRENADE, 4));
			inv.setItem(6, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.MEDKIT, 2));
			inv.setItem(9, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_545, 90));
			inv.setItem(10, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_762, 90));
			inv.setItem(11, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.RPG_ROUND, 4));
			inv.setItem(12, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_9MM, 64));
		});
		selectSlot(ctx, 0);
		ctx.waitTicks(15);
		ctx.takeScreenshot("90_gun_hip");

		// AK-74 at four husks 18 blocks out (the second one has a helmet).
		List<Integer> row = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			row.add(husk(server, x + 0.5 - 4.5 + i * 3, g, -17.5, i == 1, false));
		}
		ctx.waitTicks(10);
		int shots0 = com.stasdoto.airdefense.weapon.GunServer.SHOTS.get();
		int hits0 = com.stasdoto.airdefense.weapon.GunServer.HITS.get();
		int heads0 = com.stasdoto.airdefense.weapon.GunServer.HEADSHOTS.get();
		boolean firingShot = false;
		for (int id : row) {
			for (int t = 0; t < 20 && alive(server, id); t++) {
				Vec3 at = entityPos(server, id).add(0, t < 8 ? 1.2 : 1.65, 0);
				aimAt(ctx, at);
				if (t == 0) {
					ctx.getInput().holdMouse(left);
				}
				ctx.waitTick();
				if (t == 3 && !firingShot) {
					ctx.takeScreenshot("91_gun_firing");
					firingShot = true;
				}
			}
			ctx.getInput().releaseMouse(left);
			ctx.waitTicks(4);
		}
		int killed = 0;
		for (int id : row) {
			killed += alive(server, id) ? 0 : 1;
		}
		int magLeft = server.computeOnServer(s -> com.stasdoto.airdefense.weapon.GunItem.ammo(s.getPlayerList().getPlayers().getFirst().getMainHandItem()));
		AirDefense.LOGGER.info("[airdefense-test] RESULT ak74: shots={} hits={} headshots={} killed={}/4 magazine={} clientShots={} confirmedHits={}",
				com.stasdoto.airdefense.weapon.GunServer.SHOTS.get() - shots0, com.stasdoto.airdefense.weapon.GunServer.HITS.get() - hits0,
				com.stasdoto.airdefense.weapon.GunServer.HEADSHOTS.get() - heads0, killed, magLeft,
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.weapon.GunClient.shotsSent),
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.weapon.GunClient.hitsConfirmed));

		// Reload with R from the rounds in the inventory.
		ctx.getInput().pressKey(com.stasdoto.airdefense.client.vehicle.VehicleClient.DEPLOY);
		ctx.waitTicks(8);
		ctx.takeScreenshot("92_gun_reload");
		ctx.waitTicks(60);
		String reload = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			return "magazine " + com.stasdoto.airdefense.weapon.GunItem.ammo(p.getMainHandItem()) + "/30, spare "
					+ com.stasdoto.airdefense.weapon.GunServer.countAmmo(p, com.stasdoto.airdefense.weapon.GunType.AK74);
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT reload: before {} -> {}", magLeft, reload);

		// Aiming down the sights; then a glass wall that shatters.
		ctx.getInput().holdMouse(right);
		ctx.waitTicks(12);
		ctx.takeScreenshot("93_gun_aim");
		ctx.getInput().releaseMouse(right);
		server.runCommand(String.format("fill %d %d -7 %d %d -7 minecraft:glass_pane", x - 2, g, x + 3, g + 2));
		ctx.waitTicks(10);
		int glass0 = com.stasdoto.airdefense.weapon.GunServer.GLASS_BROKEN.get();
		for (int i = 0; i < 3; i++) {
			aimAt(ctx, new Vec3(x - 1.5 + i * 2, g + 1.5, -6.5));
			ctx.getInput().pressMouse(left);
			ctx.waitTicks(4);
		}
		ctx.waitTicks(5);
		AirDefense.LOGGER.info("[airdefense-test] RESULT glass: panes broken {}", com.stasdoto.airdefense.weapon.GunServer.GLASS_BROKEN.get() - glass0);
		server.runCommand(String.format("fill %d %d -7 %d %d -7 minecraft:air", x - 2, g, x + 3, g + 2));

		// PKM, seen from the front in third person.
		selectSlot(ctx, 1);
		int mg1 = husk(server, x - 1.5, g, -26.5, false, false);
		int mg2 = husk(server, x + 2.5, g, -26.5, false, false);
		ctx.waitTicks(15);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(5);
		ctx.getInput().holdMouse(left);
		for (int t = 0; t < 40; t++) {
			int target = alive(server, mg1) ? mg1 : mg2;
			if (alive(server, target)) {
				aimAt(ctx, entityPos(server, target).add(0, 1.2, 0));
			}
			ctx.waitTick();
			if (t == 6) {
				ctx.takeScreenshot("94_pkm_third_person");
			}
		}
		ctx.getInput().releaseMouse(left);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(5);
		ctx.takeScreenshot("94b_pkm_back");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		AirDefense.LOGGER.info("[airdefense-test] RESULT pkm: killed {}/2, arm pose mixin {}", (alive(server, mg1) ? 0 : 1) + (alive(server, mg2) ? 0 : 1),
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.weapon.GunClient.armPoseApplied));

		// SVD through the scope: one husk without and one with body armour and a helmet, a single round into each chest.
		selectSlot(ctx, 2);
		int plain = husk(server, x - 2.5, g, -40.5, false, false);
		int armoured = husk(server, x + 3.5, g, -40.5, true, true);
		ctx.waitTicks(15);
		ctx.getInput().holdMouse(right);
		aimAt(ctx, entityPos(server, plain).add(0, 1.15, 0));
		ctx.waitTicks(15);
		float zoom = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.weapon.GunClient.fovMultiplier());
		ctx.takeScreenshot("95_svd_scope");
		ctx.getInput().pressMouse(left);
		ctx.waitTicks(12);
		aimAt(ctx, entityPos(server, armoured).add(0, 1.15, 0));
		ctx.waitTicks(8);
		ctx.getInput().pressMouse(left);
		ctx.waitTicks(12);
		ctx.getInput().releaseMouse(right);
		float armouredHealth = server.computeOnServer(s -> s.overworld().getEntity(armoured) instanceof net.minecraft.world.entity.LivingEntity l && l.isAlive() ? l.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT svd: zoom {} plain husk {} armoured husk {} (health {})", zoom,
				alive(server, plain) ? "alive" : "dead", alive(server, armoured) ? "alive" : "dead", armouredHealth);

		// RPG-7 at a Gepard 30 blocks away.
		selectSlot(ctx, 4);
		int gepard = spawnVehicle(server, VehicleType.GEPARD, x, -32, 90);
		ctx.waitTicks(20);
		float hp0 = server.computeOnServer(s -> s.overworld().getEntity(gepard) instanceof VehicleEntity v ? v.getHealth() : -1f);
		int impacts0 = MissileStats.ROCKET_IMPACTS.get();
		aimAt(ctx, new Vec3(x + 0.5, g + 1.3, -31.5));
		ctx.waitTicks(3);
		ctx.getInput().pressMouse(left);
		ctx.waitTicks(5);
		ctx.takeScreenshot("96_rpg_flight");
		ctx.waitTicks(10);
		ctx.takeScreenshot("96b_rpg_hit");
		ctx.waitTicks(30);
		float hp1 = server.computeOnServer(s -> s.overworld().getEntity(gepard) instanceof VehicleEntity v ? v.getHealth() : -1f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT rpg: gepard health {} -> {} (rocket impacts {})", hp0, hp1,
				MissileStats.ROCKET_IMPACTS.get() - impacts0);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(gepard), Entity::discard));

		// A grenade at three husks standing together 10 blocks away.
		selectSlot(ctx, 5);
		List<Integer> group = new ArrayList<>();
		group.add(husk(server, x - 0.5, g, -10.5, false, false));
		group.add(husk(server, x + 1.5, g, -10.5, false, false));
		group.add(husk(server, x + 0.5, g, -12.0, false, false));
		ctx.waitTicks(15);
		int boom0 = com.stasdoto.airdefense.weapon.GrenadeEntity.exploded;
		aimAt(ctx, new Vec3(x + 0.5, g + 1.55, -9.0));
		ctx.waitTicks(3);
		ctx.getInput().pressMouse(right);
		ctx.waitTicks(30);
		ctx.takeScreenshot("97_grenade_rolling");
		ctx.waitTicks(41);
		ctx.takeScreenshot("97b_grenade_blast");
		ctx.waitTicks(20);
		int grenadeKills = 0;
		for (int id : group) {
			grenadeKills += alive(server, id) ? 0 : 1;
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT grenade: exploded {} killed {}/3", com.stasdoto.airdefense.weapon.GrenadeEntity.exploded - boom0, grenadeKills);

		// Gear: helmet and vest (third person), then night vision goggles at night.
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.HELMET));
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.VEST));
		});
		selectSlot(ctx, 0);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(15);
		ctx.takeScreenshot("98_gear");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("time set 18000");
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.NVG_HELMET)));
		ctx.waitTicks(10);
		ctx.takeScreenshot("99a_night_without_nvg");
		ctx.getInput().pressKey(com.stasdoto.airdefense.client.weapon.GunClient.NVG);
		ctx.waitTicks(15);
		ctx.takeScreenshot("99_nvg_night");
		boolean nvg = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION));
		AirDefense.LOGGER.info("[airdefense-test] RESULT nvg: night vision {}", nvg);
		ctx.getInput().pressKey(com.stasdoto.airdefense.client.weapon.GunClient.NVG);
		server.runCommand("time set 1000");

		// Medkit: from 6 health, two seconds of bandaging.
		selectSlot(ctx, 6);
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setHealth(6));
		ctx.waitTicks(5);
		ctx.getInput().holdMouse(right);
		ctx.waitTicks(48);
		ctx.getInput().releaseMouse(right);
		ctx.waitTicks(10);
		float health = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
		AirDefense.LOGGER.info("[airdefense-test] RESULT medkit: health 6 -> {}", health);
		AirDefense.LOGGER.info("[airdefense-test] RESULT small_arms_fx: impacts drawn {} other shots seen {}",
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.fx.ShotFx.IMPACTS),
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.weapon.GunClient.otherShotsSeen));

		server.runCommand("kill @e[type=minecraft:husk]");
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		server.runCommand("gamemode spectator @a");
	}

	/**
	 * Stage 8: villages and countries. A village is found and named; people look like people (every one different);
	 * a charter is bought from the elder (respect + emeralds) through the village screen; two villagers are called up
	 * and sent to a point from the tablet map; bandits raid; a second village with a made-up country's guards is
	 * taken by force (guards beaten, 30 s at the flag); a third is taken at once in creative.
	 */
	private void nations(ClientGameTestContext ctx, TestServerContext server) {
		int x = 26000;
		int g = ground;
		server.runCommand("difficulty normal");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
		camera(server, x + 0.5, g + 6, 14.5, 180, 25);
		ctx.waitTicks(30);
		String[] jobs = {"farmer", "librarian", "cleric", "armorer", "butcher", "fisherman", "shepherd", "mason", "none", "toolsmith"};
		village(server, x, 0, jobs);
		int a = waitUntil(ctx, () -> settlementAt(server, x, 0) >= 0, 400);
		int villageA = settlementAt(server, x, 0);
		String nameA = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(villageA);
			if (st == null) {
				return "-";
			}
			// Make it independent for the peaceful purchase.
			st.country = -1;
			com.stasdoto.airdefense.nation.Nations.placeFlag(s.overworld(), p, st);
			return st.name;
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT village_found: '{}' after {} ticks", nameA, a);
		ctx.waitTicks(20);
		ctx.takeScreenshot("100_village_people");
		camera(server, x + 3.5, g + 1, 4.5, 150, 5);
		ctx.waitTicks(10);
		ctx.takeScreenshot("100b_people_closeup");

		// Peaceful: respect + emeralds, the charter bought on the village screen (Shift + right click on a villager).
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:emerald 64");
		server.runOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			p.settlements.get(villageA).bonus.put(s.getPlayerList().getPlayers().getFirst().getUUID(), 40);
		});
		camera(server, x + 0.5, g, 6.5, 180, 0);
		ctx.waitTicks(10);
		boolean clicked = false;
		for (int attempt = 0; attempt < 6 && !clicked; attempt++) {
			Vec3 v = server.computeOnServer(s -> {
				var list = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
						new net.minecraft.world.phys.AABB(x - 12, g - 2, -12, x + 12, g + 4, 12));
				return list.isEmpty() ? Vec3.ZERO : list.getFirst().getEyePosition();
			});
			server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %d %.1f", v.x, g, v.z + 2.2));
			ctx.waitTicks(3);
			aimAt(ctx, v.add(0, -0.3, 0));
			ctx.getInput().holdShift();
			ctx.waitTicks(3);
			aimAt(ctx, v.add(0, -0.3, 0));
			ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(2);
			ctx.getInput().releaseShift();
			if (ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen)) {
				ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
				ctx.waitTicks(3);
			}
			for (int t = 0; t < 20 && !clicked; t++) {
				ctx.waitTick();
				clicked = ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen);
			}
		}
		if (!clicked) {
			server.runOnServer(s -> com.stasdoto.airdefense.nation.NationNet.sendInfo(s.overworld(), s.getPlayerList().getPlayers().getFirst(),
					com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(villageA), true));
			ctx.waitTicks(10);
		}
		ctx.waitTicks(10);
		ctx.takeScreenshot("101_village_screen");
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen sc) {
				sc.act(com.stasdoto.airdefense.nation.NationActionPayload.BUY, 0);
			}
		});
		ctx.waitTicks(20);
		String owner = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var c = p.country(p.settlements.get(villageA).country);
			return c == null ? "nobody" : c.name + (c.owner != null ? " (player)" : "");
		});
		int emeralds = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().countItem(net.minecraft.world.item.Items.EMERALD));
		AirDefense.LOGGER.info("[airdefense-test] RESULT charter: screen by click {} -> owner {} emeralds left {}", clicked, owner, emeralds);

		// Call up two villagers.
		for (int i = 0; i < 2; i++) {
			ctx.runOnClient(mc -> {
				if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen sc) {
					sc.act(com.stasdoto.airdefense.nation.NationActionPayload.MOBILIZE, 1);
				}
			});
			ctx.waitTicks(10);
		}
		ctx.waitTicks(10);
		ctx.takeScreenshot("102_village_mine");
		int soldiers = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Nations.soldiers(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(villageA)).size());
		AirDefense.LOGGER.info("[airdefense-test] RESULT mobilize: soldiers {}", soldiers);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		camera(server, x + 4.5, g + 1, 7.5, 160, 10);
		ctx.waitTicks(15);
		ctx.takeScreenshot("102b_soldiers");

		// The tablet map: the army tab, the village, a point 40 blocks east, "To point".
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.DESIGNATOR)));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.open());
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.map.TacticalMapScreen m) {
				m.setTab(1);
				m.centerOn(x + 20, 0, 1);
				m.selectVillage(villageA);
				m.pickPoint(x + 40, 0);
			}
		});
		ctx.waitTicks(30);
		boolean sent = ctx.tryClickScreenButton("To point");
		ctx.waitTicks(10);
		ctx.takeScreenshot("103_map_army");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		double before = server.computeOnServer(s -> soldierDistance(s.overworld(), villageA, x + 40, 0));
		camera(server, x + 20.5, g + 12, 18.5, 180, 35);
		ctx.waitTicks(200);
		double after = server.computeOnServer(s -> soldierDistance(s.overworld(), villageA, x + 40, 0));
		ctx.takeScreenshot("104_squad_moved");
		AirDefense.LOGGER.info("[airdefense-test] RESULT order: button {} distance to the point {} -> {}", sent, (int) before, (int) after);

		// Bandits raid the village; its guards (a new owner gets guards in time) and soldiers fight back.
		server.runOnServer(s -> {
			var st = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(villageA);
			com.stasdoto.airdefense.nation.Nations.spawnGuard(s.overworld(), st, com.stasdoto.airdefense.nation.Politics.get(s).country(st.country));
			com.stasdoto.airdefense.nation.Nations.raid(s.overworld(), st, 3);
		});
		server.runCommand("gamemode spectator @a");
		camera(server, x + 0.5, g + 18, 30.5, 180, 30);
		int bandits0 = com.stasdoto.airdefense.nation.Nations.banditsSpawned;
		for (int t = 0; t < 700; t += 50) {
			ctx.waitTicks(50);
			if (t == 300) {
				ctx.takeScreenshot("105_bandits");
			}
		}
		int banditsAlive = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(com.stasdoto.airdefense.nation.SoldierEntity.class,
				new net.minecraft.world.phys.AABB(x - 120, g - 10, -120, x + 120, g + 30, 120),
				e -> e.isAlive() && e.role() == com.stasdoto.airdefense.nation.SoldierEntity.BANDIT).size());
		AirDefense.LOGGER.info("[airdefense-test] RESULT bandits: came {} still alive {}", 3, banditsAlive);
		server.runCommand("kill @e[type=airdefense:soldier,distance=..10000]");

		// By force: village B belongs to a country the world made up and has guards.
		int bx = x + 220;
		camera(server, bx + 0.5, g + 8, 16.5, 180, 25);
		ctx.waitTicks(30);
		village(server, bx, 0, new String[]{"farmer", "mason", "none", "fletcher", "shepherd"});
		waitUntil(ctx, () -> settlementAt(server, bx, 0) >= 0, 400);
		int villageB = settlementAt(server, bx, 0);
		String countryB = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(villageB);
			var c = com.stasdoto.airdefense.nation.Nations.makeCountry(s.overworld(), st, false);
			com.stasdoto.airdefense.nation.Nations.spawnGuard(s.overworld(), st, c);
			com.stasdoto.airdefense.nation.Nations.spawnGuard(s.overworld(), st, c);
			return c.name;
		});
		ctx.waitTicks(40);
		ctx.takeScreenshot("106_guards");
		// The guards are beaten (here simply killed), then the player stands at the flag for 30 seconds.
		server.runOnServer(s -> {
			var st = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(villageB);
			for (var gd : com.stasdoto.airdefense.nation.Nations.guards(s.overworld(), st)) {
				gd.kill(s.overworld());
			}
		});
		server.runCommand("gamemode survival @a");
		BlockPos flag = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(villageB).flag);
		camera(server, flag.getX() + 2.5, flag.getY(), flag.getZ() + 0.5, 90, 0);
		int took = waitUntil(ctx, () -> server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var c = p.country(p.settlements.get(villageB).country);
			return c != null && c.owner != null;
		}), 900);
		ctx.takeScreenshot("107_captured");
		AirDefense.LOGGER.info("[airdefense-test] RESULT capture: {} village {} taken after {} ticks", countryB, villageB, took);

		// Creative: a third village taken at once.
		int cx = x + 440;
		server.runCommand("gamemode creative @a");
		camera(server, cx + 0.5, g + 6, 12.5, 180, 25);
		ctx.waitTicks(30);
		village(server, cx, 0, new String[]{"none", "farmer", "cleric"});
		waitUntil(ctx, () -> settlementAt(server, cx, 0) >= 0, 400);
		int villageC = settlementAt(server, cx, 0);
		server.runOnServer(s -> com.stasdoto.airdefense.nation.NationNet.sendInfo(s.overworld(), s.getPlayerList().getPlayers().getFirst(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(villageC), true));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen sc) {
				sc.act(com.stasdoto.airdefense.nation.NationActionPayload.TAKE, 0);
			}
		});
		ctx.waitTicks(15);
		ctx.takeScreenshot("108_creative_take");
		boolean takenC = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var c = p.country(p.settlements.get(villageC).country);
			return c != null && c.owner != null;
		});
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		AirDefense.LOGGER.info("[airdefense-test] RESULT creative_take: {} (villages found {}, guards spawned {}, captures {})", takenC,
				com.stasdoto.airdefense.nation.Nations.discovered, com.stasdoto.airdefense.nation.Nations.guardsSpawned,
				com.stasdoto.airdefense.nation.Nations.captures);
		server.runCommand("kill @e[type=airdefense:soldier,distance=..10000]");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
	}

	private void economy(ClientGameTestContext ctx, TestServerContext server) {
		int x = 32000;
		int g = ground;
		server.runCommand("difficulty peaceful");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
		camera(server, x + 0.5, g + 10, 20.5, 180, 25);
		ctx.waitTicks(40);
		String[] jobs = {"none", "none", "none", "farmer", "librarian", "cleric", "mason", "none", "fisherman", "none", "toolsmith", "nitwit",
				"none", "shepherd"};
		village(server, x, 0, jobs);
		// A little wood and some rock near the village for the gatherers.
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int i = 0; i < 4; i++) {
				int tx = x - 30 + i * 5;
				int tz = -36 - (i % 2) * 3;
				for (int y = 0; y < 5; y++) {
					l.setBlock(new BlockPos(tx, g + y, tz), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
				}
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						for (int y = 3; y <= 6; y++) {
							BlockPos q = new BlockPos(tx + dx, g + y, tz + dz);
							if (l.getBlockState(q).isAir() && Math.abs(dx) + Math.abs(dz) + Math.max(0, y - 5) * 2 <= 3) {
								l.setBlock(q, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
										Block.UPDATE_ALL);
							}
						}
					}
				}
			}
			for (int i = 0; i < 3; i++) {
				int rx = x + 28 + i * 4;
				for (int dx = 0; dx < 2; dx++) {
					for (int dz = 0; dz < 2; dz++) {
						l.setBlock(new BlockPos(rx + dx, g, 36 + dz), (i == 2 ? Blocks.IRON_ORE : Blocks.STONE).defaultBlockState(), Block.UPDATE_ALL);
						l.setBlock(new BlockPos(rx + dx, g + 1, 36 + dz), Blocks.ANDESITE.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		});
		waitUntil(ctx, () -> settlementAt(server, x, 0) >= 0, 400);
		int id = settlementAt(server, x, 0);
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g, 9.5, 180, 0);
		ctx.waitTicks(5);
		server.runOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			com.stasdoto.airdefense.nation.Nations.takeOver(s.overworld(), s.getPlayerList().getPlayers().getFirst(), p.settlements.get(id));
		});
		ctx.waitTicks(5);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.WORK);
		// Put people to work: two woodcutters, two stonecutters, an iron miner, three builders.
		int[] want = {2, 2, 1, 3};
		for (int j = 0; j < want.length; j++) {
			for (int k = 0; k < want[j]; k++) {
				int job = j;
				ctx.runOnClient(mc -> {
					if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen sc) {
						sc.act(com.stasdoto.airdefense.nation.NationActionPayload.JOB, job, 1);
					}
				});
				ctx.waitTicks(3);
			}
		}
		ctx.waitTicks(25);
		ctx.takeScreenshot("110_work_tab");
		int[] counts = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Economy.jobCounts(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)));
		AirDefense.LOGGER.info("[airdefense-test] RESULT workers: wood {} stone {} iron {} build {} (hired {})", counts[0], counts[1], counts[2],
				counts[3], com.stasdoto.airdefense.nation.Economy.hired);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);

		// Survival: resources handed over, a small house paid for and put up by the builders; gatherers deliver.
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:oak_log 32");
		server.runCommand("give @a minecraft:cobblestone 64");
		server.runCommand("give @a minecraft:iron_ingot 20");
		camera(server, x + 6.5, g, 6.5, 135, 10);
		ctx.waitTicks(10);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.WORK);
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.DONATE, 0);
		ctx.waitTicks(25);
		int[] stock = stock(server, id);
		AirDefense.LOGGER.info("[airdefense-test] RESULT donate: wood {} stone {} iron {}", stock[0], stock[1], stock[2]);
		ctx.takeScreenshot("110b_store");
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.BUILD);
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.BUILD, com.stasdoto.airdefense.nation.BuildingType.SMALL_HOUSE.ordinal());
		ctx.waitTicks(25);
		int[] paid = stock(server, id);
		ctx.takeScreenshot("110c_build_tab");
		AirDefense.LOGGER.info("[airdefense-test] RESULT house_paid: wood {} -> {}, stone {} -> {}", stock[0], paid[0], stock[1], paid[1]);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		AirDefense.LOGGER.info("[airdefense-test] site search: {}", com.stasdoto.airdefense.nation.Sites.lastReport);
		BlockPos house = server.computeOnServer(s -> {
			var st = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id);
			return st.eco.buildings.isEmpty() ? st.center : st.eco.buildings.getFirst().middle();
		});
		server.runCommand("gamemode spectator @a");
		camera(server, house.getX() + 10.5, g + 7, house.getZ() + 10.5, 135, 22);
		int t0 = 0;
		boolean shotMid = false;
		for (; t0 < 2400; t0 += 20) {
			ctx.waitTicks(20);
			int pct = buildingPercent(server, id, 0);
			if (!shotMid && pct >= 40) {
				shotMid = true;
				ctx.takeScreenshot("111_builders_at_work");
			}
			if (pct >= 100) {
				break;
			}
		}
		ctx.takeScreenshot("112_small_house");
		AirDefense.LOGGER.info("[airdefense-test] RESULT small_house: {}% after {} ticks, delivered {}", buildingPercent(server, id, 0), t0,
				com.stasdoto.airdefense.nation.Economy.delivered);
		String states = server.computeOnServer(s -> {
			StringBuilder sb = new StringBuilder();
			for (var w : com.stasdoto.airdefense.nation.Economy.workers(s.overworld(), com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id))) {
				sb.append(w.job()).append(':').append(w.debug).append(" at ").append(w.blockPosition().getX() - x).append(',')
						.append(w.blockPosition().getZ()).append("; ");
			}
			return sb.toString();
		});
		AirDefense.LOGGER.info("[airdefense-test] workers: trips {} works {} deliveries {} | {}", com.stasdoto.airdefense.nation.WorkerEntity.trips,
				com.stasdoto.airdefense.nation.WorkerEntity.works, com.stasdoto.airdefense.nation.WorkerEntity.deliveries, states);
		// The gatherers at work.
		camera(server, x - 22.5, g + 6, -24.5, 160, 20);
		ctx.waitTicks(30);
		ctx.takeScreenshot("113_woodcutters");
		camera(server, x + 34.5, g + 5, 28.5, 180, 25);
		ctx.waitTicks(30);
		ctx.takeScreenshot("113b_stonecutters");

		// Creative: everything else, free and fast.
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g + 2, 6.5, 180, 0);
		ctx.waitTicks(10);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.BUILD);
		com.stasdoto.airdefense.nation.BuildingType[] first = {com.stasdoto.airdefense.nation.BuildingType.HOUSE,
				com.stasdoto.airdefense.nation.BuildingType.APARTMENTS, com.stasdoto.airdefense.nation.BuildingType.BARRACKS,
				com.stasdoto.airdefense.nation.BuildingType.HANGAR, com.stasdoto.airdefense.nation.BuildingType.HOSPITAL,
				com.stasdoto.airdefense.nation.BuildingType.WAREHOUSE};
		for (var t : first) {
			ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.BUILD, t.ordinal());
			ctx.waitTicks(6);
		}
		ctx.waitTicks(20);
		ctx.takeScreenshot("114_build_queue");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		server.runCommand("gamemode spectator @a");
		camera(server, x - 58.5, g + 42, -58.5, -45, 32);
		int waited = waitUntil(ctx, () -> server.computeOnServer(s -> com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).eco.active() == null), 3000);
		ctx.waitTicks(20);
		ctx.takeScreenshot("115_village_built");
		AirDefense.LOGGER.info("[airdefense-test] RESULT creative_buildings: done after {} ticks, built {}", waited,
				com.stasdoto.airdefense.nation.Economy.built);
		// The factory and the roads.
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g + 2, 6.5, 180, 0);
		ctx.waitTicks(10);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.BUILD);
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.BUILD, com.stasdoto.airdefense.nation.BuildingType.FACTORY.ordinal());
		ctx.waitTicks(10);
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.BUILD, com.stasdoto.airdefense.nation.BuildingType.ROADS.ordinal());
		ctx.waitTicks(10);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		server.runCommand("gamemode spectator @a");
		camera(server, x + 58.5, g + 46, 58.5, 135, 34);
		waited = waitUntil(ctx, () -> server.computeOnServer(s -> com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).eco.active() == null), 3000);
		ctx.waitTicks(20);
		ctx.takeScreenshot("116_factory_roads");
		String layout = server.computeOnServer(s -> {
			StringBuilder sb = new StringBuilder();
			for (var b : com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).eco.buildings) {
				sb.append(b.type.id).append(b.done ? "" : "(unfinished)").append('@').append(b.origin.getX() - x).append(',').append(b.origin.getZ())
						.append(' ').append(b.facing).append("; ");
			}
			return sb.toString();
		});
		int beds = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Economy.beds(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id), false));
		AirDefense.LOGGER.info("[airdefense-test] RESULT layout: factory+roads after {} ticks, beds {}: {}", waited, beds, layout);
		// Close-ups of the buildings.
		String[] shots = {"house", "apartments", "barracks", "hangar", "hospital", "warehouse", "factory"};
		for (String name : shots) {
			double[] cam = server.computeOnServer(s -> {
				for (var b : com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).eco.buildings) {
					if (b.type.id.equals(name)) {
						int dist = Math.max(b.type.width, b.type.height) + 4;
						BlockPos front = b.at(-b.type.halfWidth() - 2, 0, -dist);
						BlockPos mid = b.middle();
						double dx = mid.getX() - front.getX();
						double dz = mid.getZ() - front.getZ();
						float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
						return new double[]{front.getX() + 0.5, b.origin.getY() + 3 + b.type.height * 0.35, front.getZ() + 0.5, yaw};
					}
				}
				return null;
			});
			if (cam != null) {
				camera(server, cam[0], cam[1], cam[2], (float) cam[3], 12);
				ctx.waitTicks(25);
				ctx.takeScreenshot("117_" + name);
			}
		}
		// Inside the hangar a vehicle is made (creative: in seconds) and rolls out onto its floor.
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g + 2, 6.5, 180, 0);
		ctx.waitTicks(10);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.HANGAR);
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.VEHICLE, VehicleType.GEPARD.ordinal());
		ctx.waitTicks(30);
		ctx.takeScreenshot("118_hangar_tab");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		int made = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Economy.vehiclesMade > 0, 400);
		double[] hcam = server.computeOnServer(s -> {
			for (var b : com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).eco.buildings) {
				if (b.type == com.stasdoto.airdefense.nation.BuildingType.HANGAR) {
					BlockPos at = b.at(0, 4, -6);
					return new double[]{at.getX() + 0.5, at.getY(), at.getZ() + 0.5, b.facing.toYRot()};
				}
			}
			return new double[]{x, g + 5, 0, 0};
		});
		server.runCommand("gamemode spectator @a");
		camera(server, hcam[0], hcam[1], hcam[2], (float) hcam[3], 8);
		ctx.waitTicks(30);
		ctx.takeScreenshot("119_hangar_vehicle");
		AirDefense.LOGGER.info("[airdefense-test] RESULT hangar: vehicles made {} after {} ticks", com.stasdoto.airdefense.nation.Economy.vehiclesMade, made);
		// The village with its buildings on the tablet map.
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g + 2, 6.5, 180, 0);
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.DESIGNATOR)));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.open());
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.map.TacticalMapScreen m) {
				m.setTab(1);
				m.centerOn(x, 0, 1);
				m.selectVillage(id);
			}
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("119a_map_buildings");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		// The maternity hospital: babies while there are free beds.
		int born = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(id);
			int n = 0;
			for (int i = 0; i < 3; i++) {
				if (com.stasdoto.airdefense.nation.Economy.birth(s.overworld(), p, st)) {
					n++;
				}
			}
			return n;
		});
		int mob = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Nations.mobilizable(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)));
		AirDefense.LOGGER.info("[airdefense-test] RESULT hospital: born {} · barracks: can call up {}", born, mob);
		// Everybody home from work: the same villagers again (trades kept).
		String before = professions(server, x);
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g + 2, 6.5, 180, 0);
		ctx.waitTicks(10);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.OVERVIEW);
		ctx.takeScreenshot("119b_overview");
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.WORKERS_HOME, 0);
		ctx.waitTicks(20);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		String after = professions(server, x);
		int left = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Economy.workers(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)).size());
		AirDefense.LOGGER.info("[airdefense-test] RESULT home: workers left {}; villagers before [{}] after [{}]", left, before, after);
		server.runCommand("kill @e[type=airdefense:soldier,distance=..10000]");
		server.runCommand("kill @e[type=airdefense:gepard,distance=..10000]");
		server.runCommand("clear @a");
		server.runCommand("difficulty normal");
		server.runCommand("gamemode spectator @a");
	}

	private void breeding(ClientGameTestContext ctx, TestServerContext server) {
		int x = 34000;
		int g = ground;
		server.runCommand("difficulty peaceful");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
		camera(server, x + 0.5, g + 6, 14.5, 180, 25);
		ctx.waitTicks(40);
		village(server, x, 0, new String[]{"none", "farmer", "none", "librarian", "none"});
		// Eight beds by the square (the five villagers take five of them).
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int i = 0; i < 8; i++) {
				BlockPos foot = new BlockPos(x - 7 + i * 2, g, 8);
				var bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
				l.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), Block.UPDATE_ALL);
				l.setBlock(foot.south(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), Block.UPDATE_ALL);
			}
		});
		waitUntil(ctx, () -> settlementAt(server, x, 0) >= 0, 400);
		int id = settlementAt(server, x, 0);
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:bread 16");
		camera(server, x + 0.5, g, 6.5, 180, 0);
		ctx.waitTicks(5);
		// The bread is in the first slot: hold it (earlier scenes may have left another slot selected).
		selectSlot(ctx, 0);
		server.runOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			com.stasdoto.airdefense.nation.Nations.takeOver(s.overworld(), s.getPlayerList().getPlayers().getFirst(), p.settlements.get(id));
		});
		ctx.waitTicks(5);
		// Feeding a villager bread from the hand.
		int fed0 = com.stasdoto.airdefense.nation.Economy.fed;
		for (int attempt = 0; attempt < 6 && com.stasdoto.airdefense.nation.Economy.fed - fed0 < 3; attempt++) {
			Vec3 v = server.computeOnServer(s -> {
				var list = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
						new net.minecraft.world.phys.AABB(x - 12, g - 2, -12, x + 12, g + 4, 12), vv -> !vv.isBaby());
				return list.isEmpty() ? Vec3.ZERO : list.getFirst().getEyePosition();
			});
			server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %d %.1f", v.x, g, v.z + 2.2));
			ctx.waitTicks(3);
			aimAt(ctx, v.add(0, -0.3, 0));
			ctx.waitTicks(2);
			ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(4);
			if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) {
				ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
				ctx.waitTicks(3);
			}
		}
		ctx.takeScreenshot("120_feeding");
		int bread = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().countItem(net.minecraft.world.item.Items.BREAD));
		AirDefense.LOGGER.info("[airdefense-test] RESULT feeding: fed {} times, bread left {}", com.stasdoto.airdefense.nation.Economy.fed - fed0, bread);
		// A child born by the square (no hospital needed in your own village), growing up fast.
		int every = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Economy.birthEvery(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s), com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)));
		boolean born = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Economy.birth(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s), com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)));
		int[] age0 = server.computeOnServer(s -> {
			var list = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
					new net.minecraft.world.phys.AABB(x - 20, g - 2, -20, x + 20, g + 4, 20), vv -> vv.isBaby());
			return list.isEmpty() ? new int[]{0, 0} : new int[]{list.getFirst().getId(), list.getFirst().getAge()};
		});
		camera(server, x + 4.5, g + 2, 4.5, 135, 15);
		ctx.waitTicks(400);
		int age1 = server.computeOnServer(s -> s.overworld().getEntity(age0[0]) instanceof net.minecraft.world.entity.npc.villager.Villager v ? v.getAge() : 0);
		ctx.takeScreenshot("121_child");
		AirDefense.LOGGER.info("[airdefense-test] RESULT births: one every {} s, born {}, child grew {} ticks in 400", every, born, age1 - age0[1]);
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g, 6.5, 180, 0);
		ctx.waitTicks(5);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.OVERVIEW);
		ctx.waitTicks(25);
		ctx.takeScreenshot("122_births_line");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		server.runCommand("clear @a");
		server.runCommand("difficulty normal");
		server.runCommand("gamemode spectator @a");
	}

	private void unrest(ClientGameTestContext ctx, TestServerContext server) {
		int x = 36000;
		int g = ground;
		server.runCommand("difficulty normal");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
		camera(server, x + 0.5, g + 8, 16.5, 180, 25);
		ctx.waitTicks(40);
		village(server, x, 0, new String[]{"none", "farmer", "none", "librarian", "none", "mason", "none", "cleric", "none", "fisherman"});
		waitUntil(ctx, () -> settlementAt(server, x, 0) >= 0, 400);
		int id = settlementAt(server, x, 0);
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g, 9.5, 180, 0);
		ctx.waitTicks(5);
		server.runOnServer(s -> {
			var l = s.overworld();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(id);
			var player = s.getPlayerList().getPlayers().getFirst();
			com.stasdoto.airdefense.nation.Nations.takeOver(l, player, st);
			// Half the village called up: the people do not like it.
			com.stasdoto.airdefense.nation.Nations.mobilize(l, player, st, 99);
			var c = p.country(st.country);
			com.stasdoto.airdefense.nation.Nations.spawnGuard(l, st, c);
		});
		ctx.waitTicks(10);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.OVERVIEW);
		ctx.waitTicks(20);
		ctx.takeScreenshot("130_mood");
		int mood = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Unrest.mood(s.overworld(), com.stasdoto.airdefense.nation.Politics.get(s),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)).value());
		AirDefense.LOGGER.info("[airdefense-test] RESULT mood: {} with half the village called up", mood);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);

		// A riot: rebels make for the flag, the guard and the soldiers fight them.
		boolean started = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Unrest.startRiot(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s), com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)));
		int rebels = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Unrest.rebels(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)).size());
		server.runCommand("gamemode spectator @a");
		camera(server, x + 8.5, g + 5, 10.5, 140, 20);
		ctx.waitTicks(80);
		ctx.takeScreenshot("131_riot");
		AirDefense.LOGGER.info("[airdefense-test] RESULT riot: started {} rebels {}", started, rebels);
		// Calmed with gifts (creative: free): the rebels go home.
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, g, 9.5, 180, 0);
		ctx.waitTicks(5);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.OVERVIEW);
		ctx.takeScreenshot("132_riot_screen");
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.CALM, 0);
		ctx.waitTicks(20);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		int afterCalm = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Unrest.rebels(s.overworld(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id)).size());
		AirDefense.LOGGER.info("[airdefense-test] RESULT calm: rebels left {} (calmed {})", afterCalm, com.stasdoto.airdefense.nation.Unrest.calmed);

		// Another riot with nobody to stop it: the village breaks away.
		server.runOnServer(s -> {
			var l = s.overworld();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(id);
			st.calmUntil = 0;
			for (var e : com.stasdoto.airdefense.nation.Nations.soldiers(l, st)) {
				e.demobilize(l);
			}
			st.soldiers.clear();
			for (var gd : com.stasdoto.airdefense.nation.Nations.guards(l, st)) {
				gd.discard();
			}
			com.stasdoto.airdefense.nation.Unrest.startRiot(l, p, st);
		});
		server.runCommand("gamemode spectator @a");
		camera(server, x + 8.5, g + 6, 12.5, 140, 25);
		int took = waitUntil(ctx, () -> server.computeOnServer(s -> com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).country < 0), 1200);
		ctx.takeScreenshot("133_seceded");
		AirDefense.LOGGER.info("[airdefense-test] RESULT secede: after {} ticks (seceded {})", took, com.stasdoto.airdefense.nation.Unrest.seceded);

		// A riot put down: the rebels are beaten.
		int bx = x + 200;
		camera(server, bx + 0.5, g + 8, 16.5, 180, 25);
		ctx.waitTicks(30);
		village(server, bx, 0, new String[]{"none", "farmer", "none", "mason", "none"});
		waitUntil(ctx, () -> settlementAt(server, bx, 0) >= 0, 400);
		int id2 = settlementAt(server, bx, 0);
		server.runOnServer(s -> {
			var l = s.overworld();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(id2);
			com.stasdoto.airdefense.nation.Nations.takeOver(l, s.getPlayerList().getPlayers().getFirst(), st);
			com.stasdoto.airdefense.nation.Unrest.startRiot(l, p, st);
		});
		ctx.waitTicks(40);
		server.runOnServer(s -> {
			for (var e : com.stasdoto.airdefense.nation.Unrest.rebels(s.overworld(), com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id2))) {
				e.kill(s.overworld());
			}
		});
		int over = waitUntil(ctx, () -> server.computeOnServer(s -> !com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id2).riot), 200);
		AirDefense.LOGGER.info("[airdefense-test] RESULT suppressed: riot over after {} ticks (suppressed {}, riots {})", over,
				com.stasdoto.airdefense.nation.Unrest.suppressed, com.stasdoto.airdefense.nation.Unrest.riots);
		server.runCommand("kill @e[type=airdefense:soldier,distance=..10000]");
		server.runCommand("gamemode spectator @a");
	}

	private void war(ClientGameTestContext ctx, TestServerContext server) {
		int x = 38000;
		int bx = x + 120;
		int g = ground;
		server.runCommand("difficulty normal");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
		camera(server, x + 60.5, g + 30, 30.5, 180, 40);
		ctx.waitTicks(40);
		village(server, x, 0, new String[]{"none", "farmer", "none", "librarian", "none", "mason", "none", "cleric"});
		village(server, bx, 0, new String[]{"none", "farmer", "none", "fletcher", "none"});
		waitUntil(ctx, () -> settlementAt(server, x, 0) >= 0 && settlementAt(server, bx, 0) >= 0, 400);
		int a = settlementAt(server, x, 0);
		int b = settlementAt(server, bx, 0);
		String enemy = server.computeOnServer(s -> {
			var l = s.overworld();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var player = s.getPlayerList().getPlayers().getFirst();
			var stA = p.settlements.get(a);
			com.stasdoto.airdefense.nation.Nations.takeOver(l, player, stA);
			com.stasdoto.airdefense.nation.Nations.spawnGuard(l, stA, p.country(stA.country));
			com.stasdoto.airdefense.nation.Nations.spawnGuard(l, stA, p.country(stA.country));
			var stB = p.settlements.get(b);
			var c = com.stasdoto.airdefense.nation.Nations.makeCountry(l, stB, false);
			com.stasdoto.airdefense.nation.Nations.spawnGuard(l, stB, c);
			return c.name;
		});
		// War declared from the enemy village's screen.
		server.runCommand("gamemode creative @a");
		camera(server, bx + 0.5, g, 9.5, 180, 0);
		ctx.waitTicks(10);
		ecoScreen(ctx, server, b, com.stasdoto.airdefense.client.nation.SettlementScreen.OVERVIEW);
		ctx.takeScreenshot("140_enemy_village");
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.DECLARE_WAR, 0);
		ctx.waitTicks(25);
		ctx.takeScreenshot("140b_war_declared");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		boolean atWar = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			return com.stasdoto.airdefense.nation.Nations.atWar(s.overworld(), p.settlements.get(a).country, p.settlements.get(b).country);
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT war_declared: {} against {} (declared {})", atWar, enemy, com.stasdoto.airdefense.nation.War.declared);
		// The tablet map shows the enemy.
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.DESIGNATOR)));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.open());
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.map.TacticalMapScreen m) {
				m.setTab(1);
				m.centerOn(x + 60, 0, 1);
				m.selectVillage(b);
			}
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("141_map_war");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		server.runCommand("clear @a");

		// The enemy sends a squad against village A; its guards fight.
		server.runCommand("gamemode spectator @a");
		camera(server, x + 10.5, g + 8, 14.5, 150, 25);
		ctx.waitTicks(20);
		int sent = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var stA = p.settlements.get(a);
			var ai = p.country(p.settlements.get(b).country);
			return com.stasdoto.airdefense.nation.War.sendSquad(s.overworld(), p, ai, stA, 4).size();
		});
		ctx.waitTicks(260);
		ctx.takeScreenshot("142_battle");
		String fight = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var stA = p.settlements.get(a);
			int ai = p.settlements.get(b).country;
			int enemies = s.overworld().getEntitiesOfClass(com.stasdoto.airdefense.nation.SoldierEntity.class,
					new net.minecraft.world.phys.AABB(x - 120, g - 10, -120, x + 120, g + 30, 120), e -> e.isAlive() && e.country() == ai).size();
			return enemies + " enemies, " + com.stasdoto.airdefense.nation.Nations.guards(s.overworld(), stA).size() + " guards";
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT squad: sent {}, after the fight {}", sent, fight);

		// Nobody defends A: a second squad takes it.
		server.runOnServer(s -> {
			var l = s.overworld();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var stA = p.settlements.get(a);
			for (var gd : com.stasdoto.airdefense.nation.Nations.guards(l, stA)) {
				gd.discard();
			}
			var ai = p.country(p.settlements.get(b).country);
			com.stasdoto.airdefense.nation.War.sendSquad(l, p, ai, stA, 4);
		});
		camera(server, x - 170.5, g + 20, 0.5, -90, 10);
		int took = waitUntil(ctx, () -> server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			return p.settlements.get(a).country == p.settlements.get(b).country;
		}), 2400);
		camera(server, x + 10.5, g + 8, 14.5, 150, 25);
		ctx.waitTicks(30);
		ctx.takeScreenshot("143_village_lost");
		AirDefense.LOGGER.info("[airdefense-test] RESULT ai_capture: village A taken after {} ticks (captures {})", took,
				com.stasdoto.airdefense.nation.War.aiCaptures);

		// Peace: they want emeralds (they are winning); paid (creative: free).
		server.runCommand("gamemode creative @a");
		camera(server, bx + 0.5, g, 9.5, 180, 0);
		ctx.waitTicks(10);
		ecoScreen(ctx, server, b, com.stasdoto.airdefense.client.nation.SettlementScreen.OVERVIEW);
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.PEACE, 0);
		ctx.waitTicks(30);
		ctx.takeScreenshot("144_peace_price");
		int price = ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen sc ? sc.info().tribute() : -1);
		ecoAct(ctx, com.stasdoto.airdefense.nation.NationActionPayload.TRIBUTE, 0);
		ctx.waitTicks(20);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		boolean still = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var mine = p.countryOwnedBy(s.getPlayerList().getPlayers().getFirst().getUUID());
			return mine != null && mine.atWarWith(p.settlements.get(b).country);
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT peace: price {} -> still at war {} (peaces {})", price, still, com.stasdoto.airdefense.nation.War.peaces);
		server.runCommand("kill @e[type=airdefense:soldier,distance=..10000]");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
	}

	/** Opens the village screen of village {@code id} on the given tab. */
	private static void ecoScreen(ClientGameTestContext ctx, TestServerContext server, int id, int tab) {
		server.runOnServer(s -> com.stasdoto.airdefense.nation.NationNet.sendInfo(s.overworld(), s.getPlayerList().getPlayers().getFirst(),
				com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id), true));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen sc) {
				sc.setTab(tab);
			}
		});
		ctx.waitTicks(10);
	}

	private static void ecoAct(ClientGameTestContext ctx, int action, int a) {
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.SettlementScreen sc) {
				sc.act(action, a);
			}
		});
	}

	private static int[] stock(TestServerContext server, int id) {
		return server.computeOnServer(s -> com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).eco.stock.clone());
	}

	private static int buildingPercent(TestServerContext server, int id, int index) {
		return server.computeOnServer(s -> {
			var list = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id).eco.buildings;
			return index < list.size() ? list.get(index).percent() : -1;
		});
	}

	/** Professions of the villagers around a village, sorted (to compare before and after work). */
	private String professions(TestServerContext server, int x) {
		int g = ground;
		return server.computeOnServer(s -> {
			List<String> list = new ArrayList<>();
			for (var v : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
					new net.minecraft.world.phys.AABB(x - 90, g - 10, -90, x + 90, g + 30, 90), v -> v.isAlive() && !v.isBaby())) {
				list.add(v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
			}
			java.util.Collections.sort(list);
			return String.join(",", list);
		});
	}

	/** A little village: a bell on the square and villagers of the given trades around it. */
	private void village(TestServerContext server, int x, int z, String[] jobs) {
		int g = ground;
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			l.setBlock(new BlockPos(x, g, z), Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
			for (int i = 0; i < jobs.length; i++) {
				double a = Math.PI * 2 * i / jobs.length;
				var v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				v.snapTo(x + 0.5 + Math.cos(a) * 4, g, z + 0.5 + Math.sin(a) * 4, (float) Math.toDegrees(a) + 90, 0);
				var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION,
						net.minecraft.resources.Identifier.withDefaultNamespace(jobs[i]));
				v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(), key));
				v.setVillagerXp(1);
				v.setPersistenceRequired();
				l.addFreshEntity(v);
			}
		});
	}

	private int settlementAt(TestServerContext server, int x, int z) {
		int g = ground;
		return server.computeOnServer(s -> {
			var st = com.stasdoto.airdefense.nation.Politics.get(s).settlementAt(new BlockPos(x, g, z));
			return st == null ? -1 : st.id;
		});
	}

	/** Average distance (horizontal) of a village's called-up soldiers to a point. */
	private static double soldierDistance(ServerLevel level, int village, int x, int z) {
		var st = com.stasdoto.airdefense.nation.Politics.get(level.getServer()).settlements.get(village);
		var list = com.stasdoto.airdefense.nation.Nations.soldiers(level, st);
		if (list.isEmpty()) {
			return -1;
		}
		double sum = 0;
		for (var e : list) {
			sum += Math.sqrt(Mth.square(e.getX() - x) + Mth.square(e.getZ() - z));
		}
		return sum / list.size();
	}

	private static int husk(TestServerContext server, double x, double y, double z, boolean helmet, boolean vest) {
		return server.computeOnServer(s -> {
			var h = net.minecraft.world.entity.EntityTypes.HUSK.create(s.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			h.snapTo(x, y, z, 0, 0);
			h.setNoAi(true);
			h.setPersistenceRequired();
			if (helmet) {
				h.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.HELMET));
			}
			if (vest) {
				h.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.stasdoto.airdefense.registry.ModItems.VEST));
			}
			s.overworld().addFreshEntity(h);
			return h.getId();
		});
	}

	/** Where the entity is (or the origin, if it is gone). */
	private static Vec3 entityPos(TestServerContext server, int id) {
		return server.computeOnServer(s -> s.overworld().getEntity(id) instanceof Entity e ? e.position() : Vec3.ZERO);
	}

	private static boolean alive(TestServerContext server, int id) {
		return server.computeOnServer(s -> s.overworld().getEntity(id) instanceof Entity e && e.isAlive());
	}

	private static void aimAt(ClientGameTestContext ctx, Vec3 target) {
		float[] yp = ctx.computeOnClient(mc -> {
			Vec3 d = target.subtract(mc.player.getEyePosition());
			return new float[]{(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))};
		});
		ctx.getInput().lookAt(yp[0], yp[1]);
	}

	private static void selectSlot(ClientGameTestContext ctx, int slot) {
		ctx.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
		ctx.waitTicks(3);
	}

	// --- helpers -----------------------------------------------------------------------------------

	private int spawnVehicle(TestServerContext server, VehicleType type, int x, int z, float yaw) {
		return server.computeOnServer(s -> VehicleEntity.spawn(s.overworld(), type, new Vec3(x + 0.5, ground, z + 0.5), yaw).getId());
	}

	private static void forVehicles(ServerLevel level, List<Integer> ids, java.util.function.Consumer<VehicleEntity> action) {
		for (int id : ids) {
			if (level.getEntity(id) instanceof VehicleEntity v) {
				action.accept(v);
			}
		}
	}

	private static void strike(TestServerContext server, int vehicleId, BlockPos target) {
		server.runOnServer(s -> {
			if (s.overworld().getEntity(vehicleId) instanceof VehicleEntity v) {
				boolean ok = v.commandStrike(target, null);
				AirDefense.LOGGER.info("[airdefense-test] strike from {} at {}: {}", v.getVehicleType(), target, ok);
			} else {
				AirDefense.LOGGER.error("[airdefense-test] no vehicle {}", vehicleId);
			}
		});
	}

	private static void camera(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f %.1f %.1f", x, y, z, yaw, pitch));
	}

	/** Waits until the condition holds or {@code maxTicks} pass; returns ticks waited. */
	private static int waitUntil(ClientGameTestContext ctx, java.util.function.BooleanSupplier condition, int maxTicks) {
		for (int i = 0; i < maxTicks; i++) {
			if (condition.getAsBoolean()) {
				return i;
			}
			ctx.waitTick();
		}
		AirDefense.LOGGER.warn("[airdefense-test] condition not met within {} ticks", maxTicks);
		return maxTicks;
	}
}
