"""Helicopters and planes (stage R6). Metres, x right, y up, z forward; wheels on the ground at y = 0."""
from boxgen import Model
from kit import lights
from airdefense import barrel, canister


def taper(part, z0, z1, y, r0, r1, paint, steps=4, wy=None):
    """A round-ish body from z0 (radius r0) to z1 (radius r1), in steps."""
    for i in range(steps):
        za = z0 + (z1 - z0) * i / steps
        zb = z0 + (z1 - z0) * (i + 1) / steps
        r = r0 + (r1 - r0) * (i + 0.5) / steps
        h = r * (wy if wy else 1.0)
        part.box((-r, y - h, za), (r, y + h, zb), paint)


def rotor(m, parent, pivot, radius, blades, paint='dark', name='rotor', chord=0.5):
    """Main rotor: hub and blades, turning about y (the spinner)."""
    r = m.part(name, pivot, parent=parent) if parent is None else parent.part(name, pivot)
    x, y, z = pivot
    r.box((x - 0.35, y - 0.25, z - 0.35), (x + 0.35, y + 0.1, z + 0.35), 'dgrey')
    for i in range(blades):
        b = r.part('%s_blade_%d' % (name, i), pivot, (0, 360.0 * i / blades, 0))
        b.box((x - chord / 2, y - 0.04, z + 0.3), (x + chord / 2, y + 0.04, z + radius), paint)
    return r


def wing(part, x_root, x_tip, y, z_lead_root, z_lead_tip, chord_root, chord_tip, thick, paint, steps=5):
    """A tapered, swept wing from x_root to x_tip (either side), in steps."""
    for i in range(steps):
        t0 = i / steps
        t1 = (i + 1) / steps
        xa = x_root + (x_tip - x_root) * t0
        xb = x_root + (x_tip - x_root) * t1
        tm = (t0 + t1) / 2
        zl = z_lead_root + (z_lead_tip - z_lead_root) * tm
        c = chord_root + (chord_tip - chord_root) * tm
        part.box((min(xa, xb), y - thick / 2, zl - c), (max(xa, xb), y + thick / 2, zl), paint)


def gear(m, x, z, r, steer=False, name=None, strut=1.0):
    n = name or 'gear_%d_%d' % (int(x * 10), int(z * 10))
    m.wheel(n, x, r, z, r, 0.25, steer=steer, side='wheel_grey', tread='tire')
    body = m.parts[0]
    body.box((x - 0.06, r, z - 0.06), (x + 0.06, r + strut, z + 0.06), 'dgrey')


# ------------------------------------------------------------------------------------------------------------------
# Helicopters


def mi8():
    m = Model('mi8', paint='ugreen', seed=3001)
    m.width = 2.5
    m.camera = 20
    body = m.part('body')
    y = 1.9
    # Cabin and the glazed nose.
    body.box((-1.15, 0.7, -2.5), (1.15, 3.1, 5.2), 'ugreen')
    taper(body, 5.2, 7.3, 1.95, 1.1, 0.5, 'ugreen', steps=3)
    body.box((-0.95, 2.0, 5.5), (0.95, 2.9, 6.6), 'glass')
    for s in (-1, 1):
        for z in (-1.2, 0.2, 1.6, 3.0):
            body.box((s * 1.15 - (0.0 if s > 0 else 0.02), 2.0, z), (s * 1.15 + (0.02 if s > 0 else 0.0), 2.5, z + 0.6), 'glass')
    # Engines on the roof.
    body.box((-0.95, 3.1, 0.0), (0.95, 3.85, 5.0), 'ugreen')
    body.box((-0.4, 3.85, 1.5), (0.4, 4.2, 3.0), 'dgrey')
    # Tail boom, fin, tail rotor.
    taper(body, -2.5, -10.5, 2.6, 0.7, 0.3, 'ugreen', steps=4)
    body.box((-0.12, 2.6, -11.4), (0.12, 4.6, -10.0), 'ugreen')
    body.box((0.12, 3.4, -11.2), (0.18, 4.8, -10.6), 'dark')
    body.box((-1.4, 2.5, -9.4), (1.4, 2.6, -8.7), 'ugreen')
    # Outriggers with rocket pods, fuel tanks on the sides.
    for s in (-1, 1):
        body.box((s * 1.15 - (0.0 if s > 0 else 1.0), 1.6, 1.0), (s * 1.15 + (1.0 if s > 0 else 0.0), 1.7, 2.0), 'dgrey')
        canister(body, s * 2.0, 1.3, 0.6, 1.8, 0.5, 'dgrey')
        body.box((s * 1.15 - (0.0 if s > 0 else 0.45), 0.9, -0.5), (s * 1.15 + (0.45 if s > 0 else 0.0), 1.5, 2.5), 'ugreen')
    gear(m, 0, 6.0, 0.3, name='gear_nose', strut=0.4)
    gear(m, -1.5, -0.2, 0.4, name='gear_l', strut=0.5)
    gear(m, 1.5, -0.2, 0.4, name='gear_r', strut=0.5)
    rotor(m, body, (0, 4.4, 2.3), 10.6, 5, 'dark')
    m.spinner = 'rotor'
    m.seat('driver', -0.45, 2.0, 5.3)
    m.seat('gunner', 0.45, 2.0, 5.3)
    for i in range(4):
        m.seat('passenger', -0.6 + (i % 2) * 1.2, 1.2, 2.5 - (i // 2) * 2.0)
    return m


def mi24():
    m = Model('mi24', paint='ugreen', seed=3002)
    m.width = 2.0
    m.camera = 18
    body = m.part('body')
    body.box((-0.85, 0.8, -2.0), (0.85, 2.8, 4.0), 'ugreen')
    # Stepped tandem cockpits: the gunner low in front, the pilot higher behind.
    body.box((-0.7, 0.9, 4.0), (0.7, 2.1, 6.4), 'ugreen')
    body.box((-0.6, 2.1, 4.6), (0.6, 2.7, 6.2), 'glass')
    body.box((-0.65, 2.8, 2.6), (0.65, 3.4, 4.0), 'glass')
    taper(body, 6.4, 7.6, 1.3, 0.55, 0.2, 'ugreen', steps=2)
    barrel(body, 0, 0.75, 6.0, 1.2, 0.08)
    body.box((-0.85, 2.8, -0.5), (0.85, 3.5, 2.6), 'ugreen')
    taper(body, -2.0, -9.5, 2.3, 0.55, 0.25, 'ugreen', steps=4)
    body.box((-0.1, 2.3, -10.2), (0.1, 4.2, -9.0), 'ugreen')
    body.box((-0.18, 3.2, -10.0), (-0.12, 4.4, -9.4), 'dark')
    # Stub wings with rocket pods.
    for s in (-1, 1):
        body.box((s * 0.85 - (0.0 if s > 0 else 2.2), 1.9, -0.2), (s * 0.85 + (2.2 if s > 0 else 0.0), 2.05, 1.1), 'ugreen')
        for k in (1.3, 2.4):
            canister(body, s * (0.85 + k), 1.55, -0.4, 1.7, 0.42, 'dgrey')
    gear(m, 0, 5.4, 0.3, name='gear_nose', strut=0.5)
    gear(m, -1.2, 0.2, 0.38, name='gear_l', strut=0.5)
    gear(m, 1.2, 0.2, 0.38, name='gear_r', strut=0.5)
    rotor(m, body, (0, 3.9, 1.0), 8.6, 5, 'dark')
    m.spinner = 'rotor'
    m.seat('driver', 0.0, 2.3, 3.2)
    m.seat('gunner', 0.0, 1.5, 5.3)
    return m


def ka52():
    m = Model('ka52', paint='dgrey', seed=3003)
    m.width = 2.2
    m.camera = 16
    body = m.part('body')
    body.box((-0.95, 0.8, -1.5), (0.95, 2.7, 3.5), 'dgrey')
    taper(body, 3.5, 5.6, 1.6, 0.85, 0.3, 'dgrey', steps=3)
    body.box((-0.8, 2.0, 2.6), (0.8, 2.75, 4.1), 'glass')
    body.box((-0.8, 2.7, -0.8), (0.8, 3.3, 2.4), 'dgrey')
    taper(body, -1.5, -7.5, 2.0, 0.6, 0.3, 'dgrey', steps=3)
    body.box((-1.3, 1.7, -7.8), (1.3, 1.85, -7.0), 'dgrey')
    for s in (-1, 1):
        body.box((s * 1.3 - 0.07, 1.4, -7.9), (s * 1.3 + 0.07, 2.6, -7.0), 'dgrey')
        body.box((s * 0.95 - (0.0 if s > 0 else 2.0), 1.6, -0.2), (s * 0.95 + (2.0 if s > 0 else 0.0), 1.75, 1.0), 'dgrey')
        canister(body, s * 2.3, 1.25, -0.5, 1.7, 0.42, 'dark')
    barrel(body, 0.9, 1.3, 0.8, 2.4, 0.08)
    gear(m, 0, 3.8, 0.3, name='gear_nose', strut=0.5)
    gear(m, -1.1, -0.3, 0.38, name='gear_l', strut=0.4)
    gear(m, 1.1, -0.3, 0.38, name='gear_r', strut=0.4)
    # Two rotors one above the other (the Kamov trademark).
    body.box((-0.15, 3.3, 0.6), (0.15, 4.6, 0.9), 'dark')
    rot = rotor(m, body, (0, 3.9, 0.75), 7.25, 3, 'dark')
    m.spinner = 'rotor'
    rot2 = rot.part('rotor_upper', (0, 4.6, 0.75), (0, 60, 0))
    rot2.box((-0.3, 4.4, 0.45), (0.3, 4.65, 1.05), 'dgrey')
    for i in range(3):
        b = rot2.part('upper_blade_%d' % i, (0, 4.6, 0.75), (0, 120.0 * i, 0))
        b.box((-0.25, 4.56, 1.05), (0.25, 4.64, 0.75 + 7.25), 'dark')
    m.seat('driver', -0.4, 1.8, 3.2)
    m.seat('gunner', 0.4, 1.8, 3.2)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Planes


def su25():
    m = Model('su25', paint='ugreen', seed=3004)
    m.width = 2.6
    m.camera = 18
    body = m.part('body')
    y = 1.9
    taper(body, -6.5, 5.0, y, 0.75, 0.75, 'ugreen', steps=2)
    taper(body, 5.0, 7.8, y, 0.7, 0.25, 'ugreen', steps=3)
    body.box((-0.5, y + 0.6, 2.6), (0.5, y + 1.2, 4.6), 'glass')
    # Engine nacelles along the fuselage.
    for s in (-1, 1):
        body.box((s * 0.95 - 0.45, y - 0.55, -5.5), (s * 0.95 + 0.45, y + 0.35, 2.5), 'ugreen')
        body.box((s * 0.95 - 0.35, y - 0.45, -6.0), (s * 0.95 + 0.35, y + 0.25, -5.5), 'dark')
    wing(body, 0.9, 7.2, y + 0.1, 1.6, -0.2, 3.6, 1.6, 0.18, 'ugreen')
    wing(body, -0.9, -7.2, y + 0.1, 1.6, -0.2, 3.6, 1.6, 0.18, 'ugreen')
    wing(body, 0.5, 2.9, y + 0.5, -4.6, -5.4, 1.6, 0.8, 0.12, 'ugreen', steps=3)
    wing(body, -0.5, -2.9, y + 0.5, -4.6, -5.4, 1.6, 0.8, 0.12, 'ugreen', steps=3)
    body.box((-0.1, y + 0.7, -6.6), (0.1, y + 3.0, -4.6), 'ugreen')
    # Pylons: rocket pods and bombs.
    for s in (-1, 1):
        canister(body, s * 3.2, y - 0.45, -0.6, 2.0, 0.42, 'dgrey')
        canister(body, s * 4.6, y - 0.4, -0.4, 1.7, 0.35, 'dark')
    gear(m, 0, 4.6, 0.3, name='gear_nose', strut=1.2)
    gear(m, -1.6, -1.0, 0.42, name='gear_l', strut=1.0)
    gear(m, 1.6, -1.0, 0.42, name='gear_r', strut=1.0)
    m.seat('driver', 0.0, 2.2, 3.5)
    return m


def f16():
    m = Model('f16', paint='grey', seed=3005)
    m.width = 2.4
    m.camera = 18
    body = m.part('body')
    y = 1.8
    taper(body, -6.8, 3.0, y, 0.65, 0.65, 'grey', steps=2)
    taper(body, 3.0, 8.2, y + 0.05, 0.6, 0.15, 'grey', steps=4)
    body.box((-0.4, y + 0.45, 2.6), (0.4, y + 1.05, 5.0), 'glass')
    # Chin intake and the nozzle.
    body.box((-0.5, y - 1.0, 0.5), (0.5, y - 0.45, 2.9), 'grey', sides={'front': 'dark'})
    body.box((-0.45, y - 0.45, -7.3), (0.45, y + 0.45, -6.8), 'dark')
    wing(body, 0.6, 4.7, y, 1.8, -2.8, 4.8, 1.2, 0.14, 'grey')
    wing(body, -0.6, -4.7, y, 1.8, -2.8, 4.8, 1.2, 0.14, 'grey')
    wing(body, 0.5, 2.8, y, -4.8, -6.1, 1.8, 0.8, 0.1, 'grey', steps=3)
    wing(body, -0.5, -2.8, y, -4.8, -6.1, 1.8, 0.8, 0.1, 'grey', steps=3)
    body.box((-0.08, y + 0.5, -7.0), (0.08, y + 2.9, -4.4), 'grey')
    # Wingtip Sidewinders, a fuel tank under each wing.
    for s in (-1, 1):
        canister(body, s * 4.8, y, -2.6, 2.6, 0.13, 'white')
        canister(body, s * 2.4, y - 0.55, -1.4, 3.0, 0.38, 'lgrey')
    gear(m, 0, 4.0, 0.25, name='gear_nose', strut=1.1)
    gear(m, -1.2, -1.2, 0.35, name='gear_l', strut=1.0)
    gear(m, 1.2, -1.2, 0.35, name='gear_r', strut=1.0)
    m.seat('driver', 0.0, 2.1, 3.6)
    return m


def all_models():
    return [mi8(), mi24(), ka52(), su25(), f16()]
