"""
1.34 "Drones and EW": the launchers of the reconnaissance drones and the loitering munitions, and the electronic
warfare vehicles. Orlan-10 (its catapult on a KamAZ), the Bayraktar TB2's ground control station (with two TB2 in their
crates on the trailer), the Lancet's catapult truck, the Switchblade 600 launch tubes on a JLTV, the Borisoglebsk-2
(an MT-LBu with the antenna mast) and the Bukovel-AD (on a pickup). Metres, x right, y up, z forward.
"""
from boxgen import Model
from kit import axles, frame, lights, cabover, van, tracks
from airdefense import canister, dish


def orlan_drone(part, x, y, z, paint='lgrey'):
    """The Orlan-10 sitting on its catapult: the slim fuselage, the high straight wing, the twin booms with the tail,
    the pusher propeller."""
    part.box((x - 0.11, y, z - 0.75), (x + 0.11, y + 0.22, z + 0.85), paint)
    part.box((x - 0.08, y + 0.02, z + 0.85), (x + 0.08, y + 0.18, z + 1.0), 'dgrey')
    part.box((x - 1.55, y + 0.2, z + 0.1), (x + 1.55, y + 0.25, z + 0.42), paint)
    for s in (-1, 1):
        part.box((x + s * 0.45 - 0.03, y + 0.12, z - 1.1), (x + s * 0.45 + 0.03, y + 0.18, z + 0.1), paint)
        part.box((x + s * 0.45 - 0.02, y + 0.15, z - 1.1), (x + s * 0.45 + 0.02, y + 0.45, z - 0.9), paint)
    part.box((x - 0.5, y + 0.42, z - 1.1), (x + 0.5, y + 0.46, z - 0.9), paint)
    part.box((x - 0.35, y + 0.08, z - 0.8), (x + 0.35, y + 0.14, z - 0.77), 'dark')


def orlan():
    """Orlan-10 complex: a KamAZ-5350 with the pneumatic catapult rail on its back, the drone ready on it, the second
    in its box."""
    m = Model('orlan', paint='rgreen', seed=3401)
    m.width = 2.55
    m.wheelbase = 4.2
    m.camera = 12
    body = m.part('body')
    zc = cabover(body, 3.6, 2.5, 'rgreen', cab_len=1.8, cab_h=2.9, y0=1.1)
    frame(body, -3.8, zc + 0.2, 0.8, 1.1, half=0.48)
    body.box((-1.2, 1.1, -3.9), (1.2, 1.3, zc - 0.1), 'rgreen')
    # The operators' box behind the cab, the catapult rail rising towards the back.
    van(body, zc - 2.0, zc - 0.1, 1.3, 2.9, 2.4, 'rgreen', door=False)
    dish(body, 0.7, 3.2, zc - 1.0, 0.6, 0.6, face='aesa', depth=0.1)
    body.box((0.65, 2.9, zc - 1.05), (0.75, 3.2, zc - 0.95), 'dgrey')
    piv = (0, 1.4, -3.6)
    cat = body.part('catapult', piv)
    m.set_elevator(cat, deploy=12, fixed=6)
    cat.box((-0.18, 1.3, -3.9), (0.18, 1.5, -0.4), "dgrey")
    cat.box((-0.3, 1.25, -3.9), (0.3, 1.32, -0.4), "dark")
    cat.box((-0.25, 1.5, -0.8), (0.25, 1.7, -0.45), "dgrey")
    uav = cat.part('uav_0', piv)
    orlan_drone(uav, 0, 1.55, -1.2)
    m.rail('uav_0', (0, 1.75, 0.4))
    crate = body.part('box_1', (0, 1.3, 0))
    crate.box((-1.1, 1.3, -3.8), (-0.35, 2.1, -1.4), 'olive')
    m.rail('box_1', (0, 1.75, 0.4))
    lights(body, -3.9, 1.0, 1.0, front=False)
    axles(m, [2.5, -1.5, -2.8], 0.6, 1.0)
    m.seat('driver', -0.55, 1.55, 2.6)
    m.seat('gunner', 0.55, 1.55, 2.6)
    return m


def tb2_gcs():
    """Bayraktar TB2 ground control station: the shelter truck with its data-link dish, and on the trailer behind the
    two TB2 in their transport crates (in the game the drone goes up from the road in front of the station)."""
    m = Model('tb2_gcs', paint='sand', seed=3402)
    m.width = 2.55
    m.wheelbase = 4.0
    m.camera = 14
    body = m.part('body')
    zc = cabover(body, 4.2, 2.5, 'sand', cab_len=1.7, cab_h=2.9, y0=1.1)
    frame(body, -2.4, zc + 0.2, 0.8, 1.1, half=0.48)
    van(body, -2.4, zc - 0.15, 1.2, 3.3, 2.5, 'sand', vents=2)
    # The satellite dish and the line-of-sight data link on the roof.
    body.box((-0.1, 3.3, 0.0), (0.1, 3.9, 0.2), 'dgrey')
    dish(body, 0.0, 4.3, 0.1, 1.2, 1.2, face='radome', depth=0.25)
    body.box((-0.9, 3.3, -2.0), (-0.7, 4.8, -1.8), 'dgrey')
    body.box((-1.0, 4.8, -2.1), (-0.6, 5.2, -1.7), 'lgrey')
    # The trailer with two crates.
    body.box((-0.12, 0.7, -3.6), (0.12, 0.85, -2.4), 'dark')
    body.box((-1.2, 0.85, -9.6), (1.2, 1.05, -3.6), 'sand')
    for i, x in enumerate((-0.6, 0.6)):
        crate = body.part('crate_%d' % i, (0, 1.05, 0))
        crate.box((x - 0.55, 1.05, -9.4), (x + 0.55, 1.95, -3.8), 'olive')
        crate.box((x - 0.56, 1.5, -9.41), (x + 0.56, 1.56, -3.79), 'dark')
    m.set_virtual_elevator('runway', (0, 1.2, 6.0), 4.0, 4.0)
    m.rail('crate_0', (0, 1.6, 7.0), at=4.0)
    m.rail('crate_1', (0, 1.6, 7.0), at=4.0)
    lights(body, -9.6, 0.9, 1.0, front=False)
    axles(m, [3.0, -1.0, -2.0], 0.6, 1.0)
    m.wheel('trailer_l', -1.1, 0.5, -6.6, 0.5, 0.36)
    m.wheel('trailer_r', 1.1, 0.5, -6.6, 0.5, 0.36)
    m.seat('driver', -0.55, 1.55, 3.2)
    m.seat('gunner', 0.55, 1.55, 3.2)
    return m


def lancet_drone(part, x, y, z, paint='lgrey'):
    """A Lancet-3: the round body, two sets of X wings, the pusher propeller at the tail."""
    part.box((x - 0.1, y - 0.1, z - 0.8), (x + 0.1, y + 0.1, z + 0.85), paint)
    part.box((x - 0.06, y - 0.06, z + 0.85), (x + 0.06, y + 0.06, z + 0.98), 'dark')
    for zz, span in ((0.35, 0.55), (-0.55, 0.5)):
        part.box((x - span, y - 0.015, z + zz - 0.12), (x + span, y + 0.015, z + zz + 0.12), paint)
        part.box((x - 0.015, y - span, z + zz - 0.12), (x + 0.015, y + span, z + zz + 0.12), paint)
    part.box((x - 0.22, y - 0.02, z - 0.83), (x + 0.22, y + 0.02, z - 0.8), 'dark')


def lancet():
    """Lancet: a KamAZ with the catapult rail and the containers of the loitering munitions."""
    m = Model('lancet', paint='rgreen', seed=3403)
    m.width = 2.55
    m.wheelbase = 4.2
    m.camera = 12
    body = m.part('body')
    zc = cabover(body, 3.6, 2.5, 'rgreen', cab_len=1.8, cab_h=2.9, y0=1.1)
    frame(body, -3.8, zc + 0.2, 0.8, 1.1, half=0.48)
    body.box((-1.2, 1.1, -3.9), (1.2, 1.3, zc - 0.1), 'rgreen')
    piv = (0, 1.4, -3.6)
    cat = body.part('catapult', piv)
    m.set_elevator(cat, deploy=15, fixed=8)
    cat.box((-0.15, 1.3, -3.9), (0.15, 1.48, 0.5), 'dgrey')
    cat.box((-0.25, 1.48, 0.0), (0.25, 1.62, 0.4), 'dgrey')
    uav = cat.part('uav_0', piv)
    lancet_drone(uav, 0, 1.68, -0.7)
    m.rail('uav_0', (0, 1.68, 0.6))
    for i, (x0, y0) in enumerate(((-1.15, 1.3), (0.4, 1.3), (-1.15, 1.72))):
        box = body.part('box_%d' % (i + 1), (0, 1.3, 0))
        box.box((x0, y0, -3.7), (x0 + 0.75, y0 + 0.4, -1.6), 'olive')
        box.box((x0 - 0.01, y0 + 0.17, -3.71), (x0 + 0.76, y0 + 0.23, -1.59), 'dark')
        m.rail('box_%d' % (i + 1), (0, 1.68, 0.6))
    lights(body, -3.9, 1.0, 1.0, front=False)
    axles(m, [2.5, -1.5, -2.8], 0.6, 1.0)
    m.seat('driver', -0.55, 1.55, 2.6)
    m.seat('gunner', 0.55, 1.55, 2.6)
    return m


def switchblade():
    """Switchblade 600 on a JLTV: a pod of six launch tubes on the bed that rises to fire."""
    m = Model('switchblade', paint='sand', seed=3404)
    m.width = 2.5
    m.wheelbase = 3.6
    m.camera = 10
    body = m.part('body')
    body.box((-1.2, 0.8, 1.6), (1.2, 1.7, 3.2), 'sand')
    body.box((-1.15, 1.7, 2.2), (1.15, 1.9, 3.15), 'sand')
    body.box((-1.2, 0.8, -0.2), (1.2, 2.35, 1.6), 'sand')
    body.box((-1.05, 1.75, 1.6), (1.05, 2.25, 1.63), 'glass')
    for s in (-1, 1):
        body.box((s * 1.2 - (0.0 if s > 0 else 0.03), 1.7, 0.1), (s * 1.2 + (0.03 if s > 0 else 0.0), 2.2, 1.3), 'glass')
    m.cab(-1.2, 1.2, 0.8, 2.35, -0.2, 1.6, kind='truck')
    body.box((-1.25, 0.75, -3.0), (1.25, 1.1, -0.2), 'sand')
    body.box((-1.0, 0.85, 3.2), (1.0, 1.25, 3.3), 'dark')
    lights(body, 3.25, 1.3, 1.0)
    lights(body, -3.0, 1.0, 1.0, front=False)
    piv = (0, 1.15, -2.8)
    pod = body.part('pod', piv)
    m.set_elevator(pod, deploy=45, fixed=0)
    pod.box((-0.8, 1.1, -2.9), (0.8, 1.15, -0.5), 'dgrey')
    k = 0
    for row in range(2):
        for col in range(3):
            x = -0.5 + col * 0.5
            y = 1.38 + row * 0.46
            canister(pod, x, y, -2.9, 2.3, 0.4, 'sand')
            cap = pod.part('tube_%d' % k, piv)
            cap.box((x - 0.18, y - 0.18, -0.6), (x + 0.18, y + 0.18, -0.55), 'dark')
            m.rail('tube_%d' % k, (x, y, -0.45))
            k += 1
    axles(m, [2.2, -1.4], 0.55, 1.0)
    m.seat('driver', -0.5, 1.2, 0.8)
    m.seat('gunner', 0.5, 1.2, 0.8)
    return m


def borisoglebsk():
    """Borisoglebsk-2 (R-330BMV): the MT-LBu with its equipment box and the telescopic antenna mast that stands up."""
    m = Model('borisoglebsk', paint='rgreen', seed=3405)
    m.width = 2.85
    m.camera = 11
    m.tracked = True
    body = m.part('body')
    body.box((-1.35, 0.45, -3.2), (1.35, 1.5, 3.1), 'rgreen')
    for i in range(3):
        k = (i + 1) / 4
        body.box((-1.35 * (1 - k * 0.15), 0.45 + 0.2 * i, 3.1 + 0.15 * i), (1.35 * (1 - k * 0.15), 1.5, 3.25 + 0.15 * i), 'rgreen')
    body.box((-1.3, 1.5, -3.0), (1.3, 2.75, 0.8), 'rgreen')
    body.box((-1.1, 1.5, 1.3), (-0.2, 2.0, 2.4), 'rgreen')
    body.box((-1.0, 1.75, 2.4), (-0.3, 1.95, 2.43), 'glass')
    m.cab(-1.3, 1.3, 0.6, 1.5, 0.9, 2.9, kind='armour')
    for x, h in ((-0.9, 1.0), (0.9, 0.8), (0.0, 1.3)):
        body.box((x - 0.04, 2.75, -2.6), (x + 0.04, 2.75 + h, -2.52), 'dark')
    tracks(m, body, -3.2, 3.1, 1.0, 2.85, 0.42, 6, 0.3, 'rgreen')
    piv = (0, 2.75, -2.2)
    mast = body.part('mast', piv)
    m.set_elevator(mast, deploy=90, fixed=0)
    mast.box((-0.12, 2.65, -2.3), (0.12, 2.9, 3.3), 'dgrey')
    for z, w in ((0.6, 1.4), (1.8, 1.1), (3.0, 0.8)):
        mast.box((-w, 2.7, z - 0.05), (w, 2.85, z + 0.05), 'dark')
        for s in (-1, 1):
            mast.box((s * w - 0.06, 2.6, z - 0.06), (s * w + 0.06, 2.95, z + 0.06), 'lgrey')
    mast.box((-0.35, 2.6, 3.3), (0.35, 3.0, 3.7), 'lgrey')
    lights(body, 3.55, 1.3, 1.0)
    m.seat('driver', -0.6, 1.0, 1.9)
    m.seat('gunner', 0.6, 1.0, 1.9)
    return m


def bukovel():
    """Bukovel-AD: a pickup with the jammer's box in the bed, its omnidirectional antennas and the mast with the
    direction-finding array."""
    m = Model('bukovel', paint='ugreen', seed=3406)
    m.width = 2.0
    m.wheelbase = 3.1
    m.camera = 9
    body = m.part('body')
    body.box((-0.95, 0.55, 1.2), (0.95, 1.25, 2.7), 'ugreen')
    body.box((-0.95, 0.55, -0.3), (0.95, 1.9, 1.2), 'ugreen')
    body.box((-0.85, 1.3, 1.2), (0.85, 1.8, 1.23), 'glass')
    for s in (-1, 1):
        body.box((s * 0.95 - (0.0 if s > 0 else 0.03), 1.3, -0.1), (s * 0.95 + (0.03 if s > 0 else 0.0), 1.75, 1.0), 'glass')
    m.cab(-0.95, 0.95, 0.55, 1.9, -0.3, 1.2, kind='truck')
    body.box((-0.95, 0.55, -2.4), (0.95, 0.95, -0.3), 'ugreen')
    for s in (-1, 1):
        body.box((s * 0.95 - (0.06 if s > 0 else 0.0), 0.95, -2.4), (s * 0.95 + (0.0 if s > 0 else 0.06), 1.3, -0.3), 'ugreen')
    body.box((-0.8, 0.95, -2.2), (0.8, 1.7, -0.6), 'dgrey')
    for x in (-0.6, -0.2, 0.2, 0.6):
        body.box((x - 0.03, 1.7, -0.9), (x + 0.03, 2.4, -0.84), 'dark')
    lights(body, 2.7, 0.95, 0.7)
    lights(body, -2.4, 0.9, 0.75, front=False)
    piv = (0, 1.7, -2.0)
    mast = body.part('mast', piv)
    m.set_elevator(mast, deploy=90, fixed=0)
    mast.box((-0.06, 1.65, -2.05), (0.06, 1.77, 0.6), 'dgrey')
    mast.box((-0.45, 1.6, 0.6), (0.45, 1.85, 1.0), 'aesa')
    axles(m, [1.6, -1.5], 0.42, 0.82, width=0.3)
    m.seat('driver', -0.4, 0.95, 0.4)
    m.seat('gunner', 0.4, 0.95, 0.4)
    return m


def all_models():
    return [orlan(), tb2_gcs(), lancet(), switchblade(), borisoglebsk(), bukovel()]
