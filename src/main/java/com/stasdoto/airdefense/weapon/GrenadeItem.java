package com.stasdoto.airdefense.weapon;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.stasdoto.airdefense.registry.ModSounds;

/** F-1 hand grenade: right click pulls the pin and throws it. */
public class GrenadeItem extends Item {
	public GrenadeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			GrenadeEntity.throwFrom(server, player, player.isCrouching() ? 0.55f : 1.05f);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GRENADE_PIN, SoundSource.PLAYERS, 0.7f, 1.0f);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.THROW, SoundSource.PLAYERS, 0.6f, 1.0f);
		}
		player.getCooldowns().addCooldown(stack, 20);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.airdefense.grenade").withStyle(ChatFormatting.GRAY));
	}
}
