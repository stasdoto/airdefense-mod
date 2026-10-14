"""
2.0: self-propelled guns, the thermobaric launcher and the recovery vehicles as real shapes (MSTA-S, M109, TOS-1, BREM-1, M88):
lofted hulls, polygon turrets, barrels with bore evacuators and brakes, running gear from the shared kit.
"""
import math

import numpy as np

import kit_tanks as K
import m_tanks as MT
import m_west as W
from core import Geo
from geom import cyl, lathe, panel, tube, loft  # noqa: F401


from geom import face_normal as geom_normal  # noqa: E402


def stow(g, s, x0, x1, y0, y1, z0, z1, mat='armour', c=0.03):
    """A stowage box on the side s (x0..x1 are distances from the middle line)."""
    lo, hi = sorted((s * x0, s * x1))
    g.extend(K.chbox((lo, y0, z0), (hi, y1, z1), mat, c))


def tow_hooks(d, zf, y=0.62, x=0.75):
    for s in (-1, 1):
        d.extend(tube([(s * x, y, zf - 0.1), (s * x, y + 0.03, zf + 0.05), (s * x, y - 0.06, zf + 0.1), (s * x, y - 0.12, zf)], 0.03, 'metal_dark', n=6))


def side_skirts(d, hw, z0, z1, n, y0=0.62, y1=1.0, mat='armour_dark'):
    """Plates hanging over the track's upper run."""
    sl = (z1 - z0) / n
    for s in (-1, 1):
        for k in range(n):
            za = z0 + k * sl + 0.01
            d.extend(K.chbox((s * hw - 0.02, y0, za), (s * hw + 0.02, y1, za + sl - 0.02), mat, 0.008))


def tail_lamps(d, xs, y, z, hw=0.1):
    for x in xs:
        d.extend(K.chbox((x - hw, y - 0.08, z - 0.03), (x + hw, y + 0.08, z + 0.03), 'armour', 0.01))
        d.extend(panel((x, y, z - 0.032), 2 * hw * 0.7, 0.07, (-1, 0, 0), (0, 1, 0), 'taillight'))


def gear_for(bm, **kw):
    kw.setdefault('drive', 'front')
    kw.setdefault('pitch', 0.15)
    kw.setdefault('thick', 0.06)
    kw.setdefault('n', 10)
    kw.setdefault('teeth', 10)
    kw.setdefault('wheel_kind', 'single')
    kw.setdefault('idler_kind', 'single')
    return MT.Gear(**kw)


# ---------------------------------------------------------------------------------------------------------------
# 2S19 MSTA-S


def msta_s(bm):
    m = MT.new_model(bm, density=26)
    body = m.part('body', (0, 0, 0))
    st = [(-3.45, 1.35, 0.5), (-3.40, 1.6, 0.45), (2.25, 1.6, 0.45), (3.5, 0.72, 0.5)]
    hw_up, hw_low = 1.79, 1.21
    g = Geo(25)
    g.extend(K.section_loft(MT.hull_sections(st, hw_low, hw_up, 1.0, ch=0.07), 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    # Driver's hatch with periscopes on the front deck, headlights and tow hooks on the glacis, tail lights.
    d.extend(K.hatch((-0.55, 1.2, 2.5), 0.26, h=0.05))
    for a in (-30, 0, 30):
        ar = math.radians(a)
        d.extend(K.periscope((-0.55 + math.sin(ar) * 0.31, 1.2, 2.5 + math.cos(ar) * 0.31), 0.14, (math.sin(ar), 0, math.cos(ar))))
    for s in (-1, 1):
        d.extend(K.headlight((s * 1.4, 1.17, 3.15), (0, 0, 1), r=0.07))
        d.extend(K.chbox((s * 1.4 - 0.12, 1.0, 3.1), (s * 1.4 + 0.12, 1.06, 3.2), 'armour', 0.01))
        stow(d, s, 1.33, 1.77, 1.0, 1.32, -2.6, -1.3)
        stow(d, s, 1.33, 1.77, 1.0, 1.32, -1.2, 0.6, c=0.04)
        d.extend(K.chbox((s * 1.45 - 0.14, 0.6, -3.46), (s * 1.45 + 0.14, 0.9, -3.42), 'skirt_rubber', 0.005))
        d.extend(K.tow_cable([(s * 1.6, 1.6, -3.3), (s * 1.62, 1.62, -1.0), (s * 1.6, 1.62, 1.0), (s * 1.5, 1.6, 2.0)]))
    tail_lamps(d, (-1.5, 1.5), 1.2, -3.44)
    tow_hooks(d, 3.5, y=0.6, x=0.7)
    side_skirts(d, 1.795, -2.7, 2.7, 6)
    # Engine deck behind the turret, the exhaust grille ahead of it.
    d.extend(MT.louvres(-0.9, 0.9, 1.6, -3.3, -3.0))
    d.extend(MT.louvres(0.3, 1.5, 1.6, 1.55, 2.2))
    d.extend(K.chbox((-1.3, 1.6, 1.55), (-0.2, 1.64, 2.2), 'armour', 0.01))
    # Rear: the unditching log on brackets, a stowage box.
    d.extend(cyl((-1.3, 1.78, -3.7), (1.3, 1.78, -3.7), 0.15, 'wood', n=10))
    for s in (-1, 1):
        d.extend(tube([(s * 1.1, 1.6, -3.5), (s * 1.1, 1.9, -3.7), (s * 1.1, 1.95, -3.82)], 0.012, 'metal_dark', n=4, caps=False))
    d.extend(K.chbox((-1.0, 1.6, -3.55), (1.0, 1.85, -3.4), 'armour', 0.04))
    body.add(d)
    MT.running_gear(m, bm, gear_for(bm, rollers=[(-1.9, 0.82, 0.1), (-0.5, 0.82, 0.1), (0.9, 0.82, 0.1), (2.2, 0.82, 0.1)]))

    # Turret: a big welded box with a sloped front and a roof, mesh baskets on the sides, the commander's cupola.
    tp = MT.mirror_part(m, bm, 'turret')
    T = Geo(50)
    corners = [(0.9, 1.2), (1.35, 1.0), (1.62, 0.4), (1.62, -2.6), (1.45, -3.2), (-1.45, -3.2), (-1.62, -2.6), (-1.62, 0.4), (-1.35, 1.0), (-0.9, 1.2)]
    T.extend(W.poly_turret(corners, [(1.62, 1.0, 1.0, 1.0), (2.1, 1.0, 1.0, 1.0), (2.55, 0.985, 0.98, 1.0), (2.85, 0.93, 0.93, 0.985), (2.97, 0.88, 0.9, 0.97)], smooth=22))
    tp.add(T)
    t = Geo(30)
    roof = 2.97
    t.extend(W.cupola((0.8, roof, -1.25), 0.34, h=0.2, n_peri=6))
    t.extend(K.mg((0.8, roof + 0.3, -1.25), (0, 0, 1), 1.0))
    t.extend(K.hatch((-0.75, roof - 0.01, -1.5), 0.3, h=0.06))
    t.extend(K.chbox((-1.1, roof, -0.2), (-0.7, roof + 0.25, 0.15), 'armour', 0.03))
    t.extend(panel((-0.9, roof + 0.12, 0.152), 0.3, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(K.chbox((-0.55, roof, -2.4), (0.2, roof + 0.1, -1.9), 'armour', 0.02))
    t.extend(K.chbox((0.3, roof, 0.35), (0.65, roof + 0.2, 0.62), 'armour', 0.03))
    t.extend(K.hatch((0.0, roof, -2.7), 0.26, h=0.05))
    for s in (-1, 1):
        t.extend(K.chbox((min(s * 1.62, s * 1.88), 2.0, -2.8), (max(s * 1.62, s * 1.88), 2.55, -1.2), 'grille_mesh', 0.02))
        t.extend(K.chbox((min(s * 1.62, s * 1.89), 2.0, -2.8), (max(s * 1.62, s * 1.89), 2.04, -1.2), 'armour', 0.01))
        t.extend(K.smoke_launchers((s * 1.52, 2.55, 0.55), 4, (s * 0.8, 0, 0.6), spacing=0.12, length=0.3, elev=25, spread=7))
    # The rear: the ammunition feed door, antennas.
    t.extend(K.chbox((-0.9, 1.75, -3.27), (0.9, 2.55, -3.2), 'armour_dark', 0.02))
    t.extend(K.antenna((-1.2, roof, -2.6), 3.2))
    t.extend(K.antenna((-1.35, roof, -2.9), 2.6))
    tp.add(t)

    gp = MT.mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = MT.muzzle_abs(bm)
    gg = Geo(40)
    gg.extend(K.chbox((-0.55, piv[1] - 0.42, 0.8), (0.55, piv[1] + 0.42, 1.45), 'armour', 0.08))
    gg.extend(K.chbox((-0.38, piv[1] - 0.3, 1.45), (0.38, piv[1] + 0.34, 2.65), 'armour', 0.06))
    for s in (-1, 1):
        gg.extend(cyl((s * 0.17, piv[1] + 0.38, 1.4), (s * 0.17, piv[1] + 0.38, 2.8), 0.07, 'metal_dark', n=8))
    z0 = 1.45
    L = mz[2] - z0
    gg.extend(K.barrel((0, piv[1], z0), (0, piv[1], mz[2]), K.tank_gun_profile(L, 0.1, extractor=(L * 0.5, L * 0.68, 0.16), muzzle='brake'), n=12))
    gp.add(gg)
    return m


# ---------------------------------------------------------------------------------------------------------------
# M109 Paladin


def m109(bm):
    m = MT.new_model(bm, density=26)
    body = m.part('body', (0, 0, 0))
    st = [(-3.1, 1.3, 0.5), (-3.06, 1.9, 0.5), (1.8, 1.9, 0.5), (1.85, 1.62, 0.5), (3.15, 0.75, 0.5)]
    g = Geo(25)
    g.extend(K.section_loft(MT.hull_sections(st, 1.15, 1.57, 0.9, ch=0.06), 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    # Driver's hatch (left) with periscopes, the engine grille (right), the barrel travel lock on the glacis.
    d.extend(K.chbox((-1.2, 1.9, 1.35), (-0.55, 2.0, 1.8), 'armour', 0.03))
    d.extend(K.hatch((-0.88, 2.0, 1.58), 0.26, h=0.05))
    for a in (-35, 0, 35):
        ar = math.radians(a)
        d.extend(K.periscope((-0.88 + math.sin(ar) * 0.3, 1.9, 1.58 + math.cos(ar) * 0.3 + 0.2), 0.13, (math.sin(ar), 0, math.cos(ar))))
    d.extend(MT.louvres(0.25, 1.35, 1.89, 1.85, 2.6, pitch=0.09))
    for s in (-1, 1):
        d.extend(K.chbox((s * 0.3 - 0.05, 1.3, 2.52), (s * 0.3 + 0.05, 2.3, 2.66), 'armour', 0.015))
        d.extend(K.headlight((s * 1.2, 1.27, 2.97), (0, 0, 1), r=0.065))
        d.extend(K.chbox((s * 1.2 - 0.12, 1.1, 2.9), (s * 1.2 + 0.12, 1.16, 3.05), 'armour', 0.01))
    d.extend(K.chbox((-0.35, 2.15, 2.5), (0.35, 2.3, 2.7), 'armour', 0.04))
    d.extend(K.chbox((-0.2, 2.28, 2.52), (0.2, 2.34, 2.68), 'metal_dark', 0.02))
    tow_hooks(d, 3.15, y=0.6, x=0.7)
    # The back: the ammunition door, the spade and its hydraulics, tail lights.
    d.extend(K.chbox((-0.6, 0.95, -3.17), (0.6, 1.8, -3.1), 'armour', 0.025))
    d.extend(K.chbox((-0.45, 1.1, -3.2), (0.45, 1.6, -3.17), 'armour_dark', 0.01))
    d.extend(K.chbox((-1.4, 0.55, -3.32), (1.4, 1.25, -3.2), 'armour', 0.03))
    for s in (-1, 1):
        d.extend(cyl((s * 0.9, 1.35, -3.1), (s * 0.9, 0.9, -3.3), 0.05, 'metal_dark', n=6))
    tail_lamps(d, (-1.2, 1.2), 1.48, -3.08)
    side_skirts(d, 1.585, -2.85, 2.2, 5, y0=0.64, y1=1.0)
    for s in (-1, 1):
        d.extend(K.tow_cable([(s * 1.45, 1.9, -3.0), (s * 1.5, 1.92, -1.0), (s * 1.5, 1.92, 1.0), (s * 1.3, 1.65, 1.9)]))
    body.add(d)
    MT.running_gear(m, bm, gear_for(bm, rollers=[(-1.6, 0.76, 0.1), (-0.2, 0.76, 0.1), (1.2, 0.76, 0.1)]))

    # Turret: the tall box with sloped faces and a big rear bustle, the commander's cupola with the M2.
    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(0.75, 0.4), (1.38, 0.15), (1.52, -0.8), (1.52, -3.35), (1.4, -3.95), (-1.4, -3.95), (-1.52, -3.35), (-1.52, -0.8), (-1.38, 0.15), (-0.75, 0.4)]
    T = W.poly_turret(corners, [(1.9, 1.0, 1.0, 1.0), (2.3, 1.0, 1.0, 1.0), (2.75, 0.97, 0.96, 0.96), (2.95, 0.9, 0.9, 0.94)], smooth=22)
    tp.add(T)
    t = Geo(30)
    roof = 2.95
    t.extend(W.cupola((0.78, roof, -1.8), 0.34, h=0.2, n_peri=6))
    t.extend(K.mg((0.78, roof + 0.3, -1.8), (0, 0, 1), 1.0))
    t.extend(K.hatch((-0.78, roof - 0.01, -2.0), 0.28, h=0.06))
    t.extend(K.chbox((-1.25, roof, -0.5), (-0.95, roof + 0.3, -0.2), 'armour', 0.03))
    t.extend(panel((-1.1, roof + 0.15, -0.198), 0.22, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(K.chbox((0.0, roof, -3.3), (0.9, roof + 0.12, -2.6), 'armour', 0.03))
    t.extend(K.chbox((-0.7, roof, -3.7), (0.7, roof + 0.07, -3.2), 'armour', 0.02))
    for s in (-1, 1):
        t.extend(K.smoke_launchers((s * 1.42, 2.55, 0.15), 3, (s * 0.8, 0, 0.6), spacing=0.12, length=0.3, elev=25, spread=7))
        t.extend(K.chbox((s * 1.52 - (0.01 if s > 0 else 0.05), 2.15, -2.2), (s * 1.52 + (0.05 if s > 0 else 0.01), 2.8, -1.3), 'armour_dark', 0.01))
    t.extend(K.chbox((-1.4, 2.0, -4.1), (1.4, 2.1, -3.95), 'armour_dark', 0.02))
    t.extend(K.antenna((-1.1, roof, -2.4), 3.0))
    t.extend(K.antenna((-1.3, roof, -2.7), 2.4))
    tp.add(t)

    gp = MT.mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = MT.muzzle_abs(bm)
    gg = Geo(40)
    gg.extend(K.chbox((-0.55, piv[1] - 0.42, 0.05), (0.55, piv[1] + 0.42, 0.7), 'armour', 0.08))
    gg.extend(K.chbox((-0.38, piv[1] - 0.3, 0.7), (0.38, piv[1] + 0.34, 1.7), 'armour', 0.06))
    for s in (-1, 1):
        gg.extend(cyl((s * 0.17, piv[1] + 0.38, 0.65), (s * 0.17, piv[1] + 0.38, 1.9), 0.065, 'metal_dark', n=8))
    z0 = 0.7
    L = mz[2] - z0
    gg.extend(K.barrel((0, piv[1], z0), (0, piv[1], mz[2]), K.tank_gun_profile(L, 0.085, extractor=(L * 0.47, L * 0.66, 0.14), muzzle='brake'), n=12))
    gp.add(gg)
    return m


# ---------------------------------------------------------------------------------------------------------------
# The T-72-based chassis of the TOS-1 and the BREM-1


def t72_hull(m, bm, kind):
    body = m.part('body', (0, 0, 0))
    st = [(-3.45, 1.35, 0.5), (-3.40, 1.55, 0.45), (2.25, 1.55, 0.45), (3.5, 0.7, 0.5)]
    g = Geo(25)
    g.extend(K.section_loft(MT.hull_sections(st, 1.22, 1.8, 1.0, ch=0.07), 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    gl_angle = math.degrees(math.atan2(1.55 - 0.7, 3.5 - 2.25))
    mid_z = (2.25 + 3.5) / 2
    # TOS-1: ERA bricks on the glacis, the driver's hatch behind it.
    if kind == 'tos1':
        fr = K.slope_frame((0, 1.0, mid_z - 0.1), gl_angle, 1)
        d.extend(K.era_grid(fr, 6, 2, 0.46, 0.55, 0.06, gap=0.03, wedge=0.04))
        d.extend(K.hatch((0.0, 1.55, 1.9), 0.27, h=0.05))
        d.extend(K.periscope((0.0, 1.55, 2.2), 0.22, (0, 0, 1)))
    for s in (-1, 1):
        d.extend(K.headlight((s * 1.5, 1.17, 3.2), (0, 0, 1), r=0.07))
        d.extend(K.chbox((s * 1.5 - 0.14, 1.0, 3.12), (s * 1.5 + 0.14, 1.07, 3.24), 'armour', 0.01))
        d.extend(K.chbox((s * 1.5 - 0.14, 0.58, -3.46), (s * 1.5 + 0.14, 0.9, -3.42), 'skirt_rubber', 0.005))
        d.extend(K.tow_cable([(s * 1.62, 1.55, -3.3), (s * 1.66, 1.57, -1.0), (s * 1.64, 1.58, 1.0), (s * 1.5, 1.57, 2.0)]))
    tail_lamps(d, (-1.5, 1.5), 1.2, -3.44)
    tow_hooks(d, 3.5, y=0.6, x=0.72)
    side_skirts(d, 1.815, -2.9, 2.5, 7, y0=0.58, y1=1.0)
    body.add(d)
    MT.running_gear(m, bm, gear_for(bm, rollers=[(-1.95, 0.78, 0.1), (-0.05, 0.78, 0.1), (1.9, 0.78, 0.1)]))
    return body


def tos1(bm):
    m = MT.new_model(bm, density=26)
    body = t72_hull(m, bm, 'tos1')
    e = Geo(30)
    # Engine deck behind the turret, fuel drums at the rear, stowage boxes on the fenders.
    e.extend(MT.louvres(-1.3, -0.2, 1.55, -3.2, -2.5))
    e.extend(MT.louvres(0.2, 1.3, 1.55, -3.2, -2.5))
    for s in (-1, 1):
        e.extend(K.drum((s * 0.85, 1.85, -3.8), (1, 0, 0), r=0.29, length=0.72))
        e.extend(K.chbox((s * 0.85 - 0.4, 1.53, -3.58), (s * 0.85 + 0.4, 1.57, -3.4), 'metal_dark', 0.01))
        stow(e, s, 1.36, 1.78, 1.0, 1.4, -2.0, -0.9)
        stow(e, s, 1.36, 1.78, 1.0, 1.3, -0.8, 0.5, c=0.04)
    body.add(e)

    tp = MT.mirror_part(m, bm, 'turret')
    # The mount: a round base on the roof, the two side housings with the elevation gear.
    T = Geo(40)
    lv = []
    for y, f in ((1.55, 1.0), (1.7, 1.0), (1.85, 0.93)):
        lv.append((y, K.superellipse(24, 1.1 * f, 1.0, e=2.6, cz=-0.6, front=1.1 * f, back=1.1 * f)))
    T.extend(K.plan_loft(lv, 'armour', smooth=40))
    tp.add(T)
    t = Geo(30)
    for s in (-1, 1):
        t.extend(K.chbox((min(s * 1.1, s * 1.4), 1.85, -2.4), (max(s * 1.1, s * 1.4), 2.4, -0.4), 'armour', 0.05))
        t.extend(K.chbox((min(s * 1.1, s * 1.43), 1.85, -2.4), (max(s * 1.1, s * 1.43), 1.95, -0.4), 'armour_dark', 0.02))
        t.extend(cyl((s * 1.15, 2.2, -0.9), (s * 1.15, 2.2, -2.2), 0.06, 'metal_dark', n=6))
        t.extend(K.smoke_launchers((s * 1.35, 2.4, 0.2), 3, (s * 0.8, 0, 0.6), spacing=0.12, length=0.3, elev=25, spread=7))
    t.extend(K.chbox((-1.1, 1.85, -1.8), (1.1, 1.95, -0.2), 'armour', 0.03))
    t.extend(K.hatch((0.5, 1.95, -1.0), 0.25, h=0.05))
    t.extend(K.antenna((-0.8, 1.95, -1.6), 2.2))
    tp.add(t)

    # The pack: 24 rocket tubes in three rows in a frame, the muzzles black.
    pp = MT.mirror_part(m, bm, 'pack', tp)
    P = Geo(40)
    for y in (2.11, 2.43, 2.75):
        for k in range(8):
            x = (k - 3.5) * 0.285
            P.extend(lathe((x, y, -2.3), (x, y, 2.3), [(0, 0.0, 'metal_dark'), (0, 0.11, 'metal_dark'), (0.06, 0.115, 'armour'), (4.52, 0.115, 'armour'), (4.55, 0.13, 'steel_worn'),
                                                     (4.6, 0.13, 'steel_worn'), (4.6, 0.095, 'black'), (4.58, 0.0, 'black')], 'armour', 8, 40))
    for z0, z1 in ((-0.9, -0.82), (0.6, 0.68), (1.9, 1.98)):
        P.extend(K.chbox((-1.15, 1.95, z0), (1.15, 2.95, z1), 'armour', 0.02))
    for s in (-1, 1):
        P.extend(K.chbox((min(s * 1.13, s * 1.17), 1.95, -2.5), (max(s * 1.13, s * 1.17), 2.95, 1.9), 'armour', 0.015))
    P.extend(K.chbox((-1.15, 2.93, -2.5), (1.15, 2.97, 1.9), 'armour', 0.02))
    P.extend(K.chbox((-1.15, 1.93, -2.5), (1.15, 1.97, 1.9), 'armour', 0.02))
    P.extend(K.chbox((-1.15, 1.95, -2.5), (1.15, 2.95, -2.3), 'armour_dark', 0.04))
    pp.add(P)
    return m


# ---------------------------------------------------------------------------------------------------------------
# BREM-1


def brem1(bm):
    m = MT.new_model(bm, density=26)
    body = t72_hull(m, bm, 'brem1')
    e = Geo(30)
    # The superstructure: a sloped cab over the front of the hull.
    corners = [(1.1, 2.1), (1.45, 1.8), (1.5, 0.5), (1.45, 0.1), (-1.45, 0.1), (-1.5, 0.5), (-1.45, 1.8), (-1.1, 2.1)]
    e.extend(W.poly_turret(corners, [(1.55, 1.0, 1.0, 1.0), (1.95, 1.0, 1.0, 1.0), (2.2, 0.97, 0.94, 1.0), (2.35, 0.88, 0.86, 1.0)], smooth=22))
    # Vision blocks on the cab front, the crane boom folded along the right side with its pulley block.
    for x in (-1.0, -0.3, 0.4, 1.0):
        e.extend(K.chbox((x - 0.16, 2.0, 2.0), (x + 0.16, 2.14, 2.12), 'armour', 0.01))
        e.extend(panel((x, 2.07, 2.121), 0.26, 0.07, (-1, 0, 0), (0, 1, 0), 'glass'))
    e.extend(K.chbox((0.95, 2.45, -1.15), (1.35, 2.75, 2.6), 'armour', 0.04))
    for z in np.linspace(-1.0, 2.4, 9):
        e.extend(tube([(0.95, 2.5, z), (1.35, 2.7, z + 0.2)], 0.012, 'metal_dark', n=4))
    e.extend(K.chbox((0.9, 1.55, 2.15), (1.4, 2.6, 2.65), 'armour', 0.05))
    e.extend(cyl((1.15, 2.6, 2.55), (1.15, 2.6, 2.75), 0.12, 'metal_dark', n=8))
    e.extend(tube([(1.15, 2.45, 2.6), (1.15, 2.05, 2.7)], 0.02, 'steel_worn', n=5))
    e.extend(K.hatch((-0.9, 2.35, 1.1), 0.24, h=0.05))
    # Rear deck: stowage, the winch drum, the spade beam; the dozer blade in front on its arms.
    e.extend(K.chbox((-1.6, 1.55, -3.25), (1.6, 1.85, -0.05), 'armour', 0.05))
    e.extend(K.chbox((-1.6, 1.85, -3.25), (1.6, 2.15, -3.1), 'armour', 0.04))
    e.extend(MT.louvres(-1.2, -0.2, 1.85, -2.8, -1.2))
    e.extend(MT.louvres(0.2, 1.2, 1.85, -2.8, -1.2))
    e.extend(cyl((-0.9, 2.0, -0.6), (0.9, 2.0, -0.6), 0.22, 'metal_dark', n=10))
    e.extend(K.chbox((-0.1, 0.8, -4.35), (0.1, 0.9, -3.45), 'metal_dark', 0.02))
    for s in (-1, 1):
        e.extend(K.chbox((s * 1.2 - 0.1, 0.78, -4.35), (s * 1.2 + 0.1, 0.88, -3.45), 'armour', 0.02))
        e.extend(tube([(s * 1.4, 0.55, 3.6), (s * 1.4, 0.7, 2.6), (s * 1.3, 1.05, 2.1)], 0.07, 'metal_dark', n=6))
        e.extend(K.chbox((s * 1.4 - 0.1, 0.45, 3.4), (s * 1.4 + 0.1, 0.75, 3.6), 'metal_dark', 0.02))
    e.extend(K.chbox((-1.7, 0.2, 3.55), (1.7, 1.0, 3.8), 'armour_dark', 0.05))
    e.extend(K.chbox((-1.7, 0.18, 3.82), (1.7, 0.26, 3.9), 'steel_worn', 0.01))
    body.add(e)

    tp = MT.mirror_part(m, bm, 'turret')
    t = Geo(30)
    t.extend(W.cupola((-0.6, 2.35, 1.05), 0.5, h=0.2, n_peri=7))
    t.extend(K.chbox((-1.0, 2.35, 0.65), (-0.2, 2.45, 0.78), 'armour', 0.01))
    tp.add(t)
    gp = MT.mirror_part(m, bm, 'gun', tp)
    gg = Geo(40)
    gx, gy, gz = gp.pivot
    mz = MT.muzzle_abs(bm)
    L = mz[2] - gz
    gg.extend(K.chbox((gx - 0.2, gy - 0.12, gz - 0.2), (gx + 0.2, gy + 0.12, gz + 0.35), 'armour', 0.04))
    gg.extend(K.barrel((gx, gy, gz), (gx, gy, mz[2]), [(0, 0.0, 'armour'), (0, 0.04, 'armour'), (0.5, 0.035, 'armour'), (0.52, 0.05, 'armour'), (1.0, 0.05, 'armour'), (1.02, 0.03, 'armour'),
                                                      (L - 0.2, 0.025, 'armour'), (L - 0.18, 0.04, 'metal_dark'), (L, 0.04, 'metal_dark'), (L, 0.015, 'black'), (L - 0.04, 0.0, 'black')], n=8))
    gp.add(gg)
    return m


# ---------------------------------------------------------------------------------------------------------------
# M88 Hercules


def m88(bm):
    m = MT.new_model(bm, density=26)
    body = m.part('body', (0, 0, 0))
    st = [(-4.3, 1.35, 0.55), (-4.25, 1.75, 0.5), (0.95, 1.75, 0.5), (1.0, 2.75, 0.5), (3.45, 2.75, 0.5), (3.5, 2.35, 0.5), (4.35, 0.75, 0.5)]
    g = Geo(25)
    g.extend(K.section_loft(MT.hull_sections(st, 1.1, 1.7, 0.95, ch=0.07), 'armour', smooth=20))
    for p in g.polys:  # the cab's rear wall (a step between two sections) must face back
        if np.all(np.abs(p.v[:, 2] - 1.0) < 0.06) and abs(geom_normal(p.v)[2]) > 0.9 and geom_normal(p.v)[2] > 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
            if p.n is not None:
                p.n = p.n[::-1].copy()
    body.add(g)
    d = Geo(30)
    # The cab: vision blocks across the front, hatches on the roof, the winch house on the deck.
    for x in (-1.2, -0.4, 0.4, 1.2):
        d.extend(K.chbox((x - 0.24, 2.4, 3.46), (x + 0.24, 2.65, 3.5), 'armour', 0.01))
        d.extend(panel((x, 2.52, 3.505), 0.36, 0.1, (-1, 0, 0), (0, 1, 0), 'glass'))
    d.extend(K.chbox((-0.9, 1.75, -0.3), (0.9, 2.2, 1.0), 'armour', 0.06))
    d.extend(K.hatch((-0.8, 2.75, 2.3), 0.28, h=0.05))
    d.extend(K.periscope((-0.8, 2.75, 2.65), 0.2, (0, 0, 1)))
    d.extend(K.hatch((-0.5, 2.75, 1.6), 0.24, h=0.05))
    for s in (-1, 1):
        d.extend(K.headlight((s * 1.3, 1.27, 4.28), (0, 0, 1), r=0.07))
        d.extend(K.chbox((s * 1.3 - 0.14, 1.13, 4.2), (s * 1.3 + 0.14, 1.2, 4.32), 'armour', 0.01))
        # Rails along both sides of the rear deck.
        d.extend(K.chbox((min(s * 1.13, s * 1.37), 1.75, -3.9), (max(s * 1.13, s * 1.37), 1.95, 0.9), 'armour_dark', 0.04))
        d.extend(K.tow_cable([(s * 1.4, 2.76, 1.2), (s * 1.45, 2.78, 2.4), (s * 1.35, 2.78, 3.3)]))
        d.extend(cyl((s * 0.9, 1.3, -4.26), (s * 0.9, 0.8, -4.34), 0.05, 'metal_dark', n=6))
        d.extend(tube([(s * 1.5, 0.5, 4.5), (s * 1.5, 0.7, 3.4), (s * 1.4, 1.0, 2.7)], 0.08, 'metal_dark', n=6))
        d.extend(K.chbox((s * 1.45 - 0.17, 0.5, -4.34), (s * 1.45 + 0.17, 0.8, -4.3), 'skirt_rubber', 0.005))
    d.extend(K.chbox((-1.37, 1.75, -4.0), (1.37, 1.95, -3.8), 'armour_dark', 0.04))
    tail_lamps(d, (-1.3, 1.3), 1.35, -4.3)
    # The boom stowed along the deck (two chords and braces), the spade across the rear, the dozer blade in front.
    for s in (-0.55, 0.55):
        d.extend(tube([(s, 2.1, -3.7), (s, 2.1, -0.4)], 0.07, 'armour', n=6))
    for z in np.linspace(-3.6, -0.5, 10):
        d.extend(tube([(-0.55, 2.1, z), (0.55, 2.1, z + 0.15)], 0.02, 'metal_dark', n=4))
    d.extend(K.chbox((-1.4, 0.5, -4.34), (1.4, 1.3, -4.26), 'armour_dark', 0.04))
    d.extend(K.chbox((-1.75, 0.15, 4.35), (1.75, 1.05, 4.65), 'armour_dark', 0.05))
    d.extend(K.chbox((-1.75, 0.13, 4.67), (1.75, 0.22, 4.75), 'steel_worn', 0.01))
    side_skirts(d, 1.715, -3.7, 3.4, 8, y0=0.6, y1=1.0, mat='armour')
    body.add(d)
    MT.running_gear(m, bm, gear_for(bm, rollers=[(-2.6, 0.8, 0.1), (-0.9, 0.8, 0.1), (0.9, 0.8, 0.1), (2.6, 0.8, 0.1)]))

    tp = MT.mirror_part(m, bm, 'turret')
    t = Geo(30)
    t.extend(W.cupola((0.9, 2.75, 2.1), 0.45, h=0.2, n_peri=7))
    tp.add(t)
    gp = MT.mirror_part(m, bm, 'gun', tp)
    gx, gy, gz = gp.pivot
    mz = MT.muzzle_abs(bm)
    gg = Geo(40)
    L = mz[2] - gz
    gg.extend(K.chbox((gx - 0.1, gy - 0.1, gz - 0.2), (gx + 0.1, gy + 0.1, gz + 0.3), 'metal_dark', 0.03))
    gg.extend(K.barrel((gx, gy, gz), (gx, gy, mz[2]), [(0, 0.0), (0, 0.025), (L * 0.2, 0.02), (L * 0.25, 0.04), (L * 0.7, 0.04), (L * 0.72, 0.025), (L - 0.1, 0.02), (L - 0.08, 0.035),
                                                     (L, 0.035), (L, 0.012), (L - 0.04, 0.0)], n=8))
    gp.add(gg)
    return m


JOBS = {'msta_s': msta_s, 'm109': m109, 'tos1': tos1, 'brem1': brem1, 'm88': m88}
