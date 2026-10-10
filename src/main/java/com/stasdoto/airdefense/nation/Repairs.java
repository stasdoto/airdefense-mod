package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;

/**
 * Towns rebuild what the war broke (1.25.2). Every explosion that breaks blocks in a town, a hamlet, a depot or on a
 * road is noted; once things have been quiet there for a while (half a minute), the town's people put it back as it
 * was - the streets and craters, the houses and the decorations - block by block, a few a second, so it can be
 * watched. With a player in creative mode near by it all comes back almost at once. Only what the war broke is put
 * back: blocks a player has put there himself are left alone.
 */
public final class Repairs extends SavedData {
	/** A patch of damage: centre, radius, when it was last hit (game time), whose it is (settlement id or -1). */
	public record Zone(int x, int y, int z, int r, long since, int town) {
		static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("x").forGetter(Zone::x),
				Codec.INT.fieldOf("y").forGetter(Zone::y),
				Codec.INT.fieldOf("z").forGetter(Zone::z),
				Codec.INT.fieldOf("r").forGetter(Zone::r),
				Codec.LONG.fieldOf("since").forGetter(Zone::since),
				Codec.INT.fieldOf("town").forGetter(Zone::town)
		).apply(i, Zone::new));

		BoundingBox box() {
			return new BoundingBox(x - r, y - r - 2, z - r, x + r, y + r + 6, z + r);
		}

		long key() {
			return BlockPos.asLong(x, y, z);
		}
	}

	public static final Codec<Repairs> CODEC = RecordCodecBuilder.create(i -> i.group(
			Zone.CODEC.listOf().fieldOf("zones").forGetter(r -> r.zones)
	).apply(i, Repairs::new));
	public static final SavedDataType<Repairs> TYPE = new SavedDataType<>(AirDefense.id("repairs"), Repairs::new, CODEC, null);

	/** Quiet time before work starts: in survival, with a creative player near by (his own towns), in a country at war. */
	private static final int WAIT = 600;
	private static final int WAIT_CREATIVE = 40;
	private static final int WAIT_WAR = 3600;
	/** Blocks put back per second: by the town's people, with a creative player near by (his own towns), at war. */
	private static final int RATE = 12;
	private static final int RATE_CREATIVE = 6000;
	private static final int RATE_WAR = 4;

	private final List<Zone> zones = new ArrayList<>();
	/** The work out for each zone being rebuilt (not saved: worked out again after a restart). */
	private final Map<Long, List<long[]>> work = new HashMap<>();
	private final Map<Long, Map<Long, BlockState>> plans = new HashMap<>();
	/** For the tests: zones noted, blocks put back, zones finished. */
	public static int noted;
	public static int rebuilt;
	public static int finished;

	public Repairs() {
		this(List.of());
	}

	private Repairs(List<Zone> zones) {
		this.zones.addAll(zones);
	}

	public static Repairs get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 7) {
				long t0 = System.nanoTime();
				get(server).tick(server.overworld());
				com.stasdoto.airdefense.util.Perf.add(com.stasdoto.airdefense.util.Perf.REPAIRS, System.nanoTime() - t0);
			}
		});
	}

	/** An explosion broke blocks here (called for every one, from the game's explosion). */
	public static void exploded(ServerLevel level, Vec3 at, float radius) {
		if (level != level.getServer().overworld() || radius < 1.2f) {
			return;
		}
		int town = townAt(level, at);
		if (town == -2) {
			return;
		}
		Repairs r = get(level.getServer());
		int rr = (int) Math.ceil(radius * 1.6) + 2;
		long now = level.getGameTime();
		// Merge with a patch it overlaps.
		for (int i = 0; i < r.zones.size(); i++) {
			Zone z = r.zones.get(i);
			double d = Math.sqrt(Mth2.sq(z.x - at.x) + Mth2.sq(z.y - at.y) + Mth2.sq(z.z - at.z));
			if (d < z.r + rr) {
				int nr = (int) Math.min(40, Math.ceil(Math.max(z.r, d + rr)));
				r.zones.set(i, new Zone(z.x, z.y, z.z, nr, now, z.town >= 0 ? z.town : town));
				r.forget(z.key());
				r.setDirty();
				return;
			}
		}
		r.zones.add(new Zone((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z), rr, now, town));
		noted++;
		r.setDirty();
		if (town >= 0) {
			// 1.38: the fire engine, the ambulance and the police come.
			Services.explosion(level, at, town);
		}
	}

	/** Whose land a point is for rebuilding: a settlement's id, -1 for a road or a planned town not founded, -2 none. */
	private static int townAt(ServerLevel level, Vec3 at) {
		Politics p = Politics.get(level.getServer());
		BlockPos b = BlockPos.containing(at);
		for (Settlement s : p.settlements.values()) {
			double r = s.radius + 24;
			if (s.center.distSqr(b) < r * r) {
				return s.id;
			}
		}
		long seed = level.getSeed();
		if (Cities.plannedCityAt(seed, b.getX(), b.getZ(), 30) != null || Cities.plannedHamletAt(seed, b.getX(), b.getZ(), 60) != null) {
			return -1;
		}
		// On (or right by) a road.
		Cities.Terrain t = Cities.terrain(level);
		Cities.Road.Spot spot = new Cities.Road.Spot();
		for (Cities.Road road : Cities.mainRoadsNear(seed, t, b.getX(), b.getZ())) {
			if (road.locate(at.x, at.z, road.half + 8, spot)) {
				return -1;
			}
		}
		return -2;
	}

	private void forget(long key) {
		work.remove(key);
		plans.remove(key);
	}

	private void tick(ServerLevel level) {
		if (zones.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		Politics p = Politics.get(level.getServer());
		for (int i = 0; i < zones.size(); i++) {
			Zone z = zones.get(i);
			BoundingBox box = z.box();
			if (!loaded(level, box)) {
				continue;
			}
			boolean creative = creativeNear(level, p, z);
			// 1.33: a town of a country at war rebuilds slowly, and only after things have been quiet for a few
			// minutes (an enemy's town the player is shelling no longer stands up again in seconds).
			Settlement owner = p.settlements.get(z.town);
			Country oc = owner == null ? null : p.country(owner.country);
			boolean war = !creative && oc != null && !oc.wars.isEmpty();
			if (now - z.since < (creative ? WAIT_CREATIVE : war ? WAIT_WAR : WAIT)) {
				continue;
			}
			List<long[]> todo = work.get(z.key());
			if (todo == null) {
				todo = plan(level, p, z);
				work.put(z.key(), todo);
			}
			int budget = creative ? RATE_CREATIVE : war ? RATE_WAR : RATE;
			int done = 0;
			Map<Long, BlockState> plan = plans.get(z.key());
			while (!todo.isEmpty() && done < budget) {
				long[] e = todo.removeLast();
				BlockPos pos = BlockPos.of(e[0]);
				BlockState want = plan.get(e[0]);
				if (want == null) {
					continue;
				}
				BlockState cur = level.getBlockState(pos);
				if (!needs(cur, want)) {
					continue;
				}
				level.setBlock(pos, want, Block.UPDATE_ALL);
				done++;
				rebuilt++;
				if (!creative && (done & 3) == 0) {
					level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
					level.playSound(null, pos, want.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 0.9f);
				}
			}
			if (todo.isEmpty()) {
				zones.remove(i--);
				forget(z.key());
				finished++;
				setDirty();
				Settlement s = p.settlements.get(z.town);
				AirDefense.LOGGER.info("[airdefense] rebuilt the damage at {} {} {}{}", z.x, z.y, z.z, s == null ? "" : " (" + s.name + ")");
			}
		}
	}

	/**
	 * What to put back: the town as the generator makes it (streets, ground, houses, lamps), and inside the buildings
	 * a town has put up itself, their own plans. Lowest first (what stands on something comes after it).
	 */
	private List<long[]> plan(ServerLevel level, Politics p, Zone z) {
		BoundingBox box = z.box();
		Map<Long, BlockState> plan = CityGen.intended(level, box);
		Settlement s = p.settlements.get(z.town);
		if (s != null) {
			Country c = p.country(s.country);
			DyeColor dye = c == null ? DyeColor.WHITE : c.dye();
			for (Building b : s.eco.buildings) {
				if (!b.done || b.type == BuildingType.ROADS) {
					continue;
				}
				BlockPos o = b.origin;
				int reach = Math.max(b.type.width, b.type.depth) + 4;
				if (o.getX() + reach < box.minX() || o.getX() - reach > box.maxX() || o.getZ() + reach < box.minZ() || o.getZ() - reach > box.maxZ()) {
					continue;
				}
				for (Blueprints.Placement pl : Blueprints.placements(b, dye)) {
					put(plan, box, pl.pos(), pl.state());
					if (pl.pair()) {
						put(plan, box, pl.pos2(), pl.state2());
					}
				}
			}
		}
		plans.put(z.key(), plan);
		List<long[]> todo = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : plan.entrySet()) {
			if (needs(level.getBlockState(BlockPos.of(e.getKey())), e.getValue())) {
				todo.add(new long[]{e.getKey(), BlockPos.getY(e.getKey())});
			}
		}
		// Taken from the end: highest first in the list, so the lowest come out first.
		todo.sort(Comparator.comparingLong((long[] a) -> -a[1]));
		return todo;
	}

	private static void put(Map<Long, BlockState> plan, BoundingBox box, BlockPos pos, BlockState state) {
		if (box.isInside(pos)) {
			plan.put(pos.asLong(), state);
		}
	}

	/**
	 * Whether a block is to be put back: where the war left air, fire, loose rubble or water running in, the planned
	 * block goes back; fire goes out; whatever else is there (a player's own blocks) stays.
	 */
	private static boolean needs(BlockState cur, BlockState want) {
		if (cur == want) {
			return false;
		}
		if (want.isAir()) {
			return cur.is(BlockTags.FIRE);
		}
		if (cur.getBlock() == want.getBlock()) {
			return false;
		}
		return cur.isAir() || cur.is(BlockTags.FIRE) || cur.canBeReplaced() || cur.is(Blocks.COARSE_DIRT) || cur.is(Blocks.ROOTED_DIRT)
				|| cur.is(Blocks.GRAVEL) && !want.is(Blocks.GRAVEL) || !cur.getFluidState().isEmpty() && !cur.getFluidState().isSource();
	}

	private static boolean loaded(ServerLevel level, BoundingBox box) {
		// (At a height inside the world: a patch near the bottom of it reaches below, where nothing counts as loaded.)
		int y = Math.max(level.getMinY(), Math.min(level.getMaxY(), box.minY()));
		return level.isLoaded(new BlockPos(box.minX(), y, box.minZ())) && level.isLoaded(new BlockPos(box.maxX(), y, box.maxZ()))
				&& level.isLoaded(new BlockPos(box.minX(), y, box.maxZ())) && level.isLoaded(new BlockPos(box.maxX(), y, box.minZ()));
	}

	/**
	 * A player in creative mode near by, and the damage is in one of his own towns (or out on a road, no town's): it all
	 * comes back at once. Other towns (the enemy's he is shelling, free villages) rebuild at their own pace (1.33).
	 */
	private static boolean creativeNear(ServerLevel level, Politics p, Zone z) {
		Settlement s = p.settlements.get(z.town);
		Country c = s == null ? null : p.country(s.country);
		for (ServerPlayer pl : level.players()) {
			if (pl.getAbilities().instabuild && !pl.isSpectator() && pl.distanceToSqr(z.x, z.y, z.z) < 220 * 220
					&& (s == null || c != null && c.isMember(pl.getUUID()))) {
				return true;
			}
		}
		return false;
	}

	/** For the tests: the patches within {@code r} of a point - where, how big, how long quiet, whose, loaded, work left. */
	public String describe(ServerLevel level, BlockPos at, int r) {
		StringBuilder b = new StringBuilder();
		long now = level.getGameTime();
		for (Zone z : zones) {
			if (Math.abs(z.x - at.getX()) <= r && Math.abs(z.z - at.getZ()) <= r) {
				List<long[]> todo = work.get(z.key());
				b.append(String.format(java.util.Locale.ROOT, "[%d %d %d r%d quiet %d town %d loaded %s todo %s] ", z.x - at.getX(), z.y, z.z - at.getZ(), z.r,
						now - z.since, z.town, loaded(level, z.box()), todo == null ? "-" : String.valueOf(todo.size())));
			}
		}
		return b.toString();
	}

	/** For the tests: patches still waiting or being rebuilt. */
	public int pending() {
		return zones.size();
	}

	private static final class Mth2 {
		static double sq(double v) {
			return v * v;
		}
	}
}
