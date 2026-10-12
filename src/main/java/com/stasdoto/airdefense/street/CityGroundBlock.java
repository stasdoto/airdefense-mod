package com.stasdoto.airdefense.street;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.stasdoto.airdefense.nation.Seasons;

/**
 * 1.50 "A living town": the street under the weather, in its random ticks. Potholes fill with rain and dry out again;
 * in winter the pavements and the roads are cleared of snow and it is heaped up on the kerbs; in autumn the leaves lie
 * on the pavements under the trees, and are swept away when it is over.
 */
public class CityGroundBlock extends Block {
	public static final int ROAD = 0;
	public static final int WALK = 1;
	public static final int KERB = 2;
	public static final int POTHOLE = 3;
	public static final int PUDDLE = 4;
	private final int kind;

	public CityGroundBlock(Properties properties, int kind) {
		super(properties);
		this.kind = kind;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos up = pos.above();
		BlockState above = level.getBlockState(up);
		switch (kind) {
			case POTHOLE -> {
				if (level.isRainingAt(up)) {
					level.setBlock(pos, Weathered.ASPHALT_PUDDLE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				clearSnow(level, up, above, random);
			}
			case PUDDLE -> {
				if (!level.isRaining() && random.nextInt(3) == 0) {
					level.setBlock(pos, Weathered.ASPHALT_POTHOLE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			case ROAD -> clearSnow(level, up, above, random);
			case WALK -> {
				clearSnow(level, up, above, random);
				leaves(level, up, above, random);
			}
			case KERB -> {
				// The snow shovelled off the pavement and the road heaped up along the kerb.
				if (winter(level, pos)) {
					if (above.isAir() && level.getRandom().nextInt(2) == 0) {
						level.setBlock(up, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 2), Block.UPDATE_CLIENTS);
					} else if (above.is(Blocks.SNOW) && above.getValue(BlockStateProperties.LAYERS) < 4) {
						level.setBlock(up, above.setValue(BlockStateProperties.LAYERS, above.getValue(BlockStateProperties.LAYERS) + 1), Block.UPDATE_CLIENTS);
					}
				}
				leaves(level, up, above, random);
			}
			default -> {
			}
		}
	}

	private static boolean winter(ServerLevel level, BlockPos pos) {
		return Seasons.winterAt(level.getBiome(pos).value().getBaseTemperature());
	}

	/** The pavement and the road are cleared of snow (by the morning), whatever the weather does. */
	private static void clearSnow(ServerLevel level, BlockPos up, BlockState above, RandomSource random) {
		if (above.is(Blocks.SNOW) && random.nextInt(2) == 0) {
			level.setBlock(up, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Autumn: leaves fall on the pavement under a tree; after it they are swept away. */
	private static void leaves(ServerLevel level, BlockPos up, BlockState above, RandomSource random) {
		boolean autumn = Seasons.season == Seasons.AUTUMN;
		if (autumn && above.isAir() && random.nextInt(3) == 0) {
			BlockPos.MutableBlockPos q = up.mutable();
			for (int dy = 1; dy <= 8; dy++) {
				q.move(0, 1, 0);
				if (level.getBlockState(q).is(BlockTags.LEAVES)) {
					level.setBlock(up, Blocks.LEAF_LITTER.defaultBlockState().setValue(BlockStateProperties.SEGMENT_AMOUNT, 1 + random.nextInt(4))
							.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(random.nextInt(4))), Block.UPDATE_CLIENTS);
					return;
				}
			}
		} else if (!autumn && above.is(Blocks.LEAF_LITTER) && random.nextInt(3) == 0) {
			level.setBlock(up, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
