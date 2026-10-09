"""
1.32 "Aviation": the AH-64 Apache and the A-10 Thunderbolt II - the western side's attack aircraft (the eastern one
flies the Mi-24, Ka-52 and Su-25). Metres, x right, y up, z forward; built like aircraft.py (lofted round bodies, glass
canopies with the cockpit inside).
"""
from boxgen import Model
from kit import lights
from airdefense import barrel, canister, missile
from aircraft import loft, bubble, rotor, tail_rotor, wing, fin, gear, pylon


def ah64():
    """AH-64D Apache: the narrow fuselage with the two cockpits in tandem (gunner in front, pilot above and behind), the
    sensor turret on the nose, the 30 mm chain gun under the chin, the engine pods on the shoulders, stub wings with
    Hellfires and rocket pods, the radome over the four-blade rotor, the tailwheel."""
    m = Model('ah64', paint='nato', seed=3201)
    m.width = 2.0
    m.camera = 16
    body = m.part('body')
    loft(body, [(6.5, 1.6, 0.2, 0.22), (6.1, 1.62, 0.45, 0.48), (5.4, 1.7, 0.6, 0.64), (4.2, 1.82, 0.7, 0.8),
                (2.6, 1.9, 0.78, 0.92), (0.0, 1.95, 0.8, 0.95), (-1.6, 2.05, 0.65, 0.75)], 'nato',
         cuts=[(3.95, 5.55, 2.05, 0.46), (2.4, 3.95, 2.62, 0.5)])
    bubble(body, 3.95, 5.15, 5.55, 2.05, 2.8, 0.46, bow=4.6)
    bubble(body, 2.4, 3.6, 3.95, 2.62, 3.35, 0.5, bow=3.2)
    # The TADS/PNVS sensor turret on the nose and the chain gun under the chin.
    body.box((-0.3, 1.3, 6.3), (0.3, 1.85, 6.85), 'dgrey', sides={'front': 'glass'})
    body.box((-0.18, 0.95, 4.3), (0.18, 1.25, 4.7), 'dark')
    barrel(body, 0.0, 1.05, 4.7, 1.7, 0.07)
    # Spine and the engine pods on the shoulders with their exhausts turned out.
    loft(body, [(2.4, 2.9, 0.45, 0.4), (1.8, 3.05, 0.6, 0.45), (-0.8, 3.0, 0.6, 0.45), (-1.6, 2.8, 0.45, 0.35)], 'nato', steps=1)
    for s in (-1, 1):
        loft(body, [(1.6, 2.6, 0.32, 0.32), (1.3, 2.62, 0.38, 0.38), (-1.2, 2.6, 0.38, 0.38), (-1.6, 2.58, 0.3, 0.3)], 'nato', steps=1,
             x=s * 0.98)
        body.box((s * 0.98 - 0.26, 2.35, 1.6), (s * 0.98 + 0.26, 2.85, 1.64), 'dark')
        body.box((s * 1.25 - 0.18, 2.4, -1.75), (s * 1.25 + 0.22, 2.8, -1.4), 'dark')
    # Mast with the Longbow radome.
    body.box((-0.12, 3.4, 0.55), (0.12, 4.0, 0.8), 'dgrey')
    body.box((-0.45, 4.25, 0.25), (0.45, 4.6, 1.1), 'lgrey')
    # Tail boom, the T-tail fin with the tail rotor on its left, the tailwheel.
    loft(body, [(-1.5, 2.1, 0.5, 0.5), (-5.0, 2.3, 0.32, 0.36), (-7.6, 2.45, 0.2, 0.24)], 'nato', steps=3)
    fin(body, -7.0, -8.0, 2.4, 4.4, 1.3, 0.7, 0.16, 'nato', steps=4)
    body.box((-1.1, 4.35, -8.2), (1.1, 4.45, -7.4), 'nato')
    tail_rotor(body, -0.22, 3.7, -7.7, 1.4, 4, side=-1)
    # Stub wings: Hellfires outboard, rocket pods inboard.
    for s in (-1, 1):
        body.box((s * 0.75 if s > 0 else -2.45, 2.0, -0.1), (2.45 if s > 0 else -0.75, 2.14, 1.0), 'nato')
        pylon(body, s * 1.3, 2.0, 1.75, -0.1, 0.8)
        canister(body, s * 1.3, 1.45, -0.6, 1.5, 0.42, 'dgrey')
        pylon(body, s * 2.1, 2.0, 1.8, 0.0, 0.7)
        for dx in (-0.12, 0.12):
            for dy in (-0.12, 0.12):
                missile(body, s * 2.1 + dx, 1.6 + dy, -0.6, 1.6, 0.17, body='olive', nose='dgrey')
    gear(m, -1.0, 3.6, 0.32, name='gear_l', strut=0.6)
    gear(m, 1.0, 3.6, 0.32, name='gear_r', strut=0.6)
    gear(m, 0, -7.2, 0.18, name='gear_tail', strut=0.5)
    rotor(m, body, (0, 3.95, 0.7), 7.3, 4, 'dark', chord=0.53)
    m.spinner = 'rotor'
    m.seat('driver', 0.0, 2.2, 3.15)
    m.seat('gunner', 0.0, 1.62, 4.75)
    m.cab(-0.46, 0.46, 1.75, 2.62, 2.4, 3.95, kind='heli', glazing='bubble', panel_top=2.6, panel_z=3.7, modern=True)
    m.cab(-0.42, 0.42, 1.15, 2.05, 3.95, 5.55, kind='heli', glazing='bubble', panel_top=2.05, panel_z=5.25, modern=True)
    return m


def a10():
    """A-10C Thunderbolt II: the straight wing low on the fuselage, the two big engines on pylons high at the back, the
    twin fins, the GAU-8 Avenger's muzzle under the nose, ten pylons of bombs and Mavericks."""
    m = Model('a10', paint='grey', seed=3202)
    m.width = 2.4
    m.camera = 19
    body = m.part('body')
    loft(body, [(8.0, 1.75, 0.1, 0.1), (7.6, 1.78, 0.38, 0.42), (6.6, 1.85, 0.58, 0.66), (5.2, 1.9, 0.7, 0.8), (3.5, 1.92, 0.75, 0.85),
                (0.0, 1.95, 0.72, 0.82), (-3.5, 2.05, 0.6, 0.66), (-6.2, 2.2, 0.38, 0.4), (-7.8, 2.3, 0.2, 0.22)], 'grey',
         cuts=[(3.9, 6.0, 2.45, 0.46)])
    # The bubble canopy far forward, the spine.
    bubble(body, 3.9, 5.4, 6.0, 2.45, 3.35, 0.46, bow=5.0)
    for (za, zb, top, hw) in ((2.6, 3.9, 3.0, 0.42), (0.6, 2.6, 2.85, 0.38)):
        body.box((-hw, 2.4, za), (hw, top, zb), 'grey')
    # The gun's muzzle under the nose, a little left (the seven barrels).
    body.box((-0.3, 1.05, 7.6), (0.1, 1.42, 8.3), 'dark')
    for dx in (-0.2, -0.1, 0.0):
        barrel(body, dx, 1.24, 8.3, 0.25, 0.05)
    # Wings, low and straight, the landing gear pods under them.
    wing(body, 0.7, 8.6, 1.65, 1.6, 0.9, 3.0, 1.5, 0.3, 'grey', steps=8)
    wing(body, -0.7, -8.6, 1.65, 1.6, 0.9, 3.0, 1.5, 0.3, 'grey', steps=8)
    for s in (-1, 1):
        body.box((s * 2.4 - 0.35, 1.0, 0.0), (s * 2.4 + 0.35, 1.6, 2.2), 'grey')
    # Engines on pylons high at the back of the fuselage, their intakes and nozzles.
    for s in (-1, 1):
        body.box((s * 0.75 - 0.08, 2.4, -3.2), (s * 0.75 + 0.08, 2.9, -2.0), 'grey')
        loft(body, [(-1.5, 3.25, 0.5, 0.5), (-1.8, 3.25, 0.62, 0.62), (-3.9, 3.25, 0.62, 0.62), (-4.4, 3.25, 0.5, 0.5)], 'grey', steps=1,
             x=s * 1.1)
        body.box((s * 1.1 - 0.45, 2.85, -1.5), (s * 1.1 + 0.45, 3.65, -1.45), 'dark')
        body.box((s * 1.1 - 0.4, 2.9, -4.45), (s * 1.1 + 0.4, 3.6, -4.4), 'dark')
    # The tailplane with the twin fins at its tips.
    wing(body, 0.3, 3.2, 2.35, -6.0, -6.4, 1.8, 1.4, 0.14, 'grey', steps=3)
    wing(body, -0.3, -3.2, 2.35, -6.0, -6.4, 1.8, 1.4, 0.14, 'grey', steps=3)
    for s in (-1, 1):
        fin(body, -6.0, -6.6, 1.6, 4.3, 1.8, 1.2, 0.14, 'grey', steps=5, x=s * 3.2)
    # Pylons: Mavericks, bombs, rocket pods.
    for s in (-1, 1):
        for x, kind in ((1.4, 'bomb'), (3.4, 'pod'), (4.6, 'bomb'), (5.8, 'agm'), (7.0, 'pod')):
            pylon(body, s * x, 1.6, 1.4, 0.6, 1.8)
            if kind == 'bomb':
                body.box((s * x - 0.22, 0.95, 0.2), (s * x + 0.22, 1.39, 2.0), 'olive')
            elif kind == 'pod':
                canister(body, s * x, 1.15, 0.0, 2.1, 0.44, 'dgrey')
            else:
                missile(body, s * x, 1.2, 0.0, 2.4, 0.3, body='lgrey', nose='dgrey')
    gear(m, -0.3, 6.0, 0.33, name='gear_nose', strut=0.9)
    gear(m, -2.4, 0.9, 0.42, name='gear_l', strut=0.7)
    gear(m, 2.4, 0.9, 0.42, name='gear_r', strut=0.7)
    lights(body, 7.6, 1.3, 0.2)
    m.seat('driver', 0.0, 2.15, 4.45)
    m.cab(-0.5, 0.5, 1.6, 2.45, 3.9, 6.0, kind='jet', sill=2.45, western=True)
    return m


def all_models():
    return [ah64(), a10()]
