"""1.41: the working country - lorries on the highways (a box lorry, a tanker, a tipper), a tractor with its seed drill
in the fields, a tower crane over the building sites. Metres, the front at +z, wheels on the ground at y = 0. Drawn by
the client only (client.nation.PropRenderer): no items."""
from boxgen import Model
from kit import lights


def long_box(part, a, b, style, axis, seg=10.0):
    """A box split into pieces of at most seg metres along an axis (a long one does not fit the texture atlas)."""
    lo, hi = a[axis], b[axis]
    n = max(1, int((hi - lo) / seg + 0.999))
    for i in range(n):
        p = list(a)
        q = list(b)
        p[axis] = lo + (hi - lo) * i / n
        q[axis] = lo + (hi - lo) * (i + 1) / n
        part.box(tuple(p), tuple(q), style)


def wheel_pair(body, z, r, half, w=0.38, twin=False):
    for s in (-1, 1):
        x = s * half
        body.box((x - w / 2, 0.0, z - r), (x + w / 2, 2 * r, z + r), 'wheel_grey')
        if twin:
            body.box((x - s * w - w / 2, 0.0, z - r), (x - s * w + w / 2, 2 * r, z + r), 'wheel_grey')


def cab(body, zf, width, paint, length=2.2, top=3.4, y0=1.1):
    """A flat-fronted lorry cab: the windscreen, the side windows, the grille, the lights, the mirrors."""
    hw = width / 2
    body.box((-hw, y0, zf - length), (hw, top, zf), paint)
    body.box((-hw + 0.1, top - 1.2, zf), (hw - 0.1, top - 0.35, zf + 0.03), 'window')
    for s in (-1, 1):
        body.box((s * hw - (0.0 if s > 0 else 0.03), top - 1.15, zf - 1.0), (s * hw + (0.03 if s > 0 else 0.0), top - 0.4, zf - 0.2), 'window')
        body.box((s * (hw + 0.25) - 0.05, top - 1.2, zf - 0.3), (s * (hw + 0.25) + 0.05, top - 0.6, zf - 0.2), 'dark')
    body.box((-hw + 0.3, y0 + 0.2, zf), (hw - 0.3, y0 + 0.9, zf + 0.04), 'dark')
    body.box((-hw, y0 - 0.25, zf - 0.2), (hw, y0 + 0.1, zf + 0.1), 'dgrey')
    lights(body, zf + 0.04, y0 + 0.15, hw - 0.3)
    body.box((-hw + 0.2, top, zf - length + 0.3), (hw - 0.2, top + 0.45, zf - 0.6), paint)


def chassis(body, z0, z1):
    body.box((-0.5, 0.75, z0), (0.5, 1.1, z1), 'dark')


def lorry_box():
    m = Model('lorry_box', paint='white', seed=4101)
    m.no_item = True
    body = m.part('body')
    chassis(body, -5.0, 4.6)
    cab(body, 5.0, 2.5, 'liner_blue')
    body.box((-1.27, 1.15, -5.2), (1.27, 3.9, 2.6), 'white')
    for s in (-1, 1):
        body.box((s * 1.27 - (0.0 if s > 0 else 0.02), 1.6, -5.0), (s * 1.27 + (0.02 if s > 0 else 0.0), 2.0, 2.4), 'liner_blue')
    body.box((-1.2, 1.3, -5.23), (1.2, 3.8, -5.2), 'lgrey')
    lights(body, -5.23, 1.0, 1.0, front=False)
    wheel_pair(body, 3.6, 0.52, 1.0)
    wheel_pair(body, -2.6, 0.52, 1.0, twin=True)
    wheel_pair(body, -3.9, 0.52, 1.0, twin=True)
    return m


def lorry_tank():
    m = Model('lorry_tank', paint='lgrey', seed=4102)
    m.no_item = True
    body = m.part('body')
    chassis(body, -5.2, 4.6)
    cab(body, 5.0, 2.5, 'red')
    # The tank: a long rounded drum on the frame (three stacked boxes), the walkway on top.
    body.box((-1.2, 1.5, -5.4), (1.2, 2.9, 2.5), 'chrome')
    body.box((-0.95, 1.25, -5.4), (0.95, 3.15, 2.5), 'chrome')
    body.box((-0.6, 1.15, -5.3), (0.6, 3.25, 2.4), 'chrome')
    body.box((-0.3, 3.25, -5.0), (0.3, 3.32, 2.2), 'dark')
    body.box((-1.21, 2.0, -3.0), (1.21, 2.3, 1.0), 'orange')
    lights(body, -5.42, 1.0, 1.0, front=False)
    wheel_pair(body, 3.6, 0.52, 1.0)
    wheel_pair(body, -2.8, 0.52, 1.0, twin=True)
    wheel_pair(body, -4.1, 0.52, 1.0, twin=True)
    return m


def tipper():
    m = Model('tipper', paint='orange', seed=4103)
    m.no_item = True
    body = m.part('body')
    chassis(body, -4.0, 4.0)
    cab(body, 4.4, 2.5, 'orange')
    # The tipping body, its load of gravel heaped up.
    body.box((-1.25, 1.2, -4.1), (1.25, 1.35, 1.9), 'dgrey')
    for s in (-1, 1):
        body.box((s * 1.25 - (0.1 if s > 0 else 0.0), 1.35, -4.1), (s * 1.25 + (0.0 if s > 0 else 0.1), 2.6, 1.9), 'dgrey')
    body.box((-1.25, 1.35, 1.8), (1.25, 3.0, 1.9), 'dgrey')
    body.box((-1.25, 1.35, -4.1), (1.25, 2.6, -4.0), 'dgrey')
    body.box((-1.15, 1.35, -4.0), (1.15, 2.5, 1.8), 'gravel')
    body.box((-0.8, 2.5, -3.2), (0.8, 2.9, 1.0), 'gravel')
    wheel_pair(body, 3.0, 0.55, 1.0)
    wheel_pair(body, -2.2, 0.55, 1.0, twin=True)
    wheel_pair(body, -3.5, 0.55, 1.0, twin=True)
    return m


def tractor():
    """A farm tractor (big wheels at the back, small in front, the cab over the back axle) with a seed drill behind."""
    m = Model('tractor', paint='tractor_red', seed=4104)
    m.no_item = True
    body = m.part('body')
    # The bonnet, the engine, the grille, the exhaust.
    body.box((-0.55, 0.75, 0.4), (0.55, 1.75, 2.4), 'tractor_red')
    body.box((-0.45, 0.8, 2.4), (0.45, 1.65, 2.48), 'dark')
    body.box((0.35, 1.75, 1.6), (0.47, 2.9, 1.72), 'dark')
    lights(body, 2.48, 1.3, 0.3)
    # The cab: the frame, glass all round, the roof.
    body.box((-0.75, 1.1, -1.3), (0.75, 1.3, 0.4), 'tractor_red')
    for x in (-0.75, 0.7):
        for z in (-1.3, 0.35):
            body.box((x, 1.3, z), (x + 0.05, 2.8, z + 0.05), 'dark')
    body.box((-0.72, 1.4, 0.36), (0.72, 2.7, 0.39), 'window')
    body.box((-0.72, 1.4, -1.28), (0.72, 2.7, -1.25), 'window')
    for s in (-1, 1):
        body.box((s * 0.73 - 0.015, 1.4, -1.25), (s * 0.73 + 0.015, 2.7, 0.35), 'window')
    body.box((-0.85, 2.8, -1.4), (0.85, 2.92, 0.5), 'white')
    # The wheels: the big back ones, the small front ones, the mudguards.
    for s in (-1, 1):
        body.box((s * 0.98 - 0.25, 0.0, -1.35), (s * 0.98 + 0.25, 1.5, 0.15), 'wheel_grey')
        body.box((s * 0.98 - 0.3, 1.5, -1.45), (s * 0.98 + 0.3, 1.6, 0.25), 'tractor_red')
        body.box((s * 0.78 - 0.16, 0.0, 1.45), (s * 0.78 + 0.16, 0.9, 2.35), 'wheel_grey')
    # The seed drill on its hitch behind: a long box across, its wheels, the hoppers.
    body.box((-0.1, 0.6, -2.4), (0.1, 0.8, -1.35), 'dark')
    body.box((-1.8, 0.55, -3.2), (1.8, 1.25, -2.4), 'grey')
    body.box((-1.7, 1.25, -3.1), (1.7, 1.6, -2.5), 'liner_blue')
    for s in (-1, 1):
        body.box((s * 1.6 - 0.1, 0.0, -3.05), (s * 1.6 + 0.1, 0.55, -2.55), 'wheel_grey')
    for x in (-1.5, -0.9, -0.3, 0.3, 0.9, 1.5):
        body.box((x - 0.04, 0.05, -3.1), (x + 0.04, 0.55, -3.02), 'steel')
    return m


def crane():
    """A tower crane: the lattice mast on its base, and on top the part that turns ('spin'): the jib with its trolley
    and hook, the counter-jib with the weights, the cab, the peak with its ties."""
    m = Model('tower_crane', paint='crane_yellow', seed=4105)
    m.no_item = True
    body = m.part('body')
    h = 40.0
    # The base: concrete blocks; the mast: four corner legs and a lattice face on each side.
    body.box((-2.2, 0.0, -2.2), (2.2, 1.0, 2.2), 'concrete')
    for sx in (-1, 1):
        for sz in (-1, 1):
            long_box(body, (sx * 0.85 - 0.12, 1.0, sz * 0.85 - 0.12), (sx * 0.85 + 0.12, h, sz * 0.85 + 0.12), 'crane_yellow', 1)
    for s in (-1, 1):
        long_box(body, (s * 0.85 - 0.02, 1.0, -0.85), (s * 0.85 + 0.02, h, 0.85), 'mesh_yellow', 1)
        long_box(body, (-0.85, 1.0, s * 0.85 - 0.02), (0.85, h, s * 0.85 + 0.02), 'mesh_yellow', 1)
    top = m.part('spin', pivot=(0, h, 0))
    # The slewing ring and the cab.
    top.box((-1.3, h, -1.3), (1.3, h + 1.0, 1.3), 'crane_yellow')
    top.box((0.95, h + 1.0, 0.0), (2.2, h + 3.0, 1.6), 'white')
    top.box((2.2, h + 1.6, 0.05), (2.23, h + 2.8, 1.55), 'window')
    top.box((1.0, h + 1.6, 1.6), (2.15, h + 2.8, 1.63), 'window')
    # The jib forward (+z): a triangular lattice beam, the trolley and the hook on its cable.
    L = 45.0
    long_box(top, (-0.8, h + 1.0, 1.3), (0.8, h + 1.2, L), 'crane_yellow', 2)
    long_box(top, (-0.08, h + 2.6, 1.3), (0.08, h + 2.75, L - 1.0), 'crane_yellow', 2)
    for s in (-1, 1):
        long_box(top, (s * 0.4 - 0.02, h + 1.2, 1.3), (s * 0.4 + 0.02, h + 2.6, L - 1.0), 'mesh_yellow', 2)
    top.box((-0.9, h + 0.4, 24.0), (0.9, h + 1.0, 26.0), 'dgrey')
    long_box(top, (-0.03, h - 17.0, 24.95), (0.03, h + 0.4, 25.05), 'dark', 1)
    top.box((-0.35, h - 18.0, 24.65), (0.35, h - 17.0, 25.35), 'red')
    # The counter-jib behind (-z) with its concrete weights; the peak and the ties to both ends.
    long_box(top, (-1.0, h + 1.0, -14.0), (1.0, h + 1.3, -1.3), 'crane_yellow', 2)
    top.box((-1.1, h - 1.2, -13.5), (1.1, h + 1.0, -10.5), 'concrete')
    top.box((-0.6, h + 1.0, -0.6), (0.6, h + 7.5, 0.6), 'crane_yellow')
    for z0, z1, y0, y1 in ((0.6, 30.0, h + 7.3, h + 2.7), (-12.5, -0.6, h + 1.3, h + 7.3)):
        n = 8
        for i in range(n):
            za = z0 + (z1 - z0) * i / n
            zb = z0 + (z1 - z0) * (i + 1) / n
            ya = y0 + (y1 - y0) * (i + 0.5) / n
            top.box((-0.05, ya - 0.05, min(za, zb)), (0.05, ya + 0.05, max(za, zb)), 'dark')
    top.box((-0.2, h + 7.5, -0.2), (0.2, h + 7.8, 0.2), 'redlight')
    top.box((-0.2, h + 1.3, L - 0.6), (0.2, h + 1.6, L - 0.2), 'redlight')
    return m


def all_models():
    return [lorry_box(), lorry_tank(), tipper(), tractor(), crane()]
