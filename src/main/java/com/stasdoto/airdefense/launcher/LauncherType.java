package com.stasdoto.airdefense.launcher;

import net.minecraft.sounds.SoundEvent;

import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Strike launchers. Salvo = missiles per launch command, interval/cooldown in ticks, spread = aiming error in blocks,
 * max range in blocks (scaled down like the flight speeds: what matters is which system reaches further).
 */
public enum LauncherType {
	ISKANDER(MissileType.ISKANDER, 2, 70, 400, 1.2, 2.4, -0.2, ModSounds.LAUNCH_HEAVY, 1500),
	KALIBR(MissileType.KALIBR, 2, 45, 300, 1.8, 1.6, 0.3, ModSounds.LAUNCH_LIGHT, 2500),
	SHAHED(MissileType.SHAHED, 5, 30, 300, 3.0, 1.5, 0.6, ModSounds.LAUNCH_LIGHT, 2000),
	HIMARS(MissileType.GMLRS, 6, 8, 360, 2.0, 1.9, -0.6, ModSounds.LAUNCH_MLRS, 640),
	// 1.30: artillery - howitzers fire a fire mission of six shells, the Grad ripples off all forty rockets. They carry
	// rounds, not one missile per rail; the spread is how widely the shells fall round the aim point at full range.
	MSTA_S(MissileType.SHELL_152, 6, 50, 140, 5.0, ModSounds.ARTY_NEAR, 260, 30, 110),
	M109(MissileType.SHELL_155, 6, 44, 140, 4.5, ModSounds.ARTY_NEAR, 280, 30, 110),
	BM21(MissileType.GRAD, 40, 3, 600, 12.0, ModSounds.LAUNCH_MLRS, 200, 40, 90);

	public final MissileType missile;
	public final int salvo;
	public final int interval;
	public final int cooldown;
	public final double spread;
	/** Launch point relative to the block centre: height above the block bottom and offset forward. */
	public final double launchHeight;
	public final double launchForward;
	public final SoundEvent sound;
	public final int maxRange;
	/** 1.30: rounds carried (artillery: shells or rockets, not one per rail); 0 = one missile per rail. */
	public final int rounds;
	/** Closer than this (blocks) it cannot fire. */
	public final int minRange;

	LauncherType(MissileType missile, int salvo, int interval, int cooldown, double spread, double launchHeight,
			double launchForward, SoundEvent sound, int maxRange) {
		this.missile = missile;
		this.salvo = salvo;
		this.interval = interval;
		this.cooldown = cooldown;
		this.spread = spread;
		this.launchHeight = launchHeight;
		this.launchForward = launchForward;
		this.sound = sound;
		this.maxRange = maxRange * MissileType.RANGE_SCALE;
		this.rounds = 0;
		this.minRange = 24;
	}

	/** Artillery: a gun or a rocket launcher that carries rounds. */
	LauncherType(MissileType missile, int salvo, int interval, int cooldown, double spread, SoundEvent sound, int maxRange, int rounds,
			int minRange) {
		this.missile = missile;
		this.salvo = salvo;
		this.interval = interval;
		this.cooldown = cooldown;
		this.spread = spread;
		this.launchHeight = 2.0;
		this.launchForward = 0;
		this.sound = sound;
		this.maxRange = maxRange * MissileType.RANGE_SCALE;
		this.rounds = rounds;
		this.minRange = minRange;
	}

	/** 1.30: artillery (fires rounds from a barrel or a pack of tubes, with a fire mission's spread). */
	public boolean artillery() {
		return rounds > 0;
	}

	/** A gun (shells) rather than rockets. */
	public boolean gun() {
		return missile.shell();
	}
}
