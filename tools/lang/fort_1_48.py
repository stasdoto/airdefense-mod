"""1.48 "Positions" strings. python3 tools/lang/fort_1_48.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'itemGroup.airdefense.fort': ('Positions', 'Позиции', 'Позиції'),
    'block.airdefense.sandbags': ('Sandbags', 'Мешки с песком', 'Мішки з піском'),
    'block.airdefense.sandbags_low': ('Sandbag parapet', 'Бруствер из мешков', 'Бруствер з мішків'),
    'block.airdefense.hedgehog': ('Czech hedgehog', 'Противотанковый ёж', 'Протитанковий їжак'),
    'block.airdefense.barbed_wire': ('Barbed wire', 'Колючая проволока', 'Колючий дріт'),
    'block.airdefense.camo_net': ('Camouflage net', 'Маскировочная сеть', 'Маскувальна сітка'),
    'item.airdefense.trench_kit': ('Trench', 'Окоп', 'Окоп'),
    'item.airdefense.position_kit': ('Firing position', 'Огневая точка', 'Вогнева точка'),
    'item.airdefense.dugout_kit': ('Dugout', 'Блиндаж', 'Бліндаж'),
    'item.airdefense.pillbox_kit': ('Pillbox', 'ДОТ', 'ДОТ'),
    'item.airdefense.revetment_kit': ('Vehicle revetment', 'Капонир для техники', 'Капонір для техніки'),
    'item.airdefense.fort_kit.hint': ('Right-click the ground: it is dug and built there, its front the way you look.',
                                      'Правый клик по земле — будет вырыто и построено там, передом туда, куда вы смотрите.',
                                      'Правий клік по землі — буде викопано й збудовано там, передом туди, куди ви дивитесь.'),
    'message.airdefense.fort.built': ('%s is ready', '%s готов(а)', '%s готовий(а)'),
    'message.airdefense.fort.no_room': ('It will not go here (water or no room)', 'Здесь не построить (вода или нет места)',
                                        'Тут не збудувати (вода або немає місця)'),
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
