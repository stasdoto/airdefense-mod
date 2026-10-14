"""trucks3, part G: Bastion, Nemesis, Switchblade, Orlan, Lancet launchers."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *
from m_t3c import western_front, shelter, landing_gear
from m_t3d import light_truck
from m_t3f import frame_of


def wing(g, x0, x1, y, zle, chord, t=0.025, mat='paint', taper=0.7, sweep=0.0):
    """A thin straight wing from x0 to x1 (leading edge at zle, tapering towards both tips)."""
    g.extend(K.rbox((x0, y - t / 2, zle - chord), (x1, y + t / 2, zle), mat, r=0.008))


def fuselage(g, a, b_, r, mat='paint', nose_mat='metal_dark', n=10):
    """A UAV / missile fuselage from tail a to nose b (z axis): rounded nose, tapered tail."""
    L = b_[2] - a[2]
    prof = [(0, 0.0), (0.02, r * 0.5), (L * 0.12, r * 0.85), (L * 0.3, r), (L * 0.78, r), (L * 0.92, r * 0.72), (L * 0.985, r * 0.35, nose_mat), (L, 0.0, nose_mat)]
    g.extend(geom.lathe(a, b_, prof, mat, n, 50))


def kamaz_base(b, cab_args, deck, zrear, zdeck_front, w=1.2, **kw):
    cab = kamaz_cab(b, b.body, w, *cab_args, b.r.seats, 0.8)
    K.cab_inside_part(b.body, 'cab_inside', cab, b.r.seats, dash_y=cab_args[2] + 0.8, tilt=-62)
    chassis(b, deck, zrear, zdeck_front, w=w + 0.05, zfront=cab_args[1] - 0.1, **kw)
    return cab


def bastion():
    b = Build('bastion', density=15)
    body = b.body
    cab = kamaz_cab(b, body, 1.45, 4.4, 6.7, 1.5, 3.1, b.r.seats, 0.95)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.5 + 0.8, tilt=-62)
    chassis(b, (1.5, 1.72), -6.8, 4.3, w=1.5, zfront=6.5, wn=11, tanks=True, jacks=None, bar_bumper=True, deck_plate=True)
    g = Geo(40)
    # Engine bay under the cab, the grille, steps and spare wheels, the stabilising jacks.
    g.extend(K.rbox((-1.0, 0.95, 4.4), (1.0, 1.5, 6.6), 'paint', r=0.05))
    for s in (-1, 1):
        g.extend(stowed_jack(s * 1.4, -6.5, 1.72, s))
        g.extend(stowed_jack(s * 1.4, -0.4, 1.72, s))
    g.extend(K.exhaust_stack(1.4, 1.7, 3.1, 4.5, r=0.06))
    g.extend(K.whip_antenna((-1.3, 3.12, 4.7), 1.2))
    g.extend(K.whip_antenna((1.3, 3.12, 4.7), 0.9))
    body.add(g)
    c = b['container']
    cg = Geo(40)
    cg.extend(K.rbox((-1.35, 2.1, -6.4), (1.35, 3.8, 4.2), 'paint', r=0.05))
    for z in np.linspace(-5.4, 3.4, 8):
        cg.extend(K.band((-1.35, 2.1, z - 0.06), (1.35, 3.8, z + 0.06), 2, z, 0.12, 0.03, mat='paint'))
    for z in (-5.475, -1.875, 1.725):
        cg.extend(K.band((-1.35, 2.1, z - 0.125), (1.35, 3.8, z + 0.125), 2, z, 0.25, 0.05, mat='metal_dark'))
    for s in (-1, 1):
        for z in (-6.2, 4.0):
            cg.extend(box((s * 1.35 - 0.1, 3.8, z - 0.1), (s * 1.35 + 0.1, 3.88, z + 0.1), 'metal_dark'))
    cg.extend(geom.panel((0, 2.95, 4.203), 2.5, 1.5, (1, 0, 0), (0, 1, 0), 'metal_dark'))
    cg.extend(K.ram((-0.9, 1.72, -2.0), (-0.9, 2.1, -3.0), r=0.09))
    cg.extend(K.ram((0.9, 1.72, -2.0), (0.9, 2.1, -3.0), r=0.09))
    c.add(cg)
    for k, (x0, x1) in enumerate(((-1.2, -0.1), (0.1, 1.2))):
        lg = Geo(30)
        lg.extend(K.rbox((x0, 2.4, 4.2), (x1, 3.5, 4.32), 'paint', r=0.02))
        xc = (x0 + x1) / 2
        lg.extend(geom.cyl((xc, 2.95, 4.32), (xc, 2.95, 4.335), 0.35, 'metal_dark', n=14))
        b['lid_%d' % k].add(lg)
    return b.finish()


def pod_truck(b, zf, zcowl, zrear, hood, roof, bed, w=1.15):
    light_truck(b, zf=zf, zcowl=zcowl, zrear=zrear, y_bot=1.0, hood=hood, roof=roof, w=w, bed=bed, bed_w=0.82, deck_y=1.18)


def nmesis():
    b = Build('nmesis', density=17)
    pod_truck(b, 3.2, 1.6, -0.2, 1.55, 2.35, (-3.2, -0.2))
    p = b['pod']
    g = Geo(40)
    g.extend(K.rbox((-0.95, 1.25, -3.0), (0.95, 1.3, 1.2), 'chassis', r=0.01))
    for s in (-1, 1):
        g.extend(K.rbox((s * 0.48 - 0.19, 1.49, -3.0), (s * 0.48 + 0.19, 1.87, 1.1), 'paint', r=0.03))
        for z in (-2.4, -1.2, 0.0, 0.8):
            g.extend(K.band((s * 0.48 - 0.19, 1.49, z - 0.05), (s * 0.48 + 0.19, 1.87, z + 0.05), 2, z, 0.1, 0.025, mat='paint'))
        g.extend(box((s * 0.48 - 0.04, 1.87, -2.8), (s * 0.48 + 0.04, 1.92, -2.3), 'metal_dark'))
    for z in (-2.7, -0.4):
        g.extend(box((-0.95, 1.18, z - 0.06), (0.95, 1.25, z + 0.06), 'chassis'))
    g.extend(box((-0.1, 1.3, -3.05), (0.1, 1.45, -2.7), 'chassis'))
    p.add(g)
    for k in range(2):
        cp = b['cap_%d' % k]
        x = (-0.48, 0.48)[k]
        cg = Geo(20)
        cg.extend(K.rbox((x - 0.2, 1.48, 1.1), (x + 0.2, 1.88, 1.16), 'metal_dark', r=0.015))
        cp.add(cg)
    return b.finish()


def switchblade():
    b = Build('switchblade', density=17)
    pod_truck(b, 3.2, 1.6, -0.2, 1.55, 2.35, (-3.0, -0.2))
    p = b['pod']
    g = Geo(40)
    g.extend(K.rbox((-0.8, 1.1, -2.9), (0.8, 1.16, -0.5), 'chassis', r=0.01))
    for j, y in enumerate((1.38, 1.84)):
        for i, x in enumerate((-0.5, 0.0, 0.5)):
            g.extend(K.rbox((x - 0.19, y - 0.2, -2.9), (x + 0.19, y + 0.2, -0.62), 'paint', r=0.03))
            for z in (-2.5, -1.7, -0.9):
                g.extend(K.band((x - 0.19, y - 0.2, z - 0.04), (x + 0.19, y + 0.2, z + 0.04), 2, z, 0.08, 0.02, mat='paint', faces=('top',) if j else ('bottom',)))
    for x in (-0.65, 0.65):
        g.extend(box((x - 0.04, 1.16, -2.7), (x + 0.04, 2.04, -2.6), 'chassis'))
    p.add(g)
    for k in range(6):
        tp = b['tube_%d' % k]
        x = (-0.5, 0.0, 0.5)[k % 3]
        y = (1.38, 1.84)[k // 3]
        tg = Geo(20)
        tg.extend(K.rbox((x - 0.17, y - 0.17, -0.62), (x + 0.17, y + 0.17, -0.55), 'paint_black', r=0.015))
        tg.extend(geom.cyl((x, y, -0.56), (x, y, -0.54), 0.12, 'metal_dark', n=10))
        tp.add(tg)
    return b.finish()


def orlan_uav(g, z_nose=-0.35):
    """Orlan-10: a slim fuselage, straight high wing, twin tail booms with an H-tail, the pusher engine and prop."""
    y = 1.66
    fuselage(g, (0, y, -1.95), (0, y, z_nose), 0.105, 'white')
    wing(g, -1.55, 1.55, y + 0.1, -0.78, 0.32, 0.03, 'white', taper=0.8)
    for s in (-1, 1):
        g.extend(bar((s * 0.45, y + 0.06, -2.3), (s * 0.45, y + 0.06, -1.0), 0.04, 0.04, 'white'))
        g.extend(box((s * 0.45 - 0.015, y + 0.06, -2.3), (s * 0.45 + 0.015, y + 0.34, -2.1), 'white'))
        g.extend(bar((s * 0.45, y + 0.1, -1.1), (s * 0.12, y + 0.1, -1.0), 0.03, 0.03, 'white'))
    g.extend(K.rbox((-0.5, y + 0.33, -2.3), (0.5, y + 0.35, -2.1), 'white', r=0.005))
    g.extend(cyl((0, y, -1.97), (0, y, -2.04), 0.035, 'metal_dark', n=8))
    g.extend(box((-0.015, y - 0.28, -2.06), (0.015, y + 0.28, -2.02), 'black'))
    g.extend(cyl((0, y - 0.1, -1.2), (0, y - 0.16, -1.2), 0.07, 'metal_dark', n=8))


def lancet_uav(g):
    """Lancet: a cigar fuselage with a camera nose, two crossed pairs of wings (X-wing) mid and aft, a pusher prop."""
    y = 1.68
    fuselage(g, (0, y, -1.5), (0, y, 0.15), 0.1, 'white')
    g.extend(cyl((0, y, 0.15), (0, y, 0.28), 0.055, 'metal_dark', n=8))
    for z0 in (-0.47, -1.37):
        g.extend(K.rbox((-0.55 if z0 > -1 else -0.5, y - 0.015, z0), (0.55 if z0 > -1 else 0.5, y + 0.015, z0 + 0.24), 'white', r=0.005))
        g.extend(K.rbox((-0.015, 1.13 if z0 > -1 else 1.18, z0), (0.015, 2.23 if z0 > -1 else 2.18, z0 + 0.24), 'white', r=0.005))
    g.extend(cyl((0, y, -1.5), (0, y, -1.55), 0.04, 'metal_dark', n=8))
    g.extend(box((-0.22, y - 0.01, -1.55), (0.22, y + 0.01, -1.53), 'black'))


def orlan():
    b = Build('orlan', density=16)
    body = b.body
    cab = kamaz_base(b, (1.8, 3.5, 1.3, 2.95), (1.1, 1.3), -3.9, 1.8, w=1.2, tanks=True, jacks='stowed', deck_plate=True, wn=12)
    g = Geo(40)
    shelter(g, (-1.2, 1.3, -0.2), (1.2, 2.9, 1.7), door=None, windows=((0.9, 2.2, 1), (0.9, 2.2, -1)))
    # Antenna mast with a flat panel (the data link) and a whip.
    g.extend(K.rbox((0.4, 2.9, 0.7), (1.0, 3.5, 0.8), 'paint', r=0.02))
    g.extend(cyl((0.7, 2.9, 0.75), (0.7, 3.2, 0.75), 0.05, 'metal_dark', n=8))
    g.extend(K.whip_antenna((-0.9, 2.98, 0.2), 1.2))
    g.extend(K.exhaust_stack(1.15, 1.3, 3.0, 1.85, r=0.055))
    # The catapult rail's cradle at the rear, ground supports under the rail.
    g.extend(K.rbox((-1.2, 1.1, -3.9), (1.2, 1.3, -3.7), 'paint', r=0.02))
    body.add(g)
    c = b['catapult']
    cg = Geo(40)
    cg.extend(K.rbox((-0.18, 1.3, -3.9), (0.18, 1.5, -0.4), 'paint', r=0.02))
    cg.extend(box((-0.3, 1.25, -3.9), (0.3, 1.32, -0.4), 'chassis'))
    cg.extend(K.rbox((-0.25, 1.5, -0.8), (0.25, 1.7, -0.45), 'paint', r=0.02))
    for z in (-3.5, -2.2, -0.9):
        cg.extend(box((-0.32, 1.3, z - 0.05), (0.32, 1.35, z + 0.05), 'chassis'))
    c.add(cg)
    u = b['uav_0']
    ug = Geo(50)
    orlan_uav(ug)
    u.add(ug)
    bx = b['box_1']
    xg = Geo(30)
    xg.extend(K.rbox((-1.1, 1.3, -3.8), (-0.35, 2.1, -1.4), 'paint', r=0.03))
    xg.extend(box((-1.12, 1.62, -3.8), (-0.33, 1.68, -1.4), 'metal_dark'))
    for z in (-3.5, -1.7):
        xg.extend(box((-0.34, 1.9, z - 0.05), (-0.31, 2.0, z + 0.05), 'steel'))
    bx.add(xg)
    return b.finish()


def lancet():
    b = Build('lancet', density=16)
    body = b.body
    cab = kamaz_base(b, (1.8, 3.5, 1.3, 2.95), (1.1, 1.3), -3.9, 1.8, w=1.2, tanks=True, jacks='stowed', deck_plate=True, wn=12)
    g = Geo(40)
    # The control cabin is behind the cab on this launcher: a short module and the rail's pedestal.
    g.extend(K.rbox((-1.2, 1.3, 0.7), (1.2, 2.4, 1.75), 'paint', r=0.04))
    g.extend(K.whip_antenna((0.9, 2.42, 1.4), 1.2))
    g.extend(K.exhaust_stack(1.15, 1.3, 3.0, 1.85, r=0.055))
    g.extend(K.rbox((-0.5, 1.3, -3.9), (0.5, 1.45, -3.2), 'chassis', r=0.02))
    body.add(g)
    c = b['catapult']
    cg = Geo(40)
    cg.extend(K.rbox((-0.15, 1.3, -3.9), (0.15, 1.48, 0.5), 'paint', r=0.02))
    cg.extend(K.rbox((-0.25, 1.48, 0.0), (0.25, 1.62, 0.4), 'paint', r=0.02))
    cg.extend(K.ram((0.0, 1.3, -2.8), (0.0, 1.48, -3.4), r=0.05))
    c.add(cg)
    u = b['uav_0']
    ug = Geo(40)
    lancet_uav(ug)
    u.add(ug)
    for k, (lo, hi) in enumerate((((-1.15, 1.3, -3.7), (-0.4, 1.7, -1.6)), ((0.4, 1.3, -3.7), (1.15, 1.7, -1.6)), ((-1.15, 1.72, -3.7), (-0.4, 2.12, -1.6)))):
        bx = b['box_%d' % (k + 1)]
        xg = Geo(30)
        xg.extend(K.rbox(lo, hi, 'paint', r=0.03))
        ym = (lo[1] + hi[1]) / 2
        xg.extend(box((lo[0] - 0.01, ym + 0.02, lo[2] - 0.01), (hi[0] + 0.01, ym + 0.06, hi[2] + 0.01), 'metal_dark'))
        for z in (lo[2] + 0.4, hi[2] - 0.4):
            xg.extend(box((lo[0] - 0.01, ym - 0.1, z - 0.04), (hi[0] + 0.01, ym - 0.03, z + 0.04), 'metal_dark'))
        bx.add(xg)
    return b.finish()


BUILDERS = {'bastion': bastion, 'nmesis': nmesis, 'switchblade': switchblade, 'orlan': orlan, 'lancet': lancet}
