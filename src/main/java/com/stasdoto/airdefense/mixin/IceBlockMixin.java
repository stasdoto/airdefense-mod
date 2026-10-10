package com.stasdoto.airdefense.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.nation.Seasons;

/** 1.42: the ice on the lakes and rivers breaks up after the winter (where the land is not icy of itself). */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {
	@Shadow
	protected abstract void melt(BlockState state, Level level, BlockPos pos);

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void airdefense$thaw(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (Seasons.here(level) && Seasons.thawing(level.getBiome(pos).value().getBaseTemperature()) && random.nextInt(4) == 0) {
			melt(state, level, pos);
			ci.cancel();
		}
	}
}
