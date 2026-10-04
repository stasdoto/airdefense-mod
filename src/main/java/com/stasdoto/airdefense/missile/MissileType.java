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
	ISKANDER("iskander_missile", Kind.BALLISTIC, true, 4.6, 0.07, 9.0f, false, 8f, 1.45f, 0, 3000, 0.0, Trail.HEAVY, null),
	KALIBR("kalibr_missile", Kind.CRUISE, true, 1.9, 0.05, 6.5f, false, 4f, 1.25f, 0, 6000, 0.07, Trail.JET, ModSounds.CRUISE_FLIGHT),
	SHAHED("shahed_drone", Kind.DRONE, true, 0.8, 0.03, 4.5f, true, 3f, 1.0f, 0, 9000, 0.05, Trail.NONE, ModSounds.DRONE_BUZZ),
	GMLRS("gmlrs_rocket", Kind.ROCKET, true, 3.8, 0.18, 4.5f, false, 3f, 1.0f, 0, 2400, 0.0, Trail.MEDIUM, ModSounds.MISSILE_FLIGHT),
	PAC3("pac3_missile", Kind.INTERCEPTOR, false, 5.2, 0.35, 2.5f, false, 2f, 1.0f, 3.2, 220, 0.24, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	IRIST("irist_missile", Kind.INTERCEPTOR, false, 4.3, 0.32, 2.2f, false, 2f, 0.9f, 4.0, 190, 0.3, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	AMRAAM("amraam_missile", Kind.INTERCEPTOR, false, 4.0, 0.3, 2.2f, false, 2f, 0.9f, 4.0, 190, 0.26, Trail.WHITE, ModSounds.MISSILE_FLIGHT),
	STINGER("stinger_missile", Kind.INTERCEPTOR, false, 3.1, 0.3, 1.6f, false, 1f, 0.55f, 3.0, 150, 0.32, Trail.WHITE, null);

	public enum Kind { BALLISTIC, ROCKET, CRUISE, DRONE, INTERCEPTOR }

	public enum Trail { HEAVY, MEDIUM, JET, WHITE, NONE }

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

	/** Chance that this interceptor destroys a target of the given kind once its fuse triggers. */
	public double killChance(Kind target) {
		return switch (this) {
			case PAC3 -> switch (target) {
				case BALLISTIC -> 0.9;
				case ROCKET -> 0.85;
				default -> 0.92;
			};
			case IRIST -> switch (target) {
				case BALLISTIC -> 0.35;
				case ROCKET -> 0.7;
				default -> 0.93;
			};
			case AMRAAM -> switch (target) {
				case BALLISTIC -> 0.15;
				case ROCKET -> 0.5;
				default -> 0.88;
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

	/** Cruise height above the terrain for cruise missiles and drones. */
	public double cruiseAltitude() {
		return kind == Kind.DRONE ? 22 : 14;
	}

	public static MissileType byId(int id) {
		MissileType[] values = values();
		return id >= 0 && id < values.length ? values[id] : ISKANDER;
	}
}
