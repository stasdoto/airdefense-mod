"""
1.33 "Navy": the item definitions of the warships and coastal launchers, and the anti-ship missiles (P-800 Oniks, NSM,
RBS15) - these reuse the Kalibr's 3D model, scaled in flight. Run: python3 tools/make_navy_items.py
"""
import json
import os

import make_arty_items as arty


def reuse(name, parent):
    with open(os.path.join(arty.MODELS, name + '.json'), 'w') as fh:
        json.dump({'parent': 'airdefense:item/' + parent}, fh, indent=2)
        fh.write('\n')
    arty.item(name)


if __name__ == '__main__':
    for v in ('buyan_m', 'visby', 'bastion', 'nmesis'):
        arty.vehicle_item(v)
    for m in ('oniks_missile', 'nsm_missile', 'rbs15_missile'):
        reuse(m, 'kalibr_missile')
    print('ok')
