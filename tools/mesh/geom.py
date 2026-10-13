"""
2.0: the primitives the meshes are built from. Each returns a Geo (one smoothing group). Polygons are wound
counter-clockwise seen from outside (their cross product points out).
"""
import math

import numpy as np

from core import Geo, normalize, face_normal, v3


# ---------------------------------------------------------------------------------------------------------------
# Boxes


def _axis_values(h, b, s):
    """Grid positions along one axis of a rounded box's face: the flat middle and s steps of the rounded edge, so that
    each step is an equal angle of the round (see rbox)."""
    if b <= 0 or s <= 0:
        return [-h, h]
    out = []
    for k in range(s, -1, -1):
        out.append(-(h - b) - b * math.tan(math.radians(45.0 * k / s)))
    for k in range(0, s + 1):
        out.append((h - b) + b * math.tan(math.radians(45.0 * k / s)))
    if h - b <= 1e-9:
        # No flat middle: the two middle values coincide.
        out.pop(s + 1)
    return out


def rbox(lo, hi, mat, r=0.0, segs=1, faces=None, mats=None, smooth=None):
    """A box with rounded edges of radius r ({segs} steps per 45 degrees). faces: which of 'right', 'left', 'top',
    'bottom', 'front', 'back' to keep; mats: a material per face. The corners carry their true normals (rounded edges
    shade smoothly, flat faces stay flat)."""
    lo = np.asarray(lo, dtype=np.float64)
    hi = np.asarray(hi, dtype=np.float64)
    c = (lo + hi) / 2
    h = (hi - lo) / 2
    r = min(r, float(h.min()) * 0.999) if r > 0 else 0.0
    inner = h - r
    g = Geo(smooth if smooth is not None else 80)
    # Each face: (name, axis, sign, u axis, v axis) with u x v = outward normal.
    specs = [('right', 0, 1, 2, 1), ('left', 0, -1, 1, 2), ('top', 1, 1, 0, 2), ('bottom', 1, -1, 2, 0),
             ('front', 2, 1, 1, 0), ('back', 2, -1, 0, 1)]
    for name, ax, sg, ua, va in specs:
        if faces is not None and name not in faces:
            continue
        m = (mats or {}).get(name, mat)
        us = _axis_values(h[ua], r, segs if r > 0 else 0)
        vs = _axis_values(h[va], r, segs if r > 0 else 0)
        # Make u x v point out of the face: if not, swap the order of u.
        e = np.zeros(3)
        e[ua] = 1
        f = np.zeros(3)
        f[va] = 1
        if np.dot(np.cross(e, f), np.eye(3)[ax] * sg) < 0:
            us = us[::-1]
        P = np.zeros((len(us), len(vs), 3))
        N = np.zeros((len(us), len(vs), 3))
        UV = np.zeros((len(us), len(vs), 2))
        for i, u in enumerate(us):
            for j, v in enumerate(vs):
                p = np.zeros(3)
                p[ax] = sg * h[ax]
                p[ua] = u
                p[va] = v
                q = np.clip(p, -inner, inner)
                d = p - q
                dl = np.linalg.norm(d)
                if dl > 1e-12:
                    n = d / dl
                    pos = q + n * r
                else:
                    n = np.eye(3)[ax] * sg
                    pos = p
                P[i, j] = pos + c
                N[i, j] = n
                UV[i, j] = (u + h[ua], v + h[va])
        for i in range(len(us) - 1):
            for j in range(len(vs) - 1):
                idx = [(i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)]
                vv = np.array([P[a] for a in idx])
                if np.linalg.norm(vv[2] - vv[0]) < 1e-9 and np.linalg.norm(vv[3] - vv[1]) < 1e-9:
                    continue
                g.add(vv, m, uv=np.array([UV[a] for a in idx]), n=np.array([N[a] for a in idx]) if r > 0 else None)
    return g


def box(lo, hi, mat, faces=None, mats=None):
    return rbox(lo, hi, mat, 0.0, 0, faces, mats)


def cbox(c, size, mat, r=0.0, segs=1, faces=None, mats=None):
    c = np.asarray(c, dtype=np.float64)
    s = np.asarray(size, dtype=np.float64) / 2
    return rbox(c - s, c + s, mat, r, segs, faces, mats)


# ---------------------------------------------------------------------------------------------------------------
# Round things: lathe, cylinders, spheres, tubes


def _basis(axis):
    a = normalize(axis)
    ref = np.array([0.0, 1.0, 0.0]) if abs(a[1]) < 0.9 else np.array([1.0, 0.0, 0.0])
    u = normalize(np.cross(ref, a))
    w = np.cross(a, u)
    return a, u, w


def lathe(a, b, profile, mat, n=24, smooth=40.0, start=0.0, sweep=360.0, phase=0.0):
    """Turns a profile about the axis a -> b. profile: [(t, r) or (t, r, mat)], t metres along the axis from a, r the
    radius; the material of a point covers the band to the next point. Corners in the profile sharper than {smooth}
    degrees stay sharp. primitive uv: (angle 0..1 round, t)."""
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    axis, u, w = _basis(b - a)
    prof = [(float(p[0]), float(p[1]), p[2] if len(p) > 2 else mat) for p in profile]
    m = len(prof)
    # 2D normals of each profile segment (pointing out: away from the axis for a profile going along +t).
    seg_n = []
    for i in range(m - 1):
        dt = prof[i + 1][0] - prof[i][0]
        dr = prof[i + 1][1] - prof[i][1]
        ln = math.hypot(dt, dr) or 1
        seg_n.append((-dr / ln, dt / ln))  # (along t, along r)
    cs = math.cos(math.radians(smooth))
    full = abs(sweep - 360.0) < 1e-6
    steps = n
    angs = [math.radians(phase + start + sweep * k / steps) for k in range(steps + 1)]
    g = Geo(smooth)
    for i in range(m - 1):
        t0, r0, mt = prof[i]
        t1, r1, _ = prof[i + 1]
        if abs(t1 - t0) < 1e-12 and abs(r1 - r0) < 1e-12:
            continue
        sn = seg_n[i]

        def vnormal(k_end):
            # The normal at one end of this segment: averaged with the neighbour segment if the corner is soft.
            j = i - 1 if k_end == 0 else i + 1
            nn = np.array(sn)
            if 0 <= j < m - 1:
                o = np.array(seg_n[j])
                if np.dot(nn, o) > cs:
                    nn = nn + o
                    nn /= np.linalg.norm(nn) or 1
            return nn
        n0 = vnormal(0)
        n1 = vnormal(1)
        for k in range(steps):
            a0, a1 = angs[k], angs[k + 1]
            d0 = u * math.cos(a0) + w * math.sin(a0)
            d1 = u * math.cos(a1) + w * math.sin(a1)
            p00 = a + axis * t0 + d0 * r0
            p01 = a + axis * t0 + d1 * r0
            p10 = a + axis * t1 + d0 * r1
            p11 = a + axis * t1 + d1 * r1
            N00 = normalize(axis * n0[0] + d0 * n0[1])
            N01 = normalize(axis * n0[0] + d1 * n0[1])
            N10 = normalize(axis * n1[0] + d0 * n1[1])
            N11 = normalize(axis * n1[0] + d1 * n1[1])
            uu0 = k / steps
            uu1 = (k + 1) / steps
            # Wound out: going round (u -> w) and along the axis.
            verts = [p00, p10, p11, p01]
            norms = [N00, N10, N11, N01]
            uvs = [(uu0, t0), (uu0, t1), (uu1, t1), (uu1, t0)]
            if r0 < 1e-9:
                verts, norms, uvs = verts[1:], norms[1:], uvs[1:]
            elif r1 < 1e-9:
                verts, norms, uvs = [verts[0], verts[1], verts[3]], [norms[0], norms[1], norms[3]], [uvs[0], uvs[1], uvs[3]]
            vv = np.array(verts)
            fn = face_normal(vv)
            avg = np.mean(norms, axis=0)
            if np.dot(fn, avg) < 0:
                vv = vv[::-1]
                norms = norms[::-1]
                uvs = uvs[::-1]
            g.add(vv, mt, uv=np.array(uvs), n=np.array(norms))
    return g


def cyl(a, b, r, mat, n=16, r2=None, caps=True, cap_mat=None, bevel=0.0, smooth=40.0):
    """A cylinder (a cone if r2) from a to b, with flat caps (a bevel rounds their edges)."""
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    L = float(np.linalg.norm(b - a))
    r2 = r if r2 is None else r2
    cm = cap_mat or mat
    prof = []
    if caps:
        prof.append((0.0, 0.0, cm))
        if bevel > 0:
            prof.append((0.0, max(0.0, r - bevel), cm))
            prof.append((bevel * 0.3, r - bevel * 0.3, mat))
            prof.append((bevel, r, mat))
        else:
            prof.append((0.0, r, mat))
    else:
        prof.append((0.0, r, mat))
    if bevel > 0 and caps:
        prof.append((L - bevel, r2, mat))
        prof.append((L - bevel * 0.3, r2 - bevel * 0.3, cm))
        prof.append((L, max(0.0, r2 - bevel), cm))
    else:
        prof.append((L, r2, cm if caps else mat))
    if caps:
        prof.append((L, 0.0, cm))
    return lathe(a, b, prof, mat, n, smooth)


def sphere(c, r, mat, n=16, rings=8, squash=1.0):
    c = np.asarray(c, dtype=np.float64)
    prof = []
    for k in range(rings + 1):
        th = math.pi * k / rings
        prof.append((r * squash - math.cos(th) * r * squash, math.sin(th) * r))
    return lathe(c - np.array([0, r * squash, 0]), c + np.array([0, r * squash, 0]), prof, mat, n, 89)


def dome(c, r, h, mat, n=16, rings=5, axis=(0, 1, 0)):
    """Half a squashed sphere standing on its base at c (base radius r, height h along axis)."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    prof = []
    for k in range(rings + 1):
        th = math.pi / 2 * k / rings
        prof.append((math.sin(th) * h, math.cos(th) * r))
    return lathe(c, c + ax * h, prof, mat, n, 89)


def tube(points, r, mat, n=8, closed=False, caps=True, smooth=60.0):
    """A pipe swept along a polyline (cables, rails, handles, exhausts)."""
    P = [np.asarray(p, dtype=np.float64) for p in points]
    m = len(P)
    if m < 2:
        return Geo()
    tans = []
    for i in range(m):
        if closed:
            d = normalize(P[(i + 1) % m] - P[i]) + normalize(P[i] - P[i - 1])
        elif i == 0:
            d = P[1] - P[0]
        elif i == m - 1:
            d = P[-1] - P[-2]
        else:
            d = normalize(P[i + 1] - P[i]) + normalize(P[i] - P[i - 1])
        tans.append(normalize(d))
    # Parallel transport of a frame along the path.
    _, u, _ = _basis(tans[0])
    frames = []
    for i in range(m):
        tt = tans[i]
        u = normalize(u - tt * np.dot(u, tt))
        w = np.cross(tt, u)
        frames.append((u, w))
    # Mitre scale at the bends.
    rings = []
    norms = []
    for i in range(m):
        u, w = frames[i]
        scale = 1.0
        if 0 < i < m - 1 or closed:
            a = normalize(P[(i + 1) % m] - P[i])
            b = normalize(P[i] - P[i - 1])
            cosh = max(0.3, math.sqrt(max(0.0, (1 + np.dot(a, b)) / 2)))
            scale = 1 / cosh
        ring = []
        nr = []
        for k in range(n):
            th = 2 * math.pi * k / n
            d = u * math.cos(th) + w * math.sin(th)
            ring.append(P[i] + d * r * scale)
            nr.append(d)
        rings.append(ring)
        norms.append(nr)
    g = Geo(smooth)
    segs = m if closed else m - 1
    length = 0.0
    for i in range(segs):
        j = (i + 1) % m
        seg = float(np.linalg.norm(P[j] - P[i]))
        for k in range(n):
            k2 = (k + 1) % n
            vv = np.array([rings[i][k], rings[i][k2], rings[j][k2], rings[j][k]])
            nn = np.array([norms[i][k], norms[i][k2], norms[j][k2], norms[j][k]])
            uv = np.array([(k / n, length), ((k + 1) / n, length), ((k + 1) / n, length + seg), (k / n, length + seg)])
            if np.dot(face_normal(vv), nn.mean(axis=0)) < 0:
                vv, nn, uv = vv[::-1], nn[::-1], uv[::-1]
            g.add(vv, mat, uv=uv, n=nn)
        length += seg
    if caps and not closed:
        for idx, sgn in ((0, -1), (m - 1, 1)):
            ring = np.array(rings[idx])
            vv = ring if sgn > 0 else ring[::-1]
            if np.dot(face_normal(vv), tans[idx] * sgn) < 0:
                vv = vv[::-1]
            g.add(vv, mat)
    return g


# ---------------------------------------------------------------------------------------------------------------
# Lofts and extrusions


def loft(rings, mat, closed=True, cap0=False, cap1=False, smooth=40.0, mats=None, cap_mat=None):
    """A skin over rings (each a list of points, all the same count; going round each ring so that the surface's
    outside is on the left looking along the next ring). mats(i, k) -> material of the band between ring i and i+1,
    point k and k+1 (or None to leave a hole). primitive uv: (point index along the ring / count, distance along)."""
    R = [np.asarray(r, dtype=np.float64) for r in rings]
    n = len(R[0])
    g = Geo(smooth)
    along = [0.0]
    for i in range(1, len(R)):
        along.append(along[-1] + float(np.linalg.norm(R[i].mean(axis=0) - R[i - 1].mean(axis=0))))
    kk = n if closed else n - 1
    for i in range(len(R) - 1):
        for k in range(kk):
            k2 = (k + 1) % n
            m = mats(i, k) if mats else mat
            if m is None:
                continue
            vv = np.array([R[i][k], R[i + 1][k], R[i + 1][k2], R[i][k2]])
            uv = np.array([(k / n, along[i]), (k / n, along[i + 1]), ((k + 1) / n, along[i + 1]), ((k + 1) / n, along[i])])
            # Drop degenerate corners (rings that pinch to a point).
            keep = [0]
            for q in range(1, 4):
                if np.linalg.norm(vv[q] - vv[keep[-1]]) > 1e-9:
                    keep.append(q)
            if len(keep) > 1 and np.linalg.norm(vv[keep[-1]] - vv[keep[0]]) < 1e-9:
                keep.pop()
            if len(keep) < 3:
                continue
            g.add(vv[keep], m, uv=uv[keep])
    cm = cap_mat or mat
    if cap0:
        g.extend(polygon_cap(R[0][::-1], cm))
    if cap1:
        g.extend(polygon_cap(R[-1], cm))
    return g


def polygon_cap(points, mat):
    """A flat (or nearly flat) polygon closing a ring, triangulated by ear clipping. Wound as given."""
    P = np.asarray(points, dtype=np.float64)
    g = Geo(0)
    nrm = face_normal(P)
    if np.linalg.norm(nrm) < 1e-9:
        return g
    _, u, w = _basis(nrm)
    pts2 = np.stack([P @ u, P @ w], axis=1)
    for tri in ear_clip(pts2):
        vv = P[list(tri)]
        if np.dot(face_normal(vv), nrm) < 0:
            vv = vv[::-1]
        g.add(vv, mat)
    return g


def ear_clip(pts):
    """Triangulates a simple polygon (2D points, either winding) -> index triples."""
    n = len(pts)
    if n < 3:
        return []
    if n == 3:
        return [(0, 1, 2)]
    area = 0.0
    for i in range(n):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % n]
        area += x0 * y1 - x1 * y0
    ccw = area > 0
    idx = list(range(n))
    out = []

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        found = False
        m = len(idx)
        for k in range(m):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % m]
            a, b, c = pts[i0], pts[i1], pts[i2]
            cr = cross(a, b, c)
            if (cr <= 1e-12) if ccw else (cr >= -1e-12):
                continue
            inside = False
            for j in idx:
                if j in (i0, i1, i2):
                    continue
                p = pts[j]
                c1 = cross(a, b, p)
                c2 = cross(b, c, p)
                c3 = cross(c, a, p)
                if ccw and c1 >= 0 and c2 >= 0 and c3 >= 0 or not ccw and c1 <= 0 and c2 <= 0 and c3 <= 0:
                    inside = True
                    break
            if inside:
                continue
            out.append((i0, i1, i2))
            idx.pop(k)
            found = True
            break
        if not found:
            break
    if len(idx) == 3:
        out.append(tuple(idx))
    elif len(idx) > 3:
        for k in range(1, len(idx) - 1):
            out.append((idx[0], idx[k], idx[k + 1]))
    return out


def extrude(poly2d, axis_u, axis_v, origin, depth, mat, side_mat=None, back=True, bevel=0.0, smooth=30.0):
    """A flat shape (2D points in the plane origin + x*axis_u + y*axis_v) pushed out by {depth} along u x v."""
    u = np.asarray(axis_u, dtype=np.float64)
    v = np.asarray(axis_v, dtype=np.float64)
    o = np.asarray(origin, dtype=np.float64)
    nrm = normalize(np.cross(u, v))
    P2 = np.asarray(poly2d, dtype=np.float64)
    area = 0.0
    for i in range(len(P2)):
        area += P2[i][0] * P2[(i + 1) % len(P2)][1] - P2[(i + 1) % len(P2)][0] * P2[i][1]
    if area < 0:
        P2 = P2[::-1]
    base = np.array([o + p[0] * u + p[1] * v for p in P2])
    top = base + nrm * depth
    if bevel > 0:
        # The front face shrunk inwards by the bevel (an offset polygon for convex-ish shapes).
        c2 = P2.mean(axis=0)
        inset = []
        for i in range(len(P2)):
            p = P2[i]
            d = p - c2
            dl = np.linalg.norm(d) or 1
            inset.append(p - d / dl * bevel)
        top_in = np.array([o + p[0] * u + p[1] * v for p in inset]) + nrm * depth
        mid = base + nrm * (depth - bevel)
        g = loft([base, mid, top_in], side_mat or mat, closed=True, smooth=smooth)
        g.extend(polygon_cap(top_in, mat))
    else:
        g = loft([base, top], side_mat or mat, closed=True, smooth=smooth)
        g.extend(polygon_cap(top, mat))
    if back:
        g.extend(polygon_cap(base[::-1], side_mat or mat))
    # The loft goes round the ring counter-clockwise seen from the front: its outside faces away from the middle.
    fix_winding(g, (base.mean(axis=0) + top.mean(axis=0)) / 2)
    return g


def fix_winding(g, centre):
    """Turns polygons of a closed, roughly convex shape to face away from its centre."""
    c = np.asarray(centre, dtype=np.float64)
    for p in g.polys:
        fn = face_normal(p.v)
        if np.dot(fn, p.v.mean(axis=0) - c) < 0:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
            if p.n is not None:
                p.n = p.n[::-1].copy()
    return g


def quad(a, b, c, d, mat, uv=None, double=False):
    g = Geo(0)
    vv = np.array([a, b, c, d], dtype=np.float64)
    if uv is None:
        uv = [(0, 0), (1, 0), (1, 1), (0, 1)]
    g.add(vv, mat, uv=np.array(uv, dtype=np.float64))
    if double:
        g.add(vv[::-1].copy(), mat, uv=np.array(uv, dtype=np.float64)[::-1].copy())
    return g


def panel(c, size_u, size_v, axis_u, axis_v, mat, double=False):
    """A flat rectangle centred at c (a decal surface, a plate, a screen), uv 0..1 across it."""
    c = np.asarray(c, dtype=np.float64)
    u = normalize(np.asarray(axis_u, dtype=np.float64)) * size_u / 2
    v = normalize(np.asarray(axis_v, dtype=np.float64)) * size_v / 2
    return quad(c - u - v, c + u - v, c + u + v, c - u + v, mat, double=double)


# ---------------------------------------------------------------------------------------------------------------
# Composite: wheels


def tyre(c, axis, radius, width, rim_r, mat='tyre', n=28, sidewall='tyre_wall', bulge=0.18):
    """A tyre: the rounded tread, the sidewalls with their bulge, down to the rim. axis: the axle direction (x)."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    hw = width / 2
    sh = min(width * 0.22, (radius - rim_r) * 0.45)
    prof = [(-hw * 0.82, rim_r, sidewall), (-hw, rim_r + (radius - rim_r) * 0.35, sidewall),
            (-hw * (1 + bulge * 0.15), radius - sh * 1.2, sidewall), (-hw + sh * 0.25, radius - sh * 0.25, mat),
            (-hw + sh, radius, mat), (hw - sh, radius, mat), (hw - sh * 0.25, radius - sh * 0.25, sidewall),
            (hw * (1 + bulge * 0.15), radius - sh * 1.2, sidewall), (hw, rim_r + (radius - rim_r) * 0.35, sidewall),
            (hw * 0.82, rim_r, sidewall)]
    return lathe(c - ax * 0, c + ax, [(t, r, m) for t, r, m in prof], mat, n, 45)


def rim(c, axis, r, depth, style='alloy', mat='rim', n=24, spokes=5, hub_mat='rim_dark'):
    """A wheel's rim seen from outside: the lip, the dish or spokes, the hub and its nuts. c: the outer face's centre;
    axis points out of the wheel; the rim goes {depth} back into it."""
    c = np.asarray(c, dtype=np.float64)
    ax = normalize(np.asarray(axis, dtype=np.float64))
    _, u, w = _basis(ax)
    g = Geo(40)
    rb = r * 0.9
    # The lip (its face out) and the barrel's inside, seen through the spokes.
    g.extend(lathe(c - ax * depth, c, [(depth, r, mat), (depth, rb, mat), (depth * 0.85, rb, 'rim_dark'), (0.0, rb, 'rim_dark')], mat, n, 50))
    back = c - ax * depth * 0.8
    if style == 'steel':
        # A pressed steel wheel: a dish with round holes (dark), a small hub cap.
        g.extend(lathe(back, c, [(0, rb, mat), (depth * 0.45, r * 0.62, mat), (depth * 0.5, r * 0.42, mat), (depth * 0.75, r * 0.3, mat),
                                 (depth * 0.8, 0.0, mat)], mat, n, 50))
        for k in range(6):
            th = 2 * math.pi * k / 6 + math.pi / 6
            p = back + ax * depth * 0.47 + (u * math.cos(th) + w * math.sin(th)) * r * 0.52
            g.extend(cyl(p - ax * 0.004, p + ax * 0.006, r * 0.09, 'rim_dark', n=8))
    elif style == 'military':
        # A military wheel: a deep disc, a ring of big nuts, the central tyre-inflation hub.
        g.extend(lathe(back, c - ax * depth * 0.25, [(0, rb, mat), (depth * 0.1, r * 0.8, mat), (depth * 0.12, r * 0.45, mat),
                                                     (depth * 0.5, r * 0.32, mat), (depth * 0.55, 0.0, mat)], mat, n, 40))
        for k in range(8):
            th = 2 * math.pi * k / 8
            p = back + ax * depth * 0.12 + (u * math.cos(th) + w * math.sin(th)) * r * 0.6
            g.extend(cyl(p, p + ax * 0.035, r * 0.07, 'bolt', n=6))
        g.extend(cyl(c - ax * depth * 0.3, c + ax * 0.05, r * 0.16, 'rim_dark', n=10, bevel=0.01))
    else:
        # Alloy: a dark disc behind, spokes from the hub to the lip, the hub with its nuts and a cap.
        g.extend(lathe(back - ax * 0.002, back, [(0.0, rb, 'rim_dark'), (0.0, 0.0, 'rim_dark')], 'rim_dark', n))
        hub_r = r * 0.3
        hub_c = c - ax * 0.02
        for k in range(spokes):
            th = 2 * math.pi * k / spokes
            d = u * math.cos(th) + w * math.sin(th)
            e = np.cross(ax, d)
            p0 = hub_c + d * hub_r * 0.8
            p1 = c + d * rb * 0.99 - ax * depth * 0.18
            ring0 = [p0 - e * r * 0.09, p0 + e * r * 0.09, p0 + e * r * 0.07 - ax * 0.05, p0 - e * r * 0.07 - ax * 0.05]
            ring1 = [p1 - e * r * 0.13, p1 + e * r * 0.13, p1 + e * r * 0.1 - ax * 0.04, p1 - e * r * 0.1 - ax * 0.04]
            sp = loft([ring0, ring1], mat, closed=True, smooth=30)
            fix_winding(sp, (p0 + p1) / 2 - ax * 0.022)
            g.extend(sp)
        g.extend(lathe(hub_c - ax * 0.05, hub_c + ax * 0.02, [(0, hub_r, mat), (0.05, hub_r, mat), (0.065, hub_r * 0.75, mat),
                                                              (0.07, 0, hub_mat)], mat, 16, 50))
        for k in range(5):
            th = 2 * math.pi * k / 5 + 0.3
            p = hub_c + ax * 0.015 + (u * math.cos(th) + w * math.sin(th)) * hub_r * 0.6
            g.extend(cyl(p, p + ax * 0.012, 0.012, 'bolt', n=6))
    return g


def wheel(c, side, radius, width, rim_ratio=0.62, style='alloy', n=28, tread='tyre'):
    """A whole road wheel at c (the axle centre), its outer face on {side} (+1 right, -1 left)."""
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    g = Geo(45)
    rr = radius * rim_ratio
    g.extend(tyre(c, ax, radius, width, rr, tread, n))
    g.extend(rim(c + ax * (width / 2 - 0.03), ax, rr, width * 0.75, style, n=max(12, n - 4)))
    # The inner face: a dark disc (the brake, the axle) so you cannot see through.
    g.extend(lathe(c - ax * (width * 0.35), c - ax * (width * 0.35 + 0.001), [(0, 0, 'rim_dark'), (0, rr, 'rim_dark')], 'rim_dark', 12))
    g.extend(lathe(c - ax * (width * 0.35 + 0.001), c - ax * (width * 0.35), [(0, 0, 'rim_dark'), (0, rr, 'rim_dark')], 'rim_dark', 12))
    return g
