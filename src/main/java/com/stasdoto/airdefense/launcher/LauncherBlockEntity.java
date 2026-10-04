package com.stasdoto.airdefense.launcher;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.Effects;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.registry.ModBlockEntities;

public class LauncherBlockEntity extends BlockEntity {
	private static final int MIN_DISTANCE = 24;

	@Nullable
	private BlockPos target;
	private int cooldown;
	private int salvoLeft;
	private int salvoTimer;

	public LauncherBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LAUNCHER, pos, state);
	}

	public LauncherType type() {
		return getBlockState().getBlock() instanceof LauncherBlock block ? block.type : LauncherType.ISKANDER;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, LauncherBlockEntity be) {
		if (be.cooldown > 0) {
			be.cooldown--;
		}
		if (be.salvoLeft > 0 && --be.salvoTimer <= 0 && level instanceof ServerLevel serverLevel) {
			be.fireOne(serverLevel, state);
			be.salvoLeft--;
			be.salvoTimer = be.type().interval;
			if (be.salvoLeft == 0) {
				be.cooldown = be.type().cooldown;
			}
			be.setChanged();
		}
	}

	public void startSalvo(BlockPos newTarget, Player player) {
		LauncherType type = type();
		if (salvoLeft > 0 || cooldown > 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.reloading", (cooldown + salvoLeft * type.interval) / 20 + 1));
			return;
		}
		double dist = Math.sqrt(newTarget.distSqr(worldPosition));
		if (dist < MIN_DISTANCE) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.too_close", MIN_DISTANCE));
			return;
		}
		target = newTarget;
		salvoLeft = type.salvo;
		salvoTimer = 1;
		setChanged();
		player.sendOverlayMessage(Component.translatable("message.airdefense.launch", newTarget.getX(), newTarget.getY(), newTarget.getZ(), (int) dist));
	}

	/** Redstone pulse: repeat the last strike. */
	public void fireAtStoredTarget() {
		if (target != null && salvoLeft == 0 && cooldown == 0) {
			salvoLeft = type().salvo;
			salvoTimer = 1;
			setChanged();
		}
	}

	private void fireOne(ServerLevel level, BlockState state) {
		if (target == null) {
			salvoLeft = 0;
			return;
		}
		LauncherType type = type();
		Direction facing = state.getValue(LauncherBlock.FACING);
		Vec3 forward = new Vec3(facing.getStepX(), 0, facing.getStepZ());
		Vec3 from = Vec3.atBottomCenterOf(worldPosition).add(0, type.launchHeight, 0).add(forward.scale(type.launchForward));
		var r = level.getRandom();
		Vec3 aim = new Vec3(
				target.getX() + 0.5 + r.nextGaussian() * type.spread,
				target.getY() + 1.0,
				target.getZ() + 0.5 + r.nextGaussian() * type.spread);
		MissileEntity.launchStrike(level, type.missile, from, aim, forward);
		Effects.launchBlast(level, from.add(0, -1, 0), type.missile);
		level.playSound(null, from.x, from.y, from.z, type.sound, SoundSource.BLOCKS, 4.0f, 0.95f + r.nextFloat() * 0.1f);
	}

	public Component status() {
		LauncherType type = type();
		Component name = getBlockState().getBlock().getName();
		Component targetText = target == null
				? Component.translatable("message.airdefense.status.no_target")
				: Component.literal(target.getX() + " " + target.getY() + " " + target.getZ());
		Component ready = salvoLeft > 0
				? Component.translatable("message.airdefense.status.firing", salvoLeft)
				: cooldown > 0
				? Component.translatable("message.airdefense.status.reload", cooldown / 20 + 1)
				: Component.translatable("message.airdefense.status.ready", type.salvo);
		return Component.translatable("message.airdefense.status.launcher", name, targetText, ready);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("has_target", target != null);
		if (target != null) {
			output.putInt("tx", target.getX());
			output.putInt("ty", target.getY());
			output.putInt("tz", target.getZ());
		}
		output.putInt("cooldown", cooldown);
		output.putInt("salvo_left", salvoLeft);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		target = input.getBooleanOr("has_target", false)
				? new BlockPos(input.getIntOr("tx", 0), input.getIntOr("ty", 0), input.getIntOr("tz", 0))
				: null;
		cooldown = input.getIntOr("cooldown", 0);
		salvoLeft = input.getIntOr("salvo_left", 0);
		salvoTimer = 20;
	}
}
