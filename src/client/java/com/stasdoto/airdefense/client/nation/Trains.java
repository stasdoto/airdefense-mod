package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.nation.TrainEntity;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.street.TrackBlock;

/**
 * 1.39: the trains round the player, made and run on his own client (the server knows nothing of them). The track
 * blocks of the chunks that come in are noted; now and then, when the player is near a line, a train is put on it a
 * good way off, heading his way - a suburban electric train (it stops at the stations and goes back) or a goods train
 * behind a diesel. It hoots as it comes, the wheels clatter over the joints; far enough off again, it is taken away.
 */
public final class Trains {
	/** The track blocks of each loaded chunk (positions). */
	private static final Map<Long, long[]> TRACKS = new ConcurrentHashMap<>();
	public static final List<TrainEntity> ALL = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static int nextId = -2_000_000;
	private static ClientLevel lastLevel;
	private static int ticks;
	/** Game ticks until the next train may come. */
	private static int wait = 400;
	/** For the tests: a train as soon as there is a line near; trains made, taken away, stops at stations. */
	public static volatile boolean forceSpawn;
	/** For the tests: 1 an electric train next, 2 a goods train (0 either). */
	public static volatile int forceKind;
	public static int made;
	public static int gone;
	public static int hoots;

	private Trains() {
	}

	public static void init() {
		TrainEntity.clientTick = Trains::step;
		ClientTickEvents.END_CLIENT_TICK.register(Trains::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> index(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> TRACKS.remove(chunk.getPos().pack()));
	}

	/** Notes the track blocks of a chunk (only its sections that hold any are looked through). */
	private static void index(LevelChunk chunk) {
		LevelChunkSection[] sections = chunk.getSections();
		long[] found = null;
		int count = 0;
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection s = sections[i];
			if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.getBlock() instanceof TrackBlock)) {
				continue;
			}
			int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (s.getBlockState(x, y, z).getBlock() instanceof TrackBlock) {
							if (found == null) {
								found = new long[64];
							} else if (count == found.length) {
								found = java.util.Arrays.copyOf(found, count * 2);
							}
							found[count++] = BlockPos.asLong(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z);
						}
					}
				}
			}
		}
		long key = chunk.getPos().pack();
		if (count > 0) {
			TRACKS.put(key, java.util.Arrays.copyOf(found, count));
		} else {
			TRACKS.remove(key);
		}
	}

	/** The track block nearest (x, z) within {@code reach} (horizontally), or null. */
	public static BlockPos nearestTrack(double x, double z, int reach) {
		BlockPos best = null;
		double bd = (double) reach * reach;
		for (long[] list : TRACKS.values()) {
			for (long l : list) {
				double dx = BlockPos.getX(l) + 0.5 - x;
				double dz = BlockPos.getZ(l) + 0.5 - z;
				double d = dx * dx + dz * dz;
				if (d < bd) {
					bd = d;
					best = BlockPos.of(l);
				}
			}
		}
		return best;
	}

	public static int indexed() {
		int n = 0;
		for (long[] l : TRACKS.values()) {
			n += l.length;
		}
		return n;
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			ALL.clear();
			if (lastLevel != null && level != null) {
				TRACKS.clear();
			}
			lastLevel = level;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		ALL.removeIf(Entity::isRemoved);
		if (wait > 0) {
			wait--;
		}
		if (++ticks % 20 != 0) {
			return;
		}
		Vec3 me = mc.player.position();
		for (TrainEntity t : new ArrayList<>(ALL)) {
			double d = nearest(t, me);
			if (d > 260 || t.done && d > 40 || t.stuck > 200) {
				remove(level, t);
			}
		}
		if (ALL.isEmpty() && (wait == 0 || forceSpawn) && !TRACKS.isEmpty()) {
			if (spawn(level, me)) {
				forceSpawn = false;
				wait = 1800 + RANDOM.nextInt(2400);
			} else {
				wait = 100;
			}
		}
	}

	/** How near the player the train is (its nearest car). */
	private static double nearest(TrainEntity t, Vec3 me) {
		double best = Double.MAX_VALUE;
		for (double[] p : t.pose) {
			best = Math.min(best, Math.sqrt((p[0] - me.x) * (p[0] - me.x) + (p[2] - me.z) * (p[2] - me.z)));
		}
		return best == Double.MAX_VALUE ? t.position().distanceTo(me) : best;
	}

	private static void remove(ClientLevel level, TrainEntity t) {
		level.removeEntity(t.getId(), Entity.RemovalReason.DISCARDED);
		t.discard();
		ALL.remove(t);
		gone++;
	}

	private static boolean spawn(ClientLevel level, Vec3 me) {
		BlockPos near = nearestTrack(me.x, me.z, 200);
		if (near == null) {
			return false;
		}
		boolean electric = forceKind == 1 || forceKind == 0 && RANDOM.nextInt(10) < 6;
		TrainEntity t = new TrainEntity(ModEntities.TRAIN, level);
		t.electric = electric;
		if (electric) {
			String[] cars = {"emu_head", "emu_car", "emu_car", "emu_head"};
			for (int i = 0; i < cars.length; i++) {
				t.cars.add(cars[i]);
				t.lengths.add(20f);
				t.flipped.add(i == cars.length - 1);
			}
			t.top = 0.85;
		} else {
			t.cars.add("loco");
			t.lengths.add(18f);
			t.flipped.add(false);
			int n = 7 + RANDOM.nextInt(6);
			String kind = null;
			for (int i = 0; i < n; i++) {
				// Wagons in runs of the same kind, as goods trains are made up.
				if (kind == null || RANDOM.nextInt(3) == 0) {
					kind = switch (RANDOM.nextInt(3)) {
						case 0 -> "boxcar";
						case 1 -> "tank_car";
						default -> "gondola";
					};
				}
				t.cars.add(kind);
				t.lengths.add(kind.equals("boxcar") ? 14f : kind.equals("tank_car") ? 12f : 13f);
				t.flipped.add(false);
			}
			t.top = 0.65;
		}
		double len = t.length();
		// Out along the line one way or the other, far enough to be out of the way; the train comes back from there.
		int dir = level.getBlockState(near).getValue(TrackBlock.DIR);
		int[] w = TrackBlock.way(dir);
		int sign = RANDOM.nextBoolean() ? 1 : -1;
		List<BlockPos> out = null;
		for (int k = 0; k < 2; k++) {
			List<BlockPos> o = TrainEntity.trace(level, near, w[0] * sign, w[1] * sign, 170);
			if (o.size() >= len + 20) {
				out = o;
				break;
			}
			sign = -sign;
		}
		if (out == null) {
			return false;
		}
		BlockPos from = out.getLast();
		BlockPos prev = out.get(out.size() - 2);
		t.setId(nextId--);
		t.start(from, Integer.signum(prev.getX() - from.getX()), Integer.signum(prev.getZ() - from.getZ()));
		t.head = len + 0.5;
		// Off at speed, unless it starts from the end of the line (a station).
		boolean station = out.size() < 170;
		t.speed = station ? 0 : t.top;
		t.runTick();
		t.setOldPosAndRot();
		level.addEntity(t);
		ALL.add(t);
		made++;
		return true;
	}

	/** One tick of a train: it runs; it hoots as it comes near; the wheels clatter. */
	private static void step(TrainEntity t) {
		t.runTick();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || t.pose.length == 0) {
			return;
		}
		Vec3 me = mc.player.position();
		double d = nearest(t, me);
		double[] front = t.pose[0];
		double fd = Math.hypot(front[0] - me.x, front[2] - me.z);
		// A hoot when it comes within a hundred blocks (and on setting off from a station).
		boolean leaving = t.departing && fd < 160;
		t.departing = false;
		if (leaving || t.speed > 0.2 && fd < 110 && fd > 50 && t.tickCount - t.lastHoot > 400) {
			t.lastHoot = t.tickCount;
			mc.level.playLocalSound(front[0], front[1] + 3, front[2], t.electric ? ModSounds.TRAIN_HORN : ModSounds.LOCO_HORN, SoundSource.AMBIENT,
					4.0f, 0.95f + RANDOM.nextFloat() * 0.1f, false);
			hoots++;
		}
		// The clatter of the wheels over the rail joints, from the car nearest the player, faster as it goes faster.
		if (t.speed > 0.08 && d < 90) {
			int every = Math.max(4, (int) (12 / Math.max(0.2, t.speed / 0.6)));
			if (t.tickCount % every == 0) {
				double[] near = t.pose[0];
				double nd = Double.MAX_VALUE;
				for (double[] p : t.pose) {
					double pd = (p[0] - me.x) * (p[0] - me.x) + (p[2] - me.z) * (p[2] - me.z);
					if (pd < nd) {
						nd = pd;
						near = p;
					}
				}
				mc.level.playLocalSound(near[0], near[1] + 0.5, near[2], ModSounds.TRAIN_CLACK, SoundSource.AMBIENT,
						(float) Math.min(1.6, 0.4 + t.speed * 1.4), 0.85f + (float) t.speed * 0.3f, false);
			}
		}
	}
}
