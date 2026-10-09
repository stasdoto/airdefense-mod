package com.stasdoto.airdefense.client.siren;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.siren.SirenBlock;
import com.stasdoto.airdefense.siren.SirenNet;
import com.stasdoto.airdefense.siren.SirenSounds;

/**
 * What the sirens sound like where you stand (rebuilt in 1.26). The town's sirens are heard as one: a near voice that
 * comes from the nearest sounding siren (gliding over to the next one as you walk - never restarting) and a far,
 * echoing one whose loudness follows the distance and how many sirens there are. It spins up once when the alert
 * starts, holds its wail through lag and short gaps in the reports, swaps to the steady tone for the all clear and
 * coasts down when the sirens around stop. (Before, every siren had voices of its own that restarted whenever the
 * nearest few changed - the "buggy" sound.)
 */
public final class SirenClient {
	private static final double HEARING = 420;
	/** A siren unheard from for this long (ticks) is taken as gone (lag, a chunk unloading). */
	private static final int FORGET = 60;
	private static final Map<Long, Seen> SEEN = new HashMap<>();
	/** The last page from the server (for the tablet screen). */
	public static SirenNet.State state;
	/** Debug counters read by the automated test. */
	public static int startsPlayed;
	public static int voicesNow;

	private static SirenBlock.Signal playing = SirenBlock.Signal.OFF;
	@Nullable
	private static Loop near;
	@Nullable
	private static Loop far;
	private static Vec3 lastAt = Vec3.ZERO;
	/** Ticks with nothing sounding in hearing (the voice ends after a short grace). */
	private static int silent;
	/** Did a siren in hearing report "off" (they were switched off, rather than left behind)? */
	private static boolean switchedOff;

	private record Seen(SirenBlock.Signal signal, long tick) {
	}

	private SirenClient() {
	}

	/** 1.37: the player hears an alert (the passers-by hurry indoors). */
	public static boolean alertHeard() {
		return playing == SirenBlock.Signal.ALERT;
	}

	public static void init() {
		SirenSounds.listener = (pos, signal) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) {
				Seen old = SEEN.put(pos.asLong(), new Seen(signal, mc.level.getGameTime()));
				if (signal == SirenBlock.Signal.OFF && old != null && old.signal != SirenBlock.Signal.OFF) {
					switchedOff = true;
				}
			}
		};
		ClientPlayNetworking.registerGlobalReceiver(SirenNet.State.TYPE, (payload, context) -> state = payload);
		ClientTickEvents.END_CLIENT_TICK.register(SirenClient::tick);
	}

	public static void send(int action, int arg, long pos) {
		if (ClientPlayNetworking.canSend(SirenNet.Action.TYPE)) {
			ClientPlayNetworking.send(new SirenNet.Action(action, arg, pos));
		}
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			stopAll();
			SEEN.clear();
			return;
		}
		long now = mc.level.getGameTime();
		SEEN.values().removeIf(s -> now - s.tick > FORGET);
		Vec3 ear = mc.gameRenderer.mainCamera().position();
		// The nearest sounding siren, its signal, and how many are sounding in hearing.
		Vec3 nearest = null;
		double best = Double.MAX_VALUE;
		SirenBlock.Signal signal = SirenBlock.Signal.OFF;
		int count = 0;
		for (Map.Entry<Long, Seen> e : SEEN.entrySet()) {
			if (e.getValue().signal == SirenBlock.Signal.OFF) {
				continue;
			}
			Vec3 at = Vec3.atCenterOf(BlockPos.of(e.getKey())).add(0, 1.1, 0);
			double d = at.distanceTo(ear);
			if (d > HEARING) {
				continue;
			}
			count++;
			if (d < best) {
				best = d;
				nearest = at;
				signal = e.getValue().signal;
			}
		}
		if (nearest == null) {
			if (playing != SirenBlock.Signal.OFF && ++silent > 10) {
				// Everything around went quiet: coast down if they were switched off, else (walked away) just fade.
				if (switchedOff) {
					play(mc, ModSounds.SIREN_STOP, lastAt, 1.0f);
				}
				stopAll();
			}
			switchedOff = false;
			voicesNow = near != null ? 1 : 0;
			return;
		}
		silent = 0;
		switchedOff = false;
		if (signal != playing) {
			boolean fresh = playing == SirenBlock.Signal.OFF;
			stopAll();
			playing = signal;
			int delay = 0;
			if (signal == SirenBlock.Signal.ALERT && fresh && best < 260) {
				// The rotor spins up from rest, then the wail takes over.
				play(mc, ModSounds.SIREN_START, nearest, 1.0f);
				startsPlayed++;
				delay = 40;
			}
			boolean alert = signal == SirenBlock.Signal.ALERT;
			near = new Loop(alert ? ModSounds.SIREN_WAIL : ModSounds.SIREN_CLEAR, nearest, delay);
			far = new Loop(alert ? ModSounds.SIREN_WAIL_FAR : ModSounds.SIREN_CLEAR_FAR, nearest, delay);
			mc.getSoundManager().play(near);
			mc.getSoundManager().play(far);
		}
		lastAt = nearest;
		// Loudness by distance (our own curve: the game's would cut off far too soon or too late).
		float nearGain = (float) Math.pow(Mth.clamp(1 - best / 190.0, 0, 1), 1.6);
		float crowd = 1 + 0.12f * Math.min(4, count - 1);
		float farGain = (float) Mth.clamp((1 - best / HEARING) * 0.85, 0, 0.85) * crowd * (0.55f + 0.45f * (1 - nearGain));
		if (near != null) {
			near.aim(nearest, nearGain);
		}
		if (far != null) {
			far.aim(nearest, Math.min(1f, farGain));
		}
		voicesNow = near != null ? 1 : 0;
	}

	private static void stopAll() {
		if (near != null) {
			near.finish();
		}
		if (far != null) {
			far.finish();
		}
		near = null;
		far = null;
		playing = SirenBlock.Signal.OFF;
	}

	private static void play(Minecraft mc, SoundEvent event, Vec3 at, float volume) {
		mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.BLOCKS, volume, 1.0f, RandomSource.create(), at.x, at.y, at.z));
	}

	/** One looping layer: follows the nearest siren smoothly, its loudness set by us (no game attenuation). */
	private static final class Loop extends AbstractTickableSoundInstance {
		private float target;
		private Vec3 goal;
		private int fade = -1;

		Loop(SoundEvent event, Vec3 at, int delay) {
			super(event, SoundSource.BLOCKS, RandomSource.create());
			this.looping = true;
			this.delay = delay;
			this.volume = 0.001f;
			this.pitch = 1.0f;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
			this.goal = at;
		}

		void aim(Vec3 at, float gain) {
			goal = at;
			target = gain;
		}

		void finish() {
			if (fade < 0) {
				fade = 12;
			}
		}

		@Override
		public void tick() {
			if (fade >= 0) {
				volume *= 0.7f;
				if (--fade <= 0) {
					stop();
				}
				return;
			}
			// Glide over to a new nearest siren in about a second instead of jumping.
			x += (goal.x - x) * 0.08;
			y += (goal.y - y) * 0.08;
			z += (goal.z - z) * 0.08;
			volume += (Math.max(0.001f, target) - volume) * 0.1f;
		}
	}
}
