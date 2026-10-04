package com.stasdoto.airdefense.registry;

import java.util.EnumMap;
import java.util.Map;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

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

	private static final Map<VehicleType, EntityType<VehicleEntity>> VEHICLES = new EnumMap<>(VehicleType.class);

	static {
		for (VehicleType type : VehicleType.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id(type.id));
			EntityType<VehicleEntity> entityType = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
					EntityType.Builder.<VehicleEntity>of((t, level) -> new VehicleEntity(t, level, type), MobCategory.MISC)
							.sized(type.boxWidth, type.boxHeight)
							.eyeHeight(type.boxHeight * 0.8f)
							.clientTrackingRange(16)
							.updateInterval(1)
							.fireImmune()
							.noLootTable()
							.build(key));
			VEHICLES.put(type, entityType);
		}
	}

	private ModEntities() {
	}

	public static EntityType<VehicleEntity> vehicle(VehicleType type) {
		return VEHICLES.get(type);
	}

	public static void init() {
		for (VehicleType type : VehicleType.values()) {
			FabricDefaultAttributeRegistry.register(vehicle(type), VehicleEntity.createAttributes(type));
		}
	}
}
