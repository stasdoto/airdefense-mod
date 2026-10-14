"""2.0 vehicle sounds: subtitles. python3 tools/lang/vehicles_2_0.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'subtitles.airdefense.veh_engine': ('Car engine', 'Работает мотор машины', 'Працює мотор машини'),
    'subtitles.airdefense.veh_diesel': ('Diesel engine', 'Тарахтит дизель', 'Торохтить дизель'),
    'subtitles.airdefense.veh_tank_engine': ('Tank engine roars', 'Ревёт двигатель танка', 'Реве двигун танка'),
    'subtitles.airdefense.veh_turbine': ('Turbine whines', 'Свистит турбина', 'Свистить турбіна'),
    'subtitles.airdefense.veh_electric': ('Electric motor hums', 'Гудит электромотор', 'Гуде електромотор'),
    'subtitles.airdefense.veh_road': ('Tyres on the road', 'Шуршат шины', 'Шурхотять шини'),
    'subtitles.airdefense.veh_tyre_squeal': ('Tyres squeal', 'Визжат шины', 'Вищать шини'),
    'subtitles.airdefense.veh_reverse_beep': ('Reversing beeper', 'Пищит задний ход', 'Пищить задній хід'),
    'subtitles.airdefense.veh_shift': ('Gear changes', 'Переключается передача', 'Перемикається передача'),
    'subtitles.airdefense.veh_turbo': ('Turbo whooshes', 'Шипит турбина наддува', 'Шипить турбіна наддуву'),
    'subtitles.airdefense.veh_brake_squeal': ('Brakes squeal', 'Скрипят тормоза', 'Скриплять гальма'),
    'subtitles.airdefense.veh_air_brake': ('Air brakes hiss', 'Шипят пневмотормоза', 'Шиплять пневмогальма'),
    'subtitles.airdefense.veh_horn': ('Horn honks', 'Сигналит машина', 'Сигналить машина'),
    'subtitles.airdefense.veh_door_open': ('Car door opens', 'Открывается дверь машины', 'Відчиняються двері машини'),
    'subtitles.airdefense.veh_door_close': ('Car door shuts', 'Хлопает дверь машины', 'Грюкають двері машини'),
    'subtitles.airdefense.veh_bus_door': ('Bus doors hiss', 'Шипят двери автобуса', 'Шиплять двері автобуса'),
    'subtitles.airdefense.veh_thump': ('Suspension thumps', 'Стучит подвеска', 'Стукає підвіска'),
    'subtitles.airdefense.veh_start': ('Engine starts', 'Заводится двигатель', 'Заводиться двигун'),
    'subtitles.airdefense.veh_stop': ('Engine stops', 'Глохнет двигатель', 'Глухне двигун'),
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
