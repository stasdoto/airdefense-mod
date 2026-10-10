package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 1.29: talking to anyone in the towns - a townsman (a villager), a worker, a guard or a soldier. He says who he is,
 * what he does and where he lives; you can ask about the town, how he is getting on, trade with him, take him on as a
 * worker in your own town, or - your own soldiers - have them follow you, stand where they are or go back to their post.
 */
public final class Dialogue {
	public static final int TOWN = 1;
	public static final int HOW = 2;
	public static final int TRADE = 3;
	public static final int FOLLOW = 4;
	public static final int STAY = 5;
	public static final int RETURN = 6;
	public static final int HIRE = 7;
	public static final int BYE = 8;
	public static final int WORK_DAY = 9;

	/** For the automated test. */
	public static int opened;
	public static int answered;

	private Dialogue() {
	}

	/** Who can be talked to: townsfolk, workers, guards and soldiers (not bandits, not rebels in a riot). */
	public static boolean talks(Entity e) {
		if (e instanceof SoldierEntity s) {
			return s.role() == SoldierEntity.GUARD || s.role() == SoldierEntity.SOLDIER;
		}
		return e instanceof Villager v && !v.isBaby() || e instanceof WorkerEntity;
	}

	public static void open(ServerLevel level, ServerPlayer player, LivingEntity who) {
		opened++;
		send(level, player, who, greeting(level, player, who), true);
	}

	private static void send(ServerLevel level, ServerPlayer player, LivingEntity who, Component speech, boolean open) {
		ServerPlayNetworking.send(player, new DialoguePayload(who.getId(), name(level, who), title(level, who), speech, options(level, player, who), open));
	}

	// ------------------------------------------------------------------------------------------------
	// Who he is

	private static Settlement home(ServerLevel level, LivingEntity who) {
		Politics p = Politics.get(level.getServer());
		if (who instanceof WorkerEntity w) {
			Settlement s = w.settlement();
			if (s != null) {
				return s;
			}
		}
		if (who instanceof SoldierEntity s && s.home() >= 0) {
			Settlement h = p.settlements.get(s.home());
			if (h != null) {
				return h;
			}
		}
		return p.settlementAt(who.blockPosition());
	}

	private static CityStyle style(Settlement s) {
		return s == null ? CityStyle.CLASSIC : CityStyle.of(s.style);
	}

	static String name(ServerLevel level, LivingEntity who) {
		Settlement s = home(level, who);
		int country = who instanceof SoldierEntity so ? so.country() : s == null ? 0 : s.country;
		boolean east = SoldierEntity.bloc(country) == com.stasdoto.airdefense.weapon.GunType.Bloc.EAST;
		return People.name(who.getUUID(), style(s), east, who instanceof SoldierEntity);
	}

	private static Component title(ServerLevel level, LivingEntity who) {
		Settlement s = home(level, who);
		Component town = s == null ? Component.translatable("dialogue.airdefense.nowhere") : Component.literal(s.name);
		Component what;
		if (who instanceof SoldierEntity so) {
			what = so.title();
		} else if (who instanceof WorkerEntity w) {
			what = w.getTypeName();
		} else if (who instanceof Villager v) {
			String job = v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
			what = Component.translatable("dialogue.airdefense.job." + (job.equals("none") || job.equals("nitwit") ? "none" : job));
		} else {
			what = who.getName();
		}
		return Component.translatable("dialogue.airdefense.title", what, town);
	}

	private static List<Integer> options(ServerLevel level, ServerPlayer player, LivingEntity who) {
		List<Integer> out = new ArrayList<>();
		out.add(TOWN);
		out.add(HOW);
		Politics p = Politics.get(level.getServer());
		Settlement s = home(level, who);
		if (who instanceof Villager v) {
			String job = v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
			if (!job.equals("none") && !job.equals("nitwit")) {
				out.add(TRADE);
			}
			if (s != null && Economy.owner(p, s, player) && !v.getUUID().equals(s.elder)) {
				out.add(HIRE);
			}
		}
		if (who instanceof WorkerEntity) {
			out.add(WORK_DAY);
		}
		if (who instanceof SoldierEntity so && own(p, player, so)) {
			out.add(so.following(player) ? STAY : FOLLOW);
			if (so.order() != null || so.following(player)) {
				out.add(RETURN);
			} else {
				out.add(STAY);
			}
		}
		out.add(BYE);
		return out;
	}

	/** A soldier or guard of a country the player runs. */
	private static boolean own(Politics p, ServerPlayer player, SoldierEntity s) {
		Country c = p.country(s.country());
		return c != null && c.isMember(player.getUUID());
	}

	// ------------------------------------------------------------------------------------------------
	// What he says

	private static Component greeting(ServerLevel level, ServerPlayer player, LivingEntity who) {
		int k = People.roll(who.getUUID(), level.getGameTime(), 3);
		if (who instanceof SoldierEntity so) {
			if (so.getTarget() != null) {
				return Component.translatable("dialogue.airdefense.hello.soldier_fight");
			}
			return Component.translatable("dialogue.airdefense.hello.soldier." + k);
		}
		if (level.isDarkOutside()) {
			return Component.translatable("dialogue.airdefense.hello.night." + k);
		}
		return Component.translatable("dialogue.airdefense.hello." + k);
	}

	public static void handle(ServerPlayer player, DialogueActionPayload a) {
		ServerLevel level = player.level();
		Entity e = level.getEntity(a.entity());
		if (!(e instanceof LivingEntity who) || !who.isAlive() || who.distanceToSqr(player) > 12 * 12 || !talks(who)) {
			return;
		}
		answered++;
		Politics p = Politics.get(level.getServer());
		Settlement s = home(level, who);
		Component say;
		switch (a.action()) {
			case TOWN -> say = aboutTown(level, p, s);
			case HOW -> say = howAreYou(level, p, s, who);
			case TRADE -> {
				if (who instanceof Villager v && !v.isTrading()) {
					v.setTradingPlayer(player);
					v.openTradingScreen(player, Component.literal(name(level, who)), v.getVillagerData().level());
				}
				return;
			}
			case HIRE -> {
				WorkerEntity w = hire(level, player, p, s, who);
				if (w == null) {
					say = Component.translatable("dialogue.airdefense.hire.no");
				} else {
					// He is a worker now (another entity): the talk goes on with him.
					send(level, player, w, Component.translatable("dialogue.airdefense.hire.yes", w.getTypeName()), true);
					return;
				}
			}
			case FOLLOW -> {
				if (who instanceof SoldierEntity so && own(p, player, so)) {
					so.follow(player);
					say = Component.translatable("dialogue.airdefense.follow");
				} else {
					return;
				}
			}
			case STAY -> {
				if (who instanceof SoldierEntity so && own(p, player, so)) {
					so.follow(null);
					so.orderTo(so.blockPosition());
					say = Component.translatable("dialogue.airdefense.stay");
				} else {
					return;
				}
			}
			case RETURN -> {
				if (who instanceof SoldierEntity so && own(p, player, so)) {
					so.follow(null);
					so.orderTo(null);
					say = Component.translatable("dialogue.airdefense.return");
				} else {
					return;
				}
			}
			case WORK_DAY -> say = workDay(level, who);
			default -> {
				return;
			}
		}
		send(level, player, who, say, false);
	}

	private static Component aboutTown(ServerLevel level, Politics p, Settlement s) {
		if (s == null) {
			return Component.translatable("dialogue.airdefense.town.none");
		}
		Unrest.Mood m = Unrest.mood(level, p, s);
		int worst = -1;
		int worstPoints = 0;
		int best = -1;
		int bestPoints = 0;
		for (int[] r : m.reasons()) {
			if (r[1] < worstPoints) {
				worstPoints = r[1];
				worst = r[0];
			}
			if (r[1] > bestPoints && r[0] != Unrest.BASE) {
				bestPoints = r[1];
				best = r[0];
			}
		}
		Country c = p.country(s.country);
		Component country = c == null ? Component.translatable("dialogue.airdefense.free_town") : Component.literal(c.name);
		Component mood = Component.translatable("screen.airdefense.village.mood." + m.word());
		Component why = worst >= 0 ? Component.translatable("dialogue.airdefense.town.bad", Component.translatable("screen.airdefense.village.mood.reason." + worst))
				: best >= 0 ? Component.translatable("dialogue.airdefense.town.good", Component.translatable("screen.airdefense.village.mood.reason." + best))
				: Component.empty();
		return Component.translatable("dialogue.airdefense.town", s.name, country, s.people(), mood, why);
	}

	private static Component howAreYou(ServerLevel level, Politics p, Settlement s, LivingEntity who) {
		int k = People.roll(who.getUUID(), level.getGameTime(), 3);
		if (who instanceof SoldierEntity so) {
			Country c = p.country(so.country());
			if (c != null && !c.wars.isEmpty()) {
				return Component.translatable("dialogue.airdefense.how.soldier_war." + k);
			}
			return Component.translatable("dialogue.airdefense.how.soldier." + k);
		}
		if (who instanceof WorkerEntity w) {
			return Component.translatable("dialogue.airdefense.how.worker." + k, w.getTypeName());
		}
		String word = s == null ? "calm" : Unrest.mood(level, p, s).word();
		return Component.translatable("dialogue.airdefense.how." + word + "." + k);
	}

	private static Component workDay(ServerLevel level, LivingEntity who) {
		long t = level.getOverworldClockTime() % 24000;
		return Component.translatable(level.isDarkOutside() ? "dialogue.airdefense.workday.night" : t < 2000 ? "dialogue.airdefense.workday.morning"
				: "dialogue.airdefense.workday.day");
	}

	@org.jetbrains.annotations.Nullable
	private static WorkerEntity hire(ServerLevel level, ServerPlayer player, Politics p, Settlement s, LivingEntity who) {
		if (!(who instanceof Villager v) || s == null || !Economy.owner(p, s, player)) {
			return null;
		}
		// The job the town has fewest hands on (builders first while something is going up).
		int[] count = new int[WorkerEntity.JOBS];
		for (WorkerEntity w : Economy.workers(level, s)) {
			count[w.job()]++;
		}
		int job;
		if (s.eco.active() != null && count[WorkerEntity.BUILD] < 3) {
			job = WorkerEntity.BUILD;
		} else {
			job = WorkerEntity.WOOD;
			for (int j = 0; j < WorkerEntity.BUILD; j++) {
				if (count[j] < count[job]) {
					job = j;
				}
			}
		}
		WorkerEntity w = WorkerEntity.hire(level, v, s.id, job);
		w.setHomeTo(s.flag, 48);
		s.eco.workers.add(w.getUUID());
		p.setDirty();
		return w;
	}
}
