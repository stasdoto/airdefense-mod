package com.stasdoto.airdefense;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stasdoto.airdefense.command.ModCommands;
import com.stasdoto.airdefense.fx.FxPayload;
import com.stasdoto.airdefense.registry.ModBlockEntities;
import com.stasdoto.airdefense.registry.ModBlocks;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModItems;
import com.stasdoto.airdefense.registry.ModParticles;
import com.stasdoto.airdefense.registry.ModSounds;

public class AirDefense implements ModInitializer {
	public static final String MOD_ID = "airdefense";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModParticles.init();
		PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
		ModEntities.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModItems.init();
		ModCommands.init();
		LOGGER.info("Stasdoto Air Defense loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
