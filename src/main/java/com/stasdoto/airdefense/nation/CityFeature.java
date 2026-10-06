package com.stasdoto.airdefense.nation;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;

import com.stasdoto.airdefense.AirDefense;

/**
 * The world generation hook for the cities: placed once in every overworld chunk, last of all (after trees and snow),
 * it hands the chunk to {@link CityGen}.
 */
public record CityFeature() implements Feature {
	public static final MapCodec<CityFeature> CODEC = MapCodec.unit(CityFeature::new);
	/** Off for the tests that want a bare world. */
	public static volatile boolean enabled = true;

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, AirDefense.id("cities"), CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
				ResourceKey.create(Registries.PLACED_FEATURE, AirDefense.id("cities")));
	}

	@Override
	public MapCodec<CityFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!enabled || level.getLevel().dimension() != Level.OVERWORLD) {
			return false;
		}
		try {
			CityGen.generate(level, Cities.terrain(level.getLevel()), level.getSeed(), ChunkPos.containing(origin));
		} catch (RuntimeException e) {
			AirDefense.LOGGER.error("[airdefense] city generation failed at {}", origin, e);
		}
		return true;
	}
}
