package com.stasdoto.airdefense.client.vehicle;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.client.ClientSettings;
import com.stasdoto.airdefense.client.fx.FxClient;
import com.stasdoto.airdefense.client.fx.FxParticle;
import com.stasdoto.airdefense.registry.ModParticles;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleGeometry;

/**
 * 2.0: what the client sees of a vehicle each tick besides its model. How it drives (braking, reversing, turning: for
 * its lights - see {@link VehicleLights}), and how badly it is hurt: below 60 % of its strength light smoke from the
 * engine, below 35 % thick dark smoke, below 15 % the engine burns, throwing sparks; a wreck burns on with embers and
 * the glow of its fire, then smoulders. Cheap: at most a puff or two a tick, fewer far away, nothing beyond 96 blocks.
 */
public final class VehicleFx {
	private static final class Track {
		float speed;
		int brake;
		int turn;
		int turnDir;
		boolean reverse;
	}

	private static final Map<VehicleEntity, Track> TRACKS = new WeakHashMap<>();
	private static final RandomSource R = RandomSource.create();
	/** For the tests: puffs of light and dark smoke, flames, sparks made. */
	public static final int[] MADE = new int[4];

	private VehicleFx() {
	}

	public static void init() {
		VehicleEntity.clientFx = VehicleFx::tick;
	}

	private static void tick(VehicleEntity v) {
		Track t = TRACKS.computeIfAbsent(v, k -> new Track());
		double dx = v.getX() - v.xo;
		double dz = v.getZ() - v.zo;
		float yaw = v.getYRot() * Mth.DEG_TO_RAD;
		float along = (float) (dx * -Mth.sin(yaw) + dz * Mth.cos(yaw));
		float prev = t.speed;
		t.speed += (along - t.speed) * 0.5f;
		boolean driven = v.lightsOn();
		// Braking: slowing down hard, or standing with somebody at the wheel (the foot on the brake).
		if (Math.abs(prev) > 0.03f && Math.abs(t.speed) < Math.abs(prev) - 0.005f) {
			t.brake = 10;
		} else if (driven && Math.abs(t.speed) < 0.01f && v.getControllingPassenger() != null) {
			t.brake = Math.max(t.brake, 2);
		} else if (t.brake > 0) {
			t.brake--;
		}
		t.reverse = t.speed < -0.02f;
		// Turning hard at speed: the indicator on that side for a while.
		if (Math.abs(v.steerVis) > 12 && Math.abs(t.speed) > 0.02f) {
			t.turn = 24;
			t.turnDir = v.steerVis > 0 ? 1 : -1;
		} else if (t.turn > 0) {
			t.turn--;
		}
		v.brakingSeen = t.brake > 0;
		if (ClientSettings.damageSmoke()) {
			damage(v);
		}
	}

	/** What lights are on (the bits of {@link VehicleLights}) - 0 for aircraft, ships and boats. */
	public static int flags(VehicleEntity v, float dark) {
		if (!v.isAlive() || v.getVehicleType().isAir() || v.getVehicleType().isShip() || v.getVehicleType().boat || !v.lightsOn()) {
			return 0;
		}
		Track t = TRACKS.get(v);
		boolean tracked = v.getVehicleType().tracked();
		int f = VehicleLights.TAIL;
		// Day running lights on the trucks and cars; a tank's lamps only in the dark.
		if (!tracked || dark > 0.3f) {
			f |= VehicleLights.HEAD;
		}
		if (t != null) {
			if (t.brake > 0) {
				f |= VehicleLights.BRAKE;
			}
			if (t.reverse) {
				f |= VehicleLights.REVERSE;
			}
			if (t.turn > 0 && !tracked) {
				// steerVis > 0: the yaw grows - a turn to the right (south-facing, west is on the right).
				f |= t.turnDir > 0 ? VehicleLights.RIGHT : VehicleLights.LEFT;
			}
		}
		return f;
	}

	/** 0 sound .. 1 nearly done for: how rough its engine should sound (the hook for VehicleSounds). */
	public static float roughness(VehicleEntity v) {
		if (!v.isAlive()) {
			return 1;
		}
		float hp = v.getHealth() / Math.max(1, v.getMaxHealth());
		return Mth.clamp((0.6f - hp) / 0.6f, 0, 1);
	}

	// ------------------------------------------------------------------------------------------------
	// Damage

	private static void damage(VehicleEntity v) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(v.level() instanceof ClientLevel level)) {
			return;
		}
		double d2 = v.distanceToSqr(mc.player);
		if (d2 > 96 * 96 || v.getVehicleType().isShip()) {
			return;
		}
		// Far away, half as many.
		if (d2 > 48 * 48 && (v.tickCount & 1) == 0) {
			return;
		}
		VehicleGeometry.Geometry g = v.getVehicleType().geometry;
		boolean tracked = v.getVehicleType().tracked();
		// The engine: in front on wheels, at the back of a tracked hull; on its top deck.
		Vec3 eng = v.toWorld(0, g.height() * (tracked ? 0.52 : 0.6), g.length() * (tracked ? -0.32 : 0.36));
		if (!v.isAlive()) {
			wreck(level, v, g);
			return;
		}
		float hp = v.getHealth() / Math.max(1, v.getMaxHealth());
		int n = v.tickCount;
		if (hp < 0.15f) {
			if (n % 3 == 0) {
				level.addParticle(ModParticles.FLAME, true, true, eng.x + (R.nextDouble() - 0.5) * g.width() * 0.4, eng.y - 0.6,
						eng.z + (R.nextDouble() - 0.5) * 0.8, 0, 0, 0);
				MADE[2]++;
			}
			if (n % 5 == 0) {
				level.addParticle(ModParticles.GLOW, true, true, eng.x, eng.y, eng.z, 0, 0, 0);
			}
			if (R.nextInt(14) == 0) {
				for (int i = 0; i < 2 + R.nextInt(3); i++) {
					level.addParticle(ModParticles.SPARK, true, true, eng.x, eng.y, eng.z, (R.nextDouble() - 0.5) * 0.3, 0.12 + R.nextDouble() * 0.2,
							(R.nextDouble() - 0.5) * 0.3);
				}
				MADE[3]++;
			}
			if (n % 4 == 1) {
				level.addParticle(ModParticles.EMBER, true, true, eng.x, eng.y + 0.4, eng.z, (R.nextDouble() - 0.5) * 0.05, 0.06, (R.nextDouble() - 0.5) * 0.05);
			}
		}
		if (hp < 0.35f) {
			if (n % 2 == 0) {
				puff(level, eng, true, 1f + (0.35f - hp) * 2f);
				MADE[1]++;
			}
		} else if (hp < 0.6f) {
			if (n % 4 == 0) {
				puff(level, eng, false, 0.7f + (0.6f - hp) * 1.5f);
				MADE[0]++;
			}
		}
	}

	/** The wreck: flames and dark smoke come from the vehicle's own wreck code; here embers, sparks, the fire's glow, then smoulder. */
	private static void wreck(ClientLevel level, VehicleEntity v, VehicleGeometry.Geometry g) {
		int age = v.deathTime;
		Vec3 mid = v.toWorld((R.nextDouble() - 0.5) * g.width() * 0.6, g.height() * 0.6, (R.nextDouble() - 0.5) * g.length() * 0.6);
		if (age < 900) {
			if (v.tickCount % 3 == 0) {
				level.addParticle(ModParticles.EMBER, true, true, mid.x, mid.y, mid.z, (R.nextDouble() - 0.5) * 0.06, 0.08, (R.nextDouble() - 0.5) * 0.06);
			}
			if (v.tickCount % 4 == 0) {
				Vec3 c = v.toWorld(0, g.height() * 0.4, 0);
				level.addParticle(ModParticles.GLOW, true, true, c.x, c.y, c.z, 0, 0, 0);
			}
			if (R.nextInt(45) == 0) {
				// Something inside goes off: a burst of sparks and a puff.
				for (int i = 0; i < 4 + R.nextInt(4); i++) {
					level.addParticle(ModParticles.SPARK, true, true, mid.x, mid.y, mid.z, (R.nextDouble() - 0.5) * 0.5, 0.15 + R.nextDouble() * 0.3,
							(R.nextDouble() - 0.5) * 0.5);
				}
				MADE[3]++;
			}
		} else if (v.tickCount % 5 == 0) {
			// Smouldering: thin grey smoke.
			puff(level, mid, false, 0.8f);
		}
	}

	/** A puff of engine smoke: light grey (damaged) or thick and dark (badly hit), rising and drifting with the wind. */
	private static void puff(ClientLevel level, Vec3 at, boolean dark, float scale) {
		FxParticle p = FxClient.make(dark ? ModParticles.TRAIL_DARK : ModParticles.DEBRIS_SMOKE, level, at.x + (R.nextDouble() - 0.5) * 0.4, at.y,
				at.z + (R.nextDouble() - 0.5) * 0.4, (R.nextDouble() - 0.5) * 0.02, dark ? 0.07 : 0.05, (R.nextDouble() - 0.5) * 0.02);
		if (p == null) {
			return;
		}
		if (dark) {
			p.life(70, 120).size(0.5f * scale, 2.6f * scale).grow(18).alpha(0.62f, 2, 0.4f).color(0.08f, 0.075f, 0.07f, 0.3f, 0.29f, 0.28f);
		} else {
			p.life(45, 80).size(0.35f * scale, 1.7f * scale).grow(14).alpha(0.32f, 2, 0.35f).color(0.55f, 0.55f, 0.56f, 0.78f, 0.78f, 0.8f);
		}
		p.drag(0.93f).rise(0.0012f).windy().turbulent(0.003f);
		Minecraft.getInstance().particleEngine.add(p);
	}
}
