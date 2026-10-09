"""1.29 "Life" strings: talking to people, their jobs, the soldiers' orders. python3 tools/lang/life_1_29.py"""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'lang')

S = {
    'dialogue.airdefense.title': ('%s, %s', '%s, %s', '%s, %s'),
    'dialogue.airdefense.nowhere': ('no home', 'без дома', 'без дому'),
    'dialogue.airdefense.free_town': ('a free town', 'вольный город', 'вільне місто'),
    'dialogue.airdefense.job.none': ('townsman', 'горожанин', 'містянин'),
    'dialogue.airdefense.job.armorer': ('armourer', 'бронник', 'бронник'),
    'dialogue.airdefense.job.butcher': ('butcher', 'мясник', 'м’ясник'),
    'dialogue.airdefense.job.cartographer': ('surveyor', 'картограф', 'картограф'),
    'dialogue.airdefense.job.cleric': ('doctor', 'врач', 'лікар'),
    'dialogue.airdefense.job.farmer': ('farmer', 'фермер', 'фермер'),
    'dialogue.airdefense.job.fisherman': ('fisherman', 'рыбак', 'рибалка'),
    'dialogue.airdefense.job.fletcher': ('carpenter', 'столяр', 'столяр'),
    'dialogue.airdefense.job.leatherworker': ('tailor', 'портной', 'кравець'),
    'dialogue.airdefense.job.librarian': ('teacher', 'учитель', 'вчитель'),
    'dialogue.airdefense.job.mason': ('builder', 'строитель', 'будівельник'),
    'dialogue.airdefense.job.shepherd': ('shepherd', 'пастух', 'пастух'),
    'dialogue.airdefense.job.toolsmith': ('mechanic', 'механик', 'механік'),
    'dialogue.airdefense.job.weaponsmith': ('gunsmith', 'оружейник', 'зброяр'),
    'dialogue.airdefense.option.1': ('Tell me about the town', 'Расскажи о городе', 'Розкажи про місто'),
    'dialogue.airdefense.option.2': ('How are you?', 'Как жизнь?', 'Як життя?'),
    'dialogue.airdefense.option.3': ('Let\'s trade', 'Поторгуем', 'Поторгуємо'),
    'dialogue.airdefense.option.4': ('Follow me!', 'За мной!', 'За мною!'),
    'dialogue.airdefense.option.5': ('Hold here', 'Стоять здесь', 'Стояти тут'),
    'dialogue.airdefense.option.6': ('Back to your post', 'Вернуться на пост', 'Повернутися на пост'),
    'dialogue.airdefense.option.7': ('Will you work for me?', 'Пойдёшь ко мне работать?', 'Підеш до мене працювати?'),
    'dialogue.airdefense.option.8': ('Goodbye', 'Пока', 'Бувай'),
    'dialogue.airdefense.option.9': ('When do you work?', 'Когда работаешь?', 'Коли працюєш?'),
    'dialogue.airdefense.hello.0': ('Hello! Can I help you?', 'Здравствуйте! Чем могу помочь?', 'Добрий день! Чим можу допомогти?'),
    'dialogue.airdefense.hello.1': ('Oh, a new face. What brings you here?', 'О, новое лицо. Какими судьбами?', 'О, нове обличчя. Якими судьбами?'),
    'dialogue.airdefense.hello.2': ('Good day. Quiet today, at least.', 'Добрый день. Сегодня хоть тихо.', 'Добрий день. Сьогодні хоч тихо.'),
    'dialogue.airdefense.hello.night.0': ('It\'s late... what do you want?', 'Поздно уже... что вам?', 'Вже пізно... що вам?'),
    'dialogue.airdefense.hello.night.1': ('Shh, people are asleep.', 'Тсс, люди спят.', 'Тсс, люди сплять.'),
    'dialogue.airdefense.hello.night.2': ('I was just going to bed.', 'Я как раз спать собирался.', 'Я якраз спати збирався.'),
    'dialogue.airdefense.hello.soldier.0': ('Sir! All quiet.', 'Здравия желаю! Всё спокойно.', 'Бажаю здоров’я! Все спокійно.'),
    'dialogue.airdefense.hello.soldier.1': ('On watch. Any orders?', 'На посту. Будут приказания?', 'На посту. Будуть накази?'),
    'dialogue.airdefense.hello.soldier.2': ('Keep your head down out here.', 'Не высовывайтесь тут лишний раз.', 'Не висовуйтеся тут зайвий раз.'),
    'dialogue.airdefense.hello.soldier_fight': ('Not now - we\'re under fire!', 'Не сейчас — по нам стреляют!', 'Не зараз — по нас стріляють!'),
    'dialogue.airdefense.town': ('%1$s, %2$s. There are %3$s of us. People are %4$s. %5$s', '%1$s, %2$s. Живёт нас %3$s. Люди %4$s. %5$s',
                                 '%1$s, %2$s. Живе нас %3$s. Люди %4$s. %5$s'),
    'dialogue.airdefense.town.bad': ('What\'s wrong: %s.', 'Плохо, что: %s.', 'Погано, що: %s.'),
    'dialogue.airdefense.town.good': ('What\'s good: %s.', 'Хорошо, что: %s.', 'Добре, що: %s.'),
    'dialogue.airdefense.town.none': ('I\'m not from around here.', 'Я не из этих мест.', 'Я не з цих місць.'),
    'dialogue.airdefense.how.happy.0': ('Life is good! Work, a roof, bread on the table.', 'Хорошо живём! Работа есть, крыша есть, хлеб есть.',
                                        'Добре живемо! Робота є, дах є, хліб є.'),
    'dialogue.airdefense.how.happy.1': ('Can\'t complain. The town is growing.', 'Грех жаловаться. Город растёт.', 'Гріх скаржитися. Місто росте.'),
    'dialogue.airdefense.how.happy.2': ('Fine, thanks! My kids go to the new school.', 'Отлично, спасибо! Дети в новую школу ходят.',
                                        'Чудово, дякую! Діти до нової школи ходять.'),
    'dialogue.airdefense.how.calm.0': ('As usual. Work, home, work.', 'Как обычно. Работа, дом, работа.', 'Як завжди. Робота, дім, робота.'),
    'dialogue.airdefense.how.calm.1': ('Getting by. Could be worse.', 'Потихоньку. Бывало и хуже.', 'Потроху. Бувало й гірше.'),
    'dialogue.airdefense.how.calm.2': ('Nothing new. Prices are up again.', 'Ничего нового. Цены опять выросли.', 'Нічого нового. Ціни знову виросли.'),
    'dialogue.airdefense.how.unhappy.0': ('Hard times. Nobody listens to us.', 'Тяжело. Нас никто не слушает.', 'Важко. Нас ніхто не слухає.'),
    'dialogue.airdefense.how.unhappy.1': ('Badly. We barely make ends meet.', 'Плохо. Еле концы с концами сводим.', 'Погано. Ледь кінці з кінцями зводимо.'),
    'dialogue.airdefense.how.unhappy.2': ('If things don\'t change, people will take to the streets.', 'Если так пойдёт, люди выйдут на улицы.',
                                          'Якщо так піде, люди вийдуть на вулиці.'),
    'dialogue.airdefense.how.angry.0': ('Enough! We\'ve had enough of all this!', 'Хватит! Надоело всё это!', 'Досить! Набридло все це!'),
    'dialogue.airdefense.how.angry.1': ('Leave me alone. You lot have done enough.', 'Отстаньте. Вы и так уже натворили.', 'Відчепіться. Ви й так уже накоїли.'),
    'dialogue.airdefense.how.angry.2': ('One more day like this and the town will rise.', 'Ещё день такой — и город поднимется.',
                                        'Ще день такий — і місто підніметься.'),
    'dialogue.airdefense.how.worker.0': ('Working as a %s. Tired, but paid.', 'Работаю — %s. Устаю, но платят.', 'Працюю — %s. Втомлююсь, але платять.'),
    'dialogue.airdefense.how.worker.1': ('%s - that\'s me. Honest work.', '%s — это я. Честный труд.', '%s — це я. Чесна праця.'),
    'dialogue.airdefense.how.worker.2': ('I\'m a %s. At night I sleep, by day I work.', 'Я %s. Ночью сплю, днём работаю.', 'Я %s. Вночі сплю, вдень працюю.'),
    'dialogue.airdefense.how.soldier.0': ('Serving. Boring, but that\'s good.', 'Служу. Скучно — и хорошо.', 'Служу. Нудно — і добре.'),
    'dialogue.airdefense.how.soldier.1': ('Rifle cleaned, boots dry. Fine.', 'Автомат почищен, ноги сухие. Нормально.', 'Автомат почищений, ноги сухі. Нормально.'),
    'dialogue.airdefense.how.soldier.2': ('Waiting for leave.', 'Жду отпуска.', 'Чекаю відпустки.'),
    'dialogue.airdefense.how.soldier_war.0': ('There\'s a war on. Every night drones.', 'Война идёт. Каждую ночь дроны.', 'Війна йде. Щоночі дрони.'),
    'dialogue.airdefense.how.soldier_war.1': ('We\'ll hold. We have to.', 'Выстоим. Деваться некуда.', 'Вистоїмо. Нікуди дітися.'),
    'dialogue.airdefense.how.soldier_war.2': ('Lost two friends this week.', 'На этой неделе двоих друзей потерял.', 'Цього тижня двох друзів втратив.'),
    'dialogue.airdefense.follow': ('Yes sir! Right behind you.', 'Есть! Иду за вами.', 'Є! Іду за вами.'),
    'dialogue.airdefense.stay': ('Holding this spot.', 'Держу позицию здесь.', 'Тримаю позицію тут.'),
    'dialogue.airdefense.return': ('Back to my post.', 'Возвращаюсь на пост.', 'Повертаюся на пост.'),
    'dialogue.airdefense.hire.yes': ('Deal! I\'ll be a %s.', 'Договорились! Буду — %s.', 'Домовились! Буду — %s.'),
    'dialogue.airdefense.hire.no': ('Thanks, but no.', 'Спасибо, но нет.', 'Дякую, але ні.'),
    'dialogue.airdefense.workday.morning': ('Off to work soon - mornings and days I work, nights I sleep.', 'Скоро на работу — утром и днём работаю, ночью сплю.',
                                            'Скоро на роботу — вранці й удень працюю, вночі сплю.'),
    'dialogue.airdefense.workday.day': ('All day, till dark. Then home to bed.', 'Весь день, до темноты. Потом домой спать.',
                                        'Весь день, до темряви. Потім додому спати.'),
    'dialogue.airdefense.workday.night': ('Night - I sleep. Back to work at sunrise.', 'Ночь — сплю. С рассветом снова на работу.',
                                          'Ніч — сплю. Зі світанком знову на роботу.'),
    'guide.airdefense.page.30': ('PEOPLE\n\nRight-click anyone in a town: who he is, what he does, how the town lives. Trade, hire him for your town.\nYour soldiers: "Follow me", "Hold here", "Back to your post".\nWorkers sleep at night, work by day.',
                                 'ЛЮДИ\n\nПКМ по любому жителю: кто он, чем занят, как живёт город. Можно поторговать, позвать к себе на работу.\nСвоим солдатам: «За мной», «Стоять здесь», «Вернуться на пост».\nРабочие ночью спят, днём работают.',
                                 'ЛЮДИ\n\nПКМ по будь-якому мешканцю: хто він, чим зайнятий, як живе місто. Можна поторгувати, покликати до себе на роботу.\nСвоїм солдатам: «За мною», «Стояти тут», «Повернутися на пост».\nРобітники вночі сплять, вдень працюють.'),
    'guide.airdefense.page.31': ('A SMARTER ARMY\n\nSoldiers take cover, work round the flank of a dug-in enemy, shoot wide when pinned down.\nThe medic runs to the wounded and dresses them.\nA man with a radio calls everyone near when he is hit.',
                                 'УМНАЯ АРМИЯ\n\nСолдаты прячутся за укрытиями, обходят окопавшегося врага с фланга, под огнём стреляют хуже.\nМедик бежит к раненым и перевязывает их.\nСолдат с рацией, когда в него попадают, зовёт всех ближних на помощь.',
                                 'РОЗУМНА АРМІЯ\n\nСолдати ховаються за укриттями, обходять окопаного ворога з флангу, під вогнем стріляють гірше.\nМедик біжить до поранених і перев’язує їх.\nСолдат з рацією, коли в нього влучають, кличе всіх ближніх на допомогу.'),
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
