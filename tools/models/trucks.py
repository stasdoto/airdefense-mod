"""Logistics trucks (stage R7): a fuel tanker and a supply truck, both on the Ural-4320. Metres."""
from boxgen import Model
from kit import axles, frame, hood_cab, lights
from aircraft import taper


def fuel_truck():
    m = Model('fuel_truck', paint='ugreen', seed=4001)
    m.width = 2.5
    m.wheelbase = 4.2
    m.camera = 12
    body = m.part('body')
    frame(body, -3.6, 3.4, 0.75, 1.0)
    zc = hood_cab(body, 3.7, 2.4, 'ugreen', hood_len=1.4, hood_h=1.85, cab_len=1.35, cab_h=2.6)
    # The tank: a long rounded cylinder on cradles, a walkway and hatches on top.
    body.box((-1.0, 1.0, -3.5), (1.0, 1.15, zc - 0.2), 'dark')
    for hw, y0, y1 in ((1.12, 1.45, 2.65), (0.88, 1.2, 2.9), (0.5, 1.1, 3.0)):
        body.box((-hw, y0, -3.5), (hw, y1, zc - 0.3), 'ugreen')
    body.box((-0.95, 1.2, -3.55), (0.95, 2.9, -3.45), 'ugreen')
    body.box((-0.95, 1.2, zc - 0.35), (0.95, 2.9, zc - 0.25), 'ugreen')
    body.box((-0.3, 3.0, -2.0), (0.3, 3.08, zc - 0.6), 'dgrey')
    for z in (-1.4, -0.2):
        body.box((-0.25, 2.98, z - 0.25), (0.25, 3.15, z + 0.25), 'dgrey')
    # Hose box and the warning plates.
    body.box((-1.2, 0.9, -3.6), (1.2, 1.4, -3.3), 'dgrey')
    body.box((-0.4, 1.6, -3.62), (0.4, 1.9, -3.6), 'orange')
    lights(body, -3.62, 1.0, 1.0, front=False)
    axles(m, [2.75, -0.9, -2.3], 0.62, 0.98)
    m.seat('driver', -0.5, 1.45, 1.55)
    m.seat('gunner', 0.5, 1.45, 1.55)
    return m


def supply_truck():
    m = Model('supply_truck', paint='ugreen', seed=4002)
    m.width = 2.5
    m.wheelbase = 4.2
    m.camera = 12
    body = m.part('body')
    frame(body, -3.6, 3.4, 0.75, 1.0)
    zc = hood_cab(body, 3.7, 2.4, 'ugreen', hood_len=1.4, hood_h=1.85, cab_len=1.35, cab_h=2.6)
    # Flatbed with drop sides and a canvas cover on hoops.
    body.box((-1.25, 1.05, -3.65), (1.25, 1.2, zc - 0.15), 'ugreen')
    for s in (-1, 1):
        body.box((s * 1.25 - (0.06 if s > 0 else 0.0), 1.2, -3.65), (s * 1.25 + (0.0 if s > 0 else 0.06), 1.7, zc - 0.15), 'ugreen')
    body.box((-1.25, 1.2, -3.65), (1.25, 1.7, -3.59), 'ugreen')
    body.box((-1.3, 1.7, -3.7), (1.3, 3.1, zc - 0.1), 'canvas')
    body.box((-1.1, 3.1, -3.6), (1.1, 3.3, zc - 0.2), 'canvas')
    # Ammunition crates showing at the back.
    for x in (-0.7, 0.0, 0.7):
        body.box((x - 0.3, 1.2, -3.55), (x + 0.3, 1.6, -3.0), 'olive')
    lights(body, -3.7, 1.0, 1.0, front=False)
    axles(m, [2.75, -0.9, -2.3], 0.62, 0.98)
    m.seat('driver', -0.5, 1.45, 1.55)
    m.seat('gunner', 0.5, 1.45, 1.55)
    return m


def all_models():
    return [fuel_truck(), supply_truck()]
