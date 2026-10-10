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
			if (scene("citylook")) {
				cityLook(ctx, server);
			}
			if (scene("fpv")) {
				pilotedDrones(ctx, server);
			}
			if (scene("cities")) {
				cities(ctx, server);
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
			if (scene("arsenal")) {
				arsenal(ctx, server);
			}
			if (scene("smallArms")) {
				smallArms(ctx, server);
			}
			if (scene("sirens")) {
				sirens(ctx, server);
			}
			if (scene("rifleDrone")) {
				rifleVsShahed(ctx, server);
			}
			if (scene("townWar")) {
				townWar(ctx, server);
			}
			if (scene("repair")) {
				repair(ctx, server);
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
			if (scene("gear")) {
				gear(ctx, server);
			}
			if (scene("styles")) {
				styles(ctx, server);
			}
			if (scene("port")) {
				port(ctx, server);
			}
			if (scene("life")) {
				life(ctx, server);
			}
			if (scene("arty")) {
				arty(ctx, server);
			}
			if (scene("armor2")) {
				armor2(ctx, server);
			}
			if (scene("airwar")) {
				airWar(ctx, server);
			}
			if (scene("strikewave")) {
				strikeWave(ctx, server);
			}
			if (scene("cleanup")) {
				cleanup(ctx, server);
			}
			if (scene("navy")) {
				navy(ctx, server);
			}
			if (scene("uav")) {
				uav(ctx, server);
			}
			if (scene("streets")) {
				streets(ctx, server);
			}
			if (scene("night")) {
				night(ctx, server);
			}
			if (scene("people")) {
				people(ctx, server);
			}
			if (scene("services")) {
				services(ctx, server);
			}

			AirDefense.LOGGER.info("[airdefense-test] SUMMARY {}", MissileStats.summary());
		}
		if (scene("realcity")) {
			realCity(ctx);
		}
		if (scene("rail") || scene("airport")) {
			rail(ctx);
		}
	}

	/**
	 * Looks at the track block {x, y, z, dir x, dir z} from {@code dist} blocks off to one side ({@code side}: 1 left of
	 * the way, -1 right) and {@code back} blocks back along it, at least {@code up} above the rails and 3 above the ground.
	 */
	private static void railCam(ClientGameTestContext ctx, TestServerContext server, int[] p, int side, double dist, double up, double back) {
		double ax = -p[4] * side;
		double az = p[3] * side;
		double al = Math.hypot(ax, az);
		double fx = p[0] + 0.5 + ax / al * dist - p[3] * back;
		double fz = p[2] + 0.5 + az / al * dist - p[4] * back;
		look(server, fx, p[1] + up, fz, p[0] + 0.5, p[1] + 1, p[2] + 0.5);
		ctx.waitTicks(140);
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(fx), (int) Math.floor(fz)));
		look(server, fx, Math.max(p[1] + up, ground + 3), fz, p[0] + 0.5 + p[3] * 4, p[1] + 1.5, p[2] + 0.5 + p[4] * 4);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		ctx.waitTicks(30);
	}

	/** 1.39: the railway out of the capital in a normal world - the station, a bridge, a tunnel, the trains on it. */
	private void rail(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(st -> st.setSeed("airdefense")).create()) {
			TestServerContext server = sp.getServer();
			language(ctx, "ru_ru");
			server.runCommand("gamemode spectator @a");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			if (scene("airport")) {
				airport(ctx, server);
			}
			if (!scene("rail")) {
				return;
			}
			// The line's points to look at: {x, y, z, dir x, dir z} for the station, a bridge, a tunnel mouth, a diagonal, the open line.
			int[][] spots = server.computeOnServer(s -> {
				ServerLevel l = s.overworld();
				var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
				long t0 = System.nanoTime();
				java.util.List<com.stasdoto.airdefense.nation.Railways.Line> lines = new java.util.ArrayList<>();
				for (int r = 0; r <= 2 && lines.isEmpty(); r++) {
					for (int cx = -r; cx <= r && lines.isEmpty(); cx++) {
						for (int cz = -r; cz <= r && lines.isEmpty(); cz++) {
							if (Math.max(Math.abs(cx), Math.abs(cz)) == r) {
								lines.addAll(com.stasdoto.airdefense.nation.Railways.lines(l.getSeed(), t, cx, cz));
							}
						}
					}
				}
				AirDefense.LOGGER.info("[airdefense-test] RESULT rail_plan: {} lines planned, {} given up, in {} ms (the test's own call); planning took {} ms "
						+ "in all; roads raised over a line {}; ways given up: {}", com.stasdoto.airdefense.nation.Railways.planned,
						com.stasdoto.airdefense.nation.Railways.refused, (System.nanoTime() - t0) / 1_000_000,
						com.stasdoto.airdefense.nation.Railways.planNanos / 1_000_000, com.stasdoto.airdefense.nation.Railways.bridged,
						com.stasdoto.airdefense.nation.Railways.WHY);
				if (lines.isEmpty()) {
					return null;
				}
				var line = lines.getFirst();
				int n = line.length();
				int bridges = 0;
				int tunnels = 0;
				int diag = -1;
				int bridge = -1;
				int bestBridge = 0;
				int tunnel = -1;
				int run = 0;
				for (int i = 0; i < n; i++) {
					int kd = line.kind(t, i);
					if (kd == 1) {
						bridges++;
						run++;
						if (run > bestBridge) {
							bestBridge = run;
							bridge = i - run / 2;
						}
					} else {
						run = 0;
					}
					if (kd == 2) {
						tunnels++;
						if (tunnel < 0 && i > 8) {
							tunnel = i - 8;
						}
					}
					if (diag < 0 && (line.dir[i] & 1) == 1 && i + 20 < n && (line.dir[i + 20] & 1) == 1) {
						diag = i + 10;
					}
				}
				AirDefense.LOGGER.info("[airdefense-test] RESULT rail_line: {} blocks from {} {} to {} {}, {} on bridges, {} in tunnels, platform side {}", n,
						line.xs[0], line.zs[0], line.xs[n - 1], line.zs[n - 1], bridges, tunnels, line.platform);
				// A road over the line on its bridge.
				int under = -1;
				var probe = new com.stasdoto.airdefense.nation.Cities.Road.Spot();
				for (int i = 0; i < n && under < 0; i += 2) {
					for (var r : com.stasdoto.airdefense.nation.Cities.roadsNear(l.getSeed(), t, line.xs[i], line.zs[i])) {
						if (r.locate(line.xs[i] + 0.5, line.zs[i] + 0.5, r.half + 1, probe) && probe.along > 0 && probe.along < r.length
								&& r.height(probe.along) >= line.y(i) + 6) {
							under = i;
							break;
						}
					}
				}
				// The roundabout on the highway nearest the first station.
				int[] ring = null;
				double best = 1e9;
				for (var r : com.stasdoto.airdefense.nation.Cities.roadsNear(l.getSeed(), t, line.xs[0], line.zs[0])) {
					if (!r.highway) {
						continue;
					}
					for (double at : new double[]{30, r.length - 30}) {
						double[] c = r.pointAt(at);
						double d = Math.hypot(c[0] - line.xs[0], c[1] - line.zs[0]);
						if (d < best) {
							best = d;
							ring = new int[]{(int) Math.floor(c[0]), (int) Math.floor(r.height(at)), (int) Math.floor(c[1]), 1, 0, 1};
						}
					}
				}
				int[] pick = {12, bridge, tunnel, diag, n / 3, under};
				int[][] out = new int[pick.length][];
				for (int k = 0; k < pick.length; k++) {
					int i = pick[k];
					if (i < 0) {
						continue;
					}
					int j = Math.min(n - 1, i + 1);
					out[k] = new int[]{line.xs[i], line.y(i), line.zs[i], line.xs[j] - line.xs[i], line.zs[j] - line.zs[i], line.platform};
				}
				int[][] all = java.util.Arrays.copyOf(out, out.length + 1);
				all[out.length] = ring;
				return all;
			});
			if (spots == null) {
				return;
			}
			String[] names = {"rl1_station", "rl2_bridge", "rl3_tunnel", "rl4_diagonal", "rl5_line", "rl9_road_over", "rl10_roundabout"};
			for (int k = 0; k < spots.length; k++) {
				int[] p = spots[k];
				if (p == null) {
					continue;
				}
				// From the side (the platform side at the station), back along the line a little, above the trees.
				// The station and the tunnel mouth from above the line, looking along it (they lie in cuttings).
				int side = k == 0 ? p[5] : 1;
				boolean along = k == 0 || k == 2;
				if (k == 6) {
					// The roundabout from straight above.
					camera(server, p[0] + 0.5, p[1] + 34, p[2] + 0.5, 0, 90);
					ctx.waitTicks(140);
					ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
					ctx.waitTicks(20);
				} else {
					railCam(ctx, server, p, side, along ? 4 : k == 1 ? 40 : 22, along ? 16 : 12, along ? 22 : 18);
				}
				ctx.takeScreenshot(names[k]);
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT rail_built: {} track blocks laid, {} chunks made, avg {} us per chunk; client saw {} track blocks",
					server.computeOnServer(s -> com.stasdoto.airdefense.nation.Rails.laid()), com.stasdoto.airdefense.nation.CityGen.chunks,
					com.stasdoto.airdefense.nation.CityGen.chunks == 0 ? 0 : com.stasdoto.airdefense.nation.CityGen.nanos / 1000 / com.stasdoto.airdefense.nation.CityGen.chunks,
					ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.Trains.indexed()));
			// The trains: stand by the open line and wait for one of each.
			int[] p = spots[4];
			double ax = -p[4];
			double az = p[3];
			double al = Math.hypot(ax, az);
			ax /= al;
			az /= al;
			String[] trainNames = {"rl6_electric", "rl7_goods"};
			for (int kind = 1; kind <= 2; kind++) {
				int k = kind;
				railCam(ctx, server, p, 1, 18, 7, 0);
				ctx.runOnClient(mc -> {
					for (var tr : new java.util.ArrayList<>(com.stasdoto.airdefense.client.nation.Trains.ALL)) {
						tr.done = true;
						tr.stuck = 1000;
					}
					com.stasdoto.airdefense.client.nation.Trains.forceKind = k;
					com.stasdoto.airdefense.client.nation.Trains.forceSpawn = true;
				});
				int waited = waitUntil(ctx, () -> ctx.computeOnClient(mc -> {
					for (var tr : com.stasdoto.airdefense.client.nation.Trains.ALL) {
						if (tr.pose.length > 1 && tr.electric == (k == 1)) {
							double[] h = tr.pose[1];
							return Math.hypot(h[0] - p[0], h[2] - p[2]) < 14;
						}
					}
					return false;
				}), 1500);
				ctx.takeScreenshot(trainNames[kind - 1]);
				AirDefense.LOGGER.info("[airdefense-test] RESULT rail_train_{}: came by after {} ticks; trains made {}, taken away {}, hoots {}",
						kind == 1 ? "electric" : "goods", waited, ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.Trains.made),
						ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.Trains.gone),
						ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.Trains.hoots));
			}
			// An electric train at the station: it runs in, stops, sets off back.
			int[] st = spots[0];
			double sx = -st[4];
			double sz = st[3];
			double sl = Math.hypot(sx, sz);
			railCam(ctx, server, st, st[5], 6, 14, 26);
			ctx.runOnClient(mc -> {
				for (var tr : new java.util.ArrayList<>(com.stasdoto.airdefense.client.nation.Trains.ALL)) {
					tr.done = true;
					tr.stuck = 1000;
				}
				com.stasdoto.airdefense.client.nation.Trains.forceKind = 1;
				com.stasdoto.airdefense.client.nation.Trains.forceSpawn = true;
			});
			int stopped = waitUntil(ctx, () -> ctx.computeOnClient(mc -> {
				for (var tr : com.stasdoto.airdefense.client.nation.Trains.ALL) {
					if (tr.stops > 0 || tr.waiting > 0) {
						return true;
					}
				}
				return false;
			}), 2400);
			ctx.waitTicks(20);
			ctx.takeScreenshot("rl8_at_the_station");
			AirDefense.LOGGER.info("[airdefense-test] RESULT rail_station: an electric train stopped at the station after {} ticks", stopped);
		}
	}

	/** 1.40: a capital's airport in a normal world - from above, the terminal, the runway; planes parked, landing, leaving. */
	private void airport(ClientGameTestContext ctx, TestServerContext server) {
		// {threshold x, z, u x, u z, v x, v z, y}
		int[] a = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			long t0 = System.nanoTime();
			com.stasdoto.airdefense.nation.Airports.Airport found = null;
			for (int r = 0; r <= 2 && found == null; r++) {
				for (int cx = -r; cx <= r && found == null; cx++) {
					for (int cz = -r; cz <= r && found == null; cz++) {
						if (Math.max(Math.abs(cx), Math.abs(cz)) == r) {
							found = com.stasdoto.airdefense.nation.Airports.of(l.getSeed(), t, cx, cz);
						}
					}
				}
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT airport_plan: {} planned, {} capitals without room for one, in {} ms",
					com.stasdoto.airdefense.nation.Airports.planned, com.stasdoto.airdefense.nation.Airports.refused, (System.nanoTime() - t0) / 1_000_000);
			if (found == null) {
				return null;
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT airport_at: threshold {} {} level {}, runway along {} {}, apron towards {} {}, town {} at {} {}",
					found.sx, found.sz, found.y, found.ux, found.uz, found.vx, found.vz, found.city.size, found.city.x, found.city.z);
			return new int[]{found.sx, found.sz, found.ux, found.uz, found.vx, found.vz, found.y};
		});
		if (a == null) {
			return;
		}
		java.util.function.BiFunction<Double, Double, double[]> at = (u, v) -> new double[]{a[0] + 0.5 + u * a[2] + v * a[4], a[1] + 0.5 + u * a[3] + v * a[5]};
		int mid = com.stasdoto.airdefense.nation.Airports.MID;
		// The terminal and the apron first (the planes are put on the stands when the player comes near).
		double[] term = at.apply((double) mid, 175.0);
		double[] apron = at.apply((double) mid, 90.0);
		look(server, term[0], a[6] + 30, term[1], apron[0], a[6] + 2, apron[1]);
		ctx.waitTicks(300);
		int parked = waitUntil(ctx, () -> ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.planes().size()) > 0, 400);
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		ctx.takeScreenshot("ap1_terminal_apron");
		AirDefense.LOGGER.info("[airdefense-test] RESULT airport_parked: {} planes at the stands after {} ticks, {} airports seen",
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.planes().size()), parked,
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.airports()));
		// From above, the whole airport.
		double[] c = at.apply((double) mid, 60.0);
		double[] eye = at.apply((double) mid, 330.0);
		look(server, eye[0], a[6] + 170, eye[1], c[0], a[6], c[1]);
		ctx.waitTicks(200);
		ctx.takeScreenshot("ap2_from_above");
		// A plane lands: watched from beside the runway near the threshold.
		double[] side = at.apply(150.0, -40.0);
		double[] touch = at.apply(60.0, 0.0);
		look(server, side[0], a[6] + 8, side[1], touch[0], a[6] + 6, touch[1]);
		ctx.waitTicks(60);
		ctx.runOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.force = 1);
		int landing = waitUntil(ctx, () -> ctx.computeOnClient(mc -> {
			for (double[] p : com.stasdoto.airdefense.client.nation.AirTraffic.planes()) {
				if (p[3] == 0 && Math.hypot(p[0] - touch[0], p[2] - touch[1]) < 140) {
					return true;
				}
			}
			return false;
		}), 1400);
		ctx.takeScreenshot("ap3_landing");
		int down = waitUntil(ctx, () -> ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.landed) > 0, 2400);
		ctx.takeScreenshot("ap4_parked_after_landing");
		AirDefense.LOGGER.info("[airdefense-test] RESULT airport_landing: on the approach after {} ticks, at the stand {} ticks later", landing, down);
		// One leaves: watched from beside the runway half way along.
		double[] side2 = at.apply(300.0, -45.0);
		double[] run = at.apply(380.0, 0.0);
		look(server, side2[0], a[6] + 6, side2[1], run[0], a[6] + 5, run[1]);
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.force = 2);
		int rolling = waitUntil(ctx, () -> ctx.computeOnClient(mc -> {
			for (double[] p : com.stasdoto.airdefense.client.nation.AirTraffic.planes()) {
				if (p[3] == 2 && p[4] > 1.5) {
					return true;
				}
			}
			return false;
		}), 2400);
		ctx.waitTicks(30);
		ctx.takeScreenshot("ap5_take_off");
		int gone = waitUntil(ctx, () -> ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.departed) > 0, 1200);
		AirDefense.LOGGER.info("[airdefense-test] RESULT airport_take_off: on the run after {} ticks, gone {} ticks later; parked {}, landed {}, left {}",
				rolling, gone, ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.parked),
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.landed),
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.nation.AirTraffic.departed));
		// At night: the runway's lights, the terminal's windows.
		server.runCommand("time set 18000");
		look(server, eye[0], a[6] + 120, eye[1], c[0], a[6], c[1]);
		ctx.waitTicks(100);
		ctx.takeScreenshot("ap6_night");
		server.runCommand("time set 6000");
	}

	/** A normal world (hills, rivers, forests): the capital of the first country as the world generator builds it. */
	private void realCity(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(st -> st.setSeed("airdefense")).create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("gamemode spectator @a");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			int[] cap = server.computeOnServer(s -> {
				ServerLevel l = s.overworld();
				var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
				var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), t, 0, 0);
				for (var c : list) {
					AirDefense.LOGGER.info("[airdefense-test] RESULT real_plan: #{} {} at {} {} ground {} (sea {})", c.index, c.size, c.x, c.z, c.base, t.sea());
				}
				AirDefense.LOGGER.info("[airdefense-test] RESULT real_roads: {}", com.stasdoto.airdefense.nation.Cities.roads(l.getSeed(), t, 0, 0).size());
				return list.isEmpty() ? null : new int[]{list.getFirst().x, list.getFirst().z, list.getFirst().half(), list.getFirst().base};
			});
			if (cap == null) {
				return;
			}
			int cx = cap[0];
			int cz = cap[1];
			int base = cap[3];
			camera(server, cx + 0.5, base + 80, cz + cap[2] + 80, 180, 38);
			int waited = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Nations.citiesFounded > 0, 1800);
			ctx.waitTicks(200);
			AirDefense.LOGGER.info("[airdefense-test] RESULT real_city: founded after {} ticks, {} chunks, avg {} us per chunk", waited,
					com.stasdoto.airdefense.nation.CityGen.chunks,
					com.stasdoto.airdefense.nation.CityGen.chunks == 0 ? 0 : com.stasdoto.airdefense.nation.CityGen.nanos / 1000 / com.stasdoto.airdefense.nation.CityGen.chunks);
			ctx.takeScreenshot("140_real_capital");
			// 1.35: what is left floating round the capital (halves of trees, crowns, plants where the ground was levelled), once
			// the chunks the generator cut back afterwards have been looked over again.
			int swept = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Orphans.idleTicks >= 80, 1500);
			AirDefense.LOGGER.info("[airdefense-test] RESULT real_recheck: {} chunks looked over again once complete ({} looked at, {} still waiting for "
					+ "their neighbours), done within {} ticks; on the game's thread {} us a chunk on average ({} us of it working), longest {} us; "
					+ "on the sweeping thread {} us a chunk", com.stasdoto.airdefense.nation.Orphans.rechecks,
					com.stasdoto.airdefense.nation.Orphans.chunksLooked, com.stasdoto.airdefense.nation.Orphans.pending(), swept,
					com.stasdoto.airdefense.nation.Orphans.stepNanos / 1000 / Math.max(1, com.stasdoto.airdefense.nation.Orphans.chunksLooked),
					com.stasdoto.airdefense.nation.Orphans.stepCpu / 1000 / Math.max(1, com.stasdoto.airdefense.nation.Orphans.chunksLooked),
					com.stasdoto.airdefense.nation.Orphans.stepMax / 1000,
					com.stasdoto.airdefense.nation.Orphans.workerNanos / 1000 / Math.max(1, com.stasdoto.airdefense.nation.Orphans.chunksLooked));
			int half = cap[2];
			int[] orphans = server.computeOnServer(s -> {
				ServerLevel l = s.overworld();
				int left = 0;
				int looked = 0;
				for (int x = cx - half - 48; x <= cx + half + 48; x += 16) {
					for (int z = cz - half - 48; z <= cz + half + 48; z += 16) {
						var cp = new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4);
						boolean all = true;
						for (int dx = -1; dx <= 1 && all; dx++) {
							for (int dz = -1; dz <= 1 && all; dz++) {
								all = l.hasChunk(cp.x() + dx, cp.z() + dz);
							}
						}
						if (all) {
							looked++;
							left += com.stasdoto.airdefense.nation.Orphans.count(l, cp, base - 2, base + 90);
						}
					}
				}
				return new int[]{looked, left};
			});
			AirDefense.LOGGER.info("[airdefense-test] RESULT real_orphans: removed by the generator {}, by the sweep {}; left round the capital {} (in {} chunks): "
					+ "logs {}, leaves {}, plants {}; e.g. {}", com.stasdoto.airdefense.nation.Orphans.removedAtGeneration,
					com.stasdoto.airdefense.nation.Orphans.removedLater, orphans[1], orphans[0], com.stasdoto.airdefense.nation.Orphans.FOUND[0],
					com.stasdoto.airdefense.nation.Orphans.FOUND[1], com.stasdoto.airdefense.nation.Orphans.FOUND[2], com.stasdoto.airdefense.nation.Orphans.SAMPLES);
			AirDefense.LOGGER.info("[airdefense-test] RESULT real_removed: {} (changed before they could go: {})",
					new java.util.TreeMap<>(com.stasdoto.airdefense.nation.Orphans.REMOVED), com.stasdoto.airdefense.nation.Orphans.changedMeanwhile);
			// Where they are (town, its margin, by a road...), and a look at a few.
			java.util.List<BlockPos> seen = new java.util.ArrayList<>();
			int shots = 0;
			for (int k = 0; k < com.stasdoto.airdefense.nation.Orphans.SAMPLE_POS.size() && seen.size() < 10; k++) {
				BlockPos p = BlockPos.of(com.stasdoto.airdefense.nation.Orphans.SAMPLE_POS.get(k));
				boolean dup = false;
				for (BlockPos o : seen) {
					dup |= o.distManhattan(p) < 6;
				}
				if (dup) {
					continue;
				}
				seen.add(p);
				String what = com.stasdoto.airdefense.nation.Orphans.SAMPLES.get(k);
				String info = server.computeOnServer(s -> com.stasdoto.airdefense.nation.CityGen.describe(s.overworld(), p));
				AirDefense.LOGGER.info("[airdefense-test] RESULT orphan_at: {} | {}", what, info);
				if (shots < 4) {
					double ex = p.getX() + 0.5 + 9;
					double ey = p.getY() + 5;
					double ez = p.getZ() + 0.5 + 9;
					float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() + 0.5 - ex), p.getZ() + 0.5 - ez));
					float pitch = (float) Math.toDegrees(Math.atan2(ey - p.getY() - 0.5, Math.hypot(9, 9)));
					camera(server, ex, ey, ez, yaw, pitch);
					ctx.waitTicks(60);
					ctx.takeScreenshot("148_orphan_" + shots);
					shots++;
				}
			}
			camera(server, cx + 0.5, base + 140, cz + 0.5, 0, 90);
			ctx.waitTicks(100);
			ctx.takeScreenshot("141_real_capital_top");
			float[][] cc = cityCams(server);
			shot(ctx, server, cc[0], "142_real_city_hall", 60);
			camera(server, cx - cap[2] - 30.5, base + 25, cz + 0.5, 270, 20);
			ctx.waitTicks(80);
			ctx.takeScreenshot("143_real_city_edge");
			shot(ctx, server, cc[1], "144_real_street", 80);
			if (cc[3] != null) {
				shot(ctx, server, cc[3], "145_real_hamlet", 300);
				int hw = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Nations.hamletsFounded > 0, 600);
				AirDefense.LOGGER.info("[airdefense-test] RESULT real_hamlet: founded {} after {} ticks", com.stasdoto.airdefense.nation.Nations.hamletsFounded, hw);
				shot(ctx, server, cc[4], "146_real_hamlet_farm", 60);
				shot(ctx, server, cc[5], "147_real_hamlet_field", 60);
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT real_profile_ms: {} tick {} ms", java.util.Arrays.toString(
					java.util.Arrays.stream(com.stasdoto.airdefense.nation.Nations.PROFILE).map(v -> v / 1_000_000).toArray()),
					server.computeOnServer(s -> s.getAverageTickTimeNanos() / 1_000_000f));
			atlas(ctx, server, cx, cz, base);
		}
	}

	/**
	 * 1.25: the atlas - the 10 km round the spawn with every country, city, hamlet and road on the tablet map from the
	 * start; a winding highway out of the capital; a widened street.
	 */
	private void atlas(ClientGameTestContext ctx, TestServerContext server, int cx, int cz, int base) {
		int waited = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Atlas.ready, 3600);
		int before = -1;
		for (int k = 0; k < 40 && com.stasdoto.airdefense.nation.Atlas.founded != before; k++) {
			before = com.stasdoto.airdefense.nation.Atlas.founded;
			ctx.waitTicks(30);
		}
		String pol = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			int cities = 0;
			int hamlets = 0;
			for (var st : p.settlements.values()) {
				if (st.isCity()) {
					cities++;
				} else if (st.isHamlet()) {
					hamlets++;
				}
			}
			return p.countries.size() + " countries, " + cities + " cities, " + hamlets + " hamlets, flags waiting " + p.flagsPending.size();
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT atlas: ready after {} ticks ({} rows), founded {}; politics: {}; client has {} cities {} roads", waited,
				com.stasdoto.airdefense.nation.Atlas.ROWS.get(), com.stasdoto.airdefense.nation.Atlas.founded, pol,
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.AtlasClient.CITIES.size()),
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.AtlasClient.ROADS.size()));
		// The tablet map, from the whole atlas down to one city.
		server.runCommand("gamemode creative @a");
		camera(server, cx + 0.5, base + 60, cz + 0.5, 0, 90);
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(40);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(40);
		int[][] views = {{7, 0}, {6, 0}, {5, 0}, {4, 0}, {2, 0}};
		String[] names = {"150_atlas_whole", "151_atlas_countries", "152_atlas_region", "153_atlas_city", "154_atlas_streets"};
		for (int i = 0; i < views.length; i++) {
			int zi = views[i][0];
			ctx.runOnClient(mc -> ((com.stasdoto.airdefense.client.map.TacticalMapScreen) mc.gui.screen()).centerOn(cx, cz, zi));
			ctx.waitTicks(20);
			ctx.takeScreenshot(names[i]);
		}
		// 1.25.1: how smooth the map is - dragged across the land at three zooms, the longest frame measured.
		for (int zi : new int[]{6, 4, 2}) {
			ctx.runOnClient(mc -> com.stasdoto.airdefense.client.map.TacticalMapScreen.maxFrameNanos = 0);
			for (int k = 0; k < 60; k++) {
				int kk = k;
				ctx.runOnClient(mc -> ((com.stasdoto.airdefense.client.map.TacticalMapScreen) mc.gui.screen()).centerOn(cx + kk * (zi == 6 ? 40 : zi == 4 ? 12 : 3),
						cz + kk * (zi == 6 ? 25 : zi == 4 ? 8 : 2), zi));
				ctx.waitTick();
			}
			long worst = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.TacticalMapScreen.maxFrameNanos);
			long last = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.TacticalMapScreen.frameNanos);
			AirDefense.LOGGER.info("[airdefense-test] RESULT map_frame zoom {}: longest {} us, last {} us, {}", zi, worst / 1000, last / 1000,
					ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.TacticalMapScreen.tileStats()));
		}
		ctx.takeScreenshot("157_atlas_dragged");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		// The radar screen with a station and a few batteries about.
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int k = 0; k < 5; k++) {
				VehicleType t = k == 0 ? VehicleType.TRML4D : k == 1 ? VehicleType.PATRIOT : k == 2 ? VehicleType.IRIS_T : k == 3 ? VehicleType.NASAMS : VehicleType.GEPARD;
				int x = cx + 20 + k * 12;
				int z = cz + 20;
				VehicleEntity.spawn(l, t, new Vec3(x + 0.5, l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5), 0);
			}
		});
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(60);
		ctx.runOnClient(mc -> mc.gui.setScreen(new com.stasdoto.airdefense.client.map.RadarScreen()));
		long radarWorst = 0;
		for (int k = 0; k < 60; k++) {
			ctx.waitTick();
			radarWorst = Math.max(radarWorst, ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.map.RadarScreen.frameNanos));
		}
		ctx.takeScreenshot("158_radar");
		AirDefense.LOGGER.info("[airdefense-test] RESULT radar_frame: longest {} us", radarWorst / 1000);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		// A highway out of the capital: from above and along it.
		double[] r = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var roads = com.stasdoto.airdefense.nation.Cities.roads(l.getSeed(), t, Math.floorDiv(cx, com.stasdoto.airdefense.nation.Cities.CELL),
					Math.floorDiv(cz, com.stasdoto.airdefense.nation.Cities.CELL));
			if (roads.isEmpty()) {
				return null;
			}
			var road = roads.getFirst();
			double[] a = road.pointAt(180);
			double[] b = road.pointAt(200);
			return new double[]{a[0], a[1], b[0] - a[0], b[1] - a[1], road.height(180), road.length};
		});
		if (r != null) {
			double len = Math.hypot(r[2], r[3]);
			double ux = r[2] / len;
			double uz = r[3] / len;
			camera(server, r[0] - ux * 30, r[4] + 14, r[1] - uz * 30, (float) Math.toDegrees(Math.atan2(-ux, uz)), 20);
			ctx.waitTicks(200);
			ctx.takeScreenshot("155_highway");
			camera(server, r[0], r[4] + 90, r[1], 0, 90);
			ctx.waitTicks(120);
			ctx.takeScreenshot("156_highway_above");
			AirDefense.LOGGER.info("[airdefense-test] RESULT highway: length {} at {} {}", (int) r[5], (int) r[0], (int) r[1]);
		}
		soak(ctx, server, cx, cz, base);
	}

	/**
	 * 1.25.1: two minutes of play in the real world - flying round the capital and out over the country, a drone raid
	 * and a strike at the town - with every server tick over 60 ms written down (and where it went).
	 */
	private void soak(ClientGameTestContext ctx, TestServerContext server, int cx, int cz, int base) {
		int slow0 = com.stasdoto.airdefense.util.Perf.slowTicks;
		com.stasdoto.airdefense.util.Perf.WORST.clear();
		com.stasdoto.airdefense.util.Perf.worstMs = 0;
		server.runCommand("gamemode spectator @a");
		long[] frames = new long[2];
		for (int k = 0; k < 120; k++) {
			double a = k * 0.06;
			double r = 120 + k * 4;
			camera(server, cx + Math.cos(a) * r, base + 40, cz + Math.sin(a) * r, (float) Math.toDegrees(a) + 90, 15);
			if (k % 10 == 0) {
				Runtime rt = Runtime.getRuntime();
				int kk = k;
				AirDefense.LOGGER.info("[airdefense-test] soak {}: memory {} MB of {} MB, {}", kk, (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20,
						server.computeOnServer(s -> "entities " + java.util.stream.StreamSupport.stream(s.overworld().getAllEntities().spliterator(), false).count()
								+ ", chunks " + s.overworld().getChunkSource().getLoadedChunksCount()
								+ ", later jobs " + com.stasdoto.airdefense.util.Later.pending()));
			}
			if (k == 20) {
				server.runOnServer(s -> com.stasdoto.airdefense.drone.Raids.onPlayer(s.getPlayerList().getPlayers().getFirst(), 12, null));
			}
			if (k == 40) {
				// What fills the heap (the 25 biggest classes).
				try {
					Object h = java.lang.management.ManagementFactory.getPlatformMBeanServer().invoke(
							new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
							new Object[]{null}, new String[]{"[Ljava.lang.String;"});
					String[] lines = String.valueOf(h).split("\n");
					for (int i = 0; i < Math.min(30, lines.length); i++) {
						AirDefense.LOGGER.info("[airdefense-test] RESULT heap: {}", lines[i]);
					}
				} catch (Exception e) {
					AirDefense.LOGGER.info("[airdefense-test] RESULT heap: no histogram ({})", e.toString());
				}
			}
			if (k == 50) {
				server.runOnServer(s -> {
					var p = com.stasdoto.airdefense.nation.Politics.get(s);
					var capital = testCapital(s);
					for (var st : p.settlements.values()) {
						if (capital != null && st.country != capital.country && st.isCity()
								&& com.stasdoto.airdefense.nation.Arsenals.strikeNow(s.overworld(), st, capital)) {
							break;
						}
					}
				});
			}
			ctx.waitTicks(20);
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT soak: slow ticks {} (over 60 ms), worst {} ms, average {} ms", com.stasdoto.airdefense.util.Perf.slowTicks - slow0,
				com.stasdoto.airdefense.util.Perf.worstMs, server.computeOnServer(s -> s.getAverageTickTimeNanos() / 1_000_000f));
		for (String line : com.stasdoto.airdefense.util.Perf.WORST) {
			AirDefense.LOGGER.info("[airdefense-test] RESULT soak_tick: {}", line);
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

	/**
	 * 1.33: the navy on a bit of sea dug out of the flat world (3 deep, 260 x 200): the two warships close up; the
	 * Buyan-M's Kalibrs out of the cells and its gun at a BTR on the shore (the player aims); its close-in gun against
	 * Shaheds flying at it; a Bastion-P on the shore sinking an enemy Visby with its Oniks; an enemy warship's raid on a
	 * town on the shore.
	 */
	private void navy(ClientGameTestContext ctx, TestServerContext server) {
		int x = 130000;
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 3000");
		camera(server, x + 0.5, ground + 20, 80, 0, 20);
		ctx.waitTicks(60);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int wx = x - 130; wx <= x + 130; wx++) {
				for (int wz = -20; wz <= 180; wz++) {
					for (int wy = ground - 3; wy <= ground - 1; wy++) {
						l.setBlock(new BlockPos(wx, wy, wz), Blocks.WATER.defaultBlockState(), 2);
					}
				}
			}
		});
		ctx.waitTicks(40);
		int buyan = server.computeOnServer(s -> VehicleEntity.spawn(s.overworld(), VehicleType.BUYAN_M, new Vec3(x - 40.5, ground - 0.1, 50.5), 90).getId());
		int visby = server.computeOnServer(s -> VehicleEntity.spawn(s.overworld(), VehicleType.VISBY, new Vec3(x + 50.5, ground - 0.1, 95.5), 250).getId());
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(buyan, visby), v -> v.country = -1));
		ctx.waitTicks(40);
		look(server, x - 20, ground + 9, 18, x - 40, ground + 3, 50);
		ctx.waitTicks(30);
		ctx.takeScreenshot("n1_buyan");
		look(server, x + 30, ground + 8, 62, x + 50, ground + 3, 95);
		ctx.waitTicks(25);
		ctx.takeScreenshot("n2_visby");
		String floats = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			forVehicles(s.overworld(), List.of(buyan, visby), v -> b.append(v.getVehicleType().id).append(String.format(java.util.Locale.ROOT, " y %.2f; ", v.getY())));
			return b.toString();
		});

		// The Buyan-M's Kalibrs out of the cells at a point 250 blocks inland.
		int strikes0 = (int) com.stasdoto.airdefense.missile.MissileStats.STRIKES_LAUNCHED.get();
		boolean ordered = server.computeOnServer(s -> s.overworld().getEntity(buyan) instanceof VehicleEntity v
				&& v.commandStrike(new BlockPos(x - 40, ground, -230), null));
		look(server, x - 75, ground + 10, 30, x - 40, ground + 12, 50);
		waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileStats.STRIKES_LAUNCHED.get() > strikes0, 120);
		ctx.waitTicks(12);
		ctx.takeScreenshot("n3_kalibr_launch");
		ctx.waitTicks(60);
		int launched = (int) com.stasdoto.airdefense.missile.MissileStats.STRIKES_LAUNCHED.get() - strikes0;
		int left = server.computeOnServer(s -> s.overworld().getEntity(buyan) instanceof VehicleEntity v ? v.getAmmo() : -1);
		AirDefense.LOGGER.info("[airdefense-test] RESULT navy_cells: ordered {}, launched {}, cells full {} of 8; {}", ordered, launched, left, floats);

		// The gun: the player on the bridge aims at a BTR on the shore and fires.
		int btr = spawnVehicle(server, VehicleType.BTR82, x - 40, -45, 0);
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			if (s.overworld().getEntity(buyan) instanceof VehicleEntity v) {
				s.getPlayerList().getPlayers().getFirst().startRiding(v);
			}
		});
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(30);
		float bhp0 = server.computeOnServer(s -> s.overworld().getEntity(btr) instanceof VehicleEntity v ? v.getHealth() : -1f);
		float[] yp = ctx.computeOnClient(mc -> {
			Vec3 d = new Vec3(x - 39.5, ground + 1.2, -44.5).subtract(mc.player.getEyePosition());
			return new float[]{(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))};
		});
		ctx.getInput().lookAt(yp[0], yp[1]);
		ctx.waitTicks(80);
		for (int i = 0; i < 4; i++) {
			ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(3);
			if (i == 0) {
				ctx.takeScreenshot("n4_gun_fires");
			}
			ctx.waitTicks(60);
		}
		// Where the shells went: the BTR on the shore.
		ctx.getInput().holdKey(o -> o.keyShift);
		ctx.waitTicks(5);
		ctx.getInput().releaseKey(o -> o.keyShift);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode spectator @a");
		look(server, x - 25, ground + 6, -62, x - 40, ground + 1, -45);
		ctx.waitTicks(15);
		ctx.takeScreenshot("n4b_btr_hit");
		float bhp1 = server.computeOnServer(s -> s.overworld().getEntity(btr) instanceof VehicleEntity v && v.isAlive() ? v.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT navy_gun: BTR health {} -> {}", (int) bhp0, (int) bhp1);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(btr), Entity::discard));

		// The close-in gun: three Shaheds of somebody else's at the Buyan-M.
		int bursts0 = VehicleEntity.ciwsBursts;
		int kills0 = VehicleEntity.ciwsKills;
		float shp0 = server.computeOnServer(s -> s.overworld().getEntity(buyan) instanceof VehicleEntity v ? v.getHealth() : -1f);
		server.runOnServer(s -> {
			for (int i = 0; i < 3; i++) {
				var m = com.stasdoto.airdefense.missile.MissileEntity.launchStrike(s.overworld(), com.stasdoto.airdefense.missile.MissileType.SHAHED,
						new Vec3(x - 40 - 30 + i * 30, ground + 30, 170), new Vec3(x - 40.5, ground + 2, 50.5), new Vec3(0, 0, -1));
				m.setCountry(777);
			}
		});
		look(server, x - 70, ground + 8, 20, x - 40, ground + 25, 120);
		waitUntil(ctx, () -> VehicleEntity.ciwsBursts > bursts0, 900);
		ctx.waitTicks(6);
		ctx.takeScreenshot("n5_ciws");
		ctx.waitTicks(300);
		float shp1 = server.computeOnServer(s -> s.overworld().getEntity(buyan) instanceof VehicleEntity v ? v.getHealth() : -1f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT navy_ciws: bursts {}, brought down {}, ship health {} -> {}", VehicleEntity.ciwsBursts - bursts0,
				VehicleEntity.ciwsKills - kills0, (int) shp0, (int) shp1);

		// A Bastion-P on the shore against an enemy Visby: aimed at a point 25 blocks off the ship, the Oniks finds it.
		int bastion = spawnVehicle(server, VehicleType.BASTION, x + 60, -45, 0);
		int nmesis = spawnVehicle(server, VehicleType.NMESIS, x + 75, -42, 0);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(bastion, nmesis), v -> v.country = -1));
		ctx.waitTicks(20);
		look(server, x + 82, ground + 3.5, -28, x + 66, ground + 1.5, -44);
		ctx.waitTicks(20);
		ctx.takeScreenshot("n5b_coastal");
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(visby), v -> {
			v.country = 777;
			v.snapTo(x + 20.5, ground - 0.1, 150.5, 270, 0);
		}));
		ctx.waitTicks(20);
		int locks0 = com.stasdoto.airdefense.missile.MissileEntity.SHIP_LOCKS.get();
		int hits0 = com.stasdoto.airdefense.missile.MissileEntity.SHIP_HITS.get();
		float vhp0 = server.computeOnServer(s -> s.overworld().getEntity(visby) instanceof VehicleEntity v ? v.getHealth() : -1f);
		boolean fired = server.computeOnServer(s -> s.overworld().getEntity(bastion) instanceof VehicleEntity v
				&& v.commandStrike(new BlockPos(x + 45, ground - 1, 150), null));
		look(server, x + 85, ground + 6, -70, x + 60, ground + 6, -40);
		int up = waitUntil(ctx, () -> server.computeOnServer(s -> s.overworld().getEntity(bastion) instanceof VehicleEntity v && v.loadedRounds() < 2), 400);
		ctx.waitTicks(10);
		ctx.takeScreenshot("n6_bastion_launch");
		look(server, x - 10, ground + 10, 110, x + 20, ground + 3, 150);
		int hit = waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.SHIP_HITS.get() > hits0, 600);
		ctx.waitTicks(4);
		ctx.takeScreenshot("n7_oniks_hits");
		ctx.waitTicks(200);
		float vhp1 = server.computeOnServer(s -> s.overworld().getEntity(visby) instanceof VehicleEntity v && v.isAlive() ? v.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT navy_coastal: ordered {}, launched after {} ticks, seeker locks {}, hits {} (first after {} ticks), "
				+ "Visby health {} -> {}", fired, up, com.stasdoto.airdefense.missile.MissileEntity.SHIP_LOCKS.get() - locks0,
				com.stasdoto.airdefense.missile.MissileEntity.SHIP_HITS.get() - hits0, hit, (int) vhp0, (int) vhp1);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(buyan, visby, bastion, nmesis), Entity::discard));

		// A naval raid: an enemy Buyan-M comes in from the open sea to a town on the shore.
		int[] ids = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var mine = com.stasdoto.airdefense.nation.Nations.countryOf(l, p, pl, true);
			int id = p.newId();
			BlockPos at = new BlockPos(x, ground, -70);
			var town = new com.stasdoto.airdefense.nation.Settlement(id, "Приморск", at, at.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(id, town);
			town.country = mine.id;
			int eid = p.newId();
			BlockPos eat = new BlockPos(x + 2000, ground, 0);
			var et = new com.stasdoto.airdefense.nation.Settlement(eid, "Заморье", eat, eat.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(eid, et);
			var enemy = com.stasdoto.airdefense.nation.Nations.newWorldCountry(p, eid);
			et.country = enemy.id;
			com.stasdoto.airdefense.nation.War.declare(l, p, enemy, mine, net.minecraft.network.chat.Component.literal("test"));
			for (int t : new int[]{id, eid}) {
				var ar = com.stasdoto.airdefense.nation.Arsenals.get(s).of(p, p.settlements.get(t));
				ar.units.clear();
				ar.stock.clear();
			}
			com.stasdoto.airdefense.nation.War.navalRaidAt(l, p, enemy, town, VehicleType.BUYAN_M, new Vec3(x + 100.5, ground - 0.1, 172.5),
					new Vec3(x - 20.5, ground - 0.1, 45.5));
			return new int[]{id, enemy.id};
		});
		int shells0 = VehicleEntity.raidShells;
		int onStation = waitUntil(ctx, () -> server.computeOnServer(s -> {
			VehicleEntity v = raider(s.overworld());
			return v != null && v.raidPhase() >= 1;
		}), 900);
		look(server, x + 20, ground + 12, -40, x - 20, ground + 4, 45);
		ctx.waitTicks(20);
		ctx.takeScreenshot("n8_raider_on_station");
		waitUntil(ctx, () -> VehicleEntity.raidShells > shells0, 400);
		ctx.waitTicks(8);
		ctx.takeScreenshot("n9_raider_fires");
		ctx.waitTicks(400);
		look(server, x + 30, ground + 25, -110, x, ground, -70);
		ctx.waitTicks(10);
		ctx.takeScreenshot("n10_town_shelled");
		String raid = server.computeOnServer(s -> {
			VehicleEntity v = raider(s.overworld());
			return v == null ? "gone" : "phase " + v.raidPhase() + String.format(java.util.Locale.ROOT, " at %.0f %.0f", v.getX() - x, v.getZ());
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT navy_raid: sent {}, on station after {} ticks, missiles {}, shells {}; now {}",
				com.stasdoto.airdefense.nation.War.navalRaids, onStation, VehicleEntity.raidMissiles, VehicleEntity.raidShells - shells0, raid);
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getAllEntities()) {
				if (e instanceof VehicleEntity v && v.onRaid()) {
					v.discard();
				}
			}
		});
		language(ctx, "en_us");
	}

	/** 1.35: a street block facing {@code f} (or a pole, bottom or not) put down at (x, y, z). */
	private static void putStreet(ServerLevel l, String id, int x, int y, int z, net.minecraft.core.Direction f) {
		net.minecraft.world.level.block.Block b = com.stasdoto.airdefense.street.StreetBlocks.ALL.get(id);
		var st = b.defaultBlockState();
		if (st.hasProperty(com.stasdoto.airdefense.street.StreetBlock.FACING)) {
			st = st.setValue(com.stasdoto.airdefense.street.StreetBlock.FACING, f);
		}
		if (st.hasProperty(com.stasdoto.airdefense.street.StreetPoleBlock.BOTTOM)) {
			st = st.setValue(com.stasdoto.airdefense.street.StreetPoleBlock.BOTTOM,
					!(l.getBlockState(new BlockPos(x, y - 1, z)).getBlock() instanceof com.stasdoto.airdefense.street.StreetPoleBlock));
		}
		l.setBlock(new BlockPos(x, y, z), st, 2);
	}

	private static void postWith(ServerLevel l, String pole, int h, String top, int x, int g, int z, net.minecraft.core.Direction f) {
		for (int y = 1; y <= h; y++) {
			putStreet(l, pole, x, g + y, z, f);
		}
		putStreet(l, top, x, g + h + 1, z, f);
	}

	/**
	 * 1.35: the town furniture lined up (lamps, traffic lights, signs, benches, bins, bus stops...) by day and by night,
	 * the traffic lights changing; then towns of the new outlines (round, star, ring, square, horseshoe, two halves) from
	 * above, and their streets.
	 */
	private void streets(ClientGameTestContext ctx, TestServerContext server) {
		int x = 160000;
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 6000");
		camera(server, x + 15.5, ground + 6, 14.5, 180, 15);
		ctx.waitTicks(60);
		var S = net.minecraft.core.Direction.SOUTH;
		var E = net.minecraft.core.Direction.EAST;
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			int g = ground - 1;
			postWith(l, "pole_steel", 4, "lamp_modern", x, g, 0, S);
			postWith(l, "pole_concrete", 4, "lamp_cobra", x + 4, g, 0, S);
			postWith(l, "pole_green", 3, "lamp_lantern", x + 8, g, 0, S);
			postWith(l, "pole_black", 2, "lamp_globe", x + 12, g, 0, S);
			postWith(l, "pole_black", 2, "traffic_light", x + 16, g, 0, E);
			String[] signs = {"sign_stop", "sign_give_way", "sign_crossing", "sign_no_parking", "sign_speed", "sign_main_road", "sign_bus"};
			for (int i = 0; i < signs.length; i++) {
				postWith(l, "pole_steel", 2, signs[i], x + 20 + i * 2, g, 0, S);
			}
			String[] small = {"bench_park", "bench_soviet", "bench_modern", "bin_soviet", "bin_modern", "bin_euro", "hydrant", "mailbox_us",
					"mailbox_euro", "mailbox_soviet", "bollard", "planter", "bike_rack", "vending"};
			int[] at = {0, 3, 6, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 30};
			for (int i = 0; i < small.length; i++) {
				putStreet(l, small[i], x + at[i], g + 1, 12, S);
			}
			l.setBlock(new BlockPos(x + 32, g, 12), com.stasdoto.airdefense.street.StreetBlocks.MANHOLE.defaultBlockState(), 2);
			putStreet(l, "bus_stop_modern", x + 2, g + 1, 24, S);
			putStreet(l, "bus_stop_soviet", x + 8, g + 1, 24, S);
			putStreet(l, "booth_red", x + 13, g + 1, 24, S);
			putStreet(l, "booth_soviet", x + 15, g + 1, 24, S);
			putStreet(l, "advert_column", x + 18, g + 1, 24, S);
			putStreet(l, "kiosk", x + 22, g + 1, 24, S);
			putStreet(l, "billboard", x + 29, g + 2, 24, S);
		});
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		shot(ctx, server, new float[]{x + 8.5f, ground + 4.5f, 11.5f, 180, 8}, "st1_lamps", 20);
		shot(ctx, server, new float[]{x + 26.5f, ground + 2.5f, 7.5f, 180, 5}, "st2_signs", 20);
		shot(ctx, server, new float[]{x + 9.5f, ground + 2.5f, 17.5f, 180, 18}, "st3_small_a", 20);
		shot(ctx, server, new float[]{x + 25.5f, ground + 2.5f, 17.5f, 180, 18}, "st3_small_b", 20);
		shot(ctx, server, new float[]{x + 8.5f, ground + 3.5f, 32.5f, 180, 10}, "st4_big_a", 20);
		shot(ctx, server, new float[]{x + 24.5f, ground + 3.5f, 33.5f, 180, 10}, "st4_big_b", 20);
		// The traffic light close by: the heads change in turn (green - yellow - red), six seconds apart.
		shot(ctx, server, new float[]{x + 19.0f, ground + 2.4f, 3.0f, 135, -8}, "st5_traffic_a", 20);
		ctx.waitTicks(105);
		ctx.takeScreenshot("st5_traffic_b");
		server.runCommand("time set 18000");
		ctx.waitTicks(30);
		shot(ctx, server, new float[]{x + 8.5f, ground + 4.5f, 12.5f, 180, 8}, "st6_night_lamps", 30);
		shot(ctx, server, new float[]{x + 15.5f, ground + 4.5f, 34.5f, 180, 10}, "st6_night_big", 20);
		server.runCommand("time set 6000");
		int placed = server.computeOnServer(s -> {
			int n = 0;
			for (int dx = -2; dx < 36; dx++) {
				for (int dz = -2; dz < 28; dz++) {
					for (int dy = 0; dy < 8; dy++) {
						if (s.overworld().getBlockState(new BlockPos(x + dx, ground - 1 + dy, dz)).getBlock().getDescriptionId().startsWith("block.airdefense.")) {
							n++;
						}
					}
				}
			}
			return n;
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT street_furniture: {} blocks of it standing (registered {})", placed,
				com.stasdoto.airdefense.street.StreetBlocks.ALL.size());

		// Towns of the new outlines, from above, and their streets.
		Object[][] forms = {{com.stasdoto.airdefense.nation.CityForm.ROUND, com.stasdoto.airdefense.nation.CityStyle.SOVIET},
				{com.stasdoto.airdefense.nation.CityForm.STAR, com.stasdoto.airdefense.nation.CityStyle.SOVIET},
				{com.stasdoto.airdefense.nation.CityForm.RING, com.stasdoto.airdefense.nation.CityStyle.EUROPEAN},
				{com.stasdoto.airdefense.nation.CityForm.SQUARE, com.stasdoto.airdefense.nation.CityStyle.AMERICAN},
				{com.stasdoto.airdefense.nation.CityForm.CRESCENT, com.stasdoto.airdefense.nation.CityStyle.EUROPEAN},
				{com.stasdoto.airdefense.nation.CityForm.TWIN, com.stasdoto.airdefense.nation.CityStyle.AMERICAN}};
		ctx.runOnClient(mc -> mc.options.renderDistance().set(20));
		StringBuilder report = new StringBuilder();
		for (int k = 0; k < forms.length; k++) {
			var form = (com.stasdoto.airdefense.nation.CityForm) forms[k][0];
			var style = (com.stasdoto.airdefense.nation.CityStyle) forms[k][1];
			int cellX = 70 + k * 2;
			int cellZ = 70;
			int[] cap = server.computeOnServer(s -> {
				ServerLevel l = s.overworld();
				com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = style;
				com.stasdoto.airdefense.nation.Cities.FORCE_FORM = form;
				var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), cellX, cellZ);
				com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = null;
				if (list.isEmpty()) {
					com.stasdoto.airdefense.nation.Cities.FORCE_FORM = null;
					return null;
				}
				var c = list.getFirst();
				var sh = c.shape();
				com.stasdoto.airdefense.nation.Cities.FORCE_FORM = null;
				var hall = sh.hallLot();
				report.append(form).append('/').append(c.style).append(": form ").append(sh.form).append(", ").append(sh.lots.size()).append(" lots, ")
						.append(c.buildings().size()).append(" buildings; ");
				return new int[]{c.x, c.z, c.half(), c.base, hall.x0, hall.z0, hall.x1, hall.z1};
			});
			if (cap == null) {
				continue;
			}
			int cx = cap[0];
			int cz = cap[1];
			int half = cap[2];
			int base = cap[3];
			camera(server, cx + 0.5, base + 60, cz + 0.5, 0, 90);
			ctx.waitTicks(40);
			int m = half + 14;
			generateCity(server, cx - m, cz - m, cx + m, cz + m);
			String tag = "f" + k + "_" + form.name().toLowerCase(java.util.Locale.ROOT);
			// A wide lens and not too high (past the view distance the ground fades into the sky).
			ctx.runOnClient(mc -> mc.options.fov().set(100));
			shot(ctx, server, new float[]{cx + 0.5f, base + Math.min(half * 0.95f + 25, 175), cz + 0.5f, 0, 90}, tag + "_above", 300);
			ctx.runOnClient(mc -> mc.options.fov().set(70));
			if (k == 0 || k == 2 || k == 3) {
				// A crossing by the town hall (a traffic light on its corner) and the square.
				shot(ctx, server, new float[]{cap[4] - 9.5f, base + 4, cap[5] - 11.5f, -35, 12}, tag + "_crossing", 60);
			}
		}
		ctx.runOnClient(mc -> mc.options.renderDistance().set(8));
		AirDefense.LOGGER.info("[airdefense-test] RESULT city_forms: {}", report);
		language(ctx, "en_us");
	}

	/** 1.38: the fire engine, the ambulance and the police come to a blast in town; a police car on patrol. */
	private void services(ClientGameTestContext ctx, TestServerContext server) {
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 6000");
		int cellX = 98;
		int cellZ = 70;
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = com.stasdoto.airdefense.nation.CityStyle.EUROPEAN;
			com.stasdoto.airdefense.nation.Cities.FORCE_FORM = com.stasdoto.airdefense.nation.CityForm.SQUARE;
			var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), cellX, cellZ);
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = null;
			com.stasdoto.airdefense.nation.Cities.FORCE_FORM = null;
			if (list.isEmpty()) {
				return null;
			}
			var c = list.getFirst();
			c.shape();
			return new int[]{c.x, c.z, c.half(), c.base};
		});
		if (cap == null) {
			return;
		}
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		camera(server, cx + 0.5, base + 60, cz + 0.5, 0, 90);
		ctx.waitTicks(40);
		generateCity(server, cx - half - 14, cz - half - 14, cx + half + 14, cz + half + 14);
		int town = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var c = com.stasdoto.airdefense.nation.Cities.plannedCityAt(l.getSeed(), cx, cz, 40);
			var st = com.stasdoto.airdefense.nation.Nations.foundCity(l, com.stasdoto.airdefense.nation.Politics.get(s), c);
			return st == null ? -1 : st.id;
		});
		// A blast by a house a little way from the middle: fires start.
		int bx = cx + 30;
		int bz = cz + 24;
		camera(server, bx - 25.5, base + 18, bz - 25.5, -45, 25);
		ctx.waitTicks(60);
		server.runOnServer(s -> s.overworld().explode(null, bx + 0.5, base + 2, bz + 0.5, 3.5f, true, net.minecraft.world.level.Level.ExplosionInteraction.TNT));
		int sent = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Services.SENT[0] > 0, 100);
		java.util.function.Function<VehicleType, float[]> near = t -> server.computeOnServer(s -> {
			for (Entity e : s.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(VehicleEntity.class),
					v -> v.isAlive() && v.getVehicleType() == t)) {
				return new float[]{(float) e.getX(), (float) e.getY(), (float) e.getZ(), (float) e.distanceToSqr(bx, base + 1, bz)};
			}
			return null;
		});
		// The fire engine on its way: the camera follows it.
		ctx.waitTicks(120);
		float[] f = near.apply(VehicleType.FIRE_TRUCK);
		if (f != null) {
			look(server, f[0] - 9, f[1] + 5, f[2] - 9, f[0], f[1] + 1.5, f[2]);
			ctx.waitTicks(3);
			ctx.takeScreenshot("sv1_fire_engine_coming");
		}
		int there = waitUntil(ctx, () -> {
			float[] g = near.apply(VehicleType.FIRE_TRUCK);
			return g != null && g[3] < 26 * 26;
		}, 1200);
		ctx.waitTicks(40);
		look(server, bx - 12.5, base + 16, bz - 12.5, bx, base + 1, bz);
		ctx.waitTicks(20);
		ctx.takeScreenshot("sv2_at_the_blast");
		float[] a = near.apply(VehicleType.AMBULANCE);
		if (a != null) {
			look(server, a[0] - 4, a[1] + 9, a[2] - 4, a[0], a[1] + 1.5, a[2]);
			ctx.waitTicks(3);
			ctx.takeScreenshot("sv3_ambulance");
		}
		ctx.waitTicks(300);
		float[] pc = near.apply(VehicleType.POLICE_CAR);
		if (pc != null) {
			look(server, pc[0] - 4, pc[1] + 8, pc[2] - 4, pc[0], pc[1] + 1, pc[2]);
			ctx.waitTicks(3);
			ctx.takeScreenshot("sv4_police");
		}
		// Night: the lights flash.
		server.runCommand("time set 18000");
		ctx.waitTicks(20);
		if (pc != null) {
			float[] pn = near.apply(VehicleType.POLICE_CAR);
			if (pn != null) {
				look(server, pn[0] - 4, pn[1] + 8, pn[2] - 4, pn[0], pn[1] + 1, pn[2]);
				ctx.waitTicks(3);
				ctx.takeScreenshot("sv5_lights_at_night");
			}
		}
		server.runCommand("time set 6000");
		int callsBefore = VehicleEntity.callsDone;
		ctx.waitTicks(900);
		// A patrol: the player stands in the town.
		camera(server, cx + 0.5, base + 2.5, cz + 0.5, 0, 10);
		com.stasdoto.airdefense.nation.Services.patrolNow = true;
		ctx.waitTicks(260);
		float[] p2 = near.apply(VehicleType.POLICE_CAR);
		if (p2 != null) {
			look(server, p2[0] - 4, p2[1] + 9, p2[2] - 4, p2[0], p2[1] + 1, p2[2]);
			ctx.waitTicks(3);
			ctx.takeScreenshot("sv6_patrol");
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT services: town {}, sent fire {} ambulance {} police {} patrols {} (first after {} ticks), "
				+ "the engine there after {} ticks, fires put out {}, calls done {}", town, com.stasdoto.airdefense.nation.Services.SENT[0],
				com.stasdoto.airdefense.nation.Services.SENT[1], com.stasdoto.airdefense.nation.Services.SENT[2],
				com.stasdoto.airdefense.nation.Services.SENT[3], sent, there, VehicleEntity.firesOut, VehicleEntity.callsDone - callsBefore);
		language(ctx, "en_us");
	}

	/**
	 * 1.37: the passers-by in a town's streets - many in the morning rush, few at night, gone indoors on an alert; the
	 * shelter entrance by the square.
	 */
	private void people(ClientGameTestContext ctx, TestServerContext server) {
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		int cellX = 94;
		int cellZ = 70;
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = com.stasdoto.airdefense.nation.CityStyle.SOVIET;
			com.stasdoto.airdefense.nation.Cities.FORCE_FORM = com.stasdoto.airdefense.nation.CityForm.SQUARE;
			var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), cellX, cellZ);
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = null;
			com.stasdoto.airdefense.nation.Cities.FORCE_FORM = null;
			if (list.isEmpty()) {
				return null;
			}
			var c = list.getFirst();
			var hall = c.shape().hallLot();
			return new int[]{c.x, c.z, c.half(), c.base, hall.x0, hall.z0, hall.x1, hall.z1};
		});
		if (cap == null) {
			return;
		}
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		camera(server, cx + 0.5, base + 60, cz + 0.5, 0, 90);
		ctx.waitTicks(40);
		generateCity(server, cx - half - 14, cz - half - 14, cx + half + 14, cz + half + 14);
		// The morning rush (8 am), standing at the crossing by the town hall.
		server.runCommand("time set 2000");
		float[] crossing = {cap[4] - 9.5f, base + 3.5f, cap[5] - 11.5f, -35, 10};
		camera(server, crossing[0], crossing[1], crossing[2], crossing[3], crossing[4]);
		int made0 = com.stasdoto.airdefense.client.nation.Pedestrians.made;
		ctx.waitTicks(500);
		int rush = com.stasdoto.airdefense.client.nation.Pedestrians.count();
		ctx.takeScreenshot("p1_morning_rush");
		camera(server, crossing[0] + 6, base + 2.6, crossing[2] - 2, 160, 8);
		ctx.waitTicks(100);
		ctx.takeScreenshot("p2_pavement");
		int crossings = com.stasdoto.airdefense.client.nation.Pedestrians.crossings;
		// Midday, then night.
		server.runCommand("time set 6000");
		ctx.waitTicks(300);
		int noon = com.stasdoto.airdefense.client.nation.Pedestrians.count();
		server.runCommand("time set 19000");
		ctx.waitTicks(400);
		int night = com.stasdoto.airdefense.client.nation.Pedestrians.count();
		ctx.takeScreenshot("p3_night");
		// Back to day; then the sirens: everybody indoors.
		server.runCommand("time set 2000");
		ctx.waitTicks(400);
		int before = com.stasdoto.airdefense.client.nation.Pedestrians.count();
		com.stasdoto.airdefense.client.nation.Pedestrians.forceAlert = true;
		ctx.waitTicks(30);
		ctx.takeScreenshot("p4_alert_hurry");
		ctx.waitTicks(250);
		int alert = com.stasdoto.airdefense.client.nation.Pedestrians.count();
		com.stasdoto.airdefense.client.nation.Pedestrians.forceAlert = false;
		// The shelter entrance by the square.
		int[] sh = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
			for (int x = cx - half; x <= cx + half; x++) {
				for (int z = cz - half; z <= cz + half; z++) {
					if (l.getBlockState(q.set(x, base + 1, z)).is(com.stasdoto.airdefense.street.StreetBlocks.SHELTER_ENTRANCE)) {
						return new int[]{x, z};
					}
				}
			}
			return null;
		});
		if (sh != null) {
			look(server, sh[0] + 0.5, base + 3, sh[1] + 7.5, sh[0] + 0.5, base + 1.5, sh[1] + 0.5);
			ctx.waitTicks(40);
			ctx.takeScreenshot("p5_shelter");
		}
		server.runCommand("time set 6000");
		AirDefense.LOGGER.info("[airdefense-test] RESULT people: made {}, about the player: morning {}, noon {}, night {}, before the alert {}, "
				+ "after it {}; crossings {}; shelter entrance {}; villagers sent to cover {}", com.stasdoto.airdefense.client.nation.Pedestrians.made - made0,
				rush, noon, night, before, alert, crossings, sh != null, com.stasdoto.airdefense.nation.Shelters.hidden);
		language(ctx, "en_us");
	}

	/**
	 * 1.36: a town's parks as a playground, a sports ground and a stadium; the town by night with its windows lit (and dark
	 * in a blackout), the town's sounds round the player.
	 */
	private void night(ClientGameTestContext ctx, TestServerContext server) {
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 6000");
		int cellX = 90;
		int cellZ = 70;
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = com.stasdoto.airdefense.nation.CityStyle.EUROPEAN;
			com.stasdoto.airdefense.nation.Cities.FORCE_FORM = com.stasdoto.airdefense.nation.CityForm.ROUND;
			var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), cellX, cellZ);
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = null;
			com.stasdoto.airdefense.nation.Cities.FORCE_FORM = null;
			if (list.isEmpty()) {
				return null;
			}
			var c = list.getFirst();
			c.shape();
			// The parks: the first a stadium, the second a playground, the third a sports ground.
			int[] kinds = {5, 2, 4};
			int k = 0;
			int[] out = new int[11];
			out[0] = c.x;
			out[1] = c.z;
			out[2] = c.half();
			out[3] = c.base;
			for (var b : c.buildings()) {
				if (b.type == com.stasdoto.airdefense.nation.BuildingType.PARK && k < 3) {
					b.variant = kinds[k];
					var center = b.origin.relative(b.facing, 10);
					out[4 + k * 2] = center.getX();
					out[5 + k * 2] = center.getZ();
					k++;
				}
			}
			out[10] = k;
			AirDefense.LOGGER.info("[airdefense-test] RESULT night_town: parks {} parks flag {}", k, com.stasdoto.airdefense.nation.Cities.parks);
			return out;
		});
		if (cap == null) {
			return;
		}
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		camera(server, cx + 0.5, base + 60, cz + 0.5, 0, 90);
		ctx.waitTicks(40);
		generateCity(server, cx - half - 14, cz - half - 14, cx + half + 14, cz + half + 14);
		String[] names = {"n1_stadium", "n2_playground", "n3_sports"};
		for (int k = 0; k < cap[10]; k++) {
			int px = cap[4 + k * 2];
			int pz = cap[5 + k * 2];
			look(server, px - 8.5, base + 8, pz - 8.5, px + 0.5, base + 1, pz + 0.5);
			ctx.waitTicks(k == 0 ? 120 : 40);
			ctx.takeScreenshot(names[k]);
		}
		// The town's sounds by day, standing on a street by the town hall.
		look(server, cx + 0.5, base + 2, cz + 20.5, cx + 0.5, base + 2, cz + 0.5);
		int played0 = com.stasdoto.airdefense.client.fx.CityAmbience.played;
		ctx.waitTicks(600);
		int playedDay = com.stasdoto.airdefense.client.fx.CityAmbience.played - played0;
		boolean inTown = com.stasdoto.airdefense.client.fx.CityAmbience.inTown;
		// Night: the windows' random ticks quickened so they catch up in a few seconds.
		server.runCommand("time set 15000");
		server.runCommand("gamerule randomTickSpeed 400");
		server.runCommand("gamerule random_tick_speed 400");
		ctx.waitTicks(200);
		int r = half + 10;
		java.util.function.Supplier<int[]> count = () -> server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			int lit = 0;
			int all = 0;
			BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
			for (int x = cx - r; x <= cx + r; x += 1) {
				for (int z = cz - r; z <= cz + r; z += 1) {
					for (int y = base + 1; y < base + 40; y++) {
						var st = l.getBlockState(q.set(x, y, z));
						if (st.is(com.stasdoto.airdefense.street.StreetBlocks.CITY_WINDOW) || st.is(com.stasdoto.airdefense.street.StreetBlocks.CITY_GLASS)) {
							all++;
							if (st.getValue(com.stasdoto.airdefense.street.WindowLights.LIT)) {
								lit++;
							}
						}
					}
				}
			}
			return new int[]{lit, all};
		});
		int[] lit = count.get();
		look(server, cx + 0.5, base + 22, cz + half * 0.5 + 20.5, cx + 0.5, base + 8, cz + 0.5);
		ctx.waitTicks(80);
		ctx.takeScreenshot("n4_night_town");
		look(server, cx + 0.5, base + 2.5, cz + 20.5, cx + 0.5, base + 6, cz + 0.5);
		played0 = com.stasdoto.airdefense.client.fx.CityAmbience.played;
		ctx.waitTicks(400);
		int playedNight = com.stasdoto.airdefense.client.fx.CityAmbience.played - played0;
		ctx.takeScreenshot("n5_night_street");
		if (cap[10] > 0) {
			look(server, cap[4] - 8.5, base + 8, cap[5] - 8.5, cap[4] + 0.5, base + 1, cap[5] + 0.5);
			ctx.waitTicks(40);
			ctx.takeScreenshot("n6_stadium_night");
		}
		// A blackout: every town on alert, the lights go out.
		server.runOnServer(s -> com.stasdoto.airdefense.siren.Sirens.get(s).everywhere = true);
		ctx.waitTicks(200);
		int[] dark = count.get();
		look(server, cx + 0.5, base + 22, cz + half * 0.5 + 20.5, cx + 0.5, base + 8, cz + 0.5);
		ctx.waitTicks(40);
		ctx.takeScreenshot("n7_blackout");
		server.runOnServer(s -> com.stasdoto.airdefense.siren.Sirens.get(s).everywhere = false);
		server.runCommand("gamerule randomTickSpeed 3");
		server.runCommand("gamerule random_tick_speed 3");
		server.runCommand("time set 6000");
		AirDefense.LOGGER.info("[airdefense-test] RESULT night_lights: windows {} lit {} at 21:00; in the blackout lit {}; sounds by day {} at night {}, "
				+ "in town {}", lit[1], lit[0], dark[0], playedDay, playedNight, inTown);
		language(ctx, "en_us");
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
	 * 1.34: drones and electronic warfare - the six vehicles lined up (catapults raised, masts up); an Orlan-10 over an
	 * enemy column marks it (glow, the tablet's map) and corrects the guns (their rounds closer round the aim than at a
	 * point it does not watch); a Lancet, a TB2's bombs and a Switchblade on the column's vehicles; an enemy jammer cuts
	 * a Lancet's link, the player's jammer sends an enemy Shahed astray; a country at war sends its recon drone over the
	 * player's town and a loitering munition at his tank.
	 */
	private void uav(ClientGameTestContext ctx, TestServerContext server) {
		int x = 175000;
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 3000");
		camera(server, x + 0.5, ground + 20, -30.5, 0, 20);
		ctx.waitTicks(60);
		// The ground the targets stand on is kept loaded (they are put there, and given their side, before anybody is near).
		server.runCommand(String.format("forceload add %d %d %d %d", x - 176, 288, x + 184, 352));
		server.runCommand(String.format("forceload add %d %d %d %d", x - 432, 8, x - 384, 56));
		server.runCommand(String.format("forceload add %d %d %d %d", x + 16, -336, x + 48, -320));
		ctx.waitTicks(60);
		VehicleType[] types = {VehicleType.ORLAN, VehicleType.TB2_GCS, VehicleType.LANCET, VehicleType.SWITCHBLADE, VehicleType.BORISOGLEBSK,
				VehicleType.BUKOVEL};
		int[] own = new int[types.length];
		for (int i = 0; i < types.length; i++) {
			own[i] = spawnVehicle(server, types[i], x - 40 + i * 16, 0, 0);
		}
		List<Integer> ownList = java.util.Arrays.stream(own).boxed().toList();
		server.runOnServer(s -> forVehicles(s.overworld(), ownList, v -> {
			v.country = -1;
			v.raiseLauncher();
		}));
		// The targets: a column by the point the Orlan watches, a tank and a BTR for the Lancet and the Switchblade, a
		// BMP for the TB2.
		int[] col = {spawnAs(server, VehicleType.T72, x - 20, 300, 180, 777), spawnAs(server, VehicleType.BMP2, x, 306, 180, 777),
				spawnAs(server, VehicleType.BTR82, x + 20, 300, 180, 777), spawnAs(server, VehicleType.SUPPLY_TRUCK, x + 8, 322, 180, 777),
				spawnAs(server, VehicleType.T72, x + 150, 330, 180, 777), spawnAs(server, VehicleType.BTR82, x + 168, 336, 180, 777),
				spawnAs(server, VehicleType.BMP2, x - 150, 330, 180, 777)};
		List<Integer> colList = java.util.Arrays.stream(col).boxed().toList();
		ctx.waitTicks(200);
		look(server, x - 30, ground + 6, 26, x - 22, ground + 1.5, 0);
		ctx.waitTicks(20);
		ctx.takeScreenshot("u1_lineup");
		look(server, x - 47, ground + 4, 12, x - 40, ground + 2.2, -1);
		ctx.waitTicks(10);
		ctx.takeScreenshot("u1b_orlan_catapult");
		look(server, x + 22, ground + 6, 26, x + 28, ground + 3, 0);
		ctx.waitTicks(10);
		ctx.takeScreenshot("u1c_ew_masts");
		int jammers = server.computeOnServer(s -> com.stasdoto.airdefense.drone.Jammers.jammers(s.overworld()).size());

		// The Orlan over the column.
		int spotted0 = com.stasdoto.airdefense.drone.Recon.spotted;
		boolean orlanOk = server.computeOnServer(s -> s.overworld().getEntity(own[0]) instanceof VehicleEntity v
				&& v.commandStrike(new BlockPos(x, ground, 300), null));
		look(server, x - 54, ground + 5, 10, x - 40, ground + 3, 8);
		int up = waitUntil(ctx, () -> server.computeOnServer(s -> droneOf(s.overworld(), MissileType.ORLAN10, x) != null), 300);
		ctx.waitTicks(6);
		ctx.takeScreenshot("u2_orlan_launch");
		int circling = waitUntil(ctx, () -> server.computeOnServer(s -> {
			MissileEntity m = droneOf(s.overworld(), MissileType.ORLAN10, x);
			return m != null && m.circling();
		}), 1200);
		ctx.waitTicks(80);
		Vec3 dp = server.computeOnServer(s -> {
			MissileEntity m = droneOf(s.overworld(), MissileType.ORLAN10, x);
			return m == null ? new Vec3(x, ground + 50, 300) : m.position().add(m.getFlightVelocity().scale(4));
		});
		look(server, dp.x - 7, dp.y + 2.5, dp.z - 7, dp.x, dp.y, dp.z);
		ctx.waitTicks(3);
		ctx.takeScreenshot("u3_orlan_over");
		look(server, x + 45, ground + 22, 255, x, ground + 1, 308);
		ctx.waitTicks(20);
		ctx.takeScreenshot("u4_column_marked");
		int spotted = com.stasdoto.airdefense.drone.Recon.spotted - spotted0;
		int onMap = server.computeOnServer(s -> com.stasdoto.airdefense.drone.Recon.seen(s.overworld(), -1).size());

		// The tablet's map: the drone and what it sees.
		server.runCommand("gamemode creative @a");
		camera(server, x + 0.5, ground, -20.5, 0, 0);
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(40);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.runOnClient(mc -> ((com.stasdoto.airdefense.client.map.TacticalMapScreen) mc.gui.screen()).centerOn(x, 260, 3));
		ctx.waitTicks(40);
		ctx.takeScreenshot("u5_map_recon");
		int[] mapSeen = ctx.computeOnClient(mc -> new int[]{com.stasdoto.airdefense.client.map.MapClient.spots().size(),
				com.stasdoto.airdefense.client.map.MapClient.eyes().size()});
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		AirDefense.LOGGER.info("[airdefense-test] RESULT uav_recon: jammers up {}, ordered {}, launched after {} ticks, circling after {}, spotted {}, "
				+ "on the side's map {}, on the client's map {} marks and {} drones", jammers, orlanOk, up, circling, spotted, onMap, mapSeen[0], mapSeen[1]);

		// The guns: one fires at the column the Orlan watches, the other at a point it does not; where the rounds fall.
		int gunA = spawnVehicle(server, VehicleType.MSTA_S, x - 70, -40, 0);
		int gunB = spawnVehicle(server, VehicleType.MSTA_S, x + 70, -40, 0);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(gunA, gunB), v -> v.country = -1));
		ctx.waitTicks(20);
		int landed0 = MissileEntity.ARTY_LANDED.size();
		int corr0 = com.stasdoto.airdefense.drone.Recon.corrected;
		BlockPos pa = new BlockPos(x, ground, 300);
		BlockPos pb = new BlockPos(x + 360, ground, 300);
		boolean watchedNow = server.computeOnServer(s -> {
			boolean w = com.stasdoto.airdefense.drone.Recon.watched(s.overworld(), Vec3.atCenterOf(pa), -1);
			forVehicles(s.overworld(), List.of(gunA), v -> v.commandFire(pa, null, 8));
			forVehicles(s.overworld(), List.of(gunB), v -> v.commandFire(pb, null, 8));
			return w;
		});
		look(server, x + 40, ground + 14, 262, x, ground + 1, 304);
		waitUntil(ctx, () -> MissileEntity.ARTY_LANDED.size() > landed0, 900);
		ctx.waitTicks(2);
		ctx.takeScreenshot("u12_guns_corrected");
		waitUntil(ctx, () -> MissileEntity.ARTY_LANDED.size() >= landed0 + 16, 900);
		double sa = 0;
		double sb = 0;
		int na = 0;
		int nb = 0;
		synchronized (MissileEntity.ARTY_LANDED) {
			for (int i = landed0; i < MissileEntity.ARTY_LANDED.size(); i++) {
				Vec3 l = MissileEntity.ARTY_LANDED.get(i);
				double da = Math.sqrt(Mth.square(l.x - pa.getX() - 0.5) + Mth.square(l.z - pa.getZ() - 0.5));
				double db = Math.sqrt(Mth.square(l.x - pb.getX() - 0.5) + Mth.square(l.z - pb.getZ() - 0.5));
				if (da < db) {
					sa += da;
					na++;
				} else {
					sb += db;
					nb++;
				}
			}
		}
		AirDefense.LOGGER.info(String.format(java.util.Locale.ROOT, "[airdefense-test] RESULT uav_guns: drone over the aim %s, rounds corrected %d; "
				+ "watched point: %d rounds, %.1f blocks off on average; unwatched: %d rounds, %.1f blocks off", watchedNow,
				com.stasdoto.airdefense.drone.Recon.corrected - corr0, na, na == 0 ? -1 : sa / na, nb, nb == 0 ? -1 : sb / nb));

		// A Lancet at the tank by (x+150, 330).
		int hits0 = MissileEntity.LOITER_HITS.get();
		int prey0 = MissileEntity.PREY_FOUND.get();
		float t72hp0 = server.computeOnServer(s -> s.overworld().getEntity(col[4]) instanceof VehicleEntity v ? v.getHealth() : -1f);
		boolean lancetOk = server.computeOnServer(s -> s.overworld().getEntity(own[2]) instanceof VehicleEntity v
				&& v.commandStrike(new BlockPos(x + 150, ground, 320), null));
		look(server, x - 14, ground + 4, 12, x - 8, ground + 2.5, 2);
		waitUntil(ctx, () -> server.computeOnServer(s -> droneOf(s.overworld(), MissileType.LANCET, x) != null), 300);
		ctx.waitTicks(5);
		ctx.takeScreenshot("u6_lancet_launch");
		// The camera by the tank before the Lancet gets there (its ground drawn by then).
		look(server, x + 132, ground + 7, 310, x + 150, ground + 8, 330);
		int found = waitUntil(ctx, () -> MissileEntity.PREY_FOUND.get() > prey0, 1200);
		ctx.waitTicks(7);
		ctx.takeScreenshot("u7_lancet_dive");
		int hit = waitUntil(ctx, () -> MissileEntity.LOITER_HITS.get() > hits0, 400);
		ctx.waitTicks(3);
		ctx.takeScreenshot("u8_lancet_hit");
		float t72hp1 = server.computeOnServer(s -> s.overworld().getEntity(col[4]) instanceof VehicleEntity v && v.isAlive() ? v.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT uav_lancet: ordered {}, prey found after {} ticks, hit after {} more ({} hits), tank health {} -> {}",
				lancetOk, found, hit, MissileEntity.LOITER_HITS.get() - hits0, (int) t72hp0, (int) t72hp1);

		// The TB2 off the road in front of its station, its bombs on the BMP by (x-150, 330).
		int bombs0 = MissileEntity.BOMBS_DROPPED.get();
		float bmphp0 = server.computeOnServer(s -> s.overworld().getEntity(col[6]) instanceof VehicleEntity v ? v.getHealth() : -1f);
		boolean tb2Ok = server.computeOnServer(s -> s.overworld().getEntity(own[1]) instanceof VehicleEntity v
				&& v.commandStrike(new BlockPos(x - 150, ground, 320), null));
		look(server, x - 34, ground + 5, 22, x - 24, ground + 2, 8);
		waitUntil(ctx, () -> server.computeOnServer(s -> droneOf(s.overworld(), MissileType.TB2, x) != null), 300);
		ctx.waitTicks(15);
		ctx.takeScreenshot("u9_tb2_takeoff");
		look(server, x - 126, ground + 5, 316, x - 150, ground + 14, 330);
		int dropped = waitUntil(ctx, () -> MissileEntity.BOMBS_DROPPED.get() > bombs0, 1500);
		ctx.waitTicks(14);
		ctx.takeScreenshot("u10_tb2_bomb");
		waitUntil(ctx, () -> server.computeOnServer(s -> !(s.overworld().getEntity(col[6]) instanceof VehicleEntity v) || v.getHealth() < bmphp0), 200);
		ctx.waitTicks(2);
		ctx.takeScreenshot("u10b_tb2_bomb_hit");
		ctx.waitTicks(200);
		float bmphp1 = server.computeOnServer(s -> s.overworld().getEntity(col[6]) instanceof VehicleEntity v && v.isAlive() ? v.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT uav_tb2: ordered {}, first bomb after {} ticks, bombs {}, BMP health {} -> {}", tb2Ok, dropped,
				MissileEntity.BOMBS_DROPPED.get() - bombs0, (int) bmphp0, (int) bmphp1);

		// A Switchblade at the BTR by (x+168, 336).
		int hits1 = MissileEntity.LOITER_HITS.get();
		look(server, x + 17, ground + 4, 12, x + 8, ground + 2.5, 0);
		ctx.waitTicks(40);
		boolean swOk = server.computeOnServer(s -> s.overworld().getEntity(own[3]) instanceof VehicleEntity v
				&& v.commandStrike(new BlockPos(x + 168, ground, 340), null));
		waitUntil(ctx, () -> server.computeOnServer(s -> droneOf(s.overworld(), MissileType.SWITCHBLADE, x) != null), 300);
		ctx.waitTicks(3);
		ctx.takeScreenshot("u11_switchblade_launch");
		int swHit = waitUntil(ctx, () -> MissileEntity.LOITER_HITS.get() > hits1, 1500);
		AirDefense.LOGGER.info("[airdefense-test] RESULT uav_switchblade: ordered {}, hit after {} ticks", swOk, swHit);

		// Electronic warfare: an enemy Bukovel by an enemy BTR - the player's Lancet loses its link on the way in; an
		// enemy Shahed over the player's jammers strays off its aim.
		int ebuk = spawnAs(server, VehicleType.BUKOVEL, x - 420, 20, 90, 777);
		int ebtr = spawnAs(server, VehicleType.BTR82, x - 400, 40, 90, 777);
		ctx.waitTicks(220);
		int lost0 = com.stasdoto.airdefense.drone.Jammers.linksLost;
		int drift0 = com.stasdoto.airdefense.drone.Jammers.drifted;
		int hits2 = MissileEntity.LOITER_HITS.get();
		float ebtrhp0 = server.computeOnServer(s -> s.overworld().getEntity(ebtr) instanceof VehicleEntity v ? v.getHealth() : -1f);
		int enemyJammers = server.computeOnServer(s -> com.stasdoto.airdefense.drone.Jammers.jammers(s.overworld()).size());
		boolean jamOk = server.computeOnServer(s -> {
			boolean ok = s.overworld().getEntity(own[2]) instanceof VehicleEntity v && v.commandStrike(new BlockPos(x - 400, ground, 40), null);
			MissileEntity sh = MissileEntity.launchStrike(s.overworld(), MissileType.SHAHED, new Vec3(x + 30, ground + 70, -328),
					new Vec3(x + 30, ground, 80), new Vec3(0, 0, 1));
			sh.setCountry(777);
			return ok;
		});
		int jammed = waitUntil(ctx, () -> com.stasdoto.airdefense.drone.Jammers.linksLost > lost0, 900);
		Vec3 jp = server.computeOnServer(s -> {
			MissileEntity m = droneOf(s.overworld(), MissileType.LANCET, x);
			return m == null ? new Vec3(x - 300, ground + 30, 30) : m.position().add(m.getFlightVelocity().scale(4));
		});
		look(server, jp.x + 4, jp.y + 2, jp.z - 5, jp.x, jp.y - 1, jp.z);
		ctx.waitTicks(3);
		ctx.takeScreenshot("u13_lancet_jammed");
		int drifted = waitUntil(ctx, () -> com.stasdoto.airdefense.drone.Jammers.drifted > drift0, 900);
		ctx.waitTicks(300);
		float ebtrhp1 = server.computeOnServer(s -> s.overworld().getEntity(ebtr) instanceof VehicleEntity v && v.isAlive() ? v.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT uav_ew: jammers {}, Lancet ordered {}, link lost after {} ticks ({}), hits {}, enemy BTR health {} -> {}; "
				+ "Shahed astray after {} ticks ({})", enemyJammers, jamOk, jammed, com.stasdoto.airdefense.drone.Jammers.linksLost - lost0,
				MissileEntity.LOITER_HITS.get() - hits2, (int) ebtrhp0, (int) ebtrhp1, drifted, com.stasdoto.airdefense.drone.Jammers.drifted - drift0);

		// A country at war: its recon drone over the player's town, a loitering munition at his tank.
		int[] towns = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var mine = com.stasdoto.airdefense.nation.Nations.countryOf(l, p, pl, true);
			int id = p.newId();
			BlockPos at = new BlockPos(x, ground, -600);
			var town = new com.stasdoto.airdefense.nation.Settlement(id, "Сосновка", at, at.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(id, town);
			town.country = mine.id;
			int eid = p.newId();
			BlockPos eat = new BlockPos(x + 650, ground, -600);
			var et = new com.stasdoto.airdefense.nation.Settlement(eid, "Дронск", eat, eat.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(eid, et);
			var enemy = com.stasdoto.airdefense.nation.Nations.newWorldCountry(p, eid);
			et.country = enemy.id;
			com.stasdoto.airdefense.nation.War.declare(l, p, enemy, mine, net.minecraft.network.chat.Component.literal("test"));
			for (int t : new int[]{id, eid}) {
				var ar = com.stasdoto.airdefense.nation.Arsenals.get(s).of(p, p.settlements.get(t));
				ar.units.clear();
				ar.stock.clear();
			}
			return new int[]{id, eid, enemy.id};
		});
		camera(server, x + 0.5, ground + 30, -640.5, 0, 25);
		ctx.waitTicks(40);
		int flights0 = com.stasdoto.airdefense.nation.Arsenals.reconFlights;
		boolean sent = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			return com.stasdoto.airdefense.nation.Arsenals.reconNow(s.overworld(), p.settlements.get(towns[1]), p.settlements.get(towns[0]));
		});
		MissileType enemyRecon = server.computeOnServer(s -> droneOf(s.overworld(), MissileType.ORLAN10, x) != null ? MissileType.ORLAN10 : MissileType.TB2);
		int over = waitUntil(ctx, () -> server.computeOnServer(s -> {
			MissileEntity m = droneOf(s.overworld(), MissileType.ORLAN10, x + 300);
			MissileEntity t = droneOf(s.overworld(), MissileType.TB2, x + 300);
			MissileEntity d = m != null && m.country() == towns[2] ? m : t != null && t.country() == towns[2] ? t : null;
			return d != null && d.circling();
		}), 1200);
		ctx.waitTicks(40);
		Vec3 ep = server.computeOnServer(s -> {
			MissileEntity m = droneOf(s.overworld(), MissileType.ORLAN10, x + 300);
			if (m == null || m.country() != towns[2]) {
				m = droneOf(s.overworld(), MissileType.TB2, x + 300);
			}
			return m == null ? new Vec3(x, ground + 55, -600) : m.position().add(m.getFlightVelocity().scale(4));
		});
		look(server, ep.x - 9, ep.y - 2, ep.z - 9, ep.x, ep.y, ep.z);
		ctx.waitTicks(3);
		ctx.takeScreenshot("u14_enemy_recon");
		boolean enemyWatches = server.computeOnServer(s -> com.stasdoto.airdefense.drone.Recon.watched(s.overworld(), new Vec3(x, ground, -600), towns[2]));
		AirDefense.LOGGER.info("[airdefense-test] RESULT uav_ai_recon: sent {} ({} flights), circling over the town after {} ticks, the town watched by them {}",
				sent, com.stasdoto.airdefense.nation.Arsenals.reconFlights - flights0, over, enemyWatches);

		int tank = spawnAs(server, VehicleType.T72, x + 100, -580, 90, -1);
		camera(server, x + 70.5, ground + 12, -620.5, -40, 10);
		ctx.waitTicks(40);
		int hits3 = MissileEntity.LOITER_HITS.get();
		float tankhp0 = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getHealth() : -1f);
		boolean hunt = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			return com.stasdoto.airdefense.nation.Arsenals.huntNow(s.overworld(), p.settlements.get(towns[1]));
		});
		int huntHit = waitUntil(ctx, () -> MissileEntity.LOITER_HITS.get() > hits3, 1500);
		look(server, x + 85, ground + 6, -600, x + 100, ground + 1, -580);
		ctx.waitTicks(5);
		ctx.takeScreenshot("u15_enemy_lancet_hit");
		float tankhp1 = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v && v.isAlive() ? v.getHealth() : 0f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT uav_ai_hunt: sent {} ({}), hit after {} ticks, the player's tank health {} -> {}", hunt,
				com.stasdoto.airdefense.nation.Arsenals.hunts, huntHit, (int) tankhp0, (int) tankhp1);

		server.runOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var enemy = p.country(towns[2]);
			if (enemy != null) {
				for (var c : p.countries.values()) {
					c.wars.remove(enemy.id);
				}
				enemy.wars.clear();
			}
			forVehicles(s.overworld(), ownList, Entity::discard);
			forVehicles(s.overworld(), colList, Entity::discard);
			forVehicles(s.overworld(), List.of(gunA, gunB, ebuk, ebtr, tank), Entity::discard);
		});
		server.runCommand(String.format("forceload remove %d %d %d %d", x - 176, 288, x + 184, 352));
		server.runCommand(String.format("forceload remove %d %d %d %d", x - 432, 8, x - 384, 56));
		server.runCommand(String.format("forceload remove %d %d %d %d", x + 16, -336, x + 48, -320));
		language(ctx, "en_us");
	}

	/**
	 * 1.32.1: a country of ten villages at war with the player's town: its strikes come in waves (one to three launch
	 * sites, one warning), not ten villages each on its own.
	 */
	private void strikeWave(ClientGameTestContext ctx, TestServerContext server) {
		int x = 140000;
		server.runCommand("gamemode spectator @a");
		camera(server, x + 0.5, ground + 30, 0.5, 90, 20);
		ctx.waitTicks(40);
		int waves0 = com.stasdoto.airdefense.nation.Arsenals.waves;
		int sites0 = com.stasdoto.airdefense.nation.Arsenals.waveSites;
		int strikes0 = com.stasdoto.airdefense.nation.Arsenals.strikes;
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var mine = com.stasdoto.airdefense.nation.Nations.countryOf(l, p, pl, true);
			int id = p.newId();
			BlockPos at = new BlockPos(x, ground, 0);
			var town = new com.stasdoto.airdefense.nation.Settlement(id, "Ключи", at, at.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(id, town);
			town.country = mine.id;
			com.stasdoto.airdefense.nation.Country enemy = null;
			for (int i = 0; i < 10; i++) {
				int vid = p.newId();
				double a = i * Math.PI / 5;
				BlockPos vat = new BlockPos(x + (int) (Math.cos(a) * 900), ground, (int) (Math.sin(a) * 900));
				var v = new com.stasdoto.airdefense.nation.Settlement(vid, "Озерки " + i, vat, vat.above(2), -1, java.util.Optional.empty(), 0,
						java.util.Map.of(), List.of(), List.of());
				p.settlements.put(vid, v);
				if (enemy == null) {
					enemy = com.stasdoto.airdefense.nation.Nations.newWorldCountry(p, vid);
				}
				v.country = enemy.id;
			}
			com.stasdoto.airdefense.nation.War.declare(l, p, enemy, mine, net.minecraft.network.chat.Component.literal("test"));
		});
		ctx.waitTicks(3000);
		AirDefense.LOGGER.info("[airdefense-test] RESULT strike_waves: in 3000 ticks {} waves from {} launch sites ({} strikes)",
				com.stasdoto.airdefense.nation.Arsenals.waves - waves0, com.stasdoto.airdefense.nation.Arsenals.waveSites - sites0,
				com.stasdoto.airdefense.nation.Arsenals.strikes - strikes0);
		ctx.takeScreenshot("sw_chat");
	}

	/**
	 * 1.32.2: what had piled up round the towns in old worlds is cleared away - a town of somebody's (and the same town
	 * founded a second time), with a dozen leftover garrison vehicles, forty attackers and fifteen guards round its flag,
	 * stray banners round its square and five sirens in a row.
	 */
	private void cleanup(ClientGameTestContext ctx, TestServerContext server) {
		int x = 150000;
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 3000");
		camera(server, x + 0.5, ground + 30, -40, 0, 35);
		ctx.waitTicks(60);
		int[] ids = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			int id = p.newId();
			BlockPos at = new BlockPos(x, ground, 0);
			var town = new com.stasdoto.airdefense.nation.Settlement(id, "Ключи", at, at.offset(2, 0, 0), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			// A city west of x = 0: its key is negative.
			town.city = -987654321L;
			town.population = 15;
			p.settlements.put(id, town);
			var country = com.stasdoto.airdefense.nation.Nations.newWorldCountry(p, id);
			town.country = country.id;
			// The same town founded again.
			int id2 = p.newId();
			var twin = new com.stasdoto.airdefense.nation.Settlement(id2, "Ключи 2", at, at.offset(0, 0, 2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			twin.city = -987654321L;
			twin.country = country.id;
			p.settlements.put(id2, twin);
			// An enemy at war with it.
			int eid = p.newId();
			var et = new com.stasdoto.airdefense.nation.Settlement(eid, "Вражеск", new BlockPos(x + 3000, ground, 0), new BlockPos(x + 3000, ground + 2, 0), -1,
					java.util.Optional.empty(), 0, java.util.Map.of(), List.of(), List.of());
			p.settlements.put(eid, et);
			var enemy = com.stasdoto.airdefense.nation.Nations.newWorldCountry(p, eid);
			et.country = enemy.id;
			com.stasdoto.airdefense.nation.War.declare(l, p, enemy, country, net.minecraft.network.chat.Component.literal("test"));
			var r = new java.util.Random(3);
			// A dozen leftover garrison vehicles in a heap by the square.
			VehicleType[] heap = {VehicleType.BUK, VehicleType.TOR, VehicleType.PANTSIR, VehicleType.ISKANDER, VehicleType.SHAHED, VehicleType.T72};
			for (int i = 0; i < 12; i++) {
				VehicleEntity v = VehicleEntity.spawn(l, heap[i % heap.length], new Vec3(x - 20 + (i % 4) * 2.5, ground, 20 + (i / 4) * 3.0), r.nextFloat() * 360);
				v.garrison = true;
				v.home = id;
				v.country = country.id;
			}
			// Forty attackers and fifteen guards round the flag.
			for (int i = 0; i < 40; i++) {
				var e = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, enemy.id, enemy.color, eid,
						new Vec3(x + 4 + r.nextGaussian() * 4, ground, 6 + r.nextGaussian() * 4), r.nextInt());
				e.setNoAi(true);
				l.addFreshEntity(e);
			}
			for (int i = 0; i < 15; i++) {
				var g = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.GUARD, country.id, country.color, id,
						new Vec3(x - 4 + r.nextGaussian() * 3, ground, 4 + r.nextGaussian() * 3), r.nextInt());
				g.setNoAi(true);
				l.addFreshEntity(g);
			}
			// Stray banners round the square, five sirens in a row on the pavement.
			for (int k = 3; k <= 5; k++) {
				l.setBlockAndUpdate(new BlockPos(x - k, ground, 0), net.minecraft.world.level.block.Blocks.BANNER.pick(net.minecraft.world.item.DyeColor.BLUE).defaultBlockState());
				l.setBlockAndUpdate(new BlockPos(x, ground, -k), net.minecraft.world.level.block.Blocks.BANNER.pick(net.minecraft.world.item.DyeColor.BLUE).defaultBlockState());
			}
			var sirens = com.stasdoto.airdefense.siren.Sirens.get(s);
			for (int k = 0; k < 5; k++) {
				BlockPos head = new BlockPos(x + 8 + k, ground + com.stasdoto.airdefense.siren.SirenItem.MAST, 12);
				com.stasdoto.airdefense.siren.SirenItem.buildMast(l, head, net.minecraft.core.Direction.SOUTH);
				l.setBlock(head, com.stasdoto.airdefense.registry.ModBlocks.SIREN.defaultBlockState(), 3);
				sirens.register(head);
			}
			return new int[]{id, id2, eid, country.id};
		});
		ctx.waitTicks(20);
		String before = cleanupCounts(server, x, ids);
		look(server, x + 30, ground + 14, 40, x, ground + 1, 6);
		ctx.waitTicks(20);
		ctx.takeScreenshot("cl1_piled_up");
		// The player watches from a little way off (nothing is taken from right under his nose).
		camera(server, x + 0.5, ground + 30, -40, 0, 35);
		ctx.waitTicks(700);
		String after = cleanupCounts(server, x, ids);
		look(server, x + 30, ground + 14, 40, x, ground + 1, 6);
		ctx.waitTicks(20);
		ctx.takeScreenshot("cl2_cleared");
		// A negative city key survives saving and loading.
		String keyBack = server.computeOnServer(s -> {
			var town = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(ids[0]);
			if (town == null) {
				return "town gone";
			}
			var back = com.stasdoto.airdefense.nation.Settlement.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, town)
					.flatMap(t -> com.stasdoto.airdefense.nation.Settlement.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, t));
			return back.result().map(b -> "city key " + b.city + " (is city " + b.isCity() + ")").orElse("error " + back.error());
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT cleanup: before {} | after {} | swept {} adopted {} thinned {} sirens down {} stray flags {} twins dropped {} | saved: {}",
				before, after, com.stasdoto.airdefense.nation.Arsenals.sweptVehicles, com.stasdoto.airdefense.nation.Arsenals.adoptedVehicles,
				com.stasdoto.airdefense.nation.Nations.thinned, com.stasdoto.airdefense.siren.Sirens.doubled,
				com.stasdoto.airdefense.nation.Nations.strayFlagsRemoved, com.stasdoto.airdefense.nation.Nations.refounded, keyBack);
		language(ctx, "en_us");
	}

	private String cleanupCounts(TestServerContext server, int x, int[] ids) {
		return server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var box = new net.minecraft.world.phys.AABB(x - 120, ground - 10, -120, x + 120, ground + 40, 120);
			int vehicles = l.getEntitiesOfClass(VehicleEntity.class, box, v -> v.isAlive() && v.garrison).size();
			int enemy = otherCountry(s, ids);
			int attackers = l.getEntitiesOfClass(com.stasdoto.airdefense.nation.SoldierEntity.class, box,
					e -> e.isAlive() && e.role() == com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER && e.country() == enemy).size();
			int guards = l.getEntitiesOfClass(com.stasdoto.airdefense.nation.SoldierEntity.class, box,
					e -> e.isAlive() && e.role() == com.stasdoto.airdefense.nation.SoldierEntity.GUARD).size();
			int banners = 0;
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					for (int dy = -1; dy <= 3; dy++) {
						if (l.getBlockState(new BlockPos(x + dx, ground + dy, dz)).getBlock() instanceof net.minecraft.world.level.block.BannerBlock) {
							banners++;
						}
					}
				}
			}
			int sirens = 0;
			for (int k = 0; k < 5; k++) {
				if (l.getBlockState(new BlockPos(x + 8 + k, ground + com.stasdoto.airdefense.siren.SirenItem.MAST, 12)).getBlock()
						instanceof com.stasdoto.airdefense.siren.SirenBlock) {
					sirens++;
				}
			}
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			int towns = (p.settlements.containsKey(ids[0]) ? 1 : 0) + (p.settlements.containsKey(ids[1]) ? 1 : 0);
			return "vehicles " + vehicles + ", attackers " + attackers + ", guards " + guards + ", banners " + banners + ", sirens " + sirens + ", towns " + towns;
		});
	}

	private static int otherCountry(net.minecraft.server.MinecraftServer s, int[] ids) {
		var p = com.stasdoto.airdefense.nation.Politics.get(s);
		var et = p.settlements.get(ids[2]);
		return et == null ? -99 : et.country;
	}

	/**
	 * 1.32: the air war - a town of the player's country, an enemy country at war with it 900 blocks east. An enemy
	 * attack helicopter comes in and fires at the town's soldiers; then one comes in over the player's Pantsir and is shot
	 * down (it falls burning); then a jet makes bombing runs.
	 */
	private void airWar(ClientGameTestContext ctx, TestServerContext server) {
		int x = 120000;
		language(ctx, "ru_ru");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 3000");
		// The new aircraft first: the AH-64 and the A-10 on the ground, close.
		int lx = x - 400;
		camera(server, lx + 0.5, ground + 6, -30, 0, 10);
		ctx.waitTicks(40);
		int apache = spawnVehicle(server, VehicleType.AH64, lx, 0, 30);
		int warthog = spawnVehicle(server, VehicleType.A10, lx + 24, 0, 30);
		ctx.waitTicks(30);
		// From the front quarter (both face yaw 30).
		look(server, lx + 4, ground + 3.5, 12.5, lx, ground + 1.8, 0);
		ctx.waitTicks(20);
		ctx.takeScreenshot("w0_ah64");
		look(server, lx + 28.5, ground + 5, 16.5, lx + 24, ground + 1.5, 0);
		ctx.waitTicks(20);
		ctx.takeScreenshot("w0b_a10");
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(apache, warthog), Entity::discard));
		camera(server, x + 0.5, ground + 6, -30, 0, 10);
		ctx.waitTicks(40);
		int[] ids = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var mine = com.stasdoto.airdefense.nation.Nations.countryOf(l, p, pl, true);
			int id = p.newId();
			BlockPos at = new BlockPos(x, ground, 0);
			var town = new com.stasdoto.airdefense.nation.Settlement(id, "Небесное", at, at.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(id, town);
			town.country = mine.id;
			int eid = p.newId();
			BlockPos eat = new BlockPos(x + 900, ground, 0);
			var et = new com.stasdoto.airdefense.nation.Settlement(eid, "Вражеск", eat, eat.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(eid, et);
			var enemy = com.stasdoto.airdefense.nation.Nations.newWorldCountry(p, eid);
			et.country = enemy.id;
			com.stasdoto.airdefense.nation.War.declare(l, p, enemy, mine, net.minecraft.network.chat.Component.literal("test"));
			// Neither village keeps a garrison here: the first helicopter meets no air defence, and no missiles fly.
			for (int t : new int[]{id, eid}) {
				var ar = com.stasdoto.airdefense.nation.Arsenals.get(s).of(p, p.settlements.get(t));
				ar.units.clear();
				ar.stock.clear();
			}
			// The town's soldiers on the square.
			for (int i = 0; i < 4; i++) {
				var so = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, mine.id, 5, id,
						new Vec3(x - 6 + i * 4, ground, 4), 7);
				l.addFreshEntity(so);
			}
			return new int[]{id, enemy.id, eid, mine.id};
		});
		// An attack helicopter (no air defence here yet).
		String heli = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var t = com.stasdoto.airdefense.nation.War.airStrike(s.overworld(), p, p.country(ids[1]), p.settlements.get(ids[0]), new java.util.Random(5),
					VehicleType.MI24);
			return t == null ? "-" : t.id;
		});
		int in = waitUntil(ctx, () -> server.computeOnServer(s -> {
			VehicleEntity v = sortie(s.overworld());
			return v != null && v.sortiePhase() >= 1;
		}), 900);
		Vec3 hAt = server.computeOnServer(s -> {
			VehicleEntity v = sortie(s.overworld());
			return v == null ? new Vec3(x + 100, ground + 30, 0) : v.position();
		});
		look(server, x - 30, ground + 8, -40, hAt.x, hAt.y, hAt.z);
		ctx.waitTicks(60);
		ctx.takeScreenshot("w1_heli_attacks");
		int salvos0 = VehicleEntity.aiRocketSalvos;
		waitUntil(ctx, () -> VehicleEntity.aiRocketSalvos > salvos0 || VehicleEntity.aiGunBursts > 0, 600);
		Vec3 hAt2 = server.computeOnServer(s -> {
			VehicleEntity v = sortie(s.overworld());
			return v == null ? new Vec3(x + 80, ground + 28, 0) : v.position();
		});
		// From beside the soldiers, looking up at it.
		look(server, x - 12, ground + 4, -14, hAt2.x, hAt2.y, hAt2.z);
		ctx.waitTicks(8);
		ctx.takeScreenshot("w2_heli_fires");
		ctx.waitTicks(300);
		int soldiersLeft = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(com.stasdoto.airdefense.nation.SoldierEntity.class,
				new net.minecraft.world.phys.AABB(x - 40, ground - 5, -40, x + 40, ground + 20, 40), e -> e.isAlive()).size());
		AirDefense.LOGGER.info("[airdefense-test] RESULT air_heli: {} sent, over the town after {} ticks, rocket salvos {}, gun bursts {}, "
				+ "soldiers left {} of 4", heli, in, VehicleEntity.aiRocketSalvos, VehicleEntity.aiGunBursts, soldiersLeft);
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getAllEntities()) {
				if (e instanceof VehicleEntity v && v.onSortie()) {
					v.discard();
				}
			}
		});

		// Now with the player's Pantsir in the town: the next helicopter is shot down.
		int pantsir = spawnVehicle(server, VehicleType.PANTSIR, x + 10, 10, 90);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(pantsir), v -> v.country = -1));
		ctx.waitTicks(60);
		int down0 = VehicleEntity.aircraftDown;
		int hits0 = com.stasdoto.airdefense.missile.MissileEntity.AIRCRAFT_MISSILE_HITS.get() + com.stasdoto.airdefense.missile.MissileEntity.AIRCRAFT_GUN_HITS.get();
		server.runOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			com.stasdoto.airdefense.nation.War.airStrike(s.overworld(), p, p.country(ids[1]), p.settlements.get(ids[0]), new java.util.Random(6),
					VehicleType.KA52);
		});
		look(server, x - 20, ground + 6, -25, x + 120, ground + 30, 0);
		int downAfter = waitUntil(ctx, () -> VehicleEntity.aircraftDown > down0, 1200);
		ctx.takeScreenshot("w3_heli_shot_down");
		// Then close to the falling helicopter, from its side.
		Vec3 fall = server.computeOnServer(s -> {
			for (Entity e : s.overworld().getAllEntities()) {
				if (e instanceof VehicleEntity v && !v.isAlive() && v.onSortie()) {
					return v.position();
				}
			}
			return new Vec3(x + 150, ground + 20, 0);
		});
		look(server, fall.x - 18, Math.max(ground + 3, fall.y - 6), fall.z - 34, fall.x, fall.y - 6, fall.z);
		ctx.waitTicks(12);
		ctx.takeScreenshot("w4_heli_falls");
		ctx.waitTicks(100);
		AirDefense.LOGGER.info("[airdefense-test] RESULT air_defence: aircraft shot down {} after {} ticks, hits on it {} (missiles {}, gun {})",
				VehicleEntity.aircraftDown - down0, downAfter,
				com.stasdoto.airdefense.missile.MissileEntity.AIRCRAFT_MISSILE_HITS.get() + com.stasdoto.airdefense.missile.MissileEntity.AIRCRAFT_GUN_HITS.get() - hits0,
				com.stasdoto.airdefense.missile.MissileEntity.AIRCRAFT_MISSILE_HITS.get(), com.stasdoto.airdefense.missile.MissileEntity.AIRCRAFT_GUN_HITS.get());
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(pantsir), Entity::discard));
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getAllEntities()) {
				if (e instanceof VehicleEntity v && v.onSortie()) {
					v.discard();
				}
			}
		});

		// A jet: bombing runs over the town.
		int bombs0 = VehicleEntity.aiBombs;
		server.runOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			com.stasdoto.airdefense.nation.War.airStrike(s.overworld(), p, p.country(ids[1]), p.settlements.get(ids[0]), new java.util.Random(7),
					VehicleType.A10);
		});
		look(server, x - 60, ground + 15, -60, x, ground + 20, 0);
		int bombed = waitUntil(ctx, () -> VehicleEntity.aiBombs > bombs0, 900);
		ctx.waitTicks(8);
		ctx.takeScreenshot("w5_jet_bombs");
		ctx.waitTicks(45);
		ctx.takeScreenshot("w6_bombs_land");
		ctx.waitTicks(400);
		String jet = server.computeOnServer(s -> {
			VehicleEntity v = sortie(s.overworld());
			return v == null ? "gone" : "phase " + v.sortiePhase();
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT air_jet: bombs dropped {} (first after {} ticks); then {}; sorties {}",
				VehicleEntity.aiBombs - bombs0, bombed, jet, VehicleEntity.sortiesFlown);
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getAllEntities()) {
				if (e instanceof VehicleEntity v && v.onSortie()) {
					v.discard();
				}
			}
		});
		language(ctx, "en_us");
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

	/** 1.27: helmets, vests and pouches in 3D on soldiers, a zombie, an armour stand and the player; the pouches at work. */
	private void gear(ClientGameTestContext ctx, TestServerContext server) {
		int x = 70000;
		camera(server, x + 0.5, ground, -5.5, 0, 6);
		ctx.waitTicks(40);
		server.runCommand("time set 1000");
		server.runCommand("gamemode creative @a");
		List<Integer> ids = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			List<Integer> out = new ArrayList<>();
			com.stasdoto.airdefense.gear.Pouch M = com.stasdoto.airdefense.gear.Pouch.MAG;
			com.stasdoto.airdefense.gear.Pouch G = com.stasdoto.airdefense.gear.Pouch.GRENADE;
			com.stasdoto.airdefense.gear.Pouch K = com.stasdoto.airdefense.gear.Pouch.MEDKIT;
			com.stasdoto.airdefense.gear.Pouch R = com.stasdoto.airdefense.gear.Pouch.RADIO;
			com.stasdoto.airdefense.gear.Pouch N = com.stasdoto.airdefense.gear.Pouch.NONE;
			ItemStack[][] kits = {
					{new ItemStack(com.stasdoto.airdefense.registry.ModItems.HELMET), vestWith(com.stasdoto.airdefense.registry.ModItems.VEST_HEAVY, M, M, G, K, R, M)},
					{nvg(true), vestWith(com.stasdoto.airdefense.registry.ModItems.VEST, M, M, M, K, R, G)},
					{nvg(false), vestWith(com.stasdoto.airdefense.registry.ModItems.VEST, G, M, N, R, K, N)},
					{new ItemStack(com.stasdoto.airdefense.registry.ModItems.HELMET_FAST), vestWith(com.stasdoto.airdefense.registry.ModItems.VEST)},
			};
			for (int i = 0; i < kits.length; i++) {
				var m = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, i % 2, 5, -1,
						new Vec3(x - 3.0 + i * 2.0 + 0.5, ground, 0.5), i * 7 + 3);
				m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, kits[i][0]);
				m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, kits[i][1]);
				m.setNoAi(true);
				m.snapTo(m.getX(), m.getY(), m.getZ(), 180f, 0f);
				m.setYHeadRot(180f);
				m.setYBodyRot(180f);
				l.addFreshEntity(m);
				out.add(m.getId());
			}
			var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			z.snapTo(x + 5.5, ground, 0.5, 180f, 0f);
			z.setYHeadRot(180f);
			z.setYBodyRot(180f);
			z.setNoAi(true);
			z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(com.stasdoto.airdefense.registry.ModItems.HELMET));
			z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, vestWith(com.stasdoto.airdefense.registry.ModItems.VEST_HEAVY, M, K));
			l.addFreshEntity(z);
			var st = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			st.snapTo(x - 5.5, ground, 0.5, 180f, 0f);
			st.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, nvg(false));
			st.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, vestWith(com.stasdoto.airdefense.registry.ModItems.VEST, M, M, M, R, K, G));
			l.addFreshEntity(st);
			out.add(z.getId());
			out.add(st.getId());
			return out;
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("g1_gear_front");
		camera(server, x + 0.5, ground + 0.8, -2.6, 0, 12);
		ctx.waitTicks(15);
		ctx.takeScreenshot("g1b_gear_close");
		camera(server, x - 2.2, ground + 1.5, -1.6, 20, 24);
		ctx.waitTicks(15);
		ctx.takeScreenshot("g1c_gear_6b47_close");
		camera(server, x + 0.5, ground, 6.5, 180, 6);
		ctx.waitTicks(15);
		ctx.takeScreenshot("g2_gear_back");
		camera(server, x + 6.5, ground + 0.6, -1.5, 60, 10);
		ctx.waitTicks(15);
		ctx.takeScreenshot("g2b_gear_side");
		int drawn = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.gear.GearLayer.drawn);

		// Pouches put on and taken off with the mouse, as in the inventory.
		String clicks = server.computeOnServer(s -> {
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			pl.getInventory().clearContent();
			pl.getInventory().setItem(9, new ItemStack(com.stasdoto.airdefense.registry.ModItems.VEST));
			pl.containerMenu.setCarried(new ItemStack(com.stasdoto.airdefense.registry.ModItems.POUCH_MAG, 3));
			pl.containerMenu.clicked(9, 1, net.minecraft.world.inventory.ContainerInput.PICKUP, pl);
			pl.containerMenu.clicked(9, 1, net.minecraft.world.inventory.ContainerInput.PICKUP, pl);
			int on = com.stasdoto.airdefense.gear.Pouches.total(pl.getInventory().getItem(9));
			int left = pl.containerMenu.getCarried().getCount();
			pl.containerMenu.setCarried(ItemStack.EMPTY);
			pl.containerMenu.clicked(9, 1, net.minecraft.world.inventory.ContainerInput.PICKUP, pl);
			String back = pl.containerMenu.getCarried().getHoverName().getString();
			int after = com.stasdoto.airdefense.gear.Pouches.total(pl.getInventory().getItem(9));
			pl.containerMenu.setCarried(ItemStack.EMPTY);
			return "put on " + on + " (left in hand " + left + "), took off -> " + back + ", on the vest " + after;
		});

		// The player kitted out, seen from the front.
		server.runOnServer(s -> {
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			pl.getInventory().clearContent();
			pl.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, nvg(true));
			pl.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, vestWith(com.stasdoto.airdefense.registry.ModItems.VEST_HEAVY,
					com.stasdoto.airdefense.gear.Pouch.MAG, com.stasdoto.airdefense.gear.Pouch.MAG, com.stasdoto.airdefense.gear.Pouch.MAG,
					com.stasdoto.airdefense.gear.Pouch.MEDKIT, com.stasdoto.airdefense.gear.Pouch.RADIO, com.stasdoto.airdefense.gear.Pouch.GRENADE));
			pl.getInventory().setItem(0, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(
					com.stasdoto.airdefense.weapon.GunType.AK74)));
			pl.getInventory().setItem(1, new ItemStack(com.stasdoto.airdefense.registry.ModItems.THERMAL_MONOCULAR));
			pl.getInventory().setItem(5, new ItemStack(com.stasdoto.airdefense.registry.ModItems.F1_GRENADE, 2));
			pl.getInventory().setItem(6, new ItemStack(com.stasdoto.airdefense.registry.ModItems.MEDKIT, 2));
			pl.getInventory().setSelectedSlot(0);
		});
		camera(server, x + 0.5, ground, -8.5, 0, 0);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(15);
		ctx.takeScreenshot("g3_player_gear");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(10);
		ctx.takeScreenshot("g3b_player_gear_back");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		float reload = server.computeOnServer(s -> com.stasdoto.airdefense.gear.GearServer.reloadFactor(s.getPlayerList().getPlayers().getFirst()));

		// B: a grenade from the pouch, the rifle still in hand. H: a wound dressed from the kit (in survival, hurt).
		ctx.getInput().lookAt(0, -20);
		ctx.waitTicks(5);
		server.runCommand("gamemode survival @a");
		ctx.waitTicks(5);
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.weapon.GunClient.GRENADE);
		ctx.waitTicks(10);
		String grenade = server.computeOnServer(s -> {
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			return "thrown " + com.stasdoto.airdefense.gear.GearServer.grenadesThrown + ", left " + pl.getInventory().getItem(5).getCount()
					+ ", in hand " + pl.getMainHandItem().getItem();
		});
		ctx.waitTicks(80);
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setHealth(7f));
		ctx.waitTicks(3);
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.weapon.GunClient.MEDKIT);
		ctx.waitTicks(50);
		String medkit = server.computeOnServer(s -> {
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			return String.format(java.util.Locale.ROOT, "health 7 -> %.1f, dressed %d, kits left %d", pl.getHealth(),
					com.stasdoto.airdefense.gear.GearServer.woundsDressed, pl.getInventory().getItem(6).getCount());
		});
		// The ammunition crate: rounds for the rifle on the hotbar.
		String crate = server.computeOnServer(s -> {
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			pl.getInventory().setItem(8, new ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_CRATE));
			pl.getInventory().setSelectedSlot(8);
			pl.getInventory().getItem(8).getItem().use(s.overworld(), pl, net.minecraft.world.InteractionHand.MAIN_HAND);
			int rounds = 0;
			for (int i = 0; i < pl.getInventory().getContainerSize(); i++) {
				if (pl.getInventory().getItem(i).is(com.stasdoto.airdefense.registry.ModItems.AMMO_545)) {
					rounds += pl.getInventory().getItem(i).getCount();
				}
			}
			pl.getInventory().setSelectedSlot(0);
			return "5.45 rounds " + rounds + ", crates left " + pl.getInventory().getItem(8).getCount();
		});
		medkit = medkit + "; crate: " + crate;
		server.runCommand("gamemode creative @a");

		// The radio: a drone sent at a spot 40 blocks off.
		server.runOnServer(s -> MissileEntity.launchStrike(s.overworld(), MissileType.SHAHED, new Vec3(x + 120, ground + 60, 140),
				new Vec3(x + 40, ground, 40), new Vec3(-1, 0, -1).normalize()));
		ctx.waitTicks(60);
		int radio = server.computeOnServer(s -> com.stasdoto.airdefense.gear.GearServer.radioWarnings);
		ctx.takeScreenshot("g4_radio_chat");

		// The thermal monocular at night, then at 6x.
		server.runCommand("time set 15000");
		camera(server, x + 0.5, ground, -14.5, 0, 4);
		ctx.waitTicks(10);
		ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]);
		ctx.waitTicks(5);
		ctx.getInput().holdKey(o -> o.keyUse);
		ctx.waitTicks(25);
		ctx.takeScreenshot("g5_monocular_night");
		ctx.getInput().pressKey(o -> com.stasdoto.airdefense.client.vehicle.GunnerSight.ZOOM);
		ctx.waitTicks(10);
		ctx.takeScreenshot("g5b_monocular_6x");
		String mono = ctx.computeOnClient(mc -> "ticks " + com.stasdoto.airdefense.client.gear.MonocularView.ticksOn + " thermal "
				+ com.stasdoto.airdefense.client.vehicle.ThermalView.wanted() + " failed " + com.stasdoto.airdefense.client.vehicle.ThermalView.failed(mc));
		ctx.getInput().releaseKey(o -> o.keyUse);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(5);
		ctx.getInput().holdKey(o -> o.keyUse);
		ctx.waitTicks(10);
		ctx.takeScreenshot("g5c_monocular_third_person");
		ctx.getInput().releaseKey(o -> o.keyUse);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("time set 1000");

		// Soldiers of both blocs as the game dresses them.
		String dressed = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (int c = 0; c < 2; c++) {
				var m = com.stasdoto.airdefense.nation.SoldierEntity.create(s.overworld(), com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, c, 5, -1,
						new Vec3(x + 0.5, ground, 30.5), 1);
				ItemStack v = m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
				b.append(c == 0 ? "east " : " | west ").append(m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem())
						.append(" + ").append(v.getItem()).append(" pouches ").append(com.stasdoto.airdefense.gear.Pouches.total(v));
				m.discard();
			}
			return b.toString();
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT gear_drawn: {} pieces drawn; clicks: {}", drawn, clicks);
		AirDefense.LOGGER.info("[airdefense-test] RESULT gear_pouches: reload x{}; grenade: {}; medkit: {}; radio warnings {}", reload, grenade, medkit, radio);
		AirDefense.LOGGER.info("[airdefense-test] RESULT gear_monocular: {}; soldiers: {}", mono, dressed);
		server.runOnServer(s -> {
			for (int id : ids) {
				Entity e = s.overworld().getEntity(id);
				if (e != null) {
					e.discard();
				}
			}
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			pl.getInventory().clearContent();
		});
		server.runCommand("gamemode spectator @a");
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

	/** Camera in front of the first building of this type in the city: {x, y, z, yaw, pitch}, or null. */
	private static float[] facing(TestServerContext server, com.stasdoto.airdefense.nation.BuildingType type, int skip, double dist, double up,
			double side) {
		return server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), t, 0, 0).getFirst();
			int k = 0;
			for (var b : c.buildings()) {
				if (b.type != type || k++ < skip) {
					continue;
				}
				var f = b.facing;
				var right = f.getClockWise();
				double mx = b.origin.getX() + 0.5 + f.getStepX() * b.type.depth / 2.0;
				double mz = b.origin.getZ() + 0.5 + f.getStepZ() * b.type.depth / 2.0;
				double cx = b.origin.getX() + 0.5 - f.getStepX() * dist + right.getStepX() * side;
				double cz = b.origin.getZ() + 0.5 - f.getStepZ() * dist + right.getStepZ() * side;
				double cy = c.base + up;
				double dx = mx - cx;
				double dz = mz - cz;
				double dy = c.base + Math.min(b.type.height, 14) * 0.45 - cy;
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
				return new float[]{(float) cx, (float) cy, (float) cz, yaw, pitch};
			}
			return null;
		});
	}

	/** Cameras on the capital of cell (0, 0): hall, street, street from above, its first hamlet from above, the farm, a field; then that hamlet's area. */
	private static float[][] cityCams(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), t, 0, 0);
			if (list.isEmpty()) {
				return null;
			}
			var c = list.getFirst();
			var sh = c.shape();
			float[][] out = new float[7][];
			for (var b : c.buildings()) {
				if (b.type == com.stasdoto.airdefense.nation.BuildingType.CITY_HALL) {
					out[0] = look(b, 24, 7, c.base);
				}
			}
			int sx = sh.gx[sh.ci + 1];
			int sz = sh.gz[sh.cj + 1];
			out[1] = new float[]{sx + 0.5f, c.base + 3.2f, sz - 3.5f, 180, 4};
			out[2] = new float[]{sh.gx[sh.ci] - 0.5f, c.base + 16, sz + 8.5f, 160, 22};
			var hs = c.hamlets(l.getSeed(), t);
			StringBuilder sb = new StringBuilder();
			for (var h : hs) {
				java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
				for (var b : h.buildings) {
					kinds.merge(b.type.id, 1, Integer::sum);
				}
				sb.append(String.format(java.util.Locale.ROOT, "#%d at %d %d y%d (%d from city) %s fields %d road %d; ", h.index, h.x, h.z, h.base,
						(int) Math.hypot(h.x - c.x, h.z - c.z), kinds, h.fields.size(), (int) h.road.length));
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT hamlets: {} | {} (sites turned down: water/slope/road/near {})", hs.size(), sb,
					java.util.Arrays.toString(com.stasdoto.airdefense.nation.Hamlets.REJECTED));
			if (!hs.isEmpty()) {
				var h = hs.getFirst();
				out[3] = new float[]{h.x + 0.5f, h.base + 38, h.z + 55.5f, 180, 34};
				for (var b : h.buildings) {
					if (b.type == com.stasdoto.airdefense.nation.BuildingType.FARM) {
						out[4] = look(b, 17, 6, b.origin.getY());
					}
				}
				if (out[4] == null && !h.buildings.isEmpty()) {
					out[4] = look(h.buildings.getFirst(), 12, 4, h.buildings.getFirst().origin.getY());
				}
				if (!h.fields.isEmpty()) {
					var f = h.fields.get(h.fields.size() > 1 ? 1 : 0).pad();
					out[5] = new float[]{(f.x0() + f.x1()) / 2f + 0.5f, f.y() + 7, f.z1() + 10.5f, 180, 30};
				}
				out[6] = new float[]{h.minX, h.minZ, h.maxX, h.maxZ};
			}
			return out;
		});
	}

	/** A camera in front of a building, looking at its middle. */
	private static float[] look(com.stasdoto.airdefense.nation.Building b, double dist, double up, int y) {
		var f = b.facing;
		double mx = b.origin.getX() + 0.5 + f.getStepX() * b.type.depth / 2.0;
		double mz = b.origin.getZ() + 0.5 + f.getStepZ() * b.type.depth / 2.0;
		double cx = b.origin.getX() + 0.5 - f.getStepX() * dist;
		double cz = b.origin.getZ() + 0.5 - f.getStepZ() * dist;
		double cy = y + up;
		double dx = mx - cx;
		double dz = mz - cz;
		double dy = y + Math.min(b.type.height, 14) * 0.45 - cy;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		return new float[]{(float) cx, (float) cy, (float) cz, yaw, pitch};
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

	private void cityLook(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 5000");
		server.runCommand("difficulty peaceful");
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), 0, 0).getFirst();
			return new int[]{c.x, c.z, c.half(), c.base};
		});
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		camera(server, cx + 0.5, base + 70, cz + half + 90, 180, 35);
		ctx.waitTicks(60);
		int m = half + 20;
		generateCity(server, cx - m, cz - m, cx + m, cz + m);
		ctx.waitTicks(120);
		String[] names = {"200_aerial", "201_hall", "202_panel9", "203_panel5", "204_tower", "205_office", "206_shop", "207_shop2", "208_villa",
				"209_villa2", "210_school", "211_hospital", "212_street", "213_street_high"};
		List<float[]> cams = new ArrayList<>();
		cams.add(new float[]{cx + 0.5f, base + 75, cz + half + 70, 180, 38});
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.CITY_HALL, 0, 14, 9, 0));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.PANEL9, 0, 11, 12, 4));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.PANEL5, 0, 10, 9, 3));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.TOWER, 0, 12, 16, 4));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.OFFICE, 0, 11, 9, 3));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.SHOP, 0, 9, 2.5, 3));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.SHOP, 1, 9, 2.5, -3));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.COTTAGE, 0, 9, 3, 4));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.HOUSE, 0, 9, 3, -4));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.SCHOOL, 0, 11, 8, 0));
		cams.add(facing(server, com.stasdoto.airdefense.nation.BuildingType.HOSPITAL, 0, 9, 6, 2));
		float[][] cc = cityCams(server);
		cams.set(1, cc[0]);
		cams.add(cc[1]);
		cams.add(cc[2]);
		for (int i = 0; i < names.length; i++) {
			shot(ctx, server, cams.get(i), names[i], i == 0 ? 60 : 30);
		}
		// Night: the same city with its lights.
		server.runCommand("time set 18000");
		ctx.waitTicks(40);
		String[] night = {"220_night_aerial", "221_night_hall", "222_night_panel9", "224_night_tower", "226_night_shop", "232_night_street",
				"233_night_street_high"};
		int[] which = {0, 1, 2, 4, 6, 12, 13};
		for (int i = 0; i < night.length; i++) {
			shot(ctx, server, cams.get(which[i]), night[i], 40);
		}
		// A hamlet out of town: the houses round the well, the farm, a field.
		server.runCommand("time set 5000");
		if (cc[6] != null) {
			generateCity(server, (int) cc[6][0] - 8, (int) cc[6][1] - 8, (int) cc[6][2] + 8, (int) cc[6][3] + 8);
			ctx.waitTicks(60);
			shot(ctx, server, cc[3], "240_hamlet", 80);
			shot(ctx, server, cc[4], "241_hamlet_farm", 40);
			shot(ctx, server, cc[5], "242_hamlet_field", 40);
		}
		server.runCommand("time set 1000");
	}

	/** 1.28: a capital in each regional style (planned in a far cell with the style forced), built and photographed. */
	private void styles(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 5000");
		server.runCommand("difficulty peaceful");
		com.stasdoto.airdefense.nation.CityStyle[] all = {com.stasdoto.airdefense.nation.CityStyle.SOVIET,
				com.stasdoto.airdefense.nation.CityStyle.EUROPEAN, com.stasdoto.airdefense.nation.CityStyle.AMERICAN,
				com.stasdoto.airdefense.nation.CityStyle.DESERT};
		com.stasdoto.airdefense.nation.BuildingType[] types = {com.stasdoto.airdefense.nation.BuildingType.CITY_HALL,
				com.stasdoto.airdefense.nation.BuildingType.PANEL5, com.stasdoto.airdefense.nation.BuildingType.PANEL9,
				com.stasdoto.airdefense.nation.BuildingType.APARTMENTS, com.stasdoto.airdefense.nation.BuildingType.TOWER,
				com.stasdoto.airdefense.nation.BuildingType.OFFICE, com.stasdoto.airdefense.nation.BuildingType.SHOP,
				com.stasdoto.airdefense.nation.BuildingType.HOUSE, com.stasdoto.airdefense.nation.BuildingType.COTTAGE,
				com.stasdoto.airdefense.nation.BuildingType.SMALL_HOUSE};
		StringBuilder report = new StringBuilder();
		for (int k = 0; k < all.length; k++) {
			var style = all[k];
			int cellX = 40 + k * 2;
			int cellZ = 40;
			int[] cap = server.computeOnServer(s -> {
				ServerLevel l = s.overworld();
				com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = style;
				var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), cellX, cellZ);
				com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = null;
				if (list.isEmpty()) {
					return null;
				}
				var c = list.getFirst();
				java.util.Map<com.stasdoto.airdefense.nation.BuildingType, Integer> n = new java.util.EnumMap<>(com.stasdoto.airdefense.nation.BuildingType.class);
				for (var b : c.buildings()) {
					n.merge(b.type, 1, Integer::sum);
				}
				report.append(style).append(' ').append(c.style).append(' ').append(n).append("; ");
				return new int[]{c.x, c.z, c.half(), c.base};
			});
			if (cap == null) {
				continue;
			}
			int cx = cap[0];
			int cz = cap[1];
			int half = cap[2];
			int base = cap[3];
			camera(server, cx + 0.5, base + 80, cz + half + 80, 180, 35);
			ctx.waitTicks(40);
			int m = half + 12;
			generateCity(server, cx - m, cz - m, cx + m, cz + m);
			ctx.waitTicks(100);
			String tag = style.name().toLowerCase(java.util.Locale.ROOT);
			shot(ctx, server, new float[]{cx + 0.5f, base + 70, cz + half + 60, 180, 35}, "s" + k + "_" + tag + "_aerial", 60);
			shot(ctx, server, new float[]{cx + half * 0.5f, base + 30, cz + half * 0.4f, 150, 25}, "s" + k + "_" + tag + "_aerial_low", 30);
			for (var t : types) {
				float[] cam = facingIn(server, cellX, cellZ, t, 0, t == com.stasdoto.airdefense.nation.BuildingType.TOWER ? 22 : 12,
						t.height > 20 ? 10 : 4, 3);
				if (cam != null) {
					shot(ctx, server, cam, "s" + k + "_" + tag + "_" + t.id, 25);
				}
			}
			if (k == 1 || k == 3) {
				server.runCommand("time set 18000");
				ctx.waitTicks(20);
				shot(ctx, server, new float[]{cx + 0.5f, base + 70, cz + half + 60, 180, 35}, "s" + k + "_" + tag + "_night", 40);
				server.runCommand("time set 5000");
			}
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT styles: {}", report);
	}

	/**
	 * 1.28: a port. The flat test world has no sea, so one is dug south of a European capital (water two deep) and the
	 * town is built with a terrain that knows about it.
	 */
	private void port(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 5000");
		server.runCommand("difficulty peaceful");
		int cellX = 50;
		int cellZ = 40;
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = com.stasdoto.airdefense.nation.CityStyle.EUROPEAN;
			var flat = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), flat, cellX, cellZ);
			com.stasdoto.airdefense.nation.Cities.FORCE_STYLE = null;
			var c = list.getFirst();
			// The port is planned now, with the sea: the land round the town is made as soon as the camera comes near.
			int wz = c.z + c.half() + 40;
			c.port(l.getSeed(), seaTerrain(flat, c.base, c.x - 90, c.x + 90, wz, wz + 100));
			return new int[]{c.x, c.z, c.half(), c.base};
		});
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		int wz0 = cz + half + 40;
		int wz1 = wz0 + 100;
		int wx0 = cx - 90;
		int wx1 = cx + 90;
		// Dig the sea: the grass gone, water in its place two deep.
		camera(server, cx + 0.5, base + 60, wz0 + 20, 0, 60);
		ctx.waitTicks(80);
		for (int x = wx0; x <= wx1; x += 60) {
			int xe = Math.min(wx1, x + 59);
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x, base - 2, wz0, xe, base - 1, wz1));
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x, base, wz0, xe, base, wz1));
		}
		ctx.waitTicks(10);
		String plan = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var flat = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var sea = seaTerrain(flat, base, wx0, wx1, wz0, wz1);
			var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), sea, cellX, cellZ).getFirst();
			var pt = c.port(l.getSeed(), sea);
			if (pt == null) {
				return "no port";
			}
			long t0 = System.nanoTime();
			int before = com.stasdoto.airdefense.nation.CityGen.chunks;
			int m = half + 12;
			for (int qx = Math.floorDiv(Math.min(cx - m, pt.x0), 16); qx <= Math.floorDiv(Math.max(cx + m, pt.x1), 16); qx++) {
				for (int qz = Math.floorDiv(Math.min(cz - m, pt.z0), 16); qz <= Math.floorDiv(Math.max(cz + m, pt.z1), 16); qz++) {
					com.stasdoto.airdefense.nation.CityGen.generate(l, sea, l.getSeed(), new net.minecraft.world.level.ChunkPos(qx, qz));
				}
			}
			return "sea " + pt.sea + " shore " + pt.shore + " mid " + pt.mid + " y " + pt.y + " floor " + pt.floor + " buildings " + pt.buildings.size()
					+ " road " + (pt.access != null) + "; " + (com.stasdoto.airdefense.nation.CityGen.chunks - before) + " chunks in "
					+ (System.nanoTime() - t0) / 1_000_000 + " ms";
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT port: {}", plan);
		ctx.waitTicks(100);
		shot(ctx, server, new float[]{cx + 0.5f, base + 70, wz0 - 60, 0, 40}, "p1_port_aerial", 60);
		shot(ctx, server, new float[]{cx + 40.5f, base + 14, wz0 + 10, 120, 12}, "p2_port_quay", 30);
		shot(ctx, server, new float[]{cx - 50.5f, base + 6, wz0 + 30, -100, 2}, "p3_port_ship", 30);
		shot(ctx, server, new float[]{cx + 0.5f, base + 30, wz0 + 70, 180, 25}, "p4_port_from_sea", 30);
		server.runCommand("time set 18000");
		ctx.waitTicks(20);
		shot(ctx, server, new float[]{cx + 0.5f, base + 30, wz0 + 70, 180, 25}, "p5_port_night", 40);
		server.runCommand("time set 5000");
	}

	/** 1.29: talking to a townsman and to one's own soldier (follow me), a medic at work, a small fight (cover, flanks, radio). */
	private void life(ClientGameTestContext ctx, TestServerContext server) {
		int x = 90000;
		server.runCommand("gamemode creative @a");
		server.runCommand("time set 1000");
		camera(server, x + 0.5, ground, -6.5, 0, 5);
		ctx.waitTicks(40);
		int[] ids = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			ServerPlayer pl = s.getPlayerList().getPlayers().getFirst();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var country = com.stasdoto.airdefense.nation.Nations.countryOf(l, p, pl, true);
			// The player's own town here (else the villager below founds a village of some other country on the spot,
			// and its guards and garrison get mixed up in the scene).
			int tid = p.newId();
			BlockPos tat = new BlockPos(x, ground, 0);
			var town = new com.stasdoto.airdefense.nation.Settlement(tid, "Ясное", tat, tat.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(tid, town);
			town.country = country.id;
			var ar = com.stasdoto.airdefense.nation.Arsenals.get(s).of(p, town);
			ar.units.clear();
			ar.stock.clear();
			var v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			v.snapTo(x - 1.5, ground, -2.5, 180f, 0f);
			v.setNoAi(true);
			l.addFreshEntity(v);
			var so = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, country.id, 5, -1,
					new Vec3(x + 2.5, ground, -2.5), 7);
			l.addFreshEntity(so);
			return new int[]{v.getId(), so.getId(), country.id};
		});
		// The townsman: hello, then about the town (in Russian, as the player plays).
		language(ctx, "ru_ru");
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			com.stasdoto.airdefense.nation.Dialogue.open(l, s.getPlayerList().getPlayers().getFirst(), (net.minecraft.world.entity.LivingEntity) l.getEntity(ids[0]));
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("l1_talk_townsman");
		ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.stasdoto.airdefense.nation.DialogueActionPayload(ids[0], com.stasdoto.airdefense.nation.Dialogue.HOW)));
		ctx.waitTicks(10);
		ctx.takeScreenshot("l2_talk_how");
		String screen = ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.stasdoto.airdefense.client.nation.DialogueScreen d
				? d.talk().name() + " / " + d.talk().title().getString() + " / " + d.talk().speech().getString() + " / options " + d.talk().options() : "no screen");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		// One's own soldier: follow me, then walk away from him.
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			com.stasdoto.airdefense.nation.Dialogue.open(l, s.getPlayerList().getPlayers().getFirst(), (net.minecraft.world.entity.LivingEntity) l.getEntity(ids[1]));
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("l3_talk_soldier");
		ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.stasdoto.airdefense.nation.DialogueActionPayload(ids[1], com.stasdoto.airdefense.nation.Dialogue.FOLLOW)));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		language(ctx, "en_us");
		server.runCommand("gamemode survival @a");
		camera(server, x + 0.5, ground, 20.5, 180, 5);
		ctx.waitTicks(160);
		double follow = server.computeOnServer(s -> {
			var e = s.overworld().getEntity(ids[1]);
			return e == null ? -1 : e.distanceTo(s.getPlayerList().getPlayers().getFirst());
		});
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(5);
		ctx.takeScreenshot("l4_soldier_follows");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode creative @a");
		// A medic and a wounded comrade.
		int healedBefore = server.computeOnServer(s -> com.stasdoto.airdefense.nation.SoldierEntity.healedOthers);
		float[] wounded = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var m = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, ids[2], 5, -1,
					new Vec3(x + 10.5, ground, 30.5), 9);
			m.setMedic(true);
			l.addFreshEntity(m);
			var w = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, ids[2], 5, -1,
					new Vec3(x + 18.5, ground, 30.5), 11);
			l.addFreshEntity(w);
			w.setHealth(6f);
			w.setNoAi(true);
			return new float[]{w.getId(), w.getHealth()};
		});
		ctx.waitTicks(120);
		String medic = server.computeOnServer(s -> {
			var w = (net.minecraft.world.entity.LivingEntity) s.overworld().getEntity((int) wounded[0]);
			return String.format(java.util.Locale.ROOT, "wounded %.1f -> %.1f, healed %d", wounded[1], w == null ? -1f : w.getHealth(),
					com.stasdoto.airdefense.nation.SoldierEntity.healedOthers - healedBefore);
		});
		// A small fight: five soldiers against bandits dug in behind a wall.
		int fx = x + 300;
		camera(server, fx + 0.5, ground + 12, -20.5, 0, 30);
		ctx.waitTicks(40);
		int[] counts0 = server.computeOnServer(s -> new int[]{com.stasdoto.airdefense.nation.SoldierEntity.tookCover,
				com.stasdoto.airdefense.nation.SoldierEntity.flanked, com.stasdoto.airdefense.nation.SoldierEntity.radioCalls});
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int i = 0; i < 9; i++) {
				// Waist high: the bandits behind it are seen (a two-block wall hid them, and whether a fight began at
				// all was down to how they wandered).
				l.setBlockAndUpdate(new BlockPos(fx - 4 + i, ground, 22), Blocks.STONE_BRICKS.defaultBlockState());
			}
			for (int i = 0; i < 5; i++) {
				var so = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, ids[2], 5, -1,
						new Vec3(fx - 4 + i * 2 + 0.5, ground, -4.5), 20 + i);
				l.addFreshEntity(so);
			}
			for (int i = 0; i < 3; i++) {
				var b = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.BANDIT, -1, -1, -1,
						new Vec3(fx - 2 + i * 2 + 0.5, ground, 24.5), 40 + i);
				l.addFreshEntity(b);
			}
		});
		ctx.waitTicks(200);
		ctx.takeScreenshot("l5_fight");
		ctx.waitTicks(200);
		String fight = server.computeOnServer(s -> {
			int alive = s.overworld().getEntitiesOfClass(com.stasdoto.airdefense.nation.SoldierEntity.class,
					new net.minecraft.world.phys.AABB(fx - 60, ground - 10, -60, fx + 60, ground + 20, 60),
					e -> e.isAlive() && e.role() == com.stasdoto.airdefense.nation.SoldierEntity.BANDIT).size();
			return "bandits left " + alive + ", took cover " + (com.stasdoto.airdefense.nation.SoldierEntity.tookCover - counts0[0]) + ", flanked "
					+ (com.stasdoto.airdefense.nation.SoldierEntity.flanked - counts0[1]) + ", radio calls "
					+ (com.stasdoto.airdefense.nation.SoldierEntity.radioCalls - counts0[2]);
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT life_talk: {}; opened {} answered {}", screen,
				server.computeOnServer(s -> com.stasdoto.airdefense.nation.Dialogue.opened), server.computeOnServer(s -> com.stasdoto.airdefense.nation.Dialogue.answered));
		AirDefense.LOGGER.info("[airdefense-test] RESULT life_army: follow distance {}; medic: {}; fight: {}", String.format(java.util.Locale.ROOT, "%.1f", follow),
				medic, fight);
		server.runCommand("gamemode spectator @a");
	}

	/** The flat world's terrain with a sea dug into it (see {@link #port}). */
	private static com.stasdoto.airdefense.nation.Cities.Terrain seaTerrain(com.stasdoto.airdefense.nation.Cities.Terrain flat, int base, int wx0, int wx1,
			int wz0, int wz1) {
		return new com.stasdoto.airdefense.nation.Cities.Terrain() {
			private boolean wet(int x, int z) {
				return x >= wx0 && x <= wx1 && z >= wz0 && z <= wz1;
			}

			@Override
			public int top(int x, int z) {
				return wet(x, z) ? base - 1 : flat.top(x, z);
			}

			@Override
			public int sea() {
				return base;
			}

			@Override
			public int floor(int x, int z) {
				return wet(x, z) ? base - 3 : flat.top(x, z);
			}
		};
	}

	/** Like {@link #facing}, for the capital of another cell. */
	private static float[] facingIn(TestServerContext server, int cellX, int cellZ, com.stasdoto.airdefense.nation.BuildingType type, int skip,
			double dist, double up, double side) {
		return server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), t, cellX, cellZ);
			if (list.isEmpty()) {
				return null;
			}
			var c = list.getFirst();
			int k = 0;
			for (var b : c.buildings()) {
				if (b.type != type || k++ < skip) {
					continue;
				}
				var f = b.facing;
				var right = f.getClockWise();
				double mx = b.origin.getX() + 0.5 + f.getStepX() * b.type.depth / 2.0;
				double mz = b.origin.getZ() + 0.5 + f.getStepZ() * b.type.depth / 2.0;
				double cx = b.origin.getX() + 0.5 - f.getStepX() * dist + right.getStepX() * side;
				double cz = b.origin.getZ() + 0.5 - f.getStepZ() * dist + right.getStepZ() * side;
				double cy = c.base + up;
				double dx = mx - cx;
				double dz = mz - cz;
				double dy = c.base + Math.min(b.type.height, 24) * 0.45 - cy;
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
				return new float[]{(float) cx, (float) cy, (float) cz, yaw, pitch};
			}
			return null;
		});
	}

	/** Builds the chunks of a square (as the world generator would). */
	private static void generateCity(TestServerContext server, int x0, int z0, int x1, int z1) {
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			long t0 = System.nanoTime();
			int before = com.stasdoto.airdefense.nation.CityGen.chunks;
			for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x1, 16); cx++) {
				for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z1, 16); cz++) {
					com.stasdoto.airdefense.nation.CityGen.generate(l, t, l.getSeed(), new net.minecraft.world.level.ChunkPos(cx, cz));
				}
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT city_chunks: {} chunks built in {} ms", com.stasdoto.airdefense.nation.CityGen.chunks - before,
					(System.nanoTime() - t0) / 1_000_000);
		});
	}

	private void cities(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 6000");
		server.runCommand("difficulty peaceful");
		// The plan of the cell at the world's centre.
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			long t0 = System.nanoTime();
			var list = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), t, 0, 0);
			var roads = com.stasdoto.airdefense.nation.Cities.roads(l.getSeed(), t, 0, 0);
			long ms = (System.nanoTime() - t0) / 1_000_000;
			for (var c : list) {
				java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
				for (var b : c.buildings()) {
					kinds.merge(b.type.id, 1, Integer::sum);
				}
				AirDefense.LOGGER.info("[airdefense-test] RESULT city_plan: #{} {} at {} {} ground {} people {} lacks {} buildings {} {}", c.index, c.size, c.x,
						c.z, c.base, c.citizens, c.lack().id, c.buildings().size(), kinds);
			}
			StringBuilder rs = new StringBuilder();
			for (var r : roads) {
				rs.append((int) r.length).append(' ');
			}
			AirDefense.LOGGER.info("[airdefense-test] RESULT city_roads: {} roads, lengths {} (plan {} ms)", roads.size(), rs.toString().trim(), ms);
			if (list.isEmpty()) {
				return null;
			}
			var c = list.getFirst();
			var r = roads.isEmpty() ? null : roads.getFirst();
			return new int[]{c.x, c.z, c.half(), c.base, r == null ? c.x : r.x0, r == null ? c.z : r.z0, r == null ? 0 : (int) Math.round((r.pointAt(30)[0] - r.pointAt(0)[0]) / 30 * 100),
					r == null ? 0 : (int) Math.round((r.pointAt(30)[1] - r.pointAt(0)[1]) / 30 * 100)};
		});
		if (cap == null) {
			return;
		}
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		camera(server, cx + 0.5, base + 70, cz + half + 90, 180, 35);
		ctx.waitTicks(60);
		int m = half + 20;
		generateCity(server, cx - m, cz - m, cx + m, cz + m);
		// The first stretch of the first road out of the capital.
		for (int k = 0; k < 10; k++) {
			int rx = cap[4] + cap[6] * k * 16 / 100;
			int rz = cap[5] + cap[7] * k * 16 / 100;
			generateCity(server, rx - 8, rz - 8, rx + 8, rz + 8);
		}
		ctx.waitTicks(100);
		ctx.takeScreenshot("130_capital_aerial");
		camera(server, cx + 0.5, base + 120, cz + 0.5, 0, 90);
		ctx.waitTicks(60);
		ctx.takeScreenshot("130b_capital_top");
		// The city hall and the square.
		float[][] cc = cityCams(server);
		shot(ctx, server, cc[0], "131_city_hall", 40);
		// Along a street.
		shot(ctx, server, cc[1], "132_street", 40);
		shot(ctx, server, cc[2], "132b_street_above", 40);
		// Where the road leaves the city.
		float yaw = (float) Math.toDegrees(Math.atan2(-cap[6], cap[7]));
		camera(server, cap[4] - cap[6] * 0.2 + 0.5, base + 14, cap[5] - cap[7] * 0.2 + 0.5, yaw, 20);
		ctx.waitTicks(40);
		ctx.takeScreenshot("133_road");
		// The city joins the map (its square stands).
		camera(server, cx + 0.5, base + 20, cz + 40.5, 180, 20);
		int waited = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Nations.citiesFounded > 0, 300);
		String founded = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			StringBuilder sb = new StringBuilder();
			for (var st : p.settlements.values()) {
				if (st.isCity()) {
					var c = p.country(st.country);
					sb.append(st.name).append(" r=").append(st.radius).append(" people=").append(st.citizens).append(" buildings=")
							.append(st.eco.buildings.size()).append(" country=").append(c == null ? "-" : c.name).append(" capital=")
							.append(c != null && c.capital == st.id).append(" villagers=").append(com.stasdoto.airdefense.nation.Nations.villagers(s.overworld(), st).size())
							.append("; ");
				}
			}
			return sb.toString();
		});
		String trade = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			com.stasdoto.airdefense.nation.Settlement capital = testCapital(s);
			if (capital == null) {
				return "no capital";
			}
			int food = com.stasdoto.airdefense.nation.VillageEconomy.FOOD;
			var town = new com.stasdoto.airdefense.nation.Settlement(p.newId(), "Test", capital.center.offset(600, 0, 0), capital.center.offset(600, 0, 0),
					capital.country, java.util.Optional.empty(), 0, java.util.Map.of(), List.of(), List.of());
			town.city = 999_999;
			town.citizens = 300;
			p.settlements.put(town.id, town);
			int before = capital.eco.stock[food];
			com.stasdoto.airdefense.nation.Market.tradeNow(p);
			String r = "capital food " + before + " -> " + capital.eco.stock[food] + " (needs " + com.stasdoto.airdefense.nation.Supply.foodNeed(capital)
					+ "/min), hungry town 0 -> " + town.eco.stock[food] + ", caravans " + com.stasdoto.airdefense.nation.Market.caravans
					+ " | capital stock " + java.util.Arrays.toString(capital.eco.stock);
			p.settlements.remove(town.id);
			return r;
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT caravan: {}", trade);
		AirDefense.LOGGER.info("[airdefense-test] RESULT city_founded after {} ticks: {} | avg {} ms per chunk", waited, founded,
				com.stasdoto.airdefense.nation.CityGen.chunks == 0 ? 0 : com.stasdoto.airdefense.nation.CityGen.nanos / 1_000_000 / com.stasdoto.airdefense.nation.CityGen.chunks);
		ctx.waitTicks(20);
		ctx.takeScreenshot("134_flag_square");
		// The tablet map.
		server.runCommand("gamemode creative @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(40);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof com.stasdoto.airdefense.client.map.TacticalMapScreen s) {
				s.centerOn(cx, cz, 2);
			}
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("135_city_map");
		// Take the capital, pick a panel block on the map and rebuild it as a tower.
		int[] target = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			for (var st : java.util.Collections.singletonList(testCapital(s))) {
				if (st != null) {
					com.stasdoto.airdefense.nation.Nations.takeOver(s.overworld(), s.getPlayerList().getPlayers().getFirst(), st);
					for (int i = 0; i < st.eco.buildings.size(); i++) {
						var b = st.eco.buildings.get(i);
						if (b.type == com.stasdoto.airdefense.nation.BuildingType.PANEL9) {
							return new int[]{st.id, i, b.middle().getX(), b.middle().getZ(), b.origin.getY()};
						}
					}
				}
			}
			return null;
		});
		if (target != null) {
			ctx.waitTicks(30);
			boolean picked = ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.stasdoto.airdefense.client.map.TacticalMapScreen sc
					&& sc.pickForTest(target[2] + 0.5, target[3] + 0.5, com.stasdoto.airdefense.nation.BuildingType.TOWER.ordinal()));
			ctx.waitTicks(10);
			ctx.takeScreenshot("136_rebuild_panel");
			boolean pressed = ctx.tryClickScreenButton("Rebuild");
			ctx.waitTicks(20);
			String after = server.computeOnServer(s -> {
				var st = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(target[0]);
				var last = st.eco.buildings.getLast();
				var at = new BlockPos(target[2], target[4] + 8, target[3]);
				return last.type.id + " done=" + last.done + " at " + last.origin.toShortString() + ", old site block " + s.overworld().getBlockState(at)
						+ ", queue " + st.eco.queued();
			});
			AirDefense.LOGGER.info("[airdefense-test] RESULT city_rebuild: picked {} pressed {} -> {}", picked, pressed, after);
			ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			ctx.waitTicks(5);
			camera(server, target[2] + 0.5, base + 30, target[3] + 40.5, 180, 25);
			for (int k = 0; k < 30 && !server.computeOnServer(s -> com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(target[0]).eco.buildings.getLast().done); k++) {
				ctx.waitTicks(40);
			}
			ctx.takeScreenshot("137_rebuilt_tower");
			String rebuilt = server.computeOnServer(s -> {
				var last = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(target[0]).eco.buildings.getLast();
				return last.type.id + " " + last.percent() + "%";
			});
			AirDefense.LOGGER.info("[airdefense-test] RESULT city_rebuilt: {}", rebuilt);
		} else {
			ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			ctx.waitTicks(5);
		}
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
		AirDefense.LOGGER.info("[airdefense-test] RESULT nations_profile_ms: {}", java.util.Arrays.toString(
				java.util.Arrays.stream(com.stasdoto.airdefense.nation.Nations.PROFILE).map(v -> v / 1_000_000).toArray()));
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
				com.stasdoto.airdefense.nation.BuildingType.SMALL_HOUSE, com.stasdoto.airdefense.nation.BuildingType.FARM,
				com.stasdoto.airdefense.nation.BuildingType.FOOD_PLANT, com.stasdoto.airdefense.nation.BuildingType.ARMS_FACTORY,
				com.stasdoto.airdefense.nation.BuildingType.MARKET};
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
			camera(server, mid + 0.5, g + 12 + span * 0.1, 14 + span * 0.42, 180, 16);
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
		camera(server, front + 4.5, g + 9, vz + 22, 180, 22);
		ctx.waitTicks(40);
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
		// 1.23: a farm, a food plant, an arms factory and a market in the town; trade; a truck of food.
		String industry = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			var st = p.settlements.get(id);
			var pl = s.getPlayerList().getPlayers().getFirst();
			int off = 0;
			for (var t : new com.stasdoto.airdefense.nation.BuildingType[]{com.stasdoto.airdefense.nation.BuildingType.FARM,
					com.stasdoto.airdefense.nation.BuildingType.FOOD_PLANT, com.stasdoto.airdefense.nation.BuildingType.ARMS_FACTORY,
					com.stasdoto.airdefense.nation.BuildingType.MARKET}) {
				var b = new com.stasdoto.airdefense.nation.Building(p.newId(), t, new BlockPos(vx - 40 + off, g - 1, vz + 110), net.minecraft.core.Direction.SOUTH, true);
				b.done = true;
				st.eco.buildings.add(b);
				off += 30;
			}
			int[] k = st.eco.stock;
			k[com.stasdoto.airdefense.nation.VillageEconomy.IRON] = 200;
			k[com.stasdoto.airdefense.nation.VillageEconomy.FUEL] = Math.max(k[com.stasdoto.airdefense.nation.VillageEconomy.FUEL], 2000);
			int food0 = k[com.stasdoto.airdefense.nation.VillageEconomy.FOOD];
			for (int i = 0; i < 3; i++) {
				com.stasdoto.airdefense.nation.Supply.produce(p, st);
			}
			int em0 = pl.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
			int arms0 = k[com.stasdoto.airdefense.nation.VillageEconomy.ARMS];
			boolean sold = com.stasdoto.airdefense.nation.Market.trade(s.overworld(), pl, st, com.stasdoto.airdefense.nation.VillageEconomy.ARMS, false);
			int food1 = k[com.stasdoto.airdefense.nation.VillageEconomy.FOOD];
			boolean bought = com.stasdoto.airdefense.nation.Market.trade(s.overworld(), pl, st, com.stasdoto.airdefense.nation.VillageEconomy.FOOD, true);
			int em1 = pl.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
			int[] pf = com.stasdoto.airdefense.nation.Market.price(p, st, com.stasdoto.airdefense.nation.VillageEconomy.FOOD);
			int[] pa = com.stasdoto.airdefense.nation.Market.price(p, st, com.stasdoto.airdefense.nation.VillageEconomy.ARMS);
			return String.format(java.util.Locale.ROOT,
					"food %d -> %d (made %d/min), arms %d (made %d), ammo %d, iron %d, fuel %d | sold arms %s (%d -> %d) bought food %s (%d -> %d) emeralds %d -> %d | prices food %d/%d arms %d/%d",
					food0, food1, com.stasdoto.airdefense.nation.Supply.foodMade(st), arms0, com.stasdoto.airdefense.nation.Supply.armsMade,
					k[com.stasdoto.airdefense.nation.VillageEconomy.AMMO], k[com.stasdoto.airdefense.nation.VillageEconomy.IRON],
					k[com.stasdoto.airdefense.nation.VillageEconomy.FUEL], sold, arms0, k[com.stasdoto.airdefense.nation.VillageEconomy.ARMS], bought, food1,
					k[com.stasdoto.airdefense.nation.VillageEconomy.FOOD], em0, em1, pf[0], pf[1], pa[0], pa[1]);
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT industry: {}", industry);
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.MARKET);
		ctx.takeScreenshot("129_market_tab");
		ecoScreen(ctx, server, id, com.stasdoto.airdefense.client.nation.SettlementScreen.WORK);
		ctx.takeScreenshot("129a_store_food_arms");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		server.runOnServer(s -> {
			if (s.overworld().getEntity(ammoTruck) instanceof VehicleEntity t) {
				t.teleportTo(vx - 6.5, g, vz + 34.5);
				t.setCargo(0);
				t.setTruckMode(VehicleEntity.TRUCK_LOAD_OTHER);
			}
		});
		ctx.waitTicks(80);
		String foodTruck = server.computeOnServer(s -> s.overworld().getEntity(ammoTruck) instanceof VehicleEntity t
				? t.getCargo() + " of kind " + t.getCargoKind() + " mode " + t.truckModeKey() : "gone");
		AirDefense.LOGGER.info("[airdefense-test] RESULT food_truck: {}", foodTruck);
		server.runOnServer(s -> {
			var pl = s.getPlayerList().getPlayers().getFirst();
			if (s.overworld().getEntity(ammoTruck) instanceof VehicleEntity t) {
				pl.startRiding(t);
			}
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("129b_food_truck_hud");
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

	/**
	 * 1.25: towns at war. The capital's garrison stands round it (air defence at the edge, launchers in the depot's
	 * yard); an enemy town 1.5 km off fires at it - the missiles come in from its side and the garrison shoots at them;
	 * then the enemy sends a column by road: the vehicles drive in, the men get out at the edge of town and fight.
	 */
	private void townWar(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 5000");
		server.runCommand("difficulty normal");
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), t, 0, 0).getFirst();
			var d = c.depot(l.getSeed(), t);
			BlockPos bell = c.bell();
			return d == null ? new int[]{c.x, c.z, c.half(), c.base, bell.getX(), bell.getZ(), 0, 0, 0, 0}
					: new int[]{c.x, c.z, c.half(), c.base, bell.getX(), bell.getZ(), d.x0, d.z0, d.x1, d.z1};
		});
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		camera(server, cx + 0.5, base + 70, cz + half + 90, 180, 35);
		ctx.waitTicks(60);
		int m = half + 60;
		generateCity(server, cx - m, cz - m, cx + m, cz + m);
		if (cap[6] != 0 || cap[8] != 0) {
			generateCity(server, cap[6] - 16, cap[7] - 16, cap[8] + 16, cap[9] + 16);
		}
		// The roads out of the capital, their first 260 blocks (the flat test world does not build them by itself).
		List<int[]> roadPts = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = com.stasdoto.airdefense.nation.Cities.terrain(l);
			var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), t, 0, 0).getFirst();
			List<int[]> pts = new ArrayList<>();
			for (var r : com.stasdoto.airdefense.nation.Cities.roadsNear(l.getSeed(), t, c.x, c.z)) {
				boolean start = c.outside(r.x0, r.z0) <= 12;
				boolean end = c.outside(r.x1, r.z1) <= 12;
				if (!start && !end) {
					continue;
				}
				for (double a = 0; a <= Math.min(260, r.length); a += 12) {
					double[] q = r.pointAt(start ? a : r.length - a);
					pts.add(new int[]{(int) q[0], (int) q[1]});
				}
			}
			return pts;
		});
		java.util.Set<Long> done = new java.util.HashSet<>();
		for (int[] q : roadPts) {
			if (done.add(net.minecraft.world.level.ChunkPos.pack(q[0] >> 4, q[1] >> 4))) {
				generateCity(server, q[0] - 12, q[1] - 12, q[0] + 12, q[1] + 12);
			}
		}
		camera(server, cx + 0.5, base + 40, cz + 0.5, 180, 40);
		waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Nations.citiesFounded > 0, 400);
		int garrison = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Arsenals.spawned >= 3, 600);
		ctx.waitTicks(60);
		String units = server.computeOnServer(s -> {
			StringBuilder sb = new StringBuilder();
			for (VehicleEntity v : s.overworld().getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(cx - 400, base - 40, cz - 400,
					cx + 400, base + 60, cz + 400), v -> v.garrison)) {
				sb.append(v.getVehicleType().id).append('@').append(v.getBlockX()).append(',').append(v.getBlockZ()).append(' ');
			}
			return sb.toString();
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT garrison: {} vehicles after {} ticks: {}", com.stasdoto.airdefense.nation.Arsenals.spawned,
				garrison, units);
		float[] gcam = server.computeOnServer(s -> {
			for (VehicleEntity v : s.overworld().getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(cx - 400, base - 40, cz - 400,
					cx + 400, base + 60, cz + 400), v -> v.garrison && v.getVehicleType().isDefense())) {
				return look(v.getX() + 12, v.getY() + 6, v.getZ() + 12, v.getX(), v.getY() + 1.5, v.getZ());
			}
			return null;
		});
		shot(ctx, server, gcam, "180_garrison_ad", 40);
		if (cap[6] != 0 || cap[8] != 0) {
			shot(ctx, server, look((cap[6] + cap[8]) / 2.0 + 40, base + 40, (cap[7] + cap[9]) / 2.0 + 40, (cap[6] + cap[8]) / 2.0, base, (cap[7] + cap[9]) / 2.0),
					"181_depot", 80);
		}
		// A lorry brings missiles in from the factory: on the highway, in at the depot's gate.
		int lorriesBefore = com.stasdoto.airdefense.nation.Arsenals.lorries;
		String load = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			for (var st : java.util.Collections.singletonList(testCapital(s))) {
				if (st != null) {
					var mt = com.stasdoto.airdefense.nation.Arsenals.testDelivery(s.overworld(), st);
					return mt == null ? "none" : mt.name();
				}
			}
			return "no capital";
		});
		int lorryOut = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Arsenals.lorries > lorriesBefore, 200);
		ctx.waitTicks(60);
		float[] lcam = server.computeOnServer(s -> {
			for (VehicleEntity v : s.overworld().getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(cx - 700, base - 40, cz - 700,
					cx + 700, base + 80, cz + 700), v -> v.cargoDelivery != 0)) {
				return look(v.getX() + 10, v.getY() + 6, v.getZ() + 10, v.getX(), v.getY() + 1, v.getZ());
			}
			return null;
		});
		shot(ctx, server, lcam, "181b_supply_lorry", 30);
		int lorryIn = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Arsenals.lorryArrivals > 0, 1600);
		float[] ucam = server.computeOnServer(s -> {
			for (VehicleEntity v : s.overworld().getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(cx - 700, base - 40, cz - 700,
					cx + 700, base + 80, cz + 700), v -> v.getVehicleType() == com.stasdoto.airdefense.vehicle.VehicleType.SUPPLY_TRUCK && !v.driving())) {
				return look(v.getX() + 16, v.getY() + 10, v.getZ() + 16, v.getX(), v.getY() + 1, v.getZ());
			}
			return null;
		});
		shot(ctx, server, ucam, "181c_lorry_unloaded", 20);
		AirDefense.LOGGER.info("[airdefense-test] RESULT supply_lorry: load {} lorry out after {} ticks, at the depot after {} ticks (lorries {}, arrived {},"
						+ " loads into the stores {})", load, lorryOut, lorryIn, com.stasdoto.airdefense.nation.Arsenals.lorries,
				com.stasdoto.airdefense.nation.Arsenals.lorryArrivals, com.stasdoto.airdefense.nation.Arsenals.lorryLoads);
		// The enemy: a town of another country 1.5 km to the east, at war with the capital's country.
		int[] ids = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			com.stasdoto.airdefense.nation.Settlement capital = testCapital(s);
			if (capital == null) {
				return null;
			}
			int id = p.newId();
			BlockPos at = new BlockPos(cx + 1500, base, cz);
			var enemyTown = new com.stasdoto.airdefense.nation.Settlement(id, "Вражеск", at, at.above(2), -1, java.util.Optional.empty(), 0,
					java.util.Map.of(), List.of(), List.of());
			p.settlements.put(id, enemyTown);
			var enemy = com.stasdoto.airdefense.nation.Nations.newWorldCountry(p, id);
			enemyTown.country = enemy.id;
			var mine = p.country(capital.country);
			com.stasdoto.airdefense.nation.War.declare(l, p, enemy, mine, net.minecraft.network.chat.Component.literal("test"));
			return new int[]{id, capital.id, enemy.id};
		});
		if (ids == null) {
			AirDefense.LOGGER.info("[airdefense-test] RESULT town_war: no capital");
			return;
		}
		int[] before = counters();
		// Watching from the east edge of the capital (the enemy is that way) when the strike comes.
		camera(server, cx + half + 10, base + 30, cz + 20, 250, -10);
		ctx.waitTicks(40);
		boolean fired = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			return com.stasdoto.airdefense.nation.Arsenals.strikeNow(s.overworld(), p.settlements.get(ids[0]), p.settlements.get(ids[1]));
		});
		ctx.waitTicks(80);
		ctx.takeScreenshot("182_strike_incoming");
		ctx.waitTicks(320);
		report("town_strike", before);
		AirDefense.LOGGER.info("[airdefense-test] RESULT town_strike_fired: {}", fired);
		// A column by road.
		int sent = server.computeOnServer(s -> {
			var p = com.stasdoto.airdefense.nation.Politics.get(s);
			return com.stasdoto.airdefense.nation.War.sendColumn(s.overworld(), p, p.country(ids[2]), p.settlements.get(ids[1]), 10);
		});
		float[] ccam = server.computeOnServer(s -> {
			for (VehicleEntity v : s.overworld().getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(cx - 600, base - 40, cz - 600,
					cx + 600, base + 60, cz + 600), v -> v.driving())) {
				return look(v.getX() + 14, v.getY() + 7, v.getZ() + 14, v.getX(), v.getY() + 1, v.getZ());
			}
			return null;
		});
		shot(ctx, server, ccam, "183_column", 30);
		int arrived = waitUntil(ctx, () -> VehicleEntity.arrivals > 0, 1800);
		waitUntil(ctx, () -> VehicleEntity.dismounted > 0, 1200);
		ctx.waitTicks(100);
		// The men who got out, on their way in (from behind and above them).
		float[] fcam = server.computeOnServer(s -> {
			var list = s.overworld().getEntitiesOfClass(com.stasdoto.airdefense.nation.SoldierEntity.class, new net.minecraft.world.phys.AABB(cx - 600,
					base - 40, cz - 600, cx + 600, base + 80, cz + 600), e -> e.isAlive());
			com.stasdoto.airdefense.nation.SoldierEntity best = null;
			for (var e : list) {
				if (best == null || e.distanceToSqr(cx, base, cz) > best.distanceToSqr(cx, base, cz)) {
					best = e;
				}
			}
			if (best == null) {
				return null;
			}
			Vec3 away = new Vec3(best.getX() - cx, 0, best.getZ() - cz).normalize();
			return look(best.getX() + away.x * 14 + away.z * 6, best.getY() + 8, best.getZ() + away.z * 14 - away.x * 6, best.getX(), best.getY() + 1,
					best.getZ());
		});
		shot(ctx, server, fcam, "184_column_men", 10);
		camera(server, cap[4] + 0.5, base + 18, cap[5] + 30.5, 180, 25);
		ctx.waitTicks(200);
		ctx.takeScreenshot("184_column_fight");
		AirDefense.LOGGER.info("[airdefense-test] RESULT column: {} vehicles sent, first arrived after {} ticks ({} arrived), {} men got out;"
						+ " cover {} fell back {} grenades {}", sent, arrived, VehicleEntity.arrivals, VehicleEntity.dismounted,
				com.stasdoto.airdefense.nation.SoldierEntity.tookCover, com.stasdoto.airdefense.nation.SoldierEntity.fellBack,
				com.stasdoto.airdefense.nation.SoldierEntity.grenadesThrown);
		server.runCommand("kill @e[type=airdefense:soldier,distance=..10000]");
	}

	/**
	 * The town founded on the first city of the plan (the scenes' capital); the atlas founds every other town of the
	 * 10 km round it too, so "a capital" is not enough to find it.
	 */
	@org.jetbrains.annotations.Nullable
	private static com.stasdoto.airdefense.nation.Settlement testCapital(net.minecraft.server.MinecraftServer s) {
		ServerLevel l = s.overworld();
		var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), 0, 0).getFirst();
		com.stasdoto.airdefense.nation.Settlement best = null;
		double bestD = 300.0 * 300.0;
		for (var st : com.stasdoto.airdefense.nation.Politics.get(s).settlements.values()) {
			double d = Math.pow(st.center.getX() - c.x, 2) + Math.pow(st.center.getZ() - c.z, 2);
			if (st.isCity() && d < bestD) {
				bestD = d;
				best = st;
			}
		}
		return best;
	}

	/**
	 * 1.25.2: the town rebuilds. A blast in a street and one by a house of the capital; with nobody in creative near by
	 * the people put it back a few blocks a second after a quiet half minute; with a creative player there, at once.
	 */
	private void repair(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 5000");
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), 0, 0).getFirst();
			BlockPos bell = c.bell();
			return new int[]{c.x, c.z, c.half(), c.base, bell.getX(), bell.getZ()};
		});
		int cx = cap[0];
		int cz = cap[1];
		int base = cap[3];
		camera(server, cx + 0.5, base + 60, cz + cap[2] + 60, 180, 35);
		ctx.waitTicks(60);
		int m = cap[2] + 20;
		generateCity(server, cx - m, cz - m, cx + m, cz + m);
		ctx.waitTicks(40);
		for (int phase = 0; phase < 2; phase++) {
			boolean creative = phase == 1;
			BlockPos at = new BlockPos(cap[4] + (creative ? -14 : 12), base + 1, cap[5] + (creative ? -12 : 10));
			int before = holes(server, at, 6, base);
			camera(server, at.getX() + 14.5, base + 12, at.getZ() + 14.5, 135, 30);
			ctx.waitTicks(20);
			ctx.takeScreenshot("190_repair_" + phase + "_before");
			int rebuilt0 = com.stasdoto.airdefense.nation.Repairs.rebuilt;
			server.runOnServer(s -> s.overworld().explode(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 5f,
					net.minecraft.world.level.Level.ExplosionInteraction.TNT));
			ctx.waitTicks(10);
			int after = holes(server, at, 6, base);
			ctx.takeScreenshot("191_repair_" + phase + "_blast");
			String foreign = "";
			if (creative) {
				server.runCommand("gamemode creative @a");
				// 1.33: somebody else's town does not stand up again at once for a creative player...
				ctx.waitTicks(200);
				foreign = " (not his town: " + (com.stasdoto.airdefense.nation.Repairs.rebuilt - rebuilt0) + " blocks in 200 ticks)";
				// ...his own does.
				server.runOnServer(s -> {
					var p = com.stasdoto.airdefense.nation.Politics.get(s);
					var st = p.settlementAt(at);
					if (st != null) {
						com.stasdoto.airdefense.nation.Nations.takeOver(s.overworld(), s.getPlayerList().getPlayers().getFirst(), st);
					}
				});
			}
			int waited = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Repairs.rebuilt - rebuilt0 > 0
					&& holes(server, at, 6, base) <= before, creative ? 300 : 1500);
			int end = holes(server, at, 6, base);
			ctx.takeScreenshot("192_repair_" + phase + "_after");
			String zones = server.computeOnServer(s -> com.stasdoto.airdefense.nation.Repairs.get(s).describe(s.overworld(), at, 60));
			AirDefense.LOGGER.info("[airdefense-test] RESULT repair_{}: holes {} -> {} -> {} after {} ticks, blocks put back {}, zones noted {} finished {}{}; {}",
					creative ? "creative" : "survival", before, after, end, waited, com.stasdoto.airdefense.nation.Repairs.rebuilt - rebuilt0,
					com.stasdoto.airdefense.nation.Repairs.noted, com.stasdoto.airdefense.nation.Repairs.finished, foreign, zones);
			server.runCommand("gamemode spectator @a");
		}
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

	/**
	 * The capital is built and joins the map, its sirens go up by themselves. The tablet's warning page: the alert
	 * everywhere, the all clear, silence; one town by a click on its row; one siren switched by hand; a siren out in the
	 * field that does not follow the town. Then Iron Dome by the city: a HIMARS salvo at an empty field is let go, a
	 * salvo at the city is shot down, and the city's sirens sound by themselves.
	 */
	private void sirens(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 5000");
		server.runCommand("difficulty peaceful");
		int[] cap = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var c = com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(), com.stasdoto.airdefense.nation.Cities.terrain(l), 0, 0).getFirst();
			BlockPos bell = c.bell();
			return new int[]{c.x, c.z, c.half(), c.base, bell.getX(), bell.getZ(), c.radius()};
		});
		int cx = cap[0];
		int cz = cap[1];
		int half = cap[2];
		int base = cap[3];
		camera(server, cx + 0.5, base + 70, cz + half + 90, 180, 35);
		ctx.waitTicks(60);
		int m = half + 20;
		generateCity(server, cx - m, cz - m, cx + m, cz + m);
		// The atlas founded the capital (and planned its sirens) at the start, and earlier scenes may have put them up
		// on the bare test ground; the city has just been built over them: plan them afresh.
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			var z = com.stasdoto.airdefense.siren.Sirens.get(s);
			z.known.removeIf(k -> Math.abs(BlockPos.of(k).getX() - cx) <= m && Math.abs(BlockPos.of(k).getZ() - cz) <= m);
			z.pending.keySet().removeIf(k -> Math.abs(BlockPos.of(k).getX() - cx) <= m && Math.abs(BlockPos.of(k).getZ() - cz) <= m);
			com.stasdoto.airdefense.siren.Sirens.planCity(l, com.stasdoto.airdefense.nation.Cities.cities(l.getSeed(),
					com.stasdoto.airdefense.nation.Cities.terrain(l), 0, 0).getFirst());
		});
		camera(server, cx + 0.5, base + 40, cz + 0.5, 180, 40);
		int founded = waitUntil(ctx, () -> com.stasdoto.airdefense.nation.Nations.citiesFounded > 0, 400);
		int waited = waitUntil(ctx, () -> sirensNear(server, cx, cz, half + 40).size() >= 2, 400);
		ctx.waitTicks(80);
		List<BlockPos> city = sirensNear(server, cap[4], cap[5], half + 40);
		String plan = server.computeOnServer(s -> {
			var z = com.stasdoto.airdefense.siren.Sirens.get(s);
			return "put up " + com.stasdoto.airdefense.siren.Sirens.townSirensPlaced + ", still planned " + z.pending.size() + ", known " + z.known.size();
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT siren_city: {} sirens in the capital (founded after {} ticks, sirens after {}; {}) at {}",
				city.size(), founded, waited, plan, city);
		if (city.isEmpty()) {
			return;
		}
		int town = server.computeOnServer(s -> com.stasdoto.airdefense.siren.Sirens.townOf(s.overworld(), city.getFirst()));
		ctx.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		shot(ctx, server, sirenCam(server, city.getFirst(), 9, -3.6), "300_siren_square", 40);
		shot(ctx, server, sirenCam(server, city.getFirst(), 3.2, 0.6), "300b_siren_close", 20);
		if (city.size() > 1) {
			shot(ctx, server, sirenCam(server, city.get(1), 11, -3.4), "301_siren_street", 30);
		}
		ctx.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		// One more siren out in the field, put up by hand on its mast (it belongs to no town).
		int fx = cap[4] + cap[6] + 24;
		int fz = cap[5] - 20;
		BlockPos field = new BlockPos(fx, base + 1 + com.stasdoto.airdefense.siren.SirenItem.MAST, fz);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			com.stasdoto.airdefense.siren.SirenItem.buildMast(l, field, net.minecraft.core.Direction.SOUTH);
			l.setBlock(field, com.stasdoto.airdefense.registry.ModBlocks.SIREN.defaultBlockState()
					.setValue(com.stasdoto.airdefense.siren.SirenBlock.FACING, net.minecraft.core.Direction.SOUTH), Block.UPDATE_ALL);
		});
		ctx.waitTicks(30);
		List<BlockPos> fieldList = List.of(field);

		// The tablet: right click opens the map, the "Air raid alert" button the warning page.
		camera(server, cap[4] + 0.5, base + 1, cap[5] + 12.5, 180, 0);
		server.runCommand("gamemode creative @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with airdefense:designator");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(30);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitForScreen(com.stasdoto.airdefense.client.map.TacticalMapScreen.class);
		ctx.waitTicks(10);
		boolean page = ctx.tryClickScreenButton("Air raid alert");
		ctx.waitForScreen(com.stasdoto.airdefense.client.siren.SirenScreen.class);
		ctx.waitTicks(30);
		ctx.takeScreenshot("302_siren_tablet");
		// Alert everywhere.
		boolean all = ctx.tryClickScreenButton("Alert everywhere");
		ctx.waitTicks(50);
		int[] a = signals(server, city);
		int[] af = signals(server, fieldList);
		ctx.takeScreenshot("303_siren_tablet_alert");
		AirDefense.LOGGER.info("[airdefense-test] RESULT siren_all: page {} button {} -> city alert {}/{}, field siren alert {}", page, all, a[1], city.size(), af[1]);
		// The all clear everywhere, then silence.
		boolean clear = ctx.tryClickScreenButton("All clear everywhere");
		ctx.waitTicks(50);
		int[] c = signals(server, city);
		boolean silence = ctx.tryClickScreenButton("Silence all");
		ctx.waitTicks(50);
		int[] q = signals(server, city);
		AirDefense.LOGGER.info("[airdefense-test] RESULT siren_clear: button {} -> all clear {}/{}; silence {} -> quiet {}/{}", clear, c[2], city.size(),
				silence, q[0], city.size());
		// One town: a click on the "Alert" of the first row (the nearest town, the capital).
		int[] click = ctx.computeOnClient(mc -> ((com.stasdoto.airdefense.client.siren.SirenScreen) mc.gui.screen()).townAlertCenter(0));
		int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(click[0] * scale, click[1] * scale);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(50);
		int[] t = signals(server, city);
		int[] tf = signals(server, fieldList);
		String townName = server.computeOnServer(s -> {
			var st = com.stasdoto.airdefense.nation.Politics.get(s).settlements.get(town);
			return st == null ? "-" : st.name + " alert=" + com.stasdoto.airdefense.siren.Sirens.get(s).alert.contains(town);
		});
		ctx.takeScreenshot("304_siren_tablet_town");
		AirDefense.LOGGER.info("[airdefense-test] RESULT siren_town: town {} ({}) -> city alert {}/{}, field siren alert {} (should stay quiet)", town, townName,
				t[1], city.size(), tf[1]);
		int heard = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.siren.SirenClient.voicesNow);
		int starts = ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.siren.SirenClient.startsPlayed);
		AirDefense.LOGGER.info("[airdefense-test] RESULT siren_sound: {} siren voices playing, {} spin-ups heard", heard, starts);
		// The town's all clear; one siren switched on by hand stays on.
		ctx.computeOnClient(mc -> {
			com.stasdoto.airdefense.client.siren.SirenClient.send(com.stasdoto.airdefense.siren.SirenNet.Action.TOWN_CLEAR, town, 0);
			com.stasdoto.airdefense.client.siren.SirenClient.send(com.stasdoto.airdefense.siren.SirenNet.Action.SIREN_MODE,
					com.stasdoto.airdefense.siren.Sirens.MODE_ON, field.asLong());
			return 0;
		});
		ctx.waitTicks(50);
		int[] h = signals(server, city);
		int[] hf = signals(server, fieldList);
		AirDefense.LOGGER.info("[airdefense-test] RESULT siren_manual: town all clear -> city clear {}/{}; field siren switched on -> alert {}", h[2], city.size(), hf[1]);
		ctx.computeOnClient(mc -> {
			com.stasdoto.airdefense.client.siren.SirenClient.send(com.stasdoto.airdefense.siren.SirenNet.Action.SIREN_MODE,
					com.stasdoto.airdefense.siren.Sirens.MODE_AUTO, field.asLong());
			com.stasdoto.airdefense.client.siren.SirenClient.send(com.stasdoto.airdefense.siren.SirenNet.Action.SILENCE, 0, 0);
			return 0;
		});
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");

		// Iron Dome east of the city with its radar.
		int ix = cx + half + 16;
		int iz = cz + 8;
		prepareDefense(ctx, server, VehicleType.IRON_DOME, ix, iz, 270);
		spawnVehicle(server, VehicleType.ELM2084, ix + 4, iz + 14, 250);
		ctx.waitTicks(40);
		shot(ctx, server, look(ix - 9, base + 5, iz + 12, ix, base + 2, iz), "305_iron_dome", 20);
		// A salvo at an empty field: Iron Dome lets it go.
		int ignoredBefore = VehicleEntity.IGNORED_HARMLESS.size();
		int autoBefore = com.stasdoto.airdefense.siren.Sirens.autoAlerts;
		int[] before = counters();
		BlockPos emptyField = new BlockPos(ix + 95, base, iz - 80);
		int launched = MissileStats.STRIKES_LAUNCHED.get();
		launchFrom(ctx, server, VehicleType.HIMARS, ix + 60, iz - 280, emptyField);
		waitUntil(ctx, () -> MissileStats.STRIKES_LAUNCHED.get() > launched, 300);
		ctx.waitTicks(20);
		camera(server, ix - 14, base + 6, iz + 20, 215, -12);
		ctx.waitTicks(260);
		report("iron_dome_field_salvo", before);
		AirDefense.LOGGER.info("[airdefense-test] RESULT iron_dome_ignored: {} rockets let go (falling in an empty field), automatic alerts {}",
				VehicleEntity.IGNORED_HARMLESS.size() - ignoredBefore, com.stasdoto.airdefense.siren.Sirens.autoAlerts - autoBefore);
		// A salvo at the city: Iron Dome fires, the city's sirens sound by themselves.
		before = counters();
		BlockPos square = new BlockPos(cap[4] - 6, base, cap[5] + 10);
		int launched2 = MissileStats.STRIKES_LAUNCHED.get();
		launchFrom(ctx, server, VehicleType.HIMARS, ix + 70, iz - 270, square);
		waitUntil(ctx, () -> MissileStats.STRIKES_LAUNCHED.get() > launched2, 300);
		ctx.waitTicks(10);
		camera(server, ix - 14, base + 6, iz + 20, 200, -22);
		int interceptors = MissileStats.INTERCEPTORS_LAUNCHED.get();
		waitUntil(ctx, () -> MissileStats.INTERCEPTORS_LAUNCHED.get() > interceptors, 300);
		ctx.waitTicks(6);
		ctx.takeScreenshot("306_iron_dome_launch");
		ctx.waitTicks(25);
		ctx.takeScreenshot("307_iron_dome_intercepts");
		ctx.waitTicks(40);
		int[] auto = signals(server, city);
		shot(ctx, server, sirenCam(server, city.getFirst(), 9, -3.6), "308_siren_auto_alert", 30);
		ctx.waitTicks(200);
		report("iron_dome_city_salvo", before);
		AirDefense.LOGGER.info("[airdefense-test] RESULT siren_auto: automatic alerts {}, city sirens on alert {}/{}",
				com.stasdoto.airdefense.siren.Sirens.autoAlerts - autoBefore, auto[1], city.size());
		// Dusk: the lamp on the sounding siren.
		server.runCommand("time set 13200");
		shot(ctx, server, sirenCam(server, city.getFirst(), 7, -2.5), "309_siren_dusk", 40);
		server.runOnServer(s -> com.stasdoto.airdefense.siren.Sirens.get(s).silence());
		server.runCommand("time set 1000");
	}

	/**
	 * 1.24: the arsenal. Every gun on a wall in item frames and in a soldier's hands; each one in first person at
	 * the hip and aimed (with its scope or collimator picture); then the new mechanics: shotgun pellets, the M16A4's
	 * burst, the Barrett against light armour, AT4 / Carl Gustaf / NLAW / Javelin against vehicles (the one-shot tube
	 * is gone after firing, the Javelin needs a lock and dives on the roof), 40 mm grenades.
	 */
	private void arsenal(ClientGameTestContext ctx, TestServerContext server) {
		int x0 = 30000;
		int g = ground;
		final int left = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
		final int right = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
		com.stasdoto.airdefense.weapon.GunType[] all = com.stasdoto.airdefense.weapon.GunType.values();
		server.runCommand("difficulty normal");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("effect give @a minecraft:resistance 600 4 true");
		// A wall of item frames: seven guns a row.
		server.runCommand(String.format("fill %d %d -11 %d %d -10 minecraft:spruce_planks", x0 - 5, g, x0 + 5, g + 6));
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int i = 0; i < all.length; i++) {
				int col = i % 7;
				int row = i / 7;
				var frame = new net.minecraft.world.entity.decoration.ItemFrame(l, new BlockPos(x0 - 3 + col, g + 5 - row, -9), net.minecraft.core.Direction.SOUTH);
				frame.setItem(new ItemStack(com.stasdoto.airdefense.registry.ModItems.GUNS.get(all[i])), false);
				l.addFreshEntity(frame);
			}
		});
		ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) { mc.gui.hud.toggle(); } });
		camera(server, x0 + 0.5, g + 2.0, -5.4, 180, -8);
		ctx.waitTicks(40);
		ctx.takeScreenshot("150_arsenal_wall");
		camera(server, x0 - 1.5, g + 4.5, -7.6, 180, 0);
		ctx.waitTicks(10);
		ctx.takeScreenshot("150b_arsenal_wall_close");

		// Soldiers holding them (third person, from the side), six at a time.
		List<Integer> men = new ArrayList<>();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int i = 0; i < 6; i++) {
				var m = com.stasdoto.airdefense.nation.SoldierEntity.create(l, com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, -1, 5, -1,
						new Vec3(x0 - 4.25 + i * 1.7, g, -24.5), i);
				m.setNoAi(true);
				m.snapTo(x0 - 4.25 + i * 1.7, g, -24.5, -90f, 0f);
				m.setYHeadRot(-90f);
				m.setYBodyRot(-90f);
				l.addFreshEntity(m);
				men.add(m.getId());
			}
		});
		for (int batch = 0; batch * 6 < all.length; batch++) {
			int b = batch;
			server.runOnServer(s -> {
				for (int i = 0; i < men.size(); i++) {
					int k = b * 6 + i;
					if (s.overworld().getEntity(men.get(i)) instanceof net.minecraft.world.entity.LivingEntity m) {
						m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, k < all.length
								? com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(all[k])) : ItemStack.EMPTY);
					}
				}
			});
			camera(server, x0 + 0.0, g + 1.3, -20.4, 180, 6);
			ctx.waitTicks(20);
			ctx.takeScreenshot("151_arsenal_soldiers_" + batch);
		}
		ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) { mc.gui.hud.toggle(); } });
		for (int id : men) {
			server.runOnServer(s -> {
				if (s.overworld().getEntity(id) != null) {
					s.overworld().getEntity(id).discard();
				}
			});
		}

		// First person: every gun at the hip and aimed, looking out over the field at a tank.
		int tank = spawnVehicle(server, VehicleType.T72, x0, -60, 90);
		camera(server, x0 + 0.5, g, 10.5, 180, 0);
		ctx.waitTicks(20);
		for (com.stasdoto.airdefense.weapon.GunType t : all) {
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().setItem(0,
					com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(t))));
			selectSlot(ctx, 0);
			aimAt(ctx, entityPos(server, tank).add(0, 1.2, 0));
			ctx.waitTicks(6);
			ctx.takeScreenshot("152_fp_" + t.id + "_hip");
			ctx.getInput().holdMouse(right);
			ctx.waitTicks(t.needsLock() ? 50 : 12);
			ctx.takeScreenshot("153_fp_" + t.id + "_aim");
			ctx.getInput().releaseMouse(right);
			ctx.waitTicks(3);
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT arsenal_fp: {} guns shown, javelin locks {}", all.length,
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.weapon.GunClient.locksMade));

		// Ammunition for the firing tests.
		server.runOnServer(s -> {
			var inv = s.getPlayerList().getPlayers().getFirst().getInventory();
			inv.clearContent();
			inv.setItem(0, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.M870)));
			inv.setItem(1, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.M16A4)));
			inv.setItem(2, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.M82)));
			inv.setItem(3, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.AT4)));
			inv.setItem(4, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.CG84)));
			inv.setItem(5, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.NLAW)));
			inv.setItem(6, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.JAVELIN)));
			inv.setItem(7, com.stasdoto.airdefense.weapon.GunItem.loaded(com.stasdoto.airdefense.registry.ModItems.GUNS.get(com.stasdoto.airdefense.weapon.GunType.M32)));
			inv.setItem(9, new ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_12G, 24));
			inv.setItem(10, new ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_556, 60));
			inv.setItem(11, new ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_127, 10));
			inv.setItem(12, new ItemStack(com.stasdoto.airdefense.registry.ModItems.CG_ROUND, 2));
			inv.setItem(13, new ItemStack(com.stasdoto.airdefense.registry.ModItems.JAVELIN_MISSILE, 2));
			inv.setItem(14, new ItemStack(com.stasdoto.airdefense.registry.ModItems.AMMO_40MM, 6));
		});

		// Shotgun at three husks eight blocks out.
		camera(server, x0 + 0.5, g, 10.5, 180, 0);
		selectSlot(ctx, 0);
		List<Integer> trio = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			trio.add(husk(server, x0 - 0.5 + i, g, 2.5, false, false));
		}
		ctx.waitTicks(10);
		int pel0 = com.stasdoto.airdefense.weapon.GunServer.PELLETS.get();
		for (int i = 0; i < 3; i++) {
			int id = trio.get(i);
			if (alive(server, id)) {
				aimAt(ctx, entityPos(server, id).add(0, 1.2, 0));
				ctx.waitTicks(2);
				ctx.getInput().pressMouse(left);
				ctx.waitTicks(i == 0 ? 3 : 18);
				if (i == 0) {
					ctx.takeScreenshot("154_shotgun");
					ctx.waitTicks(15);
				}
			}
		}
		int sk = 0;
		for (int id : trio) {
			sk += alive(server, id) ? 0 : 1;
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT shotgun: pellets {} killed {}/3", com.stasdoto.airdefense.weapon.GunServer.PELLETS.get() - pel0, sk);

		// M16A4: one click, one three-round burst.
		selectSlot(ctx, 1);
		aimAt(ctx, new Vec3(x0 + 0.5, g + 1.5, -20));
		ctx.waitTicks(5);
		int sh0 = com.stasdoto.airdefense.weapon.GunServer.SHOTS.get();
		ctx.getInput().pressMouse(left);
		ctx.waitTicks(15);
		AirDefense.LOGGER.info("[airdefense-test] RESULT burst: one pull fired {} rounds", com.stasdoto.airdefense.weapon.GunServer.SHOTS.get() - sh0);

		// Barrett M82 against a BTR-82 forty blocks away.
		int btr = spawnVehicle(server, VehicleType.BTR82, x0 + 12, -28, 90);
		selectSlot(ctx, 2);
		ctx.waitTicks(15);
		float b0 = server.computeOnServer(s -> s.overworld().getEntity(btr) instanceof VehicleEntity v ? v.getHealth() : -1f);
		for (int i = 0; i < 3; i++) {
			aimAt(ctx, entityPos(server, btr).add(0, 1.4, 0));
			ctx.waitTicks(3);
			ctx.getInput().pressMouse(left);
			ctx.waitTicks(14);
		}
		float b1 = server.computeOnServer(s -> s.overworld().getEntity(btr) instanceof VehicleEntity v ? v.getHealth() : -1f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT m82: btr health {} -> {}", b0, b1);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(btr), Entity::discard));

		// AT4, then the Carl Gustaf, at a BMP-2.
		int bmp = spawnVehicle(server, VehicleType.BMP2, x0 - 10, -22, 90);
		ctx.waitTicks(15);
		for (int slot : new int[]{3, 4}) {
			selectSlot(ctx, slot);
			float h0 = server.computeOnServer(s -> s.overworld().getEntity(bmp) instanceof VehicleEntity v ? v.getHealth() : -1f);
			aimAt(ctx, entityPos(server, bmp).add(0, 1.3, 0));
			ctx.waitTicks(4);
			ctx.getInput().pressMouse(left);
			ctx.waitTicks(4);
			ctx.takeScreenshot(slot == 3 ? "155_at4_fired" : "156_cg84_fired");
			ctx.waitTicks(30);
			float h1 = server.computeOnServer(s -> s.overworld().getEntity(bmp) instanceof VehicleEntity v ? v.getHealth() : -1f);
			String inHand = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().getItem().toString());
			AirDefense.LOGGER.info("[airdefense-test] RESULT {}: bmp health {} -> {}, in hand after: {}", slot == 3 ? "at4" : "cg84", h0, h1, inHand);
		}
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(bmp), Entity::discard));

		// NLAW at a fresh BMP-2: it flies over and strikes down.
		int bmp2 = spawnVehicle(server, VehicleType.BMP2, x0 + 6, -32, 90);
		selectSlot(ctx, 5);
		ctx.waitTicks(15);
		int top0 = com.stasdoto.airdefense.missile.MissileEntity.TOP_ATTACKS.get();
		float n0 = server.computeOnServer(s -> s.overworld().getEntity(bmp2) instanceof VehicleEntity v ? v.getHealth() : -1f);
		aimAt(ctx, entityPos(server, bmp2).add(0, 1.0, 0));
		ctx.getInput().holdMouse(right);
		ctx.waitTicks(10);
		ctx.getInput().pressMouse(left);
		ctx.waitTicks(6);
		ctx.getInput().releaseMouse(right);
		ctx.takeScreenshot("157_nlaw_flight");
		ctx.waitTicks(40);
		float n1 = server.computeOnServer(s -> s.overworld().getEntity(bmp2) instanceof VehicleEntity v ? v.getHealth() : -1f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT nlaw: bmp health {} -> {}, top attacks {}", n0, n1,
				com.stasdoto.airdefense.missile.MissileEntity.TOP_ATTACKS.get() - top0);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(bmp2), Entity::discard));

		// Javelin at the T-72, seventy blocks away: no lock - no launch; two seconds in the sight - lock, launch, dive.
		selectSlot(ctx, 6);
		ctx.waitTicks(10);
		int noLock0 = com.stasdoto.airdefense.weapon.GunServer.NO_LOCK.get();
		int topJ = com.stasdoto.airdefense.missile.MissileEntity.TOP_ATTACKS.get();
		int guided0 = com.stasdoto.airdefense.weapon.GunServer.GUIDED.get();
		aimAt(ctx, new Vec3(x0 + 20, g + 2, -40));
		ctx.getInput().pressMouse(left);
		ctx.waitTicks(10);
		float t0 = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getHealth() : -1f);
		aimAt(ctx, entityPos(server, tank).add(0, 1.2, 0));
		ctx.getInput().holdMouse(right);
		ctx.waitTicks(20);
		ctx.takeScreenshot("158_javelin_seek");
		ctx.waitTicks(30);
		ctx.takeScreenshot("158b_javelin_locked");
		ctx.getInput().pressMouse(left);
		ctx.waitTicks(4);
		ctx.getInput().releaseMouse(right);
		ctx.waitTicks(20);
		ctx.takeScreenshot("159_javelin_climb");
		int jw = waitUntil(ctx, () -> com.stasdoto.airdefense.missile.MissileEntity.TOP_ATTACKS.get() > topJ, 160);
		ctx.takeScreenshot("159b_javelin_hit");
		float t1 = server.computeOnServer(s -> s.overworld().getEntity(tank) instanceof VehicleEntity v ? v.getHealth() : -1f);
		AirDefense.LOGGER.info("[airdefense-test] RESULT javelin: refused without lock {}, launched {}, locks {}, top attacks {}, t72 health {} -> {} after {} ticks",
				com.stasdoto.airdefense.weapon.GunServer.NO_LOCK.get() - noLock0, com.stasdoto.airdefense.weapon.GunServer.GUIDED.get() - guided0,
				ctx.computeOnClient(mc -> com.stasdoto.airdefense.client.weapon.GunClient.locksMade),
				com.stasdoto.airdefense.missile.MissileEntity.TOP_ATTACKS.get() - topJ, t0, t1, jw);
		server.runOnServer(s -> forVehicles(s.overworld(), List.of(tank), Entity::discard));

		// M32: two grenades at husks eighteen blocks away.
		selectSlot(ctx, 7);
		List<Integer> grp = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			grp.add(husk(server, x0 - 1 + i * 1.2, g, -8.0, false, false));
		}
		ctx.waitTicks(10);
		aimAt(ctx, new Vec3(x0 + 0.5, g + 2.0, -8.0));
		for (int i = 0; i < 2; i++) {
			ctx.getInput().pressMouse(left);
			ctx.waitTicks(i == 0 ? 8 : 40);
			if (i == 0) {
				ctx.takeScreenshot("160_m32_grenade");
			}
		}
		int gk = 0;
		for (int id : grp) {
			gk += alive(server, id) ? 0 : 1;
		}
		AirDefense.LOGGER.info("[airdefense-test] RESULT m32: killed {}/3", gk);
		server.runCommand("kill @e[type=minecraft:husk]");
		server.runCommand("kill @e[type=minecraft:item_frame]");
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
		// Two villages closer than Nations.SPACING would be one.
		int bx = x + 170;
		int g = ground;
		server.runCommand("difficulty normal");
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set 1000");
		camera(server, x + 85.5, g + 30, 30.5, 180, 40);
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
				m.centerOn(x + 85, 0, 1);
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
					new net.minecraft.world.phys.AABB(x - 120, g - 10, -120, x + 200, g + 30, 120), e -> e.isAlive() && e.country() == ai).size();
			return enemies + " enemies, " + com.stasdoto.airdefense.nation.Nations.guards(s.overworld(), stA).size() + " guards";
		});
		AirDefense.LOGGER.info("[airdefense-test] RESULT squad: sent {}, after the fight {}; soldiers took cover {} fell back {} grenades {}", sent, fight,
				com.stasdoto.airdefense.nation.SoldierEntity.tookCover, com.stasdoto.airdefense.nation.SoldierEntity.fellBack,
				com.stasdoto.airdefense.nation.SoldierEntity.grenadesThrown);

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
		return server.computeOnServer(s -> {
			VehicleEntity v = VehicleEntity.spawn(s.overworld(), type, new Vec3(x + 0.5, ground, z + 0.5), yaw);
			if (type.isLauncher()) {
				// The tests' launchers play the enemy: the air defence (the player's) shoots at what they fire.
				v.country = 900;
			}
			return v.getId();
		});
	}

	/** 1.34: a vehicle of this side (set as it is put down: one far off is not found by its id until its ground runs). */
	private int spawnAs(TestServerContext server, VehicleType type, int x, int z, float yaw, int country) {
		return server.computeOnServer(s -> {
			VehicleEntity v = VehicleEntity.spawn(s.overworld(), type, new Vec3(x + 0.5, ground, z + 0.5), yaw);
			v.country = country;
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
