#!/usr/bin/env python3
"""
2.0: builds the vehicles' meshes. Each m_*.py module has models() -> [core.Model]; every model is baked and written as
  src/main/resources/assets/airdefense/meshes/<id>.mesh        (bake.py has the format)
  src/main/resources/assets/airdefense/textures/entity/mesh/<id>.png
python3 tools/mesh/build_meshes.py [--preview DIR] [module ...] [--only id,id]
"""
import argparse
import importlib
import os
import sys
import time

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'models'))
import bake  # noqa: E402
import meshv2  # noqa: E402
import render  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
MESHES = os.path.join(ROOT, 'src/main/resources/assets/airdefense/meshes')
TEX = os.path.join(ROOT, 'src/main/resources/assets/airdefense/textures/entity/mesh')
MODULES = ['m_town', 'm_tanks', 'm_trucks', 'm_air', 'm_props']


def save_png(arr, path):
    """The texture as a 256-colour PNG (libimagequant: no visible loss, about a third of the size)."""
    im = Image.fromarray(arr, 'RGBA')
    try:
        import imagequant
        im = imagequant.quantize_pil_image(im, dithering_level=0.6, max_quality=95, min_quality=60)
    except ImportError:
        pass
    im.save(path, optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('modules', nargs='*')
    ap.add_argument('--preview')
    ap.add_argument('--only')
    a = ap.parse_args()
    os.makedirs(MESHES, exist_ok=True)
    os.makedirs(TEX, exist_ok=True)
    only = set(a.only.split(',')) if a.only else None
    for name in a.modules or MODULES:
        if not os.path.exists(os.path.join(HERE, name + '.py')):
            continue
        mod = importlib.import_module(name)
        for m in mod.models():
            if only and m.id not in only:
                continue
            t = time.time()
            flat, img, W, H = bake.bake(m)
            save_png(np.clip(img, 0, 255).astype(np.uint8), os.path.join(TEX, m.id + '.png'))
            mp = os.path.join(MESHES, m.id + '.mesh')
            v, q = bake.export(m, flat, W, H, mp)
            data = open(mp, 'rb').read()
            open(mp, 'wb').write(meshv2.write_v2(*meshv2.read_v1(data)))
            print('  %-18s verts %6d quads %6d  mesh %4d KB  png %4d KB  %.1fs' % (
                m.id, v, q, os.path.getsize(os.path.join(MESHES, m.id + '.mesh')) // 1024,
                os.path.getsize(os.path.join(TEX, m.id + '.png')) // 1024, time.time() - t))
            if a.preview:
                os.makedirs(a.preview, exist_ok=True)
                render.views(flat, img, W, H, m, os.path.join(a.preview, m.id + '.png'))


if __name__ == '__main__':
    main()
