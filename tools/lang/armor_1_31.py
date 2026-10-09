"""1.31 "Armour" strings: the new vehicles, the repair kit, repairs. python3 tools/lang/armor_1_31.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    't80bvm': ('T-80BVM', 'Т-80БВМ', 'Т-80БВМ'),
    'challenger2': ('Challenger 2', 'Challenger 2', 'Challenger 2'),
    'bmp3': ('BMP-3', 'БМП-3', 'БМП-3'),
    'cv90': ('CV9030', 'CV9030', 'CV9030'),
    'stryker': ('M1126 Stryker', 'M1126 «Страйкер»', 'M1126 «Страйкер»'),
    'tigr': ('Tigr-M', '«Тигр-М»', '«Тигр-М»'),
    'hmmwv': ('M1151 HMMWV', 'M1151 «Хаммер»', 'M1151 «Хамві»'),
    'brem1': ('BREM-1 recovery vehicle', 'БРЭМ-1', 'БРЕМ-1'),
    'm88': ('M88A2 Hercules', 'M88A2 «Геркулес»', 'M88A2 «Геркулес»'),
    'tos1': ('TOS-1A Solntsepyok', 'ТОС-1А «Солнцепёк»', 'ТОС-1А «Солнцепьок»'),
}
SHORT = {
    't80bvm': ('T-80', 'Т-80', 'Т-80'), 'challenger2': ('CR2', 'CR2', 'CR2'), 'bmp3': ('BMP-3', 'БМП-3', 'БМП-3'),
    'cv90': ('CV90', 'CV90', 'CV90'), 'stryker': ('Stryker', 'Страйкер', 'Страйкер'), 'tigr': ('Tigr', 'Тигр', 'Тигр'),
    'hmmwv': ('HMMWV', 'Хаммер', 'Хамві'), 'brem1': ('BREM', 'БРЭМ', 'БРЕМ'), 'm88': ('M88', 'M88', 'M88'), 'tos1': ('TOS', 'ТОС', 'ТОС'),
}
S = {
    'item.airdefense.repair_kit': ('Repair kit', 'Ремкомплект', 'Ремкомплект'),
    'item.airdefense.tos_rocket': ('TOS-1A rocket (220 mm)', 'Реактивный снаряд ТОС-1А (220 мм)', 'Реактивний снаряд ТОС-1А (220 мм)'),
    'radar.airdefense.contact.tos': ('MLRS', 'РСЗО', 'РСЗВ'),
    'item.airdefense.vehicle.hint_recovery': ('Stand still: its crew mend your vehicles within 14 blocks',
                                              'Встань на месте — экипаж чинит твою технику в 14 блоках вокруг',
                                              'Стань на місці — екіпаж лагодить твою техніку в 14 блоках навколо'),
    'message.airdefense.repair.full': ('Nothing to mend here', 'Ремонт не нужен', 'Ремонт не потрібен'),
    'message.airdefense.repair.done': ('Mended: %s%% strength', 'Отремонтировано: прочность %s%%', 'Відремонтовано: міцність %s%%'),
    'guide.airdefense.page.34': (
        'REPAIRS\n\nA damaged vehicle mends:\n- with a repair kit (right-click the vehicle; 4 iron + redstone) - a quarter of its strength;\n- next to a recovery vehicle (BREM-1, M88) standing still - 3% a second;\n- by a town\'s hangar - 1% a second.\nTOS-1A: 24 thermobaric rockets at short range.',
        'РЕМОНТ\n\nПовреждённую технику можно починить:\n- ремкомплектом (правый клик по машине; 4 железа + редстоун) — четверть прочности;\n- рядом с ремонтной машиной (БРЭМ-1, M88), пока она стоит, — 3% в секунду;\n- у ангара своего города — 1% в секунду.\nТОС-1А «Солнцепёк»: 24 термобарические ракеты на короткую дальность.',
        'РЕМОНТ\n\nПошкоджену техніку можна полагодити:\n- ремкомплектом (правий клік по машині; 4 заліза + редстоун) — чверть міцності;\n- поруч із ремонтною машиною (БРЕМ-1, M88), поки вона стоїть, — 3% за секунду;\n- біля ангара свого міста — 1% за секунду.\nТОС-1А: 24 термобаричні ракети на коротку дальність.'),
}


def main():
    for i, code in enumerate(('en_us', 'ru_ru', 'uk_ua')):
        p = os.path.join(LANG, code + '.json')
        with open(p, encoding='utf-8') as f:
            d = json.load(f)
        for k, t in NAMES.items():
            d['entity.airdefense.' + k] = t[i]
            d['item.airdefense.' + k] = t[i]
        for k, t in SHORT.items():
            d['map.airdefense.short.' + k] = t[i]
        for k, t in S.items():
            d[k] = t[i]
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write('\n')
        print(code, len(d))


if __name__ == '__main__':
    main()
