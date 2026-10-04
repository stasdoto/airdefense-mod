package com.stasdoto.airdefense.weapon;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.registry.ModComponents;

/**
 * A firearm. Left mouse button fires (handled by the client, checked by the server: see {@link GunServer}), holding
 * the right button aims down the sights (or the scope), R reloads from the ammunition in the inventory. The bar under
 * the hotbar icon shows the rounds left in the magazine.
 */
public class GunItem extends Item {
	public final GunType gun;

	public GunItem(GunType gun, Properties properties) {
		super(properties);
		this.gun = gun;
	}

	public static int ammo(ItemStack stack) {
		return stack.getOrDefault(ModComponents.AMMO, 0);
	}

	public static void setAmmo(ItemStack stack, int rounds) {
		stack.set(ModComponents.AMMO, Math.max(0, rounds));
	}

	/** A gun with a full magazine (what the creative tab hands out). */
	public static ItemStack loaded(Item item) {
		ItemStack stack = new ItemStack(item);
		if (item instanceof GunItem g) {
			setAmmo(stack, g.gun.magazine);
		}
		return stack;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || player.isPassenger()) {
			return InteractionResult.PASS;
		}
		// Aiming: held as long as the right button is down.
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity entity) {
		return false;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13f * Mth.clamp(ammo(stack), 0, gun.magazine) / gun.magazine);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		float k = (float) ammo(stack) / gun.magazine;
		return k <= 0 ? 0xFF4040 : k < 0.25f ? 0xFFA030 : 0xE8D070;
	}

	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.airdefense.gun.magazine", ammo(stack), gun.magazine,
				Component.translatable("item.airdefense." + gun.ammoId)).withStyle(ChatFormatting.GRAY));
		if (gun.rocket()) {
			tooltip.accept(Component.translatable("tooltip.airdefense.gun.rpg").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("tooltip.airdefense.gun.stats", (int) gun.damage, (int) gun.range,
					Component.translatable(gun.auto ? "tooltip.airdefense.gun.auto" : "tooltip.airdefense.gun.semi")).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("tooltip.airdefense.gun.keys").withStyle(ChatFormatting.DARK_GRAY));
	}
}
