package com.stasdoto.airdefense.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.stasdoto.airdefense.AirDefense;

/**
 * Where a slow server tick went (1.25.1): the mod's own parts time themselves here; a tick over 60 ms is written to
 * the log with the share of each part (and of everything else - the game itself). For finding freezes.
 */
public final class Perf {
	public static final int NATIONS = 0;
	public static final int ECONOMY = 1;
	public static final int ARSENALS = 2;
	public static final int WAR = 3;
	public static final int SIRENS = 4;
	public static final int ATLAS = 5;
	public static final int MISSILES = 6;
	public static final int VEHICLES = 7;
	public static final int SOLDIERS = 8;
	public static final int WORKERS = 9;
	public static final int LATER = 10;
	public static final int CITYGEN = 11;
	public static final int REPAIRS = 12;
	private static final String[] NAMES = {"nations", "economy", "arsenals", "war", "sirens", "atlas", "missiles", "vehicles", "soldiers",
			"workers", "later", "citygen", "repairs"};
	private static final long[] NOW = new long[NAMES.length];
	private static final int[] COUNT = new int[NAMES.length];
	private static long start;
	/** For the tests: slow ticks seen, the slowest (ms), and the worst ticks' lines. */
	public static int slowTicks;
	public static long worstMs;
	public static final java.util.List<String> WORST = new java.util.ArrayList<>();

	private Perf() {
	}

	public static void init() {
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			java.util.Arrays.fill(NOW, 0);
			java.util.Arrays.fill(COUNT, 0);
			start = System.nanoTime();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long total = System.nanoTime() - start;
			long ms = total / 1_000_000;
			if (ms < 60) {
				return;
			}
			slowTicks++;
			StringBuilder sb = new StringBuilder();
			long ours = 0;
			for (int i = 0; i < NAMES.length; i++) {
				if (NOW[i] > 500_000) {
					sb.append(' ').append(NAMES[i]).append(' ').append(NOW[i] / 1_000_000).append(" ms");
					if (COUNT[i] > 0) {
						sb.append(" (").append(COUNT[i]).append(')');
					}
				}
				ours += i == CITYGEN ? 0 : NOW[i];
			}
			String line = "slow tick " + ms + " ms:" + sb + "; the rest (the game) " + Math.max(0, total - ours) / 1_000_000 + " ms";
			if (ms > worstMs) {
				worstMs = ms;
			}
			if (WORST.size() < 40) {
				WORST.add(line);
			}
			AirDefense.LOGGER.info("[airdefense] {}", line);
		});
	}

	/** Adds {@code nanos} to a part of this tick. */
	public static void add(int part, long nanos) {
		NOW[part] += nanos;
		COUNT[part]++;
	}
}
