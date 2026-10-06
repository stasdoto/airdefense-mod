package com.stasdoto.airdefense.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.factory.FactoryKitItem;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.item.ManpadsItem;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.vehicle.VehicleItem;
import com.stasdoto.airdefense.vehicle.VehicleType;
import com.stasdoto.airdefense.weapon.GrenadeItem;
import com.stasdoto.airdefense.weapon.GunItem;
import com.stasdoto.airdefense.weapon.GunType;
import com.stasdoto.airdefense.weapon.NvgItem;

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
	// Radar stations.
	public static final Item P18 = vehicle("p18", VehicleType.P18);
	public static final Item ST68 = vehicle("st68", VehicleType.ST68);
	public static final Item TRML4D = vehicle("trml4d", VehicleType.TRML4D);
	public static final Item SENTINEL = vehicle("sentinel", VehicleType.SENTINEL);
	public static final Item MPQ65 = vehicle("mpq65", VehicleType.MPQ65);
	public static final Item KUPOL = vehicle("kupol", VehicleType.KUPOL);

	// Missile items: what the flying entities look like (their 3D model), also usable as decoration in item frames.
	public static final Item ISKANDER_MISSILE = missile(MissileType.ISKANDER);
	public static final Item KALIBR_MISSILE = missile(MissileType.KALIBR);
	public static final Item SHAHED_DRONE = missile(MissileType.SHAHED);
	public static final Item GMLRS_ROCKET = missile(MissileType.GMLRS);
	public static final Item PAC3_MISSILE = missile(MissileType.PAC3);
	public static final Item IRIST_MISSILE = missile(MissileType.IRIST);
	public static final Item AMRAAM_MISSILE = missile(MissileType.AMRAAM);
	public static final Item STINGER_MISSILE = missile(MissileType.STINGER);

	// Small arms and gear (stage 7). One ammunition item = one round.
	public static final Item AMMO_545 = register("ammo_545", Item::new, new Item.Properties().stacksTo(90));
	public static final Item AMMO_762 = register("ammo_762", Item::new, new Item.Properties().stacksTo(90));
	public static final Item AMMO_9MM = register("ammo_9mm", Item::new, new Item.Properties().stacksTo(96));
	/** The RPG-7 rocket grenade: an item in the inventory, and the model of the rocket in flight. */
	public static final Item RPG_ROUND = register("rpg_round", Item::new, new Item.Properties().stacksTo(8));
	public static final Item AK74 = gun(GunType.AK74);
	public static final Item PKM = gun(GunType.PKM);
	public static final Item SVD = gun(GunType.SVD);
	public static final Item PM = gun(GunType.PM);
	public static final Item RPG7 = gun(GunType.RPG7);
	public static final Item F1_GRENADE = register("f1_grenade", GrenadeItem::new, new Item.Properties().stacksTo(16));
	public static final Item HELMET = register("helmet", Item::new, new Item.Properties().humanoidArmor(ModArmor.HELMET, ArmorType.HELMET));
	public static final Item NVG_HELMET = register("nvg_helmet", NvgItem::new,
			new Item.Properties().humanoidArmor(ModArmor.NVG, ArmorType.HELMET).rarity(Rarity.UNCOMMON));
	public static final Item VEST = register("vest", Item::new, new Item.Properties().humanoidArmor(ModArmor.VEST, ArmorType.CHESTPLATE));
	/** Field dressing: two seconds to apply, heals four hearts at once and more over the next ten seconds. */
	public static final Item MEDKIT = register("medkit", Item::new, new Item.Properties().stacksTo(8)
			.component(DataComponents.CONSUMABLE, Consumable.builder()
					.consumeSeconds(2.0f)
					.animation(ItemUseAnimation.BRUSH)
					.sound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.MEDKIT))
					.hasConsumeParticles(false)
					.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
							new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 1),
							new MobEffectInstance(MobEffects.REGENERATION, 200, 0))))
					.build()));

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, AirDefense.id("main"));
	public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(ISKANDER_MISSILE))
			.title(Component.translatable("itemGroup.airdefense"))
			.displayItems((params, output) -> {
				output.accept(com.stasdoto.airdefense.guide.Guide.book());
				output.accept(DESIGNATOR);
				output.accept(ISKANDER);
				output.accept(KALIBR);
				output.accept(SHAHED);
				output.accept(HIMARS);
				output.accept(PATRIOT);
				output.accept(IRIS_T);
				output.accept(NASAMS);
				output.accept(GEPARD);
				output.accept(P18);
				output.accept(ST68);
				output.accept(KUPOL);
				output.accept(TRML4D);
				output.accept(SENTINEL);
				output.accept(MPQ65);
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
				output.accept(GunItem.loaded(AK74));
				output.accept(GunItem.loaded(PKM));
				output.accept(GunItem.loaded(SVD));
				output.accept(GunItem.loaded(PM));
				output.accept(GunItem.loaded(RPG7));
				output.accept(AMMO_545);
				output.accept(AMMO_762);
				output.accept(AMMO_9MM);
				output.accept(RPG_ROUND);
				output.accept(F1_GRENADE);
				output.accept(HELMET);
				output.accept(NVG_HELMET);
				output.accept(VEST);
				output.accept(MEDKIT);
			})
			.build();

	private ModItems() {
	}

	private static Item vehicle(String name, VehicleType type) {
		return register(name, p -> new VehicleItem(type, p), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	}

	private static Item gun(GunType type) {
		// Aiming slows you down (the sniper rifle more), no sprinting with the sights up.
		return register(type.id, p -> new GunItem(type, p), new Item.Properties().stacksTo(1)
				.component(DataComponents.USE_EFFECTS, new UseEffects(false, false, type == GunType.SVD ? 0.35f : 0.6f)));
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
