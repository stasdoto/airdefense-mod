package com.stasdoto.airdefense.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

import com.stasdoto.airdefense.client.vehicle.GunnerSight;

/** 1.26: through the thermal sight people and animals glow white-hot (whatever the light where they stand). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
	private static void airdefense$hot(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
		if (GunnerSight.thermalOn()) {
			cir.setReturnValue(OverlayTexture.pack(1.0f, false));
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void airdefense$bright(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		if (GunnerSight.thermalOn()) {
			state.lightCoords = 0xF000F0;
		}
	}
}
