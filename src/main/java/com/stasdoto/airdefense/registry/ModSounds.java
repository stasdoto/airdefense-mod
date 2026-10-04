package com.stasdoto.airdefense.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import com.stasdoto.airdefense.AirDefense;

/** All mod sounds. The range is how far away (in blocks) players still receive the sound. */
public final class ModSounds {
	public static final SoundEvent LAUNCH_HEAVY = register("launch_heavy", 320);
	public static final SoundEvent LAUNCH_LIGHT = register("launch_light", 200);
	public static final SoundEvent LAUNCH_MLRS = register("launch_mlrs", 260);
	public static final SoundEvent MISSILE_FLIGHT = register("missile_flight", 96);
	public static final SoundEvent CRUISE_FLIGHT = register("cruise_flight", 128);
	public static final SoundEvent DRONE_BUZZ = register("drone_buzz", 112);
	public static final SoundEvent EXPLOSION_HUGE = register("explosion_huge", 360);
	public static final SoundEvent EXPLOSION_BIG = register("explosion_big", 260);
	public static final SoundEvent EXPLOSION_AIR = register("explosion_air", 300);
	public static final SoundEvent EXPLOSION_FAR = register("explosion_far", 900);
	public static final SoundEvent GEPARD_BURST = register("gepard_burst", 220);
	public static final SoundEvent RADAR_LOCK = register("radar_lock", 48);
	public static final SoundEvent SIREN = register("siren", 220);
	public static final SoundEvent DESIGNATE = register("designate", 16);
	/** Handed to vanilla explosions: the client plays the real explosion sound itself, delayed by distance. */
	public static final SoundEvent SILENT = register("silent", 16);

	private ModSounds() {
	}

	private static SoundEvent register(String name, float range) {
		Identifier id = AirDefense.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
	}

	public static void init() {
	}
}
