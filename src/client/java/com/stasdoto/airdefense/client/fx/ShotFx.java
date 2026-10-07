package com.stasdoto.airdefense.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.weapon.GunType;
import com.stasdoto.airdefense.weapon.ShotPayload;

/**
 * What a small-arms shot looks like: the muzzle flash (lights up the night for a moment), a puff of smoke, the spent
 * case flying out, the round's streak (every n-th a bright tracer), and the impact when the round gets there - dust
 * and chips from a wall, sparks off metal, a red puff from a body.
 */
public final class ShotFx {
	/** Debug counter read by the automated test. */
	public static int IMPACTS;

	private ShotFx() {
	}

	/**
	 * @param right unit vector to the shooter's right (where the case flies), or null for no case
	 */
	public static void shot(Minecraft mc, GunType gun, Vec3 muzzle, Vec3 to, int hit, int round, Vec3 right) {
		shot(mc, gun, muzzle, to, hit, round, right, false);
	}

	/** @param own the shooter's own first-person view: a smaller flash and no smoke ball right in front of the eyes */
	public static void shot(Minecraft mc, GunType gun, Vec3 muzzle, Vec3 to, int hit, int round, Vec3 right, boolean own) {
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		RandomSource r = level.getRandom();
		var pe = mc.particleEngine;
		Vec3 d = to.subtract(muzzle);
		double dist = d.length();
		Vec3 dir = dist > 1e-4 ? d.scale(1 / dist) : Vec3.ZERO;
		float flash = switch (gun.report) {
			case PISTOL -> 0.16f;
			case SNIPER, SHOTGUN -> 0.34f;
			case HEAVY -> 0.5f;
			case SUPPRESSED -> 0.0f;
			case CARBINE -> 0.28f;
			default -> 0.24f;
		} * (own ? 0.6f : 1f);
		boolean quiet = gun.report == GunType.Report.SUPPRESSED;
		if (!quiet) {
			pe.add(FxClient.flash(level, muzzle.x, muzzle.y, muzzle.z, flash).life(1, 1));
			// A short light that brightens the surroundings at night (small and faint, or it hangs there as a disc).
			pe.add(FxClient.glow(level, muzzle.x, muzzle.y, muzzle.z, flash * 1.6f, 1).alpha(0.22f, 1, 0.2f));
		}
		if (!own && !quiet) {
			Vec3 puff = muzzle.add(dir.scale(0.4));
			pe.add(FxClient.smokeWhite(level, puff.x, puff.y, puff.z, dir.x * 0.03, 0.01, dir.z * 0.03, 0.05f).life(14, 24));
		}
		if (right != null) {
			Vec3 port = muzzle.subtract(dir.scale(gun.longGun() ? 0.55 : 0.2));
			Vec3 v = right.scale(0.1 + r.nextDouble() * 0.05).add(0, 0.1 + r.nextDouble() * 0.06, 0).subtract(dir.scale(0.02));
			pe.add(FxClient.casing(level, port.x, port.y, port.z, v.x, v.y, v.z));
		}
		double speed = gun.tracerSpeed();
		if (dist > 2) {
			boolean bright = gun.tracerEvery > 0 && round % gun.tracerEvery == 0;
			Vec3 v = dir.scale(speed);
			Vec3 start = muzzle.add(dir.scale(0.5));
			FxParticle t = bright ? FxClient.tracer(level, start.x, start.y, start.z, v.x, v.y, v.z)
					: FxClient.streak(level, start.x, start.y, start.z, v.x, v.y, v.z);
			t.setLifetime(Math.max(1, (int) (dist / speed)));
			pe.add(t);
		}
		FxClient.after((int) (dist / speed), () -> impact(mc, to, dir, hit));
	}

	/** Where the round lands. */
	public static void impact(Minecraft mc, Vec3 at, Vec3 dir, int hit) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}
		IMPACTS++;
		RandomSource r = level.getRandom();
		var pe = mc.particleEngine;
		boolean near = mc.player.position().distanceToSqr(at) < 40 * 40;
		switch (hit) {
			case ShotPayload.HIT_BLOCK -> {
				BlockState state = level.getBlockState(BlockPos.containing(at.add(dir.scale(0.08))));
				if (state.isAir()) {
					return;
				}
				for (int i = 0; i < 7; i++) {
					Vec3 v = dir.scale(-0.12).add(r.nextGaussian() * 0.06, 0.04 + r.nextDouble() * 0.12, r.nextGaussian() * 0.06);
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, v.x, v.y, v.z);
				}
				pe.add(FxClient.dust(level, at.x, at.y, at.z, -dir.x * 0.03, 0.01, -dir.z * 0.03, 0.18f).life(25, 40));
				SoundType sound = state.getSoundType();
				boolean hard = sound == SoundType.STONE || sound == SoundType.METAL || sound == SoundType.DEEPSLATE
						|| sound == SoundType.DEEPSLATE_BRICKS || sound == SoundType.POLISHED_DEEPSLATE || sound == SoundType.NETHER_BRICKS
						|| sound == SoundType.COPPER || sound == SoundType.ANVIL || sound == SoundType.CHAIN;
				if (hard) {
					for (int i = 0; i < 3; i++) {
						Vec3 v = dir.scale(-0.2).add(r.nextGaussian() * 0.12, r.nextDouble() * 0.15, r.nextGaussian() * 0.12);
						pe.add(FxClient.spark(level, at.x, at.y, at.z, v.x, v.y, v.z).life(4, 9).size(0.08f, 0.03f));
					}
				}
				if (near) {
					level.playLocalSound(at.x, at.y, at.z, sound.getHitSound(), SoundSource.BLOCKS, 0.5f, 1.3f + r.nextFloat() * 0.3f, false);
					if (hard && r.nextInt(3) == 0) {
						level.playLocalSound(at.x, at.y, at.z, ModSounds.RICOCHET, SoundSource.BLOCKS, 0.7f, 0.9f + r.nextFloat() * 0.3f, false);
					}
				}
			}
			case ShotPayload.HIT_FLESH, ShotPayload.HIT_HEAD -> {
				for (int i = 0; i < (hit == ShotPayload.HIT_HEAD ? 9 : 5); i++) {
					Vec3 v = dir.scale(0.08).add(r.nextGaussian() * 0.05, r.nextDouble() * 0.06, r.nextGaussian() * 0.05);
					level.addParticle(new DustParticleOptions(0x8A0E0E, 1.1f), at.x, at.y, at.z, v.x, v.y, v.z);
				}
				if (near) {
					level.playLocalSound(at.x, at.y, at.z, ModSounds.BULLET_HIT, SoundSource.PLAYERS, 0.6f, 0.9f + r.nextFloat() * 0.2f, false);
				}
			}
			case ShotPayload.HIT_METAL -> {
				for (int i = 0; i < 5; i++) {
					Vec3 v = dir.scale(-0.25).add(r.nextGaussian() * 0.15, r.nextDouble() * 0.2, r.nextGaussian() * 0.15);
					pe.add(FxClient.spark(level, at.x, at.y, at.z, v.x, v.y, v.z).life(4, 10).size(0.09f, 0.03f));
				}
				pe.add(FxClient.flash(level, at.x, at.y, at.z, 0.25f).life(1, 1));
				if (near) {
					level.playLocalSound(at.x, at.y, at.z, ModSounds.RICOCHET, SoundSource.BLOCKS, 0.8f, 0.9f + r.nextFloat() * 0.3f, false);
				}
			}
			default -> {
			}
		}
	}
}
