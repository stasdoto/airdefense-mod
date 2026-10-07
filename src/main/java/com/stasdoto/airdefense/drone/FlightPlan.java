package com.stasdoto.airdefense.drone;

/**
 * A flight task for drones and cruise missiles, set in the tablet's flight window: cruise height above the ground,
 * speed (share of full speed), the way it flies, how many go, and whether the player watches through the first one's
 * camera.
 */
public record FlightPlan(int altitude, int speedPercent, int maneuver, int count, boolean camera) {
	/** Straight at the target. */
	public static final int STRAIGHT = 0;
	/** Weaving left and right (harder to hit with guns). */
	public static final int WEAVE = 1;
	/** Round the side: swings wide and comes in from the flank. */
	public static final int FLANK = 2;
	/** Low: drops to hedge-hopping height half way, under the radars. */
	public static final int LOW = 3;
	public static final int MANEUVERS = 4;

	public static final int MIN_ALT = 15;
	public static final int MAX_ALT = 200;
	public static final int LOW_ALT = 7;

	public static final FlightPlan DEFAULT = new FlightPlan(70, 100, WEAVE, 0, false);

	public FlightPlan {
		altitude = Math.max(MIN_ALT, Math.min(MAX_ALT, altitude));
		speedPercent = Math.max(50, Math.min(100, speedPercent));
		maneuver = Math.floorMod(maneuver, MANEUVERS);
		count = Math.max(0, Math.min(16, count));
	}
}
