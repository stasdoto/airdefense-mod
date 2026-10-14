"""trucks3, part F: SAMP/T, Iron Dome, Avenger, MFG, ZU-23."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *
from m_t3c import western_front, shelter, landing_gear
from m_t3d import light_truck


def frame_of(g, x0, x1, y0, y1, z, t=0.1, d=0.2, mid=(), mat='paint'):
    """A rectangular frame (open middle) standing in the xy plane at z: posts, top/bottom bars, optional mid bars."""
    g.extend(K.rbox((x0, y0, z - d / 2), (x0 + t, y1, z + d / 2), mat, r=0.015))
    g.extend(K.rbox((x1 - t, y0, z - d / 2), (x1, y1, z + d / 2), mat, r=0.015))
    for y in (y0, y1 - t) + tuple(mid):
        g.extend(K.rbox((x0, y, z - d / 2), (x1, y + t, z + d / 2), mat, r=0.015))


def sampt():
    b = Build('sampt', density=16)
    body = b.body
    W = 1.27
    cab = western_cab(b, 3.65, 5.3, 1.1, 3.05, W)
    western_front(b, cab, W, 1.1, 5.3, 1.1)
    chassis(b, (1.1, 1.45), -5.5, 3.6, w=1.25, zfront=5.1, wn=11, jacks=None, bolts=2)
    g = Geo(40)
    # The equipment module behind the cab, the stabiliser jacks.
    g.extend(K.kung((-1.2, 1.45, 2.35), (1.2, 2.6, 3.55), 'paint', door=None, ribs=False, windows=((2.9, 2.0, 1), (2.9, 2.0, -1))))
    g.extend(K.exhaust_stack(1.15, 1.4, 3.15, 3.7, r=0.055))
    g.extend(K.whip_antenna((-1.0, 3.06, 3.85), 1.0))
    for s in (-1, 1):
        for z in (-5.3, -0.4):
            g.extend(stowed_jack(s * 1.12, z, 1.2, s))
    body.add(g)
    la = b['launcher']
    lg = Geo(40)
    lg.extend(K.rbox((-1.2, 1.55, -5.4), (1.2, 1.75, 1.6), 'paint', r=0.02))
    frame_of(lg, -1.25, 1.25, 1.75, 3.0, -4.8, t=0.12, d=0.2, mid=(2.3,))
    frame_of(lg, -1.25, 1.25, 1.75, 3.0, 0.7, t=0.12, d=0.2, mid=(2.3,))
    lg.extend(K.rbox((-0.07, 1.75, -4.9), (0.07, 3.0, -4.7), 'paint', r=0.01))
    lg.extend(K.rbox((-0.07, 1.75, 0.6), (0.07, 3.0, 0.8), 'paint', r=0.01))
    for s in (-1, 1):
        lg.extend(K.ram((s * 0.9, 1.4, -4.0), (s * 1.0, 1.7, -3.0), r=0.07))
    la.add(lg)
    for k in range(8):
        cp = b['can_%d' % k]
        x = [-0.87, -0.29, 0.29, 0.87][k % 4]
        y = 2.05 if k < 4 else 2.65
        cg = Geo(40)
        cg.extend(K.tpk((x, y, -5.4), (x, y, 1.1), 0.56, 0.56, 'paint', ribs=5))
        cp.add(cg)
    return b.finish()


def iron_dome():
    b = Build('iron_dome', density=16)
    body = b.body
    W = 1.27
    cab = western_cab(b, 2.8, 4.6, 1.1, 3.0, W)
    western_front(b, cab, W, 1.1, 4.6, 1.1)
    chassis(b, (1.1, 1.4), -4.35, 2.7, w=1.27, zfront=4.4, wn=12, jacks=None)
    g = Geo(40)
    # Power / control unit behind the cab, the generator hood and exhaust on top, rear stabilisers.
    g.extend(K.kung((-1.2, 1.4, 1.7), (1.2, 2.5, 2.65), 'paint', door=None, ribs=False, windows=((2.2, 2.0, 1), (2.2, 2.0, -1))))
    g.extend(K.rbox((-0.9, 2.5, 1.9), (0.9, 2.7, 2.5), 'chassis', r=0.03))
    g.extend(geom.panel((0, 2.703, 2.2), 1.5, 0.5, (1, 0, 0), (0, 0, -1), 'grille'))
    g.extend(K.rbox((-1.15, 1.4, -4.3), (1.15, 1.75, -3.7), 'chassis', r=0.03))
    g.extend(K.exhaust_stack(1.15, 1.4, 3.1, 2.9, r=0.055))
    g.extend(K.whip_antenna((-1.0, 3.01, 3.05), 1.0))
    for s in (-1, 1):
        g.extend(stowed_jack(s * 1.15, -4.2, 1.1, s))
    body.add(g)
    la = b['launcher']
    lg = Geo(40)
    lg.extend(K.rbox((-1.12, 1.75, -4.05), (1.12, 1.95, 1.5), 'paint', r=0.02))
    lg.extend(K.rbox((-1.12, 3.65, -4.05), (1.12, 3.85, 1.5), 'paint', r=0.02))
    for s in (-1, 1):
        lg.extend(K.rbox((s * 1.12 - (0.14 if s > 0 else 0.0), 1.95, -4.05), (s * 1.12 + (0.0 if s > 0 else 0.14), 3.65, 1.5), 'paint', r=0.02))
        for z in np.linspace(-3.4, 0.9, 6):
            lg.extend(box((s * 1.12 - 0.015 + s * 0.015, 1.8, z - 0.04), (s * 1.12 + s * 0.04, 3.82, z + 0.04), 'paint'))
    lg.extend(box((-0.98, 1.95, -4.05), (0.98, 3.65, -3.9), 'paint'))
    lg.extend(K.rbox((-1.16, 1.71, 1.48), (1.16, 3.89, 1.52), 'metal_dark', r=0.01))
    for s in (-1, 1):
        lg.extend(K.rbox((s * 1.16 - 0.04, 2.0, -3.6), (s * 1.16 + 0.04, 2.6, -1.0), 'chassis', r=0.01))
        lg.extend(K.ram((s * 1.0, 1.4, -2.9), (s * 1.1, 1.75, -2.2), r=0.07))
    la.add(lg)
    for k in range(20):
        cp = b['cell_%d' % k]
        x = [-0.69, -0.23, 0.23, 0.69][k % 4]
        y = [2.15, 2.48, 2.81, 3.14, 3.47][k // 4]
        cg = Geo(30)
        cg.extend(K.tpk((x, y, -3.9), (x, y, 1.55), 0.4, 0.28, 'paint', ribs=0))
        cp.add(cg)
    return b.finish()


def avenger():
    b = Build('avenger', density=17)
    light_truck(b, zf=2.3, zcowl=0.6, zrear=-1.3, y_bot=0.82, hood=1.15, roof=1.95, w=1.0, bed=(-2.3, -1.3))
    g = Geo(40)
    g.extend(K.rbox((-1.0, 1.15, -2.3), (1.0, 1.35, -1.3), 'paint', r=0.03))
    for s in (-1, 1):
        g.extend(stowed_jack(s * 0.85, -2.2, 1.15, s))
    b.body.add(g)
    t = b['turret']
    tg = Geo(40)
    tg.extend(cyl((0, 1.35, -1.5), (0, 1.5, -1.5), 0.72, 'paint', n=20, bevel=0.015))
    tg.extend(K.rbox((-0.6, 1.4, -2.1), (0.6, 2.35, -0.9), 'paint', r=0.07))
    tg.extend(geom.panel((0, 2.15, -0.898), 0.7, 0.3, (1, 0, 0), (0, 1, 0), 'glass'))
    tg.extend(K.rbox((-0.3, 2.35, -1.9), (0.3, 2.45, -1.2), 'paint', r=0.02))
    tg.extend(K.rbox((-0.14, 2.45, -1.3), (0.14, 2.6, -1.0), 'metal_dark', r=0.02))
    tg.extend(geom.panel((0, 2.52, -0.998), 0.18, 0.1, (1, 0, 0), (0, 1, 0), 'glass'))
    # The .50-cal on the right: receiver, barrel with a flash hider, ammo box.
    tg.extend(K.rbox((0.58, 1.55, -1.8), (0.72, 1.7, -1.0), 'metal_dark', r=0.01))
    tg.extend(cyl((0.65, 1.62, -1.0), (0.65, 1.62, 0.12), 0.022, 'metal_dark', n=6))
    tg.extend(cyl((0.65, 1.62, 0.0), (0.65, 1.62, 0.2), 0.034, 'metal_dark', n=6))
    tg.extend(K.rbox((0.72, 1.5, -1.6), (0.9, 1.7, -1.2), 'paint', r=0.01))
    t.add(tg)
    p = b['pods']
    pg = Geo(40)
    pg.extend(K.rbox((-0.95, 1.92, -1.58), (0.95, 2.08, -1.42), 'chassis', r=0.02))
    for s in (-1, 1):
        x0, x1 = (0.78, 1.22) if s > 0 else (-1.22, -0.78)
        xc = (x0 + x1) / 2
        pg.extend(K.rbox((x0, 1.6, -2.3), (x1, 2.4, -0.7), 'paint', r=0.04))
        for dx in (-0.11, 0.11):
            for dy in (-0.2, 0.2):
                pg.extend(cyl((xc + dx, 2.0 + dy, -0.76), (xc + dx, 2.0 + dy, -0.62), 0.1, 'paint', n=10, cap_mat='tpk_end'))
        pg.extend(box((xc - 0.16, 2.4, -2.0), (xc + 0.16, 2.46, -1.0), 'chassis'))
        pg.extend(K.rbox((xc - 0.12, 1.55, -2.35), (xc + 0.12, 1.75, -2.2), 'metal_dark', r=0.01))
    p.add(pg)
    return b.finish()


def mfg():
    b = Build('mfg', density=17)
    light_truck(b, zf=2.65, zcowl=1.0, zrear=-0.4, y_bot=0.72, hood=1.12, roof=1.85, w=0.9, bed=(-2.65, -0.4), open_bed=0.62)
    m = b['mount']
    mg = Geo(30)
    mg.extend(K.rbox((-0.35, 0.95, -1.9), (0.35, 1.0, -1.1), 'chassis', r=0.01))
    mg.extend(cyl((0, 1.0, -1.5), (0, 1.78, -1.5), 0.1, 'metal_dark', n=10))
    for s in (-1, 1):
        mg.extend(bar((s * 0.3, 1.0, -1.7), (0, 1.5, -1.5), 0.04, 0.04, 'metal_dark'))
        mg.extend(bar((s * 0.3, 1.0, -1.3), (0, 1.5, -1.5), 0.04, 0.04, 'metal_dark'))
    m.add(mg)
    g_ = b['gun']
    gg = Geo(40)
    gg.extend(K.rbox((-0.1, 1.7, -2.1), (0.1, 1.9, -1.2), 'metal_dark', r=0.03))
    gg.extend(cyl((0, 1.8, -1.25), (0, 1.8, -0.2), 0.03, 'metal_dark', n=8))
    gg.extend(cyl((0, 1.8, -0.55), (0, 1.8, -0.1), 0.042, 'metal_dark', n=8))
    gg.extend(cyl((0, 1.8, -0.2), (0, 1.8, -0.08), 0.055, 'metal_dark', n=8))
    gg.extend(K.rbox((-0.32, 1.68, -1.7), (-0.12, 1.9, -1.35), 'paint', r=0.015))
    gg.extend(bar((-0.12, 1.8, -1.55), (-0.02, 1.8, -1.5), 0.03, 0.05, 'metal_dark'))
    # Spade grips and the shield.
    gg.extend(bar((0, 1.78, -2.1), (0, 1.8, -2.3), 0.3, 0.03, 'metal_dark'))
    gg.extend(geom.panel((0, 1.92, -1.1), 0.7, 0.45, (1, 0, 0), (0, 1, 0), 'paint', double=True))
    g_.add(gg)
    return b.finish()


def zu23():
    b = Build('zu23', density=16)
    body = b.body
    cab = kamaz_cab(b, body, 1.22, 1.9, 3.4, 1.3, 2.9, b.r.seats, 0.8)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.3 + 0.8, tilt=-62)
    chassis(b, (1.05, 1.2), -3.5, 1.9, w=1.25, zfront=3.3, deck_plate=True, tanks=True, jacks=None)
    g = Geo(40)
    # Drop sides and a tailgate round the deck.
    for s in (-1, 1):
        x0, x1 = (1.2, 1.25) if s > 0 else (-1.25, -1.2)
        g.extend(K.rbox((x0, 1.2, -3.5), (x1, 1.6, 1.75), 'paint', r=0.01))
        for z in np.linspace(-3.2, 1.5, 7):
            g.extend(box((x0 - 0.01, 1.2, z - 0.02), (x1 + 0.01, 1.6, z + 0.02), 'paint'))
    g.extend(K.rbox((-1.25, 1.2, -3.5), (1.25, 1.6, -3.45), 'paint', r=0.01))
    g.extend(K.rbox((-1.2, 1.2, 1.7), (1.2, 1.55, 1.75), 'paint', r=0.01))
    for s in (-1, 1):
        g.extend(stowed_jack(s * 1.1, -3.3, 1.05, s))
    body.add(g)
    m = b['mount']
    mg = Geo(40)
    mg.extend(cyl((0, 1.2, -1.4), (0, 1.45, -1.4), 0.9, 'paint', n=24, bevel=0.02))
    mg.extend(K.rbox((-0.8, 1.2, -2.2), (0.8, 1.45, -0.6), 'paint', r=0.03))
    for s in (-1, 1):
        mg.extend(K.rbox((s * 0.52 - 0.075, 1.45, -1.9), (s * 0.52 + 0.075, 2.1, -1.2), 'paint', r=0.03))
    mg.extend(K.rbox((-0.25, 1.45, -2.25), (0.25, 1.75, -2.0), 'metal_dark', r=0.02))
    m.add(mg)
    gu = b['guns']
    gg = Geo(40)
    gg.extend(K.rbox((-0.4, 1.8, -2.3), (0.4, 2.1, -1.0), 'paint', r=0.04))
    for s in (-1, 1):
        x = s * 0.22
        gg.extend(cyl((x, 1.95, -1.0), (x, 1.95, 0.9), 0.04, 'metal_dark', n=8))
        gg.extend(cyl((x, 1.95, -0.7), (x, 1.95, 0.3), 0.06, 'metal_dark', n=8))
        gg.extend(cyl((x, 1.95, 0.7), (x, 1.95, 0.95), 0.065, 'metal_dark', n=8))
        gg.extend(K.rbox((s * 0.5 - 0.12, 1.75, -2.0), (s * 0.5 + 0.12, 2.05, -1.4), 'chassis', r=0.015))
    gg.extend(K.rbox((0.45, 2.05, -1.9), (0.62, 2.25, -1.5), 'metal_dark', r=0.015))
    gg.extend(geom.panel((0, 2.12, -2.32), 0.6, 0.3, (1, 0, 0), (0, 1, 0), 'metal_dark'))
    gu.add(gg)
    return b.finish()


BUILDERS = {'sampt': sampt, 'iron_dome': iron_dome, 'avenger': avenger, 'mfg': mfg, 'zu23': zu23}
