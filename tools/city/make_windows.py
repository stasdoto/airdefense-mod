#!/usr/bin/env python3
"""
1.49: the towns' windows, each flat its own: a frame and a transom, tinted glass with the sky in it, and behind it
what the people hung there - white tulle, beige drapes, red or green curtains, blinds, a flowered curtain; in wartime
(1.50) paper tape crossed on the glass, or the glass cracked. Lit at night in three tones: a warm lamp, a cold one, the
blue of a television. Writes the textures (block/city/window_*), the models (block/city/window_*) and the blockstate of
city_window (lit x look x tone, the pane's four sides). python3 tools/city/make_windows.py
"""
import json
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense')
TEX = os.path.join(ASSETS, 'textures', 'block', 'city')
MODELS = os.path.join(ASSETS, 'models', 'block', 'city')
STATES = os.path.join(ASSETS, 'blockstates')
STREET_MODELS = os.path.join(ASSETS, 'models', 'block', 'street')

LOOKS = 8  # 0 bare, 1 tulle, 2 beige drapes, 3 red, 4 green, 5 blinds, 6 taped, 7 cracked
TONES = [(250, 204, 120), (226, 236, 250), (120, 160, 250)]  # warm lamp, cold light, television
FRAME = (226, 224, 216)
r = np.random.default_rng(1490)


def glass(lit=None):
    a = np.zeros((16, 16, 4))
    if lit is None:
        a[..., :3] = (70, 92, 112)
        a[..., 3] = 150
        # The sky in it: a light diagonal.
        for k in range(16):
            for w in range(3):
                y, x = 15 - k, k + w - 4
                if 0 <= x < 16:
                    a[y, x, :3] = (150, 176, 196)
                    a[y, x, 3] = 165
    else:
        a[..., :3] = lit
        a[..., 3] = 215
    return a


def curtain(a, look, lit):
    k = 0.55 if lit is not None else 1.0
    light = np.array(lit if lit is not None else (255, 255, 255), float) / 255

    def cloth(y0, y1, x0, x1, rgb, alpha=245, folds=True):
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = np.array(rgb, float)
                if folds and x % 2 == 0:
                    c *= 0.86
                if lit is not None:
                    c = c * light * 1.05
                a[y, x, :3] = c
                a[y, x, 3] = alpha
    if look == 1:  # tulle: white, see-through
        for y in range(1, 15):
            for x in range(1, 15):
                if (x + y) % 2 == 0 or y > 11:
                    c = np.array((236, 236, 230), float) * (light if lit is not None else 1)
                    a[y, x, :3] = c
                    a[y, x, 3] = 200
    elif look == 2:
        cloth(1, 15, 1, 5, (206, 186, 150))
        cloth(1, 15, 11, 15, (206, 186, 150))
    elif look == 3:
        cloth(1, 15, 1, 6, (150, 52, 46))
        cloth(1, 15, 12, 15, (150, 52, 46))
        cloth(1, 3, 1, 15, (130, 42, 38), folds=False)
    elif look == 4:
        cloth(1, 15, 1, 4, (70, 110, 84))
        cloth(1, 15, 10, 15, (70, 110, 84))
    elif look == 5:  # blinds, half down
        for y in range(1, 10):
            for x in range(1, 15):
                c = np.array((214, 212, 204) if y % 2 else (176, 174, 166), float)
                if lit is not None:
                    c = c * light
                a[y, x, :3] = c
                a[y, x, 3] = 250
    elif look == 6:  # paper tape crossed on the glass (wartime)
        for t in range(1, 15):
            for y, x in ((t, t), (t, 15 - t)):
                a[y, x, :3] = (232, 226, 206)
                a[y, x, 3] = 255
                if x + 1 < 15:
                    a[y, x + 1, :3] = (232, 226, 206)
                    a[y, x + 1, 3] = 255
    elif look == 7:  # cracked: a star of cracks, a corner gone
        cx, cy = 10, 6
        for ang in np.linspace(0, 2 * np.pi, 7, endpoint=False):
            for t in range(1, 9):
                x = int(round(cx + np.cos(ang) * t + r.normal(0, 0.3)))
                y = int(round(cy + np.sin(ang) * t + r.normal(0, 0.3)))
                if 1 <= x < 15 and 1 <= y < 15:
                    a[y, x, :3] = (220, 228, 232)
                    a[y, x, 3] = 255
        for y in range(1, 5):
            for x in range(1, 6 - y):
                a[y, x, 3] = 0


def frame(a, lit):
    f = np.array(FRAME, float) * (0.8 if lit is None else 1)
    a[0, :, :3] = f
    a[15, :, :3] = f
    a[:, 0, :3] = f
    a[:, 15, :3] = f
    a[0, :, 3] = a[15, :, 3] = a[:, 0, 3] = a[:, 15, 3] = 255
    # The transom (the little top window that opens).
    a[5, 1:15, :3] = f
    a[5, 1:15, 3] = 255


def save(name, a):
    os.makedirs(TEX, exist_ok=True)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(TEX, name + '.png'))


def textures():
    for look in range(LOOKS):
        a = glass()
        curtain(a, look, None)
        frame(a, None)
        save('window_%d' % look, a)
        for t, tone in enumerate(TONES):
            b = glass(tone)
            curtain(b, look, tone)
            frame(b, tone)
            if t == 2:
                # 1.50: a television's light flickers (four frames, brighter and dimmer, a shift of blue).
                frames = []
                for k, f in enumerate((1.0, 0.72, 0.9, 0.6, 0.95, 0.8)):
                    c = b.copy()
                    inner = c[1:15, 1:15, :3]
                    c[1:15, 1:15, :3] = inner * f + (0 if k % 2 else 6)
                    frames.append(c)
                save('window_%d_lit_%d' % (look, t), np.concatenate(frames, axis=0))
                with open(os.path.join(TEX, 'window_%d_lit_%d.png.mcmeta' % (look, t)), 'w') as fh:
                    json.dump({'animation': {'frametime': 4}}, fh)
            else:
                save('window_%d_lit_%d' % (look, t), b)
    e = np.zeros((16, 16, 4))
    e[..., :3] = FRAME
    e[..., 3] = 255
    save('window_edge', e)


PARTS = ['post', 'side', 'side_alt', 'noside', 'noside_alt']


def models():
    os.makedirs(MODELS, exist_ok=True)
    for part in PARTS:
        with open(os.path.join(STREET_MODELS, 'window_lit_' + part + '.json')) as fh:
            lit = json.load(fh)
        for look in range(LOOKS):
            dark = json.loads(json.dumps(lit))
            dark.pop('ambientocclusion', None)
            for el in dark['elements']:
                el.pop('light_emission', None)
            dark['textures'] = {'pane': 'airdefense:block/city/window_%d' % look, 'edge': 'airdefense:block/city/window_edge',
                                'particle': 'airdefense:block/city/window_%d' % look}
            with open(os.path.join(MODELS, 'window_%d_%s.json' % (look, part)), 'w') as fh:
                json.dump(dark, fh)
            for t in range(len(TONES)):
                m = json.loads(json.dumps(lit))
                m['textures'] = {'pane': 'airdefense:block/city/window_%d_lit_%d' % (look, t), 'edge': 'airdefense:block/city/window_edge',
                                 'particle': 'airdefense:block/city/window_%d_lit_%d' % (look, t)}
                with open(os.path.join(MODELS, 'window_%d_lit_%d_%s.json' % (look, t, part)), 'w') as fh:
                    json.dump(m, fh)


def blockstate():
    parts = []
    sides = [('north', 'side', 0, 'noside', 'noside', 0), ('east', 'side', 90, 'noside_alt', 'noside_alt', 0),
             ('south', 'side_alt', 0, 'noside_alt', 'noside_alt', 90), ('west', 'side_alt', 90, 'noside', 'noside', 270)]

    def add(when, model, y=0):
        apply = {'model': model}
        if y:
            apply['y'] = y
        parts.append({'when': when, 'apply': apply})

    for look in range(LOOKS):
        combos = [({'lit': 'false', 'look': str(look)}, 'airdefense:block/city/window_%d_' % look)]
        for t in range(len(TONES)):
            combos.append(({'lit': 'true', 'look': str(look), 'tone': str(t)}, 'airdefense:block/city/window_%d_lit_%d_' % (look, t)))
        for when, prefix in combos:
            add(dict(when), prefix + 'post')
            for side, on, yon, _, off, yoff in sides:
                w = dict(when)
                w[side] = 'true'
                add(w, prefix + on, yon)
                w = dict(when)
                w[side] = 'false'
                add(w, prefix + off, yoff)
    with open(os.path.join(STATES, 'city_window.json'), 'w') as fh:
        json.dump({'multipart': parts}, fh)
    return len(parts)


if __name__ == '__main__':
    textures()
    models()
    print('parts', blockstate())
