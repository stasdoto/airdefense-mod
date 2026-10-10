package com.stasdoto.airdefense.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * 1.43: a button in the new look - flat, soft-cornered, lighting up under the mouse. PRIMARY is the one thing the
 * screen is for (the accent colour), SECONDARY the other choices, GHOST the quiet ones (cancel, later), DANGER the
 * ones that cannot be taken back. A TOGGLE shows a switch and stays on or off.
 */
public class UiButton extends AbstractButton {
	public enum Style {
		PRIMARY, SECONDARY, GHOST, DANGER, TOGGLE, TAB
	}

	/** What a button does when pressed (it is given the button: to change its own label, say). */
	@FunctionalInterface
	public interface OnPress {
		void onPress(UiButton button);
	}

	/** Like the game's own Button.builder: label and action, then bounds (and a style), then build. */
	public static Builder builder(Component label, OnPress action) {
		return new Builder(label, action);
	}

	public static final class Builder {
		private final Component label;
		private final OnPress action;
		private int x;
		private int y;
		private int w = 150;
		private int h = 20;
		private Style style = Style.SECONDARY;

		private Builder(Component label, OnPress action) {
			this.label = label;
			this.action = action;
		}

		public Builder bounds(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			return this;
		}

		public Builder style(Style s) {
			style = s;
			return this;
		}

		public UiButton build() {
			UiButton[] self = new UiButton[1];
			self[0] = new UiButton(x, y, w, h, label, style, () -> action.onPress(self[0]));
			return self[0];
		}
	}

	private final Runnable action;
	private Style style;
	private float hover;
	/** For TOGGLE: on or off. */
	public boolean on;

	public UiButton(int x, int y, int w, int h, Component label, Style style, Runnable action) {
		super(x, y, w, h, label);
		this.style = style;
		this.action = action;
	}

	public void setStyle(Style s) {
		style = s;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (style == Style.TOGGLE) {
			on = !on;
		}
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		boolean lit = active && isHoveredOrFocused();
		hover += ((lit ? 1f : 0f) - hover) * 0.35f;
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + width;
		int y1 = y0 + height;
		int fg;
		switch (style) {
			case PRIMARY -> {
				int bg = active ? Ui.mix(Ui.ACCENT, Ui.ACCENT_HI, hover) : 0xFF4A3C36;
				Ui.round(g, x0, y0 + 1, x1, y1 + 1, 2, active ? Ui.ACCENT_LO : 0xFF2A2522);
				Ui.round(g, x0, y0, x1, y1, 2, bg);
				g.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, 0x30FFFFFF);
				fg = active ? 0xFFFFFFFF : 0xFF9A8F88;
			}
			case DANGER -> {
				int bg = Ui.mix(0xFF5A2A26, 0xFF7A3330, hover);
				Ui.round(g, x0, y0, x1, y1, 2, active ? bg : 0xFF3A2826);
				fg = active ? 0xFFFFD9D4 : Ui.FAINT;
			}
			case GHOST -> {
				if (hover > 0.02f) {
					Ui.round(g, x0, y0, x1, y1, 2, Ui.mix(0x00FFFFFF, 0x18FFFFFF, hover));
				}
				fg = active ? Ui.mix(Ui.DIM, Ui.TEXT, hover) : Ui.FAINT;
			}
			case TAB -> {
				// A tab: the chosen one (inactive: it is open already) underlined in the accent colour.
				boolean chosen = !active;
				if (!chosen && hover > 0.02f) {
					Ui.round(g, x0, y0, x1, y1, 2, Ui.mix(0x00FFFFFF, 0x14FFFFFF, hover));
				}
				if (chosen) {
					g.fill(x0 + 2, y1 - 2, x1 - 2, y1, Ui.ACCENT);
				}
				fg = chosen ? Ui.TEXT : Ui.mix(Ui.DIM, Ui.TEXT, hover);
			}
			case TOGGLE -> {
				// A switch, then the label.
				int sw = 18;
				int sy = y0 + (height - 10) / 2;
				Ui.round(g, x0, sy, x0 + sw, sy + 10, 3, on ? Ui.ACCENT : 0xFF45474E);
				int kx = on ? x0 + sw - 9 : x0 + 1;
				Ui.round(g, kx, sy + 1, kx + 8, sy + 9, 3, 0xFFF4F2EE);
				int c = active ? Ui.mix(Ui.DIM, Ui.TEXT, Math.max(hover, on ? 1 : 0)) : Ui.FAINT;
				Ui.clipped(g, font, getMessage(), x0 + sw + 6, y0 + (height - 8) / 2, width - sw - 6, c);
				return;
			}
			default -> {
				int bg = Ui.mix(0xFF2C2D33, 0xFF3A3B42, hover);
				Ui.round(g, x0, y0, x1, y1, 2, Ui.LINE);
				Ui.round(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 2, active ? bg : 0xFF232428);
				fg = active ? Ui.TEXT : Ui.FAINT;
			}
		}
		Component label = getMessage();
		int tw = font.width(label);
		if (tw > width - 8) {
			Ui.clipped(g, font, label, x0 + 4, y0 + (height - 8) / 2, width - 8, fg);
		} else {
			g.text(font, label, x0 + (width - tw) / 2, y0 + (height - 8) / 2, fg, false);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		defaultButtonNarrationText(out);
	}
}
