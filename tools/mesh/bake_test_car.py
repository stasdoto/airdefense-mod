#!/usr/bin/env python3
"""2.0: bakes the cars.py draft as car_saloon (a placeholder for testing the client's mesh renderer)."""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bake  # noqa: E402
import cars  # noqa: E402
from build_meshes import MESHES, TEX  # noqa: E402

os.makedirs(MESHES, exist_ok=True)
os.makedirs(TEX, exist_ok=True)
m = cars.build(cars.Spec(id='car_saloon'))
flat, img, W, H = bake.bake(m)
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(TEX, m.id + '.png'), optimize=True)
print(bake.export(m, flat, W, H, os.path.join(MESHES, m.id + '.mesh')), W, H)
