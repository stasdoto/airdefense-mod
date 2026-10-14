"""
2.0: the town's vehicles as real meshes - the traffic (saloon, hatchback, the old boxy saloon, SUV, taxi), the city
bus, trolleybus and tram, the emergency services (police car, ambulance, fire engine). Built by
tools/mesh/build_meshes.py m_town.
"""
import numpy as np

import geom
import kit_town as K
import materials
import town_cars as TC
import town_bus as TB
import town_svc as TS
from core import Geo


def saloon():
    """A modern saloon ~4.5 m (Vesta / Octavia class), painted white and tinted per car by the client."""
    return TC.build_car(TC.CarSpec(id='car_saloon'))


def hatch():
    """A hatchback ~3.9 m (Polo / Fabia class)."""
    return TC.build_car(TC.CarSpec(
        id='car_hatch', zf=1.95, zr=-1.95, width=1.72, front_axle=1.2, rear_axle=-1.25, wheel_r=0.30, tyre_w=0.195, track=0.74,
        sill=0.29, bump_f=0.2, bump_r=0.22, hood_front=0.72, belt_f=0.90, belt_r=0.97, tail_top=0.97, roof=1.45,
        cowl_z=0.6, ws_top_z=-0.2, roof_end_z=-1.22, deck_z=-1.74, gh_end=-1.74, b_z=-0.38, c_z=-1.12, q_z=-1.5, roof_hw=0.6,
        nose_len=0.5, tail_len=0.2, tail_p=3.0, back_k=1.1, hatch=True, doors=4, rear_seat_z=-0.9, driver=(-0.38, 0.5, -0.02)))


def boxy():
    """The old square Soviet saloon (VAZ-2107 style): chrome bumpers, square lamps, steel wheels."""
    return TC.build_car(TC.CarSpec(
        id='car_boxy', zf=2.075, zr=-2.075, width=1.62, front_axle=1.25, rear_axle=-1.2, wheel_r=0.29, tyre_w=0.175, track=0.7,
        sill=0.30, bump_f=0.34, bump_r=0.34, hood_front=0.80, belt_f=0.88, belt_r=0.90, tail_top=0.92, roof=1.42, cowl_z=0.75,
        ws_top_z=-0.05, roof_end_z=-0.95, deck_z=-1.7, gh_end=-1.7, b_z=-0.3, c_z=-0.98, roof_hw=0.62, rr=0.03, rs=0.035, crown=0.02,
        tumble=0.02, tuck=0.03, nose_len=0.25, nose_p=5.0, tail_len=0.25, tail_p=5.0, boxy=True, lamps='old', bumper='chrome',
        wheel_style='steel', tinted=True, paint=0xEDEDED, rear_seat_z=-0.85, driver=(-0.36, 0.48, -0.0)))


def suv():
    """A tall SUV ~4.7 m: roof rails, arch cladding, tinted glass; the paint is neutral so the client tints it."""
    return TC.build_car(TC.CarSpec(
        id='car_suv', zf=2.35, zr=-2.35, width=1.9, front_axle=1.5, rear_axle=-1.45, wheel_r=0.38, tyre_w=0.255, track=0.82,
        sill=0.4, bump_f=0.3, bump_r=0.3, hood_front=0.96, belt_f=1.1, belt_r=1.12, tail_top=1.15, roof=1.76, cowl_z=0.85,
        ws_top_z=-0.15, roof_end_z=-1.8, deck_z=-2.09, gh_end=-2.09, b_z=-0.5, c_z=-1.38, q_z=-1.8, roof_hw=0.72,
        nose_len=0.6, tail_len=0.25, hatch=True, cladding=True, roof_rails=True, tinted=True, rear_seat_z=-1.1,
        driver=(-0.4, 0.66, -0.15)))


def taxi_extra(m, s, B, C, body, inside):
    zc = -0.5
    y = B.gh_y(zc) + s.crown
    g = Geo(40)
    c = np.array([0.0, y, zc])
    g.extend(K.hbox(c + [-0.28, 0.0, -0.09], c + [0.28, 0.05, 0.09], 'trim', r=0.01))
    g.extend(K.hbox(c + [-0.3, 0.05, -0.1], c + [0.3, 0.2, 0.1], 'tl_taxi_sign', r=0.012, faces=('right', 'left', 'top', 'bottom')))
    for sg in (1, -1):
        g.extend(geom.panel(c + [0, 0.125, sg * 0.1], 0.6, 0.15, (-sg, 0, 0), (0, 1, 0), 'tl_taxi_sign'))
    body.add(g)
    # A chequer band along both flanks (the doors and wings), and a small ТАКСИ roundel on the doors.
    dark = (22, 22, 22, 255)
    light = (245, 245, 240, 255)
    hw = B.hw
    zc2 = (s.zf + s.zr) / 2
    L = s.zf - s.zr
    for side in (-1, 1):
        cv = K.Canvas((side * hw, 0.7, zc2), (0, 0, -side * L), (0, 1.2, 0), ppm=220)
        n = 0
        cell = 0.05
        z = s.front_axle - 0.5
        x = side * hw
        while z > s.rear_axle + 0.5:
            for row in range(2):
                col = dark if (n + row) % 2 == 0 else light
                cv.rect((x, 0.69 - row * cell + cell, z), (x, 0.69 - row * cell, z - cell), col)
            n += 1
            z -= cell
        m.decals.append(cv.decal(depth=0.3, region=lambda P: (P[:, 1] > 0.55) & (P[:, 1] < 0.85)))


def taxi():
    """A yellow saloon taxi with a roof sign and checker stripes."""
    s = TC.CarSpec(id='car_taxi', paint=0xF0B800, tinted=False, dirt=0.12)
    m = TC.build_car(s, extra=taxi_extra)
    return m


def side_canvas(s, B, side, y_c=0.7, h=1.2):
    hw = B.hw
    zc = (s.zf + s.zr) / 2
    L = s.zf - s.zr
    return K.Canvas((side * hw, y_c, zc), (0, 0, -side * L), (0, h, 0), ppm=220), side * hw, zc, L


def police_extra(m, s, B, C, body, inside):
    # The light bar on the roof (lamps at +-0.48 m, y ~1.6, z -0.1 as the game flashes them).
    zc = -0.1
    y = B.gh_y(zc) + s.crown - 0.005
    body.add(K.light_bar((0.0, y, zc), 1.3, depth=0.3, h=0.15))
    blue = (20, 62, 150, 255)
    white = (245, 245, 245, 255)
    for side in (-1, 1):
        cv, x, zc2, L = side_canvas(s, B, side)
        # A blue band under the windows with the lettering, running round the arches.
        zf0, zr0 = s.front_axle - 0.1, s.rear_axle + 0.1
        cv.rect((x, 0.78, zf0), (x, 0.52, zr0), blue)
        cv.rect((x, 0.52, zf0 + 0.1), (x, 0.47, zr0 - 0.1), white)
        cv.text((x, 0.65, (s.b_z + s.cowl_z) / 2 - 0.1), 1.25, 0.2, 'ПОЛІЦІЯ', white[:3])
        cv.text((x, 0.65, s.rear_axle + 0.55), 0.5, 0.15, '102', white[:3])
        m.decals.append(cv.decal(depth=0.3, region=lambda P: (P[:, 1] > 0.4) & (P[:, 1] < 0.85)))
    # The bonnet: a blue chevron stripe; the boot lid: text.
    cv = K.Canvas((0, 1.0, (s.cowl_z + s.zf) / 2), (-B.hw * 2.2, 0, 0), (0, 0, s.zf - s.cowl_z + 0.2), ppm=220)
    cv.rect((-0.18, 1.0, s.zf), (0.18, 1.0, s.cowl_z + 0.15), blue)
    d = cv.decal(depth=0.8, region=lambda P: (P[:, 2] > s.cowl_z - 0.02) & (P[:, 1] < s.belt_f + 0.03))
    d.n = np.array([0, 1.0, 0])
    m.decals.append(d)


def police():
    """The police saloon (drivable): the box model's size and axles, a light bar over the roof, the livery."""
    s = TC.CarSpec(
        id='police_car', zf=2.46, zr=-2.29, width=1.85, front_axle=1.4, rear_axle=-1.4, wheel_r=0.33, tyre_w=0.22, track=0.78,
        cowl_z=0.85, ws_top_z=-0.08, roof_end_z=-1.0, deck_z=-1.65, b_z=-0.42, c_z=-1.12, nose_len=0.55,
        wheel_names=('wheel_1_l', 'wheel_1_r', 'wheel_2_l', 'wheel_2_r'), tinted=False, dirt=0.1, rear_seat_z=-0.95, paint=0xF2F3F4,
        roof=1.45, steering=((-0.4, 0.9, 0.15), (-24.0, 0.0, 0.0)), driver=(-0.4, 0.52, 0.1), seat_mat='seat', plate_text=None)
    return TC.build_car(s, extra=police_extra)


def seat_rows(zs, cells, left=(-0.95, -0.55), right=(0.55, 0.95)):
    rows = []
    for z in zs:
        xs = list(left)
        if not any(lo - 0.15 < z < hi + 0.15 for (hi, lo, k) in cells if k == 'd'):
            xs += list(right)
        rows.append((z, xs))
    return rows


def bus():
    cells = [(4.85, 3.85, 'w'), (3.85, 2.7, 'w'), (2.7, 1.5, 'd'), (1.5, 0.2, 'w'), (0.2, -1.0, 'd'), (-1.0, -2.3, 'w'), (-2.3, -3.5, 'w'),
             (-3.5, -4.75, 'w')]
    s = TB.TransitSpec(id='town_bus', zf=5.5, zr=-5.5, paint=0xE3A700, second=0xF1EFE6, cells=cells,
                       wheels=[(3.6, 0.5, 1.05, 0.3, True), (-2.6, 0.5, 1.05, 0.3, False)], driver=(-0.6, 4.35),
                       seat_rows=seat_rows([-4.3, -3.55, -2.8, -2.05, -1.4, -0.55, 0.5, 1.15, 2.0, 3.2], cells))
    return TB.build_transit(s, extra=bus_extra)


def bus_extra(m, s, B, C, body, inside):
    g = Geo(40)
    y = s.roof + s.crown
    if s.ac:
        g.extend(K.hbox((-0.85, y - 0.02, -4.6), (0.85, y + 0.26, -3.0), 'steel', r=0.08))
        for k in range(5):
            g.extend(K.hbox((-0.7, y + 0.255, -4.45 + k * 0.3), (0.7, y + 0.275, -4.3 + k * 0.3), 'tl_vent'))
    body.add(g)


def trolleybus():
    cells = [(5.15, 4.0, 'w'), (4.0, 2.9, 'w'), (2.9, 1.7, 'd'), (1.7, 0.4, 'w'), (0.4, -0.8, 'd'), (-0.8, -2.0, 'w'), (-2.0, -3.3, 'w'),
             (-3.3, -4.2, 'w'), (-4.2, -5.05, 'w')]
    s = TB.TransitSpec(id='trolleybus', zf=5.8, zr=-5.8, paint=0x2056A8, second=0xF1EFE6, cells=cells,
                       wheels=[(3.8, 0.5, 1.05, 0.3, True), (-2.7, 0.5, 1.05, 0.3, False)], driver=(-0.6, 4.65), ac=False,
                       seat_rows=seat_rows([-4.6, -3.85, -3.1, -2.35, -1.6, -0.9, -0.1, 0.9, 1.7, 2.5, 3.5], cells))
    return TB.build_transit(s, extra=trolley_extra)


POLE_PIVOT_Z = -2.3


def trolley_extra(m, s, B, C, body, inside):
    g = Geo(40)
    y = s.roof + s.crown
    # Roof gear: resistor boxes, the pole bases on insulators.
    g.extend(K.hbox((-0.8, y - 0.02, -1.4), (0.8, y + 0.3, 3.0), 'steel', r=0.07))
    for k in range(7):
        g.extend(K.hbox((-0.65, y + 0.295, -1.2 + k * 0.6), (0.65, y + 0.315, -1.05 + k * 0.6), 'tl_vent'))
    for sx in (-0.25, 0.25):
        g.extend(K.hbox((sx - 0.1, y - 0.02, POLE_PIVOT_Z - 0.1), (sx + 0.1, y + 0.16, POLE_PIVOT_Z + 0.2), 'tl_pole', r=0.03))
    body.add(g)
    # The two poles, from the back of the roof up and back to the wires (as the box model: pivot (+-0.25, 3.3, -2.3), tilted -46).
    for sx, name in ((-0.25, 'pole_l'), (0.25, 'pole_r')):
        pole = body.part(name, (sx, 3.3, POLE_PIVOT_Z), (-46.0, 0.0, 0.0))
        pg = Geo(40)
        pg.extend(geom.tube([(sx, 3.3, POLE_PIVOT_Z), (sx, 3.3, POLE_PIVOT_Z - 4.95)], 0.032, 'tl_pole', n=6))
        pg.extend(geom.cyl((sx, 3.3, POLE_PIVOT_Z - 0.35), (sx, 3.3, POLE_PIVOT_Z - 0.55), 0.05, 'tl_pole', n=8))
        pg.extend(K.hbox((sx - 0.07, 3.23, POLE_PIVOT_Z - 5.12), (sx + 0.07, 3.39, POLE_PIVOT_Z - 4.88), 'tl_pole', r=0.02))
        pg.extend(K.hbox((sx - 0.045, 3.25, POLE_PIVOT_Z - 5.12), (sx + 0.045, 3.37, POLE_PIVOT_Z - 5.0), 'metal_dark', r=0.01))
        pole.add(pg)


def tram():
    cells = [(6.35, 5.1, 'w'), (5.1, 3.0, 'w'), (3.0, 1.6, 'd'), (1.6, 0.0, 'w'), (0.0, -1.6, 'w'), (-1.6, -3.0, 'd'), (-3.0, -5.1, 'w'),
             (-5.1, -6.35, 'w')]
    s = TB.TransitSpec(id='tram', zf=7.0, zr=-7.0, paint=0xB3261E, second=0xE9DFC5, cells=cells, ybot=0.3, belt_lo=1.0, win_lo=1.05,
                       win_hi=2.5, top_hi=2.68, roof=3.1, floor=0.7, hw=1.25, driver=(-0.6, 5.7), ac=False,
                       wheels=[(4.6, 0.42, 0.75, 0.24, False), (3.4, 0.42, 0.75, 0.24, False), (-3.4, 0.42, 0.75, 0.24, False),
                               (-4.6, 0.42, 0.75, 0.24, False)],
                       seat_rows=seat_rows([-5.8, -5.0, -4.2, -2.4, -0.8, 0.0], cells))
    return TB.build_transit(s, extra=tram_extra)


def tram_extra(m, s, B, C, body, inside):
    g = Geo(40)
    y = s.roof + s.crown
    for z in (4.2, -4.2):
        g.extend(K.hbox((-0.8, y - 0.02, z - 0.7), (0.8, y + 0.22, z + 0.7), 'steel', r=0.07))
    g.extend(K.hbox((-0.5, y - 0.02, -0.5), (0.5, y + 0.14, 0.5), 'tl_pole', r=0.03))
    # Bogie frames and axles between the wheel pairs (seen through the arches).
    for zc in (4.0, -4.0):
        for sd in (-1, 1):
            g.extend(K.hbox((sd * 0.62 - 0.05, 0.2, zc - 0.62), (sd * 0.62 + 0.05, 0.46, zc + 0.62), 'metal_dark', r=0.02))
        for z in (zc - 0.6, zc + 0.6):
            g.extend(geom.cyl((-0.75, 0.42, z), (0.75, 0.42, z), 0.04, 'steel', n=8))
    body.add(g)
    pz = 0.0
    arm = body.part('pantograph', (0.0, 3.25, pz), (-57.0, 0.0, 0.0))
    ag = Geo(40)
    for sx in (-0.25, 0.25):
        ag.extend(geom.tube([(sx, 3.25, pz), (sx, 3.25, pz - 2.2)], 0.025, 'tl_pole', n=5))
        ag.extend(geom.tube([(sx, 3.25, pz - 2.2), (sx * 0.5, 3.25, pz - 4.2)], 0.025, 'tl_pole', n=5))
    ag.extend(geom.tube([(-0.6, 3.25, pz - 4.25), (0.6, 3.25, pz - 4.25)], 0.03, 'tl_pole', n=6))
    ag.extend(K.hbox((-0.6, 3.27, pz - 4.35), (0.6, 3.31, pz - 4.15), 'metal_dark', r=0.008))
    arm.add(ag)


def models():
    return [saloon(), hatch(), boxy(), suv(), taxi(), police(), bus(), trolleybus(), tram(), TS.ambulance(), TS.fire_truck()]
