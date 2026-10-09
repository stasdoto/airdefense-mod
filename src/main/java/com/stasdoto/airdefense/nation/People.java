package com.stasdoto.airdefense.nation;

import java.util.UUID;

/**
 * 1.29: people's names - the same for a person every time (from his id), in the manner of his country: Russian
 * names in Soviet towns and eastern armies, English ones in American towns and western armies, a European mix in
 * the European towns, Arabic names in the desert towns.
 */
public final class People {
	private People() {
	}

	private static final String[] RU_M = {"Иван", "Алексей", "Дмитрий", "Сергей", "Андрей", "Михаил", "Николай", "Павел", "Владимир", "Артём",
			"Егор", "Константин", "Виктор", "Олег", "Юрий", "Григорий", "Степан", "Тимофей", "Максим", "Роман"};
	private static final String[] RU_F = {"Анна", "Мария", "Ольга", "Елена", "Наталья", "Татьяна", "Ирина", "Светлана", "Екатерина", "Юлия",
			"Дарья", "Ксения", "Галина", "Вера", "Людмила", "Полина"};
	private static final String[] RU_S = {"Иванов", "Петров", "Смирнов", "Кузнецов", "Попов", "Соколов", "Лебедев", "Козлов", "Новиков",
			"Морозов", "Волков", "Соловьёв", "Васильев", "Зайцев", "Павлов", "Семёнов", "Голубев", "Виноградов", "Богданов", "Фёдоров"};
	private static final String[] EN_M = {"John", "Michael", "David", "James", "Robert", "William", "Daniel", "Matthew", "Joseph", "Ryan",
			"Tyler", "Kevin", "Brian", "Jason", "Eric", "Mark"};
	private static final String[] EN_F = {"Emily", "Sarah", "Jessica", "Ashley", "Megan", "Hannah", "Lauren", "Rachel", "Amanda", "Olivia"};
	private static final String[] EN_S = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson", "Anderson", "Taylor",
			"Thomas", "Moore", "Martin", "Jackson", "Thompson", "White", "Harris", "Clark", "Lewis", "Walker"};
	private static final String[] EU_M = {"Lukas", "Jan", "Pierre", "Marco", "Tomasz", "Henrik", "Paul", "Luca", "Mateo", "Felix", "Karl",
			"Antoine", "Piotr", "Lars", "Diego"};
	private static final String[] EU_F = {"Sophie", "Anna", "Marie", "Giulia", "Zofia", "Elena", "Clara", "Ingrid", "Lucia", "Emma"};
	private static final String[] EU_S = {"Müller", "Schmidt", "Dubois", "Rossi", "Kowalski", "Novák", "Jansen", "Garcia", "Bernard", "Weber",
			"Lindqvist", "Ferrari", "Wiśniewski", "Moreau", "Hansen", "Fischer"};
	private static final String[] AR_M = {"Ahmad", "Omar", "Yusuf", "Khalid", "Hassan", "Karim", "Tariq", "Samir", "Faris", "Nabil", "Rashid",
			"Hamza", "Ziyad", "Mahmoud"};
	private static final String[] AR_F = {"Fatima", "Layla", "Amina", "Zainab", "Mariam", "Noor", "Salma", "Yasmin", "Huda", "Rania"};
	private static final String[] AR_S = {"al-Rashid", "Haddad", "Nasser", "Khalil", "Mansour", "Saleh", "Hamdan", "Darwish", "Aziz", "Farouk",
			"Qasim", "Sabri"};

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	/** Does this person go by a woman's name (only for the townsfolk; the soldiers are men). */
	public static boolean woman(UUID id) {
		return Math.floorMod(mix(id.getLeastSignificantBits() ^ 0x5EL), 2) == 0;
	}

	/** First name and surname of the person with this id, in his {@code style}'s manner; {@code east} for the classic towns. */
	public static String name(UUID id, CityStyle style, boolean east, boolean soldier) {
		long h = mix(id.getMostSignificantBits() ^ id.getLeastSignificantBits() * 31);
		boolean f = !soldier && woman(id);
		String[] first;
		String[] last;
		boolean russian = false;
		switch (style == CityStyle.CLASSIC ? (east ? CityStyle.SOVIET : CityStyle.AMERICAN) : style) {
			case SOVIET -> {
				first = f ? RU_F : RU_M;
				last = RU_S;
				russian = true;
			}
			case EUROPEAN -> {
				first = f ? EU_F : EU_M;
				last = EU_S;
			}
			case DESERT -> {
				first = f ? AR_F : AR_M;
				last = AR_S;
			}
			default -> {
				first = f ? EN_F : EN_M;
				last = EN_S;
			}
		}
		String a = first[(int) Math.floorMod(h, (long) first.length)];
		String b = last[(int) Math.floorMod(h >>> 20, (long) last.length)];
		if (russian && f) {
			// Russian surnames take the feminine ending.
			b = b.endsWith("ий") ? b.substring(0, b.length() - 2) + "ая" : b + "а";
		}
		return a + " " + b;
	}

	/** A roll from 0 to n - 1 for the person and the moment (what he says this time). */
	public static int roll(UUID id, long time, int n) {
		return (int) Math.floorMod(mix(id.getLeastSignificantBits() + time / 40), (long) n);
	}
}
