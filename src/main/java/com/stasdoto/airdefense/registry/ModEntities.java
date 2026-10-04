package com.stasdoto.airdefense.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileEntity;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> MISSILE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id("missile"));

	public static final EntityType<MissileEntity> MISSILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, MISSILE_KEY,
			EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
					.sized(0.7f, 0.7f)
					.clientTrackingRange(24)
					.updateInterval(1)
					.fireImmune()
					.noSave()
					.noSummon()
					.build(MISSILE_KEY));

	private ModEntities() {
	}

	public static void init() {
	}
}
