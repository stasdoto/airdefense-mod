package com.stasdoto.airdefense.client.vehicle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * Vehicles you can hear (new sounds in 1.26): a truck's V8 diesel or a tank's V12 (the Abrams' turbine) idling with a
 * crew aboard and revving with speed, tracks clattering by how fast they run, helicopter rotors spinning up and
 * thumping, a jet's roar with the throttle; hydraulics while an erector moves, the turret drive while it turns.
 */
public final class VehicleSounds {
	private static final double RANGE = 80;
	private static final double AIR_RANGE = 230;
	private static final Map<Integer, Loop[]> PLAYING = new HashMap<>();

	private VehicleSounds() {
	}

	/** For the automated test: how many vehicle loops are playing. */
	public static int playing() {
		int n = 0;
		for (Loop[] loops : PLAYING.values()) {
			for (Loop l : loops) {
				if (l != null && !l.isStopped()) {
					n++;
				}
			}
		}
		return n;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			PLAYING.clear();
			return;
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof VehicleEntity v) || !v.isAlive()) {
				continue;
			}
			double range = v.getVehicleType().isAir() ? AIR_RANGE : RANGE;
			if (v.distanceToSqr(mc.player) > range * range) {
				continue;
			}
			Loop[] loops = PLAYING.computeIfAbsent(v.getId(), id -> new Loop[4]);
			ensure(mc, loops, 0, v, engineSound(v.getVehicleType()), Part.ENGINE);
			if (v.getVehicleType().tracked()) {
				ensure(mc, loops, 1, v, ModSounds.TRACKS, Part.TRACKS);
			}
			ensure(mc, loops, 2, v, ModSounds.HYDRAULICS, Part.HYDRAULICS);
			ensure(mc, loops, 3, v, ModSounds.TURRET, Part.TURRET);
		}
		for (Iterator<Map.Entry<Integer, Loop[]>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
			Loop[] loops = it.next().getValue();
			boolean any = false;
			for (Loop l : loops) {
				any |= l != null && !l.isStopped();
			}
			if (!any) {
				it.remove();
			}
		}
	}

	static SoundEvent engineSound(VehicleType t) {
		if (t.air == VehicleType.HELI) {
			return t == VehicleType.KA52 ? ModSounds.ROTOR_COAX : ModSounds.ROTOR_HEAVY;
		}
		if (t.air == VehicleType.PLANE) {
			return ModSounds.JET;
		}
		if (t.isShip()) {
			// 1.33: a warship's engines (diesels and gas turbines driving water-jets), low and heavy.
			return ModSounds.ENGINE_TURBINE;
		}
		if (t == VehicleType.ABRAMS || t == VehicleType.T80BVM) {
			// Gas turbines: the Abrams and the T-80.
			return ModSounds.ENGINE_TURBINE;
		}
		return t.tracked() ? ModSounds.ENGINE_TRACKED : ModSounds.ENGINE_TRUCK;
	}

	private static void ensure(Minecraft mc, Loop[] loops, int i, VehicleEntity v, SoundEvent event, Part part) {
		if (loops[i] != null && !loops[i].isStopped()) {
			return;
		}
		if (part.level(v, 0) > 0.02f) {
			loops[i] = new Loop(event, v, part);
			mc.getSoundManager().play(loops[i]);
		}
	}

	enum Part {
		ENGINE, TRACKS, HYDRAULICS, TURRET;

		/** 0..1: how loud this part should be right now ({@code spool}: the loop's own engine run-up, for aircraft). */
		float level(VehicleEntity v, float spool) {
			double speed = Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
			return switch (this) {
				case ENGINE -> {
					VehicleType t = v.getVehicleType();
					boolean crewed = !v.getPassengers().isEmpty() || v.getVehicleType().isAir() && v.getState() == VehicleEntity.DEPLOYED;
					if (t.isAir()) {
						// Aircraft: as loud as the engines have spun up (a pilot aboard starts them).
						yield crewed ? Math.max(0.03f, spool) : spool;
					}
					yield (float) Mth.clamp((crewed ? 0.35 : 0) + speed * 1.4, 0, 1);
				}
				case TRACKS -> (float) Mth.clamp(speed * 2.2, 0, 1);
				case HYDRAULICS -> Math.abs(v.elevation - v.elevationO) > 0.03f || Math.abs(v.roofOpen - v.roofOpenO) > 0.002f ? 0.7f : 0f;
				case TURRET -> Math.abs(Mth.wrapDegrees(v.turretYaw - v.turretYawO)) > 0.15f ? 0.55f : 0f;
			};
		}
	}

	/** A looping sound that follows its vehicle and fades in and out with what the part is doing. */
	static final class Loop extends AbstractTickableSoundInstance {
		private final VehicleEntity vehicle;
		private final Part part;
		private float level;
		/** Aircraft engines spin up and down slowly (rotors take a good while). */
		private float spool;
		private int quiet;

		Loop(SoundEvent event, VehicleEntity vehicle, Part part) {
			super(event, SoundSource.NEUTRAL, vehicle.getRandom());
			this.vehicle = vehicle;
			this.part = part;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.pitch = 1f;
			this.x = vehicle.getX();
			this.y = vehicle.getY() + 1;
			this.z = vehicle.getZ();
		}

		@Override
		public void tick() {
			if (!vehicle.isAlive() || vehicle.isRemoved()) {
				stop();
				return;
			}
			VehicleType t = vehicle.getVehicleType();
			// 1.32: an aircraft flown by the enemy's pilot (no passenger here) is "deployed" while it flies.
			boolean crewed = !vehicle.getPassengers().isEmpty() || t.isAir() && vehicle.getState() == VehicleEntity.DEPLOYED;
			if (t.isAir()) {
				spool = Mth.approach(spool, crewed ? 1f : 0f, t.air == VehicleType.HELI ? 0.006f : 0.015f);
			}
			float target = part.level(vehicle, spool);
			// Fast up, slow down: engines spool, hydraulics stop quickly.
			level += (target - level) * (target > level ? 0.25f : part == Part.ENGINE ? 0.06f : 0.3f);
			x = vehicle.getX();
			y = vehicle.getY() + 1;
			z = vehicle.getZ();
			double speed = Math.hypot(vehicle.getX() - vehicle.xo, vehicle.getZ() - vehicle.zo);
			switch (part) {
				case ENGINE -> {
					if (t.air == VehicleType.HELI) {
						// The rotor speeds up to its working rpm; a little harder under load (climbing, fast flight).
						double climb = Math.max(0, vehicle.getY() - vehicle.yo);
						volume = level * 1.4f;
						pitch = 0.45f + 0.55f * spool + (float) Mth.clamp(speed * 0.05 + climb * 0.3, 0, 0.08);
					} else if (t.air == VehicleType.PLANE) {
						float thr = (float) Mth.clamp(speed / Math.max(0.5, t.maxSpeed), 0, 1);
						volume = level * (0.6f + 0.8f * thr);
						pitch = 0.55f + 0.35f * spool + 0.35f * thr;
					} else {
						volume = level * 1.2f;
						pitch = t.isShip() ? 0.5f + level * 0.3f : t == VehicleType.ABRAMS ? 0.8f + level * 0.45f : t == VehicleType.T80BVM ? 0.9f + level * 0.5f
								: 0.62f + level * 0.95f;
						if (t.boat && !t.isShip()) {
							pitch *= 1.15f;
						}
					}
				}
				case TRACKS -> {
					volume = level * 1.1f;
					pitch = 0.6f + level * 0.8f;
				}
				case HYDRAULICS -> {
					volume = level * 0.8f;
					pitch = 0.9f + level * 0.2f;
				}
				case TURRET -> {
					volume = level * 0.8f;
					pitch = 1.0f;
				}
			}
			volume = Math.max(0, volume);
			if (level < 0.015f) {
				if (++quiet > 30) {
					stop();
				}
			} else {
				quiet = 0;
			}
		}
	}
}
