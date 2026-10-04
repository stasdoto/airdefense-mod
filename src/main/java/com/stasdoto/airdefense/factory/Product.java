package com.stasdoto.airdefense.factory;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * What a factory makes: missiles, drones, interceptors, rockets and 35 mm ammunition. Each costs ordinary resources
 * and takes time (a creative player's factory works for free and five times faster). {@code units} = how many shots
 * one item gives a vehicle (an ammunition box holds ten Gepard bursts).
 */
public enum Product {
	ISKANDER("iskander_missile", 1200, 1, cost(Items.IRON_INGOT, 16, Items.GUNPOWDER, 8, Items.REDSTONE, 4, Items.GOLD_INGOT, 2)),
	KALIBR("kalibr_missile", 900, 1, cost(Items.IRON_INGOT, 12, Items.GUNPOWDER, 6, Items.REDSTONE, 4, Items.COPPER_INGOT, 4)),
	SHAHED("shahed_drone", 600, 1, cost(Items.IRON_INGOT, 4, Items.COPPER_INGOT, 4, Items.GUNPOWDER, 4, Items.REDSTONE, 2)),
	GMLRS("gmlrs_rocket", 300, 1, cost(Items.IRON_INGOT, 4, Items.GUNPOWDER, 3, Items.REDSTONE, 1)),
	PAC3("pac3_missile", 800, 1, cost(Items.IRON_INGOT, 8, Items.GUNPOWDER, 4, Items.REDSTONE, 4, Items.DIAMOND, 1)),
	IRIST("irist_missile", 600, 1, cost(Items.IRON_INGOT, 6, Items.GUNPOWDER, 3, Items.REDSTONE, 3, Items.GOLD_INGOT, 1)),
	AMRAAM("amraam_missile", 500, 1, cost(Items.IRON_INGOT, 6, Items.GUNPOWDER, 3, Items.REDSTONE, 3)),
	STINGER("stinger_missile", 300, 1, cost(Items.IRON_INGOT, 3, Items.GUNPOWDER, 2, Items.REDSTONE, 1)),
	GEPARD_AMMO("gepard_ammo", 200, 10, cost(Items.COPPER_INGOT, 6, Items.GUNPOWDER, 4)),
	// Small arms (stage 7): one order makes a batch of rounds (one item = one round).
	AMMO_545("ammo_545", 160, 1, 60, cost(Items.COPPER_INGOT, 4, Items.GUNPOWDER, 4, Items.IRON_NUGGET, 4)),
	AMMO_762("ammo_762", 200, 1, 60, cost(Items.COPPER_INGOT, 4, Items.GUNPOWDER, 6, Items.IRON_NUGGET, 4)),
	AMMO_9MM("ammo_9mm", 120, 1, 64, cost(Items.COPPER_INGOT, 3, Items.GUNPOWDER, 3)),
	RPG_ROUND("rpg_round", 300, 1, 2, cost(Items.IRON_INGOT, 2, Items.GUNPOWDER, 3, Items.TNT, 1)),
	F1_GRENADE("f1_grenade", 240, 1, 4, cost(Items.IRON_INGOT, 3, Items.TNT, 1));

	public record Cost(Item item, int count) {
	}

	public final String itemId;
	public final int ticks;
	public final int units;
	/** Items one order makes (ammunition comes in batches). */
	public final int batch;
	public final List<Cost> cost;

	Product(String itemId, int ticks, int units, List<Cost> cost) {
		this(itemId, ticks, units, 1, cost);
	}

	Product(String itemId, int ticks, int units, int batch, List<Cost> cost) {
		this.itemId = itemId;
		this.ticks = ticks;
		this.units = units;
		this.batch = batch;
		this.cost = cost;
	}

	public Item item() {
		return BuiltInRegistries.ITEM.getValue(AirDefense.id(itemId));
	}

	public int time(boolean creative) {
		return creative ? Math.max(20, ticks / 5) : ticks;
	}

	/** What this vehicle fires (and so what restocks it), or null. */
	@Nullable
	public static Product forVehicle(VehicleType type) {
		return switch (type) {
			case ISKANDER -> ISKANDER;
			case KALIBR -> KALIBR;
			case SHAHED -> SHAHED;
			case HIMARS -> GMLRS;
			case PATRIOT -> PAC3;
			case IRIS_T -> IRIST;
			case NASAMS -> AMRAAM;
			case GEPARD -> GEPARD_AMMO;
		};
	}

	@Nullable
	public static Product forItem(Item item) {
		for (Product p : values()) {
			if (p.item() == item) {
				return p;
			}
		}
		return null;
	}

	public static Product byId(int id) {
		Product[] all = values();
		return id >= 0 && id < all.length ? all[id] : ISKANDER;
	}

	private static List<Cost> cost(Object... pairs) {
		Cost[] out = new Cost[pairs.length / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = new Cost((Item) pairs[i * 2], (Integer) pairs[i * 2 + 1]);
		}
		return List.of(out);
	}
}
