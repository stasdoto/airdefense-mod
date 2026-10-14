"""
2.0 town vehicles: helpers shared by the cars, buses, trams and service vehicles (m_town.py).

  * Caster: rays against a body's own surface, so lamps, grilles, plates, handles and decal panels are laid exactly on
    the curved skin (no floating or sunken parts): patch() builds a thin shell a few millimetres over the skin, its
    primitive uv running 0..1 across it (the lamp materials draw their insides from that).
  * The town's own materials (lamp units, grilles, frit, arch liners, fabrics, lettering) registered with materials.py.
  * Wheels (low-poly but round: tyre with shoulders, alloy spokes or a pressed steel disc), seats, steering wheels,
    mirrors, light bars.
  * line_decal(): shut lines, creases and stripes drawn as a picture laid on the paint.
"""
import math

import numpy as np
from PIL import Image, ImageDraw

import geom
import materials
from core import Geo, face_normal, normalize, rot_x, rot_y, rot_z
from materials import hexc, plain, occlusion, sheen, weather, env_metal, vnoise, fbm, text_mask, sample

# ---------------------------------------------------------------------------------------------------------------
# Small geometry helpers


def about(g, c, R):
    c = np.asarray(c, dtype=np.float64)
    g.transform(t=-c)
    g.transform(R=R)
    g.transform(t=c)
    return g


def flip(g):
    """The same polygons facing the other way (inside linings)."""
    for p in g.polys:
        p.v = p.v[::-1].copy()
        if p.uv is not None:
            p.uv = p.uv[::-1].copy()
        if p.n is not None:
            p.n = -p.n[::-1].copy()
    return g


def orient(g, inside_pt, outward=True):
    """Turns each polygon to face away from (or toward) a point."""
    c = np.asarray(inside_pt, dtype=np.float64)
    for p in g.polys:
        fn = face_normal(p.v)
        d = np.dot(fn, p.v.mean(axis=0) - c)
        if (d < 0) == outward:
            p.v = p.v[::-1].copy()
            if p.uv is not None:
                p.uv = p.uv[::-1].copy()
            if p.n is not None:
                p.n = p.n[::-1].copy()
    return g


def bar(a, b, w, h, mat, up=(0, 1, 0), caps=True):
    """A rectangular bar from a to b (w across, h along {up})."""
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    d = normalize(b - a)
    upv = np.asarray(up, dtype=np.float64)
    s = normalize(np.cross(d, upv))
    if np.linalg.norm(np.cross(d, upv)) < 1e-6:
        s = normalize(np.cross(d, [1.0, 0, 0]))
    u = np.cross(s, d)
    ring = lambda c: [c + s * w / 2 + u * h / 2, c - s * w / 2 + u * h / 2, c - s * w / 2 - u * h / 2, c + s * w / 2 - u * h / 2]
    g = geom.loft([ring(a), ring(b)], mat, closed=True, cap0=caps, cap1=caps, smooth=20)
    return orient(g, (a + b) / 2)


def hbox(lo, hi, mat, r=0.0, segs=1, faces=None, mats=None):
    return geom.rbox(lo, hi, mat, r, segs, faces, mats)


# ---------------------------------------------------------------------------------------------------------------
# Rays against a surface, patches laid on it


class Caster:
    def __init__(self, *geos):
        A, B, C, N = [], [], [], []
        for g in geos:
            for p in g.polys:
                v = p.v
                fn = face_normal(v)
                for i in range(1, len(v) - 1):
                    A.append(v[0])
                    B.append(v[i])
                    C.append(v[i + 1])
                    N.append(fn)
        self.A = np.array(A)
        self.B = np.array(B)
        self.C = np.array(C)
        self.N = np.array(N)
        self.E1 = self.B - self.A
        self.E2 = self.C - self.A

    def cast(self, O, D):
        """Nearest hits of rays O + t D (t > 0): (t, point, normal); t = inf where nothing is hit."""
        O = np.atleast_2d(np.asarray(O, dtype=np.float64))
        D = np.atleast_2d(np.asarray(D, dtype=np.float64))
        D = np.broadcast_to(D, O.shape)
        ts = np.full(len(O), np.inf)
        ns = np.zeros((len(O), 3))
        for s0 in range(0, len(O), 64):
            o = O[s0:s0 + 64, None, :]
            d = D[s0:s0 + 64, None, :]
            p = np.cross(d, self.E2[None])
            det = np.einsum('rnk,nk->rn', p, self.E1)
            ok = np.abs(det) > 1e-12
            inv = np.where(ok, 1.0 / np.where(ok, det, 1), 0)
            tv = o - self.A[None]
            u = np.einsum('rnk,rnk->rn', tv, p) * inv
            q = np.cross(tv, self.E1[None])
            v = np.einsum('rnk,rnk->rn', np.broadcast_to(d, q.shape), q) * inv
            t = np.einsum('nk,rnk->rn', self.E2, q) * inv
            hit = ok & (u >= -1e-6) & (v >= -1e-6) & (u + v <= 1 + 1e-6) & (t > 1e-6)
            t = np.where(hit, t, np.inf)
            k = np.argmin(t, axis=1)
            tt = t[np.arange(len(k)), k]
            ts[s0:s0 + 64] = tt
            ns[s0:s0 + 64] = self.N[k]
        P = O + D * np.where(np.isinf(ts), 0, ts)[:, None]
        return ts, P, ns

    def hit(self, o, d):
        t, P, n = self.cast([o], [d])
        if np.isinf(t[0]):
            return None, None
        return P[0], n[0]


def patch(caster, c, U, V, D, mat, na=8, nb=3, shape=None, offset=0.004, back=0.6, smooth=60, skirt=0.0, skirt_mat=None):
    """A thin shell laid on the surface: the rectangle c + a U + b V (a, b in -0.5..0.5, or a shape(a, b) -> (a', b')
    for a, b in 0..1 giving the place in that rectangle's -0.5..0.5 coordinates) projected along D onto the skin and
    lifted {offset} along its normal. primitive uv: (a, b) 0..1. skirt: a rim of that depth down to the skin."""
    c = np.asarray(c, dtype=np.float64)
    U = np.asarray(U, dtype=np.float64)
    V = np.asarray(V, dtype=np.float64)
    D = normalize(np.asarray(D, dtype=np.float64))
    A = np.linspace(0, 1, na + 1)
    Bs = np.linspace(0, 1, nb + 1)
    pts = []
    for a in A:
        for b in Bs:
            if shape is not None:
                x, y = shape(a, b)
            else:
                x, y = a - 0.5, b - 0.5
            pts.append(c + x * U + y * V - D * back)
    pts = np.array(pts)
    t, P, N = caster.cast(pts, D)
    G = P.reshape(na + 1, nb + 1, 3)
    Nn = N.reshape(na + 1, nb + 1, 3)
    T = t.reshape(na + 1, nb + 1)
    # Normals averaged over the neighbouring hits (smooth lift).
    L = G + Nn * offset
    g = Geo(smooth)
    want = normalize(np.cross(U, V))
    for i in range(na):
        for j in range(nb):
            if not np.isfinite(T[i:i + 2, j:j + 2]).all():
                continue
            vv = np.array([L[i, j], L[i + 1, j], L[i + 1, j + 1], L[i, j + 1]])
            uv = np.array([(A[i], Bs[j]), (A[i + 1], Bs[j]), (A[i + 1], Bs[j + 1]), (A[i], Bs[j + 1])])
            fn = face_normal(vv)
            if np.dot(fn, -D) < 0:
                vv, uv = vv[::-1], uv[::-1]
            g.add(vv, mat, uv=uv)
    if skirt > 0:
        sm = skirt_mat or mat
        edge = [(i, 0) for i in range(na + 1)] + [(na, j) for j in range(1, nb + 1)] + \
               [(i, nb) for i in range(na - 1, -1, -1)] + [(0, j) for j in range(nb - 1, 0, -1)]
        ring_top = [L[i, j] for i, j in edge]
        ring_bot = [G[i, j] - Nn[i, j] * skirt for i, j in edge]
        sk = Geo(30)
        cc = G.reshape(-1, 3).mean(axis=0) - (-D) * 0.05
        for k in range(len(edge)):
            k2 = (k + 1) % len(edge)
            i0, j0 = edge[k]
            i1, j1 = edge[k2]
            if not (np.isfinite(T[i0, j0]) and np.isfinite(T[i1, j1])):
                continue
            vv = np.array([ring_bot[k], ring_bot[k2], ring_top[k2], ring_top[k]])
            sk.add(vv, sm, uv=np.array([(0, 0), (1, 0), (1, 1), (0, 1)]))
        orient(sk, cc)
        g.extend(sk)
    return g


def surf_point(caster, o, d):
    p, n = caster.hit(o, d)
    return p, n


def ellipse_shape(a, b):
    """a: across 0..1, b: 0..1 from the bottom to the top of an ellipse."""
    x = a - 0.5
    h = math.sqrt(max(0.0, 0.25 - x * x)) * 2 * 0.999
    return x, (b - 0.5) * h


def taper_shape(top0, top1, bot0=0.0, bot1=0.0, round_=0.0):
    """A lamp outline across a (0..1 = -0.5..0.5 of the patch's width): the top set down from the patch's top edge by top0
    at a = 0 to top1 at a = 1, the bottom set up by bot0 to bot1 (shares of the height), the ends rounded by {round_}."""
    def f(a, b):
        x = a - 0.5
        t = 0.5 - (top0 + (top1 - top0) * a)
        bo = -0.5 + bot0 + (bot1 - bot0) * a
        if round_ > 0:
            e = min(a, 1 - a)
            if e < round_:
                k = 1 - math.sqrt(max(0.0, 1 - ((round_ - e) / round_) ** 2))
                mid = (t + bo) / 2
                t = t - (t - mid) * k
                bo = bo + (mid - bo) * k
        return x, bo + (t - bo) * b
    return f


# ---------------------------------------------------------------------------------------------------------------
# Decals drawn with lines (shut lines, creases, stripes)


class RegionDecal(materials.Decal):
    """A decal that only lands where region(pos) is true (so a bonnet's lines do not smear up over the roof)."""

    def __init__(self, c, u, v, img, region, depth=0.6, facing=None, double=False):
        super().__init__(c, u, v, img, depth, facing, double)
        self.region = region

    def apply(self, rgb, pos, nrm):
        out = super().apply(rgb, pos, nrm)
        ok = self.region(pos)
        return np.where(ok[:, None], out, rgb)


class Canvas:
    """A picture to lay on the paint from a direction: world points map to pixels."""

    def __init__(self, c, u, v, ppm=110):
        self.c = np.asarray(c, dtype=np.float64)
        self.u = np.asarray(u, dtype=np.float64)
        self.v = np.asarray(v, dtype=np.float64)
        self.w = max(8, int(np.linalg.norm(self.u) * ppm))
        self.h = max(8, int(np.linalg.norm(self.v) * ppm))
        self.ppm = ppm
        self.img = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def px(self, p):
        d = np.asarray(p, dtype=np.float64) - self.c
        a = d @ self.u / (self.u @ self.u) + 0.5
        b = d @ self.v / (self.v @ self.v) + 0.5
        return (a * self.w, (1 - b) * self.h)

    def line(self, pts, rgba=(20, 20, 20, 170), width=2, light=None):
        P = [self.px(p) for p in pts]
        if light is not None:
            # A light edge just above the gap (the panel's lip catching the light).
            Q = [(x, y - width) for x, y in P]
            self.d.line(Q, fill=light, width=max(1, width - 1), joint='curve')
        self.d.line(P, fill=rgba, width=width, joint='curve')

    def poly(self, pts, rgba):
        self.d.polygon([self.px(p) for p in pts], fill=rgba)

    def rect(self, p0, p1, rgba):
        a = self.px(p0)
        b = self.px(p1)
        self.d.rectangle([min(a[0], b[0]), min(a[1], b[1]), max(a[0], b[0]), max(a[1], b[1])], fill=rgba)

    def text(self, c, size_u, size_v, text, rgb, bold=True):
        """Lettering centred at world point c, size in metres along u and v."""
        w = max(8, int(size_u * self.ppm))
        h = max(6, int(size_v * self.ppm))
        m = text_mask(text, w, h, bold, 0.02)
        a = (np.clip(m, 0, 1) * 255).astype(np.uint8)
        im = Image.new('RGBA', (w, h), tuple(int(x) for x in rgb) + (0,))
        im.putalpha(Image.fromarray(a))
        x, y = self.px(c)
        self.img.alpha_composite(im, (int(x - w / 2), int(y - h / 2)))

    def decal(self, depth=0.5, double=False, region=None):
        arr = np.asarray(self.img, dtype=np.float64)
        if region is not None:
            return RegionDecal(self.c, self.u, self.v, arr, region, depth=depth, double=double)
        return materials.Decal(self.c, self.u, self.v, arr, depth=depth, double=double)


# ---------------------------------------------------------------------------------------------------------------
# Materials


def _env(nrm, top, low):
    return env_metal(nrm, np.asarray(top, dtype=np.float64), np.asarray(low, dtype=np.float64))


def _dims(model, mat):
    return getattr(model, 'tl_dims', {}).get(mat, (1.0, 1.0))


def m_frit(T, scheme, model):
    n = len(T['pos'])
    rgb = np.tile(np.array((16, 17, 19.0)), (n, 1))
    rgb = sheen(rgb, T['nrm'], 1.2)
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 18)[:, None]
    return occlusion(rgb, T['ao'], 0.5), 255


def m_liner(T, scheme, model):
    rgb = plain(T, 0x1E1F20, scheme, 1.4, 0.9)
    rib = np.abs(np.sin(T['pos'][:, 2] * 90 + T['pos'][:, 1] * 30)) > 0.85
    rgb[rib] *= 0.8
    return rgb, 255


def m_under(T, scheme, model):
    return plain(T, 0x2C2A27, scheme, 1.5, 1.0), 255


def m_headliner(T, scheme, model):
    rgb = plain(T, 0x8E8C86, scheme, 0.05, 0.9)
    return rgb, 255


def m_door_card(T, scheme, model):
    rgb = plain(T, 0x3A3B3E, scheme, 0.05, 0.9)
    y = T['pos'][:, 1]
    rgb[(np.abs(y - 0.72) < 0.03)] *= 1.3
    return rgb, 255


def m_head(T, scheme, model):
    """A modern headlamp: smoked chrome housing, a projector lens, a reflector, an LED strip along the bottom."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    Lu, Lv = _dims(model, 'tl_head')
    x, y = a * Lu, b * Lv
    n = len(a)
    base = _env(T['nrm'], (150, 156, 164), (40, 42, 46))
    rgb = base * 0.55 + 18
    # Projector lens (inner end) and the reflector bowl (outer end).
    for cx, r, k in ((0.3 * Lu, 0.36 * Lv, 1), (0.66 * Lu, 0.42 * Lv, 0)):
        d = np.sqrt((x - cx) ** 2 + (y - 0.56 * Lv) ** 2)
        ring = (d < r) & (d > r * 0.78)
        rgb[ring] = (190, 196, 204)
        inner = d <= r * 0.78
        if k:
            rgb[inner] = np.array((24, 28, 36.0)) + (np.clip(1 - d[inner] / (r * 0.78), 0, 1) ** 2 * 120)[:, None]
        else:
            rgb[inner] = (205, 210, 216)
            rgb[inner] *= (0.8 + 0.2 * np.cos(d[inner] / r * 14))[:, None]
            rgb[d < r * 0.2] = (252, 250, 240)
    # The LED daytime strip along the bottom edge, curving up at the outer end.
    led = (y < 0.2 * Lv) & (y > 0.07 * Lv) & (x > 0.08 * Lu)
    led |= (x > 0.9 * Lu) & (x < 0.96 * Lu) & (y < 0.75 * Lv) & (y > 0.07 * Lv)
    rgb[led] = (250, 252, 255)
    # The black edge seal.
    edge = (a < 0.025) | (a > 0.975) | (b < 0.05) | (b > 0.95)
    rgb[edge] = (20, 20, 22)
    # The clear lens: a highlight across the top.
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 30)[:, None]
    return occlusion(rgb, T['ao'], 0.4), 255


def m_head_sq(T, scheme, model):
    """The old saloon's rectangular lamp unit: a chrome rim, a fluted glass over two bowls."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    Lu, Lv = _dims(model, 'tl_head_sq')
    x, y = a * Lu, b * Lv
    rgb = np.tile(np.array((196, 200, 206.0)), (len(a), 1))
    # Flutes: vertical ribs of the lens.
    rgb *= (0.86 + 0.14 * np.cos(x * 2 * math.pi / 0.012))[:, None]
    for cx in (0.3, 0.72):
        d = np.sqrt(((x - cx * Lu)) ** 2 + (y - 0.5 * Lv) ** 2)
        r = 0.44 * Lv
        rgb[(d < r) & (d > r * 0.9)] *= 0.75
        rgb[d < r * 0.22] = (250, 248, 232)
    rim = (a < 0.04) | (a > 0.96) | (b < 0.1) | (b > 0.9)
    rgb[rim] = _env(T['nrm'], (226, 230, 236), (70, 72, 76))[rim]
    return occlusion(rgb, T['ao'], 0.4), 255


def m_tail(T, scheme, model):
    """A modern tail lamp: deep red lens, an LED loop, the white reversing light and the amber indicator inboard."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    Lu, Lv = _dims(model, 'tl_tail')
    x, y = a * Lu, b * Lv
    rgb = np.tile(np.array((128, 14, 16.0)), (len(a), 1))
    rgb *= (0.85 + 0.15 * np.cos(y / Lv * 9))[:, None]
    # The LED loop (a C round the outer end).
    d = np.sqrt(((x - 0.62 * Lu) / (0.32 * Lu)) ** 2 + ((y - 0.5 * Lv) / (0.32 * Lv)) ** 2)
    rgb[(d > 0.8) & (d < 1.0) & (x > 0.35 * Lu)] = (235, 60, 50)
    # The reversing light and the indicator at the inner end.
    inner = x < 0.3 * Lu
    rgb[inner & (y > 0.5 * Lv)] = (205, 206, 210)
    rgb[inner & (y <= 0.5 * Lv)] = (215, 130, 40)
    rgb[(np.abs(x - 0.3 * Lu) < 0.006)] = (40, 10, 10)
    edge = (a < 0.02) | (a > 0.98) | (b < 0.05) | (b > 0.95)
    rgb[edge] = (24, 18, 18)
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 30)[:, None]
    return occlusion(rgb, T['ao'], 0.4), 255


def m_tail_old(T, scheme, model):
    """The old saloon's wide ribbed tail lamps: amber, red, white, red sections in a chrome frame."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    rgb = np.tile(np.array((150, 22, 20.0)), (len(a), 1))
    rgb[(a > 0.62) & (a < 0.82)] = (210, 210, 212)
    rgb[a <= 0.28] = (210, 120, 30)
    rgb *= (0.82 + 0.18 * np.cos(b * 2 * math.pi * 7))[:, None]
    rgb *= (0.9 + 0.1 * np.cos(a * 2 * math.pi * 18))[:, None]
    sep = (np.abs(a - 0.28) < 0.01) | (np.abs(a - 0.62) < 0.01) | (np.abs(a - 0.82) < 0.01)
    rim = (a < 0.02) | (a > 0.98) | (b < 0.07) | (b > 0.93)
    rgb[sep | rim] = _env(T['nrm'], (220, 224, 230), (70, 72, 76))[sep | rim]
    return occlusion(rgb, T['ao'], 0.4), 255


def m_grille(T, scheme, model):
    """A modern grille: black gloss slats, a thin chrome frame along the top."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    Lu, Lv = _dims(model, 'tl_grille')
    y = b * Lv
    rgb = np.tile(np.array((12, 12, 13.0)), (len(a), 1))
    slat = ((y / 0.028) % 1.0) < 0.5
    rgb[slat] = (44, 46, 50)
    rgb[slat] += (np.clip(T['nrm'][slat, 1], 0, 1) * 30)[:, None]
    chrome = (b > 0.86) | (a < 0.015) | (a > 0.985)
    rgb[chrome] = _env(T['nrm'], (220, 224, 230), (70, 72, 76))[chrome]
    # The badge in the middle.
    d = np.sqrt(((a - 0.5) * Lu / 0.07) ** 2 + ((b - 0.5) * Lv / 0.045) ** 2)
    rgb[d < 1] = _env(T['nrm'], (230, 232, 236), (90, 92, 96))[d < 1]
    rgb[(d < 0.7)] = (30, 50, 110)
    return occlusion(rgb, T['ao'], 0.6), 255


def m_grille_old(T, scheme, model):
    """The old saloon's chrome grille: a frame round tall vertical bars, a horizontal bar across."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    Lu, Lv = _dims(model, 'tl_grille_old')
    x = a * Lu
    rgb = np.tile(np.array((14, 14, 15.0)), (len(a), 1))
    bars = ((x / 0.022) % 1.0) < 0.45
    bars |= np.abs(b - 0.5) < 0.06
    bars |= (a < 0.03) | (a > 0.97) | (b < 0.08) | (b > 0.92)
    rgb[bars] = _env(T['nrm'], (225, 228, 232), (70, 72, 76))[bars]
    return occlusion(rgb, T['ao'], 0.6), 255


def m_mesh(T, scheme, model):
    """Black honeycomb intake."""
    pos = T['pos']
    u = pos[:, 0] / 0.016
    v = (pos[:, 1] + pos[:, 2] * 0.3) / 0.012
    cell = ((np.floor(v) % 2) * 0.5)
    hole = (np.abs(((u + cell) % 1.0) - 0.5) < 0.32) & (np.abs((v % 1.0) - 0.5) < 0.3)
    rgb = np.tile(np.array((40, 41, 43.0)), (len(u), 1))
    rgb[hole] = (8, 8, 9)
    return occlusion(rgb, T['ao'], 0.6), 255


def m_fog(T, scheme, model):
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    Lu, Lv = _dims(model, 'tl_fog')
    d = np.sqrt(((a - 0.5) * Lu) ** 2 + ((b - 0.5) * Lv) ** 2)
    r = min(Lu, Lv) * 0.42
    rgb = np.tile(np.array((22, 22, 24.0)), (len(a), 1))
    m = d < r
    rgb[m] = (200, 204, 210)
    rgb[m] *= (0.8 + 0.2 * np.cos(d[m] / r * 12))[:, None]
    rgb[d < r * 0.25] = (250, 248, 230)
    return occlusion(rgb, T['ao'], 0.5), 255


def m_amber(T, scheme, model):
    n = len(T['pos'])
    rgb = np.tile(np.array((226, 138, 30.0)), (n, 1))
    rgb *= (0.85 + 0.15 * np.cos(T['puv'][:, 1] * 9))[:, None]
    return rgb, 255


def m_dark_chrome(T, scheme, model):
    rgb = _env(T['nrm'], (120, 124, 130), (30, 32, 34))
    return occlusion(rgb, T['ao'], 0.7), 255


def m_route(T, scheme, model):
    """A route board: amber LED dots spelling the route on black."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    text = getattr(model, 'route_text', '12  ЦЕНТР')
    key = ('route', text)
    if key not in _cache:
        _cache[key] = text_mask(text, 160, 18, True, 0.1)
    m = sample(_cache[key], np.stack([a, 1 - b], axis=1))
    dots = (((a * 160) % 1.0) < 0.7) & (((b * 18) % 1.0) < 0.7)
    rgb = np.tile(np.array((14, 12, 10.0)), (len(a), 1))
    on = (m > 0.35) & dots
    rgb[on] = (255, 176, 40)
    rgb[~on & dots] = (34, 26, 14)
    return rgb, 255


_cache = {}


def _sign(text, w, h, fg, bg):
    key = (text, w, h, tuple(fg), tuple(bg))
    if key not in _cache:
        m = text_mask(text, w, h, True, 0.12)
        img = np.zeros((h, w, 3))
        img[:] = bg
        img = img * (1 - m[..., None]) + np.asarray(fg, dtype=np.float64) * m[..., None]
        _cache[key] = img
    return _cache[key]


def m_taxi_sign(T, scheme, model):
    """The roof sign: TAXI one side, ТАКСИ the other, black on yellow with a checker strip."""
    a, b = T['puv'][:, 0], T['puv'][:, 1]
    nz = T['nrm'][:, 2]
    rgb = np.tile(np.array((240, 200, 40.0)), (len(a), 1))
    for sgn, text in ((1, 'ТАКСИ'), (-1, 'TAXI')):
        m = (nz * sgn > 0.5) & (b < 0.72)
        if m.any():
            img = _sign(text, 120, 26, (20, 20, 20), (240, 200, 40))
            rgb[m] = sample(img, np.stack([a[m], 1 - b[m] / 0.72], axis=1))
        chk = (nz * sgn > 0.5) & (b >= 0.72)
        cell = ((np.floor(a * 16) + np.floor((b - 0.72) / 0.28 * 2)) % 2) == 0
        rgb[chk & cell] = (20, 20, 20)
        rgb[chk & ~cell] = (240, 240, 236)
    side = np.abs(nz) <= 0.5
    rgb[side] *= 0.9
    return occlusion(rgb, T['ao'], 0.4), 255


def m_lightbar(T, scheme, model):
    """A light bar's lens: red on the left half, blue on the right, LED rows behind clear plastic."""
    pos = T['pos']
    x = pos[:, 0]
    rgb = np.tile(np.array((30, 60, 210.0)), (len(x), 1))
    rgb[x < 0] = (220, 30, 26)
    cells = ((np.abs(x) / 0.05) % 1.0) < 0.75
    rgb[~cells] *= 0.55
    mid = np.abs(x) < 0.12
    rgb[mid] = (200, 202, 206)
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 35)[:, None]
    return rgb, 255


def m_rubber_mat(T, scheme, model):
    rgb = plain(T, 0x202122, scheme, 0.3, 0.9)
    rib = ((T['pos'][:, 2] / 0.03) % 1.0) < 0.3
    rgb[rib] *= 0.75
    return rgb, 255


def m_fabric_blue(T, scheme, model):
    rgb = plain(T, 0x2E3A5A, scheme, 0.05, 0.9)
    rgb *= (0.9 + 0.1 * np.sin(T['pos'][:, 1] * 120))[:, None]
    return rgb, 255


def m_fabric_red(T, scheme, model):
    rgb = plain(T, 0x6A2626, scheme, 0.05, 0.9)
    rgb *= (0.9 + 0.1 * np.sin(T['pos'][:, 1] * 120))[:, None]
    return rgb, 255


def m_bus_floor(T, scheme, model):
    rgb = plain(T, 0x4A4C4E, scheme, 0.3, 0.9)
    dots = (((T['pos'][:, 0] / 0.05) % 1.0) < 0.3) & (((T['pos'][:, 2] / 0.05) % 1.0) < 0.3)
    rgb[dots] *= 0.8
    return rgb, 255


def m_handrail(T, scheme, model):
    rgb = np.tile(np.array((214, 186, 40.0)), (len(T['pos']), 1))
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 25)[:, None]
    return occlusion(rgb, T['ao'], 0.5), 255


def m_shutter(T, scheme, model):
    """A roller shutter: thin horizontal aluminium slats, a handle bar at the bottom."""
    pos = T['pos']
    y = pos[:, 1]
    rgb = _env(T['nrm'], (196, 200, 206), (110, 112, 116))
    slat = ((y / 0.045) % 1.0) < 0.15
    rgb[slat] *= 0.65
    rgb = weather(rgb, T, scheme, 0.6, mud=False)
    return occlusion(rgb, T['ao'], 0.8), 255


def m_ladder(T, scheme, model):
    rgb = _env(T['nrm'], (205, 208, 212), (90, 92, 96))
    rgb = weather(rgb, T, scheme, 0.5, mud=False)
    return occlusion(rgb, T['ao'], 0.7), 255


def m_vent(T, scheme, model):
    pos = T['pos']
    s = (pos[:, 0] + pos[:, 2]) / 0.03
    rgb = np.tile(np.array((36, 37, 39.0)), (len(s), 1))
    rgb[(s % 1.0) < 0.4] = (16, 16, 17)
    return occlusion(rgb, T['ao'], 0.6), 255


def m_hazard(T, scheme, model):
    """Red and white diagonal stripes (the back of a fire engine)."""
    pos = T['pos']
    s = (pos[:, 0] + pos[:, 1]) / 0.14
    rgb = np.tile(np.array((236, 236, 232.0)), (len(s), 1))
    rgb[(s % 1.0) < 0.5] = (200, 24, 22)
    return occlusion(rgb, T['ao'], 0.5), 255


def m_steel_wheel(T, scheme, model):
    rgb = _env(T['nrm'], (150, 154, 158), (60, 62, 64))
    rgb = weather(rgb, T, scheme, 0.8, mud=False)
    return occlusion(rgb, T['ao'], 0.9), 255


def m_red_steel(T, scheme, model):
    rgb = plain(T, 0x9A1C18, scheme, 0.9)
    return rgb, 255


def m_badge(T, scheme, model):
    return _env(T['nrm'], (230, 232, 236), (80, 82, 86)), 255


def m_glass_tint(T, scheme, model):
    """Darker glass (the back windows of the SUV, the bus' top band)."""
    nrm = T['nrm']
    n = len(nrm)
    t = np.clip(0.2 + 0.6 * nrm[:, 1], 0, 1)
    rgb = np.array((18, 22, 26.0)) * (1 - t)[:, None] + np.array((110, 124, 136.0)) * t[:, None]
    s = np.sin((T['pos'][:, 0] * 0.7 + T['pos'][:, 2] * 1.3 + T['pos'][:, 1] * 2.0) * 2.4)
    rgb += (np.clip(s - 0.8, 0, 1) * 100)[:, None]
    return rgb, np.full(n, 215.0)


def m_lamp_white(T, scheme, model):
    n = len(T['pos'])
    rgb = np.tile(np.array((236, 238, 232.0)), (n, 1))
    return rgb, 255


def m_cream(T, scheme, model):
    rgb = plain(T, 0xE6DCC0, scheme, 0.6)
    rgb = sheen(rgb, T['nrm'], 0.6)
    return rgb, 255


def m_pole(T, scheme, model):
    rgb = plain(T, 0x2A2B2D, scheme, 0.6, 0.8)
    rgb += (np.clip(T['nrm'][:, 1], 0, 1) * 12)[:, None]
    return rgb, 255


REG = [('tl_frit', m_frit, None, 1.0), ('tl_liner', m_liner, None, 0.6), ('tl_under', m_under, None, 0.25),
       ('tl_headliner', m_headliner, None, 0.4), ('tl_door_card', m_door_card, None, 0.5), ('tl_head', m_head, None, 4.0),
       ('tl_head_sq', m_head_sq, None, 4.0), ('tl_tail', m_tail, None, 3.0), ('tl_tail_old', m_tail_old, None, 3.0),
       ('tl_grille', m_grille, None, 3.0), ('tl_grille_old', m_grille_old, None, 3.5), ('tl_mesh', m_mesh, None, 3.0),
       ('tl_fog', m_fog, None, 3.0), ('tl_amber', m_amber, None, 1.5), ('tl_dark_chrome', m_dark_chrome, None, 1.0),
       ('tl_route', m_route, None, 3.0), ('tl_taxi_sign', m_taxi_sign, None, 4.0), ('tl_lightbar', m_lightbar, None, 2.5),
       ('tl_rubber_mat', m_rubber_mat, None, 0.4), ('tl_fabric_blue', m_fabric_blue, None, 0.5),
       ('tl_fabric_red', m_fabric_red, None, 0.5), ('tl_bus_floor', m_bus_floor, None, 0.3),
       ('tl_handrail', m_handrail, None, 0.5), ('tl_shutter', m_shutter, None, 1.0), ('tl_ladder', m_ladder, None, 0.8),
       ('tl_vent', m_vent, None, 2.0), ('tl_hazard', m_hazard, None, 1.0), ('tl_steel_wheel', m_steel_wheel, None, 1.0),
       ('tl_red_steel', m_red_steel, None, 0.8), ('tl_badge', m_badge, None, 1.0), ('tl_glass_tint', m_glass_tint, 'glass', 0.5),
       ('tl_lamp_white', m_lamp_white, None, 1.0), ('tl_cream', m_cream, None, 1.0), ('tl_pole', m_pole, None, 0.6)]

for _n, _f, _l, _d in REG:
    materials.register(_n, _f, layer=_l, density=_d)
# Glass and the fine materials of the stock set: a bit less texture on glass (it is see-through, smooth).
import bake  # noqa: E402
bake.DENSITY.setdefault('glass', 0.5)


# ---------------------------------------------------------------------------------------------------------------
# Wheels


def tyre(c, side, r, w, rim_r, n=20):
    """A tyre round the axle (x) at c: tread with rounded shoulders, bulging sidewalls."""
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    hw = w / 2
    sh = min(w * 0.2, (r - rim_r) * 0.4)
    prof = [(-hw * 0.86, rim_r, 'tyre_wall'), (-hw * 1.02, rim_r + (r - rim_r) * 0.45, 'tyre_wall'),
            (-hw * 0.94, r - sh, 'tyre'), (-hw + sh * 0.45, r - sh * 0.12, 'tyre'), (hw - sh * 0.45, r - sh * 0.12, 'tyre'),
            (hw * 0.94, r - sh, 'tyre_wall'), (hw * 1.02, rim_r + (r - rim_r) * 0.45, 'tyre_wall'), (hw * 0.86, rim_r, 'tyre_wall')]
    # The lathe runs along +axis from c - 0: profile t from -hw..hw, so start the axis at c.
    g = geom.lathe(c, c + ax, prof, 'tyre', n, 50)
    return g


def car_wheel(c, side, r, w, style='alloy', n=20, rim_ratio=0.64, spokes=5, mat_rim='rim', hub='chrome'):
    """A whole wheel at c (axle centre), its outer face to {side}."""
    c = np.asarray(c, dtype=np.float64)
    ax = np.array([float(side), 0, 0])
    _, u, wv = geom._basis(ax)
    g = Geo(45)
    rr = r * rim_ratio
    g.extend(tyre(c, side, r, w, rr, n))
    face = c + ax * (w / 2 - 0.02)
    rb = rr * 0.9
    # The rim's lip and the barrel inside it.
    g.extend(geom.lathe(face - ax * 0.12, face + ax * 0.012,
                        [(0.0, rb * 0.98, 'rim_dark'), (0.11, rb, mat_rim), (0.132, rr, mat_rim), (0.128, rr * 1.02, 'tyre_wall')],
                        mat_rim, n, 40))
    back = face - ax * 0.07
    if style == 'alloy':
        # A dark dish behind the spokes (the brake disc and caliper show as a darker ring).
        g.extend(geom.lathe(back, back + ax * 0.002, [(0.0, rb, 'rim_dark'), (0.0, 0.0, 'rim_dark')], 'rim_dark', n))
        hub_r = rr * 0.26
        for k in range(spokes):
            th = 2 * math.pi * k / spokes + 0.2
            for dth in (-0.07, 0.07) if spokes <= 5 else (0.0,):
                d = u * math.cos(th + dth) + wv * math.sin(th + dth)
                e = np.cross(ax, d)
                p0 = face + d * hub_r * 0.9 + ax * 0.0
                p1 = face + d * rb * 0.97 - ax * 0.035
                hw0, hw1 = rr * 0.06, rr * 0.075
                ring0 = [p0 - e * hw0, p0 + e * hw0, p0 + e * hw0 * 0.7 - ax * 0.035, p0 - e * hw0 * 0.7 - ax * 0.035]
                ring1 = [p1 - e * hw1, p1 + e * hw1, p1 + e * hw1 * 0.7 - ax * 0.03, p1 - e * hw1 * 0.7 - ax * 0.03]
                sp = geom.loft([ring0, ring1], mat_rim, closed=True, smooth=35)
                orient(sp, (p0 + p1) / 2 - ax * 0.02)
                g.extend(sp)
        g.extend(geom.lathe(face - ax * 0.05, face + ax * 0.012, [(0, hub_r, mat_rim), (0.05, hub_r, mat_rim), (0.062, hub_r * 0.55, hub),
                                                                 (0.062, 0.0, hub)], mat_rim, 10, 50))
    elif style == 'steel':
        # A pressed steel disc: dished, ventilation holes (dark), a chrome hub cap.
        g.extend(geom.lathe(back, face + ax * 0.004, [(0.0, rb, 'tl_steel_wheel'), (0.03, rr * 0.7, 'tl_steel_wheel'),
                                                      (0.05, rr * 0.5, 'tl_steel_wheel'), (0.07, rr * 0.36, hub),
                                                      (0.075, 0.0, hub)], 'tl_steel_wheel', n, 40))
        for k in range(6):
            th = 2 * math.pi * k / 6
            p = back + ax * 0.04 + (u * math.cos(th) + wv * math.sin(th)) * rr * 0.6
            g.extend(geom.panel(p + ax * 0.0, rr * 0.16, rr * 0.16, np.cross(ax, u * math.cos(th) + wv * math.sin(th)),
                                u * math.cos(th) + wv * math.sin(th), 'rim_dark'))
            g.polys[-1].v = g.polys[-1].v + ax * 0.002
            fixp = g.polys[-1]
            if np.dot(face_normal(fixp.v), ax) < 0:
                fixp.v = fixp.v[::-1].copy()
    elif style == 'truck':
        # A heavy steel disc with a ring of nuts and a hub.
        g.extend(geom.lathe(back, face + ax * 0.01, [(0.0, rb, mat_rim), (0.02, rr * 0.75, mat_rim), (0.025, rr * 0.4, mat_rim),
                                                     (0.06, rr * 0.3, 'rim_dark'), (0.065, 0.0, 'rim_dark')], mat_rim, n, 40))
        for k in range(8):
            th = 2 * math.pi * k / 8
            p = back + ax * 0.025 + (u * math.cos(th) + wv * math.sin(th)) * rr * 0.55
            g.extend(geom.cyl(p, p + ax * 0.03, rr * 0.05, 'bolt', n=6, caps=True))
        for k in range(8):
            th = 2 * math.pi * (k + 0.5) / 8
            p = back + ax * 0.02 + (u * math.cos(th) + wv * math.sin(th)) * rr * 0.75
            q = geom.panel(p + ax * 0.002, rr * 0.18, rr * 0.12, np.cross(ax, u * math.cos(th) + wv * math.sin(th)),
                           u * math.cos(th) + wv * math.sin(th), 'rim_dark')
            orient(q, p - ax)
            g.extend(q)
    # The inner face: dark, so nothing shows through.
    inner = c - ax * (w * 0.3)
    g.extend(geom.lathe(inner, inner - ax * 0.001, [(0, 0.0, 'rim_dark'), (0, rr, 'rim_dark')], 'rim_dark', 10))
    return g


# ---------------------------------------------------------------------------------------------------------------
# Inside pieces


def seat(x, y, z, w=0.5, mat='seat', back_h=0.62, tilt=-14, headrest=True, facing=1, cushion=0.48):
    """A car seat: the cushion (top at y), the back leaning back by {tilt}, a head rest. facing: +1 looks to +z."""
    g = Geo(40)
    f = facing
    zc = z + f * cushion * 0.5 - f * 0.05
    g.extend(hbox((x - w / 2, y - 0.12, min(zc - cushion / 2, zc + cushion / 2)), (x + w / 2, y, max(zc - cushion / 2, zc + cushion / 2)),
                  mat, r=0.05))
    # Side bolsters.
    back = hbox((x - w / 2, y - 0.05, -0.06), (x + w / 2, y + back_h, 0.06), mat, r=0.05)
    back.transform(t=(0, 0, z - f * 0.06))
    about(back, (x, y, z - f * 0.06), rot_x(-tilt * f))
    g.extend(back)
    if headrest:
        hr = hbox((x - w * 0.27, y + back_h + 0.05, -0.05), (x + w * 0.27, y + back_h + 0.24, 0.05), mat, r=0.04)
        hr.transform(t=(0, 0, z - f * 0.06))
        about(hr, (x, y, z - f * 0.06), rot_x(-tilt * f))
        g.extend(hr)
        for s in (-1, 1):
            p = np.array([x + s * w * 0.15, y + back_h, z - f * 0.06])
            q = np.array([x + s * w * 0.15, y + back_h + 0.07, z - f * 0.06])
            t = geom.tube([p, q], 0.008, 'chrome', n=4, caps=False)
            about(t, (x, y, z - f * 0.06), rot_x(-tilt * f))
            g.extend(t)
    return g


def steering_wheel_geo(c, r=0.19, n=14, mat='trim', column=0.3):
    """A steering wheel in its own plane (x, y) at c, the column running forward (+z) - for a part rotated about c."""
    c = np.asarray(c, dtype=np.float64)
    g = Geo(50)
    ring = [c + np.array([math.cos(2 * math.pi * k / n) * r, math.sin(2 * math.pi * k / n) * r, 0]) for k in range(n)]
    g.extend(geom.tube(ring, 0.016, mat, n=5, closed=True))
    for a in (0.0, math.pi, -math.pi / 2):
        e = c + np.array([math.cos(a) * (r - 0.01), math.sin(a) * (r - 0.01), 0.0])
        g.extend(bar(c + (e - c) * 0.25, e, 0.04 if a == -math.pi / 2 else 0.03, 0.012, mat, up=(0, 0, 1)))
    g.extend(geom.cyl(c + [0, 0, 0.03], c - [0, 0, 0.025], 0.055, mat, n=10, bevel=0.01))
    if column > 0:
        g.extend(geom.cyl(c + [0, 0, 0.03], c + [0, 0, column], 0.03, 'dash', n=6, caps=False))
    return g


def mirror(base, side, mat='paint', size=(0.2, 0.12, 0.07), arm=0.08, glass='mirror_glass'):
    """A door mirror: an arm out from the door at {base}, the housing (paint), its glass facing back."""
    base = np.asarray(base, dtype=np.float64)
    w, h, d = size
    g = Geo(40)
    c = base + np.array([side * (arm + w / 2), 0.02, -0.01])
    g.extend(bar(base + [0, 0, 0.0], base + [side * (arm + 0.02), 0.01, 0], 0.05, 0.035, 'trim'))
    housing = hbox(c - [w / 2, h / 2, d / 2], c + [w / 2, h / 2, d / 2], mat, r=0.03, segs=1)
    # Rounded front: push the front face corners back.
    for p in housing.polys:
        p.v[:, 2] = np.where(p.v[:, 2] > c[2], c[2] + (p.v[:, 2] - c[2]) * (1 - 0.5 * (np.abs(p.v[:, 0] - c[0]) / (w / 2)) ** 2), p.v[:, 2])
    g.extend(housing)
    gl = geom.panel(c - [0, 0, d / 2 + 0.002], w * 0.86, h * 0.78, (side, 0, 0), (0, 1, 0), glass)
    orient(gl, c)
    g.extend(gl)
    # Side indicator repeater on the housing.
    ind = geom.panel(c + [side * w * 0.2, -h * 0.3, d * 0.2], w * 0.4, 0.015, (side, 0, 0), (0, 0, 1), 'tl_amber')
    for p in ind.polys:
        p.v[:, 1] = c[1] - h / 2 - 0.001
    orient(ind, c)
    g.extend(ind)
    return g


def light_bar(c, length, mat='tl_lightbar', depth=0.28, h=0.1, feet=True):
    """A roof light bar across the car: a rounded lens on a dark base, its feet."""
    c = np.asarray(c, dtype=np.float64)
    g = Geo(40)
    g.extend(hbox(c + [-length / 2, 0, -depth / 2], c + [length / 2, 0.03, depth / 2], 'trim', r=0.012))
    lens = hbox(c + [-length / 2 + 0.01, 0.03, -depth / 2 + 0.02], c + [length / 2 - 0.01, 0.03 + h, depth / 2 - 0.02], mat, r=0.04, segs=1)
    g.extend(lens)
    if feet:
        for s in (-1, 1):
            g.extend(hbox(c + [s * length * 0.38 - 0.04, -0.04, -depth / 2 + 0.03], c + [s * length * 0.38 + 0.04, 0.0, depth / 2 - 0.03], 'trim'))
    return g


def wiper(p0, p1, mat='wiper'):
    p0 = np.asarray(p0, dtype=np.float64)
    p1 = np.asarray(p1, dtype=np.float64)
    g = Geo(40)
    g.extend(bar(p0, p1, 0.012, 0.012, mat))
    g.extend(bar(p0 + (p1 - p0) * 0.05 + [0, 0.012, 0], p0 + (p1 - p0) * 0.98 + [0, 0.012, 0], 0.02, 0.008, mat))
    return g
