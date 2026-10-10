"""1.39: the trains on the railways - a suburban electric train (the head car with its cab, the middle car with the
pantograph), a diesel locomotive and its wagons (a box car, a tank car, an open wagon with coal). Metres; the front at +z,
the rail top at y = 0. Drawn by the client only (client.nation.TrainRenderer): no items, no geometry used."""
from boxgen import Model


def bogie(body, z, axles=2, half=1.1):
    """A bogie: its frame and wheels (pairs at the gauge), the springs."""
    span = 2.4 if axles == 2 else 3.8
    body.box((-half, 0.35, z - span / 2 - 0.3), (half, 0.85, z + span / 2 + 0.3), 'dark')
    for k in range(axles):
        zz = z - span / 2 + span * k / max(1, axles - 1)
        for s in (-1, 1):
            body.box((s * 0.82 - 0.08, 0.0, zz - 0.48), (s * 0.82 + 0.08, 0.96, zz + 0.48), 'wheel_grey')
        body.box((-0.75, 0.42, zz - 0.06), (0.75, 0.54, zz + 0.06), 'steel')
    for s in (-1, 1):
        body.box((s * half - 0.05, 0.5, z - 0.5), (s * half + 0.12, 1.0, z + 0.5), 'dgrey')


def coupler(body, z, sign):
    body.box((-0.18, 0.85, z if sign > 0 else z - 0.6), (0.18, 1.15, z + 0.6 if sign > 0 else z), 'dark')
    for s in (-1, 1):
        body.box((s * 0.85 - 0.12, 0.95, z if sign > 0 else z - 0.35), (s * 0.85 + 0.12, 1.15, z + 0.35 if sign > 0 else z), 'steel')


def windows(body, half, z0, z1, y0, y1, step, width, skip=()):
    z = z0
    k = 0
    while z + width <= z1:
        if k not in skip:
            for s in (-1, 1):
                body.box((s * half - (0.0 if s > 0 else 0.02), y0, z), (s * half + (0.02 if s > 0 else 0.0), y1, z + width), 'window')
        z += step
        k += 1


def emu_body(m, body, length, cab):
    """A car of an electric train: green with a red stripe, a grey roof, doors near the ends."""
    hw = 1.62
    z0 = -length / 2
    z1 = length / 2 - (1.2 if cab else 0)
    body.box((-hw, 1.2, z0), (hw, 4.0, z1), 'rail_green')
    body.box((-hw + 0.25, 4.0, z0), (hw - 0.25, 4.3, z1), 'lgrey')
    body.box((-hw + 0.6, 4.3, z0 + 0.4), (hw - 0.6, 4.4, z1 - 0.4), 'dgrey')
    body.box((-hw - 0.01, 1.55, z0), (hw + 0.01, 1.72, z1), 'rail_red')
    body.box((-hw - 0.01, 3.45, z0), (hw + 0.01, 3.55, z1), 'rail_yellow')
    body.box((-1.3, 0.95, z0 + 3), (1.3, 1.2, z1 - 3), 'dark')
    windows(body, hw, z0 + 3.1, z1 - 2.6, 2.35, 3.25, 1.55, 1.1)
    for zc in (z0 + 1.6, z1 - 1.8):
        for s in (-1, 1):
            body.box((s * hw - (0.0 if s > 0 else 0.03), 1.25, zc - 0.65), (s * hw + (0.03 if s > 0 else 0.0), 3.35, zc + 0.65), 'dgrey')
            body.box((s * hw - (0.0 if s > 0 else 0.035), 2.4, zc - 0.5), (s * hw + (0.035 if s > 0 else 0.0), 3.2, zc - 0.05), 'window')
            body.box((s * hw - (0.0 if s > 0 else 0.035), 2.4, zc + 0.05), (s * hw + (0.035 if s > 0 else 0.0), 3.2, zc + 0.5), 'window')
    # Ends: the gangway at the back (and at the front of a middle car).
    body.box((-0.6, 1.3, z0 - 0.25), (0.6, 3.6, z0), 'dark')
    if cab:
        zf = length / 2
        # The cab: a sloping yellow face (in steps), the windscreen, the lights.
        body.box((-hw, 1.2, z1), (hw, 2.6, zf), 'rail_green')
        body.box((-hw, 2.6, z1), (hw, 3.4, zf - 0.35), 'window')
        body.box((-hw + 0.05, 2.6, zf - 0.4), (hw - 0.05, 3.4, zf - 0.32), 'window')
        body.box((-hw, 3.4, z1), (hw, 4.0, zf - 0.7), 'rail_yellow')
        body.box((-hw + 0.25, 4.0, z1), (hw - 0.25, 4.25, zf - 1.0), 'lgrey')
        body.box((-hw, 1.9, zf - 0.02), (hw, 2.6, zf), 'rail_yellow')
        body.box((-hw - 0.01, 1.55, z1), (hw + 0.01, 1.72, zf + 0.01), 'rail_red')
        body.box((-0.05, 2.62, zf - 0.38), (0.05, 3.38, zf - 0.3), 'dark')
        for s in (-1, 1):
            body.box((s * 1.1 - 0.15, 1.95, zf), (s * 1.1 + 0.15, 2.15, zf + 0.03), 'light')
            body.box((s * 1.1 - 0.12, 1.4, zf), (s * 1.1 + 0.12, 1.5, zf + 0.03), 'redlight')
        body.box((-0.2, 3.5, zf - 0.72), (0.2, 3.75, zf - 0.68), 'light')
        coupler(body, zf, 1)
    else:
        body.box((-0.6, 1.3, z1), (0.6, 3.6, z1 + 0.25), 'dark')
        coupler(body, z1, 1)
        # The pantograph, folded half up.
        body.box((-0.9, 4.4, -1.6), (0.9, 4.5, 1.6), 'dark')
        for s in (-1, 1):
            body.box((-0.06, 4.5, s * 0.6 - 0.06), (0.06, 5.2, s * 0.6 + 0.06), 'steel')
        body.box((-0.9, 5.2, -0.65), (0.9, 5.3, 0.65), 'steel')
    coupler(body, z0, -1)
    bogie(body, z0 + 3.0)
    bogie(body, length / 2 - 3.0)


def emu_head():
    m = Model('emu_head', paint='rail_green', seed=3901)
    m.no_item = True
    emu_body(m, m.part('body'), 20.0, True)
    return m


def emu_car():
    m = Model('emu_car', paint='rail_green', seed=3902)
    m.no_item = True
    emu_body(m, m.part('body'), 20.0, False)
    return m


def loco():
    """A diesel locomotive (a 2TE10-like section): the cab at the front, the long body behind, three-axle bogies."""
    m = Model('loco', paint='loco_green', seed=3903)
    m.no_item = True
    body = m.part('body')
    hw = 1.6
    length = 18.0
    z0 = -length / 2
    zf = length / 2
    body.box((-hw, 1.3, z0), (hw, 4.2, zf - 1.0), 'loco_green')
    body.box((-hw - 0.01, 1.3, z0), (hw + 0.01, 1.9, zf - 0.99), 'rail_red')
    body.box((-hw - 0.01, 1.9, z0), (hw + 0.01, 2.0, zf - 0.99), 'rail_yellow')
    body.box((-hw + 0.3, 4.2, z0 + 0.5), (hw - 0.3, 4.45, zf - 3.5), 'dgrey')
    # Grilles along the sides, the fans on the roof.
    for z in (-6.5, -3.0, 0.5):
        for s in (-1, 1):
            body.box((s * hw - (0.0 if s > 0 else 0.02), 2.6, z), (s * hw + (0.02 if s > 0 else 0.0), 3.7, z + 2.6), 'mesh_dark')
    for z in (-6.0, -3.5):
        body.box((-0.9, 4.45, z), (0.9, 4.6, z + 1.8), 'mesh_dark')
    # The cab.
    body.box((-hw, 1.3, zf - 1.0), (hw, 2.7, zf), 'loco_green')
    body.box((-hw, 1.3, zf - 0.02), (hw, 1.9, zf + 0.01), 'rail_red')
    body.box((-hw + 0.05, 2.7, zf - 0.45), (hw - 0.05, 3.5, zf - 0.37), 'window')
    body.box((-hw, 2.7, zf - 1.0), (hw, 3.5, zf - 0.45), 'loco_green')
    body.box((-hw, 3.5, zf - 1.0), (hw, 4.2, zf - 0.6), 'loco_green')
    body.box((-0.05, 2.72, zf - 0.38), (0.05, 3.48, zf - 0.32), 'dark')
    for s in (-1, 1):
        body.box((s * hw - (0.0 if s > 0 else 0.02), 2.8, zf - 2.6), (s * hw + (0.02 if s > 0 else 0.0), 3.5, zf - 1.3), 'window')
        body.box((s * 1.1 - 0.15, 2.1, zf), (s * 1.1 + 0.15, 2.3, zf + 0.03), 'light')
    body.box((-0.25, 3.7, zf - 0.6), (0.25, 3.95, zf - 0.56), 'light')
    body.box((-1.4, 0.9, z0 + 4.5), (1.4, 1.3, zf - 4.5), 'dark')
    coupler(body, zf, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 4.0, axles=3)
    bogie(body, zf - 4.0, axles=3)
    return m


def boxcar():
    m = Model('boxcar', paint='wagon_brown', seed=3904)
    m.no_item = True
    body = m.part('body')
    hw = 1.55
    length = 14.0
    z0, z1 = -length / 2, length / 2
    body.box((-hw, 1.2, z0), (hw, 3.9, z1), 'wagon_brown')
    body.box((-hw + 0.15, 3.9, z0), (hw - 0.15, 4.1, z1), 'rust')
    z = z0 + 0.4
    while z < z1 - 0.2:
        for s in (-1, 1):
            body.box((s * hw - (0.0 if s > 0 else 0.05), 1.2, z), (s * hw + (0.05 if s > 0 else 0.0), 3.9, z + 0.12), 'rust')
        z += 1.3
    for s in (-1, 1):
        body.box((s * hw - (0.0 if s > 0 else 0.07), 1.3, -1.0), (s * hw + (0.07 if s > 0 else 0.0), 3.7, 1.0), 'wagon_brown')
        body.box((s * hw - (0.0 if s > 0 else 0.09), 3.7, -1.3), (s * hw + (0.09 if s > 0 else 0.0), 3.8, 1.3), 'dark')
    body.box((-1.3, 0.95, z0 + 1), (1.3, 1.2, z1 - 1), 'dark')
    coupler(body, z1, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 2.3)
    bogie(body, z1 - 2.3)
    return m


def tank():
    m = Model('tank_car', paint='tank_black', seed=3905)
    m.no_item = True
    body = m.part('body')
    length = 12.0
    z0, z1 = -length / 2, length / 2
    # The tank, round enough: three boxes stacked crosswise; the dome on top, the end caps.
    body.box((-1.45, 1.75, z0 + 0.4), (1.45, 3.55, z1 - 0.4), 'tank_black')
    body.box((-1.1, 1.4, z0 + 0.4), (1.1, 3.9, z1 - 0.4), 'tank_black')
    body.box((-0.7, 1.3, z0 + 0.4), (0.7, 4.0, z1 - 0.4), 'tank_black')
    body.box((-0.95, 1.6, z0 + 0.15), (0.95, 3.7, z0 + 0.4), 'tank_black')
    body.box((-0.95, 1.6, z1 - 0.4), (0.95, 3.7, z1 - 0.15), 'tank_black')
    body.box((-0.45, 4.0, -0.45), (0.45, 4.35, 0.45), 'tank_black')
    body.box((-1.46, 2.5, -2.0), (1.46, 2.8, 2.0), 'orange')
    body.box((-1.4, 1.0, z0), (1.4, 1.3, z1), 'dark')
    coupler(body, z1, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 1.9)
    bogie(body, z1 - 1.9)
    return m


def gondola():
    m = Model('gondola', paint='grey', seed=3906)
    m.no_item = True
    body = m.part('body')
    hw = 1.55
    length = 13.0
    z0, z1 = -length / 2, length / 2
    body.box((-hw, 1.2, z0), (hw, 1.4, z1), 'dgrey')
    for s in (-1, 1):
        body.box((s * hw - (0.12 if s > 0 else 0.0), 1.4, z0), (s * hw + (0.0 if s < 0 else 0.0) + (0.0 if s > 0 else 0.12), 3.3, z1), 'grey')
    body.box((-hw, 1.4, z0), (hw, 3.3, z0 + 0.12), 'grey')
    body.box((-hw, 1.4, z1 - 0.12), (hw, 3.3, z1), 'grey')
    z = z0 + 0.8
    while z < z1 - 0.3:
        for s in (-1, 1):
            body.box((s * hw - (0.0 if s > 0 else 0.06), 1.4, z), (s * hw + (0.06 if s > 0 else 0.0), 3.3, z + 0.14), 'rust')
        z += 1.6
    # The coal heaped up inside.
    body.box((-hw + 0.12, 1.4, z0 + 0.12), (hw - 0.12, 3.2, z1 - 0.12), 'coal')
    body.box((-1.0, 3.2, z0 + 1.2), (1.0, 3.6, z1 - 1.2), 'coal')
    body.box((-1.3, 0.95, z0 + 1), (1.3, 1.2, z1 - 1), 'dark')
    coupler(body, z1, 1)
    coupler(body, z0, -1)
    bogie(body, z0 + 2.2)
    bogie(body, z1 - 2.2)
    return m


def all_models():
    return [emu_head(), emu_car(), loco(), boxcar(), tank(), gondola()]
