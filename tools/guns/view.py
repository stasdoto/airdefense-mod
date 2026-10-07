#!/usr/bin/env python3
"""
A small software renderer for the mod's item models, to look at a gun the way the game shows it without starting
the game: first person at the hip and aimed (camera at the eye, the hand's place, vertical FOV 70 like the game's
hand view), in an item frame and as the inventory icon.

python3 tools/guns/view.py OUT_DIR [ids...]      -> OUT_DIR/<id>.png (hip | aimed | frame)

Faces are textured and lit like items in the hand (two soft lights + ambient), z-buffered, 2x supersampled.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.abspath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets'))

# Corner order per face (x, y, z index into (lo, hi)) as the texture's (u0v0, u1v0, u1v1, u0v1), matching gunkit's
# painter: east u runs to -z, west to +z, north to -x, south to +x, up: u = +x, v = +z, down: u = +x, v = -z.
FACE_CORNERS = {
    'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}
NORMALS = {'east': (1, 0, 0), 'west': (-1, 0, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def resolve(ref):
    ns, p = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    return ns, p


_tex_cache = {}


def texture(ref):
    if ref not in _tex_cache:
        ns, p = resolve(ref)
        _tex_cache[ref] = np.asarray(Image.open(os.path.join(ASSETS, ns, 'textures', p + '.png')).convert('RGBA')).astype(np.float64)
    return _tex_cache[ref]


def load_model(ref):
    ns, p = resolve(ref)
    with open(os.path.join(ASSETS, ns, 'models', p + '.json')) as f:
        m = json.load(f)
    if 'parent' in m and 'elements' not in m:
        parent = load_model(m['parent'])
        tex = dict(parent.get('textures', {}))
        tex.update(m.get('textures', {}))
        parent['textures'] = tex
        disp = dict(parent.get('display', {}))
        disp.update(m.get('display', {}))
        parent['display'] = disp
        return parent
    return m


def tex_of(m, key):
    t = m['textures']
    while key.startswith('#'):
        key = t[key[1:]]
    return key


def model_triangles(m):
    """All faces as (4 corners in model px space, uv rect (0..16), texture ref, normal)."""
    out = []
    for e in m.get('elements', []):
        lo = np.array(e['from'], dtype=np.float64)
        hi = np.array(e['to'], dtype=np.float64)
        R = np.eye(3)
        o = np.zeros(3)
        if 'rotation' in e:
            r = e['rotation']
            if 'axis' in r:
                a = math.radians(r['angle'])
                R = {'x': rx, 'y': ry, 'z': rz}[r['axis']](a)
            else:
                R = rx(math.radians(r.get('x', 0))) @ ry(math.radians(r.get('y', 0))) @ rz(math.radians(r.get('z', 0)))
            o = np.array(r['origin'], dtype=np.float64)
        box = (lo, hi)
        for face, f in e['faces'].items():
            pts = []
            for ix, iy, iz in FACE_CORNERS[face]:
                p = np.array([box[ix][0], box[iy][1], box[iz][2]])
                pts.append(R @ (p - o) + o)
            n = R @ np.array(NORMALS[face], dtype=np.float64)
            out.append((np.array(pts), f.get('uv', [0, 0, 16, 16]), tex_of(m, f['texture']), n))
    return out


def display_matrix(d):
    """Minecraft's ItemTransform: translate, rotate XYZ, scale - then the model is drawn from -0.5..0.5."""
    t = np.array(d.get('translation', [0, 0, 0]), dtype=np.float64) / 16.0
    r = [math.radians(a) for a in d.get('rotation', [0, 0, 0])]
    s = np.array(d.get('scale', [1, 1, 1]), dtype=np.float64)
    R = rx(r[0]) @ ry(r[1]) @ rz(r[2])
    return lambda p: t + R @ (s * (p / 16.0 - 0.5))


def rasterize(tris, W, H, project, light=True, bg=None):
    """tris: list of (world pts (4,3), uv rect, tex, normal (world)). project: world -> (x_px, y_px, depth)."""
    color = np.zeros((H, W, 3)) if bg is None else bg.astype(np.float64).copy()
    zbuf = np.full((H, W), np.inf)
    l1 = np.array([0.2, 1.0, -0.7]); l1 /= np.linalg.norm(l1)
    l2 = np.array([-0.2, 1.0, 0.7]); l2 /= np.linalg.norm(l2)
    for pts, uv, tref, n in tris:
        tex = texture(tref)
        th, tw = tex.shape[:2]
        u0, v0, u1, v1 = [c / 16.0 for c in uv]
        uvs = np.array([[u0, v0], [u1, v0], [u1, v1], [u0, v1]])
        P = np.array([project(p) for p in pts])
        if np.any(P[:, 2] <= 0.01):
            continue
        nn = n / max(1e-9, np.linalg.norm(n))
        shade = 1.0
        if light:
            shade = min(1.0, 0.4 + 0.6 * (max(0, nn @ l1) + max(0, nn @ l2)) * 0.75 + 0.15)
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = P[list(tri)]
            ua, ub, uc = uvs[list(tri)]
            xmin = max(0, int(math.floor(min(a[0], b[0], c[0]))))
            xmax = min(W - 1, int(math.ceil(max(a[0], b[0], c[0]))))
            ymin = max(0, int(math.floor(min(a[1], b[1], c[1]))))
            ymax = min(H - 1, int(math.ceil(max(a[1], b[1], c[1]))))
            if xmin > xmax or ymin > ymax:
                continue
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-12:
                continue
            ys, xs = np.mgrid[ymin:ymax + 1, xmin:xmax + 1]
            px = xs + 0.5
            py = ys + 0.5
            w0 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / den
            w1 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            # Perspective-correct: interpolate 1/z and uv/z.
            iz = w0 / a[2] + w1 / b[2] + w2 / c[2]
            z = 1 / iz
            uu = (w0 * ua[0] / a[2] + w1 * ub[0] / b[2] + w2 * uc[0] / c[2]) * z
            vv = (w0 * ua[1] / a[2] + w1 * ub[1] / b[2] + w2 * uc[1] / c[2]) * z
            ti = np.clip((uu * tw).astype(int), 0, tw - 1)
            tj = np.clip((vv * th).astype(int), 0, th - 1)
            texel = tex[tj, ti]
            ok = inside & (texel[..., 3] > 25) & (z < zbuf[ys, xs])
            if not ok.any():
                continue
            yy, xx = ys[ok], xs[ok]
            zbuf[yy, xx] = z[ok]
            color[yy, xx] = texel[ok][:, :3] * shade
    return color, zbuf


def sky(W, H, horizon=0.52):
    img = np.zeros((H, W, 3))
    hy = int(H * horizon)
    t = np.linspace(0, 1, hy)[:, None]
    img[:hy] = (np.array([120, 166, 255]) * (1 - t) + np.array([190, 214, 255]) * t)[:, None, :]
    img[hy:] = np.array([96, 140, 62])
    return img


def first_person(model_ref, display_key='firstperson_righthand', W=854, H=480, fov=70.0, ss=2):
    m = load_model(model_ref)
    d = m.get('display', {}).get(display_key, {})
    disp = display_matrix(d)
    hand = np.array([0.56, -0.52, -0.72])
    tris = []
    for pts, uv, tref, n in model_triangles(m):
        w = np.array([hand + disp(p) for p in pts])
        r = [math.radians(a) for a in d.get('rotation', [0, 0, 0])]
        R = rx(r[0]) @ ry(r[1]) @ rz(r[2])
        tris.append((w, uv, tref, R @ n))
    Ws, Hs = W * ss, H * ss
    f = (Hs / 2) / math.tan(math.radians(fov) / 2)

    def project(p):
        return (Ws / 2 + p[0] / -p[2] * f, Hs / 2 - p[1] / -p[2] * f, -p[2])
    col, _ = rasterize(tris, Ws, Hs, project, bg=np.kron(sky(W, H), np.ones((ss, ss, 1))))
    small = col.reshape(H, ss, W, ss, 3).mean(axis=(1, 3))
    # Crosshair.
    cx, cy = W // 2, H // 2
    small[cy, cx - 5:cx + 6] = 230
    small[cy - 5:cy + 6, cx] = 230
    return np.clip(small, 0, 255).astype(np.uint8)


def ortho(model_ref, display_key='fixed', size=256, ss=2, yaw=0.0, pitch=0.0):
    """Looking at the model from the front (an item frame on a wall), orthographic, the 16x16 block filling the image."""
    m = load_model(model_ref)
    d = m.get('display', {}).get(display_key, {})
    disp = display_matrix(d)
    r = [math.radians(a) for a in d.get('rotation', [0, 0, 0])]
    R = rx(r[0]) @ ry(r[1]) @ rz(r[2])
    V = rx(math.radians(pitch)) @ ry(math.radians(yaw))
    tris = []
    for pts, uv, tref, n in model_triangles(m):
        w = np.array([V @ disp(p) for p in pts])
        tris.append((w, uv, tref, V @ R @ n))
    S = size * ss

    def project(p):
        return (S / 2 + p[0] * S, S / 2 - p[1] * S, 10 - p[2])
    bg = np.zeros((S, S, 3)) + np.array([139, 106, 70])
    col, _ = rasterize(tris, S, S, project, bg=bg)
    return np.clip(col.reshape(size, ss, size, ss, 3).mean(axis=(1, 3)), 0, 255).astype(np.uint8)


def showcase(model_ref, W=1400, H=560, yaw=-28.0, pitch=18.0, ss=2):
    """A big 3/4 view of the raw model (barrel to the left, seen from the front-left and above), fitted to the image."""
    m = load_model(model_ref)
    V = rx(math.radians(pitch)) @ ry(math.radians(yaw))
    tris = []
    for pts, uv, tref, n in model_triangles(m):
        tris.append((np.array([V @ (p - 8) for p in pts]), uv, tref, V @ n))
    allp = np.concatenate([t[0] for t in tris])
    lo, hi = allp.min(axis=0), allp.max(axis=0)
    Ws, Hs = W * ss, H * ss
    sc = min((Ws - 60) / (hi[0] - lo[0]), (Hs - 60) / (hi[1] - lo[1]))
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2

    def project(p):
        return (Ws / 2 + (p[0] - cx) * sc, Hs / 2 - (p[1] - cy) * sc, 100 - p[2])
    bg = np.zeros((Hs, Ws, 3)) + np.array([168, 178, 188])
    col, _ = rasterize(tris, Ws, Hs, project, bg=bg)
    return np.clip(col.reshape(H, ss, W, ss, 3).mean(axis=(1, 3)), 0, 255).astype(np.uint8)


def sheet(gid, out):
    hip = first_person('airdefense:item/' + gid)
    aim = first_person('airdefense:item/' + gid + '_aim')
    frame = ortho('airdefense:item/' + gid, size=240)
    icon = np.asarray(Image.open(os.path.join(ASSETS, 'airdefense', 'textures', 'item', gid + '.png')).convert('RGBA'))
    icon = np.kron(icon, np.ones((6, 6, 1), dtype=np.uint8))
    H = hip.shape[0]
    W = hip.shape[1] * 2 + 250
    img = np.zeros((H, W, 3), dtype=np.uint8) + 40
    img[:, :hip.shape[1]] = hip
    img[:, hip.shape[1]:hip.shape[1] * 2] = aim
    x0 = hip.shape[1] * 2 + 5
    img[5:245, x0:x0 + 240] = frame
    a = icon[..., 3:4] / 255.0
    region = img[260:260 + icon.shape[0], x0 + 24:x0 + 24 + icon.shape[1]].astype(np.float64)
    img[260:260 + icon.shape[0], x0 + 24:x0 + 24 + icon.shape[1]] = (region * (1 - a) + icon[..., :3] * a).astype(np.uint8)
    Image.fromarray(img).save(out)


if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for gid in sys.argv[2:]:
        sheet(gid, os.path.join(out, gid + '.png'))
        a = showcase('airdefense:item/' + gid)
        b = showcase('airdefense:item/' + gid, yaw=152.0, pitch=12.0)
        Image.fromarray(np.concatenate([a, b], axis=0)).save(os.path.join(out, gid + '_3d.png'))
        print(gid)
