package com.stasdoto.airdefense.siren;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.registry.ModBlocks;

/**
 * The air raid siren item (1.24). Put on the ground, it goes up on its own steel mast, the siren six metres up like
 * the real ones in towns; on a roof edge or anywhere with sneak, or on top of a mast, just the siren.
 */
public class SirenItem extends BlockItem {
	/** Mast blocks under the siren. */
	public static final int MAST = 5;
	private static final ThreadLocal<Boolean> RAISED = ThreadLocal.withInitial(() -> false);

	public SirenItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.airdefense.siren").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
		RAISED.set(false);
		Level level = context.getLevel();
		BlockPos at = context.getClickedPos();
		boolean sneak = context.getPlayer() != null && context.getPlayer().isSecondaryUseActive();
		if (sneak || context.getClickedFace() != Direction.UP || level.getBlockState(at.below()).getBlock() instanceof SirenMastBlock) {
			return context;
		}
		for (int i = 0; i <= MAST + 1; i++) {
			BlockState s = level.getBlockState(at.above(i));
			if (!s.canBeReplaced() || !s.getFluidState().isEmpty() || level.isOutsideBuildHeight(at.above(i))) {
				return context;
			}
		}
		RAISED.set(true);
		return BlockPlaceContext.at(context, at.above(MAST), Direction.UP);
	}

	@Override
	protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
		if (!super.placeBlock(context, state)) {
			return false;
		}
		if (RAISED.get()) {
			RAISED.set(false);
			buildMast(context.getLevel(), context.getClickedPos(), state.getValue(SirenBlock.FACING));
		}
		return true;
	}

	/** The mast under a siren standing at {@code head}. */
	public static void buildMast(Level level, BlockPos head, Direction facing) {
		for (int i = 0; i < MAST; i++) {
			level.setBlock(head.below(MAST - i), SirenMastBlock.part(ModBlocks.SIREN_MAST, facing, i), Block.UPDATE_ALL);
		}
	}
}
