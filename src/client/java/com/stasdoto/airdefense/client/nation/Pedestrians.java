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
		for (int k = 0; k < 3 && ALL.size() < target; k++) {
			trySpawn(mc, level, me);
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
		gone++;
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
		PedestrianEntity p = new PedestrianEntity(ModEntities.PEDESTRIAN, level);
		p.setId(nextId--);
		p.look = RANDOM.nextInt();
		p.kid = RANDOM.nextInt(100) < 12;
		String type = climate(level, new BlockPos(x, y, z));
		p.outfit = CLOTHES[RANDOM.nextInt(CLOTHES.length)] + ";" + type;
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
		return g == WALKWAY || g == KERB;
	}

	private static boolean road(ClientLevel level, int x, int y, int z) {
		Block g = level.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
		Block s = level.getBlockState(new BlockPos(x, y, z)).getBlock();
		return g == ASPHALT || g == MARK || s == ASPHALT_SLAB || s == MARK_SLAB;
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
				if (level.getBlockState(new BlockPos(x + dx, y - 1, z + dz)).getBlock() == MARK) {
					return true;
				}
			}
		}
		return false;
	}
}
