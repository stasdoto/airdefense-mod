package com.stasdoto.airdefense.client.vehicle;

import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleGeometry;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * "As if you really sit in the tank" (1.26): the gunner of a tank, an infantry fighting vehicle, an armoured car or a
 * gunboat looks through the gun sight - the camera sits in the sight on the turret, the vehicle itself is not drawn,
 * the screen is the sight's eyepiece with its reticle, rangefinder and turret indicator; Z steps the magnification,
 * N switches the thermal channel on and off. The driver of a tracked fighting vehicle can look through his periscope
 * instead of sitting with his head out of the hatch. C switches between the sight (or periscope) and the seat.
 */
public final class GunnerSight {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(AirDefense.id("crew"));
	public static final KeyMapping VIEW = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.airdefense.view", InputConstants.KEY_C, CATEGORY));
	public static final KeyMapping ZOOM = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.airdefense.zoom", InputConstants.KEY_Z, CATEGORY));
	public static final KeyMapping THERMAL = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.airdefense.thermal", InputConstants.KEY_N, CATEGORY));
	public static final KeyMapping SMOKE = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.airdefense.smoke", InputConstants.KEY_X, CATEGORY));

	/** How the field of view is narrowed at each step of magnification. */
	private static final float[] ZOOMS = {1f, 1 / 3.5f, 1 / 8f};
	private static final String[] ZOOM_NAMES = {"1.0x", "3.5x", "8.0x"};

	public enum Kind {
		NONE, SIGHT, PERISCOPE
	}

	/** Looking through the sight (or periscope) rather than from the seat. */
	private static boolean through = true;
	private static int zoom;
	public static boolean thermal;
	private static int lastVehicle = -1;
	/** For the automated test: how many frames the camera was moved into a sight. */
	public static int framesInSight;

	private GunnerSight() {
	}

	/** Loads the class during the client's start-up, so its keys are registered in time. */
	public static void init() {
	}

	/** Does this seat of this vehicle have a sight or a periscope? */
	public static Kind kindFor(VehicleEntity v, LocalPlayer p) {
		VehicleType t = v.getVehicleType();
		if (!v.isAlive() || t.isAir()) {
			return Kind.NONE;
		}
		boolean turretGun = t.isArmed() && t.geometry.turret() != null;
		if (turretGun && v.shooter() == p) {
			return Kind.SIGHT;
		}
		if (turretGun && t.tracked() && v.isDriver(p) && v.getGunner() != null) {
			return Kind.PERISCOPE;
		}
		return Kind.NONE;
	}

	/** The view in use right now (NONE: the ordinary seat view). */
	public static Kind active() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !(p.getVehicle() instanceof VehicleEntity v) || !through || !mc.options.getCameraType().isFirstPerson()) {
			return Kind.NONE;
		}
		return kindFor(v, p);
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		VehicleEntity v = p != null && p.getVehicle() instanceof VehicleEntity ve ? ve : null;
		int id = v == null ? -1 : v.getId();
		if (id != lastVehicle) {
			// A new seat: straight into the sight ("you sit down right in the turret") - except the driver of a wheeled
			// vehicle, who first sits in his cab (C takes him to the gun).
			lastVehicle = id;
			through = v == null || !v.isDriver(p) || v.getVehicleType().tracked();
			zoom = 0;
		}
		Kind k = v == null ? Kind.NONE : kindFor(v, p);
		while (VIEW.consumeClick()) {
			if (k != Kind.NONE) {
				through = !through;
			}
		}
		while (ZOOM.consumeClick()) {
			if (k == Kind.SIGHT && through) {
				zoom = (zoom + 1) % ZOOMS.length;
			}
		}
		while (THERMAL.consumeClick()) {
			if (k != Kind.NONE && through) {
				thermal = !thermal;
			}
		}
		if (k == Kind.NONE || !through) {
			thermal = false;
		}
	}

	/** Is the picture thermal right now (the sight's thermal channel in use)? */
	public static boolean thermalOn() {
		return thermal && active() != Kind.NONE;
	}

	public static float fovMultiplier() {
		return active() == Kind.SIGHT ? ZOOMS[zoom] : 1f;
	}

	public static float sensitivity() {
		float f = fovMultiplier();
		return f < 0.95f ? Math.max(0.15f, f * 1.2f) : 1f;
	}

	public static String zoomName() {
		return ZOOM_NAMES[zoom];
	}

	/** Where the camera goes for the current view, or null for the game's own. */
	@Nullable
	public static Vec3 cameraPos(float pt) {
		Kind k = active();
		if (k == Kind.NONE) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		VehicleEntity v = (VehicleEntity) mc.player.getVehicle();
		framesInSight++;
		return k == Kind.SIGHT ? sightPoint(v, pt) : periscopePoint(v, mc.player, pt);
	}

	/** The gunner's sight: on the turret roof, to the right of the gun and a little forward of its trunnions. */
	public static Vec3 sightPoint(VehicleEntity v, float pt) {
		VehicleGeometry.Geometry g = v.getVehicleType().geometry;
		float[] tp = g.turretPivot();
		float[] ep = g.elevatorPivot();
		double x = ep[0] + 0.42;
		double y = ep[1] + 0.32;
		double z = ep[2] + 0.25;
		double a = Math.toRadians(Mth.rotLerp(pt, v.turretYawO, v.turretYaw));
		double xr = x * Math.cos(a) + z * Math.sin(a);
		double zr = z * Math.cos(a) - x * Math.sin(a);
		return local(v, tp[0] + xr, tp[1] + y, tp[2] + zr, pt);
	}

	/** The driver's periscope: just above the hatch in front of his seat. */
	private static Vec3 periscopePoint(VehicleEntity v, LocalPlayer p, float pt) {
		VehicleGeometry.Seat s = v.getVehicleType().geometry.seats()[0];
		return local(v, s.x(), s.y() + 0.78, s.z() + 0.35, pt);
	}

	private static Vec3 local(VehicleEntity v, double x, double y, double z, float pt) {
		float yaw = Mth.rotLerp(pt, v.yRotO, v.getYRot()) * Mth.DEG_TO_RAD;
		Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 r = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 base = v.getPosition(pt);
		return base.add(r.x * x + f.x * z, y + Mth.lerp(pt, v.liftO, v.lift), r.z * x + f.z * z);
	}

	/** Is this vehicle the one the camera sits in (it is not drawn then)? */
	public static boolean hides(VehicleEntity v) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getVehicle() == v && active() != Kind.NONE;
	}
}
