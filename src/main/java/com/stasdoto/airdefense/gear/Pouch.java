package com.stasdoto.airdefense.gear;

import net.minecraft.world.item.Item;

import com.stasdoto.airdefense.registry.ModItems;

/**
 * 1.27: a pouch on a vest. Each kind gives its wearer something:
 * MAG - magazines at hand, a quicker reload (each pouch); GRENADE - B throws a grenade without taking it in hand;
 * MEDKIT - H dresses a wound at once from the kit; RADIO - warnings of missiles and drones coming your way.
 */
public enum Pouch {
	NONE, MAG, GRENADE, MEDKIT, RADIO;

	/** Which vest slots this kind takes first (0-2 the front's lower row, 3 the chest, 4-5 the back). */
	int[] order() {
		return switch (this) {
			case MAG -> new int[]{1, 0, 2, 3, 5, 4};
			case GRENADE -> new int[]{2, 0, 1, 5, 4, 3};
			case MEDKIT -> new int[]{3, 5, 4, 0, 2, 1};
			case RADIO -> new int[]{4, 5, 3, 0, 2, 1};
			default -> new int[0];
		};
	}

	public Item item() {
		return switch (this) {
			case MAG -> ModItems.POUCH_MAG;
			case GRENADE -> ModItems.POUCH_GRENADE;
			case MEDKIT -> ModItems.POUCH_MEDKIT;
			case RADIO -> ModItems.POUCH_RADIO;
			default -> null;
		};
	}

	public String key() {
		return "pouch.airdefense." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static Pouch of(int id) {
		Pouch[] v = values();
		return id > 0 && id < v.length ? v[id] : NONE;
	}

	public static Pouch of(Item item) {
		for (Pouch p : values()) {
			if (p != NONE && p.item() == item) {
				return p;
			}
		}
		return NONE;
	}
}
