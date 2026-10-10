package com.stasdoto.airdefense.fort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.street.StreetBlock;

/**
 * 1.48 "Positions": the field works - sandbags (a full wall and a low parapet), Czech hedgehogs against tanks, barbed
 * wire, camouflage nets; and kits that dig and build a whole work at once: a trench, a firing position, a dugout, a
 * pillbox, a vehicle revetment. Their own tab in the creative inventory.
 */
public final class FortBlocks {
	private static final List<Item> ITEMS = new ArrayList<>();

	public static final Block SANDBAGS = register("sandbags", p -> new StreetBlock(p, new double[]{0, 0, 0, 16, 16, 16}),
			BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.2f, 10f).sound(SoundType.SAND));
	public static final Block SANDBAGS_LOW = register("sandbags_low", p -> new StreetBlock(p, new double[]{0, 0, 0, 16, 8, 16}),
			BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.0f, 10f).sound(SoundType.SAND).noOcclusion());
	public static final Block HEDGEHOG = register("hedgehog", p -> new StreetBlock(p, new double[]{1, 0, 1, 15, 15, 15}),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6f, 1200f).sound(SoundType.METAL).noOcclusion());
	public static final Block BARBED_WIRE = register("barbed_wire", BarbedWireBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 4f).sound(SoundType.CHAIN).noOcclusion().noCollision());
	public static final Block CAMO_NET = register("camo_net", CamoNetBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.3f).sound(SoundType.WOOL).noOcclusion().noCollision());

	public static final Item TRENCH_KIT = kit("trench_kit", Fortify.Kind.TRENCH);
	public static final Item POSITION_KIT = kit("position_kit", Fortify.Kind.POSITION);
	public static final Item DUGOUT_KIT = kit("dugout_kit", Fortify.Kind.DUGOUT);
	public static final Item PILLBOX_KIT = kit("pillbox_kit", Fortify.Kind.PILLBOX);
	public static final Item REVETMENT_KIT = kit("revetment_kit", Fortify.Kind.REVETMENT);

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, AirDefense.id("fort"));
	public static final CreativeModeTab TAB = net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(SANDBAGS))
			.title(Component.translatable("itemGroup.airdefense.fort"))
			.displayItems((params, output) -> {
				for (Item i : ITEMS) {
					output.accept(i);
				}
			})
			.build();

	private FortBlocks() {
	}

	private static Item kit(String name, Fortify.Kind kind) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AirDefense.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new FortKitItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON).setId(key), kind));
		ITEMS.add(item);
		return item;
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		Identifier id = AirDefense.id(name);
		BlockItemId ids = BlockItemId.create(id, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, ids.block(), factory.apply(props.setId(ids.block())));
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
		ITEMS.add(item);
		return block;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
		Fortify.init();
	}
}
