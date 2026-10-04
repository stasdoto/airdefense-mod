package com.stasdoto.airdefense.missile;

import java.util.concurrent.atomic.AtomicInteger;

import com.stasdoto.airdefense.AirDefense;

/** Counters used by the automated in-game test (and handy for debugging); logging only with -Dairdefense.debug=true. */
public final class MissileStats {
	public static final AtomicInteger STRIKES_LAUNCHED = new AtomicInteger();
	public static final AtomicInteger INTERCEPTORS_LAUNCHED = new AtomicInteger();
	public static final AtomicInteger THREATS_SHOT_DOWN = new AtomicInteger();
	public static final AtomicInteger GROUND_IMPACTS = new AtomicInteger();
	public static final AtomicInteger INTERCEPTOR_BURSTS = new AtomicInteger();
	/** Interceptors that flew into the ground instead of bursting in the air (should stay near zero). */
	public static final AtomicInteger INTERCEPTOR_CRASHES = new AtomicInteger();

	private MissileStats() {
	}

	public static boolean debug() {
		return Boolean.getBoolean("airdefense.debug");
	}

	public static void log(String message, Object... args) {
		if (debug()) {
			AirDefense.LOGGER.info("[airdefense-test] " + message, args);
		}
	}

	public static String summary() {
		return "strikes=" + STRIKES_LAUNCHED.get() + " interceptors=" + INTERCEPTORS_LAUNCHED.get()
				+ " shotDown=" + THREATS_SHOT_DOWN.get() + " impacts=" + GROUND_IMPACTS.get()
				+ " interceptorBursts=" + INTERCEPTOR_BURSTS.get() + " interceptorCrashes=" + INTERCEPTOR_CRASHES.get();
	}
}
