package com.stasdoto.airdefense.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.fx.Fx;
import com.stasdoto.airdefense.fx.FxPayload;
import com.stasdoto.airdefense.registry.ModParticles;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Explosions: crater, flying debris, shock wave and sound that carries far. The visuals (fireball, smoke column,
 * dust ring, burning fragments) are drawn by each client from one {@link FxPayload} message.
 */
public final class Effects {
	private Effects() {
	}

	/** Warhead hits the ground (or a building, or someone). */
	public static void groundImpact(ServerLevel level, Entity source, Vec3 at, MissileType type) {
		RandomSource r = level.getRandom();
		float power = type.power;

		// Debris thrown out of the crater before the blast removes the blocks.
		int wanted = (int) (power * 2.2f);
		int spawned = 0;
		for (int i = 0; i < wanted * 4 && spawned < wanted; i++) {
			BlockPos p = BlockPos.containing(
					at.x + r.nextGaussian() * power * 0.45,
					at.y - 0.5 + r.nextGaussian() * power * 0.25,
					at.z + r.nextGaussian() * power * 0.45);
			BlockState state = level.getBlockState(p);
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, p);
			if (hardness < 0 || hardness > 20) {
				continue;
			}
			FallingBlockEntity block = FallingBlockEntity.fall(level, p, state);
			Vec3 out = new Vec3(p.getX() + 0.5 - at.x, 0, p.getZ() + 0.5 - at.z);
			out = out.lengthSqr() > 1e-4 ? out.normalize() : new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5).normalize();
			double push = 0.25 + r.nextDouble() * 0.55;
			block.setDeltaMovement(out.x * push, 0.55 + r.nextDouble() * 0.9, out.z * push);
			block.disableDrop();
			block.setHurtsEntities(1.5f, 20);
			spawned++;
		}

		explode(level, source, at, power, type.fire, Level.ExplosionInteraction.TNT);
		scorch(level, at, power);
		Fx.send(level, FxPayload.GROUND_IMPACT, at, power, new Vec3(0, 1, 0));
		shockWave(level, source, at, power * 3.5, power * 1.1, 1.4);
	}

	/** Burnt, churned-up ground around the crater. Only the surface layer, and only soil-like blocks. */
	private static void scorch(ServerLevel level, Vec3 at, float power) {
		RandomSource r = level.getRandom();
		double radius = power * 1.15;
		int tries = (int) (power * power * 2.2f);
		for (int i = 0; i < tries; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(r.nextDouble()) * radius;
			int x = (int) Math.floor(at.x + Math.cos(a) * d);
			int z = (int) Math.floor(at.z + Math.sin(a) * d);
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			if (Math.abs(top - at.y) > power) {
				continue;
			}
			BlockPos pos = new BlockPos(x, top, z);
			BlockState state = level.getBlockState(pos);
			// Closer to the centre the ground is burnt black-brown; further out only some of it is churned.
			boolean inner = d < radius * 0.6;
			if (!inner && r.nextFloat() < 0.45f) {
				continue;
			}
			if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
					|| state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MUD)) {
				level.setBlock(pos, (inner && r.nextFloat() < 0.35f ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT).defaultBlockState(), 3);
			} else if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
				if (inner && r.nextFloat() < 0.3f) {
					level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 3);
				}
			} else if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) {
				level.setBlock(pos, state.is(Blocks.SNOW) ? Blocks.AIR.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), 3);
			} else if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)) {
				level.removeBlock(pos, false);
			}
		}
	}

	/** Blown up in the air: an interceptor's warhead, or a shot-down threat. */
	public static void airBurst(ServerLevel level, Entity source, Vec3 at, MissileType type) {
		// A shot-down attack missile still detonates its own warhead, so it is much bigger than an interceptor.
		float size = type.threat ? Math.max(2.5f, type.power * 0.6f) : type.power;
		// Fragmentation hurts what's close, but an air burst does not dig into the ground.
		explode(level, source, at, Math.min(size, 3.0f), false, Level.ExplosionInteraction.NONE);
		Fx.send(level, type.threat ? FxPayload.AIR_BURST_THREAT : FxPayload.AIR_BURST_INTERCEPTOR, at, size, Vec3.ZERO);
		shockWave(level, source, at, size * 2.5, 0, 0.6);
	}

	/** A vehicle blows up: fuel, ammunition, flying wreckage. */
	public static void vehicleDestroyed(ServerLevel level, Entity source, Vec3 at, float power) {
		explode(level, source, at, power, true, Level.ExplosionInteraction.TNT);
		Fx.send(level, FxPayload.GROUND_IMPACT, at, power, new Vec3(0, 1, 0));
		shockWave(level, source, at, power * 3.0, power * 0.8, 1.2);
	}

	/** Launch blast: a smoke cloud rolling out around the launcher. */
	public static void launchBlast(ServerLevel level, Vec3 at, MissileType type) {
		float size = switch (type.kind) {
			case BALLISTIC -> 3.0f;
			case ROCKET, CRUISE -> 2.0f;
			default -> type == MissileType.STINGER ? 0.5f : 1.3f;
		};
		Fx.send(level, FxPayload.LAUNCH, at, size, Vec3.ZERO);
	}

	/** One glowing tracer round from {@code from} to {@code to} (Gepard). */
	public static void tracer(ServerLevel level, Vec3 from, Vec3 to) {
		Fx.send(level, FxPayload.TRACER, from, 0, to);
	}

	/**
	 * A real explosion (damage, blocks, knockback) without Minecraft's own pixel puffs and bang: our particles replace
	 * the puffs, and each client plays the explosion sound itself, late by the distance it has to travel.
	 */
	private static void explode(ServerLevel level, Entity source, Vec3 at, float power, boolean fire,
			Level.ExplosionInteraction interaction) {
		level.explode(source, null, null, at.x, at.y, at.z, power, fire, interaction,
				ModParticles.GLOW, ModParticles.GLOW, WeightedList.of(), Holder.direct(ModSounds.SILENT));
	}

	private static void shockWave(ServerLevel level, Entity source, Vec3 at, double outer, double inner, double strength) {
		AABB box = new AABB(at.x - outer, at.y - outer, at.z - outer, at.x + outer, at.y + outer, at.z + outer);
		for (Entity e : level.getEntities(source, box)) {
			if (e instanceof FallingBlockEntity || e instanceof MissileEntity || e.isSpectator()) {
				continue;
			}
			Vec3 d = e.position().subtract(at);
			double dist = d.length();
			if (dist < inner || dist > outer || dist < 1e-3) {
				continue;
			}
			double f = strength * (1 - dist / outer);
			Vec3 push = d.scale(1 / dist).scale(f).add(0, f * 0.35, 0);
			e.push(push.x, push.y, push.z);
			if (e instanceof ServerPlayer player) {
				player.connection.send(new ClientboundSetEntityMotionPacket(player));
			}
		}
	}
}
