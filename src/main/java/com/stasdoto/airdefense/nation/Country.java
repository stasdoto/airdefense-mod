package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.DyeColor;

/**
 * A country: a name, a colour (its flag, uniforms and its area on the map), an owner (a player, or nobody for a
 * country the world made up), a capital, and the countries it is at war with.
 */
public final class Country {
	public static final Codec<Country> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(c -> c.id),
			Codec.STRING.fieldOf("name").forGetter(c -> c.name),
			Codec.INT.fieldOf("color").forGetter(c -> c.color),
			UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(c -> Optional.ofNullable(c.owner)),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(c -> c.ownerName),
			Codec.INT.optionalFieldOf("capital", -1).forGetter(c -> c.capital),
			Codec.BOOL.optionalFieldOf("city_state", false).forGetter(c -> c.cityState),
			Codec.INT.listOf().optionalFieldOf("wars", List.of()).forGetter(c -> new ArrayList<>(c.wars)),
			UUIDUtil.CODEC.listOf().optionalFieldOf("wanted", List.of()).forGetter(c -> new ArrayList<>(c.wanted))
	).apply(i, Country::new));

	public final int id;
	public String name;
	/** A {@link DyeColor} id: flags are vanilla banners of this colour. */
	public int color;
	public UUID owner;
	public String ownerName;
	public int capital;
	/** A one-village country. */
	public boolean cityState;
	public final Set<Integer> wars = new HashSet<>();
	/** Players who shot at this country's people: its guards shoot them on sight. */
	public final Set<UUID> wanted = new HashSet<>();

	public Country(int id, String name, int color, Optional<UUID> owner, String ownerName, int capital, boolean cityState, List<Integer> wars) {
		this(id, name, color, owner, ownerName, capital, cityState, wars, List.of());
	}

	public Country(int id, String name, int color, Optional<UUID> owner, String ownerName, int capital, boolean cityState, List<Integer> wars,
			List<UUID> wanted) {
		this.id = id;
		this.name = name;
		this.color = color;
		this.owner = owner.orElse(null);
		this.ownerName = ownerName;
		this.capital = capital;
		this.cityState = cityState;
		this.wars.addAll(wars);
		this.wanted.addAll(wanted);
	}

	public DyeColor dye() {
		return DyeColor.byId(color);
	}

	/** Colour for the map and the HUD (ARGB). */
	public int argb() {
		return 0xFF000000 | dye().getTextureDiffuseColor();
	}

	public boolean isPlayers() {
		return owner != null;
	}

	public boolean atWarWith(int other) {
		return wars.contains(other);
	}
}
