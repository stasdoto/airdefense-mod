package com.stasdoto.airdefense.client.vehicle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 2.0: one looping layer of a vehicle's sound (an engine's rev band, the tracks, the tyres...). Its owner sets where
 * it is, how loud and at what pitch every tick; it starts its sound when it gets loud enough, stops it after a while of
 * silence, and stops by itself when the owner forgets it (vehicle gone, out of range).
 */
public final class LoopLayer {
	/** Advanced once a client tick (VehicleSounds.tick). */
	static int clock;

	private final SoundEvent event;
	private final SoundSource source;
	private Inst inst;
	float volume;
	float pitch = 1;
	double x;
	double y;
	double z;
	int stamp = Integer.MIN_VALUE / 2;

	public LoopLayer(SoundEvent event, SoundSource source) {
		this.event = event;
		this.source = source;
	}

	/** Every tick, by the owner: position, volume (0..1) and pitch (0.5..2). */
	public void set(double x, double y, double z, float volume, float pitch) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.volume = volume < 0 ? 0 : Math.min(1, volume);
		this.pitch = pitch < 0.5f ? 0.5f : Math.min(2f, pitch);
		stamp = clock;
		if (this.volume > 0.012f && (inst == null || inst.isStopped())) {
			inst = new Inst(this);
			Minecraft.getInstance().getSoundManager().play(inst);
		}
	}

	/** Lets the sound go (it stops on its next tick). */
	public void stop() {
		stamp = Integer.MIN_VALUE / 2;
		volume = 0;
	}

	public boolean playing() {
		return inst != null && !inst.isStopped();
	}

	static final class Inst extends AbstractTickableSoundInstance {
		private final LoopLayer owner;
		private int quiet;

		Inst(LoopLayer owner) {
			super(owner.event, owner.source, SoundInstance.createUnseededRandom());
			this.owner = owner;
			this.looping = true;
			this.delay = 0;
			this.volume = owner.volume;
			this.pitch = owner.pitch;
			this.x = owner.x;
			this.y = owner.y;
			this.z = owner.z;
		}

		@Override
		public void tick() {
			if (clock - owner.stamp > 3) {
				stop();
				return;
			}
			volume = owner.volume;
			pitch = owner.pitch;
			x = owner.x;
			y = owner.y;
			z = owner.z;
			if (owner.volume < 0.004f) {
				if (++quiet > 40) {
					stop();
				}
			} else {
				quiet = 0;
			}
		}
	}
}
