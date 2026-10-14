"""
2.0: bakes a mesh model: smooth normals, a patch of texture for every surface (charts), the texture painted texel by
texel in 3D (materials.py) with ambient occlusion from a voxel copy of the model, and the binary mesh the client reads.

Binary format (big-endian, read by client.vehicle.MeshLoader):
  'ADM2' int version=1, int texW, int texH, byte flags (1 = tinted), short parts
  per part: UTF name, short parent, float pivot x y z, float rot x y z (degrees), float spin, byte steer, byte track,
            byte layers; per layer: byte layer (0 solid, 1 glass, 2 cutout), float near (0 = always),
            float scale (metres per unit of the positions), float reserved (0), int verts, int quads,
            verts: short x y z (relative to the pivot, vehicle space), byte nx ny nz, short u v (0..65535),
                   int argb, byte flags (1 tint, 2 track belt)
            quads: 4 x (short or int when verts > 65535) indices
"""
import math
import struct

import numpy as np

import materials
from core import face_normal, normalize

LAYERS = {'solid': 0, 'glass': 1, 'cutout': 2}
# Texels per metre, relative to the model's density, for materials with fine detail.
DENSITY = {'plate': 7.0, 'gauges': 5.0, 'headlight': 3.0, 'headlight_box': 3.0, 'taillight': 2.5, 'indicator': 2.0, 'grille': 2.0,
           'grille_mesh': 3.0, 'screen': 3.0, 'tyre': 1.3, 'tyre_wall': 1.0, 'seat': 0.6, 'carpet': 0.4, 'interior': 0.6,
           'rim_dark': 0.6, 'dash': 0.8, 'chrome': 1.2}
PAD = 1


class Vert:
    __slots__ = ()


def _smooth_normals(geo):
    """Corner normals for a smoothing group: averaged over the faces meeting at a corner within the group's angle."""
    cs = math.cos(math.radians(geo.smooth)) if geo.smooth > 0 else 2.0
    fn = [face_normal(p.v) for p in geo.polys]
    area = []
    for p in geo.polys:
        a = 0.0
        for i in range(1, len(p.v) - 1):
            a += np.linalg.norm(np.cross(p.v[i] - p.v[0], p.v[i + 1] - p.v[0])) / 2
        area.append(max(a, 1e-9))
    corners = {}
    for pi, p in enumerate(geo.polys):
        if p.n is not None:
            continue
        for ci, v in enumerate(p.v):
            key = (round(v[0], 5), round(v[1], 5), round(v[2], 5))
            corners.setdefault(key, []).append(pi)
    out = []
    for pi, p in enumerate(geo.polys):
        if p.n is not None:
            out.append(normalize(p.n))
            continue
        ns = []
        for v in p.v:
            key = (round(v[0], 5), round(v[1], 5), round(v[2], 5))
            acc = np.zeros(3)
            for qi in corners.get(key, (pi,)):
                if qi == pi or np.dot(fn[qi], fn[pi]) > cs:
                    acc += fn[qi] * area[qi]
            if np.linalg.norm(acc) < 1e-12:
                acc = fn[pi]
            ns.append(normalize(acc))
        out.append(np.array(ns))
    return out, fn


class Flat:
    """The model as a flat list of polygons, with everything the baking needs."""

    def __init__(self, model):
        self.model = model
        self.polys = []  # dicts: part, geo, mat, v (local, authoring frame), n, uv, w (rest world), wn, fn, layer
        for pi, part in enumerate(model.parts):
            R, t = part.rest_matrix()
            for gi, geo in enumerate(part.geos):
                ns, fns = _smooth_normals(geo)
                for p, n, fnorm in zip(geo.polys, ns, fns):
                    k = len(p.v)
                    uv = p.uv if p.uv is not None else np.zeros((k, 2))
                    w = p.v @ R.T + t
                    wn = normalize(n @ R.T)
                    self.polys.append(dict(part=pi, geo=(pi, gi), mat=p.mat, v=p.v, n=n, uv=uv, w=w, wn=wn, fn=fnorm,
                                           wfn=normalize(fnorm @ R.T), layer=materials.layer_of(p.mat)))


# ---------------------------------------------------------------------------------------------------------------
# Ambient occlusion from a voxel copy of the model


class Occluder:
    def __init__(self, flat, ground=True):
        ws = [p['w'] for p in flat.polys if p['layer'] != 'glass']
        allv = np.concatenate(ws) if ws else np.zeros((1, 3))
        lo = allv.min(axis=0) - 0.4
        hi = allv.max(axis=0) + 0.4
        size = float((hi - lo).max())
        self.vs = max(0.025, size / 260)
        self.lo = lo
        self.dims = np.ceil((hi - lo) / self.vs).astype(int) + 1
        self.grid = np.zeros(self.dims, dtype=bool)
        self.ground = ground
        self.reach = float(np.clip(size * 0.12, 0.5, 2.5))
        step = self.vs * 0.6
        for p in flat.polys:
            if p['layer'] == 'glass':
                continue
            w = p['w']
            for i in range(1, len(w) - 1):
                a, b, c = w[0], w[i], w[i + 1]
                emax = max(np.linalg.norm(b - a), np.linalg.norm(c - a), np.linalg.norm(c - b))
                m = int(math.ceil(emax / step)) + 1
                ii, jj = np.meshgrid(np.arange(m + 1), np.arange(m + 1), indexing='ij')
                keep = ii + jj <= m
                s = ii[keep] / m
                tt = jj[keep] / m
                pts = a + np.outer(s, b - a) + np.outer(tt, c - a)
                idx = ((pts - lo) / self.vs).astype(int)
                idx = np.clip(idx, 0, self.dims - 1)
                self.grid[idx[:, 0], idx[:, 1], idx[:, 2]] = True
        # A fixed set of directions over the hemisphere (cosine weighted).
        K = 28
        rng = np.random.default_rng(7)
        dirs = []
        for k in range(K):
            u1 = (k + rng.random()) / K
            u2 = rng.random()
            r = math.sqrt(u1)
            th = 2 * math.pi * u2
            dirs.append((r * math.cos(th), r * math.sin(th), math.sqrt(max(0.0, 1 - u1))))
        self.dirs = np.array(dirs)

    def ao(self, pos, nrm):
        """How open each point is (1 = the whole sky, 0 = shut in)."""
        n = len(pos)
        out = np.ones(n)
        if n == 0:
            return out
        steps = np.arange(1.0, self.reach / self.vs + 1) * self.vs
        for s0 in range(0, n, 4096):
            P = pos[s0:s0 + 4096]
            N = normalize(nrm[s0:s0 + 4096])
            ref = np.where(np.abs(N[:, 1:2]) < 0.9, np.array([[0, 1.0, 0]]), np.array([[1.0, 0, 0]]))
            U = normalize(np.cross(ref, N))
            W = np.cross(N, U)
            D = (self.dirs[None, :, 0:1] * U[:, None, :] + self.dirs[None, :, 1:2] * W[:, None, :] + self.dirs[None, :, 2:3] * N[:, None, :])
            O = P + N * self.vs * 2.2
            hit_t = np.full(D.shape[:2], np.inf)
            for t in steps:
                Q = O[:, None, :] + D * t
                idx = np.floor((Q - self.lo) / self.vs).astype(int)
                inside = np.all((idx >= 0) & (idx < self.dims), axis=2)
                idc = np.clip(idx, 0, self.dims - 1)
                h = self.grid[idc[..., 0], idc[..., 1], idc[..., 2]] & inside
                if self.ground:
                    h |= Q[..., 1] < -0.02
                newly = h & np.isinf(hit_t)
                hit_t[newly] = t
            occl = np.where(np.isinf(hit_t), 0.0, 1 - (hit_t / (self.reach + self.vs)) ** 2)
            out[s0:s0 + 4096] = 1 - occl.mean(axis=1)
        return out


# ---------------------------------------------------------------------------------------------------------------
# Charts


AXES = {0: (2, 1), 1: (0, 2), 2: (0, 1)}


class Chart:
    def __init__(self, polys, axis, density):
        self.polys = polys
        self.axis = axis
        a, b = AXES[axis // 2]
        self.ua, self.va = a, b
        self.density = density
        pts = np.concatenate([p['v'] for p in polys])
        self.umin = pts[:, a].min()
        self.vmin = pts[:, b].min()
        self.w = max(1, int(math.ceil((pts[:, a].max() - self.umin) * density))) + 1
        self.h = max(1, int(math.ceil((pts[:, b].max() - self.vmin) * density))) + 1
        self.x = self.y = 0
        self.flat = False

    def tex(self, v):
        """Texel coordinates (x, y) of local points v."""
        return np.stack([(v[:, self.ua] - self.umin) * self.density + self.x + PAD,
                         (v[:, self.va] - self.vmin) * self.density + self.y + PAD], axis=1)


def make_charts(flat, density, by_mat=False):
    polys = [p for p in flat.polys]
    groups = {}
    for i, p in enumerate(polys):
        fn = p['fn']
        ax = int(np.argmax(np.abs(fn)))
        d = ax * 2 + (0 if fn[ax] >= 0 else 1)
        groups.setdefault((p['geo'], d, p['mat'] if by_mat else None), []).append(i)
    charts = []
    for (geo, d, _m), idxs in groups.items():
        # Connected pieces (sharing an edge).
        parent = {i: i for i in idxs}

        def find(i):
            while parent[i] != i:
                parent[i] = parent[parent[i]]
                i = parent[i]
            return i
        edges = {}
        for i in idxs:
            v = polys[i]['v']
            k = len(v)
            for c in range(k):
                a = tuple(np.round(v[c], 5))
                b = tuple(np.round(v[(c + 1) % k], 5))
                key = (a, b) if a < b else (b, a)
                if key in edges:
                    ra, rb = find(i), find(edges[key])
                    if ra != rb:
                        parent[ra] = rb
                else:
                    edges[key] = i
        comps = {}
        for i in idxs:
            comps.setdefault(find(i), []).append(polys[i])
        for comp in comps.values():
            mult = max(DENSITY.get(p['mat'], 1.0) for p in comp)
            charts.append(Chart(comp, d, density * mult))
    return charts


def pack(charts, min_w=64):
    real = [c for c in charts if not c.flat]
    area = sum((c.w + 2 * PAD) * (c.h + 2 * PAD) for c in real)
    W = min_w
    while W * W < area * 1.25 or any(c.w + 2 * PAD > W for c in real):
        W *= 2
    order = sorted(real, key=lambda c: (-(c.h), -(c.w)))
    x = y = row = 0
    for c in order:
        cw, ch = c.w + 2 * PAD, c.h + 2 * PAD
        if x + cw > W:
            y += row
            x = 0
            row = 0
        c.x, c.y = x, y
        x += cw
        row = max(row, ch)
    H = y + row
    return W, H


# ---------------------------------------------------------------------------------------------------------------
# Rasterising charts into texels


def raster(charts, W, H):
    """For every texel covered by a chart: the polygon it belongs to and its barycentric place on it."""
    owner = np.full((H, W), -1, dtype=np.int64)
    quality = np.zeros((H, W), dtype=np.int8)
    tri_of = []  # (poly dict, i0, i1, i2)
    bary = np.zeros((H, W, 3))
    for c in charts:
        if c.flat:
            continue
        for p in c.polys:
            tx = c.tex(p['v'])
            p['tex'] = tx
            k = len(tx)
            for i in range(1, k - 1):
                tid = len(tri_of)
                tri_of.append((p, 0, i, i + 1))
                a, b, cc = tx[0], tx[i], tx[i + 1]
                x0 = int(math.floor(min(a[0], b[0], cc[0]) - 1))
                x1 = int(math.ceil(max(a[0], b[0], cc[0]) + 1))
                y0 = int(math.floor(min(a[1], b[1], cc[1]) - 1))
                y1 = int(math.ceil(max(a[1], b[1], cc[1]) + 1))
                x0, y0 = max(x0, c.x), max(y0, c.y)
                x1, y1 = min(x1, c.x + c.w + 2 * PAD), min(y1, c.y + c.h + 2 * PAD)
                if x1 <= x0 or y1 <= y0:
                    continue
                xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
                den = (b[1] - cc[1]) * (a[0] - cc[0]) + (cc[0] - b[0]) * (a[1] - cc[1])
                if abs(den) < 1e-12:
                    continue
                l0 = ((b[1] - cc[1]) * (xs - cc[0]) + (cc[0] - b[0]) * (ys - cc[1])) / den
                l1 = ((cc[1] - a[1]) * (xs - cc[0]) + (a[0] - cc[0]) * (ys - cc[1])) / den
                l2 = 1 - l0 - l1
                L = np.stack([l0, l1, l2], axis=-1)
                inside = (L >= -1e-6).all(axis=-1)
                # Texels just outside the edges (the padding) take the nearest point of the triangle.
                near = (L >= -1.2 / max(1.0, abs(den) ** 0.5)).all(axis=-1) & ~inside
                Lc = np.clip(L, 0, None)
                Lc /= Lc.sum(axis=-1, keepdims=True)
                sub_o = owner[y0:y1, x0:x1]
                sub_q = quality[y0:y1, x0:x1]
                sub_b = bary[y0:y1, x0:x1]
                w_in = inside & (sub_q < 2)
                w_near = near & (sub_q < 1)
                sub_o[w_in] = tid
                sub_q[w_in] = 2
                sub_b[w_in] = Lc[w_in]
                sub_o[w_near] = tid
                sub_q[w_near] = 1
                sub_b[w_near] = Lc[w_near]
    return owner, bary, tri_of


def texel_attrs(owner, bary, tri_of):
    ys, xs = np.nonzero(owner >= 0)
    tids = owner[ys, xs]
    B = bary[ys, xs]
    n = len(tids)
    pos = np.zeros((n, 3))
    nrm = np.zeros((n, 3))
    puv = np.zeros((n, 2))
    mats = []
    # Gather the corners of each texel's triangle.
    W0 = np.zeros((n, 3))
    W1 = np.zeros((n, 3))
    W2 = np.zeros((n, 3))
    N0 = np.zeros((n, 3))
    N1 = np.zeros((n, 3))
    N2 = np.zeros((n, 3))
    U0 = np.zeros((n, 2))
    U1 = np.zeros((n, 2))
    U2 = np.zeros((n, 2))
    mat_of_tri = []
    for tid, (p, i0, i1, i2) in enumerate(tri_of):
        mat_of_tri.append(p['mat'])
    tri_w = np.array([[p['w'][i0], p['w'][i1], p['w'][i2]] for p, i0, i1, i2 in tri_of]) if tri_of else np.zeros((0, 3, 3))
    tri_n = np.array([[p['wn'][i0], p['wn'][i1], p['wn'][i2]] for p, i0, i1, i2 in tri_of]) if tri_of else np.zeros((0, 3, 3))
    tri_u = np.array([[p['uv'][i0], p['uv'][i1], p['uv'][i2]] for p, i0, i1, i2 in tri_of]) if tri_of else np.zeros((0, 3, 2))
    pos = np.einsum('nk,nkd->nd', B, tri_w[tids])
    nrm = normalize(np.einsum('nk,nkd->nd', B, tri_n[tids]))
    puv = np.einsum('nk,nkd->nd', B, tri_u[tids])
    mats = np.array(mat_of_tri, dtype=object)[tids]
    return ys, xs, pos, nrm, puv, mats


# ---------------------------------------------------------------------------------------------------------------
# The whole bake


def bake(model, verbose=True):
    flat = Flat(model)
    scheme = model.scheme or materials.Scheme()
    charts = make_charts(flat, model.density, getattr(model, 'chart_by_mat', False))
    # Tiny pieces (bolts, thin edges) share one swatch of their material and get their shading per corner instead.
    for c in charts:
        if c.w * c.h <= 4 and all(DENSITY.get(p['mat'], 1.0) <= 1.3 for p in c.polys):
            c.flat = True
    W, H = pack(charts)
    mats_flat = sorted({p['mat'] for c in charts if c.flat for p in c.polys})
    # Swatches (3x3 texels each) in a row under the charts.
    sw_y = H
    sw = {}
    x = 0
    for m in mats_flat:
        if x + 3 > W:
            x = 0
            sw_y += 3
        sw[m] = (x, sw_y)
        x += 3
    H = sw_y + (3 if mats_flat else 0)
    H = (H + 15) // 16 * 16
    owner, bary, tri_of = raster(charts, W, H)
    ys, xs, pos, nrm, puv, mats = texel_attrs(owner, bary, tri_of)
    occ = Occluder(flat, model.ground)
    ao = occ.ao(pos, nrm)
    img = np.zeros((H, W, 4))
    for m in sorted(set(mats)):
        sel = mats == m
        T = dict(pos=pos[sel], nrm=nrm[sel], puv=puv[sel], ao=ao[sel])
        rgb, alpha = materials.shade(m, T, scheme, model)
        img[ys[sel], xs[sel], :3] = rgb
        img[ys[sel], xs[sel], 3] = alpha
    # Swatches: the material's own colour, open to the sky (their corners carry the occlusion).
    centre = (np.array(model.bounds()[0]) + np.array(model.bounds()[1])) / 2
    for m, (sx, sy) in sw.items():
        T = dict(pos=np.tile(centre, (9, 1)) + np.random.default_rng(1).normal(0, 0.3, (9, 3)), nrm=np.tile([0, 0.6, 0.8], (9, 1)),
                 puv=np.full((9, 2), 0.5), ao=np.ones(9))
        rgb, alpha = materials.shade(m, T, scheme, model)
        img[sy:sy + 3, sx:sx + 3, :3] = rgb.mean(axis=0)
        img[sy:sy + 3, sx:sx + 3, 3] = alpha.mean()
    img = dilate(img, owner >= 0, sw)
    # Per-corner occlusion for the swatch pieces.
    flat_polys = [p for c in charts if c.flat for p in c.polys]
    if flat_polys:
        P = np.concatenate([p['w'] for p in flat_polys])
        N = np.concatenate([p['wn'] for p in flat_polys])
        A = occ.ao(P, N)
        k = 0
        for p in flat_polys:
            n = len(p['w'])
            p['vao'] = A[k:k + n]
            k += n
            sx, sy = sw[p['mat']]
            p['tex'] = np.tile([sx + 1.5, sy + 1.5], (n, 1))
    if verbose:
        print('%-18s polys %6d charts %5d (%d swatch) texture %dx%d grid %s' % (model.id, len(flat.polys), len(charts),
                                                                              sum(1 for c in charts if c.flat), W, H, occ.dims))
    return flat, img, W, H


def dilate(img, filled, sw, passes=3):
    """Spreads the edge texels of the charts out into the empty space round them (no dark seams)."""
    out = img.copy()
    have = filled.copy()
    for (sx, sy) in sw.values():
        have[sy:sy + 3, sx:sx + 3] = True
    for _ in range(passes):
        acc = np.zeros_like(out)
        cnt = np.zeros(have.shape)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                sh = np.roll(np.roll(out, dy, 0), dx, 1)
                hv = np.roll(np.roll(have, dy, 0), dx, 1)
                acc += sh * hv[..., None]
                cnt += hv
        grow = (~have) & (cnt > 0)
        out[grow] = acc[grow] / cnt[grow][:, None]
        have = have | grow
    return out


# ---------------------------------------------------------------------------------------------------------------
# Writing the binary mesh


def export(model, flat, W, H, path):
    by_part = {}
    for p in flat.polys:
        by_part.setdefault(p['part'], []).append(p)
    out = bytearray()
    out += b'ADM2'
    out += struct.pack('>iiibh', 1, W, H, 1 if model.tinted else 0, len(model.parts))
    index = {id(p): i for i, p in enumerate(model.parts)}
    stats = [0, 0]
    for pi, part in enumerate(model.parts):
        name = part.name.encode('utf-8')
        out += struct.pack('>H', len(name)) + name
        parent = index[id(part.parent)] if part.parent is not None else -1
        out += struct.pack('>h', parent)
        out += struct.pack('>6f', *part.pivot, *part.rot)
        out += struct.pack('>fbb', part.spin, 1 if part.steer else 0, part.track)
        polys = by_part.get(pi, [])
        layers = {}
        for p in polys:
            layers.setdefault(p['layer'], []).append(p)
        out += struct.pack('>b', len(layers))
        piv = np.asarray(part.pivot)
        for lname in sorted(layers, key=lambda k: LAYERS[k]):
            lp = layers[lname]
            verts = {}
            vlist = []
            quads = []
            allv = np.concatenate([p['v'] for p in lp]) - piv
            ext = float(np.abs(allv).max()) if len(allv) else 1.0
            scale = max(ext, 1e-3) / 32000.0
            for p in lp:
                k = len(p['v'])
                tx = p['tex']
                flags = 1 if (model.tinted and p['mat'] in materials.TINTED) else 0
                if 'vao' in p:
                    shade_c = [int(np.clip(255 * (0.42 + 0.58 * a ** 1.1), 0, 255)) for a in p['vao']]
                else:
                    shade_c = [255] * k
                ids = []
                for c in range(k):
                    lv = p['v'][c] - piv
                    q = (int(round(lv[0] / scale)), int(round(lv[1] / scale)), int(round(lv[2] / scale)))
                    nn = p['n'][c]
                    qn = (int(round(nn[0] * 127)), int(round(nn[1] * 127)), int(round(nn[2] * 127)))
                    u = int(np.clip(round(tx[c][0] / W * 65535), 0, 65535))
                    v = int(np.clip(round(tx[c][1] / H * 65535), 0, 65535))
                    s = shade_c[c]
                    argb = (255 << 24) | (s << 16) | (s << 8) | s
                    key = (q, qn, u, v, argb, flags)
                    if key not in verts:
                        verts[key] = len(vlist)
                        vlist.append(key)
                    ids.append(verts[key])
                # Wound for the game: its space is a mirror of ours (y down, then turned), so the corners go the other way.
                ids = ids[::-1]
                if k == 4:
                    quads.append(ids)
                elif k == 3:
                    quads.append([ids[0], ids[1], ids[2], ids[2]])
                else:
                    for i in range(1, k - 1):
                        quads.append([ids[0], ids[i], ids[i + 1], ids[i + 1]])
            out += struct.pack('>bfffii', LAYERS[lname], float(part.near), scale, 0.0, len(vlist), len(quads))
            for (q, qn, u, v, argb, flags) in vlist:
                out += struct.pack('>hhhbbbHHIb', q[0], q[1], q[2], qn[0], qn[1], qn[2], u, v, argb & 0xFFFFFFFF, flags)
            big = len(vlist) > 65535
            for qd in quads:
                out += struct.pack('>4i' if big else '>4H', *qd)
            stats[0] += len(vlist)
            stats[1] += len(quads)
    with open(path, 'wb') as fh:
        fh.write(bytes(out))
    return stats
