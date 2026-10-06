package com.stasdoto.airdefense.vehicle;

import org.jetbrains.annotations.Nullable;

import com.stasdoto.airdefense.defense.DefenseType;
import com.stasdoto.airdefense.launcher.LauncherType;
import com.stasdoto.airdefense.radar.RadarType;

/**
 * The vehicles. Speeds are in blocks per tick (1 block/tick = 72 km/h), angles in degrees, rates per tick.
 * The collision box is only as wide as the vehicle (Minecraft boxes cannot turn), its length is handled separately.
 */
public enum VehicleType {
	ISKANDER("iskander", VehicleGeometry.ISKANDER, LauncherType.ISKANDER, null, 260, 0.82f, 0.011f, 30, 0, 0.75f, 0, 3.0f, 3.3f),
	KALIBR("kalibr", VehicleGeometry.KALIBR, LauncherType.KALIBR, null, 240, 0.82f, 0.011f, 30, 0, 0.9f, 0, 3.0f, 3.3f),
	SHAHED("shahed", VehicleGeometry.SHAHED, LauncherType.SHAHED, null, 150, 0.9f, 0.015f, 34, 0, 0, 0, 2.5f, 3.0f),
	HIMARS("himars", VehicleGeometry.HIMARS, LauncherType.HIMARS, null, 200, 0.95f, 0.018f, 34, 0, 1.2f, 2.5f, 2.4f, 3.1f),
	PATRIOT("patriot", VehicleGeometry.PATRIOT, null, DefenseType.PATRIOT, 220, 0.85f, 0.013f, 30, 0, 1.4f, 4.0f, 2.55f, 3.3f),
	IRIS_T("iris_t", VehicleGeometry.IRIS_T, null, DefenseType.IRIS_T, 210, 0.85f, 0.013f, 30, 0, 2.0f, 0, 2.55f, 3.3f),
	NASAMS("nasams", VehicleGeometry.NASAMS, null, DefenseType.NASAMS, 190, 0.85f, 0.014f, 32, 0, 1.6f, 4.5f, 2.55f, 3.2f),
	GEPARD("gepard", VehicleGeometry.GEPARD, null, DefenseType.GEPARD, 340, 0.9f, 0.016f, 0, 3.0f, 5.0f, 9.0f, 3.4f, 3.0f),
	// Radar stations (stage R1): no weapons, they see.
	P18("p18", GenGeometry.P18, RadarType.P18, 170, 0.8f, 0.012f, 30, 0, 0, 2.5f, 3.2f),
	ST68("st68", GenGeometry.ST68, RadarType.ST68, 190, 0.75f, 0.010f, 28, 0, 0, 2.6f, 3.2f),
	TRML4D("trml4d", GenGeometry.TRML4D, RadarType.TRML4D, 180, 0.88f, 0.013f, 30, 0, 0, 2.55f, 3.2f),
	SENTINEL("sentinel", GenGeometry.SENTINEL, RadarType.SENTINEL, 130, 0.95f, 0.016f, 32, 0, 0, 2.2f, 2.2f),
	MPQ65("mpq65", GenGeometry.MPQ65, RadarType.MPQ65, 220, 0.75f, 0.010f, 28, 0, 0.8f, 2.5f, 3.3f),
	KUPOL("kupol", GenGeometry.KUPOL, RadarType.KUPOL, 260, 0.75f, 0.012f, 0, 2.6f, 0, 3.2f, 3.0f),
	// Air defence of the war in Ukraine (stage R4).
	PANTSIR("pantsir", GenGeometry.PANTSIR, null, DefenseType.PANTSIR, 260, 0.9f, 0.013f, 30, 0, 4.0f, 6.0f, 2.55f, 3.2f),
	TOR("tor", GenGeometry.TOR, null, DefenseType.TOR, 300, 0.8f, 0.014f, 0, 2.6f, 0, 6.0f, 3.3f, 3.0f),
	BUK("buk", GenGeometry.BUK, null, DefenseType.BUK, 280, 0.75f, 0.012f, 0, 2.4f, 1.0f, 3.0f, 3.25f, 3.0f),
	S300("s300", GenGeometry.S300, null, DefenseType.S300, 260, 0.75f, 0.010f, 28, 0, 1.0f, 0, 3.05f, 3.4f),
	OSA("osa", GenGeometry.OSA, null, DefenseType.OSA, 220, 0.85f, 0.013f, 30, 0, 1.5f, 5.0f, 2.75f, 2.9f),
	STRELA10("strela10", GenGeometry.STRELA10, null, DefenseType.STRELA10, 200, 0.85f, 0.016f, 0, 3.0f, 2.0f, 5.0f, 2.85f, 2.4f),
	SHILKA("shilka", GenGeometry.SHILKA, null, DefenseType.SHILKA, 260, 0.8f, 0.015f, 0, 3.0f, 5.0f, 8.0f, 3.1f, 2.6f),
	TUNGUSKA("tunguska", GenGeometry.TUNGUSKA, null, DefenseType.TUNGUSKA, 300, 0.85f, 0.015f, 0, 3.0f, 4.5f, 7.0f, 3.25f, 3.0f),
	SAMPT("sampt", GenGeometry.SAMPT, null, DefenseType.SAMPT, 230, 0.85f, 0.012f, 30, 0, 1.4f, 0, 2.55f, 3.3f),
	AVENGER("avenger", GenGeometry.AVENGER, null, DefenseType.AVENGER, 130, 1.05f, 0.02f, 34, 0, 2.5f, 6.0f, 2.2f, 2.3f),
	MFG("mfg", GenGeometry.MFG, null, DefenseType.MFG, 90, 1.15f, 0.025f, 35, 0, 6.0f, 10.0f, 1.85f, 1.9f),
	ZU23("zu23", GenGeometry.ZU23, null, DefenseType.ZU23, 150, 0.9f, 0.016f, 32, 0, 5.0f, 8.0f, 2.5f, 2.9f);

	public final String id;
	public final VehicleGeometry.Geometry geometry;
	@Nullable
	public final LauncherType launcher;
	@Nullable
	public final DefenseType defense;
	@Nullable
	public final RadarType radar;
	public final float maxHealth;
	public final float maxSpeed;
	public final float accel;
	/** Wheeled: max steering angle of the front wheels. */
	public final float maxSteer;
	/** Tracked: turn rate on the spot. */
	public final float pivotTurn;
	public final float elevationRate;
	public final float turretRate;
	public final float boxWidth;
	public final float boxHeight;

	VehicleType(String id, VehicleGeometry.Geometry geometry, @Nullable LauncherType launcher, @Nullable DefenseType defense,
			float maxHealth, float maxSpeed, float accel, float maxSteer, float pivotTurn, float elevationRate, float turretRate,
			float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = launcher;
		this.defense = defense;
		this.radar = null;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = maxSteer;
		this.pivotTurn = pivotTurn;
		this.elevationRate = elevationRate;
		this.turretRate = turretRate;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
	}

	/** A radar station: drives like a truck, sees what flies. */
	VehicleType(String id, VehicleGeometry.Geometry geometry, RadarType radar, float maxHealth, float maxSpeed, float accel, float maxSteer,
			float pivotTurn, float elevationRate, float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = null;
		this.defense = null;
		this.radar = radar;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = maxSteer;
		this.pivotTurn = pivotTurn;
		this.elevationRate = elevationRate;
		this.turretRate = 0;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
	}

	public boolean isRadar() {
		return radar != null;
	}

	/** Vehicles with a fire control mode (air defence) or an on/off switch (radars). */
	public boolean hasMode() {
		return defense != null || radar != null;
	}

	public boolean isLauncher() {
		return launcher != null;
	}

	public boolean isDefense() {
		return defense != null;
	}

	public boolean tracked() {
		return geometry.tracked();
	}

	public int rails() {
		return geometry.rails().length;
	}

	private int[] missileRails;
	private int[] barrelRails;

	/** Rails that hold missiles (not gun barrels). */
	public int[] missileRails() {
		if (missileRails == null) {
			split();
		}
		return missileRails;
	}

	/** Gun barrels (rails named "barrel..."); for pure gun systems every rail. */
	public int[] barrelRails() {
		if (barrelRails == null) {
			split();
		}
		return barrelRails;
	}

	private void split() {
		VehicleGeometry.Rail[] r = geometry.rails();
		java.util.List<Integer> m = new java.util.ArrayList<>();
		java.util.List<Integer> b = new java.util.ArrayList<>();
		for (int i = 0; i < r.length; i++) {
			(r[i].part().startsWith("barrel") || (defense != null && defense.gunOnly()) ? b : m).add(i);
		}
		missileRails = m.stream().mapToInt(Integer::intValue).toArray();
		barrelRails = b.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Guns only (they can fire on the move, aim straight at the target). */
	public boolean gunOnly() {
		return defense != null && defense.gunOnly();
	}

	/** Guns, alone or with missiles: the mount tracks the target. */
	public boolean hasGuns() {
		return defense != null && defense.hasGuns();
	}

	/** The second seat is in the turret (it turns with it). */
	public boolean gunnerInTurret() {
		return switch (this) {
			case GEPARD, SHILKA, TUNGUSKA, TOR, BUK, STRELA10, MFG, ZU23, AVENGER -> true;
			default -> false;
		};
	}

	/** Missiles carried: launchers one per rail; air defence per its magazine (Patriot canisters hold several). */
	public int magazine() {
		return defense != null ? defense.magazine : radar != null ? 0 : rails();
	}

	public static VehicleType byId(int ordinal) {
		VehicleType[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ISKANDER;
	}
}
