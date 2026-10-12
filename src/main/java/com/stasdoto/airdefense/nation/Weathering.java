package com.stasdoto.airdefense.nation;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.street.Weathered;

/**
 * 1.49 "A town with a past": a building's design, drawn in clean blocks, gets the wear of its years before it is built
 * (new worlds, see {@link Cities#weathered}). Its walls become the weathered materials ({@link Weathered}): the faces
 * that look out get dirt splashed up at the bottom, streaks under the windows, here and there a crack or peeled
 * plaster; how much depends on the building's age (a few are new or just done up). Flat roofs are tar.
 */
public final class Weathering {
	/** For the tests: walls weathered and how (plain, grime, streak, crack, roof). */
	public static volatile long plain;
	public static volatile long grime;
	public static volatile long streak;
	public static volatile long crack;
	public static volatile long roofs;

	private Weathering() {
	}

	/** How worn a building is: 0 new (or done up), towards 1 the oldest. */
	static double age(Building b) {
		long h = CityGen.mix(b.origin.asLong() * 0x9E37 + b.variant * 7919L + b.type.ordinal());
		double u = (h >>> 11) * 0x1.0p-53;
		if (u < 0.12) {
			return 0.05;
		}
		return switch (b.type) {
			case OFFICE, TOWER -> 0.2 + u * 0.4;
			default -> 0.25 + u * 0.75;
		};
	}

	static void apply(Blueprints.Plan p) {
		if (!Cities.weathered || p.blocks.isEmpty()) {
			return;
		}
		Building b = p.b;
		double age = age(b);
		Outline o = new Outline(p.blocks);
		long seed = CityGen.mix(b.origin.asLong() ^ 0x57EA7E3L);
		// Each kind of block's material worked out once (the country's swap, then the weathered stand-in).
		java.util.IdentityHashMap<Block, Weathered.Material> mats = new java.util.IdentityHashMap<>();
		Weathered.Material none = new Weathered.Material("", Blocks.AIR, new Block[0]);
		for (Map.Entry<Long, BlockState> e : p.blocks.entrySet()) {
			BlockState s = e.getValue();
			Weathered.Material m = mats.computeIfAbsent(s.getBlock(), bl -> {
				Weathered.Material w = Weathered.of(swap(b, bl));
				return w == null ? none : w;
			});
			if (m == none) {
				continue;
			}
			long k = e.getKey();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			double u = (CityGen.mix(seed ^ k) >>> 11) * 0x1.0p-53;
			// Flat roofs: the top of a column in grey concrete, high up.
			if (y >= 3 && o.top(x, z) == y && roofable(s.getBlock()) && o.top(x - 1, z) == y && o.top(x + 1, z) == y && o.top(x, z - 1) == y
					&& o.top(x, z + 1) == y) {
				e.setValue(Weathered.ROOF_TAR.defaultBlockState());
				roofs++;
				continue;
			}
			Blueprints.L out = o.outward(x, y, z);
			Weathered.Look look = u < 0.72 ? Weathered.Look.A : Weathered.Look.B;
			if (out != null) {
				double v = (CityGen.mix(seed ^ k ^ 0xD1A7L) >>> 11) * 0x1.0p-53;
				if (y == 1 && v < 0.35 + 0.6 * age || y == 2 && v < 0.25 * age) {
					look = Weathered.Look.GRIME;
					grime++;
				} else if (window(p.blocks.get(BlockPos.asLong(x, y + 1, z))) && v < 0.15 + 0.6 * age) {
					look = Weathered.Look.STREAK;
					streak++;
				} else if (v > 1 - (0.004 + 0.022 * age)) {
					look = Weathered.Look.CRACK;
					crack++;
				} else {
					plain++;
				}
			} else {
				plain++;
			}
			Direction world = out == null ? Direction.NORTH : p.w(out);
			e.setValue(m.state(look, world));
		}
	}

	/**
	 * 1.50: each country's towns built in their own way - Soviet blocks of white silicate or red brick as often as of
	 * panels, European ones rendered in pastel colours (each house its own), American walk-ups of red brick, the desert's
	 * of sandstone.
	 */
	private static final CityStyle[] STYLES = CityStyle.values();

	private static Block swap(Building b, Block block) {
		long h = CityGen.mix(b.origin.asLong() * 0x51_7CC1B7L + b.variant);
		double u = (h >>> 11) * 0x1.0p-53;
		double v = ((h * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
		boolean panel = block == Blocks.CONCRETE.pick(DyeColor.WHITE) || block == Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY);
		boolean flats = b.type == BuildingType.PANEL5 || b.type == BuildingType.APARTMENTS || b.type == BuildingType.PANEL9;
		CityStyle style = b.style >= 0 && b.style < STYLES.length ? STYLES[b.style] : CityStyle.CLASSIC;
		switch (style) {
			case SOVIET -> {
				if (panel && flats && b.type != BuildingType.PANEL9 && u < 0.35) {
					return v < 0.5 ? Blocks.SMOOTH_QUARTZ : Blocks.BRICKS;
				}
			}
			case EUROPEAN -> {
				DyeColor[] pastel = {DyeColor.YELLOW, DyeColor.PINK, DyeColor.LIGHT_BLUE, DyeColor.WHITE, DyeColor.LIME, DyeColor.ORANGE};
				DyeColor own = pastel[(int) (v * pastel.length)];
				if (block instanceof net.minecraft.world.level.block.Block && isDyedTerracotta(block)) {
					return Blocks.DYED_TERRACOTTA.pick(own);
				}
				if (panel && flats && u < 0.5) {
					return Blocks.DYED_TERRACOTTA.pick(own);
				}
			}
			case AMERICAN, CLASSIC -> {
				if (panel && flats && u < 0.5) {
					return Blocks.BRICKS;
				}
			}
			case DESERT -> {
				if (panel && u < 0.6) {
					return Blocks.SMOOTH_SANDSTONE;
				}
			}
			default -> {
			}
		}
		return block;
	}

	private static boolean isDyedTerracotta(Block block) {
		for (DyeColor d : DyeColor.values()) {
			if (Blocks.DYED_TERRACOTTA.pick(d) == block) {
				return true;
			}
		}
		return false;
	}

	/** 1.50: does this town run trams (down its main north-south street)? The big Soviet and European towns. */
	public static boolean trams(Cities.City c) {
		return Cities.weathered && c.size == Cities.Size.LARGE && (c.style == CityStyle.SOVIET || c.style == CityStyle.EUROPEAN);
	}

	/** What the street is made of in a weathered town: {@code kind} 0 asphalt, 1 paint, 2 kerb, 3 pavement, 4 lot grass. */
	static BlockState street(Cities.City c, int kind, int x, int z, BlockState plain) {
		long h = CityGen.mix(c.seed ^ BlockPos.asLong(x, 0, z) * 0x2545F491L);
		double u = (h >>> 11) * 0x1.0p-53;
		double wear = 0.3 + 0.7 * ((CityGen.mix(c.seed ^ 0xA5F1L) >>> 11) * 0x1.0p-53);
		switch (kind) {
			case 0 -> {
				// Patches come in rectangles (a trench dug and filled again), the rest varies in larger blotches.
				long cell = CityGen.mix(c.seed ^ BlockPos.asLong(Math.floorDiv(x, 5), 1, Math.floorDiv(z, 3)) * 0x9E3779B9L);
				if ((cell >>> 11) * 0x1.0p-53 < 0.05 * wear) {
					return Weathered.ASPHALT_PATCH.defaultBlockState();
				}
				if (u < 0.007 * wear) {
					return Weathered.ASPHALT_POTHOLE.defaultBlockState();
				}
				if (u < 0.08 * wear) {
					return Weathered.ASPHALT_CRACK.defaultBlockState();
				}
				long blot = CityGen.mix(c.seed ^ BlockPos.asLong(Math.floorDiv(x, 3), 2, Math.floorDiv(z, 3)));
				return ((blot & 3) == 0 ? Weathered.ASPHALT_B : Weathered.ASPHALT_A).defaultBlockState();
			}
			case 1 -> {
				return (u < 0.15 + 0.5 * wear ? Weathered.ROAD_MARK_WORN : Weathered.ROAD_MARK).defaultBlockState();
			}
			case 2 -> {
				return Weathered.KERB.defaultBlockState();
			}
			case 3 -> {
				return switch (c.style) {
					case SOVIET -> (u < 0.14 * wear ? Weathered.WALK_ASPHALT_CRACK : Weathered.WALK_ASPHALT).defaultBlockState();
					case EUROPEAN -> (u < 0.05 * wear ? Weathered.PAVING_BROKEN : Weathered.PAVING_BRICK).defaultBlockState();
					case DESERT -> Weathered.PAVING_SAND.defaultBlockState();
					default -> {
						long slab = CityGen.mix(c.seed ^ BlockPos.asLong(Math.floorDiv(x, 4), 3, Math.floorDiv(z, 4)));
						if ((slab & 7) == 0) {
							yield Weathered.PAVING_GREY.defaultBlockState();
						}
						yield (u < 0.05 * wear ? Weathered.PAVING_WEEDS : u < 0.09 * wear ? Weathered.PAVING_BROKEN : Weathered.WALK_CONCRETE).defaultBlockState();
					}
				};
			}
			default -> {
				// Trodden ground by the pavements: where people cut across.
				if (u < 0.10 * wear) {
					return Blocks.COARSE_DIRT.defaultBlockState();
				}
				return plain;
			}
		}
	}

	/** The flat roofs' concrete (the parapets round them stay as the walls). */
	private static boolean roofable(Block b) {
		return b == Blocks.CONCRETE.pick(DyeColor.GRAY) || b == Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY) || b == Blocks.CONCRETE.pick(DyeColor.WHITE);
	}

	private static boolean window(BlockState s) {
		if (s == null) {
			return false;
		}
		Block b = s.getBlock();
		return b == Blocks.GLASS_PANE || b == Blocks.GLASS || b instanceof StainedGlassPaneBlock || b instanceof StainedGlassBlock;
	}

	private static long key(int a, int b) {
		return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
	}
}
