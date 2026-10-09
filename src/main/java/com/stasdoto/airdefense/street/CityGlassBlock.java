package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** 1.36: a town's glass block (shop fronts, glazed walls) that lights up at night (see {@link WindowLights}). */
public class CityGlassBlock extends TransparentBlock {
	public CityGlassBlock(Properties p) {
		super(p);
		registerDefaultState(defaultBlockState().setValue(WindowLights.LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		super.createBlockStateDefinition(b);
		b.add(WindowLights.LIT);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		WindowLights.tick(state, level, pos);
	}
}
