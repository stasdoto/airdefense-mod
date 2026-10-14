"""
2.0 town cars: a body lofted through cross-sections (plan rounded at the nose and tail as a superellipse, the hood and
the boot rolling over onto the fascias, the sides swelling to the shoulder and tucked in at the sills, real wheel
wells with liners), a glasshouse lofted on top of it (the windscreen rounding into the roof, the side glass between the
pillars, the backlight), lamps, grilles, plates and handles laid onto the skin by rays (kit_town.patch), an interior
seen through the glass.

CarSpec holds the sizes (metres); build_car(spec) -> core.Model.
"""
import math

import numpy as np

import geom
import kit_town as K
import materials
from core import Geo, Model, face_normal, normalize, rot_x, rot_y, rot_z


def smooth(a, b, x):
    if b == a:
        return 1.0 if x >= a else 0.0
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def roll(d, R):
    """How far a rounded edge of radius R drops at distance d in from its outline."""
    if R <= 0 or d >= R:
        return 0.0
    d = max(0.0, d)
    return R - math.sqrt(max(0.0, R * R - (R - d) ** 2))


class CarSpec:
    def __init__(self, **kw):
        self.id = 'car'
        self.zf = 2.25
        self.zr = -2.25
        self.width = 1.78
        self.front_axle = 1.38
        self.rear_axle = -1.26
        self.wheel_r = 0.31
        self.tyre_w = 0.205
        self.track = 0.765
        self.wheel_style = 'alloy'
        self.spokes = 5
        self.sill = 0.30
        self.bump_f = 0.22
        self.bump_r = 0.27
        self.hood_front = 0.74
        self.belt_f = 0.93
        self.belt_r = 0.99
        self.tail_top = 0.95
        self.roof = 1.47
        self.cowl_z = 0.78
        self.ws_top_z = -0.14
        self.roof_end_z = -0.98
        self.deck_z = -1.62
        self.gh_end = None  # the glasshouse's end (the deck for a saloon, the tail for a hatch)
        self.b_z = -0.42
        self.c_z = -1.1  # where the rear door's glass ends
        self.q_z = None  # a third side window (quarter glass) to this z
        self.roof_hw = 0.62
        self.rr = 0.065  # roof edge radius
        self.rs = 0.07  # shoulder radius
        self.crown = 0.04
        self.tumble = 0.035
        self.tuck = 0.045
        self.nose_len = 0.55
        self.nose_p = 4.0
        self.tail_len = 0.45
        self.tail_p = 4.5
        self.roll_tf = 0.1
        self.roll_bf = 0.16
        self.roll_tr = 0.08
        self.roll_br = 0.15
        self.lean_f = 0.6
        self.lean_r = 0.3
        self.screen_k = 1.4
        self.back_k = 1.4
        self.boxy = False
        self.lamps = 'modern'
        self.bumper = 'body'  # body (painted, black lower lip) / chrome / plastic
        self.cladding = False
        self.roof_rails = False
        self.tinted = True
        self.paint = 0xEDEDED
        self.dirt = 0.18
        self.near = 24.0
        self.doors = 4
        self.hatch = False
        self.seat_mat = 'seat'
        self.wheel_names = ('wheel_fl', 'wheel_fr', 'wheel_rl', 'wheel_rr')
        self.wheel_parts_spin = True
        self.steering = None  # (pivot, rot) for a drivable car's 'steering_column'/'steering_wheel' parts
        self.driver = (-0.38, 0.52, -0.05)  # hip point of the driver's seat (left-hand drive)
        self.rear_seat_z = -0.95
        self.plate_text = None
        self.mirror_paint = 'paint'
        self.handle_mat = 'paint'
        for k, v in kw.items():
            if not hasattr(self, k):
                raise AttributeError(k)
            setattr(self, k, v)
        if self.gh_end is None:
            self.gh_end = self.deck_z


class CarBody:
    def __init__(self, s):
        self.s = s
        self.hw = s.width / 2
        self.z0f = s.zf - s.nose_len
        self.z0r = s.zr + s.tail_len
        self.Ra = s.wheel_r + 0.065
        self.x_in = s.track - s.tyre_w / 2 - 0.05

    # Plan --------------------------------------------------------------------------------------------------------
    def X(self, z):
        s = self.s
        if z > self.z0f:
            t = min(1.0, (z - self.z0f) / s.nose_len)
            return self.hw * max(0.0, 1 - t ** 2) ** (1 / s.nose_p)
        if z < self.z0r:
            t = min(1.0, (self.z0r - z) / s.tail_len)
            return self.hw * max(0.0, 1 - t ** 2) ** (1 / s.tail_p)
        return self.hw

    def front_out(self, x):
        s = self.s
        q = min(1.0, abs(x) / self.hw)
        return self.z0f + s.nose_len * math.sqrt(max(0.0, 1 - q ** s.nose_p))

    def rear_out(self, x):
        s = self.s
        q = min(1.0, abs(x) / self.hw)
        return self.z0r - s.tail_len * math.sqrt(max(0.0, 1 - q ** s.tail_p))

    # Elevation ---------------------------------------------------------------------------------------------------
    def belt(self, z):
        s = self.s
        t = (s.cowl_z - z) / (s.cowl_z - s.gh_end)
        return s.belt_f + (s.belt_r - s.belt_f) * min(1.0, max(0.0, t))

    def top_c(self, z):
        s = self.s
        if z >= s.cowl_z:
            t = (z - s.cowl_z) / (s.zf - s.cowl_z)
            return s.belt_f + (s.hood_front - s.belt_f) * t ** 1.5
        if z >= s.gh_end:
            return self.belt(z)
        t = (s.gh_end - z) / max(0.01, s.gh_end - s.zr)
        return s.belt_r + (s.tail_top - s.belt_r) * t ** 2

    def crown(self, z):
        s = self.s
        c = s.crown * 0.8
        # Fades to nothing at the cowl (the screen's base is straight) and at the deck.
        if s.gh_end - 0.05 <= z <= s.cowl_z + 0.25:
            if z > s.cowl_z:
                return c * smooth(s.cowl_z, s.cowl_z + 0.25, z)
            if z < s.gh_end:
                return c * smooth(s.gh_end, s.gh_end - 0.05, z)
            return 0.0
        return c

    def bot_c(self, z):
        s = self.s
        fa, ra = s.front_axle, s.rear_axle
        y = s.sill
        y += (s.bump_f - s.sill) * smooth(fa + self.Ra * 0.5, fa + self.Ra + 0.1, z)
        y += (s.bump_r - s.sill) * smooth(ra - self.Ra * 0.5, ra - self.Ra - 0.1, z)
        return y

    def arch(self, z):
        best = -1.0
        for za in (self.s.front_axle, self.s.rear_axle):
            dz = z - za
            if abs(dz) < self.Ra:
                best = max(best, self.s.wheel_r + math.sqrt(self.Ra ** 2 - dz * dz) * 0.97)
        return best

    def dists(self, x, z):
        return max(0.0, self.front_out(x) - z), max(0.0, z - self.rear_out(x))

    def top_y(self, x, z, xb):
        s = self.s
        df, dr = self.dists(x, z)
        y = self.top_c(z)
        if xb > 1e-6:
            y += self.crown(z) * max(0.0, 1 - (abs(x) / xb) ** 2)
        return y - roll(df, s.roll_tf) - roll(dr, s.roll_tr)

    def bot_y(self, x, z):
        s = self.s
        df, dr = self.dists(x, z)
        return self.bot_c(z) + roll(df, s.roll_bf) + roll(dr, s.roll_br)

    def lean(self, p):
        """Leans the fascias: the top of the nose and of the tail set back from the bumper line."""
        s = self.s
        x, y, z = p
        df, dr = self.dists(x, z)
        yb = 0.45
        if df < 0.4:
            k = (1 - df / 0.4) ** 2
            z -= k * s.lean_f * (max(0.0, y - yb) ** 2 + 0.6 * max(0.0, yb - y) ** 2)
        if dr < 0.4:
            k = (1 - dr / 0.4) ** 2
            z += k * s.lean_r * (max(0.0, y - yb) ** 2 + 0.6 * max(0.0, yb - y) ** 2)
        return (x, y, z)

    # Sections ----------------------------------------------------------------------------------------------------
    def xb(self, z):
        X = self.X(z)
        k = min(1.0, X / (self.hw * 0.5))
        return max(0.0, X - (self.s.tumble + self.s.rs) * k)

    def lower_half(self, z):
        s = self.s
        X = self.X(z)
        k = min(1.0, X / (self.hw * 0.5))
        xb = self.xb(z)
        rs = s.rs * k
        tumble = s.tumble * k
        xin = min(self.x_in, X * 0.7)
        arch = self.arch(z)
        pts = []
        yf = self.bot_y(0, z)
        pts.append((0.0, yf))
        y1 = self.bot_y(xin, z)
        pts.append((xin, y1))
        pts.append((xin, max(y1, arch)))
        tuck = s.tuck * k
        x3 = X - tuck
        y3 = max(self.bot_y(x3, z), arch)
        pts.append((x3, y3))
        # The shoulder's top.
        xs = X - tumble
        yt = self.top_y(xs, z, xb)
        ysh = yt - rs
        for t in (0.25, 0.55, 0.8):
            y = y3 + (ysh - y3) * t
            lo = smooth(s.sill + 0.22, s.sill, y)
            up = smooth(y3 + (ysh - y3) * 0.6, ysh, y)
            x = X - tuck * lo - tumble * up
            pts.append((x, y))
        pts.append((xs, self.top_y(xs, z, xb) - rs))
        xc = xs - rs * 0.29
        pts.append((xc, self.top_y(xc, z, xb) - rs * 0.29))
        pts.append((xb, self.top_y(xb, z, xb)))
        pts.append((xb * 0.55, self.top_y(xb * 0.55, z, xb)))
        pts.append((0.0, self.top_y(0.0, z, xb)))
        return pts

    def gh_y(self, z):
        s = self.s
        if z >= s.ws_top_z:
            t = (s.cowl_z - z) / (s.cowl_z - s.ws_top_z)
            b = self.belt(s.cowl_z)
            return b + (s.roof - b) * (1 - (1 - min(1.0, max(0.0, t))) ** s.screen_k)
        if z >= s.roof_end_z:
            t = (z - s.roof_end_z) / max(0.01, s.ws_top_z - s.roof_end_z)
            return s.roof - 0.02 * (2 * t - 1) ** 2
        t = (s.roof_end_z - z) / max(0.01, s.roof_end_z - s.gh_end)
        t = min(1.0, max(0.0, t))
        b = self.belt(s.gh_end)
        return s.roof - 0.02 - (s.roof - 0.02 - b) * t ** s.back_k

    def gh_xr(self, z):
        s = self.s
        xb = self.xb(z)
        if z >= s.ws_top_z:
            t = (s.cowl_z - z) / (s.cowl_z - s.ws_top_z)
            f = 1 - (1 - min(1.0, max(0.0, t))) ** 1.2
            return xb + (s.roof_hw - xb) * f
        if z >= s.roof_end_z:
            return s.roof_hw
        t = (s.roof_end_z - z) / max(0.01, s.roof_end_z - s.gh_end)
        t = min(1.0, max(0.0, t))
        return s.roof_hw + (min(s.roof_hw, xb * 0.97) - s.roof_hw) * t ** 1.5 if xb * 0.97 < s.roof_hw else s.roof_hw + (xb * 0.97 - s.roof_hw) * t

    def gh_half(self, z):
        s = self.s
        xb = self.xb(z)
        y0 = self.top_y(xb, z, xb)
        yg = self.gh_y(z)
        xr = self.gh_xr(z)
        rr = min(s.rr, max(0.0, yg - y0) * 0.5)
        ye = yg - rr
        h = max(0.0, ye - y0)
        pts = []
        pts.append((xb, y0))

        def side(y):
            t = 0 if h <= 1e-9 else (y - y0) / h
            return xb + (xr - xb) * t
        y1 = y0 + min(0.028, h * 0.2)
        y3 = ye - min(0.03, h * 0.2)
        y2 = (y1 + y3) / 2
        for y in (y1, y2, y3, ye):
            pts.append((side(y), y))
        pts.append((xr - rr * 0.29, yg - rr * 0.29))
        xe = max(0.0, xr - rr)
        cr = s.crown
        pts.append((xe, yg))
        pts.append((max(0.0, xe - 0.045), yg + cr * (1 - (max(0.0, xe - 0.045) / max(xe, 1e-6)) ** 2) if xe > 0 else yg))
        pts.append((xe * 0.5, yg + cr * 0.75))
        pts.append((0.0, yg + cr))
        return pts

    # Stations ----------------------------------------------------------------------------------------------------
    def lower_stations(self):
        s = self.s
        zs = set()
        for k in range(9):
            th = math.pi / 2 * k / 8
            zs.add(self.z0f + s.nose_len * math.sin(th))
            zs.add(self.z0r - s.tail_len * math.sin(th))
        for za in (s.front_axle, s.rear_axle):
            for k in range(-5, 6):
                zs.add(za + self.Ra * math.sin(k / 5 * math.pi / 2))
            zs.add(za + self.Ra + 0.04)
            zs.add(za - self.Ra - 0.04)
        for z in (s.cowl_z, s.cowl_z + 0.09, s.cowl_z + 0.3, s.gh_end, s.gh_end - 0.04, s.b_z):
            zs.add(z)
        zs = sorted(z for z in zs if s.zr <= z <= s.zf)
        # Fill long gaps.
        out = [zs[0]]
        for z in zs[1:]:
            gap = z - out[-1]
            if gap > 0.42:
                n = int(gap / 0.4) + 1
                base = out[-1]
                for i in range(1, n):
                    out.append(base + gap * i / n)
            if z - out[-1] > 0.008:
                out.append(z)
        out[-1] = zs[-1]  # the nose and the tail themselves must stay (the fascia closes there)
        return sorted(set(round(z, 4) for z in out))

    def gh_stations(self):
        s = self.s
        zs = {s.cowl_z, s.cowl_z - 0.04, s.ws_top_z, s.ws_top_z + 0.05, s.roof_end_z, s.roof_end_z - 0.05, s.gh_end, s.gh_end + 0.04,
              s.b_z - 0.045, s.b_z + 0.045, s.c_z, s.cowl_z - 0.11}
        if s.q_z is not None:
            zs |= {s.c_z - 0.07, s.q_z}
        for k in range(1, 5):
            t = k / 5
            zs.add(s.cowl_z + (s.ws_top_z - s.cowl_z) * t)
            zs.add(s.roof_end_z + (s.gh_end - s.roof_end_z) * t)
        n = int((s.ws_top_z - s.roof_end_z) / 0.4)
        for k in range(1, n + 1):
            zs.add(s.ws_top_z + (s.roof_end_z - s.ws_top_z) * k / (n + 1))
        zs = sorted((z for z in zs if s.gh_end <= z <= s.cowl_z), reverse=True)
        out = [zs[0]]
        for z in zs[1:]:
            if out[-1] - z > 0.01:
                out.append(z)
        return out[::-1]


def full_ring(half, z, closed=True):
    """The right half (from the bottom centre up and over) and the left half mirrored back down, as 3D points."""
    right = [(x, y, z) for x, y in half]
    left = [(-x, y, z) for x, y in reversed(half[1:-1])] if closed else [(-x, y, z) for x, y in reversed(half[:-1])]
    if closed:
        return right + left
    return [(-x, y, z) for x, y in half[:-1]] + list(reversed(right))


def lower_body(s, B):
    zs = B.lower_stations()
    halves = [B.lower_half(z) for z in zs]
    nh = len(halves[0])
    rings = []
    for z, h in zip(zs, halves):
        ring = [B.lean(p) for p in full_ring(h, z)]
        rings.append(ring)
    n = len(rings[0])

    def band(k):
        """Band k of the ring as the band index on the right half."""
        return k if k < nh - 1 else n - 1 - k

    def mats(i, k):
        b = band(k)
        z0, z1 = zs[i], zs[i + 1]
        zm = (z0 + z1) / 2
        if b == 0:
            return 'tl_under'
        if b == 1:
            return 'tl_liner'
        if b == 2:
            return 'tl_liner' if B.arch(zm) > B.bot_c(zm) + 0.01 else 'tl_under'
        if b == 3:
            if s.cladding:
                return 'plastic'
            if s.bumper == 'body' and (zm > s.front_axle + B.Ra + 0.02 or zm < s.rear_axle - B.Ra - 0.02):
                return 'plastic'
            return 'paint'
        if b >= 9:
            if s.gh_end + 0.001 < zm < s.cowl_z - 0.001:
                return None
            if s.cowl_z < zm < s.cowl_z + 0.09:
                return 'trim'
        return 'paint'

    g = K.geom.loft(rings, 'paint', closed=True, smooth=50, mats=mats)
    _orient_like(g, np.array([1.0, 0, 0]), lambda p: p.v.mean(axis=0)[0] > B.hw * 0.9 and abs(p.v.mean(axis=0)[2]) < 0.3)
    return g, rings, zs


def _orient_like(g, want, pick):
    """Flips the whole loft if a band picked as a reference faces the wrong way (a loft's winding is consistent)."""
    for p in g.polys:
        if pick(p):
            if np.dot(face_normal(p.v), want) < 0:
                K.flip(g)
            return g
    return g


def glasshouse(s, B):
    zs = B.gh_stations()
    halves = [B.gh_half(z) for z in zs]
    nh = len(halves[0])
    rings = [full_ring(h, z, closed=False) for z, h in zip(zs, halves)]
    n = len(rings[0])
    boxy = s.boxy
    seal = 'chrome' if boxy else 'trim'

    def band(k):
        # Open ring: left half first (from the left base up), then the right half back down.
        return k if k < nh - 1 else n - 2 - k

    def side_kind(zm):
        if zm > s.cowl_z - 0.11:
            return 'sail'
        if abs(zm - s.b_z) < 0.045:
            return 'b'
        if zm > s.c_z:
            return 'glass'
        if s.q_z is not None and s.c_z - 0.07 < zm <= s.c_z:
            return 'b'
        if s.q_z is not None and s.q_z < zm <= s.c_z - 0.07:
            return 'quarter'
        return 'c'

    def mats(i, k):
        b = band(k)
        zm = (zs[i] + zs[i + 1]) / 2
        if b <= 3:
            kind = side_kind(zm)
            if kind in ('glass', 'quarter'):
                if b in (0, 3):
                    return seal
                return 'glass' if kind == 'glass' or not s.cladding else 'tl_glass_tint'
            if kind == 'sail':
                return 'paint' if boxy else 'trim'
            if kind == 'b':
                return 'paint' if boxy else 'trim'
            return 'paint'
        if b in (4, 5):
            return 'paint'
        # The top.
        if zm > s.ws_top_z:
            if zm > s.cowl_z - 0.04 or zm < s.ws_top_z + 0.05 or b == 6:
                return 'rubber' if boxy else 'tl_frit'
            return 'glass'
        if zm < s.roof_end_z:
            if zm > s.roof_end_z - 0.05 or zm < s.gh_end + 0.04 or b == 6:
                return 'rubber' if boxy else 'tl_frit'
            return 'glass'
        return 'paint'

    g = K.geom.loft(rings, 'paint', closed=False, smooth=50, mats=mats)
    _orient_like(g, np.array([0, 1.0, 0]), lambda p: abs(p.v.mean(axis=0)[0]) < 0.2 and s.roof_end_z < p.v.mean(axis=0)[2] < s.ws_top_z)
    return g, rings, zs


# ---------------------------------------------------------------------------------------------------------------
# Front and back: lamps, grilles, bumpers, plates


def front_rear(s, B, C, model):
    g = Geo(50)
    hw = B.hw
    dims = model.tl_dims
    if s.lamps == 'modern':
        L, H = 0.46, 0.15
        dims['tl_head'] = (L, H)
        for side in (-1, 1):
            xc = side * (hw - 0.3)
            y = s.hood_front - 0.1
            D = normalize(np.array([-side * 0.45, -0.1, -1.0]))
            U, V = lamp_axes(D, side, L, H)
            shape = K.taper_shape(0.22, 0.0, 0.0, 0.18, round_=0.12)
            g.extend(K.patch(C, (xc, y, s.zf), U, V, D, 'tl_head', na=8, nb=3, shape=shape, offset=0.005, back=1.0))
        # The grille between them, a wide intake low in the bumper, fog lamps.
        Lg, Hg = 0.62, 0.12
        dims['tl_grille'] = (Lg, Hg)
        g.extend(K.patch(C, (0, s.hood_front - 0.11, s.zf), (Lg, 0, 0), (0, Hg, 0), (0, 0, -1), 'tl_grille', na=6, nb=2,
                         shape=K.taper_shape(0.0, 0.0, 0.08, 0.08, round_=0.1), offset=0.006, back=1.0))
        g.extend(K.patch(C, (0, 0.36, s.zf), (1.0, 0, 0), (0, 0.13, 0), (0, 0, -1), 'tl_mesh', na=8, nb=2,
                         shape=K.taper_shape(0.0, 0.0, 0.06, 0.06, round_=0.08), offset=0.006, back=1.0))
        dims['tl_fog'] = (0.13, 0.09)
        for side in (-1, 1):
            g.extend(K.patch(C, (side * (hw - 0.2), 0.36, s.zf), (0.13 * side, 0, 0), (0, 0.09, 0), normalize([-side * 0.5, 0, -1]), 'tl_fog',
                             na=3, nb=2, offset=0.006, back=1.0))
        # Tail lamps wrapping round the corners.
        Lt, Ht = 0.4, 0.13
        dims['tl_tail'] = (Lt, Ht)
        for side in (-1, 1):
            xc = side * (hw - 0.21)
            y = s.belt_r - (0.12 if not s.hatch else 0.3)
            D = normalize(np.array([-side * 0.7, -0.05, 1.0]))
            U, V = lamp_axes(D, side, Lt, Ht)
            shape = K.taper_shape(0.0, 0.0, 0.0, 0.25, round_=0.1)
            g.extend(K.patch(C, (xc, y, s.zr), U, V, D, 'tl_tail', na=7, nb=2, shape=shape, offset=0.005, back=1.0))
        # A reflector strip low on the back bumper and a black diffuser.
        for side in (-1, 1):
            g.extend(K.patch(C, (side * (hw - 0.18), 0.42, s.zr), (0.16, 0, 0), (0, 0.03, 0), normalize([-side * 0.4, 0, 1]), 'redlight',
                             na=2, nb=1, offset=0.004, back=1.0))
    else:
        # The old saloon: rectangular lamp units in a chrome surround, a chrome grille right across between them.
        Ls, Hs = 0.36, 0.17
        dims['tl_head_sq'] = (Ls, Hs)
        dims['tl_grille_old'] = (0.62, 0.17)
        y = s.hood_front - 0.13
        for side in (-1, 1):
            g.extend(K.patch(C, (side * (hw - 0.24), y, s.zf), (Ls, 0, 0), (0, Hs, 0), (0, 0, -1), 'tl_head_sq', na=3, nb=1, offset=0.012,
                             back=1.0, skirt=0.012, skirt_mat='chrome'))
            g.extend(K.patch(C, (side * (hw - 0.07), 0.48, s.zf), (0.1, 0, 0), (0, 0.06, 0), (0, 0, -1), 'tl_amber', na=1, nb=1, offset=0.01,
                             back=1.0, skirt=0.01, skirt_mat='chrome'))
        g.extend(K.patch(C, (0, y, s.zf), (0.62, 0, 0), (0, 0.17, 0), (0, 0, -1), 'tl_grille_old', na=4, nb=1, offset=0.014, back=1.0,
                         skirt=0.014, skirt_mat='chrome'))
        for side in (-1, 1):
            g.extend(K.patch(C, (side * (hw - 0.3), s.tail_top - 0.16, s.zr), (0.5 * side, 0, 0), (0, 0.16, 0), (0, 0, 1), 'tl_tail_old',
                             na=4, nb=1, offset=0.01, back=1.0, skirt=0.01, skirt_mat='chrome'))
    # Bumpers.
    if s.bumper == 'chrome':
        g.extend(chrome_bumper(s, B, front=True))
        g.extend(chrome_bumper(s, B, front=False))
    # Plates.
    yfp = 0.4 if s.bumper != 'chrome' else 0.27
    g.extend(K.patch(C, (0, yfp, s.zf), (-0.52, 0, 0), (0, 0.112, 0), (0, 0, -1), 'plate', na=2, nb=1, offset=0.008, back=1.0,
                     skirt=0.008, skirt_mat='trim'))
    yrp = s.tail_top - 0.25 if s.bumper != 'chrome' else 0.6
    if s.hatch:
        yrp = s.belt_r - 0.28
    g.extend(K.patch(C, (0, yrp, s.zr), (0.52, 0, 0), (0, 0.112, 0), (0, 0, 1), 'plate', na=2, nb=1, offset=0.008, back=1.0,
                     skirt=0.008, skirt_mat='trim'))
    return g


def lamp_axes(D, side, L, H):
    """Across (pointing outboard) and up axes of a lamp seen along D."""
    U = normalize(np.cross(D, [0, 1.0, 0]))
    if U[0] * side < 0:
        U = -U
    V = normalize(np.cross(U, D))
    if V[1] < 0:
        V = -V
    return U * L, V * H


def chrome_bumper(s, B, front=True):
    """A chrome bar following the plan of the nose (or tail) with a black rubber strip and overriders."""
    g = Geo(40)
    y0, y1 = (0.25, 0.4) if front else (0.27, 0.42)
    xs = np.linspace(-B.hw - 0.01, B.hw + 0.01, 13)
    rings = []
    for x in xs:
        z = (B.front_out(min(abs(x), B.hw * 0.999)) + 0.035) if front else (B.rear_out(min(abs(x), B.hw * 0.999)) - 0.035)
        wrap = max(0.0, abs(x) - B.hw * 0.86) * 0.9
        z = z - wrap if front else z + wrap
        f = 1 if front else -1
        rings.append([(x, y0, z - f * 0.06), (x, y0, z), (x, y0 + 0.02, z + f * 0.015), (x, y1 - 0.02, z + f * 0.015), (x, y1, z), (x, y1, z - f * 0.06)])
    loft = geom.loft(rings, 'chrome', closed=False, smooth=40)
    K.orient(loft, (0, (y0 + y1) / 2, (B.s.zf - 0.3) if front else (B.s.zr + 0.3)))
    g.extend(loft)
    strip = []
    for x in xs[1:-1]:
        z = (B.front_out(min(abs(x), B.hw * 0.999)) + 0.052) if front else (B.rear_out(min(abs(x), B.hw * 0.999)) - 0.052)
        wrap = max(0.0, abs(x) - B.hw * 0.86) * 0.9
        z = z - wrap if front else z + wrap
        strip.append((x, (y0 + y1) / 2, z))
    g.extend(geom.tube(strip, 0.022, 'rubber', n=5))
    for side in (-1, 1):
        x = side * B.hw * 0.45
        z = (B.front_out(x) + 0.08) if front else (B.rear_out(x) - 0.08)
        g.extend(K.hbox((x - 0.035, y0 - 0.03, z - 0.07), (x + 0.035, y1 + 0.06, z + 0.03), 'rubber', r=0.02))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Side details: mirrors, handles, indicators, wipers, aerial, rails, cladding


def side_details(s, B, C, model):
    g = Geo(45)
    hw = B.hw
    # Door handles: at the shoulder line, behind each door's front edge.
    zs = [s.b_z - 0.18 if s.doors == 4 else s.b_z - 0.2]
    if s.doors == 4:
        zs.append(s.c_z + 0.1 if s.q_z is None else s.c_z - 0.25)
    yh = s.belt_f - 0.1
    for side in (-1, 1):
        for z in zs:
            p, n = C.hit((side * (hw + 0.5), yh, z), (-side, 0, 0))
            if p is None:
                continue
            c = p + n * 0.012
            g.extend(K.hbox(c - [0.012, 0.016, 0.075], c + [0.012, 0.016, 0.075], s.handle_mat, r=0.008))
            g.extend(K.patch(C, (side * hw, yh, z), (0, 0, 0.17), (0, 0.045, 0), (-side, 0, 0), 'trim', na=1, nb=1, offset=0.0015, back=0.8))
    # Mirrors on the doors at the foot of the A-pillars.
    zm = s.cowl_z - 0.12
    ym = B.belt(zm) + 0.07
    for side in (-1, 1):
        p, n = C.hit((side * (hw + 0.5), ym, zm), (-side, 0, 0))
        if p is None:
            p = np.array([side * hw, ym, zm])
        m = K.mirror(p - [side * 0.01, 0, 0], side, s.mirror_paint, size=(0.21, 0.13, 0.08) if not s.boxy else (0.15, 0.1, 0.06))
        g.extend(m)
    # Side indicators behind the front arches (modern).
    if not s.boxy:
        for side in (-1, 1):
            z = s.front_axle - B.Ra - 0.12
            g.extend(K.patch(C, (side * hw, 0.72, z), (0, 0, 0.07 * side), (0, 0.025, 0), (-side, 0, 0), 'tl_amber', na=1, nb=1,
                             offset=0.003, back=0.8))
    # Wipers parked at the foot of the screen.
    yc = B.belt(s.cowl_z) + 0.012
    for x0 in (-0.62, 0.05):
        z0 = s.cowl_z - 0.05
        g.extend(K.wiper((x0, yc + 0.012, z0 + 0.03), (x0 + 0.52, yc + 0.03, z0 - 0.02)))
    # Aerial (a shark fin on modern cars, a whip on the old one).
    za = s.roof_end_z + 0.12
    if s.boxy:
        p = np.array([hw - 0.08, B.top_y(hw - 0.12, s.cowl_z + 0.2, B.xb(s.cowl_z + 0.2)), s.cowl_z + 0.15])
        g.extend(geom.cyl(p, p + [0.0, 0.75, -0.22], 0.004, 'chrome', n=4, caps=False))
    elif not s.roof_rails:
        y = B.gh_y(za) + s.crown
        fin = Geo(40)
        prof = [(za - 0.08, y), (za + 0.07, y), (za - 0.04, y + 0.06), (za - 0.08, y + 0.055)]
        rings = []
        for x in (-0.025, 0.0, 0.025):
            sc = 1.0 - abs(x) / 0.03 * 0.3
            rings.append([(x, y + (yy - y) * (1 if x == 0 else 0.6), za + (zz - za) * sc) for zz, yy in prof])
        fin = geom.loft(rings, 'trim', closed=True, smooth=40)
        K.orient(fin, (0, y + 0.02, za - 0.03))
        g.extend(fin)
    # Roof rails.
    if s.roof_rails:
        for side in (-1, 1):
            x = side * (s.roof_hw - 0.06)
            z0, z1 = s.ws_top_z - 0.12, s.roof_end_z + 0.05
            ytop = B.gh_y((z0 + z1) / 2) + s.crown * 0.3
            pts = [(x, ytop + 0.0, z0 + 0.05), (x, ytop + 0.05, z0 - 0.03), (x, ytop + 0.055, z1 + 0.15), (x, ytop + 0.0, z1 + 0.05)]
            g.extend(geom.tube(pts, 0.018, 'tl_dark_chrome', n=6))
            for z in (z0 + 0.02, z1 + 0.08):
                g.extend(K.hbox((x - 0.025, ytop - 0.02, z - 0.04), (x + 0.025, ytop + 0.03, z + 0.04), 'trim', r=0.01))
    # Arch flares (cladding) on the SUV.
    if s.cladding:
        for za in (s.front_axle, s.rear_axle):
            for side in (-1, 1):
                g.extend(arch_flare(s, B, C, za, side))
    return g


def arch_flare(s, B, C, za, side):
    rows_in, rows_out = [], []
    n = 12
    R = B.Ra
    for k in range(n + 1):
        a = math.pi * k / n
        dz, dy = math.cos(a), math.sin(a)
        pin = np.array([0, s.wheel_r + dy * (R + 0.005), za + dz * (R + 0.005)])
        pout = np.array([0, s.wheel_r + dy * (R + 0.075), za + dz * (R + 0.075)])
        hi, ni = C.hit((side * (B.hw + 0.5), pin[1], pin[2]), (-side, 0, 0))
        ho, no = C.hit((side * (B.hw + 0.5), pout[1], pout[2]), (-side, 0, 0))
        if hi is None or ho is None:
            continue
        rows_in.append((hi + np.array([side * 0.022, 0, 0]), hi))
        rows_out.append((ho + np.array([side * 0.012, 0, 0]), ho))
    g = Geo(40)
    for i in range(len(rows_in) - 1):
        a0, a1 = rows_in[i][0], rows_in[i + 1][0]
        b0, b1 = rows_out[i][0], rows_out[i + 1][0]
        q = np.array([a0, a1, b1, b0])
        g.add(q, 'plastic')
        e0, e1 = rows_in[i][1], rows_in[i + 1][1]
        g.add(np.array([e0, e1, a1, a0]), 'plastic')
    K.orient(g, (0, s.wheel_r, za))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Decals: shut lines, fuel flap


def shut_lines(s, B, model):
    out = []
    hw = B.hw
    dark = (22, 22, 22, 175)
    light = (255, 255, 255, 40)
    zc = (s.zf + s.zr) / 2
    L = s.zf - s.zr
    for side in (-1, 1):
        cv = K.Canvas((side * hw, 0.7, zc), (0, 0, -side * L), (0, 1.2, 0), ppm=220)
        if side < 0:
            cv = K.Canvas((side * hw, 0.7, zc), (0, 0, L), (0, 1.2, 0), ppm=220)
        x = side * hw
        y_low = s.sill + 0.03
        # Front door: its front edge just behind the front arch, up to the belt.
        zf_d = min(s.cowl_z - 0.08, s.front_axle - B.Ra - 0.08)
        top = s.belt_f - 0.005
        cv.line([(x, top, zf_d + 0.02), (x, top - 0.1, zf_d), (x, y_low + 0.05, zf_d), (x, y_low, zf_d - 0.04)], dark, 4, light)
        cv.line([(x, y_low, zf_d - 0.04), (x, y_low, s.b_z + 0.02)], dark, 4)
        cv.line([(x, y_low, s.b_z), (x, B.belt(s.b_z), s.b_z)], dark, 4, light)
        if s.doors == 4:
            zr_d = s.c_z - 0.02 if s.q_z is None else s.c_z - 0.08
            ra = s.rear_axle
            pts = [(x, B.belt(zr_d), zr_d)]
            if zr_d < ra + B.Ra + 0.05:
                # Round the rear arch.
                for k in range(8):
                    a = math.pi / 2 * (1 - k / 7) * 0.9
                    pts.append((x, s.wheel_r + (B.Ra + 0.035) * math.sin(a), ra + (B.Ra + 0.035) * math.cos(a)))
                pts.append((x, y_low, ra + B.Ra + 0.035))
            else:
                pts.append((x, y_low, zr_d))
            cv.line(pts, dark, 4, light)
            cv.line([(x, y_low, pts[-1][2]), (x, y_low, s.b_z - 0.02)], dark, 4)
        # The front wing / bonnet line along the shoulder.
        # The fuel flap on the right rear wing.
        if side > 0:
            zfl = s.rear_axle + B.Ra + 0.25 if not s.hatch else s.rear_axle - 0.05
            yfl = s.belt_r - (0.12 if not s.hatch else 0.3)
            cv.line([(x, yfl - 0.07, zfl - 0.08), (x, yfl - 0.07, zfl + 0.08), (x, yfl + 0.07, zfl + 0.08), (x, yfl + 0.07, zfl - 0.08),
                     (x, yfl - 0.07, zfl - 0.08)], dark, 4)
        # A subtle crease: a highlight along the swage line.
        yc = s.belt_f - 0.17
        cv.line([(x, yc, s.zf - 0.4), (x, yc + 0.03, s.zr + 0.5)], (255, 255, 255, 28), 6)
        cv.line([(x, yc - 0.03, s.zf - 0.4), (x, yc, s.zr + 0.5)], (0, 0, 0, 30), 4)
        d = cv.decal(depth=0.35)
        out.append(d)
    # Bonnet: along the shoulders and across the cowl, seen from above.
    cv = K.Canvas((0, 1.0, (s.cowl_z + s.zf) / 2), (-hw * 2.2, 0, 0), (0, 0, s.zf - s.cowl_z + 0.2), ppm=220)
    xb = B.xb(s.cowl_z + 0.3) + 0.01
    zz = np.linspace(s.cowl_z + 0.1, s.zf, 12)
    for side in (-1, 1):
        pts = [(side * min(xb, B.xb(z) + 0.01) * 0.93, 1.0, z) for z in zz]
        cv.line(pts, dark, 4)
    cv.line([(-xb, 1.0, s.cowl_z + 0.1), (xb, 1.0, s.cowl_z + 0.1)], dark, 4)
    d = cv.decal(depth=0.8, region=lambda P: (P[:, 2] > s.cowl_z - 0.02) & (P[:, 1] < s.belt_f + 0.03))
    d.n = np.array([0, 1.0, 0])
    out.append(d)
    if not s.hatch:
        # The boot lid.
        cv = K.Canvas((0, 1.0, (s.gh_end + s.zr) / 2), (hw * 2.2, 0, 0), (0, 0, s.gh_end - s.zr + 0.2), ppm=220)
        cv.line([(-xb, 1.0, s.gh_end - 0.03), (xb, 1.0, s.gh_end - 0.03)], dark, 4)
        for side in (-1, 1):
            zz = np.linspace(s.gh_end - 0.03, s.zr + 0.08, 8)
            cv.line([(side * min(xb, B.xb(z) - 0.02) * 0.93, 1.0, z) for z in zz], dark, 4)
        d = cv.decal(depth=0.8, region=lambda P: (P[:, 2] < s.gh_end + 0.02) & (P[:, 1] < s.belt_r + 0.03))
        d.n = np.array([0, 1.0, 0])
        out.append(d)
        cv = K.Canvas((0, s.tail_top - 0.1, s.zr), (hw * 2.2, 0, 0), (0, 0.4, 0), ppm=220)
        cv.line([(-hw * 0.82, s.tail_top - 0.22, s.zr), (hw * 0.82, s.tail_top - 0.22, s.zr)], dark, 4)
        d = cv.decal(depth=0.4)
        d.n = np.array([0, 0, -1.0])
        out.append(d)
    else:
        # The tailgate's edges on the back.
        cv = K.Canvas((0, (s.belt_r + 0.3) / 2, s.zr), (hw * 2.2, 0, 0), (0, s.belt_r, 0), ppm=220)
        yb = 0.45
        cv.line([(-hw * 0.72, yb, s.zr), (hw * 0.72, yb, s.zr)], dark, 4)
        d = cv.decal(depth=0.5)
        d.n = np.array([0, 0, -1.0])
        out.append(d)
    return out


# ---------------------------------------------------------------------------------------------------------------
# Inside


def interior(s, B, lower, gh, model):
    g = Geo(40)
    hw = B.hw
    xb = B.xb(0.0)
    floor = s.sill + 0.04
    # Door cards: the inside of the sides in the cabin, and the inside of the pillars and the roof (the headliner).
    for p in lower.polys:
        if p.mat != 'paint':
            continue
        c = p.v.mean(axis=0)
        if s.gh_end + 0.02 < c[2] < s.cowl_z - 0.05 and abs(c[0]) > hw * 0.6 and c[1] > floor + 0.05:
            n = face_normal(p.v)
            g.add((p.v - n * 0.035)[::-1].copy(), 'tl_door_card')
    for p in gh.polys:
        if p.mat in ('glass', 'tl_glass_tint'):
            continue
        n = face_normal(p.v)
        g.add((p.v - n * 0.02)[::-1].copy(), 'tl_headliner')
    # Floor, the bulkhead behind the rear seat (or the boot floor), the dashboard.
    zb = s.gh_end + 0.05
    g.extend(geom.box((-xb, floor, zb), (xb, floor + 0.01, s.cowl_z - 0.1), 'carpet', faces=('top',)))
    dash_z = s.cowl_z - 0.06
    dz = 0.48
    yb = B.belt(s.cowl_z)
    prof = [(dash_z, floor + 0.25), (dash_z - dz + 0.12, floor + 0.3), (dash_z - dz + 0.05, yb - 0.12), (dash_z - dz, yb - 0.02),
            (dash_z - dz + 0.1, yb + 0.03), (dash_z, yb + 0.0)]
    rings = []
    for x in (-xb + 0.01, xb - 0.01):
        rings.append([(x, y, z) for z, y in prof])
    dash = geom.loft(rings, 'dash', closed=True, cap0=True, cap1=True, smooth=40)
    K.orient(dash, (0, (floor + yb) / 2, dash_z - 0.15))
    g.extend(dash)
    dx = s.driver[0]
    # The instrument binnacle and its dials, the centre stack's screen, vents.
    binn = K.hbox((dx - 0.17, yb - 0.1, dash_z - dz - 0.02), (dx + 0.17, yb + 0.06, dash_z - dz + 0.14), 'dash', r=0.03)
    g.extend(binn)
    gp = geom.panel((dx, yb - 0.03, dash_z - dz - 0.021), 0.3, 0.1, (1, 0, 0), (0, 1, 0), 'gauges')
    K.orient(gp, (dx, yb, dash_z))
    g.extend(gp)
    sc = geom.panel((0, yb - 0.13, dash_z - dz + 0.06), 0.2, 0.12, (1, 0, 0), (0, 1, 0), 'screen' if not s.boxy else 'dash')
    K.orient(sc, (0, yb, dash_z))
    g.extend(sc)
    # Centre console between the front seats.
    g.extend(K.hbox((-0.1, floor, s.driver[2] - 0.35), (0.1, floor + 0.22, dash_z - dz + 0.1), 'dash', r=0.03))
    # Seats.
    sm = s.seat_mat
    sx = abs(s.driver[0])
    for x in (-sx, sx):
        g.extend(K.seat(x, s.driver[1], s.driver[2], w=0.5, mat=sm))
    rz = s.rear_seat_z
    if s.doors == 4 or not s.boxy:
        g.extend(K.hbox((-xb + 0.06, floor + 0.12, rz - 0.05), (xb - 0.06, s.driver[1] - 0.02, rz + 0.42), sm, r=0.05))
        back = K.hbox((-xb + 0.06, s.driver[1] - 0.05, -0.06), (xb - 0.06, s.driver[1] + 0.52, 0.06), sm, r=0.05)
        back.transform(t=(0, 0, rz - 0.1))
        K.about(back, (0, s.driver[1], rz - 0.1), rot_x(16))
        g.extend(back)
        for x in (-0.48, 0.0, 0.48):
            hr = K.hbox((x - 0.12, s.driver[1] + 0.55, rz - 0.22), (x + 0.12, s.driver[1] + 0.7, rz - 0.13), sm, r=0.04)
            g.extend(hr)
    # The parcel shelf (saloon) behind the rear seat.
    if not s.hatch:
        y = B.belt(s.gh_end) - 0.01
        g.extend(geom.box((-xb, y, s.gh_end + 0.02), (xb, y + 0.012, rz - 0.18), 'interior', faces=('top',)))
    else:
        y = B.belt(s.gh_end) - 0.06
        g.extend(geom.box((-xb, y, s.gh_end + 0.02), (xb, y + 0.012, rz - 0.18), 'interior', faces=('top',)))
    # Inside mirror.
    zi = s.ws_top_z + 0.07
    yi = B.gh_y(zi) - 0.08
    g.extend(K.hbox((-0.12, yi - 0.03, zi - 0.02), (0.12, yi + 0.03, zi + 0.01), 'trim', r=0.012))
    return g


# ---------------------------------------------------------------------------------------------------------------


def build_car(s, extra=None):
    scheme = materials.Scheme(base=s.paint, dirt=s.dirt, wear=0.08, seed=abs(hash(s.id)) % 997, gloss=0.9)
    m = Model(s.id, scheme, density=60, tex_width=512)
    m.chart_by_mat = True
    m.tinted = s.tinted
    m.tl_dims = {}
    if s.plate_text:
        m.plate_text = s.plate_text
    B = CarBody(s)
    body = m.part('body')
    lower, lrings, lzs = lower_body(s, B)
    gh, grings, gzs = glasshouse(s, B)
    body.add(lower)
    body.add(gh)
    C = K.Caster(lower, gh)
    body.add(front_rear(s, B, C, m))
    body.add(side_details(s, B, C, m))
    m.decals += shut_lines(s, B, m)
    inside = body.part('inside')
    inside.near = s.near
    inside.add(interior(s, B, lower, gh, m))
    yb = B.belt(s.cowl_z)
    if s.steering is None:
        c = np.array([s.driver[0], yb - 0.03, s.cowl_z - 0.06 - 0.48 - 0.16])
        sw = K.steering_wheel_geo((0, 0, 0), r=0.18 if not s.boxy else 0.2, column=0.25)
        K.about(sw, (0, 0, 0), rot_x(-24))
        sw.transform(t=c)
        inside.add(sw)
    else:
        piv, rot = s.steering
        col = body.part('steering_column', piv, rot)
        col.near = s.near
        wp = col.part('steering_wheel', piv)
        wp.near = s.near
        wp.add(K.steering_wheel_geo(piv, r=0.18, column=0.0))
        col.add(geom.cyl(np.asarray(piv) + [0, 0, 0.03], np.asarray(piv) + [0, 0, 0.35], 0.03, 'dash', n=6, caps=False))
    # Wheels.
    for name, z, steer in zip(s.wheel_names, (s.front_axle, s.front_axle, s.rear_axle, s.rear_axle), (True, True, False, False)):
        side = -1 if name.endswith('l') else 1
        c = (side * s.track, s.wheel_r, z)
        p = body.part(name, c)
        p.add(K.car_wheel(c, side, s.wheel_r, s.tyre_w, s.wheel_style, n=20, spokes=s.spokes,
                          mat_rim='rim' if s.wheel_style != 'steel' else 'tl_steel_wheel'))
        if s.wheel_parts_spin:
            p.spin = s.wheel_r
            p.steer = steer
    if extra:
        extra(m, s, B, C, body, inside)
    return m
