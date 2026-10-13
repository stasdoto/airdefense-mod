"""
2.0: how each material of a mesh is painted, texel by texel, from where the texel is in 3D (its position, normal, the
primitive's own coordinate, how open it is to the sky). Nothing is drawn flat: paint has a slow variation of tone, dust
settles low down and on top, grime gathers in the corners, metal has a sheen that changes with the way it faces.

The game lights the mesh by its normals itself (two lamps from above), so nothing here is lit from a direction - only the
ambient occlusion (corners and the underside darker) is baked in.
"""
import math
import os
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONT_DIR = '/usr/share/fonts/truetype/dejavu'


def hexc(h):
    return np.array(((h >> 16) & 255, (h >> 8) & 255, h & 255), dtype=np.float64)


# ---------------------------------------------------------------------------------------------------------------
# Noise


def _hash3(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float64) / float(0xFFFFFF)


def vnoise(p, scale=1.0, seed=0):
    """Smooth value noise in 3D (0..1), features about {scale} metres apart."""
    q = np.asarray(p, dtype=np.float64) / scale
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)
    out = np.zeros(len(q))
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                out += w * _hash3(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, seed)
    return out


def fbm(p, scale=1.0, seed=0, octaves=4, gain=0.5):
    total = np.zeros(len(p))
    amp = 1.0
    norm = 0.0
    s = scale
    for o in range(octaves):
        total += amp * vnoise(p, s, seed + o * 17)
        norm += amp
        amp *= gain
        s *= 0.5
    return total / norm


# ---------------------------------------------------------------------------------------------------------------
# Paint schemes


class Scheme:
    """How a model is painted: a plain colour or a camouflage (colours over a base, blobs about {blob} metres across,
    stretched {stretch} times along the length, slanted), how dirty it is (0 clean town car .. 1 army vehicle in the
    field), how worn (chipped edges, faded)."""

    def __init__(self, base=0xD8D8D8, camo=None, blob=1.4, stretch=1.6, slant=0.6, dirt=0.3, wear=0.2, seed=1, gloss=0.5,
                 second=None, stripes=None):
        self.base = hexc(base)
        self.camo = [hexc(c) for c in camo] if camo else []
        self.blob = blob
        self.stretch = stretch
        self.slant = slant
        self.dirt = dirt
        self.wear = wear
        self.seed = seed
        self.gloss = gloss
        # A second colour (two-tone buses), and stripes: [(y0, y1, colour)] in metres.
        self.second = hexc(second) if second is not None else None
        self.stripes = stripes or []


def camo_rgb(scheme, pos):
    """The camouflage colour at each point: soft-edged slanted swathes, the edges ragged."""
    base = np.tile(scheme.base, (len(pos), 1))
    if not scheme.camo:
        return base
    # Stretch along the length, slant the swathes (the way they are sprayed on real vehicles).
    q = pos.copy()
    q[:, 2] = pos[:, 2] / scheme.stretch + pos[:, 1] * scheme.slant
    out = base
    for k, col in enumerate(scheme.camo):
        f = fbm(q, scheme.blob, scheme.seed * 31 + k * 7, octaves=3) * 0.85 + vnoise(pos, 0.12, scheme.seed + 99 + k) * 0.15
        thr = 0.56 + 0.035 * k
        m = np.clip((f - thr) / 0.012, 0, 1)[:, None]
        out = out * (1 - m) + col * m
    return out


DUST = hexc(0x8C7B62)
MUD = hexc(0x4E4232)
GRIME = hexc(0x2E2A24)


def weather(rgb, T, scheme, amount=1.0, mud=True):
    """Dust low down and on top, grime in the corners, streaks under the edges, a slow variation of tone."""
    pos = T['pos']
    nrm = T['nrm']
    ao = T['ao']
    d = scheme.dirt * amount
    n = len(pos)
    var = fbm(pos, 0.9, scheme.seed + 5, 3)
    rgb = rgb * (0.94 + 0.12 * var)[:, None]
    # Fine speckle so the paint is not a flat plastic colour.
    rgb = rgb * (0.975 + 0.05 * vnoise(pos, 0.035, scheme.seed + 6))[:, None]
    if d > 0:
        y = pos[:, 1]
        # Dust and mud thrown up low on the sides: thicker toward the ground, ragged.
        ragged = fbm(pos * np.array([1, 2.5, 1]), 0.35, scheme.seed + 7, 3)
        low = np.clip((0.75 + 0.5 * ragged - y) / 0.75, 0, 1) ** 1.5 * d
        if mud:
            mcol = MUD * (0.85 + 0.3 * vnoise(pos, 0.08, scheme.seed + 8))[:, None]
            rgb = rgb * (1 - low[:, None] * 0.75) + mcol * (low[:, None] * 0.75)
        # Dust on the upward faces.
        up = np.clip(nrm[:, 1], 0, 1) ** 2 * (0.25 + 0.5 * fbm(pos, 0.5, scheme.seed + 9, 3)) * d * 0.6
        rgb = rgb * (1 - up[:, None]) + DUST * up[:, None]
        # Streaks running down the vertical faces.
        vert = 1 - np.abs(nrm[:, 1])
        st = vnoise(pos * np.array([9.0, 0.6, 9.0]), 1.0, scheme.seed + 10)
        streak = np.clip((st - 0.62) / 0.25, 0, 1) * vert * d * 0.35
        rgb = rgb * (1 - streak[:, None] * 0.5) + GRIME * (streak[:, None] * 0.5)
    # Grime in the corners (where the sky is hidden).
    g = np.clip((0.75 - ao) / 0.75, 0, 1) * (0.3 + 0.7 * max(d, 0.25))
    rgb = rgb * (1 - g[:, None] * 0.45) + GRIME * (g[:, None] * 0.45)
    return rgb


def occlusion(rgb, ao, strength=1.0):
    k = 1 - strength * (1 - (0.42 + 0.58 * np.clip(ao, 0, 1) ** 1.1))
    return rgb * k[:, None]


def sheen(rgb, nrm, amount):
    """Glossy paint catching the sky on its top faces, a darker band where it reflects the ground."""
    ny = nrm[:, 1]
    k = 1 + amount * (0.10 * np.clip(ny, 0, 1) - 0.08 * np.clip(-ny, 0, 1) + 0.05 * np.exp(-((ny - 0.15) / 0.12) ** 2))
    return rgb * k[:, None]


def env_metal(nrm, bright, dark, pos=None):
    """Chrome and polished metal: the sky above, a bright horizon, the dark ground (a fixed reflection)."""
    ny = nrm[:, 1]
    sky = bright[None, :] * (0.85 + 0.15 * np.clip(ny, 0, 1))[:, None]
    ground = dark
    t = np.clip((ny + 0.15) / 0.35, 0, 1)
    v = sky * t[:, None] + ground[None, :] * (1 - t)[:, None]
    hz = np.exp(-((ny - 0.05) / 0.08) ** 2)
    return v + (255 - v) * (0.5 * hz)[:, None]


# ---------------------------------------------------------------------------------------------------------------
# Text and small pictures (number plates, lettering)


_font_cache = {}


def font(size, bold=True):
    key = (size, bold)
    if key not in _font_cache:
        name = 'DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf'
        _font_cache[key] = ImageFont.truetype(os.path.join(FONT_DIR, name), size)
    return _font_cache[key]


def text_mask(text, w, h, bold=True, margin=0.08):
    """A mask (h, w) 0..1 of the text fitted into the box."""
    img = Image.new('L', (w * 4, h * 4), 0)
    d = ImageDraw.Draw(img)
    size = int(h * 4 * (1 - 2 * margin))
    f = font(max(6, size), bold)
    while size > 6:
        f = font(size, bold)
        bb = d.textbbox((0, 0), text, font=f)
        if bb[2] - bb[0] <= w * 4 * (1 - 2 * margin) and bb[3] - bb[1] <= h * 4 * (1 - 2 * margin):
            break
        size -= 2
    bb = d.textbbox((0, 0), text, font=f)
    x = (w * 4 - (bb[2] - bb[0])) / 2 - bb[0]
    y = (h * 4 - (bb[3] - bb[1])) / 2 - bb[1]
    d.text((x, y), text, fill=255, font=f)
    img = img.resize((w, h), Image.LANCZOS)
    return np.asarray(img, dtype=np.float64) / 255.0


def sample(img, uv):
    """Nearest sample of an (h, w[, c]) array at uv in 0..1 (v down the picture)."""
    h, w = img.shape[:2]
    x = np.clip((uv[:, 0] * w).astype(int), 0, w - 1)
    y = np.clip((uv[:, 1] * h).astype(int), 0, h - 1)
    return img[y, x]


class Decal:
    """A picture laid onto the paint from a direction: centre c, across u (metres), up v, the picture an (h, w, 4)
    array; reaches {depth} metres either side of its plane; only on faces turned toward it (facing)."""

    def __init__(self, c, u, v, img, depth=0.6, facing=None, double=False):
        self.c = np.asarray(c, dtype=np.float64)
        self.u = np.asarray(u, dtype=np.float64)
        self.v = np.asarray(v, dtype=np.float64)
        self.img = img
        self.depth = depth
        n = np.cross(self.u, self.v)
        self.n = n / (np.linalg.norm(n) or 1)
        self.facing = facing
        self.double = double

    def apply(self, rgb, pos, nrm):
        d = pos - self.c
        lu = np.linalg.norm(self.u)
        lv = np.linalg.norm(self.v)
        a = (d @ self.u) / (lu * lu) + 0.5
        b = (d @ self.v) / (lv * lv) + 0.5
        dist = d @ self.n
        face = nrm @ self.n
        ok = (a >= 0) & (a < 1) & (b >= 0) & (b < 1) & (np.abs(dist) < self.depth)
        ok &= (np.abs(face) > 0.35) if self.double else (face > 0.35)
        if not ok.any():
            return rgb
        uv = np.stack([a[ok], 1 - b[ok]], axis=1)
        px = sample(self.img, uv).astype(np.float64)
        al = px[:, 3:4] / 255.0
        out = rgb.copy()
        out[ok] = rgb[ok] * (1 - al) + px[:, :3] * al
        return out


def text_decal(c, u, v, text, fg, bg=None, px_per_m=200, bold=True, outline=None):
    """Lettering as a decal (u, v: the box it fills)."""
    w = max(8, int(np.linalg.norm(u) * px_per_m))
    h = max(6, int(np.linalg.norm(v) * px_per_m))
    m = text_mask(text, w, h, bold)
    img = np.zeros((h, w, 4))
    if bg is not None:
        img[..., :3] = bg
        img[..., 3] = 255
    fgc = np.asarray(fg, dtype=np.float64)
    img[..., :3] = img[..., :3] * (1 - m[..., None]) + fgc * m[..., None]
    img[..., 3] = np.maximum(img[..., 3], m * 255)
    return Decal(c, u, v, img)


def stripe_decal(c, u, v, colour, alpha=255):
    img = np.zeros((2, 2, 4))
    img[..., :3] = colour
    img[..., 3] = alpha
    return Decal(c, u, v, img)


# ---------------------------------------------------------------------------------------------------------------
# The materials


class Mat:
    def __init__(self, name, layer='solid', tint=False, tile=None):
        self.name = name
        self.layer = layer
        self.tint = tint
        self.tile = tile


LAYER = {'glass': 'glass', 'glass_light': 'glass', 'headlight_glass': 'glass', 'mesh': 'cutout'}
# Materials the client tints with the car's colour (when the model is tinted).
TINTED = {'paint'}


def layer_of(mat):
    return LAYER.get(mat, 'solid')


def plain(T, colour, scheme, dirt=0.5, ao=1.0, var=0.04):
    n = len(T['pos'])
    rgb = np.tile(hexc(colour), (n, 1))
    rgb = rgb * (1 - var + 2 * var * vnoise(T['pos'], 0.3, 3))[:, None]
    rgb = weather(rgb, T, scheme, dirt, mud=False)
    return occlusion(rgb, T['ao'], ao)


EXTRA = {}


def register(name, fn, layer=None, density=None, tinted=False):
    """Adds a material from another module: fn(T, scheme, model) -> (rgb (n,3), alpha (n,)). layer: 'glass' or 'cutout'
    (default solid); density: texels per metre relative to the model's."""
    EXTRA[name] = fn
    if layer:
        LAYER[name] = layer
    if density:
        import bake
        bake.DENSITY[name] = density
    if tinted:
        TINTED.add(name)


def shade(mat, T, scheme, model):
    """Colour (n, 3) and alpha (n,) of texels of one material."""
    pos, nrm, puv, ao = T['pos'], T['nrm'], T['puv'], T['ao']
    if mat in EXTRA:
        rgb, alpha = EXTRA[mat](T, scheme, model)
        return np.clip(rgb, 0, 255), np.asarray(alpha, dtype=np.float64) * np.ones(len(pos))
    n = len(pos)
    alpha = np.full(n, 255.0)
    tinted = model.tinted
    if mat in ('paint', 'paint2', 'paint_dark'):
        if mat == 'paint':
            rgb = np.full((n, 3), 236.0) if tinted else camo_rgb(scheme, pos)
            for y0, y1, col in scheme.stripes:
                m = (pos[:, 1] >= y0) & (pos[:, 1] < y1)
                rgb[m] = hexc(col) if not tinted else rgb[m]
        elif mat == 'paint2':
            rgb = np.tile(scheme.second if scheme.second is not None else scheme.base, (n, 1))
        else:
            rgb = np.tile(scheme.base * 0.55, (n, 1))
        for dcl in model.decals:
            rgb = dcl.apply(rgb, pos, nrm)
        rgb = sheen(rgb, nrm, scheme.gloss)
        rgb = weather(rgb, T, scheme)
        rgb = occlusion(rgb, ao)
    elif mat == 'glass':
        ny = nrm[:, 1]
        base = np.array((38, 52, 62), dtype=np.float64)
        sky = np.array((150, 170, 186), dtype=np.float64)
        t = np.clip(0.25 + 0.6 * ny, 0, 1)
        rgb = base * (1 - t)[:, None] + sky * t[:, None]
        # A soft streak of reflection.
        s = np.sin((pos[:, 0] * 0.7 + pos[:, 2] * 1.3 + pos[:, 1] * 2.0) * 2.4)
        rgb += (np.clip(s - 0.75, 0, 1) * 120)[:, None]
        rgb = weather(rgb, T, scheme, 0.3, mud=False)
        alpha = np.full(n, 150.0) + 40 * np.clip(ny, 0, 1)
    elif mat == 'glass_dark':
        rgb = np.tile(np.array((26, 32, 38.0)), (n, 1))
        rgb += (np.clip(nrm[:, 1], 0, 1) * 40)[:, None]
    elif mat == 'chrome':
        rgb = env_metal(nrm, np.array((214, 220, 226.0)), np.array((58, 60, 62.0)))
        rgb = occlusion(rgb, ao, 0.8)
    elif mat in ('rim', 'alloy'):
        rgb = env_metal(nrm, np.array((188, 192, 196.0)), np.array((92, 94, 98.0)))
        rgb = weather(rgb, T, scheme, 0.6, mud=False)
        rgb = occlusion(rgb, ao, 0.9)
    elif mat == 'rim_steel':
        rgb = plain(T, 0x50555A, scheme, 0.8)
    elif mat == 'rim_dark':
        rgb = plain(T, 0x26282A, scheme, 0.3)
    elif mat == 'bolt':
        rgb = env_metal(nrm, np.array((170, 172, 176.0)), np.array((70, 70, 72.0)))
        rgb = occlusion(rgb, ao, 0.7)
    elif mat == 'tyre':
        # The tread: blocks and grooves round the tyre and across it.
        u = puv[:, 0]
        t = puv[:, 1]
        groove = (np.abs(((u * 64) % 1.0) - 0.5) < 0.12) | (np.abs(((t * 9 + 0.5) % 1.0) - 0.5) < 0.1)
        rgb = np.tile(np.array((34, 34, 35.0)), (n, 1))
        rgb[groove] *= 0.55
        rgb = weather(rgb, T, scheme, 0.7)
        rgb = occlusion(rgb, ao, 0.8)
    elif mat == 'tyre_wall':
        rgb = np.tile(np.array((30, 30, 31.0)), (n, 1))
        # A raised ring of lettering round the sidewall: faint lighter dashes.
        u = puv[:, 0]
        ring = (np.abs(((u * 40) % 1.0) - 0.5) < 0.3) & (np.abs(np.sin(puv[:, 1] * 50)) > 0.6)
        rgb[ring] *= 1.25
        rgb = weather(rgb, T, scheme, 0.6)
        rgb = occlusion(rgb, ao, 0.8)
    elif mat == 'rubber':
        rgb = plain(T, 0x1C1D1E, scheme, 0.4, 0.8)
    elif mat == 'plastic':
        rgb = plain(T, 0x2A2C2E, scheme, 0.5)
    elif mat == 'plastic_grey':
        rgb = plain(T, 0x5A5E62, scheme, 0.5)
    elif mat == 'trim':
        rgb = plain(T, 0x16171A, scheme, 0.3)
    elif mat == 'grille':
        # Horizontal slats, dark between them.
        v = puv[:, 1]
        slat = ((v * 10) % 1.0) < 0.45
        rgb = np.tile(np.array((24, 25, 27.0)), (n, 1))
        rgb[slat] = (58, 60, 63)
        rgb = occlusion(rgb, ao, 0.7)
    elif mat == 'grille_mesh':
        u, v = puv[:, 0], puv[:, 1]
        hole = (((u * 24) % 1.0) > 0.35) & (((v * 12) % 1.0) > 0.3)
        rgb = np.tile(np.array((64, 66, 68.0)), (n, 1))
        rgb[hole] = (14, 14, 15)
        rgb = occlusion(rgb, ao, 0.6)
    elif mat == 'headlight':
        # A reflector bowl behind a clear lens: bright centre, rings, a chrome surround.
        u, v = puv[:, 0] - 0.5, puv[:, 1] - 0.5
        r = np.sqrt(u * u + v * v) * 2
        rgb = np.tile(np.array((210, 214, 218.0)), (n, 1))
        rgb *= (0.75 + 0.25 * np.cos(r * 18))[:, None]
        rgb[r < 0.28] = (250, 248, 236)
        rgb[r > 0.92] = (120, 124, 128)
    elif mat == 'headlight_box':
        # A modern lamp unit: a dark housing with two round reflectors side by side.
        u, v = puv[:, 0], puv[:, 1]
        rgb = np.tile(np.array((40, 42, 46.0)), (n, 1))
        for cx in (0.3, 0.72):
            r = np.sqrt(((u - cx) * 2.2) ** 2 + ((v - 0.5) * 1.3) ** 2)
            m = r < 0.55
            col = np.array((205, 210, 216.0))
            rgb[m] = col * (0.8 + 0.2 * np.cos(r[m] * 20))[:, None]
            rgb[r < 0.18] = (248, 246, 232)
    elif mat == 'taillight':
        u, v = puv[:, 0], puv[:, 1]
        rgb = np.tile(np.array((150, 24, 20.0)), (n, 1))
        rgb[(((u * 6) % 1.0) < 0.12) | (((v * 3) % 1.0) < 0.1)] = (110, 16, 14)
        rgb[(u > 0.62) & (v > 0.15) & (v < 0.85)] = (200, 120, 40)
        rgb[(u > 0.62) & (u < 0.66)] = (60, 20, 18)
    elif mat == 'indicator':
        rgb = np.tile(np.array((220, 140, 40.0)), (n, 1))
        rgb *= (0.85 + 0.15 * np.cos((puv[:, 0] - 0.5) * 6))[:, None]
    elif mat == 'redlight':
        rgb = np.tile(np.array((190, 30, 24.0)), (n, 1))
    elif mat == 'lamp':
        rgb = np.tile(np.array((240, 236, 214.0)), (n, 1))
    elif mat == 'beacon_blue':
        rgb = np.tile(np.array((40, 80, 230.0)), (n, 1)) * (0.8 + 0.2 * np.clip(nrm[:, 1], 0, 1))[:, None]
        alpha[:] = 255
    elif mat == 'beacon_red':
        rgb = np.tile(np.array((230, 40, 30.0)), (n, 1)) * (0.8 + 0.2 * np.clip(nrm[:, 1], 0, 1))[:, None]
    elif mat == 'plate':
        rgb = plate(T, model)
    elif mat == 'seat':
        rgb = plain(T, 0x34363A, scheme, 0.1, 0.9)
        rgb *= (0.92 + 0.08 * np.sin(puv[:, 1] * 60))[:, None]
    elif mat == 'seat_beige':
        rgb = plain(T, 0x9C8C70, scheme, 0.1, 0.9)
        rgb *= (0.92 + 0.08 * np.sin(puv[:, 1] * 60))[:, None]
    elif mat == 'dash':
        rgb = plain(T, 0x232427, scheme, 0.1, 0.8)
    elif mat == 'gauges':
        u, v = puv[:, 0], puv[:, 1]
        rgb = np.tile(np.array((20, 20, 22.0)), (n, 1))
        for cx in (0.3, 0.7):
            r = np.sqrt((u - cx) ** 2 * 4 + (v - 0.5) ** 2)
            ang = np.arctan2(v - 0.5, u - cx)
            ring = (r > 0.36) & (r < 0.42)
            tick = (r > 0.3) & (r < 0.42) & (np.abs(((ang * 8 / np.pi) % 1.0) - 0.5) < 0.12)
            rgb[ring | tick] = (210, 210, 205)
            needle = (r < 0.36) & (np.abs(ang - 0.6) < 0.06)
            rgb[needle] = (230, 90, 40)
    elif mat == 'carpet':
        rgb = plain(T, 0x222324, scheme, 0.2, 0.9)
    elif mat == 'interior':
        rgb = plain(T, 0x47494C, scheme, 0.1, 0.9)
    elif mat == 'interior_light':
        rgb = plain(T, 0x9A9890, scheme, 0.1, 0.9)
    elif mat == 'steel':
        rgb = env_metal(nrm, np.array((150, 154, 158.0)), np.array((70, 72, 74.0)))
        rgb = weather(rgb, T, scheme, 0.8)
        rgb = occlusion(rgb, ao, 0.9)
    elif mat == 'metal_dark':
        rgb = plain(T, 0x3A3C3E, scheme, 0.9)
    elif mat == 'rust':
        rgb = plain(T, 0x6A4630, scheme, 1.0)
    elif mat == 'exhaust':
        rgb = plain(T, 0x3E3A36, scheme, 1.0)
        rgb[puv[:, 1] > 0.0] *= 0.9
    elif mat == 'black':
        rgb = plain(T, 0x0E0E0F, scheme, 0.2, 0.5)
    elif mat == 'white':
        rgb = plain(T, 0xE4E6E6, scheme, 0.4)
    elif mat == 'yellow':
        rgb = plain(T, 0xE0B020, scheme, 0.5)
    elif mat == 'screen':
        rgb = np.tile(np.array((240, 170, 50.0)), (n, 1))
        u, v = puv[:, 0], puv[:, 1]
        dots = (((u * 60) % 1.0) < 0.5) & (((v * 8) % 1.0) < 0.6)
        rgb[~dots] = (40, 26, 8)
    elif mat == 'wiper':
        rgb = plain(T, 0x141516, scheme, 0.2, 0.5)
    elif mat == 'mirror_glass':
        rgb = env_metal(nrm, np.array((180, 196, 210.0)), np.array((50, 56, 60.0)))
    elif mat == 'canvas':
        rgb = camo_rgb(scheme, pos) * 0.0 + hexc(0x5E5C40)
        rgb *= (0.92 + 0.08 * np.sin(pos[:, 2] * 40))[:, None]
        rgb = weather(rgb, T, scheme)
        rgb = occlusion(rgb, ao)
    elif mat == 'track':
        rgb = np.tile(np.array((52, 50, 46.0)), (n, 1))
        rgb = weather(rgb, T, scheme, 1.2)
        rgb = occlusion(rgb, ao)
    else:
        raise ValueError('unknown material ' + mat)
    return np.clip(rgb, 0, 255), alpha


def plate(T, model):
    """A number plate: white, a black border, the number (each model its own)."""
    puv = T['puv']
    n = len(puv)
    key = getattr(model, 'plate_text', None) or plate_number(model.id)
    if key not in _plate_cache:
        w, h = 260, 56
        img = np.full((h, w, 3), 236.0)
        img[:3, :] = img[-3:, :] = 20
        img[:, :3] = img[:, -3:] = 20
        m = text_mask(key, w - 40, h - 8, True, 0.04)
        sub = img[4:h - 4, 6:w - 34]
        sub[:] = sub * (1 - m[..., None]) + 20 * m[..., None]
        # The flag strip at the right.
        img[6:h - 6, w - 30:w - 6] = (230, 230, 230)
        img[6:h // 2, w - 30:w - 6] = (236, 236, 236)
        img[h // 2 - 6:h // 2 + 4, w - 30:w - 6] = (40, 80, 170)
        img[h // 2 + 4:h - 6, w - 30:w - 6] = (200, 40, 40)
        _plate_cache[key] = img
    img = _plate_cache[key]
    uv = np.stack([puv[:, 0], 1 - puv[:, 1]], axis=1)
    return sample(img, uv).astype(np.float64)


_plate_cache = {}


def plate_number(seed_text):
    rng = np.random.default_rng(zlib.crc32(seed_text.encode()))
    letters = 'АВЕКМНОРСТУХ'
    return '%s %03d %s%s' % (letters[rng.integers(len(letters))], rng.integers(1, 999), letters[rng.integers(len(letters))],
                            letters[rng.integers(len(letters))])
