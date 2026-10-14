"""
2.0: the armoured vehicles' kit - paint schemes matching the box models' styles, the materials of armour (worn paint,
rubber with mud, the track links' tile), and the shapes every tank shares: hulls lofted through cross-sections, cast or
welded turrets lofted through plan outlines at their heights, running gear (road wheels with rubber tyres, toothed
sprockets, idlers, return rollers), track belts the client scrolls, gun barrels turned on a lathe (thermal sleeves, fume
extractors, muzzle brakes), ERA bricks, hatches, periscopes, smoke grenade launchers, lights with guards, tow cables.

Coordinates as everywhere: metres, x right, y up, z forward, ground y = 0.
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import bake  # noqa: E402
import geom  # noqa: E402
import materials  # noqa: E402
from core import Geo, normalize, rot_x, rot_y, rot_z  # noqa: E402
from geom import cyl, lathe, loft, polygon_cap, rbox, tube, fix_winding  # noqa: E402
from materials import fbm, hexc, occlusion, plain, vnoise, weather  # noqa: E402

# ---------------------------------------------------------------------------------------------------------------
# Paint schemes (the box models' style names)


def scheme_for(paint, seed=1, dirt=0.75):
    """A materials.Scheme for a boxgen paint style: Russian protective green, the Ukrainian green with black and brown
    blotches, NATO three-colour, plain NATO green, olive, desert tan (with lighter/darker patches) and plain sand."""
    S = materials.Scheme
    if paint == 'rgreen':
        return S(0x515C37, dirt=dirt, wear=0.35, seed=seed, gloss=0.12)
    if paint == 'ugreen':
        return S(0x55613A, camo=[0x2D3122, 0x6A5636], blob=1.15, stretch=1.5, slant=0.7, dirt=dirt, wear=0.35, seed=seed, gloss=0.12)
    if paint == 'camo':
        return S(0x4A5A32, camo=[0x5E4A30, 0x23281D], blob=1.05, stretch=1.4, slant=0.6, dirt=dirt, wear=0.3, seed=seed, gloss=0.1)
    if paint == 'nato':
        return S(0x4A5238, dirt=dirt, wear=0.3, seed=seed, gloss=0.1)
    if paint == 'olive':
        return S(0x5B6236, dirt=dirt, wear=0.3, seed=seed, gloss=0.1)
    if paint == 'tan':
        return S(0xB59A6A, camo=[0x8C7650, 0xC9B486], blob=1.3, stretch=1.5, slant=0.5, dirt=dirt * 0.8, wear=0.3, seed=seed, gloss=0.1)
    if paint == 'sand':
        return S(0xB8A27A, dirt=dirt * 0.8, wear=0.3, seed=seed, gloss=0.1)
    if paint == 'grey':
        return S(0x7C8186, dirt=dirt * 0.7, wear=0.3, seed=seed, gloss=0.2)
    return S(0x515C37, dirt=dirt, wear=0.3, seed=seed, gloss=0.12)


# ---------------------------------------------------------------------------------------------------------------
# Materials


def _armour_paint(T, scheme, model, k=1.0, mud=True):
    pos, nrm = T['pos'], T['nrm']
    rgb = materials.camo_rgb(scheme, pos) * k
    for dcl in model.decals:
        rgb = dcl.apply(rgb, pos, nrm)
    rgb = materials.sheen(rgb, nrm, scheme.gloss)
    # Chipped and faded paint: lighter worn patches, a few dark chips.
    wear = fbm(pos, 0.25, scheme.seed + 41, 3)
    fade = np.clip((wear - 0.6) / 0.2, 0, 1) * scheme.wear * 0.25
    rgb = rgb * (1 + fade[:, None] * 0.5)
    chips = vnoise(pos, 0.045, scheme.seed + 42) > 0.95 - 0.03 * scheme.wear
    rgb[chips] = rgb[chips] * 0.78
    rgb = weather(rgb, T, scheme, 1.0, mud=mud)
    return occlusion(rgb, T['ao'])


def _mat_rw_rubber(T, scheme, model):
    """Road wheel tyres: black rubber, worn grey at the edge, caked with mud."""
    rgb = np.tile(hexc(0x202122), (len(T['pos']), 1))
    rgb *= (0.9 + 0.2 * vnoise(T['pos'], 0.05, 3))[:, None]
    rgb = weather(rgb, T, scheme, 1.3, mud=True)
    return occlusion(rgb, T['ao'], 0.9), 255


def _mat_wood(T, scheme, model):
    pos = T['pos']
    grain = np.sin(pos[:, 0] * 40 + fbm(pos, 0.2, 5, 2) * 8)
    rgb = np.tile(hexc(0x6B4E32), (len(pos), 1)) * (0.85 + 0.1 * grain)[:, None]
    rgb = weather(rgb, T, scheme, 0.8, mud=False)
    return occlusion(rgb, T['ao']), 255


def _mat_optic(T, scheme, model):
    """Sight and periscope glass: dark blue-green with a sky glint (solid, it is small)."""
    ny = T['nrm'][:, 1]
    rgb = np.tile(np.array((24, 38, 44.0)), (len(ny), 1))
    rgb += (np.clip(ny, 0, 1) * 70)[:, None] * np.array([0.7, 0.9, 1.0])
    return rgb, 255


def _mat_steel_worn(T, scheme, model):
    """Bare worn steel (barrel muzzles, tow hooks, polished wheel paths)."""
    rgb = materials.env_metal(T['nrm'], np.array((140, 140, 136.0)), np.array((60, 58, 54.0)))
    rgb = weather(rgb, T, scheme, 0.7, mud=False)
    return occlusion(rgb, T['ao'], 0.9), 255


def _mat_era(T, scheme, model):
    """Explosive reactive armour bricks: the paint, slightly darker, with bolt heads at the corners of each brick (the
    brick's own uv)."""
    rgb = _armour_paint(T, scheme, model, 0.93)
    return rgb, 255


def _mat_rubber_skirt(T, scheme, model):
    rgb = np.tile(hexc(0x26272A), (len(T['pos']), 1))
    rgb = weather(rgb, T, scheme, 1.0, mud=True)
    return occlusion(rgb, T['ao']), 255


def _mat_grille_deck(T, scheme, model):
    """Engine deck louvres: painted slats over darkness (along the primitive uv's v)."""
    u, v = T['puv'][:, 0], T['puv'][:, 1]
    rgb = _armour_paint(T, scheme, model, 0.9)
    gap = ((v * 12) % 1.0) < 0.35
    rgb[gap] = rgb[gap] * 0.3
    return rgb, 255


def _mat_paint_dark(T, scheme, model):
    return _armour_paint(T, scheme, model, 0.72), 255


def _mat_armour(T, scheme, model):
    return _armour_paint(T, scheme, model), 255


materials.register('armour', _mat_armour)
materials.register('armour_dark', _mat_paint_dark)
materials.register('rw_rubber', _mat_rw_rubber)
materials.register('wood', _mat_wood)
materials.register('optic', _mat_optic, density=2.0)
materials.register('steel_worn', _mat_steel_worn)
materials.register('era', _mat_era)
materials.register('skirt_rubber', _mat_rubber_skirt)
materials.register('deck_grille', _mat_grille_deck, density=1.5)


# ---------------------------------------------------------------------------------------------------------------
# The track links' tile (see bake.register_tile): u across the tile - the outer face (0 .. 0.5), the inner face
# (0.5 .. 0.85), the link edges (0.85 .. 1); v along one link.

TILE_W = 48
TILE_PER = 12
U_OUT = (0.0, 0.5)
U_IN = (0.5, 0.85)
U_EDGE = (0.85, 1.0)


def _tile(style, scheme):
    per = TILE_PER
    w = TILE_W
    rng = np.random.default_rng(7)
    img = np.zeros((per, w, 4))
    img[..., 3] = 255
    steel = np.array((70, 64, 56.0))
    rust = np.array((92, 66, 46.0))
    dust = np.array((122, 106, 82.0))
    bright = np.array((150, 146, 138.0))
    rubber = np.array((30, 30, 31.0))
    yy, xx = np.mgrid[0:per, 0:w]
    u = (xx + 0.5) / w
    v = (yy + 0.5) / per
    col = np.zeros((per, w, 3)) + steel
    # Outer face.
    o = (u < U_OUT[1])
    uo = u / U_OUT[1]
    if style == 'rubber':
        # Western double-pin track: two rubber pads on a steel shoe, end connectors at the edges and in the middle.
        pad = (np.abs(uo - 0.5) > 0.06) & (np.abs(uo - 0.5) < 0.45) & (v > 0.12) & (v < 0.82)
        conn = (v < 0.12) | (v > 0.9)
        c = np.where(pad[..., None], rubber, steel)
        c = np.where(conn[..., None], steel * 0.75, c)
        # Worn bright edges of the connectors.
        c = np.where(((v > 0.9) & (np.abs(uo - 0.5) < 0.48))[..., None], bright * 0.8, c)
    else:
        # Soviet single-pin steel track: a shoe with a raised grouser across it, the hinge lugs between links.
        c = np.zeros((per, w, 3)) + steel
        grouser = (v > 0.38) & (v < 0.62)
        c = np.where(grouser[..., None], bright, c)
        c = np.where(((v > 0.32) & (v <= 0.38))[..., None], steel * 0.55, c)
        hinge = (v < 0.12) | (v > 0.92)
        c = np.where(hinge[..., None], steel * 0.45, c)
        # Lightening holes between the hinge lugs.
        holes = ((np.abs(uo - 0.25) < 0.07) | (np.abs(uo - 0.75) < 0.07)) & (v > 0.68) & (v < 0.86)
        c = np.where(holes[..., None], np.array((20, 18, 16.0)), c)
    col = np.where(o[..., None], c, col)
    # Inner face: the wheel paths polished bright, the guide horns in the middle (dark between them).
    i = (u >= U_IN[0]) & (u < U_IN[1])
    ui = (u - U_IN[0]) / (U_IN[1] - U_IN[0])
    ci = np.zeros((per, w, 3)) + steel * 0.9
    path = (np.abs(ui - 0.5) > 0.12) & (np.abs(ui - 0.5) < 0.42)
    ci = np.where(path[..., None], bright * 0.95, ci)
    horn = (np.abs(ui - 0.5) <= 0.12) & (v > 0.25) & (v < 0.75)
    ci = np.where(horn[..., None], steel * 1.2, ci)
    ci = np.where(((np.abs(ui - 0.5) <= 0.12) & ~horn)[..., None], steel * 0.4, ci)
    col = np.where(i[..., None], ci, col)
    # Edges: the pins' ends.
    e = u >= U_EDGE[0]
    ce = np.zeros((per, w, 3)) + steel * 0.7
    ce = np.where(((v < 0.18) | (v > 0.88))[..., None], bright * 0.7, ce)
    col = np.where(e[..., None], ce, col)
    # Rust, dust and dried mud, the same in every link but varying across.
    n = rng.random((per, w))
    col = col * (0.88 + 0.24 * n[..., None])
    mud = (np.sin(xx * 0.9 + yy * 0.3) + rng.random((per, w)) * 1.5) > 1.6
    col = np.where(mud[..., None] & ~path[..., None], col * 0.4 + dust * 0.6, col)
    rs = rng.random((per, w)) > 0.82
    col = np.where(rs[..., None] & (style != 'rubber'), col * 0.5 + rust * 0.5, col)
    # Scheme dirt: a dusty tone over everything.
    d = getattr(scheme, 'dirt', 0.6)
    col = col * (1 - 0.25 * d) + dust * 0.25 * d
    img[..., :3] = np.clip(col, 0, 255)
    # Two periods with a wrapped row of padding above and below.
    full = np.concatenate([img[-1:], img, img, img[:1]], axis=0)
    return full


bake.register_tile('track_steel', lambda model, scheme: _tile('steel', scheme))
bake.register_tile('track_rubber', lambda model, scheme: _tile('rubber', scheme))
materials.register('track_steel', lambda T, s, m: (np.tile(np.array((70, 64, 56.0)), (len(T['pos']), 1)), 255))
materials.register('track_rubber', lambda T, s, m: (np.tile(np.array((40, 40, 40.0)), (len(T['pos']), 1)), 255))


# ---------------------------------------------------------------------------------------------------------------
# Placing shapes


def frame(origin, x_axis, y_axis):
    """A frame: R (3x3, columns the axes) and the origin; local (x across, y up/out, z along) -> model space."""
    x = normalize(np.asarray(x_axis, dtype=np.float64))
    y = np.asarray(y_axis, dtype=np.float64)
    y = normalize(y - x * np.dot(y, x))
    z = np.cross(x, y)
    return np.stack([x, y, z], axis=1), np.asarray(origin, dtype=np.float64)


def place(g, fr):
    R, t = fr
    return g.transform(R, t)


def slope_frame(origin, angle_deg, facing=1):
    """A frame on a plate sloping down toward the front (facing 1, a glacis) or the back (facing -1): local y = the
    plate's outward normal, local z = up the plate, local x across. angle: the plate's angle from horizontal."""
    a = math.radians(angle_deg)
    n = np.array([0.0, math.cos(a), math.sin(a) * facing])
    along = np.array([0.0, math.sin(a), -math.cos(a) * facing])
    x = np.cross(n, along)
    return np.stack([x, n, along], axis=1), np.asarray(origin, dtype=np.float64)


# ---------------------------------------------------------------------------------------------------------------
# Lofts: hulls through cross-sections, turrets through plan outlines


def section_loft(sections, mat='armour', smooth=25.0, cap0=True, cap1=True, mats=None):
    """A closed body lofted along z through sections: [(z, [(x, y), ...])], each outline going round the same number of
    points counter-clockwise seen from the front (+z looking back: x to the left... the winding is fixed up after)."""
    rings = []
    for z, pts in sections:
        rings.append([(x, y, z) for x, y in pts])
    g = loft(rings, mat, closed=True, cap0=cap0, cap1=cap1, smooth=smooth, mats=mats)
    _orient(g, rings)
    return g


def plan_loft(levels, mat='armour', smooth=25.0, cap_top=True, cap_bottom=False, mats=None):
    """A body lofted up through plan outlines: [(y, [(x, z), ...])], each outline the same number of points going round
    from +x toward +z (counter-clockwise seen from below... the winding is fixed up after)."""
    rings = []
    for y, pts in levels:
        rings.append([(x, y, z) for x, z in pts])
    g = loft(rings, mat, closed=True, cap0=cap_bottom, cap1=cap_top, smooth=smooth, mats=mats)
    _orient(g, rings)
    return g


def _orient(g, rings):
    """Makes a loft face outward: each polygon is checked against the centre of its own band of the rings (works for
    bodies that are star-shaped about their axis, which hulls and turrets are)."""
    cs = [np.asarray(r, dtype=np.float64).mean(axis=0) for r in rings]
    allc = np.mean(cs, axis=0)
    for p in g.polys:
        c = p.v.mean(axis=0)
        # Nearest ring centre along the loft for the side faces; the body centre for the caps.
        fn = geom.face_normal(p.v)
        best = min(cs, key=lambda q: float(np.linalg.norm(q - c)))
        axis = normalize(cs[-1] - cs[0])
        ref = best
        d = c - ref
        # Remove the along-axis part: side faces point away from the axis.
        d_side = d - axis * np.dot(d, axis)
        if np.linalg.norm(d_side) < 1e-6 or abs(np.dot(fn, axis)) > 0.98:
            d_side = c - allc
        if np.dot(fn, d_side) < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
            if p.n is not None:
                p.n = p.n[::-1].copy()
    return g


def superellipse(n, rx, rz, e=2.5, cx=0.0, cz=0.0, front=None, back=None, start=0.0):
    """n points round a superellipse in plan (x, z), from +x toward +z; front/back: the z reach forward and back if
    different from rz."""
    pts = []
    for k in range(n):
        th = 2 * math.pi * k / n + start
        c, s = math.cos(th), math.sin(th)
        x = rx * np.sign(c) * abs(c) ** (2 / e)
        zr = (front if s > 0 and front is not None else back if s < 0 and back is not None else rz)
        z = zr * np.sign(s) * abs(s) ** (2 / e)
        pts.append((cx + x, cz + z))
    return pts


# ---------------------------------------------------------------------------------------------------------------
# Running gear


def road_wheel(c, side, r, width, kind='double', n=14, mat='armour'):
    """A road wheel at c (its centre), the outside toward {side} (+1 right, -1 left): rubber tyres round pressed discs
    with a dished face and a hub cap. kind: 'double' (Soviet pairs with a gap for the guide horns), 'single' (one wide
    disc), 'spoked' (Leopard/Abrams: dished disc with a deep hub)."""
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    hw = width / 2
    a = c - ax * hw
    tyre = min(0.07, r * 0.2)
    g = Geo(40)
    if kind == 'double':
        tw = width * 0.38
        gap = width - 2 * tw
        # The inner disc: its tyre and a plain dished face toward the outer one.
        prof_in = [(0.0, r * 0.5, 'metal_dark'), (0.0, r - tyre, 'rw_rubber'), (0.01, r, 'rw_rubber'), (tw - 0.01, r, 'rw_rubber'),
                   (tw, r - tyre, mat), (tw + 0.03, r * 0.3, mat), (tw + 0.03, 0.0, mat)]
        g.extend(lathe(a, a + ax, prof_in, mat, n, 40))
        b = a + ax * (tw + gap)
        # The outer disc: the tyre, the pressed dish with its rim, the hub and cap.
        prof_out = [(-0.03, r * 0.3, mat), (0.0, r - tyre, 'rw_rubber'), (0.01, r, 'rw_rubber'), (tw - 0.01, r, 'rw_rubber'),
                    (tw, r - tyre, mat), (tw - 0.035, r * 0.66, mat), (tw - 0.01, r * 0.36, mat), (tw + 0.02, r * 0.24, 'metal_dark'),
                    (tw + 0.05, r * 0.15, 'metal_dark'), (tw + 0.055, 0.0, 'metal_dark')]
        g.extend(lathe(b, b + ax, prof_out, mat, n, 40))
    else:
        dish = 0.06 if kind == 'spoked' else 0.03
        prof = [(0.0, r * 0.5, 'metal_dark'), (0.0, r - tyre, 'rw_rubber'), (0.012, r, 'rw_rubber'), (width - 0.012, r, 'rw_rubber'),
                (width, r - tyre, mat), (width - dish, r * 0.66, mat), (width - dish * 0.5, r * 0.36, mat),
                (width + 0.02, r * 0.24, 'metal_dark'), (width + 0.045, r * 0.12, 'metal_dark'), (width + 0.05, 0.0, 'metal_dark')]
        g.extend(lathe(a, a + ax, prof, mat, n, 40))
    return g


def toothed_ring(c, axis, r_root, r_tip, teeth, t0, t1, mat='metal_dark', r_in=None):
    """A ring of sprocket teeth about the axis through c, from t0 to t1 along it: a star outline (rounded tips) and an
    inner circle, joined by flat faces on both sides."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    _, u, w = geom._basis(ax)
    r_in = r_root * 0.75 if r_in is None else r_in
    out2 = []
    for k in range(teeth):
        base = 2 * math.pi * k / teeth
        step = 2 * math.pi / teeth
        for f, rr in ((0.0, r_root), (0.3, r_tip), (0.6, r_tip), (0.9, r_root)):
            th = base + f * step
            out2.append((math.cos(th) * rr, math.sin(th) * rr))
    m = len(out2)
    in2 = [(math.cos(math.atan2(y, x)) * r_in, math.sin(math.atan2(y, x)) * r_in) for x, y in out2]
    g = Geo(30)

    def P(p2, t):
        return c + ax * t + u * p2[0] + w * p2[1]
    for k in range(m):
        k2 = (k + 1) % m
        # Outer wall.
        g.add(np.array([P(out2[k], t0), P(out2[k2], t0), P(out2[k2], t1), P(out2[k], t1)]), mat)
        # Faces.
        g.add(np.array([P(in2[k], t1), P(out2[k], t1), P(out2[k2], t1), P(in2[k2], t1)]), mat)
        g.add(np.array([P(in2[k2], t0), P(out2[k2], t0), P(out2[k], t0), P(in2[k], t0)]), mat)
    fix_outward_about_axis(g, c, ax, (t0 + t1) / 2)
    return g


def fix_outward_about_axis(g, c, ax, tmid):
    """Faces of a ring about an axis: walls face away from the axis, the flat faces away from the ring's mid-plane."""
    c = np.asarray(c, dtype=np.float64)
    for p in g.polys:
        fn = geom.face_normal(p.v)
        m = p.v.mean(axis=0) - c
        along = np.dot(m, ax)
        radial = m - ax * along
        if abs(np.dot(fn, ax)) > 0.7:
            want = ax * (1 if along >= tmid else -1)
        else:
            want = radial
        if np.dot(fn, want) < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
    return g


def sprocket(c, side, r, width, teeth=12, mat='armour', n=14):
    """A drive sprocket: a hub with a domed cap, two toothed rings with the gap between them (where the track's pins
    engage)."""
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    hw = width / 2
    a = c - ax * hw
    g = Geo(40)
    hub = [(0.0, r * 0.3, mat), (0.0, r * 0.62, mat), (width * 0.3, r * 0.5, mat), (width * 0.7, r * 0.5, mat),
           (width, r * 0.6, mat), (width + 0.04, r * 0.35, mat), (width + 0.06, r * 0.2, 'metal_dark'), (width + 0.07, 0.0, 'metal_dark')]
    g.extend(lathe(a, a + ax, hub, mat, n, 40))
    tr = r * 0.82
    for t0, t1 in ((0.0, width * 0.3), (width * 0.7, width)):
        g.extend(toothed_ring(a, ax, tr, r, teeth, t0, t1, 'metal_dark', r_in=r * 0.6))
    # The bolts round the hub cap.
    return g


def idler(c, side, r, width, mat='armour', n=14, kind='double'):
    """The front (or rear) idler: a large dished wheel without rubber (T-72: a pair of spoked discs)."""
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    hw = width / 2
    a = c - ax * hw
    g = Geo(40)
    rim_t = 0.035
    if kind == 'double':
        tw = width * 0.36
        # Inner disc: the rim and a flat face; outer disc: the rim, the dish, the hub.
        g.extend(lathe(a, a + ax, [(0.0, r * 0.4, mat), (0.0, r - rim_t, mat), (0.006, r, 'steel_worn'), (tw - 0.006, r, 'steel_worn'),
                                   (tw, r - rim_t, mat), (tw + 0.02, r * 0.3, mat), (tw + 0.02, 0.0, mat)], mat, n, 40))
        t0 = width - tw
        g.extend(lathe(a, a + ax, [(t0 - 0.02, r * 0.3, mat), (t0, r - rim_t, mat), (t0 + 0.006, r, 'steel_worn'), (width - 0.006, r, 'steel_worn'),
                                   (width, r - rim_t, mat), (width - 0.045, r * 0.6, mat), (width - 0.02, r * 0.32, mat),
                                   (width + 0.03, r * 0.2, 'metal_dark'), (width + 0.05, 0.0, 'metal_dark')], mat, n, 40))
    else:
        prof = [(0.0, r * 0.4, mat), (0.0, r - rim_t, mat), (0.006, r, 'steel_worn'), (width - 0.006, r, 'steel_worn'), (width, r - rim_t, mat),
                (width - 0.05, r * 0.6, mat), (width - 0.02, r * 0.3, mat), (width + 0.03, r * 0.2, 'metal_dark'), (width + 0.05, 0.0, 'metal_dark')]
        g.extend(lathe(a, a + ax, prof, mat, n, 40))
    return g


def return_roller(c, side, r, width, mat='armour', n=10):
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    a = c - ax * (width / 2)
    prof = [(0.0, 0.0, mat), (0.0, r * 0.8, mat), (0.01, r, 'rw_rubber'), (width - 0.01, r, 'rw_rubber'), (width, r * 0.8, mat),
            (width + 0.03, r * 0.4, mat), (width + 0.04, 0.0, mat)]
    g = lathe(a, a + ax, prof, mat, n, 40)
    # The arm holding it to the hull.
    g.extend(cyl(c - ax * (width / 2 + 0.12), c - ax * (width / 2 - 0.005), r * 0.3, 'metal_dark', n=6))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Track belts


def _hull2d(points):
    pts = sorted(set(points))
    if len(pts) < 3:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def belt_path(circles, pitch, sag=0.0, supports_top=None):
    """The inner surface of a track belt round wheels: circles [(z, y, r)] -> points (z, y) one link apart, going
    counter-clockwise in (z, y) (forward along the bottom run, back along the top), and the link pitch used (adjusted so
    a whole number of links goes round)."""
    samp = []
    for z, y, r in circles:
        for k in range(72):
            th = 2 * math.pi * k / 72
            samp.append((round(z + r * math.cos(th), 6), round(y + r * math.sin(th), 6)))
    hull = _hull2d(samp)  # counter-clockwise in (z, y)
    P = np.array(hull + [hull[0]], dtype=np.float64)
    seg = np.linalg.norm(np.diff(P, axis=0), axis=1)
    L = float(seg.sum())
    n = max(8, int(round(L / pitch)))
    pitch = L / n
    cum = np.concatenate([[0.0], np.cumsum(seg)])
    # Start at the bottom middle.
    i0 = int(np.argmin(P[:-1, 1] + np.abs(P[:-1, 0] - np.mean([c[0] for c in circles])) * 0.01))
    s0 = cum[i0]
    out = []
    for k in range(n):
        s = (s0 + k * pitch) % L
        j = int(np.searchsorted(cum, s, side='right') - 1)
        j = min(j, len(seg) - 1)
        f = (s - cum[j]) / (seg[j] or 1)
        out.append(P[j] + (P[j + 1] - P[j]) * f)
    out = np.array(out)
    if sag > 0 and supports_top:
        # The top run droops between its supports (return rollers, the sprocket and idler tops).
        ys = sorted(supports_top)
        top = out[:, 1] > np.median([c[1] for c in circles]) + 0.05
        for k in np.nonzero(top)[0]:
            z = out[k, 0]
            left = [s for s in ys if s[0] <= z]
            right = [s for s in ys if s[0] > z]
            if not left or not right:
                continue
            a, b = left[-1], right[0]
            f = (z - a[0]) / (b[0] - a[0])
            ylin = a[1] + (b[1] - a[1]) * f
            droop = sag * math.sin(math.pi * f) * min(1.0, (b[0] - a[0]) / 1.5)
            out[k, 1] = min(out[k, 1], ylin - droop)
    return out, pitch


def track_belt(x_centre, width, path, thickness=0.06, mat='track_steel', horn=False):
    """The belt as quads, one link each (outer face, inner face, both edges), uv mapped into the track tile so the client
    can scroll the links (see bake.register_tile): u across the tile's sections, v 0..1 along each link."""
    g = Geo(0)
    n = len(path)
    x0, x1 = x_centre - width / 2, x_centre + width / 2
    # Outward normals at the points (the loop is counter-clockwise in (z, y): outward = (dy, -dz) of the tangent).
    nrm = []
    for k in range(n):
        d = path[(k + 1) % n] - path[k - 1]
        d = d / (np.linalg.norm(d) or 1)
        nrm.append(np.array([d[1], -d[0]]))
    nrm = np.array(nrm)
    for k in range(n):
        k2 = (k + 1) % n
        a, b = path[k], path[k2]
        ao, bo = a + nrm[k] * thickness, b + nrm[k2] * thickness

        def V(x, p):
            return np.array([x, p[1], p[0]])
        uo0, uo1 = U_OUT
        ui0, ui1 = U_IN
        ue0, ue1 = U_EDGE
        # Outer face (faces out of the loop).
        _add_oriented(g, [V(x0, ao), V(x1, ao), V(x1, bo), V(x0, bo)], [(uo0, 0), (uo1, 0), (uo1, 1), (uo0, 1)], mat,
                      np.array([0, nrm[k][1] + nrm[k2][1], nrm[k][0] + nrm[k2][0]]))
        # Inner face (faces into the loop).
        _add_oriented(g, [V(x0, a), V(x1, a), V(x1, b), V(x0, b)], [(ui0, 0), (ui1, 0), (ui1, 1), (ui0, 1)], mat,
                      -np.array([0, nrm[k][1] + nrm[k2][1], nrm[k][0] + nrm[k2][0]]))
        # Edges.
        for x, sg in ((x0, -1), (x1, 1)):
            _add_oriented(g, [V(x, a), V(x, ao), V(x, bo), V(x, b)], [(ue0, 0), (ue1, 0), (ue1, 1), (ue0, 1)], mat, np.array([sg, 0, 0.0]))
    return g


def _add_oriented(g, verts, uv, mat, want):
    v = np.array(verts, dtype=np.float64)
    uvs = np.array(uv, dtype=np.float64)
    fn = geom.face_normal(v)
    if np.dot(fn, want) < 0:
        v = v[::-1].copy()
        uvs = uvs[::-1].copy()
    g.add(v, mat, uv=uvs)


# ---------------------------------------------------------------------------------------------------------------
# Guns


def barrel(a, b, profile, mat='armour', n=12):
    """A gun barrel turned along a -> b: profile [(t, r[, mat])] with t in metres from a."""
    return lathe(a, b, profile, mat, n, 35)


def tank_gun_profile(L, r, extractor=None, sleeves=(), muzzle='plain', mat='armour', start_r=None, collar=True):
    """A tank gun's profile from the mantlet (t = 0) to the muzzle (t = L): the barrel radius r, a fume extractor
    (t0, t1, R), thermal sleeve sections (bands of clamps at the given t), the muzzle ('plain' with a reference collar,
    'brake' a two-baffle muzzle brake, 'flash' a cone)."""
    prof = [(0.0, 0.0, mat), (0.0, start_r or r * 1.25, mat)]
    marks = []
    if extractor:
        t0, t1, R = extractor
        marks.append((t0, [(t0 - 0.04, r * 1.12, mat), (t0, R * 0.9, mat), (t0 + 0.06, R, mat), (t1 - 0.08, R, mat), (t1, R * 0.88, mat),
                           (t1 + 0.06, r * 1.08, mat)]))
    for t in sleeves:
        marks.append((t, [(t - 0.03, r * 1.08, mat), (t - 0.02, r * 1.2, 'armour_dark'), (t + 0.02, r * 1.2, 'armour_dark'), (t + 0.03, r * 1.08, mat)]))
    marks.sort(key=lambda m: m[0])
    prof.append((0.25, r * 1.18, mat))
    prof.append((0.35, r * 1.08, mat))
    for _, pts in marks:
        prof.extend(pts)
    if muzzle == 'brake':
        mb = r * 1.75
        prof += [(L - 0.62, r, mat), (L - 0.6, mb * 0.85, mat), (L - 0.55, mb, mat), (L - 0.36, mb, 'black'), (L - 0.32, mb, mat),
                 (L - 0.12, mb, 'black'), (L - 0.08, mb, mat), (L, mb * 0.9, mat), (L, r * 0.65, 'black'), (L - 0.05, 0.0, 'black')]
    elif muzzle == 'collar':
        prof += [(L - 0.16, r, mat), (L - 0.14, r * 1.45, 'steel_worn'), (L - 0.01, r * 1.45, 'steel_worn'), (L, r * 1.3, 'steel_worn'),
                 (L, r * 0.62, 'black'), (L - 0.06, 0.0, 'black')]
    elif muzzle == 'flash':
        prof += [(L - 0.25, r, mat), (L - 0.22, r * 1.15, 'metal_dark'), (L, r * 1.4, 'metal_dark'), (L, r * 0.7, 'black'), (L - 0.05, 0.0, 'black')]
    else:
        prof += [(L - 0.02, r, mat), (L, r * 0.92, 'steel_worn'), (L, r * 0.6, 'black'), (L - 0.05, 0.0, 'black')]
    # Keep t increasing.
    out = []
    last = -1.0
    for p in prof:
        t = max(p[0], last)
        out.append((t,) + tuple(p[1:]))
        last = t
    return out


# ---------------------------------------------------------------------------------------------------------------
# Small fittings


def hatch(c, r, mat='armour', h=0.06, n=12, open_deg=0.0, hinge_dir=(0, 0, -1), handle=True):
    """A round hatch lid on a roof at c (its centre on the roof): a domed lid with a rim and a handle."""
    c = np.asarray(c, dtype=np.float64)
    g = Geo(40)
    g.extend(lathe(c - np.array([0, 0.01, 0]), c + np.array([0, 1, 0]),
                   [(0.0, r * 1.08, mat), (0.03, r * 1.08, mat), (0.035, r, mat), (h * 0.7, r * 0.95, mat), (h, r * 0.6, mat), (h + 0.01, 0.0, mat)],
                   mat, n, 35))
    if handle:
        g.extend(tube([c + np.array([-r * 0.3, h, 0.02]), c + np.array([-r * 0.3, h + 0.03, 0.02]), c + np.array([r * 0.3, h + 0.03, 0.02]),
                       c + np.array([r * 0.3, h, 0.02])], 0.008, 'metal_dark', n=4))
    return g


def periscope(c, w, facing=(0, 0, 1), mat='armour'):
    """A periscope head: a small armoured box with a dark glass window facing out."""
    c = np.asarray(c, dtype=np.float64)
    f = normalize(np.asarray(facing, dtype=np.float64))
    side = normalize(np.cross(np.array([0, 1.0, 0]), f))
    R = np.stack([side, np.array([0, 1.0, 0]), f], axis=1)
    g = rbox((-w / 2, 0, -0.05), (w / 2, 0.07, 0.05), mat)
    g.extend(geom.panel((0, 0.04, 0.0505), w * 0.8, 0.035, (1, 0, 0), (0, 1, 0), 'optic'))
    return g.transform(R, c)


def smoke_launchers(c, count, facing, spacing=0.11, r=0.04, length=0.3, elev=20.0, mat='armour', spread=0.0):
    """A row of smoke grenade launcher tubes on a bracket, pointing along facing (raised by elev degrees)."""
    c = np.asarray(c, dtype=np.float64)
    f = normalize(np.asarray(facing, dtype=np.float64))
    side = normalize(np.cross(np.array([0, 1.0, 0]), f))
    g = Geo(40)
    for k in range(count):
        o = c + side * (k - (count - 1) / 2) * spacing
        yaw = spread * (k - (count - 1) / 2)
        d = normalize(f * math.cos(math.radians(elev)) + np.array([0, math.sin(math.radians(elev)), 0]) + side * math.radians(yaw))
        g.extend(cyl(o, o + d * length, r, mat, n=8, caps=False))
        g.extend(lathe(o + d * length, o + d * (length - 0.02), [(0.0, r, mat), (0.0, r * 0.75, 'black'), (0.02, 0.0, 'black')], mat, 8))
        g.extend(lathe(o, o - d * 0.01, [(0.0, r, mat), (0.01, 0.0, mat)], mat, 8))
    # The bracket under them.
    a = c + side * (-(count - 1) / 2 * spacing - r) - np.array([0, r * 1.2, 0])
    b = c + side * ((count - 1) / 2 * spacing + r) - np.array([0, r * 1.2, 0])
    g.extend(tube([a, b], 0.02, mat, n=4))
    return g


def headlight(c, facing, r=0.08, guard=True, mat='armour', side=(1, 0, 0)):
    """A round headlight in a housing, with a wire guard."""
    c = np.asarray(c, dtype=np.float64)
    f = normalize(np.asarray(facing, dtype=np.float64))
    g = Geo(40)
    g.extend(lathe(c - f * 0.12, c, [(0.0, 0.0, mat), (0.0, r * 0.7, mat), (0.06, r, mat), (0.12, r * 1.05, mat), (0.12, r * 0.92, 'headlight'),
                                      (0.125, 0.0, 'headlight')], mat, 10, 40))
    # The lens uv: lathe uv is (angle, t) - the headlight material wants a disc uv; it reads well enough as a bright lens.
    if guard:
        s = normalize(np.cross(np.array([0, 1.0, 0]), f))
        up = np.array([0, 1.0, 0])
        for dx in (-0.4, 0.4):
            p0 = c - f * 0.05 + s * r * dx * 2.4 + up * (-r * 1.1)
            g.extend(tube([p0, p0 + f * 0.1, p0 + f * 0.12 + up * r * 2.2, p0 + up * r * 2.4], 0.008, 'metal_dark', n=4, caps=False))
    return g


def tow_cable(points, r=0.022, mat='steel_worn'):
    return tube(points, r, mat, n=5)


def era_grid(fr, nx, nz, bw, bl, th, gap=0.012, mat='era', bevel=0.006, wedge=0.0):
    """A grid of ERA bricks on a plate (frame from slope_frame/frame: local x across, y out of the plate, z along it),
    centred on the frame's origin. wedge: bricks thicker at the far end (Kontakt-5 slabs)."""
    g = Geo(30)
    for i in range(nx):
        for j in range(nz):
            x = (i - (nx - 1) / 2) * (bw + gap)
            z = (j - (nz - 1) / 2) * (bl + gap)
            bx = chbox((x - bw / 2, 0, z - bl / 2), (x + bw / 2, th, z + bl / 2), mat, bevel)
            if wedge:
                for p in bx.polys:
                    p.v[:, 1] = np.where(p.v[:, 1] > th / 2, p.v[:, 1] + wedge * (p.v[:, 2] - (z - bl / 2)) / bl, p.v[:, 1])
                    p.n = None
                bx.smooth = 30
            g.extend(bx)
    return place(g, fr)


def antenna(base, height, r=0.008, mat='black', base_mat='metal_dark'):
    base = np.asarray(base, dtype=np.float64)
    g = cyl(base, base + np.array([0, 0.12, 0]), 0.035, base_mat, n=8)
    g.extend(cyl(base + np.array([0, 0.12, 0]), base + np.array([0, height, 0]), r, mat, n=4, r2=r * 0.5))
    return g


def drum(c, axis, r=0.29, length=0.88, mat='armour', n=14):
    """A 200 l fuel drum lying along axis: rolled hoops, the end with its bung."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    a = c - ax * length / 2
    L = length
    prof = [(0.0, 0.0, mat), (0.0, r * 0.95, mat), (0.015, r, mat), (L * 0.3, r, mat), (L * 0.31, r * 1.03, mat), (L * 0.33, r, mat),
            (L * 0.67, r, mat), (L * 0.69, r * 1.03, mat), (L * 0.7, r, mat), (L - 0.015, r, mat), (L, r * 0.95, mat), (L, 0.0, mat)]
    return lathe(a, a + ax * L, prof, mat, n, 35)


def mg(c, facing=(0, 0, 1), length=1.1, mat='metal_dark', shield=False):
    """A heavy machine gun (NSVT / M2 style) on a small mount: receiver, barrel with a flash hider, ammo box."""
    c = np.asarray(c, dtype=np.float64)
    f = normalize(np.asarray(facing, dtype=np.float64))
    s = normalize(np.cross(np.array([0, 1.0, 0]), f))
    R = np.stack([s, np.array([0, 1.0, 0]), f], axis=1)
    g = rbox((-0.05, -0.05, -0.35), (0.05, 0.06, 0.12), mat)
    g.extend(cyl((0, 0, 0.12), (0, 0, length * 0.75), 0.022, mat, n=6))
    g.extend(cyl((0, 0, length * 0.75), (0, 0, length * 0.85), 0.03, mat, n=6))
    g.extend(rbox((0.05, -0.1, -0.2), (0.16, 0.02, -0.02), 'armour'))
    g.extend(cyl((0, -0.05, -0.05), (0, -0.25, -0.05), 0.025, mat, n=6))
    if shield:
        g.extend(rbox((-0.25, -0.1, 0.15), (0.25, 0.25, 0.18), 'armour'))
    return g.transform(R, c)


def box_r(lo, hi, mat='armour', r=0.02):
    return chbox(lo, hi, mat, r)


def chbox(lo, hi, mat='armour', c=0.02, faces=None):
    """A box with chamfered edges (26 faces: 6 sides, 12 bevels, 8 corner triangles) - the cheap rounded box."""
    lo = np.asarray(lo, dtype=np.float64)
    hi = np.asarray(hi, dtype=np.float64)
    ctr = (lo + hi) / 2
    h = (hi - lo) / 2
    c = min(c, float(h.min()) * 0.45)
    if c <= 1e-5:
        return rbox(lo, hi, mat, faces=faces)

    def P(sg, a):
        p = np.empty(3)
        for i in range(3):
            p[i] = sg[i] * (h[i] if i == a else h[i] - c)
        return ctr + p
    g = Geo(30)
    signs = [(sx, sy, sz) for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]
    polys = []
    for a in range(3):
        for sa in (-1, 1):
            polys.append([P(s, a) for s in signs if s[a] == sa])
    for a in range(3):
        b, d = [i for i in range(3) if i != a]
        for sb in (-1, 1):
            for sd in (-1, 1):
                pts = []
                for s in signs:
                    if s[b] == sb and s[d] == sd:
                        pts += [P(s, b), P(s, d)]
                polys.append(pts)
    for s in signs:
        polys.append([P(s, 0), P(s, 1), P(s, 2)])
    for pts in polys:
        V = np.array(pts)
        cen = V.mean(axis=0)
        nrm = normalize(cen - ctr)
        _, u, w = geom._basis(nrm)
        ang = np.arctan2((V - cen) @ w, (V - cen) @ u)
        V = V[np.argsort(ang)]
        if np.dot(geom.face_normal(V), cen - ctr) < 0:
            V = V[::-1]
        g.add(V, mat)
    return g


def slats(x0, x1, y0, y1, z, n, mat='armour', r=0.012, axis='x'):
    """Slat armour: a frame with vertical bars, standing across x at z (or across z at x when axis == 'z')."""
    g = Geo(40)
    if axis == 'x':
        g.extend(tube([(x0, y0, z), (x1, y0, z)], r * 1.4, mat, n=4))
        g.extend(tube([(x0, y1, z), (x1, y1, z)], r * 1.4, mat, n=4))
        for k in range(n + 1):
            x = x0 + (x1 - x0) * k / n
            g.extend(tube([(x, y0, z), (x, y1, z)], r, mat, n=4, caps=False))
    else:
        g.extend(tube([(z, y0, x0), (z, y0, x1)], r * 1.4, mat, n=4))
        g.extend(tube([(z, y1, x0), (z, y1, x1)], r * 1.4, mat, n=4))
        for k in range(n + 1):
            x = x0 + (x1 - x0) * k / n
            g.extend(tube([(z, y0, x), (z, y1, x)], r, mat, n=4, caps=False))
    return g


def fender(x0, x1, prof, th=0.025, mat='armour'):
    """A fender plate across x0..x1 following a side profile [(z, y)] (its top surface), {th} thick."""
    lo, hi = min(x0, x1), max(x0, x1)
    top = [(lo, y, z) for z, y in prof]
    rings = []
    for x in (lo, hi):
        rings.append([(x, y, z) for z, y in prof] + [(x, y - th, z) for z, y in reversed(prof)])
    g = loft(rings, mat, closed=True, cap0=True, cap1=True, smooth=20)
    # Wind it out: each face away from the plate's middle line below it.
    for p in g.polys:
        fn = geom.face_normal(p.v)
        c = p.v.mean(axis=0)
        zz = [q[0] for q in prof]
        yy = [q[1] for q in prof]
        ymid = float(np.interp(c[2], zz, yy)) - th / 2
        if abs(fn[0]) > 0.9:
            want = np.array([np.sign(c[0] - (lo + hi) / 2), 0, 0])
        elif abs(c[1] - ymid) < 1e-6:
            want = np.array([0, 0, np.sign(c[2] - np.mean(zz))])
        else:
            want = np.array([0, c[1] - ymid, 0])
        if np.dot(fn, want) < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
    return g


def mirror_x(g):
    return g.mirrored_x()


def sym(g):
    """The geometry and its mirror image (left and right)."""
    out = g.copy()
    out.extend(g.mirrored_x())
    return out
