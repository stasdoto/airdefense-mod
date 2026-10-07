"""The radar stations (stage R1). Real proportions, metres."""
from boxgen import Model
from kit import axles, frame, hood_cab, cabover, van, tracks, lights


def p18():
    """P-18 "Terek" (1RL131): metre-band early warning radar - two rows of Yagi antennas on a mast, on an Ural truck."""
    m = Model('p18', paint='rgreen', seed=181)
    m.width = 2.5
    m.wheelbase = 4.2
    m.camera = 14
    body = m.part('body')
    frame(body, -3.6, 3.4, 0.75, 1.0)
    zc = hood_cab(body, 3.7, 2.4, 'rgreen', hood_len=1.4, hood_h=1.85, cab_len=1.35, cab_h=2.6)
    # Kung (equipment van).
    van(body, -3.65, zc - 0.15, 1.15, 3.2, 2.45, 'rgreen', vents=2)
    lights(body, -3.65, 1.05, 1.0, front=False)
    # Generator box and ladder.
    body.box((0.95, 0.9, -1.0), (1.22, 1.15, 0.6), 'dark')
    body.box((-1.27, 1.2, -3.0), (-1.22, 3.2, -2.6), 'dark')
    # Mast on the roof.
    mz = -1.4
    body.box((-0.35, 3.2, mz - 0.35), (0.35, 3.45, mz + 0.35), 'dark')
    body.box((-0.12, 3.45, mz - 0.12), (0.12, 5.2, mz + 0.12), 'steel')
    for s in (-1, 1):
        body.box((s * 0.12 - 0.02, 3.45, mz - 0.02), (s * 0.9 + 0.02, 3.5, mz + 0.02), 'steel')
    # The rotating antenna: a horizontal beam with two rows of six Yagi antennas looking forward.
    ant = m.part('antenna', (0, 5.2, mz))
    m.spinner = 'antenna'
    ant.box((-0.25, 5.2, mz - 0.25), (0.25, 5.45, mz + 0.25), 'dark')
    ant.box((-3.7, 5.45, mz - 0.08), (3.7, 5.6, mz + 0.08), 'steel')
    ant.box((-3.7, 7.1, mz - 0.06), (3.7, 7.2, mz + 0.06), 'steel')
    for x in (-3.6, -1.2, 1.2, 3.6):
        ant.box((x - 0.05, 5.6, mz - 0.05), (x + 0.05, 7.1, mz + 0.05), 'steel')
    for row_y in (5.95, 6.75):
        ant.box((-3.7, row_y - 0.04, mz - 0.04), (3.7, row_y + 0.04, mz + 0.04), 'steel')
        for i in range(6):
            x = -3.0 + i * 1.2
            # Boom along z, elements across it.
            ant.box((x - 0.03, row_y - 0.03, mz - 0.5), (x + 0.03, row_y + 0.03, mz + 2.6), 'lgrey')
            ant.box((x - 0.55, row_y - 0.02, mz - 0.45), (x + 0.55, row_y + 0.02, mz - 0.41), 'lgrey')
            for k in range(6):
                z = mz + 0.1 + k * 0.45
                half = 0.45 - k * 0.035
                ant.box((x - half, row_y - 0.02, z - 0.02), (x + half, row_y + 0.02, z + 0.02), 'lgrey')
    axles(m, [2.75, -0.9, -2.3], 0.62, 0.98)
    m.seat('driver', -0.5, 1.45, 1.55)
    m.seat('gunner', 0.5, 1.45, 1.55)
    return m


def st68():
    """36D6 (ST-68U): 3D surveillance radar - a big lattice reflector on a turntable, on a KrAZ semi-trailer."""
    m = Model('st68', paint='ugreen', seed=368)
    m.width = 2.6
    m.wheelbase = 7.0
    m.camera = 17
    body = m.part('body')
    frame(body, 0.8, 6.4, 0.8, 1.1, half=0.5)
    zc = hood_cab(body, 6.8, 2.6, 'ugreen', hood_len=1.6, hood_h=2.0, cab_len=1.4, cab_h=2.85, hood_w=1.7, y0=1.15)
    # Fifth wheel and the semi-trailer's gooseneck.
    body.box((-1.1, 1.1, 1.2), (1.1, 1.3, 3.6), 'dark')
    body.box((-1.2, 1.55, 0.6), (1.2, 1.95, 3.6), 'ugreen')
    body.box((-1.25, 1.3, -6.7), (1.25, 1.6, 0.8), 'ugreen')
    body.box((-1.25, 1.0, -6.7), (1.25, 1.3, -5.9), 'dark')
    lights(body, -6.7, 1.1, 1.0, front=False)
    # Equipment cabin on the trailer.
    van(body, -1.1, 0.6, 1.6, 3.1, 2.4, 'ugreen', door=False)
    # Outriggers.
    for s in (-1, 1):
        for z in (-6.2, -1.6):
            body.box((s * 1.25 - (0.0 if s > 0 else 0.6), 0.0, z - 0.15), (s * 1.25 + (0.6 if s > 0 else 0.0), 0.15, z + 0.15), 'dark')
            body.box((s * 1.55 - 0.06, 0.15, z - 0.06), (s * 1.55 + 0.06, 1.35, z + 0.06), 'dark')
    # Turntable.
    tz = -3.4
    body.box((-0.9, 1.6, tz - 0.9), (0.9, 2.1, tz + 0.9), 'dgrey')
    ant = m.part('antenna', (0, 2.1, tz))
    m.spinner = 'antenna'
    ant.box((-0.7, 2.1, tz - 0.7), (0.7, 2.5, tz + 0.7), 'ugreen')
    # Feed: arms forward to a horizontal feed bar in front of the reflector.
    for s in (-1, 1):
        ant.box((s * 0.5 - 0.06, 2.4, tz), (s * 0.5 + 0.06, 2.52, tz + 1.8), 'steel')
    ant.box((-2.6, 2.45, tz + 1.7), (2.6, 2.85, tz + 2.0), 'dgrey')
    # Reflector: a 7 x 2.6 m lattice, leaning back.
    ref = ant.part('reflector', (0, 2.5, tz + 0.3), (16, 0, 0))
    ref.box((-3.5, 2.5, tz + 0.25), (3.5, 5.1, tz + 0.35), 'mesh_fine')
    ref.box((-3.5, 2.5, tz + 0.17), (3.5, 2.6, tz + 0.25), 'steel')
    ref.box((-3.5, 5.0, tz + 0.17), (3.5, 5.1, tz + 0.25), 'steel')
    for x in (-3.5, -1.75, 0, 1.75, 3.5):
        ref.box((x - 0.06, 2.5, tz + 0.1), (x + 0.06, 5.1, tz + 0.25), 'steel')
    # Back truss.
    ref.box((-0.15, 2.5, tz - 0.4), (0.15, 4.9, tz + 0.1), 'dgrey')
    ref.box((-3.2, 3.7, tz - 0.25), (3.2, 3.85, tz + 0.1), 'dgrey')
    axles(m, [5.7, 3.0, 1.75], 0.68, 1.02, steer=1)
    axles(m, [-4.4, -5.5], 0.6, 1.0, steer=0, prefix='trailer')
    m.seat('driver', -0.55, 1.6, 4.0)
    m.seat('gunner', 0.55, 1.6, 4.0)
    return m


def trml4d():
    """Hensoldt TRML-4D: modern AESA air surveillance radar (IRIS-T SLM's eyes) on a MAN truck."""
    m = Model('trml4d', paint='nato', seed=4)
    m.width = 2.55
    m.wheelbase = 5.0
    m.camera = 14
    body = m.part('body')
    frame(body, -4.3, 3.0, 0.75, 1.05)
    zc = cabover(body, 4.4, 2.55, 'nato', cab_len=1.8, cab_h=3.05, y0=1.05)
    # Shelter with the operators.
    van(body, -0.6, zc - 0.1, 1.15, 3.2, 2.5, 'nato', door=False, vents=2)
    # Rear platform with the radar pedestal and a power unit.
    body.box((-1.25, 1.05, -4.35), (1.25, 1.3, -0.65), 'nato')
    body.box((0.4, 1.3, -1.4), (1.2, 2.2, -0.7), 'dgrey')
    lights(body, -4.35, 1.0, 1.0, front=False)
    pz = -2.7
    body.box((-0.55, 1.3, pz - 0.55), (0.55, 2.3, pz + 0.55), 'nato')
    for s in (-1, 1):
        body.box((s * 1.25 - (0.0 if s > 0 else 0.5), 0.0, pz - 0.15), (s * 1.25 + (0.5 if s > 0 else 0.0), 0.15, pz + 0.15), 'dark')
    ant = m.part('antenna', (0, 2.3, pz))
    m.spinner = 'antenna'
    ant.box((-0.4, 2.3, pz - 0.4), (0.4, 2.7, pz + 0.4), 'dgrey')
    panel = ant.part('panel', (0, 2.7, pz), (12, 0, 0))
    # The array: 2.7 x 2.2 m, the face looks forward.
    panel.box((-1.35, 2.7, pz - 0.1), (1.35, 4.9, pz + 0.25), 'nato', sides={'front': 'aesa'})
    panel.box((-1.45, 2.65, pz - 0.15), (1.45, 2.75, pz + 0.3), 'dgrey')
    panel.box((-1.45, 4.85, pz - 0.15), (1.45, 4.95, pz + 0.3), 'dgrey')
    # IFF strip on top, electronics box behind.
    panel.box((-1.2, 4.95, pz - 0.05), (1.2, 5.2, pz + 0.2), 'nato', sides={'front': 'aesa_light'})
    panel.box((-0.9, 3.0, pz - 0.55), (0.9, 4.4, pz - 0.1), 'nato')
    axles(m, [3.3, -1.7, -3.0], 0.6, 1.0, steer=1)
    m.seat('driver', -0.6, 1.75, 3.2)
    m.seat('gunner', 0.6, 1.75, 3.2)
    return m


def elm2084():
    """ELTA EL/M-2084 MMR: Iron Dome's multi-mission radar - a big AESA face turning on an 8x8 truck, desert sand."""
    m = Model('elm2084', paint='sand', seed=2084)
    m.width = 2.55
    m.wheelbase = 5.2
    m.camera = 15
    body = m.part('body')
    frame(body, -4.6, 3.2, 0.75, 1.05)
    zc = cabover(body, 4.7, 2.55, 'sand', cab_len=1.9, cab_h=3.05, y0=1.05)
    van(body, -0.4, zc - 0.1, 1.15, 3.1, 2.5, 'sand', door=True, vents=2)
    body.box((-1.25, 1.05, -4.65), (1.25, 1.3, -0.45), 'sand')
    body.box((0.45, 1.3, -1.3), (1.2, 2.1, -0.55), 'dgrey')
    lights(body, -4.65, 1.0, 1.0, front=False)
    pz = -2.8
    body.box((-0.6, 1.3, pz - 0.6), (0.6, 2.4, pz + 0.6), 'sand')
    for s_ in (-1, 1):
        for z in (pz - 1.2, pz + 1.2):
            body.box((s_ * 1.25 - (0.0 if s_ > 0 else 0.5), 0.0, z - 0.15), (s_ * 1.25 + (0.5 if s_ > 0 else 0.0), 0.15, z + 0.15), 'dark')
    ant = m.part('antenna', (0, 2.4, pz))
    m.spinner = 'antenna'
    ant.box((-0.45, 2.4, pz - 0.45), (0.45, 2.85, pz + 0.45), 'dgrey')
    panel = ant.part('panel', (0, 2.85, pz), (15, 0, 0))
    panel.box((-1.8, 2.85, pz - 0.12), (1.8, 5.3, pz + 0.3), 'sand', sides={'front': 'aesa_light'})
    panel.box((-1.9, 2.8, pz - 0.18), (1.9, 2.9, pz + 0.36), 'dgrey')
    panel.box((-1.9, 5.25, pz - 0.18), (1.9, 5.35, pz + 0.36), 'dgrey')
    for x in (-1.9, 1.8):
        panel.box((x, 2.85, pz - 0.18), (x + 0.1, 5.3, pz + 0.36), 'dgrey')
    panel.box((-1.2, 3.2, pz - 0.6), (1.2, 4.9, pz - 0.12), 'sand')
    panel.box((-1.5, 5.35, pz - 0.05), (1.5, 5.55, pz + 0.22), 'sand', sides={'front': 'aesa'})
    axles(m, [3.6, 2.2, -2.2, -3.6], 0.6, 1.0, steer=2)
    m.seat('driver', -0.6, 1.75, 3.5)
    m.seat('gunner', 0.6, 1.75, 3.5)
    return m


def sentinel():
    """AN/MPQ-64 Sentinel: X-band radar on a trailer behind a Humvee (NASAMS' radar)."""
    m = Model('sentinel', paint='camo', seed=64)
    m.width = 2.2
    m.wheelbase = 3.3
    m.camera = 12
    body = m.part('body')
    # HMMWV.
    hz0, hz1 = 0.3, 4.9
    body.box((-1.09, 0.45, hz0), (1.09, 1.15, hz1 - 0.1), 'camo')
    body.box((-0.95, 1.15, 3.2), (0.95, 1.3, hz1 - 0.15), 'camo')
    body.box((-1.0, 0.5, hz1 - 0.1), (1.0, 1.05, hz1), 'dark')
    lights(body, hz1, 0.95, 0.75)
    body.box((-1.0, 1.15, 1.25), (1.0, 1.95, 3.2), 'camo')
    body.box((-0.9, 1.35, 3.2), (-0.05, 1.85, 3.23), 'glass')
    body.box((0.05, 1.35, 3.2), (0.9, 1.85, 3.23), 'glass')
    for s in (-1, 1):
        body.box((s * 1.0 - (0.02 if s > 0 else 0.01), 1.35, 1.5), (s * 1.0 + (0.01 if s > 0 else 0.02), 1.8, 3.0), 'glass')
    body.box((-1.0, 1.15, hz0), (1.0, 1.4, 1.25), 'canvas')
    # Tow bar.
    body.box((-0.08, 0.55, -0.7), (0.08, 0.65, hz0), 'dark')
    # Trailer.
    tz0, tz1 = -4.6, -0.7
    body.box((-1.0, 0.65, tz0), (1.0, 1.0, tz1), 'camo')
    body.box((-1.0, 1.0, -1.6), (1.0, 1.6, tz1), 'camo')
    body.box((-0.9, 1.0, tz0), (0.9, 1.4, -3.8), 'camo')
    lights(body, tz0, 0.75, 0.8, front=False)
    pz = -2.7
    body.box((-0.4, 1.0, pz - 0.4), (0.4, 1.85, pz + 0.4), 'dgrey')
    ant = m.part('antenna', (0, 1.85, pz))
    m.spinner = 'antenna'
    ant.box((-0.3, 1.85, pz - 0.3), (0.3, 2.05, pz + 0.3), 'dgrey')
    panel = ant.part('panel', (0, 2.05, pz), (8, 0, 0))
    panel.box((-1.2, 2.05, pz - 0.1), (1.2, 3.35, pz + 0.18), 'camo', sides={'front': 'aesa'})
    panel.box((-1.0, 3.35, pz - 0.05), (1.0, 3.6, pz + 0.12), 'camo', sides={'front': 'aesa_light'})
    panel.box((-0.6, 2.3, pz - 0.45), (0.6, 3.1, pz - 0.1), 'dgrey')
    axles(m, [4.0, 0.75], 0.46, 0.85, width=0.36, steer=1)
    m.wheel('trailer_l', -0.98, 0.46, -2.6, 0.46, 0.36, side='wheel')
    m.wheel('trailer_r', 0.98, 0.46, -2.6, 0.46, 0.36, side='wheel')
    m.seat('driver', -0.45, 1.05, 2.1)
    m.seat('gunner', 0.45, 1.05, 2.1)
    return m


def mpq65():
    """AN/MPQ-65: the Patriot's phased array - raised at the back of a semi-trailer behind a HEMTT; looks one way."""
    m = Model('mpq65', paint='camo', seed=65)
    m.width = 2.5
    m.wheelbase = 7.5
    m.camera = 17
    body = m.part('body')
    frame(body, 1.6, 6.3, 0.85, 1.15, half=0.5)
    zc = cabover(body, 6.6, 2.45, 'camo', cab_len=1.6, cab_h=3.1, y0=1.2)
    body.box((-1.1, 1.2, 3.6), (1.1, 2.7, zc), 'camo')
    body.box((-1.1, 1.15, 1.7), (1.1, 1.35, 3.6), 'dark')
    # Semi-trailer: gooseneck and the long equipment shelter.
    body.box((-1.2, 1.7, 0.6), (1.2, 2.05, 3.4), 'camo')
    body.box((-1.25, 1.3, -6.4), (1.25, 1.7, 0.9), 'camo')
    van(body, -5.6, 0.6, 1.7, 3.35, 2.5, 'camo', door=False, vents=3)
    lights(body, -6.4, 1.2, 1.0, front=False)
    for s in (-1, 1):
        for z in (-6.0, 0.2):
            body.box((s * 1.6 - 0.07, 0.0, z - 0.07), (s * 1.6 + 0.07, 1.4, z + 0.07), 'dark')
            body.box((s * 1.25 - (0.0 if s > 0 else 0.4), 1.3, z - 0.07), (s * 1.25 + (0.4 if s > 0 else 0.0), 1.4, z + 0.07), 'dark')
    # The array swings up around the back edge of the roof and faces backwards.
    piv = (0, 3.4, -5.6)
    arr = m.part('array', piv)
    m.elevator = 'array'
    m.elevator_pivot = piv
    m.deploy_elevation = 72
    arr.box((-1.3, 3.4, -5.6), (1.3, 3.75, -3.0), 'camo', sides={'top': 'aesa'})
    arr.box((-1.4, 3.35, -5.7), (1.4, 3.45, -2.9), 'dgrey')
    # IFF and sidelobe canceller arrays along the edges.
    arr.box((-1.1, 3.75, -2.95), (1.1, 3.9, -2.75), 'camo', sides={'top': 'aesa_light'})
    for s in (-1, 1):
        arr.box((s * 1.35 - 0.12, 3.75, -4.8), (s * 1.35 + 0.12, 3.9, -3.6), 'camo', sides={'top': 'aesa_light'})
    axles(m, [5.75, 4.55], 0.66, 1.0, width=0.45, steer=2)
    axles(m, [2.85, 1.75], 0.66, 1.0, width=0.45, steer=0, prefix='rear')
    axles(m, [-3.9, -4.95], 0.58, 1.0, steer=0, prefix='trailer')
    m.seat('driver', -0.55, 1.85, 5.6)
    m.seat('gunner', 0.55, 1.85, 5.6)
    return m


def kupol():
    """9S18M1 "Kupol": the Buk battery's target acquisition radar, a big rotating panel on a tracked chassis."""
    m = Model('kupol', paint='rgreen', seed=918)
    m.width = 3.25
    m.tracked = True
    m.camera = 14
    body = m.part('body')
    top = 0.95
    tracks(m, body, -4.6, 4.6, top, 3.25, 0.5, 6, 0.33, 'rgreen')
    # Hull and the low driver's cab in front.
    body.box((-1.1, 0.45, -4.5), (1.1, top + 0.1, 4.5), 'rgreen')
    body.box((-1.62, top + 0.1, -4.5), (1.62, 1.9, 2.2), 'rgreen')
    body.box((-1.62, top + 0.1, 2.2), (1.62, 1.75, 4.6), 'rgreen')
    body.box((-1.62, 1.75, 2.9), (1.62, 2.7, 4.3), 'rgreen')
    body.box((-1.4, 2.0, 4.3), (-0.1, 2.5, 4.33), 'glass')
    body.box((0.1, 2.0, 4.3), (1.4, 2.5, 4.33), 'glass')
    lights(body, 4.6, 1.3, 1.2)
    # Equipment compartment.
    body.box((-1.55, 1.9, -4.4), (1.55, 2.85, 1.4), 'rgreen')
    body.box((-1.1, 2.85, -4.2), (1.1, 3.05, -3.0), 'dgrey')
    lights(body, -4.5, 1.2, 1.2, front=False)
    tz = -1.0
    body.box((-1.0, 2.85, tz - 1.0), (1.0, 3.15, tz + 1.0), 'dgrey')
    ant = m.part('antenna', (0, 3.15, tz))
    m.spinner = 'antenna'
    ant.box((-0.8, 3.15, tz - 0.8), (0.8, 3.5, tz + 0.8), 'rgreen')
    ant.box((-0.25, 3.5, tz - 0.6), (0.25, 4.2, tz - 0.2), 'dgrey')
    panel = ant.part('panel', (0, 3.5, tz), (10, 0, 0))
    # A big panel, its outer thirds angled forward a little.
    panel.box((-1.3, 3.6, tz - 0.1), (1.3, 5.9, tz + 0.2), 'rgreen', sides={'front': 'aesa'})
    for s in (-1, 1):
        wing = panel.part('wing_' + ('l' if s < 0 else 'r'), (s * 1.3, 3.6, tz), (0, -14 * s, 0))
        wing.box((s * 1.3 - (0 if s > 0 else 1.9), 3.6, tz - 0.1), (s * 1.3 + (1.9 if s > 0 else 0), 5.9, tz + 0.2), 'rgreen',
                 sides={'front': 'aesa'})
    panel.box((-1.0, 3.9, tz - 0.5), (1.0, 5.5, tz - 0.1), 'rgreen')
    m.seat('driver', -0.6, 1.6, 3.4)
    m.seat('gunner', 0.6, 1.6, 3.4)
    return m


def all_models():
    return [p18(), st68(), trml4d(), sentinel(), mpq65(), kupol(), elm2084()]
