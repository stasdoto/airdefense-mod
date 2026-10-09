package com.stasdoto.airdefense.gear;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 1.27: a body armour vest with webbing for six pouches. In the inventory: right-click the vest holding a pouch to put
 * it on, right-click it with an empty hand to take one off (or right-click a pouch while holding the vest).
 */
public class VestItem extends Item {
	/** How much of a bullet's damage to the chest gets through the plates. */
	public final float bulletFactor;

	public VestItem(Properties properties, float bulletFactor) {
		super(properties);
		this.bulletFactor = bulletFactor;
	}

	private static void sound(Player player) {
		player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 1.15f);
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack vest, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.SECONDARY || vest.getCount() != 1) {
			return false;
		}
		if (other.isEmpty()) {
			Pouch p = Pouches.detachLast(vest);
			if (p == Pouch.NONE) {
				return false;
			}
			carried.set(new ItemStack(p.item()));
			slot.setChanged();
			sound(player);
			return true;
		}
		Pouch p = Pouch.of(other.getItem());
		if (p == Pouch.NONE) {
			return false;
		}
		if (Pouches.attach(vest, p)) {
			other.shrink(1);
			slot.setChanged();
			sound(player);
		} else {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vest.full"));
		}
		return true;
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack vest, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.SECONDARY || vest.getCount() != 1) {
			return false;
		}
		ItemStack there = slot.getItem();
		if (there.isEmpty()) {
			Pouch p = Pouches.detachLast(vest);
			if (p == Pouch.NONE) {
				return false;
			}
			slot.safeInsert(new ItemStack(p.item()));
			sound(player);
			return true;
		}
		Pouch p = Pouch.of(there.getItem());
		if (p == Pouch.NONE) {
			return false;
		}
		if (Pouches.attach(vest, p)) {
			there.shrink(1);
			slot.setChanged();
			sound(player);
		} else {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vest.full"));
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		Map<Pouch, Integer> n = new EnumMap<>(Pouch.class);
		for (int i = 0; i < Pouches.SLOTS; i++) {
			Pouch p = Pouches.at(stack, i);
			if (p != Pouch.NONE) {
				n.merge(p, 1, Integer::sum);
			}
		}
		tooltip.accept(Component.translatable("tooltip.airdefense.vest.pouches", Pouches.total(stack), Pouches.SLOTS).withStyle(ChatFormatting.GRAY));
		for (Map.Entry<Pouch, Integer> e : n.entrySet()) {
			tooltip.accept(Component.literal("  ").append(Component.translatable(e.getKey().key()))
					.append(e.getValue() > 1 ? " ×" + e.getValue() : "").withStyle(ChatFormatting.DARK_GREEN));
		}
		tooltip.accept(Component.translatable("tooltip.airdefense.vest.how").withStyle(ChatFormatting.DARK_GRAY));
	}
}
