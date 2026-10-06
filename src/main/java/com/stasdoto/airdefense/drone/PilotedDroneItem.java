package com.stasdoto.airdefense.drone;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;

import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * A drone you fly yourself, through its camera (stage R9): the FPV quadcopter with an RPG grenade (right-click to send
 * it up, it goes where you look, blows up on what it flies into), and the Magura sea drone (right-click on water).
 */
public class PilotedDroneItem extends Item {
	private final MissileType type;

	public PilotedDroneItem(Properties props, MissileType type) {
		super(props);
		this.type = type;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (DroneCam.watching(sp)) {
			return InteractionResult.FAIL;
		}
		Vec3 look = player.getLookAngle();
		MissileEntity m;
		if (type == MissileType.MAGURA) {
			BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
			if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)
					|| hit.getLocation().distanceTo(player.getEyePosition()) > 8) {
				sp.sendOverlayMessage(Component.translatable("message.airdefense.magura.water"));
				return InteractionResult.FAIL;
			}
			Vec3 at = new Vec3(hit.getLocation().x, hit.getBlockPos().getY() + 1.05, hit.getLocation().z);
			m = MissileEntity.launchPiloted(server, type, at, new Vec3(look.x, 0, look.z), sp);
		} else {
			m = MissileEntity.launchPiloted(server, type, player.getEyePosition().add(look.scale(1.2)), look.add(0, 0.25, 0), sp);
		}
		level.playSound(null, player.blockPosition(), ModSounds.DRONE_BUZZ, SoundSource.PLAYERS, 1.0f, type == MissileType.FPV ? 1.6f : 0.7f);
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		DroneCam.start(sp, m);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, java.util.function.Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.airdefense." + (type == MissileType.FPV ? "fpv_drone" : "magura_drone") + ".hint")
				.withStyle(ChatFormatting.GRAY));
	}
}
