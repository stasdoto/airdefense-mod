package com.stasdoto.airdefense.client.ui;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 1.43: the mod's new look for its screens - one palette (dark warm greys, a terracotta accent) and the pieces they
 * are built of: soft-cornered panels with a shadow, cards, chips, bars, big titles. Every new screen draws with these,
 * so the whole interface looks like one thing.
 */
public final class Ui {
	/** Behind a screen: the world, darkened. */
	public static final int SCRIM = 0xB80B0C0E;
	public static final int PANEL = 0xF4191A1D;
	public static final int PANEL_2 = 0xF4212226;
	public static final int CARD = 0xFF25262B;
	public static final int CARD_HOVER = 0xFF2D2E34;
	public static final int CARD_ON = 0xFF33302D;
	public static final int LINE = 0xFF34363C;
	public static final int LINE_SOFT = 0x30FFFFFF;
	public static final int TEXT = 0xFFF0EEEA;
	public static final int DIM = 0xFFA6A29B;
	public static final int FAINT = 0xFF6F6C66;
	public static final int ACCENT = 0xFFD97757;
	public static final int ACCENT_HI = 0xFFE88E70;
	public static final int ACCENT_LO = 0xFF9E523A;
	public static final int OK = 0xFF86C17F;
	public static final int BAD = 0xFFE36A5C;
	public static final int GOLD = 0xFFE9BC62;
	public static final int BLUE = 0xFF7EA6E0;

	private Ui() {
	}

	/** How far each of the corner's columns is cut down, by radius. */
	private static final int[][] CUTS = {{}, {1}, {2, 1}, {3, 1, 1}};

	/** A filled rectangle with rounded corners (radius 1..3 pixels). */
	public static void round(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
		r = Math.max(0, Math.min(3, r));
		if (r == 0 || x1 - x0 < 2 * r + 1 || y1 - y0 < 2 * r + 1) {
			g.fill(x0, y0, x1, y1, color);
			return;
		}
		g.fill(x0 + r, y0, x1 - r, y1, color);
		int[] cut = CUTS[r];
		for (int i = 0; i < r; i++) {
			g.fill(x0 + i, y0 + cut[i], x0 + i + 1, y1 - cut[i], color);
			g.fill(x1 - i - 1, y0 + cut[i], x1 - i, y1 - cut[i], color);
		}
	}

	/** A soft shadow under a panel (a few faint layers). */
	public static void shadow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		round(g, x0 - 1, y0 + 1, x1 + 1, y1 + 4, 3, 0x30000000);
		round(g, x0, y0 + 1, x1, y1 + 2, 3, 0x40000000);
	}

	/** A panel: shadow, a hairline edge, the body, and a faint light along its top. */
	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		shadow(g, x0, y0, x1, y1);
		round(g, x0, y0, x1, y1, 3, LINE);
		round(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 2, PANEL);
		g.fill(x0 + 3, y0 + 1, x1 - 3, y0 + 2, LINE_SOFT);
	}

	/** A card inside a panel (hover and selected states). */
	public static void card(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean hover, boolean on) {
		if (on) {
			round(g, x0, y0, x1, y1, 2, ACCENT);
			round(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 2, CARD_ON);
		} else {
			round(g, x0, y0, x1, y1, 2, hover ? CARD_HOVER : CARD);
		}
	}

	/** A thin line across. */
	public static void divider(GuiGraphicsExtractor g, int x0, int x1, int y) {
		g.fill(x0, y, x1, y + 1, LINE);
	}

	/** A little rounded label: e.g. "at war", "your country". Returns its width. */
	public static int chip(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int fg, int bg) {
		int w = font.width(text) + 8;
		round(g, x, y, x + w, y + 11, 2, bg);
		g.text(font, text, x + 4, y + 2, fg, false);
		return w;
	}

	/** A bar filled to {@code part} (0..1). */
	public static void bar(GuiGraphicsExtractor g, int x0, int y, int x1, float part, int color) {
		round(g, x0, y, x1, y + 4, 1, 0xFF34363C);
		int w = Math.round((x1 - x0) * Math.max(0, Math.min(1, part)));
		if (w > 0) {
			round(g, x0, y, x0 + Math.max(w, 2), y + 4, 1, color);
		}
	}

	/** A flag: a small rectangle of the country's colour with a dark edge and a light top. */
	public static void flag(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0C0C0E);
		g.fill(x, y, x + w, y + h, argb);
		g.fill(x, y, x + w, y + 1, 0x40FFFFFF);
		g.fill(x, y + h - 1, x + w, y + h, 0x30000000);
	}

	/** Text scaled up (titles). */
	public static void big(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float scale, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		g.text(font, text, 0, 0, color, false);
		pose.popMatrix();
	}

	/** Text cut to a width with an ellipsis. */
	public static void clipped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
		if (font.width(text) <= width) {
			g.text(font, text, x, y, color, false);
			return;
		}
		String s = text.getString();
		while (!s.isEmpty() && font.width(s + "…") > width) {
			s = s.substring(0, s.length() - 1);
		}
		g.text(font, s + "…", x, y, color, false);
	}

	/** Wrapped text, at most {@code lines} lines; returns the height used. */
	public static int wrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int lines, int color) {
		var list = font.split(text, width);
		int n = Math.min(lines, list.size());
		for (int i = 0; i < n; i++) {
			FormattedCharSequence line = list.get(i);
			g.text(font, line, x, y + i * 10, color, false);
		}
		return n * 10;
	}

	/** Blends two colours (t = 0: a, 1: b). */
	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24;
		int ar = a >> 16 & 255;
		int ag = a >> 8 & 255;
		int ab = a & 255;
		int ba = b >>> 24;
		int br = b >> 16 & 255;
		int bg = b >> 8 & 255;
		int bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8
				| Math.round(ab + (bb - ab) * t);
	}

	/** People counted in a short form: 950, 12.4k, 1.2M. */
	public static String count(int n) {
		if (n < 1000) {
			return Integer.toString(n);
		}
		if (n < 1_000_000) {
			return String.format(java.util.Locale.ROOT, n < 10_000 ? "%.1fk" : "%.0fk", n / 1000.0);
		}
		return String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0);
	}
}
