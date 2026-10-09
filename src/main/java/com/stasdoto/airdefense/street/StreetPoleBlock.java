package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.35: a lamp post's or a traffic light's pole, one block of it; the bottom one has its foot. Stack them and put a
 * lamp, a traffic light or a sign on top.
 */
public class StreetPoleBlock extends Block {
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	private final VoxelShape shape;

	public StreetPoleBlock(Properties properties, double width) {
		super(properties);
		double a = 8 - Math.max(1.5, width / 2);
		this.shape = Block.box(a, 0, a, 16 - a, 16, 16 - a);
		registerDefaultState(stateDefinition.any().setValue(BOTTOM, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BOTTOM);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BOTTOM, !(context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof StreetPoleBlock));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos from,
			BlockState neighbour, RandomSource random) {
		if (dir == Direction.DOWN) {
			return state.setValue(BOTTOM, !(neighbour.getBlock() instanceof StreetPoleBlock));
		}
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}
}
