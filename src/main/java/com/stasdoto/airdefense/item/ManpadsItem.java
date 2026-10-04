package com.stasdoto.airdefense.item;

import java.util.Comparator;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.Effects;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModSounds;

/** Shoulder-fired SAM (Stinger-style): aim at a drone or missile and right-click; the seeker locks the closest one in the sight. */
public class ManpadsItem extends Item {
	private static final double RANGE = 110;
	private static final double CONE_COS = Math.cos(Math.toRadians(10));
	private static final int COOLDOWN = 50;

	public ManpadsItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB box = new AABB(eye.x - RANGE, eye.y - RANGE, eye.z - RANGE, eye.x + RANGE, eye.y + RANGE, eye.z + RANGE);
		MissileEntity target = serverLevel.getEntitiesOfClass(MissileEntity.class, box, m -> {
					if (!m.getMissileType().threat || !m.isAlive()) {
						return false;
					}
					Vec3 to = m.position().subtract(eye);
					double d = to.length();
					return d < RANGE && d > 3 && to.scale(1 / d).dot(look) > CONE_COS;
				})
				.stream()
				.max(Comparator.comparingDouble(m -> m.position().subtract(eye).normalize().dot(look)))
				.orElse(null);
		if (target == null) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.no_lock"));
			return InteractionResult.FAIL;
		}
		if (!player.getAbilities().instabuild) {
			// Survival: every shot uses a Stinger missile from the inventory (made at a factory).
			ItemStack missile = null;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack s = player.getInventory().getItem(i);
				if (s.is(com.stasdoto.airdefense.registry.ModItems.STINGER_MISSILE)) {
					missile = s;
					break;
				}
			}
			if (missile == null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.no_stinger"));
				return InteractionResult.FAIL;
			}
			missile.shrink(1);
		}
		Vec3 from = eye.add(look.scale(1.2)).add(0, -0.2, 0);
		MissileEntity.launchInterceptor(serverLevel, MissileType.STINGER, from, look, target);
		Effects.launchBlast(serverLevel, eye.subtract(look.scale(1.5)), MissileType.STINGER);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RADAR_LOCK, SoundSource.PLAYERS, 1.0f, 1.3f);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		player.sendOverlayMessage(Component.translatable("message.airdefense.lock", target.getDisplayStack().getHoverName(), (int) target.distanceTo(player)));
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.airdefense.manpads_help").withStyle(ChatFormatting.GRAY));
	}
}
