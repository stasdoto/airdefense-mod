"""1.42 "World" strings: the seasons, the rulers, the dealings between countries. python3 tools/lang/world_1_42.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

NAMES = {
    'season.airdefense.came.0': ('Spring has come: the snow is melting', 'Пришла весна: снег тает', 'Прийшла весна: сніг тане'),
    'season.airdefense.came.1': ('Summer has come', 'Пришло лето', 'Прийшло літо'),
    'season.airdefense.came.2': ('Autumn has come: the leaves are turning', 'Пришла осень: листья желтеют', 'Прийшла осінь: листя жовтіє'),
    'season.airdefense.came.3': ('Winter has come: snow will lie, the water will freeze', 'Пришла зима: ляжет снег, замёрзнет вода',
                                 'Прийшла зима: ляже сніг, замерзне вода'),
    'ruler.airdefense.full': ('%s %s %s', '%s %s %s', '%s %s %s'),
    'ruler.airdefense.trait.0': ('peaceful', 'миролюбивый', 'миролюбний'),
    'ruler.airdefense.trait.1': ('cautious', 'осторожный', 'обережний'),
    'ruler.airdefense.trait.2': ('greedy', 'жадный', 'жадібний'),
    'ruler.airdefense.trait.3': ('warlike', 'воинственный', 'войовничий'),
    'nation.airdefense.diplomacy.new_ruler': ('%s has a new ruler: %s (%s)', 'В стране %s новый правитель: %s (%s)', 'У країні %s новий правитель: %s (%s)'),
    'nation.airdefense.diplomacy.alliance': ('%s and %s have made an alliance', '%s и %s заключили союз', '%s і %s уклали союз'),
    'nation.airdefense.diplomacy.alliance_broken': ('The alliance of %s and %s has broken up', 'Союз стран %s и %s распался', 'Союз країн %s і %s розпався'),
    'nation.airdefense.diplomacy.trade_income': ('Trade with %s brought %s emeralds', 'Торговля со страной %s принесла %s изумрудов',
                                                 'Торгівля з країною %s принесла %s смарагдів'),
    'nation.airdefense.diplomacy.gift': ('%s of %s accepts your gift. Standing: %s', '%s (%s) принимает ваш подарок. Отношения: %s',
                                         '%s (%s) приймає ваш подарунок. Відносини: %s'),
    'nation.airdefense.diplomacy.at_war': ('You are at war with %s: make peace first', 'Вы воюете со страной %s: сначала заключите мир',
                                           'Ви воюєте з країною %s: спершу укладіть мир'),
    'nation.airdefense.diplomacy.already_trade': ('You already trade with %s', 'Со страной %s уже есть торговый договор', 'З країною %s вже є торговельний договір'),
    'nation.airdefense.diplomacy.already_alliance': ('You are already allies of %s', 'Вы уже в союзе со страной %s', 'Ви вже в союзі з країною %s'),
    'nation.airdefense.diplomacy.refused_trade': ('%s of %s will not trade with you: your standing is %s, it needs %s',
                                                  '%s (%s) отказывается торговать: отношения %s, нужно %s',
                                                  '%s (%s) відмовляється торгувати: відносини %s, потрібно %s'),
    'nation.airdefense.diplomacy.refused_alliance': ('%s of %s will not be your ally: your standing is %s, it needs %s',
                                                     '%s (%s) не пойдёт на союз: отношения %s, нужно %s',
                                                     '%s (%s) не піде на союз: відносини %s, потрібно %s'),
    'nation.airdefense.diplomacy.refused_ally_of_enemy': ('%s of %s will not be your ally: they are allied with your enemy',
                                                          '%s (%s) не пойдёт на союз: он в союзе с вашим врагом',
                                                          '%s (%s) не піде на союз: він у союзі з вашим ворогом'),
    'nation.airdefense.diplomacy.agreed_trade': ('%s of %s signs a trade treaty: every day it will bring you emeralds',
                                                 '%s (%s) подписывает торговый договор: каждый день он будет приносить вам изумруды',
                                                 '%s (%s) підписує торговельний договір: щодня він приноситиме вам смарагди'),
    'nation.airdefense.diplomacy.agreed_alliance': ('%s of %s makes an alliance with you: their army will march against your enemies',
                                                    '%s (%s) заключает с вами союз: его армия выступит против ваших врагов',
                                                    '%s (%s) укладає з вами союз: його армія виступить проти ваших ворогів'),
    'nation.airdefense.war.why_ally': ('to help an ally, %s', 'на помощь союзнику — стране %s', 'на допомогу союзнику — країні %s'),
    'screen.airdefense.village.gift': ('Gift (%s emeralds)', 'Подарок (%s изумр.)', 'Подарунок (%s смарагд.)'),
    'screen.airdefense.village.trade': ('Offer trade', 'Торговый договор', 'Торговельний договір'),
    'screen.airdefense.village.alliance': ('Offer an alliance', 'Предложить союз', 'Запропонувати союз'),
    'screen.airdefense.village.ruler': ('Ruler: %s, %s', 'Правитель: %s, %s', 'Правитель: %s, %s'),
    'screen.airdefense.village.standing': ('Standing with you: %s (%s) %s', 'Отношения с вами: %s (%s) %s', 'Відносини з вами: %s (%s) %s'),
    'screen.airdefense.village.standing.friendly': ('friendly', 'дружеские', 'дружні'),
    'screen.airdefense.village.standing.warm': ('warm', 'тёплые', 'теплі'),
    'screen.airdefense.village.standing.neutral': ('neutral', 'ровные', 'рівні'),
    'screen.airdefense.village.standing.cool': ('cool', 'прохладные', 'прохолодні'),
    'screen.airdefense.village.standing.hostile': ('hostile', 'враждебные', 'ворожі'),
    'screen.airdefense.village.allied': ('- allies', '· союзники', '· союзники'),
    'screen.airdefense.village.trading': ('- trade treaty', '· торговый договор', '· торговельний договір'),
}

TITLES = [('President', 'Президент', 'Президент'), ('King', 'Король', 'Король'), ('Prince', 'Князь', 'Князь'),
          ('Prime Minister', 'Премьер-министр', "Прем'єр-міністр"), ('Chancellor', 'Канцлер', 'Канцлер'),
          ('Emperor', 'Император', 'Імператор'), ('Grand Duke', 'Великий герцог', 'Великий герцог')]
RULER_NAMES = [('Alexander', 'Александр', 'Олександр'), ('Yaroslav', 'Ярослав', 'Ярослав'), ('Vladimir', 'Владимир', 'Володимир'),
               ('Oleg', 'Олег', 'Олег'), ('Henry', 'Генрих', 'Генріх'), ('Louis', 'Людовик', 'Людовик'), ('Charles', 'Карл', 'Карл'),
               ('Frederick', 'Фридрих', 'Фрідріх'), ('William', 'Вильгельм', 'Вільгельм'), ('Otto', 'Отто', 'Отто'),
               ('Anna', 'Анна', 'Анна'), ('Catherine', 'Екатерина', 'Катерина'), ('Maria', 'Мария', 'Марія'),
               ('Elizabeth', 'Елизавета', 'Єлизавета'), ('Victoria', 'Виктория', 'Вікторія'), ('Helga', 'Хельга', 'Гельга'),
               ('Ingrid', 'Ингрид', 'Інгрід'), ('Olaf', 'Олаф', 'Олаф'), ('Eric', 'Эрик', 'Ерік'), ('Gustav', 'Густав', 'Густав'),
               ('Richard', 'Ричард', 'Річард'), ('Edward', 'Эдуард', 'Едуард'), ('Arthur', 'Артур', 'Артур'), ('Philip', 'Филипп', 'Філіп'),
               ('Leopold', 'Леопольд', 'Леопольд'), ('Maximilian', 'Максимилиан', 'Максиміліан'), ('Stephen', 'Стефан', 'Стефан'),
               ('Michael', 'Михаил', 'Михайло'), ('Boris', 'Борис', 'Борис'), ('Igor', 'Игорь', 'Ігор'), ('Svyatoslav', 'Святослав', 'Святослав'),
               ('Dmitry', 'Дмитрий', 'Дмитро')]
TITLES_F = [('President', 'Президент', 'Президентка'), ('Queen', 'Королева', 'Королева'), ('Princess', 'Княгиня', 'Княгиня'),
            ('Prime Minister', 'Премьер-министр', "Прем'єр-міністерка"), ('Chancellor', 'Канцлер', 'Канцлерка'),
            ('Empress', 'Императрица', 'Імператриця'), ('Grand Duchess', 'Великая герцогиня', 'Велика герцогиня')]
for i, t in enumerate(TITLES):
    NAMES['ruler.airdefense.title.%d' % i] = t
for i, t in enumerate(TITLES_F):
    NAMES['ruler.airdefense.title_f.%d' % i] = t
for i, n in enumerate(RULER_NAMES):
    NAMES['ruler.airdefense.name.%d' % i] = n


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
