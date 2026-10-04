package com.stasdoto.airdefense.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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

		explode(level, source, at, power, type.fire, Level.ExplosionInteraction.TNT,
				power >= 6 ? ModSounds.EXPLOSION_HUGE : ModSounds.EXPLOSION_BIG);
		Fx.send(level, FxPayload.GROUND_IMPACT, at, power, new Vec3(0, 1, 0));
		shockWave(level, source, at, power * 3.5, power * 1.1, 1.4);
		level.playSound(null, at.x, at.y, at.z, ModSounds.EXPLOSION_FAR, SoundSource.BLOCKS, 6.0f, 0.85f + r.nextFloat() * 0.15f);
	}

	/** Blown up in the air: an interceptor's warhead, or a shot-down threat. */
	public static void airBurst(ServerLevel level, Entity source, Vec3 at, MissileType type) {
		RandomSource r = level.getRandom();
		// A shot-down attack missile still detonates its own warhead, so it is much bigger than an interceptor.
		float size = type.threat ? Math.max(2.5f, type.power * 0.6f) : type.power;
		// Fragmentation hurts what's close, but an air burst does not dig into the ground.
		explode(level, source, at, Math.min(size, 3.0f), false, Level.ExplosionInteraction.NONE, ModSounds.EXPLOSION_AIR);
		Fx.send(level, type.threat ? FxPayload.AIR_BURST_THREAT : FxPayload.AIR_BURST_INTERCEPTOR, at, size, Vec3.ZERO);
		shockWave(level, source, at, size * 2.5, 0, 0.6);
		if (type.threat) {
			level.playSound(null, at.x, at.y, at.z, ModSounds.EXPLOSION_FAR, SoundSource.BLOCKS, 5.0f, 1.0f + r.nextFloat() * 0.1f);
		}
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

	/** A real explosion (damage, blocks, knockback) without Minecraft's own pixel puffs: our flash replaces them. */
	private static void explode(ServerLevel level, Entity source, Vec3 at, float power, boolean fire,
			Level.ExplosionInteraction interaction, SoundEvent sound) {
		level.explode(source, null, null, at.x, at.y, at.z, power, fire, interaction,
				ModParticles.FLASH, ModParticles.FLASH, WeightedList.of(), Holder.direct(sound));
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
