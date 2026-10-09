"""1.27 "Gear" strings: helmets, vests, pouches, the thermal imager, the radio. python3 tools/lang/gear_1_27.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'item.airdefense.helmet': ('6B47 helmet', 'Шлем 6Б47', 'Шолом 6Б47'),
    'item.airdefense.helmet_fast': ('FAST helmet', 'Шлем FAST', 'Шолом FAST'),
    'item.airdefense.nvg_helmet': ('FAST helmet with night goggles', 'Шлем FAST с ночным прибором', 'Шолом FAST з нічним приладом'),
    'item.airdefense.vest': ('Plate carrier', 'Плитоноска', 'Плитоноска'),
    'item.airdefense.vest_heavy': ('6B45 body armour', 'Бронежилет 6Б45', 'Бронежилет 6Б45'),
    'item.airdefense.pouch_mag': ('Magazine pouch', 'Подсумок для магазинов', 'Підсумок для магазинів'),
    'item.airdefense.pouch_grenade': ('Grenade pouch', 'Гранатный подсумок', 'Гранатний підсумок'),
    'item.airdefense.pouch_medkit': ('First aid pouch', 'Подсумок-аптечка', 'Підсумок-аптечка'),
    'item.airdefense.pouch_radio': ('Radio pouch', 'Подсумок с рацией', 'Підсумок з рацією'),
    'item.airdefense.thermal_monocular': ('Thermal monocular', 'Тепловизионный монокуляр', 'Тепловізійний монокуляр'),
    'pouch.airdefense.mag': ('magazines', 'магазинный', 'магазинний'),
    'pouch.airdefense.grenade': ('grenades', 'гранатный', 'гранатний'),
    'pouch.airdefense.medkit': ('first aid', 'аптечка', 'аптечка'),
    'pouch.airdefense.radio': ('radio', 'рация', 'рація'),
    'pouch.airdefense.mag.what': ('Spare magazines at hand: each pouch on the vest makes reloading 15% quicker (up to three).',
                                  'Запасные магазины под рукой: каждый подсумок на жилете — перезарядка на 15% быстрее (до трёх).',
                                  'Запасні магазини під рукою: кожен підсумок на жилеті — перезаряджання на 15% швидше (до трьох).'),
    'pouch.airdefense.grenade.what': ('B throws a grenade from the pouch without putting the gun away.',
                                      'B — бросить гранату из подсумка, не убирая оружие.',
                                      'B — кинути гранату з підсумка, не прибираючи зброю.'),
    'pouch.airdefense.medkit.what': ('H dresses a wound from the kit on the vest (uses a first aid kit from the inventory).',
                                     'H — перевязаться из аптечки на жилете (тратит аптечку из инвентаря).',
                                     'H — перев’язатися з аптечки на жилеті (витрачає аптечку з інвентарю).'),
    'pouch.airdefense.radio.what': ('Warns of missiles and drones heading your way: what, and how many seconds.',
                                    'Предупреждает о ракетах и дронах, летящих к вам: что и через сколько секунд.',
                                    'Попереджає про ракети й дрони, що летять до вас: що і через скільки секунд.'),
    'tooltip.airdefense.pouch.how': ('In the inventory: right-click a vest with it to put it on.',
                                     'В инвентаре: ПКМ этим подсумком по жилету — прицепить.',
                                     'В інвентарі: ПКМ цим підсумком по жилету — причепити.'),
    'tooltip.airdefense.vest.pouches': ('Pouches %s of %s:', 'Подсумки: %s из %s', 'Підсумки: %s з %s'),
    'tooltip.airdefense.vest.how': ('Right-click with a pouch - put it on, with an empty hand - take one off',
                                    'ПКМ подсумком — прицепить, пустой рукой — снять',
                                    'ПКМ підсумком — причепити, порожньою рукою — зняти'),
    'tooltip.airdefense.thermal_monocular': ('Hold the right button to look: warm bodies and engines glow white, day and night. Z - magnification.',
                                             'Зажмите ПКМ, чтобы смотреть: люди и моторы светятся белым днём и ночью. Z — кратность.',
                                             'Затисніть ПКМ, щоб дивитися: люди й мотори світяться білим удень і вночі. Z — кратність.'),
    'message.airdefense.vest.full': ('No room on the vest', 'На жилете нет места', 'На жилеті немає місця'),
    'message.airdefense.pouch.no_grenade_pouch': ('No grenade pouch on your vest', 'Нет гранатного подсумка на жилете',
                                                  'Немає гранатного підсумка на жилеті'),
    'message.airdefense.pouch.no_grenades': ('No grenades', 'Нет гранат', 'Немає гранат'),
    'message.airdefense.pouch.no_medkit_pouch': ('No first aid pouch on your vest', 'Нет подсумка-аптечки на жилете',
                                                 'Немає підсумка-аптечки на жилеті'),
    'message.airdefense.pouch.no_medkits': ('No first aid kits', 'Нет аптечек', 'Немає аптечок'),
    'message.airdefense.pouch.not_hurt': ('You are not hurt', 'Вы не ранены', 'Ви не поранені'),
    'message.airdefense.pouch.dressing': ('Dressing the wound...', 'Перевязка...', 'Перев’язка...'),
    'message.airdefense.pouch.dressed': ('Wound dressed', 'Перевязано', 'Перев’язано'),
    'message.airdefense.radio.incoming': ('[Radio] %s coming your way, about %s s!', '[Рация] %s на вас, примерно %s с!',
                                          '[Рація] %s на вас, приблизно %s с!'),
    'message.airdefense.radio.ballistic': ('A ballistic missile', 'Баллистическая ракета', 'Балістична ракета'),
    'message.airdefense.radio.cruise': ('A cruise missile', 'Крылатая ракета', 'Крилата ракета'),
    'message.airdefense.radio.drone': ('A drone', 'Дрон', 'Дрон'),
    'message.airdefense.radio.rocket': ('Rockets', 'Реактивные снаряды', 'Реактивні снаряди'),
    'key.airdefense.grenade': ('Grenade from the pouch', 'Граната из подсумка', 'Граната з підсумка'),
    'key.airdefense.medkit': ('First aid from the pouch', 'Аптечка из подсумка', 'Аптечка з підсумка'),
    'hud.airdefense.monocular.line': ('THERMAL %s   RANGE %s m', 'ТПВ %s   Д %s м', 'ТПВ %s   Д %s м'),
    'hud.airdefense.monocular.keys': ('%s - magnification', '%s — кратность', '%s — кратність'),
    'item.airdefense.ammo_crate': ('Ammunition crate', 'Ящик с патронами', 'Ящик з набоями'),
    'tooltip.airdefense.ammo_crate': ('Open (RMB): three magazines for every gun on your hotbar.',
                                      'Открыть (ПКМ): по три магазина ко всему оружию на панели быстрого доступа.',
                                      'Відкрити (ПКМ): по три магазини до всієї зброї на панелі швидкого доступу.'),
    'message.airdefense.ammo_crate.no_guns': ('No guns on your hotbar', 'Нет оружия на панели', 'Немає зброї на панелі'),
    'message.airdefense.ammo_crate.opened': ('Rounds for %s guns taken', 'Патроны взяты: для %s видов оружия', 'Набої взято: для %s видів зброї'),
    'subtitles.airdefense.radio': ('Radio crackles', 'Шипит рация', 'Шипить рація'),
    'guide.airdefense.page.27': ('GEAR\n\nHelmets: 6B47, FAST, FAST with night goggles (N - goggles down / up).\nVests: plate carrier, heavy 6B45 - six pouches each, seen on you.\nPouch on the cursor, right-click the vest - on; empty hand - off.',
                                 'СНАРЯЖЕНИЕ\n\nШлемы: 6Б47, FAST, FAST с ночным прибором (N — опустить / поднять).\nЖилеты: плитоноска и тяжёлый 6Б45 — по шесть подсумков, их видно на вас.\nПодсумок в руке, ПКМ по жилету — прицепить; пустой рукой — снять.',
                                 'СПОРЯДЖЕННЯ\n\nШоломи: 6Б47, FAST, FAST з нічним приладом (N — опустити / підняти).\nЖилети: плитоноска й важкий 6Б45 — по шість підсумків, їх видно на вас.\nПідсумок у руці, ПКМ по жилету — причепити; порожньою рукою — зняти.'),
    'guide.airdefense.page.28': ('POUCHES\n\nMagazines - quicker reload (15% each, up to three).\nGrenades - B: a grenade without putting the gun away.\nFirst aid - H: dress a wound in 1.5 s.\nRadio - warns of missiles and drones coming at you.\nThermal monocular: hold RMB, Z - 2.5x / 6x.',
                                 'ПОДСУМКИ\n\nМагазинный — быстрее перезарядка (15% каждый, до трёх).\nГранатный — B: граната, не убирая оружие.\nАптечка — H: перевязка за 1,5 с.\nРация — предупредит о ракетах и дронах на вас.\nТепловизионный монокуляр: держите ПКМ, Z — 2,5× / 6×.',
                                 'ПІДСУМКИ\n\nМагазинний — швидше перезаряджання (15% кожен, до трьох).\nГранатний — B: граната, не прибираючи зброю.\nАптечка — H: перев’язка за 1,5 с.\nРація — попередить про ракети й дрони на вас.\nТепловізійний монокуляр: тримайте ПКМ, Z — 2,5× / 6×.'),
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
