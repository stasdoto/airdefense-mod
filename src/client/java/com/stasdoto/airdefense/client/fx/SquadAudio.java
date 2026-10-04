package com.stasdoto.airdefense.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Sound the way it carries outdoors. What you hear depends on how far you are: close up a sharp crack and a punch,
 * further out a boom with a reflection, far away only a low roll that echoes off the hills (the layers blend into
 * each other at the borders). And it takes time to arrive - 17 blocks per tick, about the real 343 m/s - so a far
 * explosion is seen before it is heard. A round passing close by cracks overhead.
 */
public final class SquadAudio {
	/** Speed of sound in blocks per tick. */
	public static final double SPEED = 17.0;
	/** Debug counters read by the automated test: layers played (near, mid, far) and cracks heard. */
	public static final int[] PLAYED = new int[3];
	public static int CRACKS;

	public enum Kind {
		EXPLOSION(70, 220, 1400),
		AIRBURST(80, -1, 1400),
		GUN(60, -1, 700),
		LAUNCH_HEAVY(90, -1, 1000),
		LAUNCH_LIGHT(60, -1, 600),
		LAUNCH_MLRS(70, -1, 800);

		/** Up to here the near layer; {@code mid} (if any) up to its border; then far; silent beyond {@code max}. */
		final double near;
		final double mid;
		final double max;

		Kind(double near, double mid, double max) {
			this.near = near;
			this.mid = mid;
			this.max = max;
		}
	}

	private SquadAudio() {
	}

	private static SoundEvent[] layers(Kind kind) {
		return switch (kind) {
			case EXPLOSION -> new SoundEvent[]{ModSounds.EXPLOSION_NEAR, ModSounds.EXPLOSION_MID, ModSounds.EXPLOSION_DISTANT};
			case AIRBURST -> new SoundEvent[]{ModSounds.AIRBURST_NEAR, null, ModSounds.AIRBURST_FAR};
			case GUN -> new SoundEvent[]{ModSounds.GUN_NEAR, null, ModSounds.GUN_FAR};
			case LAUNCH_HEAVY -> new SoundEvent[]{ModSounds.LAUNCH_HEAVY_NEAR, null, ModSounds.LAUNCH_HEAVY_FAR};
			case LAUNCH_LIGHT -> new SoundEvent[]{ModSounds.LAUNCH_LIGHT_NEAR, null, ModSounds.LAUNCH_LIGHT_FAR};
			case LAUNCH_MLRS -> new SoundEvent[]{ModSounds.LAUNCH_MLRS_NEAR, null, ModSounds.LAUNCH_MLRS_FAR};
		};
	}

	private static Vec3 listener(Minecraft mc) {
		return mc.gameRenderer.mainCamera().position();
	}

	/** Plays the layers that fit the distance, late by the time the sound needs to get here. {@code size} ~ 1. */
	public static void play(Vec3 at, Kind kind, float size) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		double d = listener(mc).distanceTo(at);
		if (d > kind.max * Math.max(1, size)) {
			return;
		}
		SoundEvent[] l = layers(kind);
		int delay = (int) (d / SPEED);
		// Loudness falls off with distance (slowly: these are big noises); the layers cross-fade over 30 blocks.
		float loud = (float) Math.pow(Mth.clamp(1 - d / (kind.max * Math.max(1, size)), 0, 1), 0.7) * Math.min(1.6f, 0.6f + size * 0.5f);
		double nearEnd = kind.near;
		double farStart = kind.mid > 0 ? kind.mid : kind.near;
		float wNear = (float) Mth.clamp((nearEnd + 15 - d) / 30, 0, 1);
		float wFar = (float) Mth.clamp((d - (farStart - 15)) / 30, 0, 1);
		float wMid = kind.mid > 0 ? Math.max(0, 1 - wNear - wFar) : 0;
		RandomSource r = mc.level.getRandom();
		float pitch = 0.92f + r.nextFloat() * 0.16f;
		if (wNear > 0.01f) {
			emit(mc, l[0], at, loud * wNear, pitch, delay);
			PLAYED[0]++;
		}
		if (wMid > 0.01f && l[1] != null) {
			emit(mc, l[1], at, loud * wMid, pitch, delay);
			PLAYED[1]++;
		}
		if (wFar > 0.01f) {
			emit(mc, l[2], at, loud * wFar, pitch * 0.95f, delay);
			PLAYED[2]++;
		}
	}

	/**
	 * A round flying from {@code from} to {@code to}: if it passes within a few blocks of the listener (and it is not
	 * our own gun firing), its supersonic crack is heard at the closest point, when it gets there.
	 */
	public static void bulletPass(Vec3 from, Vec3 to, double speed) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Vec3 ear = listener(mc);
		Vec3 seg = to.subtract(from);
		double len = seg.length();
		if (len < 1e-3) {
			return;
		}
		Vec3 dir = seg.scale(1 / len);
		double along = Mth.clamp(ear.subtract(from).dot(dir), 0, len);
		Vec3 closest = from.add(dir.scale(along));
		double miss = closest.distanceTo(ear);
		if (miss > 7 || along < 8) {
			return;
		}
		int delay = (int) (along / speed + miss / SPEED);
		float vol = (float) Mth.clamp(1.2 - miss / 7, 0.2, 1.0);
		emit(mc, ModSounds.CRACK, closest, vol, 0.9f + mc.level.getRandom().nextFloat() * 0.2f, delay);
		CRACKS++;
	}

	private static void emit(Minecraft mc, SoundEvent event, Vec3 at, float volume, float pitch, int delay) {
		SoundInstance s = new SimpleSoundInstance(event.location(), SoundSource.BLOCKS, Math.min(volume, 2f), pitch, mc.level.getRandom(),
				false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
		if (delay > 0) {
			mc.getSoundManager().playDelayed(s, delay);
		} else {
			mc.getSoundManager().play(s);
		}
	}
}
