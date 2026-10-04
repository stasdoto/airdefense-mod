package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * A village as a political unit: where it is (its bell is the town square, the flag stands next to it), its name,
 * which country it belongs to (or none), its guards and soldiers, how much each player is respected there, and how
 * far a capture by force has got.
 */
public final class Settlement {
	public static final int RADIUS = 48;

	public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(s -> s.id),
			Codec.STRING.fieldOf("name").forGetter(s -> s.name),
			BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
			BlockPos.CODEC.fieldOf("flag").forGetter(s -> s.flag),
			Codec.INT.optionalFieldOf("country", -1).forGetter(s -> s.country),
			UUIDUtil.CODEC.optionalFieldOf("elder").forGetter(s -> Optional.ofNullable(s.elder)),
			Codec.INT.optionalFieldOf("population", 0).forGetter(s -> s.population),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("bonus", Map.of()).forGetter(s -> s.bonus),
			UUIDUtil.CODEC.listOf().optionalFieldOf("guards", List.of()).forGetter(s -> new ArrayList<>(s.guards)),
			UUIDUtil.CODEC.listOf().optionalFieldOf("soldiers", List.of()).forGetter(s -> new ArrayList<>(s.soldiers))
	).apply(i, Settlement::new));

	public final int id;
	public String name;
	/** The town square (the bell, or where the first villager was found). */
	public final BlockPos center;
	/** Where the flag (a banner in the country's colour) stands. */
	public BlockPos flag;
	public int country;
	public UUID elder;
	public int population;
	/** Respect earned on top of the villagers' own opinion (defending the village). */
	public final Map<UUID, Integer> bonus = new HashMap<>();
	public final List<UUID> guards = new ArrayList<>();
	/** Villagers called up from here (soldiers of the owner's army). */
	public final List<UUID> soldiers = new ArrayList<>();

	// Not saved: a capture in progress.
	public UUID capturer;
	public int captureTicks;
	public int guardsAlive;

	public Settlement(int id, String name, BlockPos center, BlockPos flag, int country, Optional<UUID> elder, int population,
			Map<UUID, Integer> bonus, List<UUID> guards, List<UUID> soldiers) {
		this.id = id;
		this.name = name;
		this.center = center;
		this.flag = flag;
		this.country = country;
		this.elder = elder.orElse(null);
		this.population = population;
		this.bonus.putAll(bonus);
		this.guards.addAll(guards);
		this.soldiers.addAll(soldiers);
	}

	public boolean contains(BlockPos pos) {
		return pos.distSqr(center) <= (double) RADIUS * RADIUS;
	}

	public boolean independent() {
		return country < 0;
	}
}
