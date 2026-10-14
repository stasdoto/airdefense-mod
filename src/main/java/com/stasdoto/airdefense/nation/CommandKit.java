package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * 2.0: what a country can be ordered to put up in command mode (the same for the server, which checks it, and the map,
 * which lists it): air defence, missile and artillery launchers, radars.
 */
public final class CommandKit {
	public static final int AIR_DEFENCE = 0;
	public static final int LAUNCHERS = 1;
	public static final int RADARS = 2;
	public static final int KINDS = 3;

	private static List<List<VehicleType>> lists;

	private CommandKit() {
	}

	/** Which list a vehicle is in, or -1 when it cannot be ordered this way. */
	public static int kindOf(VehicleType t) {
		if (t.isShip() || t.boat || t.isAir() || t.isTruck() || t.isService()) {
			return -1;
		}
		if (t.isDefense()) {
			return AIR_DEFENCE;
		}
		if (t.isRadar()) {
			return RADARS;
		}
		if (t.isLauncher() && !t.isDroneLauncher() && !t.isCoastal()) {
			return LAUNCHERS;
		}
		return -1;
	}

	/** The vehicles of one list, in the order of the catalogue. */
	public static List<VehicleType> of(int kind) {
		if (lists == null) {
			List<List<VehicleType>> l = new ArrayList<>();
			for (int k = 0; k < KINDS; k++) {
				l.add(new ArrayList<>());
			}
			for (VehicleType t : VehicleType.values()) {
				int k = kindOf(t);
				if (k >= 0) {
					l.get(k).add(t);
				}
			}
			lists = l;
		}
		return kind >= 0 && kind < KINDS ? lists.get(kind) : List.of();
	}

	/** A launcher a strike can be ordered from. */
	public static boolean canStrikeWith(VehicleType t) {
		return kindOf(t) == LAUNCHERS;
	}
}
