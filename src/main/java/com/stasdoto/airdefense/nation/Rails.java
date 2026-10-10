package com.stasdoto.airdefense.nation;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.tags.BlockTags;

import com.stasdoto.airdefense.street.StreetBlock;
import com.stasdoto.airdefense.street.StreetBlocks;
import com.stasdoto.airdefense.street.StreetPoleBlock;
import com.stasdoto.airdefense.street.TrackBlock;

/**
 * 1.39: lays a railway ({@link Railways.Line}) through a chunk as the land is made: the bed of gravel, the embankments
 * and cuttings beside it, the bridges (a deck on pillars, railings; high over the roads it crosses), the tunnels (lined,
 * lit), the track on top, the stations at the ends (a platform with its edge line, a canopy, benches, lamps, the sign,
 * a buffer stop).
 */
public final class Rails {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
	private static final BlockState DECK = Blocks.POLISHED_ANDESITE.defaultBlockState();
	private static final BlockState PILLAR = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState LINING = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState RAILING = Blocks.IRON_BARS.defaultBlockState();
	private static final BlockState PLATFORM = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState EDGE = Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState ROOF = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);

	/** For the tests: track blocks laid. */
	private static volatile int laid;

	public static int laid() {
		return laid;
	}

	private Rails() {
	}

	static void build(CityGen.Writer w, Railways.Line l, List<Cities.Road> roads, List<Cities.City> cities, ChunkPos cp) {
		int x0 = cp.getMinBlockX();
		int z0 = cp.getMinBlockZ();
		int reach = Railways.SIDE + 2;
		int from = -1;
		int to = -1;
		for (int i = 0; i < l.length(); i++) {
			if (l.xs[i] >= x0 - reach && l.xs[i] <= x0 + 15 + reach && l.zs[i] >= z0 - reach && l.zs[i] <= z0 + 15 + reach) {
				if (from < 0) {
					from = i;
				}
				to = i;
			}
		}
		if (from < 0) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Railways.Spot spot = new Railways.Spot();
		Cities.Road.Spot rs = new Cities.Road.Spot();
		for (int x = x0; x < x0 + 16; x++) {
			for (int z = z0; z < z0 + 16; z++) {
				if (!l.locate(x + 0.5, z + 0.5, from - 1, to + 2, Railways.SIDE + 0.5, spot) || town(cities, x, z)) {
					continue;
				}
				column(w, l, roads, spot, rs, x, z, pos);
			}
		}
		for (int i = from; i <= to; i++) {
			int x = l.xs[i];
			int z = l.zs[i];
			if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) {
				continue;
			}
			int y = l.y(i);
			if (town(cities, x, z)) {
				continue;
			}
			if (i == 0 || i == l.length() - 1) {
				int j = i == 0 ? 1 : i - 1;
				Direction d = Direction.getApproximateNearest(l.xs[j] - x, 0, l.zs[j] - z);
				w.set(pos.set(x, y, z), StreetBlocks.BUFFER_STOP.defaultBlockState().setValue(StreetBlock.FACING, d));
			} else {
				w.set(pos.set(x, y, z), StreetBlocks.TRACK.defaultBlockState().setValue(TrackBlock.DIR, (int) l.dir[i]).setValue(TrackBlock.LIFT, l.lift(i)));
			}
			laid++;
			if (l.station(i)) {
				station(w, l, i, cp, pos);
			}
		}
	}

	/** Never into a town or its margin (the planner keeps out of them; this is in case). */
	private static boolean town(List<Cities.City> cities, int x, int z) {
		for (Cities.City c : cities) {
			if (c.outside(x, z) <= Cities.MARGIN) {
				return true;
			}
		}
		return false;
	}

	/** Is any road's carriageway on this column? Its surface height, or MIN_VALUE. */
	private static int roadTop(List<Cities.Road> roads, Cities.Road.Spot rs, int x, int z) {
		int top = Integer.MIN_VALUE;
		for (Cities.Road r : roads) {
			if (x < r.minX - r.half - 2 || x > r.maxX + r.half + 2 || z < r.minZ - r.half - 2 || z > r.maxZ + r.half + 2) {
				continue;
			}
			if (r.locate(x + 0.5, z + 0.5, r.half + 2, rs) && rs.along > -1 && rs.along < r.length + 1 && Math.abs(rs.across) <= r.halfAt(rs.along) + 1.5) {
				top = Math.max(top, (int) Math.floor(r.height(rs.along)));
			}
		}
		return top;
	}

	private static void column(CityGen.Writer w, Railways.Line l, List<Cities.Road> roads, Railways.Spot spot, Cities.Road.Spot rs, int x, int z,
			BlockPos.MutableBlockPos pos) {
		int nb = spot.u < 0.5 ? spot.i : Math.min(l.length() - 1, spot.i + 1);
		int y = (int) Math.floor(l.height(spot));
		double d = spot.dist;
		int kind = l.kind[nb];
		boolean platform = l.station(nb) && spot.across * l.platform > 0 && kind == 0;
		int road = roadTop(roads, rs, x, z);
		if (road != Integer.MIN_VALUE) {
			// Over a road: only the deck, high above it (the road below is left as it is).
			if (d <= Railways.BED && y - 1 > road + 3) {
				deck(w, l, nb, x, z, y, d, false, pos);
			}
			return;
		}
		int[] g = CityGen.ground(w, x, z, pos);
		if (kind == 2 && g[0] > y + 6) {
			tunnel(w, nb, x, z, y, d, pos);
			return;
		}
		if (d <= Railways.BED) {
			if (kind == 1 || g[2] == 1 || g[0] < y - 5) {
				deck(w, l, nb, x, z, y, d, true, pos);
			} else {
				CityGen.shape(w, x, z, y - 1, GRAVEL, g, pos);
			}
			return;
		}
		if (platform && d <= Railways.BED + 4) {
			CityGen.shape(w, x, z, y, d <= Railways.BED + 1 ? EDGE : PLATFORM, g, pos);
			return;
		}
		if (g[2] == 1 || kind == 2) {
			return;
		}
		// The embankment or the cutting beside the bed (beside the platform at a station).
		int base = platform ? y : y - 1;
		double side = d - (platform ? Railways.BED + 4 : Railways.BED);
		if (g[0] < base - 1) {
			int top = base - (int) Math.ceil(side);
			if (top > g[0]) {
				CityGen.shape(w, x, z, top, GRASS, g, pos);
			}
		} else if (g[0] > base + 1) {
			int top = base + (int) Math.ceil(side * 1.5);
			if (top < g[0]) {
				BlockState s = w.get(pos.set(x, g[0], z));
				CityGen.shape(w, x, z, top, s.is(BlockTags.DIRT) ? GRASS : s, g, pos);
			}
		}
	}

	/** A bridge's deck: railings along its edges, a pillar down to the ground every 16 blocks (not onto a road). */
	private static void deck(CityGen.Writer w, Railways.Line l, int nb, int x, int z, int y, double d, boolean pillars, BlockPos.MutableBlockPos pos) {
		w.set(pos.set(x, y - 1, z), DECK);
		for (int yy = y; yy <= y + 5; yy++) {
			if (!w.get(pos.set(x, yy, z)).isAir()) {
				w.set(pos, AIR);
			}
		}
		if (d > Railways.BED - 1) {
			w.set(pos.set(x, y, z), RAILING);
		}
		if (pillars && Math.floorMod(nb, 16) < 2 && d <= 1.6) {
			for (int yy = y - 2; yy > w.level.getMinY(); yy--) {
				BlockState s = w.get(pos.set(x, yy, z));
				if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) {
					break;
				}
				w.set(pos, PILLAR);
			}
		}
		w.levelled(y - 1, y + 5);
	}

	/** Inside a tunnel: the floor, the clear way (the train is four blocks and a bit high), the lining, a lamp now and then. */
	private static void tunnel(CityGen.Writer w, int nb, int x, int z, int y, double d, BlockPos.MutableBlockPos pos) {
		if (d <= Railways.BED) {
			w.set(pos.set(x, y - 1, z), GRAVEL);
			for (int yy = y; yy <= y + 5; yy++) {
				w.set(pos.set(x, yy, z), AIR);
			}
			w.set(pos.set(x, y + 6, z), nb % 12 == 0 && d < 0.8 ? LIGHT : LINING);
		} else if (d <= Railways.BED + 1.2) {
			for (int yy = y - 1; yy <= y + 6; yy++) {
				w.set(pos.set(x, yy, z), LINING);
			}
		}
	}

	private static boolean inChunk(ChunkPos cp, int x, int z) {
		return x >= cp.getMinBlockX() && x <= cp.getMaxBlockX() && z >= cp.getMinBlockZ() && z <= cp.getMaxBlockZ();
	}

	/** The station's things by track block i: benches, lamps, bins, the canopy, the sign (on the platform beside it). */
	private static void station(CityGen.Writer w, Railways.Line l, int i, ChunkPos cp, BlockPos.MutableBlockPos pos) {
		int n = l.length();
		int a = Math.min(i, n - 2);
		int sx = l.xs[a + 1] - l.xs[a];
		int sz = l.zs[a + 1] - l.zs[a];
		if (sx != 0 && sz != 0) {
			return;
		}
		// Across, towards the platform.
		int px = -sz * l.platform;
		int pz = sx * l.platform;
		int j = i < Railways.STATION ? i : n - 1 - i;
		int y = l.y(i);
		Direction toTrack = Direction.getApproximateNearest(-px, 0, -pz);
		if (j >= 5 && j <= 25) {
			// The canopy: posts at the back of the platform, the roof over it.
			for (int k = 3; k <= 6; k++) {
				int x = l.xs[i] + px * k;
				int z = l.zs[i] + pz * k;
				if (inChunk(cp, x, z)) {
					w.set(pos.set(x, y + 4, z), ROOF);
				}
			}
			int x = l.xs[i] + px * 6;
			int z = l.zs[i] + pz * 6;
			if (j % 5 == 0 && inChunk(cp, x, z)) {
				for (int yy = 1; yy <= 3; yy++) {
					w.set(pos.set(x, y + yy, z), StreetBlocks.POLE_STEEL.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, yy == 1));
				}
			}
		}
		int x = l.xs[i] + px * 5;
		int z = l.zs[i] + pz * 5;
		if (!inChunk(cp, x, z)) {
			return;
		}
		if (j == 15) {
			w.set(pos.set(x, y + 1, z), StreetBlocks.STATION_SIGN.defaultBlockState().setValue(StreetBlock.FACING, toTrack));
		} else if (j % 6 == 2 && j < 28) {
			w.set(pos.set(x, y + 1, z), StreetBlocks.BENCH_MODERN.defaultBlockState().setValue(StreetBlock.FACING, toTrack));
		} else if (j % 12 == 9) {
			w.set(pos.set(x, y + 1, z), StreetBlocks.BIN_MODERN.defaultBlockState().setValue(StreetBlock.FACING, toTrack));
		} else if (j == 0 || j == 28) {
			// Lamps at the ends of the canopy.
			for (int yy = 1; yy <= 3; yy++) {
				w.set(pos.set(x, y + yy, z), StreetBlocks.POLE_STEEL.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, yy == 1));
			}
			w.set(pos.set(x, y + 4, z), StreetBlocks.LAMP_MODERN.defaultBlockState().setValue(StreetBlock.FACING, toTrack));
		}
	}
}
