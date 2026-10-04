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
			Codec.INT.optionalFieldOf("births", 0).forGetter(e -> e.births)
	).apply(i, (wood, stone, iron, buildings, workers, hangar, progress, births) -> {
		VillageEconomy e = new VillageEconomy();
		e.stock[WOOD] = wood;
		e.stock[STONE] = stone;
		e.stock[IRON] = iron;
		e.buildings.addAll(buildings);
		e.workers.addAll(workers);
		e.hangar.addAll(hangar);
		e.hangarProgress = progress;
		e.births = births;
		return e;
	}));

	public final int[] stock = new int[3];
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
		return stock[0] == 0 && stock[1] == 0 && stock[2] == 0 && buildings.isEmpty() && workers.isEmpty() && hangar.isEmpty();
	}
}
