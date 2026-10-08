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
import net.minecraft.world.phys.AABB;
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
import com.stasdoto.airdefense.weapon.GunModels;
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

	private static float cooldown;
	/** Rounds still to go in the current burst (M16A4: three a pull). */
	private static int burstLeft;
	/** Javelin seeker: the vehicle in the sight, how long it has been held there, whether it is locked. */
	private static int lockCandidate = -1;
	private static int lockTicks;
	private static boolean locked;
	public static final int LOCK_TICKS = 40;
	/** Debug counters read by the automated test. */
	public static int locksMade;
	public static int burstsFired;
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
			if (!scoped() && !dotSight()) {
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

	/** Looking through a scope (first person): its picture covers the screen. */
	public static boolean scoped() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || !aiming(mc.player)) {
			return false;
		}
		GunType g = gunOf(mc.player.getUseItem());
		return g != null && g.scoped() && zoom < Math.min(0.95f, g.aimZoom + (1 - g.aimZoom) * 0.35f);
	}

	/** Aimed through a collimator: its reticle is drawn in the middle instead of the crosshair. */
	public static boolean dotSight() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || !aiming(mc.player)) {
			return false;
		}
		GunType g = gunOf(mc.player.getUseItem());
		return g != null && g.dot != GunType.Dot.NONE && zoom < g.aimZoom + (1 - g.aimZoom) * 0.3f;
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
			cooldown = Math.max(0, cooldown - 1);
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
			burstLeft = 0;
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
			// In a vehicle's sight the same key works its thermal channel (GunnerSight), not the helmet's goggles.
			if (com.stasdoto.airdefense.client.vehicle.GunnerSight.active() == com.stasdoto.airdefense.client.vehicle.GunnerSight.Kind.NONE) {
				send(GunActionPayload.NVG, Vec3.ZERO, -1);
			}
		}
		seeker(mc, p, gun);
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
			if (!reloading && ammo < gun.magazine && !gun.disposable) {
				send(GunActionPayload.RELOAD, Vec3.ZERO, -1);
			}
		}
		if (mc.gui.screen() != null || p.isSpectator() || !p.isAlive() || p.isPassenger()) {
			burstLeft = 0;
			return;
		}
		if (click && gun.action == GunType.Action.BURST && burstLeft == 0 && cooldown <= 0) {
			burstLeft = gun.burst;
			burstsFired++;
		}
		boolean want = switch (gun.action) {
			case AUTO -> click || mc.mouseHandler.isLeftPressed();
			case BURST -> burstLeft > 0;
			default -> click;
		};
		if (!want || reloading || cooldown > 0) {
			return;
		}
		if (ammo - unconfirmed > 0) {
			if (gun.needsLock() && !locked) {
				// No lock, no launch: the seeker must hold the target first.
				cooldown = 10;
				p.sendOverlayMessage(Component.translatable("message.airdefense.gun.no_lock"));
				return;
			}
			fire(mc, p, gun);
			if (burstLeft > 0) {
				burstLeft--;
			}
		} else {
			burstLeft = 0;
			// Empty: a dry click, and the reload starts by itself (if there is anything to load).
			cooldown = 6;
			if (dryClicks++ % 2 == 0) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GUN_DRY, 1.0f, 0.6f));
			}
			if (!gun.disposable && (p.getAbilities().instabuild || GunServer.countAmmo(p, gun) > 0)) {
				send(GunActionPayload.RELOAD, Vec3.ZERO, -1);
			}
		}
	}

	/**
	 * The Javelin's seeker: while aiming through the CLU, the vehicle nearest the middle of the sight (within a few
	 * degrees) is tracked; held for two seconds it is locked (a steady tone), lost if it leaves the sight.
	 */
	private static void seeker(Minecraft mc, LocalPlayer p, GunType gun) {
		if (gun == null || !gun.needsLock() || !aiming(p) || zoom > gun.aimZoom + 0.15f) {
			lockCandidate = -1;
			lockTicks = 0;
			locked = false;
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		VehicleEntity best = null;
		double bestCos = Math.cos(Math.toRadians(locked ? 6 : 3));
		AABB box = new AABB(eye, eye.add(look.scale(gun.range))).inflate(20);
		for (VehicleEntity v : p.level().getEntitiesOfClass(VehicleEntity.class, box, v -> v.isAlive() && v.getVehicle() == null)) {
			if (v == p.getVehicle()) {
				continue;
			}
			Vec3 to = v.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d > gun.range || d < 6) {
				continue;
			}
			double c = to.scale(1 / d).dot(look);
			if (c > bestCos) {
				bestCos = c;
				best = v;
			}
		}
		if (best == null) {
			if (locked || lockTicks > 0) {
				lockTicks = 0;
				locked = false;
			}
			lockCandidate = -1;
			return;
		}
		if (best.getId() != lockCandidate) {
			lockCandidate = best.getId();
			lockTicks = 0;
			locked = false;
		}
		lockTicks++;
		if (!locked && lockTicks % 8 == 1) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.JAVELIN_SEEK, 1.0f, 0.5f));
		}
		if (!locked && lockTicks >= LOCK_TICKS) {
			locked = true;
			locksMade++;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.JAVELIN_LOCK, 1.0f, 0.6f));
		}
	}

	/** The Javelin's seeker state for the HUD: -1 nothing, 0..1 acquiring, 2 locked. */
	public static float lockState() {
		return locked ? 2 : lockCandidate < 0 ? -1 : Math.min(1f, lockTicks / (float) LOCK_TICKS);
	}

	private static void fire(Minecraft mc, LocalPlayer p, GunType gun) {
		cooldown += gun.interval;
		unconfirmed++;
		round++;
		RandomSource r = p.getRandom();
		boolean aim = aiming(p);
		float spread = aim ? gun.aimSpread : gun.hipSpread;
		if (gun.pellets > 1) {
			// The pellets spread by themselves; the shooter's own shake moves the whole cloud a little.
			spread = aim ? 0.5f : 1.2f;
		}
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
		boolean fast = gun.auto || gun.action == GunType.Action.BURST;
		bloom = Math.min(fast ? 3f : 1.5f, bloom + (fast ? 0.25f : 0.6f) * (aim ? 0.6f : 1f));
		Vec3 look = p.getLookAngle();
		Vec3 dir = cone(look, spread, r);
		send(GunActionPayload.FIRE, dir, gun.needsLock() ? lockCandidate : -1);
		shotsSent++;
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(p.getMainArm() == HumanoidArm.LEFT ? -1 : 1);
		Vec3 muzzle = firstPerson ? viewMuzzle(mc, aim, gun) : GunServer.muzzle(p, look);
		if (!gun.rocket()) {
			int n = Math.max(1, gun.pellets);
			for (int i = 0; i < n; i++) {
				Vec3 d = n > 1 ? GunServer.cone(dir, (aim ? gun.aimSpread : gun.hipSpread) * 0.8f, r) : dir;
				GunServer.Trace t = GunServer.trace(p.level(), p, p.getEyePosition(), d, gun.range);
				int hit = t.entity() == null ? (t.block() != null ? ShotPayload.HIT_BLOCK : ShotPayload.HIT_NONE)
						: t.entity() instanceof LivingEntity && !(t.entity() instanceof VehicleEntity) ? ShotPayload.HIT_FLESH : ShotPayload.HIT_METAL;
				ShotFx.shot(mc, gun, muzzle, t.pos(), hit, round, i == 0 ? right : null, firstPerson);
			}
			SquadAudio.play(muzzle, SquadAudio.of(gun.report), 1f);
		}
		// Recoil: the muzzle climbs and wanders a little (less when aiming or crouching).
		float kick = gun.recoil * (aim ? 0.65f : 1f) * (p.isCrouching() ? 0.8f : 1f);
		p.setXRot(Mth.clamp(p.getXRot() - kick * (0.8f + r.nextFloat() * 0.4f), -90f, 90f));
		p.setYRot(p.getYRot() + (r.nextFloat() - 0.5f) * kick * 0.6f);
		CameraShake.add(switch (gun.report) {
			case SNIPER, ROCKET, SHOTGUN -> 0.3f;
			case HEAVY -> 0.55f;
			case GRENADE -> 0.2f;
			case SUPPRESSED, PISTOL -> 0.03f;
			default -> 0.05f;
		});
		if (gun.needsLock()) {
			locked = false;
			lockTicks = 0;
		}
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

	/** Where the barrel ends on screen in first person (from the models: tools/guns/build.py writes GunModels). */
	private static Vec3 viewMuzzle(Minecraft mc, boolean aim, GunType gun) {
		Camera cam = mc.gameRenderer.mainCamera();
		Vector3fc f = cam.forwardVector();
		Vector3fc u = cam.upVector();
		Vector3fc l = cam.leftVector();
		double[] v = GunModels.view(gun.id, aim);
		double side = mc.player.getMainArm() == HumanoidArm.LEFT ? -v[0] : v[0];
		double up = v[1];
		double fwd = v[2];
		return cam.position().add(f.x() * fwd, f.y() * fwd, f.z() * fwd).add(-l.x() * side, -l.y() * side, -l.z() * side)
				.add(u.x() * up, u.y() * up, u.z() * up);
	}

	private static void send(int action, Vec3 dir, int target) {
		if (ClientPlayNetworking.canSend(GunActionPayload.TYPE)) {
			ClientPlayNetworking.send(new GunActionPayload(action, (float) dir.x, (float) dir.y, (float) dir.z, target));
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
		if (mc.level.getEntity(s.shooter()) instanceof LivingEntity shooter) {
			float yaw = shooter.getYHeadRot() * Mth.DEG_TO_RAD;
			right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		}
		ShotFx.shot(mc, gun, from, to, s.hit(), Math.max(0, s.round()), s.round() < 0 ? null : right);
		if (s.round() >= 0) {
			SquadAudio.play(from, SquadAudio.of(gun.report), 1f);
		}
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
			GunType gun = gunOf(p.getUseItem());
			switch (gun == null ? GunType.Scope.PSO : gun.scope) {
				case MILDOT -> mildot(g, w, h);
				case ACOG -> acog(g, w, h);
				case JAVELIN -> clu(g, mc, w, h, delta);
				case PGO -> pgo7(g, mc, w, h);
				case LAUNCHER -> launcherSight(g, w, h);
				default -> scope(g, w, h);
			}
		} else if (dotSight()) {
			GunType gun = gunOf(p.getUseItem());
			collimator(g, w, h, gun != null && gun.dot == GunType.Dot.HOLO);
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
		String reserve = gun.disposable ? "-" : creative ? "\u221e" : String.valueOf(GunServer.countAmmo(p, gun));
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

	/** Shared by the round scopes: everything outside a circle of radius r black, a darker rim inside it. */
	private static void tube(GuiGraphicsExtractor g, int w, int h, int r) {
		int cx = w / 2;
		int cy = h / 2;
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
			int rim = Math.max(1, r / 14);
			g.fill(cx - dx, y, Math.min(cx + dx, cx - dx + rim), y + 1, 0x90000000);
			g.fill(Math.max(cx - dx, cx + dx - rim), y, cx + dx, y + 1, 0x90000000);
		}
	}

	/** A Western sniper scope: thin crosshairs with mil dots, thick posts towards the edge. */
	private static void mildot(GuiGraphicsExtractor g, int w, int h) {
		int cx = w / 2;
		int cy = h / 2;
		int r = (int) (Math.min(w, h) * 0.47);
		tube(g, w, h, r);
		int ink = 0xF0080808;
		g.fill(cx - r, cy, cx + r, cy + 1, ink);
		g.fill(cx, cy - r, cx + 1, cy + r, ink);
		int post = r * 55 / 100;
		g.fill(cx - r, cy - 1, cx - post, cy + 2, ink);
		g.fill(cx + post, cy - 1, cx + r, cy + 2, ink);
		g.fill(cx - 1, cy + post, cx + 2, cy + r, ink);
		g.fill(cx - 1, cy - r, cx + 2, cy - post, ink);
		int step = Math.max(6, r / 10);
		for (int i = 1; i <= 4; i++) {
			int d = i * step;
			g.fill(cx - d - 1, cy - 1, cx - d + 1, cy + 1, ink);
			g.fill(cx + d - 1, cy - 1, cx + d + 1, cy + 1, ink);
			g.fill(cx - 1, cy - d - 1, cx + 1, cy - d + 1, ink);
			g.fill(cx - 1, cy + d - 1, cx + 1, cy + d + 1, ink);
		}
	}

	/** The ACOG: a smaller field of view in a thick housing, a red chevron and the bullet-drop marks under it. */
	private static void acog(GuiGraphicsExtractor g, int w, int h) {
		int cx = w / 2;
		int cy = h / 2;
		int r = (int) (Math.min(w, h) * 0.36);
		tube(g, w, h, r);
		int red = 0xFFE02A1E;
		chevron(g, cx, cy, 5, red);
		g.fill(cx, cy + 7, cx + 1, cy + r * 7 / 10, 0xE0101010);
		int[] drops = {14, 22, 30, 37};
		for (int i = 0; i < drops.length; i++) {
			int half = 7 - i;
			g.fill(cx - half, cy + drops[i], cx + half + 1, cy + drops[i] + 1, 0xE0101010);
		}
		g.fill(cx - r, cy, cx - 12, cy + 1, 0xC0101010);
		g.fill(cx + 12, cy, cx + r, cy + 1, 0xC0101010);
	}

	/** The RPG-7's PGO-7: the aiming chevrons for 200-500 m down the middle, the lead scale, the range finder. */
	private static void pgo7(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
		int cx = w / 2;
		int cy = h / 2;
		int r = (int) (Math.min(w, h) * 0.4);
		tube(g, w, h, r);
		int ink = 0xF0101010;
		Font font = mc.font;
		// The main chevron is the 200 m mark; 3, 4, 5 hundred below it.
		for (int i = 0; i < 4; i++) {
			int y = cy + i * 13;
			for (int k = 0; k <= 4; k++) {
				g.fill(cx - k, y + k, cx - k + 1, y + k + 1, ink);
				g.fill(cx + k, y + k, cx + k + 1, y + k + 1, ink);
			}
			if (i > 0) {
				small(g, font, String.valueOf(i + 2), cx + 7, y, ink);
			}
		}
		// Lead marks either side of each chevron.
		for (int i = 1; i <= 4; i++) {
			g.fill(cx - i * 10, cy + 2, cx - i * 10 + 1, cy + 5, ink);
			g.fill(cx + i * 10, cy + 2, cx + i * 10 + 1, cy + 5, ink);
		}
		g.fill(cx - 44, cy + 3, cx - 6, cy + 4, ink);
		g.fill(cx + 6, cy + 3, cx + 44, cy + 4, ink);
		// Range finder: a tank 2.7 m tall fits between the base line and the curve at its range.
		for (int i = 0; i < 40; i++) {
			int px = cx - r + 18 + i;
			int py = cy + r / 2 - (int) (18 * Math.pow(1 - i / 40.0, 1.6));
			g.fill(px, py, px + 1, py + 1, ink);
		}
		g.fill(cx - r + 18, cy + r / 2 + 1, cx - r + 58, cy + r / 2 + 2, ink);
	}

	/** A launcher's optic (Carl Gustaf, NLAW): a fine cross, a centre ring, range bars under it. */
	private static void launcherSight(GuiGraphicsExtractor g, int w, int h) {
		int cx = w / 2;
		int cy = h / 2;
		int r = (int) (Math.min(w, h) * 0.42);
		tube(g, w, h, r);
		int ink = 0xE0101010;
		g.fill(cx - r, cy, cx - 8, cy + 1, ink);
		g.fill(cx + 8, cy, cx + r, cy + 1, ink);
		g.fill(cx, cy - r, cx + 1, cy - 8, ink);
		for (int a = 0; a < 48; a++) {
			double t = a * Math.PI * 2 / 48;
			g.fill(cx + (int) Math.round(Math.cos(t) * 5), cy + (int) Math.round(Math.sin(t) * 5),
					cx + (int) Math.round(Math.cos(t) * 5) + 1, cy + (int) Math.round(Math.sin(t) * 5) + 1, ink);
		}
		for (int i = 1; i <= 5; i++) {
			int y = cy + 8 + i * 9;
			int half = 9 - i;
			g.fill(cx - half, y, cx + half + 1, y + 1, ink);
		}
		g.fill(cx, cy + 8, cx + 1, cy + 60, ink);
	}

	/** The Javelin's Command Launch Unit: a grey-green thermal picture, the seeker's track gates round the target. */
	private static void clu(GuiGraphicsExtractor g, Minecraft mc, int w, int h, DeltaTracker delta) {
		g.fill(0, 0, w, h, 0x5A28402C);
		int cx = w / 2;
		int cy = h / 2;
		int bw = (int) (w * 0.36);
		int bh = (int) (h * 0.36);
		int black = 0xFF060806;
		// The sight's rectangular window.
		g.fill(0, 0, w, cy - bh, black);
		g.fill(0, cy + bh, w, h, black);
		g.fill(0, cy - bh, cx - bw, cy + bh, black);
		g.fill(cx + bw, cy - bh, w, cy + bh, black);
		int ink = 0xFFB8E8B0;
		g.fill(cx - bw + 6, cy, cx - 10, cy + 1, ink);
		g.fill(cx + 10, cy, cx + bw - 6, cy + 1, ink);
		g.fill(cx, cy - bh + 6, cx + 1, cy - 10, ink);
		g.fill(cx, cy + 10, cx + 1, cy + bh - 6, ink);
		Font font = mc.font;
		float st = lockState();
		String mode = st >= 2 ? "LOCK" : st >= 0 ? "SEEK" : "NFOV";
		g.text(font, "TOP", cx - bw + 6, cy - bh + 5, ink);
		g.text(font, mode, cx + bw - 6 - font.width(mode), cy - bh + 5, st >= 2 ? 0xFFFFE070 : ink);
		Component hint = Component.translatable(st >= 2 ? "hud.airdefense.gun.locked" : st >= 0 ? "hud.airdefense.gun.locking" : "hud.airdefense.gun.seek");
		g.text(font, hint, cx - font.width(hint) / 2, cy + bh - 14, st >= 2 ? 0xFFFFE070 : ink);
		// Track gates: four corner brackets round the tracked vehicle, closing in as the lock builds.
		if (st >= 0 && lockCandidate >= 0 && mc.level != null && mc.level.getEntity(lockCandidate) instanceof VehicleEntity v) {
			int[] sc = toScreen(mc, v.getBoundingBox().getCenter(), w, h, delta.getGameTimeDeltaPartialTick(true));
			if (sc != null) {
				int size = (int) (8 + (1 - Math.min(1, st)) * 22);
				int c = st >= 2 ? 0xFFFFE070 : ink;
				bracket(g, sc[0] - size, sc[1] - size, 1, 1, c);
				bracket(g, sc[0] + size, sc[1] - size, -1, 1, c);
				bracket(g, sc[0] - size, sc[1] + size, 1, -1, c);
				bracket(g, sc[0] + size, sc[1] + size, -1, -1, c);
			}
		}
	}

	private static void bracket(GuiGraphicsExtractor g, int x, int y, int dx, int dy, int c) {
		g.fill(Math.min(x, x + dx * 6), y, Math.max(x, x + dx * 6) + 1, y + 1, c);
		g.fill(x, Math.min(y, y + dy * 6), x + 1, Math.max(y, y + dy * 6) + 1, c);
	}

	/** A world point on the GUI (null if behind the camera). */
	private static int[] toScreen(Minecraft mc, Vec3 p, int w, int h, float partial) {
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 d = p.subtract(cam.position());
		Vector3fc f = cam.forwardVector();
		Vector3fc u = cam.upVector();
		Vector3fc l = cam.leftVector();
		double z = d.x * f.x() + d.y * f.y() + d.z * f.z();
		if (z < 0.1) {
			return null;
		}
		double x = -(d.x * l.x() + d.y * l.y() + d.z * l.z());
		double y = d.x * u.x() + d.y * u.y() + d.z * u.z();
		double fov = Math.toRadians(mc.options.fov().get() * zoom);
		double k = (h / 2.0) / Math.tan(fov / 2);
		return new int[]{(int) (w / 2 + x / z * k), (int) (h / 2 - y / z * k)};
	}

	/** A red dot (or a holographic ring with a dot) in the middle while aiming through a collimator. */
	private static void collimator(GuiGraphicsExtractor g, int w, int h, boolean holo) {
		int cx = w / 2;
		int cy = h / 2;
		int red = 0xFFFF3020;
		g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0x40FF3020);
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, red);
		if (holo) {
			int r = 9;
			for (int a = 0; a < 64; a++) {
				double t = a * Math.PI * 2 / 64;
				int x = cx + (int) Math.round(Math.cos(t) * r);
				int y = cy + (int) Math.round(Math.sin(t) * r);
				g.fill(x, y, x + 1, y + 1, 0xD0FF3020);
			}
			g.fill(cx - r - 3, cy, cx - r - 1, cy + 1, red);
			g.fill(cx + r + 2, cy, cx + r + 4, cy + 1, red);
			g.fill(cx, cy + r + 2, cx + 1, cy + r + 4, red);
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
