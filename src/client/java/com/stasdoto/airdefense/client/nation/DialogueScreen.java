package com.stasdoto.airdefense.client.nation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.stasdoto.airdefense.client.ui.UiButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.stasdoto.airdefense.nation.Dialogue;
import com.stasdoto.airdefense.nation.DialogueActionPayload;
import com.stasdoto.airdefense.nation.DialoguePayload;

/**
 * 1.29: a talk with a person: his name, what he is and where from, what he just said, and the things you can say
 * (as buttons) - about the town, how he is, trading, hiring him, orders for your own soldiers, goodbye.
 */
public class DialogueScreen extends Screen {
	private static final int C_NAME = 0xFFFFD24A;
	private static final int C_DIM = com.stasdoto.airdefense.client.ui.Ui.DIM;
	private static final int C_TEXT = com.stasdoto.airdefense.client.ui.Ui.TEXT;

	private DialoguePayload talk;
	private int x0;
	private int y0;
	private int w;
	private int h;

	public DialogueScreen(DialoguePayload talk) {
		super(Component.literal(talk.name()));
		this.talk = talk;
	}

	public int entity() {
		return talk.entity();
	}

	public DialoguePayload talk() {
		return talk;
	}

	public void update(DialoguePayload p) {
		talk = p;
		rebuildWidgets();
	}

	@Override
	protected void init() {
		w = Math.min(width - 12, 320);
		int rows = (talk.options().size() + 1) / 2;
		h = 86 + rows * 22;
		x0 = (width - w) / 2;
		y0 = height - h - 28;
		int bw = (w - 18) / 2;
		int k = 0;
		for (int option : talk.options()) {
			int col = k % 2;
			int row = k / 2;
			addRenderableWidget(UiButton.builder(Component.translatable("dialogue.airdefense.option." + option), b -> choose(option))
					.bounds(x0 + 6 + col * (bw + 6), y0 + 78 + row * 22, bw, 20).build());
			k++;
		}
	}

	private void choose(int option) {
		if (option == Dialogue.BYE) {
			onClose();
			return;
		}
		if (ClientPlayNetworking.canSend(DialogueActionPayload.TYPE)) {
			ClientPlayNetworking.send(new DialogueActionPayload(talk.entity(), option));
		}
		if (option == Dialogue.TRADE) {
			onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0x70000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		com.stasdoto.airdefense.client.ui.Ui.panel(g, x0, y0, x0 + w, y0 + h);
		int x = x0 + 8;
		com.stasdoto.airdefense.client.ui.Ui.big(g, font, Component.literal(talk.name()), x, y0 + 6, 1.2f, com.stasdoto.airdefense.client.ui.Ui.TEXT);
		g.text(font, talk.title(), x, y0 + 19, C_DIM);
		com.stasdoto.airdefense.client.ui.Ui.divider(g, x, x0 + w - 8, y0 + 30);
		g.textWithWordWrap(font, Component.literal("«").append(talk.speech()).append("»"), x, y0 + 36, w - 16, C_TEXT);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}
}
