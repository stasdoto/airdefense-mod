#!/usr/bin/env python3
"""Quick look at generated models without the game: flat-shaded boxes, painter's algorithm.
python3 tools/models/preview.py out_dir [model ids...]  (spin = angle of the spinner, elev = elevator angle)"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import boxgen  # noqa: E402
import build  # noqa: E402

S = np.diag([1.0, -1.0, 1.0])


def rot_mc(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def world_faces(m, anim):
    faces = []

    def walk(p, R_parent, t_parent, parent_pivot):
        rx, ry, rz = (math.radians(a) for a in p.rot)
        a = anim.get(p.name)
        if a:
            rx += math.radians(a[0])
            ry += math.radians(a[1])
            rz += math.radians(a[2])
        R_local = S @ rot_mc(rx, ry, rz) @ S
        offset = np.array(p.pivot) - np.array(parent_pivot)
        t = t_parent + R_parent @ offset
        R = R_parent @ R_local
        for b in p.boxes:
            lo = np.array(b.lo) - np.array(p.pivot)
            hi = np.array(b.hi) - np.array(p.pivot)
            c = [np.array([x, y, z]) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
            w = [t + R @ v for v in c]
            # corner index: x*4 + y*2 + z
            quads = {
                'top': (2, 3, 7, 6), 'bottom': (0, 1, 5, 4), 'front': (1, 3, 7, 5), 'back': (0, 2, 6, 4),
                'right': (4, 5, 7, 6), 'left': (0, 1, 3, 2),
            }
            for face in b.faces:
                st = boxgen.STYLES[b.sides.get(face, b.style)]
                faces.append(([w[i] for i in quads[face]], st, face))
        for ch in p.children:
            walk(ch, R, t, p.pivot)

    for r in m.roots:
        walk(r, np.eye(3), np.zeros(3), (0, 0, 0))
    return faces


def render(m, path, yaw=35, pitch=25, anim=None, size=(900, 600)):
    faces = world_faces(m, anim or {})
    ya, pa = math.radians(yaw), math.radians(pitch)
    # Camera looks at the model from the front-right, above.
    view = np.array([math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)])
    right = np.cross(view, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, view)
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    pts = np.array([v for q, _, _ in faces for v in q])
    proj = np.stack([pts @ right, pts @ up], axis=1)
    lo, hi = proj.min(axis=0), proj.max(axis=0)
    W, H = size
    scale = min((W - 40) / (hi[0] - lo[0]), (H - 40) / (hi[1] - lo[1]))
    img = Image.new('RGB', size, (150, 170, 190))
    d = ImageDraw.Draw(img)
    # Ground grid.
    order = sorted(faces, key=lambda f: float(np.mean([v @ view for v in f[0]])))
    for quad, st, face in order:
        q = np.array(quad)
        n = np.cross(q[1] - q[0], q[3] - q[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n /= nl
        if n @ view < 0:
            n = -n
        k = 0.55 + 0.45 * max(0.0, float(n @ light))
        c = boxgen.rgb(st['base'])
        col = tuple(int(min(255, v * k)) for v in c)
        poly = [(20 + (v @ right - lo[0]) * scale, H - 20 - (v @ up - lo[1]) * scale) for v in q]
        if st['kind'] == 'mesh':
            d.polygon(poly, outline=col)
        else:
            d.polygon(poly, fill=col, outline=tuple(int(v * 0.7) for v in col))
    img.save(path)


def sheet(out, ids=None):
    import glob
    fs = []
    for m in build.models():
        if ids and m.id not in ids:
            continue
        fs += [os.path.join(out, m.id + '_a.png'), os.path.join(out, m.id + '_b.png')]
    ims = [Image.open(f).resize((450, 300)) for f in fs]
    W = Image.new('RGB', (900, 300 * ((len(ims) + 1) // 2)))
    for i, im in enumerate(ims):
        W.paste(im, ((i % 2) * 450, (i // 2) * 300))
    W.save(os.path.join(out, 'sheet.png'))


if __name__ == '__main__':
    out = sys.argv[1]
    ids = sys.argv[2:]
    os.makedirs(out, exist_ok=True)
    for m in build.models():
        if ids and m.id not in ids:
            continue
        anim = {}
        if m.elevator:
            anim[m.elevator] = (m.fixed_elevation, 0, 0)
        render(m, os.path.join(out, m.id + '_a.png'), 35, 22, dict(anim))
        if m.elevator:
            anim[m.elevator] = (m.deploy_elevation, 0, 0)
        if m.turret:
            anim[m.turret] = (0, 35, 0)
        if m.spinner:
            anim[m.spinner] = (0, 60, 0)
        render(m, os.path.join(out, m.id + '_b.png'), -130, 18, anim)
        print(m.id)
    sheet(out, ids)

