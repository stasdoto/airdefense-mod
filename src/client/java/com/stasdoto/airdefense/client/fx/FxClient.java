package com.stasdoto.airdefense.client.fx;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.fx.FxPayload;
import com.stasdoto.airdefense.registry.ModParticles;

/** Client side of the visual effects: particle presets, and the big composite effects (explosions, launches). */
public final class FxClient {
	private static final Map<SimpleParticleType, SpriteSet> SPRITES = new HashMap<>();

	private FxClient() {
	}

	public static void init() {
		register(ModParticles.EXHAUST, FxClient::exhaust);
		register(ModParticles.FLASH, (l, x, y, z, vx, vy, vz) -> flash(l, x, y, z, 6f));
		register(ModParticles.SPARK, FxClient::spark);
		register(ModParticles.TRACER, FxClient::tracer);
		register(ModParticles.FIREBALL, (l, x, y, z, vx, vy, vz) -> fireball(l, x, y, z, vx, vy, vz, 1.5f));
		register(ModParticles.TRAIL, (l, x, y, z, vx, vy, vz) -> trail(l, x, y, z, vx, vy, vz, false));
		register(ModParticles.TRAIL_DARK, (l, x, y, z, vx, vy, vz) -> trail(l, x, y, z, vx, vy, vz, true));
		register(ModParticles.SMOKE_BIG, (l, x, y, z, vx, vy, vz) -> smokeBig(l, x, y, z, vx, vy, vz, 1f));
		register(ModParticles.SMOKE_WHITE, (l, x, y, z, vx, vy, vz) -> smokeWhite(l, x, y, z, vx, vy, vz, 1f));
		register(ModParticles.DUST, (l, x, y, z, vx, vy, vz) -> dust(l, x, y, z, vx, vy, vz, 1f));
		register(ModParticles.DEBRIS_SMOKE, FxClient::debrisSmoke);

		ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, context) -> play(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> FxWind.tick());
	}

	@FunctionalInterface
	private interface Preset {
		FxParticle make(ClientLevel level, double x, double y, double z, double vx, double vy, double vz);
	}

	private static void register(SimpleParticleType type, Preset preset) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> {
			SPRITES.put(type, sprites);
			return (options, level, x, y, z, vx, vy, vz, random) -> preset.make(level, x, y, z, vx, vy, vz);
		});
	}

	private static FxParticle base(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
		return new FxParticle(level, x, y, z, vx, vy, vz, SPRITES.get(type));
	}

	// --- presets ---------------------------------------------------------------------------------------

	/** Rocket motor plume: white-hot core cooling to orange, glows in the dark. */
	static FxParticle exhaust(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.EXHAUST, l, x, y, z, vx, vy, vz).life(3, 5).size(0.55f, 0.12f)
				.color(1f, 0.97f, 0.85f, 1f, 0.5f, 0.12f).alpha(1f, 1, 0.3f).glow().drag(0.8f);
	}

	static FxParticle flash(ClientLevel l, double x, double y, double z, float size) {
		return base(ModParticles.FLASH, l, x, y, z, 0, 0, 0).life(4, 5).size(size, size * 1.5f)
				.color(1f, 0.97f, 0.88f, 1f, 0.75f, 0.45f).alpha(1f, 1, 0.2f).glow();
	}

	/** Burning fragment: glowing, falls, leaves a thin smoke trail. */
	static FxParticle spark(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.SPARK, l, x, y, z, vx, vy, vz).life(18, 34).size(0.22f, 0.08f)
				.color(1f, 0.9f, 0.55f, 1f, 0.3f, 0.05f).alpha(1f, 1, 0.6f).glow().drag(0.97f).falls(0.035f)
				.smokeTrail(ModParticles.DEBRIS_SMOKE, 1);
	}

	/** Gepard shell: a bright dot flying along the shot line (velocity = direction * speed, life set by the caller). */
	static FxParticle tracer(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.TRACER, l, x, y, z, vx, vy, vz).life(14, 14).size(0.2f, 0.16f)
				.color(1f, 0.85f, 0.45f, 1f, 0.55f, 0.15f).alpha(1f, 1, 0.85f).glow().drag(1f);
	}

	static FxParticle fireball(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float size) {
		return base(ModParticles.FIREBALL, l, x, y, z, vx, vy, vz).life(16, 26).size(size * 0.6f, size * 1.6f)
				.alpha(1f, 1, 0.55f).glow().animate().drag(0.86f).rise(0.006f).spin((l.getRandom().nextFloat() - 0.5f) * 0.08f);
	}

	/** Contrail puff: starts small and dense, swells to a wide soft trail that lingers ~15 s and drifts with the wind. */
	static FxParticle trail(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, boolean dark) {
		FxParticle p = base(dark ? ModParticles.TRAIL_DARK : ModParticles.TRAIL, l, x, y, z, vx, vy, vz)
				.drag(0.9f).windy().rise(0.0004f).spin((l.getRandom().nextFloat() - 0.5f) * 0.01f);
		if (dark) {
			return p.life(200, 280).size(0.8f, 3.2f).grow(16).alpha(0.72f, 2, 0.4f).color(0.6f, 0.56f, 0.5f, 0.74f, 0.73f, 0.72f);
		}
		return p.life(260, 360).size(0.8f, 3.8f).grow(18).alpha(0.62f, 2, 0.45f).color(0.97f, 0.97f, 0.97f, 0.86f, 0.87f, 0.9f);
	}

	/** Heavy black-brown explosion smoke that slowly lightens as it spreads. */
	static FxParticle smokeBig(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float scale) {
		return base(ModParticles.SMOKE_BIG, l, x, y, z, vx, vy, vz).life(380, 620).size(2.2f * scale, 7.5f * scale).grow(70)
				.alpha(0.88f, 6, 0.55f).color(0.11f, 0.1f, 0.09f, 0.4f, 0.39f, 0.38f)
				.drag(0.94f).rise(0.0012f).windy().spin((l.getRandom().nextFloat() - 0.5f) * 0.006f);
	}

	static FxParticle smokeWhite(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float scale) {
		return base(ModParticles.SMOKE_WHITE, l, x, y, z, vx, vy, vz).life(160, 260).size(1.3f * scale, 5.5f * scale).grow(40)
				.alpha(0.78f, 4, 0.45f).color(0.88f, 0.86f, 0.83f, 0.8f, 0.8f, 0.8f)
				.drag(0.9f).rise(0.0008f).windy().spin((l.getRandom().nextFloat() - 0.5f) * 0.01f);
	}

	/** Dust thrown along the ground by the blast wave. */
	static FxParticle dust(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float scale) {
		return base(ModParticles.DUST, l, x, y, z, vx, vy, vz).life(100, 170).size(1.0f * scale, 4.5f * scale).grow(30)
				.alpha(0.8f, 3, 0.4f).color(0.52f, 0.45f, 0.35f, 0.62f, 0.58f, 0.52f)
				.drag(0.88f).rise(0.0008f).windy();
	}

	static FxParticle debrisSmoke(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.DEBRIS_SMOKE, l, x, y, z, vx, vy, vz).life(40, 70).size(0.25f, 0.9f).grow(10)
				.alpha(0.3f, 1, 0.3f).color(0.18f, 0.17f, 0.16f, 0.45f, 0.45f, 0.45f).drag(0.9f).windy().rise(0.0006f);
	}

	// --- composite effects ------------------------------------------------------------------------------

	private static void play(Minecraft mc, FxPayload p) {
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		Vec3 at = new Vec3(p.x(), p.y(), p.z());
		switch (p.kind()) {
			case FxPayload.GROUND_IMPACT -> groundImpact(mc, level, at, p.power());
			case FxPayload.AIR_BURST_THREAT -> airBurst(mc, level, at, p.power(), true);
			case FxPayload.AIR_BURST_INTERCEPTOR -> airBurst(mc, level, at, p.power(), false);
			case FxPayload.LAUNCH -> launch(mc, level, at, p.power());
			case FxPayload.TRACER -> {
				Vec3 end = new Vec3(p.ax(), p.ay(), p.az());
				Vec3 d = end.subtract(at);
				double dist = d.length();
				double speed = 4.0;
				Vec3 v = d.scale(speed / Math.max(dist, 1e-3));
				FxParticle t = tracer(level, at.x, at.y, at.z, v.x, v.y, v.z);
				t.setLifetime(Math.max(1, (int) (dist / speed)));
				mc.particleEngine.add(t);
			}
			default -> {
			}
		}
	}

	private static void groundImpact(Minecraft mc, ClientLevel level, Vec3 at, float power) {
		RandomSource r = level.getRandom();
		float scale = power / 6f;
		mc.particleEngine.add(flash(level, at.x, at.y + 1 + power * 0.5, at.z, power * 1.4f));

		// Fireball: a cluster of glowing animated fire puffs bursting outwards and up.
		int fire = (int) (14 + power * 4);
		for (int i = 0; i < fire; i++) {
			Vec3 d = randomDir(r, 0.25);
			double sp = 0.1 + r.nextDouble() * 0.3 * scale;
			double lift = 1.0 + Math.abs(d.y) * power * 0.45;
			mc.particleEngine.add(fireball(level, at.x + d.x * power * 0.3, at.y + lift, at.z + d.z * power * 0.3,
					d.x * sp, Math.abs(d.y) * sp + 0.08, d.z * sp, 1.0f + power * 0.22f));
		}
		// Burning fragments arcing out of the blast.
		int sparks = (int) (8 + power * 2.5);
		for (int i = 0; i < sparks; i++) {
			Vec3 d = randomDir(r, 0.35);
			double sp = 0.4 + r.nextDouble() * 0.8;
			mc.particleEngine.add(spark(level, at.x, at.y + 0.5, at.z, d.x * sp, Math.abs(d.y) * sp + 0.35, d.z * sp));
		}
		// Smoke column: puffs launched upwards at different speeds settle at different heights; the fastest form the cap.
		int column = (int) (14 + power * 4);
		for (int i = 0; i < column; i++) {
			double up = 0.15 + r.nextDouble() * 0.55 * Math.max(0.6, scale);
			double side = r.nextDouble() * 0.12 * (up > 0.5 ? 2.5 : 1);
			double a = r.nextDouble() * Mth.TWO_PI;
			mc.particleEngine.add(smokeBig(level, at.x + r.nextGaussian() * power * 0.2, at.y + 1 + r.nextDouble() * power * 0.3,
					at.z + r.nextGaussian() * power * 0.2, Math.cos(a) * side, up, Math.sin(a) * side, Math.max(0.5f, scale * (0.8f + r.nextFloat() * 0.5f))));
		}
		// Dust ring rolling out along the ground.
		int ring = (int) (14 + power * 3);
		for (int i = 0; i < ring; i++) {
			double a = Mth.TWO_PI * i / ring + r.nextDouble() * 0.2;
			double sp = 0.5 + r.nextDouble() * 0.6 * Math.max(0.7, scale);
			mc.particleEngine.add(dust(level, at.x, at.y + 0.3, at.z, Math.cos(a) * sp, 0.02, Math.sin(a) * sp, Math.max(0.6f, scale)));
		}
	}

	private static void airBurst(Minecraft mc, ClientLevel level, Vec3 at, float size, boolean threat) {
		RandomSource r = level.getRandom();
		mc.particleEngine.add(flash(level, at.x, at.y, at.z, size * (threat ? 2.2f : 1.6f)));
		int fire = (int) (4 + size * 2.5);
		for (int i = 0; i < fire; i++) {
			Vec3 d = randomDir(r, -1);
			double sp = 0.08 + r.nextDouble() * 0.25;
			mc.particleEngine.add(fireball(level, at.x + d.x, at.y + d.y, at.z + d.z, d.x * sp, d.y * sp, d.z * sp, 0.9f + size * 0.35f));
		}
		// The black cloud of an air burst hangs in the sky for a long time.
		int smoke = (int) (5 + size * 2.5);
		for (int i = 0; i < smoke; i++) {
			Vec3 d = randomDir(r, -1);
			double sp = 0.05 + r.nextDouble() * 0.15;
			mc.particleEngine.add(smokeBig(level, at.x + d.x, at.y + d.y, at.z + d.z, d.x * sp, d.y * sp * 0.5, d.z * sp,
					(threat ? 0.55f : 0.35f) + size * 0.06f));
		}
		// Fragments falling down with smoke trails.
		int sparks = (int) ((threat ? 10 : 5) + size * 2);
		for (int i = 0; i < sparks; i++) {
			Vec3 d = randomDir(r, -1);
			double sp = 0.2 + r.nextDouble() * 0.6;
			mc.particleEngine.add(spark(level, at.x, at.y, at.z, d.x * sp, d.y * sp, d.z * sp));
		}
	}

	private static void launch(Minecraft mc, ClientLevel level, Vec3 at, float size) {
		RandomSource r = level.getRandom();
		mc.particleEngine.add(flash(level, at.x, at.y + 0.5, at.z, size * 1.2f));
		int puffs = (int) (10 + size * 10);
		for (int i = 0; i < puffs; i++) {
			double a = r.nextDouble() * Mth.TWO_PI;
			double sp = 0.15 + r.nextDouble() * 0.35 * size;
			mc.particleEngine.add(smokeWhite(level, at.x + r.nextGaussian() * 0.5, at.y + r.nextDouble(), at.z + r.nextGaussian() * 0.5,
					Math.cos(a) * sp, 0.02 + r.nextDouble() * 0.08, Math.sin(a) * sp, Math.max(0.3f, size * 0.45f)));
		}
		for (int i = 0; i < 4 + size * 2; i++) {
			mc.particleEngine.add(exhaust(level, at.x + r.nextGaussian() * 0.3, at.y + 0.3, at.z + r.nextGaussian() * 0.3, 0, 0, 0));
		}
	}

	/** Random unit vector; {@code minUp} >= 0 keeps it in the upper hemisphere (with that much minimum lift), < 0 = any direction. */
	private static Vec3 randomDir(RandomSource r, double minUp) {
		Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
		v = v.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : v.normalize();
		if (minUp >= 0) {
			v = new Vec3(v.x, Math.max(Math.abs(v.y), minUp), v.z).normalize();
		}
		return v;
	}
}
