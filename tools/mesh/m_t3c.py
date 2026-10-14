"""trucks3, part C: radar trucks (P-18, TRML-4D, ELM-2084, Sentinel, TPQ-36, MPQ-65)."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *


def western_front(b, cab, w, d0, zf, cy0):
    """Bumper, grille, lamps, tow eyes of a western truck (under/in front of the cab)."""
    g = Geo(30)
    g.extend(K.rbox((-w + 0.01, d0 - 0.37, zf - 0.12), (w - 0.01, d0 - 0.07, zf + 0.12), 'paint', r=0.03))
    g.extend(geom.panel((0, cy0 + 0.41, zf + 0.005), 1.3, 0.42, (1, 0, 0), (0, 1, 0), 'grille'))
    for s in (-1, 1):
        g.extend(K.headlamp((s * 0.88, d0 - 0.2, zf + 0.12), r=0.085, depth=0.08))
        g.extend(cyl((s * 0.45, d0 - 0.25, zf + 0.12), (s * 0.45, d0 - 0.25, zf + 0.22), 0.05, 'metal_dark', n=8))
    b.body.add(g)


def shelter(g, lo, hi, door='back', windows=()):
    """An equipment shelter on the deck with a roof air-conditioner, vents and a cable entry."""
    g.extend(K.kung(lo, hi, 'paint', door=door, windows=windows))
    xc = (lo[0] + hi[0]) / 2
    zc = hi[2] - 0.6
    g.extend(K.rbox((xc - 0.45, hi[1], zc - 0.4), (xc + 0.45, hi[1] + 0.22, zc + 0.4), 'paint', r=0.04))
    g.extend(geom.panel((xc, hi[1] + 0.222, zc), 0.7, 0.6, (1, 0, 0), (0, 0, -1), 'grille'))
    g.extend(cyl((xc + 0.7, hi[1], zc - 0.5), (xc + 0.7, hi[1] + 0.07, zc - 0.5), 0.12, 'metal_dark', n=8))


def p18():
    b = Build('p18')
    body = b.body
    cab = ural_cab(b, body, 1.15, 0.75, 2.1, 3.5, 1.05, 2.6, 1.8, b.r.seats, 1.3)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.05 + 0.85, tilt=-62)
    chassis(b, (1.0, 1.15), -3.65, 0.75, w=1.2, zfront=3.4, deck_plate=False, tanks=True)
    g = Geo(40)
    g.extend(box((-1.2, 1.0, -3.7), (1.2, 1.15, 0.65), 'paint'))
    shelter(g, (-1.23, 1.15, -3.65), (1.23, 3.2, 0.6), door='back')
    # The folded mast: a stowed tube in its cradle with a turntable base.
    g.extend(cyl((0, 3.2, -1.4), (0, 3.5, -1.4), 0.36, 'paint', n=16, bevel=0.02))
    for k, (r_, y0, y1) in enumerate(((0.12, 3.5, 4.3), (0.095, 4.3, 5.0), (0.075, 5.0, 5.2))):
        g.extend(cyl((0, y0, -1.4), (0, y1, -1.4), r_, 'steel', n=10))
        g.extend(cyl((0, y1 - 0.04, -1.4), (0, y1 + 0.03, -1.4), r_ + 0.02, 'metal_dark', n=10))
    # Guy-struts from the mast down to the roof, an access ladder and rails on the shelter's roof.
    for a in (0, 120, 240):
        c, s_ = math.cos(math.radians(a)), math.sin(math.radians(a))
        g.extend(bar((0.1 * c, 4.3, -1.4 + 0.1 * s_), (0.7 * c, 3.26, -1.4 + 0.7 * s_), 0.035, 0.035, 'metal_dark'))
    for s in (-1, 1):
        g.extend(K.handrail([(s * 1.15, 3.2, -3.5), (s * 1.15, 3.5, -3.5), (s * 1.15, 3.5, 0.45), (s * 1.15, 3.2, 0.45)]))
    # The two stowed levelling jacks mid-body and the cable drum behind the cab.
    for s in (-1, 1):
        g.extend(stowed_jack(s * 1.2, -0.7, 1.15, s))
    body.add(g)
    # The antenna: a stack of Yagi booms along z on a lattice frame, rotating about the mast.
    a = b['antenna']
    ag = Geo(40)
    ag.extend(cyl((0, 5.2, -1.4), (0, 5.5, -1.4), 0.28, 'paint', n=14, bevel=0.015))
    ag.extend(box((-3.7, 5.45, -1.48), (3.7, 5.6, -1.32), 'steel'))
    ag.extend(box((-3.7, 7.1, -1.46), (3.7, 7.2, -1.34), 'steel'))
    ag.extend(box((-3.7, 5.91, -1.44), (3.7, 5.99, -1.36), 'steel'))
    for x in (-3.6, -1.2, 1.2, 3.6):
        ag.extend(box((x - 0.05, 5.6, -1.45), (x + 0.05, 7.1, -1.35), 'steel'))
    # Four columns of Yagi elements: a boom along z every row, director rods across x.
    for xc in (-2.4, 0.0, 2.4):
        for y in (6.3, 6.95):
            ag.extend(bar((xc, y, -1.9), (xc, y, 1.2), 0.035, 0.035, 'steel'))
            for z in np.linspace(-1.85, 1.1, 7):
                ln = 0.55 if z < -1.5 else (0.46 if z > 0.6 else 0.5)
                ag.extend(bar((xc - ln, y, z), (xc + ln, y, z), 0.016, 0.016, 'steel'))
        ag.extend(bar((xc, 5.99, -1.9), (xc, 5.99, 1.2), 0.04, 0.04, 'steel'))
    a.add(ag)
    return b.finish()


def radar_truck(id, W, cabz, zrear, shelter_z, ped_z, panel, rear_boxes, paintdeck=(1.05, 1.3), cab_hi=3.05, wn=12, western=True, extra=None):
    """TRML-4D / ELM-2084 style: a western cab, a deck with a shelter behind the cab and a rotating pedestal with a
    big flat array at the rear; the array (part 'panel') tilts on the pedestal head (part 'antenna')."""
    b = Build(id)
    body = b.body
    z0, z1 = cabz
    cab = western_cab(b, z0, z1, 1.05, cab_hi, W)
    chassis(b, paintdeck, zrear, z0, w=W, zfront=z1 - 0.1, wn=wn, jacks='stowed')
    western_front(b, cab, W, paintdeck[0], z1, 1.05)
    g = Geo(40)
    szlo, szhi = shelter_z
    shelter(g, (-W + 0.02, paintdeck[1], szlo), (W - 0.02, paintdeck[1] + 1.9, szhi), door='back')
    for lo, hi in rear_boxes:
        g.extend(K.rbox(lo, hi, 'paint', r=0.03))
    for s in (-1, 1):
        g.extend(box((s * (W - 0.01) - 0.02, paintdeck[1] + 0.6, szhi - 1.2), (s * (W - 0.01) + 0.02, paintdeck[1] + 1.0, szhi - 0.7), 'metal_dark'))
    # Four deployed-style levelling feet are folded: stowed jacks at the rear corners are on the chassis.
    if extra:
        extra(g)
    body.add(g)
    # Pedestal.
    an = b['antenna']
    pg = Geo(40)
    ay = an.pivot[1]
    pg.extend(cyl((0, ay - 0.0, ped_z), (0, ay + 0.4, ped_z), 0.45, 'paint', n=20, bevel=0.02))
    pg.extend(K.rbox((-0.5, ay + 0.3, ped_z - 0.3), (0.5, ay + 0.42, ped_z + 0.3), 'metal_dark', r=0.02))
    an.add(pg)
    # The array: face towards +z, a deep electronics unit behind, cooling fins, trunnions at the foot.
    pn = b['panel']
    pw, y0p, y1p, zface, depth = panel
    qg = Geo(40)
    qg.extend(K.array_face((0, (y0p + y1p) / 2, zface), pw, y1p - y0p, (0, 0, 1), (0, 1, 0), 'aesa', frame=0.1, depth=depth, back='paint'))
    qg.extend(K.rbox((-pw * 0.33, y0p + 0.3, zface - depth - 0.45), (pw * 0.33, y1p - 0.5, zface - depth), 'paint', r=0.04))
    for k in range(7):
        x = -pw * 0.3 + k * pw * 0.1
        qg.extend(box((x - 0.012, y0p + 0.35, zface - depth - 0.52), (x + 0.012, y1p - 0.55, zface - depth - 0.45), 'metal_dark'))
    for s in (-1, 1):
        qg.extend(K.rbox((s * (pw / 2 - 0.12) - 0.1, y0p - 0.08, zface - depth - 0.1), (s * (pw / 2 - 0.12) + 0.1, y0p + 0.25, zface + 0.05), 'metal_dark', r=0.02))
        qg.extend(box((s * 0.9 - 0.04, y1p, zface - depth), (s * 0.9 + 0.04, y1p + 0.18, zface - depth + 0.1), 'metal_dark'))
    qg.extend(cyl((-pw / 2 + 0.2, pn.pivot[1], zface - depth - 0.1), (pw / 2 - 0.2, pn.pivot[1], zface - depth - 0.1), 0.07, 'metal_dark', n=10))
    pn.add(qg)
    return b.finish()


def trml4d():
    return radar_truck('trml4d', 1.25, (2.6, 4.3), -4.35, (-0.6, 2.5), -2.7, (2.7, 2.75, 4.9, -2.45, 0.35),
                       [((-0.55, 1.3, -3.4), (0.55, 2.2, -2.2)), ((0.4, 1.3, -1.45), (1.2, 2.2, -0.7))])


def elm2084():
    return radar_truck('elm2084', 1.25, (2.8, 4.6), -4.65, (-0.4, 2.7), -2.8, (3.6, 2.9, 5.3, -2.5, 0.4),
                       [((-0.6, 1.3, -3.5), (0.6, 2.4, -2.2)), ((0.45, 1.3, -1.3), (1.2, 2.1, -0.55))], wn=11)


def trailer_guards(g, z_axles, x, r, y):
    """Mudguards over a trailer's axle group (both sides) with flaps."""
    for sgn in (-1, 1):
        x0, x1 = (x - 0.34, x + 0.24) if sgn > 0 else (-x - 0.24, -x + 0.34)
        g.extend(K.flat_guard(x0, x1, min(z_axles) - 0.8, max(z_axles) + 0.8, y, 'paint', lip=0.14, flap=0.3))


def landing_gear(g, z, y_top, x=0.9):
    for s in (-1, 1):
        g.extend(box((s * x - 0.07, 0.35, z - 0.07), (s * x + 0.07, y_top, z + 0.07), 'metal_dark'))
        g.extend(box((s * x - 0.2, 0.0, z - 0.14), (s * x + 0.2, 0.06, z + 0.14), 'chassis'))
    g.extend(box((-x, 0.7, z - 0.03), (x, 0.76, z + 0.03), 'metal_dark'))


def mpq65():
    b = Build('mpq65', density=15)
    body = b.body
    W = 1.23
    cab = western_cab(b, 5.0, 6.5, 1.2, 3.1, W)
    western_front(b, cab, W, 1.25, 6.5, 1.2)
    chassis(b, (1.3, 1.7), -6.4, 0.9, w=1.25, zfront=6.3, rear_guards=False, tanks=False, deck_plate=False, wn=11, jacks=None)
    g = Geo(40)
    # Tractor: fenders over the two steered axles are in chassis(); mudguards for the rear tandem, the fifth wheel plate.
    for sgn in (-1, 1):
        x0, x1 = (0.66, 1.3) if sgn > 0 else (-1.3, -0.66)
        g.extend(K.flat_guard(x0, x1, 1.0, 3.7, 1.35 + 0.0, 'paint', lip=0.15, flap=0.0))
    g.extend(K.rbox((-1.2, 1.7, 0.6), (1.2, 2.05, 3.4), 'paint', r=0.03))
    g.extend(box((-1.1, 1.15, 1.7), (1.1, 1.35, 3.6), 'chassis'))
    # The crew / equipment module behind the cab, with louvres and a side door.
    g.extend(K.kung((-1.1, 1.2, 3.6), (1.1, 2.7, 5.0), 'paint', door=None, ribs=True, windows=((4.3, 2.0, 1), (4.3, 2.0, -1))))
    # Trailer: the long shelter, the generator box under the deck, tail lamps, the landing legs.
    g.extend(box((-1.25, 1.3, -6.4), (1.25, 1.7, 0.9), 'paint'))
    shelter(g, (-1.25, 1.7, -5.6), (1.25, 3.35, 0.6), door='back')
    g.extend(K.rbox((-1.25, 1.55, -6.44), (1.25, 1.75, -6.3), 'chassis', r=0.02))
    for sgn in (-1, 1):
        g.extend(K.lamp_cluster((sgn * 1.1, 1.3, -6.43), sgn))
    trailer_guards(g, [-3.9, -4.95], 1.0, 0.58, 1.27)
    landing_gear(g, 0.9, 1.3, 0.9)
    g.extend(K.whip_antenna((-1.0, 3.0, 5.6), 1.1))
    g.extend(K.exhaust_stack(1.15, 1.2, 3.2, 5.05, r=0.055))
    body.add(g)
    # The folded antenna array on the roof, hinged at the rear.
    a = b['array']
    ag = Geo(40)
    ag.extend(box((-1.4, 3.35, -5.7), (1.4, 3.45, -2.9), 'chassis'))
    ag.extend(K.array_face((0, 3.76, -4.3), 2.6, 2.5, (0, 1, 0), (0, 0, 1), 'aesa_green', frame=0.1, depth=0.32, back='paint'))
    for sgn in (-1, 1):
        ag.extend(K.rbox((sgn * 1.2 - 0.12, 3.45, -5.7), (sgn * 1.2 + 0.12, 3.8, -5.45), 'metal_dark', r=0.02))
        ag.extend(box((sgn * 1.35 - 0.12, 3.75, -4.8), (sgn * 1.35 + 0.12, 3.9, -3.6), 'paint'))
    a.add(ag)
    return b.finish()


def st68():
    b = Build('st68', density=15)
    body = b.body
    cab = ural_cab(b, body, 1.2, 3.6, 5.0, 6.7, 1.15, 2.85, 2.0, b.r.seats, 1.35)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.15 + 0.85, tilt=-62)
    chassis(b, (1.1, 1.3), -6.7, 0.8, w=1.2, zfront=6.5, rear_guards=False, tanks=False, deck_plate=False, wn=12, jacks=None)
    g = Geo(40)
    for sgn in (-1, 1):
        x0, x1 = (0.68, 1.3) if sgn > 0 else (-1.3, -0.68)
        g.extend(K.flat_guard(x0, x1, 1.0, 3.9, 1.4, 'paint', lip=0.14, flap=0.0))
    # Tractor body behind the cab: equipment box, tool lockers, fifth wheel.
    g.extend(K.kung((-1.2, 1.55, 0.6), (1.2, 1.95, 3.6), 'paint', door=None, ribs=False))
    # Trailer: floor, the control cabin right behind the tractor, the generator under the floor.
    g.extend(box((-1.25, 1.3, -6.7), (1.25, 1.6, 0.8), 'paint'))
    shelter(g, (-1.2, 1.6, -1.1), (1.2, 3.1, 0.6), door='back')
    g.extend(K.rbox((-1.25, 1.1, -6.74), (1.25, 1.3, -6.5), 'chassis', r=0.02))
    for sgn in (-1, 1):
        g.extend(K.lamp_cluster((sgn * 1.1, 1.3, -6.73), sgn))
    trailer_guards(g, [-4.4, -5.5], 1.0, 0.6, 1.3)
    landing_gear(g, 0.0, 1.3, 0.9)
    # The antenna's pedestal on the trailer deck, rotating base plate.
    g.extend(K.rbox((-0.9, 1.6, -4.3), (0.9, 2.1, -2.5), 'paint', r=0.04))
    g.extend(K.exhaust_stack(1.1, 1.3, 2.9, 5.1, r=0.055))
    g.extend(K.whip_antenna((-0.9, 3.1, -0.4), 1.2))
    body.add(g)
    # Rotating head: turntable, the frame arms and the feed horn beam.
    a = b['antenna']
    ag = Geo(40)
    ag.extend(cyl((0, 2.1, -3.4), (0, 2.5, -3.4), 0.7, 'paint', n=20, bevel=0.02))
    for sgn in (-1, 1):
        ag.extend(K.rbox((sgn * 0.5 - 0.06, 2.4, -3.4), (sgn * 0.5 + 0.06, 2.52, -1.6), 'steel', r=0.015))
    ag.extend(K.rbox((-2.6, 2.45, -1.7), (2.6, 2.85, -1.4), 'paint', r=0.03))
    for k in range(9):
        x = -2.4 + 0.6 * k
        ag.extend(box((x - 0.05, 2.85, -1.68), (x + 0.05, 2.97, -1.45), 'metal_dark'))
    a.add(ag)
    # The reflector: a cylindrical mesh dish, ribbed, on a spine with the feed struts.
    r_ = b['reflector']
    rg = Geo(40)
    ys = np.linspace(2.5, 5.1, 9)
    zc = (ys[0] + ys[-1]) / 2
    pts_l = []
    for sx in (-3.5, 3.5):
        pts_l.append([V(sx, y, -3.1 + 0.28 * ((y - zc) / 1.3) ** 2) for y in ys])
    gf = geom.loft(pts_l, 'lattice', closed=False)
    rg.extend(gf)
    rg.extend(K.flipped(geom.loft(pts_l, 'lattice', closed=False)))
    for x in (-3.5, -1.75, 0.0, 1.75, 3.5):
        rg.extend(geom.tube([V(x, y, -3.1 + 0.28 * ((y - zc) / 1.3) ** 2 - 0.07) for y in ys], 0.05, 'steel', n=4))
    for y in (2.5, 5.1):
        rg.extend(geom.tube([V(sx, y, -3.1 + 0.28 * ((y - zc) / 1.3) ** 2 - 0.05) for sx in (-3.55, 3.55)], 0.05, 'steel', n=4))
    rg.extend(K.rbox((-0.2, 2.5, -3.95), (0.2, 4.9, -3.25), 'paint', r=0.03))
    rg.extend(K.rbox((-3.2, 3.7, -3.7), (3.2, 3.85, -3.3), 'paint', r=0.02))
    r_.add(rg)
    return b.finish()


BUILDERS = {'p18': p18, 'trml4d': trml4d, 'elm2084': elm2084, 'mpq65': mpq65, 'st68': st68}
