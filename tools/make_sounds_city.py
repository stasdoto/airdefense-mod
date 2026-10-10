"""
1.36: the sounds of a town (synthesised, nothing recorded): the hum of traffic by day, cars going by, a horn now and
then, birds in the morning and by day, crickets and an owl at night, the town hall's bell striking the hours.

Run: python3 tools/make_sounds_city.py   (then add the entries to sounds.json: done by this script too)
"""
import json
import os

import numpy as np

import make_sounds as ms
from make_sounds import SR, t_axis, white, brown, lp, hp, bp, reverb, save

rng = np.random.default_rng(1360)
SOUNDS_JSON = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'sounds.json')


def loop_fade(y, seconds=0.25):
    """Cross-fade the start with the end so the clip loops without a click."""
    k = int(seconds * SR)
    out = y[:-k].copy()
    out[:k] = y[:k] * np.linspace(0, 1, k) + y[-k:] * np.linspace(1, 0, k)
    return out


def traffic():
    """A town's traffic from a street or two away: a low rumble, tyres, engines coming and going."""
    length = 8.25
    n = int(length * SR)
    t = np.arange(n) / SR
    rumble = lp(brown(length), 220) * 1.0
    tyres = bp(white(length), 400, 1600) * 0.25
    swell = 0.7 + 0.3 * np.sin(2 * np.pi * t / 8.0) + 0.15 * np.sin(2 * np.pi * t / 2.7 + 1)
    y = (rumble + tyres) * swell
    y = hp(y, 40)
    save('city_traffic', loop_fade(y), peak=0.6, loop=True)


def car_pass(i):
    """One car going by: the engine's note dropping as it passes (Doppler), the tyres' roar swelling and fading."""
    length = 4.0
    t = t_axis(length)
    mid = 1.8 + 0.4 * rng.random()
    speed = 12 + 6 * rng.random()  # m/s
    c = 343.0
    d = 6.0
    x = speed * (t - mid)
    r = np.sqrt(x ** 2 + d ** 2)
    v_rad = speed * x / r
    f0 = (70 + 40 * rng.random()) * c / (c + v_rad)
    phase = 2 * np.pi * np.cumsum(f0) / SR
    engine = np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.3 * np.sin(3 * phase + 0.4) + 0.2 * np.sin(4 * phase)
    tyres = bp(white(length), 300, 2500)
    env = 1.0 / (1 + (x / 5.0) ** 2)
    y = (0.45 * engine + 1.0 * tyres) * env
    y = reverb(y, length, 0.6, damp=3000, mix=0.15)
    save('car_pass_%d' % i, y, peak=0.7)


def horn(i):
    """A car's horn, a short toot or two (two notes a third apart)."""
    length = 1.2
    t = t_axis(length)
    f = 400 + 60 * i
    tone = np.zeros_like(t)
    for ratio in (1.0, 1.26):
        ph = 2 * np.pi * f * ratio * t
        tone += np.sign(np.sin(ph)) * 0.5 + np.sin(2 * ph) * 0.2
    tone = bp(tone, 300, 3500)
    gate = np.zeros_like(t)
    beeps = [(0.0, 0.35)] if i == 0 else [(0.0, 0.18), (0.28, 0.5)]
    for a, b in beeps:
        gate += ((t >= a) & (t < b)).astype(float)
    gate = lp(gate, 60)
    y = reverb(tone * gate, length, 0.7, damp=4000, mix=0.3)
    save('car_horn_%d' % i, y, peak=0.6)


def birds(i):
    """A small bird's song: quick chirps, each a fast sweep with a trill, a pause, again."""
    length = 2.6
    y = np.zeros(int(length * SR))
    at = 0.05
    while at < length - 0.3:
        dur = 0.05 + 0.08 * rng.random()
        tt = t_axis(dur)
        f_start = 2500 + 2500 * rng.random()
        f_end = f_start * (0.6 + 0.8 * rng.random())
        f = np.linspace(f_start, f_end, len(tt)) * (1 + 0.04 * np.sin(2 * np.pi * (60 + 40 * rng.random()) * tt))
        chirp = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * np.linspace(0, 1, len(tt))) ** 2
        ms.place(y, chirp, at, 0.5 + 0.5 * rng.random())
        at += dur + (0.03 + 0.06 * rng.random() if rng.random() < 0.7 else 0.25 + 0.3 * rng.random())
    y = reverb(y, length, 0.5, damp=7000, mix=0.2)
    save('birds_%d' % i, y, peak=0.5)


def crickets():
    """Crickets in the grass at night: several, each chirping its own beat (a 4-5 kHz pulse train)."""
    length = 6.0
    n = int(length * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for k in range(5):
        f = 4200 + 600 * rng.random()
        rate = 2.0 + 1.5 * rng.random()
        # A whole number of chirps in the loop.
        rate = round(rate * length) / length
        phase0 = rng.random()
        chirp_phase = (t * rate + phase0) % 1.0
        on = (chirp_phase < 0.18).astype(float)
        pulses = (np.sin(2 * np.pi * 45 * t) > 0).astype(float)
        tone = np.sin(2 * np.pi * f * t)
        y += tone * lp(on * pulses, 400) * (0.4 + 0.6 * rng.random())
    y += bp(white(length), 3000, 8000) * 0.02
    save('crickets', loop_fade(y, 0.1), peak=0.4, loop=True)


def owl():
    """An owl far off: hoo... hoo-hoo-hoo."""
    length = 3.0
    y = np.zeros(int(length * SR))
    for at, dur in ((0.0, 0.45), (0.9, 0.18), (1.15, 0.18), (1.4, 0.5)):
        tt = t_axis(dur)
        f = 380 * (1 + 0.03 * np.sin(2 * np.pi * 6 * tt)) * np.linspace(1.03, 0.97, len(tt))
        h = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * np.linspace(0, 1, len(tt))) ** 1.5
        ms.place(y, h + 0.05 * bp(white(dur), 300, 1200), at, 1.0)
    y = reverb(y, length, 1.2, damp=2500, mix=0.35)
    save('owl', y, peak=0.5)


def bell():
    """The town hall's bell: one stroke (the client strikes it as many times as the hour)."""
    length = 4.5
    t = t_axis(length)
    f = 330.0
    y = np.zeros_like(t)
    for ratio, amp, tau in ((0.5, 0.6, 2.5), (1.0, 1.0, 2.0), (1.19, 0.5, 1.6), (1.5, 0.45, 1.3), (2.0, 0.4, 1.0), (2.51, 0.25, 0.7),
                            (2.66, 0.2, 0.6), (3.01, 0.15, 0.5), (4.1, 0.1, 0.3)):
        y += amp * np.sin(2 * np.pi * f * ratio * t + rng.random() * 6) * np.exp(-t / tau)
    strike = bp(white(length), 1500, 6000) * np.exp(-t / 0.01) * 0.6
    y = reverb(y + strike, length, 1.5, damp=3500, mix=0.3)
    save('town_bell', y, peak=0.8)


def service_siren():
    """1.38: an emergency vehicle's siren - a wail rising and falling (three seconds, played again while it drives)."""
    length = 3.0
    t = t_axis(length)
    f = 700 + 650 * (0.5 - 0.5 * np.cos(2 * np.pi * t / length))
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.35 * np.sign(np.sin(ph)) + 0.2 * np.sin(2 * ph)
    tone = bp(tone, 400, 4500)
    y = np.tanh(1.6 * tone)
    y = reverb(y, length, 0.6, damp=4000, mix=0.2)
    save('service_siren', y, peak=0.7)


def horn_chord(freqs, beeps, length, name, peak, bright=0.4):
    """A train's horn: reeds sounding a chord (rich in harmonics), on for the given (start, end) blasts, a long echo."""
    t = t_axis(length)
    tone = np.zeros_like(t)
    for f in freqs:
        ph = 2 * np.pi * f * t * (1 + 0.002 * np.sin(2 * np.pi * 5 * t))
        tone += np.sin(ph) + bright * np.sign(np.sin(ph)) * 0.5 + 0.3 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)
    gate = np.zeros_like(t)
    for a, b in beeps:
        gate += ((t >= a) & (t < b)).astype(float)
    gate = lp(np.clip(gate, 0, 1), 25)
    y = bp(tone, 150, 5000) * gate
    y = np.tanh(1.3 * y / np.max(np.abs(y)))
    y = reverb(y, length, 1.6, damp=2500, mix=0.35)
    save(name, y, peak=peak)


def train_horn():
    """1.39: the electric train's horn - a high two-note call, short then long."""
    horn_chord((660, 830), [(0.0, 0.35), (0.55, 1.6)], 3.2, 'train_horn', 0.75, bright=0.6)


def loco_horn():
    """The diesel's horn: a low, deep chord, one long blast."""
    horn_chord((233, 294, 349), [(0.0, 1.9)], 3.6, 'loco_horn', 0.85, bright=0.3)


def train_clack(i):
    """The wheels over a rail joint: a bogie's two axles, ta-dam, over the rumble of the train."""
    length = 0.9
    t = t_axis(length)
    y = lp(brown(length), 300) * 0.35 * np.exp(-t / 0.6)
    for at, amp in ((0.02, 1.0), (0.17 + 0.02 * i, 0.85)):
        tt = t_axis(0.12)
        hit = bp(white(0.12), 600, 4000) * np.exp(-tt / 0.012) + np.sin(2 * np.pi * (180 + 30 * i) * tt) * np.exp(-tt / 0.03) * 0.8
        ms.place(y, hit, at, amp)
    y = reverb(y, length, 0.5, damp=3000, mix=0.2)
    save('train_clack_%d' % i, y, peak=0.7)


def register():
    with open(SOUNDS_JSON, encoding='utf-8') as fh:
        d = json.load(fh)
    entries = {
        'city_traffic': (['city_traffic'], 48, 'city_traffic'),
        'city_car': (['car_pass_0', 'car_pass_1', 'car_pass_2'], 48, 'city_car'),
        'city_horn': (['car_horn_0', 'car_horn_1'], 64, 'city_horn'),
        'city_birds': (['birds_0', 'birds_1', 'birds_2'], 32, 'city_birds'),
        'city_crickets': (['crickets'], 24, 'city_crickets'),
        'city_owl': (['owl'], 64, 'city_owl'),
        'town_bell': (['town_bell'], 160, 'town_bell'),
        'service_siren': (['service_siren'], 160, 'service_siren'),
        'train_horn': (['train_horn'], 200, 'train_horn'),
        'loco_horn': (['loco_horn'], 220, 'train_horn'),
        'train_clack': (['train_clack_0', 'train_clack_1'], 64, 'train_clack'),
    }
    for key, (files, dist, sub) in entries.items():
        d[key] = {'sounds': [{'name': 'airdefense:' + f, 'attenuation_distance': dist} for f in files], 'subtitle': 'subtitles.airdefense.' + sub}
    with open(SOUNDS_JSON, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=2, ensure_ascii=False)
        fh.write('\n')


if __name__ == '__main__':
    traffic()
    for i in range(3):
        car_pass(i)
    for i in range(2):
        horn(i)
    for i in range(3):
        birds(i)
    crickets()
    owl()
    bell()
    service_siren()
    train_horn()
    loco_horn()
    for i in range(2):
        train_clack(i)
    register()
