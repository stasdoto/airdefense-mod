package com.stasdoto.airdefense.client.ui;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.stasdoto.airdefense.client.ClientSettings;

/**
 * 2.0: the mod's own settings (opened from the pause menu, with /airdefense settings, or its key): how busy the towns'
 * streets are (cars, people), how detailed the vehicles are, their lights, the smoke of damaged ones, the chase camera.
 * Each change is saved at once to config/airdefense-client.json.
 */
public class SettingsScreen extends Screen {
	private static final int W = 300;
	private static final int ROW = 24;
	@Nullable
	private final Screen parent;
	private int x0;
	private int y0;
	private int h;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.airdefense.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(W, width - 16);
		h = 40 + ROW * 7 + 30;
		x0 = (width - w) / 2;
		y0 = Math.max(6, (height - h) / 2);
		int y = y0 + 34;
		int right = x0 + w - 10;
		// The two densities: - and + either side of the value (in steps of 10 %).
		stepper(right, y, () -> ClientSettings.trafficDensity(), ClientSettings::setTrafficDensity);
		y += ROW;
		stepper(right, y, () -> ClientSettings.pedestrianDensity(), ClientSettings::setPedestrianDensity);
		y += ROW;
		addRenderableWidget(UiButton.create(detailLabel(), b -> {
			ClientSettings.setDetail(ClientSettings.highDetail() ? ClientSettings.Detail.LOW : ClientSettings.Detail.HIGH);
			b.setMessage(detailLabel());
		}).bounds(right - 96, y, 96, 20).build());
		y += ROW;
		toggle(x0 + 10, y, w - 20, "lights", ClientSettings.vehicleLights(), ClientSettings::setVehicleLights);
		y += ROW;
		toggle(x0 + 10, y, w - 20, "smoke", ClientSettings.damageSmoke(), ClientSettings::setDamageSmoke);
		y += ROW;
		toggle(x0 + 10, y, w - 20, "chase", ClientSettings.chaseCamera(), ClientSettings::setChaseCamera);
		y += ROW + 8;
		int bw = (w - 30) / 2;
		addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.settings.reset"), b -> {
			ClientSettings.reset();
			rebuildWidgets();
		}).bounds(x0 + 10, y, bw, 20).style(UiButton.Style.GHOST).build());
		addRenderableWidget(UiButton.create(Component.translatable("gui.done"), b -> onClose())
				.bounds(x0 + 20 + bw, y, bw, 20).style(UiButton.Style.PRIMARY).build());
	}

	private interface Getter {
		float get();
	}

	private interface Setter {
		void set(float v);
	}

	private void stepper(int right, int y, Getter get, Setter set) {
		addRenderableWidget(UiButton.create(Component.literal("+"), b -> set.set(get.get() + 0.1f)).bounds(right - 20, y, 20, 20).build());
		addRenderableWidget(UiButton.create(Component.literal("−"), b -> set.set(get.get() - 0.1f)).bounds(right - 96, y, 20, 20).build());
	}

	private void toggle(int x, int y, int w, String key, boolean on, java.util.function.Consumer<Boolean> set) {
		UiButton t = UiButton.create(Component.translatable("screen.airdefense.settings." + key), b -> set.accept(b.on))
				.bounds(x, y, w, 20).style(UiButton.Style.TOGGLE).build();
		t.on = on;
		addRenderableWidget(t);
	}

	private static Component detailLabel() {
		return Component.translatable(ClientSettings.highDetail() ? "screen.airdefense.settings.detail_high" : "screen.airdefense.settings.detail_low");
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, Ui.SCRIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		int w = Math.min(W, width - 16);
		Ui.panel(g, x0, y0, x0 + w, y0 + h);
		Ui.big(g, font, title, x0 + 10, y0 + 8, 1.25f, Ui.TEXT);
		Ui.divider(g, x0 + 10, x0 + w - 10, y0 + 26);
		int y = y0 + 34;
		int right = x0 + w - 10;
		row(g, Component.translatable("screen.airdefense.settings.traffic"), x0 + 10, y);
		value(g, ClientSettings.trafficDensity(), right, y);
		y += ROW;
		row(g, Component.translatable("screen.airdefense.settings.people"), x0 + 10, y);
		value(g, ClientSettings.pedestrianDensity(), right, y);
		y += ROW;
		row(g, Component.translatable("screen.airdefense.settings.detail"), x0 + 10, y);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void row(GuiGraphicsExtractor g, Component label, int x, int y) {
		g.text(font, label, x, y + 6, Ui.DIM, false);
	}

	/** The percentage between the - and + buttons, and a bar under it. */
	private void value(GuiGraphicsExtractor g, float v, int right, int y) {
		String s = Math.round(v * 100) + "%";
		int cx = right - 48;
		g.text(font, Component.literal(s), cx - font.width(s) / 2, y + 3, v == 0 ? Ui.FAINT : Ui.TEXT, false);
		Ui.bar(g, right - 72, y + 14, right - 24, v / 2f, v > 1.4f ? Ui.GOLD : Ui.ACCENT);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
