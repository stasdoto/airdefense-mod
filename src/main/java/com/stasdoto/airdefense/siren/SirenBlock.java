package com.stasdoto.airdefense.siren;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.stasdoto.airdefense.registry.ModBlockEntities;

/**
 * An air raid siren on its pole (1.24): a motor siren with its horn, a junction box with a red lamp that lights while
 * it sounds. It follows its town's alert (switched from the tablet, or raised by itself when a raid comes), or is
 * switched on or off by hand: right click cycles auto / on / off, sneak + right click shows its state.
 */
public class SirenBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Signal> SIGNAL = EnumProperty.create("signal", Signal.class);
	private static final VoxelShape SHAPE = Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);

	public enum Signal implements StringRepresentable {
		OFF("off"), ALERT("alert"), CLEAR("clear");

		private final String name;

		Signal(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		public static Signal of(int s) {
			return s == Sirens.ALERT ? ALERT : s == Sirens.CLEAR ? CLEAR : OFF;
		}
	}

	public SirenBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SIGNAL, Signal.OFF));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SIGNAL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SirenBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, ModBlockEntities.SIREN, SirenBlockEntity::clientTick)
				: createTickerHelper(type, ModBlockEntities.SIREN, SirenBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SirenBlockEntity siren) {
			if (!player.isSecondaryUseActive()) {
				siren.setMode((siren.mode() + 1) % 3);
			}
			player.sendOverlayMessage(Component.translatable("message.airdefense.siren.status",
					Component.translatable("screen.airdefense.siren.mode." + siren.mode()),
					Component.translatable("screen.airdefense.siren.signal." + state.getValue(SIGNAL).getSerializedName())));
		}
		return InteractionResult.SUCCESS;
	}
}
