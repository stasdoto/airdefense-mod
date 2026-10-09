"""1.28 "Cities of the world" strings: the town styles, the port, the guide page. python3 tools/lang/cities_1_28.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'style.airdefense.classic': ('Modern', 'Современный', 'Сучасний'),
    'style.airdefense.soviet': ('Soviet', 'Советский', 'Радянський'),
    'style.airdefense.european': ('European', 'Европейский', 'Європейський'),
    'style.airdefense.american': ('American', 'Американский', 'Американський'),
    'style.airdefense.desert': ('Desert', 'Пустынный', 'Пустельний'),
    'nation.airdefense.style': ('Style: %s', 'Стиль: %s', 'Стиль: %s'),
    'guide.airdefense.page.29': ('CITIES OF THE WORLD\n\nEvery country builds its own way: Soviet (panel blocks, dachas), European (tiled roofs, half-timbering, a church), American (brick walk-ups, suburbs, strip malls), desert (sandstone, domes, palms, a bazaar).\nA town by the sea has a port: cranes, containers, a ship, a lighthouse.',
                                 'ГОРОДА МИРА\n\nКаждая страна строит по-своему: советский стиль (панельки, дачи), европейский (черепица, фахверк, церковь), американский (кирпичные дома с пожарными лестницами, пригороды, торговые ряды), пустынный (песчаник, купола, пальмы, базар).\nУ города на берегу моря — порт: краны, контейнеры, корабль, маяк.',
                                 'МІСТА СВІТУ\n\nКожна країна будує по-своєму: радянський стиль (панельки, дачі), європейський (черепиця, фахверк, церква), американський (цегляні будинки з пожежними драбинами, передмістя, торгові ряди), пустельний (пісковик, куполи, пальми, базар).\nУ міста на березі моря — порт: крани, контейнери, корабель, маяк.'),
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
