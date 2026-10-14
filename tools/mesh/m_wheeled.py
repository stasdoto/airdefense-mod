"""
2.0: wheeled armoured vehicles (BTR-82A, BTR-4, Stryker): lofted hulls tucked over the wheels, turrets, tyres.
"""
import math

import numpy as np

import geom
import kit_tanks as K
import m_tanks as MT
import m_west as W
from core import Geo
from geom import cyl, lathe, panel, tube


def ring(hs_low, hs_up, hs_top, belly, deck, shelf=1.25, side_top=None):
    """A wheeled hull's cross-section (12 points): belly, the narrow lower sides above the wheel tops, the shelf out to the
    upper sides, the sloped top. Everything above {deck} is flattened to the deck."""
    st = side_top if side_top is not None else deck - 0.25
    pts = [(-hs_low, belly), (hs_low, belly), (hs_low + 0.05, belly + 0.1), (hs_low + 0.05, shelf), (hs_up, shelf + 0.05), (hs_up, st),
           (hs_top, deck), (-hs_top, deck), (-hs_up, st), (-hs_up, shelf + 0.05), (-hs_low - 0.05, shelf), (-hs_low - 0.05, belly + 0.1)]
    out = []
    for x, y in pts:
        out.append((x, min(y, deck)))
    return out


def wheeled(bm, kind):
    m = MT.new_model(bm, density=26)
    body = m.part('body', (0, 0, 0))
    cfg = {
        'btr82': dict(hs_low=0.9, hs_up=1.45, hs_top=1.2, belly=0.5, deck=1.95, shelf=1.25, z0=-3.8, zf=2.85, zn=3.85, yn=0.62, hood=1.2, rw=0.4, rr=0.6),
        'btr4': dict(hs_low=0.9, hs_up=1.47, hs_top=1.25, belly=0.55, deck=2.1, shelf=1.3, z0=-3.85, zf=3.7, zn=3.9, yn=0.8, hood=1.3, rw=0.42, rr=0.62),
        'stryker': dict(hs_low=0.84, hs_up=1.36, hs_top=1.15, belly=0.5, deck=2.15, shelf=1.2, z0=-3.45, zf=2.25, zn=3.45, yn=0.65, hood=1.77, rw=0.4, rr=0.55),
    }[kind]
    z0, zf, zn = cfg['z0'], cfg['zf'], cfg['zn']
    deck = cfg['deck']
    mk = lambda d, **kw: ring(cfg['hs_low'], cfg['hs_up'], cfg['hs_top'], cfg['belly'], d, cfg['shelf'], **kw)
    secs = [(z0, mk(deck - 0.5)), (z0 + 0.05, mk(deck)), (zf, mk(deck)), (zf + 0.02, mk(cfg['hood'])), (zn, mk(cfg['yn']))]
    g = Geo(25)
    g.extend(K.section_loft(secs, 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    # Windscreen: two armoured glass blocks with frames on the front wall.
    if kind in ('btr82', 'btr4'):
        wy0, wy1 = (1.5, 1.86) if kind == 'btr82' else (1.5, 1.95)
        zz = zf if kind == 'btr82' else 3.7
        for s in (-1, 1):
            x0, x1 = (0.08, 1.1) if s > 0 else (-1.1, -0.08)
            d.extend(K.chbox((x0 - 0.04, wy0 - 0.04, zz - 0.02), (x1 + 0.04, wy1 + 0.04, zz + 0.02), 'armour', 0.01))
            d.extend(panel(((x0 + x1) / 2, (wy0 + wy1) / 2, zz + 0.025), x1 - x0, wy1 - wy0, (-1, 0, 0), (0, 1, 0), 'glass'))
    # Headlights, towing hooks, rear lamps.
    for s in (-1, 1):
        d.extend(K.headlight((s * 1.0, cfg['hood'] - 0.05 if kind != 'btr4' else 1.0, zn - 0.12), (0, 0, 1), r=0.07))
        d.extend(tube([(s * 0.5, 0.55, zn - 0.05), (s * 0.5, 0.58, zn + 0.1), (s * 0.5, 0.5, zn + 0.15), (s * 0.5, 0.45, zn)], 0.03, 'metal_dark', n=6))
        d.extend(K.chbox((s * 1.0 - 0.1, 1.3, z0 - 0.03), (s * 1.0 + 0.1, 1.4, z0 + 0.04), 'armour', 0.01))
        d.extend(panel((s * 1.0, 1.35, z0 - 0.032), 0.14, 0.07, (-1, 0, 0), (0, 1, 0), 'taillight'))
    # Wheel arch flares: a lip on the shelf round each wheel.
    for name, r, steer in bm.wheels:
        bp = MT.bpart(bm, name)
        c = bp.pivot
        s = -1 if name.endswith('_l') else 1
        d.extend(K.chbox((min(s * 1.0, s * 1.5), cfg['shelf'] - 0.01, c[2] - r * 1.1), (max(s * 1.0, s * 1.5), cfg['shelf'] + 0.03, c[2] + r * 1.1), 'armour', 0.01))
    if kind == 'btr82':
        # The roof raised over the troop bay with hatches, side doors with firing ports, the engine louvres at the rear.
        for s in (-1, 1):
            d.extend(K.chbox((s * 1.47 - 0.015, 1.0, -0.2), (s * 1.47 + 0.015, 1.7, 0.6), 'armour_dark', 0.005))
            for k in range(3):
                d.extend(K.hatch((s * 0.7, deck, -2.0 + k * 0.6 + 0.0), 0.2, h=0.04, handle=False))
            for k in range(3):
                d.extend(panel((s * 1.475, 1.55, -1.2 + k * 0.8), 0.14, 0.1, (0, 0, s), (0, 1, 0), 'black', double=False) if False else Geo(1))
            d.extend(K.smoke_launchers((s * 1.0, 2.25, 0.2), 3, (s * 0.9, 0, 0.45), spacing=0.12, r=0.045, length=0.3, elev=30, spread=8)) if False else Geo(1)
        d.extend(K.chbox((-1.2, deck, -2.5), (1.2, 2.15, 1.3), 'armour', 0.05))  # roof
        d.extend(MT.louvres(-0.8, 0.8, deck, -3.7, -2.9))
        d.extend(K.chbox((-0.8, 0.8, z0 - 0.15), (0.8, 1.55, z0 + 0.02), 'armour', 0.03))
        d.extend(K.tow_cable([(-1.3, 1.5, -3.5), (-1.3, 1.5, -1.0), (-1.3, 1.5, 2.0)]))
    body.add(d)
    # Wheels
    for name, r, steer in bm.wheels:
        bp = MT.bpart(bm, name)
        c = tuple(bp.pivot)
        side = -1 if name.endswith('_l') else 1
        p = m.part(name, c, (0, 0, 0))
        p.add(geom.wheel(c, side, r, cfg['rw'], 0.58, 'steel', n=24))
        p.spin = r
        p.steer = steer
    # Turret
    tp = MT.mirror_part(m, bm, 'turret')
    tx, ty, tz = tp.pivot
    T = Geo(50)
    t = Geo(30)
    if kind == 'btr82':
        cz = 0.58
        lv = [(2.15, K.superellipse(24, 0.7, 1.0, e=2.6, cz=cz, front=0.73, back=0.73)), (2.29, K.superellipse(24, 0.66, 1.0, e=2.6, cz=cz, front=0.69, back=0.7)),
              (2.42, K.superellipse(24, 0.57, 1.0, e=2.6, cz=cz, front=0.6, back=0.6)), (2.56, K.superellipse(24, 0.5, 1.0, e=2.6, cz=cz, front=0.55, back=0.5)),
              (2.7, K.superellipse(24, 0.38, 1.0, e=2.6, cz=cz, front=0.45, back=0.4))]
        T.extend(K.plan_loft(lv, 'armour', smooth=50))
        t.extend(K.hatch((-0.25, 2.68, cz - 0.3), 0.2, h=0.05))
        t.extend(K.chbox((0.1, 2.62, cz - 0.1), (0.5, 2.78, cz + 0.2), 'armour', 0.03))
        t.extend(panel((0.3, 2.7, cz + 0.202), 0.2, 0.08, (-1, 0, 0), (0, 1, 0), 'optic'))
        for s in (-1, 1):
            t.extend(K.smoke_launchers((s * 0.62, 2.45, cz + 0.45), 3, (s * 0.8, 0, 0.6), spacing=0.1, r=0.04, length=0.28, elev=30, spread=8))
    elif kind == 'btr4':
        corners = [(0.85, -0.6), (0.85, -0.2), (0.7, 0.2), (0.4, 0.38), (-0.4, 0.38), (-0.7, 0.2), (-0.85, -0.2), (-0.85, -1.4), (-0.7, -1.6), (0.7, -1.6), (0.85, -1.4)]
        T.extend(W.poly_turret(corners, [(2.1, 1.0, 1.0, 1.0), (2.2, 1.0, 1.0, 1.0), (2.55, 0.97, 0.97, 1.0), (2.68, 0.9, 0.9, 0.95), (2.7, 0.86, 0.86, 0.93)]))
        t.extend(K.hatch((-0.3, 2.68, -0.9), 0.22, h=0.05))
        t.extend(K.chbox((0.3, 2.68, -0.9), (0.7, 2.95, -0.5), 'armour', 0.03))  # the commander's panoramic sight
        t.extend(panel((0.5, 2.82, -0.498), 0.22, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
        # The Barrier ATGM pod on the left of the turret: two tubes.
        t.extend(K.chbox((-1.14, 2.35, -1.0), (-0.9, 2.65, 0.3), 'armour', 0.03))
        for dz in (-0.15, 0.1):
            t.extend(cyl((-1.02, 2.5, dz - 0.5), (-1.02, 2.5, dz + 0.35), 0.06, 'armour_dark', n=8))
        t.extend(K.smoke_launchers((0.9, 2.45, -0.1), 3, (1, 0, 0.35), spacing=0.1, r=0.04, length=0.28, elev=28, spread=8))
    elif kind == 'stryker':
        cz = 0.2
        t.extend(K.chbox((-0.28, 2.3, -0.12), (0.28, 2.58, 0.48), 'armour', 0.05))
        t.extend(K.chbox((-0.24, 2.58, 0.02), (0.06, 2.78, 0.42), 'armour', 0.04))
        t.extend(panel((-0.09, 2.7, 0.422), 0.2, 0.1, (-1, 0, 0), (0, 1, 0), 'optic'))
    tp.add(T)
    tp.add(t)
    # Gun
    gp = MT.mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = MT.muzzle_abs(bm)
    gg = Geo(40)
    z0g = piv[2] + 0.1
    L = mz[2] - z0g
    gx = piv[0]
    if kind == 'stryker':
        gg.extend(K.mg((gx, piv[1], piv[2] - 0.3), (0, 0, 1), L + 0.6))
    else:
        gg.extend(K.chbox((gx - 0.2, piv[1] - 0.13, piv[2] - 0.2), (gx + 0.2, piv[1] + 0.13, piv[2] + 0.3), 'armour', 0.04))
        gg.extend(K.barrel((gx, piv[1], z0g), (gx, piv[1], mz[2]), [(0, 0.0, 'armour'), (0, 0.05, 'armour'), (L * 0.3, 0.04, 'armour'), (L * 0.33, 0.05, 'armour'), (L * 0.7, 0.05, 'armour'),
                                                                   (L * 0.72, 0.04, 'armour'), (L - 0.2, 0.035, 'armour'), (L - 0.19, 0.05, 'metal_dark'), (L, 0.05, 'metal_dark'),
                                                                   (L, 0.02, 'black'), (L - 0.05, 0.0, 'black')], n=10))
        gg.extend(cyl((gx - 0.12, piv[1] - 0.02, z0g), (gx - 0.12, piv[1] - 0.02, z0g + 0.6), 0.02, 'metal_dark', n=6))
    gp.add(gg)
    return m
