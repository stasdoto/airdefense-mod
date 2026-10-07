"""1.25 strings: the world and the war. python3 tools/lang/world_1_25.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'building.airdefense.depot': ('Distribution warehouse', 'Распределительный склад', 'Розподільчий склад'),
    'map.airdefense.depot': ('Depot', 'Склады', 'Склади'),
    'nation.airdefense.war.why_border': ('a quarrel over the border', 'спор о границе', 'суперечка через кордон'),
    'nation.airdefense.war.news': ('News: %s and %s are at war', 'Новости: %s и %s начали войну', 'Новини: %s і %s почали війну'),
    'nation.airdefense.strike.incoming': ('%s fires missiles at %s!', '%s бьёт ракетами по городу %s!', '%s б\'є ракетами по місту %s!'),
    'nation.airdefense.strike.remote': ('%s struck %s: %s missiles, %s shot down', '%s ударил по %s: ракет %s, сбито %s',
                                        '%s вдарив по %s: ракет %s, збито %s'),
}


def main():
    for i, code in enumerate(('en_us', 'ru_ru', 'uk_ua')):
        p = os.path.join(LANG, code + '.json')
        with open(p, encoding='utf-8') as f:
            d = json.load(f)
        for k, t in S.items():
            d[k] = t[i]
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write('\n')
        print(code, len(d))


if __name__ == '__main__':
    main()
