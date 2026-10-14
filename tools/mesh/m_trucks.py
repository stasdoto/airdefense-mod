"""
2.0: the wheeled military vehicles as real meshes - launchers, air-defence systems, radars, trucks. Each builder reads
its box model back (kit_trucks.ref: the parts, pivots, wheels, rails the game animates) and dresses the same parts with
real shapes from the kit (kit_trucks.py): cabs, chassis, wheels, launch containers, missiles, antennas.
"""
import math

import numpy as np

import geom
import kit_trucks as K
from core import Geo, Model, rot_x, rot_y, rot_z
from kit_trucks import V, box, rbox, cbox, cyl, tube, bar

DENSITY = 26
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

    def wheels(self, rim='paint', width=None, n=20, lugs=22, rim_ratio=0.5, sink=0.015, names=None):
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
    b.wheels(rim='paint', lugs=20)
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


BUILDERS = [himars]


def models():
    return [f() for f in BUILDERS]
