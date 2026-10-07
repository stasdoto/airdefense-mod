"""The Kalashnikov family: AK-74, AKM, AK-12, AKS-74U, RPK-74, Saiga-12, PP-19 Bizon."""
from gunkit import Gun
import parts as P

BORE = 3.4
SIGHT = 7.35


def receiver(g, z_front=-16.0, z_back=8.0, metal='black', dust='black', ribbed=True, rail_top=False):
    # Stamped receiver with the trunnion in front, the dust cover on top.
    g.bar(z_front, z_back, 0.0, 5.4, 3.0, metal)
    g.bar(z_front - 2.0, z_front, 0.6, 5.0, 3.2, metal)
    g.bar(-10.5, z_back, 5.4, 6.0, 2.8, dust, aim=not rail_top)
    g.bar(-10.0, z_back - 0.2, 6.0, 6.35, 2.2, dust, aim=not rail_top)
    if ribbed:
        for z in (z_back - 6.0, z_back - 4.0, z_back - 2.0):
            g.bar(z - 0.25, z + 0.25, 6.0, 6.5, 2.3, dust, aim=False)
    # Dust cover catch at the back, rivets along the side.
    g.bar(z_back - 0.2, z_back + 0.7, 4.6, 5.8, 1.2, 'steel', aim=False)
    for z in (z_front + 2.0, z_front + 4.5, -2.0, z_back - 2.5):
        for x in (-1.55, 1.5):
            g.box((x, 1.0, z - 0.25), (x + 0.05, 1.5, z + 0.25), 'steel', faces=('east', 'west'))
    # Bolt carrier and charging handle on the right, the long selector lever.
    g.bar(-9.0, -2.5, 3.6, 4.6, 0.4, 'gunmetal', x=1.65)
    g.bar(-10.6, -9.0, 3.5, 4.7, 0.6, 'gunmetal', x=1.8)
    g.box((1.8, 3.6, -10.6), (3.4, 4.6, -9.4), 'gunmetal')
    g.bar(-9.5, 1.0, 3.5, 4.2, 0.2, metal, x=1.6)
    g.box((1.5, 2.6, 1.0), (1.85, 4.2, 2.0), metal)
    # Magazine catch in front of the trigger guard.
    g.bar(-8.4, -7.4, -1.6, 0.0, 1.0, metal, aim=False)


def rear_sight(g, z=-12.5, metal='black'):
    g.bar(z - 2.2, z + 2.2, 5.4, 6.6, 2.3, metal)
    g.bar(z - 2.0, z + 1.6, 6.6, 6.95, 1.8, metal)
    P.notch(g, z + 1.4, 6.95, SIGHT + 0.2, w=1.8, gap=0.45, style=metal)


def front_sight(g, z, metal='black'):
    g.bar(z - 1.4, z + 1.4, BORE - 1.2, BORE + 1.3, 2.2, metal)
    P.iron_post(g, z, BORE + 1.3, SIGHT, ear_gap=0.85, style=metal)
    g.bar(z - 1.0, z + 0.6, BORE - 1.9, BORE - 1.2, 1.0, metal)


def handguards(g, z0, z1, lower, upper, w=4.2, metal='black'):
    """Lower handguard round the barrel and the upper one over the gas tube, with the retainer bands."""
    g.bar(z0, z1, -0.4, 4.1, w, lower)
    g.bar(z0 + 0.3, z1 + 0.6, 4.1, 5.9, 2.8, upper)
    g.bar(z0 + 0.5, z1 + 1.0, 5.9, 6.35, 2.1, upper)
    g.bar(z1, z1 - 1.0, -0.6, 4.3, w + 0.25, metal)
    g.bar(z0 + 0.6, z0 - 0.4, -0.6, 4.3, w + 0.25, metal)


def gas_block(g, z, metal='black'):
    g.bar(z, z - 2.6, BORE - 1.0, BORE + 1.0, 2.4, metal)
    g.bar(z + 0.4, z - 2.4, BORE + 1.0, 6.2, 1.8, metal)


def barrel(g, z0, z1, r=0.85, metal='black', rod=True):
    g.cyl(z0, z1, BORE, r, metal)
    if rod:
        g.cyl(z0 - 0.5, z1 + 1.5, BORE - 1.55, 0.3, 'steel', fine=False)


def ak74(g, stock='plum', furniture='plum', mag_style='mag_plum', barrel_end=-53.5, muzzle='74'):
    receiver(g)
    rear_sight(g)
    P.trigger(g, z_front=-7.2, z_back=-0.2, trig_z=-3.0)
    P.pistol_grip(g, furniture if furniture != 'wood' else 'plum', sides={'east': 'grip_wood' if furniture == 'wood' else 'mag_plum', 'west': 'mag_plum'})
    P.curved_mag(g, z_back=-8.6, depth=6.8, length=19.5, curve=6.0, style=mag_style)
    handguards(g, -17.5, -32.0, furniture + '_grooves' if furniture in ('plum', 'wood') else furniture, furniture)
    gas_block(g, -33.0)
    barrel(g, -33.0, barrel_end)
    front_sight(g, barrel_end + 1.8)
    if muzzle == '74':
        P.ak74_brake(g, barrel_end, BORE)
    elif muzzle == 'akm':
        P.slant_comp(g, barrel_end, BORE)
    # Fixed stock: top edge almost level, the lower edge dropping to a deep butt.
    if stock:
        P.profile_stock(g, 8.0, 32.4, 4.6, 3.8, 0.8, -8.6, 3.0, 3.6, stock, plate='black')
        # Sling swivel on the left.
        g.bar(28.0, 29.2, -6.8, -5.8, 0.3, 'steel', x=-1.9, aim=False)


def make_ak74():
    g = Gun('ak74', SIGHT, R=6, seed=74)
    ak74(g)
    return g


def make_akm():
    g = Gun('akm', SIGHT, R=6, seed=47)
    ak74(g, stock='wood', furniture='wood', mag_style='mag_steel', barrel_end=-50.5, muzzle='akm')
    return g


def make_ak12():
    g = Gun('ak12', SIGHT + 3.2, R=6, seed=12)
    receiver(g, rail_top=True, dust='black')
    # Full-length rail on the dust cover and the handguard, a collimator sight on it.
    P.rail(g, -33.0, 7.5, 6.35)
    P.trigger(g)
    P.pistol_grip(g, 'poly', sides={'east': 'grip', 'west': 'grip'})
    P.curved_mag(g, z_back=-8.6, depth=6.8, length=19.5, curve=6.0, style='mag')
    # Railed handguard: square, with side rails.
    g.bar(-17.0, -33.0, -0.6, 6.35, 4.4, 'poly_grooves')
    for x in (2.2, -2.2):
        P.side_rail(g, -20.0, -31.0, 2.6, x)
    g.bar(-21.0, -31.0, -1.3, -0.6, 2.0, 'rail')
    gas_block(g, -33.5)
    barrel(g, -33.5, -52.0, rod=False)
    g.bar(-49.0, -50.6, BORE - 1.0, BORE + 1.0, 2.0, 'black')
    # AK-12 brake: round with long slots.
    g.cyl(-52.0, -59.5, BORE, 1.15, 'slots')
    g.cyl(-59.0, -59.6, BORE, 1.2, 'black')
    # Folding telescopic stock: a tube, a polymer body, an adjustable cheek rest.
    g.bar(8.0, 11.0, 1.5, 5.2, 2.8, 'black', aim=False)
    g.cyl(10.0, 22.0, 4.0, 1.2, 'black', aim=False)
    P.profile_stock(g, 14.0, 31.0, 5.6, 5.4, 1.5, -6.5, 3.4, 3.6, 'poly', plate='rubber')
    g.bar(16.0, 27.0, 5.6, 6.6, 2.6, 'poly', aim=False)
    P.red_dot(g, -4.0, 7.25, SIGHT + 3.2)
    return g


def make_aks74u():
    g = Gun('aks74u', SIGHT, R=6, seed=75)
    receiver(g)
    # Rear sight on the hinged dust cover, front sight on the gas block.
    g.bar(-12.0, -8.0, 5.4, 6.4, 2.2, 'black')
    P.notch(g, -9.0, 6.4, SIGHT + 0.2, w=1.6, gap=0.4)
    P.trigger(g)
    P.pistol_grip(g, 'plum', sides={'east': 'mag_plum', 'west': 'mag_plum'})
    P.curved_mag(g, z_back=-8.6, depth=6.8, length=19.5, curve=6.0, style='mag_plum')
    g.bar(-17.5, -27.0, -0.4, 4.1, 4.0, 'plum_grooves')
    g.bar(-17.0, -26.4, 4.1, 5.9, 2.8, 'plum')
    g.bar(-27.0, -28.0, -0.6, 4.3, 4.3, 'black')
    g.bar(-28.0, -31.0, BORE - 1.0, 6.2, 2.4, 'black')
    P.iron_post(g, -30.0, 6.2, SIGHT)
    g.cyl(-28.0, -32.0, BORE, 0.85, 'black')
    # The cone-shaped booster.
    g.cyl(-32.0, -37.5, BORE, 1.55, 'black')
    g.cyl(-37.5, -38.5, BORE, 1.15, 'black')
    # Folding frame stock (unfolded) on the left side hinge.
    g.bar(8.0, 9.5, 0.5, 5.0, 3.4, 'black', aim=False)
    P.skeleton_stock(g, 9.5, 31.5, 4.8, 4.2, 0.9, -5.6, t=1.0, w=1.2, style='black', plate='black')
    return g


def make_rpk74():
    g = Gun('rpk74', SIGHT, R=6, seed=76)
    receiver(g, z_front=-16.5)
    rear_sight(g)
    P.trigger(g)
    P.pistol_grip(g, 'plum', sides={'east': 'mag_plum', 'west': 'mag_plum'})
    # 45-round magazine.
    P.curved_mag(g, z_back=-8.6, depth=6.8, length=24.5, curve=8.5, style='mag_plum')
    handguards(g, -17.5, -33.0, 'plum_grooves', 'plum')
    gas_block(g, -34.0)
    g.cyl(-34.0, -72.0, BORE, 1.0, 'black')
    front_sight(g, -70.0)
    P.slant_comp(g, -72.0, BORE)
    P.bipod(g, -66.0, BORE - 0.9, 25.0)
    # Club-shaped RPK stock.
    P.profile_stock(g, 8.0, 33.0, 4.6, 4.2, 0.8, -9.8, 3.0, 3.8, 'plum', plate='black')
    return g


def make_saiga12():
    g = Gun('saiga12', SIGHT, R=6, seed=13)
    receiver(g, z_front=-16.5)
    rear_sight(g)
    P.trigger(g)
    P.pistol_grip(g, 'poly', sides={'east': 'grip', 'west': 'grip'})
    # Big straight box magazine for 12-gauge shells.
    P.straight_mag(g, z_back=-8.0, depth=9.0, length=17.0, w=3.6, style='mag', slant=2.5)
    handguards(g, -17.5, -33.0, 'poly_grooves', 'poly')
    gas_block(g, -34.0)
    g.cyl(-34.0, -62.0, BORE, 1.15, 'black')
    front_sight(g, -60.5)
    P.profile_stock(g, 8.0, 32.0, 4.6, 3.8, 0.8, -8.4, 3.0, 3.6, 'poly', plate='rubber')
    return g


def make_bizon():
    g = Gun('bizon', SIGHT, R=6, seed=19)
    receiver(g, z_front=-14.0, z_back=6.0)
    rear_sight(g, z=-10.5)
    P.trigger(g)
    P.pistol_grip(g, 'poly', sides={'east': 'grip', 'west': 'grip'})
    # The helical 64-round magazine under the barrel, which also is the front grip.
    g.cyl(-12.0, -38.0, -1.9, 2.4, 'mag')
    g.cyl(-38.0, -39.0, -1.9, 2.0, 'black')
    g.bar(-14.0, -22.0, 4.1, 5.9, 2.6, 'poly')
    g.cyl(-22.0, -38.0, BORE, 1.1, 'black')
    g.cyl(-38.0, -41.5, BORE, 0.9, 'black')
    g.bar(-36.0, -38.0, BORE + 1.0, 6.1, 1.6, 'black')
    P.iron_post(g, -37.0, 6.1, SIGHT)
    # Folding frame stock.
    P.skeleton_stock(g, 6.0, 27.0, 4.6, 4.0, 0.8, -5.2, t=1.0, w=1.2, style='black', plate='black')
    return g


ALL = [make_ak74, make_akm, make_ak12, make_aks74u, make_rpk74, make_saiga12, make_bizon]
