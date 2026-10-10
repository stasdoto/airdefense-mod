#!/usr/bin/env python3
"""1.44: weathers the textures of the hand-made vehicles (Iskander, Kalibr, HIMARS, Patriot, IRIS-T, NASAMS, Shahed) the
way the generator now paints its own: faded patches, warmer sun-bleached and cooler shaded spots, a fine grain. Their
layouts are hand-made, so nothing that depends on which way a face looks (no dust from below). Run ONCE (it adds to
what is there): python3 tools/weather_handmade.py"""
import os

import numpy as np
from PIL import Image
from scipy.ndimage import zoom

DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'textures', 'entity', 'vehicle')
NAMES = ['iskander', 'kalibr', 'himars', 'patriot', 'iris_t', 'nasams', 'shahed']


def field(rng, h, w, cell):
    g = rng.random((h // cell + 2, w // cell + 2))
    z = zoom(g, (h / g.shape[0] + 1e-4, w / g.shape[1] + 1e-4), order=1)
    z = np.pad(z, ((0, max(0, h - z.shape[0])), (0, max(0, w - z.shape[1]))), mode='edge')
    return z[:h, :w]


def main():
    rng = np.random.default_rng(1440)
    for n in NAMES:
        p = os.path.join(DIR, n + '.png')
        im = np.asarray(Image.open(p).convert('RGBA')).astype(np.float64)
        h, w = im.shape[:2]
        c = im[..., :3]
        lum = c.mean(axis=2)
        # Leave the lights, the glass and the near-black parts alone.
        paint = (im[..., 3] > 200) & (lum > 30) & (lum < 200)
        m1 = field(rng, h, w, 9) - 0.5
        m2 = field(rng, h, w, 3) - 0.5
        k = 1 + 0.16 * m1 + 0.07 * m2
        out = c.copy()
        out *= k[..., None]
        out[..., 0] += 9 * m1
        out[..., 2] -= 6 * m1
        out += rng.normal(0, 2.5, (h, w, 1))
        c[paint] = out[paint]
        im[..., :3] = np.clip(c, 0, 255)
        Image.fromarray(im.astype(np.uint8), 'RGBA').save(p)
        print(n, w, h)


if __name__ == '__main__':
    main()
