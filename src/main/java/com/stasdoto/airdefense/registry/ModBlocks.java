package com.stasdoto.airdefense.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.defense.DefenseBlock;
import com.stasdoto.airdefense.defense.DefenseType;
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
		Block block = Registry.register(BuiltInRegistries.BLOCK, ids.block(), factory.apply(props));
		Registry.register(BuiltInRegistries.ITEM, ids.item(),
				new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(ids.item())));
		return block;
	}

	public static void init() {
	}
}
