"""The other arms: PKM and PKP machine guns, SVD, SV-98, VSS and AS Val, the Fort-221 bullpup, pistols (PM,
Fort-12, Glock 17), the MP5, the Barrett M82, the AWM and the Remington 870."""
import math

from gunkit import Gun
import parts as P


# ----------------------------------------------------------------------------------------------------------------
# Machine guns

def pk_receiver(g, B, z_front=-14.0, z_back=14.0):
    """The PK's stamped receiver: the feed cover hinged at the front with the belt in its tray, the charging handle
    on the right, the 100-round box on the right side."""
    g.bar(z_front, z_back, 0.0, 6.6, 3.4, 'black')
    g.bar(z_front + 1.0, z_back - 3.0, 6.6, 7.6, 3.2, 'black')
    g.bar(z_front + 2.0, z_back - 4.0, 7.6, 8.0, 2.4, 'black')
    for z in (z_front + 4.0, z_front + 7.0, z_front + 10.0):
        g.bar(z - 0.3, z + 0.3, 7.6, 8.1, 2.6, 'black', aim=False)
    # Charging handle (right), rivets, the trunnion.
    g.box((1.7, 3.0, 2.0), (3.4, 4.0, 3.2), 'gunmetal')
    for z in (z_front + 1.0, z_front + 2.5, z_back - 2.0):
        P.rivet(g, 1.7, 1.2, z)
        P.rivet(g, -1.7, 1.2, z, side=-1)
    # Belt coming out of the box into the feed tray on the right.
    for i in range(5):
        z = z_front + 2.5 + i * 1.15
        g.box((1.7, 4.6 - i * 0.25, z - 0.35), (3.4, 5.3 - i * 0.25, z + 0.35), 'brass_case')
    g.box((1.8, -9.0, z_front - 1.5), (6.8, 2.8, z_front + 9.0), 'rgreen')
    g.box((1.7, 2.6, z_front - 1.7), (6.9, 3.2, z_front + 9.2), 'od_metal')
    g.bar(z_front + 2.0, z_front + 6.0, 3.2, 3.8, 0.6, 'steel', x=4.3)
    # Trigger group and the grip.
    g.bar(-2.0, 1.0, -2.6, 0.0, 2.8, 'black', aim=False)
    g.bar(-2.0, 1.0, -3.0, -2.6, 1.0, 'black', aim=False)
    P.grip(g, (2.2, 0.2), (5.0, -10.0), 3.4, 2.8, 'wood', cap=None, sides={'east': 'grip_wood', 'west': 'grip_wood'})


def make_pkm():
    B = 4.4
    g = Gun('pkm', 9.6, bore=B, R=5, seed=62)
    pk_receiver(g, B)
    # Rear sight on the receiver, the fluted barrel with its carry handle, gas cylinder, front sight, flash hider.
    g.bar(-12.0, -9.0, 8.0, 8.8, 2.0, 'black')
    P.notch(g, -9.5, 8.8, 9.8, w=1.6, gap=0.4)
    g.cyl(-14.0, -62.0, B, 1.0, 'black')
    g.cyl(-14.0, -40.0, B - 2.4, 0.9, 'black')
    g.bar(-38.0, -42.0, B - 3.2, B + 1.0, 2.6, 'black')
    g.bar(-18.0, -20.0, B + 1.0, B + 3.6, 0.8, 'black')
    g.bar(-16.0, -24.0, B + 3.6, B + 4.4, 1.2, 'wood')
    g.bar(-58.0, -60.0, B + 0.8, B + 3.4, 1.6, 'black')
    P.iron_post(g, -59.0, B + 3.2, 9.6, ear_gap=0.9)
    g.cyl(-62.0, -67.0, B, 1.15, 'slots')
    P.bipod(g, -39.0, B - 3.2, 26.0)
    # Skeleton (thumb-hole) wooden stock.
    g.bar(14.0, 16.0, 0.0, 6.2, 3.2, 'black', aim=False)
    g.slab((16.0, 5.4), (38.0, 4.6), 1.6, 3.2, 'wood', aim=False)
    g.slab((16.0, 0.8), (38.0, -7.6), 2.0, 3.2, 'wood', aim=False)
    g.slab((30.0, 4.6), (31.0, -5.0), 2.2, 3.2, 'wood', aim=False)
    g.box((-1.8, -8.6, 38.0), (1.8, 5.6, 39.0), 'black', aim=False)
    return g


def make_pkp():
    B = 4.4
    g = Gun('pkp', 10.6, bore=B, R=5, seed=63)
    pk_receiver(g, B)
    P.rail(g, -12.0, -4.0, 8.0)
    # Heavy barrel inside the forced-air cooling jacket with the carry handle at its front, bipod at the muzzle.
    g.cyl(-14.0, -60.0, B, 1.55, 'slots')
    g.cyl(-14.0, -16.0, B, 1.7, 'black')
    g.cyl(-58.0, -60.0, B, 1.7, 'black')
    g.bar(-50.0, -56.0, B + 1.6, B + 2.4, 1.0, 'black')
    g.bar(-48.0, -58.0, B + 2.4, B + 3.4, 1.4, 'poly')
    g.bar(-55.0, -57.0, B + 1.6, B + 4.6, 1.6, 'black')
    P.iron_post(g, -56.0, B + 4.4, 10.6, ear_gap=0.9)
    g.cyl(-60.0, -66.0, B, 1.1, 'black')
    P.bipod(g, -58.0, B - 1.8, 24.0)
    g.bar(-12.0, -9.0, 8.9, 9.4, 2.0, 'black')
    P.notch(g, -9.5, 9.4, 10.8, w=1.6, gap=0.4)
    # Polymer skeleton stock.
    g.bar(14.0, 16.0, 0.0, 6.2, 3.2, 'black', aim=False)
    g.slab((16.0, 5.4), (38.0, 4.6), 1.6, 3.2, 'poly', aim=False)
    g.slab((16.0, 0.8), (38.0, -7.6), 2.0, 3.2, 'poly', aim=False)
    g.slab((30.0, 4.6), (31.0, -5.0), 2.2, 3.2, 'poly', aim=False)
    g.box((-1.8, -8.6, 38.0), (1.8, 5.6, 39.0), 'rubber', aim=False)
    return g


# ----------------------------------------------------------------------------------------------------------------
# Marksman and sniper rifles

def svd_body(g, B, metal='black', wood='wood'):
    """The SVD: a long receiver, the long slotted handguard, the thumb-hole stock with its cheek rest."""
    g.bar(-14.0, 12.0, 0.0, 5.6, 3.0, metal)
    g.bar(-10.0, 12.0, 5.6, 6.3, 2.6, metal)
    g.bar(-16.0, -14.0, 0.4, 5.2, 3.2, metal)
    P.plate(g, -8.0, 2.0, 3.4, 5.4, 1.5, 'groove')
    g.box((1.6, 3.6, -6.5), (3.0, 4.6, -5.4), 'gunmetal')
    g.bar(-6.0, 4.0, 3.9, 4.5, 0.2, metal, x=1.62)
    # Handguard (two halves with vent slots), the gas tube over the barrel.
    g.bar(-17.0, -40.0, 0.6, 5.2, 4.0, wood)
    g.bar(-17.0, -40.0, 5.2, 6.4, 2.6, wood)
    for z in (-21.0, -26.0, -31.0, -36.0):
        for x, side in ((2.0, 1), (-2.0, -1)):
            P.plate(g, z, z - 2.6, 2.2, 3.8, x, 'groove', side=side)
    g.bar(-40.0, -41.0, 0.4, 5.6, 4.3, metal)
    g.bar(-41.0, -45.0, B - 1.0, 6.2, 2.0, metal)
    g.cyl(-14.0, -76.0, B, 0.85, metal)
    # Front sight and the long slotted flash hider.
    g.bar(-73.0, -75.0, B + 0.8, B + 2.6, 1.6, metal)
    P.iron_post(g, -74.0, B + 2.4, 7.0, ear_gap=0.8)
    g.cyl(-76.0, -84.0, B, 1.0, 'slots')
    # Magazine (10 rounds), trigger.
    P.straight_mag(g, -6.2, 6.0, 10.5, 2.6, 'mag', slant=1.6)
    P.trigger(g, z_front=-4.0, z_back=1.0, trig_z=-1.6, style=metal)
    # Thumb-hole stock: the comb with the cheek rest, the grip in the frame, the butt.
    g.bar(12.0, 13.4, 0.4, 5.8, 2.9, metal, aim=False)
    g.slab((13.0, 4.8), (36.0, 3.8), 2.0, 3.2, wood, aim=False)
    g.box((-1.5, 5.6, 18.0), (1.5, 7.2, 30.0), wood, aim=False)
    g.slab((13.0, -0.2), (17.0, -9.5), 2.8, 3.0, wood, aim=False)
    g.slab((17.0, -9.5), (36.0, -8.6), 1.6, 3.2, wood, aim=False)
    g.slab((31.0, 4.0), (32.0, -8.6), 4.0, 3.2, wood, aim=False)
    g.box((-1.8, -10.2, 36.0), (1.8, 5.0, 36.9), 'black', aim=False)


def make_svd():
    g = Gun('svd', 10.4, bore=3.6, R=5, seed=73)
    svd_body(g, 3.6)
    P.pso1(g, -8.0, 6.0, 5.6, 10.4)
    return g


def make_sv98():
    B = 3.8
    g = Gun('sv98', 11.6, bore=B, R=5, seed=98)
    # Receiver with the bolt handle on the right, a rail on top.
    g.bar(-14.0, 8.0, 0.4, 6.0, 3.2, 'black')
    P.rail(g, -14.0, 7.0, 6.0)
    g.cyl(4.0, 9.0, 5.0, 0.8, 'gunmetal')
    g.slab((3.0, 5.0), (3.6, 2.6), 0.6, 0.6, 'gunmetal', x=2.2)
    g.cyl_x(1.6, 3.4, 2.4, 3.6, 0.7, 'black')
    # Heavy barrel with a suppressor-like muzzle brake.
    g.cyl(-14.0, -62.0, B, 1.05, 'black')
    g.cyl(-62.0, -71.0, B, 1.5, 'black')
    P.straight_mag(g, -4.0, 7.0, 9.0, 2.8, 'mag', slant=0.8)
    P.trigger(g, z_front=-3.0, z_back=1.0, trig_z=-1.4)
    # Adjustable thumb-hole stock (birch plywood, painted dark green) and the forend.
    wood = 'ukr_green'
    g.bar(-14.0, -40.0, -0.6, 3.6, 4.2, wood)
    g.slab((-14.0, -1.2), (2.0, -2.6), 2.4, 3.6, wood)
    g.slab((8.0, 5.0), (34.0, 4.6), 2.2, 3.4, wood, aim=False)
    g.box((-1.5, 6.1, 14.0), (1.5, 7.4, 28.0), wood, aim=False)
    g.slab((2.0, -0.4), (6.0, -9.8), 3.0, 3.2, wood, aim=False)
    g.slab((6.0, -9.8), (34.0, -8.0), 1.8, 3.4, wood, aim=False)
    g.slab((27.0, 4.8), (28.0, -8.2), 6.0, 3.4, wood, aim=False)
    g.box((-1.9, -10.0, 34.0), (1.9, 6.4, 35.4), 'rubber', aim=False)
    P.bipod(g, -36.0, -0.6, 18.0)
    P.scope(g, -16.0, 6.0, 6.9, 11.6, r=1.5, obj=2.4)
    return g


def make_vss():
    B = 3.6
    g = Gun('vss', 10.4, bore=B, R=6, seed=93)
    g.bar(-16.0, 8.0, 0.0, 5.4, 3.0, 'black')
    g.bar(-10.5, 8.0, 5.4, 6.2, 2.6, 'black')
    P.plate(g, -9.6, 0.4, 3.4, 5.3, 1.5, 'groove')
    g.box((1.9, 3.7, -10.4), (3.2, 4.6, -9.6), 'gunmetal')
    g.bar(-9.6, 1.2, 4.15, 4.75, 0.18, 'black', x=1.62)
    P.trigger(g, style='black')
    P.straight_mag(g, -8.6, 6.4, 10.0, 2.6, 'mag', slant=1.2)
    # The integral suppressor is the whole front, with a wooden forend under its back half.
    g.cyl(-16.0, -45.0, B, 1.95, 'black')
    g.cyl(-16.0, -17.5, B, 2.05, 'black')
    g.bar(-17.0, -29.0, 0.6, B, 4.0, 'wood')
    g.bar(-41.0, -43.0, B + 1.8, B + 3.0, 1.2, 'black')
    P.iron_post(g, -42.0, B + 2.8, 7.4, ear_gap=0.7)
    # Wooden skeleton stock (thumb hole).
    g.slab((8.0, 5.0), (30.0, 4.2), 1.8, 3.0, 'wood', aim=False)
    g.slab((8.0, 0.0), (12.0, -8.6), 2.8, 3.0, 'wood', aim=False)
    g.slab((12.0, -8.6), (30.0, -7.6), 1.6, 3.0, 'wood', aim=False)
    g.slab((25.0, 4.4), (26.0, -7.8), 4.0, 3.0, 'wood', aim=False)
    g.box((-1.7, -8.8, 30.0), (1.7, 5.0, 30.8), 'black', aim=False)
    P.pso1(g, -10.0, 4.0, 5.4, 10.4)
    return g


def make_asval():
    B = 3.6
    g = Gun('asval', 7.6, bore=B, R=6, seed=94)
    g.bar(-16.0, 8.0, 0.0, 5.4, 3.0, 'black')
    g.bar(-10.5, 8.0, 5.4, 6.2, 2.6, 'black')
    P.plate(g, -9.6, 0.4, 3.4, 5.3, 1.5, 'groove')
    g.box((1.9, 3.7, -10.4), (3.2, 4.6, -9.6), 'gunmetal')
    P.trigger(g, style='black')
    P.grip(g, (1.7, 0.2), (5.2, -10.2), 3.2, 2.8, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    P.straight_mag(g, -8.6, 6.4, 14.0, 2.6, 'mag', slant=1.8)
    g.cyl(-16.0, -45.0, B, 1.95, 'black')
    g.cyl(-16.0, -17.5, B, 2.05, 'black')
    g.bar(-17.0, -29.0, 0.6, B, 4.0, 'poly_grooves')
    g.bar(-12.0, -8.0, 5.8, 6.6, 2.2, 'black')
    P.notch(g, -8.5, 6.6, 7.8, w=1.6, gap=0.4)
    g.bar(-41.0, -43.0, B + 1.8, B + 3.0, 1.2, 'black')
    P.iron_post(g, -42.0, B + 2.8, 7.6, ear_gap=0.7)
    # Side-folding skeleton stock (unfolded).
    g.bar(8.0, 9.6, 0.5, 5.0, 3.2, 'black', aim=False)
    P.skeleton_stock(g, 9.6, 29.0, 4.8, 4.2, 0.9, -5.2, t=1.0, w=1.2, style='black', plate='black')
    return g


def make_m82():
    B = 4.0
    g = Gun('m82', 13.0, bore=B, R=4, seed=82)
    # Upper receiver (long, with the barrel jacket's slots), the lower with the grip and the magazine in front of it.
    g.bar(-22.0, 22.0, 0.0, 8.4, 5.0, 'park')
    g.bar(-48.0, -22.0, 0.8, 7.6, 4.6, 'slots')
    P.rail(g, -18.0, 16.0, 8.4)
    g.bar(-20.0, -10.0, -3.0, 0.0, 4.6, 'park')
    P.straight_mag(g, -10.4, 7.8, 9.0, 4.0, 'mag', slant=0.3)
    g.bar(-2.0, 4.0, -3.0, 0.0, 3.0, 'park', aim=False)
    g.bar(-2.0, 4.0, -3.4, -3.0, 1.0, 'park', aim=False)
    P.grip(g, (4.8, 0.0), (7.8, -10.0), 3.4, 2.9, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    # Carry handle over the receiver, the charging handle on the right.
    g.bar(-10.0, -8.0, 8.4, 11.0, 1.2, 'park')
    g.bar(-2.0, 0.0, 8.4, 11.0, 1.2, 'park')
    g.bar(-10.0, 0.0, 11.0, 11.8, 1.4, 'park')
    g.box((2.5, 4.6, -6.0), (4.0, 6.0, -4.0), 'gunmetal')
    # Barrel and the big double-chamber muzzle brake, the bipod.
    g.cyl(-48.0, -86.0, B, 1.3, 'park')
    P.big_brake(g, -86.0, B, r=1.6, length=10.0)
    P.bipod(g, -46.0, 0.6, 30.0)
    # Butt: a long block with the pad and the monopod, the cheek rest.
    g.profile(((22.0, 8.4), (44.0, 8.4)), ((22.0, 0.0), (44.0, -4.0)), 4.6, 'park', aim=False)
    g.box((-2.0, 8.4, 26.0), (2.0, 9.8, 40.0), 'rubber', aim=False)
    g.box((-2.6, -4.6, 44.0), (2.6, 9.0, 46.6), 'rubber', aim=False)
    P.scope(g, -20.0, 12.0, 9.3, 13.0, r=1.6, obj=2.8)
    return g


def make_awm():
    B = 4.0
    g = Gun('awm', 11.8, bore=B, R=5, seed=338)
    od = 'od'
    # Aluminium chassis inside the green stock: the action with the bolt, a rail on top.
    g.bar(-12.0, 10.0, 0.6, 6.4, 3.0, 'park')
    P.rail(g, -12.0, 9.0, 6.4)
    g.cyl(6.0, 11.0, 5.2, 0.85, 'steel')
    g.slab((5.0, 5.2), (5.8, 2.8), 0.6, 0.6, 'steel', x=2.2)
    g.cyl_x(1.6, 3.6, 2.6, 5.4, 0.8, 'black')
    # Fluted barrel and its muzzle brake.
    g.cyl(-12.0, -66.0, B, 1.05, 'park')
    for z in range(-60, -16, 8):
        P.plate(g, z, z + 5.0, B - 0.25, B + 0.25, 1.05, 'groove')
    P.big_brake(g, -66.0, B, r=1.3, length=7.0)
    P.straight_mag(g, -2.6, 7.4, 7.0, 3.0, 'mag', slant=0.0)
    P.trigger(g, z_front=-1.0, z_back=3.2, trig_z=0.6)
    # The thumb-hole stock in two halves (green): forend, grip, the adjustable butt.
    g.bar(-12.0, -38.0, -1.4, 3.8, 4.4, od)
    g.slab((-12.0, -1.8), (-1.0, -3.0), 1.6, 4.0, od)
    g.slab((10.0, 5.6), (34.0, 5.6), 2.0, 3.6, od, aim=False)
    g.box((-1.6, 6.6, 16.0), (1.6, 7.8, 30.0), od, aim=False)
    g.slab((3.4, -2.6), (7.2, -11.0), 3.0, 3.4, od, aim=False)
    g.slab((7.2, -11.0), (34.0, -8.0), 1.8, 3.6, od, aim=False)
    g.slab((27.0, 5.6), (28.0, -8.4), 6.0, 3.6, od, aim=False)
    g.box((-2.0, -10.2, 34.0), (2.0, 7.0, 35.6), 'rubber', aim=False)
    g.cyl_y(0, 30.0, -12.0, -9.0, 0.6, 'black', aim=False)
    P.bipod(g, -36.0, -1.4, 20.0)
    P.scope(g, -16.0, 8.0, 7.3, 11.8, r=1.5, obj=2.7)
    return g


# ----------------------------------------------------------------------------------------------------------------
# Bullpup, submachine gun, shotgun

def make_fort221():
    """Fort-221 (Ukrainian Tavor): the magazine behind the grip, the action inside the stock body."""
    B = 4.6
    g = Gun('fort221', 10.8, bore=B, R=6, seed=221)
    # The long polymer body: from the handguard to the butt, the grip with the full trigger guard.
    g.bar(-22.0, 34.0, -0.2, 6.4, 4.4, 'ukr_green')
    g.bar(-22.0, -8.0, 6.4, 7.0, 3.6, 'ukr_green')
    P.grip(g, (1.4, 0.0), (3.6, -9.2), 3.4, 3.0, 'ukr_green', cap=None, sides={'east': 'od_grip', 'west': 'od_grip'})
    # The big trigger guard that closes round the whole hand.
    g.slab((-8.0, -0.4), (-2.6, -9.4), 1.0, 2.4, 'ukr_green', aim=False)
    g.slab((-2.9, -9.6), (2.4, -9.6), 0.9, 2.4, 'ukr_green', aim=False)
    g.bar(-1.4, -0.8, -1.4, 0.0, 0.45, 'black', aim=False)
    # Magazine well behind the grip, the magazine, the ejection port on the right, the charging handle on the left.
    P.arc_mag(g, (12.0, 0.0), 17.5, 2.0, 10.0, 6.2, 2.3, 'mag', n=6, depth1=6.4)
    P.plate(g, 12.0, 20.0, 3.0, 5.4, 2.2, 'groove')
    g.box((-3.2, 4.4, -12.0), (-2.2, 5.4, -10.4), 'black')
    # Upper rail, the barrel, the flash hider.
    g.bar(-24.0, 10.0, 7.0, 7.6, 2.4, 'black')
    P.rail(g, -24.0, 10.0, 7.6)
    g.cyl(-22.0, -34.0, B, 0.8, 'black')
    P.birdcage(g, -34.0, B)
    g.box((-2.3, -0.4, 34.0), (2.3, 6.6, 35.4), 'rubber', aim=False)
    P.holo(g, -10.0, 8.5, 10.8)
    return g


def make_mp5():
    B = 3.8
    g = Gun('mp5', 7.6, bore=B, R=7, seed=5)
    # Round receiver tube with the cocking tube over the barrel, the drum rear sight.
    g.cyl(-14.0, 10.0, B + 0.6, 1.9, 'black')
    g.bar(-14.0, 10.0, 0.0, B, 2.6, 'black')
    g.cyl(-22.0, -10.0, B + 1.9, 0.6, 'black')
    g.cyl_x(-1.6, 1.6, 7.0, 6.0, 1.1, 'black')
    g.bar(5.0, 7.0, B + 2.2, 6.0, 1.6, 'black')
    g.bar(-19.6, -18.0, B + 2.2, 6.6, 1.4, 'black')
    P.iron_post(g, -18.8, 6.6, 7.6, ears=True, ear_gap=0.6)
    # Cocking handle on the left, the curved magazine, the slim handguard, the barrel and its lugs.
    g.box((-2.8, B + 1.4, -18.4), (-1.0, B + 2.2, -17.4), 'black')
    P.arc_mag(g, (-7.6, 0.0), 17.0, 2.0, 24.0, 3.2, 2.3, 'mag_steel', n=6, depth1=3.4)
    g.bar(-10.0, -6.0, -1.0, 0.0, 2.6, 'black')
    g.cyl(-11.0, -21.0, B - 0.5, 2.1, 'poly_grooves')
    g.cyl(-21.0, -25.0, B, 0.7, 'black')
    g.cyl(-23.0, -24.0, B, 0.95, 'black')
    # Trigger group (the "navy" lower) and grip.
    g.bar(-4.0, 2.0, -2.6, 0.0, 2.6, 'poly', aim=False)
    P.grip(g, (1.4, 0.0), (4.2, -8.8), 3.2, 2.7, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    g.bar(-2.6, -1.9, -1.2, 0.0, 0.45, 'black', aim=False)
    # Retractable stock (extended): two rods and the butt pad.
    for x in (-1.2, 1.2):
        g.bar(10.0, 26.0, 3.8, 4.4, 0.4, 'black', x=x, aim=False)
        g.bar(10.0, 26.0, 0.6, 1.2, 0.4, 'black', x=x, aim=False)
    g.box((-1.8, -2.6, 26.0), (1.8, 6.2, 27.4), 'rubber', aim=False)
    return g


def make_m870():
    B = 4.2
    g = Gun('m870', 7.4, bore=B, R=5, seed=870)
    # Receiver, the barrel with its rib and bead, the magazine tube under it, the pump.
    g.bar(-16.0, 6.0, 0.0, 6.0, 3.0, 'black')
    P.plate(g, -12.0, -4.0, 3.0, 5.4, 1.5, 'groove')
    P.trigger(g, z_front=-4.0, z_back=1.4, trig_z=-1.0)
    g.cyl(-16.0, -66.0, B, 1.15, 'black')
    g.bar(-16.0, -66.0, B + 1.15, B + 1.5, 0.6, 'black')
    g.box((-0.25, B + 1.5, -65.6), (0.25, B + 2.0, -65.1), 'steel')
    g.cyl(-16.0, -60.0, B - 2.6, 1.1, 'black')
    g.cyl(-60.0, -62.0, B - 2.6, 1.25, 'black')
    g.bar(-58.0, -60.0, B - 2.6, B, 1.8, 'black')
    g.cyl(-22.0, -40.0, B - 2.6, 1.9, 'poly_grooves')
    # Polymer stock with the pistol-grip shape of the wrist.
    g.slab((6.0, 4.2), (36.0, 3.6), 2.2, 3.4, 'poly', aim=False)
    g.slab((6.0, 1.0), (12.0, -5.0), 3.0, 3.2, 'poly', aim=False)
    g.slab((12.0, -5.0), (36.0, -7.6), 2.0, 3.4, 'poly', aim=False)
    g.profile(((14.0, 3.6), (36.0, 3.4)), ((14.0, -4.4), (36.0, -6.6)), 3.4, 'poly', aim=False)
    g.box((-1.8, -8.0, 36.0), (1.8, 4.8, 37.6), 'rubber', aim=False)
    return g


# ----------------------------------------------------------------------------------------------------------------
# Pistols. The slide's bottom is y = 0.6, the bore 2.2 up, the sights at about 3.8; the grip hangs from y = 0.

def pistol(g, z_muzzle, z_back, slide, frame, grip_style, grip_sides, grip_len=9.0, slant=2.6, h=3.2, w=2.4, hammer=True):
    g.bar(z_muzzle, z_back, 0.6, h, w, slide)
    # Serrations at the back of the slide, the ejection port.
    for i in range(5):
        z = z_back - 0.8 - i * 0.5
        P.plate(g, z, z - 0.25, 1.0, h - 0.4, w / 2, 'groove')
        P.plate(g, z, z - 0.25, 1.0, h - 0.4, -w / 2, 'groove', side=-1)
    P.plate(g, z_back - 7.0, z_back - 4.0, 1.8, h - 0.3, w / 2, 'groove')
    # Sights.
    g.bar(z_muzzle + 0.6, z_muzzle + 1.2, h, h + 0.55, 0.4, slide)
    g.bar(z_back - 1.2, z_back - 0.4, h, h + 0.6, 1.4, slide)
    # Frame, trigger guard, trigger.
    g.bar(z_muzzle + 1.5, z_back - 0.2, -0.2, 0.6, w - 0.2, frame)
    g.bar(-5.6, -5.0, -2.6, -0.2, 0.8, frame, aim=False)
    g.bar(-5.6, 0.0, -2.6, -2.2, 0.8, frame, aim=False)
    g.bar(-3.0, -2.5, -1.4, -0.2, 0.45, 'gunmetal', aim=False)
    # Grip.
    P.grip(g, (1.6, 0.0), (1.6 + slant, -grip_len), 3.2, w + 0.3, grip_style, cap=frame, cap_t=0.4,
           sides={'east': grip_sides, 'west': grip_sides}, aim=False)
    if hammer:
        g.slab((z_back - 0.2, 1.4), (z_back + 0.7, 2.6), 0.6, 0.6, 'gunmetal', aim=False)


def make_pm():
    g = Gun('pm', 3.75, bore=2.0, R=10, seed=9, long_gun=False)
    pistol(g, -11.0, 4.6, 'blued', 'blued', 'plum', 'grip_wood', grip_len=8.6, slant=2.2, h=3.0, w=2.3)
    g.box((1.15, -4.6, 2.2), (1.35, -3.6, 3.2), 'plum_dark', faces=('east',), aim=False)
    return g


def make_fort12():
    g = Gun('fort12', 3.9, bore=2.1, R=10, seed=12012, long_gun=False)
    pistol(g, -12.4, 5.0, 'black', 'black', 'poly', 'grip', grip_len=9.2, slant=2.4, h=3.2, w=2.5)
    return g


def make_glock17():
    g = Gun('glock17', 4.0, bore=2.2, R=10, seed=17, long_gun=False)
    pistol(g, -13.4, 5.6, 'park', 'poly', 'poly', 'grip', grip_len=10.0, slant=3.0, h=3.3, w=2.6, hammer=False)
    # Accessory rail under the frame, the trigger safety.
    g.bar(-12.0, -6.0, -1.0, -0.2, 2.0, 'poly')
    return g


ALL = [make_pkm, make_pkp, make_svd, make_sv98, make_vss, make_asval, make_m82, make_awm, make_fort221, make_mp5,
       make_m870, make_pm, make_fort12, make_glock17]
