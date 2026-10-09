package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 1.36: the towns' windows light up in the evening and go dark late at night, one flat after another (each flat - a few
 * windows side by side on one floor - has its own hour). In a town on alert at night the lights go out (blackout).
 * Done in the windows' random ticks: nothing runs for a window between them, and a window far from any player is left
 * as it is.
 */
public final class WindowLights {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** How much light a lit window gives (a room's lamp seen from the street). */
	public static final int LIGHT = 11;

	private WindowLights() {
	}

	/** Share of the flats with their lights on at this time of day (0 = 6 am, 6000 = noon, 18000 = midnight). */
	static double share(long dayTime) {
		double t = Math.floorMod(dayTime, 24000L);
		if (t < 1000) {
			return lerp(0.25, 0.05, t / 1000);
		}
		if (t < 11000) {
			return 0.04;
		}
		if (t < 13500) {
			return lerp(0.04, 0.75, (t - 11000) / 2500);
		}
		if (t < 17000) {
			return 0.75;
		}
		if (t < 20000) {
			return lerp(0.75, 0.12, (t - 17000) / 3000);
		}
		if (t < 23000) {
			return 0.12;
		}
		return lerp(0.12, 0.25, (t - 23000) / 1000);
	}

	private static double lerp(double a, double b, double f) {
		return a + (b - a) * f;
	}

	/** The flat's own hour: a number in [0, 1) for the three windows side by side on one floor. */
	static double flat(BlockPos p) {
		long h = Math.floorDiv(p.getX(), 3) * 0x9E3779B97F4A7C15L ^ p.getY() * 0xC2B2AE3D27D4EB4FL ^ Math.floorDiv(p.getZ(), 3) * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Should the window at {@code p} be lit now? */
	static boolean wanted(ServerLevel level, BlockPos p) {
		if (flat(p) >= share(level.getOverworldClockTime())) {
			return false;
		}
		// Blackout: a town on alert at night puts its lights out (by day nobody sees the few lamps).
		try {
			com.stasdoto.airdefense.siren.Sirens sirens = com.stasdoto.airdefense.siren.Sirens.get(level.getServer());
			if (sirens.everywhere) {
				return false;
			}
			if (sirens.alert.isEmpty() && sirens.autoUntil.isEmpty()) {
				return true;
			}
			int town = com.stasdoto.airdefense.siren.Sirens.townOf(level, p);
			if (town >= 0 && sirens.signalFor(level.getGameTime(), town, com.stasdoto.airdefense.siren.Sirens.MODE_AUTO)
					== com.stasdoto.airdefense.siren.Sirens.ALERT) {
				return false;
			}
		} catch (RuntimeException e) {
			// No sirens data: lights as usual.
		}
		return true;
	}

	/** The random tick of a window: on or off as the hour (and the sirens) say. */
	static void tick(BlockState state, ServerLevel level, BlockPos p) {
		boolean want = wanted(level, p);
		if (state.getValue(LIT) != want) {
			level.setBlock(p, state.setValue(LIT, want), Block.UPDATE_CLIENTS);
		}
	}
}
