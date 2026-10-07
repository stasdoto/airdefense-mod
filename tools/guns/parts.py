"""Reusable gun parts: grips, magazines, stocks, sights and optics, muzzle devices, rails, bipods."""


# ----------------------------------------------------------------------------------------------------------------
# Grips and trigger

def trigger(g, z_front=-7.2, z_back=-0.2, depth=2.6, trig_z=-3.0, style='black', w=1.0):
    """The trigger guard (a loop in front of the grip) and the trigger blade."""
    g.bar(z_front, z_front + 0.7, -depth, 0, w * 0.7, style, aim=False)
    g.bar(z_front, z_back, -depth, -depth + 0.4, w * 0.7, style, aim=False)
    g.bar(trig_z - 0.35, trig_z + 0.35, -1.0, 0, 0.45, style, aim=False)
    g.bar(trig_z, trig_z + 0.7, -1.9, -1.0, 0.45, style, aim=False)


def pistol_grip(g, style='plum', top=(0.0, 3.4), bottom=(3.4, 7.2), y_bottom=-10.5, w=2.7, sides=None):
    """A slanted pistol grip from its top (z range at y=0) to its bottom (z range at y_bottom)."""
    n = 7
    for i in range(n):
        ya = -abs(y_bottom) * i / n
        yb = -abs(y_bottom) * (i + 1) / n
        t = (i + 0.5) / n
        z0 = top[0] + (bottom[0] - top[0]) * t
        z1 = top[1] + (bottom[1] - top[1]) * t
        g.box((-w / 2, yb, z0), (w / 2, ya, z1), style, aim=False, sides=sides, edges=False)
    # Base cap.
    g.box((-w / 2 - 0.05, y_bottom - 0.4, bottom[0] - 0.1), (w / 2 + 0.05, y_bottom, bottom[1] + 0.2), style, aim=False)


# ----------------------------------------------------------------------------------------------------------------
# Magazines

def curved_mag(g, z_back=-8.6, depth=7.0, length=19.0, curve=6.0, w=2.6, style='mag_plum', y_top=0.6, lip=True, plate='black'):
    """A curved box magazine hanging from the receiver, curving forward as it goes down."""
    n = max(6, int(length / 1.6))
    for i in range(n):
        ya = y_top - length * i / n
        yb = y_top - length * (i + 1) / n
        t = (i + 0.5) / n
        off = curve * t * t
        g.box((-w / 2, yb, z_back - depth - off), (w / 2, ya, z_back - off), style, edges=False)
    # Floor plate.
    off = curve
    g.box((-w / 2 - 0.15, y_top - length - 0.5, z_back - depth - off - 0.2), (w / 2 + 0.15, y_top - length, z_back - off + 0.2), plate)
    if lip:
        # The reinforcing lugs along the top edge.
        g.box((-w / 2 - 0.1, y_top - 1.2, z_back - depth - 0.1), (w / 2 + 0.1, y_top, z_back - depth + 0.6), style)


def straight_mag(g, z_back, depth, length, w, style, y_top=0.5, slant=0.0, plate='black'):
    n = max(3, int(length / 2.0))
    for i in range(n):
        ya = y_top - length * i / n
        yb = y_top - length * (i + 1) / n
        off = slant * (i + 0.5) / n
        g.box((-w / 2, yb, z_back - depth - off), (w / 2, ya, z_back - off), style, edges=False)
    g.box((-w / 2 - 0.15, y_top - length - 0.5, z_back - depth - slant - 0.15), (w / 2 + 0.15, y_top - length, z_back - slant + 0.15), plate)


# ----------------------------------------------------------------------------------------------------------------
# Sights and optics. y = where the line of sight is (the gun's sight height); z = position along the gun.

def rail(g, z0, z1, y, w=2.1, style='rail', h=0.9):
    """A Picatinny rail on top: a base and the ribbed top."""
    za, zb = min(z0, z1), max(z0, z1)
    g.box((-w / 2, y, za), (w / 2, y + h * 0.55, zb), style.replace('rail', 'black') if style == 'rail' else 'tan_metal')
    g.box((-w / 2 - 0.15, y + h * 0.55, za), (w / 2 + 0.15, y + h, zb), style)


def side_rail(g, z0, z1, y, x, style='rail', h=1.8):
    za, zb = min(z0, z1), max(z0, z1)
    s = 1 if x > 0 else -1
    g.box((x if s > 0 else x - 0.7, y - h / 2, za), (x + 0.7 if s > 0 else x, y + h / 2, zb), style)


def red_dot(g, z, rail_top, sight, style='black'):
    """An Aimpoint-style red dot on a mount: the tube with its lenses, the battery knob."""
    g.bar(z - 3.0, z + 3.0, rail_top, sight - 1.9, 2.4, style)
    g.cyl(z - 6.0, z + 3.5, sight, 2.0, style)
    g.cyl(z - 6.6, z - 6.0, sight, 2.15, 'rubber')
    g.box((-1.3, sight - 1.3, z - 6.65), (1.3, sight + 1.3, z - 6.6), 'glass_red', faces=('north',))
    g.box((-1.3, sight - 1.3, z + 3.5), (1.3, sight + 1.3, z + 3.55), 'glass', faces=('south',))
    g.cyl_x(2.0, 2.9, sight, z - 1.5, 0.9, style)
    g.cyl_y(0, z - 1.5, sight + 2.0, sight + 2.7, 0.8, style)


def holo(g, z, rail_top, sight, style='black'):
    """An EOTech-style holographic sight: a box with a big window and a hood."""
    g.bar(z - 4.0, z + 6.0, rail_top, sight - 2.2, 3.4, style)
    # Hood over the window: two side walls and a roof.
    for x in (-2.0, 1.6):
        g.box((x, sight - 2.2, z - 4.0), (x + 0.4, sight + 2.2, z + 1.0), style)
    g.box((-2.0, sight + 1.8, z - 4.0), (2.0, sight + 2.3, z + 1.0), style)
    g.box((-1.6, sight - 1.7, z - 1.0), (1.6, sight + 1.6, z - 0.9), 'glass', faces=('north', 'south'))
    # Buttons at the back.
    g.box((-1.2, sight - 2.2, z + 6.0), (1.2, sight - 0.9, z + 6.4), 'rubber')


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
    g.box((-2.6, y_mount - 0.5, z_front + L * 0.25), (-1.3, sight - 0.8, z_front + L * 0.7), style)
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


# ----------------------------------------------------------------------------------------------------------------
# Muzzle devices

def ak74_brake(g, z0, y, style='black'):
    g.cyl(z0, z0 - 7.5, y, 1.1, 'slots')
    g.cyl(z0 - 7.0, z0 - 7.6, y, 1.15, style)


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
    """A solid stock whose top and bottom edges run straight from (z0) to (z1); a butt plate at the end."""
    n = n or max(4, int(abs(z1 - z0) / 1.8))
    for i in range(n):
        t = (i + 0.5) / n
        za = z0 + (z1 - z0) * i / n
        zb = z0 + (z1 - z0) * (i + 1) / n
        top = top0 + (top1 - top0) * t
        bot = bot0 + (bot1 - bot0) * t
        w = w0 + (w1 - w0) * t
        g.box((-w / 2, bot, min(za, zb)), (w / 2, top, max(za, zb)), style, aim=aim, sides=sides, edges=False)
    if plate:
        g.box((-w1 / 2 - 0.05, bot1 - 0.1, z1), (w1 / 2 + 0.05, top1 + 0.1, z1 + plate_t), plate, aim=aim)


def skeleton_stock(g, z0, z1, top0, top1, bot0, bot1, t=1.2, w=1.6, style='black', plate='rubber', aim=False):
    """A frame stock: an upper and a lower bar and a butt plate (AKS-74U, PKM, VSS)."""
    n = max(4, int(abs(z1 - z0) / 2.0))
    for i in range(n):
        f = (i + 0.5) / n
        za = z0 + (z1 - z0) * i / n
        zb = z0 + (z1 - z0) * (i + 1) / n
        top = top0 + (top1 - top0) * f
        bot = bot0 + (bot1 - bot0) * f
        g.box((-w / 2, top - t, min(za, zb)), (w / 2, top, max(za, zb)), style, aim=aim, edges=False)
        g.box((-w / 2, bot, min(za, zb)), (w / 2, bot + t, max(za, zb)), style, aim=aim, edges=False)
    g.box((-w / 2 - 0.3, bot1 - 0.3, z1), (w / 2 + 0.3, top1 + 0.3, z1 + 1.0), plate, aim=aim)


def buffer_stock(g, z0, y, style='poly', length=17.0, butt_bottom=-4.5, tube_r=1.5, cheek=True):
    """An AR-style collapsible stock on a buffer tube."""
    g.cyl(z0, z0 + length * 0.75, y, tube_r, 'black', aim=False)
    zs = z0 + length * 0.4
    ze = z0 + length
    # Stock body round the tube, sloping lower edge, the butt pad.
    for i in range(5):
        t = (i + 0.5) / 5
        za = zs + (ze - zs) * i / 5
        zb = zs + (ze - zs) * (i + 1) / 5
        bot = y - tube_r - 0.6 - (y - tube_r - 0.6 - butt_bottom) * t * t
        g.box((-1.9, bot, za), (1.9, y + tube_r + 0.7, zb), style, aim=False, edges=False)
    if cheek:
        g.box((-1.6, y + tube_r + 0.7, zs + 1.0), (1.6, y + tube_r + 1.4, ze - 0.5), style, aim=False)
    g.box((-2.0, butt_bottom - 0.3, ze), (2.0, y + tube_r + 1.5, ze + 1.2), 'rubber', aim=False)
    # Adjustment lever under the tube.
    g.bar(zs - 1.0, zs + 2.0, y - tube_r - 1.0, y - tube_r - 0.2, 0.8, 'black', aim=False)


# ----------------------------------------------------------------------------------------------------------------
# Bipods (folded under the barrel)

def bipod(g, z_pivot, y_pivot, length, style='black', spread=1.0):
    g.bar(z_pivot - 1.2, z_pivot + 1.2, y_pivot - 1.0, y_pivot, 2.4, style)
    for x in (-spread, spread):
        g.bar(z_pivot - length, z_pivot, y_pivot - 1.3, y_pivot - 0.7, 0.55, style, x=x)
        g.bar(z_pivot - length - 1.2, z_pivot - length, y_pivot - 1.6, y_pivot - 0.5, 0.8, 'rubber', x=x)
