"""
2.0 emergency services: the ambulance (a lofted van: short bonnet, raked windscreen, a cab, the tall patient box) and
the fire engine (a cab-over cab with real window openings, the equipment body with roller-shutter lockers, ladders,
a roof light bar, three axles). Both keep the box models' size, axles and part names (wheel_N_l/r, steering parts).
"""
import math

import numpy as np

import geom
import kit_town as K
import materials
from core import Geo, Model, face_normal, normalize, rot_x
from town_cars import full_ring, _orient_like


def lerp(a, b, t):
    return a + (b - a) * min(1.0, max(0.0, t))


def interp(table, z):
    """Piecewise linear value of table [(z, v), ...] (z descending)."""
    if z >= table[0][0]:
        return table[0][1]
    for (z0, v0), (z1, v1) in zip(table, table[1:]):
        if z1 <= z <= z0:
            return lerp(v0, v1, (z0 - z) / (z0 - z1))
    return table[-1][1]


# ---------------------------------------------------------------------------------------------------------------
# The ambulance


class Van:
    zf = 3.0
    zr = -3.08
    hw = 1.05
    ybot = 0.3
    nose_len = 0.5
    tail_len = 0.3
    nose_p = 3.5
    tail_p = 4.0
    wheels = [(1.9, 0.42, 0.88, 0.3, True), (-1.7, 0.42, 0.88, 0.3, False)]
    arch_gap = 0.07
    # z -> roof height (descending z)
    ROOF = [(3.0, 1.08), (2.55, 1.3), (2.3, 1.38), (1.3, 2.25), (0.72, 2.25), (0.62, 2.75), (-3.08, 2.75)]
    cab_win = (0.86, 2.02)
    ws = (1.32, 2.3)

    def X(self, z):
        z0f = self.zf - self.nose_len
        z0r = self.zr + self.tail_len
        if z > z0f:
            t = min(1.0, (z - z0f) / self.nose_len)
            return self.hw * max(0.0, 1 - t ** 2) ** (1 / self.nose_p)
        if z < z0r:
            t = min(1.0, (z0r - z) / self.tail_len)
            return self.hw * max(0.0, 1 - t ** 2) ** (1 / self.tail_p)
        return self.hw

    def arch_y(self, z):
        best = self.ybot + 0.02
        for (wz, r, track, w, st) in self.wheels:
            R = r + self.arch_gap
            d = abs(z - wz)
            if d < R:
                best = max(best, r + math.sqrt(R * R - d * d))
        return best

    def in_arch(self, z):
        return any(abs(z - wz) < r + self.arch_gap + 1e-6 for (wz, r, _t, _w, _s) in self.wheels)

    def roof(self, z):
        return interp(self.ROOF, z)

    def rxy(self, R):
        return (0.2, 0.16) if R < 1.6 else ((0.12, 0.12) if R < 2.4 else (0.26, 0.26))

    def levels(self, z):
        R = self.roof(z)
        rx, ry = self.rxy(R)
        ys = R - ry
        belt = min(0.95, ys - 0.03)
        wlo = belt
        whi = belt
        if self.cab_win[0] <= z <= self.cab_win[1]:
            wlo = min(1.28, ys - 0.02)
            whi = max(wlo, min(2.04, ys - 0.03))
        return belt, wlo, whi, ys, R, ry

    def stations(self):
        zs = set()
        z0f = self.zf - self.nose_len
        z0r = self.zr + self.tail_len
        for k in range(7):
            th = math.pi / 2 * k / 6
            zs.add(z0f + self.nose_len * math.sin(th))
            zs.add(z0r - self.tail_len * math.sin(th))
        zs.add(z0f + self.nose_len * 0.93)
        zs.add(z0r - self.tail_len * 0.93)
        for (wz, r, track, w, st) in self.wheels:
            R = r + self.arch_gap
            for k in range(-6, 7):
                zs.add(wz + R * math.sin(k / 6 * math.pi / 2))
        for z in (2.6, 2.3, 2.2, 2.05, 2.02, 1.99, 1.8, 1.6, 1.45, 1.32, 1.29, 1.2, 1.05, 0.9, 0.86, 0.83, 0.72, 0.67, 0.62, 0.55):
            zs.add(z)
        for z in np.arange(0.4, -2.9, -0.55):
            zs.add(round(float(z), 3))
        zs = sorted(z for z in zs if self.zr <= z <= self.zf)
        out = [zs[0]]
        for z in zs[1:]:
            if z - out[-1] > 0.006:
                out.append(z)
        out[-1] = zs[-1]
        return sorted(set(round(z, 4) for z in out))


    def mat(self, b, zm, right):
        V = self
        belt, wlo, whi, ys, R, ry = V.levels(zm)
        if b == 0:
            return 'tl_under'
        if b in (1, 2):
            return 'tl_liner' if V.in_arch(zm) else 'tl_under'
        if b == 3:
            return 'tl_liner' if V.in_arch(zm) else 'trim'
        if b == 4:
            return 'plastic' if (zm > V.zf - 0.45 or zm < V.zr + 0.4) else 'paint'
        if b in (5, 6):
            return 'paint'
        win = V.cab_win[0] - 0.03 < zm < V.cab_win[1] + 0.03 and whi - wlo > 0.06
        in_cab_win = V.cab_win[0] + 0.03 < zm < V.cab_win[1] - 0.01 and whi - wlo > 0.06
        if b == 7:
            if in_cab_win:
                return 'glass'
            return 'trim' if win else 'paint'
        if b == 8:
            return 'trim' if win else 'paint'
        in_ws = V.ws[0] < zm < V.ws[1]
        if b == 9:
            return 'paint'
        if b in (10, 11, 12, 13):
            if in_ws and R - ys < 0.3:
                return 'glass' if (zm > V.ws[0] + 0.03 and zm < V.ws[1] - 0.04) else 'trim'
            return 'paint'
        return 'paint'

    def half(self, z):
        k = self.X(z) / self.hw
        hw = self.hw * k
        yb = self.ybot
        ya = self.arch_y(z)
        xi = min(0.72, hw * 0.9)
        for (wz, r, track, w, st) in self.wheels:
            if abs(z - wz) < r + self.arch_gap:
                xi = min(track - w / 2 - 0.04, hw * 0.9)
        belt, wlo, whi, ys, R, ry = self.levels(z)
        ymid = min(belt - 0.01, max(0.62, ya + 0.06))
        pts = [(0.0, yb), (xi, yb), (xi, ya), (max(xi, hw - 0.03), ya), (hw, ya + 0.03), (hw, ymid), (hw, belt), (hw, wlo), (hw, whi), (hw, ys)]
        rx = self.rxy(R)[0] * ((0.4 + 0.6 * min(1.0, k)) if k < 1 else 1.0)
        for a in (30, 60, 90):
            r = math.radians(a)
            pts.append((hw - rx + rx * math.cos(r), ys + ry * math.sin(r) - (0.01 if a == 90 else 0)))
        pts.append((hw * 0.5, R + 0.01))
        pts.append((0.0, R + 0.02))
        return pts


def van_shell(V):
    zs = V.stations()
    halves = [V.half(z) for z in zs]
    nh = len(halves[0])
    rings = [full_ring(h, z) for z, h in zip(zs, halves)]
    n = len(rings[0])

    def mats(i, k):
        right = k < nh - 1
        b = k if right else n - 1 - k
        zm = (zs[i] + zs[i + 1]) / 2
        return V.mat(b, zm, right)

    g = geom.loft(rings, 'paint', closed=True, smooth=50, mats=mats)
    _orient_like(g, np.array([1.0, 0, 0]), lambda p: p.v.mean(axis=0)[0] > V.hw * 0.9 and abs(p.v.mean(axis=0)[2] - (V.zf + V.zr) / 2) < 0.6
                 and p.v.mean(axis=0)[1] > V.ybot + 0.9)
    return g, zs


def decal_side(V, side, y_c=1.4, h=2.6):
    zc = (V.zf + V.zr) / 2
    L = V.zf - V.zr
    return K.Canvas((side * V.hw, y_c, zc), (0, 0, -side * L), (0, h, 0), ppm=220), side * V.hw


def ambulance():
    V = Van()
    scheme = materials.Scheme(base=0xF3F3F1, second=0xD8281E, dirt=0.1, wear=0.1, seed=102, gloss=0.9)
    m = Model('ambulance', scheme, density=40, tex_width=512)
    m.chart_by_mat = True
    m.tl_dims = {}
    body = m.part('body')
    skin, zs = van_shell(V)
    body.add(skin)
    C = K.Caster(skin)
    g = Geo(45)
    hw = V.hw
    # Front: head lamps, grille, plate, bumper details.
    m.tl_dims['tl_head_sq'] = (0.36, 0.17)
    for sd in (-1, 1):
        g.extend(K.patch(C, (sd * (hw - 0.3), 0.8, V.zf), (0.36, 0, 0), (0, 0.17, 0), (0, 0, -1), 'tl_head_sq', na=3, nb=1, offset=0.012, back=1.0,
                         skirt=0.012, skirt_mat='chrome'))
        g.extend(K.patch(C, (sd * (hw - 0.12), 0.66, V.zf), (0.1, 0, 0), (0, 0.06, 0), (0, 0, -1), 'tl_amber', na=1, nb=1, offset=0.01, back=1.0,
                         skirt=0.01, skirt_mat='trim'))
    m.tl_dims['tl_grille_old'] = (0.7, 0.3)
    g.extend(K.patch(C, (0, 0.78, V.zf), (0.62, 0, 0), (0, 0.26, 0), (0, 0, -1), 'tl_grille', na=4, nb=2, offset=0.012, back=1.0, skirt=0.012, skirt_mat='chrome'))
    g.extend(K.patch(C, (0, 0.44, V.zf), (-0.52, 0, 0), (0, 0.112, 0), (0, 0, -1), 'plate', na=2, nb=1, offset=0.008, back=1.0, skirt=0.008, skirt_mat='trim'))
    # Tail: tall lamps, the plate, rear door windows (frosted), a step bumper.
    for sd in (-1, 1):
        g.extend(K.patch(C, (sd * (hw - 0.14), 1.0, V.zr), (0.16 * sd, 0, 0), (0, 0.6, 0), (0, 0, 1), 'tl_tail_old', na=1, nb=3, offset=0.01, back=1.0,
                         skirt=0.01, skirt_mat='trim'))
        g.extend(K.patch(C, (sd * 0.43, 1.85, V.zr), (0.56, 0, 0), (0, 0.55, 0), (0, 0, 1), 'tl_frit', na=3, nb=2, offset=0.008, back=1.0, skirt=0.01, skirt_mat='trim'))
    g.extend(K.patch(C, (0, 0.62, V.zr), (0.52, 0, 0), (0, 0.112, 0), (0, 0, 1), 'plate', na=2, nb=1, offset=0.008, back=1.0, skirt=0.008, skirt_mat='trim'))
    g.extend(K.hbox((-hw + 0.1, 0.38, V.zr - 0.1), (hw - 0.1, 0.56, V.zr + 0.02), 'tl_dark_chrome', r=0.03))
    # Mirrors (big van mirrors on arms at the A-pillars).
    for sd in (-1, 1):
        base = np.array([sd * (hw - 0.02), 1.62, 1.97])
        g.extend(geom.tube([base, base + [sd * 0.14, 0.06, 0.1], base + [sd * 0.24, 0.12, 0.12]], 0.014, 'trim', n=5))
        g.extend(K.hbox(base + [sd * 0.24 - 0.04, -0.1, 0.1], base + [sd * 0.24 + 0.04, 0.34, 0.2], 'trim', r=0.025))
        g.extend(geom.panel(base + [sd * 0.24, 0.12, 0.099], 0.07, 0.4, (1, 0, 0), (0, 1, 0), 'mirror_glass'))
    # Door handles and a wiper pair; a step at the sliding... (the side door of the box).
    for sd in (-1, 1):
        g.extend(K.hbox((sd * hw - 0.015 + 0.0, 1.14, 1.22), (sd * hw + 0.015, 1.18, 1.38), 'chrome', r=0.01))
    for x0 in (-0.85, -0.05):
        g.extend(K.wiper((x0, 1.36, 2.28), (x0 + 0.72, 1.4, 2.28 + 0.0), 'wiper'))
    # The light bar on the cab roof (lamps at +-0.63, y 2.42, z 1.3 as the game flashes them).
    g.extend(K.light_bar((0.0, 2.25 + 0.03, 1.3), 1.4, depth=0.3, h=0.15))
    # A roof vent on the box, a blue "star of life" is painted by decal.
    g.extend(K.hbox((-0.35, 2.74, -1.9), (0.35, 2.86, -1.3), 'steel', r=0.04))
    body.add(g)
    # Decals: red band round the box and cab, text, crosses.
    red = (216, 40, 30, 255)
    white = (245, 245, 245, 255)
    for sd in (-1, 1):
        cv, x = decal_side(V, sd)
        cv.rect((x, 1.08, V.zf - 0.35), (x, 0.84, V.zr + 0.1), red)
        cv.rect((x, 0.81, V.zf - 0.35), (x, 0.79, V.zr + 0.1), red)
        cv.text((x, 0.96, -1.1), 2.6, 0.2, 'ШВИДКА ДОПОМОГА', white[:3])
        # The cross on the box side.
        zc = -1.5
        cv.rect((x, 2.22, zc - 0.11), (x, 1.52, zc + 0.11), red)
        cv.rect((x, 1.99, zc - 0.34), (x, 1.75, zc + 0.34), red)
        cv.text((x, 1.38, 0.0), 0.5, 0.2, '103', red[:3])
        m.decals.append(cv.decal(depth=0.3, region=lambda P: (P[:, 2] < 2.4)))
    # Rear: a band across the doors.
    cv = K.Canvas((0, 1.4, V.zr), (V.hw * 2.2, 0, 0), (0, 2.6, 0), ppm=220)
    cv.rect((-hw, 1.08, V.zr), (hw, 0.86, V.zr), red)
    d = cv.decal(depth=0.4)
    d.n = np.array([0, 0, -1.0])
    m.decals.append(d)
    cv = K.Canvas((0, 0.9, V.zf), (-V.hw * 2.2, 0, 0), (0, 1.6, 0), ppm=220)
    cv.rect((-hw, 1.08, V.zf), (hw, 0.9, V.zf), red)
    d = cv.decal(depth=0.4)
    d.n = np.array([0, 0, 1.0])
    m.decals.append(d)
    # The inside: cab seats, dashboard, wheel, the patient compartment floor.
    inside = body.part('inside')
    inside.near = 24.0
    ig = Geo(40)
    fl = 0.68
    ig.extend(geom.box((-hw + 0.06, fl - 0.02, V.zr + 0.1), (hw - 0.06, fl, 2.3), 'tl_bus_floor', faces=('top',)))
    ig.extend(K.hbox((-hw + 0.1, fl, 2.0), (hw - 0.1, 1.2, 2.3), 'dash', r=0.05))
    ig.extend(K.hbox((-0.1, fl, 1.05), (0.1, fl + 0.3, 2.0), 'dash', r=0.03))
    for x in (-0.5, 0.5):
        ig.extend(K.seat(x, 1.05, 1.4, w=0.5, mat='seat', back_h=0.7))
    # The partition behind the cab and the stretcher.
    ig.extend(geom.box((-hw + 0.1, fl, 0.62), (hw - 0.1, 2.15, 0.66), 'tl_door_card', faces=('front', 'back')))
    ig.extend(K.hbox((-0.3, fl + 0.5, -2.4), (0.3, fl + 0.62, -0.4), 'white', r=0.02))
    ig.extend(K.hbox((hw - 0.6, fl, -2.5), (hw - 0.08, fl + 0.45, -0.7), 'tl_fabric_blue', r=0.03))
    inside.add(ig)
    # The steering wheel (turns with the steering) as the box model's interior puts it.
    piv = (-0.5, 1.6, 1.92)
    col = body.part('steering_column', piv, (-50.0, 0.0, 0.0))
    col.near = 24.0
    wp = col.part('steering_wheel', piv)
    wp.near = 24.0
    wp.add(K.steering_wheel_geo(piv, r=0.2, column=0.0))
    col.add(geom.cyl(np.asarray(piv) + [0, 0, 0.03], np.asarray(piv) + [0, 0, 0.4], 0.035, 'dash', n=6, caps=False))
    # Wheels.
    for idx, (wz, r, track, w, st) in enumerate(V.wheels):
        for sd, tag in ((-1, 'l'), (1, 'r')):
            c = (sd * track, r, wz)
            p = body.part('wheel_%d_%s' % (idx + 1, tag), c)
            p.add(K.car_wheel(c, sd, r, w, 'steel', n=22, mat_rim='tl_steel_wheel'))
            p.spin = r
            p.steer = bool(st)
    return m


# ---------------------------------------------------------------------------------------------------------------
# The fire engine


class Cab(Van):
    """The cab-over cab of the fire engine: z 1.7 .. 3.6, floor y 1.0, roof 3.0."""
    zf = 3.6
    zr = 1.7
    hw = 1.25
    ybot = 0.98
    nose_len = 0.3
    tail_len = 0.12
    nose_p = 5.0
    tail_p = 6.0
    wheels = [(2.75, 0.55, 0.98, 0.42, True)]
    arch_gap = 0.08
    ROOF = [(3.6, 3.0), (1.7, 3.0)]
    cab_win = (2.12, 3.28)
    ws = (9.0, 9.0)

    def rxy(self, R):
        return (0.14, 0.14)

    def levels(self, z):
        belt = 1.82
        return belt, 1.9, 2.82, 2.86, 3.0, 0.14

    def stations(self):
        zs = set()
        z0f = self.zf - self.nose_len
        z0r = self.zr + self.tail_len
        for k in range(7):
            th = math.pi / 2 * k / 6
            zs.add(z0f + self.nose_len * math.sin(th))
            zs.add(z0r - self.tail_len * math.sin(th))
        zs.add(z0f + self.nose_len * 0.93)
        for (wz, r, track, w, st) in self.wheels:
            R = r + self.arch_gap
            for k in range(-6, 7):
                zs.add(wz + R * math.sin(k / 6 * math.pi / 2))
        for z in (3.3, 3.28, 3.25, 3.0, 2.7, 2.4, 2.15, 2.12, 2.09, 1.95, 1.8):
            zs.add(z)
        zs = sorted(z for z in zs if self.zr <= z <= self.zf)
        out = [zs[0]]
        for z in zs[1:]:
            if z - out[-1] > 0.006:
                out.append(z)
        out[-1] = zs[-1]
        return sorted(set(round(z, 4) for z in out))

    def mat(self, b, zm, right):
        belt, wlo, whi, ys, R, ry = self.levels(zm)
        if b == 4:
            return 'plastic' if zm > self.zf - 0.12 else 'paint'
        if b in (7, 8) and zm > self.zf - self.nose_len * 0.7:
            # The windscreen: glass across the front (the flat of the nose), a pillar at each corner.
            return 'glass' if b == 7 else 'trim'
        return Van.mat(self, b, zm, right)


def fire_truck():
    scheme = materials.Scheme(base=0xC4161C, second=0xF1EFE6, dirt=0.14, wear=0.1, seed=103, gloss=0.9)
    m = Model('fire_truck', scheme, density=36, tex_width=512)
    m.chart_by_mat = True
    m.tl_dims = {}
    body = m.part('body')
    V = Cab()
    skin, zs = van_shell(V)
    body.add(skin)
    Cc = K.Caster(skin)
    g = Geo(45)
    hw = V.hw
    # Equipment body.
    zb0, zb1 = -3.98, 1.62
    eq = K.hbox((-hw, 1.12, zb0), (hw, 2.85, zb1), 'paint', r=0.07, segs=2)
    body.add(eq)
    Ce = K.Caster(eq)
    # Cab details: grille, lamps, bumper, plate, steps, mirrors, roof gear.
    m.tl_dims['tl_head_sq'] = (0.4, 0.22)
    m.tl_dims['tl_grille'] = (1.1, 0.5)
    for sd in (-1, 1):
        g.extend(K.patch(Cc, (sd * (hw - 0.3), 1.28, V.zf), (0.4, 0, 0), (0, 0.22, 0), (0, 0, -1), 'tl_head_sq', na=3, nb=1, offset=0.012, back=1.0,
                         skirt=0.012, skirt_mat='chrome'))
        g.extend(K.patch(Cc, (sd * (hw - 0.3), 1.58, V.zf), (0.3, 0, 0), (0, 0.1, 0), (0, 0, -1), 'tl_amber', na=1, nb=1, offset=0.01, back=1.0,
                         skirt=0.01, skirt_mat='trim'))
    g.extend(K.patch(Cc, (0, 1.4, V.zf), (1.0, 0, 0), (0, 0.46, 0), (0, 0, -1), 'tl_grille', na=6, nb=2, offset=0.012, back=1.0, skirt=0.012, skirt_mat='chrome'))
    g.extend(K.patch(Cc, (0, 0.98, V.zf), (0.52, 0, 0), (0, 0.112, 0), (0, 0, -1), 'plate', na=2, nb=1, offset=0.008, back=1.0, skirt=0.008, skirt_mat='trim'))
    g.extend(K.hbox((-hw + 0.08, 0.72, 3.4), (hw - 0.08, 1.05, 3.68), 'tl_dark_chrome', r=0.05))
    for sd in (-1, 1):
        g.extend(K.hbox((sd * (hw - 0.14) - 0.1, 0.8, 3.45), (sd * (hw - 0.14) + 0.1, 0.9, 3.5), 'tl_amber', r=0.01))
        # Cab steps.
        for z0, y0 in ((2.45, 0.62), (2.45, 0.35)):
            pass
        g.extend(K.hbox((sd * hw - (0.3 if sd > 0 else -0.3) * 1.0 - 0.0, 0.44, 2.0), (sd * hw + (0.0 if sd > 0 else 0.0), 0.5, 2.9), 'tl_ladder', r=0.01)
                 if False else Geo(1))
        base = np.array([sd * (hw - 0.02), 2.55, 3.38])
        g.extend(geom.tube([base, base + [sd * 0.22, 0.05, 0.1], base + [sd * 0.34, 0.1, 0.13]], 0.016, 'trim', n=5))
        g.extend(K.hbox(base + [sd * 0.34 - 0.05, -0.2, 0.1], base + [sd * 0.34 + 0.05, 0.4, 0.22], 'trim', r=0.03))
        g.extend(geom.panel(base + [sd * 0.34, 0.1, 0.0995 + 0.0], 0.08, 0.55, (1, 0, 0), (0, 1, 0), 'mirror_glass'))
        # Door handles.
        g.extend(K.hbox((sd * hw - 0.02, 1.7, 2.3), (sd * hw + 0.02, 1.76, 2.6), 'chrome', r=0.01))
    # Roof: the light bar (lamps at +-0.78, y 3.18, z 2.4), two air horns, a spotlight.
    g.extend(K.light_bar((0.0, 3.0 + 0.04, 2.4), 1.9, depth=0.34, h=0.2))
    for sx in (-0.5, 0.5):
        g.extend(geom.cyl((sx, 3.0, 3.0), (sx, 3.14, 3.0), 0.06, 'chrome', n=8, r2=0.08))
    # The equipment body's furniture: lockers with roller shutters on both sides, the white band, the pump panel.
    m.tl_dims['tl_shutter'] = (1.0, 1.0)
    lockers = [(-3.2, 1.3), (-1.75, 1.45), (-0.25, 1.35)]
    for sd in (-1, 1):
        for zc, w in lockers:
            g.extend(K.patch(Ce, (sd * hw, 1.88, zc), (0, 0, w * sd * -1), (0, 1.1, 0), (-sd, 0, 0), 'tl_shutter', na=1, nb=1, offset=0.012, back=1.0,
                             skirt=0.016, skirt_mat='chrome') if False else Geo(1))
    body.add(g)
    # Lockers as patches (U runs along the car's z, V up; D into the side).
    lg = Geo(45)
    for sd in (-1, 1):
        for zc, w in lockers:
            lg.extend(K.patch(Ce, (sd * (hw + 0.4), 1.8, zc), (0, 0, w), (0, 1.15, 0), (-sd, 0, 0), 'tl_shutter', na=4, nb=2, offset=0.014, back=0.0,
                              skirt=0.02, skirt_mat='chrome'))
    # The rear of the body: hazard stripes, lamps, plate, a step.
    lg.extend(K.patch(Ce, (0, 1.75, zb0), (1.8, 0, 0), (0, 1.1, 0), (0, 0, 1), 'tl_hazard', na=4, nb=2, offset=0.01, back=0.6, skirt=0.014, skirt_mat='chrome'))
    for sd in (-1, 1):
        lg.extend(K.patch(Ce, (sd * (hw - 0.2), 2.5, zb0), (0.2 * sd, 0, 0), (0, 0.3, 0), (0, 0, 1), 'tl_tail_old', na=1, nb=1, offset=0.01, back=0.6,
                          skirt=0.01, skirt_mat='trim'))
    lg.extend(K.patch(Ce, (0, 2.35, zb0), (0.5, 0, 0), (0, 0.112, 0), (0, 0, 1), 'plate', na=2, nb=1, offset=0.008, back=0.6, skirt=0.008, skirt_mat='trim'))
    body.add(lg)
    # The ladder on top (two rails, rungs), a hose reel and hatches.
    tg = Geo(40)
    for x in (-0.45, 0.45):
        tg.extend(geom.tube([(x, 2.96, zb0 + 0.2), (x, 2.96, 1.55)], 0.03, 'tl_ladder', n=6))
        for z in (zb0 + 0.4, -1.0, 0.8):
            tg.extend(K.hbox((x - 0.04, 2.85, z - 0.05), (x + 0.04, 2.95, z + 0.05), 'metal_dark', r=0.01))
    z = zb0 + 0.35
    while z < 1.5:
        tg.extend(geom.cyl((-0.45, 2.96, z), (0.45, 2.96, z), 0.016, 'tl_ladder', n=5, caps=False))
        z += 0.3
    for x in (-0.45, 0.45):
        tg.extend(geom.tube([(x, 3.2, zb0 + 0.2), (x, 3.2, -0.5)], 0.025, 'tl_ladder', n=5))
    z = zb0 + 0.3
    while z < -0.5:
        tg.extend(geom.cyl((-0.45, 3.2, z), (0.45, 3.2, z), 0.014, 'tl_ladder', n=5, caps=False))
        z += 0.32
    tg.extend(K.hbox((-0.7, 2.85, -0.45), (0.7, 3.02, 0.35), 'tl_red_steel', r=0.04))
    body.add(tg)
    # White band along the sides of the body and the cab.
    white = (245, 245, 242, 255)
    for sd in (-1, 1):
        zc = (V.zf + zb0) / 2
        L = V.zf - zb0
        cv = K.Canvas((sd * hw, 2.0, zc), (0, 0, -sd * L), (0, 2.4, 0), ppm=160)
        x = sd * hw
        cv.rect((x, 2.72, V.zf - 0.2), (x, 2.55, zb0 + 0.05), white)
        cv.rect((x, 1.85, V.zf - 0.3), (x, 1.72, 2.05), white)
        cv.text((x, 2.0, 2.6), 1.0, 0.18, 'ДСНС', white[:3])
        cv.text((x, 2.64, -0.9), 3.4, 0.14, 'ПОЖЕЖНА ОХОРОНА', (178, 20, 24))
        m.decals.append(cv.decal(depth=0.3, region=lambda P: (P[:, 1] > 1.6)))
    # Chassis: frame rails, the engine and tanks, axles, the rear step.
    ch = Geo(40)
    for sd in (-1, 1):
        ch.extend(K.hbox((sd * 0.45 - 0.08, 0.72, -3.85), (sd * 0.45 + 0.08, 1.02, 3.2), 'metal_dark', r=0.01))
        ch.extend(K.hbox((sd * 0.95 - 0.22, 0.86, -1.9), (sd * 0.95 + 0.22, 1.1, -0.1), 'tl_ladder', r=0.04))  # side lockers / tank straps
    ch.extend(K.hbox((-0.3, 0.55, 2.0), (0.3, 0.95, 3.1), 'metal_dark', r=0.06))
    ch.extend(K.hbox((-0.45, 0.6, -3.2), (0.45, 1.1, -2.9), 'metal_dark', r=0.03))
    ch.extend(K.hbox((-1.1, 0.72, -4.1), (1.1, 1.02, -3.9), 'tl_dark_chrome', r=0.03))
    for wz in (2.75, -0.9, -2.3):
        ch.extend(geom.cyl((-0.98, 0.55, wz), (0.98, 0.55, wz), 0.07, 'metal_dark', n=8))
    body.add(ch)
    # Mud guards behind each axle set.
    mg = Geo(40)
    for sd in (-1, 1):
        for wz in (-0.9, -2.3):
            mg.extend(K.hbox((sd * 0.98 - 0.26, 0.15, wz - 0.3), (sd * 0.98 + 0.26, 0.2, wz - 0.28), 'rubber', r=0.005))
    body.add(mg)
    # The cab's inside: floor, seats, dashboard, the wheel.
    inside = body.part('inside')
    inside.near = 24.0
    ig = Geo(40)
    ig.extend(geom.box((-hw + 0.08, 1.0, V.zr + 0.1), (hw - 0.08, 1.04, 3.5), 'tl_bus_floor', faces=('top',)))
    ig.extend(K.hbox((-hw + 0.12, 1.0, 3.05), (hw - 0.12, 1.84, 3.48), 'dash', r=0.05))
    for x, z in ((-0.5, 2.55), (0.5, 2.55), (-0.5, 1.95), (0.5, 1.95)):
        ig.extend(K.seat(x, 1.55 if z > 2.0 else 1.55, z, w=0.5, mat='seat', back_h=0.7))
    wall = Geo(40)
    for sd in (-1, 1):
        xw = sd * (hw - 0.06)
        wall.add(np.array([(xw, 1.04, 2.0), (xw, 1.04, 3.45), (xw, 1.9, 3.45), (xw, 1.9, 2.0)]), 'tl_door_card')
        wall.add(np.array([(xw, 2.82, 2.0), (xw, 2.82, 3.45), (xw, 2.9, 3.45), (xw, 2.9, 2.0)]), 'tl_door_card')
    K.orient(wall, (0, 2.0, 2.7), outward=False)
    ig.extend(wall)
    ig.extend(geom.box((-hw + 0.08, 1.04, V.zr + 0.02), (hw - 0.08, 2.9, V.zr + 0.06), 'tl_door_card', faces=('front',)))
    hl = geom.panel((0, 2.9, 2.6), 2.3, 1.7, (1, 0, 0), (0, 0, 1), 'tl_headliner')
    K.orient(hl, (0, 2.0, 2.6), outward=False)
    ig.extend(hl)
    inside.add(ig)
    piv = (-0.5, 2.03, 2.82)
    col = body.part('steering_column', piv, (-55.0, 0.0, 0.0))
    col.near = 24.0
    wp = col.part('steering_wheel', piv)
    wp.near = 24.0
    wp.add(K.steering_wheel_geo(piv, r=0.22, column=0.0))
    col.add(geom.cyl(np.asarray(piv) + [0, 0, 0.03], np.asarray(piv) + [0, 0, 0.45], 0.04, 'dash', n=6, caps=False))
    # Wheels: three axles, the first steers; the rear pair twinned (a second tyre on the inside).
    for idx, wz in enumerate((2.75, -0.9, -2.3)):
        for sd, tag in ((-1, 'l'), (1, 'r')):
            c = (sd * 0.98, 0.55, wz)
            p = body.part('wheel_%d_%s' % (idx + 1, tag), c)
            p.add(K.car_wheel(c, sd, 0.55, 0.4, 'steel', n=24, mat_rim='tl_steel_wheel'))
            p.spin = 0.55
            p.steer = idx == 0
    return m
