"""
2.0: the wheeled military vehicles as real meshes - launchers, air-defence systems, radars, trucks. Each builder reads
its box model back (kit_trucks.ref: the parts, pivots, wheels, rails the game animates) and dresses the same parts with
real shapes from the kit (kit_trucks.py): cabs, chassis, wheels, launch containers, missiles, antennas.
"""
import math
import sys

import numpy as np

import geom
import kit_trucks as K
from core import Geo, Model, rot_x, rot_y, rot_z
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar

DENSITY = 22
QUANT = 5


class Build:
    """A model being built: the reference, the parts by name, helpers to dress them."""

    def __init__(self, id, paint=None, seed=None, dirt=0.85, density=DENSITY):
        self.r = K.ref(id)
        p = paint or self.r.paint
        sc = K.scheme(p, seed=seed if seed is not None else (sum(map(ord, id)) % 97), dirt=dirt,
                      hand_colour=K.HANDMADE_PAINT.get(id))
        self.m = Model(id, sc, density=density, tex_width=1024)
        self.m.quant = QUANT
        self.p = K.skeleton(self.r, self.m)
        self.body = self.p.get('body') or self.m.part('body')
        self.lo, self.hi = self.r.bounds()

    def __getitem__(self, name):
        return self.p[name]

    def wheels(self, rim="paint", width=None, n=16, lugs=16, rim_ratio=0.5, sink=0.015, names=None):
        """Every wheel of the reference: a military wheel on its own part at the same axle."""
        for name, R, steer in self.r.wheels:
            if names is not None and name not in names:
                continue
            rp = self.r.by[name]
            piv = np.array(rp.pivot)
            b = rp.bounds()
            w = width if width is not None else (b[1][0] - b[0][0]) - 0.06
            r = min(R, piv[1] + sink)
            side = -1 if piv[0] < 0 else 1
            self.m.tyre_half = w / 2
            self.m.tyre_lugs = lugs
            self.p[name].add(K.mil_wheel(piv, side, r, w, rim=rim, n=n, rim_ratio=rim_ratio, lugs=lugs))

    def wheel_pos(self, name):
        rp = self.r.by[name]
        return np.array(rp.pivot)

    def axles(self, names=None, y=None, track=None):
        """Axle housings between each pair of wheels (_l / _r)."""
        g = Geo(30)
        done = set()
        for name, R, steer in self.r.wheels:
            if not name.endswith('_l'):
                continue
            if names is not None and name not in names:
                continue
            c = self.wheel_pos(name)
            g.extend(K.axle(abs(c[0]) - 0.2, c[1], c[2]))
            done.add(name)
        self.body.add(g)

    def finish(self):
        lo, hi = self.m.bounds()
        return self.m


# ---------------------------------------------------------------------------------------------------------------
# Cabs of the trucks


def fmtv_cab(b, part, z0, z1, y0, y1, w, seats, armoured=True):
    """The FMTV's cab (HIMARS): a tall box over the front axle, a near-vertical split windscreen, the armoured cab's
    flat sloped panels, big square fenders, the grille low in front, lamps in the bumper."""
    zf = z1
    yb = y0 + (y1 - y0) * 0.5
    ws_top = y1 - 0.12
    prof = [(z0, y0, 0.03), (zf - 0.02, y0, 0.05), (zf, yb - 0.25, 0.06), (zf - 0.03, yb, 0.03),
            (zf - 0.035, yb + 0.06, 0, 'ws0'), (zf - 0.16, ws_top - 0.05, 0, 'ws1'), (zf - 0.19, ws_top, 0.1), (z0 + 0.1, y1, 0.08)]
    cab = K.Cab(prof, w, r=0.09)
    cab.windscreen([(-w + 0.16, -0.05), (0.05, w - 0.16)])
    cab.side_window(zf - 0.85, zf - 0.17, yb + 0.06, ws_top - 0.07, follow=0.08)
    part.add(cab.geo())
    b.m.decals.append(K.seam_decal(V(w, (y0 + y1) / 2, (z0 + z1) / 2), V(0, 0, -(z1 - z0)), V(0, y1 - y0, 0), [(-0.48, -0.75, 0.42, 0.85)]))
    b.m.decals.append(K.seam_decal(V(-w, (y0 + y1) / 2, (z0 + z1) / 2), V(0, 0, (z1 - z0)), V(0, y1 - y0, 0), [(-0.42, -0.75, 0.48, 0.85)]))
    g = Geo(30)
    for s in (-1, 1):
        x = s * w
        # Door handle, hinges, the grab handle by the door.
        g.extend(box((x - 0.02 if s > 0 else x - 0.012, yb - 0.02, zf - 0.75), (x + 0.012 if s > 0 else x + 0.02, yb + 0.01, zf - 0.6), 'metal_dark'))
        g.extend(K.handrail([(x + s * 0.03, yb - 0.4, zf - 0.95), (x + s * 0.07, yb - 0.38, zf - 0.95), (x + s * 0.07, yb + 0.35, zf - 0.95), (x + s * 0.03, yb + 0.37, zf - 0.95)]))
        # Mirrors on tube arms out in front of the doors.
        g.extend(K.mirror((x, ws_top - 0.1, zf - 0.25), (x + s * 0.32, ws_top - 0.18, zf - 0.05), (0.2, 0.36), s))
        g.extend(K.mirror((x, yb - 0.1, zf - 0.1), (x + s * 0.2, yb - 0.2, zf + 0.12), (0.16, 0.16), s))
    # Wipers at the foot of the panes, hinged at the top on FMTV (laid along the top frame).
    a, c = cab.ws_corners()
    for xa in (-w * 0.55, w * 0.1):
        g.extend(K.wiper((xa, c[1] - 0.02, c[0] + 0.02), (xa + w * 0.4, c[1] - 0.05, c[0] + 0.03)))
    # Roof: the gunner's hatch ring, marker lamps, the air-conditioning box behind.
    g.extend(cyl((0.35, y1 - 0.01, z0 + 0.6), (0.35, y1 + 0.06, z0 + 0.6), 0.36, 'paint', n=16, bevel=0.02))
    g.extend(cyl((0.35, y1 + 0.06, z0 + 0.6), (0.35, y1 + 0.1, z0 + 0.6), 0.3, 'paint', n=16, bevel=0.015))
    for s in (-1, 1):
        g.extend(cbox((s * (w - 0.15), y1 + 0.015, zf - 0.25), (0.08, 0.03, 0.05), 'indicator'))
    part.add(g)
    return cab


def himars():
    b = Build('himars')
    m = b.m
    body = b.body
    W = 1.19
    cab_z0, cab_z1, cab_y0, cab_y1 = 1.875, 3.5, 0.94, 2.94
    cab = fmtv_cab(b, body, cab_z0, cab_z1, cab_y0, cab_y1, W, b.r.seats)
    K.cab_inside_part(body, 'cab_inside', cab, b.r.seats, dash_y=cab_y0 + 0.85, tilt=-62)
    g = Geo(30)
    # Frame, the deck under the launcher, the hull under the cab.
    g.extend(K.frame_rails(0.43, 0.8, 1.1, -3.45, 3.3, cross=(-3.2, -2.0, -0.6, 0.8, 2.0, 3.1)))
    g.extend(box((-1.19, 1.12, -3.5), (1.19, 1.38, 1.85), 'paint', faces=('left', 'right', 'back', 'front', 'bottom')))
    g.extend(box((-1.17, 1.38, -3.48), (1.17, 1.4, 1.83), 'grating', faces=('top',)))
    # Front: bumper with the lamps behind guards, the grille, towing eyes, the winch.
    g.extend(K.rbox((-1.18, 0.75, 3.38), (1.18, 1.05, 3.62), 'paint', r=0.03))
    g.extend(geom.panel((0, 1.35, cab_z1 + 0.005), 1.3, 0.42, (1, 0, 0), (0, 1, 0), 'grille'))
    for s in (-1, 1):
        g.extend(K.headlamp((s * 0.88, 0.92, 3.62), r=0.085, depth=0.08))
        g.extend(cyl((s * 0.45, 0.85, 3.62), (s * 0.45, 0.85, 3.72), 0.05, 'metal_dark', n=8))
        # The big square front fenders over the steered wheels, with the side marker lamp.
        g.extend(K.fender(s * 0.66, s * 1.22, 2.375, 0.5625, 0.7, -5, 185) if s > 0 else K.fender(-1.22, -0.66, 2.375, 0.5625, 0.7, -5, 185))
        g.extend(K.step(s * 1.0, s * 1.24, 0.62, 1.9, 2.3) if s > 0 else K.step(-1.24, -1.0, 0.62, 1.9, 2.3))
        # Battery box and fuel tank under the deck, the air tanks.
        if s < 0:
            g.extend(K.fuel_tank(-0.55, -1.12, 0.62, 1.08, -0.3, 0.9, 'box'))
        else:
            g.extend(K.toolbox((0.6, 0.65, -0.35), (1.12, 1.08, 0.85)))
        g.extend(cyl((s * 0.7, 0.95, 1.0), (s * 0.7, 0.95, 1.7), 0.11, 'chassis', n=8))
        # Rear mudguards over the tandem, rear lamps.
        g.extend(K.flat_guard(min(s * 0.62, s * 1.24), max(s * 0.62, s * 1.24), -3.4, -0.5, 1.27, 'paint', lip=0.15, flap=0.35))
        g.extend(K.lamp_cluster((s * 0.95, 1.05, -3.52), s))
        # The rear stabilising jacks.
        g.extend(K.outrigger(s * 1.0, -3.3, 1.25, down=0.0, side=s))
    # Exhaust stack behind the cab on the right.
    g.extend(K.exhaust_stack(1.05, 1.4, 3.05, 1.72, r=0.055))
    g.extend(K.whip_antenna((-1.0, 2.95, 1.98), 0.9))
    g.extend(K.whip_antenna((1.0, 2.95, 1.98), 0.7))
    body.add(g)
    b.axles()
    b.wheels(rim='paint')
    # The turntable, the launcher's frame and the pod (6 GMLRS rockets).
    t = b['turret']
    tg = Geo(30)
    tg.extend(cyl((0, 1.375, -1.31), (0, 1.55, -1.31), 0.88, 'paint', n=24, bevel=0.02))
    tg.extend(cyl((0, 1.4, -1.31), (0, 1.43, -1.31), 0.92, 'chassis', n=24))
    tg.extend(box((-0.62, 1.55, -2.2), (0.62, 1.66, -0.45), 'paint'))
    for s in (-1, 1):
        tg.extend(bar((s * 0.55, 1.55, -0.6), (s * 0.55, 1.7, -3.2), 0.14, 0.14, 'paint'))
    tg.extend(K.ram((0.0, 1.55, -0.7), (0.0, 1.7, -2.2), r=0.08))
    t.add(tg)
    pod = b['pod']
    pg = Geo(30)
    x0, x1, y0, y1, z0, z1 = -1.125, 1.125, 1.6875, 3.0, -3.3125, 1.3125
    pg.extend(K.rbox((x0, y0, z0), (x1, y1, z1), 'paint', r=0.04))
    # Stiffening ribs round the pod, the lifting eyes on top, the front cover with the six tube ends.
    for k in range(6):
        z = z0 + 0.35 + k * (z1 - z0 - 0.7) / 5
        pg.extend(K.band((x0, y0, z0), (x1, y1, z1), 2, z, 0.1, 0.03, faces=('top', 'left', 'right')))
    for x in (-0.8, 0.8):
        for z in (z0 + 0.3, z1 - 0.3):
            pg.extend(cyl((x, y1 + 0.03, z - 0.06), (x, y1 + 0.03, z + 0.06), 0.06, 'metal_dark', n=6))
    for _, rx, ry, rz in b.r.rails:
        c = np.array(pod.pivot) + [rx, ry, rz]
        pg.extend(K.disc((c[0], c[1], z1 + 0.003), 0.15, (0, 0, 1), 'tpk_end', 12))
        pg.extend(cyl((c[0], c[1], z1 - 0.01), (c[0], c[1], z1 + 0.012), 0.165, 'paint', n=12, caps=False))
    pg.extend(K.panel_m((0, y1 + 0.031, (z0 + z1) / 2), 1.6, 3.6, (1, 0, 0), (0, 0, -1), 'grating'))
    pod.add(pg)
    m.decals.append(materials_text((x1 + 0.001, 2.45, -0.8), (0, 0, 1.2), (0, 0.22, 0), 'M31 GMLRS', (220, 220, 210)))
    m.decals.append(materials_text((x0 - 0.001, 2.45, -0.8), (0, 0, -1.2), (0, 0.22, 0), 'M31 GMLRS', (220, 220, 210)))
    return b.finish()


def materials_text(c, u, v, text, fg):
    import materials
    d = materials.text_decal(V(*c), V(*u), V(*v), text, fg)
    d.depth = 0.05
    return d



# ---------------------------------------------------------------------------------------------------------------
# A generic heavy 6x6 / 8x8 carrier for the hand-modelled launchers


def carrier(b, cab, deck, zrear, w=1.19, fender_r=0.08, tanks=True, jacks=True, front=None):
    """cab = (z0, z1, y0, y1) of the cab, deck = (y0, y1) of the load deck, zrear = the frame's tail. Frame, deck, cab,
    bumper and lamps, fenders over the steered wheels, mudguards over the rear tandem, tanks, jacks, antennas, wheels."""
    body = b.body
    cz0, cz1, cy0, cy1 = cab
    d0, d1 = deck
    cab_obj = fmtv_cab(b, body, cz0, cz1, cy0, cy1, w, b.r.seats)
    K.cab_inside_part(body, 'cab_inside', cab_obj, b.r.seats, dash_y=cy0 + 0.85, tilt=-62)
    g = Geo(30)
    g.extend(K.frame_rails(0.43, d0 - 0.4, d0 - 0.05, zrear + 0.05, cz1 - 0.2, cross=tuple(np.linspace(zrear + 0.3, cz0 - 0.3, 6))))
    g.extend(box((-w, d0, zrear), (w, d1 - 0.02, cz0 + 0.05), 'paint', faces=('left', 'right', 'back', 'front', 'bottom')))
    g.extend(box((-w + 0.02, d1 - 0.02, zrear + 0.02), (w - 0.02, d1, cz0), 'grating', faces=('top',)))
    g.extend(K.rbox((-w + 0.01, d0 - 0.37, cz1 - 0.12), (w - 0.01, d0 - 0.07, cz1 + 0.12), 'paint', r=0.03))
    g.extend(geom.panel((0, cy0 + 0.41, cz1 + 0.005), 1.3, 0.42, (1, 0, 0), (0, 1, 0), 'grille'))
    for s in (-1, 1):
        g.extend(K.headlamp((s * 0.88, d0 - 0.2, cz1 + 0.12), r=0.085, depth=0.08))
        g.extend(cyl((s * 0.45, d0 - 0.25, cz1 + 0.12), (s * 0.45, d0 - 0.25, cz1 + 0.22), 0.05, 'metal_dark', n=8))
        g.extend(K.lamp_cluster((s * 0.95, d0 + 0.0, zrear - 0.02), s))
        if tanks:
            if s < 0:
                g.extend(K.fuel_tank(-w * 0.45, -w + 0.07, d0 - 0.5, d0 - 0.04, -0.5, 0.8, 'box'))
            else:
                g.extend(K.toolbox((w * 0.45, d0 - 0.5, -0.5), (w - 0.07, d0 - 0.04, 0.8)))
    ws = [(n, R, st) for n, R, st in b.r.wheels if n.endswith('_l')]
    for n, R, st in ws:
        pz = b.r.by[n].pivot[2]
        px = abs(b.r.by[n].pivot[0])
        if st:
            for s in (-1, 1):
                x0, x1 = (px - 0.34, px + 0.22) if s > 0 else (-px - 0.22, -px + 0.34)
                g.extend(K.fender(x0, x1, pz, R - 0.06, R + fender_r, -5, 185))
    rear = [b.r.by[n].pivot[2] for n, R, st in ws if not st]
    if rear:
        pr = abs(b.r.by[ws[0][0]].pivot[0])
        for s in (-1, 1):
            x0, x1 = (pr - 0.34, pr + 0.24) if s > 0 else (-pr - 0.24, -pr + 0.34)
            g.extend(K.flat_guard(x0, x1, min(rear) - 0.8, max(rear) + 0.8, ws[0][1] * 2 + 0.07, 'paint', lip=0.15, flap=0.3))
    if jacks:
        for s in (-1, 1):
            g.extend(K.outrigger(s * (w - 0.2), zrear + 0.12, d0, down=0.0, side=s))
    g.extend(K.exhaust_stack(w - 0.14, d0, cy1 - 0.2, cz0 + 0.1, r=0.055))
    g.extend(K.whip_antenna((-w + 0.19, cy1 + 0.01, cz0 + 0.15), 0.9))
    g.extend(K.whip_antenna((w - 0.19, cy1 + 0.01, cz0 + 0.15), 0.7))
    body.add(g)
    b.axles()
    b.wheels(rim='paint')
    return cab_obj


def canister(a, c, w, h, ribs=5, mat='paint'):
    """A box launch canister from its back a to its muzzle c: ribbed, the closed front with a frangible cover."""
    return K.tpk(a, c, w, h, mat, ribs=ribs)


def patriot():
    b = Build('patriot')
    carrier(b, (3.44, 5.19, 1.12, 3.19), (1.12, 1.44), -5.2, w=1.25)
    t = b['turret']
    tg = Geo(30)
    tg.extend(cyl((0, 1.44, -1.62), (0, 1.62, -1.62), 1.12, 'paint', n=28, bevel=0.02))
    tg.extend(cyl((0, 1.46, -1.62), (0, 1.5, -1.62), 1.16, 'chassis', n=28))
    t.add(tg)
    la = b['launcher']
    lg = Geo(30)
    # The launcher frame: two long beams, cross members, the cable trays; the rear hinge block.
    for s in (-1, 1):
        lg.extend(K.rbox((s * 1.0 - 0.07, 1.62, -4.44), (s * 1.0 + 0.07, 1.81, 1.75), 'paint', r=0.02))
    for z in (-4.3, -2.5, -0.5, 1.3):
        lg.extend(K.rbox((-1.06, 1.62, z - 0.08), (1.06, 1.81, z + 0.08), 'paint', r=0.02))
    la.add(lg)
    for n_, c in zip(('can_0', 'can_1', 'can_2', 'can_3'), range(4)):
        rp = b.r.by[n_]
        lo, hi = rp.bounds()
        pg = Geo(40)
        x0, x1 = lo[0] + 0.03, hi[0] - 0.03
        y0, y1 = lo[1] + 0.03, hi[1] - 0.03
        pg.extend(K.rbox((x0, y0, lo[2]), (x1, y1, hi[2]), 'paint', r=0.035))
        for k in range(8):
            z = lo[2] + 0.4 + k * (hi[2] - lo[2] - 0.8) / 7
            pg.extend(K.band((x0, y0, lo[2]), (x1, y1, hi[2]), 2, z, 0.12, 0.025))
        # The front cover (the canister's closure) and the rear end plate.
        pg.extend(geom.panel(((x0 + x1) / 2, (y0 + y1) / 2, hi[2] + 0.004), x1 - x0 - 0.12, y1 - y0 - 0.12, (1, 0, 0), (0, 1, 0), 'tpk_end'))
        pg.extend(K.rbox((x0 + 0.03, y0 + 0.03, hi[2] - 0.02), (x1 - 0.03, y1 - 0.03, hi[2] + 0.02), 'paint', r=0.01))
        for u in (0.25, 0.75):
            for v in (0.25, 0.75):
                pg.extend(cyl((x0 + (x1 - x0) * u, y0 + (y1 - y0) * v, hi[2]), (x0 + (x1 - x0) * u, y0 + (y1 - y0) * v, hi[2] + 0.012), 0.07, 'steel', n=10))
        b[n_].add(pg)
    b.m.decals.append(materials_text((1.001, 3.0, -0.5), (0, 0, 1.4), (0, 0.2, 0), 'PATRIOT', (225, 225, 215)))
    return b.finish()


def iris_t():
    b = Build('iris_t')
    carrier(b, (3.38, 5.12, 1.12, 3.19), (1.12, 1.44), -5.12, w=1.25)
    la = b['launcher']
    lg = Geo(30)
    lg.extend(cyl((0, 1.44, -3.9), (0, 1.52, -3.9), 0.9, 'paint', n=24))
    lg.extend(K.rbox((-0.94, 1.5, -5.0), (0.94, 1.62, -0.44), 'paint', r=0.03))
    lg.extend(K.rbox((-0.94, 1.62, -3.05), (0.94, 2.56, -2.9), 'paint', r=0.02))
    la.add(lg)
    for n_, *_ in b.r.rails:
        rp = b.r.by[n_]
        lo, hi = rp.bounds()
        pg = Geo(30)
        pg.extend(K.tpk((lo[0] + 0.02, lo[1] + 0.02, lo[2]), (hi[0] - 0.02, hi[1] - 0.02, hi[2]), hi[0] - lo[0] - 0.04, hi[1] - lo[1] - 0.04, 'paint', ribs=5))
        b[n_].add(pg)
    return b.finish()


def nasams():
    b = Build('nasams', dirt=0.8)
    carrier(b, (2.56, 4.31, 1.12, 3.12), (1.12, 1.38), -4.31, w=1.25)
    t = b['turret']
    tg = Geo(30)
    tg.extend(cyl((0, 1.38, -1.0), (0, 1.62, -1.0), 0.9, 'paint', n=24, bevel=0.02))
    t.add(tg)
    la = b['launcher']
    lg = Geo(30)
    lg.extend(K.rbox((-1.0, 1.62, -3.75), (1.0, 1.75, 0.69), 'paint', r=0.03))
    la.add(lg)
    for n_, *_ in b.r.rails:
        rp = b.r.by[n_]
        lo, hi = rp.bounds()
        pg = Geo(30)
        pg.extend(K.tpk((lo[0] + 0.02, lo[1] + 0.02, lo[2]), (hi[0] - 0.02, hi[1] - 0.02, hi[2]), hi[0] - lo[0] - 0.04, hi[1] - lo[1] - 0.04, 'paint', ribs=5))
        b[n_].add(pg)
    return b.finish()


def iskander():
    b = Build('iskander')
    carrier(b, (4.56, 6.5, 1.19, 3.19), (1.38, 1.75), -6.45, w=1.5, tanks=True)
    g = Geo(30)
    # The long shelter behind the cab: side walls, the roof (two halves that open), the rear doors.
    g.extend(K.rbox((-1.5, 1.75, -6.38), (-1.38, 3.0, 3.0), 'paint', r=0.02))
    g.extend(K.rbox((1.38, 1.75, -6.38), (1.5, 3.0, 3.0), 'paint', r=0.02))
    g.extend(box((-1.5, 1.38, 2.88), (1.5, 3.0, 3.0), 'paint'))
    g.extend(box((-1.5, 1.38, -6.38), (1.5, 3.0, -6.31), 'paint'))
    b.body.add(g)
    for rn, s in (('roof_l', -1), ('roof_r', 1)):
        rg = Geo(30)
        x0, x1 = (-1.5, 0.0) if s < 0 else (0.0, 1.5)
        rg.extend(K.rbox((x0, 3.0, -6.38), (x1, 3.25, 3.0), 'paint', r=0.04))
        for z in np.linspace(-5.5, 2.5, 6):
            rg.extend(box((x0, 3.24, z - 0.04), (x1, 3.3, z + 0.04), 'paint'))
        b[rn].add(rg)
    er = b['erector']
    eg = Geo(30)
    eg.extend(K.rbox((-0.19, 1.81, -6.0), (0.19, 2.06, 0.75), 'paint', r=0.03))
    for z in (-5.2, -0.8):
        eg.extend(K.rbox((-1.0, 1.81, z - 0.2), (1.0, 1.94, z + 0.2), 'paint', r=0.03))
    er.add(eg)
    for k, x in ((0, -0.62), (1, 0.62)):
        n_ = 'missile_%d' % k
        pg = Geo(30)
        pg.extend(K.missile((x, 2.42, -5.94), (x, 2.42, 1.38), 0.44, body='missile_grey', nose=0.28, fins=4, fin_len=0.9, fin_h=0.35, n=16, canards=0.5))
        pg.extend(K.rbox((x - 0.62, 2.32, -5.94), (x + 0.62, 2.38, -5.12), 'paint', r=0.01))
        b[n_].add(pg)
    return b.finish()


def kalibr():
    b = Build('kalibr')
    carrier(b, (4.56, 6.5, 1.19, 3.19), (1.38, 1.88), -6.45, w=1.5)
    g = Geo(30)
    g.extend(K.rbox((-1.5, 1.88, 1.0), (1.5, 2.62, 3.0), 'paint', r=0.04))
    b.body.add(g)
    er = b['erector']
    eg = Geo(30)
    eg.extend(K.rbox((-1.19, 1.88, -6.19), (1.19, 2.06, 0.12), 'paint', r=0.03))
    for z in (-5.18, -2.18, 0.82):
        eg.extend(K.rbox((-0.94, 2.06, z - 0.06), (0.94, 3.81, z + 0.06), 'paint', r=0.02))
    er.add(eg)
    for n_, *_ in b.r.rails:
        rp = b.r.by[n_]
        lo, hi = rp.bounds()
        c = ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2)
        pg = Geo(30)
        pg.extend(K.tpk((c[0], c[1], lo[2]), (c[0], c[1], hi[2]), 0.88, mat='paint', ribs=9, round_=True, r=0.42))
        b[n_].add(pg)
    return b.finish()


def shahed():
    b = Build('shahed', dirt=0.7)
    carrier(b, (2.56, 4.06, 1.06, 3.06), (1.06, 1.38), -4.31, w=1.25)
    r = b['rack']
    rg = Geo(30)
    for x in (-0.94, 0.94):
        for z in (-3.9, -0.78):
            rg.extend(K.rbox((x - 0.06, 1.38, z - 0.1), (x + 0.06, 5.2, z + 0.1), 'paint', r=0.015))
    for y in (1.56, 2.25, 3.0, 3.69, 4.44):
        rg.extend(K.rbox((-0.12, y, -3.88), (0.12, y + 0.08, 0.12), 'paint', r=0.01))
        rg.extend(K.rbox((-1.0, y, -3.69), (1.0, y + 0.07, -3.56), 'paint', r=0.01))
    r.add(rg)
    for n_, *_ in b.r.rails:
        rp = b.r.by[n_]
        lo, hi = rp.bounds()
        y = lo[1]
        pg = Geo(30)
        # The delta wing drone: fuselage, the swept wing (a flat plate with the tips turned up), the engine and prop at the tail.
        cy = y + 0.12
        pg.extend(geom.lathe((0, cy, -3.75), (0, cy, -0.44), [(0, 0, 'white'), (0.05, 0.1, 'white'), (0.8, 0.16, 'white'), (2.2, 0.15, 'white'), (3.1, 0.1, 'white'), (3.31, 0.0, 'white')], 'white', 12, 40))
        wing = [(0, cy - 0.04, -0.7), (-1.2, cy - 0.04, -3.75), (-1.2, cy - 0.04, -3.4), (-0.0, cy - 0.04, -2.0)]
        for sx in (-1, 1):
            pts = np.array([(0, cy, -3.4), (sx * 1.25, cy, -3.75), (sx * 1.25, cy, -3.25), (sx * 0.15, cy, -1.0)])
            g1 = Geo(0)
            tt = 0.025
            g1.add(pts + [0, tt, 0], 'white')
            g1.add((pts - [0, tt, 0])[::-1], 'white')
            for i in range(4):
                a_, c_ = pts[i], pts[(i + 1) % 4]
                g1.add(np.array([a_ - [0, tt, 0], c_ - [0, tt, 0], c_ + [0, tt, 0], a_ + [0, tt, 0]]), 'white')
            geom.fix_winding(g1, pts.mean(axis=0))
            pg.extend(g1)
            pg.extend(K.rbox((sx * 1.25 - 0.02, cy, -3.78), (sx * 1.25 + 0.02, cy + 0.42, -3.3), 'white', r=0.01))
        pg.extend(cyl((0, cy, -3.88), (0, cy, -3.74), 0.09, 'metal_dark', n=10))
        pg.extend(box((-0.015, cy - 0.28, -3.9), (0.015, cy + 0.28, -3.88), 'black'))
        pg.extend(box((-0.28, cy - 0.015, -3.9), (0.28, cy + 0.015, -3.88), 'black'))
        b[n_].add(pg)
    return b.finish()


# ---------------------------------------------------------------------------------------------------------------
# The vehicles nobody models by hand: the general converter (boxes -> chamfered shapes), with real military wheels.

CONVERT_IDS = ['s300', 'pantsir', 'bm21', 'fuel_truck', 'supply_truck', 'p18', 'st68', 'trml4d', 'sentinel', 'mpq65', 'elm2084',
               'osa', 'sampt', 'avenger', 'mfg', 'zu23', 'iron_dome', 'tpq36', 'bastion', 'nmesis', 'orlan', 'tb2_gcs', 'lancet',
               'switchblade', 'bukovel', 'zoopark', 'kupol', 'borisoglebsk']


def converted(id):
    import convert
    bm = K._box_model(id)
    m = convert.convert(bm, density=DENSITY - 3)
    m.quant = QUANT
    # Replace the plain wheels by cross-country military wheels (same pivots, spin and steer).
    wheels = {n: (r, st) for n, r, st in bm.wheels}
    for part in m.parts:
        if part.name in wheels:
            r, st = wheels[part.name]
            bp = [q for q in bm.parts if q.name == part.name][0]
            w = max(0.3, min(0.62, (bp.boxes[0].hi[0] - bp.boxes[0].lo[0]) if bp.boxes else 0.4))
            piv = np.array(part.pivot)
            side = -1 if piv[0] < 0 else 1
            part.geos = []
            m.tyre_half = w / 2
            m.tyre_lugs = 16
            part.add(K.mil_wheel(piv, side, r, w, rim='paint', n=16, lugs=16))
    return m


def _mk(id):
    return lambda: converted(id)


BUILDERS = {'himars': himars, 'patriot': patriot, 'iris_t': iris_t, 'nasams': nasams, 'iskander': iskander, 'kalibr': kalibr, 'shahed': shahed}
for _i in CONVERT_IDS:
    BUILDERS[_i] = _mk(_i)


def models():
    import os
    only = os.environ.get('TRUCKS_ONLY')
    if not only and '--only' in sys.argv:
        only = sys.argv[sys.argv.index('--only') + 1]
    ids = only.split(',') if only else list(BUILDERS)
    return [BUILDERS[i]() for i in ids if i in BUILDERS]
