package com.stasdoto.airdefense.client.nation;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.nation.SoldierEntity;

/**
 * Paints people (64x64 player-style skins) from a seed: skin tone across the whole real human range, eye colour,
 * hair colour and style, beards, and clothes for the job (villager professions, guards, soldiers in camouflage with
 * an armband in their country's colour, bandits). Looks only - nothing here changes what anyone can do.
 */
public final class SkinBaker {
	private static final Map<Long, Identifier> CACHE = new HashMap<>();
	private static final Map<Identifier, net.minecraft.world.entity.player.PlayerSkin> SKINS = new HashMap<>();

	/** From very light to very dark, in roughly even steps. */
	static final int[] SKIN = {0xFFF6DCC8, 0xFFEFC9AE, 0xFFE3B595, 0xFFD5A07E, 0xFFC48A66, 0xFFAE7451, 0xFF955F3E, 0xFF7B4A2E,
			0xFF603823, 0xFF472818};
	private static final int[] HAIR = {0xFF17120F, 0xFF17120F, 0xFF17120F, 0xFF33231A, 0xFF33231A, 0xFF5E412B, 0xFF7E5E3E,
			0xFFC4A266, 0xFF8A3A1E, 0xFF8C8C8C};
	private static final int[] EYES = {0xFF4A2E1A, 0xFF4A2E1A, 0xFF4A2E1A, 0xFF2A1A10, 0xFF3A6CA6, 0xFF4E7A3A, 0xFF7A8590};

	private SkinBaker() {
	}

	// ------------------------------------------------------------------------------------------------
	// Public entry points (cached textures)

	public static Identifier soldier(int look, int role, int color) {
		long key = ((long) look << 32) ^ ((long) role << 8) ^ (color + 1) ^ 0x5011D1E5L << 40;
		return bake(key, img -> {
			Person p = Person.of(look);
			paintPerson(img, p);
			switch (role) {
				case SoldierEntity.BANDIT -> bandit(img, p, look);
				case SoldierEntity.SOLDIER -> soldierClothes(img, p, color);
				default -> guardClothes(img, p, color);
			}
		});
	}

	public static Identifier villager(int look, String profession, String type) {
		long key = ((long) look << 32) ^ ((long) profession.hashCode() << 12) ^ type.hashCode() ^ 0x7111A6EL << 44;
		return bake(key, img -> {
			Person p = Person.of(look);
			paintPerson(img, p);
			villagerClothes(img, p, profession, type, look);
		});
	}

	/**
	 * The texture as a player skin: the player model draws its body from the render state's skin, so the people's
	 * render states carry this one (otherwise they would all look like the default player).
	 */
	public static net.minecraft.world.entity.player.PlayerSkin skin(Identifier texture) {
		return SKINS.computeIfAbsent(texture, id -> {
			net.minecraft.core.ClientAsset.ResourceTexture body = new net.minecraft.core.ClientAsset.ResourceTexture(id, id);
			return net.minecraft.world.entity.player.PlayerSkin.insecure(body, null, null,
					net.minecraft.world.entity.player.PlayerModelType.WIDE);
		});
	}

	private static Identifier bake(long key, java.util.function.Consumer<NativeImage> painter) {
		Identifier id = CACHE.get(key);
		if (id != null) {
			return id;
		}
		NativeImage img = new NativeImage(64, 64, true);
		img.fillRect(0, 0, 64, 64, 0);
		painter.accept(img);
		id = AirDefense.id("dynamic/person_" + Long.toHexString(key));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "airdefense person", img));
		CACHE.put(key, id);
		return id;
	}

	// ------------------------------------------------------------------------------------------------
	// The person underneath: skin, face, hair

	record Person(int skin, int hair, int eyes, int hairStyle, int beard, boolean female, int seed) {
		static Person of(int look) {
			Random r = new Random(look * 0x9E3779B97F4A7C15L);
			boolean female = r.nextInt(100) < 45;
			int style = female ? (r.nextInt(100) < 70 ? 3 : r.nextInt(100) < 50 ? 4 : 0) : pick(r, 0, 0, 0, 1, 1, 2, 4);
			int beard = female ? 0 : pick(r, 0, 0, 0, 1, 2);
			return new Person(SKIN[r.nextInt(SKIN.length)], HAIR[r.nextInt(HAIR.length)], EYES[r.nextInt(EYES.length)], style, beard,
					female, r.nextInt());
		}
	}

	private static int pick(Random r, int... options) {
		return options[r.nextInt(options.length)];
	}

	/** One face of a cube part: where it is on the skin and its size. */
	private record Face(int x, int y, int w, int h) {
		void fill(NativeImage img, int color) {
			for (int j = 0; j < h; j++) {
				for (int i = 0; i < w; i++) {
					img.setPixel(x + i, y + j, color);
				}
			}
		}

		void set(NativeImage img, int i, int j, int color) {
			if (i >= 0 && i < w && j >= 0 && j < h) {
				img.setPixel(x + i, y + j, color);
			}
		}

		void row(NativeImage img, int j, int color) {
			for (int i = 0; i < w; i++) {
				set(img, i, j, color);
			}
		}
	}

	/** A cube on the skin (vanilla layout): faces top, bottom, right, front, left, back. */
	private record Part(int u, int v, int w, int h, int d) {
		Face top() {
			return new Face(u + d, v, w, d);
		}

		Face bottom() {
			return new Face(u + d + w, v, w, d);
		}

		Face right() {
			return new Face(u, v + d, d, h);
		}

		Face front() {
			return new Face(u + d, v + d, w, h);
		}

		Face left() {
			return new Face(u + d + w, v + d, d, h);
		}

		Face back() {
			return new Face(u + d + w + d, v + d, w, h);
		}

		Face[] sides() {
			return new Face[]{right(), front(), left(), back()};
		}

		void fill(NativeImage img, int color) {
			top().fill(img, color);
			bottom().fill(img, color);
			for (Face f : sides()) {
				f.fill(img, color);
			}
		}

		/** Rows {@code from..to} (from the top) of all four sides. */
		void band(NativeImage img, int from, int to, int color) {
			for (Face f : sides()) {
				for (int j = from; j <= to; j++) {
					f.row(img, j, color);
				}
			}
		}
	}

	static final Part HEAD = new Part(0, 0, 8, 8, 8);
	static final Part BODY = new Part(16, 16, 8, 12, 4);
	static final Part RIGHT_ARM = new Part(40, 16, 4, 12, 4);
	static final Part LEFT_ARM = new Part(32, 48, 4, 12, 4);
	static final Part RIGHT_LEG = new Part(0, 16, 4, 12, 4);
	static final Part LEFT_LEG = new Part(16, 48, 4, 12, 4);

	static int shade(int argb, float k) {
		int r = Math.min(255, Math.round(((argb >> 16) & 255) * k));
		int g = Math.min(255, Math.round(((argb >> 8) & 255) * k));
		int b = Math.min(255, Math.round((argb & 255) * k));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static void paintPerson(NativeImage img, Person p) {
		int skin = p.skin;
		for (Part part : new Part[]{HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG}) {
			part.fill(img, skin);
		}
		// Face: brows, eyes, nose, mouth.
		Face f = HEAD.front();
		int brow = shade(p.hair, 0.85f);
		int white = 0xFFF2F0EC;
		f.set(img, 1, 3, brow);
		f.set(img, 2, 3, brow);
		f.set(img, 5, 3, brow);
		f.set(img, 6, 3, brow);
		f.set(img, 1, 4, white);
		f.set(img, 2, 4, p.eyes);
		f.set(img, 5, 4, p.eyes);
		f.set(img, 6, 4, white);
		f.set(img, 3, 5, shade(skin, 0.9f));
		f.set(img, 4, 5, shade(skin, 0.88f));
		int lips = mix(shade(skin, 0.72f), 0xFF9A4A44, p.female ? 0.45f : 0.25f);
		f.set(img, 3, 6, lips);
		f.set(img, 4, 6, lips);
		if (p.female) {
			f.set(img, 2, 6, shade(skin, 0.92f));
			f.set(img, 5, 6, shade(skin, 0.92f));
		}
		// Ears on the sides, a darker neck under the chin.
		HEAD.right().set(img, 3, 4, shade(skin, 0.9f));
		HEAD.left().set(img, 4, 4, shade(skin, 0.9f));
		HEAD.bottom().fill(img, shade(skin, 0.85f));
		hair(img, p);
	}

	private static void hair(NativeImage img, Person p) {
		int h = p.hair;
		int h2 = shade(h, 1.18f);
		Random r = new Random(p.seed);
		int style = p.hairStyle;
		if (style != 2) {
			Face top = HEAD.top();
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					top.set(img, i, j, r.nextInt(5) == 0 ? h2 : h);
				}
			}
		}
		Face front = HEAD.front();
		Face right = HEAD.right();
		Face left = HEAD.left();
		Face back = HEAD.back();
		int sideRows = switch (style) {
			case 1 -> 1;
			case 2 -> 0;
			case 3 -> 8;
			case 4 -> 4;
			default -> 3;
		};
		int backRows = switch (style) {
			case 1 -> 2;
			case 2 -> 0;
			case 3 -> 8;
			case 4 -> 5;
			default -> 4;
		};
		for (int j = 0; j < sideRows; j++) {
			for (int i = 0; i < 8; i++) {
				// Long hair leaves the ear and the cheek free at the front.
				boolean front3 = style == 3 && j >= 3 && i >= 5;
				if (!front3) {
					right.set(img, i, j, r.nextInt(6) == 0 ? h2 : h);
					left.set(img, 7 - i, j, r.nextInt(6) == 0 ? h2 : h);
				}
			}
		}
		for (int j = 0; j < backRows; j++) {
			back.row(img, j, h);
		}
		if (style != 2) {
			front.row(img, 0, h);
			if (style == 0 || style == 4 || style == 3) {
				front.set(img, 0, 1, h);
				front.set(img, 7, 1, h);
				if (style != 0) {
					front.set(img, 1, 1, h);
					front.set(img, 6, 1, h);
				}
			}
			if (style == 3) {
				for (int j = 1; j < 8; j++) {
					front.set(img, 0, j, h);
					front.set(img, 7, j, h);
				}
			}
		}
		// Beards: stubble darkens the jaw, a full beard covers it.
		if (p.beard > 0) {
			int b = p.beard == 1 ? mix(p.skin, h, 0.35f) : h;
			for (int i = 1; i < 7; i++) {
				if (p.beard == 2 || i != 3 && i != 4) {
					front.set(img, i, 7, b);
				}
			}
			front.set(img, 1, 6, b);
			front.set(img, 6, 6, b);
			if (p.beard == 2) {
				front.set(img, 1, 5, b);
				front.set(img, 6, 5, b);
				front.set(img, 2, 7, b);
				front.set(img, 5, 7, b);
				for (int j = 5; j < 8; j++) {
					right.set(img, 6, j, b);
					right.set(img, 7, j, b);
					left.set(img, 0, j, b);
					left.set(img, 1, j, b);
				}
			}
			HEAD.bottom().fill(img, mix(shade(p.skin, 0.85f), b, 0.7f));
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Clothes

	/** Shirt on body and arms (hands stay bare), trousers, shoes. */
	private static void outfit(NativeImage img, Person p, int shirt, int sleeves, int trousers, int shoes, int sleeveRows) {
		BODY.fill(img, shirt);
		for (Part arm : new Part[]{RIGHT_ARM, LEFT_ARM}) {
			arm.top().fill(img, sleeves);
			arm.band(img, 0, sleeveRows - 1, sleeves);
		}
		for (Part leg : new Part[]{RIGHT_LEG, LEFT_LEG}) {
			leg.fill(img, trousers);
			leg.band(img, 10, 11, shoes);
			leg.bottom().fill(img, shade(shoes, 0.8f));
		}
		// Belt.
		BODY.band(img, 9, 9, shade(trousers, 0.7f));
	}

	private static int camo(Random r, int[] palette) {
		int v = r.nextInt(100);
		return v < 45 ? palette[0] : v < 75 ? palette[1] : v < 92 ? palette[2] : palette[3];
	}

	private static void camoPart(NativeImage img, Part part, int[] palette, Random r, int fromRow, int toRow) {
		for (Face f : part.sides()) {
			for (int j = fromRow; j <= toRow; j++) {
				for (int i = 0; i < f.w; i++) {
					f.set(img, i, j, camo(r, palette));
				}
			}
		}
		Face t = part.top();
		for (int j = 0; j < t.h; j++) {
			for (int i = 0; i < t.w; i++) {
				t.set(img, i, j, camo(r, palette));
			}
		}
	}

	private static int dye(int color, int fallback) {
		return color < 0 ? fallback : 0xFF000000 | DyeColor.byId(color).getTextureDiffuseColor();
	}

	private static void soldierClothes(NativeImage img, Person p, int color) {
		Random r = new Random(p.seed ^ 0xC4A40L);
		int[] palette = {0xFF5C6338, 0xFF4A4F2D, 0xFF7A6E48, 0xFF2F3320};
		camoPart(img, BODY, palette, r, 0, 11);
		camoPart(img, RIGHT_ARM, palette, r, 0, 9);
		camoPart(img, LEFT_ARM, palette, r, 0, 9);
		camoPart(img, RIGHT_LEG, palette, r, 0, 8);
		camoPart(img, LEFT_LEG, palette, r, 0, 8);
		for (Part leg : new Part[]{RIGHT_LEG, LEFT_LEG}) {
			leg.band(img, 9, 11, 0xFF2A2420);
			leg.bottom().fill(img, 0xFF1E1A16);
		}
		BODY.band(img, 9, 9, 0xFF2E2A22);
		// Gloves.
		for (Part arm : new Part[]{RIGHT_ARM, LEFT_ARM}) {
			arm.band(img, 10, 11, 0xFF2A2A26);
			arm.bottom().fill(img, 0xFF2A2A26);
		}
		// The armband in the country's colour on the left arm.
		LEFT_ARM.band(img, 2, 3, dye(color, 0xFFE0E0E0));
		RIGHT_ARM.band(img, 2, 2, dye(color, 0xFFE0E0E0));
	}

	private static void guardClothes(NativeImage img, Person p, int color) {
		int base = color < 0 ? 0xFF4A5038 : shade(mix(dye(color, 0xFF4A5038), 0xFF3A3A3A, 0.55f), 0.9f);
		outfit(img, p, base, base, 0xFF2E3034, 0xFF1A1A1A, 10);
		// Shoulder boards and a badge.
		BODY.top().fill(img, shade(base, 1.15f));
		BODY.front().set(img, 1, 2, 0xFFD8B850);
		LEFT_ARM.band(img, 2, 3, dye(color, 0xFFE0E0E0));
		for (Part arm : new Part[]{RIGHT_ARM, LEFT_ARM}) {
			arm.band(img, 10, 11, 0xFF2A2A26);
		}
	}

	private static void bandit(NativeImage img, Person p, int look) {
		Random r = new Random(look ^ 0xBADL);
		int hoodie = pick(r, 0xFF26272A, 0xFF3A3530, 0xFF2E3A30, 0xFF4A4A4A);
		outfit(img, p, hoodie, hoodie, pick(r, 0xFF3A4A62, 0xFF2A2C30, 0xFF4A4436), 0xFF2A2420, 11);
		BODY.front().row(img, 7, shade(hoodie, 0.8f));
		if (r.nextBoolean()) {
			// Balaclava: only the eyes show.
			int mask = 0xFF141414;
			for (Face f : HEAD.sides()) {
				f.fill(img, mask);
			}
			HEAD.top().fill(img, mask);
			Face front = HEAD.front();
			front.set(img, 1, 4, 0xFFF2F0EC);
			front.set(img, 2, 4, p.eyes);
			front.set(img, 5, 4, p.eyes);
			front.set(img, 6, 4, 0xFFF2F0EC);
			for (int i = 1; i < 7; i++) {
				front.set(img, i, 3, p.skin);
			}
		}
	}

	private static void villagerClothes(NativeImage img, Person p, String profession, String type, int look) {
		Random r = new Random(look ^ 0x7E57L);
		// Everyday clothes depend on the climate the village lives in.
		int[] shirts = switch (type) {
			case "desert" -> new int[]{0xFFE8DCC0, 0xFFD8C8A0, 0xFFC9B48A};
			case "savanna" -> new int[]{0xFFC9783C, 0xFFB5652E, 0xFFD9A040};
			case "snow" -> new int[]{0xFF8A9CB0, 0xFF6E7F94, 0xFFB0B8C4};
			case "swamp" -> new int[]{0xFF4E6248, 0xFF5E6E50, 0xFF3E4A3A};
			case "taiga" -> new int[]{0xFF7A5038, 0xFF5A6E8A, 0xFF8A6A48};
			case "jungle" -> new int[]{0xFF3E8A4A, 0xFFD9B040, 0xFF2E7A8A};
			default -> new int[]{0xFF6A8AB0, 0xFF9A6A48, 0xFFB8B0A0, 0xFF7A9A6A};
		};
		int shirt = shirts[r.nextInt(shirts.length)];
		int trousers = pick(r, 0xFF4A3A2A, 0xFF3A4458, 0xFF5A5040, 0xFF2E2E34);
		int shoes = 0xFF3A2A1E;
		switch (profession) {
			case "farmer" -> {
				outfit(img, p, 0xFFE0D8C0, 0xFFE0D8C0, 0xFF5A4A80, shoes, 6);
				// Overalls and a straw hat.
				BODY.band(img, 6, 11, 0xFF4A5A8A);
				BODY.front().set(img, 2, 3, 0xFF4A5A8A);
				BODY.front().set(img, 5, 3, 0xFF4A5A8A);
				hat(img, 0xFFD8C070, 1);
			}
			case "fisherman" -> {
				outfit(img, p, 0xFF2E6E8A, 0xFF2E6E8A, trousers, 0xFF2A2A2A, 10);
				hat(img, 0xFFD8B040, 1);
			}
			case "shepherd" -> outfit(img, p, 0xFFE8E4DC, 0xFF7A5A3A, trousers, shoes, 8);
			case "fletcher" -> outfit(img, p, 0xFF4E7A3E, 0xFF4E7A3E, 0xFF5A4630, shoes, 9);
			case "librarian" -> {
				outfit(img, p, 0xFFE8E8E0, 0xFFE8E8E0, 0xFF2E2E34, 0xFF1A1A1A, 10);
				BODY.band(img, 0, 8, 0xFF7A2A2A);
				BODY.front().row(img, 0, 0xFFE8E8E0);
			}
			case "cartographer" -> {
				outfit(img, p, 0xFFE8E8E0, 0xFFE8E8E0, 0xFF3A3A44, shoes, 10);
				BODY.band(img, 0, 8, 0xFF2E3E6A);
			}
			case "cleric" -> {
				outfit(img, p, 0xFF5A2E6E, 0xFF5A2E6E, 0xFF5A2E6E, 0xFF2A2A2A, 11);
				BODY.front().set(img, 3, 3, 0xFFE0C050);
				BODY.front().set(img, 4, 3, 0xFFE0C050);
			}
			case "armorer", "weaponsmith", "toolsmith" -> {
				outfit(img, p, 0xFF6A6A6A, 0xFF6A6A6A, 0xFF3A3A3A, 0xFF1E1E1E, 4);
				int apron = profession.equals("weaponsmith") ? 0xFF2A2A2A : profession.equals("toolsmith") ? 0xFF6A4A2E : 0xFF3A3A3A;
				BODY.front().fill(img, apron);
				BODY.front().row(img, 0, 0xFF6A6A6A);
			}
			case "butcher" -> {
				outfit(img, p, 0xFFB03A3A, 0xFFB03A3A, 0xFF3A3A3A, shoes, 6);
				BODY.front().fill(img, 0xFFF0F0EC);
			}
			case "leatherworker" -> outfit(img, p, shirt, shirt, 0xFF6A4A2E, 0xFF4A3220, 8);
			case "mason" -> {
				outfit(img, p, 0xFF8A8A84, 0xFF8A8A84, 0xFF5A5A54, shoes, 9);
				BODY.front().fill(img, 0xFF5A4E44);
			}
			case "nitwit" -> outfit(img, p, 0xFF3E7A3E, 0xFF3E7A3E, 0xFF3E7A3E, shoes, 11);
			default -> outfit(img, p, shirt, shirt, trousers, shoes, 7);
		}
	}

	/** A hat: crown on the top of the head and a brim row round the sides. */
	private static void hat(NativeImage img, int color, int brimRow) {
		HEAD.top().fill(img, color);
		for (Face f : HEAD.sides()) {
			for (int j = 0; j <= brimRow; j++) {
				f.row(img, j, j == brimRow ? shade(color, 0.85f) : color);
			}
		}
	}
}
