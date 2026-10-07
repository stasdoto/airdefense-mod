"""1.25 strings: the world and the war. python3 tools/lang/world_1_25.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'building.airdefense.depot': ('Distribution warehouse', 'Распределительный склад', 'Розподільчий склад'),
    'map.airdefense.depot': ('Depot', 'Склады', 'Склади'),
    'nation.airdefense.war.why_border': ('a quarrel over the border', 'спор о границе', 'суперечка через кордон'),
    'nation.airdefense.war.news': ('News: %s and %s are at war', 'Новости: %s и %s начали войну', 'Новини: %s і %s почали війну'),
    'nation.airdefense.war.column': ('%s sends %s soldiers in %s vehicles against %s by road!', '%s ведёт на %4$s колонну: %3$s машин, %2$s солдат!',
                                     '%s веде на %4$s колону: %3$s машин, %2$s солдатів!'),
    'hud.airdefense.vehicle.keys_heli': ('W/S - nose down/up · A/D - bank · mouse - turn · Space - up · Ctrl - down · LMB - fire · %1$s - rockets',
                                         'W/S — нос вниз/вверх · A/D — крен · мышь — поворот · Пробел — вверх · Ctrl — вниз · ЛКМ — огонь · %1$s — ракеты',
                                         'W/S — ніс униз/угору · A/D — крен · миша — поворот · Пробіл — угору · Ctrl — униз · ЛКМ — вогонь · %1$s — ракети'),
    'nation.airdefense.strike.incoming': ('%s fires missiles at %s!', '%s бьёт ракетами по городу %s!', '%s б\'є ракетами по місту %s!'),
    'nation.airdefense.strike.remote': ('%s struck %s: %s missiles, %s shot down', '%s ударил по %s: ракет %s, сбито %s',
                                        '%s вдарив по %s: ракет %s, збито %s'),
    'guide.airdefense.page.14': (
        'TANKS, BOATS, AIRCRAFT\n\nGet in (RMB), aim with the mouse, LMB fires.\nBoats: RMB on water.\nHelicopter: Space/Ctrl up/down, W/S nose down/up (to fly on), A/D bank.\nPlane: W/S throttle, flies where you look.\nAll need fuel: a jerrycan, RMB on the vehicle.',
        'ТАНКИ, КАТЕРА, АВИАЦИЯ\n\nСядь (ПКМ), целься мышью, ЛКМ — огонь.\nКатер — ПКМ по воде.\nВертолёт: Пробел/Ctrl — вверх/вниз, W/S — нос вниз/вверх (лететь), A/D — крен.\nСамолёт: W/S — тяга, летит куда смотришь.\nВсей технике нужен бензин: канистра — ПКМ по машине.',
        'ТАНКИ, КАТЕРИ, АВІАЦІЯ\n\nСідай (ПКМ), цілься мишею, ЛКМ — вогонь.\nКатер — ПКМ по воді.\nГелікоптер: Пробіл/Ctrl — вгору/вниз, W/S — ніс униз/угору (летіти), A/D — крен.\nЛітак: W/S — тяга, летить куди дивишся.\nУсій техніці потрібне пальне: каністра — ПКМ по машині.'),
    'guide.airdefense.page.21': (
        'THE MAP\n\nThe tablet shows 10 km round the spawn: the countries\' borders, every town\'s and village\'s own bounds, highways and country roads, the depots.\nScroll to zoom; furthest out you see it all.',
        'КАРТА\n\nНа планшете — 10 км вокруг спавна: границы стран, границы каждого города и села, трассы, просёлки и склады.\nКолесо мыши — масштаб, на самом дальнем видна вся карта.',
        'МАПА\n\nНа планшеті — 10 км довкола спавну: кордони країн, межі кожного міста й села, траси, путівці та склади.\nКолесо миші — масштаб, на найдальшому видно всю мапу.'),
    'guide.airdefense.page.22': (
        'TOWN ARSENALS\n\nEvery town and village has its own air defence and launchers. In a war they fire at the enemy\'s towns themselves.\nFactories make missiles from iron and fuel, lorries bring them in. Stores are limited.',
        'АРСЕНАЛ ГОРОДОВ\n\nУ каждого города и села своё ПВО и пусковые. В войну они сами бьют по городам врага.\nЗаводы делают ракеты из железа и топлива, грузовики везут их на склады. Запасы ограничены.',
        'АРСЕНАЛ МІСТ\n\nУ кожного міста й села своя ППО і пускові. У війну вони самі б\'ють по містах ворога.\nЗаводи роблять ракети із заліза й пального, вантажівки везуть їх на склади. Запаси обмежені.'),
    'guide.airdefense.page.23': (
        'COLUMNS\n\nThe enemy comes by road: a car for a few men, a carrier and a lorry for more, a column led by a tank for many.\nAt the edge of town they get out, take cover, fall back when hurt, throw grenades.',
        'КОЛОННЫ\n\nВраг едет по дорогам: пара бойцов — на машине, отряд — на БМП с грузовиком, много — колонной с танком впереди.\nУ города высаживаются, прячутся за укрытиями, раненые отходят, летят гранаты.',
        'КОЛОНИ\n\nВорог їде дорогами: кілька бійців — машиною, загін — на БМП з вантажівкою, багато — колоною з танком попереду.\nБіля міста висаджуються, ховаються за укриттями, поранені відходять, летять гранати.'),
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
