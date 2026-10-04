package com.stasdoto.airdefense.defense;

import org.jetbrains.annotations.Nullable;

import com.stasdoto.airdefense.missile.MissileType;

/**
 * Air defence systems. Range in blocks, magazine = interceptors (Gepard: bursts) before reloading,
 * reload/interval in ticks. Gepard has no interceptor: it fires its guns directly.
 */
public enum DefenseType {
	PATRIOT(MissileType.PAC3, 220, 8, 500, 22),
	IRIS_T(MissileType.IRIST, 140, 8, 360, 14),
	NASAMS(MissileType.AMRAAM, 120, 6, 360, 16),
	GEPARD(null, 64, 30, 300, 12);

	@Nullable
	public final MissileType interceptor;
	public final double range;
	public final int magazine;
	public final int reload;
	public final int interval;

	DefenseType(@Nullable MissileType interceptor, double range, int magazine, int reload, int interval) {
		this.interceptor = interceptor;
		this.range = range;
		this.magazine = magazine;
		this.reload = reload;
		this.interval = interval;
	}

	/** How many interceptors this system sends at one target of the given kind (Patriot fires two at ballistic missiles). */
	public int shotsPerTarget(MissileType.Kind kind) {
		return this == PATRIOT && (kind == MissileType.Kind.BALLISTIC || kind == MissileType.Kind.ROCKET) ? 2 : 1;
	}

	/** Engagement priority: lower = shot first. Each system prefers what it was built for. */
	public int priority(MissileType.Kind kind) {
		return switch (this) {
			case PATRIOT -> switch (kind) {
				case BALLISTIC -> 0;
				case ROCKET -> 1;
				case CRUISE -> 2;
				default -> 3;
			};
			case GEPARD -> switch (kind) {
				case DRONE -> 0;
				case CRUISE -> 1;
				default -> 3;
			};
			default -> switch (kind) {
				case CRUISE -> 0;
				case DRONE -> 1;
				case ROCKET -> 2;
				default -> 3;
			};
		};
	}

	/** Gepard: chance that one round of a burst hits a target of this kind. */
	public static double gunHitChance(MissileType.Kind kind) {
		return switch (kind) {
			case DRONE -> 0.3;
			case CRUISE -> 0.15;
			case ROCKET -> 0.04;
			default -> 0.012;
		};
	}
}
