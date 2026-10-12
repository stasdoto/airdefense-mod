package com.stasdoto.airdefense.street;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** 1.49: a facade thing that comes in several looks (shop signs, graffiti, notices, washing, balcony junk). */
public class KindBlock extends StreetBlock {
	public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 11);

	public KindBlock(Properties properties, double[] box) {
		super(properties, box);
		registerDefaultState(defaultBlockState().setValue(KIND, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(KIND);
	}
}
