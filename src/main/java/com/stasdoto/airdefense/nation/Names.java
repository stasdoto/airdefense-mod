package com.stasdoto.airdefense.nation;

import java.util.Collection;
import java.util.Random;

/** Made-up place names (in Russian: the player's language). */
public final class Names {
	private static final String[][] VILLAGES = {
			// name, grammatical gender for the adjectives: f, m, n, p (plural)
			{"Липовка", "f"}, {"Дубки", "p"}, {"Березино", "n"}, {"Каменка", "f"}, {"Ольховец", "m"}, {"Сосновка", "f"},
			{"Ясенево", "n"}, {"Калиновка", "f"}, {"Рябиновка", "f"}, {"Вишнёвое", "n"}, {"Яблоневка", "f"}, {"Озерки", "p"},
			{"Заречье", "n"}, {"Подгорное", "n"}, {"Луговое", "n"}, {"Полянка", "f"}, {"Ручьи", "p"}, {"Ключи", "p"},
			{"Мостки", "p"}, {"Мельничное", "n"}, {"Кузнецово", "n"}, {"Гончарово", "n"}, {"Пасека", "f"}, {"Хуторок", "m"},
			{"Боровое", "n"}, {"Сосновый Бор", "m"}, {"Красный Яр", "m"}, {"Белый Ключ", "m"}, {"Тихая Заводь", "f"},
			{"Светлый Луг", "m"}, {"Зелёная Роща", "f"}, {"Ясная Поляна", "f"}, {"Камышино", "n"}, {"Ракитное", "n"},
			{"Верболозье", "n"}, {"Медовка", "f"}, {"Малиновка", "f"}, {"Черёмушки", "p"}, {"Ивановка", "f"},
			{"Петровское", "n"}, {"Семёновка", "f"}, {"Никольское", "n"}, {"Покровка", "f"}, {"Троицкое", "n"},
			{"Студёное", "n"}, {"Ветряки", "p"}, {"Курганы", "p"}, {"Степное", "n"}, {"Овражки", "p"}, {"Пригорки", "p"},
	};
	private static final String[][] ADJ = {
			{"Новая", "Новый", "Новое", "Новые"}, {"Старая", "Старый", "Старое", "Старые"},
			{"Верхняя", "Верхний", "Верхнее", "Верхние"}, {"Нижняя", "Нижний", "Нижнее", "Нижние"},
			{"Малая", "Малый", "Малое", "Малые"}, {"Большая", "Большой", "Большое", "Большие"},
	};
	private static final String[] LANDS = {
			"Велария", "Борения", "Зарания", "Кремония", "Лиравия", "Мирания", "Норвалия", "Оргения", "Ровения",
			"Светония", "Ульмания", "Фалькония", "Холмия", "Эстравия", "Ясония", "Белогория", "Златогория", "Лесогория",
			"Новоземье", "Приречье", "Серебрания", "Одолания", "Вышеград", "Ладония", "Дубравия", "Каменея", "Полесия",
			"Синегория", "Ветрания", "Тавелия",
	};
	private static final String[] FORMS = {"Республика", "Княжество", "Королевство", "Федерация", "Союз", "Земля"};

	private Names() {
	}

	public static String village(Random r, Collection<String> taken) {
		for (int attempt = 0; attempt < 40; attempt++) {
			String[] v = VILLAGES[r.nextInt(VILLAGES.length)];
			String name = v[0];
			if (attempt > 4 || r.nextInt(4) == 0) {
				int g = switch (v[1]) {
					case "f" -> 0;
					case "m" -> 1;
					case "n" -> 2;
					default -> 3;
				};
				if (!name.contains(" ")) {
					name = ADJ[r.nextInt(ADJ.length)][g] + " " + name;
				}
			}
			if (!taken.contains(name)) {
				return name;
			}
		}
		return VILLAGES[r.nextInt(VILLAGES.length)][0] + " " + (taken.size() + 1);
	}

	public static String country(Random r, Collection<String> taken) {
		for (int attempt = 0; attempt < 40; attempt++) {
			String name = FORMS[r.nextInt(FORMS.length)] + " " + LANDS[r.nextInt(LANDS.length)];
			if (!taken.contains(name)) {
				return name;
			}
		}
		return "Земля " + (taken.size() + 1);
	}
}
