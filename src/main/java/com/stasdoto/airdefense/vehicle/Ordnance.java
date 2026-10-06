package com.stasdoto.airdefense.vehicle;

import com.stasdoto.airdefense.missile.MissileType;

/** What aircraft carry under their wings: rocket pods, bombs, air-to-air missiles. */
public enum Ordnance {
	/** S-8 rockets: salvos of four from the pods. */
	S8(MissileType.S8, 40, 4, 3, 24, "s8_rockets"),
	/** FAB-250 free-fall bombs, one at a time. */
	FAB250(MissileType.FAB250, 4, 1, 0, 30, "fab250"),
	/** AIM-9 Sidewinders: lock on to a drone or missile in front and fire. */
	AIM9(MissileType.AIM9, 4, 1, 0, 40, "stinger_missile");

	public final MissileType missile;
	public final int count;
	public final int salvo;
	public final int interval;
	public final int cooldown;
	/** The factory item that reloads it. */
	public final String itemId;

	Ordnance(MissileType missile, int count, int salvo, int interval, int cooldown, String itemId) {
		this.missile = missile;
		this.count = count;
		this.salvo = salvo;
		this.interval = interval;
		this.cooldown = cooldown;
		this.itemId = itemId;
	}
}
