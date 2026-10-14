package com.stasdoto.airdefense.vehicle;

/**
 * 2.0: an engine and its gearbox, worked out from how fast the vehicle goes - the revs, how hard the engine pulls
 * (throttle / load) and the gear, for the engine sounds. A simple automatic gearbox: it changes up when the revs
 * climb past a point that rises with the throttle, down when they sag or the driver floors it (kick-down); while it
 * changes, the throttle is cut for a moment and the revs fall to the new gear. Pure arithmetic (no Minecraft), so the
 * town's traffic uses it too.
 *
 * <p>This is a stand-in: when the vehicle physics keeps (and syncs) its own engine state, VehicleEntity's
 * getEngineRpm / getThrottle / getGear return that instead and this is only used for the town's traffic.
 */
public final class EngineSim {
	/**
	 * Kinds of engine and their gearboxes. rpm: for piston engines revolutions a minute; for turbines, jets, rotors
	 * and electric motors the share of full speed in percent (60 = 60 %).
	 */
	public enum Kind {
		/** A car's petrol four (and outboard motors). */
		PETROL(850, 6200, new float[]{3.6f, 2.15f, 1.45f, 1.08f, 0.84f}, 5),
		/** A truck's / bus's turbo diesel six. */
		DIESEL(650, 2400, new float[]{5.0f, 3.4f, 2.4f, 1.7f, 1.25f, 1.0f}, 9),
		/** A tank's V12 diesel (and fighting vehicles, warships' diesels). */
		V12(620, 2100, new float[]{4.2f, 2.8f, 1.95f, 1.4f, 1.0f}, 10),
		/** A tank's gas turbine (T-80, Abrams): the spool's speed in percent; the gearbox still changes (clunks). */
		TURBINE(60, 100, new float[]{3.0f, 1.9f, 1.3f, 1.0f}, 10),
		/** Trolleybus and tram motors: speed in percent, no gears. */
		ELECTRIC(0, 100, new float[]{1f}, 0),
		/** A helicopter's rotor (percent of its working speed). */
		ROTOR(0, 100, new float[]{1f}, 0),
		/** A jet's engine (percent). */
		JET(0, 100, new float[]{1f}, 0);

		public final float idle;
		public final float max;
		final float[] ratios;
		final int shiftTicks;

		Kind(float idle, float max, float[] ratios, int shiftTicks) {
			this.idle = idle;
			this.max = max;
			this.ratios = ratios;
			this.shiftTicks = shiftTicks;
		}

		public boolean piston() {
			return this == PETROL || this == DIESEL || this == V12;
		}

		public int gears() {
			return ratios.length;
		}
	}

	/** Which engine a vehicle has. */
	public static Kind kindOf(VehicleType t) {
		if (t.air == VehicleType.HELI) {
			return Kind.ROTOR;
		}
		if (t.air == VehicleType.PLANE) {
			return Kind.JET;
		}
		if (t == VehicleType.ABRAMS || t == VehicleType.T80BVM) {
			return Kind.TURBINE;
		}
		if (t.isShip()) {
			return Kind.V12;
		}
		if (t == VehicleType.RHIB || t == VehicleType.POLICE_CAR || t == VehicleType.AMBULANCE) {
			return Kind.PETROL;
		}
		return t.tracked() ? Kind.V12 : Kind.DIESEL;
	}

	public float rpm;
	/** 0..1: how hard the engine pulls. */
	public float throttle;
	/** 0 neutral / engine off, 1.. forward gears, -1 reverse. */
	public int gear;
	/** Counts every gear change (the sounds play a clunk when it moves on). */
	public int shifts;
	/** Smoothed speed along the vehicle's heading (blocks a tick, negative backwards). */
	public float speed;
	private float speedLast;
	private float accel;
	private int shiftTimer;
	/** The throttle the driver asks for (shift points follow this, not the cut during a change). */
	private float pedal;
	private boolean started;

	/**
	 * One tick. {@code along}: the speed along the heading (blocks a tick); {@code maxSpeed}: the vehicle's top speed;
	 * {@code accelRef}: its acceleration (blocks a tick per tick); {@code throttleIn}: the driver's throttle when known
	 * (0..1, NaN when only the motion is known); {@code running}: whether the engine runs; {@code spool}: for aircraft,
	 * how far the engines have spun up (0..1); {@code marine}: a boat (propeller: no gearbox).
	 */
	public void step(Kind kind, double along, double maxSpeed, double accelRef, float throttleIn, boolean running, float spool, boolean marine) {
		float v = (float) along;
		if (!started) {
			speed = speedLast = v;
			started = true;
		}
		speed += (v - speed) * 0.35f;
		accel += ((speed - speedLast) - accel) * 0.3f;
		speedLast = speed;
		float vmax = (float) Math.max(0.05, maxSpeed);
		float share = Math.min(1.3f, Math.abs(speed) / vmax);

		// How hard it pulls: the driver's pedal, or guessed from the motion (speeding up, holding a speed, slowing).
		float want;
		if (!running) {
			want = 0;
		} else if (!Float.isNaN(throttleIn)) {
			want = throttleIn;
		} else {
			float a = (float) (accel / Math.max(1e-4, accelRef));
			if (Math.abs(speed) < 0.01f && Math.abs(a) < 0.2f) {
				want = 0;
			} else if (a > 0.15f) {
				want = Math.min(1, 0.35f + 0.65f * a);
			} else if (a < -0.35f) {
				want = 0;
			} else {
				// Holding a speed: rolling and air resistance (rises with speed).
				want = 0.15f + 0.5f * share * share;
			}
		}
		pedal += (want - pedal) * 0.3f;
		if (shiftTimer > 0) {
			shiftTimer--;
			want *= 0.1f;
		}
		throttle += (want - throttle) * (want > throttle ? 0.35f : 0.5f);

		switch (kind) {
			case ROTOR -> {
				rpm = 100 * spool;
				gear = spool > 0.05f ? 1 : 0;
			}
			case JET -> {
				float n = 60 + 40 * Math.max(throttle, Math.min(1, share));
				rpm = spool * n;
				gear = spool > 0.05f ? 1 : 0;
			}
			case ELECTRIC -> {
				rpm += (Math.min(110, share * 100) - rpm) * 0.4f;
				gear = speed < -0.005f ? -1 : 1;
			}
			default -> {
				if (!running) {
					rpm = Math.max(0, rpm - Math.max(kind.max * 0.05f, rpm * 0.12f));
					gear = 0;
					return;
				}
				float gearRpm = gearbox(kind, share, marine);
				if (kind == Kind.TURBINE) {
					// The spool runs with the throttle; it takes a moment to wind up and down.
					float n = kind.idle + (kind.max - kind.idle) * Math.max(throttle, 0.25f * share);
					rpm += clamp(n - rpm, -1.6f, 2.2f);
					if (rpm < kind.idle * 0.9f) {
						rpm = kind.idle * 0.9f;
					}
				} else {
					rpm += (gearRpm - rpm) * (shiftTimer > 0 ? 0.3f : 0.4f);
				}
			}
		}
	}

	/** The gearbox: picks the gear, returns the engine's revs in it. */
	private int gearbox(Kind kind, float share, boolean marine) {
		float max = kind.piston() ? kind.max : 3000;
		float idle = kind.piston() ? kind.idle : 700;
		float top = max * 0.88f;
		int n = kind.gears();
		if (marine) {
			// A propeller: the revs follow the throttle, the boat follows the revs.
			gear = speed < -0.005f ? -1 : 1;
			return (int) Math.max(idle + (max * 0.9f - idle) * throttle, idle + share * (top - idle));
		}
		if (speed < -0.008f) {
			if (gear != -1) {
				gear = -1;
				shifts++;
				shiftTimer = kind.shiftTicks;
			}
		} else if (gear <= 0) {
			gear = 1;
		}
		if (gear > 0) {
			float up = max * (0.42f + 0.46f * pedal);
			float down = Math.max(idle * 1.15f, max * (0.2f + 0.25f * pedal));
			if (shiftTimer == 0) {
				if (gear < n && inGear(kind, gear, share, top) > up && inGear(kind, gear + 1, share, top) > down * 1.05f) {
					gear++;
					shifts++;
					shiftTimer = kind.shiftTicks;
				} else if (gear > 1 && (inGear(kind, gear, share, top) < down
						|| pedal > 0.92f && inGear(kind, gear - 1, share, top) < max * 0.72f && inGear(kind, gear, share, top) < max * 0.5f)) {
					gear--;
					shifts++;
					shiftTimer = kind.shiftTicks;
				}
			}
		}
		float r = inGear(kind, Math.abs(gear), share, top);
		// Pulling away: the clutch / torque converter lets the revs rise ahead of the wheels.
		float slip = idle + (max * (gear == 1 || gear == -1 ? 0.42f : 0.25f) - idle) * throttle;
		r = Math.max(r, Math.max(idle, gear == 1 || gear == -1 ? slip : idle));
		if (kind == Kind.TURBINE) {
			return (int) r;
		}
		return (int) Math.min(max * 1.02f, r);
	}

	private static float inGear(Kind kind, int g, float share, float top) {
		float[] r = kind.ratios;
		int i = Math.max(0, Math.min(r.length - 1, g - 1));
		return share * top * r[i] / r[r.length - 1];
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : Math.min(hi, v);
	}
}
