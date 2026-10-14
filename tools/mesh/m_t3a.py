"""trucks3, part A: the Ural-class and KamAZ-class trucks (BM-21, tankers, supply) and the radar trucks."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *


def tube_bundle(g, x0, y0, z0, z1, cols, rows, px, py, r, mat='paint', cap='metal_dark', n=8):
    """A grid of launch tubes (centres x0 + i*px, y0 + j*py) from z0 to z1."""
    for j in range(rows):
        for i in range(cols):
            x = x0 + i * px
            y = y0 + j * py
            g.extend(cyl((x, y, z0), (x, y, z1), r, mat, n=n, caps=False))
            g.extend(K.disc((x, y, z1 + 0.002), r * 0.8, (0, 0, 1), cap, n))


def bm21():
    b = Build('bm21')
    body = b.body
    W = 1.15
    cab = ural_cab(b, body, W, 0.75, 2.1, 3.5, 1.05, 2.6, 1.8, b.r.seats, 1.3)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.05 + 0.85, tilt=-62)
    chassis(b, (1.0, 1.2), -3.65, 0.75, w=1.2, zfront=3.4)
    t = b['turret']
    tg = Geo(30)
    tg.extend(cyl((0, 1.2, -1.9), (0, 1.5, -1.9), 0.76, 'paint', n=24, bevel=0.02))
    tg.extend(cyl((0, 1.5, -1.9), (0, 1.56, -1.9), 0.82, 'chassis', n=24))
    tg.extend(K.rbox((-0.5, 1.56, -2.5), (0.5, 1.9, -1.4), 'paint', r=0.04))
    for s in (-1, 1):
        tg.extend(K.rbox((s * 0.95 - 0.1, 1.56, -3.0), (s * 0.95 + 0.1, 2.06, -1.1), 'paint', r=0.03))
        tg.extend(cyl((s * 0.78, 2.05, -3.0), (s * 1.05, 2.05, -3.0), 0.1, 'metal_dark', n=10))
    tg.extend(K.ram((0.0, 1.8, -1.6), (0.0, 2.05, -2.4), r=0.07))
    t.add(tg)
    p = b['pack']
    pg = Geo(40)
    tube_bundle(pg, -0.72, 2.12, -3.15, -0.15, 10, 4, 0.16, 0.165, 0.073, 'paint', n=6)
    pg.extend(box((-0.86, 2.02, -3.17), (0.86, 2.72, -3.15), 'paint'))
    for z in (-2.85, -1.65, -0.5):
        pg.extend(K.band((-0.82, 2.04, z - 0.05), (0.82, 2.72, z + 0.05), 2, z, 0.1, 0.03, mat='metal_dark'))
    pg.extend(box((-0.1, 2.72, -3.0), (0.1, 2.77, -2.4), 'paint'))
    p.add(pg)
    return b.finish()


BUILDERS = {'bm21': bm21}
