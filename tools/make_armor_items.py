"""
1.31 "Armour": the repair kit (a 16x16 icon and its recipe), the TOS-1A rocket (a 3D model at real size, drawn in
flight too) and the item definitions of the new vehicles. Run: python3 tools/make_armor_items.py
"""
import json
import os

from PIL import Image

import make_arty_items as arty

ROOT = arty.ROOT
TEX = os.path.join(ROOT, 'textures', 'item')
RECIPES = os.path.join(ROOT, '..', '..', 'data', 'airdefense', 'recipe')


def repair_kit():
    """A red steel toolbox with a handle and a spanner lying on it."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    red, dark, light, steel, grey = (178, 38, 30, 255), (110, 22, 18, 255), (214, 70, 58, 255), (186, 190, 196, 255), (96, 100, 106, 255)
    for x in range(2, 14):
        for y in range(7, 14):
            px[x, y] = red
        px[x, 13] = dark
        px[x, 7] = light
    for y in range(7, 14):
        px[2, y] = dark
        px[13, y] = dark
    # The lid's seam and the clasp.
    for x in range(3, 13):
        px[x, 9] = dark
    px[7, 9] = steel
    px[8, 9] = steel
    # The handle.
    for x in range(5, 11):
        px[x, 4] = grey
    for y in range(4, 7):
        px[5, y] = grey
        px[10, y] = grey
    # A spanner lying across the top.
    for i in range(7):
        px[3 + i, 3 + (i // 3)] = steel
    px[2, 2] = steel
    px[3, 2] = steel
    px[2, 3] = steel
    img.save(os.path.join(TEX, 'repair_kit.png'))
    with open(os.path.join(arty.MODELS, 'repair_kit.json'), 'w') as fh:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/repair_kit'}}, fh, indent=2)
        fh.write('\n')
    arty.item('repair_kit')
    with open(os.path.join(RECIPES, 'repair_kit.json'), 'w') as fh:
        json.dump({'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': [' I ', 'IRI', ' I '],
                   'key': {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}, 'result': {'id': 'airdefense:repair_kit', 'count': 1}},
                  fh, indent=2)
        fh.write('\n')


def tos_rocket():
    """The 220 mm thermobaric rocket: a fat body, a blunt warhead, folding fins. Drawn at half size (3.3 m would not fit
    an item model): the flying rocket is scaled up twice."""
    length, dia = 26.5, 1.75
    r = dia / 2
    z0 = 8 - length / 2
    els = []
    els += arty.body(z0 + 1.0, z0 + length * 0.7, r, 'paint')
    els += arty.body(z0, z0 + 1.0, r * 0.8, 'fuze')
    els.append(arty.box((8 - r * 2.2, 8 - 0.08, z0 + 1.0), (8 + r * 2.2, 8 + 0.08, z0 + 2.5), 'fuze'))
    els.append(arty.box((8 - 0.08, 8 - r * 2.2, z0 + 1.0), (8 + 0.08, 8 + r * 2.2, z0 + 2.5), 'fuze'))
    els += arty.body(z0 + length * 0.7, z0 + length * 0.93, r * 1.05, 'warhead')
    els += arty.body(z0 + length * 0.93, z0 + length, r * 0.6, 'warhead')
    textures = {'paint': 'airdefense:block/missile_olive', 'fuze': 'airdefense:block/metal_dark', 'warhead': 'airdefense:block/metal_grey',
                'particle': 'airdefense:block/missile_olive'}
    arty.write('tos_rocket', els, textures)


if __name__ == '__main__':
    repair_kit()
    tos_rocket()
    for v in ('t80bvm', 'challenger2', 'bmp3', 'cv90', 'stryker', 'tigr', 'hmmwv', 'brem1', 'm88', 'tos1'):
        arty.vehicle_item(v)
    print('ok')
