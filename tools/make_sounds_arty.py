"""
1.30 "Artillery": a howitzer's shot near and far, and the whistle of a shell coming in. Synthesised like the rest of
the mod's sounds (nothing recorded), then written into sounds.json.

Run: python3 tools/make_sounds_arty.py
"""
import json
import os
import sys

import numpy as np
from scipy import signal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_sounds as ms  # noqa: E402
from make_sounds import SR, t_axis, white, brown, lp, hp, bp, decay, place, sweep, reverb, echoes, save  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'airdefense')
ms.OUT = os.environ.get('SOUNDS_OUT', os.path.join(ROOT, 'sounds'))
rng = np.random.default_rng(20261130)


def shot(size, length):
    """The muzzle blast of a 152/155 mm gun: a hard crack, a chest-deep boom, the gas roaring out of the muzzle brake."""
    out = np.zeros(int(length * SR))
    place(out, hp(white(0.02), 1100) * decay(int(0.02 * SR), 0.003), 0.0, 1.0)
    place(out, sweep(75 * size, 26, 1.9, 4.5) * decay(int(1.9 * SR), 0.45, 0.002), 0.0, 1.3)
    out += lp(white(length), 1400) * decay(len(out), 0.13, 0.002) * 1.0
    out += bp(white(length), 200, 3200) * decay(len(out), 0.35, 0.01) * 0.35
    out += lp(brown(length), 180) * decay(len(out), 1.2, 0.05) * 1.4
    return out


def howitzer():
    for v in range(2):
        size = 1.0 + 0.12 * v
        near = shot(size, 5.0)
        # The breech opening and the rammer of the autoloader a second later.
        for at, g in ((1.25 + 0.05 * v, 0.22), (1.55 + 0.05 * v, 0.14)):
            place(near, bp(white(0.05), 900, 4200) * decay(int(0.05 * SR), 0.01), at, g)
        near = reverb(near, 5.0, 2.6, damp=4000, mix=0.3)
        save(f'arty_near_{v}', near)
        core = lp(shot(size * 1.15, 8.0), 320) * np.clip(t_axis(8.0) / 0.06, 0, 1)
        far = echoes(core, 8.0, [(0.6, 0.6, 280), (1.4 + 0.1 * v, 0.45, 230), (2.6, 0.3, 180), (3.9, 0.18, 150)])
        save(f'arty_far_{v}', reverb(far, 8.0, 4.5, damp=500, mix=0.5))


def whistle():
    """
    A shell coming in: the rushing hiss of air growing louder and the whistle dropping in pitch as it falls, for about
    two and a half seconds, cut off where it lands (the blast is its own sound).
    """
    for v in range(2):
        length = 2.6
        t = t_axis(length)
        n = len(t)
        f0, f1 = 1650 - 120 * v, 620 - 40 * v
        f = f1 + (f0 - f1) * (1 - t / length) ** 1.6
        phase = 2 * np.pi * np.cumsum(f) / SR
        # Loudness: from far off to right overhead.
        env = (0.06 + 0.94 * (t / length) ** 2.2) * np.clip((length - t) / 0.02, 0, 1)
        tone = (np.sin(phase) + 0.25 * np.sin(2 * phase + 0.4)) * (1 + 0.15 * np.sin(2 * np.pi * 7.5 * t))
        # The hiss follows the whistle's pitch: white noise shaped frame by frame round the sliding pitch.
        fr, tt, z = signal.stft(white(length), fs=SR, nperseg=1024)
        fc = np.interp(tt, t, f)[None, :] * 1.4
        z *= np.exp(-0.5 * (np.log(np.maximum(fr[:, None], 1.0) / fc) / 0.45) ** 2)
        hiss = signal.istft(z, fs=SR, nperseg=1024)[1][:n]
        hiss = np.pad(hiss, (0, n - len(hiss)))
        hiss /= np.max(np.abs(hiss)) + 1e-9
        rumble = lp(brown(length), 260) * (t / length) ** 3
        x = (tone * 0.5 + hiss * 0.9 + rumble * 0.25) * env
        x = reverb(x, length, 0.4, damp=6000, mix=0.12)
        save(f'shell_whistle_{v}', x, peak=0.85)


def sounds_json():
    p = os.path.join(ROOT, 'sounds.json')
    with open(p, encoding='utf-8') as fh:
        d = json.load(fh)
    d['arty_near'] = {'sounds': [{'name': f'airdefense:arty_near_{v}', 'attenuation_distance': 160} for v in range(2)],
                      'subtitle': 'subtitles.airdefense.arty'}
    d['arty_far'] = {'sounds': [{'name': f'airdefense:arty_far_{v}', 'attenuation_distance': 1800} for v in range(2)],
                     'subtitle': 'subtitles.airdefense.arty'}
    d['shell_whistle'] = {'sounds': [{'name': f'airdefense:shell_whistle_{v}', 'attenuation_distance': 96} for v in range(2)],
                          'subtitle': 'subtitles.airdefense.whistle'}
    with open(p, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=2, ensure_ascii=False)
        fh.write('\n')


if __name__ == '__main__':
    howitzer()
    whistle()
    sounds_json()
