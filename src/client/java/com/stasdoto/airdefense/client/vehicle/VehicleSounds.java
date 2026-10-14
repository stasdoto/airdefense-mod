package com.stasdoto.airdefense.client.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.EngineSim;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;
import com.stasdoto.airdefense.vehicle.Weapon;

/**
 * Vehicles you can hear (rebuilt in 2.0). Every vehicle near the player has an engine of its kind (a car's petrol
 * four, a truck's turbo diesel, a tank's V12 or gas turbine, a helicopter's rotor and turboshafts, a jet) whose sound
 * follows its revs, throttle and gear (VehicleEntity.getEngineRpm / getThrottle / getGear): layers made at idle, mid
 * and high revs, on and off load, crossfaded and pitched (EngineVoice). Around it: the starter cranking when a driver
 * gets in and the engine dying when he leaves, the gearbox clunking at each change, a diesel's turbo letting go, the
 * tracks clanking by their speed, tyres on the road, squealing in a hard turn or a hard stop, brakes squealing or an
 * air brake hissing as it pulls up, a truck's reverse beeper, the suspension thumping on a drop, doors, the horn
 * ({@link #horn}); hydraulics while an erector moves, the turret drive while it turns.
 */
public final class VehicleSounds {
	private static final double RANGE = 96;
	private static final double AIR_RANGE = 260;
	/** Only the nearest few ground vehicles get the whole treatment (the sound engine has a limited number of voices). */
	private static final int MAX_GROUND = 8;
	private static final Map<Integer, State> STATES = new HashMap<>();
	private static final Map<Integer, Integer> HORNS = new HashMap<>();
	private static final List<VehicleEntity> NEAR = new ArrayList<>();
	/** For the tests: one-shots played (start, stop, shift, turbo, squeal / air brake, thump, door, horn). */
	public static final int[] PLAYED = new int[8];

	private VehicleSounds() {
	}

	/** What one vehicle sounds like right now. */
	private static final class State {
		final VehicleEntity v;
		final EngineSim.Kind kind;
		final EngineVoice engine;
		final LoopLayer tracksSlow;
		final LoopLayer tracksFast;
		final LoopLayer road;
		final LoopLayer squeal;
		final LoopLayer beeper;
		final LoopLayer hydraulics;
		final LoopLayer turret;
		boolean init;
		boolean running;
		int startTicks;
		float fade;
		int gearChanges;
		int highThrottleAt = -1000;
		float speed;
		float peakSpeed;
		int brakingAt = -1000;
		float prevVy;
		float squealLevel;
		int passengers;
		int doorClose;
		float hydraulicsLevel;
		float turretLevel;
		int seen;

		State(VehicleEntity v) {
			this.v = v;
			VehicleType t = v.getVehicleType();
			this.kind = EngineSim.kindOf(t);
			this.engine = new EngineVoice(kind, pitchScale(t), rotorOf(t), SoundSource.NEUTRAL);
			this.tracksSlow = t.tracked() ? new LoopLayer(ModSounds.VEH_TRACKS_SLOW, SoundSource.NEUTRAL) : null;
			this.tracksFast = t.tracked() ? new LoopLayer(ModSounds.VEH_TRACKS_FAST, SoundSource.NEUTRAL) : null;
			boolean wheels = !t.tracked() && !t.boat && !t.isAir();
			this.road = wheels ? new LoopLayer(kind == EngineSim.Kind.PETROL ? ModSounds.VEH_ROAD : ModSounds.VEH_ROAD_KNOBBY, SoundSource.NEUTRAL) : null;
			this.squeal = wheels ? new LoopLayer(ModSounds.VEH_TYRE_SQUEAL, SoundSource.NEUTRAL) : null;
			this.beeper = beeps(t) ? new LoopLayer(ModSounds.VEH_REVERSE_BEEP, SoundSource.NEUTRAL) : null;
			this.hydraulics = new LoopLayer(ModSounds.HYDRAULICS, SoundSource.NEUTRAL);
			this.turret = new LoopLayer(ModSounds.TURRET, SoundSource.NEUTRAL);
		}

		void stop() {
			engine.stop();
			for (LoopLayer l : new LoopLayer[]{tracksSlow, tracksFast, road, squeal, beeper, hydraulics, turret}) {
				if (l != null) {
					l.stop();
				}
			}
		}

		int playing() {
			int n = engine.playing();
			for (LoopLayer l : new LoopLayer[]{tracksSlow, tracksFast, road, squeal, beeper, hydraulics, turret}) {
				n += l != null && l.playing() ? 1 : 0;
			}
			return n;
		}
	}

	/** For the automated test: how many vehicle loops are playing (engines' layers, tracks, tyres... and the town's). */
	public static int playing() {
		int n = TrafficSounds.playing();
		for (State s : STATES.values()) {
			n += s.playing();
		}
		return n;
	}

	/** For the automated test: the engine's state as the sounds see it. */
	public static String describe(VehicleEntity v) {
		State s = STATES.get(v.getId());
		return String.format(java.util.Locale.ROOT, "%s rpm=%.0f thr=%.2f gear=%d running=%s loops=%d", EngineSim.kindOf(v.getVehicleType()),
				v.getEngineRpm(), v.getThrottle(), v.getGear(), v.engineRunning(), s == null ? 0 : s.playing());
	}

	public static void tick(Minecraft mc) {
		LoopLayer.clock++;
		if (mc.level == null || mc.player == null) {
			for (State s : STATES.values()) {
				s.stop();
			}
			STATES.clear();
			HORNS.clear();
			return;
		}
		Vec3 me = mc.player.position();
		NEAR.clear();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof VehicleEntity v && v.isAlive()) {
				double range = v.getVehicleType().isAir() ? AIR_RANGE : RANGE;
				if (v.distanceToSqr(me) < range * range) {
					NEAR.add(v);
				}
			}
		}
		NEAR.sort((a, b) -> Double.compare(a.distanceToSqr(me), b.distanceToSqr(me)));
		int ground = 0;
		int clock = LoopLayer.clock;
		for (VehicleEntity v : NEAR) {
			if (!v.getVehicleType().isAir() && ++ground > MAX_GROUND) {
				continue;
			}
			State s = STATES.get(v.getId());
			if (s == null || s.v != v) {
				if (s != null) {
					s.stop();
				}
				s = new State(v);
				STATES.put(v.getId(), s);
			}
			s.seen = clock;
			update(mc, s);
		}
		for (Iterator<State> it = STATES.values().iterator(); it.hasNext(); ) {
			State s = it.next();
			if (s.seen != clock) {
				s.stop();
				it.remove();
			}
		}
		HORNS.values().removeIf(t -> clock - t > 200);
	}

	private static void update(Minecraft mc, State s) {
		VehicleEntity v = s.v;
		VehicleType t = v.getVehicleType();
		double x = v.getX();
		double y = v.getY() + 1;
		double z = v.getZ();
		float rpm = v.getEngineRpm();
		float thr = v.getThrottle();
		int gear = v.getGear();
		boolean running = v.engineRunning();
		Vec3 f = v.forward();
		float along = (float) ((v.getX() - v.xo) * f.x + (v.getZ() - v.zo) * f.z);
		float prevSpeed = s.speed;
		s.speed += (along - s.speed) * 0.35f;
		float speed = Math.abs(s.speed);
		float vy = (float) (v.getY() - v.yo);
		int passengers = v.getPassengers().size();
		int clock = LoopLayer.clock;
		if (!s.init) {
			s.init = true;
			s.running = running;
			s.fade = running ? 1 : 0;
			s.gearChanges = v.getGearChanges();
			s.passengers = passengers;
			s.prevVy = vy;
		}

		// Starting and stopping (ground vehicles; aircraft spool up and down in their loops).
		if (running != s.running) {
			s.running = running;
			if (!t.isAir()) {
				if (running && speed < 0.05f) {
					SoundEvent start = switch (s.kind) {
						case PETROL -> ModSounds.VEH_START_PETROL;
						case DIESEL -> ModSounds.VEH_START_DIESEL;
						case V12 -> ModSounds.VEH_START_TANK;
						case TURBINE -> ModSounds.VEH_START_TURBINE;
						default -> null;
					};
					if (start != null) {
						oneShot(mc, v, start, 1.0f, pitchScale(t), 0);
						s.startTicks = switch (s.kind) {
							case PETROL -> 30;
							case DIESEL -> 42;
							case V12 -> 60;
							default -> 70;
						};
					}
				} else if (!running) {
					SoundEvent stop = switch (s.kind) {
						case PETROL -> ModSounds.VEH_STOP_PETROL;
						case DIESEL -> ModSounds.VEH_STOP_DIESEL;
						case V12 -> ModSounds.VEH_STOP_TANK;
						case TURBINE -> ModSounds.VEH_STOP_TURBINE;
						default -> null;
					};
					if (stop != null && s.fade > 0.3f) {
						oneShot(mc, v, stop, 0.9f, pitchScale(t), 1);
					}
					s.startTicks = 0;
				}
			}
		}
		float fadeTarget = running ? 1 : 0;
		if (s.startTicks > 0) {
			// The engine loops come in under the end of the start sound (it fades out as they take over).
			s.startTicks--;
			fadeTarget = s.startTicks > 10 ? 0 : 1 - s.startTicks / 10f;
		}
		if (t.isAir()) {
			fadeTarget = 1;
		}
		s.fade += Mth.clamp(fadeTarget - s.fade, -0.15f, 0.1f);
		s.engine.update(x, y, z, rpm, thr, s.fade * loudness(t, s.kind));

		// The gearbox: a clunk at each change; a diesel's turbo letting go when the throttle closes at high revs.
		int changes = v.getGearChanges();
		if (changes != s.gearChanges) {
			s.gearChanges = changes;
			if (running && !t.isAir() && !t.boat && s.kind != EngineSim.Kind.ELECTRIC) {
				float p = s.kind == EngineSim.Kind.PETROL ? 1.25f : s.kind == EngineSim.Kind.DIESEL ? 1.0f : 0.75f;
				float vol = s.kind == EngineSim.Kind.PETROL ? 0.35f : 0.6f;
				oneShot(mc, v, ModSounds.VEH_SHIFT, vol, p * (0.95f + 0.1f * v.getRandom().nextFloat()), 2);
			}
		}
		if (thr > 0.7f) {
			s.highThrottleAt = clock;
		} else if (thr < 0.25f && clock - s.highThrottleAt < 8 && s.kind == EngineSim.Kind.DIESEL && rpm > 1400) {
			s.highThrottleAt = -1000;
			oneShot(mc, v, ModSounds.VEH_TURBO, 0.45f, 0.95f + 0.1f * v.getRandom().nextFloat(), 3);
		}

		// Tracks: the links clank faster with speed (slow and fast loops crossfaded by the link rate).
		if (s.tracksSlow != null) {
			float links = speed * 20 / 0.33f;
			float vol = (float) Math.pow(Mth.clamp(speed / 0.06f, 0, 1), 0.8) * (0.6f + 0.4f * Math.min(1, speed / t.maxSpeed));
			float u = Mth.clamp((links - 14.3f) / (18f - 14.3f), 0, 1);
			s.tracksSlow.set(x, y - 0.5, z, vol * (float) Math.cos(u * Math.PI / 2) * 1.1f, links / 10f);
			s.tracksFast.set(x, y - 0.5, z, vol * (float) Math.sin(u * Math.PI / 2) * 1.1f, links / 26f);
		}

		// Tyres on the road; squealing in a hard turn or a hard stop.
		boolean grounded = v.onGround();
		if (s.road != null) {
			float share = Math.min(1.2f, speed / 0.9f);
			s.road.set(x, y - 0.7, z, grounded ? (float) Math.pow(share, 1.3) * 0.9f : 0, 0.7f + 0.5f * share);
			float yawRate = Math.abs(Mth.wrapDegrees(v.getYRot() - v.yRotO)) * Mth.DEG_TO_RAD;
			float lateral = yawRate * speed;
			float decel = prevSpeed * s.speed > 0 ? (Math.abs(prevSpeed) - speed) : 0;
			float want = Mth.clamp((lateral - 0.05f) / 0.06f, 0, 1);
			if (decel > t.accel * 2.0f && speed > 0.3f) {
				want = Math.max(want, Mth.clamp(speed - 0.2f, 0, 0.7f));
			}
			if (!grounded) {
				want = 0;
			}
			s.squealLevel += (want - s.squealLevel) * (want > s.squealLevel ? 0.5f : 0.2f);
			s.squeal.set(x, y - 0.7, z, s.squealLevel * 0.8f, 0.9f + 0.25f * want);
			// Pulling up after braking: brakes squeal (cars), the air brakes hiss (trucks, buses).
			if (decel > t.accel * 1.2f && speed > 0.08f) {
				s.brakingAt = clock;
			}
			s.peakSpeed = Math.max(s.peakSpeed * 0.995f, speed);
			if (speed < 0.03f && Math.abs(prevSpeed) >= 0.03f && clock - s.brakingAt < 30 && s.peakSpeed > 0.2f) {
				s.peakSpeed = 0;
				if (s.kind == EngineSim.Kind.PETROL) {
					if (v.getRandom().nextFloat() < 0.4f) {
						oneShot(mc, v, ModSounds.VEH_BRAKE_SQUEAL, 0.35f, 0.95f + 0.1f * v.getRandom().nextFloat(), 4);
					}
				} else {
					oneShot(mc, v, ModSounds.VEH_AIR_BRAKE, 0.7f, 0.95f + 0.1f * v.getRandom().nextFloat(), 4);
				}
			}
		}
		if (s.beeper != null) {
			s.beeper.set(x, y, z, running && s.speed < -0.012f ? 0.55f : 0, 1);
		}

		// The suspension: a thump landing from a drop, a knock going up a step.
		if (!t.isAir() && !t.boat) {
			if (s.prevVy < -0.3f && vy > -0.06f) {
				oneShot(mc, v, ModSounds.VEH_THUMP, Math.min(1, -s.prevVy * 1.4f), heavy(t) ? 0.75f : 1.05f, 5);
			} else if (vy > 0.22f && s.prevVy < 0.08f && grounded) {
				oneShot(mc, v, ModSounds.VEH_THUMP, 0.35f, heavy(t) ? 0.8f : 1.15f, 5);
			}
		}
		s.prevVy = vy;

		// Doors (hatches on armour) when someone gets in or out.
		if (passengers != s.passengers) {
			s.passengers = passengers;
			if (!t.isAir()) {
				if (t.armor < 0.9f) {
					oneShot(mc, v, ModSounds.VEH_DOOR_CLOSE, 0.7f, 0.65f, 6);
				} else {
					oneShot(mc, v, ModSounds.VEH_DOOR_OPEN, 0.6f, 1.0f, 6);
					s.doorClose = 8;
				}
			}
		}
		if (s.doorClose > 0 && --s.doorClose == 0) {
			oneShot(mc, v, ModSounds.VEH_DOOR_CLOSE, 0.75f, s.kind == EngineSim.Kind.PETROL ? 1.05f : 0.85f, 6);
		}

		// Hydraulics while an erector moves, the turret drive while it turns.
		float hyd = Math.abs(v.elevation - v.elevationO) > 0.03f || Math.abs(v.roofOpen - v.roofOpenO) > 0.002f ? 0.7f : 0f;
		s.hydraulicsLevel += (hyd - s.hydraulicsLevel) * 0.3f;
		s.hydraulics.set(x, y, z, s.hydraulicsLevel * 0.8f, 0.9f + s.hydraulicsLevel * 0.2f);
		float tur = Math.abs(Mth.wrapDegrees(v.turretYaw - v.turretYawO)) > 0.15f ? 0.55f : 0f;
		s.turretLevel += (tur - s.turretLevel) * 0.3f;
		s.turret.set(x, y, z, s.turretLevel * 0.8f, 1.0f);
	}

	private static void oneShot(Minecraft mc, VehicleEntity v, SoundEvent e, float vol, float pitch, int stat) {
		if (mc.level != null) {
			mc.level.playLocalSound(v.getX(), v.getY() + 1, v.getZ(), e, SoundSource.NEUTRAL, vol, pitch, false);
			PLAYED[stat]++;
		}
	}

	/**
	 * Sounds the vehicle's horn (for the driver's horn key): a car's two-tone, a truck's air horn, the electric horn of
	 * buses and armour. Plays on this client; at most once in 0.6 s per vehicle. True when it sounded.
	 */
	public static boolean horn(VehicleEntity v) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		Integer last = HORNS.get(v.getId());
		if (last != null && LoopLayer.clock - last < 12) {
			return false;
		}
		HORNS.put(v.getId(), LoopLayer.clock);
		VehicleType t = v.getVehicleType();
		EngineSim.Kind k = EngineSim.kindOf(t);
		SoundEvent e = k == EngineSim.Kind.PETROL ? ModSounds.VEH_HORN_CAR
				: k == EngineSim.Kind.DIESEL && t.armor >= 0.9f ? ModSounds.VEH_HORN_TRUCK : ModSounds.VEH_HORN_BUS;
		oneShot(mc, v, e, 1.0f, k == EngineSim.Kind.V12 || k == EngineSim.Kind.TURBINE ? 0.9f : 1.0f, 7);
		return true;
	}

	/** How loud this vehicle's engine is overall. */
	private static float loudness(VehicleType t, EngineSim.Kind k) {
		return switch (k) {
			case PETROL -> 0.8f;
			case DIESEL -> 0.95f;
			case ELECTRIC -> 0.7f;
			default -> 1.0f;
		};
	}

	private static boolean heavy(VehicleType t) {
		return t.tracked() || t.maxHealth >= 300 || t.isShip();
	}

	/** Trucks that back up beeping: unarmoured wheeled diesel trucks (logistics, launchers and radars on truck chassis). */
	private static boolean beeps(VehicleType t) {
		return !t.tracked() && !t.boat && !t.isAir() && t.armor >= 0.9f && EngineSim.kindOf(t) == EngineSim.Kind.DIESEL;
	}

	/** Bigger / smaller engines of a kind sound a little lower / higher; helicopters' rotors turn at their own rates. */
	static float pitchScale(VehicleType t) {
		if (t.isShip()) {
			return 0.68f;
		}
		return switch (t) {
			case MI8 -> 0.92f;
			case MI24 -> 1.14f;
			case KA52 -> 1.06f;
			case AH64 -> 0.96f;
			case SU25 -> 0.92f;
			case A10 -> 1.12f;
			case RHIB -> 1.12f;
			default -> {
				EngineSim.Kind k = EngineSim.kindOf(t);
				if (k == EngineSim.Kind.V12) {
					boolean tank = t.weapon == Weapon.CANNON_125 || t.weapon == Weapon.CANNON_120 || t.maxHealth >= 600;
					yield tank ? 1.0f : 1.12f;
				}
				if (k == EngineSim.Kind.DIESEL) {
					yield t.boxWidth <= 2.5f && t.maxHealth <= 240 ? 1.1f : t.boat ? 1.05f : 1.0f;
				}
				yield 1.0f;
			}
		};
	}

	static SoundEvent rotorOf(VehicleType t) {
		return t == VehicleType.KA52 ? ModSounds.VEH_ROTOR_COAX : t == VehicleType.AH64 ? ModSounds.VEH_ROTOR_APACHE : ModSounds.VEH_ROTOR_MAIN;
	}
}
