package com.stasdoto.airdefense.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import com.stasdoto.airdefense.client.weapon.GunClient;

/** Slower mouse while zoomed in, so a scope does not make the aim jumpy. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double airdefense$zoomYaw(double value) {
		return (Object) this instanceof LocalPlayer ? value * GunClient.sensitivity() * com.stasdoto.airdefense.client.vehicle.GunnerSight.sensitivity() : value;
	}

	@ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double airdefense$zoomPitch(double value) {
		return (Object) this instanceof LocalPlayer ? value * GunClient.sensitivity() * com.stasdoto.airdefense.client.vehicle.GunnerSight.sensitivity() : value;
	}
}
