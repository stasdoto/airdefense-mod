package com.stasdoto.airdefense.client.drone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.drone.DroneCam;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Client side of drones: the nose camera's picture (grain, scan lines, the reticle and the telemetry; static when
 * the signal is lost), and the engines heard in the air - the Shahed's rasping buzz near by and its low drone far
 * away, the cruise missile's jet - each following its drone.
 */
public final class DroneClient {
	private static final int MAX_LOOPS = 10;
	private static final Map<Integer, List<Loop>> LOOPS = new HashMap<>();
	private static final Random RANDOM = new Random();
	private static int camDrone = -1;
	private static int camTx;
	private static int camTz;
	private static int lostTicks;
	/** For the tests: the camera's view was used, and how many engine loops played. */
	public static int camFrames;
	public static int loopsStarted;

	private DroneClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DroneCam.CamPayload.TYPE, (payload, context) -> {
			camDrone = payload.droneId();
			camTx = payload.tx();
			camTz = payload.tz();
			if (payload.droneId() < 0 && payload.lost()) {
				lostTicks = 30;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(DroneClient::tick);
		HudElementRegistry.addLast(AirDefense.id("drone_cam"), DroneClient::hud);
	}

	private static void tick(Minecraft mc) {
		if (lostTicks > 0) {
			lostTicks--;
		}
		if (mc.level == null) {
			LOOPS.clear();
			return;
		}
		Entity cam = mc.getCameraEntity();
		Vec3 ear = cam != null ? cam.position() : Vec3.ZERO;
		// The nearest flying engines get a sound each.
		List<MissileEntity> near = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof MissileEntity m && m.isAlive() && hasEngine(m.getMissileType()) && m.distanceToSqr(ear) < 640 * 640) {
				near.add(m);
			}
		}
		near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(ear)));
		for (int i = 0; i < Math.min(MAX_LOOPS, near.size()); i++) {
			MissileEntity m = near.get(i);
			if (!LOOPS.containsKey(m.getId())) {
				List<Loop> list = new ArrayList<>();
				float pitch = 0.94f + RANDOM.nextFloat() * 0.12f;
				if (m.getMissileType().kind == MissileType.Kind.DRONE) {
					list.add(new Loop(ModSounds.SHAHED_LOOP, m, 1.0f, pitch));
					list.add(new Loop(ModSounds.SHAHED_FAR, m, 0.55f, pitch));
				} else {
					list.add(new Loop(ModSounds.CRUISE_LOOP, m, 1.0f, pitch));
				}
				for (Loop l : list) {
					mc.getSoundManager().play(l);
				}
				LOOPS.put(m.getId(), list);
				loopsStarted++;
			}
		}
		for (Iterator<Map.Entry<Integer, List<Loop>>> it = LOOPS.entrySet().iterator(); it.hasNext(); ) {
			List<Loop> list = it.next().getValue();
			if (list.getFirst().isStopped()) {
				it.remove();
			}
		}
	}

	private static boolean hasEngine(MissileType t) {
		return t.kind == MissileType.Kind.DRONE || t.kind == MissileType.Kind.CRUISE;
	}

	/** An engine sound that follows its drone and dies with it. */
	private static final class Loop extends AbstractTickableSoundInstance {
		private final MissileEntity drone;
		private final float base;

		Loop(SoundEvent event, MissileEntity drone, float volume, float pitch) {
			super(event, SoundSource.HOSTILE, drone.getRandom());
			this.drone = drone;
			this.base = volume;
			this.looping = true;
			this.delay = 0;
			this.volume = volume;
			this.pitch = pitch;
			this.x = drone.getX();
			this.y = drone.getY();
			this.z = drone.getZ();
		}

		@Override
		public void tick() {
			if (!drone.isAlive() || drone.isRemoved()) {
				stop();
				return;
			}
			x = drone.getX();
			y = drone.getY();
			z = drone.getZ();
			// Diving: the engine screams a little higher.
			volume = base;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// The camera picture

	private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		Font font = mc.font;
		if (mc.getCameraEntity() instanceof MissileEntity m) {
			camFrames++;
			grain(g, w, h, 0x30);
			int cx = w / 2;
			int cy = h / 2;
			int c = 0xC0E8F0E8;
			g.fill(cx - 14, cy, cx - 4, cy + 1, c);
			g.fill(cx + 5, cy, cx + 15, cy + 1, c);
			g.fill(cx, cy - 14, cx + 1, cy - 4, c);
			g.fill(cx, cy + 5, cx + 1, cy + 15, c);
			int bw = Math.min(w, h) / 3;
			corners(g, cx - bw, cy - bw * 2 / 3, cx + bw, cy + bw * 2 / 3, c);
			boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
			if (blink) {
				g.fill(8, 9, 13, 14, 0xFFFF3030);
			}
			g.text(font, "REC", 16, 8, 0xFFFF5050);
			g.text(font, Component.translatable("hud.airdefense.drone.title", Component.translatable("radar.airdefense.contact."
					+ m.getMissileType().name().toLowerCase(java.util.Locale.ROOT))), 44, 8, 0xFFE8F0E8);
			int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, m.getBlockX(), m.getBlockZ());
			double hgt = m.getY() - ground;
			double v = Math.hypot(m.getX() - m.xo, m.getZ() - m.zo) * 20 * 3.6;
			double dist = camDrone == m.getId() ? Math.hypot(camTx - m.getX(), camTz - m.getZ()) : -1;
			float heading = Mth.wrapDegrees(-m.getYRot() + 180);
			int y = h - 52;
			g.text(font, Component.translatable("hud.airdefense.drone.alt", (int) Math.round(hgt)), 8, y, 0xFFE8F0E8);
			g.text(font, Component.translatable("hud.airdefense.drone.speed", (int) Math.round(v)), 8, y + 10, 0xFFE8F0E8);
			if (dist >= 0) {
				g.text(font, Component.translatable("hud.airdefense.drone.dist", (int) Math.round(dist)), 8, y + 20, dist < 60 ? 0xFFFF7A6A : 0xFFE8F0E8);
			}
			g.text(font, Component.translatable("hud.airdefense.drone.heading", (int) Math.floorMod((int) heading, 360)), 8, y + 30, 0xFFE8F0E8);
			Component hint = Component.translatable("hud.airdefense.drone.exit", mc.options.keyShift.getTranslatedKeyMessage());
			g.text(font, hint, w - font.width(hint) - 8, h - 14, 0xA0E8F0E8);
		} else if (lostTicks > 0) {
			g.fill(0, 0, w, h, 0xFF101010);
			grain(g, w, h, 0xC0);
			Component lost = Component.translatable("hud.airdefense.drone.lost");
			g.text(font, lost, (w - font.width(lost)) / 2, h / 2 - 4, 0xFFFF5050);
		}
	}

	private static void grain(GuiGraphicsExtractor g, int w, int h, int alpha) {
		for (int y = 0; y < h; y += 3) {
			g.fill(0, y, w, y + 1, 0x18000000);
		}
		for (int i = 0; i < 260; i++) {
			int x = RANDOM.nextInt(w);
			int y = RANDOM.nextInt(h);
			int v = 90 + RANDOM.nextInt(160);
			g.fill(x, y, x + 1 + RANDOM.nextInt(2), y + 1, (alpha << 24) | (v << 16) | (v << 8) | v);
		}
		// Darker edges, like a cheap lens.
		g.fill(0, 0, w, 6, 0x50000000);
		g.fill(0, h - 6, w, h, 0x50000000);
		g.fill(0, 0, 6, h, 0x50000000);
		g.fill(w - 6, 0, w, h, 0x50000000);
	}

	private static void corners(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
		int l = 10;
		g.fill(x0, y0, x0 + l, y0 + 1, c);
		g.fill(x0, y0, x0 + 1, y0 + l, c);
		g.fill(x1 - l, y0, x1, y0 + 1, c);
		g.fill(x1 - 1, y0, x1, y0 + l, c);
		g.fill(x0, y1 - 1, x0 + l, y1, c);
		g.fill(x0, y1 - l, x0 + 1, y1, c);
		g.fill(x1 - l, y1 - 1, x1, y1, c);
		g.fill(x1 - 1, y1 - l, x1, y1, c);
	}
}
