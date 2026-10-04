package com.stasdoto.airdefense.registry;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

import com.stasdoto.airdefense.AirDefense;

/** Item data of the mod: rounds left in a gun's magazine, whether night vision goggles are switched on. */
public final class ModComponents {
	/** Rounds in the magazine (absent = empty). Changing it must not replay the "take the item out" animation. */
	public static final DataComponentType<Integer> AMMO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, AirDefense.id("ammo"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT)
					.ignoreSwapAnimation().build());
	public static final DataComponentType<Boolean> NVG_ON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, AirDefense.id("nvg_on"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)
					.ignoreSwapAnimation().build());

	private ModComponents() {
	}

	public static void init() {
	}
}
