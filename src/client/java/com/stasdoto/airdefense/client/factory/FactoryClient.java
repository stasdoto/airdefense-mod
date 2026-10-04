package com.stasdoto.airdefense.client.factory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import com.stasdoto.airdefense.factory.FactoryStatusPayload;

/** Opens and updates the factory menu when the server sends the factory's state. */
public final class FactoryClient {
	private FactoryClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(FactoryStatusPayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof FactoryScreen screen) {
				screen.update(payload);
			} else if (payload.open()) {
				mc.gui.setScreen(new FactoryScreen(payload));
			}
		});
	}
}
