package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.nation.Airports;
import com.stasdoto.airdefense.nation.PlaneEntity;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.street.StandBlock;
import com.stasdoto.airdefense.street.StreetBlock;

/**
 * 1.40: the air traffic at the airports round the player, on his own client. An airport is found by its stands' marks
 * (each knows its number and the way the planes park: the rest of the airport is laid out from the plan in
 * nation.Airports). When the player comes near, a few planes stand parked; every minute or two one lands (down the
 * glide path, rolling out, off the runway, along the taxiway to a free stand) or one leaves (pushed back, taxiing out,
 * lining up, the take-off run, climbing away).
 */
public final class AirTraffic {
	/** An airport as the client sees it: its frame (the threshold, the two ways), the ground's height. */
	public record Field(int ox, int oz, int ux, int uz, int vx, int vz, int y) {
		Vec3 at(double u, double v, double alt) {
			return new Vec3(ox + 0.5 + u * ux + v * vx, y + 1 + alt, oz + 0.5 + u * uz + v * vz);
		}

		long key() {
			return BlockPos.asLong(ox, y, oz);
		}
	}

	private static final class State {
		final Field field;
		final List<PlaneEntity> planes = new ArrayList<>();
		long nextEvent;

		State(Field field) {
			this.field = field;
		}
	}

	private static final Map<Long, long[]> STANDS = new ConcurrentHashMap<>();
	private static final Map<Long, State> STATES = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static int nextId = -3_000_000;
	private static ClientLevel lastLevel;
	private static int ticks;
	/** For the tests: the next event at once (1 a landing, 2 a take-off); planes parked, landed, gone. */
	public static volatile int force;
	public static int parked;
	public static int landed;
	public static int departed;

	private AirTraffic() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AirTraffic::tick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> index(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> STANDS.remove(chunk.getPos().pack()));
	}

	private static void index(LevelChunk chunk) {
		LevelChunkSection[] sections = chunk.getSections();
		List<Long> found = null;
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection s = sections[i];
			if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.getBlock() instanceof StandBlock)) {
				continue;
			}
			int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (s.getBlockState(x, y, z).getBlock() instanceof StandBlock) {
							if (found == null) {
								found = new ArrayList<>();
							}
							found.add(BlockPos.asLong(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z));
						}
					}
				}
			}
		}
		long key = chunk.getPos().pack();
		if (found != null) {
			STANDS.put(key, found.stream().mapToLong(Long::longValue).toArray());
		} else {
			STANDS.remove(key);
		}
	}

	/** The airport a stand mark belongs to. */
	private static Field field(BlockPos p, BlockState s) {
		Direction f = s.getValue(StreetBlock.FACING);
		int k = s.getValue(StandBlock.INDEX);
		int vx = f.getStepX();
		int vz = f.getStepZ();
		// As the plan lays it out: u is v turned a quarter to the right.
		int ux = vz;
		int uz = -vx;
		int su = Airports.standU(k);
		int ox = p.getX() - su * ux - Airports.STAND_V * vx;
		int oz = p.getZ() - su * uz - Airports.STAND_V * vz;
		return new Field(ox, oz, ux, uz, vx, vz, p.getY());
	}

	public static int airports() {
		return STATES.size();
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			STATES.clear();
			if (lastLevel != null && level != null) {
				STANDS.clear();
			}
			lastLevel = level;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		Vec3 me = mc.player.position();
		for (State st : STATES.values()) {
			for (PlaneEntity p : new ArrayList<>(st.planes)) {
				p.setOldPosAndRot();
				p.step();
				sound(mc, p, me);
				if (p.arrived && p.phase == 2) {
					remove(level, st, p);
					departed++;
				} else if (p.arrived && p.phase == 0) {
					p.phase = 1;
					p.parkedAt = level.getGameTime();
					landed++;
				}
			}
		}
		if (++ticks % 20 != 0) {
			return;
		}
		// The airports round the player (by the stand marks of the chunks about him).
		for (long[] list : STANDS.values()) {
			for (long l : list) {
				BlockPos p = BlockPos.of(l);
				if (Math.abs(p.getX() - me.x) > 700 || Math.abs(p.getZ() - me.z) > 700) {
					continue;
				}
				BlockState s = level.getBlockState(p);
				if (!(s.getBlock() instanceof StandBlock)) {
					continue;
				}
				Field f = field(p, s);
				STATES.computeIfAbsent(f.key(), k -> {
					State n = new State(f);
					populate(level, n);
					n.nextEvent = level.getGameTime() + 300 + RANDOM.nextInt(600);
					return n;
				});
			}
		}
		long now = level.getGameTime();
		for (var it = STATES.entrySet().iterator(); it.hasNext(); ) {
			State st = it.next().getValue();
			Vec3 mid = st.field.at(Airports.MID, Airports.TAXI_V, 0);
			if (mid.distanceTo(me) > 1100) {
				for (PlaneEntity p : new ArrayList<>(st.planes)) {
					remove(level, st, p);
				}
				it.remove();
				continue;
			}
			if (force != 0 || now >= st.nextEvent) {
				event(level, st, now);
			}
		}
	}

	private static void remove(ClientLevel level, State st, PlaneEntity p) {
		level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
		p.discard();
		st.planes.remove(p);
	}

	private static PlaneEntity make(ClientLevel level, State st) {
		PlaneEntity p = new PlaneEntity(ModEntities.PLANE, level);
		p.setId(nextId--);
		boolean jet = RANDOM.nextInt(10) < 7;
		p.model = jet ? "airliner" : "turboprop";
		p.nose = jet ? 18.8 : 13.6;
		p.airport = st.field.key();
		return p;
	}

	private static double yawOf(Field f, int du, int dv) {
		double dx = f.ux * du + f.vx * dv;
		double dz = f.uz * du + f.vz * dv;
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** A parked plane at stand k. */
	private static void park(ClientLevel level, State st, PlaneEntity p, int k) {
		Field f = st.field;
		Vec3 at = f.at(Airports.standU(k), Airports.STAND_V - p.nose, 0);
		p.place(at.x, at.y, at.z, yawOf(f, 0, 1));
		p.stand = k;
		p.phase = 1;
	}

	/** Two or three planes at the stands when the player first comes. */
	private static void populate(ClientLevel level, State st) {
		int n = 2 + RANDOM.nextInt(2);
		for (int i = 0; i < n; i++) {
			int k = freeStand(st);
			if (k < 0) {
				break;
			}
			PlaneEntity p = make(level, st);
			park(level, st, p, k);
			p.parkedAt = level.getGameTime() - RANDOM.nextInt(2400);
			level.addEntity(p);
			st.planes.add(p);
			parked++;
		}
	}

	private static int freeStand(State st) {
		List<Integer> free = new ArrayList<>();
		for (int k = 0; k < Airports.STANDS; k++) {
			boolean taken = false;
			for (PlaneEntity p : st.planes) {
				taken |= p.stand == k && p.phase != 2;
			}
			if (!taken) {
				free.add(k);
			}
		}
		return free.isEmpty() ? -1 : free.get(RANDOM.nextInt(free.size()));
	}

	/** Something happens: a plane lands, or one leaves (never two moving at once). */
	private static void event(ClientLevel level, State st, long now) {
		for (PlaneEntity p : st.planes) {
			if (p.phase != 1) {
				st.nextEvent = now + 200;
				return;
			}
		}
		int count = st.planes.size();
		boolean leave = force == 2 || force == 0 && count > 0 && (count >= 3 || RANDOM.nextBoolean());
		force = 0;
		st.nextEvent = now + 1400 + RANDOM.nextInt(1800);
		Field f = st.field;
		if (leave && count > 0) {
			PlaneEntity p = st.planes.getFirst();
			for (PlaneEntity q : st.planes) {
				if (q.parkedAt < p.parkedAt) {
					p = q;
				}
			}
			int su = Airports.standU(p.stand);
			int a = Airports.LINK_A;
			List<PlaneEntity.Point> w = new ArrayList<>();
			add(w, f, su, Airports.STAND_V - p.nose, 0, 0, false, 0);
			// Pushed back to the taxiway, the engines started.
			add(w, f, su, Airports.TAXI_V, 0, 0.1, true, 80);
			add(w, f, su - 12, Airports.TAXI_V, 0, 0.35, false, 0);
			add(w, f, a + 6, Airports.TAXI_V, 0, 0.35, false, 0);
			add(w, f, a, Airports.TAXI_V - 6, 0, 0.3, false, 0);
			add(w, f, a, 6, 0, 0.3, false, 0);
			add(w, f, a + 8, 0, 0, 0.25, false, 0);
			// Lined up, waiting for the take-off clearance; the run, lifting off, climbing away.
			add(w, f, a + 24, 0, 0, 0.2, false, 100);
			add(w, f, a + 24 + 380, 0, 0, 3.0, false, 0);
			add(w, f, a + 24 + 580, 0, 14, 3.1, false, 0);
			add(w, f, 2400, 0, 150, 3.4, false, 0);
			p.go(w);
			p.phase = 2;
			p.stand = -1;
			return;
		}
		int k = freeStand(st);
		if (k < 0) {
			return;
		}
		PlaneEntity p = make(level, st);
		int su = Airports.standU(k);
		int b = Airports.LINK_B;
		List<PlaneEntity.Point> w = new ArrayList<>();
		add(w, f, -1500, 0, (80 + 1500) * 0.0524, 3.2, false, 0);
		add(w, f, -300, 0, (80 + 300) * 0.0524, 3.0, false, 0);
		add(w, f, 80, 0, 0, 2.6, false, 0);
		add(w, f, 380, 0, 0, 0.45, false, 0);
		add(w, f, b - 6, 0, 0, 0.35, false, 0);
		add(w, f, b, 6, 0, 0.3, false, 0);
		add(w, f, b, Airports.TAXI_V - 6, 0, 0.35, false, 0);
		add(w, f, b - 6, Airports.TAXI_V, 0, 0.35, false, 0);
		add(w, f, su + 8, Airports.TAXI_V, 0, 0.35, false, 0);
		add(w, f, su, Airports.TAXI_V + 8, 0, 0.25, false, 0);
		add(w, f, su, Airports.STAND_V - p.nose, 0, 0.12, false, 0);
		p.go(w);
		p.stand = k;
		p.phase = 0;
		p.pose();
		p.poseO = p.pose.clone();
		p.setOldPosAndRot();
		level.addEntity(p);
		st.planes.add(p);
	}

	private static void add(List<PlaneEntity.Point> w, Field f, double u, double v, double alt, double speed, boolean back, int hold) {
		Vec3 p = f.at(u, v, alt);
		w.add(new PlaneEntity.Point(p.x, p.y, p.z, speed, back, hold));
	}

	/** The engines: a roar on the take-off run and climbing out, a whine taxiing near the player. */
	private static void sound(Minecraft mc, PlaneEntity p, Vec3 me) {
		double d = p.position().distanceTo(me);
		if (p.phase == 1 || p.tickCount - p.lastSound < 40) {
			return;
		}
		boolean jet = p.model.equals("airliner");
		if (p.speed > 1.2 && d < 320) {
			p.lastSound = p.tickCount;
			mc.level.playLocalSound(p.getX(), p.getY() + 2, p.getZ(), ModSounds.JET, SoundSource.AMBIENT, 4.0f, jet ? 0.75f : 1.1f, false);
		} else if (p.speed > 0.05 && d < 90) {
			p.lastSound = p.tickCount;
			mc.level.playLocalSound(p.getX(), p.getY() + 2, p.getZ(), ModSounds.ENGINE_TURBINE, SoundSource.AMBIENT, 1.4f, jet ? 0.8f : 1.2f, false);
		}
	}

	/** For the tests: every airport seen and its planes (phase, stand, speed, way point, waiting). */
	public static String debug() {
		StringBuilder sb = new StringBuilder();
		for (State st : STATES.values()) {
			sb.append(String.format(" {%d,%d u%d,%d v%d,%d next %d:", st.field.ox, st.field.oz, st.field.ux, st.field.uz, st.field.vx, st.field.vz,
					st.nextEvent));
			for (PlaneEntity p : st.planes) {
				sb.append(String.format(" [%s ph%d st%d v%.2f n%d/%d h%d %s]", p.model, p.phase, p.stand, p.speed, p.next, p.way.size(), p.holding,
						p.arrived ? "arrived" : "going"));
			}
			sb.append('}');
		}
		return sb.toString();
	}

	/** For the tests: the planes about (x, y, z, phase) of the nearest airport. */
	public static List<double[]> planes() {
		List<double[]> out = new ArrayList<>();
		for (State st : STATES.values()) {
			for (PlaneEntity p : st.planes) {
				out.add(new double[]{p.getX(), p.getY(), p.getZ(), p.phase, p.speed});
			}
		}
		return out;
	}
}
