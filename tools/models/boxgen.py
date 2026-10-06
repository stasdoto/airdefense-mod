"""
Box-model generator for the mod's vehicles (and anything else built from boxes).

A model is a tree of parts; each part has a pivot and boxes. Everything is written in metres in vehicle space:
x to the right, y up, z forward (the nose), the ground at y = 0. Boxes are given where they are at rest
(before any part turns); a part's fixed rotation (degrees, Minecraft's sense: +x raises the front, +y turns the
front to the right) is applied around its pivot.

Output: a Java LayerDefinition per model (1 block = 1 m = 16 px), the part paths, the geometry record the
server uses, the texture atlas (each box painted by its style) and a burnt-out "wreck" copy, and a 32x32 item icon.
"""
import math
import os
import random

import numpy as np
from PIL import Image
from scipy.ndimage import zoom as nd_zoom

PX = 16

# ----------------------------------------------------------------------------------------------------------------
# Styles


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


STYLES = {
    # Woodland camouflage (NATO / Ukrainian vehicles).
    'camo': dict(kind='camo', base=0x4A5A32, c2=0x5E4A30, c3=0x22281C),
    # Russian / Soviet protective green.
    'rgreen': dict(kind='plain', base=0x56633A),
    # Ukrainian digital-ish green (Soviet vehicles in Ukrainian service).
    'ugreen': dict(kind='camo', base=0x55613A, c2=0x3C4A2A, c3=0x6B6C47, blob=3),
    # Bundeswehr / NATO plain dark green.
    'nato': dict(kind='plain', base=0x4A5238),
    'olive': dict(kind='plain', base=0x5B6236),
    'tan': dict(kind='camo', base=0xB59A6A, c2=0x8C7650, c3=0xC9B486),
    'sand': dict(kind='plain', base=0xB8A27A),
    'grey': dict(kind='plain', base=0x7C8186),
    'lgrey': dict(kind='plain', base=0xA7ADB2),
    'dgrey': dict(kind='plain', base=0x4A4E52),
    'dark': dict(kind='plain', base=0x2C2F31),
    'black': dict(kind='plain', base=0x18191A),
    'white': dict(kind='plain', base=0xD8DCDE),
    'steel': dict(kind='plain', base=0x8A8F94),
    'rust': dict(kind='plain', base=0x6E4A2E),
    'canvas': dict(kind='plain', base=0x6E6B4C),
    'glass': dict(kind='glass', base=0x2A3946),
    'light': dict(kind='plain', base=0xF2E7B0, flat=True),
    'redlight': dict(kind='plain', base=0xB82A20, flat=True),
    'orange': dict(kind='plain', base=0xD8792A),
    'tire': dict(kind='tire', base=0x1E1F20),
    'track': dict(kind='track', base=0x2E2C29),
    # Wheel sidewall: round, the tyre ring and the hub in the given colour.
    'wheel': dict(kind='wheel', base=0x1E1F20, hub=0x55613A),
    'wheel_tan': dict(kind='wheel', base=0x1E1F20, hub=0x9E8660),
    'wheel_grey': dict(kind='wheel', base=0x1E1F20, hub=0x6C7176),
    'roadwheel': dict(kind='wheel', base=0x26282A, hub=0x4E5A36, ring=0.22),
    # Radar faces.
    'aesa': dict(kind='grid', base=0x3A3F44, line=0x565D63, step=2),
    'aesa_light': dict(kind='grid', base=0x8C9196, line=0xA6ABB0, step=2),
    'radome': dict(kind='plain', base=0xC9CCC4),
    # Open lattice (see-through): reflectors, mesh antennas, fences.
    'mesh': dict(kind='mesh', base=0x6F7468, step=3),
    'mesh_dark': dict(kind='mesh', base=0x3E4237, step=3),
    'mesh_fine': dict(kind='mesh', base=0x80857A, step=2),
}


def mul(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


class Painter:
    def __init__(self, seed):
        self.rng = np.random.default_rng(seed)

    def noise_field(self, w, h, cell):
        gw = max(2, w // cell + 2)
        gh = max(2, h // cell + 2)
        g = self.rng.random((gh, gw))
        z = nd_zoom(g, (h / gh * 1.0 + 0.0001, w / gw * 1.0 + 0.0001), order=1)
        z = np.pad(z, ((0, max(0, h - z.shape[0])), (0, max(0, w - z.shape[1]))), mode='edge')
        return z[:h, :w]

    def face(self, img, x0, y0, w, h, style, shade, face):
        """Paints one face rectangle (integer pixels) of the atlas."""
        if w <= 0 or h <= 0:
            return
        st = STYLES[style]
        kind = st['kind']
        base = rgb(st['base'])
        flat = st.get('flat', False)
        k = 1.0 if flat else shade
        region = np.zeros((h, w, 4), dtype=np.float64)
        region[..., 3] = 255
        if kind in ('plain', 'glass'):
            region[..., :3] = mul(base, k)
            if not flat:
                region[..., :3] += self.rng.normal(0, 3.0, (h, w, 1))
            if kind == 'glass' and w >= 3 and h >= 3:
                # A light streak across the window.
                for i in range(w):
                    j = int(i * 0.6) + h // 4
                    if 0 <= j < h:
                        region[j, i, :3] = mul(base, 1.6)
        elif kind == 'camo':
            region[..., :3] = mul(base, k)
            cell = st.get('blob', 7)
            f2 = self.noise_field(w, h, cell)
            f3 = self.noise_field(w, h, cell)
            m2 = f2 > 0.62
            m3 = f3 > 0.70
            region[m2, :3] = mul(rgb(st['c2']), k)
            region[m3, :3] = mul(rgb(st['c3']), k)
            region[..., :3] += self.rng.normal(0, 2.5, (h, w, 1))
        elif kind == 'grid':
            region[..., :3] = mul(base, k)
            step = st.get('step', 2)
            line = mul(rgb(st['line']), k)
            region[::step, :, :3] = line
            region[:, ::step, :3] = line
        elif kind == 'mesh':
            region[..., 3] = 0
            step = st.get('step', 3)
            c = mul(base, k)
            region[::step, :, :3] = c
            region[::step, :, 3] = 255
            region[:, ::step, :3] = c
            region[:, ::step, 3] = 255
            region[0, :, :3] = c
            region[-1, :, :3] = c
            region[:, 0, :3] = c
            region[:, -1, :3] = c
            region[0, :, 3] = region[-1, :, 3] = 255
            region[:, 0, 3] = region[:, -1, 3] = 255
        elif kind == 'tire':
            region[..., :3] = mul(base, k)
            region[::3, :, :3] = mul(base, k * 0.6)
            region[..., :3] += self.rng.normal(0, 2.0, (h, w, 1))
        elif kind == 'track':
            region[..., :3] = mul(base, k)
            region[::3, :, :3] = mul(base, k * 0.55)
            region[1::3, :, :3] = mul(base, k * 1.25)
            region[..., :3] += self.rng.normal(0, 2.0, (h, w, 1))
        elif kind == 'wheel':
            # Round wheel seen from the side: transparent outside the circle.
            cx = (w - 1) / 2.0
            cy = (h - 1) / 2.0
            r = min(w, h) / 2.0
            yy, xx = np.mgrid[0:h, 0:w]
            d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
            ring = st.get('ring', 0.32)
            hub = rgb(st['hub'])
            region[..., :3] = mul(base, k)
            region[d > r, 3] = 0
            inner = d <= r * (1 - ring)
            region[inner, :3] = mul(hub, k)
            region[d <= r * 0.18, :3] = mul(hub, k * 0.6)
            # Bolts.
            for a in range(6):
                bx = int(round(cx + math.cos(a * math.pi / 3) * r * 0.42))
                by = int(round(cy + math.sin(a * math.pi / 3) * r * 0.42))
                if 0 <= bx < w and 0 <= by < h:
                    region[by, bx, :3] = mul(hub, k * 0.55)
            # Sidewall line.
            region[(d > r * (1 - ring)) & (d < r * (1 - ring) + 1.0), :3] = mul(base, k * 0.6)
        else:
            raise ValueError(kind)
        if kind in ('plain', 'camo', 'glass') and not flat and w >= 4 and h >= 4:
            region[0, :, :3] *= 0.78
            region[-1, :, :3] *= 0.72
            region[:, 0, :3] *= 0.8
            region[:, -1, :3] *= 0.8
        img[y0:y0 + h, x0:x0 + w] = np.clip(region, 0, 255)


# ----------------------------------------------------------------------------------------------------------------
# Model tree

FACE_DIR = {'top': 'DOWN', 'bottom': 'UP', 'front': 'SOUTH', 'back': 'NORTH', 'right': 'EAST', 'left': 'WEST'}
ALL_FACES = ('top', 'bottom', 'front', 'back', 'right', 'left')


def f(v):
    s = ('%.4f' % v).rstrip('0').rstrip('.')
    if s in ('-0', ''):
        s = '0'
    return s + 'F'


class Box:
    def __init__(self, a, b, style, faces=None, sides=None):
        self.lo = tuple(min(a[i], b[i]) for i in range(3))
        self.hi = tuple(max(a[i], b[i]) for i in range(3))
        self.style = style
        self.faces = tuple(faces) if faces else ALL_FACES
        # Per-face style overrides, e.g. {'front': 'glass'}.
        self.sides = dict(sides or {})

    def px_size(self):
        return tuple(max(1, int(round((self.hi[i] - self.lo[i]) * PX))) for i in range(3))

    def key(self):
        return (self.px_size(), self.style, self.faces, tuple(sorted(self.sides.items())))


class Part:
    def __init__(self, model, name, pivot, rot, parent):
        self.model = model
        self.name = name
        self.pivot = tuple(pivot)
        self.rot = tuple(rot)
        self.parent = parent
        self.boxes = []
        self.children = []

    def box(self, a, b, style=None, faces=None, sides=None):
        """Box between corners a and b (metres, vehicle space at rest)."""
        self.boxes.append(Box(a, b, style or self.model.paint, faces, sides))
        return self

    def cbox(self, c, size, style=None, faces=None, sides=None):
        """Box by centre and size."""
        a = tuple(c[i] - size[i] / 2 for i in range(3))
        b = tuple(c[i] + size[i] / 2 for i in range(3))
        return self.box(a, b, style, faces, sides)

    def bbox(self, x, z, y0, size, style=None, faces=None, sides=None):
        """Box standing on height y0, centred at (x, z) with size (sx, sy, sz)."""
        sx, sy, sz = size
        return self.box((x - sx / 2, y0, z - sz / 2), (x + sx / 2, y0 + sy, z + sz / 2), style, faces, sides)

    def part(self, name, pivot=None, rot=(0, 0, 0)):
        return self.model.part(name, pivot if pivot is not None else self.pivot, rot, self)

    def path(self):
        p = self
        out = []
        while p is not None:
            out.append(p.name)
            p = p.parent
        return '/'.join(reversed(out))


class Model:
    def __init__(self, id, paint='camo', tex_width=1024, seed=1):
        self.id = id
        self.paint = paint
        self.tex_width = tex_width
        self.seed = seed
        self.parts = []
        self.roots = []
        # Geometry for the server.
        self.seats = []
        self.wheels = []
        self.turret = None
        self.turret_pivot = (0, 0, 0)
        self.elevator = None
        self.elevator_pivot = (0, 0, 0)
        self.deploy_elevation = 0.0
        self.fixed_elevation = 0.0
        self.rails = []
        self.spinner = None
        self.open_parts = []
        self.camera = 12.0
        self.wheelbase = 0.0
        self.tracked = False
        # Width of the chassis (when antennas or wings stick out further than the body).
        self.width = None

    def part(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
        p = Part(self, name, pivot, rot, parent)
        self.parts.append(p)
        if parent is None:
            self.roots.append(p)
        else:
            parent.children.append(p)
        return p

    def seat(self, role, x, y, z):
        self.seats.append((role, x, y, z))

    def wheel(self, name, x, y, z, r, width, steer=False, side='wheel', tread='tire', parent=None):
        """A wheel turning about the x axis, pivot at its axle (x, y, z)."""
        p = self.part(name, (x, y, z), (0, 0, 0), parent)
        hw = width / 2
        # Octagon from three crossed slabs, tread faces only, plus the round sidewalls.
        a = r * 0.42
        p.box((x - hw, y - a, z - r), (x + hw, y + a, z + r), tread, faces=('top', 'bottom', 'front', 'back'))
        p.box((x - hw, y - r, z - a), (x + hw, y + r, z + a), tread, faces=('top', 'bottom', 'front', 'back'))
        p.box((x + hw, y - r, z - r), (x + hw + 0.04, y + r, z + r), side, faces=('right', 'left'))
        p.box((x - hw - 0.04, y - r, z - r), (x - hw, y + r, z + r), side, faces=('right', 'left'))
        for k, ang in enumerate((45, -45)):
            d = p.part(name + '_d' + str(k + 1), (x, y, z), (ang, 0, 0))
            d.box((x - hw, y - a, z - r), (x + hw, y + a, z + r), tread, faces=('top', 'bottom', 'front', 'back'))
        self.wheels.append((name, r, steer))
        return p

    # --- output -------------------------------------------------------------------------------------------------

    def bounds(self):
        lo = [1e9] * 3
        hi = [-1e9] * 3
        for p in self.parts:
            for b in p.boxes:
                for i in range(3):
                    lo[i] = min(lo[i], b.lo[i])
                    hi[i] = max(hi[i], b.hi[i])
        return lo, hi

    def layout(self):
        """Places every distinct box's texture region on the atlas (shelf packing)."""
        regions = {}
        for p in self.parts:
            for b in p.boxes:
                k = b.key()
                if k not in regions:
                    w, h, d = b.px_size()
                    regions[k] = (2 * (w + d), h + d, b)
        order = sorted(regions.items(), key=lambda kv: (-kv[1][1], -kv[1][0]))
        W = self.tex_width
        x = y = row_h = 0
        uv = {}
        for k, (rw, rh, b) in order:
            if rw > W:
                raise ValueError('%s: box too big for the atlas: %s' % (self.id, k))
            if x + rw > W:
                y += row_h
                x = 0
                row_h = 0
            uv[k] = (x, y)
            x += rw
            row_h = max(row_h, rh)
        H = y + row_h
        H = (H + 15) // 16 * 16
        return uv, regions, W, H

    def paint_atlas(self, uv, regions, W, H):
        img = np.zeros((H, W, 4), dtype=np.uint8)
        painter = Painter(self.seed)
        for k, (u, v) in uv.items():
            b = regions[k][2]
            w, h, d = b.px_size()
            rects = {
                'top': (u + d, v, w, d),
                'bottom': (u + d + w, v, w, d),
                'left': (u, v + d, d, h),
                'back': (u + d, v + d, w, h),
                'right': (u + d + w, v + d, d, h),
                'front': (u + 2 * d + w, v + d, w, h),
            }
            shade = {'top': 1.1, 'bottom': 0.7, 'left': 0.92, 'right': 0.92, 'front': 1.0, 'back': 0.95}
            for face in b.faces:
                x0, y0, fw, fh = rects[face]
                painter.face(img, x0, y0, fw, fh, b.sides.get(face, b.style), shade[face], face)
        return img

    def java_layer(self, uv, W, H):
        lines = []
        lines.append('\tpublic static LayerDefinition %s() {' % self.id)
        lines.append('\t\tMeshDefinition mesh = new MeshDefinition();')
        lines.append('\t\tPartDefinition root = mesh.getRoot();')
        names = {}
        counter = [0]

        def emit(p, parent_var, parent_pivot):
            var = 'p%d' % counter[0]
            counter[0] += 1
            names[p] = var
            px, py, pz = p.pivot
            ox = (px - parent_pivot[0]) * PX
            oy = -(py - parent_pivot[1]) * PX
            oz = (pz - parent_pivot[2]) * PX
            lines.append('\t\tPartDefinition %s = %s.addOrReplaceChild("%s", CubeListBuilder.create()' % (var, parent_var, p.name))
            for b in p.boxes:
                u, v = uv[b.key()]
                w, h, d = b.px_size()
                # Box min corner relative to the pivot, Minecraft space (y down).
                x0 = (b.lo[0] - px) * PX
                y0 = -(b.hi[1] - py) * PX
                z0 = (b.lo[2] - pz) * PX
                faces = ''
                if set(b.faces) != set(ALL_FACES):
                    faces = ', EnumSet.of(%s)' % ', '.join('Direction.' + FACE_DIR[x] for x in b.faces)
                lines.append('\t\t\t\t.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s%s)' % (u, v, f(x0), f(y0), f(z0), f(w), f(h), f(d), faces))
            rx, ry, rz = (math.radians(a) for a in p.rot)
            lines.append('\t\t\t\t, PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));' % (f(ox), f(oy), f(oz), f(rx), f(ry), f(rz)))
            for c in p.children:
                emit(c, var, p.pivot)

        for r in self.roots:
            emit(r, 'root', (0, 0, 0))
        lines.append('\t\treturn LayerDefinition.create(mesh, %d, %d);' % (W, H))
        lines.append('\t}')
        lines.append('')
        lines.append('\tpublic static Map<String, String> %sPaths() {' % self.id)
        lines.append('\t\treturn Map.ofEntries(')
        ents = ['\t\t\t\tMap.entry("%s", "%s")' % (p.name, p.path()) for p in self.parts]
        lines.append(',\n'.join(ents))
        lines.append('\t\t);')
        lines.append('\t}')
        return '\n'.join(lines)

    def java_geometry(self, W, H):
        lo, hi = self.bounds()
        length = hi[2] - lo[2]
        width = self.width or (hi[0] - lo[0])
        height = hi[1]
        seats = ', '.join('new Seat("%s", %s, %s, %s)' % (r, f(x), f(y), f(z)) for r, x, y, z in self.seats)
        wheels = ', '.join('new Wheel("%s", %s, %s)' % (n, f(r), 'true' if s else 'false') for n, r, s in self.wheels)
        rails = ', '.join('new Rail("%s", %s, %s, %s)' % (n, f(x), f(y), f(z)) for n, x, y, z in self.rails)
        tp = self.turret_pivot
        ep = self.elevator_pivot
        opens = ', '.join('"%s"' % n for n in self.open_parts)

        def s(v):
            return 'null' if v is None else '"%s"' % v
        return ('\tpublic static final Geometry %s = new Geometry("%s", %s, %s, %s, %s, %s,\n'
                '\t\t\tnew Seat[]{%s},\n'
                '\t\t\tnew Wheel[]{%s},\n'
                '\t\t\t%s, new float[]{%s, %s, %s}, %s, new float[]{%s, %s, %s},\n'
                '\t\t\t%s, %s, new Rail[]{%s}, %s, new String[]{%s}, %s,\n'
                '\t\t\t%d, %d);\n') % (
            self.id.upper(), self.id, f(length), f(width), f(height), f(self.wheelbase), 'true' if self.tracked else 'false',
            seats, wheels, s(self.turret), f(tp[0]), f(tp[1]), f(tp[2]), s(self.elevator), f(ep[0]), f(ep[1]), f(ep[2]),
            f(self.deploy_elevation), f(self.fixed_elevation), rails, s(self.spinner), opens, f(self.camera), W, H)

    def icon(self, path):
        """A 32x32 side view (from the right), for the item."""
        lo, hi = self.bounds()
        length = hi[2] - lo[2]
        height = hi[1] - min(0, lo[1])
        size = 32
        scale = (size - 4) / max(length, height * 1.4)
        img = np.zeros((size, size, 4), dtype=np.uint8)
        boxes = []
        for p in self.parts:
            for b in p.boxes:
                boxes.append(b)
        boxes.sort(key=lambda b: b.hi[0])
        ox = (size - length * scale) / 2
        oy = size - 3
        for b in boxes:
            st = STYLES[b.sides.get('right', b.style)]
            if st['kind'] == 'mesh':
                c = rgb(st['base'])
            elif st['kind'] == 'wheel':
                c = rgb(st['base'])
            else:
                c = rgb(st['base'])
            z0 = int(math.floor(ox + (b.lo[2] - lo[2]) * scale))
            z1 = int(math.ceil(ox + (b.hi[2] - lo[2]) * scale))
            y0 = int(math.floor(oy - b.hi[1] * scale))
            y1 = int(math.ceil(oy - b.lo[1] * scale))
            # Nose to the right.
            xa, xb = size - z1, size - z0
            xa, xb = max(0, xa), min(size, xb)
            y0, y1 = max(0, y0), min(size, y1)
            if xb > xa and y1 > y0:
                img[y0:y1, xa:xb, :3] = c
                img[y0:y1, xa:xb, 3] = 255
        # Outline.
        a = img[..., 3] > 0
        out = np.zeros_like(a)
        out[1:, :] |= a[:-1, :]
        out[:-1, :] |= a[1:, :]
        out[:, 1:] |= a[:, :-1]
        out[:, :-1] |= a[:, 1:]
        edge = out & ~a
        img[edge] = (20, 22, 20, 255)
        # A little shading: lighter top half.
        Image.fromarray(img, 'RGBA').save(path)


def wreck(img, seed):
    rng = np.random.default_rng(seed)
    out = img.astype(np.float64).copy()
    h, w = img.shape[:2]
    lum = out[..., :3].mean(axis=2, keepdims=True)
    char = np.concatenate([lum * 0.28 + 8, lum * 0.25 + 6, lum * 0.22 + 5], axis=2)
    out[..., :3] = char + rng.normal(0, 4, (h, w, 1))
    # Rust patches.
    g = rng.random((h // 6 + 2, w // 6 + 2))
    z = nd_zoom(g, (h / g.shape[0] + 1e-4, w / g.shape[1] + 1e-4), order=1)[:h, :w]
    m = z > 0.72
    out[m, 0] = out[m, 0] * 0.6 + 70
    out[m, 1] = out[m, 1] * 0.6 + 38
    out[m, 2] = out[m, 2] * 0.6 + 20
    return np.clip(out, 0, 255).astype(np.uint8)


def build(models, java_main, java_client, tex_dir, item_dir, pkg_main, pkg_client):
    layers = []
    geoms = []
    for m in models:
        uv, regions, W, H = m.layout()
        img = m.paint_atlas(uv, regions, W, H)
        Image.fromarray(img, 'RGBA').save(os.path.join(tex_dir, m.id + '.png'), optimize=True)
        Image.fromarray(wreck(img, m.seed + 99), 'RGBA').save(os.path.join(tex_dir, m.id + '_wreck.png'), optimize=True)
        if item_dir:
            m.icon(os.path.join(item_dir, m.id + '.png'))
        layers.append(m.java_layer(uv, W, H))
        geoms.append(m.java_geometry(W, H))
        print('%-12s parts %3d boxes %4d atlas %dx%d' % (m.id, len(m.parts), sum(len(p.boxes) for p in m.parts), W, H))
    with open(java_client, 'w') as out:
        out.write('package %s;\n\n' % pkg_client)
        out.write('import java.util.EnumSet;\nimport java.util.Map;\n\n')
        out.write('import net.minecraft.client.model.geom.PartPose;\n')
        out.write('import net.minecraft.client.model.geom.builders.CubeListBuilder;\n')
        out.write('import net.minecraft.client.model.geom.builders.LayerDefinition;\n')
        out.write('import net.minecraft.client.model.geom.builders.MeshDefinition;\n')
        out.write('import net.minecraft.client.model.geom.builders.PartDefinition;\n')
        out.write('import net.minecraft.core.Direction;\n\n')
        out.write('/** GENERATED by tools/models/build.py - do not edit. Real-size models (1 block = 1 m). */\n')
        out.write('@SuppressWarnings("unused")\npublic final class GenModels {\n\tprivate GenModels() {\n\t}\n\n')
        out.write('\tpublic static LayerDefinition layer(String id) {\n\t\treturn switch (id) {\n')
        for m in models:
            out.write('\t\t\tcase "%s" -> %s();\n' % (m.id, m.id))
        out.write('\t\t\tdefault -> throw new IllegalArgumentException(id);\n\t\t};\n\t}\n\n')
        out.write('\tpublic static Map<String, String> paths(String id) {\n\t\treturn switch (id) {\n')
        for m in models:
            out.write('\t\t\tcase "%s" -> %sPaths();\n' % (m.id, m.id))
        out.write('\t\t\tdefault -> throw new IllegalArgumentException(id);\n\t\t};\n\t}\n\n')
        out.write('\n\n'.join(layers))
        out.write('\n}\n')
    with open(java_main, 'w') as out:
        out.write('package %s;\n\n' % pkg_main)
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Geometry;\n')
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Rail;\n')
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Seat;\n')
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Wheel;\n\n')
        out.write('/** GENERATED by tools/models/build.py - do not edit. Geometry of the generated vehicles. */\n')
        out.write('@SuppressWarnings("unused")\npublic final class GenGeometry {\n\tprivate GenGeometry() {\n\t}\n\n')
        out.write('\n'.join(geoms))
        out.write('}\n')
