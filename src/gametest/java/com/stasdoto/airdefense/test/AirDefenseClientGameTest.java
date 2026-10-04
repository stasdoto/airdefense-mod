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

			// AIRDEFENSE_SCENES (from the workflow) picks scenes for a quick run; empty = everything.
			if (scene("lineup")) {
				lineup(ctx, server);
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
		BlockPos house = server.computeOnServer(s -> {
			var st = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(id);
			return st.eco.buildings.isEmpty() ? BlockPos.ZERO : st.eco.buildings.getFirst().middle();
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
