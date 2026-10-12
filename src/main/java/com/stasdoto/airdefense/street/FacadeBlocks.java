package com.stasdoto.airdefense.street;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.49 "A town with a past": what people and years hang on a town's walls and put on its roofs and in its yards (models
 * in tools/city/make_facade.py): air conditioners, satellite dishes, drainpipes, shop signs, graffiti, notices, flower
 * boxes, washing and junk on the balconies, roof vents and aerials, rubbish containers. Their own creative tab.
 */
public final class FacadeBlocks {
	private static final List<Item> ITEMS = new ArrayList<>();

	public static final Block AC_UNIT = thing("ac_unit", MapColor.SNOW, SoundType.METAL, false, 2, 2, 9, 14, 13, 16);
	public static final Block SAT_DISH = thing("sat_dish", MapColor.SNOW, SoundType.METAL, false, 2, 2, 2, 14, 14, 16);
	public static final Block DRAINPIPE = thing("drainpipe", MapColor.METAL, SoundType.METAL, false, 6.5, 0, 12, 9.5, 16, 15);
	public static final Block DRAINPIPE_TOP = thing("drainpipe_top", MapColor.METAL, SoundType.METAL, false, 4, 0, 9, 12, 16, 16);
	public static final Block DRAINPIPE_BOTTOM = thing("drainpipe_bottom", MapColor.METAL, SoundType.METAL, false, 6.5, 0, 8, 9.5, 16, 15);
	public static final Block FLOWER_BOX = thing("flower_box", MapColor.WOOD, SoundType.WOOD, false, 1, 9, 11, 15, 16, 16);
	public static final Block ROOF_VENT = thing("roof_vent", MapColor.METAL, SoundType.METAL, false, 4, 0, 4, 12, 11, 12);
	public static final Block TV_AERIAL = thing("tv_aerial", MapColor.METAL, SoundType.METAL, true, 1, 0, 1, 15, 16, 15);
	public static final Block TROLLEY_WIRE = thing("trolley_wire", MapColor.METAL, SoundType.CHAIN, true, 5, 12, 0, 11, 14, 16);
	public static final Block TROLLEY_SPAN = thing("trolley_span", MapColor.METAL, SoundType.CHAIN, true, 0, 13, 7, 16, 15, 9);
	public static final Block STATUE = thing("statue", MapColor.COLOR_GREEN, SoundType.METAL, false, 4, 0, 4, 12, 16, 12);
	public static final Block DUMPSTER = thing("dumpster", MapColor.COLOR_GREEN, SoundType.METAL, false, 1, 0, 1, 15, 14, 15);

	public static final Block SHOP_SIGN = kind("shop_sign", MapColor.METAL, SoundType.METAL, true, 0, 2, 13.5, 16, 14, 16);
	public static final Block GRAFFITI = kind("graffiti", MapColor.NONE, SoundType.WOOL, true, 0, 0, 15.5, 16, 16, 16);
	public static final Block NOTICE = kind("notice", MapColor.NONE, SoundType.WOOL, true, 0, 0, 15.5, 16, 16, 16);
	public static final Block LAUNDRY = kind("laundry", MapColor.WOOL, SoundType.WOOL, true, 0, 0, 7.5, 16, 16, 8.5);
	public static final Block PRODUCE_CRATE = kind("produce_crate", MapColor.WOOD, SoundType.WOOD, false, 1, 0, 1, 15, 8, 15);
	public static final Block BALCONY_JUNK = kind("balcony_junk", MapColor.WOOD, SoundType.WOOD, false, 1, 0, 3, 15, 10, 13);

	/** How many looks each comes in (the blockstates repeat them up to 12). */
	public static final int SIGNS = 12;
	public static final int SIGNS_RU = 6;
	public static final int GRAFFITI_KINDS = 6;
	public static final int NOTICE_KINDS = 3;
	public static final int LAUNDRY_KINDS = 3;
	public static final int JUNK_KINDS = 3;

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, AirDefense.id("city"));
	public static final CreativeModeTab TAB = net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(AC_UNIT))
			.title(Component.translatable("itemGroup.airdefense.city"))
			.displayItems((params, output) -> {
				for (Item i : ITEMS) {
					output.accept(i);
				}
			})
			.build();

	private FacadeBlocks() {
	}

	public static BlockState facing(Block b, Direction out) {
		return b.defaultBlockState().setValue(StreetBlock.FACING, out);
	}

	public static BlockState kind(Block b, int kind, Direction out) {
		return facing(b, out).setValue(KindBlock.KIND, kind);
	}

	private static Block thing(String name, MapColor color, SoundType sound, boolean noCollision, double x0, double y0, double z0, double x1, double y1,
			double z1) {
		double[] box = {x0, y0, z0, x1, y1, z1};
		return register(name, p -> new StreetBlock(p, box), props(color, sound, noCollision));
	}

	private static Block kind(String name, MapColor color, SoundType sound, boolean noCollision, double x0, double y0, double z0, double x1, double y1,
			double z1) {
		double[] box = {x0, y0, z0, x1, y1, z1};
		return register(name, p -> new KindBlock(p, box), props(color, sound, noCollision));
	}

	private static BlockBehaviour.Properties props(MapColor color, SoundType sound, boolean noCollision) {
		BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color).strength(1.0f, 4f).sound(sound).noOcclusion();
		return noCollision ? p.noCollision() : p;
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
	}
}
