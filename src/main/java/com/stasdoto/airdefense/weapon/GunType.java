package com.stasdoto.airdefense.weapon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import com.stasdoto.airdefense.AirDefense;

/**
 * The small arms. Damage in half-hearts (a player has 20), spread in degrees, range in blocks, times in ticks.
 * A shot is instant (the server traces the line), the tracer the players see flies at {@link #tracerSpeed()}.
 */
public enum GunType {
	//   id      ammo item   mag  int  auto   dmg    hip    aim    range reload recoil zoom   sound          tracer
	AK74("ak74", "ammo_545", 30, 2, true, 6.0f, 2.0f, 0.45f, 150, 46, 1.3f, 0.78f, Report.RIFLE, 0),
	PKM("pkm", "ammo_762", 100, 2, true, 7.5f, 3.0f, 1.2f, 190, 110, 1.5f, 0.8f, Report.MG, 4),
	SVD("svd", "ammo_762", 10, 7, false, 22.0f, 3.5f, 0.04f, 320, 60, 4.0f, 0.25f, Report.SNIPER, 0),
	PM("pm", "ammo_9mm", 8, 3, false, 5.0f, 2.2f, 0.9f, 60, 30, 2.4f, 0.85f, Report.PISTOL, 0),
	/** RPG-7: fires an unguided rocket grenade (a {@link com.stasdoto.airdefense.missile.MissileType#RPG}). */
	RPG7("rpg7", "rpg_round", 1, 10, false, 0f, 2.5f, 0.6f, 220, 70, 3.0f, 0.6f, Report.ROCKET, 0);

	/** What a shot sounds like (the client picks the near/far layers). */
	public enum Report { RIFLE, MG, SNIPER, PISTOL, ROCKET }

	public final String id;
	public final String ammoId;
	public final int magazine;
	/** Ticks between two shots (2 = 600 rounds a minute). */
	public final int interval;
	public final boolean auto;
	public final float damage;
	public final float hipSpread;
	public final float aimSpread;
	public final double range;
	public final int reload;
	/** How far the muzzle jumps up per shot, degrees. */
	public final float recoil;
	/** Field of view while aiming (1 = no zoom, 0.25 = 4x scope). */
	public final float aimZoom;
	public final Report report;
	/** Every n-th round is a bright tracer (0 = none, only the faint streak every bullet leaves). */
	public final int tracerEvery;

	GunType(String id, String ammoId, int magazine, int interval, boolean auto, float damage, float hipSpread, float aimSpread,
			double range, int reload, float recoil, float aimZoom, Report report, int tracerEvery) {
		this.id = id;
		this.ammoId = ammoId;
		this.magazine = magazine;
		this.interval = interval;
		this.auto = auto;
		this.damage = damage;
		this.hipSpread = hipSpread;
		this.aimSpread = aimSpread;
		this.range = range;
		this.reload = reload;
		this.recoil = recoil;
		this.aimZoom = aimZoom;
		this.report = report;
		this.tracerEvery = tracerEvery;
	}

	public Item ammo() {
		return BuiltInRegistries.ITEM.getValue(AirDefense.id(ammoId));
	}

	public boolean rocket() {
		return this == RPG7;
	}

	/** Rifles, the machine gun and the RPG are held with both hands; the pistol in one (two when aiming). */
	public boolean longGun() {
		return this != PM;
	}

	/** Blocks per tick of the visible tracer (the hit itself is instant). */
	public double tracerSpeed() {
		return switch (this) {
			case PM -> 14;
			case SVD -> 40;
			default -> 30;
		};
	}

	/** Damage falls off over the second half of the range, down to 55 % at the end. */
	public float damageAt(double distance) {
		double k = distance <= range * 0.5 ? 1 : 1 - 0.45 * (distance - range * 0.5) / (range * 0.5);
		return (float) (damage * Math.max(0.55, k));
	}

	public static GunType byId(int id) {
		GunType[] all = values();
		return id >= 0 && id < all.length ? all[id] : AK74;
	}
}
