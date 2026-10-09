"""1.33 "Navy" strings: the warships, the coastal launchers, the anti-ship missiles, naval raids. python3 tools/lang/navy_1_33.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'buyan_m': ('Buyan-M small missile ship', 'МРК «Буян-М»', 'МРК «Буян-М»'),
    'visby': ('Visby corvette', 'Корвет «Висбю»', 'Корвет «Вісбю»'),
    'bastion': ('Bastion-P', '«Бастион-П»', '«Бастіон-П»'),
    'nmesis': ('NMESIS', 'NMESIS', 'NMESIS'),
}
ITEM_NAMES = {
    'bastion': ('Bastion-P coastal missile system', 'Береговой ракетный комплекс «Бастион-П»', 'Береговий ракетний комплекс «Бастіон-П»'),
    'nmesis': ('NMESIS coastal anti-ship launcher', 'Береговой противокорабельный комплекс NMESIS', 'Береговий протикорабельний комплекс NMESIS'),
}
SHORT = {
    'buyan_m': ('BUYAN-M', 'БУЯН-М', 'БУЯН-М'),
    'visby': ('VISBY', 'ВИСБЮ', 'ВІСБЮ'),
    'bastion': ('BASTION', 'БАСТИОН', 'БАСТІОН'),
    'nmesis': ('NMESIS', 'NMESIS', 'NMESIS'),
}

S = {
    'item.airdefense.oniks_missile': ('P-800 Oniks anti-ship missile', 'Противокорабельная ракета П-800 «Оникс»', 'Протикорабельна ракета П-800 «Онікс»'),
    'item.airdefense.nsm_missile': ('Naval Strike Missile', 'Противокорабельная ракета NSM', 'Протикорабельна ракета NSM'),
    'item.airdefense.rbs15_missile': ('RBS15 anti-ship missile', 'Противокорабельная ракета RBS15', 'Протикорабельна ракета RBS15'),
    'radar.airdefense.contact.oniks': ('ANTI-SHIP', 'ПКР', 'ПКР'),
    'radar.airdefense.contact.nsm': ('ANTI-SHIP', 'ПКР', 'ПКР'),
    'radar.airdefense.contact.rbs15': ('ANTI-SHIP', 'ПКР', 'ПКР'),
    'item.airdefense.vehicle.hint_ship': ('Right-click the water to put her to sea. The gunner aims the gun with the mouse; cruise missiles strike from the tablet\'s map; her own close-in gun fires at drones and missiles',
                                          'ПКМ по воде — спустить на воду. Наводчик целится мышью; крылатые ракеты — удар с карты планшета; своя скорострельная пушка сама бьёт по дронам и ракетам',
                                          'ПКМ по воді — спустити на воду. Навідник цілиться мишею; крилаті ракети — удар з карти планшета; власна скорострільна гармата сама б\'є по дронах і ракетах'),
    'item.airdefense.vehicle.hint_coastal': ('Mark an enemy ship (or a point near it) on the tablet\'s map: the missile finds the ship by itself',
                                             'Отметь вражеский корабль (или точку рядом) на карте планшета — ракета сама найдёт корабль',
                                             'Познач ворожий корабель (або точку поруч) на карті планшета — ракета сама знайде корабель'),
    'message.airdefense.ship.water': ('A warship goes on the water: right-click the water\'s surface', 'Корабль спускают на воду: ПКМ по воде',
                                      'Корабель спускають на воду: ПКМ по воді'),
    'hud.airdefense.vehicle.ship': ('GUN %s mm · %s: %s of %s', 'ОРУДИЕ %s мм · %s: %s из %s', 'ГАРМАТА %s мм · %s: %s з %s'),
    'message.airdefense.vehicle.status_ship': ('%s: health %s%%, %s mm gun, %s: %s of %s, fuel %s/%s l',
                                               '%s: прочность %s%%, орудие %s мм, %s: %s из %s, топливо %s/%s л',
                                               '%s: міцність %s%%, гармата %s мм, %s: %s з %s, пальне %s/%s л'),
    'nation.airdefense.war.navy': ('%s sends %s against %s - warship off the coast!', '%s посылает %s к городу %s — вражеский корабль у берега!',
                                   '%s посилає %s до міста %s — ворожий корабель біля берега!'),
    'guide.airdefense.page.36': (
        'THE NAVY\n\nWarships (Buyan-M, Visby) carry a gun, eight cruise missiles and a close-in gun of their own. Put one on the water, take the gunner\'s seat and aim with the mouse; missiles go from the tablet\'s map.\nCoastal launchers (Bastion-P, NMESIS) sink ships: mark the ship on the map. In a war the enemy\'s ships come to your towns by the sea.',
        'ФЛОТ\n\nБоевые корабли («Буян-М», «Висбю»): пушка, восемь крылатых ракет и своя скорострельная пушка от дронов и ракет. Спусти корабль на воду, сядь наводчиком и целься мышью; ракеты — с карты планшета.\nБереговые комплексы («Бастион-П», NMESIS) топят корабли: отметь корабль на карте. На войне враг посылает корабли к твоим приморским городам.',
        'ФЛОТ\n\nБойові кораблі («Буян-М», «Вісбю»): гармата, вісім крилатих ракет і власна скорострільна гармата від дронів і ракет. Спусти корабель на воду, сядь навідником і цілься мишею; ракети — з карти планшета.\nБерегові комплекси («Бастіон-П», NMESIS) топлять кораблі: познач корабель на карті. На війні ворог посилає кораблі до твоїх приморських міст.'),
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
