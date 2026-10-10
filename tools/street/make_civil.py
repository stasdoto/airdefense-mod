#!/usr/bin/env python3
"""1.41: the foot of a tower crane on a building site (a concrete footing with the mast's bolts): the crane itself is
drawn by the client. python3 tools/street/make_civil.py"""
import json
import os

import make_street as ms
from make_street import box, model, ROOT


def crane_base():
    tex = {'concrete': 'minecraft:block/light_gray_concrete'}
    e = [box((0, 0, 0), (16, 8, 16), 'concrete')]
    for x in (2, 12):
        for z in (2, 12):
            e.append(box((x, 8, z), (x + 2, 10, z + 2), 'steel'))
    model('crane_base', e, gui=0.6, extra_tex=tex)


def blockstates():
    bs = os.path.join(ROOT, 'blockstates')
    items = os.path.join(ROOT, 'items')
    m = 'airdefense:block/street/crane_base'
    v = {'facing=%s' % f: {'model': m} for f in ms.FACING_Y}
    with open(os.path.join(bs, 'crane_base.json'), 'w') as fh:
        json.dump({'variants': v}, fh, indent=1)
    with open(os.path.join(items, 'crane_base.json'), 'w') as fh:
        json.dump({'model': {'type': 'minecraft:model', 'model': m}}, fh, indent=1)


if __name__ == '__main__':
    crane_base()
    blockstates()
    print('ok')
