package com.stasdoto.airdefense.client.weapon;

import org.joml.Vector3fc;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.client.fx.CameraShake;
import com.stasdoto.airdefense.client.fx.ShotFx;
import com.stasdoto.airdefense.client.fx.SquadAudio;
import com.stasdoto.airdefense.client.vehicle.VehicleClient;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.weapon.GunActionPayload;
import com.stasdoto.airdefense.weapon.GunItem;
import com.stasdoto.airdefense.weapon.GunServer;
import com.stasdoto.airdefense.weapon.GunType;
import com.stasdoto.airdefense.weapon.NvgItem;
import com.stasdoto.airdefense.weapon.ShotPayload;

/**
 * Client side of the small arms: the trigger (left mouse button), aiming (right button: zoom, less spread, the
 * scope picture for the sniper rifle), recoil, reloading (R), what the shooter sees and hears at once, other people's
 * shots, the ammunition counter, the hit marker, and the night vision goggles (N).
 */
public final class GunClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(AirDefense.id("gear"));
	public static final KeyMapping NVG = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.airdefense.nvg", InputConstants.KEY_N, CATEGORY));

	private static int cooldown;
	/** Shots fired whose effect on the magazine the server has not sent back yet. */
	private static int unconfirmed;
	private static int lastAmmo = -1;
	private static ItemStack lastStack = ItemStack.EMPTY;
	private static int round;
	/** Extra spread from firing fast (degrees), cools down quickly. */
	private static float bloom;
	private static float zoom = 1f;
	private static int hitMarker;
	private static boolean hitHead;
	private static int dryClicks;
	/** Debug counters read by the automated test. */
	public static int shotsSent;
	public static int hitsConfirmed;
	public static int otherShotsSeen;
	/** Set by the arm-pose mixin, so the test can tell it is active. */
	public static boolean armPoseApplied;

	private GunClient() {
	}

	public static void init() {
		EntityRenderers.register(ModEntities.GRENADE, ThrownItemRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(ShotPayload.TYPE, (payload, context) -> onShot(context.client(), payload));
		// The scope picture and the night vision tint go under the hotbar and the chat; the ammo counter on top.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, AirDefense.id("gun_overlays"), GunClient::overlays);
		HudElementRegistry.addLast(AirDefense.id("gun_hud"), GunClient::hud);
		// No crosshair over the scope picture: the scope has its own.
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, delta) -> {
			if (!scoped()) {
				original.extractRenderState(g, delta);
			}
		});
	}

	// ------------------------------------------------------------------------------------------------
	// State queries (also used by the mixins)

	public static GunType gunOf(ItemStack stack) {
		return stack.getItem() instanceof GunItem g ? g.gun : null;
	}

	public static boolean aiming(Player p) {
		return p.isUsingItem() && p.getUseItem().getItem() instanceof GunItem;
	}

	/** Field-of-view factor: below 1 while aiming (4x for the sniper scope). */
	public static float fovMultiplier() {
		return zoom;
	}

	/** Mouse turn speed factor: slower while zoomed, so the picture moves at the usual speed. */
	public static float sensitivity() {
		return zoom < 0.95f ? zoom : 1f;
	}

	/** Looking through the sniper scope (first person). */
	public static boolean scoped() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && zoom < 0.5f && mc.options.getCameraType().isFirstPerson() && aiming(mc.player)
				&& gunOf(mc.player.getUseItem()) == GunType.SVD;
	}

	/** Rifles are held with both hands (the vanilla charged-crossbow pose fits); a pistol only when aiming. */
	public static void applyArmPose(Avatar entity, AvatarRenderState state) {
		ItemStack main = entity.getMainHandItem();
		GunType gun = gunOf(main);
		if (gun == null || (!gun.longGun() && !aiming(entity instanceof Player p ? p : null))) {
			return;
		}
		boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
		boolean offEmpty = entity.getOffhandItem().isEmpty();
		if (right) {
			state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
			if (offEmpty) {
				state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
			}
		} else {
			state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
			if (offEmpty) {
				state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			}
		}
	}

	private static boolean aiming(LivingEntity e) {
		return e instanceof Player p && aiming(p);
	}

	// ------------------------------------------------------------------------------------------------
	// Input, every tick (on foot; in a vehicle the vehicle keys apply)

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (cooldown > 0) {
			cooldown--;
		}
		if (hitMarker > 0) {
			hitMarker--;
		}
		bloom = Math.max(0, bloom - 0.15f);
		ItemStack stack = p.getMainHandItem();
		GunType gun = gunOf(stack);
		int ammo = gun == null ? -1 : GunItem.ammo(stack);
		if (gun == null || stack.getItem() != lastStack.getItem()) {
			unconfirmed = 0;
		} else if (ammo != lastAmmo) {
			unconfirmed = ammo > lastAmmo ? 0 : Math.max(0, unconfirmed - (lastAmmo - ammo));
		}
		lastAmmo = ammo;
		lastStack = stack;
		float target = gun != null && aiming(p) && !p.isPassenger() ? gun.aimZoom : 1f;
		zoom += (target - zoom) * 0.45f;
		if (Math.abs(target - zoom) < 0.004f) {
			zoom = target;
		}
		while (NVG.consumeClick()) {
			send(GunActionPayload.NVG, Vec3.ZERO);
		}
		if (gun == null || p.getVehicle() instanceof VehicleEntity) {
			return;
		}
		// The left button is the trigger: it must not hit anything or break blocks.
		boolean click = false;
		while (mc.options.keyAttack.consumeClick()) {
			click = true;
		}
		mc.options.keyAttack.setDown(false);
		boolean reloading = p.getCooldowns().isOnCooldown(stack);
		while (VehicleClient.DEPLOY.consumeClick()) {
			if (!reloading && ammo < gun.magazine) {
				send(GunActionPayload.RELOAD, Vec3.ZERO);
			}
		}
		if (mc.gui.screen() != null || p.isSpectator() || !p.isAlive() || p.isPassenger()) {
			return;
		}
		boolean want = gun.auto ? click || mc.mouseHandler.isLeftPressed() : click;
		if (!want || reloading || cooldown > 0) {
			return;
		}
		if (ammo - unconfirmed > 0) {
			fire(mc, p, gun);
		} else {
			// Empty: a dry click, and the reload starts by itself (if there is anything to load).
			cooldown = 6;
			if (dryClicks++ % 2 == 0) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GUN_DRY, 1.0f, 0.6f));
			}
			if (p.getAbilities().instabuild || GunServer.countAmmo(p, gun) > 0) {
				send(GunActionPayload.RELOAD, Vec3.ZERO);
			}
		}
	}

	private static void fire(Minecraft mc, LocalPlayer p, GunType gun) {
		cooldown = gun.interval;
		unconfirmed++;
		round++;
		RandomSource r = p.getRandom();
		boolean aim = aiming(p);
		float spread = aim ? gun.aimSpread : gun.hipSpread;
		if (p.getDeltaMovement().horizontalDistance() > 0.06) {
			spread *= 1.6f;
		}
		if (!p.onGround()) {
			spread *= 2.2f;
		}
		if (p.isCrouching()) {
			spread *= 0.7f;
		}
		spread += bloom;
		bloom = Math.min(gun.auto ? 3f : 1.5f, bloom + (gun.auto ? 0.25f : 0.6f) * (aim ? 0.6f : 1f));
		Vec3 look = p.getLookAngle();
		Vec3 dir = cone(look, spread, r);
		send(GunActionPayload.FIRE, dir);
		shotsSent++;
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(p.getMainArm() == HumanoidArm.LEFT ? -1 : 1);
		Vec3 muzzle = firstPerson ? viewMuzzle(mc, aim, gun) : GunServer.muzzle(p, look);
		if (!gun.rocket()) {
			GunServer.Trace t = GunServer.trace(p.level(), p, p.getEyePosition(), dir, gun.range);
			int hit = t.entity() == null ? (t.block() != null ? ShotPayload.HIT_BLOCK : ShotPayload.HIT_NONE)
					: t.entity() instanceof LivingEntity && !(t.entity() instanceof VehicleEntity) ? ShotPayload.HIT_FLESH : ShotPayload.HIT_METAL;
			ShotFx.shot(mc, gun, muzzle, t.pos(), hit, round, right);
			SquadAudio.play(muzzle, SquadAudio.of(gun.report), 1f);
		}
		// Recoil: the muzzle climbs and wanders a little (less when aiming or crouching).
		float kick = gun.recoil * (aim ? 0.65f : 1f) * (p.isCrouching() ? 0.8f : 1f);
		p.setXRot(Mth.clamp(p.getXRot() - kick * (0.8f + r.nextFloat() * 0.4f), -90f, 90f));
		p.setYRot(p.getYRot() + (r.nextFloat() - 0.5f) * kick * 0.6f);
		CameraShake.add(gun == GunType.SVD || gun.rocket() ? 0.3f : 0.05f);
	}

	/** A random direction within {@code degrees} (a cone, denser in the middle) around {@code look}. */
	private static Vec3 cone(Vec3 look, float degrees, RandomSource r) {
		if (degrees <= 0) {
			return look;
		}
		Vec3 up = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = look.cross(up).normalize();
		Vec3 b = look.cross(a).normalize();
		double rad = Math.toRadians(degrees) * 0.5;
		double x = r.nextGaussian() * rad * 0.6;
		double y = r.nextGaussian() * rad * 0.6;
		return look.add(a.scale(x)).add(b.scale(y)).normalize();
	}

	/** Where the barrel ends on screen in first person: lower right at the hip, in the middle when aiming. */
	private static Vec3 viewMuzzle(Minecraft mc, boolean aim, GunType gun) {
		Camera cam = mc.gameRenderer.mainCamera();
		Vector3fc f = cam.forwardVector();
		Vector3fc u = cam.upVector();
		Vector3fc l = cam.leftVector();
		// The models hold the grip 0.72 ahead, 0.56 right and 0.43 down at the hip (0.56 ahead, centred and 0.1 down
		// when aiming); the barrel reaches this far beyond the grip.
		double barrel = switch (gun) {
			case PKM -> 0.84;
			case SVD -> 0.86;
			case PM -> 0.23;
			case RPG7 -> 0.78;
			default -> 0.63;
		};
		double fwd = (aim ? 0.56 : 0.72) + barrel;
		double side = aim ? 0 : (mc.player.getMainArm() == HumanoidArm.LEFT ? 0.52 : -0.52);
		double down = aim ? 0.07 : 0.4;
		return cam.position().add(f.x() * fwd, f.y() * fwd, f.z() * fwd).add(l.x() * side, l.y() * side, l.z() * side)
				.add(-u.x() * down, -u.y() * down, -u.z() * down);
	}

	private static void send(int action, Vec3 dir) {
		if (ClientPlayNetworking.canSend(GunActionPayload.TYPE)) {
			ClientPlayNetworking.send(new GunActionPayload(action, (float) dir.x, (float) dir.y, (float) dir.z));
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Shots fired by anyone (our own only bring the hit marker)

	private static void onShot(Minecraft mc, ShotPayload s) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		GunType gun = s.gunType();
		if (s.shooter() == mc.player.getId()) {
			if (s.hit() == ShotPayload.HIT_FLESH || s.hit() == ShotPayload.HIT_HEAD) {
				hitMarker = 6;
				hitHead = s.hit() == ShotPayload.HIT_HEAD;
				hitsConfirmed++;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HIT_MARKER, hitHead ? 1.3f : 1.0f, 0.5f));
			}
			return;
		}
		otherShotsSeen++;
		Vec3 from = s.from();
		Vec3 to = s.to();
		Vec3 right = null;
		if (mc.level.getEntity(s.shooter()) instanceof Player shooter) {
			float yaw = shooter.getYRot() * Mth.DEG_TO_RAD;
			right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		}
		ShotFx.shot(mc, gun, from, to, s.hit(), s.round(), right);
		SquadAudio.play(from, SquadAudio.of(gun.report), 1f);
		SquadAudio.bulletPass(from, to, gun.tracerSpeed());
	}

	// ------------------------------------------------------------------------------------------------
	// HUD

	private static void overlays(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		if (NvgItem.isOn(p.getItemBySlot(EquipmentSlot.HEAD)) && mc.options.getCameraType().isFirstPerson()) {
			nightVision(g, w, h);
		}
		if (scoped()) {
			scope(g, w, h);
		}
	}

	private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		ItemStack stack = p.getMainHandItem();
		GunType gun = gunOf(stack);
		if (gun == null || p.isPassenger()) {
			return;
		}
		Font font = mc.font;
		int ammo = Math.max(0, GunItem.ammo(stack) - unconfirmed);
		boolean creative = p.getAbilities().instabuild;
		String reserve = creative ? "\u221e" : String.valueOf(GunServer.countAmmo(p, gun));
		// Bottom right, clear of the hotbar: the gun, rounds in the magazine, spare rounds.
		int pw = 104;
		int x0 = w - pw - 4;
		int y0 = h - 31;
		g.fill(x0, y0, w - 4, h - 4, 0x88000000);
		small(g, font, stack.getHoverName().getString(), x0 + 4, y0 + 3, 0xFFB8B8B8);
		g.text(font, ammo + " / " + gun.magazine, x0 + 4, y0 + 14, ammo == 0 ? 0xFFFF6A5A : 0xFFFFE08A);
		String spare = Component.translatable("hud.airdefense.gun.spare", reserve).getString();
		g.text(font, spare, w - 8 - font.width(spare), y0 + 14, 0xFFA8A8A8);
		float cd = p.getCooldowns().getCooldownPercent(stack, delta.getGameTimeDeltaPartialTick(true));
		if (cd > 0) {
			Component rl = Component.translatable("hud.airdefense.gun.reloading");
			g.fill(x0, y0 - 5, w - 4, y0 - 2, 0xFF303A44);
			g.fill(x0, y0 - 5, x0 + (int) (pw * (1 - cd)), y0 - 2, 0xFF8AE07A);
			g.text(font, rl, w - 4 - font.width(rl), y0 - 16, 0xFFE0E0E0);
		} else if (ammo == 0) {
			Component rl = Component.translatable("hud.airdefense.gun.empty", VehicleClient.DEPLOY.getTranslatedKeyMessage());
			g.text(font, rl, w - 4 - font.width(rl), y0 - 11, 0xFFFF8A7A);
		}
		if (hitMarker > 0) {
			int cx = w / 2;
			int cy = h / 2;
			int c = hitHead ? 0xFFFF5040 : 0xFFFFFFFF;
			for (int i = 3; i <= 6; i++) {
				g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, c);
				g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, c);
				g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, c);
				g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
			}
		}
	}

	/** PSO-1 style: everything black outside the round field of view, red chevrons and a stadia line inside. */
	private static void scope(GuiGraphicsExtractor g, int w, int h) {
		int cx = w / 2;
		int cy = h / 2;
		int r = (int) (Math.min(w, h) * 0.47);
		int black = 0xFF050505;
		for (int y = 0; y < h; y++) {
			int dy = y - cy;
			if (Math.abs(dy) >= r) {
				g.fill(0, y, w, y + 1, black);
				continue;
			}
			int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
			g.fill(0, y, cx - dx, y + 1, black);
			g.fill(cx + dx, y, w, y + 1, black);
			// Darker rim where the tube shades the picture.
			int rim = Math.max(1, r / 14);
			g.fill(cx - dx, y, Math.min(cx + dx, cx - dx + rim), y + 1, 0x90000000);
			g.fill(Math.max(cx - dx, cx + dx - rim), y, cx + dx, y + 1, 0x90000000);
		}
		int red = 0xFFC01818;
		int ink = 0xE0101010;
		// Main chevron (its tip is the aiming point) and the three below for longer ranges.
		chevron(g, cx, cy, 6, red);
		chevron(g, cx, cy + 14, 4, red);
		chevron(g, cx, cy + 24, 4, red);
		chevron(g, cx, cy + 33, 4, red);
		// Lateral stadia: a line with ticks to the left and right.
		g.fill(cx - r + 10, cy, cx - 12, cy + 1, ink);
		g.fill(cx + 12, cy, cx + r - 10, cy + 1, ink);
		for (int i = 1; i <= 10; i++) {
			int len = i % 5 == 0 ? 5 : 3;
			g.fill(cx - 12 - i * 8, cy - len, cx - 11 - i * 8, cy, ink);
			g.fill(cx + 11 + i * 8, cy - len, cx + 12 + i * 8, cy, ink);
		}
		// Rangefinder curve in the lower left.
		for (int i = 0; i < 40; i++) {
			int px = cx - 40 - i;
			int py = cy + 20 + (int) (Math.sqrt(i) * 3);
			g.fill(px, py, px + 1, py + 1, ink);
			g.fill(cx - 40 - i, cy + 34, cx - 39 - i, cy + 35, i % 4 == 0 ? ink : 0);
		}
	}

	private static void small(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	private static void chevron(GuiGraphicsExtractor g, int x, int y, int legs, int color) {
		for (int i = 0; i <= legs; i++) {
			g.fill(x - i, y + i, x - i + 1, y + i + 2, color);
			g.fill(x + i, y + i, x + i + 1, y + i + 2, color);
		}
	}

	/** Night vision goggles: a green picture in a round tube field of view. */
	private static void nightVision(GuiGraphicsExtractor g, int w, int h) {
		g.fill(0, 0, w, h, 0x3A20FF40);
		int cx = w / 2;
		int cy = h / 2;
		double rx = w * 0.5;
		double ry = h * 0.62;
		for (int y = 0; y < h; y++) {
			double dy = (y - cy) / ry;
			if (Math.abs(dy) >= 1) {
				g.fill(0, y, w, y + 1, 0xD0000000);
				continue;
			}
			int dx = (int) (Math.sqrt(1 - dy * dy) * rx);
			g.fill(0, y, cx - dx, y + 1, 0xD0000000);
			g.fill(cx + dx, y, w, y + 1, 0xD0000000);
		}
	}
}
