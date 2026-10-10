package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.42: the world's rulers and their dealings. Every country of the world has a ruler - a title, a name, a number, a
 * character (peaceful, cautious, greedy, warlike) - who now and then gives way to another. Between every two countries
 * there is a standing (-100..100) that drifts back to what their rulers and their dealings make it, and may be a treaty
 * of trade and an alliance. The warlike fall out more often, friends seldom, allies never; an ally joins its ally's war.
 * The player can send a ruler gifts, offer trade (emeralds every day) and an alliance (allies go to war against whoever
 * falls upon him). Kept in its own saved data next to the politics.
 */
public final class Diplomacy extends SavedData {
	public static final int PEACEFUL = 0;
	public static final int CAUTIOUS = 1;
	public static final int GREEDY = 2;
	public static final int WARLIKE = 3;
	public static final int TITLES = 7;
	public static final int NAMES = 32;
	public static final int TRADE = 1;
	public static final int ALLY = 2;
	public static final int GIFT_PRICE = 8;

	/** A ruler: title, name, number (the Second...), character, the day his rule began. */
	public record Ruler(int title, int name, int number, int trait, long since) {
	}

	private static final Codec<Diplomacy> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.listOf().optionalFieldOf("rulers", List.of()).forGetter(d -> {
				List<Long> out = new ArrayList<>();
				d.rulers.forEach((k, r) -> {
					out.add((long) k);
					out.add((long) r.title);
					out.add((long) r.name);
					out.add((long) r.number);
					out.add((long) r.trait);
					out.add(r.since);
				});
				return out;
			}),
			Codec.LONG.listOf().optionalFieldOf("relations", List.of()).forGetter(d -> {
				List<Long> out = new ArrayList<>();
				d.relations.forEach((k, v) -> {
					out.add(k);
					out.add((long) v[0]);
					out.add((long) v[1]);
				});
				return out;
			}),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(d -> d.day)
	).apply(i, (rulers, relations, day) -> {
		Diplomacy d = new Diplomacy();
		d.day = day;
		for (int k = 0; k + 5 < rulers.size(); k += 6) {
			d.rulers.put((int) (long) rulers.get(k), new Ruler((int) (long) rulers.get(k + 1), (int) (long) rulers.get(k + 2), (int) (long) rulers.get(k + 3),
					(int) (long) rulers.get(k + 4), rulers.get(k + 5)));
		}
		for (int k = 0; k + 2 < relations.size(); k += 3) {
			d.relations.put(relations.get(k), new int[]{(int) (long) relations.get(k + 1), (int) (long) relations.get(k + 2)});
		}
		return d;
	}));
	public static final SavedDataType<Diplomacy> TYPE = new SavedDataType<>(AirDefense.id("diplomacy"), Diplomacy::new, CODEC, null);

	private final Map<Integer, Ruler> rulers = new HashMap<>();
	/** Two countries (the smaller id first) -> {standing, treaties}. */
	private final Map<Long, int[]> relations = new HashMap<>();
	/** Days gone by (counted by the daily round). */
	private long day;
	/** For the tests: rulers who gave way, alliances made, offers accepted. */
	public static int successions;
	public static int alliances;
	public static int accepted;

	public Diplomacy() {
	}

	public static Diplomacy get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	private static long pair(int a, int b) {
		return a < b ? ((long) a << 32) | (b & 0xffffffffL) : ((long) b << 32) | (a & 0xffffffffL);
	}

	// ------------------------------------------------------------------------------------------------
	// Rulers

	/** The ruler of a world's country (one is chosen if it has none yet); null for a player's own. */
	public Ruler ruler(ServerLevel level, Country c) {
		if (c == null || c.owner != null) {
			return null;
		}
		Ruler r = rulers.get(c.id);
		if (r == null) {
			Random rnd = new Random(level.getSeed() ^ c.id * 0x9E3779B97F4A7C15L);
			int title = c.cityState ? 2 : rnd.nextInt(TITLES);
			r = new Ruler(title, rnd.nextInt(NAMES), 1 + rnd.nextInt(4), rnd.nextInt(4), day);
			rulers.put(c.id, r);
			setDirty();
		}
		return r;
	}

	/** A ruler's whole style ("King Henry III"), in the reader's own language. */
	public static Component name(Ruler r) {
		if (r == null) {
			return Component.empty();
		}
		return Component.translatable("ruler.airdefense.full", Component.translatable(titleKey(r.title, r.name)),
				Component.translatable("ruler.airdefense.name." + r.name), roman(r.number));
	}

	/** The names 10..16 are women's: their titles are the queens' and princesses'. */
	public static String titleKey(int title, int name) {
		return name >= 10 && name <= 16 ? "ruler.airdefense.title_f." + title : "ruler.airdefense.title." + title;
	}

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			default -> String.valueOf(n);
		};
	}

	// ------------------------------------------------------------------------------------------------
	// Standing and treaties

	public int standing(int a, int b) {
		int[] v = relations.get(pair(a, b));
		return v == null ? 0 : v[0];
	}

	public int treaties(int a, int b) {
		int[] v = relations.get(pair(a, b));
		return v == null ? 0 : v[1];
	}

	public boolean allied(int a, int b) {
		return (treaties(a, b) & ALLY) != 0;
	}

	private int[] entry(int a, int b) {
		return relations.computeIfAbsent(pair(a, b), k -> new int[2]);
	}

	public void change(int a, int b, int delta) {
		int[] v = entry(a, b);
		v[0] = Math.max(-100, Math.min(100, v[0] + delta));
		setDirty();
	}

	private void treaty(int a, int b, int flag, boolean on) {
		int[] v = entry(a, b);
		v[1] = on ? v[1] | flag : v[1] & ~flag;
		setDirty();
	}

	/** What the standing between them drifts back to: their rulers' characters, their treaties, a war between them. */
	private int base(Politics p, ServerLevel level, Country a, Country b) {
		// The world's own countries like or dislike each other of old (the same for every two, from the seed).
		int s = a.owner == null && b.owner == null ? (int) Math.floorMod(level.getSeed() ^ pair(a.id, b.id) * 0x9E3779B97F4A7C15L, 101L) - 40 : 0;
		for (Country c : new Country[]{a, b}) {
			Ruler r = ruler(level, c);
			if (r != null) {
				s += r.trait == PEACEFUL ? 10 : r.trait == WARLIKE ? -15 : 0;
			}
		}
		int t = treaties(a.id, b.id);
		if ((t & ALLY) != 0) {
			s += 40;
		}
		if ((t & TRADE) != 0) {
			s += 15;
		}
		if (a.atWarWith(b.id)) {
			s -= 80;
		}
		return s;
	}

	// ------------------------------------------------------------------------------------------------
	// For the wars

	/** How much likelier than usual two of the world's countries are to fall out (0: never - they are allies). */
	public double quarrel(ServerLevel level, Country a, Country b) {
		if (allied(a.id, b.id)) {
			return 0;
		}
		double f = 1;
		for (Country c : new Country[]{a, b}) {
			Ruler r = ruler(level, c);
			if (r != null) {
				f *= switch (r.trait) {
					case PEACEFUL -> 0.4;
					case CAUTIOUS -> 0.7;
					case GREEDY -> 1.2;
					default -> 2.2;
				};
			}
		}
		int s = standing(a.id, b.id);
		if (s < -30) {
			f *= 2;
		} else if (s > 30) {
			f *= 0.3;
		}
		if ((treaties(a.id, b.id) & TRADE) != 0) {
			f *= 0.5;
		}
		return f;
	}

	/** A war began: the standing falls; the defender's allies of the world join it against the attacker. */
	public static void warBegan(ServerLevel level, Politics p, Country attacker, Country defender) {
		Diplomacy d = get(level.getServer());
		d.change(attacker.id, defender.id, -50);
		for (Country c : new ArrayList<>(p.countries.values())) {
			if (c.id == attacker.id || c.id == defender.id || c.owner != null || !d.allied(c.id, defender.id) || d.allied(c.id, attacker.id)
					|| c.atWarWith(attacker.id)) {
				continue;
			}
			War.declare(level, p, c, attacker, Component.translatable("nation.airdefense.war.why_ally", defender.name));
		}
	}

	public static void peaceMade(ServerLevel level, Country a, Country b) {
		Diplomacy d = get(level.getServer());
		int s = d.standing(a.id, b.id);
		if (s < -20) {
			d.change(a.id, b.id, -20 - s);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Once a day

	public static void daily(ServerLevel level, Politics p) {
		Diplomacy d = get(level.getServer());
		Random r = new Random();
		long day = ++d.day;
		d.setDirty();
		List<Country> all = new ArrayList<>();
		for (Country c : p.countries.values()) {
			if (c.cityState || p.settlements.get(c.capital) == null && c.owner == null) {
				continue;
			}
			all.add(c);
		}
		for (Country a : all) {
			// A ruler gives way now and then (old age, an election, a coup).
			Ruler ru = d.ruler(level, a);
			if (ru != null && day - ru.since > 4 && r.nextInt(100) < 3) {
				Ruler next = new Ruler(ru.title, r.nextInt(NAMES), ru.number + 1 > 6 ? 1 : ru.number + 1, r.nextInt(4), day);
				d.rulers.put(a.id, next);
				d.setDirty();
				successions++;
				news(level, Component.translatable("nation.airdefense.diplomacy.new_ruler", a.name, name(next),
						Component.translatable("ruler.airdefense.trait." + next.trait)));
			}
			for (Country b : all) {
				if (a.id >= b.id) {
					continue;
				}
				int s = d.standing(a.id, b.id);
				int target = d.base(p, level, a, b);
				int step = Integer.signum(target - s) * Math.min(Math.abs(target - s), 4);
				// The world's own countries' standing wanders a little besides.
				if (a.owner == null && b.owner == null) {
					step += r.nextInt(9) - 4;
				}
				if (step != 0) {
					d.change(a.id, b.id, step);
				}
				s = d.standing(a.id, b.id);
				boolean ally = d.allied(a.id, b.id);
				if (a.owner == null && b.owner == null && !ally && s >= 55 && !a.atWarWith(b.id) && r.nextInt(100) < 15) {
					d.treaty(a.id, b.id, ALLY, true);
					alliances++;
					news(level, Component.translatable("nation.airdefense.diplomacy.alliance", a.name, b.name));
				} else if (ally && s < 0) {
					d.treaty(a.id, b.id, ALLY, false);
					news(level, Component.translatable("nation.airdefense.diplomacy.alliance_broken", a.name, b.name));
				}
				if (a.owner == null && b.owner == null && (d.treaties(a.id, b.id) & TRADE) == 0 && s >= 25 && r.nextInt(100) < 10) {
					d.treaty(a.id, b.id, TRADE, true);
				}
			}
		}
		// Trade with the player: emeralds every day (more, the better they stand).
		for (ServerPlayer pl : level.getServer().getPlayerList().getPlayers()) {
			Country mine = p.countryOwnedBy(pl.getUUID());
			if (mine == null) {
				continue;
			}
			for (Country c : all) {
				if (c.owner == null && (d.treaties(mine.id, c.id) & TRADE) != 0 && !mine.atWarWith(c.id)) {
					int n = 3 + Math.max(0, d.standing(mine.id, c.id)) / 20;
					ItemStack stack = new ItemStack(Items.EMERALD, n);
					if (!pl.getInventory().add(stack) && !stack.isEmpty()) {
						pl.spawnAtLocation(level, stack);
					}
					pl.sendSystemMessage(Component.translatable("nation.airdefense.diplomacy.trade_income", c.name, n));
				}
			}
		}
	}

	private static void news(ServerLevel level, Component message) {
		for (ServerPlayer pl : level.getServer().getPlayerList().getPlayers()) {
			pl.sendSystemMessage(message);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The player's offers

	private static Country them(ServerLevel level, Settlement s) {
		Country c = Politics.get(level.getServer()).country(s.country);
		return c == null || c.owner != null || c.cityState ? null : c;
	}

	/** A gift to the ruler of the country this town belongs to: their standing with the player's country rises. */
	public static boolean gift(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		Country them = them(level, s);
		Country mine = p.countryOwnedBy(player.getUUID());
		if (them == null || mine == null) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.war.no_country"));
			return false;
		}
		if (!player.getAbilities().instabuild && !pay(player, GIFT_PRICE)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.need_emeralds", GIFT_PRICE));
			return false;
		}
		Diplomacy d = get(level.getServer());
		Ruler r = d.ruler(level, them);
		int gain = r != null && r.trait == GREEDY ? 22 : 14;
		d.change(mine.id, them.id, gain);
		player.sendSystemMessage(Component.translatable("nation.airdefense.diplomacy.gift", name(r), them.name, d.standing(mine.id, them.id)));
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		return true;
	}

	/** A trade treaty: agreed when they stand well enough with the player (a greedy ruler sooner). */
	public static boolean offerTrade(ServerLevel level, ServerPlayer player, Settlement s) {
		return offer(level, player, s, TRADE);
	}

	/** An alliance: agreed when they stand well with the player (a warlike or cautious ruler wants more). */
	public static boolean offerAlliance(ServerLevel level, ServerPlayer player, Settlement s) {
		return offer(level, player, s, ALLY);
	}

	private static boolean offer(ServerLevel level, ServerPlayer player, Settlement s, int what) {
		Politics p = Politics.get(level.getServer());
		Country them = them(level, s);
		Country mine = p.countryOwnedBy(player.getUUID());
		if (them == null || mine == null || p.settlementsOf(mine.id).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.war.no_country"));
			return false;
		}
		Diplomacy d = get(level.getServer());
		Ruler r = d.ruler(level, them);
		String key = what == ALLY ? "alliance" : "trade";
		if (mine.atWarWith(them.id)) {
			player.sendSystemMessage(Component.translatable("nation.airdefense.diplomacy.at_war", them.name));
			return false;
		}
		if ((d.treaties(mine.id, them.id) & what) != 0) {
			player.sendSystemMessage(Component.translatable("nation.airdefense.diplomacy.already_" + key, them.name));
			return false;
		}
		int need = what == ALLY ? 40 : 10;
		if (r != null) {
			if (what == ALLY) {
				need += r.trait == WARLIKE ? 15 : r.trait == CAUTIOUS ? 10 : r.trait == PEACEFUL ? -5 : 0;
			} else {
				need += r.trait == GREEDY ? -15 : r.trait == CAUTIOUS ? 5 : 0;
			}
		}
		// An alliance with the player is not made with an enemy's ally's enemy... nor against an ally of theirs.
		if (what == ALLY) {
			for (int enemy : mine.wars) {
				if (d.allied(them.id, enemy)) {
					player.sendSystemMessage(Component.translatable("nation.airdefense.diplomacy.refused_ally_of_enemy", name(r), them.name));
					return false;
				}
			}
		}
		int s0 = d.standing(mine.id, them.id);
		if (s0 < need) {
			player.sendSystemMessage(Component.translatable("nation.airdefense.diplomacy.refused_" + key, name(r), them.name, s0, need));
			d.change(mine.id, them.id, -2);
			return false;
		}
		d.treaty(mine.id, them.id, what, true);
		d.change(mine.id, them.id, 5);
		accepted++;
		player.sendSystemMessage(Component.translatable("nation.airdefense.diplomacy.agreed_" + key, name(r), them.name));
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		return true;
	}

	/** The player declared war on a country: his own allies of the world join him. */
	public static void playerWar(ServerLevel level, Politics p, Country mine, Country target) {
		Diplomacy d = get(level.getServer());
		for (Country c : new ArrayList<>(p.countries.values())) {
			if (c.owner == null && c.id != target.id && d.allied(c.id, mine.id) && !d.allied(c.id, target.id) && !c.atWarWith(target.id)) {
				War.declare(level, p, c, target, Component.translatable("nation.airdefense.war.why_ally", mine.name));
			}
		}
	}

	private static boolean pay(ServerPlayer player, int price) {
		if (player.getInventory().countItem(Items.EMERALD) < price) {
			return false;
		}
		int left = price;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			var stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				int k = Math.min(left, stack.getCount());
				stack.shrink(k);
				left -= k;
			}
		}
		return true;
	}
}
