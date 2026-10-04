package com.stasdoto.airdefense.registry;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import com.stasdoto.airdefense.AirDefense;

/** Damage from small arms (data in data/airdefense/damage_type, tags in data/minecraft/tags/damage_type). */
public final class ModDamageTypes {
	/** A rifle, machine gun or pistol round. Ignores the hurt cooldown (automatic fire) and does not knock back. */
	public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, AirDefense.id("bullet"));
	/** Fragments of a hand grenade. */
	public static final ResourceKey<DamageType> SHRAPNEL = ResourceKey.create(Registries.DAMAGE_TYPE, AirDefense.id("shrapnel"));

	private ModDamageTypes() {
	}

	public static DamageSource bullet(Level level, Entity shooter) {
		return level.damageSources().source(BULLET, shooter);
	}

	public static DamageSource shrapnel(Level level, Entity grenade, @Nullable Entity thrower) {
		return level.damageSources().source(SHRAPNEL, grenade, thrower);
	}
}
