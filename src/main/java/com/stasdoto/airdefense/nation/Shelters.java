package com.stasdoto.airdefense.nation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

import com.stasdoto.airdefense.siren.Sirens;

/**
 * 1.37: in a town on alert the people take cover - its villagers hide indoors as when the village bell rings (and come
 * out again a little after the all clear). The passers-by on the client hurry away at the same time. Looked at every five
 * seconds, and only while some town is on alert.
 */
public final class Shelters {
	/** For the tests: villagers sent to cover so far. */
	public static int hidden;

	private Shelters() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 != 17) {
				return;
			}
			ServerLevel level = server.overworld();
			Sirens sirens;
			try {
				sirens = Sirens.get(server);
			} catch (RuntimeException e) {
				return;
			}
			if (!sirens.everywhere && sirens.alert.isEmpty() && sirens.autoUntil.isEmpty()) {
				return;
			}
			long now = level.getGameTime();
			for (Settlement s : Politics.get(server).settlements.values()) {
				if (sirens.signalFor(now, s.id, Sirens.MODE_AUTO) != Sirens.ALERT || !level.isLoaded(s.center)) {
					continue;
				}
				for (Villager v : Nations.villagers(level, s)) {
					v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
					hidden++;
				}
			}
		});
	}
}
