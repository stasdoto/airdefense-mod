"""1.24 strings (small arms): python3 tools/lang/arms_1_24.py adds them to en_us, ru_ru and uk_ua."""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

# id: (en, ru, uk, en description, ru description, uk description)
GUNS = {
    'ak74': ('AK-74 assault rifle', 'Автомат АК-74', 'Автомат АК-74',
             'The classic 5.45 mm Kalashnikov', 'Классический Калашников под 5,45 мм', 'Класичний Калашников під 5,45 мм'),
    'pkm': ('PKM machine gun', 'Пулемёт ПКМ', 'Кулемет ПКМ',
            'Belt-fed 7.62 mm general purpose machine gun', 'Единый пулемёт под 7,62 мм с лентой на 100', 'Єдиний кулемет 7,62 мм зі стрічкою на 100'),
    'svd': ('SVD sniper rifle', 'Снайперская винтовка СВД', 'Снайперська гвинтівка СВД',
            'Self-loading marksman rifle with a PSO-1 scope', 'Самозарядная, с прицелом ПСО-1', 'Самозарядна, з прицілом ПСО-1'),
    'pm': ('Makarov pistol', 'Пистолет Макарова', 'Пістолет Макарова',
           'The old officer\'s pistol', 'Старый офицерский пистолет', 'Старий офіцерський пістолет'),
    'rpg7': ('RPG-7 grenade launcher', 'Гранатомёт РПГ-7', 'Гранатомет РПГ-7',
             'Reloadable anti-tank grenade launcher', 'Многоразовый противотанковый гранатомёт', 'Багаторазовий протитанковий гранатомет'),
    'akm': ('AKM assault rifle', 'Автомат АКМ', 'Автомат АКМ',
            '7.62×39 mm, heavier hitting, more recoil', '7,62×39 мм: бьёт сильнее, отдача больше', '7,62×39 мм: б\'є сильніше, віддача більша'),
    'ak12': ('AK-12 assault rifle', 'Автомат АК-12', 'Автомат АК-12',
             'Modern Kalashnikov with rails and a red dot', 'Современный Калашников с планками и коллиматором', 'Сучасний Калашников із планками та коліматором'),
    'aks74u': ('AKS-74U carbine', 'Автомат АКС-74У', 'Автомат АКС-74У',
               'Short, for vehicle crews and close quarters', 'Короткий: для экипажей и ближнего боя', 'Короткий: для екіпажів і ближнього бою'),
    'rpk74': ('RPK-74 light machine gun', 'Ручной пулемёт РПК-74', 'Ручний кулемет РПК-74',
              'Long barrel, 45-round magazine, bipod', 'Длинный ствол, магазин на 45, сошки', 'Довгий ствол, магазин на 45, сошки'),
    'pkp': ('PKP Pecheneg machine gun', 'Пулемёт ПКП «Печенег»', 'Кулемет ПКП «Печеніг»',
            'PKM with a cooled heavy barrel, more accurate', 'ПКМ с охлаждаемым тяжёлым стволом, точнее', 'ПКМ з охолоджуваним важким стволом, точніший'),
    'sv98': ('SV-98 sniper rifle', 'Снайперская винтовка СВ-98', 'Снайперська гвинтівка СВ-98',
             'Bolt action, very accurate, hits hard', 'Затворная, очень точная, бьёт сильно', 'Затворна, дуже точна, б\'є сильно'),
    'vss': ('VSS Vintorez', 'Винтовка ВСС «Винторез»', 'Гвинтівка ВСС «Винторіз»',
            'Silent sniper rifle, 9×39 mm', 'Бесшумная снайперская винтовка, 9×39 мм', 'Безшумна снайперська гвинтівка, 9×39 мм'),
    'asval': ('AS Val', 'Автомат АС «Вал»', 'Автомат АС «Вал»',
              'Silent assault rifle, 9×39 mm', 'Бесшумный автомат, 9×39 мм', 'Безшумний автомат, 9×39 мм'),
    'saiga12': ('Saiga-12 shotgun', 'Ружьё «Сайга-12»', 'Рушниця «Сайга-12»',
                'Semi-automatic shotgun with a box magazine', 'Самозарядное ружьё с магазином', 'Самозарядна рушниця з магазином'),
    'bizon': ('PP-19 Bizon', 'Пистолет-пулемёт ПП-19 «Бизон»', 'Пістолет-кулемет ПП-19 «Бізон»',
              '64 rounds in the helical magazine', '64 патрона в шнековом магазине', '64 набої в шнековому магазині'),
    'rpg22': ('RPG-22 one-shot launcher', 'Гранатомёт РПГ-22', 'Гранатомет РПГ-22',
              'Fire once and throw the tube away', 'Одноразовый: выстрелил — выбросил', 'Одноразовий: вистрілив — викинув'),
    'fort12': ('Fort-12 pistol', 'Пистолет «Форт-12»', 'Пістолет «Форт-12»',
               'Ukrainian service pistol, 12 rounds', 'Украинский служебный пистолет, 12 патронов', 'Український службовий пістолет, 12 набоїв'),
    'fort221': ('Fort-221 bullpup rifle', 'Автомат «Форт-221»', 'Автомат «Форт-221»',
                'Ukrainian bullpup with a holographic sight', 'Украинский буллпап с голографическим прицелом', 'Український булпап із голографічним прицілом'),
    'm4a1': ('M4A1 carbine', 'Карабин M4A1', 'Карабін M4A1',
             'Fast-firing 5.56 mm carbine with a holographic sight', 'Скорострельный карабин 5,56 мм с голографическим прицелом', 'Скорострільний карабін 5,56 мм з голографічним прицілом'),
    'm16a4': ('M16A4 rifle', 'Винтовка M16A4', 'Гвинтівка M16A4',
              'Three-round bursts, ACOG scope', 'Очереди по три, прицел ACOG', 'Черги по три, приціл ACOG'),
    'hk416': ('HK416 rifle', 'Автомат HK416', 'Автомат HK416',
              'Reliable piston rifle with a red dot', 'Надёжный автомат с газовым поршнем и коллиматором', 'Надійний автомат із газовим поршнем і коліматором'),
    'scarh': ('FN SCAR-H rifle', 'Винтовка FN SCAR-H', 'Гвинтівка FN SCAR-H',
              'Full-power 7.62 mm battle rifle', 'Мощная боевая винтовка 7,62 мм', 'Потужна бойова гвинтівка 7,62 мм'),
    'm249': ('M249 light machine gun', 'Ручной пулемёт M249', 'Ручний кулемет M249',
             '100 rounds of 5.56 mm, high rate of fire', '100 патронов 5,56 мм, высокий темп', '100 набоїв 5,56 мм, високий темп'),
    'm240b': ('M240B machine gun', 'Пулемёт M240B', 'Кулемет M240B',
              'Heavy 7.62 mm general purpose machine gun', 'Тяжёлый единый пулемёт 7,62 мм', 'Важкий єдиний кулемет 7,62 мм'),
    'm110': ('M110 marksman rifle', 'Снайперская винтовка M110', 'Снайперська гвинтівка M110',
             'Self-loading 7.62 mm sniper rifle', 'Самозарядная снайперская винтовка 7,62 мм', 'Самозарядна снайперська гвинтівка 7,62 мм'),
    'm82': ('Barrett M82 anti-materiel rifle', 'Крупнокалиберная винтовка Barrett M82', 'Великокаліберна гвинтівка Barrett M82',
            '12.7 mm: through light armour, from far away', '12,7 мм: пробивает лёгкую броню, бьёт очень далеко', '12,7 мм: пробиває легку броню, б\'є дуже далеко'),
    'awm': ('AWM sniper rifle', 'Снайперская винтовка AWM', 'Снайперська гвинтівка AWM',
            'Bolt action, the longest reach', 'Затворная, самая дальнобойная', 'Затворна, найдалекобійніша'),
    'mp5': ('MP5 submachine gun', 'Пистолет-пулемёт MP5', 'Пістолет-кулемет MP5',
            'Light, accurate, 9 mm', 'Лёгкий и точный, 9 мм', 'Легкий і точний, 9 мм'),
    'glock17': ('Glock 17 pistol', 'Пистолет Glock 17', 'Пістолет Glock 17',
                '17 rounds, fast to fire', '17 патронов, быстрый', '17 набоїв, швидкий'),
    'm870': ('Remington 870 shotgun', 'Ружьё Remington 870', 'Рушниця Remington 870',
             'Pump action: eight pellets a shot', 'Помповое: восемь картечин за выстрел', 'Помпова: вісім картечин за постріл'),
    'm32': ('M32 grenade launcher', 'Гранатомёт M32', 'Гранатомет M32',
            'Six 40 mm grenades in a revolving drum', 'Шесть гранат 40 мм в барабане', 'Шість гранат 40 мм у барабані'),
    'at4': ('AT4 one-shot launcher', 'Гранатомёт AT4', 'Гранатомет AT4',
            'Fire once and throw the tube away', 'Одноразовый: выстрелил — выбросил', 'Одноразовий: вистрілив — викинув'),
    'cg84': ('Carl Gustaf M4 recoilless rifle', 'Безоткатное орудие Carl Gustaf M4', 'Безвідкатна гармата Carl Gustaf M4',
             'Reloadable 84 mm, accurate and powerful', 'Многоразовое 84 мм: точное и мощное', 'Багаторазова 84 мм: точна й потужна'),
    'nlaw': ('NLAW anti-tank launcher', 'ПТРК NLAW', 'ПТРК NLAW',
             'Flies over the tank and strikes its roof', 'Пролетает над танком и бьёт в крышу', 'Пролітає над танком і б\'є в дах'),
    'javelin': ('FGM-148 Javelin', 'ПТРК Javelin', 'ПТРК Javelin',
                'Fire and forget: lock on, it climbs and dives on the roof', 'Выстрелил и забыл: захват цели, горка и удар сверху', 'Вистрілив і забув: захоплення цілі, гірка й удар зверху'),
}

LAUNCHER = {
    'rpg7': ('Burns through vehicle armour, explodes on impact', 'Пробивает броню техники, взрывается при попадании', 'Пробиває броню техніки, вибухає при влучанні'),
    'rpg22': ('A light shaped charge against armour', 'Лёгкий кумулятивный заряд против брони', 'Легкий кумулятивний заряд проти броні'),
    'at4': ('84 mm shaped charge, strong against armour', 'Кумулятивный заряд 84 мм, сильный против брони', 'Кумулятивний заряд 84 мм, сильний проти броні'),
    'cg84': ('84 mm rounds, the strongest of the unguided', 'Выстрелы 84 мм — самые мощные из неуправляемых', 'Постріли 84 мм — найпотужніші з некерованих'),
    'nlaw': ('Top attack: aim at the tank, it passes over and strikes down', 'Удар сверху: наведи на танк — ракета пролетит над ним и ударит вниз', 'Удар зверху: наведи на танк — ракета пролетить над ним і вдарить вниз'),
    'javelin': ('Hold the sight on a vehicle for 2 s to lock on, then fire', 'Держи технику в прицеле 2 с до захвата, потом стреляй', 'Тримай техніку в прицілі 2 с до захоплення, потім стріляй'),
    'm32': ('Grenades arc - aim above far targets', 'Гранаты летят по дуге — по дальним целям цель выше', 'Гранати летять дугою — по далеких цілях цілься вище'),
}

AMMO = {
    'ammo_556': ('5.56 mm round', 'Патрон 5,56 мм', 'Набій 5,56 мм'),
    'ammo_762x39': ('7.62×39 mm round', 'Патрон 7,62×39 мм', 'Набій 7,62×39 мм'),
    'ammo_9x39': ('9×39 mm round', 'Патрон 9×39 мм', 'Набій 9×39 мм'),
    'ammo_127': ('12.7 mm round', 'Патрон 12,7 мм', 'Набій 12,7 мм'),
    'ammo_12g': ('12-gauge shell', 'Патрон 12 калибра (картечь)', 'Набій 12 калібру (картеч)'),
    'ammo_40mm': ('40 mm grenade', 'Граната 40 мм', 'Граната 40 мм'),
    'cg_round': ('84 mm Carl Gustaf round', 'Выстрел 84 мм к Carl Gustaf', 'Постріл 84 мм до Carl Gustaf'),
    'javelin_missile': ('Javelin missile', 'Ракета Javelin', 'Ракета Javelin'),
    'rpg22_rocket': ('RPG-22 grenade', 'Граната РПГ-22', 'Граната РПГ-22'),
    'at4_rocket': ('AT4 rocket', 'Граната AT4', 'Граната AT4'),
    'nlaw_missile': ('NLAW missile', 'Ракета NLAW', 'Ракета NLAW'),
}

MISC = {
    'itemGroup.airdefense.arms': ('Small arms', 'Стрелковое оружие', 'Стрілецька зброя'),
    'tooltip.airdefense.gun.burst': ('bursts of %s', 'очередями по %s', 'чергами по %s'),
    'tooltip.airdefense.gun.bolt': ('bolt action', 'ручная перезарядка затвором', 'ручне перезаряджання затвором'),
    'tooltip.airdefense.gun.pump': ('pump action', 'помповое', 'помпова'),
    'tooltip.airdefense.gun.disposable': ('One shot, then the tube is thrown away', 'Один выстрел, потом труба выбрасывается', 'Один постріл, потім труба викидається'),
    'tooltip.airdefense.gun.keys_launcher': ('LMB fire · RMB aim', 'ЛКМ — огонь · ПКМ — прицелиться', 'ЛКМ — вогонь · ПКМ — прицілитися'),
    'message.airdefense.gun.no_lock': ('No lock: hold the vehicle in the sight', 'Нет захвата: держи технику в прицеле', 'Немає захоплення: тримай техніку в прицілі'),
    'hud.airdefense.gun.locked': ('TARGET LOCKED - fire', 'ЦЕЛЬ ЗАХВАЧЕНА — огонь', 'ЦІЛЬ ЗАХОПЛЕНО — вогонь'),
    'hud.airdefense.gun.locking': ('Locking on...', 'Захват цели...', 'Захоплення цілі...'),
    'hud.airdefense.gun.seek': ('Put a vehicle in the cross', 'Наведи перекрестие на технику', 'Наведи перехрестя на техніку'),
    'subtitles.airdefense.shotgun': ('Shotgun blast', 'Выстрел из ружья', 'Постріл із рушниці'),
    'subtitles.airdefense.gunshot_heavy': ('Heavy rifle shot', 'Выстрел крупнокалиберной винтовки', 'Постріл великокаліберної гвинтівки'),
    'subtitles.airdefense.gunshot_suppressed': ('Muffled shot', 'Глухой выстрел', 'Глухий постріл'),
    'subtitles.airdefense.gun_pump': ('Pump racked', 'Передёрнуто цевьё', 'Пересмикнуто цівку'),
    'subtitles.airdefense.grenade_launch': ('Grenade launcher fires', 'Выстрел гранатомёта', 'Постріл гранатомета'),
    'subtitles.airdefense.javelin_lock': ('Javelin locked on', 'Javelin: цель захвачена', 'Javelin: ціль захоплено'),
}


def main():
    for i, code in enumerate(('en_us', 'ru_ru', 'uk_ua')):
        p = os.path.join(LANG, code + '.json')
        with open(p, encoding='utf-8') as f:
            d = json.load(f)
        for gid, t in GUNS.items():
            d['item.airdefense.' + gid] = t[i]
            d['tooltip.airdefense.gun.desc.' + gid] = t[3 + i]
        for gid, t in LAUNCHER.items():
            d['tooltip.airdefense.gun.launcher.' + gid] = t[i]
        for k, t in AMMO.items():
            d['item.airdefense.' + k] = t[i]
        for k, t in MISC.items():
            d[k] = t[i]
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write('\n')
        print(code, len(d))


if __name__ == '__main__':
    main()
