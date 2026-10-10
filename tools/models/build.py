#!/usr/bin/env python3
"""Builds every generated model: python3 tools/models/build.py (from the repository root or anywhere)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import boxgen  # noqa: E402
import radars  # noqa: E402
import airdefense  # noqa: E402
import armor  # noqa: E402
import aircraft  # noqa: E402
import trucks  # noqa: E402
import handmade  # noqa: E402
import artillery  # noqa: E402
import armor2  # noqa: E402
import aircraft2  # noqa: E402
import navy  # noqa: E402
import drones  # noqa: E402
import services  # noqa: E402
import trains  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
MAIN = os.path.join(ROOT, 'src/main/java/com/stasdoto/airdefense/vehicle/GenGeometry.java')
CLIENT = os.path.join(ROOT, 'src/client/java/com/stasdoto/airdefense/client/vehicle/GenModels.java')
TEX = os.path.join(ROOT, 'src/main/resources/assets/airdefense/textures/entity/vehicle')
ITEMS = os.path.join(ROOT, 'src/main/resources/assets/airdefense/textures/item')


def models():
    out = []
    out += radars.all_models()
    out += airdefense.all_models()
    out += armor.all_models()
    out += aircraft.all_models()
    out += trucks.all_models()
    out += services.all_models()
    out += trains.all_models()
    out += handmade.all_models()
    out += artillery.all_models()
    out += armor2.all_models()
    out += aircraft2.all_models()
    out += navy.all_models()
    out += drones.all_models()
    return out


if __name__ == '__main__':
    boxgen.build(models(), MAIN, CLIENT, TEX, ITEMS, 'com.stasdoto.airdefense.vehicle', 'com.stasdoto.airdefense.client.vehicle')
