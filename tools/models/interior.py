"""
Insides of the crew compartments (1.26 "Crew"): every cab, wheelhouse and cockpit the outer model marks with
Model.cab() gets an inside model of its own ("<id>_int"), drawn only near the vehicle. What it holds:

  * the walls, the roof and the floor lined from the inside, with the windows left open (found from the outer
    model's glass), so from a seat you see the cab round you and the world through the glass;
  * per kind - truck / armour / boat: a dashboard with dials, a steering wheel that turns with the wheels, pedals,
    a gear lever, seats with backs and head rests, sun visors, a mirror, a radio set, a fire extinguisher, a rifle
    rack; heli: instrument panel and centre console, an overhead switch panel, cyclic sticks and collectives,
    armoured seats; jet: the ejection seat, the panel under a glare shield with the head-up display glass, side
    consoles with the throttle, the stick, the canopy frame.

Seats are also checked against the windows: a seat whose eye (hip + 1.02 m, as the game seats a player) would be
level with the roof or the dashboard is moved so the driver looks out through the middle of the windscreen.

Everything is in the outer model's frame (metres, x right, y up, z forward), on parts named like the outer ones, so
a turret's inside turns with the turret.
"""
from boxgen import Model

T = 0.035  # lining thickness
EYE = 1.02  # the game puts a seated player's eyes this far above the seat point


# ----------------------------------------------------------------------------------------------------------------
# Walls with window holes

def _cells(u0, u1, v0, v1, holes):
    """Rectangles covering [u0,u1]x[v0,v1] minus the holes (merged along u, then along v)."""
    us = sorted({u0, u1} | {min(max(h[i], u0), u1) for h in holes for i in (0, 1)})
    vs = sorted({v0, v1} | {min(max(h[i], v0), v1) for h in holes for i in (2, 3)})
    rows = []
    for j in range(len(vs) - 1):
        va, vb = vs[j], vs[j + 1]
        if vb - va < 1e-4:
            continue
        spans = []
        cur = None
        for i in range(len(us) - 1):
            ua, ub = us[i], us[i + 1]
            if ub - ua < 1e-4:
                continue
            uc, vc = (ua + ub) / 2, (va + vb) / 2
            open_ = any(h[0] < uc < h[1] and h[2] < vc < h[3] for h in holes)
            if open_:
                if cur:
                    spans.append(cur)
                    cur = None
            elif cur and abs(cur[1] - ua) < 1e-6:
                cur = (cur[0], ub)
            else:
                if cur:
                    spans.append(cur)
                cur = (ua, ub)
        if cur:
            spans.append(cur)
        rows.append((va, vb, spans))
    # Merge rows with the same spans.
    out = []
    prev = None
    for va, vb, spans in rows:
        if prev and prev[2] == spans and abs(prev[1] - va) < 1e-6:
            prev = (prev[0], vb, spans)
        else:
            if prev:
                out.append(prev)
            prev = (va, vb, spans)
    if prev:
        out.append(prev)
    rects = []
    for va, vb, spans in out:
        for ua, ub in spans:
            rects.append((ua, ub, va, vb))
    return rects


def _holes(m, box, wall, reach=0.36, given=None):
    """Windows on one wall: the outer model's glass panes lying in that wall's plane, in the wall's (u, v); or the
    windows given with the cab (a hand-made model has its windows painted on)."""
    x0, x1, y0, y1, z0, z1 = box
    if given is not None:
        return [tuple(w[1:]) for w in given if w[0] == wall]
    out = []
    for _, b in m.glass_boxes():
        lo, hi = b.lo, b.hi
        if wall in ('front', 'back'):
            z = z1 if wall == 'front' else z0
            if hi[2] - lo[2] > 0.3 or abs((lo[2] + hi[2]) / 2 - z) > reach:
                continue
            if hi[0] < x0 or lo[0] > x1 or hi[1] < y0 or lo[1] > y1:
                continue
            out.append((lo[0], hi[0], lo[1], hi[1]))
        elif wall in ('left', 'right'):
            x = x0 if wall == 'left' else x1
            if hi[0] - lo[0] > 0.3 or abs((lo[0] + hi[0]) / 2 - x) > reach:
                continue
            if hi[2] < z0 or lo[2] > z1 or hi[1] < y0 or lo[1] > y1:
                continue
            out.append((lo[2], hi[2], lo[1], hi[1]))
        elif wall == 'roof':
            if hi[1] - lo[1] > 0.3 and not (lo[1] <= y1 <= hi[1]):
                continue
            if abs(min(hi[1], y1) - y1) > reach and not (lo[1] <= y1 <= hi[1]):
                continue
            if hi[0] < x0 or lo[0] > x1 or hi[2] < z0 or lo[2] > z1:
                continue
            out.append((lo[0], hi[0], lo[2], hi[2]))
    return out


def line_walls(m, p, box, style, roof_style=None, floor_style='rubber', open_walls=(), tunnel=0.0, windows=None):
    """Lines the compartment from the inside, leaving the windows open. Returns the windows per wall."""
    x0, x1, y0, y1, z0, z1 = box
    holes = {w: _holes(m, box, w, given=windows) for w in ('front', 'back', 'left', 'right', 'roof')}
    if windows is not None:
        # Painted-on windows outside: a pane of glass in each opening, just inside the wall.
        for w, hs in holes.items():
            for a0, a1, b0, b1 in hs:
                if w == 'front':
                    p.box((a0, b0, z1 - 0.02), (a1, b1, z1 - 0.01), 'glass', faces=('back',))
                elif w == 'back':
                    p.box((a0, b0, z0 + 0.01), (a1, b1, z0 + 0.02), 'glass', faces=('front',))
                elif w == 'left':
                    p.box((x0 + 0.01, b0, a0), (x0 + 0.02, b1, a1), 'glass', faces=('right',))
                elif w == 'right':
                    p.box((x1 - 0.02, b0, a0), (x1 - 0.01, b1, a1), 'glass', faces=('left',))
    for w in ('front', 'back', 'left', 'right', 'roof'):
        if w in open_walls:
            continue
        st = roof_style if w == 'roof' and roof_style else style
        if w == 'front':
            for ua, ub, va, vb in _cells(x0, x1, y0, y1, holes[w]):
                p.box((ua, va, z1 - T), (ub, vb, z1), st, faces=('back',))
        elif w == 'back':
            for ua, ub, va, vb in _cells(x0, x1, y0, y1, holes[w]):
                p.box((ua, va, z0), (ub, vb, z0 + T), st, faces=('front',))
        elif w == 'left':
            for ua, ub, va, vb in _cells(z0, z1, y0, y1, holes[w]):
                p.box((x0, va, ua), (x0 + T, vb, ub), st, faces=('right',))
        elif w == 'right':
            for ua, ub, va, vb in _cells(z0, z1, y0, y1, holes[w]):
                p.box((x1 - T, va, ua), (x1, vb, ub), st, faces=('left',))
        else:
            for ua, ub, va, vb in _cells(x0, x1, z0, z1, holes[w]):
                p.box((ua, y1 - T, va), (ub, y1, vb), st, faces=('bottom',))
    if floor_style:
        p.box((x0, y0, z0), (x1, y0 + T, z1), floor_style, faces=('top',))
    # Window frames: a slim dark rim round each window, standing proud of the lining.
    for w, hs in holes.items():
        if w in open_walls:
            continue
        for a0, a1, b0, b1 in hs:
            frame(p, w, box, max(a0, x0 if w in ('front', 'back', 'roof') else z0),
                  min(a1, x1 if w in ('front', 'back', 'roof') else z1), max(b0, y0 if w != 'roof' else z0), min(b1, y1 if w != 'roof' else z1),
                  tunnel=tunnel)
    return holes


def frame(p, wall, box, a0, a1, b0, b1, s=0.035, d=0.06, style='int_dark', tunnel=0.0):
    """A slim rim round a window; {tunnel}: the window sits that far out (a thick wall) and the rim lines the way to it."""
    x0, x1, y0, y1, z0, z1 = box
    if a1 - a0 < 0.05 or b1 - b0 < 0.05:
        return
    if tunnel > 0 and wall in ('left', 'right'):
        # The window's reveal: four plates from the lining out to the glass, showing their inner faces.
        x = x0 if wall == 'left' else x1
        xo = x - tunnel if wall == 'left' else x + tunnel
        xa, xb = sorted((x, xo))
        p.box((xa, b0 - 0.01, a0), (xb, b0, a1), style, faces=('top',))
        p.box((xa, b1, a0), (xb, b1 + 0.01, a1), style, faces=('bottom',))
        p.box((xa, b0, a0 - 0.01), (xb, b1, a0), style, faces=('front',))
        p.box((xa, b0, a1), (xb, b1, a1 + 0.01), style, faces=('back',))
    if wall == 'front':
        z = z1 - T
        for (ua, ub, va, vb) in ((a0, a1, b0, b0 + s), (a0, a1, b1 - s, b1), (a0, a0 + s, b0, b1), (a1 - s, a1, b0, b1)):
            p.box((ua, va, z - d), (ub, vb, z), style, faces=('back', 'top', 'bottom', 'left', 'right'))
    elif wall == 'back':
        z = z0 + T
        for (ua, ub, va, vb) in ((a0, a1, b0, b0 + s), (a0, a1, b1 - s, b1), (a0, a0 + s, b0, b1), (a1 - s, a1, b0, b1)):
            p.box((ua, va, z), (ub, vb, z + d), style, faces=('front', 'top', 'bottom', 'left', 'right'))
    elif wall == 'left':
        x = x0 + T
        for (ua, ub, va, vb) in ((a0, a1, b0, b0 + s), (a0, a1, b1 - s, b1), (a0, a0 + s, b0, b1), (a1 - s, a1, b0, b1)):
            p.box((x, va, ua), (x + d, vb, ub), style, faces=('right', 'top', 'bottom', 'front', 'back'))
    elif wall == 'right':
        x = x1 - T
        for (ua, ub, va, vb) in ((a0, a1, b0, b0 + s), (a0, a1, b1 - s, b1), (a0, a0 + s, b0, b1), (a1 - s, a1, b0, b1)):
            p.box((x - d, va, ua), (x, vb, ub), style, faces=('left', 'top', 'bottom', 'front', 'back'))


# ----------------------------------------------------------------------------------------------------------------
# Furniture

def seat(p, x, y, z, style='seat', w=0.5, back_h=0.68, armoured=False, facing=1):
    """A seat whose sitting point (hips) is (x, y, z); facing +1 = forward."""
    f = facing
    p.box((x - w / 2, y - 0.16, z - 0.28 * f), (x + w / 2, y - 0.04, z + 0.22 * f), style)
    p.box((x - w * 0.42, y - 0.4, z - 0.2 * f), (x + w * 0.42, y - 0.16, z + 0.12 * f), 'int_dark')
    zb = z - 0.3 * f
    p.box((x - w / 2, y - 0.08, zb - 0.12 * f), (x + w / 2, y + back_h, zb), style)
    p.box((x - w * 0.3, y + back_h, zb - 0.1 * f), (x + w * 0.3, y + back_h + 0.2, zb - 0.01 * f), 'seat_black' if style == 'seat' else style)
    if armoured:
        p.box((x - w / 2 - 0.05, y - 0.2, zb - 0.2 * f), (x + w / 2 + 0.05, y + back_h + 0.1, zb - 0.12 * f), 'int_dark')
        for s in (-1, 1):
            p.box((x + s * (w / 2 + 0.02) - 0.03, y - 0.1, zb - 0.12 * f), (x + s * (w / 2 + 0.02) + 0.03, y + 0.5, z + 0.1 * f), 'int_dark')


def steering_wheel(m, parent, x, y, z, r=0.2, tilt=-50, style='int_black', column_len=0.45):
    """A wheel at (x, y, z) facing the driver, its top leaning {tilt} degrees forward; spins with the steering."""
    col = parent.part('steering_column', (x, y, z), (tilt, 0, 0))
    col.box((x - 0.035, y - 0.035, z), (x + 0.035, y + 0.035, z + column_len), 'int_dark')
    w = col.part('steering_wheel', (x, y, z))
    t = 0.035
    a = r * 0.42
    w.box((x - a, y + r - t, z - t), (x + a, y + r, z + t), style)
    w.box((x - a, y - r, z - t), (x + a, y - r + t, z + t), style)
    w.box((x - r, y - a, z - t), (x - r + t, y + a, z + t), style)
    w.box((x + r - t, y - a, z - t), (x + r, y + a, z + t), style)
    d = w.part('steering_wheel_d', (x, y, z), (0, 0, 45))
    d.box((x - a, y + r - t, z - t), (x + a, y + r, z + t), style)
    d.box((x - a, y - r, z - t), (x + a, y - r + t, z + t), style)
    d.box((x - r, y - a, z - t), (x - r + t, y + a, z + t), style)
    d.box((x + r - t, y - a, z - t), (x + r, y + a, z + t), style)
    # Hub and three spokes.
    w.box((x - 0.06, y - 0.06, z - 0.02), (x + 0.06, y + 0.06, z + 0.05), 'int_dark')
    w.box((x - r + 0.03, y - 0.02, z - 0.015), (x + r - 0.03, y + 0.02, z + 0.015), style)
    w.box((x - 0.02, y - r + 0.03, z - 0.015), (x + 0.02, y, z + 0.015), style)


def gauges(p, x0, x1, y0, y1, z, style='gauge', facing='back'):
    """A row of dials on a panel face at depth z (facing the crew)."""
    if facing == 'back':
        p.box((x0, y0, z - 0.02), (x1, y1, z), 'int_black', faces=('back', 'top', 'bottom', 'left', 'right'),
              sides={'back': style})


def stick(p, x, y, z, h=0.55, style='int_black', grip='int_dark'):
    p.box((x - 0.025, y, z - 0.025), (x + 0.025, y + h, z + 0.025), style)
    p.box((x - 0.04, y + h, z - 0.05), (x + 0.04, y + h + 0.12, z + 0.04), grip)


# ----------------------------------------------------------------------------------------------------------------
# The kinds

def _fit_seats(m, box, holes, kind):
    """Moves a seat whose eye would sit under the roof or below the windscreen so it looks out of the middle."""
    x0, x1, y0, y1, z0, z1 = box
    front = holes.get('front') or []
    if not front:
        return
    lo = min(h[2] for h in front)
    hi = max(h[3] for h in front)
    for i, (role, sx, sy, sz) in enumerate(m.seats):
        if not (x0 - 0.05 <= sx <= x1 + 0.05 and z0 - 0.05 <= sz <= z1 + 0.05 and y0 - 0.3 <= sy <= y1):
            continue
        eye = sy + EYE
        want = lo + (hi - lo) * (0.62 if kind != 'jet' else 0.4)
        want = min(want, hi - 0.1, y1 - 0.14)
        if eye > min(hi - 0.05, y1 - 0.12) or eye < lo + 0.12:
            ny = max(y0 + 0.22, want - EYE)
            m.seats[i] = (role, sx, round(ny, 3), sz)


def _seats_in(m, box):
    x0, x1, y0, y1, z0, z1 = box
    return [(r, x, y, z) for r, x, y, z in m.seats if x0 - 0.05 <= x <= x1 + 0.05 and z0 - 0.05 <= z <= z1 + 0.05 and y0 - 0.3 <= y <= y1]


def truck_cab(m, p, cab, armoured=False, boat=False):
    box = cab['box']
    x0, x1, y0, y1, z0, z1 = box
    wall = cab.get('wall', 'int_light' if armoured else 'int_panel')
    holes = line_walls(m, p, box, wall, roof_style=cab.get('roof', 'int_light' if not armoured else 'int_grey'), windows=cab.get('windows'))
    _fit_seats(m, box, holes, 'truck')
    seats = _seats_in(m, box)
    front = holes['front']
    dash_top = (min(h[2] for h in front) - 0.02) if front else y0 + (y1 - y0) * 0.45
    dash_top = max(y0 + 0.35, min(dash_top, y1 - 0.5))
    depth = min(0.5, (z1 - z0) * 0.3)
    zd = z1 - T - depth
    p.box((x0 + T, y0 + 0.25, zd), (x1 - T, dash_top, z1 - T), 'int_dark', faces=('back', 'top', 'left', 'right'))
    # Dash top cover (a hood over the dials) and the lower panel down to the floor.
    p.box((x0 + T, dash_top, zd - 0.02), (x1 - T, dash_top + 0.03, z1 - T), 'int_black', faces=('top', 'back', 'bottom'))
    p.box((x0 + T, y0 + T, zd + 0.15), (x1 - T, y0 + 0.25, z1 - T), 'int_dark', faces=('back', 'top'))
    radio_done = False
    for role, sx, sy, sz in seats:
        seat(p, sx, sy, sz, 'seat_black' if armoured else 'seat', w=min(0.52, (x1 - x0) / 2 - 0.1), armoured=armoured)
        if role == 'driver':
            # Dials in front of the driver, the wheel, pedals, the gear lever.
            gauges(p, sx - 0.22, sx + 0.22, dash_top - 0.2, dash_top - 0.06, zd)
            if armoured:
                p.box((sx + 0.25, dash_top - 0.22, zd - 0.02), (sx + 0.45, dash_top - 0.06, zd), 'int_black', sides={'back': 'screen'},
                      faces=('back', 'top', 'bottom', 'left', 'right'))
            wz = min(sz + 0.52, zd - 0.12)
            wy = sy + 0.48
            if boat:
                wheel_r, tilt = 0.24, -18
            else:
                wheel_r, tilt = 0.2, -55 if not armoured else -40
            steering_wheel(m, p, sx, wy, wz, r=wheel_r, tilt=tilt, column_len=max(0.2, zd - wz + 0.1))
            for k, dx in enumerate((-0.12, 0.0, 0.14)):
                p.box((sx + dx - 0.04, y0 + 0.05, zd - 0.1), (sx + dx + 0.04, y0 + 0.2, zd - 0.06), 'int_black')
            gx = sx + (0.32 if sx < 0 else -0.32)
            p.box((gx - 0.02, y0 + T, sz + 0.05), (gx + 0.02, y0 + 0.5, sz + 0.09), 'int_black')
            p.box((gx - 0.04, y0 + 0.5, sz + 0.03), (gx + 0.04, y0 + 0.58, sz + 0.11), 'int_dark')
        else:
            # The other side: a glove box, a switch panel or a screen.
            if armoured:
                p.box((sx - 0.2, dash_top - 0.24, zd - 0.02), (sx + 0.2, dash_top - 0.06, zd), 'int_black', sides={'back': 'switches'},
                      faces=('back', 'top', 'bottom', 'left', 'right'))
            else:
                p.box((sx - 0.18, dash_top - 0.2, zd - 0.015), (sx + 0.18, dash_top - 0.08, zd), 'int_black', faces=('back',))
        # Sun visor folded up under the roof over the seat.
        if front:
            top = max(h[3] for h in front)
            p.box((sx - 0.2, y1 - T - 0.03, z1 - T - 0.2), (sx + 0.2, y1 - T, z1 - T - 0.02), 'int_grey', faces=('bottom', 'back', 'left', 'right'))
        # The door: an armrest with a pocket under the window, a handle.
        left = sx < (x0 + x1) / 2
        side_x = x0 + T if left else x1 - T
        sl = holes['left' if left else 'right']
        sill = min((h[2] for h in sl), default=sy + 0.45) - 0.03
        za, zb = max(z0 + 0.1, sz - 0.35), min(z1 - 0.15, sz + 0.55)
        if left:
            p.box((side_x, sill - 0.09, za), (side_x + 0.09, sill, zb), 'int_dark')
            p.box((side_x, y0 + 0.2, za), (side_x + 0.04, sill - 0.18, zb), 'int_grey', faces=('right', 'top'))
            p.box((side_x, sy + 0.3, sz + 0.12), (side_x + 0.05, sy + 0.34, sz + 0.28), 'chrome')
        else:
            p.box((side_x - 0.09, sill - 0.09, za), (side_x, sill, zb), 'int_dark')
            p.box((side_x - 0.04, y0 + 0.2, za), (side_x, sill - 0.18, zb), 'int_grey', faces=('left', 'top'))
            p.box((side_x - 0.05, sy + 0.3, sz + 0.12), (side_x, sy + 0.34, sz + 0.28), 'chrome')
    # The middle of the dashboard: switches and the heater's vents; an engine hump between the seats in a cab-over.
    if len(seats) >= 2 or (x1 - x0) > 1.6:
        p.box((-0.2, dash_top - 0.3, zd - 0.03), (0.2, dash_top - 0.05, zd), 'int_black', sides={'back': 'switches'},
              faces=('back', 'top', 'bottom', 'left', 'right'))
        for vx in (-0.14, 0.14):
            p.box((vx - 0.06, dash_top - 0.42, zd - 0.025), (vx + 0.06, dash_top - 0.35, zd), 'int_black', faces=('back', 'top', 'bottom', 'left', 'right'))
    if cab.get('hump'):
        p.box((-0.3, y0 + T, z0 + 0.2), (0.3, y0 + 0.42, zd), 'int_dark', faces=('top', 'left', 'right', 'back'))
    # A roof hatch.
    if cab.get('hatch', True) and (z1 - z0) > 1.0:
        hx, hz = 0.3, (z0 + z1) / 2 - 0.1
        for a, b in (((-0.32, hz - 0.32), (0.32, hz - 0.27)), ((-0.32, hz + 0.27), (0.32, hz + 0.32))):
            p.box((a[0], y1 - T - 0.04, a[1]), (b[0], y1 - T, b[1]), 'int_dark', faces=('bottom', 'front', 'back', 'left', 'right'))
        for xx in (-0.32, 0.27):
            p.box((xx, y1 - T - 0.04, hz - 0.27), (xx + 0.05, y1 - T, hz + 0.27), 'int_dark', faces=('bottom', 'front', 'back', 'left', 'right'))
        p.box((-0.27, y1 - T - 0.01, hz - 0.27), (0.27, y1 - T, hz + 0.27), 'int_grey', faces=('bottom',))
    # Mirror in the middle of the windscreen, a dome light, a radio set and a fire extinguisher at the back.
    if front:
        top = max(h[3] for h in front)
        p.box((-0.12, top - 0.12, z1 - T - 0.08), (0.12, top - 0.04, z1 - T - 0.05), 'int_black')
    p.box((-0.1, y1 - T - 0.03, (z0 + z1) / 2 - 0.08), (0.1, y1 - T, (z0 + z1) / 2 + 0.08), 'light', faces=('bottom', 'left', 'right', 'front', 'back'))
    if (x1 - x0) > 1.2 and not radio_done:
        p.box((-0.25, y0 + 0.9, z0 + T), (0.25, y0 + 1.2, z0 + T + 0.28), 'radio')
        p.box((-0.08, y0 + 1.2, z0 + T + 0.05), (-0.05, y0 + 1.75, z0 + T + 0.08), 'int_black')
    p.box((x1 - T - 0.14, y0 + T, z0 + T), (x1 - T - 0.04, y0 + 0.45, z0 + T + 0.1), 'red')
    p.box((x1 - T - 0.12, y0 + 0.45, z0 + T + 0.02), (x1 - T - 0.06, y0 + 0.52, z0 + T + 0.08), 'int_black')
    if armoured:
        # Rifles in a rack on the back wall and a box of rounds.
        for k in (-0.45, -0.3):
            p.box((k, y0 + 0.4, z0 + T), (k + 0.06, y0 + 1.3, z0 + T + 0.08), 'int_black')
        p.box((x0 + T + 0.05, y0 + T, z0 + T + 0.05), (x0 + T + 0.4, y0 + 0.25, z0 + T + 0.3), 'olive')


def _tub(p, box, wall, floor='rubber'):
    """The cockpit under a glass canopy: walls up to the sill (above it is glass), a floor."""
    x0, x1, y0, y1, z0, z1 = box
    p.box((x0, y0, z1 - T), (x1, y1, z1), wall, faces=('back',))
    p.box((x0, y0, z0), (x1, y1, z0 + T), wall, faces=('front',))
    p.box((x0, y0, z0), (x0 + T, y1, z1), wall, faces=('right',))
    p.box((x1 - T, y0, z0), (x1, y1, z1), wall, faces=('left',))
    p.box((x0, y0, z0), (x1, y0 + T, z1), floor, faces=('top',))
    # The sill: a padded rim along the top of the walls.
    p.box((x0, y1 - 0.06, z0), (x0 + 0.08, y1, z1), 'int_dark')
    p.box((x1 - 0.08, y1 - 0.06, z0), (x1, y1, z1), 'int_dark')


def heli_cockpit(m, p, cab):
    box = cab['box']
    x0, x1, y0, y1, z0, z1 = box
    modern = cab.get('modern', False)
    wall = cab.get('wall', 'int_navy' if modern else 'int_turq')
    if cab.get('glazing') == 'bubble':
        _tub(p, box, wall)
        holes = {}
    else:
        holes = line_walls(m, p, box, wall, roof_style=cab.get('roof', 'int_light' if not modern else wall), open_walls=cab.get('open', ()),
                           tunnel=cab.get('tunnel', 0.3))
        _fit_seats(m, box, holes, 'heli')
    seats = _seats_in(m, box)
    panel_top = cab.get('panel_top', y0 + (y1 - y0) * 0.5)
    if seats and cab.get('glazing') == 'bubble':
        # Up to just under the eye line, as in the jets.
        panel_top = max(panel_top, max(s[2] for s in seats) + EYE - 0.33)
    pz = cab.get('panel_z', z1 - 0.35)
    # The instrument panel across the front under its hood, the lower panel down to the floor.
    p.box((x0 + 0.05, y0 + 0.35, pz), (x1 - 0.05, panel_top, min(z1 - T, pz + 0.14)), 'int_dark' if modern else 'int_turq_dark',
          faces=('back', 'top', 'left', 'right'))
    p.box((x0 + 0.05, panel_top, pz - 0.08), (x1 - 0.05, panel_top + 0.05, pz + 0.12), 'int_black', faces=('top', 'back', 'bottom', 'left', 'right'))
    for role, sx, sy, sz in seats:
        seat(p, sx, sy, sz, 'seat_black' if modern else 'seat', w=0.5, armoured=True)
        # Dials (or screens) in front of each pilot.
        if modern:
            for dx in (-0.15, 0.15):
                p.box((sx + dx - 0.12, panel_top - 0.32, pz - 0.02), (sx + dx + 0.12, panel_top - 0.07, pz), 'int_black', sides={'back': 'screen'},
                      faces=('back', 'top', 'bottom', 'left', 'right'))
        else:
            gauges(p, sx - 0.26, sx + 0.26, panel_top - 0.17, panel_top - 0.04, pz)
            gauges(p, sx - 0.2, sx + 0.2, panel_top - 0.32, panel_top - 0.2, pz)
        # Cyclic between the knees, the collective on the left, pedals at the front.
        stick(p, sx, sy - 0.32, sz + 0.4, h=0.6)
        p.box((sx - 0.37, sy - 0.18, sz - 0.12), (sx - 0.32, sy + 0.1, sz + 0.36), 'int_black')
        p.box((sx - 0.39, sy + 0.08, sz + 0.28), (sx - 0.3, sy + 0.17, sz + 0.42), 'int_dark')
        for dx in (-0.14, 0.14):
            p.box((sx + dx - 0.05, y0 + 0.05, pz - 0.16), (sx + dx + 0.05, y0 + 0.22, pz - 0.1), 'int_black')
    if len(seats) >= 2:
        cx = sum(s[1] for s in seats) / len(seats)
        zs = min(s[3] for s in seats)
        p.box((cx - 0.14, y0 + T, zs - 0.15), (cx + 0.14, y0 + 0.58, pz), 'int_dark', sides={'top': 'switches'})
    if cab.get('glazing') != 'bubble':
        # Overhead switch panel.
        p.box((-0.3, y1 - 0.12, z0 + (z1 - z0) * 0.3), (0.3, y1 - T, z0 + (z1 - z0) * 0.62), 'int_dark', sides={'bottom': 'switches'},
              faces=('bottom', 'front', 'back', 'left', 'right'))
        # The bulkhead door to the cabin at the back.
        p.box((-0.35, y0 + T, z0 + T), (0.35, y1 - 0.25, z0 + T + 0.02), 'int_dark', faces=('front',))


def jet_cockpit(m, p, cab):
    box = cab['box']
    x0, x1, y0, y1, z0, z1 = box
    western = cab.get('western', False)
    wall = 'int_navy' if western else 'int_turq'
    _tub(p, box, wall)
    seats = _seats_in(m, box)
    eye = (seats[0][2] + EYE) if seats else y1 + 0.7
    # The panel rises above the canopy rail to just under the eye line: the glare shield on top, the head-up display.
    sill = max(y1, eye - 0.3)
    pz = z1 - 0.62
    # Instrument panel under the glare shield, the shield, the head-up display glass on top of it.
    p.box((x0 + T, y0 + 0.3, pz), (x1 - T, sill - 0.02, z1 - T), 'int_navy' if western else 'int_turq_dark', faces=('back', 'top', 'left', 'right'))
    p.box((x0 + 0.06, sill - 0.02, pz - 0.07), (x1 - 0.06, sill + 0.07, pz + 0.32), 'int_black', faces=('top', 'back', 'left', 'right'))
    p.box((-0.11, sill + 0.07, pz + 0.12), (0.11, sill + 0.1, pz + 0.2), 'int_black')
    p.box((-0.1, sill + 0.1, pz + 0.15), (0.1, sill + 0.28, pz + 0.17), 'hud', faces=('back', 'front'))
    if western:
        for dx in (-0.2, 0.2):
            p.box((dx - 0.11, sill - 0.32, pz - 0.02), (dx + 0.11, sill - 0.1, pz), 'int_black', sides={'back': 'screen'},
                  faces=('back', 'top', 'bottom', 'left', 'right'))
        gauges(p, -0.07, 0.07, sill - 0.25, sill - 0.12, pz - 0.005)
        p.box((-0.09, sill - 0.08, pz - 0.03), (0.09, sill - 0.02, pz), 'int_black', sides={'back': 'screen_amber'},
              faces=('back', 'top', 'bottom', 'left', 'right'))
    else:
        gauges(p, -0.3, 0.3, sill - 0.17, sill - 0.05, pz)
        gauges(p, -0.24, 0.24, sill - 0.33, sill - 0.21, pz)
        p.box((-0.08, sill - 0.49, pz - 0.02), (0.08, sill - 0.37, pz), 'int_black', sides={'back': 'screen_amber'},
              faces=('back', 'top', 'bottom', 'left', 'right'))
    for role, sx, sy, sz in seats:
        # The ejection seat: tall back, head box, the yellow-and-black pull handle between the knees.
        p.box((sx - 0.24, sy - 0.18, sz - 0.25), (sx + 0.24, sy - 0.05, sz + 0.25), 'seat_black')
        p.box((sx - 0.26, sy - 0.12, sz - 0.42), (sx + 0.26, sy + 0.82, sz - 0.28), 'seat_black')
        p.box((sx - 0.2, sy + 0.82, sz - 0.45), (sx + 0.2, sy + 1.12, sz - 0.25), 'int_dark')
        p.box((sx - 0.06, sy - 0.14, sz + 0.25), (sx + 0.06, sy - 0.09, sz + 0.31), 'yellow')
        p.box((sx - 0.3, y0 + T, sz - 0.45), (sx + 0.3, sy - 0.18, sz + 0.2), 'int_dark')
        # Side consoles; the throttle at the left hand, the stick (on the F-16 at the right hand).
        for s_ in (-1, 1):
            cx = sx + s_ * 0.35
            p.box((cx - 0.08, y0 + 0.2, sz - 0.4), (cx + 0.08, sy + 0.1, min(z1 - T, sz + 0.55)), 'int_dark', sides={'top': 'switches'})
        p.box((sx - 0.4, sy + 0.1, sz + 0.02), (sx - 0.31, sy + 0.27, sz + 0.14), 'int_black')
        if western:
            p.box((sx + 0.33, sy + 0.1, sz + 0.26), (sx + 0.38, sy + 0.3, sz + 0.31), 'int_black')
        else:
            stick(p, sx, sy - 0.12, sz + 0.36, h=0.42)
        for dx in (-0.12, 0.12):
            p.box((sx + dx - 0.05, y0 + 0.08, pz - 0.18), (sx + dx + 0.05, y0 + 0.24, pz - 0.12), 'int_black')
        # Mirrors on the canopy bow are the outer model's; here the leg-room under the panel is dark.
        p.box((x0 + T, y0 + T, pz - 0.02), (x1 - T, y0 + 0.3, pz), 'int_black', faces=('back',))


def troop_cabin(m, p, cab):
    """A transport cabin: benches along both walls under the windows, a floor with tie-down rails, handrails under
    the roof, the bulkhead to the cockpit with its door, the lights."""
    box = cab['box']
    x0, x1, y0, y1, z0, z1 = box
    holes = line_walls(m, p, box, cab.get('wall', 'int_turq'), roof_style=cab.get('roof', 'int_light'), tunnel=cab.get('tunnel', 0.3))
    for s_ in (-1, 1):
        xw = x0 + T if s_ < 0 else x1 - T
        xa, xb = sorted((xw, xw - s_ * 0.45))
        # Bench: seat and back cushion, the frame under it.
        p.box((xa, y0 + 0.42, z0 + 0.3), (xb, y0 + 0.5, z1 - 0.35), 'seat')
        xa2, xb2 = sorted((xw, xw - s_ * 0.08))
        p.box((xa2, y0 + 0.55, z0 + 0.3), (xb2, y0 + 1.05, z1 - 0.35), 'seat')
        xa3, xb3 = sorted((xw - s_ * 0.36, xw - s_ * 0.4))
        p.box((xa3, y0 + T, z0 + 0.3), (xb3, y0 + 0.42, z1 - 0.35), 'int_dark')
        # Handrail under the roof.
        xh = xw - s_ * 0.55
        p.box((xh - 0.02, y1 - 0.2, z0 + 0.3), (xh + 0.02, y1 - 0.16, z1 - 0.3), 'chrome')
    for xr in (-0.35, 0.35):
        p.box((xr - 0.03, y0 + T, z0 + 0.1), (xr + 0.03, y0 + T + 0.02, z1 - 0.1), 'steel')
    # The bulkhead door to the cockpit (in the front lining) and lamps along the roof.
    p.box((-0.32, y0 + T, z1 - T - 0.02), (0.32, y1 - 0.3, z1 - T), 'int_dark', faces=('back',))
    p.box((0.2, y0 + 1.0, z1 - T - 0.05), (0.26, y0 + 1.06, z1 - T - 0.02), 'chrome')
    z = z0 + 0.8
    while z < z1 - 0.5:
        p.box((-0.12, y1 - T - 0.03, z - 0.08), (0.12, y1 - T, z + 0.08), 'light', faces=('bottom', 'left', 'right', 'front', 'back'))
        z += 1.6


def build(m):
    """The inside model of an outer one (its cabs)."""
    im = Model(m.id + '_int', paint='int_panel', tex_width=512, seed=m.seed + 777)
    im.interior_of = m.id
    made = {}

    def part_for(name):
        if name in made:
            return made[name]
        src = next((p for p in m.parts if p.name == name), None)
        if src is None and m.stub:
            made[name] = im.part(name)
            return made[name]
        if src is None:
            raise ValueError('%s: no part %s for a cab' % (m.id, name))
        if src.parent is None:
            q = im.part(src.name, src.pivot, src.rot)
        else:
            q = part_for(src.parent.name).part(src.name, src.pivot, src.rot)
        made[name] = q
        return q

    for cab in m.cabs:
        p = part_for(cab['part'])
        kind = cab['kind']
        if kind == 'truck':
            truck_cab(m, p, cab)
        elif kind == 'armour':
            truck_cab(m, p, cab, armoured=True)
        elif kind == 'boat':
            truck_cab(m, p, cab, boat=True)
        elif kind == 'heli':
            heli_cockpit(m, p, cab)
        elif kind == 'jet':
            jet_cockpit(m, p, cab)
        elif kind == 'troops':
            troop_cabin(m, p, cab)
        else:
            raise ValueError(kind)
    return im
