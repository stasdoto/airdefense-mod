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
	KALIBR("kalibr_missile", Kind.CRUISE, true, 1.9, 0.05, 6.5f, false, 4f, 2.11f, 0, 6000, 0.07, Trail.JET, ModSounds.CRUISE_FLIGHT),
	SHAHED("shahed_drone", Kind.DRONE, true, 0.8, 0.03, 4.5f, true, 3f, 1.75f, 0, 9000, 0.05, Trail.NONE, ModSounds.DRONE_BUZZ),
	GMLRS("gmlrs_rocket", Kind.ROCKET, true, 3.8, 0.18, 4.5f, false, 3f, 1.37f, 0, 2400, 0.0, Trail.MEDIUM, ModSounds.MISSILE_FLIGHT),
	PAC3("pac3_missile", Kind.INTERCEPTOR, false, 5.2, 0.35, 2.5f, false, 2f, 1.85f, 3.2, 220, 0.24, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	IRIST("irist_missile", Kind.INTERCEPTOR, false, 4.3, 0.32, 2.2f, false, 2f, 1.27f, 4.0, 190, 0.3, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	AMRAAM("amraam_missile", Kind.INTERCEPTOR, false, 4.0, 0.3, 2.2f, false, 2f, 1.43f, 4.0, 190, 0.26, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	STINGER("stinger_missile", Kind.INTERCEPTOR, false, 3.1, 0.3, 1.6f, false, 1f, 1.0f, 3.0, 150, 0.32, Trail.WHITE, null),
	// Decoys: look like the real thing to a radar, carry no warhead (only a small charge).
	ISKANDER_DECOY("stinger_missile", Kind.BALLISTIC, true, 4.0, 0.06, 0.5f, false, 1f, 1.5f, 0, 1200, 0.0, Trail.MEDIUM, null),
	GERBERA("shahed_drone", Kind.DRONE, true, 0.82, 0.03, 0.7f, false, 1.5f, 1.7f, 0, 9000, 0.05, Trail.NONE, ModSounds.DRONE_BUZZ),
	// RPG-7 rocket grenade: unguided, straight out of the tube, the sustainer burns 1.5 s, then it drops; shaped charge.
	RPG("rpg_round", Kind.DIRECT, false, 3.4, 0.25, 2.2f, false, 1f, 1.0f, 0, 90, 0.0, Trail.SMALL, null);

	public enum Kind { BALLISTIC, ROCKET, CRUISE, DRONE, INTERCEPTOR, DIRECT }

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
		this.maxLife = maxLife;
		this.turnRate = turnRate;
		this.trail = trail;
		this.loopSound = loopSound;
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
			case STINGER -> 0.15;
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
			case STINGER -> switch (target) {
				case BALLISTIC -> 0.03;
				case ROCKET -> 0.2;
				case CRUISE -> 0.55;
				default -> 0.8;
			};
			default -> 0;
		};
	}

	/** Cruise height above the terrain for cruise missiles and drones: high up, they come down only at the end. */
	public double cruiseAltitude() {
		return kind == Kind.DRONE ? 50 : 40;
	}

	public static MissileType byId(int id) {
		MissileType[] values = values();
		return id >= 0 && id < values.length ? values[id] : ISKANDER;
	}
}
