package com.stasdoto.airdefense.drone;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileEntity;

/**
 * Watching through a drone's nose camera. The player turns into a spectator riding the drone's view (so the world
 * around the drone loads and is sent to him wherever it flies); when the drone hits, is shot down, or he presses
 * sneak, he is back where he stood, in his own game mode.
 */
public final class DroneCam {
	private record Session(int droneId, ResourceKey<Level> dimension, Vec3 origin, float yRot, float xRot, GameType mode) {
	}

	/** Server → client: watching drone {@code droneId} heading for (tx, tz); droneId -1 = the feed is over. */
	public record CamPayload(int droneId, int tx, int tz, boolean lost) implements CustomPacketPayload {
		public static final Type<CamPayload> TYPE = new Type<>(AirDefense.id("drone_cam"));
		public static final StreamCodec<ByteBuf, CamPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, CamPayload::droneId,
				ByteBufCodecs.INT, CamPayload::tx,
				ByteBufCodecs.INT, CamPayload::tz,
				ByteBufCodecs.BOOL, CamPayload::lost,
				CamPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** For the tests. */
	public static int started;

	private DroneCam() {
	}

	public static boolean watching(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	public static void start(ServerPlayer player, MissileEntity drone) {
		if (SESSIONS.containsKey(player.getUUID())) {
			end(player, false);
		}
		SESSIONS.put(player.getUUID(), new Session(drone.getId(), player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
				player.gameMode()));
		player.setGameMode(GameType.SPECTATOR);
		player.setCamera(drone);
		Vec3 t = drone.getTarget();
		ServerPlayNetworking.send(player, new CamPayload(drone.getId(), (int) t.x, (int) t.z, false));
		player.sendOverlayMessage(Component.translatable("message.airdefense.drone_cam"));
		started++;
	}

	public static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		for (UUID id : SESSIONS.keySet().toArray(new UUID[0])) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				SESSIONS.remove(id);
				continue;
			}
			Entity cam = player.getCamera();
			if (cam == player || cam == null || !cam.isAlive() || cam.isRemoved()) {
				Session s = SESSIONS.get(id);
				boolean alive = player.level().getEntity(s.droneId) instanceof MissileEntity m && m.isAlive();
				end(player, !alive);
			}
		}
	}

	/** Back to where he stood. {@code lost} = the drone is gone (the screen shows the lost signal). */
	public static void end(ServerPlayer player, boolean lost) {
		Session s = SESSIONS.remove(player.getUUID());
		if (s == null) {
			return;
		}
		player.setCamera(player);
		ServerLevel level = player.level().getServer().getLevel(s.dimension);
		if (level != null) {
			player.teleportTo(level, s.origin.x, s.origin.y, s.origin.z, Set.of(), s.yRot, s.xRot, true);
		}
		player.setGameMode(s.mode);
		ServerPlayNetworking.send(player, new CamPayload(-1, 0, 0, lost));
	}
}
