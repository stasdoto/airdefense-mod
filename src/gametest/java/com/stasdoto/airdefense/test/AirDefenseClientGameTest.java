package com.stasdoto.airdefense.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.defense.DefenseBlock;
import com.stasdoto.airdefense.launcher.LauncherBlock;
import com.stasdoto.airdefense.launcher.LauncherBlockEntity;
import com.stasdoto.airdefense.missile.MissileStats;
import com.stasdoto.airdefense.registry.ModBlocks;

/**
 * Plays every system in a real client and records screenshots + counters:
 * line-up, an unopposed Iskander strike, Patriot vs Iskander, Gepard + IRIS-T vs a Shahed swarm,
 * NASAMS vs a HIMARS salvo, IRIS-T vs cruise missiles.
 */
@SuppressWarnings("UnstableApiUsage")
public class AirDefenseClientGameTest implements FabricClientGameTest {
	private int ground;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		System.setProperty("airdefense.debug", "true");
		ctx.runOnClient(mc -> {
			mc.options.renderDistance().set(12);
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("time set 1000");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			AirDefense.LOGGER.info("[airdefense-test] ground level {}", ground);

			lineup(ctx, server);
			unopposedIskander(ctx, server);
			patriotVsIskander(ctx, server);
			droneSwarm(ctx, server);
			server.runCommand("time set 13800");
			himarsVsNasams(ctx, server);
			server.runCommand("time set 1000");
			cruiseVsIrisT(ctx, server);

			AirDefense.LOGGER.info("[airdefense-test] SUMMARY {}", MissileStats.summary());
		}
	}

	// --- scenes ------------------------------------------------------------------------------------

	private void lineup(ClientGameTestContext ctx, TestServerContext server) {
		Block[] all = {ModBlocks.ISKANDER_LAUNCHER, ModBlocks.KALIBR_LAUNCHER, ModBlocks.SHAHED_LAUNCHER, ModBlocks.HIMARS,
				ModBlocks.PATRIOT, ModBlocks.IRIS_T, ModBlocks.NASAMS, ModBlocks.GEPARD};
		server.runOnServer(s -> {
			for (int i = 0; i < all.length; i++) {
				place(s.overworld(), new BlockPos(-14 + i * 4, ground, i % 2 == 0 ? 0 : 6), all[i], Direction.WEST);
			}
		});
		camera(server, 2, ground + 9, 26, 180, 25);
		ctx.waitTicks(80);
		ctx.takeScreenshot("01_lineup");
		camera(server, -20, ground + 5, 14, -135, 15);
		ctx.waitTicks(20);
		ctx.takeScreenshot("02_lineup_side");
		// Remove them so the air defence here doesn't interfere with later scenes.
		server.runOnServer(s -> {
			for (int i = 0; i < all.length; i++) {
				s.overworld().setBlock(new BlockPos(-14 + i * 4, ground, i % 2 == 0 ? 0 : 6), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		});
	}

	private void unopposedIskander(ClientGameTestContext ctx, TestServerContext server) {
		int x = 1500;
		BlockPos launcher = new BlockPos(x, ground, 0);
		BlockPos target = new BlockPos(x, ground - 1, 170);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			place(level, launcher, ModBlocks.ISKANDER_LAUNCHER, Direction.SOUTH);
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
		camera(server, x + 14, ground + 4, 14, 135, 20);
		ctx.waitTicks(60);
		int impacts = MissileStats.GROUND_IMPACTS.get();
		strike(server, launcher, target);
		ctx.waitTicks(8);
		ctx.takeScreenshot("10_iskander_launch");
		ctx.waitTicks(14);
		ctx.takeScreenshot("11_iskander_boost");
		camera(server, x + 40, ground + 20, 150, 120, -10);
		ctx.waitTicks(10);
		ctx.takeScreenshot("12_iskander_midcourse");
		waitUntil(ctx, () -> MissileStats.GROUND_IMPACTS.get() > impacts, 400);
		ctx.waitTicks(3);
		ctx.takeScreenshot("13_iskander_impact");
		ctx.waitTicks(30);
		ctx.takeScreenshot("14_iskander_smoke");
		ctx.waitTicks(80);
		camera(server, x + 16, ground + 12, 186, 135, 30);
		ctx.waitTicks(10);
		ctx.takeScreenshot("15_iskander_crater");
		AirDefense.LOGGER.info("[airdefense-test] after unopposed Iskander: {}", MissileStats.summary());
	}

	private void patriotVsIskander(ClientGameTestContext ctx, TestServerContext server) {
		int x = 3000;
		BlockPos launcher = new BlockPos(x, ground, -150);
		BlockPos target = new BlockPos(x, ground - 1, 90);
		BlockPos patriot = new BlockPos(x + 12, ground, 70);
		server.runOnServer(s -> {
			place(s.overworld(), launcher, ModBlocks.ISKANDER_LAUNCHER, Direction.SOUTH);
			place(s.overworld(), patriot, ModBlocks.PATRIOT, Direction.NORTH);
		});
		camera(server, x + 26, ground + 3, 92, 160, -18);
		ctx.waitTicks(60);
		int shot = MissileStats.THREATS_SHOT_DOWN.get();
		int impacts = MissileStats.GROUND_IMPACTS.get();
		strike(server, launcher, target);
		int t = waitUntil(ctx, () -> MissileStats.INTERCEPTORS_LAUNCHED.get() > 0, 300);
		ctx.waitTicks(4);
		ctx.takeScreenshot("20_patriot_launch");
		ctx.waitTicks(12);
		ctx.takeScreenshot("21_patriot_climb");
		waitUntil(ctx, () -> MissileStats.THREATS_SHOT_DOWN.get() > shot || MissileStats.GROUND_IMPACTS.get() > impacts, 400);
		ctx.waitTicks(2);
		ctx.takeScreenshot("22_patriot_intercept");
		ctx.waitTicks(25);
		ctx.takeScreenshot("23_patriot_after");
		// The launcher fires a second missile 70 ticks later; let the whole salvo play out.
		ctx.waitTicks(300);
		ctx.takeScreenshot("24_patriot_second");
		AirDefense.LOGGER.info("[airdefense-test] after Patriot vs Iskander (first interceptor at {} ticks): {}", t, MissileStats.summary());
	}

	private void droneSwarm(ClientGameTestContext ctx, TestServerContext server) {
		int x = 4500;
		BlockPos launcher = new BlockPos(x, ground, -180);
		BlockPos target = new BlockPos(x, ground - 1, 60);
		server.runOnServer(s -> {
			place(s.overworld(), launcher, ModBlocks.SHAHED_LAUNCHER, Direction.SOUTH);
			place(s.overworld(), new BlockPos(x + 8, ground, 40), ModBlocks.GEPARD, Direction.NORTH);
		});
		camera(server, x + 4, ground + 6, -165, 180, 10);
		ctx.waitTicks(60);
		strike(server, launcher, target);
		ctx.waitTicks(30);
		ctx.takeScreenshot("30_shahed_takeoff");
		ctx.waitTicks(60);
		camera(server, x + 18, ground + 4, 62, 160, -12);
		for (int i = 0; i < 6; i++) {
			ctx.waitTicks(i < 2 ? 70 : 30);
			ctx.takeScreenshot("3" + (i + 1) + "_shahed_defense");
		}
		ctx.waitTicks(200);
		AirDefense.LOGGER.info("[airdefense-test] after Shahed swarm: {}", MissileStats.summary());
	}

	private void himarsVsNasams(ClientGameTestContext ctx, TestServerContext server) {
		int x = 6000;
		BlockPos launcher = new BlockPos(x, ground, -140);
		BlockPos target = new BlockPos(x, ground - 1, 80);
		server.runOnServer(s -> {
			place(s.overworld(), launcher, ModBlocks.HIMARS, Direction.SOUTH);
			place(s.overworld(), new BlockPos(x + 10, ground, 64), ModBlocks.NASAMS, Direction.NORTH);
		});
		camera(server, x + 10, ground + 4, -126, 150, 5);
		ctx.waitTicks(60);
		strike(server, launcher, target);
		ctx.waitTicks(12);
		ctx.takeScreenshot("40_himars_salvo");
		ctx.waitTicks(20);
		ctx.takeScreenshot("41_himars_salvo2");
		camera(server, x + 30, ground + 6, 96, 140, -15);
		ctx.waitTicks(40);
		ctx.takeScreenshot("42_himars_nasams");
		ctx.waitTicks(40);
		ctx.takeScreenshot("43_himars_impacts");
		ctx.waitTicks(200);
		AirDefense.LOGGER.info("[airdefense-test] after HIMARS vs NASAMS: {}", MissileStats.summary());
	}

	private void cruiseVsIrisT(ClientGameTestContext ctx, TestServerContext server) {
		int x = 7500;
		BlockPos launcher = new BlockPos(x, ground, -160);
		BlockPos target = new BlockPos(x, ground - 1, 80);
		server.runOnServer(s -> {
			place(s.overworld(), launcher, ModBlocks.KALIBR_LAUNCHER, Direction.SOUTH);
			place(s.overworld(), new BlockPos(x + 10, ground, 50), ModBlocks.IRIS_T, Direction.NORTH);
		});
		camera(server, x + 12, ground + 5, -146, 150, 0);
		ctx.waitTicks(60);
		strike(server, launcher, target);
		ctx.waitTicks(15);
		ctx.takeScreenshot("50_kalibr_launch");
		camera(server, x + 24, ground + 8, 70, 160, -8);
		ctx.waitTicks(80);
		ctx.takeScreenshot("51_kalibr_cruise");
		ctx.waitTicks(60);
		ctx.takeScreenshot("52_kalibr_iris");
		ctx.waitTicks(260);
		AirDefense.LOGGER.info("[airdefense-test] after Kalibr vs IRIS-T: {}", MissileStats.summary());
	}

	// --- helpers -----------------------------------------------------------------------------------

	private static void place(ServerLevel level, BlockPos pos, Block block, Direction facing) {
		var state = block.defaultBlockState();
		if (block instanceof LauncherBlock) {
			state = state.setValue(LauncherBlock.FACING, facing);
		} else if (block instanceof DefenseBlock) {
			state = state.setValue(DefenseBlock.FACING, facing);
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	private static void strike(TestServerContext server, BlockPos launcher, BlockPos target) {
		server.runOnServer(s -> {
			if (s.overworld().getBlockEntity(launcher) instanceof LauncherBlockEntity be) {
				boolean ok = be.commandStrike(target);
				AirDefense.LOGGER.info("[airdefense-test] strike from {} at {}: {}", launcher, target, ok);
			} else {
				AirDefense.LOGGER.error("[airdefense-test] no launcher at {}", launcher);
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
