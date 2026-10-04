package com.stasdoto.airdefense.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A slowly turning wind that every smoke particle drifts with, so trails bend and smoke columns lean. */
public final class FxWind {
	static double x = 0.02;
	static double z = 0.01;
	private static double angle = 0.6;
	private static final RandomSource RANDOM = RandomSource.create();

	private FxWind() {
	}

	public static void tick() {
		angle += (RANDOM.nextDouble() - 0.5) * 0.004;
		double speed = 0.025 + 0.008 * Math.sin(angle * 3.1);
		x = Mth.cos((float) angle) * speed;
		z = Mth.sin((float) angle) * speed;
	}
}
