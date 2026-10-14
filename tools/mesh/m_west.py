"""
2.0: Western main battle tanks (Leopard 2, Abrams, Challenger 2): wide welded hulls with side skirts, angular
turrets lofted from corner polygons, smooth-bore barrels with sleeves, rubber-padded tracks.
"""
import math

import numpy as np

import kit_tanks as K
import m_tanks as MT
from core import Geo
from geom import cyl, lathe, panel, tube, loft


def poly_turret(corners, levels, mat='armour', smooth=18):
    """A turret lofted through the same corner polygon (x, z) at several heights: levels [(y, sx, sf, sb)] scale the
    outline about its centre (x by sx, the part in front of the centre by sf, behind by sb)."""
    pts = np.array(corners, dtype=np.float64)
    c = pts.mean(axis=0)
    rings = []
    for y, sx, sf, sb in levels:
        ring = []
        for x, z in pts:
            dz = z - c[1]
            ring.append((c[0] + (x - c[0]) * sx, y, c[1] + dz * (sf if dz > 0 else sb)))
        rings.append(ring)
    g = loft(rings, mat, closed=True, cap0=False, cap1=True, smooth=smooth)
    K._orient(g, rings)
    return g


def gun_profile(L, r, sleeve=None, extractor=None, evac=None, muzzle='plain', mat='armour'):
    """From the mantlet (t=0) to the muzzle (t=L). sleeve (t0, t1, R): the thermal sleeve; extractor/evac (t0, t1, R)."""
    prof = [(0.0, 0.0, mat), (0.0, r * 1.3, mat), (0.3, r * 1.2, mat), (0.4, r * 1.05, mat)]
    marks = []
    if sleeve:
        t0, t1, R = sleeve
        marks.append((t0, [(t0, r * 1.05, mat), (t0 + 0.02, R, mat)]))
        n = max(1, int((t1 - t0) / 0.7))
        for k in range(1, n):
            t = t0 + (t1 - t0) * k / n
            marks.append((t, [(t - 0.02, R, mat), (t - 0.01, R * 1.07, 'armour_dark'), (t + 0.01, R * 1.07, 'armour_dark'), (t + 0.02, R, mat)]))
        marks.append((t1 - 0.02, [(t1 - 0.02, R, mat), (t1, r * 1.05, mat)]))
    for ex in (extractor, evac):
        if ex:
            t0, t1, R = ex
            marks.append((t0, [(t0 - 0.03, r * 1.05, mat), (t0, R * 0.9, mat), (t0 + 0.05, R, mat), (t1 - 0.06, R, mat), (t1, R * 0.88, mat), (t1 + 0.05, r * 1.05, mat)]))
    marks.sort(key=lambda m: m[0])
    for _, pts in marks:
        prof.extend(pts)
    if muzzle == 'plain':
        prof += [(L - 0.02, r, mat), (L, r * 0.93, 'steel_worn'), (L, r * 0.6, 'black'), (L - 0.05, 0.0, 'black')]
    else:  # muzzle reference sensor collar
        prof += [(L - 0.2, r, mat), (L - 0.18, r * 1.3, 'steel_worn'), (L - 0.03, r * 1.3, 'steel_worn'), (L, r * 1.15, 'steel_worn'), (L, r * 0.6, 'black'),
                 (L - 0.05, 0.0, 'black')]
    out = []
    last = -1.0
    for p in prof:
        t = max(p[0], last)
        out.append((t,) + tuple(p[1:]))
        last = t
    return out


def cupola(c, r, h=0.2, n_peri=6, mat='armour', ring=True):
    """A commander's cupola ring with periscopes round it and a hatch on top."""
    g = Geo(35)
    cx, cy, cz = c
    g.extend(lathe((cx, cy, cz), (cx, cy + h, cz), [(0.0, r), (0.0, r * 1.02), (h * 0.85, r), (h, r * 0.9), (h, 0.0)], mat, 16, 40))
    for k in range(n_peri):
        a = 2 * math.pi * (k + 0.5) / n_peri
        d = (math.sin(a), 0, math.cos(a))
        g.extend(K.periscope((cx + d[0] * r * 0.98, cy + h * 0.55, cz + d[2] * r * 0.98), r * 0.5, d))
    g.extend(K.hatch((cx, cy + h, cz), r * 0.78, h=0.05))
    return g


def west_mbt(bm, kind):
    m = MT.new_model(bm, density=24)
    body = m.part('body', (0, 0, 0))
    cfg = {
        'leopard2': dict(deck=1.75, hw_low=1.29, hw_up=1.88, rear=-3.85, glacis=(2.65, 3.9), skirt=(-3.45, 3.15), top=2.55,
                         corners=[(1.7, -0.7), (1.7, 0.05), (1.05, 0.95), (0.5, 1.22), (-0.5, 1.22), (-1.05, 0.95), (-1.7, 0.05), (-1.7, -2.4), (-1.45, -2.85),
                                  (1.45, -2.85), (1.7, -2.4)],
                         levels=[(1.75, 1.0, 1.0, 1.0), (1.85, 1.0, 1.0, 1.0), (2.3, 0.98, 0.97, 0.99), (2.5, 0.94, 0.9, 0.96), (2.55, 0.9, 0.86, 0.94)]),
        'abrams': dict(deck=1.55, hw_low=1.25, hw_up=1.83, rear=-3.95, glacis=(2.75, 4.0), skirt=(-3.55, 3.33), top=2.4,
                       corners=[(1.74, -0.8), (1.74, -0.1), (1.25, 0.62), (0.55, 0.9), (-0.55, 0.9), (-1.25, 0.62), (-1.74, -0.1), (-1.74, -2.2), (-1.45, -2.8),
                                (1.45, -2.8), (1.74, -2.2)],
                       levels=[(1.55, 1.0, 1.0, 1.0), (1.66, 1.0, 1.0, 1.0), (2.1, 0.97, 0.95, 1.0), (2.33, 0.9, 0.82, 0.96), (2.4, 0.84, 0.78, 0.94)]),
        'challenger2': dict(deck=1.65, hw_low=1.18, hw_up=1.76, rear=-3.95, glacis=(2.95, 4.2), skirt=(-3.6, 3.45), top=2.45,
                            corners=[(1.55, -0.6), (1.55, 0.2), (0.95, 0.9), (0.42, 1.2), (-0.42, 1.2), (-0.95, 0.9), (-1.55, 0.2), (-1.55, -2.0), (-1.25, -2.55),
                                     (1.25, -2.55), (1.55, -2.0)],
                            levels=[(1.65, 1.0, 1.0, 1.0), (1.75, 1.0, 1.0, 1.0), (2.2, 0.98, 0.98, 1.0), (2.38, 0.94, 0.9, 0.97), (2.45, 0.9, 0.86, 0.95)]),
    }[kind]
    deck, hwl, hwu = cfg['deck'], cfg['hw_low'], cfg['hw_up']
    g0, g1 = cfg['glacis']
    rear = cfg['rear']
    st = [(rear, deck - 0.35, 0.62), (rear + 0.06, deck - 0.03, 0.5), (rear + 0.4, deck, 0.45), (g0, deck, 0.45), ((g0 + g1) / 2, (deck + 0.8) / 2 + 0.05, 0.45), (g1, 0.82, 0.45)]
    secs = MT.hull_sections(st, hwl, hwu, 1.05, ch=0.05)
    g = Geo(25)
    g.extend(K.section_loft(secs, 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    # Glacis details: tow hooks, headlights on the fender corners, the driver's hatch and periscopes.
    ga = math.degrees(math.atan2(deck - 0.82, g1 - g0))
    d.extend(K.hatch((0, deck, g0 - 0.35), 0.28, h=0.05))
    for a in (-30, 0, 30):
        d.extend(K.periscope((math.sin(math.radians(a)) * 0.33, deck, g0 - 0.35 + math.cos(math.radians(a)) * 0.33), 0.15,
                             (math.sin(math.radians(a)), 0, math.cos(math.radians(a)))))
    for s in (-1, 1):
        zz = g1 - 0.2
        d.extend(K.chbox((s * 1.6 - 0.15, 0.78, zz - 0.15), (s * 1.6 + 0.15, 0.9, zz + 0.12), 'armour', 0.015))
        d.extend(K.headlight((s * 1.6, 0.97, zz + 0.1), (0, 0, 1), r=0.07))
        d.extend(K.headlight((s * 1.45, 0.95, zz + 0.1), (0, 0, 1), r=0.04, guard=False))
        d.extend(tube([(s * 0.7, 0.55, g1 - 0.15), (s * 0.7, 0.58, g1 + 0.05), (s * 0.7, 0.5, g1 + 0.1), (s * 0.7, 0.45, g1 - 0.05)], 0.03, 'metal_dark', n=6))
        # Skirts hanging from the sponson, with a gap between the plates; mud flaps front and rear.
        x = s * (hwu + 0.005)
        z0, z1 = cfg['skirt']
        nseg = 7
        sl = (z1 - z0) / nseg
        for k in range(nseg):
            za = z0 + k * sl + 0.015
            zb = za + sl - 0.03
            d.extend(K.chbox((x - 0.03, 0.62, za), (x + 0.03, 1.05, zb), 'armour', 0.012, faces=None))
            d.extend(K.chbox((x + s * 0.03 - 0.008, 0.7, za + 0.05), (x + s * 0.03 + 0.008, 0.98, zb - 0.05), 'armour_dark', 0.004))
        d.extend(K.chbox((x - 0.02, 0.35, z1 - 0.05), (x + 0.02, 1.0, z1 + 0.35), 'skirt_rubber', 0.006))
        d.extend(K.chbox((x - 0.02, 0.4, z0 - 0.3), (x + 0.02, 1.0, z0 + 0.05), 'skirt_rubber', 0.006))
    # Engine deck: grilles and exhaust, tow cables, the rear plate with its lights.
    for s in (-1, 1):
        d.extend(MT.louvres(min(s * 0.15, s * 1.4), max(s * 0.15, s * 1.4), deck, rear + 0.3, rear + 1.9))
        d.extend(K.tow_cable([(s * (hwu - 0.12), deck + 0.02, rear + 0.4), (s * (hwu - 0.12), deck + 0.03, 0.0), (s * (hwu - 0.12), deck + 0.03, g0 - 0.2)]))
        d.extend(K.chbox((s * 1.55 - 0.1, 1.15, rear - 0.03), (s * 1.55 + 0.1, 1.3, rear + 0.04), 'armour', 0.01))
        d.extend(panel((s * 1.55, 1.22, rear - 0.032), 0.14, 0.07, (-1, 0, 0), (0, 1, 0), 'taillight'))
    d.extend(K.chbox((-0.5, 1.0, rear - 0.12), (0.5, 1.45, rear + 0.02), 'armour', 0.02))  # rear plate with the towing coupling
    d.extend(cyl((0, 1.2, rear - 0.12), (0, 1.2, rear - 0.2), 0.05, 'metal_dark', n=8))
    # Stowage boxes and rails on the sponsons.
    for s in (-1, 1):
        sx0, sx1 = s * (hwu - 0.08), s * (hwu - 0.5)
        lo, hi = min(sx0, sx1), max(sx0, sx1)
        d.extend(K.chbox((lo, deck - 0.01, -1.6), (hi, deck + 0.14, -0.3), 'armour', 0.02))
        d.extend(K.chbox((lo, deck - 0.01, 0.2), (hi, deck + 0.1, 1.2), 'armour', 0.02))
    body.add(d)
    MT.running_gear(m, bm, MT.Gear(rollers=[(-2.4, 0.84, 0.09), (-1.0, 0.84, 0.09), (0.4, 0.84, 0.09), (1.8, 0.84, 0.09)], drive='rear', pitch=0.15,
                                     thick=0.07, wheel_kind='spoked', track_mat='track_rubber', n=16))

    # Turret
    tp = MT.mirror_part(m, bm, 'turret')
    tz = tp.pivot[2]
    top = cfg['top']
    T = Geo(50)
    T.extend(poly_turret(cfg['corners'], cfg['levels']))
    tp.add(T)
    t = Geo(30)
    s_ = lambda x: x
    # Front cheek armour modules (the wedge on the Leopard / Challenger, the Abrams' sloped faces): bolted plates.
    cs = np.array(cfg['corners'])
    for s in (-1, 1):
        a, b = (1.7, 0.05), (1.05, 0.95)
        # smoke dischargers on the front corners, angled outward
        zc = cs[1][1] - 0.15
        t.extend(K.smoke_launchers((s * (cs[1][0] + 0.03), top - 0.55, zc), 4 if kind != 'challenger2' else 5,
                                   (s * 0.8, 0, 0.6), spacing=0.14, r=0.05, length=0.4, elev=30, spread=6))
    # Roof: the commander's cupola on the right, the loader's hatch with his gun on the left, sights.
    t.extend(cupola((0.62, top - 0.04, tz - 0.55), 0.34, h=0.2))
    t.extend(K.mg((0.7, top + 0.32, tz - 0.55 + 0.1), (0, 0, 1), 1.05, shield=kind == 'abrams'))
    t.extend(K.hatch((-0.62, top - 0.04, tz - 0.78), 0.3, h=0.06))
    t.extend(cyl((-0.62, top + 0.02, tz - 0.78 + 0.05), (-0.62, top + 0.12, tz - 0.78 + 0.05), 0.04, 'metal_dark', n=6))
    t.extend(K.mg((-0.62, top + 0.2, tz - 0.78 + 0.05), (0, 0, 1), 1.0))
    # Gunner's sight: an armoured housing on the front right of the roof with the glass facing forward (CITV / TOGS box left).
    t.extend(K.chbox((0.35, top - 0.02, tz + 0.95), (0.95, top + 0.28, tz + 1.12), 'armour', 0.03))
    t.extend(panel((0.5, top + 0.15, tz + 1.123), 0.14, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(panel((0.8, top + 0.15, tz + 1.123), 0.14, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(K.chbox((-0.95, top - 0.02, tz + 0.85), (-0.4, top + 0.2, tz + 1.0), 'armour', 0.03))
    t.extend(panel((-0.675, top + 0.1, tz + 1.003), 0.3, 0.1, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(K.antenna((-1.25, top - 0.08, tz - 1.9), 2.5 if kind != 'challenger2' else 1.8))
    t.extend(K.antenna((1.25, top - 0.08, tz - 1.9), 2.2))
    # The bustle: a rack of stowage at the rear, boxes and a net frame.
    zr = cfg['corners'][8][1]
    for s in (-1, 1):
        t.extend(K.chbox((min(s * 0.1, s * 1.2), top - 0.5, zr - 0.04), (max(s * 0.1, s * 1.2), top - 0.1, zr + 0.06), 'armour', 0.02))
        t.extend(K.slats(min(s * 0.15, s * 1.25), max(s * 0.15, s * 1.25), top - 0.45, top - 0.1, zr - 0.12, 6, axis='x'))
    t.extend(K.chbox((-1.3, top - 0.08, zr + 0.2), (1.3, top - 0.02, zr + 1.2), 'armour', 0.01) if kind == 'abrams' else Geo(1))
    for x, z in ((-0.3, tz - 1.2), (0.0, tz + 0.2), (0.8, tz - 1.2)):
        t.extend(tube([(x - 0.05, top - 0.02, z), (x, top + 0.05, z), (x + 0.05, top - 0.02, z)], 0.012, 'metal_dark', n=4))
    if kind == 'leopard2':
        # The wedge's bolted side plates and the rectangular gun shield.
        for s in (-1, 1):
            t.extend(K.chbox((s * 1.72 - 0.02, 1.95, -2.1), (s * 1.72 + 0.02, 2.3, -1.0), 'armour_dark', 0.005))
    tp.add(t)

    # The gun
    gp = MT.mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = MT.muzzle_abs(bm)
    gg = Geo(40)
    r = 0.075
    z0 = piv[2] + 0.2
    L = mz[2] - z0
    # Mantlet: a wide gun shield round the breech end.
    if kind == 'leopard2':
        gg.extend(K.chbox((-0.6, piv[1] - 0.26, piv[2] - 0.2), (0.6, piv[1] + 0.26, piv[2] + 0.3), 'armour', 0.05))
    elif kind == 'abrams':
        gg.extend(K.chbox((-0.5, piv[1] - 0.22, piv[2] - 0.25), (0.5, piv[1] + 0.25, piv[2] + 0.28), 'armour', 0.06))
    else:
        gg.extend(K.chbox((-0.55, piv[1] - 0.25, piv[2] - 0.25), (0.55, piv[1] + 0.25, piv[2] + 0.3), 'armour', 0.05))
    sleeve = (L * 0.1, L * 0.7, r * 1.55) if kind != 'leopard2' else (L * 0.07, L * 0.78, r * 1.5)
    prof = gun_profile(L, r, sleeve=sleeve, evac=(L * 0.75, L * 0.83, r * 2.0) if kind == 'abrams' else None,
                       extractor=(L * 0.52, L * 0.62, r * 2.0) if kind != 'abrams' else None, muzzle='collar' if kind == 'abrams' else 'plain')
    gg.extend(K.barrel((0, piv[1], z0), (0, piv[1], mz[2]), prof))
    # The coaxial MG next to the barrel.
    gg.extend(cyl((0.2, piv[1] - 0.04, z0 - 0.3), (0.2, piv[1] - 0.04, z0 + 0.3), 0.025, 'metal_dark', n=6))
    gp.add(gg)
    return m
