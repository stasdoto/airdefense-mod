"""trucks3, part B: tanker, supply truck."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *


def fuel_truck():
    b = Build('fuel_truck')
    body = b.body
    cab = kamaz_cab(b, body, 1.15, 1.5, 3.45, 1.3, 2.95, b.r.seats, 0.8)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.3 + 0.8, tilt=-62)
    chassis(b, (1.0, 1.15), -3.6, 1.5, w=1.15, zfront=3.4, tanks=False, deck_plate=False)
    g = Geo(40)
    # Sub-frame saddles and the elliptical tank with its reinforcing rings.
    g.extend(box((-1.0, 1.0, -3.5), (1.0, 1.2, 0.5), 'chassis'))
    for z in (-3.0, -1.6, -0.2):
        g.extend(box((-0.95, 1.1, z - 0.1), (0.95, 1.3, z + 0.1), 'chassis'))
    g.extend(oval((0, 2.1), 1.0, 0.88, -3.5, 0.5, 'paint', n=20))
    for z in (-3.0, -2.0, -1.0, 0.0):
        ring = [V(math.cos(2 * math.pi * k / 20) * 1.012, 2.1 + math.sin(2 * math.pi * k / 20) * 0.892, z) for k in range(20)]
        g.extend(geom.tube(ring, 0.014, 'paint', n=4, closed=True))
    # The top walkway with rails, three manhole domes.
    g.extend(box((-0.25, 2.97, -3.3), (0.25, 3.0, 0.3), 'grating'))
    for z in (-2.6, -1.3, -0.2):
        g.extend(cyl((0.0, 2.9, z), (0.0, 3.07, z), 0.2, 'paint', n=14, bevel=0.025))
        g.extend(cyl((0.0, 3.07, z), (0.0, 3.1, z), 0.155, 'metal_dark', n=14))
        g.extend(box((-0.015, 3.1, z - 0.1), (0.015, 3.15, z + 0.1), 'chrome'))
    for s in (-1, 1):
        g.extend(K.handrail([(s * 0.3, 3.0, -3.3), (s * 0.3, 3.22, -3.3), (s * 0.3, 3.22, 0.3), (s * 0.3, 3.0, 0.3)]))
    # Rear: the pump and valve cabinet with doors, lamps, bumper, a ladder, hose reels.
    g.extend(K.rbox((-1.02, 0.95, -3.78), (1.02, 1.95, -3.52), 'paint', r=0.03))
    for s in (-1, 1):
        g.extend(geom.panel((s * 0.5, 1.45, -3.782), 0.86, 0.82, (-1, 0, 0), (0, 1, 0), 'plate'))
        g.extend(box((s * 0.05 - 0.02, 1.35, -3.8), (s * 0.05 + 0.02, 1.6, -3.78), 'metal_dark'))
        g.extend(K.lamp_cluster((s * 0.9, 1.1, -3.79), s))
    g.extend(K.rbox((-1.15, 0.78, -3.92), (1.15, 1.0, -3.72), 'chassis', r=0.02))
    g.extend(box((-0.3, 1.97, -3.72), (0.3, 2.17, -3.62), 'orange'))
    for s in (-1, 1):
        g.extend(cyl((s * 1.1, 1.35, -3.62), (s * 1.3, 1.35, -3.62), 0.22, 'rubber', n=12))
        g.extend(cyl((s * 1.1, 1.35, -3.62), (s * 1.31, 1.35, -3.62), 0.08, 'metal_dark', n=8))
    # Side lockers under the tank and the ladder at the back.
    for s in (-1, 1):
        lo, hi = (1.0, 1.14) if s > 0 else (-1.14, -1.0)
        g.extend(K.toolbox((lo, 0.62, -0.7), (hi, 1.0, 0.4)))
    for k in range(6):
        g.extend(box((0.55, 1.15 + 0.28 * k, -3.86), (0.95, 1.2 + 0.28 * k, -3.8), 'metal_dark'))
    for x in (0.55, 0.95):
        g.extend(box((x - 0.02, 1.1, -3.87), (x + 0.02, 2.75, -3.8), 'metal_dark'))
    body.add(g)
    return b.finish()


def supply_truck():
    b = Build('supply_truck')
    body = b.body
    cab = ural_cab(b, body, 1.15, 0.75, 2.1, 3.5, 1.05, 2.6, 1.8, b.r.seats, 1.3)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.05 + 0.85, tilt=-62)
    chassis(b, (1.0, 1.15), -3.7, 0.75, w=1.2, zfront=3.4, deck_plate=False, tanks=True)
    g = Geo(40)
    # Load bed: floor, boarded drop sides, tailgate, canvas tilt on hoops.
    g.extend(box((-1.2, 1.0, -3.74), (1.2, 1.18, 0.7), 'paint'))
    g.extend(box((-1.17, 1.18, -3.7), (1.17, 1.2, 0.68), 'grating', faces=('top',)))
    for s in (-1, 1):
        x0, x1 = (1.16, 1.24) if s > 0 else (-1.24, -1.16)
        g.extend(K.rbox((x0, 1.18, -3.74), (x1, 1.65, 0.7), 'paint', r=0.015))
        for z in np.linspace(-3.5, 0.5, 7):
            g.extend(box((x0 - 0.01, 1.18, z - 0.02), (x1 + 0.01, 1.65, z + 0.02), 'paint'))
    g.extend(K.rbox((-1.2, 1.18, -3.8), (1.2, 1.65, -3.72), 'paint', r=0.015))
    g.extend(K.canvas_tilt(1.14, 1.65, 3.3, -3.62, 0.62, hoops=6, r=0.35))
    body.add(g)
    return b.finish()


BUILDERS = {'fuel_truck': fuel_truck, 'supply_truck': supply_truck}
