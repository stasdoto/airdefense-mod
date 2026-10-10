#!/usr/bin/env python3
"""1.48 "Positions": sandbags (a wall and a low parapet), a Czech hedgehog, barbed wire, a camouflage net, and the
icons of the field-work kits. Textures, models, blockstates, item definitions. python3 tools/street/make_fort.py"""
import json
import math
import os

import numpy as np
from PIL import Image

import make_street as ms
from make_street import box, model, ROOT, save, paint

rng = np.random.default_rng(1480)
ITEMTEX = os.path.join(ROOT, 'textures', 'item')


def burlap():
    """Sandbag cloth: a coarse weave, sun-faded khaki, a dark seam round the edge, a few stains."""
    a = paint((150, 132, 96), 6)
    for y in range(16):
        for x in range(16):
            if (x + y) % 2 == 0:
                a[y, x, :3] *= 1.05
            if x % 4 == 0 or y % 4 == 0:
                a[y, x, :3] *= 0.94
    for _ in range(3):
        cx, cy = rng.integers(2, 14, 2)
        a[cy - 1:cy + 2, cx - 1:cx + 2, :3] *= 0.85
    a[0, :, :3] *= 0.65
    a[-1, :, :3] *= 0.62
    a[:, 0, :3] *= 0.7
    a[:, -1, :3] *= 0.7
    # The tie at one end.
    a[6:10, 1, :3] = (92, 78, 52)
    return a


def steel():
    """The hedgehog's rolled steel: dark, rusted at the edges and in streaks."""
    a = paint((70, 64, 58), 5)
    rust = np.array((118, 66, 36), float)
    for _ in range(14):
        y, x = rng.integers(0, 16, 2)
        a[y, x, :3] = a[y, x, :3] * 0.4 + rust * 0.6
    for x in range(16):
        if rng.random() < 0.3:
            length = rng.integers(3, 10)
            a[:length, x, :3] = a[:length, x, :3] * 0.6 + rust * 0.4
    a[0, :, :3] *= 1.25
    return a


def wire():
    """Barbed wire coils seen from the side: loops of thin grey wire with barbs, transparent between."""
    a = np.zeros((16, 16, 4))
    for k in range(3):
        cx = 3 + k * 5
        for t in np.linspace(0, math.pi * 2, 40):
            x = int(round(cx + math.cos(t) * 4))
            y = int(round(8 + math.sin(t) * 6))
            if 0 <= x < 16 and 0 <= y < 16:
                a[y, x] = (150, 152, 150, 255)
        # Barbs.
        for t in np.linspace(0, math.pi * 2, 6, endpoint=False):
            x = int(round(cx + math.cos(t) * 4))
            y = int(round(8 + math.sin(t) * 6))
            for dx, dy in ((1, 0), (0, 1)):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                    a[y + dy, x + dx] = (110, 110, 108, 255)
    a[13, :, :] = (120, 122, 120, 255)
    a[3, :, :] = (120, 122, 120, 255)
    return a


def net():
    """A camouflage net: a knotted mesh with leaves of green and brown cloth tied to it, holes between."""
    a = np.zeros((16, 16, 4))
    cols = [(64, 82, 44), (92, 104, 58), (96, 78, 52), (52, 64, 38)]
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0:
                a[y, x] = (58, 62, 44, 255)
    for _ in range(26):
        y, x = rng.integers(0, 15, 2)
        c = cols[rng.integers(0, len(cols))]
        a[y:y + 2, x:x + rng.integers(1, 3)] = (*c, 255)
    return a


def kit_icon(name, draw):
    a = np.zeros((16, 16, 4))
    draw(a)
    os.makedirs(ITEMTEX, exist_ok=True)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(ITEMTEX, name + '.png'))


def fill(a, x0, y0, x1, y1, rgb):
    a[y0:y1, x0:x1] = (*rgb, 255)


def icons():
    bag = (170, 150, 108)
    plank = (112, 84, 52)
    earth = (96, 72, 50)
    grey = (128, 128, 124)
    green = (70, 92, 50)

    def trench(a):
        fill(a, 0, 8, 16, 16, earth)
        fill(a, 2, 9, 14, 14, plank)
        fill(a, 3, 10, 13, 13, (40, 32, 24))
        for x in range(1, 15, 3):
            fill(a, x, 6, x + 3, 9, bag)
            a[6, x] = (110, 96, 70, 255)

    def position(a):
        for x in range(2, 14, 3):
            fill(a, x, 9, x + 3, 12, bag)
            fill(a, x + 1, 12, x + 4, 15, bag)
        fill(a, 1, 4, 15, 6, green)
        fill(a, 2, 5, 3, 9, plank)
        fill(a, 13, 5, 14, 9, plank)

    def dugout(a):
        fill(a, 0, 4, 16, 7, (84, 110, 56))
        fill(a, 0, 7, 16, 9, earth)
        fill(a, 1, 9, 15, 11, plank)
        fill(a, 2, 11, 14, 15, (40, 32, 24))
        fill(a, 6, 11, 10, 15, (24, 20, 16))

    def pillbox(a):
        fill(a, 2, 5, 14, 14, grey)
        fill(a, 1, 4, 15, 6, (150, 150, 146))
        fill(a, 4, 8, 12, 9, (20, 20, 20))
        fill(a, 0, 13, 16, 16, earth)

    def revetment(a):
        fill(a, 0, 7, 3, 15, earth)
        fill(a, 13, 7, 16, 15, earth)
        fill(a, 0, 6, 3, 8, bag)
        fill(a, 13, 6, 16, 8, bag)
        fill(a, 4, 9, 12, 13, (80, 92, 56))
        fill(a, 6, 7, 10, 9, (70, 80, 50))
        fill(a, 1, 3, 15, 5, green)

    for n, d in (('trench_kit', trench), ('position_kit', position), ('dugout_kit', dugout), ('pillbox_kit', pillbox),
                 ('revetment_kit', revetment)):
        kit_icon(n, d)
        md = os.path.join(ROOT, 'models', 'item')
        with open(os.path.join(md, n + '.json'), 'w') as fh:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/' + n}}, fh, indent=1)
        with open(os.path.join(ROOT, 'items', n + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + n}}, fh, indent=1)


def models():
    save('sandbag', burlap())
    save('hedgehog_steel', steel())
    save('barbed_wire', wire())
    save('camo_net', net())
    # Sandbags: rows of bags laid in a bond (each row shifted half a bag), slightly bulging.
    e = []
    rows = 4
    for r in range(rows):
        y0 = r * 4
        off = 0 if r % 2 == 0 else 4
        for k in range(-1, 3):
            x0 = k * 8 + off
            a, b = max(0, x0), min(16, x0 + 8)
            if b - a < 2:
                continue
            e.append(box((a + 0.2, y0, 0.3), (b - 0.2, y0 + 4, 15.7), 'sandbag'))
            e.append(box((a + 0.8, y0 + 3.6, 1.0), (b - 0.8, y0 + 4.3 if r == rows - 1 else y0 + 4, 15.0), 'sandbag'))
    model('sandbags', e, gui=0.62)
    low = [x for x in e if x['to'][1] <= 8.31]
    for x in low:
        if x['to'][1] > 8:
            x['to'][1] = 8.3
    model('sandbags_low', low, gui=0.62)
    # The hedgehog: three steel angles crossing, standing on their ends.
    h = []
    h.append(box((-1, 7, 7), (17, 9, 9), 'hedgehog_steel', rot={'origin': [8, 8, 8], 'axis': 'z', 'angle': 45}))
    h.append(box((7, 7, -1), (9, 9, 17), 'hedgehog_steel', rot={'origin': [8, 8, 8], 'axis': 'x', 'angle': 45}))
    h.append(box((-1, 7, 7), (17, 9, 9), 'hedgehog_steel', rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}))
    h.append(box((7, 0, 7), (9, 15, 9), 'hedgehog_steel', rot={'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}))
    # Flanges on the angles (they read as L-shaped, not round).
    h.append(box((7.5, 6, 6.5), (8.5, 10, 9.5), 'hedgehog_steel'))
    model('hedgehog', h, gui=0.6)
    # Barbed wire: two crossed panels of coils and a straight wire along each side.
    wv = [box((0, 0, 8), (16, 12, 8.01), 'barbed_wire', faces=['north', 'south']),
          box((8, 0, 0), (8.01, 12, 16), 'barbed_wire', faces=['east', 'west']),
          box((0, 0, 3), (16, 12, 3.01), 'barbed_wire', faces=['north', 'south']),
          box((0, 0, 13), (16, 12, 13.01), 'barbed_wire', faces=['north', 'south'])]
    model('barbed_wire', wv, gui=0.7)
    # The camouflage net: a sheet sagging a little in the middle.
    nv = [box((0, 15, 0), (16, 15.2, 16), 'camo_net', faces=['up', 'down']),
          box((4, 14.4, 4), (12, 14.6, 12), 'camo_net', faces=['up', 'down'])]
    model('camo_net', nv, gui=0.7)


def blockstates():
    bs = os.path.join(ROOT, 'blockstates')
    items = os.path.join(ROOT, 'items')
    for name in ('sandbags', 'sandbags_low', 'hedgehog'):
        m = 'airdefense:block/street/' + name
        v = {'facing=%s' % f: ({'model': m, 'y': y} if y else {'model': m}) for f, y in ms.FACING_Y.items()}
        with open(os.path.join(bs, name + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(items, name + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)
    for name in ('barbed_wire', 'camo_net'):
        m = 'airdefense:block/street/' + name
        with open(os.path.join(bs, name + '.json'), 'w') as fh:
            json.dump({'variants': {'': {'model': m}}}, fh, indent=1)
        with open(os.path.join(items, name + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)


if __name__ == '__main__':
    models()
    blockstates()
    icons()
    print('ok')
