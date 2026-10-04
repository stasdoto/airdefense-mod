package com.stasdoto.airdefense.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.stasdoto.airdefense.item.DesignatorItem;

/**
 * /ad target x y z — writes coordinates into the targeting tablet in your hand (for targets further away than you can see).
 * /ad clear — clears it.
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("ad")
						.then(Commands.literal("target")
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
										.executes(ctx -> {
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											ItemStack stack = designator(player);
											if (stack == null) {
												ctx.getSource().sendFailure(Component.translatable("message.airdefense.hold_designator"));
												return 0;
											}
											BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
											DesignatorItem.setTarget(stack, pos);
											int dist = (int) Math.sqrt(pos.distToCenterSqr(player.position()));
											ctx.getSource().sendSuccess(() -> Component.translatable("message.airdefense.target_set",
													pos.getX(), pos.getY(), pos.getZ(), dist), false);
											return 1;
										})))
						.then(Commands.literal("clear")
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									ItemStack stack = designator(player);
									if (stack == null) {
										ctx.getSource().sendFailure(Component.translatable("message.airdefense.hold_designator"));
										return 0;
									}
									DesignatorItem.clearTarget(stack);
									ctx.getSource().sendSuccess(() -> Component.translatable("message.airdefense.target_cleared"), false);
									return 1;
								}))));
	}

	private static ItemStack designator(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof DesignatorItem) {
				return stack;
			}
		}
		return null;
	}
}
