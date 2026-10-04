package com.stasdoto.airdefense.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import com.stasdoto.airdefense.AirDefense;

/** Custom particles with soft, high-resolution textures (they replace Minecraft's pixel smoke and fire). */
public final class ModParticles {
	public static final SimpleParticleType EXHAUST = register("exhaust");
	public static final SimpleParticleType FLASH = register("flash");
	public static final SimpleParticleType SPARK = register("spark");
	public static final SimpleParticleType TRACER = register("tracer");
	public static final SimpleParticleType FIREBALL = register("fireball");
	public static final SimpleParticleType TRAIL = register("trail");
	public static final SimpleParticleType TRAIL_DARK = register("trail_dark");
	public static final SimpleParticleType SMOKE_BIG = register("smoke_big");
	public static final SimpleParticleType SMOKE_WHITE = register("smoke_white");
	public static final SimpleParticleType DUST = register("dust");
	public static final SimpleParticleType DEBRIS_SMOKE = register("debris_smoke");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		// "true" = always spawned, even with the "Particles: minimal" setting.
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, AirDefense.id(name), FabricParticleTypes.simple(true));
	}

	public static void init() {
	}
}
