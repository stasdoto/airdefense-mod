package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.stasdoto.airdefense.client.map.AtlasClient;
import com.stasdoto.airdefense.nation.PropEntity;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * 1.41: lorries on the highways near the player, on his own client: box lorries, tankers, tippers, each keeping to the
 * right-hand lane, coming from out of sight and going on out of it again. The highways are the atlas's (a point every
 * twelve blocks); the lorries ride on the road's own surface (slabs and bridges too).
 */
public final class Traffic {
	private static final List<PropEntity> ALL = new ArrayList<>();
	private static final Map<AtlasClient.Road, double[]> CUM = new IdentityHashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static final String[] MODELS = {"lorry_box", "lorry_box", "lorry_tank", "tipper"};
	private static int nextId = -4_000_000;
	private static ClientLevel lastLevel;
	private static int ticks;
	/** For the tests: lorries made, and as many as wanted at once (0: by the hour). */
	public static int made;
	public static volatile int force;

	private Traffic() {
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			ALL.clear();
			CUM.clear();
			lastLevel = level;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		Vec3 me = mc.player.position();
		for (PropEntity p : new ArrayList<>(ALL)) {
			if (!drive(level, p) || p.position().distanceTo(me) > 230) {
				remove(level, p);
				continue;
			}
			if (p.timer == 0 && p.position().distanceTo(me) < 30) {
				p.timer = 1;
				level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), ModSounds.CITY_CAR, SoundSource.AMBIENT, 1.6f, 0.7f, false);
			}
		}
		if (++ticks % 20 != 0 || !AtlasClient.loaded) {
			return;
		}
		long hour = (Math.floorMod(level.getOverworldClockTime(), 24000L) / 1000 + 6) % 24;
		int want = force > 0 ? force : hour >= 6 && hour < 22 ? 4 : 1;
		if (ALL.size() >= want) {
			return;
		}
		// The highway nearest the player (within 140 blocks of him).
		AtlasClient.Road best = null;
		int bestI = -1;
		double bd = 140 * 140;
		for (AtlasClient.Road r : AtlasClient.ROADS) {
			if (!r.highway() || me.x < r.minX() - 140 || me.x > r.maxX() + 140 || me.z < r.minZ() - 140 || me.z > r.maxZ() + 140) {
				continue;
			}
			for (int i = 0; i < r.xs().length; i++) {
				double dx = r.xs()[i] - me.x;
				double dz = r.zs()[i] - me.z;
				double d = dx * dx + dz * dz;
				if (d < bd) {
					bd = d;
					best = r;
					bestI = i;
				}
			}
		}
		if (best == null) {
			return;
		}
		double[] cum = CUM.computeIfAbsent(best, Traffic::cumulative);
		int dir = RANDOM.nextBoolean() ? 1 : -1;
		// From out of sight on one side, coming the player's way.
		double s = cum[bestI] - dir * (150 + RANDOM.nextInt(50));
		if (s < 0 || s > cum[cum.length - 1]) {
			dir = -dir;
			s = cum[bestI] - dir * (150 + RANDOM.nextInt(50));
			if (s < 0 || s > cum[cum.length - 1]) {
				return;
			}
		}
		PropEntity p = new PropEntity(ModEntities.PROP, level);
		p.setId(nextId--);
		p.model = MODELS[RANDOM.nextInt(MODELS.length)];
		p.way = best;
		p.s = s;
		p.dir = dir;
		p.speed = 0.8 + RANDOM.nextDouble() * 0.25;
		p.pose[1] = Double.NaN;
		if (!drive(level, p)) {
			return;
		}
		p.poseO = p.pose;
		p.setOldPosAndRot();
		level.addEntity(p);
		ALL.add(p);
		made++;
	}

	private static double[] cumulative(AtlasClient.Road r) {
		double[] c = new double[r.xs().length];
		for (int i = 1; i < c.length; i++) {
			c[i] = c[i - 1] + Math.hypot(r.xs()[i] - r.xs()[i - 1], r.zs()[i] - r.zs()[i - 1]);
		}
		return c;
	}

	private static double[] at(AtlasClient.Road r, double[] cum, double s) {
		int i = 1;
		while (i < cum.length - 1 && cum[i] < s) {
			i++;
		}
		double len = Math.max(1e-6, cum[i] - cum[i - 1]);
		double f = Math.max(0, Math.min(1, (s - cum[i - 1]) / len));
		return new double[]{r.xs()[i - 1] + (r.xs()[i] - r.xs()[i - 1]) * f, r.zs()[i - 1] + (r.zs()[i] - r.zs()[i - 1]) * f};
	}

	/** One tick along the road; false when it has run off its end or into land that is not loaded. */
	private static boolean drive(ClientLevel level, PropEntity p) {
		p.hold();
		AtlasClient.Road r = (AtlasClient.Road) p.way;
		double[] cum = CUM.computeIfAbsent(r, Traffic::cumulative);
		p.s += p.dir * p.speed;
		if (p.s < 0 || p.s > cum[cum.length - 1]) {
			return false;
		}
		double[] a = at(r, cum, p.s - 5 * p.dir);
		double[] b = at(r, cum, p.s + 5 * p.dir);
		double dx = b[0] - a[0];
		double dz = b[1] - a[1];
		double len = Math.max(1e-6, Math.hypot(dx, dz));
		// The right-hand lane: three and a half blocks right of the middle, going this way.
		double[] c = at(r, cum, p.s);
		double x = c[0] + 0.5 - dz / len * 3.5;
		double z = c[1] + 0.5 + dx / len * 3.5;
		BlockPos at = BlockPos.containing(x, 0, z);
		if (!level.hasChunkAt(at)) {
			return false;
		}
		double y = surface(level, x, z, p.pose[1]);
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = Double.isNaN(p.pose[1]) ? 0 : Math.toDegrees(Math.atan2(y - p.pose[1], p.speed)) * 0.5;
		p.move(x, Double.isNaN(p.pose[1]) ? y : p.pose[1] + (y - p.pose[1]) * 0.5, z, yaw, Math.max(-8, Math.min(8, pitch)));
		return true;
	}

	/** The road's surface at (x, z): near the height it was at (under a bridge, not on it), slabs counted. */
	private static double surface(ClientLevel level, double x, double z, double near) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (!Double.isNaN(near)) {
			int from = (int) Math.floor(near) + 2;
			for (int y = from; y >= from - 5; y--) {
				BlockState s = level.getBlockState(pos.set(bx, y, bz));
				VoxelShape shape = s.getCollisionShape(level, pos);
				if (!shape.isEmpty() && level.getBlockState(pos.set(bx, y + 1, bz)).getCollisionShape(level, pos).isEmpty()) {
					pos.set(bx, y, bz);
					return y + shape.max(net.minecraft.core.Direction.Axis.Y);
				}
			}
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
		BlockState s = level.getBlockState(pos.set(bx, top, bz));
		VoxelShape shape = s.getCollisionShape(level, pos);
		return top + (shape.isEmpty() ? 1 : shape.max(net.minecraft.core.Direction.Axis.Y));
	}

	private static void remove(ClientLevel level, PropEntity p) {
		level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
		p.discard();
		ALL.remove(p);
	}

	public static int count() {
		return ALL.size();
	}

	/** For the tests: where the lorries are. */
	public static List<double[]> where() {
		List<double[]> out = new ArrayList<>();
		for (PropEntity p : ALL) {
			out.add(new double[]{p.getX(), p.getY(), p.getZ()});
		}
		return out;
	}
}
