"""
More procedural sounds (2026 additions): the radar screen's ping, the Shahed's moped buzz, the cruise missile's jet
whine. Built the same way as make_sounds.py (synthesised, nothing recorded).

Run: python3 tools/make_sounds_war.py
"""
import sys

import numpy as np
from scipy import signal

import make_sounds as ms
from make_sounds import SR, t_axis, white, lp, hp, bp, reverb, save, rng


def radar_ping():
    """A short sonar-like ping for a new contact on the scope."""
    length = 0.9
    t = t_axis(length)
    x = np.sin(2 * np.pi * 1320 * t) * np.exp(-t / 0.12) + 0.3 * np.sin(2 * np.pi * 2640 * t) * np.exp(-t / 0.05)
    x = reverb(x, length, 0.6, damp=4000, mix=0.25)
    save('radar_ping', x, peak=0.5)


def shahed():
    """
    The Shahed-136's engine: a small two-stroke (Mado/Limbach copy) at high revs - the rasping "moped in the sky".
    A pulse train at ~118 Hz with uneven pulses, a resonant exhaust, the propeller's blade hum, a slow wobble.
    Loops seamlessly (whole number of cycles of everything periodic).
    """
    length = 3.0
    n = int(length * SR)
    t = np.arange(n) / SR
    f0 = 118.0
    # Slight rpm wobble, periodic over the loop.
    wob = 1 + 0.012 * np.sin(2 * np.pi * t / length) + 0.006 * np.sin(2 * np.pi * 3 * t / length)
    phase = np.cumsum(f0 * wob) / SR
    # Make the total phase a whole number of cycles so the loop does not click.
    phase *= np.round(phase[-1]) / phase[-1]
    frac = phase % 1.0
    idx = np.floor(phase).astype(int)
    amp = 0.75 + 0.25 * rng.random(idx.max() + 2)
    pulses = np.exp(-frac / 0.07) * amp[idx]
    sharp = np.where(frac < 0.02, 1.0, 0.0) * amp[idx]
    x = pulses + 0.6 * sharp
    x = x - np.mean(x)
    # Exhaust and engine body resonances.
    body = bp(x, 90, 900, order=2) * 1.0 + bp(x, 1200, 2600, order=2) * 0.45 + bp(x, 3000, 6000, order=2) * 0.12
    # Propeller blade passing (2 blades, ~ rpm/60 * 2).
    blade = np.sin(2 * np.pi * np.cumsum(np.full(n, f0 * 2.0)) / SR)
    blade_phase = np.round(f0 * 2.0 * length)
    blade = np.sin(2 * np.pi * blade_phase * t / length)
    noise = bp(white(length), 400, 5000) * 0.08 * (1 + 0.6 * pulses / (np.max(pulses) + 1e-9))
    y = body + 0.25 * blade + noise
    # Gritty saturation - what makes it sound like a cheap engine.
    y = np.tanh(2.2 * y / (np.max(np.abs(y)) + 1e-9))
    y = hp(y, 70)
    # Cross-fade the ends (the filters' start-up) for a clean loop.
    k = int(0.05 * SR)
    y[:k] = y[:k] * np.linspace(0, 1, k) + y[-k:] * np.linspace(1, 0, k)
    y = y[:-k]
    save('shahed_loop', y, peak=0.8, loop=True)
    # The same heard from far away: only the low buzz survives.
    far = lp(y, 900) + 0.2 * bp(y, 900, 1600)
    save('shahed_far', far, peak=0.8, loop=True)


def jet():
    """Cruise missile turbojet: a hoarse roar with a high whine."""
    length = 3.0
    t = t_axis(length)
    roar = bp(white(length), 150, 2500, order=2)
    whine_f = np.round(3400 * length) / length
    whine = np.sin(2 * np.pi * whine_f * t) * 0.18 + np.sin(2 * np.pi * (np.round(5100 * length) / length) * t) * 0.07
    y = roar + whine
    k = int(0.05 * SR)
    y[:k] = y[:k] * np.linspace(0, 1, k) + y[-k:] * np.linspace(1, 0, k)
    y = y[:-k]
    save('cruise_loop', y, peak=0.75, loop=True)


if __name__ == '__main__':
    radar_ping()
    shahed()
    jet()
