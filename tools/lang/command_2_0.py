"""2.0 "command any country" strings: the map's command panel, the orders' messages, the game rule. python3 tools/lang/command_2_0.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    # The orders.
    'nation.airdefense.command.not_allowed': (
        'Commanding other countries is off here (game rule airdefense:command_any_country; on a server: operators and creative players)',
        'Управлять чужими странами здесь нельзя (правило игры airdefense:command_any_country; на сервере — операторы и игроки в креативе)',
        'Керувати чужими країнами тут не можна (правило гри airdefense:command_any_country; на сервері — оператори й гравці в креативі)'),
    'nation.airdefense.command.no_country': ('That country is no more', 'Этой страны больше нет', 'Цієї країни більше немає'),
    'nation.airdefense.command.taken': (
        'You command %s now: its air defence, launchers and radars take your orders from the map.',
        'Теперь вы командуете страной %s: её ПВО, пусковые и радары слушают ваши приказы с карты.',
        'Тепер ви командуєте країною %s: її ППО, пускові й радари слухають ваші накази з мапи.'),
    'nation.airdefense.command.released': ('Back to your own country.', 'Вы вернулись к своей стране.', 'Ви повернулися до своєї країни.'),
    'nation.airdefense.command.no_towns': ('%s has no towns', 'У страны %s нет городов', 'У країни %s немає міст'),
    'nation.airdefense.command.too_far': (
        'Too far from the towns of %s (%s blocks, at most %s)', 'Слишком далеко от городов страны %s (%s блоков, можно до %s)',
        'Надто далеко від міст країни %s (%s блоків, можна до %s)'),
    'nation.airdefense.command.full': ('%s has no room for more vehicles', 'В городе %s больше нет места для техники',
                                       'У місті %s більше немає місця для техніки'),
    'nation.airdefense.command.placed': (
        '%s: %s ordered to %s - it rolls out when somebody is near', '%s: %s — приказ отдан в %s, выедет, когда рядом кто-то будет',
        '%s: %s — наказ віддано в %s, виїде, коли поруч хтось буде'),
    'nation.airdefense.command.placed_now': ('%s: %s is up by %s', '%s: %s развёрнут у города %s', '%s: %s розгорнуто біля міста %s'),
    'nation.airdefense.command.strike_none': (
        'No launcher of %s reaches %s - put launchers closer', 'Ни одна пусковая страны %s не достаёт до %s — поставьте пусковые ближе',
        'Жодна пускова країни %s не дістає до %s — поставте пускові ближче'),
    'nation.airdefense.command.strike_empty': ('The launchers of %s are empty', 'Пусковые страны %s пусты', 'Пускові країни %s порожні'),
    'nation.airdefense.command.strike': ('%s fires %s rounds at %s', '%s выпускает %s ракет по %s', '%s випускає %s ракет по %s'),
    'nation.airdefense.command.pick_other': (
        'Pick another country: click one of its towns on the map', 'Выберите другую страну: нажмите на карте на один из её городов',
        'Виберіть іншу країну: натисніть на мапі на одне з її міст'),
    'nation.airdefense.command.why': ('by order of %s', 'по приказу %s', 'за наказом %s'),
    'nation.airdefense.command.not_at_war': ('%s and %s are not at war', '%s и %s не воюют', '%s і %s не воюють'),
    'nation.airdefense.command.belongs': ('%s belongs to %s now', '%s теперь принадлежит стране %s', '%s тепер належить країні %s'),
    # The map's command panel.
    'screen.airdefense.map.cmd.command': ('Command this country', 'Управлять страной', 'Керувати країною'),
    'screen.airdefense.map.cmd.command_short': ('Command', 'Управлять', 'Керувати'),
    'screen.airdefense.map.cmd.commanding': ('YOU COMMAND THIS COUNTRY', 'ВЫ КОМАНДУЕТЕ ЭТОЙ СТРАНОЙ', 'ВИ КОМАНДУЄТЕ ЦІЄЮ КРАЇНОЮ'),
    'screen.airdefense.map.cmd.waiting': ('Taking command...', 'Принимаем командование…', 'Приймаємо командування…'),
    'screen.airdefense.map.cmd.free': ('Creative: everything is free', 'Креатив: всё бесплатно', 'Креатив: усе безкоштовно'),
    'screen.airdefense.map.cmd.stores': ('Stores: wood %s, stone %s, iron %s', 'Склады: дерево %s, камень %s, железо %s',
                                         'Склади: дерево %s, камінь %s, залізо %s'),
    'screen.airdefense.map.cmd.forces': ('Air defence %s · launchers %s · radars %s', 'ПВО %s · пусковых %s · радаров %s',
                                         'ППО %s · пускових %s · радарів %s'),
    'screen.airdefense.map.cmd.at_peace': ('At peace with everyone', 'Ни с кем не воюет', 'Ні з ким не воює'),
    'screen.airdefense.map.cmd.at_war': ('At war with: %s', 'Воюет с: %s', 'Воює з: %s'),
    'screen.airdefense.map.cmd.place_ad': ('Place air defence', 'Поставить ПВО', 'Поставити ППО'),
    'screen.airdefense.map.cmd.place_launchers': ('Place launchers', 'Поставить пусковые', 'Поставити пускові'),
    'screen.airdefense.map.cmd.place_radars': ('Place radars', 'Поставить радары', 'Поставити радари'),
    'screen.airdefense.map.cmd.strike': ('Strike at...', 'Ударить по...', 'Вдарити по...'),
    'screen.airdefense.map.cmd.war': ('Declare war', 'Объявить войну', 'Оголосити війну'),
    'screen.airdefense.map.cmd.peace': ('Make peace', 'Заключить мир', 'Укласти мир'),
    'screen.airdefense.map.cmd.back_own': ('Back to my country', 'Вернуться к своей стране', 'Повернутися до своєї країни'),
    'screen.airdefense.map.cmd.back': ('Back', 'Назад', 'Назад'),
    'screen.airdefense.map.cmd.here': ('Place here', 'Поставить здесь', 'Поставити тут'),
    'screen.airdefense.map.cmd.auto': ('Auto (near the borders)', 'Авто (у границ)', 'Авто (біля кордонів)'),
    'screen.airdefense.map.cmd.cost': ('Cost: %s wood, %s stone, %s iron', 'Цена: %s дерева, %s камня, %s железа',
                                       'Ціна: %s дерева, %s каменю, %s заліза'),
    'screen.airdefense.map.cmd.pick_spot': ('Click the map: where to put it', 'Нажмите на карту — где поставить',
                                            'Натисніть на мапу — де поставити'),
    'screen.airdefense.map.cmd.auto_hint': ('or Auto: by a town near the borders', 'или «Авто» — у города ближе к границе',
                                            'або «Авто» — біля міста ближче до кордону'),
    'screen.airdefense.map.cmd.any': ('Any launchers', 'Любые пусковые', 'Будь-які пускові'),
    'screen.airdefense.map.cmd.rounds': ('Rounds: %s', 'Ракет: %s', 'Ракет: %s'),
    'screen.airdefense.map.cmd.fire': ('Fire!', 'Огонь!', 'Вогонь!'),
    'screen.airdefense.map.cmd.pick_target': ('Click the map: a point or a town', 'Нажмите на карту: точка или город',
                                              'Натисніть на мапу: точка або місто'),
    'screen.airdefense.map.cmd.in_reach': ('Launchers in reach: %s', 'Пусковых в досягаемости: %s', 'Пускових у досяжності: %s'),
    'screen.airdefense.map.cmd.other': ('Other country: %s', 'Другая страна: %s', 'Інша країна: %s'),
    'screen.airdefense.map.cmd.other_war': ('At war with %s', 'Воюет со страной %s', 'Воює з країною %s'),
    'screen.airdefense.map.cmd.pick_other': ('Click a town of another country', 'Нажмите на город другой страны',
                                             'Натисніть на місто іншої країни'),
    # The game rule.
    'gamerule.airdefense.command_any_country': (
        'Air Defense: players may command any country from the map', 'ПВО: игроки могут управлять любой страной с карты',
        'ППО: гравці можуть керувати будь-якою країною з мапи'),
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
