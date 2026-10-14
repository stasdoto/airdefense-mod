"""
2.0 "Vehicles that sound like vehicles": engines built the way an engine makes its noise, synthesised here (nothing
recorded or copied), in layers the game crossfades and pitch-shifts as the engine's revs change.

Piston engines (petrol 4-cylinder car, 6-cylinder turbo diesel truck/bus, V12 tank diesel) are rendered from the
physics of the noise:
  - each cylinder fires once every two turns of the crank (firing rate = rpm / 60 * cylinders / 2); every firing is
    an exhaust blow-down pulse (a sharp pressure rise and a decay that lasts a fixed crank angle), with its own small
    timing jitter and strength (cylinder to cylinder and cycle to cycle) - that is what makes an engine "lumpy"
    rather than a buzz;
  - the pulses go down the exhaust pipe, a tube closed at the engine and open at the tail (resonances at odd
    multiples of c / 4L, a comb filter), through the silencer (a low pass with a chamber ripple), and out of the
    tailpipe (radiation lifts the highs);
  - intake roar (noise opened by every intake stroke, by throttle), valve-train ticks, the block ringing when a
    diesel's combustion knocks it (the clatter), a turbo's whistle, fans and gear whine for a tank;
  - on load / off load layers: on the overrun a petrol engine burbles and pops, a diesel without fuel only pumps air.
Every loop holds a whole number of engine cycles and is filtered circularly (FFT), so it loops without a click.

Turbine (T-80, Abrams), electric traction (trolleybus, tram), helicopter rotors + turbine, jet: idle / mid / high
loops. Plus one-shots: gear-change clunk, turbo whoosh, tyre squeal (loop), brake squeal, air-brake hiss, horns (car,
truck air horn, bus), doors, the reverse beeper (loop), starting (starter cranking, the engine catching) and stopping
per engine, the tracks' clank (slow / fast loops), road noise (loops), a suspension thump.

Run: python3 tools/make_sounds_vehicles.py     (numpy, scipy, ffmpeg with libvorbis). Writes only its own veh_*.ogg
files, adds its entries to sounds.json and prints a check of every loop (level, seam, spectrum).
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

SR = 32000
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get('SOUNDS_OUT', os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'sounds'))
SOUNDS_JSON = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'sounds.json')
rng = np.random.default_rng(20261013)
CHECKS = []


# ---------------------------------------------------------------------------------------------------------------
# Spectral (circular) filters: a loop's end meets its start

def fr(n):
    return np.fft.rfftfreq(n, 1 / SR)


def apply(x, h):
    return np.fft.irfft(np.fft.rfft(x) * h, len(x))


def h_lp(f, fc, order=2):
    return 1 / np.sqrt(1 + (f / fc) ** (2 * order))


def h_hp(f, fc, order=2):
    return 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def h_peak(f, fc, q, gain):
    """A resonance: gain at fc, 1 far from it."""
    return 1 + (gain - 1) / (1 + (q * (f / fc - fc / np.maximum(f, 1e-3))) ** 2)


def h_res(f, fc, q):
    """A band-pass resonator (1 at fc)."""
    return 1 / np.sqrt(1 + (q * (f / fc - fc / np.maximum(f, 1e-3))) ** 2)


def h_comb(f, delay, r):
    """A tube: the wave runs down and back (delay) and is reflected (r, negative at an open end)."""
    return 1 / (1 - r * np.exp(-2j * np.pi * f * delay))


def band(x, lo=None, hi=None, order=2):
    f = fr(len(x))
    h = np.ones_like(f)
    if lo:
        h = h * h_hp(f, lo, order)
    if hi:
        h = h * h_lp(f, hi, order)
    return apply(x, h)


def noise(n):
    return rng.standard_normal(n)


def ctone(n, f, phase=0.0):
    """A sine with a whole number of cycles in n samples (f rounded to fit), so the loop meets itself."""
    cyc = max(1, round(f * n / SR))
    return np.sin(2 * np.pi * cyc * np.arange(n) / n + phase)


def cmod(n, rate, depth, seed_phase=None):
    """A slow wobble (1 +- depth) with whole cycles in the loop."""
    ph = rng.random() * 2 * np.pi if seed_phase is None else seed_phase
    return 1 + depth * ctone(n, rate, ph)


def slow_noise(n, fc):
    """Smooth random modulation (circular low-passed noise), unit RMS."""
    x = band(noise(n), None, fc, 2)
    return x / (np.std(x) + 1e-9)


def add_at(buf, i, shape, g=1.0, wrap=True):
    n = len(buf)
    i = int(i)
    k = len(shape)
    if wrap:
        i %= n
        j = min(n, i + k)
        buf[i:j] += shape[: j - i] * g
        if i + k > n:
            rest = shape[j - i:]
            buf[: len(rest)] += rest * g
    else:
        if i >= n or i + k <= 0:
            return
        a = max(0, i)
        j = min(n, i + k)
        buf[a:j] += shape[a - i: j - i] * g


def env_exp(n, tau, attack=0.0):
    t = np.arange(n) / SR
    e = np.exp(-t / max(tau, 1e-5))
    if attack > 0:
        e = e * np.clip(t / attack, 0, 1)
    return e


def burst(seconds, tau, lo=None, hi=None):
    n = max(8, int(seconds * SR))
    x = noise(n) * env_exp(n, tau)
    return band(np.pad(x, (0, n)), lo, hi)[:n] if (lo or hi) else x


def modes(seconds, spec, ph=None):
    """A struck object: decaying sines (freq, amp, decay s)."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for f, a, d in spec:
        out += a * np.sin(2 * np.pi * f * t + (rng.random() * 6.28 if ph is None else ph)) * np.exp(-t / d)
    return out


# ---------------------------------------------------------------------------------------------------------------
# Saving and checking

def soft_limit(x, peak):
    p = np.max(np.abs(x))
    if p <= peak:
        return x
    return peak * np.tanh(x / peak)


def level(x, rms_db, peak=0.97):
    x = x - np.mean(x)
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    x = x * (10 ** (rms_db / 20) / rms)
    return soft_limit(x, peak)


def encode(name, x, q=3):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + '.ogg')
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + '.wav')
        wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', str(q), path],
                       check=True)
    return path


def decode(path):
    r = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', path, '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'],
                       check=True, capture_output=True)
    return np.frombuffer(r.stdout, dtype=np.int16).astype(float) / 32767


def peaks(x, lo=20, hi=SR / 2, k=4):
    """The strongest spectral peaks (Hz) between lo and hi, for the printed check."""
    n = len(x)
    s = np.abs(np.fft.rfft(x * np.hanning(n)))
    f = fr(n)
    m = (f >= lo) & (f <= hi)
    s = np.where(m, s, 0)
    # Local maxima only.
    loc = (s[1:-1] > s[:-2]) & (s[1:-1] >= s[2:])
    idx = np.where(loc)[0] + 1
    idx = idx[np.argsort(s[idx])[::-1][:k]]
    return sorted(round(float(f[i]), 1) for i in idx)


def centroid(x):
    s = np.abs(np.fft.rfft(x)) ** 2
    f = fr(len(x))
    return float(np.sum(f * s) / (np.sum(s) + 1e-12))


def save_loop(name, x, rms_db, expect=None, q=3):
    """A seamless loop: levelled, encoded, decoded again and checked (length, the seam, the spectrum)."""
    x = level(x, rms_db)
    path = encode(name, x, q)
    y = decode(path)
    n = len(x)
    # The seam: the jump from the last sample to the first against the typical step inside the loop.
    steps = np.abs(np.diff(y))
    seam = abs(y[0] - y[-1]) / (np.percentile(steps, 99) + 1e-9)
    ok_len = len(y) == n
    rms = 20 * np.log10(np.sqrt(np.mean(y ** 2)) + 1e-12)
    pk = peaks(y, 20, 400, 3)
    CHECKS.append((name, len(y) / SR, os.path.getsize(path), rms, np.max(np.abs(y)), seam, ok_len, pk, centroid(y), expect))
    return y


def save_shot(name, x, rms_db=None, peak=0.9, fade_in=0.002, fade_out=0.04, q=3):
    x = x - np.mean(x)
    n_in, n_out = int(fade_in * SR), int(fade_out * SR)
    x = x.copy()
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    if rms_db is not None:
        x = level(x, rms_db, peak)
    else:
        x = x / (np.max(np.abs(x)) + 1e-9) * peak
    path = encode(name, x, q)
    CHECKS.append((name, len(x) / SR, os.path.getsize(path), 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12), np.max(np.abs(x)),
                   0.0, True, [], centroid(x), None))


# ---------------------------------------------------------------------------------------------------------------
# Piston engines

PETROL = dict(
    name='petrol', cyl=4, order=[0, 2, 3, 1], offsets=None, spread=0.07, var=0.05, timing=0.006,
    pulse_deg=50, pipe=[2.4], c=500.0, refl=-0.5, muffler=1300, chamber=(0.0011, 0.35), rad=55,
    knock=0.04, knock_modes=[(2300, 1.0, 0.002), (3900, 0.6, 0.0015)],
    intake=(240, 6.0), intake_gain=0.5, valve=0.035, whine=[(31, 0.004)], turbo=None, fan=0.0, gear=[], boom=None,
    pops=True, idle_lump=0.10)
DIESEL = dict(
    name='diesel', cyl=6, order=[0, 4, 2, 5, 1, 3], offsets=None, spread=0.10, var=0.07, timing=0.010,
    pulse_deg=55, pipe=[3.6], c=470.0, refl=-0.45, muffler=950, chamber=(0.0016, 0.4), rad=40,
    knock=0.55, knock_modes=[(1180, 1.0, 0.004), (1820, 0.8, 0.0035), (2650, 0.6, 0.003), (3500, 0.4, 0.002)],
    intake=(190, 5.0), intake_gain=0.35, valve=0.05, whine=[(23, 0.006)], turbo=(3600, 5600), fan=0.008, gear=[], boom=(48, 0.35),
    pops=False, idle_lump=0.14)
V12 = dict(
    name='v12', cyl=12, order=None, offsets=None, spread=0.16, var=0.11, timing=0.014,
    pulse_deg=60, pipe=[1.5, 1.85], c=470.0, refl=-0.45, muffler=760, chamber=(0.0021, 0.45), rad=35,
    knock=0.42, knock_modes=[(980, 1.0, 0.005), (1510, 0.8, 0.004), (2240, 0.6, 0.003), (3050, 0.35, 0.002)],
    intake=(160, 4.0), intake_gain=0.3, valve=0.05, whine=[(19, 0.004)], turbo=None, fan=0.018, gear=[(23.0, 0.018), (37.0, 0.010)],
    boom=(34, 0.6), pops=False, idle_lump=0.18)


def firing(spec):
    """Firing offsets (fraction of the 720 degree cycle) and the cylinder for each."""
    c = spec['cyl']
    return [(k / c, (spec['order'][k] if spec['order'] else k)) for k in range(c)]


def exhaust_pulse(rpm, strength, spec, load):
    """One blow-down: the rise, a decay lasting a fixed crank angle, turbulent rush on top."""
    tau = spec['pulse_deg'] / (6.0 * max(rpm, 60.0))  # seconds for that many degrees at this rpm
    tau = min(tau, 0.02)
    n = int(min(0.06, tau * 7) * SR) + 8
    t = np.arange(n) / SR
    rise = 1 - np.exp(-t / (tau * 0.12))
    p = rise * np.exp(-t / tau)
    turb = noise(n) * np.exp(-t / (tau * 1.6)) * rise
    mix = 0.25 + 0.35 * load
    return (p + mix * turb) * strength


def render_piston(spec, rpm, load, n, wrap, throttle=None, seed=None):
    """
    The engine at rpm (scalar for a loop, or an array per sample) and load (scalar or array, 0 off .. 1 full).
    Returns the exhaust sound and the mechanical sound (block, valves, intake) separately so callers can weigh them.
    """
    r = rng if seed is None else np.random.default_rng(seed)
    rpm_a = np.full(n, float(rpm)) if np.isscalar(rpm) else np.asarray(rpm, float)
    load_a = np.full(n, float(load)) if np.isscalar(load) else np.asarray(load, float)
    thr = load_a if throttle is None else (np.full(n, float(throttle)) if np.isscalar(throttle) else throttle)
    cyc = np.cumsum(rpm_a / 120.0) / SR  # engine cycles so far (one cycle = two turns)
    total = cyc[-1]
    cyl = spec['cyl']
    gains = 1 + spec['spread'] * (r.random(cyl) - 0.5) * 2
    toff = r.normal(0, spec['timing'], cyl) / cyl
    banks = len(spec['pipe'])
    ex = [np.zeros(n) for _ in range(banks)]
    knock = np.zeros(n)
    valves = np.zeros(n)
    intake_env = np.zeros(n)
    ticks = burst(0.004, 0.0006, 3500, 9000)
    for m in range(int(np.ceil(total)) + 1):
        for off, c in firing(spec):
            target = m + off + toff[c]
            if target < 0 or target >= total:
                continue
            i = int(np.searchsorted(cyc, target))
            if i >= n:
                continue
            rp = rpm_a[i]
            ld = load_a[i]
            if rp < 40:
                continue
            g = gains[c] * (1 + spec['var'] * r.standard_normal())
            if rp < 1.3 * 800 and ld < 0.6:
                g *= 1 + spec['idle_lump'] * r.standard_normal()
            # Strength of the firing: full fuel on load, little when off it (petrol: a thin mixture burning; diesel: no fuel).
            s = 0.18 + 0.82 * ld if spec['name'] == 'petrol' else 0.08 + 0.92 * ld
            pulse = exhaust_pulse(rp, g * s, spec, ld)
            if spec['pops'] and ld < 0.15 and rp > 1800 and r.random() < 0.035:
                # Overrun: unburnt fuel lights in the hot pipe - a pop and a crackle.
                pulse = burst(0.03, 0.006, 150, 5000) * 1.5 + np.pad(exhaust_pulse(rp, 1.8, spec, 1.0), (0, int(0.03 * SR)))[: int(0.03 * SR)]
            add_at(ex[(c % banks)], i, pulse, 1.0, wrap)
            # Diesel knock: the sharp rise of the burning rings the block. Louder at light load and low revs (cold, idle).
            kn = spec['knock'] * (0.6 + 0.4 * ld) * (1.25 if rp < 1000 else 1.0) * (0.15 if spec['name'] != 'petrol' and ld < 0.12 else 1)
            if kn > 0.005:
                km = modes(0.02, [(f * (1 + 0.02 * r.standard_normal()), a, d) for f, a, d in spec['knock_modes']])
                add_at(knock, i + int(0.0008 * SR), km, kn * (0.8 + 0.4 * r.random()), wrap)
            # The valves: an exhaust and an intake valve closing every cycle per cylinder.
            add_at(valves, i + int((0.33 / (rp / 60)) * SR), ticks, spec['valve'] * (0.7 + 0.6 * r.random()), wrap)
            # The intake stroke of this cylinder opens half a turn after it fires.
            j = i + int((0.75 / (rp / 60)) * SR)
            w = int(min(0.04, 0.5 / (rp / 60)) * SR)
            win = np.sin(np.linspace(0, np.pi, max(w, 4))) ** 2
            add_at(intake_env, j, win, 1.0, wrap)
    f = fr(n)
    out = np.zeros(n)
    for b in range(banks):
        h = h_comb(f, 2 * spec['pipe'][b] / spec['c'], spec['refl'] * np.exp(-f / 2500))
        d, rr = spec['chamber']
        h = h * h_comb(f, d, rr) * h_lp(f, spec['muffler'], 2) * h_hp(f, spec['rad'], 1)
        out += apply(ex[b], h) * (1.0 if b == 0 else 0.85)
    ex_rms = np.sqrt(np.mean(out ** 2)) + 1e-9
    # Intake roar: noise through the airbox's resonance, opened by each intake stroke, by throttle and revs.
    rpm_norm = np.clip(rpm_a / 4000.0, 0.1, 1.5)
    fc, q = spec['intake']
    ib = apply(noise(n), h_res(f, fc, q * 0.5) * 0.6 + h_res(f, fc * 3.1, 3.0) * 0.4 + h_hp(f, 900, 2) * h_lp(f, 4000, 2) * 0.3)
    intake = ib * np.minimum(intake_env, 1.5) * (0.15 + thr) * rpm_norm
    intake = intake / (np.sqrt(np.mean(intake ** 2)) + 1e-9) * ex_rms * spec['intake_gain'] * float(np.mean(0.2 + 0.8 * thr))
    # The block ringing (diesel clatter) and the valves, filtered as heard through the bonnet.
    mech = knock + valves
    mech = band(mech, 300, 8000) if wrap else band(np.pad(mech, (0, 4000)), 300, 8000)[:n]
    km = np.sqrt(np.mean(knock ** 2)) + 1e-9
    mech = mech / (np.sqrt(np.mean(mech ** 2)) + 1e-9) * ex_rms * (0.08 + 0.9 * spec['knock']) * (km > 1e-6 or spec['knock'] < 0.1)
    return out, intake, mech


def piston_extras(spec, rpm, load, n):
    """Loop-only additions at a steady rpm: turbo, fans, gear whine, the crank's whine, the body boom (all circular)."""
    x = np.zeros(n)
    ref = 1.0
    rps = rpm / 60.0
    for teeth, a in spec['whine']:
        x += ctone(n, rps * teeth) * a * ref * 0.6
    for ratio, a in spec['gear']:
        x += ctone(n, rps * ratio) * a * cmod(n, 0.5, 0.3) + ctone(n, rps * ratio * 2) * a * 0.35
    if spec['turbo'] and load > 0.2 and rpm > 1100:
        lo, hi = spec['turbo']
        ft = lo + (hi - lo) * np.clip((rpm - 1100) / 1200, 0, 1)
        ph = 2 * np.pi * np.round(ft * n / SR) * np.arange(n) / n + 3.0 * ctone(n, 1.5) * 0.3
        x += np.sin(ph) * 0.008 * load + band(noise(n), ft * 0.5, ft * 1.4) * 0.008 * load
    if spec['fan']:
        bpf = 9 * rps * 1.6  # cooling fan blades driven off the crank
        x += band(noise(n), 300, 2200) * spec['fan'] * (1 + 0.35 * ctone(n, bpf)) * (0.6 + 0.4 * rpm / 2000)
    return x


def boom(spec, n, ex):
    if not spec['boom']:
        return 0
    fc, g = spec['boom']
    return apply(ex, h_res(fr(n), fc, 2.0)) * g


def loop_len(rpm, seconds=1.6):
    """Samples for a whole number of engine cycles close to seconds."""
    cyc_hz = rpm / 120.0
    cycles = max(2, round(seconds * cyc_hz))
    n = int(round(cycles / cyc_hz * SR))
    return n, cycles * SR / n * 120.0  # the rpm that exactly fits


def piston_loop(spec, name, rpm, load, rms_db, seconds=1.6):
    n, rpm_fit = loop_len(rpm, seconds)
    ex, intake, mech = render_piston(spec, rpm_fit, load, n, True)
    x = ex + intake + mech + boom(spec, n, ex)
    ex_rms = np.sqrt(np.mean(ex ** 2))
    x += piston_extras(spec, rpm_fit, load, n) * ex_rms / 0.05
    if load < 0.15:
        # Off load: the throttle plate's whistle (petrol), injection pump and gear noise (diesel) come through.
        if spec['name'] == 'petrol':
            x += apply(noise(n), h_res(fr(n), 2400 * (rpm_fit / 3000) ** 0.3, 12)) * ex_rms * 0.25
        else:
            x += band(noise(n), 1500, 6000) * ex_rms * 0.12
    firing_hz = rpm_fit / 60 * spec['cyl'] / 2
    save_loop(name, band(x, 22, 13000), rms_db, expect='firing %.1f Hz' % firing_hz)
    return rpm_fit


# ---------------------------------------------------------------------------------------------------------------
# Engines: the band rpm the game must use for each loop is printed (and written into EngineSounds.java by hand).

BANDS = {}


def engines():
    for spec, idle, mid, high, dbs in (
            (PETROL, 850, 2600, 5000, (-22, -21, -16, -19, -13)),
            (DIESEL, 650, 1450, 2200, (-21, -21, -15, -19, -13)),
            (V12, 620, 1300, 2000, (-20, -20, -14, -18, -12))):
        nm = 'veh_' + spec['name']
        b = {}
        b['idle'] = piston_loop(spec, nm + '_idle', idle, 0.22, dbs[0])
        b['mid_off'] = piston_loop(spec, nm + '_mid_off', mid, 0.03, dbs[1])
        b['mid_on'] = piston_loop(spec, nm + '_mid_on', mid, 0.9, dbs[2])
        b['high_off'] = piston_loop(spec, nm + '_high_off', high, 0.03, dbs[3])
        b['high_on'] = piston_loop(spec, nm + '_high_on', high, 1.0, dbs[4])
        BANDS[spec['name']] = b


# ---------------------------------------------------------------------------------------------------------------
# Gas turbine (T-80's GTD-1250, the Abrams' AGT1500): compressor whine over a soft, ducted roar

def turbine():
    for nm, N, db in (('idle', 0.6, -21), ('mid', 0.8, -17), ('high', 1.0, -14)):
        n = int(1.6 * SR)
        f = fr(n)
        whine = np.zeros(n)
        # Blade-pass tones of the compressor stages (scale with spool speed), a little detuned pairs that beat.
        for base, a in ((2950, 1.0), (4420, 0.55), (1480, 0.35), (6100, 0.25)):
            ff = base * N / 0.8
            whine += a * ctone(n, ff) + a * 0.4 * ctone(n, ff * 1.004)
        whine *= cmod(n, 3.0, 0.08) * cmod(n, 0.6, 0.1)
        # The spool's shaft hum and its harmonics (the "turbine voice").
        shaft = sum(ctone(n, 395 * N / 0.8 * k) / k for k in (1, 2, 3))
        roar = apply(noise(n), h_lp(f, 900 * N, 2) * h_hp(f, 40, 2)) * (N ** 4) * 3.2
        hiss = apply(noise(n), h_hp(f, 2500, 2) * h_lp(f, 9000, 2)) * (0.4 + N)
        rumble = apply(noise(n), h_res(f, 70, 1.5)) * N ** 3 * 1.2
        x = whine * (0.10 + 0.05 * (1 - N)) + shaft * 0.05 + roar * 0.35 + hiss * 0.10 + rumble * 0.3
        save_loop('veh_turbine_' + nm, band(x, 25, 13000), db, expect='whine %d Hz' % (2950 * N / 0.8))


# ---------------------------------------------------------------------------------------------------------------
# Electric traction (trolleybus, tram): the traction motor's gear whine, its commutator buzz, the converter's hum

def electric():
    for nm, rps, db in (('low', 14.0, -22), ('high', 42.0, -17)):
        n = int(1.5 * SR)
        f = fr(n)
        teeth = 17
        gm = rps * teeth
        whine = ctone(n, gm) + 0.45 * ctone(n, gm * 2) + 0.2 * ctone(n, gm * 3) + 0.15 * ctone(n, gm * 0.5)
        whine *= cmod(n, 2.0, 0.12)
        slots = rps * 33
        comm = apply(noise(n), h_res(f, 1800, 2)) * (1 + 0.8 * ctone(n, slots))
        hum = ctone(n, 100) + 0.5 * ctone(n, 200) + 0.3 * ctone(n, 300)
        rumble = apply(noise(n), h_lp(f, 250, 2)) * (rps / 42)
        x = whine * 0.30 + comm * 0.04 * (rps / 30) + hum * 0.05 + rumble * 0.35
        save_loop('veh_electric_' + nm, band(x, 30, 12000), db, expect='gear mesh %.0f Hz' % gm)


# ---------------------------------------------------------------------------------------------------------------
# Helicopters: blade passage "whop" (thickness pulse + loading noise + slap), the tail rotor's buzz, gearbox whine

def blade_pass(n, bpf, depth, slap, blades, spread=0.12, wrap=True):
    x = np.zeros(n)
    count = int(round(bpf * n / SR))
    period = n / count
    gains = 1 + spread * (rng.random(blades) - 0.5) * 2
    for i in range(count):
        g = gains[i % blades] * (1 + 0.05 * rng.standard_normal())
        m = int(0.09 * SR)
        t = np.arange(m) / SR
        # A pressure dip (thickness) and a push (loading): a low, round thump.
        th = -np.exp(-((t - 0.012) / 0.0035) ** 2) + 0.6 * np.exp(-((t - 0.019) / 0.005) ** 2)
        # The slap: the blade's tip hitting the air (and the vortex of the blade ahead) - a sharp, broad crack.
        pulse = th * 1.0 + slap * burst(0.09, 0.004, 250, 4000) * np.exp(-((t - 0.012) / 0.0025) ** 2) * 9
        pulse += np.sin(2 * np.pi * depth * t) * np.exp(-t / 0.015) * 0.25
        add_at(x, i * period, pulse, g, wrap)
    return x


def rotors():
    n = int(2.0 * SR)
    f = fr(n)
    # Mi-8 / Mi-24: five blades (17.5 a second), a three-blade tail rotor at 56 Hz.
    main = blade_pass(n, 17.5, 55, 0.35, 5)
    tail = sum((ctone(n, 56 * k) * (0.5 / k ** 0.7)) for k in range(1, 12)) * (1 + 0.3 * ctone(n, 17.5))
    gear = ctone(n, 1330) * 0.03 + ctone(n, 2660) * 0.012
    air = apply(noise(n), h_lp(f, 1600, 2)) * 0.12 * (1 + 0.5 * ctone(n, 17.5))
    x = apply(main, h_hp(f, 25, 2)) * 1.0 + tail * 0.09 + gear + air
    save_loop('veh_rotor_main', band(x, 22, 12000), -14, expect='BPF 17.5 Hz')
    # Ka-52: two three-blade rotors turning opposite ways - blades crossing make a double thump.
    up = blade_pass(n, 12.0, 48, 0.3, 3)
    down = np.roll(blade_pass(n, 12.0, 42, 0.25, 3), int(SR / 12.0 * 0.38))
    air = apply(noise(n), h_lp(f, 1500, 2)) * 0.13 * (1 + 0.5 * ctone(n, 24))
    x = apply(up + 0.85 * down, h_hp(f, 25, 2)) + air + ctone(n, 1410) * 0.025
    save_loop('veh_rotor_coax', band(x, 22, 12000), -14, expect='BPF 12 Hz x2')
    # AH-64: four blades (20 a second), the four-blade tail rotor's blades in uneven pairs (55/125 degrees): a buzzy rasp.
    main = blade_pass(n, 20.0, 60, 0.4, 4)
    tail = np.zeros(n)
    rev = 23.5
    for i in range(int(rev * 2)):
        for off in (0.0, 55 / 360, 0.5, 0.5 + 55 / 360):
            m = int(0.01 * SR)
            add_at(tail, (i + off) / rev * SR, burst(0.01, 0.0015, 200, 3000) + np.sin(2 * np.pi * 180 * np.arange(m) / SR) * np.exp(-np.arange(m) / SR / 0.002), 1.0)
    air = apply(noise(n), h_lp(f, 1800, 2)) * 0.12 * (1 + 0.5 * ctone(n, 20))
    x = apply(main, h_hp(f, 25, 2)) + band(tail, 60, 4000) * 0.25 + air + ctone(n, 1550) * 0.02
    save_loop('veh_rotor_apache', band(x, 22, 12000), -14, expect='BPF 20 Hz, tail 94 Hz')
    # The turboshafts on their own (they spool up before the rotor gets going): whine, hiss, a little exhaust roar.
    n = int(1.6 * SR)
    f = fr(n)
    whine = ctone(n, 3150) + 0.6 * ctone(n, 4730) + 0.35 * ctone(n, 1575) + 0.2 * ctone(n, 6300)
    whine *= cmod(n, 2.5, 0.06)
    x = whine * 0.12 + apply(noise(n), h_hp(f, 2000, 2) * h_lp(f, 8000, 2)) * 0.2 + apply(noise(n), h_lp(f, 500, 2)) * 0.35
    save_loop('veh_heli_turbine', band(x, 30, 13000), -18, expect='whine 3150 Hz')


# ---------------------------------------------------------------------------------------------------------------
# Jets: idle whine; mid roar; full power roar with crackle

def jets():
    for nm, N, db in (('idle', 0.6, -20), ('mid', 0.8, -15), ('high', 1.0, -12)):
        n = int(1.8 * SR)
        f = fr(n)
        vj = N ** 3  # jet velocity share; mixing noise goes up steeply with it
        peak = 70 + 180 * vj
        roar = apply(noise(n), h_res(f, peak, 0.45) * h_lp(f, 2500 + 2500 * vj, 2) * h_hp(f, 30, 2))
        fan = 0
        shaft = 160 * N / 0.8
        for k, a in ((7, 0.5), (9, 0.35), (13, 0.25), (19, 0.6), (27, 0.3)):
            fan = fan + a * ctone(n, shaft * k)
        # Buzz-saw tones at the shaft's harmonics when the fan tips go supersonic (high power).
        buzz = sum(ctone(n, shaft * k) / k ** 0.5 for k in range(2, 14)) * max(0, N - 0.75) * 2
        crackle = np.zeros(n)
        if N > 0.9:
            for _ in range(160):
                add_at(crackle, rng.random() * n, burst(0.006, 0.0008, 600, 9000), 0.8 + rng.random())
        hiss = apply(noise(n), h_hp(f, 3000, 2) * h_lp(f, 11000, 2))
        x = roar * (0.15 + 1.6 * vj) + fan * (0.05 + 0.03 * (1 - vj)) + buzz * 0.03 + crackle * 0.5 * vj + hiss * (0.08 + 0.1 * vj)
        save_loop('veh_jet_' + nm, band(x, 22, 13500), db, expect='roar peak %.0f Hz' % peak)


# ---------------------------------------------------------------------------------------------------------------
# Running gear: tracks (slow / fast), road noise (smooth tyres / knobby military tyres), tyre squeal

def tracks():
    for nm, links, db in (('slow', 10.0, -18), ('fast', 26.0, -15)):
        n = int(2.0 * SR)
        f = fr(n)
        x = np.zeros(n)
        count = int(round(links * n / SR))
        for i in range(count):
            # The link meeting the sprocket: a heavy metallic clank (pins and plates ringing) and a thud.
            clank = modes(0.06, [(420 + 60 * rng.standard_normal(), 1.0, 0.012), (1150 + 120 * rng.standard_normal(), 0.7, 0.008),
                                 (2300 + 300 * rng.random(), 0.4, 0.005), (3700, 0.2, 0.003)])
            clank += burst(0.06, 0.004, 500, 6000) * 0.8
            thud = np.sin(2 * np.pi * 90 * np.arange(len(clank)) / SR) * env_exp(len(clank), 0.012)
            add_at(x, (i + 0.08 * rng.standard_normal()) * n / count, clank * (0.6 + 0.5 * rng.random()) + thud * 0.8, 1.0)
        # Road wheels rolling over the track pins: a rumble at the pin rate; rattle; now and then a squeal.
        rattle = np.zeros(n)
        for _ in range(int(60 * links / 10)):
            add_at(rattle, rng.random() * n, burst(0.008, 0.0012, 1500, 8000), 0.3 * rng.random())
        rumble = apply(noise(n), h_lp(f, 220, 2) * h_hp(f, 25, 2)) * (1 + 0.5 * ctone(n, links * 0.5))
        sq = np.zeros(n)
        for k in range(2 if nm == 'slow' else 1):
            m = int(0.35 * SR)
            t = np.arange(m) / SR
            s = np.sin(2 * np.pi * (2100 + 500 * k) * t + 4 * np.sin(2 * np.pi * 7 * t)) * np.sin(np.pi * t / t[-1]) ** 2
            add_at(sq, rng.random() * n, s, 0.05)
        xx = x * 1.0 + rattle * 0.5 + rumble * (0.25 if nm == 'slow' else 0.5) + sq
        save_loop('veh_tracks_' + nm, band(xx, 25, 12000), db, expect='links %.0f/s' % links)


def road():
    n = int(1.6 * SR)
    f = fr(n)
    # Smooth tyres on asphalt at ~50 km/h: tread hiss peaking near a kilohertz, cavity resonance, low rumble.
    x = apply(noise(n), h_res(f, 900, 0.8) * 0.8 + h_res(f, 220, 4) * 0.5 + h_lp(f, 120, 2) * 1.2)
    x *= 1 + 0.15 * slow_noise(n, 3)
    save_loop('veh_road', band(x, 25, 9000), -20, expect='hiss ~900 Hz')
    # Knobby military tyres: the tread blocks hum (a tone at the block rate with harmonics, wavering).
    blocks = 145.0
    hum = sum(ctone(n, blocks * k) / k for k in range(1, 6)) * (1 + 0.3 * slow_noise(n, 2))
    x = apply(noise(n), h_res(f, 700, 0.8) * 0.6 + h_lp(f, 150, 2) * 1.5) + hum * 0.25
    save_loop('veh_road_knobby', band(x, 25, 8000), -19, expect='block hum 145 Hz')


def squeal():
    n = int(2.0 * SR)
    f = fr(n)
    x = np.zeros(n)
    # Rubber stick-slipping on the road: narrow bands that wander in pitch and loudness.
    for fc, a in ((1050, 1.0), (1700, 0.6), (2550, 0.35)):
        x += apply(noise(n), h_res(f, fc, 35)) * a * (1 + 0.6 * slow_noise(n, 4)).clip(0.1)
    ph = np.cumsum(1250 * (1 + 0.04 * slow_noise(n, 3))) / SR * 2 * np.pi
    # Make the tonal part close on itself: a whole number of cycles over the loop.
    ph = ph * (np.round(ph[-1] / (2 * np.pi)) * 2 * np.pi / ph[-1])
    x += np.sin(ph) * 0.4 * np.std(x) / 0.7 * (1 + 0.3 * slow_noise(n, 5))
    save_loop('veh_tyre_squeal', band(x, 300, 9000), -16, expect='squeal ~1.1 kHz')


def beeper():
    n = int(1.1 * SR)  # 1.1 s: a beep of 0.5 s, a pause
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * 1250 * t) + 0.25 * np.sin(2 * np.pi * 3750 * t) + 0.1 * np.sin(2 * np.pi * 2500 * t)
    gate = np.clip(np.minimum((t - 0.3) / 0.006, (0.8 - t) / 0.006), 0, 1)
    save_loop('veh_reverse_beep', tone * gate + noise(n) * 1e-4, -14, expect='1250 Hz')


# ---------------------------------------------------------------------------------------------------------------
# One-shots

def clunk(heavy):
    """A gear change: the driveline's slack taken up - a dull clunk and a short metallic clack."""
    n = int(0.4 * SR)
    x = np.zeros(n)
    th = modes(0.3, [(70 + 40 * (1 - heavy), 1.0, 0.04 + 0.03 * heavy), (180, 0.5, 0.02)])
    add_at(x, 0, th, 1.0, False)
    add_at(x, int(0.012 * SR), modes(0.1, [(900 + 400 * rng.random(), 0.6, 0.01), (2100, 0.35, 0.006)]) + burst(0.1, 0.004, 800, 6000) * 0.4,
           0.5, False)
    return x


def whoosh(seconds, f0, f1):
    """A turbo letting go: air rushing out of the dump and the whistle spinning down."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    air = band(noise(n), 900, 7000) * np.exp(-t / (seconds * 0.25)) * np.clip(t / 0.01, 0, 1)
    fw = f1 + (f0 - f1) * np.exp(-t / (seconds * 0.35))
    wh = np.sin(2 * np.pi * np.cumsum(fw) / SR) * np.exp(-t / (seconds * 0.4)) * 0.25
    flutter = 1 + 0.5 * np.sin(2 * np.pi * 22 * t) * np.exp(-t / 0.15)
    return (air * flutter + wh)


def brake_squeal(f0, seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    env = np.clip(t / 0.05, 0, 1) * np.clip((seconds - t) / 0.15, 0, 1) * (0.6 + 0.4 * np.sin(np.pi * t / seconds))
    f = f0 * (1 + 0.008 * np.sin(2 * np.pi * 6 * t) + 0.02 * t / seconds)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
    return x * env + band(noise(n), 1500, 6000) * env * 0.05


def air_brake(seconds, bright):
    """The 'pssht' of an air brake letting go: a sharp burst of air, falling away."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    env = np.clip(t / 0.004, 0, 1) * (0.75 * np.exp(-t / (seconds * 0.22)) + 0.25 * np.exp(-t / (seconds * 0.6)))
    x = band(noise(n), 1400 * bright, 9000)
    x += apply(noise(n), h_res(fr(n), 3100 * bright, 6)) * 0.4
    return x * env


def horn(freqs, seconds, buzz, attack=0.015, air=0.0):
    """A horn: tones rich in harmonics (a diaphragm slapping, or an air trumpet's brass), a horn's resonance."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for k, f0 in enumerate(freqs):
        ph = 2 * np.pi * f0 * t * (1 + 0.002 * np.sin(2 * np.pi * 5 * t)) + k
        # Pulse-like wave (sum of harmonics, the slope sets how buzzy).
        w = sum(np.sin(h * ph) / h ** (1.0 / buzz) for h in range(1, 18))
        x += w
    x = np.tanh(x * 0.6)
    x = apply(np.pad(x, (0, 4000)), h_peak(fr(n + 4000), 2200, 2, 2.5) * h_peak(fr(n + 4000), 900, 1.5, 1.8) * h_hp(fr(n + 4000), 150, 2))[:n]
    env = np.clip(t / attack, 0, 1) * np.clip((seconds - t) / 0.04, 0, 1)
    if air:
        env *= 1 - 0.25 * np.exp(-t / 0.05)
        x += band(noise(n), 1500, 7000) * air * np.exp(-t / 0.08)
    return x * env


def door(close, bus=False):
    n = int((0.9 if bus else 0.5) * SR)
    x = np.zeros(n)
    if bus:
        # Pneumatic folding doors: a hiss, the leaves running, a thump at the end.
        x += air_brake(0.9, 0.8)[:n] * 0.5
        add_at(x, int(0.55 * SR), modes(0.25, [(110, 1.0, 0.04), (420, 0.5, 0.02), (1300, 0.3, 0.01)]), 0.9, False)
        add_at(x, int(0.05 * SR), band(noise(int(0.5 * SR)), 300, 2500) * np.hanning(int(0.5 * SR)) * 0.15, 1.0, False)
        return x
    if close:
        # The door's panel boom, the latch catching.
        add_at(x, 0, modes(0.35, [(85, 1.0, 0.05), (150, 0.6, 0.03), (340, 0.3, 0.015)]), 1.0, False)
        add_at(x, int(0.004 * SR), modes(0.06, [(2400, 0.5, 0.006), (3900, 0.3, 0.004)]) + burst(0.06, 0.003, 1500, 8000) * 0.4, 0.5, False)
    else:
        # The handle and the latch letting go, a small creak of the hinge.
        add_at(x, 0, modes(0.05, [(1800, 0.6, 0.005), (3200, 0.4, 0.004)]) + burst(0.05, 0.002, 1500, 8000) * 0.5, 0.6, False)
        add_at(x, int(0.06 * SR), modes(0.12, [(140, 0.6, 0.03), (420, 0.2, 0.02)]), 0.5, False)
        m = int(0.25 * SR)
        tt = np.arange(m) / SR
        creak = np.sin(2 * np.pi * np.cumsum(380 + 200 * tt / tt[-1]) / SR) * (np.sin(2 * np.pi * 31 * tt) > 0.3) * np.hanning(m)
        add_at(x, int(0.12 * SR), band(np.pad(creak, (0, 2000)), 300, 3000)[:m], 0.12, False)
    return x


def thump(v):
    n = int(0.5 * SR)
    x = modes(0.5, [(55 + 15 * v, 1.0, 0.07), (130 + 30 * v, 0.5, 0.04)])
    x += band(noise(n), 400, 4000) * env_exp(n, 0.02) * 0.3  # the body and loose kit rattling
    for _ in range(6):
        add_at(x, int(rng.random() * 0.2 * SR), burst(0.01, 0.0015, 1200, 7000), 0.25 * rng.random(), False)
    return x


def starter_and_catch(spec, idle, crank_s, catch_rpm, tail_s, starter_hz, seed):
    """Starting: the starter motor turning the engine over against its compressions, the first firings, the flare, idle."""
    total = crank_s + tail_s
    n = int(total * SR)
    t = np.arange(n) / SR
    crank_rpm = 180.0
    # Revs: cranking (lurching with each compression), then the catch flaring up and settling to idle.
    rpm = np.where(t < crank_s, crank_rpm, 0.0)
    after = t >= crank_s
    ta = t - crank_s
    rpm = np.where(after, idle + (catch_rpm - idle) * (1 - np.exp(-ta / 0.12)) * np.exp(-ta / 0.6) + crank_rpm * np.exp(-ta / 0.05) * 0, rpm)
    rpm = np.where(after, np.maximum(rpm, crank_rpm + (idle - crank_rpm) * np.clip(ta / 0.15, 0, 1)), rpm)
    comp = np.cumsum(rpm / 120 * spec['cyl']) / SR  # compressions so far
    lurch = 1 - 0.28 * np.cos(2 * np.pi * comp) * (t < crank_s)
    rpm_l = rpm * lurch
    load = np.where(t < crank_s, 0.0, np.clip(0.9 * np.exp(-ta / 0.5) + 0.22, 0, 1))
    ex, intake, mech = render_piston(spec, rpm_l, load, n, False, seed=seed)
    # Cranking: no firing yet - the compressions puff out of the pipe.
    cr = t < crank_s
    pumping = ex * 0
    x = np.where(cr, 0, ex + intake + mech)
    # The starter: its whine bogs at every compression; the pinion clacks in.
    sf = starter_hz * lurch
    sw = np.sin(2 * np.pi * np.cumsum(sf) / SR) + 0.4 * np.sin(4 * np.pi * np.cumsum(sf) / SR)
    gear = band(noise(n), 1200, 5000) * 0.3 * (1 + np.sin(2 * np.pi * np.cumsum(sf / 9) / SR))
    starter = (sw * 0.6 + gear) * (cr * np.clip(t / 0.03, 0, 1) + (~cr) * np.exp(-np.maximum(ta, 0) / 0.05))
    puffs = band(noise(n), 80, 900) * (0.5 + 0.5 * np.cos(2 * np.pi * comp)) ** 4 * cr
    ref = np.sqrt(np.mean(x[~cr] ** 2)) + 1e-9
    clack = np.zeros(n)
    add_at(clack, 0, modes(0.08, [(1600, 1.0, 0.008), (2900, 0.6, 0.005)]) + burst(0.08, 0.003, 1000, 7000), 1.0, False)
    x = x + starter * ref * 0.35 + puffs * ref * 0.6 + clack * ref * 1.2
    # Fade the tail: the game's idle loop takes over there.
    x *= np.clip((total - t) / 0.45, 0, 1)
    return x


def stop_engine(spec, idle, seconds, seed):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    rpm = idle * np.clip(1 - t / (seconds * 0.55), 0, 1) ** 0.7
    load = np.full(n, 0.05)
    ex, intake, mech = render_piston(spec, rpm, load, n, False, seed=seed)
    x = ex + mech
    # The last turn rocks back against a compression: a shudder and a clunk.
    stop_at = seconds * 0.55
    add_at(x, int(stop_at * SR), modes(0.4, [(38, 1.0, 0.09), (95, 0.5, 0.05), (900, 0.08, 0.01)]) * (np.sqrt(np.mean(x ** 2)) * 6), 1.0, False)
    return x


def turbine_start(seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    N = 0.6 * (1 - np.exp(-t / (seconds * 0.35)))
    ph = 2 * np.pi * np.cumsum(2950 * N / 0.8) / SR
    whine = (np.sin(ph) + 0.55 * np.sin(ph * 1.5) + 0.35 * np.sin(ph * 0.5)) * (N / 0.6) ** 1.5
    # The igniters ticking, then the light-off "whump" a second in.
    ign = np.zeros(n)
    for k in range(int(seconds * 4)):
        add_at(ign, int(k / 4 * SR), burst(0.01, 0.002, 2000, 8000), 0.3, False)
    lo = int(seconds * 0.3 * SR)
    whump = np.zeros(n)
    add_at(whump, lo, band(noise(int(0.8 * SR)), 30, 400) * env_exp(int(0.8 * SR), 0.2, 0.03), 1.0, False)
    roar = band(noise(n), 40, 900) * np.clip((t - seconds * 0.3) / (seconds * 0.4), 0, 1) * 0.4
    hiss = band(noise(n), 2500, 9000) * (N / 0.6) * 0.15
    x = whine * 0.25 + ign * 0.4 + whump * 0.9 + roar + hiss
    return x * np.clip((seconds - t) / 0.5, 0, 1)


def turbine_stop(seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    N = 0.6 * np.exp(-t / (seconds * 0.35))
    ph = 2 * np.pi * np.cumsum(2950 * N / 0.8) / SR
    whine = (np.sin(ph) + 0.55 * np.sin(ph * 1.5) + 0.35 * np.sin(ph * 0.5)) * (N / 0.6) ** 1.2
    x = whine * 0.25 + band(noise(n), 2500, 9000) * (N / 0.6) * 0.15 + band(noise(n), 40, 700) * np.exp(-t / 0.4) * 0.4
    return x


def one_shots():
    for v, h in enumerate((0.0, 0.5, 1.0)):
        save_shot('veh_shift_%d' % v, clunk(h), peak=0.8)
    save_shot('veh_turbo_0', whoosh(0.7, 5200, 1500), peak=0.6)
    save_shot('veh_turbo_1', whoosh(0.9, 4300, 1200), peak=0.6)
    save_shot('veh_brake_squeal_0', brake_squeal(3150, 1.0), peak=0.55)
    save_shot('veh_brake_squeal_1', brake_squeal(2480, 1.3), peak=0.55)
    save_shot('veh_air_brake_0', air_brake(0.8, 1.0), peak=0.75)
    save_shot('veh_air_brake_1', air_brake(1.1, 0.8), peak=0.75)
    save_shot('veh_horn_car', horn([392, 494], 0.55, 2.2), peak=0.8)
    save_shot('veh_horn_truck', horn([233, 294, 349], 1.1, 3.0, attack=0.06, air=0.25), peak=0.85)
    save_shot('veh_horn_bus', horn([311, 330], 0.7, 2.6, attack=0.02), peak=0.8)
    save_shot('veh_door_open', door(False), peak=0.6)
    save_shot('veh_door_close', door(True), peak=0.8)
    save_shot('veh_bus_door', door(True, bus=True), peak=0.7)
    for v in range(2):
        save_shot('veh_thump_%d' % v, thump(v), peak=0.8)
    save_shot('veh_start_petrol', starter_and_catch(PETROL, 850, 0.75, 1900, 1.0, 260, 11), peak=0.8)
    save_shot('veh_start_diesel', starter_and_catch(DIESEL, 650, 1.1, 1250, 1.3, 190, 12), peak=0.85)
    save_shot('veh_start_tank', starter_and_catch(V12, 620, 1.8, 1200, 1.6, 150, 13), peak=0.9)
    save_shot('veh_start_turbine', turbine_start(4.0), peak=0.8)
    save_shot('veh_stop_petrol', stop_engine(PETROL, 850, 0.9, 21), peak=0.7)
    save_shot('veh_stop_diesel', stop_engine(DIESEL, 650, 1.2, 22), peak=0.75)
    save_shot('veh_stop_tank', stop_engine(V12, 620, 1.5, 23), peak=0.8)
    save_shot('veh_stop_turbine', turbine_stop(3.5), peak=0.7)


# ---------------------------------------------------------------------------------------------------------------
# sounds.json

EVENTS = {
    # event: (files, attenuation distance, subtitle)
    'veh_petrol_idle': (['veh_petrol_idle'], 24, 'veh_engine'),
    'veh_petrol_mid_on': (['veh_petrol_mid_on'], 40, 'veh_engine'),
    'veh_petrol_mid_off': (['veh_petrol_mid_off'], 32, 'veh_engine'),
    'veh_petrol_high_on': (['veh_petrol_high_on'], 56, 'veh_engine'),
    'veh_petrol_high_off': (['veh_petrol_high_off'], 40, 'veh_engine'),
    'veh_diesel_idle': (['veh_diesel_idle'], 32, 'veh_diesel'),
    'veh_diesel_mid_on': (['veh_diesel_mid_on'], 56, 'veh_diesel'),
    'veh_diesel_mid_off': (['veh_diesel_mid_off'], 40, 'veh_diesel'),
    'veh_diesel_high_on': (['veh_diesel_high_on'], 72, 'veh_diesel'),
    'veh_diesel_high_off': (['veh_diesel_high_off'], 48, 'veh_diesel'),
    'veh_v12_idle': (['veh_v12_idle'], 48, 'veh_tank_engine'),
    'veh_v12_mid_on': (['veh_v12_mid_on'], 80, 'veh_tank_engine'),
    'veh_v12_mid_off': (['veh_v12_mid_off'], 56, 'veh_tank_engine'),
    'veh_v12_high_on': (['veh_v12_high_on'], 96, 'veh_tank_engine'),
    'veh_v12_high_off': (['veh_v12_high_off'], 64, 'veh_tank_engine'),
    'veh_turbine_idle': (['veh_turbine_idle'], 56, 'veh_turbine'),
    'veh_turbine_mid': (['veh_turbine_mid'], 80, 'veh_turbine'),
    'veh_turbine_high': (['veh_turbine_high'], 100, 'veh_turbine'),
    'veh_electric_low': (['veh_electric_low'], 32, 'veh_electric'),
    'veh_electric_high': (['veh_electric_high'], 48, 'veh_electric'),
    'veh_rotor_main': (['veh_rotor_main'], 220, 'rotor'),
    'veh_rotor_coax': (['veh_rotor_coax'], 220, 'rotor'),
    'veh_rotor_apache': (['veh_rotor_apache'], 220, 'rotor'),
    'veh_heli_turbine': (['veh_heli_turbine'], 96, 'veh_turbine'),
    'veh_jet_idle': (['veh_jet_idle'], 120, 'jet'),
    'veh_jet_mid': (['veh_jet_mid'], 240, 'jet'),
    'veh_jet_high': (['veh_jet_high'], 320, 'jet'),
    'veh_tracks_slow': (['veh_tracks_slow'], 48, 'tracks'),
    'veh_tracks_fast': (['veh_tracks_fast'], 64, 'tracks'),
    'veh_road': (['veh_road'], 24, 'veh_road'),
    'veh_road_knobby': (['veh_road_knobby'], 32, 'veh_road'),
    'veh_tyre_squeal': (['veh_tyre_squeal'], 48, 'veh_tyre_squeal'),
    'veh_reverse_beep': (['veh_reverse_beep'], 32, 'veh_reverse_beep'),
    'veh_shift': (['veh_shift_0', 'veh_shift_1', 'veh_shift_2'], 24, 'veh_shift'),
    'veh_turbo': (['veh_turbo_0', 'veh_turbo_1'], 32, 'veh_turbo'),
    'veh_brake_squeal': (['veh_brake_squeal_0', 'veh_brake_squeal_1'], 40, 'veh_brake_squeal'),
    'veh_air_brake': (['veh_air_brake_0', 'veh_air_brake_1'], 40, 'veh_air_brake'),
    'veh_horn_car': (['veh_horn_car'], 72, 'veh_horn'),
    'veh_horn_truck': (['veh_horn_truck'], 120, 'veh_horn'),
    'veh_horn_bus': (['veh_horn_bus'], 80, 'veh_horn'),
    'veh_door_open': (['veh_door_open'], 16, 'veh_door_open'),
    'veh_door_close': (['veh_door_close'], 20, 'veh_door_close'),
    'veh_bus_door': (['veh_bus_door'], 24, 'veh_bus_door'),
    'veh_thump': (['veh_thump_0', 'veh_thump_1'], 24, 'veh_thump'),
    'veh_start_petrol': (['veh_start_petrol'], 32, 'veh_start'),
    'veh_start_diesel': (['veh_start_diesel'], 48, 'veh_start'),
    'veh_start_tank': (['veh_start_tank'], 72, 'veh_start'),
    'veh_start_turbine': (['veh_start_turbine'], 80, 'veh_start'),
    'veh_stop_petrol': (['veh_stop_petrol'], 24, 'veh_stop'),
    'veh_stop_diesel': (['veh_stop_diesel'], 32, 'veh_stop'),
    'veh_stop_tank': (['veh_stop_tank'], 48, 'veh_stop'),
    'veh_stop_turbine': (['veh_stop_turbine'], 64, 'veh_stop'),
}


def register():
    with open(SOUNDS_JSON, encoding='utf-8') as fh:
        d = json.load(fh)
    for key, (files, dist, sub) in EVENTS.items():
        d[key] = {'sounds': [{'name': 'airdefense:' + f, 'attenuation_distance': dist} for f in files], 'subtitle': 'subtitles.airdefense.' + sub}
    with open(SOUNDS_JSON, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=2, ensure_ascii=False)
        fh.write('\n')


def report():
    total = 0
    print('%-22s %6s %7s %7s %6s %6s %4s  %-26s %7s  %s' % ('name', 'sec', 'bytes', 'rmsdB', 'peak', 'seam', 'len', 'low peaks (Hz)', 'centr', 'expect'))
    for name, sec, size, rms, pk, seam, ok, low, cen, exp in CHECKS:
        total += size
        print('%-22s %6.2f %7d %7.1f %6.2f %6.2f %4s  %-26s %7.0f  %s' % (name, sec, size, rms, pk, seam, 'ok' if ok else 'BAD', low, cen, exp or ''))
    print('total %.0f KB in %d files' % (total / 1024, len(CHECKS)))
    for k, b in BANDS.items():
        print('band rpm', k, {kk: round(v, 1) for kk, v in b.items()})


if __name__ == '__main__':
    what = sys.argv[1:] or ['engines', 'turbine', 'electric', 'rotors', 'jets', 'tracks', 'road', 'squeal', 'beeper', 'one_shots', 'register']
    for w in what:
        {'engines': engines, 'turbine': turbine, 'electric': electric, 'rotors': rotors, 'jets': jets, 'tracks': tracks, 'road': road,
         'squeal': squeal, 'beeper': beeper, 'one_shots': one_shots, 'register': register}[w]()
    report()
