package com.stasdoto.airdefense.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * One configurable soft particle: grows, fades, changes colour, drifts with the wind and turbulence, can glow
 * (emissive, so shader packs bloom it), animate or loop its frames, flicker, collide with the ground and leave its
 * own trail (burning fragments, tracer streaks).
 */
public class FxParticle extends SingleQuadParticle {
	private final SpriteSet sprites;
	private int frameMode; // 0 = fixed random sprite, 1 = play once over the life, 2 = loop
	private int ticksPerFrame = 2;
	private int frameCount = 8;
	private int frameOffset;
	private float size0 = 0.5f;
	private float size1 = 1f;
	private float growTau;
	private float peakAlpha = 1f;
	private int fadeIn = 1;
	private float fadeOutStart = 0.5f;
	private float flicker;
	private float r0 = 1, g0 = 1, b0 = 1, r1 = 1, g1 = 1, b1 = 1;
	private boolean emissive;
	private float spin;
	private float buoyancy;
	private float fall;
	private boolean wind;
	private float turbulence;
	private ParticleOptions trail;
	private int trailEvery;
	private int trailSteps = 1;

	public FxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.sprites = sprites;
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.hasPhysics = false;
		this.friction = 0.96f;
		this.gravity = 0;
		this.roll = this.oRoll = 0;
	}

	// --- builder-style setup -------------------------------------------------------------------------

	public FxParticle life(int min, int max) {
		this.lifetime = min + (max > min ? random.nextInt(max - min + 1) : 0);
		return this;
	}

	public FxParticle size(float from, float to) {
		this.size0 = from;
		this.size1 = to;
		this.quadSize = from;
		// The box is only used for frustum culling (no physics), so make it as big as the puff will get:
		// otherwise large smoke would pop out of view at the screen edges.
		if (!hasPhysics) {
			float s = Math.max(from, to) * 2;
			setSize(s, s);
		}
		return this;
	}

	/** Growth time constant in ticks: smoke swells fast at first, then slowly. 0 = ease over the whole life. */
	public FxParticle grow(float tauTicks) {
		this.growTau = tauTicks;
		return this;
	}

	public FxParticle alpha(float peak, int fadeInTicks, float fadeOutStartFraction) {
		this.peakAlpha = peak;
		this.fadeIn = Math.max(1, fadeInTicks);
		this.fadeOutStart = fadeOutStartFraction;
		return this;
	}

	public FxParticle flicker(float amount) {
		this.flicker = amount;
		return this;
	}

	public FxParticle color(float r, float g, float b, float r2, float g2, float b2) {
		this.r0 = r;
		this.g0 = g;
		this.b0 = b;
		this.r1 = r2;
		this.g1 = g2;
		this.b1 = b2;
		setColor(r, g, b);
		return this;
	}

	public FxParticle glow() {
		this.emissive = true;
		return this;
	}

	/** Plays the sprite frames once over the particle's life (fireball: fire -> smoke). */
	public FxParticle animate() {
		this.frameMode = 1;
		setSpriteFromAge(sprites);
		return this;
	}

	/** Loops {@code frames} sprite frames, {@code ticksPerFrame} each (flames). */
	public FxParticle loop(int frames, int ticksPerFrame) {
		this.frameMode = 2;
		this.frameCount = frames;
		this.ticksPerFrame = Math.max(1, ticksPerFrame);
		this.frameOffset = random.nextInt(frames * this.ticksPerFrame);
		updateLoopSprite();
		return this;
	}

	public FxParticle drag(float friction) {
		this.friction = friction;
		return this;
	}

	public FxParticle rise(float buoyancy) {
		this.buoyancy = buoyancy;
		return this;
	}

	public FxParticle falls(float gravity) {
		this.fall = gravity;
		return this;
	}

	public FxParticle windy() {
		this.wind = true;
		return this;
	}

	public FxParticle turbulent(float amount) {
		this.turbulence = amount;
		return this;
	}

	public FxParticle spin(float perTick) {
		this.spin = perTick;
		return this;
	}

	/** Random roll: only for symmetric sprites (flashes, sparks, fire) — lit smoke keeps "up" up. */
	public FxParticle randomRoll() {
		this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
		return this;
	}

	/** Collides with blocks (dirt clods, burning fragments land instead of falling through the ground). */
	public FxParticle solid() {
		this.hasPhysics = true;
		setSize(0.25f, 0.25f);
		return this;
	}

	public FxParticle smokeTrail(ParticleOptions particle, int everyTicks) {
		this.trail = particle;
		this.trailEvery = Math.max(1, everyTicks);
		return this;
	}

	/** Fills the path covered each tick with this many trail particles (fast tracers would leave dotted lines). */
	public FxParticle trailSteps(int steps) {
		this.trailSteps = Math.max(1, steps);
		return this;
	}

	// --- behaviour ----------------------------------------------------------------------------------

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		oRoll = roll;
		if (age++ >= lifetime) {
			remove();
			return;
		}
		yd += buoyancy - fall;
		if (wind) {
			xd += (FxWind.x - xd) * 0.015;
			zd += (FxWind.z - zd) * 0.015;
		}
		if (turbulence > 0) {
			xd += (random.nextFloat() - 0.5f) * turbulence;
			yd += (random.nextFloat() - 0.5f) * turbulence * 0.6f;
			zd += (random.nextFloat() - 0.5f) * turbulence;
		}
		move(xd, yd, zd);
		if (onGround && hasPhysics) {
			xd *= 0.6;
			zd *= 0.6;
		}
		xd *= friction;
		yd *= friction;
		zd *= friction;
		roll += spin;

		float t = age / (float) lifetime;
		float a = peakAlpha * Math.min(1f, age / (float) fadeIn);
		if (t > fadeOutStart) {
			a *= 1f - (t - fadeOutStart) / (1f - fadeOutStart);
		}
		if (flicker > 0) {
			a *= 1f - flicker * random.nextFloat();
		}
		this.alpha = Mth.clamp(a, 0, 1);
		setColor(Mth.lerp(t, r0, r1), Mth.lerp(t, g0, g1), Mth.lerp(t, b0, b1));
		if (frameMode == 1) {
			setSpriteFromAge(sprites);
		} else if (frameMode == 2) {
			updateLoopSprite();
		}
		if (trail != null && age % trailEvery == 0 && !(onGround && hasPhysics)) {
			for (int i = 0; i < trailSteps; i++) {
				double k = (double) i / trailSteps;
				level.addParticle(trail, true, true, Mth.lerp(k, x, xo), Mth.lerp(k, y, yo), Mth.lerp(k, z, zo), 0, 0, 0);
			}
		}
	}

	private void updateLoopSprite() {
		int frame = ((age + frameOffset) / ticksPerFrame) % frameCount;
		setSprite(sprites.get(frame, frameCount - 1));
	}

	@Override
	public float getQuadSize(float partialTick) {
		if (growTau > 0) {
			float k = 1f - (float) Math.exp(-(age + partialTick) / growTau);
			return Mth.lerp(k, size0, size1);
		}
		float t = Mth.clamp((age + partialTick) / lifetime, 0, 1);
		float eased = 1 - (1 - t) * (1 - t);
		return Mth.lerp(eased, size0, size1);
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return emissive ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
