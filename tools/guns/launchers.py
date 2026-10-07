"""Launchers: RPG-7, RPG-22, AT4, Carl Gustaf M4, NLAW, Javelin and the M32 grenade launcher.
Parts tagged 'loaded' show only with a round in (the RPG-7's grenade sticking out of the muzzle)."""
from gunkit import Gun
import parts as P


def tube(g, z0, z1, y, r, style, x=0.0, **kw):
    g.cyl(z0, z1, y, r, style, x=x, **kw)


def pg7v(g, z_muzzle, y, tags=('loaded',)):
    """The PG-7V grenade in the RPG-7's muzzle: the long cone, the 85 mm warhead, the fuze tip."""
    tube(g, z_muzzle + 2.0, z_muzzle - 6.0, y, 2.4, 'warhead_green', tags=tags)
    tube(g, z_muzzle - 6.0, z_muzzle - 16.0, y, 4.25, 'warhead_green', tags=tags)
    tube(g, z_muzzle - 16.0, z_muzzle - 21.0, y, 3.2, 'warhead_green', tags=tags)
    tube(g, z_muzzle - 21.0, z_muzzle - 25.0, y, 2.0, 'warhead_green', tags=tags)
    tube(g, z_muzzle - 25.0, z_muzzle - 29.0, y, 1.0, 'warhead_green', tags=tags)
    tube(g, z_muzzle - 29.0, z_muzzle - 30.5, y, 0.5, 'steel', tags=tags)


def make_rpg7():
    Y = 5.2
    g = Gun('rpg7', Y + 3.4, bore=Y, R=4, seed=7)
    # The tube: front, the wooden heat guards in the middle, the back and the flared blast cone.
    tube(g, -38.0, 44.0, Y, 2.3, 'black')
    tube(g, -38.0, -36.5, Y, 2.6, 'black')
    tube(g, -12.0, 14.0, Y, 3.1, 'wood')
    tube(g, -12.5, -12.0, Y, 3.3, 'black')
    tube(g, 14.0, 14.5, Y, 3.3, 'black')
    tube(g, 44.0, 47.0, Y, 3.0, 'black')
    tube(g, 47.0, 51.0, Y, 3.9, 'black')
    tube(g, 51.0, 56.0, Y, 4.6, 'black')
    g.box((-4.0, Y - 4.0, 56.0), (4.0, Y + 4.0, 56.1), 'rubber', faces=('south',))
    # Grip with the trigger, a second grip ahead of it, the iron sights on top, the PGO-7 sight on the left.
    P.grip(g, (1.7, 2.9), (3.6, -7.4), 3.2, 2.8, 'wood', cap=None, sides={'east': 'grip_wood', 'west': 'grip_wood'})
    g.bar(-1.5, 4.5, 2.9, Y - 2.1, 2.6, 'black')
    P.trigger(g, z_front=-3.4, z_back=0.4, trig_z=-1.4, depth=2.0)
    P.grip(g, (-21.0, 2.9), (-19.6, -6.0), 3.0, 2.6, 'wood', cap=None, sides={'east': 'grip_wood', 'west': 'grip_wood'})
    g.bar(-23.0, -18.5, 2.9, Y - 2.1, 2.4, 'black')
    g.bar(-31.0, -29.0, Y + 2.2, Y + 3.6, 1.0, 'black')
    g.bar(-12.0, -9.0, Y + 2.2, Y + 3.0, 1.6, 'black')
    P.notch(g, -9.6, Y + 3.0, Y + 3.6, w=1.4, gap=0.4)
    g.bar(-12.0, 4.0, Y - 1.0, Y + 1.2, 1.4, 'black', x=-3.6)
    tube(g, -12.0, 2.0, Y + 1.0, 1.6, 'black', x=-5.6)
    tube(g, 2.0, 6.0, Y + 1.0, 1.9, 'rubber', x=-5.6)
    g.box((-6.8, Y - 0.2, -12.05), (-4.4, Y + 2.2, -12.0), 'glass', faces=('north',))
    pg7v(g, -38.0, Y)
    return g


def make_rpg22():
    Y = 3.8
    g = Gun('rpg22', Y + 4.8, bore=Y, R=4, seed=22)
    g.sight_x = -3.0
    # Telescopic tube (pulled out): the slimmer front section, the rear one, end caps, yellow band, sling.
    tube(g, -42.0, -12.0, Y, 3.5, 'tube_green')
    tube(g, -12.0, 40.0, Y, 3.8, 'tube_green')
    tube(g, -42.0, -40.5, Y, 3.7, 'black')
    tube(g, 38.5, 40.0, Y, 4.0, 'black')
    tube(g, -30.0, -28.0, Y, 3.55, 'yellow')
    # Trigger lever and safety on top, the flip-up frame sights.
    g.bar(-8.0, 4.0, Y + 3.6, Y + 4.6, 2.2, 'black')
    g.bar(-6.0, -2.0, Y + 4.6, Y + 5.2, 1.0, 'red')
    # Flip-up sights on the left: the front frame with its post, the rear aperture.
    for z in (-34.0,):
        g.bar(z, z + 0.6, Y + 1.0, Y + 6.4, 0.5, 'black', x=-4.6)
        g.bar(z, z + 0.6, Y + 1.0, Y + 6.4, 0.5, 'black', x=-1.4)
        g.bar(z, z + 0.6, Y + 6.0, Y + 6.4, 3.7, 'black', x=-3.0)
        g.bar(z, z + 0.6, Y + 1.0, Y + 4.8, 0.3, 'black', x=-3.0)
    g.bar(-2.0, 1.0, Y + 1.0, Y + 4.0, 1.8, 'black', x=-3.0)
    g.box((-3.25, Y + 4.6, -1.0), (-2.75, Y + 5.1, -0.5), 'rubber')
    g.bar(-1.0, -0.4, Y + 4.0, Y + 5.6, 0.6, 'black', x=-3.6)
    g.bar(-1.0, -0.4, Y + 4.0, Y + 5.6, 0.6, 'black', x=-2.4)
    g.box((-1.8, Y - 0.5, -36.0), (1.8, Y + 0.5, -35.0), 'canvas')
    return g


def make_at4():
    Y = 6.0
    g = Gun('at4', Y + 6.8, bore=Y, R=4, seed=4)
    g.sight_x = -4.0
    tube(g, -46.0, 52.0, Y, 4.2, 'tube_olive')
    # Rubber shock absorbers at both ends.
    tube(g, -50.0, -42.0, Y, 4.8, 'rubber')
    tube(g, 48.0, 56.0, Y, 4.8, 'rubber')
    # The front grip (folded down), the shoulder rest, the firing mechanism and sights on top.
    g.bar(-8.0, -2.0, 1.6, Y - 3.8, 2.6, 'black')
    P.grip(g, (1.4, 2.0), (2.6, -6.4), 2.8, 2.6, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    g.bar(16.0, 30.0, 0.6, Y - 3.8, 3.0, 'poly')
    g.bar(-4.0, 12.0, Y + 4.0, Y + 5.6, 3.0, 'od_metal')
    g.bar(2.0, 6.0, Y + 5.6, Y + 6.4, 1.2, 'red')
    # Pop-up sights on the left of the tube: the front post on its blade, the rear aperture.
    g.bar(-28.0, -26.6, Y + 2.0, Y + 6.0, 0.6, 'black', x=-4.0)
    g.bar(-27.6, -27.0, Y + 6.0, Y + 6.8, 0.3, 'black', x=-4.0)
    g.bar(8.0, 12.0, Y + 2.0, Y + 6.2, 0.8, 'black', x=-4.0)
    g.box((-4.6, Y + 6.2, 9.4), (-3.4, Y + 7.4, 10.4), 'black')
    for z in (-36.0, 30.0):
        tube(g, z, z + 1.0, Y, 4.35, 'black')
    g.box((-2.0, Y - 0.6, -10.0), (2.0, Y + 0.6, 40.0), 'tube_olive', faces=('west',))
    return g


def make_cg84():
    Y = 5.6
    g = Gun('cg84', Y + 4.2, bore=Y, R=4, seed=84)
    # Carbon-fibre barrel, the steel venturi and its cone at the back, the front grip and the pistol grip.
    tube(g, -45.0, 36.0, Y, 4.6, 'poly_grey')
    tube(g, -45.0, -43.0, Y, 5.0, 'black')
    tube(g, 36.0, 40.0, Y, 4.0, 'gunmetal')
    tube(g, 40.0, 48.0, Y, 5.2, 'gunmetal')
    g.box((-4.0, Y - 4.0, 48.0), (4.0, Y + 4.0, 48.1), 'rubber', faces=('south',))
    g.bar(-1.6, 4.4, 1.0, Y - 4.4, 2.8, 'black')
    P.grip(g, (1.7, 1.0), (3.4, -8.4), 3.2, 2.8, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    P.trigger(g, z_front=-3.2, z_back=0.4, trig_z=-1.4, depth=2.0)
    P.grip(g, (-26.0, 1.0), (-25.0, -7.0), 3.0, 2.6, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    g.bar(-28.0, -23.0, 1.0, Y - 4.4, 2.4, 'black')
    # Shoulder pad and the venturi lock lever, the sight on the left.
    g.bar(14.0, 26.0, 0.2, Y - 4.4, 3.2, 'rubber')
    g.bar(30.0, 34.0, Y + 4.4, Y + 5.6, 1.6, 'black')
    g.bar(-16.0, 0.0, Y - 1.0, Y + 1.6, 1.6, 'black', x=-5.4)
    tube(g, -16.0, -2.0, Y + 1.4, 1.8, 'black', x=-7.4)
    tube(g, -2.0, 1.0, Y + 1.4, 2.0, 'rubber', x=-7.4)
    g.box((-8.8, Y + 0.2, -16.05), (-6.0, Y + 2.6, -16.0), 'glass', faces=('north',))
    g.bar(-40.0, -38.0, Y + 4.6, Y + 6.0, 1.2, 'black')
    return g


def make_nlaw():
    Y = 7.0
    g = Gun('nlaw', Y + 7.4, bore=Y, R=4, seed=101)
    # The squarish launch tube with its front and rear caps.
    g.bar(-48.0, 50.0, Y - 6.2, Y + 6.2, 12.0, 'nlaw_green')
    g.bar(-50.0, -46.0, Y - 6.8, Y + 6.8, 12.6, 'rubber')
    g.bar(48.0, 52.0, Y - 6.8, Y + 6.8, 12.6, 'rubber')
    g.bar(-30.0, -28.0, Y - 6.3, Y + 6.3, 12.2, 'canvas')
    # The grip and trigger unit under the front, the shoulder rest under the back, the optical sight on the left.
    g.bar(-6.0, 6.0, 0.4, Y - 6.2, 4.0, 'poly')
    P.grip(g, (1.6, 0.4), (3.4, -8.6), 3.4, 2.9, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    P.trigger(g, z_front=-3.2, z_back=0.4, trig_z=-1.4, depth=2.0)
    g.bar(-20.0, -10.0, 0.0, Y - 6.2, 4.0, 'poly')
    g.bar(-8.0, 10.0, Y + 0.6, Y + 6.0, 4.0, 'poly', x=-8.0)
    g.box((-9.6, Y + 2.0, -8.05), (-6.4, Y + 5.0, -8.0), 'glass', faces=('north',))
    g.box((-9.4, Y + 2.2, 10.0), (-6.6, Y + 4.6, 12.0), 'rubber')
    g.bar(-6.0, 4.0, Y + 6.2, Y + 7.4, 2.4, 'black')
    g.bar(18.0, 32.0, Y - 7.6, Y - 6.2, 8.0, 'rubber')
    return g


def make_javelin():
    """The Command Launch Unit (a box with its sights and two handles) on the left of the launch tube."""
    g = Gun('javelin', 10.0, bore=10.0, R=4, seed=1000)
    TX, TY = 12.0, 6.0
    # Launch tube with the battery/coolant unit at the back, the end caps.
    tube(g, -60.0, 58.0, TY, 7.0, 'jav_tube', x=TX)
    tube(g, -62.0, -58.0, TY, 7.4, 'rubber', x=TX)
    tube(g, 56.0, 60.0, TY, 7.4, 'rubber', x=TX)
    g.box((TX - 3.0, TY - 9.6, 30.0), (TX + 3.0, TY - 6.8, 44.0), 'clu')
    g.box((TX - 7.2, TY - 1.0, -20.0), (TX - 6.8, TY + 1.0, 30.0), 'canvas')
    # CLU: the body, the day sight and the thermal sight objectives, the eyepiece with its rubber cup.
    g.box((-6.0, 1.0, -14.0), (6.0, 14.0, 14.0), 'clu')
    g.box((-5.0, 14.0, -12.0), (5.0, 16.0, 8.0), 'clu')
    g.cyl(-14.0, -17.0, 10.0, 3.6, 'clu')
    g.box((-3.0, 7.0, -17.05), (3.0, 13.0, -17.0), 'glass', faces=('north',))
    g.cyl(-14.0, -16.0, 4.0, 2.2, 'clu', x=-2.0)
    g.box((-3.8, 2.2, -16.05), (-0.2, 5.8, -16.0), 'glass', faces=('north',))
    g.cyl(14.0, 18.0, 10.0, 2.4, 'rubber')
    # The two handles with their triggers.
    for x in (-7.5, 7.5):
        g.bar(-6.0, 2.0, -2.0, 1.0, 1.6, 'black', x=x)
        g.bar(-6.0, -4.0, 1.0, 6.0, 1.6, 'black', x=x)
        g.bar(0.0, 2.0, 1.0, 6.0, 1.6, 'black', x=x)
    g.bar(-4.0, 0.0, 6.0, 7.0, 1.0, 'black', x=-7.5)
    return g


def make_m32():
    Y = 4.6
    g = Gun('m32', 11.2, bore=Y, R=5, seed=32)
    # Frame, the six-shot cylinder (an octagon with the chambers' ends showing), the rifled barrel.
    g.bar(-24.0, 6.0, 0.0, Y - 4.0, 3.0, 'black')
    g.cyl(-22.0, -6.0, Y, 5.6, 'black')
    for i in range(6):
        import math
        a = math.radians(30 + 60 * i)
        x = 3.6 * math.cos(a)
        y = Y + 3.6 * math.sin(a)
        g.box((x - 1.6, y - 1.6, -22.05), (x + 1.6, y + 1.6, -22.0), 'brass_case', faces=('north',))
    g.cyl(-22.0, -50.0, Y, 2.6, 'black')
    g.cyl(-50.0, -51.0, Y, 2.9, 'black')
    g.bar(-6.0, 6.0, Y + 1.0, Y + 5.6, 2.6, 'black')
    # Rails round the barrel, the M2A1 sight on top, the grip, the collapsible stock.
    for x in (3.2, -3.2):
        P.side_rail(g, -26.0, -46.0, Y, x)
    P.rail(g, -26.0, -46.0, Y + 2.6)
    P.bottom_rail(g, -26.0, -44.0, Y - 2.6)
    g.cyl_y(0, -34.0, -2.0, Y - 2.6, 1.3, 'poly')
    g.bar(-4.0, 6.0, Y + 5.6, Y + 6.4, 2.2, 'black')
    g.box((-2.2, Y + 6.4, -2.0), (2.2, 11.2 + 2.4, 0.0), 'black')
    g.box((-1.8, 11.2 - 1.6, -2.05), (1.8, 11.2 + 1.6, -2.0), 'glass', faces=('north',))
    P.grip(g, (1.6, 0.0), (4.4, -9.4), 3.2, 2.8, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    P.trigger(g, z_front=-3.6, z_back=0.4, trig_z=-1.6)
    P.buffer_stock(g, 6.0, Y + 1.0, length=18.0, butt_bottom=-4.0)
    return g


ALL = [make_rpg7, make_rpg22, make_at4, make_cg84, make_nlaw, make_javelin, make_m32]
