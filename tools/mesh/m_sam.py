"""
2.0: the tracked air-defence vehicles as real shapes: Tor (turret with a tracking array and a folding search radar), Buk (the
four-missile launcher), Shilka (four 23 mm barrels and the dish), Tunguska (two twin 30 mm guns with the missile containers),
Strela-10 (four containers on the cradle). Same animated parts, pivots and parents as the box models.
"""
import math

import numpy as np

import kit_tanks as K
import kit_trucks as KT
import m_arty as A
import m_tanks as MT
import m_west as W
from core import Geo
from geom import cyl, lathe, panel, tube


def hull(m, bm, st, hw_up, hw_low, ys, front_z, rear_z, deck, skirt=(-3.0, 3.0, 7), rollers=(), lamp_y=1.3, hatch=None, lights_x=1.25):
    """The chassis common to the vehicles: a lofted hull with the track guards, the lights, tow hooks, hatches."""
    body = m.part('body', (0, 0, 0))
    g = Geo(25)
    g.extend(K.section_loft(MT.hull_sections(st, hw_low, hw_up, ys, ch=0.06), 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    lz = st[-1][0]
    for s in (-1, 1):
        d.extend(K.headlight((s * lights_x, lamp_y - 0.1, lz - 0.02), (0, 0, 1), r=0.065))
        d.extend(K.chbox((s * lights_x - 0.12, lamp_y - 0.28, lz - 0.1), (s * lights_x + 0.12, lamp_y - 0.2, lz + 0.02), 'armour', 0.01))
        d.extend(K.chbox((s * 1.2 - 0.14, 0.58, rear_z - 0.04), (s * 1.2 + 0.14, 0.9, rear_z), 'skirt_rubber', 0.005))
    A.tail_lamps(d, (-lights_x, lights_x), lamp_y, rear_z - 0.01)
    A.tow_hooks(d, lz + 0.02, y=0.6, x=0.7)
    z0, z1, n = skirt
    A.side_skirts(d, hw_up + 0.015, z0, z1, n, y0=0.6, y1=ys)
    if hatch:
        x, z = hatch
        d.extend(K.hatch((x, deck, z), 0.26, h=0.05))
        for a in (-35, 0, 35):
            ar = math.radians(a)
            d.extend(K.periscope((x + math.sin(ar) * 0.31, deck, z + math.cos(ar) * 0.31 + 0.1), 0.13, (math.sin(ar), 0, math.cos(ar))))
    body.add(d)
    return body, d


def tracks(m, bm, rollers):
    MT.running_gear(m, bm, A.gear_for(bm, rollers=rollers))


# ---------------------------------------------------------------------------------------------------------------
# 9K331 Tor


def tor(bm):
    m = MT.new_model(bm, density=22)
    st = [(-3.74, 1.35, 0.5), (-3.70, 1.8, 0.5), (2.7, 1.8, 0.5), (3.1, 1.5, 0.5), (3.6, 1.45, 0.5), (3.89, 0.85, 0.5)]
    body, d = hull(m, bm, st, 1.62, 1.15, 1.0, 3.89, -3.74, 1.8, skirt=(-3.2, 2.7, 7), hatch=(-0.7, 3.2), lights_x=1.25, lamp_y=1.3)
    e = Geo(30)
    e.extend(MT.louvres(-1.2, 1.2, 1.8, -3.5, -2.5))
    e.extend(K.chbox((-1.0, 1.5, 3.0), (0.2, 1.62, 3.7), 'armour', 0.02))
    for s in (-1, 1):
        A.stow(e, s, 1.0, 1.6, 1.8, 2.05, -3.6, -2.4, c=0.04)
        e.extend(K.tow_cable([(s * 1.5, 1.8, -3.3), (s * 1.55, 1.82, -1.0), (s * 1.55, 1.82, 1.0), (s * 1.4, 1.8, 2.2)]))
    body.add(e)
    tracks(m, bm, [(-2.2, 0.78, 0.1), (-0.4, 0.78, 0.1), (1.4, 0.78, 0.1), (2.9, 0.78, 0.1)])

    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(1.0, 0.9), (1.3, 0.6), (1.3, -1.8), (1.0, -2.1), (-1.0, -2.1), (-1.3, -1.8), (-1.3, 0.6), (-1.0, 0.9)]
    tp.add(W.poly_turret(corners, [(1.8, 1.0, 1.0, 1.0), (2.8, 1.0, 1.0, 1.0), (3.0, 0.94, 0.96, 0.98), (3.1, 0.9, 0.93, 0.96)], smooth=22))
    t = Geo(30)
    # The tracking array on the front face, the optical sensor beside it, the eight cell lids, the pedestal of the search radar.
    t.extend(K.chbox((-0.8, 1.88, 0.86), (0.8, 3.03, 0.95), 'armour', 0.03))
    t.extend(KT.array_face((0, 2.46, 1.0), 1.4, 1.0, (0, 0, 1), (0, 1, 0), mat='aesa', frame=0.06, depth=0.12, back='armour'))
    t.extend(K.chbox((0.85, 2.7, 0.9), (1.15, 2.95, 1.1), 'metal_dark', 0.03))
    t.extend(panel((1.0, 2.82, 1.102), 0.18, 0.14, (-1, 0, 0), (0, 1, 0), 'optic'))
    for x in (-0.6, -0.2, 0.2, 0.6):
        for z in (-1.3, -0.85):
            t.extend(K.chbox((x - 0.17, 3.04, z - 0.18), (x + 0.17, 3.15, z + 0.18), 'armour_dark', 0.02))
            t.extend(K.chbox((x - 0.12, 3.14, z - 0.13), (x + 0.12, 3.17, z + 0.13), 'armour', 0.01))
    t.extend(cyl((0, 3.1, -1.85), (0, 3.6, -1.85), 0.11, 'metal_dark', n=8))
    t.extend(K.hatch((-0.8, 3.08, 0.2), 0.22, h=0.05))
    t.extend(K.antenna((1.05, 3.05, -1.6), 2.3))
    for s in (-1, 1):
        t.extend(K.chbox((min(s * 1.3, s * 1.38), 2.2, -1.8), (max(s * 1.3, s * 1.38), 2.8, -0.4), 'armour_dark', 0.02))
        t.extend(K.smoke_launchers((s * 1.15, 2.95, 0.55), 3, (s * 0.7, 0, 0.6), spacing=0.12, length=0.28, elev=25, spread=7))
    tp.add(t)
    sp = MT.mirror_part(m, bm, 'search', tp)
    S = Geo(30)
    S.extend(KT.array_face((0, 3.95, -1.85), 2.4, 0.66, (0, 0, 1), (0, 1, 0), mat='aesa', frame=0.07, depth=0.2, back='armour'))
    S.extend(K.chbox((-0.2, 3.6, -1.95), (0.2, 3.65, -1.7), 'metal_dark', 0.01))
    sp.add(S)
    return m


# ---------------------------------------------------------------------------------------------------------------
# 9K37 Buk


def buk(bm):
    m = MT.new_model(bm, density=22)
    st = [(-4.5, 1.4, 0.5), (-4.45, 1.85, 0.5), (2.7, 1.85, 0.5), (3.2, 1.6, 0.5), (4.3, 1.6, 0.5), (4.65, 1.05, 0.5)]
    body, d = hull(m, bm, st, 1.62, 1.1, 1.05, 4.65, -4.5, 1.85, skirt=(-4.1, 3.5, 9), hatch=None, lights_x=1.25, lamp_y=1.3)
    e = Geo(30)
    # The driver's cab (left, forward): a sloped box with a glass front and side vision blocks.
    cab = [(-0.2, 4.3), (-0.2, 3.35), (-1.0, 3.3), (-1.62, 3.3), (-1.62, 4.1)]
    e.extend(W.poly_turret([(-0.25, 4.25), (-0.25, 3.35), (-1.62, 3.35), (-1.62, 4.25), (-1.45, 4.4), (-0.4, 4.4)], [(1.6, 1.0, 1.0, 1.0), (2.1, 1.0, 1.0, 1.0), (2.35, 0.9, 0.9, 0.95)], smooth=22))
    e.extend(K.chbox((-1.45, 1.85, 4.38), (-0.35, 2.25, 4.46), 'armour', 0.02))
    e.extend(panel((-0.9, 2.05, 4.462), 1.0, 0.34, (-1, 0, 0), (0, 1, 0), 'glass'))
    e.extend(K.hatch((-0.9, 2.35, 3.7), 0.24, h=0.05))
    e.extend(MT.louvres(-1.2, 1.2, 1.85, -4.2, -3.0))
    e.extend(MT.louvres(0.1, 1.4, 1.6, 3.5, 4.3, pitch=0.1))
    for s in (-1, 1):
        e.extend(K.tow_cable([(s * 1.5, 1.85, -4.2), (s * 1.55, 1.87, -1.0), (s * 1.55, 1.87, 1.0), (s * 1.4, 1.62, 3.0)]))
    body.add(e)
    tracks(m, bm, [(-3.0, 0.8, 0.1), (-1.4, 0.8, 0.1), (0.4, 0.8, 0.1), (2.2, 0.8, 0.1), (3.6, 0.8, 0.1)])

    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(1.0, 0.9), (1.4, 0.5), (1.4, -1.8), (1.1, -2.2), (-1.1, -2.2), (-1.4, -1.8), (-1.4, 0.5), (-1.0, 0.9)]
    tp.add(W.poly_turret(corners, [(1.85, 1.0, 1.0, 1.0), (2.45, 1.0, 1.0, 1.0), (2.7, 0.95, 0.96, 0.98), (2.8, 0.9, 0.9, 0.95)], smooth=22))
    t = Geo(30)
    # The illumination radar's pedestal and its array at the front, hatches, antennas.
    t.extend(K.chbox((-0.75, 1.95, 0.85), (0.75, 3.35, 1.5), 'armour', 0.05))
    t.extend(KT.array_face((0, 2.65, 1.52), 1.0, 1.1, (0, 0.0, 1), (0, 1, 0), mat='aesa', frame=0.06, depth=0.1, back='armour'))
    t.extend(K.chbox((-0.5, 3.3, 0.9), (0.5, 3.38, 1.45), 'armour_dark', 0.02))
    t.extend(K.hatch((-0.8, 2.78, -0.3), 0.26, h=0.05))
    t.extend(K.hatch((0.8, 2.78, -0.3), 0.26, h=0.05))
    t.extend(K.chbox((0.5, 2.8, -1.8), (1.2, 2.95, -1.2), 'armour', 0.03))
    t.extend(K.antenna((-1.0, 2.8, -1.7), 2.8))
    for s in (-1, 1):
        t.extend(K.chbox((min(s * 1.4, s * 1.46), 2.0, -2.0), (max(s * 1.4, s * 1.46), 2.6, -0.3), 'armour_dark', 0.02))
    tp.add(t)
    # The launcher: the cradle (a plate, the spine, the dividing rails), its rams, and the four missiles each on its own part.
    lp = MT.mirror_part(m, bm, 'launcher', tp)
    L = Geo(30)
    L.extend(K.chbox((-1.0, 2.8, -2.3), (1.0, 3.0, 2.0), 'armour', 0.03))
    L.extend(K.chbox((-0.15, 3.0, -2.3), (0.15, 3.2, 2.0), 'armour_dark', 0.03))
    for s in (-1, 1):
        L.extend(K.chbox((s * 0.55 - 0.05, 3.45, -1.1), (s * 0.55 + 0.05, 3.55, 0.9), 'armour_dark', 0.01))
        L.extend(K.chbox((s * 0.55 - 0.05, 3.0, -1.1), (s * 0.55 + 0.05, 3.1, 0.9), 'armour_dark', 0.01))
        L.extend(tube([(s * 0.5, 3.0, -1.1), (s * 0.5, 3.5, -1.1)], 0.02, 'metal_dark', n=4))
        L.extend(tube([(s * 0.5, 3.0, 0.9), (s * 0.5, 3.5, 0.9)], 0.02, 'metal_dark', n=4))
        L.extend(cyl((s * 0.7, 2.4, -0.5), (s * 0.7, 2.85, -1.5), 0.06, 'metal_dark', n=8))
    lp.add(L)
    for i, (x, y) in enumerate(((-0.55, 3.2), (0.55, 3.2), (-0.55, 3.75), (0.55, 3.75))):
        mp = MT.mirror_part(m, bm, 'missile_%d' % i, lp)
        mp.add(KT.missile((x, y, -2.15), (x, y, 3.35), 0.2, body='missile_white', nose=0.16, fins=4, fin_len=0.85, fin_h=0.3, n=10,
                          band=[(0.55, 0.6, 'missile_grey')]))
    return m


# ---------------------------------------------------------------------------------------------------------------
# 2K22 Tunguska


def tunguska(bm):
    m = MT.new_model(bm, density=22)
    st = [(-3.8, 1.35, 0.5), (-3.76, 1.8, 0.5), (2.7, 1.8, 0.5), (3.1, 1.5, 0.5), (3.7, 1.45, 0.5), (3.99, 0.85, 0.5)]
    body, d = hull(m, bm, st, 1.62, 1.12, 1.0, 3.99, -3.8, 1.8, skirt=(-3.3, 2.8, 7), hatch=(-0.7, 3.2), lights_x=1.25, lamp_y=1.3)
    e = Geo(30)
    e.extend(MT.louvres(-1.2, 1.2, 1.8, -3.6, -2.6))
    for s in (-1, 1):
        A.stow(e, s, 0.9, 1.6, 1.8, 2.05, -3.6, -2.6, c=0.04)
        e.extend(K.tow_cable([(s * 1.5, 1.8, -3.4), (s * 1.55, 1.82, -1.0), (s * 1.55, 1.82, 1.0), (s * 1.4, 1.8, 2.2)]))
    body.add(e)
    tracks(m, bm, [(-2.3, 0.78, 0.1), (-0.5, 0.78, 0.1), (1.4, 0.78, 0.1), (3.0, 0.78, 0.1)])

    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(0.95, 0.8), (1.3, 0.5), (1.3, -1.9), (1.0, -2.2), (-1.0, -2.2), (-1.3, -1.9), (-1.3, 0.5), (-0.95, 0.8)]
    tp.add(W.poly_turret(corners, [(1.8, 1.0, 1.0, 1.0), (2.7, 1.0, 1.0, 1.0), (2.9, 0.95, 0.96, 0.98), (3.0, 0.9, 0.93, 0.96)], smooth=22))
    t = Geo(30)
    # The tracking radar array and the optical sight at the front, the commander's hatch, the search radar's pedestal.
    t.extend(K.chbox((-0.55, 2.15, 0.76), (0.55, 3.05, 0.86), 'armour', 0.03))
    t.extend(KT.array_face((0, 2.6, 0.9), 1.0, 0.78, (0, 0, 1), (0, 1, 0), mat='aesa', frame=0.05, depth=0.1, back='armour'))
    t.extend(K.chbox((0.6, 2.7, 0.55), (0.95, 3.0, 0.85), 'metal_dark', 0.03))
    t.extend(panel((0.78, 2.85, 0.852), 0.2, 0.16, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(K.hatch((-0.6, 3.0, -0.3), 0.26, h=0.05))
    t.extend(K.hatch((0.6, 3.0, -0.9), 0.24, h=0.05))
    t.extend(cyl((0, 3.0, -1.88), (0, 3.3, -1.88), 0.1, 'metal_dark', n=8))
    t.extend(K.antenna((1.05, 3.0, -1.6), 2.3))
    for s in (-1, 1):
        t.extend(K.smoke_launchers((s * 1.15, 2.85, 0.45), 3, (s * 0.7, 0, 0.6), spacing=0.12, length=0.28, elev=25, spread=7))
    tp.add(t)
    sp = MT.mirror_part(m, bm, 'search', tp)
    S = Geo(30)
    S.extend(KT.array_face((0, 3.6, -1.88), 2.0, 0.6, (0, 0, 1), (0, 1, 0), mat='lattice', frame=0.06, depth=0.12, back='armour'))
    S.extend(K.chbox((-0.12, 3.3, -1.98), (0.12, 3.35, -1.78), 'metal_dark', 0.01))
    sp.add(S)

    wp = MT.mirror_part(m, bm, 'weapons', tp)
    Wg = Geo(40)
    Wg.extend(K.chbox((-1.45, 2.4, -0.6), (1.45, 2.6, -0.4), 'armour_dark', 0.03))
    mz = MT.muzzle_abs(bm, rail=4)
    zm = mz[2]
    for s in (-1, 1):
        Wg.extend(K.chbox((min(s * 1.55, s * 1.95), 2.05, -1.1), (max(s * 1.55, s * 1.95), 2.35, 0.0), 'armour', 0.05))
        for dx in (-0.09, 0.09):
            x = s * 1.75 + dx
            Wg.extend(lathe((x, 2.2, 0.0), (x, 2.2, zm), [(0, 0.0, 'metal_dark'), (0, 0.04, 'metal_dark'), (0.8, 0.04, 'steel_worn'), (0.9, 0.065, 'metal_dark'), (1.5, 0.065, 'metal_dark'),
                                                       (1.55, 0.04, 'steel_worn'), (zm - 0.15, 0.04, 'steel_worn'), (zm - 0.12, 0.06, 'metal_dark'), (zm, 0.06, 'metal_dark'),
                                                       (zm, 0.025, 'black'), (zm - 0.04, 0.0, 'black')], 'metal_dark', 8, 40))
        Wg.extend(K.chbox((min(s * 1.5, s * 2.0), 2.3, -1.15), (max(s * 1.5, s * 2.0), 2.42, -0.95), 'armour', 0.02))
    wp.add(Wg)
    for i in range(8):
        r = [r_ for r_ in bm.rails if r_[0] == 'can_%d' % i][0]
        e0 = MT.elev_abs(bm)
        x, y = e0[0] + r[1], e0[1] + r[2]
        cp = MT.mirror_part(m, bm, 'can_%d' % i, wp)
        cp.add(KT.tpk((x, y, -1.5), (x, y, 1.13), 0.24, 0.24, mat='armour', ribs=4))
    return m


# ---------------------------------------------------------------------------------------------------------------
# ZSU-23-4 Shilka


def shilka(bm):
    m = MT.new_model(bm, density=22)
    st = [(-3.2, 1.3, 0.5), (-3.15, 1.7, 0.5), (2.3, 1.7, 0.5), (2.55, 1.45, 0.5), (3.1, 1.4, 0.5), (3.35, 0.95, 0.5)]
    body, d = hull(m, bm, st, 1.55, 1.1, 0.95, 3.35, -3.2, 1.7, skirt=(-2.8, 2.5, 6), hatch=(-0.7, 2.9), lights_x=1.2, lamp_y=1.25)
    e = Geo(30)
    e.extend(MT.louvres(-1.1, 1.1, 1.7, -3.0, -2.0))
    for s in (-1, 1):
        A.stow(e, s, 0.95, 1.5, 1.7, 1.95, -3.0, -2.3, c=0.04)
        e.extend(K.tow_cable([(s * 1.45, 1.7, -3.0), (s * 1.5, 1.72, -1.0), (s * 1.5, 1.72, 1.0), (s * 1.3, 1.45, 2.4)]))
    body.add(e)
    tracks(m, bm, [(-1.6, 0.72, 0.1), (-0.4, 0.72, 0.1), (0.8, 0.72, 0.1), (1.9, 0.72, 0.1)])

    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(1.15, 1.1), (1.45, 0.7), (1.45, -1.5), (1.2, -1.9), (-1.2, -1.9), (-1.45, -1.5), (-1.45, 0.7), (-1.15, 1.1)]
    tp.add(W.poly_turret(corners, [(1.7, 1.0, 1.0, 1.0), (2.2, 1.0, 1.0, 1.0), (2.45, 0.95, 0.95, 0.97), (2.65, 0.72, 0.75, 0.8)], smooth=22))
    t = Geo(30)
    t.extend(K.hatch((-0.55, 2.62, -0.6), 0.26, h=0.05))
    t.extend(K.hatch((0.5, 2.62, -0.9), 0.24, h=0.05))
    t.extend(K.chbox((-0.12, 2.65, -1.8), (0.12, 3.25, -1.55), 'metal_dark', 0.02))
    t.extend(K.chbox((0.2, 2.62, 0.15), (0.6, 2.8, 0.45), 'armour', 0.03))
    t.extend(panel((0.4, 2.71, 0.452), 0.28, 0.1, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(K.antenna((-1.0, 2.5, -1.5), 2.4))
    for s in (-1, 1):
        t.extend(K.smoke_launchers((s * 1.35, 2.35, 0.75), 3, (s * 0.7, 0, 0.6), spacing=0.12, length=0.28, elev=25, spread=7))
        A.stow(t, s, 1.45, 1.6, 1.8, 2.3, -1.5, -0.2, c=0.02)
    tp.add(t)
    rp = MT.mirror_part(m, bm, 'radar', tp)
    R = Geo(30)
    R.extend(KT.dish((0, 3.4, -1.5), 0.5, (0, 0.15, 1), depth=0.22, mat='lattice', rim='metal_dark', n=14, rings=3))
    R.extend(cyl((0, 3.4, -1.55), (0, 3.4, -1.3), 0.05, 'metal_dark', n=6))
    R.extend(cyl((0, 3.05, -1.68), (0, 3.4, -1.55), 0.07, 'metal_dark', n=6))
    rp.add(R)

    gp = MT.mirror_part(m, bm, 'guns', tp)
    G = Geo(40)
    mz = MT.muzzle_abs(bm)
    zm = mz[2]
    G.extend(K.chbox((-0.9, 1.85, 0.8), (0.9, 2.25, 1.2), 'armour', 0.05))
    for s in (-1, 1):
        G.extend(K.chbox((min(s * 0.45, s * 0.9), 1.8, 0.8), (max(s * 0.45, s * 0.9), 2.3, 1.45), 'armour', 0.04))
        for y in (1.95, 2.15):
            x = s * 0.55 + (0.0)
            G.extend(lathe((x, y, 1.4), (x, y, zm), [(0, 0.0, 'metal_dark'), (0, 0.075, 'armour'), (0.3, 0.075, 'armour'), (0.35, 0.06, 'metal_dark'), (0.9, 0.06, 'metal_dark'),
                                                    (0.95, 0.032, 'steel_worn'), (zm - 1.4 - 0.15, 0.032, 'steel_worn'), (zm - 1.4 - 0.12, 0.055, 'metal_dark'), (zm - 1.4, 0.055, 'metal_dark'),
                                                    (zm - 1.4, 0.02, 'black'), (zm - 1.4 - 0.04, 0.0, 'black')], 'metal_dark', 8, 40))
    gp.add(G)
    return m


# ---------------------------------------------------------------------------------------------------------------
# 9K35 Strela-10


def strela10(bm):
    m = MT.new_model(bm, density=22)
    st = [(-3.2, 1.2, 0.45), (-3.15, 1.65, 0.45), (2.5, 1.65, 0.45), (2.75, 1.4, 0.45), (3.1, 1.4, 0.45), (3.35, 0.85, 0.45)]
    body, d = hull(m, bm, st, 1.42, 1.0, 0.85, 3.35, -3.2, 1.65, skirt=(-2.9, 2.5, 6), hatch=(-0.75, 2.8), lights_x=1.1, lamp_y=1.2)
    e = Geo(30)
    e.extend(MT.louvres(-1.0, 1.0, 1.65, -3.0, -2.0))
    e.extend(K.chbox((-1.2, 1.4, 2.5), (1.2, 1.5, 2.9), 'armour', 0.02))
    for s in (-1, 1):
        A.stow(e, s, 0.8, 1.4, 1.65, 1.9, -3.0, -2.2, c=0.04)
    body.add(e)
    tracks(m, bm, [(-1.4, 0.65, 0.09), (0.0, 0.65, 0.09), (1.4, 0.65, 0.09)])

    tp = MT.mirror_part(m, bm, 'turret')
    corners = [(0.6, 0.35), (0.75, 0.1), (0.75, -0.9), (0.5, -1.15), (-0.5, -1.15), (-0.75, -0.9), (-0.75, 0.1), (-0.6, 0.35)]
    tp.add(W.poly_turret(corners, [(1.65, 1.0, 1.0, 1.0), (2.1, 1.0, 1.0, 1.0), (2.35, 0.9, 0.92, 0.95)], smooth=22))
    t = Geo(30)
    t.extend(K.chbox((-0.3, 2.35, -0.7), (0.3, 2.55, 0.0), 'armour', 0.03))
    t.extend(K.chbox((-0.25, 2.1, 0.33), (0.25, 2.4, 0.44), 'armour', 0.02))
    t.extend(panel((0, 2.25, 0.442), 0.36, 0.22, (-1, 0, 0), (0, 1, 0), 'glass'))
    t.extend(K.hatch((0.0, 2.35, -0.95), 0.22, h=0.05))
    t.extend(K.antenna((0.55, 2.35, -1.0), 2.0))
    tp.add(t)
    lp = MT.mirror_part(m, bm, 'launcher', tp)
    L = Geo(30)
    L.extend(K.chbox((-1.15, 2.22, -0.48), (1.15, 2.38, -0.32), 'armour_dark', 0.03))
    for s in (-1, 1):
        L.extend(K.chbox((s * 1.1 - 0.06, 2.0, -0.55), (s * 1.1 + 0.06, 2.3, -0.25), 'armour', 0.02))
        L.extend(cyl((s * 0.3, 1.9, -0.3), (s * 0.3, 2.25, -0.9), 0.05, 'metal_dark', n=8))
    lp.add(L)
    for i in range(4):
        r = [r_ for r_ in bm.rails if r_[0] == 'can_%d' % i][0]
        e0 = MT.elev_abs(bm)
        x, y = e0[0] + r[1], e0[1] + r[2]
        cp = MT.mirror_part(m, bm, 'can_%d' % i, lp)
        cp.add(KT.tpk((x, y, -1.4), (x, y, 0.8), 0.24, 0.24, mat='armour', ribs=4))
    return m


JOBS = {'tor': tor, 'buk': buk, 'tunguska': tunguska, 'shilka': shilka, 'strela10': strela10}
