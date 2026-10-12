package com.stasdoto.airdefense.nation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.50 "A living town": the building sites really build. While a player is about, each tall building going up near him
 * rises a storey's height every few minutes (the shell poured, the rods sticking out of the new top, the scaffolding up
 * with it); when the shell reaches the top the building is finished - windows, doors, balconies, the flats inside - and
 * the crane is taken away. How high each site has got is kept with the world (worlds started since 1.49).
 */
public final class Growth {
	/** A site's key (city key * 4096 + building id) -> the height its shell has reached (absolute y); FINISHED once done. */
	private static final Map<Long, Integer> CUTS = new ConcurrentHashMap<>();
	public static final int FINISHED = Integer.MAX_VALUE;
	/** Blocks still to be put in place (a finishing building, a little at a time). */
	private static final ArrayDeque<Object[]> PENDING = new ArrayDeque<>();
	/** Panes, fences, walls and stairs put in: their shapes fixed once everything round them is in. */
	private static final ArrayDeque<BlockPos> SHAPES = new ArrayDeque<>();
	/** How often a site near a player grows (ticks), and how many blocks a tick go in when one is finished. */
	public static int every = 600;
	private static final int PER_TICK = 1500;
	/** For the tests: storeys grown, buildings finished. */
	public static int grown;
	public static int finished;

	private Growth() {
	}

	static final class Data extends SavedData {
		static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.listOf().optionalFieldOf("cuts", List.of()).forGetter(d -> {
					List<Long> out = new ArrayList<>();
					d.cuts.forEach((k, v) -> {
						out.add(k);
						out.add((long) v);
					});
					return out;
				})
		).apply(i, list -> {
			Data d = new Data();
			for (int k = 0; k + 1 < list.size(); k += 2) {
				d.cuts.put(list.get(k), (int) (long) list.get(k + 1));
			}
			return d;
		}));
		static final SavedDataType<Data> TYPE = new SavedDataType<>(AirDefense.id("town_growth"), Data::new, CODEC, null);
		final Map<Long, Integer> cuts = new HashMap<>();

		static Data get(MinecraftServer server) {
			return server.getDataStorage().computeIfAbsent(TYPE);
		}
	}

	public static void init() {
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				CUTS.clear();
				PENDING.clear();
				SHAPES.clear();
				try {
					CUTS.putAll(Data.get(server).cuts);
				} catch (RuntimeException e) {
					// A fresh start.
				}
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD) {
				return;
			}
			for (int k = 0; k < PER_TICK && !PENDING.isEmpty(); k++) {
				Object[] e = PENDING.poll();
				BlockPos p = (BlockPos) e[0];
				if (level.isLoaded(p)) {
					BlockState s = (BlockState) e[1];
					level.setBlock(p, s, Block.UPDATE_CLIENTS);
					Block b = s.getBlock();
					if (b instanceof net.minecraft.world.level.block.CrossCollisionBlock || b instanceof net.minecraft.world.level.block.WallBlock
							|| b instanceof net.minecraft.world.level.block.StairBlock || b instanceof net.minecraft.world.level.block.FenceGateBlock) {
						SHAPES.add(p);
					}
				}
			}
			for (int k = 0; k < PER_TICK && PENDING.isEmpty() && !SHAPES.isEmpty(); k++) {
				BlockPos p = SHAPES.poll();
				if (level.isLoaded(p)) {
					BlockState s = level.getBlockState(p);
					BlockState u = Block.updateFromNeighbourShapes(s, level, p);
					if (u != s && !u.isAir()) {
						level.setBlock(p, u, Block.UPDATE_CLIENTS);
					}
				}
			}
			if (level.getGameTime() % every == 0 && Cities.growth && Cities.weathered) {
				grow(level);
			}
		});
	}

	static boolean finished(long key) {
		Integer c = CUTS.get(key);
		return c != null && c == FINISHED;
	}

	/** How high the site's shell stands now (MIN_VALUE: as when the town was built). */
	static int cut(long key) {
		Integer c = CUTS.get(key);
		return c == null ? Integer.MIN_VALUE : c;
	}

	/** One step for every site near a player: a storey up, or finished. */
	public static void grow(ServerLevel level) {
		Cities.Terrain t = Cities.terrain(level);
		long seed = level.getSeed();
		java.util.Set<Long> done = new java.util.HashSet<>();
		for (ServerPlayer player : level.players()) {
			for (Cities.City c : Cities.citiesAround(seed, t, player.getBlockX(), player.getBlockZ())) {
				if (c.outside(player.getBlockX(), player.getBlockZ()) > 120) {
					continue;
				}
				for (Building b : c.buildings()) {
					long key = c.key() * 4096 + b.id;
					if (!done.add(key) || !Construction.site(c, b) || finished(key) || b.origin.distSqr(player.blockPosition()) > 160 * 160
							|| !level.isLoaded(b.origin)) {
						continue;
					}
					step(level, c, b, key);
				}
			}
		}
	}

	private static void step(ServerLevel level, Cities.City c, Building b, long key) {
		List<Blueprints.Placement> full = Blueprints.placements(b, DyeColor.byId(c.color));
		int top = Construction.top(b, full);
		int cur = CUTS.containsKey(key) ? CUTS.get(key) : Construction.firstCut(b, full);
		Map<Long, BlockState> before = map(Construction.shell(b, full, cur));
		Map<Long, BlockState> after;
		int next = cur + 1;
		if (next > top) {
			after = map(full);
			// The scaffolding and the crane's mark go.
			for (long k : before.keySet()) {
				after.putIfAbsent(k, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			}
			CUTS.put(key, FINISHED);
			finished++;
		} else {
			after = map(Construction.shell(b, full, next));
			CUTS.put(key, next);
			grown++;
		}
		for (Map.Entry<Long, BlockState> e : after.entrySet()) {
			BlockState was = before.get(e.getKey());
			if (was != e.getValue()) {
				BlockState s = CityGen.window(e.getValue(), BlockPos.of(e.getKey()));
				PENDING.add(new Object[]{BlockPos.of(e.getKey()), s});
			}
		}
		CityGen.forgetPlan(key);
		Data d = Data.get(level.getServer());
		d.cuts.put(key, CUTS.get(key));
		d.setDirty();
	}

	private static Map<Long, BlockState> map(List<Blueprints.Placement> list) {
		Map<Long, BlockState> m = new HashMap<>(list.size() * 2);
		for (Blueprints.Placement p : list) {
			m.put(p.pos().asLong(), p.state());
			if (p.pair()) {
				m.put(p.pos2().asLong(), p.state2());
			}
		}
		return m;
	}
}
