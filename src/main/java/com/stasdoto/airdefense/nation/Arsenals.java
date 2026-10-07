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

	public record Delivery(int to, MissileType type, int count, long arrives) {
		static final Codec<Delivery> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("to").forGetter(Delivery::to),
				Codec.STRING.fieldOf("type").forGetter(d -> d.type.name()),
				Codec.INT.fieldOf("count").forGetter(Delivery::count),
				Codec.LONG.fieldOf("arrives").forGetter(Delivery::arrives)
		).apply(i, (to, t, n, at) -> new Delivery(to, MissileType.valueOf(t), n, at)));
	}

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
		Politics p = Politics.get(level.getServer());
		Arsenals a = get(level.getServer());
		for (Settlement s : p.settlements.values()) {
			Arsenal ar = a.of(p, s);
			boolean near = level.isLoaded(s.center) && level.getNearestPlayer(s.center.getX(), s.center.getY(), s.center.getZ(), 240,
					pl -> !pl.isSpectator()) != null;
			if ((now + s.id) % 40 == 0 && incoming(level, s)) {
				ar.alertUntil = now + 600;
			}
			boolean alert = ar.alertUntil > now;
			if (near || alert) {
				a.materialize(level, p, s, ar, near, alert);
			}
			if ((now + s.id * 13L) % 600 == 0) {
				a.produce(level, p, s, ar);
			}
		}
		// Deliveries arriving.
		if (!a.deliveries.isEmpty()) {
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
		if (now % 400 == 200) {
			a.war(level, p);
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
	private static BlockPos spot(ServerLevel level, Settlement s, int index, boolean launcher) {
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
				double a = Math.PI / 4 + index * Math.PI / 2 + (index >= 4 ? Math.PI / 4 : 0);
				int r = c.half() + 14;
				return new BlockPos(c.x + (int) Math.round(Math.cos(a) * r), c.base, c.z + (int) Math.round(Math.sin(a) * r));
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
				continue;
			}
			if (v == null) {
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
	 * what is made goes to that town (by road: it takes a while). No iron or fuel - nothing is made.
	 */
	private void produce(ServerLevel level, Politics p, Settlement s, Arsenal ar) {
		if (s.city < 0 || s.eco.count(BuildingType.ARMS_FACTORY) == 0 && s.eco.count(BuildingType.FACTORY) == 0) {
			return;
		}
		VillageEconomy e = s.eco;
		if (ar.making == null) {
			// The emptiest store in the country.
			Settlement neediest = null;
			MissileType need = null;
			double worst = 0.75;
			for (Settlement o : s.country < 0 ? List.of(s) : p.settlementsOf(s.country)) {
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
		ar.progress += 600;
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
				deliveries.add(new Delivery(dest.id, m, count, level.getGameTime() + 1200 + (long) (km * 1800)));
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
		boolean seen = level.isLoaded(target.center) && level.getNearestPlayer(target.center.getX(), target.center.getY(), target.center.getZ(), 400,
				pl -> !pl.isSpectator()) != null;
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
				Vec3 pos = tc.add(dir.scale(start + k * 12)).add(side).add(0, h, 0);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
				pos = new Vec3(pos.x, Math.max(pos.y, ground + 20), pos.z);
				Vec3 aim = tc.add(level.getRandom().nextGaussian() * 3, 1, level.getRandom().nextGaussian() * 3);
				MissileEntity me = MissileEntity.launchStrike(level, m == MissileType.SHAHED && level.getRandom().nextFloat() < 0.3f ? MissileType.GERBERA : m,
						pos, aim, dir.scale(-1));
				me.setCountry(side(from));
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
