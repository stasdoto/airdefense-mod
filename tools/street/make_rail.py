#!/usr/bin/env python3
"""
1.39 "Roads": the railway's own blocks - the track (concrete sleepers, two rails, the ballast under them; straight and
diagonal, raised in eighths of a block so the line climbs smoothly), the buffer stop at the end of a line and the
station's sign.

The track is wider than a block (the gauge is a metre and a half, the ballast three blocks wide): its model reaches
into the blocks either side. python3 tools/street/make_rail.py
"""
import json
import math
import os

import numpy as np

import make_street as ms
from make_street import box, model, save, paint, FULL, ROOT

rng = np.random.default_rng(1390)


def textures():
    # The rail: a dark rusty web, the bright worn running surface along the top.
    a = paint((92, 78, 66), 5)
    a[:, 6:10, :3] = np.array((176, 178, 180)) + rng.normal(0, 6, (16, 4, 1))
    save('rail_steel', a)
    # A concrete sleeper.
    a = paint((158, 154, 146), 7)
    a[0, :, :3] *= 0.85
    a[-1, :, :3] *= 0.85
    save('sleeper', a)
    # The ballast: crushed grey stone.
    a = paint((118, 114, 108), 4)
    for _ in range(90):
        y, x = rng.integers(0, 16, 2)
        k = rng.uniform(0.6, 1.35)
        a[y, x, :3] *= k
        if x < 15:
            a[y, x + 1, :3] *= (k + 1) / 2
    save('ballast', a)
    # The buffer stop's beam: red and white stripes.
    a = paint((210, 210, 206), 3)
    for x in range(16):
        if (x // 4) % 2 == 0:
            a[:, x, :3] = np.array((186, 34, 30)) + rng.normal(0, 4, (16, 1))
    save('buffer_beam', a)
    # The station sign: a blue board with a white train on it.
    from PIL import Image, ImageDraw
    img = Image.new('RGBA', (32, 16), (26, 70, 150, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 31, 15], outline=(236, 236, 232, 255))
    w = (236, 236, 232, 255)
    d.rectangle([8, 4, 23, 10], fill=w)
    d.polygon([(23, 4), (27, 7), (27, 10), (23, 10)], fill=w)
    for x in (10, 14, 18):
        d.rectangle([x, 5, x + 2, 7], fill=(26, 70, 150, 255))
    d.line([(7, 12), (28, 12)], fill=w)
    for x in (11, 20):
        d.ellipse([x, 10, x + 2, 12], fill=w)
    save('station_sign', np.array(img).astype(float))


TEX = {'ballast': 'airdefense:block/street/ballast'}


def rot45(e):
    e['rotation'] = {'origin': [8, 0, 8], 'axis': 'y', 'angle': 45}
    return e


def track(lift, diagonal):
    """One block of track raised by lift eighths; diagonal: turned 45 degrees (it runs corner to corner)."""
    b = lift * 2
    length = 16 * math.sqrt(2) if diagonal else 16
    z0 = 8 - length / 2 - (0.3 if diagonal else 0)
    z1 = 8 + length / 2 + (0.3 if diagonal else 0)
    e = []
    if b > 0:
        for x0 in (-16, 0, 16):
            e.append(box((x0, 0, z0), (x0 + 16, b, z1), 'ballast', faces=('up', 'north', 'south', 'east', 'west') if x0 != 0 else ('up', 'north', 'south')))
    count = 3 if diagonal else 2
    step = length / count
    for k in range(count):
        zc = z0 + step * (k + 0.5)
        e.append(box((-9, b, zc - 1.5), (25, b + 1.5, zc + 1.5), 'sleeper', faces=('up', 'north', 'south', 'east', 'west')))
    for x in (-4, 18):
        e.append(box((x, b + 1.5, z0), (x + 2, b + 4, z1), 'rail_steel', faces=('up', 'east', 'west'),
                     uv={'up': [6, 0, 10, 16], 'east': [0, 0, 16, 2.5], 'west': [0, 0, 16, 2.5]}))
    if diagonal:
        e = [rot45(x) for x in e]
    name = 'track_%s_%d' % ('diag' if diagonal else 'straight', lift)
    model(name, e, gui=0.45, extra_tex=TEX)


def buffer_stop():
    e = [box((-4, 0, 6), (20, 4, 14), 'sleeper'),
         box((-3, 4, 9), (0, 14, 12), 'steel'), box((16, 4, 9), (19, 14, 12), 'steel'),
         box((-3, 2, 12), (0, 5, 20), 'steel'), box((16, 2, 12), (19, 5, 20), 'steel'),
         box((-6, 10, 6), (22, 15, 9), {'north': 'buffer_beam', 'south': 'steel', 'up': 'steel', 'down': 'steel', 'east': 'steel', 'west': 'steel'},
             uv={'north': FULL}),
         box((-1, 11, 4), (2, 14, 6), 'steel'), box((14, 11, 4), (17, 14, 6), 'steel')]
    model('buffer_stop', e, gui=0.5)


def station_sign():
    e = [box((-6, 0, 7.4), (-4.8, 30, 8.6), 'steel'), box((20.8, 0, 7.4), (22, 30, 8.6), 'steel'),
         box((-8, 20, 7.6), (24, 30, 8.4), {'north': 'station_sign', 'south': 'station_sign', 'up': 'steel', 'down': 'steel', 'east': 'steel', 'west': 'steel'},
             uv={'north': FULL, 'south': [16, 0, 0, 16]})]
    model('station_sign', e, gui=0.4)


def blockstates():
    bs = os.path.join(ROOT, 'blockstates')
    items = os.path.join(ROOT, 'items')
    v = {}
    for d in range(4):
        for lift in range(8):
            diag = d in (1, 3)
            m = 'airdefense:block/street/track_%s_%d' % ('diag' if diag else 'straight', lift)
            y = 90 if d in (1, 2) else 0
            v['dir=%d,lift=%d' % (d, lift)] = {'model': m, 'y': y} if y else {'model': m}
    with open(os.path.join(bs, 'track.json'), 'w') as fh:
        json.dump({'variants': v}, fh, indent=1)
    with open(os.path.join(items, 'track.json'), 'w') as fh:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'airdefense:block/street/track_straight_0'}}, fh, indent=1)
    for bid in ('buffer_stop', 'station_sign'):
        m = 'airdefense:block/street/' + bid
        v = {'facing=%s' % f: ({'model': m, 'y': y} if y else {'model': m}) for f, y in ms.FACING_Y.items()}
        with open(os.path.join(bs, bid + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(items, bid + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)


if __name__ == '__main__':
    textures()
    for lift in range(8):
        track(lift, False)
        track(lift, True)
    buffer_stop()
    station_sign()
    blockstates()
    print('ok')
