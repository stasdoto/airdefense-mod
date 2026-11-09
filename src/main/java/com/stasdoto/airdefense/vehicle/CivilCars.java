package com.stasdoto.airdefense.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;


/** 2.0: the civilian car (its models and paints). */
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
}
