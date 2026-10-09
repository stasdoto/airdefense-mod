package com.stasdoto.airdefense.gear;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 1.27: a pouch to put on a vest (see {@link VestItem}); what it is good for is in its tooltip. */
public class PouchItem extends Item {
	public PouchItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		Pouch p = Pouch.of(stack.getItem());
		tooltip.accept(Component.translatable(p.key() + ".what").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.airdefense.pouch.how").withStyle(ChatFormatting.DARK_GRAY));
	}
}
