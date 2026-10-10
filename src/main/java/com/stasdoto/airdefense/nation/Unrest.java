package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;

/**
 * How the people of a player's village feel about their ruler, and the (very rare) riot when they have had enough:
 * crowded homes, half the men called up, everybody worked off their feet, a village taken by force. Riots start only
 * when the mood is bad, and even then seldom. The rebels make for the flag; if they hold it with no guards or soldiers
 * of yours around, the village breaks away. Beat them, or calm the people down with gifts from the village screen.
 */
public final class Unrest {
	/** Reasons for the mood (codes sent to the village screen). */
	public static final int BASE = 0;
	public static final int RESPECT = 1;
	public static final int DEFENDED = 2;
	public static final int CROWDED = 3;
	public static final int ROOMY = 4;
	public static final int CONSCRIPTION = 5;
	public static final int OVERWORK = 6;
	public static final int BUILDINGS = 7;
	public static final int CONQUERED = 8;
	public static final int WAR = 9;
	public static final int CALMED = 10;
	/** Short of food (cities), or plenty of it. */
	public static final int HUNGRY = 11;
	public static final int FED = 12;

	/** A day: how long a village remembers being conquered, and how long gifts keep it calm. */
	public static final long DAY = 24000;
	public static final int SECEDE_SECONDS = 30;
	/** Debug counters read by the automated test. */
	public static int riots;
	public static int suppressed;
	public static int seceded;
	public static int calmed;

	private Unrest() {
	}

	/** The mood and what makes it: pairs of (reason, points). */
	public record Mood(int value, List<int[]> reasons) {
		public String word() {
			return value >= 10 ? "happy" : value >= 0 ? "calm" : value >= -15 ? "unhappy" : "angry";
		}
	}

	public static Mood mood(ServerLevel level, Politics p, Settlement s) {
		List<int[]> reasons = new ArrayList<>();
		Country c = p.country(s.country);
		long now = level.getGameTime();
		reasons.add(new int[]{BASE, 5});
		if (c != null && c.owner != null) {
			UUID owner = c.owner;
			int rep = 0;
			for (Villager v : Nations.villagers(level, s)) {
				rep += v.getGossips().getReputation(owner, t -> true);
			}
			rep = Math.max(-100, Math.min(100, rep)) / 5;
			if (rep != 0) {
				reasons.add(new int[]{RESPECT, rep});
			}
			int defended = Math.min(15, s.bonus.getOrDefault(owner, 0) / 4);
			if (defended > 0) {
				reasons.add(new int[]{DEFENDED, defended});
			}
		}
		int villagers = Nations.villagers(level, s).size();
		int workers = Economy.workers(level, s).size();
		int soldiers = Nations.soldiers(level, s).size();
		int people = villagers + workers;
		int beds = Economy.beds(level, s, false);
		if (people > beds + 1) {
			reasons.add(new int[]{CROWDED, -10});
		} else if (Economy.beds(level, s, true) >= 2) {
			reasons.add(new int[]{ROOMY, 4});
		}
		double called = (double) soldiers / Math.max(1, people + soldiers);
		if (called > 0.4) {
			reasons.add(new int[]{CONSCRIPTION, -15});
		} else if (called > 0.25) {
			reasons.add(new int[]{CONSCRIPTION, -6});
		}
		if (people > 2 && (double) workers / people > 0.6) {
			reasons.add(new int[]{OVERWORK, -8});
		}
		VillageEconomy e = s.eco;
		int homes = Math.min(8, 2 * (e.count(BuildingType.SMALL_HOUSE) + e.count(BuildingType.HOUSE) + e.count(BuildingType.APARTMENTS)));
		int good = homes + 5 * Math.min(1, e.count(BuildingType.HOSPITAL)) + 2 * Math.min(1, e.count(BuildingType.WAREHOUSE));
		if (good > 0) {
			reasons.add(new int[]{BUILDINGS, good});
		}
		if (s.capturedAt >= 0 && now - s.capturedAt < 2 * DAY) {
			int left = (int) Math.round(-25 * (1 - (now - s.capturedAt) / (2.0 * DAY)));
			if (left < 0) {
				reasons.add(new int[]{CONQUERED, left});
			}
		}
		if (c != null && !c.wars.isEmpty()) {
			reasons.add(new int[]{WAR, -5});
		}
		if (s.calmUntil > now) {
			reasons.add(new int[]{CALMED, 15});
		}
		int need = Supply.foodNeed(s);
		if (need > 0) {
			if (e.hungry > 0) {
				reasons.add(new int[]{HUNGRY, -Math.min(25, 5 + 5 * e.hungry)});
			} else if (e.stock[VillageEconomy.FOOD] >= need * 10) {
				reasons.add(new int[]{FED, 4});
			}
		}
		int value = 0;
		for (int[] r : reasons) {
			value += r[1];
		}
		return new Mood(value, reasons);
	}

	// ------------------------------------------------------------------------------------------------
	// Ticking

	static void tick(ServerLevel level, Politics p) {
		long t = level.getGameTime();
		for (Settlement s : p.settlements.values()) {
			if (s.riot) {
				if (t % 20 == 13) {
					riotStep(level, p, s);
				}
				continue;
			}
			// Every five minutes a village in a bad mood may rise (rarely; never in a good mood).
			if ((t + s.id * 397L) % 6000 == 0 && Economy.owned(p, s) && level.isLoaded(s.flag)) {
				Mood m = mood(level, p, s);
				if (m.value < -10) {
					double chance = Math.min(0.3, (-m.value - 10) / 100.0);
					if (level.getRandom().nextDouble() < chance) {
						startRiot(level, p, s);
					}
				}
			}
		}
	}

	/** The village rises: a few of its grown-ups take up arms and make for the flag. */
	public static boolean startRiot(ServerLevel level, Politics p, Settlement s) {
		Country c = p.country(s.country);
		if (c == null || s.riot) {
			return false;
		}
		List<Villager> adults = new ArrayList<>();
		for (Villager v : Nations.villagers(level, s)) {
			if (!v.isBaby() && !v.getUUID().equals(s.elder)) {
				adults.add(v);
			}
		}
		if (adults.size() < 2) {
			return false;
		}
		Random r = new Random();
		int n = Math.min(adults.size(), 2 + r.nextInt(3));
		for (int i = 0; i < n; i++) {
			Villager v = adults.get(i);
			SoldierEntity e = SoldierEntity.create(level, SoldierEntity.REBEL, c.id, -1, s.id, v.position(), Nations.lookOf(v.getUUID()));
			e.setOrigin(v.getVillagerData(), v.getUUID());
			e.setVillagerTag(WorkerEntity.save(level, v));
			e.orderTo(s.flag);
			v.discard();
			level.addFreshEntity(e);
		}
		s.riot = true;
		s.riotTicks = 0;
		p.setDirty();
		riots++;
		level.playSound(null, s.flag, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 2f, 1.2f);
		Mood m = mood(level, p, s);
		int worst = BASE;
		int worstValue = 0;
		for (int[] reason : m.reasons) {
			if (reason[1] < worstValue) {
				worst = reason[0];
				worstValue = reason[1];
			}
		}
		tell(level, c, Component.translatable("nation.airdefense.riot.started", s.name,
				Component.translatable("screen.airdefense.village.mood.reason." + worst)));
		AirDefense.LOGGER.info("[airdefense] riot in {} ({} rebels, mood {})", s.name, n, m.value);
		return true;
	}

	public static List<SoldierEntity> rebels(ServerLevel level, Settlement s) {
		return level.getEntitiesOfClass(SoldierEntity.class, new AABB(s.center).inflate(s.radius + 32, 40, s.radius + 32),
				e -> e.isAlive() && e.role() == SoldierEntity.REBEL && e.home() == s.id);
	}

	/** Every second of a riot: beaten, or holding the flag (and breaking away after 30 s of it). */
	private static void riotStep(ServerLevel level, Politics p, Settlement s) {
		if (!level.isLoaded(s.flag)) {
			return;
		}
		List<SoldierEntity> rebels = rebels(level, s);
		Country c = p.country(s.country);
		if (rebels.isEmpty() || c == null) {
			s.riot = false;
			s.riotTicks = 0;
			// After a riot is put down the village keeps its head down for a while.
			s.calmUntil = level.getGameTime() + DAY / 2;
			p.setDirty();
			suppressed++;
			if (c != null) {
				tell(level, c, Component.translatable("nation.airdefense.riot.over", s.name));
			}
			return;
		}
		Vec3 flag = Vec3.atCenterOf(s.flag);
		boolean atFlag = false;
		for (SoldierEntity e : rebels) {
			if (e.distanceToSqr(flag) < 6 * 6) {
				atFlag = true;
				break;
			}
		}
		boolean defended = !level.getEntitiesOfClass(SoldierEntity.class, new AABB(s.flag).inflate(20, 8, 20),
				e -> e.isAlive() && e.country() == c.id && (e.role() == SoldierEntity.GUARD || e.role() == SoldierEntity.SOLDIER)).isEmpty();
		if (atFlag && !defended) {
			s.riotTicks++;
			if (s.riotTicks % 10 == 0) {
				tell(level, c, Component.translatable("nation.airdefense.riot.flag", s.name, s.riotTicks, SECEDE_SECONDS));
			}
			if (s.riotTicks >= SECEDE_SECONDS) {
				secede(level, p, s, rebels);
			}
		} else if (s.riotTicks > 0) {
			s.riotTicks = Math.max(0, s.riotTicks - 2);
		}
	}

	/** The rebels won: the village leaves its country and the rebels go home. */
	private static void secede(ServerLevel level, Politics p, Settlement s, List<SoldierEntity> rebels) {
		Country from = p.country(s.country);
		for (SoldierEntity e : rebels) {
			e.demobilize(level);
		}
		// The ruler's soldiers from this village go home too.
		for (SoldierEntity e : Nations.soldiers(level, s)) {
			e.demobilize(level);
		}
		s.soldiers.clear();
		for (SoldierEntity g : Nations.guards(level, s)) {
			g.discard();
		}
		s.riot = false;
		s.riotTicks = 0;
		s.country = -1;
		s.capturedAt = -1;
		if (from != null) {
			List<Settlement> left = p.settlementsOf(from.id);
			if (from.capital == s.id) {
				from.capital = left.isEmpty() ? -1 : left.getFirst().id;
			}
			tell(level, from, Component.translatable("nation.airdefense.riot.seceded", s.name));
		}
		Nations.placeFlag(level, p, s);
		p.setDirty();
		seceded++;
		level.playSound(null, s.flag, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 2f, 1f);
	}

	/** Gifts for the village: emeralds that calm the people for two days (and send the rebels home). */
	public static int calmPrice(Settlement s) {
		return 5 + 2 * Math.max(1, s.population);
	}

	public static boolean calm(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		if (!Economy.owner(p, s, player)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.not_yours"));
			return false;
		}
		int price = calmPrice(s);
		if (!player.getAbilities().instabuild) {
			if (player.getInventory().countItem(Items.EMERALD) < price) {
				player.sendOverlayMessage(Component.translatable("nation.airdefense.need_emeralds", price));
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
		}
		s.calmUntil = level.getGameTime() + 2 * DAY;
		if (s.riot) {
			for (SoldierEntity e : rebels(level, s)) {
				e.demobilize(level);
			}
			s.riot = false;
			s.riotTicks = 0;
		}
		p.setDirty();
		calmed++;
		level.playSound(null, s.flag, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1.1f);
		player.sendSystemMessage(Component.translatable("nation.airdefense.riot.calmed", s.name));
		return true;
	}

	private static void tell(ServerLevel level, Country c, Component message) {
		for (ServerPlayer owner : Politics.online(level.getServer(), c)) {
			owner.sendSystemMessage(message);
		}
	}
}
