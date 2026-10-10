"""1.40 "Airport" strings. python3 tools/lang/airport_1_40.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'block.airdefense.runway_light': ('Runway light', 'Огонь ВПП', 'Вогонь ЗПС'),
    'block.airdefense.windsock': ('Windsock', 'Ветроуказатель', 'Вітровказівник'),
    'block.airdefense.airport_sign': ('Airport sign', 'Вывеска аэропорта', 'Вивіска аеропорту'),
    'block.airdefense.threshold': ('Runway threshold mark', 'Метка торца ВПП', 'Мітка торця ЗПС'),
    'block.airdefense.stand': ('Aircraft stand mark', 'Метка стоянки самолёта', 'Мітка стоянки літака'),
    'entity.airdefense.airliner': ('Airliner', 'Авиалайнер', 'Авіалайнер'),
}


def main():
    for i, code in enumerate(('en_us', 'ru_ru', 'uk_ua')):
        p = os.path.join(LANG, code + '.json')
        with open(p, encoding='utf-8') as f:
            d = json.load(f)
        for k, t in NAMES.items():
            d[k] = t[i]
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write('\n')
        print(code, len(d))


if __name__ == '__main__':
    main()
