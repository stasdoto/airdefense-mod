package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.39: a block of railway track - sleepers, two rails, the ballast under them (the model reaches into the blocks either
 * side: the gauge is wider than a block). {@link #DIR}: 0 north-south, 1 north-east, 2 east-west, 3 south-east;
 * {@link #LIFT}: how far it is raised in the block, in eighths (so a line climbs smoothly, not in steps).
 */
public class TrackBlock extends Block {
	public static final IntegerProperty DIR = IntegerProperty.create("dir", 0, 3);
	public static final IntegerProperty LIFT = IntegerProperty.create("lift", 0, 7);
	private static final VoxelShape[] SHAPES = new VoxelShape[8];

	static {
		for (int i = 0; i < 8; i++) {
			SHAPES[i] = Block.box(0, 0, 0, 16, i * 2 + 4, 16);
		}
	}

	public TrackBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(DIR, 0).setValue(LIFT, 0));
	}

	/** The rail's top over the block's floor (blocks): where a train's wheels run. */
	public static double railTop(BlockState s) {
		return (s.getValue(LIFT) * 2 + 4) / 16.0;
	}

	/** The way along the track (x, z), one of the two. */
	public static int[] way(int dir) {
		return switch (dir) {
			case 0 -> new int[]{0, 1};
			case 1 -> new int[]{1, -1};
			case 2 -> new int[]{1, 0};
			default -> new int[]{1, 1};
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(DIR, LIFT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction d = context.getHorizontalDirection();
		return defaultBlockState().setValue(DIR, d.getAxis() == Direction.Axis.Z ? 0 : 2);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LIFT)];
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		int d = state.getValue(DIR);
		int turns = switch (rotation) {
			case CLOCKWISE_90 -> 1;
			case CLOCKWISE_180 -> 2;
			case COUNTERCLOCKWISE_90 -> 3;
			default -> 0;
		};
		// Each quarter turn: north-south <-> east-west, north-east <-> south-east.
		return state.setValue(DIR, turns % 2 == 1 ? d ^ 2 : d);
	}
}
