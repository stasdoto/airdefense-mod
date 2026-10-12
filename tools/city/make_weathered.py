#!/usr/bin/env python3
"""
1.49 "A town with a past": the weathered materials of the towns. Walls that have stood forty winters (concrete panels
in every paint, plaster in every colour, red and silicate brick, sandstone) - each in two plain looks and three that
face the street: dirt splashed up from the pavement, streaks run down from the window sills, cracks and peeled patches;
the tar of the flat roofs; the street: asphalt with cracks, patches and potholes, worn paint, granite kerbs, paving of
four kinds (broken slabs, weeds in the joints), the Soviet asphalt pavement.

Writes textures (block/city/*), models (block/city/*), blockstates, loot tables (a weathered wall gives back the block
it was made of). The Java side (street/Weathered.java) registers the same names in the same order.
python3 tools/city/make_weathered.py
"""
import json
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'airdefense')
TEX = os.path.join(ASSETS, 'textures', 'block', 'city')
MODELS = os.path.join(ASSETS, 'models', 'block', 'city')
STATES = os.path.join(ASSETS, 'blockstates')
LOOT = os.path.join(RES, 'data', 'airdefense', 'loot_table', 'blocks')

DYES = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
        'blue', 'brown', 'green', 'red', 'black']

# Vanilla concrete, roughly; the paint of a panel has faded towards grey.
CONCRETE = {'white': (207, 213, 214), 'orange': (224, 97, 1), 'magenta': (169, 48, 159), 'light_blue': (36, 137, 199),
            'yellow': (241, 175, 21), 'lime': (94, 169, 24), 'pink': (214, 101, 143), 'gray': (55, 58, 62),
            'light_gray': (125, 125, 115), 'cyan': (21, 119, 136), 'purple': (100, 32, 156), 'blue': (45, 47, 143),
            'brown': (96, 60, 32), 'green': (73, 91, 36), 'red': (142, 33, 33), 'black': (8, 10, 15)}
# Plaster for the terracotta of the designs: the colours towns are painted in.
PLASTER = {'white': (226, 218, 198), 'orange': (214, 152, 96), 'magenta': (182, 128, 152), 'light_blue': (156, 184, 202),
           'yellow': (226, 198, 124), 'lime': (176, 192, 128), 'pink': (222, 174, 166), 'gray': (128, 126, 122),
           'light_gray': (182, 178, 168), 'cyan': (126, 164, 162), 'purple': (154, 126, 162), 'blue': (116, 136, 172),
           'brown': (152, 114, 84), 'green': (126, 146, 106), 'red': (172, 96, 80), 'black': (68, 66, 64),
           'plain': (190, 136, 104)}

LOOKS = ['a', 'b', 'grime', 'streak', 'crack']
FACING = {'grime', 'streak', 'crack'}


def rng_for(name):
    return np.random.default_rng(sum(ord(c) * (i + 7) * 31 for i, c in enumerate(name)))


def blur(m, k=1):
    out = m.copy()
    for _ in range(k):
        out = (out + np.roll(out, 1, 0) + np.roll(out, -1, 0) + np.roll(out, 1, 1) + np.roll(out, -1, 1)) / 5
    return out


def field(r, scale=3):
    """Soft tileable noise in 0..1 (wrap-around blur of random values)."""
    m = r.random((16, 16))
    m = blur(m, scale)
    m -= m.min()
    return m / max(1e-6, m.max())


def base_layer(r, rgb, noise, mottle):
    a = np.zeros((16, 16, 4))
    f = field(r, 3)
    a[..., :3] = np.array(rgb, float) * (1 - mottle / 2 + mottle * f[..., None]) + r.normal(0, noise, (16, 16, 1))
    a[..., 3] = 255
    return a


def pits(a, r, n, dark=0.78):
    for _ in range(n):
        y, x = r.integers(0, 16, 2)
        a[y, x, :3] *= dark


def dirt(a, r, rgb=(84, 72, 58)):
    """Dirt splashed up from the ground: heavy at the bottom, flecks higher up."""
    dc = np.array(rgb, float)
    for y in range(16):
        k = max(0.0, (y - 6) / 9.0) ** 1.6 * 0.75
        for x in range(16):
            kk = k * (0.7 + 0.6 * r.random())
            a[y, x, :3] = a[y, x, :3] * (1 - kk) + dc * kk
    for _ in range(10):
        y = int(r.integers(4, 16))
        x = int(r.integers(0, 16))
        a[y, x, :3] = a[y, x, :3] * 0.5 + dc * 0.5


def streaks(a, r, rgb=(70, 66, 60), n=None):
    """Dark streaks run down from a sill above: strong at the top, thinning out."""
    dc = np.array(rgb, float)
    cols = r.choice(16, size=n or int(r.integers(2, 4)), replace=False)
    for x0 in cols:
        length = int(r.integers(9, 17))
        w = int(r.integers(1, 3))
        for y in range(length):
            k = 0.55 * (1 - y / length) ** 0.8
            for x in range(x0, min(16, x0 + w)):
                a[y, x, :3] = a[y, x, :3] * (1 - k) + dc * k
        if r.random() < 0.5 and x0 + w < 16:
            # A rust spot where a bracket was.
            a[0:1, x0:x0 + w, :3] = a[0:1, x0:x0 + w, :3] * 0.6 + np.array((120, 70, 40)) * 0.4


def crack(a, r, under=None):
    """A crack across the face; plaster peeled off in a patch where the thing underneath shows."""
    x = float(r.integers(3, 13))
    for y in range(16):
        x += r.normal(0, 0.8)
        xi = int(np.clip(round(x), 0, 15))
        a[y, xi, :3] *= 0.45
        if r.random() < 0.3 and xi + 1 < 16:
            a[y, xi + 1, :3] *= 0.7
    if under is not None:
        cy, cx = r.integers(3, 12, 2)
        hh, ww = r.integers(3, 6, 2)
        for y in range(cy, min(16, cy + hh)):
            for x in range(cx, min(16, cx + ww)):
                if (y - cy - hh / 2) ** 2 / (hh / 2) ** 2 + (x - cx - ww / 2) ** 2 / (ww / 2) ** 2 <= 1.1:
                    a[y, x, :3] = under[y, x, :3]
        # The edge of the peeled patch, a darker rim.
        for y in range(max(0, cy - 1), min(16, cy + hh + 1)):
            for x in range(max(0, cx - 1), min(16, cx + ww + 1)):
                e = (y - cy - hh / 2) ** 2 / (hh / 2 + 1) ** 2 + (x - cx - ww / 2) ** 2 / (ww / 2 + 1) ** 2
                if 0.75 < e <= 1.0:
                    a[y, x, :3] *= 0.8


def bricks(r, rgb, mortar, var=18, soot=0.0):
    a = np.zeros((16, 16, 4))
    a[..., 3] = 255
    a[..., :3] = mortar
    for row in range(4):
        off = 0 if row % 2 == 0 else 4
        for k in range(-1, 3):
            x0 = k * 8 + off
            c = np.array(rgb, float) * (1 + r.normal(0, var / 160)) + r.normal(0, var / 6, 3)
            if r.random() < 0.12:
                c *= 0.8  # an overburnt brick
            for y in range(row * 4, row * 4 + 3):
                for x in range(max(0, x0), min(16, x0 + 7)):
                    a[y, x, :3] = c + r.normal(0, 4)
    a[..., :3] *= 1 - soot * field(r, 2)[..., None]
    pits(a, r, 6, 0.85)
    return a


def write_png(name, a):
    os.makedirs(TEX, exist_ok=True)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(TEX, name + '.png'))


# ------------------------------------------------------------------------------------------------------------------
# Materials

def panel_set(name, rgb):
    r = rng_for(name)
    faded = np.minimum(255, np.array(rgb, float) * 0.86 + np.array((176, 174, 168)) * 0.14 + 6)
    if name.endswith('_white'):
        faded = np.array((222, 222, 216), float)
    elif name.endswith('_light_gray'):
        faded = np.array((176, 176, 170), float)
    plain = base_layer(r, faded, 3.0, 0.08)
    pits(plain, r, 3, 0.86)
    b = plain.copy()
    # Panel joints: the sealed seam along the bottom and one side, here and there gone dark.
    b[15, :, :3] *= 0.84
    b[:, 0, :3] *= 0.88
    b[14, :, :3] *= 0.95
    under = base_layer(r, (120, 118, 112), 6, 0.2)
    return looks(name, plain, b, under)


def plaster_set(name, rgb):
    r = rng_for(name)
    plain = base_layer(r, rgb, 3.0, 0.16)
    b = plain.copy()
    # Old repairs: a slightly different shade of the same paint.
    f = field(r, 2)
    b[f > 0.62, :3] = b[f > 0.62, :3] * 0.94 + 6
    under = bricks(r, (150, 74, 56), (150, 140, 128), 14)
    return looks(name, plain, b, under)


def brick_set(name, rgb, mortar, soot):
    r = rng_for(name)
    plain = bricks(r, rgb, mortar, 18, soot)
    b = bricks(r, rgb, mortar, 22, soot + 0.08)
    return looks(name, plain, b, None, crack_under=False)


def looks(name, plain, b, under, crack_under=True):
    r = rng_for(name + '/looks')
    out = {'a': plain, 'b': b}
    g = plain.copy()
    dirt(g, r)
    out['grime'] = g
    s = plain.copy()
    streaks(s, r)
    out['streak'] = s
    c = plain.copy()
    crack(c, r, under if crack_under else None)
    if not crack_under:
        # Brick: a few bricks gone, the hole dark.
        for _ in range(2):
            row = int(r.integers(0, 4))
            x0 = int(r.integers(0, 12))
            c[row * 4:row * 4 + 3, x0:x0 + 4, :3] = c[row * 4:row * 4 + 3, x0:x0 + 4, :3] * 0.35
    out['crack'] = c
    for k, v in out.items():
        write_png(name + '_' + k, v)
    return list(out)


def ground_set():
    """The street: asphalt, paint, kerbs, paving. Each a plain block (the top is what shows)."""
    made = []

    def asphalt(r, tone=66):
        a = base_layer(r, (tone, tone, tone + 2), 5, 0.12)
        for _ in range(30):
            y, x = r.integers(0, 16, 2)
            a[y, x, :3] = (tone + 34, tone + 32, tone + 30) if r.random() < 0.5 else (tone - 18, tone - 18, tone - 16)
        return a

    def put(name, a):
        write_png(name, a)
        made.append(name)

    r = rng_for('asphalt')
    put('asphalt_a', asphalt(r))
    put('asphalt_b', asphalt(r, 62))
    c = asphalt(r)
    crack(c, r)
    c2 = c.copy()
    put('asphalt_crack', c)
    p = asphalt(r, 54)
    p[0, :, :3] *= 0.8
    p[:, 0, :3] *= 0.8
    pits(p, r, 4, 0.8)
    put('asphalt_patch', p)
    h = asphalt(r)
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.sqrt((yy - 8.3) ** 2 + ((xx - 7.6) * 0.8) ** 2)
    h[d < 6.2, :3] = h[d < 6.2, :3] * 0.55
    h[(d >= 5.2) & (d < 6.4), :3] = (104, 100, 94)
    for _ in range(14):
        y, x = r.integers(3, 13, 2)
        if d[y, x] < 5:
            h[y, x, :3] = (118, 112, 100)
    put('asphalt_pothole', h)
    # Puddle in the pothole (it rains): dark water with a light glint.
    w = h.copy()
    w[d < 5.0, :3] = (46, 52, 58)
    w[(d < 5.0) & (yy < 6) & (xx > 6) & (xx < 10), :3] = (120, 130, 140)
    put('asphalt_puddle', w)
    m = asphalt(r)
    m[..., :3] = m[..., :3] * 0.18 + np.array((222, 220, 210)) * 0.82
    for _ in range(26):
        y, x = r.integers(0, 16, 2)
        m[y, x, :3] = (90, 90, 92)
    put('road_mark', m)
    mw = asphalt(r)
    keep = blur(r.random((16, 16)), 1) < 0.52
    mw[keep, :3] = mw[keep, :3] * 0.3 + np.array((210, 208, 200)) * 0.7
    put('road_mark_worn', mw)
    # Granite kerb: light, speckled, a worn top edge.
    k = base_layer(r, (150, 148, 144), 7, 0.1)
    for _ in range(40):
        y, x = r.integers(0, 16, 2)
        k[y, x, :3] = (190, 186, 180) if r.random() < 0.5 else (92, 90, 88)
    k[0, :, :3] *= 1.1
    put('kerb', k)
    # Paving: grey square slabs.
    pg = base_layer(r, (150, 148, 144), 4, 0.1)
    for i in range(16):
        if i % 8 == 0:
            pg[i, :, :3] *= 0.7
            pg[:, i, :3] *= 0.7
    for q in range(4):
        y0, x0 = (q // 2) * 8, (q % 2) * 8
        pg[y0 + 1:y0 + 8, x0 + 1:x0 + 8, :3] *= 0.94 + 0.12 * r.random()
    put('paving_grey', pg)
    pb = pg.copy()
    crack(pb, r)
    y0, x0 = 8, 0
    pb[y0 + 1:y0 + 8, x0 + 1:x0 + 8, :3] = pb[y0 + 1:y0 + 8, x0 + 1:x0 + 8, :3] * 0.6 + np.array((90, 76, 60)) * 0.4
    put('paving_broken', pb)
    pw = pg.copy()
    for i in range(16):
        for j in (0, 8):
            if r.random() < 0.35:
                pw[i, j, :3] = (78, 112, 52)
            if r.random() < 0.35:
                pw[j, i, :3] = (88, 122, 58)
    put('paving_weeds', pw)
    # Brick paving (European), red and grey in a herring-bone-ish bond.
    pr = np.zeros((16, 16, 4))
    pr[..., 3] = 255
    pr[..., :3] = (96, 90, 84)
    for row in range(4):
        for k in range(2):
            x0 = k * 8 + (row % 2) * 4
            c = np.array((150, 84, 64) if (row + k) % 3 else (128, 122, 116), float) * (1 + r.normal(0, 0.07))
            for y in range(row * 4, row * 4 + 3):
                for x in range(x0, x0 + 7):
                    pr[y, x % 16, :3] = c + r.normal(0, 4)
    put('paving_brick', pr)
    # Concrete sidewalk slabs (American), expansion joints.
    pc = base_layer(r, (168, 166, 160), 4, 0.12)
    pc[0, :, :3] *= 0.72
    pc[:, 0, :3] *= 0.72
    pits(pc, r, 6)
    put('walk_concrete', pc)
    # The Soviet pavement: light asphalt, cracked.
    wa = asphalt(r, 92)
    put('walk_asphalt', wa)
    wc = asphalt(r, 92)
    crack(wc, r)
    for x in range(16):
        if r.random() < 0.3:
            wc[int(r.integers(0, 16)), x, :3] = (84, 116, 56)
    put('walk_asphalt_crack', wc)
    # Sand-coloured tiles (desert towns).
    ps = base_layer(r, (206, 186, 146), 4, 0.12)
    for i in range(16):
        if i % 8 == 0:
            ps[i, :, :3] *= 0.8
            ps[:, i, :3] *= 0.8
    put('paving_sand', ps)
    # 1.50: tram rails set in the asphalt (two steel rails, the grooves beside them), a car park's bay.
    tr = asphalt(r)
    for x in (3, 12):
        tr[:, x, :3] = (150, 150, 152)
        tr[:, x + 1, :3] = (110, 108, 106)
        tr[:, x - 1, :3] = (36, 36, 38)
    put('tram_track', tr)
    pk = asphalt(r)
    pk[:, 0, :3] = pk[:, 0, :3] * 0.3 + np.array((220, 218, 208)) * 0.7
    put('parking_x', pk)
    pz = asphalt(r)
    pz[0, :, :3] = pz[0, :, :3] * 0.3 + np.array((220, 218, 208)) * 0.7
    put('parking_z', pz)
    # The tar of a flat roof: black, patched, bubbled, gravel.
    rt = base_layer(r, (52, 50, 50), 4, 0.2)
    for _ in range(20):
        y, x = r.integers(0, 16, 2)
        rt[y, x, :3] = (96, 92, 86)
    rt[3:7, 2:12, :3] *= 0.85
    put('roof_tar', rt)
    return made


def write_models_states(name, looks_made, vanilla):
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(STATES, exist_ok=True)
    os.makedirs(LOOT, exist_ok=True)
    base = 'airdefense:block/city/' + name + '_a'
    for look in looks_made:
        block = 'w_' + name + '_' + look
        tex = 'airdefense:block/city/' + name + '_' + look
        if look in FACING:
            model = {'parent': 'minecraft:block/cube', 'textures': {
                'particle': base, 'north': tex, 'south': base, 'east': base, 'west': base, 'up': base, 'down': base}}
            states = {'variants': {}}
            for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                v = {'model': 'airdefense:block/city/' + block}
                if y:
                    v['y'] = y
                states['variants']['facing=' + f] = v
        else:
            model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex}}
            states = {'variants': {'': {'model': 'airdefense:block/city/' + block}}}
        with open(os.path.join(MODELS, block + '.json'), 'w') as fh:
            json.dump(model, fh)
        with open(os.path.join(STATES, block + '.json'), 'w') as fh:
            json.dump(states, fh)
        loot(block, vanilla)


def loot(block, vanilla):
    with open(os.path.join(LOOT, block + '.json'), 'w') as fh:
        json.dump({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': vanilla}],
                                                         'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}, fh)


def main():
    names = []
    for d in DYES:
        made = panel_set('panel_' + d, CONCRETE[d])
        write_models_states('panel_' + d, made, 'minecraft:' + d + '_concrete')
        names.append('panel_' + d)
    for d in DYES + ['plain']:
        made = plaster_set('plaster_' + d, PLASTER[d])
        write_models_states('plaster_' + d, made, 'minecraft:' + (d + '_terracotta' if d != 'plain' else 'terracotta'))
        names.append('plaster_' + d)
    made = brick_set('brick', (146, 70, 52), (148, 138, 124), 0.12)
    write_models_states('brick', made, 'minecraft:bricks')
    made = brick_set('silicate', (204, 200, 186), (176, 172, 160), 0.1)
    write_models_states('silicate', made, 'minecraft:smooth_quartz')
    made = plaster_set('sandstone', (214, 196, 150))
    write_models_states('sandstone', made, 'minecraft:smooth_sandstone')
    names += ['brick', 'silicate', 'sandstone']
    ground = ground_set()
    drops = {'asphalt': 'minecraft:gray_concrete', 'road': 'minecraft:white_concrete', 'kerb': 'minecraft:smooth_stone', 'tram': 'minecraft:gray_concrete', 'parking': 'minecraft:gray_concrete',
             'roof': 'minecraft:gray_concrete'}
    for g in ground:
        os.makedirs(MODELS, exist_ok=True)
        with open(os.path.join(MODELS, 'g_' + g + '.json'), 'w') as fh:
            json.dump({'parent': 'minecraft:block/cube_all', 'textures': {'all': 'airdefense:block/city/' + g}}, fh)
        with open(os.path.join(STATES, 'g_' + g + '.json'), 'w') as fh:
            json.dump({'variants': {'': {'model': 'airdefense:block/city/g_' + g}}}, fh)
        loot('g_' + g, drops.get(g.split('_')[0], 'minecraft:polished_andesite'))
    print('walls', len(names), 'x', len(LOOKS), '; ground', len(ground), ground)


if __name__ == '__main__':
    main()
