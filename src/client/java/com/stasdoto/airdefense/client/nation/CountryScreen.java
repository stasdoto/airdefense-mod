package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.stasdoto.airdefense.client.map.AtlasClient;
import com.stasdoto.airdefense.client.map.AtlasTiles;
import com.stasdoto.airdefense.client.ui.Ui;
import com.stasdoto.airdefense.client.ui.UiButton;
import com.stasdoto.airdefense.nation.CityStyle;
import com.stasdoto.airdefense.nation.CountryActionPayload;
import com.stasdoto.airdefense.nation.CountryListPayload;
import com.stasdoto.airdefense.nation.Diplomacy;

/**
 * 1.43: choosing the country to play for. On the left the countries (nearest first), on the right the map with their
 * lands and capitals and, under it, the one picked: its ruler, its people, who plays for it, whom it fights. Below:
 * play for it (and go to its capital), play on your own, or decide later.
 */
public class CountryScreen extends Screen {
	private static final int CARD_H = 30;
	private static final int CARD_GAP = 3;
	/** Pixels per block on the map: from the whole region to a town. */
	private static final float[] ZOOMS = {0.03f, 0.045f, 0.07f, 0.1f, 0.15f, 0.22f, 0.33f};

	private CountryListPayload data;
	private int selected;
	private int scroll;
	private int zoom = 2;
	private double centerX;
	private double centerZ;
	private double goX;
	private double goZ;
	private boolean pressedInMap;
	private boolean dragged;
	private double pressX;
	private double pressY;
	private boolean decided;

	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int listX0;
	private int listX1;
	private int bodyY0;
	private int bodyY1;
	private int mapX0;
	private int mapX1;
	private int mapY1;

	private UiButton play;
	private UiButton travel;

	public CountryScreen(CountryListPayload data) {
		super(Component.translatable("screen.airdefense.country.title"));
		this.data = data;
		selected = 0;
		for (int i = 0; i < data.countries().size(); i++) {
			if (data.countries().get(i).id() == data.current()) {
				selected = i;
			}
		}
		CountryListPayload.Entry e = entry();
		centerX = goX = e != null ? e.capX() : data.spawnX();
		centerZ = goZ = e != null ? e.capZ() : data.spawnZ();
	}

	public void update(CountryListPayload p) {
		int id = entry() == null ? -1 : entry().id();
		data = p;
		selected = 0;
		for (int i = 0; i < p.countries().size(); i++) {
			if (p.countries().get(i).id() == id) {
				selected = i;
			}
		}
		updateButtons();
	}

	/** For the tests: pick the n-th country. */
	public void select(int i) {
		if (i >= 0 && i < data.countries().size()) {
			selected = i;
			CountryListPayload.Entry e = entry();
			goX = e.capX();
			goZ = e.capZ();
			int rows = visibleRows();
			if (selected < scroll) {
				scroll = selected;
			} else if (selected >= scroll + rows) {
				scroll = selected - rows + 1;
			}
			updateButtons();
		}
	}

	public CountryListPayload data() {
		return data;
	}

	private CountryListPayload.Entry entry() {
		return selected >= 0 && selected < data.countries().size() ? data.countries().get(selected) : null;
	}

	@Override
	protected void init() {
		int m = width < 480 ? 6 : 12;
		x0 = m;
		y0 = m;
		x1 = width - m;
		y1 = height - m;
		bodyY0 = y0 + 40;
		bodyY1 = y1 - 32;
		listX0 = x0 + 8;
		listX1 = listX0 + Mth.clamp((int) ((x1 - x0) * 0.4), 150, 230);
		mapX0 = listX1 + 8;
		mapX1 = x1 - 8;
		mapY1 = bodyY1 - detailsHeight() - 6;
		int by = y1 - 26;
		addRenderableWidget(new UiButton(x0 + 8, by, 74, 18, Component.translatable(data.first() ? "screen.airdefense.country.later"
				: "screen.airdefense.country.close"), UiButton.Style.GHOST, this::later));
		addRenderableWidget(new UiButton(x0 + 86, by, 92, 18, Component.translatable("screen.airdefense.country.alone"), UiButton.Style.SECONDARY,
				this::alone));
		int pw = Math.min(170, Math.max(110, (x1 - x0) / 3));
		play = addRenderableWidget(new UiButton(x1 - 8 - pw, by, pw, 18, Component.empty(), UiButton.Style.PRIMARY, this::join));
		int tw = Math.min(150, play.getX() - (x0 + 186) - 6);
		travel = addRenderableWidget(new UiButton(play.getX() - tw - 8, by, tw, 18, Component.translatable("screen.airdefense.country.travel"),
				UiButton.Style.TOGGLE, () -> {
				}));
		travel.on = data.first();
		travel.visible = tw > 60;
		updateButtons();
	}

	private int detailsHeight() {
		return 70;
	}

	private void updateButtons() {
		if (play == null) {
			return;
		}
		CountryListPayload.Entry e = entry();
		play.active = e != null && e.id() != data.current();
		play.setMessage(e == null ? Component.translatable("screen.airdefense.country.none")
				: e.id() == data.current() ? Component.translatable("screen.airdefense.country.yours")
				: Component.translatable("screen.airdefense.country.play"));
	}

	private void join() {
		CountryListPayload.Entry e = entry();
		if (e == null) {
			return;
		}
		decided = true;
		ClientPlayNetworking.send(new CountryActionPayload(CountryActionPayload.JOIN, e.id(), travel.on && travel.visible));
		onClose();
	}

	private void alone() {
		decided = true;
		ClientPlayNetworking.send(new CountryActionPayload(CountryActionPayload.ALONE, -1, false));
		onClose();
	}

	private void later() {
		onClose();
	}

	@Override
	public void onClose() {
		if (!decided && data.first()) {
			ClientPlayNetworking.send(new CountryActionPayload(CountryActionPayload.LATER, -1, false));
		}
		decided = true;
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------------------------------------
	// Drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, Ui.SCRIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// The map glides to the country picked.
		centerX += (goX - centerX) * 0.18;
		centerZ += (goZ - centerZ) * 0.18;
		Ui.panel(g, x0, y0, x1, y1);
		Ui.big(g, font, Component.translatable("screen.airdefense.country.title"), x0 + 10, y0 + 9, 1.5f, Ui.TEXT);
		Ui.clipped(g, font, Component.translatable("screen.airdefense.country.subtitle"), x0 + 10, y0 + 26, x1 - x0 - 20, Ui.DIM);
		Ui.divider(g, x0 + 1, x1 - 1, bodyY0 - 5);
		Ui.divider(g, x0 + 1, x1 - 1, y1 - 31);
		list(g, mouseX, mouseY);
		map(g, mouseX, mouseY);
		details(g);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private int visibleRows() {
		return Math.max(1, (bodyY1 - bodyY0 + CARD_GAP) / (CARD_H + CARD_GAP));
	}

	private void list(GuiGraphicsExtractor g, int mx, int my) {
		List<CountryListPayload.Entry> cs = data.countries();
		if (cs.isEmpty()) {
			Ui.wrapped(g, font, Component.translatable("screen.airdefense.country.empty"), listX0, bodyY0 + 4, listX1 - listX0, 6, Ui.DIM);
			return;
		}
		int rows = visibleRows();
		scroll = Mth.clamp(scroll, 0, Math.max(0, cs.size() - rows));
		int y = bodyY0;
		for (int i = scroll; i < cs.size() && i < scroll + rows; i++) {
			CountryListPayload.Entry e = cs.get(i);
			boolean hover = mx >= listX0 && mx < listX1 && my >= y && my < y + CARD_H;
			Ui.card(g, listX0, y, listX1, y + CARD_H, hover, i == selected);
			Ui.flag(g, listX0 + 6, y + 6, 12, 8, e.argb());
			int tx = listX0 + 24;
			int right = listX1 - 6;
			// Chips on the right: yours, players, war.
			int cx = right;
			if (e.id() == data.current()) {
				Component c = Component.translatable("screen.airdefense.country.chip_yours");
				cx -= font.width(c) + 8;
				Ui.chip(g, font, c, cx, y + 5, 0xFF1A1A1A, Ui.OK);
			} else if (!e.players().isEmpty()) {
				Component c = Component.translatable("screen.airdefense.country.chip_players", e.players().size());
				cx -= font.width(c) + 8;
				Ui.chip(g, font, c, cx, y + 5, 0xFF10161F, Ui.BLUE);
			} else if (!e.wars().isEmpty()) {
				Component c = Component.translatable("screen.airdefense.country.chip_war");
				cx -= font.width(c) + 8;
				Ui.chip(g, font, c, cx, y + 5, 0xFFFFFFFF, 0xFF8A3A33);
			}
			Ui.clipped(g, font, Component.literal(e.name()), tx, y + 6, cx - tx - 4, i == selected ? Ui.TEXT : Ui.mix(Ui.DIM, Ui.TEXT, hover ? 1 : 0.6f));
			Ui.clipped(g, font, Component.translatable("screen.airdefense.country.card_line", e.capital(), e.towns(), Ui.count(e.people())), tx, y + 18,
					right - tx, Ui.FAINT);
			y += CARD_H + CARD_GAP;
		}
		// A thin scroll bar when the list is longer than the column.
		if (cs.size() > rows) {
			int h = bodyY1 - bodyY0;
			int bh = Math.max(12, h * rows / cs.size());
			int by = bodyY0 + (h - bh) * scroll / Math.max(1, cs.size() - rows);
			g.fill(listX1 + 2, by, listX1 + 4, by + bh, 0x60FFFFFF);
		}
	}

	private float scale() {
		return ZOOMS[zoom];
	}

	private int mapCX() {
		return (mapX0 + mapX1) / 2;
	}

	private int mapCY() {
		return (bodyY0 + mapY1) / 2;
	}

	private int sx(double wx) {
		return (int) Math.round(mapCX() + (wx - centerX) * scale());
	}

	private int sy(double wz) {
		return (int) Math.round(mapCY() + (wz - centerZ) * scale());
	}

	private boolean inMap(double x, double y) {
		return x >= mapX0 && x < mapX1 && y >= bodyY0 && y < mapY1;
	}

	private void map(GuiGraphicsExtractor g, int mx, int my) {
		Ui.round(g, mapX0 - 1, bodyY0 - 1, mapX1 + 1, mapY1 + 1, 2, Ui.LINE);
		g.fill(mapX0, bodyY0, mapX1, mapY1, 0xFF14181C);
		g.enableScissor(mapX0, bodyY0, mapX1, mapY1);
		if (AtlasClient.loaded) {
			AtlasTiles.draw(g, scale(), centerX, centerZ, mapX0, bodyY0, mapX1, mapY1);
		} else {
			g.centeredText(font, Component.translatable("screen.airdefense.country.map_wait"), mapCX(), mapCY() - 4, Ui.DIM);
		}
		// Where the player stands.
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			int px = sx(mc.player.getX());
			int py = sy(mc.player.getZ());
			g.fill(px - 3, py - 3, px + 4, py + 4, 0xFF0C0C0E);
			g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
			g.text(font, Component.translatable("screen.airdefense.country.you"), px + 6, py - 4, 0xFFFFFFFF, true);
		}
		// The capitals: the picked one larger, ringed, with its name.
		List<CountryListPayload.Entry> cs = data.countries();
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < cs.size(); i++) {
				if (pass == 0 == (i == selected)) {
					continue;
				}
				CountryListPayload.Entry e = cs.get(i);
				int x = sx(e.capX());
				int y = sy(e.capZ());
				if (x < mapX0 - 40 || x > mapX1 + 40 || y < bodyY0 - 20 || y > mapY1 + 20) {
					continue;
				}
				boolean on = i == selected;
				int r = on ? 4 : 2;
				if (on) {
					g.fill(x - 7, y - 7, x + 8, y + 8, 0x50FFFFFF);
				}
				g.fill(x - r - 1, y - r - 1, x + r + 2, y + r + 2, 0xFF0C0C0E);
				g.fill(x - r, y - r, x + r + 1, y + r + 1, e.argb());
				if (on || scale() >= 0.07f) {
					Component name = Component.literal(e.name());
					int w = font.width(name);
					g.fill(x - w / 2 - 3, y + r + 3, x + w / 2 + 3, y + r + 14, on ? 0xE0101114 : 0x90101114);
					g.text(font, name, x - w / 2, y + r + 5, on ? Ui.TEXT : Ui.DIM, false);
				}
			}
		}
		g.disableScissor();
		// What the mouse does here.
		Component hint = Component.translatable("screen.airdefense.country.map_hint");
		int hw = font.width(hint);
		if (hw < mapX1 - mapX0 - 12) {
			g.fill(mapX0 + 4, mapY1 - 15, mapX0 + 10 + hw, mapY1 - 4, 0xA0101114);
			g.text(font, hint, mapX0 + 7, mapY1 - 13, Ui.FAINT, false);
		}
	}

	private void details(GuiGraphicsExtractor g) {
		int dy0 = mapY1 + 6;
		int dy1 = bodyY1;
		Ui.round(g, mapX0, dy0, mapX1, dy1, 2, Ui.CARD);
		CountryListPayload.Entry e = entry();
		if (e == null) {
			return;
		}
		int x = mapX0 + 8;
		int w = mapX1 - mapX0 - 16;
		int y = dy0 + 6;
		Ui.flag(g, x, y + 1, 16, 11, e.argb());
		Ui.big(g, font, Component.literal(e.name()), x + 22, y + 1, 1.25f, Ui.TEXT);
		y += 16;
		Component who;
		if (e.rulerTitle() >= 0) {
			who = Component.translatable("screen.airdefense.country.ruler",
					Component.translatable("ruler.airdefense.full", Component.translatable(Diplomacy.titleKey(e.rulerTitle(), e.rulerName())),
							Component.translatable("ruler.airdefense.name." + e.rulerName()), Diplomacy.roman(e.rulerNumber())),
					Component.translatable("ruler.airdefense.trait." + e.rulerTrait()));
		} else {
			who = Component.translatable("screen.airdefense.country.players", String.join(", ", e.players()));
		}
		Ui.clipped(g, font, who, x, y, w, e.rulerTitle() >= 0 ? Ui.DIM : Ui.BLUE);
		y += 11;
		Component style = Component.translatable("style.airdefense." + CityStyle.of(e.style()).name().toLowerCase(java.util.Locale.ROOT));
		Ui.clipped(g, font, Component.translatable("screen.airdefense.country.capital", e.capital(), style), x, y, w, Ui.DIM);
		y += 11;
		Ui.clipped(g, font, Component.translatable("screen.airdefense.country.facts", e.towns(), Ui.count(e.people())), x, y, w, Ui.DIM);
		y += 11;
		Component war = e.wars().isEmpty() ? Component.translatable("screen.airdefense.country.peace")
				: Component.translatable("screen.airdefense.country.wars", String.join(", ", e.wars()));
		if (e.allies() > 0) {
			war = Component.translatable("screen.airdefense.country.with_allies", war, e.allies());
		}
		Ui.clipped(g, font, war, x, y, w, e.wars().isEmpty() ? Ui.OK : Ui.BAD);
	}

	// ------------------------------------------------------------------------------------------------
	// The mouse

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double x = event.x();
		double y = event.y();
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && x >= listX0 && x < listX1 && y >= bodyY0 && y < bodyY1) {
			int i = scroll + (int) ((y - bodyY0) / (CARD_H + CARD_GAP));
			if (i < data.countries().size() && (y - bodyY0) % (CARD_H + CARD_GAP) < CARD_H) {
				select(i);
				if (doubleClick) {
					join();
				}
				return true;
			}
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inMap(x, y)) {
			pressedInMap = true;
			dragged = false;
			pressX = x;
			pressY = y;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (pressedInMap) {
			if (!dragged && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) {
				dragged = true;
			}
			if (dragged) {
				centerX -= dx / scale();
				centerZ -= dy / scale();
				goX = centerX;
				goZ = centerZ;
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedInMap && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			pressedInMap = false;
			if (!dragged) {
				pickAt(event.x(), event.y());
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	/** A click on the map: the capital under it, or the country whose land it is. */
	private void pickAt(double x, double y) {
		List<CountryListPayload.Entry> cs = data.countries();
		for (int i = 0; i < cs.size(); i++) {
			if (Math.abs(sx(cs.get(i).capX()) - x) <= 6 && Math.abs(sy(cs.get(i).capZ()) - y) <= 6) {
				select(i);
				return;
			}
		}
		double wx = centerX + (x - mapCX()) / scale();
		double wz = centerZ + (y - mapCY()) / scale();
		int country = AtlasClient.countryOfCity(AtlasClient.regionAt(wx, wz));
		for (int i = 0; i < cs.size(); i++) {
			if (cs.get(i).id() == country) {
				select(i);
				// Picking by its land does not move the map away from where he clicked.
				goX = centerX;
				goZ = centerZ;
				return;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY == 0) {
			return false;
		}
		if (inMap(x, y)) {
			double wx = centerX + (x - mapCX()) / scale();
			double wz = centerZ + (y - mapCY()) / scale();
			zoom = Mth.clamp(zoom + (scrollY > 0 ? 1 : -1), 0, ZOOMS.length - 1);
			centerX = wx - (x - mapCX()) / scale();
			centerZ = wz - (y - mapCY()) / scale();
			goX = centerX;
			goZ = centerZ;
			return true;
		}
		if (x >= listX0 && x < listX1 + 6 && y >= bodyY0 && y < bodyY1) {
			scroll = Mth.clamp(scroll + (scrollY > 0 ? -1 : 1), 0, Math.max(0, data.countries().size() - visibleRows()));
			return true;
		}
		return false;
	}

	/** The countries in the list now (for the tests). */
	public List<String> names() {
		List<String> out = new ArrayList<>();
		for (CountryListPayload.Entry e : data.countries()) {
			out.add(e.name());
		}
		return out;
	}
}
