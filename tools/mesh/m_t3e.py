"""trucks3, part E: air-defence carriers (Osa, SAMP/T, Iron Dome, Avenger, ZU-23, MFG)."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *
from m_t3c import western_front, shelter
from m_t3d import light_truck


def hull_ring(z, s=1.0, ytop=1.85, ybot=0.58):
    """One cross-section of the amphibious hull: narrow bottom (between the wheels), flared sponsons over the wheels."""
    P = [(-0.8, ybot), (0.8, ybot), (0.86, 1.2), (1.37, 1.34), (1.37, ytop - 0.07), (1.22, ytop), (-1.22, ytop), (-1.37, ytop - 0.07),
         (-1.37, 1.34), (-0.86, 1.2)]
    k = (ytop - 1.3) / 0.55
    return [V(x * s, y if y <= 1.3 else 1.3 + (y - 1.3) * k, z) for x, y in P]


def osa():
    b = Build('osa', density=17)
    body = b.body
    zs = [(-4.4, 1.0, 1.85), (-4.37, 0.97, 1.85), (3.9, 1.0, 1.85), (4.3, 0.92, 1.75), (4.7, 0.78, 1.62), (4.92, 0.6, 1.52)]
    rings = [hull_ring(z, s, yt) for z, s, yt in zs]
    hull = K.flipped(geom.loft(rings, 'paint', closed=True))
    hull.extend(geom.polygon_cap(rings[0], 'paint'))
    hull.extend(geom.polygon_cap(rings[-1][::-1], 'paint'))
    body.add(hull)
    # The cab block on top, windscreen and side windows.
    prof = [(1.6, 1.85, 0.03), (3.75, 1.85, 0.03), (3.75, 1.98, 0.03, 'ws0'), (3.4, 2.35, 0.0, 'ws1'), (3.3, 2.38, 0.05), (1.7, 2.38, 0.05)]
    cab = K.Cab(prof, 1.2, r=0.06)
    cab.windscreen([(-1.08, -0.04), (0.04, 1.08)])
    cab.side_window(2.3, 3.25, 2.0, 2.28, follow=0.04)
    body.add(cab.geo())
    body.add(cab.lining())
    g = Geo(40)
    # Prow: lamps, a trim vane, tow eyes, the propeller guards and the cable-covered rear.
    g.extend(box((-0.45, 1.5, 4.7), (0.45, 1.75, 4.99), 'paint'))
    for s in (-1, 1):
        g.extend(K.headlamp((s * 0.6, 1.3, 4.9), r=0.085, depth=0.09))
        g.extend(K.lamp_cluster((s * 1.05, 1.15, -4.42), s))
        g.extend(K.mirror((s * 1.25, 2.2, 3.4), (s * 1.5, 2.3, 3.65), (0.16, 0.3), s))
        g.extend(K.handrail([(s * 1.2, 1.85, 0.2), (s * 1.3, 1.95, 0.2), (s * 1.3, 1.95, 1.4), (s * 1.2, 1.85, 1.4)]))
        # Fenders over the wheels (they sit under the sponsons).
        for pz in (3.0, 0.4, -1.8):
            g.extend(K.fender(s * 0.72 if s > 0 else -1.37, 1.37 if s > 0 else s * 0.72, pz, 0.56, 0.7, -10, 190, thick=0.01))
    # Hatches, the rear ventilation louvres.
    g.extend(geom.cyl((0.6, 2.38, 2.6), (0.6, 2.44, 2.6), 0.27, 'paint', n=14, bevel=0.015))
    g.extend(geom.panel((0, 1.86, -3.3), 1.6, 1.8, (1, 0, 0), (0, 0, -1), 'grating'))
    for k in range(4):
        g.extend(box((-1.1, 1.86, -2.2 - k * 0.3), (-0.6, 1.9, -2.1 - k * 0.3), 'metal_dark'))
    body.add(g)
    # Wheel wells' inner housings (dark discs) are inside the hull; axles.
    b.axles()
    wheels(b, n=12, bolts=5)
    # The turret.
    t = b['turret']
    tg = Geo(40)
    tg.extend(cyl((0, 1.85, -0.9), (0, 1.93, -0.9), 1.12, 'chassis', n=24))
    tg.extend(K.rbox((-1.0, 1.9, -1.9), (1.0, 2.9, 0.1), 'paint', r=0.06))
    tg.extend(K.rbox((-0.6, 2.15, 0.1), (0.6, 2.95, 0.35), 'paint', r=0.04))
    tg.extend(geom.panel((0, 2.55, 0.352), 0.8, 0.5, (1, 0, 0), (0, 1, 0), 'grille'))
    for s in (-1, 1):
        tg.extend(box((s * 1.0 - 0.02, 2.2, -1.5), (s * 1.0 + 0.02, 2.7, -0.7), 'metal_dark'))
    tg.extend(K.rbox((-0.12, 2.9, -1.6), (0.12, 3.3, -1.36), 'metal_dark', r=0.02))
    # The tracking radar's dish and the TV/optical box on the turret front.
    tg.extend(K.rbox((-0.3, 2.3, 0.1), (0.3, 2.7, 0.15), 'paint_black', r=0.02))
    t.add(tg)
    sp = b['search']
    sg = Geo(40)
    sg.extend(geom.cyl((0, 3.3, -1.48), (0, 3.45, -1.48), 0.2, 'metal_dark', n=10))
    reflector(sg, (0, 3.52, -1.48), 2.0, 0.45, 0.2, facing=1, nx=2, ny=5, ribs=3)
    sg.extend(K.rbox((-1.02, 3.3, -1.55), (1.02, 3.36, -1.4), 'paint', r=0.01))
    sg.extend(bar((0, 3.52, -1.45), (0, 3.52, -1.15), 0.05, 0.05, 'steel'))
    sg.extend(K.rbox((-0.1, 3.47, -1.2), (0.1, 3.6, -1.1), 'paint', r=0.01))
    sp.add(sg)
    # The launcher: a yoke across, the three rail beams each side.
    la = b['launcher']
    lg = Geo(40)
    lg.extend(K.rbox((-1.3, 2.3, -1.4), (1.3, 2.5, -1.2), 'paint', r=0.03))
    for s in (-1, 1):
        x = s * 1.4
        lg.extend(K.rbox((x - 0.045, 1.95, -1.45), (x + 0.045, 3.0, -1.15), 'paint', r=0.015))
        for y in (2.05, 2.47, 2.89):
            lg.extend(K.rbox((x - 0.05, y - 0.04, -2.1), (x + 0.05, y + 0.04, -0.1), 'paint', r=0.01))
    la.add(lg)
    for k in range(6):
        mp = b['missile_%d' % k]
        x = -1.5 if k < 3 else 1.5
        y = [2.05, 2.47, 2.89][k % 3]
        mg = Geo(40)
        mg.extend(K.missile((x, y, -2.4), (x, y, 0.75), 0.105, body='missile_white', nose=0.22, fins=4, fin_len=0.3, fin_h=0.2, n=10,
                            canards=0.25))
        mg.extend(box((x - 0.04, y - 0.12, -1.0), (x + 0.04, y - 0.09, -0.4), 'metal_dark'))
        mp.add(mg)
    return b.finish()


BUILDERS = {'osa': osa}
