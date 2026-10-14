"""2.0: the boats (Gyurza, Raptor, RHIB) and the ships (Buyan-M, Visby) as meshes. Waterline at y = 0, z forward."""
import math

import numpy as np

import geom
import materials
import kit_air as K
from core import Geo, Model
from kit_air import Body, orient
from m_air import S, star_img
from m_jets import T


# ---------------------------------------------------------------------------------------------------------------
# Pieces


def hull_body(L, beam, deck, deck_bow, draft, bow_frac=0.3, kt=1.0, pb=2.6, pt=5.0, transom=0.9, flare=1.12, n=36, stern_keel=1.0):
    """A smooth hull through stations (z, half beam, deck, keel): the transom at -L/2, the bow rising to the stem."""
    z0, z1 = -L / 2, L / 2
    hb = beam / 2
    zb = z1 - L * bow_frac
    rows = []

    def add(z, w, d, k, kk=kt):
        rows.append(T(z, d, -k, w, pt=pt, pb=pb, kt=kk))
    add(z0, hb * transom, deck, draft * stern_keel)
    add(z0 + L * 0.03, hb * 0.97, deck, draft)
    add(z0 + L * 0.12, hb, deck, draft)
    add((z0 + zb) / 2, hb, deck, draft)
    add(zb, hb, deck, draft)
    for t in (0.18, 0.36, 0.54, 0.7, 0.82, 0.92, 0.975, 1.0):
        w = hb * max(0.03, (1 - t ** 1.7)) ** 0.8
        d = deck + (deck_bow - deck) * t ** 1.4
        k = draft * max(0.0, 1 - t ** 0.8)
        add(zb + (z1 - zb) * t, w, d, max(k, 0.08), kk=kt * (1 + (flare - 1) * math.sin(math.pi * min(t, 1.0) * 0.8)))
    return Body(rows, n=n), z0, z1, zb


def hull_skin(B, step, mats_extra=None, zextra=()):
    zs = B.zs(step, extra=list(zextra))

    def mats(z, th, k, i):
        if math.sin(th) > 0.82:
            return 'a_deck'
        return 'skin'
    g = B.skin(zs, 'skin', mats=mats, smooth=55.0)
    cap = geom.polygon_cap(B.ring(zs[0]), 'skin')
    orient(cap, lambda c: np.array([0, 0, -1.0]))
    g.extend(cap)
    return g, zs


def block(x0, x1, y0, y1, z0, z1, mat='skin', dx=0.0, dzf=0.0, dzb=0.0, dy=0.0):
    """A block whose top is smaller than its base (dx each side, dzf at the front, dzb at the back): a faceted superstructure."""
    def rect(x0, x1, y, za, zb):
        return [np.array(p, dtype=np.float64) for p in ((x0, y, za), (x1, y, za), (x1, y, zb), (x0, y, zb))]
    r0 = rect(x0, x1, y0, z0, z1)
    r1 = rect(x0 + dx, x1 - dx, y1, z0 + dzb, z1 - dzf)
    g = geom.loft([r0, r1], mat, closed=True, smooth=40.0)
    g.extend(geom.polygon_cap(r1, mat))
    g.extend(geom.polygon_cap(r0, mat))
    c = np.array([(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2])
    orient(g, lambda p: p - c)
    return g


def cabin(x0, x1, z0, z1, y0, y1, sill=0.9, rake=0.5, rake_b=0.0, mat='skin', nf=3, ns=3, glass='a_glass_ship'):
    """A wheelhouse with real window openings: a sill block, the roof, slender posts, glass panes between them (the front
    raked back by {rake}). The deck of the hull is its floor."""
    g = Geo(40)
    ys = y0 + sill
    yt = y1 - 0.16
    g.extend(geom.rbox((x0, y0, z0), (x1, ys, z1), mat, r=0.05))
    g.extend(geom.rbox((x0 - 0.12, yt, z0 - 0.12 + rake_b), (x1 + 0.12, y1, z1 + 0.1 - rake * 0.2), mat, r=0.05))

    def zf(y):
        return z1 - rake * (y - ys) / (yt - ys)

    def zb_(y):
        return z0 + rake_b * (y - ys) / (yt - ys)
    xs = np.linspace(x0, x1, nf + 1)
    for x in xs:
        g.extend(geom.cyl((x, ys, zf(ys)), (x, yt, zf(yt)), 0.045, mat, n=6))
    for i in range(nf):
        a, b = xs[i], xs[i + 1]
        g.extend(geom.quad((a, ys, zf(ys)), (b, ys, zf(ys)), (b, yt, zf(yt)), (a, yt, zf(yt)), glass, double=True))
    zsd = np.linspace(z0, z1, ns + 1)
    for x in (x0, x1):
        for z in zsd[1:-1]:
            g.extend(geom.cyl((x, ys, z), (x, yt, z), 0.045, mat, n=6))
        for i in range(ns):
            za, zb = zsd[i], zsd[i + 1]
            ya = zf(ys) if i == ns - 1 else None
            if i == ns - 1:
                p = [(x, ys, za), (x, ys, zf(ys)), (x, yt, zf(yt)), (x, yt, za)]
            else:
                p = [(x, ys, za), (x, ys, zb), (x, yt, zb), (x, yt, za)]
            g.extend(geom.quad(*p, glass, double=True))
        g.extend(geom.cyl((x, ys, zf(ys)), (x, yt, zf(yt)), 0.05, mat, n=6))
    # The back wall.
    g.extend(geom.box((x0, ys, z0), (x1, yt, z0 + 0.06), mat))
    return g


def console(g, z, ys, hw, seats=((-0.5, 0.0),), y_floor=None):
    """The helm inside: the panel facing aft at z, the seats."""
    g.extend(K.instrument_panel(z, ys, ys + 0.45, hw))
    g.extend(geom.rbox((-hw, ys - 0.5, z - 0.5), (hw, ys + 0.02, z + 0.1), 'a_coaming', r=0.04))
    for x, dz in seats:
        g.extend(K.seat((x, ys - 0.45, z - 1.1 + dz), 0.46, ejection=False))


def cannon(el, pivot, length, dia, mat='a_dark', brake=True, shroud=0.0):
    """A gun barrel from the cradle forward (along +z)."""
    x, y, z = pivot
    g = Geo(40)
    prof = [(0, dia * 1.5, mat), (0.35, dia * 1.0, mat), (length * 0.2, dia * 0.95, mat), (length * 0.45, dia * 0.8, mat),
            (length * 0.96, dia * 0.72, mat)]
    if brake:
        prof += [(length * 0.96, dia * 0.95, mat), (length, dia * 0.95, 'a_black'), (length, 0, 'a_black')]
    else:
        prof += [(length, 0.0, 'a_black')]
    g.extend(geom.lathe((x, y, z), (x, y, z + length), prof, mat, 14, 50))
    if shroud:
        g.extend(geom.lathe((x, y, z + 0.4), (x, y, z + 0.4 + shroud), [(0, dia * 1.1, mat), (0.05, dia * 1.5, mat), (shroud - 0.05, dia * 1.5, mat),
                                                                         (shroud, dia * 1.0, mat), (shroud, 0, mat)], mat, 12, 50))
    el.add(g)


def lifelines(body, z0, z1, y, hb, post=3.0, h=0.95):
    g = Geo(50)
    for s in (-1, 1):
        g.extend(geom.tube([(s * hb, y + h * 0.5, z0), (s * hb, y + h * 0.5, z1)], 0.012, 'a_metal', n=4, caps=False))
        g.extend(geom.tube([(s * hb, y + h, z0), (s * hb, y + h, z1)], 0.014, 'a_metal', n=4, caps=False))
        for z in np.arange(z0, z1 + 0.01, post):
            g.extend(geom.cyl((s * hb, y, z), (s * hb, y + h, z), 0.022, 'a_metal', n=4))
    body.add(g)


def portholes(body, side, x, z0, z1, y, n, r=0.16, mat='glass_dark', frame='a_frame'):
    """A row of small dark windows (single quads) on a side wall at x facing {side}."""
    g = Geo(30)
    for z in np.linspace(z0, z1, n):
        g.extend(geom.panel((x + side * 0.012, y, z), r * 2.0, r * 1.5, (0, 0, -side), (0, 1, 0), mat))
    body.add(g)


def windows_band(body, c, w, h, u, v, mat='glass_dark'):
    body.add(geom.panel(c, w, h, u, v, mat, double=True))


def waterjets(body, z0, xs, y=-0.4, r=0.5):
    for x in xs:
        body.add(geom.cyl((x, y, z0 - 0.15), (x, y, z0 + 0.05), r, 'a_black', n=12))
        body.add(geom.cyl((x, y, z0 - 0.12), (x, y, z0 + 0.02), r * 0.82, 'a_soot', n=12))


def ship_scheme(base, seed, dirt=0.35, gloss=0.3):
    sch = materials.Scheme(base=base, camo=None, blob=3.0, stretch=1.0, dirt=dirt, wear=0.35, seed=seed, gloss=gloss)
    return sch


def antiship_air(A, boot=0.3, antifoul=0x6E2E26):
    A.waterline = 0.0
    A.boot = boot
    A.antifoul = materials.hexc(antifoul)


# ---------------------------------------------------------------------------------------------------------------
# Gyurza-M


def gyurza():
    m = Model('gyurza', ship_scheme(0x6E7A72, 21), density=16, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    antiship_air(A, 0.25)
    m.deck_colour = 0x4F5650
    body = m.part('body')
    B, z0, z1, zb = hull_body(23.0, 4.8, 1.6, 2.0, 0.6, bow_frac=0.3, pb=3.0, pt=7.0, transom=0.95)
    hull, zs = hull_skin(B, 1.4)
    body.add(hull)
    lifelines(body, z0 + 0.5, zb - 1.0, 1.6, 2.3, post=2.0, h=0.8)
    # The superstructure: the engine casing, the raked wheelhouse over it, the mast.
    body.add(block(-1.9, 1.9, 1.6, 3.6, -6.0, 4.0, dx=0.25, dzf=0.9, dzb=0.5))
    body.add(cabin(-1.6, 1.6, -3.0, 2.5, 3.6, 5.2, sill=0.65, rake=0.7, nf=3, ns=3))
    for s in (-1, 1):
        portholes(body, s, s * (1.9 - 0.12 * 1), -4.8, 2.8, 2.7, 6, r=0.13)
        body.add(geom.rbox((s * 1.9 - 0.3, 3.0, -5.9), (s * 1.9 + 0.3, 3.15, -5.3), 'a_darkgrey', r=0.03))
    # Hatches and exhausts on the casing roof, the stern: the remote weapon station, the davit.
    body.add(geom.rbox((-0.6, 3.6, -5.5), (0.6, 3.9, -3.5), 'skin', r=0.06))
    body.add(geom.cyl((0.9, 3.6, -5.2), (0.9, 4.3, -5.2), 0.17, 'a_soot', n=8))
    body.add(geom.cyl((-0.9, 3.6, -5.2), (-0.9, 4.3, -5.2), 0.17, 'a_soot', n=8))
    body.add(geom.rbox((-1.4, 1.6, -10.8), (1.4, 1.9, -6.2), 'a_deck', r=0.05))
    body.add(geom.rbox((-0.5, 1.9, -9.8), (0.5, 2.4, -7.8), 'skin', r=0.1))
    for s in (-1, 1):
        body.add(geom.cyl((s * 2.0, 1.6, -9.0), (s * 2.0, 3.0, -9.0), 0.05, 'a_gear', n=6))
        body.add(geom.tube([(s * 2.0, 3.0, -9.0), (s * 1.0, 3.0, -9.0)], 0.035, 'a_gear', n=6))
    # Mast with radar and antennas.
    body.add(geom.cyl((0, 5.2, -1.0), (0, 7.3, -1.0), 0.09, 'a_darkgrey', n=8))
    body.add(geom.rbox((-0.8, 6.8, -1.1), (0.8, 6.9, -0.85), 'a_darkgrey', r=0.02))
    body.add(geom.rbox((-0.45, 5.35, -0.4), (0.45, 5.75, 0.3), 'a_darkgrey', r=0.08))
    body.add(geom.cyl((0, 5.75, -0.05), (0, 5.95, -0.05), 0.2, 'a_darkgrey', n=10))
    body.add(geom.tube([(0.7, 5.2, -2.2), (0.9, 6.6, -2.4)], 0.012, 'a_dark', n=4))
    body.add(geom.sphere((0.0, 6.0, 1.9), 0.18, 'a_lamp', n=8, rings=5))
    for s in (-1, 1):
        body.add(geom.sphere((s * 1.7, 4.2, 2.4), 0.06, 'a_nav_red' if s < 0 else 'a_nav_green', n=6, rings=4))
    # The bow: the anchor, the bollards.
    body.add(geom.rbox((-0.5, 1.6, 8.4), (0.5, 1.9, 9.2), 'a_darkgrey', r=0.05))
    # The turret with the 30 mm.
    tur = m.part('turret', (0, 1.6, 6.5))
    tg = Body([T(5.6, 2.3, 1.6, 0.6, pt=4, pb=4), T(6.0, 2.46, 1.6, 0.9), T(7.0, 2.46, 1.6, 0.92), T(7.4, 2.3, 1.6, 0.7)], n=16)
    tur.add(tg.skin(tg.zs(0.4), 'skin', cap0=True, cap1=True))
    for s in (-1, 1):
        tur.add(geom.rbox((s * 0.95, 1.8, 6.0), (s * 0.95 + s * 0.28, 2.3, 6.9), 'a_darkgrey', r=0.04))
    tur.add(geom.rbox((-0.3, 2.46, 5.9), (0.3, 2.74, 6.5), 'a_darkgrey', r=0.06))
    tur.add(geom.cyl((0.4, 2.46, 6.2), (0.4, 2.62, 6.2), 0.12, 'a_sensor', n=8))
    gun = tur.part('gun', (0, 2.1, 7.4))
    gun.add(geom.rbox((-0.3, 1.85, 7.25), (0.3, 2.4, 7.8), 'a_darkgrey', r=0.06))
    cannon(gun, (0, 2.1, 7.75), 2.6, 0.075, shroud=0.7)
    cannon(gun, (0.22, 1.98, 7.75), 1.4, 0.03, brake=False)
    # The wheelhouse inside.
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    console(g, 2.0, 4.25, 1.4, seats=((-0.6, 0.0), (0.6, 0.0)))
    ck.add(g)
    for z in np.arange(-10.0, 9.0, 2.0):
        A.lines.append((B.section_line(z), 0.02, True))
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 2.35, 1.1, 6.0), (s, 0, 0), 1.6, 0.6, '58', fg=(235, 235, 230)))
    waterjets(body, z0, (-1.2, 1.2), -0.2, 0.35)
    return m


# ---------------------------------------------------------------------------------------------------------------
# Raptor


def raptor():
    m = Model('raptor', ship_scheme(0xA2A8AA, 3, dirt=0.3), density=16, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    antiship_air(A, 0.2)
    m.deck_colour = 0x6A7076
    body = m.part('body')
    B, z0, z1, zb = hull_body(16.9, 4.1, 1.4, 1.8, 0.6, bow_frac=0.34, pb=3.6, pt=7.0, transom=0.95)
    hull, zs = hull_skin(B, 1.2)
    body.add(hull)
    lifelines(body, z0 + 0.3, zb - 0.5, 1.4, 1.95, post=1.8, h=0.7)
    # The wheelhouse forward of amidships, the open aft cockpit with its bench.
    body.add(block(-1.7, 1.7, 1.4, 2.3, -4.0, 2.5, dx=0.2, dzf=0.6, dzb=0.3))
    body.add(cabin(-1.6, 1.6, -3.0, 2.2, 2.3, 3.2, sill=0.45, rake=0.6, nf=3, ns=2))
    body.add(geom.rbox((-1.0, 1.4, -7.0), (1.0, 1.7, -4.2), 'a_deck', r=0.05))
    body.add(geom.rbox((-0.9, 1.7, -7.8), (0.9, 2.1, -6.9), 'skin', r=0.08))
    for s in (-1, 1):
        body.add(geom.rbox((s * 1.7 - 0.2, 2.0, -3.7), (s * 1.7 + 0.2, 2.25, -3.2), 'a_darkgrey', r=0.04))
        portholes(body, s, s * 1.7, -3.4, 1.5, 1.85, 4, r=0.11)
    # Mast, antennas, radar.
    body.add(geom.cyl((0, 3.2, -1.0), (0, 4.8, -1.0), 0.07, 'a_darkgrey', n=8))
    body.add(geom.rbox((-0.5, 3.2, -1.15), (0.5, 3.3, -0.85), 'a_darkgrey', r=0.02))
    body.add(geom.rbox((-0.4, 4.2, -1.12), (0.4, 4.28, -0.88), 'a_darkgrey', r=0.02))
    body.add(geom.tube([(0.5, 3.2, -0.2), (0.7, 4.4, -0.4)], 0.01, 'a_dark', n=4))
    body.add(geom.cyl((0.0, 3.2, 0.6), (0.0, 3.4, 0.6), 0.16, 'a_darkgrey', n=10))
    # Fenders and the bow cleats.
    for s in (-1, 1):
        body.add(geom.tube([(s * 2.04, 1.0, -3.0), (s * 2.04, 1.0, 2.0)], 0.07, 'a_rust', n=6))
        body.add(geom.sphere((s * 1.5, 3.2, 1.8), 0.05, 'a_nav_red' if s < 0 else 'a_nav_green', n=6, rings=4))
    waterjets(body, z0, (-0.9, 0.9), -0.2, 0.3)
    # The turret with the 14.5 mm KPVT on the bow.
    tur = m.part('turret', (0, 1.4, 4.6))
    tg = Body([T(4.0, 2.0, 1.4, 0.5, pt=4, pb=4), T(4.3, 2.1, 1.4, 0.62), T(4.9, 2.1, 1.4, 0.62), T(5.2, 2.0, 1.4, 0.5)], n=14)
    tur.add(tg.skin(tg.zs(0.3), 'skin', cap0=True, cap1=True))
    tur.add(geom.rbox((-0.25, 2.0, 4.0), (0.25, 2.2, 4.5), 'a_darkgrey', r=0.04))
    gun = tur.part('gun', (0, 1.8, 5.2))
    gun.add(geom.rbox((-0.2, 1.62, 5.05), (0.2, 2.0, 5.5), 'a_darkgrey', r=0.05))
    cannon(gun, (0, 1.8, 5.45), 1.6, 0.06, shroud=0.5)
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    console(g, 1.8, 2.75, 1.3, seats=((-0.6, 0.0), (0.6, 0.0)))
    ck.add(g)
    for z in np.arange(-7.0, 5.0, 1.8):
        A.lines.append((B.section_line(z), 0.02, True))
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 2.02, 0.95, 3.0), (s, 0, 0), 1.2, 0.5, '03160', fg=(40, 44, 50)))
    return m


# ---------------------------------------------------------------------------------------------------------------
# RHIB


def rhib():
    m = Model('rhib', ship_scheme(0x4A4D48, 5, dirt=0.3), density=40, tex_width=1024)
    m.ground = False
    m.air = A = K.Air()
    antiship_air(A, 0.0, 0x40443F)
    A.boot = 0.0
    body = m.part('body')
    # The rigid hull: a deep-V with a round bilge, under the tubes.
    B = Body([T(-3.9, 0.22, -0.3, 0.7, pt=3, pb=3.2), T(-3.0, 0.24, -0.4, 0.82), T(-0.5, 0.26, -0.5, 0.86), T(1.5, 0.28, -0.4, 0.8),
              T(2.8, 0.34, -0.2, 0.55), T(3.6, 0.4, 0.0, 0.25), T(3.95, 0.45, 0.2, 0.05)], n=20)
    zs = B.zs(0.7)
    body.add(B.skin(zs, 'a_dark', mats=lambda z, th, k, i: 'a_deck' if math.sin(th) > 0.7 else 'a_dark', cap0=True))
    # The tube: a ring along the planform, thicker amidships and tapering to the points at the stern.
    path = []
    for z in np.linspace(-3.95, 2.0, 12):
        path.append((-1.03, 0.43, z))
    for a in np.linspace(math.pi, 0, 14)[1:-1]:
        t = (math.pi - a) / math.pi
        path.append((1.03 * math.cos(a), 0.43 + 0.1 * math.sin(math.pi * t), 2.0 + 2.0 * math.sin(math.pi * t) * (0.55 + 0.45 * (1 - t))))
    path = path[:12] + path[12:]
    right = [(-p[0], p[1], p[2]) for p in reversed(path[:12])]
    path = path + right
    pts = np.array(path, dtype=np.float64)
    # Smooth the path a little and put rings of radius r(t) round it.
    rings = []
    nseg = len(pts)
    for i, p in enumerate(pts):
        tdir = pts[min(i + 1, nseg - 1)] - pts[max(i - 1, 0)]
        tdir = tdir / (np.linalg.norm(tdir) + 1e-9)
        a = np.cross(tdir, (0, 1, 0))
        if np.linalg.norm(a) < 1e-6:
            a = np.array([1.0, 0, 0])
        a /= np.linalg.norm(a)
        b = np.cross(a, tdir)
        s = i / (nseg - 1)
        r = 0.36 * min(1.0, 0.25 + 3.2 * min(s, 1 - s) ** 0.8)
        r = max(r, 0.03)
        rings.append([p + a * r * math.cos(t) + b * r * math.sin(t) for t in np.linspace(0, 2 * math.pi, 14, endpoint=False)])
    tube = geom.loft(rings, 'a_tube', closed=True, smooth=60.0, cap0=True, cap1=True)
    c = np.array([0, 0.43, 0.0])

    def outw(p):
        i = int(np.argmin([np.linalg.norm(p - q) for q in pts]))
        return p - pts[i]
    orient(tube, outw)
    body.add(tube)
    # The floor, the bench seat, the console with its windscreen, the grab line.
    body.add(geom.rbox((-0.78, 0.18, -3.6), (0.78, 0.26, 2.4), 'a_wood', r=0.02))
    body.add(geom.rbox((-0.5, 0.2, -2.4), (0.5, 0.65, -1.4), 'a_tube', r=0.1))
    body.add(geom.rbox((-0.45, 0.2, -1.2), (0.45, 0.85, -0.5), 'skin', r=0.08))
    body.add(geom.panel((0, 1.1, -0.48), 0.8, 0.4, (-1, 0, 0), (0, 1, 0), 'a_glass_ship', double=True))
    body.add(geom.rbox((-0.45, 0.85, -0.55), (0.45, 1.0, -0.4), 'a_darkgrey', r=0.04))
    body.add(geom.tube([(-0.9, 0.62, -3.0), (-0.9, 0.62, 2.5)], 0.015, 'a_black', n=4, caps=False))
    body.add(geom.tube([(0.9, 0.62, -3.0), (0.9, 0.62, 2.5)], 0.015, 'a_black', n=4, caps=False))
    # The outboard on the transom.
    body.add(geom.rbox((-0.3, 0.3, -4.6), (0.3, 1.0, -4.0), 'a_darkgrey', r=0.12))
    body.add(geom.rbox((-0.08, -0.55, -4.45), (0.08, 0.3, -4.15), 'a_darkgrey', r=0.03))
    body.add(geom.cyl((-0.14, -0.5, -4.5), (0.14, -0.5, -4.5), 0.07, 'a_metal', n=8))
    body.add(geom.rbox((-0.25, 0.15, -4.1), (0.25, 0.7, -3.98), 'a_black', r=0.02))
    # The pintle mount and the M2 on the bow.
    tur = m.part('turret', (0, 0.2, 1.8))
    tur.add(geom.cyl((0, 0.2, 1.8), (0, 1.0, 1.8), 0.05, 'a_black', n=8))
    tur.add(geom.cyl((0, 0.2, 1.8), (0, 0.32, 1.8), 0.2, 'a_darkgrey', n=12))
    tur.add(geom.rbox((-0.25, 0.9, 1.7), (0.25, 1.0, 1.9), 'a_darkgrey', r=0.02))
    gun = tur.part('gun', (0, 1.25, 1.8))
    gun.add(geom.rbox((-0.07, 1.15, 1.45), (0.07, 1.35, 1.9), 'a_black', r=0.02))
    gun.add(geom.rbox((0.1, 1.05, 1.55), (0.3, 1.22, 1.85), 'a_olive', r=0.02))
    gun.add(geom.rbox((-0.28, 1.1, 1.8), (-0.12, 1.4, 1.84), 'a_darkgrey', r=0.01))
    cannon(gun, (0, 1.25, 1.9), 1.1, 0.035, shroud=0.45)
    m.decals.append(K.number_decal((1.1, 0.5, -0.5), (1, 0, 0), 1.0, 0.35, '07', fg=(200, 200, 195)))
    return m


# ---------------------------------------------------------------------------------------------------------------
# Buyan-M


def duet(g, x, y, z, s=1.0):
    """The AK-630M2 Duet: a round mount, the gun housings, the twin barrels."""
    g.extend(geom.cyl((x, y - 0.3, z), (x, y + 0.2, z), 0.8, 'skin', n=16, bevel=0.06))
    g.extend(geom.rbox((x - 0.65, y + 0.15, z - 0.7), (x + 0.65, y + 1.25, z + 0.5), 'skin', r=0.18))
    g.extend(geom.rbox((x - 0.5, y + 0.85, z - 0.5), (x + 0.5, y + 1.25, z + 0.2), 'a_darkgrey', r=0.05))
    for dx in (-0.3, 0.3):
        g.extend(geom.cyl((x + dx, y + 0.75, z + 0.5), (x + dx, y + 0.75, z + 2.3), 0.12, 'a_dark', n=8))
        g.extend(geom.cyl((x + dx, y + 0.75, z + 2.1), (x + dx, y + 0.75, z + 2.35), 0.16, 'a_black', n=8))
        g.extend(geom.cyl((x + dx, y + 0.75, z + 0.5), (x + dx, y + 0.75, z + 1.3), 0.19, 'a_darkgrey', n=8))
    return g


def lattice(g, x, y0, y1, z, hw, mat='a_darkgrey'):
    for sx in (-1, 1):
        for sz in (-1, 1):
            g.extend(geom.cyl((x + sx * hw, y0, z + sz * hw), (x + sx * hw * 0.3, y1, z + sz * hw * 0.3), 0.045, mat, n=5))


def buyan_m():
    m = Model('buyan_m', ship_scheme(0x8E9AA2, 31), density=6, tex_width=1024)
    m.ground = False
    m.vls_grid = (2, 4)
    m.air = A = K.Air()
    antiship_air(A, 0.35)
    m.deck_colour = 0x5C6064
    deck = 3.2
    body = m.part('body')
    B, z0, z1, zb = hull_body(74.0, 11.0, deck, 4.6, 2.6, bow_frac=0.3, pb=2.8, pt=6.0, transom=0.9)
    hull, zs = hull_skin(B, 3.0, zextra=[z1 - 2, z1 - 5, z1 - 8, z1 - 12, z1 - 17, z1 - 22])
    body.add(hull)
    lifelines(body, z0 + 1.0, zb - 1.0, deck, 5.35, post=3.0, h=1.0)
    # The forecastle with the gun base; the superstructure: two levels and the bridge.
    body.add(geom.rbox((-3.6, deck, 14.0), (3.6, deck + 0.9, 20.0), 'skin', r=0.15))
    body.add(block(-4.4, 4.4, deck, deck + 2.8, -4.0, 13.0, dx=0.3, dzf=1.0, dzb=0.4))
    body.add(block(-4.0, 4.0, deck + 2.8, deck + 5.4, -2.0, 12.0, dx=0.3, dzf=1.2, dzb=0.6))
    body.add(cabin(-3.4, 3.4, 4.4, 11.4, deck + 5.4, deck + 7.6, sill=0.7, rake=0.9, rake_b=0.0, nf=5, ns=4))
    # The bridge wings, the windows on the levels below.
    for s in (-1, 1):
        body.add(geom.rbox((s * 3.4 if s > 0 else s * 3.4 - 1.2, deck + 5.3, 9.0), (s * 3.4 + 1.2 if s > 0 else s * 3.4, deck + 5.5, 11.4), 'skin', r=0.03))
        body.add(geom.tube([(s * 4.6, deck + 5.5, 9.0), (s * 4.6, deck + 6.3, 9.0), (s * 4.6, deck + 6.3, 11.4), (s * 3.4, deck + 6.3, 11.4)],
                           0.015, 'a_metal', n=4, caps=False))
        portholes(body, s, s * 4.0 - s * 0.05 * 0, 0.0, 11.0, deck + 4.2, 8, r=0.18)
        portholes(body, s, s * 4.4 - s * 0.05 * 0, -2.0, 11.5, deck + 1.9, 9, r=0.18)
        body.add(geom.rbox((s * 4.4 - 0.35 if s > 0 else s * 4.4 - 0.05, deck + 0.0, -3.9), (s * 4.4 + 0.05 if s > 0 else s * 4.4 + 0.35, deck + 2.0, -3.2), 'skin', r=0.03))
    windows_band(body, (0, deck + 3.8, 12.0 - 0.4), 7.4, 1.1, (1, 0, 0), (0, 1, 0.08))
    # The pyramid mast with the radars.
    for hw, y0, y1, za, zz in ((2.4, 7.6, 9.8, 2.2, 7.8), (1.8, 9.8, 12.2, 2.9, 7.1), (1.2, 12.2, 14.6, 3.6, 6.4), (0.6, 14.6, 17.6, 4.3, 5.7)):
        body.add(block(-hw, hw, deck + y0 - 0.0, deck + y1, za, zz, dx=hw * 0.15, dzf=0.2, dzb=0.2))
    body.add(geom.rbox((-1.4, deck + 12.6, 6.55), (1.4, deck + 14.2, 6.65), 'a_darkgrey', r=0.04))
    body.add(geom.rbox((-1.3, deck + 12.7, 6.66), (1.3, deck + 14.1, 6.7), 'a_sensor', r=0.0))
    body.add(geom.rbox((-1.2, deck + 15.0, 5.7), (1.2, deck + 16.4, 5.8), 'a_darkgrey', r=0.03))
    body.add(geom.rbox((-2.6, deck + 15.6, 4.9), (2.6, deck + 15.75, 5.1), 'a_darkgrey', r=0.03))
    body.add(geom.cyl((0, deck + 17.6, 5.0), (0, deck + 21.5, 5.0), 0.07, 'a_darkgrey', n=6))
    body.add(geom.cyl((0, deck + 19.0, 5.0), (0, deck + 19.1, 5.0), 0.5, 'a_darkgrey', n=10))
    body.add(geom.tube([(0, deck + 21.5, 5.0), (-0.5, deck + 18.5, 3.6)], 0.01, 'a_dark', n=3, caps=False))
    body.add(geom.sphere((0.0, deck + 9.2, 9.0), 0.75, 'a_radome', n=14, rings=8))
    body.add(geom.cyl((0, deck + 7.6, 9.0), (0, deck + 8.6, 9.0), 0.3, 'a_darkgrey', n=8))
    g = Geo(40)
    lattice(g, 0, deck + 7.6, deck + 12.0, 5.0, 2.0)
    body.add(g)
    # The aft deckhouse with the Duet and the Gibka launcher.
    body.add(block(-3.4, 3.4, deck, deck + 2.4, -22.0, -10.0, dx=0.25, dzf=0.4, dzb=0.4))
    body.add(block(-1.5, 1.5, deck + 2.4, deck + 3.0, -20.0, -17.0, dx=0.2, dzf=0.2, dzb=0.2))
    gg = Geo(40)
    duet(gg, 0.0, deck + 2.6, -19.2)
    body.add(gg)
    body.add(geom.rbox((-0.6, deck + 2.4, -14.5), (0.6, deck + 3.4, -13.3), 'a_darkgrey', r=0.06))
    for s in (-1, 1):
        body.add(geom.cyl((s * 0.4, deck + 3.7, -14.7), (s * 0.4, deck + 3.9, -13.0), 0.14, 'a_olive', n=8))
    # The Kalibr block: eight cells behind the superstructure.
    body.add(geom.rbox((-1.75, deck, -9.0), (1.75, deck + 0.9, -7.0), 'skin', r=0.05))
    body.add(geom.panel((0, deck + 0.905, -8.0), 1.5, 3.0, (0, 0, 1), (1, 0, 0), 'a_vls'))
    # Liferaft canisters, the water jets, the anchors, the hull number.
    for s in (-1, 1):
        for k in range(3):
            body.add(geom.cyl((s * 4.9, deck + 0.7, -24.0 + k * 1.4), (s * 4.9, deck + 0.7, -23.2 + k * 1.4), 0.25, 'a_white', n=10))
        m.decals.append(K.number_decal((s * 5.52, 2.4, 23.0), (s, 0, 0), 3.0, 1.1, '616', fg=(240, 240, 235)))
    waterjets(body, z0, (-2.2, 2.2), -0.4, 0.6)
    body.add(geom.cyl((0, deck, -35.5), (0, deck + 3.0, -35.5), 0.05, 'a_darkgrey', n=6))
    for s in (-1, 1):
        body.add(geom.rbox((s * 2.0 - 0.5, deck + 0.0, z1 - 8.6), (s * 2.0 + 0.5, deck + 0.3, z1 - 8.0), 'a_darkgrey', r=0.04))
    # The A-190: a faceted stealth turret and the long barrel.
    tz = 17.0
    tur = m.part('turret', (0, deck + 0.9, tz))
    tg = Body([T(tz - 1.7, deck + 1.9, deck + 0.9, 1.0, pt=4, pb=4), T(tz - 1.4, deck + 2.9, deck + 0.9, 1.5), T(tz + 0.9, deck + 2.9, deck + 0.9, 1.5),
               T(tz + 1.5, deck + 2.1, deck + 0.9, 1.2), T(tz + 1.7, deck + 1.9, deck + 0.9, 0.9)], n=12)
    tur.add(tg.skin(tg.zs(0.5), 'skin', cap0=True, cap1=True))
    tur.add(geom.rbox((-0.4, deck + 2.9, tz - 1.0), (0.4, deck + 3.25, tz - 0.2), 'a_darkgrey', r=0.06))
    gun = tur.part('gun', (0, deck + 1.7, tz + 1.4))
    gun.add(geom.rbox((-0.45, deck + 1.35, tz + 1.2), (0.45, deck + 2.2, tz + 1.85), 'skin', r=0.08))
    cannon(gun, (0, deck + 1.7, tz + 1.8), 5.6, 0.1, shroud=1.2)
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    console(g, 10.2, deck + 6.0, 3.0, seats=((-1.2, 0.0), (1.2, 0.0)))
    ck.add(g)
    for z in np.arange(-30.0, 28.0, 5.0):
        A.lines.append((B.section_line(z), 0.03, True))
    return m


# ---------------------------------------------------------------------------------------------------------------
# Visby


def visby():
    m = Model('visby', ship_scheme(0x555C60, 32, dirt=0.3), density=7, tex_width=1024)
    m.ground = False
    m.vls_grid = (2, 4)
    m.air = A = K.Air()
    antiship_air(A, 0.3, 0x343A3E)
    m.deck_colour = 0x3A3E42
    deck = 3.4
    body = m.part('body')
    B, z0, z1, zb = hull_body(72.0, 10.4, deck, 4.4, 2.4, bow_frac=0.28, kt=0.78, pb=2.8, pt=7.0, transom=0.9, flare=1.0)
    hull, zs = hull_skin(B, 3.0, zextra=[z1 - 2, z1 - 5, z1 - 8, z1 - 12, z1 - 17])
    body.add(hull)
    # The faceted superstructure leaning in; the bridge windows in a dark band at the front, the radar block.
    body.add(block(-4.0, 4.0, deck, deck + 6.2, -6.0, 18.0, dx=1.1, dzf=4.8, dzb=4.2))
    fy = 5.0
    zfront = lambda y: 18.0 - 4.8 * y / 6.2
    body.add(geom.panel((0, deck + fy + 0.5, (zfront(fy) + zfront(fy + 1.0)) / 2 + 0.03), 5.4, 1.2, (1, 0, 0), (0, 6.2, -4.8), 'glass_dark', double=True))
    for s in (-1, 1):
        xs_ = lambda y: 4.0 - 1.1 * y / 6.2
        body.add(geom.panel((s * (xs_(fy + 0.5) + 0.03), deck + fy + 0.5, 5.7), 14.0, 1.0, (0, 0, -s), (-s * 1.1, 6.2, 0), 'glass_dark', double=True))
    for s in (-1, 1):
        portholes(body, s, s * 4.0 - s * 0.0, -3.5, 14.0, deck + 1.5, 6, r=0.16)
    body.add(block(-2.2, 2.2, deck + 6.2, deck + 7.4, 2.0, 10.0, dx=0.5, dzf=0.8, dzb=0.8))
    body.add(block(-1.4, 1.4, deck + 7.4, deck + 8.6, 3.2, 8.8, dx=0.35, dzf=0.8, dzb=0.8))
    body.add(geom.rbox((-1.42, deck + 7.6, 4.0), (1.42, deck + 8.4, 4.05), 'a_sensor', r=0.0))
    body.add(block(-0.5, 0.5, deck + 8.6, deck + 9.4, 5.4, 6.6, dx=0.1, dzf=0.1, dzb=0.1))
    body.add(geom.cyl((0, deck + 9.4, 6.0), (0, deck + 11.4, 6.0), 0.06, 'a_darkgrey', n=6))
    body.add(geom.cyl((0, deck + 10.4, 6.0), (0, deck + 10.45, 6.0), 0.4, 'a_darkgrey', n=8))
    # The helicopter deck aft: a ring and a bar.
    body.add(geom.cyl((0, deck + 0.0, -21.0), (0, deck + 0.012, -21.0), 3.0, 'a_yellow', n=24, caps=True))
    body.add(geom.cyl((0, deck + 0.0, -21.0), (0, deck + 0.02, -21.0), 2.75, 'a_deck', n=24, caps=True))
    body.add(geom.panel((0, deck + 0.03, -21.0), 0.3, 3.0, (0, 0, 1), (1, 0, 0), 'a_white'))
    body.add(geom.panel((0, deck + 0.03, -21.0), 2.2, 0.3, (0, 0, 1), (1, 0, 0), 'a_white'))
    body.add(geom.rbox((-2.0, deck, -11.0), (2.0, deck + 1.2, -7.6), 'skin', r=0.1))
    # The RBS15 hatches amidships (flush: two rows of four).
    body.add(geom.panel((0, deck + 0.006, -6.0 - 0.0), 2.2, 4.4, (0, 0, 1), (1, 0, 0), 'a_vls'))
    body.add(geom.rbox((-2.4, deck - 0.05, -7.8), (2.4, deck + 0.0, -4.2), 'a_darkgrey', r=0.0))
    # Number, water jets, the mast.
    for s in (-1, 1):
        m.decals.append(K.number_decal((s * 4.95, 2.2, 21.5), (s, 0, 0), 2.4, 1.0, 'K32', fg=(236, 236, 230)))
    waterjets(body, z0, (-2.0, 2.0), -0.4, 0.55)
    # The 57 mm Mk3 under its faceted cupola.
    tz = 24.0
    tur = m.part('turret', (0, deck, tz))
    tg = Body([T(tz - 2.0, deck + 1.0, deck, 0.9, pt=4, pb=4), T(tz - 1.7, deck + 2.0, deck, 1.5), T(tz + 0.9, deck + 2.0, deck, 1.5),
               T(tz + 1.5, deck + 1.5, deck, 1.2), T(tz + 1.7, deck + 1.0, deck, 1.0)], n=12)
    tur.add(tg.skin(tg.zs(0.5), 'skin', cap0=True, cap1=True))
    gun = tur.part('gun', (0, deck + 1.1, tz + 1.4))
    gun.add(geom.rbox((-0.35, deck + 0.8, tz + 1.2), (0.35, deck + 1.4, tz + 1.75), 'skin', r=0.06))
    cannon(gun, (0, deck + 1.1, tz + 1.7), 3.4, 0.07, shroud=0.9)
    ck = body.part('cockpit')
    ck.near = 24
    g = Geo(40)
    console(g, 12.2, deck + 5.3, 2.4, seats=((-1.0, 0.0), (1.0, 0.0)))
    ck.add(g)
    for z in np.arange(-30.0, 28.0, 5.0):
        A.lines.append((B.section_line(z), 0.03, True))
    return m


def models():
    return [gyurza(), raptor(), rhib(), buyan_m(), visby()]
