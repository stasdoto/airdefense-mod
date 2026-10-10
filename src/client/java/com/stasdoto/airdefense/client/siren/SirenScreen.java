package com.stasdoto.airdefense.client.siren;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.stasdoto.airdefense.client.ui.UiButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.stasdoto.airdefense.client.map.TacticalMapScreen;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.siren.Sirens;
import com.stasdoto.airdefense.siren.SirenNet;

/**
 * The tablet's air raid warning page (1.24): alert or all clear in every town at once, in one town, or one siren by
 * hand. Towns on the left (nearest first), the sirens nearest to you on the right.
 */
public class SirenScreen extends Screen {
	private static final int ROW = 22;
	private int refresh;
	private int townScroll;
	private int sirenScroll;
	private int x0;
	private int top;
	private int colW;
	/** Row buttons (worked out from the text widths, so they fit in any language and at any window size). */
	private int alertW;
	private int clearW;
	private int modeW;

	public SirenScreen() {
		super(Component.translatable("screen.airdefense.siren.title"));
	}

	@Override
	protected void init() {
		colW = Math.min(260, (width - 30) / 2);
		x0 = (width - colW * 2 - 10) / 2;
		int total = colW * 2 + 10;
		Component[] labels = {Component.translatable("screen.airdefense.siren.all_alert"), Component.translatable("screen.airdefense.siren.all_clear"),
				Component.translatable("screen.airdefense.siren.silence"), Component.translatable("screen.airdefense.siren.back")};
		UiButton.OnPress[] actions = {b -> SirenClient.send(SirenNet.Action.ALL, 1, 0), b -> SirenClient.send(SirenNet.Action.ALL, 0, 0),
				b -> SirenClient.send(SirenNet.Action.SILENCE, 0, 0), b -> minecraft.gui.setScreen(new TacticalMapScreen())};
		int need = 0;
		for (Component c : labels) {
			need = Math.max(need, font.width(c) + 14);
		}
		// One row of four if they fit, else two rows of two.
		boolean oneRow = need * 4 + 12 <= total;
		int bw = oneRow ? (total - 12) / 4 : (total - 4) / 2;
		for (int i = 0; i < 4; i++) {
			int col = oneRow ? i : i % 2;
			int row = oneRow ? 0 : i / 2;
			addRenderableWidget(UiButton.create(labels[i], actions[i]).bounds(x0 + col * (bw + 4), 22 + row * 22, bw, 20).build());
		}
		top = oneRow ? 50 : 72;
		alertW = font.width(Component.translatable("screen.airdefense.siren.alert")) + 10;
		clearW = font.width(Component.translatable("screen.airdefense.siren.clear")) + 10;
		modeW = 0;
		for (int m = 0; m < 3; m++) {
			modeW = Math.max(modeW, font.width(Component.translatable("screen.airdefense.siren.mode." + m)) + 8);
		}
		SirenClient.send(SirenNet.Action.REFRESH, 0, 0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (minecraft.player == null || DesignatorItem.held(minecraft.player) == null && !minecraft.player.getAbilities().instabuild) {
			onClose();
			return;
		}
		if (++refresh % 10 == 0) {
			SirenClient.send(SirenNet.Action.REFRESH, 0, 0);
		}
	}

	private int rows() {
		return Math.max(1, (height - top - 30) / ROW);
	}

	private int clearX() {
		return x0 + colW - 2 - clearW;
	}

	private int alertX() {
		return clearX() - 2 - alertW;
	}

	private int modeX(int m) {
		return x0 + colW + 10 + colW - 2 - (3 - m) * (modeW + 2);
	}

	/** Where the "Alert" button of a town row is (gui coordinates of its centre; for the tests). */
	public int[] townAlertCenter(int row) {
		return new int[]{alertX() + alertW / 2, top + 14 + row * ROW + 2 + 8};
	}

	private List<SirenNet.Town> towns() {
		return SirenClient.state == null ? List.of() : SirenClient.state.towns();
	}

	private List<SirenNet.Siren> sirens() {
		return SirenClient.state == null ? List.of() : SirenClient.state.sirens();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int d = (int) -Math.signum(scrollY);
		if (mouseX < x0 + colW + 5) {
			townScroll = Math.max(0, Math.min(Math.max(0, towns().size() - rows()), townScroll + d));
		} else {
			sirenScroll = Math.max(0, Math.min(Math.max(0, sirens().size() - rows()), sirenScroll + d));
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.y() < top + 14) {
			return false;
		}
		int row = (int) ((event.y() - top - 14) / ROW);
		double x = event.x();
		List<SirenNet.Town> towns = towns();
		if (x >= x0 && x < x0 + colW && row + townScroll < towns.size()) {
			SirenNet.Town t = towns.get(row + townScroll);
			if (x >= alertX() && x < alertX() + alertW) {
				SirenClient.send(SirenNet.Action.TOWN, t.id(), 0);
				return true;
			}
			if (x >= clearX() && x < clearX() + clearW) {
				SirenClient.send(SirenNet.Action.TOWN_CLEAR, t.id(), 0);
				return true;
			}
		}
		List<SirenNet.Siren> sirens = sirens();
		int sx = x0 + colW + 10;
		if (x >= sx && x < sx + colW && row + sirenScroll < sirens.size()) {
			SirenNet.Siren s = sirens.get(row + sirenScroll);
			for (int m = 0; m < 3; m++) {
				if (x >= modeX(m) && x < modeX(m) + modeW) {
					SirenClient.send(SirenNet.Action.SIREN_MODE, m, s.pos());
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, com.stasdoto.airdefense.client.ui.Ui.SCRIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		boolean all = SirenClient.state != null && SirenClient.state.everywhere();
		g.text(font, title, x0, 8, 0xFFFFFFFF);
		int nameW = alertX() - x0 - 6;
		if (all) {
			Component c = Component.translatable("screen.airdefense.siren.everywhere");
			g.text(font, c, x0 + colW * 2 + 10 - font.width(c), 8, blink() ? 0xFFFF5040 : 0xFFB03020);
		}
		// Towns.
		g.text(font, Component.translatable("screen.airdefense.siren.towns"), x0, top, 0xFFB8C8D8);
		List<SirenNet.Town> towns = towns();
		int rows = rows();
		for (int i = 0; i < rows && i + townScroll < towns.size(); i++) {
			SirenNet.Town t = towns.get(i + townScroll);
			int y = top + 14 + i * ROW;
			int bg = t.signal() == Sirens.ALERT ? (blink() ? 0x80802020 : 0x60601818) : t.signal() == Sirens.CLEAR ? 0x50206020 : 0x40303840;
			g.fill(x0, y, x0 + colW, y + ROW - 2, bg);
			g.text(font, fit((t.capital() ? "★ " : "") + t.name(), nameW), x0 + 4, y + 2, 0xFFFFFFFF);
			Component sub = Component.translatable("screen.airdefense.siren.town_line",
					Component.translatable("screen.airdefense.siren.signal." + signalName(t.signal())), t.sirens(), t.distance());
			small(g, fit(sub.getString(), (int) (nameW / 0.75f)), x0 + 4, y + 12, 0xFFC0C8D0);
			button(g, alertX(), y + 2, alertW, Component.translatable("screen.airdefense.siren.alert"), 0xFFB02818, mouseX, mouseY);
			button(g, clearX(), y + 2, clearW, Component.translatable("screen.airdefense.siren.clear"), 0xFF287838, mouseX, mouseY);
		}
		if (towns.isEmpty()) {
			small(g, Component.translatable("screen.airdefense.siren.no_towns"), x0 + 4, top + 16, 0xFFA0A0A0);
		}
		// Sirens.
		int sx = x0 + colW + 10;
		g.text(font, Component.translatable("screen.airdefense.siren.sirens"), sx, top, 0xFFB8C8D8);
		List<SirenNet.Siren> sirens = sirens();
		for (int i = 0; i < rows && i + sirenScroll < sirens.size(); i++) {
			SirenNet.Siren s = sirens.get(i + sirenScroll);
			int y = top + 14 + i * ROW;
			int bg = s.signal() == Sirens.ALERT ? (blink() ? 0x80802020 : 0x60601818) : s.signal() == Sirens.CLEAR ? 0x50206020 : 0x40303840;
			g.fill(sx, y, sx + colW, y + ROW - 2, bg);
			BlockPos p = BlockPos.of(s.pos());
			String where = s.town().isEmpty() ? Component.translatable("screen.airdefense.siren.field").getString() : s.town();
			int sw = modeX(0) - sx - 6;
			g.text(font, fit(where, sw), sx + 4, y + 2, 0xFFFFFFFF);
			Component line = Component.translatable("screen.airdefense.siren.siren_line", p.getX(), p.getZ(), s.distance(),
					Component.translatable("screen.airdefense.siren.signal." + signalName(s.signal())));
			small(g, fit(line.getString(), (int) (sw / 0.75f)), sx + 4, y + 12, 0xFFC0C8D0);
			for (int m = 0; m < 3; m++) {
				int c = m == s.mode() ? (m == Sirens.MODE_ON ? 0xFFB02818 : m == Sirens.MODE_OFF ? 0xFF505860 : 0xFF2860A0) : 0xFF303840;
				button(g, modeX(m), y + 2, modeW, Component.translatable("screen.airdefense.siren.mode." + m), c, mouseX, mouseY);
			}
		}
		if (sirens.isEmpty()) {
			small(g, Component.translatable("screen.airdefense.siren.no_sirens"), sx + 4, top + 16, 0xFFA0A0A0);
		}
		small(g, fit(Component.translatable("screen.airdefense.siren.hint").getString(), (int) ((colW * 2 + 10) / 0.75f)), x0, height - 14, 0xFF8090A0);
	}

	private static String signalName(int s) {
		return s == Sirens.ALERT ? "alert" : s == Sirens.CLEAR ? "clear" : "off";
	}

	private boolean blink() {
		return minecraft.level != null && minecraft.level.getGameTime() / 10 % 2 == 0;
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, Component text, int color, int mx, int my) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
		g.fill(x, y, x + w, y + 16, hover ? 0xFFFFFFFF : 0xFF101010);
		g.fill(x + 1, y + 1, x + w - 1, y + 15, color);
		g.text(font, text, x + (w - font.width(text)) / 2, y + 4, 0xFFFFFFFF);
	}

	private void small(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
		small(g, text.getString(), x, y, color);
	}

	private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	/** The text cut to fit the width, with an ellipsis. */
	private String fit(String text, int w) {
		if (font.width(text) <= w) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, w - font.width("…"))) + "…";
	}
}
