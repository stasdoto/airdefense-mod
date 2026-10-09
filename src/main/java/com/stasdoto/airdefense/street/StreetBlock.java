package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.35: a piece of street furniture (a lamp head, a traffic light, a sign, a bench, a bin, a bus stop...): one block
 * whose model is drawn for it (it may reach out over the next blocks), turned to face the way it was put down - its
 * front towards the player who put it, or, in the towns, towards the street.
 */
public class StreetBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final VoxelShape[] shapes;

	/** {@code box} = its outline in the block when it faces north (x0, y0, z0, x1, y1, z1 in sixteenths). */
	public StreetBlock(Properties properties, double[] box) {
		super(properties);
		this.shapes = new VoxelShape[4];
		for (Direction d : Direction.Plane.HORIZONTAL) {
			shapes[d.get2DDataValue()] = turned(box, d);
		}
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	/** The north-facing box turned to face {@code d} (clockwise seen from above, like the blockstates turn the model). */
	static VoxelShape turned(double[] b, Direction d) {
		double x0 = b[0];
		double z0 = b[2];
		double x1 = b[3];
		double z1 = b[5];
		for (int k = 0; k < (d.get2DDataValue() + 2) % 4; k++) {
			// get2DDataValue: south 0, west 1, north 2, east 3 - from north, east is one turn, south two, west three.
			double nx0 = 16 - z1;
			double nx1 = 16 - z0;
			z0 = x0;
			z1 = x1;
			x0 = nx0;
			x1 = nx1;
		}
		return Block.box(x0, b[1], z0, x1, b[4], z1);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes[state.getValue(FACING).get2DDataValue()];
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
