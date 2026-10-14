"""
2.0: the mesh file's version 2 - the same content as version 1 (see bake.py) but laid out to pack small in the jar:
per layer the vertex fields one after another (all x, all y, ... - "planar"), positions and uvs as differences from the
vertex before, normals 3 bytes, the corner shade one grey byte, flags one byte, the quad corners as differences from the
corner before (short, or int when the layer has more than 32767 vertices).
python3 tools/mesh/meshv2.py <file.mesh ...>   converts version 1 files in place.
"""
import struct
import sys

import numpy as np


def read_v1(data):
    o = 0

    def take(fmt):
        nonlocal o
        v = struct.unpack_from(fmt, data, o)
        o += struct.calcsize(fmt)
        return v
    assert data[:4] == b'ADM2'
    o = 4
    ver, W, H, mflags, nparts = take('>iiibh')
    if ver != 1:
        raise ValueError('version %d' % ver)
    parts = []
    for _ in range(nparts):
        (ln,) = take('>H')
        name = data[o:o + ln]
        o += ln
        parent, = take('>h')
        pr = take('>6f')
        spin, steer, track = take('>fbb')
        (nl,) = take('>b')
        layers = []
        for _ in range(nl):
            lt, near, scale, res, nv, nq = take('>bfffii')
            dt = np.dtype([('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('nx', 'i1'), ('ny', 'i1'), ('nz', 'i1'), ('u', '>u2'), ('v', '>u2'),
                           ('c', '>u4'), ('f', 'u1')])
            vs = np.frombuffer(data, dt, nv, o)
            o += nv * dt.itemsize
            big = nv > 65535
            q = np.frombuffer(data, '>i4' if big else '>u2', nq * 4, o).astype(np.int64)
            o += nq * 4 * (4 if big else 2)
            layers.append((lt, near, scale, res, vs, q))
        parts.append((name, parent, pr, spin, steer, track, layers))
    return W, H, mflags, parts


def write_v2(W, H, mflags, parts):
    out = bytearray(b'ADM2')
    out += struct.pack('>iiibh', 2, W, H, mflags, len(parts))
    for name, parent, pr, spin, steer, track, layers in parts:
        out += struct.pack('>H', len(name)) + name
        out += struct.pack('>h', parent)
        out += struct.pack('>6f', *pr)
        out += struct.pack('>fbb', spin, steer, track)
        out += struct.pack('>b', len(layers))
        for lt, near, scale, res, vs, q in layers:
            nv = len(vs)
            out += struct.pack('>bfffii', lt, near, scale, res, nv, len(q) // 4)
            for k in ('x', 'y', 'z'):
                a = vs[k].astype(np.int32)
                d = np.diff(a, prepend=0).astype(np.int16)
                out += d.astype('>i2').tobytes()
            for k in ('nx', 'ny', 'nz'):
                out += vs[k].astype('i1').tobytes()
            for k in ('u', 'v'):
                a = vs[k].astype(np.int32)
                d = (np.diff(a, prepend=0) & 0xFFFF).astype(np.uint16)
                out += d.astype('>u2').tobytes()
            grey = (vs['c'] & 0xFF).astype(np.uint8)
            out += grey.tobytes()
            out += vs['f'].astype('u1').tobytes()
            d = np.diff(q, prepend=0)
            if nv > 32767:
                out += d.astype('>i4').tobytes()
            else:
                out += d.astype('>i2').tobytes()
    return bytes(out)


if __name__ == '__main__':
    import zlib
    a = b = 0
    for p in sys.argv[1:]:
        data = open(p, 'rb').read()
        if struct.unpack_from('>i', data, 4)[0] != 1:
            continue
        new = write_v2(*read_v1(data))
        a += len(zlib.compress(data, 9))
        b += len(zlib.compress(new, 9))
        open(p, 'wb').write(new)
    print('deflated: v1 %d KB -> v2 %d KB' % (a // 1024, b // 1024))
