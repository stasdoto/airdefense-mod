package com.stasdoto.airdefense.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.AbstractClientPlayer;

import com.stasdoto.airdefense.client.weapon.GunClient;

/** Zoom while aiming a gun (4x through the sniper scope), and in a vehicle's gun sight (1.26). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void airdefense$aimZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		float zoom = GunClient.fovMultiplier() * com.stasdoto.airdefense.client.vehicle.GunnerSight.fovMultiplier()
				* com.stasdoto.airdefense.client.gear.MonocularView.fovMultiplier();
		if (zoom < 0.999f && (Object) this == net.minecraft.client.Minecraft.getInstance().player) {
			cir.setReturnValue(cir.getReturnValueF() * zoom);
		}
	}
}
