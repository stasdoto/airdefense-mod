package com.stasdoto.airdefense.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.nation.Seasons;

/** 1.42: winter's snow thaws once it is over (where the land is not snowy of itself), a layer at a time. */
@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void airdefense$thaw(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (!Seasons.here(level) || !Seasons.thawing(level.getBiome(pos).value().getBaseTemperature()) || random.nextInt(3) != 0) {
			return;
		}
		int layers = state.getValue(SnowLayerBlock.LAYERS);
		if (layers > 1) {
			level.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1), 2);
		} else {
			level.removeBlock(pos, false);
		}
		ci.cancel();
	}
}
