"""
Box-model generator for the mod's vehicles (and anything else built from boxes).

A model is a tree of parts; each part has a pivot and boxes. Everything is written in metres in vehicle space:
x to the right, y up, z forward (the nose), the ground at y = 0. Boxes are given where they are at rest
(before any part turns); a part's fixed rotation (degrees, Minecraft's sense: +x raises the front, +y turns the
front to the right) is applied around its pivot.

Output: a Java LayerDefinition per model (1 block = 1 m = 16 px), the part paths, the geometry record the
server uses, the texture atlas (each box painted by its style) and a burnt-out "wreck" copy, and a 32x32 item icon.
"""
import math
import os
import random

import numpy as np
from PIL import Image
from scipy.ndimage import zoom as nd_zoom

PX = 16

# ----------------------------------------------------------------------------------------------------------------
# Styles


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


STYLES = {
    # Woodland camouflage (NATO / Ukrainian vehicles).
    'camo': dict(kind='camo', base=0x4A5A32, c2=0x5E4A30, c3=0x22281C),
    # Russian / Soviet protective green.
    'rgreen': dict(kind='plain', base=0x56633A),
    # Ukrainian digital-ish green (Soviet vehicles in Ukrainian service).
    'ugreen': dict(kind='camo', base=0x55613A, c2=0x3C4A2A, c3=0x6B6C47, blob=3),
    # Bundeswehr / NATO plain dark green.
    'nato': dict(kind='plain', base=0x4A5238),
    'olive': dict(kind='plain', base=0x5B6236),
    'tan': dict(kind='camo', base=0xB59A6A, c2=0x8C7650, c3=0xC9B486),
    'sand': dict(kind='plain', base=0xB8A27A),
    'grey': dict(kind='plain', base=0x7C8186),
    # 1.33: warships - Russian naval grey, the Visby's dark stealth grey, decks, the red antifouling below the waterline.
    'navy': dict(kind='plain', base=0x6F777D),
    'visby': dict(kind='plain', base=0x596166),
    'deck': dict(kind='plain', base=0x4B5154),
    'hull_red': dict(kind='plain', base=0x7A2E26),
    # The F-16's two greys.
    'fgrey': dict(kind='plain', base=0x8C949A),
    'fgrey_d': dict(kind='plain', base=0x6A737A),
    'lgrey': dict(kind='plain', base=0xA7ADB2),
    'dgrey': dict(kind='plain', base=0x4A4E52),
    'dark': dict(kind='plain', base=0x2C2F31),
    'black': dict(kind='plain', base=0x18191A),
    'white': dict(kind='plain', base=0xD8DCDE),
    'steel': dict(kind='plain', base=0x8A8F94),
    'rust': dict(kind='plain', base=0x6E4A2E),
    'canvas': dict(kind='plain', base=0x6E6B4C),
    # Window glass: see-through (drawn translucent near the vehicle, when its inside is drawn too).
    'glass': dict(kind='glass', base=0x2A3946, alpha=118),
    'light': dict(kind='plain', base=0xF2E7B0, flat=True),
    'redlight': dict(kind='plain', base=0xB82A20, flat=True),
    'orange': dict(kind='plain', base=0xD8792A),
    'tire': dict(kind='tire', base=0x1E1F20),
    'track': dict(kind='track', base=0x2E2C29),
    # Wheel sidewall: round, the tyre ring and the hub in the given colour.
    'wheel': dict(kind='wheel', base=0x1E1F20, hub=0x55613A),
    'wheel_tan': dict(kind='wheel', base=0x1E1F20, hub=0x9E8660),
    'wheel_grey': dict(kind='wheel', base=0x1E1F20, hub=0x6C7176),
    'roadwheel': dict(kind='wheel', base=0x26282A, hub=0x4E5A36, ring=0.22),
    # Radar faces.
    'aesa': dict(kind='grid', base=0x3A3F44, line=0x565D63, step=2),
    'aesa_light': dict(kind='grid', base=0x8C9196, line=0xA6ABB0, step=2),
    'radome': dict(kind='plain', base=0xC9CCC4),
    # Open lattice (see-through): reflectors, mesh antennas, fences.
    'mesh': dict(kind='mesh', base=0x6F7468, step=3),
    'mesh_dark': dict(kind='mesh', base=0x3E4237, step=3),
    'mesh_fine': dict(kind='mesh', base=0x80857A, step=2),
    # Insides (1.26): painted walls, the dashboard, seats, dials, screens, switch panels, radios, rubber mats.
    'int_panel': dict(kind='plain', base=0x6E7964),
    'int_light': dict(kind='plain', base=0x9AA294),
    'int_grey': dict(kind='plain', base=0x7E8288),
    'int_dark': dict(kind='plain', base=0x2B2D2E),
    'int_black': dict(kind='plain', base=0x161718),
    # The turquoise of Soviet cockpits and the dark grey of western ones.
    'int_turq': dict(kind='plain', base=0x4E9C98),
    'int_turq_dark': dict(kind='plain', base=0x2F6662),
    'int_navy': dict(kind='plain', base=0x34393F),
    'rubber': dict(kind='track', base=0x2A2B2C),
    'seat': dict(kind='seat', base=0x3E4034, stitch=0x2C2E25),
    'seat_black': dict(kind='seat', base=0x262728, stitch=0x18191A),
    'gauge': dict(kind='gauge', base=0x121314),
    'screen': dict(kind='screen', base=0x0C2410, line=0x3CD25A, flat=True),
    'screen_amber': dict(kind='screen', base=0x241A08, line=0xE0A030, flat=True),
    'switches': dict(kind='switches', base=0x3A3D40),
    'radio': dict(kind='radio', base=0x55603C),
    'red': dict(kind='plain', base=0xA4281E),
    # 1.38: the emergency services - fire engine red, the police blue, the beacons (drawn bright).
    'fire_red': dict(kind='plain', base=0xB8211B),
    'police_blue': dict(kind='plain', base=0x23409A),
    'beacon_blue': dict(kind='plain', base=0x2C5BFF, flat=True),
    'beacon_red': dict(kind='plain', base=0xFF2A1E, flat=True),
    'stripe_yellow': dict(kind='plain', base=0xE6C21E),
    'yellow': dict(kind='plain', base=0xC9A227),
    # 1.39: the trains - the electric train's green, red and yellow, the locomotive's green, the wagons, the coal.
    'rail_green': dict(kind='plain', base=0x2F6A45),
    'rail_red': dict(kind='plain', base=0xA8261F),
    'rail_yellow': dict(kind='plain', base=0xE1B227),
    'loco_green': dict(kind='plain', base=0x46613C),
    'wagon_brown': dict(kind='plain', base=0x6A3B27),
    'tank_black': dict(kind='plain', base=0x26272A),
    'coal': dict(kind='camo', base=0x1C1C1D, c2=0x2C2C2E, c3=0x111112, blob=1),
    'window': dict(kind='plain', base=0x22303B),
    # 1.40: the airliners' blue and red, a green navigation light.
    'liner_blue': dict(kind='plain', base=0x1F3F8C),
    'liner_red': dict(kind='plain', base=0xB0262A),
    'green_light': dict(kind='plain', base=0x2EC24A, flat=True),
    # 1.41: the working country - a tractor's red, a crane's yellow (and its lattice), concrete, a load of gravel.
    'tractor_red': dict(kind='plain', base=0xB3261E),
    'crane_yellow': dict(kind='plain', base=0xE3B21C),
    'mesh_yellow': dict(kind='mesh', base=0xD9A818, step=3),
    'concrete': dict(kind='plain', base=0x9C9A94),
    'gravel': dict(kind='camo', base=0x7E7A74, c2=0x95918A, c3=0x5E5B57, blob=1),
    'chrome': dict(kind='plain', base=0xB9BEC2),
    # The head-up display's combiner glass (the inside is drawn solid: a dark green tinted pane).
    'hud': dict(kind='plain', base=0x2F5240, flat=True),
}


# 1.44: how worn and dirty each paint gets (1 = an army vehicle in the field). Styles not listed: by their kind.
WEAR = {
    'white': 0.35, 'chrome': 0.0, 'liner_blue': 0.25, 'liner_red': 0.25, 'fire_red': 0.35, 'police_blue': 0.35,
    'stripe_yellow': 0.4, 'yellow': 0.6, 'red': 0.5, 'orange': 0.5, 'light': 0.0, 'redlight': 0.0, 'hud': 0.0,
    'radome': 0.4, 'rail_green': 0.55, 'rail_red': 0.55, 'rail_yellow': 0.55, 'tractor_red': 0.7, 'crane_yellow': 0.6,
    'concrete': 0.5, 'window': 0.0, 'coal': 0.0, 'gravel': 0.0, 'black': 0.5, 'dark': 0.6,
}
# Paints that are armour: welded plates with panel seams and rivets.
ARMOUR = {'camo', 'rgreen', 'ugreen', 'nato', 'olive', 'tan', 'sand', 'grey', 'navy', 'visby', 'deck', 'dgrey', 'steel'}
DUST = (126, 110, 84)
MUD = (74, 62, 46)


def mul(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


class Painter:
    def __init__(self, seed):
        self.rng = np.random.default_rng(seed)

    def noise_field(self, w, h, cell):
        gw = max(2, w // cell + 2)
        gh = max(2, h // cell + 2)
        g = self.rng.random((gh, gw))
        z = nd_zoom(g, (h / gh * 1.0 + 0.0001, w / gw * 1.0 + 0.0001), order=1)
        z = np.pad(z, ((0, max(0, h - z.shape[0])), (0, max(0, w - z.shape[1]))), mode='edge')
        return z[:h, :w]

    def gear_cloth(self, st, w, h, k):
        """Cloth of the soldiers' gear: plain, digital (square cells) or soft-blob camouflage, by st['camo']."""
        base = np.array(rgb(st['base']), dtype=np.float64) * k
        out = np.zeros((h, w, 3)) + base
        camo = st.get('camo')
        cols = [np.array(rgb(c), dtype=np.float64) * k for c in st.get('colors', ())]
        if camo == 'pixel' and cols:
            cell = st.get('cell', 2)
            gw, gh = (w + cell - 1) // cell, (h + cell - 1) // cell
            for i, c in enumerate(cols):
                f = self.noise_field(gw, gh, st.get('blob', 3))
                m = f > (0.58 + 0.05 * i)
                m = np.kron(m, np.ones((cell, cell), dtype=bool))[:h, :w]
                out[m] = c
        elif camo == 'blobs' and cols:
            for i, c in enumerate(cols):
                f = self.noise_field(w, h, st.get('blob', 6) + (2 if i == 0 else -1 if i > 1 else 0))
                m = f > (0.6 + 0.06 * i)
                out[m] = c
        out += self.rng.normal(0, st.get('grain', 2.5), (h, w, 1))
        return out

    def face(self, img, x0, y0, w, h, style, shade, face):
        """Paints one face rectangle (integer pixels) of the atlas."""
        if w <= 0 or h <= 0:
            return
        st = STYLES[style]
        kind = st['kind']
        base = rgb(st['base'])
        flat = st.get('flat', False)
        k = 1.0 if flat else shade
        region = np.zeros((h, w, 4), dtype=np.float64)
        region[..., 3] = 255
        if kind in ('plain', 'glass'):
            region[..., :3] = mul(base, k)
            if not flat:
                region[..., :3] += self.rng.normal(0, 3.0, (h, w, 1))
            if kind == 'glass' and w >= 3 and h >= 3:
                # A light streak across the window.
                for i in range(w):
                    j = int(i * 0.6) + h // 4
                    if 0 <= j < h:
                        region[j, i, :3] = mul(base, 1.6)
        elif kind == 'camo':
            region[..., :3] = mul(base, k)
            cell = st.get('blob', 7)
            if cell >= 5 and w >= 6 and h >= 6:
                # 1.44: swathes running slantwise (as painted on real vehicles), two layers of noise for ragged edges.
                f2 = self.skewed(w, h, cell) * 0.8 + self.noise_field(w, h, 2) * 0.2
                f3 = self.skewed(w, h, cell) * 0.8 + self.noise_field(w, h, 2) * 0.2
                m2 = f2 > 0.6
                m3 = (f3 > 0.66) & ~m2
            else:
                m2 = self.noise_field(w, h, cell) > 0.62
                m3 = self.noise_field(w, h, cell) > 0.70
            region[m2, :3] = mul(rgb(st['c2']), k)
            region[m3, :3] = mul(rgb(st['c3']), k)
            region[..., :3] += self.rng.normal(0, 2.5, (h, w, 1))
        elif kind == 'grid':
            region[..., :3] = mul(base, k)
            step = st.get('step', 2)
            line = mul(rgb(st['line']), k)
            region[::step, :, :3] = line
            region[:, ::step, :3] = line
        elif kind == 'mesh':
            region[..., 3] = 0
            step = st.get('step', 3)
            c = mul(base, k)
            region[::step, :, :3] = c
            region[::step, :, 3] = 255
            region[:, ::step, :3] = c
            region[:, ::step, 3] = 255
            region[0, :, :3] = c
            region[-1, :, :3] = c
            region[:, 0, :3] = c
            region[:, -1, :3] = c
            region[0, :, 3] = region[-1, :, 3] = 255
            region[:, 0, 3] = region[:, -1, 3] = 255
        elif kind == 'tire':
            region[..., :3] = mul(base, k)
            region[::3, :, :3] = mul(base, k * 0.6)
            region[..., :3] += self.rng.normal(0, 2.0, (h, w, 1))
        elif kind == 'track':
            # 1.44: link plates 4 texels long: the gap, the pin (shiny), the plate, its grouser; the guide horn in the middle.
            region[..., :3] = mul(base, k)
            region[0::4, :, :3] = mul(base, k * 0.45)
            region[1::4, :, :3] = mul(base, k * 1.45)
            region[3::4, :, :3] = mul(base, k * 0.85)
            if w >= 5:
                c = w // 2
                region[2::4, c:c + 1, :3] = mul(base, k * 1.6)
            region[:, 0, :3] *= 1.2
            region[:, -1, :3] *= 1.2
            mud = self.noise_field(w, h, 3)
            a = np.clip((mud - 0.45) * 1.6, 0, 0.7)[..., None]
            region[..., :3] = region[..., :3] * (1 - a) + np.array(mul(MUD, k)) * a
            region[..., :3] += self.rng.normal(0, 2.5, (h, w, 1))
        elif kind == 'wheel':
            # Round wheel seen from the side: transparent outside the circle.
            cx = (w - 1) / 2.0
            cy = (h - 1) / 2.0
            r = min(w, h) / 2.0
            yy, xx = np.mgrid[0:h, 0:w]
            d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
            ring = st.get('ring', 0.32)
            hub = rgb(st['hub'])
            region[..., :3] = mul(base, k)
            region[d > r, 3] = 0
            inner = d <= r * (1 - ring)
            region[inner, :3] = mul(hub, k)
            region[d <= r * 0.18, :3] = mul(hub, k * 0.6)
            # Bolts.
            for a in range(6):
                bx = int(round(cx + math.cos(a * math.pi / 3) * r * 0.42))
                by = int(round(cy + math.sin(a * math.pi / 3) * r * 0.42))
                if 0 <= bx < w and 0 <= by < h:
                    region[by, bx, :3] = mul(hub, k * 0.55)
            # Sidewall line.
            region[(d > r * (1 - ring)) & (d < r * (1 - ring) + 1.0), :3] = mul(base, k * 0.6)
            if r >= 4:
                # 1.44: the dished hub - lighter towards its rim, darker in the middle - and a little dust on the rubber.
                hubm = inner & (d > r * 0.18)
                t = np.clip(d / max(1.0, r * (1 - ring)), 0, 1)
                region[hubm, :3] *= (0.85 + 0.25 * t[hubm])[..., None]
                rub = (d <= r) & ~inner
                region[rub, :3] = region[rub, :3] * 0.8 + np.array(mul(DUST, k * 0.6)) * 0.2
        elif kind == 'seat':
            region[..., :3] = mul(base, k)
            region[..., :3] += self.rng.normal(0, 2.5, (h, w, 1))
            st_c = mul(rgb(st['stitch']), k)
            step = max(3, min(w, h) // 3)
            region[:, ::step, :3] = st_c
            if h >= 6:
                region[h // 2, :, :3] = st_c
        elif kind == 'gauge':
            # A row of round dials: dark face, light bezel, ticks and a white needle (tiny ones: a light face).
            region[..., :3] = mul((40, 42, 44), k)
            n = max(1, int(round(w / max(1, h))))
            size = w / n
            yy, xx = np.mgrid[0:h, 0:w]
            for i in range(n):
                cx = size * (i + 0.5) - 0.5
                cy = (h - 1) / 2.0
                r = min(size, h) / 2.0 - 0.4
                d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
                if r < 3:
                    # Too small to draw a dial: a dark glass face with a light needle (a light face read as a chequerboard).
                    region[d <= r, :3] = (64, 68, 66)
                    region[(d <= 0.8), :3] = (226, 226, 214)
                    continue
                region[d <= r, :3] = mul(base, 1.0)
                region[(d <= r) & (d > r - 1.1), :3] = (190, 192, 184)
                for a in np.linspace(-2.4, 0.8, 7):
                    px = int(round(cx + math.cos(a) * (r - 2.0)))
                    py = int(round(cy + math.sin(a) * (r - 2.0)))
                    if 0 <= px < w and 0 <= py < h:
                        region[py, px, :3] = (210, 210, 200)
                ang = -2.2 + self.rng.random() * 2.8
                for t in np.linspace(0, r - 1.6, 10):
                    px = int(round(cx + math.cos(ang) * t))
                    py = int(round(cy + math.sin(ang) * t))
                    if 0 <= px < w and 0 <= py < h:
                        region[py, px, :3] = (240, 240, 232)
        elif kind == 'screen':
            region[..., :3] = rgb(st['base'])
            line = rgb(st['line'])
            for yy in range(1, h - 1, 3):
                x0_ = int(self.rng.integers(1, max(2, w // 3)))
                x1_ = int(self.rng.integers(x0_ + 1, max(x0_ + 2, w - 1)))
                region[yy, x0_:x1_, :3] = line
            region[0, :, :3] = mul(line, 0.5)
            region[-1, :, :3] = mul(line, 0.5)
            region[:, 0, :3] = mul(line, 0.5)
            region[:, -1, :3] = mul(line, 0.5)
        elif kind == 'switches':
            region[..., :3] = mul(base, k)
            for yy in range(1, h - 1, 3):
                for xx in range(1, w - 1, 3):
                    c = (210, 210, 200) if self.rng.random() < 0.7 else (200, 60, 40) if self.rng.random() < 0.5 else (90, 200, 90)
                    region[yy, xx, :3] = c
        elif kind == 'radio':
            region[..., :3] = mul(base, k)
            region[..., :3] += self.rng.normal(0, 2.0, (h, w, 1))
            for xx in range(2, w - 1, 4):
                region[h // 3, xx, :3] = (30, 30, 30)
                if h > 4:
                    region[2 * h // 3, xx, :3] = (220, 200, 120)
        elif kind in ('pixel', 'multicam', 'molle', 'cross', 'velcro', 'lens'):
            # 1.27, soldiers' gear (tools/models/gear.py).
            region[..., :3] = self.gear_cloth(st, w, h, k)
            if kind == 'molle':
                # Webbing: rows of tape with gaps where the pouches' straps go through.
                step = st.get('step', 5)
                tape = st.get('tape', 2)
                for yy in range(1, h - 1, step):
                    region[yy:yy + tape, :, :3] *= 0.8
                    region[yy, :, :3] *= 1.12
                    for xx in range(st.get('gap', 6) // 2, w, st.get('gap', 6)):
                        region[yy:yy + tape, xx, :3] *= 0.6
            elif kind == 'velcro':
                region[..., :3] += self.rng.normal(0, 7.0, (h, w, 1))
                region[0, :, :3] *= 0.7
                region[-1, :, :3] *= 0.7
            elif kind == 'cross':
                cw = max(1, min(w, h) // 4)
                cy, cx = h // 2, w // 2
                arm = max(cw + 1, int(min(w, h) * 0.42))
                red = rgb(st.get('mark', 0xB0201A))
                region[cy - cw:cy + cw, cx - arm:cx + arm, :3] = red
                region[cy - arm:cy + arm, cx - cw:cx + cw, :3] = red
            elif kind == 'lens':
                yy, xx = np.mgrid[0:h, 0:w]
                d = np.sqrt((xx - (w - 1) / 2) ** 2 + (yy - (h - 1) / 2) ** 2)
                r = min(w, h) / 2.0
                region[d <= r * 0.8, :3] = rgb(st.get('glass', 0x1C3A2C))
                region[(d <= r * 0.8) & (xx < w / 2) & (yy < h / 2) & (d > r * 0.45), :3] = rgb(st.get('glint', 0x6FA08A))
        else:
            raise ValueError(kind)
        if kind == 'glass':
            region[..., 3] = st.get('alpha', 255)
        if kind in ('plain', 'camo') and not flat:
            wear = WEAR.get(style, 0.25 if style.startswith('int_') else 1.0)
            self.weather(region, w, h, face, wear, k, style in ARMOUR)
        if kind in ('plain', 'camo', 'glass', 'pixel', 'multicam', 'molle', 'cross') and not flat and w >= 4 and h >= 4:
            # Edges: a worn light lip along the top, shadowed sides and bottom (the boxes read as solid plates).
            region[0, :, :3] *= 1.12 if face in ('front', 'back', 'left', 'right') else 0.82
            region[-1, :, :3] *= 0.7
            region[:, 0, :3] *= 0.82
            region[:, -1, :3] *= 0.82
        img[y0:y0 + h, x0:x0 + w] = np.clip(region, 0, 255)

    def skewed(self, w, h, cell):
        """Noise stretched along a slant (camouflage swathes)."""
        big = self.noise_field(w + h, h, cell)
        yy = np.arange(h)[:, None]
        xx = np.arange(w)[None, :] + yy // 2
        return big[np.broadcast_to(yy, (h, w)), np.clip(xx, 0, w + h - 1)]

    def weather(self, region, w, h, face, wear, k, armour):
        """1.44: paint that has seen the field - faded in patches, dust rising from below, rain streaks down the sides,
        chipped edges, and on armour the seams of the welded plates with their rivets. Not the clean plastic of before."""
        if wear <= 0 or w < 3 or h < 3:
            return
        c = region[..., :3]
        # Faded patches, large and small.
        m1 = self.noise_field(w, h, 9) - 0.5
        m2 = self.noise_field(w, h, 3) - 0.5
        c *= (1 + wear * (0.13 * m1 + 0.06 * m2))[..., None]
        dust = np.array(mul(DUST, k))
        side = face in ('front', 'back', 'left', 'right')
        if side and h >= 5:
            # Dust and mud thrown up from the ground: thickest at the bottom of each plate.
            yy = (np.arange(h) / max(1, h - 1))[:, None]
            n = self.noise_field(w, h, 4)
            a = np.clip((yy - 0.45) / 0.55, 0, 1) ** 1.6 * (0.3 + 0.6 * n) * wear * 0.7
            c[:] = c * (1 - a[..., None]) + dust * a[..., None]
            # Rain streaks running down from the top edge.
            for _ in range(max(1, w // 5)):
                x = int(self.rng.integers(0, w))
                length = int(self.rng.integers(max(2, h // 4), h + 1))
                fade = np.linspace(1.0, 0.2, length)[:, None]
                c[:length, x] *= 1 - 0.12 * wear * fade
        elif face == 'top':
            # Dust settled on the flat tops.
            n = self.noise_field(w, h, 3)
            a = np.clip((n - 0.5) * 1.5, 0, 0.45) * wear
            c[:] = c * (1 - a[..., None]) + dust * 1.08 * a[..., None]
        else:
            c *= 0.88
        # Chipped paint along the edges: lighter scratches and the odd dark spot of bare metal.
        if w >= 4 and h >= 4:
            border = np.zeros((h, w), dtype=bool)
            border[:2, :] = border[-1:, :] = True
            border[:, :1] = border[:, -1:] = True
            r = self.rng.random((h, w))
            c[border & (r < 0.22 * wear)] *= 1.22
            c[border & (r > 1 - 0.05 * wear)] *= 0.55
        # Seams of the plates every 0.8-1.2 m (on armour), with a lit edge beside each and a row of rivets.
        if armour and wear >= 0.5:
            long_axis = 1 if w >= h else 0
            span = w if long_axis == 1 else h
            if span >= 16 and min(w, h) >= 6:
                step = int(self.rng.integers(13, 20))
                pos = int(self.rng.integers(5, step))
                while pos < span - 4:
                    if long_axis == 1:
                        c[1:-1, pos] *= 0.68
                        c[1:-1, pos + 1] *= 1.07
                        c[2:-2:3, pos - 1] *= 1.18
                    else:
                        c[pos, 1:-1] *= 0.68
                        c[pos + 1, 1:-1] *= 1.07
                        c[pos - 1, 2:-2:3] *= 1.18
                    pos += step
            # Rivets along the top edge of the larger side plates.
            if side and w >= 10 and h >= 8:
                c[2, 2:-2:3] *= 1.2
                c[3, 2:-2:3] *= 0.8


# ----------------------------------------------------------------------------------------------------------------
# Model tree

FACE_DIR = {'top': 'DOWN', 'bottom': 'UP', 'front': 'SOUTH', 'back': 'NORTH', 'right': 'EAST', 'left': 'WEST'}
ALL_FACES = ('top', 'bottom', 'front', 'back', 'right', 'left')


def f(v):
    s = ('%.4f' % v).rstrip('0').rstrip('.')
    if s in ('-0', ''):
        s = '0'
    return s + 'F'


class Box:
    def __init__(self, a, b, style, faces=None, sides=None):
        self.lo = tuple(min(a[i], b[i]) for i in range(3))
        self.hi = tuple(max(a[i], b[i]) for i in range(3))
        self.style = style
        self.faces = tuple(faces) if faces else ALL_FACES
        # Per-face style overrides, e.g. {'front': 'glass'}.
        self.sides = dict(sides or {})
        # Texels (and model units) per metre: 16, or more for a finely drawn inside (see Model.px).
        self.px = PX

    def px_size(self):
        return tuple(max(1, int(round((self.hi[i] - self.lo[i]) * self.px))) for i in range(3))

    def key(self):
        return (self.px_size(), self.style, self.faces, tuple(sorted(self.sides.items())))


def outward_face(a, b, pivot):
    """A thin pane of glass shows only its outer side: from inside a cab you look out through it."""
    lo = [min(a[i], b[i]) for i in range(3)]
    hi = [max(a[i], b[i]) for i in range(3)]
    dx = hi[0] - lo[0]
    dz = hi[2] - lo[2]
    if min(dx, dz) > 0.1:
        return None
    if dz <= dx:
        return ('front',) if (lo[2] + hi[2]) / 2 >= 0 else ('back',)
    return ('right',) if (lo[0] + hi[0]) / 2 >= 0 else ('left',)


def unrotate_x(p, pivot, deg):
    """Point p given where it is after turning the part by deg about x (around pivot): where it is at rest."""
    t = math.radians(-deg)
    y = p[1] - pivot[1]
    z = p[2] - pivot[2]
    # Forward rotation (raises the front): y' = y cos + z sin, z' = z cos - y sin; here with -deg.
    yr = y * math.cos(t) + z * math.sin(t)
    zr = z * math.cos(t) - y * math.sin(t)
    return (p[0], pivot[1] + round(yr, 6), pivot[2] + round(zr, 6))


class Part:
    def __init__(self, model, name, pivot, rot, parent):
        self.model = model
        self.name = name
        self.pivot = tuple(pivot)
        self.rot = tuple(rot)
        self.parent = parent
        self.boxes = []
        self.children = []
        # Boxes are given as they look turned by this many degrees about x (e.g. a vertical launcher drawn upright).
        self.design_x = 0

    def box(self, a, b, style=None, faces=None, sides=None):
        """Box between corners a and b (metres, vehicle space at rest, or as designed - see design_x)."""
        if self.design_x:
            a = unrotate_x(a, self.pivot, self.design_x)
            b = unrotate_x(b, self.pivot, self.design_x)
            if faces or sides:
                # Faces keep their names in the designed frame: top <-> front etc.
                k = round(self.design_x / 90) % 4
                cyc = ['top', 'back', 'bottom', 'front'] if k else None
                def mapf(f):
                    if cyc is None or f not in cyc:
                        return f
                    return cyc[(cyc.index(f) - k) % 4]
                if faces:
                    faces = tuple(mapf(f) for f in faces)
                if sides:
                    sides = {mapf(f): v for f, v in sides.items()}
        if style == 'glass' and faces is None and not self.design_x:
            faces = outward_face(a, b, self.pivot)
        bx = Box(a, b, style or self.model.paint, faces, sides)
        bx.px = self.model.px
        self.boxes.append(bx)
        return self

    def cbox(self, c, size, style=None, faces=None, sides=None):
        """Box by centre and size."""
        a = tuple(c[i] - size[i] / 2 for i in range(3))
        b = tuple(c[i] + size[i] / 2 for i in range(3))
        return self.box(a, b, style, faces, sides)

    def bbox(self, x, z, y0, size, style=None, faces=None, sides=None):
        """Box standing on height y0, centred at (x, z) with size (sx, sy, sz)."""
        sx, sy, sz = size
        return self.box((x - sx / 2, y0, z - sz / 2), (x + sx / 2, y0 + sy, z + sz / 2), style, faces, sides)

    def part(self, name, pivot=None, rot=(0, 0, 0)):
        return self.model.part(name, pivot if pivot is not None else self.pivot, rot, self)

    def path(self):
        p = self
        out = []
        while p is not None:
            out.append(p.name)
            p = p.parent
        return '/'.join(reversed(out))


class Model:
    def __init__(self, id, paint='camo', tex_width=1024, seed=1):
        self.id = id
        self.paint = paint
        self.tex_width = tex_width
        self.seed = seed
        self.parts = []
        self.roots = []
        # Geometry for the server.
        self.seats = []
        self.wheels = []
        self.turret = None
        self.turret_pivot = (0, 0, 0)
        self.elevator = None
        self.elevator_pivot = (0, 0, 0)
        self.deploy_elevation = 0.0
        self.fixed_elevation = 0.0
        self.rails = []
        self.spinner = None
        self.open_parts = []
        self.camera = 12.0
        self.wheelbase = 0.0
        self.tracked = False
        # Width of the chassis (when antennas or wings stick out further than the body).
        self.width = None
        # 1.26: the crew compartments (cabs, cockpits) that get an inside: dicts made by cab() / cockpit().
        self.cabs = []
        # Set on an inside model: the outside model it belongs to.
        self.interior_of = None
        # A stand-in for a hand-made model (VehicleModels.java): only its inside is generated.
        self.stub = False
        # Model units (and texels) per metre. The insides are drawn at twice the detail (the client scales them down).
        self.px = PX

    def part(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
        p = Part(self, name, pivot, rot, parent)
        self.parts.append(p)
        if parent is None:
            self.roots.append(p)
        else:
            parent.children.append(p)
        return p

    def seat(self, role, x, y, z):
        self.seats.append((role, x, y, z))

    def cab(self, x0, x1, y0, y1, z0, z1, kind='truck', part='body', **opts):
        """A crew compartment (inner walls, metres) that gets an inside (see interior.py): kind truck / armour / heli / jet."""
        self.cabs.append(dict(box=(min(x0, x1), max(x0, x1), min(y0, y1), max(y0, y1), min(z0, z1), max(z0, z1)), kind=kind, part=part, **opts))

    def glass_boxes(self):
        """Every window pane: (part, box)."""
        return [(p, b) for p in self.parts for b in p.boxes if b.style == 'glass']

    def set_turret(self, part, rate=0):
        self.turret = part.name
        self.turret_pivot = part.pivot

    def set_elevator(self, part, deploy=0.0, fixed=0.0):
        """The part that elevates (launcher, guns); rails are given relative to it."""
        self.elevator = part.name
        self._elev_abs = part.pivot
        self.deploy_elevation = deploy
        self.fixed_elevation = fixed
        tp = self.turret_pivot if self.turret else (0, 0, 0)
        self.elevator_pivot = tuple(part.pivot[i] - tp[i] for i in range(3))

    def set_virtual_elevator(self, name, pivot_abs, deploy, fixed):
        """A launch frame without a moving part (vertical launch cells)."""
        self.elevator = name
        self._elev_abs = pivot_abs
        self.deploy_elevation = deploy
        self.fixed_elevation = fixed
        tp = self.turret_pivot if self.turret else (0, 0, 0)
        self.elevator_pivot = tuple(pivot_abs[i] - tp[i] for i in range(3))

    def rail(self, part_name, pos, at=0.0):
        """Launch point (missile centre) where it is with the elevator at {at} degrees; stored at elevation 0."""
        p = unrotate_x(pos, self._elev_abs, at) if at else pos
        self.rails.append((part_name, p[0] - self._elev_abs[0], p[1] - self._elev_abs[1], p[2] - self._elev_abs[2]))

    def wheel(self, name, x, y, z, r, width, steer=False, side='wheel', tread='tire', parent=None):
        """A wheel turning about the x axis, pivot at its axle (x, y, z)."""
        p = self.part(name, (x, y, z), (0, 0, 0), parent)
        hw = width / 2
        # Octagon from three crossed slabs, tread faces only, plus the round sidewalls.
        a = r * 0.42
        p.box((x - hw, y - a, z - r), (x + hw, y + a, z + r), tread, faces=('top', 'bottom', 'front', 'back'))
        p.box((x - hw, y - r, z - a), (x + hw, y + r, z + a), tread, faces=('top', 'bottom', 'front', 'back'))
        p.box((x + hw, y - r, z - r), (x + hw + 0.04, y + r, z + r), side, faces=('right', 'left'))
        p.box((x - hw - 0.04, y - r, z - r), (x - hw, y + r, z + r), side, faces=('right', 'left'))
        for k, ang in enumerate((45, -45)):
            d = p.part(name + '_d' + str(k + 1), (x, y, z), (ang, 0, 0))
            d.box((x - hw, y - a, z - r), (x + hw, y + a, z + r), tread, faces=('top', 'bottom', 'front', 'back'))
        self.wheels.append((name, r, steer))
        return p

    # --- output -------------------------------------------------------------------------------------------------

    def bounds(self):
        lo = [1e9] * 3
        hi = [-1e9] * 3
        for p in self.parts:
            for b in p.boxes:
                for i in range(3):
                    lo[i] = min(lo[i], b.lo[i])
                    hi[i] = max(hi[i], b.hi[i])
        return lo, hi

    def layout(self):
        """Places every distinct box's texture region on the atlas (shelf packing)."""
        regions = {}
        for p in self.parts:
            for b in p.boxes:
                k = b.key()
                if k not in regions:
                    w, h, d = b.px_size()
                    regions[k] = (2 * (w + d), h + d, b)
        order = sorted(regions.items(), key=lambda kv: (-kv[1][1], -kv[1][0]))
        W = self.tex_width
        x = y = row_h = 0
        uv = {}
        for k, (rw, rh, b) in order:
            if rw > W:
                raise ValueError('%s: box too big for the atlas: %s' % (self.id, k))
            if x + rw > W:
                y += row_h
                x = 0
                row_h = 0
            uv[k] = (x, y)
            x += rw
            row_h = max(row_h, rh)
        H = y + row_h
        H = (H + 15) // 16 * 16
        return uv, regions, W, H

    def paint_atlas(self, uv, regions, W, H):
        img = np.zeros((H, W, 4), dtype=np.uint8)
        painter = Painter(self.seed)
        for k, (u, v) in uv.items():
            b = regions[k][2]
            w, h, d = b.px_size()
            rects = {
                'top': (u + d, v, w, d),
                'bottom': (u + d + w, v, w, d),
                'left': (u, v + d, d, h),
                'back': (u + d, v + d, w, h),
                'right': (u + d + w, v + d, d, h),
                'front': (u + 2 * d + w, v + d, w, h),
            }
            shade = {'top': 1.1, 'bottom': 0.7, 'left': 0.92, 'right': 0.92, 'front': 1.0, 'back': 0.95}
            for face in b.faces:
                x0, y0, fw, fh = rects[face]
                painter.face(img, x0, y0, fw, fh, b.sides.get(face, b.style), shade[face], face)
        return img

    def java_layer(self, uv, W, H, only=None, suffix='', paths=True):
        """The model's layer; {only}: 'solid' leaves the window glass out, 'glass' keeps only the glass."""
        lines = []
        lines.append('\tpublic static LayerDefinition %s%s() {' % (self.id, suffix))
        lines.append('\t\tMeshDefinition mesh = new MeshDefinition();')
        lines.append('\t\tPartDefinition root = mesh.getRoot();')
        names = {}
        counter = [0]

        def emit(p, parent_var, parent_pivot):
            var = 'p%d' % counter[0]
            counter[0] += 1
            names[p] = var
            px, py, pz = p.pivot
            ox = (px - parent_pivot[0]) * self.px
            oy = -(py - parent_pivot[1]) * self.px
            oz = (pz - parent_pivot[2]) * self.px
            lines.append('\t\tPartDefinition %s = %s.addOrReplaceChild("%s", CubeListBuilder.create()' % (var, parent_var, p.name))
            for b in p.boxes:
                if only == 'solid' and b.style == 'glass' or only == 'glass' and b.style != 'glass':
                    continue
                u, v = uv[b.key()]
                w, h, d = b.px_size()
                # Box min corner relative to the pivot, Minecraft space (y down).
                x0 = (b.lo[0] - px) * self.px
                y0 = -(b.hi[1] - py) * self.px
                z0 = (b.lo[2] - pz) * self.px
                faces = ''
                if set(b.faces) != set(ALL_FACES):
                    faces = ', EnumSet.of(%s)' % ', '.join('Direction.' + FACE_DIR[x] for x in b.faces)
                lines.append('\t\t\t\t.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s%s)' % (u, v, f(x0), f(y0), f(z0), f(w), f(h), f(d), faces))
            rx, ry, rz = (math.radians(a) for a in p.rot)
            lines.append('\t\t\t\t, PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));' % (f(ox), f(oy), f(oz), f(rx), f(ry), f(rz)))
            for c in p.children:
                emit(c, var, p.pivot)

        for r in self.roots:
            emit(r, 'root', (0, 0, 0))
        lines.append('\t\treturn LayerDefinition.create(mesh, %d, %d);' % (W, H))
        lines.append('\t}')
        if not paths:
            return '\n'.join(lines)
        lines.append('')
        lines.append('\tpublic static Map<String, String> %sPaths() {' % self.id)
        lines.append('\t\treturn Map.ofEntries(')
        ents = ['\t\t\t\tMap.entry("%s", "%s")' % (p.name, p.path()) for p in self.parts]
        lines.append(',\n'.join(ents))
        lines.append('\t\t);')
        lines.append('\t}')
        return '\n'.join(lines)

    def java_geometry(self, W, H):
        lo, hi = self.bounds()
        length = hi[2] - lo[2]
        width = self.width or (hi[0] - lo[0])
        height = hi[1]
        seats = ', '.join('new Seat("%s", %s, %s, %s)' % (r, f(x), f(y), f(z)) for r, x, y, z in self.seats)
        wheels = ', '.join('new Wheel("%s", %s, %s)' % (n, f(r), 'true' if s else 'false') for n, r, s in self.wheels)
        rails = ', '.join('new Rail("%s", %s, %s, %s)' % (n, f(x), f(y), f(z)) for n, x, y, z in self.rails)
        tp = self.turret_pivot
        ep = self.elevator_pivot
        opens = ', '.join('"%s"' % n for n in self.open_parts)

        def s(v):
            return 'null' if v is None else '"%s"' % v
        return ('\tpublic static final Geometry %s = new Geometry("%s", %s, %s, %s, %s, %s,\n'
                '\t\t\tnew Seat[]{%s},\n'
                '\t\t\tnew Wheel[]{%s},\n'
                '\t\t\t%s, new float[]{%s, %s, %s}, %s, new float[]{%s, %s, %s},\n'
                '\t\t\t%s, %s, new Rail[]{%s}, %s, new String[]{%s}, %s,\n'
                '\t\t\t%d, %d);\n') % (
            self.id.upper(), self.id, f(length), f(width), f(height), f(self.wheelbase), 'true' if self.tracked else 'false',
            seats, wheels, s(self.turret), f(tp[0]), f(tp[1]), f(tp[2]), s(self.elevator), f(ep[0]), f(ep[1]), f(ep[2]),
            f(self.deploy_elevation), f(self.fixed_elevation), rails, s(self.spinner), opens, f(self.camera), W, H)

    def icon(self, path):
        """A 32x32 side view (from the right), for the item."""
        lo, hi = self.bounds()
        length = hi[2] - lo[2]
        height = hi[1] - min(0, lo[1])
        size = 32
        scale = (size - 4) / max(length, height * 1.4)
        img = np.zeros((size, size, 4), dtype=np.uint8)
        boxes = []
        for p in self.parts:
            for b in p.boxes:
                boxes.append(b)
        boxes.sort(key=lambda b: b.hi[0])
        ox = (size - length * scale) / 2
        oy = size - 3
        for b in boxes:
            st = STYLES[b.sides.get('right', b.style)]
            if st['kind'] == 'mesh':
                c = rgb(st['base'])
            elif st['kind'] == 'wheel':
                c = rgb(st['base'])
            else:
                c = rgb(st['base'])
            z0 = int(math.floor(ox + (b.lo[2] - lo[2]) * scale))
            z1 = int(math.ceil(ox + (b.hi[2] - lo[2]) * scale))
            y0 = int(math.floor(oy - b.hi[1] * scale))
            y1 = int(math.ceil(oy - b.lo[1] * scale))
            # Nose to the right.
            xa, xb = size - z1, size - z0
            xa, xb = max(0, xa), min(size, xb)
            y0, y1 = max(0, y0), min(size, y1)
            if xb > xa and y1 > y0:
                img[y0:y1, xa:xb, :3] = c
                img[y0:y1, xa:xb, 3] = 255
        # Outline.
        a = img[..., 3] > 0
        out = np.zeros_like(a)
        out[1:, :] |= a[:-1, :]
        out[:-1, :] |= a[1:, :]
        out[:, 1:] |= a[:, :-1]
        out[:, :-1] |= a[:, 1:]
        edge = out & ~a
        img[edge] = (20, 22, 20, 255)
        # A little shading: lighter top half.
        Image.fromarray(img, 'RGBA').save(path)


def wreck(img, seed):
    rng = np.random.default_rng(seed)
    out = img.astype(np.float64).copy()
    h, w = img.shape[:2]
    lum = out[..., :3].mean(axis=2, keepdims=True)
    char = np.concatenate([lum * 0.28 + 8, lum * 0.25 + 6, lum * 0.22 + 5], axis=2)
    out[..., :3] = char + rng.normal(0, 4, (h, w, 1))
    # Rust patches.
    g = rng.random((h // 6 + 2, w // 6 + 2))
    z = nd_zoom(g, (h / g.shape[0] + 1e-4, w / g.shape[1] + 1e-4), order=1)[:h, :w]
    m = z > 0.72
    out[m, 0] = out[m, 0] * 0.6 + 70
    out[m, 1] = out[m, 1] * 0.6 + 38
    out[m, 2] = out[m, 2] * 0.6 + 20
    return np.clip(out, 0, 255).astype(np.uint8)


def build(models, java_main, java_client, tex_dir, item_dir, pkg_main, pkg_client):
    import interior
    layers = []
    geoms = []
    # Every layer id the client can ask for, and whose part paths it uses.
    ids = []
    insides = []
    for m in models:
        if m.cabs:
            insides.append(interior.build(m))
    stubs = [m for m in models if m.stub]
    for m in stubs:
        print('%-12s (hand-made) seats now %s' % (m.id, m.seats))
    for m in [m for m in models if not m.stub] + insides:
        uv, regions, W, H = m.layout()
        img = m.paint_atlas(uv, regions, W, H)
        Image.fromarray(img, 'RGBA').save(os.path.join(tex_dir, m.id + '.png'), optimize=True)
        if m.interior_of is None:
            if not getattr(m, 'no_item', False):
                Image.fromarray(wreck(img, m.seed + 99), 'RGBA').save(os.path.join(tex_dir, m.id + '_wreck.png'), optimize=True)
            if item_dir and not getattr(m, 'no_item', False):
                m.icon(os.path.join(item_dir, m.id + '.png'))
            geoms.append(m.java_geometry(W, H))
        has_glass = m.interior_of is None and any(True for _ in m.glass_boxes())
        layers.append(m.java_layer(uv, W, H, only='solid' if has_glass else None))
        ids.append((m.id, m.id))
        if has_glass:
            layers.append(m.java_layer(uv, W, H, only='glass', suffix='_glass', paths=False))
            ids.append((m.id + '_glass', m.id))
        print('%-12s parts %3d boxes %4d atlas %dx%d%s' % (m.id, len(m.parts), sum(len(p.boxes) for p in m.parts), W, H,
                                                          ' + glass' if has_glass else ''))
    with open(java_client, 'w') as out:
        out.write('package %s;\n\n' % pkg_client)
        out.write('import java.util.EnumSet;\nimport java.util.Map;\n\n')
        out.write('import net.minecraft.client.model.geom.PartPose;\n')
        out.write('import net.minecraft.client.model.geom.builders.CubeListBuilder;\n')
        out.write('import net.minecraft.client.model.geom.builders.LayerDefinition;\n')
        out.write('import net.minecraft.client.model.geom.builders.MeshDefinition;\n')
        out.write('import net.minecraft.client.model.geom.builders.PartDefinition;\n')
        out.write('import net.minecraft.core.Direction;\n\n')
        out.write('/** GENERATED by tools/models/build.py - do not edit. Real-size models (1 block = 1 m). */\n')
        out.write('@SuppressWarnings("unused")\npublic final class GenModels {\n\tprivate GenModels() {\n\t}\n\n')
        out.write('\t/** Is there a layer of this id (a vehicle, its window glass "<id>_glass", its inside "<id>_int")? */\n')
        out.write('\tpublic static boolean has(String id) {\n\t\treturn switch (id) {\n')
        out.write('\t\t\tcase %s -> true;\n' % ', '.join('"%s"' % i for i, _ in ids))
        out.write('\t\t\tdefault -> false;\n\t\t};\n\t}\n\n')
        out.write('\tpublic static LayerDefinition layer(String id) {\n\t\treturn switch (id) {\n')
        for i, _ in ids:
            out.write('\t\t\tcase "%s" -> %s();\n' % (i, i))
        out.write('\t\t\tdefault -> throw new IllegalArgumentException(id);\n\t\t};\n\t}\n\n')
        out.write('\tpublic static Map<String, String> paths(String id) {\n\t\treturn switch (id) {\n')
        for i, base in ids:
            out.write('\t\t\tcase "%s" -> %sPaths();\n' % (i, base))
        out.write('\t\t\tdefault -> throw new IllegalArgumentException(id);\n\t\t};\n\t}\n\n')
        out.write('\n\n'.join(layers))
        out.write('\n}\n')
    with open(java_main, 'w') as out:
        out.write('package %s;\n\n' % pkg_main)
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Geometry;\n')
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Rail;\n')
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Seat;\n')
        out.write('import com.stasdoto.airdefense.vehicle.VehicleGeometry.Wheel;\n\n')
        out.write('/** GENERATED by tools/models/build.py - do not edit. Geometry of the generated vehicles. */\n')
        out.write('@SuppressWarnings("unused")\npublic final class GenGeometry {\n\tprivate GenGeometry() {\n\t}\n\n')
        out.write('\n'.join(geoms))
        out.write('}\n')
