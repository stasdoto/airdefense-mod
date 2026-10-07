"""Reusable gun parts: grips, magazines, stocks, sights and optics, muzzle devices, rails, bipods.
Everything in centimetres, gun space (x right, y up, barrel to -z, origin at the top front of the pistol grip)."""
import math


# ----------------------------------------------------------------------------------------------------------------
# Little things

def rivet(g, x, y, z, r=0.22, style='steel', side=1):
    """A rivet or a pin head on the side (side = +1 right, -1 left); x = the side's surface."""
    if side > 0:
        g.box((x, y - r, z - r), (x + 0.06, y + r, z + r), style, faces=('east', 'up', 'down', 'north', 'south'))
    else:
        g.box((x - 0.06, y - r, z - r), (x, y + r, z + r), style, faces=('west', 'up', 'down', 'north', 'south'))


def plate(g, z0, z1, y0, y1, x, style, side=1, t=0.05, **kw):
    """A flat patch on a side (a marking, a stamped dimple, a groove's shadow)."""
    if side > 0:
        g.box((x, y0, min(z0, z1)), (x + t, y1, max(z0, z1)), style, faces=('east',), **kw)
    else:
        g.box((x - t, y0, min(z0, z1)), (x, y1, max(z0, z1)), style, faces=('west',), **kw)


def both_sides(fn, *a, **kw):
    fn(*a, side=1, **kw)
    fn(*a, side=-1, **kw)


# ----------------------------------------------------------------------------------------------------------------
# Grips and trigger

def trigger(g, z_front=-7.2, z_back=-0.2, depth=2.6, trig_z=-3.0, style='black', w=1.0, curved=True):
    """The trigger guard (a loop in front of the grip) and the trigger blade."""
    g.bar(z_front, z_front + 0.7, -depth, 0, w * 0.7, style, aim=False)
    g.bar(z_front, z_back, -depth, -depth + 0.4, w * 0.7, style, aim=False)
    g.bar(trig_z - 0.35, trig_z + 0.35, -1.0, 0, 0.45, style, aim=False)
    if curved:
        g.slab((trig_z + 0.05, -0.9), (trig_z + 0.6, -2.0), 0.55, 0.45, style, aim=False)
    else:
        g.bar(trig_z, trig_z + 0.7, -1.9, -1.0, 0.45, style, aim=False)


def grip(g, top, bottom, thick, w, style, cap='black', sides=None, cap_t=0.5, aim=False):
    """A pistol grip as one slab from the middle of its top (z, y) to the middle of its bottom, {thick} front to
    back, {w} wide, with a cap at the bottom."""
    g.slab(top, bottom, thick, w, style, aim=aim, sides=sides, edges=False, ext=0.15)
    if cap:
        (zt, yt), (zb, yb) = top, bottom
        L = math.hypot(zb - zt, yb - yt)
        dz, dy = (zb - zt) / L, (yb - yt) / L
        c0 = (zb - dz * cap_t, yb - dy * cap_t)
        g.slab(c0, (zb + dz * 0.05, yb + dy * 0.05), thick + 0.25, w + 0.15, cap, aim=aim)


def pistol_grip(g, style='plum', top=(0.0, 3.4), bottom=(3.4, 7.2), y_bottom=-10.5, w=2.7, sides=None, cap='black'):
    """A slanted pistol grip from its top (z range at y=0) to its bottom (z range at y_bottom)."""
    zt = (top[0] + top[1]) / 2
    zb = (bottom[0] + bottom[1]) / 2
    thick = ((top[1] - top[0]) + (bottom[1] - bottom[0])) / 2
    grip(g, (zt, 0.2), (zb, y_bottom), thick, w, style, cap=cap, sides=sides)


# ----------------------------------------------------------------------------------------------------------------
# Magazines

def arc_mag(g, top, length, ang0, ang1, depth, w, style, n=7, plate='black', plate_t=0.6, lugs=None, depth1=None, aim=True):
    """A magazine as a chain of slabs along an arc: from the middle of its top (z, y), {length} long, its direction
    turning from {ang0} to {ang1} degrees off straight down (towards the muzzle = positive); {depth} front to back
    (growing to depth1 at the bottom), {w} wide."""
    z, y = top
    seg = length / n
    depth1 = depth if depth1 is None else depth1
    pts = [(z, y)]
    for i in range(n):
        a = math.radians(ang0 + (ang1 - ang0) * (i + 0.5) / n)
        z += -math.sin(a) * seg
        y += -math.cos(a) * seg
        pts.append((z, y))
    for i in range(n):
        d = depth + (depth1 - depth) * (i + 0.5) / n
        g.slab(pts[i], pts[i + 1], d, w, style, ext=0.35 if i < n - 1 else 0.0, edges=False, aim=aim)
    # Floor plate, square to the last slab.
    (za, ya), (zb, yb) = pts[-2], pts[-1]
    L = math.hypot(zb - za, yb - ya)
    dz, dy = (zb - za) / L, (yb - ya) / L
    if plate:
        g.slab((zb - dz * 0.1, yb - dy * 0.1), (zb + dz * plate_t, yb + dy * plate_t), depth1 + 0.35, w + 0.3, plate, aim=aim)
    return pts


def curved_mag(g, z_back=-8.6, depth=7.0, length=19.0, curve=6.0, w=2.6, style='mag_plum', y_top=0.6, lip=True, plate='black'):
    """A curved box magazine hanging from the receiver, curving forward as it goes down."""
    top = (z_back - depth / 2, y_top)
    ang1 = math.degrees(math.atan2(curve * 2.2, length))
    pts = arc_mag(g, top, length, 3.0, ang1, depth, w, style, plate=plate)
    if lip:
        g.box((-w / 2 - 0.1, y_top - 1.2, z_back - depth - 0.1), (w / 2 + 0.1, y_top, z_back - depth + 0.6), style)
    return pts


def straight_mag(g, z_back, depth, length, w, style, y_top=0.5, slant=0.0, plate='black', aim=True):
    """A straight box magazine (slant = how far forward its bottom is)."""
    top = (z_back - depth / 2, y_top)
    bottom = (z_back - depth / 2 - slant, y_top - length)
    g.slab(top, bottom, depth, w, style, edges=False, aim=aim)
    L = math.hypot(slant, length)
    dz, dy = -slant / L, -length / L
    if plate:
        g.slab((bottom[0] - dz * 0.1, bottom[1] - dy * 0.1), (bottom[0] + dz * 0.5, bottom[1] + dy * 0.5), depth + 0.3, w + 0.3, plate, aim=aim)


# ----------------------------------------------------------------------------------------------------------------
# Sights and optics. y = where the line of sight is (the gun's sight height); z = position along the gun.

def rail(g, z0, z1, y, w=2.1, style='rail', h=0.9):
    """A Picatinny rail on top: a base and the ribbed top."""
    za, zb = min(z0, z1), max(z0, z1)
    g.box((-w / 2, y, za), (w / 2, y + h * 0.55, zb), 'black' if style == 'rail' else 'tan_metal')
    g.box((-w / 2 - 0.15, y + h * 0.55, za), (w / 2 + 0.15, y + h, zb), style)


def side_rail(g, z0, z1, y, x, style='rail', h=1.8):
    za, zb = min(z0, z1), max(z0, z1)
    s = 1 if x > 0 else -1
    g.box((x if s > 0 else x - 0.7, y - h / 2, za), (x + 0.7 if s > 0 else x, y + h / 2, zb), style)


def bottom_rail(g, z0, z1, y, style='rail', w=2.1):
    za, zb = min(z0, z1), max(z0, z1)
    g.box((-w / 2 - 0.15, y - 0.5, za), (w / 2 + 0.15, y, zb), style)


def red_dot(g, z, rail_top, sight, style='black'):
    """An Aimpoint-style red dot on a mount: the tube with its lenses, the battery knob."""
    g.bar(z - 3.0, z + 3.0, rail_top, sight - 1.9, 2.4, style)
    g.bar(z - 2.6, z + 2.6, rail_top, rail_top + 0.6, 3.2, style)
    g.cyl(z - 6.0, z + 3.5, sight, 2.0, style)
    g.cyl(z - 6.6, z - 6.0, sight, 2.15, 'rubber')
    g.box((-1.3, sight - 1.3, z - 6.65), (1.3, sight + 1.3, z - 6.6), 'glass_red', faces=('north',))
    g.box((-1.3, sight - 1.3, z + 3.5), (1.3, sight + 1.3, z + 3.55), 'glass', faces=('south',))
    g.cyl_x(2.0, 2.9, sight, z - 1.5, 0.9, style)
    g.cyl_y(0, z - 1.5, sight + 2.0, sight + 2.7, 0.8, style)


def holo(g, z, rail_top, sight, style='black'):
    """An EOTech-style holographic sight: a box with a big window and a hood."""
    g.bar(z - 4.0, z + 6.0, rail_top, sight - 2.2, 3.4, style)
    for x in (-2.0, 1.6):
        g.box((x, sight - 2.2, z - 4.0), (x + 0.4, sight + 2.2, z + 1.0), style)
    g.box((-2.0, sight + 1.8, z - 4.0), (2.0, sight + 2.3, z + 1.0), style)
    g.box((-1.6, sight - 1.7, z - 1.0), (1.6, sight + 1.6, z - 0.9), 'glass', faces=('north', 'south'))
    g.box((-1.2, sight - 2.2, z + 6.0), (1.2, sight - 0.9, z + 6.4), 'rubber')
    g.box((1.7, sight - 2.2, z + 1.5), (2.3, sight - 1.0, z + 5.0), style)


def acog(g, z, rail_top, sight, style='black'):
    """A Trijicon ACOG-style 4x scope: a short fat tube with the fibre on top."""
    g.bar(z - 3.0, z + 3.0, rail_top, sight - 1.8, 2.6, style)
    g.cyl(z - 7.0, z + 6.0, sight, 2.0, style)
    g.cyl(z - 9.0, z - 7.0, sight, 2.4, style)
    g.cyl(z + 6.0, z + 7.5, sight, 1.8, 'rubber')
    g.box((-1.7, sight - 1.7, z - 9.05), (1.7, sight + 1.7, z - 9.0), 'glass', faces=('north',))
    g.bar(z - 6.0, z + 2.0, sight + 2.0, sight + 2.6, 0.7, 'glow_green')


def scope(g, z_front, z_back, rail_top, sight, r=1.6, obj=2.4, style='black', rings=2, turrets=True):
    """A long rifle scope: objective bell, tube, turrets, eyepiece; rings to the rail."""
    zf, zb = z_front, z_back
    L = zb - zf
    g.cyl(zf, zf + L * 0.22, sight, obj, style)
    g.cyl(zf + L * 0.22, zf + L * 0.3, sight, (obj + r) / 2, style)
    g.cyl(zf + L * 0.3, zb - L * 0.2, sight, r, style)
    g.cyl(zb - L * 0.2, zb, sight, r * 1.25, style)
    g.cyl(zb, zb + 1.2, sight, r * 1.2, 'rubber')
    g.box((-obj * 0.7, sight - obj * 0.7, zf - 0.05), (obj * 0.7, sight + obj * 0.7, zf), 'glass', faces=('north',))
    if turrets:
        zt = zf + L * 0.55
        g.cyl_y(0, zt, sight + r, sight + r + 1.4, 1.0, style)
        g.cyl_x(r, r + 1.4, sight, zt, 1.0, style)
        g.cyl_x(-r - 0.9, -r, sight, zt + 2.5, 0.9, style)
    for i in range(rings):
        zr = zf + L * (0.35 + 0.35 * i)
        g.bar(zr - 1.0, zr + 1.0, rail_top, sight - r + 0.2, 2.4, style)
        g.bar(zr - 1.0, zr + 1.0, sight - r - 0.3, sight + r + 0.3, r * 2 + 0.6, style)


def pso1(g, z_front, z_back, y_mount, sight, style='black'):
    """The PSO-1 sniper scope (SVD, VSS): side mount on the left, rubber eyecup, two turrets, a sunshade."""
    L = z_back - z_front
    g.box((-2.8, y_mount - 0.5, z_front + L * 0.25), (-1.3, sight - 0.8, z_front + L * 0.7), style)
    g.box((-3.2, y_mount - 1.6, z_front + L * 0.3), (-2.6, y_mount + 0.2, z_front + L * 0.62), style)
    g.cyl(z_front, z_front + L * 0.2, sight, 2.1, style)
    g.cyl(z_front + L * 0.2, z_back - L * 0.15, sight, 1.5, style)
    g.cyl(z_back - L * 0.15, z_back, sight, 1.7, style)
    g.cyl(z_back, z_back + 3.0, sight, 1.9, 'rubber')
    g.box((-1.5, sight - 1.5, z_front - 0.05), (1.5, sight + 1.5, z_front), 'glass', faces=('north',))
    zt = z_front + L * 0.5
    g.cyl_y(0, zt, sight + 1.5, sight + 3.0, 1.1, style)
    g.cyl_x(1.5, 3.0, sight, zt, 1.1, style)
    g.cyl_x(-3.0, -1.5, sight, zt + 3.0, 0.7, 'black')


def iron_post(g, z, y_base, top, w=0.3, ears=True, ear_w=0.35, ear_gap=0.9, style='black'):
    """A front sight post between two protective ears."""
    g.bar(z - 0.3, z + 0.3, y_base, top, w, style)
    if ears:
        for x in (-ear_gap, ear_gap):
            g.bar(z - 0.5, z + 0.5, y_base, top + 0.3, ear_w, style, x=x)


def notch(g, z, y_base, top, w=2.0, gap=0.5, style='black'):
    """A rear sight leaf with its notch."""
    for x in (-(gap / 2 + (w - gap) / 4), gap / 2 + (w - gap) / 4):
        g.bar(z - 0.4, z + 0.4, y_base, top, (w - gap) / 2, style, x=x)


def flip_rear(g, z, base, sight, style='black', w=2.2):
    """A folding aperture rear sight on a rail (AR, SCAR): the base and the ring."""
    g.bar(z - 1.6, z + 1.6, base, base + 0.7, w, style)
    g.bar(z - 0.4, z + 0.4, base + 0.7, sight + 0.7, w * 0.7, style)
    g.box((-0.25, sight - 0.25, z - 0.45), (0.25, sight + 0.25, z + 0.45), 'rubber', faces=('north', 'south'))


def flip_front(g, z, base, sight, style='black', w=2.0):
    g.bar(z - 1.2, z + 1.2, base, base + 0.7, w, style)
    g.bar(z - 0.25, z + 0.25, base + 0.7, sight, 0.3, style)
    for x in (-0.8, 0.8):
        g.bar(z - 0.4, z + 0.4, base + 0.7, sight + 0.3, 0.3, style, x=x)


# ----------------------------------------------------------------------------------------------------------------
# Muzzle devices

def ak74_brake(g, z0, y, style='black'):
    """The AK-74 muzzle brake: a big chamber with two side windows, the front cut in a V."""
    g.cyl(z0, z0 - 1.2, y, 0.95, style)
    g.cyl(z0 - 1.2, z0 - 7.6, y, 1.15, style)
    # The big side windows and the small vent holes on top.
    for x in (1.0, -1.06):
        g.box((x, y - 0.55, z0 - 6.9), (x + 0.06, y + 0.55, z0 - 4.8), 'rubber', faces=('east', 'west'))
    g.box((-0.55, y + 1.12, z0 - 3.8), (0.55, y + 1.18, z0 - 2.6), 'rubber', faces=('up',))
    g.box((-0.55, y + 1.12, z0 - 2.2), (0.55, y + 1.18, z0 - 1.6), 'rubber', faces=('up',))


def slant_comp(g, z0, y, style='black'):
    """AKM slant compensator: the bottom longer than the top."""
    g.cyl(z0, z0 - 2.8, y, 0.95, style)
    g.box((-0.95, y - 0.95, z0 - 4.2), (0.95, y + 0.1, z0 - 2.8), style)


def birdcage(g, z0, y, r=0.85, style='black'):
    g.cyl(z0, z0 - 4.6, y, r, 'slots')
    g.cyl(z0 - 4.6, z0 - 4.9, y, r * 1.05, style)


def big_brake(g, z0, y, r=1.6, style='black', length=9.0):
    """A heavy double-chamber muzzle brake (Barrett, AWM)."""
    g.box((-r * 1.6, y - r, z0 - length), (r * 1.6, y + r, z0), style)
    for i in range(2):
        zc = z0 - length * (0.3 + 0.4 * i)
        g.box((-r * 1.62, y - r * 0.6, zc - length * 0.12), (r * 1.62, y + r * 0.6, zc + length * 0.12), 'black', sides={'east': 'rubber', 'west': 'rubber'})


def suppressor(g, z0, length, y, r, style='black'):
    g.cyl(z0, z0 - length, y, r, style)
    g.cyl(z0 - length, z0 - length - 0.3, y, r * 0.8, 'black')


# ----------------------------------------------------------------------------------------------------------------
# Stocks

def profile_stock(g, z0, z1, top0, top1, bot0, bot1, w0, w1, style, n=None, plate='rubber', plate_t=0.8, aim=False, sides=None):
    """A solid stock whose top and bottom edges run straight from z0 to z1 (clean edges), with a butt plate."""
    w = (w0 + w1) / 2
    g.profile(((z0, top0), (z1, top1)), ((z0, bot0), (z1, bot1)), w, style, aim=aim, n=n)
    if plate:
        g.box((-w1 / 2 - 0.05, bot1 - 0.1, z1), (w1 / 2 + 0.05, top1 + 0.1, z1 + plate_t), plate, aim=aim)


def skeleton_stock(g, z0, z1, top0, top1, bot0, bot1, t=1.2, w=1.6, style='black', plate='rubber', aim=False):
    """A frame stock: an upper and a lower bar and a butt plate (AKS-74U, PKM, VSS)."""
    g.slab((z0, top0 - t / 2), (z1, top1 - t / 2), t, w, style, aim=aim)
    g.slab((z0, bot0 + t / 2), (z1, bot1 + t / 2), t, w, style, aim=aim)
    g.box((-w / 2 - 0.3, bot1 - 0.3, z1), (w / 2 + 0.3, top1 + 0.3, z1 + 1.0), plate, aim=aim)


def buffer_stock(g, z0, y, style='poly', length=17.0, butt_bottom=-4.5, tube_r=1.5, cheek=True):
    """An AR-style collapsible stock on a buffer tube."""
    g.cyl(z0, z0 + length * 0.75, y, tube_r, 'black', aim=False)
    zs = z0 + length * 0.4
    ze = z0 + length
    top = y + tube_r + 0.7
    g.profile(((zs, top), (ze, top)), ((zs, y - tube_r - 0.6), (ze, butt_bottom)), 3.8, style, aim=False)
    if cheek:
        g.box((-1.6, top, zs + 1.0), (1.6, top + 0.7, ze - 0.5), style, aim=False)
    g.box((-2.0, butt_bottom - 0.3, ze), (2.0, top + 0.8, ze + 1.2), 'rubber', aim=False)
    # Adjustment lever under the tube, the castle nut and the end plate at the receiver.
    g.bar(zs - 1.0, zs + 2.0, y - tube_r - 1.0, y - tube_r - 0.2, 0.8, 'black', aim=False)
    g.cyl(z0, z0 + 1.0, y, tube_r + 0.35, 'gunmetal', aim=False)


# ----------------------------------------------------------------------------------------------------------------
# Bipods (folded under the barrel)

def bipod(g, z_pivot, y_pivot, length, style='black', spread=1.0, feet='rubber'):
    """A bipod folded back along the barrel: the clamp, two legs running back from it, the feet."""
    g.bar(z_pivot - 1.2, z_pivot + 1.2, y_pivot - 1.0, y_pivot + 0.4, 2.6, style)
    for x in (-spread, spread):
        g.bar(z_pivot, z_pivot + length, y_pivot - 1.3, y_pivot - 0.7, 0.55, style, x=x)
        g.bar(z_pivot + length, z_pivot + length + 1.4, y_pivot - 1.7, y_pivot - 0.4, 0.9, feet, x=x)


def sling_loop(g, z, y, x, style='steel', side=1):
    """A sling swivel: a little loop on the side."""
    xo = x if side > 0 else x - 0.3
    g.box((xo, y - 0.6, z - 0.6), (xo + 0.3, y + 0.6, z - 0.4), style)
    g.box((xo, y - 0.6, z + 0.4), (xo + 0.3, y + 0.6, z + 0.6), style)
    g.box((xo, y - 0.8, z - 0.6), (xo + 0.3, y - 0.6, z + 0.6), style)
