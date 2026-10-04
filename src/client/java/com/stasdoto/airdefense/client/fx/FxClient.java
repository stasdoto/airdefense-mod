package com.stasdoto.airdefense.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.fx.FxPayload;
import com.stasdoto.airdefense.registry.ModParticles;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Client side of the visual effects: particle presets, and the big composite effects (explosions, launches) that
 * unfold over several seconds. Also camera shake and sound that arrives late at long range (sound travels ~17 blocks
 * per tick, like 343 m/s in real life).
 */
public final class FxClient {
	private static final Map<SimpleParticleType, SpriteSet> SPRITES = new HashMap<>();
	private static final List<Scheduled> SCHEDULED = new ArrayList<>();
	private static final double SOUND_BLOCKS_PER_TICK = 17.0;
	/** Debug counter read by the in-game test. */
	public static int FLAMES_SPAWNED;

	private FxClient() {
	}

	private record Scheduled(int[] ticksLeft, Runnable action) {
	}

	public static void init() {
		register(ModParticles.EXHAUST, FxClient::exhaust);
		register(ModParticles.FLASH, (l, x, y, z, vx, vy, vz) -> flash(l, x, y, z, 6f));
		register(ModParticles.GLOW, (l, x, y, z, vx, vy, vz) -> glow(l, x, y, z, 2f, 6));
		register(ModParticles.SPARK, FxClient::spark);
		register(ModParticles.EMBER, FxClient::ember);
		register(ModParticles.TRACER, FxClient::tracer);
		register(ModParticles.TRACER_TAIL, FxClient::tracerTail);
		register(ModParticles.FIREBALL, (l, x, y, z, vx, vy, vz) -> fireball(l, x, y, z, vx, vy, vz, 1.5f));
		register(ModParticles.FLAME, (l, x, y, z, vx, vy, vz) -> flame(l, x, y, z, 0.8f));
		register(ModParticles.CHUNK, FxClient::chunk);
		register(ModParticles.TRAIL, (l, x, y, z, vx, vy, vz) -> trail(l, x, y, z, vx, vy, vz, false));
		register(ModParticles.TRAIL_DARK, (l, x, y, z, vx, vy, vz) -> trail(l, x, y, z, vx, vy, vz, true));
		register(ModParticles.SMOKE_BIG, (l, x, y, z, vx, vy, vz) -> smokeBig(l, x, y, z, vx, vy, vz, 1f));
		register(ModParticles.SMOKE_WHITE, (l, x, y, z, vx, vy, vz) -> smokeWhite(l, x, y, z, vx, vy, vz, 1f));
		register(ModParticles.DUST, (l, x, y, z, vx, vy, vz) -> dust(l, x, y, z, vx, vy, vz, 1f));
		register(ModParticles.DIRT, (l, x, y, z, vx, vy, vz) -> dirt(l, x, y, z, vx, vy, vz, 1f));
		register(ModParticles.SHOCK, FxClient::shock);
		register(ModParticles.DEBRIS_SMOKE, FxClient::debrisSmoke);

		ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, context) -> play(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(FxClient::tick);
	}

	private static void tick(Minecraft mc) {
		FxWind.tick();
		CameraShake.tick(mc);
		if (mc.level == null) {
			SCHEDULED.clear();
			return;
		}
		List<Runnable> due = new ArrayList<>();
		for (Iterator<Scheduled> it = SCHEDULED.iterator(); it.hasNext(); ) {
			Scheduled s = it.next();
			if (--s.ticksLeft()[0] <= 0) {
				due.add(s.action());
				it.remove();
			}
		}
		due.forEach(Runnable::run);
	}

	static void after(int ticks, Runnable action) {
		if (ticks <= 0) {
			action.run();
		} else {
			SCHEDULED.add(new Scheduled(new int[]{ticks}, action));
		}
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

	private static float rnd(RandomSource r, float min, float max) {
		return min + r.nextFloat() * (max - min);
	}

	// --- presets ---------------------------------------------------------------------------------------

	/** Rocket motor plume: white-hot core cooling to orange, glows in the dark. */
	static FxParticle exhaust(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.EXHAUST, l, x, y, z, vx, vy, vz).life(2, 4).size(0.62f, 0.14f)
				.color(1f, 0.97f, 0.88f, 1f, 0.52f, 0.14f).alpha(1f, 1, 0.3f).glow().drag(0.8f).randomRoll();
	}

	static FxParticle flash(ClientLevel l, double x, double y, double z, float size) {
		return base(ModParticles.FLASH, l, x, y, z, 0, 0, 0).life(3, 4).size(size, size * 1.35f)
				.color(1f, 0.98f, 0.9f, 1f, 0.78f, 0.5f).alpha(1f, 1, 0.1f).glow().randomRoll();
	}

	/** Soft light halo: makes fire and motors light up the scene, and gives shader packs something to bloom. */
	static FxParticle glow(ClientLevel l, double x, double y, double z, float size, int life) {
		return base(ModParticles.GLOW, l, x, y, z, 0, 0, 0).life(life, life + 2).size(size, size)
				.color(1f, 0.72f, 0.38f, 1f, 0.45f, 0.15f).alpha(0.55f, 2, 0.4f).flicker(0.15f).glow();
	}

	/** Burning fragment: glowing, falls, leaves a thin smoke trail, lands on the ground. */
	static FxParticle spark(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.SPARK, l, x, y, z, vx, vy, vz).life(18, 36).size(0.22f, 0.08f)
				.color(1f, 0.9f, 0.55f, 1f, 0.3f, 0.05f).alpha(1f, 1, 0.6f).glow().drag(0.97f).falls(0.035f)
				.smokeTrail(ModParticles.DEBRIS_SMOKE, 1).randomRoll().solid();
	}

	/** Glowing ember rising from a fire. */
	static FxParticle ember(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.EMBER, l, x, y, z, vx, vy, vz).life(40, 80).size(0.07f, 0.03f)
				.color(1f, 0.7f, 0.25f, 1f, 0.25f, 0.05f).alpha(1f, 2, 0.5f).flicker(0.45f).glow()
				.drag(0.96f).rise(0.0025f).turbulent(0.02f).windy();
	}

	/** Gepard shell: a bright dot flying along the shot line, leaving a short glowing streak. */
	static FxParticle tracer(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.TRACER, l, x, y, z, vx, vy, vz).life(14, 14).size(0.19f, 0.16f)
				.color(1f, 0.85f, 0.45f, 1f, 0.55f, 0.15f).alpha(1f, 1, 0.85f).glow().drag(1f)
				.smokeTrail(ModParticles.TRACER_TAIL, 1);
	}

	static FxParticle tracerTail(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.TRACER_TAIL, l, x, y, z, 0, 0, 0).life(2, 3).size(0.14f, 0.05f)
				.color(1f, 0.75f, 0.35f, 1f, 0.45f, 0.1f).alpha(0.8f, 1, 0.1f).glow();
	}

	static FxParticle fireball(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float size) {
		return base(ModParticles.FIREBALL, l, x, y, z, vx, vy, vz).life(18, 30).size(size * 0.55f, size * 1.6f).grow(9)
				.alpha(1f, 1, 0.55f).glow().animate().drag(0.86f).rise(0.006f).randomRoll()
				.spin((l.getRandom().nextFloat() - 0.5f) * 0.06f);
	}

	/** Licking flame on the ground (fires after an impact, burning wreckage). */
	static FxParticle flame(ClientLevel l, double x, double y, double z, float size) {
		return base(ModParticles.FLAME, l, x, y + size * 0.8, z, 0, 0, 0).life(28, 50).size(size, size * 0.85f)
				.alpha(0.95f, 4, 0.6f).flicker(0.12f).glow().loop(8, 2).drag(0.9f).rise(0.001f);
	}

	/** Dirt clod thrown out of the crater: arcs, lands, stays a moment. */
	static FxParticle chunk(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		RandomSource r = l.getRandom();
		float s = rnd(r, 0.09f, 0.22f);
		return base(ModParticles.CHUNK, l, x, y, z, vx, vy, vz).life(45, 85).size(s, s)
				.color(0.42f, 0.33f, 0.24f, 0.36f, 0.29f, 0.22f).alpha(1f, 1, 0.8f).drag(0.985f).falls(0.06f)
				.randomRoll().spin(rnd(r, -0.3f, 0.3f)).solid();
	}

	/** Contrail puff: starts dense, swells into a wide soft trail that lingers ~15 s and drifts with the wind. */
	static FxParticle trail(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, boolean dark) {
		FxParticle p = base(dark ? ModParticles.TRAIL_DARK : ModParticles.TRAIL, l, x, y, z, vx, vy, vz)
				.drag(0.9f).windy().rise(0.0004f).turbulent(0.0035f);
		if (dark) {
			return p.life(200, 280).size(0.8f, 3.3f).grow(16).alpha(0.74f, 2, 0.4f).color(0.62f, 0.58f, 0.52f, 0.76f, 0.75f, 0.74f);
		}
		return p.life(280, 380).size(0.8f, 4.0f).grow(20).alpha(0.62f, 2, 0.45f).color(0.98f, 0.98f, 0.98f, 0.86f, 0.87f, 0.9f);
	}

	/** Heavy black-brown explosion smoke that slowly lightens as it spreads. */
	static FxParticle smokeBig(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float scale) {
		return base(ModParticles.SMOKE_BIG, l, x, y, z, vx, vy, vz).life(420, 700).size(2.2f * scale, 8f * scale).grow(80)
				.alpha(0.9f, 6, 0.55f).color(0.1f, 0.09f, 0.08f, 0.42f, 0.41f, 0.4f)
				.drag(0.94f).rise(0.0012f).windy().turbulent(0.002f);
	}

	static FxParticle smokeWhite(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float scale) {
		return base(ModParticles.SMOKE_WHITE, l, x, y, z, vx, vy, vz).life(170, 280).size(1.3f * scale, 5.8f * scale).grow(40)
				.alpha(0.8f, 4, 0.45f).color(0.9f, 0.88f, 0.85f, 0.8f, 0.8f, 0.8f)
				.drag(0.9f).rise(0.0008f).windy().turbulent(0.002f);
	}

	/** Dust rolled along the ground by the blast wave. */
	static FxParticle dust(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float scale) {
		return base(ModParticles.DUST, l, x, y, z, vx, vy, vz).life(90, 150).size(1.0f * scale, 4.8f * scale).grow(30)
				.alpha(0.62f, 3, 0.35f).color(0.52f, 0.45f, 0.35f, 0.64f, 0.6f, 0.54f)
				.drag(0.88f).rise(0.0008f).windy();
	}

	/** Soil thrown up into the air: a brown fountain that falls back. */
	static FxParticle dirt(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, float scale) {
		return base(ModParticles.DIRT, l, x, y, z, vx, vy, vz).life(60, 110).size(0.9f * scale, 3.4f * scale).grow(25)
				.alpha(0.88f, 2, 0.45f).color(0.38f, 0.3f, 0.22f, 0.55f, 0.5f, 0.44f)
				.drag(0.95f).falls(0.022f).windy();
	}

	/** Condensation / shock ring: a fast pale ring that races outwards for a split second. */
	static FxParticle shock(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.SHOCK, l, x, y, z, vx, vy, vz).life(9, 13).size(0.8f, 2.8f).grow(4)
				.alpha(0.5f, 1, 0.2f).color(0.95f, 0.95f, 0.95f, 0.9f, 0.9f, 0.9f).drag(0.8f);
	}

	static FxParticle debrisSmoke(ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
		return base(ModParticles.DEBRIS_SMOKE, l, x, y, z, vx, vy, vz).life(40, 70).size(0.25f, 0.95f).grow(10)
				.alpha(0.3f, 1, 0.3f).color(0.16f, 0.15f, 0.14f, 0.45f, 0.45f, 0.45f).drag(0.9f).windy().rise(0.0006f);
	}

	// --- composite effects ------------------------------------------------------------------------------

	private static void play(Minecraft mc, FxPayload p) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
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

	/** Fewer particles for far-away explosions: nobody sees the details at 500 blocks, but the frame rate would. */
	private static float detail(Minecraft mc, Vec3 at) {
		double d = mc.player.position().distanceTo(at);
		return d < 250 ? 1f : d < 500 ? 0.55f : 0.3f;
	}

	private static int n(float base, float detail) {
		return Math.max(1, Math.round(base * detail));
	}

	private static void groundImpact(Minecraft mc, ClientLevel level, Vec3 at, float power) {
		RandomSource r = level.getRandom();
		float scale = power / 6f;
		float lod = detail(mc, at);
		var pe = mc.particleEngine;

		// 1. Flash and white-hot core.
		pe.add(flash(level, at.x, at.y + 1 + power * 0.5, at.z, power * 1.6f));
		pe.add(glow(level, at.x, at.y + power * 1.1, at.z, power * 1.1f, 24));
		for (int i = 0; i < 3; i++) {
			pe.add(fireball(level, at.x, at.y + 1 + power * 0.2, at.z, 0, 0.05, 0, power * 0.55f));
		}
		// 2. Fireball: glowing billows bursting out and rolling upwards, turning into smoke.
		for (int i = 0; i < n(16 + power * 4, lod); i++) {
			Vec3 d = randomDir(r, 0.25);
			double sp = 0.1 + r.nextDouble() * 0.32 * scale;
			double lift = 1.0 + Math.abs(d.y) * power * 0.45;
			pe.add(fireball(level, at.x + d.x * power * 0.3, at.y + lift, at.z + d.z * power * 0.3,
					d.x * sp, Math.abs(d.y) * sp + 0.08, d.z * sp, 1.0f + power * 0.22f));
		}
		// 3. Soil fountain and flying clods.
		for (int i = 0; i < n(18 + power * 5, lod); i++) {
			double a = r.nextDouble() * Mth.TWO_PI;
			double side = r.nextDouble() * 0.25 * scale;
			pe.add(dirt(level, at.x, at.y + 0.5, at.z, Math.cos(a) * side, 0.45 + r.nextDouble() * 0.9 * Math.max(0.7, scale),
					Math.sin(a) * side, Math.max(0.6f, scale)));
		}
		for (int i = 0; i < n(26 + power * 7, lod); i++) {
			Vec3 d = randomDir(r, 0.4);
			double sp = 0.35 + r.nextDouble() * 0.9 * Math.max(0.7, scale);
			pe.add(chunk(level, at.x, at.y + 0.6, at.z, d.x * sp, Math.abs(d.y) * sp + 0.25, d.z * sp));
		}
		for (int i = 0; i < n(8 + power * 2.5f, lod); i++) {
			Vec3 d = randomDir(r, 0.35);
			double sp = 0.4 + r.nextDouble() * 0.8;
			pe.add(spark(level, at.x, at.y + 0.5, at.z, d.x * sp, Math.abs(d.y) * sp + 0.35, d.z * sp));
		}
		// 4. Shock ring racing out along the ground and the rolling dust skirt behind it.
		int ring = n(26, lod);
		for (int i = 0; i < ring; i++) {
			double a = Mth.TWO_PI * i / ring;
			double sp = 1.3 + r.nextDouble() * 0.4;
			pe.add(shock(level, at.x, at.y + 0.8, at.z, Math.cos(a) * sp, 0.02, Math.sin(a) * sp));
		}
		int skirt = n(16 + power * 3, lod);
		for (int i = 0; i < skirt; i++) {
			double a = Mth.TWO_PI * i / skirt + r.nextDouble() * 0.2;
			double sp = 0.5 + r.nextDouble() * 0.6 * Math.max(0.7, scale);
			pe.add(dust(level, at.x, at.y + 0.3, at.z, Math.cos(a) * sp, 0.02, Math.sin(a) * sp, Math.max(0.6f, scale)));
		}
		// 5. Smoke column fed for ~1.5 s: early puffs shoot highest and spread into the mushroom cap,
		//    later ones are slower and form the stem.
		for (int wave = 0; wave < 12; wave++) {
			final int w = wave;
			after(2 + wave * 3, () -> {
				for (int i = 0; i < n(3 + power * 0.6f, lod); i++) {
					double up = (0.75 - w * 0.05) * Math.max(0.6, scale) * (0.8 + r.nextDouble() * 0.4);
					double side = (w < 3 ? 0.18 : 0.05) * r.nextDouble();
					double a = r.nextDouble() * Mth.TWO_PI;
					pe.add(smokeBig(level, at.x + r.nextGaussian() * power * 0.15, at.y + 1.5, at.z + r.nextGaussian() * power * 0.15,
							Math.cos(a) * side, up, Math.sin(a) * side, Math.max(0.5f, scale * (0.75f + r.nextFloat() * 0.5f))));
				}
			});
		}
		// 6. Fires burning in the crater for ~12 s, with embers and thin smoke.
		for (int k = 0; k < 60; k++) {
			after(6 + k * 4, () -> {
				double a = r.nextDouble() * Mth.TWO_PI;
				double d = r.nextDouble() * power * 0.55;
				double fx = at.x + Math.cos(a) * d;
				double fz = at.z + Math.sin(a) * d;
				double fy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(fx), Mth.floor(fz));
				if (Math.abs(fy - at.y) > power) {
					fy = at.y;
				}
				float fs = 0.9f + r.nextFloat() * 0.9f;
				pe.add(flame(level, fx, fy, fz, fs));
				FLAMES_SPAWNED++;
				if (r.nextInt(2) == 0) {
					pe.add(glow(level, fx, fy + fs, fz, fs * 2.2f, 30));
				}
				if (r.nextInt(2) == 0) {
					pe.add(ember(level, fx, fy + 0.6, fz, 0, 0.05, 0));
				}
				if (r.nextInt(3) == 0) {
					pe.add(debrisSmoke(level, fx, fy + 1, fz, 0, 0.08, 0));
				}
			});
		}
		// 7. The shock and the sound arrive later the further away you are.
		double dist = mc.player.position().distanceTo(at);
		int delay = (int) (dist / SOUND_BLOCKS_PER_TICK);
		float shake = (float) Math.min(3.0, power * 4.5 / (dist + 10));
		after(delay, () -> CameraShake.add(shake));
		sound(level, at, delay, power >= 6 ? ModSounds.EXPLOSION_HUGE : ModSounds.EXPLOSION_BIG, 4f, 0.9f + r.nextFloat() * 0.15f);
		sound(level, at, delay, ModSounds.EXPLOSION_FAR, 6f, 0.85f + r.nextFloat() * 0.15f);
	}

	private static void airBurst(Minecraft mc, ClientLevel level, Vec3 at, float size, boolean threat) {
		RandomSource r = level.getRandom();
		float lod = detail(mc, at);
		var pe = mc.particleEngine;
		pe.add(flash(level, at.x, at.y, at.z, size * (threat ? 2.4f : 1.7f)));
		pe.add(glow(level, at.x, at.y, at.z, size * 2.2f, 16));
		for (int i = 0; i < n(5 + size * 3, lod); i++) {
			Vec3 d = randomDir(r, -1);
			double sp = 0.08 + r.nextDouble() * 0.25;
			pe.add(fireball(level, at.x + d.x, at.y + d.y, at.z + d.z, d.x * sp, d.y * sp, d.z * sp, 0.9f + size * 0.35f));
		}
		// Shock sphere: a pale puff ring for a split second.
		int ring = n(18, lod);
		for (int i = 0; i < ring; i++) {
			Vec3 d = randomDir(r, -1);
			pe.add(shock(level, at.x, at.y, at.z, d.x * 1.1, d.y * 1.1, d.z * 1.1));
		}
		// The black cloud of an air burst hangs in the sky for a long time.
		for (int i = 0; i < n(6 + size * 3, lod); i++) {
			Vec3 d = randomDir(r, -1);
			double sp = 0.05 + r.nextDouble() * 0.15;
			FxParticle s = smokeBig(level, at.x + d.x, at.y + d.y, at.z + d.z, d.x * sp, d.y * sp * 0.5, d.z * sp,
					(threat ? 0.55f : 0.35f) + size * 0.06f);
			s.life(700, 950);
			pe.add(s);
		}
		// Fragments falling down with smoke trails; a shot-down missile also drops a few big burning pieces.
		for (int i = 0; i < n((threat ? 10 : 5) + size * 2, lod); i++) {
			Vec3 d = randomDir(r, -1);
			double sp = 0.2 + r.nextDouble() * 0.6;
			pe.add(spark(level, at.x, at.y, at.z, d.x * sp, d.y * sp, d.z * sp));
		}
		if (threat) {
			for (int i = 0; i < 3 + (int) (size / 2); i++) {
				Vec3 d = randomDir(r, -1);
				double sp = 0.15 + r.nextDouble() * 0.3;
				FxParticle wreck = spark(level, at.x, at.y, at.z, d.x * sp, d.y * sp + 0.2, d.z * sp);
				wreck.size(0.45f, 0.3f).life(120, 200).falls(0.02f).smokeTrail(ModParticles.TRAIL_DARK, 2);
				pe.add(wreck);
			}
		}
		double dist = mc.player.position().distanceTo(at);
		int delay = (int) (dist / SOUND_BLOCKS_PER_TICK);
		after(delay, () -> CameraShake.add((float) Math.min(1.5, size * 2.0 / (dist + 10))));
		sound(level, at, delay, ModSounds.EXPLOSION_AIR, 3.5f, 0.95f + r.nextFloat() * 0.15f);
		if (threat) {
			sound(level, at, delay, ModSounds.EXPLOSION_FAR, 5f, 1.0f + r.nextFloat() * 0.1f);
		}
	}

	private static void launch(Minecraft mc, ClientLevel level, Vec3 at, float size) {
		RandomSource r = level.getRandom();
		var pe = mc.particleEngine;
		pe.add(flash(level, at.x, at.y + 0.5, at.z, size * 1.3f));
		pe.add(glow(level, at.x, at.y + 0.5, at.z, size * 3f, 12));
		for (int i = 0; i < n(10 + size * 10, 1); i++) {
			double a = r.nextDouble() * Mth.TWO_PI;
			double sp = 0.15 + r.nextDouble() * 0.35 * size;
			pe.add(smokeWhite(level, at.x + r.nextGaussian() * 0.5, at.y + r.nextDouble(), at.z + r.nextGaussian() * 0.5,
					Math.cos(a) * sp, 0.02 + r.nextDouble() * 0.08, Math.sin(a) * sp, Math.max(0.3f, size * 0.45f)));
		}
		for (int i = 0; i < n(4 + size * 4, 1); i++) {
			double a = r.nextDouble() * Mth.TWO_PI;
			double sp = 0.3 + r.nextDouble() * 0.4;
			pe.add(dust(level, at.x, at.y + 0.2, at.z, Math.cos(a) * sp, 0.01, Math.sin(a) * sp, Math.max(0.3f, size * 0.35f)));
		}
		for (int i = 0; i < 4 + size * 2; i++) {
			pe.add(exhaust(level, at.x + r.nextGaussian() * 0.3, at.y + 0.3, at.z + r.nextGaussian() * 0.3, 0, 0, 0));
		}
		double dist = mc.player.position().distanceTo(at);
		if (size >= 1.5f) {
			after((int) (dist / SOUND_BLOCKS_PER_TICK), () -> CameraShake.add((float) Math.min(0.8, size * 1.5 / (dist + 6))));
		}
	}

	private static void sound(ClientLevel level, Vec3 at, int delay, SoundEvent sound, float volume, float pitch) {
		after(delay, () -> level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false));
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
