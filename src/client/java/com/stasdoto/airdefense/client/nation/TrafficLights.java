package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.client.map.AtlasClient;
import com.stasdoto.airdefense.client.vehicle.VehicleLights;
import com.stasdoto.airdefense.street.StreetBlock;
import com.stasdoto.airdefense.street.StreetBlocks;

/**
 * 2.0: the towns' traffic lights work. Each crossing has its own cycle (the two streets in turn: green 100 ticks, amber
 * 20, red 120), set by the world's clock and the crossing's place, so that every player sees the same lights. The
 * light blocks show it (their lenses are dark in the model; the lit one glows, drawn here), the town's cars stop at red
 * and go on green (see TownTraffic), and people cross at the zebras while the cars there have red (see Pedestrians).
 */
public final class TrafficLights {
	public static final int GREEN = 0;
	public static final int AMBER = 1;
	public static final int RED = 2;
	public static final int CYCLE = 240;

	/** The lights round the player (x, y, z, facing as 2D data value, crossing x line, crossing z line). */
	private static final List<int[]> LIGHTS = new ArrayList<>();
	private static final List<int[]> SCANNING = new ArrayList<>();
	/** The crossings that have lights: key of (x line, z line). */
	private static final Map<Long, Boolean> LIT = new HashMap<>();
	private static final Map<Long, Boolean> LIT_NEXT = new HashMap<>();
	/** 2.0: the filling stations' pumps round the player (x, y, z, facing) - found by the same sweep. */
	private static final List<int[]> PUMPS = new ArrayList<>();
	private static final List<int[]> PUMPS_NEXT = new ArrayList<>();
	private static ClientLevel lastLevel;
	private static int scanX;
	private static Vec3 scanAt;
	private static final int R = 64;
	private static final int STRIP = 6;

	private TrafficLights() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(TrafficLights::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(TrafficLights::render);
	}

	// ------------------------------------------------------------------------------------------------
	// The cycle

	/** Where in its cycle the crossing of streets x = {@code ix}, z = {@code iz} is at this time. */
	public static int cycle(long gameTime, int ix, int iz) {
		long h = (ix * 0x9E3779B1L) ^ (iz * 0x85EBCA77L);
		h ^= h >>> 15;
		int offset = (int) Math.floorMod(h * 0x2C1B3C6DL, (long) CYCLE);
		return (int) Math.floorMod(gameTime + offset, (long) CYCLE);
	}

	/**
	 * The light for the cars driving along {@code axis} (0: along z, 1: along x) at a crossing at this point of its
	 * cycle: the x street (axis 1) green first, then the z street.
	 */
	public static int signal(int axis, int t) {
		if (axis == 1) {
			return t < 100 ? GREEN : t < 120 ? AMBER : RED;
		}
		return t < 120 ? RED : t < 220 ? GREEN : AMBER;
	}

	public static int signal(ClientLevel level, int axis, int ix, int iz) {
		return signal(axis, cycle(level.getGameTime(), ix, iz));
	}

	/** Has the crossing of the street lines x = ix, z = iz traffic lights (that the player's game knows of)? */
	public static boolean hasLights(int ix, int iz) {
		return LIT.containsKey(key(ix, iz));
	}

	private static long key(int ix, int iz) {
		return ((long) ix << 32) ^ (iz & 0xFFFFFFFFL);
	}

	/**
	 * May somebody cross the street here, walking across the cars going along {@code axis}? Yes where there are no
	 * lights; at lights, while the cars have red.
	 */
	public static boolean mayWalk(ClientLevel level, double x, double z, double y, int axis) {
		int[] c = crossingNear(level, x, z, y, 10);
		if (c == null || !hasLights(c[0], c[1])) {
			return true;
		}
		int t = cycle(level.getGameTime(), c[0], c[1]);
		// Not in the last seconds of the red: nobody starts out when it is about to change.
		return signal(axis, t) == RED && signal(axis, (t + 30) % CYCLE) == RED;
	}

	/** The crossing (x line, z line) of the town's grid within {@code r} of (x, z), or null. */
	static int[] crossingNear(ClientLevel level, double x, double z, double y, int r) {
		AtlasClient.City town = TownTraffic.grid(level, x, z, y);
		if (town == null) {
			return null;
		}
		int bx = Integer.MIN_VALUE;
		int bz = Integer.MIN_VALUE;
		double best = r + 0.5;
		for (int gx : town.gx()) {
			double d = Math.abs(gx + 0.5 - x);
			if (d < best) {
				best = d;
				bx = gx;
			}
		}
		best = r + 0.5;
		for (int gz : town.gz()) {
			double d = Math.abs(gz + 0.5 - z);
			if (d < best) {
				best = d;
				bz = gz;
			}
		}
		return bx == Integer.MIN_VALUE || bz == Integer.MIN_VALUE ? null : new int[]{bx, bz};
	}

	// ------------------------------------------------------------------------------------------------
	// Finding the lights round the player: a strip of the land a tick (a sweep every ~22 ticks)

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			LIGHTS.clear();
			SCANNING.clear();
			LIT.clear();
			LIT_NEXT.clear();
			PUMPS.clear();
			PUMPS_NEXT.clear();
			lastLevel = level;
			scanAt = null;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		if (!com.stasdoto.airdefense.client.fx.CityAmbience.inTown && LIGHTS.isEmpty() && PUMPS.isEmpty()) {
			return;
		}
		Vec3 me = mc.player.position();
		if (scanAt == null) {
			scanAt = me;
			scanX = -R;
		}
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		int cx = Mth.floor(scanAt.x);
		int cy = Mth.floor(scanAt.y);
		int cz = Mth.floor(scanAt.z);
		for (int x = scanX; x < scanX + STRIP && x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				for (int y = -10; y <= 12; y++) {
					BlockState st = level.getBlockState(q.set(cx + x, cy + y, cz + z));
					if (st.getBlock() == StreetBlocks.FUEL_PUMP) {
						PUMPS_NEXT.add(new int[]{q.getX(), q.getY(), q.getZ(), st.getValue(StreetBlock.FACING).get2DDataValue()});
						continue;
					}
					if (st.getBlock() != StreetBlocks.TRAFFIC_LIGHT) {
						continue;
					}
					int[] c = crossingNear(level, q.getX() + 0.5, q.getZ() + 0.5, q.getY(), 11);
					int facing = st.getValue(StreetBlock.FACING).get2DDataValue();
					SCANNING.add(new int[]{q.getX(), q.getY(), q.getZ(), facing, c == null ? Integer.MIN_VALUE : c[0], c == null ? Integer.MIN_VALUE : c[1]});
					if (c != null) {
						LIT_NEXT.put(key(c[0], c[1]), Boolean.TRUE);
					}
				}
			}
		}
		scanX += STRIP;
		if (scanX > R) {
			// A sweep done: what it found is now what there is.
			LIGHTS.clear();
			LIGHTS.addAll(SCANNING);
			SCANNING.clear();
			LIT.clear();
			LIT.putAll(LIT_NEXT);
			LIT_NEXT.clear();
			PUMPS.clear();
			PUMPS.addAll(PUMPS_NEXT);
			PUMPS_NEXT.clear();
			scanAt = me;
			scanX = -R;
		}
	}

	/** For the tests: traffic lights known round the player. */
	public static int known() {
		return LIGHTS.size();
	}

	/** The filling stations' pumps round the player (x, y, z, facing). */
	public static List<int[]> pumps() {
		return PUMPS;
	}

	// ------------------------------------------------------------------------------------------------
	// Drawing the lit lenses

	/** Lens centres in the north-facing model (sixteenths): x, and y of red, amber, green; the second head's x and z. */
	private static final float[] LENS_Y = {24f, 16.5f, 9f};
	private static final Vector3f CR = new Vector3f();
	private static final Vector3f CU = new Vector3f();

	private static void render(LevelRenderContext ctx) {
		if (LIGHTS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		var camState = ctx.levelState().cameraRenderState;
		Vec3 cam = camState.pos;
		Quaternionf ori = camState.orientation;
		long time = level.getGameTime();
		float dark = VehicleLights.darkness(level);
		CR.set(1, 0, 0);
		ori.transform(CR);
		CU.set(0, 1, 0);
		ori.transform(CU);
		// Quads: 4 corners of x, y, z, u, v; then r, g, b, a (relative to the camera).
		List<float[]> quads = new ArrayList<>();
		for (int[] l : LIGHTS) {
			double dx = l[0] + 0.5 - cam.x;
			double dy = l[1] - cam.y;
			double dz = l[2] + 0.5 - cam.z;
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > 90 * 90) {
				continue;
			}
			int t = l[4] == Integer.MIN_VALUE ? (int) Math.floorMod(time, (long) CYCLE) : cycle(time, l[4], l[5]);
			// Head a (facing north in the model) shows the x street's light; head b (turned east) the z street's.
			for (int head = 0; head < 2; head++) {
				int sig = signal(head == 0 ? 1 : 0, t);
				float px = head == 0 ? 7f : 14.3f;
				float pz = head == 0 ? 1.7f : 9.5f;
				float nx = head == 0 ? 0 : 1;
				float nz = head == 0 ? -1 : 0;
				// The block's turn: north 2, east 3, south 0, west 1 (2D data values) - clockwise steps from north.
				int steps = Math.floorMod(l[3] - 2, 4);
				for (int k = 0; k < steps; k++) {
					float ox = px;
					px = 16 - pz;
					pz = ox;
					float onx = nx;
					nx = -nz;
					nz = onx;
				}
				float ly = LENS_Y[sig] / 16f;
				float wx = (float) (l[0] - cam.x) + px / 16f + nx * 0.02f;
				float wy = (float) (l[1] - cam.y) + ly;
				float wz = (float) (l[2] - cam.z) + pz / 16f + nz * 0.02f;
				float r = sig == RED ? 1f : sig == AMBER ? 1f : 0.35f;
				float g = sig == RED ? 0.12f : sig == AMBER ? 0.62f : 1f;
				float b = sig == RED ? 0.08f : sig == AMBER ? 0.08f : 0.55f;
				// The lens itself, lit: a soft disc flat on the head's face.
				float s = 0.2f;
				float ax = -nz * s;
				float az = nx * s;
				quads.add(new float[]{wx + ax, wy - s, wz + az, 1, 1, wx + ax, wy + s, wz + az, 1, 0, wx - ax, wy + s, wz - az, 0, 0,
						wx - ax, wy - s, wz - az, 0, 1, r, g, b, 1f});
				// Its glow round it (bigger in the dark).
				float h = (0.3f + 0.35f * dark) * (1 + (float) Math.sqrt(d2) * 0.01f);
				float hx = wx + nx * 0.08f;
				float hz = wz + nz * 0.08f;
				quads.add(new float[]{hx + CR.x * h - CU.x * h, wy + CR.y * h - CU.y * h, hz + CR.z * h - CU.z * h, 1, 1,
						hx + CR.x * h + CU.x * h, wy + CR.y * h + CU.y * h, hz + CR.z * h + CU.z * h, 1, 0,
						hx - CR.x * h + CU.x * h, wy - CR.y * h + CU.y * h, hz - CR.z * h + CU.z * h, 0, 0,
						hx - CR.x * h - CU.x * h, wy - CR.y * h - CU.y * h, hz - CR.z * h - CU.z * h, 0, 1, r, g, b, 0.25f + 0.45f * dark});
			}
		}
		if (quads.isEmpty()) {
			return;
		}
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), VehicleLights.GLOW, (pose, vc) -> {
			for (float[] q : quads) {
				int r = (int) (q[20] * 255);
				int g = (int) (q[21] * 255);
				int b = (int) (q[22] * 255);
				int a = (int) (q[23] * 255);
				for (int pass = 0; pass < 2; pass++) {
					for (int i = 0; i < 4; i++) {
						int v = (pass == 0 ? i : 3 - i) * 5;
						vc.addVertex(pose, q[v], q[v + 1], q[v + 2]).setColor(r, g, b, a).setUv(q[v + 3], q[v + 4])
								.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
					}
				}
			}
		});
	}
}
