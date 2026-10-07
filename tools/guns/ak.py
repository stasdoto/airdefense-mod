"""The Kalashnikov family: AK-74, AKM, AK-12, AKS-74U, RPK-74, Saiga-12, PP-19 Bizon.

All share the stamped receiver (origin at the top front of the pistol grip, the receiver's bottom edge at y = 0),
the bore 3.6 cm above that, the sight line 7.35 cm up."""
from gunkit import Gun
import parts as P

BORE = 3.6
SIGHT = 7.35


def receiver(g, z_front=-16.0, z_back=8.0, metal='black', dust='black', ribbed=True, rail_top=False, dimples=True):
    """Stamped receiver with the front trunnion, the dust cover, the bolt carrier and charging handle in the
    ejection port on the right, the long selector lever, rivets, the magazine well dimples."""
    g.bar(z_front, z_back, 0.0, 5.4, 3.0, metal)
    # Front trunnion and the rear trunnion / stock tang.
    g.bar(z_front - 2.0, z_front, 0.5, 5.0, 3.2, metal)
    g.bar(z_back - 0.1, z_back + 0.6, 0.4, 5.3, 2.8, metal, aim=False)
    # Dust cover: a rounded top, ribbed on the AK-74.
    if not rail_top:
        g.bar(-10.5, z_back, 5.4, 6.0, 2.8, dust)
        g.bar(-10.0, z_back - 0.2, 6.0, 6.35, 2.2, dust)
        if ribbed:
            for z in (z_back - 6.5, z_back - 4.5, z_back - 2.5):
                g.bar(z - 0.25, z + 0.25, 6.0, 6.45, 2.4, dust, aim=False)
    # Recoil spring guide button behind the dust cover.
    g.bar(z_back, z_back + 0.7, 4.7, 5.7, 1.1, 'steel', aim=False)
    # Ejection port on the right: the dark opening, the bolt carrier, the charging handle at its front.
    P.plate(g, -9.6, 0.4, 3.4, 5.35, 1.5, 'groove', side=1)
    g.bar(-9.4, -2.6, 3.6, 4.7, 0.3, 'gunmetal', x=1.62)
    g.bar(-10.7, -9.2, 3.55, 4.75, 0.6, 'gunmetal', x=1.85)
    g.box((1.9, 3.7, -10.6), (3.2, 4.6, -9.6), 'gunmetal')
    g.cyl_x(3.2, 3.6, 4.15, -10.1, 0.55, 'gunmetal')
    # Selector lever over the port: pivot at the back, the long blade forward, the finger tab.
    g.bar(-9.6, 1.2, 4.15, 4.75, 0.18, metal, x=1.62)
    g.bar(-9.6, -8.5, 3.2, 4.75, 0.22, metal, x=1.64)
    P.rivet(g, 1.5, 4.4, 0.9, r=0.35, style=metal)
    # Rivets of the trunnions (both sides).
    for z in (z_front + 0.9, z_front + 2.3, z_front + 3.7):
        P.rivet(g, 1.5, 1.0, z)
        P.rivet(g, -1.5, 1.0, z, side=-1)
    for z in (-1.4, 0.4, 2.2):
        P.rivet(g, 1.5, 0.9, z)
        P.rivet(g, -1.5, 0.9, z, side=-1)
    P.rivet(g, 1.5, 4.3, z_front + 1.0)
    P.rivet(g, -1.5, 4.3, z_front + 1.0, side=-1)
    # Magazine well dimples above the magazine.
    if dimples:
        P.plate(g, -14.4, -11.2, 2.6, 4.1, 1.5, 'groove', side=1)
        P.plate(g, -14.4, -11.2, 2.6, 4.1, -1.5, 'groove', side=-1)
    # Magazine catch in front of the trigger guard.
    g.bar(-8.4, -7.3, -1.7, 0.0, 1.0, metal, aim=False)
    g.bar(-8.6, -7.3, -2.0, -1.6, 1.6, metal, aim=False)


def rear_sight(g, z=-12.5, metal='black'):
    """The rear sight block on the front trunnion with the tangent leaf and its slider."""
    g.bar(z - 2.4, z + 2.2, 5.4, 6.55, 2.4, metal)
    g.slab((z - 2.4, 6.6), (z + 1.6, 6.95), 0.35, 1.9, metal)
    g.bar(z - 0.6, z + 0.4, 6.4, 7.1, 2.2, 'steel')
    P.notch(g, z + 1.5, 6.95, SIGHT + 0.2, w=1.8, gap=0.45, style=metal)


def front_sight(g, z, metal='black', lug=True):
    """The front sight block on the barrel: the post between its ears, the bayonet lug under it."""
    g.bar(z - 1.5, z + 1.5, BORE - 1.2, BORE + 1.25, 2.2, metal)
    g.slab((z + 1.5, BORE + 1.25), (z - 0.9, BORE + 2.4), 0.8, 1.6, metal)
    P.iron_post(g, z - 0.4, BORE + 2.2, SIGHT, ear_gap=0.85, style=metal)
    if lug:
        g.bar(z - 1.1, z + 0.7, BORE - 2.0, BORE - 1.2, 1.0, metal)


def handguards(g, z0, z1, lower, upper, w=4.2, metal='black', grooves=True):
    """Lower handguard round the barrel (rounded underneath), the upper one over the gas tube, the retainer bands."""
    g.bar(z0, z1, 0.5, 4.3, w, lower)
    g.bar(z0 - 0.2, z1 + 0.2, 0.05, 0.5, w - 0.9, lower)
    g.bar(z0 + 0.3, z1 + 0.6, 4.3, 5.9, 2.9, upper)
    g.bar(z0 + 0.5, z1 + 1.0, 5.9, 6.35, 2.1, upper)
    # Retainer bands: the rear one round both, the front one with the sling swivel.
    g.bar(z0 + 0.6, z0 - 0.4, 0.0, 4.4, w + 0.25, metal)
    g.bar(z1, z1 - 1.0, 0.0, 4.4, w + 0.25, metal)
    P.sling_loop(g, z1 - 0.5, 1.2, -w / 2 - 0.12, side=-1)
    if grooves:
        # The finger grooves along the lower handguard (dark lines on both sides).
        for y in (1.6, 2.6):
            P.plate(g, z0 - 1.0, z1 + 1.5, y, y + 0.25, w / 2, 'plum_dark' if 'plum' in lower else 'wood_dark' if 'wood' in lower else 'groove')
            P.plate(g, z0 - 1.0, z1 + 1.5, y, y + 0.25, -w / 2, 'plum_dark' if 'plum' in lower else 'wood_dark' if 'wood' in lower else 'groove', side=-1)


def gas_block(g, z, metal='black'):
    """The gas block (the port to the barrel at 45 degrees) and the gas tube's end."""
    g.bar(z, z - 2.6, BORE - 1.0, BORE + 1.0, 2.4, metal)
    g.bar(z + 0.4, z - 2.4, BORE + 1.0, 6.2, 1.9, metal)
    g.slab((z - 2.4, BORE + 0.6), (z - 1.0, 6.0), 1.4, 1.7, metal)


def barrel(g, z0, z1, r=0.85, metal='black', rod=True):
    g.cyl(z0, z1, BORE, r, metal)
    if rod:
        g.cyl(z0 - 0.5, z1 + 1.8, BORE - 1.6, 0.3, 'steel', fine=False)
        g.bar(z1 + 1.8, z1 + 2.3, BORE - 1.9, BORE - 1.3, 0.7, 'steel')


def ak_stock(g, style, plate='black', grooves=None, z0=8.0, z1=32.4, top0=4.6, top1=3.7, bot0=0.6, bot1=-8.6, w=3.3):
    """The fixed stock: the top edge almost level, the lower edge falling to a deep butt; the steel butt plate with
    its trapdoor, the two lightening grooves on each side, the sling swivel on the left."""
    g.profile(((z0, top0), (z1, top1)), ((z0, bot0), (z1, bot1)), w, style, aim=False)
    g.box((-w / 2 - 0.05, bot1 - 0.15, z1), (w / 2 + 0.05, top1 + 0.15, z1 + 0.8), plate, aim=False)
    g.box((-w / 2 + 0.3, bot1 + 2.0, z1 + 0.8), (w / 2 - 0.3, top1 - 1.5, z1 + 0.9), 'gunmetal', aim=False)
    if grooves:
        for (ya, yb) in ((0.4, 1.0), (-1.6, -1.0)):
            for x, side in ((w / 2, 1), (-w / 2, -1)):
                P.plate(g, z0 + 4.0, z1 - 4.0, ya + (top1 - top0) * 0.3, yb + (top1 - top0) * 0.3, x, grooves, side=side, aim=False)
    P.sling_loop(g, z1 - 4.0, bot1 + 3.4 + (bot0 - bot1) * 0.15, -w / 2 - 0.1, side=-1)


def ak_grip(g, style, sides='plum_grooves'):
    P.grip(g, (1.7, 0.2), (5.3, -10.4), 3.3, 2.7, style, cap=None, sides={'east': sides, 'west': sides} if sides else None)


def ak74(g, stock='plum', furniture='plum', mag_style='mag_plum', barrel_end=-53.5, muzzle='74', metal='black'):
    receiver(g, metal=metal, dust=metal)
    rear_sight(g, metal=metal)
    P.trigger(g, z_front=-7.2, z_back=-0.2, trig_z=-3.0, style=metal)
    ak_grip(g, 'plum' if furniture == 'plum' else 'wood' if furniture == 'wood' else furniture,
            sides='plum_grooves' if furniture == 'plum' else 'grip_wood' if furniture == 'wood' else 'grip')
    P.arc_mag(g, (-12.1, 0.6), 19.0, 3.0, 30.0 if muzzle == 'akm' else 24.0, 6.8, 2.6, mag_style, depth1=7.3,
              plate=metal if mag_style != 'mag_plum' else 'plum_dark')
    handguards(g, -17.5, -32.0, furniture + '_grooves' if furniture in ('plum', 'wood') else furniture, furniture, metal=metal)
    gas_block(g, -33.0, metal)
    barrel(g, -33.0, barrel_end, metal=metal)
    front_sight(g, barrel_end + 1.8, metal)
    if muzzle == '74':
        P.ak74_brake(g, barrel_end, BORE, metal)
    elif muzzle == 'akm':
        P.slant_comp(g, barrel_end, BORE, metal)
    if stock:
        ak_stock(g, stock, plate=metal, grooves='plum_dark' if stock == 'plum' else None)


def make_ak74():
    g = Gun('ak74', SIGHT, bore=BORE, R=6, seed=74)
    ak74(g)
    return g


def make_akm():
    g = Gun('akm', SIGHT, bore=BORE, R=6, seed=47)
    ak74(g, stock='wood', furniture='wood', mag_style='mag_steel', barrel_end=-50.5, muzzle='akm', metal='blued')
    return g


def make_ak12():
    g = Gun('ak12', SIGHT + 3.4, bore=BORE, R=6, seed=12)
    receiver(g, rail_top=True, dust='black')
    # Rail-top dust cover hinged at the front, a long rail on it and a collimator sight.
    g.bar(-10.5, 8.0, 5.4, 6.4, 2.9, 'black')
    P.rail(g, -10.0, 7.5, 6.4)
    P.trigger(g)
    P.grip(g, (1.6, 0.2), (5.4, -10.2), 3.4, 2.9, 'poly', cap='poly', sides={'east': 'grip', 'west': 'grip'})
    P.arc_mag(g, (-12.1, 0.6), 19.0, 3.0, 24.0, 6.8, 2.6, 'mag', depth1=7.3)
    # Railed handguard: square, M-LOK slots on the sides, a short rail underneath, the top rail running on.
    g.bar(-17.0, -33.0, 0.2, 6.0, 4.4, 'poly_grooves')
    g.bar(-17.0, -33.0, -0.3, 0.2, 3.4, 'poly')
    P.rail(g, -17.5, -32.0, 6.0)
    for x, side in ((2.2, 1), (-2.2, -1)):
        for z in (-19.5, -23.0, -26.5, -30.0):
            P.plate(g, z, z + 2.4, 2.4, 3.4, x, 'groove', side=side)
    P.bottom_rail(g, -22.0, -31.0, -0.3)
    gas_block(g, -33.5)
    barrel(g, -33.5, -52.0, rod=False)
    # AK-12 brake: round, two long slots each side.
    g.cyl(-52.0, -53.0, BORE, 0.95, 'black')
    g.cyl(-53.0, -59.5, BORE, 1.2, 'black')
    for x in (1.15, -1.21):
        g.box((x, BORE - 0.35, -58.6), (x + 0.06, BORE + 0.35, -54.0), 'rubber', faces=('east', 'west'))
    P.iron_post(g, -50.0, BORE + 1.0, SIGHT + 3.0, ear_gap=0.85)
    # Folding, telescopic stock: hinge, tube, body with the cheek rest, the rubber pad.
    g.bar(8.0, 10.5, 0.8, 5.4, 3.0, 'black', aim=False)
    g.cyl(10.0, 20.0, 4.3, 1.2, 'black', aim=False)
    g.profile(((14.0, 6.0), (31.0, 6.0)), ((14.0, 1.6), (31.0, -6.8)), 3.6, 'poly', aim=False)
    g.box((-1.5, 6.0, 17.0), (1.5, 6.9, 28.0), 'poly', aim=False)
    g.box((-1.9, -7.0, 31.0), (1.9, 6.2, 32.4), 'rubber', aim=False)
    P.red_dot(g, -4.0, 7.3, SIGHT + 3.4)
    return g


def make_aks74u():
    g = Gun('aks74u', SIGHT, bore=BORE, R=6, seed=75)
    receiver(g, ribbed=False)
    # Rear sight on the hinged dust cover, front sight on the gas block.
    g.bar(-12.0, -7.5, 5.4, 6.45, 2.3, 'black')
    g.bar(-12.0, -10.5, 6.45, 6.95, 1.9, 'black')
    P.notch(g, -8.5, 6.45, SIGHT + 0.2, w=1.6, gap=0.4)
    P.trigger(g)
    ak_grip(g, 'plum')
    P.arc_mag(g, (-12.1, 0.6), 19.0, 3.0, 24.0, 6.8, 2.6, 'mag_plum', depth1=7.3, plate='plum_dark')
    g.bar(-17.5, -27.0, 0.5, 4.3, 4.0, 'plum_grooves')
    g.bar(-17.7, -26.8, 0.05, 0.5, 3.2, 'plum')
    g.bar(-17.0, -26.4, 4.3, 5.9, 2.9, 'plum')
    g.bar(-16.6, -26.0, 5.9, 6.3, 2.1, 'plum')
    g.bar(-27.0, -28.0, 0.0, 4.4, 4.3, 'black')
    # The combined gas block / front sight.
    g.bar(-28.0, -31.2, BORE - 1.0, 6.2, 2.4, 'black')
    g.slab((-31.2, BORE + 0.4), (-29.0, 6.6), 1.0, 1.7, 'black')
    P.iron_post(g, -30.0, 6.2, SIGHT, ear_gap=0.8)
    g.cyl(-28.0, -32.0, BORE, 0.85, 'black')
    # The cone-shaped booster.
    g.cyl(-32.0, -33.0, BORE, 1.3, 'black')
    g.cyl(-33.0, -37.0, BORE, 1.55, 'black')
    g.cyl(-37.0, -38.4, BORE, 1.2, 'black')
    # Folding frame stock (unfolded) on the left side hinge.
    g.bar(8.0, 9.6, 0.5, 5.0, 3.4, 'black', aim=False)
    P.skeleton_stock(g, 9.6, 31.5, 4.8, 4.2, 0.9, -5.4, t=1.0, w=1.2, style='black', plate='black')
    g.slab((10.0, 4.3), (13.5, 1.3), 0.9, 1.2, 'black', aim=False)
    return g


def make_rpk74():
    g = Gun('rpk74', SIGHT, bore=BORE, R=6, seed=76)
    receiver(g, z_front=-16.5)
    rear_sight(g)
    P.trigger(g)
    ak_grip(g, 'plum')
    # 45-round magazine.
    P.arc_mag(g, (-12.1, 0.6), 24.5, 3.0, 36.0, 6.8, 2.6, 'mag_plum', n=9, depth1=7.5, plate='plum_dark')
    handguards(g, -18.0, -33.0, 'plum_grooves', 'plum')
    gas_block(g, -34.0)
    g.cyl(-34.0, -72.0, BORE, 1.0, 'black')
    front_sight(g, -70.0)
    P.slant_comp(g, -72.0, BORE)
    P.bipod(g, -67.0, BORE - 0.9, 24.0)
    # Club-shaped RPK stock with a thumb rest behind the grip.
    g.profile(((8.0, 4.6), (33.0, 4.0)), ((8.0, 0.6), (33.0, -9.6)), 3.6, 'plum', aim=False)
    g.slab((8.5, 0.2), (12.0, -3.8), 2.4, 3.4, 'plum', aim=False)
    g.box((-1.9, -9.8, 33.0), (1.9, 4.2, 33.8), 'black', aim=False)
    return g


def make_saiga12():
    g = Gun('saiga12', SIGHT, bore=BORE, R=6, seed=13)
    receiver(g, z_front=-16.5, dimples=False)
    rear_sight(g)
    P.trigger(g)
    P.grip(g, (1.6, 0.2), (5.4, -10.2), 3.4, 2.9, 'poly', cap='poly', sides={'east': 'grip', 'west': 'grip'})
    # Big straight box magazine for 12-gauge shells.
    P.straight_mag(g, z_back=-8.0, depth=9.0, length=17.0, w=3.8, style='mag', slant=2.5)
    handguards(g, -17.5, -33.0, 'poly_grooves', 'poly', grooves=False)
    gas_block(g, -34.0)
    g.cyl(-34.0, -62.0, BORE, 1.2, 'black')
    front_sight(g, -60.5, lug=False)
    g.cyl(-62.0, -63.0, BORE, 1.35, 'black')
    g.profile(((8.0, 4.6), (32.0, 3.8)), ((8.0, 0.6), (32.0, -8.4)), 3.3, 'poly', aim=False)
    g.box((-1.8, -8.6, 32.0), (1.8, 4.0, 33.4), 'rubber', aim=False)
    return g


def make_bizon():
    g = Gun('bizon', SIGHT, bore=BORE, R=6, seed=19)
    receiver(g, z_front=-14.0, z_back=6.0, ribbed=False)
    rear_sight(g, z=-10.5)
    P.trigger(g)
    P.grip(g, (1.6, 0.2), (5.2, -10.0), 3.3, 2.8, 'poly', cap=None, sides={'east': 'grip', 'west': 'grip'})
    # The helical 64-round magazine under the barrel, which also is the front grip.
    g.cyl(-12.0, -38.0, -1.9, 2.45, 'mag')
    g.cyl(-38.0, -39.0, -1.9, 2.0, 'black')
    g.cyl(-11.5, -12.0, -1.9, 2.1, 'black')
    g.bar(-14.0, -22.0, 4.1, 5.9, 2.7, 'poly')
    g.cyl(-22.0, -38.0, BORE, 1.1, 'black')
    g.cyl(-38.0, -41.5, BORE, 0.9, 'black')
    g.bar(-36.0, -38.0, BORE + 1.0, 6.1, 1.6, 'black')
    P.iron_post(g, -37.0, 6.1, SIGHT)
    # Folding frame stock.
    P.skeleton_stock(g, 6.0, 27.0, 4.6, 4.0, 0.8, -5.2, t=1.0, w=1.2, style='black', plate='black')
    return g


ALL = [make_ak74, make_akm, make_ak12, make_aks74u, make_rpk74, make_saiga12, make_bizon]
