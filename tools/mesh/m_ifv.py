"""
2.0: tracked infantry fighting vehicles and APCs (BMP-2, BMP-3, Bradley, CV90, M113): lofted hulls, low turrets
with autocannons and launchers, running gear from the shared kit.
"""
import math

import numpy as np

import kit_tanks as K
import m_tanks as MT
import m_west as W
from core import Geo
from geom import cyl, lathe, panel, tube, loft


def missile(a, b, r, mat='armour'):
    """An ATGM round in its tube: body, nose cone, fins stub."""
    g = Geo(40)
    d = np.array(b, dtype=np.float64) - np.array(a, dtype=np.float64)
    L = float(np.linalg.norm(d))
    g.extend(lathe(a, b, [(0, 0), (0.0, r), (L * 0.78, r), (L * 0.9, r * 0.75), (L, r * 0.15), (L, 0.0)], mat, 10, 40))
    return g


def ifv(bm, kind):
    m = MT.new_model(bm, density=26)
    body = m.part('body', (0, 0, 0))
    cfg = {
        'bmp2': dict(hw_low=1.1, hw_up=1.57, ys=0.9, st=[(-3.35, 1.4, 0.55), (-3.3, 1.74, 0.45), (1.75, 1.75, 0.45), (3.4, 0.66, 0.45)], drive='front',
                     wk='single', rl=[(-1.6, 0.84, 0.07), (-0.1, 0.84, 0.07), (1.3, 0.84, 0.07)], track='track_steel'),
        'bmp3': dict(hw_low=1.1, hw_up=1.57, ys=0.9, st=[(-3.57, 1.5, 0.55), (-3.52, 1.74, 0.45), (2.27, 1.75, 0.45), (3.62, 0.66, 0.45)], drive='rear',
                     wk='single', rl=[(-1.9, 0.84, 0.07), (-0.2, 0.84, 0.07), (1.5, 0.84, 0.07)], track='track_steel'),
        'bradley': dict(hw_low=1.25, hw_up=1.8, ys=1.0, st=[(-3.28, 1.55, 0.55), (-3.22, 2.12, 0.45), (2.28, 2.15, 0.45), (3.33, 0.75, 0.45)], drive='front',
                        wk='spoked', rl=[(-2.0, 0.84, 0.08), (-0.7, 0.84, 0.08), (0.7, 0.84, 0.08), (1.9, 0.84, 0.08)], track='track_rubber'),
        'cv90': dict(hw_low=1.1, hw_up=1.6, ys=0.95, st=[(-3.3, 1.5, 0.55), (-3.25, 1.93, 0.45), (2.1, 1.95, 0.45), (3.35, 0.72, 0.45)], drive='front',
                     wk='single', rl=[(-2.0, 0.8, 0.07), (-0.6, 0.8, 0.07), (0.8, 0.8, 0.07), (1.8, 0.8, 0.07)], track='track_rubber'),
        'm113': dict(hw_low=1.1, hw_up=1.33, ys=0.78, st=[(-2.49, 1.5, 0.5), (-2.45, 1.94, 0.45), (1.73, 1.95, 0.45), (2.43, 0.55, 0.45)], drive='front',
                     wk='single', rl=[(-0.9, 0.78, 0.07)], track='track_steel'),
    }[kind]
    st = cfg['st']
    deck = st[2][1]
    secs = MT.hull_sections(st, cfg['hw_low'], cfg['hw_up'], cfg['ys'], ch=0.06)
    g = Geo(25)
    g.extend(K.section_loft(secs, 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    hwu = cfg['hw_up']
    g0 = st[2][0]
    gl = st[3][0]
    rear = st[0][0]
    # Front: headlights, trim vane (BMP), tow hooks, driver's hatch.
    drv_x = {'bmp2': -0.55, 'bmp3': 0.0, 'bradley': -0.9, 'cv90': -0.7, 'm113': -0.6}[kind]
    d.extend(K.hatch((drv_x, deck, g0 - 0.3), 0.26, h=0.05))
    for a in (-30, 0, 30):
        ar = math.radians(a)
        d.extend(K.periscope((drv_x + math.sin(ar) * 0.31, deck, g0 - 0.3 + math.cos(ar) * 0.31), 0.14, (math.sin(ar), 0, math.cos(ar))))
    gz = gl - 0.3
    for s in (-1, 1):
        d.extend(K.headlight((s * (hwu - 0.2), 0.95 if kind != 'm113' else 0.9, gl - 0.05), (0, 0, 1), r=0.07))
        d.extend(tube([(s * 0.6, 0.55, gl - 0.05), (s * 0.6, 0.58, gl + 0.12), (s * 0.6, 0.5, gl + 0.17), (s * 0.6, 0.45, gl)], 0.03, 'metal_dark', n=6))
        # rear lights and a stowage box on the fender
        d.extend(K.chbox((s * (hwu - 0.35) - 0.1, deck - 0.45, rear - 0.03), (s * (hwu - 0.35) + 0.1, deck - 0.3, rear + 0.03), 'armour', 0.01))
        d.extend(panel((s * (hwu - 0.35), deck - 0.375, rear - 0.032), 0.14, 0.07, (-1, 0, 0), (0, 1, 0), 'taillight'))
        # Track guards / skirts hanging from the sponson
        if kind != 'm113':
            z0, z1 = -2.9 if kind != 'cv90' else -3.0, 2.6
            n = 5
            sl = (z1 - z0) / n
            x = s * (hwu + 0.005)
            for k in range(n):
                za = z0 + k * sl + 0.01
                d.extend(K.chbox((x - 0.025, 0.62, za), (x + 0.025, 1.0, za + sl - 0.02), 'armour_dark' if kind in ('bmp2', 'bmp3') else 'armour', 0.01))
    if kind in ('bmp2', 'bmp3'):
        # Trim vane on the glacis, the troop hatches at the rear, the engine deck.
        d.extend(K.chbox((-1.0, 0.95, gl - 0.9), (1.0, 1.3, gl - 0.83), 'armour', 0.015))
        zh = -3.0 if kind == 'bmp2' else -3.2
        for s in (-1, 1):
            for k in range(4):
                d.extend(K.hatch((s * 1.03, deck, zh + k * 0.45), 0.15, h=0.04, handle=False))
        d.extend(MT.louvres(0.2, 0.9, deck, -2.85 if kind == 'bmp2' else -3.27, -1.45 if kind == 'bmp2' else -1.77))
        d.extend(K.chbox((1.1, deck - 0.01, -3.15), (1.55, deck + 0.22, -2.35), 'armour', 0.03))
        d.extend(K.tow_cable([(-1.5, deck + 0.03, -2.0), (-1.5, deck + 0.04, 0.0), (-1.4, deck + 0.04, 1.2)]))
        d.extend(K.chbox((0.25, 0.95, rear - 0.1), (0.95, 1.6, rear + 0.04), 'armour', 0.02))
        d.extend(K.chbox((-0.9, 0.95, rear - 0.1), (-0.25, 1.6, rear + 0.04), 'armour', 0.02))
        if kind == 'bmp3':
            d.extend(MT.louvres(-1.2, 1.2, 1.95, -3.27, -1.77))
    elif kind == 'bradley':
        # Spaced-armour tiles on the hull sides, the ramp at the rear, the gunner's hatch, driver's periscopes.
        for s in (-1, 1):
            for k in range(5):
                za = -2.88 + k * 0.95
                d.extend(K.chbox((s * 1.8 - (0.05 if s > 0 else -0.05) * 0 + (0.0 if s > 0 else -0.1), 1.25, za), (s * 1.8 + (0.1 if s > 0 else 0.0), 1.95, za + 0.9), 'era', 0.015))
        d.extend(K.chbox((-1.6, deck, -2.98), (-0.6, deck + 0.3, -2.08), 'armour', 0.03))
        d.extend(MT.louvres(-1.4, -0.8, deck + 0.3, -2.9, -2.2, pitch=0.1))
        d.extend(K.chbox((-0.4, deck, -3.1), (1.3, deck + 0.04, -1.6), 'armour', 0.01))
        d.extend(K.chbox((-1.0, 0.9, rear - 0.08), (1.0, 1.9, rear + 0.04), 'armour', 0.03))
    elif kind == 'cv90':
        d.extend(K.chbox((-0.75, 1.0, rear - 0.08), (0.75, 1.9, rear + 0.04), 'armour', 0.03))
        for s in (-1, 1):
            for k in range(2):
                d.extend(K.hatch((s * 0.6, deck, -2.8 + k * 0.7), 0.2, h=0.05))
        d.extend(MT.louvres(0.0, 0.9, deck, -1.6, -0.9))
        d.extend(K.tow_cable([(-1.5, deck + 0.03, -3.0), (-1.5, deck + 0.04, 0.0), (-1.4, deck + 0.04, 1.8)]))
    elif kind == 'm113':
        d.extend(K.chbox((-0.85, 0.5, rear - 0.08), (0.85, 1.8, rear + 0.04), 'armour', 0.03))  # ramp
        for s in (-1, 1):
            d.extend(K.hatch((s * 0.7, deck, -1.0), 0.28, h=0.05))
        d.extend(K.chbox((-0.9, deck, 0.7), (-0.4, deck + 0.1, 1.5), 'armour', 0.02))
        d.extend(K.tow_cable([(-1.3, deck - 0.2, -2.0), (-1.3, deck - 0.2, 0.5), (-1.3, deck - 0.25, 1.5)]))
        # Corrugations on the sides (aluminium hull ribs)
        for s in (-1, 1):
            for k in range(8):
                za = -2.2 + k * 0.5
                d.extend(K.chbox((s * (hwu + 0.0) - 0.015, 1.1, za), (s * (hwu + 0.0) + 0.015, 1.9, za + 0.08), 'armour_dark', 0.004))
    body.add(d)
    MT.running_gear(m, bm, MT.Gear(rollers=cfg['rl'], drive=cfg['drive'], pitch=0.15, thick=0.055, wheel_kind=cfg['wk'], track_mat=cfg['track'], n=14))

    # Turret ------------------------------------------------------------------------------------------------
    tp = MT.mirror_part(m, bm, 'turret')
    tx, ty, tz = tp.pivot
    T = Geo(50)
    t = Geo(30)
    gp0 = MT.bpart(bm, 'gun')
    gy = gp0.pivot[1]
    if kind in ('bmp2', 'bmp3'):
        zc = (-1.1 + 0.66) / 2 if kind == 'bmp2' else (-0.85 + 1.2) / 2
        rzf, rzb = (0.66 - zc, zc + 1.1) if kind == 'bmp2' else (1.2 - zc, zc + 0.85)
        rx = 0.85 if kind == 'bmp2' else 0.97
        lv = []
        for y, f in ((1.75, 1.0), (1.86, 1.0), (1.96, 0.9), (2.05, 0.75), (2.1 if kind == 'bmp2' else 2.3, 0.55)):
            if kind == 'bmp3':
                y = y if y < 2.1 else 2.3
            lv.append((y, K.superellipse(28, rx * f, 1.0, e=2.4, cz=zc, front=rzf * f, back=rzb * f)))
        T.extend(K.plan_loft(lv, 'armour', smooth=50))
        topy = lv[-1][0]
        # hatches: commander (right), gunner (left)
        t.extend(K.hatch((0.42, topy - 0.04, zc - 0.25), 0.25, h=0.05))
        t.extend(K.hatch((-0.42, topy - 0.04, zc - 0.25), 0.25, h=0.05))
        t.extend(K.periscope((0.42, topy - 0.0, zc + 0.15), 0.18, (0, 0, 1)))
        t.extend(K.chbox((-0.65, topy - 0.1, zc + 0.15), (-0.2, topy + 0.12, zc + 0.4), 'armour', 0.03))
        t.extend(panel((-0.42, topy + 0.03, zc + 0.402), 0.22, 0.09, (-1, 0, 0), (0, 1, 0), 'optic'))
        for s in (-1, 1):
            t.extend(K.smoke_launchers((s * 0.97, 2.0, zc + 0.5), 3, (s * 0.85, 0, 0.5), spacing=0.1, r=0.04, length=0.28, elev=30, spread=8))
        t.extend(cyl((-0.82, topy - 0.2, zc - 0.5), (-0.82, topy + 0.9, zc - 0.5), 0.012, 'black', n=4))
        if kind == 'bmp2':
            # The Konkurs launcher folded on the right of the roof: rail and the missile.
            t.extend(K.chbox((0.55, 2.15, -0.75), (0.75, 2.38, 0.1), 'armour', 0.02))
            t.extend(missile((0.65, 2.45, -0.8), (0.65, 2.45, 0.35), 0.06, 'armour'))
        else:
            t.extend(K.chbox((0.2, 2.1, -1.1), (0.9, 2.3, -0.8), 'armour', 0.03))
    elif kind == 'bradley':
        lv = []
        zc = (-1.4 + 0.6) / 2
        for y, f in ((2.15, 1.0), (2.3, 1.0), (2.6, 0.97), (2.82, 0.9), (2.85, 0.84)):
            cx = 0.35
            lv.append((y, K.superellipse(24, 0.95 * f, 1.0, e=3.2, cx=cx, cz=zc, front=1.0 * f, back=1.0 * f)))
        T.extend(K.plan_loft(lv, 'armour', smooth=45))
        t.extend(W.cupola((0.85, 2.82, -0.3), 0.3, h=0.2, n_peri=5))
        t.extend(K.mg((0.85, 3.12, -0.3), (0, 0, 1), 0.9))
        t.extend(K.hatch((0.0, 2.84, -0.9), 0.22, h=0.05))
        # The TOW pod on the left side, two tubes.
        t.extend(K.chbox((-1.15, 2.3, -0.95), (-0.62, 2.8, 0.1), 'armour', 0.04))
        for dz in (-0.55, -0.1):
            t.extend(cyl((-0.9, 2.55 + 0.0, dz - 0.4), (-0.9, 2.55, dz + 0.45), 0.13, 'armour_dark', n=10))
        t.extend(panel((-0.88, 2.55, 0.108), 0.3, 0.3, (-1, 0, 0), (0, 1, 0), 'black'))
        # Smoke launchers: four tubes on the right.
        t.extend(K.smoke_launchers((1.45, 2.65, 0.3), 4, (1, 0, 0.35), spacing=0.12, r=0.045, length=0.35, elev=28, spread=7))
        t.extend(K.chbox((0.35, 2.29, -1.8), (1.15, 2.6, -1.45), 'armour', 0.03))  # bustle stowage
    elif kind == 'cv90':
        cz = -0.4
        corners = [(1.3, -0.4), (1.3, 0.3), (0.95, 0.75), (0.45, 1.0), (-0.45, 1.0), (-0.95, 0.75), (-1.3, 0.3), (-1.3, -1.6), (-1.1, -2.3), (1.1, -2.3), (1.3, -1.6)]
        T.extend(W.poly_turret(corners, [(1.95, 1.0, 1.0, 1.0), (2.05, 1.0, 1.0, 1.0), (2.45, 0.98, 0.98, 1.0), (2.62, 0.9, 0.9, 0.95), (2.65, 0.86, 0.86, 0.93)]))
        t.extend(W.cupola((0.7, 2.62, -0.65), 0.28, h=0.18, n_peri=5))
        t.extend(K.mg((0.7, 2.88, -0.65), (0, 0, 1), 0.8))
        t.extend(K.hatch((-0.6, 2.62, -0.7), 0.24, h=0.05))
        t.extend(K.chbox((-0.4, 2.62, 0.05), (0.2, 2.82, 0.4), 'armour', 0.03))  # gunner's sight
        t.extend(panel((-0.1, 2.72, 0.402), 0.3, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
        for s in (-1, 1):
            t.extend(K.smoke_launchers((s * 1.34, 2.38, -0.2), 4, (s * 0.9, 0, 0.3), spacing=0.1, r=0.045, length=0.32, elev=28, spread=7))
            # Rear stowage basket sides
            t.extend(K.slats(-1.1, 1.1, 2.1, 2.5, -2.3, 12, axis='x') if s == 1 else Geo(1))
        t.extend(K.antenna((-1.0, 2.62, -1.6), 3.0))
    elif kind == 'm113':
        pass
    tp.add(T)
    tp.add(t)

    # Gun ---------------------------------------------------------------------------------------------------
    gp = MT.mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = MT.muzzle_abs(bm)
    gg = Geo(40)
    z0 = piv[2] + 0.1
    L = mz[2] - z0
    gx = piv[0]
    if kind == 'bmp2':
        gg.extend(K.chbox((-0.25, piv[1] - 0.14, piv[2] - 0.2), (0.25, piv[1] + 0.14, piv[2] + 0.3), 'armour', 0.04))
        gg.extend(K.barrel((gx, piv[1], z0), (gx, piv[1], mz[2]), [(0, 0.0, 'armour'), (0, 0.05, 'armour'), (L * 0.3, 0.04, 'armour'), (L * 0.35, 0.05, 'armour'),
                                                                   (L * 0.7, 0.05, 'armour'), (L * 0.72, 0.04, 'armour'), (L - 0.2, 0.035, 'armour'), (L - 0.19, 0.05, 'metal_dark'),
                                                                   (L, 0.05, 'metal_dark'), (L, 0.02, 'black'), (L - 0.05, 0.0, 'black')], n=10))
        gg.extend(cyl((gx - 0.12, piv[1] - 0.02, z0), (gx - 0.12, piv[1] - 0.02, z0 + 0.6), 0.02, 'metal_dark', n=6))
    elif kind == 'bmp3':
        gg.extend(K.chbox((-0.35, piv[1] - 0.2, piv[2] - 0.3), (0.35, piv[1] + 0.2, piv[2] + 0.3), 'armour', 0.05))
        gg.extend(K.barrel((gx, piv[1], z0), (gx, piv[1], mz[2]), W.gun_profile(L, 0.058, extractor=(L * 0.45, L * 0.58, 0.08), muzzle='plain'), n=12))
        gg.extend(K.barrel((gx + 0.22, piv[1] - 0.02, z0), (gx + 0.22, piv[1] - 0.02, mz[2] + 0.1), [(0, 0.0), (0, 0.04), (L * 0.6, 0.035), (L * 0.62, 0.03), (L + 0.1, 0.03),
                                                                                                         (L + 0.1, 0.0)], n=8))
    elif kind == 'bradley':
        gg.extend(K.chbox((gx - 0.22, piv[1] - 0.14, piv[2] - 0.2), (gx + 0.22, piv[1] + 0.14, piv[2] + 0.35), 'armour', 0.04))
        gg.extend(K.barrel((gx, piv[1], z0), (gx, piv[1], mz[2]), [(0, 0.0, 'armour'), (0, 0.05, 'armour'), (L * 0.3, 0.04, 'armour'), (L * 0.33, 0.055, 'armour'), (L * 0.6, 0.055, 'armour'),
                                                                   (L * 0.62, 0.04, 'armour'), (L - 0.25, 0.035, 'armour'), (L - 0.24, 0.045, 'metal_dark'), (L, 0.045, 'metal_dark'),
                                                                   (L, 0.02, 'black'), (L - 0.05, 0.0, 'black')], n=10))
    elif kind == 'cv90':
        gg.extend(K.chbox((-0.35, piv[1] - 0.22, piv[2] - 0.35), (0.35, piv[1] + 0.22, piv[2] + 0.25), 'armour', 0.05))
        gg.extend(K.barrel((gx, piv[1], z0), (gx, piv[1], mz[2]), W.gun_profile(L, 0.06, sleeve=(L * 0.08, L * 0.5, 0.095), extractor=(L * 0.52, L * 0.62, 0.085), muzzle='plain'), n=12))
    elif kind == 'm113':
        gg.extend(K.mg((gx, piv[1], piv[2]), (0, 0, 1), 1.2, shield=True))
    gp.add(gg)
    return m
