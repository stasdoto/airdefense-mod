"""1.26 "Crew" strings: the gun sight, the periscope, the flight display, the new sounds. python3 tools/lang/crew_1_26.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'key.category.airdefense.crew': ('Crew', 'Экипаж', 'Екіпаж'),
    'key.airdefense.view': ('Sight / seat', 'Прицел / место', 'Приціл / місце'),
    'key.airdefense.zoom': ('Sight magnification', 'Кратность прицела', 'Кратність прицілу'),
    'key.airdefense.thermal': ('Thermal imager', 'Тепловизор', 'Тепловізор'),
    # The gunner's sight.
    'hud.airdefense.sight.range': ('RANGE %s m', 'Д %s м', 'Д %s м'),
    'hud.airdefense.sight.gun': ('%s  rounds %s', '%s  выстрелов %s', '%s  пострілів %s'),
    'hud.airdefense.sight.ready': ('LOADED', 'ЗАРЯЖЕНО', 'ЗАРЯДЖЕНО'),
    'hud.airdefense.sight.reloading': ('RELOADING', 'ПЕРЕЗАРЯДКА', 'ПЕРЕЗАРЯДЖАННЯ'),
    'hud.airdefense.sight.thermal': ('THERMAL', 'ТПВ', 'ТПВ'),
    'hud.airdefense.sight.day': ('DAY', 'ДЕНЬ', 'ДЕНЬ'),
    'hud.airdefense.sight.status': ('%s · armour %s%% · %s km/h · in reserve %s', '%s · броня %s%% · %s км/ч · в запасе %s',
                                    '%s · броня %s%% · %s км/год · у запасі %s'),
    'hud.airdefense.sight.keys': ('LMB - fire · %s - out of the sight · %s - magnification · %s - thermal · %s - smoke · WASD - drive',
                                  'ЛКМ — огонь · %s — из прицела · %s — кратность · %s — тепловизор · %s — дым · WASD — ехать',
                                  'ЛКМ — вогонь · %s — з прицілу · %s — кратність · %s — тепловізор · %s — дим · WASD — їхати'),
    'key.airdefense.smoke': ('Smoke grenades', 'Дымовые гранаты', 'Димові гранати'),
    'message.airdefense.vehicle.smoke': ('Smoke!', 'Дым!', 'Дим!'),
    'message.airdefense.vehicle.smoke_reload': ('Smoke grenades: reloading, %s s', 'Дымовые гранаты: перезарядка, %s с',
                                                'Димові гранати: перезаряджання, %s с'),
    'hud.airdefense.sight.driver': ('%s km/h · armour %s%%', '%s км/ч · броня %s%%', '%s км/год · броня %s%%'),
    'hud.airdefense.sight.driver_keys': ('%s - head out of the hatch', '%s — высунуться из люка', '%s — висунутися з люка'),
    # The flight display.
    'hud.airdefense.flight.kmh': ('km/h', 'км/ч', 'км/год'),
    'hud.airdefense.flight.m': ('m', 'м', 'м'),
    'hud.airdefense.flight.ms': ('m/s', 'м/с', 'м/с'),
    'hud.airdefense.flight.agl': ('R %s', 'Р %s', 'Р %s'),
    'hud.airdefense.flight.throttle': ('THR %s%%', 'РУД %s%%', 'РУД %s%%'),
    'hud.airdefense.flight.rotor': ('ROTOR %s%%', 'НВ %s%%', 'НГ %s%%'),
    'hud.airdefense.flight.ccip': ('BOMB', 'БОМБА', 'БОМБА'),
    'hud.airdefense.flight.lock': ('LOCK', 'ЗАХВАТ', 'ЗАХОПЛЕННЯ'),
    'hud.airdefense.flight.stall': ('STALL', 'СВАЛИВАНИЕ', 'ЗВАЛЮВАННЯ'),
    'hud.airdefense.flight.pull_up': ('PULL UP', 'ЗЕМЛЯ! ВВЕРХ', 'ЗЕМЛЯ! ВГОРУ'),
    'hud.airdefense.vehicle.keys_plane': ('Mouse - where to fly · W/S - throttle · A/D - bank · Space - air brake · LMB - gun · %1$s - ordnance · Shift - leave',
                                          'Мышь — куда лететь · W/S — тяга · A/D — крен · Пробел — тормоз · ЛКМ — пушка · %1$s — подвеска · Shift — выйти',
                                          'Миша — куди летіти · W/S — тяга · A/D — крен · Пробіл — гальмо · ЛКМ — гармата · %1$s — підвіска · Shift — вийти'),
    # Sounds.
    'subtitles.airdefense.rotor': ('Helicopter rotor', 'Винт вертолёта', 'Гвинт гелікоптера'),
    'subtitles.airdefense.jet': ('Jet engine', 'Реактивный двигатель', 'Реактивний двигун'),
    'subtitles.airdefense.tracks': ('Tracks clatter', 'Лязг гусениц', 'Брязкіт гусениць'),
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
