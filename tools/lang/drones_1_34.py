"""1.34 "Drones and EW" strings: the drone launchers, the drones, the jammers, the map marks. python3 tools/lang/drones_1_34.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'orlan': ('Orlan-10', '«Орлан-10»', '«Орлан-10»'),
    'tb2_gcs': ('Bayraktar TB2 station', 'Станция Bayraktar TB2', 'Станція Bayraktar TB2'),
    'lancet': ('Lancet', '«Ланцет»', '«Ланцет»'),
    'switchblade': ('Switchblade 600', 'Switchblade 600', 'Switchblade 600'),
    'borisoglebsk': ('Borisoglebsk-2', '«Борисоглебск-2»', '«Борисоглєбськ-2»'),
    'bukovel': ('Bukovel-AD', '«Буковель-АД»', '«Буковель-АД»'),
}
ITEM_NAMES = {
    'orlan': ('Orlan-10 reconnaissance drone complex', 'Комплекс разведывательных дронов «Орлан-10»', 'Комплекс розвідувальних дронів «Орлан-10»'),
    'tb2_gcs': ('Bayraktar TB2 ground control station', 'Наземная станция управления Bayraktar TB2', 'Наземна станція керування Bayraktar TB2'),
    'lancet': ('Lancet loitering munition launcher', 'Пусковая установка барражирующих боеприпасов «Ланцет»',
               'Пускова установка баражуючих боєприпасів «Ланцет»'),
    'switchblade': ('Switchblade 600 launcher (JLTV)', 'Пусковая установка Switchblade 600 (JLTV)', 'Пускова установка Switchblade 600 (JLTV)'),
    'borisoglebsk': ('Borisoglebsk-2 electronic warfare station', 'Комплекс РЭБ «Борисоглебск-2»', 'Комплекс РЕБ «Борисоглєбськ-2»'),
    'bukovel': ('Bukovel-AD anti-drone jammer', 'Противодроновый комплекс РЭБ «Буковель-АД»', 'Протидроновий комплекс РЕБ «Буковель-АД»'),
}
SHORT = {
    'orlan': ('ORLAN', 'ОРЛАН', 'ОРЛАН'),
    'tb2_gcs': ('TB2', 'TB2', 'TB2'),
    'lancet': ('LANCET', 'ЛАНЦЕТ', 'ЛАНЦЕТ'),
    'switchblade': ('SWBLADE', 'SWBLADE', 'SWBLADE'),
    'borisoglebsk': ('EW', 'РЭБ', 'РЕБ'),
    'bukovel': ('EW', 'РЭБ', 'РЕБ'),
}

S = {
    'item.airdefense.orlan10_drone': ('Orlan-10 drone', 'Дрон «Орлан-10»', 'Дрон «Орлан-10»'),
    'item.airdefense.tb2_drone': ('Bayraktar TB2 drone', 'Дрон Bayraktar TB2', 'Дрон Bayraktar TB2'),
    'item.airdefense.lancet_drone': ('Lancet-3 loitering munition', 'Барражирующий боеприпас «Ланцет-3»', 'Баражуючий боєприпас «Ланцет-3»'),
    'item.airdefense.switchblade_drone': ('Switchblade 600 loitering munition', 'Барражирующий боеприпас Switchblade 600',
                                          'Баражуючий боєприпас Switchblade 600'),
    'item.airdefense.maml_bomb': ('MAM-L guided bomb', 'Управляемая бомба MAM-L', 'Керована бомба MAM-L'),
    'radar.airdefense.contact.orlan10': ('ORLAN', 'ОРЛАН', 'ОРЛАН'),
    'radar.airdefense.contact.tb2': ('TB2', 'TB2', 'TB2'),
    'radar.airdefense.contact.lancet': ('LANCET', 'ЛАНЦЕТ', 'ЛАНЦЕТ'),
    'radar.airdefense.contact.switchblade': ('SWITCHBLADE', 'SWITCHBLADE', 'SWITCHBLADE'),
    'radar.airdefense.contact.maml': ('BOMB', 'БОМБА', 'БОМБА'),
    'item.airdefense.vehicle.hint_recon': (
        'Mark a point on the tablet\'s map: the drone flies there, circles and marks the enemy on the map (they glow); your guns fire twice as close round it. Then it comes back',
        'Отметь точку на карте планшета: дрон полетит туда, будет кружить и отмечать врагов на карте (они светятся); твоя артиллерия бьёт по ним вдвое точнее. Потом вернётся',
        'Познач точку на карті планшета: дрон полетить туди, кружлятиме й позначатиме ворогів на карті (вони світяться); твоя артилерія б\'є по них удвічі точніше. Потім повернеться'),
    'item.airdefense.vehicle.hint_loiter': (
        'Mark a point near the enemy on the tablet\'s map: the munition circles there, picks an enemy vehicle and dives on it',
        'Отметь точку рядом с врагом на карте планшета: боеприпас покружит там, сам выберет вражескую машину и спикирует на неё',
        'Познач точку поруч із ворогом на карті планшета: боєприпас покружляє там, сам обере ворожу машину й спікірує на неї'),
    'item.airdefense.vehicle.hint_ew': (
        'Stand still: the mast goes up and the enemy\'s drones round you lose their link - FPVs fall, loitering munitions go blind, Shaheds stray',
        'Встань на месте — мачта поднимется, и вражеские дроны вокруг потеряют связь: FPV падают, «Ланцеты» слепнут, «Шахеды» сбиваются с курса',
        'Стань на місці — щогла підніметься, і ворожі дрони навколо втратять зв\'язок: FPV падають, «Ланцети» сліпнуть, «Шахеди» збиваються з курсу'),
    'item.airdefense.ew.stats': ('Jams drones within %s blocks', 'Глушит дроны в радиусе %s блоков', 'Глушить дрони в радіусі %s блоків'),
    'hud.airdefense.vehicle.ew_on': ('EW: jamming · reach %s', 'РЭБ: глушит · радиус %s', 'РЕБ: глушить · радіус %s'),
    'hud.airdefense.vehicle.ew_off': ('EW: off (R to switch on)', 'РЭБ: выключен (R — включить)', 'РЕБ: вимкнено (R — увімкнути)'),
    'hud.airdefense.vehicle.ew_deploying': ('EW: raising the mast · stand still', 'РЭБ: поднимает мачту · стой на месте',
                                            'РЕБ: піднімає щоглу · стій на місці'),
    'message.airdefense.ew.on': ('Jammer on', 'РЭБ включён', 'РЕБ увімкнено'),
    'message.airdefense.ew.off': ('Jammer off', 'РЭБ выключен', 'РЕБ вимкнено'),
    'message.airdefense.vehicle.status_ew': ('%s: health %s%%, jammer %s, reach %s', '%s: прочность %s%%, РЭБ %s, радиус %s',
                                             '%s: міцність %s%%, РЕБ %s, радіус %s'),
    'message.airdefense.drone.jammed': ('Link lost - enemy jamming!', 'Связь потеряна — вражеский РЭБ!', 'Зв\'язок втрачено — ворожий РЕБ!'),
    'screen.airdefense.map.ew_range': ('Jamming reach: %s', 'Радиус РЭБ: %s', 'Радіус РЕБ: %s'),
    'nation.airdefense.recon.incoming': ('%s sends a reconnaissance drone (%3$s) over %2$s - their guns will fire more accurately while it is there!',
                                         '%s посылает разведывательный дрон (%3$s) к городу %2$s — пока он там, их артиллерия бьёт точнее!',
                                         '%s посилає розвідувальний дрон (%3$s) до міста %2$s — поки він там, їхня артилерія б\'є точніше!'),
    'guide.airdefense.page.37': (
        'DRONES AND EW\n\nReconnaissance (Orlan-10, TB2): mark a point on the map - the drone circles there and marks the enemy (red on the map, they glow); your guns fire twice as close. The TB2 also drops bombs.\nLoitering munitions (Lancet, Switchblade) pick a vehicle by the point themselves.\nEW (Borisoglebsk-2, Bukovel-AD) jams the enemy\'s drones round it.',
        'ДРОНЫ И РЭБ\n\nРазведка («Орлан-10», TB2): отметь точку на карте — дрон кружит там и отмечает врагов (красным на карте, они светятся); артиллерия бьёт вдвое точнее. TB2 ещё и сбрасывает бомбы.\nБарражирующие боеприпасы («Ланцет», Switchblade) сами выбирают машину у точки.\nРЭБ («Борисоглебск-2», «Буковель-АД») глушит вражеские дроны вокруг.',
        'ДРОНИ ТА РЕБ\n\nРозвідка («Орлан-10», TB2): познач точку на карті — дрон кружляє там і позначає ворогів (червоним на карті, вони світяться); артилерія б\'є вдвічі точніше. TB2 ще й скидає бомби.\nБаражуючі боєприпаси («Ланцет», Switchblade) самі обирають машину біля точки.\nРЕБ («Борисоглєбськ-2», «Буковель-АД») глушить ворожі дрони навколо.'),
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
