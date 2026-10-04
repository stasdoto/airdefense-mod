package com.stasdoto.airdefense.factory;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Server side of the factory menu. */
public final class FactoryNet {
	private FactoryNet() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(FactoryActionPayload.TYPE, FactoryActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FactoryStatusPayload.TYPE, FactoryStatusPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FactoryActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	private static void handle(ServerPlayer player, FactoryActionPayload p) {
		if (player.distanceToSqr(Vec3.atCenterOf(p.pos())) > 10 * 10
				|| !(player.level().getBlockEntity(p.pos()) instanceof FactoryBlockEntity factory)) {
			return;
		}
		switch (p.action()) {
			case FactoryActionPayload.ORDER -> player.sendOverlayMessage(factory.order(player, Product.byId(p.product()), Mth.clamp(p.count(), 1, 10)));
			case FactoryActionPayload.CANCEL -> factory.cancelLast(player);
			case FactoryActionPayload.TAKE -> {
				int n = factory.takeAll(player);
				player.sendOverlayMessage(Component.translatable("message.airdefense.factory.taken", n));
			}
			default -> {
			}
		}
		send(player, factory, false);
	}

	public static void send(ServerPlayer player, FactoryBlockEntity factory, boolean open) {
		List<Integer> stock = new ArrayList<>();
		for (int s : factory.stock()) {
			stock.add(s);
		}
		ServerPlayNetworking.send(player, new FactoryStatusPayload(factory.getBlockPos(), open, factory.buildPercent(), factory.isCreative(),
				stock, new ArrayList<>(factory.queue()), factory.progress()));
	}
}
