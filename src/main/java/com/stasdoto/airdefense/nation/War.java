package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;

/**
 * Wars between your country and the countries the world made up. You declare war from the village screen of one of
 * their villages (taking one of their villages by force starts a war too, and now and then a neighbour attacks a
 * growing country). At war their guards shoot on sight, your soldiers and theirs fight wherever they meet, and every
 * few minutes they send a squad against one of your villages: if it holds the flag with nobody of yours there, the
 * village is theirs. Peace is asked for on the same screen - they agree when the war goes badly for them or has
 * dragged on, otherwise they want emeralds for it.
 */
public final class War {
	public static final int CAPTURE_SECONDS = 30;
	/** Debug counters read by the automated test. */
	public static int declared;
	public static int squads;
	public static int aiCaptures;
	public static int peaces;

	/** What a country asks for peace (key: two country ids), until it is paid or the war ends. */
	private static final Map<Long, Integer> TRIBUTE = new HashMap<>();
	/** After a peace the same two do not go to war again for a day (unless the player declares it). */
	private static final Map<Long, Long> PEACE_SINCE = new HashMap<>();

	private War() {
	}

	private static long pair(int a, int b) {
		return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFFFFFFL);
	}

	// ------------------------------------------------------------------------------------------------
	// War and peace

	public static void declare(ServerLevel level, Politics p, Country attacker, Country defender, Component why) {
		if (attacker.id == defender.id || attacker.atWarWith(defender.id)) {
			return;
		}
		long now = level.getGameTime();
		attacker.wars.add(defender.id);
		defender.wars.add(attacker.id);
		attacker.warSince.put(defender.id, now);
		defender.warSince.put(attacker.id, now);
		attacker.warScore.put(defender.id, 0);
		defender.warScore.put(attacker.id, 0);
		TRIBUTE.remove(pair(attacker.id, defender.id));
		p.setDirty();
		declared++;
		Component message = Component.translatable("nation.airdefense.war.declared", attacker.name, defender.name, why);
		for (Country c : new Country[]{attacker, defender}) {
			tell(level, c, message);
		}
		AirDefense.LOGGER.info("[airdefense] war: {} against {}", attacker.name, defender.name);
	}

	public static void peace(ServerLevel level, Politics p, Country a, Country b) {
		a.wars.remove(b.id);
		b.wars.remove(a.id);
		a.warSince.remove(b.id);
		b.warSince.remove(a.id);
		a.warScore.remove(b.id);
		b.warScore.remove(a.id);
		TRIBUTE.remove(pair(a.id, b.id));
		PEACE_SINCE.put(pair(a.id, b.id), level.getGameTime());
		p.setDirty();
		peaces++;
		Component message = Component.translatable("nation.airdefense.war.peace", a.name, b.name);
		tell(level, a, message);
		tell(level, b, message);
		// Their soldiers in the field go home.
		for (SoldierEntity e : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(SoldierEntity.class),
				e -> e.isAlive() && e.role() == SoldierEntity.SOLDIER && (e.country() == a.id || e.country() == b.id)
						&& e.getTarget() != null)) {
			e.setTarget(null);
		}
	}

	/** The player declares war on the country that owns this village. */
	public static boolean playerDeclares(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		Country target = p.country(s.country);
		Country mine = p.countryOwnedBy(player.getUUID());
		if (target == null || target.owner != null) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.war.not_country"));
			return false;
		}
		if (mine == null || p.settlementsOf(mine.id).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.war.no_country"));
			return false;
		}
		if (mine.atWarWith(target.id)) {
			return false;
		}
		declare(level, p, mine, target, Component.translatable("nation.airdefense.war.why_player"));
		level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 2f, 0.9f);
		return true;
	}

	/** The player asks for peace: accepted, or a price is named. */
	public static boolean proposePeace(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		Country them = p.country(s.country);
		Country mine = p.countryOwnedBy(player.getUUID());
		if (them == null || mine == null || !mine.atWarWith(them.id)) {
			return false;
		}
		int score = mine.warScore.getOrDefault(them.id, 0);
		long minutes = (level.getGameTime() - mine.warSince.getOrDefault(them.id, level.getGameTime())) / 1200;
		if (score >= 2 || minutes >= 10 && score >= 0) {
			player.sendSystemMessage(Component.translatable("nation.airdefense.war.peace_accepted", them.name));
			peace(level, p, mine, them);
			return true;
		}
		int price = 16 + 8 * Math.max(0, -score) + (minutes < 5 ? 16 : 0);
		TRIBUTE.put(pair(mine.id, them.id), price);
		player.sendSystemMessage(Component.translatable("nation.airdefense.war.peace_price", them.name, price));
		return false;
	}

	/** What the enemy wants for peace (0 = nothing asked yet). */
	public static int tribute(Country a, Country b) {
		return TRIBUTE.getOrDefault(pair(a.id, b.id), 0);
	}

	public static boolean payTribute(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		Country them = p.country(s.country);
		Country mine = p.countryOwnedBy(player.getUUID());
		if (them == null || mine == null || !mine.atWarWith(them.id)) {
			return false;
		}
		int price = tribute(mine, them);
		if (price <= 0) {
			return proposePeace(level, player, s);
		}
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
		player.sendSystemMessage(Component.translatable("nation.airdefense.war.peace_bought", them.name, price));
		peace(level, p, mine, them);
		return true;
	}

	/** A village changed hands by force between two countries at war: the winner scores. */
	static void scored(Politics p, Country winner, Country loser) {
		if (winner == null || loser == null || !winner.atWarWith(loser.id)) {
			return;
		}
		winner.warScore.merge(loser.id, 1, Integer::sum);
		loser.warScore.merge(winner.id, -1, Integer::sum);
		p.setDirty();
	}

	// ------------------------------------------------------------------------------------------------
	// The enemy at work

	static void tick(ServerLevel level, Politics p) {
		long t = level.getGameTime();
		if (t % 20 == 9) {
			captureTick(level, p);
		}
		if (t % 1200 == 600) {
			attacks(level, p);
		}
		if (t % 24000 == 12000) {
			neighbours(level, p);
		}
	}

	/** Once a minute every enemy country at war with a player may send a squad against one of his villages. */
	private static void attacks(ServerLevel level, Politics p) {
		Random r = new Random();
		for (Country ai : new ArrayList<>(p.countries.values())) {
			if (ai.owner != null || ai.wars.isEmpty()) {
				continue;
			}
			for (int enemyId : new ArrayList<>(ai.wars)) {
				Country enemy = p.country(enemyId);
				if (enemy == null || enemy.owner == null || r.nextInt(100) >= 35) {
					continue;
				}
				Settlement target = nearestTarget(level, p, ai, enemy);
				if (target != null) {
					sendSquad(level, p, ai, target, 3 + r.nextInt(3));
				}
				// At night, now and then: a massed drone raid on one of his villages.
				if (target != null && level.isDarkOutside() && r.nextInt(100) < 12
						&& !com.stasdoto.airdefense.drone.Raids.activeNear(level, net.minecraft.world.phys.Vec3.atCenterOf(target.center), 200)) {
					raid(level, p, ai, target, r);
				}
			}
		}
	}

	/** A massed raid from the enemy's side: more drones the bigger the enemy country is. */
	public static int raid(ServerLevel level, Politics p, Country ai, Settlement target, Random r) {
		Settlement home = null;
		double bestD = Double.MAX_VALUE;
		for (Settlement o : p.settlementsOf(ai.id)) {
			double d = o.center.distSqr(target.center);
			if (d < bestD) {
				bestD = d;
				home = o;
			}
		}
		double bearing = home != null ? Math.atan2(home.center.getZ() - target.center.getZ(), home.center.getX() - target.center.getX())
				: r.nextDouble() * Math.PI * 2;
		int size = p.settlementsOf(ai.id).size();
		int drones = Math.min(24, 6 + size * 3 + r.nextInt(5));
		Country victim = p.country(target.country);
		if (victim != null) {
			tell(level, victim, Component.translatable("nation.airdefense.war.raid", ai.name));
		}
		return com.stasdoto.airdefense.drone.Raids.start(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(target.center), bearing, drones,
				r.nextInt(3), size >= 4 ? 1 : 0, true);
	}

	/** The enemy's loaded village nearest to the attacker's own villages (within 900 blocks). */
	@Nullable
	private static Settlement nearestTarget(ServerLevel level, Politics p, Country ai, Country enemy) {
		Settlement best = null;
		double bestD = 900.0 * 900.0;
		List<Settlement> own = p.settlementsOf(ai.id);
		for (Settlement s : p.settlementsOf(enemy.id)) {
			if (!level.isLoaded(s.flag)) {
				continue;
			}
			for (Settlement o : own) {
				double d = o.center.distSqr(s.center);
				if (d < bestD) {
					bestD = d;
					best = s;
				}
			}
			if (own.isEmpty() && best == null) {
				best = s;
			}
		}
		return best;
	}

	/** An enemy squad turns up 50-70 blocks from the village (on the side of their own land) and heads for its flag. */
	public static List<SoldierEntity> sendSquad(ServerLevel level, Politics p, Country ai, Settlement target, int count) {
		Random r = new Random();
		Settlement home = null;
		double bestD = Double.MAX_VALUE;
		for (Settlement o : p.settlementsOf(ai.id)) {
			double d = o.center.distSqr(target.center);
			if (d < bestD) {
				bestD = d;
				home = o;
			}
		}
		double a = home != null ? Math.atan2(home.center.getZ() - target.center.getZ(), home.center.getX() - target.center.getX())
				: r.nextDouble() * Math.PI * 2;
		List<SoldierEntity> squad = new ArrayList<>();
		for (double dist : new double[]{60, 45, 30}) {
			for (int i = 0; i < count && squad.size() < count; i++) {
				int x = (int) Math.floor(target.center.getX() + Math.cos(a) * dist + r.nextInt(7) - 3);
				int z = (int) Math.floor(target.center.getZ() + Math.sin(a) * dist + r.nextInt(7) - 3);
				if (!level.isLoaded(new BlockPos(x, 64, z))) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				SoldierEntity e = SoldierEntity.create(level, SoldierEntity.SOLDIER, ai.id, ai.color, home == null ? -1 : home.id,
						new Vec3(x + 0.5, y, z + 0.5), r.nextInt());
				e.orderTo(target.flag);
				level.addFreshEntity(e);
				squad.add(e);
			}
			if (!squad.isEmpty()) {
				break;
			}
		}
		if (!squad.isEmpty()) {
			squads++;
			Country owner = p.country(target.country);
			if (owner != null) {
				tell(level, owner, Component.translatable("nation.airdefense.war.squad", ai.name, squad.size(), target.name));
			}
			AirDefense.LOGGER.info("[airdefense] {} sends {} soldiers against {}", ai.name, squad.size(), target.name);
		}
		return squad;
	}

	/** Every second: enemy soldiers holding the flag of a player's village with nobody of his around take it in 30 s. */
	private static void captureTick(ServerLevel level, Politics p) {
		for (Settlement s : p.settlements.values()) {
			Country owner = p.country(s.country);
			if (owner == null || owner.owner == null || owner.wars.isEmpty() || !level.isLoaded(s.flag)) {
				if (s.aiCaptureTicks > 0) {
					s.aiCaptureTicks = 0;
				}
				continue;
			}
			Vec3 flag = Vec3.atCenterOf(s.flag);
			SoldierEntity enemy = null;
			for (SoldierEntity e : level.getEntitiesOfClass(SoldierEntity.class, new AABB(s.flag).inflate(6, 4, 6),
					e -> e.isAlive() && (e.role() == SoldierEntity.SOLDIER || e.role() == SoldierEntity.GUARD) && owner.atWarWith(e.country()))) {
				enemy = e;
				break;
			}
			if (enemy == null) {
				s.aiCaptureTicks = Math.max(0, s.aiCaptureTicks - 2);
				continue;
			}
			boolean defended = !level.getEntitiesOfClass(SoldierEntity.class, new AABB(s.flag).inflate(20, 8, 20),
					e -> e.isAlive() && e.country() == owner.id && (e.role() == SoldierEntity.GUARD || e.role() == SoldierEntity.SOLDIER)).isEmpty();
			ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(owner.owner);
			if (ruler != null && !ruler.isSpectator() && ruler.level() == level && ruler.distanceToSqr(flag) < 16 * 16) {
				defended = true;
			}
			if (defended) {
				continue;
			}
			s.aiCaptureTicks++;
			Country ai = p.country(enemy.country());
			if (s.aiCaptureTicks % 10 == 0) {
				tell(level, owner, Component.translatable("nation.airdefense.war.capturing", ai == null ? "?" : ai.name, s.name, s.aiCaptureTicks,
						CAPTURE_SECONDS));
			}
			if (s.aiCaptureTicks >= CAPTURE_SECONDS && ai != null) {
				s.aiCaptureTicks = 0;
				scored(p, ai, owner);
				// The village's soldiers in the field are no longer the player's.
				for (SoldierEntity e : Nations.soldiers(level, s)) {
					e.demobilize(level);
				}
				s.soldiers.clear();
				Nations.transfer(level, p, s, ai);
				aiCaptures++;
				tell(level, owner, Component.translatable("nation.airdefense.war.lost", s.name, ai.name));
				level.playSound(null, s.flag, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 2f, 0.8f);
			}
		}
	}

	/** Once a day a neighbour may fall upon a country that has grown big (three villages or more). */
	private static void neighbours(ServerLevel level, Politics p) {
		Random r = new Random();
		long now = level.getGameTime();
		for (Country player : new ArrayList<>(p.countries.values())) {
			if (player.owner == null) {
				continue;
			}
			List<Settlement> villages = p.settlementsOf(player.id);
			if (villages.size() < 3) {
				continue;
			}
			for (Country ai : new ArrayList<>(p.countries.values())) {
				if (ai.owner != null || ai.cityState || ai.atWarWith(player.id) || r.nextInt(100) >= 8) {
					continue;
				}
				Long since = PEACE_SINCE.get(pair(ai.id, player.id));
				if (since != null && now - since < 24000) {
					continue;
				}
				Settlement cap = p.settlements.get(ai.capital);
				boolean near = false;
				for (Settlement v : villages) {
					if (cap != null && cap.center.distSqr(v.center) < 700 * 700) {
						near = true;
						break;
					}
				}
				if (near) {
					declare(level, p, ai, player, Component.translatable("nation.airdefense.war.why_growth"));
				}
			}
		}
	}

	private static void tell(ServerLevel level, Country c, Component message) {
		if (c.owner != null && level.getServer().getPlayerList().getPlayer(c.owner) instanceof ServerPlayer owner) {
			owner.sendSystemMessage(message);
		}
	}
}
