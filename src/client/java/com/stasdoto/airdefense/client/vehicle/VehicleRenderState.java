package com.stasdoto.airdefense.client.vehicle;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Everything the vehicle model needs for one frame. Angles in degrees. */
public class VehicleRenderState extends EntityRenderState {
	public float yaw;
	public float pitch;
	public float roll;
	public float lift;
	public float wheelRoll;
	public float steer;
	public float turretYaw;
	public float elevation;
	public float roofOpen;
	public float radarSpin;
	public int loaded;
	public boolean wreck;
	/** The local player looks out of this vehicle's sight: it is not drawn (1.26). */
	public boolean hidden;
}
