package com.stasdoto.airdefense.nation;

/**
 * What a village can build. Sizes are the footprint in blocks (width across the front, depth from the front wall
 * back) and the height above the floor; costs are in the village's own wood, stone and iron. In creative everything is
 * free and goes up at once.
 */
public enum BuildingType {
	/** One floor, two beds. */
	SMALL_HOUSE("small_house", 7, 7, 13, 60, 30, 0, 2),
	/** Two floors, four beds. */
	HOUSE("house", 9, 9, 15, 100, 90, 4, 4),
	/** A block of flats: four floors, eight beds. */
	APARTMENTS("apartments", 11, 9, 17, 80, 260, 20, 8),
	/** More men can be called up, soldiers nearby heal. */
	BARRACKS("barracks", 13, 7, 8, 110, 140, 10, 6),
	/** Makes vehicles (they roll out of its gate). */
	HANGAR("hangar", 15, 17, 12, 40, 220, 60, 0),
	/** The missile factory (it puts itself up, then works like one made from a kit). */
	FACTORY("factory", 21, 15, 20, 120, 260, 40, 0),
	/** Babies are born here while there are free beds in the village. */
	HOSPITAL("hospital", 11, 9, 11, 60, 180, 12, 2),
	/** Keeps more of everything; the workers bring what they get here. */
	WAREHOUSE("warehouse", 11, 9, 10, 150, 50, 4, 0),
	/** Roads from the square to every building and to the nearest villages of the same country. */
	ROADS("roads", 0, 0, 0, 0, 0, 0, 0),
	// Stage R7-R8: the modern town.
	/** Five-storey panel block. */
	PANEL5("panel5", 21, 10, 19, 40, 300, 30, 6),
	/** Nine-storey panel block. */
	PANEL9("panel9", 25, 13, 31, 40, 520, 60, 8),
	/** Glass tower of sixteen floors. */
	TOWER("tower", 15, 15, 60, 20, 600, 120, 2),
	OFFICE("office", 17, 13, 25, 20, 380, 80, 0),
	COTTAGE("cottage", 9, 9, 15, 60, 80, 6, 3),
	SHOP("shop", 17, 9, 8, 30, 120, 20, 0),
	SCHOOL("school", 25, 13, 15, 40, 300, 30, 0),
	CITY_HALL("city_hall", 21, 15, 29, 40, 360, 40, 0),
	PARK("park", 21, 21, 7, 40, 30, 0, 0),
	/** Refuels every vehicle that stops next to it (from the town's fuel). */
	GAS_STATION("gas_station", 15, 13, 8, 20, 80, 40, 0),
	/** Refuels and rearms vehicles near it from the town's stock; makes ammunition from iron. */
	LOGISTICS_HUB("logistics_hub", 21, 17, 10, 40, 200, 80, 0),
	/** Pumps crude oil (only on an oil field). */
	OIL_WELL("oil_well", 7, 9, 8, 10, 30, 60, 0),
	/** Turns crude oil into fuel. */
	REFINERY("refinery", 19, 17, 22, 30, 200, 160, 0),
	GARAGES("garages", 17, 7, 5, 10, 80, 10, 0);

	public final String id;
	public final int width;
	public final int depth;
	public final int height;
	public final int wood;
	public final int stone;
	public final int iron;
	public final int beds;

	BuildingType(String id, int width, int depth, int height, int wood, int stone, int iron, int beds) {
		this.id = id;
		this.width = width;
		this.depth = depth;
		this.height = height;
		this.wood = wood;
		this.stone = stone;
		this.iron = iron;
		this.beds = beds;
	}

	public int halfWidth() {
		return width / 2;
	}

	public static BuildingType byId(int ordinal) {
		BuildingType[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SMALL_HOUSE;
	}

	public String key() {
		return "building.airdefense." + id;
	}
}
