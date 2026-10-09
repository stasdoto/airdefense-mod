"""1.35 "The look of the towns" strings: the street furniture, the town outlines. python3 tools/lang/street_1_35.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

BLOCKS = {
    'pole_steel': ('Steel lamp post pole', 'Стальная опора', 'Сталева опора'),
    'pole_black': ('Black pole', 'Чёрная опора', 'Чорна опора'),
    'pole_green': ('Cast-iron pole', 'Чугунная опора', 'Чавунна опора'),
    'pole_concrete': ('Concrete pole', 'Бетонная опора', 'Бетонна опора'),
    'lamp_modern': ('LED street lamp', 'Светодиодный фонарь', 'Світлодіодний ліхтар'),
    'lamp_cobra': ('"Cobra" street lamp', 'Фонарь «кобра»', 'Ліхтар «кобра»'),
    'lamp_lantern': ('Lantern', 'Фонарь-фонарик', 'Ліхтар-ліхтарик'),
    'lamp_globe': ('Park globe lamp', 'Парковый фонарь-шар', 'Парковий ліхтар-куля'),
    'traffic_light': ('Traffic light', 'Светофор', 'Світлофор'),
    'sign_stop': ('Stop sign', 'Знак «Стоп»', 'Знак «Стоп»'),
    'sign_give_way': ('Give way sign', 'Знак «Уступи дорогу»', 'Знак «Дай дорогу»'),
    'sign_crossing': ('Pedestrian crossing sign', 'Знак «Пешеходный переход»', 'Знак «Пішохідний перехід»'),
    'sign_no_parking': ('No parking sign', 'Знак «Стоянка запрещена»', 'Знак «Стоянку заборонено»'),
    'sign_speed': ('Speed limit 40 sign', 'Знак «Ограничение 40»', 'Знак «Обмеження 40»'),
    'sign_main_road': ('Main road sign', 'Знак «Главная дорога»', 'Знак «Головна дорога»'),
    'sign_bus': ('Bus stop sign', 'Знак остановки автобуса', 'Знак зупинки автобуса'),
    'bench_park': ('Park bench', 'Парковая скамейка', 'Паркова лавка'),
    'bench_soviet': ('Concrete bench', 'Бетонная скамейка', 'Бетонна лавка'),
    'bench_modern': ('Modern bench', 'Современная скамейка', 'Сучасна лавка'),
    'bin_soviet': ('Concrete litter urn', 'Бетонная урна', 'Бетонна урна'),
    'bin_modern': ('Litter bin', 'Мусорный бак', 'Смітник'),
    'bin_euro': ('Green litter bin', 'Зелёная урна на столбике', 'Зелена урна на стовпчику'),
    'bus_stop_modern': ('Bus shelter', 'Остановка', 'Зупинка'),
    'bus_stop_soviet': ('Soviet bus stop with a mosaic', 'Советская остановка с мозаикой', 'Радянська зупинка з мозаїкою'),
    'hydrant': ('Fire hydrant', 'Пожарный гидрант', 'Пожежний гідрант'),
    'mailbox_us': ('Mailbox', 'Почтовый ящик (американский)', 'Поштова скринька (американська)'),
    'mailbox_euro': ('Post box', 'Почтовый ящик (европейский)', 'Поштова скринька (європейська)'),
    'mailbox_soviet': ('Post box (blue)', 'Почтовый ящик «Почта»', 'Поштова скринька «Пошта»'),
    'booth_red': ('Red telephone booth', 'Красная телефонная будка', 'Червона телефонна будка'),
    'booth_soviet': ('Telephone booth', 'Телефонная будка', 'Телефонна будка'),
    'advert_column': ('Advertising column', 'Афишная тумба', 'Афішна тумба'),
    'bollard': ('Bollard', 'Столбик-ограничитель', 'Стовпчик-обмежувач'),
    'planter': ('Planter', 'Вазон с кустом', 'Вазон із кущем'),
    'bike_rack': ('Bike rack', 'Велопарковка', 'Велопарковка'),
    'manhole': ('Manhole cover', 'Канализационный люк', 'Каналізаційний люк'),
    'kiosk': ('Newspaper kiosk', 'Газетный киоск', 'Газетний кіоск'),
    'vending': ('Drinks machine', 'Автомат с напитками', 'Автомат із напоями'),
    'billboard': ('Billboard', 'Рекламный щит', 'Рекламний щит'),
}

S = {
    'itemGroup.airdefense.street': ('Street', 'Улица', 'Вулиця'),
    'form.airdefense.blob': ('free', 'свободная', 'вільна'),
    'form.airdefense.round': ('round', 'круглая', 'кругла'),
    'form.airdefense.square': ('square', 'квадратная', 'квадратна'),
    'form.airdefense.diamond': ('diamond', 'ромб', 'ромб'),
    'form.airdefense.cross': ('cross', 'крест', 'хрест'),
    'form.airdefense.star': ('star', 'звезда', 'зірка'),
    'form.airdefense.ring': ('ring round a park', 'кольцо вокруг парка', 'кільце навколо парку'),
    'form.airdefense.crescent': ('horseshoe', 'подкова', 'підкова'),
    'form.airdefense.linear': ('along one street', 'вдоль одной улицы', 'уздовж однієї вулиці'),
    'form.airdefense.ell': ('L-shaped', 'буквой Г', 'літерою Г'),
    'form.airdefense.twin': ('two halves', 'из двух половин', 'з двох половин'),
}


def main():
    for i, code in enumerate(('en_us', 'ru_ru', 'uk_ua')):
        p = os.path.join(LANG, code + '.json')
        with open(p, encoding='utf-8') as f:
            d = json.load(f)
        for k, t in BLOCKS.items():
            d['block.airdefense.' + k] = t[i]
        for k, t in S.items():
            d[k] = t[i]
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write('\n')
        print(code, len(d))


if __name__ == '__main__':
    main()
