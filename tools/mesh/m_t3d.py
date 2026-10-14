"""trucks3, part D: light 4x4 trucks with towed trailers (Sentinel, TPQ-36), small gun / launcher carriers."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *
from m_t3c import western_front, shelter, landing_gear


def light_truck(b, zf=4.8, zcowl=3.2, zrear=1.25, y_bot=0.82, hood=1.18, roof=1.98, w=1.02, bed=(0.3, 1.25)):
    """A Humvee/JLTV-class 4x4: one wide shell (hood, cowl, cab), flared arches, grille and lamps, a flat bed behind."""
    body = b.body
    yb = hood + 0.12
    prof = [(zrear, y_bot, 0.04), (zf - 0.06, y_bot, 0.05), (zf, y_bot + 0.22, 0.07), (zf - 0.02, hood, 0.08), (zcowl + 0.1, hood + 0.01, 0.05),
            (zcowl, hood + 0.1, 0, 'ws0'), (zcowl - 0.2, roof - 0.07, 0, 'ws1'), (zcowl - 0.28, roof, 0.09), (zrear + 0.05, roof, 0.08)]
    cab = K.Cab(prof, w, r=0.09)
    cab.windscreen([(-w + 0.17, -0.04), (0.04, w - 0.17)])
    cab.side_window(zcowl - 1.4, zcowl - 0.28, hood + 0.14, roof - 0.14, follow=0.07)
    body.add(cab.geo())
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=hood + 0.05, tilt=-62)
    g = Geo(40)
    # Hood details: raised centre line, vents, the front: grille with slots and square lamps in the corners, winch bumper.
    g.extend(box((-0.12, hood, zcowl + 0.2), (0.12, hood + 0.035, zf - 0.2), 'paint'))
    g.extend(geom.panel((0, y_bot + 0.38, zf + 0.004), 0.7, 0.34, (1, 0, 0), (0, 1, 0), 'grille'))
    for s in (-1, 1):
        g.extend(K.rect_lamp((s * 0.82, y_bot + 0.42, zf + 0.0), 0.2, 0.17, 'headlight_box', (0, 0, 1)))
        g.extend(K.rect_lamp((s * 0.82, y_bot + 0.2, zf + 0.0), 0.2, 0.08, 'indicator', (0, 0, 1), depth=0.04))
        g.extend(K.mirror((s * w, hood + 0.55, zcowl - 0.12), (s * (w + 0.18), hood + 0.65, zcowl + 0.12), (0.14, 0.26), s))
        g.extend(K.wiper((s * 0.5, hood + 0.12, zcowl - 0.02), (s * 0.5 + (0.45 if s < 0 else -0.45) * 0.5, hood + 0.2, zcowl - 0.1)))
        g.extend(box((s * w - (0.02 if s > 0 else -0.0), hood - 0.1, zcowl - 0.8), (s * w + (0.02 if s < 0 else 0.0) + (0.02 if s > 0 else -0.02) * 0, hood - 0.07, zcowl - 0.6), 'metal_dark'))
    g.extend(K.rbox((-1.0, y_bot - 0.05, zf - 0.08), (1.0, y_bot + 0.2, zf + 0.22), 'metal_dark', r=0.03))
    for s in (-1, 1):
        g.extend(cyl((s * 0.35, y_bot + 0.05, zf + 0.22), (s * 0.35, y_bot + 0.05, zf + 0.33), 0.04, 'metal_dark', n=8))
    # Arch flares round the wheels.
    for n, R, st in b.r.wheels:
        if n.endswith('_l') and n.startswith('wheel'):
            pz = b.r.by[n].pivot[2]
            px = abs(b.r.by[n].pivot[0])
            for s in (-1, 1):
                x0, x1 = (px - 0.25, px + 0.3) if s > 0 else (-px - 0.3, -px + 0.25)
                g.extend(K.fender(x0, x1, pz, R - 0.02, R + 0.1, -10, 190))
    # Frame, skid plates, fuel tank, bed with low sides, tow hitch at the back.
    g.extend(K.frame_rails(0.42, 0.55, 0.72, zrear - 1.1, zf - 0.1, cross=(0.0, 1.3, 2.6, 3.8)))
    g.extend(box((-0.7, 0.5, 2.8), (0.7, 0.62, 4.5), 'chassis'))
    g.extend(K.rbox((-1.0, 0.7, bed[0]), (1.0, 1.15, bed[1]), 'paint', r=0.03))
    for s in (-1, 1):
        g.extend(K.lamp_cluster((s * 0.9, 0.75, bed[0] - 0.02), s))
    g.extend(box((-0.08, 0.55, -0.7), (0.08, 0.65, bed[0]), 'metal_dark'))
    g.extend(K.exhaust_stack(1.0, 0.7, 1.9, 1.7, r=0.04, bend=None))
    g.extend(K.whip_antenna((-0.9, roof + 0.02, zrear + 0.25), 1.4))
    body.add(g)
    b.axles()
    wheels(b, n=12, bolts=5)
    return cab


def trailer_deck(g, wheel_z, zlo, zhi, wx=0.98, R=0.46, y=(0.65, 1.0)):
    """A trailer's frame/deck with arches and mudguards over the single axle, a drawbar to the truck."""
    g.extend(K.rbox((-0.8, y[0], zlo), (0.8, y[1], zhi), 'paint', r=0.03))
    g.extend(cyl((-wx + 0.2, 0.46, wheel_z), (wx - 0.2, 0.46, wheel_z), 0.07, 'chassis', n=8))
    for s in (-1, 1):
        x0, x1 = (wx - 0.26, wx + 0.24) if s > 0 else (-wx - 0.24, -wx + 0.26)
        g.extend(K.fender(x0, x1, wheel_z, R - 0.0, R + 0.1, -10, 190))
        g.extend(K.lamp_cluster((s * 0.7, 0.8, zlo - 0.02), s))
    g.extend(box((-0.05, 0.55, zhi), (0.05, 0.65, zhi + 0.9), 'metal_dark'))


def sentinel():
    b = Build('sentinel', density=17)
    light_truck(b)
    g = Geo(40)
    trailer_deck(g, -2.6, -4.6, -0.7)
    g.extend(K.rbox((-0.9, 1.0, -4.6), (0.9, 1.4, -3.8), 'paint', r=0.03))
    g.extend(K.rbox((-0.8, 1.0, -1.6), (0.8, 1.6, -0.7), 'paint', r=0.03))
    g.extend(K.rbox((-0.4, 1.0, -3.1), (0.4, 1.85, -2.3), 'paint', r=0.04))
    landing_gear(g, -0.8, 0.7, 0.7)
    b.body.add(g)
    an = b['antenna']
    ag = Geo(30)
    ag.extend(cyl((0, 1.85, -2.7), (0, 2.05, -2.7), 0.35, 'paint', n=18, bevel=0.015))
    an.add(ag)
    pn = b['panel']
    qg = Geo(40)
    qg.extend(K.array_face((0, 2.7, -2.52), 2.4, 1.3, (0, 0, 1), (0, 1, 0), 'aesa', frame=0.08, depth=0.28, back='paint'))
    qg.extend(K.rbox((-0.6, 2.3, -3.15), (0.6, 3.1, -2.8), 'paint', r=0.03))
    for k in range(5):
        x = -0.45 + k * 0.22
        qg.extend(box((x - 0.012, 2.35, -3.2), (x + 0.012, 3.05, -3.15), 'metal_dark'))
    qg.extend(K.rbox((-1.0, 3.35, -2.75), (1.0, 3.6, -2.58), 'paint', r=0.03))
    pn.add(qg)
    return b.finish()


def tpq36():
    b = Build('tpq36', density=17)
    light_truck(b)
    g = Geo(40)
    # The shelter behind the cab on the truck bed, the trailer with the folded array, generators at the back.
    g.extend(K.kung((-1.05, 1.15, 0.25), (1.05, 2.45, 1.22), 'paint', door=None, ribs=False))
    trailer_deck(g, -2.6, -4.6, -0.7)
    g.extend(K.rbox((-0.9, 1.0, -1.5), (0.9, 1.5, -0.7), 'paint', r=0.03))
    for s in (-1, 1):
        g.extend(K.rbox((s * 0.85 - 0.1, 1.0, -4.25), (s * 0.85 + 0.1, 1.45, -3.95), 'paint', r=0.03))
        g.extend(cyl((s * 0.85, 1.45, -4.1), (s * 0.85, 1.55, -4.1), 0.07, 'metal_dark', n=8))
    landing_gear(g, -0.8, 0.7, 0.7)
    b.body.add(g)
    a = b['array']
    ag = Geo(40)
    ag.extend(box((-1.15, 1.35, -4.05), (1.15, 1.6, -1.65), 'chassis'))
    ag.extend(K.array_face((0, 1.72, -2.85), 2.0, 2.3, (0, 1, 0), (0, 0, 1), 'aesa_green', frame=0.1, depth=0.12, back='paint'))
    ag.extend(box((-1.0, 1.6, -1.65), (1.0, 1.72, -1.5), 'paint'))
    for s in (-1, 1):
        ag.extend(K.rbox((s * 1.0 - 0.1, 1.55, -4.15), (s * 1.0 + 0.1, 1.75, -3.9), 'metal_dark', r=0.02))
    a.add(ag)
    return b.finish()


BUILDERS = {'sentinel': sentinel, 'tpq36': tpq36}
