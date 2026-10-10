package com.stasdoto.airdefense.client.nation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.stasdoto.airdefense.nation.Seasons;

/**
 * 1.42: the season on the client - the grass and the leaves coloured for it (the world is drawn again only when the
 * season changes), a word in the chat when a new season comes.
 */
public final class SeasonColors {
	/** The step the colours are drawn for (season * 4 + quarter), and the one last drawn. */
	private static volatile int step = -1;
	private static int drawn = -1;
	private static int announced = -1;
	private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

	private SeasonColors() {
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			return;
		}
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			announced = -1;
		}
		Seasons.update(mc.level.getOverworldClockTime());
		// One look for the whole season (the world is drawn again only when the season changes: four times a year).
		int s = Seasons.season * 4 + 2;
		step = s;
		if (drawn != s) {
			drawn = s;
			// The colours have moved on (or the world was drawn before the season was known): drawn again with them.
			mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
		}
		if (announced != Seasons.season && mc.player != null) {
			if (announced >= 0) {
				mc.player.sendSystemMessage(Component.translatable("season.airdefense.came." + Seasons.season));
			}
			announced = Seasons.season;
		}
	}

	private static int blend(int c, int to, float f) {
		int r = (c >> 16) & 255;
		int g = (c >> 8) & 255;
		int b = c & 255;
		int r2 = (to >> 16) & 255;
		int g2 = (to >> 8) & 255;
		int b2 = to & 255;
		return 0xFF000000 | (int) (r + (r2 - r) * f) << 16 | (int) (g + (g2 - g) * f) << 8 | (int) (b + (b2 - b) * f);
	}

	/** How far into its season the step is (0.125, 0.375, 0.625, 0.875). */
	private static float stepProgress() {
		return ((Math.max(0, step) % 4) + 0.5f) / 4f;
	}

	private static int season() {
		return step < 0 ? Seasons.SUMMER : step / 4;
	}

	public static int grass(int c, BlockPos pos) {
		float p = stepProgress();
		return switch (season()) {
			case Seasons.SPRING -> blend(c, 0xFF6CC13A, 0.25f * (1 - p));
			case Seasons.AUTUMN -> blend(c, 0xFFB59A45, 0.15f + 0.35f * p);
			case Seasons.WINTER -> blend(c, 0xFF9C9A7A, 0.55f);
			default -> c;
		};
	}

	public static int foliage(int c, BlockPos pos) {
		float p = stepProgress();
		return switch (season()) {
			case Seasons.SPRING -> blend(c, 0xFF7ED34A, 0.3f * (1 - p));
			case Seasons.AUTUMN -> {
				// Each tree its own colour, more of them turned as the autumn goes on: gold, orange, red, brown.
				long h = pos.getX() / 4 * 341873128712L ^ pos.getZ() / 4 * 132897987541L ^ pos.getY() / 5 * 42317861L;
				h ^= h >>> 29;
				int[] tones = {0xFFE0B020, 0xFFE07A1E, 0xFFC2341C, 0xFF9A6A2A, 0xFFD89A2A};
				int tone = tones[(int) Math.floorMod(h, (long) tones.length)];
				boolean turned = Math.floorMod(h >>> 8, 100L) < 25 + 75 * p;
				yield turned ? blend(c, tone, 0.75f) : blend(c, 0xFFB0A040, 0.25f * p);
			}
			case Seasons.WINTER -> blend(c, 0xFF7E7458, 0.55f);
			default -> c;
		};
	}
}
