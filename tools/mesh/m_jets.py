"""2.0: the Su-25, F-16 and A-10 as meshes (see m_air.py / kit_air.py for the shared pieces)."""
import math

import numpy as np

import geom
import materials
import kit_air as K
from core import Geo, Model
from kit_air import Body
from m_air import S, wheel_part, cockpit_skin, bubble, star_img


def T(z, top, bot, hw, **kw):
    """A station given by its top and bottom heights."""
    d = dict(z=z, yc=(top + bot) / 2, ht=(top - bot) / 2, hb=(top - bot) / 2, hw=hw)
    d.update(kw)
    return d


def wing_y(c, t, camber=0.015):
    yt = 5 * t * (0.2969 * math.sqrt(c) - 0.126 * c - 0.3516 * c * c + 0.2843 * c ** 3 - 0.1036 * c ** 4)
    return yt + camber * 4 * c * (1 - c)


def wing_line(root, tip, cr, ct, tr, tt, frac, s0=0.0, s1=1.0, top=True, k=10):
    """A chordwise fraction line along a wing (aileron, flap hinge lines), a hair above the skin."""
    root = np.asarray(root, dtype=np.float64)
    tip = np.asarray(tip, dtype=np.float64)
    pts = []
    for s in np.linspace(s0, s1, k):
        le = root + (tip - root) * s
        ch = cr + (ct - cr) * s
        t = tr + (tt - tr) * s
        y = wing_y(frac, t, 0.015) * ch
        if not top:
            y = -(wing_y(frac, t, 0.0) - 0.015 * 4 * frac * (1 - frac)) * ch + 0.015 * 4 * frac * (1 - frac) * ch
        pts.append(le + np.array([0, (y + 0.006) if top else (y - 0.006), -frac * ch]))
    return np.array(pts)


def nozzle(x, y, z_tail, length, r_out, r_in, mat='skin'):
    """A jet pipe along +z ending at z_tail: petals of the nozzle, a dark recessed exhaust inside."""
    a = (x, y, z_tail)
    b = (x, y, z_tail + length)
    prof = [(0.3, 0.0, 'a_soot'), (0.3, r_in, 'a_nozzle'), (0.0, r_in, 'a_nozzle'), (0.0, r_out, 'a_nozzle'), (length * 0.35, r_out * 1.04, 'a_nozzle'),
            (length * 0.55, r_out * 1.12, mat), (length, r_out * 1.12, mat)]
    return geom.lathe(a, b, prof, mat, 20, 50)


def intake_lip(x, y, z, r, depth=0.5, mat='skin'):
    prof = [(0, r * 1.0, mat), (0, r * 0.86, 'a_intake'), (-depth, r * 0.86, 'a_intake'), (-depth, 0, 'a_intake')]
    return geom.lathe((x, y, z), (x, y, z + 1), prof, mat, 20, 50)


def gear_leg(m, body, top, wheel_c, r, width, name, trail=0.0, twin=False):
    top = np.asarray(top, dtype=np.float64)
    body.add(K.strut(top, (wheel_c[0], wheel_c[1] + r * 0.9, wheel_c[2]), 0.06))
    wheel_part(m, name, wheel_c, r, width)


def jet_cockpit(body, seat_c, panel_z, panel_hw, panel_y, western, ejection=True):
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    x, y, z = seat_c
    g.extend(K.seat((x, y, z), 0.48, ejection=ejection))
    g.extend(K.stick((x, y - 0.35, z + 0.5)))
    g.extend(K.instrument_panel(panel_z, panel_y, panel_y + 0.4, panel_hw))
    g.extend(geom.rbox((x - 0.3, y - 0.5, z + 0.35), (x + 0.3, y - 0.2, panel_z + 0.05), 'a_coaming', r=0.04))
    ck.add(g)


def stores(body, kind, x, y, z, s):
    if kind == 'pod':
        body.add(K.rocket_pod((x, y, z), 2.5, 0.52, 'a_pod'))
    elif kind == 'bomb':
        body.add(K.bomb((x, y, z), 2.0, 0.42, 'a_bomb'))
    elif kind == 'aam':
        body.add(K.missile((x, y, z), 2.1, 0.13, mat='a_white', nose='a_darkgrey', fins=(0.9, 1.1), canards=False))


# ---------------------------------------------------------------------------------------------------------------


def su25():
    sch = materials.Scheme(base=0x7C8256, camo=[0xA39A72, 0x4D5A3A], blob=2.8, stretch=2.2, slant=0.1, dirt=0.35, wear=0.4, seed=25, gloss=0.2)
    sch.thr = 0.55
    m = Model('su25', sch, density=18, tex_width=1024)
    m.ground = False
    m.blade_tip = (214, 176, 40)
    m.air = A = K.Air()
    A.under = materials.hexc(0xAAB6BA)
    A.under_y = 2.0
    body = m.part('body')
    B = Body([T(-7.85, 2.58, 2.12, 0.1, pt=2, pb=2), T(-7.6, 2.58, 2.0, 0.16), T(-6.4, 2.6, 1.8, 0.32), T(-4.0, 2.72, 1.5, 0.5),
              T(-1.2, 2.92, 1.3, 0.6), T(0.4, 3.06, 1.25, 0.6, pt=2.3), T(2.0, 3.05, 1.2, 0.6, pt=2.4),
              T(3.2, 2.78, 1.22, 0.66, pt=2.4), T(4.5, 2.7, 1.3, 0.68), T(5.3, 2.62, 1.35, 0.62, pt=2.2, pb=2.4),
              T(6.4, 2.45, 1.5, 0.5, pt=2, pb=2.2), T(7.3, 2.28, 1.62, 0.3), T(7.78, 2.1, 1.9, 0.12), T(7.88, 2.02, 1.96, 0.02)], n=28)
    holes = [(2.55, 5.0)]
    zs = B.zs(0.5, extra=[2.55, 3.0, 3.05, 3.9, 3.95, 4.4, 5.0, 5.45, 6.6])

    def nose(z, th, k, i):
        if z > 7.3:
            return 'a_radome'
        return None
    skin, lining = cockpit_skin(B, zs, holes, 10, 18, glass_fn=nose)
    body.add(skin)
    body.add(lining)
    body.add(bubble(B, zs, 2.55, 5.0, 10, 18, 0.5, frames=[(3.0, 3.06), (3.9, 3.96), (4.4, 4.46)], front=0.3, back=0.2))
    # The nose: the laser ranger window in the tip, the pitot probe.
    body.add(geom.sphere((0, 2.0, 7.85), 0.06, 'a_sensor', n=8, rings=5))
    body.add(geom.tube([(0.0, 2.36, 7.35), (0.0, 2.3, 8.0)], 0.02, 'a_metal', n=5))
    # The engine nacelles along the fuselage: intakes at the front with splitter plates, the nozzles at the back.
    for s in (-1, 1):
        N = Body([T(2.4, 2.42, 1.4, 0.2, x0=s * 0.9), T(2.95, 2.5, 1.35, 0.46, x0=s * 1.0), T(3.5, 2.55, 1.35, 0.55),
                  T(-3.0, 2.55, 1.38, 0.55), T(-5.4, 2.45, 1.5, 0.45)], n=18)
        body.add(N.skin(N.zs(0.6), 'skin'))
        body.add(intake_lip(s * 1.0, 1.95, 2.97, 0.46, 0.45))
        body.add(nozzle(s * 1.0, 1.95, -6.2, 1.2, 0.36, 0.3))
        A.stains.append(((s * 1.0, 2.0, -5.8), (s * 1.1, 2.2, -2.0), 0.35, 0.7, (28, 26, 24), 0.8, True))
    # Wings: a straight shoulder wing with a little sweep, the tip airbrake pods; the tailplane and the tall fin.
    for s in (-1, 1):
        r = (s * 1.45, 2.45, 1.7)
        t = (s * 7.2, 2.35, 0.2)
        body.add(K.wing(r, t, 3.3, 1.5, 0.13, 0.1, steps=7, chord_dir=(0, 0, -1)))
        A.lines.append((wing_line(r, t, 3.3, 1.5, 0.13, 0.1, 0.72, 0.55, 0.96), 0.012, True))
        A.lines.append((wing_line(r, t, 3.3, 1.5, 0.13, 0.1, 0.72, 0.1, 0.5), 0.012, True))
        A.lines.append((wing_line(r, t, 3.3, 1.5, 0.13, 0.1, 0.1, 0.1, 0.95), 0.01, True))
        body.add(K.wing((s * 0.35, 2.75, -4.7), (s * 2.9, 2.75, -5.5), 1.7, 0.75, 0.09, 0.07, steps=3, chord_dir=(0, 0, -1)))
        # The tip airbrake: a pod split in the middle.
        c = (s * 7.3, 2.32, -0.5)
        body.add(geom.lathe((c[0], c[1], c[2] - 0.8), (c[0], c[1], c[2] + 0.9), [(0, 0.0, 'skin'), (0.15, 0.17, 'skin'), (0.6, 0.25, 'skin'), (1.3, 0.24, 'skin'),
                                                                               (1.7, 0.1, 'a_dark'), (1.7, 0.0, 'a_dark')], 'skin', 12, 50))
        # Pylons and stores.
        for x, kind in ((2.3, 'pod'), (3.3, 'bomb'), (4.3, 'pod'), (5.3, 'bomb'), (6.3, 'aam')):
            yw = 2.45 - (x - 1.45) / 5.75 * 0.1 - 0.12
            body.add(K.pylon(s * x, yw + 0.05, 2.12, -0.6, 0.6, 'skin', w=0.09))
            stores(body, kind, s * x, 1.8, 0.25 if kind != 'aam' else 0.2, s)
    body.add(K.fin_surface((2.7, -4.9), (5.0, -6.9), 2.6, 1.0, t=0.1, steps=7))
    body.add(geom.rbox((-0.07, 4.8, -6.75), (0.07, 5.0, -6.95), 'a_darkgrey', r=0.03))
    body.add(geom.rbox((-0.1, 3.0, -7.4), (0.1, 3.3, -7.9), 'skin', r=0.06))
    # Landing gear: the nose leg (offset left), the mains in the wing roots.
    gear_leg(m, body, (-0.2, 1.4, 5.2), (-0.2, 0.33, 5.2), 0.33, 0.2, 'gear_nose')
    for s in (-1, 1):
        body.add(geom.rbox((s * 1.05 - 0.28, 1.15, -1.3), (s * 1.05 + 0.28, 1.6, 0.1), 'skin', r=0.1))
        gear_leg(m, body, (s * 1.0, 1.4, -0.6), (s * 1.05, 0.45, -0.6), 0.45, 0.26, 'gear_l' if s < 0 else 'gear_r')
        body.add(geom.tube([(s * 0.9, 1.3, -0.1), (s * 1.05, 0.7, -0.6)], 0.035, 'a_gear', n=6))
    body.add(geom.sphere((-7.2, 2.4, -0.2), 0.04, 'a_nav_red', n=6, rings=4))
    body.add(geom.sphere((7.2, 2.4, -0.2), 0.04, 'a_nav_green', n=6, rings=4))
    jet_cockpit(body, (0, 1.98, 3.45), 4.55, 0.45, 2.15, False)
    for z in list(np.arange(-4.5, 2.5, 0.7)):
        A.lines.append((B.section_line(z), 0.014, True))
    for th in (-math.pi / 2 - 0.6, -math.pi / 2 + 0.6, -math.pi * 1.5 + 0.8, -math.pi * 1.5 - 0.8):
        A.lines.append((B.long_line(th, -4.0, 6.5), 0.013, True))
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 0.5, 2.55, -2.2), (s, 0, 0), 1.3, 0.55, '08', fg=(222, 60, 50), outline=(240, 240, 235)))
        m.decals.append(K.image_decal((s * 0.62, 2.7, -5.6), (s, 0, 0), (0, 0, 0.8 * s), (0, 0.8, 0), star_img(64), depth=0.2))
        m.decals.append(K.image_decal((s * 5.0, 2.46, -0.4), (0, 1, 0), (0.9, 0, 0), (0, 0, 0.9), star_img(64), depth=0.2))
    return m


def f16():
    sch = materials.Scheme(base=0x8A9298, camo=[0x6F767D, 0xA6ACB0], blob=3.2, stretch=2.6, slant=0.05, dirt=0.25, wear=0.3, seed=16, gloss=0.35)
    sch.thr = 0.58
    m = Model('f16', sch, density=18, tex_width=1024)
    m.ground = False
    m.western = True
    m.radome = 0x4A4E52
    m.air = A = K.Air()
    A.under = materials.hexc(0xA6ADB2)
    A.under_y = 1.55
    body = m.part('body')
    B = Body([T(-7.4, 2.38, 1.46, 0.46, pt=2, pb=2), T(-6.6, 2.4, 1.4, 0.5), T(-5.6, 2.46, 1.35, 0.6), T(-3.0, 2.62, 1.26, 0.74, pt=2.4),
              T(-1.0, 2.7, 1.3, 0.74, pt=2.4), T(1.0, 2.54, 1.2, 0.74, pt=2.3), T(2.5, 2.5, 1.18, 0.66), T(3.5, 2.5, 1.18, 0.62),
              T(5.0, 2.44, 1.22, 0.58, pt=2.2), T(6.4, 2.32, 1.34, 0.45), T(7.5, 2.14, 1.56, 0.28), T(8.0, 2.0, 1.72, 0.12), T(8.15, 1.9, 1.8, 0.02)], n=28)
    holes = [(2.5, 5.35)]
    zs = B.zs(0.5, extra=[2.5, 3.3, 3.5, 4.5, 5.0, 5.35, 6.3, 7.0])

    def nose(z, th, k, i):
        return 'a_radome' if z > 6.6 else None
    skin, lining = cockpit_skin(B, zs, holes, 10, 18, glass_fn=nose)
    body.add(skin)
    body.add(lining)
    body.add(bubble(B, zs, 2.5, 5.35, 10, 18, 0.46, frames=[(5.0, 5.08)], front=0.2, back=0.25, shape=0.4))
    body.add(geom.tube([(0, 2.05, 8.05), (0, 2.0, 8.5)], 0.02, 'a_metal', n=5))
    # The chin intake, a fixed lip, the splitter plate.
    I = Body([T(3.4, 1.5, 0.9, 0.5, pt=2.4, pb=2.2), T(2.8, 1.5, 0.86, 0.5), T(1.0, 1.5, 0.95, 0.5), T(-1.4, 1.5, 1.12, 0.45)], n=20)
    body.add(I.skin(I.zs(0.5), 'skin'))
    ring = I.ring(3.4)
    body.add(geom.polygon_cap([p * 0.92 + np.array([0, 1.2, 3.4]) * 0.08 for p in ring][::-1], 'a_intake'))
    body.add(geom.rbox((-0.03, 1.2, 3.2), (0.03, 1.55, 3.6), 'skin', r=0.01))
    body.add(nozzle(0, 1.92, -7.45, 1.4, 0.4, 0.34))
    A.stains.append(((0, 1.9, -7.2), (0, 2.1, -3.5), 0.55, 0.9, (30, 28, 26), 0.8, True))
    # Strakes, wings with the Sidewinders on the tips, the all-moving tailplane, the fin.
    for s in (-1, 1):
        body.add(K.wing((s * 0.55, 1.97, 4.2), (s * 1.3, 1.95, 1.2), 3.2, 1.0, 0.07, 0.05, steps=3, chord_dir=(0, 0, -1)))
        r = (s * 0.75, 1.92, 1.6)
        t = (s * 4.7, 1.9, -2.6)
        body.add(K.wing(r, t, 4.4, 1.2, 0.07, 0.05, steps=8, chord_dir=(0, 0, -1)))
        A.lines.append((wing_line(r, t, 4.4, 1.2, 0.07, 0.05, 0.72, 0.1, 0.52), 0.012, True))
        A.lines.append((wing_line(r, t, 4.4, 1.2, 0.07, 0.05, 0.72, 0.56, 0.98), 0.012, True))
        A.lines.append((wing_line(r, t, 4.4, 1.2, 0.07, 0.05, 0.14, 0.05, 0.98), 0.01, True))
        body.add(K.wing((s * 0.7, 1.86, -4.4), (s * 2.9, 1.82, -5.6), 1.9, 0.75, 0.07, 0.05, steps=3, chord_dir=(0, 0, -1)))
        body.add(K.missile((s * 4.9, 1.9, -2.7), 2.9, 0.13, mat='a_white', nose='a_darkgrey', fins=(1.0, 1.3), canards=False))
        body.add(K.pylon(s * 2.4, 1.86, 1.62, -1.6, 0.4, 'skin', w=0.09))
        body.add(K.drop_tank((s * 2.4, 1.3, -1.4), 3.0, 0.6, 'a_lightgrey'))
        body.add(K.pylon(s * 3.5, 1.84, 1.66, -1.5, 0.2, 'skin', w=0.07))
        body.add(K.missile((s * 3.5, 1.55, -1.9), 3.6, 0.18, mat='a_white', nose='a_white', fins=(1.0, 0.9), canards=False, wings=0.2))
    body.add(K.fin_surface((2.55, -3.9), (4.85, -6.4), 3.0, 1.1, t=0.07, steps=8))
    body.add(geom.rbox((-0.1, 4.7, -6.2), (0.1, 4.9, -6.5), 'a_darkgrey', r=0.04))
    # The ventral fins and the nozzle's surroundings.
    for s in (-1, 1):
        body.add(K.fin_surface((1.0, -4.6), (1.6, -5.4), 1.0, 0.6, t=0.06, x=s * 0.3, steps=2, side_tilt=s * 0.3))
    gear_leg(m, body, (0, 1.3, 4.0), (0, 0.27, 4.0), 0.27, 0.15, 'gear_nose')
    for s in (-1, 1):
        body.add(geom.rbox((s * 1.0 - 0.3, 1.05, -2.0), (s * 1.0 + 0.3, 1.4, -0.7), 'skin', r=0.1))
        gear_leg(m, body, (s * 1.0, 1.4, -1.4), (s * 1.1, 0.36, -1.4), 0.36, 0.2, 'gear_l' if s < 0 else 'gear_r')
        body.add(geom.tube([(s * 0.9, 1.35, -0.9), (s * 1.1, 0.7, -1.4)], 0.03, 'a_gear', n=6))
    jet_cockpit(body, (0, 1.85, 3.55), 4.75, 0.4, 2.0, True)
    for z in list(np.arange(-5.5, 4.0, 0.75)):
        A.lines.append((B.section_line(z), 0.013, True))
    for th in (-math.pi / 2 - 0.6, -math.pi / 2 + 0.6, -math.pi * 1.5 + 0.7, -math.pi * 1.5 - 0.7):
        A.lines.append((B.long_line(th, -5.0, 6.0), 0.012, True))
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 0.1, 3.9, -5.0), (s, 0, 0), 1.2, 0.35, 'AF 86', fg=(70, 74, 80)))
    return m


def a10():
    sch = materials.Scheme(base=0x7A8086, camo=[0x6C7277, 0x9CA2A6], blob=3.4, stretch=2.4, slant=0.05, dirt=0.4, wear=0.4, seed=10, gloss=0.25)
    sch.thr = 0.58
    m = Model('a10', sch, density=18, tex_width=1024)
    m.ground = False
    m.western = True
    m.radome = 0x4C5054
    m.air = A = K.Air()
    A.under = materials.hexc(0x9AA0A6)
    A.under_y = 1.5
    body = m.part('body')
    B = Body([T(-7.8, 2.4, 2.0, 0.1, pt=2, pb=2), T(-6.2, 2.5, 1.7, 0.36), T(-3.5, 2.62, 1.34, 0.6), T(0.0, 2.85, 1.1, 0.7, pt=2.3),
              T(2.6, 2.88, 1.08, 0.72, pt=2.3), T(3.5, 2.8, 1.04, 0.76), T(5.2, 2.6, 1.08, 0.74, pt=2.2),
              T(6.6, 2.4, 1.1, 0.58), T(7.6, 2.16, 1.34, 0.36), T(8.4, 1.9, 1.6, 0.14), T(8.55, 1.8, 1.7, 0.02)], n=28)
    holes = [(3.9, 6.0)]
    zs = B.zs(0.5, extra=[3.9, 4.4, 4.45, 5.4, 5.45, 6.0, 6.4, 7.5])

    def nose(z, th, k, i):
        return 'a_radome' if z > 7.7 else None
    skin, lining = cockpit_skin(B, zs, holes, 10, 18, glass_fn=nose)
    body.add(skin)
    body.add(lining)
    body.add(bubble(B, zs, 3.9, 6.0, 10, 18, 0.5, frames=[(4.4, 4.45), (5.4, 5.45)], front=0.45, back=0.15, shape=0.35))
    # The GAU-8's seven barrels under the nose (a little left), the pitot on the nose.
    body.add(geom.rbox((-0.34, 1.0, 6.9), (0.14, 1.5, 7.9), 'a_darkgrey', r=0.07))
    for k in range(7):
        a = k * 2 * math.pi / 7
        c = (-0.1 + 0.11 * math.cos(a) * (k > 0), 1.25 + 0.11 * math.sin(a) * (k > 0))
        body.add(geom.cyl((c[0], c[1], 7.85), (c[0], c[1], 8.3), 0.04, 'a_dark', n=6))
    body.add(geom.tube([(0, 1.85, 8.5), (0, 1.8, 9.0)], 0.02, 'a_metal', n=5))
    # The two engines high on pylons at the back, intakes at the front, nozzles at the back.
    for s in (-1, 1):
        N = Body([T(-4.4, 3.72, 2.84, 0.4, x0=s * 1.1), T(-3.9, 3.86, 2.64, 0.6), T(-1.8, 3.87, 2.63, 0.62), T(-1.55, 3.82, 2.68, 0.55),
                  T(-1.5, 3.76, 2.74, 0.5)], n=20)
        body.add(N.skin(N.zs(0.5), 'skin'))
        body.add(intake_lip(s * 1.1, 3.25, -1.45, 0.5, 0.45))
        body.add(nozzle(s * 1.1, 3.25, -4.5, 0.8, 0.4, 0.34))
        body.add(K.pylon(s * 0.75, 2.65, 2.55, -3.4, -1.9, 'skin', w=0.18))
        A.stains.append(((s * 1.1, 3.2, -4.3), (s * 1.1, 3.1, -2.0), 0.4, 0.6, (28, 26, 24), 0.8, True))
    # Wings, tailplane and the twin fins at its tips.
    for s in (-1, 1):
        r = (s * 0.7, 1.7, 1.6)
        t = (s * 8.6, 1.78, 0.9)
        body.add(K.wing(r, t, 3.0, 1.5, 0.16, 0.12, steps=8, chord_dir=(0, 0, -1)))
        A.lines.append((wing_line(r, t, 3.0, 1.5, 0.16, 0.12, 0.7, 0.5, 0.97), 0.012, True))
        A.lines.append((wing_line(r, t, 3.0, 1.5, 0.16, 0.12, 0.7, 0.1, 0.45), 0.012, True))
        A.lines.append((wing_line(r, t, 3.0, 1.5, 0.16, 0.12, 0.2, 0.1, 0.95), 0.01, True))
        body.add(K.wing((s * 0.3, 2.4, -6.0), (s * 3.2, 2.4, -6.4), 1.8, 1.4, 0.12, 0.1, steps=3, chord_dir=(0, 0, -1)))
        body.add(K.fin_surface((1.7, -6.0), (4.3, -6.6), 1.8, 1.2, t=0.1, x=s * 3.2, steps=5))
        # The gear pods under the wings.
        P = Body([T(-0.1, 1.5, 1.1, 0.1, x0=s * 2.4), T(0.3, 1.52, 0.96, 0.32), T(1.6, 1.52, 0.96, 0.34), T(2.3, 1.45, 1.02, 0.2)], n=14)
        body.add(P.skin(P.zs(0.5), 'skin', cap1=True))
        for x, kind in ((1.4, 'bomb'), (3.4, 'pod'), (4.6, 'bomb'), (5.8, 'aam'), (7.0, 'pod')):
            yw = 1.7 + (x - 0.7) / 7.9 * 0.08 + 0.08
            body.add(K.pylon(s * x, yw, 1.4, 0.6, 1.8, 'skin', w=0.1))
            if kind == 'bomb':
                body.add(K.bomb((s * x, 1.18, 1.1), 2.0, 0.42, 'a_bomb'))
            elif kind == 'pod':
                body.add(K.rocket_pod((s * x, 1.15, 1.05), 2.1, 0.44, 'a_podgrey'))
            else:
                body.add(K.missile((s * x, 1.2, 1.2), 2.4, 0.3, mat='a_lightgrey', nose='a_darkgrey', fins=(0.8, 1.0), canards=False, wings=0.3))
    gear_leg(m, body, (-0.3, 1.2, 6.0), (-0.3, 0.33, 6.0), 0.33, 0.2, 'gear_nose')
    for s in (-1, 1):
        gear_leg(m, body, (s * 2.4, 1.2, 0.9), (s * 2.4, 0.42, 0.9), 0.42, 0.28, 'gear_l' if s < 0 else 'gear_r')
    jet_cockpit(body, (0, 2.0, 4.45), 5.75, 0.5, 2.1, True)
    for z in list(np.arange(-5.5, 6.5, 0.9)):
        A.lines.append((B.section_line(z), 0.014, True))
    for th in (-math.pi / 2 - 0.6, -math.pi / 2 + 0.6, -math.pi * 1.5 + 0.7, -math.pi * 1.5 - 0.7):
        A.lines.append((B.long_line(th, -5.0, 7.0), 0.013, True))
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 0.6, 3.2, -2.9), (s, 0, 0), 1.0, 0.3, 'AF 80', fg=(60, 64, 68)))
        m.decals.append(K.number_decal((s * 3.3, 3.2, -6.35), (s, 0, 0), 0.7, 0.35, 'OA', fg=(60, 64, 68)))
    return m


def models():
    return [su25(), f16(), a10()]
