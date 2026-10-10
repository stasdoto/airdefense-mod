package com.stasdoto.airdefense.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

import com.stasdoto.airdefense.nation.Seasons;

/** 1.42: in winter the temperate lands are cold - it snows there instead of raining, snow lies, water freezes. */
@Mixin(Biome.class)
public abstract class BiomeMixin {
	@Inject(method = "coldEnoughToSnow", at = @At("HEAD"), cancellable = true)
	private void airdefense$winterSnow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Boolean> cir) {
		if (Seasons.winterAt(((Biome) (Object) this).getBaseTemperature())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "warmEnoughToRain", at = @At("HEAD"), cancellable = true)
	private void airdefense$winterNoRain(BlockPos pos, int seaLevel, CallbackInfoReturnable<Boolean> cir) {
		if (Seasons.winterAt(((Biome) (Object) this).getBaseTemperature())) {
			cir.setReturnValue(false);
		}
	}
}
