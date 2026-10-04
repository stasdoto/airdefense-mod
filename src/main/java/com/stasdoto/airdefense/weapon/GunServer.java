package com.stasdoto.airdefense.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.Effects;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModDamageTypes;
import com.stasdoto.airdefense.registry.ModItems;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * Server side of the small arms. The client says "I fired, in this direction"; the server checks the gun is in the
 * hand, loaded and ready (rate of fire, reloading) and that the direction is near where the player looks, traces the
 * shot through the world and deals the damage, then tells everyone around what to draw and hear.
 */
public final class GunServer {
	/** Debug counters read by the automated test. */
	public static final AtomicInteger SHOTS = new AtomicInteger();
	public static final AtomicInteger HITS = new AtomicInteger();
	public static final AtomicInteger HEADSHOTS = new AtomicInteger();
	public static final AtomicInteger RELOADS = new AtomicInteger();
	public static final AtomicInteger ROCKETS = new AtomicInteger();
	public static final AtomicInteger GLASS_BROKEN = new AtomicInteger();

	private static final Map<UUID, State> STATES = new HashMap<>();

	private static final class State {
		long nextShot;
		int round;
		int reloadLeft;
		int reloadTotal;
		int reloadSlot;
		Item reloadItem;
	}

	/** Where a shot ended: in an entity, in a block, or nowhere within range. */
	public record Trace(Vec3 pos, @Nullable Entity entity, @Nullable BlockHitResult block) {
	}

	private GunServer() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(GunActionPayload.TYPE, GunActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShotPayload.TYPE, ShotPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GunActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(GunServer::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
	}

	private static State state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	private static void handle(ServerPlayer player, GunActionPayload p) {
		switch (p.action()) {
			case GunActionPayload.FIRE -> fire(player, new Vec3(p.dx(), p.dy(), p.dz()));
			case GunActionPayload.RELOAD -> startReload(player);
			case GunActionPayload.NVG -> NvgItem.toggle(player);
			default -> {
			}
		}
	}

	/** True while this player is reloading (the client shows it from the item cooldown). */
	public static boolean reloading(Player player) {
		State st = STATES.get(player.getUUID());
		return st != null && st.reloadLeft > 0;
	}

	// ------------------------------------------------------------------------------------------------
	// Firing

	public static void fire(ServerPlayer player, Vec3 wanted) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem item) || !player.isAlive() || player.isSpectator() || player.isPassenger()) {
			return;
		}
		GunType gun = item.gun;
		ServerLevel level = player.level();
		State st = state(player);
		long now = level.getGameTime();
		// One tick of slack: packets do not arrive exactly on the tick they were sent.
		if (st.reloadLeft > 0 || player.getCooldowns().isOnCooldown(stack) || now + 1 < st.nextShot) {
			return;
		}
		int ammo = GunItem.ammo(stack);
		if (ammo <= 0) {
			sound(level, player, ModSounds.GUN_DRY, 0.7f);
			st.nextShot = now + 4;
			return;
		}
		st.nextShot = Math.max(now, st.nextShot) + gun.interval;
		GunItem.setAmmo(stack, ammo - 1);
		st.round++;
		SHOTS.incrementAndGet();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 dir = wanted.lengthSqr() > 0.25 ? wanted.normalize() : look;
		if (dir.dot(look) < Math.cos(Math.toRadians(gun.hipSpread * 4 + 10))) {
			dir = look;
		}
		Vec3 muzzle = muzzle(player, look);
		if (gun.rocket()) {
			MissileEntity.launchDirect(level, MissileType.RPG, eye.add(dir.scale(1.3)).add(0, -0.12, 0), dir, player);
			Effects.rpgBackblast(level, eye.subtract(dir.scale(1.4)).add(0, -0.2, 0), muzzle);
			ROCKETS.incrementAndGet();
			return;
		}
		Trace t = trace(level, player, eye, dir, gun.range);
		int hit = ShotPayload.HIT_NONE;
		if (t.entity() != null) {
			hit = damage(level, player, gun, t.entity(), t.pos(), eye.distanceTo(t.pos()));
		} else if (t.block() != null) {
			hit = ShotPayload.HIT_BLOCK;
			breakGlass(level, player, t.block().getBlockPos());
		}
		ShotPayload shot = new ShotPayload(player.getId(), gun.ordinal(), muzzle.x, muzzle.y, muzzle.z, t.pos().x, t.pos().y, t.pos().z,
				hit, st.round);
		double hear = gun == GunType.SVD ? 800 : gun == GunType.PM ? 300 : 600;
		for (ServerPlayer p : PlayerLookup.around(level, muzzle, hear)) {
			if (ServerPlayNetworking.canSend(p, ShotPayload.TYPE)) {
				ServerPlayNetworking.send(p, shot);
			}
		}
	}

	/** Where the rounds leave the barrel for those watching from outside (the shooter sees his own gun). */
	public static Vec3 muzzle(Player player, Vec3 look) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double side = player.getMainArm() == HumanoidArm.LEFT ? -0.22 : 0.22;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		return player.getEyePosition().add(look.scale(0.9)).add(right.scale(side)).add(0, -0.18, 0);
	}

	/** Follows a shot from {@code from} along {@code dir}: the first entity or block in the way (client and server). */
	public static Trace trace(Level level, Entity shooter, Vec3 from, Vec3 dir, double range) {
		Vec3 end = from.add(dir.scale(range));
		BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, shooter, from, stop, new AABB(from, stop).inflate(1.0),
				e -> e != shooter && e.isAlive() && !e.isSpectator() && e.isPickable() && e != shooter.getVehicle()
						&& !shooter.isPassengerOfSameVehicle(e), 0.0f);
		if (entity != null) {
			return new Trace(entity.getLocation(), entity.getEntity(), null);
		}
		if (block.getType() != HitResult.Type.MISS) {
			return new Trace(block.getLocation(), null, block);
		}
		return new Trace(end, null, null);
	}

	/**
	 * Deals one round's damage. A head is worth 1.8x (a helmet stops most of that), a body armour vest takes more than
	 * half of a hit in the chest; vehicles and missiles mostly shrug rifle rounds off.
	 */
	private static int damage(ServerLevel level, ServerPlayer shooter, GunType gun, Entity target, Vec3 at, double dist) {
		float dmg = gun.damageAt(dist);
		int hit;
		if (target instanceof MissileEntity) {
			dmg *= 0.25f;
			hit = ShotPayload.HIT_METAL;
		} else if (target instanceof VehicleEntity) {
			hit = ShotPayload.HIT_METAL;
		} else if (target instanceof LivingEntity living) {
			boolean head = living.getBbHeight() > 1.2f && at.y >= living.getEyeY() - 0.28;
			boolean chest = !head && at.y >= living.getY() + living.getBbHeight() * 0.42;
			if (head) {
				ItemStack helmet = living.getItemBySlot(EquipmentSlot.HEAD);
				boolean protectedHead = helmet.is(ModItems.HELMET) || helmet.is(ModItems.NVG_HELMET);
				dmg *= protectedHead ? 1.15f : 1.8f;
				if (protectedHead) {
					helmet.hurtAndBreak(2, living, EquipmentSlot.HEAD);
				}
				HEADSHOTS.incrementAndGet();
			} else if (chest && living.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.VEST)) {
				dmg *= 0.45f;
				living.getItemBySlot(EquipmentSlot.CHEST).hurtAndBreak(2, living, EquipmentSlot.CHEST);
			}
			hit = head ? ShotPayload.HIT_HEAD : ShotPayload.HIT_FLESH;
		} else {
			hit = ShotPayload.HIT_METAL;
		}
		if (target.hurtServer(level, ModDamageTypes.bullet(level, shooter), dmg)) {
			HITS.incrementAndGet();
		}
		return hit;
	}

	/** Glass shatters (tinted glass is bulletproof). Respects spawn protection and adventure mode. */
	private static void breakGlass(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if ((s.is(ConventionalBlockTags.GLASS_BLOCKS) || s.is(ConventionalBlockTags.GLASS_PANES)) && !s.is(ConventionalBlockTags.GLASS_BLOCKS_TINTED)
				&& player.mayBuild() && player.mayInteract(level, pos)) {
			level.destroyBlock(pos, false, player, 512);
			GLASS_BROKEN.incrementAndGet();
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Reloading: the rounds come out of the inventory (free in creative); switching away cancels it

	public static void startReload(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem item) || player.isSpectator() || !player.isAlive()) {
			return;
		}
		GunType gun = item.gun;
		State st = state(player);
		if (st.reloadLeft > 0 || GunItem.ammo(stack) >= gun.magazine) {
			return;
		}
		if (!player.getAbilities().instabuild && countAmmo(player, gun) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.gun.no_ammo", Component.translatable("item.airdefense." + gun.ammoId)));
			return;
		}
		st.reloadLeft = st.reloadTotal = gun.reload;
		st.reloadSlot = player.getInventory().getSelectedSlot();
		st.reloadItem = item;
		player.getCooldowns().addCooldown(stack, gun.reload);
		player.stopUsingItem();
		sound(player.level(), player, ModSounds.GUN_MAG_OUT, 0.8f);
		RELOADS.incrementAndGet();
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State st = STATES.get(player.getUUID());
			if (st == null || st.reloadLeft <= 0) {
				continue;
			}
			ItemStack stack = player.getMainHandItem();
			if (!player.isAlive() || player.getInventory().getSelectedSlot() != st.reloadSlot || stack.getItem() != st.reloadItem
					|| !(stack.getItem() instanceof GunItem item)) {
				st.reloadLeft = 0;
				player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(new ItemStack(st.reloadItem)));
				continue;
			}
			st.reloadLeft--;
			if (st.reloadLeft == st.reloadTotal * 35 / 100) {
				sound(player.level(), player, ModSounds.GUN_MAG_IN, 0.8f);
			}
			if (st.reloadLeft == 0) {
				GunType gun = item.gun;
				int need = gun.magazine - GunItem.ammo(stack);
				int got = player.getAbilities().instabuild ? need : takeAmmo(player, gun, need);
				GunItem.setAmmo(stack, GunItem.ammo(stack) + got);
				sound(player.level(), player, ModSounds.GUN_BOLT, 0.8f);
			}
		}
	}

	public static int countAmmo(Player player, GunType gun) {
		Item ammo = gun.ammo();
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(ammo)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static int takeAmmo(Player player, GunType gun, int want) {
		Item ammo = gun.ammo();
		int got = 0;
		for (int i = 0; i < player.getInventory().getContainerSize() && got < want; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(ammo)) {
				int k = Math.min(s.getCount(), want - got);
				s.shrink(k);
				got += k;
			}
		}
		return got;
	}

	private static void sound(Level level, Player player, SoundEvent sound, float volume) {
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume,
				0.95f + level.getRandom().nextFloat() * 0.1f);
	}
}
