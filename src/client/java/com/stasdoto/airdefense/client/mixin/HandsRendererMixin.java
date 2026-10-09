package com.stasdoto.airdefense.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;

import com.stasdoto.airdefense.vehicle.VehicleEntity;

/** 1.26: no bare arm in front of the gun sight or across a cockpit - in a vehicle's seat your hands are on its controls. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandsRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void airdefense$noHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
			FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && (mc.player.getVehicle() instanceof VehicleEntity || com.stasdoto.airdefense.client.gear.MonocularView.active())) {
			ci.cancel();
		}
	}
}
