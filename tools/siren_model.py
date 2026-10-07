#!/usr/bin/env python3
"""The air raid siren (1.24): a motor siren like the Soviet S-40 on a steel mast.

The siren head is one block (the round stator drum with its slots, the horn ring and the rotor behind the grille, the
electric motor with its cooling fins at the back, a rain hood, a red lamp that lights while it sounds). The mast is a
separate block put under it: the foot with its base plate, the part with the switch cabinet, plain pipe.
Round parts are 16-sided: eight crossed slabs turned 22.5 degrees apart (block models take any angle since 26.x).

Writes the block models, the 64x64 texture, the blockstates and the item definitions.
python3 tools/siren_model.py
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense')
rng = np.random.default_rng(1944)
TEX = 64
K = 16 / TEX  # uv units per texel

# Texture regions (texels): (u, v, w, h).
REG = {
    'pipe': (0, 0, 8, 32),
    'slots': (8, 0, 24, 8),
    'ring': (8, 8, 24, 8),
    'motor': (8, 16, 24, 8),
    'paint': (8, 24, 24, 8),
    'grille': (32, 0, 32, 32),
    'motor_end': (0, 32, 24, 24),
    'drum_end': (24, 32, 24, 24),
    'box': (48, 32, 16, 24),
    'lamp_off': (0, 56, 8, 8),
    'lamp_on': (8, 56, 8, 8),
    'base': (16, 56, 8, 8),
    'steel': (24, 56, 16, 8),
    'cable': (40, 56, 8, 8),
    'bolt': (48, 56, 8, 8),
    'cabinet': (56, 56, 8, 8),
}

PAINT = np.array([104, 116, 98])      # grey-green enamel
STEEL = np.array([92, 96, 98])
GALV = np.array([150, 154, 152])      # galvanized mast


def texture():
    img = np.zeros((TEX, TEX, 4))
    img[..., 3] = 255

    def fill(name, rgb, noise=5.0):
        u, v, w, h = REG[name]
        img[v:v + h, u:u + w, :3] = np.array(rgb, dtype=float) + rng.normal(0, noise, (h, w, 1))
        return img[v:v + h, u:u + w, :3]

    # Mast: a galvanized pipe, light streak, darker edges, a few rust runs.
    p = fill('pipe', GALV, 4)
    p[:, 0] *= 0.72
    p[:, -1] *= 0.68
    p[:, 2:4] = p[:, 2:4] * 0.6 + 255 * 0.4
    for x in rng.integers(0, 8, 2):
        y0 = int(rng.integers(0, 20))
        p[y0:y0 + 10, x] = p[y0:y0 + 10, x] * 0.6 + np.array([120, 80, 50]) * 0.4
    # Stator drum seen from the side: u along the drum (front to back), v around it. Each of the 16 faces shows one
    # port (the slots through which the rotor blows) with the dark inside and a glimpse of the rotor blade.
    s = fill('slots', PAINT, 4)
    s[:, :2] *= 0.85
    s[2:6, 4:20] = (26, 28, 26)
    s[3:5, 6:18] = (54, 56, 52)
    s[2, 4:20] = (70, 74, 68)
    s[0, :] *= 1.12
    # Grime streaks running back from the ports.
    s[6:, 4:20] *= 0.9
    r = fill('ring', PAINT * 1.05, 4)
    r[:, -2:] *= 0.8
    m = fill('motor', STEEL, 3)
    m[:, ::3] *= 0.7  # cooling fins round the motor
    m[0, :] *= 1.15
    fill('paint', PAINT, 4)
    # The horn: rotor vanes behind a guard grid, all symmetric for the 16-fold drawing.
    g = fill('grille', PAINT, 3)
    c = (31) / 2
    for i in range(32):
        for j in range(32):
            d = math.hypot(i - c, j - c)
            a = math.atan2(i - c, j - c)
            if d > 15.6:
                g[i, j] = PAINT * 0.85
            elif d > 13.6:
                g[i, j] = PAINT * (1.08 if d > 14.6 else 0.7)  # the lip of the horn
            elif d < 3.2:
                g[i, j] = (60, 62, 60) if d > 1.5 else (90, 92, 90)  # hub
            else:
                vane = (math.cos(a * 8) > 0.55)
                g[i, j] = (66, 70, 66) if vane else (18, 19, 18)
                if abs(d - 8.5) < 0.6 or abs(d - 12.0) < 0.6:
                    g[i, j] = (120, 124, 118)  # guard rings
    me = fill('motor_end', STEEL, 3)
    c = 11.5
    for i in range(24):
        for j in range(24):
            d = math.hypot(i - c, j - c)
            a = math.atan2(i - c, j - c)
            if d > 11:
                me[i, j] = STEEL * 0.7
            elif d < 2.5:
                me[i, j] = STEEL * 0.6
            elif abs(d - 7.5) < 0.9 and math.cos(a * 8) > 0.7:
                me[i, j] = (40, 42, 44)  # bolts
            elif math.cos(a * 16) > 0.6 and d > 4:
                me[i, j] = STEEL * 0.8  # fan cover slots
    de = fill('drum_end', PAINT * 0.78, 3)
    de[::4, :] *= 0.9
    b = fill('box', (168, 170, 164), 4)
    b[0, :] *= 1.15
    b[-1, :] *= 0.7
    b[:, 0] *= 0.85
    b[:, -1] *= 0.8
    b[2:22, 2] = (110, 112, 108)     # door gap
    b[2:22, 13] = (110, 112, 108)
    b[2, 2:14] = (110, 112, 108)
    b[21, 2:14] = (110, 112, 108)
    b[10:13, 11:13] = (40, 40, 40)   # handle
    # Warning triangle with the lightning bolt.
    for i in range(6):
        b[5 + i, 8 - i // 2 - 1:8 + i // 2 + 1] = (230, 196, 40)
    b[7:10, 7:9] = (30, 30, 30)
    lo = fill('lamp_off', (96, 22, 20), 3)
    lo[1:3, 1:3] = (150, 70, 66)
    ln = fill('lamp_on', (255, 70, 46), 2)
    ln[1:4, 1:4] = (255, 226, 210)
    bp = fill('base', (118, 120, 118), 5)
    bp[::7, :] *= 0.8
    fill('steel', STEEL * 0.85, 4)
    fill('cable', (24, 24, 26), 2)
    bo = fill('bolt', (70, 72, 74), 3)
    bo[2:6, 2:6] = (120, 122, 124)
    cb = fill('cabinet', (150, 152, 146), 4)
    cb[0, :] *= 1.15
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(ROOT, 'textures', 'block', 'siren.png'))


# ------------------------------------------------------------------------------------------------
# Elements

DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def region_uv(name, w, h, dens=2.0):
    """The uv of a face w x h model pixels inside a region, at dens texels per pixel (clipped to the region)."""
    u, v, rw, rh = REG[name]
    tw = min(rw, w * dens)
    th = min(rh, h * dens)
    return [round(u * K, 4), round(v * K, 4), round((u + tw) * K, 4), round((v + th) * K, 4)]


def el(frm, to, tex, faces=None, uvs=None, rot=None, dens=2.0):
    """One element. tex: the region for all faces, or a dict face -> region. uvs: face -> explicit uv."""
    size = [to[i] - frm[i] for i in range(3)]
    out = {}
    for f in faces or DIMS:
        name = tex[f] if isinstance(tex, dict) else tex
        if uvs and f in uvs:
            uv = uvs[f]
        else:
            a, b = DIMS[f]
            uv = region_uv(name, size[a], size[b], dens)
        out[f] = {'texture': '#t', 'uv': uv}
    e = {'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'faces': out}
    if rot:
        e['rotation'] = rot
    return e


def disc_uv(name, frac_w, frac_h):
    """The centre strip of a round picture: frac_w x frac_h of the region's size."""
    u, v, w, h = REG[name]
    cu = u + w / 2
    cv = v + h / 2
    return [round((cu - w / 2 * frac_w) * K, 4), round((cv - h / 2 * frac_h) * K, 4),
            round((cu + w / 2 * frac_w) * K, 4), round((cv + h / 2 * frac_h) * K, 4)]


def drum(cx, cy, z0, z1, r, side, front=None, back=None, side_uv=None):
    """A 16-sided cylinder along z: eight slabs 22.5 degrees apart. Each slab shows its two long sides; the end
    pictures (round, symmetric) are drawn by each slab at a hair's different depth so they never flicker."""
    a = r * math.tan(math.radians(11.25))
    els = []
    for k in range(8):
        ang = -67.5 + k * 22.5
        faces = ['east', 'west']
        uvs = {}
        if side_uv:
            uvs['east'] = uvs['west'] = side_uv
        texd = {'east': side, 'west': side}
        dz = k * 0.002
        if front:
            faces.append('north')
            texd['north'] = front
            uvs['north'] = disc_uv(front, 1.0, a / r)
        if back:
            faces.append('south')
            texd['south'] = back
            uvs['south'] = disc_uv(back, 1.0, a / r)
        rot = None if ang == 0 else {'origin': [cx, cy, (z0 + z1) / 2], 'axis': 'z', 'angle': ang}
        els.append(el((cx - r, cy - a, z0 - dz), (cx + r, cy + a, z1 + dz), texd, faces=faces, uvs=uvs, rot=rot))
    return els


def head(lamp):
    """The siren head facing north (the horn's mouth to -z), standing on the end of the mast."""
    els = []
    # The top of the mast and the cradle.
    els.append(el((6.5, 0, 6.5), (9.5, 9, 9.5), 'pipe', faces=['north', 'south', 'east', 'west'], dens=2.0))
    els.append(el((5.5, 9, 5), (10.5, 10.4, 12), 'steel'))
    els.append(el((4.6, 10.4, 4.5), (6, 13.5, 11), 'steel'))
    els.append(el((10, 10.4, 4.5), (11.4, 13.5, 11), 'steel'))
    # Stator drum with its ports, the horn ring and the rotor behind the guard, the back plate.
    els += drum(8, 17.5, 1.5, 11.5, 6.0, 'slots', side_uv=[REG['slots'][0] * K, REG['slots'][1] * K,
                                                           (REG['slots'][0] + 24) * K, (REG['slots'][1] + 8) * K])
    els += drum(8, 17.5, 0.4, 1.6, 6.6, 'ring', front='grille')
    els += drum(8, 17.5, 11.4, 12.0, 6.2, 'ring', back='drum_end')
    # The motor with its fins and the fan cover at the back.
    els += drum(8, 17.5, 12.0, 18.6, 4.3, 'motor', back='motor_end')
    # Rain hood over the drum: three curved plates.
    for ang in (-34, 0, 34):
        els.append(el((5.4, 23.7, 0.2), (10.6, 24.3, 12.4), 'paint',
                      rot=None if ang == 0 else {'origin': [8, 17.5, 6], 'axis': 'z', 'angle': ang}))
    # The red lamp on the motor, its cable down to the mast.
    els.append(el((6.9, 21.6, 14.0), (9.1, 24.0, 16.2), lamp, dens=3))
    els.append(el((7.3, 21.4, 13.6), (8.7, 21.8, 16.6), 'steel'))
    els.append(el((7.6, 9.6, 13.0), (8.4, 13.4, 13.8), 'cable'))
    els.append(el((7.6, 9.2, 9.0), (8.4, 10.0, 13.8), 'cable'))
    return els


def mast(part):
    els = [el((6.5, 0, 6.5), (9.5, 16, 9.5), 'pipe', faces=['north', 'south', 'east', 'west'])]
    if part == 'base':
        els.append(el((4, 0, 4), (12, 1, 12), 'base'))
        for x, z in ((4.6, 4.6), (10.4, 4.6), (4.6, 10.4), (10.4, 10.4)):
            els.append(el((x - 0.5, 1, z - 0.5), (x + 0.5, 1.6, z + 0.5), 'bolt'))
        # Four gussets round the foot of the pipe.
        els.append(el((7.6, 1, 4.6), (8.4, 4, 6.5), 'steel'))
        els.append(el((7.6, 1, 9.5), (8.4, 4, 11.4), 'steel'))
        els.append(el((4.6, 1, 7.6), (6.5, 4, 8.4), 'steel'))
        els.append(el((9.5, 1, 7.6), (11.4, 4, 8.4), 'steel'))
    if part == 'box':
        # The switch cabinet facing the street, a conduit up the mast.
        els.append(el((4.2, 2, 2.4), (11.8, 13, 6.5), {'north': 'box', 'south': 'cabinet', 'east': 'cabinet', 'west': 'cabinet',
                                                        'up': 'cabinet', 'down': 'cabinet'},
                      uvs={'north': [REG['box'][0] * K, REG['box'][1] * K, (REG['box'][0] + 16) * K, (REG['box'][1] + 24) * K]}))
        els.append(el((3.8, 13, 2.0), (12.2, 13.6, 6.8), 'steel'))
        els.append(el((7.5, 13.6, 5.6), (8.5, 16, 6.5), 'cable'))
    return els


def block_model(els, gui_scale=0.42):
    return {'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'particle': 'airdefense:block/siren', 't': 'airdefense:block/siren'},
            'elements': els,
            'display': {
                'gui': {'rotation': [30, 225, 0], 'translation': [0, -2.0, 0], 'scale': [gui_scale] * 3},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, -2, 0], 'scale': [0.45, 0.45, 0.45]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 0], 'scale': [0.3, 0.3, 0.3]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, -1, 0], 'scale': [0.35, 0.35, 0.35]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, -1, 0], 'scale': [0.35, 0.35, 0.35]},
            }}


def main():
    texture()
    mdir = os.path.join(ROOT, 'models', 'block')
    for name, lamp in (('siren', 'lamp_off'), ('siren_on', 'lamp_on')):
        with open(os.path.join(mdir, name + '.json'), 'w') as f:
            json.dump(block_model(head(lamp), 0.5), f, indent=1)
    for part in ('base', 'pipe', 'box'):
        with open(os.path.join(mdir, 'siren_mast_' + part + '.json'), 'w') as f:
            json.dump(block_model(mast(part), 0.6), f, indent=1)
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    variants = {}
    for facing, y in rot.items():
        for sig in ('off', 'alert', 'clear'):
            m = {'model': 'airdefense:block/' + ('siren' if sig == 'off' else 'siren_on')}
            if y:
                m['y'] = y
            variants['facing=%s,signal=%s' % (facing, sig)] = m
    with open(os.path.join(ROOT, 'blockstates', 'siren.json'), 'w') as f:
        json.dump({'variants': variants}, f, indent=1)
    variants = {}
    for facing, y in rot.items():
        for part in ('base', 'pipe', 'box'):
            m = {'model': 'airdefense:block/siren_mast_' + part}
            if y:
                m['y'] = y
            variants['facing=%s,part=%s' % (facing, part)] = m
    with open(os.path.join(ROOT, 'blockstates', 'siren_mast.json'), 'w') as f:
        json.dump({'variants': variants}, f, indent=1)
    with open(os.path.join(ROOT, 'items', 'siren.json'), 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'airdefense:block/siren'}}, f, indent=1)
    with open(os.path.join(ROOT, 'items', 'siren_mast.json'), 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'airdefense:block/siren_mast_box'}}, f, indent=1)
    print('siren models written')


if __name__ == '__main__':
    main()
