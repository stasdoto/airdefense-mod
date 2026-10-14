"""
2.0 (trucks3): hand-built meshes for the vehicles that were only converted from box models: different cabs
(Ural long bonnet, KamAZ flat cab, MAZ/MZKT slab cab, western FMTV/HEMTT), proper chassis, payloads.
"""
import math

import numpy as np

import geom
import kit_trucks as K
import m_trucks as T
from core import Geo, rot_x, rot_y, rot_z
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar

Build = T.Build
BUILDERS = {}


def load_parts():
    """The vehicle builders live in m_t3a, m_t3b, ... (imported here, after the shared helpers above exist)."""
    import importlib
    for n in ('m_t3a', 'm_t3b', 'm_t3c', 'm_t3d', 'm_t3e', 'm_t3f'):
        try:
            mod = importlib.import_module(n)
        except ModuleNotFoundError as e:
            if e.name != n:
                raise
            continue
        BUILDERS.update(getattr(mod, 'BUILDERS', {}))


def stowed_jack(x, z, y_top, side):
    """A stowed levelling jack: housing on the frame, the leg folded up with its pad snug under the housing."""
    g = Geo(30)
    g.extend(geom.rbox((x - 0.1, y_top - 0.42, z - 0.1), (x + 0.1, y_top, z + 0.1), 'paint', r=0.015))
    g.extend(geom.cyl((x, y_top - 0.42, z), (x, y_top - 0.62, z), 0.045, 'chrome', n=8))
    g.extend(geom.cyl((x, y_top - 0.68, z), (x, y_top - 0.6, z), 0.17, 'chassis', n=12, bevel=0.012))
    return g


def jack_down(x, z, y_top, side=1, reach=0.0):
    """A deployed jack: leg to the ground and a round pad standing on it (y = 0.05)."""
    g = Geo(40)
    xo = x + side * reach
    if reach:
        g.extend(box((min(x, xo) - 0.07, y_top - 0.16, z - 0.08), (max(x, xo) + 0.07, y_top, z + 0.08), 'paint'))
    g.extend(geom.rbox((xo - 0.1, y_top - 0.5, z - 0.1), (xo + 0.1, y_top, z + 0.1), 'paint', r=0.015))
    g.extend(geom.cyl((xo, 0.1, z), (xo, y_top - 0.45, z), 0.05, 'chrome', n=8))
    g.extend(geom.cyl((xo, 0.0, z), (xo, 0.09, z), 0.17, 'chassis', n=12, bevel=0.015))
    return g


def oval(c, rx, ry, z0, z1, mat, n=20):
    """A horizontal elliptical tank along z, centre c = (x, y), dished ends."""
    cx, cy = c
    prof = [(z0, 0.02), (z0 + 0.025, 0.7), (z0 + 0.07, 0.9), (z0 + 0.15, 0.985), (z0 + 0.3, 1.0), (z1 - 0.3, 1.0), (z1 - 0.15, 0.985),
            (z1 - 0.07, 0.9), (z1 - 0.025, 0.7), (z1, 0.02)]
    rings = []
    for z, f in prof:
        rings.append([V(cx + math.cos(2 * math.pi * k / n) * rx * f, cy + math.sin(2 * math.pi * k / n) * ry * f, z) for k in range(n)])
    return K.flipped(geom.loft(rings, mat, closed=True))


# ---------------------------------------------------------------------------------------------------------------
# Cabs


def _cab_trim(g, cab, w, y0, y1, zf, yb, ws_top, z0, mirror_z=None):
    for s in (-1, 1):
        x = s * w
        g.extend(box((x - 0.02 if s > 0 else x - 0.012, yb - 0.02, zf - 0.7), (x + 0.012 if s > 0 else x + 0.02, yb + 0.01, zf - 0.56), 'metal_dark'))
        g.extend(K.mirror((x, ws_top - 0.12, zf - 0.28), (x + s * 0.34, ws_top - 0.22, zf - 0.02), (0.18, 0.34), s))
    a, c = cab.ws_corners()
    for xa in (-w * 0.5, w * 0.12):
        g.extend(K.wiper((xa, c[1] - 0.02, c[0] + 0.02), (xa + w * 0.38, c[1] - 0.05, c[0] + 0.03)))


def ural_cab(b, part, w, z0, zc, zf, y0, y1, hood_top, seats, wing_y):
    """Ural/ZIL: a short cab behind a long bonnet with big wings; cab z0..zc, bonnet zc..zf."""
    yb = y0 + (y1 - y0) * 0.38
    ws_top = y1 - 0.13
    prof = [(z0, y0, 0.03), (zc, y0, 0.03), (zc, yb, 0.03), (zc, yb + 0.03, 0, 'ws0'), (zc - 0.17, ws_top, 0, 'ws1'), (zc - 0.2, y1 - 0.03, 0.08), (z0 + 0.05, y1, 0.07)]
    cab = K.Cab(prof, w - 0.08, r=0.08)
    cab.windscreen([(-w + 0.17, -0.04), (0.04, w - 0.17)])
    cab.side_window(zc - 0.75, zc - 0.2, yb + 0.05, ws_top - 0.07, follow=0.08)
    g = cab.geo()
    part.add(g)
    b.m.decals.append(K.seam_decal(V(w - 0.08, (y0 + y1) / 2, (z0 + zc) / 2), V(0, 0, -(zc - z0)), V(0, y1 - y0, 0), [(-0.44, -0.78, 0.35, 0.85)]))
    b.m.decals.append(K.seam_decal(V(-w + 0.08, (y0 + y1) / 2, (z0 + zc) / 2), V(0, 0, (zc - z0)), V(0, y1 - y0, 0), [(-0.35, -0.78, 0.44, 0.85)]))
    h = Geo(40)
    _cab_trim(h, cab, w - 0.08, y0, y1, zc, yb, ws_top, z0)
    # Bonnet: the sloping hood with a ridge, side louvres, the radiator and grille at the front.
    xb = 0.66
    h.extend(geom.rbox((-xb, y0 + 0.05, zc + 0.02), (xb, hood_top, zf - 0.12), 'paint', r=0.12, segs=2))
    for s in (-1, 1):
        h.extend(geom.panel((s * (xb + 0.002), (y0 + hood_top) / 2 - 0.02, (zc + zf) / 2 - 0.05), (zf - zc) * 0.5, (hood_top - y0) * 0.45,
                            (0, 0, 1) if s > 0 else (0, 0, -1), (0, 1, 0), 'grille'))
    h.extend(box((-0.05, hood_top - 0.02, zc + 0.15), (0.05, hood_top + 0.015, zf - 0.3), 'paint'))
    h.extend(geom.rbox((-0.58, y0 + 0.05, zf - 0.14), (0.58, hood_top - 0.02, zf - 0.06), 'metal_dark', r=0.03))
    h.extend(geom.panel((0, (y0 + hood_top) / 2 + 0.02, zf - 0.055), 1.0, hood_top - y0 - 0.14, (1, 0, 0), (0, 1, 0), 'grille'))
    # Wings with the lamps.
    for s in (-1, 1):
        xa, xz = (xb - 0.02, w + 0.04) if s > 0 else (-w - 0.04, -xb + 0.02)
        h.extend(geom.rbox((xa, wing_y, zc + 0.1), (xz, wing_y + 0.06, zf - 0.02), 'paint', r=0.025))
        h.extend(K.headlamp((s * 0.9, wing_y + 0.14, zf - 0.04), r=0.09, depth=0.1))
        h.extend(box((s * 1.05 - 0.04, wing_y + 0.06, zf - 0.1), (s * 1.05 + 0.04, wing_y + 0.1, zf - 0.04), 'indicator'))
    h.extend(K.rbox((-w + 0.1, wing_y - 0.5, zf - 0.14), (w - 0.1, wing_y - 0.28, zf + 0.04), 'metal_dark', r=0.03))
    for s in (-1, 1):
        h.extend(cyl((s * 0.42, wing_y - 0.38, zf + 0.04), (s * 0.42, wing_y - 0.38, zf + 0.14), 0.045, 'metal_dark', n=8))
    h.extend(K.step(w - 0.3, w + 0.05, y0 - 0.2, zc - 0.9, zc - 0.25))
    h.extend(K.step(-w - 0.05, -w + 0.3, y0 - 0.2, zc - 0.9, zc - 0.25))
    h.extend(geom.cyl((0.0, y1, z0 + 0.4), (0.0, y1 + 0.03, z0 + 0.4), 0.28, 'paint', n=14))
    part.add(h)
    return cab


def kamaz_cab(b, part, w, z0, zf, y0, y1, seats, bumper_y, bonnet_front=0.08):
    """KamAZ/MAZ style flat-fronted cab-over: a tall box with a squarish windscreen, grille in the lower front, big
    bumper with lamps, a roof visor."""
    yb = y0 + (y1 - y0) * 0.48
    ws_top = y1 - 0.14
    prof = [(z0, y0, 0.03), (zf - 0.02, y0, 0.05), (zf, yb - 0.3, 0.07), (zf - 0.015, yb, 0.03), (zf - 0.02, yb + 0.03, 0, 'ws0'),
            (zf - 0.12, ws_top, 0, 'ws1'), (zf - 0.16, y1 - 0.01, 0.1), (z0 + 0.1, y1, 0.07)]
    cab = K.Cab(prof, w, r=0.1)
    cab.windscreen([(-w + 0.16, -0.04), (0.04, w - 0.16)])
    cab.side_window(zf - 0.95, zf - 0.17, yb + 0.05, ws_top - 0.07, follow=0.08)
    part.add(cab.geo())
    b.m.decals.append(K.seam_decal(V(w, (y0 + y1) / 2, (z0 + zf) / 2), V(0, 0, -(zf - z0)), V(0, y1 - y0, 0), [(-0.48, -0.75, 0.42, 0.85)]))
    b.m.decals.append(K.seam_decal(V(-w, (y0 + y1) / 2, (z0 + zf) / 2), V(0, 0, (zf - z0)), V(0, y1 - y0, 0), [(-0.42, -0.75, 0.48, 0.85)]))
    g = Geo(40)
    _cab_trim(g, cab, w, y0, y1, zf, yb, ws_top, z0)
    # Grille / radiator door, a sun visor over the glass, lamps and indicators in the lower front.
    g.extend(geom.panel((0, y0 + (yb - y0) * 0.52, zf + 0.004), w * 1.3, (yb - y0) * 0.5, (1, 0, 0), (0, 1, 0), 'grille'))
    g.extend(box((-w * 0.78, ws_top + 0.04, zf - 0.2), (w * 0.78, ws_top + 0.1, zf + 0.03), 'paint_black'))
    for s in (-1, 1):
        g.extend(K.rect_lamp((s * (w - 0.3), y0 + 0.28, zf + 0.0), 0.3, 0.14, 'headlight_box', (0, 0, 1)))
        g.extend(box((s * (w - 0.15) - 0.07, y0 + 0.16, zf - 0.03), (s * (w - 0.15) + 0.07, y0 + 0.3, zf + 0.02), 'indicator'))
        g.extend(K.handrail([(s * (w + 0.03), yb - 0.4, zf - 0.95), (s * (w + 0.07), yb - 0.38, zf - 0.95), (s * (w + 0.07), yb + 0.35, zf - 0.95), (s * (w + 0.03), yb + 0.37, zf - 0.95)]))
    # Bumper and the step under each door.
    g.extend(K.rbox((-w + 0.02, bumper_y, zf - 0.02), (w - 0.02, bumper_y + 0.27, zf + 0.22), 'paint', r=0.03))
    for s in (-1, 1):
        g.extend(cyl((s * 0.5, bumper_y + 0.12, zf + 0.22), (s * 0.5, bumper_y + 0.12, zf + 0.34), 0.05, 'metal_dark', n=8))
        g.extend(K.step(min(s * (w - 0.35), s * (w + 0.1)), max(s * (w - 0.35), s * (w + 0.1)), y0 - 0.24, zf - 0.95, zf - 0.3))
    g.extend(geom.rbox((-0.5, y1, z0 + 0.15), (0.5, y1 + 0.17, z0 + 0.95), 'paint', r=0.05))
    for s in (-1, 1):
        for xx in (-0.3, 0.0, 0.3):
            pass
        g.extend(cbox((s * (w - 0.2), y1 + 0.015, zf - 0.25), (0.08, 0.03, 0.05), 'indicator'))
    part.add(g)
    return cab


def wheels(b, names=None, n=12, bolts=None, rim='paint', width=None):
    """Military wheels on every wheel part of the reference (fewer nuts when there are many wheels)."""
    cnt = len(b.r.wheels)
    if bolts is None:
        bolts = 6 if cnt <= 6 else (3 if cnt <= 8 else 0)
    for name, R, steer in b.r.wheels:
        if names is not None and name not in names:
            continue
        rp = b.r.by[name]
        piv = np.array(rp.pivot)
        bb = rp.bounds()
        w = width if width is not None else (bb[1][0] - bb[0][0]) - 0.06
        r = min(R, piv[1] + 0.015)
        side = -1 if piv[0] < 0 else 1
        b.m.tyre_half = w / 2
        b.m.tyre_lugs = n
        b.p[name].add(K.mil_wheel(piv, side, r, w, rim=rim, n=n, rim_ratio=0.5, lugs=n, bolts=bolts, ctis=False))


def reflector(g, c, w, h, depth, facing=1, nx=2, ny=9, ribs=3, frame='steel', mat='lattice'):
    """A cylindrical (curved along y) mesh dish centred at c = (x, y, z) facing +z (facing=1) or -z: w wide, h tall,
    the middle {depth} behind the rim; a frame round it and ribs behind."""
    cx, cy, cz = c
    ys = np.linspace(-h / 2, h / 2, ny)
    curve = lambda y: facing * -depth * (1 - (y / (h / 2)) ** 2) * 0 + facing * depth * (y / (h / 2)) ** 2
    cols = []
    for sx in np.linspace(-w / 2, w / 2, nx):
        cols.append([V(cx + sx, cy + y, cz + curve(y)) for y in ys])
    g.extend(geom.loft(cols, mat, closed=False))
    g.extend(K.flipped(geom.loft(cols, mat, closed=False)))
    for sx in np.linspace(-w / 2, w / 2, ribs + 2):
        g.extend(geom.tube([V(cx + sx, cy + y, cz + curve(y) - facing * 0.03) for y in ys], 0.025, frame, n=4))
    for yy in (-h / 2, h / 2):
        g.extend(geom.tube([V(cx + sx, cy + yy, cz + curve(yy) - facing * 0.03) for sx in (-w / 2, w / 2)], 0.025, frame, n=4))


def western_cab(b, z0, z1, y0, y1, w, dash=0.85):
    """The FMTV/HEMTT-class cab of the western trucks, with its inside."""
    cab = T.fmtv_cab(b, b.body, z0, z1, y0, y1, w, b.r.seats)
    K.cab_inside_part(b.body, 'cab_inside', cab, b.r.seats, dash_y=y0 + dash, tilt=-62)
    return cab


# ---------------------------------------------------------------------------------------------------------------
# Chassis


def chassis(b, deck, zrear, zdeck_front, w=1.15, tanks=True, jacks='stowed', front_fenders=True, rear_guards=True,
            bar_bumper=True, deck_plate=True, lamps=True, cross_to=None, zfront=None, wn=12, bolts=None):
    """Frame, the load deck (a plate on the frame, optional), mudguards, tanks, rear lamps and bumper, jacks, axles and
    wheels. deck = (y0, y1) of the load deck: y0 = under it, y1 = the loading surface."""
    body = b.body
    d0, d1 = deck
    g = Geo(40)
    zf = zfront if zfront is not None else zdeck_front
    g.extend(K.frame_rails(0.43, d0 - 0.4, d0 - 0.05, zrear + 0.05, zf, cross=tuple(np.linspace(zrear + 0.3, zdeck_front - 0.3, 6))))
    if deck_plate:
        g.extend(box((-w, d0, zrear), (w, d1 - 0.02, zdeck_front), 'paint', faces=('left', 'right', 'back', 'front', 'bottom')))
        g.extend(box((-w + 0.02, d1 - 0.02, zrear + 0.02), (w - 0.02, d1, zdeck_front - 0.02), 'grating', faces=('top',)))
    ws = [(n, R, st) for n, R, st in b.r.wheels if n.endswith('_l')]
    for n, R, st in ws:
        pz = b.r.by[n].pivot[2]
        px = abs(b.r.by[n].pivot[0])
        if st and front_fenders:
            for s in (-1, 1):
                x0, x1 = (px - 0.34, px + 0.22) if s > 0 else (-px - 0.22, -px + 0.34)
                g.extend(K.fender(x0, x1, pz, R - 0.06, R + 0.08, -5, 185))
    rear = [b.r.by[n].pivot[2] for n, R, st in ws if not st]
    if rear and rear_guards:
        pr = abs(b.r.by[ws[0][0]].pivot[0])
        for s in (-1, 1):
            x0, x1 = (pr - 0.34, pr + 0.24) if s > 0 else (-pr - 0.24, -pr + 0.34)
            g.extend(K.flat_guard(x0, x1, min(rear) - 0.8, max(rear) + 0.8, ws[0][1] * 2 + 0.07, 'paint', lip=0.15, flap=0.3))
    if lamps:
        for s in (-1, 1):
            g.extend(K.lamp_cluster((s * (w - 0.2), d0 + 0.0, zrear - 0.02), s))
    if bar_bumper:
        g.extend(K.rbox((-w + 0.1, d0 - 0.36, zrear - 0.16), (w - 0.1, d0 - 0.16, zrear), 'chassis', r=0.02))
        g.extend(box((-0.1, d0 - 0.3, zrear - 0.3), (0.1, d0 - 0.24, zrear - 0.16), 'metal_dark'))
        g.extend(cyl((0.0, d0 - 0.3, zrear - 0.3), (0.0, d0 - 0.24, zrear - 0.3), 0.05, 'metal_dark', n=8))
    if tanks:
        zt = (zrear + zdeck_front) / 2
        g.extend(K.fuel_tank(-w * 0.45, -w + 0.07, d0 - 0.5, d0 - 0.04, zt - 0.6, zt + 0.7, 'box'))
        g.extend(K.toolbox((w * 0.45, d0 - 0.5, zt - 0.6), (w - 0.07, d0 - 0.04, zt + 0.7)))
    if jacks == 'stowed':
        for s in (-1, 1):
            g.extend(stowed_jack(s * (w - 0.18), zrear + 0.25, d0, s))
    body.add(g)
    b.axles()
    wheels(b, n=wn, bolts=bolts)
