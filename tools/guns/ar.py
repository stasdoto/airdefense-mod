"""NATO rifles and machine guns: M4A1, M16A4, HK416, FN SCAR-H, M110, M249, M240B.

AR-15 / AR-10 layout: the lower receiver's bottom at the grip is y = 0, the bore (and the buffer tube) 5 cm up,
the flat-top rail on the upper receiver from 6.9 to 7.8 cm."""
from gunkit import Gun
import parts as P

BORE = 5.0
RAIL = 6.9


def lower(g, style='black', big=1.0, grip='poly', grip_sides='grip', magwell=(-11.8, -3.4)):
    """Lower receiver: the flared magazine well, trigger guard, buffer tower, the controls, the pistol grip."""
    zf, zb = magwell
    g.bar(-12.6 * big, 7.6, 0.0, 3.3, 2.4, style)
    g.bar(zf, zb, -2.2, 0.0, 2.6, style)
    g.bar(zf - 0.3, zf + 0.6, -2.5, -1.6, 2.9, style)
    g.bar(zb - 0.6, zb + 0.3, -2.5, -1.6, 2.9, style)
    # Buffer tower where the stock's tube goes in, the takedown pins.
    g.bar(6.4, 8.2, 1.3, 6.4, 2.5, style)
    for z in (-11.6 * big, 6.8):
        P.rivet(g, 1.2, 2.4, z, r=0.3, style='gunmetal')
        P.rivet(g, -1.2, 2.4, z, r=0.3, style='gunmetal', side=-1)
    # Trigger guard, trigger, the selector (left), mag release (right), bolt catch (left).
    g.bar(zb, 0.4, -2.6, -2.2, 0.8, style, aim=False)
    g.bar(zb, zb + 0.6, -2.6, 0.0, 0.8, style, aim=False)
    g.bar(-1.9, -1.3, -1.0, 0.0, 0.45, 'gunmetal', aim=False)
    g.slab((-1.6, -0.9), (-1.1, -2.0), 0.5, 0.45, 'gunmetal', aim=False)
    g.bar(1.0, 2.4, 2.2, 2.9, 0.3, style, x=-1.35)
    P.rivet(g, -1.2, 2.55, 1.7, r=0.45, style=style, side=-1)
    g.bar(-3.2, -2.2, 1.1, 2.1, 0.5, 'gunmetal', x=1.35)
    g.bar(-2.6, 0.4, 1.4, 2.6, 0.25, style, x=-1.33)
    # Pistol grip.
    P.grip(g, (1.4, 0.3), (4.4, -9.3), 3.2, 2.7, grip, cap=None, sides={'east': grip_sides, 'west': grip_sides})


def upper(g, z_front=-10.5, z_back=7.6, style='black', rail=True, rail_style='rail', assist=True):
    """Upper receiver: the forward assist, the ejection port and its cover, the brass deflector, the charging
    handle, the flat-top rail."""
    g.bar(z_front, z_back, 3.3, RAIL, 2.4, style)
    g.bar(z_front, z_front + 2.6, 3.0, RAIL + 0.1, 2.9, style)
    # Ejection port cover (closed) and the brass deflector behind it, on the right.
    P.plate(g, -5.2, 1.6, 4.0, 5.9, 1.2, 'gunmetal')
    g.bar(-5.0, 1.4, 4.2, 4.5, 0.2, 'gunmetal', x=1.32)
    g.slab((1.8, 6.0), (3.2, 4.6), 0.9, 0.9, style, x=1.45)
    if assist:
        g.slab((1.6, 5.6), (5.0, 4.6), 1.2, 1.3, style, x=1.6)
        g.cyl(5.0, 6.2, 4.65, 0.6, 'gunmetal', x=1.95)
    # Charging handle at the back.
    g.bar(z_back - 1.2, z_back + 1.4, RAIL - 1.1, RAIL - 0.3, 1.4, style)
    g.bar(z_back + 1.0, z_back + 1.6, RAIL - 1.1, RAIL - 0.2, 3.4, style)
    if rail:
        P.rail(g, z_front + 0.3, z_back - 0.6, RAIL, style=rail_style)


def stanag(g, z_back=-3.6, length=18.0, style='mag', curve=10.0, plate='black'):
    """A 30-round STANAG magazine, slightly curved, its top inside the magazine well."""
    P.arc_mag(g, (z_back - 3.15, -0.6), length, 2.0, curve, 6.2, 2.3, style, n=6, depth1=6.4, plate=plate)


def quad_rail(g, z0, z1, size=5.2, style='black', rail_style='rail'):
    """A quad-rail handguard round the barrel: a square tube with rails on the top, the sides and underneath."""
    h = size / 2
    g.bar(z0, z1, BORE - h, BORE + h, size, style)
    P.rail(g, z0 - 0.2, z1 + 0.4, BORE + h, style=rail_style, h=0.7)
    for x in (h, -h):
        P.side_rail(g, z0 - 0.4, z1 + 0.6, BORE, x, style=rail_style, h=1.8)
    P.bottom_rail(g, z0 - 0.4, z1 + 0.6, BORE - h, style=rail_style)
    # Front end cap.
    g.bar(z1, z1 - 0.6, BORE - h - 0.2, BORE + h + 0.2, size + 0.4, style)


def a2_fsb(g, z, style='black', post_top=None):
    """The triangular A2 front sight base on the gas block, with its post and the sling loop under it."""
    g.bar(z - 1.4, z + 1.4, BORE - 1.0, BORE + 1.0, 2.2, style)
    g.slab((z + 1.4, BORE + 0.6), (z + 0.1, BORE + 4.0), 1.2, 1.5, style)
    g.slab((z - 1.4, BORE + 0.6), (z - 0.1, BORE + 4.0), 1.2, 1.5, style)
    g.bar(z - 0.7, z + 0.7, BORE + 3.6, BORE + 4.4, 2.0, style)
    for x in (-0.8, 0.8):
        g.bar(z - 0.5, z + 0.5, BORE + 4.4, (post_top or BORE + 5.6) + 0.2, 0.3, style, x=x)
    g.bar(z - 0.25, z + 0.25, BORE + 4.4, post_top or BORE + 5.6, 0.3, style)
    g.bar(z - 0.6, z + 0.6, BORE - 2.0, BORE - 1.0, 0.8, style)


def barrel(g, z0, z1, r=0.75, style='black', step=None):
    if step:
        g.cyl(z0, step, BORE, r * 1.15, style)
        g.cyl(step, z1, BORE, r, style)
    else:
        g.cyl(z0, z1, BORE, r, style)


def a2_stock(g, z0=8.2, z1=33.5, style='poly'):
    """The fixed A2 stock: straight top in line with the bore, the butt with its trapdoor."""
    g.profile(((z0, BORE + 1.6), (z1, BORE + 1.6)), ((z0, BORE - 1.8), (z1, -5.0)), 3.6, style, aim=False)
    g.box((-1.9, -5.2, z1), (1.9, BORE + 1.8, z1 + 1.0), 'rubber', aim=False)
    g.box((-1.2, -1.0, z1 - 4.0), (1.2, 2.0, z1 + 0.02), 'black', faces=('east', 'west'), aim=False)


def make_m4a1():
    g = Gun('m4a1', 10.6, bore=BORE, R=6, seed=41)
    lower(g)
    upper(g)
    stanag(g)
    quad_rail(g, -10.5, -27.0)
    barrel(g, -27.0, -42.5, step=-31.0)
    a2_fsb(g, -31.0, post_top=10.4)
    P.birdcage(g, -42.5, BORE)
    P.buffer_stock(g, 8.2, BORE, length=17.5, butt_bottom=-4.6)
    P.holo(g, -3.0, RAIL + 0.9, 10.6)
    # Folded rear sight at the back of the rail, a vertical grip under the handguard.
    g.bar(4.0, 6.4, RAIL + 0.9, RAIL + 1.6, 2.0, 'black')
    g.bar(-22.0, -20.0, BORE - 2.6 - 0.5, BORE - 2.6, 2.6, 'black')
    g.cyl_y(0, -21.0, -5.0, BORE - 3.1, 1.3, 'poly')
    return g


def make_m16a4():
    g = Gun('m16a4', 11.0, bore=BORE, R=5, seed=16)
    lower(g)
    upper(g)
    stanag(g)
    quad_rail(g, -10.5, -38.0)
    barrel(g, -38.0, -56.0, step=-41.0)
    a2_fsb(g, -41.5, post_top=10.6)
    P.birdcage(g, -56.0, BORE)
    a2_stock(g)
    P.acog(g, -2.0, RAIL + 0.9, 11.0)
    return g


def make_hk416():
    g = Gun('hk416', 10.8, bore=BORE, R=6, seed=416)
    lower(g)
    upper(g, assist=True)
    stanag(g, style='mag_fde', plate='fde')
    # The HK free-floating rail, long, with the piston inside; a folding front sight at its end.
    quad_rail(g, -10.5, -32.0, size=5.4)
    barrel(g, -32.0, -40.0)
    P.flip_front(g, -30.5, BORE + 2.7 + 0.7, 10.8)
    g.cyl(-40.0, -44.0, BORE, 0.95, 'slots')
    # HK stock: a slim body on the tube, a big rubber pad.
    g.cyl(8.2, 22.0, BORE, 1.5, 'black', aim=False)
    g.cyl(8.2, 9.2, BORE, 1.85, 'gunmetal', aim=False)
    g.profile(((14.5, BORE + 2.4), (26.0, BORE + 2.4)), ((14.5, BORE - 2.0), (26.0, -4.4)), 3.8, 'poly', aim=False)
    g.box((-2.0, -4.6, 26.0), (2.0, BORE + 2.6, 27.4), 'rubber', aim=False)
    P.red_dot(g, -3.0, RAIL + 0.9, 10.8)
    P.flip_rear(g, 4.6, RAIL + 0.9, 10.8)
    return g


def make_scarh():
    g = Gun('scarh', 10.9, bore=BORE, R=5, seed=17)
    # Lower (polymer, tan) with the grip.
    g.bar(-12.6, 7.0, 0.0, 3.3, 2.6, 'fde')
    g.bar(-11.6, -3.4, -2.4, 0.0, 2.8, 'fde')
    g.bar(-3.4, 0.4, -2.6, -2.2, 0.8, 'fde', aim=False)
    g.bar(-1.9, -1.3, -1.0, 0.0, 0.45, 'gunmetal', aim=False)
    g.slab((-1.6, -0.9), (-1.1, -2.0), 0.5, 0.45, 'gunmetal', aim=False)
    P.grip(g, (1.4, 0.3), (4.4, -9.3), 3.3, 2.8, 'fde', cap=None, sides={'east': 'fde_grip', 'west': 'fde_grip'})
    g.bar(1.0, 2.4, 2.2, 2.9, 0.3, 'black', x=-1.45)
    # Monolithic aluminium upper: one long block from the stock hinge to the front of the handguard.
    g.bar(-34.0, 8.0, 3.3, RAIL, 3.4, 'fde_metal')
    g.bar(-34.0, -14.0, 1.6, 3.3, 3.4, 'fde_metal')
    P.rail(g, -34.0, 7.8, RAIL, style='rail_tan')
    for x in (1.7, -1.7):
        P.side_rail(g, -16.0, -32.0, 3.6, x, style='rail_tan', h=1.6)
    P.bottom_rail(g, -16.0, -32.0, 1.6, style='rail_tan')
    # Non-reciprocating charging handle on the left, the ejection port on the right.
    g.box((-2.9, 4.6, -12.0), (-1.7, 5.4, -10.6), 'black')
    P.plate(g, -6.0, 1.0, 4.0, 5.6, 1.7, 'groove')
    # 20-round 7.62 magazine, almost straight.
    P.straight_mag(g, z_back=-3.6, depth=7.6, length=15.0, w=2.6, style='mag', slant=1.6)
    barrel(g, -34.0, -46.0, r=0.85)
    P.flip_front(g, -33.0, RAIL + 0.9, 10.9, style='black')
    g.cyl(-46.0, -51.0, BORE, 1.0, 'slots')
    # Side-folding stock with the cheek riser and the butt pad.
    g.bar(8.0, 10.0, 1.6, RAIL, 3.0, 'black', aim=False)
    g.bar(10.0, 17.0, 3.6, 6.4, 2.0, 'fde', aim=False)
    g.profile(((16.0, RAIL + 1.6), (29.0, RAIL + 1.6)), ((16.0, 2.6), (29.0, -4.6)), 3.4, 'fde', aim=False)
    g.box((-1.9, -4.8, 29.0), (1.9, RAIL + 1.8, 30.4), 'rubber', aim=False)
    P.holo(g, -3.0, RAIL + 0.9, 10.9)
    return g


def make_m110():
    g = Gun('m110', 13.2, bore=BORE + 0.4, R=5, seed=110)
    lower(g, big=1.08, magwell=(-13.0, -3.4))
    upper(g, z_front=-12.0, z_back=8.4)
    P.straight_mag(g, z_back=-3.6, depth=8.6, length=13.5, w=2.6, style='mag', slant=1.0)
    # Long free-floating rail, the heavy barrel, a muzzle brake.
    quad_rail(g, -12.0, -40.0, size=5.6)
    barrel(g, -40.0, -57.0, r=0.95)
    g.cyl(-57.0, -61.0, BORE, 1.15, 'slots')
    P.bipod(g, -36.0, BORE - 3.0, 18.0)
    # Adjustable stock (cheek piece and length).
    g.cyl(8.4, 20.0, BORE, 1.6, 'black', aim=False)
    g.profile(((12.0, BORE + 2.6), (31.0, BORE + 2.6)), ((12.0, BORE - 2.0), (31.0, -5.6)), 3.6, 'poly', aim=False)
    g.box((-1.7, BORE + 2.6, 18.0), (1.7, BORE + 3.6, 29.0), 'poly', aim=False)
    g.box((-2.0, -5.8, 31.0), (2.0, BORE + 3.8, 32.4), 'rubber', aim=False)
    P.scope(g, -14.0, 9.0, RAIL + 0.9, 13.2, r=1.5, obj=2.3)
    return g


def make_m249():
    g = Gun('m249', 9.8, bore=4.4, R=5, seed=249)
    B = 4.4
    # Receiver: a long box with the feed tray cover hinged at the back, the ejection port under it.
    g.bar(-16.0, 10.0, 0.0, 7.4, 3.8, 'black')
    g.bar(-14.0, 6.0, 7.4, 8.6, 3.6, 'black')
    g.bar(-13.0, 4.0, 8.6, 9.2, 2.4, 'black')
    P.rail(g, -12.0, 4.0, 9.2)
    g.bar(6.0, 8.0, 7.4, 8.8, 4.0, 'gunmetal')
    # Belt in the feed tray (left), the 100-round soft pouch underneath.
    for i in range(6):
        z = -12.0 + i * 1.1
        g.box((-3.6, 6.2, z - 0.35), (-1.9, 6.9, z + 0.35), 'brass_case')
    g.box((-4.4, -8.0, -14.5), (1.2, 0.0, -4.0), 'od', edges=True)
    g.box((-4.5, -8.4, -14.7), (1.3, -7.6, -3.8), 'od_metal')
    g.bar(-12.0, -6.5, -2.0, 0.0, 6.0, 'od', x=-1.6)
    # Trigger group and grip.
    g.bar(-2.0, 2.0, -2.8, 0.0, 3.0, 'black', aim=False)
    g.bar(-2.0, 2.0, -3.2, -2.8, 1.0, 'black', aim=False)
    P.grip(g, (3.4, 0.0), (6.4, -9.6), 3.4, 2.8, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    # Barrel with the carry handle, gas cylinder under it, the bipod.
    g.cyl(-16.0, -58.0, B, 0.95, 'black')
    g.cyl(-16.0, -42.0, B - 2.2, 0.9, 'black')
    g.bar(-16.0, -30.0, -1.2, B - 1.2, 4.2, 'poly')
    g.bar(-24.0, -27.0, B + 1.0, B + 2.6, 0.8, 'black')
    g.bar(-21.0, -30.0, B + 2.6, B + 3.4, 1.0, 'poly')
    g.bar(-42.0, -44.0, B - 2.4, B + 1.2, 2.4, 'black')
    P.iron_post(g, -50.0, B + 0.9, 9.8, ears=False)
    g.bar(-49.0, -51.0, B + 0.6, B + 1.1, 1.4, 'black')
    g.cyl(-58.0, -62.0, B, 1.0, 'slots')
    P.bipod(g, -43.0, B - 2.6, 22.0)
    # Tube-frame stock (Para) with the butt plate.
    g.cyl(10.0, 26.0, 4.6, 1.3, 'black', aim=False)
    g.slab((10.0, 1.0), (26.0, -2.6), 1.2, 1.4, 'black', aim=False)
    g.box((-2.0, -5.4, 26.0), (2.0, 6.6, 27.6), 'poly', aim=False)
    return g


def make_m240b():
    g = Gun('m240b', 11.8, bore=5.2, R=5, seed=240)
    B = 5.2
    # Big rectangular receiver, the feed cover with its rail, the cocking handle on the right.
    g.bar(-20.0, 16.0, 0.0, 8.8, 4.4, 'black')
    g.bar(-17.0, 10.0, 8.8, 10.0, 4.2, 'black')
    P.rail(g, -15.0, 8.0, 10.0)
    g.bar(-14.0, -10.0, 4.4, 5.6, 1.6, 'gunmetal', x=2.8)
    for i in range(7):
        z = -16.0 + i * 1.2
        g.box((-4.4, 7.0, z - 0.35), (-2.2, 7.8, z + 0.35), 'brass_case')
    # Ammunition box under the feed on the left.
    g.box((-6.0, -9.0, -18.0), (0.2, 0.0, -6.0), 'od_metal')
    g.box((-6.1, -1.0, -18.2), (0.3, 0.3, -5.8), 'od_metal')
    # Trigger group, grip, the stock.
    g.bar(0.0, 5.0, -3.0, 0.0, 3.0, 'black', aim=False)
    g.bar(0.0, 5.0, -3.4, -3.0, 1.0, 'black', aim=False)
    P.grip(g, (5.5, 0.0), (8.6, -10.0), 3.4, 2.9, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    g.profile(((16.0, 8.0), (38.0, 7.0)), ((16.0, 1.0), (38.0, -6.0)), 4.0, 'poly', aim=False)
    g.box((-2.2, -6.2, 38.0), (2.2, 7.2, 39.4), 'rubber', aim=False)
    # The barrel with the heat shield on top, the carry handle, gas regulator, flash hider, bipod.
    g.cyl(-20.0, -78.0, B, 1.1, 'black')
    g.bar(-22.0, -48.0, B + 1.1, B + 1.6, 2.8, 'slots')
    g.bar(-30.0, -33.0, B + 1.6, B + 4.2, 0.8, 'black')
    g.bar(-27.0, -36.0, B + 4.2, B + 5.0, 1.0, 'black')
    g.cyl(-20.0, -60.0, B - 2.6, 1.0, 'black')
    g.bar(-58.0, -61.0, B - 3.2, B + 1.2, 2.8, 'black')
    P.iron_post(g, -74.0, B + 1.0, 11.8, ears=False)
    g.bar(-73.0, -75.0, B + 0.7, B + 1.2, 1.6, 'black')
    g.cyl(-78.0, -84.0, B, 1.25, 'black')
    P.bipod(g, -58.0, B - 3.4, 30.0)
    return g


ALL = [make_m4a1, make_m16a4, make_hk416, make_scarh, make_m110, make_m249, make_m240b]
