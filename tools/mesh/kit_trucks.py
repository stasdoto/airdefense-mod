"""
2.0: the kit for the wheeled military vehicles (m_trucks.py): the box models read back as references (their parts,
pivots, wheels, rails - the contract with the game), the paint schemes, the materials of military trucks (tyres with an
aggressive tread, phased-array faces, launch-container ends, stencils), and the pieces trucks are made of: cabs (a lofted
shell with rounded edges, real glass with the cab's inside behind it), bonnets and wings, frames, axles and bogies,
military wheels, mudguards, fuel tanks, tool boxes, spare wheels, exhausts, mirrors, lamps with guards, wipers, steps,
shelters (kungs), canvas tilts, launch containers, missiles, hydraulic rams, outriggers, radar faces and lattice antennas.

Coordinates as everywhere in tools/mesh: metres, x right, y up, z forward, the ground at y = 0.
"""
import contextlib
import io
import math
import os
import re
import sys

import numpy as np

import geom
import materials
from core import Geo, Model, normalize, face_normal, rot_x, rot_y, rot_z

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, os.path.join(HERE, '..', 'models'))

# ---------------------------------------------------------------------------------------------------------------
# The box models as references


class RefPart:
    def __init__(self, name, pivot, rot, parent, boxes):
        self.name = name
        self.pivot = tuple(float(v) for v in pivot)
        self.rot = tuple(float(v) for v in rot)
        self.parent = parent
        self.boxes = boxes  # [(lo, hi, style)]

    def bounds(self):
        if not self.boxes:
            return None
        lo = np.min([b[0] for b in self.boxes], axis=0)
        hi = np.max([b[1] for b in self.boxes], axis=0)
        return lo, hi


class Ref:
    """What the game knows of a vehicle: the parts (names, pivots, fixed rotations, the parent chain), the wheels, the
    launch rails, the turret / elevator / spinner, the parts that open, the seats and cabs, the paint, the size."""

    def __init__(self, id):
        self.id = id
        self.parts = []
        self.by = {}
        self.wheels = []
        self.rails = []
        self.turret = self.elevator = self.spinner = None
        self.open_parts = []
        self.seats = []
        self.cabs = []
        self.paint = 'camo'

    def add(self, rp):
        self.parts.append(rp)
        self.by[rp.name] = rp

    def bounds(self):
        bs = [p.bounds() for p in self.parts if p.boxes]
        lo = np.min([b[0] for b in bs], axis=0)
        hi = np.max([b[1] for b in bs], axis=0)
        return lo, hi

    def wheel(self, name):
        for n, r, s in self.wheels:
            if n == name:
                return self.by[n].pivot, r, s
        raise KeyError(name)

    def boxes(self, part='body', style=None):
        return [b for b in self.by[part].boxes if style is None or b[2] == style]


_box_models = None


def _box_model(id):
    global _box_models
    if _box_models is None:
        with contextlib.redirect_stdout(io.StringIO()):
            import build
            _box_models = {m.id: m for m in build.models()}
    return _box_models[id]


SKIP = re.compile(r'_d\d$|steering_wheel_d$')


def ref(id):
    m = _box_model(id)
    if m.stub:
        r = java_ref(id)
        r.seats = list(m.seats)
        r.cabs = list(m.cabs)
        return r
    r = Ref(id)
    r.paint = m.paint
    for p in m.parts:
        if SKIP.search(p.name):
            continue
        boxes = [(np.array(b.lo), np.array(b.hi), b.style) for b in p.boxes]
        r.add(RefPart(p.name, p.pivot, p.rot, p.parent.name if p.parent is not None else None, boxes))
    r.wheels = [(n, float(rr), bool(s)) for n, rr, s in m.wheels]
    r.rails = list(m.rails)
    r.turret = m.turret
    r.elevator = m.elevator
    r.spinner = m.spinner
    r.open_parts = list(m.open_parts)
    r.seats = list(m.seats)
    r.cabs = list(m.cabs)
    return r


JAVA_MODELS = os.path.join(ROOT, 'src/client/java/com/stasdoto/airdefense/client/vehicle/VehicleModels.java')
JAVA_GEOM = os.path.join(ROOT, 'src/main/java/com/stasdoto/airdefense/vehicle/VehicleGeometry.java')
HANDMADE_PAINT = {'himars': 0x4D5332, 'patriot': 0x444B2F, 'iris_t': 0x464B38, 'nasams': 0x4E583B, 'iskander': 0x4C5831,
                  'kalibr': 0x57633A, 'shahed': 0x978A64}


def java_ref(id):
    """A hand-made model (VehicleModels.java): its parts in Minecraft's model space (1/16 m, y down) read back."""
    src = open(JAVA_MODELS).read()
    start = src.index('public static LayerDefinition %s()' % id)
    end = src.index('return LayerDefinition.create', start)
    body = src[start:end]
    r = Ref(id)
    r.paint = 'hand'
    var_part = {'root': None}
    abs_piv = {None: np.zeros(3)}
    pat = re.compile(r'PartDefinition (p\d+) = (\w+)\.addOrReplaceChild\("([\w]+)", CubeListBuilder\.create\(\)(.*?)'
                     r'PartPose\.offsetAndRotation\(([^)]*)\)\);', re.S)
    for mt in pat.finditer(body):
        var, parent_var, name, cubes, pose = mt.groups()
        pose = [float(x.strip().rstrip('F')) for x in pose.split(',')]
        parent = var_part[parent_var]
        piv = abs_piv[parent] + np.array([pose[0], -pose[1], pose[2]]) / 16.0
        rot = tuple(math.degrees(a) for a in pose[3:6])
        var_part[var] = name
        abs_piv[name] = piv
        boxes = []
        for bm in re.finditer(r'addBox\(([^)]*?)F?\s*(?:,\s*EnumSet[^)]*\))?\)', cubes):
            nums = [float(x.strip().rstrip('F')) for x in bm.group(1).split(',')[:6]]
            x0, y0, z0, w, h, d = nums
            lo = piv + np.array([x0, -(y0 + h), z0]) / 16.0
            hi = piv + np.array([x0 + w, -y0, z0 + d]) / 16.0
            boxes.append((lo, hi, 'hand'))
        if SKIP.search(name) or re.search(r'_(body|nose_\d)_d\d$', name):
            continue
        r.add(RefPart(name, piv, rot, parent, boxes))
    g = open(JAVA_GEOM).read()
    gs = g.index('new Geometry("%s"' % id)
    ge = g.index(');\n', gs)
    gt = g[gs:ge]
    r.wheels = [(n, float(rr.rstrip('F')), s == 'true') for n, rr, s in re.findall(r'new Wheel\("(\w+)", ([\d.]+F), (\w+)\)', gt)]
    r.rails = [(n, float(a.rstrip('F')), float(b.rstrip('F')), float(c.rstrip('F')))
               for n, a, b, c in re.findall(r'new Rail\("(\w+)", ([-\d.]+F), ([-\d.]+F), ([-\d.]+F)\)', gt)]
    strs = re.findall(r'(null|"\w*")', gt.split('new Rail[]')[0].split('new Wheel[]{')[1].split('}', 1)[1])
    r.turret = strs[0].strip('"') if strs and strs[0] != 'null' else None
    r.elevator = strs[1].strip('"') if len(strs) > 1 and strs[1] != 'null' else None
    tail = gt.split('new Rail[]')[1]
    sp = re.search(r'\}, (null|"\w+"), new String\[\]\{([^}]*)\}', tail)
    r.spinner = None if sp.group(1) == 'null' else sp.group(1).strip('"')
    r.open_parts = re.findall(r'"(\w+)"', sp.group(2))
    return r


def skeleton(r, m):
    """The mesh model's parts mirroring the reference: the same names, pivots, rotations and parents."""
    parts = {}
    for rp in r.parts:
        parent = parts[rp.parent] if rp.parent else None
        parts[rp.name] = m.part(rp.name, rp.pivot, rp.rot, parent)
    return parts


# ---------------------------------------------------------------------------------------------------------------
# Paint schemes (matching the box models' paints)


def scheme(paint, seed=1, dirt=0.85, hand_colour=None):
    if paint == 'camo':
        return materials.Scheme(0x4A5A32, camo=[0x5E4A30, 0x23291C], blob=1.1, stretch=1.7, slant=0.55, dirt=dirt, wear=0.35,
                                seed=seed, gloss=0.15)
    if paint == 'rgreen':
        return materials.Scheme(0x525D37, dirt=dirt, wear=0.35, seed=seed, gloss=0.15)
    if paint == 'ugreen':
        return materials.Scheme(0x55613A, camo=[0x3B4829, 0x6E6E48], blob=1.6, stretch=2.0, slant=0.4, dirt=dirt, wear=0.4,
                                seed=seed, gloss=0.15)
    if paint == 'nato':
        # Bundeswehr three-colour: bronze green, leather brown, tar black.
        return materials.Scheme(0x48523A, camo=[0x5A4634, 0x22241F], blob=1.0, stretch=1.6, slant=0.6, dirt=dirt, wear=0.3,
                                seed=seed, gloss=0.15)
    if paint == 'olive':
        return materials.Scheme(0x5B6236, dirt=dirt, wear=0.35, seed=seed, gloss=0.15)
    if paint == 'sand':
        return materials.Scheme(0xB4A07A, dirt=dirt * 0.8, wear=0.3, seed=seed, gloss=0.12)
    if paint == 'tan':
        return materials.Scheme(0xB59A6A, camo=[0x8C7650, 0xC9B486], blob=1.2, dirt=dirt, wear=0.3, seed=seed, gloss=0.12)
    c = hand_colour if hand_colour is not None else 0x4A5A32
    return materials.Scheme(c, dirt=dirt, wear=0.35, seed=seed, gloss=0.15)


# ---------------------------------------------------------------------------------------------------------------
# Materials


hexc = materials.hexc


def _plain(T, colour, scheme, dirt=0.6, ao=1.0, var=0.05, mud=True):
    n = len(T['pos'])
    rgb = np.tile(hexc(colour), (n, 1)) if np.isscalar(colour) else np.tile(np.asarray(colour, dtype=np.float64), (n, 1))
    rgb = rgb * (1 - var + 2 * var * materials.vnoise(T['pos'], 0.3, 3))[:, None]
    rgb = materials.weather(rgb, T, scheme, dirt, mud=mud)
    return materials.occlusion(rgb, T['ao'], ao)


def _tyre_mil(T, scheme, model):
    """A cross-country tyre: big chevron lugs across the tread (an open V pointing the way it rolls), deep dark grooves
    between them, a narrow rib in the middle, worn and dusty."""
    puv = T['puv']
    u = puv[:, 0]
    t = puv[:, 1]
    n = len(u)
    w = getattr(model, 'tyre_half', 0.25)
    tt = np.clip(t / max(w, 0.05), -1.2, 1.2)
    lugs = getattr(model, 'tyre_lugs', 22)
    ph = (u * lugs + np.abs(tt) * 0.55) % 1.0
    groove = (ph < 0.42) & (np.abs(tt) > 0.12)
    rgb = np.tile(np.array((40, 40, 40.0)), (n, 1))
    rgb[groove] = (17, 17, 17)
    edge = (np.abs(ph - 0.42) < 0.05) | (ph > 0.96)
    rgb[edge & (np.abs(tt) > 0.12)] = (54, 53, 51)
    rgb = materials.weather(rgb, T, scheme, 1.0)
    return materials.occlusion(rgb, T['ao'], 0.7), 255


def _tyre_side(T, scheme, model):
    puv = T['puv']
    n = len(puv)
    rgb = np.tile(np.array((34, 34, 35.0)), (n, 1))
    # The lugs' ends showing round the shoulder, and a ring of raised lettering.
    u = puv[:, 0]
    lugs = getattr(model, 'tyre_lugs', 22)
    rgb[((u * lugs) % 1.0) < 0.42] *= 0.8
    rgb = materials.weather(rgb, T, scheme, 1.0)
    return materials.occlusion(rgb, T['ao'], 0.7), 255


def _grid_face(T, scheme, model, base, line, pitch, frame=0.0):
    """A phased-array face: square elements in a grid (uv in metres)."""
    puv = T['puv']
    u = puv[:, 0] / pitch
    v = puv[:, 1] / pitch
    n = len(u)
    rgb = np.tile(hexc(base), (n, 1))
    fu = u % 1.0
    fv = v % 1.0
    ln = (fu < 0.14) | (fv < 0.14)
    rgb[ln] = hexc(line)
    dot = (np.abs(fu - 0.57) < 0.13) & (np.abs(fv - 0.57) < 0.13)
    rgb[dot] = hexc(base) * 0.72
    rgb = rgb * (0.94 + 0.12 * materials.vnoise(T['pos'], 0.6, 11))[:, None]
    rgb = materials.weather(rgb, T, scheme, 0.4, mud=False)
    return materials.occlusion(rgb, T['ao'], 0.8), 255


def _aesa(T, scheme, model):
    return _grid_face(T, scheme, model, 0x3B4044, 0x51575C, 0.11)


def _aesa_light(T, scheme, model):
    return _grid_face(T, scheme, model, 0x7E848A, 0x969CA1, 0.11)


def _aesa_green(T, scheme, model):
    base = scheme.base
    c = int(base[0]) << 16 | int(base[1]) << 8 | int(base[2])
    return _grid_face(T, scheme, model, c, int(base[0] * 0.8) << 16 | int(base[1] * 0.8) << 8 | int(base[2] * 0.8), 0.16)


def _lattice(T, scheme, model):
    """An open mesh (a reflector, a grating): see-through between the wires (cutout)."""
    puv = T['puv']
    pitch = 0.07
    u = (puv[:, 0] / pitch) % 1.0
    v = (puv[:, 1] / pitch) % 1.0
    wire = (u < 0.24) | (v < 0.24)
    rgb = _plain(T, 0x6C7064, scheme, 0.5, 0.6, mud=False)
    alpha = np.where(wire, 255.0, 0.0)
    return rgb, alpha


def _grating(T, scheme, model):
    """Steps and walkways: a pressed steel grating with diamond holes (solid, the holes dark)."""
    puv = T['puv']
    p = 0.09
    u = (puv[:, 0] / p) % 1.0
    v = (puv[:, 1] / p * 0.6) % 1.0
    hole = (np.abs(u - 0.5) + np.abs(v - 0.5)) < 0.3
    rgb = _plain(T, 0x55585A, scheme, 0.9, 0.9)
    rgb[hole] *= 0.25
    return rgb, 255


def _rubber_mat(T, scheme, model):
    return _plain(T, 0x1E1F20, scheme, 0.3, 0.9, mud=False), 255


def _tpk_end(T, scheme, model):
    """The end cover of a launch container: a dark rubberised cap with radial ribs and a ring."""
    puv = T['puv']
    n = len(puv)
    rgb = np.tile(np.array((44, 46, 44.0)), (n, 1))
    a = puv[:, 0]
    rib = ((a * 12) % 1.0) < 0.12
    rgb[rib] = (60, 62, 60)
    rgb = materials.weather(rgb, T, scheme, 0.5, mud=False)
    return materials.occlusion(rgb, T['ao'], 0.8), 255


def _missile_white(T, scheme, model):
    return _plain(T, 0xD9DAD4, scheme, 0.35, 0.9, var=0.03, mud=False), 255


def _missile_grey(T, scheme, model):
    return _plain(T, 0x8E9390, scheme, 0.4, 0.9, var=0.03, mud=False), 255


def _missile_green(T, scheme, model):
    return _plain(T, 0x4E5A3A, scheme, 0.5, 0.9, var=0.03, mud=False), 255


def _radome(T, scheme, model):
    return _plain(T, 0xC6C8BE, scheme, 0.4, 0.9, var=0.03, mud=False), 255


def _hose(T, scheme, model):
    return _plain(T, 0x1A1A1B, scheme, 0.6, 0.8), 255


def _red(T, scheme, model):
    return _plain(T, 0xA6271E, scheme, 0.5), 255


def _orange(T, scheme, model):
    return _plain(T, 0xD07428, scheme, 0.4), 255


def _cable(T, scheme, model):
    return _plain(T, 0x191A1A, scheme, 0.5, 0.8), 255


def _paint_black(T, scheme, model):
    """Black-painted steel (bumpers, wheel rims of NATO trucks, the chassis)."""
    return _plain(T, 0x202222, scheme, 0.9, 1.0), 255


def _chassis(T, scheme, model):
    """The frame and running gear: the vehicle's colour gone dark with oil and mud."""
    rgb = np.tile(scheme.base * 0.62, (len(T['pos']), 1))
    rgb = materials.weather(rgb, T, scheme, 1.3)
    return materials.occlusion(rgb, T['ao'], 1.0), 255


def _lens(T, scheme, model):
    puv = T['puv']
    n = len(puv)
    rgb = np.tile(np.array((30, 40, 52.0)), (n, 1))
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 70)[:, None]
    return rgb, 255


def _glass_side(T, scheme, model):
    rgb, a = materials.shade('glass', T, scheme, model)
    return rgb, a


def _cab_inside(T, scheme, model):
    """The painted inside of a cab: the vehicle's colour, lighter, a little worn."""
    rgb = np.tile(scheme.base * 1.05 + 10, (len(T['pos']), 1))
    rgb = rgb * (0.95 + 0.1 * materials.vnoise(T['pos'], 0.3, 4))[:, None]
    return materials.occlusion(rgb, T['ao'], 0.9), 255


def _seat_mil(T, scheme, model):
    puv = T['puv']
    rgb = _plain(T, 0x34372E, scheme, 0.15, 0.9, mud=False)
    rgb *= (0.9 + 0.1 * np.sin(puv[:, 1] * 50))[:, None]
    return rgb, 255


def _tilt(T, scheme, model):
    """A canvas tilt: the tarpaulin's own olive drab, faded on top, ribs showing through as darker bands."""
    pos = T['pos']
    n = len(pos)
    col = getattr(model, 'canvas_colour', 0x56583E)
    rgb = np.tile(hexc(col), (n, 1))
    rgb *= (0.9 + 0.1 * materials.fbm(pos, 0.7, 21, 3))[:, None]
    rgb *= (0.93 + 0.07 * np.sin(pos[:, 2] * 34) * np.sin(pos[:, 0] * 30))[:, None]
    rgb = rgb * (1 + 0.12 * np.clip(T['nrm'][:, 1], 0, 1))[:, None]
    rgb = materials.weather(rgb, T, scheme, 0.8)
    return materials.occlusion(rgb, T['ao']), 255


_shade = materials.shade


def _shade_quantised(mat, T, scheme, model):
    """The truck models' texels rounded to steps of model.quant (the fine noise of the paint otherwise keeps the PNG
    from compressing: a step of 4-5 halves the file and cannot be seen once lit)."""
    rgb, alpha = _shade(mat, T, scheme, model)
    q = getattr(model, 'quant', 0)
    if q:
        rgb = np.clip(np.round(np.asarray(rgb) / q) * q, 0, 255)
    return rgb, alpha


materials.shade = _shade_quantised

materials.register('tyre_mil', _tyre_mil, density=1.6)
materials.register('tyre_side', _tyre_side, density=1.0)
materials.register('aesa', _aesa, density=2.6)
materials.register('aesa_light', _aesa_light, density=2.6)
materials.register('aesa_green', _aesa_green, density=2.0)
materials.register('lattice', _lattice, layer='cutout', density=3.0)
materials.register('grating', _grating, density=1.3)
materials.register('rubber_mat', _rubber_mat, density=0.5)
materials.register('tpk_end', _tpk_end, density=1.5)
materials.register('missile_white', _missile_white)
materials.register('missile_grey', _missile_grey)
materials.register('missile_green', _missile_green)
materials.register('radome', _radome)
materials.register('hose', _hose, density=0.5)
materials.register('red', _red)
materials.register('orange', _orange)
materials.register('cable', _cable, density=0.4)
materials.register('paint_black', _paint_black)
materials.register('chassis', _chassis, density=0.6)
materials.register('lens', _lens)
materials.register('cab_inside', _cab_inside, density=0.5)
materials.register('seat_mil', _seat_mil, density=0.6)
materials.register('tilt', _tilt)


# ---------------------------------------------------------------------------------------------------------------
# Small geometry helpers


def V(*a):
    return np.array(a, dtype=np.float64)


def flipped(g):
    """The same polygons facing the other way (an inside lining)."""
    for p in g.polys:
        p.v = p.v[::-1].copy()
        if p.uv is not None:
            p.uv = p.uv[::-1].copy()
        if p.n is not None:
            p.n = -p.n[::-1].copy()
    return g


def about(g, c, R):
    c = np.asarray(c, dtype=np.float64)
    g.transform(t=-c)
    g.transform(R=R)
    g.transform(t=c)
    return g


def box(lo, hi, mat, faces=None, mats=None):
    return geom.box(lo, hi, mat, faces, mats)


def rbox(lo, hi, mat, r=0.03, segs=1, faces=None, mats=None):
    return geom.rbox(lo, hi, mat, r, segs, faces, mats)


def cbox(c, size, mat, r=0.0, faces=None, mats=None):
    return geom.cbox(c, size, mat, r, 1, faces, mats)


def bar(a, b, w, h, mat, up=(0, 1, 0)):
    """A rectangular bar from a to b, w wide and h high (its cross-section turned so 'up' is up)."""
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    d = normalize(b - a)
    u = np.asarray(up, dtype=np.float64)
    if abs(np.dot(u, d)) > 0.95:
        u = np.array([1.0, 0, 0]) if abs(d[0]) < 0.9 else np.array([0, 0, 1.0])
    s = normalize(np.cross(d, u)) * w / 2
    t = normalize(np.cross(s, d)) * h / 2
    ra = [a - s - t, a + s - t, a + s + t, a - s + t]
    rb = [b - s - t, b + s - t, b + s + t, b - s + t]
    g = geom.loft([ra, rb], mat, closed=True, cap0=True, cap1=True, smooth=20)
    return geom.fix_winding(g, (a + b) / 2)


def cyl(a, b, r, mat, n=10, **kw):
    return geom.cyl(a, b, r, mat, n=n, **kw)


def tube(pts, r, mat, n=6, **kw):
    return geom.tube(pts, r, mat, n=n, **kw)


def panel_m(c, su, sv, au, av, mat, double=False):
    """A flat rectangle whose uv runs in metres (grids, gratings)."""
    c = np.asarray(c, dtype=np.float64)
    u = normalize(np.asarray(au, dtype=np.float64))
    v = normalize(np.asarray(av, dtype=np.float64))
    a = c - u * su / 2 - v * sv / 2
    return geom.quad(a, a + u * su, a + u * su + v * sv, a + v * sv, mat, uv=[(0, 0), (su, 0), (su, sv), (0, sv)], double=double)


# ---------------------------------------------------------------------------------------------------------------
# 2D polygon tools (a cab's side profile in the z-y plane)


def poly_area(P):
    a = 0.0
    for i in range(len(P)):
        x0, y0 = P[i]
        x1, y1 = P[(i + 1) % len(P)]
        a += x0 * y1 - x1 * y0
    return a / 2


def ccw(P):
    P = [tuple(p) for p in P]
    return P if poly_area(P) > 0 else P[::-1]


def round_corners(P, r, steps=2, only=None):
    """Rounds the corners of a polygon (each by r, or r[i]) with {steps} extra points (quadratic arcs)."""
    out = []
    n = len(P)
    for i in range(n):
        rr = r[i] if isinstance(r, (list, tuple)) else r
        if only is not None and i not in only:
            rr = 0
        p = np.array(P[i], dtype=np.float64)
        if rr <= 0:
            out.append(tuple(p))
            continue
        a = np.array(P[i - 1], dtype=np.float64)
        b = np.array(P[(i + 1) % n], dtype=np.float64)
        la = np.linalg.norm(a - p)
        lb = np.linalg.norm(b - p)
        d = min(rr, la * 0.45, lb * 0.45)
        p0 = p + (a - p) / la * d
        p1 = p + (b - p) / lb * d
        for k in range(steps + 2):
            t = k / (steps + 1)
            q = (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p + t * t * p1
            out.append(tuple(q))
    return out


def offset_poly(P, d):
    """A convex counter-clockwise polygon moved inwards by d (mitred corners)."""
    P = np.asarray(P, dtype=np.float64)
    n = len(P)
    if d == 0:
        return [tuple(p) for p in P]
    lines = []
    for i in range(n):
        a = P[i]
        b = P[(i + 1) % n]
        e = b - a
        ln = np.linalg.norm(e)
        if ln < 1e-9:
            lines.append(None)
            continue
        nrm = np.array([-e[1], e[0]]) / ln  # inward for a ccw polygon
        lines.append((a + nrm * d, e / ln))
    out = []
    for i in range(n):
        l0 = lines[i - 1]
        l1 = lines[i]
        if l0 is None or l1 is None:
            out.append(tuple(P[i]))
            continue
        (p0, d0), (p1, d1) = l0, l1
        den = d0[0] * d1[1] - d0[1] * d1[0]
        if abs(den) < 1e-9:
            out.append(tuple(p1))
            continue
        t = ((p1[0] - p0[0]) * d1[1] - (p1[1] - p0[1]) * d1[0]) / den
        out.append(tuple(p0 + d0 * t))
    return out


def clip_poly(P, axis, value, keep_greater):
    """Sutherland-Hodgman: keeps the part of a polygon on one side of the line {axis} = value."""
    out = []
    n = len(P)
    for i in range(n):
        a = P[i]
        b = P[(i + 1) % n]
        ina = (a[axis] >= value) if keep_greater else (a[axis] <= value)
        inb = (b[axis] >= value) if keep_greater else (b[axis] <= value)
        if ina:
            out.append(a)
        if ina != inb:
            t = (value - a[axis]) / (b[axis] - a[axis])
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    return out


def clip_rect(P, z0, z1, y0, y1):
    Q = list(P)
    for ax, v, g in ((0, z0, True), (0, z1, False), (1, y0, True), (1, y1, False)):
        Q = clip_poly(Q, ax, v, g)
        if not Q:
            return []
    return Q


def poly_with_holes(outer, holes):
    """One simple polygon made of an outer polygon (ccw) and holes (cw) joined by bridges (Eberly), for ear clipping."""
    poly = [tuple(p) for p in ccw(outer)]
    hs = [list(reversed(ccw(h))) for h in holes if len(h) >= 3]
    hs.sort(key=lambda h: -max(p[0] for p in h))
    for h in hs:
        mi = max(range(len(h)), key=lambda i: h[i][0])
        M = h[mi]
        best = None
        for i in range(len(poly)):
            a = poly[i]
            b = poly[(i + 1) % len(poly)]
            if (a[1] - M[1]) * (b[1] - M[1]) > 0 or a[1] == b[1]:
                continue
            t = (M[1] - a[1]) / (b[1] - a[1])
            x = a[0] + (b[0] - a[0]) * t
            if x < M[0] - 1e-9:
                continue
            if best is None or x < best[0]:
                best = (x, i)
        if best is None:
            continue
        i = best[1]
        a = poly[i]
        b = poly[(i + 1) % len(poly)]
        j = i if a[0] > b[0] else (i + 1) % len(poly)
        P = poly[j]
        ring = h[mi:] + h[:mi]
        poly = poly[:j + 1] + ring + [ring[0], P] + poly[j + 1:]
    return poly


def flat_cap(poly2d, x, side, mat, holes=()):
    """A flat side panel at x (the plane z-y), facing +x (side 1) or -x, with holes cut in it."""
    P = poly_with_holes(poly2d, holes) if holes else ccw(poly2d)
    pts = np.array(P, dtype=np.float64)
    g = Geo(0)
    for tri in geom.ear_clip(pts):
        vv = np.array([[x, pts[k][1], pts[k][0]] for k in tri])
        if np.linalg.norm(np.cross(vv[1] - vv[0], vv[2] - vv[0])) < 1e-10:
            continue
        if face_normal(vv)[0] * side < 0:
            vv = vv[::-1]
        g.add(vv, mat, uv=vv[:, [2, 1]])
    return g


# ---------------------------------------------------------------------------------------------------------------
# The shell: a side profile swept across the width with rounded edges


class Shell:
    """A body (cab, bonnet, shelter) as a side profile (z, y points, counter-clockwise seen from the right) swept from
    -w to +w with its long edges rounded by r. Holes (windows) can be cut in the bands of the sweep (front / back /
    roof: x range and a profile segment range) and in the flat sides (z-y polygons)."""

    def __init__(self, profile, w, r=0.08, steps=2, x0=None):
        self.profile = ccw(profile)
        self.w = w
        self.x0 = -w if x0 is None else x0
        self.r = r
        self.steps = steps
        self.cuts = []
        self.side_holes = []
        self.extra_x = []

    def cut(self, xa, xb, ka, kb):
        """A hole in the swept surface: across xa..xb, profile segments ka..kb-1."""
        self.cuts.append((xa, xb, ka, kb))
        self.extra_x += [xa, xb]

    def side_hole(self, poly):
        self.side_holes.append(poly)

    def xs(self):
        xl, xr = self.x0, self.w
        r = self.r
        out = []
        for k in range(self.steps + 1):
            th = math.pi / 2 * (1 - k / self.steps)
            out.append((xl + r - r * math.sin(th), r * (1 - math.cos(th))))
        mids = sorted(set(round(x, 5) for x in self.extra_x if xl + r < x < xr - r))
        for x in mids:
            out.append((x, 0.0))
        for k in range(self.steps + 1):
            th = math.pi / 2 * k / self.steps
            out.append((xr - r + r * math.sin(th), r * (1 - math.cos(th))))
        return out

    def geo(self, mat='paint', smooth=50):
        xs = self.xs()
        rings = []
        for x, inset in xs:
            P = offset_poly(self.profile, inset)
            rings.append([np.array([x, y, z]) for z, y in P])
        n = len(self.profile)

        def mats(i, k):
            xa = xs[i][0]
            xb = xs[i + 1][0]
            for ca, cb, ka, kb in self.cuts:
                if xa >= ca - 1e-6 and xb <= cb + 1e-6 and ka <= k < kb:
                    return None
            return mat
        g = geom.loft(rings, mat, closed=True, smooth=smooth, mats=mats)
        top = max(g.polys, key=lambda p: p.v[:, 1].mean() - abs(p.v[:, 0].mean() - (self.x0 + self.w) / 2) * 0.01)
        if face_normal(top.v)[1] < 0:
            flipped(g)
            for p in g.polys:
                p.n = None
        side = offset_poly(self.profile, self.r)
        g.extend(flat_cap(side, self.w, 1, mat, self.side_holes))
        g.extend(flat_cap(side, self.x0, -1, mat, self.side_holes))
        return g

    def side_poly(self):
        return offset_poly(self.profile, self.r)

    def point(self, k, t=0.0):
        """A point along profile segment k (t 0..1) as (z, y)."""
        a = np.array(self.profile[k])
        b = np.array(self.profile[(k + 1) % len(self.profile)])
        return a + (b - a) * t


def pane(corners, mat='glass', frame=0.018, frame_mat='rubber', inset=0.012):
    """A window pane over a hole (corners in order, 3D), set back a little, with a rubber seal round it."""
    C = np.array(corners, dtype=np.float64)
    c = C.mean(axis=0)
    nrm = face_normal(C)
    g = Geo(0)
    vv = C - nrm * inset
    g.add(vv, mat, uv=np.array([[0, 0], [1, 0], [1, 1], [0, 1]])[:len(vv)])
    if face_normal(vv).dot(nrm) < 0:
        g.polys[-1].v = g.polys[-1].v[::-1].copy()
    if frame > 0:
        ring = [p - nrm * inset * 0.5 for p in C]
        g.extend(geom.tube(ring, frame, frame_mat, n=4, closed=True))
    return g


def window_side(poly2d, x, side, inset=0.015, frame=0.016):
    """A side window: the glass over a hole cut in a flat side at x, and its seal."""
    P = ccw(poly2d)
    pts3 = [np.array([x - side * inset, y, z]) for z, y in P]
    g = Geo(0)
    vv = np.array(pts3)
    if face_normal(vv)[0] * side < 0:
        vv = vv[::-1]
    g.add(vv, 'glass', uv=vv[:, [2, 1]])
    g.extend(geom.tube([np.array([x - side * 0.004, y, z]) for z, y in P], frame, 'rubber', n=4, closed=True))
    return g


def seam_decal(c, u, v, rects, width=0.012, colour=(28, 30, 24), alpha=200, px=60, facing=None, depth=0.08):
    """Shut lines (doors, hatches) painted as a decal: rects in decal metres [(u0, v0, u1, v1)] about c."""
    su = np.linalg.norm(u)
    sv = np.linalg.norm(v)
    W = max(8, int(su * px))
    H = max(8, int(sv * px))
    img = np.zeros((H, W, 4))
    lw = max(1, int(round(width * px)))
    for (u0, v0, u1, v1) in rects:
        x0 = int((u0 / su + 0.5) * W)
        x1 = int((u1 / su + 0.5) * W)
        y0 = int((0.5 - v1 / sv) * H)
        y1 = int((0.5 - v0 / sv) * H)
        for (a, b, c2, d) in ((x0, y0, x1, y0 + lw), (x0, y1 - lw, x1, y1), (x0, y0, x0 + lw, y1), (x1 - lw, y0, x1, y1)):
            img[max(0, b):max(0, d), max(0, a):max(0, c2), :3] = colour
            img[max(0, b):max(0, d), max(0, a):max(0, c2), 3] = alpha
    return materials.Decal(c, u, v, img, depth=depth)


# ---------------------------------------------------------------------------------------------------------------
# Running gear


def mil_wheel(c, side, r, w, rim='paint', n=18, ctis=True, bolts=8, rim_ratio=0.5, lugs=22):
    """A military wheel: a big cross-country tyre (square shoulders, lugged tread), a deep steel disc in the vehicle's
    paint (or black), the ring of nuts, the central tyre-inflation hub."""
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    hw = w / 2
    rr = r * rim_ratio
    sh = min(w * 0.16, (r - rr) * 0.3)
    prof = [(-hw * 0.86, rr * 1.02, 'tyre_side'), (-hw * 1.0, rr + (r - rr) * 0.45, 'tyre_side'),
            (-hw * 1.0, r - sh * 1.1, 'tyre_side'), (-hw + sh * 0.6, r, 'tyre_mil'), (hw - sh * 0.6, r, 'tyre_mil'),
            (hw * 1.0, r - sh * 1.1, 'tyre_side'), (hw * 1.0, rr + (r - rr) * 0.45, 'tyre_side'), (hw * 0.86, rr * 1.02, 'tyre_side')]
    g = geom.lathe(c, c + ax, prof, 'tyre_mil', n, 50)
    # The disc: the lip, dished in, the flat with the nuts, the hub standing out.
    face = c + ax * hw * 0.86
    m = max(12, n - 4)
    dprof = [(0.0, rr * 1.02, 'rim_dark'), (-0.0, rr * 0.97, rim), (-w * 0.2, rr * 0.66, rim), (-w * 0.2, rr * 0.32, rim),
             (-w * 0.06, rr * 0.24, rim), (-w * 0.05, 0.0, rim)]
    g.extend(geom.lathe(face, face + ax, dprof, rim, m, 40))
    _, u, ww = geom._basis(ax)
    plate = face - ax * w * 0.2
    for k in range(bolts):
        th = 2 * math.pi * k / bolts
        p = plate + (u * math.cos(th) + ww * math.sin(th)) * rr * 0.49
        g.extend(geom.lathe(p, p + ax, [(0, rr * 0.075, 'bolt'), (0.03, rr * 0.075, 'bolt'), (0.03, 0, 'bolt')], 'bolt', 5, 40))
    if ctis:
        # The tyre inflation valve: a pipe from the hub to the rim.
        h0 = face - ax * w * 0.04 + u * rr * 0.12
        g.extend(geom.tube([h0, face - ax * w * 0.1 + u * rr * 0.75], 0.012, 'steel', n=4, caps=False))
    # The inside: a dark disc so the wheel cannot be seen through, the brake drum.
    back = c - ax * hw * 0.8
    g.extend(geom.lathe(back, back - ax, [(0, 0, 'chassis'), (0.0, rr * 1.02, 'chassis'), (0.12, rr * 0.7, 'chassis')], 'chassis', 10, 60))
    return g


def band(lo, hi, axis, pos, width=0.08, thick=0.03, mat='paint', faces=('top', 'bottom', 'left', 'right')):
    """A stiffening rib round a box (lo..hi) at {pos} along {axis} (2 = z): four strips standing proud of the sides,
    without the hidden faces against the box."""
    lo = np.asarray(lo, dtype=np.float64)
    hi = np.asarray(hi, dtype=np.float64)
    g = Geo(20)
    a = axis
    o = [i for i in range(3) if i != a]
    names = {0: ('left', 'right'), 1: ('bottom', 'top'), 2: ('back', 'front')}
    for oi in o:
        for sgn in (0, 1):
            if names[oi][sgn] not in faces:
                continue
            l = lo.copy()
            h = hi.copy()
            l[a] = pos - width / 2
            h[a] = pos + width / 2
            for oj in o:
                if oj != oi:
                    l[oj] -= thick
                    h[oj] += thick
            if sgn:
                l[oi] = hi[oi]
                h[oi] = hi[oi] + thick
            else:
                h[oi] = lo[oi]
                l[oi] = lo[oi] - thick
            keep = [f for f in ('right', 'left', 'top', 'bottom', 'front', 'back') if f != names[oi][1 - sgn]]
            g.extend(geom.box(l, h, mat, faces=keep))
    return g


def fender(x0, x1, zc, yc, r, a0=-10, a1=190, mat='paint', n=8, thick=0.012):
    """A curved mudguard over a wheel (x0..x1 wide), round the axle (zc, yc) at radius r from angle a0 to a1
    (degrees; 0 = straight ahead, 90 = straight up)."""
    pts = []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        pts.append((zc + math.cos(a) * r, yc + math.sin(a) * r))
    g = Geo(50)
    outer = [[np.array([x, y, z]) for z, y in pts] for x in (x0, x1)]
    g.extend(geom.loft(outer, mat, closed=False, smooth=50))
    inner = [[np.array([x, yc + (y - yc) * (r - thick) / r, zc + (z - zc) * (r - thick) / r]) for z, y in pts] for x in (x1, x0)]
    g.extend(geom.loft(inner, 'chassis', closed=False, smooth=50))
    for p in g.polys:
        pass
    _orient_out(g, np.array([(x0 + x1) / 2, yc, zc]))
    # Edge strips at the sides.
    for x, sgn in ((x0, -1), (x1, 1)):
        for k in range(n):
            a, b = pts[k], pts[k + 1]
            ai = (zc + (a[0] - zc) * (r - thick) / r, yc + (a[1] - yc) * (r - thick) / r)
            bi = (zc + (b[0] - zc) * (r - thick) / r, yc + (b[1] - yc) * (r - thick) / r)
            vv = np.array([[x, a[1], a[0]], [x, b[1], b[0]], [x, bi[1], bi[0]], [x, ai[1], ai[0]]])
            if face_normal(vv)[0] * sgn < 0:
                vv = vv[::-1]
            g.add(vv, mat)
    return g


def _orient_out(g, c):
    """Orients polygons of an open curved sheet: the paint side away from c (outer), the chassis side toward it."""
    for p in g.polys:
        fn = face_normal(p.v)
        out = p.v.mean(axis=0) - c
        out[0] = 0
        want = 1 if p.mat != 'chassis' else -1
        if np.dot(fn, out) * want < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
    return g


def flat_guard(x0, x1, z0, z1, y, mat='paint', lip=0.12, flap=0.0):
    """A flat mudguard plate over rear wheels, its edges turned down, a rubber flap behind."""
    g = Geo(30)
    g.extend(box((x0, y, z0), (x1, y + 0.012, z1), mat))
    g.extend(box((x0 if x0 < 0 else x1 - 0.012, y - lip, z0), (x0 + 0.012 if x0 < 0 else x1, y, z1), mat))
    if flap:
        g.extend(box((x0 + 0.02, y - flap, z0 - 0.012), (x1 - 0.02, y, z0), 'rubber'))
    return g


def frame_rails(x, y0, y1, z0, z1, w=0.09, cross=(), mat='chassis'):
    """The ladder frame: two channel rails at +-x (y0..y1 high) and cross members at the given z."""
    g = Geo(20)
    for s in (-1, 1):
        g.extend(box((s * x - w / 2, y0, z0), (s * x + w / 2, y1, z1), mat))
    for z in cross:
        g.extend(box((-x, y0 + 0.03, z - 0.05), (x, y1 - 0.03, z + 0.05), mat))
    return g


def axle(xw, y, z, r=0.11, diff=True, mat='chassis'):
    """An axle housing across between the wheels with the differential's pumpkin and the hubs' reduction gear."""
    g = Geo(40)
    g.extend(geom.cyl((-xw, y, z), (xw, y, z), r * 0.55, mat, n=8))
    if diff:
        g.extend(geom.sphere((0.08, y, z), r * 1.5, mat, n=10, rings=5, squash=0.85))
    return g


def bogie_springs(xs, y, za, zb, mat='chassis'):
    """The tandem bogie between two rear axles: a stack of leaf springs on each side, the trunnion in the middle."""
    g = Geo(25)
    zm = (za + zb) / 2
    L = abs(za - zb) * 0.5 + 0.18
    for s in (-1, 1):
        x = s * xs
        for k in range(4):
            t = 0.03 * k
            g.extend(box((x - 0.05, y + 0.06 - t - 0.025, zm - L + k * 0.1), (x + 0.05, y + 0.06 - t, zm + L - k * 0.1), mat))
        g.extend(geom.cyl((x - 0.12 * s, y + 0.04, zm), (x + 0.1 * s, y + 0.04, zm), 0.09, mat, n=8))
    return g


def fuel_tank(x_in, x_out, y0, y1, z0, z1, kind='box', mat='paint'):
    """A fuel tank hung on the frame: a box with rounded edges and two straps (KamAZ), or a drum (Ural)."""
    g = Geo(40)
    if kind == 'drum':
        r = (y1 - y0) / 2
        yc = (y0 + y1) / 2
        xc = (x_in + x_out) / 2
        g.extend(geom.cyl((xc, yc, z0), (xc, yc, z1), r, mat, n=12, bevel=0.03))
        for z in (z0 + 0.15, z1 - 0.15):
            g.extend(geom.cyl((xc, yc, z - 0.02), (xc, yc, z + 0.02), r + 0.012, 'chassis', n=12))
        g.extend(geom.cyl((xc, y1 - 0.02, z1 - 0.3), (xc, y1 + 0.05, z1 - 0.3), 0.05, 'metal_dark', n=8))
        return g
    lo = (min(x_in, x_out), y0, z0)
    hi = (max(x_in, x_out), y1, z1)
    g.extend(geom.rbox(lo, hi, mat, r=0.05, segs=1))
    s = 1 if x_out > x_in else -1
    for z in (z0 + 0.2, z1 - 0.2):
        g.extend(box((lo[0] - 0.01, y0 - 0.012, z - 0.025), (hi[0] + 0.01, y1 + 0.012, z + 0.025), 'chassis'))
    g.extend(geom.cyl((x_out - s * 0.15, y1, z1 - 0.25), (x_out - s * 0.15, y1 + 0.05, z1 - 0.25), 0.05, 'metal_dark', n=8))
    return g


def toolbox(lo, hi, mat='paint', latch_side='front'):
    """A stowage box with its lid seam (a raised rim) and two latches."""
    lo = np.asarray(lo, dtype=np.float64)
    hi = np.asarray(hi, dtype=np.float64)
    g = Geo(30)
    g.extend(geom.rbox(lo, hi, mat, r=0.02, segs=1))
    yl = hi[1] - (hi[1] - lo[1]) * 0.18
    g.extend(box((lo[0] - 0.008, yl - 0.012, lo[2] - 0.008), (hi[0] + 0.008, yl + 0.012, hi[2] + 0.008), mat))
    side = 1 if (lo[0] + hi[0]) / 2 > 0 else -1
    xf = hi[0] if side > 0 else lo[0]
    for t in (0.25, 0.75):
        z = lo[2] + (hi[2] - lo[2]) * t
        g.extend(box((xf - 0.01 if side > 0 else xf - 0.012, yl - 0.07, z - 0.025), (xf + 0.012 if side > 0 else xf + 0.01, yl + 0.01, z + 0.025), 'steel'))
    return g


def exhaust_stack(x, y0, y1, z, r=0.06, cap=True, bend=None):
    """A vertical exhaust pipe with a heat shield and a rain cap (or bent sideways at the top)."""
    g = Geo(50)
    pts = [(x, y0, z), (x, y1, z)]
    if bend is not None:
        pts.append((x + bend[0], y1 + bend[1], z + bend[2]))
    g.extend(geom.tube(pts, r, 'exhaust', n=8))
    g.extend(geom.cyl((x, y0 + (y1 - y0) * 0.35, z), (x, y0 + (y1 - y0) * 0.75, z), r * 1.45, 'metal_dark', n=8, caps=False))
    g.extend(geom.cyl((x, y0 + (y1 - y0) * 0.75, z), (x, y0 + (y1 - y0) * 0.35, z), r * 1.4, 'metal_dark', n=8, caps=False))
    if cap and bend is None:
        g.extend(geom.cyl((x, y1, z), (x, y1 + 0.02, z), r * 1.3, 'exhaust', n=8))
    return g


def disc(c, r, axis, mat, n=12):
    """A flat round face (a lens, a cap) with uv 0..1 across it."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    _, u, w = geom._basis(ax)
    pts = []
    uv = []
    for k in range(n):
        th = 2 * math.pi * k / n
        pts.append(c + (u * math.cos(th) + w * math.sin(th)) * r)
        uv.append((0.5 + 0.5 * math.cos(th), 0.5 + 0.5 * math.sin(th)))
    g = Geo(0)
    P = np.array(pts)
    UV = np.array(uv)
    if face_normal(P).dot(ax) < 0:
        P, UV = P[::-1], UV[::-1]
    g.add(P, mat, uv=UV)
    return g


def headlamp(c, r=0.09, depth=0.1, guard=True, axis=(0, 0, 1), body='paint', lens='headlight'):
    """A round headlamp: the housing, a chrome rim, the lens with its reflector, a wire guard of three bars."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    g = Geo(40)
    g.extend(geom.lathe(c - ax * depth, c, [(0, 0, body), (0, r * 0.8, body), (depth * 0.6, r * 1.05, body), (depth, r * 1.08, 'chrome'),
                                            (depth, r * 0.92, 'chrome')], body, 12, 50))
    g.extend(disc(c - ax * 0.004, r * 0.93, ax, lens, 12))
    if guard:
        side = normalize(np.cross([0, 1, 0], ax)) if abs(ax[1]) < 0.9 else np.array([1.0, 0, 0])
        f = c + ax * 0.05
        for k in (-0.5, 0, 0.5):
            g.extend(geom.tube([f + side * r * k - np.array([0, r * 1.1, 0]) - ax * 0.05, f + side * r * k - np.array([0, r * 1.1, 0]),
                                f + side * r * k + np.array([0, r * 1.1, 0]), f + side * r * k + np.array([0, r * 1.1, 0]) - ax * 0.05],
                               0.007, 'metal_dark', n=4, caps=False))
    return g


def rect_lamp(c, w, h, mat='headlight_box', axis=(0, 0, 1), depth=0.05, body='paint_black'):
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    side = normalize(np.cross([0, 1, 0], ax))
    g = Geo(30)
    lo = c - side * w / 2 - np.array([0, h / 2, 0]) - ax * depth
    hi = c + side * w / 2 + np.array([0, h / 2, 0])
    g.extend(box(np.minimum(lo, hi), np.maximum(lo, hi), body))
    g.extend(geom.panel(c + ax * 0.004, w * 0.9, h * 0.84, side, (0, 1, 0), mat))
    return g


def mirror(base, arm_to, size=(0.2, 0.3), side=1, back='paint_black'):
    """A rear-view mirror on a tube arm: the arm from the cab to the mirror, the box, its glass facing back."""
    base = np.asarray(base, dtype=np.float64)
    m = np.asarray(arm_to, dtype=np.float64)
    g = Geo(40)
    g.extend(geom.tube([base, base + (m - base) * np.array([0.6, 0.2, 0.6]), m], 0.014, 'metal_dark', n=5))
    w, h = size
    g.extend(geom.rbox((m[0] - w / 2, m[1] - h / 2, m[2] - 0.02), (m[0] + w / 2, m[1] + h / 2, m[2] + 0.035), back, r=0.015))
    g.extend(geom.panel((m[0], m[1], m[2] - 0.022), w * 0.86, h * 0.88, (-1, 0, 0), (0, 1, 0), 'mirror_glass'))
    return g


def wiper(p0, p1, mat='wiper'):
    """A wiper: the arm and the blade lying on a windscreen (p0 the pivot, p1 the tip)."""
    g = Geo(20)
    p0 = np.asarray(p0, dtype=np.float64)
    p1 = np.asarray(p1, dtype=np.float64)
    g.extend(bar(p0, p1, 0.016, 0.012, mat))
    d = normalize(p1 - p0)
    e = normalize(np.cross(d, [0, 0, 1])) if abs(d[2]) < 0.9 else np.array([1, 0, 0.0])
    m = p0 + (p1 - p0) * 0.62
    g.extend(bar(m - d * 0.3, p1 + d * 0.03, 0.02, 0.016, mat))
    return g


def step(x0, x1, y, z0, z1):
    """A boarding step: a steel grating on brackets."""
    g = Geo(20)
    g.extend(box((x0, y - 0.03, z0), (x1, y, z1), 'chassis', faces=('bottom', 'front', 'back', 'left', 'right')))
    g.extend(panel_m(((x0 + x1) / 2, y + 0.001, (z0 + z1) / 2), abs(x1 - x0), abs(z1 - z0), (1, 0, 0), (0, 0, -1), 'grating'))
    return g


def handrail(pts, r=0.016, mat='metal_dark'):
    return geom.tube(pts, r, mat, n=5)


def whip_antenna(base, h=2.6, lean=(0, 0, -0.05)):
    b = np.asarray(base, dtype=np.float64)
    g = Geo(40)
    g.extend(geom.cyl(b, b + [0, 0.12, 0], 0.035, 'rubber', n=6))
    g.extend(geom.cyl(b + [0, 0.12, 0], b + [0, 0.25, 0], 0.02, 'metal_dark', n=6))
    top = b + np.array([lean[0] * h, h, lean[2] * h])
    g.extend(geom.tube([b + [0, 0.25, 0], b + (top - b) * 0.5 + [0, 0.12, 0], top], 0.006, 'black', n=3, caps=False))
    return g


def jerrycan(c, side=1):
    c = np.asarray(c, dtype=np.float64)
    g = Geo(30)
    g.extend(geom.rbox(c - [0.08, 0.235, 0.18], c + [0.08, 0.235, 0.18], 'paint', r=0.02))
    g.extend(box(c + [-0.03, 0.235, 0.05], c + [0.03, 0.27, 0.15], 'paint'))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Launchers, radars


def tpk(a, b, w, h=None, mat='paint', ribs=6, round_=False, end='tpk_end', r=None):
    """A transport-launch container from a (back) to b (front): a box (or a tube) with ribs round it and the end
    covers."""
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    d = normalize(b - a)
    L = np.linalg.norm(b - a)
    g = Geo(40)
    if round_:
        rr = r if r is not None else w / 2
        g.extend(geom.cyl(a, b, rr, mat, n=14, cap_mat=end))
        for k in range(ribs):
            t = (k + 0.5) / ribs
            p = a + (b - a) * t
            g.extend(geom.cyl(p - d * 0.03, p + d * 0.03, rr + 0.025, mat, n=14))
        g.extend(geom.cyl(b - d * 0.05, b + d * 0.01, rr + 0.03, mat, n=14, cap_mat=end))
        g.extend(geom.cyl(a - d * 0.01, a + d * 0.05, rr + 0.03, mat, n=14, cap_mat=end))
        return g
    h = h if h is not None else w
    up = np.array([0, 1.0, 0]) if abs(d[1]) < 0.9 else np.array([0, 0, 1.0])
    g.extend(bar(a, b, w, h, mat, up))
    for k in range(ribs):
        t = (k + 0.5) / ribs
        p = a + (b - a) * t
        g.extend(bar(p - d * 0.03, p + d * 0.03, w + 0.04, h + 0.04, mat, up))
    for p in (a, b):
        pass
    s = normalize(np.cross(d, up))
    u2 = normalize(np.cross(s, d))
    for p, sg in ((b, 1), (a, -1)):
        g.extend(geom.panel(p + d * sg * 0.004, w * 0.86, h * 0.86, s if sg > 0 else -s, u2, end))
    return g


def missile(a, b, r, body='missile_white', nose=0.25, fins=4, fin_len=0.3, fin_h=None, fin_mat=None, n=12, canards=0,
            band=None, fin_phase=45.0):
    """A missile from its tail a to its nose b: a body of revolution with an ogive nose, tail fins (and canards)."""
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    L = np.linalg.norm(b - a)
    d = (b - a) / L
    nl = L * nose
    prof = [(0, 0, 'metal_dark'), (0, r * 0.75, 'metal_dark'), (0.02, r, body)]
    if band:
        for t0, t1, m in band:
            prof.append((L * t0, r, m))
            prof.append((L * t1, r, body))
    prof.append((L - nl, r, body))
    for k in range(1, 5):
        t = k / 5
        prof.append((L - nl + nl * t, r * math.sqrt(max(0.0, 1 - t ** 1.6)), body))
    prof.append((L, 0.0, body))
    prof.sort(key=lambda p: p[0])
    g = geom.lathe(a, b, prof, body, n, 35)
    fm = fin_mat or body
    fh = fin_h if fin_h is not None else r * 1.6
    _, u, w = geom._basis(d)
    for k in range(fins):
        th = math.radians(fin_phase) + 2 * math.pi * k / fins
        e = u * math.cos(th) + w * math.sin(th)
        p0 = a + d * 0.02 + e * r
        pts = [p0, p0 + d * fin_len, p0 + d * fin_len * 0.55 + e * fh, p0 + e * fh]
        g.extend(_fin(pts, np.cross(d, e), 0.008, fm))
        if canards:
            q0 = a + d * (L - nl - 0.05 - canards) + e * r
            g.extend(_fin([q0, q0 + d * canards, q0 + d * canards * 0.4 + e * fh * 0.55, q0 + e * fh * 0.55], np.cross(d, e), 0.006, fm))
    return g


def _fin(pts, nrm, t, mat):
    P = np.array(pts)
    nrm = normalize(nrm) * t / 2
    g = Geo(0)
    g.add(P + nrm, mat)
    g.add((P - nrm)[::-1], mat)
    k = len(P)
    for i in range(k):
        a, b = P[i], P[(i + 1) % k]
        g.add(np.array([a - nrm, b - nrm, b + nrm, a + nrm]), mat)
    geom.fix_winding(g, P.mean(axis=0))
    return g


def ram(a, b, r=0.07, mat='paint', rod='chrome'):
    """A hydraulic ram: the cylinder from a, the polished rod out to b, eyes at both ends."""
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    m = a + (b - a) * 0.55
    g = Geo(40)
    g.extend(geom.cyl(a, m, r, mat, n=10, bevel=0.01))
    g.extend(geom.cyl(m, b, r * 0.55, rod, n=8))
    for p in (a, b):
        g.extend(geom.cyl(p - [r * 0.9, 0, 0], p + [r * 0.9, 0, 0], r * 0.8, 'chassis', n=8))
    return g


def outrigger(x, z, y_top, foot=0.32, down=0.0, side=1, reach=0.0):
    """A levelling jack: the housing on the frame, the leg down to a round foot pad (y = down, 0 = stowed above it)."""
    g = Geo(40)
    xo = x + side * reach
    if reach:
        g.extend(box((min(x, xo) - 0.06, y_top - 0.14, z - 0.08), (max(x, xo) + 0.06, y_top, z + 0.08), 'paint'))
    g.extend(geom.rbox((xo - 0.09, y_top - 0.55, z - 0.09), (xo + 0.09, y_top, z + 0.09), 'paint', r=0.015))
    yb = max(down, 0.06) if down else y_top - 0.75
    g.extend(geom.cyl((xo, y_top - 0.5, z), (xo, yb + 0.06, z), 0.05, 'chrome', n=8))
    g.extend(geom.cyl((xo, yb, z), (xo, yb + 0.06, z), foot / 2, 'chassis', n=12, bevel=0.01))
    return g


def array_face(c, w, h, nrm, up, mat='aesa', frame=0.06, depth=0.18, back='paint', segs_x=1):
    """A flat phased-array antenna: the radiating face (element grid) in a frame, a box behind it."""
    c = np.asarray(c, dtype=np.float64)
    n = normalize(np.asarray(nrm, dtype=np.float64))
    upv = normalize(np.asarray(up, dtype=np.float64))
    upv = normalize(upv - n * np.dot(upv, n))
    s = np.cross(upv, n)
    g = Geo(30)
    corners = []
    for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        corners.append(c + s * su * w / 2 + upv * sv * h / 2)
    C = np.array(corners)
    # The box behind: a loft from the face's outline back by depth.
    back_ring = [p - n * depth for p in C]
    front_ring = [p + n * 0.002 for p in C]
    g.extend(geom.fix_winding(geom.loft([back_ring, front_ring], back, closed=True, smooth=10), c - n * depth / 2))
    g.extend(geom.polygon_cap(back_ring[::-1] if face_normal(np.array(back_ring)).dot(-n) < 0 else back_ring, back))
    # The frame (a raised rim) and the face.
    g.extend(geom.tube([p + n * 0.01 for p in C], frame / 2, back, n=4, closed=True))
    g.extend(panel_m(c + n * 0.006, w - frame, h - frame, s, upv, mat))
    return g


def dish(c, r, axis, depth=0.25, mat='lattice', rim='paint', n=16, rings=3, back=False):
    """A parabolic reflector (a mesh dish) opening along axis, its rim and ribs."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    prof = []
    for k in range(rings + 1):
        t = k / rings
        prof.append((depth * t * t, r * t, mat))
    g = geom.lathe(c, c + ax, [(pz, pr, m) for pz, pr, m in prof], mat, n, 60)
    g2 = geom.lathe(c + ax * 0.002, c + ax, [(pz + 0.0, pr, m) for pz, pr, m in prof], mat, n, 60)
    flipped(g2)
    g.extend(g2)
    g.extend(geom.tube([c + ax * depth + (np.cos(2 * math.pi * k / n) * geom._basis(ax)[1] + np.sin(2 * math.pi * k / n) * geom._basis(ax)[2]) * r
                        for k in range(n)], 0.02, rim, n=4, closed=True))
    return g


def kung(lo, hi, mat='paint', door='back', ribs=True, r=0.05, windows=()):
    """A shelter body (kung): a box with rounded edges, the door with its hinges and handle, a ladder, vents."""
    lo = np.asarray(lo, dtype=np.float64)
    hi = np.asarray(hi, dtype=np.float64)
    g = Geo(40)
    g.extend(geom.rbox(lo, hi, mat, r=r, segs=1))
    if ribs:
        # Roof gutter strips along the top edges.
        for s in (-1, 1):
            x = hi[0] if s > 0 else lo[0]
            g.extend(box((x - 0.02 if s > 0 else x - 0.005, hi[1] - 0.06, lo[2] + 0.05), (x + 0.005 if s > 0 else x + 0.02, hi[1] - 0.03, hi[2] - 0.05), mat))
    xc = (lo[0] + hi[0]) / 2
    if door == 'back':
        z = lo[2]
        dx = xc + (hi[0] - lo[0]) * 0.18
        for yy in (lo[1] + 0.35, hi[1] - 0.45):
            g.extend(box((dx - 0.45, yy, z - 0.02), (dx - 0.3, yy + 0.06, z), 'metal_dark'))
        g.extend(box((dx + 0.28, lo[1] + 0.9, z - 0.04), (dx + 0.31, lo[1] + 1.15, z), 'metal_dark'))
        # The ladder hanging under the door.
        for k in range(3):
            g.extend(box((dx - 0.25, lo[1] - 0.25 - 0.28 * k, z - 0.05), (dx + 0.25, lo[1] - 0.22 - 0.28 * k, z + 0.05), 'metal_dark'))
        for s in (-0.27, 0.27):
            g.extend(box((dx + s - 0.015, lo[1] - 0.85, z - 0.02), (dx + s + 0.015, lo[1], z + 0.02), 'metal_dark'))
    for (wz, wy, wx_side) in windows:
        x = hi[0] if wx_side > 0 else lo[0]
        g.extend(geom.panel((x + 0.004 * wx_side, wy, wz), 0.42, 0.32, (0, 0, 1) if wx_side > 0 else (0, 0, -1), (0, 1, 0), 'glass_dark'))
    return g


def canvas_tilt(x, y0, y1, z0, z1, mat='tilt', hoops=5, r=0.18):
    """A canvas tilt over a cargo body: sides, a crowned roof, the hoops showing as ridges, straps down the sides."""
    prof = [(z0, y0), (z1, y0), (z1, y1), (z0, y1)]
    g = Geo(40)
    sh = Shell(prof, x, r=r, steps=2)
    g.extend(sh.geo(mat))
    for k in range(hoops):
        z = z0 + 0.15 + (z1 - z0 - 0.3) * k / max(1, hoops - 1)
        ring = [(-x - 0.012, y0 + 0.1, z), (-x - 0.012, y1 - r, z), (-x + r, y1 + 0.012, z), (x - r, y1 + 0.012, z), (x + 0.012, y1 - r, z), (x + 0.012, y0 + 0.1, z)]
        g.extend(geom.tube(ring, 0.014, mat, n=4, caps=False))
    for k in range(6):
        z = z0 + 0.3 + (z1 - z0 - 0.6) * k / 5
        for s in (-1, 1):
            g.extend(box((s * (x + 0.006) - 0.006, y0 - 0.02, z - 0.02), (s * (x + 0.006) + 0.006, y0 + 0.12, z + 0.02), 'rubber'))
    return g


# ---------------------------------------------------------------------------------------------------------------
# The inside of a cab (seen through the glass, drawn near only)


def cab_interior(lo, hi, seats, dash_y=None, dash_depth=0.42, drive_x=None, tilt=-60, wall='cab_inside', front_slope=0.0):
    """Inner walls, a floor mat, the dashboard with dials, seats for the crew, a radio. Returns (geo, wheel geo, wheel
    centre) - the steering wheel is its own part (it turns)."""
    lo = np.asarray(lo, dtype=np.float64)
    hi = np.asarray(hi, dtype=np.float64)
    g = Geo(30)
    lining = geom.box(lo, hi, wall, faces=('left', 'right', 'top', 'back', 'front'))
    g.extend(flipped(lining))
    g.extend(flipped(geom.box(lo, (hi[0], lo[1] + 0.001, hi[2]), 'rubber_mat', faces=('bottom',))))
    h = hi[1] - lo[1]
    dy = dash_y if dash_y is not None else lo[1] + h * 0.42
    zf = hi[2] - 0.02
    zd = zf - dash_depth
    # The dashboard: a sloped top, the face with dials toward the crew.
    g.extend(box((lo[0] + 0.02, lo[1] + 0.2, zd + 0.1), (hi[0] - 0.02, dy - 0.04, zf), 'dash'))
    top = geom.loft([[V(lo[0] + 0.02, dy - 0.04, zd + 0.1), V(lo[0] + 0.02, dy + 0.03, zf)], [V(hi[0] - 0.02, dy - 0.04, zd + 0.1), V(hi[0] - 0.02, dy + 0.03, zf)]],
                    'dash', closed=False)
    _face(top, (0, 1, -0.3))
    g.extend(top)
    wheel = None
    centre = None
    for role, sx, sy, sz in seats:
        # A seat: the cushion, the back, a headrest.
        g.extend(geom.rbox((sx - 0.24, sy - 0.12, sz - 0.25), (sx + 0.24, sy - 0.02, sz + 0.2), 'seat_mil', r=0.03))
        g.extend(box((sx - 0.18, lo[1], sz - 0.2), (sx + 0.18, sy - 0.12, sz + 0.12), 'chassis'))
        g.extend(about(geom.rbox((sx - 0.24, sy - 0.04, sz - 0.36), (sx + 0.24, sy + 0.62, sz - 0.24), 'seat_mil', r=0.03), (sx, sy, sz - 0.3), rot_x(8)))
        if role == 'driver' or (drive_x is not None and abs(sx - drive_x) < 0.05):
            g.extend(geom.panel((sx, dy - 0.15, zd + 0.099), 0.42, 0.16, (-1, 0, 0), (0, 1, 0), 'gauges'))
            centre = np.array([sx, sy + 0.42, min(sz + 0.5, zd + 0.02)])
            wheel = steering_wheel(centre, tilt, column_to=zd + 0.12)
        else:
            g.extend(box((sx - 0.2, dy - 0.28, zd + 0.08), (sx + 0.05, dy - 0.1, zd + 0.1), 'metal_dark'))
    # The radio set between the seats, on the back wall.
    g.extend(box((-0.18, lo[1] + 0.9, lo[2] + 0.02), (0.18, lo[1] + 1.15, lo[2] + 0.3), 'olive_radio' if 'olive_radio' in materials.EXTRA else 'metal_dark'))
    return g, wheel, centre


def _face(g, d):
    d = np.asarray(d, dtype=np.float64)
    for p in g.polys:
        if np.dot(face_normal(p.v), d) < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
    return g


def steering_wheel(c, tilt=-60, r=0.2, column_to=None):
    """The steering wheel (a rim, three spokes, the hub) facing the driver, leaning forward by {tilt} degrees, and its
    column down into the dashboard."""
    c = np.asarray(c, dtype=np.float64)
    g = Geo(50)
    ring = [np.array([math.cos(2 * math.pi * k / 12) * r, math.sin(2 * math.pi * k / 12) * r, 0]) for k in range(12)]
    g.extend(geom.tube(ring, 0.018, 'trim', n=4, closed=True))
    for a in (0, math.pi, -math.pi / 2):
        g.extend(geom.tube([V(0, 0, 0), V(math.cos(a) * r, math.sin(a) * r, 0)], 0.012, 'trim', n=4))
    g.extend(geom.cyl(V(0, 0, 0.03), V(0, 0, -0.02), 0.05, 'trim', n=8))
    g.extend(geom.cyl(V(0, 0, 0.03), V(0, 0, 0.45), 0.03, 'trim', n=6))
    g.transform(R=rot_x(-(90 + tilt)))
    g.transform(t=c)
    return g


def lamp_cluster(c, side, axis=(0, 0, -1)):
    """A rear lamp cluster: the red stop/tail lamp and the amber indicator in a guard box."""
    c = np.asarray(c, dtype=np.float64)
    g = Geo(30)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    g.extend(box(c - [0.13, 0.06, 0.04], c + [0.13, 0.06, 0.0], 'paint_black'))
    g.extend(geom.panel(c + ax * 0.045, 0.24, 0.1, np.cross([0, 1, 0], ax) * -1, (0, 1, 0), 'taillight'))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Cabs


def tag_round(P, radii, steps=2):
    """round_corners keeping track of where each original vertex went: (points, [first index of vertex i])."""
    out = []
    first = []
    for i in range(len(P)):
        r = radii[i]
        first.append(len(out))
        if r <= 0:
            out.append(tuple(P[i]))
            continue
        pts = round_corners([P[i - 1], P[i], P[(i + 1) % len(P)]], [0, r, 0], steps)
        out += pts[1:-1]
    return out, first


class Cab:
    """A truck cab built from a side profile: the shell with rounded edges, the windscreen panes in holes cut in it,
    the door windows in the flat sides, the inside (a lining with the same holes, dashboard, seats, the steering
    wheel) seen through them.

    profile: [(z, y, radius, tag)], counter-clockwise seen from the right; tags 'ws0'/'ws1' mark the windscreen's
    lower and upper frame points (the glass spans between them)."""

    def __init__(self, profile, w, r=0.1, x0=None, steps=2):
        pts = [(p[0], p[1]) for p in profile]
        if poly_area(pts) < 0:
            profile = profile[::-1]
            pts = pts[::-1]
        radii = [p[2] if len(p) > 2 else 0.0 for p in profile]
        self.pts, first = tag_round(pts, radii)
        self.tags = {}
        for i, p in enumerate(profile):
            if len(p) > 3 and p[3]:
                self.tags[p[3]] = first[i]
        self.w = w
        self.x0 = -w if x0 is None else x0
        self.r = r
        self.steps = steps
        self.shell = Shell(self.pts, w, r, steps, x0=self.x0)
        self.ws = []
        self.side_windows = []

    def windscreen(self, panes):
        """panes: [(xa, xb)] across the windscreen hole."""
        ka = self.tags['ws0']
        kb = self.tags['ws1']
        if kb < ka:
            ka, kb = kb, ka
        for xa, xb in panes:
            self.shell.cut(xa, xb, ka, kb)
            self.ws.append((xa, xb, ka, kb))

    def side_window(self, z0, z1, y0, y1, follow=0.07, radius=0.04):
        """A window in both flat sides: the rectangle clipped to the side outline brought in by {follow}."""
        side = offset_poly(self.shell.profile, self.r + follow)
        P = clip_rect(side, z0, z1, y0, y1)
        if len(P) >= 3:
            P = round_corners(ccw(P), radius, 1)
            self.shell.side_hole(P)
            self.side_windows.append(P)
        return P

    def geo(self, mat='paint'):
        g = self.shell.geo(mat)
        for xa, xb, ka, kb in self.ws:
            for k in range(ka, kb):
                a = self.pts[k]
                b = self.pts[(k + 1) % len(self.pts)]
                corners = [V(xa, a[1], a[0]), V(xb, a[1], a[0]), V(xb, b[1], b[0]), V(xa, b[1], b[0])]
                if face_normal(np.array(corners)).dot(self._out(k)) < 0:
                    corners = corners[::-1]
                g.extend(pane(corners, inset=0.012, frame=0.0))
            # The rubber seal round the whole pane.
            a = self.pts[ka]
            b = self.pts[kb]
            ring = [V(xa, a[1], a[0]), V(xb, a[1], a[0]), V(xb, b[1], b[0]), V(xa, b[1], b[0])]
            g.extend(geom.tube(ring, 0.017, 'rubber', n=4, closed=True))
        for P in self.side_windows:
            for x, s in ((self.w, 1), (self.x0, -1)):
                g.extend(window_side(P, x, s))
        return g

    def _out(self, k):
        a = np.array(self.pts[k])
        b = np.array(self.pts[(k + 1) % len(self.pts)])
        e = b - a
        n2 = np.array([e[1], -e[0]])  # outward for a ccw polygon in (z, y)
        return V(0, n2[1], n2[0])

    def lining(self, t=0.05, mat='cab_inside'):
        """The inner walls: the profile brought in by t, swept, facing in, with the same windows."""
        inner = Shell(offset_poly(self.pts, t), self.w - t, max(0.02, self.r - t), self.steps, x0=self.x0 + t)
        for xa, xb, ka, kb in self.ws:
            inner.cut(xa, xb, ka, kb)
        for P in self.side_windows:
            inner.side_hole(P)
        g = inner.geo(mat)
        return flipped(g)

    def front_point(self, y):
        """The z of the cab's front surface at height y (the furthest forward profile point there)."""
        best = None
        P = self.pts
        for i in range(len(P)):
            a, b = P[i], P[(i + 1) % len(P)]
            if (a[1] - y) * (b[1] - y) <= 0 and a[1] != b[1]:
                z = a[0] + (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1])
                best = z if best is None else max(best, z)
        return best

    def ws_corners(self):
        ka = self.tags['ws0']
        kb = self.tags['ws1']
        return self.pts[min(ka, kb)], self.pts[max(ka, kb)]


def door_seams(model, x, z0, z1, y0, y1, side, hinge_front=True):
    """The shut lines of a door painted on a flat side, its hinges and handle (geometry)."""
    zc = (z0 + z1) / 2
    yc = (y0 + y1) / 2
    L = z1 - z0 + 0.1
    H = y1 - y0 + 0.1
    u = V(0, 0, -L) if side > 0 else V(0, 0, L)
    v = V(0, H, 0)
    rect = (-(z1 - z0) / 2, -(y1 - y0) / 2, (z1 - z0) / 2, (y1 - y0) / 2)
    model.decals.append(seam_decal(V(x, yc, zc), u, v, [rect], width=0.014))
    g = Geo(20)
    zh = z1 - 0.03 if hinge_front else z0 + 0.03
    for yy in (y0 + 0.15, y1 - 0.25):
        g.extend(cyl((x + side * 0.015, yy, zh), (x + side * 0.015, yy + 0.1, zh), 0.016, 'paint', n=6))
    zhd = z0 + 0.1 if hinge_front else z1 - 0.1
    g.extend(box((x - 0.005 if side > 0 else x - 0.025, yc + 0.08, zhd - 0.07), (x + 0.025 if side > 0 else x + 0.005, yc + 0.11, zhd + 0.07), 'metal_dark'))
    return g


def cab_inside_part(body, name, cab, seats, near=24.0, dash_y=None, dash_depth=0.4, tilt=-60, x_range=None):
    """The inside of a cab as its own part (drawn near only): lining, dashboard, seats; the steering wheel a child."""
    inside = body.part(name)
    inside.near = near
    lo = V(cab.x0 + 0.06, min(p[1] for p in cab.pts) + 0.06, min(p[0] for p in cab.pts) + 0.06)
    dy = dash_y if dash_y is not None else lo[1] + 0.5
    zf = cab.front_point(dy) or max(p[0] for p in cab.pts)
    hi = V(cab.w - 0.06, max(p[1] for p in cab.pts) - 0.06, zf - 0.06)
    if x_range is not None:
        seats = [s for s in seats if x_range[0] <= s[1] <= x_range[1]]
    g, wheel, c = cab_interior(lo, hi, seats, dash_y=dash_y, dash_depth=dash_depth, tilt=tilt)
    g.polys = [p for p in g.polys if p.mat not in ('cab_inside', 'rubber_mat')]
    g.extend(cab.lining())
    g.extend(_face(geom.quad(V(lo[0], lo[1] - 0.04, lo[2]), V(hi[0], lo[1] - 0.04, lo[2]), V(hi[0], lo[1] - 0.04, hi[2]), V(lo[0], lo[1] - 0.04, hi[2]),
                             'rubber_mat'), (0, 1, 0)))
    inside.add(g)
    if wheel is not None:
        sw = inside.part('steering_wheel', tuple(c))
        sw.near = near
        sw.add(wheel)
    return inside
