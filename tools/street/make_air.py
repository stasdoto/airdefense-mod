#!/usr/bin/env python3
"""
1.40 "Airport": the airport's own blocks - a runway edge light (a little glowing dome on a stalk), a windsock on its
frame, the terminal's sign (a long blue board, the aeroplane and the word), and the two markers the planes find their
way by: the threshold (in the runway's centre line, white) and the stand (where a parked plane's nose stops, yellow).

python3 tools/street/make_air.py
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import make_street as ms
from make_street import box, model, save, paint, FULL, ROOT

rng = np.random.default_rng(1400)


def font(size):
    for p in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', '/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf'):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def textures():
    # The edge light's glass: warm white, bright (drawn full bright by the model's emission).
    a = paint((250, 244, 214), 3)
    a[5:11, 5:11, :3] = (255, 255, 240)
    save('runway_lamp', a)
    # The windsock: orange and white bands.
    a = paint((236, 236, 230), 3)
    for x in range(16):
        if (x // 4) % 2 == 0:
            a[:, x, :3] = np.array((232, 110, 30)) + rng.normal(0, 4, (16, 1))
    save('windsock', a)
    # The terminal's sign: white letters on blue, a plane at the left.
    img = Image.new('RGBA', (128, 16), (24, 70, 160, 255))
    d = ImageDraw.Draw(img)
    w = (244, 244, 240, 255)
    d.polygon([(3, 8), (13, 6), (16, 2), (18, 2), (17, 6), (22, 6), (24, 4), (25, 4), (24, 8), (25, 12), (24, 12), (22, 10), (17, 10),
               (18, 14), (16, 14), (13, 10)], fill=w)
    f = font(13)
    d.text((32, 0), 'АЭРОПОРТ', font=f, fill=w)
    d.rectangle([0, 0, 127, 15], outline=(210, 214, 220, 255))
    save('airport_sign', np.array(img).astype(float))


def runway_light():
    e = [box((7, 0, 7), (9, 2, 9), 'steel'),
         box((5.5, 2, 5.5), (10.5, 3.5, 10.5), 'steel'),
         box((6, 3.5, 6), (10, 5, 10), 'runway_lamp', emit=15)]
    model('runway_light', e, gui=1.4)


def windsock():
    # A frame on top of the post, the sock streaming out with the wind (tapering, drooping a little).
    e = [box((7, 0, 7), (9, 6, 9), 'steel'),
         box((7.5, 5, 9), (8.5, 6, 12), 'steel')]
    z = 12
    for i, (r, dy) in enumerate(((3.2, 0), (2.8, -0.4), (2.4, -0.9), (2.0, -1.5), (1.6, -2.2))):
        e.append(box((8 - r, 5.5 - r + dy, z), (8 + r, 5.5 + r + dy, z + 4), 'windsock', faces=('up', 'down', 'east', 'west')))
        z += 4
    model('windsock', e, gui=0.5)


def airport_sign():
    e = [box((-16, 0, 7), (32, 16, 9), {'north': 'airport_sign', 'south': 'airport_sign', 'up': 'steel', 'down': 'steel', 'east': 'steel', 'west': 'steel'},
             uv={'north': FULL, 'south': [16, 0, 0, 16]}, emit=10),
         box((-14, 0, 9), (-12, 2, 11), 'steel'), box((28, 0, 9), (30, 2, 11), 'steel')]
    model('airport_sign', e, gui=0.3)


def blockstates():
    bs = os.path.join(ROOT, 'blockstates')
    items = os.path.join(ROOT, 'items')
    for bid in ('runway_light', 'windsock', 'airport_sign'):
        m = 'airdefense:block/street/' + bid
        v = {'facing=%s' % f: ({'model': m, 'y': y} if y else {'model': m}) for f, y in ms.FACING_Y.items()}
        with open(os.path.join(bs, bid + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(items, bid + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)
    # The markers: whole blocks of white and yellow concrete (they are the surface they lie in).
    for bid, tex in (('threshold', 'minecraft:block/white_concrete'), ('stand', 'minecraft:block/yellow_concrete')):
        m = 'airdefense:block/street/' + bid
        with open(os.path.join(ms.MODELDIR, bid + '.json'), 'w') as fh:
            json.dump({'parent': 'minecraft:block/cube_all', 'textures': {'all': tex}}, fh, indent=1)
        v = {'facing=%s' % f: {'model': m} for f in ms.FACING_Y}
        with open(os.path.join(bs, bid + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(items, bid + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)


if __name__ == '__main__':
    textures()
    runway_light()
    windsock()
    airport_sign()
    blockstates()
    print('ok')
