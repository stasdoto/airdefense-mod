"""1.30 "Artillery" strings: the guns, the Grad, the counter-battery radars, their rounds, the map. python3 tools/lang/arty_1_30.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'msta_s': ('2S19 Msta-S', '2С19 «Мста-С»', '2С19 «Мста-С»'),
    'm109': ('M109A6 Paladin', 'M109A6 «Паладин»', 'M109A6 «Паладін»'),
    'bm21': ('BM-21 Grad', 'БМ-21 «Град»', 'БМ-21 «Град»'),
    'zoopark': ('Zoopark-1M', '«Зоопарк-1М»', '«Зоопарк-1М»'),
    'tpq36': ('AN/TPQ-36 Firefinder', 'AN/TPQ-36 Firefinder', 'AN/TPQ-36 Firefinder'),
}
ITEM_NAMES = {
    'zoopark': ('Zoopark-1M counter-battery radar', 'РЛС контрбатарейной борьбы «Зоопарк-1М»', 'РЛС контрбатарейної боротьби «Зоопарк-1М»'),
    'tpq36': ('AN/TPQ-36 counter-battery radar', 'РЛС контрбатарейной борьбы AN/TPQ-36', 'РЛС контрбатарейної боротьби AN/TPQ-36'),
}
SHORT = {
    'msta_s': ('2S19', '2С19', '2С19'),
    'm109': ('M109', 'M109', 'M109'),
    'bm21': ('BM-21', 'БМ-21', 'БМ-21'),
    'zoopark': ('1L219', '1Л219', '1Л219'),
    'tpq36': ('TPQ-36', 'TPQ-36', 'TPQ-36'),
}

S = {
    'item.airdefense.shell_152': ('152 mm shell', '152-мм снаряд', '152-мм снаряд'),
    'item.airdefense.shell_155': ('155 mm shell', '155-мм снаряд', '155-мм снаряд'),
    'item.airdefense.grad_rocket': ('Grad rocket (122 mm)', 'Реактивный снаряд «Града» (122 мм)', 'Реактивний снаряд «Граду» (122 мм)'),
    'radar.airdefense.contact.shell_152': ('SHELL', 'СНАРЯД', 'СНАРЯД'),
    'radar.airdefense.contact.shell_155': ('SHELL', 'СНАРЯД', 'СНАРЯД'),
    'radar.airdefense.contact.grad': ('MLRS', 'РСЗО', 'РСЗВ'),
    'subtitles.airdefense.arty': ('Howitzer fires', 'Выстрел гаубицы', 'Постріл гаубиці'),
    'subtitles.airdefense.whistle': ('Shell incoming', 'Свист летящего снаряда', 'Свист снаряда, що летить'),
    'item.airdefense.vehicle.hint_artillery': ('Mark a target on the tablet\'s map, pick the gun and press "Fire!" - a fire mission lands round it',
                                               'Отметь цель на карте планшета, выбери орудие и нажми «Огонь!» — снаряды лягут вокруг цели',
                                               'Познач ціль на карті планшета, обери гармату й натисни «Вогонь!» — снаряди ляжуть навколо цілі'),
    'item.airdefense.vehicle.hint_cb': ('Stand still facing the enemy: it finds the guns that fire at you and marks them on the map',
                                        'Встань лицом к противнику: найдёт стреляющие по тебе батареи и отметит их на карте',
                                        'Стань обличчям до ворога: знайде батареї, що стріляють по тобі, і позначить їх на карті'),
    'item.airdefense.arty.stats': ('Range %s blocks · %s rounds · fire mission of %s',
                                   'Дальность %s бл. · боекомплект %s · залп %s',
                                   'Дальність %s бл. · боєкомплект %s · залп %s'),
    'message.airdefense.arty.auto': ('Counter-battery fire: on - it answers the enemy guns your radar finds',
                                     'Ответный огонь: включён — сама бьёт по батареям, которые нашёл твой радар',
                                     'Вогонь у відповідь: увімкнено — сама б\'є по батареях, які знайшов твій радар'),
    'message.airdefense.arty.manual': ('Counter-battery fire: off - it fires only on your orders',
                                       'Ответный огонь: выключен — стреляет только по твоей команде',
                                       'Вогонь у відповідь: вимкнено — стріляє лише за твоєю командою'),
    'message.airdefense.cb.found': ('Counter-battery radar: enemy guns firing %s blocks away, bearing %s° (%s %s) - marked on the map',
                                    'Радар контрбатарейной борьбы: стреляет батарея противника — %s бл., азимут %s° (%s %s), отмечена на карте',
                                    'Радар контрбатарейної боротьби: стріляє батарея противника — %s бл., азимут %s° (%s %s), позначена на карті'),
    'hud.airdefense.vehicle.arty': ('ROUNDS %s of %s · answer fire: off', 'БОЕКОМПЛЕКТ %s из %s · ответный огонь: выкл',
                                    'БОЄКОМПЛЕКТ %s з %s · вогонь у відповідь: вимк'),
    'hud.airdefense.vehicle.arty_cb': ('ROUNDS %s of %s · answer fire: on', 'БОЕКОМПЛЕКТ %s из %s · ответный огонь: вкл',
                                       'БОЄКОМПЛЕКТ %s з %s · вогонь у відповідь: увімк'),
    'screen.airdefense.map.cb_on': ('Reply: on', 'Ответ: вкл', 'Відповідь: увімк'),
    'screen.airdefense.map.cb_off': ('Reply: off', 'Ответ: выкл', 'Відповідь: вимк'),
    'screen.airdefense.map.rounds': ('Rounds: %s', 'Снарядов: %s', 'Снарядів: %s'),
    'screen.airdefense.map.st.cb': (' · answers fire', ' · отвечает на огонь', ' · відповідає вогнем'),
    'screen.airdefense.map.st.ready_rounds': ('ready · rounds: %s', 'готова · снарядов: %s', 'готова · снарядів: %s'),
    'screen.airdefense.map.sec': ('s', 'с', 'с'),
    'screen.airdefense.map.min': (' min', ' мин', ' хв'),
    'screen.airdefense.map.fire_pos': ('enemy guns · %s ago', 'огневая позиция · %s назад', 'вогнева позиція · %s тому'),
    'guide.airdefense.page.32': (
        'ARTILLERY\n\nHowitzers (2S19 Msta-S, M109 Paladin) and the BM-21 Grad fire from far off at a point on the map: pick the gun on the tablet\'s map, mark the target, choose how many rounds, press "Fire!".\nShells fall in a spread round the target. You hear them whistle before they land.',
        'АРТИЛЛЕРИЯ\n\nГаубицы («Мста-С», «Паладин») и «Град» бьют издалека по точке на карте: выбери орудие на карте планшета, отметь цель, выбери, сколько снарядов, и нажми «Огонь!».\nСнаряды ложатся с разбросом вокруг цели. Перед падением слышен свист.',
        'АРТИЛЕРІЯ\n\nГаубиці («Мста-С», «Паладін») і «Град» б\'ють здалеку по точці на карті: обери гармату на карті планшета, познач ціль, обери, скільки снарядів, і натисни «Вогонь!».\nСнаряди лягають із розкидом навколо цілі. Перед падінням чути свист.'),
    'guide.airdefense.page.33': (
        'COUNTER-BATTERY\n\nA counter-battery radar (Zoopark-1M, AN/TPQ-36) finds the guns that fire at you and marks them on the map with a red cross.\nYour guns with "answer fire" on hit back by themselves. The enemy can do the same: after firing, drive off!',
        'КОНТРБАТАРЕЙНАЯ БОРЬБА\n\nРадар контрбатарейной борьбы («Зоопарк-1М», AN/TPQ-36) находит стреляющие по тебе батареи и ставит на карте красный крест.\nТвои орудия с включённым «ответным огнём» сами бьют в ответ. Враг умеет так же: отстрелялся — уезжай!',
        'КОНТРБАТАРЕЙНА БОРОТЬБА\n\nРадар контрбатарейної боротьби («Зоопарк-1М», AN/TPQ-36) знаходить батареї, що стріляють по тобі, і ставить на карті червоний хрест.\nТвої гармати з увімкненим «вогнем у відповідь» самі б\'ють у відповідь. Ворог уміє так само: відстрілявся — їдь!'),
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
