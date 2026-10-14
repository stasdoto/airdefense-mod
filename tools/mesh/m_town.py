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
        wheel_style='steel', tinted=False, paint=0xE8E3D0, rear_seat_z=-0.85, driver=(-0.36, 0.48, -0.0)))


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


def models():
    return [saloon(), hatch(), boxy(), suv(), taxi()]
