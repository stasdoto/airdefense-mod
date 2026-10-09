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
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.radar.RadarNetwork;
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
		PayloadTypeRegistry.clientboundPlay().register(RadarPayload.TYPE, RadarPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MapActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	private static void handle(ServerPlayer player, MapActionPayload p) {
		// The map is the tablet's screen: no tablet in hand, no orders.
		ItemStack tablet = DesignatorItem.held(player);
		if (tablet == null) {
			return;
		}
		ServerLevel level = player.level();
		switch (p.kind()) {
			case MapActionPayload.REFRESH -> {
			}
			case MapActionPayload.RADAR -> {
				ServerPlayNetworking.send(player, status(level, player));
				ServerPlayNetworking.send(player, radar(level));
				return;
			}
			case MapActionPayload.STRIKE -> {
				if (level.getEntity(p.vehicleId()) instanceof VehicleEntity v && v.getVehicleType().isLauncher() && mine(level, player, v)) {
					BlockPos target = ground(level, p.x(), p.y(), p.z());
					DesignatorItem.setTarget(tablet, target);
					v.commandStrike(target, player);
				}
			}
			case MapActionPayload.FIRE_MISSION -> {
				if (level.getEntity(p.vehicleId()) instanceof VehicleEntity v && v.getVehicleType().isArtillery() && mine(level, player, v)) {
					BlockPos target = ground(level, p.x(), p.y(), p.z());
					DesignatorItem.setTarget(tablet, target);
					v.commandFire(target, player, p.rounds());
				}
			}
			case MapActionPayload.SET_MODE -> {
				if (level.getEntity(p.vehicleId()) instanceof VehicleEntity v && v.getVehicleType().hasMode() && mine(level, player, v)) {
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
			case MapActionPayload.MASS_STRIKE -> {
				BlockPos target = ground(level, p.x(), p.y(), p.z());
				DesignatorItem.setTarget(tablet, target);
				com.stasdoto.airdefense.drone.DroneNet.massStrike(player, target);
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
		// The chunk's height is the top block itself (the level's would be the air above it). A point far off in
		// ground nobody has loaded is not loaded for it (that would stop the server): the land's height from the
		// world generator will do - the missile finds the ground there by itself.
		int top = level.hasChunk(x >> 4, z >> 4)
				? level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15)
				: com.stasdoto.airdefense.nation.Cities.terrain(level).top(x, z);
		return new BlockPos(x, Math.max(level.getMinY(), top), z);
	}

	/** What the radar stations see right now. */
	public static RadarPayload radar(ServerLevel level) {
		List<RadarPayload.Station> stations = new ArrayList<>();
		for (RadarNetwork.Station st : RadarNetwork.stations(level)) {
			int type = level.getEntity(st.id()) instanceof VehicleEntity v ? v.getVehicleType().ordinal() : 0;
			stations.add(new RadarPayload.Station(st.id(), type, (float) st.pos().x, (float) st.pos().y, (float) st.pos().z, st.yaw()));
		}
		List<RadarPayload.Contact> contacts = new ArrayList<>();
		for (MissileEntity m : RadarNetwork.contacts(level, 96)) {
			MissileType t = m.getMissileType();
			if (t.kind == MissileType.Kind.DIRECT) {
				continue;
			}
			MissileType shown = t;
			boolean decoy = false;
			if (t.isDecoy()) {
				RadarNetwork.Station c = RadarNetwork.coverage(level, m.position());
				decoy = c != null && m.decoyRoll() < 0.35 + c.type().discrimination;
				if (!decoy) {
					shown = t == MissileType.GERBERA ? MissileType.SHAHED : MissileType.ISKANDER;
				}
			}
			Vec3 v = m.getFlightVelocity();
			Vec3 tg = m.getTarget();
			Vec3 l = m.getLaunchPos();
			BlockPos column = m.blockPosition();
			float height = level.hasChunkAt(column)
					? (float) (m.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ())) : (float) (m.getY() - 64);
			contacts.add(new RadarPayload.Contact(m.getId(), shown.ordinal(), t.threat, decoy, (float) m.getX(), (float) m.getY(), (float) m.getZ(),
					(float) v.x, (float) v.y, (float) v.z, (float) tg.x, (float) tg.y, (float) tg.z, (float) l.x, (float) l.z,
					Math.min(127, m.getEngagedBy()), height));
		}
		return new RadarPayload(stations, contacts);
	}

	/** The player's own vehicle (or one of his country's towns'): only those take orders from his tablet. */
	public static boolean mine(ServerLevel level, ServerPlayer player, VehicleEntity v) {
		if (v.country == -1) {
			return true;
		}
		com.stasdoto.airdefense.nation.Country own = com.stasdoto.airdefense.nation.Politics.get(level.getServer()).countryOwnedBy(player.getUUID());
		return own != null && own.id == v.country;
	}

	public static MapStatusPayload status(ServerLevel level, ServerPlayer player) {
		com.stasdoto.airdefense.nation.Country own = com.stasdoto.airdefense.nation.Politics.get(level.getServer()).countryOwnedBy(player.getUUID());
		int ownId = own == null ? -1 : own.id;
		List<VehicleEntity> vehicles = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(VehicleEntity.class),
				v -> v.isAlive() && v.distanceToSqr(player) < LIST_RANGE * LIST_RANGE && (v.country == -1 || v.country == ownId)));
		vehicles.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
		List<MapStatusPayload.Entry> entries = new ArrayList<>();
		for (VehicleEntity v : vehicles) {
			if (entries.size() >= MAX_LISTED) {
				break;
			}
			entries.add(v.mapEntry());
		}
		List<MapStatusPayload.Fire> fires = new ArrayList<>();
		long now = level.getGameTime();
		for (com.stasdoto.airdefense.radar.CounterBattery.Fire f : com.stasdoto.airdefense.radar.CounterBattery.fires(level, ownId)) {
			if (fires.size() < 32) {
				fires.add(new MapStatusPayload.Fire((int) f.pos.x, (int) f.pos.y, (int) f.pos.z, (int) Math.min(32000, (now - f.last) / 20),
						Math.min(32000, f.rounds)));
			}
		}
		// 1.34: what the side's reconnaissance drones see, and the drones themselves.
		List<MapStatusPayload.Spot> spots = new ArrayList<>();
		for (com.stasdoto.airdefense.drone.Recon.Seen s : com.stasdoto.airdefense.drone.Recon.seen(level, ownId)) {
			if (spots.size() >= 160) {
				break;
			}
			if (s.pos.distanceToSqr(player.position()) < LIST_RANGE * LIST_RANGE) {
				spots.add(new MapStatusPayload.Spot((int) Math.floor(s.pos.x), (int) s.pos.y, (int) Math.floor(s.pos.z), s.vtype,
						(int) Math.min(32000, (now - s.last) / 20)));
			}
		}
		List<MapStatusPayload.Eye> eyes = new ArrayList<>();
		for (com.stasdoto.airdefense.drone.Recon.Eye e : com.stasdoto.airdefense.drone.Recon.eyes(level, ownId)) {
			if (eyes.size() < 16) {
				eyes.add(new MapStatusPayload.Eye((int) Math.floor(e.pos().x), (int) Math.floor(e.pos().z), (int) e.range(), e.type()));
			}
		}
		return new MapStatusPayload(entries, fires, spots, eyes);
	}
}
