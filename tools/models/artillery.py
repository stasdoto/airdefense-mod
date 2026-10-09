"""
1.30 "Artillery": the 2S19 Msta-S and M109A6 Paladin self-propelled howitzers, the BM-21 Grad rocket launcher on its
Ural, and the counter-battery radars - Zoopark-1M on an MT-LBu and the AN/TPQ-36 on a Humvee's trailer. Metres,
x right, y up, z forward (the nose).
"""
from boxgen import Model
from kit import axles, frame, hood_cab, tracks, lights
from armor import glacis, tank_hull


def howitzer_gun(m, tur, pivot, length, dia, paint, brake=0.5, sleeve=1.6, evacuator=True):
    """The elevating mass of a howitzer: cradle with the recoil cylinders, the tube, the bore evacuator, the muzzle
    brake; the launch point (rail) at the muzzle. Laid level for travel, up to 70 degrees to fire."""
    el = tur.part('gun', pivot)
    m.set_elevator(el, deploy=45, fixed=0)
    px, py, pz = pivot
    r = dia / 2
    # Mantlet and cradle with the recoil cylinders above the tube.
    el.box((px - 0.55, py - 0.42, pz - 0.35), (px + 0.55, py + 0.42, pz + 0.3), paint)
    el.box((px - 0.38, py - 0.3, pz + 0.3), (px + 0.38, py + 0.34, pz + sleeve), paint)
    for s in (-1, 1):
        el.box((px + s * 0.17 - 0.08, py + 0.3, pz + 0.3), (px + s * 0.17 + 0.08, py + 0.46, pz + sleeve + 0.15), 'dgrey')
    z = pz + sleeve
    el.box((px - r, py - r, z), (px + r, py + r, z + length), 'dgrey')
    if evacuator:
        el.box((px - r * 1.55, py - r * 1.55, z + length * 0.38), (px + r * 1.55, py + r * 1.55, z + length * 0.55), 'dgrey')
    # Muzzle brake: a block with the side ports.
    zb = z + length
    el.box((px - r * 1.9, py - r * 1.5, zb), (px + r * 1.9, py + r * 1.5, zb + brake), 'dark')
    el.box((px - r * 2.05, py - r * 1.1, zb + brake * 0.2), (px + r * 2.05, py + r * 1.1, zb + brake * 0.45), 'black')
    el.box((px - r * 2.05, py - r * 1.1, zb + brake * 0.6), (px + r * 2.05, py + r * 1.1, zb + brake * 0.85), 'black')
    m.rail('barrel', (px, py, zb + brake))
    return el


# ------------------------------------------------------------------------------------------------------------------
# Self-propelled howitzers


def msta_s():
    """2S19 Msta-S: a 152 mm 2A64 howitzer in a big box turret on a T-80/T-72 hull."""
    m = Model('msta_s', paint='ugreen', seed=3001)
    m.width = 3.58
    m.tracked = True
    m.camera = 12
    body = m.part('body')
    zf, zb = tank_hull(m, body, 6.9, 3.58, 0.95, 1.6, 'ugreen')
    # Stowage boxes on the fenders, the unditching log and the drums at the back.
    for s in (-1, 1):
        body.box((s * 1.55 - 0.22, 1.0, -2.6), (s * 1.55 + 0.22, 1.35, 0.6), 'ugreen')
    body.box((-1.45, 1.6, zb - 0.45), (1.45, 1.9, zb - 0.1), 'rust')
    tz = -0.6
    tur = m.part('turret', (0, 1.6, tz))
    m.set_turret(tur)
    # The turret: tall, square, overhanging the hull sides a little; its front slopes back.
    tur.box((-1.62, 1.62, tz - 2.5), (1.62, 2.85, tz + 1.3), 'ugreen')
    tur.box((-1.5, 1.62, tz + 1.3), (1.5, 2.6, tz + 1.75), 'ugreen')
    tur.box((-1.45, 2.85, tz - 2.3), (1.45, 2.97, tz + 1.0), 'ugreen')
    # The ammunition conveyor door at the back, stowage baskets on the sides.
    tur.box((-0.9, 1.75, tz - 2.62), (0.9, 2.55, tz - 2.5), 'ugreen')
    for s in (-1, 1):
        tur.box((s * 1.62, 2.0, tz - 2.2), (s * 1.62 + s * 0.25, 2.55, tz - 0.6), 'mesh_dark')
    # Commander's cupola with the 12.7 mm machine gun, the loader's hatch, sights.
    tur.box((0.45, 2.97, tz - 1.0), (1.15, 3.17, tz - 0.3), 'ugreen')
    tur.box((0.75, 3.17, tz - 0.75), (0.82, 3.25, tz + 0.4), 'dark')
    tur.box((-1.0, 2.97, tz - 1.2), (-0.4, 3.07, tz - 0.6), 'ugreen')
    tur.box((-1.1, 2.97, tz + 0.4), (-0.75, 3.22, tz + 0.75), 'ugreen', sides={'front': 'glass'})
    # Smoke grenade launchers on the front corners.
    for s in (-1, 1):
        tur.box((s * 1.25 - 0.3, 2.45, tz + 1.55), (s * 1.25 + 0.3, 2.7, tz + 1.8), 'dgrey')
    howitzer_gun(m, tur, (0, 2.25, tz + 1.75), 5.9, 0.2, 'ugreen', brake=0.6, sleeve=1.5)
    m.seat('driver', 0.0, 1.0, 2.3)
    m.seat('gunner', 0.8, 2.75, tz - 0.65)
    return m


def m109():
    """M109A6 Paladin: a 155 mm M284 howitzer in a boxy turret on an aluminium hull, its travel lock on the glacis."""
    m = Model('m109', paint='nato', seed=3002)
    m.width = 3.15
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    z0, z1 = -3.1, 3.1
    tracks(m, body, z0, z1, 0.85, 3.15, 0.4, 7, 0.33, 'nato')
    body.box((-1.15, 0.45, z0 + 0.2), (1.15, 0.9, z1 - 0.3), 'nato')
    body.box((-1.57, 0.9, z0), (1.57, 1.9, z1 - 1.3), 'nato')
    glacis(body, z1 - 1.3, z1 + 0.05, 0.75, 1.9, 1.52, 'nato', steps=4)
    # Driver's hatch (front left), the engine grille (front right), the rear door.
    body.box((-1.2, 1.9, z1 - 1.75), (-0.55, 2.0, z1 - 1.3), 'nato')
    body.box((0.25, 1.85, z1 - 1.25), (1.35, 1.88, z1 - 0.5), 'mesh_dark')
    body.box((-0.6, 0.95, z0 - 0.07), (0.6, 1.8, z0), 'nato')
    lights(body, z1 - 0.15, 1.2, 1.2)
    lights(body, z0, 1.3, 1.2, front=False)
    # The travel lock: an A-frame on the glacis the barrel rests in when the turret is straight.
    for s in (-1, 1):
        body.box((s * 0.3 - 0.05, 1.35, z1 - 0.55), (s * 0.3 + 0.05, 2.3, z1 - 0.45), 'dgrey')
    body.box((-0.35, 2.2, z1 - 0.58), (0.35, 2.3, z1 - 0.42), 'dgrey')
    tz = -1.2
    tur = m.part('turret', (0, 1.9, tz))
    m.set_turret(tur)
    tur.box((-1.52, 1.9, tz - 2.2), (1.52, 2.95, tz + 1.6), 'nato')
    # Bustle at the back with stowage racks, the hatches, the commander's .50 cal.
    tur.box((-1.4, 2.0, tz - 2.75), (1.4, 2.85, tz - 2.2), 'nato')
    tur.box((-1.45, 2.05, tz - 2.95), (1.45, 2.1, tz - 2.75), 'dgrey')
    tur.box((0.45, 2.95, tz - 0.9), (1.1, 3.1, tz - 0.25), 'nato')
    tur.box((0.75, 3.1, tz - 0.7), (0.8, 3.18, tz + 0.5), 'dark')
    tur.box((-1.1, 2.95, tz - 0.9), (-0.5, 3.05, tz - 0.3), 'nato')
    # The panoramic sight and the side doors.
    tur.box((-1.25, 2.95, tz + 0.7), (-0.95, 3.25, tz + 1.0), 'nato', sides={'front': 'glass'})
    for s in (-1, 1):
        tur.box((s * 1.52, 2.15, tz - 1.0), (s * 1.52 + s * 0.04, 2.8, tz - 0.1), 'dgrey')
    howitzer_gun(m, tur, (0, 2.3, tz + 1.6), 5.1, 0.18, 'nato', brake=0.55, sleeve=1.3)
    m.seat('driver', -0.85, 1.25, 2.3)
    m.seat('gunner', 0.8, 2.85, tz - 0.6)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Rocket artillery


def bm21():
    """BM-21 Grad: forty 122 mm tubes (four rows of ten) on a turntable behind the cab of a Ural-375D."""
    m = Model('bm21', paint='rgreen', seed=3003)
    m.width = 2.4
    m.wheelbase = 4.2
    m.camera = 12
    body = m.part('body')
    frame(body, -3.6, 3.4, 0.75, 1.0)
    hood_cab(body, 3.7, 2.4, 'rgreen', hood_len=1.4, hood_h=1.85, cab_len=1.35, cab_h=2.6)
    # Platform behind the cab, the stabiliser jacks at the back, a spare wheel and the crew's boxes.
    body.box((-1.2, 1.0, -3.65), (1.2, 1.2, 0.6), 'rgreen')
    for s in (-1, 1):
        body.box((s * 1.05 - 0.1, 0.35, -3.5), (s * 1.05 + 0.1, 1.0, -3.3), 'dark')
        body.box((s * 1.2 - (0.0 if s > 0 else 0.25), 1.2, -0.6), (s * 1.2 + (0.25 if s > 0 else 0.0), 1.55, 0.5), 'rgreen')
    body.box((-0.45, 1.2, 0.15), (0.45, 2.0, 0.55), 'dark')
    lights(body, -3.65, 1.05, 1.0, front=False)
    tz = -1.9
    tur = m.part('turret', (0, 1.2, tz))
    m.set_turret(tur)
    tur.box((-0.75, 1.2, tz - 0.75), (0.75, 1.5, tz + 0.75), 'dgrey')
    tur.box((-0.95, 1.5, tz - 0.9), (0.95, 1.65, tz + 0.9), 'rgreen')
    # The cradle's side frames up to the trunnions at the back.
    for s in (-1, 1):
        tur.box((s * 0.95 - 0.1, 1.65, -3.2), (s * 0.95 + 0.1, 2.05, tz + 0.2), 'rgreen')
    piv = (0, 2.05, -3.0)
    pack = tur.part('pack', piv)
    m.set_elevator(pack, deploy=40, fixed=0)
    # The pack: 4 x 10 tubes 3 m long, banded by frames.
    d = 0.165
    x0 = -d * 4.5
    y0 = 2.12
    zp0, zp1 = -3.15, -0.15
    for row in range(4):
        for col in range(10):
            x = x0 + col * d
            y = y0 + row * d
            pack.box((x - 0.07, y - 0.07, zp0), (x + 0.07, y + 0.07, zp1), 'rgreen', sides={'front': 'black', 'back': 'dark'})
    for zz in (zp0 + 0.3, zp0 + 1.5, zp1 - 0.35):
        pack.box((x0 - 0.12, y0 - 0.12, zz), (x0 + 9 * d + 0.12, y0 + 3 * d + 0.12, zz + 0.12), 'dgrey')
    for col in (0, 3, 6, 9):
        for row in (0, 3):
            m.rail('pack', (x0 + col * d, y0 + row * d, zp1))
    for col in (1, 4, 7, 8):
        m.rail('pack', (x0 + col * d, y0 + 1.5 * d, zp1))
    axles(m, [2.75, -0.9, -2.3], 0.62, 0.98)
    m.seat('driver', -0.5, 1.45, 1.55)
    m.seat('gunner', 0.5, 1.45, 1.55)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Counter-battery radars


def zoopark():
    """Zoopark-1M (1L219M): a big flat array folded on the roof of an MT-LBu, raised upright to look ahead."""
    m = Model('zoopark', paint='ugreen', seed=3004)
    m.width = 2.85
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    z0, z1 = -3.6, 3.6
    tracks(m, body, z0, z1, 0.75, 2.85, 0.4, 6, 0.3, 'ugreen')
    body.box((-1.0, 0.4, z0 + 0.2), (1.0, 0.8, z1 - 0.3), 'ugreen')
    body.box((-1.42, 0.8, z0), (1.42, 1.87, z1 - 1.1), 'ugreen')
    glacis(body, z1 - 1.1, z1 + 0.05, 0.55, 1.87, 1.38, 'ugreen', steps=4)
    # The crew cab's hatches with their vision blocks, the rear doors, the generator and the mast.
    for s in (-1, 1):
        body.box((s * 0.65 - 0.4, 1.87, z1 - 2.0), (s * 0.65 + 0.4, 1.97, z1 - 1.3), 'ugreen')
        body.box((s * 0.65 - 0.3, 1.6, z1 - 1.12), (s * 0.65 + 0.3, 1.75, z1 - 1.08), 'glass')
    for s in (-1, 1):
        body.box((s * 0.55 - 0.4, 0.95, z0 - 0.06), (s * 0.55 + 0.4, 1.75, z0), 'ugreen')
    body.box((0.75, 1.87, z0 + 0.2), (1.35, 2.35, z0 + 1.0), 'dgrey')
    body.box((-1.3, 1.87, z0 + 0.3), (-1.22, 3.4, z0 + 0.38), 'dark')
    lights(body, z1 - 0.2, 1.1, 1.0)
    # The array: hinged at the back of the roof, lying forward over it on the march.
    hz = -2.6
    for s in (-1, 1):
        body.box((s * 1.1 - 0.12, 1.87, hz - 0.2), (s * 1.1 + 0.12, 2.25, hz + 0.2), 'dgrey')
    piv = (0, 2.12, hz)
    arr = m.part('array', piv)
    m.set_elevator(arr, deploy=80, fixed=0)
    arr.box((-1.3, 2.12, hz + 0.1), (1.3, 2.4, hz + 4.3), 'ugreen', sides={'bottom': 'aesa'})
    arr.box((-1.38, 2.08, hz + 0.05), (1.38, 2.14, hz + 4.35), 'dgrey')
    arr.box((-0.6, 2.4, hz + 1.0), (0.6, 2.62, hz + 3.0), 'ugreen')
    m.seat('driver', -0.65, 1.0, z1 - 1.75)
    m.seat('gunner', 0.65, 1.0, z1 - 1.75)
    return m


def tpq36():
    """AN/TPQ-36 Firefinder: the shelter on a Humvee, the antenna on its trailer, raised to look back."""
    m = Model('tpq36', paint='camo', seed=3005)
    m.width = 2.2
    m.wheelbase = 3.3
    m.camera = 12
    body = m.part('body')
    hz0, hz1 = 0.3, 4.9
    body.box((-1.09, 0.45, 3.2), (1.09, 1.15, hz1 - 0.1), 'camo')
    body.box((-1.09, 0.45, hz0), (1.09, 1.15, 1.25), 'camo')
    body.box((-0.95, 1.15, 3.2), (0.95, 1.3, hz1 - 0.15), 'camo')
    body.box((-1.0, 0.5, hz1 - 0.1), (1.0, 1.05, hz1), 'dark')
    lights(body, hz1, 0.95, 0.75)
    body.box((-1.05, 0.45, 1.25), (1.05, 1.98, 3.2), 'camo')
    body.box((-0.9, 1.3, 3.2), (-0.05, 1.9, 3.23), 'glass')
    body.box((0.05, 1.3, 3.2), (0.9, 1.9, 3.23), 'glass')
    for s in (-1, 1):
        body.box((s * 1.05 - (0.02 if s > 0 else 0.01), 1.3, 1.5), (s * 1.05 + (0.01 if s > 0 else 0.02), 1.85, 3.0), 'glass')
    m.cab(-1.05, 1.05, 0.5, 1.98, 1.25, 3.2, part='body', hatch=False)
    # The operations shelter on the bed.
    body.box((-1.05, 1.15, hz0 - 0.05), (1.05, 2.45, 1.22), 'camo')
    body.box((-0.4, 1.3, hz0 - 0.08), (0.4, 2.2, hz0 - 0.05), 'dark')
    body.box((-0.08, 0.55, -0.7), (0.08, 0.65, hz0), 'dark')
    # The antenna trailer.
    tz0, tz1 = -4.6, -0.7
    body.box((-1.0, 0.65, tz0), (1.0, 1.0, tz1), 'camo')
    body.box((-0.9, 1.0, -1.5), (0.9, 1.5, tz1), 'camo')
    lights(body, tz0, 0.75, 0.8, front=False)
    pz = -4.1
    for s in (-1, 1):
        body.box((s * 0.85 - 0.1, 1.0, pz - 0.15), (s * 0.85 + 0.1, 1.45, pz + 0.15), 'dgrey')
    piv = (0, 1.35, pz)
    arr = m.part('array', piv)
    m.set_elevator(arr, deploy=75, fixed=0)
    arr.box((-1.15, 1.35, pz + 0.05), (1.15, 1.6, pz + 2.45), 'camo', sides={'top': 'aesa'})
    arr.box((-1.0, 1.6, pz + 2.45), (1.0, 1.72, pz + 2.6), 'camo', sides={'top': 'aesa_light'})
    axles(m, [4.0, 0.75], 0.46, 0.85, width=0.36, steer=1)
    m.wheel('trailer_l', -0.98, 0.46, -2.6, 0.46, 0.36, side='wheel')
    m.wheel('trailer_r', 0.98, 0.46, -2.6, 0.46, 0.36, side='wheel')
    m.seat('driver', -0.45, 1.05, 2.1)
    m.seat('gunner', 0.45, 1.05, 2.1)
    return m


def all_models():
    return [msta_s(), m109(), bm21(), zoopark(), tpq36()]
