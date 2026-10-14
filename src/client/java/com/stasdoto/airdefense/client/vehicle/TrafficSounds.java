package com.stasdoto.airdefense.client.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.EngineSim;

/**
 * 2.0: the town's traffic heard properly - the nearest few cars, buses, trolleybuses and trams (client.nation.
 * TownTraffic) get engines like the real vehicles' (a car's petrol four through its gears, a bus's diesel, the electric
 * motors' whine), tyres on the road; a bus's air brakes hiss and its doors at the stops, horns now and then.
 */
public final class TrafficSounds {
	/** Kinds as TownTraffic numbers them: car, taxi, bus, trolleybus, tram. */
	public static final int CAR = 0;
	public static final int TAXI = 1;
	public static final int BUS = 2;
	public static final int TROLLEYBUS = 3;
	public static final int TRAM = 4;
	private static final int VOICES = 4;
	private static final double RANGE = 56;

	/** What TownTraffic tells about one of its vehicles. */
	public interface Info {
		int kind(Entity vehicle);

		/** Blocks a tick. */
		double speed(Entity vehicle);
	}

	private static final class Voice {
		final Entity e;
		final int kind;
		final EngineSim sim = new EngineSim();
		final EngineVoice engine;
		final LoopLayer road;
		int seen;
		float fade;

		Voice(Entity e, int kind) {
			this.e = e;
			this.kind = kind;
			EngineSim.Kind k = kindOf(kind);
			this.engine = new EngineVoice(k, kind == BUS ? 0.97f : kind == TAXI ? 1.03f : 1.0f, null, SoundSource.AMBIENT);
			this.road = new LoopLayer(k == EngineSim.Kind.PETROL ? ModSounds.VEH_ROAD : ModSounds.VEH_ROAD_KNOBBY, SoundSource.AMBIENT);
		}
	}

	private static final Map<Entity, Voice> VOICED = new HashMap<>();
	private static final List<Entity> SORTED = new ArrayList<>();

	private TrafficSounds() {
	}

	static EngineSim.Kind kindOf(int kind) {
		return kind == BUS ? EngineSim.Kind.DIESEL : kind >= TROLLEYBUS ? EngineSim.Kind.ELECTRIC : EngineSim.Kind.PETROL;
	}

	public static int playing() {
		int n = 0;
		for (Voice v : VOICED.values()) {
			n += v.engine.playing() + (v.road.playing() ? 1 : 0);
		}
		return n;
	}

	/** Whether this vehicle has its engine heard (TownTraffic then leaves out its old "car going by" sound). */
	public static boolean voiced(Entity e) {
		return VOICED.containsKey(e);
	}

	public static void clear() {
		for (Voice v : VOICED.values()) {
			v.engine.stop();
			v.road.stop();
		}
		VOICED.clear();
	}

	/** Every tick, after the traffic has moved: the nearest few get (or keep) their engines. */
	public static void tick(List<? extends Entity> all, Info info, Vec3 me) {
		int clock = LoopLayer.clock;
		SORTED.clear();
		for (Entity e : all) {
			if (e.position().distanceToSqr(me) < RANGE * RANGE) {
				SORTED.add(e);
			}
		}
		SORTED.sort((a, b) -> Double.compare(a.position().distanceToSqr(me), b.position().distanceToSqr(me)));
		for (int i = 0; i < Math.min(VOICES, SORTED.size()); i++) {
			Entity e = SORTED.get(i);
			int kind = info.kind(e);
			Voice v = VOICED.get(e);
			if (v == null || v.kind != kind) {
				v = new Voice(e, kind);
				VOICED.put(e, v);
			}
			v.seen = clock;
			double sp = info.speed(e);
			EngineSim.Kind k = kindOf(kind);
			double vmax = k == EngineSim.Kind.PETROL ? 1.6 : k == EngineSim.Kind.DIESEL ? 1.0 : 0.7;
			v.sim.step(k, sp, vmax, 0.012, Float.NaN, true, 0, false);
			v.fade = Math.min(1, v.fade + 0.05f);
			double x = e.getX();
			double y = e.getY() + 1;
			double z = e.getZ();
			v.engine.update(x, y, z, v.sim.rpm, v.sim.throttle, v.fade * (k == EngineSim.Kind.PETROL ? 0.75f : k == EngineSim.Kind.DIESEL ? 0.9f : 0.75f));
			float share = (float) Math.min(1.2, Math.abs(sp) / 0.9);
			v.road.set(x, y - 0.7, z, (float) Math.pow(share, 1.3) * 0.8f * v.fade, 0.7f + 0.5f * share);
		}
		for (Iterator<Voice> it = VOICED.values().iterator(); it.hasNext(); ) {
			Voice v = it.next();
			if (v.seen != clock || v.e.isRemoved()) {
				v.engine.stop();
				v.road.stop();
				it.remove();
			}
		}
	}

	private static void play(Entity e, SoundEvent s, float vol, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			mc.level.playLocalSound(e.getX(), e.getY() + 1, e.getZ(), s, SoundSource.AMBIENT, vol, pitch, false);
		}
	}

	/** A bus or trolleybus pulling up at a stop: the air brakes hiss, the doors fold open. */
	public static void arrive(Entity e) {
		play(e, ModSounds.VEH_AIR_BRAKE, 0.8f, 0.95f + 0.1f * e.getRandom().nextFloat());
		play(e, ModSounds.VEH_BUS_DOOR, 0.8f, 1.0f);
	}

	/** Leaving the stop: the doors shut with their hiss. */
	public static void depart(Entity e) {
		play(e, ModSounds.VEH_BUS_DOOR, 0.8f, 0.92f);
	}

	/** A horn of this kind of vehicle (a tram rings its bell instead). */
	public static void horn(Entity e, int kind) {
		if (kind == TRAM) {
			play(e, ModSounds.TRAM_BELL, 0.9f, 1.0f);
			return;
		}
		SoundEvent s = kind == BUS || kind == TROLLEYBUS ? ModSounds.VEH_HORN_BUS : ModSounds.VEH_HORN_CAR;
		play(e, s, 0.7f, kind == TAXI ? 1.06f : 0.96f + 0.08f * e.getRandom().nextFloat());
	}
}
