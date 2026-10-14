package com.stasdoto.airdefense.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.street.Weathered;

/**
 * 2.0: the town's own cars, made drivable. A car parked in a bay (drawn by each client for itself, see the client's
 * TownTraffic) can be taken: the player's client asks, the server checks the bay and puts a real car there - the same
 * model and paint - with the player at the wheel.
 */
public final class CivilCars {
	/** The models (and paints) of the town's cars; the same list as the client's traffic uses. */
	public static final String[] MODELS = {"car_saloon_white", "car_saloon_red", "car_saloon_blue", "car_saloon_green", "car_saloon_beige",
			"car_saloon_cherry", "car_saloon_black", "car_saloon_silver", "car_hatch_white", "car_hatch_red", "car_hatch_blue", "car_hatch_green",
			"car_hatch_silver", "car_boxy_beige", "car_boxy_cherry", "car_boxy_white", "car_boxy_blue", "car_boxy_green", "car_suv_black",
			"car_suv_silver", "car_suv_white", "car_taxi"};

	/**
	 * A saloon of about 4.5 m: four seats (the driver front left), wheels on the ground at y = 0. The models are the
	 * town's box models (only a body, no turning wheels yet).
	 */
	public static final VehicleGeometry.Geometry GEOMETRY = new VehicleGeometry.Geometry("civil_car", 4.5F, 1.8F, 1.5F, 2.75F, false,
			new VehicleGeometry.Seat[]{new VehicleGeometry.Seat("driver", -0.4F, 0.35F, -0.15F), new VehicleGeometry.Seat("gunner", 0.4F, 0.35F, -0.15F),
					new VehicleGeometry.Seat("passenger", -0.4F, 0.35F, -1.05F), new VehicleGeometry.Seat("passenger", 0.4F, 0.35F, -1.05F)},
			new VehicleGeometry.Wheel[]{new VehicleGeometry.Wheel("wheel_1_l", 0.31F, true), new VehicleGeometry.Wheel("wheel_1_r", 0.31F, true),
					new VehicleGeometry.Wheel("wheel_2_l", 0.31F, false), new VehicleGeometry.Wheel("wheel_2_r", 0.31F, false)},
			null, new float[]{0F, 0F, 0F}, null, new float[]{0F, 0F, 0F},
			0F, 0F, new VehicleGeometry.Rail[]{}, null, new String[]{}, 6F,
			512, 128);

	private CivilCars() {
	}

	public static int index(String model) {
		for (int i = 0; i < MODELS.length; i++) {
			if (MODELS[i].equals(model)) {
				return i;
			}
		}
		return 0;
	}

	public static String model(int variant) {
		return MODELS[Math.floorMod(variant, MODELS.length)];
	}

	/** For the tests: cars taken from their bays. */
	public static int taken;

	/**
	 * Server: the player takes the car parked in the bay at {@code bay} (the bay's marked block). The car stands where
	 * the client drew it ({@code x, y, z, yaw}), which must be on that bay and near the player.
	 */
	public static boolean take(ServerPlayer player, BlockPos bay, String model, double x, double y, double z, float yaw) {
		if (!(player.level() instanceof ServerLevel level) || player.isPassenger() || player.isSpectator()) {
			return false;
		}
		Block b = level.getBlockState(bay).getBlock();
		if (b != Weathered.PARKING_X && b != Weathered.PARKING_Z) {
			return false;
		}
		Vec3 at = new Vec3(x, y, z);
		if (at.distanceToSqr(Vec3.atCenterOf(bay)) > 9 || player.position().distanceToSqr(at) > 64) {
			return false;
		}
		if (!level.getEntitiesOfClass(VehicleEntity.class, new AABB(at, at).inflate(2.0, 2, 2.0)).isEmpty()) {
			return false;
		}
		VehicleEntity car = VehicleEntity.spawn(level, VehicleType.CIVIL_CAR, at, yaw);
		car.setVariant(index(model));
		car.setUnlimited(false);
		// Somebody's car: a part of a tank of petrol in it.
		car.setFuel(VehicleType.CIVIL_CAR.fuelCapacity() * (0.25f + level.getRandom().nextFloat() * 0.6f));
		player.startRiding(car);
		taken++;
		return true;
	}
}
