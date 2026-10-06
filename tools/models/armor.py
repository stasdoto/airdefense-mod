"""Tanks, infantry fighting vehicles, armoured personnel carriers, armoured cars and boats (stage R5). Metres."""
from boxgen import Model
from kit import axles, tracks, lights, cabover
from airdefense import barrel, missile, canister


def glacis(body, z0, z1, y0, y1, half, paint, steps=4):
    """A sloped front plate from (z0, y1) down to (z1, y0) built from steps."""
    for i in range(steps):
        t0 = i / steps
        t1 = (i + 1) / steps
        za = z0 + (z1 - z0) * t0
        zb = z0 + (z1 - z0) * t1
        yb = y1 - (y1 - y0) * t1
        body.box((-half, y0, za), (half, yb + (y1 - y0) / steps, zb), paint)


def tank_hull(m, body, length, width, top, hull_top, paint, wheels=6, r=0.36, track_w=0.58):
    """Tracked hull centred on z=0; returns (front z, back z)."""
    z0, z1 = -length / 2, length / 2
    tracks(m, body, z0, z1, top, width, track_w, wheels, r, paint)
    hw = width / 2
    inner = hw - track_w
    body.box((-inner, 0.4, z0 + 0.2), (inner, top + 0.05, z1 - 0.3), paint)
    body.box((-hw, top + 0.05, z0), (hw, hull_top, z1 - 1.2), paint)
    glacis(body, z1 - 1.2, z1 + 0.05, top - 0.25, hull_top, hw - 0.05, paint)
    lights(body, z1 - 0.3, top + 0.15, hw - 0.4)
    lights(body, z0, top + 0.2, hw - 0.3, front=False)
    return z1, z0


def turret_dome(part, cx, cz, y0, w, l, h, paint, layers=3):
    """A rounded cast turret from stacked boxes."""
    for i in range(layers):
        k = 1 - i * 0.16
        part.box((cx - w / 2 * k, y0 + h * i / layers, cz - l / 2 * k), (cx + w / 2 * k, y0 + h * (i + 1) / layers, cz + l / 2 * k * 0.95), paint)


def era(part, x0, x1, y0, y1, z, paint, n=4):
    """Explosive reactive armour bricks on a front face."""
    w = (x1 - x0) / n
    for i in range(n):
        part.box((x0 + i * w + 0.02, y0, z), (x0 + (i + 1) * w - 0.02, y1, z + 0.12), paint)


def gun(m, tur, pivot, length, dia, paint='dgrey', mantlet=0.6, name='gun', deploy=0):
    el = tur.part(name, pivot)
    m.set_elevator(el, deploy=deploy)
    px, py, pz = pivot
    el.box((px - mantlet / 2, py - mantlet / 3, pz - 0.2), (px + mantlet / 2, py + mantlet / 3, pz + 0.35), paint)
    barrel(el, px, py, pz + 0.35, length, dia, paint)
    # Fume extractor.
    el.box((px - dia * 0.9, py - dia * 0.9, pz + 0.35 + length * 0.45), (px + dia * 0.9, py + dia * 0.9, pz + 0.35 + length * 0.6), paint)
    m.rail('barrel', (px, py, pz + 0.35 + length))
    return el


# ------------------------------------------------------------------------------------------------------------------
# Tanks


def t72():
    m = Model('t72', paint='rgreen', seed=2001)
    m.width = 3.6
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    zf, zb = tank_hull(m, body, 6.9, 3.6, 0.95, 1.55, 'rgreen')
    era(body, -1.6, 1.6, 1.2, 1.5, zf - 1.0, 'rgreen', n=6)
    # Fuel drums at the back.
    for s in (-1, 1):
        body.box((s * 0.9 - 0.3, 1.3, zb - 0.7), (s * 0.9 + 0.3, 1.9, zb - 0.1), 'dgrey')
    tz = -0.3
    tur = m.part('turret', (0, 1.55, tz))
    m.set_turret(tur)
    turret_dome(tur, 0, tz, 1.55, 2.3, 2.6, 0.8, 'rgreen')
    # Kontakt-5 wedges and the commander's cupola, smoke dischargers.
    for s in (-1, 1):
        tur.box((s * 0.65 - 0.45, 1.6, tz + 1.1), (s * 0.65 + 0.45, 2.15, tz + 1.55), 'rgreen')
    tur.box((0.35, 2.35, tz - 0.4), (0.85, 2.55, tz + 0.1), 'rgreen')
    tur.box((-0.75, 2.35, tz - 0.3), (-0.35, 2.5, tz + 0.1), 'rgreen')
    tur.box((0.45, 2.55, tz - 0.25), (0.95, 2.62, tz - 0.2), 'dark')
    gun(m, tur, (0, 1.95, tz + 1.4), 5.2, 0.16)
    m.seat('driver', 0.0, 1.0, 2.3)
    m.seat('gunner', 0.6, 2.2, tz - 0.1)
    return m


def t90():
    m = Model('t90', paint='rgreen', seed=2002)
    m.width = 3.78
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    zf, zb = tank_hull(m, body, 6.9, 3.78, 0.95, 1.55, 'rgreen')
    era(body, -1.6, 1.6, 1.2, 1.5, zf - 1.0, 'rgreen', n=6)
    tz = -0.3
    tur = m.part('turret', (0, 1.55, tz))
    m.set_turret(tur)
    # Welded angular turret with Relikt.
    tur.box((-1.2, 1.55, tz - 1.4), (1.2, 2.3, tz + 1.0), 'rgreen')
    for s in (-1, 1):
        tur.box((s * 0.7 - 0.5, 1.6, tz + 1.0), (s * 0.7 + 0.5, 2.3, tz + 1.55), 'rgreen')
    tur.box((-1.0, 1.65, tz - 2.0), (1.0, 2.2, tz - 1.4), 'rgreen')
    # The anti-drone "cope cage" over the turret.
    for x in (-1.25, 1.25):
        for z in (tz - 1.6, tz + 0.9):
            tur.box((x - 0.04, 2.3, z - 0.04), (x + 0.04, 3.05, z + 0.04), 'dgrey')
    tur.box((-1.3, 3.05, tz - 1.7), (1.3, 3.1, tz + 1.0), 'mesh_dark')
    tur.box((0.3, 2.3, tz - 0.5), (0.8, 2.6, tz)).box((-0.8, 2.3, tz - 0.4), (-0.4, 2.45, tz), 'rgreen')
    gun(m, tur, (0, 1.95, tz + 1.5), 5.2, 0.16)
    m.seat('driver', 0.0, 1.0, 2.3)
    m.seat('gunner', 0.6, 2.2, tz - 0.1)
    return m


def leopard2():
    m = Model('leopard2', paint='camo', seed=2003)
    m.width = 3.75
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    zf, zb = tank_hull(m, body, 7.7, 3.75, 1.0, 1.75, 'camo', wheels=7)
    tz = -0.6
    tur = m.part('turret', (0, 1.75, tz))
    m.set_turret(tur)
    tur.box((-1.75, 1.75, tz - 2.2), (1.75, 2.55, tz + 1.1), 'camo')
    # The wedge armour of the 2A6.
    for s in (-1, 1):
        tur.box((s * 0.95 - 0.75, 1.8, tz + 1.1), (s * 0.95 + 0.75, 2.5, tz + 1.7), 'camo')
        tur.box((s * 0.95 - 0.6, 1.9, tz + 1.7), (s * 0.95 + 0.6, 2.4, tz + 2.1), 'camo')
    tur.box((0.4, 2.55, tz - 0.6), (0.9, 2.95, tz - 0.1), 'camo', sides={'front': 'glass'})
    tur.box((-0.9, 2.55, tz - 0.8), (-0.4, 2.7, tz - 0.3), 'camo')
    tur.box((-1.6, 1.9, tz - 2.6), (1.6, 2.4, tz - 2.2), 'camo')
    gun(m, tur, (0, 2.15, tz + 1.8), 6.0, 0.15)
    m.seat('driver', -0.6, 1.05, 2.6)
    m.seat('gunner', 0.6, 2.4, tz - 0.4)
    return m


def abrams():
    m = Model('abrams', paint='nato', seed=2004)
    m.width = 3.66
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    zf, zb = tank_hull(m, body, 7.9, 3.66, 0.95, 1.55, 'nato', wheels=7)
    tz = -0.8
    tur = m.part('turret', (0, 1.55, tz))
    m.set_turret(tur)
    tur.box((-1.75, 1.55, tz - 2.0), (1.75, 2.45, tz + 1.3), 'nato')
    for s in (-1, 1):
        tur.box((s * 1.0 - 0.75, 1.6, tz + 1.3), (s * 1.0 + 0.75, 2.35, tz + 2.0), 'nato')
    tur.box((-1.7, 1.65, tz - 2.8), (1.7, 2.4, tz - 2.0), 'nato')
    tur.box((0.3, 2.45, tz - 0.4), (0.95, 2.7, tz + 0.2), 'nato')
    barrel(tur, 0.62, 2.85, tz + 0.2, 1.0, 0.05)
    tur.box((-0.9, 2.45, tz - 0.3), (-0.4, 2.62, tz + 0.2), 'nato')
    gun(m, tur, (0, 2.0, tz + 2.0), 5.0, 0.15)
    m.seat('driver', 0.0, 1.0, 2.9)
    m.seat('gunner', 0.6, 2.3, tz - 0.4)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Infantry fighting vehicles and armoured personnel carriers


def bmp2():
    m = Model('bmp2', paint='ugreen', seed=2005)
    m.width = 3.15
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.35, 3.35
    tracks(m, body, z0, z1, 0.85, 3.15, 0.48, 6, 0.32, 'ugreen')
    body.box((-1.1, 0.4, z0 + 0.2), (1.1, 0.9, z1 - 0.3), 'ugreen')
    body.box((-1.57, 0.9, z0), (1.57, 1.75, z1 - 1.6), 'ugreen')
    glacis(body, z1 - 1.6, z1 + 0.05, 0.6, 1.75, 1.5, 'ugreen', steps=5)
    # Rear doors (fuel tanks).
    for s in (-1, 1):
        body.box((s * 0.6 - 0.35, 0.95, z0 - 0.08), (s * 0.6 + 0.35, 1.6, z0), 'ugreen')
    lights(body, z1 - 0.6, 1.05, 1.2)
    tz = -0.2
    tur = m.part('turret', (0, 1.75, tz))
    m.set_turret(tur)
    turret_dome(tur, 0, tz, 1.75, 1.6, 1.8, 0.55, 'ugreen', layers=2)
    tur.box((0.25, 2.3, tz - 0.4), (0.7, 2.45, tz + 0.1), 'ugreen')
    el = gun(m, tur, (0, 2.05, tz + 0.85), 2.9, 0.08, mantlet=0.4)
    # Konkurs ATGM tube on the roof.
    canister(el, 0.0, 2.55, tz + 0.2, 1.2, 0.16, 'ugreen')
    m.seat('driver', -0.7, 1.15, 2.3)
    m.seat('gunner', 0.0, 2.1, tz - 0.3)
    return m


def bradley():
    m = Model('bradley', paint='tan', seed=2006)
    m.width = 3.6
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.28, 3.28
    tracks(m, body, z0, z1, 0.95, 3.6, 0.55, 6, 0.35, 'tan')
    body.box((-1.25, 0.45, z0 + 0.2), (1.25, 1.0, z1 - 0.3), 'tan')
    body.box((-1.8, 1.0, z0), (1.8, 2.15, z1 - 1.0), 'tan')
    glacis(body, z1 - 1.0, z1 + 0.05, 0.7, 2.15, 1.75, 'tan', steps=4)
    body.box((-0.7, 0.9, z0 - 0.1), (0.7, 2.0, z0), 'tan')
    lights(body, z1 - 0.2, 1.1, 1.35)
    tz = -0.3
    tur = m.part('turret', (0.35, 2.15, tz))
    m.set_turret(tur)
    tur.box((-0.6, 2.15, tz - 1.1), (1.3, 2.85, tz + 0.9), 'tan')
    # TOW launcher box on the left of the turret.
    tur.box((-1.05, 2.3, tz - 0.6), (-0.6, 2.75, tz + 0.7), 'tan', sides={'front': 'dark'})
    tur.box((0.6, 2.85, tz - 0.2), (1.1, 3.1, tz + 0.2), 'tan', sides={'front': 'glass'})
    gun(m, tur, (0.35, 2.45, tz + 1.0), 2.4, 0.07, mantlet=0.4)
    m.seat('driver', -0.9, 1.4, 2.0)
    m.seat('gunner', 0.35, 2.5, tz - 0.4)
    return m


def btr82():
    m = Model('btr82', paint='rgreen', seed=2007)
    m.width = 2.9
    m.wheelbase = 4.4
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.8, 3.85
    body.box((-1.45, 0.55, z0), (1.45, 1.95, z1 - 1.3), 'rgreen')
    glacis(body, z1 - 1.3, z1, 0.6, 1.95, 1.35, 'rgreen', steps=4)
    body.box((-1.2, 1.95, -2.5), (1.2, 2.15, 1.3), 'rgreen')
    body.box((-1.1, 1.55, z1 - 1.05), (1.1, 1.75, z1 - 0.95), 'glass')
    lights(body, z1 - 0.4, 1.2, 1.0)
    lights(body, z0, 1.3, 1.0, front=False)
    for s in (-1, 1):
        body.box((s * 1.45 - (0.0 if s > 0 else 0.05), 1.0, -0.2), (s * 1.45 + (0.05 if s > 0 else 0.0), 1.7, 0.6), 'dark')
    tz = 0.6
    tur = m.part('turret', (0, 2.15, tz))
    m.set_turret(tur)
    turret_dome(tur, 0, tz, 2.15, 1.4, 1.5, 0.55, 'rgreen', layers=2)
    gun(m, tur, (0, 2.45, tz + 0.75), 2.6, 0.08, mantlet=0.35)
    axles(m, [2.7, 1.3, -0.7, -2.1], 0.6, 1.15, width=0.4, steer=2)
    m.seat('driver', -0.5, 1.45, 2.5)
    m.seat('gunner', 0.0, 2.3, tz - 0.2)
    return m


def btr4():
    m = Model('btr4', paint='ugreen', seed=2008)
    m.width = 2.94
    m.wheelbase = 4.5
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.85, 3.9
    body.box((-1.47, 0.6, z0), (1.47, 2.1, z1 - 0.9), 'ugreen')
    # Crew cab in front with windows.
    body.box((-1.4, 1.3, z1 - 0.9), (1.4, 2.1, z1 - 0.2), 'ugreen')
    body.box((-1.4, 0.6, z1 - 0.9), (1.4, 1.3, z1), 'ugreen')
    body.box((-1.2, 1.5, z1 - 0.2), (-0.1, 1.95, z1 - 0.17), 'glass')
    body.box((0.1, 1.5, z1 - 0.2), (1.2, 1.95, z1 - 0.17), 'glass')
    lights(body, z1, 0.9, 1.1)
    lights(body, z0, 1.3, 1.1, front=False)
    body.box((-0.6, 0.8, z0 - 0.08), (0.6, 1.9, z0), 'dark')
    tz = -0.6
    tur = m.part('turret', (0, 2.1, tz))
    m.set_turret(tur)
    tur.box((-0.85, 2.1, tz - 0.9), (0.85, 2.7, tz + 0.8), 'ugreen')
    canister(tur, -1.05, 2.45, tz - 0.4, 1.3, 0.16, 'ugreen')
    tur.box((0.3, 2.7, tz - 0.3), (0.7, 2.95, tz + 0.1), 'ugreen', sides={'front': 'glass'})
    gun(m, tur, (0.15, 2.4, tz + 0.85), 2.6, 0.08, mantlet=0.35)
    axles(m, [2.9, 1.5, -0.8, -2.2], 0.62, 1.15, width=0.42, steer=2)
    m.seat('driver', -0.6, 1.5, 2.9)
    m.seat('gunner', 0.0, 2.3, tz - 0.3)
    return m


def m113():
    m = Model('m113', paint='nato', seed=2009)
    m.width = 2.69
    m.tracked = True
    m.camera = 9
    body = m.part('body')
    z0, z1 = -2.43, 2.43
    tracks(m, body, z0, z1, 0.75, 2.69, 0.38, 5, 0.3, 'nato')
    body.box((-1.33, 0.4, z0), (1.33, 1.95, z1 - 0.7), 'nato')
    glacis(body, z1 - 0.7, z1, 0.5, 1.95, 1.3, 'nato', steps=3)
    body.box((-0.85, 0.5, z0 - 0.06), (0.85, 1.8, z0), 'nato')
    lights(body, z1 - 0.1, 0.9, 0.95)
    tz = 0.3
    tur = m.part('turret', (0.3, 1.95, tz))
    m.set_turret(tur)
    tur.box((-0.1, 1.95, tz - 0.4), (0.7, 2.25, tz + 0.4), 'nato')
    tur.box((-0.05, 2.25, tz + 0.3), (0.65, 2.6, tz + 0.36), 'nato')
    gun(m, tur, (0.3, 2.45, tz + 0.3), 1.2, 0.05, mantlet=0.2)
    m.seat('driver', -0.6, 1.25, 1.6)
    m.seat('gunner', 0.3, 2.0, tz - 0.3)
    return m


def maxxpro():
    m = Model('maxxpro', paint='tan', seed=2010)
    m.width = 2.6
    m.wheelbase = 3.9
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.2, 3.2
    # V-hull and the armoured box body.
    body.box((-0.9, 0.55, z0 + 0.3), (0.9, 1.0, z1 - 0.5), 'dark')
    body.box((-1.3, 1.0, z0), (1.3, 2.9, z1 - 1.6), 'tan')
    body.box((-1.2, 1.0, z1 - 1.6), (1.2, 1.9, z1), 'tan')
    body.box((-1.2, 0.8, z1 - 0.05), (1.2, 1.3, z1 + 0.1), 'dark')
    body.box((-1.15, 2.0, z1 - 1.62), (-0.05, 2.7, z1 - 1.58), 'glass')
    body.box((0.05, 2.0, z1 - 1.62), (1.15, 2.7, z1 - 1.58), 'glass')
    for s in (-1, 1):
        body.box((s * 1.3 - (0.0 if s > 0 else 0.03), 1.9, -0.8), (s * 1.3 + (0.03 if s > 0 else 0.0), 2.5, 1.4), 'glass')
    lights(body, z1 + 0.1, 1.4, 0.95)
    lights(body, z0, 1.3, 1.0, front=False)
    tz = -0.6
    tur = m.part('turret', (0, 2.9, tz))
    m.set_turret(tur)
    tur.box((-0.75, 2.9, tz - 0.75), (0.75, 3.05, tz + 0.75), 'tan')
    tur.box((-0.6, 3.05, tz + 0.45), (0.6, 3.5, tz + 0.55), 'tan')
    gun(m, tur, (0, 3.3, tz + 0.55), 1.2, 0.05, mantlet=0.2)
    axles(m, [2.0, -1.9], 0.6, 1.0, width=0.42, steer=1)
    m.seat('driver', -0.5, 1.6, 1.8)
    m.seat('gunner', 0.0, 2.6, tz - 0.4)
    return m


def kozak():
    """Kozak-2: Ukrainian armoured car (BBM)."""
    m = Model('kozak', paint='ugreen', seed=2011)
    m.width = 2.5
    m.wheelbase = 3.6
    m.camera = 9
    body = m.part('body')
    z0, z1 = -2.9, 2.9
    body.box((-1.2, 0.6, z0), (1.2, 2.45, z1 - 1.5), 'ugreen')
    body.box((-1.15, 0.6, z1 - 1.5), (1.15, 1.6, z1), 'ugreen')
    body.box((-1.05, 1.75, z1 - 1.52), (-0.05, 2.3, z1 - 1.48), 'glass')
    body.box((0.05, 1.75, z1 - 1.52), (1.05, 2.3, z1 - 1.48), 'glass')
    for s in (-1, 1):
        body.box((s * 1.2 - (0.0 if s > 0 else 0.03), 1.75, -0.6), (s * 1.2 + (0.03 if s > 0 else 0.0), 2.2, 0.9), 'glass')
    body.box((-1.15, 0.7, z1), (1.15, 1.1, z1 + 0.08), 'dark')
    lights(body, z1 + 0.08, 1.2, 0.85)
    lights(body, z0, 1.3, 0.9, front=False)
    tz = -0.8
    tur = m.part('turret', (0, 2.45, tz))
    m.set_turret(tur)
    tur.box((-0.6, 2.45, tz - 0.6), (0.6, 2.6, tz + 0.6), 'ugreen')
    tur.box((-0.5, 2.6, tz + 0.35), (0.5, 3.0, tz + 0.45), 'ugreen')
    gun(m, tur, (0, 2.85, tz + 0.45), 1.2, 0.05, mantlet=0.2)
    axles(m, [1.8, -1.8], 0.55, 0.95, width=0.4, steer=1)
    m.seat('driver', -0.5, 1.4, 1.0)
    m.seat('gunner', 0.0, 2.2, tz - 0.3)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Boats (they float: y = 0 is the waterline)


def boat_hull(body, length, beam, freeboard, paint, bow=0.35):
    """Hull from z = -length/2 to +length/2, the bow narrowing in steps. Draft 0.6 below the water."""
    z0, z1 = -length / 2, length / 2
    hb = beam / 2
    nb = int(round(length * bow / 1.0))
    body.box((-hb * 0.75, -0.6, z0 + 0.3), (hb * 0.75, 0.0, z1 - length * bow), 'dgrey')
    body.box((-hb, 0.0, z0), (hb, freeboard, z1 - length * bow), paint)
    steps = 5
    zs = z1 - length * bow
    for i in range(steps):
        k = 1 - (i + 1) / (steps + 1)
        za = zs + length * bow * i / steps
        zb = zs + length * bow * (i + 1) / steps
        body.box((-hb * k, 0.0 + 0.04 * i, za), (hb * k, freeboard + 0.06 * i, zb), paint)
    return z0, z1


def gyurza():
    """Gyurza-M (project 58155): Ukrainian armoured gunboat with a 30 mm turret."""
    m = Model('gyurza', paint='grey', seed=2101)
    m.width = 4.8
    m.camera = 18
    body = m.part('body')
    z0, z1 = boat_hull(body, 23.0, 4.8, 1.6, 'grey')
    body.box((-1.9, 1.6, -6.0), (1.9, 3.6, 4.0), 'grey')
    body.box((-1.6, 3.6, -3.0), (1.6, 4.4, 2.5), 'grey')
    body.box((-1.4, 3.85, 2.5), (1.4, 4.25, 2.53), 'glass')
    body.box((-0.1, 4.4, -1.0), (0.1, 6.5, -0.8), 'dgrey')
    body.box((-0.8, 6.0, -0.95), (0.8, 6.1, -0.85), 'dgrey')
    body.box((-2.4, 1.6, z0), (2.4, 1.75, z0 + 0.2), 'dgrey')
    lights(body, 4.0, 2.4, 1.6)
    tz = 6.5
    tur = m.part('turret', (0, 1.6, tz))
    m.set_turret(tur)
    tur.box((-0.9, 1.6, tz - 0.9), (0.9, 2.4, tz + 0.9), 'grey')
    gun(m, tur, (0, 2.1, tz + 0.9), 2.6, 0.08, mantlet=0.4)
    m.seat('driver', -0.6, 3.8, 1.5)
    m.seat('gunner', 0.0, 2.0, tz - 0.6)
    return m


def raptor():
    """Raptor (project 03160): fast patrol boat with a 14.5 mm machine gun turret."""
    m = Model('raptor', paint='lgrey', seed=2102)
    m.width = 4.1
    m.camera = 14
    body = m.part('body')
    z0, z1 = boat_hull(body, 16.9, 4.1, 1.4, 'lgrey')
    body.box((-1.7, 1.4, -4.0), (1.7, 3.2, 2.5), 'lgrey')
    body.box((-1.5, 2.4, 2.5), (1.5, 3.0, 2.53), 'glass')
    for s in (-1, 1):
        body.box((s * 1.7 - (0.0 if s > 0 else 0.03), 2.4, -2.5), (s * 1.7 + (0.03 if s > 0 else 0.0), 3.0, 1.5), 'glass')
    body.box((-0.08, 3.2, -1.0), (0.08, 4.8, -0.84), 'dgrey')
    lights(body, 2.5, 2.0, 1.4)
    tz = 4.6
    tur = m.part('turret', (0, 1.4, tz))
    m.set_turret(tur)
    tur.box((-0.6, 1.4, tz - 0.6), (0.6, 2.0, tz + 0.6), 'lgrey')
    gun(m, tur, (0, 1.8, tz + 0.6), 1.6, 0.06, mantlet=0.3)
    m.seat('driver', -0.6, 2.3, 1.0)
    m.seat('gunner', 0.0, 1.8, tz - 0.5)
    return m


def rhib():
    """A rigid inflatable boat with a heavy machine gun (river and coastal raids)."""
    m = Model('rhib', paint='dgrey', seed=2103)
    m.width = 2.6
    m.camera = 9
    body = m.part('body')
    z0, z1 = -4.0, 4.0
    body.box((-0.9, -0.4, z0 + 0.3), (0.9, 0.2, z1 - 1.5), 'dgrey')
    for s in (-1, 1):
        body.box((s * 1.3 - (0.5 if s > 0 else 0.0), 0.1, z0), (s * 1.3 + (0.0 if s > 0 else 0.5), 0.75, z1 - 1.2), 'olive')
    for i in range(3):
        k = 1 - (i + 1) / 4
        body.box((-1.3 * k, 0.1 + i * 0.05, z1 - 1.2 + i * 0.4), (1.3 * k, 0.75 + i * 0.05, z1 - 0.8 + i * 0.4), 'olive')
    body.box((-0.5, 0.2, -1.2), (0.5, 1.1, -0.4), 'dgrey')
    body.box((-0.45, 1.1, -0.45), (0.45, 1.4, -0.42), 'glass')
    body.box((-0.4, 0.2, z0 - 0.5), (0.4, 1.0, z0), 'dark')
    tz = 1.8
    tur = m.part('turret', (0, 0.2, tz))
    m.set_turret(tur)
    tur.box((-0.06, 0.2, tz - 0.06), (0.06, 1.2, tz + 0.06), 'dark')
    el = gun(m, tur, (0, 1.25, tz), 1.1, 0.05, mantlet=0.2)
    m.seat('driver', -0.3, 0.4, -0.8)
    m.seat('gunner', 0.0, 0.4, tz - 0.6)
    return m


def all_models():
    return [t72(), t90(), leopard2(), abrams(), bmp2(), bradley(), btr82(), btr4(), m113(), maxxpro(), kozak(), gyurza(), raptor(), rhib()]
