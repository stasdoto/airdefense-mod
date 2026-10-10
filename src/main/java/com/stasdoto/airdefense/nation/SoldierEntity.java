package com.stasdoto.airdefense.nation;

import java.util.EnumSet;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModItems;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.weapon.GunItem;
import com.stasdoto.airdefense.weapon.GunServer;
import com.stasdoto.airdefense.weapon.GunType;

/**
 * A person with a gun: a village guard, a soldier called up from a village, or a bandit. Guards stay by their
 * village's flag, soldiers go where their country's owner sends them (tablet map), bandits raid. They shoot with the
 * same guns as players (and the same hits and sounds), in short bursts, and reload. Their look - skin tone, face,
 * hair - comes from {@link #look()}; a soldier keeps the face he had as a villager.
 */
public class SoldierEntity extends PathfinderMob {
	public static final int GUARD = 0;
	public static final int SOLDIER = 1;
	public static final int BANDIT = 2;
	/** A villager who rose against the village's ruler ({@link #country} is the country he rebels against). */
	public static final int REBEL = 3;

	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);

	private int country = -1;
	private int home = -1;
	@Nullable
	private BlockPos order;
	/** What he was before he was called up: goes home as this villager. */
	@Nullable
	private VillagerData origin;
	@Nullable
	private UUID originId;
	/** The whole villager he was (trades, experience, gossip, homes), for going home as him. */
	@Nullable
	private net.minecraft.nbt.CompoundTag villagerTag;
	/** 1.29: the commander he follows (not kept over a restart), and whether he is his squad's medic. */
	@Nullable
	private UUID follow;
	private boolean medic;
	/** When he last called for help on the radio. */
	private long radioAt = -10000;
	public static int healedOthers;
	public static int radioCalls;
	public static int flanked;
	private int ammo = -1;
	private int reload;
	private int cooldown;
	private int burst;
	private int round;

	public SoldierEntity(EntityType<? extends SoldierEntity> type, Level level) {
		super(type, level);
		setDropChance(EquipmentSlot.MAINHAND, 0);
		setDropChance(EquipmentSlot.HEAD, 0);
		setDropChance(EquipmentSlot.CHEST, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 24)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.FOLLOW_RANGE, 64)
				.add(Attributes.ATTACK_DAMAGE, 3);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, GUARD);
		builder.define(DATA_COLOR, -1);
		builder.define(DATA_LOOK, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		// 1.29: a medic runs to a wounded comrade even in a fight; a soldier told to follow keeps near his commander.
		goalSelector.addGoal(0, new MedicGoal(this));
		goalSelector.addGoal(1, new ShootGoal(this));
		goalSelector.addGoal(2, new FollowGoal(this));
		goalSelector.addGoal(3, new OrderGoal(this));
		goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, (e, level) -> isEnemy(e)));
	}

	// ------------------------------------------------------------------------------------------------
	// Who he is

	/** A new guard, soldier or bandit, armed and dressed for the part. */
	public static SoldierEntity create(ServerLevel level, int role, int country, int color, int home, Vec3 at, int look) {
		SoldierEntity s = new SoldierEntity(com.stasdoto.airdefense.registry.ModEntities.SOLDIER, level);
		s.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0);
		s.entityData.set(DATA_ROLE, role);
		s.entityData.set(DATA_COLOR, color);
		s.entityData.set(DATA_LOOK, look);
		s.country = country;
		s.home = home;
		s.equip(level.getRandom().nextInt(100));
		if (role != BANDIT) {
			s.setPersistenceRequired();
		}
		s.setCustomName(s.title());
		s.setCustomNameVisible(false);
		return s;
	}

	/** Countries with an even id arm themselves from the eastern arsenal, odd ones from the western (1.24). */
	public static GunType.Bloc bloc(int country) {
		return Math.floorMod(country, 2) == 0 ? GunType.Bloc.EAST : GunType.Bloc.WEST;
	}

	private static Item pick(int roll, GunType... pool) {
		return ModItems.GUNS.get(pool[Math.floorMod(roll, pool.length)]);
	}

	private void equip(int roll) {
		int role = role();
		Item gun;
		if (role == BANDIT) {
			gun = roll < 55 ? pick(roll, GunType.AKM, GunType.AKS74U, GunType.AK74, GunType.SAIGA12, GunType.M870)
					: pick(roll, GunType.PM, GunType.GLOCK17, GunType.FORT12);
		} else if (role == REBEL) {
			gun = roll < 45 ? pick(roll, GunType.AKM, GunType.AK74, GunType.MP5) : pick(roll, GunType.PM, GunType.FORT12);
		} else {
			boolean east = bloc(country) == GunType.Bloc.EAST;
			if (roll < 62) {
				gun = east ? pick(roll, GunType.AK74, GunType.AK74, GunType.AK12, GunType.AKM, GunType.FORT221)
						: pick(roll, GunType.M4A1, GunType.M4A1, GunType.HK416, GunType.SCARH, GunType.M16A4);
			} else if (roll < 74) {
				gun = east ? pick(roll, GunType.RPK74, GunType.PKM, GunType.PKP) : pick(roll, GunType.M249, GunType.M240B);
			} else if (roll < 86) {
				gun = east ? pick(roll, GunType.SVD, GunType.SV98, GunType.VSS) : pick(roll, GunType.M110, GunType.AWM);
			} else {
				gun = east ? pick(roll, GunType.AKS74U, GunType.ASVAL, GunType.SAIGA12) : pick(roll, GunType.MP5, GunType.M870, GunType.HK416);
			}
			gear(east, roll, gun);
		}
		setItemSlot(EquipmentSlot.MAINHAND, GunItem.loaded(gun));
	}

	/**
	 * 1.27: the eastern armies in the 6B47 helmet and the heavy 6B45 vest (digital), the western ones in the FAST (now and
	 * then with night goggles) and a plate carrier; pouches by the weapon: magazines, a first-aid kit, grenades, a radio.
	 */
	private void gear(boolean east, int roll, Item gun) {
		int r = random.nextInt(100);
		ItemStack helmet = new ItemStack(east ? ModItems.HELMET : r < 25 ? ModItems.NVG_HELMET : ModItems.HELMET_FAST);
		ItemStack vest = new ItemStack(east ? ModItems.VEST_HEAVY : ModItems.VEST);
		GunType g = gun instanceof GunItem gi ? gi.gun : null;
		boolean mg = g != null && g.magazine >= 60;
		boolean marksman = g != null && g.scoped();
		int mags = mg ? 1 : marksman ? 1 : 2 + random.nextInt(2);
		for (int i = 0; i < mags; i++) {
			com.stasdoto.airdefense.gear.Pouches.attach(vest, com.stasdoto.airdefense.gear.Pouch.MAG);
		}
		com.stasdoto.airdefense.gear.Pouches.attach(vest, com.stasdoto.airdefense.gear.Pouch.MEDKIT);
		if (!marksman && random.nextInt(100) < 60) {
			com.stasdoto.airdefense.gear.Pouches.attach(vest, com.stasdoto.airdefense.gear.Pouch.GRENADE);
		}
		if (marksman || random.nextInt(100) < 25) {
			com.stasdoto.airdefense.gear.Pouches.attach(vest, com.stasdoto.airdefense.gear.Pouch.RADIO);
		}
		// 1.29: about one in six is the squad's medic, with a second first-aid kit on his vest.
		if (!marksman && !mg && random.nextInt(6) == 0) {
			com.stasdoto.airdefense.gear.Pouches.attach(vest, com.stasdoto.airdefense.gear.Pouch.MEDKIT);
			medic = true;
		}
		setItemSlot(EquipmentSlot.HEAD, helmet);
		setItemSlot(EquipmentSlot.CHEST, vest);
		grenades = 1 + 2 * com.stasdoto.airdefense.gear.Pouches.count(vest, com.stasdoto.airdefense.gear.Pouch.GRENADE);
		medkits = com.stasdoto.airdefense.gear.Pouches.count(vest, com.stasdoto.airdefense.gear.Pouch.MEDKIT) * (medic ? 3 : 1);
	}

	/** Weapons from the town's arsenal (1.23): a machine gun or a marksman's rifle instead of what he got. */
	public void issueArms(int roll) {
		boolean east = bloc(country) == GunType.Bloc.EAST;
		Item gun = roll < 55 ? (east ? pick(roll, GunType.PKM, GunType.PKP) : pick(roll, GunType.M240B, GunType.M249))
				: (east ? pick(roll, GunType.SVD, GunType.SV98) : pick(roll, GunType.M110, GunType.M82));
		setItemSlot(EquipmentSlot.MAINHAND, GunItem.loaded(gun));
	}

	public Component title() {
		Politics p = level() instanceof ServerLevel sl ? Politics.get(sl.getServer()) : null;
		Country c = p == null ? null : p.country(country);
		if (role() == REBEL) {
			return Component.translatable("entity.airdefense.soldier.rebel");
		}
		String key = switch (role()) {
			case BANDIT -> "entity.airdefense.soldier.bandit";
			case SOLDIER -> "entity.airdefense.soldier.soldier";
			default -> "entity.airdefense.soldier.guard";
		};
		return c == null ? Component.translatable(key) : Component.translatable(key + ".of", c.name);
	}

	public int role() {
		return entityData.get(DATA_ROLE);
	}

	/** Dye colour of the country (armband, guard uniform), -1 = none. */
	public int color() {
		return entityData.get(DATA_COLOR);
	}

	/** Seed of the face, skin tone and hair. */
	public int look() {
		return entityData.get(DATA_LOOK);
	}

	public int country() {
		return country;
	}

	public int home() {
		return home;
	}

	@Nullable
	public BlockPos order() {
		return order;
	}

	public void setCountry(int country, int color) {
		this.country = country;
		entityData.set(DATA_COLOR, color);
		setCustomName(title());
	}

	public void setRole(int role) {
		entityData.set(DATA_ROLE, role);
		setCustomName(title());
	}

	public void setOrigin(VillagerData data, UUID id) {
		this.origin = data;
		this.originId = id;
	}

	public void setVillagerTag(net.minecraft.nbt.CompoundTag tag) {
		this.villagerTag = tag;
	}

	@Nullable
	public VillagerData origin() {
		return origin;
	}

	@Nullable
	public UUID originId() {
		return originId;
	}

	/** 1.29: follow this player (null: stop following). */
	public void follow(@Nullable Player p) {
		follow = p == null ? null : p.getUUID();
		if (p != null) {
			order = null;
			clearHome();
		}
	}

	public boolean following(Player p) {
		return follow != null && follow.equals(p.getUUID());
	}

	public boolean medic() {
		return medic;
	}

	public void setMedic(boolean medic) {
		this.medic = medic;
		if (medic) {
			medkits = Math.max(medkits, 4);
		}
	}

	/** Go there and hold the place (null = back to the home village). */
	public void orderTo(@Nullable BlockPos target) {
		this.order = target;
		clearHome();
		getNavigation().stop();
	}

	@Nullable
	public GunType gun() {
		return GunItemType.of(getMainHandItem());
	}

	// ------------------------------------------------------------------------------------------------
	// Friend or foe

	public boolean isFriend(LivingEntity e) {
		if (e == this) {
			return true;
		}
		if (role() == BANDIT) {
			return e instanceof SoldierEntity s && s.role() == BANDIT;
		}
		if (role() == REBEL) {
			if (e instanceof SoldierEntity s) {
				return s.role() == REBEL && s.home == home;
			}
			return e instanceof AbstractVillager || e instanceof WorkerEntity;
		}
		if (e instanceof SoldierEntity s) {
			return s.role() != BANDIT && s.role() != REBEL && s.country == country && (country >= 0 || s.home == home);
		}
		if (e instanceof AbstractVillager || e instanceof IronGolem || e instanceof WorkerEntity) {
			return true;
		}
		if (e instanceof Player p && level() instanceof ServerLevel sl) {
			Country c = Politics.get(sl.getServer()).country(country);
			return c != null && c.isMember(p.getUUID());
		}
		return false;
	}

	public boolean isEnemy(LivingEntity e) {
		if (e == this || !e.isAlive() || isFriend(e)) {
			return false;
		}
		if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return false;
		}
		if (role() == BANDIT) {
			return e instanceof Player || e instanceof SoldierEntity || e instanceof AbstractVillager || e instanceof IronGolem
					|| e instanceof WorkerEntity;
		}
		if (role() == REBEL) {
			// Rebels fight their ruler's guards and soldiers, and the ruler himself.
			if (e instanceof SoldierEntity s) {
				return (s.role() == GUARD || s.role() == SOLDIER) && s.country == country;
			}
			if (e instanceof Player p && level() instanceof ServerLevel sl) {
				Country c = Politics.get(sl.getServer()).country(country);
				return c != null && c.isMember(p.getUUID());
			}
			return false;
		}
		if (e instanceof SoldierEntity s) {
			return s.role() == BANDIT || s.role() == REBEL && s.country == country || Nations.atWar(level(), country, s.country);
		}
		if (e instanceof Enemy) {
			return true;
		}
		if (e instanceof Player p && level() instanceof ServerLevel sl) {
			return Nations.hostileToPlayer(sl, this, p);
		}
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && !isFriend(attacker)
				&& !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			// Comrades nearby join in.
			for (SoldierEntity s : level.getEntitiesOfClass(SoldierEntity.class, getBoundingBox().inflate(24),
					s -> s != this && s.isFriend(this) && s.getTarget() == null)) {
				s.setTarget(attacker);
			}
			// 1.29: with a radio he calls for help - every free comrade within 64 blocks comes over.
			long now = level.getGameTime();
			if (com.stasdoto.airdefense.gear.Pouches.worn(this, com.stasdoto.airdefense.gear.Pouch.RADIO) > 0 && now - radioAt > 600) {
				radioAt = now;
				radioCalls++;
				level.playSound(null, getX(), getEyeY(), getZ(), com.stasdoto.airdefense.registry.ModSounds.RADIO,
						net.minecraft.sounds.SoundSource.HOSTILE, 0.6f, 1.0f);
				BlockPos here = blockPosition();
				for (SoldierEntity s : level.getEntitiesOfClass(SoldierEntity.class, getBoundingBox().inflate(64),
						s -> s != this && s.isFriend(this) && s.getTarget() == null && s.follow == null && s.role() != GUARD)) {
					s.order = here;
					s.clearHome();
				}
			}
			if (attacker instanceof Player p) {
				Nations.offended(level, this, p);
			}
		}
		return hurt;
	}

	// ------------------------------------------------------------------------------------------------
	// Shooting

	/** 1.32.2: when an attacker set off (game time; 0 = not one), how long they stay; for the tests: attackers gone. */
	public long raidSince;
	public static final long RAID_LIFE = 6000;
	public static int raidersGone;

	@Override
	public void tick() {
		if (level().isClientSide()) {
			super.tick();
			return;
		}
		long perf0 = System.nanoTime();
		super.tick();
		// 1.32.2: an attacker sent in a column or a squad does not stay for ever: after five minutes he is gone (back
		// home) once nobody is near enough to see him go - troops kept coming and crowded round the towns' flags.
		if (raidSince > 0 && (tickCount + getId()) % 100 == 0 && level().getGameTime() - raidSince > RAID_LIFE
				&& level().getNearestPlayer(this, 48) == null) {
			raidersGone++;
			discard();
			return;
		}
		// 1.27: night goggles down after dark, up by day.
		if ((tickCount + getId()) % 100 == 0) {
			ItemStack head = getItemBySlot(EquipmentSlot.HEAD);
			if (head.getItem() instanceof com.stasdoto.airdefense.weapon.NvgItem) {
				boolean night = level().isDarkOutside();
				if (com.stasdoto.airdefense.weapon.NvgItem.isOn(head) != night) {
					head.set(com.stasdoto.airdefense.registry.ModComponents.NVG_ON, night);
				}
			}
		}
		com.stasdoto.airdefense.util.Perf.add(com.stasdoto.airdefense.util.Perf.SOLDIERS, System.nanoTime() - perf0);
		com.stasdoto.airdefense.util.Perf.over("soldier at " + blockPosition().toShortString(), perf0);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		// On the march the ground under the squad stays loaded, so it keeps going when nobody is near.
		if (order != null && role() == SOLDIER && tickCount % 20 == 0 && distanceToSqr(Vec3.atBottomCenterOf(order)) > 36
				&& level() instanceof ServerLevel sl) {
			sl.getChunkSource().addTicketWithRadius(com.stasdoto.airdefense.registry.ModTickets.VEHICLE,
					net.minecraft.world.level.ChunkPos.containing(blockPosition()), 2);
		}
		GunType gun = gun();
		if (gun == null) {
			return;
		}
		if (ammo < 0) {
			ammo = gun.magazine;
		}
		if (reload > 0 && --reload == 0) {
			ammo = gun.magazine;
			level().playSound(null, getX(), getEyeY(), getZ(), ModSounds.GUN_BOLT, SoundSource.HOSTILE, 0.7f, 1f);
		}
	}

	/** One shot (or nothing, if not ready or a friend is in the way). */
	void tryShoot(LivingEntity target) {
		GunType gun = gun();
		if (gun == null || gun.rocket() || cooldown > 0 || reload > 0 || !(level() instanceof ServerLevel level)) {
			return;
		}
		if (ammo <= 0) {
			reload = gun.reload + 10;
			level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.GUN_MAG_OUT, SoundSource.HOSTILE, 0.7f, 1f);
			return;
		}
		Vec3 eye = getEyePosition();
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.62, 0);
		Vec3 straight = aim.subtract(eye).normalize();
		GunServer.Trace clear = GunServer.trace(level, this, eye, straight, gun.range);
		if (clear.entity() instanceof LivingEntity in && in != target && isFriend(in)) {
			cooldown = 6;
			return;
		}
		float spread = role() == BANDIT ? 3.0f : role() == REBEL ? 2.5f : 1.4f;
		// 1.29: under fire (pinned down) he shoots wide.
		if (hurtTime > 0 || tickCount - getLastHurtByMobTimestamp() < 40) {
			spread *= 1.7f;
		}
		Vec3 dir = cone(straight, spread + gun.aimSpread);
		GunServer.shoot(level, this, gun, eye, dir, GunServer.muzzle(this, straight), ++round);
		ammo--;
		if (gun.auto) {
			if (++burst >= 3 + random.nextInt(3)) {
				burst = 0;
				cooldown = 14 + random.nextInt(12);
			} else {
				cooldown = (int) Math.ceil(gun.interval);
			}
		} else {
			cooldown = (int) Math.ceil(gun.interval) + 10 + random.nextInt(14);
		}
	}

	private Vec3 cone(Vec3 look, float degrees) {
		Vec3 up = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = look.cross(up).normalize();
		Vec3 b = look.cross(a).normalize();
		double rad = Math.toRadians(degrees) * 0.5;
		return look.add(a.scale(random.nextGaussian() * rad * 0.6)).add(b.scale(random.nextGaussian() * rad * 0.6)).normalize();
	}

	// ------------------------------------------------------------------------------------------------
	// Life and death

	@Override
	public boolean removeWhenFarAway(double distance) {
		return role() == BANDIT && distance > 128 * 128;
	}

	@Override
	public boolean requiresCustomPersistence() {
		// Their names are only labels: bandits still wander off and vanish when nobody is around.
		return role() != BANDIT && super.requiresCustomPersistence();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		GunType gun = gun();
		if (gun == null) {
			return;
		}
		Item ammoItem = gun.ammo();
		ItemStack vest = getItemBySlot(EquipmentSlot.CHEST);
		if (ammoItem != null && !gun.rocket()) {
			// 1.27: what was in his magazine pouches too.
			int mags = com.stasdoto.airdefense.gear.Pouches.count(vest, com.stasdoto.airdefense.gear.Pouch.MAG);
			int n = 4 + random.nextInt(gun.magazine >= 60 ? 30 : 14) + mags * (gun.magazine / 2 + random.nextInt(gun.magazine / 2 + 1));
			spawnAtLocation(level, new ItemStack(ammoItem, Math.min(n, ammoItem.getDefaultMaxStackSize())));
		}
		if (medkits > 0 && random.nextInt(3) == 0) {
			spawnAtLocation(level, new ItemStack(ModItems.MEDKIT));
		}
		if (grenades > 0 && com.stasdoto.airdefense.gear.Pouches.count(vest, com.stasdoto.airdefense.gear.Pouch.GRENADE) > 0 && random.nextInt(3) == 0) {
			spawnAtLocation(level, new ItemStack(ModItems.F1_GRENADE, Math.min(grenades, 2)));
		}
		if (random.nextInt(6) == 0) {
			com.stasdoto.airdefense.gear.Pouch p = com.stasdoto.airdefense.gear.Pouches.at(vest, random.nextInt(com.stasdoto.airdefense.gear.Pouches.SLOTS));
			if (p != com.stasdoto.airdefense.gear.Pouch.NONE) {
				spawnAtLocation(level, new ItemStack(p.item()));
			}
		}
		if (random.nextInt(8) == 0) {
			ItemStack g = getMainHandItem().copy();
			GunItem.setAmmo(g, random.nextInt(gun.magazine / 2 + 1));
			spawnAtLocation(level, g);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			Nations.soldierDied(level, this);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("role", role());
		output.putInt("color", color());
		output.putInt("look", look());
		output.putInt("country", country);
		output.putInt("home", home);
		output.putLong("raid_since", raidSince);
		output.putInt("grenades", grenades);
		output.putInt("medkits", medkits);
		output.putBoolean("medic", medic);
		if (order != null) {
			output.store("order", BlockPos.CODEC, order);
		}
		if (origin != null) {
			output.store("origin", VillagerData.CODEC, origin);
		}
		if (originId != null) {
			output.store("origin_id", UUIDUtil.CODEC, originId);
		}
		if (villagerTag != null) {
			output.store("villager", net.minecraft.nbt.CompoundTag.CODEC, villagerTag);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DATA_ROLE, input.getIntOr("role", GUARD));
		entityData.set(DATA_COLOR, input.getIntOr("color", -1));
		entityData.set(DATA_LOOK, input.getIntOr("look", 0));
		country = input.getIntOr("country", -1);
		home = input.getIntOr("home", -1);
		raidSince = input.getLongOr("raid_since", 0L);
		grenades = input.getIntOr("grenades", 2);
		medkits = input.getIntOr("medkits", 0);
		medic = input.getBooleanOr("medic", false);
		order = input.read("order", BlockPos.CODEC).orElse(null);
		origin = input.read("origin", VillagerData.CODEC).orElse(null);
		originId = input.read("origin_id", UUIDUtil.CODEC).orElse(null);
		villagerTag = input.read("villager", net.minecraft.nbt.CompoundTag.CODEC).orElse(null);
	}

	// ------------------------------------------------------------------------------------------------
	// Goals

	/** For the tests: soldiers that took cover, fell back wounded, threw grenades (1.25). */
	public static int tookCover;
	public static int fellBack;
	public static int grenadesThrown;
	private int grenades = 2;
	/** 1.27: first-aid kits on the vest, used on himself once out of the line of fire. */
	private int medkits;
	public static int selfAid;
	private int grenadeCooldown;

	/**
	 * Fighting (1.25: smarter). Under fire a soldier looks for cover nearby - a wall, a bank, a car - he can shoot over,
	 * and fights from there; badly hurt he falls back out of the line of fire; an enemy hiding behind something close by
	 * gets a grenade; the men of a squad keep a few steps apart. Otherwise: close in to a good range and fire.
	 */
	static final class ShootGoal extends Goal {
		private final SoldierEntity s;
		private int repath;
		@Nullable
		private BlockPos flankTo;
		private int flankTimer;
		private int flankCheck;
		@Nullable
		private BlockPos cover;
		private int coverSearch;
		private int fallBack;

		ShootGoal(SoldierEntity s) {
			this.s = s;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = s.getTarget();
			return t != null && t.isAlive() && s.gun() != null;
		}

		@Override
		public void start() {
			s.setAggressive(true);
			cover = null;
		}

		@Override
		public void stop() {
			s.getNavigation().stop();
			s.setAggressive(false);
			cover = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity t = s.getTarget();
			GunType gun = s.gun();
			if (t == null || gun == null || !(s.level() instanceof ServerLevel level)) {
				return;
			}
			double d = s.distanceTo(t);
			boolean see = s.getSensing().hasLineOfSight(t) && !com.stasdoto.airdefense.fx.Smoke.blocks(level, s.getEyePosition(), t.getEyePosition());
			s.getLookControl().setLookAt(t, 40f, 40f);
			double good = gun.pistol || gun.pellets > 1 ? 12 : gun.scoped() ? 45 : gun.range < 90 ? 18 : 26;
			boolean underFire = s.hurtTime > 0 || s.tickCount - s.getLastHurtByMobTimestamp() < 60;
			if (s.grenadeCooldown > 0) {
				s.grenadeCooldown--;
			}
			// Badly hurt: back out of the line of fire for a while.
			if (fallBack > 0) {
				fallBack--;
				if (fallBack == 30 && s.medkits > 0 && s.getHealth() < s.getMaxHealth() * 0.6f) {
					s.medkits--;
					s.heal(8.0f);
					s.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
					level.playSound(null, s.getX(), s.getY(), s.getZ(), com.stasdoto.airdefense.registry.ModSounds.MEDKIT,
							net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.0f);
					selfAid++;
				}
				if (see && fallBack % 10 == 0) {
					s.tryShoot(t);
				}
				return;
			}
			if (s.getHealth() < s.getMaxHealth() * 0.35f && underFire && s.random.nextInt(3) == 0) {
				Vec3 away = s.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(12);
				Vec3 to = s.position().add(away);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(to.x), Mth.floor(to.z));
				s.getNavigation().moveTo(to.x, y, to.z, 1.25);
				fallBack = 70;
				cover = null;
				fellBack++;
				return;
			}
			// A grenade for an enemy hiding close by.
			if (!see && d > 6 && d < 18 && s.grenades > 0 && s.grenadeCooldown == 0 && s.random.nextInt(4) == 0) {
				throwGrenade(level, t, d);
				return;
			}
			// Cover when under fire (or just when close): a spot nearby with something solid towards the enemy.
			if (cover == null && (underFire || d < 20) && --coverSearch <= 0) {
				coverSearch = 40;
				cover = findCover(level, t, good);
				if (cover != null) {
					tookCover++;
				}
			}
			if (cover != null) {
				if (cover.distSqr(t.blockPosition()) < 36 || !level.getBlockState(cover).isAir()) {
					cover = null;
				} else if (s.blockPosition().distSqr(cover) > 1) {
					if (--repath <= 0) {
						repath = 10;
						s.getNavigation().moveTo(cover.getX() + 0.5, cover.getY(), cover.getZ() + 0.5, underFire ? 1.25 : 1.0);
					}
				} else {
					s.getNavigation().stop();
				}
				if (see && d <= gun.range * 0.8) {
					s.tryShoot(t);
				}
				return;
			}
			// 1.29: an enemy dug in out of sight - every other man works round his flank instead of walking at him.
			if (flankTo != null) {
				if (--flankTimer <= 0 || see || s.blockPosition().distSqr(flankTo) < 4) {
					flankTo = null;
				} else {
					if (--repath <= 0) {
						repath = 10;
						s.getNavigation().moveTo(flankTo.getX() + 0.5, flankTo.getY(), flankTo.getZ() + 0.5, 1.15);
					}
					return;
				}
			}
			if (!see && d > 10 && d < 45 && (s.getId() & 1) == 1 && --flankCheck <= 0) {
				flankCheck = 120;
				Vec3 to = t.position().subtract(s.position()).multiply(1, 0, 1);
				if (to.lengthSqr() > 1e-4) {
					to = to.normalize();
					Vec3 side = new Vec3(-to.z, 0, to.x).scale((s.getId() & 2) == 0 ? 1 : -1);
					Vec3 at = t.position().add(side.scale(14)).subtract(to.scale(3));
					int fy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
					if (Math.abs(fy - s.getY()) < 6) {
						flankTo = new BlockPos(Mth.floor(at.x), fy, Mth.floor(at.z));
						flankTimer = 160;
						repath = 0;
						flanked++;
						return;
					}
				}
			}
			if (!see || d > good) {
				if (--repath <= 0) {
					repath = 10;
					s.getNavigation().moveTo(t, 1.0);
				}
			} else {
				// A few steps apart from the next man.
				SoldierEntity close = null;
				for (SoldierEntity o : level.getEntitiesOfClass(SoldierEntity.class, s.getBoundingBox().inflate(2.2), o -> o != s && o.isAlive()
						&& o.country() == s.country())) {
					close = o;
					break;
				}
				if (close != null && --repath <= 0) {
					repath = 15;
					Vec3 side = s.position().subtract(close.position()).multiply(1, 0, 1);
					side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
					Vec3 to = s.position().add(side.scale(3));
					s.getNavigation().moveTo(to.x, s.getY(), to.z, 0.9);
				} else if (close == null) {
					s.getNavigation().stop();
				}
			}
			if (see && d <= gun.range * 0.8) {
				s.tryShoot(t);
			}
		}

		/** A spot within a few blocks to stand behind something solid, towards the enemy, still in range of him. */
		@Nullable
		private BlockPos findCover(ServerLevel level, LivingEntity t, double good) {
			Vec3 to = t.position().subtract(s.position());
			BlockPos best = null;
			double bestScore = Double.MAX_VALUE;
			BlockPos here = s.blockPosition();
			for (int dx = -7; dx <= 7; dx++) {
				for (int dz = -7; dz <= 7; dz++) {
					if (dx * dx + dz * dz > 49) {
						continue;
					}
					int x = here.getX() + dx;
					int z = here.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					if (Math.abs(y - here.getY()) > 2) {
						continue;
					}
					BlockPos p = new BlockPos(x, y, z);
					if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) {
						continue;
					}
					double tx = t.getX() - (x + 0.5);
					double tz = t.getZ() - (z + 0.5);
					double dist = Math.sqrt(tx * tx + tz * tz);
					if (dist < 8 || dist > good * 1.5) {
						continue;
					}
					// The block one step towards the enemy: something solid at the height of the body.
					BlockPos front = BlockPos.containing(x + 0.5 + tx / dist, y, z + 0.5 + tz / dist);
					boolean low = !level.getBlockState(front).getCollisionShape(level, front).isEmpty();
					boolean high = !level.getBlockState(front.above()).getCollisionShape(level, front.above()).isEmpty();
					if (!low) {
						continue;
					}
					boolean taken = !level.getEntitiesOfClass(SoldierEntity.class, new net.minecraft.world.phys.AABB(p).inflate(0.8), o -> o != s).isEmpty();
					if (taken) {
						continue;
					}
					double score = Math.sqrt(dx * dx + dz * dz) + (high ? 3 : 0) + Math.max(0, dist - good) * 0.5;
					if (score < bestScore) {
						bestScore = score;
						best = p;
					}
				}
			}
			return best;
		}

		private void throwGrenade(ServerLevel level, LivingEntity t, double d) {
			Vec3 to = t.position().subtract(s.getEyePosition());
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			s.setYRot(yaw);
			s.setYHeadRot(yaw);
			s.setXRot(-32f);
			com.stasdoto.airdefense.weapon.GrenadeEntity.throwFrom(level, s, (float) Mth.clamp(0.35 + d * 0.042, 0.5, 1.15));
			s.grenades--;
			s.grenadeCooldown = 200;
			grenadesThrown++;
		}
	}

	/** Marching orders: there in legs of ~30 blocks (long paths are not found in one go), then hold the spot. */
	/** 1.29: the medic: to a wounded comrade within 16 blocks, a field dressing, back to the fight. */
	static final class MedicGoal extends Goal {
		private final SoldierEntity s;
		@Nullable
		private SoldierEntity patient;
		private int timer;
		private int scan;

		MedicGoal(SoldierEntity s) {
			this.s = s;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!s.medic || s.medkits <= 0 || --scan > 0) {
				return false;
			}
			scan = 20;
			patient = null;
			double best = Double.MAX_VALUE;
			for (SoldierEntity o : s.level().getEntitiesOfClass(SoldierEntity.class, s.getBoundingBox().inflate(16),
					o -> o != s && o.isAlive() && o.isFriend(s) && o.getHealth() < o.getMaxHealth() * 0.6f)) {
				double d = o.distanceToSqr(s);
				if (d < best) {
					best = d;
					patient = o;
				}
			}
			return patient != null;
		}

		@Override
		public boolean canContinueToUse() {
			return patient != null && patient.isAlive() && s.medkits > 0 && timer < 200 && patient.getHealth() < patient.getMaxHealth() * 0.9f;
		}

		@Override
		public void start() {
			timer = 0;
		}

		@Override
		public void stop() {
			patient = null;
			s.getNavigation().stop();
		}

		@Override
		public void tick() {
			timer++;
			if (patient == null) {
				return;
			}
			s.getLookControl().setLookAt(patient, 30f, 30f);
			if (s.distanceToSqr(patient) > 2.5 * 2.5) {
				if (timer % 10 == 1) {
					s.getNavigation().moveTo(patient, 1.25);
				}
				return;
			}
			s.getNavigation().stop();
			s.medkits--;
			patient.heal(10.0f);
			patient.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
			s.level().playSound(null, patient.getX(), patient.getY(), patient.getZ(), com.stasdoto.airdefense.registry.ModSounds.MEDKIT,
					net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.0f);
			healedOthers++;
			patient = null;
		}
	}

	/** 1.29: following the commander who told him to: keeps a few steps behind, catches up if left far behind. */
	static final class FollowGoal extends Goal {
		private final SoldierEntity s;
		private int repath;

		FollowGoal(SoldierEntity s) {
			this.s = s;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Nullable
		private Player leader() {
			if (s.follow == null || !(s.level() instanceof ServerLevel sl)) {
				return null;
			}
			Player p = sl.getPlayerByUUID(s.follow);
			return p != null && p.isAlive() && !p.isSpectator() ? p : null;
		}

		@Override
		public boolean canUse() {
			Player p = leader();
			return p != null && s.getTarget() == null && s.distanceToSqr(p) > 5 * 5;
		}

		@Override
		public boolean canContinueToUse() {
			Player p = leader();
			return p != null && s.getTarget() == null && s.distanceToSqr(p) > 3 * 3;
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void stop() {
			s.getNavigation().stop();
		}

		@Override
		public void tick() {
			Player p = leader();
			if (p == null) {
				return;
			}
			s.getLookControl().setLookAt(p, 20f, 20f);
			if (s.distanceToSqr(p) > 40 * 40 && p.onGround()) {
				// Left far behind: he catches up (as a dog would).
				Vec3 back = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
				s.teleportTo(back.x, p.getY(), back.z);
				s.getNavigation().stop();
				return;
			}
			if (--repath <= 0) {
				repath = 10;
				s.getNavigation().moveTo(p, s.distanceToSqr(p) > 12 * 12 ? 1.25 : 1.0);
			}
		}
	}

	static final class OrderGoal extends Goal {
		private final SoldierEntity s;
		private int repath;

		OrderGoal(SoldierEntity s) {
			this.s = s;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return s.order != null && s.getTarget() == null && s.distanceToSqr(Vec3.atBottomCenterOf(s.order)) > 16;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void stop() {
			if (s.order != null && s.distanceToSqr(Vec3.atBottomCenterOf(s.order)) <= 16) {
				// Arrived: hold the position.
				s.setHomeTo(s.order, 8);
			}
		}

		@Override
		public void tick() {
			if (--repath > 0) {
				return;
			}
			repath = 20;
			Vec3 to = Vec3.atBottomCenterOf(s.order);
			Vec3 from = s.position();
			Vec3 d = to.subtract(from);
			double len = Math.sqrt(d.x * d.x + d.z * d.z);
			if (len > 30) {
				double k = 28 / len;
				int x = Mth.floor(from.x + d.x * k);
				int z = Mth.floor(from.z + d.z * k);
				int y = s.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				s.getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.0);
			} else {
				s.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
			}
		}
	}

	/** Which gun an item is (helper kept apart so the AI code reads simply). */
	static final class GunItemType {
		private GunItemType() {
		}

		@Nullable
		static GunType of(ItemStack stack) {
			return stack.getItem() instanceof GunItem g ? g.gun : null;
		}
	}

	/** Called up villagers go home: they become villagers again (same person, same face). */
	public void demobilize(ServerLevel level) {
		net.minecraft.world.entity.npc.villager.Villager villager = null;
		if (villagerTag != null && level.getEntity(originId == null ? UUID.randomUUID() : originId) == null) {
			Entity e = EntityType.loadEntityRecursive(net.minecraft.world.entity.EntityTypes.VILLAGER, villagerTag, level,
					net.minecraft.world.entity.EntitySpawnReason.CONVERSION, net.minecraft.world.entity.EntityProcessor.NOP);
			if (e instanceof net.minecraft.world.entity.npc.villager.Villager v) {
				villager = v;
			}
		}
		if (villager == null) {
			villager = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.CONVERSION);
			if (villager == null) {
				return;
			}
			if (origin != null) {
				villager.setVillagerData(origin);
			}
			if (originId != null && level.getEntity(originId) == null) {
				villager.setUUID(originId);
			}
		}
		villager.snapTo(getX(), getY(), getZ(), getYRot(), getXRot());
		level.addFreshEntity(villager);
		discard();
	}

	/** Item drops of the bandits' loot: kept simple, a few emeralds sometimes. */
	static void loot(ServerLevel level, Entity at) {
		if (level.getRandom().nextInt(3) == 0) {
			level.addFreshEntity(new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(),
					new ItemStack(net.minecraft.world.item.Items.EMERALD, 1 + level.getRandom().nextInt(3))));
		}
	}
}
