#!/usr/bin/env python3
"""Shrinks the mod's big textures: a palette of 256 colours (no dithering) where that looks the same (the mean
change per pixel stays under a couple of levels). The generated textures are full of fine noise, which plain PNG
cannot pack; as palette images they take a third of the room. Run after the generators:
python3 tools/optimize_png.py [min_kb]"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets')
MIN = int(sys.argv[1]) if len(sys.argv) > 1 else 24


def main():
    before = after = 0
    for dirpath, _, files in os.walk(ROOT):
        # The guns are seen up close in the hand, the helmets on the head: they keep their full colour.
        d = dirpath.replace('\\', '/')
        if d.endswith('textures/item/gun') or d.endswith('textures/entity/gear'):
            continue
        for f in files:
            if not f.endswith('.png'):
                continue
            p = os.path.join(dirpath, f)
            size = os.path.getsize(p)
            if size < MIN * 1024:
                continue
            im = Image.open(p)
            if im.mode == 'P':
                continue
            rgba = im.convert('RGBA')
            a = np.asarray(rgba).astype(np.int16)
            q = rgba.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
            b = np.asarray(q.convert('RGBA')).astype(np.int16)
            err = np.abs(a - b)
            # Fully transparent pixels may change colour freely.
            err[a[..., 3] == 0] = 0
            if err.mean() > 2.5 or err[..., 3].max() > 8:
                continue
            tmp = p + '.tmp'
            q.save(tmp, format='PNG', optimize=True)
            if os.path.getsize(tmp) < size * 0.9:
                os.replace(tmp, p)
                before += size
                after += os.path.getsize(p)
            else:
                os.remove(tmp)
    print('shrunk %d KB -> %d KB' % (before // 1024, after // 1024))


if __name__ == '__main__':
    main()
