"""1.50 "A living town": the town's own traffic - cars of several kinds and colours (a saloon, a hatchback, the old boxy
Soviet saloon, an SUV, a taxi), a city bus, a trolleybus with its poles up on the wires, a tram. Metres, the front at
+z, wheels on the ground at y = 0. Drawn by the client only (client.nation.PropRenderer): no items."""
from boxgen import Model
from kit import lights

CAR_PAINTS = ['car_white', 'car_red', 'car_blue', 'car_green', 'car_beige', 'car_cherry', 'car_black', 'car_silver']


def wheels(body, zs, r, half, w=0.24):
    for z in zs:
        for s in (-1, 1):
            body.box((s * half - w / 2, 0.0, z - r), (s * half + w / 2, 2 * r, z + r), 'tire')
            body.box((s * (half + w / 2) - (0.01 if s > 0 else -0.01) - 0.005, r - 0.12, z - 0.12),
                     (s * (half + w / 2) + 0.005, r + 0.12, z + 0.12), 'chrome')


def car_body(body, paint, length, width, y0, belt, roof, cab_back, cab_front, hood_drop=0.0, boot_drop=0.0):
    """A car of boxes: the lower body (bumper to bumper), the bonnet and the boot, the cabin with its glass."""
    hl = length / 2
    hw = width / 2
    body.box((-hw, y0, -hl), (hw, belt, hl), paint)
    if hood_drop:
        body.box((-hw + 0.02, belt, cab_front), (hw - 0.02, belt + 0.06 - hood_drop, hl - 0.05), paint)
    # The cabin: pillars and roof in the paint, glass all round.
    body.box((-hw + 0.08, belt, cab_back), (hw - 0.08, roof, cab_front), 'window')
    body.box((-hw + 0.06, roof - 0.06, cab_back + 0.05), (hw - 0.06, roof, cab_front - 0.05), paint)
    for z in (cab_back, cab_front - 0.08):
        for s in (-1, 1):
            body.box((s * (hw - 0.08) - 0.04, belt, z), (s * (hw - 0.08) + 0.04, roof, z + 0.08), paint)
    # Bumpers, the grille, lights, plates.
    body.box((-hw, y0 - 0.02, hl), (hw, y0 + 0.16, hl + 0.06), 'dark')
    body.box((-hw, y0 - 0.02, -hl - 0.06), (hw, y0 + 0.16, -hl), 'dark')
    body.box((-hw * 0.5, y0 + 0.2, hl), (hw * 0.5, belt - 0.08, hl + 0.03), 'dark')
    lights(body, hl + 0.03, belt - 0.2, hw - 0.18)
    lights(body, -hl - 0.03, belt - 0.2, hw - 0.15, front=False)
    body.box((-0.22, y0 + 0.2, hl + 0.06), (0.22, y0 + 0.31, hl + 0.07), 'white')
    body.box((-0.22, y0 + 0.25, -hl - 0.07), (0.22, y0 + 0.36, -hl - 0.06), 'white')
    # Mirrors.
    for s in (-1, 1):
        body.box((s * (hw + 0.08) - 0.06, belt + 0.05, cab_front - 0.25), (s * (hw + 0.08) + 0.06, belt + 0.17, cab_front - 0.15), paint)


def saloon(paint):
    m = Model('car_saloon_' + paint[4:], paint=paint, tex_width=512, seed=5001 + CAR_PAINTS.index(paint))
    m.no_item = True
    body = m.part('body')
    car_body(body, paint, 4.5, 1.78, 0.28, 0.92, 1.42, -1.05, 0.75)
    wheels(body, (1.4, -1.35), 0.31, 0.78)
    return m


def hatch(paint):
    m = Model('car_hatch_' + paint[4:], paint=paint, tex_width=512, seed=5101 + CAR_PAINTS.index(paint))
    m.no_item = True
    body = m.part('body')
    car_body(body, paint, 3.9, 1.72, 0.28, 0.9, 1.45, -1.75, 0.55)
    wheels(body, (1.2, -1.25), 0.3, 0.76)
    return m


def boxy(paint):
    """The old boxy saloon: square lines, chrome bumpers, a tall glasshouse."""
    m = Model('car_boxy_' + paint[4:], paint=paint, tex_width=512, seed=5201 + CAR_PAINTS.index(paint))
    m.no_item = True
    body = m.part('body')
    car_body(body, paint, 4.15, 1.62, 0.3, 0.88, 1.42, -0.95, 0.75)
    body.box((-0.81, 0.28, 2.075), (0.81, 0.42, 2.14), 'chrome')
    body.box((-0.81, 0.28, -2.14), (0.81, 0.42, -2.075), 'chrome')
    wheels(body, (1.25, -1.2), 0.3, 0.7)
    return m


def suv(paint):
    m = Model('car_suv_' + paint[4:], paint=paint, tex_width=512, seed=5301 + CAR_PAINTS.index(paint))
    m.no_item = True
    body = m.part('body')
    car_body(body, paint, 4.7, 1.9, 0.4, 1.12, 1.78, -1.95, 0.8)
    body.box((-0.8, 1.78, -1.6), (-0.74, 1.85, 0.5), 'dark')
    body.box((0.74, 1.78, -1.6), (0.8, 1.85, 0.5), 'dark')
    wheels(body, (1.5, -1.45), 0.38, 0.84, 0.28)
    return m


def taxi():
    m = Model('car_taxi', paint='taxi_yellow', tex_width=512, seed=5400)
    m.no_item = True
    body = m.part('body')
    car_body(body, 'taxi_yellow', 4.5, 1.78, 0.28, 0.92, 1.42, -1.05, 0.75)
    for s in (-1, 1):
        for k in range(8):
            body.box((s * 0.89 - (0.0 if s > 0 else 0.01), 0.62, -1.4 + k * 0.4), (s * 0.89 + (0.01 if s > 0 else 0.0), 0.72, -1.2 + k * 0.4),
                     'dark' if k % 2 == 0 else 'white')
    body.box((-0.25, 1.42, -0.3), (0.25, 1.6, -0.05), 'light')
    wheels(body, (1.4, -1.35), 0.31, 0.78)
    return m


def bus_body(body, paint, stripe, length, roof=2.95):
    hl = length / 2
    hw = 1.25
    body.box((-hw, 0.35, -hl), (hw, roof, hl), paint)
    # The windows' band down both sides, the windscreen, the back window.
    for s in (-1, 1):
        x = s * hw
        body.box((x - (0.0 if s > 0 else 0.02), 1.3, -hl + 0.6), (x + (0.02 if s > 0 else 0.0), 2.55, hl - 1.4), 'window')
        body.box((x - (0.0 if s > 0 else 0.02), 0.85, -hl + 0.2), (x + (0.02 if s > 0 else 0.0), 1.05, hl - 0.2), stripe)
    body.box((-hw + 0.1, 1.1, hl), (hw - 0.1, 2.65, hl + 0.03), 'window')
    body.box((-hw + 0.3, 1.6, -hl - 0.03), (hw - 0.3, 2.5, -hl), 'window')
    # Doors on the right side (dark glass, a frame).
    for z in (hl - 1.0, 0.4, -hl + 2.0):
        body.box((hw, 0.4, z - 0.6), (hw + 0.03, 2.6, z + 0.6), 'dark')
        body.box((hw + 0.03, 0.9, z - 0.5), (hw + 0.04, 2.5, z + 0.5), 'window')
    # The route board over the windscreen.
    body.box((-0.9, 2.68, hl), (0.9, 2.88, hl + 0.04), 'screen_amber')
    lights(body, hl + 0.03, 0.55, hw - 0.25)
    lights(body, -hl - 0.03, 0.6, hw - 0.2, front=False)
    body.box((-hw, 0.3, hl - 0.05), (hw, 0.5, hl + 0.08), 'dark')
    body.box((-hw, 0.3, -hl - 0.08), (hw, 0.5, -hl + 0.05), 'dark')


def bus():
    m = Model('town_bus', paint='bus_yellow', tex_width=1024, seed=5500)
    m.no_item = True
    body = m.part('body')
    bus_body(body, 'bus_yellow', 'white', 11.0)
    body.box((-1.0, 2.95, -4.8), (1.0, 3.25, -3.2), 'dgrey')
    wheels(body, (3.6, -2.6), 0.5, 1.05, 0.3)
    return m


def trolleybus():
    m = Model('trolleybus', paint='trolley_blue', tex_width=1024, seed=5501)
    m.no_item = True
    body = m.part('body')
    bus_body(body, 'trolley_blue', 'white', 11.6)
    body.box((-0.9, 2.95, -2.5), (0.9, 3.3, 3.0), 'dgrey')
    wheels(body, (3.8, -2.7), 0.5, 1.05, 0.3)
    # The two poles, from the back of the roof up and back to the wires (about 6.8 m over the road).
    for s in (-0.25, 0.25):
        pole = body.part('pole_%s' % ('l' if s < 0 else 'r'), pivot=(s, 3.3, -2.3), rot=(-46, 0, 0))
        pole.box((s - 0.04, 3.27, -2.3 - 4.95), (s + 0.04, 3.35, -2.3), 'dark')
        pole.box((s - 0.07, 3.24, -2.3 - 5.1), (s + 0.07, 3.38, -2.3 - 4.9), 'dgrey')
    return m


def tram():
    m = Model('tram', paint='tram_red', tex_width=1024, seed=5502)
    m.no_item = True
    body = m.part('body')
    bus_body(body, 'tram_red', 'bus_cream', 14.0, roof=3.1)
    body.box((-1.25, 1.05, -6.9), (1.25, 1.3, 6.9), 'bus_cream')
    # The pantograph: a frame up to the wire.
    body.box((-0.7, 3.1, -0.4), (0.7, 3.25, 0.4), 'dgrey')
    arm = body.part('pantograph', pivot=(0, 3.25, 0.0), rot=(-57, 0, 0))
    arm.box((-0.05, 3.2, -4.2), (0.05, 3.3, 0.0), 'dark')
    arm.box((-0.6, 3.15, -4.3), (0.6, 3.3, -4.1), 'dgrey')
    for z in (4.6, 3.4, -3.4, -4.6):
        for s in (-1, 1):
            body.box((s * 0.75 - 0.12, 0.0, z - 0.42), (s * 0.75 + 0.12, 0.84, z + 0.42), 'tire')
    body.box((-1.2, 0.1, -7.0), (1.2, 0.35, 7.0), 'dark')
    return m


def all_models():
    out = []
    for p in CAR_PAINTS:
        out.append(saloon(p))
    for p in ('car_white', 'car_red', 'car_blue', 'car_green', 'car_silver'):
        out.append(hatch(p))
    for p in ('car_beige', 'car_cherry', 'car_white', 'car_blue', 'car_green'):
        out.append(boxy(p))
    for p in ('car_black', 'car_silver', 'car_white'):
        out.append(suv(p))
    out += [taxi(), bus(), trolleybus(), tram()]
    return out
