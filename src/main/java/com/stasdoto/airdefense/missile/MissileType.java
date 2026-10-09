package com.stasdoto.airdefense.missile;

import net.minecraft.sounds.SoundEvent;

import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Every flying thing in the mod. Distances are in blocks, speeds in blocks per tick (20 ticks = 1 s).
 * The numbers are scaled down to Minecraft size: the flight profiles copy the real systems
 * (ballistic arc, low terrain-following cruise, slow drone, fast agile interceptors), not their real ranges.
 */
public enum MissileType {
	//            kind                 threat maxSpd accel  power  fire   hp   scale prox  life  turn   trail             loop sound
	ISKANDER("iskander_missile", Kind.BALLISTIC, true, 4.6, 0.07, 9.0f, false, 8f, 2.54f, 0, 3000, 0.0, Trail.HEAVY, null),
	KALIBR("kalibr_missile", Kind.CRUISE, true, 1.9, 0.05, 6.5f, false, 4f, 2.11f, 0, 6000, 0.07, Trail.JET, null),
	SHAHED("shahed_drone", Kind.DRONE, true, 0.8, 0.03, 4.5f, true, 3f, 1.75f, 0, 9000, 0.05, Trail.NONE, null),
	GMLRS("gmlrs_rocket", Kind.ROCKET, true, 3.8, 0.18, 4.5f, false, 3f, 1.37f, 0, 2400, 0.0, Trail.MEDIUM, ModSounds.MISSILE_FLIGHT),
	PAC3("pac3_missile", Kind.INTERCEPTOR, false, 5.2, 0.35, 2.5f, false, 2f, 1.85f, 3.2, 220, 0.24, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	IRIST("irist_missile", Kind.INTERCEPTOR, false, 4.3, 0.32, 2.2f, false, 2f, 1.27f, 4.0, 190, 0.3, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	AMRAAM("amraam_missile", Kind.INTERCEPTOR, false, 4.0, 0.3, 2.2f, false, 2f, 1.43f, 4.0, 190, 0.26, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	STINGER("stinger_missile", Kind.INTERCEPTOR, false, 3.1, 0.3, 1.6f, false, 1f, 1.0f, 3.0, 150, 0.32, Trail.WHITE, null),
	// Decoys: look like the real thing to a radar, carry no warhead (only a small charge).
	ISKANDER_DECOY("stinger_missile", Kind.BALLISTIC, true, 4.0, 0.06, 0.5f, false, 1f, 1.5f, 0, 1200, 0.0, Trail.MEDIUM, null),
	GERBERA("shahed_drone", Kind.DRONE, true, 0.82, 0.03, 0.7f, false, 1.5f, 1.7f, 0, 9000, 0.05, Trail.NONE, null),
	// RPG-7 rocket grenade: unguided, straight out of the tube, the sustainer burns 1.5 s, then it drops; shaped charge.
	RPG("rpg_round", Kind.DIRECT, false, 3.4, 0.25, 2.2f, false, 1f, 1.0f, 0, 90, 0.0, Trail.SMALL, null),
	// Stage R4: the interceptors of the other air defence systems (the models reuse the closest-looking missile).
	M57E6("irist_missile", Kind.INTERCEPTOR, false, 4.6, 0.36, 2.0f, false, 2f, 1.2f, 3.5, 170, 0.28, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	M9M338("amraam_missile", Kind.INTERCEPTOR, false, 3.9, 0.34, 2.0f, false, 2f, 1.25f, 3.5, 160, 0.3, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	M9M317("pac3_missile", Kind.INTERCEPTOR, false, 4.4, 0.3, 3.0f, false, 2f, 2.3f, 4.0, 230, 0.22, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	M48N6("pac3_missile", Kind.INTERCEPTOR, false, 5.6, 0.32, 3.5f, false, 2f, 2.8f, 4.0, 260, 0.2, Trail.HEAVY, ModSounds.MISSILE_FLIGHT),
	M9M33("amraam_missile", Kind.INTERCEPTOR, false, 3.6, 0.3, 1.8f, false, 2f, 1.35f, 3.5, 150, 0.27, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	M9M37("stinger_missile", Kind.INTERCEPTOR, false, 3.0, 0.3, 1.5f, false, 1f, 1.5f, 3.0, 140, 0.32, Trail.WHITE, null),
	ASTER30("irist_missile", Kind.INTERCEPTOR, false, 5.4, 0.36, 2.5f, false, 2f, 2.0f, 3.2, 230, 0.26, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	M9M311("irist_missile", Kind.INTERCEPTOR, false, 4.2, 0.34, 1.8f, false, 2f, 1.0f, 3.5, 150, 0.3, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	// Stage R6: aircraft weapons.
	S8("rpg_round", Kind.DIRECT, false, 4.5, 0.4, 2.4f, false, 1f, 1.1f, 0, 140, 0.0, Trail.SMALL, null),
	FAB250("kalibr_missile", Kind.DIRECT, false, 0, 0, 6.0f, false, 1f, 0.75f, 0, 600, 0.0, Trail.NONE, null),
	AIM9("stinger_missile", Kind.INTERCEPTOR, false, 4.8, 0.4, 2.0f, false, 1f, 1.3f, 3.5, 200, 0.3, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	// Stage R9: piloted from their camera - an FPV quadcopter with an RPG grenade, a Magura sea drone with 300 kg.
	FPV("fpv_drone", Kind.DIRECT, false, 1.4, 0.06, 2.8f, false, 1f, 0.8f, 0, 1800, 0.0, Trail.NONE, ModSounds.DRONE_BUZZ),
	MAGURA("magura_drone", Kind.DIRECT, false, 1.1, 0.03, 8.0f, true, 6f, 2.4f, 0, 6000, 0.0, Trail.NONE, ModSounds.ENGINE_TRUCK),
	// 1.24: what the infantry's launchers fire (render scale 1 = real size). RPG-22, AT4 and the Carl Gustaf round fly
	// straight; the NLAW flies a line just above the sight line and goes off over a vehicle (top attack); the
	// Javelin climbs high and dives onto the locked target; the 40 mm grenade arcs.
	RPG22("rpg22_rocket", Kind.DIRECT, false, 3.0, 0.3, 1.9f, false, 1f, 1.0f, 0, 80, 0.0, Trail.SMALL, null),
	AT4("at4_rocket", Kind.DIRECT, false, 4.0, 0.0, 2.3f, false, 1f, 1.0f, 0, 90, 0.0, Trail.SMALL, null),
	CG84("cg_round", Kind.DIRECT, false, 4.2, 0.0, 2.6f, false, 1f, 1.0f, 0, 100, 0.0, Trail.SMALL, null),
	NLAW("nlaw_missile", Kind.DIRECT, false, 3.4, 0.3, 2.4f, false, 1f, 1.0f, 0, 120, 0.0, Trail.SMALL, null),
	JAVELIN("javelin_missile", Kind.DIRECT, false, 2.4, 0.12, 3.2f, false, 1f, 1.0f, 0, 400, 0.18, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	G40("ammo_40mm", Kind.DIRECT, false, 3.2, 0.0, 1.6f, false, 1f, 1.0f, 0, 200, 0.0, Trail.NONE, null),
	// 1.24: Iron Dome's Tamir - quick, very agile, a proximity fuse (the model is drawn at half size, so scale 2).
	TAMIR("tamir_missile", Kind.INTERCEPTOR, false, 4.6, 0.38, 2.0f, false, 2f, 2.0f, 3.6, 170, 0.36, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	// 1.30: artillery. Howitzer shells have no motor: they leave the barrel at full speed and fly a high arc (the air
	// defence leaves them be); the Grad's 122 mm rocket burns for a moment and flies the same kind of arc.
	SHELL_152("shell_152", Kind.ROCKET, false, 4.0, 0.0, 3.4f, false, 1f, 1.0f, 0, 700, 0.0, Trail.NONE, null),
	SHELL_155("shell_155", Kind.ROCKET, false, 4.2, 0.0, 3.5f, false, 1f, 1.0f, 0, 700, 0.0, Trail.NONE, null),
	GRAD("grad_rocket", Kind.ROCKET, true, 3.4, 0.22, 2.5f, false, 1f, 1.0f, 0, 700, 0.0, Trail.SMALL, null),
	// 1.31: the TOS-1A's 220 mm thermobaric rocket: a cloud of fuel set off - a huge fireball and a blast that crushes.
	TOS("tos_rocket", Kind.ROCKET, true, 2.8, 0.2, 5.5f, true, 1f, 2.0f, 0, 700, 0.0, Trail.MEDIUM, null);

	public enum Kind { BALLISTIC, ROCKET, CRUISE, DRONE, INTERCEPTOR, DIRECT }

	/**
	 * 1.24: every launcher, air defence missile and radar reaches five times further than before (the numbers in the
	 * tables are the old ones); the guns keep their range.
	 */
	public static final int RANGE_SCALE = 5;

	public enum Trail { HEAVY, MEDIUM, JET, WHITE, NONE, SMALL }

	public final String itemId;
	public final Kind kind;
	public final boolean threat;
	public final double maxSpeed;
	public final double accel;
	public final float power;
	public final boolean fire;
	public final float health;
	public final float renderScale;
	public final double proximity;
	public final int maxLife;
	public final double turnRate;
	public final Trail trail;
	public final SoundEvent loopSound;

	MissileType(String itemId, Kind kind, boolean threat, double maxSpeed, double accel, float power, boolean fire,
			float health, float renderScale, double proximity, int maxLife, double turnRate, Trail trail, SoundEvent loopSound) {
		this.itemId = itemId;
		this.kind = kind;
		this.threat = threat;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.power = power;
		this.fire = fire;
		this.health = health;
		this.renderScale = renderScale;
		this.proximity = proximity;
		// Far-flying things live long enough for the longer ranges; infantry rounds do not change.
		this.maxLife = kind == Kind.DIRECT ? maxLife : maxLife * RANGE_SCALE;
		this.turnRate = turnRate;
		this.trail = trail;
		this.loopSound = loopSound;
	}

	/** Flies to a target on its own after the launch (Javelin: the locked vehicle; NLAW: over the sight line). */
	public boolean guided() {
		return this == JAVELIN || this == NLAW;
	}

	/** An infantry rocket or grenade (no sustainer burn of the RPG's kind: AT4, Carl Gustaf, 40 mm). */
	public boolean ballisticRound() {
		return this == AT4 || this == CG84 || this == G40;
	}

	/** Damage a direct hit does to a vehicle (shaped charges; the top attackers hit the thin roof). */
	public float vehicleDamage() {
		return switch (this) {
			case RPG -> 260f;
			case RPG22 -> 220f;
			case AT4 -> 320f;
			case CG84 -> 380f;
			case NLAW -> 900f;
			case JAVELIN -> 1400f;
			case G40 -> 30f;
			case FPV -> 260f;
			case MAGURA -> 600f;
			default -> 70f;
		};
	}

	/** 1.30: a howitzer shell (no motor, no trail). */
	public boolean shell() {
		return this == SHELL_152 || this == SHELL_155;
	}

	/** 1.30: what artillery fires - shells and the Grad's rockets (a counter-battery radar tracks these back). */
	public boolean artillery() {
		return shell() || this == GRAD || this == TOS;
	}

	/** Flown by a player from its camera. */
	public boolean piloted() {
		return this == FPV || this == MAGURA;
	}

	/** A decoy: on the radar it is a missile or a drone, but it has no real warhead. */
	public boolean isDecoy() {
		return this == ISKANDER_DECOY || this == GERBERA;
	}

	/**
	 * Chance that this interceptor's seeker loses the target on the way (clutter, a hard turn, a failure):
	 * it then flies on blind and blows itself up.
	 */
	public double seekerFailChance() {
		return switch (this) {
			case PAC3 -> 0.05;
			case IRIST -> 0.06;
			case AMRAAM -> 0.08;
			case STINGER, M9M37 -> 0.15;
			case M57E6, M9M311 -> 0.08;
			case M9M338, M9M33 -> 0.07;
			case M9M317, M48N6 -> 0.06;
			case ASTER30 -> 0.04;
			case AIM9 -> 0.08;
			case TAMIR -> 0.04;
			default -> 0;
		};
	}

	/** Chance that this interceptor destroys a target of the given kind once its fuse triggers. */
	public double killChance(Kind target) {
		return switch (this) {
			case PAC3 -> switch (target) {
				case BALLISTIC -> 0.82;
				case ROCKET -> 0.78;
				default -> 0.85;
			};
			case IRIST -> switch (target) {
				case BALLISTIC -> 0.3;
				case ROCKET -> 0.62;
				default -> 0.86;
			};
			case AMRAAM -> switch (target) {
				case BALLISTIC -> 0.12;
				case ROCKET -> 0.45;
				default -> 0.8;
			};
			case STINGER, M9M37 -> switch (target) {
				case BALLISTIC -> 0.03;
				case ROCKET -> 0.2;
				case CRUISE -> 0.55;
				default -> 0.8;
			};
			case M57E6, M9M311 -> switch (target) {
				case BALLISTIC -> 0.08;
				case ROCKET -> 0.45;
				case CRUISE -> 0.75;
				default -> 0.85;
			};
			case M9M338 -> switch (target) {
				case BALLISTIC -> 0.15;
				case ROCKET -> 0.55;
				case CRUISE -> 0.8;
				default -> 0.88;
			};
			case M9M33 -> switch (target) {
				case BALLISTIC -> 0.05;
				case ROCKET -> 0.3;
				case CRUISE -> 0.65;
				default -> 0.75;
			};
			case M9M317 -> switch (target) {
				case BALLISTIC -> 0.35;
				case ROCKET -> 0.55;
				case CRUISE -> 0.8;
				default -> 0.8;
			};
			case M48N6 -> switch (target) {
				case BALLISTIC -> 0.6;
				case ROCKET -> 0.65;
				case CRUISE -> 0.82;
				default -> 0.75;
			};
			case AIM9 -> switch (target) {
				case BALLISTIC -> 0.05;
				case ROCKET -> 0.3;
				case CRUISE -> 0.8;
				default -> 0.9;
			};
			case TAMIR -> switch (target) {
				case BALLISTIC -> 0.12;
				case ROCKET -> 0.92;
				case CRUISE -> 0.85;
				default -> 0.9;
			};
			case ASTER30 -> switch (target) {
				case BALLISTIC -> 0.72;
				case ROCKET -> 0.7;
				case CRUISE -> 0.88;
				default -> 0.85;
			};
			default -> 0;
		};
	}

	/** Cruise height above the terrain for cruise missiles and drones: high up, they come down only at the end. */
	public double cruiseAltitude() {
		return kind == Kind.DRONE ? 70 : 55;
	}

	/**
	 * How much bigger than its entity box a bullet finds it: a Shahed is 2.5 m across the wings, a cruise missile has
	 * wings too; rockets and ballistic missiles are about as thin as the box.
	 */
	public double hitGrow() {
		return switch (kind) {
			case DRONE -> 0.9;
			case CRUISE -> 0.35;
			case BALLISTIC, ROCKET -> 0.1;
			default -> 0;
		};
	}

	public static MissileType byId(int id) {
		MissileType[] values = values();
		return id >= 0 && id < values.length ? values[id] : ISKANDER;
	}
}
