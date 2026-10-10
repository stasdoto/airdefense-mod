package com.stasdoto.airdefense.street;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 1.40: an airport stand's mark (where a parked plane's nose stops): a block of the apron's yellow, facing the way the
 * planes park, with the stand's number - from it the client lays out the whole airport (see nation.Airports).
 */
public class StandBlock extends StreetBlock {
	public static final IntegerProperty INDEX = IntegerProperty.create("index", 0, 7);

	public StandBlock(Properties properties) {
		super(properties, new double[]{0, 0, 0, 16, 16, 16});
		registerDefaultState(defaultBlockState().setValue(INDEX, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(INDEX);
	}
}
