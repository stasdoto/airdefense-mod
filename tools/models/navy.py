"""
1.33 "Navy": the warships and the coastal anti-ship missile systems. The Buyan-M small missile ship (Russia) and the
Visby corvette (Sweden): a gun on the bow, vertical launch cells for cruise missiles amidships, a close-in gun against
drones and missiles; the Bastion-P coastal launcher (two Oniks in a container that stands up to fire) and NMESIS (two
NSM on an unmanned JLTV). Metres, x right, y up (0 = the waterline for ships), z forward.

Ships are long: their hulls are cut into equal segments so the same texture serves them all (one 8 m box would not
fit the texture atlas).
"""
from boxgen import Model
from kit import axles, frame, lights, cabover
from airdefense import barrel, canister, missile, dish
from armor import gun


def ship_hull(body, length, beam, deck, deck_bow, draft, paint, bow_frac=0.3, seg=6.0, chine=None, bottom='hull_red'):
    """Hull from z = -length/2 (the transom) to +length/2 (the stem). The mid-body in equal segments, the bow narrowing
    in steps while the deck rises to {deck_bow}; under the waterline (y < 0) the bottom paint, the keel raked up to the
    stem. {chine}: a stealthy hull - the sides lean in above this height. Returns (z0, z1, bow start)."""
    z0, z1 = -length / 2, length / 2
    hb = beam / 2
    zb = z1 - length * bow_frac
    n = max(1, int(round((zb - z0) / seg)))
    step = (zb - z0) / n
    top = {'top': 'deck'}
    for i in range(n):
        za, zz = z0 + i * step, z0 + (i + 1) * step
        body.box((-hb * 0.9, -draft, za), (hb * 0.9, 0.0, zz), bottom)
        if chine is None:
            body.box((-hb, 0.0, za), (hb, deck, zz), paint, sides=top)
        else:
            body.box((-hb, 0.0, za), (hb, chine, zz), paint)
            body.box((-hb * 0.93, chine, za), (hb * 0.93, deck, zz), paint, sides=top)
    k = 9
    for i in range(k):
        t0, t1 = i / k, (i + 1) / k
        tm = (t0 + t1) / 2
        za = zb + (z1 - zb) * t0
        zz = zb + (z1 - zb) * t1
        w = hb * max(0.12, 1 - tm ** 1.7)
        d = deck + (deck_bow - deck) * tm
        keel = draft * max(0.15, 1 - tm * 1.15)
        body.box((-w * 0.85, -keel, za), (w * 0.85, 0.0, zz), bottom)
        if chine is None:
            body.box((-w, 0.0, za), (w, d, zz), paint, sides=top)
        else:
            body.box((-w, 0.0, za), (w, chine + (d - deck) * 0.5, zz), paint)
            body.box((-w * 0.93, chine + (d - deck) * 0.5, za), (w * 0.93, d, zz), paint, sides=top)
    # The waterline stripe (boot topping) along the mid-body.
    body.box((-hb - 0.02, -0.05, z0), (-hb, 0.25, zb), 'black')
    body.box((hb, -0.05, z0), (hb + 0.02, 0.25, zb), 'black')
    return z0, z1, zb


def rails_and_lifelines(body, z0, z1, y, hb):
    for s in (-1, 1):
        body.box((s * hb - (0.06 if s > 0 else 0.0), y, z0), (s * hb + (0.0 if s > 0 else 0.06), y + 0.9, z0 + 0.06), 'lgrey')
        body.box((s * hb - (0.04 if s > 0 else 0.0), y + 0.85, z0), (s * hb + (0.0 if s > 0 else 0.04), y + 0.9, z1), 'lgrey')


def vls(m, body, x, y, z, cols, rows, pitch, paint):
    """A vertical launch block: the hatches on top. The launch points are in VehicleType's ShipFit (the geometry's one
    elevator belongs to the gun on its turret)."""
    w = cols * pitch
    d = rows * pitch
    body.box((x - w / 2 - 0.25, y - 1.0, z - d / 2 - 0.25), (x + w / 2 + 0.25, y, z + d / 2 + 0.25), paint)
    for c in range(cols):
        for r in range(rows):
            cx = x - w / 2 + pitch * (c + 0.5)
            cz = z - d / 2 + pitch * (r + 0.5)
            body.box((cx - pitch * 0.42, y, cz - pitch * 0.42), (cx + pitch * 0.42, y + 0.06, cz + pitch * 0.42), 'dgrey')
    print('%s: vls centre (%.3f, %.3f, %.3f) %dx%d pitch %.3f' % (m.id, x, y, z, cols, rows, pitch))


def buyan_m():
    """Project 21631 Buyan-M small missile ship: a low hull, the big superstructure with the pyramid mast, the 100 mm
    A-190 on the bow, eight Kalibr cells (3S14) behind the bridge, the AK-630M-2 Duet aft, water-jets at the transom."""
    m = Model('buyan_m', paint='navy', seed=3301, tex_width=2048)
    m.width = 11.0
    m.camera = 46
    body = m.part('body')
    deck = 3.2
    z0, z1, zb = ship_hull(body, 74.0, 11.0, deck, 4.6, 2.6, 'navy')
    rails_and_lifelines(body, z0 + 1, zb, deck, 5.4)
    # The forecastle step and the gun's raised base.
    body.box((-3.6, deck, 14.0), (3.6, deck + 0.9, 20.0), 'navy', sides={'top': 'deck'})
    # Superstructure: three levels, the bridge with its windows at the front top.
    body.box((-4.4, deck, -10.0), (4.4, deck + 2.8, 13.0), 'navy')
    body.box((-4.0, deck + 2.8, -6.0), (4.0, deck + 5.4, 12.0), 'navy')
    body.box((-3.4, deck + 5.4, 3.0), (3.4, deck + 7.6, 11.4), 'navy')
    body.box((-3.0, deck + 4.6, 12.0), (3.0, deck + 5.3, 12.03), 'glass')
    body.box((-3.0, deck + 6.6, 11.4), (3.0, deck + 7.3, 11.43), 'glass')
    for s in (-1, 1):
        body.box((s * 3.4 - (0.0 if s > 0 else 0.03), deck + 6.6, 5.0), (s * 3.4 + (0.03 if s > 0 else 0.0), deck + 7.3, 11.0), 'glass')
        # Bridge wings.
        body.box((s * 3.4 - (0.0 if s > 0 else 1.2), deck + 5.4, 9.4), (s * 3.4 + (1.2 if s > 0 else 0.0), deck + 5.6, 11.4), 'navy')
    m.cab(-3.4, 3.4, deck + 5.4, deck + 7.6, 5.0, 11.4, kind='boat', hatch=False)
    # The pyramid mast with the radars, a lattice yard, the antennas.
    for i, (hw, y0, y1, za, zz) in enumerate(((2.6, 7.6, 10.0, 2.0, 8.0), (1.9, 10.0, 12.4, 2.8, 7.2), (1.2, 12.4, 14.6, 3.6, 6.4),
                                              (0.6, 14.6, 17.6, 4.3, 5.7))):
        body.box((-hw, deck + y0, za), (hw, deck + y1, zz), 'navy')
    body.box((-1.4, deck + 12.6, 6.4), (1.4, deck + 14.2, 6.5), 'aesa', sides={'front': 'aesa'})
    body.box((-1.2, deck + 15.0, 5.7), (1.2, deck + 16.4, 5.8), 'aesa')
    body.box((-2.6, deck + 15.6, 4.9), (2.6, deck + 15.75, 5.1), 'dgrey')
    body.box((-0.08, deck + 17.6, 4.92), (0.08, deck + 21.5, 5.08), 'dgrey')
    dish(body, 0.0, deck + 8.6, 9.0, 1.4, 1.4, face='radome', depth=0.6)
    # Aft deckhouse with the Duet and the Gibka launcher.
    body.box((-3.4, deck, -22.0), (3.4, deck + 2.4, -10.0), 'navy')
    body.box((-1.5, deck + 2.4, -20.0), (1.5, deck + 3.0, -17.0), 'navy')
    body.box((-1.0, deck + 3.0, -19.5), (1.0, deck + 4.4, -17.5), 'navy')
    for s in (-1, 1):
        barrel(body, s * 0.28, deck + 3.85, -17.5, 2.2, 0.12, 'dark')
    body.box((-0.6, deck + 2.4, -14.5), (0.6, deck + 3.6, -13.3), 'dgrey')
    for s in (-1, 1):
        canister(body, s * 0.4, deck + 3.9, -14.7, 1.7, 0.14, 'olive')
    # The Kalibr block between the superstructure and the deckhouse (eight cells, two rows of four).
    vls(m, body, 0.0, deck + 0.9, -8.0, 4, 2, 0.75, 'navy')
    # Boats, liferaft canisters, the water-jet outlets at the transom, the anchor, the hull number.
    for s in (-1, 1):
        body.box((s * 4.6 - 0.5, deck + 2.8, -4.0), (s * 4.6 + 0.5, deck + 3.6, 1.0), 'orange')
        for k in range(3):
            canister(body, s * 4.9, deck + 0.6, -24.0 + k * 1.4, 1.0, 0.5, 'white')
        body.box((s * 2.2 - 0.7, -0.9, z0 - 0.05), (s * 2.2 + 0.7, 0.3, z0), 'dark')
        body.box((s * 2.0 - 0.6, 1.4, z1 - 8.6), (s * 2.0 + 0.6, 2.0, z1 - 8.0), 'dark')
    body.box((-5.52, 1.6, 22.0), (-5.5, 2.8, 25.0), 'white')
    body.box((5.5, 1.6, 22.0), (5.52, 2.8, 25.0), 'white')
    # The A-190 100 mm gun: a faceted stealth turret, its long barrel.
    tz = 17.0
    tur = m.part('turret', (0, deck + 0.9, tz))
    m.set_turret(tur)
    tur.box((-1.5, deck + 0.9, tz - 1.6), (1.5, deck + 2.3, tz + 1.4), 'navy')
    tur.box((-1.2, deck + 2.3, tz - 1.3), (1.2, deck + 2.9, tz + 0.9), 'navy')
    tur.box((-0.7, deck + 2.9, tz - 1.0), (0.7, deck + 3.2, tz + 0.2), 'navy')
    gun(m, tur, (0, deck + 1.7, tz + 1.4), 5.6, 0.1, paint='navy', mantlet=0.45)
    m.seat('driver', -1.2, deck + 5.6, 9.8)
    m.seat('gunner', 1.2, deck + 5.6, 9.8)
    return m


def visby():
    """Visby class corvette: the faceted stealth hull and superstructure, the 57 mm Bofors Mk3 in its angular turret on
    the bow, the helicopter deck aft, eight RBS15 under deck hatches amidships."""
    m = Model('visby', paint='visby', seed=3302, tex_width=2048)
    m.width = 10.4
    m.camera = 44
    body = m.part('body')
    deck = 3.4
    z0, z1, zb = ship_hull(body, 72.0, 10.4, deck, 4.4, 2.4, 'visby', bow_frac=0.28, chine=1.6, bottom='dgrey')
    # The superstructure: faceted, leaning in level by level, the bridge windows in a dark band.
    levels = ((4.9, 0.0, 1.6, -6.0, 18.0), (4.4, 1.6, 3.2, -4.6, 16.6), (3.8, 3.2, 4.8, -3.2, 15.0), (3.1, 4.8, 6.2, -1.8, 13.2))
    for hw, y0, y1, za, zz in levels:
        body.box((-hw, deck + y0, za), (hw, deck + y1, zz), 'visby')
    body.box((-3.0, deck + 5.0, 15.0), (3.0, deck + 5.7, 15.03), 'glass')
    body.box((-2.6, deck + 5.3, 13.2), (2.6, deck + 6.0, 13.23), 'glass')
    m.cab(-3.1, 3.1, deck + 4.8, deck + 6.2, 8.0, 13.2, kind='boat', hatch=False)
    # The radar pyramid on top, the low mast.
    body.box((-2.2, deck + 6.2, 2.0), (2.2, deck + 7.4, 10.0), 'visby')
    body.box((-1.4, deck + 7.4, 3.2), (1.4, deck + 8.6, 8.8), 'visby')
    body.box((-1.42, deck + 7.6, 4.0), (1.42, deck + 8.4, 8.0), 'aesa')
    body.box((-0.5, deck + 8.6, 5.4), (0.5, deck + 9.4, 6.6), 'visby')
    body.box((-0.06, deck + 9.4, 5.94), (0.06, deck + 11.4, 6.06), 'dgrey')
    # The helicopter deck aft: flat, a yellow circle (in squares) and a centre line.
    body.box((-4.6, deck, -34.0), (4.6, deck + 0.05, -8.0), 'deck')
    for a, b in ((-2.6, -2.3), (2.3, 2.6)):
        body.box((a, deck + 0.05, -24.0), (b, deck + 0.07, -18.0), 'yellow')
    for zz in (-24.3, -18.0):
        body.box((-2.6, deck + 0.05, zz), (2.6, deck + 0.07, zz + 0.3), 'yellow')
    body.box((-0.12, deck + 0.05, -34.0), (0.12, deck + 0.07, -8.0), 'white')
    # The RBS15 hatches amidships (two rows of four, flush with the deck).
    vls(m, body, 0.0, deck + 0.1, -6.0 + 0.0, 4, 2, 1.1, 'visby')
    # Hull number and the water-jets.
    body.box((-5.22, 1.8, 20.0), (-5.2, 3.0, 23.0), 'white')
    body.box((5.2, 1.8, 20.0), (5.22, 3.0, 23.0), 'white')
    for s in (-1, 1):
        body.box((s * 2.0 - 0.6, -0.8, z0 - 0.05), (s * 2.0 + 0.6, 0.2, z0), 'dark')
    # The 57 mm Mk3: the faceted cupola that hides the barrel when it is not firing.
    tz = 24.0
    tur = m.part('turret', (0, deck, tz))
    m.set_turret(tur)
    tur.box((-1.6, deck, tz - 1.8), (1.6, deck + 1.0, tz + 1.4), 'visby')
    tur.box((-1.35, deck + 1.0, tz - 1.6), (1.35, deck + 1.8, tz + 0.9), 'visby')
    tur.box((-0.9, deck + 1.8, tz - 1.3), (0.9, deck + 2.3, tz + 0.2), 'visby')
    gun(m, tur, (0, deck + 1.1, tz + 1.4), 3.4, 0.07, paint='visby', mantlet=0.35)
    m.seat('driver', -1.0, deck + 4.9, 11.6)
    m.seat('gunner', 1.0, deck + 4.9, 11.6)
    return m


def bastion():
    """K-340P launcher of the Bastion-P on the MZKT-7930: the long container of two P-800 Oniks that stands up on end
    to fire, the 8x8 chassis with its cab in front."""
    m = Model('bastion', paint='rgreen', seed=3303)
    m.width = 3.1
    m.wheelbase = 7.2
    m.camera = 15
    body = m.part('body')
    zc = cabover(body, 6.8, 3.0, 'rgreen', cab_len=2.4, cab_h=3.1, y0=1.2)
    frame(body, -6.6, zc + 0.2, 0.9, 1.25, half=0.55)
    body.box((-1.5, 1.25, -6.8), (1.5, 1.5, zc - 0.1), 'rgreen')
    # Jacks at the back, the container's rest at the front.
    for s in (-1, 1):
        body.box((s * 1.3 - 0.12, 0.2, -6.5), (s * 1.3 + 0.12, 1.25, -6.2), 'dark')
    body.box((-0.9, 1.5, 3.4), (0.9, 2.1, 3.8), 'dark')
    lights(body, -6.8, 1.3, 1.2, front=False)
    piv = (0, 2.1, -6.2)
    cont = body.part('container', piv)
    m.set_elevator(cont, deploy=90, fixed=0)
    cont.box((-1.35, 2.1, -6.4), (1.35, 3.8, 4.2), 'rgreen', sides={'front': 'dgrey', 'back': 'dgrey'})
    for zz in (-5.6, -2.0, 1.6):
        cont.box((-1.4, 2.05, zz), (1.4, 3.85, zz + 0.25), 'dgrey')
    for i, s in enumerate((-1, 1)):
        # The front covers of the two launch tubes (blown off when the missile goes).
        lid = cont.part('lid_%d' % i, piv)
        lid.box((s * 0.65 - 0.55, 2.4, 4.2), (s * 0.65 + 0.55, 3.5, 4.32), 'dgrey')
        m.rail('lid_%d' % i, (s * 0.65, 2.95, 4.4))
    axles(m, [5.6, 3.9, -2.4, -4.1], 0.72, 1.12, steer=2)
    m.seat('driver', -0.7, 1.75, 5.6)
    m.seat('gunner', 0.7, 1.75, 5.6)
    return m


def nmesis():
    """NMESIS: the ROGUE-Fires carrier (an unmanned JLTV) with its launcher of two Naval Strike Missiles that rises to
    fire over the side of the chassis."""
    m = Model('nmesis', paint='sand', seed=3304)
    m.width = 2.5
    m.wheelbase = 3.6
    m.camera = 10
    body = m.part('body')
    # The JLTV chassis: the low armoured nose, the short cab, a flat bed.
    body.box((-1.2, 0.8, 1.6), (1.2, 1.7, 3.2), 'sand')
    body.box((-1.15, 1.7, 2.2), (1.15, 1.9, 3.15), 'sand')
    body.box((-1.2, 0.8, -0.2), (1.2, 2.35, 1.6), 'sand')
    body.box((-1.05, 1.75, 1.6), (1.05, 2.25, 1.63), 'glass')
    for s in (-1, 1):
        body.box((s * 1.2 - (0.0 if s > 0 else 0.03), 1.7, 0.1), (s * 1.2 + (0.03 if s > 0 else 0.0), 2.2, 1.3), 'glass')
    m.cab(-1.2, 1.2, 0.8, 2.35, -0.2, 1.6, kind='truck')
    body.box((-1.25, 0.75, -3.2), (1.25, 1.1, -0.2), 'sand')
    body.box((-1.0, 0.85, 3.2), (1.0, 1.25, 3.3), 'dark')
    lights(body, 3.25, 1.3, 1.0)
    lights(body, -3.2, 1.0, 1.0, front=False)
    piv = (0, 1.25, -2.9)
    pod = body.part('pod', piv)
    m.set_elevator(pod, deploy=35, fixed=0)
    pod.box((-0.95, 1.25, -3.0), (0.95, 1.3, 1.2), 'dgrey')
    for i, s in enumerate((-1, 1)):
        canister(pod, s * 0.48, 1.68, -3.0, 4.1, 0.38, 'sand')
        cap = pod.part('cap_%d' % i, piv)
        cap.box((s * 0.48 - 0.2, 1.48, 1.1), (s * 0.48 + 0.2, 1.88, 1.16), 'dark')
        m.rail('cap_%d' % i, (s * 0.48, 1.68, 1.15))
    axles(m, [2.2, -1.4], 0.55, 1.0)
    m.seat('driver', -0.5, 1.2, 0.8)
    m.seat('gunner', 0.5, 1.2, 0.8)
    return m


def all_models():
    return [buyan_m(), visby(), bastion(), nmesis()]
