package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What a hamlet has besides its buildings: the well and the bell on the square, lamp posts, a bench, a market stall;
 * fenced fields with ripe crops and a scarecrow, a paddock with cows, sheep and chickens; flowers by the houses; a
 * villager at every door.
 */
final class HamletDecor {
	private final Hamlets.Hamlet h;
	private final Random r;
	private final CityDecor.Result out = new CityDecor.Result();

	private HamletDecor(Hamlets.Hamlet h) {
		this.h = h;
		this.r = new Random(h.seed() ^ 0xFA4DL);
	}

	static CityDecor.Result build(Hamlets.Hamlet h) {
		HamletDecor d = new HamletDecor(h);
		d.square();
		d.fields();
		d.houses();
		return d.out;
	}

	private void put(int x, int y, int z, BlockState s) {
		add(x, y, z, s, false);
	}

	private void force(int x, int y, int z, BlockState s) {
		add(x, y, z, s, true);
	}

	private void add(int x, int y, int z, BlockState s, boolean force) {
		out.blocks.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new ArrayList<>()).add(new CityDecor.D(x, y, z, s, force));
	}

	private void spawn(int x, int y, int z, int kind) {
		out.spawns.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new ArrayList<>()).add(new CityDecor.Spawn(x, y, z, kind));
	}

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState facing(Block block, Direction d) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
	}

	// ------------------------------------------------------------------------------------------------

	private void square() {
		int x = h.x;
		int z = h.z;
		int y = h.base;
		// The well: water in a ring of stone, four posts, a little roof, a bucket on a chain.
		force(x, y - 1, z, b(Blocks.COBBLESTONE));
		force(x, y - 2, z, b(Blocks.COBBLESTONE));
		force(x, y, z, b(Blocks.WATER));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					force(x + dx, y, z + dz, b(Blocks.COBBLESTONE));
					put(x + dx, y + 1, z + dz, (dx == 0 || dz == 0) ? b(Blocks.COBBLESTONE_WALL) : b(Blocks.SPRUCE_FENCE));
				}
			}
		}
		for (int dx : new int[]{-1, 1}) {
			for (int dz : new int[]{-1, 1}) {
				put(x + dx, y + 2, z + dz, b(Blocks.SPRUCE_FENCE));
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(x + dx, y + 3, z + dz, dx == 0 && dz == 0 ? b(Blocks.SPRUCE_PLANKS)
						: Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
		}
		put(x, y + 2, z, b(Blocks.IRON_CHAIN));
		// The bell on a stone base.
		BlockPos bell = h.bell();
		force(bell.getX(), y, bell.getZ(), b(Blocks.POLISHED_ANDESITE));
		put(bell.getX(), y + 1, bell.getZ(), b(Blocks.BELL));
		// Lamp posts on two corners.
		for (int[] c : new int[][]{{-5, -5}, {5, 5}}) {
			put(x + c[0], y + 1, z + c[1], b(Blocks.SPRUCE_FENCE));
			put(x + c[0], y + 2, z + c[1], b(Blocks.SPRUCE_FENCE));
			put(x + c[0], y + 3, z + c[1], b(Blocks.LANTERN));
		}
		// A bench and a market stall with a striped awning: a crate of vegetables, a barrel, a melon.
		Direction benchBack = Direction.NORTH;
		put(x - 1, y + 1, z + 5, facing(Blocks.SPRUCE_STAIRS, benchBack.getOpposite()));
		put(x, y + 1, z + 5, facing(Blocks.SPRUCE_STAIRS, benchBack.getOpposite()));
		int mx = x - 5;
		int mz = z - 1;
		for (int dz : new int[]{0, 3}) {
			put(mx, y + 1, mz + dz, b(Blocks.SPRUCE_FENCE));
			put(mx, y + 2, mz + dz, b(Blocks.SPRUCE_FENCE));
		}
		for (int dz = -1; dz <= 4; dz++) {
			for (int dx = -1; dx <= 0; dx++) {
				put(mx + dx, y + 3, mz + dz, Blocks.WOOL_SLAB.pick(dz % 2 == 0 ? DyeColor.RED : DyeColor.WHITE).defaultBlockState()
						.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
		}
		put(mx + 1, y + 1, mz + 1, b(Blocks.BARREL));
		put(mx + 1, y + 1, mz + 2, b(Blocks.COMPOSTER));
		put(mx + 1, y + 2, mz + 1, b(Blocks.MELON));
		put(mx + 1, y + 2, mz + 2, b(Blocks.PUMPKIN));
		put(mx - 1, y + 1, mz + 1, b(Blocks.HAY_BLOCK));
		// The town dog... a cat, and a couple of people on the square.
		spawn(x + 3, y + 1, z + 3, 0);
		spawn(x - 3, y + 1, z - 3, 1);
	}

	private void fields() {
		boolean scarecrow = false;
		for (Hamlets.Field f : h.fields) {
			Hamlets.Pad p = f.pad();
			int y = p.y();
			// The fence round it, a gate towards the hamlet.
			int gx = (p.x0() + p.x1()) / 2;
			int gz = (p.z0() + p.z1()) / 2;
			for (int x = p.x0() - 1; x <= p.x1() + 1; x++) {
				for (int z = p.z0() - 1; z <= p.z1() + 1; z++) {
					boolean edge = x == p.x0() - 1 || x == p.x1() + 1 || z == p.z0() - 1 || z == p.z1() + 1;
					if (!edge) {
						continue;
					}
					boolean gate = switch (f.gate()) {
						case NORTH -> z == p.z0() - 1 && x == gx;
						case SOUTH -> z == p.z1() + 1 && x == gx;
						case WEST -> x == p.x0() - 1 && z == gz;
						default -> x == p.x1() + 1 && z == gz;
					};
					put(x, y + 1, z, gate ? facing(Blocks.SPRUCE_FENCE_GATE, f.gate()) : b(Blocks.OAK_FENCE));
				}
			}
			if (f.crop() == Hamlets.PADDOCK) {
				put(p.x0() + 1, y + 1, p.z0() + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
				put(p.x0() + 2, y + 1, p.z0() + 1, b(Blocks.HAY_BLOCK));
				put(p.x1() - 1, y + 1, p.z1() - 1, b(Blocks.HAY_BLOCK));
				put(p.x1() - 1, y + 2, p.z1() - 1, b(Blocks.HAY_BLOCK));
				int[] kinds = {2, 2, 3, 3, 3, 4, 4, 4, 5};
				for (int k = 0; k < kinds.length; k++) {
					int sx = p.x0() + 2 + r.nextInt(Math.max(1, p.x1() - p.x0() - 3));
					int sz = p.z0() + 2 + r.nextInt(Math.max(1, p.z1() - p.z0() - 3));
					spawn(sx, y + 1, sz, kinds[k]);
				}
				continue;
			}
			// Ripe crops (nearly all), the channel left as water.
			int sx = -1;
			int sz = -1;
			if (!scarecrow) {
				scarecrow = true;
				sx = p.x0() + 1;
				sz = p.z0() + 1;
			}
			for (int x = p.x0(); x <= p.x1(); x++) {
				for (int z = p.z0(); z <= p.z1(); z++) {
					boolean channel = f.alongX() ? z == (p.z0() + p.z1()) / 2 : x == (p.x0() + p.x1()) / 2;
					if (channel) {
						if ((x + z) % 7 == 0) {
							put(x, y + 1, z, b(Blocks.LILY_PAD));
						}
						continue;
					}
					if (x == sx && z == sz) {
						continue;
					}
					int age = r.nextInt(10) == 0 ? 4 + r.nextInt(3) : 7;
					BlockState crop = switch (f.crop()) {
						case Hamlets.CARROTS -> Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7, age);
						case Hamlets.POTATOES -> Blocks.POTATOES.defaultBlockState().setValue(BlockStateProperties.AGE_7, age);
						case Hamlets.BEETS -> Blocks.BEETROOTS.defaultBlockState().setValue(BlockStateProperties.AGE_3, Math.min(3, age / 2));
						default -> Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, age);
					};
					put(x, y + 1, z, crop);
				}
			}
			if (sx >= 0) {
				force(sx, y, sz, b(Blocks.DIRT));
				put(sx, y + 1, sz, b(Blocks.OAK_FENCE));
				put(sx, y + 2, sz, b(Blocks.HAY_BLOCK));
				put(sx, y + 3, sz, facing(Blocks.CARVED_PUMPKIN, f.gate()));
				put(sx + 1, y + 2, sz, facing(Blocks.SPRUCE_TRAPDOOR, Direction.EAST).setValue(BlockStateProperties.OPEN, true));
			}
		}
	}

	private void houses() {
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.ALLIUM};
		for (int i = 0; i < h.buildings.size(); i++) {
			Building b = h.buildings.get(i);
			Hamlets.Pad p = h.pads.get(i);
			BlockPos door = b.at(0, 0, -2);
			if (b.type != BuildingType.FARM) {
				spawn(door.getX(), p.y() + 1, door.getZ(), 1);
			}
			// Flowers and a woodpile round the house (only on the yard, never inside).
			for (int k = 0; k < 6; k++) {
				int x = p.x0() + r.nextInt(p.x1() - p.x0() + 1);
				int z = p.z0() + r.nextInt(p.z1() - p.z0() + 1);
				if (b.covers(x, z, 1) || Math.abs(x - door.getX()) + Math.abs(z - door.getZ()) < 2) {
					continue;
				}
				put(x, p.y() + 1, z, k == 0 ? b(Blocks.OAK_LOG) : b(flowers[r.nextInt(flowers.length)]));
			}
			// Steps of a path stone in front of the door.
			if (b.type != BuildingType.FARM) {
				BlockPos front = b.at(0, 0, -1);
				force(front.getX(), p.y(), front.getZ(), b(Blocks.DIRT_PATH));
				force(door.getX(), p.y(), door.getZ(), b(Blocks.DIRT_PATH));
				BlockPos lamp = b.at(2, 0, -1);
				put(lamp.getX(), p.y() + 1, lamp.getZ(), b(Blocks.SPRUCE_FENCE));
				put(lamp.getX(), p.y() + 2, lamp.getZ(), b(Blocks.LANTERN));
			}
		}
	}

	@SuppressWarnings("unused")
	private static BlockState stairs(Block block, Direction back) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, back).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}
}
