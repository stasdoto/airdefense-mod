package com.stasdoto.airdefense.launcher;

import net.minecraft.sounds.SoundEvent;

import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModSounds;

/** Strike launchers. Salvo = missiles per launch command, interval/cooldown in ticks, spread = aiming error in blocks. */
public enum LauncherType {
	ISKANDER(MissileType.ISKANDER, 2, 70, 400, 1.2, 2.4, -0.2, ModSounds.LAUNCH_HEAVY),
	KALIBR(MissileType.KALIBR, 2, 45, 300, 1.8, 1.6, 0.3, ModSounds.LAUNCH_LIGHT),
	SHAHED(MissileType.SHAHED, 5, 30, 300, 3.0, 1.5, 0.6, ModSounds.LAUNCH_LIGHT),
	HIMARS(MissileType.GMLRS, 6, 8, 360, 2.0, 1.9, -0.6, ModSounds.LAUNCH_MLRS);

	public final MissileType missile;
	public final int salvo;
	public final int interval;
	public final int cooldown;
	public final double spread;
	/** Launch point relative to the block centre: height above the block bottom and offset forward. */
	public final double launchHeight;
	public final double launchForward;
	public final SoundEvent sound;

	LauncherType(MissileType missile, int salvo, int interval, int cooldown, double spread, double launchHeight,
			double launchForward, SoundEvent sound) {
		this.missile = missile;
		this.salvo = salvo;
		this.interval = interval;
		this.cooldown = cooldown;
		this.spread = spread;
		this.launchHeight = launchHeight;
		this.launchForward = launchForward;
		this.sound = sound;
	}
}
