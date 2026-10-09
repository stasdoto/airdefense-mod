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
	/** 1.24: rounds that hit a drone or a missile, and those brought down by rifle fire. */
	public static final AtomicInteger MISSILE_HITS = new AtomicInteger();
	public static final AtomicInteger MISSILES_DOWN = new AtomicInteger();
	public static final AtomicInteger HEADSHOTS = new AtomicInteger();
	public static final AtomicInteger RELOADS = new AtomicInteger();
	public static final AtomicInteger ROCKETS = new AtomicInteger();
	public static final AtomicInteger GLASS_BROKEN = new AtomicInteger();

	private static final Map<UUID, State> STATES = new HashMap<>();

	public static final AtomicInteger PELLETS = new AtomicInteger();
	public static final AtomicInteger GUIDED = new AtomicInteger();
	public static final AtomicInteger NO_LOCK = new AtomicInteger();
	public static final AtomicInteger TUBES_SPENT = new AtomicInteger();

	private static final class State {
		double nextShot;
		/** Game tick at which the bolt (or the pump) is worked after a shot (its sound), 0 = none. */
		long cycleAt;
		Item cycleItem;
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
			case GunActionPayload.FIRE -> fire(player, new Vec3(p.dx(), p.dy(), p.dz()), p.target());
			case GunActionPayload.RELOAD -> startReload(player);
			case GunActionPayload.NVG -> NvgItem.toggle(player);
			case GunActionPayload.GRENADE -> com.stasdoto.airdefense.gear.GearServer.quickGrenade(player);
			case GunActionPayload.MEDKIT -> com.stasdoto.airdefense.gear.GearServer.startDressing(player);
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
		fire(player, wanted, -1);
	}

	public static void fire(ServerPlayer player, Vec3 wanted, int lockId) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem item) || !player.isAlive() || player.isSpectator() || player.isPassenger()) {
			return;
		}
		GunType gun = item.gun;
		ServerLevel level = player.level();
		State st = state(player);
		long now = level.getGameTime();
		// One tick of slack: packets do not arrive exactly on the tick they were sent.
		if (st.reloadLeft > 0 || player.getCooldowns().isOnCooldown(stack) || now + 1 < st.nextShot
				|| com.stasdoto.airdefense.gear.GearServer.dressing(player)) {
			return;
		}
		int ammo = GunItem.ammo(stack);
		if (ammo <= 0) {
			sound(level, player, ModSounds.GUN_DRY, 0.7f);
			st.nextShot = now + 4;
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 dir = wanted.lengthSqr() > 0.25 ? wanted.normalize() : look;
		if (dir.dot(look) < Math.cos(Math.toRadians(gun.hipSpread * 4 + 10))) {
			dir = look;
		}
		Entity lock = null;
		if (gun.needsLock()) {
			// The Javelin flies only at a target its seeker has locked (on the client, two seconds in the sight).
			lock = lockId >= 0 ? level.getEntity(lockId) : null;
			if (lock == null || !lock.isAlive() || lock.distanceTo(player) > gun.range
					|| lock.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) < Math.cos(Math.toRadians(12))) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.gun.no_lock"));
				NO_LOCK.incrementAndGet();
				st.nextShot = now + 10;
				return;
			}
		}
		st.nextShot = Math.max(now, st.nextShot) + gun.interval;
		GunItem.setAmmo(stack, ammo - 1);
		st.round++;
		SHOTS.incrementAndGet();
		Vec3 muzzle = muzzle(player, look);
		if (gun.rocket()) {
			launch(level, player, gun, eye, dir, muzzle, lock);
			if (gun.disposable) {
				// A one-shot tube: thrown away (a creative player keeps a loaded one).
				TUBES_SPENT.incrementAndGet();
				if (player.getAbilities().instabuild) {
					GunItem.setAmmo(stack, 1);
				} else {
					stack.shrink(1);
				}
			}
			return;
		}
		shoot(level, player, gun, eye, dir, muzzle, st.round);
		if (gun.action == GunType.Action.BOLT || gun.action == GunType.Action.PUMP) {
			st.cycleAt = now + Math.max(4, (long) (gun.interval * 0.35));
			st.cycleItem = item;
		}
	}

	/** A launcher's round leaves the tube: a rocket, a guided missile, a 40 mm grenade; the back-blast behind. */
	private static void launch(ServerLevel level, ServerPlayer player, GunType gun, Vec3 eye, Vec3 dir, Vec3 muzzle, @Nullable Entity lock) {
		Vec3 from = eye.add(dir.scale(1.3)).add(0, -0.12, 0);
		MissileType type = gun.rocket;
		if (type == MissileType.G40) {
			MissileEntity.launchWithVelocity(level, type, from, dir.scale(type.maxSpeed), player, null);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GRENADE_LAUNCH, SoundSource.PLAYERS, 1.2f,
					0.95f + level.getRandom().nextFloat() * 0.1f);
			ROCKETS.incrementAndGet();
			return;
		}
		if (type.guided()) {
			MissileEntity.launchGuided(level, type, from, dir, player, lock);
			GUIDED.incrementAndGet();
		} else {
			MissileEntity.launchDirect(level, type, from, dir, player);
		}
		Effects.rpgBackblast(level, eye.subtract(dir.scale(1.4)).add(0, -0.2, 0), muzzle);
		ROCKETS.incrementAndGet();
	}

	/**
	 * One round from anyone - a player or a soldier: traced through the world, damage dealt, glass broken (players
	 * only), and everyone around told what to draw and hear. Returns what it hit ({@link ShotPayload} HIT_*).
	 */
	public static int shoot(ServerLevel level, LivingEntity shooter, GunType gun, Vec3 eye, Vec3 dir, Vec3 muzzle, int round) {
		if (gun.pellets <= 1) {
			return shootOne(level, shooter, gun, eye, dir, muzzle, round);
		}
		// A shotgun: a cloud of pellets round the aim (each its own trace); the best hit counts.
		int best = ShotPayload.HIT_NONE;
		float cone = shooter instanceof Player p && p.isUsingItem() ? gun.aimSpread : gun.hipSpread;
		for (int i = 0; i < gun.pellets; i++) {
			Vec3 d = cone(dir, cone * 0.8f, level.getRandom());
			int h = shootOne(level, shooter, gun, eye, d, muzzle, i == 0 ? round : -1);
			PELLETS.incrementAndGet();
			best = rank(h) > rank(best) ? h : best;
		}
		return best;
	}

	private static int rank(int hit) {
		return switch (hit) {
			case ShotPayload.HIT_HEAD -> 4;
			case ShotPayload.HIT_FLESH -> 3;
			case ShotPayload.HIT_METAL -> 2;
			case ShotPayload.HIT_BLOCK -> 1;
			default -> 0;
		};
	}

	/** A random direction within {@code degrees} (a cone, denser in the middle) around {@code look}. */
	public static Vec3 cone(Vec3 look, float degrees, net.minecraft.util.RandomSource r) {
		if (degrees <= 0) {
			return look;
		}
		Vec3 up = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = look.cross(up).normalize();
		Vec3 b = look.cross(a).normalize();
		double rad = Math.toRadians(degrees) * 0.5;
		return look.add(a.scale(r.nextGaussian() * rad * 0.6)).add(b.scale(r.nextGaussian() * rad * 0.6)).normalize();
	}

	private static int shootOne(ServerLevel level, LivingEntity shooter, GunType gun, Vec3 eye, Vec3 dir, Vec3 muzzle, int round) {
		Trace t = trace(level, shooter, eye, dir, gun.range);
		int hit = ShotPayload.HIT_NONE;
		if (t.entity() != null) {
			hit = damage(level, shooter, gun, t.entity(), t.pos(), eye.distanceTo(t.pos()));
		} else if (t.block() != null) {
			hit = ShotPayload.HIT_BLOCK;
			if (shooter instanceof ServerPlayer player) {
				breakGlass(level, player, t.block().getBlockPos());
			}
		}
		ShotPayload shot = new ShotPayload(shooter.getId(), gun.ordinal(), muzzle.x, muzzle.y, muzzle.z, t.pos().x, t.pos().y, t.pos().z,
				hit, round);
		for (ServerPlayer p : PlayerLookup.around(level, muzzle, gun.hearing())) {
			if (ServerPlayNetworking.canSend(p, ShotPayload.TYPE)) {
				ServerPlayNetworking.send(p, shot);
			}
		}
		return hit;
	}

	/** Where the rounds leave the barrel for those watching from outside (the shooter sees his own gun). */
	public static Vec3 muzzle(LivingEntity shooter, Vec3 look) {
		double side = shooter.getMainArm() == HumanoidArm.LEFT ? -0.22 : 0.22;
		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 right = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : new Vec3(-flat.z, 0, flat.x).normalize();
		return shooter.getEyePosition().add(look.scale(0.9)).add(right.scale(side)).add(0, -0.18, 0);
	}

	/** Follows a shot from {@code from} along {@code dir}: the first entity or block in the way (client and server). */
	public static Trace trace(Level level, Entity shooter, Vec3 from, Vec3 dir, double range) {
		Vec3 end = from.add(dir.scale(range));
		BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, shooter, from, stop, new AABB(from, stop).inflate(1.0),
				e -> e != shooter && e.isAlive() && !e.isSpectator() && e.isPickable() && e != shooter.getVehicle()
						&& !shooter.isPassengerOfSameVehicle(e), 0.0f);
		// Drones and cruise missiles are bigger than their entity box (a Shahed is 2.5 m across the wings).
		double best = entity != null ? from.distanceToSqr(entity.getLocation()) : from.distanceToSqr(stop);
		MissileEntity drone = null;
		Vec3 droneAt = null;
		for (MissileEntity m : level.getEntitiesOfClass(MissileEntity.class, new AABB(from, stop).inflate(1.5), MissileEntity::isPickable)) {
			double grow = m.getMissileType().hitGrow();
			if (grow <= 0) {
				continue;
			}
			var at = m.getBoundingBox().inflate(grow).clip(from, stop);
			if (at.isPresent() && from.distanceToSqr(at.get()) < best) {
				best = from.distanceToSqr(at.get());
				drone = m;
				droneAt = at.get();
			}
		}
		if (drone != null) {
			return new Trace(droneAt, drone, null);
		}
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
	private static int damage(ServerLevel level, LivingEntity shooter, GunType gun, Entity target, Vec3 at, double dist) {
		float dmg = gun.damageAt(dist);
		int hit;
		if (target instanceof MissileEntity m) {
			dmg *= 0.25f;
			hit = ShotPayload.HIT_METAL;
			if (target.hurtServer(level, ModDamageTypes.bullet(level, shooter), dmg)) {
				HITS.incrementAndGet();
				MISSILE_HITS.incrementAndGet();
				if (m.isRemoved() || !m.isAlive() || m.hasDetonated()) {
					MISSILES_DOWN.incrementAndGet();
					com.stasdoto.airdefense.missile.MissileStats.log("{} shot down by {} fire", m.getMissileType(), gun);
				}
			}
			return hit;
		} else if (target instanceof VehicleEntity) {
			hit = ShotPayload.HIT_METAL;
			if (gun.antiMateriel) {
				// A 12.7 mm round goes through thin armour: counted as a heavy projectile, not a rifle bullet.
				if (target.hurtServer(level, level.damageSources().thrown(shooter, shooter), dmg * 1.5f)) {
					HITS.incrementAndGet();
				}
				return hit;
			}
		} else if (target instanceof LivingEntity living) {
			boolean head = living.getBbHeight() > 1.2f && at.y >= living.getEyeY() - 0.28;
			boolean chest = !head && at.y >= living.getY() + living.getBbHeight() * 0.42;
			if (head) {
				ItemStack helmet = living.getItemBySlot(EquipmentSlot.HEAD);
				boolean protectedHead = helmet.is(ModItems.HELMET) || helmet.is(ModItems.NVG_HELMET) || helmet.is(ModItems.HELMET_FAST);
				dmg *= protectedHead ? 1.15f : 1.8f;
				if (protectedHead) {
					helmet.hurtAndBreak(2, living, EquipmentSlot.HEAD);
				}
				HEADSHOTS.incrementAndGet();
			} else if (chest && living.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.stasdoto.airdefense.gear.VestItem vest) {
				dmg *= vest.bulletFactor;
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
		if (gun.disposable || st.reloadLeft > 0 || GunItem.ammo(stack) >= gun.magazine) {
			return;
		}
		if (!player.getAbilities().instabuild && countAmmo(player, gun) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.gun.no_ammo", Component.translatable(gun.ammoKey())));
			return;
		}
		// 1.27: magazines in pouches on the vest come out quicker.
		st.reloadLeft = st.reloadTotal = Math.max(8, Math.round(gun.reload * com.stasdoto.airdefense.gear.GearServer.reloadFactor(player)));
		st.reloadSlot = player.getInventory().getSelectedSlot();
		st.reloadItem = item;
		player.getCooldowns().addCooldown(stack, st.reloadTotal);
		player.stopUsingItem();
		sound(player.level(), player, ModSounds.GUN_MAG_OUT, 0.8f);
		RELOADS.incrementAndGet();
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State st = STATES.get(player.getUUID());
			if (st == null) {
				continue;
			}
			if (st.cycleAt > 0 && player.level().getGameTime() >= st.cycleAt) {
				// Working the bolt / racking the pump after a shot.
				st.cycleAt = 0;
				if (player.getMainHandItem().getItem() == st.cycleItem && st.cycleItem instanceof GunItem gi) {
					sound(player.level(), player, gi.gun.action == GunType.Action.PUMP ? ModSounds.GUN_PUMP : ModSounds.GUN_BOLT, 0.8f);
				}
			}
			if (st.reloadLeft <= 0) {
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
		if (ammo == null) {
			return 0;
		}
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
		if (ammo == null) {
			return 0;
		}
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
