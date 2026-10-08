#!/usr/bin/env python3
"""
What a crew member sees from his seat (1.26): a perspective picture from the seat's eye (hip + 1.02 m), the outer
model (back faces culled as in the game, glass see-through) and its inside together.

python3 tools/models/cockpit_view.py out_dir model_id [seat_index] [yaw] [pitch]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import boxgen  # noqa: E402
import build  # noqa: E402
import interior  # noqa: E402
from preview import rot_mc, S  # noqa: E402

NORMALS = {'top': (0, 1, 0), 'bottom': (0, -1, 0), 'front': (0, 0, 1), 'back': (0, 0, -1), 'right': (1, 0, 0), 'left': (-1, 0, 0)}
QUADS = {'top': (2, 3, 7, 6), 'bottom': (0, 1, 5, 4), 'front': (1, 3, 7, 5), 'back': (0, 2, 6, 4), 'right': (4, 5, 7, 6), 'left': (0, 1, 3, 2)}


def faces_of(m, anim=None):
    anim = anim or {}
    out = []

    def walk(p, R_parent, t_parent, parent_pivot):
        rx, ry, rz = (math.radians(a) for a in p.rot)
        a = anim.get(p.name)
        if a:
            rx += math.radians(a[0])
            ry += math.radians(a[1])
            rz += math.radians(a[2])
        R_local = S @ rot_mc(rx, ry, rz) @ S
        t = t_parent + R_parent @ (np.array(p.pivot) - np.array(parent_pivot))
        R = R_parent @ R_local
        for b in p.boxes:
            lo = np.array(b.lo) - np.array(p.pivot)
            hi = np.array(b.hi) - np.array(p.pivot)
            c = [np.array([x, y, z]) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
            w = [t + R @ v for v in c]
            for face in b.faces:
                n = R @ np.array(NORMALS[face], dtype=float)
                st = boxgen.STYLES[b.sides.get(face, b.style)]
                out.append(([w[i] for i in QUADS[face]], n, st, face))
        for ch in p.children:
            walk(ch, R, t, p.pivot)

    for r in m.roots:
        walk(r, np.eye(3), np.zeros(3), (0, 0, 0))
    return out


def clip_near(poly, near=0.05):
    """Sutherland-Hodgman against z >= near (camera space)."""
    out = []
    n = len(poly)
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        ina, inb = a[2] >= near, b[2] >= near
        if ina:
            out.append(a)
        if ina != inb:
            t = (near - a[2]) / (b[2] - a[2])
            out.append(a + (b - a) * t)
    return out


def render(m, im, seat_i, path, yaw=0.0, pitch=-8.0, fov=75, size=(960, 540)):
    role, sx, sy, sz = m.seats[seat_i]
    eye = np.array([sx, sy + interior.EYE, sz])
    ya, pa = math.radians(yaw), math.radians(pitch)
    fwd = np.array([math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)])
    right = np.cross([0, 1, 0], fwd)
    right /= np.linalg.norm(right)
    up = np.cross(fwd, right)
    W, H = size
    f = (H / 2) / math.tan(math.radians(fov) / 2)
    faces = faces_of(m) + (faces_of(im) if im is not None else [])
    light = np.array([0.35, 0.85, 0.4])
    light /= np.linalg.norm(light)
    items = []
    for quad, n, st, face in faces:
        q = np.array(quad)
        c = q.mean(axis=0)
        if n @ (c - eye) >= 0:
            continue  # back face: culled, as in the game
        cam = np.array([[(v - eye) @ right, (v - eye) @ up, (v - eye) @ fwd] for v in q])
        cl = clip_near(list(cam))
        if len(cl) < 3:
            continue
        depth = float(np.mean([v[2] for v in cl]))
        pts = [(W / 2 + v[0] / v[2] * f, H / 2 - v[1] / v[2] * f) for v in cl]
        k = 0.6 + 0.4 * max(0.0, float(n @ light))
        if st.get('flat'):
            k = 1.0
        col = tuple(int(min(255, v * k)) for v in boxgen.rgb(st['base']))
        alpha = st.get('alpha', 255) if st['kind'] == 'glass' else 255
        items.append((depth, pts, col, alpha, st['kind']))
    items.sort(key=lambda it: -it[0])
    img = Image.new('RGBA', size, (150, 180, 210, 255))
    d = ImageDraw.Draw(img)
    # Ground below the horizon.
    hy = H / 2 + math.tan(pa) * f
    d.rectangle((0, hy, W, H), fill=(96, 120, 70, 255))
    for depth, pts, col, alpha, kind in items:
        if alpha >= 255:
            if kind == 'mesh':
                d.polygon(pts, outline=col + (255,))
            else:
                d.polygon(pts, fill=col + (255,), outline=tuple(int(v * 0.75) for v in col) + (255,))
        else:
            layer = Image.new('RGBA', size, (0, 0, 0, 0))
            ImageDraw.Draw(layer).polygon(pts, fill=col + (alpha,))
            img = Image.alpha_composite(img, layer)
            d = ImageDraw.Draw(img)
    img.convert('RGB').save(path)


if __name__ == '__main__':
    out = sys.argv[1]
    mid = sys.argv[2]
    seat_i = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    yaw = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
    pitch = float(sys.argv[5]) if len(sys.argv) > 5 else -8.0
    os.makedirs(out, exist_ok=True)
    m = next(x for x in build.models() if x.id == mid)
    im = interior.build(m) if m.cabs else None
    render(m, im, seat_i, os.path.join(out, '%s_seat%d_%d.png' % (mid, seat_i, int(yaw))), yaw, pitch)
    print('ok')
