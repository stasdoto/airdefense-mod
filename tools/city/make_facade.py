#!/usr/bin/env python3
"""
1.49: what people and years put on a town's walls: air conditioners by the windows, satellite dishes, drainpipes down
the corners (a funnel at the top, an elbow at the foot), shop signs over the ground floors (Russian ones for the Soviet
towns, others elsewhere), graffiti, notices by the doors, flower boxes under windows, washing on the balconies and the
junk everybody keeps there, vents and old TV aerials on the roofs, a big rubbish container for the yards.

The models face north (their back against the wall to the south of them, z = 16); the blockstates turn them.
python3 tools/city/make_facade.py
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'street'))
import make_street as ms  # noqa: E402
from make_street import box, model, save, paint, ROOT  # noqa: E402

rng = np.random.default_rng(1491)
STATES = os.path.join(ROOT, 'blockstates')
ITEMS = os.path.join(ROOT, 'items')

# A 3x5 font with what the signs need (Latin and Cyrillic).
F = {
    'A': '010101111101101', 'B': '110101110101110', 'C': '011100100100011', 'D': '110101101101110', 'E': '111100110100111',
    'F': '111100110100100', 'G': '011100101101011', 'H': '101101111101101', 'I': '111010010010111', 'K': '101110100110101',
    'L': '100100100100111', 'M': '101111111101101', 'N': '101111111111101', 'O': '010101101101010', 'P': '110101110100100',
    'R': '110101110101101', 'S': '011100010001110', 'T': '111010010010010', 'U': '101101101101111', 'Y': '101101010010010',
    'Z': '111001010100111', 'X': '101101010101101', 'V': '101101101101010',
    'А': '010101111101101', 'Б': '111100110101110', 'В': '110101110101110', 'Г': '111100100100100', 'Д': '011011011111101',
    'Е': '111100110100111', 'И': '101101111111101', 'К': '101110100110101', 'Л': '011101101101101', 'М': '101111111101101',
    'Н': '101101111101101', 'О': '010101101101010', 'П': '111101101101101', 'Р': '110101110100100', 'С': '011100100100011',
    'Т': '111010010010010', 'У': '101101011001110', 'Ф': '010111111010010', 'Х': '101101010101101', 'Ц': '101101101111001',
    'Ы': '101101111111111', 'Ч': '101101011001001', ' ': '000000000000000',
}


WIDE = {'Ы': '1000110001111011010111101', 'Ш': '1010110101101011010111111', 'Ж': '1010110101011101010110101'}


def glyph(ch):
    if ch in WIDE:
        return WIDE[ch], 5
    return F[ch], 3


def text(a, s, x, y, rgb, scale=1):
    for ch in s:
        g, w = glyph(ch)
        for j in range(5):
            for i in range(w):
                if g[j * w + i] == '1':
                    a[y + j * scale:y + (j + 1) * scale, x + i * scale:x + (i + 1) * scale, :3] = rgb
                    a[y + j * scale:y + (j + 1) * scale, x + i * scale:x + (i + 1) * scale, 3] = 255
        x += (w + 1) * scale


def width(s, scale):
    return sum(glyph(c)[1] + 1 for c in s) * scale - scale


SIGNS = [
    ('ПРОДУКТЫ', (34, 70, 150), (240, 240, 236), None),
    ('АПТЕКА', (236, 236, 230), (30, 140, 70), 'cross'),
    ('ХЛЕБ', (120, 70, 36), (250, 210, 110), None),
    ('КАФЕ', (170, 40, 36), (250, 244, 230), None),
    ('ЦВЕТЫ', (40, 120, 70), (250, 250, 240), None),
    ('ОПТИКА', (236, 236, 230), (40, 70, 160), None),
    ('CAFE', (30, 70, 50), (236, 224, 190), None),
    ('PHARMACY', (30, 130, 70), (250, 250, 250), 'cross'),
    ('BAKERY', (110, 66, 40), (250, 226, 170), None),
    ('PIZZA', (190, 40, 30), (250, 250, 240), None),
    ('DINER', (200, 40, 50), (250, 236, 120), None),
    ('BOOKS', (30, 40, 80), (236, 220, 160), None),
]


def sign_tex(k):
    word, bg, fg, mark = SIGNS[k]
    a = np.zeros((16, 48, 4))
    a[2:14, :, :3] = bg
    a[2:14, :, 3] = 255
    a[2:14, :, :3] += rng.normal(0, 3, (12, 48, 1))
    a[2, :, :3] *= 0.7
    a[13, :, :3] *= 0.7
    a[2:14, 0, :3] *= 0.7
    a[2:14, 47, :3] *= 0.7
    scale = 2 if width(word, 2) <= 40 and mark is None else 1
    room = 48 - (12 if mark else 0)
    x0 = (12 if mark else 0) + (room - width(word, scale)) // 2
    y0 = 8 - (5 * scale) // 2
    text(a, word, x0, y0, fg, scale)
    if mark == 'cross':
        a[4:12, 6:8, :3] = (40, 170, 80)
        a[7:9, 3:11, :3] = (40, 170, 80)
    # Rust streaks from the fixings, dirt along the bottom.
    for x in (3, 44):
        a[13:16, x, :3] = (110, 70, 40)
        a[13:16, x, 3] = 255
    a[11:14, :, :3] *= 0.92
    return a


def graffiti_tex(k):
    r = np.random.default_rng(700 + k)
    a = np.zeros((16, 16, 4))
    cols = [(220, 40, 60), (40, 180, 220), (250, 210, 40), (130, 220, 60), (240, 240, 240), (200, 80, 220), (30, 30, 30)]
    fill = cols[k % len(cols)]
    line = cols[(k + 3) % len(cols)]
    if k < 4:
        # A tag: three or four fat letters with an outline.
        word = ['ZEK', 'RAT', 'KOT', 'NIX'][k]
        tmp = np.zeros((16, 16, 4))
        text(tmp, word, 2, 5, fill, 1)
        m = tmp[..., 3] > 0
        fat = m | np.roll(m, 1, 1) | np.roll(m, 1, 0)
        out = fat | np.roll(fat, 1, 1) | np.roll(fat, -1, 1) | np.roll(fat, 1, 0) | np.roll(fat, -1, 0)
        a[out, :3] = line
        a[out, 3] = 255
        a[fat, :3] = fill
        # Drips.
        for _ in range(3):
            x = int(r.integers(3, 13))
            ys = np.where(fat[:, x])[0]
            if len(ys):
                for y in range(ys.max() + 1, min(16, ys.max() + int(r.integers(2, 5)))):
                    a[y, x, :3] = fill
                    a[y, x, 3] = 255
    else:
        # Scribbles and a heart / an arrow.
        x, y = 2.0, 8.0
        for t in range(60):
            x += math.cos(t * 0.7 + k) * 0.8 + 0.18
            y += math.sin(t * 1.3) * 0.9
            xi, yi = int(x) % 16, int(np.clip(y, 1, 14))
            a[yi, xi, :3] = fill
            a[yi, xi, 3] = 255
        heart = ['0101', '1111', '0110']
        for j, row in enumerate(heart):
            for i, c in enumerate(row):
                if c == '1':
                    a[2 + j, 10 + i, :3] = (220, 40, 60)
                    a[2 + j, 10 + i, 3] = 255
    return a


def notice_tex(k):
    r = np.random.default_rng(800 + k)
    a = np.zeros((16, 16, 4))
    for n in range(3 + k):
        w, h = int(r.integers(4, 7)), int(r.integers(5, 8))
        x0, y0 = int(r.integers(0, 16 - w)), int(r.integers(0, 16 - h))
        paper = np.array((236, 232, 216) if r.random() < 0.7 else (250, 230, 120), float)
        a[y0:y0 + h, x0:x0 + w, :3] = paper * (0.9 + 0.1 * r.random())
        a[y0:y0 + h, x0:x0 + w, 3] = 255
        for yy in range(y0 + 1, y0 + h - 2, 2):
            a[yy, x0 + 1:x0 + w - 1, :3] = (90, 90, 96)
        # Tear-off strips at the bottom.
        for xx in range(x0, x0 + w, 2):
            a[y0 + h - 1, xx, 3] = 0
    return a


def laundry_tex(k):
    a = np.zeros((16, 16, 4))
    a[1, :, :3] = (60, 60, 60)
    a[1, :, 3] = 255
    pal = [[(236, 236, 236), (90, 130, 200), (220, 80, 80)], [(240, 220, 120), (236, 236, 236), (120, 180, 120)],
           [(236, 236, 236), (236, 236, 236), (200, 140, 180)]][k]
    x = 0
    for i, c in enumerate(pal * 2):
        w = 4 if k != 2 else 7
        h = 8 if k != 2 else 12
        if x + w > 16:
            break
        a[2:2 + h, x:x + w - 1, :3] = c
        a[2:2 + h, x:x + w - 1, 3] = 255
        a[2:2 + h, x:x + w - 1, :3] *= np.linspace(1, 0.85, h)[:, None, None]
        if k == 0:
            # A shirt's sleeves.
            a[2:5, max(0, x - 1), :3] = c
            a[2:5, max(0, x - 1), 3] = 255
        x += w + 1
    return a


def make_textures():
    save('ac_front', ac_front())
    save('ac_side', paint((214, 214, 208), 3))
    save('dish', dish_face())
    save('pipe', pipe_tex())
    for k in range(len(SIGNS)):
        save('shop_sign_%d' % k, sign_tex(k))
    for k in range(6):
        save('graffiti_%d' % k, graffiti_tex(k))
    for k in range(3):
        save('notice_%d' % k, notice_tex(k))
        save('laundry_%d' % k, laundry_tex(k))
    save('planter_box', ms.wood((120, 80, 50)))
    save('flowers', flowers())
    save('crate', ms.wood((150, 116, 76)))
    save('jar', jars())
    save('ski', paint((180, 40, 40), 4))
    save('tyre', paint((30, 30, 32), 3))
    save('vent', paint((150, 152, 150), 6))
    save('aerial', paint((110, 112, 116), 5))
    save('dumpster', dumpster())
    save('dumpster_lid', paint((50, 74, 50), 4))


def ac_front():
    a = paint((222, 222, 216), 3)
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.sqrt((xx - 9.5) ** 2 + (yy - 8) ** 2)
    a[d < 5.5, :3] = (90, 92, 96)
    for ang in range(0, 360, 60):
        for t in range(6):
            x = int(round(9.5 + math.cos(math.radians(ang)) * t))
            y = int(round(8 + math.sin(math.radians(ang)) * t))
            a[y, x, :3] = (150, 150, 150)
    a[2:14, 1:3, :3] = (180, 180, 176)
    a[13:16, 2:14, :3] *= 0.85  # dirt where the water drips
    return a


def dish_face():
    a = paint((228, 228, 224), 4)
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2)
    a[d > 7.6, 3] = 0
    a[(d > 6.8) & (d <= 7.6), :3] = (180, 180, 176)
    a[5:8, 9:13, :3] = (60, 70, 140)  # the provider's logo
    return a


def pipe_tex():
    a = paint((150, 150, 148), 4)
    for x in range(16):
        a[:, x, :3] *= 0.8 + 0.25 * math.sin(x / 16 * math.pi)
    for y in (3, 11):
        a[y, :, :3] *= 0.7
    rust = np.array((128, 80, 50), float)
    for _ in range(8):
        y, x = rng.integers(0, 16, 2)
        a[y, x, :3] = a[y, x, :3] * 0.4 + rust * 0.6
    return a


def flowers():
    a = np.zeros((16, 16, 4))
    for _ in range(40):
        y, x = rng.integers(4, 16, 2)
        a[y, x, :3] = (60, 120, 50) if rng.random() < 0.6 else [(220, 60, 60), (250, 210, 60), (230, 120, 200), (240, 240, 240)][rng.integers(0, 4)]
        a[y, x, 3] = 255
    return a


def jars():
    a = np.zeros((16, 16, 4))
    for i, x0 in enumerate((1, 6, 11)):
        a[5:15, x0:x0 + 4, :3] = [(200, 90, 60), (210, 190, 90), (120, 160, 90)][i]
        a[5:15, x0:x0 + 4, 3] = 230
        a[4, x0:x0 + 4, :3] = (200, 180, 60)
        a[4, x0:x0 + 4, 3] = 255
    return a


def dumpster():
    a = paint((64, 96, 64), 5)
    for x in range(0, 16, 4):
        a[:, x, :3] *= 0.8
    a[0:2, :, :3] *= 0.8
    for _ in range(10):
        y, x = rng.integers(0, 16, 2)
        a[y, x, :3] = (120, 80, 50)
    return a


# --------------------------------------------------------------------------------------------------------------------
# Models (north-facing: the wall is behind, at z = 16)

def models():
    ac = [box((2, 4, 9), (14, 13, 16), {'north': 'ac_front', 'east': 'ac_side', 'west': 'ac_side', 'up': 'ac_side', 'down': 'ac_side'},
              uv={'north': [2, 2, 14, 11]}),
          box((3, 2, 13), (4, 4, 16), 'pipe'), box((12, 2, 13), (13, 4, 16), 'pipe')]
    model('ac_unit', ac, gui=0.8)
    dish = [box((7, 3, 12), (9, 5, 16), 'pipe'), box((7.5, 3.5, 6), (8.5, 4.5, 12), 'pipe'),
            box((2, 2, 4), (14, 14, 5), {'north': 'dish', 'south': 'dish'}, rot={'origin': [8, 8, 4.5], 'axis': 'x', 'angle': -22.5}),
            box((7.5, 7.5, 0), (8.5, 8.5, 4), 'pipe')]
    model('sat_dish', dish, gui=0.8)
    pipe = [box((6.5, 0, 12), (9.5, 16, 15), 'pipe'), box((6, 6, 15), (10, 7, 16), 'pipe')]
    model('drainpipe', pipe, gui=0.8)
    top = [box((6.5, 0, 12), (9.5, 10, 15), 'pipe'), box((5, 10, 10.5), (11, 15, 16), 'pipe'), box((4, 15, 9), (12, 16, 16), 'pipe')]
    model('drainpipe_top', top, gui=0.8)
    bottom = [box((6.5, 3, 12), (9.5, 16, 15), 'pipe'), box((6.5, 0.5, 8), (9.5, 3, 15), 'pipe')]
    model('drainpipe_bottom', bottom, gui=0.8)
    for k in range(len(SIGNS)):
        e = [box((-16, 2, 13.5), (32, 14, 15.5), {'north': 'shop_sign_%d' % k, 'south': 'pipe', 'up': 'pipe', 'down': 'pipe', 'east': 'pipe', 'west': 'pipe'},
                 uv={'north': [0, 2, 16, 14]}),
             box((-12, 7, 15.5), (-11, 9, 16), 'pipe'), box((27, 7, 15.5), (28, 9, 16), 'pipe')]
        model('shop_sign_%d' % k, e, gui=0.4, extra_tex={'shop_sign_%d' % k: 'airdefense:block/street/shop_sign_%d' % k})
    for k in range(6):
        model('graffiti_%d' % k, [box((0, 0, 15.9), (16, 16, 15.95), {'north': 'graffiti_%d' % k})], gui=0.8)
    for k in range(3):
        model('notice_%d' % k, [box((0, 0, 15.9), (16, 16, 15.95), {'north': 'notice_%d' % k})], gui=0.8)
        model('laundry_%d' % k, [box((0, 0, 8), (16, 16, 8.05), {'north': 'laundry_%d' % k, 'south': 'laundry_%d' % k}),
                                 box((0, 14, 7.8), (16, 14.3, 8.3), 'pipe')], gui=0.8)
    fb = [box((1, 9, 11), (15, 13, 16), 'planter_box'), box((1, 13, 11), (15, 16, 11.05), {'north': 'flowers'}),
          box((1, 13, 13.5), (15, 16, 13.55), {'north': 'flowers'}), box((2, 13, 12), (14, 13.05, 15.5), {'up': 'flowers'})]
    model('flower_box', fb, gui=0.8)
    junk = [
        [box((1, 0, 6), (8, 6, 13), 'crate'), box((2, 6, 7), (7, 10, 12), 'crate'), box((9, 0, 7), (15, 7, 7.05), {'north': 'jar'})],
        [box((2, 0, 12), (3, 15, 13), 'ski'), box((4, 0, 12.5), (5, 15, 13.5), 'ski'), box((7, 0, 6), (14, 4, 12), 'crate'),
         box((8, 4, 7), (13, 7, 11), 'crate')],
        [box((1, 0, 9), (15, 14, 10), {'north': 'tyre', 'south': 'tyre'}, rot={'origin': [8, 7, 9.5], 'axis': 'x', 'angle': -22.5}),
         box((3, 0, 3), (9, 5, 9), 'crate')],
    ]
    for k, e in enumerate(junk):
        model('balcony_junk_%d' % k, e, gui=0.8)
    vent = [box((6, 0, 6), (10, 8, 10), 'vent'), box((4, 8, 4), (12, 10, 12), 'vent'), box((5, 10, 5), (11, 11, 11), 'vent')]
    model('roof_vent', vent, gui=0.8)
    aer = [box((7.5, 0, 7.5), (8.5, 16, 8.5), 'aerial')]
    for y in (9, 12, 15):
        aer.append(box((1, y, 7.6), (15, y + 0.6, 8.4), 'aerial'))
    aer.append(box((7.6, 13, 1), (8.4, 13.6, 15), 'aerial'))
    model('tv_aerial', aer, gui=0.8)
    dump = [box((1, 1, 2), (15, 13, 14), {'north': 'dumpster', 'south': 'dumpster', 'east': 'dumpster', 'west': 'dumpster', 'down': 'dumpster'}),
            box((0.5, 13, 1.5), (15.5, 14, 14.5), 'dumpster_lid', rot={'origin': [8, 13, 14], 'axis': 'x', 'angle': -22.5}),
            box((2, 0, 3), (4, 1, 5), 'tyre'), box((12, 0, 3), (14, 1, 5), 'tyre'), box((2, 0, 11), (4, 1, 13), 'tyre'), box((12, 0, 11), (14, 1, 13), 'tyre')]
    model('dumpster', dump, gui=0.7)


SIMPLE = ['ac_unit', 'sat_dish', 'drainpipe', 'drainpipe_top', 'drainpipe_bottom', 'flower_box', 'roof_vent', 'tv_aerial', 'dumpster']
KINDS = {'shop_sign': len(SIGNS), 'graffiti': 6, 'notice': 3, 'laundry': 3, 'balcony_junk': 3}


def blockstates():
    os.makedirs(ITEMS, exist_ok=True)
    for name in SIMPLE:
        m = 'airdefense:block/street/' + name
        v = {'facing=%s' % f: ({'model': m, 'y': y} if y else {'model': m}) for f, y in ms.FACING_Y.items()}
        with open(os.path.join(STATES, name + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(ITEMS, name + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)
    for name, n in KINDS.items():
        v = {}
        for k in range(12):
            m = 'airdefense:block/street/%s_%d' % (name, k % n)
            for f, y in ms.FACING_Y.items():
                v['facing=%s,kind=%d' % (f, k)] = {'model': m, 'y': y} if y else {'model': m}
        with open(os.path.join(STATES, name + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(ITEMS, name + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': 'airdefense:block/street/%s_0' % name}}, fh, indent=1)


if __name__ == '__main__':
    make_textures()
    models()
    blockstates()
    print('ok')
