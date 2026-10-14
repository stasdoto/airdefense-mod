"""
2.0: the light wheeled armour (MaxxPro, Kozak, Tigr, HMMWV) built like the trucks: swept bodies with real glass and an inside
behind it, hoods, arches, bumpers, lamps, military wheels, and a gun ring with a shield and a heavy machine gun.
"""
import math

import numpy as np

import geom
import kit_trucks as K
import m_trucks as MTR
from core import Geo
from kit_trucks import V, box, rbox, cyl, tube

CFG = {
    # cab: z0, zf, y0, y1, half width; ws: lower and upper edge of the windscreen; side windows [(z0, z1, y0, y1)]; hood: z from, z front, bottom,
    # top at the back, top at the front, half width; bumper: y0, y1, z0, z1, half width
    'maxxpro': dict(cab=(-3.2, 1.6, 1.0, 2.9, 1.3), ws=(2.0, 2.7), win=[(-0.8, 1.35, 1.9, 2.5), (-2.1, -1.2, 1.9, 2.5)], hood=(1.5, 3.2, 1.0, 1.9, 1.85, 1.2),
                    bump=(0.8, 1.3, 3.1, 3.3, 1.2), lamp_y=1.45, belly=(0.55, 1.02, 0.95), tall=True),
    'kozak': dict(cab=(-2.9, 1.4, 0.6, 2.45, 1.2), ws=(1.75, 2.3), win=[(-0.6, 0.9, 1.75, 2.2)], hood=(1.3, 2.9, 0.6, 1.58, 1.52, 1.15),
                  bump=(0.7, 1.1, 2.85, 2.98, 1.15), lamp_y=1.27, belly=(0.45, 0.65, 0.85), tall=True),
    'tigr': dict(cab=(-2.85, 1.35, 0.65, 2.3, 1.2), ws=(1.65, 2.15), win=[(-0.9, 1.2, 1.65, 2.1), (-2.2, -1.1, 1.65, 2.05)], hood=(1.25, 2.85, 0.65, 1.55, 1.45, 1.2),
                 bump=(0.75, 1.2, 2.8, 2.93, 1.1), lamp_y=1.32, belly=(0.5, 0.7, 0.85), tall=True),
    'hmmwv': dict(cab=(-1.2, 0.75, 0.45, 1.98, 1.05), ws=(1.3, 1.9), win=[(-0.95, 0.55, 1.3, 1.85)], hood=(0.7, 2.35, 0.5, 1.25, 1.12, 0.98),
                  bump=(0.5, 1.05, 2.33, 2.46, 1.0), lamp_y=0.98, belly=(0.4, 0.55, 0.7), tall=False),
}


def m2(gp, piv, zend, n=8):
    """The M2 heavy machine gun: receiver, a cooling jacket with its holes, the barrel and flash hider, the spade grips."""
    g = Geo(40)
    x, y, z = piv
    g.extend(rbox((x - 0.1, y - 0.07, z - 0.25), (x + 0.1, y + 0.07, z + 0.32), 'metal_dark', r=0.02))
    g.extend(rbox((x - 0.07, y - 0.13, z - 0.1), (x + 0.07, y - 0.07, z + 0.2), 'plastic', r=0.01))
    for dx in (-0.07, 0.07):
        g.extend(tube([(x + dx, y + 0.02, z - 0.25), (x + dx * 1.6, y + 0.05, z - 0.4), (x + dx * 1.6, y + 0.12, z - 0.4)], 0.014, 'metal_dark', n=4))
    L = zend - z
    g.extend(geom.lathe((x, y, z + 0.3), (x, y, zend), [(0, 0.0, 'metal_dark'), (0, 0.032, 'steel'), (0.5, 0.032, 'steel'), (0.52, 0.048, 'metal_dark'), (0.75, 0.048, 'metal_dark'),
                                                       (0.77, 0.03, 'steel'), (L - 0.3 - 0.15, 0.028, 'steel'), (L - 0.3 - 0.12, 0.04, 'metal_dark'), (L - 0.3, 0.04, 'metal_dark'),
                                                       (L - 0.3, 0.015, 'black'), (L - 0.3 - 0.03, 0.0, 'black')], 'metal_dark', n, 40))
    # An ammunition box on its bracket.
    g.extend(rbox((x + 0.14, y - 0.2, z - 0.1), (x + 0.3, y - 0.04, z + 0.22), 'paint', r=0.015))
    gp.add(g)


def weapon_station(b, shield=True):
    """The turret part: a ring base, the gun shield (a front plate and two cheeks) and a hatch ring; the gun part: the M2."""
    r = b.r
    tp = b['turret']
    bx = r.by['turret'].boxes
    if r.id == 'hmmwv':
        g = Geo(40)
        g.extend(cyl((0, 1.93, -0.3), (0, 2.0, -0.3), 0.5, 'paint', n=18, bevel=0.015))
        g.extend(cyl((0, 1.98, -0.3), (0, 2.02, -0.3), 0.46, 'chassis', n=18))
        for lo, hi, _ in bx:
            g.extend(rbox(tuple(lo), tuple(hi), 'paint', r=0.025))
        g.extend(rbox((-0.12, 2.15, 0.17), (0.12, 2.38, 0.2), 'metal_dark', r=0.01))
        tp.add(g)
        gp = b['gun']
        piv = np.array(r.by['gun'].pivot)
        m2(gp, piv, piv[2] + r.rails[0][3])
        return
    lo0, hi0 = bx[0][0], bx[0][1]
    cx, cz = (lo0[0] + hi0[0]) / 2, (lo0[2] + hi0[2]) / 2
    ring_r = min(hi0[0] - lo0[0], hi0[2] - lo0[2]) / 2
    y0, y1 = lo0[1], hi0[1]
    g = Geo(40)
    g.extend(cyl((cx, y0 - 0.02, cz), (cx, y1, cz), ring_r, 'paint', n=20, bevel=0.02))
    g.extend(cyl((cx, y1, cz), (cx, y1 + 0.03, cz), ring_r * 0.92, 'chassis', n=20))
    if len(bx) > 1:
        lo1, hi1 = bx[1][0], bx[1][1]
        zf = hi1[2]
        hw = (hi1[0] - lo1[0]) / 2
        # The armoured shield: a front plate with a slot for the gun and two cheeks going back.
        g.extend(rbox((cx - hw, y1, zf - 0.1), (cx + hw, hi1[1], zf), 'paint', r=0.025))
        g.extend(rbox((cx - 0.12, y1 + (hi1[1] - y1) * 0.4, zf - 0.005), (cx + 0.12, y1 + (hi1[1] - y1) * 0.62, zf + 0.02), 'metal_dark', r=0.01))
        for s in (-1, 1):
            xs = cx + s * (hw - 0.04)
            g.extend(rbox((min(xs, xs + s * 0.08), y1, zf - 0.75), (max(xs, xs + s * 0.08), y1 + (hi1[1] - y1) * 0.82, zf - 0.08), 'paint', r=0.02))
    # The cupola hatch lid and the handles round the ring.
    for s in (-1, 1):
        g.extend(tube([(cx + s * ring_r * 0.6, y1, cz - ring_r * 0.7), (cx + s * ring_r * 0.6, y1 + 0.12, cz - ring_r * 0.7), (cx + s * ring_r * 0.2, y1 + 0.12, cz - ring_r * 0.9),
                       (cx + s * ring_r * 0.2, y1, cz - ring_r * 0.9)], 0.012, 'metal_dark', n=4))
    tp.add(g)
    gp = b['gun']
    piv = np.array(r.by['gun'].pivot)
    m2(gp, piv, piv[2] + r.rails[0][3])


def bumper(g, c, w):
    y0, y1, z0, z1, hw = c
    g.extend(rbox((-hw, y0, z0), (hw, y1, z1), 'paint', r=0.04))
    g.extend(box((-hw + 0.1, y0 + 0.05, z1), (hw - 0.1, y0 + 0.09, z1 + 0.03), 'metal_dark'))
    # Towing eyes.
    for s in (-1, 1):
        g.extend(tube([(s * 0.45, y0 + 0.12, z1), (s * 0.45, y0 + 0.14, z1 + 0.1), (s * 0.45, y0 + 0.02, z1 + 0.12), (s * 0.45, y0 - 0.02, z1 + 0.02)], 0.025, 'metal_dark', n=6))


def mrap(id):
    b = MTR.Build(id)
    cfg = CFG[id]
    body = b.body
    r = b.r
    z0, zf, y0, y1, w = cfg['cab']
    wy0, wy1 = cfg['ws']
    prof = [(z0, y0, 0.05), (zf, y0, 0.04), (zf, wy0 - 0.1, 0.0), (zf - 0.015, wy0, 0, 'ws0'), (zf - 0.12, wy1, 0, 'ws1'), (zf - 0.2, y1, 0.12), (z0 + 0.14, y1, 0.1), (z0, y1 - 0.25, 0.08)]
    cab = K.Cab(prof, w, r=0.1)
    cab.windscreen([(-w + 0.16, -0.04), (0.04, w - 0.16)])
    for zz0, zz1, ya, yb in cfg['win']:
        cab.side_window(zz0, zz1, ya, yb, follow=0.09, radius=0.05)
    body.add(cab.geo())
    dash_y = wy0 - 0.1
    K.cab_inside_part(body, 'cab_inside', cab, [s for s in r.seats], dash_y=dash_y, tilt=-62)
    g = Geo(30)
    # The hood: a swept shell, the grille and the lamps in it, the bumper in front.
    hz0, hzf, hyb, hyt0, hyt1, hw = cfg['hood']
    hood = K.Cab([(hz0, hyb, 0.03), (hzf, hyb, 0.03), (hzf, hyt1 - 0.25, 0.06), (hzf - 0.2, hyt1, 0.1), (hz0, hyt0, 0.05)], hw, r=0.1)
    g.extend(hood.geo())
    g.extend(geom.panel((0, (hyb + hyt1) / 2 - 0.05, hzf + 0.004), hw * 1.5, (hyt1 - hyb) * 0.45, (1, 0, 0), (0, 1, 0), 'grille'))
    ly = cfg['lamp_y']
    for s in (-1, 1):
        g.extend(K.headlamp((s * (hw - 0.3), ly, hzf + 0.004), r=0.085, depth=0.08))
        g.extend(box((s * (hw - 0.07) - 0.03, ly - 0.06, hzf - 0.02), (s * (hw - 0.07) + 0.03, ly + 0.06, hzf + 0.01), 'indicator'))
    bumper(g, cfg['bump'], w)
    # Mirrors on arms, door handles and hinges on the sides, the rear lamps and door.
    wsb = wy0 + 0.35
    for s in (-1, 1):
        x = s * w
        g.extend(K.mirror((x, wy1 - 0.1, zf - 0.2), (x + s * 0.3, wy1 - 0.2, zf + 0.02), (0.18, 0.32), s))
        g.extend(box((x - 0.02 if s > 0 else x - 0.012, wy0 - 0.15, zf - 0.55), (x + 0.012 if s > 0 else x + 0.02, wy0 - 0.12, zf - 0.38), 'metal_dark'))
        g.extend(K.lamp_cluster((s * (w - 0.18), cfg['lamp_y'] + 0.05, z0 - 0.005), s))
        g.extend(K.step(min(s * (w - 0.02), s * (w + 0.18)), max(s * (w - 0.02), s * (w + 0.18)), y0 + 0.12, zf - 1.1, zf - 0.3))
    g.extend(K.whip_antenna((w - 0.15, y1, z0 + 0.4), 1.6))
    # Door seams as decals; the underbody and the axles.
    ya, yb, hb = cfg['belly'][0], cfg['belly'][1], cfg['belly'][2]
    zlo = min(p[0] for p in r.by['wheel_2_l'].boxes[0][:1]) if False else z0 + 0.3
    g.extend(box((-hb, ya, z0 + 0.3), (hb, yb, hzf - 0.2), 'chassis'))
    if id == 'hmmwv':
        # The cargo bed: a floor, the sides over the wheels, a tailgate, stowage rails.
        bed = K.Cab([(-2.45, 0.55, 0.04), (-1.15, 0.55, 0.04), (-1.15, 1.2, 0.04), (-2.45, 1.2, 0.04)], 1.05, r=0.06)
        g.extend(bed.geo())
        g.extend(box((-0.93, 1.2, -2.4), (0.93, 1.23, -1.2), 'grating', faces=('top',)))
        for s_ in (-1, 1):
            g.extend(rbox((min(s_ * 0.92, s_ * 1.0), 1.2, -2.45), (max(s_ * 0.92, s_ * 1.0), 1.4, -1.2), 'paint', r=0.02))
        g.extend(rbox((-1.0, 1.2, -2.5), (1.0, 1.4, -2.42), 'paint', r=0.02))
        for s_ in (-1, 1):
            g.extend(K.lamp_cluster((s_ * 0.8, 0.95, -2.5), s_))
    body.add(g)
    b.axles()
    # Arches round the wheels.
    ag = Geo(40)
    for name, R, steer in r.wheels:
        piv = np.array(r.by[name].pivot)
        s = -1 if piv[0] < 0 else 1
        xa, xb = sorted((s * (abs(piv[0]) - 0.28), s * (abs(piv[0]) + 0.3)))
        ag.extend(K.fender(xa, xb, piv[2], piv[1], R + 0.09, -8, 188, 'paint', n=8))
    body.add(ag)
    b.wheels(rim='paint', n=18, lugs=18, bolts=0) if False else b.wheels(rim='paint', n=18, lugs=18)
    if id == 'tigr':
        # The spare wheel on the back door.
        sp = Geo(30)
        sp.extend(geom.lathe((0, 1.3, z0 - 0.0), (0, 1.3, z0 - 0.3), [(0, 0.0, 'chassis'), (0, 0.48, 'tyre_side'), (0.03, 0.54, 'tyre_side'), (0.07, 0.55, 'tyre_mil'), (0.24, 0.55, 'tyre_mil'),
                                                                      (0.27, 0.54, 'tyre_side'), (0.3, 0.48, 'tyre_side'), (0.3, 0.3, 'rim_dark'), (0.3, 0.0, 'rim_dark')], 'tyre_mil', 18, 50))
        body.add(sp)
    weapon_station(b)
    return b.finish()


JOBS = {k: (lambda bm, k=k: mrap(k)) for k in CFG}
