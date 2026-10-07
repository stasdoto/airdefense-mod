"""Crafting recipes of the 1.24 arms and ammunition: python3 tools/guns/recipes.py
Rifles keep the AK-74's shape (iron on top, a trigger, a stock) and differ by one part: the furniture, the optics,
a polymer (black dye), electronics (redstone) and so on, so no two recipes overlap."""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'data', 'airdefense', 'recipe')

M = {
    'I': 'minecraft:iron_ingot', 'N': 'minecraft:iron_nugget', 'T': 'minecraft:tripwire_hook', 'W': '#minecraft:planks',
    'B': 'minecraft:iron_block', 'S': 'minecraft:spyglass', 'K': 'minecraft:black_dye', 'G': 'minecraft:glass_pane',
    'R': 'minecraft:redstone', 'C': 'minecraft:copper_ingot', 'L': 'minecraft:leather', 'Y': 'minecraft:string',
    'O': 'minecraft:obsidian', 'D': 'minecraft:diamond', 'P': 'minecraft:piston', 'X': 'minecraft:tnt', 'Q': 'minecraft:quartz',
    'H': 'minecraft:iron_chain', 'E': 'minecraft:green_dye', 'U': 'minecraft:brown_dye', 'Z': 'minecraft:gold_ingot',
    'A': 'minecraft:iron_bars', 'F': 'minecraft:flint_and_steel',
}

GUNS = {
    'akm': ['III', 'TWW', 'IW '],
    'ak12': ['GII', 'TKK', 'I  '],
    'aks74u': ['II ', 'TA ', 'I  '],
    'rpk74': ['III', 'TWW', 'IIW'],
    'pkp': ['III', 'TBI', 'IAK'],
    'sv98': ['SII', 'TEE', 'IE '],
    'vss': ['SOI', 'TWW', 'I  '],
    'asval': ['IOI', 'TA ', 'I  '],
    'saiga12': ['IIC', 'TKK', 'I  '],
    'bizon': ['IIH', 'TA ', 'I  '],
    'rpg22': ['EXE', 'T  '],
    'fort12': ['II', 'TK'],
    'fort221': ['GIE', 'TEE', 'I  '],
    'm4a1': ['GII', 'TKA', 'I  '],
    'm16a4': ['SII', 'TKK', 'II '],
    'hk416': ['GIR', 'TKA', 'I  '],
    'scarh': ['GII', 'TUU', 'I  '],
    'm249': ['III', 'TBK', 'IAK'],
    'm240b': ['III', 'TBB', 'I K'],
    'm110': ['SII', 'TKK', 'IK '],
    'm82': ['SBI', 'TBK', 'I  '],
    'awm': ['SII', 'TEE', 'IEE'],
    'mp5': ['IIQ', 'TA ', 'I  '],
    'glock17': ['IK', 'T '],
    'm870': ['III', 'TPK', 'I  '],
    'm32': ['IPI', 'TAK', 'I  '],
    'at4': ['EXE', 'TE '],
    'cg84': ['IXI', 'TA ', 'K  '],
    'nlaw': ['ERE', 'TXE'],
    'javelin': ['SRD', 'IXI', 'TE '],
}

AMMO = {
    'ammo_556': (['minecraft:copper_ingot', 'minecraft:gunpowder', 'minecraft:iron_nugget', 'minecraft:iron_nugget'], 15),
    'ammo_762x39': (['minecraft:copper_ingot', 'minecraft:copper_ingot', 'minecraft:gunpowder'], 15),
    'ammo_9x39': (['minecraft:copper_ingot', 'minecraft:gunpowder', 'minecraft:gunpowder', 'minecraft:iron_ingot'], 10),
    'ammo_127': (['minecraft:copper_ingot', 'minecraft:copper_ingot', 'minecraft:gunpowder', 'minecraft:gunpowder', 'minecraft:iron_ingot'], 6),
    'ammo_12g': (['minecraft:red_dye', 'minecraft:gunpowder', 'minecraft:iron_nugget', 'minecraft:paper'], 8),
    'ammo_40mm': (['minecraft:iron_ingot', 'minecraft:gunpowder', 'minecraft:tnt'], 3),
    'cg_round': (['minecraft:iron_ingot', 'minecraft:iron_ingot', 'minecraft:tnt', 'minecraft:gunpowder', 'minecraft:copper_ingot'], 1),
    'javelin_missile': (['minecraft:iron_ingot', 'minecraft:tnt', 'minecraft:redstone', 'minecraft:redstone', 'minecraft:gold_ingot',
                         'minecraft:glass_pane'], 1),
}


def shaped(gid, pattern):
    keys = sorted({c for row in pattern for c in row if c != ' '})
    return {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
            'key': {k: M[k] for k in keys}, 'result': {'id': 'airdefense:' + gid, 'count': 1}}


def main():
    seen = {}
    for gid, pat in GUNS.items():
        sig = tuple(pat)
        assert sig not in seen, (gid, seen.get(sig))
        seen[sig] = gid
        with open(os.path.join(OUT, gid + '.json'), 'w') as f:
            json.dump(shaped(gid, pat), f, indent=2)
    for aid, (ing, n) in AMMO.items():
        with open(os.path.join(OUT, aid + '.json'), 'w') as f:
            json.dump({'type': 'minecraft:crafting_shapeless', 'category': 'equipment', 'ingredients': ing,
                       'result': {'id': 'airdefense:' + aid, 'count': n}}, f, indent=2)
    print(len(GUNS), 'guns,', len(AMMO), 'ammo')


if __name__ == '__main__':
    main()
