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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
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
			Codec.LONG.listOf().optionalFieldOf("sirens", List.of()).forGetter(s -> new ArrayList<>(s.known))
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
	/** For the tests: automatic alerts raised. */
	public static int autoAlerts;

	public Sirens() {
		this(false, 0L, List.of(), List.of(), List.of(), List.of());
	}

	private Sirens(boolean everywhere, long clearEverywhere, List<Integer> alert, List<long[]> auto, List<long[]> clear, List<Long> known) {
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

	/** Called every second: automatic alerts that ran out turn into the all clear. */
	public void tick(long now) {
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

	@Nullable
	public static Settlement town(ServerLevel level, int id) {
		return Politics.get(level.getServer()).settlements.get(id);
	}
}
