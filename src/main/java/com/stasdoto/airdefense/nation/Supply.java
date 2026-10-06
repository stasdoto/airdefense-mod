package com.stasdoto.airdefense.nation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import com.stasdoto.airdefense.registry.ModItems;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * Oil, fuel and ammunition (stage R7), as simple as it gets: oil wells pump crude oil, the refinery makes fuel from it,
 * the logistics hub makes ammunition from iron. Every vehicle that stops by a gas station gets fuel; by a logistics
 * hub, fuel and ammunition - all from the town's own stock. Fuel trucks and supply trucks carry it to where it is
 * needed.
 */
public final class Supply {
	public static final int WELL_OIL = 40;
	public static final int REFINERY_OIL = 60;
	public static final double REFINERY_YIELD = 0.8;
	public static final int STATION_RANGE = 10;
	public static final int HUB_RANGE = 18;
	/** For the tests. */
	public static int pumped;
	public static int refined;
	public static int served;

	private Supply() {
	}

	static void tick(ServerLevel level, Politics p) {
		long t = level.getGameTime();
		for (Settlement s : p.settlements.values()) {
			if ((t + s.id * 97L) % 1200 == 0) {
				produce(p, s);
			}
			if (t % 20 == 13 && Economy.owned(p, s) && level.isLoaded(s.center)) {
				serve(level, s);
			}
		}
	}

	/** Once a minute: pump, refine, make ammunition. */
	public static void produce(Politics p, Settlement s) {
		VillageEconomy e = s.eco;
		int wells = e.count(BuildingType.OIL_WELL);
		int refineries = e.count(BuildingType.REFINERY);
		int hubs = e.count(BuildingType.LOGISTICS_HUB);
		if (wells + refineries + hubs == 0) {
			return;
		}
		int cap = e.liquidCap();
		int oil = Math.min(cap - e.stock[VillageEconomy.OIL], wells * WELL_OIL);
		e.stock[VillageEconomy.OIL] += Math.max(0, oil);
		pumped += Math.max(0, oil);
		int crude = Math.min(e.stock[VillageEconomy.OIL], refineries * REFINERY_OIL);
		int fuel = Math.min(cap - e.stock[VillageEconomy.FUEL], (int) (crude * REFINERY_YIELD));
		if (fuel > 0) {
			e.stock[VillageEconomy.OIL] -= (int) Math.ceil(fuel / REFINERY_YIELD);
			e.stock[VillageEconomy.FUEL] += fuel;
			refined += fuel;
		}
		int batches = Math.min(hubs * 5, Math.min(e.stock[VillageEconomy.IRON] / 2, (e.ammoCap() - e.stock[VillageEconomy.AMMO]) / 10));
		if (batches > 0) {
			e.stock[VillageEconomy.IRON] -= batches * 2;
			e.stock[VillageEconomy.AMMO] += batches * 10;
		}
		p.setDirty();
	}

	/** Every second: vehicles at a gas station or a logistics hub are filled up and rearmed. */
	static void serve(ServerLevel level, Settlement s) {
		VillageEconomy e = s.eco;
		for (Building b : e.buildings) {
			if (!b.done || b.type != BuildingType.GAS_STATION && b.type != BuildingType.LOGISTICS_HUB) {
				continue;
			}
			boolean hub = b.type == BuildingType.LOGISTICS_HUB;
			int r = hub ? HUB_RANGE : STATION_RANGE;
			for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, new AABB(b.middle()).inflate(r, 10, r), VehicleEntity::isAlive)) {
				refuel(e, v, 80);
				if (hub) {
					rearm(e, v);
				}
			}
		}
	}

	/** Fuel from a stock into a vehicle; returns litres given. */
	public static int refuel(VillageEconomy e, VehicleEntity v, int max) {
		if (v.infiniteFuel()) {
			return 0;
		}
		int want = (int) Math.min(max, v.getVehicleType().fuelCapacity() - v.getFuel());
		int give = Math.min(want, e.stock[VillageEconomy.FUEL]);
		if (give <= 0) {
			return 0;
		}
		v.refuel(give);
		e.stock[VillageEconomy.FUEL] -= give;
		served++;
		return give;
	}

	/** One step of rearming: spare rounds (or a missile), paid in ammunition points. */
	public static void rearm(VillageEconomy e, VehicleEntity v) {
		VehicleType t = v.getVehicleType();
		if (t.ordnance != null && v.getOrdnance() < t.ordnance.count && e.stock[VillageEconomy.AMMO] >= 6) {
			v.addOrdnance(1);
			e.stock[VillageEconomy.AMMO] -= 6;
			served++;
		}
		if (v.reserveSpace() <= 0) {
			return;
		}
		int[] step = rearmStep(t);
		if (e.stock[VillageEconomy.AMMO] >= step[1]) {
			v.addReserve(step[0]);
			e.stock[VillageEconomy.AMMO] -= step[1];
			served++;
		}
	}

	/** {units given, ammunition points they cost}. */
	public static int[] rearmStep(VehicleType t) {
		if (t.isLauncher()) {
			return new int[]{1, 8};
		}
		if (t.weapon != null) {
			return t.weapon.cannon() ? new int[]{1, 3} : new int[]{10, 2};
		}
		if (t.defense != null) {
			if (t.defense.gunOnly()) {
				return new int[]{1, 1};
			}
			return switch (t) {
				case PATRIOT, S300, SAMPT, BUK -> new int[]{1, 8};
				default -> new int[]{1, 4};
			};
		}
		return new int[]{1, 4};
	}

	/** A player's town whose centre or one of whose buildings is within 40 blocks (where trucks load and unload). */
	public static Settlement townNear(Politics p, net.minecraft.core.BlockPos pos) {
		for (Settlement s : p.settlements.values()) {
			if (!Economy.owned(p, s)) {
				continue;
			}
			if (s.center.distSqr(pos) < 40 * 40) {
				return s;
			}
			for (Building b : s.eco.buildings) {
				if (b.done && b.type != BuildingType.ROADS && b.middle().distSqr(pos) < 40 * 40) {
					return s;
				}
			}
		}
		return null;
	}

	/** Empty jerrycans filled at the player's own gas station, refinery or hub (20 l each). */
	public static boolean fillCans(ServerLevel level, ServerPlayer player, ItemStack cans) {
		Politics p = Politics.get(level.getServer());
		for (Settlement s : p.settlements.values()) {
			if (!Economy.owner(p, s, player)) {
				continue;
			}
			for (Building b : s.eco.buildings) {
				if (!b.done || b.type != BuildingType.GAS_STATION && b.type != BuildingType.REFINERY && b.type != BuildingType.LOGISTICS_HUB) {
					continue;
				}
				if (b.middle().distSqr(player.blockPosition()) > 16 * 16) {
					continue;
				}
				int n = Math.min(cans.getCount(), s.eco.stock[VillageEconomy.FUEL] / 20);
				if (n <= 0) {
					player.sendOverlayMessage(Component.translatable("nation.airdefense.supply.no_fuel", s.name));
					return true;
				}
				s.eco.stock[VillageEconomy.FUEL] -= n * 20;
				cans.shrink(n);
				ItemStack full = new ItemStack(ModItems.JERRYCAN, n);
				if (!player.getInventory().add(full)) {
					player.spawnAtLocation(level, full);
				}
				p.setDirty();
				level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1f, 0.8f);
				player.sendOverlayMessage(Component.translatable("nation.airdefense.supply.filled", n, s.eco.stock[VillageEconomy.FUEL]));
				return true;
			}
		}
		player.sendOverlayMessage(Component.translatable("nation.airdefense.supply.no_station"));
		return false;
	}
}
