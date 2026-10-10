package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.stasdoto.airdefense.AirDefense;

/** The political map of the world (saved with it): every country and every village found so far. */
public final class Politics extends SavedData {
	public static final Codec<Politics> CODEC = RecordCodecBuilder.create(i -> i.group(
			Country.CODEC.listOf().optionalFieldOf("countries", List.of()).forGetter(p -> new ArrayList<>(p.countries.values())),
			Settlement.CODEC.listOf().optionalFieldOf("settlements", List.of()).forGetter(p -> new ArrayList<>(p.settlements.values())),
			Codec.INT.optionalFieldOf("next_id", 1).forGetter(p -> p.nextId),
			Codec.INT.listOf().optionalFieldOf("flags_pending", List.of()).forGetter(p -> new ArrayList<>(p.flagsPending)),
			Codec.INT.optionalFieldOf("styles", 0).forGetter(p -> p.styles),
			Codec.INT.optionalFieldOf("shapes", 0).forGetter(p -> p.shapes),
			net.minecraft.core.UUIDUtil.CODEC.listOf().optionalFieldOf("chosen", List.of()).forGetter(p -> new ArrayList<>(p.chosen))
	).apply(i, Politics::new));
	public static final SavedDataType<Politics> TYPE = new SavedDataType<>(AirDefense.id("politics"), Politics::new, CODEC, null);

	public final Map<Integer, Country> countries = new LinkedHashMap<>();
	public final Map<Integer, Settlement> settlements = new LinkedHashMap<>();
	/** Towns founded from afar (the atlas) whose flag goes up once somebody comes near. */
	public final java.util.Set<Integer> flagsPending = new java.util.HashSet<>();
	private int nextId;
	/** 1.43: the players who have chosen their country (or to go on their own) in this world. */
	public final java.util.Set<UUID> chosen = new java.util.HashSet<>();

	/**
	 * 1.28: 1 in worlds started with the town styles; worlds saved before them read 0 and keep the classic look (their
	 * towns are half built already).
	 */
	public final int styles;
	/**
	 * 1.35: 1 in worlds started with the town outlines (round, square...); older worlds read 0 and keep the blobs. 1.36: 2 in
	 * worlds started with the playgrounds and stadiums in the parks; 3 with the shelters; 4 (1.39) with the railways; 5 (1.40) with the airports; 6 (1.41) with the building sites.
	 */
	public final int shapes;

	public Politics() {
		this(List.of(), List.of(), 1, List.of(), 1, 6, List.of());
	}

	private Politics(List<Country> countries, List<Settlement> settlements, int nextId, List<Integer> pending, int styles, int shapes,
			List<UUID> chosen) {
		this.chosen.addAll(chosen);
		this.styles = styles;
		this.shapes = shapes;
		this.flagsPending.addAll(pending);
		for (Country c : countries) {
			this.countries.put(c.id, c);
		}
		for (Settlement s : settlements) {
			this.settlements.put(s.id, s);
		}
		this.nextId = nextId;
	}

	public static Politics get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public int newId() {
		setDirty();
		return nextId++;
	}

	@Nullable
	public Country country(int id) {
		return countries.get(id);
	}

	@Nullable
	public Country countryOf(Settlement s) {
		return s == null ? null : countries.get(s.country);
	}

	@Nullable
	public Country countryOwnedBy(UUID player) {
		for (Country c : countries.values()) {
			if (player.equals(c.owner)) {
				return c;
			}
		}
		for (Country c : countries.values()) {
			if (c.members.containsKey(player)) {
				return c;
			}
		}
		return null;
	}

	/** 1.43: the players of this country who are in the game now (the leader first). */
	public static List<net.minecraft.server.level.ServerPlayer> online(MinecraftServer server, @Nullable Country c) {
		List<net.minecraft.server.level.ServerPlayer> out = new ArrayList<>();
		if (c != null) {
			for (UUID u : c.everyone()) {
				net.minecraft.server.level.ServerPlayer pl = server.getPlayerList().getPlayer(u);
				if (pl != null) {
					out.add(pl);
				}
			}
		}
		return out;
	}

	/** The village whose area contains {@code pos} (the nearest, if areas overlap). */
	@Nullable
	public Settlement settlementAt(BlockPos pos) {
		Settlement best = null;
		double bestD = Double.MAX_VALUE;
		for (Settlement s : settlements.values()) {
			double d = pos.distSqr(s.center);
			if (d <= (double) s.radius * s.radius && d < bestD) {
				best = s;
				bestD = d;
			}
		}
		return best;
	}

	/** Villages within {@code range} of {@code pos} (for the map). */
	public List<Settlement> near(BlockPos pos, double range) {
		List<Settlement> out = new ArrayList<>();
		for (Settlement s : settlements.values()) {
			if (pos.distSqr(s.center) <= range * range) {
				out.add(s);
			}
		}
		return out;
	}

	public List<Settlement> settlementsOf(int country) {
		List<Settlement> out = new ArrayList<>();
		for (Settlement s : settlements.values()) {
			if (s.country == country) {
				out.add(s);
			}
		}
		return out;
	}
}
