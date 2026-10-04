package com.stasdoto.airdefense.vehicle;

import org.jetbrains.annotations.Nullable;

import com.stasdoto.airdefense.defense.DefenseType;
import com.stasdoto.airdefense.launcher.LauncherType;

/**
 * The eight vehicles. Speeds are in blocks per tick (1 block/tick = 72 km/h), angles in degrees, rates per tick.
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
	GEPARD("gepard", VehicleGeometry.GEPARD, null, DefenseType.GEPARD, 340, 0.9f, 0.016f, 0, 3.0f, 5.0f, 9.0f, 3.4f, 3.0f);

	public final String id;
	public final VehicleGeometry.Geometry geometry;
	@Nullable
	public final LauncherType launcher;
	@Nullable
	public final DefenseType defense;
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

	/** Missiles carried: launchers one per rail; air defence per its magazine (Patriot canisters hold several). */
	public int magazine() {
		return defense != null ? defense.magazine : rails();
	}

	public static VehicleType byId(int ordinal) {
		VehicleType[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ISKANDER;
	}
}
