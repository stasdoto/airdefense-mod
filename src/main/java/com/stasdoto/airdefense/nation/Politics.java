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
			Codec.INT.optionalFieldOf("next_id", 1).forGetter(p -> p.nextId)
	).apply(i, Politics::new));
	public static final SavedDataType<Politics> TYPE = new SavedDataType<>(AirDefense.id("politics"), Politics::new, CODEC, null);

	public final Map<Integer, Country> countries = new LinkedHashMap<>();
	public final Map<Integer, Settlement> settlements = new LinkedHashMap<>();
	private int nextId;

	public Politics() {
		this(List.of(), List.of(), 1);
	}

	private Politics(List<Country> countries, List<Settlement> settlements, int nextId) {
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
		return null;
	}

	/** The village whose area contains {@code pos} (the nearest, if areas overlap). */
	@Nullable
	public Settlement settlementAt(BlockPos pos) {
		Settlement best = null;
		double bestD = Double.MAX_VALUE;
		for (Settlement s : settlements.values()) {
			double d = pos.distSqr(s.center);
			if (d <= (double) Settlement.RADIUS * Settlement.RADIUS && d < bestD) {
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
