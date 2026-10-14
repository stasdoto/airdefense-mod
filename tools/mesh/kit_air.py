"""
2.0: the kit the aircraft, ships, boats and trains (m_air.py) are built from.

 - Body: a smooth body lofted through stations (z, y centre, half width, half heights above/below, the squareness of the
   top and bottom halves), interpolated smoothly between them (monotone cubic); its skin can have holes (where a canopy
   sits) and glazed bands; Canopy: glass lofted over such a hole, blending into the body at both ends.
 - lifting surfaces (wings, fins, tailplanes, rotor blades): an airfoil swept between span stations, tapered, twisted,
   drooped.
 - ordnance (rocket pods, bombs, missiles, drop tanks), landing gear, cockpits (a tub seen through the glass, seats and
   instrument panels drawn close up only).
 - the materials: the airframe's paint (camouflage, a lighter belly, panel lines and rivet rows, exhaust soot and oil
   streaks, the boot-topping and antifouling of a hull below the waterline), cockpits, instruments, metal, nozzles.
"""
import math

import numpy as np
from scipy.interpolate import PchipInterpolator

import geom
import materials
from core import Geo, normalize, face_normal
from materials import hexc, vnoise, fbm


# ---------------------------------------------------------------------------------------------------------------
# Small helpers


def flip(g):
    """Turns every polygon of a Geo the other way (an inside-out box: a cockpit tub seen from within)."""
    for p in g.polys:
        p.v = p.v[::-1].copy()
        if p.uv is not None:
            p.uv = p.uv[::-1].copy()
        if p.n is not None:
            p.n = -p.n[::-1].copy()
    return g


def orient(g, outward):
    """Makes the polygons of a Geo face out: outward(centroid) -> a rough outward direction; each polygon is turned to
    agree with it (used on lofts whose ring direction is not known up front)."""
    for p in g.polys:
        c = p.v.mean(axis=0)
        if np.dot(face_normal(p.v), outward(c)) < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
            if p.n is not None:
                p.n = p.n[::-1].copy()
    return g


def orient_majority(g, outward):
    """Like orient, but the whole Geo is turned or not by a vote (for thin shapes where single polygons are ambiguous)."""
    s = 0.0
    for p in g.polys:
        s += np.sign(np.dot(face_normal(p.v), outward(p.v.mean(axis=0))))
    if s < 0:
        flip_winding(g)
    return g


def flip_winding(g):
    for p in g.polys:
        p.v = p.v[::-1].copy()
        if p.uv is not None:
            p.uv = p.uv[::-1].copy()
        if p.n is not None:
            p.n = p.n[::-1].copy()
    return g


def tilt(g, c, R):
    c = np.asarray(c, dtype=np.float64)
    g.transform(t=-c)
    g.transform(R=R)
    g.transform(t=c)
    return g


def rot_axis(axis, deg):
    a = normalize(np.asarray(axis, dtype=np.float64))
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    x, y, z = a
    return np.array([[c + x * x * (1 - c), x * y * (1 - c) - z * s, x * z * (1 - c) + y * s],
                     [y * x * (1 - c) + z * s, c + y * y * (1 - c), y * z * (1 - c) - x * s],
                     [z * x * (1 - c) - y * s, z * y * (1 - c) + x * s, c + z * z * (1 - c)]])


def both(g):
    """The Geo and its mirror image (left and right)."""
    out = Geo(g.smooth)
    out.extend(g)
    out.extend(g.mirrored_x())
    return out


# ---------------------------------------------------------------------------------------------------------------
# Bodies


FIELDS = ('yc', 'hw', 'ht', 'hb', 'pt', 'pb', 'x0', 'kt')


class Body:
    """A smooth body through stations: each a dict with z and any of yc (centre height), hw (half width), ht / hb (half
    height above / below the centre), pt / pb (squareness of the upper / lower half: 2 = round, 4 = boxy), x0 (centre
    sideways), kt (the top's width relative to the bottom's: <1 tumblehome). Missing values carry over."""

    def __init__(self, stations, n=24):
        st = sorted(stations, key=lambda s: s['z'])
        last = dict(yc=0.0, hw=1.0, ht=1.0, hb=None, pt=2.0, pb=None, x0=0.0, kt=1.0)
        rows = []
        for s in st:
            d = dict(last)
            d.update(s)
            if 'hb' not in s and s.get('ht') is not None and last.get('hb') is None:
                d['hb'] = d['ht']
            if d['hb'] is None:
                d['hb'] = d['ht']
            if d['pb'] is None:
                d['pb'] = d['pt']
            rows.append(d)
            last = d
        self.rows = rows
        self.z = np.array([r['z'] for r in rows])
        self.f = {}
        for k in FIELDS:
            vals = np.array([r[k] for r in rows], dtype=np.float64)
            self.f[k] = PchipInterpolator(self.z, vals) if len(rows) > 1 else (lambda zz, v=vals[0]: v)
        self.n = n
        self.z0 = float(self.z[0])
        self.z1 = float(self.z[-1])
        # Angles of the ring's points: from the bottom, round the left side, over the top, down the right.
        self.th = np.array([-math.pi / 2 - 2 * math.pi * k / n for k in range(n)])

    def at(self, z):
        return {k: float(self.f[k](z)) for k in FIELDS}

    def inset(self, d, n=None):
        """The same body {d} metres smaller all round (a lining inside it)."""
        rows = []
        for r in self.rows:
            q = dict(r)
            for k in ('hw', 'ht', 'hb'):
                q[k] = max(0.01, r[k] - d)
            rows.append(q)
        return Body(rows, n or self.n)

    def theta_at(self, z, y, side):
        """The ring angle on the left (side -1) or right (+1) where the skin is at height y."""
        d = self.at(z)
        ts = np.linspace(-math.pi / 2, math.pi / 2, 181) if side > 0 else np.linspace(-math.pi / 2, -3 * math.pi / 2, 181)
        ys = np.array([self.point(z, t, d)[1] for t in ts])
        i = int(np.argmin(np.abs(ys - y)))
        return float(ts[i])

    def surf(self, z, th, off=0.0):
        d = self.at(z)
        p = self.point(z, th, d)
        c = np.array([d['x0'], d['yc'], z])
        return p + normalize(p - c) * off

    def point(self, z, th, d=None):
        d = d or self.at(z)
        c, s = math.cos(th), math.sin(th)
        p = d['pt'] if s > 0 else d['pb']
        h = d['ht'] if s > 0 else d['hb']
        e = 2.0 / max(p, 0.5)
        x = math.copysign(abs(c) ** e, c) * max(d['hw'], 0.0)
        if s > 0:
            x *= 1 + (d['kt'] - 1) * abs(s) ** 0.8
        y = math.copysign(abs(s) ** e, s) * max(h, 0.0)
        return np.array([d['x0'] + x, d['yc'] + y, z])

    def ring(self, z):
        d = self.at(z)
        return [self.point(z, t, d) for t in self.th]

    def top_y(self, z):
        d = self.at(z)
        return d['yc'] + d['ht']

    def zs(self, step, extra=(), end_pinch=True):
        """Stations every {step} metres (closer at the ends, where the body turns), plus the extra ones."""
        L = self.z1 - self.z0
        k = max(2, int(math.ceil(L / step)))
        t = np.linspace(0, 1, k + 1)
        if end_pinch:
            t = 0.5 - 0.5 * np.cos(np.pi * t) * 0.85 / 0.85 if False else t
        zz = list(self.z0 + t * L)
        # Closer near the two ends (the nose and the tail turn quickly).
        for e in (self.z0, self.z1):
            for f in (0.12, 0.3):
                zz.append(e + (step * f if e == self.z0 else -step * f))
        zz += [z for z in extra if self.z0 < z < self.z1]
        zz = sorted(set(round(z, 4) for z in zz))
        out = [zz[0]]
        for z in zz[1:]:
            if z - out[-1] > 0.01:
                out.append(z)
        return out

    def skin(self, zs, mat, mats=None, smooth=50.0, cap0=False, cap1=False):
        """The skin through stations zs; mats(z_mid, theta_mid, k, i) -> material, None for a hole."""
        rings = [self.ring(z) for z in zs]
        n = self.n
        f = None
        if mats is not None:
            def f(i, k):
                zm = (zs[i] + zs[i + 1]) / 2
                tm = (self.th[k] + (self.th[(k + 1) % n] if k + 1 < n else self.th[0] - 2 * math.pi)) / 2
                return mats(zm, tm, k, i)
        g = geom.loft(rings, mat, closed=True, smooth=smooth, mats=f, cap0=cap0, cap1=cap1)

        def out(c):
            d = self.at(min(max(c[2], self.z0), self.z1))
            v = c - np.array([d['x0'], d['yc'], c[2]])
            if np.linalg.norm(v) < 1e-6:
                v = np.array([0, 0, 1.0 if c[2] > (self.z0 + self.z1) / 2 else -1.0])
            return v
        orient(g, out)
        return g

    def section_line(self, z, th0=None, th1=None, off=0.004, k=40):
        """A ring (or part of it) a hair outside the skin, as a polyline (panel lines)."""
        d = self.at(z)
        if th0 is None:
            ts = np.linspace(-math.pi / 2, -math.pi / 2 - 2 * math.pi, k + 1)
        else:
            ts = np.linspace(th0, th1, k)
        c = np.array([d['x0'], d['yc'], z])
        pts = []
        for t in ts:
            p = self.point(z, t, d)
            v = p - c
            pts.append(p + normalize(v) * off)
        return np.array(pts)

    def long_line(self, th, z0, z1, k=30, off=0.004):
        pts = []
        for z in np.linspace(z0, z1, k):
            d = self.at(z)
            p = self.point(z, th, d)
            c = np.array([d['x0'], d['yc'], z])
            pts.append(p + normalize(p - c) * off)
        return np.array(pts)


def canopy(body, zs, k0, k1, lift, mat='a_glass', frames=(), frame_mat='skin', smooth=60.0, sharp=False):
    """Glass over the hole the body leaves between ring points k0..k1 (the top) at stations zs: each point raised above
    the sill line by lift(z, s) (s 0..1 across), so the canopy meets the body exactly at its edges. frames: z ranges
    [(za, zb)] drawn as frame (opaque paint) instead of glass."""
    rings = []
    for z in zs:
        r = body.ring(z)
        a = r[k0]
        b = r[k1 % body.n]
        pts = []
        m = k1 - k0
        for j in range(m + 1):
            p = r[(k0 + j) % body.n].copy()
            s = j / m
            ysill = a[1] + (b[1] - a[1]) * s
            f = lift(z, s)
            p[1] = ysill + (p[1] - ysill) * f[0] + f[1]
            p[0] = p[0] * f[2] if len(f) > 2 else p[0]
            pts.append(p)
        rings.append(pts)

    def mats(i, k):
        zm = (zs[i] + zs[i + 1]) / 2
        for za, zb in frames:
            if za <= zm <= zb:
                return frame_mat
        return mat
    g = geom.loft(rings, mat, closed=False, smooth=smooth, mats=mats)
    d0 = body.at((zs[0] + zs[-1]) / 2)

    def out(c):
        d = body.at(min(max(c[2], body.z0), body.z1))
        return c - np.array([d['x0'], d['yc'] - d['hb'] * 0.5, c[2]])
    orient(g, out)
    return g


# ---------------------------------------------------------------------------------------------------------------
# Lifting surfaces


def airfoil(n=12, t=0.12, camber=0.0):
    """A closed airfoil (chord 0..1 from the leading edge, thickness up): from the trailing edge along the top to the
    leading edge and back along the bottom. Returns [(c, y)] of 2n points."""
    out = []
    for i in range(n + 1):
        b = math.pi * i / n
        c = 0.5 * (1 + math.cos(b))  # 1 -> 0 (TE to LE), close spacing at both ends
        yt = 5 * t * (0.2969 * math.sqrt(c) - 0.126 * c - 0.3516 * c * c + 0.2843 * c ** 3 - 0.1036 * c ** 4)
        yc = camber * 4 * c * (1 - c)
        out.append((c, yc + yt))
    for i in range(1, n):
        b = math.pi * i / n
        c = 0.5 * (1 - math.cos(b))
        yt = 5 * t * (0.2969 * math.sqrt(c) - 0.126 * c - 0.3516 * c * c + 0.2843 * c ** 3 - 0.1036 * c ** 4)
        yc = camber * 4 * c * (1 - c)
        out.append((c, yc - yt))
    return out


def surface(stations, mat, n=10, t=0.12, camber=0.0, cap_tip=True, cap_root=False, mats=None, smooth=45.0):
    """A lifting surface through span stations, each (le, chord_dir, up, chord, thickness ratio): le the leading edge
    point, chord_dir the way to the trailing edge, up the thickness direction. mats(i, j) -> material of the band
    between station i and i+1 at profile point j (control surfaces, de-icing boots)."""
    rings = []
    for st in stations:
        le, cd, up, ch, tr = st[:5]
        prof = airfoil(n, tr if tr is not None else t, camber)
        le = np.asarray(le, dtype=np.float64)
        cd = normalize(np.asarray(cd, dtype=np.float64))
        up = normalize(np.asarray(up, dtype=np.float64))
        rings.append([le + cd * c * ch + up * y * ch for c, y in prof])
    g = geom.loft(rings, mat, closed=True, smooth=smooth, mats=mats)
    if cap_tip:
        g.extend(geom.polygon_cap(rings[-1], mat))
    if cap_root:
        g.extend(geom.polygon_cap(rings[0][::-1], mat))
    # Out: away from the chord line of the nearest station.
    les = [np.asarray(s[0], dtype=np.float64) for s in stations]
    mids = [np.asarray(s[0], dtype=np.float64) + normalize(np.asarray(s[1], dtype=np.float64)) * s[3] * 0.45 for s in stations]
    ups = [normalize(np.asarray(s[2], dtype=np.float64)) for s in stations]

    def out(c):
        i = int(np.argmin([np.linalg.norm(c - m) for m in mids]))
        v = c - mids[i]
        return v
    orient(g, out)
    return g


def wing(root, tip, chord_root, chord_tip, t_root=0.12, t_tip=0.1, n=11, steps=4, twist=0.0, mat='skin', up=(0, 1, 0),
         chord_dir=(0, 0, -1), camber=0.015, cap_tip=True, mats=None, round_tip=False):
    """A straight-tapered wing from the root's leading edge to the tip's (points), chord along chord_dir."""
    root = np.asarray(root, dtype=np.float64)
    tip = np.asarray(tip, dtype=np.float64)
    st = []
    for i in range(steps + 1):
        s = i / steps
        le = root + (tip - root) * s
        ch = chord_root + (chord_tip - chord_root) * s
        tr = t_root + (t_tip - t_root) * s
        cd = np.asarray(chord_dir, dtype=np.float64)
        u = np.asarray(up, dtype=np.float64)
        if twist:
            span = normalize(tip - root)
            R = rot_axis(span, twist * s)
            cd = R @ cd
            u = R @ u
        st.append((le, cd, u, ch, tr))
    if round_tip:
        le, cd, u, ch, tr = st[-1]
        span = normalize(tip - root)
        st.append((le + span * ch * 0.06 + normalize(cd) * ch * 0.08, cd, u, ch * 0.84, tr * 0.6))
    return surface(st, mat, n=n, camber=camber, cap_tip=cap_tip, mats=mats)


def fin_surface(base_le, top_le, chord_base, chord_top, t=0.1, x=0.0, mat='skin', steps=3, n=10, side_tilt=0.0):
    """A vertical (or canted) fin: base_le, top_le: (y, z) of the leading edge at the base and top."""
    up = np.array([1.0, 0, 0])
    b = np.array([x, base_le[0], base_le[1]])
    tp = np.array([x + side_tilt, top_le[0], top_le[1]])
    return wing(b, tp, chord_base, chord_top, t, t * 0.85, n=n, steps=steps, mat=mat, up=up, camber=0.0)


def blade(root_r, tip_r, chord, y, droop=0.25, n=8, steps=6, t=0.11, twist=-8.0, mat='a_blade', root_mat='a_hub', tip_sweep=0.0,
          axis='z', up_sign=1.0):
    """A rotor blade along +z (from root_r to tip_r from the hub axis at height y), the chord across x, drooping by
    {droop} at the tip, twisted (washout) toward the tip; a narrow root cuff."""
    st = []
    for i in range(steps + 1):
        s = i / steps
        r = root_r + (tip_r - root_r) * s
        yy = y - droop * s * s
        tw = twist * s
        R = rot_axis((0, 0, 1), tw)
        cd = R @ np.array([1.0, 0, 0])
        u = R @ np.array([0, 1.0, 0])
        le = np.array([-chord * 0.25, yy, r]) - cd * 0 + np.array([0, 0, 0])
        le = np.array([0, yy, r]) - cd * chord * 0.3
        st.append((le, cd, u, chord, t))
    # A swept, narrower tip cap.
    le, cd, u, ch, tr = st[-1]
    st.append((le + np.array([chord * 0.12, -0.01, 0.22]), cd, u, chord * 0.7, t * 0.8))
    g = surface(st, mat, n=n, camber=0.02, smooth=50)
    # Root: the cuff (a round spar to the hub).
    g.extend(geom.cyl((0, y + 0.0, root_r * 0.25), (0, y, root_r + 0.25), 0.06, root_mat, n=8))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Ordnance and pods


def rocket_pod(c, length, dia, mat='a_pod', n=14, face='a_podface'):
    """A rocket pod (B-8, Hydra): a tube with a rounded nose fairing ring, the tubes' mouths in front, along +z."""
    c = np.asarray(c, dtype=np.float64)
    r = dia / 2
    a = c - np.array([0, 0, length / 2])
    b = c + np.array([0, 0, length / 2])
    prof = [(0, 0, mat), (0, r * 0.75, mat), (length * 0.05, r * 0.92, mat), (length * 0.12, r, mat), (length * 0.93, r, mat),
            (length * 0.97, r * 0.96, face), (length, r * 0.9, face), (length, 0, face)]
    return geom.lathe(a, b, prof, mat, n, 40)


def bomb(c, length, dia, mat='a_olive', n=12, fins=True):
    c = np.asarray(c, dtype=np.float64)
    r = dia / 2
    a = c - np.array([0, 0, length / 2])
    b = c + np.array([0, 0, length / 2])
    prof = [(0, 0), (0, r * 0.35), (length * 0.08, r * 0.45), (length * 0.3, r * 0.92), (length * 0.42, r), (length * 0.72, r),
            (length * 0.88, r * 0.82), (length * 0.97, r * 0.45), (length, 0)]
    g = geom.lathe(a, b, prof, mat, n, 60)
    if fins:
        for k in range(4):
            ang = math.pi / 4 + k * math.pi / 2
            d = np.array([math.cos(ang), math.sin(ang), 0])
            e = np.array([-d[1], d[0], 0])
            pts = [(0, r * 0.4), (length * 0.24, r * 0.6), (length * 0.18, r * 1.35), (0.0, r * 1.35)]
            g.extend(geom.extrude([(p[0], p[1]) for p in pts], np.array([0, 0, 1.0]), d, a - e * 0.006, 0.012, mat))
    return g


def missile(c, length, dia, mat='a_white', nose='a_seeker', n=10, fins=(0.9, 1.2), canards=True, wings=0.0):
    """An air-to-air / anti-tank missile along +z: body, ogive or glass-domed nose, tail fins, canards."""
    c = np.asarray(c, dtype=np.float64)
    r = dia / 2
    a = c - np.array([0, 0, length / 2])
    b = c + np.array([0, 0, length / 2])
    prof = [(0, 0, 'a_nozzle'), (0, r * 0.8, mat), (length * 0.02, r, mat), (length * 0.86, r, nose), (length * 0.95, r * 0.7, nose),
            (length, 0, nose)]
    g = geom.lathe(a, b, prof, mat, n, 50)
    fw, fl = fins
    for k in range(4):
        ang = math.pi / 4 + k * math.pi / 2
        d = np.array([math.cos(ang), math.sin(ang), 0])
        e = np.array([-d[1], d[0], 0])
        pts = [(0.0, r), (dia * fl, r), (dia * fl * 0.55, r + dia * fw), (0.0, r + dia * fw)]
        g.extend(geom.extrude(pts, np.array([0, 0, 1.0]), d, a - e * 0.005, 0.01, mat))
        if canards:
            pts = [(length * 0.8, r), (length * 0.86, r), (length * 0.85, r + dia * 0.45), (length * 0.81, r + dia * 0.45)]
            g.extend(geom.extrude(pts, np.array([0, 0, 1.0]), d, a - e * 0.005, 0.01, mat))
        if wings:
            pts = [(length * 0.35, r), (length * 0.62, r), (length * 0.6, r + wings), (length * 0.45, r + wings)]
            g.extend(geom.extrude(pts, np.array([0, 0, 1.0]), d, a - e * 0.005, 0.01, mat))
    return g


def drop_tank(c, length, dia, mat='skin', n=14):
    c = np.asarray(c, dtype=np.float64)
    r = dia / 2
    a = c - np.array([0, 0, length / 2])
    b = c + np.array([0, 0, length / 2])
    prof = [(0, 0), (length * 0.05, r * 0.3), (length * 0.25, r * 0.92), (length * 0.4, r), (length * 0.62, r), (length * 0.85, r * 0.75),
            (length * 0.97, r * 0.3), (length, 0)]
    g = geom.lathe(a, b, prof, mat, n, 60)
    for k in (0, 1, 3):
        ang = -math.pi / 2 + k * math.pi / 2 + math.pi / 4
        d = np.array([math.cos(ang), math.sin(ang), 0])
        e = np.array([-d[1], d[0], 0])
        pts = [(length * 0.04, r * 0.4), (length * 0.2, r * 0.8), (length * 0.12, r * 1.4), (length * 0.0, r * 1.4)]
        g.extend(geom.extrude(pts, np.array([0, 0, 1.0]), d, a - e * 0.006, 0.012, mat))
    return g


def pylon(x, y_top, y_bot, z0, z1, mat='skin', w=0.08):
    """A streamlined pylon under a wing: a thin airfoil-ish slab from the wing down to the store."""
    L = z1 - z0
    pts = [(z0, y_top), (z1, y_top), (z1 - L * 0.08, y_bot), (z0 + L * 0.2, y_bot)]
    g = geom.extrude([(p[0], p[1]) for p in pts], np.array([0, 0, 1.0]), np.array([0, 1.0, 0]), np.array([x - w / 2, 0, 0]),
                     w, mat, bevel=0.0)
    # extrude's plane: origin + u*z + v*y, pushed along u x v = z x y = -x: so the slab sits at x - w/2 .. x - 3w/2: move.
    g.transform(t=(w, 0, 0))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Landing gear and cockpits


def gear_wheel(c, r, width, n=16, hub='rim_steel', tread='tyre'):
    """An aircraft wheel at c (axle along x): a fat tyre and a hub."""
    c = np.asarray(c, dtype=np.float64)
    g = Geo(45)
    rr = r * 0.55
    g.extend(geom.tyre(c - np.array([width / 2, 0, 0]), (1, 0, 0), r, width, rr, tread, n))
    for s in (-1, 1):
        f = c + np.array([s * width * 0.38, 0, 0])
        g.extend(geom.lathe(f - np.array([s * 0.02, 0, 0]), f + np.array([s * 0.03, 0, 0]),
                            [(0, rr, hub), (0.02, rr * 0.9, hub), (0.04, rr * 0.4, hub), (0.05, rr * 0.25, 'bolt'), (0.05, 0, 'bolt')],
                            hub, 10, 40))
    return g


def strut(top, bottom, r=0.06, mat='a_gear', oleo='chrome', n=8):
    """A landing gear leg: the outer cylinder, the shiny oleo below it, a torque link."""
    top = np.asarray(top, dtype=np.float64)
    bottom = np.asarray(bottom, dtype=np.float64)
    mid = top + (bottom - top) * 0.55
    g = Geo(50)
    g.extend(geom.cyl(top, mid, r, mat, n=n))
    g.extend(geom.cyl(mid, bottom, r * 0.7, oleo, n=n))
    d = bottom - top
    side = normalize(np.cross(d, (1, 0, 0))) * r * 1.6
    g.extend(geom.tube([mid - d * 0.12 + side, mid + d * 0.15 + side * 1.8, bottom - d * 0.05 + side], 0.015, mat, n=4))
    return g


def seat(c, w=0.5, mat='a_seat', frame='a_seatframe', ejection=True):
    """An ejection seat facing +z at c (the seat pan's centre)."""
    c = np.asarray(c, dtype=np.float64)
    g = Geo(40)
    x, y, z = c
    g.extend(geom.rbox((x - w / 2, y - 0.06, z - 0.22), (x + w / 2, y + 0.06, z + 0.24), mat, r=0.04, segs=1))
    back = geom.rbox((x - w / 2, y, z - 0.32), (x + w / 2, y + 0.85, z - 0.18), mat, r=0.04, segs=1)
    g.extend(tilt(back, (x, y, z - 0.25), rot_axis((1, 0, 0), -14)))
    g.extend(geom.box((x - w / 2 - 0.05, y - 0.3, z - 0.42), (x - w / 2, y + 1.0, z - 0.2), frame))
    g.extend(geom.box((x + w / 2, y - 0.3, z - 0.42), (x + w / 2 + 0.05, y + 1.0, z - 0.2), frame))
    if ejection:
        g.extend(geom.rbox((x - w * 0.42, y + 0.85, z - 0.48), (x + w * 0.42, y + 1.12, z - 0.26), frame, r=0.03))
        g.extend(geom.box((x - 0.06, y + 0.9, z - 0.2), (x + 0.06, y + 1.06, z - 0.16), 'yellow', faces=('front', 'top')))
    g.extend(geom.box((x - w / 2, y - 0.4, z - 0.25), (x + w / 2, y - 0.06, z + 0.2), frame, faces=('front', 'left', 'right')))
    return g


def instrument_panel(z, y0, y1, hw, mat='a_panel', depth=0.12, coaming='a_coaming', glare=0.12):
    """The instrument panel facing aft at z (its face), from y0 to y1, with the glare shield over it."""
    g = Geo(40)
    g.extend(geom.box((-hw, y0, z), (hw, y1, z + depth), 'a_coaming', faces=('top', 'left', 'right')))
    g.extend(geom.panel((0, (y0 + y1) / 2, z), hw * 2, y1 - y0, (-1, 0, 0), (0, 1, 0), mat))
    # The glare shield: a lip over the top.
    g.extend(geom.rbox((-hw * 1.02, y1, z - glare), (hw * 1.02, y1 + 0.05, z + depth), coaming, r=0.02))
    return g


def stick(c, mat='a_seatframe'):
    c = np.asarray(c, dtype=np.float64)
    g = Geo(40)
    g.extend(geom.tube([c, c + (0, 0.42, -0.03)], 0.016, mat, n=5))
    g.extend(geom.cbox(c + (0, 0.47, -0.03), (0.04, 0.1, 0.05), 'black', r=0.015))
    return g


def tub(lo, hi, mat='a_cockpit'):
    """A cockpit's inside (walls and floor seen from within; open on top)."""
    g = geom.box(lo, hi, mat, faces=('left', 'right', 'bottom', 'front', 'back'))
    return flip(g)


# ---------------------------------------------------------------------------------------------------------------
# Markings


def number_decal(c, side_n, w, h, text, fg=(236, 236, 230), outline=None, px=260, bold=True, along=(0, 0, 1)):
    """Lettering on a side (normal side_n), reading from the front... as seen by someone standing there."""
    n = np.asarray(side_n, dtype=np.float64)
    a = np.asarray(along, dtype=np.float64)
    # The game shows the model mirrored from a right-handed frame: on the right side (+x) the text runs along +z.
    if n[0] > 0.5:
        u = a * w
    elif n[0] < -0.5:
        u = -a * w
    elif n[1] > 0.5:
        u = np.array([1.0, 0, 0]) * w
    elif n[1] < -0.5:
        u = np.array([-1.0, 0, 0]) * w
    elif n[2] > 0.5:
        u = np.array([-1.0, 0, 0]) * w
    else:
        u = np.array([1.0, 0, 0]) * w
    v = np.array([0, 1.0, 0]) * h if abs(n[1]) < 0.5 else np.array([0, 0, 1.0]) * h * (1 if n[1] > 0 else 1)
    wp = max(8, int(w * px))
    hp = max(6, int(h * px))
    m = materials.text_mask(text, wp, hp, bold, 0.04)
    img = np.zeros((hp, wp, 4))
    if outline is not None:
        from scipy.ndimage import maximum_filter
        mo = maximum_filter(m, size=max(3, int(px * 0.025) * 2 + 1))
        img[..., :3] = np.asarray(outline, dtype=np.float64)
        img[..., 3] = mo * 255
    fgc = np.asarray(fg, dtype=np.float64)
    img[..., :3] = img[..., :3] * (1 - m[..., None]) + fgc * m[..., None]
    img[..., 3] = np.maximum(img[..., 3], m * 255)
    d = materials.Decal(c, u, v, img, depth=0.3)
    d.n = n / np.linalg.norm(n)
    return d


def image_decal(c, side_n, u, v, img, depth=0.3):
    d = materials.Decal(c, u, v, img, depth=depth)
    d.n = normalize(np.asarray(side_n, dtype=np.float64))
    return d


def roundel(colours, px=64):
    """Concentric rings (outer first): an image (h, w, 4)."""
    img = np.zeros((px, px, 4))
    yy, xx = np.mgrid[0:px, 0:px]
    r = np.sqrt((xx - px / 2 + 0.5) ** 2 + (yy - px / 2 + 0.5) ** 2) / (px / 2)
    k = len(colours)
    for i, col in enumerate(colours):
        m = r <= 1 - i / k
        img[m, :3] = col
        img[m, 3] = 255
    return img


# ---------------------------------------------------------------------------------------------------------------
# Materials


class Air:
    """Extra paint data a model carries (model.air): a lighter belly, panel lines, soot, stains, waterline."""

    def __init__(self):
        self.under = None  # colour of the belly
        self.under_y = 99.0  # only below this height
        self.lines = []  # (polyline (k, 3), half width, rivets)
        self.stains = []  # (p0, p1, r0, r1, colour, strength, streaky)
        self.waterline = None  # y: antifouling below, boot topping just above
        self.boot = 0.35
        self.antifoul = hexc(0x6E2E26)
        self.dirt_low = False
        self._seg = None

    def segments(self):
        if self._seg is None:
            A, B, W, R = [], [], [], []
            for pl, w, riv in self.lines:
                pl = np.asarray(pl, dtype=np.float64)
                for i in range(len(pl) - 1):
                    A.append(pl[i])
                    B.append(pl[i + 1])
                    W.append(w)
                    R.append(1.0 if riv else 0.0)
            self._seg = (np.array(A).reshape(-1, 3), np.array(B).reshape(-1, 3), np.array(W), np.array(R))
        return self._seg


def _lines(rgb, pos, air, strength=1.0):
    A, B, W, R = air.segments()
    if len(A) == 0:
        return rgb
    out = rgb.copy()
    lo_s = np.minimum(A, B) - (W[:, None] * 4 + 0.02)
    hi_s = np.maximum(A, B) + (W[:, None] * 4 + 0.02)
    AB = B - A
    L2 = np.maximum((AB * AB).sum(axis=1), 1e-12)
    L = np.sqrt(L2)
    for s0 in range(0, len(pos), 6000):
        P = pos[s0:s0 + 6000]
        plo = P.min(axis=0)
        phi = P.max(axis=0)
        sel = np.nonzero(np.all(hi_s >= plo, axis=1) & np.all(lo_s <= phi, axis=1))[0]
        if len(sel) == 0:
            continue
        dark = np.zeros(len(P))
        riv = np.zeros(len(P))
        for j0 in range(0, len(sel), 64):
            js = sel[j0:j0 + 64]
            D = P[:, None, :] - A[None, js, :]
            t = np.clip((D * AB[None, js, :]).sum(axis=2) / L2[None, js], 0, 1)
            Q = A[None, js, :] + t[..., None] * AB[None, js, :]
            d = np.linalg.norm(P[:, None, :] - Q, axis=2)
            w = W[None, js]
            line = np.clip(1.2 - d / w, 0, 1)
            dark = np.maximum(dark, line.max(axis=1))
            # Rivet rows a little to each side, spaced along the line.
            along = t * L[None, js]
            beside = (np.abs(d - w * 3.2) < w * 0.9) & (((along / 0.09) % 1.0) < 0.45) & (R[None, js] > 0)
            riv = np.maximum(riv, beside.max(axis=1).astype(float))
        k = dark * 0.42 * strength + riv * 0.1 * strength
        out[s0:s0 + 6000] *= (1 - k)[:, None]
    return out


def _stains(rgb, pos, nrm, air, seed=3):
    for p0, p1, r0, r1, col, strength, streaky in air.stains:
        p0 = np.asarray(p0, dtype=np.float64)
        p1 = np.asarray(p1, dtype=np.float64)
        ab = p1 - p0
        L2 = float(ab @ ab) or 1e-9
        t = np.clip(((pos - p0) @ ab) / L2, 0, 1)
        q = p0 + t[:, None] * ab
        d = np.linalg.norm(pos - q, axis=1)
        r = r0 + (r1 - r0) * t
        k = np.clip(1 - d / r, 0, 1) ** 0.8 * (1 - t) ** 0.7
        if streaky:
            # Streaks along the flow: noise stretched along the stain's direction.
            dirn = ab / math.sqrt(L2)
            side = pos - np.outer(pos @ dirn, dirn)
            k *= 0.45 + 0.9 * np.clip(vnoise(side * 14.0, 1.0, seed) - 0.25, 0, 1)
        else:
            k *= 0.7 + 0.3 * fbm(pos, 0.3, seed, 2)
        k = np.clip(k * strength, 0, 0.92)
        c = np.asarray(col, dtype=np.float64)
        rgb = rgb * (1 - k[:, None]) + c * k[:, None]
    return rgb


def camo_rgb(scheme, pos):
    """Camouflage with soft sprayed edges: big blotches (scheme.blob metres) stretched along the length."""
    base = np.tile(scheme.base, (len(pos), 1))
    if not scheme.camo:
        return base
    q = pos.copy()
    q[:, 2] = pos[:, 2] / scheme.stretch + pos[:, 1] * scheme.slant
    out = base
    for k, col in enumerate(scheme.camo):
        f = fbm(q, scheme.blob, scheme.seed * 31 + k * 7, octaves=3, gain=0.42)
        thr = getattr(scheme, 'thr', 0.55) + 0.03 * k
        m = np.clip((f - thr) / 0.035 + 0.5, 0, 1)
        m = m * m * (3 - 2 * m)
        out = out * (1 - m[:, None]) + col * m[:, None]
    return out


def skin_fn(T, scheme, model, base=None, lines=True, camo=True, gloss=None):
    pos, nrm, ao = T['pos'], T['nrm'], T['ao']
    n = len(pos)
    air = getattr(model, 'air', None) or Air()
    if base is not None:
        rgb = np.tile(hexc(base) if isinstance(base, int) else np.asarray(base, dtype=np.float64), (n, 1))
    else:
        rgb = camo_rgb(scheme, pos) if camo else np.tile(scheme.base, (n, 1))
    for y0, y1, col in scheme.stripes:
        m = (pos[:, 1] >= y0) & (pos[:, 1] < y1)
        rgb[m] = hexc(col)
    if air.under is not None:
        k = np.clip((-nrm[:, 1] - 0.08) / 0.3, 0, 1) * (pos[:, 1] < air.under_y)
        # A soft, slightly wavy demarcation.
        k = np.clip(k + (vnoise(pos, 0.5, 11) - 0.5) * 0.3 * (k > 0) * (k < 1), 0, 1)
        rgb = rgb * (1 - k[:, None]) + air.under * k[:, None]
    if air.waterline is not None:
        y = pos[:, 1]
        wl = air.waterline
        rgb[y < wl + air.boot] = (32, 33, 35)
        rgb[y < wl - 0.05] = air.antifoul
    for dcl in model.decals:
        rgb = dcl.apply(rgb, pos, nrm)
    rgb = materials.sheen(rgb, nrm, scheme.gloss if gloss is None else gloss)
    if lines:
        rgb = _lines(rgb, pos, air)
    # Faded paint on the top faces (sun), a little chalky.
    fade = np.clip(nrm[:, 1], 0, 1) * scheme.wear * 0.25 * fbm(pos, 1.2, 21, 2)
    rgb = rgb * (1 - fade[:, None]) + np.array([200, 200, 196.0]) * fade[:, None]
    rgb = materials.weather(rgb, T, scheme, 1.0, mud=air.dirt_low)
    rgb = _stains(rgb, pos, nrm, air)
    if air.waterline is not None:
        # A tide line of salt and grime just above the water.
        y = pos[:, 1]
        band = np.clip(1 - np.abs(y - air.waterline - air.boot - 0.08) / 0.25, 0, 1) * (0.4 + 0.6 * vnoise(pos * [1, 4, 0.3], 0.4, 7))
        rgb = rgb * (1 - band[:, None] * 0.3) + np.array([150, 150, 140.0]) * band[:, None] * 0.3
    return materials.occlusion(rgb, ao), np.full(n, 255.0)


def _plain(colour, dirt=0.4, ao=1.0, var=0.05, gloss=0.0):
    def fn(T, scheme, model):
        rgb = materials.plain(T, colour, scheme, dirt, ao, var)
        if gloss:
            rgb = materials.sheen(rgb, T['nrm'], gloss)
        return rgb, np.full(len(rgb), 255.0)
    return fn


def _glass(tint=(36, 50, 60), alpha=(110, 160), gold=False):
    def fn(T, scheme, model):
        nrm, pos = T['nrm'], T['pos']
        n = len(pos)
        ny = nrm[:, 1]
        base = np.array(tint, dtype=np.float64)
        sky = np.array((160, 178, 196), dtype=np.float64) if not gold else np.array((176, 160, 110), dtype=np.float64)
        t = np.clip(0.2 + 0.6 * ny, 0, 1)
        rgb = base * (1 - t)[:, None] + sky * t[:, None]
        s = np.sin((pos[:, 0] * 0.9 + pos[:, 2] * 1.1 + pos[:, 1] * 2.3) * 2.0)
        rgb += (np.clip(s - 0.7, 0, 1) * 130)[:, None]
        a = alpha[0] + (alpha[1] - alpha[0]) * np.clip(ny, 0, 1)
        # Dust and smears at the edges (where ao is low) less see-through.
        a = a + (1 - np.clip(T['ao'], 0, 1)) * 40
        return np.clip(rgb, 0, 255), a
    return fn


def _blade(T, scheme, model):
    """Rotor blades: dark grey, a light band near the tip (yellow on Soviet blades, white elsewhere), worn leading edge."""
    pos, nrm, puv = T['pos'], T['nrm'], T['puv']
    n = len(pos)
    tipcol = getattr(model, 'blade_tip', (226, 186, 40))
    rgb = np.tile(np.array((44, 46, 48.0)), (n, 1))
    rgb *= (0.92 + 0.12 * vnoise(pos, 0.4, 5))[:, None]
    r = np.sqrt(pos[:, 0] ** 2 + pos[:, 2] ** 2)
    R = getattr(model, 'blade_r', 8.0)
    hub = getattr(model, 'hub_xz', (0.0, 0.0))
    r = np.sqrt((pos[:, 0] - hub[0]) ** 2 + (pos[:, 2] - hub[1]) ** 2)
    band = (r > R * 0.86) & (r < R * 0.93)
    rgb[band] = tipcol
    # Erosion strip on the leading edge (lighter metal).
    le = puv[:, 0]
    edge = np.abs(le - 0.5) < 0.09
    rgb[edge & ~band] = rgb[edge & ~band] * 0.6 + np.array((120, 122, 124.0)) * 0.4
    rgb = materials.weather(rgb, T, scheme, 0.4, mud=False)
    return materials.occlusion(rgb, T['ao'], 0.8), np.full(n, 255.0)


def _panel(T, scheme, model):
    """Instrument panels: dark grey with round gauges (Soviet) or screens (western) laid out in a grid."""
    puv = T['puv']
    n = len(puv)
    u, v = puv[:, 0], puv[:, 1]
    western = getattr(model, 'western', False)
    rgb = np.tile(np.array((46, 48, 50.0) if western else (54, 82, 84.0)), (n, 1))
    cu = (u * 7) % 1.0
    cv = (v * 3) % 1.0
    r = np.sqrt((cu - 0.5) ** 2 + ((cv - 0.5) * 7 / 3 / 2.2) ** 2)
    if western:
        scr = (np.abs(u - 0.25) < 0.14) & (np.abs(v - 0.5) < 0.3) | (np.abs(u - 0.75) < 0.14) & (np.abs(v - 0.5) < 0.3)
        rgb[scr] = (30, 60, 40)
        lines = scr & ((((v * 40) % 1.0) < 0.2) | (((u * 30) % 1.0) < 0.12))
        rgb[lines] = (90, 200, 110)
        dial = ~scr & (r < 0.3)
    else:
        dial = r < 0.38
    rgb[dial] = (16, 16, 18)
    ring = dial & (r > (0.26 if western else 0.32))
    rgb[ring] = (180, 180, 172)
    needle = dial & (np.abs(cu - 0.5 - (cv - 0.5) * 0.6) < 0.03) & (cv > 0.5)
    rgb[needle] = (235, 235, 225)
    return rgb, np.full(n, 255.0)


def _nozzle(T, scheme, model):
    """A jet pipe: burnt, blued and straw-coloured metal in bands, the petals (stripes round it), soot inside."""
    pos, nrm, puv = T['pos'], T['nrm'], T['puv']
    n = len(pos)
    u = puv[:, 0]
    t = puv[:, 1]
    base = np.array((92, 86, 80.0))
    rgb = np.tile(base, (n, 1))
    heat = 0.5 + 0.5 * np.sin(t * 9.0)
    rgb = rgb * (1 - heat[:, None] * 0.3) + np.array((70, 72, 96.0)) * heat[:, None] * 0.3
    pet = (((u * 18) % 1.0) < 0.12)
    rgb[pet] *= 0.6
    rgb *= (0.85 + 0.25 * vnoise(pos, 0.15, 9))[:, None]
    return materials.occlusion(rgb, T['ao'], 1.0), np.full(n, 255.0)


def _intake(T, scheme, model):
    pos = T['pos']
    n = len(pos)
    rgb = np.tile(np.array((40, 40, 42.0)), (n, 1))
    rgb *= (0.6 + 0.4 * np.clip(T['ao'], 0, 1))[:, None]
    return rgb, np.full(n, 255.0)


def _podface(T, scheme, model):
    """The front of a rocket pod: the mouths of the tubes in rings."""
    puv = T['puv']
    pos = T['pos']
    n = len(pos)
    rgb = np.tile(np.array((70, 72, 70.0)), (n, 1))
    hole = (((puv[:, 0] * 10) % 1.0) < 0.55) & (np.sin(puv[:, 1] * 300) > -0.2)
    rgb[hole] = (14, 14, 14)
    return rgb, np.full(n, 255.0)


def _deck(T, scheme, model):
    """A ship's deck: dark non-skid grey, worn paths, rust weeping at the edges."""
    pos = T['pos']
    n = len(pos)
    col = getattr(model, 'deck_colour', 0x4A4E52)
    rgb = materials.plain(T, col, scheme, 0.3, 1.0, 0.06)
    grit = vnoise(pos, 0.03, 4)
    rgb *= (0.92 + 0.12 * grit)[:, None]
    return rgb, np.full(n, 255.0)


def _wood(T, scheme, model):
    pos = T['pos']
    n = len(pos)
    rgb = np.tile(hexc(0x7A5A3A), (n, 1))
    plank = ((pos[:, 0] * 6) % 1.0) < 0.06
    rgb *= (0.85 + 0.25 * vnoise(pos * [8, 1, 0.5], 0.3, 2))[:, None]
    rgb[plank] *= 0.6
    rgb = materials.weather(rgb, T, scheme, 0.8, mud=False)
    return materials.occlusion(rgb, T['ao']), np.full(n, 255.0)


def _rubber_boat(T, scheme, model):
    """The RHIB's tube: grey-black hypalon with seams and a rubbing strake."""
    pos = T['pos']
    n = len(pos)
    rgb = np.tile(np.array((52, 54, 56.0)), (n, 1))
    seam = ((pos[:, 2] * 0.9) % 1.0) < 0.03
    rgb[seam] *= 0.7
    rgb = materials.sheen(rgb, T['nrm'], 0.6)
    rgb = materials.weather(rgb, T, scheme, 0.5, mud=False)
    return materials.occlusion(rgb, T['ao']), np.full(n, 255.0)


def _windows(T, scheme, model):
    """Rows of windows painted onto a band (trains, airliners, bridges): dark glass with frames, by the primitive uv."""
    pos = T['pos']
    n = len(pos)
    rgb = np.tile(np.array((30, 38, 46.0)), (n, 1))
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 50)[:, None]
    s = np.sin((pos[:, 2] * 0.5 + pos[:, 1] * 3) * 2.0)
    rgb += (np.clip(s - 0.8, 0, 1) * 160)[:, None]
    return rgb, np.full(n, 255.0)


def _vls(T, scheme, model):
    """VLS hatches: square lids with seams and a darker rim."""
    puv = T['puv']
    n = len(puv)
    rgb = materials.plain(T, 0x585E62, scheme, 0.3)
    u, v = puv[:, 0], puv[:, 1]
    k = getattr(model, 'vls_grid', (4, 2))
    cu = (u * k[0]) % 1.0
    cv = (v * k[1]) % 1.0
    seam = (cu < 0.05) | (cu > 0.95) | (cv < 0.05) | (cv > 0.95)
    rgb[seam] *= 0.45
    inner = (np.abs(cu - 0.5) < 0.32) & (np.abs(cv - 0.5) < 0.32) & ((np.abs(cu - 0.5) > 0.29) | (np.abs(cv - 0.5) > 0.29))
    rgb[inner] *= 0.75
    return rgb, np.full(n, 255.0)


def _skin(name, **kw):
    def fn(T, scheme, model):
        return skin_fn(T, scheme, model, **kw)
    return fn


_done = False


def register():
    global _done
    if _done:
        return
    _done = True
    R = materials.register
    R('skin', _skin('skin'))
    R('skin_plain', _skin('skin_plain', camo=False))
    R('skin_nolines', _skin('skin_nolines', lines=False))
    R('a_radome', lambda T, s, m: skin_fn(T, s, m, base=getattr(m, 'radome', 0x5E6266), lines=False))
    R('a_antiglare', lambda T, s, m: skin_fn(T, s, m, base=0x26282A, lines=False, gloss=0.1))
    R('a_frame', lambda T, s, m: skin_fn(T, s, m, lines=False))
    R('a_dark', _plain(0x2C2E30, 0.4))
    R('a_metal', _plain(0x8A8C8E, 0.5, gloss=0.6))
    R('a_gear', _plain(0xB8BCBE, 0.6, var=0.03))
    R('a_wellgrey', _plain(0x9EA2A4, 0.6))
    R('a_hub', _plain(0x3A3C3E, 0.5, gloss=0.4))
    R('a_white', _plain(0xDCDDD8, 0.25, var=0.03, gloss=0.4))
    R('a_olive', _plain(0x4E5434, 0.5))
    R('a_bomb', _plain(0x5C6248, 0.5))
    R('a_pod', _plain(0x4A5040, 0.5))
    R('a_podgrey', _plain(0x6A6E70, 0.5))
    R('a_seeker', _plain(0x3A4048, 0.1, gloss=1.2))
    R('a_cockpit', lambda T, s, m: (materials.plain(T, 0x4E8582 if not getattr(m, 'western', False) else 0x3C3F42, s, 0.15, 0.9), 255.0))
    R('a_coaming', _plain(0x1E2022, 0.1, 0.8))
    R('a_seat', _plain(0x4A4436, 0.1, 0.9))
    R('a_seatframe', _plain(0x2A2C2E, 0.1, 0.9))
    R('a_panel', _panel, density=4.0)
    R('a_nozzle', _nozzle, density=1.5)
    R('a_intake', _intake)
    R('a_pod_face', _podface, density=2.0)
    R('a_podface', _podface, density=2.0)
    R('a_blade', _blade)
    R('a_glass', _glass(), layer='glass')
    R('a_glass_gold', _glass((60, 52, 34), (100, 150), gold=True), layer='glass')
    R('a_glass_ship', _glass((20, 28, 34), (190, 230)), layer='glass')
    R('a_window', _windows)
    R('a_deck', _deck)
    R('a_wood', _wood)
    R('a_tube', _rubber_boat)
    R('a_vls', _vls, density=2.0)
    R('a_nav_red', _plain(0xD02018, 0.0, 0.3, 0.02))
    R('a_nav_green', _plain(0x20C040, 0.0, 0.3, 0.02))
    R('a_nav_white', _plain(0xF0F0EA, 0.0, 0.3, 0.02))
    R('a_black', _plain(0x161718, 0.3))
    R('a_soot', _plain(0x1E1C1A, 0.2))
    R('a_rust', _plain(0x6A4630, 0.8))
    R('a_orange', _plain(0xD86A1E, 0.3))
    R('a_red', _plain(0xA82820, 0.3))
    R('a_yellow', _plain(0xD8B02A, 0.4))
    R('a_bogie', _plain(0x2E2C2A, 1.0))
    R('a_steel', _plain(0x6C7074, 0.7, gloss=0.3))
    R('a_lightgrey', _plain(0xA8ACAE, 0.4))
    R('a_midgrey', _plain(0x7A7E82, 0.4))
    R('a_darkgrey', _plain(0x45484B, 0.4))
    R('a_sensor', _plain(0x202428, 0.1, gloss=1.5))
    R('a_lamp', _plain(0xF4EEDA, 0.0, 0.2, 0.02))
    R('a_mesh', _plain(0x202122, 0.2))
    R('a_canvas', _plain(0x58583E, 0.6))
