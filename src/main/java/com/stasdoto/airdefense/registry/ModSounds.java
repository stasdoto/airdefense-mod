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
	public static final SoundEvent RADAR_PING = register("radar_ping", 16);
	// 1.36: the sounds of a town (played by the client round the player, see client.fx.CityAmbience).
	public static final SoundEvent CITY_TRAFFIC = register("city_traffic", 48);
	public static final SoundEvent CITY_CAR = register("city_car", 48);
	public static final SoundEvent CITY_HORN = register("city_horn", 64);
	public static final SoundEvent CITY_BIRDS = register("city_birds", 32);
	public static final SoundEvent CITY_CRICKETS = register("city_crickets", 24);
	public static final SoundEvent CITY_OWL = register("city_owl", 64);
	public static final SoundEvent TOWN_BELL = register("town_bell", 160);
	/** The Shahed's two-stroke buzz, near and far (looped on the client while one flies). */
	public static final SoundEvent SHAHED_LOOP = register("shahed_loop", 160);
	public static final SoundEvent SHAHED_FAR = register("shahed_far", 600);
	public static final SoundEvent CRUISE_LOOP = register("cruise_loop", 220);
	public static final SoundEvent DESIGNATE = register("designate", 16);
	// Stage 6 ("Squad-style"): distance layers picked by the client, see client.fx.SquadAudio.
	public static final SoundEvent EXPLOSION_NEAR = register("explosion_near", 128);
	public static final SoundEvent EXPLOSION_MID = register("explosion_mid", 400);
	public static final SoundEvent EXPLOSION_DISTANT = register("explosion_distant", 1400);
	public static final SoundEvent AIRBURST_NEAR = register("airburst_near", 128);
	public static final SoundEvent AIRBURST_FAR = register("airburst_far", 1400);
	public static final SoundEvent GUN_NEAR = register("gun_near", 96);
	public static final SoundEvent GUN_FAR = register("gun_far", 700);
	public static final SoundEvent CRACK = register("crack", 16);
	public static final SoundEvent LAUNCH_HEAVY_NEAR = register("launch_heavy_near", 128);
	public static final SoundEvent LAUNCH_HEAVY_FAR = register("launch_heavy_far", 1000);
	public static final SoundEvent LAUNCH_LIGHT_NEAR = register("launch_light_near", 96);
	public static final SoundEvent LAUNCH_LIGHT_FAR = register("launch_light_far", 600);
	public static final SoundEvent LAUNCH_MLRS_NEAR = register("launch_mlrs_near", 96);
	public static final SoundEvent LAUNCH_MLRS_FAR = register("launch_mlrs_far", 800);
	public static final SoundEvent ENGINE_TRUCK = register("engine_truck", 32);
	public static final SoundEvent ENGINE_TRACKED = register("engine_tracked", 40);
	/** 1.26: the Abrams' gas turbine, track clatter, helicopter rotors (big five-blade, coaxial), a jet. */
	public static final SoundEvent ENGINE_TURBINE = register("engine_turbine", 48);
	public static final SoundEvent TRACKS = register("tracks", 40);
	public static final SoundEvent ROTOR_HEAVY = register("rotor_heavy", 170);
	public static final SoundEvent ROTOR_COAX = register("rotor_coax", 170);
	public static final SoundEvent JET = register("jet", 240);
	public static final SoundEvent HYDRAULICS = register("hydraulics", 20);
	public static final SoundEvent TURRET = register("turret", 20);
	// Stage 7: small arms (near/far layers picked by the client like the big guns) and the gear.
	public static final SoundEvent RIFLE_NEAR = register("rifle_near", 96);
	public static final SoundEvent RIFLE_FAR = register("rifle_far", 600);
	public static final SoundEvent MG_NEAR = register("mg_near", 96);
	public static final SoundEvent MG_FAR = register("mg_far", 600);
	public static final SoundEvent SNIPER_NEAR = register("sniper_near", 128);
	public static final SoundEvent SNIPER_FAR = register("sniper_far", 800);
	public static final SoundEvent PISTOL_NEAR = register("pistol_near", 64);
	public static final SoundEvent PISTOL_FAR = register("pistol_far", 300);
	public static final SoundEvent GUN_DRY = register("gun_dry", 12);
	public static final SoundEvent GUN_MAG_OUT = register("gun_mag_out", 16);
	public static final SoundEvent GUN_MAG_IN = register("gun_mag_in", 16);
	public static final SoundEvent GUN_BOLT = register("gun_bolt", 16);
	public static final SoundEvent RICOCHET = register("ricochet", 32);
	public static final SoundEvent BULLET_HIT = register("bullet_hit", 24);
	public static final SoundEvent HIT_MARKER = register("hit_marker", 8);
	public static final SoundEvent GRENADE_PIN = register("grenade_pin", 12);
	public static final SoundEvent GRENADE_BOUNCE = register("grenade_bounce", 16);
	public static final SoundEvent THROW = register("throw", 12);
	public static final SoundEvent NVG_SWITCH = register("nvg_switch", 8);
	public static final SoundEvent MEDKIT = register("medkit", 12);
	/** 1.27: the radio on a vest - a squelch and two beeps before a warning. */
	public static final SoundEvent RADIO = register("radio", 8);
	// 1.24: more kinds of report, the pump, the 40 mm launcher, the Javelin's seeker tones.
	public static final SoundEvent CARBINE_NEAR = register("carbine_near", 96);
	public static final SoundEvent CARBINE_FAR = register("carbine_far", 600);
	public static final SoundEvent SHOTGUN_NEAR = register("shotgun_near", 96);
	public static final SoundEvent SHOTGUN_FAR = register("shotgun_far", 400);
	public static final SoundEvent SUPPRESSED_NEAR = register("suppressed_near", 32);
	public static final SoundEvent SUPPRESSED_FAR = register("suppressed_far", 90);
	public static final SoundEvent HEAVY_NEAR = register("heavy_near", 160);
	public static final SoundEvent HEAVY_FAR = register("heavy_far", 1000);
	public static final SoundEvent GUN_PUMP = register("gun_pump", 16);
	public static final SoundEvent GRENADE_LAUNCH = register("grenade_launch", 64);
	public static final SoundEvent JAVELIN_SEEK = register("javelin_seek", 8);
	public static final SoundEvent JAVELIN_LOCK = register("javelin_lock", 8);
	// 1.24: the air raid siren - the wail (near and far layers), the all clear, the spin-up and the coast-down.
	public static final SoundEvent SIREN_WAIL = register("siren_wail", 220);
	public static final SoundEvent SIREN_WAIL_FAR = register("siren_wail_far", 420);
	public static final SoundEvent SIREN_CLEAR = register("siren_clear", 220);
	public static final SoundEvent SIREN_CLEAR_FAR = register("siren_clear_far", 420);
	public static final SoundEvent SIREN_START = register("siren_start", 220);
	public static final SoundEvent SIREN_STOP = register("siren_stop", 260);
	/** Handed to vanilla explosions: the client plays the real explosion sound itself, delayed by distance. */
	public static final SoundEvent SILENT = register("silent", 16);
	/** 1.30: a howitzer's shot near and far, and the whistle of a shell coming in. */
	public static final SoundEvent ARTY_NEAR = register("arty_near", 200);
	public static final SoundEvent ARTY_FAR = register("arty_far", 1800);
	public static final SoundEvent SHELL_WHISTLE = register("shell_whistle", 110);

	private ModSounds() {
	}

	private static SoundEvent register(String name, float range) {
		Identifier id = AirDefense.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
	}

	public static void init() {
	}
}
