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
import com.stasdoto.airdefense.weapon.GrenadeEntity;
import com.stasdoto.airdefense.nation.SoldierEntity;
import com.stasdoto.airdefense.nation.WorkerEntity;

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

	private static final ResourceKey<EntityType<?>> GRENADE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id("grenade"));

	public static final EntityType<GrenadeEntity> GRENADE = Registry.register(BuiltInRegistries.ENTITY_TYPE, GRENADE_KEY,
			EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(8)
					.updateInterval(2)
					.build(GRENADE_KEY));

	private static final ResourceKey<EntityType<?>> SOLDIER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id("soldier"));

	/** Guards, soldiers and bandits (people with guns). */
	public static final EntityType<SoldierEntity> SOLDIER = Registry.register(BuiltInRegistries.ENTITY_TYPE, SOLDIER_KEY,
			EntityType.Builder.<SoldierEntity>of(SoldierEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.build(SOLDIER_KEY));

	private static final ResourceKey<EntityType<?>> WORKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id("worker"));

	/** Villagers at work (gathering or building). */
	public static final EntityType<WorkerEntity> WORKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, WORKER_KEY,
			EntityType.Builder.<WorkerEntity>of(WorkerEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.build(WORKER_KEY));

	private static final ResourceKey<EntityType<?>> PEDESTRIAN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id("pedestrian"));

	/** 1.37: passers-by in the towns (made only on the client, round the player). */
	public static final EntityType<com.stasdoto.airdefense.nation.PedestrianEntity> PEDESTRIAN = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			PEDESTRIAN_KEY, EntityType.Builder.<com.stasdoto.airdefense.nation.PedestrianEntity>of(com.stasdoto.airdefense.nation.PedestrianEntity::new,
					MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(4).noSave().noSummon().build(PEDESTRIAN_KEY));

	private static final ResourceKey<EntityType<?>> TRAIN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id("train"));

	/** 1.39: trains on the railways (made only on the client, round the player). */
	public static final EntityType<com.stasdoto.airdefense.nation.TrainEntity> TRAIN = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			TRAIN_KEY, EntityType.Builder.<com.stasdoto.airdefense.nation.TrainEntity>of(com.stasdoto.airdefense.nation.TrainEntity::new,
					MobCategory.MISC).sized(3f, 4f).clientTrackingRange(16).noSave().noSummon().build(TRAIN_KEY));

	private static final ResourceKey<EntityType<?>> PLANE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, AirDefense.id("airliner"));

	/** 1.40: the airliners at the airports (made only on the client, round the player). */
	public static final EntityType<com.stasdoto.airdefense.nation.PlaneEntity> PLANE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			PLANE_KEY, EntityType.Builder.<com.stasdoto.airdefense.nation.PlaneEntity>of(com.stasdoto.airdefense.nation.PlaneEntity::new,
					MobCategory.MISC).sized(4f, 4f).clientTrackingRange(16).noSave().noSummon().build(PLANE_KEY));

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
		FabricDefaultAttributeRegistry.register(SOLDIER, SoldierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WORKER, WorkerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PEDESTRIAN, com.stasdoto.airdefense.nation.PedestrianEntity.createAttributes());
		for (VehicleType type : VehicleType.values()) {
			FabricDefaultAttributeRegistry.register(vehicle(type), VehicleEntity.createAttributes(type));
		}
	}
}
