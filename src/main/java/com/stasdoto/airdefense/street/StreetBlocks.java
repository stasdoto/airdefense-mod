package com.stasdoto.airdefense.street;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.35: the street furniture's blocks and items (models in tools/street/make_street.py), and their own creative tab.
 * The towns put them up along their streets (see CityDecor); the player can take them from the tab.
 */
public final class StreetBlocks {
	public static final Map<String, Block> ALL = new LinkedHashMap<>();
	private static final List<Item> ITEMS = new ArrayList<>();

	public static final Block POLE_STEEL = pole("pole_steel", 2, MapColor.METAL);
	public static final Block POLE_BLACK = pole("pole_black", 2.4, MapColor.COLOR_BLACK);
	public static final Block POLE_GREEN = pole("pole_green", 3, MapColor.COLOR_GREEN);
	public static final Block POLE_CONCRETE = pole("pole_concrete", 4, MapColor.STONE);

	public static final Block LAMP_MODERN = thing("lamp_modern", 15, MapColor.METAL, SoundType.METAL, 6, 0, 6, 10, 7, 10);
	public static final Block LAMP_COBRA = thing("lamp_cobra", 15, MapColor.METAL, SoundType.METAL, 6, 0, 6, 10, 11, 10);
	public static final Block LAMP_LANTERN = thing("lamp_lantern", 15, MapColor.COLOR_BLACK, SoundType.LANTERN, 4.5, 0, 4.5, 11.5, 15, 11.5);
	public static final Block LAMP_GLOBE = thing("lamp_globe", 15, MapColor.SNOW, SoundType.GLASS, 4.5, 0, 4.5, 11.5, 12, 11.5);
	public static final Block TRAFFIC_LIGHT = thing("traffic_light", 4, MapColor.COLOR_BLACK, SoundType.METAL, 5, 0, 3, 13, 16, 11);

	public static final Block SIGN_STOP = sign("sign_stop");
	public static final Block SIGN_GIVE_WAY = sign("sign_give_way");
	public static final Block SIGN_CROSSING = sign("sign_crossing");
	public static final Block SIGN_NO_PARKING = sign("sign_no_parking");
	public static final Block SIGN_SPEED = sign("sign_speed");
	public static final Block SIGN_MAIN_ROAD = sign("sign_main_road");
	public static final Block SIGN_BUS = sign("sign_bus");

	public static final Block BENCH_PARK = thing("bench_park", 0, MapColor.WOOD, SoundType.WOOD, 0, 0, 4, 16, 8, 12.6);
	public static final Block BENCH_SOVIET = thing("bench_soviet", 0, MapColor.STONE, SoundType.STONE, 0, 0, 4, 16, 8, 12.6);
	public static final Block BENCH_MODERN = thing("bench_modern", 0, MapColor.WOOD, SoundType.WOOD, 0, 0, 4, 16, 8, 11.2);
	public static final Block BIN_SOVIET = thing("bin_soviet", 0, MapColor.STONE, SoundType.STONE, 3.5, 0, 3.5, 12.5, 10, 12.5);
	public static final Block BIN_MODERN = thing("bin_modern", 0, MapColor.COLOR_BLACK, SoundType.METAL, 4, 0, 4, 12, 12.6, 12);
	public static final Block BIN_EURO = thing("bin_euro", 0, MapColor.COLOR_GREEN, SoundType.METAL, 4.7, 0, 4.7, 11.3, 12.6, 10.2);

	public static final Block BUS_STOP_MODERN = thing("bus_stop_modern", 10, MapColor.METAL, SoundType.GLASS, 0, 0, 14, 16, 16, 16);
	public static final Block BUS_STOP_SOVIET = thing("bus_stop_soviet", 0, MapColor.STONE, SoundType.STONE, 0, 0, 13.9, 16, 16, 16);

	public static final Block HYDRANT = thing("hydrant", 0, MapColor.COLOR_RED, SoundType.METAL, 5, 0, 5, 11, 13, 11);
	public static final Block MAILBOX_US = thing("mailbox_us", 0, MapColor.COLOR_BLUE, SoundType.METAL, 3.5, 0, 3.5, 12.5, 16, 12.5);
	public static final Block MAILBOX_EURO = thing("mailbox_euro", 0, MapColor.COLOR_YELLOW, SoundType.METAL, 4, 0, 4.7, 12, 16, 11);
	public static final Block MAILBOX_SOVIET = thing("mailbox_soviet", 0, MapColor.COLOR_BLUE, SoundType.METAL, 3.6, 0, 4.6, 12.4, 16, 11);
	public static final Block BOOTH_RED = thing("booth_red", 8, MapColor.COLOR_RED, SoundType.METAL, 1, 0, 1, 15, 16, 15);
	public static final Block BOOTH_SOVIET = thing("booth_soviet", 8, MapColor.METAL, SoundType.METAL, 1, 0, 1, 15, 16, 15);
	public static final Block ADVERT_COLUMN = thing("advert_column", 0, MapColor.COLOR_GREEN, SoundType.STONE, 2.5, 0, 2.5, 13.5, 16, 13.5);
	public static final Block BOLLARD = thing("bollard", 0, MapColor.METAL, SoundType.METAL, 6, 0, 6, 10, 9, 10);
	public static final Block PLANTER = thing("planter", 0, MapColor.STONE, SoundType.STONE, 1, 0, 1, 15, 14, 15);
	public static final Block BIKE_RACK = thing("bike_rack", 0, MapColor.METAL, SoundType.METAL, 1, 0, 3, 14.5, 10, 13);
	public static final Block KIOSK = thing("kiosk", 8, MapColor.COLOR_BLUE, SoundType.WOOD, 0, 0, 0, 16, 16, 16);
	public static final Block VENDING = thing("vending", 8, MapColor.COLOR_RED, SoundType.METAL, 2, 0, 4, 14, 16, 14);
	public static final Block BILLBOARD = thing("billboard", 9, MapColor.METAL, SoundType.METAL, 0, 0, 6, 16, 16, 9);

	/** A manhole cover set in the asphalt: a whole block (it is the road surface). */
	public static final Block MANHOLE = register("manhole", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.8f, 6f)
			.sound(SoundType.STONE));

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, AirDefense.id("street"));
	public static final CreativeModeTab TAB = net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(TRAFFIC_LIGHT))
			.title(Component.translatable("itemGroup.airdefense.street"))
			.displayItems((params, output) -> {
				for (Item i : ITEMS) {
					output.accept(i);
				}
			})
			.build();

	private StreetBlocks() {
	}

	private static Block pole(String name, double width, MapColor color) {
		return register(name, p -> new StreetPoleBlock(p, width), BlockBehaviour.Properties.of().mapColor(color).strength(1.5f, 6f)
				.sound(name.contains("concrete") ? SoundType.STONE : SoundType.METAL).noOcclusion());
	}

	private static Block sign(String name) {
		return thing(name, 0, MapColor.METAL, SoundType.METAL, 1.5, 0, 7, 14.5, 16, 9);
	}

	private static Block thing(String name, int light, MapColor color, SoundType sound, double x0, double y0, double z0, double x1, double y1, double z1) {
		double[] box = {x0, y0, z0, x1, y1, z1};
		BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(color).strength(1.5f, 6f).sound(sound).noOcclusion();
		if (light > 0) {
			props = props.lightLevel(st -> light);
		}
		return register(name, p -> new StreetBlock(p, box), props);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		Identifier id = AirDefense.id(name);
		BlockItemId ids = BlockItemId.create(id, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, ids.block(), factory.apply(props.setId(ids.block())));
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
		ALL.put(name, block);
		ITEMS.add(item);
		return block;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
	}
}
