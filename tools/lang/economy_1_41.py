"""1.41 "Economy" strings. python3 tools/lang/economy_1_41.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'block.airdefense.crane_base': ('Tower crane footing', 'Основание башенного крана', 'Основа баштового крана'),
    'entity.airdefense.prop': ('Working machine', 'Рабочая машина', 'Робоча машина'),
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
