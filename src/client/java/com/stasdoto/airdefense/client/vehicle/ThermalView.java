package com.stasdoto.airdefense.client.vehicle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.26: the thermal channel's picture - our white-hot shader (assets/airdefense/post_effect/thermal.json) added to the
 * player's screen effects (see LocalPlayerMixin) while the sight's thermal channel is on.
 */
public final class ThermalView {
	public static final Identifier THERMAL = AirDefense.id("thermal");
	/** For the automated test: ticks the thermal picture was on. */
	public static int ticksOn;

	private ThermalView() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ThermalView::tick);
	}

	public static boolean wanted() {
		return GunnerSight.thermalOn() || com.stasdoto.airdefense.client.gear.MonocularView.active();
	}

	private static void tick(Minecraft mc) {
		if (mc.level != null && wanted()) {
			ticksOn++;
		}
	}

	/** Did the game fail to build the effect (a shader error)? */
	public static boolean failed(Minecraft mc) {
		return mc.gameRenderer.getAppliedPostEffects().isEmpty() && wanted();
	}
}
