package com.stasdoto.airdefense.client.nation;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.nation.CountryActionPayload;
import com.stasdoto.airdefense.nation.CountryListPayload;

/** 1.43: the country screen - opened by the server when the player first comes in, and by its key (K) later. */
public final class CountryClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(AirDefense.id("country"));
	public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.airdefense.country", InputConstants.KEY_K, CATEGORY));
	/** A list that came while another screen was open: shown once it closes. */
	private static CountryListPayload waiting;
	/** For the tests: the last list that came. */
	public static volatile CountryListPayload last;

	private CountryClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CountryListPayload.TYPE, (payload, context) -> {
			last = payload;
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof CountryScreen s) {
				s.update(payload);
			} else if (mc.gui.screen() == null) {
				mc.gui.setScreen(new CountryScreen(payload));
			} else {
				waiting = payload;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				waiting = null;
				return;
			}
			while (KEY.consumeClick()) {
				if (mc.gui.screen() == null) {
					ClientPlayNetworking.send(new CountryActionPayload(CountryActionPayload.LIST, -1, false));
				}
			}
			if (waiting != null && mc.gui.screen() == null) {
				CountryListPayload p = waiting;
				waiting = null;
				mc.gui.setScreen(new CountryScreen(p));
			}
		});
	}
}
