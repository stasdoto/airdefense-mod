"""
Helicopters and planes (stage R6, rebuilt in 1.26 "Crew"). Metres, x right, y up, z forward; wheels on the ground at
y = 0. Bodies are lofted through stations with a rounded (twelve-sided) cross-section instead of square boxes;
canopies are thin panes of glass (see-through, with the cockpit inside - interior.py), windscreens sloped.
"""
import math

from boxgen import Model
from kit import lights
from airdefense import barrel, canister, missile


def taper(part, z0, z1, y, r0, r1, paint, steps=4, wy=None):
    """A square-ish body from z0 (radius r0) to z1 (radius r1), in steps (kept for the trucks' tanks)."""
    for i in range(steps):
        za = z0 + (z1 - z0) * i / steps
        zb = z0 + (z1 - z0) * (i + 1) / steps
        r = r0 + (r1 - r0) * (i + 0.5) / steps
        h = r * (wy if wy else 1.0)
        part.box((-r, y - h, za), (r, y + h, zb), paint)


ALL = ('top', 'bottom', 'front', 'back', 'right', 'left')


def skin(part, za, zb, yc, rx, ry, paint, x=0.0, t=0.04):
    """
    A hollow slice (a cabin inside): the twelve-sided outline as thin plates, each showing only its outer face, so from
    inside nothing of it is seen but the cabin's own lining.
    """
    k = 0.42
    for s_ in (-1, 1):
        # Sides, the shoulder steps and the upper/lower corner plates, mirrored left/right and top/bottom.
        side = ('right',) if s_ > 0 else ('left',)
        part.box((x + s_ * rx - t / 2, yc - ry * k, za), (x + s_ * rx + t / 2, yc + ry * k, zb), paint, faces=side)
        part.box((x + s_ * 0.8 * rx - t / 2, yc + ry * k, za), (x + s_ * 0.8 * rx + t / 2, yc + ry * 0.8, zb), paint, faces=side)
        part.box((x + s_ * 0.8 * rx - t / 2, yc - ry * 0.8, za), (x + s_ * 0.8 * rx + t / 2, yc - ry * k, zb), paint, faces=side)
        part.box((x + s_ * k * rx - t / 2, yc + ry * 0.8, za), (x + s_ * k * rx + t / 2, yc + ry, zb), paint, faces=side)
        part.box((x + s_ * k * rx - t / 2, yc - ry, za), (x + s_ * k * rx + t / 2, yc - ry * 0.8, zb), paint, faces=side)
        for v, face in ((1, 'top'), (-1, 'bottom')):
            xa, xb = sorted((x + s_ * 0.8 * rx, x + s_ * rx))
            part.box((xa, yc + v * ry * k - t / 2, za), (xb, yc + v * ry * k + t / 2, zb), paint, faces=(face,))
            xa, xb = sorted((x + s_ * k * rx, x + s_ * 0.8 * rx))
            part.box((xa, yc + v * ry * 0.8 - t / 2, za), (xb, yc + v * ry * 0.8 + t / 2, zb), paint, faces=(face,))
    for v, face in ((1, 'top'), (-1, 'bottom')):
        part.box((x - k * rx, yc + v * ry - t / 2, za), (x + k * rx, yc + v * ry + t / 2, zb), paint, faces=(face,))


def section(part, za, zb, yc, rx, ry, paint, x=0.0, eps=0.0015, cuts=(), drop=(), hollow=()):
    """
    One slice of a rounded body: three crossed boxes give a twelve-sided cross-section. {cuts}: (z0, z1, y) - within
    z0..z1 nothing rises above y (the body under a canopy stops at the sill, so the cockpit is open inside).
    """
    k = 0.42
    # Split the slice where a cut or a hollow begins or ends.
    edges = sorted({za, zb} | {c[i] for c in list(cuts) + list(hollow) for i in (0, 1) if za < c[i] < zb})
    for a, b in zip(edges, edges[1:]):
        if any(h[0] <= (a + b) / 2 <= h[1] for h in hollow):
            skin(part, a, b, yc, rx, ry, paint, x)
            continue
        cut = None
        for c in cuts:
            if c[0] <= (a + b) / 2 <= c[1]:
                cut = c
        if cut is None:
            for (hx, hy, e) in ((rx, ry * k, 0), (rx * k, ry, eps), (rx * 0.8, ry * 0.8, 2 * eps)):
                part.box((x - hx, yc - hy, a + e), (x + hx, yc + hy, b - e), paint, faces=tuple(f for f in ALL if f not in drop))
            continue
        # Under a canopy: only the skin (sides and bottom) up to the sill, and a shelf at the sill out to the cockpit's
        # edge - the cockpit's inside model fills the rest.
        top, hw = cut[2], cut[3]
        widest = 0.0
        for (hx, hy, e) in ((rx, ry * k, 0), (rx * k, ry, eps), (rx * 0.8, ry * 0.8, 2 * eps)):
            y0, y1 = yc - hy, min(yc + hy, top)
            if y1 - y0 > 0.01:
                part.box((x - hx, y0, a + e), (x + hx, y1, b - e), paint, faces=('left', 'right', 'bottom'))
                if yc + hy >= top - 0.05:
                    widest = max(widest, hx)
        if widest > hw:
            for s_ in (-1, 1):
                xa, xb = sorted((x + s_ * hw, x + s_ * widest))
                part.box((xa, top - 0.03, a), (xb, top, b), paint, faces=('top',))


def loft(part, st, paint, steps=2, x=0.0, cuts=(), open_end=False, hollow=()):
    """A rounded body through stations (z, y centre, half width, half height); {open_end}: the first station's end
    face left off (a glazed front sits there)."""
    n = len(st) - 1
    for j, ((z0, y0, rx0, ry0), (z1, y1, rx1, ry1)) in enumerate(zip(st, st[1:])):
        for i in range(steps):
            tm = (i + 0.5) / steps
            za = z0 + (z1 - z0) * i / steps
            zb = z0 + (z1 - z0) * (i + 1) / steps
            drop = ()
            if open_end and j == 0 and i == 0:
                drop = ('front',) if z0 > z1 else ('back',)
            section(part, min(za, zb), max(za, zb), y0 + (y1 - y0) * tm, rx0 + (rx1 - rx0) * tm, ry0 + (ry1 - ry0) * tm, paint, x,
                    cuts=cuts, drop=drop, hollow=hollow)


def rotor(m, parent, pivot, radius, blades, paint='dark', name='rotor', chord=0.5):
    """Main rotor: hub and blades, turning about y (the spinner); blades droop a little and taper at the tips."""
    r = parent.part(name, pivot)
    x, y, z = pivot
    r.box((x - 0.38, y - 0.28, z - 0.38), (x + 0.38, y + 0.08, z + 0.38), 'dgrey')
    r.box((x - 0.2, y + 0.08, z - 0.2), (x + 0.2, y + 0.22, z + 0.2), 'dark')
    for i in range(blades):
        b = r.part('%s_blade_%d' % (name, i), pivot, (0, 360.0 * i / blades, 0))
        b.box((x - 0.07, y - 0.08, z + 0.3), (x + 0.07, y + 0.02, z + 0.9), 'dgrey')
        b.box((x - chord / 2, y - 0.05, z + 0.9), (x + chord / 2, y + 0.02, z + radius * 0.92), paint)
        b.box((x - chord * 0.38, y - 0.06, z + radius * 0.92), (x + chord * 0.38, y + 0.0, z + radius), paint)
        b.box((x - chord / 2 - 0.01, y - 0.055, z + radius * 0.85), (x - chord / 2 + 0.06, y + 0.025, z + radius * 0.92), 'yellow')
    return r


def tail_rotor(parent, x, y, z, radius, blades, paint='dark', side=1):
    """A tail rotor on the side of the fin (does not turn - a blur would be the honest look)."""
    t = parent.part('tail_rotor', (x, y, z))
    t.box((x - 0.12 * side, y - 0.12, z - 0.12), (x + 0.12 * side, y + 0.12, z + 0.12), 'dgrey')
    for i in range(blades):
        b = t.part('tail_blade_%d' % i, (x, y, z), (360.0 * i / blades, 0, 0))
        b.box((x + 0.02 * side - 0.02, y + 0.1, z - 0.11), (x + 0.02 * side + 0.02, y + radius, z + 0.11), paint)
    return t


def wing(part, x_root, x_tip, y, z_lead_root, z_lead_tip, chord_root, chord_tip, thick, paint, steps=5, dihedral=0.0, thin_tip=0.6):
    """A tapered, swept wing from x_root to x_tip (either side), in steps, thinner towards the tip."""
    for i in range(steps):
        t0 = i / steps
        t1 = (i + 1) / steps
        xa = x_root + (x_tip - x_root) * t0
        xb = x_root + (x_tip - x_root) * t1
        tm = (t0 + t1) / 2
        zl = z_lead_root + (z_lead_tip - z_lead_root) * tm
        c = chord_root + (chord_tip - chord_root) * tm
        th = thick * (1 - (1 - thin_tip) * tm)
        yy = y + dihedral * abs(xa + xb) / 2
        part.box((min(xa, xb), yy - th / 2, zl - c), (max(xa, xb), yy + th / 2, zl), paint)
        # The leading edge a little rounder than the rest.
        part.box((min(xa, xb), yy - th * 0.3, zl), (max(xa, xb), yy + th * 0.3, zl + th * 0.35), paint)


def fin(part, z_root, z_tip_lead, y0, y1, chord_root, chord_tip, thick, paint, steps=5, x=0.0):
    """A swept fin standing on y0 up to y1."""
    for i in range(steps):
        t0 = i / steps
        t1 = (i + 1) / steps
        tm = (t0 + t1) / 2
        zl = z_root + (z_tip_lead - z_root) * tm
        c = chord_root + (chord_tip - chord_root) * tm
        part.box((x - thick / 2, y0 + (y1 - y0) * t0, zl - c), (x + thick / 2, y0 + (y1 - y0) * t1, zl), paint)


def gear(m, x, z, r, steer=False, name=None, strut=1.0, twin=False):
    n = name or 'gear_%d_%d' % (int(x * 10), int(z * 10))
    body = m.parts[0]
    if twin:
        m.wheel(n + '_a', x - 0.17, r, z, r, 0.16, steer=steer, side='wheel_grey', tread='tire')
        m.wheel(n + '_b', x + 0.17, r, z, r, 0.16, steer=steer, side='wheel_grey', tread='tire')
    else:
        m.wheel(n, x, r, z, r, 0.22, steer=steer, side='wheel_grey', tread='tire')
    body.box((x - 0.05, r, z - 0.05), (x + 0.05, r + strut, z + 0.05), 'steel')
    body.box((x - 0.08, r + strut * 0.55, z - 0.08), (x + 0.08, r + strut, z + 0.08), 'dgrey')


def bubble(part, z0, z1, zf, ysill, ytop, hw, frame='dark', bow=None):
    """
    A canopy of thin glass panes: the sides (stepped in towards the top), the top, the back, and a windscreen sloped
    from its base at zf forward up to the top at z1; frame rails along the sills (and a bow at {bow}, if given).
    """
    t = 0.02
    ym = ysill + (ytop - ysill) * 0.55
    hw2 = hw * 0.8
    for s in (-1, 1):
        face = ('right',) if s > 0 else ('left',)
        part.box((s * hw - t / 2, ysill, z0), (s * hw + t / 2, ym, z1), 'glass', faces=face)
        part.box((s * hw2 - t / 2, ym, z0), (s * hw2 + t / 2, ytop - 0.02, z1), 'glass', faces=face)
        part.box((min(s * hw, s * hw2), ym - t / 2, z0), (max(s * hw, s * hw2), ym + t / 2, z1), 'glass', faces=('top',))
        # The triangle beside the windscreen, in steps.
        for k in range(3):
            za = z1 + (zf - z1) * k / 3
            zb = z1 + (zf - z1) * (k + 1) / 3
            yh = ytop - (ytop - ysill) * (k + 0.8) / 3
            part.box((s * hw - t / 2, ysill, za), (s * hw + t / 2, max(ysill + 0.05, min(ym, yh)), zb), 'glass', faces=face)
        # Sill rail.
        part.box((s * hw - 0.04, ysill - 0.04, z0), (s * hw + 0.04, ysill + 0.02, zf), frame)
    part.box((-hw2, ytop - t, z0), (hw2, ytop, z1), 'glass', faces=('top',))
    part.box((-hw, ysill, z0 - t), (hw, ytop, z0), 'glass', faces=('back',))
    # Windscreen: a pane turned back about its base.
    rise = ytop - ysill - 0.02
    run = zf - z1
    ang = math.degrees(math.atan2(run, rise))
    ln = math.hypot(run, rise)
    ws = part.part('windscreen_%d' % int(z1 * 100), (0, ysill, zf), (ang, 0, 0))
    ws.box((-hw2 * 1.05, ysill, zf - t), (hw2 * 1.05, ysill + ln, zf), 'glass', faces=('front',))
    ws.box((-hw2 * 1.08, ysill, zf - 0.05), (-hw2 * 1.02, ysill + ln, zf + 0.02), frame)
    ws.box((hw2 * 1.02, ysill, zf - 0.05), (hw2 * 1.08, ysill + ln, zf + 0.02), frame)
    ws.box((-hw2 * 1.08, ysill + ln - 0.05, zf - 0.05), (hw2 * 1.08, ysill + ln, zf + 0.02), frame)
    if bow is not None:
        part.box((-hw2, ytop - 0.03, bow - 0.04), (hw2, ytop + 0.02, bow + 0.04), frame)
        for s in (-1, 1):
            part.box((s * hw - 0.03, ysill, bow - 0.04), (s * hw + 0.03, ym, bow + 0.04), frame)
            part.box((s * hw2 - 0.03, ym, bow - 0.04), (s * hw2 + 0.03, ytop, bow + 0.04), frame)


def bomb(part, x, y, z0, length, dia, style='olive'):
    r = dia / 2
    part.box((x - r, y - r, z0 + length * 0.2), (x + r, y + r, z0 + length * 0.8), style)
    part.box((x - r * 0.7, y - r * 0.7, z0 + length * 0.8), (x + r * 0.7, y + r * 0.7, z0 + length), style)
    part.box((x - r * 0.6, y - r * 0.6, z0), (x + r * 0.6, y + r * 0.6, z0 + length * 0.2), style)
    part.box((x - r * 1.2, y - 0.01, z0), (x + r * 1.2, y + 0.01, z0 + length * 0.18), 'dark')
    part.box((x - 0.01, y - r * 1.2, z0), (x + 0.01, y + r * 1.2, z0 + length * 0.18), 'dark')


def pylon(part, x, y_wing, y_store, z0, z1, style='dgrey'):
    part.box((x - 0.04, y_store, z0), (x + 0.04, y_wing, z1), style)


# ------------------------------------------------------------------------------------------------------------------
# Helicopters


def mi8():
    """Mi-8MTV-5: the workhorse - glazed nose with two pilots side by side, a long cabin with round windows, two engines
    on the roof with dust filters, fuel tanks and rocket pods on outriggers, a five-blade rotor."""
    m = Model('mi8', paint='ugreen', seed=3001)
    m.width = 2.5
    m.camera = 20
    body = m.part('body')
    yc = 1.9
    # The fuselage from the cockpit roof back (open at the front: the windscreen is there), the lower nose under it.
    loft(body, [(6.4, 1.88, 1.12, 1.0), (5.3, 1.9, 1.22, 1.15), (4.4, 1.9, 1.25, 1.18), (-1.4, 1.9, 1.25, 1.18), (-2.5, 2.05, 1.05, 1.0),
                (-3.1, 2.35, 0.72, 0.7)], 'ugreen', hollow=[(-1.9, 6.4)])
    # The back of the cabin (clamshell doors) and the cockpit's roof edge over the windscreen, closed from inside.
    body.box((-1.0, 0.95, -1.95), (1.0, 2.85, -1.9), 'ugreen', faces=('front',))
    loft(body, [(7.45, 1.45, 0.38, 0.3), (7.1, 1.42, 0.82, 0.48), (6.4, 1.4, 1.08, 0.56)], 'ugreen', steps=2)
    # The glazed nose: a windscreen leaning back from the chin to the roof, stepped side panes, the frames.
    ws = body.part('windscreen', (0, 1.95, 7.02), (math.degrees(math.atan2(0.62, 0.86)), 0, 0))
    ln = math.hypot(0.62, 0.86)
    for s in (-1, 1):
        ws.box((0.05 if s > 0 else -0.9, 1.95, 7.0), (0.9 if s > 0 else -0.05, 1.95 + ln, 7.02), 'glass', faces=('front',))
        ws.box((s * 0.92 - 0.04, 1.95, 6.97), (s * 0.92 + 0.04, 1.95 + ln, 7.04), 'dark')
    ws.box((-0.05, 1.95, 6.97), (0.05, 1.95 + ln, 7.04), 'dark')
    ws.box((-0.96, 1.95 + ln - 0.05, 6.97), (0.96, 1.95 + ln, 7.04), 'dark')
    for s in (-1, 1):
        x = s * 1.0
        for k in range(3):
            za = 6.4 + 0.62 * k / 3
            zb = 6.4 + 0.62 * (k + 1) / 3
            yh = 2.81 - 0.86 * (k + 0.8) / 3
            body.box((x - 0.01, 1.93, za), (x + 0.01, max(1.99, yh), zb), 'glass', faces=('right',) if s > 0 else ('left',))
        body.box((x - 0.03, 1.9, 6.4), (x + 0.03, 1.96, 7.02), 'dark')
        body.box((s * 1.27 - (0.0 if s > 0 else 0.02), 2.0, 5.1), (s * 1.27 + (0.02 if s > 0 else 0.0), 2.62, 6.2), 'glass')
        body.box((s * 0.15 if s > 0 else -0.7, 1.05, 7.12), (0.7 if s > 0 else -0.15, 1.45, 7.15), 'glass')
    # Round windows along the cabin, the sliding door on the left, clamshell doors at the back.
    for s in (-1, 1):
        for z in (3.3, 2.1, 0.9, -0.3):
            body.box((s * 1.28 - (0.0 if s > 0 else 0.02), 2.08, z), (s * 1.28 + (0.02 if s > 0 else 0.0), 2.5, z + 0.42), 'glass')
    body.box((-1.27, 0.95, 3.85), (-1.24, 2.65, 4.75), 'dgrey')
    body.box((-0.03, 1.2, -2.62), (0.03, 2.9, -2.4), 'dark')
    # Engines on the roof: two nacelles, the dust filters in front, exhausts out to the sides; the gearbox fairing.
    for s in (-1, 1):
        loft(body, [(5.0, 3.25, 0.42, 0.4), (4.6, 3.25, 0.5, 0.48), (0.9, 3.2, 0.5, 0.48), (0.5, 3.15, 0.42, 0.4)], 'ugreen', steps=1, x=s * 0.6)
        body.box((s * 0.6 - 0.36, 2.92, 5.0), (s * 0.6 + 0.36, 3.6, 5.75), 'dgrey')
        body.box((s * 0.6 - 0.3, 2.98, 5.75), (s * 0.6 + 0.3, 3.54, 5.8), 'mesh_dark')
        body.box((s * 0.95 - 0.2, 3.0, 0.2), (s * 0.95 + 0.25, 3.4, 0.75), 'dark')
    loft(body, [(3.6, 3.25, 0.55, 0.5), (3.2, 3.4, 0.75, 0.6), (1.2, 3.4, 0.75, 0.6), (0.6, 3.25, 0.5, 0.45)], 'ugreen', steps=1)
    body.box((-0.2, 3.9, 2.0), (0.2, 4.35, 2.4), 'dgrey')
    # Tail boom, the fin with the tail rotor on its right, the stabiliser.
    loft(body, [(-3.0, 2.45, 0.58, 0.55), (-7.0, 2.65, 0.4, 0.38), (-10.3, 2.85, 0.26, 0.26)], 'ugreen', steps=3)
    fin(body, -10.0, -10.9, 2.8, 4.9, 1.5, 0.9, 0.2, 'ugreen', steps=4)
    tail_rotor(body, 0.22, 4.25, -10.5, 1.55, 3)
    for s in (-1, 1):
        body.box((s * 0.25 if s > 0 else -1.45, 2.6, -9.1), (1.45 if s > 0 else -0.25, 2.68, -8.4), 'ugreen')
    # Fuel tanks low on the sides; outriggers with rocket pods.
    for s in (-1, 1):
        loft(body, [(2.6, 1.1, 0.25, 0.28), (2.2, 1.1, 0.36, 0.4), (-0.4, 1.1, 0.36, 0.4), (-0.8, 1.1, 0.25, 0.28)], 'ugreen', steps=1,
             x=s * 1.45)
        body.box((s * 1.2 - (0.0 if s > 0 else 1.1), 1.72, 1.2), (s * 1.2 + (1.1 if s > 0 else 0.0), 1.82, 2.0), 'dgrey')
        body.box((s * 1.6 - 0.04, 1.2, 1.4), (s * 1.6 + 0.04, 1.75, 1.8), 'dgrey')
        canister(body, s * 2.05, 1.38, 0.55, 1.9, 0.5, 'dgrey')
    lights(body, 7.2, 1.25, 0.5)
    gear(m, 0, 6.2, 0.3, name='gear_nose', strut=0.55, twin=True)
    gear(m, -1.65, -0.2, 0.42, name='gear_l', strut=0.45)
    gear(m, 1.65, -0.2, 0.42, name='gear_r', strut=0.45)
    for s in (-1, 1):
        body.box((s * 1.25, 0.95, -0.25), (s * 1.62, 1.02, -0.15), 'steel')
    rotor(m, body, (0, 4.45, 2.2), 10.6, 5, 'dark', chord=0.52)
    m.spinner = 'rotor'
    m.seat('driver', -0.48, 1.45, 5.75)
    m.seat('gunner', 0.48, 1.45, 5.75)
    for i in range(4):
        m.seat('passenger', -0.6 + (i % 2) * 1.2, 1.2, 2.5 - (i // 2) * 2.0)
    m.cab(-0.98, 0.98, 0.85, 2.82, 4.6, 6.4, kind='heli', glazing='cabin', panel_top=1.92, panel_z=6.25, open=('front',))
    m.cab(-0.98, 0.98, 0.85, 2.82, -1.9, 4.6, kind='troops')
    return m


def mi24():
    """Mi-24P: the "flying tank" - two bubble cockpits one behind the other (the gunner low in front, the pilot above),
    a troop cabin, stub wings with rocket pods and anti-tank missiles, the twin 30 mm gun on the right side."""
    m = Model('mi24', paint='ugreen', seed=3002)
    m.width = 2.0
    m.camera = 18
    body = m.part('body')
    loft(body, [(7.7, 1.45, 0.2, 0.22), (7.25, 1.5, 0.48, 0.5), (6.6, 1.6, 0.66, 0.66), (5.2, 1.78, 0.78, 0.82),
                (4.2, 1.92, 0.85, 0.95), (2.6, 2.02, 0.95, 1.05), (-0.8, 2.02, 0.95, 1.05), (-2.2, 2.2, 0.72, 0.78)], 'ugreen',
         cuts=[(5.05, 6.75, 2.0, 0.5), (3.0, 5.05, 2.62, 0.56)])
    # Gunner's bubble (front, low) and the pilot's (behind, higher).
    bubble(body, 5.05, 6.15, 6.75, 2.0, 2.82, 0.5, bow=5.6)
    bubble(body, 3.0, 4.55, 5.05, 2.62, 3.42, 0.56, bow=3.8)
    # Behind the pilot the spine to the engines.
    loft(body, [(3.0, 2.95, 0.5, 0.45), (2.3, 3.1, 0.8, 0.55), (-0.6, 3.1, 0.8, 0.55), (-1.4, 2.9, 0.5, 0.4)], 'ugreen', steps=1)
    for s in (-1, 1):
        loft(body, [(3.3, 3.2, 0.36, 0.34), (2.9, 3.2, 0.42, 0.4), (-0.3, 3.15, 0.42, 0.4), (-0.6, 3.1, 0.34, 0.32)], 'ugreen', steps=1,
             x=s * 0.72)
        body.box((s * 0.72 - 0.26, 2.95, 3.3), (s * 0.72 + 0.26, 3.45, 3.34), 'dark')
        body.box((s * 1.05 - 0.15, 2.95, -0.75), (s * 1.05 + 0.25, 3.35, -0.3), 'dark')
    body.box((-0.22, 3.6, 0.8), (0.22, 3.95, 1.2), 'dgrey')
    # The gun (GSh-30K) on the right side of the nose; the sensor turret under the gunner's chin.
    body.box((0.8, 1.55, 3.6), (1.08, 1.85, 4.6), 'ugreen')
    barrel(body, 0.85, 1.7, 4.6, 1.5, 0.07)
    barrel(body, 1.02, 1.7, 4.6, 1.5, 0.07)
    body.box((-0.2, 1.0, 6.6), (0.2, 1.35, 7.0), 'dgrey', sides={'front': 'glass'})
    # Tail boom, fin with the tail rotor (left side), stabiliser.
    loft(body, [(-2.0, 2.3, 0.48, 0.48), (-6.0, 2.55, 0.34, 0.34), (-9.0, 2.75, 0.22, 0.22)], 'ugreen', steps=3)
    fin(body, -8.6, -9.6, 2.7, 4.5, 1.3, 0.8, 0.18, 'ugreen', steps=4)
    tail_rotor(body, -0.2, 4.0, -9.25, 1.65, 3, side=-1)
    for s in (-1, 1):
        body.box((s * 0.25 if s > 0 else -1.5, 2.45, -7.4), (1.5 if s > 0 else -0.25, 2.53, -6.8), 'ugreen')
    # Stub wings drooping down, pylons with rocket pods, the missile launchers at the tips.
    for s in (-1, 1):
        for k in range(3):
            xa = s * (0.9 + k * 0.85)
            xb = s * (0.9 + (k + 1) * 0.85)
            y = 2.05 - k * 0.1
            body.box((min(xa, xb), y - 0.09, -0.2), (max(xa, xb), y + 0.09, 1.25), 'ugreen')
        for k, x in enumerate((1.55, 2.45)):
            pylon(body, s * x, 1.95 - k * 0.1, 1.65 - k * 0.1, 0.0, 0.9)
            canister(body, s * x, 1.4 - k * 0.1, -0.5, 1.75, 0.46, 'dgrey')
        body.box((s * 3.45 - 0.05, 1.55, -0.3), (s * 3.45 + 0.05, 2.0, 1.2), 'ugreen')
        for dy in (-0.15, 0.15):
            canister(body, s * 3.45 + s * 0.12, 1.55 + dy, -0.6, 1.8, 0.15, 'olive')
    gear(m, 0, 6.1, 0.3, name='gear_nose', strut=0.75, twin=True)
    gear(m, -1.35, 0.3, 0.4, name='gear_l', strut=0.7)
    gear(m, 1.35, 0.3, 0.4, name='gear_r', strut=0.7)
    rotor(m, body, (0, 4.05, 1.0), 8.6, 5, 'dark', chord=0.58)
    m.spinner = 'rotor'
    m.seat('driver', 0.0, 2.25, 3.75)
    m.seat('gunner', 0.0, 1.62, 5.55)
    m.cab(-0.52, 0.52, 1.75, 2.62, 3.0, 5.05, kind='heli', glazing='bubble', panel_top=2.6, panel_z=4.75)
    m.cab(-0.48, 0.48, 1.05, 2.0, 5.05, 6.75, kind='heli', glazing='bubble', panel_top=2.0, panel_z=6.4)
    return m


def ka52():
    """Ka-52 "Alligator": two rotors one above the other, a wide cockpit with the pilots side by side, the 30 mm gun on
    the right flank, stub wings with pods and missiles, the twin-fin tail."""
    m = Model('ka52', paint='dgrey', seed=3003)
    m.width = 2.2
    m.camera = 16
    body = m.part('body')
    loft(body, [(5.75, 1.45, 0.25, 0.3), (5.3, 1.5, 0.6, 0.55), (4.5, 1.6, 0.85, 0.75), (3.4, 1.7, 0.98, 0.92),
                (1.0, 1.75, 1.0, 0.98), (-1.2, 1.85, 0.95, 0.9), (-1.9, 2.0, 0.7, 0.65)], 'dgrey', cuts=[(2.15, 4.55, 1.8, 0.88)])
    # The wide cockpit: a glass box above the sill, sloped windscreen.
    bubble(body, 2.15, 3.85, 4.55, 1.8, 2.8, 0.88, bow=3.2)
    body.box((-0.04, 2.76, 2.15), (0.04, 2.84, 3.85), 'dark')
    # Engines on the shoulders with the exhaust deflectors, the hump with the rotor mast.
    for s in (-1, 1):
        loft(body, [(1.4, 2.6, 0.32, 0.3), (1.0, 2.62, 0.4, 0.38), (-0.9, 2.6, 0.4, 0.38), (-1.3, 2.55, 0.34, 0.3)], 'dgrey', steps=1,
             x=s * 1.05)
        body.box((s * 1.05 - 0.28, 2.32, 1.4), (s * 1.05 + 0.28, 2.88, 1.45), 'dark')
        body.box((s * 1.2 - 0.2, 2.25, -1.6), (s * 1.2 + 0.25, 2.75, -1.25), 'dark')
    loft(body, [(2.1, 2.7, 0.6, 0.35), (1.6, 2.95, 0.75, 0.45), (-0.8, 2.95, 0.75, 0.45), (-1.5, 2.7, 0.5, 0.3)], 'dgrey', steps=1)
    body.box((-0.13, 3.3, 0.6), (0.13, 4.7, 0.9), 'dark')
    # Gun on the right flank.
    body.box((0.85, 1.25, 0.2), (1.15, 1.55, 1.6), 'dgrey')
    barrel(body, 1.0, 1.4, 1.6, 2.0, 0.08)
    # Sensor ball under the nose.
    body.box((-0.22, 0.95, 4.7), (0.22, 1.35, 5.1), 'dark', sides={'front': 'glass'})
    # Tail boom, the stabiliser with two fins.
    loft(body, [(-1.7, 2.0, 0.55, 0.5), (-5.0, 2.15, 0.36, 0.32), (-7.4, 2.25, 0.24, 0.22)], 'dgrey', steps=3)
    body.box((-1.4, 2.1, -7.8), (1.4, 2.22, -7.0), 'dgrey')
    for s in (-1, 1):
        fin(body, -6.9, -7.4, 1.7, 2.9, 1.0, 0.7, 0.12, 'dgrey', steps=3, x=s * 1.4)
    fin(body, -6.6, -7.4, 2.25, 3.3, 1.2, 0.7, 0.14, 'dgrey', steps=3)
    # Stub wings with pylons: rocket pods inboard, missile racks outboard; flare dispensers at the tips.
    for s in (-1, 1):
        body.box((s * 0.95 if s > 0 else -3.0, 1.6, -0.3), (3.0 if s > 0 else -0.95, 1.76, 1.0), 'dgrey')
        pylon(body, s * 1.7, 1.6, 1.35, -0.1, 0.8)
        canister(body, s * 1.7, 1.12, -0.6, 1.7, 0.44, 'dark')
        pylon(body, s * 2.55, 1.6, 1.4, -0.1, 0.8)
        for dx in (-0.1, 0.1):
            canister(body, s * 2.55 + dx, 1.25, -0.8, 2.0, 0.14, 'olive')
        body.box((s * 3.0 - 0.1, 1.45, -0.4), (s * 3.0 + 0.1, 1.9, 0.6), 'dark')
    gear(m, 0, 4.0, 0.3, name='gear_nose', strut=0.55, twin=True)
    gear(m, -1.15, -0.4, 0.38, name='gear_l', strut=0.5)
    gear(m, 1.15, -0.4, 0.38, name='gear_r', strut=0.5)
    # Two rotors one above the other (the Kamov trademark).
    rot = rotor(m, body, (0, 3.95, 0.75), 7.25, 3, 'dark', chord=0.55)
    m.spinner = 'rotor'
    rot2 = rot.part('rotor_upper', (0, 4.75, 0.75), (0, 60, 0))
    rot2.box((-0.32, 4.5, 0.43), (0.32, 4.78, 1.07), 'dgrey')
    for i in range(3):
        b = rot2.part('upper_blade_%d' % i, (0, 4.75, 0.75), (0, 120.0 * i, 0))
        b.box((-0.27, 4.71, 1.07), (0.27, 4.78, 0.75 + 7.25 * 0.92), 'dark')
        b.box((-0.2, 4.71, 0.75 + 7.25 * 0.92), (0.2, 4.77, 0.75 + 7.25), 'dark')
        b.box((-0.28, 4.71, 0.75 + 7.25 * 0.85), (-0.2, 4.79, 0.75 + 7.25 * 0.92), 'yellow')
    m.seat('driver', -0.42, 1.2, 3.25)
    m.seat('gunner', 0.42, 1.2, 3.25)
    m.cab(-0.86, 0.86, 0.95, 1.8, 2.15, 4.55, kind='heli', glazing='bubble', panel_top=1.85, panel_z=4.2, modern=True)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Planes


def su25():
    """Su-25 "Grach" (Frogfoot): the armoured attack jet - a long nose, the cockpit with its framed canopy, two engines in
    nacelles along the fuselage under a straight shoulder wing with ten pylons, the tall fin."""
    m = Model('su25', paint='ugreen', seed=3004)
    m.width = 2.6
    m.camera = 18
    body = m.part('body')
    loft(body, [(7.85, 1.98, 0.12, 0.12), (7.3, 2.0, 0.34, 0.38), (6.4, 2.03, 0.52, 0.58), (5.3, 2.05, 0.64, 0.72), (4.0, 2.05, 0.7, 0.78),
                (2.0, 2.05, 0.7, 0.78), (-1.0, 2.1, 0.66, 0.74), (-4.0, 2.2, 0.55, 0.62), (-6.4, 2.32, 0.38, 0.42), (-7.6, 2.4, 0.2, 0.22)],
         'ugreen', cuts=[(2.55, 5.0, 2.5, 0.46)])
    body.box((-0.1, 1.9, 7.85), (0.1, 2.06, 8.05), 'glass')
    # The canopy (a heavy frame: bow across the middle), the spine behind it.
    bubble(body, 2.55, 4.4, 5.0, 2.5, 3.45, 0.46, bow=3.4)
    for (za, zb, top, hw) in ((1.6, 2.55, 3.25, 0.4), (0.4, 1.6, 3.05, 0.34), (-1.2, 0.4, 2.88, 0.3)):
        body.box((-hw, 2.45, za), (hw, top, zb), 'ugreen')
    # Engine nacelles along the fuselage, intakes at the front, nozzles at the back.
    for s in (-1, 1):
        loft(body, [(2.95, 1.95, 0.4, 0.46), (2.55, 1.95, 0.55, 0.58), (-3.5, 1.95, 0.55, 0.58), (-5.6, 2.0, 0.45, 0.48)], 'ugreen', steps=1,
             x=s * 1.0)
        body.box((s * 1.0 - 0.34, 1.6, 2.95), (s * 1.0 + 0.34, 2.32, 3.0), 'dark')
        body.box((s * 1.0 - 0.36, 1.66, -6.2), (s * 1.0 + 0.36, 2.34, -5.6), 'dark')
    # Wings (straight, a little sweep), the split-flap airbrake pods at the tips, the tailplane, the fin.
    wing(body, 1.45, 7.2, 2.45, 1.7, 0.2, 3.3, 1.5, 0.26, 'ugreen', steps=8, dihedral=-0.015)
    wing(body, -1.45, -7.2, 2.45, 1.7, 0.2, 3.3, 1.5, 0.26, 'ugreen', steps=8, dihedral=-0.015)
    for s in (-1, 1):
        canister(body, s * 7.3, 2.35, -1.3, 1.7, 0.24, 'ugreen')
    wing(body, 0.35, 2.9, 2.75, -4.7, -5.5, 1.7, 0.75, 0.12, 'ugreen', steps=3)
    wing(body, -0.35, -2.9, 2.75, -4.7, -5.5, 1.7, 0.75, 0.12, 'ugreen', steps=3)
    fin(body, -4.9, -6.9, 2.75, 5.0, 2.6, 1.0, 0.16, 'ugreen', steps=8)
    # Pylons: rocket pods, bombs, a pair of air-to-air missiles outboard.
    for s in (-1, 1):
        for x, kind in ((2.3, 'pod'), (3.3, 'bomb'), (4.3, 'pod'), (5.3, 'bomb'), (6.3, 'aam')):
            pylon(body, s * x, 2.33, 2.1, -0.6, 0.6)
            if kind == 'pod':
                canister(body, s * x, 1.82, -1.0, 2.5, 0.52, 'dgrey')
            elif kind == 'bomb':
                bomb(body, s * x, 1.82, -1.0, 2.0, 0.42)
            else:
                missile(body, s * x, 1.95, -0.9, 2.1, 0.13, body='white', nose='dgrey')
    gear(m, -0.2, 5.2, 0.33, name='gear_nose', strut=0.92)
    gear(m, -1.05, -0.6, 0.45, name='gear_l', strut=0.9)
    gear(m, 1.05, -0.6, 0.45, name='gear_r', strut=0.9)
    lights(body, 7.0, 1.6, 0.2)
    m.seat('driver', 0.0, 2.2, 3.45)
    m.cab(-0.5, 0.5, 1.55, 2.5, 2.55, 5.0, kind='jet', sill=2.5)
    return m


def f16():
    """F-16C: the bubble canopy, the chin intake, the blended strakes, a cropped delta wing with the Sidewinders on the
    tips, fuel tanks and AMRAAMs under the wings, the single fin."""
    m = Model('f16', paint='fgrey', seed=3005)
    m.width = 2.4
    m.camera = 18
    body = m.part('body')
    loft(body, [(8.1, 1.86, 0.06, 0.06), (7.5, 1.86, 0.3, 0.32), (6.4, 1.86, 0.48, 0.52), (5.0, 1.86, 0.6, 0.62), (3.5, 1.84, 0.62, 0.66),
                (1.0, 1.82, 0.72, 0.64), (-3.0, 1.86, 0.78, 0.6), (-5.6, 1.9, 0.62, 0.55), (-6.6, 1.95, 0.5, 0.5)], 'fgrey',
         cuts=[(2.5, 5.35, 2.22, 0.42)])
    body.box((-0.3, 1.58, 7.5), (0.3, 2.14, 8.15), 'fgrey_d')
    # The bubble canopy: one piece, no bow; the spine behind it to the fin.
    bubble(body, 2.5, 4.5, 5.35, 2.22, 3.15, 0.42)
    for (za, zb, top, hw) in ((1.4, 2.5, 2.95, 0.36), (-1.0, 1.4, 2.75, 0.34), (-4.0, -1.0, 2.6, 0.32)):
        body.box((-hw, 2.2, za), (hw, top, zb), 'fgrey')
    # Chin intake with its dark mouth; the nozzle.
    loft(body, [(3.4, 1.18, 0.5, 0.3), (2.6, 1.18, 0.55, 0.34), (-1.2, 1.25, 0.5, 0.3)], 'fgrey', steps=1)
    body.box((-0.42, 0.95, 3.4), (0.42, 1.42, 3.45), 'dark')
    body.box((-0.45, 1.5, -7.3), (0.45, 2.4, -6.6), 'dark')
    # Strakes blending into the cropped delta, the wings, the all-moving tailplane, the fin.
    wing(body, 0.6, 1.3, 1.95, 4.2, 1.2, 3.2, 1.0, 0.1, 'fgrey', steps=3)
    wing(body, -0.6, -1.3, 1.95, 4.2, 1.2, 3.2, 1.0, 0.1, 'fgrey', steps=3)
    wing(body, 0.75, 4.7, 1.92, 1.6, -2.6, 4.4, 1.2, 0.17, 'fgrey', steps=8)
    wing(body, -0.75, -4.7, 1.92, 1.6, -2.6, 4.4, 1.2, 0.17, 'fgrey', steps=8)
    wing(body, 0.7, 2.9, 1.85, -4.4, -5.6, 1.9, 0.75, 0.1, 'fgrey', steps=3, dihedral=-0.04)
    wing(body, -0.7, -2.9, 1.85, -4.4, -5.6, 1.9, 0.75, 0.1, 'fgrey', steps=3, dihedral=-0.04)
    fin(body, -3.9, -6.4, 2.3, 4.85, 3.0, 1.1, 0.14, 'fgrey', steps=8)
    body.box((-0.08, 2.3, -6.0), (0.08, 2.75, -5.0), 'fgrey_d')
    # Sidewinders on the tips, fuel tanks and AMRAAMs under the wings.
    for s in (-1, 1):
        missile(body, s * 4.85, 1.9, -2.6, 2.9, 0.13, body='white', nose='dgrey')
        pylon(body, s * 2.4, 1.84, 1.62, -1.6, 0.4)
        canister(body, s * 2.4, 1.3, -1.9, 3.0, 0.6, 'lgrey', end='lgrey')
        pylon(body, s * 3.5, 1.84, 1.65, -1.5, 0.2)
        missile(body, s * 3.5, 1.55, -2.0, 3.6, 0.18, body='white', nose='white')
    gear(m, 0, 4.0, 0.27, name='gear_nose', strut=0.7)
    gear(m, -1.1, -1.4, 0.36, name='gear_l', strut=0.85)
    gear(m, 1.1, -1.4, 0.36, name='gear_r', strut=0.85)
    lights(body, 3.4, 1.1, 0.15)
    m.seat('driver', 0.0, 1.98, 3.55)
    m.cab(-0.46, 0.46, 1.4, 2.22, 2.5, 5.35, kind='jet', sill=2.22, western=True)
    return m


def all_models():
    return [mi8(), mi24(), ka52(), su25(), f16()]
