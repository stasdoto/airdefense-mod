package com.stasdoto.airdefense.client.nation;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.stasdoto.airdefense.nation.NationActionPayload;
import com.stasdoto.airdefense.nation.SettlementInfoPayload;

/**
 * A village's affairs (Shift + right click on one of its people): who owns it, how many live there, its guards,
 * how much it respects you; the charter that makes it yours; in creative, taking it at once; for your own villages,
 * calling up soldiers and sending them home.
 */
public class SettlementScreen extends Screen {
	private static final int C_TEXT = 0xFFE6E9EC;
	private static final int C_DIM = 0xFF9AA4AE;
	private static final int C_OK = 0xFF8AE07A;
	private static final int C_BAD = 0xFFFF7A6A;

	private SettlementInfoPayload info;
	private int x0;
	private int y0;
	private int w;
	private int h;
	private int refresh;
	private Button buy;
	private Button take;
	private Button callOne;
	private Button callAll;
	private Button recall;
	private Button dismiss;

	public SettlementScreen(SettlementInfoPayload info) {
		super(Component.literal(info.name()));
		this.info = info;
	}

	public int id() {
		return info.id();
	}

	public SettlementInfoPayload info() {
		return info;
	}

	public void update(SettlementInfoPayload p) {
		info = p;
		updateButtons();
	}

	@Override
	protected void init() {
		w = Math.min(width - 12, 320);
		h = Math.min(height - 12, 214);
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;
		int bw = (w - 18) / 2;
		int by = y0 + h - 50;
		buy = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.buy", info.price()),
				b -> send(NationActionPayload.BUY, 0)).bounds(x0 + 6, by, bw, 20).build());
		take = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.take"),
				b -> send(NationActionPayload.TAKE, 0)).bounds(x0 + 12 + bw, by, bw, 20).build());
		callOne = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.call_one"),
				b -> send(NationActionPayload.MOBILIZE, 1)).bounds(x0 + 6, by, bw, 20).build());
		callAll = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.call_all"),
				b -> send(NationActionPayload.MOBILIZE, 99)).bounds(x0 + 12 + bw, by, bw, 20).build());
		recall = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.recall"),
				b -> send(NationActionPayload.RECALL, 0)).bounds(x0 + 6, by + 24, bw, 20).build());
		dismiss = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.dismiss"),
				b -> send(NationActionPayload.DEMOBILIZE, 0)).bounds(x0 + 12 + bw, by + 24, bw, 20).build());
		updateButtons();
	}

	/** For the automated test: what a button press does. */
	public void act(int action, int a) {
		send(action, a);
	}

	private void send(int action, int a) {
		NationClient.send(action, info.id(), a, 0, 0, 0);
	}

	private void updateButtons() {
		if (buy == null) {
			return;
		}
		boolean mine = info.mine();
		buy.visible = !mine;
		buy.active = info.problem().isEmpty();
		buy.setMessage(Component.translatable("screen.airdefense.village.buy", info.price()));
		take.visible = !mine && info.creative();
		callOne.visible = mine;
		callAll.visible = mine;
		recall.visible = mine;
		dismiss.visible = mine;
		callOne.active = info.mobilizable() > 0;
		callAll.active = info.mobilizable() > 0;
		callAll.setMessage(Component.translatable("screen.airdefense.village.call_all", info.mobilizable()));
		recall.active = info.soldiers() > 0;
		dismiss.active = info.soldiers() > 0;
	}

	@Override
	public void tick() {
		if (++refresh % 20 == 0) {
			send(NationActionPayload.INFO, 0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xA0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF4A5560);
		g.fill(x0, y0, x0 + w, y0 + h, 0xF0182028);
		int x = x0 + 8;
		int y = y0 + 7;
		g.text(font, Component.literal(info.name()), x, y, 0xFFFFD24A);
		y += 13;
		if (info.country().isEmpty()) {
			g.text(font, Component.translatable("screen.airdefense.village.independent"), x, y, C_DIM);
		} else {
			g.fill(x, y, x + 9, y + 8, info.color());
			Component c = Component.literal(info.country());
			if (info.cityState()) {
				c = Component.translatable("screen.airdefense.village.city_state", info.country());
			}
			g.text(font, c, x + 13, y, info.mine() ? C_OK : C_TEXT);
		}
		y += 14;
		if (!info.elder().isEmpty()) {
			g.text(font, Component.translatable("screen.airdefense.village.elder", info.elder()), x, y, C_TEXT);
			y += 11;
		}
		g.text(font, Component.translatable("screen.airdefense.village.people", info.population(), info.guards()), x, y, C_TEXT);
		y += 11;
		if (info.mine()) {
			g.text(font, Component.translatable("screen.airdefense.village.army", info.soldiers(), info.mobilizable()), x, y, C_TEXT);
			y += 11;
		}
		// Respect: a bar from -100 to +100 with the threshold for the charter.
		int rep = info.reputation();
		g.text(font, Component.translatable("screen.airdefense.village.respect", rep), x, y, rep >= 25 ? C_OK : rep < 0 ? C_BAD : C_TEXT);
		y += 11;
		int bx = x;
		int bw = w - 16;
		g.fill(bx, y, bx + bw, y + 4, 0xFF303A44);
		int mid = bx + bw / 2;
		int pos = mid + Math.max(-bw / 2, Math.min(bw / 2, rep * bw / 200));
		g.fill(Math.min(mid, pos), y, Math.max(mid, pos), y + 4, rep >= 0 ? C_OK : C_BAD);
		int need = mid + 25 * bw / 200;
		g.fill(need, y - 2, need + 1, y + 6, 0xFFFFD24A);
		y += 10;
		if (!info.mine()) {
			if (!info.problem().isEmpty()) {
				g.textWithWordWrap(font, Component.literal(info.problem()), x, y, w - 16, C_BAD);
			} else {
				g.textWithWordWrap(font, Component.translatable("screen.airdefense.village.can_buy"), x, y, w - 16, C_OK);
			}
			y += 20;
			small(g, Component.translatable("screen.airdefense.village.how").getString(), x, Math.min(y, y0 + h - 64), C_DIM);
		} else {
			small(g, Component.translatable("screen.airdefense.village.orders").getString(), x, Math.min(y + 2, y0 + h - 62), C_DIM);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.textWithWordWrap(font, Component.literal(text), 0, 0, (int) ((w - 16) / 0.75f), color);
		g.pose().popMatrix();
	}
}
