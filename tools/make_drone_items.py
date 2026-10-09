"""
1.34 "Drones and EW": the item models of the reconnaissance drones (Orlan-10, Bayraktar TB2), the loitering munitions
(Lancet-3, Switchblade 600) and the TB2's MAM-L bomb - 3D models in 1/16 block, nose along +z, centred on the block (the
flying entity draws the same model, scaled by its render scale: they are drawn at a fraction of their size to fit the
item model's limits) - and the item definitions of the six vehicles. Run: python3 tools/make_drone_items.py
"""
import json
import os

import make_arty_items as arty

TEX = {'grey': 'airdefense:block/light_grey', 'dgrey': 'airdefense:block/metal_grey', 'dark': 'airdefense:block/metal_dark',
       'black': 'airdefense:block/black', 'olive': 'airdefense:block/missile_olive', 'white': 'airdefense:block/missile_white',
       'tan': 'airdefense:block/tan', 'glass': 'airdefense:block/gun_glass', 'particle': 'airdefense:block/light_grey'}

DISPLAY = {
    'gui': {'rotation': [60, 135, 0], 'translation': [0, 0, 0], 'scale': [0.55, 0.55, 0.55]},
    'ground': {'rotation': [90, 0, 0], 'translation': [0, 2, 0], 'scale': [0.4, 0.4, 0.4]},
    'fixed': {'rotation': [90, 135, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.35, 0.35, 0.35]},
    'firstperson_righthand': {'rotation': [0, 0, 0], 'translation': [2, 3, 0], 'scale': [0.35, 0.35, 0.35]},
}


def b(x0, y0, z0, x1, y1, z1, tex):
    """A box given by its corners, in model units round the centre (0 = the block's middle)."""
    return arty.box((8 + x0, 8 + y0, 8 + z0), (8 + x1, 8 + y1, 8 + z1), tex)


def save(name, els):
    for e in els:
        for k in ('from', 'to'):
            for v in e[k]:
                assert -16 <= v <= 32, (name, e)
    with open(os.path.join(arty.MODELS, name + '.json'), 'w') as fh:
        json.dump({'textures': TEX, 'elements': els, 'display': DISPLAY}, fh, indent=1)
        fh.write('\n')
    arty.item(name)


def orlan10():
    """Orlan-10 at half size (render scale 2): 1.8 m long, a 3.1 m straight high wing, twin tail booms, the pusher."""
    e = [b(-0.9, -0.9, -6.5, 0.9, 0.9, 7.2, 'grey'),
         b(-0.7, -0.6, 7.2, 0.7, 0.6, 8.0, 'dgrey'),
         b(-0.5, -1.4, 4.0, 0.5, -0.9, 5.5, 'dark'),
         b(-12.4, 0.9, 0.5, 12.4, 1.3, 3.3, 'grey')]
    for s in (-1, 1):
        e.append(b(s * 3.6 - 0.25, 0.9, -9.0, s * 3.6 + 0.25, 1.3, 1.0, 'grey'))
        e.append(b(s * 3.6 - 0.15, 1.3, -9.0, s * 3.6 + 0.15, 3.6, -7.4, 'grey'))
    e.append(b(-3.8, 3.3, -9.0, 3.8, 3.6, -7.4, 'grey'))
    e.append(b(-3.0, -0.2, -7.0, 3.0, 0.2, -6.7, 'black'))
    e.append(b(-0.2, -3.0, -7.0, 0.2, 3.0, -6.7, 'black'))
    save('orlan10_drone', e)


def tb2():
    """Bayraktar TB2 at a sixth (render scale 6): 6.5 m long, 12 m span, the inverted-V tail on two booms, the pusher,
    the camera ball under the nose."""
    e = [b(-0.8, -0.7, -7.0, 0.8, 0.8, 9.5, 'grey'),
         b(-0.6, -0.5, 9.5, 0.6, 0.6, 10.6, 'grey'),
         b(-0.45, -1.4, 7.6, 0.45, -0.7, 8.6, 'dark'),
         b(-16.0, 0.6, 0.6, 16.0, 0.9, 2.4, 'grey'),
         b(-0.5, -1.5, 1.0, 0.5, -0.7, 2.4, 'dgrey')]
    for s in (-1, 1):
        e.append(b(s * 2.4 - 0.2, 0.3, -7.6, s * 2.4 + 0.2, 0.6, 2.0, 'grey'))
        e.append(b(s * 2.4 - 0.1, 0.6, -7.6, s * 2.4 + 0.1, 2.3, -6.2, 'grey'))
        e.append(b(s * 2.4 - 1.6 if s < 0 else s * 2.4, 2.1, -7.6, s * 2.4 if s < 0 else s * 2.4 + 1.6, 2.3, -6.4, 'grey'))
    e.append(b(-2.2, -0.15, -7.4, 2.2, 0.15, -7.1, 'black'))
    e.append(b(-0.15, -2.2, -7.4, 0.15, 2.2, -7.1, 'black'))
    # Two of its bombs under the wings.
    for s in (-1, 1):
        e.append(b(s * 5.0 - 0.15, 0.1, 0.8, s * 5.0 + 0.15, 0.6, 1.6, 'dark'))
        e.append(b(s * 5.0 - 0.15, -0.2, -0.2, s * 5.0 + 0.15, 0.1, 2.6, 'olive'))
    save('tb2_drone', e)


def lancet():
    """Lancet-3 at about real size (render scale 1.65): the round body, two sets of X wings, the pusher at the tail."""
    e = [b(-0.75, -0.75, -6.0, 0.75, 0.75, 6.5, 'olive'),
         b(-0.5, -0.5, 6.5, 0.5, 0.5, 7.6, 'dark'),
         b(-0.35, -0.35, 7.6, 0.35, 0.35, 8.0, 'glass')]
    for z, span in ((2.4, 4.4), (-4.4, 3.8)):
        e.append(b(-span, -0.1, z - 0.9, span, 0.1, z + 0.9, 'olive'))
        e.append(b(-0.1, -span, z - 0.9, 0.1, span, z + 0.9, 'olive'))
    e.append(b(-1.8, -0.12, -6.3, 1.8, 0.12, -6.0, 'black'))
    e.append(b(-0.12, -1.8, -6.3, 0.12, 1.8, -6.0, 'black'))
    save('lancet_drone', e)


def switchblade():
    """Switchblade 600 (render scale 1.3): the slim tube body, the two pairs of unfolded wings, the camera nose."""
    e = [b(-0.8, -0.8, -7.0, 0.8, 0.8, 7.0, 'tan'),
         b(-0.6, -0.6, 7.0, 0.6, 0.6, 8.0, 'dark'),
         b(-9.0, 0.8, 2.0, 9.0, 1.0, 3.6, 'tan'),
         b(-7.5, 0.8, -5.6, 7.5, 1.0, -4.2, 'tan'),
         b(-0.1, -2.6, -6.6, 0.1, -0.8, -5.0, 'tan'),
         b(-1.8, -0.1, -7.3, 1.8, 0.1, -7.0, 'black'),
         b(-0.1, -1.8, -7.3, 0.1, 1.8, -7.0, 'black')]
    save('switchblade_drone', e)


def maml():
    """MAM-L laser-guided bomb at real size: 1 m long, the nose seeker, four fins."""
    e = [b(-1.25, -1.25, -7.0, 1.25, 1.25, 6.0, 'olive'),
         b(-0.9, -0.9, 6.0, 0.9, 0.9, 7.6, 'dgrey'),
         b(-0.5, -0.5, 7.6, 0.5, 0.5, 8.0, 'glass'),
         b(-3.0, -0.12, -7.5, 3.0, 0.12, -4.5, 'dark'),
         b(-0.12, -3.0, -7.5, 0.12, 3.0, -4.5, 'dark'),
         b(-2.2, -0.1, 3.0, 2.2, 0.1, 4.5, 'dark'),
         b(-0.1, -2.2, 3.0, 0.1, 2.2, 4.5, 'dark')]
    save('maml_bomb', e)


if __name__ == '__main__':
    orlan10()
    tb2()
    lancet()
    switchblade()
    maml()
    for v in ('orlan', 'tb2_gcs', 'lancet', 'switchblade', 'borisoglebsk', 'bukovel'):
        arty.vehicle_item(v)
    print('ok')
