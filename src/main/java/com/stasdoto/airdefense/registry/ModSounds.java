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
	// 1.50: a living town - dogs, hammering on the sites, music from the windows, children, trams, buses.
	public static final SoundEvent DOG_BARK = register("dog_bark", 48);
	public static final SoundEvent SITE_HAMMER = register("site_hammer", 64);
	public static final SoundEvent WINDOW_RADIO = register("window_radio", 20);
	public static final SoundEvent KIDS_PLAY = register("kids_play", 40);
	public static final SoundEvent TRAM_BELL = register("tram_bell", 64);
	public static final SoundEvent TRAM_PASS = register("tram_pass", 64);
	public static final SoundEvent BUS_GO = register("bus_go", 48);
	/** 1.38: an emergency vehicle's siren (a three-second wail, played again while it drives with its lights on). */
	public static final SoundEvent SERVICE_SIREN = register("service_siren", 160);
	/** 1.39: the trains - the electric train's horn, the diesel's, the wheels over the rail joints. */
	public static final SoundEvent TRAIN_HORN = register("train_horn", 200);
	public static final SoundEvent LOCO_HORN = register("loco_horn", 220);
	public static final SoundEvent TRAIN_CLACK = register("train_clack", 64);
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
	// 2.0: vehicles' engines in layers by revs (idle / mid / high, on and off load), crossfaded and pitched by the
	// client (client.vehicle.EngineVoice); running gear, the gearbox, brakes, horns, doors, starting and stopping.
	public static final SoundEvent VEH_PETROL_IDLE = register("veh_petrol_idle", 24);
	public static final SoundEvent VEH_PETROL_MID_ON = register("veh_petrol_mid_on", 40);
	public static final SoundEvent VEH_PETROL_MID_OFF = register("veh_petrol_mid_off", 32);
	public static final SoundEvent VEH_PETROL_HIGH_ON = register("veh_petrol_high_on", 56);
	public static final SoundEvent VEH_PETROL_HIGH_OFF = register("veh_petrol_high_off", 40);
	public static final SoundEvent VEH_DIESEL_IDLE = register("veh_diesel_idle", 32);
	public static final SoundEvent VEH_DIESEL_MID_ON = register("veh_diesel_mid_on", 56);
	public static final SoundEvent VEH_DIESEL_MID_OFF = register("veh_diesel_mid_off", 40);
	public static final SoundEvent VEH_DIESEL_HIGH_ON = register("veh_diesel_high_on", 72);
	public static final SoundEvent VEH_DIESEL_HIGH_OFF = register("veh_diesel_high_off", 48);
	public static final SoundEvent VEH_V12_IDLE = register("veh_v12_idle", 48);
	public static final SoundEvent VEH_V12_MID_ON = register("veh_v12_mid_on", 80);
	public static final SoundEvent VEH_V12_MID_OFF = register("veh_v12_mid_off", 56);
	public static final SoundEvent VEH_V12_HIGH_ON = register("veh_v12_high_on", 96);
	public static final SoundEvent VEH_V12_HIGH_OFF = register("veh_v12_high_off", 64);
	public static final SoundEvent VEH_TURBINE_IDLE = register("veh_turbine_idle", 56);
	public static final SoundEvent VEH_TURBINE_MID = register("veh_turbine_mid", 80);
	public static final SoundEvent VEH_TURBINE_HIGH = register("veh_turbine_high", 100);
	public static final SoundEvent VEH_ELECTRIC_LOW = register("veh_electric_low", 32);
	public static final SoundEvent VEH_ELECTRIC_HIGH = register("veh_electric_high", 48);
	public static final SoundEvent VEH_ROTOR_MAIN = register("veh_rotor_main", 220);
	public static final SoundEvent VEH_ROTOR_COAX = register("veh_rotor_coax", 220);
	public static final SoundEvent VEH_ROTOR_APACHE = register("veh_rotor_apache", 220);
	public static final SoundEvent VEH_HELI_TURBINE = register("veh_heli_turbine", 96);
	public static final SoundEvent VEH_JET_IDLE = register("veh_jet_idle", 120);
	public static final SoundEvent VEH_JET_MID = register("veh_jet_mid", 240);
	public static final SoundEvent VEH_JET_HIGH = register("veh_jet_high", 320);
	public static final SoundEvent VEH_TRACKS_SLOW = register("veh_tracks_slow", 48);
	public static final SoundEvent VEH_TRACKS_FAST = register("veh_tracks_fast", 64);
	public static final SoundEvent VEH_ROAD = register("veh_road", 24);
	public static final SoundEvent VEH_ROAD_KNOBBY = register("veh_road_knobby", 32);
	public static final SoundEvent VEH_TYRE_SQUEAL = register("veh_tyre_squeal", 48);
	public static final SoundEvent VEH_REVERSE_BEEP = register("veh_reverse_beep", 32);
	public static final SoundEvent VEH_SHIFT = register("veh_shift", 24);
	public static final SoundEvent VEH_TURBO = register("veh_turbo", 32);
	public static final SoundEvent VEH_BRAKE_SQUEAL = register("veh_brake_squeal", 40);
	public static final SoundEvent VEH_AIR_BRAKE = register("veh_air_brake", 40);
	public static final SoundEvent VEH_HORN_CAR = register("veh_horn_car", 72);
	public static final SoundEvent VEH_HORN_TRUCK = register("veh_horn_truck", 120);
	public static final SoundEvent VEH_HORN_BUS = register("veh_horn_bus", 80);
	public static final SoundEvent VEH_DOOR_OPEN = register("veh_door_open", 16);
	public static final SoundEvent VEH_DOOR_CLOSE = register("veh_door_close", 20);
	public static final SoundEvent VEH_BUS_DOOR = register("veh_bus_door", 24);
	public static final SoundEvent VEH_THUMP = register("veh_thump", 24);
	public static final SoundEvent VEH_START_PETROL = register("veh_start_petrol", 32);
	public static final SoundEvent VEH_START_DIESEL = register("veh_start_diesel", 48);
	public static final SoundEvent VEH_START_TANK = register("veh_start_tank", 72);
	public static final SoundEvent VEH_START_TURBINE = register("veh_start_turbine", 80);
	public static final SoundEvent VEH_STOP_PETROL = register("veh_stop_petrol", 24);
	public static final SoundEvent VEH_STOP_DIESEL = register("veh_stop_diesel", 32);
	public static final SoundEvent VEH_STOP_TANK = register("veh_stop_tank", 48);
	public static final SoundEvent VEH_STOP_TURBINE = register("veh_stop_turbine", 64);

	private ModSounds() {
	}

	private static SoundEvent register(String name, float range) {
		Identifier id = AirDefense.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
	}

	public static void init() {
	}
}
