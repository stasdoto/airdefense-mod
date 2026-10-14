"""
2.0: the working country's props as real meshes - the box lorry, the tanker, the tipper, the farm tractor with its seed
drill, the tower crane (tools/models/civil.py has the box models: same sizes; the crane's turning part is 'spin' with
the same pivot). Built by tools/mesh/build_meshes.py m_props. Wheels are their own parts (spin = radius, front ones steer).
"""
import math

import numpy as np

import geom
import kit_trucks as K
import materials
from core import Geo, Model
from kit_trucks import V, box, rbox, cbox, cyl, bar

DENSITY = 24


def _concrete(T, scheme, model):
    return K._plain(T, 0x8F8E88, scheme, 0.5, 0.9, var=0.09), 255


def _gravel(T, scheme, model):
    pos = T['pos']
    rgb = K._plain(T, 0x7A766C, scheme, 0.2, 1.0, var=0.12)[0]
    speck = materials.vnoise(pos, 0.045, 11)
    rgb = rgb * (0.7 + 0.6 * speck)[:, None]
    return np.clip(rgb, 0, 255), 255


materials.register('concrete', _concrete, density=0.6)
materials.register('gravel', _gravel, density=1.2)


def new(id, base, second=None, seed=1, dirt=0.3, density=DENSITY):
    sc = materials.Scheme(base, second=second, dirt=dirt, wear=0.2, seed=seed, gloss=0.45)
    m = Model(id, sc, density=density, tex_width=1024)
    return m


def wheel_part(m, parent, name, c, side, r, w, steer=False, twin=0.0, style='steel', n=16):
    """A wheel as a part: spins about its axle (spin = radius), the front ones steer. twin: the inner tyre's offset."""
    p = parent.part(name, tuple(c))
    p.spin = r
    p.steer = steer
    p.add(geom.wheel(c, side, r, w, rim_ratio=0.62, style=style, n=n))
    if twin:
        c2 = np.array(c) - np.array([side * twin, 0, 0])
        p.add(geom.wheel(c2, side, r, w, rim_ratio=0.62, style=style, n=n))
    return p


def civil_cab(m, body, zf, hw, y0, top, L=2.2, mat='paint2', seat_y=1.75):
    """A flat-fronted lorry cab: the windscreen, door glass, grille, lamps, bumper, mirrors, steps, roof deflector."""
    prof = [(zf - L, y0, 0.05), (zf - 0.03, y0, 0.05), (zf, y0 + 0.15, 0.07), (zf, top - 1.25, 0.03, 'ws0'), (zf - 0.12, top - 0.22, 0, 'ws1'),
            (zf - 0.2, top, 0.1), (zf - L + 0.1, top, 0.08)]
    cab = K.Cab(prof, hw, r=0.1)
    cab.windscreen([(-hw + 0.17, -0.04), (0.04, hw - 0.17)])
    cab.side_window(zf - 1.15, zf - 0.2, top - 1.3, top - 0.36, follow=0.08)
    body.add(cab.geo(mat))
    g = Geo(40)
    # Grille between the lamps, bumper with the plate, step, handrail, mirrors, roof deflector with its marker lamps.
    g.extend(geom.panel((0, y0 + 0.55, zf + 0.004), hw * 1.25, 0.55, (1, 0, 0), (0, 1, 0), 'grille'))
    g.extend(K.rbox((-hw + 0.04, y0 - 0.3, zf - 0.06), (hw - 0.04, y0 + 0.05, zf + 0.16), 'plastic', r=0.04))
    g.extend(geom.panel((0, y0 - 0.12, zf + 0.162), 0.5, 0.12, (1, 0, 0), (0, 1, 0), 'plate'))
    for s in (-1, 1):
        g.extend(K.rect_lamp((s * (hw - 0.3), y0 + 0.2, zf + 0.0), 0.32, 0.16, 'headlight_box', (0, 0, 1)))
        g.extend(box((s * (hw - 0.12) - 0.05, y0 + 0.12, zf - 0.02), (s * (hw - 0.12) + 0.05, y0 + 0.27, zf + 0.02), 'indicator'))
        g.extend(K.step(min(s * (hw - 0.4), s * (hw + 0.12)), max(s * (hw - 0.4), s * (hw + 0.12)), y0 - 0.32, zf - 1.1, zf - 0.35))
        g.extend(K.handrail([(s * (hw + 0.03), y0 + 0.4, zf - 1.15), (s * (hw + 0.06), y0 + 0.4, zf - 1.15), (s * (hw + 0.06), y0 + 1.3, zf - 1.15),
                             (s * (hw + 0.03), y0 + 1.32, zf - 1.15)]))
        g.extend(K.mirror((s * hw, top - 0.55, zf - 0.3), (s * (hw + 0.3), top - 0.62, zf + 0.0), (0.2, 0.4), s))
        g.extend(box((s * hw - (0.02 if s > 0 else -0.0), y0 + 1.25, zf - 0.75), (s * hw + (0.0 if s > 0 else -0.02) + (0.02 if s > 0 else 0.0), y0 + 1.28, zf - 0.58), 'metal_dark'))
    g.extend(K.rbox((-hw + 0.15, top, zf - L + 0.3), (hw - 0.15, top + 0.5, zf - 0.55), mat, r=0.12))
    for x in (-0.4, 0.0, 0.4):
        g.extend(cbox((x, top + 0.46, zf - 0.7), (0.1, 0.05, 0.05), 'indicator'))
    for s in (-1, 1):
        wx = s * (hw - 0.0)
        body.add(K.door_seams(m, wx, zf - 1.35, zf - 0.1, y0 + 0.2, top - 0.3, s, hinge_front=True)) if False else None
    body.add(g)
    return cab


def lorry_frame(m, body, z0, z1, axles, r, half=1.0, deck=1.1, tank=True):
    """Frame rails and cross members, axles, springs, fuel tank, battery box, air tanks, exhaust bits; returns the geo."""
    g = Geo(40)
    g.extend(K.frame_rails(0.45, 0.75, deck, z0, z1, w=0.12, cross=tuple(np.linspace(z0 + 0.4, z1 - 0.6, 7))))
    for z, steer, twin in axles:
        g.extend(K.axle(half - 0.2, r, z))
    zs = [a[0] for a in axles if a[2]]
    if len(zs) >= 2:
        g.extend(K.bogie_springs(0.5, r + 0.1, zs[0], zs[1]))
    if tank:
        g.extend(K.fuel_tank(0.5, 1.12, 0.75, 1.3, 0.8, 2.3, 'drum'))
        g.extend(K.toolbox((-1.12, 0.78, 1.0), (-0.55, 1.2, 2.0)))
        g.extend(cyl((-0.7, 0.9, -1.0), (-0.7, 0.9, 0.4), 0.14, 'chassis', n=10))
        g.extend(cyl((0.7, 0.85, -0.6), (0.7, 0.85, 0.5), 0.12, 'chassis', n=10))
    body.add(g)


def lorry_wheels(m, body, axles, r, half, w=0.3):
    for z, steer, twin in axles:
        for s, nm in ((-1, 'l'), (1, 'r')):
            wheel_part(m, body, 'wheel_%d_%s' % (axles.index((z, steer, twin)) + 1, nm), (s * half, r, z), s, r, w, steer, 0.33 if twin else 0.0)


def lorry_guards(m, body, axles, r, half, zf_cab):
    g = Geo(40)
    for z, steer, twin in axles:
        if steer:
            for s in (-1, 1):
                x0, x1 = (half - 0.3, half + 0.26) if s > 0 else (-half - 0.26, -half + 0.3)
                g.extend(K.fender(x0, x1, z, r - 0.04, r + 0.1, -5, 185))
    rz = [a[0] for a in axles if a[2]]
    if rz:
        for s in (-1, 1):
            x0, x1 = (half - 0.55, half + 0.3) if s > 0 else (-half - 0.3, -half + 0.55)
            g.extend(K.flat_guard(x0, x1, min(rz) - 0.75, max(rz) + 0.75, 2 * r + 0.12, 'paint', lip=0.16, flap=0.35))
    body.add(g)


def rear_end(g, zr, hw, y=1.0):
    """Rear lamps, under-run bar, plate, hitch eye."""
    for s in (-1, 1):
        g.extend(K.lamp_cluster((s * (hw - 0.2), y + 0.05, zr), s))
    g.extend(K.rbox((-hw + 0.1, 0.55, zr - 0.18), (hw - 0.1, 0.72, zr - 0.02), 'chassis', r=0.02))
    for s in (-1, 1):
        g.extend(box((s * (hw - 0.15) - 0.03, 0.72, zr - 0.1), (s * (hw - 0.15) + 0.03, y, zr - 0.02), 'chassis'))
    g.extend(geom.panel((0, 0.64, zr - 0.182), 0.5, 0.12, (-1, 0, 0), (0, 1, 0), 'plate'))


def cab_inside(body, cab, zf, y0):
    seats = [('driver', -0.5, y0 + 0.7, zf - 1.2), ('gunner', 0.5, y0 + 0.7, zf - 1.2)]
    K.cab_inside_part(body, 'cab_inside', cab, seats, dash_y=y0 + 0.95, tilt=-62)


def stripe(g, hw, y0, y1, z0, z1, mat='paint2'):
    g.extend(box((-hw - 0.006, y0, z0), (hw + 0.006, y1, z1), mat, faces=('left', 'right')))


def lorry_box():
    m = new('lorry_box', 0xE4E6E6, second=0x2B4E8F, seed=4101)
    body = m.part('body')
    axles = [(3.6, True, False), (-2.6, False, True), (-3.9, False, True)]
    lorry_frame(m, body, -5.0, 4.6, axles, 0.52)
    cab = civil_cab(m, body, 5.0, 1.25, 1.1, 3.4)
    cab_inside(body, cab, 5.0, 1.1)
    lorry_guards(m, body, axles, 0.52, 1.0, 5.0)
    lorry_wheels(m, body, axles, 0.52, 1.0)
    g = Geo(40)
    # The box body: white panels, a blue band along the sides, ribbed roof, corner posts, rear doors with lock bars.
    g.extend(K.rbox((-1.27, 1.15, -5.2), (1.27, 3.9, 2.6), 'paint', r=0.05))
    stripe(g, 1.27, 1.6, 2.0, -5.0, 2.4)
    for z in np.linspace(-4.6, 2.2, 8):
        g.extend(box((-1.27 - 0.012, 1.2, z - 0.015), (-1.27, 3.85, z + 0.015), 'paint', faces=('left',)))
        g.extend(box((1.27, 1.2, z - 0.015), (1.27 + 0.012, 3.85, z + 0.015), 'paint', faces=('right',)))
    for s in (-1, 1):
        g.extend(K.rbox((s * 1.27 - 0.05, 1.12, -5.22), (s * 1.27 + 0.05, 3.92, -5.1), 'plastic', r=0.02))
        g.extend(K.rbox((s * 1.27 - 0.05, 1.12, 2.5), (s * 1.27 + 0.05, 3.92, 2.62), 'plastic', r=0.02))
    g.extend(K.rbox((-1.27, 3.88, -5.22), (1.27, 3.98, 2.62), 'plastic', r=0.03))
    g.extend(K.rbox((-1.27, 1.1, -5.22), (1.27, 1.2, 2.62), 'plastic', r=0.03))
    g.extend(geom.panel((0, 2.5, -5.2), 2.3, 2.4, (-1, 0, 0), (0, 1, 0), 'paint'))
    for x in (-0.55, 0.55):
        g.extend(cyl((x, 1.5, -5.28), (x, 3.7, -5.28), 0.02, 'chrome', n=6))
        g.extend(box((x - 0.05, 2.4, -5.32), (x + 0.05, 2.6, -5.26), 'chrome'))
    g.extend(box((-0.02, 1.3, -5.24), (0.02, 3.8, -5.2), 'metal_dark'))
    m.decals.append(K.seam_decal(V(0, 2.5, -5.205), V(-2.3, 0, 0), V(0, 2.4, 0), [(-0.5, -0.5, 0.5, 0.5)], width=0.01))
    # Side underrun guards, a toolbox, the spare wheel carrier.
    for s in (-1, 1):
        g.extend(K.rbox((s * 1.2 - 0.02, 0.85, -2.0), (s * 1.2 + 0.02, 1.1, 1.8), 'chassis', r=0.01))
    g.extend(cyl((0.0, 0.8, 1.0), (0.0, 0.8, 1.6), 0.0, 'chassis', n=4) if False else Geo(1))
    rear_end(g, -5.23, 1.2)
    g.extend(K.exhaust_stack(1.12, 1.1, 3.0, 4.0 - 0.0, r=0.06))
    body.add(g)
    return m


def oval(c, rx, ry, z0, z1, mat, n=20):
    cx, cy = c
    prof = [(z0, 0.02), (z0 + 0.03, 0.72), (z0 + 0.08, 0.92), (z0 + 0.17, 0.99), (z0 + 0.35, 1.0), (z1 - 0.35, 1.0), (z1 - 0.17, 0.99),
            (z1 - 0.08, 0.92), (z1 - 0.03, 0.72), (z1, 0.02)]
    rings = [[V(cx + math.cos(2 * math.pi * k / n) * rx * f, cy + math.sin(2 * math.pi * k / n) * ry * f, z) for k in range(n)] for z, f in prof]
    return K.flipped(geom.loft(rings, mat, closed=True))


def lorry_tank():
    m = new('lorry_tank', 0xC5C9CC, second=0xB1271D, seed=4102, dirt=0.25)
    m.scheme.gloss = 0.7
    body = m.part('body')
    axles = [(3.6, True, False), (-2.8, False, True), (-4.1, False, True)]
    lorry_frame(m, body, -5.2, 4.6, axles, 0.52)
    cab = civil_cab(m, body, 5.0, 1.25, 1.1, 3.4)
    cab_inside(body, cab, 5.0, 1.1)
    lorry_guards(m, body, axles, 0.52, 1.0, 5.0)
    lorry_wheels(m, body, axles, 0.52, 1.0)
    g = Geo(40)
    g.extend(box((-1.0, 1.1, -5.3), (1.0, 1.3, 2.4), 'chassis'))
    for z in (-4.6, -3.0, -1.4, 0.2, 1.8):
        g.extend(box((-1.0, 1.25, z - 0.12), (1.0, 1.5, z + 0.12), 'chassis'))
    # The tank: an oval drum with a hazard band (orange), rings, manholes, the walkway and rails.
    g.extend(oval((0, 2.2), 1.2, 1.05, -5.4, 2.5, 'paint', n=24))
    g.extend(oval((0, 2.2), 1.208, 1.058, -3.0, 1.0, 'orange', n=24)) if False else None
    for z in (-3.6, -2.2, -0.8, 0.6, 2.0):
        ring = [V(math.cos(2 * math.pi * k / 24) * 1.215, 2.2 + math.sin(2 * math.pi * k / 24) * 1.065, z) for k in range(24)]
        g.extend(geom.tube(ring, 0.016, 'paint', n=4, closed=True))
    # The hazard band: a ring of orange panels round the drum.
    ring0 = [V(math.cos(2 * math.pi * k / 24) * 1.206, 2.2 + math.sin(2 * math.pi * k / 24) * 1.056, -3.0) for k in range(24)]
    ring1 = [V(math.cos(2 * math.pi * k / 24) * 1.206, 2.2 + math.sin(2 * math.pi * k / 24) * 1.056, 1.0) for k in range(24)]
    g.extend(K.flipped(geom.loft([ring0, ring1], 'orange', closed=True)))
    g.extend(box((-0.3, 3.22, -5.0), (0.3, 3.27, 2.2), 'grating'))
    for z in (-4.0, -1.6, 0.8):
        g.extend(cyl((0, 3.2, z), (0, 3.38, z), 0.22, 'paint', n=14, bevel=0.025))
        g.extend(cyl((0, 3.38, z), (0, 3.41, z), 0.17, 'metal_dark', n=14))
    for s in (-1, 1):
        g.extend(K.handrail([(s * 0.32, 3.22, -5.0), (s * 0.32, 3.55, -5.0), (s * 0.32, 3.55, 2.2), (s * 0.32, 3.22, 2.2)]))
    # Rear pump cabinet and ladder, the delivery hose reel on the side.
    g.extend(K.rbox((-1.0, 0.9, -5.55), (1.0, 1.9, -5.35), 'paint', r=0.03))
    g.extend(geom.panel((0, 1.4, -5.552), 1.8, 0.9, (-1, 0, 0), (0, 1, 0), 'plate'))
    for k in range(7):
        g.extend(box((0.6, 1.5 + 0.28 * k, -5.62), (0.95, 1.55 + 0.28 * k, -5.56), 'metal_dark'))
    for x in (0.6, 0.95):
        g.extend(box((x - 0.02, 1.4, -5.64), (x + 0.02, 3.4, -5.58), 'metal_dark'))
    for s in (-1, 1):
        g.extend(cyl((s * 1.2, 1.15, -4.7), (s * 1.2 + s * 0.25, 1.15, -4.7), 0.27, 'rubber', n=12))
    rear_end(g, -5.58, 1.15)
    g.extend(K.exhaust_stack(1.12, 1.1, 3.0, 4.0, r=0.06))
    body.add(g)
    return m


def tipper():
    m = new('tipper', 0xD9701B, second=0x3C3F42, seed=4103, dirt=0.35)
    body = m.part('body')
    axles = [(3.0, True, False), (-2.2, False, True), (-3.5, False, True)]
    r = 0.55
    lorry_frame(m, body, -4.0, 4.0, axles, r)
    cab = civil_cab(m, body, 4.4, 1.25, 1.15, 3.4)
    cab_inside(body, cab, 4.4, 1.15)
    lorry_guards(m, body, axles, r, 1.0, 4.4)
    lorry_wheels(m, body, axles, r, 1.0)
    g = Geo(40)
    # Sub-frame with the hoist ram, hinge at the rear; the tub: floor, ribbed sides, headboard with a canopy over the cab, gravel.
    g.extend(box((-1.1, 1.1, -4.0), (1.1, 1.22, 1.9), 'chassis'))
    g.extend(K.ram((0.0, 1.15, 0.6), (0.0, 1.6, 1.1), r=0.1))
    for s in (-1, 1):
        x0, x1 = (1.15, 1.27) if s > 0 else (-1.27, -1.15)
        g.extend(K.rbox((x0, 1.2, -4.1), (x1, 2.6, 1.9), 'paint2', r=0.03))
        for z in np.linspace(-3.7, 1.5, 7):
            g.extend(box((x0 - (0.02 if s < 0 else 0.0), 1.25, z - 0.04), (x1 + (0.02 if s > 0 else 0.0), 2.62, z + 0.04), 'paint2'))
        g.extend(K.rbox((x0 - 0.02, 2.58, -4.1), (x1 + 0.02, 2.7, 1.9), 'paint2', r=0.03))
    g.extend(K.rbox((-1.27, 1.2, -4.1), (1.27, 1.38, 1.9), 'paint2', r=0.03))
    g.extend(K.rbox((-1.27, 1.2, 1.78), (1.27, 3.0, 1.9), 'paint2', r=0.03))
    # The canopy over the cab (a sloping plate) and the tailgate with its hinges and latches.
    g.extend(K.rbox((-1.27, 2.95, 1.78), (1.27, 3.05, 3.3), 'paint2', r=0.03))
    g.extend(K.rbox((-1.25, 1.35, -4.12), (1.25, 2.6, -4.02), 'paint2', r=0.02))
    for s in (-1, 1):
        g.extend(cyl((s * 1.1, 2.6, -4.12), (s * 1.1, 2.75, -4.12), 0.05, 'chrome', n=8))
    g.extend(box((-1.2, 1.36, -4.0), (1.2, 2.5, 1.78), 'gravel', faces=('top',)))
    # A gravel heap: a loft of a few rings (soft pile).
    heap = geom.loft([[V(x, 2.5, z) for x, z in ((-1.1, -3.9), (1.1, -3.9), (1.1, 1.7), (-1.1, 1.7))],
                      [V(x, 2.85, z) for x, z in ((-0.85, -3.5), (0.85, -3.5), (0.85, 1.3), (-0.85, 1.3))],
                      [V(x, 2.98, z) for x, z in ((-0.5, -3.0), (0.5, -3.0), (0.5, 0.8), (-0.5, 0.8))]], 'gravel', closed=True, smooth=60)
    g.extend(K.flipped(heap) if False else heap)
    g.extend(box((-0.5, 2.98, -3.0), (0.5, 2.99, 0.8), 'gravel', faces=('top',)))
    g.extend(box((-1.15, 1.38, -4.0), (1.15, 2.5, -3.98), 'gravel', faces=('front',)))
    g.extend(box((-1.14, 2.45, -4.0), (1.14, 2.5, 1.78), 'gravel', faces=('top',)))
    rear_end(g, -4.14, 1.2, y=1.15)
    g.extend(K.exhaust_stack(1.12, 1.1, 3.0, 3.6, r=0.06))
    body.add(g)
    return m


def tractor():
    m = new('tractor', 0xB4271B, second=0x2E4F88, seed=4104, dirt=0.4)
    body = m.part('body')
    g = Geo(40)
    # Chassis, the bonnet with grille and louvres, lamps, the exhaust.
    g.extend(K.rbox((-0.3, 0.7, -1.0), (0.3, 1.0, 2.35), 'chassis', r=0.03))
    g.extend(K.rbox((-0.55, 0.78, 0.4), (0.55, 1.75, 2.35), 'paint', r=0.1, segs=2))
    g.extend(geom.panel((0, 1.22, 2.352), 0.78, 0.8, (1, 0, 0), (0, 1, 0), 'grille'))
    g.extend(K.rbox((-0.5, 0.6, 2.3), (0.5, 0.85, 2.52), 'metal_dark', r=0.04))
    for s in (-1, 1):
        g.extend(geom.panel((s * 0.552, 1.3, 1.5), 1.1, 0.5, (0, 0, 1) if s > 0 else (0, 0, -1), (0, 1, 0), 'grille'))
        g.extend(K.rect_lamp((s * 0.38, 1.5, 2.35), 0.2, 0.16, 'headlight_box', (0, 0, 1)))
    g.extend(cyl((0.35, 1.7, 1.55), (0.35, 2.85, 1.55), 0.045, 'exhaust', n=8))
    g.extend(cyl((0.35, 2.82, 1.55), (0.35, 2.9, 1.55), 0.07, 'exhaust', n=8))
    # The cab: floor, pillars, glass all round, roof with a deflector; the steps and the seat.
    g.extend(K.rbox((-0.78, 1.05, -1.35), (0.78, 1.3, 0.45), 'paint', r=0.03))
    for x in (-0.76, 0.73):
        for z in (-1.3, 0.38):
            g.extend(box((x, 1.3, z), (x + 0.03, 2.8, z + 0.03), 'paint'))
    g.extend(K.pane([V(-0.7, 1.5, 0.42), V(0.7, 1.5, 0.42), V(0.7, 2.75, 0.42), V(-0.7, 2.75, 0.42)], frame=0.0))
    g.extend(K.pane([V(0.7, 1.5, -1.28), V(-0.7, 1.5, -1.28), V(-0.7, 2.75, -1.28), V(0.7, 2.75, -1.28)], frame=0.0))
    for s in (-1, 1):
        g.extend(K.pane([V(s * 0.75, 1.5, 0.38), V(s * 0.75, 1.5, -1.28), V(s * 0.75, 2.75, -1.28), V(s * 0.75, 2.75, 0.38)] if s < 0 else
                        [V(s * 0.75, 1.5, -1.28), V(s * 0.75, 1.5, 0.38), V(s * 0.75, 2.75, 0.38), V(s * 0.75, 2.75, -1.28)], frame=0.0))
    g.extend(K.rbox((-0.85, 2.78, -1.45), (0.85, 2.92, 0.55), 'paint2' if False else 'plastic', r=0.04))
    g.extend(K.rbox((-0.85, 2.8, -1.45), (0.85, 2.92, 0.55), 'white', r=0.05))
    g.extend(K.rbox((-0.25, 1.3, -1.0), (0.25, 1.75, -0.5), 'seat', r=0.05))
    g.extend(geom.panel((0, 1.7, 0.2), 0.0001, 0.0001, (1, 0, 0), (0, 1, 0), 'seat') if False else Geo(1))
    for s in (-1, 1):
        g.extend(K.step(min(s * 0.62, s * 0.9), max(s * 0.62, s * 0.9), 0.62, -0.6, -0.1))
        # The mudguards over the big wheels, a lamp on top.
        g.extend(K.fender(s * 0.98 - 0.3 if s > 0 else -1.28, s * 0.98 + 0.3 if s > 0 else -0.68, -0.6, 0.75, 0.9, -15, 195))
        g.extend(K.fender(s * 0.78 - 0.18 if s > 0 else -0.96, s * 0.78 + 0.18 if s > 0 else -0.6, 1.9, 0.45, 0.58, -15, 195) if False else Geo(1))
        g.extend(K.mirror((s * 0.8, 2.5, 0.3), (s * 1.0, 2.55, 0.7), (0.12, 0.25), s))
    g.extend(K.whip_antenna((-0.7, 2.93, -1.2), 0.5))
    # The rear hitch, the drawbar and the seed drill: toolbar, hopper and coulters, its two wheels.
    g.extend(box((-0.1, 0.6, -2.4), (0.1, 0.78, -1.35), 'metal_dark'))
    g.extend(K.rbox((-1.8, 0.55, -3.2), (1.8, 1.25, -2.4), 'steel', r=0.04))
    g.extend(K.rbox((-1.7, 1.25, -3.1), (1.7, 1.62, -2.5), 'paint2', r=0.06))
    for x in np.linspace(-1.5, 1.5, 6):
        g.extend(box((x - 0.04, 1.62, -2.95), (x + 0.04, 1.7, -2.65), 'metal_dark'))
        g.extend(box((x - 0.03, 0.05, -3.1), (x + 0.03, 0.55, -3.04), 'steel'))
        g.extend(box((x - 0.07, 0.04, -3.14), (x + 0.07, 0.1, -3.0), 'steel'))
    g.extend(box((-1.7, 0.7, -3.2), (1.7, 0.76, -3.14), 'metal_dark'))
    for s in (-1, 1):
        g.extend(box((s * 1.6 - 0.05, 0.3, -2.9), (s * 1.6 + 0.05, 0.6, -2.7), 'metal_dark'))
    body.add(g)
    # Wheels: big at the back, small in front (steering), the drill's own.
    for s, nm in ((-1, 'l'), (1, 'r')):
        wheel_part(m, body, 'wheel_1_%s' % nm, (s * 0.98, 0.45, 1.9), s, 0.45, 0.32, True, style='steel', n=16)
        wheel_part(m, body, 'wheel_2_%s' % nm, (s * 0.98, 0.75, -0.6), s, 0.75, 0.5, False, style='steel', n=18)
        wheel_part(m, body, 'wheel_3_%s' % nm, (s * 1.6, 0.275, -2.8), s, 0.275, 0.2, False, style='steel', n=12)
    return m


# ---------------------------------------------------------------------------------------------------------------
# The tower crane


def truss_mast(g, h, hw, bays, rc=0.1, rb=0.035, mat='paint'):
    """A square lattice mast from y = 1 to h: four corner legs, diagonals (alternating) on every face, rings."""
    ys = np.linspace(1.0, h, bays + 1)
    corners = [(-hw, -hw), (hw, -hw), (hw, hw), (-hw, hw)]
    for cx, cz in corners:
        g.extend(box((cx - rc, 1.0, cz - rc), (cx + rc, h, cz + rc), mat))
    for k in range(bays):
        y0, y1 = ys[k], ys[k + 1]
        for i in range(4):
            (ax, az), (bx, bz) = corners[i], corners[(i + 1) % 4]
            if k % 2 == 0:
                p, q = V(ax, y0, az), V(bx, y1, bz)
            else:
                p, q = V(bx, y0, bz), V(ax, y1, az)
            g.extend(geom.tube([p, q], rb, mat, n=4, caps=False))
        for i in range(4):
            (ax, az), (bx, bz) = corners[i], corners[(i + 1) % 4]
            g.extend(geom.tube([V(ax, y1, az), V(bx, y1, bz)], rb * 0.9, mat, n=4, caps=False))


def truss_jib(g, z0, z1, y0, hw, hgt, bays, rc=0.06, rb=0.03, mat='paint'):
    """A triangular lattice jib along z: two lower chords at +-hw (y0), one upper at the apex (y0 + hgt), zig-zag braces."""
    zs = np.linspace(z0, z1, bays + 1)
    for p in ((-hw, y0), (hw, y0), (0.0, y0 + hgt)):
        g.extend(geom.tube([V(p[0], p[1], z0), V(p[0], p[1], z1)], rc, mat, n=4, caps=False))
    for k in range(bays):
        za, zb = zs[k], zs[k + 1]
        zm = (za + zb) / 2
        # side braces
        for s in (-1, 1):
            g.extend(geom.tube([V(s * hw, y0, za), V(0.0, y0 + hgt, zm), V(s * hw, y0, zb)], rb, mat, n=4, caps=False))
        g.extend(geom.tube([V(-hw, y0, zb), V(hw, y0, zb)], rb, mat, n=4, caps=False))


def crane():
    m = new('tower_crane', 0xE2A913, second=0xF2F2EE, seed=4105, dirt=0.35, density=12)
    body = m.part('body')
    h = 40.0
    g = Geo(40)
    # Base: the concrete blocks and the foot of the mast on them.
    g.extend(K.rbox((-2.2, 0.0, -2.2), (2.2, 1.0, 2.2), 'concrete', r=0.04))
    g.extend(K.rbox((-1.15, 1.0, -1.15), (1.15, 1.25, 1.15), 'paint', r=0.02))
    truss_mast(g, h, 0.85, 26, rc=0.1, rb=0.035)
    # A ladder-cage down one face of the mast is only a rail here; the tie-in collars every 10 m.
    for y in (10.0, 20.0, 30.0):
        g.extend(box((-0.98, y, -0.98), (0.98, y + 0.15, 0.98), 'paint'))
    body.add(g)
    top = m.part('spin', pivot=(0, h, 0))
    t = Geo(40)
    # The slewing ring and the operator's cab (glass on two sides), the machinery house.
    t.extend(K.rbox((-1.3, h, -1.3), (1.3, h + 1.0, 1.3), 'paint', r=0.05))
    t.extend(cyl((0, h - 0.05, 0), (0, h + 0.05, 0), 1.35, 'metal_dark', n=20))
    t.extend(K.rbox((0.95, h + 1.0, 0.0), (2.2, h + 3.0, 1.6), 'paint2', r=0.06))
    t.extend(geom.panel((2.2 + 0.004, h + 2.2, 0.8), 1.4, 1.2, (0, 0, -1), (0, 1, 0), 'glass'))
    t.extend(geom.panel((1.575, h + 2.2, 1.604), 1.1, 1.2, (1, 0, 0), (0, 1, 0), 'glass'))
    t.extend(K.rbox((0.9, h + 3.0, -0.1), (2.3, h + 3.12, 1.7), 'paint2', r=0.03))
    # The jib forward (+z): truss with the trolley and the hook on its cable.
    L = 45.0
    truss_jib(t, 1.3, L, h + 1.0, 0.8, 1.7, 22, rc=0.07, rb=0.03)
    t.extend(box((-0.9, h + 0.4, 24.0), (0.9, h + 1.0, 26.0), 'metal_dark'))
    t.extend(geom.tube([V(0, h + 0.4, 25.0), V(0, h - 17.0, 25.0)], 0.025, 'black', n=4, caps=False))
    t.extend(K.rbox((-0.35, h - 18.0, 24.65), (0.35, h - 17.0, 25.35), 'red', r=0.04))
    t.extend(K.rbox((-0.3, h - 17.2, 24.7), (0.3, h - 16.9, 25.3), 'metal_dark', r=0.03))
    # The counter-jib behind (-z): flat truss, the concrete weights; the peak, the ties to both ends.
    truss_jib(t, -14.0, -1.3, h + 1.0, 0.9, 0.5, 8, rc=0.06, rb=0.03)
    t.extend(K.rbox((-1.1, h - 1.2, -13.5), (1.1, h + 1.0, -10.5), 'concrete', r=0.04))
    t.extend(K.rbox((-0.6, h + 1.0, -0.6), (0.6, h + 7.5, 0.6), 'paint', r=0.04))
    for ends in (((0.0, h + 7.3, 0.0), (0.0, h + 2.7, 30.0)), ((0.0, h + 7.3, 0.0), (0.0, h + 1.3, -12.5))):
        t.extend(geom.tube([V(*ends[0]), V(*ends[1])], 0.04, 'black', n=4, caps=False))
    t.extend(box((-0.2, h + 7.5, -0.2), (0.2, h + 7.8, 0.2), 'redlight'))
    t.extend(box((-0.2, h + 2.6, L - 0.6), (0.2, h + 2.9, L - 0.2), 'redlight'))
    top.add(t)
    return m


def models():
    return [lorry_box(), lorry_tank(), tipper(), tractor(), crane()]
