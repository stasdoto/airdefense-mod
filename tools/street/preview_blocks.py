#!/usr/bin/env python3
"""Quick look at the street furniture's block models without the game: flat-shaded faces (each face takes its
texture's average colour, lit by the face's direction), painter's order. Assemblies (a pole with a lamp on top) are
lists of (model, dx, dy, dz, turn). python3 tools/street/preview_blocks.py out.png"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense')
CACHE = {}


def tex_color(ref):
    if ref in CACHE:
        return CACHE[ref]
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    if ns == 'minecraft':
        c = np.array([60, 62, 66, 255.0])
    else:
        img = np.array(Image.open(os.path.join(ROOT, 'textures', path + '.png')).convert('RGBA')).astype(float)
        if img.shape[0] > img.shape[1]:
            img = img[img.shape[1]:img.shape[1] * 2]
        a = img[..., 3:4] / 255
        c = np.concatenate([(img[..., :3] * a).sum((0, 1)) / max(1e-3, a.sum()), [img[..., 3].mean()]])
    CACHE[ref] = c
    return c


def rot_point(p, rot):
    if not rot:
        return p
    o = np.array(rot['origin'], float)
    a = math.radians(rot['angle'])
    q = p - o
    c, s = math.cos(a), math.sin(a)
    if rot['axis'] == 'x':
        q = np.array([q[0], q[1] * c - q[2] * s, q[1] * s + q[2] * c])
    elif rot['axis'] == 'y':
        q = np.array([q[0] * c + q[2] * s, q[1], -q[0] * s + q[2] * c])
    else:
        q = np.array([q[0] * c - q[1] * s, q[0] * s + q[1] * c, q[2]])
    return q + o


FACE_CORNERS = {
    'north': [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], 'south': [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
    'west': [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], 'east': [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],
    'down': [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], 'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
}
LIGHT = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.65, 'west': 0.65}


def faces_of(name, dx, dy, dz, turn):
    m = json.load(open(os.path.join(ROOT, 'models', 'block', 'street', name + '.json')))
    tex = m['textures']
    out = []
    for e in m['elements']:
        f0 = np.array(e['from'], float)
        f1 = np.array(e['to'], float)
        emit = e.get('light_emission', 0)
        for face, spec in e['faces'].items():
            ref = spec['texture'][1:]
            while ref in tex and not ':' in tex[ref] and tex[ref].startswith('#'):
                ref = tex[ref][1:]
            col = tex_color(tex.get(ref, ref))
            if col[3] < 20:
                continue
            pts = []
            for c in FACE_CORNERS[face]:
                p = np.array([f1[i] if c[i] else f0[i] for i in range(3)])
                p = rot_point(p, e.get('rotation'))
                # Turn round the block's centre (y rotation like a blockstate: clockwise seen from above).
                q = p - np.array([8, 0, 8])
                for _ in range(turn // 90):
                    q = np.array([-q[2], q[1], q[0]])
                p = q + np.array([8, 0, 8]) + np.array([dx, dy, dz]) * 16
                pts.append(p)
            shade = 1.0 if emit else LIGHT[face]
            out.append((pts, col, shade))
    return out


def render(assembly, size=260, yaw=35, pitch=28, scale=None):
    faces = []
    for part in assembly:
        faces += faces_of(*part)
    allp = np.array([p for f in faces for p in f[0]])
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, y, z = p - np.array([8, 8, 8])
        x2 = x * cy - z * sy
        z2 = x * sy + z * cy
        y2 = y * cp - z2 * sp
        d = y * sp + z2 * cp
        return x2, -y2, d
    pr = [[proj(p) for p in f[0]] for f in faces]
    xs = [q[0] for f in pr for q in f]
    ys = [q[1] for f in pr for q in f]
    span = max(max(xs) - min(xs), max(ys) - min(ys), 16)
    k = scale or (size * 0.85 / span)
    ox = size / 2 - (max(xs) + min(xs)) / 2 * k
    oy = size / 2 - (max(ys) + min(ys)) / 2 * k
    img = Image.new('RGB', (size, size), (150, 175, 205))
    d = ImageDraw.Draw(img)
    order = sorted(range(len(faces)), key=lambda i: -sum(q[2] for q in pr[i]) / 4)
    for i in order:
        col = faces[i][1]
        sh = faces[i][2]
        rgb = tuple(int(min(255, c * sh)) for c in col[:3])
        poly = [(ox + q[0] * k, oy + q[1] * k) for q in pr[i]]
        d.polygon(poly, fill=rgb, outline=tuple(int(c * 0.7) for c in rgb))
    return img


SETS = {
    'lamps': [
        [('pole_steel_bottom', 0, 0, 0, 0), ('pole_steel', 0, 1, 0, 0), ('pole_steel', 0, 2, 0, 0), ('pole_steel', 0, 3, 0, 0), ('lamp_modern', 0, 4, 0, 0)],
        [('pole_concrete_bottom', 0, 0, 0, 0), ('pole_concrete', 0, 1, 0, 0), ('pole_concrete', 0, 2, 0, 0), ('pole_concrete', 0, 3, 0, 0), ('lamp_cobra', 0, 4, 0, 0)],
        [('pole_green_bottom', 0, 0, 0, 0), ('pole_green', 0, 1, 0, 0), ('pole_green', 0, 2, 0, 0), ('lamp_lantern', 0, 3, 0, 0)],
        [('pole_black_bottom', 0, 0, 0, 0), ('pole_black', 0, 1, 0, 0), ('lamp_globe', 0, 2, 0, 0)],
        [('pole_black_bottom', 0, 0, 0, 0), ('pole_black', 0, 1, 0, 0), ('pole_black', 0, 2, 0, 0), ('traffic_light', 0, 3, 0, 0)],
        [('pole_steel_bottom', 0, 0, 0, 0), ('pole_steel', 0, 1, 0, 0), ('sign_crossing', 0, 2, 0, 0)],
    ],
    'signs': [[('sign_' + k, 0, 0, 0, 0)] for k in ('stop', 'give_way', 'crossing', 'no_parking', 'speed', 'main_road', 'bus')],
    'things': [[(n, 0, 0, 0, 0)] for n in ('bench_park', 'bench_soviet', 'bench_modern', 'bin_soviet', 'bin_modern', 'bin_euro', 'hydrant',
                                          'mailbox_us', 'mailbox_euro', 'mailbox_soviet', 'bollard', 'planter', 'bike_rack', 'manhole')],
    'big': [[(n, 0, 0, 0, 0)] for n in ('bus_stop_modern', 'bus_stop_soviet', 'booth_red', 'booth_soviet', 'advert_column', 'kiosk', 'vending',
                                        'billboard')],
}

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'street_preview.png'
    rows = []
    for name, items in SETS.items():
        tiles = [render(a, yaw=215 if name != 'signs' else 200) for a in items]
        row = Image.new('RGB', (260 * len(tiles), 260))
        for i, t in enumerate(tiles):
            row.paste(t, (i * 260, 0))
        rows.append(row)
    w = max(r.width for r in rows)
    img = Image.new('RGB', (w, 260 * len(rows)), (30, 30, 30))
    for j, r in enumerate(rows):
        img.paste(r, (0, j * 260))
    img.save(out)
    print(out, img.size)
