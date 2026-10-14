"""
2.0: the tanks and the other armoured vehicles as real shapes (see kit_tanks.py for the shared pieces).

Each model keeps the box model's animated parts (tools/models: turret, gun / elevator, spinners, launch rails, wheels)
with the same names, pivots and parents; the road wheels, sprockets and idlers are their own parts (the client rolls
them) and the track belts are parts of their own (track -1 left, 1 right; spin = the link pitch in metres) whose links
the client scrolls.
"""
import contextlib
import importlib
import io
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'models'))

import geom  # noqa: E402
import kit_tanks as K  # noqa: E402
from core import Geo, Model  # noqa: E402
from geom import cyl, lathe, rbox, tube, panel  # noqa: F401  # noqa: E402

_BOX = None


def box_models():
    """The box models by id (tools/models/build.py), loaded once."""
    global _BOX
    if _BOX is None:
        cwd = os.getcwd()
        try:
            os.chdir(os.path.join(HERE, '..', 'models'))
            with contextlib.redirect_stdout(io.StringIO()):
                import build
                _BOX = {m.id: m for m in build.models()}
        finally:
            os.chdir(cwd)
    return _BOX


def bpart(bm, name):
    for p in bm.parts:
        if p.name == name:
            return p
    raise KeyError(name)


def new_model(bm, density=26, tex_width=1024):
    m = Model(bm.id, K.scheme_for(bm.paint, seed=sum(map(ord, bm.id)) % 97 + 1), density=density, tex_width=tex_width)
    m.box = bm
    return m


def mirror_part(m, bm, name, parent=None):
    """A mesh part with the box model's name, pivot and rotation (the parent given, or none)."""
    bp = bpart(bm, name)
    return m.part(name, bp.pivot, bp.rot, parent)


def elev_abs(bm):
    """The elevating part's pivot in model space."""
    tp = bm.turret_pivot if bm.turret else (0, 0, 0)
    return tuple(tp[i] + bm.elevator_pivot[i] for i in range(3))


def muzzle_abs(bm, rail=0):
    r = bm.rails[rail]
    e = elev_abs(bm)
    return (e[0] + r[1], e[1] + r[2], e[2] + r[3])


# ---------------------------------------------------------------------------------------------------------------
# Running gear and tracks (shared by every tracked vehicle)


class Gear:
    """How a vehicle's running gear looks. The wheel positions come from the box model; the visible wheels are smaller
    than the box's by the belt's thickness (the belt runs under them on the ground)."""

    def __init__(self, **kw):
        self.wheel_kind = 'double'
        self.wheel_w = None
        self.track_w = None
        self.thick = 0.06
        self.pitch = 0.15
        self.drive = 'rear'  # where the toothed sprocket is (the other end gets the idler)
        self.rollers = []  # return rollers: (z, y, r)
        self.track_mat = 'track_steel'
        self.teeth = 13
        self.sag = 0.03
        self.idler_kind = 'double'
        self.n = 14
        for k, v in kw.items():
            if not hasattr(self, k):
                raise AttributeError(k)
            setattr(self, k, v)


def running_gear(m, bm, gear):
    """Road wheels, sprocket, idler and return rollers on both sides, and the two belts."""
    body = m.get('body')
    sides = {}
    for name, r, steer in bm.wheels:
        bp = bpart(bm, name)
        side = -1 if name.endswith('_l') else 1
        sides.setdefault(side, []).append((name, bp, r))
    rollers_geo = Geo(40)
    for side, wl in sides.items():
        # Width and centre of the track from the road wheels' boxes.
        roads = [(n, bp, r) for n, bp, r in wl if n.startswith('road_')]
        ends = [(n, bp, r) for n, bp, r in wl if not n.startswith('road_')]
        xs = [bp.pivot[0] for _, bp, _ in roads]
        x_c = float(np.mean(xs))
        bw = roads[0][1].boxes[0]
        wheel_w = gear.wheel_w or (bw.hi[0] - bw.lo[0])
        track_w = gear.track_w or wheel_w * 1.3
        circles = []
        front = max(ends, key=lambda e: e[1].pivot[2]) if ends else None
        back = min(ends, key=lambda e: e[1].pivot[2]) if ends else None
        for name, bp, r in wl:
            rv = r - gear.thick
            p = m.part(name, bp.pivot, (0, 0, 0))
            p.spin = r
            c = np.array(bp.pivot)
            if name.startswith('road_'):
                p.add(K.road_wheel(c, side, rv, wheel_w, gear.wheel_kind, n=gear.n))
                circles.append((c[2], c[1], rv))
            else:
                is_front = front is not None and name == front[0]
                drive_here = (gear.drive == 'front') == is_front
                if drive_here:
                    p.add(K.sprocket(c, side, rv, wheel_w * 0.9, gear.teeth, n=gear.n))
                    circles.append((c[2], c[1], rv * 0.86))
                else:
                    p.add(K.idler(c, side, rv, wheel_w, n=gear.n, kind=gear.idler_kind))
                    circles.append((c[2], c[1], rv))
        tops = []
        for z, y, r in gear.rollers:
            rollers_geo.extend(K.return_roller((x_c, y, z), side, r, wheel_w * 0.8))
            circles.append((z, y, r))
            tops.append((z, y + r))
        if front:
            tops.append((front[1].pivot[2], front[1].pivot[1] + front[2] - gear.thick))
        if back:
            tops.append((back[1].pivot[2], back[1].pivot[1] + back[2] - gear.thick))
        path, pitch = K.belt_path(circles, gear.pitch, gear.sag, tops)
        t = m.part('track_l' if side < 0 else 'track_r', (x_c, 0, 0), (0, 0, 0), None)
        t.track = side
        t.spin = pitch
        t.add(K.track_belt(x_c, track_w, path, gear.thick, gear.track_mat))
    body.add(rollers_geo)
    return sides


# ---------------------------------------------------------------------------------------------------------------
# Hulls


def hull_sections(stations, hw_low, hw_up, y_spon, ch=0.04, spon_ch=0.0):
    """Cross-sections of a tank hull: stations [(z, deck, belly)] -> [(z, outline)]: the narrow lower hull between the
    tracks, the sponsons over them, the deck with chamfered edges. Front/back stations where the deck is lower than the
    sponson floor squash the sponson flat."""
    out = []
    for z, deck, bot in stations:
        ys = min(y_spon, deck - 0.03)
        yb = min(bot, ys - 0.01)
        c = min(ch, (deck - ys) * 0.4)
        pts = [(-hw_low, yb), (hw_low, yb), (hw_low, ys), (hw_up - spon_ch, ys), (hw_up, ys + spon_ch), (hw_up, deck - c), (hw_up - c, deck),
               (-hw_up + c, deck), (-hw_up, deck - c), (-hw_up, ys + spon_ch), (-hw_up + spon_ch, ys), (-hw_low, ys)]
        out.append((z, pts))
    return out


def deck_y(stations, z):
    zs = [s[0] for s in stations]
    ds = [s[1] for s in stations]
    return float(np.interp(z, zs, ds))


# ---------------------------------------------------------------------------------------------------------------
# T-72B3


def louvres(x0, x1, y, z0, z1, pitch=0.1):
    """An engine deck grille: a dark recess with slats across it."""
    g = Geo(30)
    g.extend(K.chbox((x0, y - 0.005, z0), (x1, y + 0.012, z1), 'black', 0.004))
    n = int((z1 - z0) / pitch)
    for k in range(n):
        za = z0 + 0.012 + k * pitch
        g.extend(K.chbox((x0 + 0.02, y + 0.01, za), (x1 - 0.02, y + 0.03, za + pitch * 0.55), 'armour', 0.006))
    return g


def t72_like(bm, kind='t72'):
    t90 = kind != 't72'
    m = new_model(bm)
    body = m.part('body', (0, 0, 0))
    g = Geo(25)
    # Hull: rear plate leaning back, flat deck, the upper glacis at 22 degrees down to the nose, the lower glacis. The
    # upper hull is only a little wider than the lower one: the fenders over the tracks are shelves outside it.
    W = abs(bpart(bm, 'road_0_l').pivot[0]) - 1.51
    hw_up = 1.3 + W
    hw_low = 1.2 + W
    fx = 1.83 + W
    st = [(-3.47, 1.36, 0.62), (-3.40, 1.53, 0.46), (-3.3, 1.55, 0.42), (2.0, 1.55, 0.42), (2.95, 1.16, 0.42), (3.45, 0.95, 0.86)]
    secs = hull_sections(st, hw_low, hw_up, 1.0, ch=0.05)
    g.extend(K.section_loft(secs, 'armour', smooth=20))
    body.add(g)
    d = Geo(30)
    gl_angle = math.degrees(math.atan2(1.55 - 0.95, 3.45 - 2.0))
    # Kontakt-5 slabs over the upper glacis (two rows of five), wedged thicker at the top.
    mid_z = 2.0 + (3.45 - 2.0) * 0.5
    fr = K.slope_frame((0, deck_y(st, mid_z), mid_z), gl_angle, 1)
    d.extend(K.era_grid(fr, 5, 2, 0.48, 0.66, 0.07, gap=0.03, wedge=0.05))
    # The driver's hatch (in the middle, behind the glacis) and his periscope.
    d.extend(K.hatch((0, 1.55, 1.62), 0.27, h=0.05))
    d.extend(K.periscope((0, 1.55, 1.93), 0.22, (0, 0, 1)))
    # Fenders: shelves over the tracks along the hull, sloping down at the front with the glacis, turned down at the back.
    for s in (-1, 1):
        prof = [(-3.42, 0.8), (-3.4, 1.0), (2.3, 1.0), (3.42, 0.9)]
        d.extend(K.fender(s * (hw_up - 0.02), s * fx, prof, 0.025))
        # Rear mud flaps.
        d.extend(K.chbox((min(s * hw_up, s * fx), 0.62, -3.44), (max(s * hw_up, s * fx), 0.82, -3.41), 'skirt_rubber', 0.005))
    # Headlights on the front fenders with guards; tow hooks on the lower glacis.
    for s in (-1, 1):
        x = s * 1.55
        z = 2.95
        y = 0.98 - (z - 2.3) * 0.09
        d.extend(K.chbox((x - 0.14, y, z - 0.1), (x + 0.14, y + 0.06, z + 0.1), 'armour', 0.01))
        d.extend(K.headlight((x - s * 0.06, y + 0.14, z + 0.06), (0, 0, 1), r=0.07))
        d.extend(K.headlight((x + s * 0.09, y + 0.12, z + 0.06), (0, 0, 1), r=0.045, guard=False))
        d.extend(tube([(s * 0.75, 0.62, 3.2), (s * 0.75, 0.66, 3.33), (s * 0.75, 0.56, 3.38), (s * 0.75, 0.5, 3.3)], 0.03, 'metal_dark', n=6))
        d.extend(K.chbox((s * 0.75 - 0.08, 0.55, 3.05), (s * 0.75 + 0.08, 0.72, 3.22), 'armour', 0.015))
    # Side skirts hanging from the fender edges (painted rubber), Kontakt-5 panels on the front three.
    for s in (-1, 1):
        x = s * (fx + 0.01)
        segs = 7
        z0, z1 = -2.95, 2.55
        sl = (z1 - z0) / segs
        for k in range(segs):
            za = z0 + k * sl + 0.01
            zb = za + sl - 0.02
            if k >= segs - 3:
                d.extend(K.chbox((x - 0.035, 0.6, za), (x + 0.035, 1.0, zb), 'armour', 0.01))
                d.extend(K.chbox((x + s * 0.035 - 0.03, 0.66, za + 0.04), (x + s * 0.035 + 0.03, 0.94, zb - 0.04), 'era', 0.008))
            else:
                d.extend(K.chbox((x - 0.012, 0.55, za), (x + 0.012, 1.0, zb), 'armour_dark', 0.004))
    # Engine deck: two louvred grilles, the transmission access panel, lifting eyes.
    for s in (-1, 1):
        d.extend(louvres(min(s * 0.12, s * 1.12), max(s * 0.12, s * 1.12), 1.55, -3.05, -1.85))
    d.extend(K.chbox((-0.9, 1.55, -1.7), (0.9, 1.565, -0.95), 'armour', 0.005))
    for x in (-1.15, 1.15):
        d.extend(tube([(x - 0.05, 1.55, -3.25), (x - 0.05, 1.63, -3.25), (x + 0.05, 1.63, -3.25), (x + 0.05, 1.55, -3.25)], 0.012, 'metal_dark', n=4))
    # Right fender: external fuel tanks and a stowage box; left fender: boxes, and the exhaust louvre on the hull side.
    for k, (za, zb) in enumerate(((-2.82, -2.11), (-2.05, -1.35), (-1.29, -0.58))):
        d.extend(K.chbox((hw_up, 1.0, za), (fx - 0.05, 1.4, zb), 'armour', 0.05))
        d.extend(cyl(((hw_up + fx) / 2, 1.4, (za + zb) / 2), ((hw_up + fx) / 2, 1.44, (za + zb) / 2), 0.05, 'metal_dark', n=8))
        d.extend(tube([(fx - 0.05, 1.2, za + 0.05), (fx - 0.05, 1.2, zb - 0.05)], 0.008, 'metal_dark', n=4))
    d.extend(K.chbox((hw_up, 1.0, -0.45), (fx - 0.05, 1.36, 0.75), 'armour', 0.025))
    d.extend(K.chbox((-fx + 0.05, 1.0, -0.45), (-hw_up, 1.3, 0.6), 'armour', 0.025))
    d.extend(K.chbox((-fx + 0.05, 1.0, -2.0), (-hw_up, 1.33, -1.0), 'armour', 0.025))
    d.extend(K.chbox((-fx + 0.05, 1.0, -0.9), (-hw_up, 1.25, -0.55), 'armour', 0.025))
    d.extend(K.chbox((-hw_up - 0.03, 1.12, -2.95), (-hw_up + 0.01, 1.45, -2.15), 'black', 0.01))
    for k in range(5):
        z = -2.92 + k * 0.155
        d.extend(K.chbox((-hw_up - 0.06, 1.14, z), (-hw_up - 0.02, 1.43, z + 0.05), 'armour_dark', 0.005))
    # Tow cables along the deck edges.
    for s in (-1, 1):
        x = s * (hw_up - 0.1)
        d.extend(K.tow_cable([(x, 1.57, -3.2), (x, 1.58, -1.0), (x, 1.58, 1.0), (x * 0.9, 1.57, 1.8)]))
    # Rear: two 200 l fuel drums on brackets, the unditching log above them, tail lights.
    for s in (-1, 1):
        d.extend(K.drum((s * 0.85, 1.6, -3.83), (1, 0, 0), r=0.29, length=0.72))
        d.extend(K.chbox((s * 0.85 - 0.4, 1.28, -3.6), (s * 0.85 + 0.4, 1.32, -3.42), 'metal_dark', 0.01))
        for dx in (-0.25, 0.25):
            d.extend(tube([(s * 0.85 + dx, 1.3, -3.45), (s * 0.85 + dx, 1.3, -4.1), (s * 0.85 + dx, 1.9, -4.1), (s * 0.85 + dx, 1.9, -3.5)], 0.012, 'metal_dark', n=4, caps=False))
        d.extend(K.chbox((s * 1.55 - 0.1, 1.02, -3.42), (s * 1.55 + 0.1, 1.12, -3.36), 'armour', 0.01))
        d.extend(panel((s * 1.55, 1.07, -3.422), 0.14, 0.06, (-1, 0, 0), (0, 1, 0), 'taillight'))
    d.extend(cyl((-1.45, 2.08, -3.85), (1.45, 2.08, -3.85), 0.13, 'wood', n=10))
    for s in (-1, 1):
        d.extend(tube([(s * 1.15, 1.9, -3.7), (s * 1.15, 2.22, -3.72), (s * 1.15, 2.22, -3.98), (s * 1.15, 1.9, -4.0)], 0.012, 'metal_dark', n=4, caps=False))
    body.add(d)
    rl = [(-2.0, 0.76, 0.1), (-0.8, 0.76, 0.1), (0.4, 0.76, 0.1), (1.7, 0.76, 0.1)] if kind == 't80' else [(-1.95, 0.76, 0.1), (-0.05, 0.76, 0.1), (1.9, 0.76, 0.1)]
    running_gear(m, bm, Gear(rollers=rl, drive='rear', pitch=0.15, thick=0.06))

    # Turret: a cast dome, Kontakt-5 "horseshoe" on the front, the commander's cupola with the machine gun, sights,
    # smoke grenade launchers, the snorkel and stowage at the back.
    tp = mirror_part(m, bm, 'turret')
    cz = -0.3
    dz = tp.pivot[2] + 0.3
    T = Geo(50)
    levels = []
    for y, rx, f, b in ((1.55, 1.0, 1.1, 1.08), (1.64, 1.1, 1.24, 1.2), (1.8, 1.09, 1.22, 1.2), (1.98, 1.02, 1.12, 1.13), (2.14, 0.9, 0.95, 1.02),
                        (2.27, 0.74, 0.74, 0.88), (2.35, 0.6, 0.56, 0.74)):
        levels.append((y, K.superellipse(28, rx, 1.0, e=(3.6 if kind == 't90' else 2.3), cz=cz, front=f, back=b)))
    T.extend(K.plan_loft(levels, 'armour', smooth=55))
    tp.add(T.transform(np.eye(3), np.array([0, 0, dz])))
    t = Geo(30)
    # Kontakt-5 blocks round the front: on each side four boxes following the outline, leaning out.
    if kind == 't90':
        # The welded turret's front: two big Kontakt-5 wedge packs either side of the gun, angled out, over the cheeks.
        for s in (-1, 1):
            for k in range(2):
                ang = s * (24 + k * 4)
                blk = K.chbox((-0.3, -0.2, -0.1), (0.3, 0.2, 0.12), 'era', 0.02)
                blk.transform(K.rot_x(-8))
                a = math.radians(ang)
                R = K.rot_y(ang)
                t.extend(blk.transform(R, np.array([s * (0.5 + 0.5 * k) + s * 0.1, 2.05 + k * 0.0, cz + 1.18 - k * 0.12 - 0.0])))
            for k in range(2):
                t.extend(K.chbox((s * (0.25 + 0.3 * k) - 0.13, 2.3, 0.0 + k * 0.12), (s * (0.25 + 0.3 * k) + 0.13, 2.38, 0.5 + k * 0.1), 'era', 0.012))
            # Shtora-1 optronic jammers: a dark box with a red lens beside the gun.
            t.extend(K.chbox((s * 0.78 - 0.13, 2.02, 1.0), (s * 0.78 + 0.13, 2.3, 1.2), 'black', 0.02))
            t.extend(panel((s * 0.78, 2.16, 1.202), 0.16, 0.12, (-1, 0, 0), (0, 1, 0), 'beacon_red'))
    for s in ((-1, 1) if not t90 else ()):
        for k in range(4):
            th = math.radians(14 + k * 17)
            rr_x, rr_z = 1.06, 1.16
            x = s * math.sin(th) * rr_x
            z = cz + math.cos(th) * rr_z
            out = np.array([s * math.sin(th), 0, math.cos(th)])
            tang = np.array([s * math.cos(th), 0, -math.sin(th)])
            R = np.stack([tang * s, np.array([0, 1.0, 0]), out], axis=1)
            blk = K.chbox((-0.2, -0.17, -0.05), (0.2, 0.17, 0.22), 'era', 0.015)
            blk.transform(K.rot_x(-12))
            t.extend(blk.transform(R, np.array([x, 2.02, z]) + out * 0.02))
    for s in ((-1, 1) if not t90 else ()):
        t.extend(K.chbox((min(s * 0.05, s * 0.38), 2.33, 0.05), (max(s * 0.05, s * 0.38), 2.39, 0.4), 'era', 0.01))
    # The commander's cupola (right) with its hatch, periscopes and the Kord machine gun; the gunner's hatch (left).
    cx, czc = 0.5, -0.55
    t.extend(lathe((cx, 2.3, czc), (cx, 2.6, czc), [(0.0, 0.42, 'armour'), (0.1, 0.4, 'armour'), (0.14, 0.36, 'armour'), (0.14, 0.0, 'armour')], 'armour', 18, 40))
    t.extend(K.hatch((cx, 2.44, czc - 0.02), 0.3, h=0.06))
    for a in (-35, 0, 35):
        ang = math.radians(a)
        t.extend(K.periscope((cx + math.sin(ang) * 0.3, 2.36, czc + math.cos(ang) * 0.3), 0.14, (math.sin(ang), 0, math.cos(ang))))
    t.extend(K.mg((cx - 0.05, 2.62, czc + 0.25), (0, 0, 1), 1.05))
    t.extend(cyl((cx - 0.05, 2.44, czc + 0.25), (cx - 0.05, 2.6, czc + 0.25), 0.03, 'metal_dark', n=6))
    t.extend(K.hatch((-0.5, 2.35, -0.75), 0.27, h=0.05))
    # Sights: the gunner's 1A40 box on the left roof, the Sosna-U on the right.
    t.extend(K.chbox((-0.95, 2.3, -0.1), (-0.5, 2.6, 0.22), 'armour', 0.03))
    t.extend(panel((-0.725, 2.47, 0.222), 0.3, 0.14, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(K.chbox((0.3, 2.3, 0.0), (0.82, 2.62, 0.3), 'armour', 0.04))
    t.extend(K.chbox((0.38, 2.38, 0.3), (0.74, 2.56, 0.34), 'black', 0.01))
    t.extend(panel((0.47, 2.47, 0.342), 0.12, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
    t.extend(panel((0.65, 2.47, 0.342), 0.12, 0.12, (-1, 0, 0), (0, 1, 0), 'optic'))
    # Wind sensor mast and the antenna.
    t.extend(cyl((-0.1, 2.36, -1.2), (-0.1, 2.75, -1.2), 0.015, 'metal_dark', n=5))
    t.extend(cyl((-0.1, 2.75, -1.2), (-0.1, 2.8, -1.2), 0.04, 'metal_dark', n=6))
    t.extend(K.antenna((-0.95, 2.3, -1.2), 2.5))
    # Smoke grenade launchers on the front sides, four each, fanned out.
    for s in (-1, 1):
        t.extend(K.smoke_launchers((s * 1.18, 2.03, 0.42), 4, (s * 0.5, 0, 0.87), spacing=0.1, length=0.26, elev=25, spread=6))
    # The rear: stowage boxes, the snorkel tube, a rolled tarpaulin.
    for s in (-1, 1):
        t.extend(K.chbox((s * 0.4 if s > 0 else -1.2, 1.7, -1.75), (1.2 if s > 0 else -0.4, 2.08, -1.35), 'armour', 0.03))
    t.extend(cyl((-0.9, 2.2, -1.55), (0.9, 2.2, -1.55), 0.12, 'canvas', n=10, bevel=0.03))
    t.extend(cyl((-1.05, 1.9, -1.32), (1.05, 1.9, -1.32), 0.08, 'armour', n=8))
    for x in (-0.55, 0.55):
        t.extend(tube([(x, 2.08, -1.4), (x, 2.33, -1.4), (x, 2.33, -1.7), (x, 2.08, -1.7)], 0.012, 'metal_dark', n=4, caps=False))
    # Lifting hooks on the roof.
    for x, z in ((-0.8, -0.3), (0.85, -0.25), (0.0, -1.05)):
        t.extend(tube([(x - 0.04, 2.3, z), (x, 2.36, z), (x + 0.04, 2.3, z)], 0.012, 'metal_dark', n=4))
    tp.add(t.transform(np.eye(3), np.array([0, 0, dz])))

    # The gun: 2A46M-5 in its canvas mantlet boot; the barrel ends where the box model's does.
    gp = mirror_part(m, bm, 'gun', tp)
    piv = np.array(gp.pivot)
    mz = muzzle_abs(bm)
    gg = Geo(40)
    gg.extend(lathe((0, piv[1], piv[2] - 0.35), (0, piv[1], piv[2] + 0.35), [(0.0, 0.34, 'canvas'), (0.1, 0.33, 'canvas'), (0.25, 0.3, 'canvas'), (0.4, 0.26, 'canvas'),
                                                          (0.55, 0.18, 'canvas'), (0.7, 0.13, 'canvas'), (0.7, 0.0, 'canvas')], 'canvas', 14, 60))
    z0 = piv[2] + 0.1
    L = mz[2] - z0
    prof = K.tank_gun_profile(L, 0.078, extractor=(L * 0.475, L * 0.618, 0.145), sleeves=(0.9, 1.75, L * 0.69, L * 0.84),
                              muzzle='collar')
    gg.extend(K.barrel((0, piv[1], z0), (0, piv[1], mz[2]), prof))
    gp.add(gg)
    return m


def models():
    """All the models. MESH_ONLY=id,id limits what is built (the rest is skipped, which saves minutes)."""
    B = box_models()
    only = set(filter(None, os.environ.get('MESH_ONLY', '').split(',')))
    import convert
    import m_ifv
    import m_west
    import m_wheeled
    jobs = [('t72', lambda: t72_like(B['t72'])), ('t90', lambda: t72_like(B['t90'], 't90')), ('t80bvm', lambda: t72_like(B['t80bvm'], 't80'))]
    jobs += [(k, (lambda k=k: m_west.west_mbt(B[k], k))) for k in ('leopard2', 'abrams', 'challenger2')]
    jobs += [(k, (lambda k=k: m_ifv.ifv(B[k], k))) for k in ('bmp2', 'bmp3', 'bradley', 'cv90', 'm113')]
    jobs += [(k, (lambda k=k: m_wheeled.wheeled(B[k], k))) for k in ('btr82',)]
    conv = ('msta_s', 'm109', 'tos1', 'brem1', 'm88', 'maxxpro', 'kozak', 'tigr', 'hmmwv', 'tor', 'buk', 'shilka', 'tunguska', 'strela10')
    hand = {}
    for modname in ('m_arty', 'm_sam', 'm_wheel2', 'm_wheel3', 'm_gepard'):
        try:
            mod = importlib.import_module(modname)
        except ImportError:
            continue
        hand.update(mod.JOBS)
    jobs += [(k, (lambda k=k: hand[k](B.get(k)))) for k in hand]
    jobs += [(k, (lambda k=k: convert.convert(B[k]))) for k in conv if k not in hand]
    return [fn() for k, fn in jobs if not only or k in only]
