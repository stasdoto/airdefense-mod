package com.stasdoto.airdefense.fort;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * 1.48: a coil of barbed wire. It does not stop you, it holds you: whoever walks into it wades through slowly and gets
 * cut now and then. Vehicles roll over it.
 */
public class BarbedWireBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

	public BarbedWireBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean b) {
		if (entity instanceof VehicleEntity) {
			return;
		}
		entity.makeStuckInBlock(state, new Vec3(0.35, 0.6, 0.35));
		if (level instanceof ServerLevel sl && entity instanceof LivingEntity le && entity.tickCount % 20 == 0
				&& (entity.getDeltaMovement().horizontalDistanceSqr() > 1e-4 || entity.tickCount % 40 == 0)) {
			le.hurtServer(sl, sl.damageSources().cactus(), 1.0f);
		}
	}
}
