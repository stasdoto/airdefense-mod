"""1.38 "Services" strings: the fire engine, the ambulance, the police car. python3 tools/lang/services_1_38.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'entity.airdefense.fire_truck': ('Fire engine', 'Пожарная машина', 'Пожежна машина'),
    'entity.airdefense.ambulance': ('Ambulance', 'Скорая помощь', 'Швидка допомога'),
    'entity.airdefense.police_car': ('Police car', 'Полицейская машина', 'Поліцейська машина'),
    'item.airdefense.fire_truck': ('Fire engine', 'Пожарная машина', 'Пожежна машина'),
    'item.airdefense.ambulance': ('Ambulance', 'Скорая помощь', 'Швидка допомога'),
    'item.airdefense.police_car': ('Police car', 'Полицейская машина', 'Поліцейська машина'),
    'map.airdefense.short.fire_truck': ('FIRE', 'ПОЖАРН', 'ПОЖЕЖН'),
    'map.airdefense.short.ambulance': ('AMB', 'СКОРАЯ', 'ШВИДКА'),
    'map.airdefense.short.police_car': ('POLICE', 'ПОЛИЦИЯ', 'ПОЛІЦІЯ'),
    'item.airdefense.vehicle.hint_service': (
        'The towns send these themselves: the fire engine puts out fires, the ambulance and the police come to a blast. You can drive one too',
        'Города сами высылают такие машины: пожарная тушит пожары, скорая и полиция едут на место взрыва. Можно сесть и поехать самому',
        'Міста самі надсилають такі машини: пожежна гасить пожежі, швидка й поліція їдуть на місце вибуху. Можна сісти й поїхати самому'),
    'subtitles.airdefense.service_siren': ('Siren wails', 'Воет сирена', 'Виє сирена'),
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
