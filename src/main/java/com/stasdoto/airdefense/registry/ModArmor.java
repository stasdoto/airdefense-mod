package com.stasdoto.airdefense.registry;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import com.stasdoto.airdefense.AirDefense;

/**
 * Soldier's gear: a steel helmet, the same helmet with night vision goggles, a body armour vest. The look comes from
 * assets/airdefense/equipment/*.json; on top of the usual armour points, bullets do less to a covered head or chest
 * (see {@link com.stasdoto.airdefense.weapon.GunServer}).
 */
public final class ModArmor {
	public static final ResourceKey<EquipmentAsset> HELMET_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("helmet"));
	public static final ResourceKey<EquipmentAsset> NVG_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("nvg_helmet"));
	public static final ResourceKey<EquipmentAsset> VEST_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("vest"));

	public static final ArmorMaterial HELMET = new ArmorMaterial(22, defense(3, 0), 9, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f,
			ItemTags.REPAIRS_IRON_ARMOR, HELMET_ASSET);
	public static final ArmorMaterial NVG = new ArmorMaterial(22, defense(3, 0), 9, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f,
			ItemTags.REPAIRS_IRON_ARMOR, NVG_ASSET);
	public static final ArmorMaterial VEST = new ArmorMaterial(26, defense(0, 7), 9, SoundEvents.ARMOR_EQUIP_LEATHER, 2.0f, 0.05f,
			ItemTags.REPAIRS_IRON_ARMOR, VEST_ASSET);

	private ModArmor() {
	}

	private static Map<ArmorType, Integer> defense(int helmet, int chest) {
		Map<ArmorType, Integer> m = new EnumMap<>(ArmorType.class);
		m.put(ArmorType.HELMET, helmet);
		m.put(ArmorType.CHESTPLATE, chest);
		m.put(ArmorType.LEGGINGS, 0);
		m.put(ArmorType.BOOTS, 0);
		m.put(ArmorType.BODY, 0);
		return m;
	}
}
