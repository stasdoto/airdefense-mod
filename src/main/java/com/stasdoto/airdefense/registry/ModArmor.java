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
 * Soldier's gear: the 6B47 helmet, the FAST helmet (also with night vision goggles), a plate carrier and the heavy 6B45
 * vest. 1.27: they are drawn in 3D by the client (client/gear/GearLayer), so their equipment assets have no flat layers;
 * on top of the usual armour points, bullets do less to a covered head or chest (see {@link com.stasdoto.airdefense.weapon.GunServer}).
 */
public final class ModArmor {
	public static final ResourceKey<EquipmentAsset> HELMET_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("helmet"));
	public static final ResourceKey<EquipmentAsset> NVG_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("nvg_helmet"));
	public static final ResourceKey<EquipmentAsset> VEST_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("vest"));
	public static final ResourceKey<EquipmentAsset> FAST_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("helmet_fast"));
	public static final ResourceKey<EquipmentAsset> VEST_HEAVY_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, AirDefense.id("vest_heavy"));

	public static final ArmorMaterial HELMET = new ArmorMaterial(22, defense(3, 0), 9, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f,
			ItemTags.REPAIRS_IRON_ARMOR, HELMET_ASSET);
	public static final ArmorMaterial NVG = new ArmorMaterial(22, defense(3, 0), 9, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f,
			ItemTags.REPAIRS_IRON_ARMOR, NVG_ASSET);
	public static final ArmorMaterial VEST = new ArmorMaterial(26, defense(0, 7), 9, SoundEvents.ARMOR_EQUIP_LEATHER, 2.0f, 0.05f,
			ItemTags.REPAIRS_IRON_ARMOR, VEST_ASSET);

	/** 1.27: the high-cut FAST, a little lighter and tougher than the steel-and-aramid 6B47. */
	public static final ArmorMaterial FAST = new ArmorMaterial(24, defense(3, 0), 9, SoundEvents.ARMOR_EQUIP_TURTLE, 1.5f, 0.0f,
			ItemTags.REPAIRS_IRON_ARMOR, FAST_ASSET);
	/** 1.27: the heavy 6B45 with bigger plates, a collar and a groin flap. */
	public static final ArmorMaterial VEST_HEAVY = new ArmorMaterial(34, defense(0, 9), 9, SoundEvents.ARMOR_EQUIP_IRON, 3.0f, 0.1f,
			ItemTags.REPAIRS_IRON_ARMOR, VEST_HEAVY_ASSET);

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
