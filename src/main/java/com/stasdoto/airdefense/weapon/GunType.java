package com.stasdoto.airdefense.weapon;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileType;

/**
 * The small arms (1.24: 35 of them). Damage in half-hearts (a player has 20) per bullet (a shotgun fires several
 * pellets), spread in degrees, range in blocks, times in ticks. A shot is instant (the server traces the line), the
 * tracer the players see flies at {@link #tracerSpeed()}. Launchers fire a {@link MissileType} instead.
 *
 * <p>The order is the network id of a gun: new ones go at the end.
 */
public enum GunType {
	// --- the first five (stage 7), reworked ---
	AK74(new Spec("ak74", "ammo_545").mag(30).rpm(650).auto().dmg(6.0f).spread(2.0f, 0.45f).range(150).reload(46).recoil(1.3f)
			.zoom(0.78f).report(Report.RIFLE).bloc(Bloc.EAST)),
	PKM(new Spec("pkm", "ammo_762").mag(100).rpm(650).auto().dmg(7.5f).spread(3.0f, 1.1f).range(190).reload(110).recoil(1.5f)
			.zoom(0.8f).report(Report.MG).tracer(4).bloc(Bloc.EAST).heavy()),
	SVD(new Spec("svd", "ammo_762").mag(10).interval(7).dmg(22.0f).spread(3.5f, 0.04f).range(320).reload(60).recoil(4.0f)
			.zoom(0.25f).report(Report.SNIPER).scope(Scope.PSO).bloc(Bloc.EAST)),
	PM(new Spec("pm", "ammo_9mm").mag(8).interval(3).dmg(5.0f).spread(2.2f, 0.9f).range(60).reload(30).recoil(2.4f)
			.zoom(0.85f).report(Report.PISTOL).pistol().bloc(Bloc.EAST)),
	RPG7(new Spec("rpg7", "rpg_round").mag(1).interval(10).spread(2.5f, 0.6f).range(220).reload(70).recoil(3.0f).zoom(0.42f)
			.report(Report.ROCKET).rocket(MissileType.RPG).scope(Scope.PGO).bloc(Bloc.EAST)),
	// --- 1.24: the Kalashnikov family and the other Soviet / Russian / Ukrainian arms ---
	AKM(new Spec("akm", "ammo_762x39").mag(30).rpm(600).auto().dmg(7.0f).spread(2.2f, 0.55f).range(140).reload(48).recoil(1.75f)
			.zoom(0.78f).report(Report.RIFLE).bloc(Bloc.EAST)),
	AK12(new Spec("ak12", "ammo_545").mag(30).rpm(700).auto().dmg(6.0f).spread(1.9f, 0.32f).range(160).reload(44).recoil(1.15f)
			.zoom(0.7f).report(Report.RIFLE).dot(Dot.RED).bloc(Bloc.EAST)),
	AKS74U(new Spec("aks74u", "ammo_545").mag(30).rpm(700).auto().dmg(5.2f).spread(2.4f, 0.8f).range(100).reload(42).recoil(1.6f)
			.zoom(0.82f).report(Report.CARBINE).bloc(Bloc.EAST)),
	RPK74(new Spec("rpk74", "ammo_545").mag(45).rpm(600).auto().dmg(6.3f).spread(2.8f, 0.5f).range(180).reload(60).recoil(1.0f)
			.zoom(0.78f).report(Report.MG).tracer(5).bloc(Bloc.EAST)),
	PKP(new Spec("pkp", "ammo_762").mag(100).rpm(650).auto().dmg(7.5f).spread(2.8f, 0.85f).range(200).reload(105).recoil(1.3f)
			.zoom(0.78f).report(Report.MG).tracer(4).bloc(Bloc.EAST).heavy()),
	SV98(new Spec("sv98", "ammo_762").mag(10).interval(28).bolt().dmg(27.0f).spread(4.5f, 0.02f).range(380).reload(70).recoil(4.5f)
			.zoom(0.2f).report(Report.SNIPER).scope(Scope.MILDOT).bloc(Bloc.EAST)),
	VSS(new Spec("vss", "ammo_9x39").mag(20).rpm(700).auto().dmg(8.5f).spread(2.4f, 0.12f).range(120).reload(44).recoil(1.0f)
			.zoom(0.3f).report(Report.SUPPRESSED).scope(Scope.PSO).bloc(Bloc.EAST)),
	ASVAL(new Spec("asval", "ammo_9x39").mag(20).rpm(900).auto().dmg(8.0f).spread(2.2f, 0.5f).range(110).reload(42).recoil(1.2f)
			.zoom(0.78f).report(Report.SUPPRESSED).bloc(Bloc.EAST)),
	SAIGA12(new Spec("saiga12", "ammo_12g").mag(8).interval(5).dmg(3.2f).pellets(8).spread(5.5f, 4.2f).range(40).reload(56).recoil(3.0f)
			.zoom(0.85f).report(Report.SHOTGUN).bloc(Bloc.EAST)),
	BIZON(new Spec("bizon", "ammo_9mm").mag(64).rpm(680).auto().dmg(4.6f).spread(2.4f, 0.9f).range(70).reload(54).recoil(0.8f)
			.zoom(0.82f).report(Report.PISTOL).bloc(Bloc.EAST)),
	RPG22(new Spec("rpg22", null).mag(1).interval(10).spread(2.5f, 0.7f).range(150).recoil(3.0f).zoom(0.65f).report(Report.ROCKET)
			.rocket(MissileType.RPG22).disposable().bloc(Bloc.EAST)),
	FORT12(new Spec("fort12", "ammo_9mm").mag(12).interval(3).dmg(5.0f).spread(2.0f, 0.85f).range(60).reload(30).recoil(2.2f)
			.zoom(0.85f).report(Report.PISTOL).pistol().bloc(Bloc.BOTH)),
	FORT221(new Spec("fort221", "ammo_545").mag(30).rpm(750).auto().dmg(6.0f).spread(2.0f, 0.38f).range(150).reload(44).recoil(1.1f)
			.zoom(0.7f).report(Report.RIFLE).dot(Dot.HOLO).bloc(Bloc.BOTH)),
	// --- 1.24: NATO arms ---
	M4A1(new Spec("m4a1", "ammo_556").mag(30).rpm(800).auto().dmg(5.8f).spread(2.0f, 0.4f).range(150).reload(42).recoil(1.0f)
			.zoom(0.72f).report(Report.CARBINE).dot(Dot.HOLO).bloc(Bloc.WEST)),
	M16A4(new Spec("m16a4", "ammo_556").mag(30).rpm(800).burst(3).dmg(6.2f).spread(2.2f, 0.12f).range(190).reload(44).recoil(1.0f)
			.zoom(0.4f).report(Report.RIFLE).scope(Scope.ACOG).bloc(Bloc.WEST)),
	HK416(new Spec("hk416", "ammo_556").mag(30).rpm(850).auto().dmg(6.0f).spread(1.9f, 0.35f).range(160).reload(42).recoil(0.95f)
			.zoom(0.7f).report(Report.CARBINE).dot(Dot.RED).bloc(Bloc.WEST)),
	SCARH(new Spec("scarh", "ammo_762").mag(20).rpm(600).auto().dmg(8.0f).spread(2.4f, 0.35f).range(200).reload(46).recoil(1.8f)
			.zoom(0.7f).report(Report.RIFLE).dot(Dot.HOLO).bloc(Bloc.WEST)),
	M249(new Spec("m249", "ammo_556").mag(100).rpm(800).auto().dmg(6.0f).spread(3.0f, 0.95f).range(180).reload(110).recoil(1.0f)
			.zoom(0.78f).report(Report.MG).tracer(4).bloc(Bloc.WEST).heavy()),
	M240B(new Spec("m240b", "ammo_762").mag(100).rpm(650).auto().dmg(7.8f).spread(3.2f, 1.0f).range(210).reload(120).recoil(1.4f)
			.zoom(0.78f).report(Report.MG).tracer(4).bloc(Bloc.WEST).heavy()),
	M110(new Spec("m110", "ammo_762").mag(20).interval(5).dmg(18.0f).spread(3.5f, 0.05f).range(300).reload(52).recoil(3.0f)
			.zoom(0.25f).report(Report.SNIPER).scope(Scope.MILDOT).bloc(Bloc.WEST)),
	M82(new Spec("m82", "ammo_127").mag(10).interval(10).dmg(46.0f).spread(6.0f, 0.03f).range(450).reload(80).recoil(7.0f)
			.zoom(0.14f).report(Report.HEAVY).scope(Scope.MILDOT).bloc(Bloc.WEST).heavy().antiMateriel()),
	AWM(new Spec("awm", "ammo_762").mag(5).interval(30).bolt().dmg(30.0f).spread(4.5f, 0.02f).range(420).reload(64).recoil(5.0f)
			.zoom(0.17f).report(Report.SNIPER).scope(Scope.MILDOT).bloc(Bloc.WEST)),
	MP5(new Spec("mp5", "ammo_9mm").mag(30).rpm(800).auto().dmg(4.8f).spread(2.0f, 0.7f).range(75).reload(40).recoil(0.6f)
			.zoom(0.8f).report(Report.PISTOL).bloc(Bloc.WEST)),
	GLOCK17(new Spec("glock17", "ammo_9mm").mag(17).interval(2).dmg(5.2f).spread(2.0f, 0.8f).range(60).reload(28).recoil(2.0f)
			.zoom(0.85f).report(Report.PISTOL).pistol().bloc(Bloc.WEST)),
	M870(new Spec("m870", "ammo_12g").mag(7).interval(16).pump().dmg(3.4f).pellets(8).spread(5.0f, 3.8f).range(40).reload(70)
			.recoil(3.5f).zoom(0.85f).report(Report.SHOTGUN).bloc(Bloc.WEST)),
	M32(new Spec("m32", "ammo_40mm").mag(6).interval(10).spread(2.5f, 0.8f).range(150).reload(90).recoil(2.5f).zoom(0.75f)
			.report(Report.GRENADE).rocket(MissileType.G40).bloc(Bloc.WEST)),
	AT4(new Spec("at4", null).mag(1).interval(10).spread(2.5f, 0.6f).range(200).recoil(3.5f).zoom(0.6f).report(Report.ROCKET)
			.rocket(MissileType.AT4).disposable().bloc(Bloc.WEST)),
	CG84(new Spec("cg84", "cg_round").mag(1).interval(10).spread(2.5f, 0.5f).range(260).reload(80).recoil(3.5f).zoom(0.33f)
			.report(Report.ROCKET).rocket(MissileType.CG84).scope(Scope.LAUNCHER).bloc(Bloc.WEST)),
	NLAW(new Spec("nlaw", null).mag(1).interval(10).spread(2.5f, 0.4f).range(260).recoil(2.0f).zoom(0.4f).report(Report.ROCKET)
			.rocket(MissileType.NLAW).scope(Scope.LAUNCHER).disposable().bloc(Bloc.WEST)),
	JAVELIN(new Spec("javelin", "javelin_missile").mag(1).interval(10).spread(2.0f, 0.3f).range(400).reload(120).recoil(1.5f)
			.zoom(0.3f).report(Report.ROCKET).rocket(MissileType.JAVELIN).scope(Scope.JAVELIN).bloc(Bloc.WEST));

	/** What a shot sounds like (the client picks the near/far layers). */
	public enum Report { RIFLE, MG, SNIPER, PISTOL, ROCKET, CARBINE, SHOTGUN, SUPPRESSED, HEAVY, GRENADE }

	/** The picture while aiming through optics (none = iron sights or a collimator on the model). */
	public enum Scope { NONE, PSO, MILDOT, ACOG, JAVELIN, PGO, LAUNCHER }

	/** A collimator's reticle drawn in the middle while aiming (the model's window cannot show it). */
	public enum Dot { NONE, RED, HOLO }

	/** Whose armies carry it (soldiers of a country get arms of its side). */
	public enum Bloc { EAST, WEST, BOTH }

	/** How the trigger works. */
	public enum Action { SEMI, AUTO, BURST, BOLT, PUMP }

	public final String id;
	/** The ammunition item, or null for a one-shot launcher (the tube is thrown away). */
	@Nullable
	public final String ammoId;
	public final int magazine;
	/** Ticks between two shots (may be fractional: 800 rounds a minute = 1.5). */
	public final float interval;
	public final Action action;
	public final boolean auto;
	/** Rounds per trigger pull in burst mode. */
	public final int burst;
	public final float damage;
	/** Bullets (pellets) per shot: 1, or a shotgun's 8. */
	public final int pellets;
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
	@Nullable
	public final MissileType rocket;
	public final boolean disposable;
	public final Scope scope;
	public final Dot dot;
	public final Bloc bloc;
	public final boolean pistol;
	/** Heavy: slower to move with while aiming. */
	public final boolean heavy;
	/** A heavy rifle that also hurts vehicles. */
	public final boolean antiMateriel;

	GunType(Spec s) {
		this.id = s.id;
		this.ammoId = s.ammo;
		this.magazine = s.mag;
		this.interval = s.interval;
		this.action = s.action;
		this.auto = s.action == Action.AUTO;
		this.burst = s.burst;
		this.damage = s.dmg;
		this.pellets = s.pellets;
		this.hipSpread = s.hip;
		this.aimSpread = s.aim;
		this.range = s.range;
		this.reload = s.reload;
		this.recoil = s.recoil;
		this.aimZoom = s.zoom;
		this.report = s.report;
		this.tracerEvery = s.tracer;
		this.rocket = s.rocket;
		this.disposable = s.disposable;
		this.scope = s.scope;
		this.dot = s.dot;
		this.bloc = s.bloc;
		this.pistol = s.pistol;
		this.heavy = s.heavy;
		this.antiMateriel = s.antiMateriel;
	}

	@Nullable
	public Item ammo() {
		return ammoId == null ? null : BuiltInRegistries.ITEM.getValue(AirDefense.id(ammoId));
	}

	/** The translation key of what it is loaded with (the launcher itself for a one-shot tube). */
	public String ammoKey() {
		return "item.airdefense." + (ammoId == null ? id : ammoId);
	}

	public boolean rocket() {
		return rocket != null;
	}

	/** A guided missile that needs the target locked before it can be fired. */
	public boolean needsLock() {
		return rocket == MissileType.JAVELIN;
	}

	/** Rifles, machine guns and launchers are held with both hands; a pistol in one (two when aiming). */
	public boolean longGun() {
		return !pistol;
	}

	/** Looking through a scope (the screen shows its picture, the gun model is hidden). */
	public boolean scoped() {
		return scope != Scope.NONE;
	}

	/** Blocks per tick of the visible tracer (the hit itself is instant). */
	public double tracerSpeed() {
		return switch (report) {
			case PISTOL, SUPPRESSED -> 14;
			case SHOTGUN -> 18;
			case SNIPER -> 40;
			case HEAVY -> 45;
			default -> 30;
		};
	}

	/** How far away the report reaches other players (blocks). */
	public double hearing() {
		return switch (report) {
			case SNIPER, HEAVY -> 800;
			case PISTOL -> 300;
			case SUPPRESSED -> 90;
			case SHOTGUN -> 400;
			default -> 600;
		};
	}

	/** Damage falls off over the second half of the range, down to 55 % at the end (shot faster). */
	public float damageAt(double distance) {
		double k = distance <= range * 0.5 ? 1 : 1 - 0.45 * (distance - range * 0.5) / (range * 0.5);
		if (pellets > 1) {
			k = distance <= range * 0.3 ? 1 : 1 - 0.75 * Math.min(1, (distance - range * 0.3) / (range * 0.7));
		}
		return (float) (damage * Math.max(pellets > 1 ? 0.25 : 0.55, k));
	}

	/** Movement speed factor while aiming. */
	public float aimSpeed() {
		if (scoped() && rocket == null) {
			return 0.35f;
		}
		return heavy || rocket != null ? 0.45f : pistol ? 0.8f : 0.6f;
	}

	public static GunType byId(int id) {
		GunType[] all = values();
		return id >= 0 && id < all.length ? all[id] : AK74;
	}

	/** Builder for the table above. */
	static final class Spec {
		final String id;
		@Nullable
		final String ammo;
		int mag = 30;
		float interval = 2;
		Action action = Action.SEMI;
		int burst;
		float dmg;
		int pellets = 1;
		float hip = 2;
		float aim = 0.5f;
		double range = 150;
		int reload = 40;
		float recoil = 1;
		float zoom = 0.8f;
		Report report = Report.RIFLE;
		int tracer;
		MissileType rocket;
		boolean disposable;
		Scope scope = Scope.NONE;
		Dot dot = Dot.NONE;
		Bloc bloc = Bloc.BOTH;
		boolean pistol;
		boolean heavy;
		boolean antiMateriel;

		Spec(String id, @Nullable String ammo) {
			this.id = id;
			this.ammo = ammo;
		}

		Spec mag(int n) {
			mag = n;
			return this;
		}

		/** Rounds per minute (20 ticks a second). */
		Spec rpm(int rpm) {
			interval = Math.max(1f, 1200f / rpm);
			return this;
		}

		Spec interval(float ticks) {
			interval = ticks;
			return this;
		}

		Spec auto() {
			action = Action.AUTO;
			return this;
		}

		Spec burst(int n) {
			action = Action.BURST;
			burst = n;
			return this;
		}

		Spec bolt() {
			action = Action.BOLT;
			return this;
		}

		Spec pump() {
			action = Action.PUMP;
			return this;
		}

		Spec dmg(float d) {
			dmg = d;
			return this;
		}

		Spec pellets(int n) {
			pellets = n;
			return this;
		}

		Spec spread(float hipDeg, float aimDeg) {
			hip = hipDeg;
			aim = aimDeg;
			return this;
		}

		Spec range(double r) {
			range = r;
			return this;
		}

		Spec reload(int ticks) {
			reload = ticks;
			return this;
		}

		Spec recoil(float r) {
			recoil = r;
			return this;
		}

		Spec zoom(float z) {
			zoom = z;
			return this;
		}

		Spec report(Report r) {
			report = r;
			return this;
		}

		Spec tracer(int every) {
			tracer = every;
			return this;
		}

		Spec rocket(MissileType m) {
			rocket = m;
			return this;
		}

		Spec disposable() {
			disposable = true;
			return this;
		}

		Spec scope(Scope s) {
			scope = s;
			return this;
		}

		Spec dot(Dot d) {
			dot = d;
			return this;
		}

		Spec bloc(Bloc b) {
			bloc = b;
			return this;
		}

		Spec pistol() {
			pistol = true;
			return this;
		}

		Spec heavy() {
			heavy = true;
			return this;
		}

		Spec antiMateriel() {
			antiMateriel = true;
			return this;
		}
	}
}
