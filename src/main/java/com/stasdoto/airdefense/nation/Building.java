package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One of a village's buildings, built or still going up. {@link #origin} is the ground block in the middle of the
 * front wall (the door), the building stretches away from it towards {@link #facing}. Roads keep their stretches as
 * pairs of points in {@link #points}; for them {@link #index} counts the road blocks laid so far.
 */
public final class Building {
	public static final Codec<Building> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(b -> b.id),
			Codec.INT.fieldOf("type").forGetter(b -> b.type.ordinal()),
			BlockPos.CODEC.fieldOf("origin").forGetter(b -> b.origin),
			Codec.INT.fieldOf("facing").forGetter(b -> b.facing.get2DDataValue()),
			Codec.INT.optionalFieldOf("index", 0).forGetter(b -> b.index),
			Codec.BOOL.optionalFieldOf("done", false).forGetter(b -> b.done),
			Codec.BOOL.optionalFieldOf("free", false).forGetter(b -> b.free),
			BlockPos.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(b -> new ArrayList<>(b.points)),
			Codec.INT.optionalFieldOf("total", 0).forGetter(b -> b.total),
			Codec.INT.optionalFieldOf("variant", 0).forGetter(b -> b.variant),
			Codec.INT.optionalFieldOf("style", 0).forGetter(b -> b.style)
	).apply(i, (id, type, origin, facing, index, done, free, points, total, variant, style) -> {
		Building b = new Building(id, BuildingType.byId(type), origin, Direction.from2DDataValue(facing), free);
		b.variant = variant;
		b.style = style;
		b.index = index;
		b.done = done;
		b.points.addAll(points);
		b.total = total;
		return b;
	}));

	public final int id;
	public final BuildingType type;
	public final BlockPos origin;
	public final Direction facing;
	/** Ordered in creative: no builders needed, goes up fast. */
	public final boolean free;
	/** How far construction has got (blueprint entries, or road blocks). */
	public int index;
	/** Blueprint size when it was last known (for the percentage on the screen). */
	public int total;
	public boolean done;
	/** Which look of the design (colours, details). */
	public int variant;
	/** 1.28: the town's style ({@link CityStyle}, 0 = the classic look). */
	public int style;
	public final List<BlockPos> points = new ArrayList<>();

	public Building(int id, BuildingType type, BlockPos origin, Direction facing, boolean free) {
		this.id = id;
		this.type = type;
		this.origin = origin;
		this.facing = facing;
		this.free = free;
	}

	public int percent() {
		if (done) {
			return 100;
		}
		return total <= 0 ? 0 : Math.min(99, index * 100 / total);
	}

	/** Local (x right, y up, z into the building) to world. */
	public BlockPos at(int x, int y, int z) {
		Direction right = facing.getClockWise();
		return origin.offset(right.getStepX() * x + facing.getStepX() * z, y, right.getStepZ() * x + facing.getStepZ() * z);
	}

	/** The spot right in front of the door (where roads end and workers deliver). */
	public BlockPos doorstep() {
		return at(0, 0, -2);
	}

	/** The middle of the footprint at floor level. */
	public BlockPos middle() {
		return at(0, 1, type.depth / 2);
	}

	/** Does the footprint (with a margin) cover this column? */
	public boolean covers(int x, int z, int margin) {
		if (type == BuildingType.ROADS) {
			return false;
		}
		int hw = type.halfWidth() + margin;
		Direction right = facing.getClockWise();
		int dx = x - origin.getX();
		int dz = z - origin.getZ();
		int lx = dx * right.getStepX() + dz * right.getStepZ();
		int lz = dx * facing.getStepX() + dz * facing.getStepZ();
		int front = type == BuildingType.FACTORY ? 4 : 2;
		return lx >= -hw && lx <= hw && lz >= -margin - front && lz <= type.depth - 1 + margin;
	}
}
