package com.stasdoto.airdefense.client.vehicle;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * 2.0: the chase camera of a ground vehicle's driver in third person. Leave the mouse alone for a moment and the view
 * swings round behind the vehicle - behind where it is going (it lags in a slide), looking a little into the turn; move
 * the mouse and you look round freely. Distance by the vehicle's size (its camera distance). Drivers who also aim a
 * gun (no gunner aboard) keep the free mouse.
 */
public final class ChaseCamera {
	private static long lastFrame;
	private static float lastYaw = Float.NaN;
	private static float lastPitch = Float.NaN;
	private static double idle;
	private static int vehicleId = -1;
	/** For the tests: frames the chase camera turned the view. */
	public static int frames;

	private ChaseCamera() {
	}

	public static void frame(float pt) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 0.016 : Math.min(0.1, (now - lastFrame) / 1e9);
		lastFrame = now;
		if (p == null || !(p.getVehicle() instanceof VehicleEntity v) || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK
				|| !v.isDriver(p) || mc.gui.screen() != null) {
			lastYaw = Float.NaN;
			vehicleId = -1;
			return;
		}
		VehicleType t = v.getVehicleType();
		if (t.isAir() || t.boat || t.isArmed() && v.shooter() == p) {
			lastYaw = Float.NaN;
			return;
		}
		if (vehicleId != v.getId()) {
			vehicleId = v.getId();
			idle = 1.0;
		}
		float yaw = p.getYRot();
		float pitch = p.getXRot();
		if (!Float.isNaN(lastYaw) && (Math.abs(yaw - lastYaw) > 0.05f || Math.abs(pitch - lastPitch) > 0.05f)) {
			// The player moved the mouse: free look for a while.
			idle = 0;
		} else {
			idle += dt;
		}
		if (idle > 1.2) {
			double vx = v.getX() - v.xo;
			double vz = v.getZ() - v.zo;
			double sp = Math.hypot(vx, vz);
			float body = Mth.rotLerp(pt, v.yRotO, v.getYRot());
			float target = body;
			float fwd = (float) (vx * -Mth.sin(body * Mth.DEG_TO_RAD) + vz * Mth.cos(body * Mth.DEG_TO_RAD));
			if (sp > 0.08 && fwd > 0) {
				// Behind where it is going (in a drift that is not where the nose points), blended in with speed.
				float travel = (float) Math.toDegrees(Math.atan2(-vx, vz));
				float k = (float) Math.min(1, (sp - 0.08) / 0.3) * 0.6f;
				target = body + Mth.wrapDegrees(travel - body) * k;
			}
			// Look a little into the turn.
			float yawRate = Mth.wrapDegrees(v.getYRot() - v.yRotO) * 20f;
			target += Mth.clamp(yawRate * 0.18f, -20, 20);
			float ease = (float) (1 - Math.exp(-dt * Math.min(3.0, (idle - 1.2) * 4 + 0.5)));
			float dy = Mth.wrapDegrees(target - yaw) * ease;
			float wantPitch = 14f;
			float dp = (wantPitch - pitch) * ease * 0.5f;
			p.setYRot(yaw + dy);
			p.yRotO += dy;
			p.setXRot(pitch + dp);
			p.xRotO += dp;
			frames++;
		}
		lastYaw = p.getYRot();
		lastPitch = p.getXRot();
	}
}
