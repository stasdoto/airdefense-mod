package com.stasdoto.airdefense.client.gear;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;

/** 1.27: the gear drawn on people and the hand-held thermal imager's view. */
public final class GearClient {
	private GearClient() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void init() {
		// Every renderer of a human-shaped body (players, soldiers, workers, zombies, skeletons, armour stands) gets the gear.
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(new GearLayer((RenderLayerParent) renderer));
			}
		});
		MonocularView.init();
	}
}
