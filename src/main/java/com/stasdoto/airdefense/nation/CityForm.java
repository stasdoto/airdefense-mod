package com.stasdoto.airdefense.nation;

import java.util.Random;

/**
 * 1.35: the outline of a town's street plan. Until 1.35 every town was a ragged blob ({@link #BLOB}); now a town can
 * be round, square, a diamond, a cross, a star, a ring round a central park, a horseshoe, a long strip along its main
 * street, an L, or two halves joined by a street. The planned ones (square, diamond, cross, strip, L) have even
 * blocks, like a town laid out on paper. Worlds started before 1.35 keep the blobs (their roads and villages were
 * planned round them).
 */
public enum CityForm {
	BLOB(false),
	ROUND(false),
	SQUARE(true),
	DIAMOND(true),
	CROSS(true),
	STAR(false),
	RING(false),
	CRESCENT(false),
	LINEAR(true),
	ELL(true),
	TWIN(false);

	/** Even blocks (a town laid out on paper). */
	public final boolean regular;

	CityForm(boolean regular) {
		this.regular = regular;
	}

	public static CityForm of(int id) {
		CityForm[] v = values();
		return id >= 0 && id < v.length ? v[id] : BLOB;
	}

	public String key() {
		return "form.airdefense." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/** A town's outline from its roll (0..9999): capitals and the styles lean to their own kinds of plan. */
	static CityForm pick(int roll, Cities.Size size, boolean capital, CityStyle style) {
		CityForm[] kinds;
		int[] weights;
		if (size == Cities.Size.SMALL) {
			kinds = new CityForm[]{BLOB, SQUARE, CROSS, LINEAR, ELL};
			weights = new int[]{25, 25, 20, 15, 15};
		} else if (capital) {
			kinds = new CityForm[]{ROUND, STAR, RING, SQUARE, CROSS, DIAMOND, CRESCENT, BLOB};
			weights = switch (style) {
				case EUROPEAN -> new int[]{25, 10, 22, 6, 6, 6, 15, 10};
				case AMERICAN -> new int[]{8, 6, 8, 30, 20, 14, 4, 10};
				case SOVIET -> new int[]{18, 26, 14, 10, 14, 6, 4, 8};
				case DESERT -> new int[]{30, 10, 12, 18, 6, 10, 6, 8};
				default -> new int[]{20, 15, 15, 12, 10, 8, 8, 12};
			};
		} else {
			kinds = new CityForm[]{BLOB, ROUND, SQUARE, DIAMOND, CROSS, LINEAR, ELL, CRESCENT, TWIN};
			weights = switch (style) {
				case EUROPEAN -> new int[]{24, 22, 6, 4, 6, 8, 6, 14, 10};
				case AMERICAN -> new int[]{10, 6, 26, 8, 14, 16, 14, 2, 4};
				case SOVIET -> new int[]{14, 12, 14, 8, 16, 18, 10, 4, 4};
				case DESERT -> new int[]{26, 24, 18, 8, 6, 6, 4, 4, 4};
				default -> new int[]{20, 14, 14, 8, 10, 12, 8, 7, 7};
			};
		}
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int at = Math.floorMod(roll, total);
		for (int i = 0; i < kinds.length; i++) {
			at -= weights[i];
			if (at < 0) {
				return kinds[i];
			}
		}
		return BLOB;
	}

	/**
	 * Fills {@code on} (n x n blocks, the town hall's block in the middle) with this outline, as big as fits the town's
	 * size (between {@code lo} and {@code hi} blocks where it can). Returns the radius of the central park ring (RING),
	 * else 0.
	 */
	double fill(boolean[] on, int n, int lo, int hi, Random r) {
		int mid = n / 2;
		int rot = r.nextInt(4);
		boolean[] fit = null;
		int fitStep = 0;
		boolean[] over = null;
		int overStep = 0;
		// Grow the outline step by step: the biggest that is not too big (or, if even the first is, that one).
		for (int step = 0; step < 24; step++) {
			boolean[] m = new boolean[n * n];
			int count = 0;
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					int[] uv = turn(i - mid, j - mid, rot);
					if (inside(uv[0], uv[1], step, n)) {
						m[i * n + j] = true;
						count++;
					}
				}
			}
			if (count == 0) {
				continue;
			}
			if (count <= hi) {
				fit = m;
				fitStep = step;
			} else {
				if (over == null) {
					over = m;
					overStep = step;
				}
				if (fit != null) {
					break;
				}
			}
		}
		boolean[] best = fit != null ? fit : over;
		int step = fit != null ? fitStep : overStep;
		if (best != null) {
			System.arraycopy(best, 0, on, 0, on.length);
		}
		on[mid * n + mid] = true;
		return this == RING ? (0.9 + step * 0.3 >= 2.9 ? 1.5 : 1.0) : 0;
	}

	private static int[] turn(int u, int v, int rot) {
		return switch (rot) {
			case 1 -> new int[]{-v, u};
			case 2 -> new int[]{-u, -v};
			case 3 -> new int[]{v, -u};
			default -> new int[]{u, v};
		};
	}

	/** Whether block (u, v) from the middle is in the outline at growth step {@code s} (bigger steps, bigger towns). */
	private boolean inside(int u, int v, int s, int n) {
		int lim = n / 2;
		if (Math.abs(u) > lim || Math.abs(v) > lim) {
			return false;
		}
		double d2 = u * u + v * v;
		return switch (this) {
			case ROUND, RING -> d2 <= Math.pow(0.9 + s * 0.3, 2);
			case SQUARE -> {
				// Side 2, 3, 4...: the even sides one more to the east and south.
				int k = 1 + s;
				int a = (k - 1) / 2;
				int b = k / 2;
				yield u >= -a && u <= b && v >= -a && v <= b;
			}
			case DIAMOND -> Math.abs(u) + Math.abs(v) <= s;
			case CROSS -> {
				int w = s >= 6 ? 1 : 0;
				int len = w == 0 ? s : s - 4;
				yield Math.abs(u) <= w && Math.abs(v) <= len || Math.abs(v) <= w && Math.abs(u) <= len;
			}
			case STAR -> {
				// A round middle with eight arms: four straight, four along the diagonals (as stairs).
				double core = s < 4 ? 1.0 : 1.5;
				int len = Math.min(lim, 1 + s / 2);
				boolean straight = (u == 0 || v == 0) && Math.max(Math.abs(u), Math.abs(v)) <= len;
				int au = Math.abs(u);
				int av = Math.abs(v);
				boolean diag = s >= 3 && Math.max(au, av) <= len - 1 && (au == av || au == av + 1 && av >= 1);
				yield d2 <= core * core + 0.01 || straight || diag;
			}
			case CRESCENT -> {
				double rr = 1.2 + s * 0.3;
				int bay = rr >= 3.2 ? 1 : 0;
				yield d2 <= rr * rr && !(v >= 1 && Math.abs(u) <= bay && !(u == 0 && v == 0));
			}
			case LINEAR -> {
				int w = s >= 5 ? 1 : 0;
				int len = w == 0 ? s : s - 3;
				yield Math.abs(v) <= w && Math.abs(u) <= len;
			}
			case ELL -> {
				int w = s >= 5 ? 1 : 0;
				int len = w == 0 ? s : s - 3;
				yield Math.abs(v) <= w && u >= -w && u <= len || Math.abs(u) <= w && v >= -w && v <= len;
			}
			case TWIN -> {
				// Two round halves either side of the town hall's street.
				int off = Math.max(2, Math.min(lim - 1, 2 + s / 3));
				double rr = 0.9 + (s % 3) * 0.4 + (s >= 6 ? 0.3 : 0);
				boolean left = (u + off) * (u + off) + v * v <= rr * rr;
				boolean right = (u - off) * (u - off) + v * v <= rr * rr;
				boolean link = v == 0 && Math.abs(u) <= off;
				yield left || right || link;
			}
			default -> false;
		};
	}
}
