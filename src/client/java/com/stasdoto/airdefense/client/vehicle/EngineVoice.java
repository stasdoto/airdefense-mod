package com.stasdoto.airdefense.client.vehicle;

import org.jetbrains.annotations.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.EngineSim;

/**
 * 2.0: an engine you hear - its recorded-at-a-steady-speed loops (synthesised by tools/make_sounds_vehicles.py: idle,
 * mid and high revs, on load and off load) played together and crossfaded by the revs, each pitched by how far the
 * revs are from the ones it was made at, the on-load layers rising with the throttle. The crossfade between two bands
 * stays inside the range where both can be pitched without sounding wrong (and inside the game's 0.5..2 pitch).
 */
public final class EngineVoice {
	/** For one band: the sound, the revs it was made at, and the load (1 on, -1 off, 0 either). */
	private record Band(SoundEvent event, float rpm, int load) {
	}

	private final EngineSim.Kind kind;
	private final Band[] bands;
	private final float[] levels;
	private final LoopLayer[] layers;
	private final float[] vol;
	private final float pitchScale;
	private float load;

	/**
	 * @param rotor for helicopters, the rotor's own loop (by type); the turboshafts are the second layer
	 * @param pitchScale smaller / bigger engines of the same kind run a little higher / lower
	 */
	public EngineVoice(EngineSim.Kind kind, float pitchScale, @Nullable SoundEvent rotor, SoundSource source) {
		this.kind = kind;
		this.pitchScale = pitchScale;
		this.bands = bandsOf(kind, rotor);
		float[] lv = new float[bands.length];
		int k = 0;
		for (Band b : bands) {
			boolean seen = false;
			for (int i = 0; i < k; i++) {
				seen |= lv[i] == b.rpm;
			}
			if (!seen) {
				lv[k++] = b.rpm;
			}
		}
		this.levels = java.util.Arrays.copyOf(lv, k);
		java.util.Arrays.sort(this.levels);
		this.layers = new LoopLayer[bands.length];
		for (int i = 0; i < bands.length; i++) {
			layers[i] = new LoopLayer(bands[i].event, source);
		}
		this.vol = new float[bands.length];
	}

	private static Band[] bandsOf(EngineSim.Kind kind, @Nullable SoundEvent rotor) {
		return switch (kind) {
			case PETROL -> new Band[]{new Band(ModSounds.VEH_PETROL_IDLE, 850, 0), new Band(ModSounds.VEH_PETROL_MID_ON, 2600, 1),
					new Band(ModSounds.VEH_PETROL_MID_OFF, 2600, -1), new Band(ModSounds.VEH_PETROL_HIGH_ON, 5000, 1),
					new Band(ModSounds.VEH_PETROL_HIGH_OFF, 5000, -1)};
			case DIESEL -> new Band[]{new Band(ModSounds.VEH_DIESEL_IDLE, 650, 0), new Band(ModSounds.VEH_DIESEL_MID_ON, 1450, 1),
					new Band(ModSounds.VEH_DIESEL_MID_OFF, 1450, -1), new Band(ModSounds.VEH_DIESEL_HIGH_ON, 2200, 1),
					new Band(ModSounds.VEH_DIESEL_HIGH_OFF, 2200, -1)};
			case V12 -> new Band[]{new Band(ModSounds.VEH_V12_IDLE, 620, 0), new Band(ModSounds.VEH_V12_MID_ON, 1300, 1),
					new Band(ModSounds.VEH_V12_MID_OFF, 1300, -1), new Band(ModSounds.VEH_V12_HIGH_ON, 2000, 1),
					new Band(ModSounds.VEH_V12_HIGH_OFF, 2000, -1)};
			case TURBINE -> new Band[]{new Band(ModSounds.VEH_TURBINE_IDLE, 60, 0), new Band(ModSounds.VEH_TURBINE_MID, 80, 0),
					new Band(ModSounds.VEH_TURBINE_HIGH, 100, 0)};
			case ELECTRIC -> new Band[]{new Band(ModSounds.VEH_ELECTRIC_LOW, 33.3f, 0), new Band(ModSounds.VEH_ELECTRIC_HIGH, 100, 0)};
			case JET -> new Band[]{new Band(ModSounds.VEH_JET_IDLE, 60, 0), new Band(ModSounds.VEH_JET_MID, 80, 0), new Band(ModSounds.VEH_JET_HIGH, 100, 0)};
			case ROTOR -> new Band[]{new Band(rotor != null ? rotor : ModSounds.VEH_ROTOR_MAIN, 100, 0), new Band(ModSounds.VEH_HELI_TURBINE, 100, 0)};
		};
	}

	/**
	 * Every tick: where, the revs, the throttle (0..1) and the overall loudness (0..1: the engine starting, stopping,
	 * how loud this vehicle is).
	 */
	public void update(double x, double y, double z, float rpm, float throttle, float master) {
		load += (Math.min(1, throttle * 1.3f) - load) * 0.3f;
		float on = (float) Math.sqrt(load);
		float off = (float) Math.sqrt(1 - load);
		if (kind == EngineSim.Kind.ROTOR) {
			// The rotor turns up slowly with the engines; the turboshafts whine up first.
			float s = Math.max(0, rpm / 100f);
			setLayer(0, x, y, z, master * (float) Math.pow(s, 1.3) * (0.9f + 0.15f * throttle), 0.4f + 0.6f * s);
			setLayer(1, x, y, z, master * 0.55f * Math.min(1, s * 4), 0.55f + 0.5f * (float) Math.sqrt(s));
			return;
		}
		// Quieter below the lowest band (spooling up, running down, an electric motor barely turning).
		float low = levels[0];
		float under = low <= 0 ? 1 : Math.min(1, rpm / low);
		if (kind == EngineSim.Kind.ELECTRIC) {
			under = Math.min(1, rpm / 12f);
		}
		master *= under * under;
		for (int i = 0; i < bands.length; i++) {
			Band b = bands[i];
			float w = levelWeight(b.rpm, rpm);
			if (b.load > 0) {
				w *= on;
			} else if (b.load < 0) {
				w *= off * 0.9f;
			}
			setLayer(i, x, y, z, master * w * (0.85f + 0.15f * throttle), rpm / b.rpm);
		}
	}

	private void setLayer(int i, double x, double y, double z, float target, float pitch) {
		// Smooth the loudness a little (the game updates sounds 20 times a second).
		vol[i] += (target - vol[i]) * 0.45f;
		layers[i].set(x, y, z, vol[i], pitch * pitchScale);
	}

	/** Equal-power crossfade weight of the band at {@code r} for revs {@code rpm}. */
	private float levelWeight(float r, float rpm) {
		int i = 0;
		while (i < levels.length && levels[i] != r) {
			i++;
		}
		float[] lv = levels;
		if (rpm <= lv[0]) {
			return i == 0 ? 1 : 0;
		}
		if (rpm >= lv[lv.length - 1]) {
			return i == lv.length - 1 ? 1 : 0;
		}
		int k = 0;
		while (k < lv.length - 2 && rpm > lv[k + 1]) {
			k++;
		}
		float a = lv[k];
		float b = lv[k + 1];
		// The window where both can be pitched: the upper band no lower than 0.55x, the lower no higher than 1.8x.
		float lo = Math.max(a, b * 0.55f);
		float hi = Math.min(b, a * 1.8f);
		if (lo >= hi) {
			lo = hi = (float) Math.sqrt(a * b);
		}
		float u = rpm <= lo ? 0 : rpm >= hi ? 1 : (float) (Math.log(rpm / lo) / Math.log(hi / lo));
		if (i == k) {
			return (float) Math.cos(u * Math.PI / 2);
		}
		if (i == k + 1) {
			return (float) Math.sin(u * Math.PI / 2);
		}
		return 0;
	}

	public void stop() {
		for (LoopLayer l : layers) {
			l.stop();
		}
	}

	public int playing() {
		int n = 0;
		for (LoopLayer l : layers) {
			n += l.playing() ? 1 : 0;
		}
		return n;
	}
}
