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
		VehicleEntity vehicle = VehicleEntity.spawn(level, type, pos, yaw);
		if (player == null || !player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
			// Survival: comes loaded, but further reloads need missiles (from a factory, or loaded by hand).
			vehicle.setUnlimited(false);
		}
		return InteractionResult.SUCCESS;
	}

	/** Boats go on the water: right-click the water surface. */
	@Override
	public InteractionResult use(net.minecraft.world.level.Level level, Player player, net.minecraft.world.InteractionHand hand) {
		if (!type.boat) {
			return InteractionResult.PASS;
		}
		net.minecraft.world.phys.BlockHitResult hit = getPlayerPOVHitResult(level, player, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos water = hit.getBlockPos();
		VehicleEntity boat = VehicleEntity.spawn(server, type, new Vec3(hit.getLocation().x, water.getY() + 0.9, hit.getLocation().z), player.getYRot());
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
			boat.setUnlimited(false);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.airdefense.vehicle.hint").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable(type.repairs() ? "item.airdefense.vehicle.hint_recovery" : type.isArtillery() ? "item.airdefense.vehicle.hint_artillery"
				: type.isCounterBattery() ? "item.airdefense.vehicle.hint_cb" : type.isLauncher() ? "item.airdefense.vehicle.hint_launcher"
				: type.isTruck() ? "item.airdefense.vehicle.hint_truck" : type.isAir() ? "item.airdefense.vehicle.hint_air"
				: type.isRadar() ? "item.airdefense.vehicle.hint_radar" : type.boat ? "item.airdefense.vehicle.hint_boat"
				: type.isArmed() ? "item.airdefense.vehicle.hint_armed" : "item.airdefense.vehicle.hint_defense")
				.withStyle(ChatFormatting.DARK_GRAY));
		if (type.isArtillery()) {
			tooltip.accept(Component.translatable("item.airdefense.arty.stats", type.launcher.maxRange, type.launcher.rounds, type.launcher.salvo)
					.withStyle(ChatFormatting.DARK_AQUA));
		}
		if (type.isRadar()) {
			tooltip.accept(Component.translatable("item.airdefense.radar.stats", (int) type.radar.range, (int) type.radar.minAltitude,
					Component.translatable(type.radar.rotates() ? "item.airdefense.radar.rotating" : "item.airdefense.radar.sector", (int) type.radar.sector))
					.withStyle(ChatFormatting.DARK_AQUA));
		}
	}
}
