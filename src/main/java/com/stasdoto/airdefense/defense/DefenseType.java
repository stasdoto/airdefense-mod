package com.stasdoto.airdefense.defense;

import org.jetbrains.annotations.Nullable;

import com.stasdoto.airdefense.missile.MissileType;

/**
 * Air defence systems. Range in blocks, magazine = interceptors (guns: bursts) before reloading, reload/interval in
 * ticks (interval = time between shots at different targets). Systems without an interceptor fire their guns
 * directly; Pantsir and Tunguska have both (guns close in, missiles further out). {@code gunSkill} scales how well
 * the guns hit (Gepard's radar-laid 35 mm = 1).
 */
public enum DefenseType {
	PATRIOT(MissileType.PAC3, 220, 8, 500, 14, 0.65),
	IRIS_T(MissileType.IRIST, 140, 8, 360, 10, 0.45),
	NASAMS(MissileType.AMRAAM, 120, 6, 360, 10, 0.4),
	GEPARD(null, 64, 30, 300, 10, 0.6, 64, 1.0),
	// Stage R4.
	PANTSIR(MissileType.M57E6, 130, 12, 600, 12, 0.5, 48, 0.95),
	TOR(MissileType.M9M338, 100, 8, 500, 10, 0.55),
	BUK(MissileType.M9M317, 180, 4, 700, 18, 0.45),
	S300(MissileType.M48N6, 250, 4, 900, 20, 0.55),
	OSA(MissileType.M9M33, 75, 6, 500, 12, 0.35),
	STRELA10(MissileType.M9M37, 55, 4, 300, 14, 0.25),
	SHILKA(null, 45, 25, 300, 10, 0.4, 45, 0.7),
	TUNGUSKA(MissileType.M9M311, 90, 8, 500, 12, 0.45, 42, 0.9),
	SAMPT(MissileType.ASTER30, 230, 8, 800, 16, 0.6),
	AVENGER(MissileType.STINGER, 55, 8, 360, 12, 0.3),
	MFG(null, 32, 40, 200, 6, 0.5, 32, 0.5),
	ZU23(null, 38, 25, 260, 8, 0.35, 38, 0.6),
	// 1.24: Iron Dome - twenty Tamirs, made for rockets, shells and drones; it only shoots at what would fall on
	// something worth protecting (see {@link #protectsOnly()}).
	IRON_DOME(MissileType.TAMIR, 160, 20, 700, 5, 0.5);

	@Nullable
	public final MissileType interceptor;
	public final double range;
	public final int magazine;
	public final int reload;
	public final int interval;
	/** How good the radar (or the optics) is at telling a decoy from the real thing: share of decoys it sees through. */
	public final double discrimination;
	/** Guns: how far they shoot (0 = no guns), and how well they hit. */
	public final double gunRange;
	public final double gunSkill;

	DefenseType(@Nullable MissileType interceptor, double range, int magazine, int reload, int interval, double discrimination) {
		this(interceptor, range, magazine, reload, interval, discrimination, 0, 0);
	}

	DefenseType(@Nullable MissileType interceptor, double range, int magazine, int reload, int interval, double discrimination,
			double gunRange, double gunSkill) {
		this.interceptor = interceptor;
		// Missile systems reach five times further (1.24); guns keep their range.
		this.range = interceptor == null ? range : range * MissileType.RANGE_SCALE;
		this.magazine = magazine;
		this.reload = reload;
		this.interval = interval;
		this.discrimination = discrimination;
		this.gunRange = gunRange;
		this.gunSkill = gunSkill;
	}

	/** Only guns. */
	public boolean gunOnly() {
		return interceptor == null;
	}

	/** Missiles and guns on one mount. */
	public boolean hybrid() {
		return interceptor != null && gunRange > 0;
	}

	public boolean hasGuns() {
		return gunRange > 0;
	}

	/**
	 * Interceptors that may be on their way to one target at a time - from this battery and every other one around
	 * together (the count is the target's). One: the batteries share the targets out, and fire again only once a
	 * missile has missed and blown itself up (shoot - look - shoot), never a salvo at a single drone. A ballistic
	 * missile gets two (it is too fast to look before the second shot) - and never more, however many batteries stand
	 * there.
	 */
	public int shotsPerTarget(MissileType.Kind kind) {
		return kind == MissileType.Kind.BALLISTIC ? 2 : 1;
	}

	/**
	 * Iron Dome works out where each rocket or drone will come down and lets those that fall in empty fields go,
	 * saving its interceptors for what threatens a town, people or vehicles.
	 */
	public boolean protectsOnly() {
		return this == IRON_DOME;
	}

	/** Engagement priority: lower = shot first. Each system prefers what it was built for. */
	public int priority(MissileType.Kind kind) {
		return switch (this) {
			case IRON_DOME -> switch (kind) {
				case ROCKET -> 0;
				case DRONE -> 1;
				case CRUISE -> 2;
				default -> 3;
			};
			case PATRIOT, S300, SAMPT, BUK -> switch (kind) {
				case BALLISTIC -> 0;
				case ROCKET -> 1;
				case CRUISE -> 2;
				default -> 3;
			};
			case GEPARD, SHILKA, MFG, ZU23, STRELA10, AVENGER -> switch (kind) {
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

	/** Gepard: chance that one round hits a target of this kind (at point-blank range, guns right on the lead point). */
	public static double gunHitChance(MissileType.Kind kind) {
		return switch (kind) {
			case DRONE -> 0.24;
			case CRUISE -> 0.16;
			case ROCKET -> 0.05;
			default -> 0.01;
		};
	}
}
