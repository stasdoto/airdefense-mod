"""
2.0: the Gepard (Leopard-1 hull, a big welded turret, the lattice search radar on the rear roof (the part "radar" spins), the
tracking radar dish in front between the guns, a 35 mm gun on each side of the turret). The hand-made box model
(VehicleModels.gepard) is read back through kit_trucks.java_ref: the parts, pivots and parents the game animates.
"""
import math
from types import SimpleNamespace

import numpy as np

import kit_tanks as K
import kit_trucks as KT
import m_arty as A
import m_tanks as MT
import m_west as W
from core import Geo
from geom import cyl, lathe, panel, tube


def shim():
    r = KT.java_ref('gepard')
    parts = [SimpleNamespace(name=p.name, pivot=p.pivot, rot=p.rot, boxes=[SimpleNamespace(lo=lo, hi=hi) for lo, hi, _ in p.boxes]) for p in r.parts]
    return SimpleNamespace(id='gepard', paint='nato', wheels=r.wheels, parts=parts, rails=r.rails), r


def gepard(_bm=None):
    bm, r = shim()
    m = MT.new_model(bm, density=22)
    body = m.part('body', (0, 0, 0))
    # The hull: the box between the tracks, the shallow glacis at the front, the rear sloping down to the back plate.
    st = [(-3.38, 1.3, 0.6), (-3.3, 1.62, 0.56), (2.62, 1.62, 0.56), (3.12, 1.44, 0.75), (3.44, 1.25, 0.94)]
    g = Geo(25)
    g.extend(K.section_loft(MT.hull_sections(st, 1.45, 1.62, 0.9, ch=0.06), 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    # Track guards: shelves over the tracks, sloping down at the front, with the stowage and tool boxes; side skirts.
    for s in (-1, 1):
        prof = [(-3.5, 0.95), (-3.4, 1.06), (2.5, 1.06), (3.3, 0.92)]
        d.extend(K.fender(s * 1.6, s * 2.25, prof, 0.03))
        d.extend(K.chbox((min(s * 1.68, s * 2.2), 1.06, -2.9), (max(s * 1.68, s * 2.2), 1.3, -1.6), 'armour', 0.04))
        d.extend(K.chbox((min(s * 1.68, s * 2.2), 1.06, -1.4), (max(s * 1.68, s * 2.2), 1.25, -0.5), 'armour', 0.04))
        d.extend(K.tow_cable([(s * 1.72, 1.08, -3.3), (s * 1.74, 1.1, -0.4), (s * 1.74, 1.1, 1.5), (s * 1.7, 1.0, 2.5)]))
        d.extend(K.headlight((s * 1.95, 1.2, 3.1), (0, 0, 1), r=0.07))
        d.extend(K.chbox((s * 1.95 - 0.13, 1.0, 3.0), (s * 1.95 + 0.13, 1.07, 3.2), 'armour', 0.01))
        d.extend(K.chbox((s * 2.0 - 0.14, 0.5, -3.52), (s * 2.0 + 0.14, 0.95, -3.48), 'skirt_rubber', 0.005))
    A.side_skirts(d, 2.2, -2.85, 2.7, 6, y0=0.62, y1=1.0, mat='armour_dark')
    A.tail_lamps(d, (-1.3, 1.3), 1.3, -3.39)
    A.tow_hooks(d, 3.44, y=0.9, x=0.8)
    # The driver's hatch with periscopes (front left), the engine deck grilles at the back, the exhaust, spare track links on the glacis.
    d.extend(K.hatch((-0.75, 1.62, 2.3), 0.27, h=0.05))
    for a in (-35, 0, 35):
        ar = math.radians(a)
        d.extend(K.periscope((-0.75 + math.sin(ar) * 0.32, 1.62, 2.3 + math.cos(ar) * 0.32 + 0.1), 0.14, (math.sin(ar), 0, math.cos(ar))))
    d.extend(MT.louvres(-1.4, -0.2, 1.62, -3.2, -2.0))
    d.extend(MT.louvres(0.2, 1.4, 1.62, -3.2, -2.0))
    d.extend(K.chbox((-1.0, 1.62, -1.95), (1.0, 1.65, -1.65), 'armour', 0.01))
    for s in (-1, 1):
        d.extend(K.chbox((s * 0.9 - 0.2, 1.46, 2.8), (s * 0.9 + 0.2, 1.52, 3.2), 'steel_worn', 0.01))
    d.extend(K.chbox((-0.5, 1.28, 3.2), (0.5, 1.34, 3.4), 'armour', 0.02))
    body.add(d)
    MT.running_gear(m, bm, A.gear_for(bm, rollers=[(-2.0, 0.78, 0.1), (-0.3, 0.78, 0.1), (1.4, 0.78, 0.1)], wheel_kind='single', n=10, thick=0.06))

    # The turret: a big welded box with sloped front and sides; ammunition housings and a rear bustle.
    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(1.0, 1.69), (1.19, 1.31), (1.19, -1.6), (0.9, -2.0), (-0.9, -2.0), (-1.19, -1.6), (-1.19, 1.31), (-1.0, 1.69)]
    tp.add(W.poly_turret(corners, [(1.62, 1.0, 1.0, 1.0), (2.15, 1.0, 1.0, 1.0), (2.55, 0.97, 0.93, 1.0), (2.75, 0.9, 0.78, 0.95)], smooth=22))
    t = Geo(30)
    roof = 2.75
    t.extend(W.cupola((0.6, roof, -0.7), 0.3, h=0.16, n_peri=6))
    t.extend(K.hatch((-0.6, roof, -0.9), 0.27, h=0.05))
    t.extend(K.chbox((-0.4, roof, 0.4), (0.4, roof + 0.12, 0.9), 'armour', 0.03))
    t.extend(panel((0, roof + 0.07, 0.902), 0.4, 0.07, (-1, 0, 0), (0, 1, 0), 'optic'))
    for s in (-1, 1):
        t.extend(K.chbox((min(s * 1.19, s * 1.44), 2.5, 0.25), (max(s * 1.19, s * 1.44), 2.75, 0.62), 'armour', 0.03))
        t.extend(K.smoke_launchers((s * 1.0, 2.4, 1.35), 4, (s * 0.5, 0, 0.87), spacing=0.11, length=0.26, elev=25, spread=6))
        t.extend(K.chbox((min(s * 1.19, s * 1.28), 1.9, -1.9), (max(s * 1.19, s * 1.28), 2.45, -0.9), 'armour_dark', 0.02))
    t.extend(K.chbox((-0.85, 2.0, -2.0), (0.85, 2.6, -1.95), 'armour_dark', 0.02))
    t.extend(cyl((0, roof, -1.5), (0, 3.2, -1.5), 0.13, 'metal_dark', n=8))
    t.extend(K.chbox((-0.14, roof, -1.58), (0.14, 3.2, -1.42), 'armour', 0.02))
    t.extend(K.antenna((-1.0, roof, -1.3), 2.2))
    tp.add(t)
    # The search radar: a flat lattice antenna on a rotating pedestal.
    rp = MT.mirror_part(m, bm, 'radar', tp)
    R = Geo(30)
    R.extend(cyl((0, 3.2, -1.5), (0, 3.38, -1.5), 0.2, 'metal_dark', n=10))
    R.extend(KT.array_face((0, 3.66, -1.5), 2.2, 0.52, (0, 0, 1), (0, 1, 0), mat='lattice', frame=0.06, depth=0.1, back='armour'))
    R.extend(K.chbox((-0.1, 3.38, -1.56), (0.1, 3.5, -1.44), 'metal_dark', 0.01))
    for x in (-0.9, 0.9):
        R.extend(tube([(x, 3.4, -1.46), (x, 3.7, -1.46)], 0.02, 'metal_dark', n=4))
    rp.add(R)

    # The guns part elevates: a receiver housing and one 35 mm barrel with its cooling sleeve and brake on each side,
    # the tracking radar (dish in a housing) in the middle in front.
    gp = MT.mirror_part(m, bm, 'guns', tp)
    G = Geo(40)
    by = float(gp.pivot[1])
    for s in (-1, 1):
        xa, xb = sorted((s * 1.25, s * 1.81))
        G.extend(K.chbox((xa, 1.94, -1.19), (xb, 2.62, 1.0), 'armour', 0.06))
        G.extend(K.chbox((xa + 0.05 * (1 if s > 0 else 0), 2.62, -0.8), (xb - 0.05 * (1 if s < 0 else 0), 2.74, 0.4), 'armour_dark', 0.02))
        x = s * 1.53
        z0, zm = 1.0, 4.1
        G.extend(lathe((x, by, z0), (x, by, zm), [(0, 0.0, 'metal_dark'), (0, 0.095, 'armour'), (0.1, 0.095, 'armour'), (1.5, 0.095, 'armour_dark'), (1.55, 0.06, 'metal_dark'),
                                               (2.4, 0.06, 'metal_dark'), (2.45, 0.048, 'steel_worn'), (zm - z0 - 0.45, 0.048, 'steel_worn'), (zm - z0 - 0.4, 0.085, 'metal_dark'),
                                               (zm - z0 - 0.05, 0.1, 'metal_dark'), (zm - z0, 0.085, 'metal_dark'), (zm - z0, 0.03, 'black'), (zm - z0 - 0.04, 0.0, 'black')], 'metal_dark', 10, 40))
        for k in range(4):
            za = z0 + 0.25 + k * 0.33
            G.extend(cyl((x, by, za), (x, by, za + 0.04), 0.1, 'armour_dark', n=10))
        # The feed chute from the housing up, the ejection chute below.
        G.extend(K.chbox((x - 0.12, 2.5, -0.5), (x + 0.12, 2.74, -0.1), 'armour', 0.02))
    # The tracking radar in front between the guns: a round housing, the dish, the feed horn on three struts.
    cz = 2.05
    G.extend(lathe((0, 2.75, 1.55), (0, 2.75, 2.1), [(0, 0.0, 'armour'), (0, 0.42, 'armour'), (0.1, 0.46, 'armour'), (0.4, 0.46, 'armour'), (0.55, 0.38, 'armour_dark'), (0.55, 0.0, 'armour_dark')], 'armour', 14, 40))
    G.extend(KT.dish((0, 2.75, 2.07), 0.46, (0, 0, 1), depth=0.2, mat='lattice', rim='metal_dark', n=14, rings=3))
    for a in (90, 210, 330):
        ar = math.radians(a)
        G.extend(tube([(math.cos(ar) * 0.4, 2.75 + math.sin(ar) * 0.4, 2.1), (0, 2.75, 2.32)], 0.012, 'metal_dark', n=4))
    G.extend(cyl((0, 2.75, 2.28), (0, 2.75, 2.45), 0.035, 'black', n=6))
    G.extend(K.chbox((-0.5, 2.2, 1.0), (0.5, 2.35, 1.6), 'armour', 0.03))
    gp.add(G)
    return m


JOBS = {'gepard': gepard}
