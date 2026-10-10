"""1.38: the towns' emergency services - a fire engine, an ambulance, a police car. Metres."""
from boxgen import Model
from kit import axles, frame, cabover, lights


def beacons(body, z, y, half, colours=('beacon_red', 'beacon_blue')):
    """A light bar across the roof: a dark bar, a coloured lamp at each end (they flash in the game)."""
    body.box((-half, y, z - 0.18), (half, y + 0.08, z + 0.18), 'dark')
    body.box((-half, y + 0.08, z - 0.15), (-half + 0.45, y + 0.26, z + 0.15), colours[0])
    body.box((half - 0.45, y + 0.08, z - 0.15), (half, y + 0.26, z + 0.15), colours[1])
    body.box((-half + 0.45, y + 0.08, z - 0.12), (half - 0.45, y + 0.2, z + 0.12), 'lgrey')


def fire_truck():
    m = Model('fire_truck', paint='fire_red', seed=5101)
    m.width = 2.5
    m.wheelbase = 4.2
    m.camera = 12
    body = m.part('body')
    frame(body, -3.8, 3.2, 0.75, 1.0)
    zc = cabover(body, 3.6, 2.5, 'fire_red', cab_len=1.9, cab_h=3.0)
    # The body behind the cab: lockers with roll-up doors (light grey), a white band, the water tank inside.
    body.box((-1.25, 1.0, -3.9), (1.25, 2.85, zc - 0.1), 'fire_red')
    for s in (-1, 1):
        for z0 in (-3.6, -2.2, -0.8):
            body.box((s * 1.25 - (0.0 if s > 0 else 0.03), 1.15, z0), (s * 1.25 + (0.03 if s > 0 else 0.0), 2.45, z0 + 1.25), 'lgrey')
        body.box((s * 1.25 - (0.0 if s > 0 else 0.035), 2.5, -3.9), (s * 1.25 + (0.035 if s > 0 else 0.0), 2.65, zc - 0.1), 'white')
    # The ladder on top, the hose reel at the back.
    for s in (-1, 1):
        body.box((s * 0.45 - 0.05, 3.0, -3.7), (s * 0.45 + 0.05, 3.12, zc + 0.3), 'steel')
    z = -3.6
    while z < zc + 0.2:
        body.box((-0.45, 3.02, z), (0.45, 3.08, z + 0.06), 'steel')
        z += 0.35
    body.box((-0.6, 2.85, -1.0), (0.6, 3.0, -0.5), 'dark')
    body.box((-0.9, 1.2, -3.98), (0.9, 2.2, -3.9), 'dgrey')
    lights(body, -3.98, 1.0, 1.0, front=False)
    beacons(body, 3.0 - 0.6, 3.0, 1.0)
    axles(m, [2.75, -0.9, -2.3], 0.55, 0.98, side='wheel_grey')
    m.seat('driver', -0.5, 1.55, 2.6)
    m.seat('gunner', 0.5, 1.55, 2.6)
    return m


def ambulance():
    m = Model('ambulance', paint='white', seed=5102)
    m.width = 2.1
    m.wheelbase = 3.6
    m.camera = 9
    body = m.part('body')
    hw = 1.05
    zf = 2.9
    # A van: the short bonnet, the cab, the tall box at the back.
    body.box((-hw, 0.55, zf - 0.9), (hw, 1.35, zf), 'white')
    body.box((-hw + 0.1, 0.5, zf - 0.05), (hw - 0.1, 0.8, zf + 0.06), 'dark')
    body.box((-hw, 0.55, zf - 2.2), (hw, 2.25, zf - 0.9), 'white')
    body.box((-hw + 0.08, 1.4, zf - 0.93), (hw - 0.08, 2.05, zf - 0.88), 'glass')
    for s in (-1, 1):
        body.box((s * hw - (0.0 if s > 0 else 0.02), 1.4, zf - 1.85), (s * hw + (0.02 if s > 0 else 0.0), 2.0, zf - 1.0), 'glass')
    m.cab(-hw, hw, 0.55, 2.25, zf - 2.2, zf - 0.9)
    body.box((-hw, 0.55, -3.0), (hw, 2.75, zf - 2.2), 'white')
    # The red band round it, the red cross (in red boxes) on the sides and the back.
    for s in (-1, 1):
        x0, x1 = (s * hw - (0.0 if s > 0 else 0.02), s * hw + (0.02 if s > 0 else 0.0))
        body.box((x0, 1.25, -3.0), (x1, 1.45, zf - 0.9), 'red')
        body.box((x0, 1.75, -1.4), (x1, 2.45, -1.2), 'red')
        body.box((x0, 2.0, -1.65), (x1, 2.2, -0.95), 'red')
    body.box((-0.1, 1.7, -3.03), (0.1, 2.4, -3.0), 'red')
    body.box((-0.35, 1.95, -3.03), (0.35, 2.15, -3.0), 'red')
    body.box((-0.9, 0.7, -3.04), (0.9, 2.55, -3.02), 'lgrey')
    lights(body, zf + 0.06, 0.85, 0.8)
    lights(body, -3.04, 0.9, 0.9, front=False)
    beacons(body, zf - 1.6, 2.25, 0.85)
    axles(m, [1.9, -1.7], 0.42, 0.88, width=0.3, side='wheel_grey')
    m.seat('driver', -0.5, 1.15, zf - 1.5)
    m.seat('gunner', 0.5, 1.15, zf - 1.5)
    return m


def police_car():
    m = Model('police_car', paint='white', seed=5103)
    m.width = 1.85
    m.wheelbase = 2.8
    m.camera = 8
    body = m.part('body')
    hw = 0.92
    # A saloon: bonnet, cabin (glass all round), boot; a blue band along the sides, the light bar.
    body.box((-hw, 0.35, 1.05), (hw, 0.95, 2.35), 'white')
    body.box((-hw, 0.35, -2.25), (hw, 0.95, 1.05), 'white')
    body.box((-hw + 0.05, 0.95, -1.15), (hw - 0.05, 1.45, 0.95), 'white')
    body.box((-hw + 0.1, 1.0, 0.95), (hw - 0.1, 1.4, 1.0), 'glass')
    body.box((-hw + 0.1, 1.0, -1.2), (hw - 0.1, 1.38, -1.15), 'glass')
    for s in (-1, 1):
        body.box((s * (hw - 0.05) - (0.0 if s > 0 else 0.02), 1.0, -1.0), (s * (hw - 0.05) + (0.02 if s > 0 else 0.0), 1.38, 0.85), 'glass')
        body.box((s * hw - (0.0 if s > 0 else 0.02), 0.55, -2.25), (s * hw + (0.02 if s > 0 else 0.0), 0.75, 2.35), 'police_blue')
    m.cab(-hw + 0.05, hw - 0.05, 0.35, 1.45, -1.15, 0.95)
    body.box((-hw + 0.1, 0.3, 2.35), (hw - 0.1, 0.55, 2.42), 'dark')
    body.box((-0.6, 0.94, 1.4), (0.6, 0.96, 2.2), 'police_blue')
    lights(body, 2.42, 0.62, 0.62)
    lights(body, -2.25, 0.7, 0.7, front=False)
    beacons(body, -0.1, 1.45, 0.7)
    axles(m, [1.4, -1.4], 0.33, 0.78, width=0.25, side='wheel_grey')
    m.seat('driver', -0.4, 0.85, 0.1)
    m.seat('gunner', 0.4, 0.85, 0.1)
    return m


def all_models():
    return [fire_truck(), ambulance(), police_car()]
