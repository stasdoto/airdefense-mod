package com.stasdoto.airdefense.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;

import com.stasdoto.airdefense.client.nation.SeasonColors;

/** 1.42: the grass and the leaves take the season's colours (green spring, summer, the autumn's gold and red, winter). */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
	@Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
	private static void airdefense$grass(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(SeasonColors.grass(cir.getReturnValue(), pos));
	}

	@Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
	private static void airdefense$foliage(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(SeasonColors.foliage(cir.getReturnValue(), pos));
	}
}
