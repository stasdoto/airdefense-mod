"""
1.30 "Artillery": item models of the 152 mm and 155 mm shells and the Grad's 122 mm rocket (block-style 3D models at
real size, nose along +z, centred on the block: the flying entity draws the same model), and the item definitions of
the new vehicles. Run: python3 tools/make_arty_items.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'airdefense')
MODELS = os.path.join(ROOT, 'models', 'item')
ITEMS = os.path.join(ROOT, 'items')

DISPLAY = {
    'gui': {'rotation': [90, 135, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
    'ground': {'rotation': [90, 0, 0], 'translation': [0, 2, 0], 'scale': [0.6, 0.6, 0.6]},
    'fixed': {'rotation': [90, 135, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'firstperson_righthand': {'rotation': [0, 0, 0], 'translation': [2, 3, 0], 'scale': [0.55, 0.55, 0.55]},
}


def box(lo, hi, tex):
    faces = {f: {'texture': '#' + tex, 'uv': [0, 0, 16, 16]} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
    return {'from': list(lo), 'to': list(hi), 'faces': faces}


def body(z0, z1, r, tex):
    """A round-ish body: a square and a slimmer cross of boxes (an octagon seen end on)."""
    c = 8.0
    return [box((c - r, c - r * 0.72, z0), (c + r, c + r * 0.72, z1), tex),
            box((c - r * 0.72, c - r, z0), (c + r * 0.72, c + r, z1), tex)]


def shell(name, length, dia, paint, band):
    r = dia / 2
    z0 = 8 - length / 2
    zn = z0 + length * 0.62
    els = []
    els += body(z0, zn, r, 'paint')
    # The copper driving band near the base, the coloured band of an HE shell, the ogive and the fuze.
    els += body(z0 + length * 0.08, z0 + length * 0.14, r * 1.06, 'band')
    els += body(zn - length * 0.12, zn - length * 0.06, r * 1.01, band)
    steps = 4
    for i in range(steps):
        a = zn + (z0 + length * 0.93 - zn) * i / steps
        b = zn + (z0 + length * 0.93 - zn) * (i + 1) / steps
        els += body(a, b, r * (1 - 0.2 * (i + 1)), 'paint')
    els += body(z0 + length * 0.93, z0 + length, r * 0.28, 'fuze')
    textures = {'paint': 'airdefense:block/' + paint_tex(paint), 'band': 'airdefense:block/gun_brass', 'fuze': 'airdefense:block/metal_dark',
                'particle': 'airdefense:block/' + paint_tex(paint)}
    textures[band] = 'airdefense:block/' + paint_tex(band)
    write(name, els, textures)


def paint_tex(p):
    return {'paint': 'olive', 'yellow': 'yellow', 'black': 'black', 'grey': 'metal_grey', 'olive': 'missile_olive'}.get(p, p)


def grad():
    length, dia = 46.0, 2.0
    r = dia / 2
    z0 = 8 - length / 2
    els = []
    els += body(z0 + 1.5, z0 + length * 0.86, r, 'paint')
    # The motor's nozzle at the back, the tail fins (folded out), the warhead and its nose fuze.
    els += body(z0, z0 + 1.5, r * 0.8, 'fuze')
    for k in range(2):
        if k == 0:
            els.append(box((8 - r * 2.6, 8 - 0.12, z0 + 1.5), (8 + r * 2.6, 8 + 0.12, z0 + 4.0), 'fuze'))
        else:
            els.append(box((8 - 0.12, 8 - r * 2.6, z0 + 1.5), (8 + 0.12, 8 + r * 2.6, z0 + 4.0), 'fuze'))
    els += body(z0 + length * 0.66, z0 + length * 0.70, r * 1.02, 'yellow')
    els += body(z0 + length * 0.86, z0 + length * 0.93, r * 0.75, 'paint')
    els += body(z0 + length * 0.93, z0 + length, r * 0.35, 'fuze')
    textures = {'paint': 'airdefense:block/missile_olive', 'fuze': 'airdefense:block/metal_dark', 'yellow': 'airdefense:block/yellow',
                'particle': 'airdefense:block/missile_olive'}
    write('grad_rocket', els, textures)


def write(name, els, textures):
    os.makedirs(MODELS, exist_ok=True)
    with open(os.path.join(MODELS, name + '.json'), 'w') as fh:
        json.dump({'textures': textures, 'elements': els, 'display': DISPLAY}, fh, indent=2)
        fh.write('\n')
    item(name)


def item(name):
    with open(os.path.join(ITEMS, name + '.json'), 'w') as fh:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + name}}, fh, indent=2)


def vehicle_item(name):
    with open(os.path.join(MODELS, name + '.json'), 'w') as fh:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/' + name}}, fh, indent=2)
        fh.write('\n')
    item(name)


if __name__ == '__main__':
    # 152 mm (Russian grey-green with a black band) and 155 mm (NATO olive drab with the yellow HE band).
    shell('shell_152', 14.0, 2.45, 'paint', 'black')
    shell('shell_155', 14.5, 2.5, 'olive', 'yellow')
    grad()
    for v in ('msta_s', 'm109', 'bm21', 'zoopark', 'tpq36'):
        vehicle_item(v)
    print('ok')
