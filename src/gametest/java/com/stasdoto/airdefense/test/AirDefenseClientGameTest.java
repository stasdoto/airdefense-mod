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
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
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
			if (scene("probe")) {
				server.runOnServer(s -> {
					var ra = s.registryAccess();
					var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, ra);
					for (String id : new String[]{"trees_plains", "freeze_top_layer", "lake_lava_surface"}) {
						var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,
								net.minecraft.resources.Identifier.withDefaultNamespace(id));
						ra.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE).get(key).ifPresent(h -> {
							AirDefense.LOGGER.info("[airdefense-test] PROBE placed {} = {}", id,
									net.minecraft.world.level.levelgen.placement.PlacedFeature.DIRECT_CODEC.encodeStart(ops, h.value()));
							AirDefense.LOGGER.info("[airdefense-test] PROBE feature {} = {}", id,
									net.minecraft.world.level.levelgen.feature.Feature.DIRECT_CODEC.encodeStart(ops, h.value().feature().value()));
						});
					}
					var sk = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,
							net.minecraft.resources.Identifier.withDefaultNamespace("villages"));
					ra.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).get(sk).ifPresent(h -> AirDefense.LOGGER.info(
							"[airdefense-test] PROBE structure_set villages = {}",
							net.minecraft.world.level.levelgen.structure.StructureSet.DIRECT_CODEC.encodeStart(ops, h.value())));
				});
			}
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
				ctx.runOnClient(mc -> {
					if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) {
						b.setPage(14);
					}
				});
				ctx.waitTicks(5);
				ctx.takeScreenshot("000_guide_15");
				ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
				ctx.waitTicks(5);
			}

			// AIRDEFENSE_SCENES (from the workflow) picks scenes for a quick run; empty = everything.
			if (scene("lite")) {
				lite(ctx, server);
			}
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
			if (scene("showroom")) {
				showroom(ctx, server);
			}


			if (scene("armor")) {
				armor(ctx, server);
			}
			if (scene("air")) {
				aircraft(ctx, server);
			}

			if (scene("fpv")) {
				pilotedDrones(ctx, server);
			}


			if (scene("drive")) {
				drive(ctx, server);
			}
			if (scene("physics")) {
				physics(ctx, server);
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

			if (scene("rifleDrone")) {
				rifleVsShahed(ctx, server);
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
			if (scene("threeVsOne")) {
				threeVsOne(ctx, server);
			}
			if (scene("samePoint")) {
				samePoint(ctx, server);
			}
			if (scene("manual")) {
				manualDefense(ctx, server, VehicleType.GEPARD, 16500, "manual_gepard");
				manualDefense(ctx, server, VehicleType.IRIS_T, 18000, "manual_iris_t");
			}
			if (scene("crew")) {
				crew(ctx, server);
			}

			if (scene("vehsounds")) {
				vehSounds(ctx, server);
			}


			if (scene("arty")) {
				arty(ctx, server);
			}
			if (scene("armor2")) {
				armor2(ctx, server);
			}

			if (scene("gallery")) {
				gallery(ctx, server);
			}

			AirDefense.LOGGER.info("[airdefense-test] SUMMARY {}", MissileStats.summary());
		}

		if (scene("liteworld")) {
			liteWorld(ctx);
		}
	}

	/** Looks at a prop {x, y, z} from {@code dist} blocks off (south-east of it), {@code up} above it and the ground. */
	private static void propCam(ClientGameTestContext ctx, TestServerContext server, double[] p, double dist, double up) {
		if (p == null) {
			return;
		}
		double fx = p[0] + dist * 0.8;
		double fz = p[2] + dist * 0.6;
		int g = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(fx), (int) Math.floor(fz)));
		look(server, fx, Math.max(p[1] + up, g + 2), fz, p[0], p[1] + 1.5, p[2]);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		ctx.waitTicks(2);
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

	/** 1.44: the vehicles close up in daylight, from the front quarter and the back quarter (to judge their looks). */
	private void showroom(ClientGameTestContext ctx, TestServerContext server) {
		VehicleType[] show = {VehicleType.T72, VehicleType.T90, VehicleType.T80BVM, VehicleType.LEOPARD2, VehicleType.ABRAMS, VehicleType.CHALLENGER2,
				VehicleType.BMP2, VehicleType.BMP3, VehicleType.BRADLEY, VehicleType.CV90, VehicleType.BTR82, VehicleType.HMMWV};
		server.runCommand("time set 6000");
		server.runCommand("gamerule advance_time false");
		ctx.runOnClient(mc -> mc.gui.hud.toggle());
		int x0 = 61000;
		for (int i = 0; i < show.length; i++) {
			VehicleType t = show[i];
			int x = x0 + i * 40;
			int id = spawnVehicle(server, t, x, 0, 0);
			look(server, x + 5.0, ground + 2.6, 6.2, x + 0.5, ground + 1.3, 0.8);
			ctx.waitTicks(i == 0 ? 120 : 50);
			ctx.takeScreenshot("sr_" + t.name().toLowerCase(java.util.Locale.ROOT) + "_front");
			look(server, x - 5.0, ground + 3.4, -5.8, x + 0.5, ground + 1.3, 0.0);
			ctx.waitTicks(12);
			ctx.takeScreenshot("sr_" + t.name().toLowerCase(java.util.Locale.ROOT) + "_back");
			server.runOnServer(s -> {
				Entity e = s.overworld().getEntity(id);
				if (e != null) {
					e.discard();
				}
			});
		}
		ctx.runOnClient(mc -> mc.gui.hud.toggle());
		server.runCommand("gamerule advance_time true");
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
		double alt0 = server.computeOnServer(s -> s.overworld().getEntity(heli).getY() - ground);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(60);
		ctx.takeScreenshot("b2_mi24_flying");
		// 1.25: the flight model - nose down, speed builds up; bank sideways; let go and it levels out and holds height.
		String flying = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v
				? String.format(java.util.Locale.ROOT, "speed %.2f pitch %.1f", v.getDeltaMovement().horizontalDistance(), v.heliPitch) : "-");
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.getInput().holdKey(o -> o.keyRight);
		ctx.waitTicks(30);
		ctx.takeScreenshot("b2b_mi24_bank");
		String bank = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v
				? String.format(java.util.Locale.ROOT, "roll %.1f", v.heliRoll) : "-");
		ctx.getInput().releaseKey(o -> o.keyRight);
		ctx.waitTicks(120);
		String level = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v
				? String.format(java.util.Locale.ROOT, "speed %.2f pitch %.1f roll %.1f", v.getDeltaMovement().horizontalDistance(), v.heliPitch, v.heliRoll) : "-");
		double alt = server.computeOnServer(s -> s.overworld().getEntity(heli).getY() - ground);
		AirDefense.LOGGER.info("[airdefense-test] RESULT heli_flight: climbed to {}, forward: {}, bank: {}, let go: {}, height now {}",
				String.format(java.util.Locale.ROOT, "%.1f", alt0), flying, bank, level, String.format(java.util.Locale.ROOT, "%.1f", alt));
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

	/**
	 * 1.26 "Crew": the T-72 gunner's sight (day, magnified, thermal) and the seat view; the insides of a truck's cab,
	 * an armoured car and the cockpits; a Su-25 flown with the new controls (take-off, a banked turn) with its head-up
	 * display.
	 */
	private void crew(ClientGameTestContext ctx, TestServerContext server) {
		int x = 60000;
		camera(server, x, ground + 2, -6, 0, 5);
		ctx.waitTicks(40);
		// Targets down range: a BTR, a lorry, a few people.
		int tank = spawnVehicle(server, VehicleType.T72, x, 0, 0);
		int btr = spawnVehicle(server, VehicleType.BTR82, x + 6, 110, 60);
		int lorry = spawnVehicle(server, VehicleType.SUPPLY_TRUCK, x - 14, 160, 120);
		server.runOnServer(s -> {
			for (int i = 0; i < 4; i++) {
				var vill = net.minecraft.world.entity.EntityTypes.VILLAGER.create(s.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				if (vill != null) {
					vill.setPos(x - 4 + i * 3, ground, 80);
					vill.setNoAi(true);
					s.overworld().addFreshEntity(vill);
				}
			}
		});
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			if (s.overworld().getEntity(tank) instanceof VehicleEntity v) {
				s.getPlayerList().getPlayers().getFirst().startRiding(v);
			}
		});
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		ctx.waitTicks(30);
		ctx.getInput().lookAt(0, 2);
		ctx.waitTicks(40);
		ctx.takeScreenshot("c1_t72_sight");
		String sight = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.vehicle.GunnerSight.active() + " frames "
				+ com.stasdoto.airdefense.client.vehicle.GunnerSight.framesInSight);
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.ZOOM);
		ctx.waitTicks(20);
		ctx.takeScreenshot("c2_t72_sight_zoom");
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.THERMAL);
		ctx.waitTicks(20);
		ctx.takeScreenshot("c3_t72_thermal");
		server.runCommand("time set 15000");
		ctx.waitTicks(20);
		ctx.takeScreenshot("c3b_t72_thermal_night");
		server.runCommand("time set 1000");
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.THERMAL);
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.ZOOM);
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.ZOOM);
		// Turn the turret: the sight swings at once, the gun follows.
		ctx.getInput().lookAt(40, 2);
		ctx.waitTicks(6);
		ctx.takeScreenshot("c4_t72_sight_turning");
		ctx.waitTicks(60);
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.VIEW);
		ctx.waitTicks(20);
		ctx.takeScreenshot("c5_t72_hatch");
		String seat = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.vehicle.GunnerSight.active().toString());
		// Smoke grenades: a wall of smoke ahead of the turret.
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.SMOKE);
		ctx.waitTicks(40);
		ctx.takeScreenshot("c5b_t72_smoke");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		int smokes = server.computeOnServer(s -> com.stasdoto.airdefense.fx.Smoke.laid);
		AirDefense.LOGGER.info("[airdefense-test] RESULT crew_sight: in the tank {}; after C: {}; smoke clouds laid {}", sight, seat, smokes);
		leave(ctx);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(tank, btr, lorry), Entity::discard));

		// The insides: sit in the seat, look ahead and to the side; one look from outside through the glass.
		VehicleType[] cabs = {VehicleType.SUPPLY_TRUCK, VehicleType.MAXXPRO, VehicleType.PATRIOT, VehicleType.MI8, VehicleType.MI24, VehicleType.KA52,
				VehicleType.SU25, VehicleType.F16};
		int cx = x + 1500;
		for (int i = 0; i < cabs.length; i++) {
			VehicleType t = cabs[i];
			camera(server, cx + i * 40, ground + 2, -6, 0, 5);
			ctx.waitTicks(30);
			int id = spawnVehicle(server, t, cx + i * 40, 0, 0);
			server.runOnServer(s -> {
				if (s.overworld().getEntity(id) instanceof VehicleEntity v) {
					s.getPlayerList().getPlayers().getFirst().startRiding(v);
				}
			});
			ctx.waitTicks(20);
			ctx.getInput().lookAt(0, 12);
			ctx.waitTicks(15);
			ctx.takeScreenshot("c6_" + t.id + "_inside");
			ctx.getInput().lookAt(t.isAir() ? -55 : 55, 18);
			ctx.waitTicks(15);
			ctx.takeScreenshot("c7_" + t.id + "_inside_side");
			leave(ctx);
			if (i == 0 || i == 3) {
				Vec3 at = server.computeOnServer(s -> s.overworld().getEntity(id).position());
				camera(server, at.x - 3.2, ground + 2.6, at.z + 2.0, -120, 8);
				ctx.waitTicks(20);
				ctx.takeScreenshot("c8_" + t.id + "_through_glass");
			}
			server.runOnServer(s -> forVehicles(s.overworld(), List.of(id), Entity::discard));
		}

		// The Su-25 with the new controls: throttle up, rotate, climb; a banked turn to the right; the head-up display.
		int px = x + 3000;
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
		ctx.waitTicks(140);
		ctx.getInput().lookAt(0, -18);
		ctx.waitTicks(120);
		ctx.takeScreenshot("c9_su25_climb_hud");
		String climb = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v
				? String.format(java.util.Locale.ROOT, "alt %.1f speed %.2f pitch %.1f thr %.2f", v.getY() - ground, v.getDeltaMovement().length(), v.getXRot(), v.throttle) : "-");
		ctx.getInput().lookAt(0, 0);
		ctx.waitTicks(40);
		float yaw0 = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v ? v.getYRot() : 0f);
		ctx.getInput().lookAt(70, 0);
		ctx.waitTicks(25);
		String turning = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v
				? String.format(java.util.Locale.ROOT, "roll %.1f yaw %.1f", v.planeRoll, v.getYRot()) : "-");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(3);
		ctx.takeScreenshot("c10_su25_banked");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		ctx.waitTicks(80);
		String after = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v
				? String.format(java.util.Locale.ROOT, "roll %.1f yaw %.1f alt %.1f speed %.2f", v.planeRoll, v.getYRot(), v.getY() - ground,
				v.getDeltaMovement().length()) : "-");
		ctx.getInput().lookAt(70, 30);
		ctx.waitTicks(30);
		ctx.takeScreenshot("c11_su25_bomb_sight");
		ctx.getInput().releaseKey(o -> o.keyUp);
		AirDefense.LOGGER.info("[airdefense-test] RESULT crew_plane: climb {}; turn from yaw {}: {}; after: {}", climb, yaw0, turning, after);
		leave(ctx);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode spectator @a");
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(plane), Entity::discard));
	}

	/** Switches the game's language (the pictures for the player are in Russian; the rest of the tests read English). */
	private static void language(ClientGameTestContext ctx, String code) {
		java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>> f = new java.util.concurrent.atomic.AtomicReference<>();
		ctx.runOnClient(mc -> {
			mc.options.languageCode = code;
			mc.getLanguageManager().setSelected(code);
			f.set(mc.reloadResourcePacks());
		});
		int t = waitUntil(ctx, () -> f.get() != null && f.get().isDone(), 2400);
		// The loading screen fades out over a second or two.
		ctx.waitTicks(80);
		AirDefense.LOGGER.info("[airdefense-test] language {} after {} ticks", code, t);
	}

	/** The camera at {@code from}, looking at {@code to}. */
	private static void look(TestServerContext server, double fx, double fy, double fz, double tx, double ty, double tz) {
		double dx = tx - fx;
		double dz = tz - fz;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) Math.toDegrees(Math.atan2(-(ty - fy), Math.sqrt(dx * dx + dz * dz)));
		camera(server, fx, fy, fz, yaw, pitch);
	}

	/** Mean and largest distance from {@code c} of the artillery rounds that came down after the {@code from}-th. */
	private static double[] spread(int from, BlockPos c) {
		List<Vec3> all;
		synchronized (com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED) {
			all = new ArrayList<>(com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED);
		}
		double sum = 0;
		double max = 0;
		int n = 0;
		for (int i = from; i < all.size(); i++) {
			double d = Math.sqrt(Mth.square(all.get(i).x - c.getX() - 0.5) + Mth.square(all.get(i).z - c.getZ() - 0.5));
			sum += d;
			max = Math.max(max, d);
			n++;
		}
		return new double[]{n, n == 0 ? -1 : sum / n, max};
	}

	/**
	 * 1.30: artillery - the guns and radars lined up and deployed, a Msta-S fire mission (the muzzle blast, where the
	 * shells fall, their whistle), a Grad salvo, counter-battery work (an enemy Paladin found by the player's radar and
	 * answered by his Msta-S on its own), the tablet's map with the enemy battery marked.
	 */
	private void arty(ClientGameTestContext ctx, TestServerContext server) {
		int x = 100000;
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 3000");
		server.runCommand("weather clear");
		camera(server, x + 0.5, ground + 6, -30, 0, 10);
		ctx.waitTicks(40);
		VehicleType[] line = {VehicleType.MSTA_S, VehicleType.M109, VehicleType.BM21, VehicleType.ZOOPARK, VehicleType.TPQ36};
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < line.length; i++) {
			ids.add(spawnVehicle(server, line[i], x - 24 + i * 10, 0, 0));
		}
		server.runOnServer(s -> forVehicles(s.overworld(), ids, v -> v.country = -1));
		ctx.waitTicks(30);
		look(server, x - 24, ground + 3.5, -12, x - 9, ground + 1.2, 0);
		ctx.waitTicks(30);
		ctx.takeScreenshot("a1_arty_lineup");
		server.runOnServer(s -> forVehicles(s.overworld(), ids, v -> {
			if (v.getVehicleType().isArtillery()) {
				v.raiseLauncher();
			}
		}));
		ctx.waitTicks(120);
		look(server, x + 24, ground + 6, 19, x - 6, ground + 2.5, 0);
		ctx.waitTicks(30);
		ctx.takeScreenshot("a2_arty_deployed");
		String deployed = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			forVehicles(s.overworld(), ids, v -> b.append(v.getVehicleType().id).append(" elev ").append((int) v.elevation)
					.append(v.getVehicleType().isRadar() ? (v.radarWorking() ? " working" : " not working") : "").append("; "));
			return b.toString();
		});

		// The Msta-S fires six at a field 520 blocks ahead.
		int msta = ids.get(0);
		BlockPos fieldA = new BlockPos(x - 24, ground, 520);
		int r0 = VehicleEntity.artilleryRounds;
		int landed0 = com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size();
		int whistles0 = com.stasdoto.airdefense.missile.MissileEntity.WHISTLES.get();
		boolean ordered = server.computeOnServer(s -> s.overworld().getEntity(msta) instanceof VehicleEntity v && v.commandFire(fieldA, null, 6));
		look(server, x - 36, ground + 4, -10, x - 24, ground + 3, 4);
		int aimed = waitUntil(ctx, () -> VehicleEntity.artilleryRounds > r0, 400);
		ctx.waitTicks(2);
		ctx.takeScreenshot("a3_msta_fire");
		ctx.waitTicks(10);
		ctx.takeScreenshot("a4_msta_smoke");
		look(server, x + 6, ground + 12, 478, x - 24, ground + 2, 520);
		int flight = waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size() > landed0, 700);
		ctx.waitTicks(4);
		ctx.takeScreenshot("a5_shells_land");
		waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size() >= landed0 + 6, 700);
		ctx.waitTicks(30);
		ctx.takeScreenshot("a5b_craters");
		double[] spreadA = spread(landed0, fieldA);
		AirDefense.LOGGER.info("[airdefense-test] RESULT arty_msta: ordered {} aimed after {} ticks, fired {}, first down {} ticks later, landed {} "
						+ "mean {} max {} blocks from the aim, whistles {} | {}", ordered, aimed, VehicleEntity.artilleryRounds - r0, flight,
				(int) spreadA[0], String.format(java.util.Locale.ROOT, "%.1f", spreadA[1]), String.format(java.util.Locale.ROOT, "%.1f", spreadA[2]),
				com.stasdoto.airdefense.missile.MissileEntity.WHISTLES.get() - whistles0, deployed);

		// The Grad ripples off all forty rockets at a field 700 blocks off.
		int bm = ids.get(2);
		BlockPos fieldB = new BlockPos(x + 160, ground, 700);
		int r1 = VehicleEntity.artilleryRounds;
		int landed1 = com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size();
		look(server, x + 18, ground + 3, -14, x, ground + 2.5, 0);
		ctx.waitTicks(10);
		boolean gradOrdered = server.computeOnServer(s -> s.overworld().getEntity(bm) instanceof VehicleEntity v && v.commandFire(fieldB, null, 0));
		waitUntil(ctx, () -> VehicleEntity.artilleryRounds > r1 + 8, 400);
		ctx.takeScreenshot("a6_grad_salvo");
		look(server, x + 205, ground + 20, 640, x + 160, ground + 2, 700);
		waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size() > landed1 + 10, 900);
		ctx.waitTicks(6);
		ctx.takeScreenshot("a7_grad_impacts");
		waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size() >= landed1 + 40, 600);
		ctx.waitTicks(40);
		ctx.takeScreenshot("a7b_grad_field");
		double[] spreadB = spread(landed1, fieldB);
		AirDefense.LOGGER.info("[airdefense-test] RESULT arty_grad: ordered {} fired {} landed {} mean {} max {} blocks from the aim", gradOrdered,
				VehicleEntity.artilleryRounds - r1, (int) spreadB[0], String.format(java.util.Locale.ROOT, "%.1f", spreadB[1]),
				String.format(java.util.Locale.ROOT, "%.1f", spreadB[2]));
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));

		// Counter-battery: the player's Zoopark looks ahead, his Msta-S beside it answers fire; an enemy Paladin 700
		// blocks ahead shells a point near them.
		int cx = x + 3000;
		camera(server, cx + 0.5, ground + 6, -30, 0, 10);
		ctx.waitTicks(40);
		server.runCommand(String.format("forceload add %d %d %d %d", cx + 20, 680, cx + 60, 720));
		ctx.waitTicks(20);
		int radar = spawnVehicle(server, VehicleType.ZOOPARK, cx, 0, 0);
		int gun = spawnVehicle(server, VehicleType.MSTA_S, cx - 30, -20, 0);
		int enemy = spawnVehicle(server, VehicleType.M109, cx + 40, 700, 180);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(radar, gun), v -> {
			v.country = -1;
			v.setModeByOrder(VehicleEntity.MODE_AUTO, null);
		}));
		ctx.waitTicks(120);
		int found0 = com.stasdoto.airdefense.radar.CounterBattery.found;
		int answered0 = com.stasdoto.airdefense.radar.CounterBattery.answered;
		float hp0 = server.computeOnServer(s -> s.overworld().getEntity(enemy) instanceof VehicleEntity v ? v.getHealth() : -1f);
		int landed2 = com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size();
		boolean enemyFired = server.computeOnServer(s -> s.overworld().getEntity(enemy) instanceof VehicleEntity v
				&& v.commandFire(new BlockPos(cx + 12, ground, 70), null, 3));
		look(server, cx + 14, ground + 7, -16, cx - 6, ground + 4, 4);
		int foundAfter = waitUntil(ctx, () -> com.stasdoto.airdefense.radar.CounterBattery.found > found0, 800);
		ctx.takeScreenshot("a8_zoopark_found");
		int answeredAfter = waitUntil(ctx, () -> com.stasdoto.airdefense.radar.CounterBattery.answered > answered0, 300);
		waitUntil(ctx, () -> VehicleEntity.artilleryRounds > 0 && server.computeOnServer(s -> s.overworld().getEntity(gun) instanceof VehicleEntity v
				&& v.striking() && v.loadedRounds() < v.getVehicleType().strikeLoad()), 300);
		ctx.waitTicks(2);
		ctx.takeScreenshot("a8b_answer_fire");
		look(server, cx + 16, ground + 7, 668, cx + 40, ground + 1.5, 700);
		Vec3 enemyAt = new Vec3(cx + 40.5, ground, 700.5);
		int hitWait = waitUntil(ctx, () -> {
			List<Vec3> all;
			synchronized (com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED) {
				all = new ArrayList<>(com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED);
			}
			for (int i = landed2; i < all.size(); i++) {
				if (all.get(i).distanceTo(enemyAt) < 120) {
					return true;
				}
			}
			return false;
		}, 900);
		ctx.waitTicks(4);
		ctx.takeScreenshot("a8c_enemy_battery_hit");
		ctx.waitTicks(200);
		double nearest = 1e9;
		synchronized (com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED) {
			for (int i = landed2; i < com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size(); i++) {
				nearest = Math.min(nearest, com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.get(i).distanceTo(enemyAt));
			}
		}
		float hp1 = server.computeOnServer(s -> s.overworld().getEntity(enemy) instanceof VehicleEntity v ? v.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT arty_cb: enemy fired {}, found after {} ticks ({}), answered after {} ({}), "
						+ "answer near the enemy after {} ticks, nearest {} blocks, enemy health {} -> {}; rounds lost in flight {}", enemyFired, foundAfter,
				com.stasdoto.airdefense.radar.CounterBattery.found - found0, answeredAfter,
				com.stasdoto.airdefense.radar.CounterBattery.answered - answered0, hitWait, (int) nearest, hp0, hp1,
				com.stasdoto.airdefense.missile.MissileEntity.LOST.get());

		// The tablet's map: the gun selected, its reach, the enemy battery's red cross.
		server.runCommand("gamemode creative @a");
		camera(server, cx - 10.5, ground, -30.5, 0, 0);
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(40);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.runOnClient(mc -> {
			var sc = (com.stasdoto.airdefense.client.map.TacticalMapScreen) mc.gui.screen();
			sc.select(gun);
			sc.pickPoint(cx + 40, 700);
			sc.centerOn(cx, 340, 4);
		});
		ctx.waitTicks(40);
		ctx.takeScreenshot("a9_map_counter_battery");
		int fires = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.fires().size());
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		language(ctx, "en_us");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		AirDefense.LOGGER.info("[airdefense-test] RESULT arty_map: enemy batteries on the map {}", fires);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(radar, gun, enemy), Entity::discard));
		server.runCommand(String.format("forceload remove %d %d %d %d", cx + 20, 680, cx + 60, 720));
	}

	/** An enemy aircraft on its sortie (the first one found), or null. */
	@org.jetbrains.annotations.Nullable
	private static VehicleEntity sortie(ServerLevel level) {
		for (Entity e : level.getAllEntities()) {
			if (e instanceof VehicleEntity v && v.isAlive() && v.onSortie()) {
				return v;
			}
		}
		return null;
	}

	/** 1.33: the first warship sailing a naval raid (null if none). */
	private static VehicleEntity raider(ServerLevel level) {
		for (Entity e : level.getAllEntities()) {
			if (e instanceof VehicleEntity v && v.isAlive() && v.onRaid()) {
				return v;
			}
		}
		return null;
	}

	/** 1.34: a drone of this type in flight near {@code x} (the scene's), or null. */
	@org.jetbrains.annotations.Nullable
	private static MissileEntity droneOf(ServerLevel level, MissileType type, int x) {
		for (MissileEntity m : MissileEntity.find(level, new net.minecraft.world.phys.AABB(x - 3000, -64, -3000, x + 3000, 600, 3000),
				m -> m.isAlive() && m.getMissileType() == type)) {
			return m;
		}
		return null;
	}

	/**
	 * 1.31: the new armour lined up; a recovery vehicle mending a damaged tank, a repair kit used by hand, a hangar-less
	 * check of the numbers; a TOS-1A salvo.
	 */
	private void armor2(ClientGameTestContext ctx, TestServerContext server) {
		int x = 110000;
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 3000");
		camera(server, x + 0.5, ground + 6, -30, 0, 10);
		ctx.waitTicks(40);
		// Four rows: the tanks, the fighting vehicles, the wheeled ones, the recovery vehicles and the TOS - each shot close.
		VehicleType[][] rows = {{VehicleType.T80BVM, VehicleType.CHALLENGER2}, {VehicleType.BMP3, VehicleType.CV90},
				{VehicleType.STRYKER, VehicleType.TIGR, VehicleType.HMMWV}, {VehicleType.BREM1, VehicleType.M88, VehicleType.TOS1}};
		String[] names = {"b1_tanks", "b2_ifv", "b3_wheeled", "b4_recovery_tos"};
		List<Integer> ids = new ArrayList<>();
		for (int r = 0; r < rows.length; r++) {
			for (int i = 0; i < rows[r].length; i++) {
				ids.add(spawnVehicle(server, rows[r][i], x - 6 + i * 10, -r * 40, 0));
			}
		}
		server.runOnServer(s -> forVehicles(s.overworld(), ids, v -> v.country = -1));
		ctx.waitTicks(30);
		for (int r = 0; r < rows.length; r++) {
			int z = -r * 40;
			int n = rows[r].length;
			look(server, x - 16, ground + 3.5, z + 11, x - 6 + (n - 1) * 5, ground + 1.2, z - 1);
			ctx.waitTicks(25);
			ctx.takeScreenshot(names[r]);
		}
		look(server, x + 22, ground + 4, -14, x + 2, ground + 1.2, -2);
		ctx.waitTicks(20);
		ctx.takeScreenshot("b5_tanks_back");
		server.runOnServer(s -> forVehicles(s.overworld(), ids, Entity::discard));

		// A recovery vehicle mends a damaged tank beside it.
		int rx = x + 400;
		camera(server, rx + 0.5, ground + 6, -20, 0, 10);
		ctx.waitTicks(30);
		int tank = spawnVehicle(server, VehicleType.T80BVM, rx, 0, 0);
		int brem = spawnVehicle(server, VehicleType.BREM1, rx + 7, -4, 0);
		int kitTarget = spawnVehicle(server, VehicleType.CHALLENGER2, rx - 30, 0, 0);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(tank, brem, kitTarget), v -> {
			v.country = -1;
			if (v.getVehicleType() != VehicleType.BREM1) {
				v.setHealth(v.getMaxHealth() * 0.4f);
			}
		}));
		float before = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getHealth() : -1f);
		look(server, rx + 14, ground + 4, 10, rx + 3, ground + 1, -2);
		ctx.waitTicks(200);
		ctx.takeScreenshot("b6_recovery_mends");
		float after = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getHealth() : -1f);
		float max = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getMaxHealth() : -1f);
		// A repair kit by hand: the player stands by the Challenger and right-clicks it.
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:repair_kit 3");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		camera(server, rx - 29.5, ground, -3.9, 0, 20);
		ctx.waitTicks(30);
		float kit0 = server.computeOnServer(s -> s.overworld().getEntity(kitTarget) instanceof VehicleEntity v ? v.getHealth() : -1f);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(10);
		ctx.takeScreenshot("b7_repair_kit");
		float kit1 = server.computeOnServer(s -> s.overworld().getEntity(kitTarget) instanceof VehicleEntity v ? v.getHealth() : -1f);
		int kitsLeft = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).getCount());
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		AirDefense.LOGGER.info("[airdefense-test] RESULT armour_repair: recovery vehicle {} -> {} of {} in 10 s; repair kit {} -> {} (kits left {})",
				before, after, max, kit0, kit1, kitsLeft);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(tank, brem, kitTarget), Entity::discard));

		// The TOS-1A: 24 thermobaric rockets at a field 400 blocks off.
		int tx = x + 800;
		camera(server, tx + 0.5, ground + 6, -20, 0, 10);
		ctx.waitTicks(30);
		int tos = spawnVehicle(server, VehicleType.TOS1, tx, 0, 0);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(tos), v -> v.country = -1));
		BlockPos field = new BlockPos(tx + 20, ground, 400);
		int r0 = VehicleEntity.artilleryRounds;
		int landed0 = com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size();
		boolean fired = server.computeOnServer(s -> s.overworld().getEntity(tos) instanceof VehicleEntity v && v.commandFire(field, null, 0));
		look(server, tx - 16, ground + 4, -12, tx, ground + 2.5, 2);
		waitUntil(ctx, () -> VehicleEntity.artilleryRounds > r0 + 6, 400);
		ctx.takeScreenshot("b8_tos_salvo");
		look(server, tx + 60, ground + 22, 340, tx + 20, ground + 2, 400);
		waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size() > landed0 + 6, 700);
		ctx.waitTicks(6);
		ctx.takeScreenshot("b9_tos_impacts");
		waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.ARTY_LANDED.size() >= landed0 + 24, 500);
		ctx.waitTicks(40);
		ctx.takeScreenshot("b10_tos_field");
		double[] sp = spread(landed0, field);
		AirDefense.LOGGER.info("[airdefense-test] RESULT armour_tos: ordered {} fired {} landed {} mean {} blocks from the aim", fired,
				VehicleEntity.artilleryRounds - r0, (int) sp[0], String.format(java.util.Locale.ROOT, "%.1f", sp[1]));
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(tos), Entity::discard));
		language(ctx, "en_us");
	}

	/** A vest with these pouches in its slots (0-2 the front's lower row, 3 the chest, 4-5 the back). */
	private static ItemStack vestWith(net.minecraft.world.item.Item vest, com.stasdoto.airdefense.gear.Pouch... pouches) {
		ItemStack v = new ItemStack(vest);
		for (int i = 0; i < pouches.length; i++) {
			com.stasdoto.airdefense.gear.Pouches.set(v, i, pouches[i]);
		}
		return v;
	}


	private static ItemStack nvg(boolean on) {
		ItemStack h = new ItemStack(com.stasdoto.airdefense.registry.ModItems.NVG_HELMET);
		h.set(com.stasdoto.airdefense.registry.ModComponents.NVG_ON, on);
		return h;
	}

	private static void leave(ClientGameTestContext ctx) {
		ctx.getInput().holdKey(o -> o.keyShift);
		ctx.waitTicks(5);
		ctx.getInput().releaseKey(o -> o.keyShift);
		ctx.waitTicks(5);
	}

	/** Flies a piloted drone (FPV or Magura) from the player's seat into a vehicle; returns {ticks, health before, after}. */
	private int[] flyInto(ClientGameTestContext ctx, TestServerContext server, com.stasdoto.airdefense.missile.MissileType type, Vec3 start, Vec3 dir,
			int targetId, String shot) {
		float hp0 = server.computeOnServer(s -> s.overworld().getEntity(targetId) instanceof VehicleEntity v ? v.getHealth() : -1f);
		int drone = server.computeOnServer(s -> com.stasdoto.airdefense.missile.MissileEntity.launchPiloted(s.overworld(), type, start, dir,
				s.getPlayerList().getPlayers().getFirst()).getId());
		server.runOnServer(s -> {
			if (s.overworld().getEntity(drone) instanceof com.stasdoto.airdefense.missile.MissileEntity m) {
				com.stasdoto.airdefense.drone.DroneCam.start(s.getPlayerList().getPlayers().getFirst(), m);
			}
		});
		int t = 0;
		boolean shotTaken = false;
		for (; t < 600; t++) {
			Vec3 dp = server.computeOnServer(s -> s.overworld().getEntity(drone) instanceof com.stasdoto.airdefense.missile.MissileEntity m && m.isAlive()
					? m.position() : null);
			Vec3 tp = entityPos(server, targetId);
			if (dp == null || tp == null) {
				break;
			}
			Vec3 d = tp.add(0, 1.2, 0).subtract(dp);
			float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
			ctx.runOnClient(mc -> {
				mc.player.setYRot(yaw);
				mc.player.setXRot(pitch);
			});
			server.runOnServer(s -> {
				var pl = s.getPlayerList().getPlayers().getFirst();
				pl.setYRot(yaw);
				pl.setXRot(pitch);
			});
			if (!shotTaken && d.length() < 40) {
				shotTaken = true;
				ctx.takeScreenshot(shot);
			}
			ctx.waitTick();
		}
		ctx.waitTicks(30);
		float hp1 = server.computeOnServer(s -> s.overworld().getEntity(targetId) instanceof VehicleEntity v ? v.getHealth() : 0f);
		return new int[]{t, (int) hp0, (int) hp1};
	}

	private void pilotedDrones(ClientGameTestContext ctx, TestServerContext server) {
		int x = 41000;
		int g = ground;
		server.runCommand("gamemode creative @a");
		server.runCommand("time set 6000");
		camera(server, x + 0.5, g, 60.5, 180, 0);
		ctx.waitTicks(40);
		int tank = spawnVehicle(server, VehicleType.T72, x, -40, 0);
		ctx.waitTicks(20);
		int[] r = flyInto(ctx, server, com.stasdoto.airdefense.missile.MissileType.FPV, new Vec3(x + 0.5, g + 1.8, 59.5), new Vec3(0, 0.3, -1), tank,
				"150_fpv_camera");
		AirDefense.LOGGER.info("[airdefense-test] RESULT fpv: flew {} ticks, tank {} -> {}, cam sessions {}", r[0], r[1], r[2],
				com.stasdoto.airdefense.drone.DroneCam.started);
		ctx.waitTicks(40);
		// A lake with a boat on it; the Magura goes in at the near shore.
		server.runOnServer(s -> {
			for (int wx = x + 60; wx <= x + 90; wx++) {
				for (int wz = -60; wz <= 50; wz++) {
					for (int wy = g - 3; wy <= g - 1; wy++) {
						s.overworld().setBlock(new BlockPos(wx, wy, wz), Blocks.WATER.defaultBlockState(), 2);
					}
				}
			}
		});
		camera(server, x + 75.5, g + 1, 58.5, 180, 10);
		ctx.waitTicks(30);
		int boat = server.computeOnServer(s -> VehicleEntity.spawn(s.overworld(), VehicleType.RHIB, new Vec3(x + 75.5, g - 0.5, -45.5), 0).getId());
		ctx.waitTicks(30);
		int[] b = flyInto(ctx, server, com.stasdoto.airdefense.missile.MissileType.MAGURA, new Vec3(x + 75.5, g + 0.05, 45.5), new Vec3(0, 0, -1), boat,
				"151_magura_camera");
		AirDefense.LOGGER.info("[airdefense-test] RESULT magura: ran {} ticks, boat {} -> {}", b[0], b[1], b[2]);
		ctx.waitTicks(40);
		server.runCommand("gamemode spectator @a");
	}

	private void shot(ClientGameTestContext ctx, TestServerContext server, float[] cam, String name, int wait) {
		if (cam == null) {
			AirDefense.LOGGER.info("[airdefense-test] no camera for {}", name);
			return;
		}
		camera(server, cam[0], cam[1], cam[2], cam[3], cam[4]);
		ctx.waitTicks(wait);
		ctx.takeScreenshot(name);
	}

	/** Finds a block of these kinds within {@code r} of (x, z) between y0 and y1 (null if none): x, y, z. */
	private static int[] findNear(ServerLevel l, int x, int z, int r, int y0, int y1, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> what) {
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int d = 0; d <= r; d++) {
			for (int dx = -d; dx <= d; dx++) {
				for (int dz = -d; dz <= d; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
						continue;
					}
					for (int y = y0; y <= y1; y++) {
						if (what.test(l.getBlockState(q.set(x + dx, y, z + dz)))) {
							return new int[]{x + dx, y, z + dz};
						}
					}
				}
			}
		}
		return null;
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

	// --- 2.0: vehicle physics -----------------------------------------------------------------------------

	/** What the local driver's vehicle is doing (from his client, where it is simulated). */
	private static String driveLog(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity v
				? String.format(java.util.Locale.ROOT, "speed %.1f km/h, rpm %.0f, gear %d, throttle %.2f, brake %s, skid %.2f, yaw rate %.1f deg/s, surface %s, y %.2f",
						Math.abs(v.getSpeed()) * 72, v.getEngineRpm(), v.getGear(), v.getThrottle(), v.isBraking(), v.getSkid(),
						Math.toDegrees(v.driveState().omega), v.driveState().surface, v.getY())
				: "no vehicle");
	}

	private static float driveKmh(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity v ? Math.abs(v.getSpeed()) * 72 : -1f);
	}

	private int mountNew(ClientGameTestContext ctx, TestServerContext server, VehicleType type, double x, double z, float yaw) {
		int id = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			p.stopRiding();
			VehicleEntity v = VehicleEntity.spawn(s.overworld(), type, new Vec3(x, ground, z), yaw);
			p.startRiding(v);
			return v.getId();
		});
		ctx.waitTicks(15);
		return id;
	}

	/**
	 * 2.0 physics: a car pulls away through its gears, brakes to a stop and backs up (automatic reverse), a handbrake
	 * turn, a jump off a ramp and the landing, a tank climbing a slope. RESULT lines: speeds, revs, gears, yaw rate, slide.
	 */
	private void physics(ClientGameTestContext ctx, TestServerContext server) {
		int x = 14000;
		server.runCommand("gamemode creative @a");
		camera(server, x + 40, ground + 4, 0, -90, 10);
		ctx.waitTicks(40);
		// A long paved strip, cleared above.
		for (int seg = 0; seg < 4; seg++) {
			int a = x + seg * 80;
			server.runCommand(String.format("fill %d %d -8 %d %d 8 minecraft:gray_concrete", a, ground - 1, a + 79, ground - 1));
			server.runCommand(String.format("fill %d %d -8 %d %d 8 minecraft:air", a, ground, a + 79, ground + 6));
		}
		ctx.waitTicks(10);
		int car = mountNew(ctx, server, VehicleType.CIVIL_CAR, x + 4.5, 0.5, -90);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(20);
		ctx.takeScreenshot("phys_01_car_seated");
		// Pulling away: speed, revs and the gear every second.
		ctx.getInput().holdKey(o -> o.keyUp);
		int maxGear = 0;
		for (int t = 1; t <= 8; t++) {
			ctx.waitTicks(20);
			maxGear = Math.max(maxGear, ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v ? v.getGear() : 0));
			AirDefense.LOGGER.info("[airdefense-test] RESULT physics accel t={}s: {}", t, driveLog(ctx));
			if (t == 3) {
				ctx.takeScreenshot("phys_02_car_accel");
			}
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT physics accel: top gear reached {}, synced on server rpm {}", maxGear,
				server.computeOnServer(s -> s.overworld().getEntity(car) instanceof VehicleEntity v ? v.getEngineRpm() : -1f));
		// Braking to a stop, then holding the brake backs it up.
		ctx.getInput().releaseKey(o -> o.keyUp);
		float before = driveKmh(ctx);
		ctx.getInput().holdKey(o -> o.keyDown);
		int stop = waitUntil(ctx, () -> driveKmh(ctx) < 1f, 200);
		AirDefense.LOGGER.info("[airdefense-test] RESULT physics brake: from {} km/h to a stop in {} ticks", String.format("%.1f", before), stop);
		ctx.waitTicks(40);
		AirDefense.LOGGER.info("[airdefense-test] RESULT physics reverse: {}", driveLog(ctx));
		ctx.getInput().releaseKey(o -> o.keyDown);
		// Handbrake turn: up to speed, then left and Space.
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(60);
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.getInput().holdKey(o -> o.keyLeft);
		ctx.getInput().holdKey(o -> o.keyJump);
		float maxSkid = 0;
		double maxRate = 0;
		for (int i = 0; i < 6; i++) {
			ctx.waitTicks(4);
			maxSkid = Math.max(maxSkid, ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v ? v.getSkid() : 0f));
			maxRate = Math.max(maxRate, ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v ? Math.abs(Math.toDegrees(v.driveState().omega)) : 0.0));
			if (i == 2) {
				ctx.takeScreenshot("phys_03_handbrake_turn");
			}
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT physics handbrake: max yaw rate {} deg/s, max skid {}, {}", String.format("%.0f", maxRate),
				String.format("%.2f", maxSkid), driveLog(ctx));
		ctx.getInput().releaseKey(o -> o.keyLeft);
		ctx.getInput().releaseKey(o -> o.keyJump);
		ctx.waitTicks(40);

		// A jump: a run-up, a ramp four blocks high, then the drop.
		int rx = x + 120;
		for (int i = 0; i < 8; i++) {
			int h = i / 2;
			server.runCommand(String.format("fill %d %d -3 %d %d 3 minecraft:stone", rx + 60 + i, ground, rx + 60 + i, ground + h));
		}
		DriveStateReset.reset();
		mountNew(ctx, server, VehicleType.CIVIL_CAR, rx + 0.5, 0.5, -90);
		ctx.getInput().holdKey(o -> o.keyUp);
		int up = waitUntil(ctx, () -> ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v && v.driveState().airborne), 260);
		ctx.takeScreenshot("phys_04_jump");
		ctx.waitTicks(30);
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.waitTicks(10);
		ctx.takeScreenshot("phys_05_landed");
		AirDefense.LOGGER.info("[airdefense-test] RESULT physics jump: airborne after {} ticks, longest air time {} ticks, landing {} m/s, crashes {}, {}",
				up, com.stasdoto.airdefense.vehicle.DriveState.maxAirTicks, String.format("%.1f", com.stasdoto.airdefense.vehicle.DriveState.hardestLanding),
				VehicleEntity.crashes, driveLog(ctx));

		// A tank on a slope: one block up for every two along, ten blocks high.
		int tx = x + 240;
		for (int i = 0; i < 20; i++) {
			server.runCommand(String.format("fill %d %d -4 %d %d 4 minecraft:dirt", tx + 20 + i, ground, tx + 20 + i, ground + i / 2));
		}
		mountNew(ctx, server, VehicleType.T72, tx + 0.5, 0.5, -90);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(80);
		float flat = driveKmh(ctx);
		AirDefense.LOGGER.info("[airdefense-test] RESULT physics tank flat: {}", driveLog(ctx));
		ctx.waitTicks(40);
		ctx.takeScreenshot("phys_06_tank_slope");
		ctx.waitTicks(20);
		float slope = driveKmh(ctx);
		AirDefense.LOGGER.info("[airdefense-test] RESULT physics tank slope: {} km/h on the flat, {} km/h on the slope; {}", String.format("%.1f", flat),
				String.format("%.1f", slope), driveLog(ctx));
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.waitTicks(10);
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().stopRiding());
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode spectator @a");
	}

	/** Clears the physics counters between parts of the scene. */
	private static final class DriveStateReset {
		static void reset() {
			com.stasdoto.airdefense.vehicle.DriveState.maxAirTicks = 0;
			com.stasdoto.airdefense.vehicle.DriveState.hardestLanding = 0;
		}
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

	/**
	 * 1.25: three batteries side by side (NASAMS, IRIS-T, Buk) and a single target flying at them - a cruise missile,
	 * then one Shahed. One interceptor should go up for it (another only after a miss), not one from each.
	 */
	private void threeVsOne(ClientGameTestContext ctx, TestServerContext server) {
		int x = 51000;
		prepareDefense(ctx, server, VehicleType.NASAMS, x, 50, 180);
		spawnVehicle(server, VehicleType.IRIS_T, x + 14, 56, 180);
		spawnVehicle(server, VehicleType.BUK, x - 14, 56, 180);
		ctx.waitTicks(100);
		oneTarget(ctx, server, x, MissileType.KALIBR, 60, "three_vs_one_kalibr");
		oneTarget(ctx, server, x, MissileType.SHAHED, 60, "three_vs_one_shahed");
		// Five batteries (with Patriot and S-300) and a radar station: a ballistic missile gets two interceptors, no more;
		// a decoy the radar sees gets none.
		spawnVehicle(server, VehicleType.PATRIOT, x + 28, 62, 180);
		spawnVehicle(server, VehicleType.S300, x - 28, 62, 180);
		spawnVehicle(server, VehicleType.TRML4D, x, 80, 180);
		ctx.waitTicks(140);
		oneTarget(ctx, server, x, MissileType.ISKANDER, 240, "five_vs_one_iskander");
		oneTarget(ctx, server, x, MissileType.ISKANDER_DECOY, 240, "radar_vs_decoy");
	}

	/**
	 * 1.25.1: a salvo of Shaheds and two cruise missiles at one point. The first ones blow a crater there; the rest
	 * must still come down on it (into the crater) - not circle round the empty point in the air.
	 */
	private void samePoint(ClientGameTestContext ctx, TestServerContext server) {
		int x = 57000;
		BlockPos target = new BlockPos(x, ground - 1, 80);
		camera(server, x + 30, ground + 20, 110, 135, 20);
		ctx.waitTicks(40);
		int impacts0 = MissileStats.GROUND_IMPACTS.get();
		launchFrom(ctx, server, VehicleType.SHAHED, x - 10, -150, target);
		launchFrom(ctx, server, VehicleType.KALIBR, x + 10, -170, target);
		camera(server, x + 30, ground + 20, 110, 135, 20);
		int first = waitUntil(ctx, () -> MissileStats.GROUND_IMPACTS.get() > impacts0, 1200);
		ctx.takeScreenshot("59_same_point_first");
		int[] left = new int[1];
		int settled = waitUntil(ctx, () -> {
			left[0] = server.computeOnServer(s -> MissileEntity.find(s.overworld(), new net.minecraft.world.phys.AABB(target).inflate(400),
					m -> m.isAlive() && m.getMissileType().threat).size());
			return left[0] == 0;
		}, 1200);
		ctx.takeScreenshot("59b_same_point_after");
		AirDefense.LOGGER.info("[airdefense-test] RESULT same_point: first impact after {} ticks, all down {} ticks later (still flying {}), impacts {}",
				first, settled, left[0], MissileStats.GROUND_IMPACTS.get() - impacts0);
	}

	/** One missile of this kind at the batteries round (x, 50); counts what went up for it. */
	private void oneTarget(ClientGameTestContext ctx, TestServerContext server, int x, MissileType kind, int height, String scene) {
		int[] before = counters();
		int sd = MissileEntity.SELF_DESTRUCTS.get();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			Vec3 from = new Vec3(x + 5, ground + height, -380);
			Vec3 aim = new Vec3(x + 5, ground, 60);
			// Out beyond the loaded ground: load it, the missile keeps its way loaded from then on.
			var cp = net.minecraft.world.level.ChunkPos.containing(BlockPos.containing(from));
			l.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.ENDER_PEARL, cp, 3);
			l.getChunk(cp.x(), cp.z());
			MissileEntity m = MissileEntity.launchStrike(l, kind, from, aim, aim.subtract(from).normalize());
			m.setCountry(900);
		});
		camera(server, x + 26, ground + 8, 72, 160, -8);
		ctx.waitTicks(140);
		ctx.takeScreenshot("54_" + scene);
		ctx.waitTicks(320);
		report(scene, before);
		AirDefense.LOGGER.info("[airdefense-test] RESULT {}_selfdestruct: {}", scene, MissileEntity.SELF_DESTRUCTS.get() - sd);
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
	 * 2.0 vehicle sounds: a car, a truck and a tank drive off along a road (the AI's autopilot) while the player watches;
	 * the engine model's revs, throttle and gear and the loops playing are printed while they accelerate and cruise;
	 * the player gets into the tank (starter + engine catching) and sounds the truck's horn.
	 */
	private void vehSounds(ClientGameTestContext ctx, TestServerContext server) {
		int x = 21400;
		int g = ground;
		server.runCommand("gamemode creative @a");
		camera(server, x + 12, g + 3, -20, 90, 10);
		ctx.waitTicks(40);
		int car = spawnVehicle(server, VehicleType.POLICE_CAR, x, 0, 180);
		int truck = spawnVehicle(server, VehicleType.SUPPLY_TRUCK, x - 6, 0, 180);
		int tank = spawnVehicle(server, VehicleType.T72, x - 14, 0, 180);
		ctx.waitTicks(10);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(car, truck, tank),
				v -> v.drive(List.of(new Vec3(v.getX(), v.getY(), -60), new Vec3(v.getX(), v.getY(), -160)), 1.0f)));
		StringBuilder log = new StringBuilder();
		for (int k = 0; k < 4; k++) {
			ctx.waitTicks(25);
			String line = ctx.computeOnClient(mc -> {
				StringBuilder b = new StringBuilder();
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof VehicleEntity v && (v.getId() == car || v.getId() == truck || v.getId() == tank)) {
						b.append(v.getVehicleType().id).append(": ").append(com.stasdoto.airdefense.client.vehicle.VehicleSounds.describe(v)).append("; ");
					}
				}
				return b.toString();
			});
			log.append("[t").append(k).append("] ").append(line);
		}
		ctx.takeScreenshot("vs1_driving_off");
		int loops = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.vehicle.VehicleSounds.playing());
		int shifts = com.stasdoto.airdefense.client.vehicle.VehicleSounds.PLAYED[2];
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(car, truck, tank), Entity::discard));
		int tank2 = spawnVehicle(server, VehicleType.T72, x, 6, 180);
		int truck2 = spawnVehicle(server, VehicleType.SUPPLY_TRUCK, x + 6, 6, 180);
		ctx.waitTicks(20);
		int[] before = com.stasdoto.airdefense.client.vehicle.VehicleSounds.PLAYED.clone();
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getEntity(tank2) instanceof VehicleEntity v) {
				p.startRiding(v);
			}
		});
		ctx.waitTicks(80);
		boolean horn = ctx.computeOnClient(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof VehicleEntity v && v.getId() == truck2) {
					return com.stasdoto.airdefense.client.vehicle.VehicleSounds.horn(v);
				}
			}
			return false;
		});
		String tankState = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof VehicleEntity v
				? com.stasdoto.airdefense.client.vehicle.VehicleSounds.describe(v) : "not aboard");
		int[] now = com.stasdoto.airdefense.client.vehicle.VehicleSounds.PLAYED;
		AirDefense.LOGGER.info("[airdefense-test] RESULT vehsounds: drive {} loopsWhileDriving={} shifts={} starts={} doors={} horn={} tankIdle=[{}]",
				log, loops, shifts, now[0] - before[0], now[6] - before[6], horn, tankState);
		server.runOnServer(s -> {
			s.getPlayerList().getPlayers().getFirst().stopRiding();
			forVehicles(s.overworld(), List.of(tank2, truck2), Entity::discard);
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

	// ------------------------------------------------------------------------------------------------
	// 1.24: air raid sirens and Iron Dome

	/**
	 * 1.24: a Shahed flying over at 35 m is brought down with an AK-74 (aimed, short bursts) - drones are hit by their
	 * real size (2.5 m across the wings).
	 */
	private void rifleVsShahed(ClientGameTestContext ctx, TestServerContext server) {
		// Far from the other scenes' batteries (their reach is five times what it was: an S-300 at 33 000 shot it).
		int x = 45000;
		int g = ground;
		final int left = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
		final int right = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("effect give @a minecraft:resistance 600 4 true");
		camera(server, x + 0.5, g, 0.5, 180, -20);
		ctx.waitTicks(100);
		server.runOnServer(s -> {
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			pl.getInventory().setItem(0, com.stasdoto.airdefense.weapon.GunItem.loaded(
					com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.AK74)));
			pl.getInventory().setItem(1, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(AirDefense.id("ammo_545")), 64));
		});
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(10);
		int downBefore = com.stasdoto.airdefense.weapon.GunServer.MISSILES_DOWN.get();
		int hitsBefore = com.stasdoto.airdefense.weapon.GunServer.MISSILE_HITS.get();
		int shotsBefore = com.stasdoto.airdefense.weapon.GunServer.SHOTS.get();
		int drone = server.computeOnServer(s -> {
			var m = com.stasdoto.airdefense.missile.MissileEntity.launchStrike(s.overworld(), com.stasdoto.airdefense.missile.MissileType.SHAHED,
					new Vec3(x + 12, g + 35, -120), new Vec3(x + 12, g, 400), new Vec3(0, 0, 1));
			m.setCruiseAltitude(35);
			return m.getId();
		});
		int seen = waitUntil(ctx, () -> alive(server, drone), 60);
		AirDefense.LOGGER.info("[airdefense-test] rifle_shahed: drone {} flying after {} ticks", drone, seen);
		ctx.getInput().holdMouse(right);
		int fired = 0;
		boolean shotAt = false;
		for (int t = 0; t < 260 && alive(server, drone); t++) {
			Vec3 p = entityPos(server, drone);
			double d = p.distanceTo(new Vec3(x + 0.5, g + 1.6, 0.5));
			aimAt(ctx, p.add(0, 0.3, 0));
			if (d < 95 && t % 8 < 4) {
				ctx.getInput().holdMouse(left);
				shotAt = true;
			} else {
				ctx.getInput().releaseMouse(left);
			}
			if (t == 60 || shotAt && fired++ == 6) {
				ctx.takeScreenshot(t == 60 ? "170_rifle_shahed_coming" : "171_rifle_shahed_firing");
			}
			ctx.waitTick();
		}
		ctx.getInput().releaseMouse(left);
		ctx.getInput().releaseMouse(right);
		ctx.waitTicks(4);
		ctx.takeScreenshot("172_rifle_shahed_down");
		AirDefense.LOGGER.info("[airdefense-test] RESULT rifle_shahed: shots {}, hits on the drone {}, brought down {}, still flying {}",
				com.stasdoto.airdefense.weapon.GunServer.SHOTS.get() - shotsBefore,
				com.stasdoto.airdefense.weapon.GunServer.MISSILE_HITS.get() - hitsBefore,
				com.stasdoto.airdefense.weapon.GunServer.MISSILES_DOWN.get() - downBefore, alive(server, drone));
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
	}

	/** Air blocks in the ground (and the first storey) within {@code r} of a point: the size of the damage. */
	private static int holes(TestServerContext server, BlockPos at, int r, int base) {
		return server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			int n = 0;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					for (int y = base - 4; y <= base; y++) {
						if (l.getBlockState(new BlockPos(at.getX() + x, y, at.getZ() + z)).isAir()) {
							n++;
						}
					}
				}
			}
			return n;
		});
	}

	/** The sirens standing within {@code r} of (x, z), nearest first. */
	private static List<BlockPos> sirensNear(TestServerContext server, int x, int z, int r) {
		return server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			List<BlockPos> out = new ArrayList<>();
			for (long k : com.stasdoto.airdefense.siren.Sirens.get(s).known) {
				BlockPos p = BlockPos.of(k);
				if (Math.hypot(p.getX() - x, p.getZ() - z) <= r && l.isLoaded(p)
						&& l.getBlockState(p).getBlock() instanceof com.stasdoto.airdefense.siren.SirenBlock) {
					out.add(p);
				}
			}
			out.sort(java.util.Comparator.comparingDouble(p -> Math.hypot(p.getX() - x, p.getZ() - z)));
			return out;
		});
	}

	/** How many of these sirens show each signal: {off, alert, all clear}. */
	private static int[] signals(TestServerContext server, List<BlockPos> sirens) {
		return server.computeOnServer(s -> {
			int[] n = new int[3];
			for (BlockPos p : sirens) {
				var st = s.overworld().getBlockState(p);
				if (st.getBlock() instanceof com.stasdoto.airdefense.siren.SirenBlock) {
					n[st.getValue(com.stasdoto.airdefense.siren.SirenBlock.SIGNAL).ordinal()]++;
				}
			}
			return n;
		});
	}

	/** A camera {@code dist} blocks in front of a siren (on the side its horn faces), a little above, looking at it. */
	private static float[] sirenCam(TestServerContext server, BlockPos p, double dist, double up) {
		net.minecraft.core.Direction f = server.computeOnServer(s -> {
			var st = s.overworld().getBlockState(p);
			return st.getBlock() instanceof com.stasdoto.airdefense.siren.SirenBlock ? st.getValue(com.stasdoto.airdefense.siren.SirenBlock.FACING)
					: net.minecraft.core.Direction.SOUTH;
		});
		double side = dist * 0.45;
		net.minecraft.core.Direction across = f.getClockWise();
		double x = p.getX() + 0.5 + f.getStepX() * dist + across.getStepX() * side;
		double z = p.getZ() + 0.5 + f.getStepZ() * dist + across.getStepZ() * side;
		double y = p.getY() + up;
		return look(x, y, z, p.getX() + 0.5, p.getY() + 1.4, p.getZ() + 0.5);
	}

	private static float[] look(double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		return new float[]{(float) x, (float) y, (float) z, yaw, pitch};
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

	// --- 2.0: meshes ---------------------------------------------------------------------------------

	/** A camera at (x, y, z) (feet) looking at a point (yaw, pitch from the eye). */
	private static float[] lookEye(double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x;
		double dz = tz - z;
		double dy = ty - (y + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new float[]{(float) x, (float) y, (float) z, yaw, pitch};
	}

	private static final int MESH_PROP_ID = -7_300_000;


	private static void removeMeshProps(ClientGameTestContext ctx, int count) {
		ctx.runOnClient(mc -> {
			for (int i = 0; i < count; i++) {
				var e = mc.level.getEntity(MESH_PROP_ID - i);
				if (e != null) {
					mc.level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
				}
			}
		});
	}

	/** Average frames per second over {@code ticks}, and the mean frame time (ms). */
	private static float[] frameRate(ClientGameTestContext ctx, int ticks) {
		float fps = 0;
		double ft = 0;
		int n = 0;
		ctx.waitTicks(25);
		for (int i = 0; i < ticks; i++) {
			ctx.waitTick();
			if (i % 5 == 0) {
				fps += ctx.computeOnClient(mc -> mc.getFps());
				ft += ctx.computeOnClient(mc -> mc.getFrameTimeNs()) / 1e6;
				n++;
			}
		}
		return new float[]{fps / n, (float) (ft / n)};
	}

	/**
	 * 2.0: the test car's baked mesh (car_saloon) drawn as a town car prop and as a vehicle (the police car drawn with it):
	 * from the front, side, back and above, the eight paints, wheels turned and rolled, at night, far away; and the frame
	 * rate of thirty of them against the box models.
	 */
	/**
	 * 2.0: every vehicle that has a baked mesh, one by one, from the front three-quarter (then a few from behind and the
	 * town props: buses, trolleybus, tram).
	 */
	private void gallery(ClientGameTestContext ctx, TestServerContext server) {
		int x0 = 182000;
		int g = ground;
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 6000");
		server.runCommand("weather clear");
		camera(server, x0 + 0.5, g + 2, -8, 0, 10);
		ctx.waitTicks(40);
		int shown = 0;
		int missing = 0;
		int i = 0;
		StringBuilder miss = new StringBuilder();
		for (VehicleType type : VehicleType.values()) {
			String mid = type.isCivil() ? "car_saloon" : type.id;
			boolean has = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.vehicle.MeshLoader.get(mid) != null);
			if (!has) {
				missing++;
				miss.append(type.id).append(' ');
				continue;
			}
			int x = x0 + (i % 8) * 70;
			int z = (i / 8) * 70;
			i++;
			spawnVehicle(server, type, x, z, 0);
			float len = type.geometry.length();
			float r = Math.max(4f, len * 0.85f);
			double cx = x + 0.5;
			double cz = z + 0.5;
			double h = type.geometry.height();
			if (type.isAir() || type.boat || type.isShip()) {
				ctx.waitTicks(10);
			}
			shot(ctx, server, lookEye(cx + r * 0.75, g + h * 0.35, cz + r * 0.85, cx, g + h * 0.45, cz), "gal_" + type.id, 25);
			shown++;
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT gallery: shown {} vehicles with meshes, {} without: {}", shown, missing, miss);
	}


	// --- Lite: two sides, the tablet, the planner -----------------------------------------------------

	private static VehicleEntity vehicle(TestServerContext server, int id) {
		return server.computeOnServer(s -> s.overworld().getEntity(id) instanceof VehicleEntity v ? v : null);
	}

	private int spawnSide(TestServerContext server, VehicleType type, int x, int z, float yaw, int side) {
		int id = spawnVehicle(server, type, x, z, yaw);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(id), v -> v.country = side));
		return id;
	}

	private static com.stasdoto.airdefense.client.map.TacticalMapScreen mapScreen(net.minecraft.client.Minecraft mc) {
		return mc.gui.screen() instanceof com.stasdoto.airdefense.client.map.TacticalMapScreen s ? s : null;
	}

	/**
	 * Lite: what is left of the mod with the countries gone. The tablet lists and orders one side at a time (ours,
	 * then the enemy's); the enemy's launchers, given a massed strike in the planner, fire at our air defence, which
	 * shoots them down; ours fire at the enemy's air defence the other way round; and our own air defence lets our own
	 * rockets pass.
	 */
	private void lite(ClientGameTestContext ctx, TestServerContext server) {
		int x = 21000;
		int g = ground;
		language(ctx, "ru_ru");
		// No people, towns or countries are registered at all.
		int people = server.computeOnServer(s -> {
			int n = 0;
			for (var t : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
				String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
				if (id.startsWith("airdefense:") && (id.contains("soldier") || id.contains("worker") || id.contains("pedestrian")
						|| id.contains("train") || id.contains("airliner") || id.contains("prop"))) {
					n++;
				}
			}
			return n;
		});
		int cityBlocks = server.computeOnServer(s -> {
			int n = 0;
			for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
				String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString();
				if (id.startsWith("airdefense:") && (id.contains("street") || id.contains("city") || id.contains("facade") || id.contains("parking")
						|| id.contains("window") || id.contains("weathered"))) {
					n++;
				}
			}
			return n;
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT lite_registry: people/town entity types {}, town blocks {}", people, cityBlocks);
		camera(server, x, g + 30, 0, 180, 30);
		ctx.waitTicks(60);
		int ours = com.stasdoto.airdefense.map.Sides.OURS;
		int enemy = com.stasdoto.airdefense.map.Sides.ENEMY;
		// Ours to the south: a Patriot, a radar, an Iskander and a HIMARS; the enemy's 300 blocks north the same.
		int ourPatriot = spawnSide(server, VehicleType.PATRIOT, x, 150, 180, ours);
		spawnSide(server, VehicleType.MPQ65, x + 14, 156, 180, ours);
		int ourIskander = spawnSide(server, VehicleType.ISKANDER, x - 20, 175, 180, ours);
		int ourHimars = spawnSide(server, VehicleType.HIMARS, x + 25, 180, 180, ours);
		camera(server, x, g + 30, -150, 0, 30);
		ctx.waitTicks(40);
		int enemyPatriot = spawnSide(server, VehicleType.PATRIOT, x, -150, 0, enemy);
		int enemyIskander = spawnSide(server, VehicleType.ISKANDER, x - 20, -175, 0, enemy);
		int enemyIskander2 = spawnSide(server, VehicleType.ISKANDER, x + 20, -175, 0, enemy);
		ctx.waitTicks(120);
		// The tablet: ours.
		server.runCommand("gamemode creative @a");
		camera(server, x + 40, g + 2, 160, 90, 10);
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.open());
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.runOnClient(mc -> mapScreen(mc).centerOn(x, 0, 3));
		ctx.waitTicks(40);
		int listedOurs = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.vehicles().size());
		ctx.takeScreenshot("L1_tablet_ours");
		// The enemy's side.
		ctx.runOnClient(mc -> mapScreen(mc).toggleSide());
		ctx.waitTicks(40);
		int listedEnemy = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.vehicles().size());
		int sideNow = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.side());
		ctx.takeScreenshot("L2_tablet_enemy");
		AirDefense.LOGGER.info("[airdefense-test] RESULT lite_sides: listed ours {}, listed enemy {} (side {})", listedOurs, listedEnemy, sideNow);
		// The enemy's massed strike at our Patriot, planned in the pause.
		ctx.runOnClient(mc -> {
			var m = mapScreen(mc);
			m.setPaused(true);
			m.plannerForTest();
			m.pickPoint(x + 3, 152);
		});
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mapScreen(mc).pressCommand(1));
		ctx.waitTicks(10);
		int[] counts = ctx.computeOnClient(mc -> mapScreen(mc).plannerCounts());
		ctx.takeScreenshot("L3_planner_enemy");
		int[] before = counters();
		int interceptors = MissileStats.INTERCEPTORS_LAUNCHED.get();
		ctx.runOnClient(mc -> mapScreen(mc).pressCommand(3));
		ctx.waitTicks(20);
		AirDefense.LOGGER.info("[airdefense-test] RESULT lite_plan: planner launchers {}, in the plan {}; plans launched {}, launchers fired {}",
				counts[0], counts[1], com.stasdoto.airdefense.map.MapServer.plans, com.stasdoto.airdefense.map.MapServer.planLaunchers);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		server.runCommand("gamemode spectator @a");
		camera(server, x + 30, g + 6, 175, 160, -20);
		waitUntil(ctx, () -> MissileStats.INTERCEPTORS_LAUNCHED.get() > interceptors, 600);
		ctx.waitTicks(10);
		ctx.takeScreenshot("L4_ours_intercept");
		waitUntil(ctx, () -> MissileStats.THREATS_SHOT_DOWN.get() + MissileStats.GROUND_IMPACTS.get() >= before[1] + before[2] + 2, 600);
		ctx.waitTicks(40);
		ctx.takeScreenshot("L5_ours_after");
		report("lite_enemy_vs_our_patriot", before);
		// Ours at the enemy's Patriot (it shoots ours down).
		ctx.waitTicks(200);
		int[] before2 = counters();
		camera(server, x - 10, g + 30, 175, 0, 20);
		ctx.waitTicks(20);
		strike(server, ourIskander, new BlockPos(x + 3, g - 1, -148));
		camera(server, x + 30, g + 6, -175, 20, -20);
		waitUntil(ctx, () -> MissileStats.THREATS_SHOT_DOWN.get() + MissileStats.GROUND_IMPACTS.get() >= before2[1] + before2[2] + 2, 900);
		ctx.waitTicks(30);
		ctx.takeScreenshot("L6_enemy_intercepts");
		report("lite_ours_vs_enemy_patriot", before2);
		// Our HIMARS over our own Patriot: our air defence lets them pass.
		ctx.waitTicks(200);
		int[] before3 = counters();
		camera(server, x + 30, g + 30, 175, 180, 20);
		ctx.waitTicks(20);
		strike(server, ourHimars, new BlockPos(x + 40, g - 1, 330));
		ctx.waitTicks(500);
		report("lite_own_rockets_past_own_ad", before3);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(ourPatriot, ourIskander, ourHimars, enemyPatriot, enemyIskander, enemyIskander2),
				Entity::discard));
		language(ctx, "en_us");
	}

	/** Lite: a real world (not flat) - no towns or roads of the mod's in it, and the tablet's map of the land. */
	private void liteWorld(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(st -> st.setSeed("airdefense")).create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			language(ctx, "ru_ru");
			int waited = waitUntil(ctx, () -> com.stasdoto.airdefense.map.LandAtlas.ready, 2400);
			AirDefense.LOGGER.info("[airdefense-test] RESULT lite_atlas: land map ready after {} ticks, rows {}", waited,
					com.stasdoto.airdefense.map.LandAtlas.ROWS.get());
			int[] sp0 = server.computeOnServer(s -> {
				var p = s.overworld().getRespawnData().pos();
				return new int[]{p.getX(), s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()), p.getZ()};
			});
			server.runCommand("gamemode spectator @a");
			camera(server, sp0[0], sp0[1] + 90, sp0[2] + 90, 180, 35);
			ctx.waitTicks(400);
			ctx.takeScreenshot("L7_world_from_above");
			int modBlocks = server.computeOnServer(s -> {
				ServerLevel l = s.overworld();
				int n = 0;
				BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
				for (int dx = -96; dx <= 96; dx += 2) {
					for (int dz = -96; dz <= 96; dz += 2) {
						int x = sp0[0] + dx;
						int z = sp0[2] + dz;
						if (!l.hasChunk(x >> 4, z >> 4)) {
							continue;
						}
						int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
						for (int y = top - 3; y <= top + 1; y++) {
							q.set(x, y, z);
							if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(q).getBlock()).getNamespace().equals("airdefense")) {
								n++;
							}
						}
					}
				}
				return n;
			});
			AirDefense.LOGGER.info("[airdefense-test] RESULT lite_world: blocks of the mod round the spawn {}", modBlocks);
			server.runCommand("gamemode creative @a");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> com.stasdoto.airdefense.client.map.MapClient.open());
			ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
			ctx.runOnClient(mc -> mapScreen(mc).centerOn(sp0[0], sp0[2], 6));
			ctx.waitTicks(40);
			ctx.takeScreenshot("L8_tablet_land_10km");
			ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			ctx.waitTicks(5);
		}
	}

	// --- helpers -----------------------------------------------------------------------------------

	private int spawnVehicle(TestServerContext server, VehicleType type, int x, int z, float yaw) {
		return server.computeOnServer(s -> {
			VehicleEntity v = VehicleEntity.spawn(s.overworld(), type, new Vec3(x + 0.5, ground, z + 0.5), yaw);
			if (type.isLauncher()) {
				// The tests' launchers play the enemy: the air defence (the player's) shoots at what they fire.
				v.country = com.stasdoto.airdefense.map.Sides.ENEMY;
			}
			return v.getId();
		});
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
