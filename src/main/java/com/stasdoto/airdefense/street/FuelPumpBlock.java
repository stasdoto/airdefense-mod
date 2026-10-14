package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 2.0: a filling station's pump: right-click it to fill the vehicle standing by it (see nation.FuelPumps). */
public class FuelPumpBlock extends StreetBlock {
	public FuelPumpBlock(Properties properties, double[] box) {
		super(properties, box);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			com.stasdoto.airdefense.nation.FuelPumps.use(server, pos, player);
		}
		return InteractionResult.SUCCESS;
	}
}
