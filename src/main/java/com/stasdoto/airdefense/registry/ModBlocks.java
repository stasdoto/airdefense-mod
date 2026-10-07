package com.stasdoto.airdefense.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.defense.DefenseBlock;
import com.stasdoto.airdefense.defense.DefenseType;
import com.stasdoto.airdefense.factory.FactoryControllerBlock;
import com.stasdoto.airdefense.launcher.LauncherBlock;
import com.stasdoto.airdefense.launcher.LauncherType;

public final class ModBlocks {
	public static final Block ISKANDER_LAUNCHER = register("iskander_launcher", p -> new LauncherBlock(LauncherType.ISKANDER, p), MapColor.COLOR_GREEN);
	public static final Block KALIBR_LAUNCHER = register("kalibr_launcher", p -> new LauncherBlock(LauncherType.KALIBR, p), MapColor.COLOR_GRAY);
	public static final Block SHAHED_LAUNCHER = register("shahed_launcher", p -> new LauncherBlock(LauncherType.SHAHED, p), MapColor.SAND);
	public static final Block HIMARS = register("himars", p -> new LauncherBlock(LauncherType.HIMARS, p), MapColor.COLOR_GREEN);

	public static final Block PATRIOT = register("patriot", p -> new DefenseBlock(DefenseType.PATRIOT, p), MapColor.SAND);
	public static final Block IRIS_T = register("iris_t", p -> new DefenseBlock(DefenseType.IRIS_T, p), MapColor.COLOR_GREEN);
	public static final Block NASAMS = register("nasams", p -> new DefenseBlock(DefenseType.NASAMS, p), MapColor.COLOR_GREEN);
	public static final Block GEPARD = register("gepard", p -> new DefenseBlock(DefenseType.GEPARD, p), MapColor.COLOR_GREEN);

	/** 1.24: the air raid siren on its pole (its red lamp lights while it sounds). */
	public static final Block SIREN = registerSiren();

	/** The factory's control desk (placed by the factory kit; it has an item so it can be picked up and put back). */
	public static final Block FACTORY_CONTROLLER = register("factory_controller", FactoryControllerBlock::new, MapColor.METAL);

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, MapColor color) {
		Identifier id = AirDefense.id(name);
		BlockItemId ids = BlockItemId.create(id, id);
		BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(5.0f, 9.0f)
				.sound(SoundType.METAL)
				.noOcclusion()
				.setId(ids.block());
		// Old one-block launchers: kept so existing worlds load; each turns into a real vehicle on its first tick.
		// Their item ids now belong to the vehicle items (see ModItems).
		return Registry.register(BuiltInRegistries.BLOCK, ids.block(), factory.apply(props));
	}

	private static Block registerSiren() {
		Identifier id = AirDefense.id("siren");
		BlockItemId ids = BlockItemId.create(id, id);
		BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.strength(3.0f, 6.0f)
				.sound(SoundType.METAL)
				.noOcclusion()
				.lightLevel(st -> st.getValue(com.stasdoto.airdefense.siren.SirenBlock.SIGNAL) == com.stasdoto.airdefense.siren.SirenBlock.Signal.OFF ? 0 : 6)
				.setId(ids.block());
		return Registry.register(BuiltInRegistries.BLOCK, ids.block(), new com.stasdoto.airdefense.siren.SirenBlock(props));
	}

	public static void init() {
	}
}
