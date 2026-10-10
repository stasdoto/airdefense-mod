package com.stasdoto.airdefense.fort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.nation.Country;
import com.stasdoto.airdefense.nation.Politics;
import com.stasdoto.airdefense.nation.Settlement;
import com.stasdoto.airdefense.street.StreetBlock;

/**
 * 1.48 "Positions": the field works and who builds them. A work is dug and built at once in front of whoever puts it
 * down (a kit) - its front towards the enemy:
 * <ul>
 * <li>a trench: nine blocks of it, two deep, boarded sides, duckboards, a firing step along the front with a parapet of
 * sandbags (full bags every few blocks as traverses), the spoil heaped behind;</li>
 * <li>a firing position: a pit ringed with sandbags, a camouflage net over it on posts;</li>
 * <li>a dugout: a room three deep under a roof of logs and earth, steps down into it, a lantern and a barrel;</li>
 * <li>a pillbox: concrete, sunk a little, firing slits to the front and sides, sandbags and earth round it;</li>
 * <li>a vehicle revetment: a sunk bay with walls of earth and sandbags on three sides, a net over it;</li>
 * <li>an obstacle line (the AI's): Czech hedgehogs with coils of barbed wire between them;</li>
 * <li>a foxhole (soldiers digging in where they were sent): a horseshoe of sandbags.</li>
 * </ul>
 * The world's countries at war fortify their towns facing the enemy (only near players, so it is seen and the land is
 * there), a work every twenty seconds up to a few per town; their guards take the posts of these works when the fighting
 * comes.
 */
public final class Fortify {
	public enum Kind {
		TRENCH, POSITION, DUGOUT, PILLBOX, REVETMENT, OBSTACLES, FOXHOLE
	}

	/** For the tests: works built (all), works the AI built, soldiers who dug in, posts taken by men in a fight. */
	public static int built;
	public static int aiBuilt;
	public static int dugIn;
	public static int manned;

	private Fortify() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Fortify::tick);
	}

	// ------------------------------------------------------------------------------------------------
	// Building

	/** The local frame of a work: forward (towards the enemy), right, and the ground block it stands on. */
	private record Frame(ServerLevel level, BlockPos ground, Direction f, Direction r, List<BlockPos> posts) {
		BlockPos at(int i, int j, int dy) {
			return ground.relative(r, i).relative(f, j).above(dy);
		}

		void set(int i, int j, int dy, BlockState s) {
			level.setBlock(at(i, j, dy), s, Block.UPDATE_ALL);
		}

		void air(int i, int j, int dy) {
			set(i, j, dy, Blocks.AIR.defaultBlockState());
		}

		BlockState bags(boolean low) {
			return (low ? FortBlocks.SANDBAGS_LOW : FortBlocks.SANDBAGS).defaultBlockState().setValue(StreetBlock.FACING, f.getOpposite());
		}

		BlockState log(Direction along) {
			return Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getAxis());
		}

		/** A standing spot (for the posts): the block a man stands in. */
		void post(int i, int j, int dy) {
			posts.add(at(i, j, dy));
		}
	}

	/**
	 * Builds a work with its front facing {@code facing}, on the ground at (or below) {@code clicked}. False if it will
	 * not go there (water, the edge of the world).
	 */
	public static boolean build(ServerLevel level, BlockPos clicked, Direction facing, Kind kind) {
		int x = clicked.getX();
		int z = clicked.getZ();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos ground = new BlockPos(x, Math.min(top, clicked.getY()), z);
		if (ground.getY() - 3 < level.getMinY() || ground.getY() + 8 > level.getMaxY()) {
			return false;
		}
		if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) {
			return false;
		}
		Direction f = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
		Frame w = new Frame(level, ground, f, f.getClockWise(), new ArrayList<>());
		switch (kind) {
			case TRENCH -> trench(w);
			case POSITION -> position(w, true);
			case DUGOUT -> dugout(w);
			case PILLBOX -> pillbox(w);
			case REVETMENT -> revetment(w);
			case OBSTACLES -> obstacles(w);
			case FOXHOLE -> position(w, false);
		}
		built++;
		if (!w.posts.isEmpty()) {
			Works works = Works.get(level.getServer());
			for (BlockPos p : w.posts) {
				works.posts.add(p.asLong());
			}
			while (works.posts.size() > 4096) {
				works.posts.removeFirst();
			}
			works.setDirty();
		}
		return true;
	}

	private static void trench(Frame w) {
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (int i = -4; i <= 4; i++) {
			// The walking row (two deep, duckboards) and the firing step in front of it.
			w.air(i, 0, 0);
			w.air(i, 0, -1);
			w.set(i, 0, -2, planks);
			w.air(i, 1, 0);
			w.set(i, 1, -1, planks);
			w.air(i, 0, 1);
			w.air(i, 1, 1);
			// The boarded back wall, the spoil heaped behind it.
			w.set(i, -1, 0, planks);
			w.set(i, -1, -1, planks);
			w.set(i, -1, 1, Blocks.COARSE_DIRT.defaultBlockState());
			// The parapet: low bags to shoot over, a full bag every fourth block as a traverse.
			if (w.level.getBlockState(w.at(i, 2, 0)).isAir()) {
				w.set(i, 2, 0, Blocks.DIRT.defaultBlockState());
			}
			w.set(i, 2, 1, w.bags(Math.floorMod(i, 4) != 0));
		}
		for (int s : new int[]{-5, 5}) {
			for (int j = -1; j <= 2; j++) {
				w.set(s, j, 0, planks);
				w.set(s, j, -1, planks);
			}
		}
		w.post(-3, 1, 1);
		w.post(-1, 1, 1);
		w.post(1, 1, 1);
		w.post(3, 1, 1);
	}

	/** A firing position (a pit ringed with sandbags, a net over it on four posts) or, without the pit and net, a foxhole. */
	private static void position(Frame w, boolean full) {
		int r = full ? 2 : 1;
		for (int i = -r; i <= r; i++) {
			for (int j = 1 - r; j <= 1 + r; j++) {
				boolean edge = Math.abs(i) == r || j == 1 - r || j == 1 + r;
				if (!edge) {
					if (full) {
						w.air(i, j, 0);
					}
					w.air(i, j, 1);
					w.air(i, j, 2);
					continue;
				}
				// The back of it open (the way in).
				if (j == 1 - r && i == 0) {
					w.air(i, j, 1);
					continue;
				}
				w.set(i, j, 1, w.bags(j == 1 + r));
				if (full && j != 1 + r) {
					w.set(i, j, 2, w.bags(true));
				}
			}
		}
		w.post(0, 1, full ? 0 : 1);
		if (full) {
			BlockState post = Blocks.SPRUCE_FENCE.defaultBlockState();
			for (int i : new int[]{-r, r}) {
				for (int j : new int[]{1 - r, 1 + r}) {
					w.set(i, j, 2, post);
					w.set(i, j, 3, post);
				}
			}
			for (int i = -r - 1; i <= r + 1; i++) {
				for (int j = -r; j <= 2 + r; j++) {
					if (w.level.getBlockState(w.at(i, j, 4)).isAir()) {
						w.set(i, j, 4, FortBlocks.CAMO_NET.defaultBlockState());
					}
				}
			}
		}
	}

	private static void dugout(Frame w) {
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		// The room: five across, four deep, three high, its floor three down.
		for (int i = -3; i <= 3; i++) {
			for (int j = -1; j <= 4; j++) {
				boolean wall = Math.abs(i) == 3 || j == -1 || j == 4;
				for (int dy = -3; dy <= 0; dy++) {
					if (wall) {
						w.set(i, j, dy, w.log(Direction.UP));
					} else if (dy == -3) {
						w.set(i, j, dy, planks);
					} else {
						w.air(i, j, dy);
					}
				}
				// The roof: logs across, a layer of earth over them, grass on top.
				w.set(i, j, 1, w.log(w.r));
				w.set(i, j, 2, Blocks.DIRT.defaultBlockState());
				w.set(i, j, 3, Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		// Steps down from the back, through the wall.
		Direction up = w.f.getOpposite();
		BlockState stair = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up);
		for (int k = 0; k < 3; k++) {
			int j = -1 - k;
			w.set(0, j, -3 + k, stair);
			for (int dy = -2 + k; dy <= 3; dy++) {
				if (dy <= 0 || j < -1) {
					w.air(0, j, dy);
				}
			}
			w.set(-1, j, -2 + k, planks);
			w.set(1, j, -2 + k, planks);
		}
		w.air(0, -1, 1);
		w.air(0, -1, 2);
		w.air(0, -1, 3);
		// A lantern, a barrel of supplies, a bench along the wall.
		w.set(-2, 3, -2, Blocks.LANTERN.defaultBlockState());
		w.set(2, 3, -2, Blocks.BARREL.defaultBlockState());
		for (int i = -1; i <= 1; i++) {
			w.set(i, 3, -2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, w.f));
		}
		w.post(0, 1, -2);
		w.post(1, 2, -2);
	}

	private static void pillbox(Frame w) {
		BlockState concrete = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
		for (int i = -2; i <= 2; i++) {
			for (int j = 0; j <= 4; j++) {
				boolean wall = Math.abs(i) == 2 || j == 0 || j == 4;
				w.set(i, j, 0, concrete);
				for (int dy = 1; dy <= 3; dy++) {
					w.set(i, j, dy, wall ? concrete : Blocks.AIR.defaultBlockState());
				}
				w.set(i, j, 4, concrete);
			}
		}
		// Firing slits: three to the front, one each side; the door at the back.
		for (int i = -1; i <= 1; i++) {
			w.air(i, 4, 2);
		}
		w.air(-2, 2, 2);
		w.air(2, 2, 2);
		w.air(0, 0, 1);
		w.air(0, 0, 2);
		// Sandbags along the front, earth banked against the sides.
		for (int i = -3; i <= 3; i++) {
			w.set(i, 5, 1, w.bags(true));
		}
		for (int j = 0; j <= 4; j++) {
			for (int s : new int[]{-3, 3}) {
				w.set(s, j, 1, Blocks.COARSE_DIRT.defaultBlockState());
				w.set(s, j, 2, Blocks.DIRT.defaultBlockState());
			}
		}
		w.post(0, 3, 1);
		w.post(-1, 2, 1);
		w.post(1, 2, 1);
	}

	private static void revetment(Frame w) {
		BlockState earth = Blocks.COARSE_DIRT.defaultBlockState();
		for (int i = -3; i <= 3; i++) {
			for (int j = 0; j <= 9; j++) {
				boolean side = Math.abs(i) == 3 || j == 9;
				if (side) {
					w.set(i, j, 1, earth);
					w.set(i, j, 2, w.bags(false));
				} else {
					// The bay, sunk one block (the back of it open: the way in).
					w.air(i, j, 0);
					w.air(i, j, 1);
					w.air(i, j, 2);
					w.air(i, j, 3);
					w.set(i, j, -1, Blocks.COARSE_DIRT.defaultBlockState());
				}
			}
		}
		BlockState post = Blocks.SPRUCE_FENCE.defaultBlockState();
		for (int i : new int[]{-3, 3}) {
			for (int j : new int[]{0, 9}) {
				w.set(i, j, 3, post);
				w.set(i, j, 4, post);
			}
		}
		for (int i = -4; i <= 4; i++) {
			for (int j = -1; j <= 10; j++) {
				if (w.level.getBlockState(w.at(i, j, 5)).isAir()) {
					w.set(i, j, 5, FortBlocks.CAMO_NET.defaultBlockState());
				}
			}
		}
	}

	private static void obstacles(Frame w) {
		for (int i = -7; i <= 7; i++) {
			if (!w.level.getBlockState(w.at(i, 0, 1)).isAir() && !w.level.getBlockState(w.at(i, 0, 1)).canBeReplaced()) {
				continue;
			}
			w.set(i, 0, 1, Math.floorMod(i, 3) == 0 ? FortBlocks.HEDGEHOG.defaultBlockState().setValue(StreetBlock.FACING, w.f)
					: FortBlocks.BARBED_WIRE.defaultBlockState());
			if (Math.floorMod(i, 2) == 0 && w.level.getBlockState(w.at(i, -1, 1)).isAir()) {
				w.set(i, -1, 1, FortBlocks.BARBED_WIRE.defaultBlockState());
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Posts (where men stand to fight from the works)

	/** The nearest free post of a work within {@code reach} of {@code from} (not one somebody else stands at). */
	public static BlockPos postNear(ServerLevel level, BlockPos from, double reach, java.util.function.Predicate<BlockPos> free) {
		Works works = Works.get(level.getServer());
		BlockPos best = null;
		double bestD = reach * reach;
		for (long l : works.posts) {
			BlockPos p = BlockPos.of(l);
			double d = p.distSqr(from);
			if (d < bestD && free.test(p)) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** A soldier sent somewhere digs in when he gets there (one in four: a foxhole for the squad). */
	public static void digIn(ServerLevel level, BlockPos at, Direction facing) {
		if (postNear(level, at, 10, p -> true) != null) {
			return;
		}
		BlockPos ground = at.below();
		if (com.stasdoto.airdefense.nation.Sites.naturalGround(level.getBlockState(ground)) && build(level, ground, facing, Kind.FOXHOLE)) {
			dugIn++;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The world's countries fortify their towns

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 400 != 123) {
			return;
		}
		fortifyOnce(server);
	}

	/** One round of the world's countries fortifying (one work, for the first town that wants one). Public for the tests. */
	public static void fortifyOnce(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Politics p = Politics.get(server);
		Works works = Works.get(server);
		for (Country c : p.countries.values()) {
			if (c.owner != null || c.wars.isEmpty()) {
				continue;
			}
			for (Settlement s : p.settlementsOf(c.id)) {
				if (!level.isLoaded(s.flag) || level.getNearestPlayer(s.center.getX(), s.center.getY(), s.center.getZ(), 220, false) == null) {
					continue;
				}
				int done = works.count.getOrDefault(s.id, 0);
				int quota = s.isDistrict() ? 2 : s.isCity() ? 5 : 3;
				if (done >= quota) {
					continue;
				}
				Settlement enemy = nearestEnemy(p, c, s);
				if (enemy == null) {
					continue;
				}
				double a = Math.atan2(enemy.center.getZ() - s.center.getZ(), enemy.center.getX() - s.center.getX());
				a += (done % 2 == 0 ? 1 : -1) * ((done + 1) / 2) * 0.5;
				double dist = s.radius + 12;
				int x = s.center.getX() + (int) Math.round(Math.cos(a) * dist);
				int z = s.center.getZ() + (int) Math.round(Math.sin(a) * dist);
				BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
				works.count.put(s.id, done + 1);
				works.setDirty();
				if (!level.isLoaded(at) || !com.stasdoto.airdefense.nation.Sites.naturalGround(level.getBlockState(at))) {
					continue;
				}
				Direction facing = Direction.getApproximateNearest(Math.cos(a), 0, Math.sin(a));
				Kind kind = switch (done % 5) {
					case 0, 3 -> Kind.TRENCH;
					case 1 -> Kind.POSITION;
					case 2 -> Kind.OBSTACLES;
					default -> s.isCity() ? Kind.PILLBOX : Kind.POSITION;
				};
				if (build(level, at, facing, kind)) {
					aiBuilt++;
					AirDefense.LOGGER.info("[airdefense] {} digs a {} at {} facing {}", s.name, kind.name().toLowerCase(java.util.Locale.ROOT), at.toShortString(), enemy.name);
				}
				return;
			}
		}
	}

	private static Settlement nearestEnemy(Politics p, Country c, Settlement s) {
		Settlement best = null;
		double bd = Double.MAX_VALUE;
		for (int w : c.wars) {
			for (Settlement o : p.settlementsOf(w)) {
				double d = o.center.distSqr(s.center);
				if (d < bd) {
					bd = d;
					best = o;
				}
			}
		}
		return best;
	}

	/** Saved with the world: how many works each town has dug, and the posts of all the works. */
	public static final class Works extends SavedData {
		public static final Codec<Works> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.listOf().optionalFieldOf("count", List.of()).forGetter(w -> {
					List<Integer> out = new ArrayList<>();
					w.count.forEach((k, v) -> {
						out.add(k);
						out.add(v);
					});
					return out;
				}),
				Codec.LONG.listOf().optionalFieldOf("posts", List.of()).forGetter(w -> new ArrayList<>(w.posts))
		).apply(i, (count, posts) -> {
			Works w = new Works();
			for (int k = 0; k + 1 < count.size(); k += 2) {
				w.count.put(count.get(k), count.get(k + 1));
			}
			w.posts.addAll(posts);
			return w;
		}));
		public static final SavedDataType<Works> TYPE = new SavedDataType<>(AirDefense.id("fortifications"), Works::new, CODEC, null);

		final Map<Integer, Integer> count = new HashMap<>();
		final java.util.ArrayDeque<Long> posts = new java.util.ArrayDeque<>();

		public static Works get(MinecraftServer server) {
			return server.getDataStorage().computeIfAbsent(TYPE);
		}
	}

	/** For the tests: is a player near enough (unused helper kept for the scene). */
	static boolean near(ServerLevel level, BlockPos p, double r) {
		Player pl = level.getNearestPlayer(p.getX(), p.getY(), p.getZ(), r, false);
		return pl != null;
	}
}
