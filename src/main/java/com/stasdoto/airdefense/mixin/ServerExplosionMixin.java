package com.stasdoto.airdefense.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;

import com.stasdoto.airdefense.nation.Repairs;

/** Every explosion that breaks blocks is noted, so a town it hit can rebuild what it broke (1.25.2). */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Inject(method = "explode", at = @At("RETURN"))
	private void airdefense$noteDamage(CallbackInfoReturnable<Integer> cir) {
		ServerExplosion self = (ServerExplosion) (Object) this;
		Explosion.BlockInteraction i = self.getBlockInteraction();
		if (i == Explosion.BlockInteraction.DESTROY || i == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
			Repairs.exploded(self.level(), self.center(), self.radius());
		}
	}
}
