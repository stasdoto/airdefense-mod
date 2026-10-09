#!/usr/bin/env python3
"""
1.27 "Gear": helmets, vests and pouches drawn on people in 3D (client/gear/GearLayer.java).

Written in the person's own pixels (1/16 block):
- head frame: x to the person's right, y up from the neck, z forward (the face); the head is x -4..4, y 0..8, z -4..4,
  the hat layer half a pixel bigger;
- body frame: the neck at 0, the body x -4..4, y -12..0, z -2..2 (the chest at +2);
- a pouch: its back against the vest at z = 0, standing on y = 0, centred on x = 0.
Built at 64 texels per metre (4 per pixel) and drawn at a quarter of the size, so the cloth is finely drawn.

python3 tools/models/gear.py  (writes client/gear/GenGear.java and textures/entity/gear/*.png)
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import boxgen  # noqa: E402
from boxgen import Model, STYLES  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
JAVA = os.path.join(ROOT, 'src/client/java/com/stasdoto/airdefense/client/gear/GenGear.java')
TEX = os.path.join(ROOT, 'src/main/resources/assets/airdefense/textures/entity/gear')
ICONS = os.path.join(ROOT, 'src/main/resources/assets/airdefense/textures/item')

P = 1.0 / 16
GEAR_PX = 64

# Russian digital (EMR "tsifra"), Multicam-like blobs, coyote, ranger green, black polymer.
EMR = dict(camo='pixel', base=0x5F6747, colors=(0x46553A, 0x7F7D5C, 0x2C3324), cell=2, blob=3)
MC = dict(camo='blobs', base=0x9C8A62, colors=(0x6E6A45, 0x7B5C3E, 0xC4B48A, 0x3F3A2A), blob=6)
STYLES.update({
    'emr': dict(kind='pixel', **EMR),
    'emr_molle': dict(kind='molle', step=5, tape=2, gap=6, **EMR),
    'emr_velcro': dict(kind='velcro', base=0x56603F),
    'mc': dict(kind='multicam', **MC),
    'mc_molle': dict(kind='molle', step=5, tape=2, gap=6, **MC),
    'mc_velcro': dict(kind='velcro', base=0x8E7E58),
    'coyote': dict(kind='multicam', base=0x8B7651, grain=3.0),
    'coyote_cross': dict(kind='cross', base=0x8B7651, mark=0xB0201A),
    'ranger': dict(kind='multicam', base=0x4C5038, grain=2.5),
    'poly': dict(kind='plain', base=0x232425),
    'poly_dark': dict(kind='plain', base=0x161718),
    'rail': dict(kind='grid', base=0x2A2B2C, line=0x121314, step=3),
    'strap': dict(kind='multicam', base=0x2B2A24, grain=2.0),
    'strap_tan': dict(kind='multicam', base=0x6F5F42, grain=2.0),
    'rubber_edge': dict(kind='plain', base=0x1A1A1A),
    'lens_green': dict(kind='lens', base=0x141516, glass=0x1E4A36, glint=0x7DB89A),
    'lens_dark': dict(kind='lens', base=0x141516, glass=0x2A2E33, glint=0x8A96A3),
    'mag_ak': dict(kind='plain', base=0x6A3A20),
    'mag_black': dict(kind='plain', base=0x202122),
    'steel_dark': dict(kind='plain', base=0x4C5054),
    'olive_drab': dict(kind='plain', base=0x55583A),
    'tq_red': dict(kind='plain', base=0xA0221C),
    'id_patch': dict(kind='plain', base=0x2E3326),
    'crate_wood': dict(kind='track', base=0x4F5A34),
    'stencil': dict(kind='plain', base=0xC9B04A, flat=True),
})


class G:
    """A part with boxes given in the person's pixels."""

    def __init__(self, model, part):
        self.m = model
        self.p = part
        self.n = 0

    def box(self, x0, x1, y0, y1, z0, z1, style=None, faces=None, sides=None):
        self.p.box((x0 * P, y0 * P, z0 * P), (x1 * P, y1 * P, z1 * P), style, faces, sides)
        return self

    def child(self, name, pivot, rot=(0, 0, 0)):
        c = self.p.part(name, (pivot[0] * P, pivot[1] * P, pivot[2] * P), rot)
        return G(self.m, c)

    def uid(self, what):
        self.n += 1
        return '%s_%s%d' % (self.p.name, what, self.n)

    def slab(self, y0, y1, a, b, c, style, zc=0.0, xc=0.0, faces=None, sides=None):
        """An octagon prism: half widths a (x) and b (z), corners cut by c, from y0 to y1."""
        self.box(xc - a, xc + a, y0, y1, zc - b + c, zc + b - c, style, faces, sides)
        self.box(xc - a + c, xc + a - c, y0, y1, zc - b, zc + b, style, faces, sides)
        self.corners(y0, y1, a, b, c, style, zc, xc, faces, sides)
        return self

    def corners(self, y0, y1, a, b, c, style, zc=0.0, xc=0.0, faces=None, sides=None, only=None):
        """The four cut corners of an octagon (each a box turned 45 degrees about y)."""
        L = c * math.sqrt(2)
        d = c / math.sqrt(2) + 0.05
        for sx in (-1, 1):
            for sz in (-1, 1):
                if only and (sx, sz) not in only:
                    continue
                # The middle of the cut, and a point d further in.
                mx = xc + sx * (a - c / 2)
                mz = zc + sz * (b - c / 2)
                ix = mx - sx * d / 2 / math.sqrt(2)
                iz = mz - sz * d / 2 / math.sqrt(2)
                rot = 45 if sx * sz > 0 else -45
                k = self.child(self.uid('c'), (ix, 0, iz), (0, rot, 0))
                k.box(ix - L / 2, ix + L / 2, y0, y1, iz - d / 2, iz + d / 2, style, faces, sides)
        return self

    def tube(self, x, y, z0, z1, r, style, ends=None):
        """A round-ish tube along z (two crossed boxes), centred at (x, y)."""
        a = r * 0.92
        e = ends or (style, style)
        self.box(x - a, x + a, y - a, y + a, z0, z1, style, sides={'front': e[0], 'back': e[1]})
        # The same turned 45 degrees about the tube's axis, its ends a hair further in (no flicker where they cross).
        k = self.child(self.uid('t'), (x, y, (z0 + z1) / 2), (0, 0, 45))
        k.box(x - a, x + a, y - a, y + a, z0 + 0.03, z1 - 0.03, style, sides={'front': e[0], 'back': e[1]})
        return self


def gear_model(id, paint, tex_width=512, seed=5):
    m = Model(id, paint=paint, tex_width=tex_width, seed=seed)
    m.px = GEAR_PX
    return m


# ---------------------------------------------------------------------------------------------------------------
# Helmets


def dome(g, cover, zc=-0.25, low=5.3):
    """The helmet's crown: stacked octagons from the brow up, a little behind the middle of the head."""
    # A rounded profile: straight up to the brow line, then an ellipse to the top (thin layers where it curves most).
    A, B, mid, top = 5.25, 5.05, 6.4, 9.95
    ys = [low, mid, 7.0, 7.5, 7.95, 8.35, 8.7, 9.0, 9.25, 9.45, 9.62, 9.76, 9.88]
    for i in range(len(ys) - 1):
        y0, y1 = ys[i], ys[i + 1]
        t = max(0.0, ((y0 + y1) / 2 - mid) / (top - mid))
        k = math.sqrt(max(0.0, 1 - t * t))
        a, b = A * k, B * k
        g.slab(y0, y1, a, b, min(a, b) * 0.36, cover, zc=zc, sides={'bottom': 'rubber_edge'} if i == 0 else None)


def chin_strap(g, style='strap', z=0.9):
    for sx in (-1, 1):
        g.box(sx * 4.55, sx * 4.8, 0.3, 4.4, z - 0.25, z + 0.25, style)
    # Under the jaw to the chin cup.
    g.box(-1.3, 1.3, -0.35, 0.35, 3.9, 4.6, style)
    g.box(-4.8, -1.3, -0.15, 0.15, z - 0.25, 4.3, style)
    g.box(1.3, 4.8, -0.15, 0.15, z - 0.25, 4.3, style)


def helmet_6b47():
    """The Russian 6B47 "Ratnik" in its digital cover: a full-cut shell, an elastic band round it, a front mount."""
    m = gear_model('helmet_6b47', 'emr', seed=11)
    root = G(m, m.part('helmet'))
    zc = -0.25
    dome(root, 'emr', zc)
    # Down over the ears and the nape: the sides from in front of the ear back, the back across.
    for sx in (-1, 1):
        root.box(sx * 5.25, sx * 3.9, 2.6, 5.3, zc - 3.2, zc + 1.6, 'emr', sides={'bottom': 'rubber_edge'})
        root.box(sx * 5.25, sx * 3.9, 3.9, 5.3, zc + 1.6, zc + 3.2, 'emr', sides={'bottom': 'rubber_edge'})
    root.box(-3.4, 3.4, 2.3, 5.3, zc - 5.05, zc - 3.9, 'emr', sides={'bottom': 'rubber_edge'})
    root.corners(2.45, 5.3, 5.25, 5.05, 1.8, 'emr', zc, only=((-1, -1), (1, -1)), sides={'bottom': 'rubber_edge'})
    # The elastic band for foliage, round the crown.
    root.slab(6.95, 7.35, 5.23, 5.03, 1.8, 'olive_drab', zc=zc, faces=('front', 'back', 'left', 'right'))
    # The front mount (for goggles or a light) and the cover's tie at the back.
    root.box(-1.0, 1.0, 6.2, 7.5, zc + 4.95, zc + 5.35, 'poly')
    root.box(-0.6, 0.6, 6.45, 7.25, zc + 5.35, zc + 5.5, 'steel_dark')
    root.box(-0.5, 0.5, 5.4, 6.6, zc - 5.35, zc - 4.95, 'olive_drab')
    chin_strap(root, 'strap', z=0.6)
    return m


def fast_shell(g, cover, zc=-0.2):
    """A high-cut ballistic helmet: the crown, cut high above the ears, rails, a shroud, velcro, a counterweight."""
    dome(g, cover, zc, low=5.5)
    # Only the nape comes down.
    g.box(-3.4, 3.4, 3.9, 5.5, zc - 5.05, zc - 3.9, cover, sides={'bottom': 'rubber_edge'})
    g.corners(3.9, 5.5, 5.25, 5.05, 1.8, cover, zc, only=((-1, -1), (1, -1)), sides={'bottom': 'rubber_edge'})
    for sx in (-1, 1):
        g.box(sx * 5.25, sx * 3.9, 4.6, 5.5, zc - 3.2, zc - 1.2, cover, sides={'bottom': 'rubber_edge'})
        # The side rail with its bolts.
        g.box(sx * 5.22, sx * 5.62, 5.7, 6.55, zc - 3.0, zc + 2.6, 'rail')
        for z in (-2.2, 0.0, 2.0):
            g.box(sx * 5.62, sx * 5.72, 5.95, 6.3, zc + z - 0.2, zc + z + 0.2, 'steel_dark')
    # The goggles' shroud at the front.
    g.box(-1.3, 1.3, 6.15, 7.9, zc + 4.95, zc + 5.3, 'poly')
    g.box(-0.7, 0.7, 6.45, 7.3, zc + 5.3, zc + 5.38, 'poly_dark')
    # Velcro on the top, the back and the sides.
    g.box(-2.0, 2.0, 9.8, 9.88, zc - 1.4, zc + 1.4, cover + '_velcro' if cover + '_velcro' in STYLES else 'mc_velcro')
    # The counterweight pouch at the back.
    g.box(-1.9, 1.9, 5.0, 7.0, zc - 5.55, zc - 5.0, cover)
    g.box(-1.9, 1.9, 6.75, 7.05, zc - 5.6, zc - 4.95, 'strap_tan')
    # A strobe light on the back of the crown.
    g.box(-0.45, 0.45, 8.0, 8.6, zc - 4.85, zc - 4.45, 'poly')
    # The harness: from the rails down to the chin, and round the nape.
    for sx in (-1, 1):
        g.box(sx * 4.55, sx * 4.8, 0.3, 5.6, 0.45, 0.95, 'strap')
        g.box(sx * 4.55, sx * 4.8, 2.4, 4.7, zc - 3.0, zc - 2.5, 'strap')
    g.box(-4.8, 4.8, 2.4, 2.75, -4.7, -4.5, 'strap')
    g.box(-1.3, 1.3, -0.35, 0.35, 3.9, 4.6, 'strap')
    g.box(-4.8, -1.3, -0.15, 0.15, 0.95, 4.3, 'strap')
    g.box(1.3, 4.8, -0.15, 0.15, 0.95, 4.3, 'strap')


def helmet_fast():
    m = gear_model('helmet_fast', 'mc', seed=12)
    fast_shell(G(m, m.part('helmet')), 'mc')
    return m


def helmet_fast_nvg():
    """The FAST with binocular night goggles on a flip-up mount: part "nvg" turns about the hinge (see GearLayer)."""
    m = gear_model('helmet_fast_nvg', 'mc', seed=13)
    root = G(m, m.part('helmet'))
    zc = -0.2
    fast_shell(root, 'mc', zc)
    # The mount on the shroud, and the arm hanging from its hinge.
    root.box(-0.8, 0.8, 6.0, 7.6, zc + 5.3, zc + 6.0, 'poly_dark')
    hinge = (0.0, 6.6, zc + 6.05)
    nvg = root.child('nvg', hinge)
    nvg.box(-0.55, 0.55, 4.6, 6.9, zc + 5.85, zc + 6.55, 'poly')
    # The bridge between the tubes, in front of the nose.
    nvg.box(-1.2, 1.2, 3.6, 4.8, zc + 6.0, zc + 6.9, 'poly')
    # Two tubes in front of the eyes, the objectives forward.
    for sx in (-1, 1):
        nvg.tube(sx * 1.85, 4.05, zc + 5.0, zc + 7.9, 0.85, 'poly', ends=('lens_green', 'lens_dark'))
        nvg.box(sx * 1.85 - 0.95, sx * 1.85 + 0.95, 3.1, 5.0, zc + 7.3, zc + 7.8, 'poly_dark')
    # The battery pack on top of the bridge.
    nvg.box(-0.9, 0.9, 4.8, 5.6, zc + 6.1, zc + 7.2, 'poly_dark')
    return m


# ---------------------------------------------------------------------------------------------------------------
# Vests

# Where the pouches go (body frame, pixels): x, y (the pouch's bottom), z (the vest's surface), turned (degrees about y).
# 0-2: the front's lower row, 3: the chest, 4-5: the back. Keep in step with GearLayer.SLOTS.
PC_SLOTS = [(-2.35, -8.45, 3.35, 0), (0.0, -8.45, 3.35, 0), (2.35, -8.45, 3.35, 0),
            (0.0, -5.2, 3.35, 0), (-1.7, -8.8, -3.3, 180), (1.7, -8.8, -3.3, 180)]
HEAVY_SLOTS = [(-2.5, -9.6, 3.55, 0), (0.0, -9.6, 3.55, 0), (2.5, -9.6, 3.55, 0),
               (0.0, -6.2, 3.55, 0), (-1.8, -9.6, -3.5, 180), (1.8, -9.6, -3.5, 180)]


def vest_pc():
    """A plate carrier: front and back plates in webbed bags, shoulder straps, a cummerbund, a name-tape patch."""
    m = gear_model('vest_pc', 'mc', seed=21)
    v = G(m, m.part('vest'))
    # Front: the plate's bag, its shoulders cut.
    v.box(-3.5, 3.5, -8.6, -2.6, 2.15, 3.35, 'mc', sides={'front': 'mc_molle'})
    v.box(-2.5, 2.5, -2.6, -0.9, 2.15, 3.35, 'mc')
    v.box(-1.6, 1.6, -2.3, -1.2, 3.35, 3.45, 'mc_velcro')
    # Back.
    v.box(-3.5, 3.5, -9.0, -2.6, -3.3, -2.15, 'mc', sides={'back': 'mc_molle'})
    v.box(-2.5, 2.5, -2.6, -0.9, -3.3, -2.15, 'mc')
    # The drag handle on the back.
    v.box(-1.0, 1.0, -1.6, -0.9, -3.6, -3.3, 'strap_tan')
    # Shoulder straps over the top, with their pads.
    for sx in (-1, 1):
        v.box(sx * 1.4, sx * 3.3, -0.05, 0.5, -2.45, 2.45, 'mc')
        v.box(sx * 1.4, sx * 3.3, -1.0, -0.05, 2.15, 2.6, 'mc')
        v.box(sx * 1.4, sx * 3.3, -1.0, -0.05, -2.6, -2.15, 'mc')
        # The cummerbund round the side.
        v.box(sx * 4.0, sx * 4.35, -8.7, -4.6, -2.4, 2.4, 'mc', sides={'left' if sx < 0 else 'right': 'mc_molle'})
        # Buckles at the front edges.
        v.box(sx * 3.45, sx * 4.05, -7.6, -5.8, 2.15, 2.55, 'poly')
    return m


def vest_6b45():
    """The heavy Russian 6B45 vest in its digital cover: bigger plates, a collar, a groin flap."""
    m = gear_model('vest_6b45', 'emr', seed=22)
    v = G(m, m.part('vest'))
    v.box(-3.9, 3.9, -10.0, -2.2, 2.15, 3.55, 'emr', sides={'front': 'emr_molle'})
    v.box(-2.9, 2.9, -2.2, -0.6, 2.15, 3.55, 'emr')
    v.box(-1.8, 1.8, -2.0, -0.9, 3.55, 3.65, 'emr_velcro')
    v.box(-3.9, 3.9, -10.0, -2.2, -3.5, -2.15, 'emr', sides={'back': 'emr_molle'})
    v.box(-2.9, 2.9, -2.2, -0.6, -3.5, -2.15, 'emr')
    # The collar round the neck.
    v.box(-2.6, 2.6, -0.8, 0.5, 2.15, 2.9, 'emr')
    v.box(-3.0, 3.0, -0.6, 1.1, -3.2, -2.15, 'emr')
    for sx in (-1, 1):
        v.box(sx * 1.3, sx * 3.6, -0.05, 0.9, -2.4, 2.4, 'emr')
        v.box(sx * 2.6, sx * 3.6, -0.8, -0.05, 2.15, 2.9, 'emr')
        # Side protection, thicker than a cummerbund.
        v.box(sx * 4.0, sx * 4.45, -9.8, -3.6, -2.5, 2.5, 'emr', sides={'left' if sx < 0 else 'right': 'emr_molle'})
    # The groin flap and its quick-release tab.
    v.box(-1.8, 1.8, -12.0, -10.0, 2.25, 2.95, 'emr')
    v.box(-0.4, 0.4, -3.0, -1.6, 3.55, 3.8, 'tq_red')
    # The rear handle.
    v.box(-1.1, 1.1, -1.4, -0.6, -3.8, -3.5, 'strap')
    return m


# ---------------------------------------------------------------------------------------------------------------
# Pouches (each in multicam and in digital, to match the vest)


def pouch_mag(cloth, tag):
    """Two rifle magazines in an open-top pouch, a bungee across."""
    m = gear_model('pouch_mag_' + tag, cloth, tex_width=256, seed=31)
    g = G(m, m.part('pouch'))
    g.box(-1.05, 1.05, 0.0, 2.3, 0.0, 1.35, cloth)
    for sx in (-1, 1):
        # The magazines' bodies above the pouch, curved forward a little.
        g.box(sx * 0.08, sx * 0.95, 2.3, 3.1, 0.25, 1.05, 'mag_ak')
        g.box(sx * 0.08, sx * 0.95, 3.1, 3.5, 0.35, 1.2, 'mag_ak')
    g.box(-1.1, 1.1, 1.75, 2.05, -0.02, 1.4, 'strap')
    g.box(-0.3, 0.3, 2.05, 2.5, 1.2, 1.42, 'strap_tan')
    return m


def pouch_grenade(cloth, tag):
    """A grenade pouch with its flap; the fuse and the lever of the grenade show."""
    m = gear_model('pouch_grenade_' + tag, cloth, tex_width=256, seed=32)
    g = G(m, m.part('pouch'))
    g.slab(0.0, 2.1, 0.95, 0.75, 0.35, cloth, zc=0.78)
    g.box(-1.0, 1.0, 2.1, 2.45, 0.0, 1.6, cloth)
    g.box(-0.35, 0.35, 1.2, 2.2, 1.5, 1.62, 'strap')
    g.box(-0.22, 0.22, 2.45, 2.9, 0.55, 0.95, 'steel_dark')
    g.box(0.1, 0.32, 1.9, 2.9, 0.95, 1.1, 'steel_dark')
    return m


def pouch_medkit(cloth, tag):
    """The first-aid kit (IFAK) with a red cross, a tourniquet strapped on top."""
    m = gear_model('pouch_medkit_' + tag, cloth, tex_width=256, seed=33)
    g = G(m, m.part('pouch'))
    g.slab(0.0, 2.4, 1.15, 0.72, 0.3, cloth, zc=0.72)
    g.box(-0.95, 0.95, 0.35, 2.05, 1.44, 1.5, 'coyote_cross', faces=('front',))
    # The tourniquet: black with a red tab.
    g.box(-1.0, 1.0, 2.4, 2.85, 0.3, 1.1, 'poly')
    g.box(0.75, 1.15, 2.4, 2.85, 0.5, 0.9, 'tq_red')
    return m


def pouch_radio(cloth, tag):
    """A radio in its pouch, the whip antenna up."""
    m = gear_model('pouch_radio_' + tag, cloth, tex_width=256, seed=34)
    g = G(m, m.part('pouch'))
    g.box(-0.95, 0.95, 0.0, 2.6, 0.0, 1.3, cloth)
    g.box(-0.75, 0.75, 2.6, 3.25, 0.2, 1.1, 'poly')
    g.box(0.2, 0.55, 3.25, 3.6, 0.45, 0.8, 'poly_dark')
    g.box(-0.55, -0.15, 3.25, 3.45, 0.45, 0.8, 'tq_red')
    g.box(0.28, 0.47, 3.6, 7.6, 0.53, 0.72, 'poly_dark')
    g.box(-0.95, 0.95, 1.4, 1.7, -0.02, 1.35, 'strap')
    return m


POUCHES = (pouch_mag, pouch_grenade, pouch_medkit, pouch_radio)


def all_models():
    out = [helmet_6b47(), helmet_fast(), helmet_fast_nvg(), vest_pc(), vest_6b45()]
    for cloth, tag in (('mc', 'mc'), ('emr', 'emr')):
        for f in POUCHES:
            out.append(f(cloth, tag))
    return out


def monocular():
    """The hand-held thermal imager (only its item icon is drawn from this): a rubber body, a wide objective, an eyecup."""
    m = gear_model('thermal_monocular', 'poly', tex_width=256, seed=41)
    g = G(m, m.part('body'))
    g.box(-2.0, 2.0, -2.0, 2.0, -4.0, 4.0, 'poly')
    g.box(-2.6, 2.6, -2.6, 2.6, 4.0, 6.2, 'poly_dark', sides={'front': 'lens_dark'})
    g.box(-1.5, 1.5, -1.5, 1.5, -6.0, -4.0, 'poly_dark')
    g.box(-1.9, 1.9, -1.9, 1.9, -7.2, -6.0, 'rubber_edge')
    g.box(-0.6, 0.6, 2.0, 2.5, -1.5, -0.3, 'olive_drab')
    g.box(-0.5, 0.5, 2.0, 2.4, 0.5, 1.6, 'steel_dark')
    g.box(2.0, 2.4, -1.2, 1.2, -3.0, 2.5, 'strap')
    return m


def ammo_crate():
    """A green-painted wooden ammunition crate (its item icon): planks, a lid, rope handles, a stencilled band."""
    m = gear_model('ammo_crate', 'olive_drab', tex_width=256, seed=42)
    g = G(m, m.part('crate'))
    g.box(-6.0, 6.0, 0.0, 6.0, -4.0, 4.0, 'crate_wood')
    g.box(-6.3, 6.3, 6.0, 7.2, -4.3, 4.3, 'crate_wood')
    g.box(-2.2, 2.2, 2.4, 3.6, 4.0, 4.06, 'stencil', faces=('front',))
    for sx in (-1, 1):
        g.box(sx * 6.0, sx * 6.5, 3.6, 4.4, -1.8, 1.8, 'strap_tan')
        g.box(sx * 4.6, sx * 5.4, -0.2, 7.4, -4.4, 4.4, 'steel_dark', faces=('front', 'back', 'top'))
    return m


# ---------------------------------------------------------------------------------------------------------------
# Item icons: the models themselves, textured, seen from the front and a little above


def _faces(m, uv):
    """Every face of the model where it ends up (preview's transform), with its texture rectangle."""
    import preview
    out = []

    def walk(p, R_parent, t_parent, parent_pivot):
        rx, ry, rz = (math.radians(a) for a in p.rot)
        R_local = preview.S @ preview.rot_mc(rx, ry, rz) @ preview.S
        t = t_parent + R_parent @ (np.array(p.pivot) - np.array(parent_pivot))
        R = R_parent @ R_local
        for b in p.boxes:
            if b.style == 'strap' and b.hi[1] < 0.3:
                # No chin strap dangling under an icon.
                continue
            lo = np.array(b.lo) - np.array(p.pivot)
            hi = np.array(b.hi) - np.array(p.pivot)
            c = [t + R @ np.array([x, y, z]) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
            w, h, d = b.px_size()
            u, v = uv[b.key()]
            rects = {
                'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'left': (u, v + d, d, h),
                'back': (u + d, v + d, w, h), 'right': (u + d + w, v + d, d, h), 'front': (u + 2 * d + w, v + d, w, h),
            }
            # Corner index x*4 + y*2 + z; each quad as (origin, along u, along v) so the texture lands upright.
            quads = {
                'front': (3, 7, 1), 'back': (6, 2, 4), 'right': (7, 6, 5), 'left': (2, 3, 0),
                'top': (2, 6, 3), 'bottom': (1, 5, 0),
            }
            for face in b.faces:
                o, a, bb = quads[face]
                out.append((c[o], c[a] - c[o], c[bb] - c[o], rects[face]))
        for ch in p.children:
            walk(ch, R, t, p.pivot)

    for r in m.roots:
        walk(r, np.eye(3), np.zeros(3), (0, 0, 0))
    return out


def icon(m, uv, atlas, path, yaw=200, pitch=24, size=32, ss=6):
    faces = _faces(m, uv)
    ya, pa = math.radians(yaw), math.radians(pitch)
    view = np.array([math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)])
    right = np.cross(view, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, view)
    light = np.array([-0.3, 0.85, 0.45])
    light /= np.linalg.norm(light)
    pts = np.array([f[0] for f in faces] + [f[0] + f[1] + f[2] for f in faces] + [f[0] + f[1] for f in faces] + [f[0] + f[2] for f in faces])
    proj = np.stack([pts @ right, pts @ up], axis=1)
    lo, hi = proj.min(axis=0), proj.max(axis=0)
    N = size * ss
    margin = 1.2 * ss
    scale = (N - 2 * margin) / max(hi[0] - lo[0], hi[1] - lo[1])
    off = np.array([(N - (hi[0] - lo[0]) * scale) / 2, (N - (hi[1] - lo[1]) * scale) / 2])
    img = np.zeros((N, N, 4), dtype=np.float64)
    depth = np.full((N, N), -1e9)
    for o, du, dv, (tx, ty, tw, th) in faces:
        n = np.cross(du, dv)
        nl = np.linalg.norm(n)
        if nl < 1e-12:
            continue
        n /= nl
        if n @ view <= 0:
            continue
        k = 0.62 + 0.38 * max(0.0, float(n @ light))
        p0 = np.array([o @ right - lo[0], o @ up - lo[1]]) * scale + off
        a = np.array([du @ right, du @ up]) * scale
        b = np.array([dv @ right, dv @ up]) * scale
        M = np.array([[a[0], b[0]], [a[1], b[1]]])
        if abs(np.linalg.det(M)) < 1e-9:
            continue
        Mi = np.linalg.inv(M)
        corners = np.array([p0, p0 + a, p0 + b, p0 + a + b])
        x0, y0 = np.floor(corners.min(axis=0)).astype(int)
        x1, y1 = np.ceil(corners.max(axis=0)).astype(int)
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(N, x1), min(N, y1)
        if x1 <= x0 or y1 <= y0:
            continue
        ys, xs = np.mgrid[y0:y1, x0:x1]
        q = np.stack([xs + 0.5 - p0[0], ys + 0.5 - p0[1]], axis=-1)
        uvp = q @ Mi.T
        inside = (uvp[..., 0] >= 0) & (uvp[..., 0] <= 1) & (uvp[..., 1] >= 0) & (uvp[..., 1] <= 1)
        dz = float((o + du / 2 + dv / 2) @ view)
        px = np.clip((tx + uvp[..., 0] * tw).astype(int), tx, tx + max(0, tw - 1))
        py = np.clip((ty + uvp[..., 1] * th).astype(int), ty, ty + max(0, th - 1))
        tex = atlas[py, px].astype(np.float64)
        ok = inside & (tex[..., 3] > 100) & (dz > depth[y0:y1, x0:x1])
        region = img[y0:y1, x0:x1]
        region[ok, :3] = tex[ok, :3] * k
        region[ok, 3] = 255
        depth[y0:y1, x0:x1][ok] = dz
    # Screen y grows downwards.
    img = img[::-1]
    small = img.reshape(size, ss, size, ss, 4)
    alpha = small[..., 3].mean(axis=(1, 3))
    col = (small[..., :3] * (small[..., 3:] > 0)).sum(axis=(1, 3)) / np.maximum(1, (small[..., 3] > 0).sum(axis=(1, 3)))[..., None]
    out = np.zeros((size, size, 4), dtype=np.uint8)
    solid = alpha > 90
    out[solid, :3] = np.clip(col[solid] * 1.08, 0, 255)
    out[solid, 3] = 255
    edge = np.zeros_like(solid)
    edge[1:, :] |= solid[:-1, :]
    edge[:-1, :] |= solid[1:, :]
    edge[:, 1:] |= solid[:, :-1]
    edge[:, :-1] |= solid[:, 1:]
    edge &= ~solid
    out[edge] = (24, 25, 22, 255)
    Image.fromarray(out, 'RGBA').save(path, optimize=True)


ICONS_OF = {
    # item id: (model id, yaw, pitch) - yaw 0 looks at the front.
    'helmet': ('helmet_6b47', 32, 20),
    'helmet_fast': ('helmet_fast', 32, 20),
    'nvg_helmet': ('helmet_fast_nvg', 32, 16),
    'vest': ('vest_pc', 22, 12),
    'vest_heavy': ('vest_6b45', 22, 12),
    'pouch_mag': ('pouch_mag_mc', 28, 18),
    'pouch_grenade': ('pouch_grenade_mc', 28, 18),
    'pouch_medkit': ('pouch_medkit_mc', 28, 18),
    'pouch_radio': ('pouch_radio_mc', 28, 18),
    'thermal_monocular': ('thermal_monocular', 70, 25),
    'ammo_crate': ('ammo_crate', 30, 28),
}


# ---------------------------------------------------------------------------------------------------------------
# Output


def write_java(models, layers):
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, 'w') as out:
        out.write('package com.stasdoto.airdefense.client.gear;\n\n')
        out.write('import java.util.EnumSet;\nimport java.util.Map;\n\n')
        out.write('import net.minecraft.client.model.geom.PartPose;\n')
        out.write('import net.minecraft.client.model.geom.builders.CubeListBuilder;\n')
        out.write('import net.minecraft.client.model.geom.builders.LayerDefinition;\n')
        out.write('import net.minecraft.client.model.geom.builders.MeshDefinition;\n')
        out.write('import net.minecraft.client.model.geom.builders.PartDefinition;\n')
        out.write('import net.minecraft.core.Direction;\n\n')
        out.write('/** GENERATED by tools/models/gear.py - do not edit. The gear drawn on people, at four times the size. */\n')
        out.write('@SuppressWarnings("unused")\npublic final class GenGear {\n')
        out.write('\t/** Model units per pixel of the person (drawn at 1 / SCALE). */\n')
        out.write('\tpublic static final int SCALE = %d;\n\n' % (GEAR_PX // 16))
        out.write('\tprivate GenGear() {\n\t}\n\n')
        out.write('\tpublic static LayerDefinition layer(String id) {\n\t\treturn switch (id) {\n')
        for m in models:
            out.write('\t\t\tcase "%s" -> %s();\n' % (m.id, m.id))
        out.write('\t\t\tdefault -> throw new IllegalArgumentException(id);\n\t\t};\n\t}\n\n')
        out.write('\tpublic static Map<String, String> paths(String id) {\n\t\treturn switch (id) {\n')
        for m in models:
            out.write('\t\t\tcase "%s" -> %sPaths();\n' % (m.id, m.id))
        out.write('\t\t\tdefault -> throw new IllegalArgumentException(id);\n\t\t};\n\t}\n\n')
        out.write('\n\n'.join(layers))
        out.write('\n}\n')


def main():
    os.makedirs(TEX, exist_ok=True)
    models = all_models()
    layers = []
    atlases = {}
    for m in models + [monocular(), ammo_crate()]:
        uv, regions, W, H = m.layout()
        img = m.paint_atlas(uv, regions, W, H)
        atlases[m.id] = (m, uv, img)
        if m.id in ('thermal_monocular', 'ammo_crate'):
            continue
        Image.fromarray(img, 'RGBA').save(os.path.join(TEX, m.id + '.png'), optimize=True)
        layers.append(m.java_layer(uv, W, H))
        print('%-20s parts %3d boxes %4d atlas %dx%d' % (m.id, len(m.parts), sum(len(p.boxes) for p in m.parts), W, H))
    write_java(models, layers)
    for item, (mid, yaw, pitch) in ICONS_OF.items():
        m, uv, img = atlases[mid]
        icon(m, uv, img, os.path.join(ICONS, item + '.png'), yaw, pitch)


if __name__ == '__main__':
    main()
