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
	// Air defence (stage R4).
	public static final Item PANTSIR = vehicle("pantsir", VehicleType.PANTSIR);
	public static final Item TOR = vehicle("tor", VehicleType.TOR);
	public static final Item BUK = vehicle("buk", VehicleType.BUK);
	public static final Item S300 = vehicle("s300", VehicleType.S300);
	public static final Item OSA = vehicle("osa", VehicleType.OSA);
	public static final Item STRELA10 = vehicle("strela10", VehicleType.STRELA10);
	public static final Item SHILKA = vehicle("shilka", VehicleType.SHILKA);
	public static final Item TUNGUSKA = vehicle("tunguska", VehicleType.TUNGUSKA);
	public static final Item SAMPT = vehicle("sampt", VehicleType.SAMPT);
	public static final Item AVENGER = vehicle("avenger", VehicleType.AVENGER);
	public static final Item MFG = vehicle("mfg", VehicleType.MFG);
	public static final Item ZU23 = vehicle("zu23", VehicleType.ZU23);
	// Armour and boats (stage R5).
	public static final Item T72 = vehicle("t72", VehicleType.T72);
	public static final Item T90 = vehicle("t90", VehicleType.T90);
	public static final Item LEOPARD2 = vehicle("leopard2", VehicleType.LEOPARD2);
	public static final Item ABRAMS = vehicle("abrams", VehicleType.ABRAMS);
	public static final Item BMP2 = vehicle("bmp2", VehicleType.BMP2);
	public static final Item BRADLEY = vehicle("bradley", VehicleType.BRADLEY);
	public static final Item BTR82 = vehicle("btr82", VehicleType.BTR82);
	public static final Item BTR4 = vehicle("btr4", VehicleType.BTR4);
	public static final Item M113 = vehicle("m113", VehicleType.M113);
	public static final Item MAXXPRO = vehicle("maxxpro", VehicleType.MAXXPRO);
	public static final Item KOZAK = vehicle("kozak", VehicleType.KOZAK);
	public static final Item GYURZA = vehicle("gyurza", VehicleType.GYURZA);
	public static final Item RAPTOR = vehicle("raptor", VehicleType.RAPTOR);
	public static final Item RHIB = vehicle("rhib", VehicleType.RHIB);
	// Aircraft (stage R6).
	public static final Item MI8 = vehicle("mi8", VehicleType.MI8);
	public static final Item MI24 = vehicle("mi24", VehicleType.MI24);
	public static final Item KA52 = vehicle("ka52", VehicleType.KA52);
	public static final Item SU25 = vehicle("su25", VehicleType.SU25);
	public static final Item F16 = vehicle("f16", VehicleType.F16);
	public static final Item S8_ROCKETS = register("s8_rockets", Item::new, new Item.Properties().stacksTo(8));
	public static final Item FAB250 = register("fab250", Item::new, new Item.Properties().stacksTo(4));
	public static final Item TANK_SHELL = register("tank_shell", Item::new, new Item.Properties().stacksTo(8));
	public static final Item AMMO_30_BOX = register("ammo_30_box", Item::new, new Item.Properties().stacksTo(16));
	/** Petrol: 20 litres a can (right-click a vehicle to pour it in). */
	public static final Item JERRYCAN = register("jerrycan", Item::new, new Item.Properties().stacksTo(16));
	public static final Item EMPTY_JERRYCAN = register("empty_jerrycan", Item::new, new Item.Properties().stacksTo(16));
	public static final Item SAM_SHORT = register("sam_short", Item::new, new Item.Properties().stacksTo(8));
	public static final Item SAM_LONG = register("sam_long", Item::new, new Item.Properties().stacksTo(4));
	public static final Item AMMO_23_BOX = register("ammo_23_box", Item::new, new Item.Properties().stacksTo(16));
	public static final Item AMMO_127_BOX = register("ammo_127_box", Item::new, new Item.Properties().stacksTo(16));

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
				output.accept(PANTSIR);
				output.accept(TOR);
				output.accept(BUK);
				output.accept(S300);
				output.accept(OSA);
				output.accept(STRELA10);
				output.accept(SHILKA);
				output.accept(TUNGUSKA);
				output.accept(SAMPT);
				output.accept(AVENGER);
				output.accept(MFG);
				output.accept(ZU23);
				output.accept(T72);
				output.accept(T90);
				output.accept(LEOPARD2);
				output.accept(ABRAMS);
				output.accept(BMP2);
				output.accept(BRADLEY);
				output.accept(BTR82);
				output.accept(BTR4);
				output.accept(M113);
				output.accept(MAXXPRO);
				output.accept(KOZAK);
				output.accept(GYURZA);
				output.accept(RAPTOR);
				output.accept(RHIB);
				output.accept(MI8);
				output.accept(MI24);
				output.accept(KA52);
				output.accept(SU25);
				output.accept(F16);
				output.accept(S8_ROCKETS);
				output.accept(FAB250);
				output.accept(TANK_SHELL);
				output.accept(AMMO_30_BOX);
				output.accept(JERRYCAN);
				output.accept(EMPTY_JERRYCAN);
				output.accept(SAM_SHORT);
				output.accept(SAM_LONG);
				output.accept(AMMO_23_BOX);
				output.accept(AMMO_127_BOX);
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
