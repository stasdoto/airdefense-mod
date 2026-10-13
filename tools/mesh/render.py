"""
2.0: a quick look at a baked mesh without the game - a small software renderer lit the way Minecraft lights entities
(two lamps from above, 40% ambient), the texture sampled nearest like the game does.
python3 tools/mesh/render.py <model id> [out.png]
"""
import math

import numpy as np
from PIL import Image

from core import normalize

L0 = normalize(np.array([0.2, 1.0, -0.7]))
L1 = normalize(np.array([-0.2, 1.0, 0.7]))


def look_at(eye, target, up=(0, 1, 0)):
    eye = np.asarray(eye, dtype=np.float64)
    f = normalize(np.asarray(target, dtype=np.float64) - eye)
    r = normalize(np.cross(f, up))
    u = np.cross(r, f)
    return eye, r, u, f


def triangles(flat, W, H, anim=None):
    """World triangles (rest pose) with normals, texel uvs, corner colours and layer."""
    tris = []
    for p in flat.polys:
        k = len(p['w'])
        tx = p['tex']
        col = p.get('vao')
        # Shown as the game shows it: our space (x right, z forward) is a mirror of a right-handed one.
        M = np.array([-1.0, 1.0, 1.0])
        for i in range(1, k - 1):
            idx = [0, i + 1, i]
            c = np.ones(3) if col is None else np.array([0.42 + 0.58 * col[j] ** 1.1 for j in idx])
            tris.append((p['w'][idx] * M, p['wn'][idx] * M, tx[idx], c, p['layer'], p['mat']))
    return tris


def render(flat, img, W, H, eye, target, size=(960, 600), fov=40, ss=2, bg=True, tint=None):
    w, h = size[0] * ss, size[1] * ss
    eye, r, u, f = look_at(eye, target)
    tanf = math.tan(math.radians(fov) / 2)
    color = np.zeros((h, w, 3))
    if bg:
        yy = np.linspace(0, 1, h)[:, None]
        color[:] = (np.array([124, 166, 222]) * (1 - yy) + np.array([196, 214, 236]) * yy)[:, None, :] * np.ones((1, w, 1))
    depth = np.full((h, w), np.inf)
    tex = img.astype(np.float64)
    TH, TW = tex.shape[:2]
    tris = triangles(flat, W, H)
    # A ground plane.
    if bg:
        ground = []
        G = 30
        for gx in range(-G, G, 2):
            for gz in range(-G, G, 2):
                ground.append((gx, gz))

    def project(P):
        d = P - eye
        x = d @ r
        y = d @ u
        z = d @ f
        sx = (x / (z * tanf) * (h / 2)) + w / 2
        sy = h / 2 - (y / (z * tanf) * (h / 2))
        return np.stack([sx, sy], axis=1), z

    def draw(Ps, Ns, UVs, Cs, alpha_mode, flatcol=None):
        S, Z = project(Ps)
        if (Z <= 0.05).any():
            return
        a, b, c = S
        den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if den >= 0:  # back face (our triangles wind counter-clockwise from outside; screen y is down)
            return
        x0 = max(0, int(math.floor(min(a[0], b[0], c[0]))))
        x1 = min(w, int(math.ceil(max(a[0], b[0], c[0]))) + 1)
        y0 = max(0, int(math.floor(min(a[1], b[1], c[1]))))
        y1 = min(h, int(math.ceil(max(a[1], b[1], c[1]))) + 1)
        if x1 <= x0 or y1 <= y0:
            return
        xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        l0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
        l1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
        l2 = 1 - l0 - l1
        m = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
        if not m.any():
            return
        # Perspective-correct interpolation.
        iz = np.array([1 / Z[0], 1 / Z[1], 1 / Z[2]])
        wsum = l0 * iz[0] + l1 * iz[1] + l2 * iz[2]
        zz = 1 / wsum
        sub_d = depth[y0:y1, x0:x1]
        vis = m & (zz < sub_d)
        if not vis.any():
            return
        k0 = l0 * iz[0] * zz
        k1 = l1 * iz[1] * zz
        k2 = l2 * iz[2] * zz
        if flatcol is not None:
            col = np.broadcast_to(flatcol, vis.shape + (3,))[vis]
            al = None
        else:
            uu = k0 * UVs[0][0] + k1 * UVs[1][0] + k2 * UVs[2][0]
            vv = k0 * UVs[0][1] + k1 * UVs[1][1] + k2 * UVs[2][1]
            tx = np.clip(uu[vis].astype(int), 0, TW - 1)
            ty = np.clip(vv[vis].astype(int), 0, TH - 1)
            px = tex[ty, tx]
            col = px[:, :3].copy()
            al = px[:, 3] / 255.0
            cc = (k0 * Cs[0] + k1 * Cs[1] + k2 * Cs[2])[vis]
            col *= cc[:, None]
        nn = normalize((k0[..., None] * Ns[0] + k1[..., None] * Ns[1] + k2[..., None] * Ns[2])[vis])
        lit = np.minimum(1.0, 0.4 + 0.6 * (np.clip(nn @ L0, 0, None) + np.clip(nn @ L1, 0, None)))
        col = col * lit[:, None]
        sub_c = color[y0:y1, x0:x1]
        if alpha_mode == 'blend' and al is not None:
            sub_c[vis] = sub_c[vis] * (1 - al[:, None]) + col * al[:, None]
        else:
            if al is not None and alpha_mode == 'cutout':
                keep = al > 0.1
                idx = np.argwhere(vis)
                vis2 = np.zeros_like(vis)
                vis2[idx[keep][:, 0], idx[keep][:, 1]] = True
                vis = vis2
                col = col[keep]
            sub_c[vis] = col
            sub_d[vis] = zz[vis]

    if bg:
        for gx, gz in ground:
            P = np.array([[gx, 0, gz], [gx + 2, 0, gz], [gx + 2, 0, gz + 2], [gx, 0, gz + 2]], dtype=np.float64)
            shade = 0.92 if (gx + gz) // 2 % 2 == 0 else 1.0
            gc = np.array([110, 150, 80]) * shade
            for tri in ((0, 2, 1), (0, 3, 2)):
                draw(P[list(tri)], np.tile([0, 1, 0], (3, 1)), None, None, 'solid', flatcol=gc)
    later = []
    for Ps, Ns, UVs, Cs, layer, mat in tris:
        if layer == 'glass':
            later.append((Ps, Ns, UVs, Cs))
            continue
        draw(Ps, Ns, UVs, Cs, 'cutout')
    later.sort(key=lambda t: -np.linalg.norm(t[0].mean(axis=0) - eye))
    for Ps, Ns, UVs, Cs in later:
        draw(Ps, Ns, UVs, Cs, 'blend')
    out = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), 'RGB')
    return out.resize(size, Image.LANCZOS)


def views(flat, img, W, H, model, path, size=(640, 400)):
    """Four views side by side: front three-quarter, back three-quarter, side, a close look from above."""
    lo, hi = model.bounds()
    lo, hi = np.array([-hi[0], lo[1], lo[2]]), np.array([-lo[0], hi[1], hi[2]])
    c = (lo + hi) / 2
    L = float(max(hi - lo))
    d = L * 1.25
    eyes = [c + np.array([d * 0.75, L * 0.38, d * 0.85]), c + np.array([-d * 0.8, L * 0.45, -d * 0.75]),
            c + np.array([d * 1.15, L * 0.15, 0.0]), c + np.array([-d * 0.45, L * 0.75, d * 0.55])]
    ims = [render(flat, img, W, H, e, c, size) for e in eyes]
    out = Image.new('RGB', (size[0] * 2, size[1] * 2))
    for i, im in enumerate(ims):
        out.paste(im, ((i % 2) * size[0], (i // 2) * size[1]))
    out.save(path)
    return out
