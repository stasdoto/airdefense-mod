package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Trade (1.23). At a town's market the owner sells the town's goods for emeralds and buys what it lacks; prices
 * follow scarcity across the whole country (what is short is dear). And the towns of a country trade among
 * themselves on their own, slowly: a cart or a truck now and then takes a load of what one has plenty of to one that
 * is running out - too slowly to keep a town without a refinery or farms going, so real supply still needs trucks.
 */
public final class Market {
	/** What one deal moves of each kind: wood, stone, iron, oil, fuel, ammunition, food, weapons. */
	public static final int[] LOT = {20, 20, 20, 100, 100, 20, 20, 5};
	/** Emeralds a lot is worth when the country has about half of what it can hold. */
	public static final int[] BASE = {1, 1, 3, 2, 3, 3, 2, 5};
	/** Goods carried between towns on their own: one load per kind every few minutes. */
	public static final int[] CARAVAN = {0, 0, 20, 300, 300, 30, 40, 10};
	public static final int TRADE_EVERY = 3000;
	/** For the tests. */
	public static int deals;
	public static int caravans;

	private Market() {
	}

	/** How full the country's stores are of this kind (0..1); a town without a country by itself. */
	static double fill(Politics p, Settlement s, int kind) {
		long have = 0;
		long cap = 0;
		for (Settlement t : p.settlements.values()) {
			if (t == s || s.country >= 0 && t.country == s.country) {
				have += t.eco.stock[kind];
				cap += t.eco.capOf(kind);
			}
		}
		return cap <= 0 ? 0.5 : Math.max(0, Math.min(1, have / (double) cap));
	}

	/** Price of a lot in emeralds: {buy, sell}. */
	public static int[] price(Politics p, Settlement s, int kind) {
		double f = 0.5 + 1.5 * (1 - fill(p, s, kind));
		int buy = Math.max(1, (int) Math.ceil(BASE[kind] * f * 1.15));
		int sell = Math.max(1, (int) Math.floor(BASE[kind] * f * 0.8));
		return new int[]{buy, Math.min(sell, buy)};
	}

	/** Buy (or sell) one lot at the town's market. */
	public static boolean trade(ServerLevel level, ServerPlayer player, Settlement s, int kind, boolean buy) {
		Politics p = Politics.get(level.getServer());
		if (kind < 0 || kind >= VillageEconomy.KINDS) {
			return false;
		}
		if (!Economy.owner(p, s, player)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.not_yours"));
			return false;
		}
		if (s.eco.count(BuildingType.MARKET) == 0) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.market.none", s.name));
			return false;
		}
		VillageEconomy e = s.eco;
		int[] pr = price(p, s, kind);
		int lot = LOT[kind];
		Component what = Component.translatable("nation.airdefense.goods." + kind);
		if (buy) {
			if (e.stock[kind] + lot > e.capOf(kind)) {
				player.sendOverlayMessage(Component.translatable("nation.airdefense.market.full", what));
				return false;
			}
			if (!player.getAbilities().instabuild && !takeEmeralds(player, pr[0])) {
				player.sendOverlayMessage(Component.translatable("nation.airdefense.market.no_money", pr[0]));
				return false;
			}
			e.stock[kind] += lot;
			player.sendOverlayMessage(Component.translatable("nation.airdefense.market.bought", lot, what, pr[0]));
		} else {
			if (e.stock[kind] < lot) {
				player.sendOverlayMessage(Component.translatable("nation.airdefense.market.short", what));
				return false;
			}
			e.stock[kind] -= lot;
			ItemStack pay = new ItemStack(Items.EMERALD, pr[1]);
			if (!player.getInventory().add(pay)) {
				player.spawnAtLocation(level, pay);
			}
			player.sendOverlayMessage(Component.translatable("nation.airdefense.market.sold", lot, what, pr[1]));
		}
		deals++;
		p.setDirty();
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8f, 1f);
		return true;
	}

	private static boolean takeEmeralds(ServerPlayer player, int n) {
		var inv = player.getInventory();
		int have = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(Items.EMERALD)) {
				have += inv.getItem(i).getCount();
			}
		}
		if (have < n) {
			return false;
		}
		int left = n;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(Items.EMERALD)) {
				int k = Math.min(left, st.getCount());
				st.shrink(k);
				left -= k;
			}
		}
		return true;
	}

	/** How much of a kind a town wants to keep before it gives any away (food: a good while of eating). */
	private static int keep(Settlement s, int kind) {
		if (kind == VillageEconomy.FOOD) {
			return Math.max(30, Supply.foodNeed(s) * 20);
		}
		return s.eco.capOf(kind) / 2;
	}

	private static boolean short_(Settlement s, int kind) {
		if (kind == VillageEconomy.FOOD) {
			return Supply.foodNeed(s) > 0 && s.eco.stock[kind] < Supply.foodNeed(s) * 8;
		}
		return s.eco.stock[kind] < s.eco.capOf(kind) / 6;
	}

	/** Every few minutes: towns of a country send a load of what they have plenty of to those running short. */
	static void tick(ServerLevel level, Politics p) {
		if (level.getGameTime() % TRADE_EVERY == 1500) {
			tradeNow(p);
		}
	}

	/** One round of trade between the towns of every country. */
	public static void tradeNow(Politics p) {
		for (Country c : p.countries.values()) {
			List<Settlement> towns = new ArrayList<>();
			for (Settlement s : p.settlements.values()) {
				if (s.country == c.id) {
					towns.add(s);
				}
			}
			if (towns.size() < 2) {
				continue;
			}
			// A player's towns trade by themselves only between markets; the made-up countries always do.
			boolean player = c.owner != null;
			for (int kind = 0; kind < VillageEconomy.KINDS; kind++) {
				if (CARAVAN[kind] <= 0) {
					continue;
				}
				for (Settlement to : towns) {
					if (!short_(to, kind) || player && to.eco.count(BuildingType.MARKET) == 0) {
						continue;
					}
					Settlement from = null;
					int best = 0;
					for (Settlement f : towns) {
						if (f == to || player && f.eco.count(BuildingType.MARKET) == 0) {
							continue;
						}
						int spare = f.eco.stock[kind] - keep(f, kind);
						if (spare > best) {
							best = spare;
							from = f;
						}
					}
					if (from == null) {
						continue;
					}
					int load = Math.min(CARAVAN[kind], Math.min(best, to.eco.capOf(kind) - to.eco.stock[kind]));
					if (load <= 0) {
						continue;
					}
					from.eco.stock[kind] -= load;
					to.eco.stock[kind] += load;
					caravans++;
					p.setDirty();
				}
			}
		}
	}
}
