package com.stasdoto.airdefense.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

import com.stasdoto.airdefense.client.fx.FxClient;
import com.stasdoto.airdefense.client.factory.FactoryClient;
import com.stasdoto.airdefense.client.map.MapClient;
import com.stasdoto.airdefense.client.vehicle.VehicleClient;
import com.stasdoto.airdefense.registry.ModEntities;

public class AirDefenseClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.MISSILE, MissileRenderer::new);
		FxClient.init();
		VehicleClient.init();
		MapClient.init();
		FactoryClient.init();
	}
}
