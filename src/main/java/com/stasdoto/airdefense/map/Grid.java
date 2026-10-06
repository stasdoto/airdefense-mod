package com.stasdoto.airdefense.map;

/**
 * The map grid: squares of 100 by 100 blocks named by a letter (west to east) and a number (north to south), like
 * "Д-14", each split into nine smaller squares numbered like a telephone keypad ("улитка": 1 top left, 5 in the
 * middle, 9 bottom right), so a place reads "Д-14-5".
 */
public final class Grid {
	public static final int SIZE = 100;
	private static final String CYRILLIC = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЭЮЯ";
	private static final String LATIN = "ABCDEFGHJKLMNOPQRSTUVWXYZ";

	private Grid() {
	}

	public static int column(double x) {
		return Math.floorDiv((int) Math.floor(x), SIZE);
	}

	public static int row(double z) {
		return Math.floorDiv((int) Math.floor(z), SIZE);
	}

	/** "Д-14" for the square holding (x, z). */
	public static String square(double x, double z, boolean cyrillic) {
		return name(column(x), row(z), cyrillic);
	}

	public static String name(int column, int row, boolean cyrillic) {
		String letters = cyrillic ? CYRILLIC : LATIN;
		return letters.charAt(Math.floorMod(column, letters.length())) + "-" + (Math.floorMod(row, 99) + 1);
	}

	/** 1..9: which ninth of its square (x, z) is in. */
	public static int snail(double x, double z) {
		int cx = Math.floorMod((int) Math.floor(x), SIZE) * 3 / SIZE;
		int cz = Math.floorMod((int) Math.floor(z), SIZE) * 3 / SIZE;
		return 1 + cz * 3 + cx;
	}

	/** "Д-14-5". */
	public static String full(double x, double z, boolean cyrillic) {
		return square(x, z, cyrillic) + "-" + snail(x, z);
	}
}
