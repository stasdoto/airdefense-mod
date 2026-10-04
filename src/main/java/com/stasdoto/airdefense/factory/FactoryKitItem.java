package com.stasdoto.airdefense.factory;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

import com.stasdoto.airdefense.registry.ModBlocks;

/** Right-click the ground: a missile factory puts itself up there, gate towards you, 21 x 15 blocks. */
public class FactoryKitItem extends Item {
	public FactoryKitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		BlockPos origin = context.getClickedPos();
		Direction facing = player != null ? player.getDirection() : Direction.NORTH;
		if (origin.getY() + FactoryBlueprint.CHIMNEY_TOP + 2 > level.getMaxY() || origin.getY() - 4 < level.getMinY()) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.factory.no_room"));
			}
			return InteractionResult.FAIL;
		}
		BlockPos ctrl = FactoryBlueprint.controllerPos(origin, facing);
		level.setBlock(ctrl, ModBlocks.FACTORY_CONTROLLER.defaultBlockState().setValue(FactoryControllerBlock.FACING, facing.getOpposite()), Block.UPDATE_ALL);
		if (level.getBlockEntity(ctrl) instanceof FactoryBlockEntity factory) {
			factory.startConstruction(origin, facing, player != null && player.getAbilities().instabuild);
		}
		level.playSound(null, ctrl, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.factory.started"));
			if (!player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.airdefense.factory_kit.hint").withStyle(ChatFormatting.GRAY));
	}
}
