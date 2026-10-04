"""
Procedural sounds for the mod (stage 6, "Squad-style"): every sound is synthesised here from noise, oscillators and
filters - nothing is recorded or copied. Distance layers (near / mid / far) for explosions, launches and guns, room
reflections and long rolling echoes for far sounds, the supersonic crack of a passing round, engine loops.

Run: python3 tools/make_sounds.py   (needs numpy, scipy and ffmpeg with libvorbis). Writes mono .ogg files into
src/main/resources/assets/airdefense/sounds/ (mono, so Minecraft places them in 3D).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'sounds')
rng = np.random.default_rng(20261004)


# ---------------------------------------------------------------------------------------------------------------
# Building blocks

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def white(seconds):
    return rng.standard_normal(int(seconds * SR))


def brown(seconds):
    x = np.cumsum(rng.standard_normal(int(seconds * SR)))
    x = signal.sosfilt(signal.butter(1, 20, 'hp', fs=SR, output='sos'), x)
    return x / (np.max(np.abs(x)) + 1e-9)


def pink(seconds):
    n = int(seconds * SR)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f))) / np.maximum(np.sqrt(f), 1)
    x = np.fft.irfft(spec, n)
    return x / (np.max(np.abs(x)) + 1e-9)


def lp(x, fc, order=4):
    return signal.sosfilt(signal.butter(order, fc, 'lp', fs=SR, output='sos'), x)


def hp(x, fc, order=4):
    return signal.sosfilt(signal.butter(order, fc, 'hp', fs=SR, output='sos'), x)


def bp(x, lo, hi, order=3):
    return signal.sosfilt(signal.butter(order, [lo, hi], 'bp', fs=SR, output='sos'), x)


def decay(n_or_x, tau, attack=0.0):
    n = n_or_x if isinstance(n_or_x, int) else len(n_or_x)
    t = np.arange(n) / SR
    e = np.exp(-t / tau)
    if attack > 0:
        e *= np.clip(t / attack, 0, 1)
    return e


def pad(x, seconds):
    n = int(seconds * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def place(dst, src, at_s, gain=1.0):
    i = int(at_s * SR)
    j = min(len(dst), i + len(src))
    if i < len(dst):
        dst[i:j] += src[:j - i] * gain
    return dst


def sweep(f0, f1, seconds, curve=3.0):
    t = t_axis(seconds)
    f = f1 + (f0 - f1) * np.exp(-t * curve)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def reverb(x, seconds, tail, damp=3000, mix=0.35, pre=0.02):
    """Convolution with a decaying, darkening noise burst: a big outdoor space."""
    n = int(tail * SR)
    ir = white(tail) * decay(n, tail / 5.5)
    ir = lp(ir, damp, 2)
    ir[: int(pre * SR)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = signal.fftconvolve(x, ir)[: int(seconds * SR)]
    dry = pad(x, seconds)
    wet = pad(wet, seconds)
    return dry * (1 - mix) + wet * mix * 3


def echoes(x, seconds, taps):
    """Discrete reflections from hills and forest edges: (delay s, gain, low-pass Hz)."""
    out = pad(x, seconds).copy()
    for d, g, f in taps:
        place(out, lp(x, f, 2), d, g)
    return out


def norm(x, peak=0.95):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def fade(x, fin=0.002, fout=0.05):
    n_in = int(fin * SR)
    n_out = int(fout * SR)
    x = x.copy()
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def save(name, x, peak=0.95, loop=False):
    x = norm(x, peak) if peak else x
    if not loop:
        x = fade(x)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + '.wav')
        wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                        os.path.join(OUT, name + '.ogg')], check=True)
    print(f'{name}.ogg  {len(x) / SR:.2f} s')


# ---------------------------------------------------------------------------------------------------------------
# Explosions: near = crack + chest punch + debris; mid = boom + one reflection; far = low roll that echoes for seconds

def blast_core(size=1.0, length=4.0):
    t = t_axis(length)
    out = np.zeros(len(t))
    crack = hp(white(0.03), 900) * decay(int(0.03 * SR), 0.004)
    place(out, crack, 0.0, 1.0)
    boom = sweep(95 * (1.3 - 0.3 * size), 32, 1.6, 4.0) * decay(int(1.6 * SR), 0.35 * size, 0.002)
    place(out, boom, 0.0, 1.1)
    body = lp(white(length), 900) * decay(int(length * SR), 0.18 * size, 0.003)
    out += body * 0.9
    rumble = lp(brown(length), 220) * decay(int(length * SR), 0.9 * size, 0.06)
    out += rumble * 1.3
    return out


def debris(length, density=60, start=0.15):
    out = np.zeros(int(length * SR))
    for _ in range(density):
        at = start + rng.exponential(0.5)
        if at > length - 0.1:
            continue
        c = hp(white(0.02), 1500) * decay(int(0.02 * SR), 0.003) * rng.uniform(0.05, 0.25)
        place(out, c, at)
    return out


def explosions():
    for v in range(3):
        size = 1.0 + 0.15 * v
        near = blast_core(size, 4.5) + debris(4.5, 80)
        near = reverb(near, 4.5, 2.2, damp=4500, mix=0.25)
        save(f'explosion_near_{v}', near)
        mid = lp(blast_core(size, 5.0), 2200)
        mid = echoes(mid, 5.0, [(0.32 + 0.05 * v, 0.45, 900), (0.9, 0.25, 600)])
        mid = reverb(mid, 5.0, 3.0, damp=2000, mix=0.35)
        save(f'explosion_mid_{v}', mid)
        core = lp(blast_core(size * 1.2, 7.0), 380)
        core = core * np.clip(t_axis(7.0) / 0.05, 0, 1)
        far = echoes(core, 7.0, [(0.55, 0.6, 300), (1.25 + 0.1 * v, 0.45, 250), (2.3, 0.3, 200), (3.4, 0.18, 160)])
        far = reverb(far, 7.0, 4.0, damp=600, mix=0.5)
        save(f'explosion_far_{v}', far)
    # Air bursts: sharper, no ground shock or debris.
    for v in range(2):
        a = np.zeros(int(3.0 * SR))
        place(a, hp(white(0.02), 1200) * decay(int(0.02 * SR), 0.003), 0, 1.0)
        place(a, sweep(160, 60, 0.6, 6) * decay(int(0.6 * SR), 0.12), 0, 0.9)
        a += lp(white(3.0), 1500) * decay(int(3.0 * SR), 0.1, 0.002) * 0.7
        a += lp(brown(3.0), 300) * decay(int(3.0 * SR), 0.5, 0.03) * 0.8
        save(f'airburst_near_{v}', reverb(a, 3.0, 2.0, damp=5000, mix=0.3))
        f = lp(a, 500) * np.clip(t_axis(3.0) / 0.04, 0, 1)
        f = echoes(f, 5.0, [(0.7, 0.5, 350), (1.6, 0.3, 250)])
        save(f'airburst_far_{v}', reverb(f, 5.0, 3.0, damp=700, mix=0.45))


# ---------------------------------------------------------------------------------------------------------------
# Gepard: twin 35 mm, 6 rounds per burst at ~18 rounds/s; near is a hard staccato, far a dull drumming with echo

def gun_shot():
    s = np.zeros(int(0.25 * SR))
    place(s, hp(white(0.004), 1500) * decay(int(0.004 * SR), 0.0008), 0, 1.0)
    place(s, lp(white(0.08), 2500) * decay(int(0.08 * SR), 0.012), 0, 0.9)
    place(s, np.sin(2 * np.pi * 110 * t_axis(0.12)) * decay(int(0.12 * SR), 0.03), 0.001, 0.8)
    place(s, bp(white(0.012), 2500, 5000) * decay(int(0.012 * SR), 0.003), 0.03, 0.15)  # breech clank
    return s


def guns():
    for v in range(2):
        near = np.zeros(int(1.8 * SR))
        for i in range(6):
            place(near, gun_shot(), i * 0.056 + rng.uniform(0, 0.004), rng.uniform(0.85, 1.0))
        save(f'gun_near_{v}', reverb(near, 1.8, 1.4, damp=5000, mix=0.3))
        far = lp(near, 700) * 1.0
        far = echoes(far, 3.5, [(0.45, 0.5, 400), (1.1, 0.3, 300)])
        save(f'gun_far_{v}', reverb(far, 3.5, 2.5, damp=600, mix=0.5))


def crack():
    """A supersonic round passing close: the N-wave snap, then the whizz of air dropping in pitch."""
    for v in range(3):
        n = int(0.45 * SR)
        x = np.zeros(n)
        k = int(0.0008 * SR)
        x[:k] = np.linspace(1, -1, k)
        x[k:k + 40] = -np.linspace(1, 0, 40)
        x = hp(x, 400)
        whizz = bp(white(0.45), 1800, 5000) * decay(n, 0.08, 0.004)
        sw = sweep(4200 - v * 300, 1600, 0.45, 9) * decay(n, 0.06)
        x = x * 1.0 + whizz * 0.35 + sw * 0.08
        save(f'crack_{v}', reverb(x, 0.6, 0.5, damp=6000, mix=0.15))


# ---------------------------------------------------------------------------------------------------------------
# Launches

def launches():
    # Heavy (Iskander): a deep roar that builds, rumbles, and fades upwards.
    t = t_axis(5.0)
    roar = lp(brown(5.0), 600) * 1.2 + bp(white(5.0), 200, 2400) * 0.5
    env = np.clip(t / 0.25, 0, 1) * np.exp(-np.maximum(t - 1.2, 0) / 1.4)
    near = roar * env
    near += hp(white(5.0), 3000) * env * 0.08 * (0.5 + 0.5 * np.sin(2 * np.pi * 23 * t) ** 2)  # crackle
    save('launch_heavy_near', reverb(near, 5.0, 2.5, damp=3000, mix=0.3))
    far = lp(near, 500)
    save('launch_heavy_far', reverb(echoes(far, 6.5, [(0.6, 0.5, 400), (1.4, 0.3, 300)]), 6.5, 3.0, damp=600, mix=0.45))
    # Light (SAM / drone booster): a sharp whoosh going away.
    t = t_axis(2.5)
    whoosh = bp(white(2.5), 500, 6000) * np.clip(t / 0.03, 0, 1) * np.exp(-t / 0.5)
    whoosh += lp(brown(2.5), 400) * np.exp(-t / 0.35) * 0.8
    place(whoosh, hp(white(0.02), 1000) * decay(int(0.02 * SR), 0.004), 0, 1.0)
    save('launch_light_near', reverb(whoosh, 2.5, 1.6, damp=5000, mix=0.25))
    save('launch_light_far', reverb(echoes(lp(whoosh, 700), 4.0, [(0.5, 0.45, 450)]), 4.0, 2.5, damp=700, mix=0.45))
    # MLRS: one rocket's ripping whoosh (a salvo plays it once per rocket).
    t = t_axis(2.2)
    rip = bp(white(2.2), 300, 5000) * np.clip(t / 0.015, 0, 1) * np.exp(-t / 0.4)
    rip *= 1 + 0.4 * np.sin(2 * np.pi * 37 * t)
    rip += lp(brown(2.2), 300) * np.exp(-t / 0.3)
    save('launch_mlrs_near', reverb(rip, 2.2, 1.5, damp=4500, mix=0.25))
    save('launch_mlrs_far', reverb(echoes(lp(rip, 600), 4.0, [(0.5, 0.45, 400), (1.2, 0.25, 300)]), 4.0, 2.5, damp=600, mix=0.45))


# ---------------------------------------------------------------------------------------------------------------
# Loops: engines, hydraulics, turret drive. Built from whole cycles so they loop without a click.

def periodic_noise(seconds, period_s):
    """Noise that repeats exactly every period (for seamless loops)."""
    n = int(seconds * SR)
    one = white(period_s)
    reps = int(np.ceil(n / len(one)))
    return np.tile(one, reps)[:n]


def engine(name, rpm_hz, cylinders_hz, rough, clatter=0.0, length=2.0):
    t = t_axis(length)
    f = cylinders_hz
    # Make the loop hold an integer number of firing cycles.
    cycles = max(1, int(round(length * f)))
    length = cycles / f
    t = t_axis(length)
    phase = (t * f) % 1.0
    pulses = np.exp(-phase / 0.12) * (1 + rough * np.sin(2 * np.pi * t * f / 2))
    body = lp(pulses, 900) * 1.0
    noise = lp(periodic_noise(length, 1 / f * 4), 1800) * pulses * 0.35
    hum_f = round(rpm_hz * length) / length  # whole cycles in the loop
    hum = np.sin(2 * np.pi * hum_f * t) * 0.25 + np.sin(2 * np.pi * hum_f * 2 * t) * 0.1
    x = body + noise + hum
    if clatter:
        track = np.zeros(len(t))
        links = max(1, int(round(length * 9)))
        for i in range(links):
            c = bp(white(0.03), 900, 3500) * decay(int(0.03 * SR), 0.006)
            place(track, c, i * length / links, rng.uniform(0.6, 1.0))
        x += track * clatter
    x = hp(x, 35)
    # Loop: the filters' start-up is cut away by rendering two loops and keeping the second.
    two = np.tile(x, 2)
    save(name, two[len(x):], peak=0.8, loop=True)


def loops():
    engine('engine_truck', rpm_hz=46, cylinders_hz=38, rough=0.25)
    engine('engine_tracked', rpm_hz=42, cylinders_hz=34, rough=0.35, clatter=0.6)
    # Hydraulics: pump whine and hiss.
    length = 2.0
    t = t_axis(length)
    whine = np.sin(2 * np.pi * 330 * t) * 0.4 + np.sin(2 * np.pi * 660 * t) * 0.15
    hiss = bp(periodic_noise(length, 0.5), 2000, 7000) * 0.25
    save('hydraulics', hp(whine + hiss, 80), peak=0.6, loop=True)
    # Turret drive: an electric motor and gears.
    whine = np.sin(2 * np.pi * 740 * t) * 0.3 + np.sin(2 * np.pi * 1110 * t) * 0.12
    gears = bp(periodic_noise(length, 0.25), 1500, 4000) * 0.18 * (1 + 0.5 * np.sin(2 * np.pi * 24 * t))
    save('turret', hp(whine + gears, 120), peak=0.5, loop=True)


def siren():
    """Air-raid siren: slow rise, hold, slow fall, with the rotor's harmonics."""
    length = 7.0
    t = t_axis(length)
    f = np.interp(t, [0, 2.2, 4.2, 7.0], [180, 620, 620, 160])
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph) + 0.12 * np.sin(5 * ph)
    x *= np.interp(t, [0, 0.6, 6.0, 7.0], [0, 1, 1, 0])
    x = reverb(x, length, 2.5, damp=3000, mix=0.35)
    save('siren', x, peak=0.8)


if __name__ == '__main__':
    explosions()
    guns()
    crack()
    launches()
    loops()
    siren()
