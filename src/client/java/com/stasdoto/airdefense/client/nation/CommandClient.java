package com.stasdoto.airdefense.client.nation;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.stasdoto.airdefense.client.map.AtlasClient;
import com.stasdoto.airdefense.nation.AtlasPoliticsPayload;
import com.stasdoto.airdefense.nation.CommandActionPayload;
import com.stasdoto.airdefense.nation.CommandStatePayload;

/** 2.0: the client side of commanding any country from the map (see the server's Command). */
public final class CommandClient {
	/** The last state the server sent (null before the first). */
	@Nullable
	private static volatile CommandStatePayload state;

	private CommandClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CommandStatePayload.TYPE, (payload, context) -> state = payload);
		// A new world (or server) starts with nobody commanded.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state = null);
	}

	@Nullable
	public static CommandStatePayload state() {
		return state;
	}

	/** May the player command other countries (as the server last said). */
	public static boolean allowed() {
		CommandStatePayload s = state;
		return s != null && s.allowed();
	}

	/** The country commanded now, or -1. */
	public static int commanded() {
		CommandStatePayload s = state;
		return s == null ? -1 : s.country();
	}

	public static boolean atWar(int other) {
		CommandStatePayload s = state;
		return s != null && s.wars().contains(other);
	}

	public static void send(int action, int country, int a, int b, int x, int z) {
		if (ClientPlayNetworking.canSend(CommandActionPayload.TYPE)) {
			ClientPlayNetworking.send(new CommandActionPayload(action, country, a, b, x, z));
		}
	}

	/** The country a town (a village on the map) belongs to, from the atlas's politics; -1 if not known. */
	public static int countryOfTown(int town) {
		AtlasPoliticsPayload p = AtlasClient.politics;
		if (p == null) {
			return -1;
		}
		for (AtlasPoliticsPayload.Town t : p.towns()) {
			if (t.id() == town) {
				return t.country();
			}
		}
		return -1;
	}

	/** A country's name and colour from the atlas (null if not known). */
	@Nullable
	public static AtlasPoliticsPayload.Country country(int id) {
		return id < 0 ? null : AtlasClient.country(id);
	}
}
