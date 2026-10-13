"""
2.0: the town's cars as real shapes - a body lofted through cross-sections from the back bumper to the front one (the
sills, the swell of the sides, the waist, the glasshouse leaning in, the roof's crown), wheel arches cut into it with
liners inside, real wheels (tyres with tread, alloy or steel rims, nuts), glass you can see the inside through (seats,
dashboard, a steering wheel), lamps, grille, plates, mirrors, handles, wipers, the shut lines of the doors.

Each car is a Spec (sizes in metres); build(spec) makes the model.
"""
import math

import numpy as np

import geom
import materials
from core import Geo, Model, normalize


class Spec:
    def __init__(self, **kw):
        # Defaults: a mid-size saloon.
        self.id = 'car'
        self.length = 4.5
        self.width = 1.78
        self.wheelbase = 2.68
        self.front_axle = 1.38  # z of the front axle
        self.wheel_r = 0.31
        self.tyre_w = 0.21
        self.track = 0.77  # x of the wheel centres
        self.rim = 'alloy'
        self.sill = 0.3
        self.belt = 0.95
        self.hood_front = 0.72
        self.cowl = 0.93
        self.cowl_z = 0.85
        self.ws_top_z = -0.05
        self.roof = 1.45
        self.roof_end_z = -1.0
        self.deck_z = -1.55
        self.deck = 1.0
        self.rear = 'saloon'  # saloon, hatch, estate, van
        self.roof_w = 0.62  # roof half width as a share of the body's
        self.nose_round = 0.42
        self.tail_round = 0.32
        self.boxy = 0.0  # 1 = sharp-edged Soviet saloon
        self.bumper = 'plastic'  # plastic, chrome, paint
        self.lamps = 'modern'  # modern, square, round
        self.b_pillar = -0.48
        self.c_pillar = None
        self.doors = 4
        self.wheel_style = 'alloy'
        self.paint = 0xD8D8D8
        self.tinted = True
        self.dirt = 0.22
        self.roof_rails = False
        self.taxi = False
        self.police = False
        self.plastic_cladding = False
        for k, v in kw.items():
            if not hasattr(self, k):
                raise AttributeError(k)
            setattr(self, k, v)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


class Body:
    """The cross-section of the body at each station z."""

    def __init__(self, s):
        self.s = s
        self.hw = s.width / 2
        self.zf = s.length / 2
        self.zr = -s.length / 2
        self.ra = s.front_axle - s.wheelbase
        self.arch_r = s.wheel_r + 0.07

    def top(self, z):
        """Height of the top of the body at z, and whether that is the cabin (roof / glass) there."""
        s = self.s
        if z >= s.cowl_z:
            # The bonnet falling to the nose, rounded over at the very front.
            t = (z - s.cowl_z) / (self.zf - s.cowl_z)
            y = s.cowl + (s.hood_front - s.cowl) * (t ** 1.3)
            y -= 0.12 * smoothstep(0.85, 1.0, t) ** 2 * (1 - s.boxy * 0.7)
            return y, 'hood'
        if z >= s.ws_top_z:
            # The windscreen: a gently curved rise from the cowl to the roof.
            t = (s.cowl_z - z) / (s.cowl_z - s.ws_top_z)
            y = s.cowl + (s.roof - s.cowl) * (math.sin(t * math.pi / 2) ** (0.85 + s.boxy * 0.3))
            return y, 'screen'
        if z >= s.roof_end_z:
            # The roof with a little crown fore and aft.
            t = (z - s.roof_end_z) / max(0.01, s.ws_top_z - s.roof_end_z)
            y = s.roof - 0.025 * (1 - math.sin(t * math.pi)) * (1 - s.boxy)
            return y, 'roof'
        if s.rear in ('hatch', 'estate', 'van'):
            # The hatch: down from the roof to the tail in a short steep fall.
            t = (s.roof_end_z - z) / max(0.01, s.roof_end_z - self.zr)
            if s.rear == 'hatch':
                y = s.roof + (s.deck - s.roof) * (t ** 1.4)
            else:
                y = s.roof - 0.05 * t - 0.25 * smoothstep(0.8, 1.0, t)
            return y, 'backlight' if t < 0.92 else 'tail'
        if z >= s.deck_z:
            t = (s.roof_end_z - z) / (s.roof_end_z - s.deck_z)
            y = s.roof + (s.deck - s.roof) * (1 - math.cos(t * math.pi / 2)) ** (0.9 - 0.2 * s.boxy)
            return y, 'backlight'
        # The boot, its lid falling slightly to the tail.
        t = (s.deck_z - z) / (s.deck_z - self.zr)
        y = s.deck - 0.04 * t - 0.1 * smoothstep(0.85, 1.0, t) ** 2 * (1 - s.boxy * 0.7)
        return y, 'boot'

    def bottom(self, z):
        """The lower edge of the sides: the sill, cut up round the wheels, lower under the bumpers."""
        s = self.s
        y = s.sill
        for za in (s.front_axle, self.ra):
            dz = z - za
            if abs(dz) < self.arch_r:
                y = max(y, s.wheel_r + math.sqrt(self.arch_r ** 2 - dz * dz) * 0.98)
        # The bumpers reach lower than the sills.
        if z > s.front_axle + self.arch_r + 0.05:
            y = min(y, 0.24)
        if z < self.ra - self.arch_r - 0.05:
            y = min(y, 0.27)
        return y

    def plan(self, z):
        """Half width at z: rounded off at the nose and tail in plan."""
        s = self.s
        k = 1.0
        rn = s.nose_round
        rt = s.tail_round
        if z > self.zf - rn:
            t = (z - (self.zf - rn)) / rn
            k = math.sqrt(max(0.0, 1 - t ** (2.2 + 2 * s.boxy))) * (1 - 0.16 * (1 - s.boxy)) + 0.16 * (1 - s.boxy)
            k = max(k, 0.0)
        elif z < self.zr + rt:
            t = ((self.zr + rt) - z) / rt
            k = math.sqrt(max(0.0, 1 - t ** (2.2 + 2 * s.boxy))) * (1 - 0.18 * (1 - s.boxy)) + 0.18 * (1 - s.boxy)
        return self.hw * k

    def ring(self, z):
        """The cross-section at z: points going from the bottom centre round the right side over the top to the left
        side and back (the left mirrors the right). Returns the points and, per segment, what part of the body it is."""
        s = self.s
        hw = self.plan(z)
        yb = self.bottom(z)
        yt, zone = self.top(z)
        ybelt = min(s.belt, yt - 0.02)
        # Cars from the nineties on are wider at the shoulder than at the sill and lean in above the waist.
        tuck = 0.05 * (1 - s.boxy)
        right = []
        kinds = []
        # Floor to the bottom corner.
        right.append((0.0, yb))
        kinds.append('floor')
        right.append((hw * 0.7, yb))
        kinds.append('floor')
        right.append((hw - tuck * 0.6 - 0.02, yb))
        kinds.append('side')
        # The side, swelling out to the waist.
        for t in (0.12, 0.35, 0.62, 0.86):
            y = yb + (ybelt - yb) * t
            x = hw * (1 - tuck * (1 - t) ** 2.2) - 0.012 * math.sin(t * math.pi) * (1 - s.boxy)
            right.append((x, y))
            kinds.append('side')
        right.append((hw, ybelt))
        kinds.append('belt')
        # Above the waist: the glasshouse leaning in to the roof, or just a rounded shoulder over the bonnet and boot.
        roof_hw = hw * s.roof_w if zone in ('screen', 'roof', 'backlight') else hw * (0.97 - 0.04 * (1 - s.boxy))
        if zone == 'screen':
            t = (yt - s.cowl) / max(0.01, s.roof - s.cowl)
            roof_hw = hw * (0.95 + (s.roof_w - 0.95) * t)
        if zone == 'backlight' and s.rear == 'saloon':
            t = (yt - s.deck) / max(0.01, s.roof - s.deck)
            roof_hw = hw * (0.95 + (s.roof_w - 0.95) * t)
        rr = 0.06 if zone in ('roof', 'screen', 'backlight') else 0.05 + 0.03 * (1 - s.boxy)
        rr *= (1 - 0.6 * s.boxy)
        ytop_side = yt - rr * 0.6
        for t in (0.08, 0.5, 0.92):
            right.append((hw + (roof_hw - hw) * t, ybelt + (ytop_side - ybelt) * t))
            kinds.append('glass_side')
        right.append((roof_hw - rr * 0.35, yt - rr * 0.1))
        kinds.append('roof_edge')
        # Across the top with a crown.
        crown = 0.04 * (1 - s.boxy * 0.7) if zone in ('roof', 'screen', 'backlight') else 0.03 * (1 - s.boxy * 0.6)
        for t in (0.82, 0.55, 0.28):
            x = (roof_hw - rr * 0.35) * t
            right.append((x, yt + crown * (1 - t * t)))
            kinds.append('top')
        right.append((0.0, yt + crown))
        kinds.append('top')
        # The full ring: right side then the left mirrored (going back down).
        pts = [(x, y) for x, y in right]
        knd = list(kinds)
        for (x, y), kk in zip(reversed(right[1:-1]), reversed(kinds[1:-1])):
            pts.append((-x, y))
            knd.append(kk)
        return [(x, y, z) for x, y in pts], knd, zone

    def stations(self):
        s = self.s
        zs = set()
        n = 64
        for i in range(n + 1):
            t = i / n
            zs.add(round(self.zr + (self.zf - self.zr) * t, 4))
        # Extra stations at the round ends, the arches, the pillars and the glass edges.
        for i in range(1, 8):
            zs.add(round(self.zf - s.nose_round * (i / 8) ** 1.5, 4))
            zs.add(round(self.zr + s.tail_round * (i / 8) ** 1.5, 4))
        for za in (s.front_axle, self.ra):
            for k in range(-10, 11):
                zs.add(round(za + self.arch_r * math.sin(k / 10 * math.pi / 2), 4))
        for z in self.pillar_edges():
            zs.add(round(z, 4))
        zs = sorted(z for z in zs if self.zr <= z <= self.zf)
        # Pinch the very ends: a last station just in from each end.
        return zs

    def pillar_edges(self):
        s = self.s
        out = [s.cowl_z, s.ws_top_z, s.ws_top_z - 0.12, s.roof_end_z, s.roof_end_z + 0.14, s.b_pillar - 0.06, s.b_pillar + 0.06]
        if s.rear == 'saloon':
            out += [s.deck_z]
        if s.c_pillar is not None:
            out += [s.c_pillar - 0.06, s.c_pillar + 0.06]
        return out

    def side_glass(self, z):
        """Is the side window band glass at z (between the A, B and C pillars)?"""
        s = self.s
        a = s.ws_top_z - 0.12
        c = s.roof_end_z + 0.14
        if s.rear in ('hatch', 'estate', 'van'):
            c = s.roof_end_z + (0.14 if s.rear == 'hatch' else -0.05)
        if not (c < z < a):
            return False
        if abs(z - s.b_pillar) < 0.06:
            return False
        if s.c_pillar is not None and abs(z - s.c_pillar) < 0.06:
            return False
        return True


def body_geo(s, B):
    zs = B.stations()
    rings = []
    kinds = None
    zones = []
    for z in zs:
        r, k, zone = B.ring(z)
        rings.append(r)
        kinds = k
        zones.append(zone)
    n = len(rings[0])

    def mats(i, k):
        kind = kinds[k]
        kind2 = kinds[(k + 1) % n]
        z0, z1 = zs[i], zs[i + 1]
        zm = (z0 + z1) / 2
        zone = zones[i] if zones[i] == zones[i + 1] else B.top(zm)[1]
        if kind == 'floor' and kind2 in ('floor', 'side'):
            return 'plastic'
        if kind == 'glass_side' and kind2 == 'glass_side' or kind == 'belt' and kind2 == 'glass_side' or kind == 'glass_side' and kind2 == 'roof_edge':
            first = kind == 'belt' or kind2 == 'belt'
            last = kind2 == 'roof_edge' or kind == 'roof_edge'
            if B.side_glass(zm):
                return 'trim' if first or last else 'glass'
            return 'paint'
        if kind2 == 'belt' and kind == 'glass_side' or kind == 'glass_side' and kind2 == 'belt':
            return 'trim' if B.side_glass(zm) else 'paint'
        if kind in ('top', 'roof_edge') and kind2 in ('top', 'roof_edge'):
            edge = kind == 'roof_edge' or kind2 == 'roof_edge'
            if zone == 'screen' and s.ws_top_z + 0.04 < zm < s.cowl_z - 0.03:
                return 'paint' if edge else 'glass'
            if zone == 'backlight' and (s.rear != 'saloon' or zm > s.deck_z + 0.03) and zm < s.roof_end_z - 0.04:
                return 'paint' if edge else 'glass'
            if zone == 'tail' and s.rear in ('estate', 'van'):
                return 'paint'
        return 'paint'

    g = geom.loft(rings, 'paint', closed=True, smooth=48, mats=mats)
    # Close the nose and the tail.
    g.extend(geom.polygon_cap(rings[0][::-1], 'paint'))
    g.extend(geom.polygon_cap(rings[-1], 'paint'))
    # The ring goes round counter-clockwise seen from the front (right side first, over the top): its outside is on
    # the right of the direction of travel... make sure every band faces out.
    out = Geo(48)
    for p in g.polys:
        out.polys.append(p)
    _face_out(out)
    return out


def _face_out(g):
    """Bands of a loft face away from the body's centre line (the x = 0, y = ~0.8 axis)."""
    from core import face_normal
    for p in g.polys:
        fn = face_normal(p.v)
        c = p.v.mean(axis=0)
        ref = np.array([0.0, 0.75, c[2]])
        d = c - ref
        # The end caps face along z.
        if abs(fn[2]) > 0.9:
            d = np.array([0, 0, c[2]])
        if np.dot(fn, d) < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
            if p.n is not None:
                p.n = p.n[::-1].copy()


def arch_liners(s, B):
    g = Geo(60)
    for za in (s.front_axle, B.ra):
        c = np.array([0.0, s.wheel_r, za])
        r = B.arch_r - 0.01
        for side in (-1, 1):
            hw = B.plan(za)
            x0 = side * (hw - 0.02)
            x1 = side * (hw - 0.42)
            a = np.array([x1, s.wheel_r, za])
            b = np.array([x0, s.wheel_r, za])
            # A half tube over the wheel, its inside showing (the profile runs back along the axis).
            liner = geom.lathe(a, b, [(1.0, r, 'plastic'), (0.0, r, 'plastic')], 'plastic', n=14, start=-5, sweep=190)
            g.extend(liner)
            # The inner wall of the well.
            g.extend(geom.lathe(a, a + (b - a) * 0.001, [(0, 0, 'plastic'), (0, r, 'plastic')], 'plastic', n=14, start=-5, sweep=190))
            g.extend(geom.lathe(a + (b - a) * 0.001, a, [(0, 0, 'plastic'), (0, r, 'plastic')], 'plastic', n=14, start=-5, sweep=190))
    return g


def arch_trim(s, B, mat='trim'):
    """A lip round each arch (black plastic on SUVs, a chrome strip on old saloons, a painted flare otherwise)."""
    g = Geo(60)
    for za in (s.front_axle, B.ra):
        for side in (-1, 1):
            pts = []
            for k in range(0, 13):
                a = math.pi * k / 12
                z = za + math.cos(a) * B.arch_r
                y = s.wheel_r + math.sin(a) * B.arch_r * 0.98
                if y < B.bottom(z) - 0.02 and k not in (0, 12):
                    pass
                x = side * (B.plan(z) + 0.004)
                pts.append((x, y, z))
            g.extend(geom.tube(pts, 0.022 if mat == 'trim' else 0.012, mat, n=6))
    return g


def lamps(s, B, part):
    """Headlamps, tail lamps, indicators, the grille, bumpers, plates."""
    zf, zr = B.zf, B.zr
    hw = B.hw
    g = Geo(40)
    yh = s.hood_front - 0.13
    # The front face: where the body is at the lamp height.
    if s.lamps == 'round':
        for side in (-1, 1):
            for k, x in enumerate((hw - 0.22, hw - 0.42)):
                c = np.array([side * x, yh, zf - 0.035])
                g.extend(geom.cyl(c - [0, 0, 0.02], c + [0, 0, 0.03], 0.085, 'chrome', n=16, caps=False))
                g.extend(geom.lathe(c + [0, 0, 0.03], c + [0, 0, 0.06], [(0, 0.08, 'headlight'), (0.02, 0.06, 'headlight'), (0.03, 0.0, 'headlight')], 'headlight', n=16))
    elif s.lamps == 'square':
        for side in (-1, 1):
            c = np.array([side * (hw - 0.3), yh, zf - 0.02])
            g.extend(geom.cbox(c, (0.36, 0.17, 0.06), 'chrome', r=0.012, mats={'front': 'headlight_box'}))
    else:
        for side in (-1, 1):
            c = np.array([side * (hw - 0.3), yh + 0.02, zf - 0.07])
            lamp = geom.cbox(c, (0.42, 0.13, 0.1), 'trim', r=0.03, segs=2, mats={'front': 'headlight_box'})
            # Swept back round the corner.
            lamp.transform(R=geom_rot_y(-side * 14), t=None)
            g.extend(_about(lamp, c, -side * 16))
    # Grille between the lamps.
    if s.lamps == 'modern':
        g.extend(geom.cbox((0, yh - 0.02, zf - 0.04), (hw * 0.75, 0.14, 0.08), 'trim', r=0.03, segs=2, mats={'front': 'grille'}))
        g.extend(geom.cbox((0, 0.38, zf - 0.02), (hw * 0.9, 0.12, 0.06), 'trim', r=0.03, segs=2, mats={'front': 'grille_mesh'}))
    else:
        g.extend(geom.cbox((0, yh, zf - 0.02), (hw * 0.62, 0.16, 0.05), 'chrome', r=0.01, mats={'front': 'grille'}))
    # Bumpers.
    bm = {'plastic': 'plastic', 'chrome': 'chrome', 'paint': 'paint'}[s.bumper]
    if s.bumper == 'chrome':
        for z, d in ((zf + 0.02, 1), (zr - 0.02, -1)):
            g.extend(geom.cbox((0, 0.36, z), (hw * 2 - 0.12, 0.11, 0.1), 'chrome', r=0.03, segs=2))
            g.extend(geom.cbox((0, 0.36, z + 0.05 * d), (hw * 2 - 0.2, 0.035, 0.02), 'rubber', r=0.008))
    elif s.bumper == 'plastic':
        for z, y in ((zf - 0.05, 0.4), (zr + 0.06, 0.42)):
            g.extend(geom.cbox((0, y, z), (B.plan(z) * 2 + 0.01, 0.2, 0.18), 'paint', r=0.07, segs=2, mats={'bottom': 'plastic'}))
            g.extend(geom.cbox((0, y - 0.13, z), (B.plan(z) * 2 - 0.08, 0.07, 0.17), 'plastic', r=0.03))
    # Plates.
    g.extend(geom.panel((0, 0.42 if s.bumper != 'chrome' else 0.25, zf + 0.055), 0.52, 0.112, (-1, 0, 0), (0, 1, 0), 'plate'))
    g.extend(geom.panel((0, 0.6, zr - 0.035), 0.52, 0.112, (1, 0, 0), (0, 1, 0), 'plate'))
    # Tail lamps.
    for side in (-1, 1):
        if s.lamps == 'modern':
            c = np.array([side * (hw - 0.22), s.deck - 0.13, zr + 0.06])
            g.extend(_about(geom.cbox(c, (0.36, 0.14, 0.1), 'trim', r=0.03, segs=2, mats={'back': 'taillight'}), c, side * 18))
        else:
            c = np.array([side * (hw - 0.25), s.deck - 0.2, zr - 0.005])
            g.extend(geom.cbox(c, (0.38, 0.16, 0.05), 'chrome', r=0.01, mats={'back': 'taillight'}))
    # Fog lamps / indicators.
    for side in (-1, 1):
        c = np.array([side * (hw - 0.2), 0.3 if s.bumper != 'chrome' else 0.48, zf + (0.03 if s.bumper != 'chrome' else -0.02)])
        g.extend(geom.cbox(c, (0.16, 0.06, 0.04), 'trim', r=0.01, mats={'front': 'indicator'}))
    return g


def geom_rot_y(deg):
    from core import rot_y
    return rot_y(deg)


def _about(g, c, deg):
    """Turns a piece about the vertical through c."""
    from core import rot_y
    c = np.asarray(c, dtype=np.float64)
    g.transform(t=-c)
    g.transform(R=rot_y(deg))
    g.transform(t=c)
    return g


def details(s, B):
    """Mirrors, handles, wipers, the aerial, the exhaust, the door shut lines (painted), roof rails."""
    g = Geo(40)
    hw = B.hw
    # Mirrors on little arms at the front of the side glass.
    zm = s.ws_top_z - 0.2
    ym = s.belt + 0.1
    for side in (-1, 1):
        x = side * (B.plan(zm) * 0.97)
        arm = geom.cbox((x + side * 0.05, ym, zm), (0.1, 0.04, 0.05), 'trim', r=0.01)
        g.extend(arm)
        housing = geom.cbox((x + side * 0.14, ym + 0.03, zm), (0.16, 0.11, 0.07), 'paint', r=0.03, segs=2, mats={'back': 'mirror_glass'})
        g.extend(housing)
    # Door handles.
    doors = [s.b_pillar + 0.05, s.ws_top_z - 0.15] if s.doors == 2 else [s.b_pillar - 0.18, s.ws_top_z - 0.25]
    if s.doors == 4:
        doors = [s.b_pillar - 0.2, s.ws_top_z - 0.3, s.roof_end_z + 0.32]
        doors = [doors[0], doors[2]]
    for side in (-1, 1):
        for zd in doors:
            x = side * (B.plan(zd) - 0.005)
            g.extend(geom.cbox((x, s.belt - 0.09, zd), (0.03, 0.03, 0.15), 'chrome' if s.boxy > 0.5 else 'paint', r=0.01))
    # Wipers lying on the bottom of the windscreen.
    for x0 in (-0.45, 0.12):
        z0 = s.cowl_z - 0.06
        y0 = s.cowl + 0.035
        g.extend(geom.tube([(x0, y0, z0), (x0 + 0.48, y0 + 0.02, z0 - 0.04)], 0.008, 'wiper', n=4))
    # The aerial.
    za = s.roof_end_z + 0.1
    g.extend(geom.tube([(0, s.roof + 0.02, za), (0, s.roof + 0.06, za - 0.05), (0, s.roof + 0.08, za - 0.12)], 0.02, 'trim', n=6))
    # The exhaust under the back bumper.
    g.extend(geom.cyl((-hw * 0.55, 0.22, B.zr + 0.25), (-hw * 0.55, 0.22, B.zr - 0.03), 0.032, 'exhaust', n=10, caps=False))
    g.extend(geom.lathe((-hw * 0.55, 0.22, B.zr - 0.03), (-hw * 0.55, 0.22, B.zr + 0.02), [(0, 0.0, 'black'), (0, 0.028, 'black')], 'black', n=10))
    if s.roof_rails:
        for side in (-1, 1):
            x = side * (B.hw * s.roof_w - 0.05)
            g.extend(geom.tube([(x, s.roof + 0.02, s.ws_top_z - 0.1), (x, s.roof + 0.07, s.ws_top_z - 0.2),
                                (x, s.roof + 0.07, s.roof_end_z + 0.15), (x, s.roof + 0.02, s.roof_end_z + 0.05)], 0.018, 'trim', n=6))
    return g


def shut_lines(s, B):
    """The gaps round the doors, bonnet and boot, painted on as thin dark lines (decals)."""
    out = []
    dark = (24, 24, 24)
    hw = B.hw
    zs = [s.cowl_z - 0.05, s.b_pillar]
    if s.doors == 4:
        zs.append(s.roof_end_z + 0.1 if s.rear != 'saloon' else s.roof_end_z - 0.05)
    for z in zs:
        for side in (-1, 1):
            out.append(materials.stripe_decal((side * hw, (s.sill + s.belt) / 2 + 0.03, z), (0, 0, 0.012),
                                              (0, s.belt - s.sill + 0.05, 0), dark, 170))
            d = out[-1]
            d.n = np.array([float(side), 0, 0])
            d.depth = 0.25
    # The bonnet's shut line across the top at the cowl and down the wings.
    out.append(materials.Decal((0, s.cowl - 0.01, s.cowl_z - 0.06), (B.hw * 2, 0, 0), (0, 0, 0.012), _solid(dark, 170), depth=0.2))
    out[-1].n = np.array([0, 1.0, 0])
    return out


def _solid(rgb, a):
    img = np.zeros((2, 2, 4))
    img[..., :3] = rgb
    img[..., 3] = a
    return img


def interior(s, B, model):
    """Seats, the dashboard, the steering wheel (its own part, it turns), the rear shelf - seen through the glass."""
    g = Geo(40)
    hw = B.hw
    floor = s.sill + 0.08
    dash_z = s.cowl_z - 0.32
    g.extend(geom.rbox((-hw + 0.08, floor, dash_z - 0.05), (hw - 0.08, s.belt + 0.06, dash_z + 0.35), 'dash', r=0.06, segs=2))
    g.extend(geom.cbox((0.38, s.belt + 0.07, dash_z - 0.02), (0.3, 0.05, 0.06), 'dash', r=0.02))
    g.extend(geom.panel((0.38, s.belt + 0.07, dash_z - 0.055), 0.26, 0.09, (-1, 0, 0), (0, 1, 0), 'gauges'))
    # Seats: cushion and back for each of the front two, a bench behind.
    rows = [(s.b_pillar + 0.02, (-0.38, 0.38))]
    if s.doors == 4 or s.rear != 'van':
        rows.append((s.roof_end_z + 0.05 if s.rear == 'saloon' else s.roof_end_z + 0.2, None))
    seat_mat = 'seat_beige' if s.boxy > 0.5 else 'seat'
    for z, xs in rows:
        if xs:
            for x in xs:
                g.extend(geom.rbox((x - 0.24, floor + 0.2, z - 0.05), (x + 0.24, floor + 0.32, z + 0.45), seat_mat, r=0.05, segs=2))
                back = geom.rbox((x - 0.24, floor + 0.3, z - 0.14), (x + 0.24, floor + 0.95, z), seat_mat, r=0.06, segs=2)
                g.extend(_tilt(back, (x, floor + 0.3, z), -12))
                g.extend(geom.rbox((x - 0.12, floor + 0.98, z - 0.16), (x + 0.12, floor + 1.15, z - 0.06), seat_mat, r=0.04, segs=2))
        else:
            g.extend(geom.rbox((-hw + 0.15, floor + 0.2, z - 0.05), (hw - 0.15, floor + 0.32, z + 0.45), seat_mat, r=0.05, segs=2))
            back = geom.rbox((-hw + 0.15, floor + 0.3, z - 0.14), (hw - 0.15, floor + 0.88, z), seat_mat, r=0.06, segs=2)
            g.extend(_tilt(back, (0, floor + 0.3, z), -10))
    # The floor carpet.
    g.extend(geom.box((-hw + 0.1, floor, s.roof_end_z - 0.3), (hw - 0.1, floor + 0.02, dash_z), 'carpet', faces=('top',)))
    return g


def steering(s, B):
    dash_z = s.cowl_z - 0.32
    c = np.array([0.38, s.belt + 0.08, dash_z - 0.22])
    g = Geo(50)
    ring = []
    for k in range(16):
        a = 2 * math.pi * k / 16
        ring.append(c + np.array([math.cos(a) * 0.19, math.sin(a) * 0.19 * 0.94, math.sin(a) * 0.19 * 0.34]))
    g.extend(geom.tube(ring, 0.017, 'trim', n=6, closed=True))
    for a in (0, math.pi, -math.pi / 2):
        e = c + np.array([math.cos(a) * 0.18, math.sin(a) * 0.18 * 0.94, math.sin(a) * 0.18 * 0.34])
        g.extend(geom.tube([c, e], 0.012, 'trim', n=4))
    g.extend(geom.cyl(c + [0, 0.006, 0.018], c - [0, 0.006, 0.018], 0.05, 'trim', n=10))
    g.extend(geom.tube([c + [0, -0.02, 0.06], c + [0, -0.1, 0.3]], 0.025, 'trim', n=6))
    return g, c


def _tilt(g, c, deg):
    from core import rot_x
    c = np.asarray(c, dtype=np.float64)
    g.transform(t=-c)
    g.transform(R=rot_x(deg))
    g.transform(t=c)
    return g


def build(s, near=24.0):
    scheme = materials.Scheme(base=s.paint, dirt=s.dirt, wear=0.1, seed=abs(hash(s.id)) % 1000, gloss=0.8)
    m = Model(s.id, scheme, density=48, tex_width=512)
    m.tinted = s.tinted
    B = Body(s)
    body = m.part('body')
    body.add(body_geo(s, B))
    body.add(arch_liners(s, B))
    if s.plastic_cladding:
        body.add(arch_trim(s, B, 'trim'))
    elif s.boxy > 0.5:
        body.add(arch_trim(s, B, 'chrome'))
    body.add(lamps(s, B, body))
    body.add(details(s, B))
    m.decals += shut_lines(s, B)
    inside = body.part('inside')
    inside.add(interior(s, B, m))
    inside.near = near
    sw, c = steering(s, B)
    wheel_part = inside.part('steering_wheel', tuple(c))
    wheel_part.add(sw)
    wheel_part.near = near
    # The road wheels: each its own part turning about its axle (the front two steer).
    ra = s.front_axle - s.wheelbase
    for name, z, steer in (('wheel_fl', s.front_axle, True), ('wheel_fr', s.front_axle, True), ('wheel_rl', ra, False), ('wheel_rr', ra, False)):
        side = -1 if name.endswith('l') else 1
        c = (side * s.track, s.wheel_r, z)
        p = body.part(name, c)
        p.add(geom.wheel(c, side, s.wheel_r, s.tyre_w, 0.62 if s.wheel_style != 'steel' else 0.58, s.wheel_style, n=28))
        p.spin = s.wheel_r
        p.steer = steer
    return m
