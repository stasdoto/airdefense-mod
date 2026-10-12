"""
1.50 "A living town": more of a town's sounds (synthesised, nothing recorded) - a dog barking in a yard, hammering on a
building site, music from an open window in the evening, children shouting in a playground, a tram's bell and its
wheels on the rails, a bus pulling away (its doors hissing shut, the diesel).

Run: python3 tools/make_sounds_life.py   (adds its entries to sounds.json too). Only writes its own files.
"""
import json
import os

import numpy as np

import make_sounds as ms
from make_sounds import SR, t_axis, white, brown, lp, hp, bp, reverb, save

rng = np.random.default_rng(1500)
SOUNDS_JSON = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'sounds.json')


def bark(i):
    """A dog's bark or two: a harsh voiced burst (a falling pitch with formants), a pause, again."""
    length = 1.6
    y = np.zeros(int(length * SR))
    f0 = 330 + 90 * i
    at = 0.05
    for k in range(1 + i):
        dur = 0.16 + 0.04 * rng.random()
        tt = t_axis(dur)
        f = f0 * np.linspace(1.15, 0.8, len(tt))
        ph = 2 * np.pi * np.cumsum(f) / SR
        voice = np.sign(np.sin(ph)) * 0.6 + np.sin(2 * ph) * 0.3 + 0.25 * white(dur)
        voice = bp(voice, 400, 2600) + 0.5 * bp(voice, 900, 1400)
        env = np.minimum(1, tt / 0.012) * np.exp(-tt / (dur * 0.45))
        ms.place(y, voice * env, at, 1.0)
        at += dur + 0.12 + 0.06 * rng.random()
    y = reverb(y, length, 0.8, damp=3500, mix=0.3)
    save('dog_bark_%d' % i, y, peak=0.6)


def hammer(i):
    """Hammering on a building site: metal on metal, a few blows, an echo off the walls."""
    length = 2.4
    y = np.zeros(int(length * SR))
    at = 0.05
    n = 3 + i
    for k in range(n):
        dur = 0.25
        tt = t_axis(dur)
        ring = sum(np.sin(2 * np.pi * f * tt) * a for f, a in ((1250 + 90 * i, 1.0), (2900, 0.5), (4700, 0.3)))
        hit = (ring * np.exp(-tt / 0.04) + bp(white(dur), 1500, 6000) * np.exp(-tt / 0.006))
        ms.place(y, hit, at, 0.8 + 0.2 * rng.random())
        at += 0.38 + 0.08 * rng.random()
    y = reverb(y, length, 1.0, damp=4000, mix=0.35)
    save('site_hammer_%d' % i, y, peak=0.6)


def radio():
    """Music from an open window: a little tune on a cheap radio (a melody over chords, squeezed into a narrow band)."""
    length = 8.0
    t = t_axis(length)
    beat = 0.5
    chords = [(220, 277, 330), (196, 247, 294), (175, 220, 262), (196, 247, 294)]
    melody = [440, 494, 523, 494, 440, 392, 349, 392, 440, 523, 587, 523, 494, 440, 392, 440]
    y = np.zeros_like(t)
    for k, f in enumerate(melody):
        a = k * beat
        tt = t_axis(beat)
        note = (np.sin(2 * np.pi * f * tt) + 0.4 * np.sin(4 * np.pi * f * tt)) * np.exp(-tt / 0.3)
        ms.place(y, note, a, 0.5)
    for k in range(int(length / 2)):
        c = chords[k % len(chords)]
        tt = t_axis(2.0)
        pad = sum(np.sin(2 * np.pi * f * tt) for f in c) * (0.6 + 0.4 * np.sin(np.pi * tt / 2.0))
        ms.place(y, pad, k * 2.0, 0.18)
    for k in range(int(length / beat)):
        tt = t_axis(0.08)
        ms.place(y, bp(white(0.08), 3000, 8000) * np.exp(-tt / 0.02), k * beat, 0.15)
    y = bp(y, 300, 3000) + 0.01 * white(length)
    y = reverb(y, length, 0.6, damp=3000, mix=0.3)
    save('window_radio', y, peak=0.45, loop=True)


def kids(i):
    """Children shouting at play, far off: quick high voiced calls rising and falling, some laughter."""
    length = 2.8
    y = np.zeros(int(length * SR))
    at = 0.05
    while at < length - 0.4:
        dur = 0.15 + 0.3 * rng.random()
        tt = t_axis(dur)
        f0 = 380 + 200 * rng.random()
        f = f0 * (1 + 0.25 * np.sin(np.pi * tt / dur) * (1 if rng.random() < 0.5 else -1))
        ph = 2 * np.pi * np.cumsum(f) / SR
        v = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph)
        v = bp(v, 300, 3500) * np.sin(np.pi * tt / dur) ** 0.6
        if rng.random() < 0.3:
            # A laugh: the voice chopped into quick ha-ha-ha.
            v *= (np.sin(2 * np.pi * 7 * tt) > 0).astype(float)
        ms.place(y, v, at, 0.4 + 0.6 * rng.random())
        at += dur + 0.05 + 0.3 * rng.random()
    y = reverb(y, length, 1.0, damp=4500, mix=0.4)
    save('kids_play_%d' % i, y, peak=0.4)


def tram_bell():
    """A tram's bell: ding-ding."""
    length = 1.6
    y = np.zeros(int(length * SR))
    for at in (0.0, 0.32):
        tt = t_axis(1.2)
        ring = sum(np.sin(2 * np.pi * f * tt) * a * np.exp(-tt / d) for f, a, d in ((1180, 1.0, 0.5), (2830, 0.5, 0.3), (4400, 0.25, 0.15)))
        ms.place(y, ring, at, 1.0)
    y = reverb(y, length, 0.8, damp=5000, mix=0.25)
    save('tram_bell', y, peak=0.6)


def tram_pass():
    """A tram going by: a low hum of its motors, the wheels' clack at the rail joints, a squeal on the curve."""
    length = 5.0
    t = t_axis(length)
    mid = 2.4
    env = 1.0 / (1 + ((t - mid) / 1.0) ** 2)
    hum = np.sin(2 * np.pi * 110 * t) * 0.3 + lp(brown(length), 300)
    y = hum * env
    for k in range(8):
        at = mid - 1.4 + k * 0.38
        tt = t_axis(0.1)
        ms.place(y, bp(white(0.1), 200, 2000) * np.exp(-tt / 0.02) * (1 / (1 + ((at - mid) / 1.0) ** 2)), at, 1.2)
    squeal = np.sin(2 * np.pi * (3200 + 80 * np.sin(2 * np.pi * 3 * t)) * t) * 0.08 * np.exp(-((t - mid - 0.6) / 0.6) ** 2)
    y = y + squeal
    y = reverb(y, length, 0.8, damp=3500, mix=0.2)
    save('tram_pass', y, peak=0.6)


def bus_go():
    """A bus pulling away from a stop: the doors' hiss, the diesel revving up."""
    length = 4.0
    t = t_axis(length)
    y = np.zeros_like(t)
    tt = t_axis(0.6)
    ms.place(y, bp(white(0.6), 1500, 7000) * np.exp(-tt / 0.25), 0.0, 0.7)
    rpm = np.clip((t - 0.7) / 2.0, 0, 1)
    f = 32 + 40 * rpm
    ph = 2 * np.pi * np.cumsum(f) / SR
    diesel = (np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.4 * np.sin(3 * ph + 0.5)) * (t > 0.6)
    diesel += 0.6 * lp(brown(length), 200) * (t > 0.6)
    y += diesel * (0.3 + 0.7 * rpm) * np.exp(-np.maximum(0, t - 2.8) / 0.8)
    y = reverb(y, length, 0.7, damp=3000, mix=0.2)
    save('bus_go', y, peak=0.6)


def register():
    with open(SOUNDS_JSON, encoding='utf-8') as fh:
        d = json.load(fh)
    entries = {
        'dog_bark': (['dog_bark_0', 'dog_bark_1'], 48, 'dog_bark'),
        'site_hammer': (['site_hammer_0', 'site_hammer_1'], 64, 'site_hammer'),
        'window_radio': (['window_radio'], 20, 'window_radio'),
        'kids_play': (['kids_play_0', 'kids_play_1'], 40, 'kids_play'),
        'tram_bell': (['tram_bell'], 64, 'tram_bell'),
        'tram_pass': (['tram_pass'], 64, 'tram_pass'),
        'bus_go': (['bus_go'], 48, 'bus_go'),
    }
    for key, (files, dist, sub) in entries.items():
        d[key] = {'sounds': [{'name': 'airdefense:' + f, 'attenuation_distance': dist} for f in files], 'subtitle': 'subtitles.airdefense.' + sub}
    with open(SOUNDS_JSON, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=2, ensure_ascii=False)
        fh.write('\n')


if __name__ == '__main__':
    for i in range(2):
        bark(i)
        hammer(i)
        kids(i)
    radio()
    tram_bell()
    tram_pass()
    bus_go()
    register()
