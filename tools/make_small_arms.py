"""
Stage 7 assets: small arms and gear. Everything is drawn here from code (no copied art):

* 3D item models of the AK-74, PKM, SVD, PM and RPG-7 built from boxes, in two versions - at the hip and aimed
  (the item definition switches to the aimed one while the right button is held), plus the RPG rocket grenade
  in flight;
* inventory icons (32x32 side views rendered from the same boxes for the guns, pixel art for the rest);
* solid textures for the models, the armour layers for the helmet, the helmet with night vision goggles and the
  body armour vest, and the item definitions.

Run: python3 tools/make_small_arms.py   (needs Pillow)
"""
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense')
rnd = random.Random(7)


def path(*p):
    full = os.path.join(ROOT, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def write_json(obj, *p):
    with open(path(*p), 'w') as f:
        json.dump(obj, f, indent=2)


# ---------------------------------------------------------------------------------------------------------------
# Solid textures for the 3D models (subtle noise so the faces do not look flat)

TEX = {
    'gun_black': (40, 41, 44),
    'gun_steel': (86, 90, 95),
    'gun_wood': (128, 64, 34),
    'gun_plum': (104, 46, 30),
    'gun_olive': (78, 84, 46),
    'gun_green': (98, 108, 62),
    'gun_brass': (196, 152, 70),
    'gun_rubber': (24, 24, 24),
    'gun_glass': (60, 120, 110),
}


def solid_textures():
    for name, (r, g, b) in TEX.items():
        im = Image.new('RGBA', (16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                k = rnd.uniform(-6, 6)
                if name in ('gun_wood', 'gun_plum'):
                    # Wood grain: soft stripes along the length.
                    k += 7 * ((y * 3 + (x // 5)) % 4 == 0) - 3
                px[x, y] = (clamp(r + k), clamp(g + k), clamp(b + k), 255)
        im.save(path('textures', 'block', name + '.png'))


def clamp(v):
    return max(0, min(255, int(v)))


# ---------------------------------------------------------------------------------------------------------------
# Guns as boxes. Local units: x across (right +), y up, z along the gun (forward = -z); the hand grips (0, 0, 0).

def B(x0, y0, z0, x1, y1, z1, tex):
    return (x0, y0, z0, x1, y1, z1, tex)


AK74 = [
    # pistol grip (slanted back) and trigger guard
    B(-0.5, -1.6, -0.5, 0.5, 0, 0.7, 'gun_wood'), B(-0.5, -3.2, -0.1, 0.5, -1.6, 1.1, 'gun_wood'),
    B(-0.15, -1.2, -2.4, 0.15, -0.9, -0.2, 'gun_black'), B(-0.15, -1.2, -2.4, 0.15, 0, -2.1, 'gun_black'),
    # receiver, dust cover, rear sight, selector
    B(-0.65, 0, -7.0, 0.65, 1.7, 1.6, 'gun_black'), B(-0.6, 1.7, -5.6, 0.6, 2.05, 1.5, 'gun_black'),
    B(-0.5, 1.7, -7.2, 0.5, 2.3, -6.4, 'gun_black'), B(0.65, 0.8, -3.0, 0.8, 1.2, 0.5, 'gun_steel'),
    # handguards (laminated wood), gas block, barrel, front sight, muzzle brake
    B(-0.5, 1.3, -11.2, 0.5, 2.2, -7.0, 'gun_wood'), B(-0.75, -0.3, -11.4, 0.75, 1.4, -7.0, 'gun_wood'),
    B(-0.4, 1.1, -12.2, 0.4, 2.0, -11.2, 'gun_black'), B(-0.3, 0.5, -16.5, 0.3, 1.1, -11.4, 'gun_black'),
    B(-0.4, 0.4, -15.4, 0.4, 2.2, -14.8, 'gun_black'), B(-0.42, 0.38, -18.3, 0.42, 1.22, -16.5, 'gun_black'),
    # the curved plum magazine
    B(-0.55, -2.0, -6.4, 0.55, 0, -4.2, 'gun_plum'), B(-0.55, -3.8, -6.9, 0.55, -2.0, -4.6, 'gun_plum'),
    B(-0.55, -5.4, -7.6, 0.55, -3.8, -5.2, 'gun_plum'), B(-0.55, -6.6, -8.3, 0.55, -5.4, -5.9, 'gun_plum'),
    # stock and butt plate
    B(-0.55, -0.6, 1.6, 0.55, 1.6, 4.5, 'gun_wood'), B(-0.6, -1.6, 4.5, 0.6, 1.4, 8.5, 'gun_wood'),
    B(-0.65, -2.0, 8.5, 0.65, 1.4, 9.2, 'gun_black'),
]

PKM = [
    B(-0.5, -1.5, -0.4, 0.5, 0, 0.8, 'gun_plum'), B(-0.5, -3.0, 0, 0.5, -1.5, 1.2, 'gun_plum'),
    B(-0.15, -1.1, -2.6, 0.15, -0.8, -0.3, 'gun_black'), B(-0.15, -1.1, -2.6, 0.15, 0, -2.3, 'gun_black'),
    # long stamped receiver and top cover
    B(-0.75, 0, -7.5, 0.75, 2.0, 2.0, 'gun_black'), B(-0.7, 2.0, -7.0, 0.7, 2.4, 1.5, 'gun_black'),
    # skeleton stock
    B(-0.5, 0.6, 2.0, 0.5, 1.8, 10.0, 'gun_wood'), B(-0.5, -1.8, 3.5, 0.5, -0.8, 10.0, 'gun_wood'),
    B(-0.5, -1.0, 2.0, 0.5, 0.8, 3.5, 'gun_wood'), B(-0.6, -2.0, 10.0, 0.6, 1.8, 10.8, 'gun_black'),
    # barrel with gas tube under it, carrying handle, front sight, flash hider, folded bipod
    B(-0.35, 0.8, -24.0, 0.35, 1.5, -7.5, 'gun_black'), B(-0.3, 0.1, -17.0, 0.3, 0.6, -7.5, 'gun_black'),
    B(-0.2, 1.5, -10.5, 0.2, 2.8, -10.0, 'gun_black'), B(-0.25, 2.8, -12.5, 0.25, 3.2, -8.5, 'gun_wood'),
    B(-0.35, 1.5, -22.5, 0.35, 3.0, -22.0, 'gun_black'), B(-0.45, 0.7, -26.0, 0.45, 1.6, -24.0, 'gun_black'),
    B(-0.5, 0.1, -21.5, -0.2, 0.5, -14.0, 'gun_steel'), B(0.2, 0.1, -21.5, 0.5, 0.5, -14.0, 'gun_steel'),
    # 100-round box and the belt going in
    B(-1.2, -4.0, -6.0, 1.2, -0.3, -2.2, 'gun_green'), B(0.75, 0.3, -5.5, 1.2, 1.0, -3.0, 'gun_brass'),
]

SVD = [
    # thumbhole stock: grip, cheek rest bar, lower bar, butt
    B(-0.5, -3.0, 0, 0.5, 0, 1.4, 'gun_wood'), B(-0.55, 0.3, 1.5, 0.55, 2.4, 10.0, 'gun_wood'),
    B(-0.55, -2.6, 1.4, 0.55, -1.2, 10.0, 'gun_wood'), B(-0.6, -2.8, 10.0, 0.6, 2.4, 11.0, 'gun_black'),
    B(-0.15, -1.1, -2.4, 0.15, -0.8, -0.2, 'gun_black'), B(-0.15, -1.1, -2.4, 0.15, 0, -2.1, 'gun_black'),
    # receiver and dust cover, short magazine
    B(-0.6, 0, -7.0, 0.6, 1.6, 1.5, 'gun_black'), B(-0.55, 1.6, -6.5, 0.55, 1.9, 1.4, 'gun_black'),
    B(-0.55, -2.8, -6.0, 0.55, 0, -3.8, 'gun_black'),
    # handguard, long barrel, front sight, flash hider
    B(-0.75, -0.2, -13.0, 0.75, 1.9, -7.0, 'gun_wood'), B(-0.3, 0.6, -24.0, 0.3, 1.2, -13.0, 'gun_black'),
    B(-0.4, 0.4, -21.0, 0.4, 2.0, -20.4, 'gun_black'), B(-0.4, 0.5, -26.5, 0.4, 1.3, -24.0, 'gun_black'),
    # PSO-1 scope: mount, tube, objective, eyepiece rubber, turrets
    B(-0.4, 1.9, -5.0, 0.4, 2.5, -1.5, 'gun_black'), B(-0.55, 2.5, -7.5, 0.55, 3.6, 1.0, 'gun_black'),
    B(-0.7, 2.35, -9.0, 0.7, 3.75, -7.5, 'gun_black'), B(-0.65, 2.4, 1.0, 0.65, 3.7, 2.8, 'gun_rubber'),
    B(-0.3, 3.6, -3.8, 0.3, 4.3, -2.6, 'gun_black'), B(0.55, 2.8, -3.8, 1.2, 3.3, -2.6, 'gun_black'),
    B(-0.5, 2.6, -9.05, 0.5, 3.5, -9.0, 'gun_glass'),
]

PM = [
    B(-0.45, 0.4, -5.0, 0.45, 1.6, 0.6, 'gun_steel'), B(-0.42, -0.1, -4.5, 0.42, 0.5, 0.5, 'gun_black'),
    B(-0.45, -1.6, -0.6, 0.45, -0.1, 1.2, 'gun_plum'), B(-0.45, -3.2, -0.2, 0.45, -1.6, 1.5, 'gun_plum'),
    B(-0.15, -0.9, -2.6, 0.15, -0.6, -0.6, 'gun_black'), B(-0.15, -0.9, -2.6, 0.15, -0.1, -2.3, 'gun_black'),
    B(-0.2, 0.8, -5.2, 0.2, 1.2, -5.0, 'gun_rubber'), B(-0.2, 1.2, 0.6, 0.2, 1.8, 1.0, 'gun_black'),
]

# RPG-7: the hand is on the main grip under the tube; everything relative to its top.
_RPG = [
    B(-0.6, -0.6, -6.0, 0.6, 0.6, 14.0, 'gun_steel'), B(-0.75, -0.75, -1.5, 0.75, 0.75, 5.5, 'gun_wood'),
    B(-0.9, -0.9, 14.0, 0.9, 0.9, 16.5, 'gun_olive'), B(-0.7, -0.7, -6.5, 0.7, 0.7, -5.5, 'gun_steel'),
    # the PG-7V grenade sticking out of the muzzle
    B(-0.35, -0.35, -9.0, 0.35, 0.35, -6.5, 'gun_olive'), B(-0.5, -0.5, -12.0, 0.5, 0.5, -9.0, 'gun_olive'),
    B(-0.85, -0.85, -15.5, 0.85, 0.85, -12.0, 'gun_green'), B(-0.55, -0.55, -17.5, 0.55, 0.55, -15.5, 'gun_green'),
    B(-0.25, -0.25, -19.0, 0.25, 0.25, -17.5, 'gun_steel'),
    # grips, sights, PGO-7 optic on the left
    B(-0.45, -3.5, 1.0, 0.45, -0.6, 2.3, 'gun_black'), B(-0.4, -3.0, -3.5, 0.4, -0.6, -2.5, 'gun_black'),
    B(-0.15, -1.6, -0.5, 0.15, -1.3, 1.0, 'gun_black'),
    B(-0.15, 0.6, -4.8, 0.15, 1.6, -4.4, 'gun_black'), B(-0.15, 0.6, 4.0, 0.15, 1.4, 4.4, 'gun_black'),
    B(-1.9, 0.2, 1.0, -0.6, 1.4, 5.5, 'gun_black'), B(-1.85, 0.4, 0.95, -0.65, 1.2, 1.0, 'gun_glass'),
]
RPG7 = [B(b[0], b[1] + 0.6, b[2] - 1.6, b[3], b[4] + 0.6, b[5] - 1.6, b[6]) for b in _RPG]

# k: design scale (keeps the model inside Minecraft's -16..32 limit); then the display scale gives the real size.
GUNS = {
    #        boxes  k     length in the world (blocks)  hip translation, aim translation (first person)
    'ak74': (AK74, 1.0, 0.94),
    'pkm': (PKM, 0.85, 1.19),
    'svd': (SVD, 0.8, 1.22),
    'pm': (PM, 1.0, 0.30),
    'rpg7': (RPG7, 0.85, 1.35),
}


def bounds(boxes):
    zs = [b[2] for b in boxes] + [b[5] for b in boxes]
    ys = [b[1] for b in boxes] + [b[4] for b in boxes]
    return min(zs), max(zs), min(ys), max(ys)


def element(b, k):
    x0, y0, z0, x1, y1, z1, tex = b
    f = [round(8 + x0 * k, 3), round(8 + y0 * k, 3), round(8 + z0 * k, 3)]
    t = [round(8 + x1 * k, 3), round(8 + y1 * k, 3), round(8 + z1 * k, 3)]
    for v in f + t:
        assert -16 <= v <= 32, (b, v)
    w = (t[0] - f[0], t[1] - f[1], t[2] - f[2])

    def uv(a, b_):
        return [0, 0, min(16, max(0.5, round(a, 2))), min(16, max(0.5, round(b_, 2)))]

    faces = {
        'north': {'texture': '#' + tex, 'uv': uv(w[0], w[1])},
        'south': {'texture': '#' + tex, 'uv': uv(w[0], w[1])},
        'east': {'texture': '#' + tex, 'uv': uv(w[2], w[1])},
        'west': {'texture': '#' + tex, 'uv': uv(w[2], w[1])},
        'up': {'texture': '#' + tex, 'uv': uv(w[0], w[2])},
        'down': {'texture': '#' + tex, 'uv': uv(w[0], w[2])},
    }
    return {'from': f, 'to': t, 'faces': faces}


def gun_models():
    for name, (boxes, k, length) in GUNS.items():
        zmin, zmax, ymin, ymax = bounds(boxes)
        scale = round(length * 16 / ((zmax - zmin) * k), 3)
        textures = {t: 'airdefense:block/' + t for t in sorted({b[6] for b in boxes})}
        textures['particle'] = 'airdefense:block/gun_black'
        elements = [element(b, k) for b in boxes]
        long_gun = name != 'pm'
        # First person at the hip: lower right, barrel turned a touch towards the cross-hair.
        hip_fp = {'rotation': [0, 4, 0], 'translation': [0, 1.5 if long_gun else 2.5, 0], 'scale': [scale] * 3}
        # Aimed: the sights (about 2 units above the grip) on the middle of the screen.
        sight = 2.2 * k * scale / 16
        # (pushed a little forward so the stock is not right in front of the eye)
        aim_fp = {'rotation': [0, 0, 0], 'translation': [-8.96, round((0.52 - sight - 0.035) * 16, 2), -2.5],
                  'scale': [scale] * 3}
        if name == 'svd':
            # Through the scope the rifle itself is not seen.
            aim_fp = {'rotation': [0, 0, 0], 'translation': [0, -40, 0], 'scale': [0.01] * 3}
        # Third person: arms raised in the charged-crossbow hold, the gun along the forearm.
        tp = {'rotation': [0, 0, 0], 'translation': [0, 0.5, -1.0], 'scale': [scale] * 3}
        tp_l = dict(tp)
        variants = [('', hip_fp, elements), ('_aim', aim_fp, elements)]
        if name == 'rpg7':
            # Fired: the tube without the grenade sticking out of it.
            empty = [element(b, k) for i, b in enumerate(boxes) if not 4 <= i <= 8]
            variants += [('_empty', hip_fp, empty), ('_aim_empty', aim_fp, empty)]
        for variant, fp, els in variants:
            display = {
                'firstperson_righthand': fp,
                'firstperson_lefthand': fp,
                'thirdperson_righthand': tp,
                'thirdperson_lefthand': tp_l,
                'head': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [scale] * 3},
            }
            write_json({'textures': textures, 'elements': els, 'display': display}, 'models', 'item', name + variant + '.json')
        write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/' + name}},
                   'models', 'item', name + '_icon.json')
        def held(suffix):
            return {
                'type': 'minecraft:condition',
                'property': 'minecraft:using_item',
                'on_true': {'type': 'minecraft:model', 'model': 'airdefense:item/' + name + '_aim' + suffix},
                'on_false': {'type': 'minecraft:model', 'model': 'airdefense:item/' + name + suffix},
            }
        in_hand = held('')
        if name == 'rpg7':
            in_hand = {'type': 'minecraft:condition', 'property': 'minecraft:has_component', 'component': 'airdefense:ammo',
                       'on_true': held(''), 'on_false': held('_empty')}
        write_json({'model': {
            'type': 'minecraft:select',
            'property': 'minecraft:display_context',
            'cases': [{'when': ['gui', 'ground', 'fixed'], 'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + name + '_icon'}}],
            'fallback': in_hand,
        }}, 'items', name + '.json')
        gun_icon(name, boxes)
        print(f'{name}: scale {scale}')


def gun_icon(name, boxes):
    """
    Side view of the boxes, barrel up and to the right like Minecraft's bows and crossbows (the diagonal gives the
    long guns more pixels), shaded by depth, with lit top edges and a dark outline.
    """
    import math
    size = 32
    angle = 0 if name == 'pm' else math.radians(32)
    zmin, zmax, ymin, ymax = bounds(boxes)
    length = zmax - zmin
    height = ymax - ymin
    ca, sa = math.cos(angle), math.sin(angle)
    # Extent of the rotated outline, to fit it into the icon.
    w = length * ca + height * sa
    h = length * sa + height * ca
    s = (size - 3) / max(w, h)
    zc = (zmin + zmax) / 2
    yc = (ymin + ymax) / 2
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = im.load()
    for v in range(size):
        for u in range(size):
            dx = u + 0.5 - size / 2
            dy = v + 0.5 - size / 2
            along = dx * ca - dy * sa
            up = -dx * sa - dy * ca
            z = zc - along / s
            y = yc + up / s
            best = None
            for b in boxes:
                if b[2] <= z <= b[5] and b[1] <= y <= b[4] and (best is None or b[3] > best[3]):
                    best = b
            if best is None:
                continue
            r, g, bb = TEX[best[6]]
            light = 1.3 if best[6] in ('gun_black', 'gun_rubber') else 1.1
            px[u, v] = (clamp(r * light + 8), clamp(g * light + 8), clamp(bb * light + 8), 255)
    out = im.copy()
    po = out.load()
    for v in range(size):
        for u in range(size):
            if px[u, v][3] == 0:
                continue
            r, g, b, a = px[u, v]
            if v > 0 and px[u, v - 1][3] == 0:
                po[u, v] = (clamp(r * 1.35 + 12), clamp(g * 1.35 + 12), clamp(b * 1.35 + 12), 255)
            elif v < size - 1 and px[u, v + 1][3] == 0:
                po[u, v] = (clamp(r * 0.7), clamp(g * 0.7), clamp(b * 0.7), 255)
    for v in range(size):
        for u in range(size):
            if px[u, v][3] != 0:
                continue
            for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nu, nv = u + du, v + dv
                if 0 <= nu < size and 0 <= nv < size and px[nu, nv][3] != 0:
                    po[u, v] = (18, 18, 20, 255)
                    break
    out.save(path('textures', 'item', name + '.png'))


def rpg_round_model():
    """The PG-7V in flight: nose forward along +Z like the other missiles, centred."""
    T = 'airdefense:block/'
    parts = [
        (7.5, 6.0, 0.5, 8.5, 10.0, 2.0, 'gun_olive'), (6.0, 7.5, 0.5, 10.0, 8.5, 2.0, 'gun_olive'),
        (7.7, 7.7, 0.5, 8.3, 8.3, 5.5, 'gun_olive'), (7.5, 7.5, 5.5, 8.5, 8.5, 8.5, 'gun_olive'),
        (7.0, 7.0, 8.5, 9.0, 9.0, 12.0, 'gun_green'), (7.35, 7.35, 12.0, 8.65, 8.65, 14.0, 'gun_green'),
        (7.75, 7.75, 14.0, 8.25, 8.25, 15.5, 'gun_steel'),
    ]
    elements = [element((a - 8, b - 8, c - 8, d_ - 8, e - 8, f - 8, t), 1.0) for a, b, c, d_, e, f, t in parts]
    textures = {t: T + t for t in ('gun_olive', 'gun_green', 'gun_steel')}
    textures['particle'] = T + 'gun_olive'
    hand = {'rotation': [0, 180, 0], 'translation': [0, 2, 0], 'scale': [0.7, 0.7, 0.7]}
    write_json({'textures': textures, 'elements': elements,
                'display': {'firstperson_righthand': hand, 'firstperson_lefthand': hand,
                            'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 1, 0], 'scale': [0.6] * 3},
                            'thirdperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, 1, 0], 'scale': [0.6] * 3}}},
               'models', 'item', 'rpg_round.json')
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/rpg_round'}},
               'models', 'item', 'rpg_round_icon.json')
    write_json({'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': ['gui', 'ground', 'fixed'], 'model': {'type': 'minecraft:model', 'model': 'airdefense:item/rpg_round_icon'}}],
        'fallback': {'type': 'minecraft:model', 'model': 'airdefense:item/rpg_round'},
    }}, 'items', 'rpg_round.json')


# ---------------------------------------------------------------------------------------------------------------
# 16x16 pixel-art icons for the rest

def icon(name, rows, palette):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = palette[ch]
    im.save(path('textures', 'item', name + '.png'))
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/' + name}},
               'models', 'item', name + '.json')
    write_json({'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + name}}, 'items', name + '.json')


BRASS = {'o': (20, 18, 16, 255), 'b': (214, 168, 78, 255), 'B': (240, 204, 120, 255), 'd': (150, 108, 44, 255),
         'c': (120, 72, 40, 255), 's': (150, 150, 155, 255), 'g': (90, 130, 70, 255)}


def small_icons():
    icon('ammo_545', [
        '................',
        '................',
        '..........o.....',
        '.........obo....',
        '........obBo..o.',
        '.......obBo..obo',
        '......obBdo.obBo',
        '.....obBdo.obBdo',
        '....obBdo.obBdo.',
        '...obBdo.obBdo..',
        '..obBdo.obBdo...',
        '..obdo.obBdo....',
        '...oo.obBdo.....',
        '.....obdo.......',
        '......oo........',
        '................',
    ], {**BRASS, 'b': (196, 150, 64, 255), 'B': (228, 190, 104, 255)})
    icon('ammo_762', [
        '................',
        '.........o......',
        '........ooo.....',
        '.......obBo.....',
        '......obBBo..o..',
        '.....obBBdo.ooo.',
        '....obBBdo.obBo.',
        '...obBBdo.obBBo.',
        '..obBBdo.obBBdo.',
        '..obBdo.obBBdo..',
        '..obdo.obBBdo...',
        '...oo.obBBdo....',
        '.....obBBdo.....',
        '.....obBdo......',
        '......ooo.......',
        '................',
    ], {**BRASS, 'b': (140, 140, 120, 255), 'B': (180, 176, 150, 255), 'd': (96, 94, 80, 255)})
    icon('ammo_9mm', [
        '................',
        '................',
        '................',
        '................',
        '...ooo...ooo....',
        '..osSso.osSso...',
        '..obBbo.obBbo...',
        '..obBbo.obBbo...',
        '..obBdo.obBdo...',
        '..obBdo.obBdo...',
        '..oddoo.oddoo...',
        '...ooo...ooo....',
        '................',
        '................',
        '................',
        '................',
    ], {**BRASS, 'S': (190, 190, 196, 255)})
    icon('rpg_round', [
        '................',
        '.............oo.',
        '............osso',
        '...........ogsgo',
        '..........oggggo',
        '.........oggggo.',
        '........oggggo..',
        '.......oGGGGo...',
        '......oGGGo.....',
        '.....ooGGo......',
        '....oooGo.......',
        '...ooGoo........',
        '..oGoo..........',
        '.oGGo...........',
        '.oGo............',
        '..o.............',
    ], {'o': (24, 26, 20, 255), 's': (150, 150, 155, 255), 'g': (112, 124, 70, 255), 'G': (80, 88, 48, 255)})
    icon('f1_grenade', [
        '................',
        '.........oo.....',
        '........osso....',
        '.....ooooso.....',
        '....ossoooo.....',
        '....oo.ollloo...',
        '......olglglgo..',
        '.....ogggggggo..',
        '.....olglglglo..',
        '.....ogggggggo..',
        '.....olglglglo..',
        '.....ogggggggo..',
        '......oglglgo...',
        '.......ooooo....',
        '................',
        '................',
    ], {'o': (22, 24, 18, 255), 's': (170, 170, 175, 255), 'g': (86, 96, 54, 255), 'l': (118, 130, 76, 255)})
    helmet_rows = [
        '................',
        '................',
        '.....oooooo.....',
        '...oollllllo....',
        '..ollggglgglo...',
        '..olgggggggglo..',
        '.olgggdgggggglo.',
        '.ogggggggdgggo..',
        '.oggdggggggggo..',
        '.ogggggggggdgo..',
        '.oggggggggggggo.',
        '.oddddddddddddo.',
        '..oooooooooooo..',
        '................',
        '................',
        '................',
    ]
    pal = {'o': (22, 26, 16, 255), 'g': (88, 104, 56, 255), 'l': (118, 136, 76, 255), 'd': (62, 74, 40, 255),
           'n': (30, 30, 32, 255), 'e': (90, 220, 120, 255)}
    icon('helmet', helmet_rows, pal)
    nvg = list(helmet_rows)
    nvg[6] = '.olgggdgnngggglo'[:16]
    nvg[7] = '.ogggggnnnggggo.'[:16]
    nvg[8] = '.oggdgnnnnggggo.'
    nvg[9] = '.ogggnennenggo..'
    nvg[10] = '.ogggnennenggggo'[:16]
    nvg[11] = '.oddddnnnnddddo.'
    icon('nvg_helmet', nvg, pal)
    icon('vest', [
        '................',
        '...oooo..oooo...',
        '...ogco..ocgo...',
        '...oggo..oggo...',
        '..oggggooggggo..',
        '..ogcgggggcggo..',
        '..ogggggggcggo..',
        '..ocggggggggco..',
        '..oggggggggggo..',
        '..oppoppoppopo..',
        '..oPPoPPoPPoPo..',
        '..oPPoPPoPPoPo..',
        '..oggggggggggo..',
        '..oddddddddddo..',
        '..oooooooooooo..',
        '................',
    ], {'o': (24, 26, 18, 255), 'g': (102, 108, 72, 255), 'c': (72, 80, 48, 255), 'd': (70, 74, 50, 255),
        'p': (130, 132, 92, 255), 'P': (86, 92, 60, 255)})
    icon('medkit', [
        '................',
        '................',
        '......oooo......',
        '.....o....o.....',
        '..oooooooooooo..',
        '..owwwwwwwwwwo..',
        '..owwwwrrwwwwo..',
        '..owwwwrrwwwwo..',
        '..owwrrrrrrwwo..',
        '..owwrrrrrrwwo..',
        '..owwwwrrwwwwo..',
        '..owwwwrrwwwwo..',
        '..ossssssssssso.'[:16],
        '..oooooooooooo..',
        '................',
        '................',
    ], {'o': (40, 36, 30, 255), 'w': (226, 222, 210, 255), 'r': (200, 30, 30, 255), 's': (170, 166, 154, 255)})


# ---------------------------------------------------------------------------------------------------------------
# Armour layers (64x32 humanoid layout) and equipment definitions

def camo(px, x, y, base, spots, seed):
    rr = random.Random(seed * 1000 + x * 37 + y * 101)
    v = rr.random()
    c = base if v < 0.55 else spots[0] if v < 0.8 else spots[1]
    k = rr.uniform(-5, 5)
    px[x, y] = (clamp(c[0] + k), clamp(c[1] + k), clamp(c[2] + k), 255)


def helmet_layer(name, nvg):
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    px = im.load()
    base = (86, 102, 54)
    spots = [(66, 80, 40), (110, 124, 72)]
    rim = (50, 60, 32)
    # Top of the head.
    for y in range(0, 8):
        for x in range(8, 16):
            camo(px, x, y, base, spots, 1)
    # Sides and back: upper part covered, a rim at the lower edge of the helmet.
    for x0 in (0, 16, 24):
        for x in range(x0, x0 + 8):
            for y in range(8, 13):
                camo(px, x, y, base, spots, 2)
            px[x, 13] = (*rim, 255)
    # Front: the brow.
    for x in range(8, 16):
        for y in range(8, 10):
            camo(px, x, y, base, spots, 3)
        px[x, 10] = (*rim, 255)
    if nvg:
        dark = (34, 34, 36, 255)
        lens = (80, 230, 120, 255)
        for x in range(10, 14):
            px[x, 9] = dark
            px[x, 10] = dark
        for x in (9, 10, 13, 14):
            px[x, 11] = dark
            px[x, 12] = dark
        px[10, 12] = lens
        px[13, 12] = lens
    im.save(path('textures', 'entity', 'equipment', 'humanoid', name + '.png'))
    write_json({'layers': {'humanoid': [{'texture': 'airdefense:' + name}]}}, 'equipment', name + '.json')


def vest_layer():
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    px = im.load()
    base = (100, 106, 70)
    spots = [(74, 82, 50), (128, 126, 90)]
    # Body: top (20..28, 16..20) only the shoulder straps; bottom (28..36, 16..20); front (20..28, 20..32);
    # sides (16..20 and 28..32); back (32..40).
    for x in range(20, 28):
        for y in range(16, 20):
            if x < 22 or x >= 26:
                camo(px, x, y, base, spots, 4)
    for x in range(28, 36):
        for y in range(16, 20):
            camo(px, x, y, base, spots, 5)
    for x in range(16, 40):
        for y in range(20, 31):
            if 20 <= x < 28 and y == 20 and 22 <= x < 26:
                continue  # collar opening
            camo(px, x, y, base, spots, 6)
    # Magazine pouches on the front, a dark seam at the bottom.
    for i, x0 in enumerate((20, 23, 26)):
        for x in range(x0, x0 + 2):
            px[x, 25] = (140, 140, 100, 255)
            for y in range(26, 30):
                px[x, y] = (84, 90, 58, 255)
    for x in range(16, 40):
        px[x, 30] = (62, 66, 44, 255)
    # Drag handle on the back.
    for x in range(35, 37):
        px[x, 21] = (40, 40, 34, 255)
    im.save(path('textures', 'entity', 'equipment', 'humanoid', 'vest.png'))
    write_json({'layers': {'humanoid': [{'texture': 'airdefense:vest'}]}}, 'equipment', 'vest.json')


if __name__ == '__main__':
    solid_textures()
    gun_models()
    rpg_round_model()
    small_icons()
    helmet_layer('helmet', False)
    helmet_layer('nvg_helmet', True)
    vest_layer()
