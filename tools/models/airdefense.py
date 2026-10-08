"""Air defence of the war in Ukraine (stage R4). Real proportions, metres. x right, y up, z forward."""
from boxgen import Model
from kit import axles, frame, hood_cab, cabover, van, tracks, lights


def missile(part, x, y, z0, length, dia, body='white', nose=None, fins=True, fin_style=None):
    """A missile lying along +z from z0: body, tapered nose, tail fins."""
    r = dia / 2
    nb = length * 0.82
    part.box((x - r, y - r, z0), (x + r, y + r, z0 + nb), body)
    part.box((x - r * 0.7, y - r * 0.7, z0 + nb), (x + r * 0.7, y + r * 0.7, z0 + nb + length * 0.11), nose or body)
    part.box((x - r * 0.35, y - r * 0.35, z0 + nb + length * 0.11), (x + r * 0.35, y + r * 0.35, z0 + length), nose or body)
    if fins:
        f = fin_style or body
        w = dia * 0.9
        part.box((x - r - w, y - 0.015, z0), (x + r + w, y + 0.015, z0 + dia * 1.4), f)
        part.box((x - 0.015, y - r - w, z0), (x + 0.015, y + r + w, z0 + dia * 1.4), f)


def canister(part, x, y, z0, length, dia, style, end='dark'):
    r = dia / 2
    part.box((x - r, y - r, z0), (x + r, y + r, z0 + length), style)
    part.box((x - r * 0.85, y - r * 0.85, z0 + length), (x + r * 0.85, y + r * 0.85, z0 + length + 0.03), end)


def barrel(part, x, y, z0, length, dia, style='dark'):
    r = dia / 2
    part.box((x - r, y - r, z0), (x + r, y + r, z0 + length), style)
    part.box((x - r * 1.6, y - r * 1.6, z0 + length - 0.12), (x + r * 1.6, y + r * 1.6, z0 + length), style)


def dish(part, x, y, z, w, h, face='aesa', back=None, depth=0.18, facing='front'):
    """A flat radar face looking forward (or backward) with a rim."""
    back = back or part.model.paint
    if facing == 'front':
        part.box((x - w / 2, y - h / 2, z - depth), (x + w / 2, y + h / 2, z), back, sides={'front': face})
    else:
        part.box((x - w / 2, y - h / 2, z), (x + w / 2, y + h / 2, z + depth), back, sides={'back': face})


def kamaz_8x8(m, body, zf, length, paint, cab_h=3.0):
    """KamAZ-6560 style 8x8: cab over the front axle, frame behind."""
    zc = cabover(body, zf, 2.55, paint, cab_len=1.75, cab_h=cab_h, y0=1.1)
    frame(body, zf - length + 0.2, zc + 0.2, 0.8, 1.1, half=0.5)
    return zc


# ------------------------------------------------------------------------------------------------------------------


def pantsir():
    """Pantsir-S1: KamAZ 8x8 with a turret - twin 30 mm guns and twelve missiles, search radar on top, tracker in front."""
    m = Model('pantsir', paint='rgreen', seed=1001)
    m.width = 2.55
    m.wheelbase = 6.3
    m.camera = 14
    body = m.part('body')
    zc = kamaz_8x8(m, body, 5.0, 10.0, 'rgreen')
    body.box((-1.27, 1.1, -4.95), (1.27, 1.55, zc - 0.05), 'rgreen')
    van(body, -0.2, zc - 0.1, 1.55, 2.75, 2.5, 'rgreen', door=False, vents=1)
    body.box((-1.25, 1.55, -4.9), (1.25, 1.8, -0.2), 'rgreen')
    lights(body, -4.95, 1.2, 1.0, front=False)
    for s in (-1, 1):
        body.box((s * 1.27 - (0.0 if s > 0 else 0.3), 0.05, -4.2), (s * 1.27 + (0.3 if s > 0 else 0.0), 0.15, -3.9), 'dark')
    tz = -2.4
    tur = m.part('turret', (0, 1.8, tz))
    m.set_turret(tur)
    tur.box((-1.0, 1.8, tz - 1.2), (1.0, 3.0, tz + 1.1), 'rgreen')
    tur.box((-0.8, 3.0, tz - 1.0), (0.8, 3.25, tz + 0.7), 'rgreen')
    # Tracking radar in front of the turret.
    dish(tur, 0, 2.55, tz + 1.35, 1.0, 0.95, face='aesa', depth=0.25)
    # Search radar on a short mast, turning.
    tur.box((-0.12, 3.25, tz - 0.6), (0.12, 3.7, tz - 0.36), 'dgrey')
    sp = tur.part('search', (0, 3.7, tz - 0.48))
    m.spinner = 'search'
    sp.box((-0.9, 3.7, tz - 0.6), (0.9, 4.45, tz - 0.36), 'rgreen', sides={'front': 'aesa', 'back': 'aesa'})
    # Guns and missile packs on the turret sides, elevating together.
    el = tur.part('weapons', (0, 2.55, tz - 0.3))
    m.set_elevator(el, deploy=15)
    el.box((-1.15, 2.45, tz - 0.4), (1.15, 2.65, tz - 0.2), 'dgrey')
    n = 0
    for s in (-1, 1):
        cx = s * 1.55
        # Six missile canisters (two rows of three) per side.
        el.box((cx - 0.38, 2.2, tz - 1.3), (cx + 0.38, 2.95, tz - 1.15), 'rgreen')
        for row in range(2):
            for col in range(3):
                x = cx + (col - 1) * 0.24
                y = 2.38 + row * 0.38
                name = 'can_%d' % n
                c = el.part(name, (x, y, tz - 1.2))
                canister(c, x, y, tz - 1.2, 3.2, 0.22, 'rgreen')
                m.rail(name, (x, y, tz + 2.0))
                n += 1
    # Guns under the packs, outboard.
    for s, tag in ((-1, 'l'), (1, 'r')):
        cx = s * 1.55
        el.box((cx - 0.3, 1.92, tz - 0.9), (cx + 0.3, 2.18, tz + 0.4), 'dgrey')
        for k in (-1, 1):
            barrel(el, cx + k * 0.12, 2.05, tz + 0.4, 2.3, 0.07)
        m.rail('barrel_' + tag, (cx, 2.05, tz + 2.7))
    axles(m, [4.35, 3.0, -1.6, -3.0], 0.62, 1.0, width=0.45, steer=2)
    m.seat('driver', -0.6, 1.8, 3.8)
    m.seat('gunner', 0.6, 1.8, 3.8)
    return m


def tor():
    """Tor-M2: tracked, eight missiles standing in the turret, search radar on top, phased array in front."""
    m = Model('tor', paint='rgreen', seed=1002)
    m.width = 3.3
    m.tracked = True
    m.camera = 12
    body = m.part('body')
    top = 0.9
    tracks(m, body, -3.8, 3.8, top, 3.3, 0.48, 6, 0.32, 'rgreen')
    body.box((-1.15, 0.45, -3.7), (1.15, top + 0.1, 3.7), 'rgreen')
    body.box((-1.62, top + 0.1, -3.7), (1.62, 1.8, 2.8), 'rgreen')
    body.box((-1.62, top + 0.1, 2.8), (1.62, 1.5, 3.85), 'rgreen')
    body.box((-1.0, 1.5, 3.0), (0.2, 1.62, 3.7), 'dgrey')
    lights(body, 3.85, 1.2, 1.25)
    lights(body, -3.7, 1.3, 1.25, front=False)
    tz = -0.4
    tur = m.part('turret', (0, 1.8, tz))
    m.set_turret(tur)
    tur.box((-1.3, 1.8, tz - 1.7), (1.3, 3.1, tz + 1.3), 'rgreen')
    # Phased array in front (sloped face), TV/IR sight beside it.
    dish(tur, 0, 2.45, tz + 1.55, 1.5, 1.15, face='aesa', depth=0.25)
    tur.box((0.85, 2.7, tz + 1.3), (1.15, 2.95, tz + 1.5), 'dark')
    # Missile hatches on the roof (two rows of four).
    for i in range(8):
        x = -0.6 + (i % 4) * 0.4
        z = tz - 0.9 + (i // 4) * 0.45
        tur.box((x - 0.17, 3.1, z - 0.18), (x + 0.17, 3.14, z + 0.18), 'dgrey')
    # Search radar folded on a mast at the back, turning when working.
    tur.box((-0.15, 3.1, tz - 1.6), (0.15, 3.6, tz - 1.3), 'dgrey')
    sp = tur.part('search', (0, 3.6, tz - 1.45))
    m.spinner = 'search'
    sp.box((-1.25, 3.6, tz - 1.6), (1.25, 4.3, tz - 1.3), 'rgreen', sides={'front': 'aesa', 'back': 'aesa'})
    # Vertical launch: the missiles leave straight up from the hatches.
    m.set_virtual_elevator('vls', (0, 3.0, tz), 90, 90)
    for i in range(8):
        x = -0.6 + (i % 4) * 0.4
        z = tz - 0.9 + (i // 4) * 0.45
        m.rail('cell_%d' % i, (x, 3.6, z), at=90)
    m.seat('driver', -0.75, 1.3, 3.0)
    m.seat('gunner', 0.0, 2.6, 0.2)
    return m


def buk():
    """Buk-M1 (9A310M1 TELAR): tracked, four big missiles on a rail launcher, fire control radar on the front of the turret."""
    m = Model('buk', paint='ugreen', seed=1003)
    m.width = 3.25
    m.tracked = True
    m.camera = 14
    body = m.part('body')
    top = 0.95
    tracks(m, body, -4.6, 4.6, top, 3.25, 0.5, 6, 0.33, 'ugreen')
    body.box((-1.1, 0.45, -4.5), (1.1, top + 0.1, 4.5), 'ugreen')
    body.box((-1.62, top + 0.1, -4.5), (1.62, 1.85, 2.9), 'ugreen')
    body.box((-1.62, top + 0.1, 2.9), (1.62, 1.6, 4.65), 'ugreen')
    body.box((-1.62, 1.6, 3.3), (-0.2, 2.35, 4.4), 'ugreen')
    body.box((-1.45, 1.8, 4.4), (-0.35, 2.2, 4.43), 'glass')
    lights(body, 4.65, 1.25, 1.25)
    lights(body, -4.5, 1.3, 1.25, front=False)
    tz = -0.6
    tur = m.part('turret', (0, 1.85, tz))
    m.set_turret(tur)
    tur.box((-1.4, 1.85, tz - 1.6), (1.4, 2.8, tz + 1.5), 'ugreen')
    # The big fire control radar: radome in front of the turret.
    tur.box((-0.75, 1.95, tz + 1.5), (0.75, 3.35, tz + 2.1), 'ugreen', sides={'front': 'radome'})
    tur.box((-0.55, 2.1, tz + 2.1), (0.55, 3.2, tz + 2.2), 'radome')
    # The launcher: four missiles on rails, pivoting at the back, lying forward over the turret on the march.
    el = tur.part('launcher', (0, 3.0, tz - 1.6))
    m.set_elevator(el, deploy=25)
    el.box((-1.0, 2.8, tz - 1.7), (1.0, 3.0, tz + 2.6), 'dgrey')
    el.box((-0.15, 3.0, tz - 1.7), (0.15, 3.2, tz + 2.6), 'dgrey')
    for i, (x, y) in enumerate(((-0.55, 3.2), (0.55, 3.2), (-0.55, 3.75), (0.55, 3.75))):
        name = 'missile_%d' % i
        mp = el.part(name, (x, y, tz - 1.6))
        missile(mp, x, y, tz - 1.55, 5.5, 0.4, body='white', nose='lgrey', fin_style='lgrey')
        el.box((x - 0.05, y - 0.3 if y > 3.5 else y - 0.25, tz - 0.5), (x + 0.05, y - 0.2, tz + 1.5), 'dgrey')
        m.rail(name, (x, y, tz + 3.9))
    m.seat('driver', -0.9, 1.5, 3.8)
    m.seat('gunner', 0.6, 2.4, 0.3)
    return m


def s300():
    """S-300PS (5P85S): MAZ-543 with four big canisters raised upright to fire."""
    m = Model('s300', paint='ugreen', seed=1004)
    m.width = 3.05
    m.wheelbase = 7.0
    m.camera = 17
    body = m.part('body')
    frame(body, -6.4, 6.4, 0.85, 1.25, half=0.55)
    # MAZ-543: two cabs with the engine between them.
    zf = 6.6
    for s in (-1, 1):
        x0, x1 = (s * 0.6, s * 1.52) if s > 0 else (s * 1.52, s * 0.6)
        body.box((x0, 1.2, zf - 2.0), (x1, 2.85, zf), 'ugreen')
        body.box((x0 + 0.08, 2.1, zf), (x1 - 0.08, 2.7, zf + 0.03), 'glass')
        xo = s * 1.52
        body.box((xo - (0.0 if s > 0 else 0.02), 2.15, zf - 1.5), (xo + (0.02 if s > 0 else 0.0), 2.65, zf - 0.4), 'glass')
        m.cab(x0, x1, 1.2, 2.85, zf - 2.0, zf, part='body')
    body.box((-0.6, 1.2, zf - 2.2), (0.6, 2.2, zf - 0.1), 'ugreen')
    body.box((-1.4, 0.95, zf - 0.25), (1.4, 1.3, zf), 'dark')
    lights(body, zf, 1.1, 1.2)
    body.box((-1.52, 1.25, -6.5), (1.52, 1.6, zf - 2.0), 'ugreen')
    van(body, 2.3, zf - 2.05, 1.6, 2.5, 2.9, 'ugreen', door=False)
    lights(body, -6.5, 1.2, 1.2, front=False)
    for s in (-1, 1):
        for z in (-6.2, 2.0):
            body.box((s * 1.52 - (0.0 if s > 0 else 0.5), 0.0, z - 0.15), (s * 1.52 + (0.5 if s > 0 else 0.0), 0.15, z + 0.15), 'dark')
    # Erector at the back: canisters lie forward on the march, stand upright to fire.
    piv = (0, 2.0, -6.0)
    er = m.part('erector', piv)
    m.set_elevator(er, deploy=90)
    er.box((-1.3, 1.85, -6.2), (1.3, 2.05, 1.6), 'dgrey')
    n = 0
    for row in range(2):
        for col in range(2):
            x = (col - 0.5) * 1.12
            y = 2.62 + row * 1.1
            name = 'can_%d' % n
            c = er.part(name, (x, y, -6.0))
            canister(c, x, y, -6.1, 7.5, 1.0, 'ugreen')
            c.box((x - 0.52, y - 0.52, -4.5), (x + 0.52, y + 0.52, -4.3), 'dgrey')
            c.box((x - 0.52, y - 0.52, -0.5), (x + 0.52, y + 0.52, -0.3), 'dgrey')
            m.rail(name, (x, y, 1.6))
            n += 1
    axles(m, [5.6, 4.1, -1.0, -2.5], 0.78, 1.05, width=0.5, steer=2)
    m.seat('driver', -1.05, 1.9, 5.4)
    m.seat('gunner', 1.05, 1.9, 5.4)
    return m


def osa():
    """Osa-AKM (9A33): amphibious 6x6 with six missiles on its turret and its own radars."""
    m = Model('osa', paint='ugreen', seed=1005)
    m.width = 2.75
    m.wheelbase = 4.0
    m.camera = 12
    body = m.part('body')
    # Boat-shaped hull.
    body.box((-1.37, 0.55, -4.4), (1.37, 1.85, 3.9), 'ugreen')
    body.box((-1.2, 0.7, 3.9), (1.2, 1.7, 4.6), 'ugreen')
    body.box((-1.0, 0.9, 4.6), (1.0, 1.5, 4.9), 'ugreen')
    body.box((-1.25, 1.85, 1.6), (1.25, 2.35, 3.6), 'ugreen')
    body.box((-1.1, 2.0, 3.6), (1.1, 2.3, 3.63), 'glass')
    lights(body, 4.9, 1.2, 0.8)
    lights(body, -4.4, 1.4, 1.1, front=False)
    tz = -0.9
    tur = m.part('turret', (0, 1.85, tz))
    m.set_turret(tur)
    tur.box((-1.0, 1.85, tz - 1.0), (1.0, 2.9, tz + 1.0), 'ugreen')
    dish(tur, 0, 2.55, tz + 1.25, 1.2, 0.8, face='aesa', depth=0.25)
    tur.box((-0.12, 2.9, tz - 0.7), (0.12, 3.3, tz - 0.46), 'dgrey')
    sp = tur.part('search', (0, 3.3, tz - 0.58))
    m.spinner = 'search'
    sp.box((-1.0, 3.3, tz - 0.7), (1.0, 3.75, tz - 0.46), 'ugreen', sides={'front': 'mesh_dark', 'back': 'mesh_dark'})
    el = tur.part('launcher', (0, 2.4, tz - 0.4))
    m.set_elevator(el, deploy=25)
    el.box((-1.3, 2.3, tz - 0.5), (1.3, 2.5, tz - 0.3), 'dgrey')
    n = 0
    for s in (-1, 1):
        for k in range(3):
            x = s * 1.5
            y = 2.05 + k * 0.42
            name = 'missile_%d' % n
            mp = el.part(name, (x, y, tz - 1.5))
            missile(mp, x, y, tz - 1.5, 3.15, 0.21, body='white', nose='lgrey', fin_style='lgrey')
            el.box((x - 0.04 - s * 0.1, y - 0.04, tz - 1.2), (x + 0.04 - s * 0.1, y + 0.04, tz + 0.8), 'dgrey')
            m.rail(name, (x, y, tz + 1.65))
            n += 1
    axles(m, [3.0, 0.4, -1.8], 0.62, 1.0, width=0.42, steer=1)
    m.seat('driver', -0.5, 1.55, 2.7)
    m.seat('gunner', 0.5, 1.55, 2.7)
    return m


def strela10():
    """Strela-10M: MT-LB carrier with four infrared missile tubes on a small turret."""
    m = Model('strela10', paint='ugreen', seed=1006)
    m.width = 2.85
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    top = 0.75
    tracks(m, body, -3.3, 3.3, top, 2.85, 0.42, 6, 0.27, 'ugreen')
    body.box((-1.0, 0.4, -3.2), (1.0, top + 0.1, 3.2), 'ugreen')
    body.box((-1.42, top + 0.1, -3.2), (1.42, 1.65, 2.5), 'ugreen')
    body.box((-1.42, top + 0.1, 2.5), (1.42, 1.4, 3.35), 'ugreen')
    body.box((-1.2, 1.4, 2.5), (1.2, 1.5, 2.9), 'ugreen')
    lights(body, 3.35, 1.1, 1.1)
    lights(body, -3.2, 1.2, 1.1, front=False)
    tz = -0.4
    tur = m.part('turret', (0, 1.65, tz))
    m.set_turret(tur)
    tur.box((-0.75, 1.65, tz - 0.75), (0.75, 2.35, tz + 0.75), 'ugreen')
    tur.box((-0.3, 2.35, tz - 0.3), (0.3, 2.55, tz + 0.4), 'dgrey')
    tur.box((-0.18, 2.1, tz + 0.75), (0.18, 2.35, tz + 0.82), 'glass')
    el = tur.part('launcher', (0, 2.3, tz))
    m.set_elevator(el, deploy=30)
    el.box((-1.15, 2.22, tz - 0.08), (1.15, 2.38, tz + 0.08), 'dgrey')
    n = 0
    for s in (-1, 1):
        for k in range(2):
            x = s * (1.0 + k * 0.28)
            y = 2.45
            name = 'can_%d' % n
            c = el.part(name, (x, y, tz))
            canister(c, x, y, tz - 1.0, 2.2, 0.24, 'ugreen')
            m.rail(name, (x, y, tz + 1.3))
            n += 1
    m.seat('driver', -0.6, 1.2, 2.4)
    m.seat('gunner', 0.0, 2.0, tz)
    return m


def shilka():
    """ZSU-23-4 Shilka: four 23 mm guns in a big flat turret, radar dish on its mast."""
    m = Model('shilka', paint='rgreen', seed=1007)
    m.width = 3.1
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    top = 0.85
    tracks(m, body, -3.25, 3.25, top, 3.1, 0.45, 6, 0.3, 'rgreen')
    body.box((-1.1, 0.45, -3.2), (1.1, top + 0.1, 3.2), 'rgreen')
    body.box((-1.55, top + 0.1, -3.2), (1.55, 1.7, 2.3), 'rgreen')
    body.box((-1.55, top + 0.1, 2.3), (1.55, 1.45, 3.35), 'rgreen')
    lights(body, 3.35, 1.15, 1.2)
    lights(body, -3.2, 1.3, 1.2, front=False)
    tz = -0.3
    tur = m.part('turret', (0, 1.7, tz))
    m.set_turret(tur)
    tur.box((-1.45, 1.7, tz - 1.6), (1.45, 2.45, tz + 1.4), 'rgreen')
    tur.box((-1.0, 2.45, tz - 1.4), (1.0, 2.65, tz + 0.6), 'rgreen')
    tur.box((-0.12, 2.65, tz - 1.5), (0.12, 3.25, tz - 1.25), 'dgrey')
    sp = tur.part('radar', (0, 3.25, tz - 1.38))
    m.spinner = 'radar'
    sp.box((-0.6, 3.05, tz - 1.25), (0.6, 3.75, tz - 1.15), 'dgrey', sides={'front': 'aesa'})
    el = tur.part('guns', (0, 2.05, tz + 1.3))
    m.set_elevator(el, deploy=0)
    el.box((-0.9, 1.85, tz + 1.1), (0.9, 2.25, tz + 1.5), 'dgrey')
    for s, tag in ((-1, 'l'), (1, 'r')):
        for row in (0, 1):
            barrel(el, s * 0.55, 1.95 + row * 0.22, tz + 1.5, 1.9, 0.06)
        m.rail('barrel_' + tag, (s * 0.55, 2.05, tz + 3.4))
    m.seat('driver', -0.7, 1.3, 2.5)
    m.seat('gunner', 0.0, 2.3, tz)
    return m


def tunguska():
    """2S6M Tunguska: two 30 mm guns and eight missiles on one turret, search radar on top, tracking radar in front."""
    m = Model('tunguska', paint='rgreen', seed=1008)
    m.width = 3.25
    m.tracked = True
    m.camera = 12
    body = m.part('body')
    top = 0.9
    tracks(m, body, -3.9, 3.9, top, 3.25, 0.48, 6, 0.32, 'rgreen')
    body.box((-1.1, 0.45, -3.8), (1.1, top + 0.1, 3.8), 'rgreen')
    body.box((-1.62, top + 0.1, -3.8), (1.62, 1.8, 2.8), 'rgreen')
    body.box((-1.62, top + 0.1, 2.8), (1.62, 1.5, 3.95), 'rgreen')
    lights(body, 3.95, 1.2, 1.25)
    lights(body, -3.8, 1.3, 1.25, front=False)
    tz = -0.5
    tur = m.part('turret', (0, 1.8, tz))
    m.set_turret(tur)
    tur.box((-1.3, 1.8, tz - 1.7), (1.3, 3.0, tz + 1.3), 'rgreen')
    dish(tur, 0, 2.6, tz + 1.55, 1.1, 0.9, face='aesa', depth=0.25)
    tur.box((-0.12, 3.0, tz - 1.5), (0.12, 3.3, tz - 1.26), 'dgrey')
    sp = tur.part('search', (0, 3.3, tz - 1.38))
    m.spinner = 'search'
    sp.box((-1.0, 3.3, tz - 1.5), (1.0, 3.9, tz - 1.26), 'rgreen', sides={'front': 'mesh_dark', 'back': 'mesh_dark'})
    el = tur.part('weapons', (0, 2.5, tz))
    m.set_elevator(el, deploy=12)
    el.box((-1.45, 2.4, tz - 0.1), (1.45, 2.6, tz + 0.1), 'dgrey')
    n = 0
    for s, tag in ((-1, 'l'), (1, 'r')):
        cx = s * 1.75
        for row in range(2):
            for col in range(2):
                x = cx + (col - 0.5) * 0.28
                y = 2.6 + row * 0.3
                name = 'can_%d' % n
                c = el.part(name, (x, y, tz - 1.0))
                canister(c, x, y, tz - 1.0, 2.6, 0.24, 'rgreen')
                m.rail(name, (x, y, tz + 1.6))
                n += 1
        el.box((cx - 0.2, 2.05, tz - 0.6), (cx + 0.2, 2.35, tz + 0.5), 'dgrey')
        barrel(el, cx, 2.2, tz + 0.5, 2.0, 0.08)
        m.rail('barrel_' + tag, (cx, 2.2, tz + 2.5))
    m.seat('driver', -0.75, 1.3, 3.0)
    m.seat('gunner', 0.0, 2.6, tz)
    return m


def sampt():
    """SAMP/T "Mamba": Aster 30 launcher on a Renault Kerax 8x8, eight canisters raised upright."""
    m = Model('sampt', paint='camo', seed=1009)
    m.width = 2.55
    m.wheelbase = 6.5
    m.camera = 15
    body = m.part('body')
    zc = kamaz_8x8(m, body, 5.4, 11.0, 'camo', cab_h=3.05)
    body.box((-1.27, 1.1, -5.5), (1.27, 1.45, zc - 0.05), 'camo')
    body.box((-1.2, 1.45, zc - 1.3), (1.2, 2.6, zc - 0.1), 'camo')
    lights(body, -5.5, 1.2, 1.0, front=False)
    for s in (-1, 1):
        for z in (-5.2, 1.0):
            body.box((s * 1.27 - (0.0 if s > 0 else 0.5), 0.0, z - 0.15), (s * 1.27 + (0.5 if s > 0 else 0.0), 0.15, z + 0.15), 'dark')
    piv = (0, 1.75, -5.3)
    er = m.part('launcher', piv)
    m.set_elevator(er, deploy=90)
    er.box((-1.2, 1.55, -5.4), (1.2, 1.75, 1.6), 'dgrey')
    n = 0
    for row in range(2):
        for col in range(4):
            x = (col - 1.5) * 0.58
            y = 2.05 + row * 0.6
            name = 'can_%d' % n
            c = er.part(name, (x, y, -5.3))
            canister(c, x, y, -5.4, 6.5, 0.56, 'camo')
            m.rail(name, (x, y, 1.2))
            n += 1
    er.box((-1.25, 1.75, -4.9), (1.25, 3.0, -4.7), 'dgrey')
    er.box((-1.25, 1.75, 0.6), (1.25, 3.0, 0.8), 'dgrey')
    axles(m, [4.7, 3.3, -2.3, -3.7], 0.6, 1.0, width=0.45, steer=2)
    m.seat('driver', -0.6, 1.9, 4.2)
    m.seat('gunner', 0.6, 1.9, 4.2)
    return m


def iron_dome():
    """Iron Dome: the launcher of twenty Tamir interceptors (a box of 4 x 5 cells) on a 6x6 truck, desert sand; the
    box tilts up to 55 degrees to fire."""
    m = Model('iron_dome', paint='sand', seed=1011)
    m.width = 2.55
    m.wheelbase = 4.6
    m.camera = 13
    body = m.part('body')
    zc = cabover(body, 4.7, 2.55, 'sand', cab_len=1.9, cab_h=3.0, y0=1.1)
    frame(body, -4.3, zc + 0.2, 0.8, 1.1, half=0.5)
    body.box((-1.27, 1.1, -4.35), (1.27, 1.4, zc - 0.1), 'sand')
    # Hydraulics and the hinge block at the back, the power unit and cable reels behind the cab.
    body.box((-1.2, 1.4, zc - 1.1), (1.2, 2.5, zc - 0.15), 'sand')
    body.box((-0.9, 2.5, zc - 0.9), (0.9, 2.7, zc - 0.3), 'dgrey')
    body.box((-1.15, 1.4, -4.3), (1.15, 1.75, -3.7), 'dgrey')
    lights(body, -4.35, 1.2, 1.0, front=False)
    for s_ in (-1, 1):
        for z in (-4.0, zc - 0.4):
            body.box((s_ * 1.27 - (0.0 if s_ > 0 else 0.45), 0.0, z - 0.15), (s_ * 1.27 + (0.45 if s_ > 0 else 0.0), 0.15, z + 0.15), 'dark')
            body.box((s_ * 1.4 - 0.06, 0.15, z - 0.06), (s_ * 1.4 + 0.06, 1.2, z + 0.06), 'dark')
    piv = (0, 1.75, -4.0)
    er = m.part('launcher', piv)
    m.set_elevator(er, deploy=55)
    zf = zc - 1.3
    # The box: side walls, roof, the frame round the front; each cell a part with its cap (fired = open).
    er.box((-1.12, 1.75, -4.05), (1.12, 1.95, zf), 'sand')
    er.box((-1.12, 3.65, -4.05), (1.12, 3.85, zf), 'sand')
    for x in (-1.12, 0.98):
        er.box((x, 1.95, -4.05), (x + 0.14, 3.65, zf), 'sand')
    er.box((-0.98, 1.95, -4.05), (0.98, 3.65, -3.9), 'dgrey')
    er.box((-1.16, 1.71, zf), (1.16, 3.89, zf + 0.06), 'dgrey', faces=('front', 'back', 'left', 'right', 'top', 'bottom'))
    # Lifting arms either side.
    for x in (-1.2, 1.12):
        er.box((x, 2.0, -3.6), (x + 0.08, 2.6, -1.0), 'dgrey')
    n = 0
    for row in range(5):
        for col in range(4):
            x = (col - 1.5) * 0.46
            y = 2.15 + row * 0.33
            name = 'cell_%d' % n
            c = er.part(name, (x, y, -3.9))
            c.box((x - 0.2, y - 0.14, -3.9), (x + 0.2, y + 0.14, zf + 0.02), 'sand', sides={'front': 'dark'})
            c.box((x - 0.17, y - 0.12, zf + 0.02), (x + 0.17, y + 0.12, zf + 0.05), 'light' if (row + col) % 7 == 3 else 'lgrey')
            m.rail(name, (x, y, zf + 0.3))
            n += 1
    axles(m, [3.6, -2.0, -3.4], 0.6, 1.0, steer=1)
    m.seat('driver', -0.6, 1.9, 3.6)
    m.seat('gunner', 0.6, 1.9, 3.6)
    return m


def hmmwv(body, z0, paint, cargo=True):
    """M1097 Humvee from z0 (back) forward 4.6 m. Returns the z of the windscreen."""
    z1 = z0 + 4.6
    # Engine bay in front, the crew cab (one box, its floor low between the wheels), the cargo bed behind.
    body.box((-1.09, 0.45, z0 + 2.9), (1.09, 1.15, z1 - 0.1), paint)
    body.box((-1.09, 0.45, z0), (1.09, 1.15, z0 + 1.0), paint)
    body.box((-0.95, 1.15, z0 + 2.9), (0.95, 1.3, z1 - 0.15), paint)
    body.box((-1.0, 0.5, z1 - 0.1), (1.0, 1.05, z1), 'dark')
    lights(body, z1, 0.95, 0.75)
    body.box((-1.05, 0.45, z0 + 1.0), (1.05, 1.95, z0 + 2.9), paint)
    body.box((-0.9, 1.3, z0 + 2.9), (-0.05, 1.88, z0 + 2.93), 'glass')
    body.box((0.05, 1.3, z0 + 2.9), (0.9, 1.88, z0 + 2.93), 'glass')
    for s in (-1, 1):
        body.box((s * 1.05 - (0.02 if s > 0 else 0.01), 1.3, z0 + 1.3), (s * 1.05 + (0.01 if s > 0 else 0.02), 1.82, z0 + 2.7), 'glass')
    lights(body, z0, 0.8, 0.85, front=False)
    body.model.cab(-1.05, 1.05, 0.5, 1.95, z0 + 1.0, z0 + 2.9, part=body.name, hatch=False)
    return z0 + 2.9


def avenger():
    """M1097 Avenger: a Humvee with a turret of eight Stingers and a .50 machine gun."""
    m = Model('avenger', paint='camo', seed=1010)
    m.width = 2.2
    m.wheelbase = 3.3
    m.camera = 10
    body = m.part('body')
    hmmwv(body, -2.3, 'camo')
    body.box((-1.0, 1.15, -2.3), (1.0, 1.35, -1.3), 'camo')
    tz = -1.5
    tur = m.part('turret', (0, 1.35, tz))
    m.set_turret(tur)
    tur.box((-0.6, 1.35, tz - 0.6), (0.6, 2.35, tz + 0.6), 'camo')
    tur.box((-0.45, 2.0, tz + 0.6), (0.45, 2.3, tz + 0.63), 'glass')
    el = tur.part('pods', (0, 2.0, tz))
    m.set_elevator(el, deploy=20)
    el.box((-0.95, 1.92, tz - 0.08), (0.95, 2.08, tz + 0.08), 'dgrey')
    n = 0
    for s in (-1, 1):
        cx = s * 1.0
        el.box((cx - 0.22, 1.6, tz - 0.8), (cx + 0.22, 2.4, tz + 0.8), 'camo', sides={'front': 'dark'})
        for row in range(4):
            m.rail('pod_' + ('l' if s < 0 else 'r'), (cx, 1.7 + row * 0.2, tz + 1.0))
            n += 1
    el.box((0.55, 1.55, tz - 0.3), (0.7, 1.7, tz + 0.5), 'dark')
    barrel(el, 0.62, 1.62, tz + 0.5, 1.2, 0.05)
    axles(m, [1.65, -1.6], 0.46, 0.85, width=0.36, steer=1)
    m.seat('driver', -0.45, 1.05, 0.0)
    m.seat('gunner', 0.0, 1.6, tz)
    return m


def mfg():
    """Mobile fire group: a pickup with a heavy machine gun and a searchlight - the Shahed hunters."""
    m = Model('mfg', paint='olive', seed=1011)
    m.width = 1.85
    m.wheelbase = 3.1
    m.camera = 8
    body = m.part('body')
    z0, z1 = -2.65, 2.65
    body.box((-0.92, 0.45, 1.0), (0.92, 1.0, z1), 'olive')
    body.box((-0.92, 0.45, z0), (0.92, 1.0, -0.4), 'olive')
    body.box((-0.9, 1.0, 1.0), (0.9, 1.15, z1 - 0.05), 'olive')
    body.box((-0.92, 0.5, z1), (0.92, 0.95, z1 + 0.08), 'dark')
    lights(body, z1 + 0.08, 0.85, 0.65)
    # The cab: one box with its floor low, so there is room to sit inside.
    body.box((-0.92, 0.45, -0.4), (0.92, 1.85, 1.0), 'olive')
    body.box((-0.82, 1.18, 1.0), (0.82, 1.8, 1.03), 'glass')
    for s in (-1, 1):
        body.box((s * 0.92 - (0.02 if s > 0 else 0.01), 1.18, -0.2), (s * 0.92 + (0.01 if s > 0 else 0.02), 1.75, 0.85), 'glass')
    m.cab(-0.92, 0.92, 0.5, 1.85, -0.4, 1.0, part='body', hatch=False)
    # Open bed with sides.
    for s in (-1, 1):
        body.box((s * 0.92 - (0.06 if s > 0 else 0.0), 1.0, z0), (s * 0.92 + (0.0 if s < 0 else 0.0) + (0.0 if s > 0 else 0.06), 1.35, -0.4), 'olive')
    body.box((-0.92, 1.0, z0), (0.92, 1.35, z0 + 0.06), 'olive')
    lights(body, z0, 0.8, 0.75, front=False)
    tz = -1.5
    tur = m.part('mount', (0, 1.0, tz))
    m.set_turret(tur)
    tur.box((-0.08, 1.0, tz - 0.08), (0.08, 1.75, tz + 0.08), 'dark')
    el = tur.part('gun', (0, 1.8, tz))
    m.set_elevator(el, deploy=0)
    el.box((-0.1, 1.7, tz - 0.6), (0.1, 1.9, tz + 0.3), 'dark')
    barrel(el, 0, 1.82, tz + 0.3, 1.1, 0.05)
    el.box((-0.32, 1.68, tz - 0.2), (-0.12, 1.9, tz + 0.15), 'dgrey', sides={'front': 'light'})
    m.rail('barrel', (0, 1.82, tz + 1.4))
    m.rail('barrel_2', (0, 1.82, tz + 1.4))
    axles(m, [1.6, -1.5], 0.4, 0.78, width=0.3, steer=1)
    m.seat('driver', -0.4, 1.0, 0.4)
    m.seat('gunner', 0.0, 1.1, tz - 0.6)
    return m


def zu23():
    """ZU-23-2 twin 23 mm gun on the flatbed of a KamAZ truck."""
    m = Model('zu23', paint='ugreen', seed=1012)
    m.width = 2.5
    m.wheelbase = 4.2
    m.camera = 10
    body = m.part('body')
    zc = cabover(body, 3.5, 2.5, 'ugreen', cab_len=1.6, cab_h=2.9, y0=1.05)
    frame(body, -3.4, zc + 0.2, 0.8, 1.05, half=0.5)
    body.box((-1.25, 1.05, -3.5), (1.25, 1.2, zc - 0.15), 'ugreen')
    for s in (-1, 1):
        body.box((s * 1.25 - (0.05 if s > 0 else 0.0), 1.2, -3.5), (s * 1.25 + (0.05 if s < 0 else 0.0), 1.6, zc - 0.15), 'ugreen')
    body.box((-1.25, 1.2, -3.5), (1.25, 1.6, -3.45), 'ugreen')
    lights(body, -3.5, 1.0, 1.0, front=False)
    tz = -1.4
    tur = m.part('mount', (0, 1.2, tz))
    m.set_turret(tur)
    tur.box((-0.8, 1.2, tz - 0.8), (0.8, 1.45, tz + 0.8), 'ugreen')
    tur.box((-0.6, 1.45, tz - 0.5), (-0.45, 2.1, tz + 0.2), 'ugreen')
    tur.box((0.45, 1.45, tz - 0.5), (0.6, 2.1, tz + 0.2), 'ugreen')
    el = tur.part('guns', (0, 1.95, tz))
    m.set_elevator(el, deploy=0)
    el.box((-0.4, 1.8, tz - 0.9), (0.4, 2.1, tz + 0.4), 'ugreen')
    for s, tag in ((-1, 'l'), (1, 'r')):
        barrel(el, s * 0.22, 1.95, tz + 0.4, 1.9, 0.06)
        m.rail('barrel_' + tag, (s * 0.22, 1.95, tz + 2.3))
    axles(m, [2.6, -1.0, -2.3], 0.6, 1.0, steer=1)
    m.seat('driver', -0.55, 1.75, 2.4)
    m.seat('gunner', 0.0, 1.6, tz - 0.7)
    return m


def all_models():
    return [pantsir(), tor(), buk(), s300(), osa(), strela10(), shilka(), tunguska(), sampt(), avenger(), mfg(), zu23(), iron_dome()]
