package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.client.fx.CityAmbience;
import com.stasdoto.airdefense.client.siren.SirenClient;
import com.stasdoto.airdefense.nation.PedestrianEntity;
import com.stasdoto.airdefense.registry.ModEntities;

/**
 * 1.37: the passers-by round the player in a town. Made, walked and taken away here on the client only: they walk the
 * pavements block by block, keep going straight, turn at the corners, cross the street at a zebra now and then. How many
 * there are goes with the hour: the morning and evening rush, fewer by day, few at night. When the sirens sound they
 * hurry and are gone indoors in a few seconds; nobody comes out until the all clear.
 */
public final class Pedestrians {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<PedestrianEntity> ALL = new ArrayList<>();
	private static final int[] DX = {0, 1, 0, -1};
	private static final int[] DZ = {-1, 0, 1, 0};
	private static final Block WALKWAY = Blocks.POLISHED_ANDESITE;
	private static final Block KERB = Blocks.SMOOTH_STONE;
	private static final Block ASPHALT = Blocks.CONCRETE.pick(DyeColor.GRAY);
	private static final Block MARK = Blocks.CONCRETE.pick(DyeColor.WHITE);
	private static final Block ASPHALT_SLAB = Blocks.CONCRETE_SLAB.pick(DyeColor.GRAY);
	private static final Block MARK_SLAB = Blocks.CONCRETE_SLAB.pick(DyeColor.WHITE);
	private static final String[] CLOTHES = {"none", "none", "none", "none", "librarian", "cartographer", "cleric", "farmer", "fisherman",
			"shepherd", "fletcher", "toolsmith"};
	private static int nextId = -1_000_000;
	private static ClientLevel lastLevel;
	private static int ticks;
	/** For the tests. */
	public static int made;
	public static int gone;
	public static int crossings;
	/** For the tests: as if the sirens were heard. */
	public static boolean forceAlert;
	/** 1.50: for the tests - how many sat down, waited at a stop, played, stood by a door, walked a dog. */
	public static final int[] ACTIVITIES = new int[6];

	// 1.50 "A living town": the places round the player where people do something - benches, bus stops, playgrounds,
	// the doors of the blocks of flats (x, y, z, facing as 2D data value), looked over every five seconds.
	private static final List<int[]> BENCHES = new ArrayList<>();
	private static final List<int[]> STOPS = new ArrayList<>();
	private static final List<int[]> PLAY = new ArrayList<>();
	private static final List<int[]> DOORS = new ArrayList<>();
	private static final java.util.Set<Long> TAKEN = new java.util.HashSet<>();
	private static Vec3 scannedAt;
	public static final int WALK = 0;
	public static final int SIT = 1;
	public static final int WAIT = 2;
	public static final int PLAYING = 3;
	public static final int STAND = 4;

	private Pedestrians() {
	}

	public static void init() {
		PedestrianEntity.clientTick = Pedestrians::step;
		ClientTickEvents.END_CLIENT_TICK.register(Pedestrians::tick);
	}

	public static int count() {
		return ALL.size();
	}

	/** How many passers-by about the player at this hour (0 = midnight). */
	static int wanted(long hour) {
		// 1.46: busier streets - the great cities are crowded.
		if (hour < 5) {
			return 3;
		}
		if (hour < 7) {
			return 12;
		}
		if (hour < 9) {
			return 50;
		}
		if (hour < 17) {
			return 34;
		}
		if (hour < 19) {
			return 50;
		}
		if (hour < 22) {
			return 20;
		}
		return 6;
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			ALL.clear();
			lastLevel = level;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		ALL.removeIf(Entity::isRemoved);
		if (++ticks % 20 != 0) {
			return;
		}
		Vec3 me = mc.player.position();
		boolean alert = SirenClient.alertHeard() || forceAlert;
		long hour = (Math.floorMod(level.getOverworldClockTime(), 24000L) / 1000 + 6) % 24;
		int target = !CityAmbience.inTown || alert ? 0 : wanted(hour);
		for (PedestrianEntity p : ALL) {
			double d = p.position().distanceTo(me);
			if (d > 48 || Math.abs(p.getY() - me.y) > 40) {
				remove(level, p);
			} else if (alert && !p.hurry) {
				p.hurry = true;
				p.hurryTicks = 60 + RANDOM.nextInt(100);
			}
		}
		ALL.removeIf(Entity::isRemoved);
		if (target > 0 && (scannedAt == null || ticks % 100 == 0 || scannedAt.distanceToSqr(me) > 12 * 12)) {
			scan(level, me);
		}
		for (int k = 0; k < 3 && ALL.size() < target; k++) {
			if (!tryActivity(mc, level, me, hour)) {
				trySpawn(mc, level, me);
			}
		}
		if (ALL.size() > target + 2) {
			// One too many: the farthest goes (out of sight, most likely).
			PedestrianEntity far = null;
			for (PedestrianEntity p : ALL) {
				if (far == null || p.distanceToSqr(me) > far.distanceToSqr(me)) {
					far = p;
				}
			}
			if (far != null && far.distanceToSqr(me) > 24 * 24) {
				remove(level, far);
			}
		}
	}

	private static void remove(ClientLevel level, PedestrianEntity p) {
		level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
		p.discard();
		if (p.pet != null) {
			level.removeEntity(p.pet.getId(), Entity.RemovalReason.DISCARDED);
			p.pet.discard();
			p.pet = null;
		}
		TAKEN.remove(p.spot);
		gone++;
	}

	// ------------------------------------------------------------------------------------------------
	// 1.50: what people do

	private static void scan(ClientLevel level, Vec3 me) {
		scannedAt = me;
		BENCHES.clear();
		STOPS.clear();
		PLAY.clear();
		DOORS.clear();
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		int cx = Mth.floor(me.x);
		int cy = Mth.floor(me.y);
		int cz = Mth.floor(me.z);
		for (int x = cx - 40; x <= cx + 40; x++) {
			for (int z = cz - 40; z <= cz + 40; z++) {
				for (int y = cy - 6; y <= cy + 6; y++) {
					BlockState st = level.getBlockState(q.set(x, y, z));
					Block b = st.getBlock();
					if (b == Blocks.AIR) {
						continue;
					}
					if (b == com.stasdoto.airdefense.street.StreetBlocks.BENCH_PARK || b == com.stasdoto.airdefense.street.StreetBlocks.BENCH_SOVIET
							|| b == com.stasdoto.airdefense.street.StreetBlocks.BENCH_MODERN) {
						BENCHES.add(new int[]{x, y, z, st.getValue(com.stasdoto.airdefense.street.StreetBlock.FACING).get2DDataValue()});
					} else if (b == com.stasdoto.airdefense.street.StreetBlocks.BUS_STOP_MODERN || b == com.stasdoto.airdefense.street.StreetBlocks.BUS_STOP_SOVIET) {
						STOPS.add(new int[]{x, y, z, st.getValue(com.stasdoto.airdefense.street.StreetBlock.FACING).get2DDataValue()});
					} else if (b == com.stasdoto.airdefense.street.StreetBlocks.SWING || b == com.stasdoto.airdefense.street.StreetBlocks.SLIDE
							|| b == com.stasdoto.airdefense.street.StreetBlocks.SANDBOX || b == com.stasdoto.airdefense.street.StreetBlocks.ROUNDABOUT
							|| b == com.stasdoto.airdefense.street.StreetBlocks.CLIMBING_FRAME || b == com.stasdoto.airdefense.street.StreetBlocks.SEESAW) {
						PLAY.add(new int[]{x, y, z, 0});
					} else if (b == Blocks.CONCRETE.pick(DyeColor.LIME) && Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0) {
						// A yard's playground (its rubber floor).
						PLAY.add(new int[]{x, y + 1, z, 0});
					} else if (b == Blocks.IRON_DOOR && st.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
							== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
						net.minecraft.core.Direction out = st.getValue(net.minecraft.world.level.block.DoorBlock.FACING).getOpposite();
						DOORS.add(new int[]{x, y, z, out.get2DDataValue()});
					}
				}
			}
		}
	}

	/** The share of the people about doing one thing or another at this hour (sit, wait, play, stand, walk a dog). */
	private static int[] odds(long hour) {
		if (hour < 6 || hour >= 23) {
			return new int[]{0, 4, 0, 4, 4};
		}
		if (hour < 9) {
			// The morning: to work - many at the stops, children to school, dogs out before work.
			return new int[]{4, 26, 0, 6, 14};
		}
		if (hour < 17) {
			// The day: the old on the benches, children in the playgrounds.
			return new int[]{24, 12, 18, 8, 8};
		}
		if (hour < 20) {
			// The evening: home from work, a chat by the door, a walk with the dog.
			return new int[]{18, 16, 12, 14, 14};
		}
		return new int[]{12, 6, 0, 16, 12};
	}

	private static boolean tryActivity(Minecraft mc, ClientLevel level, Vec3 me, long hour) {
		int[] o = odds(hour);
		int roll = RANDOM.nextInt(100);
		int what = -1;
		int acc = 0;
		for (int i = 0; i < o.length; i++) {
			acc += o[i];
			if (roll < acc) {
				what = i;
				break;
			}
		}
		if (what < 0) {
			return false;
		}
		List<int[]> from = switch (what) {
			case 0 -> BENCHES;
			case 1 -> STOPS;
			case 2 -> PLAY;
			case 3 -> DOORS;
			default -> null;
		};
		if (from == null) {
			// A dog walker: an ordinary passer-by with his dog.
			PedestrianEntity p = spawnWalker(mc, level, me);
			if (p != null) {
				walkDog(level, p);
			}
			return p != null;
		}
		if (from.isEmpty()) {
			return false;
		}
		float look = mc.player.getYRot();
		for (int attempt = 0; attempt < 4; attempt++) {
			int[] spot = from.get(RANDOM.nextInt(from.size()));
			long key = BlockPos.asLong(spot[0], spot[1], spot[2]);
			double dx = spot[0] + 0.5 - me.x;
			double dz = spot[2] + 0.5 - me.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			double dirYaw = Math.toDegrees(Math.atan2(-dx, dz));
			boolean ahead = Math.abs(Mth.wrapDegrees((float) dirYaw - look)) < 55;
			if (TAKEN.contains(key) || d < 6 || d > 34 || ahead && d < 22) {
				continue;
			}
			net.minecraft.core.Direction f = net.minecraft.core.Direction.from2DDataValue(spot[3]);
			switch (what) {
				case 0 -> {
					PedestrianEntity p = make(level, spot[0], spot[1], spot[2], false, hour);
					p.activity = SIT;
					p.sitting = true;
					p.activityTicks = 600 + RANDOM.nextInt(2400);
					p.faceYaw = f.toYRot();
					p.setPos(spot[0] + 0.5, spot[1] - 0.18, spot[2] + 0.5);
					claim(p, key);
					ACTIVITIES[0]++;
				}
				case 1 -> {
					PedestrianEntity p = make(level, spot[0], spot[1], spot[2], false, hour);
					p.activity = WAIT;
					p.activityTicks = 400 + RANDOM.nextInt(1600);
					p.faceYaw = f.toYRot() + RANDOM.nextInt(60) - 30;
					p.setPos(spot[0] + 0.3 + RANDOM.nextDouble() * 0.4, spot[1], spot[2] + 0.3 + RANDOM.nextDouble() * 0.4);
					// Up to three at a stop: the spot is taken only by the third.
					if (RANDOM.nextInt(3) == 0) {
						claim(p, key);
					}
					ACTIVITIES[1]++;
				}
				case 2 -> {
					// Children round the playground (on the grass or the sand beside the swings).
					int[] at = near(level, spot[0], spot[1], spot[2], 3);
					if (at == null) {
						continue;
					}
					PedestrianEntity p = make(level, at[0], at[1], at[2], true, hour);
					p.activity = PLAYING;
					p.activityTicks = 800 + RANDOM.nextInt(2000);
					p.homeX = spot[0];
					p.homeZ = spot[2];
					ACTIVITIES[2]++;
				}
				default -> {
					// By the door: one or two standing a little way out, having a smoke or a chat.
					int ox = spot[0] + f.getStepX() * 2;
					int oz = spot[2] + f.getStepZ() * 2;
					int y = next(level, ox, spot[1], oz, false);
					if (y == Integer.MIN_VALUE) {
						continue;
					}
					PedestrianEntity a = make(level, ox, y, oz, false, hour);
					a.activity = STAND;
					a.activityTicks = 500 + RANDOM.nextInt(1500);
					a.smoker = RANDOM.nextInt(2) == 0;
					claim(a, key);
					net.minecraft.core.Direction side = f.getClockWise();
					int bx = ox + side.getStepX();
					int bz = oz + side.getStepZ();
					if (RANDOM.nextInt(2) == 0 && next(level, bx, y, bz, false) != Integer.MIN_VALUE) {
						PedestrianEntity b = make(level, bx, y, bz, false, hour);
						b.activity = STAND;
						b.activityTicks = a.activityTicks;
						b.faceYaw = side.getOpposite().toYRot();
						a.faceYaw = side.toYRot();
					} else {
						a.faceYaw = f.toYRot();
					}
					ACTIVITIES[3]++;
				}
			}
			return true;
		}
		return false;
	}

	/** 1.50: the people within {@code r} of (x, z) (for the traffic: who is crossing in front). */
	static List<PedestrianEntity> near(double x, double z, double r) {
		List<PedestrianEntity> out = new ArrayList<>();
		for (PedestrianEntity p : ALL) {
			double dx = p.getX() - x;
			double dz = p.getZ() - z;
			if (dx * dx + dz * dz <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	/** 1.50: a playground within {@code r} of the player (for the children's voices), or null. */
	public static Vec3 playgroundNear(Vec3 me, double r) {
		for (int[] p : PLAY) {
			if (Math.hypot(p[0] + 0.5 - me.x, p[2] + 0.5 - me.z) < r) {
				return new Vec3(p[0] + 0.5, p[1] + 1, p[2] + 0.5);
			}
		}
		return null;
	}

	/** 1.50: the bus stops round the player (x, y, z, facing). */
	static List<int[]> stops() {
		return STOPS;
	}

	/** 1.50: a bus has come to the stop at (x, z): those waiting there get on (and are gone). */
	static void board(double x, double z) {
		for (PedestrianEntity p : new ArrayList<>(ALL)) {
			if (p.activity == WAIT && Math.abs(p.getX() - x) < 2 && Math.abs(p.getZ() - z) < 2 && !p.isRemoved()) {
				remove((ClientLevel) p.level(), p);
				ACTIVITIES[5]++;
			}
		}
	}

	private static void claim(PedestrianEntity p, long key) {
		p.spot = key;
		TAKEN.add(key);
	}

	/** A free block to stand on within {@code r} of (x, y, z). */
	private static int[] near(ClientLevel level, int x, int y, int z, int r) {
		for (int k = 0; k < 10; k++) {
			int nx = x + RANDOM.nextInt(2 * r + 1) - r;
			int nz = z + RANDOM.nextInt(2 * r + 1) - r;
			for (int ny = y + 1; ny >= y - 1; ny--) {
				BlockPos g = new BlockPos(nx, ny - 1, nz);
				if (!level.getBlockState(g).getCollisionShape(level, g).isEmpty() && feetFree(level, nx, ny, nz)) {
					return new int[]{nx, ny, nz};
				}
			}
		}
		return null;
	}

	private static PedestrianEntity spawnWalker(Minecraft mc, ClientLevel level, Vec3 me) {
		int before = ALL.size();
		trySpawn(mc, level, me);
		return ALL.size() > before ? ALL.getLast() : null;
	}

	/** A dog on a lead: the game's own wolf, tame (its collar on), walked beside him. */
	private static void walkDog(ClientLevel level, PedestrianEntity p) {
		try {
			var dog = net.minecraft.world.entity.EntityTypes.WOLF.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
			if (dog == null) {
				return;
			}
			dog.setId(nextId--);
			dog.setTame(true, false);
			dog.noPhysics = true;
			dog.setNoGravity(true);
			dog.snapTo(p.getX() + 1, p.getY(), p.getZ(), p.getYRot(), 0);
			level.addEntity(dog);
			p.pet = dog;
			p.speed = 0.07;
			ACTIVITIES[4]++;
		} catch (RuntimeException e) {
			// No dog then.
		}
	}

	/** One tick of someone not just walking. True if he is still at it. */
	private static boolean busy(ClientLevel level, PedestrianEntity p) {
		if (p.activity == WALK) {
			return false;
		}
		if (p.hurry || --p.activityTicks <= 0) {
			// Done: back to walking, from the nearest pavement.
			p.sitting = false;
			p.activity = WALK;
			TAKEN.remove(p.spot);
			p.spot = Long.MIN_VALUE;
			int bx = Mth.floor(p.getX());
			int bz = Mth.floor(p.getZ());
			int by = (int) Math.round(p.getY() + 0.2);
			for (int r = 0; r <= 2; r++) {
				for (int k = 0; k < 4; k++) {
					int nx = bx + DX[k] * r;
					int nz = bz + DZ[k] * r;
					int ny = next(level, nx, by, nz, false);
					if (ny != Integer.MIN_VALUE) {
						p.setPos(p.getX(), ny, p.getZ());
						p.toX = nx;
						p.toZ = nz;
						p.dir = k;
						return false;
					}
				}
			}
			// Nowhere to walk from here: he goes in (out of the way, unseen).
			p.hurry = true;
			p.hurryTicks = 1;
			return false;
		}
		Vec3 at = p.position();
		switch (p.activity) {
			case PLAYING -> {
				// Running about the playground, a jump now and then.
				double tx = p.toX + 0.5;
				double tz = p.toZ + 0.5;
				double dx = tx - at.x;
				double dz = tz - at.z;
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist < 0.2 || p.tickCount % 60 == 0) {
					int[] n = near(level, p.homeX, (int) Math.round(at.y), p.homeZ, 4);
					if (n != null) {
						p.toX = n[0];
						p.toZ = n[2];
					}
					p.walkAnimation.update(0f, 0.4f, 2f);
					return true;
				}
				double sp = 0.11;
				double ny = groundAt(level, Mth.floor(at.x + dx / dist * sp), Mth.floor(at.z + dz / dist * sp), (int) Math.round(at.y));
				double hop = p.tickCount % 40 < 8 ? Math.sin((p.tickCount % 40) / 8.0 * Math.PI) * 0.35 : 0;
				p.setPos(at.x + dx / dist * sp, ny + hop, at.z + dz / dist * sp);
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				p.setYRot(Mth.rotLerp(0.4f, p.getYRot(), yaw));
				p.yBodyRot = p.getYRot();
				p.setYHeadRot(p.getYRot());
				p.walkAnimation.update(0.9f, 0.4f, 2f);
			}
			default -> {
				// Sitting, waiting, standing: still, the head turning now and then; a puff of smoke.
				p.setYRot(Mth.rotLerp(0.3f, p.getYRot(), p.faceYaw));
				p.yBodyRot = p.getYRot();
				float head = p.faceYaw + (float) Math.sin((p.tickCount + p.getId()) * 0.02) * (p.activity == WAIT ? 60 : 35);
				p.setYHeadRot(Mth.rotLerp(0.1f, p.getYHeadRot(), head));
				p.walkAnimation.update(0f, 0.4f, 1f);
				if (p.smoker && p.tickCount % 50 == 0) {
					level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y + 1.6, at.z, 0, 0.02, 0);
				}
			}
		}
		return true;
	}

	/** Leads the dog beside its owner. */
	private static void dog(PedestrianEntity p) {
		Entity dog = p.pet;
		if (dog == null) {
			return;
		}
		float yaw = p.getYRot();
		double rad = Math.toRadians(yaw);
		// A step behind and to the side, trotting.
		double tx = p.getX() - (-Math.sin(rad)) * 1.2 + Math.cos(rad) * 0.8;
		double tz = p.getZ() - Math.cos(rad) * 1.2 + Math.sin(rad) * 0.8;
		double dx = tx - dog.getX();
		double dz = tz - dog.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		double sp = Math.min(d, 0.12 + d * 0.1);
		dog.setOldPosAndRot();
		if (d > 0.05) {
			dog.setPos(dog.getX() + dx / d * sp, p.getY(), dog.getZ() + dz / d * sp);
			dog.setYRot(Mth.rotLerp(0.3f, dog.getYRot(), (float) Math.toDegrees(Math.atan2(-dx, dz))));
		}
		if (dog instanceof net.minecraft.world.entity.LivingEntity le) {
			le.yBodyRot = dog.getYRot();
			le.setYHeadRot(dog.getYRot());
			le.walkAnimation.update((float) Math.min(1.0, sp * 6), 0.4f, 1f);
		}
	}

	private static void trySpawn(Minecraft mc, ClientLevel level, Vec3 me) {
		float look = mc.player.getYRot();
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = RANDOM.nextDouble() * Math.PI * 2;
			// Not right in front of the player's eyes (nobody pops up out of thin air in view).
			double dirYaw = Math.toDegrees(Math.atan2(-Math.sin(a), Math.cos(a)));
			boolean ahead = Math.abs(Mth.wrapDegrees((float) dirYaw - look)) < 50;
			double r = (ahead ? 26 : 8) + RANDOM.nextDouble() * (ahead ? 10 : 20);
			int x = Mth.floor(me.x + Math.cos(a) * r);
			int z = Mth.floor(me.z + Math.sin(a) * r);
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			for (int y = top; y >= top - 6; y--) {
				if (walkable(level, x, y, z) && feetFree(level, x, y, z)) {
					spawn(level, x, y, z);
					return;
				}
			}
		}
	}

	private static void spawn(ClientLevel level, int x, int y, int z) {
		long hour = (Math.floorMod(level.getOverworldClockTime(), 24000L) / 1000 + 6) % 24;
		make(level, x, y, z, RANDOM.nextInt(100) < 12, hour);
	}

	/** A person at (x, y, z) dressed for the season, the hour and who he is; walking off in a random direction. */
	private static PedestrianEntity make(ClientLevel level, int x, int y, int z, boolean kid, long hour) {
		PedestrianEntity p = new PedestrianEntity(ModEntities.PEDESTRIAN, level);
		p.setId(nextId--);
		p.look = RANDOM.nextInt();
		p.kid = kid;
		String type = climate(level, new BlockPos(x, y, z));
		p.outfit = clothes(p, type, hour) + ";" + type;
		p.speed = (p.kid ? 0.09 : 0.065) + RANDOM.nextDouble() * 0.03;
		p.dir = RANDOM.nextInt(4);
		p.toX = x;
		p.toZ = z;
		p.snapTo(x + 0.5, y, z + 0.5, p.dir * 90f + 180f, 0f);
		p.setYHeadRot(p.getYRot());
		p.yBodyRot = p.getYRot();
		level.addEntity(p);
		ALL.add(p);
		made++;
		return p;
	}

	/** 1.50: what he wears - a coat in winter, a T-shirt in a hot summer, suits in the morning, tracksuits, the old ladies' kerchiefs, school bags. */
	private static String clothes(PedestrianEntity p, String type, long hour) {
		int season = com.stasdoto.airdefense.nation.Seasons.season;
		boolean female = SkinBaker.Person.of(p.look).female();
		int r = RANDOM.nextInt(100);
		if (p.kid) {
			return hour >= 7 && hour < 15 && r < 60 ? "school" : season == com.stasdoto.airdefense.nation.Seasons.WINTER || type.equals("snow") ? "coat"
					: season == com.stasdoto.airdefense.nation.Seasons.SUMMER && r < 50 ? "summer" : "none";
		}
		if (season == com.stasdoto.airdefense.nation.Seasons.WINTER || type.equals("snow")) {
			return r < 75 ? "coat" : female && r < 85 ? "babushka" : "none";
		}
		if (female && r < 10) {
			return "babushka";
		}
		if ((hour >= 7 && hour < 10 || hour >= 17 && hour < 19) && r < 30) {
			return "suit";
		}
		if (r < 38) {
			return "tracksuit";
		}
		if (season == com.stasdoto.airdefense.nation.Seasons.SUMMER && r < 70 || type.equals("desert") && r < 60) {
			return "summer";
		}
		if (season == com.stasdoto.airdefense.nation.Seasons.AUTUMN && r < 60) {
			return "coat";
		}
		return CLOTHES[RANDOM.nextInt(CLOTHES.length)];
	}

	private static String climate(ClientLevel level, BlockPos at) {
		String b = level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		if (b.contains("desert") || b.contains("badlands")) {
			return "desert";
		}
		if (b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove")) {
			return "snow";
		}
		if (b.contains("taiga")) {
			return "taiga";
		}
		if (b.contains("jungle")) {
			return "jungle";
		}
		if (b.contains("savanna")) {
			return "savanna";
		}
		if (b.contains("swamp")) {
			return "swamp";
		}
		return "plains";
	}

	/** Pavement (or a kerb) to stand on at feet height {@code y}. */
	private static boolean walkable(ClientLevel level, int x, int y, int z) {
		Block g = level.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
		return g == WALKWAY || g == KERB || com.stasdoto.airdefense.street.Weathered.WALKWAY.contains(g);
	}

	private static boolean road(ClientLevel level, int x, int y, int z) {
		Block g = level.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
		Block s = level.getBlockState(new BlockPos(x, y, z)).getBlock();
		return g == ASPHALT || g == MARK || s == ASPHALT_SLAB || s == MARK_SLAB || com.stasdoto.airdefense.street.Weathered.ROADWAY.contains(g);
	}

	private static boolean feetFree(ClientLevel level, int x, int y, int z) {
		for (int dy = 0; dy < 2; dy++) {
			BlockPos q = new BlockPos(x, y + dy, z);
			BlockState s = level.getBlockState(q);
			if (!s.getCollisionShape(level, q).isEmpty() && !(dy == 0 && (s.getBlock() == ASPHALT_SLAB || s.getBlock() == MARK_SLAB))) {
				return false;
			}
		}
		return true;
	}

	/** Feet height of the next block in that direction (the same, a step up or down), or MIN_VALUE. */
	private static int next(ClientLevel level, int x, int y, int z, boolean roadToo) {
		for (int dy : new int[]{0, 1, -1}) {
			int yy = y + dy;
			if ((walkable(level, x, yy, z) || roadToo && road(level, x, yy, z)) && feetFree(level, x, yy, z)) {
				return yy;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** One tick of one passer-by: on to the next block; at its middle, which way next. */
	private static void step(PedestrianEntity p) {
		ClientLevel level = (ClientLevel) p.level();
		if (busy(level, p)) {
			return;
		}
		dog(p);
		double sp = p.hurry ? 0.2 : p.speed;
		if (p.hurry && --p.hurryTicks <= 0) {
			// Gone indoors.
			remove(level, p);
			return;
		}
		Vec3 at = p.position();
		double tx = p.toX + 0.5;
		double tz = p.toZ + 0.5;
		double dx = tx - at.x;
		double dz = tz - at.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist <= sp) {
			p.setPos(tx, at.y, tz);
			if (!choose(level, p)) {
				p.walkAnimation.update(0f, 0.4f, 1f);
				return;
			}
			at = p.position();
			dx = p.toX + 0.5 - at.x;
			dz = p.toZ + 0.5 - at.z;
			dist = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
		}
		double nx = at.x + dx / dist * Math.min(sp, dist);
		double nz = at.z + dz / dist * Math.min(sp, dist);
		// Up or down a step on the way, smoothly.
		int ty = groundAt(level, p.toX, p.toZ, (int) Math.round(at.y));
		double ny = at.y + Mth.clamp(ty - at.y, -0.15, 0.15);
		p.setPos(nx, ny, nz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		p.setYRot(Mth.rotLerp(0.35f, p.getYRot(), yaw));
		p.yBodyRot = p.getYRot();
		p.setYHeadRot(p.getYRot());
		p.walkAnimation.update((float) Math.min(sp * 4.5, 1.0), 0.4f, p.kid ? 2f : 1f);
	}

	private static int groundAt(ClientLevel level, int x, int z, int y) {
		for (int dy : new int[]{0, 1, -1}) {
			BlockState g = level.getBlockState(new BlockPos(x, y + dy - 1, z));
			if (!g.isAir()) {
				return y + dy;
			}
		}
		return y;
	}

	/** At the middle of a block: the next one. False if there is nowhere to go (he waits and looks round). */
	private static boolean choose(ClientLevel level, PedestrianEntity p) {
		int x = p.toX;
		int z = p.toZ;
		int y = (int) Math.round(p.getY());
		if (p.crossing) {
			int nx = x + DX[p.dir];
			int nz = z + DZ[p.dir];
			int ny = next(level, nx, y, nz, true);
			if (ny != Integer.MIN_VALUE && --p.crossLeft > 0) {
				if (walkable(level, nx, ny, nz)) {
					p.crossing = false;
				}
				p.toX = nx;
				p.toZ = nz;
				return true;
			}
			p.crossing = false;
			p.dir = (p.dir + 2) % 4;
		}
		int straight = p.dir;
		int[] order = RANDOM.nextInt(100) < 85 ? new int[]{straight, (straight + 1) % 4, (straight + 3) % 4, (straight + 2) % 4}
				: RANDOM.nextBoolean() ? new int[]{(straight + 1) % 4, straight, (straight + 3) % 4, (straight + 2) % 4}
				: new int[]{(straight + 3) % 4, straight, (straight + 1) % 4, (straight + 2) % 4};
		for (int d : order) {
			int nx = x + DX[d];
			int nz = z + DZ[d];
			int ny = next(level, nx, y, nz, false);
			if (ny != Integer.MIN_VALUE) {
				p.dir = d;
				p.toX = nx;
				p.toZ = nz;
				return true;
			}
			// The kerb's edge: a zebra just ahead - cross now and then.
			if (d == straight && !p.hurry && road(level, nx, y, nz) && zebraNear(level, nx, y, nz) && RANDOM.nextInt(100) < 35) {
				p.crossing = true;
				p.crossLeft = 22;
				p.toX = nx;
				p.toZ = nz;
				crossings++;
				return true;
			}
		}
		return false;
	}

	private static boolean zebraNear(ClientLevel level, int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Block m = level.getBlockState(new BlockPos(x + dx, y - 1, z + dz)).getBlock();
				if (m == MARK || m == com.stasdoto.airdefense.street.Weathered.ROAD_MARK || m == com.stasdoto.airdefense.street.Weathered.ROAD_MARK_WORN) {
					return true;
				}
			}
		}
		return false;
	}
}
