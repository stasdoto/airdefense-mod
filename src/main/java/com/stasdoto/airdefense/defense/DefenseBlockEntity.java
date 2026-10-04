package com.stasdoto.airdefense.defense;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.Effects;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModBlockEntities;
import com.stasdoto.airdefense.registry.ModSounds;

/** The radar + fire control: finds incoming threats, keeps the siren going and fires at them. */
public class DefenseBlockEntity extends BlockEntity {
	private boolean enabled = true;
	private int ammo = -1;
	private int reloadTimer;
	private int fireTimer;
	private int sirenTimer;

	public DefenseBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DEFENSE, pos, state);
	}

	public DefenseType type() {
		return getBlockState().getBlock() instanceof DefenseBlock block ? block.type : DefenseType.PATRIOT;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, DefenseBlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (true) {
			com.stasdoto.airdefense.vehicle.VehicleType vt = com.stasdoto.airdefense.vehicle.VehicleType.valueOf(be.type().name());
			com.stasdoto.airdefense.vehicle.VehicleEntity.replaceBlock(serverLevel, pos, vt, state.getValue(DefenseBlock.FACING));
			return;
		}
		DefenseType type = be.type();
		if (be.ammo < 0) {
			be.ammo = type.magazine;
		}
		if (be.reloadTimer > 0 && --be.reloadTimer == 0) {
			be.ammo = type.magazine;
			be.setChanged();
		}
		if (be.fireTimer > 0) {
			be.fireTimer--;
		}
		if (be.sirenTimer > 0) {
			be.sirenTimer--;
		}
		if (!be.enabled || (level.getGameTime() + pos.asLong()) % 3 != 0) {
			return;
		}
		be.scanAndEngage(serverLevel, type);
	}

	private void scanAndEngage(ServerLevel level, DefenseType type) {
		Vec3 radar = Vec3.atCenterOf(worldPosition).add(0, 1.5, 0);
		double range = type.range;
		AABB box = new AABB(radar.x - range, radar.y - range, radar.z - range, radar.x + range, radar.y + range, radar.z + range);
		List<MissileEntity> threats = level.getEntitiesOfClass(MissileEntity.class, box,
				m -> m.getMissileType().threat && m.isAlive() && m.distanceToSqr(radar) < range * range);
		if (threats.isEmpty()) {
			return;
		}
		if (sirenTimer == 0) {
			level.playSound(null, radar.x, radar.y, radar.z, ModSounds.SIREN, SoundSource.BLOCKS, 3.0f, 1.0f);
			sirenTimer = 130;
		}
		if (fireTimer > 0 || ammo <= 0) {
			return;
		}
		MissileEntity target = threats.stream()
				.filter(m -> type == DefenseType.GEPARD || m.getEngagedBy() < type.shotsPerTarget(m.getMissileType().kind))
				.filter(m -> m.getY() > level.getMinY() && !isAboutToLeave(m, radar, range) && worthEngagingNow(m, radar, range))
				.min(Comparator.comparingDouble((MissileEntity m) -> type.priority(m.getMissileType().kind) * 1e6 + m.distanceToSqr(radar)))
				.orElse(null);
		if (target == null) {
			return;
		}
		if (type == DefenseType.GEPARD) {
			gunBurst(level, radar, target);
		} else {
			launchInterceptor(level, type, target);
		}
		fireTimer = type.interval;
		ammo--;
		if (ammo == 0) {
			reloadTimer = type.reload;
		}
		setChanged();
	}

	/**
	 * Ballistic missiles and rockets are engaged on the way down (terminal phase), like a real Patriot battery does,
	 * unless they are already close. Everything else is engaged as soon as it is in range.
	 */
	private static boolean worthEngagingNow(MissileEntity m, Vec3 radar, double range) {
		MissileType.Kind kind = m.getMissileType().kind;
		if (kind != MissileType.Kind.BALLISTIC && kind != MissileType.Kind.ROCKET) {
			return true;
		}
		return m.getFlightVelocity().y < 0 || m.distanceToSqr(radar) < range * range * 0.3;
	}

	/** A threat flying away from us and already far out is not worth an interceptor. */
	private static boolean isAboutToLeave(MissileEntity m, Vec3 radar, double range) {
		Vec3 rel = m.position().subtract(radar);
		return rel.lengthSqr() > range * range * 0.64 && rel.dot(m.getFlightVelocity()) > 0;
	}

	private void launchInterceptor(ServerLevel level, DefenseType type, MissileEntity target) {
		Direction facing = getBlockState().getValue(DefenseBlock.FACING);
		Vec3 from = Vec3.atBottomCenterOf(worldPosition).add(0, 2.0, 0)
				.add(-facing.getStepX() * 0.3, 0, -facing.getStepZ() * 0.3);
		Vec3 toTarget = target.position().subtract(from);
		Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
		Vec3 dir = (flat.lengthSqr() > 1e-4 ? flat.normalize().scale(0.55) : Vec3.ZERO).add(0, 1, 0).normalize();
		MissileEntity.launchInterceptor(level, type.interceptor, from, dir, target);
		Effects.launchBlast(level, from.add(0, -1.2, 0), type.interceptor);
		RandomSource r = level.getRandom();
		level.playSound(null, from.x, from.y, from.z, ModSounds.LAUNCH_LIGHT, SoundSource.BLOCKS, 3.0f, 0.95f + r.nextFloat() * 0.15f);
		level.playSound(null, from.x, from.y, from.z, ModSounds.RADAR_LOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	/** Gepard: a 6-round burst from the twin 35 mm guns with tracer, each round can hit or miss. */
	private void gunBurst(ServerLevel level, Vec3 muzzle, MissileEntity target) {
		RandomSource r = level.getRandom();
		MissileType.Kind kind = target.getMissileType().kind;
		double dist = target.position().distanceTo(muzzle);
		double baseChance = DefenseType.gunHitChance(kind) * (1.0 - 0.5 * dist / type().range);
		// Shells fly ~60 blocks/s: aim where the target will be.
		double flightTicks = dist / 3.0;
		Vec3 aim = target.position().add(target.getFlightVelocity().scale(flightTicks));
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.GEPARD_BURST, SoundSource.BLOCKS, 3.0f, 0.95f + r.nextFloat() * 0.1f);
		for (int round = 0; round < 6; round++) {
			boolean hit = r.nextDouble() < baseChance;
			Vec3 end = hit ? aim : aim.add(r.nextGaussian() * 2.5, r.nextGaussian() * 2.5, r.nextGaussian() * 2.5);
			Effects.tracer(level, muzzle.add(r.nextGaussian() * 0.2, 0, r.nextGaussian() * 0.2), end);
			if (hit && target.isAlive()) {
				target.hurtServer(level, level.damageSources().generic(), 1.5f);
			}
		}
	}

	public void toggle() {
		enabled = !enabled;
		setChanged();
	}

	public Component status() {
		DefenseType type = type();
		Component name = getBlockState().getBlock().getName();
		Component state = Component.translatable(enabled ? "message.airdefense.status.on" : "message.airdefense.status.off");
		Component ammoText = reloadTimer > 0
				? Component.translatable("message.airdefense.status.reload", reloadTimer / 20 + 1)
				: Component.translatable("message.airdefense.status.ammo", Math.max(ammo, 0), type.magazine);
		return Component.translatable("message.airdefense.status.defense", name, state, ammoText, (int) type.range);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("enabled", enabled);
		output.putInt("ammo", ammo);
		output.putInt("reload", reloadTimer);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		enabled = input.getBooleanOr("enabled", true);
		ammo = input.getIntOr("ammo", -1);
		reloadTimer = input.getIntOr("reload", 0);
	}
}
