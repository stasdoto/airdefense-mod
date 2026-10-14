"""
2.0: BTR-4 and Stryker: eight-wheeled armoured carriers with a high hull over a narrow V-hull, glass and an inside behind it
(BTR-4), slat cages (Stryker), arches round military wheels, in the tanks' paint (the tanks' kit, the trucks' shells and wheels).
"""
import numpy as np

import kit_tanks as KK
import kit_trucks as K
import m_tanks as MT
import m_west as WW
from core import Geo
from geom import panel
from kit_trucks import cyl, tube
from m_wheel2 import m2


def _wheels8(m, bm, nseg=18):
    for name, R, steer in bm.wheels:
        bp = MT.bpart(bm, name)
        piv = np.array(bp.pivot)
        side = -1 if piv[0] < 0 else 1
        w = bp.boxes[0].hi[0] - bp.boxes[0].lo[0]
        m.tyre_half = w / 2
        m.tyre_lugs = nseg
        p = m.part(name, tuple(piv), (0, 0, 0))
        p.spin = R
        p.steer = steer
        p.add(K.mil_wheel(piv, side, min(R, piv[1] + 0.015), w, rim='armour', n=nseg, lugs=nseg, ctis=False, bolts=6))


def _axles8(g, bm):
    for name, R, steer in bm.wheels:
        if name.endswith('_l'):
            c = MT.bpart(bm, name).pivot
            g.extend(K.axle(abs(c[0]) - 0.2, c[1], c[2]))


def _arches(g, bm, lift=0.09, mat='armour'):
    for name, R, steer in bm.wheels:
        piv = np.array(MT.bpart(bm, name).pivot)
        s = -1 if piv[0] < 0 else 1
        xa, xb = sorted((s * (abs(piv[0]) - 0.28), s * (abs(piv[0]) + 0.3)))
        g.extend(K.fender(xa, xb, piv[2], piv[1], R + lift, -8, 188, mat, n=8))


def btr4(bm):
    m = MT.new_model(bm, density=22)
    body = m.part('body', (0, 0, 0))
    prof = [(-3.85, 1.25, 0.05), (3.9, 1.25, 0.05), (3.9, 1.4, 0.05), (3.72, 1.5, 0.0, 'ws0'), (3.55, 1.95, 0.0, 'ws1'), (3.45, 2.1, 0.14), (-3.75, 2.1, 0.12), (-3.85, 1.9, 0.08)]
    cab = K.Cab(prof, 1.47, r=0.2)
    cab.windscreen([(-1.2, -0.08), (0.08, 1.2)])
    cab.side_window(2.3, 3.2, 1.55, 1.92, follow=0.09, radius=0.04)
    body.add(cab.geo('armour'))
    K.cab_inside_part(body, 'cab_inside', cab, [s for s in bm.seats], dash_y=1.5, tilt=-62)
    g = Geo(30)
    # The lower hull (the V under the shelf), the nose with its bumper and the tow eyes, the headlamps.
    g.extend(KK.chbox((-0.9, 0.62, -3.7), (0.9, 1.3, 3.4), 'armour_dark', 0.08))
    g.extend(KK.chbox((-1.25, 0.72, 3.4), (1.25, 1.3, 3.9), 'armour', 0.08))
    for s in (-1, 1):
        g.extend(K.headlamp((s * 1.0, 1.0, 3.9), r=0.075, depth=0.08, body='armour'))
        g.extend(tube([(s * 0.5, 0.78, 3.9), (s * 0.5, 0.8, 4.02), (s * 0.5, 0.7, 4.05), (s * 0.5, 0.64, 3.92)], 0.03, 'metal_dark', n=6))
        g.extend(K.lamp_cluster((s * 1.0, 1.35, -3.855), s))
        g.extend(K.mirror((s * 1.47, 1.95, 3.45), (s * 1.75, 1.88, 3.62), (0.14, 0.3), s))
        # Side doors with their seams and firing ports, roof hatches for the troops.
        g.extend(KK.chbox((s * 1.47 - (0.012 if s > 0 else 0.02), 1.35, 0.1), (s * 1.47 + (0.02 if s > 0 else 0.012), 2.0, 1.25), 'armour_dark', 0.004))
        for z in (-0.4, -1.5, -2.5):
            g.extend(KK.chbox((s * 1.47 - (0.012 if s > 0 else 0.03), 1.55, z - 0.07), (s * 1.47 + (0.03 if s > 0 else 0.012), 1.7, z + 0.07), 'black', 0.01))
        for z in (-3.1, -2.3, -1.5):
            g.extend(KK.hatch((s * 0.65, 2.1, z), 0.2, h=0.04, handle=False))
    _arches(g, bm)
    _axles8(g, bm)
    g.extend(KK.chbox((-0.9, 2.1, -3.6), (0.9, 2.18, 1.5), 'armour', 0.03))
    g.extend(MT.louvres(-0.7, 0.7, 2.1, -3.75, -3.2))
    g.extend(KK.hatch((-0.7, 2.1, 3.0), 0.24, h=0.05))
    g.extend(KK.periscope((-0.7, 2.1, 3.3), 0.2, (0, 0, 1)))
    g.extend(KK.chbox((-0.8, 0.8, -3.95), (0.8, 1.9, -3.85), 'armour', 0.04))
    g.extend(K.whip_antenna((1.1, 2.1, -3.3), 1.8))
    g.extend(KK.tow_cable([(-1.3, 2.12, -3.6), (-1.3, 2.14, 0.0), (-1.2, 2.12, 2.5)]))
    body.add(g)
    _wheels8(m, bm)

    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(0.85, -0.6), (0.85, -0.2), (0.7, 0.2), (0.4, 0.38), (-0.4, 0.38), (-0.7, 0.2), (-0.85, -0.2), (-0.85, -1.4), (-0.7, -1.6), (0.7, -1.6), (0.85, -1.4)]
    tp.add(WW.poly_turret(corners, [(2.1, 1.0, 1.0, 1.0), (2.2, 1.0, 1.0, 1.0), (2.55, 0.97, 0.97, 1.0), (2.68, 0.9, 0.9, 0.95), (2.7, 0.86, 0.86, 0.93)]))
    t = Geo(30)
    t.extend(KK.hatch((-0.3, 2.68, -0.9), 0.22, h=0.05))
    t.extend(KK.chbox((0.3, 2.68, -0.9), (0.7, 2.95, -0.5), 'armour', 0.03))
    t.extend(panel((0.5, 2.82, -0.498), 0.22, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(KK.chbox((-1.14, 2.35, -1.0), (-0.9, 2.65, 0.3), 'armour', 0.03))
    for dz in (-0.15, 0.1):
        t.extend(cyl((-1.02, 2.5, dz - 0.5), (-1.02, 2.5, dz + 0.35), 0.06, 'armour_dark', n=8))
    t.extend(KK.smoke_launchers((0.9, 2.45, -0.1), 3, (1, 0, 0.35), spacing=0.1, r=0.04, length=0.28, elev=28, spread=8))
    t.extend(KK.antenna((-0.6, 2.62, -1.5), 1.6))
    tp.add(t)
    gp = MT.mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = MT.muzzle_abs(bm)
    gg = Geo(40)
    z0g = piv[2] + 0.1
    L = mz[2] - z0g
    gx = piv[0]
    gg.extend(KK.chbox((gx - 0.2, piv[1] - 0.13, piv[2] - 0.2), (gx + 0.2, piv[1] + 0.13, piv[2] + 0.3), 'armour', 0.04))
    gg.extend(KK.barrel((gx, piv[1], z0g), (gx, piv[1], mz[2]), [(0, 0.0, 'armour'), (0, 0.05, 'armour'), (L * 0.3, 0.04, 'armour'), (L * 0.33, 0.05, 'armour'), (L * 0.7, 0.05, 'armour'),
                                                                (L * 0.72, 0.04, 'armour'), (L - 0.2, 0.035, 'armour'), (L - 0.19, 0.05, 'metal_dark'), (L, 0.05, 'metal_dark'),
                                                                (L, 0.02, 'black'), (L - 0.05, 0.0, 'black')], n=10))
    gg.extend(cyl((gx - 0.12, piv[1] - 0.02, z0g), (gx - 0.12, piv[1] - 0.02, z0g + 0.6), 0.02, 'metal_dark', n=6))
    gp.add(gg)
    return m


def stryker(bm):
    m = MT.new_model(bm, density=22)
    body = m.part('body', (0, 0, 0))
    prof = [(-3.45, 1.15, 0.05), (2.91, 1.15, 0.05), (2.25, 1.77, 0.06), (2.2, 2.15, 0.14), (-3.35, 2.15, 0.12), (-3.45, 2.0, 0.08)]
    cab = K.Cab(prof, 1.36, r=0.2)
    body.add(cab.geo('armour'))
    g = Geo(30)
    # The V-shaped lower hull and its nose wedge down to the bumper.
    g.extend(KK.chbox((-0.84, 0.62, -3.3), (0.84, 1.2, 2.6), 'armour_dark', 0.08))
    nose = K.Cab([(2.0, 0.65, 0.03), (3.45, 0.65, 0.04), (2.55, 1.18, 0.0), (2.0, 1.18, 0.0)], 0.84, r=0.1)
    g.extend(nose.geo('armour'))
    for s in (-1, 1):
        g.extend(K.headlamp((s * 1.0, 0.95, 3.4), r=0.07, depth=0.08, body='armour', axis=(0, 0.3, 1)))
        g.extend(tube([(s * 0.55, 0.72, 3.4), (s * 0.55, 0.74, 3.55), (s * 0.55, 0.64, 3.58), (s * 0.55, 0.6, 3.45)], 0.03, 'metal_dark', n=6))
        g.extend(K.lamp_cluster((s * 1.0, 1.45, -3.455), s))
        g.extend(K.mirror((s * 1.36, 1.9, 2.25), (s * 1.6, 1.85, 2.4), (0.14, 0.3), s))
        # The slat-armour cage on the sides: a frame with mesh, hung on posts.
        g.extend(KK.chbox((min(s * 1.4, s * 1.5), 1.0, -2.8), (max(s * 1.4, s * 1.5), 1.9, 1.2), 'grille_mesh', 0.015))
        for z in np.linspace(-2.8, 1.2, 6):
            g.extend(KK.chbox((min(s * 1.4, s * 1.52), 0.98, z - 0.02), (max(s * 1.4, s * 1.52), 1.92, z + 0.02), 'armour', 0.008))
        g.extend(KK.chbox((min(s * 1.4, s * 1.52), 1.9, -2.8), (max(s * 1.4, s * 1.52), 1.95, 1.2), 'armour', 0.01))
        g.extend(KK.chbox((min(s * 1.4, s * 1.52), 0.95, -2.8), (max(s * 1.4, s * 1.52), 1.0, 1.2), 'armour', 0.01))
        g.extend(KK.smoke_launchers((s * 1.1, 2.0, 2.0), 3, (s * 0.7, 0, 0.7), spacing=0.12, length=0.28, elev=25, spread=8))
    # Vision blocks on the front of the cab, hatches and stowage on the roof.
    for x in (-0.85, -0.2):
        g.extend(KK.chbox((x - 0.22, 1.55, 2.38), (x + 0.22, 1.85, 2.44), 'armour', 0.01))
        g.extend(panel((x, 1.7, 2.442), 0.34, 0.2, (-1, 0, 0), (0, 1, 0), 'glass'))
    g.extend(KK.chbox((-1.1, 2.15, -2.8), (1.1, 2.3, 1.6), 'armour', 0.04))
    g.extend(KK.chbox((-1.2, 2.15, 1.55), (-0.55, 2.3, 2.2), 'armour', 0.04))
    g.extend(KK.hatch((-0.87, 2.3, 1.9), 0.25, h=0.05))
    g.extend(KK.hatch((0.6, 2.3, -0.5), 0.26, h=0.05))
    for k in range(3):
        g.extend(KK.hatch((-0.5 + k * 0.5, 2.3, -1.6), 0.18, h=0.04, handle=False))
    g.extend(KK.chbox((0.0, 2.15, -3.4), (0.9, 2.3, -2.9), 'armour_dark', 0.02))
    # The back: the ramp door with its seams and handle, the exhaust, an antenna.
    g.extend(KK.chbox((-1.0, 0.8, -3.53), (1.0, 2.0, -3.45), 'armour', 0.03))
    g.extend(KK.chbox((-0.9, 1.0, -3.56), (0.9, 1.9, -3.53), 'armour_dark', 0.02))
    g.extend(KK.chbox((-0.12, 1.4, -3.6), (0.12, 1.5, -3.56), 'metal_dark', 0.01))
    g.extend(K.exhaust_stack(-1.2, 1.2, 2.3, -3.2, r=0.05))
    g.extend(K.whip_antenna((1.1, 2.3, -3.0), 2.0))
    _arches(g, bm, lift=0.07)
    _axles8(g, bm)
    body.add(g)
    _wheels8(m, bm)

    tp = MT.mirror_part(m, bm, 'turret')
    t = Geo(40)
    # The remote weapon station: a round base, the armoured housing, the sight and the ammunition box.
    t.extend(cyl((0, 2.3, 0.2), (0, 2.38, 0.2), 0.35, 'armour', n=16, bevel=0.015))
    t.extend(KK.chbox((-0.25, 2.38, -0.1), (0.25, 2.6, 0.45), 'armour', 0.05))
    t.extend(KK.chbox((-0.2, 2.6, 0.05), (0.05, 2.78, 0.4), 'armour', 0.04))
    t.extend(panel((-0.075, 2.7, 0.402), 0.2, 0.1, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(KK.chbox((-0.4, 2.4, 0.0), (-0.25, 2.55, 0.35), 'armour_dark', 0.02))
    tp.add(t)
    gp = MT.mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = MT.muzzle_abs(bm)
    m2(gp, piv, mz[2])
    return m


JOBS = {'btr4': btr4, 'stryker': stryker}
