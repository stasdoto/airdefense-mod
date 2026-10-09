"""
1.27 "Gear": the radio on a vest - a burst of squelch (band-limited static that opens and closes) and two short beeps,
heard before a warning comes over it. Synthesised like the rest of the mod's sounds.

Run: python3 tools/make_sounds_gear.py
"""
import os
import sys

import numpy as np
from scipy import signal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_sounds as ms  # noqa: E402
from make_sounds import SR, t_axis, save  # noqa: E402

ms.OUT = os.environ.get('SOUNDS_OUT', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                                                   'airdefense', 'sounds'))
rng = np.random.default_rng(20261009)


def band(x, lo, hi):
    return signal.sosfilt(signal.butter(4, [lo, hi], 'bp', fs=SR, output='sos'), x)


def radio():
    total = 0.95
    t = t_axis(total)
    out = np.zeros_like(t)
    # The squelch: static through a radio's narrow band, opening fast, a crackle, closing with a tail.
    n = band(rng.standard_normal(len(t)), 500, 3200)
    crackle = (rng.random(len(t)) < 0.004) * rng.standard_normal(len(t)) * 4
    n += band(crackle, 800, 4000)
    env = np.clip(t / 0.015, 0, 1) * np.clip((0.38 - t) / 0.06, 0, 1)
    out += n * env * 0.55
    # Two beeps, the tone a little bent like a cheap speaker.
    for start, f in ((0.44, 1250.0), (0.62, 1250.0)):
        m = (t >= start) & (t < start + 0.11)
        tt = t[m] - start
        tone = np.sign(np.sin(2 * np.pi * f * tt)) * 0.35 + np.sin(2 * np.pi * f * tt) * 0.65
        out[m] += band(tone, 400, 3500) * np.clip(tt / 0.006, 0, 1) * np.clip((0.11 - tt) / 0.01, 0, 1) * 0.6
    # The speaker's own colour.
    out = band(out, 300, 3600)
    out = np.tanh(out * 1.6)
    return out


if __name__ == '__main__':
    save('radio', radio(), peak=0.8)
