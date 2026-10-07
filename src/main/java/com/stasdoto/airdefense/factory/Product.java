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
	F1_GRENADE("f1_grenade", 240, 1, 4, cost(Items.IRON_INGOT, 3, Items.TNT, 1)),
	// Stage R4: missiles and shells for the other air defence.
	SAM_SHORT("sam_short", 500, 1, cost(Items.IRON_INGOT, 6, Items.GUNPOWDER, 3, Items.REDSTONE, 3)),
	SAM_LONG("sam_long", 1000, 1, cost(Items.IRON_INGOT, 12, Items.GUNPOWDER, 6, Items.REDSTONE, 4, Items.GOLD_INGOT, 1)),
	AMMO_23("ammo_23_box", 200, 10, cost(Items.COPPER_INGOT, 6, Items.GUNPOWDER, 4)),
	AMMO_127("ammo_127_box", 160, 10, cost(Items.COPPER_INGOT, 4, Items.GUNPOWDER, 3)),
	// Stage R5: tank shells and autocannon rounds.
	SHELL_TANK("tank_shell", 300, 1, 2, cost(Items.IRON_INGOT, 3, Items.GUNPOWDER, 3, Items.COPPER_INGOT, 1)),
	AMMO_30("ammo_30_box", 200, 15, cost(Items.COPPER_INGOT, 5, Items.GUNPOWDER, 4, Items.IRON_NUGGET, 4)),
	// Stage R6: aircraft rockets and bombs.
	S8_ROCKETS("s8_rockets", 300, 8, cost(Items.IRON_INGOT, 4, Items.GUNPOWDER, 6, Items.COPPER_INGOT, 2)),
	FAB250("fab250", 400, 1, cost(Items.IRON_INGOT, 10, Items.TNT, 4)),
	// 1.24: ammunition of the new small arms and launchers (one item = one round).
	AMMO_556("ammo_556", 160, 1, 60, cost(Items.COPPER_INGOT, 4, Items.GUNPOWDER, 4, Items.IRON_NUGGET, 6)),
	AMMO_762X39("ammo_762x39", 170, 1, 60, cost(Items.COPPER_INGOT, 5, Items.GUNPOWDER, 5)),
	AMMO_9X39("ammo_9x39", 180, 1, 40, cost(Items.COPPER_INGOT, 4, Items.GUNPOWDER, 4, Items.IRON_INGOT, 1)),
	ROUND_127("ammo_127", 220, 1, 20, cost(Items.COPPER_INGOT, 6, Items.GUNPOWDER, 6, Items.IRON_INGOT, 2)),
	AMMO_12G("ammo_12g", 140, 1, 24, cost(Items.PAPER, 4, Items.GUNPOWDER, 4, Items.IRON_NUGGET, 8)),
	AMMO_40MM("ammo_40mm", 240, 1, 6, cost(Items.IRON_INGOT, 2, Items.GUNPOWDER, 3, Items.TNT, 1)),
	CG_ROUND("cg_round", 320, 1, 2, cost(Items.IRON_INGOT, 3, Items.GUNPOWDER, 3, Items.TNT, 1, Items.COPPER_INGOT, 2)),
	JAVELIN_MISSILE("javelin_missile", 700, 1, 1, cost(Items.IRON_INGOT, 6, Items.TNT, 2, Items.REDSTONE, 4, Items.GOLD_INGOT, 2));

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
			case PANTSIR, TOR, OSA, STRELA10, TUNGUSKA -> SAM_SHORT;
			case BUK, S300, SAMPT -> SAM_LONG;
			case SHILKA, ZU23 -> AMMO_23;
			case MFG -> AMMO_127;
			case AVENGER -> STINGER;
			case T72, T90, LEOPARD2, ABRAMS -> SHELL_TANK;
			case BMP2, BRADLEY, BTR82, BTR4, GYURZA -> AMMO_30;
			case M113, MAXXPRO, KOZAK, RAPTOR, RHIB, MI24 -> AMMO_127;
			case KA52, SU25, F16 -> AMMO_30;
			default -> null;
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
