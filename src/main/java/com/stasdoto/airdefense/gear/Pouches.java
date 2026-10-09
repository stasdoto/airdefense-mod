package com.stasdoto.airdefense.gear;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.stasdoto.airdefense.registry.ModComponents;

/** 1.27: the pouches on a vest - six slots kept in one number (four bits each, see {@link ModComponents#POUCHES}). */
public final class Pouches {
	public static final int SLOTS = 6;

	private Pouches() {
	}

	public static boolean isVest(ItemStack stack) {
		return stack.getItem() instanceof VestItem;
	}

	public static int packed(ItemStack vest) {
		return isVest(vest) ? vest.getOrDefault(ModComponents.POUCHES, 0) : 0;
	}

	public static Pouch at(int packed, int slot) {
		return Pouch.of((packed >>> (slot * 4)) & 15);
	}

	public static Pouch at(ItemStack vest, int slot) {
		return at(packed(vest), slot);
	}

	public static int with(int packed, int slot, Pouch p) {
		return (packed & ~(15 << (slot * 4))) | (p.ordinal() << (slot * 4));
	}

	public static void set(ItemStack vest, int slot, Pouch p) {
		int v = with(packed(vest), slot, p);
		if (v == 0) {
			vest.remove(ModComponents.POUCHES);
		} else {
			vest.set(ModComponents.POUCHES, v);
		}
	}

	public static int count(ItemStack vest, Pouch kind) {
		int n = 0;
		int v = packed(vest);
		for (int i = 0; i < SLOTS; i++) {
			if (at(v, i) == kind) {
				n++;
			}
		}
		return n;
	}

	/** Pouches of this kind on the vest the entity wears. */
	public static int worn(LivingEntity e, Pouch kind) {
		return count(e.getItemBySlot(EquipmentSlot.CHEST), kind);
	}

	/** Puts the pouch in the first free slot it likes; false when the vest is full. */
	public static boolean attach(ItemStack vest, Pouch p) {
		if (p == Pouch.NONE || !isVest(vest)) {
			return false;
		}
		int v = packed(vest);
		for (int slot : p.order()) {
			if (at(v, slot) == Pouch.NONE) {
				set(vest, slot, p);
				return true;
			}
		}
		return false;
	}

	/** Takes off the pouch put on last (the highest slot taken); NONE if there are none. */
	public static Pouch detachLast(ItemStack vest) {
		int v = packed(vest);
		for (int slot = SLOTS - 1; slot >= 0; slot--) {
			Pouch p = at(v, slot);
			if (p != Pouch.NONE) {
				set(vest, slot, Pouch.NONE);
				return p;
			}
		}
		return Pouch.NONE;
	}

	public static int total(ItemStack vest) {
		int n = 0;
		int v = packed(vest);
		for (int i = 0; i < SLOTS; i++) {
			if (at(v, i) != Pouch.NONE) {
				n++;
			}
		}
		return n;
	}
}
