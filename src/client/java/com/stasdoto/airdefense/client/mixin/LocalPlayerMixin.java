package com.stasdoto.airdefense.client.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

import com.stasdoto.airdefense.client.vehicle.ThermalView;

/** 1.26: the thermal sight's picture is one of the player's screen effects while the thermal channel is on. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "getActivePostEffects", at = @At("RETURN"), cancellable = true)
	private void airdefense$thermal(CallbackInfoReturnable<List<Identifier>> cir) {
		if (ThermalView.wanted()) {
			List<Identifier> out = new ArrayList<>(cir.getReturnValue());
			out.add(ThermalView.THERMAL);
			cir.setReturnValue(out);
		}
	}
}
