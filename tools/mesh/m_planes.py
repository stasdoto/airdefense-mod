"""2.0: the airliners (airliner, turboprop) and the trains (emu_head, emu_car, loco, boxcar, tank_car, gondola) as meshes.
Planes: metres, wheels at y = 0, z forward. Trains: rail top at y = 0, front at +z."""
import math

import numpy as np

import geom
import materials
import kit_air as K
from core import Geo, Model
from kit_air import Body, rot_axis
from m_air import S
from m_jets import T, wing_line, nozzle, intake_lip

_reg = False


def register():
    global _reg
    if _reg:
        return
    _reg = True
    R = materials.register

    def liv(name, col, lines=False):
        R(name, lambda Tt, s, m, c=col: K.skin_fn(Tt, s, m, base=c, lines=lines, gloss=0.4))
    liv('a_liner_blue', 0x1F4E9C)
    liv('a_liner_red', 0xB02A24)
    liv('a_rail_green', 0x3C6C4C)
    liv('a_rail_red', 0xA02A26)
    liv('a_rail_yellow', 0xE2B82A)
    liv('a_loco_green', 0x3E7048)
    liv('a_wagon_brown', 0x6E4A38)
    liv('a_tank_black', 0x2A2C2E)
    liv('a_gondola', 0x686C70)
    R('a_coal', lambda Tt, s, m: (materials.plain(Tt, 0x1C1C1E, s, 0.2, 1.0, 0.25), np.full(len(Tt['pos']), 255.0)))
    R('a_ballast', lambda Tt, s, m: (materials.plain(Tt, 0x6A6862, s, 0.3, 1.0, 0.2), np.full(len(Tt['pos']), 255.0)))


register()


def plane_scheme(base, seed, stripes=None):
    return materials.Scheme(base=base, camo=None, dirt=0.2, wear=0.25, seed=seed, gloss=0.45, stripes=stripes)


def fuselage_windows(zs_pitch, z0, z1, w=0.42):
    """z stations for a row of windows: each window's start and end."""
    out = []
    z = z0
    while z + w <= z1:
        out += [round(z, 3), round(z + w, 3)]
        z += zs_pitch
    return out


# ---------------------------------------------------------------------------------------------------------------


def airliner():
    sch = plane_scheme(0xE8EAEC, 11)
    m = Model('airliner', sch, density=11, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.under = materials.hexc(0xB8BCC0)
    A.under_y = 3.2
    yc, r = 3.9, 1.98
    body = m.part('body')
    B = Body([S(-18.8, yc=5.15, hw=0.3, ht=0.3, pt=2, pb=2), S(-16.5, yc=4.8, hw=0.85, ht=0.9), S(-12.5, yc=4.25, hw=1.55, ht=1.58),
              S(-8.0, yc=yc, hw=r, ht=r), S(14.0, yc=yc, hw=r, ht=r), S(15.6, yc=3.88, hw=1.95, ht=1.95), S(17.2, yc=3.75, hw=1.6, ht=1.62),
              S(18.3, yc=3.6, hw=0.95, ht=0.95), S(18.8, yc=3.55, hw=0.2, ht=0.2)], n=28)
    wz = fuselage_windows(0.9, -9.5, 13.5)
    zs = B.zs(1.6, extra=wz + [16.2, 16.6, 17.0, 17.4, 17.6])
    win = set()
    for i in range(0, len(wz), 2):
        win.add((wz[i], wz[i + 1]))

    def mats(z, th, k, i):
        if k in (7, 20) and any(a - 1e-3 <= z <= b + 1e-3 for a, b in win):
            return 'a_window'
        if 16.2 < z < 17.6 and 0.3 < math.sin(th) < 0.8:
            return 'a_window'
        if k in (6, 21, 22) and -9.0 < z < 14.6:
            return 'a_liner_blue'
        return 'skin'
    body.add(B.skin(zs, 'skin', mats=mats, smooth=45.0, cap0=True))
    # The nose radome in grey, the door outlines, the blue tail cone.
    for z in (14.6, -15.2):
        for s in (-1, 1):
            A.lines.append((np.array([(s * (r + 0.004), 2.8, z - 0.45), (s * (r + 0.004), 4.75, z - 0.45), (s * (r + 0.004), 4.75, z + 0.45),
                                      (s * (r + 0.004), 2.8, z + 0.45), (s * (r + 0.004), 2.8, z - 0.45)]), 0.012, False))
    for z in np.arange(-12.0, 16.0, 1.4):
        A.lines.append((B.section_line(z), 0.012, True))
    # Wings with winglets; the engines on pylons.
    for s in (-1, 1):
        root = (s * 1.9, 2.45, 3.4)
        tip = (s * 17.0, 2.45 + 1.45, -5.6)
        body.add(K.wing(root, tip, 6.6, 1.6, 0.13, 0.09, steps=8, chord_dir=(0, 0, -1), twist=-2))
        A.lines.append((wing_line(root, tip, 6.6, 1.6, 0.13, 0.09, 0.78, 0.45, 0.95, k=10), 0.02, True))
        A.lines.append((wing_line(root, tip, 6.6, 1.6, 0.13, 0.09, 0.78, 0.08, 0.4, k=8), 0.02, True))
        A.lines.append((wing_line(root, tip, 6.6, 1.6, 0.13, 0.09, 0.15, 0.1, 0.95, k=10), 0.015, True))
        body.add(K.fin_surface((3.85, -6.0), (5.65, -6.9), 1.6, 0.8, t=0.1, x=s * 17.0, steps=3, side_tilt=s * 0.4, mat='a_liner_blue'))
        x = s * 5.7
        body.add(geom.lathe((x, 1.75, -0.8), (x, 1.75, 4.7),
                            [(0, 0.0, 'a_soot'), (0.0, 0.5, 'a_nozzle'), (0.4, 0.62, 'a_metal'), (1.0, 0.8, 'skin'), (2.2, 1.0, 'skin'), (4.4, 1.0, 'skin'),
                             (5.05, 0.92, 'skin'), (5.5, 0.85, 'skin'), (5.5, 0.78, 'a_intake'), (5.1, 0.78, 'a_intake'), (5.1, 0.0, 'a_intake')],
                            'skin', 24, 50))
        body.add(geom.cyl((x, 1.75, 4.6), (x, 1.75, 4.62), 0.2, 'a_metal', n=10))
        body.add(geom.lathe((x, 1.75, -0.6), (x, 1.75, 0.5), [(0, 0.0, 'a_metal'), (0, 0.55, 'a_metal'), (1.1, 0.0, 'a_metal')], 'a_metal', 12, 40))
        body.add(K.pylon(x, 2.75, 2.5, -0.4, 3.0, 'skin', w=0.2))
    # The tail: tailplane and fin (blue).
    for s in (-1, 1):
        body.add(K.wing((s * 0.6, 5.1, -14.4), (s * 6.4, 5.35, -17.6), 3.6, 1.4, 0.1, 0.08, steps=4, chord_dir=(0, 0, -1)))
    body.add(K.fin_surface((5.4, -12.6), (11.9, -17.6), 5.6, 2.0, t=0.1, steps=6, mat='a_liner_blue'))
    # The gear: the mains under the wings, the nose leg.
    for s in (-1, 1):
        x = s * 3.8
        body.add(K.strut((x * 0.9, 2.6, -1.2), (x, 0.8, -1.2), 0.1))
        for dx in (-0.25, 0.25):
            body.add(K.gear_wheel((x + dx, 0.58, -1.2), 0.58, 0.28, n=16))
        body.add(geom.rbox((x * 0.9 - 0.4, 2.3, -2.4), (x * 0.9 + 0.4, 2.7, 0.2), 'skin', r=0.1))
    body.add(K.strut((0, 2.8, 13.2), (0, 0.9, 13.2), 0.08))
    for dx in (-0.2, 0.2):
        body.add(K.gear_wheel((dx, 0.38, 13.2), 0.38, 0.2, n=14))
    # Lights.
    body.add(geom.sphere((0, yc + r + 0.05, -2.0), 0.15, 'a_red', n=8, rings=4, squash=0.5))
    body.add(geom.sphere((-17.0, 3.95, -5.0), 0.1, 'a_nav_red', n=6, rings=4))
    body.add(geom.sphere((17.0, 3.95, -5.0), 0.1, 'a_nav_green', n=6, rings=4))
    return m


def prop_blades(g, hub, radius, nb, chord, phase=0.0):
    x, y, z = hub
    for i in range(nb):
        bg = K.blade(0.3, radius, chord, 0.0, droop=0.0, steps=4, n=6, twist=-18, mat='a_blade')
        bg.transform(R=rot_axis((1, 0, 0), -90))
        bg.transform(R=rot_axis((0, 0, 1), phase + 360.0 * i / nb))
        bg.transform(t=hub)
        g.extend(bg)


def turboprop():
    sch = plane_scheme(0xE8EAEC, 12)
    m = Model('turboprop', sch, density=16, tex_width=1024)
    m.ground = False
    m.blade_tip = (230, 230, 225)
    m.air = A = K.Air()
    A.under = materials.hexc(0xB8BCC0)
    A.under_y = 2.0
    yc, r = 2.35, 1.38
    body = m.part('body')
    B = Body([S(-13.6, yc=3.2, hw=0.3, ht=0.35, pt=2, pb=2), S(-10.5, yc=2.8, hw=0.9, ht=0.95), S(-6.5, yc=yc, hw=r, ht=r),
              S(10.5, yc=yc, hw=r, ht=r), S(12.0, yc=2.3, hw=1.25, ht=1.3), S(13.1, yc=2.2, hw=0.8, ht=0.8), S(13.6, yc=2.15, hw=0.2, ht=0.2)], n=24)
    wz = fuselage_windows(0.8, -6.0, 10.0, 0.38)
    zs = B.zs(1.4, extra=wz + [11.8, 12.1, 12.5, 12.7])
    win = [(wz[i], wz[i + 1]) for i in range(0, len(wz), 2)]

    def mats(z, th, k, i):
        # The ring has 24 points (15 degrees): the bands just above the horizontals.
        if k in (6, 17) and any(a - 1e-3 <= z <= b + 1e-3 for a, b in win):
            return 'a_window'
        if 11.8 < z < 12.7 and 0.35 < math.sin(th) < 0.85:
            return 'a_window'
        if k in (5, 4, 18, 19) and -6.0 < z < 11.0:
            return 'a_liner_red'
        return 'skin'
    body.add(B.skin(zs, 'skin', mats=mats, smooth=45.0, cap0=True))
    for z in np.arange(-9.0, 12.0, 1.2):
        A.lines.append((B.section_line(z), 0.012, True))
    # The high wing, straight; the engines under it with the propellers.
    for s in (-1, 1):
        root = (s * 0.6, 3.85, 1.6)
        tip = (s * 13.5, 3.8, -0.6)
        body.add(K.wing(root, tip, 2.6, 1.5, 0.15, 0.11, steps=6, chord_dir=(0, 0, -1)))
        A.lines.append((wing_line(root, tip, 2.6, 1.5, 0.15, 0.11, 0.75, 0.3, 0.95), 0.015, True))
        x = s * 4.1
        Nn = Body([T(-2.6, 3.7, 3.4, 0.3, x0=x), T(-1.2, 3.85, 3.0, 0.55), T(3.0, 3.9, 2.95, 0.62), T(3.6, 3.8, 3.05, 0.5), T(3.95, 3.7, 3.2, 0.3)], n=16)
        body.add(Nn.skin(Nn.zs(0.7), 'skin', cap0=True, cap1=True))
        body.add(geom.rbox((x - 0.18, 3.1, 3.55), (x + 0.18, 3.6, 3.9), 'a_intake', r=0.05))
        # The propeller (its own part so the game can turn it): spinner, hub, six blades.
        pr = body.part('propeller_l' if s < 0 else 'propeller_r', (x, 3.4, 4.0))
        pg = Geo(45)
        pg.extend(geom.lathe((x, 3.4, 3.95), (x, 3.4, 4.6), [(0, 0.0, 'a_hub'), (0, 0.34, 'a_hub'), (0.2, 0.36, 'a_hub'), (0.45, 0.2, 'a_hub'), (0.65, 0.0, 'a_hub')],
                             'a_hub', 14, 50))
        prop_blades(pg, (x, 3.4, 4.1), 1.95, 6, 0.3)
        pr.add(pg)
        # The main gear in pods on the fuselage's sides.
        px = s * 1.55
        body.add(geom.rbox((s * 1.3 - 0.5 * (s < 0), 0.75, -1.5), (s * 1.3 + 0.5 * (s > 0), 1.65, 1.0), 'skin', r=0.25))
        body.add(K.strut((px * 0.95, 1.0, -0.3), (px, 0.55, -0.3), 0.07))
        body.add(K.gear_wheel((px, 0.45, -0.3), 0.45, 0.25, n=14))
    # The T-tail.
    body.add(K.fin_surface((3.3, -8.2), (7.5, -12.6), 4.0, 2.2, t=0.1, steps=5, mat='a_liner_red'))
    for s in (-1, 1):
        body.add(K.wing((s * 0.2, 7.45, -11.0), (s * 3.8, 7.45, -12.4), 2.0, 1.1, 0.09, 0.08, steps=3, chord_dir=(0, 0, -1)))
    body.add(K.strut((0, 1.0, 11.0), (0, 0.4, 11.0), 0.06))
    body.add(K.gear_wheel((0, 0.32, 11.0), 0.32, 0.2, n=12))
    body.add(geom.sphere((0, yc + r + 0.05, 0.2), 0.12, 'a_red', n=8, rings=4, squash=0.5))
    return m


# ---------------------------------------------------------------------------------------------------------------
# Trains


def rail_scheme(base, seed, stripes=None, dirt=0.4):
    return materials.Scheme(base=base, camo=None, dirt=dirt, wear=0.35, seed=seed, gloss=0.35, stripes=stripes)


def wheelset(g, z, half=0.75, r=0.48):
    """An axle with two flanged steel wheels at the gauge, the axle box on each end."""
    for s in (-1, 1):
        x = s * half
        g.extend(geom.lathe((x - 0.06, r, z) if False else (x - 0.07, r, z), (x + 0.07, r, z),
                            [(0, 0.0, 'a_steel'), (0, r - 0.08, 'a_steel'), (0.02, r, 'a_steel'), (0.1, r, 'a_steel'), (0.12, r + 0.07, 'a_steel'),
                             (0.14, r + 0.07, 'a_steel'), (0.14, r * 0.3, 'a_steel'), (0.14, 0.0, 'a_steel')], 'a_steel', 18, 40) if False else
                 geom.cyl((x - 0.07, r, z), (x + 0.07, r, z), r, 'a_steel', n=20, bevel=0.03))
        g.extend(geom.cyl((x - 0.01 * s, r, z) if False else (x + s * 0.07, r, z), (x + s * 0.12, r, z), r * 0.3, 'a_bogie', n=10))
        g.extend(geom.rbox((x + s * 0.12 - 0.08, r - 0.15, z - 0.18), (x + s * 0.12 + 0.08, r + 0.15, z + 0.18), 'a_bogie', r=0.03))
    g.extend(geom.cyl((-half, r, z), (half, r, z), 0.06, 'a_steel', n=8))


def bogie(body, z, axles=2, half=1.1):
    g = Geo(45)
    span = 2.4 if axles == 2 else 3.8
    for k in range(axles):
        zz = z - span / 2 + span * k / max(1, axles - 1)
        wheelset(g, zz)
    # The frame: side beams, transoms, the springs and the pivot.
    for s in (-1, 1):
        g.extend(geom.rbox((s * 1.0 - 0.07, 0.65, z - span / 2 - 0.45), (s * 1.0 + 0.07, 0.95, z + span / 2 + 0.45), 'a_bogie', r=0.04))
        for k in range(axles):
            zz = z - span / 2 + span * k / max(1, axles - 1)
            g.extend(geom.cyl((s * 0.95, 0.62, zz), (s * 0.95, 0.9, zz), 0.1, 'a_steel', n=8))
    for dz in (-span / 2 - 0.3, span / 2 + 0.3):
        g.extend(geom.rbox((-1.0, 0.7, z + dz - 0.06), (1.0, 0.9, z + dz + 0.06), 'a_bogie', r=0.03))
    g.extend(geom.rbox((-0.5, 0.75, z - 0.4), (0.5, 1.05, z + 0.4), 'a_bogie', r=0.06))
    body.add(g)


def coupler(body, z, sign):
    g = Geo(45)
    z0, z1 = (z, z + 0.65 * sign)
    g.extend(geom.rbox((-0.14, 0.9, min(z0, z1)), (0.14, 1.12, max(z0, z1)), 'a_bogie', r=0.04))
    g.extend(geom.rbox((-0.3, 0.88, z1 - 0.08 * sign), (0.3, 1.14, z1 + 0.08 * sign), 'a_dark', r=0.04) if False else geom.rbox((-0.3, 0.88, min(z1, z1 + 0.18 * sign)), (0.3, 1.14, max(z1, z1 + 0.18 * sign)), 'a_dark', r=0.04))
    for s in (-1, 1):
        g.extend(geom.cyl((s * 0.85, 1.05, z), (s * 0.85, 1.05, z + 0.4 * sign), 0.09, 'a_steel', n=8))
        g.extend(geom.cyl((s * 0.85, 1.05, z + 0.4 * sign), (s * 0.85, 1.05, z + 0.46 * sign), 0.2, 'a_steel', n=10))
    body.add(g)


def car_body(L, hw=1.62, y0=1.2, y1=4.0, pt=3.2, pb=8.0, n=32, front=None, back=None):
    """The shell of a rail car: a rounded box, optionally with a raked cab front: front = [(dz, top, bot, hw)] stations."""
    yc = (y0 + y1) / 2
    ht = (y1 - y0) / 2
    rows = [T(-L / 2, y1, y0, hw, pt=pt, pb=pb), T(-L / 2 + 0.05, y1, y0, hw, pt=pt, pb=pb)]
    z1 = L / 2
    if front:
        rows.append(T(front[0][0] - 0.01, y1, y0, hw, pt=pt, pb=pb))
        for z, top, bot, w in front:
            rows.append(T(z, top, bot, w, pt=pt, pb=pb))
    else:
        rows.append(T(z1 - 0.05, y1, y0, hw, pt=pt, pb=pb))
        rows.append(T(z1, y1, y0, hw, pt=pt, pb=pb))
    return Body(rows, n=n)


def end_cap(body, B, z, sgn, mat='skin'):
    cap = geom.polygon_cap(B.ring(z), mat)
    from kit_air import orient
    orient(cap, lambda c: np.array([0, 0, float(sgn)]))
    body.add(cap)


def side_windows(body, hw, z0, z1, y0, y1, step, width, skip=(), mat='a_window'):
    g = Geo(30)
    z = z0
    k = 0
    while z + width <= z1:
        if k not in skip:
            for s in (-1, 1):
                g.extend(geom.panel((s * (hw + 0.012), (y0 + y1) / 2, z + width / 2), width, y1 - y0, (0, 0, -s), (0, 1, 0), mat))
        z += step
        k += 1
    body.add(g)


def door(body, hw, zc, y0, y1, w=1.3):
    g = Geo(30)
    for s in (-1, 1):
        g.extend(geom.panel((s * (hw + 0.012), (y0 + y1) / 2, zc), w, y1 - y0, (0, 0, -s), (0, 1, 0), 'a_darkgrey'))
        for dz in (-0.33, 0.33):
            g.extend(geom.panel((s * (hw + 0.016), y0 + (y1 - y0) * 0.68, zc + dz), 0.52, (y1 - y0) * 0.42, (0, 0, -s), (0, 1, 0), 'a_window'))
    body.add(g)


def emu(cab):
    sch = rail_scheme(0x3C6C4C, 41, stripes=[(1.55, 1.72, 0xA02A26), (3.45, 3.56, 0xE2B82A)])
    m = Model('emu_head' if cab else 'emu_car', sch, density=16, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.dirt_low = True
    L = 20.0
    hw = 1.62
    body = m.part('body')
    if cab:
        zf = L / 2
        front = [(8.2, 4.0, 1.2, hw), (8.7, 3.95, 1.25, hw * 0.99), (9.3, 3.7, 1.35, hw * 0.96), (9.75, 3.2, 1.55, hw * 0.9), (10.0, 2.7, 1.8, hw * 0.8)]
        B = car_body(L, hw=hw, front=front, pt=3.4)
    else:
        B = car_body(L, hw=hw, pt=3.4)
    zs = B.zs(1.6, extra=[8.2, 8.7, 9.3, 9.75] if cab else [])

    def mats(z, th, k, i):
        if cab and z > 8.7 and math.sin(th) > 0.12:
            return 'a_window' if math.sin(th) > 0.4 and math.sin(th) < 0.82 else 'a_rail_yellow'
        if cab and z > 8.7:
            return 'a_rail_yellow'
        return 'skin'
    body.add(B.skin(zs, 'skin', mats=mats, smooth=40.0))
    end_cap(body, B, zs[0], -1)
    end_cap(body, B, zs[-1], 1, 'a_rail_yellow' if cab else 'skin')
    z0 = -L / 2
    ze = 8.2 if cab else L / 2
    # Windows between the doors, doors near the ends.
    side_windows(body, hw, z0 + 3.1, ze - 2.5, 2.35, 3.25, 1.55, 1.1)
    for zc in (z0 + 1.6, ze - 1.8):
        door(body, hw, zc, 1.3, 3.4)
    # The underframe, gangway, couplers, bogies.
    body.add(geom.rbox((-1.3, 0.95, z0 + 3), (1.3, 1.25, L / 2 - 3), 'a_bogie', r=0.04))
    body.add(geom.rbox((-0.6, 1.3, z0 - 0.15), (0.6, 3.5, z0 + 0.05), 'a_dark', r=0.06))
    coupler(body, z0, -1)
    if cab:
        coupler(body, L / 2 - 0.3, 1)
        for s in (-1, 1):
            body.add(geom.rbox((s * 1.1 - 0.14, 1.95, 9.55), (s * 1.1 + 0.14, 2.12, 9.7), 'a_lamp', r=0.03))
            body.add(geom.rbox((s * 1.1 - 0.1, 1.4, 9.7), (s * 1.1 + 0.1, 1.52, 9.8), 'a_red', r=0.03))
        body.add(geom.rbox((-0.2, 3.3, 9.2), (0.2, 3.5, 9.35), 'a_lamp', r=0.03))
        # Wipers and the roof: a horn.
        body.add(geom.rbox((-0.5, 4.0, 5.0), (0.5, 4.25, 7.0), 'a_lightgrey', r=0.08))
    else:
        body.add(geom.rbox((-0.6, 1.3, L / 2 - 0.05), (0.6, 3.5, L / 2 + 0.05), 'a_dark', r=0.06))
        coupler(body, L / 2, 1)
    bogie(body, z0 + 3.0)
    bogie(body, L / 2 - 3.0 - (0.2 if cab else 0))
    # The roof: ventilators and, on the middle car, the pantograph.
    for z in (-6.0, -2.0, 2.0, 6.0):
        body.add(geom.rbox((-0.5, 4.0, z - 0.6), (0.5, 4.22, z + 0.6), 'a_lightgrey', r=0.07))
    if not cab:
        g = Geo(45)
        g.extend(geom.rbox((-0.9, 4.0, -1.6), (0.9, 4.15, 1.6), 'a_bogie', r=0.03))
        for s in (-1, 1):
            g.extend(geom.cyl((s * 0.8, 4.15, -1.4), (s * 0.8, 4.35, -1.4), 0.07, 'a_white', n=8))
            g.extend(geom.tube([(s * 0.75, 4.35, -1.4), (s * 0.35, 5.2, -0.6)], 0.04, 'a_steel', n=6))
            g.extend(geom.tube([(s * 0.75, 4.35, 1.4), (s * 0.35, 5.2, 0.6)], 0.04, 'a_steel', n=6))
        g.extend(geom.rbox((-0.9, 5.15, -0.7), (0.9, 5.28, 0.7), 'a_steel', r=0.03))
        g.extend(geom.tube([(-0.9, 5.28, -0.6), (0.9, 5.28, -0.6)], 0.02, 'a_dark', n=4))
        g.extend(geom.tube([(-0.9, 5.28, 0.6), (0.9, 5.28, 0.6)], 0.02, 'a_dark', n=4))
        body.add(g)
    for z in np.arange(-9.0, 9.0, 3.0):
        A.lines.append((B.section_line(z), 0.012, True))
    return m


def loco():
    sch = rail_scheme(0x3E7048, 43, stripes=[(1.3, 1.9, 0xA02A26), (1.9, 2.0, 0xE2B82A)])
    m = Model('loco', sch, density=16, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.dirt_low = True
    hw, L = 1.6, 18.0
    z0, zf = -L / 2, L / 2
    body = m.part('body')
    front = [(zf - 1.0, 4.2, 1.3, hw), (zf - 0.7, 4.2, 1.3, hw), (zf - 0.3, 4.0, 1.3, hw * 0.98), (zf, 3.7, 1.35, hw * 0.96)]
    B = car_body(L, hw=hw, y0=1.3, y1=4.2, pt=4.5, front=front, n=32)
    zs = B.zs(1.6, extra=[zf - 1.0, zf - 0.7, zf - 0.3])

    def mats(z, th, k, i):
        if z > zf - 0.7 and math.sin(th) > 0.25 and math.sin(th) < 0.8:
            return 'a_window'
        return 'skin'
    body.add(B.skin(zs, 'skin', mats=mats, smooth=40.0))
    end_cap(body, B, zs[0], -1)
    end_cap(body, B, zs[-1], 1)
    # The roof: a lower hood top with fans, the cab roof.
    body.add(geom.rbox((-hw + 0.4, 4.2, z0 + 0.5), (hw - 0.4, 4.42, zf - 3.5), 'a_midgrey', r=0.08))
    for z in (-6.0, -3.5):
        body.add(geom.cyl((0, 4.42, z + 0.9), (0, 4.58, z + 0.9), 0.85, 'a_mesh', n=16))
        body.add(geom.cyl((0, 4.42, z + 0.9), (0, 4.5, z + 0.9), 0.95, 'a_darkgrey', n=16))
    body.add(geom.rbox((-0.8, 4.42, 0.8), (0.8, 4.8, 2.0), 'a_darkgrey', r=0.1))
    for s in (-1, 1):
        for z in (-6.5, -3.0, 0.5):
            body.add(geom.panel((s * (hw + 0.012), 3.15, z + 1.3), 2.6, 1.1, (0, 0, -s), (0, 1, 0), 'a_mesh'))
        body.add(geom.panel((s * (hw + 0.012), 3.15, zf - 1.95), 1.3, 0.7, (0, 0, -s), (0, 1, 0), 'a_window'))
        body.add(geom.rbox((s * 1.1 - 0.15, 2.05, zf - 0.04), (s * 1.1 + 0.15, 2.3, zf + 0.02), 'a_lamp', r=0.03))
        body.add(geom.rbox((s * 1.1 - 0.1, 1.45, zf - 0.04), (s * 1.1 + 0.1, 1.6, zf + 0.02), 'a_red', r=0.03))
    body.add(geom.rbox((-0.25, 3.7, zf - 0.5), (0.25, 3.95, zf - 0.4), 'a_lamp', r=0.04))
    # Handrails, fuel tank, sandboxes, exhaust.
    for s in (-1, 1):
        body.add(geom.tube([(s * (hw + 0.15), 2.2, z0 + 2.0), (s * (hw + 0.15), 2.2, zf - 3.0)], 0.025, 'a_metal', n=4, caps=False))
    body.add(geom.rbox((-1.4, 0.95, z0 + 4.5), (1.4, 1.3, zf - 4.5), 'a_bogie', r=0.1))
    coupler(body, zf, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 4.0, axles=3)
    bogie(body, zf - 4.0, axles=3)
    for z in np.arange(-8.0, 8.0, 3.0):
        A.lines.append((B.section_line(z), 0.012, True))
    return m


def boxcar():
    sch = rail_scheme(0x6E4A38, 44, dirt=0.6)
    m = Model('boxcar', sch, density=16, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.dirt_low = True
    hw, L = 1.55, 14.0
    z0, z1 = -L / 2, L / 2
    body = m.part('body')
    body.add(geom.rbox((-hw, 1.2, z0), (hw, 3.9, z1), 'skin', r=0.04))
    body.add(geom.rbox((-hw + 0.1, 3.9, z0), (hw - 0.1, 4.15, z1), 'a_rust', r=0.1))
    z = z0 + 0.4
    g = Geo(45)
    while z < z1 - 0.2:
        for s in (-1, 1):
            g.extend(geom.box((s * (hw + 0.03) - 0.03, 1.2, z), (s * (hw + 0.03) + 0.03, 3.9, z + 0.12), 'a_rust'))
        z += 1.3
    for s in (-1, 1):
        # The sliding door with its rail and handle.
        g.extend(geom.rbox((s * hw - 0.02 + (0.0 if s > 0 else -0.08), 1.3, -1.0), (s * hw + 0.08 + (0.0 if s > 0 else -0.1) + (0.0), 3.7, 1.0), 'skin', r=0.02))
        g.extend(geom.box((s * (hw + 0.1) - 0.03, 3.72, -1.6), (s * (hw + 0.1) + 0.03, 3.8, 1.6), 'a_dark'))
        g.extend(geom.box((s * (hw + 0.1) - 0.03, 1.2, -1.2), (s * (hw + 0.1) + 0.03, 1.28, 1.2), 'a_dark'))
        g.extend(geom.cyl((s * (hw + 0.12), 2.4, 0.7), (s * (hw + 0.12), 2.4, 0.9), 0.04, 'a_steel', n=6))
    body.add(g)
    body.add(geom.rbox((-1.3, 0.95, z0 + 1), (1.3, 1.2, z1 - 1), 'a_bogie', r=0.04))
    # The roof's ridge and the brake wheel at one end.
    body.add(geom.cyl((0.7, 4.15, z0 + 0.5), (0.7, 4.85, z0 + 0.5), 0.03, 'a_dark', n=6))
    body.add(geom.cyl((0.7, 4.85, z0 + 0.5), (0.7, 4.9, z0 + 0.5), 0.32, 'a_dark', n=12))
    coupler(body, z1, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 2.3)
    bogie(body, z1 - 2.3)
    for z in np.arange(z0 + 1.3, z1, 1.3):
        A.lines.append((np.array([(-hw - 0.004, 1.3, z), (-hw - 0.004, 3.9, z)]), 0.012, False))
    return m


def tank_car():
    sch = rail_scheme(0x2A2C2E, 45, dirt=0.5)
    m = Model('tank_car', sch, density=16, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.dirt_low = True
    L = 12.0
    z0, z1 = -L / 2, L / 2
    body = m.part('body')
    c = (0, 2.65, 0)
    # The round tank with its domed ends, the dome and walkway on top, the saddles.
    body.add(geom.lathe((0, 2.65, z0 + 0.1), (0, 2.65, z1 - 0.1),
                        [(0, 0.0, 'skin'), (0.12, 0.75, 'skin'), (0.4, 1.2, 'skin'), (0.9, 1.4, 'skin'), (1.4, 1.4, 'skin'), (L - 0.2 - 1.4, 1.4, 'skin'),
                         (L - 0.2 - 0.9, 1.4, 'skin'), (L - 0.2 - 0.4, 1.2, 'skin'), (L - 0.2 - 0.12, 0.75, 'skin'), (L - 0.2, 0.0, 'skin')], 'skin', 28, 50))
    body.add(geom.cyl((0, 3.9, -0.45), (0, 4.3, -0.45), 0.5, 'skin', n=14, bevel=0.06) if False else geom.cyl((0, 3.9, 0.0), (0, 4.3, 0.0), 0.45, 'skin', n=14, bevel=0.06))
    body.add(geom.cyl((0, 4.3, 0.0), (0, 4.36, 0.0), 0.3, 'a_steel', n=12))
    body.add(geom.rbox((-0.3, 4.0, z0 + 0.6), (0.3, 4.05, z1 - 0.6), 'a_darkgrey', r=0.01))
    for s in (-1, 1):
        body.add(geom.tube([(s * 0.3, 4.05, z0 + 0.6), (s * 0.3, 4.4, z0 + 0.6), (s * 0.3, 4.4, z1 - 0.6), (s * 0.3, 4.05, z1 - 0.6)], 0.015, 'a_metal', n=4, caps=False))
    for z in (-3.5, 3.5):
        body.add(geom.rbox((-1.2, 1.3, z - 0.3), (1.2, 1.85, z + 0.3), 'a_bogie', r=0.05))
    # The orange hazard band, the ladders, the frame and bogies.
    body.add(geom.lathe((0, 2.65, -2.0), (0, 2.65, 2.0), [(0, 1.405, 'a_orange'), (4.0, 1.405, 'a_orange')], 'a_orange', 28, 50))
    for s in (-1, 1):
        body.add(geom.tube([(s * 1.45, 1.4, z0 + 1.0), (s * 1.45, 3.7, z0 + 1.0)], 0.03, 'a_metal', n=4, caps=False))
        body.add(geom.tube([(s * 1.45, 1.4, z0 + 1.5), (s * 1.45, 3.7, z0 + 1.5)], 0.03, 'a_metal', n=4, caps=False))
        for y in np.arange(1.7, 3.7, 0.35):
            body.add(geom.tube([(s * 1.45, y, z0 + 1.0), (s * 1.45, y, z0 + 1.5)], 0.02, 'a_metal', n=4, caps=False))
    body.add(geom.rbox((-1.4, 1.0, z0), (1.4, 1.3, z1), 'a_bogie', r=0.04))
    coupler(body, z1, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 1.9)
    bogie(body, z1 - 1.9)
    return m


def gondola():
    sch = rail_scheme(0x686C70, 46, dirt=0.55)
    m = Model('gondola', sch, density=16, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.dirt_low = True
    hw, L = 1.55, 13.0
    z0, z1 = -L / 2, L / 2
    body = m.part('body')
    body.add(geom.rbox((-hw, 1.2, z0), (hw, 1.4, z1), 'a_bogie', r=0.03))
    for s in (-1, 1):
        body.add(geom.rbox((s * hw - 0.06, 1.4, z0), (s * hw + 0.06, 3.3, z1), 'skin', r=0.02))
        body.add(geom.rbox((s * hw - 0.1, 3.25, z0), (s * hw + 0.1, 3.38, z1), 'a_rust', r=0.03))
        z = z0 + 0.8
        while z < z1 - 0.3:
            body.add(geom.box((s * (hw + 0.07) - 0.04, 1.4, z), (s * (hw + 0.07) + 0.04, 3.3, z + 0.14), 'a_rust'))
            z += 1.6
    for z in (z0, z1 - 0.12):
        body.add(geom.rbox((-hw, 1.4, z), (hw, 3.3, z + 0.12), 'skin', r=0.02))
    # The coal heaped up inside.
    body.add(geom.rbox((-hw + 0.1, 1.4, z0 + 0.12), (hw - 0.1, 3.0, z1 - 0.12), 'a_coal', r=0.02))
    rng = np.random.RandomState(7)
    for k in range(9):
        z = z0 + 1.2 + k * (L - 2.4) / 8
        body.add(geom.sphere((rng.uniform(-0.4, 0.4), 3.0, z), 0.95 + rng.uniform(-0.1, 0.15), 'a_coal', n=10, rings=6, squash=0.5))
    body.add(geom.rbox((-1.3, 0.95, z0 + 1), (1.3, 1.2, z1 - 1), 'a_bogie', r=0.04))
    coupler(body, z1, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 2.2)
    bogie(body, z1 - 2.2)
    return m


def models():
    return [airliner(), turboprop(), emu(True), emu(False), loco(), boxcar(), tank_car(), gondola()]
