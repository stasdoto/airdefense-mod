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
import com.stasdoto.airdefense.registry.ModTickets;
import com.stasdoto.airdefense.map.MapServer;
import com.stasdoto.airdefense.factory.FactoryNet;
import com.stasdoto.airdefense.vehicle.VehicleActionPayload;
import com.stasdoto.airdefense.registry.ModComponents;
import com.stasdoto.airdefense.weapon.GunServer;

public class AirDefense implements ModInitializer {
	public static final String MOD_ID = "airdefense";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModComponents.init();
		ModTickets.init();
		com.stasdoto.airdefense.util.Later.init();
		com.stasdoto.airdefense.util.Perf.init();
		com.stasdoto.airdefense.fx.Smoke.init();
		com.stasdoto.airdefense.gear.GearServer.init();
		com.stasdoto.airdefense.nation.Repairs.init();
		ModParticles.init();
		PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
		VehicleActionPayload.register();
		ModEntities.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModItems.init();
		ModCommands.init();
		MapServer.init();
		com.stasdoto.airdefense.drone.DroneNet.init();
		FactoryNet.init();
		GunServer.init();
		com.stasdoto.airdefense.siren.SirenNet.init();
		com.stasdoto.airdefense.nation.Nations.init();
		com.stasdoto.airdefense.nation.CityFeature.init();
		com.stasdoto.airdefense.nation.Atlas.init();
		com.stasdoto.airdefense.guide.Guide.init();
		LOGGER.info("Stasdoto Air Defense loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
