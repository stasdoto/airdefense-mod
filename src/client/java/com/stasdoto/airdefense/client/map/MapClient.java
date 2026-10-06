package com.stasdoto.airdefense.client.map;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.map.MapActionPayload;
import com.stasdoto.airdefense.map.MapStatusPayload;

/** Client side of the tablet map: opening it, the latest vehicle situation from the server, sending orders. */
public final class MapClient {
	private static MapStatusPayload latest;

	private MapClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MapStatusPayload.TYPE, (payload, context) -> latest = payload);
		ClientPlayNetworking.registerGlobalReceiver(com.stasdoto.airdefense.map.RadarPayload.TYPE, (payload, context) -> RadarScreen.receive(payload));
		DesignatorItem.openMap = MapClient::open;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null) {
				latest = null;
			}
			MapCache.tick(mc);
		});
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.gui.setScreen(new TacticalMapScreen());
		}
	}

	/** Vehicles as of the last answer from the server (refreshed twice a second while the map is open). */
	public static List<MapStatusPayload.Entry> vehicles() {
		MapStatusPayload p = latest;
		return p == null ? List.of() : p.vehicles();
	}

	public static void send(MapActionPayload payload) {
		if (ClientPlayNetworking.canSend(MapActionPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
		}
	}
}
