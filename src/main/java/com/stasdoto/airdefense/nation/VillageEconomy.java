package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A village's household: its store of wood, stone and iron, its buildings (built and ordered), the people sent to
 * work, and what the hangar is making.
 */
public final class VillageEconomy {
	public static final int WOOD = 0;
	public static final int STONE = 1;
	public static final int IRON = 2;
	/** Crude oil and fuel in litres, ammunition in points (stage R7). */
	public static final int OIL = 3;
	public static final int FUEL = 4;
	public static final int AMMO = 5;
	/** Food (from the farms and the food plant) and weapons (from the arms factory), stage 1.23. */
	public static final int FOOD = 6;
	public static final int ARMS = 7;
	public static final int KINDS = 8;
	/** How much the village keeps of each without a warehouse, and how much more each warehouse holds. */
	public static final int BASE_CAP = 300;
	public static final int WAREHOUSE_CAP = 600;

	public static final Codec<VillageEconomy> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("wood", 0).forGetter(e -> e.stock[WOOD]),
			Codec.INT.optionalFieldOf("stone", 0).forGetter(e -> e.stock[STONE]),
			Codec.INT.optionalFieldOf("iron", 0).forGetter(e -> e.stock[IRON]),
			Building.CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(e -> new ArrayList<>(e.buildings)),
			UUIDUtil.CODEC.listOf().optionalFieldOf("workers", List.of()).forGetter(e -> new ArrayList<>(e.workers)),
			Codec.INT.listOf().optionalFieldOf("hangar", List.of()).forGetter(e -> new ArrayList<>(e.hangar)),
			Codec.INT.optionalFieldOf("hangar_progress", 0).forGetter(e -> e.hangarProgress),
			Codec.INT.optionalFieldOf("births", 0).forGetter(e -> e.births),
			Codec.INT.listOf().optionalFieldOf("liquids", List.of()).forGetter(e -> List.of(e.stock[OIL], e.stock[FUEL], e.stock[AMMO])),
			Codec.INT.listOf().optionalFieldOf("goods", List.of()).forGetter(e -> List.of(e.stock[FOOD], e.stock[ARMS], e.hungry))
	).apply(i, (wood, stone, iron, buildings, workers, hangar, progress, births, liquids, goods) -> {
		VillageEconomy e = new VillageEconomy();
		e.stock[WOOD] = wood;
		e.stock[STONE] = stone;
		e.stock[IRON] = iron;
		e.buildings.addAll(buildings);
		e.workers.addAll(workers);
		e.hangar.addAll(hangar);
		e.hangarProgress = progress;
		e.births = births;
		for (int k = 0; k < Math.min(3, liquids.size()); k++) {
			e.stock[OIL + k] = liquids.get(k);
		}
		if (goods.size() >= 2) {
			e.stock[FOOD] = goods.get(0);
			e.stock[ARMS] = goods.get(1);
		}
		if (goods.size() >= 3) {
			e.hungry = goods.get(2);
		}
		return e;
	}));

	public final int[] stock = new int[KINDS];
	/** Minutes in a row the people have gone short of food (0 = fed). */
	public int hungry;
	public final List<Building> buildings = new ArrayList<>();
	/** Villagers sent to work (they are {@link WorkerEntity}s while at it). */
	public final List<UUID> workers = new ArrayList<>();
	/** Vehicles ordered from the hangar (vehicle type ordinals), the first is being made. */
	public final List<Integer> hangar = new ArrayList<>();
	public int hangarProgress;
	/** Babies born in the maternity hospital so far. */
	public int births;

	/** How much of each the village can keep. */
	public int cap() {
		return BASE_CAP + WAREHOUSE_CAP * count(BuildingType.WAREHOUSE);
	}

	/** Litres of oil or fuel it can hold: tanks come with the refineries, hubs and gas stations. */
	public int liquidCap() {
		return 4000 + 8000 * (count(BuildingType.REFINERY) + count(BuildingType.LOGISTICS_HUB) + count(BuildingType.GAS_STATION));
	}

	public int ammoCap() {
		return 300 + 600 * count(BuildingType.LOGISTICS_HUB);
	}

	public int foodCap() {
		return cap() + 400 * (count(BuildingType.FARM) + count(BuildingType.FOOD_PLANT) + count(BuildingType.MARKET));
	}

	public int armsCap() {
		return 100 + 300 * (count(BuildingType.ARMS_FACTORY) + count(BuildingType.BARRACKS));
	}

	public int capOf(int kind) {
		return switch (kind) {
			case WOOD, STONE, IRON -> cap();
			case AMMO -> ammoCap();
			case FOOD -> foodCap();
			case ARMS -> armsCap();
			default -> liquidCap();
		};
	}

	/** Finished buildings of this type. */
	public int count(BuildingType type) {
		int n = 0;
		for (Building b : buildings) {
			if (b.done && b.type == type) {
				n++;
			}
		}
		return n;
	}

	/** The first building in line that is not finished (the one going up now). */
	@Nullable
	public Building active() {
		for (Building b : buildings) {
			if (!b.done) {
				return b;
			}
		}
		return null;
	}

	public int queued() {
		int n = 0;
		for (Building b : buildings) {
			if (!b.done) {
				n++;
			}
		}
		return n;
	}

	@Nullable
	public Building first(BuildingType type) {
		for (Building b : buildings) {
			if (b.done && b.type == type) {
				return b;
			}
		}
		return null;
	}

	public boolean isEmpty() {
		return java.util.Arrays.stream(stock).allMatch(v -> v == 0) && hungry == 0 && buildings.isEmpty() && workers.isEmpty() && hangar.isEmpty();
	}
}
