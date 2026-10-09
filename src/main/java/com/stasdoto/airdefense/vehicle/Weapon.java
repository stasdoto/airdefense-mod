package com.stasdoto.airdefense.vehicle;

/**
 * Direct-fire weapons of tanks, fighting vehicles and boats. {@code reload} = ticks between shots (bursts),
 * {@code burst} = rounds per trigger pull, {@code speed} = blocks per tick of the round, {@code blast} = explosion
 * where it lands (0 = none), damage to vehicles and to people on a direct hit, whether it breaks blocks, rounds
 * carried, how high the gun elevates.
 */
public enum Weapon {
	CANNON_125(140, 1, 26.0, 2.6f, 170f, 40f, true, 22, 15, "125"),
	CANNON_120(120, 1, 28.0, 2.4f, 180f, 40f, true, 20, 18, "120"),
	AUTO_30(24, 3, 14.0, 0.8f, 16f, 10f, false, 45, 60, "30"),
	AUTO_25(20, 3, 15.0, 0.7f, 13f, 9f, false, 50, 55, "25"),
	HMG_145(4, 1, 18.0, 0f, 4f, 9f, false, 120, 70, "14.5"),
	HMG_127(3, 1, 18.0, 0f, 2.5f, 7f, false, 150, 70, "12.7"),
	// 1.33: naval guns - the A-190 100 mm of the Buyan-M, the Bofors 57 mm Mk3 of the Visby (they carry hundreds of rounds:
	// a warship's gun never runs dry).
	NAVAL_100(50, 1, 22.0, 3.4f, 170f, 40f, true, 99, 80, "100"),
	NAVAL_57(16, 1, 24.0, 2.0f, 80f, 24f, true, 99, 77, "57");

	public final int reload;
	public final int burst;
	public final double speed;
	public final float blast;
	public final float vehicleDamage;
	public final float livingDamage;
	public final boolean breaksBlocks;
	public final int magazine;
	public final float maxElevation;
	public final String caliber;

	Weapon(int reload, int burst, double speed, float blast, float vehicleDamage, float livingDamage, boolean breaksBlocks, int magazine,
			float maxElevation, String caliber) {
		this.reload = reload;
		this.burst = burst;
		this.speed = speed;
		this.blast = blast;
		this.vehicleDamage = vehicleDamage;
		this.livingDamage = livingDamage;
		this.breaksBlocks = breaksBlocks;
		this.magazine = magazine;
		this.maxElevation = maxElevation;
		this.caliber = caliber;
	}

	public boolean cannon() {
		return this == CANNON_125 || this == CANNON_120 || naval();
	}

	/** 1.33: a warship's gun. */
	public boolean naval() {
		return this == NAVAL_100 || this == NAVAL_57;
	}

	/** Holding the trigger keeps firing (machine guns). */
	public boolean automatic() {
		return this == HMG_145 || this == HMG_127;
	}
}
