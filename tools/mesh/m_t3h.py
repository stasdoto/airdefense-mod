"""trucks3, part H: Bukovel, TB2 ground station, and the tracked radars / EW vehicles (Zoopark, Kupol, Borisoglebsk)."""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar
from m_trucks3 import *
from m_t3c import western_front, shelter, landing_gear
from m_t3d import light_truck


def bukovel():
    b = Build('bukovel', density=18)
    light_truck(b, zf=2.7, zcowl=1.2, zrear=-0.3, y_bot=0.74, hood=1.2, roof=1.9, w=0.93, bed=(-2.4, -0.3), open_bed=0.4)
    g = Geo(40)
    # The equipment cabinet in the bed, a rear step, the ground spikes.
    g.extend(K.kung((-0.8, 0.95, -2.2), (0.8, 1.7, -0.6), 'paint', door='back', ribs=False))
    for x in (-0.6, -0.2, 0.2, 0.6):
        g.extend(cyl((x, 1.7, -0.87), (x, 2.4, -0.87), 0.025, 'metal_dark', n=6))
        g.extend(cyl((x, 1.7, -0.87), (x, 1.78, -0.87), 0.05, 'metal_dark', n=6))
    b.body.add(g)
    m = b['mast']
    mg = Geo(40)
    mg.extend(bar((0, 1.71, -2.05), (0, 1.71, 0.6), 0.12, 0.12, 'paint'))
    mg.extend(K.array_face((0, 1.72, 0.92), 0.9, 0.26, (0, 0, 1), (0, 1, 0), 'aesa', frame=0.05, depth=0.28, back='paint'))
    for z in (-1.6, -1.0, -0.4):
        mg.extend(box((-0.3, 1.64, z - 0.04), (0.3, 1.78, z + 0.04), 'metal_dark'))
    m.add(mg)
    return b.finish()


def tb2_gcs():
    b = Build('tb2_gcs', density=15)
    body = b.body
    cab = kamaz_cab(b, body, 1.2, 2.5, 4.1, 1.3, 2.95, b.r.seats, 0.8)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=1.3 + 0.8, tilt=-62)
    chassis(b, (1.1, 1.2), -2.4, 2.5, w=1.25, zfront=3.95, wn=11, jacks=None, rear_guards=True, bolts=2, tanks=True)
    g = Geo(40)
    # The control shelter with a roof mast, the dish and the data-link whip.
    shelter(g, (-1.25, 1.2, -2.4), (1.25, 3.3, 2.35), door='back', windows=((0.5, 2.3, 1), (0.5, 2.3, -1), (-1.2, 2.3, 1), (-1.2, 2.3, -1)))
    g.extend(cyl((0, 3.3, 0.1), (0, 3.9, 0.1), 0.1, 'metal_dark', n=8))
    g.extend(K.rbox((-0.6, 3.7, -0.15), (0.6, 4.9, 0.1), 'paint', r=0.03))
    g.extend(geom.panel((0, 4.3, 0.102), 1.1, 1.1, (1, 0, 0), (0, 1, 0), 'aesa_light'))
    g.extend(K.rbox((-0.9, 3.3, -2.0), (-0.7, 4.8, -1.8), 'metal_dark', r=0.02))
    g.extend(K.rbox((-1.0, 4.8, -2.1), (-0.6, 5.2, -1.7), 'paint', r=0.02))
    g.extend(K.exhaust_stack(1.2, 1.2, 3.1, 2.55, r=0.055))
    # The trailer behind: a long low deck on its own axle, drawbar, guards over its wheels.
    g.extend(box((-0.12, 0.7, -3.6), (0.12, 0.85, -2.4), 'metal_dark'))
    g.extend(K.rbox((-1.2, 1.3, -9.6), (1.2, 1.45, -3.6), 'paint', r=0.02))
    for z in (-9.3, -8.0, -6.6, -5.0, -3.8):
        g.extend(box((-0.9, 1.1, z - 0.06), (0.9, 1.3, z + 0.06), 'chassis'))
    for s in (-1, 1):
        x0, x1 = (0.76, 1.34) if s > 0 else (-1.34, -0.76)
        g.extend(K.flat_guard(x0, x1, -7.4, -5.8, 1.27, 'paint', lip=0.14, flap=0.25))
        g.extend(K.lamp_cluster((s * 1.0, 1.3, -9.62), s))
    g.extend(box((-0.35, 0.8, -2.9), (0.35, 0.88, -2.4), 'chassis'))
    body.add(g)
    for k in range(2):
        cp = b['crate_%d' % k]
        s = -1 if k == 0 else 1
        x0, x1 = (-1.15, -0.05) if k == 0 else (0.05, 1.15)
        cg = Geo(40)
        cg.extend(K.rbox((x0, 1.45, -9.4), (x1, 2.2, -3.8), 'paint', r=0.04))
        cg.extend(box((x0 - 0.01, 1.8, -9.41), (x1 + 0.01, 1.86, -3.79), 'metal_dark'))
        for z in np.linspace(-9.1, -4.1, 6):
            cg.extend(K.band((x0, 1.45, z - 0.05), (x1, 2.2, z + 0.05), 2, z, 0.1, 0.02, mat='paint', faces=('top', 'left', 'right')))
        for z in (-9.0, -4.2):
            cg.extend(box((x0 + 0.35, 2.2, z - 0.05), (x1 - 0.35, 2.28, z + 0.05), 'metal_dark'))
        cp.add(cg)
    return b.finish()


# ---------------------------------------------------------------------------------------------------------------
# Tracked


def tracks(b, **kw):
    """The tank kit's running gear (road wheels, sprocket, idler, belts) on this model; the box skeleton's wheel parts are
    replaced."""
    import m_tanks
    drop = [p for p in b.m.parts if p.name.startswith(('road_', 'sprocket_', 'idler_'))]
    for p in drop:
        b.m.parts.remove(p)
        if p in b.m.roots:
            b.m.roots.remove(p)
        b.p.pop(p.name, None)
    bm = m_tanks.box_models()[b.m.id]
    gear = m_tanks.Gear(**kw)
    m_tanks.running_gear(b.m, bm, gear)


def shell_geo(prof, w, r=0.09, mat='paint'):
    """A swept body from a side profile [(z, y, corner radius)], rounded corners."""
    pts, _ = K.tag_round([(p[0], p[1]) for p in prof], [p[2] for p in prof])
    return K.Shell(pts, w, r=r, steps=2).geo(mat)


def fenders(g, x0, x1, y, z0, z1):
    for s in (-1, 1):
        lo, hi = (min(s * x0, s * x1), max(s * x0, s * x1))
        g.extend(K.rbox((lo, y, z0), (hi, y + 0.06, z1), 'paint', r=0.02))
        g.extend(box((hi - 0.03 if s > 0 else lo, y - 0.05, z0), (hi if s > 0 else lo + 0.03, y, z1), 'paint'))


def mtlb_hull(g, w, z0, z1, ytop, ybot=0.45, zbow=None):
    zb = z1 if zbow is None else zbow
    prof = [(z0, ybot, 0.05), (z1 - 0.45, ybot, 0.06), (zb, ybot + 0.4, 0.08), (zb, ytop, 0.08), (z0, ytop, 0.06)]
    g.extend(shell_geo(prof, w))


def zoopark():
    b = Build('zoopark', density=17)
    body = b.body
    tracks(b, drive='front', n=10, pitch=0.16)
    g = Geo(40)
    g.extend(K.rbox((-1.0, 0.4, -3.4), (1.0, 0.8, 3.3), 'chassis', r=0.03))
    prof = [(-3.6, 0.8, 0.07), (3.55, 0.55, 0.08), (2.5, 1.84, 0.1), (-3.6, 1.87, 0.07)]
    g.extend(shell_geo(prof, 1.4, 0.1))
    fenders(g, 1.0, 1.44, 0.77, -3.5, 3.55)
    # Driver's hatches with periscopes, the engine deck's louvres, the rear generator box and exhaust.
    for s in (-1, 1):
        xc = s * 0.65
        g.extend(K.rbox((xc - 0.4, 1.87, 1.6), (xc + 0.4, 1.97, 2.3), 'paint', r=0.03))
        g.extend(geom.panel((xc, 1.92, 2.302), 0.5, 0.1, (1, 0, 0), (0, 1, 0), 'glass'))
        g.extend(box((xc - 0.22, 1.97, 1.85), (xc + 0.22, 2.0, 2.05), 'metal_dark'))
        g.extend(K.headlamp((s * 1.0, 1.17, 3.35), r=0.07, depth=0.07))
        g.extend(K.lamp_cluster((s * 0.95, 1.3, -3.62), s))
    g.extend(geom.panel((0, 1.872, -3.0), 2.0, 1.0, (1, 0, 0), (0, 0, -1), 'grille'))
    g.extend(K.rbox((0.75, 1.87, -3.4), (1.35, 2.35, -2.6), 'chassis', r=0.03))
    g.extend(K.rbox((-1.22, 1.87, -2.8), (-0.98, 2.25, -2.4), 'chassis', r=0.03))
    g.extend(K.handrail([(-1.3, 1.87, -3.3), (-1.3, 3.4, -3.3)]))
    g.extend(cyl((-1.26, 3.4, -3.26), (-1.26, 3.6, -3.26), 0.02, 'black', n=4))
    g.extend(K.whip_antenna((1.15, 1.9, 1.2), 1.4))
    body.add(g)
    # The slope plate (rotated into the glacis by the skeleton): hull front lid with the lights' guards.
    sl = b['slope1']
    sg = Geo(40)
    sg.extend(K.rbox((-1.38, 1.84, 2.5), (1.38, 1.9, 4.25), 'paint', r=0.01))
    for s in (-1, 1):
        sg.extend(box((s * 0.4 - 0.2, 1.9, 2.8), (s * 0.4 + 0.2, 1.93, 3.2), 'metal_dark'))
    sg.extend(box((-0.02, 1.9, 2.6), (0.02, 1.95, 4.1), 'paint'))
    sl.add(sg)
    # The radar array folded flat on the roof.
    a = b['array']
    ag = Geo(40)
    ag.extend(box((-1.38, 2.08, -2.55), (1.38, 2.14, 1.75), 'chassis'))
    ag.extend(K.array_face((0, 2.4, -0.4), 2.6, 4.1, (0, 1, 0), (0, 0, 1), 'aesa_green', frame=0.1, depth=0.26, back='paint'))
    ag.extend(K.rbox((-0.6, 2.4, -1.6), (0.6, 2.62, 0.4), 'paint', r=0.03))
    a.add(ag)
    return b.finish()


def kupol():
    b = Build('kupol', density=15)
    body = b.body
    tracks(b, drive='front', n=10, pitch=0.18)
    g = Geo(40)
    g.extend(K.rbox((-1.1, 0.45, -4.5), (1.1, 1.05, 4.5), 'chassis', r=0.03))
    mtlb_hull(g, 1.6, -4.5, 4.6, 1.8, ybot=1.0, zbow=4.6)
    fenders(g, 1.1, 1.64, 0.97, -4.5, 4.55)
    # Crew cab with the windscreen, the rear shelter, roof units.
    prof = [(2.9, 1.75, 0.03), (4.3, 1.75, 0.03), (4.3, 2.0, 0, 'ws0'), (4.2, 2.5, 0, 'ws1'), (4.1, 2.7, 0.07), (2.9, 2.7, 0.05)]
    cab = K.Cab(prof, 1.6, r=0.07)
    cab.windscreen([(-1.4, -0.06), (0.06, 1.4)])
    cab.side_window(3.2, 4.0, 2.05, 2.5, follow=0.05)
    g.extend(cab.geo())
    g.extend(cab.lining())
    g.extend(K.rbox((-1.55, 1.9, -4.4), (1.55, 2.85, 1.4), 'paint', r=0.05))
    g.extend(K.rbox((-1.1, 2.85, -4.2), (1.1, 3.05, -3.0), 'chassis', r=0.03))
    g.extend(geom.panel((0, 3.052, -3.6), 1.7, 1.0, (1, 0, 0), (0, 0, -1), 'grille'))
    g.extend(K.rbox((-1.0, 2.85, -2.0), (1.0, 3.15, 0.0), 'chassis', r=0.03))
    for s in (-1, 1):
        g.extend(K.headlamp((s * 1.2, 1.37, 4.62), r=0.075, depth=0.07))
        g.extend(K.lamp_cluster((s * 1.2, 1.25, -4.52), s))
        g.extend(box((s * 1.56 - 0.03, 2.1, 0.1), (s * 1.56 + 0.03, 2.6, 0.9), 'metal_dark'))
        g.extend(K.mirror((s * 1.6, 2.5, 4.0), (s * 1.85, 2.6, 4.2), (0.14, 0.26), s))
    g.extend(box((-1.0, 2.7, 3.2), (1.0, 2.73, 3.5), 'metal_dark'))
    g.extend(K.whip_antenna((-1.2, 2.72, 3.0), 1.5))
    body.add(g)
    an = b['antenna']
    ag = Geo(40)
    ag.extend(cyl((0, 3.15, -1.0), (0, 3.4, -1.0), 0.75, 'paint', n=20, bevel=0.02))
    ag.extend(K.rbox((-0.25, 3.4, -1.6), (0.25, 4.2, -1.2), 'metal_dark', r=0.03))
    an.add(ag)
    pn = b['panel']
    pg = Geo(40)
    pg.extend(K.array_face((0, 4.75, -0.8), 2.6, 2.3, (0, 0, 1), (0, 1, 0), 'aesa', frame=0.1, depth=0.3, back='paint'))
    pg.extend(K.rbox((-1.0, 3.9, -1.5), (1.0, 5.5, -1.1), 'paint', r=0.04))
    for k in range(7):
        x = -0.8 + k * 0.27
        pg.extend(box((x - 0.012, 3.95, -1.55), (x + 0.012, 5.45, -1.5), 'metal_dark'))
    pg.extend(cyl((-1.2, 3.65, -1.15), (1.2, 3.65, -1.15), 0.07, 'metal_dark', n=10))
    pn.add(pg)
    for nm, s in (('wing_l', -1), ('wing_r', 1)):
        wg = Geo(40)
        wg.extend(K.array_face((s * 2.25, 4.75, -0.8), 1.9, 2.3, (0, 0, 1), (0, 1, 0), 'aesa', frame=0.1, depth=0.3, back='paint'))
        b[nm].add(wg)
    return b.finish()


def borisoglebsk():
    b = Build('borisoglebsk', density=17)
    body = b.body
    tracks(b, drive='front', n=10, pitch=0.16)
    g = Geo(40)
    mtlb_hull(g, 1.33, -3.2, 3.55, 1.5, ybot=0.45)
    fenders(g, 1.0, 1.44, 1.0, -3.1, 3.05)
    g.extend(K.rbox((-1.3, 1.5, -3.0), (1.3, 2.75, 0.8), 'paint', r=0.06))
    for s in (-1, 1):
        g.extend(geom.panel((s * 1.3 + s * 0.003, 2.2, -1.1), 1.2, 0.5, (0, 0, 1) if s > 0 else (0, 0, -1), (0, 1, 0), 'glass_dark'))
        g.extend(K.headlamp((s * 1.0, 1.37, 3.56), r=0.07, depth=0.06))
        g.extend(K.lamp_cluster((s * 1.0, 1.2, -3.22), s))
    # Driver's compartment on the left with a hatch, the commander's cupola, the troop hatches on the roof.
    g.extend(K.rbox((-1.1, 1.5, 1.3), (-0.2, 2.0, 2.4), 'paint', r=0.04))
    g.extend(geom.panel((-0.65, 1.85, 2.402), 0.8, 0.2, (1, 0, 0), (0, 1, 0), 'glass'))
    g.extend(cyl((0.55, 1.5, 1.6), (0.55, 1.62, 1.6), 0.3, 'paint', n=14, bevel=0.015))
    g.extend(K.rbox((0.2, 2.75, -2.2), (1.0, 2.9, -1.0), 'chassis', r=0.02))
    g.extend(geom.panel((0, 2.752, -2.0), 1.0, 0.8, (1, 0, 0), (0, 0, -1), 'grille'))
    # Stowed masts at the back.
    for x, h in ((-0.9, 3.75), (0.9, 3.55), (0.0, 4.05)):
        g.extend(cyl((x, 2.75, -2.56), (x, h, -2.56), 0.04, 'metal_dark', n=6))
        g.extend(cyl((x, 2.75, -2.56), (x, 2.95, -2.56), 0.09, 'chassis', n=8))
    body.add(g)
    m = b['mast']
    mg = Geo(40)
    mg.extend(bar((0, 2.775, -2.3), (0, 2.775, 3.3), 0.24, 0.25, 'chassis'))
    for z, hw in ((0.6, 1.4), (1.8, 1.1), (3.0, 0.8)):
        mg.extend(box((-hw, 2.7, z - 0.05), (hw, 2.85, z + 0.05), 'metal_dark'))
        for s in (-1, 1):
            mg.extend(box((s * hw - 0.06, 2.6, z - 0.06), (s * hw + 0.06, 2.95, z + 0.06), 'steel'))
    mg.extend(K.rbox((-0.35, 2.6, 3.3), (0.35, 3.0, 3.7), 'paint', r=0.03))
    mg.extend(geom.panel((0, 2.8, 3.702), 0.6, 0.35, (1, 0, 0), (0, 1, 0), 'aesa_light'))
    m.add(mg)
    return b.finish()


BUILDERS = {'bukovel': bukovel, 'tb2_gcs': tb2_gcs, 'zoopark': zoopark, 'kupol': kupol, 'borisoglebsk': borisoglebsk}
