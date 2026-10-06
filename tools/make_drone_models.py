"""Item models (3D, in 1/16 block) for the FPV drone and the Magura sea drone."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/airdefense/models/item')
UV = [0, 0, 16, 16]


def el(a, b, tex, rot=None):
    e = {"from": list(a), "to": list(b),
         "faces": {f: {"texture": "#" + tex, "uv": UV} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


def save(name, elements, display):
    textures = {"black": "airdefense:block/black", "metal_dark": "airdefense:block/metal_dark", "light_grey": "airdefense:block/light_grey",
                "olive": "airdefense:block/missile_olive", "yellow": "airdefense:block/yellow", "red": "airdefense:block/red_band",
                "grey": "airdefense:block/metal_grey", "particle": "airdefense:block/black"}
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump({"textures": textures, "elements": elements, "display": display}, f, indent=1)


def fpv():
    e = []
    # Frame plate, battery strapped on top, the camera in front, an RPG grenade slung underneath.
    e.append(el((6.5, 7.6, 5), (9.5, 8.4, 11), 'black'))
    e.append(el((6.8, 8.4, 6), (9.2, 9.8, 9.6), 'yellow'))
    e.append(el((7.3, 8.4, 10), (8.7, 9.6, 11.2), 'metal_dark'))
    e.append(el((7.2, 6.2, 6.5), (8.8, 7.6, 13.5), 'olive'))
    e.append(el((7.5, 6.5, 13.5), (8.5, 7.3, 15.5), 'metal_dark'))
    # Two crossed arms.
    for ang in (45, -45):
        e.append(el((7.6, 7.8, 2.5), (8.4, 8.3, 13.5), 'black', {"origin": [8, 8, 8], "axis": "y", "angle": ang}))
    # Motors and propellers at the four tips.
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx = 8 + sx * 3.9
            cz = 8 + sz * 3.9
            e.append(el((cx - 0.7, 7.6, cz - 0.7), (cx + 0.7, 9.0, cz + 0.7), 'metal_dark'))
            e.append(el((cx - 2.4, 9.0, cz - 0.35), (cx + 2.4, 9.15, cz + 0.35), 'light_grey',
                        {"origin": [cx, 9, cz], "axis": "y", "angle": 22.5 * sx * sz}))
    display = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
               "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
               "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
               "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 2], "scale": [0.5, 0.5, 0.5]},
               "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [2, 4, 0], "scale": [0.6, 0.6, 0.6]}}
    save('fpv_drone', e, display)


def magura():
    e = []
    # A low grey speedboat hull, pointed bow, a small mast with the camera and antennas, the jet at the stern.
    e.append(el((5, 6, -8), (11, 9, 14), 'grey'))
    e.append(el((5.8, 6.2, 14), (10.2, 8.8, 18), 'grey'))
    e.append(el((6.8, 6.5, 18), (9.2, 8.5, 21), 'grey'))
    e.append(el((5, 6, -8), (11, 6.6, 21), 'black'))
    e.append(el((6, 9, -2), (10, 9.8, 8), 'metal_dark'))
    e.append(el((7.4, 9.8, 4), (8.6, 12.5, 5.2), 'black'))
    e.append(el((7.2, 12.5, 4.2), (8.8, 13.3, 5.6), 'metal_dark'))
    e.append(el((6.2, 9.8, -4), (6.6, 14, -3.6), 'black'))
    e.append(el((9.4, 9.8, -4), (9.8, 13, -3.6), 'black'))
    e.append(el((6.5, 6.5, -9.5), (9.5, 8.5, -8), 'metal_dark'))
    display = {"gui": {"rotation": [30, 135, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
               "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
               "fixed": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
               "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.3, 0.3, 0.3]},
               "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [2, 3, 0], "scale": [0.3, 0.3, 0.3]}}
    save('magura_drone', e, display)


fpv()
magura()
for n in ('fpv_drone', 'magura_drone'):
    with open(os.path.join(OUT, '..', '..', 'items', n + '.json'), 'w') as f:
        json.dump({"model": {"type": "minecraft:model", "model": "airdefense:item/" + n}}, f, indent=2)
print('ok')
