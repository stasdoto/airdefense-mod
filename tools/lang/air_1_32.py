"""1.32 "Aviation" strings: the AH-64 and the A-10, the enemy's air strikes, aircraft on the radar. python3 tools/lang/air_1_32.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'ah64': ('AH-64D Apache', 'AH-64D «Апач»', 'AH-64D «Апач»'),
    'a10': ('A-10C Thunderbolt II', 'A-10C «Тандерболт II»', 'A-10C «Тандерболт II»'),
}
ITEM_NAMES = {
    'ah64': ('AH-64D Apache helicopter', 'Вертолёт AH-64D «Апач»', 'Гелікоптер AH-64D «Апач»'),
    'a10': ('A-10C Thunderbolt II attack jet', 'Штурмовик A-10C «Тандерболт II»', 'Штурмовик A-10C «Тандерболт II»'),
}
SHORT = {
    'ah64': ('AH-64', 'AH-64', 'AH-64'),
    'a10': ('A-10', 'A-10', 'A-10'),
}

S = {
    'radar.airdefense.contact.heli_track': ('HELICOPTER', 'ВЕРТОЛЁТ', 'ГЕЛІКОПТЕР'),
    'radar.airdefense.contact.jet_track': ('AIRCRAFT', 'САМОЛЁТ', 'ЛІТАК'),
    'nation.airdefense.war.air': ('%s sends %s against %s - air raid!', '%s посылает %s на %s — воздушная тревога!',
                                  '%s посилає %s на %s — повітряна тривога!'),
    'guide.airdefense.page.35': (
        'ENEMY AIRCRAFT\n\nIn a war the enemy sends attack helicopters (Mi-24, Ka-52, AH-64) and jets (Su-25, A-10) at your towns: they fire rockets and the gun, drop bombs, then fly home.\nYour air defence sees them on the radar and shoots them down - a hit aircraft falls burning. Keep a Pantsir or a Gepard by your towns!',
        'АВИАЦИЯ ВРАГА\n\nНа войне враг посылает на твои города ударные вертолёты (Ми-24, Ка-52, «Апач») и штурмовики (Су-25, A-10): они бьют ракетами и из пушки, бросают бомбы и улетают домой.\nТвоё ПВО видит их на радаре и сбивает — подбитая машина падает горящей. Держи у городов «Панцирь» или «Гепард»!',
        'АВІАЦІЯ ВОРОГА\n\nНа війні ворог посилає на твої міста ударні гелікоптери (Мі-24, Ка-52, «Апач») і штурмовики (Су-25, A-10): вони б\'ють ракетами й з гармати, скидають бомби й летять додому.\nТвоя ППО бачить їх на радарі й збиває — підбита машина падає палаючи. Тримай біля міст «Панцир» або «Гепард»!'),
}


def main():
    for i, code in enumerate(('en_us', 'ru_ru', 'uk_ua')):
        p = os.path.join(LANG, code + '.json')
        with open(p, encoding='utf-8') as f:
            d = json.load(f)
        for k, t in NAMES.items():
            d['entity.airdefense.' + k] = t[i]
            d['item.airdefense.' + k] = ITEM_NAMES.get(k, t)[i]
        for k, t in SHORT.items():
            d['map.airdefense.short.' + k] = t[i]
        for k, t in S.items():
            d[k] = t[i]
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write('\n')
        print(code, len(d))


if __name__ == '__main__':
    main()
