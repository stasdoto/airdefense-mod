package com.stasdoto.airdefense.vehicle;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Puts the vehicle on the ground where you click, nose pointing the way you look. */
public class VehicleItem extends Item {
	public final VehicleType type;

	public VehicleItem(VehicleType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		BlockPos clicked = context.getClickedPos();
		BlockPos at = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(context.getClickedFace());
		Vec3 pos = Vec3.atBottomCenterOf(at);
		float yaw = player != null ? player.getYRot() : 0;
		AABB box = new AABB(pos.x - type.boxWidth / 2, pos.y, pos.z - type.boxWidth / 2, pos.x + type.boxWidth / 2, pos.y + type.boxHeight, pos.z + type.boxWidth / 2);
		if (!level.noCollision(box)) {
			pos = pos.add(0, 1, 0);
		}
		VehicleEntity.spawn(level, type, pos, yaw);
		if (player == null || !player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.airdefense.vehicle.hint").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable(type.isLauncher() ? "item.airdefense.vehicle.hint_launcher" : "item.airdefense.vehicle.hint_defense")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
