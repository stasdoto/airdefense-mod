"""
2.0 town transit: a city bus, a trolleybus and a tram. The shell is lofted through cross-sections (rounded roof edges,
rounded plan at the nose and tail, real wheel arches with liners); the window openings are cut out of it as glass bands
between pillars (stations fall exactly on the pillar edges, so there is no stair-stepping), the doors on the right
hand side are glazed leaves with a black skirt. Inside: a floor, linings, seats, handrails, the driver's cab.

TransitSpec holds the sizes (metres, front at +z); build_transit(spec, extra) -> core.Model.
"""
import math

import numpy as np

import geom
import kit_town as K
import materials
from core import Geo, Model, face_normal, normalize, rot_x
from town_cars import smooth, full_ring, _orient_like


class TransitSpec:
    def __init__(self, **kw):
        self.id = 'bus'
        self.zf = 5.5
        self.zr = -5.5
        self.hw = 1.25
        self.ybot = 0.3  # underside
        self.belt_lo = 1.12  # top of the lower panel
        self.win_lo = 1.2
        self.win_hi = 2.45
        self.top_hi = 2.6
        self.roof = 3.0
        self.crown = 0.06
        self.rx = 0.32  # roof edge radius across
        self.ry = 0.34  # and up
        self.nose_len = 0.55
        self.nose_p = 4.5
        self.tail_len = 0.5
        self.tail_p = 4.0
        self.lean_f = 0.1
        self.lean_r = 0.03
        self.pillar = 0.1
        self.frame = 0.025
        # Cells along the sides, front to back: (z_hi, z_lo, kind) with kind 'w' window or 'd' door (right hand side only).
        self.cells = []
        # Wheels: (z, radius, track, tyre width, steering)
        self.wheels = []
        self.arch_gap = 0.07
        self.paint = 0xD9A400
        self.second = 0xF1EFE6
        self.dirt = 0.15
        self.floor = 0.9
        self.density = 34
        self.seat_rows = []
        self.seat_mat = 'tl_fabric_blue'
        self.stripe = 'paint2'  # the belt line (and the band over the windows)
        self.upper_mat = 'paint'
        self.skirt_black = False
        self.driver = (-0.6, 4.2)
        self.cab_front = 1.2  # how far behind the nose the cab's partition is
        self.near = 24.0
        self.glass = 'glass'
        self.ac = True
        for k, v in kw.items():
            if not hasattr(self, k):
                raise AttributeError(k)
            setattr(self, k, v)


class Shell:
    def __init__(self, s):
        self.s = s
        self.z0f = s.zf - s.nose_len
        self.z0r = s.zr + s.tail_len
        self.xi_k = 1.0

    def X(self, z):
        s = self.s
        if z > self.z0f:
            t = min(1.0, (z - self.z0f) / s.nose_len)
            return s.hw * max(0.0, 1 - t ** 2) ** (1 / s.nose_p)
        if z < self.z0r:
            t = min(1.0, (self.z0r - z) / s.tail_len)
            return s.hw * max(0.0, 1 - t ** 2) ** (1 / s.tail_p)
        return s.hw

    def arch_y(self, z):
        """The top of the wheel opening at z (the underside's own height where there is no wheel)."""
        s = self.s
        best = s.ybot + 0.02
        for (wz, r, track, w, st) in s.wheels:
            R = r + s.arch_gap
            d = abs(z - wz)
            if d < R:
                best = max(best, r + math.sqrt(R * R - d * d))
        return best

    def in_arch(self, z):
        s = self.s
        return any(abs(z - wz) < r + s.arch_gap + 1e-6 for (wz, r, _t, _w, _s) in s.wheels)

    def stations(self):
        s = self.s
        zs = set()
        for k in range(8):
            th = math.pi / 2 * k / 7
            zs.add(self.z0f + s.nose_len * math.sin(th))
            zs.add(self.z0r - s.tail_len * math.sin(th))
        # The flat of the nose and the tail (the glass strip lies in the last stretch).
        zs.add(self.z0f + s.nose_len * math.sin(math.asin(0.93)))
        zs.add(self.z0r - s.tail_len * math.sin(math.asin(0.93)))
        zs.add(self.z0f + s.nose_len * math.sin(math.asin(0.3)))
        for (z_hi, z_lo, kind) in s.cells:
            e1 = z_hi - s.pillar / 2
            e2 = z_lo + s.pillar / 2
            zs.update((z_hi, e1, e1 - s.frame, e2 + s.frame, e2, z_lo))
            if kind == 'd':
                zc = (e1 + e2) / 2
                zs.update((zc - 0.02, zc + 0.02))
        for (wz, r, track, w, st) in s.wheels:
            R = r + s.arch_gap
            for k in range(-6, 7):
                zs.add(wz + R * math.sin(k / 6 * math.pi / 2))
        zs = sorted(z for z in zs if s.zr <= z <= s.zf)
        out = [zs[0]]
        for z in zs[1:]:
            if z - out[-1] > 0.9:
                n = int((z - out[-1]) / 0.8) + 1
                base = out[-1]
                for i in range(1, n):
                    out.append(base + (z - base) * i / n)
            if z - out[-1] > 0.006:
                out.append(z)
        out[-1] = zs[-1]
        return sorted(set(round(z, 4) for z in out))

    def half(self, z):
        """The right half of the cross-section at z: bottom centre up and over to the top centre."""
        s = self.s
        k = self.X(z) / s.hw
        hw = s.hw * k
        yb = s.ybot
        ya = self.arch_y(z)
        xi = min(0.8, hw * 0.9)
        for (wz, r, track, w, st) in s.wheels:
            if abs(z - wz) < r + s.arch_gap:
                xi = min(track - w / 2 - 0.04, hw * 0.9)
        pts = [(0.0, yb), (xi, yb), (xi, ya), (max(xi, hw - 0.03), ya), (hw, ya + 0.03), (hw, min(s.belt_lo - 0.01, max(0.62, ya + 0.06))), (hw, s.belt_lo), (hw, s.win_lo), (hw, s.win_hi),
               (hw, s.top_hi)]
        ys = s.roof - s.ry
        pts.append((hw, ys))
        rx = s.rx * (0.4 + 0.6 * min(1.0, k)) if k < 1 else s.rx
        for a in (30, 60, 90):
            r = math.radians(a)
            pts.append((hw - rx + rx * math.cos(r), ys + s.ry * math.sin(r) - (s.crown * 0.3 if a == 90 else 0)))
        pts.append((hw * 0.5, s.roof + s.crown * 0.75))
        pts.append((0.0, s.roof + s.crown))
        return pts

    def lean(self, p):
        """Rakes the glazing and the roofline back at the nose and the tail."""
        s = self.s
        x, y, z = p
        up = max(0.0, y - s.belt_lo)
        if z > self.z0f - 0.6:
            t = min(1.0, max(0.0, (z - (self.z0f - 0.6)) / 0.6))
            z -= s.lean_f * up * t
        if z < self.z0r + 0.5:
            t = min(1.0, max(0.0, (self.z0r + 0.5 - z) / 0.5))
            z += s.lean_r * up * t
        return (x, y, z)


def classify(s, B, zm):
    """What a band of the side wall is at z = zm: 'front', 'rear', 'pillar', 'frame', 'glass', 'dframe', 'dsplit', 'doorglass'."""
    if zm > B.z0f + s.nose_len * 0.2:
        return 'front'
    if zm < B.z0r - s.tail_len * 0.3:
        return 'rear'
    for (z_hi, z_lo, kind) in s.cells:
        e1 = z_hi - s.pillar / 2
        e2 = z_lo + s.pillar / 2
        if zm > z_hi or zm < z_lo:
            continue
        if zm > e1 or zm < e2:
            return 'pillar'
        if zm > e1 - s.frame or zm < e2 + s.frame:
            return 'frame' if kind == 'w' else 'dframe'
        if kind == 'd':
            zc = (e1 + e2) / 2
            if abs(zm - zc) < 0.02:
                return 'dsplit'
            return 'doorglass'
        return 'glass'
    return 'pillar'


def shell(s, B):
    zs = B.stations()
    halves = [B.half(z) for z in zs]
    nh = len(halves[0])
    rings = [[B.lean(p) for p in full_ring(h, z)] for z, h in zip(zs, halves)]
    n = len(rings[0])
    paint2 = s.stripe

    def mats(i, k):
        right = k < nh - 1
        b = k if right else n - 1 - k
        low = b == 4
        if b >= 5:
            b -= 1
        z0, z1 = zs[i], zs[i + 1]
        zm = (z0 + z1) / 2
        kind = classify(s, B, zm)
        if b == 0:
            return 'tl_under'
        if b in (1, 2):
            return 'tl_liner' if B.in_arch(zm) else 'tl_under'
        if b == 3:
            return 'tl_liner' if B.in_arch(zm) else 'trim'
        end = kind in ('front', 'rear')
        if b == 4:  # the lower panel
            if end:
                return 'plastic' if low else 'paint'
            if kind in ('doorglass', 'dframe', 'dsplit') and right:
                return 'trim'
            return 'paint'
        if b == 5:  # the belt line
            if kind in ('doorglass', 'dsplit') and right:
                return 'glass' if kind == 'doorglass' else 'trim'
            if kind == 'dframe' and right:
                return 'trim'
            return paint2 if not end else 'paint'
        if b == 6:  # the glazing
            if kind == 'front':
                return s.glass
            if kind == 'rear':
                return s.glass if zm < B.z0r - s.tail_len * 0.55 else 'paint'
            if kind == 'glass':
                return s.glass
            if kind == 'frame':
                return 'trim'
            if right:
                if kind == 'doorglass':
                    return 'glass'
                if kind == 'dframe':
                    return 'trim'
                if kind == 'dsplit':
                    return 'trim'
            else:
                if kind in ('doorglass', 'dframe', 'dsplit'):
                    return s.glass if kind == 'doorglass' else 'trim'
            return 'paint'
        if b == 7:  # over the glazing
            if kind in ('front',):
                return s.glass if zm > B.z0f + s.nose_len * 0.45 else 'paint'
            if kind in ('doorglass', 'dsplit') and right:
                return 'glass' if kind == 'doorglass' else 'trim'
            return 'trim' if kind in ('frame', 'dframe') else 'paint'
        if b == 8:
            return paint2 if s.stripe != 'paint' and not end else 'paint'
        return 'paint'

    g = geom.loft(rings, 'paint', closed=True, smooth=50, mats=mats)
    _orient_like(g, np.array([1.0, 0, 0]), lambda p: p.v.mean(axis=0)[0] > s.hw * 0.9 and abs(p.v.mean(axis=0)[2]) < 0.4 and p.v.mean(axis=0)[1] > 1.5)
    return g, rings, zs


# ---------------------------------------------------------------------------------------------------------------
# Inside


def fix_out(g):
    """Turns a closed solid inside out if its polygons wind the wrong way (by the sign of its volume)."""
    vol = 0.0
    for p in g.polys:
        v = p.v
        for i in range(1, len(v) - 1):
            vol += np.dot(v[0], np.cross(v[i], v[i + 1])) / 6.0
    if vol < 0:
        K.flip(g)
    return g


SEAT = [(0.22, -0.1), (0.23, -0.02), (0.16, 0.01), (-0.14, 0.0), (-0.2, 0.05), (-0.22, 0.5), (-0.26, 0.57), (-0.31, 0.55), (-0.3, 0.05),
        (-0.28, -0.1)]


def seat_unit(x, y, z, w, mat):
    """A bus seat: one extruded L-shaped profile (cushion and back) on two legs."""
    rings = []
    for sx in (-1, 1):
        rings.append([(x + sx * w / 2, y + py, z + pz) for pz, py in SEAT])
    g = geom.loft(rings, mat, closed=True, cap0=True, cap1=True, smooth=35)
    fix_out(g)
    for sx in (-1, 1):
        g.extend(K.hbox((x + sx * (w / 2 - 0.02) - 0.015, y - 0.45, z - 0.1), (x + sx * (w / 2 - 0.02) + 0.015, y - 0.1, z - 0.04), 'metal_dark',
                        faces=('right', 'left', 'front', 'back')))
    return g


def interior(s, B, skin, rings):
    g = Geo(40)
    hw = s.hw
    z_lo, z_hi = s.zr + 0.35, s.zf - 0.35
    xw = hw - 0.04
    wall = Geo(40)
    # The inner face of the side walls: under the windows, over them and the pillars between.
    for sd in (-1, 1):
        wall.add(np.array([(sd * xw, s.floor, z_lo), (sd * xw, s.floor, z_hi), (sd * xw, s.win_lo, z_hi), (sd * xw, s.win_lo, z_lo)]), 'tl_door_card')
        wall.add(np.array([(sd * xw, s.win_hi, z_lo), (sd * xw, s.win_hi, z_hi), (sd * xw, s.top_hi, z_hi), (sd * xw, s.top_hi, z_lo)]), 'tl_door_card')
        for (zh, zl, kind) in s.cells:
            for (a, b) in ((zh - s.pillar / 2 - 0.0, zh), (zl, zl + s.pillar / 2)):
                a, b = min(a, b), max(a, b)
                if b > z_hi or a < z_lo:
                    continue
                wall.add(np.array([(sd * xw, s.win_lo, a), (sd * xw, s.win_lo, b), (sd * xw, s.win_hi, b), (sd * xw, s.win_hi, a)]), 'tl_door_card')
    K.orient(wall, (0, 1.6, (z_lo + z_hi) / 2), outward=False)
    g.extend(wall)
    # The headliner: a coarse loft of the roof's inside.
    zs = list(np.linspace(z_lo, z_hi, int((z_hi - z_lo) / 1.1) + 1))
    top = []
    for z in zs:
        h = B.half(z)[9:]
        pts = [(x * 0.97 - 0.04 if x > 0.05 else x, y - 0.045, z) for x, y in h]
        top.append([(-x, y, zz) for x, y, zz in reversed(pts[1:])] + pts)
    hl = geom.loft(top, 'tl_headliner', closed=False, smooth=60)
    K.orient(hl, (0, 1.6, (z_lo + z_hi) / 2), outward=False)
    g.extend(hl)
    # Floor.
    xf = hw - 0.05
    g.extend(geom.box((-xf, s.floor - 0.02, z_lo), (xf, s.floor, z_hi), 'tl_bus_floor', faces=('top',)))
    # Wheel housings.
    for (wz, r, track, w, st) in s.wheels:
        R = r + s.arch_gap + 0.04
        g.extend(K.hbox((xf - 0.5, s.floor, wz - R), (xf, s.floor + 0.3, wz + R), 'tl_door_card', r=0.03, faces=('top', 'left', 'front', 'back')))
        g.extend(K.hbox((-xf, s.floor, wz - R), (-xf + 0.5, s.floor + 0.3, wz + R), 'tl_door_card', r=0.03, faces=('top', 'right', 'front', 'back')))
    return g


def cab(s, B, model):
    """The driver's place: seat, dash, steering wheel."""
    g = Geo(40)
    dx, dz = s.driver
    fl = s.floor
    zf = s.zf - s.nose_len * 0.55
    wsh = 1.0
    # Dash.
    dash = K.hbox((-s.hw + 0.12, fl + 0.05, zf - 0.75), (s.hw - 0.12, fl + 0.9, zf - 0.2), 'dash', r=0.05)
    g.extend(dash)
    g.extend(geom.panel((dx, fl + 0.82, zf - 0.755), 0.6, 0.18, (1, 0, 0), (0, 1, 0), 'gauges').moved((0, 0, 0)))
    K.orient(g, (0, fl + 0.3, zf - 1.5))
    # The driver's seat and the cab partition.
    g.extend(seat_unit(dx, fl + 0.5, dz - 1.0 if dz > zf - 1.2 else dz, 0.5, 'seat'))
    return g


def build_transit(s, extra=None):
    scheme = materials.Scheme(base=s.paint, second=s.second, dirt=s.dirt, wear=0.12, seed=abs(hash(s.id)) % 997, gloss=0.8)
    m = Model(s.id, scheme, density=s.density, tex_width=512)
    m.chart_by_mat = True
    m.tl_dims = {}
    B = Shell(s)
    body = m.part('body')
    skin, rings, zs = shell(s, B)
    body.add(skin)
    C = K.Caster(skin)
    body.add(details(s, B, C, m))
    inside = body.part('inside')
    inside.near = s.near
    inside.add(interior(s, B, skin, rings))
    inside.add(cab(s, B, m))
    inside.add(furniture(s, B))
    # Wheels.
    names = {}
    for idx, (wz, r, track, w, st) in enumerate(s.wheels):
        for side, tag in ((-1, 'l'), (1, 'r')):
            name = 'wheel_%d_%s' % (idx + 1, tag)
            c = (side * track, r, wz)
            p = body.part(name, c)
            p.add(K.car_wheel(c, side, r, w, 'steel', n=22, mat_rim='tl_steel_wheel'))
            p.spin = r
            p.steer = bool(st)
    if extra:
        extra(m, s, B, C, body, inside)
    return m


def details(s, B, C, model):
    """Lamps, plates, the route board, bumpers' details, mirrors and the roof."""
    g = Geo(45)
    hw = s.hw
    dims = model.tl_dims
    zf, zr = s.zf, s.zr
    # Head lamps and indicators low in the nose (the bumper band).
    dims['tl_head_sq'] = (0.34, 0.16)
    for side in (-1, 1):
        g.extend(K.patch(C, (side * (hw - 0.34), 0.78, zf), (0.34, 0, 0), (0, 0.16, 0), (0, 0, -1), 'tl_head_sq', na=3, nb=1, offset=0.012,
                         back=1.0, skirt=0.012, skirt_mat='chrome'))
        g.extend(K.patch(C, (side * (hw - 0.14), 0.78, zf), (0.12, 0, 0), (0, 0.12, 0), (0, 0, -1), 'tl_amber', na=1, nb=1, offset=0.01,
                         back=1.0, skirt=0.01, skirt_mat='trim'))
        # Tail lamps: tall units at the corners.
        g.extend(K.patch(C, (side * (hw - 0.22), 0.95, zr), (0.14 * side, 0, 0), (0, 0.5, 0), (0, 0, 1), 'tl_tail_old', na=1, nb=3, offset=0.01,
                         back=1.0, skirt=0.01, skirt_mat='trim'))
    # Plates.
    g.extend(K.patch(C, (0, 0.62, zf), (-0.52, 0, 0), (0, 0.112, 0), (0, 0, -1), 'plate', na=2, nb=1, offset=0.008, back=1.0,
                     skirt=0.008, skirt_mat='trim'))
    g.extend(K.patch(C, (0, 0.62, zr), (0.52, 0, 0), (0, 0.112, 0), (0, 0, 1), 'plate', na=2, nb=1, offset=0.008, back=1.0,
                     skirt=0.008, skirt_mat='trim'))
    # The route board over the windscreen, a smaller one on the right flank and at the back.
    dims['tl_route'] = (1.9, 0.2)
    g.extend(K.patch(C, (0, s.top_hi - 0.08, zf), (1.9, 0, 0), (0, 0.2, 0), (0, 0, -1), 'tl_route', na=8, nb=1, offset=0.01, back=1.0,
                     skirt=0.012, skirt_mat='trim'))
    g.extend(K.patch(C, (0, s.top_hi - 0.08, zr), (-0.8, 0, 0), (0, 0.18, 0), (0, 0, 1), 'tl_route', na=4, nb=1, offset=0.01, back=1.0,
                     skirt=0.012, skirt_mat='trim'))
    # Mirrors on arms at the front corners.
    for side in (-1, 1):
        base = np.array([side * (hw - 0.02), 1.55, B.z0f + 0.05])
        arm = geom.tube([base, base + [side * 0.18, 0.05, 0.12], base + [side * 0.3, -0.05, 0.14]], 0.012, 'trim', n=5)
        g.extend(arm)
        g.extend(K.hbox(base + [side * 0.3 - 0.03, -0.3, 0.1], base + [side * 0.3 + 0.03, 0.15, 0.17], 'trim', r=0.02))
        g.extend(geom.panel(base + [side * 0.3, -0.07, 0.108 - 0.012 - 0.0], 0.05, 0.4, (1, 0, 0), (0, 1, 0), 'mirror_glass'))
    # Windscreen wipers.
    for x0 in (-0.9, 0.0):
        y = s.belt_lo + 0.22
        g.extend(K.wiper((x0, y, zf - s.nose_len * 0.03 - 0.0 - 0.05 + 0.0), (x0 + 0.8, y + 0.15, zf - 0.07), 'wiper'))
    return g


def furniture(s, B):
    g = Geo(40)
    fl = s.floor
    rows = s.seat_rows
    for (z, xs) in rows:
        for x in xs:
            g.extend(seat_unit(x, fl + 0.48, z, 0.42, s.seat_mat))
    # Handrails along the ceiling and vertical poles at the doors.
    zs = [c for c in s.cells if c[2] == 'd']
    for x in (-0.5, 0.5):
        g.extend(geom.tube([(x, s.top_hi - 0.12, s.zr + 0.7), (x, s.top_hi - 0.12, s.zf - 1.9)], 0.017, 'tl_handrail', n=5))
    for (z_hi, z_lo, kind) in zs:
        zc = (z_hi + z_lo) / 2
        for dz in (-0.55, 0.55):
            g.extend(geom.tube([(s.hw - 0.4, fl, zc + dz), (s.hw - 0.4, s.top_hi - 0.12, zc + dz)], 0.02, 'tl_handrail', n=5))
        g.extend(geom.tube([(0.5, s.top_hi - 0.12, zc), (s.hw - 0.4, s.top_hi - 0.12, zc)], 0.017, 'tl_handrail', n=5))
    return g
