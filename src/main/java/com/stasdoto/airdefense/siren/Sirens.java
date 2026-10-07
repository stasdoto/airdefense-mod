package com.stasdoto.airdefense.siren;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.registry.ModBlocks;
import com.stasdoto.airdefense.nation.Cities;
import com.stasdoto.airdefense.nation.CityShape;
import com.stasdoto.airdefense.nation.Hamlets;
import com.stasdoto.airdefense.nation.Politics;
import com.stasdoto.airdefense.nation.Settlement;

/**
 * The air raid warning system (1.24), saved with the world: which towns are under an alert (switched on from the
 * tablet, or by itself when a raid comes or the air defence opens fire), the all clear after it, an alert for the
 * whole country, and where the sirens are. Each siren decides what to sound from this (see {@link #signalFor}).
 */
public final class Sirens extends SavedData {
	public static final int OFF = 0;
	public static final int ALERT = 1;
	public static final int CLEAR = 2;

	/** A siren's own setting: follow the town (AUTO), or always on, or switched off. */
	public static final int MODE_AUTO = 0;
	public static final int MODE_ON = 1;
	public static final int MODE_OFF = 2;

	/** How long the all clear (a steady tone) sounds. */
	public static final int CLEAR_TICKS = 20 * 20;
	/** An automatic alert lasts this long after the last reason for it (it is renewed while the threat is there). */
	public static final int AUTO_TICKS = 20 * 120;

	private static final Codec<long[]> PAIR = Codec.LONG.listOf().xmap(l -> new long[]{l.get(0), l.get(1)}, a -> List.of(a[0], a[1]));
	public static final Codec<Sirens> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("everywhere", false).forGetter(s -> s.everywhere),
			Codec.LONG.optionalFieldOf("clear_everywhere", 0L).forGetter(s -> s.clearEverywhereUntil),
			Codec.INT.listOf().optionalFieldOf("alert", List.of()).forGetter(s -> new ArrayList<>(s.alert)),
			PAIR.listOf().optionalFieldOf("auto", List.of()).forGetter(s -> pairs(s.autoUntil)),
			PAIR.listOf().optionalFieldOf("clear", List.of()).forGetter(s -> pairs(s.clearUntil)),
			Codec.LONG.listOf().optionalFieldOf("sirens", List.of()).forGetter(s -> new ArrayList<>(s.known)),
			PAIR.listOf().optionalFieldOf("pending", List.of()).forGetter(s -> pairsL(s.pending))
	).apply(i, Sirens::new));
	public static final SavedDataType<Sirens> TYPE = new SavedDataType<>(AirDefense.id("sirens"), Sirens::new, CODEC, null);

	/** Alert in every town at once. */
	public boolean everywhere;
	public long clearEverywhereUntil;
	/** Towns (settlement ids) on alert from the tablet. */
	public final Set<Integer> alert = new HashSet<>();
	/** Towns on automatic alert, until this game time. */
	public final Map<Integer, Long> autoUntil = new HashMap<>();
	/** Towns sounding the all clear, until this game time. */
	public final Map<Integer, Long> clearUntil = new HashMap<>();
	/** Every siren placed or generated (block positions). */
	public final Set<Long> known = new HashSet<>();
	/**
	 * Sirens planned for the towns and not yet put up (their chunk was not loaded): position (with the ground height
	 * expected) and how to put it up ({@link #placeAt}).
	 */
	public final Map<Long, Long> pending = new HashMap<>();
	/** For the tests: automatic alerts raised, sirens put up in the towns. */
	public static int autoAlerts;
	public static int townSirensPlaced;

	public Sirens() {
		this(false, 0L, List.of(), List.of(), List.of(), List.of(), List.of());
	}

	private Sirens(boolean everywhere, long clearEverywhere, List<Integer> alert, List<long[]> auto, List<long[]> clear, List<Long> known,
			List<long[]> pending) {
		this.everywhere = everywhere;
		this.clearEverywhereUntil = clearEverywhere;
		this.alert.addAll(alert);
		for (long[] p : auto) {
			autoUntil.put((int) p[0], p[1]);
		}
		for (long[] p : clear) {
			clearUntil.put((int) p[0], p[1]);
		}
		this.known.addAll(known);
		for (long[] p : pending) {
			this.pending.put(p[0], p[1]);
		}
	}

	private static List<long[]> pairsL(Map<Long, Long> m) {
		List<long[]> out = new ArrayList<>();
		m.forEach((k, v) -> out.add(new long[]{k, v}));
		return out;
	}

	private static List<long[]> pairs(Map<Integer, Long> m) {
		List<long[]> out = new ArrayList<>();
		m.forEach((k, v) -> out.add(new long[]{k, v}));
		return out;
	}

	public static Sirens get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The town a siren belongs to (the settlement whose area it stands in), or -1. */
	public static int townOf(ServerLevel level, BlockPos pos) {
		Settlement s = Politics.get(level.getServer()).settlementAt(pos);
		return s == null ? -1 : s.id;
	}

	/** What a siren at a place in this town should be sounding, given its own setting. */
	public int signalFor(long now, int town, int mode) {
		if (mode == MODE_OFF) {
			return OFF;
		}
		if (mode == MODE_ON || everywhere || town >= 0 && (alert.contains(town) || autoUntil.getOrDefault(town, 0L) > now)) {
			return ALERT;
		}
		if (clearEverywhereUntil > now || town >= 0 && clearUntil.getOrDefault(town, 0L) > now) {
			return CLEAR;
		}
		return OFF;
	}

	/** What a town is doing right now (for the tablet). */
	public int townSignal(long now, int town) {
		return signalFor(now, town, MODE_AUTO);
	}

	public void register(BlockPos pos) {
		if (known.add(pos.asLong())) {
			setDirty();
		}
	}

	public void forget(BlockPos pos) {
		if (known.remove(pos.asLong())) {
			setDirty();
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Switching (from the tablet)

	public void alertEverywhere(boolean on, long now) {
		everywhere = on;
		if (!on) {
			// Stand down: every town that was warned hears the all clear.
			for (int t : alert) {
				clearUntil.put(t, now + CLEAR_TICKS);
			}
			for (int t : autoUntil.keySet()) {
				clearUntil.put(t, now + CLEAR_TICKS);
			}
			alert.clear();
			autoUntil.clear();
			clearEverywhereUntil = now + CLEAR_TICKS;
		} else {
			clearEverywhereUntil = 0;
			clearUntil.clear();
		}
		setDirty();
	}

	/** Everything quiet at once (no all clear tone). */
	public void silence() {
		everywhere = false;
		clearEverywhereUntil = 0;
		alert.clear();
		autoUntil.clear();
		clearUntil.clear();
		setDirty();
	}

	public void alertTown(int town, boolean on, long now) {
		if (on) {
			alert.add(town);
			clearUntil.remove(town);
		} else {
			boolean was = alert.remove(town) | autoUntil.remove(town) != null;
			if (was) {
				clearUntil.put(town, now + CLEAR_TICKS);
			}
		}
		setDirty();
	}

	// ------------------------------------------------------------------------------------------------
	// Automatic alerts: a raid seen by the radars, the air defence opening fire

	/** Every town within {@code radius} of {@code at} goes on alert (or stays on it a while longer). */
	public static void autoAlert(ServerLevel level, Vec3 at, double radius) {
		Sirens s = get(level.getServer());
		long now = level.getGameTime();
		boolean any = false;
		for (Settlement t : Politics.get(level.getServer()).near(BlockPos.containing(at), radius)) {
			Long until = s.autoUntil.get(t.id);
			if (until == null || until <= now) {
				autoAlerts++;
				AirDefense.LOGGER.info("[airdefense] air raid alert in {}", t.name);
			}
			s.autoUntil.put(t.id, now + AUTO_TICKS);
			s.clearUntil.remove(t.id);
			any = true;
		}
		if (any) {
			s.setDirty();
		}
	}

	/** Called every second: automatic alerts that ran out turn into the all clear; planned sirens are put up. */
	public void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (!pending.isEmpty()) {
			placePending(level);
		}
		boolean changed = false;
		var it = autoUntil.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			if (e.getValue() <= now) {
				it.remove();
				if (!alert.contains(e.getKey())) {
					clearUntil.put(e.getKey(), now + CLEAR_TICKS);
				}
				changed = true;
			}
		}
		changed |= clearUntil.values().removeIf(v -> v < now - 20);
		if (changed) {
			setDirty();
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Sirens in the towns: planned when a town is found, put up once their place is loaded

	/** How a planned siren goes up: on the pavement line (tries along it), or anywhere near a village's centre. */
	private static final int ON_PAVEMENT = 0;
	private static final int NEAR_CENTRE = 1;

	private void plan(int x, int y, int z, Direction facing, int how) {
		long key = BlockPos.asLong(x, y, z);
		if (!known.contains(key)) {
			pending.put(key, (long) facing.get2DDataValue() | (long) how << 8);
			setDirty();
		}
	}

	/**
	 * A city's sirens: one on the central square by the city hall and more on the pavements, spread over the city so
	 * every street hears one (2 in a small city, 4 in a middle one, 6 in a large one).
	 */
	public static void planCity(ServerLevel level, Cities.City c) {
		Sirens s = get(level.getServer());
		CityShape sh = c.shape();
		int count = switch (c.size) {
			case SMALL -> 2;
			case MEDIUM -> 4;
			case LARGE -> 6;
		};
		// The candidates: the middle of every side of every lot, on the pavement (the line of the street lamps).
		List<int[]> cand = new ArrayList<>();
		for (CityShape.Lot l : sh.lots) {
			int ax = l.cx() + 2;
			int az = l.cz() + 2;
			cand.add(new int[]{l.x0 - 1, az, Direction.WEST.get2DDataValue()});
			cand.add(new int[]{l.x1 + 1, az, Direction.EAST.get2DDataValue()});
			cand.add(new int[]{ax, l.z0 - 1, Direction.NORTH.get2DDataValue()});
			cand.add(new int[]{ax, l.z1 + 1, Direction.SOUTH.get2DDataValue()});
		}
		CityShape.Lot hall = sh.hallLot();
		List<int[]> chosen = new ArrayList<>();
		// First: the main siren on the square in front of the city hall.
		chosen.add(new int[]{hall.cx() + 2, hall.z1 + 1, Direction.SOUTH.get2DDataValue()});
		double reach = c.radius() * 0.72;
		while (chosen.size() < count) {
			int[] best = null;
			double bestD = 0;
			for (int[] k : cand) {
				if (Math.hypot(k[0] - c.x, k[1] - c.z) > reach) {
					continue;
				}
				double d = Double.MAX_VALUE;
				for (int[] o : chosen) {
					d = Math.min(d, Math.hypot(k[0] - o[0], k[1] - o[1]));
				}
				if (d > bestD) {
					bestD = d;
					best = k;
				}
			}
			if (best == null || bestD < 40) {
				break;
			}
			chosen.add(best);
		}
		for (int[] k : chosen) {
			s.plan(k[0], c.base + 1, k[1], Direction.from2DDataValue(k[2]), ON_PAVEMENT);
		}
	}

	/** A hamlet's siren: on a pole next to the well and the bell. */
	public static void planHamlet(ServerLevel level, Hamlets.Hamlet h) {
		get(level.getServer()).plan(h.x - 3, h.base + 1, h.z + 3, Direction.SOUTH, NEAR_CENTRE);
	}

	/** A village's siren: near its bell. */
	public static void planVillage(ServerLevel level, BlockPos center) {
		get(level.getServer()).plan(center.getX() + 3, center.getY(), center.getZ() + 2, Direction.SOUTH, NEAR_CENTRE);
	}

	/** Puts up the planned sirens whose place is loaded (a few each second). */
	private void placePending(ServerLevel level) {
		int budget = 4;
		var it = pending.entrySet().iterator();
		while (it.hasNext() && budget > 0) {
			var e = it.next();
			BlockPos p = BlockPos.of(e.getKey());
			if (!level.isLoaded(p)) {
				continue;
			}
			budget--;
			it.remove();
			setDirty();
			Direction facing = Direction.from2DDataValue((int) (e.getValue() & 0xFF));
			int how = (int) (e.getValue() >> 8);
			BlockPos at = placeAt(level, p, facing, how);
			if (at != null) {
				// The siren six metres up on its mast, like the ones in real towns.
				BlockPos head = at.above(SirenItem.MAST);
				SirenItem.buildMast(level, head, facing);
				level.setBlock(head, ModBlocks.SIREN.defaultBlockState().setValue(SirenBlock.FACING, facing), Block.UPDATE_ALL);
				register(head);
				at = head;
				townSirensPlaced++;
				AirDefense.LOGGER.info("[airdefense] siren put up at {}", at.toShortString());
			}
		}
	}

	/** Where exactly a planned siren can stand: free ground near the planned place, or null. */
	@Nullable
	private static BlockPos placeAt(ServerLevel level, BlockPos p, Direction facing, int how) {
		if (how == ON_PAVEMENT) {
			Direction along = facing.getClockWise();
			for (int k : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
				BlockPos q = fit(level, p.relative(along, k));
				if (q != null) {
					return q;
				}
			}
			return null;
		}
		for (int r = 0; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos q = fit(level, p.offset(dx, 0, dz));
					if (q != null) {
						return q;
					}
				}
			}
		}
		return null;
	}

	/** The place in this column (at the expected height or on the surface) where a siren and its mast fit: firm ground, free above. */
	@Nullable
	private static BlockPos fit(ServerLevel level, BlockPos p) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
		for (int y : new int[]{p.getY(), top}) {
			BlockPos q = new BlockPos(p.getX(), y, p.getZ());
			if (Math.abs(y - p.getY()) <= 3 && firm(level, q.below()) && clear(level, q, SirenItem.MAST + 2)) {
				return q;
			}
		}
		return null;
	}

	private static boolean clear(ServerLevel level, BlockPos q, int height) {
		for (int i = 0; i < height; i++) {
			if (!free(level, q.above(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean free(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty();
	}

	private static boolean firm(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return !s.isAir() && s.getFluidState().isEmpty() && !s.is(BlockTags.LEAVES) && !s.getCollisionShape(level, p).isEmpty()
				&& !s.is(ModBlocks.SIREN) && !s.is(ModBlocks.SIREN_MAST);
	}

	@Nullable
	public static Settlement town(ServerLevel level, int id) {
		return Politics.get(level.getServer()).settlements.get(id);
	}
}
