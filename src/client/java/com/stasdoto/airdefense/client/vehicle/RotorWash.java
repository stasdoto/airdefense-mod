package com.stasdoto.airdefense.client.vehicle;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * 1.26: the downwash of a helicopter's rotor near the ground - dust (or spray over water, snow over snow) blown out in
 * a ring under it, thicker the lower it hangs.
 */
final class RotorWash {
	private RotorWash() {
	}

	static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		RandomSource r = mc.level.getRandom();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof VehicleEntity v) || v.getVehicleType().air != VehicleType.HELI || !v.isAlive() || v.getPassengers().isEmpty()
					|| v.distanceToSqr(mc.player) > 90 * 90) {
				continue;
			}
			int gx = v.getBlockX();
			int gz = v.getBlockZ();
			int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz);
			double h = v.getY() - top;
			if (h > 16 || h < -1) {
				continue;
			}
			float strength = (float) Mth.clamp(1 - h / 16, 0, 1);
			int n = (int) (strength * 7) + 1;
			for (int i = 0; i < n; i++) {
				double a = r.nextDouble() * Mth.TWO_PI;
				double d = 2.5 + r.nextDouble() * 7;
				double x = v.getX() + Math.cos(a) * d;
				double z = v.getZ() + Math.sin(a) * d;
				int bx = Mth.floor(x);
				int bz = Mth.floor(z);
				int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
				BlockPos under = new BlockPos(bx, y - 1, bz);
				BlockState bs = mc.level.getBlockState(under);
				double out = 0.25 + 0.35 * strength;
				double vx = Math.cos(a) * out;
				double vz = Math.sin(a) * out;
				if (mc.level.getFluidState(under).is(Fluids.WATER)) {
					mc.level.addParticle(ParticleTypes.SPLASH, x, y + 0.1, z, vx, 0.2, vz);
					mc.level.addParticle(ParticleTypes.CLOUD, x, y + 0.3, z, vx * 0.6, 0.02, vz * 0.6);
				} else if (!bs.isAir()) {
					mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, bs), x, y + 0.1, z, vx, 0.12, vz);
					if (r.nextInt(3) == 0) {
						mc.level.addParticle(ParticleTypes.POOF, x, y + 0.3, z, vx * 0.5, 0.03, vz * 0.5);
					}
				}
			}
		}
	}
}
