#!/usr/bin/env python3
"""The air raid siren block (1.24): a motor siren on a steel pole with a junction box and a red lamp.
Writes the block models (off / sounding), the texture, the blockstate and the item definition.
python3 tools/siren_model.py"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense')
rng = np.random.default_rng(1944)

# Texture regions (64x64 atlas): (u, v, w, h) in pixels.
REG = {
    'pole': (0, 0, 8, 32),
    'drum': (8, 0, 24, 16),
    'slots': (8, 16, 24, 16),
    'motor': (32, 0, 16, 16),
    'grille': (32, 16, 16, 16),
    'box': (48, 0, 16, 16),
    'lamp_off': (48, 16, 8, 8),
    'lamp_on': (56, 16, 8, 8),
    'base': (0, 32, 16, 16),
    'bracket': (16, 32, 16, 16),
}


def texture():
    img = np.zeros((64, 64, 4))
    img[..., 3] = 255

    def fill(name, rgb, noise=6.0):
        u, v, w, h = REG[name]
        img[v:v + h, u:u + w, :3] = np.array(rgb) + rng.normal(0, noise, (h, w, 1))
        return img[v:v + h, u:u + w, :3]

    p = fill('pole', (128, 132, 128), 5)
    p[:, 0] *= 0.8
    p[:, -1] *= 0.75
    p[:, 2] = p[:, 2] * 0.7 + 255 * 0.3 * 0.5  # a highlight down the pipe
    d = fill('drum', (96, 112, 92), 5)
    # Weathering: streaks of rust and grime running down.
    for x in rng.integers(0, 24, 6):
        d[6:, x] = d[6:, x] * 0.7 + np.array([110, 70, 40]) * 0.3
    d[0, :] *= 1.15
    d[-1, :] *= 0.7
    s = fill('slots', (96, 112, 92), 5)
    for x in range(1, 24, 3):
        s[3:13, x:x + 2] = (24, 26, 24)
    s[0, :] *= 1.15
    fill('motor', (110, 114, 108), 4)[:, ::4] *= 0.85
    gr = fill('grille', (40, 42, 40), 4)
    for i in range(16):
        for j in range(16):
            r = math.hypot(i - 7.5, j - 7.5)
            if r > 7.4:
                gr[i, j] = (96, 112, 92)
            elif (i + j) % 3 == 0:
                gr[i, j] = (18, 18, 18)
    b = fill('box', (150, 150, 140), 5)
    b[0, :] *= 1.15
    b[-1, :] *= 0.75
    b[4:6, 3:13] = (40, 40, 40)
    b[9:13, 5:11] = (210, 190, 60)
    b[10:12, 7:9] = (30, 30, 30)
    lo = fill('lamp_off', (90, 20, 18), 3)
    lo[1:3, 1:3] = (140, 60, 60)
    ln = fill('lamp_on', (255, 60, 40), 2)
    ln[1:3, 1:3] = (255, 220, 200)
    fill('base', (70, 72, 70), 5)
    fill('bracket', (80, 84, 80), 5)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(ROOT, 'textures', 'block', 'siren.png'))


def uv(name, w, h):
    """A face's uv inside its region (scaled to 0..16 of the 64 px texture)."""
    u, v, rw, rh = REG[name]
    w = min(w, rw)
    h = min(h, rh)
    k = 16 / 64
    return [round(u * k, 4), round(v * k, 4), round((u + w) * k, 4), round((v + h) * k, 4)]


def box(frm, to, tex, rot=None, faces=None, ends=None):
    sx, sy, sz = (to[i] - frm[i] for i in range(3))
    dims = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}
    out = {}
    for f in faces or dims:
        t = (ends or tex) if f in ('north', 'south') else tex
        out[f] = {'texture': '#' + t, 'uv': uv(t, max(1, round(dims[f][0])), max(1, round(dims[f][1])))}
    e = {'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'faces': out}
    if rot:
        e['rotation'] = rot
    return e


def octagon_z(cx, cy, z0, z1, r, tex, ends=None):
    """A round part along z (the siren's drum) as two slabs and two slabs turned 45 degrees."""
    a = r * 0.4142
    out = [box((cx - r, cy - a, z0), (cx + r, cy + a, z1), tex, ends=ends),
           box((cx - a, cy - r, z0), (cx + a, cy + r, z1), tex, ends=ends)]
    for ang in (45, -45):
        out.append(box((cx - r, cy - a, z0), (cx + r, cy + a, z1), tex, ends=ends,
                       rot={'origin': [cx, cy, (z0 + z1) / 2], 'axis': 'z', 'angle': ang}))
    return out


def model(lamp):
    els = []
    els.append(box((5.5, 0, 5.5), (10.5, 1, 10.5), 'base'))
    els.append(box((7, 1, 7), (9, 21, 9), 'pole'))
    # Junction box with the warning lamp on top.
    els.append(box((9, 9, 6.5), (11.6, 14, 9.5), 'box'))
    els.append(box((9.6, 14, 7.3), (11, 15.2, 8.7), lamp))
    # Bracket and the siren: the slotted stator drum, the motor behind it, the horn's mouth and grille in front.
    els.append(box((6.5, 20, 6.5), (9.5, 22, 9.5), 'bracket'))
    els += octagon_z(8, 26, 4.5, 11.5, 4.2, 'slots', ends='drum')
    els += octagon_z(8, 26, 3.5, 4.5, 4.6, 'drum', ends='grille')
    els += octagon_z(8, 26, 11.5, 15.5, 3.0, 'motor', ends='motor')
    els.append(box((7.4, 22, 9.5), (8.6, 26, 12.5), 'bracket'))
    return {'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'particle': 'airdefense:block/siren', 'pole': 'airdefense:block/siren', 'drum': 'airdefense:block/siren',
                         'slots': 'airdefense:block/siren', 'motor': 'airdefense:block/siren', 'grille': 'airdefense:block/siren',
                         'box': 'airdefense:block/siren', 'lamp_off': 'airdefense:block/siren', 'lamp_on': 'airdefense:block/siren',
                         'base': 'airdefense:block/siren', 'bracket': 'airdefense:block/siren'},
            'elements': els,
            'display': {
                'gui': {'rotation': [30, 225, 0], 'translation': [0, -3.5, 0], 'scale': [0.42, 0.42, 0.42]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, -3, 0], 'scale': [0.45, 0.45, 0.45]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 0], 'scale': [0.3, 0.3, 0.3]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, -2, 0], 'scale': [0.35, 0.35, 0.35]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, -2, 0], 'scale': [0.35, 0.35, 0.35]},
            }}


def main():
    texture()
    for name, lamp in (('siren', 'lamp_off'), ('siren_on', 'lamp_on')):
        with open(os.path.join(ROOT, 'models', 'block', name + '.json'), 'w') as f:
            json.dump(model(lamp), f, indent=1)
    variants = {}
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for facing, y in rot.items():
        for sig in ('off', 'alert', 'clear'):
            m = {'model': 'airdefense:block/' + ('siren' if sig == 'off' else 'siren_on')}
            if y:
                m['y'] = y
            variants['facing=%s,signal=%s' % (facing, sig)] = m
    with open(os.path.join(ROOT, 'blockstates', 'siren.json'), 'w') as f:
        json.dump({'variants': variants}, f, indent=1)
    with open(os.path.join(ROOT, 'items', 'siren.json'), 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'airdefense:block/siren'}}, f, indent=1)
    print('siren model written')


if __name__ == '__main__':
    main()
