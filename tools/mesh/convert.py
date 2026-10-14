"""
2.0: a general converter from a boxgen box model to a decent mesh model, for the vehicles nobody models by hand.

 - every part keeps its name, pivot, rotation and parent (so the game's animation works unchanged);
 - boxes become chamfered boxes (rounded edges catch the light), long thin boxes become cylinders (barrels, antennas,
   pipes), stepped slopes (rows of boxes that descend like stairs: glacis plates) become one sloped wedge;
 - wheels (m.wheels) become real tyres and rims, tank road wheels / sprockets / idlers and the track belts come from the
   tracked kit (m_tanks.running_gear);
 - the boxgen styles map to the mesh materials (paint / armour, glass, lights, rubber, mesh, ...).
Usage: convert.convert(box_model) -> core.Model
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import geom  # noqa: E402
import kit_tanks as K  # noqa: E402
import m_tanks as MT  # noqa: E402
from core import Geo, Model  # noqa: E402
from geom import cyl, panel  # noqa: E402

ARMOUR = {'camo', 'rgreen', 'ugreen', 'nato', 'olive', 'tan', 'sand', 'grey', 'navy', 'visby', 'deck', 'dgrey', 'steel'}
MATS = {
    'glass': 'glass', 'window': 'glass', 'light': 'headlight', 'redlight': 'taillight', 'orange': 'indicator', 'black': 'black', 'dark': 'metal_dark',
    'dgrey': 'armour_dark', 'steel': 'steel_worn', 'rust': 'rust', 'canvas': 'canvas', 'wood': 'wood', 'tire': 'tyre', 'track': 'rubber', 'rubber': 'rubber',
    'mesh': 'grille_mesh', 'mesh_dark': 'grille_mesh', 'mesh_fine': 'grille_mesh', 'white': 'white', 'radome': 'white', 'yellow': 'yellow', 'chrome': 'chrome',
    'beacon_blue': 'beacon_blue', 'beacon_red': 'beacon_red', 'screen': 'screen', 'lgrey': 'plastic', 'fgrey': 'plastic', 'fgrey_d': 'plastic',
    'aesa': 'metal_dark', 'aesa_light': 'steel_worn', 'roadwheel': 'armour',
}


def mat_for(style, armoured):
    if style in MATS:
        return MATS[style]
    if style in ARMOUR:
        return 'armour' if armoured else 'paint'
    return 'paint2' if style in ('car_white', 'white') else 'paint'


def _dims(b):
    return [b.hi[i] - b.lo[i] for i in range(3)]


def stair_groups(boxes):
    """Rows of boxes with the same x range and bottom, touching along z, whose tops step steadily: a stepped slope.
    Returns (groups [[box..]], used set)."""
    cand = {}
    for b in boxes:
        cand.setdefault((round(b.lo[0], 3), round(b.hi[0], 3), round(b.lo[1], 3), b.style), []).append(b)
    groups = []
    used = set()
    for key, bs in cand.items():
        if len(bs) < 3:
            continue
        bs = sorted(bs, key=lambda b: b.lo[2])
        run = [bs[0]]
        for b in bs[1:]:
            if abs(b.lo[2] - run[-1].hi[2]) < 0.02 and ((b.hi[1] < run[-1].hi[1] - 0.05 and run[-1].hi[1] < (run[-2].hi[1] if len(run) > 1 else 1e9) + 1e-6) or
                                                         (b.hi[1] > run[-1].hi[1] + 0.05 and (len(run) < 2 or run[-1].hi[1] > run[-2].hi[1]))):
                run.append(b)
            else:
                if len(run) >= 3:
                    groups.append(run)
                run = [b]
        if len(run) >= 3:
            groups.append(run)
    for g in groups:
        for b in g:
            used.add(id(b))
    return groups, used


def wedge(run, mat):
    """One sloped body for a stepped slope: a line through the tops of the steps."""
    zs = np.array([(b.lo[2] + b.hi[2]) / 2 for b in run])
    ys = np.array([b.hi[1] for b in run])
    a, c = np.polyfit(zs, ys, 1)
    z0, z1 = run[0].lo[2], run[-1].hi[2]
    x0, x1, yl = run[0].lo[0], run[0].hi[0], run[0].lo[1]
    ring = lambda z: [(x0, yl, z), (x1, yl, z), (x1, max(yl + 0.02, a * z + c), z), (x0, max(yl + 0.02, a * z + c), z)]
    g = geom.loft([ring(z0), ring(z1)], mat, closed=True, cap0=True, cap1=True, smooth=20)
    K._orient(g, [ring(z0), ring(z1)])
    return g


def box_geo(b, armoured):
    mat = mat_for(b.style, armoured)
    dx, dy, dz = _dims(b)
    lo = np.array(b.lo)
    hi = np.array(b.hi)
    d = sorted((dx, dy, dz))
    ctr = (lo + hi) / 2
    # Long thin: a cylinder.
    if d[2] >= 5 * d[1] and d[1] <= 0.3 and d[1] <= 1.8 * d[0] + 1e-9 and b.style not in ('glass',):
        ax = int(np.argmax([dx, dy, dz]))
        r = (d[0] + d[1]) / 4
        a = ctr.copy()
        c = ctr.copy()
        a[ax] = lo[ax]
        c[ax] = hi[ax]
        return cyl(a, c, max(r, 0.012), mat, n=8 if r < 0.06 else 12)
    if b.style in ('mesh', 'mesh_dark', 'mesh_fine') and d[0] < 0.08:
        return K.chbox(lo, hi, mat, 0.004)
    if b.style in ('glass', 'light', 'redlight', 'screen', 'orange', 'beacon_blue', 'beacon_red') or d[0] < 0.05:
        return K.chbox(lo, hi, mat, min(0.008, d[0] * 0.3))
    c = min(0.06, d[0] * 0.18) if d[0] > 0.25 else min(0.03, d[0] * 0.18)
    return K.chbox(lo, hi, mat, c)


def convert(bm, density=22, tex_width=1024, armoured=None, seed=None):
    if armoured is None:
        armoured = bm.paint in ARMOUR
    m = Model(bm.id, K.scheme_for(bm.paint, seed=(seed or sum(map(ord, bm.id)) % 97 + 1)), density=density, tex_width=tex_width)
    m.box = bm
    wheels = {n: (r, steer) for n, r, steer in bm.wheels}
    tracked = any(n.startswith('road_') for n in wheels)
    made = {}
    # Parts in the box model's order (parents first).
    for p in bm.parts:
        if p.name.endswith(('_d1', '_d2')) and p.parent is not None and p.parent.name in wheels:
            continue
        if tracked and (p.name in wheels or (p.parent is not None and p.parent.name in wheels)):
            continue
        parent = made.get(p.parent.name) if p.parent is not None else None
        mp = m.part(p.name, p.pivot, p.rot, parent)
        made[p.name] = mp
        if p.name in wheels:
            r, steer = wheels[p.name]
            if tracked:
                mp.spin = r
                continue  # the running gear below builds it
            hw = max(b.hi[0] - b.lo[0] for b in p.boxes[:1] + p.boxes[2:3]) if p.boxes else 0.4
            hw = max(0.2, min(hw, 0.6))
            side = -1 if p.pivot[0] < 0 else 1
            mp.add(geom.wheel(p.pivot, side, r, hw, 0.6, 'steel' if armoured else 'alloy', n=24))
            mp.spin = r
            mp.steer = steer
            continue
        if tracked and p.name.startswith(('road_', 'sprocket_', 'idler_')):
            continue
        groups, used = stair_groups(p.boxes)
        g = Geo(30)
        for run in groups:
            g.extend(wedge(run, mat_for(run[0].style, armoured)))
        for b in p.boxes:
            if id(b) in used:
                continue
            if b.style == 'track' and tracked:
                continue
            g.extend(box_geo(b, armoured))
        mp.add(g)
    if tracked:
        roads = sorted([bpz for bpz in (MT.bpart(bm, n) for n in wheels if n.startswith('road_') and n.endswith('_l'))], key=lambda q: q.pivot[2])
        z0, z1 = roads[0].pivot[2], roads[-1].pivot[2]
        spr = [MT.bpart(bm, n) for n in wheels if n.startswith('sprocket_') and n.endswith('_l')]
        drive = 'front' if (spr and spr[0].pivot[2] > 0) else 'rear'
        top = roads[0].pivot[1] + wheels[roads[0].name][0] + 0.12
        rl = [(float(z), top, 0.08) for z in np.linspace(z0 + 0.6, z1 - 0.6, 3)]
        # Parts for the wheels exist in the model already: remove the ones running_gear makes itself.
        MT.running_gear(m, bm, MT.Gear(rollers=rl, drive=drive, wheel_kind='single', pitch=0.15, thick=0.06))
    return m

