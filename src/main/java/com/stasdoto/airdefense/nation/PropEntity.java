package com.stasdoto.airdefense.nation;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 1.41: a thing of the working country seen only on the player's own client - a lorry on a highway, a tractor in a
 * field, a tower crane over a building site. Its manager (client.nation.Traffic, Farms, Cranes) moves it; this only
 * holds its look and pose (and a turning part's angle: the crane's top).
 */
public class PropEntity extends Entity {
	public String model = "lorry_box";
	public double[] pose = new double[5];
	public double[] poseO = new double[5];
	public float spin;
	public float spinO;
	/** For its manager: how far along its way, how fast, which way, anything else. */
	public double s;
	public double speed;
	public int dir = 1;
	public int timer;
	public Object way;

	public PropEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Sets its pose (x, y, z, yaw, pitch), keeping the last one for drawing between ticks. */
	public void move(double x, double y, double z, double yaw, double pitch) {
		poseO = pose;
		pose = new double[]{x, y, z, yaw, pitch};
		setPos(x, y, z);
	}

	public void hold() {
		poseO = pose;
		spinO = spin;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			discard();
			return;
		}
		tickCount++;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
