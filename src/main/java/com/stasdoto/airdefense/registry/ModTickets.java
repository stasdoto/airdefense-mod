package com.stasdoto.airdefense.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

import com.stasdoto.airdefense.AirDefense;

/** Chunk tickets of the mod. */
public final class ModTickets {
	/**
	 * A vehicle keeps the chunk it stands in loaded and running, so it can be seen and given orders on the tablet map
	 * and its air defence keeps guarding while the player is far away. Renewed every second while the vehicle lives,
	 * saved with the world (so vehicles wake up again after a restart), runs out 10 s after the vehicle is gone.
	 */
	public static final TicketType VEHICLE = Registry.register(BuiltInRegistries.TICKET_TYPE, AirDefense.id("vehicle"),
			new TicketType(200L, TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));

	private ModTickets() {
	}

	public static void init() {
	}
}
