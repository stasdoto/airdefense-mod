package com.stasdoto.airdefense.street;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.49 "A town with a past": the weathered materials the towns are built of in new worlds (drawn in
 * tools/city/make_weathered.py). Every wall material - concrete panels in each paint, plaster in each colour (the
 * designs' terracotta), red and silicate brick, sandstone - comes in two plain looks and three that face the street:
 * dirt splashed up from the pavement, streaks down from a sill, a crack with the plaster peeled. And the street: asphalt
 * with cracks, patches and potholes, worn paint, kerb stones, paving of several kinds, the tar of flat roofs. The
 * blocks have no items: broken, they give back the plain block they stand for.
 */
public final class Weathered {
	public enum Look {
		A, B, GRIME, STREAK, CRACK;

		boolean facing() {
			return ordinal() >= 2;
		}
	}

	/** A wall material: the vanilla block it stands for and its five looks. */
	public record Material(String name, Block vanilla, Block[] looks) {
		public BlockState state(Look look, Direction out) {
			BlockState s = looks[look.ordinal()].defaultBlockState();
			return look.facing() ? s.setValue(WeatheredFacingBlock.FACING, out) : s;
		}
	}

	private static final Map<Block, Material> BY_VANILLA = new IdentityHashMap<>();
	private static final Set<Block> ALL_WALLS = new HashSet<>();
	public static final Map<String, Block> GROUND = new LinkedHashMap<>();
	public static final List<Material> MATERIALS = new ArrayList<>();

	public static final Block ASPHALT_A = ground("asphalt_a", Blocks.CONCRETE.pick(DyeColor.GRAY));
	public static final Block ASPHALT_B = ground("asphalt_b", Blocks.CONCRETE.pick(DyeColor.GRAY));
	public static final Block ASPHALT_CRACK = ground("asphalt_crack", Blocks.CONCRETE.pick(DyeColor.GRAY));
	public static final Block ASPHALT_PATCH = ground("asphalt_patch", Blocks.CONCRETE.pick(DyeColor.GRAY));
	public static final Block ASPHALT_POTHOLE = ground("asphalt_pothole", Blocks.CONCRETE.pick(DyeColor.GRAY));
	public static final Block ASPHALT_PUDDLE = ground("asphalt_puddle", Blocks.CONCRETE.pick(DyeColor.GRAY));
	public static final Block ROAD_MARK = ground("road_mark", Blocks.CONCRETE.pick(DyeColor.WHITE));
	public static final Block ROAD_MARK_WORN = ground("road_mark_worn", Blocks.CONCRETE.pick(DyeColor.WHITE));
	public static final Block KERB = ground("kerb", Blocks.SMOOTH_STONE);
	public static final Block PAVING_GREY = ground("paving_grey", Blocks.POLISHED_ANDESITE);
	public static final Block PAVING_BROKEN = ground("paving_broken", Blocks.POLISHED_ANDESITE);
	public static final Block PAVING_WEEDS = ground("paving_weeds", Blocks.POLISHED_ANDESITE);
	public static final Block PAVING_BRICK = ground("paving_brick", Blocks.POLISHED_ANDESITE);
	public static final Block WALK_CONCRETE = ground("walk_concrete", Blocks.POLISHED_ANDESITE);
	public static final Block WALK_ASPHALT = ground("walk_asphalt", Blocks.POLISHED_ANDESITE);
	public static final Block WALK_ASPHALT_CRACK = ground("walk_asphalt_crack", Blocks.POLISHED_ANDESITE);
	public static final Block PAVING_SAND = ground("paving_sand", Blocks.POLISHED_ANDESITE);
	public static final Block ROOF_TAR = ground("roof_tar", Blocks.CONCRETE.pick(DyeColor.GRAY));

	/** What the pedestrians walk on and cross (they knew only the plain blocks before). */
	public static final Set<Block> WALKWAY = Set.of(KERB, PAVING_GREY, PAVING_BROKEN, PAVING_WEEDS, PAVING_BRICK, WALK_CONCRETE, WALK_ASPHALT,
			WALK_ASPHALT_CRACK, PAVING_SAND);
	public static final Set<Block> ROADWAY = Set.of(ASPHALT_A, ASPHALT_B, ASPHALT_CRACK, ASPHALT_PATCH, ASPHALT_POTHOLE, ASPHALT_PUDDLE, ROAD_MARK,
			ROAD_MARK_WORN);

	static {
		for (DyeColor d : DyeColor.values()) {
			material("panel_" + d.getName(), Blocks.CONCRETE.pick(d));
		}
		for (DyeColor d : DyeColor.values()) {
			material("plaster_" + d.getName(), Blocks.DYED_TERRACOTTA.pick(d));
		}
		material("plaster_plain", Blocks.TERRACOTTA);
		material("brick", Blocks.BRICKS);
		material("silicate", Blocks.SMOOTH_QUARTZ);
		material("sandstone", Blocks.SMOOTH_SANDSTONE);
	}

	private Weathered() {
	}

	private static Block ground(String name, Block like) {
		Block b = block("g_" + name, BlockBehaviour.Properties.ofFullCopy(like), false);
		GROUND.put(name, b);
		return b;
	}

	private static void material(String name, Block vanilla) {
		Block[] looks = new Block[Look.values().length];
		for (Look l : Look.values()) {
			looks[l.ordinal()] = block("w_" + name + "_" + l.name().toLowerCase(java.util.Locale.ROOT), BlockBehaviour.Properties.ofFullCopy(vanilla), l.facing());
			ALL_WALLS.add(looks[l.ordinal()]);
		}
		Material m = new Material(name, vanilla, looks);
		MATERIALS.add(m);
		BY_VANILLA.put(vanilla, m);
	}

	private static Block block(String name, BlockBehaviour.Properties props, boolean facing) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, AirDefense.id(name));
		props.setId(key);
		return Registry.register(BuiltInRegistries.BLOCK, key, facing ? new WeatheredFacingBlock(props) : new Block(props));
	}

	/** The weathered material standing for this vanilla block, if there is one. */
	public static Material of(Block vanilla) {
		return BY_VANILLA.get(vanilla);
	}

	public static boolean isWall(Block b) {
		return ALL_WALLS.contains(b);
	}

	public static boolean isRoad(BlockState s) {
		return ROADWAY.contains(s.getBlock());
	}

	public static boolean isWalk(BlockState s) {
		return WALKWAY.contains(s.getBlock());
	}

	/** Loads the class (registers the blocks) during the mod's start. */
	public static void init() {
		AirDefense.LOGGER.info("[airdefense] weathered materials: {} walls, {} ground", MATERIALS.size(), GROUND.size());
	}
}
