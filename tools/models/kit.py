"""Reusable vehicle pieces: cabs, frames, axles, tracks."""


def axles(m, zs, r, half, width=0.42, steer=1, side='wheel', parent=None, prefix='wheel'):
    """Pairs of wheels at the given z positions; the first {steer} axles steer."""
    n = 0
    for i, z in enumerate(zs):
        for s, tag in ((-1, 'l'), (1, 'r')):
            m.wheel('%s_%d_%s' % (prefix, i + 1, tag), s * half, r, z, r, width, steer=i < steer, side=side, parent=parent)
            n += 1
    return n


def frame(body, z0, z1, y0, y1, half=0.45, style='dark'):
    for s in (-1, 1):
        body.box((s * half - 0.08, y0, z0), (s * half + 0.08, y1, z1), style)
    # Cross members.
    z = z0 + 0.3
    while z < z1 - 0.2:
        body.box((-half, y0 + 0.04, z), (half, y1 - 0.04, z + 0.1), style)
        z += 1.2


def lights(body, z, y, half, front=True):
    if front:
        for s in (-1, 1):
            body.box((s * half - 0.12, y, z), (s * half + 0.12, y + 0.14, z + 0.04), 'light')
    else:
        for s in (-1, 1):
            body.box((s * half - 0.1, y, z - 0.04), (s * half + 0.1, y + 0.1, z), 'redlight')


def hood_cab(body, zf, width, paint, hood_len=1.5, hood_h=1.8, cab_len=1.4, cab_h=2.65, hood_w=1.5, y0=1.05):
    """A bonneted army truck (Ural, KrAZ): bumper, hood, fenders, cab with windows. zf = front of the bumper."""
    hw = width / 2
    # Bumper and grille.
    body.box((-hw + 0.1, y0 - 0.25, zf - 0.25), (hw - 0.1, y0 + 0.1, zf), 'dark')
    body.box((-hood_w / 2, y0 + 0.1, zf - 0.2), (hood_w / 2, hood_h - 0.1, zf - 0.08), 'dark')
    lights(body, zf - 0.08, y0 + 0.25, hw - 0.35)
    # Hood.
    zh = zf - 0.2
    body.box((-hood_w / 2, y0 + 0.1, zh - hood_len), (hood_w / 2, hood_h, zh), paint)
    # Fenders over the front wheels.
    for s in (-1, 1):
        body.box((s * hw - (0.45 if s > 0 else -0.45), y0 + 0.2, zh - hood_len + 0.1), (s * hw, y0 + 0.32, zh - 0.05), paint)
        body.box((s * hw - (0.45 if s > 0 else -0.45), y0 + 0.32, zh - hood_len + 0.1), (s * (hood_w / 2), y0 + 0.42, zh - 0.3), paint)
    # Cab.
    zc = zh - hood_len
    body.box((-hw + 0.05, y0, zc - cab_len), (hw - 0.05, cab_h, zc), paint,
             sides={'front': paint})
    # Windscreen and side windows.
    body.box((-hw + 0.2, cab_h - 0.75, zc), (-0.06, cab_h - 0.18, zc + 0.03), 'glass')
    body.box((0.06, cab_h - 0.75, zc), (hw - 0.2, cab_h - 0.18, zc + 0.03), 'glass')
    for s in (-1, 1):
        x = s * (hw - 0.05)
        body.box((x - 0.02 if s > 0 else x - 0.01, cab_h - 0.75, zc - cab_len + 0.25), (x + 0.01 if s > 0 else x + 0.02, cab_h - 0.2, zc - 0.25), 'glass')
    # Mirrors.
    for s in (-1, 1):
        body.box((s * hw, cab_h - 0.7, zc - 0.05), (s * (hw + 0.2), cab_h - 0.35, zc), 'dark')
    return zc - cab_len


def cabover(body, zf, width, paint, cab_len=1.7, cab_h=3.0, y0=1.0):
    """A flat-fronted cab over the engine (MAN HX, HEMTT-ish). Returns the back of the cab."""
    hw = width / 2
    body.box((-hw + 0.1, y0 - 0.3, zf - 0.25), (hw - 0.1, y0 + 0.05, zf), 'dark')
    body.box((-hw, y0, zf - cab_len), (hw, cab_h, zf - 0.1), paint)
    # Windscreen (two panes) and side windows.
    body.box((-hw + 0.15, cab_h - 0.9, zf - 0.1), (-0.05, cab_h - 0.2, zf - 0.07), 'glass')
    body.box((0.05, cab_h - 0.9, zf - 0.1), (hw - 0.15, cab_h - 0.2, zf - 0.07), 'glass')
    for s in (-1, 1):
        x = s * hw
        body.box((x - 0.01 if s > 0 else x - 0.02, cab_h - 0.85, zf - cab_len * 0.55), (x + 0.02 if s > 0 else x + 0.01, cab_h - 0.25, zf - 0.25), 'glass')
    # Grille and lights.
    body.box((-hw + 0.4, y0 + 0.15, zf - 0.1), (hw - 0.4, y0 + 0.75, zf - 0.06), 'dark')
    lights(body, zf - 0.1, y0 + 0.15, hw - 0.25)
    # Steps.
    for s in (-1, 1):
        body.box((s * hw - (0.3 if s > 0 else -0.3), y0 - 0.4, zf - cab_len + 0.2), (s * hw, y0 - 0.32, zf - 0.6), 'dark')
    return zf - cab_len


def van(body, z0, z1, y0, y1, width, paint, door=True, vents=1):
    """A box body (kung / shelter) with a door at the back and a few details."""
    hw = width / 2
    body.box((-hw, y0, z0), (hw, y1, z1), paint)
    if door:
        body.box((-0.45, y0 + 0.2, z0 - 0.03), (0.45, y1 - 0.25, z0), 'dark')
        body.box((-0.35, y0 - 0.4, z0 - 0.5), (0.35, y0 - 0.32, z0), 'dark')
    for i in range(vents):
        z = z0 + (z1 - z0) * (i + 1) / (vents + 1)
        body.box((hw, y1 - 0.8, z - 0.25), (hw + 0.04, y1 - 0.35, z + 0.25), 'dark')
    # Roof rim.
    body.box((-hw, y1, z0), (hw, y1 + 0.06, z0 + 0.08), paint)
    body.box((-hw, y1, z1 - 0.08), (hw, y1 + 0.06, z1), paint)


def tracks(m, body, z0, z1, top, width, track_w, n, r, paint, side='roadwheel'):
    """Two tracks with road wheels (turning parts), sprocket at the front, idler at the back."""
    hw = width / 2
    for s, tag in ((-1, 'l'), (1, 'r')):
        xc = s * (hw - track_w / 2)
        xa, xb = xc - track_w / 2, xc + track_w / 2
        body.box((xa, top - 0.1, z0 + 0.35), (xb, top, z1 - 0.35), 'track')
        body.box((xa, 0, z0 + 0.7), (xb, 0.1, z1 - 0.7), 'track')
        body.box((xa, 0.1, z1 - 0.7), (xb, top - 0.1, z1 - 0.35), 'track', faces=('front', 'left', 'right', 'top', 'bottom'))
        body.box((xa, 0.1, z0 + 0.35), (xb, top - 0.1, z0 + 0.7), 'track', faces=('back', 'left', 'right', 'top', 'bottom'))
        # Fender / skirt over the track.
        body.box((xa - 0.02, top + 0.02, z0 + 0.1), (xb + 0.02, top + 0.08, z1 - 0.05), paint)
        for i in range(n):
            z = z0 + 0.95 + (z1 - z0 - 2.0) * i / (n - 1)
            m.wheel('road_%d_%s' % (i, tag), xc, r, z, r, track_w * 0.75, side=side, tread='dark')
        m.wheel('sprocket_%s' % tag, xc, top - 0.33, z1 - 0.55, 0.3, track_w * 0.7, side=side, tread='dark')
        m.wheel('idler_%s' % tag, xc, top - 0.35, z0 + 0.55, 0.28, track_w * 0.7, side=side, tread='dark')
