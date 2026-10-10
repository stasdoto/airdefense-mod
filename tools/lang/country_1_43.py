"""1.43 "Choosing a country" strings. python3 tools/lang/country_1_43.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'key.category.airdefense.country': ('Country', 'Страна', 'Країна'),
    'key.airdefense.country': ('Choose a country', 'Выбор страны', 'Вибір країни'),
    'screen.airdefense.country.title': ('Choose your country', 'Выберите страну', 'Оберіть країну'),
    'screen.airdefense.country.subtitle': ('You will play for it: its towns, army and wars become yours. Friends can choose the same one.',
                                           'Вы будете играть за неё: её города, армия и войны станут вашими. Друзья могут выбрать ту же страну.',
                                           'Ви гратимете за неї: її міста, армія та війни стануть вашими. Друзі можуть обрати ту саму країну.'),
    'screen.airdefense.country.later': ('Later', 'Позже', 'Пізніше'),
    'screen.airdefense.country.close': ('Close', 'Закрыть', 'Закрити'),
    'screen.airdefense.country.alone': ('No country', 'Без страны', 'Без країни'),
    'screen.airdefense.country.travel': ('Go to the capital', 'Перенестись в столицу', 'Перенестися до столиці'),
    'screen.airdefense.country.play': ('Play for %s', 'Играть за «%s»', 'Грати за «%s»'),
    'screen.airdefense.country.yours': ('Your country', 'Это ваша страна', 'Це ваша країна'),
    'screen.airdefense.country.none': ('Pick a country', 'Выберите страну', 'Оберіть країну'),
    'screen.airdefense.country.empty': ('The countries are still being drawn up - open this again in a few seconds (K).',
                                        'Страны ещё составляются — откройте это окно снова через несколько секунд (K).',
                                        'Країни ще складаються — відкрийте це вікно знову за кілька секунд (K).'),
    'screen.airdefense.country.chip_yours': ('yours', 'ваша', 'ваша'),
    'screen.airdefense.country.chip_players': ('players: %s', 'игроков: %s', 'гравців: %s'),
    'screen.airdefense.country.chip_war': ('at war', 'война', 'війна'),
    'screen.airdefense.country.card_line': ('%s - %s towns - %s people', '%s · городов: %s · %s чел.', '%s · міст: %s · %s осіб'),
    'screen.airdefense.country.ruler': ('Ruler: %s, %s', 'Правитель: %s, %s', 'Правитель: %s, %s'),
    'screen.airdefense.country.players': ('Played by: %s', 'Играют: %s', 'Грають: %s'),
    'screen.airdefense.country.facts': ('Capital %s - %s towns - %s people - %s style', 'Столица %s · городов: %s · жителей: %s · стиль: %s',
                                        'Столиця %s · міст: %s · жителів: %s · стиль: %s'),
    'screen.airdefense.country.peace': ('At peace with everyone', 'Ни с кем не воюет', 'Ні з ким не воює'),
    'screen.airdefense.country.wars': ('At war with: %s', 'Воюет с: %s', 'Воює з: %s'),
    'screen.airdefense.country.with_allies': ('%s - allies: %s', '%s · союзников: %s', '%s · союзників: %s'),
    'screen.airdefense.country.map_wait': ('The map is being drawn...', 'Карта рисуется…', 'Мапа малюється…'),
    'screen.airdefense.country.map_hint': ('Wheel: zoom - drag: move - click: pick', 'Колесо — масштаб · тащите — сдвиг · клик — выбор',
                                           'Коліщатко — масштаб · тягніть — зсув · клік — вибір'),
    'screen.airdefense.country.you': ('You', 'Вы', 'Ви'),
    'nation.airdefense.choice.ruler': ('You now rule %s. Its towns, army and wars are yours.',
                                       'Теперь вы правитель страны «%s». Её города, армия и войны — ваши.',
                                       'Тепер ви правитель країни «%s». Її міста, армія та війни — ваші.'),
    'nation.airdefense.choice.member': ('You now play for %s, together with %s.', 'Теперь вы играете за «%s» вместе с %s.',
                                        'Тепер ви граєте за «%s» разом із %s.'),
    'nation.airdefense.choice.alone': ('You play on your own: your country will come with the first town you win.',
                                       'Вы играете сами за себя: своя страна появится с первым городом, который вы получите.',
                                       'Ви граєте самі за себе: своя країна з’явиться з першим містом, яке ви отримаєте.'),
    'nation.airdefense.choice.arrived': ('Welcome to %s, your capital. You will come back here after death.',
                                         'Добро пожаловать в %s — вашу столицу. После гибели вы будете возрождаться здесь.',
                                         'Ласкаво просимо до %s — вашої столиці. Після загибелі ви відроджуватиметесь тут.'),
    'nation.airdefense.choice.friend_joined': ('%s now plays for %s with you', '%s теперь играет за «%s» вместе с вами',
                                               '%s тепер грає за «%s» разом із вами'),
    'nation.airdefense.choice.friend_left': ('%s no longer plays for %s', '%s больше не играет за «%s»', '%s більше не грає за «%s»'),
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
