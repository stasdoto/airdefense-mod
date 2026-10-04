package com.stasdoto.airdefense.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;

/** Explosions: crater, flying debris, fireball, smoke column, shock wave and sound that carries far. */
public final class Effects {
	private Effects() {
	}

	/** Warhead hits the ground (or a building, or someone). */
	public static void groundImpact(ServerLevel level, Entity source, Vec3 at, MissileType type) {
		RandomSource r = level.getRandom();
		float power = type.power;
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));

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

		level.explode(source, at.x, at.y, at.z, power, type.fire, Level.ExplosionInteraction.TNT);

		double radius = power * 0.9;
		// Flash and fireball.
		send(level, ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFD27A), at, 3, radius * 0.3, 0);
		send(level, ParticleTypes.EXPLOSION_EMITTER, at, (int) Math.max(2, power / 2), radius * 0.4, 0);
		send(level, ParticleTypes.EXPLOSION, at, (int) (power * 6), radius * 0.6, 0.1);
		send(level, ParticleTypes.FLAME, at.add(0, 1, 0), (int) (power * 30), radius * 0.35, 0.35);
		send(level, ParticleTypes.LAVA, at, (int) (power * 8), radius * 0.3, 0.6);
		// Dirt/stone thrown up.
		if (!ground.isAir()) {
			send(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), at, (int) (power * 40), radius * 0.4, 0.6);
			send(level, new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), at, (int) (power * 12), radius * 0.5, 0.4);
		}
		// Smoke: a wide low cloud plus a tall column that hangs in the air for a long time.
		send(level, ParticleTypes.LARGE_SMOKE, at.add(0, 1, 0), (int) (power * 45), radius * 0.7, 0.15);
		send(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.add(0, 1, 0), (int) (power * 10), radius * 0.35, 0.05);
		send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.add(0, 2, 0), (int) (power * 14), radius * 0.6, 0.04);

		shockWave(level, source, at, power * 3.5, power * 1.1, 1.4);

		level.playSound(null, at.x, at.y, at.z, power >= 6 ? ModSounds.EXPLOSION_HUGE : ModSounds.EXPLOSION_BIG,
				SoundSource.BLOCKS, power >= 6 ? 4.0f : 3.0f, 0.9f + r.nextFloat() * 0.2f);
		level.playSound(null, at.x, at.y, at.z, ModSounds.EXPLOSION_FAR, SoundSource.BLOCKS, 6.0f, 0.85f + r.nextFloat() * 0.15f);
	}

	/** Blown up in the air: an interceptor's warhead, or a shot-down threat. */
	public static void airBurst(ServerLevel level, Entity source, Vec3 at, MissileType type) {
		RandomSource r = level.getRandom();
		// A shot-down attack missile still detonates its own warhead, so it is much bigger than an interceptor.
		float size = type.threat ? Math.max(2.5f, type.power * 0.6f) : type.power;

		send(level, ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE6A8), at, 2, 0.5, 0);
		send(level, ParticleTypes.EXPLOSION_EMITTER, at, type.threat ? 2 : 1, size * 0.2, 0);
		send(level, ParticleTypes.EXPLOSION, at, (int) (size * 6), size * 0.6, 0.1);
		send(level, ParticleTypes.FLAME, at, (int) (size * 18), size * 0.3, 0.3);
		// Burning fragments falling down.
		send(level, ParticleTypes.LAVA, at, (int) (size * 10), size * 0.4, 0.5);
		send(level, ParticleTypes.LARGE_SMOKE, at, (int) (size * 25), size * 0.6, 0.08);
		send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, (int) (size * 10), size * 0.5, 0.02);

		// Fragmentation hurts what's close, but an air burst does not dig into the ground.
		level.explode(source, at.x, at.y, at.z, Math.min(size, 3.0f), false, Level.ExplosionInteraction.NONE);
		shockWave(level, source, at, size * 2.5, 0, 0.6);

		level.playSound(null, at.x, at.y, at.z, ModSounds.EXPLOSION_AIR, SoundSource.BLOCKS, 3.5f, 0.95f + r.nextFloat() * 0.15f);
		if (type.threat) {
			level.playSound(null, at.x, at.y, at.z, ModSounds.EXPLOSION_FAR, SoundSource.BLOCKS, 5.0f, 1.0f);
		}
	}

	/** Launch blast: smoke and flame around the launcher. */
	public static void launchBlast(ServerLevel level, Vec3 at, MissileType type) {
		double s = type.kind == MissileType.Kind.BALLISTIC ? 1.6 : 1.0;
		send(level, ParticleTypes.FLAME, at, (int) (25 * s), 0.6 * s, 0.12);
		send(level, ParticleTypes.LARGE_SMOKE, at, (int) (40 * s), 1.4 * s, 0.08);
		send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, (int) (18 * s), 1.6 * s, 0.03);
		send(level, ParticleTypes.CLOUD, at, (int) (20 * s), 1.8 * s, 0.12);
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

	public static void send(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		level.sendParticles(particle, true, true, at.x, at.y, at.z, count, spread, spread, spread, speed);
	}
}
