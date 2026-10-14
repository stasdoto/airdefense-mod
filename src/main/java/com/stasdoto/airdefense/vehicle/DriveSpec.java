package com.stasdoto.airdefense.vehicle;

import java.util.EnumMap;
import java.util.Map;

/**
 * 2.0: what a ground vehicle is made of, for its physics - mass, engine (power, torque curve, revs), gearbox, tyres or
 * tracks, brakes, drag; worked out once per type from what the type already says (top speed, acceleration, size, armour)
 * so that the old top speeds stay what they were. SI units inside (kg, m, s, N); the vehicle's own speeds are in blocks
 * per tick (1 block/tick = 20 m/s = 72 km/h).
 */
public final class DriveSpec {
	public static final double G = 9.81;
	/** Ticks per second and metres per block: 1 block/tick = 20 m/s. */
	public static final double MS_PER_BT = 20.0;

	public final VehicleType type;
	public final boolean tracked;
	/** A light car (handbrake turns, lively body). */
	public final boolean car;
	public final double mass;
	/** Yaw moment of inertia (kg m^2). */
	public final double inertia;
	/** Axles: distances of the front and rear axle from the centre of mass (m), track width (m), centre of mass height. */
	public final double front;
	public final double rear;
	public final double trackWidth;
	public final double cgHeight;
	/** Top speed (m/s) - what the old model reached; the rev limiter in top gear holds it. */
	public final double vmax;
	/** Forward gears: the road speed (m/s) each one reaches at the red line. Index 0 = first gear. */
	public final double[] gearTop;
	/** Reverse: its top speed (m/s). */
	public final double reverseTop;
	/** Engine revs: idle, peak torque, red line (rpm, for the sounds and the rev counter). */
	public final double idleRpm;
	public final double redRpm;
	/** Force at the wheels in first gear at peak torque (N); the higher gears get less by their ratio. */
	public final double peakForce1;
	/** Engine power (W). */
	public final double power;
	/** Share of the weight on driven wheels (all of it for tracks and all-wheel-drive lorries). */
	public final double tractionShare;
	/** Quadratic air and internal drag (N per (m/s)^2), sized so top gear just reaches vmax on a good surface. */
	public final double dragK;
	/** Rolling resistance of the running gear on its own (times the surface's). */
	public final double rollBase;
	/** Tyres: cornering stiffness per newton of load (N/rad per N); tracks: how stiffly they resist sliding sideways. */
	public final double corner;
	/** How long a gear change takes (ticks without drive). */
	public final int shiftTicks;
	/** Tracked: turn rate on the spot (rad/s) and how quickly the yaw rate follows the steering (1/s). */
	public final double pivotRate;
	/** Visual: how far the body pitches per m/s^2 of acceleration and rolls per m/s^2 of cornering (degrees). */
	public final float pitchGain;
	public final float rollGain;
	/** Spring stiffness and damping of the body on its suspension (per tick units, see DriveState). */
	public final float spring;
	public final float damping;
	/** A gas turbine (Abrams, T-80): high revs, no gears to speak of in the sound. */
	public final boolean turbine;

	private static final Map<VehicleType, DriveSpec> CACHE = new EnumMap<>(VehicleType.class);

	public static DriveSpec of(VehicleType type) {
		DriveSpec s = CACHE.get(type);
		if (s == null) {
			s = new DriveSpec(type);
			CACHE.put(type, s);
		}
		return s;
	}

	private DriveSpec(VehicleType t) {
		this.type = t;
		VehicleGeometry.Geometry g = t.geometry;
		this.tracked = t.tracked();
		double vol = g.length() * g.width() * Math.max(1.2, g.height());
		boolean tank = tracked && t.weapon != null && t.weapon.cannon();
		double m;
		if (tank) {
			// Main battle tanks: a T-72 about 45 t, an Abrams over 60, a Challenger 75.
			m = 45000 + (t.maxHealth - 700) * 140;
		} else if (tracked) {
			// Other tracked vehicles: a BMP-2 some 14 t, a Gepard or a self-propelled howitzer 40-odd.
			m = Math.max(12000, Math.min(48000, vol * 600));
		} else if (vol < 45) {
			// Cars and vans: a police car 1.6 t, an ambulance 3.5 t, a Humvee or Tigr (armoured) more.
			m = vol * 100;
			if (t.weapon != null) {
				m *= 2.2;
			}
		} else {
			// Lorries and wheeled armour: 15-40 t.
			m = vol * 220;
			if (t.weapon != null && t.armor < 0.8f) {
				m *= 1.3;
			}
		}
		this.mass = clamp(m, 1100, 75000);
		this.car = !tracked && mass < 2600;
		double wb = Math.max(1.8, g.wheelbase() > 0 ? g.wheelbase() : g.length() * 0.62);
		this.front = wb * (tank ? 0.5 : 0.48);
		this.rear = wb - front;
		this.trackWidth = Math.max(1.4, g.width() * 0.85);
		this.cgHeight = Math.max(0.45, g.height() * (tracked ? 0.38 : car ? 0.33 : 0.42));
		this.inertia = mass * (g.length() * g.length() + g.width() * g.width()) / 12.0;
		this.vmax = t.maxSpeed * MS_PER_BT;
		this.turbine = t == VehicleType.ABRAMS || t == VehicleType.T80BVM;
		// Gearboxes: cars five speeds, lorries and wheeled armour six, tracked vehicles four (an automatic of a tank).
		int gears = car ? 5 : tracked ? 4 : 6;
		double first = car ? 0.24 : tracked ? 0.26 : 0.15;
		this.gearTop = new double[gears];
		for (int i = 0; i < gears; i++) {
			gearTop[i] = vmax * Math.pow(first, (gears - 1 - i) / (double) (gears - 1));
		}
		this.reverseTop = Math.max(gearTop[0] * 1.15, vmax * (tracked ? 0.35 : 0.3));
		if (turbine) {
			idleRpm = 1000;
			redRpm = 3000;
		} else if (car) {
			idleRpm = 800;
			redRpm = 6200;
		} else if (tracked) {
			idleRpm = 650;
			redRpm = 2600;
		} else {
			idleRpm = 600;
			redRpm = 2800;
		}
		// Engine power from the old model's pull and top speed (1 b/t^2 = 400 m/s^2): a tank some 16 W/kg, a lorry 14,
		// a car 60-70. The pull at the wheels in a gear is the power over the road speed, shaped by the torque curve.
		this.power = mass * t.accel * 400.0 * vmax * 0.2;
		this.peakForce1 = power / (0.787 * gearTop[0]);
		this.tractionShare = tracked ? 1.0 : car ? 0.58 : 0.75;
		this.rollBase = tracked ? 1.6 : 1.0;
		// Drag sized so that in top gear, near the red line, on a road (wheels) or firm ground (tracks), the pull just
		// beats the resistance at vmax: the rev limiter then holds the old top speed.
		double fTop = forceInGear(gears - 1, 0.94);
		double roll = rollBase * (tracked ? Surface.DIRT.roll : Surface.ROAD.roll) * mass * G;
		double k = (fTop * 0.92 - roll) / (vmax * vmax);
		this.dragK = Math.max(0.02 * mass / 1000.0, k);
		this.corner = tracked ? 30 : car ? 11 : 8;
		this.shiftTicks = car ? 5 : tracked ? 9 : 8;
		this.pivotRate = Math.toRadians(t.pivotTurn * 20.0);
		this.pitchGain = (float) (car ? 0.55 : tracked ? 0.32 : 0.4);
		this.rollGain = (float) (car ? 0.9 : tracked ? 0.35 : 0.55);
		this.spring = car ? 0.22f : tracked ? 0.16f : 0.14f;
		this.damping = car ? 0.30f : tracked ? 0.36f : 0.28f;
	}

	/**
	 * The pull at the wheels (N) in gear {@code gear} (0-based, -1 = reverse) at this share of the red line; the
	 * torque curve of an engine: rises from idle to its peak at about 60 % of the revs, falls off a little to the red line.
	 */
	public double forceInGear(int gear, double revShare) {
		double top = gear < 0 ? reverseTop : gearTop[gear];
		double ratio = gearTop[0] / top;
		return peakForce1 * ratio * torqueCurve(revShare);
	}

	public static double torqueCurve(double x) {
		double d = x - 0.6;
		return Math.max(0.45, 1.0 - 1.4 * d * d);
	}

	public int gears() {
		return gearTop.length;
	}

	static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	/** What the ground under the wheels is like: grip (friction coefficient) and rolling resistance. */
	public enum Surface {
		ROAD(1.0, 0.013),
		WET_ROAD(0.72, 0.015),
		STONE(0.85, 0.018),
		DIRT(0.72, 0.035),
		GRASS(0.66, 0.04),
		SAND(0.55, 0.09),
		MUD(0.45, 0.12),
		SNOW(0.35, 0.06),
		ICE(0.1, 0.012),
		WATER(0.3, 0.25),
		WOOD(0.75, 0.02);

		public final double mu;
		public final double roll;

		Surface(double mu, double roll) {
			this.mu = mu;
			this.roll = roll;
		}
	}
}
