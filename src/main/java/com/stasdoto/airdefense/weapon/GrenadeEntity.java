package com.stasdoto.airdefense.weapon;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.Effects;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModItems;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * A thrown F-1 hand grenade: flies, bounces off walls and the ground, rolls to a stop, and goes off 3.5 s after the
 * pin was pulled. The physics run on both sides (smooth on screen), the explosion only on the server.
 */
public class GrenadeEntity extends Entity implements ItemSupplier {
	public static final int FUSE = 70;
	/** Debug counter read by the automated test. */
	public static int exploded;

	private int fuse = FUSE;
	@Nullable
	private Entity thrower;
	private ItemStack display;

	public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
		super(type, level);
	}

	public static GrenadeEntity throwFrom(ServerLevel level, LivingEntity thrower, float power) {
		GrenadeEntity g = new GrenadeEntity(ModEntities.GRENADE, level);
		Vec3 look = thrower.getLookAngle();
		g.setPos(thrower.getEyePosition().add(look.scale(0.5)).add(0, -0.2, 0));
		Vec3 own = thrower.getDeltaMovement();
		g.setDeltaMovement(look.scale(power).add(own.x, thrower.onGround() ? 0 : own.y, own.z).add(0, 0.1, 0));
		g.thrower = thrower;
		level.addFreshEntity(g);
		return g;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		if (display == null) {
			display = new ItemStack(ModItems.F1_GRENADE);
		}
		return display;
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement().add(0, isInWater() ? -0.02 : -0.045, 0);
		setDeltaMovement(v);
		move(MoverType.SELF, v);
		Vec3 after = getDeltaMovement();
		// Bounce: a blocked component comes back reversed and much weaker.
		double x = after.x;
		double y = after.y;
		double z = after.z;
		boolean bounced = false;
		if (horizontalCollision) {
			if (Math.abs(after.x) < 1e-7 && Math.abs(v.x) > 0.02) {
				x = -v.x * 0.35;
				bounced = true;
			}
			if (Math.abs(after.z) < 1e-7 && Math.abs(v.z) > 0.02) {
				z = -v.z * 0.35;
				bounced = true;
			}
		}
		if (verticalCollision) {
			y = v.y < -0.12 ? -v.y * 0.3 : 0;
			bounced |= v.y < -0.12;
			// Rolling on the ground.
			x *= 0.7;
			z *= 0.7;
		}
		double drag = isInWater() ? 0.8 : 0.985;
		setDeltaMovement(x * drag, y * (isInWater() ? 0.8 : 1), z * drag);
		if (bounced && !level().isClientSide() && v.lengthSqr() > 0.03) {
			level().playSound(null, getX(), getY(), getZ(), ModSounds.GRENADE_BOUNCE, SoundSource.PLAYERS, 0.5f, 0.9f + random.nextFloat() * 0.2f);
		}
		if (level() instanceof ServerLevel server && --fuse <= 0) {
			discard();
			exploded++;
			Effects.grenade(server, this, position().add(0, 0.25, 0), thrower);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		fuse = input.getIntOr("fuse", FUSE);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("fuse", fuse);
	}
}
