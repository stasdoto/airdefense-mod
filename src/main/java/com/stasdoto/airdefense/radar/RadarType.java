package com.stasdoto.airdefense.radar;

/**
 * The radar stations. Range in blocks; {@code minAltitude} = how low (above the ground under it) a target may fly and
 * still be seen (lower ones hide below the radar horizon); {@code sector} = degrees it watches (360 for rotating
 * antennas, the Patriot's fixed array watches 120 degrees); {@code facing} = where that sector points, degrees from the
 * vehicle's nose; {@code spin} = antenna turn per tick (radians, for the looks); {@code discrimination} = how much it
 * helps the air defence in its cover tell decoys from real missiles and drones.
 */
public enum RadarType {
	//        range  minAlt sector facing spin   discr  rpm
	P18(320, 16, 360, 0, 0.0314f, 0.0, 6),
	ST68(280, 5, 360, 0, 0.0628f, 0.15, 12),
	TRML4D(250, 2, 360, 0, 0.3141f, 0.3, 60),
	SENTINEL(200, 2, 360, 0, 0.1571f, 0.2, 30),
	MPQ65(340, 3, 120, 180, 0f, 0.35, 0),
	KUPOL(270, 4, 360, 0, 0.0628f, 0.15, 12),
	// 1.24: Iron Dome's multi-mission radar, a fast-turning AESA.
	ELM2084(300, 2, 360, 0, 0.2094f, 0.3, 40),
	// 1.30: counter-battery radars - a fixed array watching 90 degrees ahead for shells and rockets in flight; from the
	// rising part of their arc they work out where they were fired from. They see nothing else (no aircraft, no drones).
	ZOOPARK(360, 2, 90, 0, 0f, 0.0, 0, true),
	TPQ36(300, 2, 90, 180, 0f, 0.0, 0, true),
	// 1.34: electronic warfare - not radars at all: with the mast up they jam the control links and the satellite
	// navigation of the other side's drones round them (the range is how far). Borisoglebsk-2, Bukovel-AD.
	BORISOGLEBSK(50),
	BUKOVEL(36);

	public final double range;
	public final double minAltitude;
	public final double sector;
	public final float facing;
	public final float spin;
	public final double discrimination;
	public final int rpm;
	/** Watches for artillery (not part of the air picture). */
	public final boolean counterBattery;
	/** 1.34: a jammer (electronic warfare), not a radar: it sees nothing, it jams drones. */
	public final boolean jammer;

	RadarType(double range, double minAltitude, double sector, float facing, float spin, double discrimination, int rpm) {
		this.range = range * com.stasdoto.airdefense.missile.MissileType.RANGE_SCALE;
		this.minAltitude = minAltitude;
		this.sector = sector;
		this.facing = facing;
		this.spin = spin;
		this.discrimination = discrimination;
		this.rpm = rpm;
		this.counterBattery = false;
		this.jammer = false;
	}

	/** 1.34: an electronic warfare station jamming drones within {@code range} (before the range scale). */
	RadarType(double range) {
		this.range = range * com.stasdoto.airdefense.missile.MissileType.RANGE_SCALE;
		this.minAltitude = 0;
		this.sector = 360;
		this.facing = 0;
		this.spin = 0;
		this.discrimination = 0;
		this.rpm = 0;
		this.counterBattery = false;
		this.jammer = true;
	}

	RadarType(double range, double minAltitude, double sector, float facing, float spin, double discrimination, int rpm, boolean counterBattery) {
		this.range = range * com.stasdoto.airdefense.missile.MissileType.RANGE_SCALE;
		this.minAltitude = minAltitude;
		this.sector = sector;
		this.facing = facing;
		this.spin = spin;
		this.discrimination = discrimination;
		this.rpm = rpm;
		this.counterBattery = counterBattery;
		this.jammer = false;
	}

	public boolean rotates() {
		return sector >= 360;
	}
}
