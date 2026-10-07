"""1.24 strings: Iron Dome and the air raid sirens. python3 tools/lang/sirens_1_24.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'block.airdefense.siren': ('Air raid siren', 'Сирена воздушной тревоги', 'Сирена повітряної тривоги'),
    'block.airdefense.siren_mast': ('Siren mast', 'Мачта сирены', 'Щогла сирени'),
    'tooltip.airdefense.siren': ('On the ground it goes up on a 6 m mast; sneak to put just the siren (on a roof)',
                                 'На земле встаёт на мачту 6 м; с Shift — только сирена (например, на крышу)',
                                 'На землі стає на щоглу 6 м; із Shift — лише сирена (наприклад, на дах)'),
    'message.airdefense.siren.status': ('Siren: %s · now: %s', 'Сирена: %s · сейчас: %s', 'Сирена: %s · зараз: %s'),
    'screen.airdefense.map.sirens': ('Air raid alert', 'Тревога', 'Тривога'),
    'screen.airdefense.siren.title': ('Air raid warning', 'Воздушная тревога', 'Повітряна тривога'),
    'screen.airdefense.siren.all_alert': ('Alert everywhere', 'Тревога везде', 'Тривога всюди'),
    'screen.airdefense.siren.all_clear': ('All clear everywhere', 'Отбой везде', 'Відбій всюди'),
    'screen.airdefense.siren.silence': ('Silence all', 'Выключить все', 'Вимкнути всі'),
    'screen.airdefense.siren.back': ('Back to the map', 'Назад к карте', 'Назад до мапи'),
    'screen.airdefense.siren.everywhere': ('ALERT EVERYWHERE', 'ТРЕВОГА ВЕЗДЕ', 'ТРИВОГА ВСЮДИ'),
    'screen.airdefense.siren.towns': ('Towns', 'Города и посёлки', 'Міста й селища'),
    'screen.airdefense.siren.sirens': ('Sirens near you', 'Сирены рядом с вами', 'Сирени поруч із вами'),
    'screen.airdefense.siren.town_line': ('%s · sirens: %s · %s m', '%s · сирен: %s · %s м', '%s · сирен: %s · %s м'),
    'screen.airdefense.siren.siren_line': ('%s, %s · %s m · %s', '%s, %s · %s м · %s', '%s, %s · %s м · %s'),
    'screen.airdefense.siren.alert': ('Alert', 'Тревога', 'Тривога'),
    'screen.airdefense.siren.clear': ('All clear', 'Отбой', 'Відбій'),
    'screen.airdefense.siren.mode.0': ('Auto', 'Авто', 'Авто'),
    'screen.airdefense.siren.mode.1': ('On', 'Вкл', 'Увімк'),
    'screen.airdefense.siren.mode.2': ('Off', 'Выкл', 'Вимк'),
    'screen.airdefense.siren.signal.off': ('quiet', 'тихо', 'тихо'),
    'screen.airdefense.siren.signal.alert': ('ALERT', 'ТРЕВОГА', 'ТРИВОГА'),
    'screen.airdefense.siren.signal.clear': ('all clear', 'отбой', 'відбій'),
    'screen.airdefense.siren.no_towns': ('No towns yet', 'Городов пока нет', 'Міст поки немає'),
    'screen.airdefense.siren.no_sirens': ('No sirens yet: place one from the "Missiles & Air Defense" tab',
                                          'Сирен пока нет: поставьте из вкладки «Ракеты и ПВО»',
                                          'Сирен поки немає: поставте з вкладки «Ракети й ППО»'),
    'screen.airdefense.siren.field': ('Out in the field', 'Вне города', 'Поза містом'),
    'screen.airdefense.siren.hint': ('Auto: the siren follows its town - alerts from here, and by itself when a raid comes or the air defence opens fire',
                                     'Авто: сирена слушается своего города — тревога отсюда или сама, когда летит налёт или стреляет ПВО',
                                     'Авто: сирена слухається свого міста — тривога звідси або сама, коли летить наліт чи стріляє ППО'),
    'subtitles.airdefense.siren': ('Air raid siren wails', 'Воет сирена воздушной тревоги', 'Виє сирена повітряної тривоги'),
    'subtitles.airdefense.siren_clear': ('All clear sounds', 'Сирена: отбой', 'Сирена: відбій'),
    'subtitles.airdefense.siren_start': ('Siren spins up', 'Сирена раскручивается', 'Сирена розкручується'),
    'subtitles.airdefense.siren_stop': ('Siren winds down', 'Сирена затихает', 'Сирена затихає'),
    'entity.airdefense.iron_dome': ('Iron Dome', 'Железный купол', 'Залізний купол'),
    'item.airdefense.iron_dome': ('Iron Dome air defence (Tamir)', 'ЗРК «Железный купол» (Тамир)', 'ЗРК «Залізний купол» (Тамір)'),
    'map.airdefense.short.iron_dome': ('Dome', 'Купол', 'Купол'),
    'entity.airdefense.elm2084': ('EL/M-2084', 'EL/M-2084', 'EL/M-2084'),
    'item.airdefense.elm2084': ('EL/M-2084 radar', 'РЛС EL/M-2084', 'РЛС EL/M-2084'),
    'map.airdefense.short.elm2084': ('2084', '2084', '2084'),
    'item.airdefense.tamir_missile': ('Tamir interceptor', 'Ракета «Тамир»', 'Ракета «Тамір»'),
    'radar.airdefense.contact.tamir': ('Tamir', 'ЗУР «Тамир»', 'ЗКР «Тамір»'),
    'guide.airdefense.page.20': (
        'IRON DOME AND SIRENS\n\nIron Dome: 20 Tamirs, best against rockets, shells and drones. It lets what falls in empty fields go.\n\nSirens: on the tablet map - "Air raid alert". Alert or all clear in a town, everywhere, or one siren. They also sound by themselves when a raid comes.',
        'КУПОЛ И СИРЕНЫ\n\n«Железный купол»: 20 «Тамиров», лучше всего по ракетам РСЗО, снарядам и дронам. То, что падает в пустое поле, пропускает.\n\nСирены: на карте планшета — «Тревога». Тревога или отбой в городе, везде или у одной сирены. При налёте воют сами.',
        'КУПОЛ І СИРЕНИ\n\n«Залізний купол»: 20 «Тамірів», найкраще по ракетах РСЗВ, снарядах і дронах. Те, що падає в порожнє поле, пропускає.\n\nСирени: на мапі планшета — «Тривога». Тривога чи відбій у місті, всюди або в однієї сирени. Під час нальоту виють самі.'),
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
