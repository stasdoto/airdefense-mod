package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;

/**
 * The politics of the world, run on the server: villages are found as players come near them and get a name and a
 * political status (part of a country the world made up, a one-village city state, or independent); countries keep
 * guards at their villages' flags; bandits roam; players gain villages peacefully (respect + a charter bought from the
 * elder), by force (no guards left, hold the flag for 30 s) or, in creative, at once; owners call up soldiers and
 * send them around from the tablet map.
 */
public final class Nations {
	public static final int CAPTURE_SECONDS = 30;
	public static final int CHARTER_REPUTATION = 25;
	public static final int WANTED_REPUTATION = -40;
	/** Debug counters read by the automated test. */
	public static int discovered;
	public static int guardsSpawned;
	public static int banditsSpawned;
	public static int captures;

	private Nations() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() == Level.OVERWORLD) {
				tick(level);
			}
		});
		// Defending a village earns respect there.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player)) {
				return;
			}
			boolean bandit = entity instanceof SoldierEntity s && s.role() == SoldierEntity.BANDIT;
			if (!bandit && !(entity instanceof Enemy)) {
				return;
			}
			Politics p = Politics.get(level.getServer());
			Settlement s = p.settlementAt(entity.blockPosition());
			if (s != null) {
				s.bonus.merge(player.getUUID(), bandit ? 6 : 3, (a, b) -> Math.min(80, a + b));
				p.setDirty();
			}
		});
		NationNet.init();
	}

	/** For the tests: time spent in each part of the nations' tick (ns): found, discover, capture, maintain, bandits, economy, supply, unrest, war. */
	public static final long[] PROFILE = new long[9];

	private static void tick(ServerLevel level) {
		long t = level.getGameTime();
		Politics p = Politics.get(level.getServer());
		long t0 = System.nanoTime();
		if (t % 100 == 0) {
			raisePendingFlags(level, p);
			foundCities(level, p);
			long t1 = System.nanoTime();
			PROFILE[0] += t1 - t0;
			discover(level, p);
			t0 = System.nanoTime();
			PROFILE[1] += t0 - t1;
		}
		if (t % 20 == 7) {
			captureTick(level, p);
		}
		long t2 = System.nanoTime();
		PROFILE[2] += t2 - t0;
		if (t % 200 == 50) {
			maintain(level, p);
		}
		long t3 = System.nanoTime();
		PROFILE[3] += t3 - t2;
		if (t % 1200 == 300) {
			bandits(level, p);
		}
		long t4 = System.nanoTime();
		PROFILE[4] += t4 - t3;
		Economy.tick(level, p);
		long t5 = System.nanoTime();
		PROFILE[5] += t5 - t4;
		Supply.tick(level, p);
		long t6 = System.nanoTime();
		PROFILE[6] += t6 - t5;
		Unrest.tick(level, p);
		long t7 = System.nanoTime();
		PROFILE[7] += t7 - t6;
		War.tick(level, p);
		PROFILE[8] += System.nanoTime() - t7;
	}

	// ------------------------------------------------------------------------------------------------
	// Villages and their politics

	private static void discover(ServerLevel level, Politics p) {
		for (Villager v : level.getEntities(EntityTypeTest.forClass(Villager.class), v -> v.isAlive() && !v.isBaby())) {
			if (p.settlementAt(v.blockPosition()) != null) {
				continue;
			}
			// People of a planned city that stands here wait for their city (founded with all its buildings).
			Cities.City planned = Cities.plannedCityAt(level.getSeed(), v.getBlockX(), v.getBlockZ(), Cities.MARGIN);
			if (planned != null && level.isLoaded(planned.bell()) && level.getBlockState(planned.bell()).is(Blocks.BELL)) {
				continue;
			}
			Hamlets.Hamlet hamlet = Cities.plannedHamletAt(level.getSeed(), v.getBlockX(), v.getBlockZ(), 60);
			if (hamlet != null && level.isLoaded(hamlet.bell()) && level.getBlockState(hamlet.bell()).is(Blocks.BELL)) {
				continue;
			}
			// The town square: the bell nearest to this villager, or where he stands.
			BlockPos center = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), v.blockPosition(), 48, PoiManager.Occupancy.ANY)
					.orElse(v.blockPosition());
			// One village, one settlement: no new one right next to another (a big village has several bells).
			if (p.settlementAt(center) != null || !p.near(center, SPACING).isEmpty()) {
				continue;
			}
			found(level, p, center);
		}
	}

	public static int citiesFounded;

	/** Planned cities near the players whose central square stands (built by the world generator) join the map. */
	private static void foundCities(ServerLevel level, Politics p) {
		if (level.players().isEmpty()) {
			return;
		}
		Cities.Terrain t = Cities.terrain(level);
		long seed = level.getSeed();
		Set<Long> have = new HashSet<>();
		Set<Long> haveHamlets = new HashSet<>();
		for (Settlement s : p.settlements.values()) {
			if (s.city >= 0) {
				have.add(s.city);
			}
			if (s.hamlet >= 0) {
				haveHamlets.add(s.hamlet);
			}
		}
		for (ServerPlayer pl : level.players()) {
			int px = pl.getBlockX();
			int pz = pl.getBlockZ();
			for (int cx = Math.floorDiv(px - 300, Cities.CELL); cx <= Math.floorDiv(px + 300, Cities.CELL); cx++) {
				for (int cz = Math.floorDiv(pz - 300, Cities.CELL); cz <= Math.floorDiv(pz + 300, Cities.CELL); cz++) {
					for (Cities.City c : Cities.cities(seed, t, cx, cz)) {
						for (Hamlets.Hamlet h : c.hamlets(seed, t)) {
							BlockPos hb = h.bell();
							if (!haveHamlets.contains(h.key()) && level.isLoaded(hb) && level.getBlockState(hb).is(Blocks.BELL)) {
								foundHamlet(level, p, h);
								haveHamlets.add(h.key());
							}
						}
						BlockPos bell = c.bell();
						if (have.contains(c.key()) || !level.isLoaded(bell) || !level.getBlockState(bell).is(Blocks.BELL)) {
							continue;
						}
						foundCity(level, p, c);
						have.add(c.key());
					}
				}
			}
		}
	}

	/** A planned city on the political map: its buildings, its people, its country (made by the first of its towns found). */
	public static Settlement foundCity(ServerLevel level, Politics p, Cities.City c) {
		Random r = new Random(c.seed ^ 0x5EED1234L);
		Set<String> names = new HashSet<>();
		p.settlements.values().forEach(s -> names.add(s.name));
		int id = p.newId();
		BlockPos bell = c.bell();
		Settlement s = new Settlement(id, Names.village(r, names), bell, flagAt(level, p, id, bell), -1, Optional.empty(), 0,
				Map.of(), List.of(), List.of());
		s.city = c.key();
		s.radius = c.radius();
		s.citizens = c.citizens;
		s.capitalCity = c.capital();
		for (Building b : c.buildings()) {
			Building nb = new Building(p.newId(), b.type, b.origin, b.facing, true);
			nb.variant = b.variant;
			nb.done = true;
			s.eco.buildings.add(nb);
		}
		// The depot out of town by the highway (1.25).
		Depots.Depot depot = c.depot(level.getSeed(), Cities.terrain(level));
		if (depot != null) {
			for (Building b : depot.buildings) {
				Building nb = new Building(p.newId(), b.type, b.origin, b.facing, true);
				nb.variant = b.variant;
				nb.done = true;
				s.eco.buildings.add(nb);
			}
		}
		p.settlements.put(id, s);
		stockUp(s, c, r);
		long cell = Cities.cellKey(c.cx, c.cz);
		Country country = null;
		for (Country k : p.countries.values()) {
			if (k.cell == cell) {
				country = k;
			}
		}
		if (country == null) {
			country = newCountry(p, r, null, "", id, false);
			country.color = c.color;
			country.cell = cell;
		}
		if (c.capital()) {
			country.capital = id;
		}
		s.country = country.id;
		placeFlag(level, p, s);
		p.setDirty();
		discovered++;
		citiesFounded++;
		com.stasdoto.airdefense.siren.Sirens.planCity(level, c);
		AirDefense.LOGGER.info("[airdefense] city {} ({}, {} people, {} buildings) at {} -> {}{}", s.name, c.size, c.citizens, s.eco.buildings.size(),
				bell.toShortString(), country.name, c.capital() ? " (capital)" : "");
		return s;
	}

	/** A new city's stores: some of everything, little of what it cannot make itself. */
	static void stockUp(Settlement s, Cities.City c, Random r) {
		VillageEconomy e = s.eco;
		BuildingType lack = c.lack();
		e.stock[VillageEconomy.WOOD] = 120 + r.nextInt(120);
		e.stock[VillageEconomy.STONE] = 120 + r.nextInt(120);
		e.stock[VillageEconomy.IRON] = 100 + r.nextInt(120);
		e.stock[VillageEconomy.OIL] = lack == BuildingType.OIL_WELL ? 0 : 1500;
		e.stock[VillageEconomy.FUEL] = lack == BuildingType.REFINERY ? 400 : 3000;
		e.stock[VillageEconomy.AMMO] = 150;
		e.stock[VillageEconomy.FOOD] = Supply.foodNeed(s) * 25;
		e.stock[VillageEconomy.ARMS] = lack == BuildingType.ARMS_FACTORY ? 0 : 40;
		for (int k = 0; k < VillageEconomy.KINDS; k++) {
			e.stock[k] = Math.min(e.stock[k], e.capOf(k));
		}
	}

	public static int hamletsFounded;

	/** A hamlet round a planned city: a village of the city's country, with its houses and farm. */
	public static Settlement foundHamlet(ServerLevel level, Politics p, Hamlets.Hamlet h) {
		Random r = new Random(h.seed() ^ 0x5EED4321L);
		Set<String> names = new HashSet<>();
		p.settlements.values().forEach(s -> names.add(s.name));
		int id = p.newId();
		BlockPos bell = h.bell();
		Settlement s = new Settlement(id, Names.village(r, names), bell, flagAt(level, p, id, bell), -1, Optional.empty(), 0,
				Map.of(), List.of(), List.of());
		s.hamlet = h.key();
		s.radius = 56;
		for (Building b : h.buildings) {
			Building nb = new Building(p.newId(), b.type, b.origin, b.facing, true);
			nb.variant = b.variant;
			nb.done = true;
			s.eco.buildings.add(nb);
		}
		s.eco.stock[VillageEconomy.FOOD] = 60 + r.nextInt(40);
		p.settlements.put(id, s);
		Cities.City c = h.city;
		long cell = Cities.cellKey(c.cx, c.cz);
		Country country = null;
		for (Country k : p.countries.values()) {
			if (k.cell == cell) {
				country = k;
			}
		}
		if (country == null) {
			country = newCountry(p, r, null, "", id, false);
			country.color = c.color;
			country.cell = cell;
		}
		s.country = country.id;
		placeFlag(level, p, s);
		p.setDirty();
		discovered++;
		hamletsFounded++;
		com.stasdoto.airdefense.siren.Sirens.planHamlet(level, h);
		AirDefense.LOGGER.info("[airdefense] hamlet {} ({} buildings) at {} -> {}", s.name, s.eco.buildings.size(), bell.toShortString(), country.name);
		return s;
	}

	/** A new village on the political map, with its status decided by a roll that is fixed for this world and place. */
	public static Settlement found(ServerLevel level, Politics p, BlockPos center) {
		Random r = new Random(level.getSeed() ^ center.asLong() * 0x9E3779B97F4A7C15L);
		Set<String> names = new HashSet<>();
		p.settlements.values().forEach(s -> names.add(s.name));
		int id = p.newId();
		Settlement s = new Settlement(id, Names.village(r, names), center, flagSpot(level, center), -1, Optional.empty(), 0,
				Map.of(), List.of(), List.of());
		p.settlements.put(id, s);
		Country near = null;
		double nearD = 640 * 640;
		for (Country c : p.countries.values()) {
			Settlement cap = p.settlements.get(c.capital);
			if (c.owner == null && !c.cityState && cap != null && cap.center.distSqr(center) < nearD) {
				near = c;
				nearD = cap.center.distSqr(center);
			}
		}
		if (near != null && r.nextInt(100) < 55) {
			s.country = near.id;
		} else {
			int roll = r.nextInt(100);
			if (roll < 35) {
				s.country = newCountry(p, r, null, "", id, false).id;
			} else if (roll < 55) {
				s.country = newCountry(p, r, null, "", id, true).id;
			}
		}
		placeFlag(level, p, s);
		p.setDirty();
		discovered++;
		com.stasdoto.airdefense.siren.Sirens.planVillage(level, center);
		AirDefense.LOGGER.info("[airdefense] village {} at {} -> {}", s.name, center.toShortString(),
				s.country < 0 ? "independent" : p.country(s.country).name);
		return s;
	}

	private static Country newCountry(Politics p, Random r, @Nullable Player owner, String ownerName, int capital, boolean cityState) {
		Set<String> names = new HashSet<>();
		Set<Integer> colors = new HashSet<>();
		for (Country c : p.countries.values()) {
			names.add(c.name);
			colors.add(c.color);
		}
		int color = -1;
		for (int i = 0; i < 30 && color < 0; i++) {
			int c = 1 + r.nextInt(15);
			if (!colors.contains(c)) {
				color = c;
			}
		}
		if (color < 0) {
			color = 1 + r.nextInt(15);
		}
		Country c = new Country(p.newId(), Names.country(r, names), color, Optional.ofNullable(owner == null ? null : owner.getUUID()),
				ownerName, capital, cityState, List.of());
		p.countries.put(c.id, c);
		return c;
	}

	/** Makes this village the capital of a new country of the world's own (or a city state). */
	public static Country makeCountry(ServerLevel level, Settlement s, boolean cityState) {
		Politics p = Politics.get(level.getServer());
		Country c = newCountry(p, new Random(s.center.asLong()), null, "", s.id, cityState);
		s.country = c.id;
		placeFlag(level, p, s);
		p.setDirty();
		return c;
	}

	/** The player's own country (made on his first village). */
	public static Country countryOf(ServerLevel level, Politics p, ServerPlayer player, boolean create) {
		Country c = p.countryOwnedBy(player.getUUID());
		if (c == null && create) {
			c = newCountry(p, new Random(player.getUUID().getLeastSignificantBits()), player, player.getName().getString(), -1, false);
			c.name = Component.translatable("nation.airdefense.player_country", player.getName().getString()).getString();
			p.setDirty();
		}
		return c;
	}

	/** A free spot on the ground right next to the town square for the flag. */
	/** Where a new town's flag goes: next to the bell if it is loaded; else later, when somebody comes (founded from afar). */
	private static BlockPos flagAt(ServerLevel level, Politics p, int id, BlockPos bell) {
		if (level.isLoaded(bell)) {
			return flagSpot(level, bell);
		}
		p.flagsPending.add(id);
		return bell.above(2);
	}

	public static boolean isFounded(Politics p, Cities.City c) {
		for (Settlement s : p.settlements.values()) {
			if (s.city == c.key()) {
				return true;
			}
		}
		return false;
	}

	public static boolean isFounded(Politics p, Hamlets.Hamlet h) {
		for (Settlement s : p.settlements.values()) {
			if (s.hamlet == h.key()) {
				return true;
			}
		}
		return false;
	}

	/** Towns founded from afar get their flag once their square is loaded. */
	private static void raisePendingFlags(ServerLevel level, Politics p) {
		if (p.flagsPending.isEmpty()) {
			return;
		}
		var it = p.flagsPending.iterator();
		while (it.hasNext()) {
			Settlement s = p.settlements.get(it.next());
			if (s == null) {
				it.remove();
				continue;
			}
			if (level.isLoaded(s.center) && level.getBlockState(s.center).is(Blocks.BELL)) {
				s.flag = flagSpot(level, s.center);
				placeFlag(level, p, s);
				it.remove();
				p.setDirty();
			}
		}
	}

	private static BlockPos flagSpot(ServerLevel level, BlockPos center) {
		for (int ring = 2; ring <= 5; ring++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int x = center.getX() + d.getStepX() * ring;
				int z = center.getZ() + d.getStepZ() * ring;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos pos = new BlockPos(x, y, z);
				if (Math.abs(y - center.getY()) <= 3 && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
						&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
					return pos;
				}
			}
		}
		return center.above(2);
	}

	/** A banner in the owner's colour (white for an independent village). */
	public static void placeFlag(ServerLevel level, Politics p, Settlement s) {
		if (!level.isLoaded(s.flag)) {
			return;
		}
		Country c = p.country(s.country);
		DyeColor dye = c == null ? DyeColor.WHITE : c.dye();
		BlockState want = Blocks.BANNER.pick(dye).defaultBlockState().setValue(BannerBlock.ROTATION, 4);
		BlockState now = level.getBlockState(s.flag);
		if (now.getBlock() != want.getBlock()) {
			if (!now.isAir() && !(now.getBlock() instanceof BannerBlock)) {
				s.flag = flagSpot(level, s.center);
			}
			level.setBlock(s.flag, want, 3);
		}
	}

	/** Villages closer than this are one village. */
	public static final int SPACING = 150;
	public static int merged;

	/**
	 * Two settlements found in what is really one village (older worlds): the empty one (no player's, nothing built,
	 * no stock) is dropped, with its flag.
	 */
	private static void mergeTwins(ServerLevel level, Politics p) {
		List<Settlement> all = new ArrayList<>(p.settlements.values());
		for (int i = 0; i < all.size(); i++) {
			Settlement a = all.get(i);
			if (!p.settlements.containsKey(a.id)) {
				continue;
			}
			for (int j = i + 1; j < all.size(); j++) {
				Settlement b = all.get(j);
				if (!p.settlements.containsKey(b.id) || a.center.distSqr(b.center) > 110 * 110) {
					continue;
				}
				Settlement drop = droppable(p, b) ? b : droppable(p, a) ? a : null;
				if (drop == null) {
					continue;
				}
				remove(level, p, drop);
				if (drop == a) {
					break;
				}
			}
		}
	}

	private static boolean droppable(Politics p, Settlement s) {
		Country c = p.country(s.country);
		return (c == null || c.owner == null) && s.eco.isEmpty() && s.soldiers.isEmpty();
	}

	private static void remove(ServerLevel level, Politics p, Settlement s) {
		p.settlements.remove(s.id);
		if (level.isLoaded(s.flag) && level.getBlockState(s.flag).getBlock() instanceof BannerBlock) {
			level.removeBlock(s.flag, false);
		}
		Country c = p.country(s.country);
		if (c != null && c.capital == s.id) {
			List<Settlement> rest = p.settlementsOf(c.id);
			if (rest.isEmpty()) {
				p.countries.remove(c.id);
				for (Country o : p.countries.values()) {
					o.wars.remove(c.id);
					o.warSince.remove(c.id);
					o.warScore.remove(c.id);
				}
			} else {
				c.capital = rest.getFirst().id;
			}
		}
		merged++;
		p.setDirty();
		AirDefense.LOGGER.info("[airdefense] village {} merged into its neighbour", s.name);
	}

	private static void maintain(ServerLevel level, Politics p) {
		mergeTwins(level, p);
		for (Settlement s : p.settlements.values()) {
			if (!level.isLoaded(s.center)) {
				continue;
			}
			List<Villager> villagers = villagers(level, s);
			s.population = villagers.size() + Economy.workers(level, s).size();
			Villager elder = s.elder == null ? null : level.getEntity(s.elder) instanceof Villager v && v.isAlive() ? v : null;
			if (elder == null && !villagers.isEmpty()) {
				for (Villager v : villagers) {
					if (!v.isBaby()) {
						elder = v;
						break;
					}
				}
				if (elder != null) {
					s.elder = elder.getUUID();
					elder.setCustomName(Component.translatable("nation.airdefense.elder", s.name));
					p.setDirty();
				}
			}
			List<SoldierEntity> guards = guards(level, s);
			s.guardsAlive = guards.size();
			Country c = p.country(s.country);
			int want = c != null ? Math.max(1, Math.min(5, s.population / 3)) + (c.cityState ? 1 : 0) : s.population >= 5 ? 1 : 0;
			if (guards.size() < want && s.captureTicks == 0 && !s.riot && s.aiCaptureTicks == 0 && level.getNearestPlayer(s.flag.getX(), s.flag.getY(), s.flag.getZ(), 12, false) == null
					&& level.getNearestPlayer(s.flag.getX(), s.flag.getY(), s.flag.getZ(), 160, false) != null) {
				spawnGuard(level, s, c);
			}
			placeFlag(level, p, s);
		}
	}

	public static List<Villager> villagers(ServerLevel level, Settlement s) {
		return level.getEntitiesOfClass(Villager.class, new AABB(s.center).inflate(s.radius, 32, s.radius),
				v -> v.isAlive() && s.contains(v.blockPosition()));
	}

	public static List<SoldierEntity> guards(ServerLevel level, Settlement s) {
		return level.getEntitiesOfClass(SoldierEntity.class, new AABB(s.center).inflate(s.radius + 16, 32, s.radius + 16),
				g -> g.isAlive() && g.role() == SoldierEntity.GUARD && g.home() == s.id);
	}

	public static List<SoldierEntity> soldiers(ServerLevel level, Settlement s) {
		List<SoldierEntity> out = new ArrayList<>();
		for (UUID id : s.soldiers) {
			if (level.getEntity(id) instanceof SoldierEntity e && e.isAlive()) {
				out.add(e);
			}
		}
		return out;
	}

	public static SoldierEntity spawnGuard(ServerLevel level, Settlement s, @Nullable Country c) {
		Random r = new Random();
		int x = s.flag.getX() + r.nextInt(5) - 2;
		int z = s.flag.getZ() + r.nextInt(5) - 2;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		SoldierEntity g = SoldierEntity.create(level, SoldierEntity.GUARD, c == null ? -1 : c.id, c == null ? -1 : c.color, s.id,
				new Vec3(x + 0.5, y, z + 0.5), r.nextInt());
		g.setHomeTo(s.flag, 14);
		level.addFreshEntity(g);
		guardsSpawned++;
		return g;
	}

	// ------------------------------------------------------------------------------------------------
	// Respect, charters, capture

	/** How much the village thinks of this player: what its villagers say about him, plus what he did for it. */
	public static int reputation(ServerLevel level, Settlement s, Player player) {
		int sum = 0;
		for (Villager v : villagers(level, s)) {
			sum += v.getPlayerReputation(player);
		}
		return Math.max(-100, Math.min(100, sum)) + s.bonus.getOrDefault(player.getUUID(), 0);
	}

	public static int charterPrice(Politics p, Settlement s) {
		Country c = p.country(s.country);
		int base = 10 + 3 * s.population;
		return c == null ? base : base * 2;
	}

	/** Why the player cannot buy the charter now (null = he can). */
	@Nullable
	public static Component charterProblem(ServerLevel level, Politics p, Settlement s, ServerPlayer player) {
		Country c = p.country(s.country);
		if (c != null && player.getUUID().equals(c.owner)) {
			return Component.translatable("nation.airdefense.already_yours");
		}
		if (c != null && c.owner != null) {
			return Component.translatable("nation.airdefense.other_player", c.ownerName);
		}
		if (reputation(level, s, player) < CHARTER_REPUTATION) {
			return Component.translatable("nation.airdefense.need_respect", CHARTER_REPUTATION);
		}
		if (!player.getAbilities().instabuild && player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) < charterPrice(p, s)) {
			return Component.translatable("nation.airdefense.need_emeralds", charterPrice(p, s));
		}
		return null;
	}

	public static boolean buyCharter(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		s.population = villagers(level, s).size();
		Component problem = charterProblem(level, p, s, player);
		if (problem != null) {
			player.sendOverlayMessage(problem);
			return false;
		}
		if (!player.getAbilities().instabuild) {
			int left = charterPrice(p, s);
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				var stack = player.getInventory().getItem(i);
				if (stack.is(net.minecraft.world.item.Items.EMERALD)) {
					int k = Math.min(left, stack.getCount());
					stack.shrink(k);
					left -= k;
				}
			}
		}
		transfer(level, p, s, countryOf(level, p, player, true));
		player.sendSystemMessage(Component.translatable("nation.airdefense.charter_bought", s.name));
		level.playSound(null, s.flag, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		return true;
	}

	/** Creative: the village is yours at once. */
	public static void takeOver(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		transfer(level, p, s, countryOf(level, p, player, true));
		player.sendSystemMessage(Component.translatable("nation.airdefense.taken", s.name));
	}

	/** The village changes hands: new flag, new guards in time; the old country loses it. */
	public static void transfer(ServerLevel level, Politics p, Settlement s, Country to) {
		Country from = p.country(s.country);
		if (s.riot) {
			for (SoldierEntity r : Unrest.rebels(level, s)) {
				r.demobilize(level);
			}
			s.riot = false;
			s.riotTicks = 0;
		}
		s.country = to.id;
		s.captureTicks = 0;
		s.capturer = null;
		s.capturedAt = -1;
		if (to.capital < 0 || p.settlements.get(to.capital) == null) {
			to.capital = s.id;
		}
		// Guards of the old owner that are still around lay down their arms and leave.
		for (SoldierEntity g : guards(level, s)) {
			g.discard();
		}
		if (from != null) {
			List<Settlement> left = p.settlementsOf(from.id);
			if (from.capital == s.id) {
				from.capital = left.isEmpty() ? -1 : left.getFirst().id;
			}
			if (left.isEmpty() && from.owner == null) {
				p.countries.remove(from.id);
				// A country that is gone is at war with nobody.
				for (Country c : p.countries.values()) {
					c.wars.remove(from.id);
					c.warSince.remove(from.id);
					c.warScore.remove(from.id);
				}
			}
		}
		placeFlag(level, p, s);
		p.setDirty();
		captures++;
	}

	/** Every second: someone standing at a village's flag with no guards left takes it after 30 s. */
	private static void captureTick(ServerLevel level, Politics p) {
		for (Settlement s : p.settlements.values()) {
			if (!level.isLoaded(s.flag)) {
				continue;
			}
			Player at = level.getNearestPlayer(s.flag.getX() + 0.5, s.flag.getY(), s.flag.getZ() + 0.5, 5, false);
			Country owner = p.country(s.country);
			boolean mine = at != null && owner != null && at.getUUID().equals(owner.owner);
			if (!(at instanceof ServerPlayer player) || at.isSpectator() || mine) {
				if (s.captureTicks > 0) {
					s.captureTicks = Math.max(0, s.captureTicks - 2);
				}
				continue;
			}
			int guards = guards(level, s).size();
			s.guardsAlive = guards;
			if (guards > 0) {
				if (level.getGameTime() % 100 == 7) {
					player.sendOverlayMessage(Component.translatable("nation.airdefense.capture_guarded", guards));
				}
				continue;
			}
			if (!player.getUUID().equals(s.capturer)) {
				s.capturer = player.getUUID();
				s.captureTicks = 0;
			}
			s.captureTicks++;
			player.sendOverlayMessage(Component.translatable("nation.airdefense.capturing", s.name, s.captureTicks, CAPTURE_SECONDS));
			if (s.captureTicks >= CAPTURE_SECONDS) {
				Country winner = countryOf(level, p, player, true);
				// Taking a village of a made-up country by force means war with it (if there is none yet).
				if (owner != null && owner.owner == null && !owner.atWarWith(winner.id)) {
					War.declare(level, p, owner, winner, Component.translatable("nation.airdefense.war.why_capture", s.name));
				}
				War.scored(p, winner, owner);
				transfer(level, p, s, winner);
				// A village taken by force does not love its new ruler at first.
				s.capturedAt = level.getGameTime();
				player.sendSystemMessage(Component.translatable("nation.airdefense.captured", s.name));
				level.playSound(null, s.flag, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 2f, 1f);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Who fights whom

	public static boolean atWar(Level level, int a, int b) {
		if (a < 0 || b < 0 || a == b || !(level instanceof ServerLevel sl)) {
			return false;
		}
		Politics p = Politics.get(sl.getServer());
		Country ca = p.country(a);
		Country cb = p.country(b);
		return ca != null && cb != null && (ca.atWarWith(b) || cb.atWarWith(a));
	}

	/** Do this country's (or village's) guards shoot at this player? */
	public static boolean hostileToPlayer(ServerLevel level, SoldierEntity soldier, Player player) {
		Politics p = Politics.get(level.getServer());
		Country mine = p.countryOwnedBy(player.getUUID());
		if (mine != null && atWar(level, soldier.country(), mine.id)) {
			return true;
		}
		Country theirs = p.country(soldier.country());
		if (theirs != null && theirs.wanted.contains(player.getUUID())) {
			return true;
		}
		Settlement home = p.settlements.get(soldier.home());
		return home != null && soldier.role() == SoldierEntity.GUARD && home.contains(player.blockPosition())
				&& reputation(level, home, player) <= WANTED_REPUTATION;
	}

	/** A player shot at a country's man: that country remembers him (its guards shoot on sight). */
	public static void offended(ServerLevel level, SoldierEntity soldier, Player player) {
		if (soldier.role() == SoldierEntity.BANDIT || soldier.role() == SoldierEntity.REBEL) {
			return;
		}
		Politics p = Politics.get(level.getServer());
		Country c = p.country(soldier.country());
		if (c != null && !player.getUUID().equals(c.owner) && c.wanted.add(player.getUUID())) {
			p.setDirty();
		}
		Settlement home = p.settlements.get(soldier.home());
		if (home != null) {
			home.bonus.merge(player.getUUID(), -15, Integer::sum);
			p.setDirty();
		}
	}

	public static void soldierDied(ServerLevel level, SoldierEntity soldier) {
		Politics p = Politics.get(level.getServer());
		Settlement home = p.settlements.get(soldier.home());
		if (home != null && home.soldiers.remove(soldier.getUUID())) {
			p.setDirty();
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Army: calling villagers up, sending them around, letting them go home

	/** How many more of this village's people can be called up (at least two stay at home). */
	public static int mobilizable(ServerLevel level, Settlement s) {
		int adults = 0;
		for (Villager v : villagers(level, s)) {
			if (!v.isBaby()) {
				adults++;
			}
		}
		// Half the grown-ups (four more for every barracks), and at least two stay at home.
		return Math.max(0, Math.min(adults - 2, (adults + s.soldiers.size()) / 2 - s.soldiers.size() + Economy.barracksBonus(s)));
	}

	/** Soldiers called up with weapons from the arsenal (for the tests). */
	public static int armed;

	public static int mobilize(ServerLevel level, ServerPlayer player, Settlement s, int count) {
		Politics p = Politics.get(level.getServer());
		Country c = p.country(s.country);
		if (c == null || !player.getUUID().equals(c.owner)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.not_yours"));
			return 0;
		}
		int n = Math.min(count, mobilizable(level, s));
		int done = 0;
		for (Villager v : villagers(level, s)) {
			if (done >= n) {
				break;
			}
			if (v.isBaby() || v.getUUID().equals(s.elder)) {
				continue;
			}
			SoldierEntity e = SoldierEntity.create(level, SoldierEntity.SOLDIER, c.id, c.color, s.id, v.position(), lookOf(v.getUUID()));
			e.setOrigin(v.getVillagerData(), v.getUUID());
			e.setVillagerTag(WorkerEntity.save(level, v));
			e.setHomeTo(s.flag, 16);
			if (s.eco.stock[VillageEconomy.ARMS] > 0) {
				s.eco.stock[VillageEconomy.ARMS]--;
				e.issueArms(level.getRandom().nextInt(100));
				armed++;
			}
			v.discard();
			level.addFreshEntity(e);
			s.soldiers.add(e.getUUID());
			done++;
		}
		p.setDirty();
		player.sendOverlayMessage(Component.translatable("nation.airdefense.mobilized", done, s.name));
		return done;
	}

	/** The face a villager has (and keeps as a soldier): from his UUID. */
	public static int lookOf(UUID id) {
		return (int) (id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ (id.getLeastSignificantBits() >>> 32));
	}

	public static int order(ServerLevel level, ServerPlayer player, Settlement s, @Nullable BlockPos target) {
		Politics p = Politics.get(level.getServer());
		Country c = p.country(s.country);
		if (c == null || !player.getUUID().equals(c.owner)) {
			return 0;
		}
		int n = 0;
		for (SoldierEntity e : soldiers(level, s)) {
			e.orderTo(target == null ? s.flag : target);
			n++;
		}
		player.sendOverlayMessage(Component.translatable(target == null ? "nation.airdefense.ordered_home" : "nation.airdefense.ordered", n));
		return n;
	}

	public static int demobilize(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		Country c = p.country(s.country);
		if (c == null || !player.getUUID().equals(c.owner)) {
			return 0;
		}
		int n = 0;
		for (SoldierEntity e : soldiers(level, s)) {
			s.soldiers.remove(e.getUUID());
			e.demobilize(level);
			n++;
		}
		p.setDirty();
		player.sendOverlayMessage(Component.translatable("nation.airdefense.demobilized", n));
		return n;
	}

	// ------------------------------------------------------------------------------------------------
	// Bandits

	private static void bandits(ServerLevel level, Politics p) {
		Random r = new Random();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || r.nextInt(100) >= 4) {
				continue;
			}
			Settlement target = null;
			double best = 160 * 160;
			for (Settlement s : p.settlements.values()) {
				double d = s.center.distSqr(player.blockPosition());
				Country c = p.country(s.country);
				if (d < best && (c == null || !player.getUUID().equals(c.owner) || r.nextBoolean())) {
					best = d;
					target = s;
				}
			}
			if (target != null) {
				raid(level, target, 2 + r.nextInt(3));
			}
		}
	}

	/** A bandit gang turns up 50-70 blocks from the village and heads for it. */
	public static List<SoldierEntity> raid(ServerLevel level, Settlement target, int count) {
		Random r = new Random();
		List<SoldierEntity> gang = new ArrayList<>();
		double a = r.nextDouble() * Math.PI * 2;
		double d = 50 + r.nextDouble() * 20;
		for (int i = 0; i < count; i++) {
			int x = (int) Math.floor(target.center.getX() + Math.cos(a) * d + r.nextInt(5) - 2);
			int z = (int) Math.floor(target.center.getZ() + Math.sin(a) * d + r.nextInt(5) - 2);
			if (!level.isLoaded(new BlockPos(x, 64, z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			SoldierEntity b = SoldierEntity.create(level, SoldierEntity.BANDIT, -1, -1, -1, new Vec3(x + 0.5, y, z + 0.5), r.nextInt());
			b.orderTo(target.center);
			level.addFreshEntity(b);
			gang.add(b);
			banditsSpawned++;
		}
		if (!gang.isEmpty()) {
			AirDefense.LOGGER.info("[airdefense] {} bandits head for {}", gang.size(), target.name);
		}
		return gang;
	}
}
