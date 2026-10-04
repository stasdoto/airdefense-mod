package com.stasdoto.airdefense.client.nation;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityTypes;

import com.stasdoto.airdefense.nation.NationActionPayload;
import com.stasdoto.airdefense.nation.NationMapPayload;
import com.stasdoto.airdefense.nation.SettlementInfoPayload;
import com.stasdoto.airdefense.nation.VillageEconomyPayload;
import com.stasdoto.airdefense.registry.ModEntities;

/** Client side of the villages and countries: people's looks, the village screen, villages and soldiers on the map. */
public final class NationClient {
	private static NationMapPayload map;
	private static VillageEconomyPayload economy;

	private NationClient() {
	}

	public static void init() {
		EntityRenderers.register(ModEntities.SOLDIER, SoldierRenderer::new);
		EntityRenderers.register(ModEntities.WORKER, WorkerRenderer::new);
		// Villagers as people of every look (only the drawing changes).
		EntityRenderers.register(EntityTypes.VILLAGER, HumanVillagerRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(NationMapPayload.TYPE, (payload, context) -> map = payload);
		ClientPlayNetworking.registerGlobalReceiver(VillageEconomyPayload.TYPE, (payload, context) -> {
			economy = payload;
			if (context.client().gui.screen() instanceof SettlementScreen s && s.id() == payload.id()) {
				s.update(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SettlementInfoPayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof SettlementScreen s && s.id() == payload.id()) {
				s.update(payload);
			} else if (payload.open()) {
				mc.gui.setScreen(new SettlementScreen(payload, economy != null && economy.id() == payload.id() ? economy : null));
			}
		});
	}

	/** The last household received (for the village screen and the test). */
	public static VillageEconomyPayload economy() {
		return economy;
	}

	public static List<NationMapPayload.Village> villages() {
		NationMapPayload m = map;
		return m == null ? List.of() : m.villages();
	}

	public static List<NationMapPayload.Man> men() {
		NationMapPayload m = map;
		return m == null ? List.of() : m.men();
	}

	public static void send(int action, int settlement, int a, int x, int y, int z) {
		if (ClientPlayNetworking.canSend(NationActionPayload.TYPE)) {
			ClientPlayNetworking.send(new NationActionPayload(action, settlement, a, x, y, z));
		}
	}
}
