package com.stasdoto.airdefense.nation;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.nation.Blueprints.L;
import com.stasdoto.airdefense.nation.Blueprints.Plan;
import com.stasdoto.airdefense.street.StreetBlock;
import com.stasdoto.airdefense.street.StreetBlocks;
import com.stasdoto.airdefense.street.StreetPoleBlock;

/**
 * 1.36: the parks that are for playing - a playground (swings, a slide, a sandbox, a roundabout, a seesaw, a climbing
 * frame), a sports ground (a basketball court and a five-a-side pitch) and a small stadium (a pitch with goals, stands
 * along both sides, floodlights on the corners). Each fills a park's 21 x 21 lot (local x -10..10, z 0..20, the street in
 * front at z < 0).
 */
final class Play {
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState LINE = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	private static final BlockState PATH = Blocks.POLISHED_ANDESITE.defaultBlockState();

	private Play() {
	}

	/** A street block facing a local direction. */
	private static BlockState facing(Plan p, Block b, L l) {
		return b.defaultBlockState().setValue(StreetBlock.FACING, p.w(l));
	}

	private static void ground(Plan p, BlockState top) {
		p.fill(-10, -1, 0, 10, -1, 20, Blocks.DIRT.defaultBlockState());
		p.fill(-10, 0, 0, 10, 0, 20, top);
	}

	/** A post of street poles {@code h} high with {@code top} on it. */
	private static void post(Plan p, int x, int z, int h, BlockState top) {
		for (int y = 1; y <= h; y++) {
			p.set(x, y, z, StreetBlocks.POLE_STEEL.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, y == 1));
		}
		p.set(x, h + 1, z, top);
	}

	private static void hedge(Plan p, int gapX) {
		BlockState hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true);
		for (int x = -10; x <= 10; x++) {
			if (Math.abs(x - gapX) > 1) {
				p.set(x, 1, 0, hedge);
			}
			p.set(x, 1, 20, hedge);
		}
		for (int z = 1; z < 20; z++) {
			p.set(-10, 1, z, hedge);
			p.set(10, 1, z, hedge);
		}
	}

	/** Swings, a slide, a sandbox, a roundabout, a seesaw, a climbing frame; benches for the parents, trees, lamps. */
	static void playground(Plan p, int variant) {
		ground(p, GRASS);
		// Soft ground under the play things, a path in from the street.
		p.fill(-8, 0, 3, 8, 0, 18, Blocks.CONCRETE.pick(variant % 2 == 0 ? DyeColor.GREEN : DyeColor.RED).defaultBlockState());
		p.fill(-1, 0, 0, 1, 0, 2, PATH);
		hedge(p, 0);
		boolean mirror = variant % 4 >= 2;
		int s = mirror ? -1 : 1;
		p.set(-5 * s, 1, 5, facing(p, StreetBlocks.SWING, L.FRONT));
		p.set(5 * s, 1, 6, facing(p, StreetBlocks.SLIDE, L.FRONT));
		p.set(-6 * s, 1, 12, facing(p, StreetBlocks.SANDBOX, L.FRONT));
		p.set(5 * s, 1, 13, facing(p, StreetBlocks.ROUNDABOUT, L.FRONT));
		p.set(0, 1, 9, facing(p, StreetBlocks.SEESAW, L.FRONT));
		p.set(0, 1, 16, facing(p, StreetBlocks.CLIMBING_FRAME, L.FRONT));
		// Benches round the edge, facing in; a bin by them.
		for (int z : new int[]{4, 10, 16}) {
			p.set(-9, 1, z, facing(p, StreetBlocks.BENCH_PARK, L.RIGHT));
			p.set(9, 1, z, facing(p, StreetBlocks.BENCH_PARK, L.LEFT));
		}
		p.set(-9, 1, 7, facing(p, StreetBlocks.BIN_EURO, L.RIGHT));
		p.set(9, 1, 13, facing(p, StreetBlocks.BIN_EURO, L.LEFT));
		Block log = variant % 3 == 0 ? Blocks.BIRCH_LOG : Blocks.OAK_LOG;
		Block leaves = variant % 3 == 0 ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES;
		ModernDesigns.tree(p, -7, 18, log, leaves);
		ModernDesigns.tree(p, 7, 18, log, leaves);
		post(p, -8, 2, 2, facing(p, StreetBlocks.LAMP_GLOBE, L.FRONT));
		post(p, 8, 2, 2, facing(p, StreetBlocks.LAMP_GLOBE, L.FRONT));
		p.top = 7;
	}

	/** A basketball court (hoops at both ends) and a five-a-side pitch behind it, a volleyball net between them. */
	static void sports(Plan p, int variant) {
		ground(p, GRASS);
		// The court: dark green with white lines, z 1..9.
		p.fill(-9, 0, 1, 9, 0, 9, Blocks.CONCRETE.pick(DyeColor.GREEN).defaultBlockState());
		p.ring(-9, 1, 9, 9, 0, LINE);
		for (int x = -9; x <= 9; x++) {
			p.set(x, 0, 5, LINE);
		}
		post(p, 0, 2, 1, facing(p, StreetBlocks.BASKET_HOOP, L.BACK));
		post(p, 0, 8, 1, facing(p, StreetBlocks.BASKET_HOOP, L.FRONT));
		// The pitch, z 11..19, goals at the ends (left and right).
		p.ring(-9, 11, 9, 19, 0, LINE);
		for (int z = 11; z <= 19; z++) {
			p.set(0, 0, z, LINE);
		}
		p.set(-8, 1, 15, facing(p, StreetBlocks.FOOTBALL_GOAL, L.RIGHT));
		p.set(8, 1, 15, facing(p, StreetBlocks.FOOTBALL_GOAL, L.LEFT));
		// Between them a volleyball net on the grass strip.
		p.set(-6, 1, 10, facing(p, StreetBlocks.SPORT_NET, L.FRONT));
		p.set(6, 1, 10, facing(p, StreetBlocks.BENCH_MODERN, L.FRONT));
		// A fence of iron bars round it all, a gap at the front.
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		for (int x = -10; x <= 10; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, 0, bars);
				p.set(x, 2, 0, bars);
			}
			p.set(x, 1, 20, bars);
			p.set(x, 2, 20, bars);
		}
		for (int z = 1; z < 20; z++) {
			p.set(-10, 1, z, bars);
			p.set(-10, 2, z, bars);
			p.set(10, 1, z, bars);
			p.set(10, 2, z, bars);
		}
		post(p, -10, 10, 4, facing(p, StreetBlocks.FLOODLIGHT, L.RIGHT));
		post(p, 10, 10, 4, facing(p, StreetBlocks.FLOODLIGHT, L.LEFT));
		p.top = 7;
	}

	/** A pitch running back from the street, goals at its ends, stands along both sides, floodlights at the corners. */
	static void stadium(Plan p, int variant) {
		ground(p, GRASS);
		// The track round the pitch.
		p.fill(-10, 0, 0, 10, 0, 20, Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
		// The pitch: grass in stripes, x -5..5, z 2..18.
		for (int z = 2; z <= 18; z++) {
			BlockState g = (z / 2) % 2 == 0 ? GRASS : Blocks.MOSS_BLOCK.defaultBlockState();
			for (int x = -5; x <= 5; x++) {
				p.set(x, 0, z, g);
			}
		}
		p.ring(-5, 2, 5, 18, 0, LINE);
		for (int x = -5; x <= 5; x++) {
			p.set(x, 0, 10, LINE);
		}
		// The centre circle, near enough.
		for (int[] c : new int[][]{{-2, 9}, {-2, 10}, {-2, 11}, {2, 9}, {2, 10}, {2, 11}, {-1, 8}, {0, 8}, {1, 8}, {-1, 12}, {0, 12}, {1, 12}}) {
			p.set(c[0], 0, c[1], LINE);
		}
		// The penalty boxes.
		p.ring(-3, 2, 3, 5, 0, LINE);
		p.ring(-3, 15, 3, 18, 0, LINE);
		p.set(0, 1, 2, facing(p, StreetBlocks.FOOTBALL_GOAL, L.BACK));
		p.set(0, 1, 18, facing(p, StreetBlocks.FOOTBALL_GOAL, L.FRONT));
		// Stands along both long sides, facing the pitch.
		for (int z : new int[]{4, 7, 10, 13, 16}) {
			p.set(-8, 1, z, facing(p, StreetBlocks.BLEACHERS, L.RIGHT));
			p.set(8, 1, z, facing(p, StreetBlocks.BLEACHERS, L.LEFT));
		}
		// Floodlights on tall masts at the corners.
		post(p, -9, 1, 9, facing(p, StreetBlocks.FLOODLIGHT, L.RIGHT));
		post(p, 9, 1, 9, facing(p, StreetBlocks.FLOODLIGHT, L.LEFT));
		post(p, -9, 19, 9, facing(p, StreetBlocks.FLOODLIGHT, L.RIGHT));
		post(p, 9, 19, 9, facing(p, StreetBlocks.FLOODLIGHT, L.LEFT));
		// A scoreboard over the far goal.
		for (int y = 1; y <= 4; y++) {
			p.set(-2, y, 20, StreetBlocks.POLE_STEEL.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, y == 1));
			p.set(2, y, 20, StreetBlocks.POLE_STEEL.defaultBlockState().setValue(StreetPoleBlock.BOTTOM, y == 1));
		}
		p.fill(-3, 5, 20, 3, 6, 20, Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
		p.set(-1, 6, 20, Blocks.SEA_LANTERN.defaultBlockState());
		p.set(1, 6, 20, Blocks.SEA_LANTERN.defaultBlockState());
		p.top = 11;
	}
}
