#!/usr/bin/env python3
"""
1.35 "The look of the towns": the street furniture, drawn and modelled here instead of put together from ordinary
blocks. Poles (galvanised, black, cast-iron green, Soviet concrete), street lamps (a modern LED arm, the Soviet "cobra",
a European lantern, a park globe), traffic lights (two heads for the two streets of a crossing, the lights changing
in turn), road signs, benches, bins, bus stops (a glass shelter, a Soviet concrete one with a mosaic), a fire hydrant,
letter boxes, telephone booths, an advertising column, bollards, planters, a bike rack, a manhole cover, a newspaper
kiosk, a billboard and a drinks machine.

Writes the textures (block/street/*), the block models (block/street/*), the blockstates and the item definitions.
Models: units of 1/16 block, x east, y up, z south; "north" models face north (their front towards -z) and the
blockstates turn them. python3 tools/street/make_street.py
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense')
TEXDIR = os.path.join(ROOT, 'textures', 'block', 'street')
MODELDIR = os.path.join(ROOT, 'models', 'block', 'street')
rng = np.random.default_rng(1350)


# ----------------------------------------------------------------------------------------------------------------
# Textures

def save(name, arr, mcmeta=None):
    os.makedirs(TEXDIR, exist_ok=True)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    img.save(os.path.join(TEXDIR, name + '.png'))
    if mcmeta is not None:
        with open(os.path.join(TEXDIR, name + '.png.mcmeta'), 'w') as fh:
            json.dump(mcmeta, fh)


def paint(rgb, noise=4.0, size=16, alpha=255):
    a = np.zeros((size, size, 4))
    a[..., :3] = np.array(rgb, float) + rng.normal(0, noise, (size, size, 1))
    a[..., 3] = alpha
    return a


def wood(rgb, size=16):
    a = paint(rgb, 3, size)
    for y in range(size):
        a[y, :, :3] *= 0.92 + 0.08 * math.sin(y * 1.7)
    for _ in range(size // 2):
        y = rng.integers(0, size)
        x0 = rng.integers(0, size - 4)
        a[y, x0:x0 + rng.integers(2, 6), :3] *= 0.8
    a[0, :, :3] *= 0.75
    a[-1, :, :3] *= 0.75
    return a


def concrete(size=16):
    a = paint((150, 148, 140), 9, size)
    for _ in range(size):
        y, x = rng.integers(0, size, 2)
        a[y, x, :3] *= 0.7
    return a


def glass(size=16):
    a = np.zeros((size, size, 4))
    a[..., :3] = (190, 220, 235)
    a[..., 3] = 70
    for k in range(size):
        if 2 <= k < size - 3:
            a[size - 1 - k, k, 3] = 120
            a[size - 1 - k, k, :3] = (235, 245, 250)
    a[0, :, 3] = a[-1, :, 3] = a[:, 0, 3] = a[:, -1, 3] = 150
    return a


def lens(rgb, lit):
    a = np.zeros((16, 16, 4))
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2)
    base = np.array(rgb, float) * (1.0 if lit else 0.18)
    a[..., :3] = base
    if lit:
        hot = np.clip(1.4 - d / 7.0, 0, 1)[..., None]
        a[..., :3] = base * 0.75 + (np.minimum(base * 1.6, 255) - base * 0.75) * hot
    a[..., 3] = np.where(d <= 7.6, 255, 0)
    rim = (d > 6.6) & (d <= 7.6)
    a[rim, :3] = (20, 20, 22)
    return a


def lens_strip(rgb):
    """Two frames, off on top and lit below (the animation picks)."""
    return np.concatenate([lens(rgb, False), lens(rgb, True)], axis=0)


CYCLE = 240  # ticks: green 100, yellow 20, red 120 - the other street the other way round


def lens_anim(color, phase):
    # Phase a: green 0-100, yellow 100-120, red 120-240. Phase b: red 0-120, green 120-220, yellow 220-240.
    if phase == 'a':
        spans = {'green': [(1, 100), (0, 140)], 'yellow': [(0, 100), (1, 20), (0, 120)], 'red': [(0, 120), (1, 120)]}
    else:
        spans = {'green': [(0, 120), (1, 100), (0, 20)], 'yellow': [(0, 220), (1, 20)], 'red': [(1, 120), (0, 120)]}
    return {'animation': {'interpolate': False, 'frames': [{'index': i, 'time': t} for i, t in spans[color]]}}


FONT = {
    # 3x5 pixel letters for the little signs.
    'A': ['010', '101', '111', '101', '101'], 'B': ['110', '101', '110', '101', '110'], 'C': ['011', '100', '100', '100', '011'],
    'E': ['111', '100', '110', '100', '111'], 'H': ['101', '101', '111', '101', '101'], 'K': ['101', '110', '100', '110', '101'],
    'O': ['010', '101', '101', '101', '010'], 'P': ['110', '101', '110', '100', '100'], 'S': ['011', '100', '010', '001', '110'],
    'T': ['111', '010', '010', '010', '010'], 'Y': ['101', '101', '010', '010', '010'], 'M': ['101', '111', '111', '101', '101'],
    'L': ['100', '100', '100', '100', '111'], 'N': ['101', '111', '111', '111', '101'], 'I': ['111', '010', '010', '010', '111'],
    '4': ['101', '101', '111', '001', '001'], '0': ['111', '101', '101', '101', '111'], '1': ['010', '110', '010', '010', '111'],
    # Cyrillic.
    'П': ['111', '101', '101', '101', '101'], 'Ч': ['101', '101', '011', '001', '001'], 'Ь': ['100', '100', '110', '101', '110'],
    'Ч2': ['101', '101', '011', '001', '001'], 'Ф': ['010', '111', '111', '010', '010'], 'Я': ['011', '101', '011', '101', '101'],
    'Г': ['111', '100', '100', '100', '100'], 'Д': ['011', '011', '011', '111', '101'], 'З': ['110', '001', '010', '001', '110'],
    'Л': ['011', '101', '101', '101', '101'], 'Ш': ['101', '101', '101', '111', '111'], 'Ы': ['101', '101', '111', '111', '111'],
    ' ': ['000', '000', '000', '000', '000'],
}


def text(a, s, x, y, rgb, scale=1):
    for ch in s:
        g = FONT.get(ch, FONT[' '])
        for j, row in enumerate(g):
            for i, c in enumerate(row):
                if c == '1':
                    a[y + j * scale:y + (j + 1) * scale, x + i * scale:x + (i + 1) * scale, :3] = rgb
                    a[y + j * scale:y + (j + 1) * scale, x + i * scale:x + (i + 1) * scale, 3] = 255
        x += 4 * scale


def sign(kind):
    """A 32x32 road sign (transparent round it) and its grey back (same outline)."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    red, white, blue, yellow, black = (196, 30, 32, 255), (240, 240, 236, 255), (24, 74, 160, 255), (240, 190, 30, 255), (20, 20, 20, 255)
    if kind == 'stop':
        pts = [(10, 1), (21, 1), (30, 10), (30, 21), (21, 30), (10, 30), (1, 21), (1, 10)]
        d.polygon(pts, fill=white)
        d.polygon([(10, 3), (21, 3), (28, 10), (28, 21), (21, 28), (10, 28), (3, 21), (3, 10)], fill=red)
    elif kind == 'give_way':
        d.polygon([(1, 3), (30, 3), (15.5, 29)], fill=red)
        d.polygon([(6, 6), (25, 6), (15.5, 23)], fill=white)
    elif kind == 'crossing':
        d.rectangle([2, 2, 29, 29], fill=blue)
        d.polygon([(5, 25), (26, 25), (15.5, 6)], fill=white)
        d.rectangle([8, 22, 23, 23], fill=black)
        d.ellipse([14, 10, 17, 13], fill=black)
        d.line([(15, 13), (14, 19), (12, 22)], fill=black, width=1)
        d.line([(15, 15), (18, 18), (19, 22)], fill=black, width=1)
    elif kind == 'no_parking':
        d.ellipse([1, 1, 30, 30], fill=red)
        d.ellipse([5, 5, 26, 26], fill=blue)
        d.line([(8, 8), (23, 23)], fill=red, width=3)
    elif kind == 'speed':
        d.ellipse([1, 1, 30, 30], fill=red)
        d.ellipse([5, 5, 26, 26], fill=white)
    elif kind == 'main_road':
        d.polygon([(15.5, 1), (30, 15.5), (15.5, 30), (1, 15.5)], fill=white)
        d.polygon([(15.5, 6), (25, 15.5), (15.5, 25), (6, 15.5)], fill=yellow)
    elif kind == 'bus':
        d.rectangle([3, 1, 28, 30], fill=blue)
        d.rectangle([6, 4, 25, 27], fill=white)
    a = np.array(img).astype(float)
    if kind == 'speed':
        text(a, '40', 9, 11, (20, 20, 20), 2)
    elif kind == 'stop':
        text(a, 'STOP', 8, 13, (240, 240, 236), 1)
    elif kind == 'bus':
        # A little bus.
        a[9:20, 9:23, :3] = (24, 74, 160)
        a[11:15, 10:22, :3] = (190, 220, 240)
        a[20:22, 11:13, :3] = (20, 20, 20)
        a[20:22, 19:21, :3] = (20, 20, 20)
        text(a, 'A', 14, 22, (24, 74, 160))
    a[..., :3] += rng.normal(0, 3, (32, 32, 1)) * (a[..., 3:4] > 0)
    back = a.copy()
    back[..., :3] = np.where(a[..., 3:4] > 0, np.array([150, 152, 150]) + rng.normal(0, 4, (32, 32, 1)), 0)
    return a, back


def poster(seed, w=32, h=32, lit=False):
    """An advertisement: a coloured ground, a big shape, lines of 'text', a logo blob (no real brands)."""
    r = np.random.default_rng(seed)
    pal = [(220, 60, 50), (40, 120, 200), (250, 200, 40), (60, 170, 90), (240, 240, 235), (30, 30, 40), (230, 120, 30), (150, 60, 170)]
    bg = pal[r.integers(len(pal))]
    fg = pal[r.integers(len(pal))]
    while fg == bg:
        fg = pal[r.integers(len(pal))]
    img = Image.new('RGBA', (w, h), bg + (255,))
    d = ImageDraw.Draw(img)
    k = r.integers(3)
    if k == 0:
        d.ellipse([w * 0.15, h * 0.12, w * 0.85, h * 0.62], fill=fg + (255,))
    elif k == 1:
        d.rectangle([w * 0.1, h * 0.1, w * 0.9, h * 0.5], fill=fg + (255,))
        d.polygon([(w * 0.2, h * 0.45), (w * 0.5, h * 0.15), (w * 0.8, h * 0.45)], fill=bg + (255,))
    else:
        for i in range(4):
            d.rectangle([w * (0.1 + i * 0.2), h * (0.5 - i * 0.1), w * (0.25 + i * 0.2), h * 0.55], fill=fg + (255,))
    tc = (250, 250, 250) if sum(bg) < 400 else (30, 30, 30)
    for i in range(3):
        y = int(h * (0.68 + i * 0.09))
        d.rectangle([w * 0.12, y, w * (0.88 - 0.15 * (i % 2)), y + max(1, h // 32)], fill=tc + (255,))
    a = np.array(img).astype(float)
    a[..., :3] += r.normal(0, 3, (h, w, 1))
    if lit:
        a[..., :3] = np.minimum(255, a[..., :3] * 1.1 + 15)
    return a


def mosaic():
    """A Soviet bus stop's back wall: a mosaic of a sun, waves and birds in coloured tiles."""
    a = np.zeros((32, 32, 4))
    a[..., 3] = 255
    yy, xx = np.mgrid[0:32, 0:32]
    a[..., :3] = (60, 120, 190)
    sky = yy < 18
    a[sky, :3] = (90, 160, 210)
    d = np.sqrt((xx - 22) ** 2 + (yy - 8) ** 2)
    a[d < 6, :3] = (245, 190, 40)
    a[(d >= 6) & (d < 7.5) & ((xx + yy) % 2 == 0), :3] = (240, 140, 40)
    for k in range(3):
        wave = (yy - (20 + k * 4) - (np.sin(xx * 0.6 + k) * 1.5)).astype(int) == 0
        a[wave, :3] = (230, 240, 245)
    for bx, by in ((6, 6), (10, 9), (5, 11)):
        a[by, bx - 2:bx + 3, :3] = (30, 30, 30)
        a[by - 1, bx - 2, :3] = a[by - 1, bx + 2, :3] = (30, 30, 30)
    # Grout lines between the tiles.
    a[(xx % 4 == 0) | (yy % 4 == 0), :3] *= 0.85
    a[..., :3] += rng.normal(0, 6, (32, 32, 1))
    return a


def booth(red):
    """A telephone booth's side: the frame and the glass panes (32x32: the booth is two blocks tall)."""
    a = np.zeros((32, 32, 4))
    frame = (178, 28, 26) if red else (150, 160, 170)
    a[..., :3] = frame
    a[..., 3] = 255
    for row in range(5 if red else 3):
        for col in range(3 if red else 2):
            if red:
                x0, y0 = 4 + col * 8, 4 + row * 5
                a[y0:y0 + 4, x0:x0 + 7, :3] = (170, 200, 215)
                a[y0:y0 + 4, x0:x0 + 7, 3] = 150
            else:
                x0, y0 = 3 + col * 14, 3 + row * 9
                a[y0:y0 + 8, x0:x0 + 12, :3] = (175, 205, 220)
                a[y0:y0 + 8, x0:x0 + 12, 3] = 140
    a[28:32, :, :3] = np.array(frame) * 0.8
    a[..., :3] += rng.normal(0, 4, (32, 32, 1))
    return a


def kiosk_band():
    a = paint((30, 70, 150), 3, 32)
    text(a, 'ПЕЧАТЬ', 4, 13, (250, 250, 245), 1)
    return a


def kiosk_side():
    a = paint((235, 235, 228), 3, 32)
    a[4:20, 3:29, :3] = (150, 190, 210)
    a[4:20, 3:29, 3] = 160
    a[4:20, 15:17, :3] = (235, 235, 228)
    a[4:20, 15:17, 3] = 255
    a[22:32, :, :3] = (30, 70, 150)
    a[22:32, :, 3] = 255
    return a


def vending_front():
    a = paint((200, 30, 30), 3, 32)
    a[3:22, 3:20, :3] = (20, 20, 25)
    for row in range(4):
        for col in range(3):
            c = [(240, 60, 50), (50, 120, 220), (250, 200, 40), (70, 180, 90)][(row + col) % 4]
            a[5 + row * 4:7 + row * 4, 5 + col * 5:8 + col * 5, :3] = c
    a[4:12, 22:29, :3] = (230, 230, 220)
    a[14:16, 22:29, :3] = (20, 20, 20)
    a[24:29, 4:20, :3] = (30, 30, 30)
    return a


def manhole_top():
    a = paint((54, 57, 61), 3, 16)
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.sqrt((xx - 7.5) ** 2 + (yy - 7.5) ** 2)
    ring = (d > 5.4) & (d <= 6.6)
    a[ring, :3] = (40, 40, 42)
    cover = d <= 5.4
    a[cover, :3] = (70, 70, 72)
    a[cover & ((xx + yy) % 3 == 0), :3] = (52, 52, 54)
    return a


def bush():
    a = paint((60, 110, 50), 14, 16)
    for _ in range(30):
        y, x = rng.integers(0, 16, 2)
        a[y, x, :3] = (90, 150, 70)
    return a


def make_textures():
    save('steel', paint((150, 154, 156), 5))
    save('black', paint((36, 38, 42), 3))
    save('green', paint((34, 66, 48), 3))
    save('concrete', concrete())
    save('white', paint((228, 228, 222), 3))
    save('red', paint((186, 34, 30), 4))
    save('blue', paint((32, 80, 168), 4))
    save('yellow', paint((228, 182, 36), 4))
    save('brass', paint((176, 140, 62), 6))
    save('wood', wood((140, 96, 58)))
    save('wood_green', wood((60, 100, 70)))
    save('glass', glass())
    save('lamp_glow', paint((255, 238, 190), 2))
    save('led', paint((236, 244, 255), 2))
    save('globe', paint((246, 242, 228), 3))
    save('dark', paint((22, 22, 24), 2))
    save('rubber', paint((30, 30, 30), 3))
    save('reflect', np.concatenate([paint((230, 230, 225), 2, 16)[:8], paint((190, 30, 30), 2, 16)[:8]], axis=0))
    save('bush', bush())
    save('soil', paint((70, 50, 34), 8))
    save('manhole', manhole_top())
    for c, rgb in (('red', (255, 50, 40)), ('yellow', (255, 190, 30)), ('green', (60, 255, 120))):
        for ph in ('a', 'b'):
            save('lens_%s_%s' % (c, ph), lens_strip(rgb), lens_anim(c, ph))
    for k in ('stop', 'give_way', 'crossing', 'no_parking', 'speed', 'main_road', 'bus'):
        f, b = sign(k)
        save('sign_' + k, f)
        save('sign_' + k + '_back', b)
    for i in range(4):
        save('poster_%d' % i, poster(100 + i))
        save('poster_lit_%d' % i, poster(200 + i, lit=True))
    # The billboard: one wide picture cut into three.
    wide = poster(300, 96, 32, lit=True)
    for i in range(3):
        save('billboard_%d' % i, wide[:, i * 32:(i + 1) * 32])
    save('mosaic', mosaic())
    save('booth_red', booth(True))
    save('booth_soviet', booth(False))
    save('kiosk_band', kiosk_band())
    save('kiosk_side', kiosk_side())
    save('vending', vending_front())
    a = paint((228, 228, 222), 3, 16)
    text(a, 'TAXI', 0, 6, (30, 30, 30))
    save('booth_sign_red', paint((178, 28, 26), 3, 16))
    b = paint((240, 240, 236), 2, 32)
    text(b, 'TEЛEФOH', 1, 13, (30, 60, 140), 1)
    save('booth_sign', b)
    m = paint((32, 80, 168), 4, 32)
    # A white envelope over the word.
    m[5:13, 10:22, :3] = (240, 240, 236)
    for k in range(6):
        m[5 + k, 10 + k, :3] = (32, 80, 168)
        m[5 + k, 21 - k, :3] = (32, 80, 168)
    text(m, 'ПOЧTA', 6, 16, (240, 240, 236))
    m[24:26, 9:23, :3] = (20, 30, 60)
    save('mail_soviet', m)
    y = paint((228, 182, 36), 4, 16)
    y[5:11, 4:12, :3] = (30, 30, 30)
    y[6:10, 5:11, :3] = (228, 182, 36)
    save('mail_euro', y)


# ----------------------------------------------------------------------------------------------------------------
# Models

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def box(frm, to, tex, faces=None, uv=None, emit=0, rot=None):
    """An element. tex: one texture key for all faces, or a dict face -> key (faces missing from it are left out).
    uv: face -> explicit uv (else the face's size, capped at 16 - plain paint stretches)."""
    size = [to[i] - frm[i] for i in range(3)]
    out = {}
    names = list(tex.keys()) if isinstance(tex, dict) else list(faces or FACES)
    for f in names:
        t = tex[f] if isinstance(tex, dict) else tex
        if uv and f in uv:
            u = uv[f]
        else:
            a, b = DIMS[f]
            u = [0, 0, round(min(16, max(0.01, size[a])), 4), round(min(16, max(0.01, size[b])), 4)]
        out[f] = {'texture': '#' + t, 'uv': u}
    e = {'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'faces': out}
    if emit:
        e['light_emission'] = emit
    if rot:
        e['rotation'] = rot
    return e


FULL = [0, 0, 16, 16]


def model(name, elements, gui=0.625, extra_tex=None, gui_y=0.0):
    textures = {}
    for e in elements:
        for f in e['faces'].values():
            k = f['texture'][1:]
            textures[k] = (extra_tex or {}).get(k, 'airdefense:block/street/' + k)
    textures['particle'] = textures[next(iter(textures))]
    for e in elements:
        for v in e['from'] + e['to']:
            assert -16 <= v <= 32, (name, e)
    data = {
        'parent': 'minecraft:block/block',
        'ambientocclusion': False,
        'textures': textures,
        'elements': elements,
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, gui_y, 0], 'scale': [gui] * 3},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25] * 3},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [gui * 0.8] * 3},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [gui * 0.6] * 3},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [gui * 0.64] * 3},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [gui * 0.64] * 3},
        },
    }
    os.makedirs(MODELDIR, exist_ok=True)
    with open(os.path.join(MODELDIR, name + '.json'), 'w') as fh:
        json.dump(data, fh, indent=1)


def post(x0, x1, y0, y1, z0, z1, tex):
    return box((x0, y0, z0), (x1, y1, z1), tex)


# --- Poles: a shaft one block tall; the bottom one has its foot ---

def poles():
    for name, tex, w, foot in (('pole_steel', 'steel', 2, 'plate'), ('pole_black', 'black', 2.4, 'cone'),
                               ('pole_green', 'green', 3, 'ornate'), ('pole_concrete', 'concrete', 4, 'plain')):
        a = 8 - w / 2
        b = 8 + w / 2
        shaft = [box((a, 0, a), (b, 16, b), tex, faces=('north', 'south', 'east', 'west'))]
        model(name, shaft, gui=0.8)
        f = list(shaft)
        if foot == 'plate':
            f += [box((5, 0, 5), (11, 1, 11), tex), box((6, 1, 6), (10, 3, 10), tex)]
        elif foot == 'cone':
            f += [box((5.5, 0, 5.5), (10.5, 2, 10.5), tex), box((6.3, 2, 6.3), (9.7, 4, 9.7), tex)]
        elif foot == 'ornate':
            f += [box((4, 0, 4), (12, 2, 12), tex), box((5, 2, 5), (11, 4, 11), tex), box((5.8, 4, 5.8), (10.2, 7, 10.2), tex),
                  box((5.3, 7, 5.3), (10.7, 8, 10.7), tex), box((6, 12, 6), (10, 13, 10), tex)]
        else:
            f += [box((5, 0, 5), (11, 1.5, 11), tex)]
        model(name + '_bottom', f, gui=0.8)


# --- Street lamps: the head on top of a pole; the arm reaches out over the street (to the north) ---

def lamps():
    # Modern LED arm (steel).
    e = [box((7, 0, 7), (9, 6, 9), 'steel'),
         box((7.4, 5, -11), (8.6, 6.4, 8), 'steel'),
         box((5, 4.2, -16), (11, 5.6, -7), 'steel'),
         box((5.5, 3.9, -15.5), (10.5, 4.2, -7.5), {'down': 'led'}, uv={'down': FULL}, emit=15)]
    model('lamp_modern', e, gui=0.45)
    # Soviet "cobra": a concrete pole's steel arm curving up and out, the teardrop head tilted down.
    rot = {'origin': [8, 10, 8], 'axis': 'x', 'angle': 22.5}
    e = [box((6, 0, 6), (10, 3, 10), 'concrete'),
         box((7.4, 2, 7.4), (8.6, 11, 8.6), 'steel'),
         box((7.4, 9.5, -7), (8.6, 10.7, 8), 'steel', rot=rot),
         box((5.6, 8.0, -16), (10.4, 11.4, -7), 'steel', rot=rot),
         box((6.1, 7.7, -15.5), (9.9, 8.0, -7.5), {'down': 'lamp_glow'}, uv={'down': FULL}, emit=15, rot=rot)]
    model('lamp_cobra', e, gui=0.45)
    # European lantern on a green cast-iron pole.
    e = [box((6.5, 0, 6.5), (9.5, 2, 9.5), 'green'),
         box((5, 2, 5), (11, 3, 11), 'black'),
         box((5.7, 3, 5.7), (10.3, 10, 10.3), 'lamp_glow', emit=15),
         box((5.2, 3, 5.2), (6.0, 10, 6.0), 'black'), box((10.0, 3, 5.2), (10.8, 10, 6.0), 'black'),
         box((5.2, 3, 10.0), (6.0, 10, 10.8), 'black'), box((10.0, 3, 10.0), (10.8, 10, 10.8), 'black'),
         box((4.5, 10, 4.5), (11.5, 11, 11.5), 'black'), box((5.5, 11, 5.5), (10.5, 12, 10.5), 'black'),
         box((6.5, 12, 6.5), (9.5, 13, 9.5), 'black'), box((7.5, 13, 7.5), (8.5, 15, 8.5), 'black')]
    model('lamp_lantern', e, gui=0.9)
    # A park globe.
    e = [box((7, 0, 7), (9, 4, 9), 'black'), box((6, 3.5, 6), (10, 4.5, 10), 'black'),
         box((4.5, 4.5, 4.5), (11.5, 11.5, 11.5), 'globe', emit=15),
         box((5.5, 3.8, 5.5), (10.5, 12.2, 10.5), 'globe', emit=15)]
    model('lamp_globe', e, gui=0.9)


# --- Traffic lights: two heads, one for each street of the crossing (north and east here), lights changing in turn ---

def turn_east(e):
    """The same element turned to face east (90 degrees clockwise seen from above): (x, z) -> (16 - z, x)."""
    f, t = e['from'], e['to']
    out = dict(e)
    out['from'] = [16 - t[2], f[1], f[0]]
    out['to'] = [16 - f[2], t[1], t[0]]
    names = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}
    out['faces'] = {names[k]: v for k, v in e['faces'].items()}
    if 'rotation' in e:
        out.pop('rotation')
    return out


def head(c, phase):
    """A traffic light head facing north, its middle at x = c: three 5-pixel lenses under hoods in a black housing
    1.6 blocks tall on a white-edged backplate (seen from down the street), and the bracket to the pole."""
    plate = {f: 'black' for f in FACES}
    plate['north'] = 'white'
    els = [box((c - 4.5, 3, 5), (c + 4.5, 30, 5.5), plate),
           box((c - 3.5, 4, 2), (c + 3.5, 29, 5), 'black'),
           box((c - 1, 14, 5.5), (c + 1, 18, 7.5), 'black')]
    for col, y in (('red', 21.5), ('yellow', 14), ('green', 6.5)):
        els.append(box((c - 2.5, y, 1.7), (c + 2.5, y + 5, 2), {'north': 'lens_%s_%s' % (col, phase)}, uv={'north': FULL}, emit=15))
        els.append(box((c - 3, y + 5, 0), (c + 3, y + 5.6, 2), 'black'))
        els.append(box((c - 3, y + 2.5, 0.5), (c - 2.6, y + 5, 2), 'black'))
        els.append(box((c + 2.6, y + 2.5, 0.5), (c + 3, y + 5, 2), 'black'))
    return els


def traffic():
    # The pole up the middle; one head for each street of the crossing (north, and east turned), lights changing in turn.
    e = [box((7, 0, 7), (9, 30, 9), 'black'), box((6.5, 30, 6.5), (9.5, 31, 9.5), 'black')]
    e += head(7, 'a') + [turn_east(x) for x in head(9.5, 'b')]
    model('traffic_light', e, gui=0.6)


# --- Road signs: a plate on the top of a thin pole ---

def signs():
    for k in ('stop', 'give_way', 'crossing', 'no_parking', 'speed', 'main_road', 'bus'):
        e = [box((7.4, 0, 7.4), (8.6, 4, 8.6), 'steel', faces=('north', 'south', 'east', 'west')),
             box((1.5, 2.5, 7.85), (14.5, 15.5, 7.86), {'north': 'sign_' + k}, uv={'north': FULL}),
             box((1.5, 2.5, 7.95), (14.5, 15.5, 7.96), {'south': 'sign_' + k + '_back'}, uv={'south': [16, 0, 0, 16]}),
             box((7.4, 4, 8.0), (8.6, 14, 8.6), 'steel', faces=('south', 'east', 'west'))]
        model('sign_' + k, e, gui=0.9)


# --- Benches: one and a half blocks long, people sit facing north ---

def benches():
    def bench(name, leg, slat, back=True, chunky=False):
        e = []
        for x in (-2.5, 16.5):
            if chunky:
                e.append(box((x - 1, 0, 4), (x + 3, 7, 12), leg))
            else:
                e.append(box((x, 0, 4), (x + 2, 1, 6), leg))
                e.append(box((x, 0, 10), (x + 2, 1, 12), leg))
                e.append(box((x, 1, 4.5), (x + 2, 7, 5.5), leg))
                e.append(box((x, 1, 10.5), (x + 2, 7, 11.5), leg))
                e.append(box((x, 6, 4), (x + 2, 7, 12), leg))
            if back:
                e.append(box((x, 7, 11), (x + 2, 15, 12), leg))
        for z0, z1 in ((4, 6), (6.6, 8.6), (9.2, 11.2)):
            e.append(box((-4, 7, z0), (20, 8, z1), slat))
        if back:
            e.append(box((-4, 9.5, 11.6), (20, 11.5, 12.6), slat))
            e.append(box((-4, 12.5, 11.8), (20, 14.5, 12.8), slat))
        model(name, e, gui=0.6)

    bench('bench_park', 'black', 'wood')
    bench('bench_soviet', 'concrete', 'wood_green', chunky=True)
    bench('bench_modern', 'steel', 'wood', back=False)


# --- Bins ---

def bins():
    model('bin_soviet', [box((5.5, 0, 5.5), (10.5, 2, 10.5), 'concrete'), box((4.5, 2, 4.5), (11.5, 4, 11.5), 'concrete'),
                         box((3.8, 4, 3.8), (12.2, 9, 12.2), 'concrete'), box((3.3, 9, 3.3), (12.7, 10, 12.7), 'concrete'),
                         box((4.3, 9.95, 4.3), (11.7, 10.05, 11.7), {'up': 'dark'}, uv={'up': FULL})], gui=0.9)
    model('bin_modern', [box((4.5, 0, 4.5), (11.5, 10.5, 11.5), 'black'), box((4, 10.5, 4), (12, 12, 12), 'black'),
                         box((6, 12, 6), (10, 12.6, 10), 'steel'), box((6.5, 8, 4.4), (9.5, 9.5, 4.5), {'north': 'dark'}, uv={'north': FULL})],
          gui=0.9)
    model('bin_euro', [box((7.4, 0, 9), (8.6, 12, 10.2), 'steel'), box((5, 5, 5), (11, 12, 9), 'green'),
                       box((4.7, 12, 4.7), (11.3, 12.6, 9.3), 'green'), box((5.5, 11.9, 5.5), (10.5, 12.05, 8.5), {'up': 'dark'}, uv={'up': FULL})],
          gui=0.9)


# --- Bus stops: three blocks long, open to the street (north) ---

def bus_stops():
    e = []
    for x in (-15.5, 30.5):
        for z in (2.5, 14.5):
            e.append(box((x, 0, z), (x + 1, 29, z + 1), 'steel'))
    e.append(box((-16, 29, 0), (32, 30.5, 16), 'steel'))
    e.append(box((-12, 28.7, 5), (28, 29, 9), {'down': 'led'}, uv={'down': FULL}, emit=15))
    for i in range(3):
        x0 = -15 + i * 15.5
        e.append(box((x0, 1.5, 14.9), (x0 + 15, 28, 15.1), {'north': 'glass', 'south': 'glass'}, uv={'north': FULL, 'south': FULL}))
    e.append(box((-15.6, 1.5, 3.5), (-15.4, 28, 14.5), {'east': 'glass', 'west': 'glass'}, uv={'east': FULL, 'west': FULL}))
    e.append(box((-15.5, 0.5, 14.5), (31.5, 1.5, 15.5), 'steel'))
    # The bench inside, the lit advert at the east end, the stop's sign on the roof edge.
    e += [box((-8, 6, 10.5), (14, 7, 14), 'wood'), box((-6, 0, 12), (-5, 6, 13), 'steel'), box((11, 0, 12), (12, 6, 13), 'steel')]
    e.append(box((30.6, 1.5, 3.5), (31.4, 27, 14.5), {'east': 'poster_lit_0', 'west': 'poster_lit_1'}, uv={'east': FULL, 'west': FULL},
                 emit=12))
    e.append(box((30.4, 1.5, 3.5), (31.6, 27, 3.9), 'steel'))
    e.append(box((2, 30.5, 0.2), (14, 32, 1.2), {'north': 'sign_bus', 'south': 'sign_bus'}, uv={'north': [0, 4, 16, 12], 'south': [0, 4, 16, 12]}))
    model('bus_stop_modern', e, gui=0.32)
    # Soviet: concrete walls, a slab roof, a mosaic on the back wall, a concrete bench.
    e = [box((-16, 0, 14), (32, 28, 16), {'south': 'concrete', 'up': 'concrete', 'down': 'concrete', 'east': 'concrete', 'west': 'concrete'})]
    for i in range(3):
        x0 = -16 + i * 16
        e.append(box((x0, 0.5, 13.9), (x0 + 16, 27.5, 14), {'north': 'mosaic'}, uv={'north': [i * 16 / 3, 0, (i + 1) * 16 / 3, 16]}))
    e += [box((-16, 0, 2), (-13, 28, 14), 'concrete'), box((29, 0, 2), (32, 28, 14), 'concrete'),
          box((-16, 28, -1), (32, 31, 16), 'concrete'), box((-16, 31, -1), (32, 31.5, 0), 'concrete'),
          box((-10, 0, 10), (-7, 6, 13), 'concrete'), box((23, 0, 10), (26, 6, 13), 'concrete'), box((-11, 6, 9.5), (27, 8, 13.5), 'wood_green')]
    model('bus_stop_soviet', e, gui=0.32)


# --- Smaller things ---

def small():
    model('hydrant', [box((5, 0, 5), (11, 1, 11), 'red'), box((6, 1, 6), (10, 10, 10), 'red'), box((5.5, 10, 5.5), (10.5, 11, 10.5), 'red'),
                      box((6.5, 11, 6.5), (9.5, 12.5, 9.5), 'red'), box((7.5, 12.5, 7.5), (8.5, 13.2, 8.5), 'brass'),
                      box((4, 6, 7), (6, 8, 9), 'red'), box((10, 6, 7), (12, 8, 9), 'red'), box((7, 6, 4), (9, 8, 6), 'brass')], gui=1.0)
    model('mailbox_us', [box((4, 0, 4), (5, 5, 5), 'blue'), box((11, 0, 4), (12, 5, 5), 'blue'), box((4, 0, 11), (5, 5, 12), 'blue'),
                         box((11, 0, 11), (12, 5, 12), 'blue'), box((3.5, 5, 3.5), (12.5, 14, 12.5), 'blue'), box((4.5, 14, 3.5), (11.5, 15.5, 12.5), 'blue'),
                         box((5.5, 15.5, 3.5), (10.5, 16, 12.5), 'blue'), box((6, 11, 3.3), (10, 12, 3.5), 'steel')], gui=0.9)
    model('mailbox_euro', [box((7, 0, 9), (9, 9, 11), 'steel'), box((4, 8, 5), (12, 16, 9), {'north': 'mail_euro', 'south': 'yellow', 'east': 'yellow',
                           'west': 'yellow', 'up': 'yellow', 'down': 'yellow'}, uv={'north': FULL}), box((5, 13, 4.7), (11, 14, 5), 'dark')], gui=0.9)
    model('mailbox_soviet', [box((7, 0, 9), (9, 8, 11), 'steel'), box((4, 7, 5), (12, 16, 9), {'north': 'mail_soviet', 'south': 'blue', 'east': 'blue',
                             'west': 'blue', 'up': 'blue', 'down': 'blue'}, uv={'north': FULL}), box((3.6, 16, 4.6), (12.4, 17, 9.4), 'blue')], gui=0.9)
    model('bollard', [box((6.5, 0, 6.5), (9.5, 8, 9.5), 'steel'), box((6.4, 6, 6.4), (9.6, 7.5, 9.6), 'reflect', uv={f: [0, 0, 16, 16] for f in FACES}),
                      box((6, 8, 6), (10, 9, 10), 'steel')], gui=1.0)
    model('planter', [box((1, 0, 1), (15, 7, 15), 'concrete'), box((2, 6.9, 2), (14, 7, 14), {'up': 'soil'}, uv={'up': FULL}),
                      box((2.5, 7, 2.5), (13.5, 12, 13.5), 'bush'), box((4, 12, 4), (12, 14, 12), 'bush')], gui=0.8)
    e = []
    for x in (1.5, 7, 12.5):
        e += [box((x, 0, 3), (x + 1, 9, 4), 'steel'), box((x, 0, 12), (x + 1, 9, 13), 'steel'), box((x, 9, 3), (x + 1, 10, 13), 'steel')]
    e.append(box((1, 0, 7.5), (14.5, 1, 8.5), 'steel'))
    model('bike_rack', e, gui=0.9)
    model('manhole', [box((0, 0, 0), (16, 16, 16), {'up': 'manhole', 'down': 'asphalt', 'north': 'asphalt', 'south': 'asphalt', 'east': 'asphalt',
                                                      'west': 'asphalt'}, uv={f: FULL for f in FACES})], extra_tex={'asphalt': 'minecraft:block/gray_concrete'})
    # Telephone booths: two blocks tall.
    for name, tex, roof, signtex in (('booth_red', 'booth_red', 'red', 'booth_sign_red'), ('booth_soviet', 'booth_soviet', 'steel', 'booth_sign')):
        sides = {f: tex for f in ('north', 'south', 'east', 'west')}
        e = [box((1, 0, 1), (15, 28, 15), sides, uv={f: [0, 0, 16, 16] for f in sides}),
             box((1.2, 27.9, 1.2), (14.8, 28, 14.8), {'down': 'lamp_glow'}, uv={'down': FULL}, emit=10),
             box((0.5, 28, 0.5), (15.5, 30, 15.5), {'north': signtex, 'south': signtex, 'east': signtex, 'west': signtex, 'up': roof, 'down': roof},
                 uv={f: [0, 0, 16, 16] for f in FACES}, emit=8),
             box((1.5, 30, 1.5), (14.5, 31, 14.5), roof), box((5, 31, 5), (11, 32, 11), roof)]
        model(name, e, gui=0.45, gui_y=-3)
    # The advertising column (Litfaßsäule): two crossed slabs make it eight-sided.
    e = []
    for a, b in (((2.5, 5), (13.5, 11)), ((5, 2.5), (11, 13.5))):
        e.append(box((a[0], 0, a[1]), (b[0], 2, b[1]), 'green'))
        e.append(box((a[0] + 0.3, 2, a[1] + 0.3), (b[0] - 0.3, 26, b[1] - 0.3), {'north': 'poster_2', 'south': 'poster_3', 'east': 'poster_0', 'west': 'poster_1'},
                     uv={f: FULL for f in ('north', 'south', 'east', 'west')}))
        e.append(box((a[0], 26, a[1]), (b[0], 28, b[1]), 'green'))
    e += [box((4.5, 28, 4.5), (11.5, 30, 11.5), 'green'), box((6.5, 30, 6.5), (9.5, 31.5, 9.5), 'green')]
    model('advert_column', e, gui=0.45, gui_y=-3)
    # The newspaper kiosk: a box a block and a half across, a lit band with its name.
    e = [box((-4, 0, -4), (20, 4, 20), 'blue'),
         box((-4, 4, -4), (20, 20, 20), {f: 'kiosk_side' for f in ('north', 'south', 'east', 'west')}, uv={f: [0, 0, 16, 10] for f in FACES}),
         box((-5, 20, -5), (21, 25, 21), {f: 'kiosk_band' for f in ('north', 'south', 'east', 'west')}, uv={f: [0, 4, 16, 12] for f in FACES}, emit=9),
         box((-5, 25, -5), (21, 26.5, 21), 'blue'), box((-6, 10, -6), (22, 11, -4), 'white')]
    model('kiosk', e, gui=0.4)
    # The drinks machine.
    model('vending', [box((2, 0, 4), (14, 28, 14), {'north': 'vending', 'south': 'red', 'east': 'red', 'west': 'red', 'up': 'red', 'down': 'red'},
                              uv={'north': FULL}, emit=0),
                      box((3, 12, 3.9), (13, 27, 4), {'north': 'vending'}, uv={'north': [1, 0.5, 13, 14]}, emit=11)], gui=0.5, gui_y=-3)
    # The billboard: put a block up, its legs reach down to the ground.
    e = [box((-12, -16, 7.2), (-10, 4, 8.8), 'steel'), box((26, -16, 7.2), (28, 4, 8.8), 'steel')]
    for i in range(3):
        x0 = -16 + i * 16
        e.append(box((x0, 4, 7), (x0 + 16, 28, 8), {'north': 'billboard_%d' % i, 'south': 'steel', 'up': 'steel', 'down': 'steel'},
                     uv={'north': [0, 0, 16, 16]}, emit=10))
    e += [box((-16, 28, 6), (32, 29, 9), 'steel'), box((-16, 3, 6), (32, 4, 9), 'steel')]
    model('billboard', e, gui=0.3)


# --- 1.36: playgrounds, sports grounds, the stadium ---

def play_textures():
    save('play_yellow', paint((236, 186, 30), 4))
    save('play_blue', paint((40, 110, 200), 4))
    save('play_orange', paint((230, 110, 30), 4))
    save('rubber_green', paint((70, 140, 70), 6))
    n = np.zeros((16, 16, 4))
    for k in range(0, 16, 3):
        n[k, :, :3] = 235
        n[k, :, 3] = 230
        n[:, k, :3] = 235
        n[:, k, 3] = 230
    save('net', n)
    b = paint((240, 240, 236), 2)
    b[0, :, :3] = b[-1, :, :3] = b[:, 0, :3] = b[:, -1, :3] = (200, 40, 30)
    b[8:13, 5:11, :3] = (200, 40, 30)
    b[9:12, 6:10, :3] = (240, 240, 236)
    save('backboard', b)


def play():
    play_textures()
    sand = {'sand': 'minecraft:block/sand'}
    # Swings: a blue frame, two seats on chains.
    e = [box((-10, 0, 7), (-8, 30, 9), 'play_blue'), box((24, 0, 7), (26, 30, 9), 'play_blue'), box((-10, 28, 7), (26, 30, 9), 'play_blue'),
         box((-11, 0, 1), (-7, 1, 15), 'play_blue'), box((23, 0, 1), (27, 1, 15), 'play_blue')]
    for x0 in (-4, 12):
        e += [box((x0, 7, 5), (x0 + 8, 8, 11), 'wood'), box((x0 + 0.5, 8, 7.8), (x0 + 1, 28, 8.2), 'steel'),
              box((x0 + 7, 8, 7.8), (x0 + 7.5, 28, 8.2), 'steel')]
    model('swing', e, gui=0.4)
    # A slide: a ladder at the back, a platform with rails, the chute down to the front.
    e = [box((2, 19, 20), (14, 21, 30), 'wood')]
    for x, z in ((2, 20), (12, 20), (2, 28), (12, 28)):
        e.append(box((x, 0, z), (x + 2, 28, z + 2), 'red'))
    e += [box((2, 26, 20), (14, 28, 22), 'red'), box((2, 26, 28), (14, 28, 30), 'red'), box((2, 24, 22), (3, 25, 28), 'red'),
          box((13, 24, 22), (14, 25, 28), 'red'), box((3, 28, 20), (13, 30, 30), 'play_yellow')]
    for y in range(3, 19, 4):
        e.append(box((4, y, 30), (12, y + 1, 31), 'steel'))
    e += [box((4, 0, 30), (5, 21, 31), 'steel'), box((11, 0, 30), (12, 21, 31), 'steel')]
    rot = {'origin': [8, 20, 20], 'axis': 'x', 'angle': -30}
    e += [box((3.5, 19, -16), (12.5, 20, 20), 'play_yellow', rot=rot), box((3, 19, -16), (3.5, 22, 20), 'play_orange', rot=rot),
          box((12.5, 19, -16), (13, 22, 20), 'play_orange', rot=rot)]
    model('slide', e, gui=0.4)
    # A sandbox: a wooden frame two blocks across, sand inside, seats on the corners.
    e = [box((-7, 0, -7), (23, 2.5, 23), {'up': 'sand'}, uv={'up': FULL}),
         box((-8, 0, -8), (24, 4, -6), 'wood'), box((-8, 0, 22), (24, 4, 24), 'wood'), box((-8, 0, -6), (-6, 4, 22), 'wood'),
         box((22, 0, -6), (24, 4, 22), 'wood')]
    for x, z in ((-8, -8), (18, -8), (-8, 18), (18, 18)):
        e.append(box((x, 4, z), (x + 6, 5, z + 6), 'wood'))
    model('sandbox', e, gui=0.4, extra_tex=sand)
    # A roundabout: an eight-sided platform round a post, handrails.
    e = [box((7, 0, 7), (9, 14, 9), 'steel'), box((-6, 2, 1), (22, 3.5, 15), 'red'), box((1, 2, -6), (15, 3.5, 22), 'red'),
         box((-4, 2.2, -4), (20, 3.3, 20), 'play_yellow', rot={'origin': [8, 2, 8], 'axis': 'y', 'angle': 45})]
    for a in (0, 45):
        r = {'origin': [8, 10, 8], 'axis': 'y', 'angle': a}
        e += [box((-4, 9, 7.5), (20, 10, 8.5), 'steel', rot=r), box((7.5, 9, -4), (8.5, 10, 20), 'steel', rot=r)]
    for x, z in ((-3.5, 7.5), (18.5, 7.5), (7.5, -3.5), (7.5, 18.5)):
        e.append(box((x, 3.5, z), (x + 1, 10, z + 1), 'steel'))
    model('roundabout', e, gui=0.45)
    # A seesaw.
    e = [box((6, 0, 6), (10, 5, 10), 'steel'), box((6.5, 5, -14), (9.5, 6.5, 30), 'wood', rot={'origin': [8, 5.5, 8], 'axis': 'x', 'angle': 10}),
         box((5, 6.5, -10), (11, 10, -9), 'red', rot={'origin': [8, 5.5, 8], 'axis': 'x', 'angle': 10}),
         box((5, 6.5, 25), (11, 10, 26), 'red', rot={'origin': [8, 5.5, 8], 'axis': 'x', 'angle': 10})]
    model('seesaw', e, gui=0.45)
    # A climbing frame: four green posts, bars across at three heights, a ladder of bars overhead.
    e = []
    for x in (-8, 22):
        for z in (0, 14):
            e.append(box((x, 0, z), (x + 2, 28, z + 2), 'green'))
    for y in (8, 16, 26):
        e += [box((-8, y, 0.5), (24, y + 1, 1.5), 'play_yellow'), box((-8, y, 14.5), (24, y + 1, 15.5), 'play_yellow')]
    for x in range(-6, 22, 4):
        e.append(box((x, 26, 1), (x + 1, 27, 15), 'steel'))
    model('climbing_frame', e, gui=0.4)
    # A goal (for five-a-side): three blocks wide, net at the back and the sides.
    e = [box((-16, 0, 0), (-14, 32, 2), 'white'), box((30, 0, 0), (32, 32, 2), 'white'), box((-16, 30, 0), (32, 32, 2), 'white'),
         box((-14, 0, 15.5), (30, 30, 16), {'north': 'net', 'south': 'net'}, uv={'north': FULL, 'south': FULL}),
         box((-14, 29.5, 2), (30, 30, 16), {'up': 'net', 'down': 'net'}, uv={'up': FULL, 'down': FULL}),
         box((-14.5, 0, 2), (-14, 30, 16), {'east': 'net', 'west': 'net'}, uv={'east': FULL, 'west': FULL}),
         box((30, 0, 2), (30.5, 30, 16), {'east': 'net', 'west': 'net'}, uv={'east': FULL, 'west': FULL}),
         box((-15, 0, 15), (31, 1, 16), 'white')]
    model('football_goal', e, gui=0.3)
    # A basketball hoop: put on a pole block; board, ring and net face the front.
    e = [box((7, 0, 7), (9, 28, 9), 'steel'), box((7, 24, 6), (9, 26, 7), 'steel'),
         box((0, 18, 5), (16, 32, 6), {'north': 'backboard', 'south': 'white', 'east': 'white', 'west': 'white', 'up': 'white', 'down': 'white'},
             uv={'north': FULL}),
         box((4, 20, -2), (12, 20.6, -1.4), 'play_orange'), box((4, 20, 4.4), (12, 20.6, 5), 'play_orange'),
         box((4, 20, -1.4), (4.6, 20.6, 4.4), 'play_orange'), box((11.4, 20, -1.4), (12, 20.6, 4.4), 'play_orange'),
         box((4.6, 16, -1.4), (11.4, 20, -1.3), {'north': 'net', 'south': 'net'}, uv={'north': FULL, 'south': FULL}),
         box((4.6, 16, 4.3), (11.4, 20, 4.4), {'north': 'net', 'south': 'net'}, uv={'north': FULL, 'south': FULL})]
    model('basket_hoop', e, gui=0.45)
    # Stands: three rows of seats stepping up to the back, three blocks wide.
    e = []
    for k, col in enumerate(('play_blue', 'red', 'play_blue')):
        z0 = -8 + k * 8
        e += [box((-16, 0, z0), (32, 4 + k * 5, z0 + 8), 'concrete'), box((-15, 4 + k * 5, z0 + 3), (31, 6 + k * 5, z0 + 7), col)]
    e += [box((-16, 14, 15), (32, 24, 16), 'steel'), box((-16, 23, 13), (32, 24, 16), 'steel')]
    model('bleachers', e, gui=0.3, extra_tex={'concrete': 'minecraft:block/light_gray_concrete'})
    # A floodlight: a frame of six lamps tilted down at the field, put on top of a tall pole.
    rot = {'origin': [8, 8, 8], 'axis': 'x', 'angle': 22.5}
    e = [box((7, 0, 7), (9, 6, 9), 'steel'), box((-6, 4, 7), (22, 18, 10), 'steel', rot=rot)]
    for i in range(3):
        for j in range(2):
            e.append(box((-5 + i * 9, 5 + j * 6.5, 6.6), (3 + i * 9, 10.5 + j * 6.5, 7), {'north': 'lamp_glow'}, uv={'north': FULL}, emit=15, rot=rot))
    model('floodlight', e, gui=0.45)
    # A volleyball net: two posts, the net between them at head height.
    e = [box((-15, 0, 7), (-13, 32, 9), 'steel'), box((29, 0, 7), (31, 32, 9), 'steel'),
         box((-13, 20, 7.8), (29, 30, 8.2), {'north': 'net', 'south': 'net'}, uv={'north': FULL, 'south': FULL}),
         box((-13, 29.5, 7.6), (29, 30.5, 8.4), 'white')]
    model('sport_net', e, gui=0.3)


# ----------------------------------------------------------------------------------------------------------------
# Blockstates and items

FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

# id -> (kind, model base): 'facing' (four turns), 'pole' (bottom/not), 'plain'
BLOCKS = {
    'pole_steel': 'pole', 'pole_black': 'pole', 'pole_green': 'pole', 'pole_concrete': 'pole',
    'lamp_modern': 'facing', 'lamp_cobra': 'facing', 'lamp_lantern': 'facing', 'lamp_globe': 'facing',
    'traffic_light': 'facing',
    'sign_stop': 'facing', 'sign_give_way': 'facing', 'sign_crossing': 'facing', 'sign_no_parking': 'facing', 'sign_speed': 'facing',
    'sign_main_road': 'facing', 'sign_bus': 'facing',
    'bench_park': 'facing', 'bench_soviet': 'facing', 'bench_modern': 'facing',
    'bin_soviet': 'facing', 'bin_modern': 'facing', 'bin_euro': 'facing',
    'bus_stop_modern': 'facing', 'bus_stop_soviet': 'facing',
    'hydrant': 'facing', 'mailbox_us': 'facing', 'mailbox_euro': 'facing', 'mailbox_soviet': 'facing',
    'booth_red': 'facing', 'booth_soviet': 'facing', 'advert_column': 'facing', 'bollard': 'facing', 'planter': 'facing',
    'bike_rack': 'facing', 'kiosk': 'facing', 'vending': 'facing', 'billboard': 'facing',
    'manhole': 'plain',
    'swing': 'facing', 'slide': 'facing', 'sandbox': 'facing', 'roundabout': 'facing', 'seesaw': 'facing', 'climbing_frame': 'facing',
    'football_goal': 'facing', 'basket_hoop': 'facing', 'bleachers': 'facing', 'floodlight': 'facing', 'sport_net': 'facing',
}


def blockstates():
    bs = os.path.join(ROOT, 'blockstates')
    items = os.path.join(ROOT, 'items')
    for bid, kind in BLOCKS.items():
        m = 'airdefense:block/street/' + bid
        if kind == 'facing':
            v = {'facing=%s' % f: ({'model': m, 'y': y} if y else {'model': m}) for f, y in FACING_Y.items()}
        elif kind == 'pole':
            v = {'bottom=true': {'model': m + '_bottom'}, 'bottom=false': {'model': m}}
        else:
            v = {'': {'model': m}}
        with open(os.path.join(bs, bid + '.json'), 'w') as fh:
            json.dump({'variants': v}, fh, indent=1)
        with open(os.path.join(items, bid + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': m + ('_bottom' if kind == 'pole' else '')}}, fh, indent=1)


if __name__ == '__main__':
    make_textures()
    poles()
    lamps()
    traffic()
    signs()
    benches()
    bins()
    bus_stops()
    small()
    play()
    blockstates()
    print('ok', len(BLOCKS), 'blocks')
