package com.stasdoto.airdefense.nation;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import com.stasdoto.airdefense.street.StreetBlocks;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * 2.0: the filling stations' pumps. A vehicle standing by a pump is filled up - from the town's own fuel when the pump
 * is a town's (its stock goes down, and a dry town has none to give), freely from a pump the player put up himself.
 * Right-click the pump to fill the vehicle you came in (or the nearest one); or just stop next to it with the engine
 * running and it fills by itself, 40 litres a second.
 */
public final class FuelPumps {
	/** How near the pump a vehicle's side must be (blocks). */
	public static final double REACH = 3.5;
	/** For the tests: litres pumped. */
	public static volatile int pumped;

	private FuelPumps() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FuelPumps::tick);
	}

	/** Right-click on a pump. */
	public static void use(ServerLevel level, BlockPos pump, Player player) {
		VehicleEntity v = player.getVehicle() instanceof VehicleEntity mine ? mine : null;
		if (v == null || !near(v, pump)) {
			v = null;
			double best = Double.MAX_VALUE;
			for (VehicleEntity c : vehiclesBy(level, pump)) {
				double d = c.distanceToSqr(pump.getX() + 0.5, pump.getY(), pump.getZ() + 0.5);
				if (d < best) {
					best = d;
					v = c;
				}
			}
		}
		if (v == null) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.pump.none"));
			return;
		}
		if (v.infiniteFuel()) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.fuel.infinite"));
			return;
		}
		int got = fill(level, v, pump, v.getVehicleType().fuelCapacity());
		if (got < 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.pump.dry"));
			return;
		}
		if (got == 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.fuel.full", (int) v.getFuel(), v.getVehicleType().fuelCapacity()));
			return;
		}
		level.playSound(null, pump, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8f, 0.8f);
		player.sendOverlayMessage(Component.translatable("message.airdefense.pump.filled", got, (int) v.getFuel(), v.getVehicleType().fuelCapacity()));
	}

	/**
	 * Fills the vehicle from the pump, up to {@code max} litres: from the town's stock if the pump stands at a town's
	 * filling station. Returns the litres given, or -1 if the town has none left.
	 */
	public static int fill(ServerLevel level, VehicleEntity v, BlockPos pump, int max) {
		if (v.infiniteFuel()) {
			return 0;
		}
		Settlement town = stationTown(level, pump);
		if (town != null) {
			if (town.eco.stock[VillageEconomy.FUEL] <= 0 && v.getFuel() < v.getVehicleType().fuelCapacity() - 1) {
				return -1;
			}
			int got = Supply.refuel(town.eco, v, max);
			pumped += got;
			return got;
		}
		int want = (int) Math.min(max, v.getVehicleType().fuelCapacity() - v.getFuel());
		if (want <= 0) {
			return 0;
		}
		v.refuel(want);
		pumped += want;
		return want;
	}

	/** The town whose filling station this pump is at (null for a pump standing on its own). */
	@Nullable
	static Settlement stationTown(ServerLevel level, BlockPos pump) {
		Politics p = Politics.get(level.getServer());
		for (Settlement s : p.settlements.values()) {
			for (Building b : s.eco.buildings) {
				if (b.type == BuildingType.GAS_STATION && b.middle().distSqr(pump) < 16 * 16) {
					return s;
				}
			}
		}
		return null;
	}

	private static boolean near(VehicleEntity v, BlockPos pump) {
		AABB box = v.getBoundingBox().inflate(REACH, 1.5, REACH);
		return box.contains(pump.getX() + 0.5, pump.getY() + 0.5, pump.getZ() + 0.5)
				|| v.distanceToSqr(pump.getX() + 0.5, pump.getY(), pump.getZ() + 0.5) < Mth.square(v.getVehicleType().geometry.length() / 2 + REACH);
	}

	private static List<VehicleEntity> vehiclesBy(ServerLevel level, BlockPos pump) {
		double r = 9;
		return level.getEntitiesOfClass(VehicleEntity.class, new AABB(pump).inflate(r, 3, r), x -> x.isAlive() && near(x, pump));
	}

	// ------------------------------------------------------------------------------------------------
	// By itself: a driven vehicle standing by a pump

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 7) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.getVehicle() instanceof VehicleEntity v) || !v.isDriver(player) || v.infiniteFuel()
					|| v.getFuel() >= v.getVehicleType().fuelCapacity() - 1 || Math.abs(v.getSpeed()) > 0.02f) {
				continue;
			}
			ServerLevel level = (ServerLevel) v.level();
			BlockPos pump = pumpBy(level, v);
			if (pump == null) {
				continue;
			}
			int got = fill(level, v, pump, 40);
			if (got > 0) {
				if (server.getTickCount() % 60 == 7) {
					level.playSound(null, pump, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.5f, 0.8f);
				}
				player.sendOverlayMessage(Component.translatable("message.airdefense.pump.filling", (int) v.getFuel(),
						v.getVehicleType().fuelCapacity()));
			} else if (got < 0 && server.getTickCount() % 100 == 7) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.pump.dry"));
			}
		}
	}

	/** A pump within reach of the vehicle's sides, or null. */
	@Nullable
	public static BlockPos pumpBy(ServerLevel level, VehicleEntity v) {
		AABB box = v.getBoundingBox().inflate(REACH, 1, REACH);
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
			for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
				for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
					if (level.getBlockState(q.set(x, y, z)).is(StreetBlocks.FUEL_PUMP)) {
						return q.immutable();
					}
				}
			}
		}
		return null;
	}
}
