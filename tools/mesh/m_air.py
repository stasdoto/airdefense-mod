"""
2.0: the aircraft, helicopters, boats, ships, airliners and trains as real meshes (see kit_air.py for the pieces).

Each model mirrors its box model (tools/models/aircraft.py, aircraft2.py, armor.py, navy.py, planes.py, trains.py): the
same animated parts (rotor and its blades, wheels, turret and gun) with the same names, pivots and parents, and the same
overall size. Metres, x right, y up, z forward (the nose).
"""
import math

import numpy as np

import geom
import materials
import kit_air as K
from core import Geo, Model
from kit_air import Body, both, rot_axis, tilt

K.register()


def S(z, **kw):
    kw['z'] = z
    return kw


# ---------------------------------------------------------------------------------------------------------------
# Shared helicopter pieces


def main_rotor(body, pivot, radius, blades, chord, droop=0.3, hub_r=0.42, name='rotor', blade_prefix=None, cw=True, mast_h=0.5,
               model=None, mat='a_blade', rot=0.0, hub_mat=None):
    """The main rotor: the hub (swashplate, hinges, dampers) in the part {name} (the spinner) and each blade its own part
    (named like the box model's) turned about the mast."""
    x, y, z = pivot
    r = body.part(name, pivot, (0, rot, 0)) if rot else body.part(name, pivot)
    g = Geo(45)
    # Mast, swashplate, hub plates.
    g.extend(geom.cyl((x, y - mast_h, z), (x, y + 0.1, z), 0.12, 'a_hub', n=8, caps=False))
    g.extend(geom.cyl((x, y - mast_h * 0.7, z), (x, y - mast_h * 0.55, z), 0.34, 'a_metal', n=12))
    g.extend(geom.cyl((x, y - 0.12, z), (x, y + 0.06, z), hub_r * 0.75, 'a_hub', n=blades * 3, bevel=0.03))
    g.extend(geom.cyl((x, y + 0.06, z), (x, y + 0.2, z), 0.16, 'a_hub', n=8))
    for i in range(blades):
        a = math.radians(360.0 * i / blades)
        d = np.array([math.sin(a), 0, math.cos(a)])
        e = np.array([d[2], 0, -d[0]])
        p0 = np.array(pivot) + d * hub_r * 0.5
        p1 = np.array(pivot) + d * (hub_r + 0.25)
        g.extend(geom.tube([p0 + (0, -0.03, 0), p1 + (0, -0.03, 0)], 0.075, 'a_metal', n=6))
        # The pitch link and lag damper.
        g.extend(geom.tube([p0 + e * 0.16 + (0, -0.32, 0), p0 + e * 0.16 + d * 0.25 + (0, -0.04, 0)], 0.02, 'a_metal', n=4))
        g.extend(geom.tube([p0 - e * 0.12 + (0, 0.04, 0), p1 - e * 0.1 + (0, 0.02, 0)], 0.035, 'a_hub', n=6))
    r.add(g)
    prefix = blade_prefix or ('%s_blade' % name)
    for i in range(blades):
        b = r.part('%s_%d' % (prefix, i), pivot, (0, 360.0 * i / blades, 0))
        bg = K.blade(hub_r + 0.2, radius - 0.25, chord, 0.0, droop=droop, mat=mat)
        if not cw:
            bg = bg.mirrored_x()
        bg.transform(t=pivot)
        b.add(bg)
    if model is not None:
        model.blade_r = radius
        model.hub_xz = (x, z)
    return r


def tail_rotor(body, pivot, radius, blades, chord=0.22, side=1, prefix='tail_blade'):
    """A tail rotor on a side of the fin (blades in the y-z plane, turned about x)."""
    x, y, z = pivot
    t = body.part('tail_rotor', pivot)
    g = Geo(45)
    g.extend(geom.cyl((x - side * 0.25, y, z), (x + side * 0.12, y, z), 0.07, 'a_hub', n=10))
    g.extend(geom.lathe((x + side * 0.08, y, z), (x + side * 0.28, y, z), [(0, 0.13), (0.1, 0.12), (0.2, 0.0)], 'a_hub', 12))
    t.add(g)
    for i in range(blades):
        b = t.part('%s_%d' % (prefix, i), pivot, (360.0 * i / blades, 0, 0))
        bg = K.blade(0.18, radius - 0.08, chord, 0.0, droop=0.0, steps=3, n=6, twist=-6, mat='a_blade')
        # Along +z -> along +y, sitting on the rotor's side of the hub.
        bg.transform(R=rot_axis((1, 0, 0), -90))
        bg.transform(t=(x + side * 0.16, y, z))
        b.add(bg)
    return t


def wheel_part(m, name, c, r, width, parent=None, hub='a_gear', n=18):
    p = (parent.part(name, c) if parent is not None else m.part(name, c))
    p.add(K.gear_wheel(c, r, width, n=n, hub=hub))
    return p


def heli_scheme(base, camo, under=None, seed=1, blob=2.2, dirt=0.35):
    sch = materials.Scheme(base=base, camo=camo, blob=blob, stretch=1.8, slant=0.25, dirt=dirt, wear=0.35, seed=seed, gloss=0.25)
    return sch


def soviet_blade(m):
    m.blade_tip = (214, 176, 40)


# ---------------------------------------------------------------------------------------------------------------
# Mi-8MTV


def mi8():
    sch = heli_scheme(0x4E5A34, [0x857652, 0x2F3526], seed=8, blob=2.6)
    sch.thr = 0.57
    m = Model('mi8', sch, density=20, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.under = materials.hexc(0x8EA4B0)
    A.under_y = 1.4
    soviet_blade(m)
    body = m.part('body')
    # The fuselage: tail boom tip, boom, clamshell doors, the cabin, the glazed nose.
    B = Body([S(-10.75, yc=2.9, hw=0.12, ht=0.14, pt=2, pb=2),
              S(-10.3, yc=2.86, hw=0.25, ht=0.27),
              S(-7.0, yc=2.66, hw=0.4, ht=0.39),
              S(-3.6, yc=2.48, hw=0.6, ht=0.58),
              S(-2.9, yc=2.3, hw=0.86, ht=0.82, hb=0.9, pt=2.4, pb=2.6),
              S(-2.2, yc=2.0, hw=1.14, ht=1.08, hb=1.1, pt=2.8, pb=3.2),
              S(-1.4, yc=1.92, hw=1.24, ht=1.16, hb=1.18, pt=3.0, pb=3.6),
              S(4.4, yc=1.92, hw=1.26, ht=1.16, hb=1.18),
              S(5.4, yc=1.9, hw=1.24, ht=1.12, hb=1.16, pt=2.8, pb=3.2),
              S(6.3, yc=1.8, hw=1.15, ht=0.98, hb=1.04, pt=2.5, pb=2.8),
              S(6.95, yc=1.6, hw=0.95, ht=0.78, hb=0.82, pt=2.2, pb=2.4),
              S(7.35, yc=1.42, hw=0.66, ht=0.52, hb=0.55),
              S(7.55, yc=1.38, hw=0.32, ht=0.28, hb=0.28),
              S(7.62, yc=1.38, hw=0.04, ht=0.04, hb=0.04)], n=28)
    zs = B.zs(0.55, extra=[5.1, 6.2, 6.45, 6.5, 3.85, 4.75, -1.9])

    def glazing(z, th, k, i):
        s = math.sin(th)
        c = math.cos(th)
        y = B.at(z)['yc'] + s * B.at(z)['ht']
        # The windscreen and the nose glazing: the upper front, a frame down the middle and across.
        if z > 6.5 and s > 0.05 and abs(c) > 0.06 and not (6.95 < z < 7.05):
            return 'a_glass'
        if 6.5 < z < 7.4 and -0.55 < s < -0.15 and abs(c) > 0.3 and abs(c) < 0.85:
            return 'a_glass'
        # The bulged side windows of the cockpit.
        if 5.1 < z < 6.2 and 0.0 < s < 0.62 and abs(c) > 0.6:
            return 'a_glass'
        return 'skin'
    skin = B.skin(zs, 'skin', mats=glazing)
    body.add(skin)
    # The cockpit inside: a lining with the windows open, the bulkhead behind the pilots.
    inner = B.inset(0.05)
    zc = [z for z in zs if z >= 4.6]
    lining = inner.skin([4.6] + zc, 'a_cockpit', mats=lambda z, th, k, i: None if glazing(z, th, k, i) == 'a_glass' else 'a_cockpit')
    body.add(K.flip(lining))
    body.add(geom.polygon_cap(inner.ring(4.62), 'a_cockpit'))
    # Round cabin windows, the sliding door, the clamshell doors' seam, steps.
    win = Geo(30)
    for s in (-1, 1):
        for z in (3.45, 2.25, 1.05, -0.15) + ((4.3,) if s > 0 else ()):
            th = B.theta_at(z, 2.3, s)
            p = B.surf(z, th, 0.0)
            nrm = K.normalize(np.array([p[0], 0.0, 0.0]))
            win.extend(geom.lathe(p - nrm * 0.03, p + nrm * 0.03, [(0, 0.22, 'a_frame'), (0.05, 0.21, 'a_frame'), (0.06, 0.18, 'glass_dark'),
                                                                 (0.06, 0.0, 'glass_dark')], 'a_frame', 10, 60))
    body.add(win)
    # Engines on the roof: two nacelles, the dust protection (PZU) in front, the exhausts out to the sides; the gearbox.
    for s in (-1, 1):
        N = Body([S(0.25, yc=3.2, hw=0.18, ht=0.18, x0=s * 0.62),
                  S(0.6, yc=3.2, hw=0.38, ht=0.36), S(1.2, yc=3.24, hw=0.46, ht=0.44, pt=2.4),
                  S(4.4, yc=3.26, hw=0.48, ht=0.45), S(4.9, yc=3.25, hw=0.44, ht=0.42), S(5.05, yc=3.25, hw=0.4, ht=0.38)], n=16)
        body.add(N.skin(N.zs(0.5), 'skin', cap1=True))
        # PZU: a drum with a mesh front.
        c0 = np.array([s * 0.62, 3.26, 5.05])
        body.add(geom.lathe(c0, c0 + (0, 0, 0.7), [(0, 0.36, 'skin'), (0.05, 0.4, 'skin'), (0.5, 0.4, 'skin'),
                                                   (0.62, 0.34, 'skin'), (0.66, 0.26, 'a_mesh'), (0.7, 0.0, 'a_mesh')], 'skin', 14))
        # Exhaust: a bent pipe out to the side.
        e0 = np.array([s * 0.9, 3.22, 0.75])
        body.add(geom.lathe(e0, e0 + np.array([s * 0.45, -0.08, -0.35]), [(0, 0.24, 'a_soot'), (0.3, 0.27, 'a_soot'), (0.58, 0.29, 'a_soot'),
                                                                        (0.58, 0.2, 'a_black'), (0.3, 0.0, 'a_black')], 'a_soot', 14))
        A.stains.append(((s * 1.25, 3.15, 0.4), (s * 1.0, 2.7, -5.5), 0.3, 0.9, (30, 28, 26), 0.75, True))
    G = Body([S(0.2, yc=3.1, hw=0.25, ht=0.2), S(0.7, yc=3.3, hw=0.6, ht=0.4), S(1.4, yc=3.45, hw=0.72, ht=0.5, pt=2.6),
              S(3.2, yc=3.45, hw=0.72, ht=0.5), S(3.8, yc=3.3, hw=0.55, ht=0.38), S(4.3, yc=3.18, hw=0.3, ht=0.2)], n=18)
    body.add(G.skin(G.zs(0.5), 'skin'))
    body.add(geom.cyl((0, 3.88, 1.95), (0, 4.0, 1.95), 0.12, 'a_darkgrey', n=10))
    # Oil weeping down from the gearbox.
    for s in (-1, 1):
        A.stains.append(((s * 0.7, 3.3, 2.4), (s * 1.2, 2.2, 1.6), 0.08, 0.25, (40, 36, 30), 0.5, True))
    # Fin, tail rotor on its right, stabiliser.
    fin = K.fin_surface((2.75, -9.95), (4.95, -10.95), 1.55, 0.95, t=0.16, steps=4)
    body.add(fin)
    for s in (-1, 1):
        st = K.wing((s * 0.22, 2.64, -8.35), (s * 1.5, 2.64, -8.45), 0.75, 0.6, 0.12, 0.1, steps=2, chord_dir=(0, 0, -1), up=(0, -1, 0))
        body.add(st)
    tail_rotor(body, (0.22, 4.25, -10.5), 1.55, 3, chord=0.26, side=1)
    # The tail skid.
    body.add(geom.tube([(0, 2.5, -9.6), (0, 2.0, -10.0), (0, 1.9, -10.25)], 0.04, 'a_gear', n=6))
    # Fuel tanks low on the sides; outriggers with rocket pods.
    for s in (-1, 1):
        T = Body([S(-0.9, yc=1.08, hw=0.08, ht=0.1, x0=s * 1.45), S(-0.6, yc=1.08, hw=0.3, ht=0.34), S(-0.2, yc=1.1, hw=0.38, ht=0.42),
                  S(2.3, yc=1.1, hw=0.38, ht=0.42), S(2.7, yc=1.08, hw=0.28, ht=0.32), S(2.95, yc=1.08, hw=0.06, ht=0.08)], n=16)
        body.add(T.skin(T.zs(0.5), 'skin'))
        beam = geom.rbox((min(s * 1.2, s * 2.3), 1.72, 1.25), (max(s * 1.2, s * 2.3), 1.84, 1.95), 'skin', r=0.04)
        body.add(beam)
        body.add(geom.tube([(s * 1.22, 1.3, 1.6), (s * 1.9, 1.76, 1.6)], 0.045, 'a_darkgrey', n=6))
        body.add(K.pylon(s * 2.05, 1.74, 1.55, 1.15, 2.05, 'a_darkgrey', w=0.08))
        body.add(K.rocket_pod((s * 2.05, 1.32, 1.5), 1.9, 0.5, 'a_pod'))
    # Landing gear: the twin nose wheels, the main legs with their bracing struts.
    body.add(K.strut((0, 1.0, 6.25), (0, 0.32, 6.2), 0.06))
    body.add(geom.cyl((-0.3, 0.3, 6.2), (0.3, 0.3, 6.2), 0.035, 'a_gear', n=6))
    wheel_part(m, 'gear_nose_a', (-0.17, 0.3, 6.2), 0.3, 0.17)
    wheel_part(m, 'gear_nose_b', (0.17, 0.3, 6.2), 0.3, 0.17)
    for s in (-1, 1):
        x = s * 1.65
        body.add(K.strut((x * 0.95, 1.5, -0.2), (x, 0.44, -0.2), 0.08))
        body.add(geom.tube([(s * 1.05, 0.85, -0.25), (x, 0.44, -0.2)], 0.05, 'a_gear', n=6))
        body.add(geom.tube([(s * 1.1, 0.95, 0.6), (x, 0.44, -0.2)], 0.045, 'a_gear', n=6))
        wheel_part(m, 'gear_l' if s < 0 else 'gear_r', (x, 0.42, -0.2), 0.42, 0.28)
    # Lamps and antennas.
    body.add(geom.sphere((-1.26, 2.9, 4.8), 0.05, 'a_nav_red', n=6, rings=4))
    body.add(geom.sphere((1.26, 2.9, 4.8), 0.05, 'a_nav_green', n=6, rings=4))
    body.add(geom.tube([(0, 0.72, 3.0), (0, 0.5, 2.6)], 0.012, 'a_dark', n=4))
    body.add(geom.tube([(0.6, 3.05, -2.5), (0.0, 3.3, -6.0)], 0.008, 'a_dark', n=3, caps=False))
    # Rotors.
    main_rotor(body, (0, 4.45, 2.2), 10.6, 5, 0.52, droop=0.45, hub_r=0.5, model=m, mast_h=0.6)
    # The cockpit (seen close up): seats, the panel, the sticks, the jump seat.
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    for x in (-0.48, 0.48):
        g.extend(K.seat((x, 1.38, 5.7), 0.48, ejection=False))
        g.extend(K.stick((x, 1.0, 6.0)))
    g.extend(K.instrument_panel(6.45, 1.35, 1.85, 0.85))
    g.extend(geom.rbox((-0.2, 0.85, 6.0), (0.2, 1.4, 6.5), 'a_coaming', r=0.04))
    g.extend(geom.rbox((-0.6, 2.75, 5.2), (0.6, 2.9, 6.0), 'a_panel', r=0.03))
    ck.add(g)
    # Panel lines: the cabin's frames, stringers, the door, the boom's joints.
    for z in np.arange(-1.4, 4.6, 0.6):
        A.lines.append((B.section_line(z), 0.016, True))
    for z in (-3.6, -2.9, -6.0, -8.5):
        A.lines.append((B.section_line(z), 0.016, True))
    for th in (-math.pi / 2 - 0.5, -math.pi / 2 + 0.5, math.pi / 2 - 0.6, math.pi / 2 + 0.6, -math.pi * 1.5 + 0.6):
        A.lines.append((B.long_line(th, -3.0, 6.2), 0.014, True))
    thA = B.theta_at(4.3, 0.95, -1)
    thB = B.theta_at(4.3, 2.68, -1)
    A.lines.append((B.section_line(3.85, thA, thB, k=16), 0.02, False))
    A.lines.append((B.section_line(4.75, thA, thB, k=16), 0.02, False))
    A.lines.append((B.long_line(thA, 3.85, 4.75, k=4), 0.02, False))
    A.lines.append((B.long_line(thB, 3.85, 4.75, k=4), 0.02, False))
    A.lines.append((B.long_line(-math.pi * 1.5, -2.9, -1.9, k=6), 0.02, False))
    # Markings: the side number on the boom and nose, a rescue arrow.
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 0.5, 2.6, -5.2), (s, 0, 0), 1.5, 0.62, '52', fg=(222, 186, 60)))
        m.decals.append(K.number_decal((s * 1.15, 1.7, 5.55), (s, 0, 0), 0.9, 0.42, '52', fg=(222, 186, 60)))
    return m


def cockpit_skin(B, zs, holes, k0, k1, glass_fn=None, mat='skin'):
    """The skin of a body with the canopies' holes (z ranges over the ring points k0..k1), and its lining inside the
    cockpits (a flipped skin, open where the outer one is open or glazed)."""
    def outer(z, th, k, i):
        for za, zb in holes:
            if za <= z <= zb and k0 <= k < k1:
                return None
        if glass_fn is not None:
            g = glass_fn(z, th, k, i)
            if g:
                return g
        return mat
    skin = B.skin(zs, mat, mats=outer)
    inner = B.inset(0.05)
    za = min(h[0] for h in holes)
    zb = max(h[1] for h in holes)
    zl = [z for z in zs if za - 1e-6 <= z <= zb + 1e-6]
    lining = inner.skin(zl, 'a_cockpit', mats=lambda z, th, k, i: None if outer(z, th, k, i) != mat else 'a_cockpit')
    g = Geo(40)
    g.extend(K.flip(lining))
    g.extend(geom.polygon_cap(inner.ring(zl[0] + 0.01), 'a_cockpit'))
    g.extend(geom.polygon_cap(inner.ring(zl[-1] - 0.01)[::-1], 'a_cockpit'))
    return skin, g


def bubble(B, zs, za, zb, k0, k1, height, mat='a_glass', frames=(), shape=0.5, front=0.35, back=0.3):
    """A canopy over the hole za..zb: the glass rises {height} above the skin, rounding up from the front and back
    edges (front / back: the share of the length the rise takes)."""
    L = zb - za
    cz = [z for z in zs if za - 1e-6 <= z <= zb + 1e-6]

    def lift(z, s):
        t = (z - za) / L
        if t < back:
            h = math.sin(math.pi / 2 * t / back)
        elif t > 1 - front:
            h = math.sin(math.pi / 2 * (1 - t) / front)
        else:
            h = 1.0
        h = max(h, 0.0) ** 0.7
        return (1.0, height * h * max(math.sin(math.pi * s), 0.0) ** shape)
    return K.canopy(B, cz, k0, k1, lift, mat=mat, frames=frames, frame_mat='a_frame')


# ---------------------------------------------------------------------------------------------------------------
# Mi-24P


def stub_wing(body, x0, x1, y0, y1, z_le0, z_le1, c0, c1, t=0.16, mat='skin', steps=2):
    """A stub wing from the fuselage side out (both sides), anhedral by the drop of y."""
    g = Geo(45)
    for s in (-1, 1):
        g.extend(K.wing((s * x0, y0, z_le0), (s * x1, y1, z_le1), c0, c1, t, t * 0.9, steps=steps, mat=mat, n=9))
    return g


def mi24():
    sch = heli_scheme(0x5A6040, [0x8E7D56, 0x343B2A], seed=24, blob=2.0)
    sch.thr = 0.56
    m = Model('mi24', sch, density=20, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    A.under = materials.hexc(0x8FA6B2)
    A.under_y = 1.6
    soviet_blade(m)
    body = m.part('body')
    B = Body([S(-9.7, yc=2.79, hw=0.1, ht=0.11, pt=2, pb=2, kt=1.0),
              S(-9.3, yc=2.77, hw=0.2, ht=0.2),
              S(-6.0, yc=2.55, hw=0.34, ht=0.34),
              S(-2.8, yc=2.32, hw=0.5, ht=0.52),
              S(-1.8, yc=2.3, hw=0.82, ht=1.05, hb=0.95, pt=2.2, pb=2.8, kt=0.62),
              S(-0.6, yc=2.3, hw=0.95, ht=1.32, hb=1.3, pt=2.3, pb=3.2, kt=0.55),
              S(2.2, yc=2.3, hw=0.95, ht=1.32, hb=1.3),
              S(3.0, yc=2.2, hw=0.92, ht=1.05, hb=1.2, kt=0.6),
              S(4.2, yc=2.05, hw=0.85, ht=0.82, hb=1.08, kt=0.62),
              S(5.05, yc=1.92, hw=0.78, ht=0.62, hb=0.98, kt=0.66),
              S(5.6, yc=1.82, hw=0.72, ht=0.55, hb=0.88),
              S(6.6, yc=1.62, hw=0.6, ht=0.4, hb=0.66, kt=0.7),
              S(7.25, yc=1.5, hw=0.45, ht=0.33, hb=0.5),
              S(7.65, yc=1.45, hw=0.2, ht=0.18, hb=0.24),
              S(7.75, yc=1.45, hw=0.03, ht=0.03, hb=0.03)], n=24)
    holes = [(3.05, 4.95), (5.15, 6.7)]
    zs = B.zs(0.5, extra=[3.05, 3.5, 3.55, 4.45, 4.5, 4.95, 5.15, 5.6, 5.65, 6.15, 6.2, 6.7])
    skin, lining = cockpit_skin(B, zs, holes, 9, 15)
    body.add(skin)
    body.add(lining)
    body.add(bubble(B, zs, 3.05, 4.95, 9, 15, 0.42, frames=[(3.5, 3.55), (4.45, 4.5)], front=0.4, back=0.2))
    body.add(bubble(B, zs, 5.15, 6.7, 9, 15, 0.45, frames=[(5.6, 5.65), (6.15, 6.2)], front=0.45, back=0.25))
    # The troop cabin's windows and door (painted frame, dark glass).
    win = Geo(30)
    for s in (-1, 1):
        for z in (0.2, 0.75, 1.3):
            th = B.theta_at(z, 2.35, s)
            p = B.surf(z, th, 0.0)
            nrm = K.normalize(np.array([p[0], 0.0, 0.0]))
            win.extend(geom.lathe(p - nrm * 0.03, p + nrm * 0.03, [(0, 0.17, 'a_frame'), (0.05, 0.16, 'a_frame'), (0.06, 0.13, 'glass_dark'),
                                                                 (0.06, 0.0, 'glass_dark')], 'a_frame', 10, 60))
    body.add(win)
    # Engines on the shoulders: intakes with their spinners, exhausts turned out.
    for s in (-1, 1):
        N = Body([S(-1.3, yc=3.1, hw=0.24, ht=0.22, x0=s * 0.72), S(-0.6, yc=3.12, hw=0.36, ht=0.34), S(2.9, yc=3.22, hw=0.42, ht=0.4),
                  S(3.3, yc=3.22, hw=0.38, ht=0.36)], n=14)
        body.add(N.skin(N.zs(0.6), 'skin'))
        c0 = np.array([s * 0.72, 3.22, 3.3])
        body.add(geom.lathe(c0, c0 + (0, 0, 0.12), [(0, 0.38, 'a_intake'), (0.02, 0.3, 'a_intake'), (0.0, 0.3, 'a_intake'),
                                                    (0.1, 0.12, 'a_metal'), (0.12, 0.0, 'a_metal')], 'a_intake', 12))
        e0 = np.array([s * 1.0, 3.12, -0.45])
        body.add(geom.lathe(e0, e0 + np.array([s * 0.35, -0.05, -0.3]), [(0, 0.2, 'a_soot'), (0.42, 0.24, 'a_soot'), (0.42, 0.17, 'a_black'),
                                                                       (0.2, 0.0, 'a_black')], 'a_soot', 12))
        A.stains.append(((s * 1.2, 3.05, -0.6), (s * 0.7, 2.5, -6.0), 0.25, 0.7, (30, 28, 26), 0.7, True))
    # The gun pod on the right, its twin barrels; the sensor and missile guidance pods under the chin.
    gp = Body([S(3.4, yc=1.62, hw=0.06, ht=0.08, x0=0.93), S(3.6, yc=1.68, hw=0.15, ht=0.16), S(4.6, yc=1.7, hw=0.16, ht=0.17),
               S(4.9, yc=1.7, hw=0.11, ht=0.11)], n=12)
    body.add(gp.skin(gp.zs(0.4), 'skin', cap1=True))
    for x in (0.86, 1.0):
        body.add(geom.cyl((x, 1.7, 4.6), (x, 1.7, 6.1), 0.035, 'a_dark', n=6))
        body.add(geom.cyl((x, 1.7, 5.95), (x, 1.7, 6.12), 0.05, 'a_dark', n=6))
    body.add(geom.lathe((0, 1.18, 6.55), (0, 1.18, 7.0), [(0, 0.2, 'a_darkgrey'), (0.3, 0.2, 'a_darkgrey'), (0.42, 0.14, 'a_sensor'),
                                                         (0.45, 0.0, 'a_sensor')], 'a_darkgrey', 12))
    body.add(geom.lathe((-0.45, 1.25, 6.1), (-0.45, 1.25, 6.6), [(0, 0.12, 'a_darkgrey'), (0.35, 0.12, 'a_darkgrey'), (0.5, 0.0, 'a_sensor')],
                        'a_darkgrey', 10))
    # Tail boom fin (tail rotor on its left), the stabiliser.
    body.add(K.fin_surface((2.7, -8.55), (4.55, -9.6), 1.35, 0.8, t=0.15, steps=3))
    for s in (-1, 1):
        body.add(K.wing((s * 0.25, 2.48, -6.75), (s * 1.5, 2.48, -6.85), 0.7, 0.55, 0.12, 0.1, steps=2))
    tail_rotor(body, (-0.2, 4.0, -9.25), 1.65, 3, chord=0.26, side=-1)
    body.add(geom.tube([(0, 2.45, -8.4), (0, 1.95, -8.8), (0, 1.85, -9.1)], 0.035, 'a_gear', n=6))
    # Stub wings drooping down; pylons with B-8 pods; the tip launchers with Shturm tubes.
    body.add(stub_wing(body, 0.8, 3.45, 2.12, 1.78, 1.3, 1.2, 1.55, 1.35, t=0.14, steps=3))
    for s in (-1, 1):
        for k, x in enumerate((1.55, 2.45)):
            yw = 2.12 - (x - 0.8) / 2.65 * 0.34
            body.add(K.pylon(s * x, yw, 1.62 - k * 0.1, 0.05, 0.95, 'skin', w=0.08))
            body.add(K.rocket_pod((s * x, 1.4 - k * 0.1, 0.4), 1.75, 0.46, 'a_pod'))
        body.add(geom.rbox((s * 3.45 - 0.05, 1.5, -0.35), (s * 3.45 + 0.05, 1.95, 1.2), 'skin', r=0.03))
        for dy in (-0.15, 0.15):
            c = np.array([s * 3.58, 1.62 + dy, 0.3])
            body.add(geom.cyl(c - (0, 0, 0.9), c + (0, 0, 0.9), 0.075, 'a_olive', n=8))
    # Landing gear.
    body.add(K.strut((0, 1.1, 6.05), (0, 0.32, 6.1), 0.055))
    body.add(geom.cyl((-0.3, 0.3, 6.1), (0.3, 0.3, 6.1), 0.03, 'a_gear', n=6))
    wheel_part(m, 'gear_nose_a', (-0.17, 0.3, 6.1), 0.3, 0.16)
    wheel_part(m, 'gear_nose_b', (0.17, 0.3, 6.1), 0.3, 0.16)
    for s in (-1, 1):
        x = s * 1.35
        body.add(K.strut((s * 0.95, 1.35, 0.25), (x, 0.42, 0.3), 0.07))
        body.add(geom.tube([(s * 0.8, 1.0, 0.9), (x, 0.42, 0.3)], 0.045, 'a_gear', n=6))
        body.add(geom.tube([(s * 0.8, 0.95, -0.4), (x, 0.42, 0.3)], 0.045, 'a_gear', n=6))
        wheel_part(m, 'gear_l' if s < 0 else 'gear_r', (x, 0.4, 0.3), 0.4, 0.26)
    body.add(geom.sphere((-1.0, 3.2, -0.2), 0.05, 'a_nav_red', n=6, rings=4))
    body.add(geom.sphere((1.0, 3.2, -0.2), 0.05, 'a_nav_green', n=6, rings=4))
    main_rotor(body, (0, 4.05, 1.0), 8.6, 5, 0.58, droop=0.38, hub_r=0.45, model=m, mast_h=0.5)
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    g.extend(K.seat((0, 2.2, 3.7), 0.46, ejection=False))
    g.extend(K.stick((0, 1.95, 4.1)))
    g.extend(K.instrument_panel(4.55, 2.55, 2.95, 0.42))
    g.extend(K.seat((0, 1.58, 5.5), 0.44, ejection=False))
    g.extend(K.instrument_panel(6.3, 1.95, 2.25, 0.36))
    g.extend(geom.rbox((-0.12, 1.95, 5.9), (0.12, 2.25, 6.3), 'a_coaming', r=0.03))
    ck.add(g)
    for z in list(np.arange(-1.6, 3.0, 0.55)) + [-2.8, -5.0, -7.5]:
        A.lines.append((B.section_line(z), 0.016, True))
    for th in (-math.pi / 2 - 0.5, -math.pi / 2 + 0.5, -math.pi * 1.5 + 0.7, -math.pi * 1.5 - 0.7):
        A.lines.append((B.long_line(th, -2.6, 7.0), 0.014, True))
    for s in (-1, 1):
        thA = B.theta_at(0.5, 1.2, s)
        thB = B.theta_at(0.5, 2.7, s)
        for z in (-0.3, 1.6):
            A.lines.append((B.section_line(z, thA, thB, k=12), 0.02, False))
        m.decals.append(K.number_decal((s * 0.4, 2.48, -4.6), (s, 0, 0), 1.3, 0.55, '15', fg=(222, 186, 60)))
    return m


def star_img(px=64, fill=(190, 30, 30), edge=(240, 240, 240), edge2=None):
    """A five point star (the Russian one: red, with a light edge) as an RGBA image."""
    img = np.zeros((px, px, 4))
    yy, xx = np.mgrid[0:px, 0:px]
    x = (xx - px / 2 + 0.5) / (px / 2)
    y = -(yy - px / 2 + 0.5) / (px / 2)

    def inside(R, r):
        pts = []
        for k in range(10):
            a = math.pi / 2 + k * math.pi / 5
            rr = R if k % 2 == 0 else r
            pts.append((rr * math.cos(a), rr * math.sin(a)))
        m = np.zeros(x.shape, dtype=bool)
        j = len(pts) - 1
        for i in range(len(pts)):
            xi, yi = pts[i]
            xj, yj = pts[j]
            cond = ((yi > y) != (yj > y)) & (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi)
            m ^= cond
            j = i
        return m
    big = inside(0.98, 0.4)
    img[big, :3] = edge
    img[big, 3] = 255
    sm = inside(0.82, 0.33)
    img[sm, :3] = fill
    return img


def models():
    out = [mi8(), mi24()]
    for mod in ('m_heli2', 'm_jets', 'm_navy', 'm_planes'):
        try:
            import importlib
            out += importlib.import_module(mod).models()
        except ImportError as e:
            if mod not in str(e):
                raise
    return out
