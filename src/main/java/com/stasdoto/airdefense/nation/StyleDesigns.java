package com.stasdoto.airdefense.nation;

import net.minecraft.world.item.DyeColor;

import com.stasdoto.airdefense.nation.Blueprints.Plan;

/**
 * 1.28: the buildings of the regional styles ({@link CityStyle}). A style draws the houses, blocks of flats, shops,
 * towers and the city hall its own way; everything else (factories, the army base, the school...) keeps the common
 * design.
 */
final class StyleDesigns {
	private StyleDesigns() {
	}

	/** True if the style has its own design for this building (drawn into {@code p}). */
	static boolean design(Plan p, int variant, DyeColor flag) {
		return switch (CityStyle.of(p.b.style)) {
			case SOVIET -> SovietStyle.design(p, variant, flag);
			case EUROPEAN -> EuropeanStyle.design(p, variant, flag);
			case AMERICAN -> AmericanStyle.design(p, variant, flag);
			case DESERT -> DesertStyle.design(p, variant, flag);
			default -> false;
		};
	}
}
