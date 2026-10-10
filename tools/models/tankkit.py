"""
1.44 "Vehicles": the small things that make a tank look like one - stowage bins with straps, smoke grenade launchers,
the commander's machine gun, periscopes, antennas, tow cables, spare track links, the unditching log, engine grilles,
skirts made of separate panels, return rollers, ERA bricks on the turret roof, a rolled tarpaulin. Metres, x right,
y up, z forward.
"""
from airdefense import barrel


def bin_box(part, x0, x1, y0, y1, z0, z1, paint, straps=2, lid=True):
    """A stowage bin: the box, a lid lip on top and dark straps round it."""
    part.box((x0, y0, z0), (x1, y1, z1), paint)
    if lid:
        part.box((x0 - 0.02, y1, z0 - 0.02), (x1 + 0.02, y1 + 0.04, z1 + 0.02), paint)
    for i in range(straps):
        t = (i + 1) / (straps + 1)
        if z1 - z0 >= x1 - x0:
            z = z0 + (z1 - z0) * t
            part.box((x0 - 0.015, y0 + 0.05, z - 0.03), (x1 + 0.015, y1 + 0.045, z + 0.03), 'dark')
        else:
            x = x0 + (x1 - x0) * t
            part.box((x - 0.03, y0 + 0.05, z0 - 0.015), (x + 0.03, y1 + 0.045, z1 + 0.015), 'dark')


def smoke_launchers(part, x, y, z, n, side, step=0.11, tube=0.09, length=0.28, paint='dgrey'):
    """A row of smoke grenade tubes on a bracket, along x from x outwards (side = +1 right, -1 left), pointing forward."""
    part.box((x + (0 if side > 0 else -step * n), y - 0.06, z - 0.12), (x + (step * n if side > 0 else 0), y - 0.02, z + 0.05), paint)
    for i in range(n):
        cx = x + side * (i + 0.5) * step
        cy = y + (i % 2) * 0.05
        part.box((cx - tube / 2, cy, z - 0.1), (cx + tube / 2, cy + tube, z + length - 0.1), paint)
        part.box((cx - tube / 2 + 0.015, cy + 0.015, z + length - 0.1), (cx + tube / 2 - 0.015, cy + tube - 0.015, z + length - 0.08), 'black')


def cupola(part, x, y, z, paint, mg=True, r=0.36, h=0.16):
    """The commander's cupola: a low ring, the hatch, periscopes round it, a machine gun on its mount."""
    part.box((x - r, y, z - r), (x + r, y + h, z + r), paint)
    part.box((x - r * 0.75, y + h, z - r * 0.75), (x + r * 0.75, y + h + 0.06, z + r * 0.75), paint)
    for dx, dz, face in ((0, r, 'front'), (r, 0, 'right'), (-r, 0, 'left')):
        part.box((x + dx - 0.07, y + 0.05, z + dz - 0.07), (x + dx + 0.07, y + h - 0.02, z + dz + 0.07), 'dark', sides={face: 'glass'})
    if mg:
        part.box((x + r * 0.2, y + h + 0.06, z - 0.05), (x + r * 0.2 + 0.08, y + h + 0.32, z + 0.05), 'dgrey')
        part.box((x + r * 0.2 - 0.04, y + h + 0.28, z - 0.25), (x + r * 0.2 + 0.12, y + h + 0.4, z + 0.3), 'dgrey')
        barrel(part, x + r * 0.2 + 0.04, y + h + 0.34, z + 0.3, 0.9, 0.035, 'dark')
        part.box((x + r * 0.2 + 0.1, y + h + 0.26, z - 0.1), (x + r * 0.2 + 0.24, y + h + 0.4, z + 0.12), 'olive')


def hatch(part, x, y, z, w, l, paint):
    """A hatch: its lid a little above the roof, a hinge along its back."""
    part.box((x - w / 2, y, z - l / 2), (x + w / 2, y + 0.05, z + l / 2), paint)
    part.box((x - w / 2 + 0.05, y + 0.05, z - l / 2), (x + w / 2 - 0.05, y + 0.09, z - l / 2 + 0.08), 'dgrey')


def periscope(part, x, y, z, paint, facing='front', w=0.16):
    """A periscope head sticking up from the roof, its glass to the front (or a side)."""
    part.box((x - w / 2, y, z - 0.07), (x + w / 2, y + 0.1, z + 0.07), paint, sides={facing: 'glass'})


def antenna(part, x, y, z, h, base=0.07):
    """A whip antenna on its base."""
    part.box((x - base, y, z - base), (x + base, y + 0.12, z + base), 'dark')
    part.box((x - 0.015, y + 0.12, z - 0.015), (x + 0.015, y + h, z + 0.015), 'black')


def cables(part, side, x, y, z0, z1):
    """A tow cable along the hull side (two strands with eyes at the ends)."""
    for k in (0, 1):
        yy = y + k * 0.06
        part.box((x - 0.025, yy, z0), (x + 0.025, yy + 0.05, z1), 'steel')
    part.box((x - 0.04, y - 0.02, z0 - 0.08), (x + 0.04, y + 0.12, z0), 'steel')
    part.box((x - 0.04, y - 0.02, z1), (x + 0.04, y + 0.12, z1 + 0.08), 'steel')


def track_links(part, x, y, z, n, side):
    """Spare track links hung on a plate: a row of dark plates with their pins."""
    for i in range(n):
        zz = z + i * 0.2
        part.box((x - (0.05 if side < 0 else 0), y, zz), (x + (0.05 if side > 0 else 0), y + 0.36, zz + 0.17), 'track')


def log(part, y, z, half, wood='wood'):
    """The unditching beam carried across the back of Soviet tanks, on its two rails."""
    part.box((-half, y, z - 0.13), (half, y + 0.26, z + 0.13), wood)
    for s in (-1, 1):
        part.box((s * half * 0.8 - 0.05, y - 0.05, z - 0.16), (s * half * 0.8 + 0.05, y + 0.3, z + 0.16), 'dark')


def grille(part, x0, x1, y, z0, z1, paint='mesh_dark'):
    """An engine deck grille over a dark opening (the louvres read as a lattice)."""
    part.box((x0, y, z0), (x1, y + 0.02, z1), 'black')
    part.box((x0 + 0.03, y + 0.02, z0 + 0.03), (x1 - 0.03, y + 0.05, z1 - 0.03), paint)


def skirt_panels(part, z0, z1, y0, y1, half, paint, n=5, era=False, rubber=True):
    """Side skirts made of separate plates (each a little proud of the last), with ERA packs on the front ones."""
    step = (z1 - z0) / n
    for s in (-1, 1):
        for i in range(n):
            za = z0 + i * step
            zb = za + step - 0.02
            t = 0.06 + (i % 2) * 0.01
            x = s * half
            part.box((x - (t if s > 0 else 0.0), y0, za), (x + (0.0 if s > 0 else t), y1, zb), paint)
            if era and i >= n - 2:
                part.box((x + (0.0 if s > 0 else -0.08) + (-0.0 if s > 0 else 0), y0 + 0.05, za + 0.05),
                         (x + (0.08 if s > 0 else 0.0), y1 - 0.05, zb - 0.05), paint)
        if rubber:
            part.box((s * half - (0.05 if s > 0 else 0.0), y0 - 0.22, z0 + 0.2), (s * half + (0.0 if s > 0 else 0.05), y0, z1 - 0.2), 'rubber')


def roof_era(part, x0, x1, y, z0, z1, paint, cols=3, rows=2):
    """Flat ERA bricks in a grid on a roof."""
    w = (x1 - x0) / cols
    l = (z1 - z0) / rows
    for i in range(cols):
        for j in range(rows):
            part.box((x0 + i * w + 0.02, y, z0 + j * l + 0.02), (x0 + (i + 1) * w - 0.02, y + 0.09, z0 + (j + 1) * l - 0.02), paint)


def tarp(part, x0, x1, y, z, r=0.14):
    """A rolled tarpaulin tied across the back of a turret."""
    part.box((x0, y, z - r), (x1, y + 2 * r, z + r), 'canvas')
    for t in (0.2, 0.8):
        x = x0 + (x1 - x0) * t
        part.box((x - 0.03, y - 0.01, z - r - 0.01), (x + 0.03, y + 2 * r + 0.01, z + r + 0.01), 'dark')


def basket(part, x0, x1, y0, y1, z0, z1):
    """An open stowage basket (bustle rack): a frame of rails with gear inside."""
    part.box((x0, y0, z0), (x1, y0 + 0.04, z1), 'dgrey')
    part.box((x0, y0, z0), (x1, y1, z0 + 0.04), 'mesh_dark')
    part.box((x0, y0, z0), (x0 + 0.04, y1, z1), 'mesh_dark')
    part.box((x1 - 0.04, y0, z0), (x1, y1, z1), 'mesh_dark')
    # Kit bags and boxes in it.
    part.box((x0 + 0.1, y0 + 0.04, z0 + 0.06), (x0 + (x1 - x0) * 0.45, y0 + (y1 - y0) * 0.8, z1 - 0.06), 'canvas')
    part.box((x0 + (x1 - x0) * 0.5, y0 + 0.04, z0 + 0.08), (x1 - 0.1, y0 + (y1 - y0) * 0.6, z1 - 0.08), 'olive')


def headlights(part, z, y, half, guard=True):
    """Headlights in their guards (front)."""
    for s in (-1, 1):
        x = s * half
        part.box((x - 0.1, y, z - 0.08), (x + 0.1, y + 0.16, z), 'dark', sides={'front': 'light'})
        if guard:
            part.box((x - 0.13, y + 0.18, z - 0.1), (x + 0.13, y + 0.21, z + 0.05), 'dark')


def fuel_tanks(part, x0, x1, y, z0, z1, paint, n=3):
    """External fuel tanks on a fender (Soviet tanks): a row of boxes with filler caps."""
    l = (z1 - z0) / n
    for i in range(n):
        za = z0 + i * l + 0.03
        part.box((x0, y, za), (x1, y + 0.42, za + l - 0.06), paint)
        cx = (x0 + x1) / 2
        part.box((cx - 0.05, y + 0.42, za + 0.1), (cx + 0.05, y + 0.47, za + 0.2), 'dark')


def mantlet_cover(el, px, py, pz, w, h):
    """The canvas dust cover over the gun mantlet."""
    el.box((px - w / 2, py - h / 2, pz + 0.3), (px + w / 2, py + h / 2, pz + 0.55), 'canvas')


def sight_box(part, x0, x1, y0, y1, z0, z1, paint, doors=True):
    """A gunner's or commander's sight: an armoured box with its glass window and armoured doors either side."""
    part.box((x0, y0, z0), (x1, y1, z1), paint, sides={'front': 'glass'})
    if doors:
        part.box((x0 - 0.04, y0 + 0.04, z1 - 0.12), (x0, y1 - 0.04, z1 + 0.02), paint)
        part.box((x1, y0 + 0.04, z1 - 0.12), (x1 + 0.04, y1 - 0.04, z1 + 0.02), paint)
