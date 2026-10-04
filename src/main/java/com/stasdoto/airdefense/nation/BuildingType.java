package com.stasdoto.airdefense.nation;

/**
 * What a village can build. Sizes are the footprint in blocks (width across the front, depth from the front wall
 * back) and the height above the floor; costs are in the village's own wood, stone and iron. In creative everything is
 * free and goes up at once.
 */
public enum BuildingType {
	/** One floor, two beds. */
	SMALL_HOUSE("small_house", 7, 7, 9, 60, 30, 0, 2),
	/** Two floors, four beds. */
	HOUSE("house", 9, 9, 13, 100, 90, 4, 4),
	/** A block of flats: four floors, eight beds. */
	APARTMENTS("apartments", 11, 9, 18, 80, 260, 20, 8),
	/** More men can be called up, soldiers nearby heal. */
	BARRACKS("barracks", 13, 7, 8, 110, 140, 10, 6),
	/** Makes vehicles (they roll out of its gate). */
	HANGAR("hangar", 15, 17, 12, 40, 220, 60, 0),
	/** The missile factory (it puts itself up, then works like one made from a kit). */
	FACTORY("factory", 21, 15, 20, 120, 260, 40, 0),
	/** Babies are born here while there are free beds in the village. */
	HOSPITAL("hospital", 11, 9, 10, 60, 180, 12, 2),
	/** Keeps more of everything; the workers bring what they get here. */
	WAREHOUSE("warehouse", 11, 9, 10, 150, 50, 4, 0),
	/** Roads from the square to every building and to the nearest villages of the same country. */
	ROADS("roads", 0, 0, 0, 0, 0, 0, 0);

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
