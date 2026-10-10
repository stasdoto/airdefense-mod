"""1.39 "Roads" strings: the railway blocks, the trains. python3 tools/lang/roads_1_39.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'block.airdefense.track': ('Railway track', 'Железнодорожный путь', 'Залізнична колія'),
    'block.airdefense.buffer_stop': ('Buffer stop', 'Тупиковый упор', 'Тупиковий упор'),
    'block.airdefense.station_sign': ('Station sign', 'Табличка станции', 'Табличка станції'),
    'entity.airdefense.train': ('Train', 'Поезд', 'Потяг'),
    'subtitles.airdefense.train_horn': ('Train hoots', 'Гудит поезд', 'Гуде потяг'),
    'subtitles.airdefense.train_clack': ('Train wheels clatter', 'Стучат колёса поезда', 'Стукотять колеса потяга'),
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
