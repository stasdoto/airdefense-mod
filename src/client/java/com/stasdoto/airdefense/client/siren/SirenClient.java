package com.stasdoto.airdefense.client.siren;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.siren.SirenBlock;
import com.stasdoto.airdefense.siren.SirenNet;
import com.stasdoto.airdefense.siren.SirenSounds;

/**
 * What the sirens sound like where you stand. Every siren in sight reports its signal each tick; the nearest few
 * that sound are played: a spin-up from rest, then the rising and falling wail (a near layer and a far, echoing
 * one), the steady all clear, and the long coast-down when they stop. Each siren runs a touch faster or slower than
 * the next, so several together beat against each other as real ones do.
 */
public final class SirenClient {
	/** How many sirens are heard at once (the nearest). */
	private static final int VOICES = 6;
	private static final double HEARING = 420;
	private static final Map<Long, Seen> SEEN = new HashMap<>();
	private static final Map<Long, Voice> PLAYING = new HashMap<>();
	/** The last page from the server (for the tablet screen). */
	public static SirenNet.State state;
	/** Debug counters read by the automated test. */
	public static int startsPlayed;
	public static int voicesNow;

	private record Seen(SirenBlock.Signal signal, long tick) {
	}

	private SirenClient() {
	}

	public static void init() {
		SirenSounds.listener = (pos, signal) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) {
				SEEN.put(pos.asLong(), new Seen(signal, mc.level.getGameTime()));
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
			PLAYING.values().forEach(Voice::end);
			PLAYING.clear();
			SEEN.clear();
			return;
		}
		long now = mc.level.getGameTime();
		SEEN.values().removeIf(s -> now - s.tick > 10);
		if (now % 5 != 0) {
			return;
		}
		Vec3 ear = mc.gameRenderer.mainCamera().position();
		List<Long> sounding = new ArrayList<>();
		for (Map.Entry<Long, Seen> e : SEEN.entrySet()) {
			if (e.getValue().signal != SirenBlock.Signal.OFF && Vec3.atCenterOf(BlockPos.of(e.getKey())).distanceTo(ear) < HEARING) {
				sounding.add(e.getKey());
			}
		}
		sounding.sort((a, b) -> Double.compare(Vec3.atCenterOf(BlockPos.of(a)).distanceToSqr(ear), Vec3.atCenterOf(BlockPos.of(b)).distanceToSqr(ear)));
		if (sounding.size() > VOICES) {
			sounding = sounding.subList(0, VOICES);
		}
		for (Iterator<Map.Entry<Long, Voice>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Voice> e = it.next();
			Seen s = SEEN.get(e.getKey());
			boolean keep = sounding.contains(e.getKey()) && s != null && s.signal == e.getValue().signal;
			if (!keep) {
				// Switched off (or changed signal, or too far): the rotor coasts down - unless the siren just left hearing.
				e.getValue().end();
				if (s != null && s.signal == SirenBlock.Signal.OFF) {
					play(mc, ModSounds.SIREN_STOP, BlockPos.of(e.getKey()), 1.0f, e.getValue().pitch);
				}
				it.remove();
			}
		}
		for (long key : sounding) {
			if (!PLAYING.containsKey(key)) {
				SirenBlock.Signal signal = SEEN.get(key).signal;
				BlockPos pos = BlockPos.of(key);
				float pitch = 0.97f + (float) Math.floorMod(pos.hashCode(), 61) / 1000f;
				boolean start = signal == SirenBlock.Signal.ALERT;
				if (start) {
					play(mc, ModSounds.SIREN_START, pos, 1.0f, pitch);
					startsPlayed++;
				}
				PLAYING.put(key, new Voice(mc, pos, signal, pitch, start ? 50 : 0));
			}
		}
		voicesNow = PLAYING.size();
	}

	private static void play(Minecraft mc, SoundEvent event, BlockPos pos, float volume, float pitch) {
		mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.BLOCKS, volume, pitch, RandomSource.create(), pos.getX() + 0.5,
				pos.getY() + 1.6, pos.getZ() + 0.5));
	}

	/** One siren's sound: the near and the far loop of its signal, started after the spin-up. */
	private static final class Voice {
		final SirenBlock.Signal signal;
		final float pitch;
		final Loop near;
		final Loop far;

		Voice(Minecraft mc, BlockPos pos, SirenBlock.Signal signal, float pitch, int delay) {
			this.signal = signal;
			this.pitch = pitch;
			boolean alert = signal == SirenBlock.Signal.ALERT;
			near = new Loop(alert ? ModSounds.SIREN_WAIL : ModSounds.SIREN_CLEAR, pos, 1.0f, pitch, delay);
			far = new Loop(alert ? ModSounds.SIREN_WAIL_FAR : ModSounds.SIREN_CLEAR_FAR, pos, 1.0f, pitch, delay);
			mc.getSoundManager().play(near);
			mc.getSoundManager().play(far);
		}

		void end() {
			near.finish();
			far.finish();
		}
	}

	private static final class Loop extends AbstractTickableSoundInstance {
		private boolean done;
		private int fade = -1;

		Loop(SoundEvent event, BlockPos pos, float volume, float pitch, int delay) {
			super(event, SoundSource.BLOCKS, RandomSource.create());
			this.looping = true;
			this.delay = delay;
			this.volume = volume;
			this.pitch = pitch;
			this.x = pos.getX() + 0.5;
			this.y = pos.getY() + 1.6;
			this.z = pos.getZ() + 0.5;
		}

		void finish() {
			if (fade < 0) {
				fade = 6;
			}
		}

		@Override
		public void tick() {
			if (fade >= 0) {
				volume *= 0.6f;
				if (--fade <= 0 && !done) {
					done = true;
					stop();
				}
			}
		}
	}
}
