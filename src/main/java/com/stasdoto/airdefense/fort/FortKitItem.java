package com.stasdoto.airdefense.fort;

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

/** 1.48: a kit for a field work - right-click the ground and it is dug and built there, its front the way you look. */
public class FortKitItem extends Item {
	public final Fortify.Kind kind;

	public FortKitItem(Properties properties, Fortify.Kind kind) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		Direction facing = player != null ? player.getDirection() : Direction.NORTH;
		BlockPos ground = context.getClickedPos();
		if (!Fortify.build(level, ground, facing, kind)) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.fort.no_room"));
			}
			return InteractionResult.FAIL;
		}
		level.playSound(null, ground, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.fort.built", Component.translatable(getDescriptionId())));
			if (!player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.airdefense.fort_kit.hint").withStyle(ChatFormatting.GRAY));
	}
}
