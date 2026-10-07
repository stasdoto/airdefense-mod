"""
Gun model kit (1.24): small arms built from boxes in centimetres, painted into a per-gun texture atlas, written out
as Minecraft item models (at the hip, aimed, and a 3D model for item frames and the ground), the item definition
and a 32x32 inventory icon rendered from the same painted boxes.

Gun space: x to the right, y up, the barrel points to -z; the origin is where the hand holds the pistol grip (top
front of the grip). Everything is real size (1 unit = 1 cm); the export scales it to fit Minecraft's -16..32 model
box and the display transform scales it back to real size in the world.
"""
import json
import math
import os

import numpy as np
from PIL import Image
from scipy.ndimage import zoom as nd_zoom

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense'))


def path(*p):
    full = os.path.join(ROOT, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def write_json(obj, *p):
    with open(path(*p), 'w') as f:
        json.dump(obj, f, indent=1)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


# ----------------------------------------------------------------------------------------------------------------
# Styles: how a face is painted

STYLES = {
    # Phosphated / blued steel: near black, worn lighter on the edges.
    'black': dict(kind='metal', base=0x2B2C2F, wear=0x5A5D62),
    'gunmetal': dict(kind='metal', base=0x3C3F44, wear=0x6C7076),
    'steel': dict(kind='brushed', base=0x8C9096),
    'bright': dict(kind='brushed', base=0xB4B8BD),
    'alu': dict(kind='metal', base=0x34363A, wear=0x7A7E84),
    # Polymer furniture.
    'poly': dict(kind='stipple', base=0x232426),
    'poly_grey': dict(kind='stipple', base=0x3A3C3F),
    'tan': dict(kind='stipple', base=0xA48A62),
    'tan_metal': dict(kind='metal', base=0x9C8460, wear=0xC2AC86),
    'odg': dict(kind='stipple', base=0x575B3A),
    'rgreen': dict(kind='stipple', base=0x47503A),
    'plum': dict(kind='mottle', base=0x6A2E1E, c2=0x55241A),
    'plum_grooves': dict(kind='grooves', base=0x6A2E1E),
    'poly_grooves': dict(kind='grooves', base=0x26272A),
    'wood_grooves': dict(kind='grooves', base=0x8A4A26),
    # Wood: laminated birch (AKM), walnut (SVD, hunting rifles), light beech.
    'wood': dict(kind='wood', base=0x7C3B1E, c2=0x5A2814),
    'walnut': dict(kind='wood', base=0x5E3A22, c2=0x442818),
    'beech': dict(kind='wood', base=0xA9744A, c2=0x8A5A36),
    'brass': dict(kind='brushed', base=0xC49646),
    'copper': dict(kind='brushed', base=0xB06A3A),
    'rubber': dict(kind='plain', base=0x18181A),
    'glass': dict(kind='lens', base=0x1E3A44),
    'glass_red': dict(kind='lens', base=0x5A1A1A),
    'emitter': dict(kind='plain', base=0xE02A20, flat=True),
    'glow_green': dict(kind='plain', base=0x5AE07A, flat=True),
    # Picatinny rails (ribs across the length), cooling slots, magazine ribs, chequered grips.
    'rail': dict(kind='rail', base=0x2E2F32, wear=0x55585D),
    'rail_tan': dict(kind='rail', base=0x9C8460, wear=0xBCA67E),
    'slots': dict(kind='slots', base=0x2A2B2E),
    'slots_tan': dict(kind='slots', base=0xA48A62),
    'mag': dict(kind='ribs', base=0x2A2B2D),
    'mag_tan': dict(kind='ribs', base=0x9C845E),
    'mag_plum': dict(kind='ribs', base=0x6A2E1E),
    'mag_steel': dict(kind='ribs', base=0x383B40),
    'grip': dict(kind='checker', base=0x26272A),
    'grip_tan': dict(kind='checker', base=0x9E865E),
    'grip_wood': dict(kind='checker', base=0x6E3A1E),
    # Launchers.
    'tube_green': dict(kind='tube', base=0x4E5634, band=0xC8B040),
    'tube_olive': dict(kind='tube', base=0x5E6040, band=0xD8D8D0),
    'tube_tan': dict(kind='tube', base=0x9A8A64, band=0x3A3A30),
    'tube_dark': dict(kind='tube', base=0x34392C, band=0xC8B040),
    'warhead_green': dict(kind='metal', base=0x55603A, wear=0x7A8458),
    'warhead_black': dict(kind='metal', base=0x2A2C24, wear=0x50544A),
    'canvas': dict(kind='stipple', base=0x5E5A40),
    'white': dict(kind='plain', base=0xD8D8D2),
    'yellow': dict(kind='plain', base=0xC8A830),
    'red': dict(kind='plain', base=0x9A2420),
    'orange': dict(kind='plain', base=0xD07A2A),
    # 1.24 additions.
    'plum_dark': dict(kind='plain', base=0x3A1810),
    'wood_dark': dict(kind='plain', base=0x40200F),
    'groove': dict(kind='plain', base=0x141416),
    'blued': dict(kind='metal', base=0x22252C, wear=0x4C5664),
    'park': dict(kind='metal', base=0x35373A, wear=0x5E6267),
    'fde': dict(kind='stipple', base=0x8A7556),
    'fde_metal': dict(kind='metal', base=0x7E6A4E, wear=0xA69070),
    'fde_grip': dict(kind='checker', base=0x86714F),
    'mag_fde': dict(kind='ribs', base=0x86714F),
    'od': dict(kind='stipple', base=0x4C5034),
    'od_metal': dict(kind='metal', base=0x4E5236, wear=0x787B58),
    'od_grip': dict(kind='checker', base=0x4A4E33),
    'mag_od': dict(kind='ribs', base=0x4A4E33),
    'ukr_green': dict(kind='stipple', base=0x5A5E3E),
    'mark_white': dict(kind='plain', base=0xDADAD2, flat=True),
    'mark_red': dict(kind='plain', base=0xB02A22, flat=True),
    'mark_yellow': dict(kind='plain', base=0xD8B840, flat=True),
    'brass_case': dict(kind='brushed', base=0xC8A050),
    'grey_tube': dict(kind='tube', base=0x6E7266, band=0xD8D8D0),
    'jav_tube': dict(kind='tube', base=0x5C6044, band=0xC8B040),
    'clu': dict(kind='metal', base=0x50553C, wear=0x7A8060),
    'nlaw_green': dict(kind='tube', base=0x48523A, band=0xE0D8B0),
}

SHADE = {'up': 1.12, 'down': 0.66, 'east': 0.94, 'west': 0.94, 'north': 0.86, 'south': 0.86}


def mul(c, k):
    return np.array([c[0] * k, c[1] * k, c[2] * k])


def vnoise(X, Y, cell, seed):
    """Smooth value noise 0..1 at world coordinates (cm), lattice cells of {cell} cm."""
    gx = X / cell
    gy = Y / cell
    x0 = np.floor(gx).astype(np.int64)
    y0 = np.floor(gy).astype(np.int64)
    fx = gx - x0
    fy = gy - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)

    def h(i, j):
        v = (i * 374761393 + j * 668265263 + seed * 1442695041) & 0xFFFFFFFF
        v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
        return (v & 0xFFFF) / 65535.0
    a = h(x0, y0)
    b = h(x0 + 1, y0)
    c = h(x0, y0 + 1)
    d = h(x0 + 1, y0 + 1)
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy


class Painter:
    def __init__(self, seed):
        self.seed = seed
        self.rng = np.random.default_rng(seed)

    def paint(self, b, face, w, h, R):
        """Paints one face of box b (w x h pixels), with the patterns laid out in gun space so that a part made of
        several boxes (a curved magazine, a stock) is painted as one piece."""
        style = b.sides.get(face, b.style)
        st = STYLES[style]
        kind = st['kind']
        base = rgb(st['base'])
        k = 1.0 if st.get('flat') else SHADE[face]
        (x1, y1, z1), (x2, y2, z2) = b.lo, b.hi
        jj = (np.arange(w) + 0.5) / R
        ii = (np.arange(h) + 0.5) / R
        J, I = np.meshgrid(jj, ii)
        # (A, C): A runs along the gun (z) where it can, C across it; P = the third coordinate (depth of the face).
        if face == 'east':
            A, C = z2 - J, y2 - I
        elif face == 'west':
            A, C = z1 + J, y2 - I
        elif face == 'north':
            A, C = x2 - J, y2 - I
        elif face == 'south':
            A, C = x1 + J, y2 - I
        elif face == 'up':
            C, A = x1 + J, z1 + I
        else:
            C, A = x1 + J, z2 - I
        end = face in ('north', 'south')
        sd = self.seed * 7 + {'east': 1, 'west': 2, 'north': 3, 'south': 4, 'up': 5, 'down': 6}[face]
        img = np.zeros((h, w, 3))
        img[...] = mul(base, k)
        px = lambda s: self.rng.normal(0, s, (h, w, 1))
        if kind == 'metal':
            img += (vnoise(A, C, 5.0, sd)[..., None] - 0.5) * 12 + (vnoise(A, C, 0.9, sd + 9)[..., None] - 0.5) * 5 + px(1.8)
        elif kind in ('stipple', 'ribs'):
            img += (vnoise(A, C, 3.0, sd)[..., None] - 0.5) * 6 + px(4.5)
        elif kind == 'mottle':
            m = vnoise(A, C, 2.2, sd)[..., None]
            m = np.clip((m - 0.45) * 2.5, 0, 1) * 0.35
            img = img * (1 - m) + mul(rgb(st['c2']), k) * m
            img += px(2.2)
        elif kind == 'brushed':
            if end:
                img += px(4)
            else:
                img += (vnoise(A / 25.0, C, 0.12, sd)[..., None] - 0.5) * 22 + px(1.5)
        elif kind == 'plain':
            img += px(1.4)
        elif kind == 'wood':
            c2 = rgb(st['c2'])
            if end:
                r = np.sqrt((A - 0.3) ** 2 + (C + 6.0) ** 2)
                t = (np.sin(r * 9.0) + 1) / 2
            else:
                wav = vnoise(A / 9.0, C, 1.2, sd)
                t = (np.sin((C + wav * 1.6) * 2 * np.pi / 0.55) + 1) / 2
                t = t ** 3 * 0.7 + vnoise(A, C, 0.6, sd + 3) * 0.3
            img = img * (1 - t[..., None] * 0.5) + mul(c2, k) * (t[..., None] * 0.5)
            img += px(2.5)
        elif kind == 'grooves':
            img += px(3) + (vnoise(A, C, 3.0, sd)[..., None] - 0.5) * 6
            if not end:
                g = (np.mod(C, 1.05) < 0.2)
                img[g] *= 0.62
        elif kind == 'rail':
            img += px(2)
            if not end:
                ribs = np.mod(A, 1.0) < 0.5
                wear = mul(rgb(st['wear']), k)
                img[ribs] = img[ribs] * 0.62 + wear * 0.38
                img[~ribs] *= 0.68
        elif kind == 'checker':
            c = (np.mod(A + C, 0.45) < 0.15) | (np.mod(A - C, 0.45) < 0.15)
            img += px(2)
            img[c] *= 0.7
        elif kind == 'tube':
            img += (vnoise(A, C, 6.0, sd)[..., None] - 0.5) * 14 + px(2.5)
        elif kind == 'slots':
            img += px(3)
            if not end and min(w, h) >= 3:
                L = (A - min(z1, z2) if face in ('east', 'west', 'up', 'down') else A)
                span = (y2 - y1) if face in ('east', 'west') else (x2 - x1)
                rel = (C - (y1 if face in ('east', 'west') else x1)) / max(span, 1e-6)
                m = (np.mod(L, 2.4) < 1.2) & (rel > 0.25) & (rel < 0.75)
                img[m] = np.array([12, 12, 14])
        elif kind == 'lens':
            g = np.clip(((J / max(1e-6, jj[-1] + 0.5 / R)) + (I / max(1e-6, ii[-1] + 0.5 / R))) / 2, 0, 1)
            img[..., 0] = base[0] + g * 40
            img[..., 1] = base[1] + (1 - g) * 40
            img[..., 2] = base[2] + (1 - g) * 70
            hl = np.abs(J - I * 0.8 - (jj[-1] * 0.25)) < max(0.15, (jj[-1]) * 0.08)
            img[hl] = img[hl] * 0.4 + np.array([210, 225, 235]) * 0.6
        else:
            raise ValueError(kind)
        # Edges: metal is worn bright where hands and holsters rub it, everything else darkens a little at corners.
        if b.edges and w >= 3 and h >= 3 and not st.get('flat'):
            if kind in ('metal', 'rail'):
                wear = mul(rgb(st['wear']), k)
                img[0, :] = img[0, :] * 0.45 + wear * 0.55
                img[:, 0] = img[:, 0] * 0.65 + wear * 0.35
                img[:, -1] = img[:, -1] * 0.65 + wear * 0.35
                img[-1, :] *= 0.78
            else:
                img[0, :] *= 0.88
                img[-1, :] *= 0.74
                img[:, 0] *= 0.85
                img[:, -1] *= 0.85
        return np.clip(img, 0, 255)


# ----------------------------------------------------------------------------------------------------------------
# Boxes

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def rot_matrix(ax, deg):
    """Minecraft's element rotation (right-handed: +x raises the front (-z) end, +y turns the front to the left
    (-x), +z turns the top to the left)."""
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if ax == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if ax == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Box:
    def __init__(self, lo, hi, style, rot=0, aim=True, tags=(), faces=None, sides=None, ax='z', origin=None, edges=True, euler=None):
        self.lo = tuple(min(lo[i], hi[i]) for i in range(3))
        self.hi = tuple(max(lo[i], hi[i]) for i in range(3))
        self.style = style
        self.rot = rot          # degrees about self.ax through origin (default: the box centre); any angle
        self.ax = ax
        self.euler = euler      # or (x, y, z) degrees, applied as Minecraft's Euler XYZ rotation
        self.origin = origin
        self.aim = aim          # drawn in the aimed view
        self.tags = set(tags)   # 'loaded' = only while loaded, 'empty' = only when empty
        self.faces = tuple(faces) if faces else FACES
        self.sides = dict(sides or {})
        self.edges = edges      # darken / wear the edges (off for the slices of a part made of several boxes)

    def size(self):
        return tuple(self.hi[i] - self.lo[i] for i in range(3))

    def center(self):
        return tuple((self.lo[i] + self.hi[i]) / 2 for i in range(3))

    def rotated(self):
        return bool(self.rot) or self.euler is not None

    def matrix(self):
        if self.euler is not None:
            x, y, z = self.euler
            return rot_matrix('x', x) @ rot_matrix('y', y) @ rot_matrix('z', z)
        if self.rot:
            return rot_matrix(self.ax, self.rot)
        return np.eye(3)

    def pivot(self):
        return np.array(self.origin if self.origin is not None else self.center(), dtype=np.float64)

    def corners(self):
        """The eight corners where they really are (after the rotation)."""
        R = self.matrix()
        o = self.pivot()
        out = []
        for x in (self.lo[0], self.hi[0]):
            for y in (self.lo[1], self.hi[1]):
                for z in (self.lo[2], self.hi[2]):
                    out.append(R @ (np.array([x, y, z]) - o) + o)
        return np.array(out)


class Gun:
    def __init__(self, id, sight, R=5, seed=1, long_gun=True, length=None, bore=None):
        self.id = id
        self.sight = sight          # height (cm) of the line of sight above the grip
        self.bore = sight - 3.75 if bore is None else bore   # height of the barrel's axis
        self.sight_x = 0.0          # where the sight line is across the gun (launchers aim from the left side)
        self.R = R                  # texture pixels per centimetre
        self.seed = seed
        self.long_gun = long_gun
        self.boxes = []
        self.length_override = length
        self.scoped = False         # aimed view: the gun is not drawn (the scope picture covers the screen)
        self.extra_variants = {}

    # --- drawing helpers ---------------------------------------------------------------------------------------

    def box(self, lo, hi, style, **kw):
        b = Box(lo, hi, style, **kw)
        self.boxes.append(b)
        return b

    def bar(self, z0, z1, y0, y1, w, style, x=0.0, **kw):
        """A block along the length: from z0 to z1 (z1 > z0 not required), y0..y1, w wide, centred at x."""
        return self.box((x - w / 2, y0, min(z0, z1)), (x + w / 2, y1, max(z0, z1)), style, **kw)

    def cyl(self, z0, z1, y, r, style, x=0.0, fine=True, **kw):
        """A round part along the gun (barrel, tube, scope): an octagon of four slabs, or a square when tiny."""
        za, zb = min(z0, z1), max(z0, z1)
        if r < 0.45 or not fine:
            self.box((x - r, y - r, za), (x + r, y + r, zb), style, **kw)
            return
        a = r * 0.4142
        self.box((x - r, y - a, za), (x + r, y + a, zb), style, **kw)
        self.box((x - a, y - r, za), (x + a, y + r, zb), style, **kw)
        for ang in (45, -45):
            self.box((x - r, y - a, za), (x + r, y + a, zb), style, rot=ang, ax='z', origin=(x, y, (za + zb) / 2), **kw)

    def cyl_y(self, x, z, y0, y1, r, style, **kw):
        """A round part standing upright (a scope turret, a knob)."""
        a = r * 0.4142
        self.box((x - r, y0, z - a), (x + r, y1, z + a), style, **kw)
        self.box((x - a, y0, z - r), (x + a, y1, z + r), style, **kw)
        for ang in (45, -45):
            self.box((x - r, y0, z - a), (x + r, y1, z + a), style, rot=ang, ax='y', origin=(x, (y0 + y1) / 2, z), **kw)

    def cyl_x(self, x0, x1, y, z, r, style, **kw):
        """A round part across the gun (a turret knob on the side, a wheel)."""
        a = r * 0.4142
        self.box((x0, y - a, z - r), (x1, y + a, z + r), style, **kw)
        self.box((x0, y - r, z - a), (x1, y + r, z + a), style, **kw)
        for ang in (45, -45):
            self.box((x0, y - a, z - r), (x1, y + a, z + r), style, rot=ang, ax='x', origin=((x0 + x1) / 2, y, z), **kw)

    def slant(self, z_top, z_bottom, y_top, y_bottom, thick_z, w, style, steps=None, x=0.0, **kw):
        """A slanted part (pistol grip, magazine, stock) as a staircase of boxes from (z_top, y_top) to (z_bottom, y_bottom)."""
        h = y_top - y_bottom
        n = steps or max(2, int(abs(h) / 1.6))
        for i in range(n):
            ya = y_top - h * i / n
            yb = y_top - h * (i + 1) / n
            zc = z_top + (z_bottom - z_top) * (i + 0.5) / n
            self.box((x - w / 2, yb, zc - thick_z / 2), (x + w / 2, ya, zc + thick_z / 2), style, edges=False, **kw)

    def slab(self, p0, p1, thick, w, style, x=0.0, ext=0.0, **kw):
        """A straight part between two points of the side view (z, y): a box {thick} across the line, {w} wide,
        turned about x to lie along it (a pistol grip, a stock's edge, a magazine, a bipod leg). ext = extra length
        at both ends (to close the joints of a chain)."""
        (z0, y0), (z1, y1) = p0, p1
        if z1 < z0:
            (z0, y0), (z1, y1) = (z1, y1), (z0, y0)
        dz, dy = z1 - z0, y1 - y0
        L = math.hypot(dz, dy) + 2 * ext
        cz, cy = (z0 + z1) / 2, (y0 + y1) / 2
        ang = math.degrees(math.atan2(-dy, dz))
        ang = round(ang, 3)
        if abs(ang) < 0.05:
            ang = 0
        return self.box((x - w / 2, cy - thick / 2, cz - L / 2), (x + w / 2, cy + thick / 2, cz + L / 2), style,
                        rot=ang, ax='x', origin=(x, cy, cz), **kw)

    def profile(self, top, bottom, w, style, x=0.0, n=None, edge=1.0, inset=0.15, **kw):
        """A flat part between two straight edges of the side view - top = ((z0, y0), (z1, y1)), bottom likewise
        (same z0, z1): strips that stay inside the outline and a thin slab along each edge, so the outline is a
        clean straight line, not a staircase (a rifle stock, a handguard's taper, a butt)."""
        (za, ta), (zb, tb) = top
        (zc, ba), (zd, bb) = bottom
        assert abs(za - zc) < 1e-6 and abs(zb - zd) < 1e-6
        z0, z1 = min(za, zb), max(za, zb)
        topf = lambda z: ta + (tb - ta) * (z - za) / (zb - za)
        botf = lambda z: ba + (bb - ba) * (z - za) / (zb - za)
        n = n or max(2, int((z1 - z0) / 3.0))
        for i in range(n):
            s0 = z0 + (z1 - z0) * i / n
            s1 = z0 + (z1 - z0) * (i + 1) / n
            yt = min(topf(s0), topf(s1)) - inset
            yb = max(botf(s0), botf(s1)) + inset
            if yt > yb:
                self.box((x - w / 2 + 0.02, yb, s0), (x + w / 2 - 0.02, yt, s1), style, edges=False, **kw)
        # Edge slabs, their outer face on the line.
        for (p0, p1, sgn) in (((za, ta), (zb, tb), -1), ((za, ba), (zb, bb), 1)):
            dz, dy = p1[0] - p0[0], p1[1] - p0[1]
            L = math.hypot(dz, dy)
            nz, ny = -dy / L, dz / L          # left normal of the edge
            if ny * sgn < 0:
                nz, ny = -nz, -ny
            off = edge / 2
            q0 = (p0[0] + nz * off, p0[1] + ny * off)
            q1 = (p1[0] + nz * off, p1[1] + ny * off)
            self.slab(q0, q1, edge, w, style, x=x, edges=False, **kw)

    def curve(self, pts, w, style, x=0.0, **kw):
        """A curved part (a banana magazine) through points (z, y_top, y_bottom, thick)."""
        for i in range(len(pts) - 1):
            z0, yt0, yb0, t0 = pts[i]
            z1, yt1, yb1, t1 = pts[i + 1]
            zc = (z0 + z1) / 2
            t = (t0 + t1) / 2
            self.box((x - w / 2, yt1, zc - t / 2), (x + w / 2, yt0, zc + t / 2), style, edges=False, **kw)

    # --- bounds ------------------------------------------------------------------------------------------------

    def bounds(self, boxes=None):
        """Where the gun really reaches (rotated parts counted where they are)."""
        boxes = boxes or self.boxes
        pts = np.concatenate([b.corners() if b.rotated() else np.array([b.lo, b.hi]) for b in boxes])
        return list(pts.min(axis=0)), list(pts.max(axis=0))

    def raw_reach(self):
        """The largest coordinate of any box before its rotation (Minecraft limits from/to to -16..32)."""
        return max(max(abs(v) for v in b.lo + b.hi) for b in self.boxes)

    def length(self):
        lo, hi = self.bounds()
        return hi[2] - lo[2]

    def muzzle(self):
        """How far the barrel reaches in front of the grip (metres)."""
        lo, hi = self.bounds([b for b in self.boxes if 'empty' not in b.tags])
        return -lo[2] / 100.0


# ----------------------------------------------------------------------------------------------------------------
# Atlas and export

def face_dims(b, face, R):
    sx, sy, sz = b.size()
    d = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}[face]
    return max(1, int(round(d[0] * R))), max(1, int(round(d[1] * R)))


def along(face):
    """Where the gun's length runs on this face's texture: True = along u, False = along v, None = end face."""
    if face in ('east', 'west'):
        return True
    if face in ('up', 'down'):
        return False
    return None


def build_atlas(g):
    """Every face gets its own region (painted in gun space). Returns the image and {(box index, face): (u, v, w, h)}."""
    R = g.R
    items = []
    for bi, b in enumerate(g.boxes):
        for face in b.faces:
            w, h = face_dims(b, face, R)
            items.append((bi, face, w, h))
    items.sort(key=lambda t: (-t[3], -t[2]))
    total = sum(t[2] * t[3] for t in items)
    W = 64
    while W * W < total * 1.3 and W < 2048:
        W *= 2
    widest = max(t[2] for t in items)
    while W < widest:
        W *= 2
    x = y = row_h = 0
    uv = {}
    for bi, face, w, h in items:
        if x + w > W:
            y += row_h
            x = 0
            row_h = 0
        uv[(bi, face)] = (x, y, w, h)
        x += w
        row_h = max(row_h, h)
    H = y + row_h
    H = max(16, (H + 15) // 16 * 16)
    img = np.zeros((H, W, 4), dtype=np.uint8)
    painter = Painter(g.seed)
    for (bi, face), (u, v, w, h) in uv.items():
        px = painter.paint(g.boxes[bi], face, w, h, R)
        img[v:v + h, u:u + w, :3] = px.astype(np.uint8)
        img[v:v + h, u:u + w, 3] = 255
    return img, uv, W, H


def element(b, bi, uvpos, W, H, k):
    def m(v):
        return round(8 + v * k, 4)
    f = [m(b.lo[i]) for i in range(3)]
    t = [m(b.hi[i]) for i in range(3)]
    for v in f + t:
        assert -16 <= v <= 32, (b.lo, b.hi, v)
    faces = {}
    for face in b.faces:
        u, v, w, h = uvpos[(bi, face)]
        faces[face] = {'texture': '#t', 'uv': [round(u * 16 / W, 4), round(v * 16 / H, 4), round((u + w) * 16 / W, 4), round((v + h) * 16 / H, 4)]}
    e = {'from': f, 'to': t, 'faces': faces}
    if b.euler is not None:
        o = b.pivot()
        e['rotation'] = {'origin': [m(o[0]), m(o[1]), m(o[2])], 'x': b.euler[0], 'y': b.euler[1], 'z': b.euler[2]}
    elif b.rot:
        o = b.pivot()
        e['rotation'] = {'origin': [m(o[0]), m(o[1]), m(o[2])], 'axis': b.ax, 'angle': b.rot}
    return e


# First-person poses (display transforms, 1/16 block, from the hand's place 0.56 right, 0.52 down, 0.72 ahead of the
# eye). At the hip a long gun is held high and close, the barrel towards the left of the crosshair; a pistol out in
# front. Aimed, the sight line runs AIM_DROP blocks under the eye (the front post just under the crosshair).
HIP = [-5.6, 5.2, 6.4]
HIP_ROT = [3, -5, 4]
PISTOL_HIP = [-5.8, 5.0, 6.0]
PISTOL_ROT = [0, -3, 0]
AIM_DROP = 0.015


def fp_point(display, k, v):
    """Where gun-space point v (cm) is in first person: camera space, blocks (x right, y up, -z ahead)."""
    t = np.array(display['translation'], dtype=np.float64) / 16.0
    r = [math.radians(a) for a in display['rotation']]
    R = rot_matrix('x', display['rotation'][0]) @ rot_matrix('y', display['rotation'][1]) @ rot_matrix('z', display['rotation'][2])
    sc = display['scale'][0]
    p = np.array(v, dtype=np.float64) * k / 16.0
    return np.array([0.56, -0.52, -0.72]) + t + R @ (sc * p)


def export(g, real_length=None):
    """Writes the models, the texture, the item definition and the icon of gun g."""
    img, uvpos, W, H = build_atlas(g)
    Image.fromarray(img, 'RGBA').save(path('textures', 'item', 'gun', g.id + '.png'), optimize=True)
    lo, hi = g.bounds()
    reach = max(max(abs(v) for v in lo), max(abs(v) for v in hi), g.raw_reach())
    k = min(1.0, 23.5 / reach)
    length_cm = g.length_override or (hi[2] - lo[2])
    real = (real_length or length_cm / 100.0)
    scale = round(real * 16 / ((hi[2] - lo[2]) * k), 4)
    def els(pred):
        return [element(b, bi, uvpos, W, H, k) for bi, b in enumerate(g.boxes) if pred(b)]

    textures = {'t': 'airdefense:item/gun/' + g.id, 'particle': 'airdefense:item/gun/' + g.id}
    # First person (1/16 block from the hand's place 0.56 right, 0.52 down, 0.72 ahead of the eye): at the hip the grip
    # comes 0.16 closer in, up and back so the gun looks its real size; aimed, the line of sight runs just under
    # the eye with the grip 0.3 ahead (a pistol is held out at arm's length).
    hip_fp = {'rotation': HIP_ROT if g.long_gun else PISTOL_ROT, 'translation': HIP if g.long_gun else PISTOL_HIP, 'scale': [scale] * 3}
    sight = g.sight * k * scale / 16
    side = g.sight_x * k * scale / 16
    aim_fp = {'rotation': [0, 0, 0], 'translation': [round((-0.56 - side) * 16, 3), round((0.52 - sight - AIM_DROP) * 16, 3),
                                                       6.72 if g.long_gun else 6.4],
              'scale': [scale] * 3}
    if g.scoped:
        aim_fp = {'rotation': [0, 0, 0], 'translation': [0, -40, 0], 'scale': [0.01] * 3}
    tp = {'rotation': [0, 0, 0], 'translation': [0, 0.5, -1.0], 'scale': [scale] * 3}
    # In an item frame: lying across the frame, the whole gun inside it; on the ground: real size, lying flat.
    cz = (lo[2] + hi[2]) / 2 * k
    cy = (lo[1] + hi[1]) / 2 * k
    s_fixed = round(15.0 / ((hi[2] - lo[2]) * k), 4)
    fixed = {'rotation': [0, 90, 0], 'translation': [round(-cz * s_fixed, 3), round(-cy * s_fixed, 3), 0], 'scale': [s_fixed] * 3}
    ground = {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [round(scale * 0.8, 4)] * 3}
    gui = {'rotation': [0, 90, 0], 'translation': [round(-cz * s_fixed, 3), round(-cy * s_fixed, 3), 0], 'scale': [s_fixed] * 3}

    def model(name, fp, pred):
        display = {
            'firstperson_righthand': fp, 'firstperson_lefthand': fp,
            'thirdperson_righthand': tp, 'thirdperson_lefthand': tp,
            'fixed': fixed, 'ground': ground, 'gui': gui,
            'head': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [scale] * 3},
        }
        write_json({'textures': textures, 'elements': els(pred), 'display': display}, 'models', 'item', name + '.json')

    loaded = lambda b: 'empty' not in b.tags
    empty = lambda b: 'loaded' not in b.tags
    has_empty = any(('loaded' in b.tags) or ('empty' in b.tags) for b in g.boxes)
    model(g.id, hip_fp, loaded)
    model(g.id + '_aim', aim_fp, lambda b: loaded(b) and b.aim)
    if has_empty:
        model(g.id + '_empty', hip_fp, empty)
        model(g.id + '_aim_empty', aim_fp, lambda b: empty(b) and b.aim)
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'airdefense:item/' + g.id}}, 'models', 'item', g.id + '_icon.json')

    def held(suffix):
        return {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                'on_true': {'type': 'minecraft:model', 'model': 'airdefense:item/' + g.id + '_aim' + suffix},
                'on_false': {'type': 'minecraft:model', 'model': 'airdefense:item/' + g.id + suffix}}
    in_hand = held('')
    if has_empty:
        in_hand = {'type': 'minecraft:condition', 'property': 'minecraft:has_component', 'component': 'airdefense:ammo',
                   'on_true': held(''), 'on_false': held('_empty')}
    write_json({'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [
            {'when': ['gui'], 'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + g.id + '_icon'}},
            {'when': ['ground', 'fixed'], 'model': {'type': 'minecraft:model', 'model': 'airdefense:item/' + g.id}},
        ],
        'fallback': in_hand,
    }}, 'items', g.id + '.json')
    icon(g, img, uvpos)
    n = len(g.boxes)
    tip = (0.0, g.bore, -g.muzzle() * 100.0)
    hip_m = fp_point(hip_fp, k, tip)
    aim_m = fp_point(aim_fp, k, tip) if not g.scoped else np.array([0.0, -0.06, -0.9])
    return dict(id=g.id, boxes=n, atlas=(W, H), scale=scale, k=k, muzzle=g.muzzle(), sight=g.sight, hip=hip_m, aim=aim_m)


# ----------------------------------------------------------------------------------------------------------------
# Icon: the right side of the gun, painted, barrel up and to the right

def render_side(g, img, uvpos, size=32, ss=4, angle_deg=None, width=None, height=None, empty=False):
    """The right side of the gun, painted, as an RGBA array (barrel to the right, raised by angle_deg)."""
    boxes = [b for b in g.boxes if ('loaded' not in b.tags if empty else 'empty' not in b.tags)]
    lo, hi = g.bounds(boxes)
    angle = math.radians((32 if g.long_gun else 0) if angle_deg is None else angle_deg)
    W = width or size
    Hh = height or size
    zmin, zmax, ymin, ymax = lo[2], hi[2], lo[1], hi[1]
    length = zmax - zmin
    height_cm = ymax - ymin
    ca, sa = math.cos(angle), math.sin(angle)
    w = length * ca + height_cm * sa
    h = length * sa + height_cm * ca
    s = min((W - 3) / w, (Hh - 3) / h)
    zc = (zmin + zmax) / 2
    yc = (ymin + ymax) / 2
    NW, NH = W * ss, Hh * ss
    acc = np.zeros((NH, NW, 4))
    vv, uu = np.mgrid[0:NH, 0:NW]
    dx = (uu + 0.5) / ss - W / 2
    dy = (vv + 0.5) / ss - Hh / 2
    al = dx * ca - dy * sa
    up = -dx * sa - dy * ca
    Z = zc - al / s
    Y = yc + up / s
    index = {id(b): i for i, b in enumerate(g.boxes)}
    for b in sorted(boxes, key=lambda b: b.corners()[:, 0].max() if b.rotated() else b.hi[0]):
        if 'east' not in b.faces:
            continue
        u0, v0, fw, fh = uvpos[(index[id(b)], 'east')]
        tex = img[v0:v0 + fh, u0:u0 + fw, :3].astype(np.float64)
        ylo, yhi = b.lo[1], b.hi[1]
        zlo, zhi = b.lo[2], b.hi[2]
        Zl, Yl = Z, Y
        if b.euler is None and b.rot and b.ax == 'z':
            c = b.center()
            r = (b.hi[0] - b.lo[0]) / 2
            ylo, yhi = c[1] - r, c[1] + r
        elif b.euler is None and b.rot and b.ax == 'x':
            # Back into the box's own frame: rotate the sample point the other way round the pivot.
            o = b.pivot()
            a = math.radians(-b.rot)
            ca_, sa_ = math.cos(a), math.sin(a)
            dy_, dz_ = Y - o[1], Z - o[2]
            Yl = dy_ * ca_ - dz_ * sa_ + o[1]
            Zl = dy_ * sa_ + dz_ * ca_ + o[2]
        elif b.rotated():
            cs = b.corners()
            zlo, zhi = cs[:, 2].min(), cs[:, 2].max()
            ylo, yhi = cs[:, 1].min(), cs[:, 1].max()
        inside = (Zl >= zlo) & (Zl <= zhi) & (Yl >= ylo) & (Yl <= yhi)
        if not inside.any():
            continue
        tu = np.clip(((zhi - Zl) / max(1e-6, zhi - zlo) * fw).astype(int), 0, fw - 1)
        tv = np.clip(((yhi - Yl) / max(1e-6, yhi - ylo) * fh).astype(int), 0, fh - 1)
        col = tex[tv, tu]
        acc[inside, :3] = col[inside] * 1.12 + 6
        acc[inside, 3] = 255
    small = acc.reshape(Hh, ss, W, ss, 4).mean(axis=(1, 3))
    return small


def finish_icon(small):
    H, W = small.shape[:2]
    alpha = small[..., 3] / 255.0
    rgbm = np.where(alpha[..., None] > 0, small[..., :3] / np.maximum(alpha[..., None], 1e-6), 0)
    out = np.zeros((H, W, 4), dtype=np.uint8)
    solid = alpha > 0.45
    out[solid, :3] = np.clip(rgbm[solid], 0, 255)
    out[solid, 3] = 255
    a = solid
    top = a & ~np.vstack([np.zeros((1, W), bool), a[:-1]])
    bot = a & ~np.vstack([a[1:], np.zeros((1, W), bool)])
    o = out.astype(np.float64)
    o[top, :3] = np.clip(o[top, :3] * 1.35 + 14, 0, 255)
    o[bot, :3] *= 0.7
    out = o.astype(np.uint8)
    ring = np.zeros_like(a)
    ring[1:] |= a[:-1]
    ring[:-1] |= a[1:]
    ring[:, 1:] |= a[:, :-1]
    ring[:, :-1] |= a[:, 1:]
    edge = ring & ~a
    out[edge] = (16, 16, 18, 255)
    return out


def icon(g, img, uvpos, size=32, ss=4):
    out = finish_icon(render_side(g, img, uvpos, size, ss))
    Image.fromarray(out, 'RGBA').save(path('textures', 'item', g.id + '.png'))


def preview(g, file, width=1200, height=420):
    img, uvpos, W, H = build_atlas(g)
    side = render_side(g, img, uvpos, ss=2, angle_deg=0, width=width, height=height)
    bg = np.zeros((height, width, 3)) + np.array([150, 165, 180])
    a = side[..., 3:4] / 255.0
    out = np.clip(bg * (1 - a) + side[..., :3], 0, 255).astype(np.uint8)
    Image.fromarray(out, 'RGB').save(file)
