package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.nation.PropEntity;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * 1.41: a tractor at work in the fields near the player by day, on his client: up one row of the field and down the
 * next with its seed drill, turning at the headlands, round and round the field.
 */
public final class Farms {
	private static final Map<Long, long[]> FIELDS = new ConcurrentHashMap<>();
	private static final List<PropEntity> ALL = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static int nextId = -6_000_000;
	private static ClientLevel lastLevel;
	private static int ticks;
	private static int wait = 200;
	/** For the tests: a tractor as soon as there is a field near; tractors made. */
	public static volatile boolean force;
	public static int made;

	private Farms() {
	}

	public static void init() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			long[] f = Cranes.find(chunk, Blocks.FARMLAND);
			if (f != null && f.length >= 24) {
				FIELDS.put(chunk.getPos().pack(), f);
			} else {
				FIELDS.remove(chunk.getPos().pack());
			}
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> FIELDS.remove(chunk.getPos().pack()));
	}

	public static int count() {
		return ALL.size();
	}

	/** For the tests: where the tractors are. */
	public static List<double[]> where() {
		List<double[]> out = new ArrayList<>();
		for (PropEntity p : ALL) {
			out.add(new double[]{p.getX(), p.getY(), p.getZ()});
		}
		return out;
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			ALL.clear();
			if (lastLevel != null && level != null) {
				FIELDS.clear();
			}
			lastLevel = level;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		Vec3 me = mc.player.position();
		long hour = (Math.floorMod(level.getOverworldClockTime(), 24000L) / 1000 + 6) % 24;
		boolean day = hour >= 7 && hour < 19;
		for (PropEntity p : new ArrayList<>(ALL)) {
			step(p);
			double d = p.position().distanceTo(me);
			if (d > 150 || !day && d > 40 || !level.hasChunkAt(BlockPos.containing(p.position()))) {
				level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
				p.discard();
				ALL.remove(p);
			} else if (d < 40 && p.tickCount % 50 == 0) {
				level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), ModSounds.ENGINE_TRUCK, SoundSource.AMBIENT, 0.8f, 1.3f, false);
			}
		}
		if (wait > 0) {
			wait--;
		}
		if (++ticks % 40 != 0 || !ALL.isEmpty() || !(day || force) || wait > 0 && !force) {
			return;
		}
		// A field near the player (not right under his feet).
		BlockPos start = null;
		for (long[] list : FIELDS.values()) {
			for (long l : list) {
				BlockPos q = BlockPos.of(l);
				double d = Math.hypot(q.getX() - me.x, q.getZ() - me.z);
				if (d > 25 && d < 110 && Math.abs(q.getY() - me.y) < 40) {
					start = q;
					break;
				}
			}
			if (start != null) {
				break;
			}
		}
		if (start == null) {
			return;
		}
		List<double[]> way = rows(level, start);
		if (way.size() < 4) {
			wait = 400;
			return;
		}
		PropEntity p = new PropEntity(ModEntities.PROP, level);
		p.setId(nextId--);
		p.model = "tractor";
		p.way = way;
		p.s = 0;
		p.speed = 0.17;
		step(p);
		p.poseO = p.pose;
		p.setOldPosAndRot();
		level.addEntity(p);
		ALL.add(p);
		made++;
		force = false;
		wait = 1200;
	}

	private static boolean farmland(ClientLevel level, int x, int y, int z) {
		return level.getBlockState(new BlockPos(x, y, z)).is(Blocks.FARMLAND);
	}

	/** The way round the field: along its rows (the longer way), three blocks apart, turning at the ends. */
	private static List<double[]> rows(ClientLevel level, BlockPos f) {
		int y = f.getY();
		int runX = 0;
		int runZ = 0;
		for (int d = -30; d <= 30; d++) {
			runX += farmland(level, f.getX() + d, y, f.getZ()) ? 1 : 0;
			runZ += farmland(level, f.getX(), y, f.getZ() + d) ? 1 : 0;
		}
		boolean alongX = runX >= runZ;
		int ax = alongX ? 1 : 0;
		int az = alongX ? 0 : 1;
		int px = az;
		int pz = ax;
		List<double[]> way = new ArrayList<>();
		int lane = 0;
		for (int k = 0; k < 8; k++) {
			// This row: from one end of the farmland to the other.
			int cx = f.getX() + px * lane;
			int cz = f.getZ() + pz * lane;
			if (!farmland(level, cx, y, cz)) {
				break;
			}
			int a = 0;
			int b = 0;
			while (a > -40 && farmland(level, cx + ax * (a - 1), y, cz + az * (a - 1))) {
				a--;
			}
			while (b < 40 && farmland(level, cx + ax * (b + 1), y, cz + az * (b + 1))) {
				b++;
			}
			if (b - a < 6) {
				break;
			}
			int s0 = k % 2 == 0 ? a - 2 : b + 2;
			int s1 = k % 2 == 0 ? b + 2 : a - 2;
			way.add(new double[]{cx + ax * s0 + 0.5, y + 0.94, cz + az * s0 + 0.5});
			way.add(new double[]{cx + ax * s1 + 0.5, y + 0.94, cz + az * s1 + 0.5});
			lane += 3;
		}
		if (way.size() >= 4) {
			// Back to the first row and round again.
			List<double[]> back = new ArrayList<>(way);
			java.util.Collections.reverse(back);
			way.addAll(back.subList(1, back.size()));
		}
		return way;
	}

	@SuppressWarnings("unchecked")
	private static void step(PropEntity p) {
		p.hold();
		List<double[]> way = (List<double[]>) p.way;
		double total = 0;
		for (int i = 1; i < way.size(); i++) {
			total += dist(way.get(i - 1), way.get(i));
		}
		p.s = (p.s + p.speed) % total;
		double s = p.s;
		for (int i = 1; i < way.size(); i++) {
			double[] a = way.get(i - 1);
			double[] b = way.get(i);
			double l = dist(a, b);
			if (s <= l) {
				double f = s / Math.max(1e-6, l);
				double yaw = Math.toDegrees(Math.atan2(-(b[0] - a[0]), b[2] - a[2]));
				// Turning over the last two blocks of a row towards the next one.
				float prev = (float) p.pose[3];
				float turned = prev + net.minecraft.util.Mth.wrapDegrees((float) yaw - prev) * 0.15f;
				p.move(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, p.tickCount < 2 ? yaw : turned, 0);
				return;
			}
			s -= l;
		}
	}

	private static double dist(double[] a, double[] b) {
		return Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[2] - a[2]) * (b[2] - a[2]));
	}
}
