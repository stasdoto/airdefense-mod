package com.stasdoto.airdefense.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;

import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/** Server side of the tablet map: answers with the vehicle situation and carries out orders given on the map. */
public final class MapServer {
	/** Vehicles further away than this are not listed (blocks). */
	private static final double LIST_RANGE = 6000;
	private static final int MAX_LISTED = 96;

	private MapServer() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(MapActionPayload.TYPE, MapActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapStatusPayload.TYPE, MapStatusPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MapActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	private static void handle(ServerPlayer player, MapActionPayload p) {
		// The map is the tablet's screen: no tablet in hand, no orders.
		ItemStack tablet = DesignatorItem.held(player);
		if (tablet == null) {
			return;
		}
		ServerLevel level = player.level();
		switch (p.action()) {
			case MapActionPayload.REFRESH -> {
			}
			case MapActionPayload.STRIKE -> {
				if (level.getEntity(p.vehicleId()) instanceof VehicleEntity v && v.getVehicleType().isLauncher()) {
					BlockPos target = ground(level, p.x(), p.y(), p.z());
					DesignatorItem.setTarget(tablet, target);
					v.commandStrike(target, player);
				}
			}
			case MapActionPayload.SET_MODE -> {
				if (level.getEntity(p.vehicleId()) instanceof VehicleEntity v && v.getVehicleType().isDefense()) {
					v.setModeByOrder(p.x(), player);
				}
			}
			case MapActionPayload.SET_TARGET -> {
				BlockPos target = ground(level, p.x(), p.y(), p.z());
				DesignatorItem.setTarget(tablet, target);
				int dist = (int) Math.sqrt(target.distToCenterSqr(player.position()));
				player.sendOverlayMessage(Component.translatable("message.airdefense.target_set", target.getX(), target.getY(), target.getZ(), dist));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DESIGNATE, SoundSource.PLAYERS, 0.6f, 1.2f);
			}
			case MapActionPayload.CLEAR_TARGET -> {
				DesignatorItem.clearTarget(tablet);
				player.sendOverlayMessage(Component.translatable("message.airdefense.target_cleared"));
			}
			default -> {
				return;
			}
		}
		ServerPlayNetworking.send(player, status(level, player));
		com.stasdoto.airdefense.nation.NationNet.sendMap(level, player);
	}

	/** A point picked on the map, on the ground: the height comes from the world (the chunk is loaded for it if needed). */
	public static BlockPos ground(ServerLevel level, int x, int y, int z) {
		x = Mth.clamp(x, -29_999_000, 29_999_000);
		z = Mth.clamp(z, -29_999_000, 29_999_000);
		if (y != MapActionPayload.Y_UNKNOWN) {
			return new BlockPos(x, y, z);
		}
		// The chunk's height is the top block itself (the level's would be the air above it).
		int top = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
		return new BlockPos(x, Math.max(level.getMinY(), top), z);
	}

	public static MapStatusPayload status(ServerLevel level, ServerPlayer player) {
		List<VehicleEntity> vehicles = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(VehicleEntity.class),
				v -> v.isAlive() && v.distanceToSqr(player) < LIST_RANGE * LIST_RANGE));
		vehicles.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
		List<MapStatusPayload.Entry> entries = new ArrayList<>();
		for (VehicleEntity v : vehicles) {
			if (entries.size() >= MAX_LISTED) {
				break;
			}
			entries.add(v.mapEntry());
		}
		return new MapStatusPayload(entries);
	}
}
