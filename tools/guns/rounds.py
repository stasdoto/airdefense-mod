"""Rounds: the new ammunition items' inventory icons (16x16 pixel art) and the 3D models of what the launchers fire,
as the missile renderer draws them (display context 'none': nose along +z, centred, 1 block = 1 m at render scale 1).

python3 tools/guns/rounds.py"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gunkit  # noqa: E402
from gunkit import Gun  # noqa: E402

K = 0.16  # model pixels per centimetre: 100 cm = 16 px = one block


# ----------------------------------------------------------------------------------------------------------------
# 3D projectiles (nose to +z)

def cyl(g, z0, z1, r, style, y=0.0, x=0.0):
    g.cyl(z0, z1, y, r, style, x=x)


def fins(g, z0, z1, span, style, n=4, t=0.25):
    for i in range(n):
        if n == 4:
            if i % 2 == 0:
                g.box((-span, -t, z0), (span, t, z1), style)
            else:
                g.box((-t, -span, z0), (t, span, z1), style)


def pg7v():
    g = Gun('rpg_round', 0, R=5, seed=71)
    cyl(g, 30.0, 34.0, 0.5, 'steel')
    cyl(g, 26.0, 30.0, 1.0, 'warhead_green')
    cyl(g, 22.0, 26.0, 2.0, 'warhead_green')
    cyl(g, 17.0, 22.0, 3.2, 'warhead_green')
    cyl(g, 7.0, 17.0, 4.25, 'warhead_green')
    cyl(g, -1.0, 7.0, 2.4, 'warhead_green')
    cyl(g, -26.0, -1.0, 2.0, 'black')
    cyl(g, -46.0, -26.0, 1.4, 'gunmetal')
    fins(g, -46.0, -40.0, 3.2, 'gunmetal')
    return g


def rpg22_rocket():
    g = Gun('rpg22_rocket', 0, R=5, seed=72)
    cyl(g, 22.0, 26.0, 0.6, 'steel')
    cyl(g, 14.0, 22.0, 2.0, 'warhead_green')
    cyl(g, 4.0, 14.0, 3.6, 'warhead_green')
    cyl(g, -22.0, 4.0, 1.8, 'gunmetal')
    fins(g, -22.0, -16.0, 3.4, 'black')
    return g


def at4_rocket():
    g = Gun('at4_rocket', 0, R=5, seed=73)
    cyl(g, 30.0, 36.0, 0.5, 'steel')
    cyl(g, 20.0, 30.0, 2.4, 'warhead_black')
    cyl(g, 8.0, 20.0, 4.1, 'warhead_black')
    cyl(g, 4.0, 8.0, 3.0, 'yellow')
    cyl(g, -30.0, 4.0, 2.0, 'gunmetal')
    fins(g, -30.0, -22.0, 4.0, 'black')
    return g


def cg_round():
    g = Gun('cg_round', 0, R=5, seed=74)
    cyl(g, 22.0, 30.0, 1.4, 'warhead_black')
    cyl(g, 10.0, 22.0, 4.2, 'warhead_black')
    cyl(g, 7.0, 10.0, 4.2, 'yellow')
    cyl(g, -18.0, 7.0, 4.0, 'brass')
    cyl(g, -20.0, -18.0, 4.2, 'black')
    return g


def nlaw_missile():
    g = Gun('nlaw_missile', 0, R=5, seed=75)
    cyl(g, 34.0, 40.0, 1.6, 'warhead_black')
    cyl(g, 20.0, 34.0, 5.1, 'od_metal')
    cyl(g, -36.0, 20.0, 4.6, 'od_metal')
    fins(g, -36.0, -26.0, 7.0, 'black')
    fins(g, 14.0, 20.0, 6.0, 'black')
    return g


def javelin_missile():
    g = Gun('javelin_missile', 0, R=4, seed=76)
    cyl(g, 48.0, 54.0, 3.0, 'glass')
    cyl(g, 40.0, 48.0, 5.6, 'od_metal')
    cyl(g, -54.0, 40.0, 6.3, 'od_metal')
    cyl(g, 10.0, 12.0, 6.4, 'yellow')
    fins(g, -10.0, 8.0, 9.0, 'od_metal', t=0.3)
    fins(g, -54.0, -46.0, 8.0, 'black', t=0.3)
    return g


def tamir():
    """Iron Dome's Tamir: 3 m, 16 cm across, four long strakes and the steering fins at the tail. Built at half size
    (the missile is drawn at scale 2)."""
    g = Gun('tamir_missile', 0, R=4, seed=78)
    g.k = 0.08
    cyl(g, 140.0, 150.0, 3.0, 'glass')
    cyl(g, 120.0, 140.0, 7.0, 'white')
    cyl(g, -150.0, 120.0, 8.0, 'white')
    cyl(g, 60.0, 70.0, 8.1, 'mark_red')
    fins(g, -60.0, 60.0, 14.0, 'white', t=0.5)
    fins(g, -150.0, -125.0, 16.0, 'gunmetal', t=0.6)
    cyl(g, -152.0, -150.0, 6.0, 'black')
    return g


def g40():
    g = Gun('ammo_40mm', 0, R=8, seed=77)
    cyl(g, 4.0, 6.0, 1.2, 'gunmetal')
    cyl(g, 0.0, 4.0, 2.0, 'warhead_green')
    cyl(g, -1.0, 0.0, 2.1, 'yellow')
    cyl(g, -5.0, -1.0, 2.0, 'brass')
    return g


PROJECTILES = [pg7v, rpg22_rocket, at4_rocket, cg_round, nlaw_missile, javelin_missile, g40, tamir]


def export_projectile(g):
    img, uvpos, W, H = gunkit.build_atlas(g)
    Image.fromarray(img, 'RGBA').save(gunkit.path('textures', 'item', 'round', g.id + '.png'), optimize=True)
    k = getattr(g, 'k', K)
    els = [gunkit.element(b, bi, uvpos, W, H, k) for bi, b in enumerate(g.boxes)]
    tex = 'airdefense:item/round/' + g.id
    lo, hi = g.bounds()
    s = round(12.0 / ((hi[2] - lo[2]) * k), 4)
    cz = (lo[2] + hi[2]) / 2 * k
    view = {'rotation': [0, 90, 0], 'translation': [round(-cz * s, 3), 0, 0], 'scale': [s] * 3}
    gunkit.write_json({'textures': {'t': tex, 'particle': tex}, 'elements': els,
                       'display': {'fixed': view, 'ground': {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [0.5] * 3},
                                   'thirdperson_righthand': {'rotation': [-90, 0, 0], 'translation': [0, 2, 1], 'scale': [0.5] * 3},
                                   'firstperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [0.4] * 3}}},
                      'models', 'item', g.id + '_3d.json')
    gunkit.write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/' + g.id}},
                      'models', 'item', g.id + '.json')
    gunkit.write_json({'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': ['none', 'fixed'], 'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + g.id + '_3d'}}],
        'fallback': {'type': 'minecraft:model', 'model': 'airdefense:item/' + g.id},
    }}, 'items', g.id + '.json')
    return len(g.boxes)


# ----------------------------------------------------------------------------------------------------------------
# 16x16 icons

OUT = (16, 16, 18, 255)


def canvas():
    return np.zeros((16, 16, 4), dtype=np.uint8)


def put(a, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        a[y, x] = c


def outline(a):
    solid = a[..., 3] > 0
    ring = np.zeros_like(solid)
    ring[1:] |= solid[:-1]
    ring[:-1] |= solid[1:]
    ring[:, 1:] |= solid[:, :-1]
    ring[:, :-1] |= solid[:, 1:]
    a[ring & ~solid] = OUT
    return a


def shade(c, k):
    return tuple(int(max(0, min(255, v * k))) for v in c[:3]) + (255,)


def diag_round(a, x0, y0, length, case, tip, width=2, tip_len=3, rim=None):
    """A cartridge lying diagonally (base at lower left, tip to the upper right)."""
    for i in range(length):
        x, y = x0 + i, y0 - i
        c = tip if i >= length - tip_len else case
        if rim and i == 0:
            c = rim
        for w in range(width):
            col = shade(c, 1.25 if w == 0 else 0.85 if w == width - 1 else 1.0)
            put(a, x + w, y, col)


def straight_round(a, x0, y_top, h, w, case, tip, tip_h=3, base=None):
    for y in range(h):
        c = tip if y < tip_h else case
        if base and y == h - 1:
            c = base
        for i in range(w):
            k = 1.3 if i == 0 else 0.75 if i == w - 1 else 1.0
            if y < tip_h and (i == 0 or i == w - 1) and y == 0:
                continue
            put(a, x0 + i, y_top + y, shade(c, k))


BRASS = (200, 160, 70, 255)
STEEL = (110, 112, 104, 255)
LACQ = (150, 140, 90, 255)
COPPER = (184, 110, 64, 255)
LEAD = (120, 120, 126, 255)
GREEN = (84, 96, 58, 255)
OD = (78, 82, 54, 255)
RED = (170, 40, 34, 255)
YELLOW = (210, 180, 60, 255)
BLACK = (40, 40, 42, 255)


def icon_556():
    a = canvas()
    diag_round(a, 2, 12, 10, BRASS, COPPER, tip_len=3)
    diag_round(a, 6, 13, 9, BRASS, COPPER, tip_len=3)
    return outline(a)


def icon_762x39():
    a = canvas()
    diag_round(a, 2, 12, 9, LACQ, COPPER, tip_len=3)
    diag_round(a, 6, 13, 9, LACQ, COPPER, tip_len=3)
    return outline(a)


def icon_9x39():
    a = canvas()
    diag_round(a, 3, 12, 8, STEEL, (60, 60, 64, 255), width=3, tip_len=3)
    diag_round(a, 7, 13, 8, STEEL, (60, 60, 64, 255), width=3, tip_len=3)
    return outline(a)


def icon_127():
    a = canvas()
    diag_round(a, 1, 14, 14, BRASS, (60, 60, 64, 255), width=3, tip_len=4)
    return outline(a)


def icon_12g():
    a = canvas()
    for x0 in (3, 9):
        straight_round(a, x0, 2, 12, 4, RED, RED, tip_h=0, base=BRASS)
        for y in (11, 12):
            for i in range(4):
                put(a, x0 + i, y, shade(BRASS, 1.2 if i == 0 else 0.8 if i == 3 else 1.0))
    return outline(a)


def icon_40mm():
    a = canvas()
    for y in range(3, 14):
        for x in range(4, 12):
            dx = x - 7.5
            if y < 7 and dx * dx + (y - 7) ** 2 * 1.6 > 16:
                continue
            c = GREEN if y < 8 else YELLOW if y == 8 else BRASS
            put(a, x, y, shade(c, 1.25 if x == 4 else 0.8 if x == 11 else 1.0))
    return outline(a)


def icon_rocket(body, head, band=None, fins_c=BLACK, head_len=5, fat=3):
    a = canvas()
    for i in range(14):
        x, y = 1 + i, 14 - i
        c = head if i >= 14 - head_len else body
        if band and i == 14 - head_len - 1:
            c = band
        w = fat if i >= 14 - head_len else 2
        for j in range(w):
            put(a, x + j - (1 if w > 2 else 0), y + (1 if w > 2 else 0) - j * 0, shade(c, 1.25 if j == 0 else 0.8 if j == w - 1 else 1.0))
    for (x, y) in ((1, 12), (3, 14), (0, 13), (2, 15)):
        put(a, x, y, fins_c)
    return outline(a)


ICONS = {
    'ammo_556': icon_556,
    'ammo_762x39': icon_762x39,
    'ammo_9x39': icon_9x39,
    'ammo_127': icon_127,
    'ammo_12g': icon_12g,
    'ammo_40mm': icon_40mm,
    'cg_round': lambda: icon_rocket((70, 72, 66, 255), BLACK, band=YELLOW, head_len=6, fat=4),
    'javelin_missile': lambda: icon_rocket(OD, (60, 90, 100, 255), band=YELLOW, head_len=3, fat=3),
    'rpg_round': lambda: icon_rocket((60, 62, 60, 255), GREEN, head_len=6, fat=4),
    'rpg22_rocket': lambda: icon_rocket(STEEL, GREEN, head_len=5, fat=3),
    'at4_rocket': lambda: icon_rocket(STEEL, BLACK, band=YELLOW, head_len=6, fat=3),
    'nlaw_missile': lambda: icon_rocket(OD, BLACK, head_len=3, fat=3),
    'tamir_missile': lambda: icon_rocket((220, 222, 216, 255), (60, 90, 100, 255), band=RED, head_len=2, fat=3),
}


def icons():
    for name, fn in ICONS.items():
        Image.fromarray(fn(), 'RGBA').save(gunkit.path('textures', 'item', name + '.png'))


def simple_items(names):
    """Plain flat item models and definitions (the ammunition that has no 3D model)."""
    for n in names:
        gunkit.write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/' + n}}, 'models', 'item', n + '.json')
        gunkit.write_json({'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + n}}, 'items', n + '.json')


if __name__ == '__main__':
    icons()
    simple_items(['ammo_556', 'ammo_762x39', 'ammo_9x39', 'ammo_127', 'ammo_12g'])
    for make in PROJECTILES:
        g = make()
        print(g.id, export_projectile(g))
