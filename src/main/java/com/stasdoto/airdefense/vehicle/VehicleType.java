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
	ZU23("zu23", GenGeometry.ZU23, null, DefenseType.ZU23, 150, 0.9f, 0.016f, 32, 0, 5.0f, 8.0f, 2.5f, 2.9f),
	// Armour (stage R5): tanks, infantry fighting vehicles, carriers, armoured cars; then boats.
	T72("t72", GenGeometry.T72, Weapon.CANNON_125, 0.3f, false, 700, 0.85f, 0.012f, 0, 2.2f, 2.0f, 3.0f, 3.4f, 2.4f),
	T90("t90", GenGeometry.T90, Weapon.CANNON_125, 0.27f, false, 760, 0.85f, 0.012f, 0, 2.2f, 2.0f, 3.2f, 3.4f, 2.4f),
	LEOPARD2("leopard2", GenGeometry.LEOPARD2, Weapon.CANNON_120, 0.26f, false, 780, 0.95f, 0.013f, 0, 2.3f, 2.2f, 3.6f, 3.4f, 2.6f),
	ABRAMS("abrams", GenGeometry.ABRAMS, Weapon.CANNON_120, 0.25f, false, 820, 0.95f, 0.014f, 0, 2.3f, 2.2f, 3.6f, 3.4f, 2.5f),
	BMP2("bmp2", GenGeometry.BMP2, Weapon.AUTO_30, 0.55f, false, 320, 0.95f, 0.016f, 0, 3.0f, 4.0f, 5.0f, 3.1f, 2.3f),
	BRADLEY("bradley", GenGeometry.BRADLEY, Weapon.AUTO_25, 0.5f, false, 360, 0.9f, 0.015f, 0, 2.8f, 4.0f, 5.0f, 3.3f, 2.9f),
	BTR82("btr82", GenGeometry.BTR82, Weapon.AUTO_30, 0.65f, false, 260, 1.15f, 0.02f, 30, 0, 4.0f, 5.0f, 2.9f, 2.4f),
	BTR4("btr4", GenGeometry.BTR4, Weapon.AUTO_30, 0.62f, false, 280, 1.1f, 0.02f, 30, 0, 4.0f, 5.0f, 2.9f, 2.6f),
	M113("m113", GenGeometry.M113, Weapon.HMG_127, 0.7f, false, 220, 0.95f, 0.018f, 0, 3.0f, 6.0f, 8.0f, 2.69f, 2.3f),
	MAXXPRO("maxxpro", GenGeometry.MAXXPRO, Weapon.HMG_127, 0.72f, false, 240, 1.1f, 0.02f, 32, 0, 6.0f, 8.0f, 2.6f, 3.0f),
	KOZAK("kozak", GenGeometry.KOZAK, Weapon.HMG_127, 0.75f, false, 200, 1.2f, 0.024f, 34, 0, 6.0f, 8.0f, 2.5f, 2.6f),
	GYURZA("gyurza", GenGeometry.GYURZA, Weapon.AUTO_30, 0.7f, true, 300, 1.3f, 0.012f, 0, 1.6f, 4.0f, 5.0f, 4.8f, 3.5f),
	RAPTOR("raptor", GenGeometry.RAPTOR, Weapon.HMG_145, 0.8f, true, 220, 1.6f, 0.016f, 0, 2.0f, 6.0f, 8.0f, 4.1f, 3.0f),
	RHIB("rhib", GenGeometry.RHIB, Weapon.HMG_127, 1.0f, true, 90, 1.5f, 0.02f, 0, 2.6f, 6.0f, 10.0f, 2.6f, 1.2f),
	// Aircraft (stage R6): turn = degrees per tick.
	MI8("mi8", GenGeometry.MI8, 1, null, Ordnance.S8, 0.8f, 300, 1.8f, 0.03f, 2.5f, 3.0f, 3.4f),
	MI24("mi24", GenGeometry.MI24, 1, Weapon.HMG_127, Ordnance.S8, 0.65f, 380, 2.1f, 0.035f, 3.0f, 2.6f, 3.4f),
	KA52("ka52", GenGeometry.KA52, 1, Weapon.AUTO_30, Ordnance.S8, 0.65f, 360, 2.2f, 0.035f, 3.2f, 2.6f, 3.4f),
	SU25("su25", GenGeometry.SU25, 2, Weapon.AUTO_30, Ordnance.FAB250, 0.6f, 420, 3.6f, 0.03f, 2.4f, 3.0f, 3.0f),
	F16("f16", GenGeometry.F16, 2, Weapon.AUTO_25, Ordnance.AIM9, 0.75f, 380, 4.2f, 0.035f, 3.0f, 3.0f, 3.0f),
	// Logistics (stage R7): what they carry, and how much.
	FUEL_TRUCK("fuel_truck", GenGeometry.FUEL_TRUCK, 1, 5000, 160, 0.85f, 0.013f, 30, 2.5f, 3.2f),
	SUPPLY_TRUCK("supply_truck", GenGeometry.SUPPLY_TRUCK, 2, 400, 160, 0.85f, 0.013f, 30, 2.5f, 3.2f),
	// 1.24: Israel's Iron Dome - the launcher of twenty Tamirs and its EL/M-2084 radar.
	IRON_DOME("iron_dome", GenGeometry.IRON_DOME, null, DefenseType.IRON_DOME, 220, 0.9f, 0.014f, 32, 0, 1.6f, 0, 2.55f, 3.3f),
	ELM2084("elm2084", GenGeometry.ELM2084, RadarType.ELM2084, 200, 0.82f, 0.012f, 30, 0, 0, 2.55f, 3.3f),
	// 1.30: artillery - the 2S19 Msta-S and M109A6 Paladin self-propelled howitzers, the BM-21 Grad rocket launcher, and
	// the counter-battery radars that find the enemy's guns (Zoopark-1M on an MT-LBu, AN/TPQ-36 on a Humvee's trailer).
	MSTA_S("msta_s", GenGeometry.MSTA_S, LauncherType.MSTA_S, 0.55f, 420, 0.85f, 0.012f, 0, 2.2f, 1.0f, 2.4f, 3.4f, 3.0f),
	M109("m109", GenGeometry.M109, LauncherType.M109, 0.55f, 390, 0.85f, 0.013f, 0, 2.3f, 1.1f, 2.6f, 3.15f, 3.2f),
	BM21("bm21", GenGeometry.BM21, LauncherType.BM21, null, 170, 0.92f, 0.015f, 32, 0, 1.4f, 3.0f, 2.4f, 3.1f),
	ZOOPARK("zoopark", GenGeometry.ZOOPARK, RadarType.ZOOPARK, 240, 0.85f, 0.014f, 0, 2.6f, 0.9f, 2.85f, 2.6f),
	TPQ36("tpq36", GenGeometry.TPQ36, RadarType.TPQ36, 130, 0.95f, 0.016f, 32, 0, 0.9f, 2.2f, 2.4f),
	// 1.31: more armour - the T-80BVM (gas turbine: the fastest tank), Challenger 2 (the heaviest armour), BMP-3, CV9030,
	// the Stryker, the Tigr-M and the up-armoured Humvee; the BREM-1 and M88 recovery vehicles that mend the others; the
	// TOS-1A heavy flamethrower system.
	T80BVM("t80bvm", GenGeometry.T80BVM, Weapon.CANNON_125, 0.27f, false, 740, 1.02f, 0.017f, 0, 2.5f, 2.0f, 3.3f, 3.4f, 2.3f),
	CHALLENGER2("challenger2", GenGeometry.CHALLENGER2, Weapon.CANNON_120, 0.21f, false, 880, 0.82f, 0.011f, 0, 2.1f, 2.0f, 3.0f, 3.4f, 2.5f),
	BMP3("bmp3", GenGeometry.BMP3, Weapon.AUTO_30, 0.52f, false, 340, 1.0f, 0.016f, 0, 3.0f, 4.0f, 5.0f, 3.1f, 2.4f),
	CV90("cv90", GenGeometry.CV90, Weapon.AUTO_30, 0.45f, false, 380, 0.98f, 0.016f, 0, 2.9f, 4.0f, 5.0f, 3.15f, 2.7f),
	STRYKER("stryker", GenGeometry.STRYKER, Weapon.HMG_127, 0.62f, false, 280, 1.15f, 0.02f, 30, 0, 6.0f, 8.0f, 2.7f, 2.6f),
	TIGR("tigr", GenGeometry.TIGR, Weapon.HMG_127, 0.75f, false, 200, 1.3f, 0.025f, 34, 0, 6.0f, 8.0f, 2.4f, 2.4f),
	HMMWV("hmmwv", GenGeometry.HMMWV, Weapon.HMG_127, 0.8f, false, 170, 1.35f, 0.026f, 35, 0, 6.0f, 8.0f, 2.2f, 2.0f),
	BREM1("brem1", GenGeometry.BREM1, Weapon.HMG_127, 0.33f, false, 620, 0.85f, 0.012f, 0, 2.2f, 6.0f, 8.0f, 3.4f, 2.4f),
	M88("m88", GenGeometry.M88, Weapon.HMG_127, 0.33f, false, 640, 0.8f, 0.012f, 0, 2.0f, 6.0f, 8.0f, 3.4f, 2.8f),
	TOS1("tos1", GenGeometry.TOS1, LauncherType.TOS1, 0.33f, 640, 0.85f, 0.012f, 0, 2.2f, 1.0f, 2.0f, 3.4f, 2.4f),
	// 1.32: the western side's attack aircraft - the AH-64 Apache and the A-10 Thunderbolt II.
	AH64("ah64", GenGeometry.AH64, 1, Weapon.AUTO_30, Ordnance.S8, 0.6f, 380, 2.2f, 0.035f, 3.2f, 2.2f, 3.4f),
	A10("a10", GenGeometry.A10, 2, Weapon.AUTO_30, Ordnance.FAB250, 0.5f, 480, 3.2f, 0.028f, 2.2f, 3.0f, 3.0f),
	// 1.33: the navy - the Buyan-M small missile ship and the Visby corvette (a gun, eight cruise missiles in vertical
	// cells, a close-in gun of their own against drones and missiles); the coastal anti-ship launchers Bastion-P and NMESIS.
	BUYAN_M("buyan_m", GenGeometry.BUYAN_M, Weapon.NAVAL_100, LauncherType.KALIBR_SHIP,
			new ShipFit(0, 4.1, -8.0, 4, 2, 0.75, 0, 7.05, -16.0, 90, 2), 0.35f, 1600, 1.25f, 0.006f, 0.9f, 1.2f, 3.0f, 11f, 8f),
	VISBY("visby", GenGeometry.VISBY, Weapon.NAVAL_57, LauncherType.RBS15_SHIP,
			new ShipFit(0, 3.5, -6.0, 4, 2, 1.1, 0, 5.6, 28.5, 80, 3), 0.4f, 1300, 1.45f, 0.007f, 1.0f, 1.6f, 4.0f, 10.4f, 7f),
	BASTION("bastion", GenGeometry.BASTION, LauncherType.BASTION, null, 300, 0.8f, 0.011f, 28, 0, 0.6f, 0, 3.1f, 3.9f),
	NMESIS("nmesis", GenGeometry.NMESIS, LauncherType.NMESIS, null, 180, 1.1f, 0.02f, 34, 0, 1.0f, 0, 2.5f, 2.4f);

	public static final int HELI = 1;
	public static final int PLANE = 2;

	public final String id;
	public final VehicleGeometry.Geometry geometry;
	@Nullable
	public final LauncherType launcher;
	@Nullable
	public final DefenseType defense;
	@Nullable
	public final RadarType radar;
	/** Tanks, fighting vehicles, boats: the gun the gunner aims and fires. */
	@Nullable
	public final Weapon weapon;
	/** Share of the damage that gets through (tanks shrug off most of it). */
	public final float armor;
	/** Floats and drives on water. */
	public final boolean boat;
	/** 0 = on the ground, {@link #HELI} or {@link #PLANE}. */
	public final int air;
	@Nullable
	public final Ordnance ordnance;
	/** Trucks: 1 = fuel (litres), 2 = ammunition (points); and how much they take. */
	public final int cargo;
	public final int cargoCapacity;
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
	/** 1.33: a warship's fit (vertical launch cells, close-in gun); null for everything else. */
	@Nullable
	public final ShipFit ship;

	/**
	 * 1.33: a warship's fit, in vehicle space (metres, y up from the waterline, z forward): the block of vertical launch
	 * cells (centre of its top, columns x rows, the pitch of the cells), the close-in gun's mount, its reach (blocks) and
	 * the ticks between its bursts.
	 */
	public record ShipFit(double vlsX, double vlsY, double vlsZ, int cols, int rows, double pitch, double ciwsX, double ciwsY,
			double ciwsZ, double ciwsRange, int ciwsRate) {
		public int cells() {
			return cols * rows;
		}

		/** Where a missile leaves cell {@code i}: {x, y, z}. */
		public double[] cell(int i) {
			int c = i % cols;
			int r = (i / cols) % rows;
			return new double[]{vlsX - cols * pitch / 2 + pitch * (c + 0.5), vlsY + 0.6, vlsZ - rows * pitch / 2 + pitch * (r + 0.5)};
		}
	}

	VehicleType(String id, VehicleGeometry.Geometry geometry, @Nullable LauncherType launcher, @Nullable DefenseType defense,
			float maxHealth, float maxSpeed, float accel, float maxSteer, float pivotTurn, float elevationRate, float turretRate,
			float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = launcher;
		this.defense = defense;
		this.radar = null;
		this.weapon = null;
		this.armor = 1.0f;
		this.boat = false;
		this.air = 0;
		this.ordnance = null;
		this.cargo = 0;
		this.cargoCapacity = 0;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = maxSteer;
		this.pivotTurn = pivotTurn;
		this.elevationRate = elevationRate;
		this.turretRate = turretRate;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.ship = null;
	}

	/** 1.30: an armoured self-propelled gun (fires like a launcher, armoured like a fighting vehicle). */
	VehicleType(String id, VehicleGeometry.Geometry geometry, LauncherType launcher, float armor, float maxHealth, float maxSpeed, float accel,
			float maxSteer, float pivotTurn, float elevationRate, float turretRate, float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = launcher;
		this.defense = null;
		this.radar = null;
		this.weapon = null;
		this.armor = armor;
		this.boat = false;
		this.air = 0;
		this.ordnance = null;
		this.cargo = 0;
		this.cargoCapacity = 0;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = maxSteer;
		this.pivotTurn = pivotTurn;
		this.elevationRate = elevationRate;
		this.turretRate = turretRate;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.ship = null;
	}

	/** A radar station: drives like a truck, sees what flies. */
	VehicleType(String id, VehicleGeometry.Geometry geometry, RadarType radar, float maxHealth, float maxSpeed, float accel, float maxSteer,
			float pivotTurn, float elevationRate, float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = null;
		this.defense = null;
		this.radar = radar;
		this.weapon = null;
		this.armor = 1.0f;
		this.boat = false;
		this.air = 0;
		this.ordnance = null;
		this.cargo = 0;
		this.cargoCapacity = 0;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = maxSteer;
		this.pivotTurn = pivotTurn;
		this.elevationRate = elevationRate;
		this.turretRate = 0;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.ship = null;
	}

	/** Armour or a boat with a gun. */
	VehicleType(String id, VehicleGeometry.Geometry geometry, Weapon weapon, float armor, boolean boat, float maxHealth, float maxSpeed,
			float accel, float maxSteer, float pivotTurn, float elevationRate, float turretRate, float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = null;
		this.defense = null;
		this.radar = null;
		this.weapon = weapon;
		this.armor = armor;
		this.boat = boat;
		this.air = 0;
		this.ordnance = null;
		this.cargo = 0;
		this.cargoCapacity = 0;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = maxSteer;
		this.pivotTurn = pivotTurn;
		this.elevationRate = elevationRate;
		this.turretRate = turretRate;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.ship = null;
	}

	/** A helicopter or a plane. */
	VehicleType(String id, VehicleGeometry.Geometry geometry, int air, @Nullable Weapon weapon, Ordnance ordnance, float armor, float maxHealth,
			float maxSpeed, float accel, float turn, float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = null;
		this.defense = null;
		this.radar = null;
		this.weapon = weapon;
		this.armor = armor;
		this.boat = false;
		this.air = air;
		this.ordnance = ordnance;
		this.cargo = 0;
		this.cargoCapacity = 0;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = 0;
		this.pivotTurn = turn;
		this.elevationRate = 0;
		this.turretRate = 0;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.ship = null;
	}

	/** A logistics truck. */
	VehicleType(String id, VehicleGeometry.Geometry geometry, int cargo, int cargoCapacity, float maxHealth, float maxSpeed, float accel,
			float maxSteer, float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = null;
		this.defense = null;
		this.radar = null;
		this.weapon = null;
		this.armor = 1.0f;
		this.boat = false;
		this.air = 0;
		this.ordnance = null;
		this.cargo = cargo;
		this.cargoCapacity = cargoCapacity;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = maxSteer;
		this.pivotTurn = 0;
		this.elevationRate = 0;
		this.turretRate = 0;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.ship = null;
	}

	/** 1.33: a warship - its gun (gunner), cruise missiles in vertical cells (strikes from the map), a close-in gun. */
	VehicleType(String id, VehicleGeometry.Geometry geometry, Weapon weapon, LauncherType launcher, ShipFit ship, float armor, float maxHealth,
			float maxSpeed, float accel, float pivotTurn, float elevationRate, float turretRate, float boxWidth, float boxHeight) {
		this.id = id;
		this.geometry = geometry;
		this.launcher = launcher;
		this.defense = null;
		this.radar = null;
		this.weapon = weapon;
		this.armor = armor;
		this.boat = true;
		this.air = 0;
		this.ordnance = null;
		this.cargo = 0;
		this.cargoCapacity = 0;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
		this.accel = accel;
		this.maxSteer = 0;
		this.pivotTurn = pivotTurn;
		this.elevationRate = elevationRate;
		this.turretRate = turretRate;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.ship = ship;
	}

	/** 1.33: a warship. */
	public boolean isShip() {
		return ship != null;
	}

	/** 1.33: a coastal anti-ship missile launcher. */
	public boolean isCoastal() {
		return this == BASTION || this == NMESIS;
	}

	public boolean isTruck() {
		return cargo != 0;
	}

	public boolean isAir() {
		return air != 0;
	}

	public boolean isArmed() {
		return weapon != null;
	}

	/** Fuel tank in litres. */
	public int fuelCapacity() {
		if (air != 0) {
			return 1500;
		}
		if (ship != null) {
			return 6000;
		}
		if (boat) {
			return 800;
		}
		if (weapon != null && weapon.cannon()) {
			return 1200;
		}
		if (tracked()) {
			return 600;
		}
		return (int) (200 + geometry.length() * 25);
	}

	public boolean isRadar() {
		return radar != null;
	}

	/** Vehicles with a fire control mode (air defence), an on/off switch (radars) or counter-battery fire (artillery). */
	public boolean hasMode() {
		return defense != null || radar != null || isArtillery();
	}

	/** 1.31: a recovery vehicle - it mends its side's vehicles round it while it stands. */
	public boolean repairs() {
		return this == BREM1 || this == M88;
	}

	/** 1.30: a howitzer or a rocket artillery launcher. */
	public boolean isArtillery() {
		return launcher != null && launcher.artillery();
	}

	/** A counter-battery radar. */
	public boolean isCounterBattery() {
		return radar != null && radar.counterBattery;
	}

	/** What a launcher holds when full: rounds for artillery, else one missile per rail. */
	public int strikeLoad() {
		return isArtillery() ? launcher.rounds : ship != null ? ship.cells() : rails();
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
			case GEPARD, SHILKA, TUNGUSKA, TOR, BUK, STRELA10, MFG, ZU23, AVENGER, MSTA_S, M109, TOS1 -> true;
			default -> false;
		};
	}

	/** Missiles carried: launchers one per rail; air defence per its magazine (Patriot canisters hold several). */
	public int magazine() {
		return defense != null ? defense.magazine : radar != null ? 0 : ship != null ? strikeLoad() : weapon != null ? weapon.magazine : strikeLoad();
	}

	/** By its id ("patriot"); Patriot if there is no such vehicle. */
	public static VehicleType byName(String id) {
		for (VehicleType t : values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return PATRIOT;
	}

	public static VehicleType byId(int ordinal) {
		VehicleType[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ISKANDER;
	}
}
