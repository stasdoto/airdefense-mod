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
					// Bigger squads now (1.25), and they come by road where there is one.
					int men = 4 + r.nextInt(5) + Math.min(4, p.settlementsOf(ai.id).size() / 2);
					if (sendColumn(level, p, ai, target, men).isEmpty()) {
						sendSquad(level, p, ai, target, men);
					}
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

	/** The way into a town by road: the road, which end is out of town, how far back it starts, the points to drive. */
	public static final class Approach {
		/** The road (null: across country, in a straight line from {@code (ex, ez)} out along {@code (dx, dz)}). */
		@Nullable
		public final Cities.Road road;
		public final boolean fromEnd;
		public final double back;
		public final List<Vec3> waypoints = new ArrayList<>();
		private double ex;
		private double ez;
		private double dx;
		private double dz;

		Approach(@Nullable Cities.Road road, boolean fromEnd, double back) {
			this.road = road;
			this.fromEnd = fromEnd;
			this.back = back;
		}

		/** Distance along the road from the town's end to the road's own measure. */
		public double fromTown(double s) {
			return fromEnd ? road.length - s : s;
		}

		/** How far out the way goes. */
		public double length() {
			return road != null ? road.length : back + 60;
		}

		/** The point {@code s} blocks out of town along the way. */
		public double[] at(double s) {
			return road != null ? road.pointAt(fromTown(s)) : new double[]{ex + dx * s, ez + dz * s};
		}
	}

	/**
	 * No road in: across country in a straight line from the attacker's side (turned a little either way if that side
	 * is water), from {@code back} blocks out to the edge of the town.
	 */
	@Nullable
	public static Approach crossCountry(ServerLevel level, Settlement target, Vec3 from, double back) {
		Cities.Terrain t = Cities.terrain(level);
		double base = Math.atan2(from.z - target.center.getZ(), from.x - target.center.getX());
		double edge = Math.max(24, target.radius * 0.8);
		for (double turn : new double[]{0, 0.4, -0.4, 0.8, -0.8, 1.3, -1.3}) {
			double a = base + turn;
			double dx = Math.cos(a);
			double dz = Math.sin(a);
			double ex = target.center.getX() + 0.5 + dx * edge;
			double ez = target.center.getZ() + 0.5 + dz * edge;
			boolean dry = true;
			for (double s = 0; s <= back && dry; s += 20) {
				dry = t.top((int) Math.floor(ex + dx * s), (int) Math.floor(ez + dz * s)) >= t.sea();
			}
			if (!dry) {
				continue;
			}
			Approach ap = new Approach(null, false, back);
			ap.ex = ex;
			ap.ez = ez;
			ap.dx = dx;
			ap.dz = dz;
			for (double s = back; s >= 0; s -= 12) {
				ap.waypoints.add(new Vec3(ex + dx * s, 0, ez + dz * s));
			}
			return ap;
		}
		return null;
	}

	/**
	 * The road into a town that comes from the side of {@code from}, driven from up to {@code maxBack} blocks out to a
	 * little way into the town (a city: up its street; a hamlet: to the square's edge). Null if there is no such road.
	 */
	@Nullable
	public static Approach approach(ServerLevel level, Settlement target, Vec3 from, double maxBack) {
		long seed = level.getSeed();
		Cities.Terrain t = Cities.terrain(level);
		List<Cities.Road> in = new ArrayList<>();
		Cities.City city = target.city >= 0 ? Cities.plannedCityAt(seed, target.center.getX(), target.center.getZ(), 400) : null;
		Hamlets.Hamlet hamlet = target.hamlet >= 0 ? Cities.plannedHamletAt(seed, target.center.getX(), target.center.getZ(), 40) : null;
		if (city != null) {
			for (Cities.Road r : Cities.roadsNear(seed, t, city.x, city.z)) {
				if (city.outside(r.x0, r.z0) <= 12 || city.outside(r.x1, r.z1) <= 12) {
					in.add(r);
				}
			}
		} else if (hamlet != null && hamlet.road != null) {
			in.add(hamlet.road);
		}
		Cities.Road road = null;
		boolean towardsEnd = false;
		double best = Double.MAX_VALUE;
		for (Cities.Road r : in) {
			boolean startInTown = city != null ? city.outside(r.x0, r.z0) <= 12 : Math.hypot(r.x0 - target.center.getX(), r.z0 - target.center.getZ()) < 30;
			double[] far = startInTown ? new double[]{r.x1, r.z1} : new double[]{r.x0, r.z0};
			double d = Math.hypot(far[0] - from.x, far[1] - from.z);
			if (d < best) {
				best = d;
				road = r;
				towardsEnd = !startInTown;
			}
		}
		if (road == null || road.length < 90) {
			return null;
		}
		// Far enough out not to be seen turning up (or as far as the road goes).
		Approach ap = new Approach(road, towardsEnd, Math.min(road.length - 10, maxBack));
		for (double s = ap.back; s >= 0; s -= 12) {
			double[] pt = road.pointAt(ap.fromTown(s));
			ap.waypoints.add(new Vec3(pt[0], 0, pt[1]));
		}
		if (city != null) {
			// On the way the road was going (it runs on into a street), not straight at the centre through the houses.
			double[] e = road.pointAt(ap.fromTown(0));
			double[] b = road.pointAt(ap.fromTown(Math.min(10, road.length)));
			Vec3 into = new Vec3(e[0] - b[0], 0, e[1] - b[1]).normalize();
			ap.waypoints.add(new Vec3(e[0], 0, e[1]).add(into.scale(12)));
			ap.waypoints.add(new Vec3(e[0], 0, e[1]).add(into.scale(24)));
		}
		return ap;
	}

	/**
	 * A column against a town (1.25): the squad comes by road from the attacker's nearest town - a car for a few men,
	 * an armoured carrier and a lorry for more, a column led by a tank for a big squad - and turns up far out on the
	 * road it comes in by, not out of thin air; at the edge of town the men get out and go for the flag on foot while
	 * the armour gives fire. Empty if there is no road to come by.
	 */
	public static List<com.stasdoto.airdefense.vehicle.VehicleEntity> sendColumn(ServerLevel level, Politics p, Country ai, Settlement target, int men) {
		List<com.stasdoto.airdefense.vehicle.VehicleEntity> out = new ArrayList<>();
		long seed = level.getSeed();
		Cities.Terrain t = Cities.terrain(level);
		Settlement home = null;
		double bestD = Double.MAX_VALUE;
		for (Settlement o : p.settlementsOf(ai.id)) {
			double d = o.center.distSqr(target.center);
			if (d < bestD) {
				bestD = d;
				home = o;
			}
		}
		Vec3 homeAt = home != null ? Vec3.atCenterOf(home.center) : Vec3.atCenterOf(target.center).add(500, 0, 0);
		Approach ap = approach(level, target, homeAt, 210);
		if (ap == null) {
			// No road: they come across the fields from their side - still from out of sight, never out of thin air.
			ap = crossCountry(level, target, homeAt, 200);
		}
		if (ap == null) {
			return out;
		}
		double back = ap.back;
		List<Vec3> waypoints = ap.waypoints;
		// The vehicles: by how many men there are and which side's kit.
		boolean east = SoldierEntity.bloc(ai.id) == com.stasdoto.airdefense.weapon.GunType.Bloc.EAST;
		List<com.stasdoto.airdefense.vehicle.VehicleType> kit = new ArrayList<>();
		if (men <= 4) {
			kit.add(east ? com.stasdoto.airdefense.vehicle.VehicleType.BTR82 : com.stasdoto.airdefense.vehicle.VehicleType.MAXXPRO);
		} else if (men <= 8) {
			kit.add(east ? com.stasdoto.airdefense.vehicle.VehicleType.BMP2 : com.stasdoto.airdefense.vehicle.VehicleType.BRADLEY);
			kit.add(com.stasdoto.airdefense.vehicle.VehicleType.SUPPLY_TRUCK);
		} else {
			kit.add(east ? com.stasdoto.airdefense.vehicle.VehicleType.T72 : com.stasdoto.airdefense.vehicle.VehicleType.LEOPARD2);
			kit.add(east ? com.stasdoto.airdefense.vehicle.VehicleType.BMP2 : com.stasdoto.airdefense.vehicle.VehicleType.BRADLEY);
			kit.add(east ? com.stasdoto.airdefense.vehicle.VehicleType.BTR82 : com.stasdoto.airdefense.vehicle.VehicleType.M113);
			kit.add(com.stasdoto.airdefense.vehicle.VehicleType.SUPPLY_TRUCK);
		}
		int left = men;
		BlockPos flag = target.flag;
		for (int k = 0; k < kit.size(); k++) {
			com.stasdoto.airdefense.vehicle.VehicleType type = kit.get(k);
			double s = Math.min(ap.length() - 2, back + 16 * k);
			double[] pt = ap.at(s);
			double[] ahead = ap.at(Math.max(0, s - 6));
			BlockPos at = BlockPos.containing(pt[0], 0, pt[1]);
			if (!level.isLoaded(at)) {
				// Out on the road beyond sight: load its ground now (and keep it running) rather than skip the vehicle.
				level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.ENDER_PEARL, net.minecraft.world.level.ChunkPos.containing(at), 2);
				level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			float yaw = (float) Math.toDegrees(Math.atan2(-(ahead[0] - pt[0]), ahead[1] - pt[1]));
			com.stasdoto.airdefense.vehicle.VehicleEntity v = com.stasdoto.airdefense.vehicle.VehicleEntity.spawn(level, type,
					new Vec3(pt[0], y, pt[1]), yaw);
			v.country = ai.id;
			v.home = home == null ? -1 : home.id;
			v.garrison = true;
			int seats = type == com.stasdoto.airdefense.vehicle.VehicleType.SUPPLY_TRUCK ? 10 : type.isArmed() && type.weapon.cannon() ? 0
					: type == com.stasdoto.airdefense.vehicle.VehicleType.MAXXPRO ? 6 : 8;
			int n = Math.min(left, seats);
			v.troops = n;
			v.troopTarget = flag;
			left -= n;
			// From where it stands, along the road behind the ones in front.
			List<Vec3> mine = new ArrayList<>();
			mine.add(new Vec3(ahead[0], 0, ahead[1]));
			for (Vec3 w : waypoints) {
				mine.add(w);
			}
			v.drive(mine, type.isArmed() && type.weapon.cannon() ? 0.65f : 0.7f);
			out.add(v);
		}
		if (!out.isEmpty()) {
			squads++;
			Country owner = p.country(target.country);
			if (owner != null) {
				tell(level, owner, Component.translatable("nation.airdefense.war.column", ai.name, men, out.size(), target.name));
			}
			AirDefense.LOGGER.info("[airdefense] {} sends a column ({} vehicles, {} men) against {}", ai.name, out.size(), men, target.name);
		}
		return out;
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

	/**
	 * Wars of the world's own countries among themselves (1.25): now and then two neighbours fall out (their towns
	 * fire missiles at each other from then on, see Arsenals); after two or three days they make peace.
	 */
	private static void worldWars(ServerLevel level, Politics p, Random r) {
		long now = level.getGameTime();
		List<Country> ai = new ArrayList<>();
		for (Country c : p.countries.values()) {
			if (c.owner == null && !c.cityState && p.settlements.get(c.capital) != null) {
				ai.add(c);
			}
		}
		for (Country a : ai) {
			for (Country b : ai) {
				if (a.id >= b.id) {
					continue;
				}
				if (a.atWarWith(b.id)) {
					Long since = a.warSince.get(b.id);
					if (since != null && now - since > 48000 + r.nextInt(24000)) {
						peace(level, p, a, b);
					}
					continue;
				}
				Long peace = PEACE_SINCE.get(pair(a.id, b.id));
				if (peace != null && now - peace < 72000) {
					continue;
				}
				Settlement ca = p.settlements.get(a.capital);
				Settlement cb = p.settlements.get(b.capital);
				if (ca.center.distSqr(cb.center) < 3200.0 * 3200.0 && r.nextInt(100) < 4) {
					declare(level, p, a, b, Component.translatable("nation.airdefense.war.why_border"));
					for (ServerPlayer pl : level.getServer().getPlayerList().getPlayers()) {
						pl.sendSystemMessage(Component.translatable("nation.airdefense.war.news", a.name, b.name));
					}
				}
			}
		}
	}

	/** Once a day a neighbour may fall upon a country that has grown big (three villages or more). */
	private static void neighbours(ServerLevel level, Politics p) {
		Random r = new Random();
		long now = level.getGameTime();
		worldWars(level, p, r);
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

	/** Whether this country's forces should fight this entity: the people and vehicles of a country it is at war with. */
	public static boolean hostile(ServerLevel level, int country, net.minecraft.world.entity.Entity e) {
		Politics p = Politics.get(level.getServer());
		Country c = p.country(country);
		if (c == null) {
			return false;
		}
		if (e instanceof ServerPlayer pl) {
			if (pl.isSpectator() || pl.getAbilities().instabuild) {
				return false;
			}
			Country pc = p.countryOwnedBy(pl.getUUID());
			return pc != null && c.atWarWith(pc.id) || c.wanted.contains(pl.getUUID());
		}
		if (e instanceof SoldierEntity s) {
			return s.country() >= 0 && s.country() != country && c.atWarWith(s.country());
		}
		if (e instanceof com.stasdoto.airdefense.vehicle.VehicleEntity v) {
			if (v.country >= 0) {
				return v.country != country && c.atWarWith(v.country);
			}
			return v.getControllingPassenger() != null && hostile(level, country, v.getControllingPassenger());
		}
		return false;
	}

	private static void tell(ServerLevel level, Country c, Component message) {
		if (c.owner != null && level.getServer().getPlayerList().getPlayer(c.owner) instanceof ServerPlayer owner) {
			owner.sendSystemMessage(message);
		}
	}
}
