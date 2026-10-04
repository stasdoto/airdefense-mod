package com.stasdoto.airdefense.item;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Target designation tablet: right-click opens the tactical map (pick a launcher and a target, fire, switch air
 * defence modes); sneak + right-click marks the block you're looking at (up to 600 blocks, as far as the world is
 * loaded). Right-click a launcher with it to fire at the mark.
 */
public class DesignatorItem extends Item {
	public static final double RANGE = 600;
	/** Opens the map screen; set by the client code (the screen is client-only). */
	public static Runnable openMap = () -> {
	};

	public DesignatorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isSecondaryUseActive()) {
			if (level.isClientSide()) {
				openMap.run();
			}
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.target_too_far"));
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = hit.getBlockPos();
		setTarget(stack, pos);
		int dist = (int) Math.sqrt(pos.distToCenterSqr(player.position()));
		player.sendOverlayMessage(Component.translatable("message.airdefense.target_set", pos.getX(), pos.getY(), pos.getZ(), dist));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DESIGNATE, SoundSource.PLAYERS, 1.0f, 1.0f);
		return InteractionResult.SUCCESS;
	}

	/** The tablet in either hand, or null. */
	@Nullable
	public static ItemStack held(Player player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof DesignatorItem) {
				return stack;
			}
		}
		return null;
	}

	@Nullable
	public static BlockPos getTarget(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (!tag.contains("target_x")) {
			return null;
		}
		return new BlockPos(tag.getIntOr("target_x", 0), tag.getIntOr("target_y", 0), tag.getIntOr("target_z", 0));
	}

	public static void setTarget(ItemStack stack, BlockPos pos) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt("target_x", pos.getX());
			tag.putInt("target_y", pos.getY());
			tag.putInt("target_z", pos.getZ());
		});
	}

	public static void clearTarget(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.remove("target_x");
			tag.remove("target_y");
			tag.remove("target_z");
		});
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return getTarget(stack) != null;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		BlockPos target = getTarget(stack);
		if (target != null) {
			tooltip.accept(Component.translatable("tooltip.airdefense.target", target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.RED));
		} else {
			tooltip.accept(Component.translatable("tooltip.airdefense.no_target").withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("tooltip.airdefense.designator_help").withStyle(ChatFormatting.DARK_GRAY));
	}
}
