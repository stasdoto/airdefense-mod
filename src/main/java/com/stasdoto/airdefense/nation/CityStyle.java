package com.stasdoto.airdefense.nation;

/**
 * 1.28 "Cities of the world": how a country's towns look. Picked once per country (its cell of the plan) from the
 * world's seed, the desert's towns always in the desert style. Worlds started before 1.28 keep {@link #CLASSIC}
 * (the renovated mix of panel blocks, glass towers and villas), so their half-built towns get no seams.
 */
public enum CityStyle {
	/** The look of 1.21-1.27: colourful renovated panel blocks, glass towers, modern villas. */
	CLASSIC,
	/** Grey and white panel blocks with balconies, brick five-storeys, a Stalin-era centre, dachas, kiosks. */
	SOVIET,
	/** Plastered town houses with steep tiled roofs and dormers, half-timbered houses, a church, shop fronts. */
	EUROPEAN,
	/** A glass downtown, brick walk-ups with fire escapes, suburbs of siding houses with lawns and garages, strip malls. */
	AMERICAN,
	/** Sandstone and clay: flat roofs with terraces and awnings, arched windows, domes, courtyards, palms. */
	DESERT;

	/** Climates the planner tells apart (from the biome at the town). */
	public static final int TEMPERATE = 0;
	public static final int COLD = 1;
	public static final int DRY = 2;

	public static CityStyle of(int id) {
		CityStyle[] v = values();
		return id >= 0 && id < v.length ? v[id] : CLASSIC;
	}

	/** The style of a country's towns: {@code roll} is a per-country hash (0..99). */
	static CityStyle pick(int roll, int climate) {
		if (climate == DRY) {
			return DESERT;
		}
		if (climate == COLD) {
			return roll < 60 ? SOVIET : EUROPEAN;
		}
		return roll < 34 ? SOVIET : roll < 67 ? EUROPEAN : AMERICAN;
	}

	public String key() {
		return "style.airdefense." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
