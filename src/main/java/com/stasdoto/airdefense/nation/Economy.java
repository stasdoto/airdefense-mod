package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.factory.FactoryBlockEntity;
import com.stasdoto.airdefense.factory.FactoryBlueprint;
import com.stasdoto.airdefense.factory.FactoryControllerBlock;
import com.stasdoto.airdefense.registry.ModBlocks;
import com.stasdoto.airdefense.registry.ModTickets;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * The village economy, run on the server: workers bring wood, stone and iron into the village store; buildings ordered
 * by the owner go up block by block while builders work at them (in creative: free and fast); the hangar makes
 * vehicles; the maternity hospital brings babies into the world while there are free beds; the barracks let more men
 * be called up and heal the soldiers around them.
 */
public final class Economy {
	/** How many buildings may wait in line at once. */
	public static final int MAX_QUEUE = 6;
	public static final int MAX_HANGAR_QUEUE = 4;
	/** Blocks per tick: a builder puts up 3 blocks a second; a creative order goes up at 8 blocks a tick. */
	private static final double PER_BUILDER = 0.15;
	private static final double FREE_RATE = 8;
	/** The made-up countries' villages put up a building of their own now and then, slowly (5 blocks a second). */
	private static final double AI_RATE = 0.25;
	private static final int CLEAR_PER_TICK = 64;

	/** Debug counters read by the automated test. */
	public static int built;
	public static int delivered;
	public static int born;
	public static int vehiclesMade;
	public static int hired;
	public static int fed;
	public static int aiBuilt;

	private static final Map<Integer, List<Blueprints.Placement>> PLANS = new HashMap<>();
	private static final Map<Integer, Double> CREDIT = new HashMap<>();
	private static final Map<Integer, BlockPos> LAST = new HashMap<>();
	private static final Map<Integer, Spots> SPOTS = new HashMap<>();

	private record Spots(long time, List<BlockPos> trees, List<BlockPos> rocks, List<BlockPos> ores) {
	}

	private Economy() {
	}

	// ------------------------------------------------------------------------------------------------
	// Ticking

	static void tick(ServerLevel level, Politics p) {
		long t = level.getGameTime();
		for (Settlement s : p.settlements.values()) {
			VillageEconomy e = s.eco;
			Building b = e.active();
			if (b != null) {
				construct(level, p, s, b, t);
			}
			if (t % 20 == 11 && !e.hangar.isEmpty()) {
				hangarStep(level, p, s);
			}
			if (t % 40 == 23 && e.count(BuildingType.BARRACKS) > 0) {
				barracksHeal(level, s);
			}
			if ((t + s.id * 131L) % 6000 == 3000) {
				aiGrowth(level, p, s);
			}
			if (t % 200 == 117 && level.isLoaded(s.center)) {
				float chance = birthChance(p, s);
				if (chance > 0 && level.getRandom().nextFloat() < chance) {
					birth(level, p, s);
				}
				if (owned(p, s)) {
					growUp(level, s);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Construction

	private static void construct(ServerLevel level, Politics p, Settlement s, Building b, long t) {
		BlockPos site = siteCenter(b, s);
		// On a road the builders spread out along it; on a building they have to be there.
		int builders = b.free ? 0 : b.type == BuildingType.ROADS ? jobCounts(level, s)[WorkerEntity.BUILD] : buildersAt(level, s, site);
		boolean owned = owned(p, s);
		if ((b.free || builders > 0) && t % 20 == 0 && owned) {
			// Keeps going while the owner is away (the made-up countries build only where somebody is around).
			level.getChunkSource().addTicketWithRadius(ModTickets.VEHICLE, ChunkPos.containing(site), 2);
		}
		if (!level.isLoaded(b.type == BuildingType.ROADS ? site : b.origin) || !b.free && builders == 0) {
			return;
		}
		double credit = CREDIT.getOrDefault(b.id, 0.0) + (b.free ? (owned ? FREE_RATE : AI_RATE) : builders * PER_BUILDER);
		switch (b.type) {
			case FACTORY -> {
				factoryStep(level, p, s, b);
				credit = 0;
			}
			case ROADS -> {
				if (b.total <= 0) {
					b.total = Roads.total(b.points);
				}
				while (credit >= 1 && b.index < b.total) {
					if (!Roads.step(level, b)) {
						break;
					}
					int[] at = Roads.at(b.points, b.index);
					if (at != null) {
						LAST.put(b.id, new BlockPos(at[0], level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at[0], at[1]) - 1, at[1]));
					}
					b.index++;
					credit -= b.free ? 0.34 : 1;
				}
				if (b.index >= b.total) {
					finish(level, p, s, b);
				}
			}
			default -> credit = buildStep(level, p, s, b, credit);
		}
		CREDIT.put(b.id, Math.min(credit, 40));
		p.setDirty();
	}

	private static double buildStep(ServerLevel level, Politics p, Settlement s, Building b, double credit) {
		List<Blueprints.Placement> plan = plan(p, s, b);
		b.total = plan.size();
		int cleared = 0;
		boolean first = true;
		while (b.index < plan.size() && credit >= 1 && cleared < CLEAR_PER_TICK) {
			Blueprints.Placement pl = plan.get(b.index++);
			BlockState current = level.getBlockState(pl.pos());
			if (Blueprints.protectedBlock(current)) {
				continue;
			}
			if (pl.state().isAir()) {
				if (!current.isAir()) {
					level.setBlock(pl.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					cleared++;
				}
				continue;
			}
			if (pl.pair()) {
				if (Blueprints.protectedBlock(level.getBlockState(pl.pos2()))) {
					continue;
				}
				evict(level, pl.pos(), b);
				evict(level, pl.pos2(), b);
				level.setBlock(pl.pos(), pl.state(), Block.UPDATE_ALL);
				level.setBlock(pl.pos2(), pl.state2(), Block.UPDATE_ALL);
				credit -= 2;
			} else {
				if (current.equals(pl.state())) {
					continue;
				}
				BlockState st = Block.updateFromNeighbourShapes(pl.state(), level, pl.pos());
				if (st.isAir()) {
					st = pl.state();
				}
				if (!st.getCollisionShape(level, pl.pos()).isEmpty()) {
					evict(level, pl.pos(), b);
				}
				level.setBlock(pl.pos(), st, Block.UPDATE_ALL);
				credit -= 1;
				// The foundation does not hang in the air: fill the hollows under it.
				if (pl.pos().getY() == b.origin.getY() - 1) {
					for (int d = 1; d <= 6; d++) {
						BlockPos below = pl.pos().below(d);
						BlockState bs = level.getBlockState(below);
						if (!bs.isAir() && bs.getFluidState().isEmpty() && !bs.canBeReplaced()) {
							break;
						}
						level.setBlock(below, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			LAST.put(b.id, pl.pos());
			if (first) {
				first = false;
				Vec3 c = Vec3.atCenterOf(pl.pos());
				level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.01);
				if (level.getRandom().nextInt(b.free ? 4 : 2) == 0) {
					BlockState st = pl.state();
					level.playSound(null, pl.pos(), st.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
				}
			}
		}
		if (b.index >= plan.size()) {
			finish(level, p, s, b);
		}
		return credit;
	}

	private static List<Blueprints.Placement> plan(Politics p, Settlement s, Building b) {
		return PLANS.computeIfAbsent(b.id, id -> {
			Country c = p.country(s.country);
			return Blueprints.placements(b, c == null ? DyeColor.WHITE : c.dye());
		});
	}

	/** Villagers, workers and animals standing where a block goes up step out of the way (to the door). */
	private static void evict(ServerLevel level, BlockPos pos, Building b) {
		for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(pos),
				e -> e.isAlive() && !(e instanceof Player) && !(e instanceof VehicleEntity))) {
			BlockPos out = b.at(e.getRandom().nextInt(5) - 2, 0, -3);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, out.getX(), out.getZ());
			e.teleportTo(out.getX() + 0.5, y, out.getZ() + 0.5);
		}
	}

	/** The missile factory puts itself up (as when built from a kit); the builders only stand by. */
	private static void factoryStep(ServerLevel level, Politics p, Settlement s, Building b) {
		BlockPos ctrl = FactoryBlueprint.controllerPos(b.origin, b.facing);
		b.total = 100;
		if (b.index == 0) {
			level.setBlock(ctrl, ModBlocks.FACTORY_CONTROLLER.defaultBlockState().setValue(FactoryControllerBlock.FACING, b.facing.getOpposite()),
					Block.UPDATE_ALL);
			if (level.getBlockEntity(ctrl) instanceof FactoryBlockEntity f) {
				f.startConstruction(b.origin, b.facing, b.free);
			}
			b.index = 1;
			level.playSound(null, ctrl, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
			return;
		}
		if (level.getBlockEntity(ctrl) instanceof FactoryBlockEntity f) {
			b.index = Math.max(1, f.buildPercent());
			LAST.put(b.id, ctrl);
			if (f.isBuilt()) {
				finish(level, p, s, b);
			}
		} else {
			// The desk is gone (broken): start again.
			b.index = 0;
		}
	}

	private static void finish(ServerLevel level, Politics p, Settlement s, Building b) {
		b.done = true;
		PLANS.remove(b.id);
		CREDIT.remove(b.id);
		LAST.remove(b.id);
		built++;
		if (b.type != BuildingType.ROADS) {
			Roads.doorPath(level, b, s.center);
		}
		level.playSound(null, b.type == BuildingType.ROADS ? s.center : b.middle(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		Country c = p.country(s.country);
		if (c != null && c.owner != null && level.getServer().getPlayerList().getPlayer(c.owner) instanceof ServerPlayer owner) {
			owner.sendSystemMessage(Component.translatable("nation.airdefense.eco.built", Component.translatable(b.type.key()), s.name));
		}
		AirDefense.LOGGER.info("[airdefense] {} built in {} at {}", b.type.id, s.name, b.origin.toShortString());
		p.setDirty();
	}

	/** Builders of the village close enough to the site to work on it. */
	static int buildersAt(ServerLevel level, Settlement s, BlockPos site) {
		int n = 0;
		for (WorkerEntity w : level.getEntitiesOfClass(WorkerEntity.class, new AABB(site).inflate(40, 24, 40),
				w -> w.isAlive() && w.home() == s.id && w.job() == WorkerEntity.BUILD)) {
			if (w.distanceToSqr(Vec3.atCenterOf(site)) < 36 * 36) {
				n++;
			}
		}
		return n;
	}

	/** Where the work is: the middle of the building, or the road's current end. */
	static BlockPos siteCenter(Building b, Settlement s) {
		if (b.type == BuildingType.ROADS) {
			int[] at = Roads.at(b.points, Math.min(b.index, Math.max(0, b.total - 1)));
			BlockPos last = LAST.get(b.id);
			if (last != null) {
				return last;
			}
			return at == null ? s.center : new BlockPos(at[0], s.center.getY(), at[1]);
		}
		return b.middle();
	}

	/** Where a builder stands: somewhere around the building, on the ground. */
	static BlockPos buildSpot(ServerLevel level, Building b, Settlement s, RandomSource r) {
		BlockPos base;
		if (b.type == BuildingType.ROADS) {
			BlockPos c = siteCenter(b, s);
			base = c.offset(r.nextInt(7) - 3, 0, r.nextInt(7) - 3);
		} else {
			int hw = b.type.halfWidth() + 2;
			int d = b.type.depth + 1;
			int side = r.nextInt(4);
			int lx;
			int lz;
			switch (side) {
				case 0 -> {
					lx = r.nextInt(2 * hw + 1) - hw;
					lz = -3;
				}
				case 1 -> {
					lx = r.nextInt(2 * hw + 1) - hw;
					lz = d;
				}
				case 2 -> {
					lx = -hw;
					lz = r.nextInt(d + 3) - 2;
				}
				default -> {
					lx = hw;
					lz = r.nextInt(d + 3) - 2;
				}
			}
			base = b.at(lx, 0, lz);
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
		return new BlockPos(base.getX(), y, base.getZ());
	}

	/** The block builders look at and hammer: the last one placed. */
	static BlockPos lastPlaced(Building b, BlockPos site) {
		BlockPos last = LAST.get(b.id);
		return last != null ? last : site;
	}

	// ------------------------------------------------------------------------------------------------
	// Orders

	public static boolean owner(Politics p, Settlement s, Player player) {
		Country c = p.country(s.country);
		return c != null && player.getUUID().equals(c.owner);
	}

	/** Orders a building: pays for it from the store (not in creative), finds its place, puts it in line. */
	public static boolean order(ServerLevel level, ServerPlayer player, Settlement s, BuildingType type) {
		Politics p = Politics.get(level.getServer());
		if (!owner(p, s, player)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.not_yours"));
			return false;
		}
		VillageEconomy e = s.eco;
		if (e.queued() >= MAX_QUEUE) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.queue_full"));
			return false;
		}
		boolean free = player.getAbilities().instabuild;
		if (type == BuildingType.ROADS) {
			return orderRoads(level, p, player, s, free);
		}
		if (!free && !canPay(e, type.wood, type.stone, type.iron)) {
			player.sendOverlayMessage(missing(e, type.wood, type.stone, type.iron));
			return false;
		}
		Building b = Sites.find(level, p, s, type, p.newId(), free);
		if (b == null) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.no_site"));
			return false;
		}
		if (!free) {
			pay(e, type.wood, type.stone, type.iron);
		}
		e.buildings.add(b);
		p.setDirty();
		player.sendSystemMessage(Component.translatable("nation.airdefense.eco.ordered", Component.translatable(type.key()), s.name));
		if (!free && jobCounts(level, s)[WorkerEntity.BUILD] == 0) {
			player.sendSystemMessage(Component.translatable("nation.airdefense.eco.no_builders"));
		}
		AirDefense.LOGGER.info("[airdefense] {} ordered in {} at {} facing {}", type.id, s.name, b.origin.toShortString(), b.facing);
		return true;
	}

	/** Roads from the square to every building, and to the nearest two villages of the same country (within 400 blocks). */
	private static boolean orderRoads(ServerLevel level, Politics p, ServerPlayer player, Settlement s, boolean free) {
		List<BlockPos> points = new ArrayList<>();
		for (Building b : s.eco.buildings) {
			if (b.type != BuildingType.ROADS) {
				addStretch(points, s.center, b.doorstep(), 3);
			}
		}
		List<Settlement> others = new ArrayList<>();
		for (Settlement o : p.settlementsOf(s.country)) {
			if (o != s && o.center.distSqr(s.center) < 400 * 400) {
				others.add(o);
			}
		}
		others.sort(Comparator.comparingDouble(o -> o.center.distSqr(s.center)));
		for (int i = 0; i < Math.min(2, others.size()); i++) {
			addStretch(points, s.center, others.get(i).center, 3);
		}
		if (points.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.no_roads"));
			return false;
		}
		int length = Roads.total(points);
		int stone = Math.max(5, length / 2);
		VillageEconomy e = s.eco;
		if (!free && !canPay(e, 0, stone, 0)) {
			player.sendOverlayMessage(missing(e, 0, stone, 0));
			return false;
		}
		if (!free) {
			pay(e, 0, stone, 0);
		}
		Building road = new Building(p.newId(), BuildingType.ROADS, s.center, Direction.NORTH, free);
		road.points.addAll(points);
		road.total = length;
		e.buildings.add(road);
		p.setDirty();
		player.sendSystemMessage(Component.translatable("nation.airdefense.eco.roads_ordered", length, s.name));
		return true;
	}

	/** A stretch from a few blocks off {@code from} (not through the bell) to {@code to}. */
	private static void addStretch(List<BlockPos> points, BlockPos from, BlockPos to, int skip) {
		double d = Math.sqrt(from.distSqr(to));
		if (d < skip + 3) {
			return;
		}
		double k = skip / d;
		points.add(new BlockPos((int) Math.round(from.getX() + (to.getX() - from.getX()) * k), from.getY(),
				(int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * k)));
		points.add(to);
	}

	/** Takes a building that has not been started out of the line and gives back what it cost. */
	public static void cancel(ServerLevel level, ServerPlayer player, Settlement s, int index) {
		Politics p = Politics.get(level.getServer());
		if (!owner(p, s, player)) {
			return;
		}
		List<Building> waiting = new ArrayList<>();
		for (Building b : s.eco.buildings) {
			if (!b.done) {
				waiting.add(b);
			}
		}
		if (index < 0 || index >= waiting.size()) {
			return;
		}
		Building b = waiting.get(index);
		if (b.index > 0) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.cannot_cancel"));
			return;
		}
		s.eco.buildings.remove(b);
		if (!b.free && b.type != BuildingType.ROADS) {
			give(s.eco, b.type.wood, b.type.stone, b.type.iron);
		}
		PLANS.remove(b.id);
		p.setDirty();
	}

	static boolean canPay(VillageEconomy e, int wood, int stone, int iron) {
		return e.stock[0] >= wood && e.stock[1] >= stone && e.stock[2] >= iron;
	}

	static void pay(VillageEconomy e, int wood, int stone, int iron) {
		e.stock[0] -= wood;
		e.stock[1] -= stone;
		e.stock[2] -= iron;
	}

	static void give(VillageEconomy e, int wood, int stone, int iron) {
		int cap = e.cap();
		e.stock[0] = Math.min(cap, e.stock[0] + wood);
		e.stock[1] = Math.min(cap, e.stock[1] + stone);
		e.stock[2] = Math.min(cap, e.stock[2] + iron);
	}

	static Component missing(VillageEconomy e, int wood, int stone, int iron) {
		return Component.translatable("nation.airdefense.eco.need", Math.max(0, wood - e.stock[0]), Math.max(0, stone - e.stock[1]),
				Math.max(0, iron - e.stock[2]));
	}

	// ------------------------------------------------------------------------------------------------
	// People at work

	public static List<WorkerEntity> workers(ServerLevel level, Settlement s) {
		List<WorkerEntity> out = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		for (UUID id : s.eco.workers) {
			if (level.getEntity(id) instanceof WorkerEntity w && w.isAlive() && seen.add(id)) {
				out.add(w);
			}
		}
		return out;
	}

	public static int[] jobCounts(ServerLevel level, Settlement s) {
		int[] n = new int[WorkerEntity.JOBS];
		for (WorkerEntity w : workers(level, s)) {
			n[w.job()]++;
		}
		return n;
	}

	/** Grown-up villagers at home who could be sent to work (the elder stays). */
	public static List<Villager> free(ServerLevel level, Settlement s) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : Nations.villagers(level, s)) {
			if (!v.isBaby() && !v.getUUID().equals(s.elder)) {
				out.add(v);
			}
		}
		// Those without a trade go first, then the least experienced.
		out.sort(Comparator.comparingInt((Villager v) -> {
			String job = v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
			return job.equals("none") || job.equals("nitwit") ? 0 : 1;
		}).thenComparingInt(Villager::getVillagerXp));
		return out;
	}

	/** One more (delta > 0) or one fewer (delta < 0) worker on this job. */
	public static boolean assign(ServerLevel level, ServerPlayer player, Settlement s, int job, int delta) {
		Politics p = Politics.get(level.getServer());
		if (!owner(p, s, player) || job < 0 || job >= WorkerEntity.JOBS) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.not_yours"));
			return false;
		}
		if (delta > 0) {
			List<Villager> free = free(level, s);
			if (free.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.no_people"));
				return false;
			}
			WorkerEntity w = WorkerEntity.hire(level, free.getFirst(), s.id, job);
			w.setHomeTo(s.flag, 48);
			s.eco.workers.add(w.getUUID());
			hired++;
			p.setDirty();
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.hired", w.getTypeName()));
			return true;
		}
		for (WorkerEntity w : workers(level, s)) {
			if (w.job() == job) {
				s.eco.workers.remove(w.getUUID());
				w.goHome(level);
				p.setDirty();
				player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.sent_home", 1));
				return true;
			}
		}
		return false;
	}

	/** Every worker of the village goes home. */
	public static int allHome(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		if (!owner(p, s, player)) {
			return 0;
		}
		int n = 0;
		for (WorkerEntity w : workers(level, s)) {
			w.goHome(level);
			n++;
		}
		s.eco.workers.clear();
		p.setDirty();
		player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.sent_home", n));
		return n;
	}

	/** A gatherer's place of work: a tree, a rock (or iron ore) near the village; failing that, the edge of the village. */
	@Nullable
	static BlockPos workSpot(ServerLevel level, Settlement s, int job, RandomSource r) {
		Spots spots = SPOTS.get(s.id);
		if (spots == null || level.getGameTime() - spots.time > 2400) {
			spots = scan(level, s);
			SPOTS.put(s.id, spots);
		}
		List<BlockPos> list = job == WorkerEntity.WOOD ? spots.trees : job == WorkerEntity.IRON && !spots.ores.isEmpty() ? spots.ores : spots.rocks;
		if (!list.isEmpty()) {
			return list.get(r.nextInt(Math.min(10, list.size())));
		}
		double a = r.nextDouble() * Math.PI * 2;
		int d = 24 + r.nextInt(16);
		int x = s.center.getX() + (int) Math.round(Math.cos(a) * d);
		int z = s.center.getZ() + (int) Math.round(Math.sin(a) * d);
		if (!level.isLoaded(new BlockPos(x, 64, z))) {
			return s.flag;
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
	}

	private static Spots scan(ServerLevel level, Settlement s) {
		List<BlockPos> trees = new ArrayList<>();
		List<BlockPos> rocks = new ArrayList<>();
		List<BlockPos> ores = new ArrayList<>();
		Set<Long> trunks = new HashSet<>();
		int cx = s.center.getX();
		int cz = s.center.getZ();
		for (int dx = -46; dx <= 46; dx += 2) {
			for (int dz = -46; dz <= 46; dz += 2) {
				int x = cx + dx;
				int z = cz + dz;
				if (!level.isLoaded(new BlockPos(x, 64, z)) || insideBuilding(s, x, z)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
				BlockState t = level.getBlockState(new BlockPos(x, top, z));
				if (t.is(BlockTags.LEAVES)) {
					BlockPos trunk = trunkUnder(level, x, top, z);
					if (trunk != null && trunks.add(trunk.asLong())) {
						trees.add(trunk);
					}
					continue;
				}
				int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				BlockPos g = new BlockPos(x, gy, z);
				BlockState gs = level.getBlockState(g);
				if (gs.is(BlockTags.IRON_ORES) || gs.is(Blocks.RAW_IRON_BLOCK)) {
					ores.add(g);
				} else if (gs.is(BlockTags.BASE_STONE_OVERWORLD) || gs.is(Blocks.GRAVEL) || gs.is(Blocks.MOSSY_COBBLESTONE)) {
					rocks.add(g);
				} else if (gs.is(BlockTags.LOGS) && Sites.treeLog(level, g)) {
					if (trunks.add(g.asLong())) {
						trees.add(g);
					}
				}
			}
		}
		Comparator<BlockPos> near = Comparator.comparingDouble(q -> q.distSqr(s.center));
		trees.sort(near);
		rocks.sort(near);
		ores.sort(near);
		return new Spots(level.getGameTime(), trees, rocks, ores);
	}

	/** The foot of a trunk under this bit of canopy (looking in the columns around too). */
	@Nullable
	private static BlockPos trunkUnder(ServerLevel level, int x, int top, int z) {
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				for (int y = top; y > top - 14; y--) {
					BlockPos q = new BlockPos(x + ox, y, z + oz);
					BlockState st = level.getBlockState(q);
					if (st.is(BlockTags.LOGS)) {
						BlockPos foot = q;
						while (level.getBlockState(foot.below()).is(BlockTags.LOGS) && foot.getY() > level.getMinY()) {
							foot = foot.below();
						}
						return foot;
					}
					if (!st.isAir() && !st.is(BlockTags.LEAVES) && !st.canBeReplaced()) {
						break;
					}
				}
			}
		}
		return null;
	}

	private static boolean insideBuilding(Settlement s, int x, int z) {
		for (Building b : s.eco.buildings) {
			if (b.covers(x, z, 1)) {
				return true;
			}
		}
		return false;
	}

	/** Where gatherers bring their loads: the warehouse door, or the village square. */
	static BlockPos dropOff(Settlement s) {
		Building w = s.eco.first(BuildingType.WAREHOUSE);
		return w != null ? w.doorstep().above() : s.flag;
	}

	static void deposit(ServerLevel level, Settlement s, int job, int amount) {
		if (job < 0 || job > 2 || amount <= 0) {
			return;
		}
		VillageEconomy e = s.eco;
		e.stock[job] = Math.min(e.cap(), e.stock[job] + amount);
		delivered += amount;
		Politics.get(level.getServer()).setDirty();
	}

	/** The player hands over logs, planks, stone and iron from his inventory (as much as the store has room for). */
	public static void donate(ServerLevel level, ServerPlayer player, Settlement s) {
		Politics p = Politics.get(level.getServer());
		if (!owner(p, s, player)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.not_yours"));
			return;
		}
		VillageEconomy e = s.eco;
		int cap = e.cap();
		int[] got = new int[3];
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			int kind;
			int worth;
			if (stack.is(ItemTags.LOGS)) {
				kind = 0;
				worth = 4;
			} else if (stack.is(ItemTags.PLANKS)) {
				kind = 0;
				worth = 1;
			} else if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(Items.STONE)
					|| stack.is(Items.STONE_BRICKS) || stack.is(Items.ANDESITE) || stack.is(Items.DIORITE) || stack.is(Items.GRANITE)) {
				kind = 1;
				worth = 1;
			} else if (stack.is(Items.IRON_INGOT) || stack.is(Items.RAW_IRON)) {
				kind = 2;
				worth = 1;
			} else if (stack.is(Items.IRON_BLOCK) || stack.is(Items.RAW_IRON_BLOCK)) {
				kind = 2;
				worth = 9;
			} else {
				continue;
			}
			int room = (cap - e.stock[kind]) / worth;
			int k = Math.min(room, stack.getCount());
			if (k <= 0) {
				continue;
			}
			stack.shrink(k);
			e.stock[kind] += k * worth;
			got[kind] += k * worth;
		}
		p.setDirty();
		if (got[0] + got[1] + got[2] == 0) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.nothing_to_give"));
		} else {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.donated", got[0], got[1], got[2]));
			level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.9f);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Hangar

	/** Wood, stone, iron and seconds to make a vehicle in the hangar. */
	public static int[] vehicleCost(VehicleType t) {
		return switch (t) {
			case GEPARD -> new int[]{20, 30, 90, 60};
			case HIMARS -> new int[]{20, 40, 110, 75};
			case NASAMS -> new int[]{20, 40, 120, 80};
			case IRIS_T -> new int[]{20, 40, 130, 85};
			case PATRIOT -> new int[]{30, 60, 160, 100};
			case SHAHED -> new int[]{40, 30, 90, 60};
			case ISKANDER, KALIBR -> new int[]{30, 60, 180, 110};
			case P18 -> new int[]{30, 30, 70, 50};
			case SENTINEL -> new int[]{20, 20, 80, 50};
			case ST68, KUPOL -> new int[]{30, 40, 100, 70};
			case TRML4D -> new int[]{20, 40, 110, 70};
			case MPQ65 -> new int[]{30, 50, 130, 80};
			case MFG -> new int[]{20, 10, 30, 25};
			case ZU23 -> new int[]{20, 20, 60, 40};
			case AVENGER, STRELA10 -> new int[]{20, 20, 70, 45};
			case SHILKA, OSA -> new int[]{20, 30, 90, 55};
			case TOR, TUNGUSKA, PANTSIR -> new int[]{30, 40, 130, 80};
			case BUK -> new int[]{30, 50, 150, 90};
			case S300, SAMPT -> new int[]{40, 60, 180, 110};
		};
	}

	public static boolean orderVehicle(ServerLevel level, ServerPlayer player, Settlement s, VehicleType type) {
		Politics p = Politics.get(level.getServer());
		if (!owner(p, s, player)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.not_yours"));
			return false;
		}
		VillageEconomy e = s.eco;
		if (e.count(BuildingType.HANGAR) == 0) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.no_hangar"));
			return false;
		}
		if (e.hangar.size() >= MAX_HANGAR_QUEUE) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.queue_full"));
			return false;
		}
		boolean free = player.getAbilities().instabuild;
		int[] cost = vehicleCost(type);
		if (!free && !canPay(e, cost[0], cost[1], cost[2])) {
			player.sendOverlayMessage(missing(e, cost[0], cost[1], cost[2]));
			return false;
		}
		if (!free) {
			pay(e, cost[0], cost[1], cost[2]);
		}
		// Creative orders are marked (+100): made fast, and with unlimited missiles like a vehicle from the creative tab.
		e.hangar.add(type.ordinal() + (free ? 100 : 0));
		p.setDirty();
		player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.vehicle_ordered",
				Component.translatable("entity.airdefense." + type.id), s.name));
		return true;
	}

	private static void hangarStep(ServerLevel level, Politics p, Settlement s) {
		VillageEconomy e = s.eco;
		Building hangar = e.first(BuildingType.HANGAR);
		if (hangar == null || !level.isLoaded(hangar.middle())) {
			return;
		}
		int code = e.hangar.getFirst();
		boolean free = code >= 100;
		VehicleType type = VehicleType.byId(code % 100);
		int time = vehicleCost(type)[3];
		if (e.hangarProgress < time) {
			e.hangarProgress += free ? 10 : 1;
			p.setDirty();
			if (level.getGameTime() % 60 == 11) {
				level.playSound(null, hangar.middle(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 0.8f + level.getRandom().nextFloat() * 0.3f);
			}
			return;
		}
		// Ready: it rolls out once the hangar floor is clear.
		BlockPos spot = hangar.at(0, 1, 8);
		AABB inside = new AABB(Vec3.atCenterOf(hangar.at(-6, 1, 1)), Vec3.atCenterOf(hangar.at(6, 6, 15))).inflate(0.5);
		if (!level.getEntitiesOfClass(VehicleEntity.class, inside, VehicleEntity::isAlive).isEmpty()) {
			if (level.getGameTime() % 400 == 11) {
				tellOwner(level, p, s, Component.translatable("nation.airdefense.eco.hangar_blocked", s.name));
			}
			return;
		}
		VehicleEntity v = VehicleEntity.spawn(level, type, Vec3.atBottomCenterOf(spot), hangar.facing.getOpposite().toYRot());
		v.setUnlimited(free);
		e.hangar.removeFirst();
		e.hangarProgress = 0;
		vehiclesMade++;
		p.setDirty();
		level.playSound(null, spot, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 0.7f);
		tellOwner(level, p, s, Component.translatable("nation.airdefense.eco.vehicle_ready", Component.translatable("entity.airdefense." + type.id), s.name));
	}

	private static void tellOwner(ServerLevel level, Politics p, Settlement s, Component message) {
		Country c = p.country(s.country);
		if (c != null && c.owner != null && level.getServer().getPlayerList().getPlayer(c.owner) instanceof ServerPlayer owner) {
			owner.sendSystemMessage(message);
		}
	}

	public static int hangarPercent(Settlement s) {
		VillageEconomy e = s.eco;
		if (e.hangar.isEmpty()) {
			return 0;
		}
		int time = vehicleCost(VehicleType.byId(e.hangar.getFirst() % 100))[3];
		return Math.min(100, e.hangarProgress * 100 / time);
	}

	// ------------------------------------------------------------------------------------------------
	// Beds, births, barracks

	public static int beds(ServerLevel level, Settlement s, boolean freeOnly) {
		return (int) level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), s.center, Settlement.RADIUS + 8,
				freeOnly ? PoiManager.Occupancy.HAS_SPACE : PoiManager.Occupancy.ANY);
	}

	/**
	 * A village of a made-up country (or an independent one) grows by itself: every five minutes or so, while somebody
	 * is around, it may start a house (or a barracks, a warehouse) - up to three buildings of its own.
	 */
	private static void aiGrowth(ServerLevel level, Politics p, Settlement s) {
		if (owned(p, s) || !level.isLoaded(s.flag) || s.eco.active() != null || s.eco.buildings.size() >= 3
				|| level.getRandom().nextInt(100) >= 20 || Nations.villagers(level, s).size() < 3) {
			return;
		}
		int roll = level.getRandom().nextInt(100);
		BuildingType type;
		if (beds(level, s, true) < 2) {
			type = roll < 60 ? BuildingType.SMALL_HOUSE : BuildingType.HOUSE;
		} else if (s.country >= 0 && s.eco.count(BuildingType.BARRACKS) == 0 && roll < 35) {
			type = BuildingType.BARRACKS;
		} else {
			type = roll < 50 ? BuildingType.SMALL_HOUSE : roll < 80 ? BuildingType.HOUSE : BuildingType.WAREHOUSE;
		}
		Building b = Sites.find(level, p, s, type, p.newId(), true);
		if (b != null) {
			s.eco.buildings.add(b);
			p.setDirty();
			aiBuilt++;
			AirDefense.LOGGER.info("[airdefense] {} starts a {} by itself", s.name, type.id);
		}
	}

	/** Is the village a player's (his villages have children more easily, and they grow up faster)? */
	static boolean owned(Politics p, Settlement s) {
		Country c = p.country(s.country);
		return c != null && c.owner != null;
	}

	/**
	 * Chance of a birth every 10 seconds: in a player's village about one child in 3-4 minutes while there are free
	 * beds (no food or fuss needed), every maternity hospital adds one in about 80 seconds.
	 */
	static float birthChance(Politics p, Settlement s) {
		int hospitals = s.eco.count(BuildingType.HOSPITAL);
		float chance = (owned(p, s) ? 0.045f : 0f) + 0.12f * hospitals;
		return Math.min(0.6f, chance);
	}

	/** About how many seconds between births (0 = none: no free beds or too few grown-ups). */
	public static int birthEvery(ServerLevel level, Politics p, Settlement s) {
		float chance = birthChance(p, s);
		if (chance <= 0 || beds(level, s, true) <= babies(level, s) || adults(level, s) < 2) {
			return 0;
		}
		return Math.round(10 / chance);
	}

	static int babies(ServerLevel level, Settlement s) {
		int n = 0;
		for (Villager v : Nations.villagers(level, s)) {
			if (v.isBaby()) {
				n++;
			}
		}
		return n;
	}

	static int adults(ServerLevel level, Settlement s) {
		int n = workers(level, s).size();
		for (Villager v : Nations.villagers(level, s)) {
			if (!v.isBaby()) {
				n++;
			}
		}
		return n;
	}

	/** Children in a player's village grow up four times as fast (in about five minutes). */
	public static void growUp(ServerLevel level, Settlement s) {
		for (Villager v : Nations.villagers(level, s)) {
			if (v.isBaby()) {
				v.ageUp(30);
			}
		}
	}

	/** Food a villager takes from your hand (and the food points it is worth to him). */
	public static int foodPoints(ItemStack stack) {
		if (stack.is(Items.BREAD)) {
			return 4;
		}
		return stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT) ? 1 : 0;
	}

	/** The player feeds a villager: well-fed villagers are ready to have children (the game's own breeding). */
	public static void feed(ServerLevel level, ServerPlayer player, Villager v, ItemStack stack) {
		int points = foodPoints(stack);
		if (points <= 0 || v.isBaby()) {
			return;
		}
		ItemStack one = stack.copyWithCount(1);
		if (!v.getInventory().canAddItem(one)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.full_up"));
			return;
		}
		v.getInventory().addItem(one);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		int have = 0;
		for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
			ItemStack it = v.getInventory().getItem(i);
			have += foodPoints(it) * it.getCount();
		}
		level.sendParticles(ParticleTypes.HEART, v.getX(), v.getY() + 2.0, v.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
		level.playSound(null, v.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8f, 1.1f);
		player.sendOverlayMessage(Component.translatable(have >= 12 ? "nation.airdefense.eco.fed_ready" : "nation.airdefense.eco.fed",
				Math.min(have, 12), 12));
		fed++;
	}

	/** A baby is born (in the maternity hospital, or by the village square), if there is a free bed and grown-ups to look after it. */
	public static boolean birth(ServerLevel level, Politics p, Settlement s) {
		Building hospital = s.eco.first(BuildingType.HOSPITAL);
		if (hospital != null && !level.isLoaded(hospital.middle()) || !level.isLoaded(s.flag)) {
			return false;
		}
		List<Villager> villagers = Nations.villagers(level, s);
		int babies = 0;
		int adults = workers(level, s).size();
		for (Villager v : villagers) {
			if (v.isBaby()) {
				babies++;
			} else {
				adults++;
			}
		}
		if (adults < 2 || beds(level, s, true) <= babies) {
			return false;
		}
		Villager baby = EntityTypes.VILLAGER.create(level, EntitySpawnReason.BREEDING);
		if (baby == null) {
			return false;
		}
		BlockPos at;
		if (hospital != null) {
			at = hospital.at(0, 1, 2);
		} else {
			int x = s.flag.getX() + level.getRandom().nextInt(5) - 2;
			int z = s.flag.getZ() + level.getRandom().nextInt(5) - 2;
			at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		}
		baby.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0);
		baby.setAge(-24000);
		level.addFreshEntity(baby);
		level.sendParticles(ParticleTypes.HEART, baby.getX(), baby.getY() + 0.8, baby.getZ(), 5, 0.4, 0.3, 0.4, 0.02);
		level.playSound(null, at, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1.3f);
		s.eco.births++;
		born++;
		p.setDirty();
		Country c = p.country(s.country);
		if (c != null && c.owner != null && level.getServer().getPlayerList().getPlayer(c.owner) instanceof ServerPlayer owner
				&& owner.distanceToSqr(Vec3.atCenterOf(at)) < 96 * 96) {
			owner.sendOverlayMessage(Component.translatable("nation.airdefense.eco.born", s.name));
		}
		return true;
	}

	/** Soldiers and guards near a barracks get their wounds seen to. */
	private static void barracksHeal(ServerLevel level, Settlement s) {
		for (Building b : s.eco.buildings) {
			if (!b.done || b.type != BuildingType.BARRACKS || !level.isLoaded(b.middle())) {
				continue;
			}
			for (SoldierEntity e : level.getEntitiesOfClass(SoldierEntity.class, new AABB(b.middle()).inflate(20, 8, 20),
					e -> e.isAlive() && e.role() != SoldierEntity.BANDIT && e.country() == s.country && e.getHealth() < e.getMaxHealth())) {
				e.heal(1f);
			}
		}
	}

	/** Every barracks lets four more of the village's people be called up. */
	public static int barracksBonus(Settlement s) {
		return 4 * s.eco.count(BuildingType.BARRACKS);
	}

	/** For the map and the test: workers of all villages near a point. */
	public static List<WorkerEntity> workersNear(ServerLevel level, BlockPos pos, double range) {
		return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(WorkerEntity.class),
				w -> w.isAlive() && w.distanceToSqr(Vec3.atCenterOf(pos)) < range * range));
	}
}
