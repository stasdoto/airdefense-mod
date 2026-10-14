package com.stasdoto.airdefense.vehicle;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.vehicle.DriveSpec.Surface;

/**
 * 2.0: the physics of one ground vehicle where it is simulated (the driver's client, or the server when nobody drives):
 * the velocity as a vector in the plane, the yaw rate, tyres that grip up to what the ground allows and then slide
 * (handbrake turns, power slides, understeer when too fast into a bend), tracks that turn on the spot and slip, an
 * engine with its torque curve and an automatic gearbox (and reverse), brakes, rolling resistance and drag, the slope's
 * pull, flying off a crest and landing, crashes into walls and other vehicles.
 *
 * <p>Arcade-realistic: real masses and forces, but tuned to be stable and fun; the old top speeds are kept.
 */
public final class DriveState {
	private static final int SUBSTEPS = 4;
	private static final double DT = 0.05;

	final DriveSpec spec;
	/** Horizontal velocity in the world (blocks per tick). */
	public double vx;
	public double vz;
	/** Yaw rate (rad/s, positive = turning right, as the yaw grows). */
	public double omega;
	/** Pedals as they are pressed now (ramped, 0..1). */
	public float throttle;
	public float brake;
	public boolean handbrake;
	/** -1 reverse, 0 neutral, 1.. forward gears. */
	public int gear;
	private int shiftTimer;
	private int reverseHold;
	public float rpm;
	/** How much the tyres (or tracks) slide, 0..1: for the screech, the smoke, the sounds. */
	public float skid;
	/** Front wheels' angle (degrees, positive = right). */
	public float steer;
	/** Acceleration along and across the body (m/s^2), for the body's pitch and roll and the sounds. */
	public float accelLong;
	public float accelLat;
	public boolean airborne;
	private int airTicks;
	private double lastVy;
	/** The last crash (m/s of speed lost) and what was hit, picked up by the vehicle and reported. */
	public float impact;
	public int impactEntity = -1;
	/** For the tests and the HUD. */
	public Surface surface = Surface.ROAD;
	private int surfaceTimer;
	private boolean raining;
	public boolean cruise;
	public double cruiseSpeed;
	/** Terrain slope under the vehicle (degrees, nose up / left side up). */
	public float slopePitch;
	public float slopeRoll;

	DriveState(VehicleType type) {
		this.spec = DriveSpec.of(type);
		this.rpm = (float) spec.idleRpm;
	}

	/** Forward speed (blocks/tick, negative backing up). */
	public double forwardSpeed(Vec3 f) {
		return vx * f.x + vz * f.z;
	}

	/** The share of the red line the engine is at, 0..1. */
	public float rpmShare() {
		return (float) Mth.clamp((rpm - spec.idleRpm) / (spec.redRpm - spec.idleRpm), 0, 1);
	}

	/** Puts the state in line with how the vehicle is moving now (when it starts to be simulated here). */
	void syncFrom(VehicleEntity v) {
		Vec3 d = v.getDeltaMovement();
		vx = d.x;
		vz = d.z;
	}

	/**
	 * One tick of driving. {@code in}: the driver's keys (or the autopilot's - then {@code ai}: Space is a plain brake);
	 * {@code canMove}: the engine may pull (has fuel, nothing to fold); {@code maxShare}: of the top speed (deep water).
	 */
	void tick(VehicleEntity v, Input in, boolean ai, boolean canMove, boolean engineOn, double maxShare) {
		DriveSpec s = spec;
		Level level = v.level();
		float yawDeg = v.getYRot();
		double yaw = Math.toRadians(yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double u = (vx * fx + vz * fz) * DriveSpec.MS_PER_BT;
		double lat = (vx * rx + vz * rz) * DriveSpec.MS_PER_BT;

		// --- the ground: what it is, how it slopes, are we on it --------------------------------------------------
		if (--surfaceTimer <= 0) {
			surfaceTimer = 5;
			surface = surfaceUnder(v);
			raining = level.isRainingAt(v.blockPosition().above());
		}
		double mu = surface.mu * (raining && surface.mu > 0.5 ? 0.82 : 1.0);
		double roll = surface.roll * s.rollBase;
		if (s.tracked) {
			// Tracks: better on soft ground than wheels, still poor on ice.
			mu = Math.max(mu, surface == Surface.ICE ? 0.22 : 0.6);
			roll = s.rollBase * (0.03 + surface.roll * 0.45);
		}
		double halfLen = v.getVehicleType().geometry.length() * 0.36;
		double halfW = v.getVehicleType().geometry.width() * 0.38;
		double hf = v.groundAt(0, halfLen);
		double hr = v.groundAt(0, -halfLen);
		double hl = v.groundAt(-halfW, 0);
		double hrt = v.groundAt(halfW, 0);
		slopePitch = (float) Mth.clamp(Math.toDegrees(Math.atan2(hf - hr, halfLen * 2)), -40, 40);
		slopeRoll = (float) Mth.clamp(Math.toDegrees(Math.atan2(hl - hrt, halfW * 2)), -30, 30);
		double gap = v.getY() - Math.max(Math.max(hf, hr), Math.max(hl, hrt));
		boolean wasAirborne = airborne;
		airborne = !v.onGround() && gap > 0.45 && !v.isInWater();
		airTicks = airborne ? airTicks + 1 : 0;
		maxAirTicks = Math.max(maxAirTicks, airTicks);
		boolean grounded = !airborne;
		if (v.isInWater()) {
			mu = Math.min(mu, Surface.WATER.mu);
			roll += Surface.WATER.roll;
		}

		// --- pedals and gears --------------------------------------------------------------------------------------
		boolean fwdKey = in.forward() && canMove;
		boolean backKey = in.backward() && canMove;
		boolean hb = in.jump() && !ai;
		boolean aiBrake = in.jump() && ai;
		float wantThrottle = 0;
		float wantBrake = aiBrake ? 1 : 0;
		if (fwdKey || backKey) {
			cruise = false;
		}
		if (fwdKey) {
			if (u < -0.6) {
				wantBrake = 1;
			} else {
				if (gear <= 0) {
					gear = 1;
				}
				wantThrottle = 1;
			}
			reverseHold = 0;
		} else if (backKey) {
			if (u > 0.6 && gear >= 0) {
				wantBrake = 1;
				reverseHold = 0;
			} else if (gear < 0) {
				wantThrottle = 1;
			} else if (++reverseHold > 6 || Math.abs(u) < 0.15 && reverseHold > 2) {
				// Held the brake at a standstill: it engages reverse and backs up.
				gear = -1;
				shiftTimer = s.shiftTicks;
			} else {
				wantBrake = 1;
			}
		} else {
			reverseHold = 0;
			if (cruise && gear > 0 && canMove) {
				double err = cruiseSpeed - u;
				wantThrottle = (float) Mth.clamp(err * 0.6 + 0.25, 0, 1);
				if (err < -1.5) {
					wantBrake = 0.25f;
				}
			}
		}
		if (!engineOn) {
			// Nobody at the wheel (or a dry tank): the parking brake holds it, on a hill too.
			wantThrottle = 0;
			wantBrake = 1;
		}
		// Ramps: the throttle opens over about half a second and closes faster; the brakes bite in a few ticks.
		throttle += Mth.clamp(wantThrottle - throttle, -0.2f, s.car ? 0.11f : 0.08f);
		brake += Mth.clamp(wantBrake - brake, -0.25f, 0.18f);
		handbrake = hb;
		int n = s.gears();
		if (gear == 0 && Math.abs(u) > 0.5 && engineOn) {
			gear = u > 0 ? 1 : -1;
		}
		if (shiftTimer > 0) {
			shiftTimer--;
		} else if (gear > 0) {
			double x = Math.abs(u) / s.gearTop[gear - 1];
			double up = throttle > 0.7f ? 0.92 : 0.55 + 0.35 * throttle;
			if (gear < n && x > up && !airborne) {
				gear++;
				shiftTimer = s.shiftTicks;
			} else if (gear > 1) {
				double xl = Math.abs(u) / s.gearTop[gear - 2];
				boolean kickdown = throttle > 0.9f && x < 0.55 && xl < 0.82;
				if (x < 0.32 || kickdown) {
					gear--;
					shiftTimer = s.shiftTicks / 2;
				}
			}
		}

		// --- engine: revs and pull ---------------------------------------------------------------------------------
		double top = gear > 0 ? s.gearTop[gear - 1] : gear < 0 ? s.reverseTop : s.gearTop[0];
		double xRoad = Math.abs(u) / top;
		double idleShare = s.idleRpm / s.redRpm;
		double xEngine;
		boolean clutchSlip = xRoad < idleShare + 0.18 * throttle;
		if (gear == 0 || shiftTimer > 0 || !engineOn) {
			// Free (neutral, or between gears): the revs follow the throttle, dipping between gears.
			xEngine = engineOn ? idleShare + throttle * (shiftTimer > 0 ? 0.25 : 0.55) : 0;
		} else if (clutchSlip) {
			// Pulling away: the clutch slips (or the converter), the engine sits above idle.
			xEngine = Math.max(xRoad, idleShare + throttle * 0.3);
		} else {
			xEngine = xRoad;
		}
		double drive = 0;
		boolean limiter = xRoad >= 1.0;
		if (engineOn && gear != 0 && shiftTimer == 0 && throttle > 0.01f && !limiter) {
			drive = throttle * s.forceInGear(gear > 0 ? gear - 1 : -1, Math.min(1.0, Math.max(xEngine, 0.25))) * (gear < 0 ? -1 : 1);
		}
		if (limiter && throttle > 0.5f) {
			// Bouncing off the limiter.
			xEngine = 1.0 - (v.tickCount % 3) * 0.02;
		}
		// Traction: more pull than the ground takes spins the wheels (the revs flare, the tyres smoke).
		double weight = s.mass * DriveSpec.G * Math.cos(Math.toRadians(slopePitch));
		double tractionMax = mu * weight * s.tractionShare;
		double spin = 0;
		if (Math.abs(drive) > tractionMax) {
			spin = Math.min(1, (Math.abs(drive) - tractionMax) / tractionMax);
			drive = Math.signum(drive) * tractionMax * (1 - 0.15 * spin);
			xEngine = Math.min(1, xEngine + spin * 0.4);
		}
		if (!grounded) {
			drive = 0;
			xEngine = Math.min(1, xEngine + throttle * 0.3);
		}
		float targetRpm = (float) (Math.max(xEngine, engineOn ? idleShare : 0) * s.redRpm);
		rpm += (targetRpm - rpm) * (shiftTimer > 0 ? 0.25f : 0.4f);

		// --- steering -----------------------------------------------------------------------------------------------
		float turnInput = in.left() ? -1 : in.right() ? 1 : 0;
		if (!s.tracked) {
			// Less lock at speed (as a driver would): the full lock below ~20 km/h, about a third of it flat out.
			double sp = Math.min(1, Math.abs(u) / Math.max(8, s.vmax));
			float lock = v.getVehicleType().maxSteer * (float) (1 - 0.62 * sp * sp);
			float rate = s.car ? 4.0f : 3.0f;
			steer += Mth.clamp(turnInput * lock - steer, -rate, rate);
			if (turnInput == 0 && Math.abs(steer) < 0.3f) {
				steer = 0;
			}
		} else {
			steer = turnInput * 20;
		}

		// --- forces, integrated in a few small steps ------------------------------------------------------------------
		double vxs = vx * DriveSpec.MS_PER_BT;
		double vzs = vz * DriveSpec.MS_PER_BT;
		double w = omega;
		double h = DT / SUBSTEPS;
		double m = s.mass;
		double L = s.front + s.rear;
		double skidAcc = 0;
		double axAcc = 0;
		double ayAcc = 0;
		double delta = Math.toRadians(steer);
		double brakeForce = brake * mu * weight * 0.9;
		// Minecraft's hills are block steps (45 degrees where a real hill would be gentler): the pull of the slope is
		// eased so that tanks and lorries can still crawl up a block-stepped hillside.
		double gSlopeLong = -DriveSpec.G * Math.sin(Math.toRadians(slopePitch)) * 0.5;
		double gSlopeLat = DriveSpec.G * Math.sin(Math.toRadians(slopeRoll)) * 0.6;
		double yawNow = yaw;
		for (int i = 0; i < SUBSTEPS; i++) {
			double cf = -Math.sin(yawNow);
			double cz = Math.cos(yawNow);
			double crx = -Math.cos(yawNow);
			double crz = -Math.sin(yawNow);
			double uu = vxs * cf + vzs * cz;
			double vl = vxs * crx + vzs * crz;
			double fxL = 0;
			double fyL = 0;
			double mz = 0;
			if (grounded) {
				// Weight transfer: the load moves to the rear under acceleration, to the front under braking.
				double transfer = m * Mth.clamp(accelLong, -9, 9) * s.cgHeight / L;
				double nf = Math.max(0.15 * weight, weight * s.rear / L - transfer);
				double nr = Math.max(0.15 * weight, weight * s.front / L + transfer);
				// Rolling resistance, drag, the brakes - each against the motion, never pushing it back the other way.
				double resist = roll * weight + s.dragK * uu * uu + brakeForce;
				if (s.tracked && Math.abs(w) > 0.05) {
					// A skid-steer turn scrubs the tracks sideways: it costs a good deal of the pull.
					resist += Math.abs(w) * m * 1.4 * mu;
				}
				double active = drive + m * gSlopeLong;
				double fLong;
				if (Math.abs(uu) < 0.05) {
					// Standing: held by the brakes / rolling resistance unless pushed harder than that.
					fLong = Math.abs(active) <= resist ? -uu * m / h : active - Math.signum(active) * resist;
				} else {
					fLong = active - Math.signum(uu) * resist;
					double nu = uu + fLong / m * h;
					if (nu * uu < 0 && !(active * uu < 0 && Math.abs(active) > resist)) {
						// The resistance alone stops it; it does not push it back the other way.
						fLong = -uu * m / h;
					}
				}
				if (s.tracked) {
					// Tracks: the yaw rate goes after the steering, as fast as the tracks can drag the hull round.
					double sp = Math.min(1, Math.abs(uu) / s.vmax);
					double want = turnInput * s.pivotRate * (1 - 0.45 * sp) * (uu < -0.3 ? -1 : 1);
					if (!canMove || !engineOn) {
						want = 0;
					}
					double acc = (2.2 + 2.0 * mu) * (Math.abs(want) > Math.abs(w) ? 1 : 1.6);
					w += Mth.clamp(want - w, -acc * h, acc * h);
					// Sideways the tracks hold hard (unless on ice).
					double latMax = mu * weight;
					fyL = -Mth.clamp(vl * m * s.corner, -latMax, latMax);
					fxL = fLong;
					skidAcc += Math.min(1, Math.abs(want) / Math.max(0.2, s.pivotRate)) * (Math.abs(uu) < 2 ? 0.6 : 0.3)
							+ Math.min(1, Math.abs(vl) / 3);
				} else {
					// Tyres (a bicycle model): front with the steering, rear fixed.
					double vfx = uu;
					double vfy = vl + s.front * w;
					double wl = -vfx * Math.sin(delta) + vfy * Math.cos(delta);
					double wlong = vfx * Math.cos(delta) + vfy * Math.sin(delta);
					double alphaF = Math.atan2(wl, Math.max(0.5, Math.abs(wlong)));
					double vry = vl - s.rear * w;
					double alphaR = Math.atan2(vry, Math.max(0.5, Math.abs(uu)));
					double fmaxF = mu * nf;
					double fmaxR = mu * nr;
					// The rear wheels carry the pull (and the handbrake): what they use along, they lose across.
					double rearLong = s.car ? fLong * 0.85 : fLong * 0.5;
					if (handbrake) {
						rearLong = -Math.signum(uu) * 0.8 * fmaxR;
						fLong += rearLong;
						fmaxR *= 0.3;
					}
					double rearLatMax = Math.sqrt(Math.max(fmaxR * fmaxR * 0.04, fmaxR * fmaxR - rearLong * rearLong));
					double fyF = -Mth.clamp(s.corner * nf * alphaF, -fmaxF, fmaxF);
					double fyR = -Mth.clamp(s.corner * nr * alphaR, -rearLatMax, rearLatMax);
					fxL = fLong - fyF * Math.sin(delta);
					fyL = fyF * Math.cos(delta) + fyR;
					mz = s.front * fyF * Math.cos(delta) - s.rear * fyR;
					double slideF = Math.abs(fyF) >= fmaxF * 0.98 ? Math.min(1, Math.abs(wl) / 4) : 0;
					double slideR = Math.abs(fyR) >= rearLatMax * 0.98 ? Math.min(1, Math.abs(vry) / 3) : 0;
					skidAcc += Math.max(slideF * 0.7, slideR) + (handbrake && Math.abs(uu) > 1 ? 0.5 : 0) + spin * 0.8
							+ (brake > 0.9f && mu < 0.5 && Math.abs(uu) > 2 ? 0.4 : 0);
				}
				fyL += m * gSlopeLat;
			} else {
				// In the air: only the drag; it keeps turning as it was, a little damped.
				fxL = -s.dragK * uu * Math.abs(uu) * 0.5;
			}
			double ax = fxL / m;
			double ay = fyL / m;
			vxs += (cf * ax + crx * ay) * h;
			vzs += (cz * ax + crz * ay) * h;
			if (grounded && !s.tracked) {
				w += mz / s.inertia * h;
				// Below walking pace the tyres just roll: the yaw follows the wheels (no slip model at all).
				double uu2 = vxs * cf + vzs * cz;
				double kin = uu2 * Math.tan(delta) / L;
				double blend = Mth.clamp(1 - (Math.abs(uu2) - 1.0) / 3.0, 0, 1);
				if (handbrake && Math.abs(uu2) > 2) {
					blend *= 0.2;
				}
				w += (kin - w) * blend;
				if (blend > 0) {
					double vl2 = vxs * crx + vzs * crz;
					double cut = vl2 * blend * Math.min(1, mu * 1.5);
					vxs -= crx * cut;
					vzs -= crz * cut;
				}
			} else if (!grounded) {
				w *= 0.995;
			}
			axAcc += ax;
			ayAcc += ay;
			yawNow += w * h;
		}
		// Limit of speed (deep water, damaged...).
		double maxMs = s.vmax * maxShare * 1.06;
		double sp2 = Math.hypot(vxs, vzs);
		if (sp2 > maxMs && maxShare < 1) {
			double k = Math.max(maxMs / sp2, 0.9);
			vxs *= k;
			vzs *= k;
		}
		accelLong += ((float) (axAcc / SUBSTEPS) - accelLong) * 0.5f;
		// Across: the centripetal acceleration of the turn (speed times yaw rate) and any sideways push.
		accelLat += ((float) Mth.clamp(u * w + ayAcc / SUBSTEPS * 0.3, -15, 15) - accelLat) * 0.5f;
		omega = w;
		skid += ((float) Math.min(1, skidAcc / SUBSTEPS) - skid) * 0.35f;
		if (Math.abs(omega) < 1e-4 && grounded) {
			omega = 0;
		}
		vx = vxs / DriveSpec.MS_PER_BT;
		vz = vzs / DriveSpec.MS_PER_BT;
		if (Math.abs(vx) < 1e-5 && Math.abs(vz) < 1e-5) {
			vx = vz = 0;
		}
		float newYaw = yawDeg + (float) Math.toDegrees(yawNow - yaw);
		v.setYRot(newYaw);

		// --- vertical: on the ground it follows the slope (a crest throws it), in the air it falls ---------------------
		double vy = v.getDeltaMovement().y;
		double uBt = forwardSpeed(new Vec3(-Math.sin(Math.toRadians(newYaw)), 0, Math.cos(Math.toRadians(newYaw))));
		if (v.isInWater()) {
			vy = Math.max(vy - 0.02, -0.15);
		} else if (!airborne && v.onGround() && vy <= 0.01) {
			double along = uBt * Math.sin(Math.toRadians(slopePitch));
			// Climbing fast up a ramp it keeps that upward speed (and leaves the ground off the top); otherwise the
			// step-up carries it over the blocks and it presses down on the ground.
			vy = along > 0.12 ? Math.min(along, 0.6) : along < 0 ? Math.max(-0.08 + along, -0.8) : -0.08;
		} else {
			vy = Math.max((vy - 0.08) * 0.98, -3.0);
		}
		Vec3 motion = new Vec3(vx, vy, vz);

		// --- moving, and what it hits --------------------------------------------------------------------------------
		impact = 0;
		impactEntity = -1;
		double before = Math.hypot(vx, vz);
		if (Math.abs(uBt) > 1e-3 && v.blockedAt(Math.signum(uBt), Math.abs(uBt))) {
			// The nose (or the tail) against a wall or a tree: a crash.
			impact = (float) (Math.abs(uBt) * DriveSpec.MS_PER_BT);
			double nx = -Math.sin(Math.toRadians(newYaw));
			double nz = Math.cos(Math.toRadians(newYaw));
			double bounce = -0.12 * uBt;
			vx = vx - nx * uBt + nx * bounce;
			vz = vz - nz * uBt + nz * bounce;
			motion = new Vec3(nx * bounce, vy, nz * bounce);
			omega *= 0.5;
		}
		Vec3 p0 = v.position();
		v.setDeltaMovement(motion);
		v.move(MoverType.SELF, motion);
		Vec3 moved = v.position().subtract(p0);
		if (v.horizontalCollision) {
			double mvx = moved.x;
			double mvz = moved.z;
			double lost = Math.hypot(motion.x - mvx, motion.z - mvz);
			if (lost > 0.01) {
				float hit = (float) (lost * DriveSpec.MS_PER_BT);
				if (hit > impact) {
					impact = hit;
				}
				impactEntity = v.entityAhead(motion);
				// What was possible, and a little bounce off the obstacle.
				double bx = (motion.x - mvx) * -0.12;
				double bz = (motion.z - mvz) * -0.12;
				if (impactEntity >= 0) {
					// Another vehicle: both go on together (momentum shared).
					double share = v.massShareAgainst(impactEntity);
					vx = mvx + (motion.x - mvx) * share;
					vz = mvz + (motion.z - mvz) * share;
				} else {
					vx = mvx + bx;
					vz = mvz + bz;
				}
				omega *= 0.7;
			}
		}
		if (wasAirborne && !airborne) {
			// Landing: how hard it came down.
			double fall = -lastVy * DriveSpec.MS_PER_BT;
			if (fall > 9 && fall * 0.5 > impact) {
				impact = (float) (fall * 0.55);
			}
			landing = (float) Math.max(landing, fall);
			hardestLanding = (float) Math.max(hardestLanding, fall);
		}
		lastVy = v.getDeltaMovement().y;
		if (before < 1e-4 && Math.hypot(vx, vz) < 1e-4) {
			vx = vz = 0;
		}
	}

	/** For the tests: the longest time any vehicle was in the air (ticks), the hardest landing (m/s). */
	public static int maxAirTicks;
	public static float hardestLanding;

	/** How hard it last landed (m/s down), for the suspension; cleared by whoever reads it. */
	public float landing;

	// ------------------------------------------------------------------------------------------------
	// The ground

	private static final Map<Block, Surface> SURFACES = new IdentityHashMap<>();

	/** What it drives on: the block under it (or a snow layer / water on top of that). */
	static Surface surfaceUnder(VehicleEntity v) {
		Level level = v.level();
		BlockPos feet = BlockPos.containing(v.getX(), v.getY() + 0.05, v.getZ());
		BlockState at = level.getBlockState(feet);
		if (level.getFluidState(feet).is(FluidTags.WATER)) {
			return Surface.WATER;
		}
		if (!at.isAir()) {
			Surface s = classify(at.getBlock());
			if (s == Surface.SNOW || s == Surface.MUD) {
				return s;
			}
		}
		BlockState below = level.getBlockState(feet.below());
		if (below.isAir()) {
			below = level.getBlockState(feet.below(2));
		}
		return classify(below.getBlock());
	}

	static Surface classify(Block b) {
		Surface s = SURFACES.get(b);
		if (s == null) {
			s = classifyByName(BuiltInRegistries.BLOCK.getKey(b).getPath());
			SURFACES.put(b, s);
		}
		return s;
	}

	static Surface classifyByName(String id) {
		if (id.contains("puddle")) {
			return Surface.WET_ROAD;
		}
		if (id.contains("ice")) {
			return Surface.ICE;
		}
		if (id.contains("snow") || id.contains("powder")) {
			return Surface.SNOW;
		}
		if (id.contains("mud") || id.contains("clay") || id.contains("soul") || id.contains("swamp") || id.equals("farmland")) {
			return Surface.MUD;
		}
		if (id.contains("sand") && !id.contains("sandstone") && !id.contains("paving") || id.contains("gravel") || id.contains("suspicious")) {
			return Surface.SAND;
		}
		if (id.contains("asphalt") || id.contains("road") || id.contains("concrete") || id.contains("parking") || id.contains("tram_track")
				|| id.contains("paving") || id.contains("walk_") || id.contains("kerb") || id.contains("tar") || id.contains("smooth_stone")
				|| id.contains("polished") || id.contains("blackstone") || id.contains("tuff_bricks") || id.startsWith("city_ground")) {
			return Surface.ROAD;
		}
		if (id.contains("grass") || id.contains("moss") || id.contains("leaves") || id.contains("mycelium") || id.contains("podzol")) {
			return Surface.GRASS;
		}
		if (id.contains("dirt") || id.contains("path") || id.contains("rooted") || id.contains("coarse")) {
			return Surface.DIRT;
		}
		if (id.contains("planks") || id.contains("log") || id.contains("wood")) {
			return Surface.WOOD;
		}
		if (id.contains("stone") || id.contains("brick") || id.contains("cobble") || id.contains("andesite") || id.contains("granite")
				|| id.contains("diorite") || id.contains("deepslate") || id.contains("terracotta") || id.contains("ore") || id.contains("tuff")) {
			return Surface.STONE;
		}
		return Surface.DIRT;
	}
}
