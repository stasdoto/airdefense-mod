package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;
import com.stasdoto.airdefense.weapon.GunType;

/**
 * Every town's arsenal (1.25): its own air defence and missile launchers, kept in stores at its depot.
 *
 * <p>A village has a short-range air defence and a launcher, a town more, a capital long-range systems too (Russian
 * kit for the eastern countries, NATO kit for the western). The vehicles stand round the town while somebody is near
 * (and the air defence wakes up by itself when missiles fly at it); the rest of the time the arsenal stands for them.
 * Cities with an arms factory make missiles and drones from iron and fuel - slowly, and only up to what the depots
 * hold - and send them on to the country's other towns. In a war each country fires at the enemy's towns in range of
 * its launchers: real missiles where somebody can see them, worked out by chance where nobody is.
 */
public final class Arsenals extends SavedData {
	// ------------------------------------------------------------------------------------------------
	// Data

	public static final class Unit {
		public final VehicleType type;
		/** The vehicle while it stands in the world. */
		@Nullable
		public UUID entity;
		public boolean lost;
		/** Shots on board while it is only in the books (launchers: missiles on the rails). */
		public int ammo;

		Unit(VehicleType type, @Nullable UUID entity, boolean lost, int ammo) {
			this.type = type;
			this.entity = entity;
			this.lost = lost;
			this.ammo = ammo;
		}

		static final Codec<Unit> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("type").forGetter(u -> u.type.id),
				UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(u -> Optional.ofNullable(u.entity)),
				Codec.BOOL.optionalFieldOf("lost", false).forGetter(u -> u.lost),
				Codec.INT.optionalFieldOf("ammo", 0).forGetter(u -> u.ammo)
		).apply(i, (t, e, l, a) -> new Unit(VehicleType.byName(t), e.orElse(null), l, a)));
	}

	public static final class Arsenal {
		public final int town;
		public final List<Unit> units = new ArrayList<>();
		public final Map<MissileType, Integer> stock = new EnumMap<>(MissileType.class);
		/** What the town's factory is making (a missile type), and how far it has got (ticks). */
		@Nullable
		public MissileType making;
		public int progress;
		public long nextStrike;
		/** Its air defence is up because missiles are flying at it (game time until it stands down). */
		public long alertUntil;

		Arsenal(int town) {
			this.town = town;
		}

		public int stock(MissileType t) {
			return stock.getOrDefault(t, 0);
		}

		void add(MissileType t, int n) {
			stock.merge(t, n, Integer::sum);
			if (stock.get(t) <= 0) {
				stock.remove(t);
			}
		}

		static final Codec<Arsenal> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("town").forGetter(a -> a.town),
				Unit.CODEC.listOf().optionalFieldOf("units", List.of()).forGetter(a -> a.units),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stock", Map.of()).forGetter(a -> {
					Map<String, Integer> m = new HashMap<>();
					a.stock.forEach((k, v) -> m.put(k.name(), v));
					return m;
				}),
				Codec.STRING.optionalFieldOf("making", "").forGetter(a -> a.making == null ? "" : a.making.name()),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(a -> a.progress),
				Codec.LONG.optionalFieldOf("next_strike", 0L).forGetter(a -> a.nextStrike)
		).apply(i, (town, units, stock, making, progress, next) -> {
			Arsenal a = new Arsenal(town);
			a.units.addAll(units);
			stock.forEach((k, v) -> {
				try {
					a.stock.put(MissileType.valueOf(k), v);
				} catch (IllegalArgumentException ignored) {
				}
			});
			if (!making.isEmpty()) {
				try {
					a.making = MissileType.valueOf(making);
				} catch (IllegalArgumentException ignored) {
				}
			}
			a.progress = progress;
			a.nextStrike = next;
			return a;
		}));
	}

	public static final Codec<Arsenals> CODEC = RecordCodecBuilder.create(i -> i.group(
			Arsenal.CODEC.listOf().optionalFieldOf("arsenals", List.of()).forGetter(a -> new ArrayList<>(a.arsenals.values())),
			Delivery.CODEC.listOf().optionalFieldOf("deliveries", List.of()).forGetter(a -> a.deliveries)
	).apply(i, Arsenals::new));
	public static final SavedDataType<Arsenals> TYPE = new SavedDataType<>(AirDefense.id("arsenals"), Arsenals::new, CODEC, null);

	public final Map<Integer, Arsenal> arsenals = new HashMap<>();
	/** Missiles on their way from a factory town to another town of the country. */
	public final List<Delivery> deliveries = new ArrayList<>();

	public record Delivery(long id, int to, MissileType type, int count, long arrives) {
		static final Codec<Delivery> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("id", 0L).forGetter(Delivery::id),
				Codec.INT.fieldOf("to").forGetter(Delivery::to),
				Codec.STRING.fieldOf("type").forGetter(d -> d.type.name()),
				Codec.INT.fieldOf("count").forGetter(Delivery::count),
				Codec.LONG.fieldOf("arrives").forGetter(Delivery::arrives)
		).apply(i, (id, to, t, n, at) -> new Delivery(id, to, MissileType.valueOf(t), n, at)));
	}

	/** A lorry sets off this long (ticks) before its load is due, from 200 blocks out on the road into town. */
	private static final long SUPPLY_LEAD = 900;
	/** Deliveries whose lorry is on the road now (not saved: after a restart the load just arrives). */
	private final java.util.Set<Long> shown = new java.util.HashSet<>();
	/** For the tests: lorries sent, loads they brought in, loads lost with their lorry. */
	public static int lorries;
	public static int lorryLoads;
	public static int lorryLosses;
	public static int lorryArrivals;

	public Arsenals() {
		this(List.of(), List.of());
	}

	private Arsenals(List<Arsenal> list, List<Delivery> deliveries) {
		for (Arsenal a : list) {
			arsenals.put(a.town, a);
		}
		this.deliveries.addAll(deliveries);
	}

	public static Arsenals get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** For the tests: units put in the world, strikes fired, missiles made, strikes worked out by chance. */
	public static int spawned;
	public static int strikes;
	public static int made;
	public static int remoteStrikes;
	public static int remoteHits;

	// ------------------------------------------------------------------------------------------------
	// What a town has

	/** The side a country's kit comes from. */
	static boolean east(int country) {
		return com.stasdoto.airdefense.nation.SoldierEntity.bloc(country) == GunType.Bloc.EAST;
	}

	/** The town's arsenal (set up the first time it is asked for). */
	public Arsenal of(Politics p, Settlement s) {
		Arsenal a = arsenals.get(s.id);
		if (a == null) {
			a = new Arsenal(s.id);
			Country c = p.country(s.country);
			boolean east = east(s.country);
			boolean capital = c != null && c.capital == s.id && s.city >= 0;
			List<VehicleType> kit = new ArrayList<>();
			Random r = new Random(s.id * 7919L);
			if (s.city >= 0 && capital) {
				kit.addAll(east ? List.of(VehicleType.S300, VehicleType.BUK, VehicleType.PANTSIR, VehicleType.TOR)
						: List.of(VehicleType.PATRIOT, VehicleType.IRIS_T, VehicleType.NASAMS, VehicleType.GEPARD));
				// A radar station: the capital's batteries see further and are not fooled by decoys.
				kit.add(east ? VehicleType.ST68 : VehicleType.TRML4D);
				kit.addAll(east ? List.of(VehicleType.ISKANDER, VehicleType.SHAHED, VehicleType.SHAHED) : List.of(VehicleType.HIMARS, VehicleType.HIMARS));
			} else if (s.city >= 0) {
				kit.add(east ? (r.nextBoolean() ? VehicleType.BUK : VehicleType.TOR) : (r.nextBoolean() ? VehicleType.NASAMS : VehicleType.IRIS_T));
				kit.add(east ? VehicleType.PANTSIR : VehicleType.GEPARD);
				kit.add(east ? (r.nextBoolean() ? VehicleType.ISKANDER : VehicleType.SHAHED) : VehicleType.HIMARS);
			} else {
				// A village: a short-range air defence and one launcher.
				kit.add(east ? (r.nextBoolean() ? VehicleType.STRELA10 : VehicleType.OSA) : (r.nextBoolean() ? VehicleType.AVENGER : VehicleType.GEPARD));
				kit.add(east ? VehicleType.SHAHED : VehicleType.HIMARS);
			}
			for (VehicleType t : kit) {
				a.units.add(new Unit(t, null, false, t.isLauncher() ? t.rails() : t.magazine()));
				MissileType m = missileOf(t);
				if (m != null) {
					a.add(m, t.isLauncher() ? t.rails() : t.magazine());
				}
			}
			arsenals.put(s.id, a);
			setDirty();
		}
		return a;
	}

	/** The missile (or drone) a vehicle fires; null for guns-only air defence. */
	@Nullable
	public static MissileType missileOf(VehicleType t) {
		if (t.launcher != null) {
			return t.launcher.missile;
		}
		if (t.defense != null) {
			return t.defense.interceptor;
		}
		return null;
	}

	/** How many of a missile the town can keep: three loads for each vehicle that fires it, more with depots. */
	int cap(Settlement s, Arsenal a, MissileType m) {
		int n = 0;
		for (Unit u : a.units) {
			if (!u.lost && missileOf(u.type) == m) {
				n += u.type.isLauncher() ? u.type.rails() : u.type.magazine();
			}
		}
		int depots = s.eco.count(BuildingType.DEPOT);
		return n * (3 + depots);
	}

	/** Whose side a town's vehicles are on: its country, or -3 for a free village. */
	static int side(Settlement s) {
		return s.country >= 0 ? s.country : -3;
	}

	// ------------------------------------------------------------------------------------------------
	// Ticking (once a second)

	public static void tick(ServerLevel level) {
		long now = level.getGameTime();
		// Called once a second (game time 13, 33, 53...): count in seconds.
		long sec = now / 20;
		Politics p = Politics.get(level.getServer());
		Arsenals a = get(level.getServer());
		for (Settlement s : p.settlements.values()) {
			Arsenal ar = a.of(p, s);
			boolean near = level.isLoaded(s.center) && level.getNearestPlayer(s.center.getX(), s.center.getY(), s.center.getZ(), 240,
					pl -> true) != null;
			if ((sec + s.id) % 2 == 0 && incoming(level, s)) {
				ar.alertUntil = now + 600;
			}
			boolean alert = ar.alertUntil > now;
			if (near || alert) {
				long m0 = System.nanoTime();
				a.materialize(level, p, s, ar, near, alert);
				com.stasdoto.airdefense.util.Perf.over("arsenal of " + s.name + " comes out", m0);
			}
			if ((sec + s.id * 13L) % 30 == 0) {
				long m0 = System.nanoTime();
				a.produce(level, p, s, ar);
				com.stasdoto.airdefense.util.Perf.over("arsenal of " + s.name + " produces", m0);
			}
		}
		// Deliveries arriving; a lorry on the road into town when somebody is there to see it come.
		if (!a.deliveries.isEmpty()) {
			for (Delivery d : List.copyOf(a.deliveries)) {
				if (d.id() != 0 && d.arrives() - now <= SUPPLY_LEAD && !a.shown.contains(d.id())) {
					Settlement s = p.settlements.get(d.to());
					long m0 = System.nanoTime();
					boolean sent = s != null && a.supplyLorry(level, p, s, d);
					com.stasdoto.airdefense.util.Perf.over("supply lorry", m0);
					if (sent) {
						a.shown.add(d.id());
					}
				}
			}
			var it = a.deliveries.iterator();
			while (it.hasNext()) {
				Delivery d = it.next();
				if (d.arrives() <= now) {
					it.remove();
					Arsenal to = a.arsenals.get(d.to());
					Settlement s = p.settlements.get(d.to());
					if (to != null && s != null) {
						to.add(d.type(), Math.min(d.count(), Math.max(0, a.cap(s, to, d.type()) - to.stock(d.type()))));
					}
					a.setDirty();
				}
			}
		}
		if (sec % 20 == 10) {
			long m0 = System.nanoTime();
			a.war(level, p);
			com.stasdoto.airdefense.util.Perf.over("arsenals' war", m0);
		}
	}

	/** Missiles of another side flying at this town (its air defence wakes up for them). */
	private static boolean incoming(ServerLevel level, Settlement s) {
		Vec3 c = Vec3.atCenterOf(s.center);
		int side = side(s);
		double r = s.radius + 60;
		for (MissileEntity m : MissileEntity.find(level, new AABB(c, c).inflate(900), m -> m.isAlive() && m.getMissileType().threat)) {
			if (m.country() != side && m.getTarget().distanceToSqr(c) < r * r) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------
	// The vehicles in the world

	/** Where the town's vehicles stand: round the edge of a city (air defence), in its depot's yard (launchers). */
	private static final Map<Long, BlockPos> SPOTS = new HashMap<>();
	/** Seconds a unit's vehicle has not been found where it should be. */
	private static final Map<UUID, Integer> MISSING = new HashMap<>();

	private static BlockPos spot(ServerLevel level, Settlement s, int index, boolean launcher) {
		long key = s.id * 64L + index;
		BlockPos cached = SPOTS.get(key);
		if (cached == null) {
			if (SPOTS.size() > 4096) {
				SPOTS.clear();
			}
			cached = findSpot(level, s, index, launcher);
			SPOTS.put(key, cached);
		}
		return cached;
	}

	private static BlockPos findSpot(ServerLevel level, Settlement s, int index, boolean launcher) {
		Cities.Terrain t = Cities.terrain(level);
		long seed = level.getSeed();
		if (s.city >= 0) {
			Cities.City c = Cities.plannedCityAt(seed, s.center.getX(), s.center.getZ(), 400);
			if (c != null) {
				if (launcher) {
					Depots.Depot d = c.depot(seed, t);
					if (d != null) {
						int span = Math.max(1, (Math.max(d.x1 - d.x0, d.z1 - d.z0) - 20));
						boolean alongX = d.x1 - d.x0 >= d.z1 - d.z0;
						int off = 10 + (index * 13) % span;
						int mid = alongX ? (d.z0 + d.z1) / 2 : (d.x0 + d.x1) / 2;
						// In the yard (between the fence and the warehouses).
						int yard = switch (d.front) {
							case NORTH -> d.z0 + 10;
							case SOUTH -> d.z1 - 10;
							case WEST -> d.x0 + 10;
							default -> d.x1 - 10;
						};
						return alongX ? new BlockPos(d.x0 + off, d.y, d.front.getAxis() == net.minecraft.core.Direction.Axis.Z ? yard : mid)
								: new BlockPos(d.front.getAxis() == net.minecraft.core.Direction.Axis.X ? yard : mid, d.y, d.z0 + off);
					}
				}
				// Round the city just outside its edge, off the roads and the depot.
				List<Cities.Road> roads = Cities.roadsNear(seed, t, c.x, c.z);
				Depots.Depot d = c.depot(seed, t);
				Cities.Road.Spot probe = new Cities.Road.Spot();
				double a0 = Math.PI / 4 + index * Math.PI / 2 + (index >= 4 ? Math.PI / 4 : 0);
				for (int k = 0; k < 24; k++) {
					double a = a0 + (k % 2 == 0 ? 1 : -1) * (k / 2) * 0.13;
					for (int r = c.half() / 2; r <= c.half() + 40; r += 4) {
						int x = c.x + (int) Math.round(Math.cos(a) * r);
						int z = c.z + (int) Math.round(Math.sin(a) * r);
						int out = c.outside(x, z);
						if (out < 8) {
							continue;
						}
						boolean bad = d != null && d.out(x, z) < 6;
						for (Cities.Road road : roads) {
							if (road.locate(x, z, road.half + 4, probe)) {
								bad = true;
								break;
							}
						}
						if (!bad) {
							return new BlockPos(x, c.base, z);
						}
						break;
					}
				}
				return new BlockPos(c.x + (int) Math.round(Math.cos(a0) * (c.half() + 20)), c.base, c.z + (int) Math.round(Math.sin(a0) * (c.half() + 20)));
			}
		}
		// A village: on its edge, spread round.
		double a = index * 2.4 + s.id;
		int r = 34 + index * 4;
		return new BlockPos(s.center.getX() + (int) Math.round(Math.cos(a) * r), s.center.getY(), s.center.getZ() + (int) Math.round(Math.sin(a) * r));
	}

	/** Puts the town's vehicles into the world (those that are not there), tops up their stores from the depot. */
	private void materialize(ServerLevel level, Politics p, Settlement s, Arsenal ar, boolean near, boolean alert) {
		int side = side(s);
		// A couple of vehicles a second: a whole city's arsenal at once froze the server for a second or two.
		int budget = 2;
		for (int i = 0; i < ar.units.size(); i++) {
			Unit u = ar.units.get(i);
			if (u.lost) {
				continue;
			}
			// Only the air defence comes out for an alert nobody is near to see; launchers wait for company.
			if (!near && !u.type.isDefense()) {
				continue;
			}
			BlockPos at = spot(level, s, i, u.type.isLauncher());
			if (alert && !near) {
				// Keeps the battery's ground running while the missiles are on their way.
				level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(at), 2);
			}
			if (!level.isLoaded(at)) {
				continue;
			}
			VehicleEntity v = u.entity == null ? null : level.getEntity(u.entity) instanceof VehicleEntity ve ? ve : null;
			if (v == null && u.entity != null) {
				// Its chunk may still be loading its entities: wait for it a while before calling it gone.
				int n = MISSING.merge(u.entity, 1, Integer::sum);
				if (n < 120) {
					continue;
				}
				MISSING.remove(u.entity);
				u.entity = null;
			}
			if (v == null) {
				if (budget-- <= 0) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
				float yaw = (float) Math.toDegrees(Math.atan2(-(s.center.getX() - at.getX()), s.center.getZ() - at.getZ())) + 180f;
				v = VehicleEntity.spawn(level, u.type, new Vec3(at.getX() + 0.5, y, at.getZ() + 0.5), yaw);
				v.country = side;
				v.home = s.id;
				v.garrison = true;
				v.setUnlimited(false);
				u.entity = v.getUUID();
				spawned++;
				setDirty();
			}
			v.country = side;
			// Spare missiles from the depot, a load at a time.
			MissileType m = missileOf(u.type);
			if (m != null && v.getReserve() < v.reserveCapacity() / 2) {
				int want = Math.min(v.reserveCapacity() - v.getReserve(), ar.stock(m));
				if (want > 0) {
					v.addReserve(want);
					ar.add(m, -want);
					setDirty();
				}
			}
		}
	}

	/** A garrison vehicle was destroyed: the town has one less. */
	public static void destroyed(ServerLevel level, VehicleEntity v) {
		if (!v.garrison || v.home < 0) {
			return;
		}
		Arsenals a = get(level.getServer());
		Arsenal ar = a.arsenals.get(v.home);
		if (ar == null) {
			return;
		}
		for (Unit u : ar.units) {
			if (v.getUUID().equals(u.entity)) {
				u.lost = true;
				u.entity = null;
				a.setDirty();
				AirDefense.LOGGER.info("[airdefense] town {} lost its {}", v.home, u.type.id);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Making missiles and drones

	/** Ticks to make one missile (or drone), and what it takes from the town's stores. */
	static int buildTicks(MissileType m) {
		return switch (m.kind) {
			case BALLISTIC -> 9000;
			case CRUISE -> 7200;
			case DRONE -> 1800;
			case ROCKET -> 1500;
			default -> 2400;
		};
	}

	static int ironFor(MissileType m) {
		return switch (m.kind) {
			case BALLISTIC -> 40;
			case CRUISE -> 30;
			case DRONE -> 6;
			case ROCKET -> 8;
			default -> 10;
		};
	}

	static int fuelFor(MissileType m) {
		return switch (m.kind) {
			case BALLISTIC -> 120;
			case CRUISE -> 80;
			case DRONE -> 15;
			case ROCKET -> 25;
			default -> 30;
		};
	}

	/**
	 * Every 30 s: a city with an arms factory works on what the country's towns are shortest of, using iron and fuel;
	 * what is made goes to that town (by road: it takes a while). A town without a factory makes its own in a workshop,
	 * three times slower and only for itself. No iron or fuel - nothing is made.
	 */
	private void produce(ServerLevel level, Politics p, Settlement s, Arsenal ar) {
		boolean factory = s.city >= 0 && (s.eco.count(BuildingType.ARMS_FACTORY) > 0 || s.eco.count(BuildingType.FACTORY) > 0);
		VillageEconomy e = s.eco;
		if (ar.making == null) {
			// The emptiest store in the country.
			Settlement neediest = null;
			MissileType need = null;
			double worst = 0.75;
			for (Settlement o : s.country < 0 || !factory ? List.of(s) : p.settlementsOf(s.country)) {
				Arsenal oa = of(p, o);
				for (Unit u : oa.units) {
					MissileType m = missileOf(u.type);
					if (u.lost || m == null) {
						continue;
					}
					int cap = cap(o, oa, m);
					double fill = cap == 0 ? 1 : oa.stock(m) / (double) cap;
					if (fill < worst) {
						worst = fill;
						need = m;
						neediest = o;
					}
				}
			}
			if (need == null) {
				return;
			}
			if (e.stock[VillageEconomy.IRON] < ironFor(need) || e.stock[VillageEconomy.FUEL] < fuelFor(need)) {
				return;
			}
			e.stock[VillageEconomy.IRON] -= ironFor(need);
			e.stock[VillageEconomy.FUEL] -= fuelFor(need);
			ar.making = need;
			ar.progress = 0;
			ar.nextStrike = Math.max(ar.nextStrike, 0);
			pendingFor.put(s.id, neediest.id);
			setDirty();
			return;
		}
		ar.progress += factory ? 600 : 200;
		if (ar.progress >= buildTicks(ar.making)) {
			MissileType m = ar.making;
			int count = m.kind == MissileType.Kind.ROCKET ? 3 : 1;
			Integer to = pendingFor.remove(s.id);
			Settlement dest = to == null ? null : p.settlements.get(to);
			if (dest == null || dest.id == s.id) {
				ar.add(m, Math.min(count, Math.max(0, cap(s, ar, m) - ar.stock(m))));
			} else {
				// By road to the other town: a minute or two per kilometre.
				double km = Math.sqrt(dest.center.distSqr(s.center)) / 1000.0;
				deliveries.add(new Delivery(level.getRandom().nextLong() | 1L, dest.id, m, count, level.getGameTime() + 1200 + (long) (km * 1800)));
			}
			made += count;
			ar.making = null;
			ar.progress = 0;
			setDirty();
		}
	}

	/** Which town a factory's current work is for (not saved: after a restart it goes to the factory's own store). */
	private final Map<Integer, Integer> pendingFor = new HashMap<>();

	// ------------------------------------------------------------------------------------------------
	// War: towns fire at the enemy's towns in range

	private void war(ServerLevel level, Politics p) {
		long now = level.getGameTime();
		Random r = new Random(now * 31 + 7);
		for (Country c : new ArrayList<>(p.countries.values())) {
			if (c.owner != null || c.wars.isEmpty()) {
				continue;
			}
			for (Settlement s : p.settlementsOf(c.id)) {
				Arsenal ar = arsenals.get(s.id);
				if (ar == null || ar.nextStrike > now || r.nextInt(100) >= 30) {
					continue;
				}
				for (int i = 0; i < ar.units.size(); i++) {
					Unit u = ar.units.get(i);
					if (u.lost || !u.type.isLauncher()) {
						continue;
					}
					MissileType m = missileOf(u.type);
					VehicleEntity v = u.entity == null ? null : level.getEntity(u.entity) instanceof VehicleEntity ve ? ve : null;
					int loaded = v != null ? Integer.bitCount(v.getLoadedMask()) : u.ammo;
					if (loaded == 0 && v == null && m != null && ar.stock(m) > 0) {
						// Reloaded at the depot.
						int n = Math.min(u.type.rails(), ar.stock(m));
						ar.add(m, -n);
						u.ammo = n;
						loaded = n;
					}
					if (loaded == 0) {
						continue;
					}
					Settlement target = target(p, c, s, u.type);
					if (target == null) {
						continue;
					}
					fire(level, p, s, ar, u, v, target, Math.min(loaded, u.type.launcher.salvo));
					ar.nextStrike = now + 2400 + r.nextInt(3600);
					setDirty();
					break;
				}
			}
		}
	}

	/**
	 * The lorry bringing a delivery in (1.25): it turns up 200 blocks out on the road from the factory's side and drives
	 * in - to the depot's gate when the city has one (its launchers stand there), else into the town. When it gets there
	 * the load is in the stores (sooner than by the clock); if it is destroyed on the way, the load is lost.
	 */
	private boolean supplyLorry(ServerLevel level, Politics p, Settlement to, Delivery d) {
		// Only when somebody is in the town or by its depot to see it come.
		if (level.getNearestPlayer(to.center.getX(), to.center.getY(), to.center.getZ(), to.radius + 400, pl -> true) == null) {
			return false;
		}
		Vec3 from = Vec3.atCenterOf(to.center).add(600, 0, 0);
		for (Settlement o : p.settlementsOf(to.country)) {
			if (o.id != to.id && o.city >= 0) {
				from = Vec3.atCenterOf(o.center);
				break;
			}
		}
		List<Vec3> route = supplyRoute(level, to, from);
		if (route.size() < 2) {
			// No road in: the load just turns up by the clock.
			return true;
		}
		Vec3 start = route.getFirst();
		Vec3 next = route.get(1);
		BlockPos at = BlockPos.containing(start.x, 0, start.z);
		if (!level.isLoaded(at)) {
			// Its road out there is loading (in the background, no freeze): the lorry sets off next second.
			level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(at), 2);
			return false;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		float yaw = (float) Math.toDegrees(Math.atan2(-(next.x - start.x), next.z - start.z));
		VehicleEntity v = VehicleEntity.spawn(level, com.stasdoto.airdefense.vehicle.VehicleType.SUPPLY_TRUCK, new Vec3(start.x, y, start.z), yaw);
		v.country = side(to);
		v.home = to.id;
		v.garrison = true;
		v.setUnlimited(false);
		v.cargoDelivery = d.id();
		v.drive(route.subList(1, route.size()), 0.7f);
		// The lorry has the load now: the clock only brings it in if the lorry never gets there (two minutes).
		int idx = deliveries.indexOf(d);
		if (idx >= 0) {
			deliveries.set(idx, new Delivery(d.id(), d.to(), d.type(), d.count(), Math.max(d.arrives(), level.getGameTime() + 2400)));
			setDirty();
		}
		lorries++;
		AirDefense.LOGGER.info("[airdefense] a lorry brings {} x{} to {} ({} points from {}, {})", d.type().name(), d.count(), to.name, route.size(),
				(int) start.x, (int) start.z);
		return true;
	}

	/** The road a lorry takes in: along the highway to the depot's access road and in at its gate, or into the town. */
	private static List<Vec3> supplyRoute(ServerLevel level, Settlement to, Vec3 from) {
		long seed = level.getSeed();
		Cities.Terrain t = Cities.terrain(level);
		Cities.City city = to.city >= 0 ? Cities.plannedCityAt(seed, to.center.getX(), to.center.getZ(), 400) : null;
		Depots.Depot depot = city == null ? null : city.depot(seed, t);
		List<Vec3> out = new ArrayList<>();
		if (depot != null && depot.access != null) {
			// Where the access road meets the highway, and the highway it meets.
			double jx = depot.access.x1;
			double jz = depot.access.z1;
			Cities.Road.Spot spot = new Cities.Road.Spot();
			for (Cities.Road r : Cities.mainRoadsNear(seed, t, city.x, city.z)) {
				if (!r.highway || !r.locate(jx, jz, r.half + 6, spot)) {
					continue;
				}
				boolean startInTown = city.outside(r.x0, r.z0) <= 12;
				double join = spot.along;
				// From further out than the junction (away from town), up to it.
				double far = startInTown ? Math.min(r.length - 10, join + 200) : Math.max(10, join - 200);
				double step = startInTown ? -12 : 12;
				for (double s = far; startInTown ? s > join : s < join; s += step) {
					double[] q = r.pointAt(s);
					out.add(new Vec3(q[0], 0, q[1]));
				}
				double[] q = r.pointAt(join);
				out.add(new Vec3(q[0], 0, q[1]));
				out.add(new Vec3(depot.access.x0 + 0.5, 0, depot.access.z0 + 0.5));
				// Through the gate into the yard.
				out.add(new Vec3(depot.gateX + 0.5 - depot.front.getStepX() * 10, 0, depot.gateZ + 0.5 - depot.front.getStepZ() * 10));
				if (out.size() >= 4) {
					return out;
				}
				out.clear();
			}
		}
		War.Approach ap = War.approach(level, to, from, 200);
		if (ap != null) {
			out.addAll(ap.waypoints);
		}
		return out;
	}

	/** A lorry got to the end of its road: the load goes into the town's stores (if the clock has not put it there already). */
	public static void lorryArrived(ServerLevel level, VehicleEntity v) {
		lorryArrivals++;
		Arsenals a = get(level.getServer());
		Politics p = Politics.get(level.getServer());
		var it = a.deliveries.iterator();
		while (it.hasNext()) {
			Delivery d = it.next();
			if (d.id() == v.cargoDelivery) {
				it.remove();
				Arsenal to = a.arsenals.get(d.to());
				Settlement s = p.settlements.get(d.to());
				if (to != null && s != null) {
					to.add(d.type(), Math.min(d.count(), Math.max(0, a.cap(s, to, d.type()) - to.stock(d.type()))));
				}
				lorryLoads++;
				a.setDirty();
				AirDefense.LOGGER.info("[airdefense] the lorry unloaded {} x{} at {}", d.type().name(), d.count(), s == null ? "?" : s.name);
			}
		}
		a.shown.remove(v.cargoDelivery);
		v.cargoDelivery = 0;
	}

	/** A lorry was destroyed on the road: its load is lost. */
	public static void lorryLost(ServerLevel level, VehicleEntity v) {
		Arsenals a = get(level.getServer());
		if (a.deliveries.removeIf(d -> d.id() == v.cargoDelivery)) {
			lorryLosses++;
			a.setDirty();
			AirDefense.LOGGER.info("[airdefense] a lorry was destroyed with its load");
		}
		a.shown.remove(v.cargoDelivery);
		v.cargoDelivery = 0;
	}

	/** For the tests: a load of a town's own launcher missiles on its way, due in 20 s (its lorry sets off at once). */
	public static MissileType testDelivery(ServerLevel level, Settlement to) {
		Arsenals a = get(level.getServer());
		Arsenal ar = a.of(Politics.get(level.getServer()), to);
		MissileType m = null;
		for (Unit u : ar.units) {
			if (u.type.isLauncher()) {
				m = missileOf(u.type);
				break;
			}
		}
		if (m == null) {
			return null;
		}
		a.deliveries.add(new Delivery(level.getRandom().nextLong() | 1L, to.id, m, 3, level.getGameTime() + 400));
		a.setDirty();
		return m;
	}

	/** For the tests: this town fires its first loaded launcher at that town now. */
	public static boolean strikeNow(ServerLevel level, Settlement from, Settlement target) {
		Politics p = Politics.get(level.getServer());
		Arsenals a = get(level.getServer());
		Arsenal ar = a.of(p, from);
		for (Unit u : ar.units) {
			if (!u.lost && u.type.isLauncher()) {
				VehicleEntity v = u.entity == null ? null : level.getEntity(u.entity) instanceof VehicleEntity ve ? ve : null;
				int loaded = v != null ? Integer.bitCount(v.getLoadedMask()) : u.ammo;
				if (loaded > 0) {
					a.fire(level, p, from, ar, u, v, target, Math.min(loaded, u.type.launcher.salvo));
					return true;
				}
			}
		}
		return false;
	}

	/** The nearest enemy town in reach of this launcher. */
	@Nullable
	private static Settlement target(Politics p, Country c, Settlement from, VehicleType launcher) {
		Settlement best = null;
		double bestD = Double.MAX_VALUE;
		double reach = launcher.launcher.maxRange;
		for (int enemyId : c.wars) {
			for (Settlement t : p.settlementsOf(enemyId)) {
				double d = Math.sqrt(t.center.distSqr(from.center));
				if (d > 60 && d < reach && d < bestD) {
					bestD = d;
					best = t;
				}
			}
		}
		return best;
	}

	/**
	 * One strike: from the launcher itself if it stands in the world; else, if somebody is near the target, the
	 * missiles turn up on their way in (the air defence there gets its chance); else it is worked out by chance.
	 */
	private void fire(ServerLevel level, Politics p, Settlement from, Arsenal ar, Unit u, @Nullable VehicleEntity v, Settlement target, int salvo) {
		strikes++;
		BlockPos aimAt = aimPoint(level, target);
		MissileType m = missileOf(u.type);
		if (v != null && v.isAlive() && v.commandStrike(aimAt, null)) {
			AirDefense.LOGGER.info("[airdefense] {} fires {} at {}", from.name, u.type.id, target.name);
			tellStrike(level, p, target, from, false);
			return;
		}
		u.ammo = Math.max(0, u.ammo - salvo);
		var watcher = level.getNearestPlayer(target.center.getX(), target.center.getY(), target.center.getZ(), target.radius + 350, pl -> true);
		boolean seen = watcher != null;
		if (!seen && !level.players().isEmpty() && com.stasdoto.airdefense.missile.MissileStats.debug()) {
			var pl = level.players().getFirst();
			AirDefense.LOGGER.info("[airdefense] strike on {} unseen: nearest player {} blocks off (reach {})", target.name,
					(int) Math.sqrt(pl.distanceToSqr(Vec3.atCenterOf(target.center))), target.radius + 350);
		}
		if (seen && m != null) {
			Vec3 tc = Vec3.atBottomCenterOf(aimAt);
			Vec3 dir = Vec3.atCenterOf(from.center).subtract(tc);
			dir = new Vec3(dir.x, 0, dir.z).normalize();
			double dist = Math.sqrt(from.center.distSqr(target.center));
			double start = Math.min(dist, m.kind == MissileType.Kind.DRONE ? 300 : 360);
			for (int k = 0; k < salvo; k++) {
				double spread = (k - salvo / 2.0) * 6;
				Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(spread);
				double h = switch (m.kind) {
					case BALLISTIC -> 240;
					case ROCKET -> 120;
					case CRUISE -> 50;
					default -> 70;
				};
				Vec3 at = tc.add(dir.scale(start + k * 12)).add(side).add(0, h, 0);
				Vec3 aim = tc.add(level.getRandom().nextGaussian() * 3, 1, level.getRandom().nextGaussian() * 3);
				MissileType kind = m == MissileType.SHAHED && level.getRandom().nextFloat() < 0.3f ? MissileType.GERBERA : m;
				Vec3 back = dir;
				int sideOf = side(from);
				// Out there beyond the loaded ground perhaps: it turns up once that is loaded (in the background).
				com.stasdoto.airdefense.util.Later.whenLoaded(level, BlockPos.containing(at), 200, l -> {
					int ground = l.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
					Vec3 pos = new Vec3(at.x, Math.max(at.y, ground + 20), at.z);
					MissileEntity me = MissileEntity.launchStrike(l, kind, pos, aim, back.scale(-1));
					me.setCountry(sideOf);
				});
			}
			com.stasdoto.airdefense.siren.Sirens.autoAlert(level, Vec3.atCenterOf(target.center), 300);
			AirDefense.LOGGER.info("[airdefense] {} fires {} x{} at {} (seen on the way in)", from.name, m, salvo, target.name);
			tellStrike(level, p, target, from, false);
			return;
		}
		// Nobody there to see: worked out by chance against the target's air defence and stores.
		remoteStrikes++;
		Arsenal ta = of(p, target);
		int hits = 0;
		for (int k = 0; k < salvo; k++) {
			boolean down = false;
			for (Unit d : ta.units) {
				if (d.lost || !d.type.isDefense() || m == null) {
					continue;
				}
				MissileType im = missileOf(d.type);
				double chance;
				if (im == null) {
					chance = m.kind == MissileType.Kind.DRONE ? 0.5 : m.kind == MissileType.Kind.BALLISTIC ? 0 : 0.2;
				} else if (ta.stock(im) > 0 || d.ammo > 0) {
					if (d.ammo > 0) {
						d.ammo--;
					} else {
						ta.add(im, -1);
					}
					chance = im.killChance(m.kind) * 0.85;
				} else {
					continue;
				}
				if (level.getRandom().nextDouble() < chance) {
					down = true;
					break;
				}
			}
			if (!down) {
				hits++;
			}
		}
		remoteHits += hits;
		if (hits > 0) {
			// Damage: people, stores, and now and then a vehicle of the garrison.
			target.citizens = Math.max(0, target.citizens - hits * (m != null && m.kind == MissileType.Kind.BALLISTIC ? 6 : 2));
			for (int k = 0; k < VillageEconomy.KINDS; k++) {
				target.eco.stock[k] = (int) (target.eco.stock[k] * Math.pow(0.97, hits));
			}
			if (level.getRandom().nextInt(4) < hits) {
				for (Unit d : ta.units) {
					if (!d.lost && d.entity == null) {
						d.lost = true;
						break;
					}
				}
			}
			p.setDirty();
		}
		setDirty();
		tellStrike(level, p, target, from, true, salvo, salvo - hits);
		AirDefense.LOGGER.info("[airdefense] {} -> {}: {} x{}, {} shot down (out of sight)", from.name, target.name, m, salvo, salvo - hits);
	}

	/** Where in the target town the missiles are aimed: its square, a building now and then. */
	private static BlockPos aimPoint(ServerLevel level, Settlement t) {
		Random r = new Random();
		if (!t.eco.buildings.isEmpty() && r.nextBoolean()) {
			Building b = t.eco.buildings.get(r.nextInt(t.eco.buildings.size()));
			return b.at(0, 0, b.type.depth / 2);
		}
		return t.center;
	}

	private static void tellStrike(ServerLevel level, Politics p, Settlement target, Settlement from, boolean remote) {
		tellStrike(level, p, target, from, remote, -1, -1);
	}

	private static void tellStrike(ServerLevel level, Politics p, Settlement target, Settlement from, boolean remote, int fired, int down) {
		Country c = p.country(target.country);
		if (c == null || c.owner == null || !(level.getServer().getPlayerList().getPlayer(c.owner) instanceof ServerPlayer owner)) {
			return;
		}
		if (remote) {
			owner.sendSystemMessage(Component.translatable("nation.airdefense.strike.remote", from.name, target.name, fired, down));
		} else {
			owner.sendSystemMessage(Component.translatable("nation.airdefense.strike.incoming", from.name, target.name));
		}
	}
}
