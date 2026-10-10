"""
1.31 "Armour": more tanks, fighting vehicles and armoured cars, the recovery vehicles that mend the others in the field,
and the TOS-1A heavy flamethrower system. Metres, x right, y up, z forward (the nose).
"""
from boxgen import Model
from kit import axles, tracks, lights, hood_cab
from armor import glacis, tank_hull, turret_dome, era, gun
from airdefense import barrel, canister
from tankkit import (cast_turret, periscope, bin_box, smoke_launchers, cupola, hatch, antenna, cables, track_links, log, grille, skirt_panels, roof_era,
                     tarp, basket, headlights, fuel_tanks, mantlet_cover, sight_box)


def skirts(body, z0, z1, y0, y1, half, paint, rubber=True):
    """Side skirts over the tracks (and the rubber flaps under them)."""
    for s in (-1, 1):
        x = s * half
        body.box((x - (0.06 if s > 0 else 0.0), y0, z0), (x + (0.0 if s > 0 else 0.06), y1, z1), paint)
        if rubber:
            body.box((x - (0.05 if s > 0 else 0.0), y0 - 0.25, z0 + 0.2), (x + (0.0 if s > 0 else 0.05), y0, z1 - 0.2), 'dark')


def rws(m, tur, x, y, z, paint, caliber=0.05):
    """A remote weapon station: a small box on a pivot with the machine gun and its sight."""
    tur.box((x - 0.25, y, z - 0.3), (x + 0.25, y + 0.25, z + 0.25), paint)
    tur.box((x - 0.2, y + 0.25, z - 0.15), (x + 0.05, y + 0.45, z + 0.2), paint, sides={'front': 'glass'})
    return gun(m, tur, (x + 0.15, y + 0.33, z + 0.25), 1.15, caliber, mantlet=0.15)


# ------------------------------------------------------------------------------------------------------------------
# Tanks


def t80bvm():
    """T-80BVM: the gas-turbine tank - a low cast turret with Relikt bricks, rubber skirts with ERA, the turbine's
    long grille, the cage against drones, the unditching log."""
    m = Model('t80bvm', paint='rgreen', seed=2201)
    m.width = 3.58
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    zf, zb = tank_hull(m, body, 7.0, 3.58, 0.92, 1.5, 'rgreen', era=7)
    skirt_panels(body, zb + 0.4, zf - 0.6, 0.65, 0.98, 1.79, 'rgreen', n=6, era=True)
    headlights(body, zf - 0.9, 1.5, 1.25)
    # The turbine's deck: a long grille, the exhaust at the very back.
    grille(body, -1.2, 1.2, 1.5, zb + 0.2, zb + 2.0)
    body.box((-0.9, 1.0, zb - 0.06), (0.9, 1.35, zb), 'black')
    fuel_tanks(body, 1.25, 1.75, 0.98, zb + 2.2, zb + 4.3, 'rgreen', n=3)
    bin_box(body, -1.75, -1.25, 0.98, 1.3, zb + 2.2, zb + 3.8, 'rgreen')
    cables(body, -1, -1.6, 1.53, zb + 3.9, zf - 1.4)
    log(body, 1.55, zb + 0.1, 1.4)
    tz = -0.2
    tur = m.part('turret', (0, 1.5, tz))
    m.set_turret(tur)
    cast_turret(tur, 0, tz, 1.5, 2.25, 2.5, 0.72, 'rgreen')
    for s in (-1, 1):
        tur.box((s * 0.68 - 0.5, 1.55, tz + 1.05), (s * 0.68 + 0.5, 2.1, tz + 1.55), 'rgreen')
        tur.box((s * 1.05 - 0.15, 1.6, tz - 0.9), (s * 1.05 + 0.15, 2.05, tz + 0.9), 'rgreen')
        smoke_launchers(tur, s * 1.15, 1.95, tz + 0.6, 4, s)
    roof_era(tur, -1.0, 1.0, 2.2, tz + 0.4, tz + 1.0, 'rgreen', cols=4, rows=1)
    tur.box((0.35, 2.22, tz - 0.45), (0.9, 2.45, tz + 0.05), 'rgreen', sides={'front': 'glass'})
    cupola(tur, -0.55, 2.22, tz - 0.3, 'rgreen')
    bin_box(tur, -1.0, 1.0, 1.65, 2.05, tz - 1.6, tz - 1.2, 'rgreen', straps=3)
    tarp(tur, -0.8, 0.8, 2.05, tz - 1.4)
    # The cage against drones.
    for x in (-1.2, 1.2):
        for z in (tz - 1.5, tz + 0.8):
            tur.box((x - 0.04, 2.2, z - 0.04), (x + 0.04, 2.95, z + 0.04), 'dgrey')
    tur.box((-1.25, 2.95, tz - 1.6), (1.25, 3.0, tz + 0.9), 'mesh_dark')
    antenna(tur, -0.95, 2.2, tz - 1.0, 2.4)
    el = gun(m, tur, (0, 1.9, tz + 1.5), 5.3, 0.16)
    mantlet_cover(el, 0, 1.9, tz + 1.5, 0.6, 0.4)
    m.seat('driver', 0.0, 0.95, 2.4)
    m.seat('gunner', 0.6, 2.1, tz - 0.1)
    return m


def challenger2():
    """Challenger 2: angular Chobham turret with a big bustle, a rifled 120 mm gun in its thermal sleeve, long
    skirts in panels, the drum tanks and bins at the back."""
    m = Model('challenger2', paint='nato', seed=2202)
    m.width = 3.52
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    zf, zb = tank_hull(m, body, 8.3, 3.52, 0.98, 1.65, 'nato', wheels=6, r=0.38)
    skirt_panels(body, zb + 0.3, zf - 0.8, 0.7, 1.05, 1.76, 'nato', n=7, rubber=False)
    headlights(body, zf - 1.15, 1.65, 1.3)
    grille(body, -1.2, 1.2, 1.65, zb + 0.3, zb + 1.8)
    # Stowage bins on the hull's back, the drum tanks.
    for s in (-1, 1):
        bin_box(body, s * 0.95 - 0.35, s * 0.95 + 0.35, 1.0, 1.6, zb - 0.55, zb - 0.05, 'dgrey', straps=1)
    cables(body, 1, 1.6, 1.68, zb + 2.0, zf - 1.6)
    tz = -0.5
    tur = m.part('turret', (0, 1.65, tz))
    m.set_turret(tur)
    tur.box((-1.6, 1.65, tz - 2.0), (1.6, 2.45, tz + 1.0), 'nato')
    # The sloped front faces and the cheek armour.
    for s in (-1, 1):
        tur.box((s * 0.95 - 0.65, 1.7, tz + 1.0), (s * 0.95 + 0.65, 2.35, tz + 1.55), 'nato')
        tur.box((s * 0.95 - 0.5, 1.8, tz + 1.55), (s * 0.95 + 0.5, 2.2, tz + 1.85), 'nato')
        smoke_launchers(tur, s * 1.6, 2.2, tz + 0.2, 5, s)
        bin_box(tur, s * 1.6 - (0.0 if s > 0 else 0.3), s * 1.6 + (0.3 if s > 0 else 0.0), 1.75, 2.3, tz - 1.8, tz - 0.3, 'nato')
    # The bustle with stowage baskets.
    tur.box((-1.45, 1.75, tz - 2.9), (1.45, 2.4, tz - 2.0), 'nato')
    basket(tur, -1.5, 1.5, 1.85, 2.4, tz - 3.4, tz - 2.9)
    # Commander's sight and the loader's hatch, the gunner's sight.
    tur.box((0.45, 2.45, tz - 0.5), (1.0, 2.85, tz + 0.05), 'nato', sides={'front': 'glass'})
    hatch(tur, -0.7, 2.45, tz - 0.3, 0.6, 0.6, 'nato')
    cupola(tur, -0.7, 2.45, tz - 1.1, 'nato', r=0.28, h=0.08)
    sight_box(tur, 0.6, 1.05, 2.45, 2.65, tz + 0.4, tz + 0.85, 'nato')
    antenna(tur, -1.3, 2.45, tz - 1.9, 2.2)
    el = gun(m, tur, (0, 2.05, tz + 1.85), 5.3, 0.16, paint='nato')
    m.seat('driver', 0.0, 1.05, 3.0)
    m.seat('gunner', 0.65, 2.35, tz - 0.3)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Fighting vehicles


def bmp3():
    """BMP-3: the engine at the back, a low turret with the 100 mm gun-launcher and the 30 mm cannon beside it."""
    m = Model('bmp3', paint='rgreen', seed=2203)
    m.width = 3.15
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.57, 3.57
    tracks(m, body, z0, z1, 0.85, 3.15, 0.45, 6, 0.32, 'rgreen')
    body.box((-1.1, 0.4, z0 + 0.2), (1.1, 0.9, z1 - 0.3), 'rgreen')
    body.box((-1.57, 0.9, z0), (1.57, 1.75, z1 - 1.3), 'rgreen')
    glacis(body, z1 - 1.3, z1 + 0.05, 0.6, 1.75, 1.5, 'rgreen', steps=5)
    # The splash board folded on the glacis, the rear doors under the raised engine deck.
    body.box((-1.4, 1.55, z1 - 0.95), (1.4, 1.63, z1 - 0.6), 'rgreen')
    body.box((-1.45, 1.75, z0), (1.45, 1.95, z0 + 2.2), 'rgreen')
    grille(body, -1.2, 1.2, 1.95, z0 + 0.3, z0 + 1.8)
    for s in (-1, 1):
        body.box((s * 0.55 - 0.35, 0.95, z0 - 0.07), (s * 0.55 + 0.35, 1.6, z0), 'rgreen')
        hatch(body, s * 0.6, 1.75, z0 + 2.6, 0.6, 0.8, 'rgreen')
    for k in range(3):
        periscope(body, -0.9 + k * 0.2, 1.75, z1 - 1.5, 'rgreen')
    cables(body, 1, 1.45, 1.78, z0 + 2.3, z1 - 1.6)
    skirt_panels(body, z0 + 0.5, z1 - 0.8, 0.62, 0.9, 1.575, 'rgreen', n=5, rubber=False)
    headlights(body, z1 - 0.5, 1.1, 1.2)
    tz = 0.2
    tur = m.part('turret', (0, 1.75, tz))
    m.set_turret(tur)
    turret_dome(tur, 0, tz, 1.75, 1.9, 2.1, 0.55, 'rgreen', layers=2)
    tur.box((0.35, 2.3, tz - 0.5), (0.85, 2.55, tz + 0.0), 'rgreen', sides={'front': 'glass'})
    cupola(tur, -0.6, 2.3, tz - 0.2, 'rgreen', mg=False, r=0.28, h=0.1)
    smoke_launchers(tur, 0.95, 2.05, tz + 0.4, 3, 1)
    smoke_launchers(tur, -0.95, 2.05, tz + 0.4, 3, -1)
    antenna(tur, -0.8, 2.3, tz - 0.8, 2.0)
    bin_box(tur, -0.8, 0.8, 1.85, 2.15, tz - 1.35, tz - 1.0, 'rgreen', straps=2)
    # The 100 mm 2A70 on the centre line and the 30 mm 2A72 to its right, elevating together.
    el = gun(m, tur, (0, 2.1, tz + 1.0), 2.6, 0.13, mantlet=0.55)
    barrel(el, 0.38, 2.1, tz + 1.3, 2.9, 0.07)
    m.seat('driver', 0.0, 1.15, 2.4)
    m.seat('gunner', 0.0, 2.15, tz - 0.4)
    return m


def cv90():
    """CV9030: a tall hull with the driver forward left, a big two-man turret with the 30 mm Bushmaster."""
    m = Model('cv90', paint='camo', seed=2204)
    m.width = 3.19
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.3, 3.3
    tracks(m, body, z0, z1, 0.9, 3.19, 0.48, 7, 0.32, 'camo')
    body.box((-1.1, 0.4, z0 + 0.2), (1.1, 0.95, z1 - 0.3), 'camo')
    body.box((-1.6, 0.95, z0), (1.6, 1.95, z1 - 1.2), 'camo')
    glacis(body, z1 - 1.2, z1 + 0.05, 0.7, 1.95, 1.55, 'camo', steps=4)
    skirts(body, z0 + 0.2, z1 - 0.6, 0.6, 0.95, 1.6, 'camo', rubber=False)
    body.box((-0.75, 1.0, z0 - 0.08), (0.75, 1.85, z0), 'camo')
    lights(body, z1 - 0.2, 1.15, 1.25)
    tz = -0.4
    tur = m.part('turret', (0, 1.95, tz))
    m.set_turret(tur)
    tur.box((-1.3, 1.95, tz - 1.4), (1.3, 2.65, tz + 1.0), 'camo')
    for s in (-1, 1):
        tur.box((s * 0.8 - 0.45, 2.0, tz + 1.0), (s * 0.8 + 0.45, 2.55, tz + 1.4), 'camo')
    tur.box((-1.2, 2.05, tz - 1.9), (1.2, 2.55, tz - 1.4), 'camo')
    tur.box((0.4, 2.65, tz - 0.5), (0.95, 2.95, tz + 0.0), 'camo', sides={'front': 'glass'})
    cupola(tur, -0.6, 2.65, tz - 0.3, 'camo', mg=False, r=0.3, h=0.08)
    for s in (-1, 1):
        smoke_launchers(tur, s * 1.3, 2.4, tz + 0.2, 4, s)
    basket(tur, -1.2, 1.2, 2.1, 2.55, tz - 2.3, tz - 1.9)
    antenna(tur, -1.0, 2.65, tz - 1.2, 2.1)
    grille(body, 0.3, 1.4, 1.95, z1 - 2.4, z1 - 1.4)
    headlights(body, z1 - 0.15, 1.15, 1.25)
    gun(m, tur, (0, 2.35, tz + 1.4), 2.9, 0.08, mantlet=0.5)
    m.seat('driver', -0.75, 1.3, 2.4)
    m.seat('gunner', 0.0, 2.45, tz - 0.5)
    return m


def stryker():
    """M1126 Stryker: an 8x8 with a sloped nose, slat armour sides, the remote .50 cal on the roof."""
    m = Model('stryker', paint='tan', seed=2205)
    m.width = 2.72
    m.wheelbase = 4.8
    m.camera = 10
    body = m.part('body')
    z0, z1 = -3.45, 3.45
    body.box((-1.36, 0.6, z0), (1.36, 2.15, z1 - 1.2), 'tan')
    glacis(body, z1 - 1.2, z1, 0.65, 2.15, 1.3, 'tan', steps=4)
    body.box((-1.1, 2.15, -2.8), (1.1, 2.3, 1.6), 'tan')
    body.box((-1.0, 0.8, z0 - 0.08), (1.0, 2.0, z0), 'tan')
    m.cab(-1.3, 1.3, 0.65, 2.1, 1.0, z1 - 1.2, kind='armour', hatch=False)
    # Periscopes round the driver's hatch, the side slats.
    body.box((-1.2, 2.15, z1 - 1.9), (-0.55, 2.3, z1 - 1.25), 'tan', sides={'front': 'glass'})
    for s in (-1, 1):
        body.box((s * 1.38 - (0.0 if s > 0 else 0.04), 1.0, -2.8), (s * 1.38 + (0.04 if s > 0 else 0.0), 1.9, 1.2), 'mesh_dark')
    lights(body, z1 - 0.3, 1.0, 1.0)
    lights(body, z0, 1.4, 1.0, front=False)
    tz = 0.2
    tur = m.part('turret', (0, 2.3, tz))
    m.set_turret(tur)
    rws(m, tur, 0, 2.3, tz, 'tan', caliber=0.05)
    axles(m, [2.35, 1.0, -1.0, -2.35], 0.55, 1.08, width=0.4, steer=2)
    m.seat('driver', -0.6, 1.4, 2.3)
    m.seat('gunner', 0.4, 2.2, tz - 0.6)
    return m


def tigr():
    """GAZ-2330 Tigr-M: an armoured four-by-four with a bonnet, the Kord machine gun on a roof ring."""
    m = Model('tigr', paint='rgreen', seed=2206)
    m.width = 2.4
    m.wheelbase = 3.3
    m.camera = 9
    body = m.part('body')
    z0, z1 = -2.85, 2.85
    # Bonnet, cab and the long body in one, the high windows.
    body.box((-1.2, 0.65, z1 - 1.5), (1.2, 1.55, z1), 'rgreen')
    body.box((-1.2, 0.65, z0), (1.2, 2.3, z1 - 1.5), 'rgreen')
    body.box((-1.1, 0.75, z1), (1.1, 1.2, z1 + 0.08), 'dark')
    body.box((-1.0, 1.65, z1 - 1.52), (-0.05, 2.15, z1 - 1.49), 'glass')
    body.box((0.05, 1.65, z1 - 1.52), (1.0, 2.15, z1 - 1.49), 'glass')
    for s in (-1, 1):
        body.box((s * 1.2 - (0.0 if s > 0 else 0.03), 1.65, -0.9), (s * 1.2 + (0.03 if s > 0 else 0.0), 2.1, 1.25), 'glass')
    m.cab(-1.2, 1.2, 0.65, 2.3, -0.9, z1 - 1.5, kind='armour')
    lights(body, z1 + 0.08, 1.25, 0.85)
    lights(body, z0, 1.4, 0.9, front=False)
    body.box((-0.4, 0.9, z0 - 0.15), (0.4, 1.7, z0), 'dark')
    tz = -1.2
    tur = m.part('turret', (0, 2.3, tz))
    m.set_turret(tur)
    tur.box((-0.55, 2.3, tz - 0.55), (0.55, 2.42, tz + 0.55), 'rgreen')
    tur.box((-0.45, 2.42, tz + 0.3), (0.45, 2.8, tz + 0.38), 'rgreen')
    gun(m, tur, (0, 2.65, tz + 0.38), 1.3, 0.05, mantlet=0.18)
    axles(m, [1.65, -1.65], 0.55, 0.93, width=0.38, steer=1)
    m.seat('driver', -0.5, 1.35, 0.8)
    m.seat('gunner', 0.0, 2.05, tz - 0.25)
    return m


def hmmwv():
    """M1151 HMMWV: the up-armoured Humvee with the gunner's shield and the M2 on its ring."""
    m = Model('hmmwv', paint='tan', seed=2207)
    m.width = 2.2
    m.wheelbase = 3.3
    m.camera = 8
    body = m.part('body')
    hz0, hz1 = -2.45, 2.45
    body.box((-1.09, 0.45, 0.75), (1.09, 1.15, hz1 - 0.1), 'tan')
    body.box((-0.95, 1.15, 0.75), (0.95, 1.3, hz1 - 0.15), 'tan')
    body.box((-1.0, 0.5, hz1 - 0.1), (1.0, 1.05, hz1), 'dark')
    lights(body, hz1, 0.95, 0.75)
    body.box((-1.09, 0.45, hz0), (1.09, 1.15, -1.2), 'tan')
    body.box((-1.05, 0.45, -1.2), (1.05, 1.98, 0.75), 'tan')
    body.box((-0.9, 1.3, 0.75), (-0.05, 1.9, 0.78), 'glass')
    body.box((0.05, 1.3, 0.75), (0.9, 1.9, 0.78), 'glass')
    for s in (-1, 1):
        body.box((s * 1.05 - (0.02 if s > 0 else 0.01), 1.3, -0.95), (s * 1.05 + (0.01 if s > 0 else 0.02), 1.85, 0.55), 'glass')
    m.cab(-1.05, 1.05, 0.5, 1.98, -1.2, 0.75, part='body', hatch=False)
    body.box((-1.0, 1.15, hz0), (1.0, 1.35, -1.2), 'tan')
    lights(body, hz0, 0.8, 0.8, front=False)
    tz = -0.3
    tur = m.part('turret', (0, 1.98, tz))
    m.set_turret(tur)
    # The gunner's shield: a front plate and two side wings.
    tur.box((-0.6, 1.98, tz + 0.4), (0.6, 2.6, tz + 0.48), 'tan')
    for s in (-1, 1):
        tur.box((s * 0.6 - 0.04, 1.98, tz - 0.2), (s * 0.6 + 0.04, 2.5, tz + 0.45), 'tan')
    gun(m, tur, (0, 2.35, tz + 0.48), 1.2, 0.05, mantlet=0.15)
    axles(m, [1.65, -1.65], 0.46, 0.85, width=0.36, steer=1)
    m.seat('driver', -0.45, 1.05, 0.2)
    m.seat('gunner', 0.0, 1.55, tz - 0.25)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Recovery vehicles


def brem1():
    """BREM-1: a T-72 chassis with no turret - the crane folded along the left side, a dozer blade, the 12.7 mm on top."""
    m = Model('brem1', paint='rgreen', seed=2208)
    m.width = 3.6
    m.tracked = True
    m.camera = 10
    body = m.part('body')
    zf, zb = tank_hull(m, body, 6.9, 3.6, 0.95, 1.55, 'rgreen')
    # The superstructure, the dozer blade at the front, the cargo platform behind.
    body.box((-1.5, 1.55, zf - 3.4), (1.2, 2.35, zf - 1.3), 'rgreen')
    body.box((-1.7, 0.2, zf + 0.1), (1.7, 1.0, zf + 0.35), 'dgrey')
    body.box((-1.6, 1.55, zb + 0.2), (1.6, 1.85, zf - 3.5), 'rgreen')
    body.box((-1.6, 1.85, zb + 0.2), (1.6, 2.15, zb + 0.35), 'rgreen')
    # The crane: its post at the front right, the boom laid back along the left.
    body.box((0.9, 1.55, zf - 1.3), (1.4, 2.6, zf - 0.8), 'dgrey')
    body.box((0.95, 2.45, zf - 4.6), (1.35, 2.75, zf - 0.85), 'dgrey')
    body.box((1.05, 2.0, zf - 4.6), (1.25, 2.45, zf - 4.4), 'dark')
    # Spare track links and the tow bar.
    body.box((-1.6, 1.0, zb - 0.1), (1.6, 1.4, zb), 'track')
    body.box((-0.1, 0.8, zb - 0.9), (0.1, 0.9, zb), 'dark')
    tz = zf - 2.4
    tur = m.part('turret', (-0.6, 2.35, tz))
    m.set_turret(tur)
    tur.box((-1.1, 2.35, tz - 0.45), (-0.1, 2.55, tz + 0.45), 'rgreen')
    gun(m, tur, (-0.6, 2.7, tz + 0.45), 1.25, 0.05, mantlet=0.18)
    m.seat('driver', -0.7, 1.4, zf - 1.8)
    m.seat('gunner', -0.6, 2.15, tz - 0.3)
    return m


def m88():
    """M88A2 Hercules: a tall box hull on a long track, the A-frame boom folded on the deck, the spade at the front."""
    m = Model('m88', paint='nato', seed=2209)
    m.width = 3.43
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    z0, z1 = -4.3, 4.3
    tracks(m, body, z0, z1, 0.95, 3.43, 0.6, 6, 0.38, 'nato')
    body.box((-1.1, 0.4, z0 + 0.2), (1.1, 0.95, z1 - 0.3), 'nato')
    body.box((-1.7, 0.95, z0), (1.7, 1.75, z1 - 0.6), 'nato')
    # The crew cab high at the front, the vision blocks.
    body.box((-1.65, 1.75, z1 - 3.3), (1.65, 2.75, z1 - 0.8), 'nato')
    glacis(body, z1 - 0.8, z1 + 0.05, 0.75, 2.75, 1.6, 'nato', steps=5)
    for x in (-1.2, -0.4, 0.4, 1.2):
        body.box((x - 0.2, 2.45, z1 - 0.82), (x + 0.2, 2.6, z1 - 0.79), 'glass')
    # The spade (dozer blade) at the front, the A-frame boom laid back on the deck, the winch housing.
    body.box((-1.75, 0.15, z1 + 0.05), (1.75, 1.05, z1 + 0.35), 'dgrey')
    for s in (-1, 1):
        body.box((s * 1.25 - 0.12, 1.75, z0 + 0.4), (s * 1.25 + 0.12, 1.95, z1 - 3.4), 'dgrey')
    body.box((-1.37, 1.75, z0 + 0.3), (1.37, 1.95, z0 + 0.5), 'dgrey')
    body.box((-0.9, 1.75, z1 - 4.6), (0.9, 2.2, z1 - 3.3), 'nato')
    lights(body, z1 - 0.05, 1.2, 1.3)
    lights(body, z0, 1.3, 1.3, front=False)
    tz = z1 - 2.2
    tur = m.part('turret', (0.9, 2.75, tz))
    m.set_turret(tur)
    tur.box((0.45, 2.75, tz - 0.45), (1.35, 2.95, tz + 0.45), 'nato')
    gun(m, tur, (0.9, 3.1, tz + 0.45), 1.25, 0.05, mantlet=0.18)
    m.seat('driver', -0.8, 1.75, z1 - 1.8)
    m.seat('gunner', 0.9, 2.55, tz - 0.3)
    return m


# ------------------------------------------------------------------------------------------------------------------
# Heavy flamethrower system


def tos1():
    """TOS-1A Solntsepyok: a T-72 chassis carrying a heavy armoured box of 24 tubes for thermobaric rockets."""
    m = Model('tos1', paint='rgreen', seed=2210)
    m.width = 3.6
    m.tracked = True
    m.camera = 11
    body = m.part('body')
    zf, zb = tank_hull(m, body, 6.9, 3.6, 0.95, 1.55, 'rgreen', era=6)
    tz = -0.6
    tur = m.part('turret', (0, 1.55, tz))
    m.set_turret(tur)
    tur.box((-1.1, 1.55, tz - 1.1), (1.1, 1.85, tz + 1.1), 'rgreen')
    for s in (-1, 1):
        tur.box((s * 1.25 - 0.15, 1.85, tz - 1.8), (s * 1.25 + 0.15, 2.4, tz + 0.2), 'rgreen')
    piv = (0, 2.25, tz - 1.6)
    pack = tur.part('pack', piv)
    m.set_elevator(pack, deploy=35, fixed=0)
    # The launcher: an armoured box (3 rows of 8 tubes, 220 mm), its front with the tube mouths.
    zp0, zp1 = tz - 1.9, tz + 2.9
    pack.box((-1.15, 1.95, zp0), (1.15, 2.95, zp1), 'rgreen', sides={'front': 'dark'})
    d = 0.27
    rows = 3
    cols = 8
    for r in range(rows):
        for c in range(cols):
            x = -d * 3.5 + c * d
            y = 2.13 + r * d * 1.18
            pack.box((x - 0.1, y - 0.1, zp1), (x + 0.1, y + 0.1, zp1 + 0.03), 'black')
    for c in (0, 3, 4, 7):
        for r in (0, 2):
            m.rail('pack', (-d * 3.5 + c * d, 2.13 + r * d * 1.18, zp1))
    m.seat('driver', 0.0, 1.0, 2.3)
    m.seat('gunner', 0.9, 2.05, tz + 0.8)
    return m


def all_models():
    return [t80bvm(), challenger2(), bmp3(), cv90(), stryker(), tigr(), hmmwv(), brem1(), m88(), tos1()]
