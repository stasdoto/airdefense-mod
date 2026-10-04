package com.stasdoto.airdefense.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.factory.FactoryKitItem;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.item.ManpadsItem;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.vehicle.VehicleItem;
import com.stasdoto.airdefense.vehicle.VehicleType;

public final class ModItems {
	public static final Item DESIGNATOR = register("designator", DesignatorItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item MANPADS = register("manpads", ManpadsItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item FACTORY_KIT = register("factory_kit", FactoryKitItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item FACTORY_CONTROLLER = register("factory_controller", p -> new BlockItem(ModBlocks.FACTORY_CONTROLLER, p),
			new Item.Properties().useBlockDescriptionPrefix());
	/** 35 mm rounds for the Gepard: one box = ten bursts. */
	public static final Item GEPARD_AMMO = register("gepard_ammo", Item::new, new Item.Properties().stacksTo(16));

	// Vehicles (the item ids are the ones the old one-block launchers had, so old inventories and recipes carry over).
	public static final Item ISKANDER = vehicle("iskander_launcher", VehicleType.ISKANDER);
	public static final Item KALIBR = vehicle("kalibr_launcher", VehicleType.KALIBR);
	public static final Item SHAHED = vehicle("shahed_launcher", VehicleType.SHAHED);
	public static final Item HIMARS = vehicle("himars", VehicleType.HIMARS);
	public static final Item PATRIOT = vehicle("patriot", VehicleType.PATRIOT);
	public static final Item IRIS_T = vehicle("iris_t", VehicleType.IRIS_T);
	public static final Item NASAMS = vehicle("nasams", VehicleType.NASAMS);
	public static final Item GEPARD = vehicle("gepard", VehicleType.GEPARD);

	// Missile items: what the flying entities look like (their 3D model), also usable as decoration in item frames.
	public static final Item ISKANDER_MISSILE = missile(MissileType.ISKANDER);
	public static final Item KALIBR_MISSILE = missile(MissileType.KALIBR);
	public static final Item SHAHED_DRONE = missile(MissileType.SHAHED);
	public static final Item GMLRS_ROCKET = missile(MissileType.GMLRS);
	public static final Item PAC3_MISSILE = missile(MissileType.PAC3);
	public static final Item IRIST_MISSILE = missile(MissileType.IRIST);
	public static final Item AMRAAM_MISSILE = missile(MissileType.AMRAAM);
	public static final Item STINGER_MISSILE = missile(MissileType.STINGER);

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, AirDefense.id("main"));
	public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(ISKANDER_MISSILE))
			.title(Component.translatable("itemGroup.airdefense"))
			.displayItems((params, output) -> {
				output.accept(DESIGNATOR);
				output.accept(ISKANDER);
				output.accept(KALIBR);
				output.accept(SHAHED);
				output.accept(HIMARS);
				output.accept(PATRIOT);
				output.accept(IRIS_T);
				output.accept(NASAMS);
				output.accept(GEPARD);
				output.accept(MANPADS);
				output.accept(FACTORY_KIT);
				output.accept(FACTORY_CONTROLLER);
				output.accept(GEPARD_AMMO);
				output.accept(ISKANDER_MISSILE);
				output.accept(KALIBR_MISSILE);
				output.accept(SHAHED_DRONE);
				output.accept(GMLRS_ROCKET);
				output.accept(PAC3_MISSILE);
				output.accept(IRIST_MISSILE);
				output.accept(AMRAAM_MISSILE);
				output.accept(STINGER_MISSILE);
			})
			.build();

	private ModItems() {
	}

	private static Item vehicle(String name, VehicleType type) {
		return register(name, p -> new VehicleItem(type, p), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	}

	private static Item missile(MissileType type) {
		return register(type.itemId, Item::new, new Item.Properties().stacksTo(16));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AirDefense.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
	}
}
