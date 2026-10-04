package com.stasdoto.airdefense.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;

/** Short camera shake when the blast wave of a nearby explosion arrives. */
public final class CameraShake {
	private static final RandomSource RANDOM = RandomSource.create();
	private static float intensity;
	private static float appliedPitch;
	private static float appliedYaw;

	private CameraShake() {
	}

	public static void add(float amount) {
		intensity = Math.min(3f, intensity + amount);
	}

	static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			intensity = 0;
			appliedPitch = appliedYaw = 0;
			return;
		}
		// Undo last tick's offset so the shake never drifts the player's aim.
		player.setXRot(player.getXRot() - appliedPitch);
		player.setYRot(player.getYRot() - appliedYaw);
		appliedPitch = appliedYaw = 0;
		if (intensity < 0.02f) {
			intensity = 0;
			return;
		}
		appliedPitch = (RANDOM.nextFloat() - 0.5f) * intensity * 2f;
		appliedYaw = (RANDOM.nextFloat() - 0.5f) * intensity * 2f;
		player.setXRot(player.getXRot() + appliedPitch);
		player.setYRot(player.getYRot() + appliedYaw);
		intensity *= 0.84f;
	}
}
