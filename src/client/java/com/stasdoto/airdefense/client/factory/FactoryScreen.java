package com.stasdoto.airdefense.client.factory;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.stasdoto.airdefense.factory.FactoryActionPayload;
import com.stasdoto.airdefense.factory.FactoryBlockEntity;
import com.stasdoto.airdefense.factory.FactoryStatusPayload;
import com.stasdoto.airdefense.factory.Product;

/** The factory's production menu: what can be made and what it costs, the queue, the stock. */
public class FactoryScreen extends Screen {
	private static final int ROW_H = 20;
	private static final int C_TEXT = 0xFFE6E9EC;
	private static final int C_DIM = 0xFF9AA4AE;
	private static final int C_OK = 0xFF8AE07A;
	private static final int C_BAD = 0xFFFF7A6A;

	private FactoryStatusPayload status;
	private int selected;
	private int scroll;
	private int refresh;
	private int x0;
	private int y0;
	private int w;
	private int h;
	private Button order1;
	private Button order5;
	private Button cancel;
	private Button take;

	public FactoryScreen(FactoryStatusPayload status) {
		super(Component.translatable("screen.airdefense.factory.title"));
		this.status = status;
	}

	public void update(FactoryStatusPayload s) {
		if (s.pos().equals(status.pos())) {
			status = s;
		}
	}

	public FactoryStatusPayload status() {
		return status;
	}

	@Override
	protected void init() {
		w = Math.min(width - 12, 400);
		h = Math.min(height - 12, 232);
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;
		int rx = x0 + 168;
		int rw = w - 176;
		order1 = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.factory.order1"), b -> send(FactoryActionPayload.ORDER, 1))
				.bounds(rx, y0 + 112, rw / 2 - 2, 18).build());
		order5 = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.factory.order5"), b -> send(FactoryActionPayload.ORDER, 5))
				.bounds(rx + rw / 2 + 2, y0 + 112, rw - rw / 2 - 2, 18).build());
		cancel = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.factory.cancel"), b -> send(FactoryActionPayload.CANCEL, 0))
				.bounds(rx, y0 + 160, rw, 16).build());
		take = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.factory.take"), b -> send(FactoryActionPayload.TAKE, 0))
				.bounds(rx, y0 + h - 20, rw, 18).build());
	}

	private void send(int action, int count) {
		if (ClientPlayNetworking.canSend(FactoryActionPayload.TYPE)) {
			ClientPlayNetworking.send(new FactoryActionPayload(status.pos(), action, selected, count));
		}
	}

	/** For the automated test: pick a product. */
	public void select(Product p) {
		selected = p.ordinal();
		scroll = Math.max(0, Math.min(selected - visibleRows() / 2, Product.values().length - visibleRows()));
	}

	private int visibleRows() {
		return Math.max(1, (h - 22 - 14) / ROW_H);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= x0 && mouseX < x0 + 166) {
			int max = Math.max(0, Product.values().length - visibleRows());
			scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (++refresh % 10 == 0) {
			send(FactoryActionPayload.REFRESH, 0);
		}
		cancel.active = !status.queue().isEmpty();
		int total = 0;
		for (int s : status.stock()) {
			total += s;
		}
		take.active = total > 0;
		order1.active = status.queue().size() < FactoryBlockEntity.MAX_QUEUE;
		order5.active = order1.active;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= x0 + 6 && event.x() < x0 + 162) {
			int row = (int) ((event.y() - (y0 + 22)) / ROW_H);
			if (row >= 0 && row < visibleRows() && row + scroll < Product.values().length && event.y() >= y0 + 22) {
				row += scroll;
				selected = row;
				return true;
			}
		}
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
		g.text(font, title, x0 + 6, y0 + 6, 0xFFFFD24A);
		Component state = status.buildPercent() < 100 ? Component.translatable("screen.airdefense.factory.building", status.buildPercent())
				: status.queue().isEmpty() ? Component.translatable("screen.airdefense.factory.idle")
				: Component.translatable("screen.airdefense.factory.working");
		if (status.creative()) {
			state = Component.empty().append(state).append(Component.translatable("screen.airdefense.factory.creative"));
		}
		g.text(font, state, x0 + w - 6 - font.width(state), y0 + 6, status.queue().isEmpty() ? C_DIM : C_OK);
		Player player = minecraft.player;
		Product[] products = Product.values();
		// Product list (scrolls with the mouse wheel).
		int rows = visibleRows();
		for (int i = scroll; i < products.length && i < scroll + rows; i++) {
			Product p = products[i];
			int y = y0 + 22 + (i - scroll) * ROW_H;
			boolean sel = i == selected;
			boolean hover = mouseX >= x0 + 6 && mouseX < x0 + 162 && mouseY >= y && mouseY < y + ROW_H;
			if (sel || hover) {
				g.fill(x0 + 4, y, x0 + 162, y + ROW_H - 1, sel ? 0xFF2E3D4C : 0xFF222C36);
			}
			g.item(new ItemStack(p.item()), x0 + 6, y + 2);
			g.text(font, new ItemStack(p.item()).getHoverName(), x0 + 26, y + 2, sel ? 0xFFFFFFFF : C_TEXT);
			int inStock = i < status.stock().size() ? status.stock().get(i) : 0;
			small(g, Component.translatable("screen.airdefense.factory.row", p.time(status.creative()) / 20, inStock).getString(), x0 + 26, y + 11, C_DIM);
		}
		if (products.length > rows) {
			int trackH = rows * ROW_H;
			int barH = Math.max(10, trackH * rows / products.length);
			int barY = y0 + 22 + (trackH - barH) * scroll / Math.max(1, products.length - rows);
			g.fill(x0 + 163, y0 + 22, x0 + 165, y0 + 22 + trackH, 0xFF303A44);
			g.fill(x0 + 163, barY, x0 + 165, barY + barH, 0xFF8A96A2);
		}
		// Selected product: cost.
		Product p = products[Math.max(0, Math.min(selected, products.length - 1))];
		int rx = x0 + 168;
		g.item(new ItemStack(p.item()), rx, y0 + 22);
		g.text(font, new ItemStack(p.item()).getHoverName(), rx + 20, y0 + 26, 0xFFFFFFFF);
		boolean free = status.creative() || player != null && player.getAbilities().instabuild;
		int cy = y0 + 44;
		if (free) {
			g.text(font, Component.translatable("screen.airdefense.factory.free"), rx, cy, C_OK);
		} else {
			for (Product.Cost c : p.cost) {
				int have = player == null ? 0 : FactoryBlockEntity.count(player, c);
				g.item(new ItemStack(c.item()), rx, cy - 4);
				g.text(font, have + " / " + c.count() + "  " + new ItemStack(c.item()).getHoverName().getString(), rx + 20, cy, have >= c.count() ? C_OK : C_BAD);
				cy += 16;
			}
		}
		small(g, (p.batch > 1 ? Component.translatable("screen.airdefense.factory.time_batch", p.time(status.creative()) / 20, p.batch)
				: Component.translatable("screen.airdefense.factory.time", p.time(status.creative()) / 20, p.units)).getString(), rx, y0 + 102, C_DIM);
		// Queue.
		g.text(font, Component.translatable("screen.airdefense.factory.queue", status.queue().size(), FactoryBlockEntity.MAX_QUEUE), rx, y0 + 136, C_TEXT);
		List<Integer> q = status.queue();
		for (int i = 0; i < q.size() && i < 12; i++) {
			g.item(new ItemStack(Product.byId(q.get(i)).item()), rx + i * 17, y0 + 146 - 2);
		}
		if (!q.isEmpty()) {
			Product cur = Product.byId(q.getFirst());
			int bw = 16;
			int done = Math.min(bw, status.progress() * bw / Math.max(1, cur.time(status.creative())));
			g.fill(rx, y0 + 161 - 4, rx + bw, y0 + 161 - 2, 0xFF303A44);
			g.fill(rx, y0 + 161 - 4, rx + done, y0 + 161 - 2, C_OK);
		}
		// Stock.
		g.text(font, Component.translatable("screen.airdefense.factory.stock"), rx, y0 + 182, C_TEXT);
		int sx = rx;
		for (int i = 0; i < products.length && i < status.stock().size(); i++) {
			int n = status.stock().get(i);
			if (n <= 0) {
				continue;
			}
			ItemStack s = new ItemStack(products[i].item(), Math.min(n, 99));
			g.item(s, sx, y0 + 190);
			g.itemDecorations(font, s, sx, y0 + 190);
			sx += 18;
		}
		small(g, Component.translatable("screen.airdefense.factory.supply", FactoryBlockEntity.SUPPLY_RANGE).getString(), x0 + 6, y0 + h - 10, C_DIM);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}
}
