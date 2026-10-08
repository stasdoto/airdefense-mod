"""
Stand-ins for the hand-made models (client/vehicle/VehicleModels.java), 1.26: only their cabs, so the generator can
build their insides. Their windows are painted on the outside, so the openings are given here; the cab boxes and the
seats are the hand-made models' own (keep them in step with VehicleModels.java and VehicleGeometry.java).
"""
from boxgen import Model


def stub(id, cab, seats, windscreen=(0.5, 0.12), side=(0.45, 0.6), kind='truck', **opts):
    """cab = (x0, x1, y0, y1, z0, z1); windscreen = (lower edge as a share of the height, top margin);
    side = (lower edge share, how far back from the front the side windows reach as a share of the length)."""
    m = Model(id)
    m.stub = True
    for s in seats:
        m.seat(*s)
    x0, x1, y0, y1, z0, z1 = cab
    h = y1 - y0
    wy0 = y0 + h * windscreen[0]
    wy1 = y1 - windscreen[1]
    sy0 = y0 + h * side[0]
    sz0 = z1 - (z1 - z0) * side[1]
    windows = [('front', x0 + 0.15, -0.05, wy0, wy1), ('front', 0.05, x1 - 0.15, wy0, wy1),
               ('left', sz0, z1 - 0.2, sy0, wy1 - 0.05), ('right', sz0, z1 - 0.2, sy0, wy1 - 0.05)]
    m.cab(x0, x1, y0, y1, z0, z1, kind=kind, windows=windows, **opts)
    return m


def all_models():
    return [
        stub('iskander', (-1.5, 1.5, 1.19, 3.19, 4.56, 6.5), [('driver', -0.6, 1.7, 5.5), ('gunner', 0.6, 1.7, 5.5)]),
        stub('kalibr', (-1.5, 1.5, 1.19, 3.19, 4.56, 6.5), [('driver', -0.6, 1.7, 5.5), ('gunner', 0.6, 1.7, 5.5)]),
        stub('shahed', (-1.25, 1.25, 1.06, 3.06, 2.56, 4.06), [('driver', -0.5, 1.55, 3.35), ('gunner', 0.5, 1.55, 3.35)]),
        stub('himars', (-1.19, 1.19, 0.94, 2.94, 1.88, 3.5), [('driver', -0.5, 1.45, 2.7), ('gunner', 0.5, 1.45, 2.7)]),
        stub('patriot', (-1.25, 1.25, 1.12, 3.19, 3.44, 4.94), [('driver', -0.55, 1.75, 4.25), ('gunner', 0.55, 1.75, 4.25)]),
        stub('iris_t', (-1.25, 1.25, 1.12, 3.19, 3.38, 4.88), [('driver', -0.55, 1.75, 4.15), ('gunner', 0.55, 1.75, 4.15)]),
        stub('nasams', (-1.25, 1.25, 1.12, 3.12, 2.56, 4.06), [('driver', -0.55, 1.7, 3.4), ('gunner', 0.55, 1.7, 3.4)]),
    ]
