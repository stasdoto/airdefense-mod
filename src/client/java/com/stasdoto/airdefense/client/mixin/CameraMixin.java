package com.stasdoto.airdefense.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.client.vehicle.GunnerSight;

/** 1.26: the camera moves into the gunner's sight (or the driver's periscope) - the view from inside the tank. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 pos);

	// Right after the camera is put at the player's eyes - before the field of view and the culling frustum are worked out.
	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void airdefense$sight(DeltaTracker delta, CallbackInfo ci) {
		Vec3 p = GunnerSight.cameraPos(delta.getGameTimeDeltaPartialTick(true));
		if (p != null) {
			setPosition(p);
		}
	}
}
