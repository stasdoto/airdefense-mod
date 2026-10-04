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
 * Vehicles you can hear: the diesel idling when someone is aboard and revving up with speed (tracks clattering on the
 * Gepard), hydraulics whining while an erector or launcher moves, the turret drive humming while it turns.
 */
public final class VehicleSounds {
	private static final double RANGE = 72;
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
			if (!(e instanceof VehicleEntity v) || !v.isAlive() || v.distanceToSqr(mc.player) > RANGE * RANGE) {
				continue;
			}
			Loop[] loops = PLAYING.computeIfAbsent(v.getId(), id -> new Loop[3]);
			ensure(mc, loops, 0, v, v.getVehicleType() == VehicleType.GEPARD ? ModSounds.ENGINE_TRACKED : ModSounds.ENGINE_TRUCK, Part.ENGINE);
			ensure(mc, loops, 1, v, ModSounds.HYDRAULICS, Part.HYDRAULICS);
			ensure(mc, loops, 2, v, ModSounds.TURRET, Part.TURRET);
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

	private static void ensure(Minecraft mc, Loop[] loops, int i, VehicleEntity v, SoundEvent event, Part part) {
		if (loops[i] != null && !loops[i].isStopped()) {
			return;
		}
		if (part.level(v) > 0.02f) {
			loops[i] = new Loop(event, v, part);
			mc.getSoundManager().play(loops[i]);
		}
	}

	enum Part {
		ENGINE, HYDRAULICS, TURRET;

		/** 0..1: how loud this part should be right now. */
		float level(VehicleEntity v) {
			return switch (this) {
				case ENGINE -> {
					double speed = Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
					boolean crewed = !v.getPassengers().isEmpty();
					yield (float) Mth.clamp((crewed ? 0.35 : 0) + speed * 1.4, 0, 1);
				}
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
			float target = part.level(vehicle);
			// Fast up, slow down: engines spool, hydraulics stop quickly.
			level += (target - level) * (target > level ? 0.25f : part == Part.ENGINE ? 0.06f : 0.3f);
			x = vehicle.getX();
			y = vehicle.getY() + 1;
			z = vehicle.getZ();
			volume = Math.max(0.0f, level) * (part == Part.ENGINE ? 1.2f : 0.8f);
			pitch = part == Part.ENGINE ? 0.7f + level * 0.75f : part == Part.HYDRAULICS ? 0.9f + level * 0.2f : 1.0f;
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
