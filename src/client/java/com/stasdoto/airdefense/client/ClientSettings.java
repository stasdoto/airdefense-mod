package com.stasdoto.airdefense.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import com.stasdoto.airdefense.AirDefense;

/**
 * 2.0: the player's own settings for the mod's look and feel, kept on his machine (config/airdefense-client.json) and
 * changed on the settings screen ({@link com.stasdoto.airdefense.client.ui.SettingsScreen}): how busy the towns' streets
 * are, how detailed the vehicles are drawn, the vehicles' lights, the smoke of damaged vehicles, the chase camera.
 * Read anywhere on the client through the static getters (cheap: plain fields).
 */
public final class ClientSettings {
	/** Vehicle model detail: the full mesh, or the lighter one (far-away LODs sooner, fewer small parts). */
	public enum Detail {
		HIGH, LOW
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE = "airdefense-client.json";

	private static float trafficDensity = 1f;
	private static float pedestrianDensity = 1f;
	private static Detail detail = Detail.HIGH;
	private static boolean vehicleLights = true;
	private static boolean damageSmoke = true;
	private static boolean chaseCamera = true;
	private static boolean loaded;

	private ClientSettings() {
	}

	/** Town traffic: 0 (none) .. 2 (twice the usual), 1 by default. */
	public static float trafficDensity() {
		load();
		return trafficDensity;
	}

	/** Passers-by in the towns: 0 .. 2, 1 by default. */
	public static float pedestrianDensity() {
		load();
		return pedestrianDensity;
	}

	public static Detail detail() {
		load();
		return detail;
	}

	public static boolean highDetail() {
		return detail() == Detail.HIGH;
	}

	/** Headlights, tail and brake lights, indicators (and the light they throw on the road). */
	public static boolean vehicleLights() {
		load();
		return vehicleLights;
	}

	/** Smoke and fire from damaged vehicles, burning wrecks. */
	public static boolean damageSmoke() {
		load();
		return damageSmoke;
	}

	/** The camera that follows behind a vehicle being driven (read by the vehicle camera code). */
	public static boolean chaseCamera() {
		load();
		return chaseCamera;
	}

	public static void setTrafficDensity(float v) {
		trafficDensity = clamp(v);
		save();
	}

	public static void setPedestrianDensity(float v) {
		pedestrianDensity = clamp(v);
		save();
	}

	public static void setDetail(Detail d) {
		detail = d;
		save();
	}

	public static void setVehicleLights(boolean on) {
		vehicleLights = on;
		save();
	}

	public static void setDamageSmoke(boolean on) {
		damageSmoke = on;
		save();
	}

	public static void setChaseCamera(boolean on) {
		chaseCamera = on;
		save();
	}

	/** Back to the defaults (and saved). */
	public static void reset() {
		trafficDensity = 1f;
		pedestrianDensity = 1f;
		detail = Detail.HIGH;
		vehicleLights = true;
		damageSmoke = true;
		chaseCamera = true;
		save();
	}

	private static float clamp(float v) {
		// In steps of 10 %, 0..200 %.
		return Math.round(Math.max(0f, Math.min(2f, v)) * 10f) / 10f;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path p = path();
			if (!Files.exists(p)) {
				return;
			}
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				JsonObject o = GSON.fromJson(r, JsonObject.class);
				if (o == null) {
					return;
				}
				if (o.has("trafficDensity")) {
					trafficDensity = clamp(o.get("trafficDensity").getAsFloat());
				}
				if (o.has("pedestrianDensity")) {
					pedestrianDensity = clamp(o.get("pedestrianDensity").getAsFloat());
				}
				if (o.has("detail")) {
					detail = "low".equalsIgnoreCase(o.get("detail").getAsString()) ? Detail.LOW : Detail.HIGH;
				}
				if (o.has("vehicleLights")) {
					vehicleLights = o.get("vehicleLights").getAsBoolean();
				}
				if (o.has("damageSmoke")) {
					damageSmoke = o.get("damageSmoke").getAsBoolean();
				}
				if (o.has("chaseCamera")) {
					chaseCamera = o.get("chaseCamera").getAsBoolean();
				}
			}
		} catch (Exception e) {
			AirDefense.LOGGER.warn("[airdefense] could not read {}: {}", FILE, e.toString());
		}
	}

	private static void save() {
		loaded = true;
		JsonObject o = new JsonObject();
		o.addProperty("trafficDensity", trafficDensity);
		o.addProperty("pedestrianDensity", pedestrianDensity);
		o.addProperty("detail", detail == Detail.LOW ? "low" : "high");
		o.addProperty("vehicleLights", vehicleLights);
		o.addProperty("damageSmoke", damageSmoke);
		o.addProperty("chaseCamera", chaseCamera);
		try {
			Path p = path();
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(o, w);
			}
		} catch (Exception e) {
			AirDefense.LOGGER.warn("[airdefense] could not write {}: {}", FILE, e.toString());
		}
	}
}
