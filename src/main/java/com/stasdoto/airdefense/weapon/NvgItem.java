package com.stasdoto.airdefense.weapon;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.stasdoto.airdefense.registry.ModComponents;
import com.stasdoto.airdefense.registry.ModSounds;

/** A helmet with night vision goggles: N flips them down (night vision, green picture) or up. */
public class NvgItem extends Item {
	public NvgItem(Properties properties) {
		super(properties);
	}

	public static boolean isOn(ItemStack stack) {
		return stack.getItem() instanceof NvgItem && stack.getOrDefault(ModComponents.NVG_ON, false);
	}

	/** The N key: works only when the goggles are worn. */
	public static void toggle(ServerPlayer player) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!(head.getItem() instanceof NvgItem)) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.nvg.none"));
			return;
		}
		boolean on = !isOn(head);
		head.set(ModComponents.NVG_ON, on);
		if (!on) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.NVG_SWITCH, SoundSource.PLAYERS,
				0.6f, on ? 1.1f : 0.8f);
		player.sendOverlayMessage(Component.translatable(on ? "message.airdefense.nvg.on" : "message.airdefense.nvg.off"));
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (slot != EquipmentSlot.HEAD || !(entity instanceof LivingEntity living) || !isOn(stack)) {
			return;
		}
		MobEffectInstance current = living.getEffect(MobEffects.NIGHT_VISION);
		// Keep it above 10 s, where the picture would start to flicker.
		if (current == null || current.getDuration() < 240) {
			living.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, false));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.airdefense.nvg").withStyle(ChatFormatting.GRAY));
	}
}
