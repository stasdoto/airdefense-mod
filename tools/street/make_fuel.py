#!/usr/bin/env python3
"""2.0: the filling station's own street furniture - the fuel pump (a column with its display, nozzles on both sides,
on a little island with yellow posts) and the price board (the grades and their prices, lit at night). Weathered like
the rest of the 1.49 towns: grime at the bottom, streaks, a scuffed kick plate. Also makes the traffic lights' lenses
dark in the model (the lit one is drawn by the client, in step with the crossing's cycle: see client.nation.TrafficLights).
python3 tools/street/make_fuel.py"""
import json
import os

import numpy as np

import make_street as ms
from make_street import box, model, ROOT, save, paint, text

rng = np.random.default_rng(2000)

DIGITS = {
    '2': ['111', '001', '111', '100', '111'], '3': ['111', '001', '011', '001', '111'], '5': ['111', '100', '111', '001', '111'],
    '6': ['111', '100', '111', '101', '111'], '7': ['111', '001', '010', '010', '010'], '8': ['111', '101', '111', '101', '111'],
    '9': ['111', '101', '111', '001', '111'], '.': ['000', '000', '000', '000', '010'], 'C': ['011', '100', '100', '100', '011'],
    '-': ['000', '000', '111', '000', '000'],
}
ms.FONT.update(DIGITS)

BRAND = (34, 128, 72)
BRAND_DARK = (22, 84, 48)


def grime(a, strength=1.0, h=None, w=None):
    """The wear of the years (over the part of the texture the face shows, h x w): dirt splashed up at the bottom, a few
    streaks down from the top, specks."""
    h = h or a.shape[0]
    w = w or a.shape[1]
    for y in range(h):
        k = max(0.0, (y - h * 0.62) / (h * 0.38)) * 0.32 * strength
        a[y, :, :3] = a[y, :, :3] * (1 - k) + np.array((70, 62, 50)) * k
    for _ in range(int(w / 5 * strength)):
        x = rng.integers(0, w)
        length = rng.integers(h // 5, h // 2)
        a[:length, x, :3] *= 0.9
    specks = rng.random(a.shape[:2]) < 0.025 * strength
    a[specks, :3] *= 0.8
    return a


def drop(a, x, y, rgb):
    """A little fuel drop (the station's emblem), 5 wide, 7 high."""
    rows = ['00100', '00100', '01110', '01110', '11111', '11111', '01110']
    for j, r in enumerate(rows):
        for i, c in enumerate(r):
            if c == '1':
                a[y + j, x + i, :3] = rgb


def textures():
    # The pump's front: white enamel, the brand's band, the card slot and the nozzle grade labels; a dark kick plate.
    # (The face is 9 x 18 pixels: the texture's top left 9 x 18 texels.)
    f = paint((226, 226, 220), 3, 32)
    f[1:3, :9, :3] = BRAND
    f[3:4, :9, :3] = BRAND_DARK
    f[5:7, 1:5, :3] = (40, 42, 46)
    f[5, 2:4, :3] = (120, 200, 255)
    text(f, '95', 1, 8, (40, 40, 40))
    f[15:18, :9, :3] = (70, 72, 76)
    save('pump_front', grime(f, 1.0, 18, 9))
    s = paint((226, 226, 220), 3, 32)
    s[1:3, :6, :3] = BRAND
    s[3:4, :6, :3] = BRAND_DARK
    s[15:18, :6, :3] = (70, 72, 76)
    save('pump_side', grime(s, 1.0, 18, 6))
    # The display: litres and money, amber digits on black.
    d = paint((14, 16, 14), 1, 32)
    text(d, '125', 1, 0, (255, 176, 40))
    text(d, '340', 1, 5, (255, 176, 40))
    save('pump_display', d)
    # The head: the brand's colour, its drop in white.
    # (20 x 12 texels for the 10 x 6 face.)
    hd = paint(BRAND, 3, 32)
    hd[11:12, :20, :3] = BRAND_DARK
    drop(hd, 2, 2, (240, 240, 236))
    text(hd, '95', 10, 3, (240, 240, 236))
    save('pump_head', grime(hd, 0.5, 12, 20))
    save('pump_green', paint(BRAND, 3, 16))
    stripes = paint((228, 182, 36), 4, 16)
    for y in range(16):
        if (y // 3) % 2 == 1:
            stripes[y, :, :3] = (30, 30, 30)
    save('post_stripes', stripes)
    # The price board: the brand's band with the drop and the name, then the grades and their prices.
    b = paint((30, 32, 36), 2, 64)
    b[:14, :28, :3] = BRAND
    b[13:14, :28, :3] = BRAND_DARK
    drop(b, 2, 3, (240, 240, 236))
    text(b, 'AЗC', 10, 4, (240, 240, 236))
    rows = [('95', '52.9', (255, 214, 90)), ('92', '49.9', (255, 214, 90)), ('ДП', '51.5', (255, 214, 90)), ('ГAЗ', '27.0', (120, 230, 140))]
    for i, (g, price, rgb) in enumerate(rows):
        y = 17 + i * 10
        b[y - 1:y + 6, 0:28, :3] = (22, 24, 26)
        text(b, g, 1, y, (236, 236, 230))
        whole, frac = price.split('.')
        text(b, whole, 14, y, rgb)
        b[y + 4, 21, :3] = rgb
        text(b, frac, 23, y, rgb)
    b[56:60, :28, :3] = (60, 62, 66)
    save('price_board', grime(b, 0.6, 60, 28))


def pump():
    e = [box((2, 0, 3), (14, 2, 13), 'concrete'),
         # The column: front and back alike (a pump is used from both sides).
         box((3.5, 2, 5), (12.5, 20, 11), {'north': 'pump_front', 'south': 'pump_front', 'east': 'pump_side', 'west': 'pump_side'},
             uv={'north': [0, 0, 4.5, 9], 'south': [0, 0, 4.5, 9], 'east': [0, 0, 3, 9], 'west': [0, 0, 3, 9]}),
         box((4.5, 13, 4.8), (11.5, 18, 5), {'north': 'pump_display'}, uv={'north': [0, 0, 7, 5]}, emit=12),
         box((4.5, 13, 11), (11.5, 18, 11.2), {'south': 'pump_display'}, uv={'south': [0, 0, 7, 5]}, emit=12),
         box((3, 20, 4.5), (13, 26, 11.5), {'north': 'pump_head', 'south': 'pump_head', 'east': 'pump_green', 'west': 'pump_green',
                                             'up': 'white', 'down': 'white'},
             uv={'north': [0, 0, 10, 6], 'south': [0, 0, 10, 6]}, emit=6),
         box((2.5, 26, 4), (13.5, 27.5, 12), 'white')]
    # The nozzles in their holsters on both sides, the hoses looping down from the head.
    for x0, x1, hx0, hx1 in ((12.5, 13.5, 12.6, 14.2), (2.5, 3.5, 1.8, 3.4)):
        e.append(box((x0, 8, 6.5), (x1, 13, 9.5), 'black'))
        e.append(box((hx0, 10, 7.4), (hx1, 12.5, 8.6), 'black'))
        hx = 13.6 if x0 > 8 else 2.0
        e.append(box((hx, 4, 7.8), (hx + 0.5, 10, 8.2), 'rubber'))
        e.append(box((hx, 4, 7.8), (hx + 0.5, 4.5, 10.5), 'rubber'))
        e.append(box((hx, 4, 10.1), (hx + 0.5, 21, 10.5), 'rubber'))
    # Yellow-and-black posts at the island's corners (against the cars).
    for x, z in ((2.3, 3.3), (12.7, 3.3), (2.3, 11.7), (12.7, 11.7)):
        e.append(box((x, 2, z), (x + 1, 9, z + 1), 'post_stripes'))
    model('fuel_pump', e, gui=0.5)


def price():
    e = [box((1, 0, 7), (15, 30, 9), {'north': 'price_board', 'south': 'price_board', 'east': 'steel', 'west': 'steel', 'up': 'steel',
                                       'down': 'steel'},
             uv={'north': [0, 0, 7, 15], 'south': [0, 0, 7, 15]}, emit=10),
         box((0.5, 29.5, 6.5), (15.5, 31, 9.5), 'pump_green')]
    model('fuel_price', e, gui=0.45, gui_y=-2)


def dark_lenses():
    """The traffic lights' lenses: always the dark frame in the model (the client draws the lit one, see TrafficLights)."""
    tex = os.path.join(ROOT, 'textures', 'block', 'street')
    for c in ('red', 'yellow', 'green'):
        for ph in ('a', 'b'):
            with open(os.path.join(tex, 'lens_%s_%s.png.mcmeta' % (c, ph)), 'w') as fh:
                json.dump({'animation': {'interpolate': False, 'frames': [{'index': 0, 'time': 240}]}}, fh)


def blockstates():
    bs = os.path.join(ROOT, 'blockstates')
    items = os.path.join(ROOT, 'items')
    for bid in ('fuel_pump', 'fuel_price'):
        m = 'airdefense:block/street/' + bid
        v = {'facing=%s' % f: ({'model': m, 'y': y} if y else {'model': m}) for f, y in ms.FACING_Y.items()}
        with open(os.path.join(bs, bid + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(items, bid + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)


if __name__ == '__main__':
    textures()
    pump()
    price()
    dark_lenses()
    blockstates()
    print('ok')
