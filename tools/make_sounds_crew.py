"""
1.26 "Crew": new engine loops and a new air raid siren, synthesised like the rest of the mod's sounds (nothing is
recorded or copied).

  engine_diesel   - an army truck's V8 diesel (KamAZ / Ural): firing pulses with a cylinder-to-cylinder lilt, the
                    exhaust's resonance, injector knock, a faint turbo whistle.
  engine_tank     - a tank's V12 diesel (V-84 / MTU): heavier, rougher, the transmission's whine and the cooling fans.
  engine_turbine  - the Abrams' gas turbine: a jet-like whine over a soft roar.
  tracks          - track links slapping the sprocket and road wheels, squeal and rattle (loudness follows speed).
  rotor_heavy     - a big five-blade main rotor (Mi-8, Mi-24): the deep "whop", turbines and the tail rotor's buzz.
  rotor_coax      - two coaxial three-blade rotors (Ka-52): the double thump.
  jet             - a combat jet's engines (Su-25, F-16): roar and whine.
  siren_wail(_far), siren_clear(_far), siren_start, siren_stop - the motor siren again, rebuilt: a smooth, hollow
                    howl (two rotors a little apart, so it beats) with a city's echoes, instead of the buzzy one.

Every loop holds a whole number of every periodic thing in it and is filtered circularly, so it loops without a click.

Run: python3 tools/make_sounds_crew.py
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_sounds as ms  # noqa: E402
from make_sounds import SR, t_axis, save  # noqa: E402

# make_sounds takes its first argument as the output folder; here the arguments name what to build.
ms.OUT = os.environ.get('SOUNDS_OUT', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                                                   'airdefense', 'sounds'))

rng = np.random.default_rng(20261009)


# ---------------------------------------------------------------------------------------------------------------
# Circular building blocks (a loop's end meets its start)

def cfilt(x, lo=None, hi=None, order=4):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    m = np.ones_like(f)
    if lo:
        m *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    if hi:
        m *= 1 / np.sqrt(1 + (f / hi) ** (2 * order))
    return np.fft.irfft(X * m, len(x))


def cres(x, fc, q):
    """A resonance (peak) at fc with quality q, circular."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    m = 1 / np.sqrt(1 + (q * (f / fc - fc / np.maximum(f, 1e-3))) ** 2)
    return np.fft.irfft(X * m, len(x))


def cconv(x, ir):
    L = len(x)
    return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(ir, L), L)


def cnoise(n):
    return rng.standard_normal(n)


def pulse_train(n, times, shape):
    """Adds shape (array) at each time (samples), wrapping round the end."""
    out = np.zeros(n)
    for t, g in times:
        i = int(t) % n
        k = len(shape)
        j = min(n, i + k)
        out[i:j] += shape[: j - i] * g
        if i + k > n:
            out[: i + k - n] += shape[j - i:] * g
    return out


def burst(ms_len, tau_ms, lo=None, hi=None):
    n = int(ms_len / 1000 * SR)
    t = np.arange(n) / SR
    x = rng.standard_normal(n) * np.exp(-t / (tau_ms / 1000))
    if lo or hi:
        x = ms.bp(x, lo or 20, hi or 18000, 2) if lo and hi else (ms.hp(x, lo, 2) if lo else ms.lp(x, hi, 2))
    return x


def mixp(*parts):
    """Sum of arrays of different lengths (padded with silence)."""
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def thump(ms_len, f0, tau_ms):
    """A low pressure pulse: a damped sine starting at its peak."""
    n = int(ms_len / 1000 * SR)
    t = np.arange(n) / SR
    return np.cos(2 * np.pi * f0 * t) * np.exp(-t / (tau_ms / 1000)) * np.clip(t / 0.002, 0, 1)


def tone(n, f, length):
    """A sine with a whole number of cycles in the loop (f rounded to fit)."""
    fr = round(f * length) / length
    return np.sin(2 * np.pi * fr * np.arange(n) / SR)


def sloop(name, x, peak=0.8):
    save(name, x, peak=peak, loop=True)


# ---------------------------------------------------------------------------------------------------------------
# Engines

def diesel(name, cylinders, firing_hz, length, roughness, exhaust_hz, body_hz, knock, turbo_hz=0.0, extra=None):
    """A four-stroke diesel at a steady speed: one pulse per cylinder firing, each cylinder a little different."""
    n = int(length * SR)
    cycle_hz = firing_hz / cylinders  # one full four-stroke cycle (two turns)
    assert abs(cycle_hz * length - round(cycle_hz * length)) < 1e-6, (name, cycle_hz * length)
    gains = 1 + roughness * (rng.random(cylinders) - 0.5) * 2
    jitter = rng.normal(0, 0.03, cylinders) / firing_hz * SR
    times = []
    for c in range(int(round(cycle_hz * length))):
        for k in range(cylinders):
            t = (c * cylinders + k) / firing_hz * SR + jitter[k]
            times.append((t, gains[k]))
    # The combustion bark: a sharp pressure step that rings in the exhaust.
    bark = mixp(thump(40, exhaust_hz, 9), 0.5 * burst(30, 4, 200, 2500))
    x = pulse_train(n, times, bark)
    x = cres(x, exhaust_hz, 1.2) * 0.8 + cres(x, body_hz, 0.8) * 1.2
    # Injector / valve knock: short ticks on every firing, high and dry.
    tick = burst(6, 0.8, 1800, 7000)
    x += pulse_train(n, [(t + 0.004 * SR, g) for t, g in times], tick) * knock
    # Rumble of the block (sub-harmonic of the cycle) and some air.
    x += tone(n, cycle_hz * 2, length) * 0.18 + tone(n, firing_hz / 2, length) * 0.12
    air = cfilt(cnoise(n), 300, 2500) * 0.08
    x += air * (1 + 0.5 * np.sin(2 * np.pi * round(cycle_hz * length) / length * np.arange(n) / SR))
    if turbo_hz:
        x += tone(n, turbo_hz, length) * 0.025 + cfilt(cnoise(n), 2500, 6000) * 0.02
    if extra is not None:
        x += extra(n, length)
    return cfilt(x, 28, 9000)


def engines():
    # Army truck V8: 1200 rpm -> 80 firings a second; the game shifts the pitch from idle (~0.65) to full (~1.6).
    sloop('engine_truck', diesel('engine_truck', 8, 80, 2.0, 0.28, 120, 55, 0.35, turbo_hz=3150), peak=0.8)

    # Tank V12: 1320 rpm -> 132 firings a second; deeper exhaust, harder knock, gearbox whine and the fans.
    def tank_extra(n, length):
        t = np.arange(n) / SR
        gear = (tone(n, 610, length) * 0.05 + tone(n, 915, length) * 0.03) * (1 + 0.3 * np.sin(2 * np.pi * 3 / length * t))
        fans = cfilt(cnoise(n), 500, 3500) * 0.07 * (1 + 0.2 * tone(n, 46, length))
        return gear + fans

    sloop('engine_tracked', diesel('engine_tracked', 12, 132, 2.0, 0.42, 95, 42, 0.5, extra=tank_extra), peak=0.85)

    # Gas turbine (Abrams): compressor whine, its harmonic, a hiss and a soft roar; no pulses.
    n = int(2.0 * SR)
    t = np.arange(n) / SR
    whine = tone(n, 1880, 2.0) * 0.35 + tone(n, 3760, 2.0) * 0.12 + tone(n, 2510, 2.0) * 0.08
    whine *= 1 + 0.05 * tone(n, 7, 2.0)
    hiss = cfilt(cnoise(n), 1200, 7000) * 0.35
    roar = cfilt(cnoise(n), 40, 400) * 0.7
    sloop('engine_turbine', cfilt(whine + hiss + roar, 30, 12000), peak=0.75)

    # Tracks at a medium speed: 15 links a second over the sprocket, each a metallic slap; the road wheels' rumble.
    n = int(2.0 * SR)
    links = 30
    clank = mixp(burst(25, 5, 700, 5000), thump(25, 160, 6) * 0.6)
    times = [((i + rng.normal(0, 0.06)) / links * n, 0.7 + 0.3 * rng.random()) for i in range(links)]
    x = pulse_train(n, times, clank)
    # Rattle: many small knocks.
    small = burst(8, 1.5, 1500, 8000)
    x += pulse_train(n, [(rng.random() * n, 0.25 * rng.random()) for _ in range(140)], small)
    # Squeal now and then.
    sq = np.zeros(n)
    for k in range(3):
        a = int(rng.random() * n)
        m = int(0.25 * SR)
        tt = np.arange(m) / SR
        s = np.sin(2 * np.pi * (2300 + 300 * k) * tt + 3 * np.sin(2 * np.pi * 6 * tt)) * np.sin(np.pi * tt / tt[-1]) ** 2 * 0.06
        sq = sq + np.roll(np.pad(s, (0, n - m)), a)
    rumble = cfilt(cnoise(n), 30, 180) * 0.6
    sloop('tracks', cfilt(x + sq + rumble, 30, 10000), peak=0.8)


# ---------------------------------------------------------------------------------------------------------------
# Rotors and jets

def rotor(name, blade_hz, length, depth_hz, slap, tail_hz, turbine_hz, coax=False):
    n = int(length * SR)
    count = int(round(blade_hz * length))
    assert abs(blade_hz * length - count) < 1e-6
    whop = mixp(thump(70, depth_hz, 22), slap * burst(35, 5, 300, 3000))
    times = [(i / blade_hz * SR, 0.85 + 0.15 * np.cos(2 * np.pi * i / 5)) for i in range(count)]
    x = pulse_train(n, times, whop)
    if coax:
        # The lower rotor turns the other way: its blades pass between the upper's, a touch softer and lower.
        whop2 = mixp(thump(70, depth_hz * 0.85, 24), slap * 0.8 * burst(35, 5, 250, 2500))
        times2 = [((i + 0.5) / blade_hz * SR, 0.7) for i in range(count)]
        x += pulse_train(n, times2, whop2)
    x = cres(x, depth_hz, 0.7) * 1.4 + x * 0.6
    if tail_hz:
        x += pulse_train(n, [(i / tail_hz * SR, 1.0) for i in range(int(round(tail_hz * length)))], burst(10, 2, 400, 2500)) * 0.25
    whine = tone(n, turbine_hz, length) * 0.05 + tone(n, turbine_hz * 1.5, length) * 0.025
    hiss = cfilt(cnoise(n), 1500, 6000) * 0.06
    rumble = cfilt(cnoise(n), 30, 200) * 0.25
    return cfilt(x + whine + hiss + rumble, 25, 10000)


def aircraft():
    sloop('rotor_heavy', rotor('rotor_heavy', 17, 2.0, 55, 0.9, 58, 2890), peak=0.85)
    sloop('rotor_coax', rotor('rotor_coax', 13, 2.0, 48, 0.8, 0, 3100, coax=True), peak=0.85)
    n = int(2.0 * SR)
    roar = cfilt(cnoise(n), 35, 2600) * 0.9 + cfilt(cnoise(n), 2600, 8000) * 0.2
    whine = tone(n, 1210, 2.0) * 0.08 + tone(n, 3050, 2.0) * 0.05 + tone(n, 6100, 2.0) * 0.015
    crackle = np.zeros(n)
    for _ in range(90):
        a = int(rng.random() * n)
        b = burst(12, 2, 300, 4000) * rng.random() * 0.4
        crackle[a:a + len(b)] += b[: n - a]
    sloop('jet', cfilt(roar + whine + crackle, 25, 12000), peak=0.8)


# ---------------------------------------------------------------------------------------------------------------
# The siren

def siren_voice(f, seed=0, detune=1.011):
    """
    A motor siren for a pitch curve f (Hz per sample). Two rotors (as on the big two-tone sirens) a hair apart, so the
    sound beats slowly; a hollow tone - strong fundamental, a clear octave, a little of the odd harmonics - with the
    rush of air through the ports. Much smoother than a buzzy square: this is the howl heard across a town.
    """
    out = np.zeros_like(f)
    for rotor_k, (d, g) in enumerate(((1.0, 1.0), (detune, 0.45))):
        ph = 2 * np.pi * np.cumsum(f * d) / SR + rotor_k * 1.3
        v = (np.sin(ph) + 0.42 * np.sin(2 * ph + 0.4) + 0.2 * np.sin(3 * ph + 0.9) + 0.07 * np.sin(4 * ph + 1.7)
             + 0.05 * np.sin(5 * ph + 0.2))
        out += v * g
    out = np.tanh(out * 0.55) / 0.55
    r = np.random.default_rng(seed)
    air = r.standard_normal(len(f))
    air = cfilt(air, 250, 3000) * 0.12
    return out + air


def city(x, far=False):
    """The town's echoes: early reflections off the houses, then a long tail."""
    if far:
        x = cfilt(x, None, 1000, 2)
        taps = [(0.08, 0.5, 900), (0.45, 0.45, 800), (1.1, 0.35, 650), (2.0, 0.25, 500), (3.1, 0.15, 400)]
        tail, damp, mix = 4.5, 800, 0.6
    else:
        taps = [(0.03, 0.35, 4000), (0.11, 0.3, 3000), (0.27, 0.22, 2200), (0.6, 0.18, 1600), (1.2, 0.12, 1200)]
        tail, damp, mix = 2.6, 3200, 0.32
    out = x.copy()
    for d, g, hi in taps:
        out += np.roll(cfilt(x, None, hi, 2), int(d * SR)) * g
    return ms.circ_reverb(out, tail, damp, mix)


def sirens():
    # The alert: a 12 s cycle - the rotor spins up (the pitch climbs, fast then slowly), holds a moment at the top,
    # coasts down; repeated for as long as the alert lasts.
    T = 12.0
    t = t_axis(T)
    lo, hi = 210.0, 455.0
    up, hold = 4.6, 1.6
    rise = 1 - np.exp(-t / 1.25)
    rise /= rise[int(up * SR) - 1]
    fall_t = np.clip(t - up - hold, 0, None)
    fall = np.exp(-fall_t / 2.3)
    fall_end = np.exp(-(T - up - hold) / 2.3)
    fall = (fall - fall_end) / (1 - fall_end)
    s = np.where(t < up, np.clip(rise, 0, 1), np.where(t < up + hold, 1.0, fall))
    # A gentle sway at the top, as the motor hunts.
    s = s + np.where((t >= up) & (t < up + hold), 0.01 * np.sin(2 * np.pi * 1.3 * (t - up)), 0)
    f = lo + (hi - lo) * s
    # Whole number of cycles over the loop for both rotors (keeps the loop seamless).
    cycles = np.sum(f) / SR
    f *= round(cycles) / cycles
    x = siren_voice(f, detune=1.0 + round(0.007 * cycles) / cycles)
    loud = 0.5 + 0.5 * s ** 0.7
    sloop('siren_wail', city(x * loud), peak=0.9)
    sloop('siren_wail_far', city(x * loud, far=True), peak=0.8)
    # All clear: one steady tone.
    T2 = 6.0
    f2 = np.full(int(T2 * SR), 420.0)
    c2 = np.sum(f2) / SR
    f2 *= round(c2) / c2
    y = siren_voice(f2, seed=2, detune=1.0 + round(0.004 * c2) / c2)
    sloop('siren_clear', city(y), peak=0.85)
    sloop('siren_clear_far', city(y, far=True), peak=0.75)
    # Spin-up from rest to the bottom of the wail; coasting down to silence.
    t3 = t_axis(2.4)
    f3 = lo * (1 - np.exp(-t3 / 0.8)) / (1 - np.exp(-2.4 / 0.8))
    z = siren_voice(f3, seed=3) * np.clip(t3 / 1.1, 0, 1) ** 1.6
    save('siren_start', ms.reverb(z, 3.4, 2.0, damp=3500, mix=0.3), peak=0.8)
    t4 = t_axis(9.0)
    f4 = 330 * np.exp(-t4 / 2.8)
    w = siren_voice(f4, seed=4) * np.clip(1 - t4 / 9.0, 0, 1) ** 0.8
    save('siren_stop', ms.reverb(w, 10.0, 2.6, damp=2800, mix=0.35), peak=0.8)


if __name__ == '__main__':
    what = sys.argv[1:] or ['engines', 'aircraft', 'sirens']
    for w in what:
        {'engines': engines, 'aircraft': aircraft, 'sirens': sirens}[w]()
