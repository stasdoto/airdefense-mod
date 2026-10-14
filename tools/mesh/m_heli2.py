"""2.0: the Ka-52 (coaxial rotors) and the AH-64 Apache as meshes (see m_air.py for the shared pieces)."""
import math

import numpy as np

import geom
import materials
import kit_air as K
from core import Geo, Model
from kit_air import Body, rot_axis
from m_air import (S, main_rotor, tail_rotor, wheel_part, heli_scheme, soviet_blade, cockpit_skin, bubble, star_img)


def mast_and_hump(body, x, y0, y1, z, r=0.14):
    body.add(geom.cyl((x, y0, z), (x, y1, z), r, 'a_hub', n=10, caps=False))


def ka52():
    sch = heli_scheme(0x6C7864, [0x4C5A3E, 0x8D8A70], seed=52, blob=2.4)
    sch.thr = 0.56
    m = Model('ka52', sch, density=20, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.under = materials.hexc(0x9AA6A8)
    A.under_y = 1.35
    soviet_blade(m)
    body = m.part('body')
    B = Body([S(-8.0, yc=2.28, hw=0.1, ht=0.1, pt=2, pb=2),
              S(-7.4, yc=2.26, hw=0.2, ht=0.22),
              S(-5.0, yc=2.16, hw=0.33, ht=0.33),
              S(-2.4, yc=2.02, hw=0.52, ht=0.52),
              S(-1.4, yc=1.88, hw=0.9, ht=0.85, hb=0.85, pt=2.3, pb=2.5),
              S(0.0, yc=1.8, hw=0.98, ht=0.95, hb=0.88, pt=2.5, pb=2.8),
              S(1.8, yc=1.8, hw=0.98, ht=0.95, hb=0.88),
              S(2.3, yc=1.76, hw=0.94, ht=0.95, hb=0.86, kt=0.9),
              S(3.4, yc=1.7, hw=0.88, ht=0.95, hb=0.8, kt=0.78),
              S(4.4, yc=1.62, hw=0.72, ht=0.82, hb=0.68, kt=0.8),
              S(5.1, yc=1.5, hw=0.5, ht=0.6, hb=0.5, kt=0.9),
              S(5.6, yc=1.4, hw=0.24, ht=0.3, hb=0.3),
              S(5.78, yc=1.38, hw=0.03, ht=0.03, hb=0.03)], n=24)
    holes = [(2.15, 4.5)]
    zs = B.zs(0.5, extra=[2.15, 2.9, 2.95, 3.6, 3.65, 4.5, 5.1])

    def noseglass(z, th, k, i):
        return 'a_radome' if z > 5.1 else None
    skin, lining = cockpit_skin(B, zs, holes, 7, 17, glass_fn=noseglass)
    body.add(skin)
    body.add(lining)
    body.add(bubble(B, zs, 2.15, 4.5, 7, 17, 0.5, frames=[(2.9, 2.95), (3.6, 3.65)], front=0.45, back=0.15))
    # Engines on the shoulders: dust-filter intakes at the front, the exhaust deflectors, the hump with the mast.
    for s in (-1, 1):
        N = Body([S(-1.3, yc=2.62, hw=0.2, ht=0.2, x0=s * 1.0), S(-0.9, yc=2.62, hw=0.38, ht=0.36), S(1.0, yc=2.62, hw=0.4, ht=0.38),
                  S(1.35, yc=2.62, hw=0.34, ht=0.32)], n=14)
        body.add(N.skin(N.zs(0.5), 'skin'))
        c0 = np.array([s * 1.02, 2.62, 1.34])
        body.add(geom.lathe(c0, c0 + (0, 0, 0.14), [(0, 0.34, 'skin'), (0.02, 0.3, 'a_intake'), (0.1, 0.12, 'a_metal'), (0.14, 0.0, 'a_metal')],
                            'skin', 12))
        e0 = np.array([s * 1.12, 2.58, -1.2])
        body.add(geom.lathe(e0, e0 + np.array([s * 0.3, -0.06, -0.38]), [(0, 0.2, 'a_soot'), (0.45, 0.24, 'a_soot'), (0.45, 0.17, 'a_black'),
                                                                       (0.2, 0.0, 'a_black')], 'a_soot', 12))
        A.stains.append(((s * 1.3, 2.55, -1.4), (s * 0.8, 2.2, -6.0), 0.25, 0.65, (30, 28, 26), 0.7, True))
    G = Body([S(-1.6, yc=2.7, hw=0.3, ht=0.2), S(-0.8, yc=2.8, hw=0.6, ht=0.4), S(0.2, yc=2.88, hw=0.7, ht=0.48),
              S(1.6, yc=2.82, hw=0.66, ht=0.42), S(2.2, yc=2.7, hw=0.4, ht=0.28)], n=16)
    body.add(G.skin(G.zs(0.5), 'skin'))
    mast_and_hump(body, 0, 3.2, 4.0, 0.75, 0.16)
    # The gun on the right flank (2A42), the optics ball under the nose.
    gh = Body([S(0.1, yc=1.38, hw=0.05, ht=0.08, x0=1.05), S(0.35, yc=1.4, hw=0.17, ht=0.17), S(1.3, yc=1.4, hw=0.17, ht=0.17),
               S(1.6, yc=1.4, hw=0.08, ht=0.1)], n=12)
    body.add(gh.skin(gh.zs(0.4), 'skin', cap1=True))
    body.add(geom.cyl((1.0, 1.4, 1.5), (1.0, 1.4, 3.6), 0.045, 'a_dark', n=8))
    body.add(geom.cyl((1.0, 1.4, 3.3), (1.0, 1.4, 3.62), 0.065, 'a_dark', n=8))
    body.add(geom.sphere((0, 1.12, 4.9), 0.24, 'a_darkgrey', n=14, rings=8))
    body.add(geom.lathe((0, 1.12, 4.95), (0, 1.12, 5.2), [(0, 0.12, 'a_sensor'), (0.12, 0.12, 'a_sensor'), (0.2, 0.0, 'a_sensor')],
                        'a_sensor', 12))
    # The tail: boom, the stabiliser with the end fins, the top fin.
    body.add(K.fin_surface((2.3, -6.7), (3.75, -7.55), 1.3, 0.8, t=0.14, steps=4))
    for s in (-1, 1):
        body.add(K.wing((s * 0.2, 2.2, -6.95), (s * 1.4, 2.2, -7.05), 0.85, 0.7, 0.12, 0.1, steps=3, chord_dir=(0, 0, -1)))
        body.add(K.fin_surface((1.65, -6.95), (2.95, -7.4), 1.05, 0.7, t=0.12, x=s * 1.4, steps=3))
    body.add(geom.tube([(0, 2.0, -7.3), (0, 1.75, -7.6)], 0.03, 'a_gear', n=6))
    # Stub wings with pylons: rocket pods inboard, Vikhr missiles outboard, flare dispensers at the tips.
    for s in (-1, 1):
        body.add(K.wing((s * 0.95, 1.7, 1.0), (s * 3.0, 1.68, 1.0), 1.3, 1.1, 0.14, 0.12, steps=3, chord_dir=(0, 0, -1)))
        body.add(K.pylon(s * 1.7, 1.66, 1.3, -0.05, 0.85, 'skin', w=0.08))
        body.add(K.rocket_pod((s * 1.7, 1.1, 0.25), 1.7, 0.44, 'a_pod'))
        body.add(K.pylon(s * 2.55, 1.67, 1.45, -0.05, 0.85, 'skin', w=0.08))
        for dx in (-0.13, 0.13):
            body.add(K.missile((s * 2.55 + dx, 1.28, 0.1), 2.0, 0.16, mat='a_white', nose='a_seeker', fins=(0.7, 1.0), canards=False))
        body.add(geom.rbox((s * 3.0 - 0.07, 1.45, -0.4), (s * 3.0 + 0.07, 1.9, 0.6), 'skin', r=0.04))
    # Landing gear.
    body.add(K.strut((0, 1.0, 4.0), (0, 0.32, 4.0), 0.055))
    body.add(geom.cyl((-0.3, 0.3, 4.0), (0.3, 0.3, 4.0), 0.03, 'a_gear', n=6))
    wheel_part(m, 'gear_nose_a', (-0.17, 0.3, 4.0), 0.3, 0.14)
    wheel_part(m, 'gear_nose_b', (0.17, 0.3, 4.0), 0.3, 0.14)
    for s in (-1, 1):
        x = s * 1.15
        body.add(K.strut((s * 0.85, 1.4, -0.4), (x, 0.4, -0.4), 0.07))
        body.add(geom.tube([(s * 0.8, 1.0, 0.25), (x, 0.4, -0.4)], 0.04, 'a_gear', n=6))
        body.add(geom.tube([(s * 0.8, 1.0, -1.0), (x, 0.4, -0.4)], 0.04, 'a_gear', n=6))
        wheel_part(m, 'gear_l' if s < 0 else 'gear_r', (x, 0.38, -0.4), 0.38, 0.22)
    body.add(geom.sphere((-1.0, 2.72, 0.2), 0.05, 'a_nav_red', n=6, rings=4))
    body.add(geom.sphere((1.0, 2.72, 0.2), 0.05, 'a_nav_green', n=6, rings=4))
    # The two rotors one above the other: the lower is the spinner, the upper turned 60 degrees and counter-rotating.
    rot = main_rotor(body, (0, 3.95, 0.75), 7.25, 3, 0.55, droop=0.3, hub_r=0.5, model=m, mast_h=0.5)
    main_rotor(rot, (0, 4.75, 0.75), 7.25, 3, 0.55, droop=0.3, hub_r=0.5, name='rotor_upper', blade_prefix='upper_blade', cw=False,
               mast_h=0.82, rot=60.0)
    # Cockpit.
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    for x in (-0.42, 0.42):
        g.extend(K.seat((x, 1.15, 3.25), 0.46, ejection=True))
        g.extend(K.stick((x, 0.95, 3.7)))
    g.extend(K.instrument_panel(4.2, 1.75, 2.15, 0.78))
    g.extend(geom.rbox((-0.12, 1.2, 3.8), (0.12, 1.8, 4.2), 'a_coaming', r=0.03))
    ck.add(g)
    # Panel lines.
    for z in list(np.arange(-1.4, 2.2, 0.5)) + [-2.4, -4.0, -6.0]:
        A.lines.append((B.section_line(z), 0.016, True))
    for th in (-math.pi / 2 - 0.55, -math.pi / 2 + 0.55, -math.pi * 1.5 + 0.8, -math.pi * 1.5 - 0.8):
        A.lines.append((B.long_line(th, -2.0, 5.0), 0.014, True))
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 0.37, 2.1, -4.3), (s, 0, 0), 1.2, 0.5, '52', fg=(222, 222, 215)))
        m.decals.append(K.image_decal((s * 0.34, 2.12, -6.0), (s, 0, 0), (0, 0, 0.62 * s), (0, 0.62, 0), star_img(64), depth=0.2))
    return m


def ah64():
    sch = heli_scheme(0x4A4D44, [0x3C4036, 0x575A52], seed=64, blob=3.0, dirt=0.3)
    sch.thr = 0.55
    m = Model('ah64', sch, density=20, tex_width=1024)
    m.ground = False
    m.western = True
    m.radome = 0x34373A
    m.air = A = K.Air()
    A.under = materials.hexc(0x5A5E58)
    A.under_y = 1.0
    m.blade_tip = (200, 200, 195)
    body = m.part('body')
    B = Body([S(-7.9, yc=2.5, hw=0.1, ht=0.12, pt=2, pb=2),
              S(-7.4, yc=2.46, hw=0.17, ht=0.22),
              S(-5.0, yc=2.3, hw=0.28, ht=0.36),
              S(-2.6, yc=2.15, hw=0.42, ht=0.5),
              S(-1.5, yc=2.08, hw=0.5, ht=0.55),
              S(-0.2, yc=1.98, hw=0.62, ht=0.7, hb=0.8, pt=2.4, pb=2.6),
              S(1.6, yc=1.96, hw=0.66, ht=0.78, hb=0.86, pt=2.4, pb=2.6),
              S(2.5, yc=1.9, hw=0.66, ht=0.8, hb=0.9, kt=0.7),
              S(3.5, yc=1.84, hw=0.62, ht=0.78, hb=0.88, kt=0.55),
              S(3.95, yc=1.8, hw=0.6, ht=0.78, hb=0.88, kt=0.5),
              S(4.4, yc=1.66, hw=0.56, ht=0.66, hb=0.76, kt=0.5),
              S(5.55, yc=1.6, hw=0.5, ht=0.52, hb=0.62, kt=0.55),
              S(6.1, yc=1.58, hw=0.42, ht=0.44, hb=0.52),
              S(6.5, yc=1.56, hw=0.26, ht=0.3, hb=0.3),
              S(6.7, yc=1.55, hw=0.04, ht=0.04, hb=0.04)], n=24)
    holes = [(2.4, 3.9), (4.0, 5.5)]
    zs = B.zs(0.5, extra=[2.4, 3.1, 3.15, 3.85, 3.9, 4.0, 4.5, 4.55, 5.5, 6.1])

    def nose(z, th, k, i):
        return 'a_radome' if z > 6.1 else None
    skin, lining = cockpit_skin(B, zs, holes, 8, 16, glass_fn=nose)
    body.add(skin)
    body.add(lining)
    body.add(bubble(B, zs, 2.4, 3.9, 8, 16, 0.5, frames=[(3.1, 3.15)], front=0.35, back=0.2))
    body.add(bubble(B, zs, 4.0, 5.5, 8, 16, 0.42, frames=[(4.5, 4.55)], front=0.4, back=0.3))
    # The nose: TADS/PNVS turrets (the sensor turret with its two windows, the pilot night vision on top).
    body.add(geom.rbox((-0.3, 1.5, 6.0), (0.3, 1.9, 6.7), 'a_darkgrey', r=0.12))
    body.add(geom.cyl((-0.1, 1.74, 6.69), (-0.1, 1.74, 6.72), 0.09, 'a_sensor', n=10))
    body.add(geom.cyl((0.14, 1.64, 6.69), (0.14, 1.64, 6.72), 0.07, 'a_sensor', n=10))
    body.add(geom.cyl((0.14, 1.78, 6.69), (0.14, 1.78, 6.72), 0.05, 'a_sensor', n=10))
    body.add(geom.rbox((-0.2, 1.82, 6.2), (0.2, 2.02, 6.55), 'a_darkgrey', r=0.07))
    # The chain gun under the chin.
    body.add(geom.rbox((-0.2, 0.95, 4.2), (0.2, 1.25, 4.8), 'a_darkgrey', r=0.06))
    body.add(geom.cyl((0, 1.05, 4.7), (0, 1.05, 6.3), 0.035, 'a_dark', n=8))
    body.add(geom.cyl((0, 1.05, 6.0), (0, 1.05, 6.32), 0.055, 'a_dark', n=8))
    # The spine with the engine pods on the shoulders (exhausts turned out), the mast with the Longbow radome.
    G = Body([S(-1.7, yc=2.8, hw=0.3, ht=0.3), S(-0.6, yc=3.0, hw=0.55, ht=0.5), S(1.0, yc=3.05, hw=0.62, ht=0.45),
              S(2.2, yc=2.95, hw=0.4, ht=0.35), S(2.45, yc=2.9, hw=0.3, ht=0.2)], n=16)
    body.add(G.skin(G.zs(0.5), 'skin'))
    for s in (-1, 1):
        N = Body([S(-1.6, yc=2.62, hw=0.24, ht=0.26, x0=s * 0.98), S(-1.2, yc=2.62, hw=0.4, ht=0.4), S(1.3, yc=2.62, hw=0.42, ht=0.42),
                  S(1.65, yc=2.6, hw=0.3, ht=0.3)], n=14)
        body.add(N.skin(N.zs(0.5), 'skin'))
        c0 = np.array([s * 0.98, 2.6, 1.62])
        body.add(geom.lathe(c0, c0 + (0, 0, 0.1), [(0, 0.3, 'skin'), (0.02, 0.26, 'a_intake'), (0.1, 0.0, 'a_intake')], 'skin', 12))
        e0 = np.array([s * 1.15, 2.6, -1.5])
        body.add(geom.lathe(e0, e0 + np.array([s * 0.3, 0.02, -0.35]), [(0, 0.2, 'a_soot'), (0.4, 0.23, 'a_soot'), (0.4, 0.16, 'a_black'),
                                                                      (0.2, 0.0, 'a_black')], 'a_soot', 12))
        A.stains.append(((s * 1.3, 2.6, -1.7), (s * 0.7, 2.3, -6.0), 0.22, 0.55, (30, 28, 26), 0.7, True))
    body.add(geom.cyl((0, 3.3, 0.7), (0, 4.0, 0.7), 0.15, 'a_hub', n=10, caps=False))
    body.add(geom.lathe((0, 4.2, 0.2), (0, 4.2, 1.25), [(0, 0.0, 'a_radome'), (0.12, 0.3, 'a_radome'), (0.5, 0.36, 'a_radome'), (0.9, 0.3, 'a_radome'),
                                                       (1.05, 0.0, 'a_radome')], 'a_radome', 16))
    # The tail: the T-tail fin with the tail rotor on the left, the stabilator low at the back.
    body.add(K.fin_surface((2.4, -7.0), (4.4, -8.0), 1.3, 0.8, t=0.15, steps=4))
    for s in (-1, 1):
        body.add(K.wing((s * 0.2, 2.5, -7.35), (s * 1.1, 2.5, -7.4), 0.85, 0.75, 0.13, 0.11, steps=2, chord_dir=(0, 0, -1)))
    tail_rotor(body, (-0.22, 3.7, -7.7), 1.4, 4, chord=0.22, side=-1)
    body.add(geom.tube([(0, 2.4, -7.3), (0, 1.5, -7.2)], 0.055, 'a_gear', n=6))
    # Stub wings: Hellfires outboard, Hydra pods inboard.
    for s in (-1, 1):
        body.add(K.wing((s * 0.7, 2.14, 1.0), (s * 2.45, 2.1, 1.0), 1.1, 0.95, 0.13, 0.11, steps=3, chord_dir=(0, 0, -1)))
        body.add(K.pylon(s * 1.3, 2.05, 1.7, -0.05, 0.85, 'skin', w=0.08))
        body.add(K.rocket_pod((s * 1.3, 1.45, 0.25), 1.5, 0.4, 'a_podgrey'))
        body.add(K.pylon(s * 2.1, 2.07, 1.8, 0.0, 0.8, 'skin', w=0.08))
        for dx in (-0.12, 0.12):
            for dy in (-0.12, 0.12):
                body.add(K.missile((s * 2.1 + dx, 1.62 + dy, 0.1), 1.6, 0.16, mat='a_olive', nose='a_darkgrey', fins=(0.6, 0.9), canards=False))
    # Landing gear: the mains on the sponsons, the tailwheel.
    for s in (-1, 1):
        x = s * 1.0
        body.add(K.strut((s * 0.62, 1.25, 3.6), (x, 0.34, 3.6), 0.06))
        body.add(geom.tube([(s * 0.5, 1.0, 3.0), (x, 0.34, 3.6)], 0.035, 'a_gear', n=6))
        wheel_part(m, 'gear_l' if s < 0 else 'gear_r', (x, 0.32, 3.6), 0.32, 0.2)
    body.add(geom.tube([(0, 2.35, -6.2), (0, 0.4, -7.2)], 0.045, 'a_gear', n=6))
    wheel_part(m, 'gear_tail', (0, 0.18, -7.2), 0.18, 0.12)
    body.add(geom.sphere((-0.7, 2.2, 0.8), 0.04, 'a_nav_red', n=6, rings=4))
    body.add(geom.sphere((0.7, 2.2, 0.8), 0.04, 'a_nav_green', n=6, rings=4))
    main_rotor(body, (0, 3.95, 0.7), 7.3, 4, 0.53, droop=0.3, hub_r=0.5, model=m, mast_h=0.4)
    # The cockpits: the pilot above and behind, the gunner in front.
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    g.extend(K.seat((0, 2.05, 3.15), 0.46, ejection=False))
    g.extend(K.stick((0, 1.8, 3.5)))
    g.extend(K.instrument_panel(3.95, 2.35, 2.7, 0.4))
    g.extend(K.seat((0, 1.5, 4.75), 0.44, ejection=False))
    g.extend(K.stick((0, 1.25, 5.05)))
    g.extend(K.instrument_panel(5.45, 1.8, 2.1, 0.38))
    ck.add(g)
    for z in list(np.arange(-1.4, 2.4, 0.55)) + [-3.0, -5.0, -6.6]:
        A.lines.append((B.section_line(z), 0.015, True))
    for th in (-math.pi / 2 - 0.5, -math.pi / 2 + 0.5, -math.pi * 1.5 + 0.6, -math.pi * 1.5 - 0.6):
        A.lines.append((B.long_line(th, -3.0, 6.0), 0.013, True))
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 0.3, 2.35, -5.0), (s, 0, 0), 0.9, 0.3, '64', fg=(180, 180, 175)))
    return m


def models():
    return [ka52(), ah64()]
