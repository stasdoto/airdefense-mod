"""1.49 "A town with a past" strings. python3 tools/lang/city_1_49.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'itemGroup.airdefense.city': ('Town life', 'Жизнь города', 'Життя міста'),
    'block.airdefense.ac_unit': ('Air conditioner', 'Кондиционер', 'Кондиціонер'),
    'block.airdefense.sat_dish': ('Satellite dish', 'Спутниковая тарелка', 'Супутникова тарілка'),
    'block.airdefense.drainpipe': ('Drainpipe', 'Водосточная труба', 'Водостічна труба'),
    'block.airdefense.drainpipe_top': ('Drainpipe funnel', 'Воронка водостока', 'Лійка водостоку'),
    'block.airdefense.drainpipe_bottom': ('Drainpipe outlet', 'Колено водостока', 'Коліно водостоку'),
    'block.airdefense.flower_box': ('Flower box', 'Ящик с цветами', 'Ящик з квітами'),
    'block.airdefense.roof_vent': ('Roof vent', 'Вентиляция на крыше', 'Вентиляція на даху'),
    'block.airdefense.tv_aerial': ('TV aerial', 'Телеантенна', 'Телеантена'),
    'block.airdefense.dumpster': ('Rubbish container', 'Мусорный контейнер', 'Сміттєвий контейнер'),
    'block.airdefense.shop_sign': ('Shop sign', 'Вывеска магазина', 'Вивіска крамниці'),
    'block.airdefense.graffiti': ('Graffiti', 'Граффити', 'Графіті'),
    'block.airdefense.notice': ('Notices', 'Объявления', 'Оголошення'),
    'block.airdefense.laundry': ('Washing on a line', 'Бельё на верёвке', 'Білизна на мотузці'),
    'block.airdefense.balcony_junk': ('Balcony junk', 'Хлам на балконе', 'Мотлох на балконі'),
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
