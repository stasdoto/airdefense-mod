package com.stasdoto.airdefense.siren;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The steel mast an air raid siren stands on (1.24): the foot with its base plate, the part with the switch cabinet
 * (facing the street, like the siren's horn), plain pipe. Put up together with the siren (see SirenItem and the
 * towns' sirens); it has no item of its own.
 */
public class SirenMastBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	private static final VoxelShape PIPE = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);
	private static final VoxelShape BASE = Shapes.or(PIPE, Block.box(4, 0, 4, 12, 1, 12));

	public enum Part implements StringRepresentable {
		BASE("base"), PIPE("pipe"), BOX("box");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public SirenMastBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.PIPE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean onMast = context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof SirenMastBlock;
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(PART, onMast ? Part.PIPE : Part.BASE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PART) == Part.BASE ? BASE : PIPE;
	}

	/** A mast of {@code height} blocks: the foot, the cabinet, then pipe. */
	public static BlockState part(Block mast, Direction facing, int index) {
		Part p = index == 0 ? Part.BASE : index == 1 ? Part.BOX : Part.PIPE;
		return mast.defaultBlockState().setValue(FACING, facing).setValue(PART, p);
	}
}
