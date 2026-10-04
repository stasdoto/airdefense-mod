package com.stasdoto.airdefense.registry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.defense.DefenseBlockEntity;
import com.stasdoto.airdefense.factory.FactoryBlockEntity;
import com.stasdoto.airdefense.launcher.LauncherBlockEntity;

public final class ModBlockEntities {
	public static final BlockEntityType<LauncherBlockEntity> LAUNCHER = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			AirDefense.id("launcher"),
			FabricBlockEntityTypeBuilder.<LauncherBlockEntity>create(LauncherBlockEntity::new,
					ModBlocks.ISKANDER_LAUNCHER, ModBlocks.KALIBR_LAUNCHER, ModBlocks.SHAHED_LAUNCHER, ModBlocks.HIMARS).build());

	public static final BlockEntityType<DefenseBlockEntity> DEFENSE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			AirDefense.id("defense"),
			FabricBlockEntityTypeBuilder.<DefenseBlockEntity>create(DefenseBlockEntity::new,
					ModBlocks.PATRIOT, ModBlocks.IRIS_T, ModBlocks.NASAMS, ModBlocks.GEPARD).build());

	public static final BlockEntityType<FactoryBlockEntity> FACTORY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			AirDefense.id("factory"),
			FabricBlockEntityTypeBuilder.<FactoryBlockEntity>create(FactoryBlockEntity::new, ModBlocks.FACTORY_CONTROLLER).build());

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
