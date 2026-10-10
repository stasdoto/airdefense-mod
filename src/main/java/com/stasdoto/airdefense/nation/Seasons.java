package com.stasdoto.airdefense.nation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * 1.42: the seasons - spring, summer, autumn, winter, {@link #DAYS} days each (a year of 24 days). Worked out from the
 * day count alone (the same on the server and on every client, nothing to send). In winter the temperate lands are
 * cold: it snows instead of raining, snow lies and the water freezes (Biome mixin); after it the snow and the ice thaw
 * (SnowLayerBlock, IceBlock mixins). The weather follows the season (a wet autumn, a stormy summer). On the client the
 * grass and the leaves change colour (client mixin).
 */
public final class Seasons {
	public static final int DAYS = 6;
	public static final int SPRING = 0;
	public static final int SUMMER = 1;
	public static final int AUTUMN = 2;
	public static final int WINTER = 3;
	/** The season now and how far into it (0..1): kept by each side's tick (server and client share them in one game). */
	public static volatile int season = SPRING;
	public static volatile float progress;
	/** For the tests: set to force a season (-1: by the day). */
	public static volatile int force = -1;
	/** Biomes at or above this (deserts, savannas, jungles) know no winter. */
	public static final float WARM = 0.95f;

	private Seasons() {
	}

	/** The season and its progress from the time of day (in ticks since the world began). */
	public static void update(long dayTime) {
		long day = Math.floorDiv(dayTime, 24000L);
		int s = (int) Math.floorMod(Math.floorDiv(day, DAYS), 4L);
		season = force >= 0 ? force : s;
		progress = force >= 0 ? 0.5f : (Math.floorMod(day, DAYS) + Math.floorMod(dayTime, 24000L) / 24000f) / DAYS;
	}

	/** Is it winter where the base temperature is this (temperate and cold lands only)? */
	public static boolean winterAt(float baseTemperature) {
		return season == WINTER && baseTemperature < WARM;
	}

	/** Do snow and ice that are not the land's own thaw now? */
	public static boolean thawing(float baseTemperature) {
		return season != WINTER && baseTemperature > 0.15f;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = server.overworld();
			long t = level.getOverworldClockTime();
			int before = season;
			update(t);
			if (Math.floorMod(t, 24000L) == 23000 && level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
				weather(level, level.getRandom());
			}
			if (before != season) {
				com.stasdoto.airdefense.AirDefense.LOGGER.info("[airdefense] season {}", season);
			}
		});
	}

	/** At dawn, the day's weather from the season: how likely rain (snow in winter) and a storm are. */
	private static void weather(ServerLevel level, RandomSource r) {
		float wet = switch (season) {
			case SPRING -> 0.3f;
			case SUMMER -> 0.2f;
			case AUTUMN -> 0.55f;
			default -> 0.45f;
		};
		float storm = season == SUMMER ? 0.5f : season == WINTER ? 0.0f : 0.15f;
		WeatherData w = level.getWeatherData();
		if (r.nextFloat() < wet) {
			int len = 6000 + r.nextInt(12000);
			w.setClearWeatherTime(0);
			w.setRaining(true);
			w.setRainTime(len);
			boolean thunder = r.nextFloat() < storm;
			w.setThundering(thunder);
			w.setThunderTime(thunder ? len / 2 : 12000);
		} else {
			w.setClearWeatherTime(12000 + r.nextInt(12000));
			w.setRaining(false);
			w.setRainTime(0);
			w.setThundering(false);
			w.setThunderTime(0);
		}
		w.setDirty();
	}

	/** For a level that is the overworld (the seasons are only there). */
	public static boolean here(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}
}
