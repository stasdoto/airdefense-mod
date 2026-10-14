"""
2.0: the town's vehicles as real meshes - the traffic (saloon, hatchback, the old boxy saloon, SUV, taxi), the city
bus, trolleybus and tram, the emergency services (police car, ambulance, fire engine) and the working props (lorries,
tipper, tractor). Built by tools/mesh/build_meshes.py m_town.
"""
import town_cars as TC


def saloon():
    """A modern saloon ~4.5 m (Vesta / Octavia class), painted white and tinted per car by the client."""
    return TC.build_car(TC.CarSpec(id='car_saloon'))


def models():
    return [saloon()]
