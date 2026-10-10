"""1.40: the airliners at the airports - a narrow-body jet (an A320-like twin) and a regional turboprop (an ATR-72-like
high wing). Metres, x right, y up, z forward; wheels on the ground at y = 0. Drawn by the client only
(client.nation.PlaneRenderer): no items."""
import math

from boxgen import Model
from aircraft import loft, wing, fin


def windows(body, z0, z1, y0, y1, r, step=0.9, w=0.42):
    z = z0
    while z + w <= z1:
        for s in (-1, 1):
            body.box((s * r - (0.0 if s > 0 else 0.03), y0, z), (s * r + (0.03 if s > 0 else 0.0), y1, z + w), 'window')
        z += step


def wheels(body, x, z, r, pair=True, strut=1.0):
    for dx in ((-0.22, 0.22) if pair else (0.0,)):
        body.box((x + dx - 0.12, 0.0, z - r), (x + dx + 0.12, 2 * r, z + r), 'wheel_grey')
    body.box((x - 0.06, r, z - 0.06), (x + 0.06, r + strut, z + 0.06), 'steel')


def airliner():
    m = Model('airliner', paint='white', seed=4001)
    m.no_item = True
    body = m.part('body')
    yc = 3.9
    r = 1.98
    st = [(18.8, 3.55, 0.25, 0.25), (18.3, 3.6, 0.95, 0.95), (17.2, 3.75, 1.6, 1.65), (15.6, 3.88, 1.95, 1.95), (14.0, yc, r, r),
          (-8.0, yc, r, r), (-12.5, 4.25, 1.55, 1.6), (-16.5, 4.8, 0.85, 0.95), (-18.8, 5.15, 0.3, 0.35)]
    loft(body, st, 'white', steps=1)
    # The belly a little grey, the blue cheatline under the windows.
    body.box((-1.2, yc - r - 0.01, -8.0), (1.2, yc - r + 0.3, 14.0), 'lgrey')
    for s in (-1, 1):
        body.box((s * r - (0.0 if s > 0 else 0.025), 3.55, -8.0), (s * r + (0.025 if s > 0 else 0.0), 3.75, 14.5), 'liner_blue')
    windows(body, -9.5, 13.5, 4.2, 4.6, r + 0.005)
    # The flight deck's windows.
    for s in (-1, 1):
        body.box((s * 1.45 - 0.2, 4.35, 16.6), (s * 1.45 + 0.2, 4.85, 17.3), 'window')
    body.box((-1.1, 4.4, 17.25), (1.1, 4.85, 17.45), 'window')
    # Doors (outlined grey).
    for z in (14.6, -15.2):
        for s in (-1, 1):
            body.box((s * r - (0.0 if s > 0 else 0.035), 2.8, z - 0.45), (s * r + (0.035 if s > 0 else 0.0), 4.75, z + 0.45), 'lgrey')
    # Wings: low, swept, with winglets; the engines under them.
    for s in (-1, 1):
        wing(body, s * 1.9, s * 17.0, 2.45, 3.4, -5.6, 6.6, 1.6, 0.55, 'white', steps=6, dihedral=0.085)
        fin(body, -6.0, -6.6, 2.45 + 0.085 * 17.0, 2.45 + 0.085 * 17.0 + 1.8, 1.6, 0.8, 0.12, 'liner_blue', steps=2, x=s * 17.05)
        x = s * 5.7
        nac = [(4.6, 1.75, 0.92, 0.92), (3.9, 1.75, 1.05, 1.05), (1.0, 1.75, 1.0, 1.0), (-0.6, 1.8, 0.6, 0.6)]
        loft(body, nac, 'lgrey', steps=1, x=x)
        body.box((x - 0.8, 0.95, 4.62), (x + 0.8, 2.55, 4.65), 'dark')
        body.box((x - 0.12, 2.6, -0.8), (x + 0.12, 2.95, 3.2), 'lgrey')
    # The tail: the tailplane, the fin (blue).
    for s in (-1, 1):
        wing(body, s * 0.6, s * 6.4, 5.1, -14.4, -17.6, 3.6, 1.4, 0.3, 'white', steps=3)
    fin(body, -12.6, -17.6, 5.4, 11.9, 5.6, 2.0, 0.34, 'liner_blue', steps=5)
    # The gear: the main legs under the wings, the nose leg.
    for s in (-1, 1):
        wheels(body, s * 3.8, -1.2, 0.58, strut=1.3)
    wheels(body, 0, 13.2, 0.38, strut=1.5)
    # Lights: the beacons (red), the wingtips (red left, green right), white tail light.
    body.box((-0.15, yc + r, -2.0), (0.15, yc + r + 0.15, -1.6), 'redlight')
    body.box((-17.1, 2.45 + 1.45, -5.2), (-16.9, 2.45 + 1.6, -4.9), 'redlight')
    body.box((16.9, 2.45 + 1.45, -5.2), (17.1, 2.45 + 1.6, -4.9), 'green_light')
    return m


def turboprop():
    m = Model('turboprop', paint='white', seed=4002)
    m.no_item = True
    body = m.part('body')
    yc = 2.35
    r = 1.38
    st = [(13.6, 2.15, 0.25, 0.25), (13.1, 2.2, 0.8, 0.8), (12.0, 2.3, 1.25, 1.3), (10.5, yc, r, r), (-6.5, yc, r, r),
          (-10.5, 2.8, 0.9, 0.95), (-13.6, 3.2, 0.3, 0.35)]
    loft(body, st, 'white', steps=1)
    for s in (-1, 1):
        body.box((s * r - (0.0 if s > 0 else 0.025), 1.95, -6.5), (s * r + (0.025 if s > 0 else 0.0), 2.1, 11.0), 'liner_red')
    windows(body, -6.0, 10.0, 2.45, 2.8, r + 0.005, step=0.8, w=0.38)
    for s in (-1, 1):
        body.box((s * 1.0 - 0.2, 2.65, 11.9), (s * 1.0 + 0.2, 3.05, 12.5), 'window')
    body.box((-0.8, 2.7, 12.45), (0.8, 3.05, 12.6), 'window')
    # The high wing on top of the fuselage, straight; the engines hanging under it with their propellers.
    for s in (-1, 1):
        wing(body, s * 0.6, s * 13.5, 3.85, 2.2, 1.6, 2.6, 1.5, 0.4, 'white', steps=5, dihedral=0.0)
        x = s * 4.1
        nac = [(3.6, 3.4, 0.45, 0.5), (3.0, 3.4, 0.6, 0.7), (-1.2, 3.45, 0.55, 0.6), (-2.6, 3.55, 0.3, 0.3)]
        loft(body, nac, 'white', steps=1, x=x)
        body.box((x - 0.2, 3.2, 3.6), (x + 0.2, 3.6, 3.95), 'dark')
        # The propeller: six blades seen still (a dark cross and a second turned one).
        body.box((x - 1.95, 3.32, 3.98), (x + 1.95, 3.48, 4.05), 'dark')
        body.box((x - 0.08, 1.45, 3.98), (x + 0.08, 5.35, 4.05), 'dark')
        body.box((x - 1.4, 2.0, 3.99), (x + 1.4, 2.12, 4.04), 'dark')
        body.box((x - 1.4, 4.7, 3.99), (x + 1.4, 4.82, 4.04), 'dark')
        # The main gear in pods on the fuselage's sides.
        body.box((s * 1.3 - 0.5 * (s < 0), 0.75, -1.4), (s * 1.3 + 0.5 * (s > 0), 1.6, 0.8), 'white')
        wheels(body, s * 1.6, -0.3, 0.45, strut=0.4)
    # The T-tail: the fin, the tailplane on top.
    fin(body, -8.2, -12.6, 3.3, 7.5, 4.0, 2.2, 0.3, 'liner_red', steps=4)
    for s in (-1, 1):
        wing(body, s * 0.2, s * 3.8, 7.45, -11.0, -12.4, 2.0, 1.1, 0.22, 'white', steps=2)
    wheels(body, 0, 11.0, 0.32, strut=0.9)
    body.box((-0.13, yc + r, 0.0), (0.13, yc + r + 0.12, 0.35), 'redlight')
    return m


def all_models():
    return [airliner(), turboprop()]
