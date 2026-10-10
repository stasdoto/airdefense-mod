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
	/**
	 * 1.32.2: "no planned city / hamlet". The keys of the cities west of x = 0 are negative: with -1 for "none" (and
	 * "city >= 0" tests) such a city was taken for no city at all - founded again every few seconds, each time with its
	 * flag, sirens, arsenal and guards, until the towns there drowned in vehicles and soldiers.
	 */
	public static final long NONE = Long.MIN_VALUE;

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
			UUIDUtil.CODEC.listOf().optionalFieldOf("soldiers", List.of()).forGetter(s -> new ArrayList<>(s.soldiers)),
			VillageEconomy.CODEC.optionalFieldOf("economy").forGetter(s -> s.eco.isEmpty() ? Optional.empty() : Optional.of(s.eco)),
			Codec.LONG.optionalFieldOf("captured_at", -1L).forGetter(s -> s.capturedAt),
			Codec.LONG.optionalFieldOf("calm_until", 0L).forGetter(s -> s.calmUntil),
			Codec.BOOL.optionalFieldOf("riot", false).forGetter(s -> s.riot),
			Codec.LONG.listOf().optionalFieldOf("city", List.of()).forGetter(s -> s.city == NONE ? List.of() : List.of(s.city, (long) s.radius,
					(long) s.citizens, s.capitalCity ? 1L : 0L, (long) s.style)),
			// (Before 1.32.2 -1 stood for "none".)
			Codec.LONG.optionalFieldOf("hamlet", NONE).forGetter(s -> s.hamlet)
	).apply(i, (id, name, center, flag, country, elder, population, bonus, guards, soldiers, eco, capturedAt, calmUntil, riot, city, hamlet) -> {
		Settlement s = new Settlement(id, name, center, flag, country, elder, population, bonus, guards, soldiers, eco);
		s.capturedAt = capturedAt;
		s.calmUntil = calmUntil;
		s.riot = riot;
		s.hamlet = hamlet == -1L ? NONE : hamlet;
		if (city.size() >= 4) {
			s.city = city.get(0);
			s.radius = (int) (long) city.get(1);
			s.citizens = (int) (long) city.get(2);
			s.capitalCity = city.get(3) != 0;
			// 1.28: the town's style (absent in older worlds: the classic look).
			s.style = city.size() >= 5 ? (int) (long) city.get(4) : 0;
		}
		return s;
	}));

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
	/** Store, buildings, workers, hangar. */
	public final VillageEconomy eco;

	/** When it was taken by force (game time, -1 = not lately): its people resent the new ruler for a while. */
	public long capturedAt = -1;
	/** Until when gifts (or a put-down riot) keep the people quiet. */
	public long calmUntil;
	/** A riot is going on. */
	public boolean riot;
	/** A planned city ({@link Cities.City#key()}), or {@link #NONE} for a village found in the wild. */
	public long city = NONE;
	/** A planned hamlet round a city ({@link Hamlets.Hamlet#key()}), or {@link #NONE}. */
	public long hamlet = NONE;
	/** How far the settlement reaches. */
	public int radius = RADIUS;
	/** People living here (cities: a number; only some of them walk the streets). */
	public int citizens;
	/** The capital its country was planned around. */
	public boolean capitalCity;
	/** 1.28: the town's {@link CityStyle} (0 = classic), for the buildings ordered from its screen. */
	public int style;

	// Not saved: a capture in progress, how long rebels have held the flag.
	public int riotTicks;
	/** Seconds enemy soldiers have held the flag (of a player's village) with nobody to stop them. */
	public int aiCaptureTicks;
	public UUID capturer;
	public int captureTicks;
	public int guardsAlive;

	public Settlement(int id, String name, BlockPos center, BlockPos flag, int country, Optional<UUID> elder, int population,
			Map<UUID, Integer> bonus, List<UUID> guards, List<UUID> soldiers) {
		this(id, name, center, flag, country, elder, population, bonus, guards, soldiers, Optional.empty());
	}

	public Settlement(int id, String name, BlockPos center, BlockPos flag, int country, Optional<UUID> elder, int population,
			Map<UUID, Integer> bonus, List<UUID> guards, List<UUID> soldiers, Optional<VillageEconomy> eco) {
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
		this.eco = eco.orElseGet(VillageEconomy::new);
	}

	public boolean contains(BlockPos pos) {
		return pos.distSqr(center) <= (double) radius * radius;
	}

	/** 1.46: a district of a great city (not its centre): a town of its own, but a lighter garrison. */
	public boolean isDistrict() {
		return isCity() && Cities.metro && Math.floorMod(city, 64L) >= Cities.FIRST_DISTRICT;
	}

	public boolean isCity() {
		return city != NONE;
	}

	public boolean isHamlet() {
		return hamlet != NONE;
	}

	/** People to show: the city's number, or the villagers counted around. */
	public int people() {
		return Math.max(population, citizens);
	}

	public boolean independent() {
		return country < 0;
	}
}
