"""1.25.1 strings: fixes. python3 tools/lang/fixes_1_25_1.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'hud.airdefense.drone.pilot': ('Mouse - where to fly · W - throttle · S - slow · %s - leave',
                                   'Мышь — куда лететь · W — газ · S — тормоз · %s — выйти',
                                   'Миша — куди летіти · W — газ · S — гальмо · %s — вийти'),
    'hud.airdefense.drone.battery': ('BAT  %s%%', 'АКБ  %s%%', 'АКБ  %s%%'),
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
