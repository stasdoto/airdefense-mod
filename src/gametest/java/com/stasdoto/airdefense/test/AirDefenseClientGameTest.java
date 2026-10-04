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

			lineup(ctx, server);
			drive(ctx, server);
			tabletMap(ctx, server);
			unopposedIskander(ctx, server);
			effectsCloseup(ctx, server, false);
			server.runCommand("time set 14500");
			effectsCloseup(ctx, server, true);
			server.runCommand("time set 1000");
			patriotVsIskander(ctx, server);
			droneSwarm(ctx, server);
			server.runCommand("time set 15000");
			himarsVsNasams(ctx, server);
			server.runCommand("time set 1000");
			droneVsNasams(ctx, server);
			cruiseVsIrisT(ctx, server);
			droneVsIrisT(ctx, server);

			AirDefense.LOGGER.info("[airdefense-test] SUMMARY {}", MissileStats.summary());
		}
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
				MissileStats.INTERCEPTORS_LAUNCHED.get(), MissileStats.INTERCEPTOR_CRASHES.get()};
	}

	private static void report(String scene, int[] before) {
		int[] now = counters();
		AirDefense.LOGGER.info("[airdefense-test] RESULT {}: threats={} shotDown={} reachedGround={} interceptors={} interceptorCrashes={}",
				scene, now[0] - before[0], now[1] - before[1], now[2] - before[2], now[3] - before[3], now[4] - before[4]);
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
		// Some landmarks so the map has something to show: a lake, a road, buildings, a wood.
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 60, g - 1, 20, x - 25, g - 1, 55));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:gravel", x - 150, g - 1, -3, x + 150, g - 1, 2));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks hollow", x + 40, g, 25, x + 52, g + 6, 37));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:bricks hollow", x + 60, g, 30, x + 66, g + 4, 50));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves", x - 90, g, -60, x - 50, g + 3, -25));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:sand", x + 80, g - 1, -80, x + 120, g - 1, -40));
		server.runCommand("gamemode creative @a");
		camera(server, x, g + 1, 70, 180, 0);
		ctx.waitTicks(40);
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
			boolean off = ctx.tryClickScreenButton("Air defence: auto (turn off)");
			AirDefense.LOGGER.info("[airdefense-test] AD mode button pressed: {}", off);
			ctx.waitTicks(20);
			int mode = server.computeOnServer(s -> s.overworld().getEntity(patriot) instanceof VehicleEntity v ? v.getMode() : -1);
			AirDefense.LOGGER.info("[airdefense-test] RESULT map_mode: patriot mode after the button = {} (0 = off)", mode);
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
