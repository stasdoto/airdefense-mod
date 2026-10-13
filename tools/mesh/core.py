"""
2.0: real meshes for the vehicles (instead of boxes with noisy pixel textures).

A model is a tree of parts like the box models (tools/models/boxgen.py): metres in vehicle space, x to the right, y up,
z forward (the nose), the ground at y = 0, a part's fixed rotation (degrees, Minecraft's sense) about its pivot. Each
part holds polygons (3 or 4 corners, wound counter-clockwise seen from outside) with a material; a polygon may carry its
own corner normals (for rounded shapes) and a "primitive uv" (a coordinate over the shape - round a wheel, along a body)
the materials use when they are painted.

Geometry is built from primitives (geom.py) into a Geo (a smoothing group of polygons) and added to a part. The model is
then baked (bake.py): normals smoothed, every surface given its own patch of one texture per model, the texture painted
texel by texel in 3D (camouflage, dirt, ambient occlusion, decals), and written as a binary mesh (export.py) the client
draws (client.vehicle.MeshCube).
"""
import math

import numpy as np


def v3(x, y=None, z=None):
    if y is None:
        return np.asarray(x, dtype=np.float64)
    return np.array((x, y, z), dtype=np.float64)


def normalize(a):
    a = np.asarray(a, dtype=np.float64)
    n = np.linalg.norm(a, axis=-1, keepdims=True)
    n[n < 1e-12] = 1
    return a / n


def rot_x(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def face_normal(v):
    """Newell's normal of a polygon (k, 3)."""
    n = np.zeros(3)
    k = len(v)
    for i in range(k):
        a = v[i]
        b = v[(i + 1) % k]
        n[0] += (a[1] - b[1]) * (a[2] + b[2])
        n[1] += (a[2] - b[2]) * (a[0] + b[0])
        n[2] += (a[0] - b[0]) * (a[1] + b[1])
    ln = np.linalg.norm(n)
    return n / ln if ln > 1e-12 else n


class Poly:
    __slots__ = ('v', 'mat', 'uv', 'n')

    def __init__(self, v, mat, uv=None, n=None):
        self.v = np.asarray(v, dtype=np.float64)
        self.mat = mat
        self.uv = None if uv is None else np.asarray(uv, dtype=np.float64)
        self.n = None if n is None else np.asarray(n, dtype=np.float64)


class Geo:
    """A smoothing group: polygons whose normals are averaged where they meet at less than {smooth} degrees (unless the
    polygons bring their own normals)."""

    def __init__(self, smooth=35.0):
        self.polys = []
        self.smooth = smooth

    def add(self, v, mat, uv=None, n=None):
        if len(v) >= 3:
            self.polys.append(Poly(v, mat, uv, n))
        return self

    def extend(self, other):
        self.polys += other.polys
        return self

    def transform(self, R=None, t=None, scale=None):
        """Turned by R (3x3), scaled, then moved by t. Normals follow (a mirror flips the winding back outward)."""
        R = np.eye(3) if R is None else np.asarray(R, dtype=np.float64)
        if scale is not None:
            R = R @ np.diag(np.broadcast_to(np.asarray(scale, dtype=np.float64), (3,)))
        t = np.zeros(3) if t is None else np.asarray(t, dtype=np.float64)
        mirror = np.linalg.det(R) < 0
        Ninv = np.linalg.inv(R).T
        for p in self.polys:
            p.v = p.v @ R.T + t
            if p.n is not None:
                p.n = normalize(p.n @ Ninv.T)
            if mirror:
                p.v = p.v[::-1].copy()
                if p.n is not None:
                    p.n = p.n[::-1].copy()
                if p.uv is not None:
                    p.uv = p.uv[::-1].copy()
        return self

    def moved(self, t):
        return self.transform(t=t)

    def copy(self):
        g = Geo(self.smooth)
        for p in self.polys:
            g.polys.append(Poly(p.v.copy(), p.mat, None if p.uv is None else p.uv.copy(), None if p.n is None else p.n.copy()))
        return g

    def mirrored_x(self):
        """A copy mirrored left to right (x -> -x)."""
        return self.copy().transform(np.diag([-1.0, 1.0, 1.0]))

    def recolor(self, old, new):
        for p in self.polys:
            if p.mat == old:
                p.mat = new
        return self

    def set_mat(self, mat):
        for p in self.polys:
            p.mat = mat
        return self


class Part:
    def __init__(self, model, name, pivot, rot, parent):
        self.model = model
        self.name = name
        self.pivot = tuple(float(x) for x in pivot)
        self.rot = tuple(float(x) for x in rot)
        self.parent = parent
        self.children = []
        self.geos = []
        # Animation the client applies by itself: a wheel rolling (its radius), steering, a track's belt (-1 left, 1 right).
        self.spin = 0.0
        self.steer = False
        self.track = 0
        # Drawn only within this distance (blocks), e.g. the inside of a cab.
        self.near = 0.0

    def part(self, name, pivot=None, rot=(0, 0, 0)):
        return self.model.part(name, self.pivot if pivot is None else pivot, rot, self)

    def add(self, geo):
        if isinstance(geo, (list, tuple)):
            for g in geo:
                self.add(g)
            return self
        if geo.polys:
            self.geos.append(geo)
        return self

    def path(self):
        p = self
        out = []
        while p is not None:
            out.append(p.name)
            p = p.parent
        return '/'.join(reversed(out))

    def rest_matrix(self):
        """Rest pose: model space of this part's own geometry -> model space (the fixed rotations about the pivots)."""
        R = np.eye(3)
        t = np.zeros(3)
        chain = []
        p = self
        while p is not None:
            chain.append(p)
            p = p.parent
        for q in reversed(chain):
            if any(q.rot):
                Rq = mc_rotation(q.rot)
                piv = np.asarray(q.pivot)
                # x' = Rq (x - piv) + piv, composed after the parents' transform.
                t = R @ (piv - Rq @ piv) + t
                R = R @ Rq
        return R, t


def mc_rotation(rot):
    """A part's rotation (degrees, Minecraft's sense as the box models use it) as a matrix in vehicle space (y up).
    Minecraft turns z, then y, then x in its own y-down space; in y-up vehicle space that is S Rz Ry Rx S, S = diag(1,-1,1)."""
    rx, ry, rz = (math.radians(a) for a in rot)
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    S = np.diag([1.0, -1.0, 1.0])
    return S @ (Rz @ Ry @ Rx) @ S


class Model:
    def __init__(self, id, scheme=None, density=32, tex_width=512):
        self.id = id
        self.parts = []
        self.roots = []
        # How the paint looks: see materials.Scheme.
        self.scheme = scheme
        # Texels per metre of surface.
        self.density = density
        self.tex_width = tex_width
        # The town cars: the paint drawn white and tinted per car by the client.
        self.tinted = False
        # Decals painted onto the surfaces (materials.Decal).
        self.decals = []
        # Ground under the model for the shading (False for aircraft drawn in the air, boats).
        self.ground = True

    def part(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
        p = Part(self, name, pivot, rot, parent)
        self.parts.append(p)
        if parent is None:
            self.roots.append(p)
        else:
            parent.children.append(p)
        return p

    def get(self, name):
        for p in self.parts:
            if p.name == name:
                return p
        raise KeyError(name)

    def bounds(self):
        lo = np.full(3, 1e9)
        hi = np.full(3, -1e9)
        for p in self.parts:
            R, t = p.rest_matrix()
            for g in p.geos:
                for q in g.polys:
                    w = q.v @ R.T + t
                    lo = np.minimum(lo, w.min(axis=0))
                    hi = np.maximum(hi, w.max(axis=0))
        return lo, hi

    def poly_count(self):
        return sum(len(g.polys) for p in self.parts for g in p.geos)
