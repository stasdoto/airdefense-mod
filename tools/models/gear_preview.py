#!/usr/bin/env python3
"""A quick look at the gear on a person, without the game: python3 tools/models/gear_preview.py out_dir"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import boxgen  # noqa: E402
import gear  # noqa: E402
import preview  # noqa: E402

boxgen.STYLES['skin'] = dict(kind='plain', base=0xC69C7A)
boxgen.STYLES['shirt'] = dict(kind='plain', base=0x56603E)
boxgen.STYLES['eye'] = dict(kind='plain', base=0x2A2A40)


class Scene:
    def __init__(self):
        self.m = boxgen.Model('scene')
        self.roots = []

    def person(self, head_y=12.0):
        p = self.m.part('person')
        g = gear.G(self.m, p)
        g.box(-4, 4, 12, 20, -4, 4, 'skin')
        g.box(-3, -1, 15, 16, 4, 4.05, 'eye', faces=('front',))
        g.box(1, 3, 15, 16, 4, 4.05, 'eye', faces=('front',))
        g.box(-4, 4, 0, 12, -2, 2, 'shirt')
        g.box(-8, -4, 0, 12, -2, 2, 'shirt')
        g.box(4, 8, 0, 12, -2, 2, 'shirt')
        g.box(-4, 0, -12, 0, -2, 2, 'shirt')
        g.box(0, 4, -12, 0, -2, 2, 'shirt')
        self.roots.append(p)

    def add(self, model, dx=0.0, dy=0.0, dz=0.0, yaw=0):
        """Moves a gear model's boxes into place (pixels); yaw only 0 or 180."""
        for part in model.parts:
            part.pivot = tuple(part.pivot[i] + (dx, dy, dz)[i] * gear.P for i in range(3))
            for b in part.boxes:
                lo = list(b.lo)
                hi = list(b.hi)
                for i, d in enumerate((dx, dy, dz)):
                    lo[i] += d * gear.P
                    hi[i] += d * gear.P
                b.lo, b.hi = tuple(lo), tuple(hi)
        if yaw == 180:
            for part in model.parts:
                part.pivot = (-part.pivot[0] + 2 * dx * gear.P, part.pivot[1], -part.pivot[2] + 2 * dz * gear.P)
                for b in part.boxes:
                    lo = (-b.hi[0] + 2 * dx * gear.P, b.lo[1], -b.hi[2] + 2 * dz * gear.P)
                    hi = (-b.lo[0] + 2 * dx * gear.P, b.hi[1], -b.lo[2] + 2 * dz * gear.P)
                    b.lo, b.hi = lo, hi
                    swap = {'front': 'back', 'back': 'front', 'left': 'right', 'right': 'left'}
                    b.faces = tuple(swap.get(f, f) for f in b.faces)
                    b.sides = {swap.get(k, k): v for k, v in b.sides.items()}
                part.rot = (-part.rot[0], part.rot[1], -part.rot[2])
        self.roots += model.roots


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    models = {m.id: m for m in gear.all_models()}
    shots = []
    for helmet, vest, cloth in (('helmet_6b47', 'vest_6b45', 'emr'), ('helmet_fast_nvg', 'vest_pc', 'mc'), ('helmet_fast', 'vest_pc', 'mc')):
        for yaw, pitch in ((30, 12), (150, 15)):
            s = Scene()
            s.person()
            fresh = {m.id: m for m in gear.all_models()}
            s.add(fresh[helmet], 0, 12, 0)
            s.add(fresh[vest], 0, 12, 0)
            slots = gear.HEAVY_SLOTS if vest == 'vest_6b45' else gear.PC_SLOTS
            kinds = ['mag', 'mag', 'grenade', 'medkit', 'radio', 'mag']
            for (x, y, z, turn), kind in zip(slots, kinds):
                pm = {m.id: m for m in gear.all_models()}['pouch_%s_%s' % (kind, cloth)]
                s.add(pm, x, 12 + y, z, yaw=turn)
            fake = type('M', (), {})()
            fake.roots = s.roots
            path = os.path.join(out, '%s_%d.png' % (helmet, yaw))
            preview.render(fake, path, yaw, pitch, {}, size=(600, 700))
            shots.append(path)
    ims = [Image.open(p).resize((300, 350)) for p in shots]
    W = Image.new('RGB', (600, 350 * ((len(ims) + 1) // 2)))
    for i, im in enumerate(ims):
        W.paste(im, ((i % 2) * 300, (i // 2) * 350))
    W.save(os.path.join(out, 'gear_sheet.png'))


if __name__ == '__main__':
    main()
