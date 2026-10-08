package com.stasdoto.airdefense.vehicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.stasdoto.airdefense.defense.DefenseType;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.launcher.LauncherType;
import com.stasdoto.airdefense.missile.Effects;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.radar.RadarNetwork;
import com.stasdoto.airdefense.radar.RadarType;
import com.stasdoto.airdefense.registry.ModParticles;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.registry.ModTickets;

/**
 * A drivable military vehicle at real size: wheels or tracks, a driver and a gunner seat, and either a strike
 * launcher (deploys, aims and fires a salvo at a designated target) or an air defence system (radar, automatic
 * engagement of incoming missiles and drones).
 *
 * <p>Driving runs on the driver's own client (like boats and horses), everything else on the server.
 */
public class VehicleEntity extends LivingEntity {
	private static final EntityDataAccessor<Integer> DATA_DRIVER = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GUNNER = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_ELEV_TARGET = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_TURRET_TARGET = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_LOADED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_AMMO = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	/** Spare missiles (shots) carried for reloading; -1 = unlimited (vehicles put down in creative mode). */
	private static final EntityDataAccessor<Integer> DATA_RESERVE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	/** Litres in the tank; -1 = never runs dry (put down in creative mode). */
	private static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

	public static final int STOWED = 0;
	public static final int DEPLOYED = 1;
	public static final int MODE_OFF = 0;
	public static final int MODE_AUTO = 1;
	public static final int MODE_MANUAL = 2;

	public static final int ACTION_DEPLOY = 0;
	public static final int ACTION_MODE = 1;
	public static final int ACTION_SEAT = 2;
	public static final int ACTION_STOW_FOR_MARCH = 3;
	public static final int ACTION_FIRE = 4;
	/** 1.26: smoke grenades (fighting vehicles). */
	public static final int ACTION_SMOKE = 5;

	public static final int MIN_STRIKE_DISTANCE = 24;
	/** Missile batteries deploy after standing still this long (a blast wave rocking the truck does not count as driving). */
	private static final int DEPLOY_STILL_TICKS = 20;
	/** Gepard: speed of its 35 mm rounds in blocks per tick (slowed down from the real ~60 so the tracers can be seen). */
	public static final double SHELL_SPEED = 8.0;
	/** Gepard: how far the guns may still be off the lead point and keep firing (degrees). */
	private static final float GUN_YAW_TOLERANCE = 10f;
	private static final float GUN_ELEVATION_TOLERANCE = 8f;
	/** Client hook: asks the server to fold the launcher when the driver wants to drive off (set by client code). */
	public static Consumer<VehicleEntity> stowRequester = v -> {
	};

	private final VehicleType vtype;

	// Driving (authoritative side: the driver's client, or the server when nobody drives).
	private float speed;
	private float steer;
	private Input clientInput = Input.EMPTY;
	private int stowRequestCooldown;

	// Animated on both sides towards the synced targets.
	public float elevation;
	public float elevationO;
	public float turretYaw;
	public float turretYawO;
	public float roofOpen;
	public float roofOpenO;
	private boolean anglesInitialised;

	// Client-only visuals.
	public float wheelRoll;
	public float wheelRollO;
	public float steerVis;
	public float steerVisO;
	public float tiltPitch;
	public float tiltPitchO;
	public float tiltRoll;
	public float tiltRollO;
	public float lift;
	public float liftO;
	public float radarSpin;
	public float radarSpinO;

	// Strike launcher state (server).
	@Nullable
	private BlockPos strikeTarget;
	private boolean strikePending;
	private int salvoLeft;
	private int salvoTimer;
	private int cooldown;
	private int stowTimer;
	private boolean reloadPending;
	/** Flight task for the current salvo (drones, cruise missiles), and who watches through the first one's camera. */
	@Nullable
	private com.stasdoto.airdefense.drone.FlightPlan plan;
	@Nullable
	private java.util.UUID planViewer;
	private int salvoIndex;

	// Air defence state (server).
	private int fireTimer;
	private int reloadTimer;
	private int sirenTimer;
	private int stationaryTicks;
	private int marchTicks;
	@Nullable
	private MissileEntity tracked;
	private Vec3 lastServerPos = Vec3.ZERO;
	/** Gepard: rounds of the current burst still to fire, and rounds in flight that will hit when they arrive. */
	private int burstLeft;
	private final List<Shell> shells = new ArrayList<>();
	/** Missile batteries: standing inside a radar station's range (target data from the network). */
	private boolean radarLinked;
	/** How much further a battery shoots with target data from a radar station. */
	public static final double RADAR_RANGE_BONUS = 1.3;

	private static final class Shell {
		final MissileEntity target;
		final float damage;
		int ticks;

		Shell(MissileEntity target, float damage, int ticks) {
			this.target = target;
			this.damage = damage;
			this.ticks = ticks;
		}
	}

	public VehicleEntity(EntityType<? extends VehicleEntity> entityType, Level level, VehicleType vtype) {
		super(entityType, level);
		this.vtype = vtype;
		this.elevation = this.elevationO = vtype.geometry.fixedElevation();
		this.setNoGravity(false);
	}

	public static AttributeSupplier.Builder createAttributes(VehicleType type) {
		return LivingEntity.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, type.maxHealth)
				.add(Attributes.ARMOR, 12)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.1)
				.add(Attributes.CAMERA_DISTANCE, type.geometry.camera());
	}

	/** Puts a vehicle into the world, nose pointing along {@code yaw}. */
	public static VehicleEntity spawn(ServerLevel level, VehicleType type, Vec3 pos, float yaw) {
		VehicleEntity v = new VehicleEntity(com.stasdoto.airdefense.registry.ModEntities.vehicle(type), level, type);
		v.snapTo(pos.x, pos.y, pos.z, yaw, 0);
		v.yBodyRot = v.yHeadRot = yaw;
		v.setHealth(v.getMaxHealth());
		if (type.hasMode()) {
			v.setMode(MODE_AUTO);
		}
		v.setLoadedMask(v.fullMask());
		v.setAmmo(type.magazine());
		if (type.isArmed()) {
			v.setMode(MODE_AUTO);
		}
		if (type.ordnance != null) {
			v.entityData.set(DATA_ORDNANCE, type.ordnance.count);
		}
		v.fold();
		level.addFreshEntity(v);
		// From the first moment on, its ground stays loaded (even if the player walks off right away).
		level.getChunkSource().addTicketWithRadius(ModTickets.VEHICLE, ChunkPos.containing(v.blockPosition()), 2);
		return v;
	}

	/** Old one-block launchers turn into the real vehicle, facing the same way. */
	public static void replaceBlock(ServerLevel level, BlockPos pos, VehicleType type, Direction facing) {
		level.removeBlock(pos, false);
		spawn(level, type, Vec3.atBottomCenterOf(pos), facing.toYRot());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DRIVER, -1);
		builder.define(DATA_GUNNER, -1);
		builder.define(DATA_STATE, STOWED);
		builder.define(DATA_MODE, MODE_OFF);
		builder.define(DATA_ELEV_TARGET, 0f);
		builder.define(DATA_TURRET_TARGET, 0f);
		builder.define(DATA_LOADED, -1);
		builder.define(DATA_AMMO, -1);
		builder.define(DATA_RESERVE, -1);
		builder.define(DATA_FUEL, -1f);
		builder.define(DATA_ORDNANCE, 0);
		builder.define(DATA_CARGO, 0);
		builder.define(DATA_CARGO_KIND, -1);
	}

	// ------------------------------------------------------------------------------------------------
	// Accessors

	public VehicleType getVehicleType() {
		return vtype;
	}

	public int getState() {
		return entityData.get(DATA_STATE);
	}

	private void setState(int state) {
		if (getState() != state) {
			entityData.set(DATA_STATE, state);
		}
	}

	public int getMode() {
		return entityData.get(DATA_MODE);
	}

	private void setMode(int mode) {
		entityData.set(DATA_MODE, mode);
	}

	public float getElevationTarget() {
		return entityData.get(DATA_ELEV_TARGET);
	}

	private void setElevationTarget(float deg) {
		if (Math.abs(getElevationTarget() - deg) > 0.01f) {
			entityData.set(DATA_ELEV_TARGET, deg);
		}
	}

	public float getTurretTarget() {
		return entityData.get(DATA_TURRET_TARGET);
	}

	private void setTurretTarget(float deg) {
		if (Math.abs(getTurretTarget() - deg) > 0.01f) {
			entityData.set(DATA_TURRET_TARGET, deg);
		}
	}

	/** Bit i set = rail i still has its missile (drawn on the vehicle). */
	public int getLoadedMask() {
		int m = entityData.get(DATA_LOADED);
		return m < 0 ? fullMask() : m;
	}

	private void setLoadedMask(int mask) {
		entityData.set(DATA_LOADED, mask);
	}

	private int fullMask() {
		return (1 << vtype.rails()) - 1;
	}

	public int getAmmo() {
		int a = entityData.get(DATA_AMMO);
		return a < 0 ? vtype.magazine() : a;
	}

	private void setAmmo(int ammo) {
		entityData.set(DATA_AMMO, ammo);
	}

	// --- Ammunition reserve (stage 5: missiles come from the factory) ---

	public boolean isUnlimited() {
		return entityData.get(DATA_RESERVE) < 0;
	}

	public int getReserve() {
		return Math.max(0, entityData.get(DATA_RESERVE));
	}

	private void setReserve(int reserve) {
		entityData.set(DATA_RESERVE, reserve);
	}

	/** Put down by a survival player: reloads only from what it is given (by hand or from a factory nearby). */
	public void setUnlimited(boolean unlimited) {
		setReserve(unlimited ? -1 : 0);
		setFuel(unlimited ? -1 : vtype.fuelCapacity());
	}

	/** Spare shots it can carry: two more salvos for launchers, two magazines for missile batteries, three for Gepard. */
	public int reserveCapacity() {
		if (vtype.isRadar()) {
			return 0;
		}
		if (vtype.isArmed()) {
			return vtype.weapon.magazine;
		}
		if (vtype.isLauncher()) {
			return vtype.rails() * 2;
		}
		if (vtype.defense == null) {
			return 0;
		}
		return vtype.defense.magazine * (vtype.gunOnly() ? 3 : 2);
	}

	public int reserveSpace() {
		return isUnlimited() ? 0 : Math.max(0, reserveCapacity() - getReserve());
	}

	// --- Fuel (stage R5) ---

	public boolean infiniteFuel() {
		return entityData.get(DATA_FUEL) < 0;
	}

	public float getFuel() {
		float f = entityData.get(DATA_FUEL);
		return f < 0 ? vtype.fuelCapacity() : f;
	}

	public void setFuel(float litres) {
		entityData.set(DATA_FUEL, litres < 0 ? -1f : Math.min(vtype.fuelCapacity(), litres));
	}

	/** Pours in up to {@code litres}; returns how much went in. */
	public float refuel(float litres) {
		if (infiniteFuel()) {
			return 0;
		}
		float space = vtype.fuelCapacity() - getFuel();
		float in = Math.max(0, Math.min(space, litres));
		setFuel(getFuel() + in);
		return in;
	}

	public boolean outOfFuel() {
		return !infiniteFuel() && getFuel() <= 0.01f;
	}

	public void addReserve(int units) {
		if (!isUnlimited() && units > 0) {
			setReserve(Math.min(reserveCapacity(), getReserve() + units));
		}
	}

	/** Takes up to {@code n} shots out of the reserve (all of them when unlimited) and says how many it got. */
	private int takeReserve(int n) {
		if (isUnlimited()) {
			return n;
		}
		int k = Math.min(n, getReserve());
		setReserve(getReserve() - k);
		return k;
	}

	/** Launchers: missiles from the reserve onto the empty rails. */
	private void reloadRails() {
		int mask = getLoadedMask();
		int n = takeReserve(vtype.rails() - Integer.bitCount(mask));
		for (int i = 0; i < vtype.rails() && n > 0; i++) {
			if ((mask & (1 << i)) == 0) {
				mask |= 1 << i;
				n--;
			}
		}
		setLoadedMask(mask);
		setAmmo(Integer.bitCount(mask));
	}

	/** Air defence: refill the magazine from the reserve; the canisters still holding missiles show on the model. */
	private void reloadMagazine() {
		DefenseType type = vtype.defense;
		int have = Math.max(0, getAmmo());
		setAmmo(have + takeReserve(type.magazine - have));
		int[] missiles = vtype.missileRails();
		int rails = Math.max(1, missiles.length);
		int perRail = Math.max(1, (type.magazine + rails - 1) / rails);
		int first = Math.min(rails, (type.magazine - getAmmo()) / perRail);
		int mask = 0;
		for (int i = first; i < missiles.length; i++) {
			mask |= 1 << missiles[i];
		}
		for (int b : vtype.barrelRails()) {
			mask |= 1 << b;
		}
		setLoadedMask(getAmmo() > 0 ? mask : 0);
	}

	@Nullable
	public Player getDriver() {
		return level().getEntity(entityData.get(DATA_DRIVER)) instanceof Player p && hasPassenger(p) ? p : null;
	}

	@Nullable
	public Player getGunner() {
		return level().getEntity(entityData.get(DATA_GUNNER)) instanceof Player p && hasPassenger(p) ? p : null;
	}

	public boolean isDriver(Entity e) {
		return e != null && entityData.get(DATA_DRIVER) == e.getId();
	}

	public float getSpeed() {
		return speed;
	}

	public float getSteer() {
		return steer;
	}

	/** Driver's keys, set every tick by client code for the local driver. */
	public void setClientInput(Input input) {
		this.clientInput = input;
	}

	public boolean isFolded() {
		return getState() == STOWED && Math.abs(elevation - vtype.geometry.fixedElevation()) < 1.0f
				&& Math.abs(turretYaw) < 2.0f && roofOpen < 0.05f;
	}

	/** Gepard can shoot on the move; launchers and missile batteries must fold up first. */
	public boolean canDrive() {
		// Lorries have nothing to fold (their "deployed" is only the load bed being open).
		return isAlive() && (vtype.gunOnly() || vtype.isArmed() || vtype.isTruck() || isFolded());
	}

	// ------------------------------------------------------------------------------------------------
	// Geometry helpers

	public Vec3 forward() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	public Vec3 right() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	/** Vehicle space (x right, y up, z forward; metres) to world space. */
	public Vec3 toWorld(double x, double y, double z) {
		Vec3 f = forward();
		Vec3 r = right();
		return position().add(r.x * x + f.x * z, y, r.z * x + f.z * z);
	}

	/** Where the missile on rail {@code i} sits, in vehicle space, for the current launcher angles. */
	private Vec3 railLocal(int i, boolean direction) {
		VehicleGeometry.Geometry g = vtype.geometry;
		VehicleGeometry.Rail rail = g.rails()[i];
		double e = Math.toRadians(elevation);
		double x = direction ? 0 : rail.x();
		double y0 = direction ? 0 : rail.y();
		double z0 = direction ? 1 : rail.z();
		double y = y0 * Math.cos(e) + z0 * Math.sin(e);
		double z = z0 * Math.cos(e) - y0 * Math.sin(e);
		if (!direction) {
			x += g.elevatorPivot()[0];
			y += g.elevatorPivot()[1];
			z += g.elevatorPivot()[2];
		}
		if (g.turret() != null) {
			double a = Math.toRadians(turretYaw);
			double xr = x * Math.cos(a) + z * Math.sin(a);
			double zr = z * Math.cos(a) - x * Math.sin(a);
			x = xr;
			z = zr;
			if (!direction) {
				x += g.turretPivot()[0];
				y += g.turretPivot()[1];
				z += g.turretPivot()[2];
			}
		}
		return new Vec3(x, y, z);
	}

	public Vec3 railWorld(int i) {
		Vec3 l = railLocal(i, false);
		return toWorld(l.x, l.y, l.z);
	}

	public Vec3 railDirection(int i) {
		Vec3 l = railLocal(i, true);
		Vec3 f = forward();
		Vec3 r = right();
		return new Vec3(r.x * l.x + f.x * l.z, l.y, r.z * l.x + f.z * l.z).normalize();
	}

	/** Bearing of a world point relative to the vehicle's nose, degrees, positive = to the right. */
	private float relativeBearing(Vec3 to) {
		double dx = to.x - getX();
		double dz = to.z - getZ();
		float worldYaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		return Mth.wrapDegrees(worldYaw - getYRot());
	}

	// ------------------------------------------------------------------------------------------------
	// Riding

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!isAlive()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(com.stasdoto.airdefense.registry.ModItems.JERRYCAN)) {
			if (!level().isClientSide()) {
				pourFuel(player, stack);
			}
			return InteractionResult.SUCCESS;
		}
		com.stasdoto.airdefense.factory.Product product = com.stasdoto.airdefense.factory.Product.forItem(stack.getItem());
		if (product != null && vtype.ordnance != null && product.itemId.equals(vtype.ordnance.itemId)) {
			if (!level().isClientSide()) {
				int space = vtype.ordnance.count - getOrdnance();
				int items = Math.min(stack.getCount(), (space + product.units - 1) / product.units);
				if (items > 0) {
					addOrdnance(items * product.units);
					if (!player.getAbilities().instabuild) {
						stack.shrink(items);
					}
					level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0f, 0.8f);
				}
				player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.ordnance", getOrdnance(), vtype.ordnance.count));
			}
			return InteractionResult.SUCCESS;
		}
		if (product != null && product == com.stasdoto.airdefense.factory.Product.forVehicle(vtype)) {
			if (!level().isClientSide()) {
				loadByHand(player, stack, product);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.getItem() instanceof DesignatorItem) {
			if (level().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			BlockPos target = DesignatorItem.getTarget(stack);
			if (!vtype.isLauncher()) {
				player.sendOverlayMessage(status());
			} else if (target == null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.no_target"));
			} else {
				commandStrike(target, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(status());
			}
			return InteractionResult.SUCCESS;
		}
		if (getPassengers().size() >= vtype.geometry.seats().length || player.isPassenger()) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide()) {
			player.startRiding(this);
		}
		return InteractionResult.SUCCESS;
	}

	/** Jerrycans of petrol, 20 litres each, as many as fit. */
	private void pourFuel(Player player, ItemStack stack) {
		if (infiniteFuel()) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.fuel.infinite"));
			return;
		}
		int cans = 0;
		while (cans < stack.getCount() && vtype.fuelCapacity() - getFuel() >= 10) {
			refuel(JERRYCAN_LITRES);
			cans++;
		}
		if (cans == 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.fuel.full", (int) getFuel(), vtype.fuelCapacity()));
			return;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(cans);
			for (int i = 0; i < cans; i++) {
				player.getInventory().add(new ItemStack(com.stasdoto.airdefense.registry.ModItems.EMPTY_JERRYCAN));
			}
		}
		level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0f, 0.8f);
		player.sendOverlayMessage(Component.translatable("message.airdefense.fuel.poured", cans * (int) JERRYCAN_LITRES, (int) getFuel(), vtype.fuelCapacity()));
	}

	public static final float JERRYCAN_LITRES = 20f;

	/** Missiles (or ammunition boxes) handed over by a player go into the reserve. */
	private void loadByHand(Player player, ItemStack stack, com.stasdoto.airdefense.factory.Product product) {
		if (isUnlimited()) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.unlimited"));
			return;
		}
		int space = reserveSpace();
		int items = Math.min(stack.getCount(), (space + product.units - 1) / product.units);
		if (items <= 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.reserve_full", getReserve(), reserveCapacity()));
			return;
		}
		addReserve(items * product.units);
		if (!player.getAbilities().instabuild) {
			stack.shrink(items);
		}
		level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0f, 0.8f);
		player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.loaded", items, getReserve(), reserveCapacity()));
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < vtype.geometry.seats().length && isAlive();
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!level().isClientSide()) {
			if (getDriver() == null) {
				entityData.set(DATA_DRIVER, passenger.getId());
			} else if (getGunner() == null) {
				entityData.set(DATA_GUNNER, passenger.getId());
			}
			if (passenger instanceof Player p) {
				p.sendOverlayMessage(Component.translatable(isDriver(p) ? "message.airdefense.vehicle.driver" : "message.airdefense.vehicle.gunner"));
			}
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!level().isClientSide()) {
			if (entityData.get(DATA_DRIVER) == passenger.getId()) {
				entityData.set(DATA_DRIVER, -1);
			}
			if (entityData.get(DATA_GUNNER) == passenger.getId()) {
				entityData.set(DATA_GUNNER, -1);
			}
		}
		if (passenger instanceof Player && level().isClientSide()) {
			clientInput = Input.EMPTY;
		}
	}

	private int seatIndexOf(Entity passenger) {
		if (entityData.get(DATA_DRIVER) == passenger.getId()) {
			return 0;
		}
		if (entityData.get(DATA_GUNNER) == passenger.getId()) {
			return Math.min(1, vtype.geometry.seats().length - 1);
		}
		return Math.min(Math.max(0, getPassengers().indexOf(passenger)), vtype.geometry.seats().length - 1);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		VehicleGeometry.Seat seat = vtype.geometry.seats()[seatIndexOf(passenger)];
		Vec3 f = forward();
		Vec3 r = right();
		double x = seat.x();
		double z = seat.z();
		double y = seat.y();
		if (vtype.gunnerInTurret() && vtype.geometry.turret() != null && seatIndexOf(passenger) == 1) {
			// The commander stands in the turret hatch and turns with it.
			double a = Math.toRadians(turretYaw);
			double zt = z - vtype.geometry.turretPivot()[2];
			double xr = x * Math.cos(a) + zt * Math.sin(a);
			double zr = zt * Math.cos(a) - x * Math.sin(a);
			x = xr;
			z = zr + vtype.geometry.turretPivot()[2];
		}
		return new Vec3(r.x * x + f.x * z, y, r.z * x + f.z * z);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		VehicleGeometry.Seat seat = vtype.geometry.seats()[seatIndexOf(passenger)];
		double side = (vtype.geometry.width() / 2 + 0.9) * (seat.x() <= 0 ? -1 : 1);
		for (double dz : new double[]{0, -1.5, 1.5, -3, 3}) {
			Vec3 p = toWorld(side, 0, seat.z() + dz);
			for (int dy = 0; dy <= 2; dy++) {
				Vec3 q = p.add(0, dy, 0);
				AABB box = passenger.getBoundingBox().move(q.subtract(passenger.position()));
				if (level().noCollision(passenger, box) && !level().noCollision(passenger, box.move(0, -0.6, 0))) {
					return q;
				}
			}
		}
		return position().add(0, vtype.boxHeight + 0.2, 0);
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		Player driver = getDriver();
		return driver != null && driver.isAlive() ? driver : null;
	}

	/** Change seat between driver and gunner (if the other one is free). */
	public void switchSeat(Player player) {
		int id = player.getId();
		if (entityData.get(DATA_DRIVER) == id && getGunner() == null && vtype.geometry.seats().length > 1) {
			entityData.set(DATA_DRIVER, -1);
			entityData.set(DATA_GUNNER, id);
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.gunner"));
		} else if (entityData.get(DATA_GUNNER) == id && getDriver() == null) {
			entityData.set(DATA_GUNNER, -1);
			entityData.set(DATA_DRIVER, id);
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.driver"));
		} else {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.seat_taken"));
		}
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 input) {
		return Vec3.ZERO;
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		return 0;
	}

	// ------------------------------------------------------------------------------------------------
	// Driving physics (runs where the vehicle is simulated)

	// ------------------------------------------------------------------------------------------------
	// Driving by itself (1.25): a column of the world's countries drives along the roads, the troops on board get
	// out at the end; an AI gunner fights with the vehicle's weapon.

	@Nullable
	private List<Vec3> route;
	private int routeIndex;
	private float routeSpeed = 0.75f;
	private int stuckTicks;
	private int backingTicks;
	/** Times it has had to back off since the last point it reached (four, and it gives that point up). */
	private int stuckTries;
	/** Soldiers on board (they are only a number while they ride) and where they are going. */
	public int troops;
	@Nullable
	public BlockPos troopTarget;
	/** For the tests: columns that arrived, troops that got out. */
	public static int arrivals;
	public static int dismounted;
	/** A town's supply lorry: the delivery it carries (0 = none); after unloading it waits, then goes away. */
	public long cargoDelivery;
	private int unloadedTicks = -1;

	/** Drives along these points (no driver needed), at this share of its top speed. */
	public void drive(List<Vec3> waypoints, float speedShare) {
		route = new ArrayList<>(waypoints);
		routeIndex = 0;
		routeSpeed = speedShare;
		stuckTicks = 0;
		stuckTries = 0;
		if (!canDrive() && vtype.isLauncher()) {
			fold();
		}
	}

	public boolean driving() {
		return route != null;
	}

	/** The keys an AI driver would press: steer at the next point, ease off in bends, back out when stuck. */
	private Input autopilot() {
		if (route == null || routeIndex >= route.size() || !(level() instanceof ServerLevel level)) {
			return Input.EMPTY;
		}
		Vec3 to = route.get(routeIndex);
		double dx = to.x - getX();
		double dz = to.z - getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		boolean last = routeIndex == route.size() - 1;
		// Near the end the column bunches up: the ones behind stop where they can (sooner given up when blocked).
		boolean nearEnd = routeIndex >= route.size() - 2;
		if (dist < (last ? 6 : 8) || stuckTries >= (nearEnd ? 2 : 4) || last && dist < 14 && Math.abs(speed) < 0.02f && stuckTicks > 30) {
			// Reached (or, blocked again and again, given up and taken as reached: the men get out where it stands).
			stuckTries = 0;
			routeIndex++;
			if (routeIndex >= route.size()) {
				route = null;
				arrived(level);
				return new Input(false, false, false, false, true, false, false);
			}
			return autopilot();
		}
		float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float err = Mth.wrapDegrees(want - getYRot());
		float max = vtype.maxSpeed * routeSpeed;
		if (backingTicks > 0) {
			backingTicks--;
			return new Input(false, true, err > 0, err < 0, false, false, false);
		}
		// Stuck against something: back off a little and try again.
		if (Math.abs(speed) < 0.02f) {
			if (++stuckTicks > 50) {
				stuckTicks = 0;
				backingTicks = 25;
				stuckTries++;
			}
		} else {
			stuckTicks = 0;
		}
		boolean bend = Math.abs(err) > 30;
		float limit = bend ? max * 0.45f : last && dist < 25 ? max * 0.4f : max;
		boolean fwd = speed < limit && Math.abs(err) < 100;
		boolean brake = speed > limit * 1.15f;
		return new Input(fwd, false, err < -3, err > 3, brake, false, false);
	}

	/** At the end of the road: the troops get out and go on on foot. */
	private void arrived(ServerLevel level) {
		if (cargoDelivery != 0) {
			com.stasdoto.airdefense.nation.Arsenals.lorryArrived(level, this);
			unloadedTicks = 0;
			return;
		}
		arrivals++;
		if (troops <= 0) {
			return;
		}
		com.stasdoto.airdefense.nation.Politics p = com.stasdoto.airdefense.nation.Politics.get(level.getServer());
		com.stasdoto.airdefense.nation.Country c = p.country(country);
		Vec3 back = forward().scale(-(vtype.geometry.length() / 2 + 1.5));
		RandomSource r = level.getRandom();
		for (int i = 0; i < troops; i++) {
			Vec3 at = position().add(back).add(right().scale((i % 3 - 1) * 1.4)).add(forward().scale(-(i / 3) * 1.3));
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
			com.stasdoto.airdefense.nation.SoldierEntity e = com.stasdoto.airdefense.nation.SoldierEntity.create(level,
					com.stasdoto.airdefense.nation.SoldierEntity.SOLDIER, country, c == null ? 0 : c.color, home, new Vec3(at.x, y, at.z), r.nextInt());
			if (troopTarget != null) {
				e.orderTo(troopTarget);
			}
			level.addFreshEntity(e);
			dismounted++;
		}
		troops = 0;
	}

	/** An AI gunner (the world's countries' armour): picks the nearest enemy in sight and fires bursts at it. */
	private void aiGunner(ServerLevel level, Weapon w) {
		if (country < 0 || (tickCount + getId()) % 5 != 0) {
			return;
		}
		if (aiTarget == null || !aiTarget.isAlive() || aiTarget.distanceToSqr(this) > 140 * 140 || (tickCount + getId()) % 40 == 0) {
			aiTarget = null;
			double best = 140 * 140;
			for (Entity e : level.getEntities(this, getBoundingBox().inflate(140, 40, 140),
					e -> e.isAlive() && !e.isSpectator() && com.stasdoto.airdefense.nation.War.hostile(level, country, e))) {
				double d = e.distanceToSqr(this);
				if (d < best && sees(level, e)) {
					best = d;
					aiTarget = e;
				}
			}
		}
		if (aiTarget == null) {
			return;
		}
		Vec3 point = aiTarget.position().add(0, aiTarget.getBbHeight() * 0.5, 0);
		setTurretTarget(relativeBearing(point));
		Vec3 muzzle = railWorld(0);
		double h = Math.sqrt(Mth.square(point.x - getX()) + Mth.square(point.z - getZ()));
		double base = vtype.geometry.turretPivot()[1] + getY();
		setElevationTarget(Mth.clamp((float) Math.toDegrees(Math.atan2(point.y - Math.max(base, muzzle.y - 0.5), Math.max(1, h))), -8, w.maxElevation));
		float yawErr = Math.abs(Mth.wrapDegrees(turretYaw - getTurretTarget()));
		if (yawErr < 4 && gunCooldown == 0 && roundsLeft == 0 && getAmmo() > 0) {
			roundsLeft = Math.min(w.burst, getAmmo());
			roundTimer = 0;
			gunCooldown = w.reload + 10 + level.getRandom().nextInt(20);
		}
	}

	@Nullable
	private Entity aiTarget;

	private boolean sees(ServerLevel level, Entity e) {
		Vec3 from = position().add(0, vtype.geometry.height() * 0.8, 0);
		Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0);
		if (com.stasdoto.airdefense.fx.Smoke.blocks(level, from, to)) {
			return false;
		}
		return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/** Game time the smoke grenades are loaded again. */
	private long smokeReady;

	/** Does it carry smoke grenades (fighting vehicles with a turret, armoured cars)? */
	public boolean hasSmoke() {
		return vtype.isArmed() && vtype.geometry.turret() != null && !vtype.boat;
	}

	/**
	 * 1.26: smoke grenades - a fan of six clouds twenty-odd metres ahead of the turret, hanging for twenty seconds
	 * (half a minute to reload). Behind it nobody's guns see the vehicle.
	 */
	public void smoke(Player player) {
		if (!hasSmoke() || !(level() instanceof ServerLevel level) || player != shooter() && player != getDriver()) {
			return;
		}
		long now = level.getGameTime();
		if (now < smokeReady) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.smoke_reload", (int) ((smokeReady - now) / 20) + 1));
			return;
		}
		smokeReady = now + 600;
		float yaw = getYRot() + (vtype.geometry.turret() != null ? turretYaw : 0);
		for (int i = 0; i < 6; i++) {
			float a = (yaw + (i - 2.5f) * 14f) * Mth.DEG_TO_RAD;
			double d = 18 + (i % 2) * 5;
			Vec3 at = position().add(-Mth.sin(a) * d, 1.5, Mth.cos(a) * d);
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
			com.stasdoto.airdefense.fx.Smoke.lay(level, new Vec3(at.x, Math.max(at.y, top + 1.5), at.z), 5.5, 400);
		}
		com.stasdoto.airdefense.fx.Fx.send(level, com.stasdoto.airdefense.fx.FxPayload.LAUNCH, position().add(0, vtype.geometry.height(), 0), 0.4f,
				new Vec3(com.stasdoto.airdefense.fx.FxPayload.LAUNCH_SOUND_LIGHT, 0, 0));
		player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.smoke"));
	}

	@Override
	public void travel(Vec3 ignored) {
		Input in = level().isClientSide() ? clientInput : route != null && getControllingPassenger() == null ? autopilot() : Input.EMPTY;
		if (vtype.isAir()) {
			travelAir(in);
			return;
		}
		boolean wantsToMove = in.forward() || in.backward() || in.left() || in.right();
		if (!canDrive()) {
			if (wantsToMove && level().isClientSide() && stowRequestCooldown-- <= 0) {
				stowRequester.accept(this);
				stowRequestCooldown = 20;
			}
			in = Input.EMPTY;
		}
		if (outOfFuel()) {
			in = new Input(false, false, false, false, in.jump(), in.shift(), in.sprint());
		}
		boolean floating = vtype.boat && onWater();
		float max = vtype.maxSpeed * (vtype.boat ? (floating ? 1.0f : 0.06f) : isInWater() ? 0.35f : 1.0f);
		if (in.forward()) {
			speed = Math.min(max, speed + (speed < 0 ? vtype.accel * 3 : vtype.accel));
		} else if (in.backward()) {
			speed = Math.max(-max * 0.35f, speed - (speed > 0 ? vtype.accel * 3 : vtype.accel * 0.7f));
		} else {
			speed *= onGround() ? 0.965f : 0.99f;
			if (Math.abs(speed) < 0.004f) {
				speed = 0;
			}
		}
		if (in.jump()) {
			speed *= 0.86f;
		}
		float turnInput = in.left() ? -1 : in.right() ? 1 : 0;
		float yawRate;
		if (vtype.boat) {
			// A boat turns with its rudder: hardly at all standing still.
			float sp = Math.abs(speed) / vtype.maxSpeed;
			yawRate = turnInput * vtype.pivotTurn * (0.25f + 0.75f * sp) * (speed < -0.01f ? -1 : 1);
			steer = turnInput * 25;
		} else if (vtype.tracked()) {
			float sp = Math.abs(speed) / vtype.maxSpeed;
			yawRate = turnInput * vtype.pivotTurn * (1 - 0.45f * sp) * (speed < -0.01f ? -1 : 1);
			steer = turnInput * 20;
		} else {
			float target = turnInput * vtype.maxSteer;
			steer += Mth.clamp(target - steer, -3.5f, 3.5f);
			float wb = Math.max(2.5f, vtype.geometry.wheelbase());
			yawRate = (float) (speed * Math.tan(Math.toRadians(steer)) / wb * Mth.RAD_TO_DEG);
		}
		if (onGround() || isInWater() || floating) {
			setYRot(getYRot() + yawRate);
		}
		yBodyRot = yHeadRot = getYRot();

		Vec3 f = forward();
		double vy = getDeltaMovement().y;
		if (vtype.boat && submerged()) {
			// Floats up to its waterline.
			vy = Math.min(vy * 0.6 + 0.05, 0.12);
		} else if (floating) {
			vy = 0;
		} else if (isInWater()) {
			vy = Math.max(vy - 0.02, -0.15);
		} else if (onGround() && vy <= 0) {
			vy = -0.08;
		} else {
			vy = Math.max((vy - 0.08) * 0.98, -3.0);
		}
		Vec3 motion = new Vec3(f.x * speed, vy, f.z * speed);
		if (speed != 0 && blockedAt(Math.signum(speed), Math.abs(speed))) {
			speed = 0;
			motion = new Vec3(0, vy, 0);
		}
		setDeltaMovement(motion);
		move(MoverType.SELF, motion);
		if (horizontalCollision) {
			speed *= 0.3f;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Flying (arcade): helicopters hover and climb with Space, sink with Ctrl; planes go where the pilot looks.

	/** Engine power 0..1 (spools up with a pilot in the seat), plane throttle 0..1. */
	public float engine;
	public float throttle;

	private void travelAir(Input in) {
		Player pilot = getDriver();
		boolean piloted = pilot != null && !outOfFuel();
		engine = Mth.approach(engine, piloted ? 1 : 0, vtype.air == VehicleType.HELI ? 0.008f : 0.02f);
		Vec3 v = getDeltaMovement();
		if (vtype.air == VehicleType.HELI) {
			travelHeli(in, pilot, v);
		} else {
			travelPlane(in, pilot, piloted, v);
		}
		yBodyRot = yHeadRot = getYRot();
	}

	/** The plane's bank (degrees, right wing down is positive): it turns by banking (1.26). */
	public float planeRoll;

	/**
	 * A plane's flight (rebuilt in 1.26): the pilot looks where he wants to go and the plane goes there the way a
	 * plane does - it banks into the turn (A / D bank it by hand), pulls its nose round, levels out as the nose comes on
	 * target. W / S open and close the throttle; speed builds up and bleeds off (climbing costs speed, diving gives
	 * it), too slow and it stalls - the nose drops and it sinks. On the ground it rolls on its wheels and lifts off past
	 * the take-off speed.
	 */
	private void travelPlane(Input in, @Nullable Player pilot, boolean piloted, Vec3 v) {
		if (piloted && in.forward()) {
			throttle = Math.min(1, throttle + 0.012f);
		}
		if (in.backward() || !piloted) {
			throttle = Math.max(0, throttle - 0.018f);
		}
		boolean airborne = !onGround();
		float top = vtype.maxSpeed;
		float takeoff = top * 0.4f;
		float stall = top * 0.3f;
		double sp = v.length();
		// Thrust against drag (full throttle holds the top speed), gravity along the path.
		double thrust = throttle * engine * vtype.accel * 1.15;
		double drag = vtype.accel * 1.15 * (sp / top) * (sp / top);
		double slope = airborne ? Math.sin(Math.toRadians(getXRot())) * 0.045 : 0;
		sp = Math.max(0, sp + thrust - drag + slope);
		if (!airborne && throttle < 0.05f) {
			// Wheel brakes.
			sp *= 0.95;
		}
		if (in.jump() && airborne) {
			// Air brake.
			sp *= 0.985;
		}
		// How well the controls bite: little at low speed.
		float bite = (float) Mth.clamp(sp / (top * 0.55), 0.12, 1.0);
		float aimYaw = pilot != null ? pilot.getYRot() : getYRot();
		float aimPitch = pilot != null ? Mth.clamp(pilot.getXRot(), -60, 60) : 6;
		float yawErr = Mth.wrapDegrees(aimYaw - getYRot());
		float pitchErr = aimPitch - getXRot();
		// Bank into the turn (by hand with A / D), back to level as the nose comes round.
		float wantRoll = Mth.clamp(yawErr * 2.2f, -72, 72);
		if (in.left()) {
			wantRoll = -75;
		} else if (in.right()) {
			wantRoll = 75;
		}
		if (!airborne) {
			wantRoll = 0;
		}
		float rollRate = vtype.pivotTurn * 2.4f * bite;
		planeRoll += Mth.clamp(wantRoll - planeRoll, -rollRate, rollRate);
		// A banked plane turns: the steeper the bank and the slower it flies, the quicker.
		float yawRate = 0;
		if (airborne) {
			yawRate = (float) Math.toDegrees(Math.tan(Math.toRadians(planeRoll)) * 0.05 / Math.max(0.6, sp));
			yawRate = Mth.clamp(yawRate, -vtype.pivotTurn * 1.3f, vtype.pivotTurn * 1.3f) * bite;
			// A touch of rudder to settle the last few degrees.
			yawRate += Mth.clamp(yawErr * 0.06f, -0.4f, 0.4f) * bite;
		} else if (sp > 0.05) {
			// Nose wheel steering on the ground.
			yawRate = Mth.clamp(yawErr * 0.1f, -2.0f, 2.0f) * (float) Math.min(1, sp / 0.5);
		}
		setYRot(getYRot() + yawRate);
		// Pitch: towards where the pilot looks; banked hard it pulls the nose round rather than up.
		float pitchRate = vtype.pivotTurn * 0.75f * bite * (1 - 0.5f * Math.abs(planeRoll) / 75f);
		float pitch = getXRot() + Mth.clamp(pitchErr, -pitchRate, pitchRate);
		if (airborne && sp < stall) {
			// Stalled: the nose falls through.
			pitch = Math.min(45, pitch + 1.2f);
		}
		if (!airborne) {
			// Rolling: the nose comes up only past the take-off speed.
			pitch = sp < takeoff ? 0 : Mth.clamp(pitch, -12, 0);
		}
		setXRot(Mth.clamp(pitch, -80, 80));
		Vec3 motion = Vec3.directionFromRotation(getXRot(), getYRot()).scale(sp);
		if (airborne && sp < stall) {
			motion = motion.add(0, -0.08 - (stall - sp) * 0.5, 0);
		} else if (!airborne) {
			motion = new Vec3(motion.x, sp >= takeoff && getXRot() < -1 ? motion.y : Math.max(motion.y, -0.04), motion.z);
			if (sp < takeoff) {
				motion = new Vec3(motion.x, -0.04, motion.z);
			}
		}
		setDeltaMovement(motion);
		move(MoverType.SELF, motion);
		if (horizontalCollision && sp > top * 0.5) {
			// Flown into a hill or a house.
			if (level() instanceof ServerLevel level) {
				hurtServer(level, level.damageSources().flyIntoWall(), getMaxHealth());
			}
		}
		speed = (float) sp;
	}

	/**
	 * A helicopter's flight (1.25): it flies by tilting its rotor. W / S tip the nose down / up and it gathers speed
	 * forwards / backwards, A / D bank it and it slides sideways, the pilot's look turns it (the pedals), Space / Ctrl
	 * pull / lower the collective. The body leans and comes back level with some weight to it; speed builds up and
	 * dies away gradually, there is extra lift once it moves (translational lift) and a cushion of air near the ground.
	 * Let go of everything and it levels itself and holds its height. Without power it autorotates down.
	 */
	public float heliPitch;
	public float heliRoll;

	private void travelHeli(Input in, @Nullable Player pilot, Vec3 v) {
		boolean powered = engine >= 0.75f;
		float fwdIn = in.forward() ? 1 : in.backward() ? -0.7f : 0;
		float sideIn = in.left() ? -1 : in.right() ? 1 : 0;
		boolean grounded = onGround() && v.y <= 0.01;
		float wantPitch = grounded || !powered ? 0 : fwdIn * 22f;
		float wantRoll = grounded || !powered ? 0 : sideIn * 20f;
		if (!grounded && powered) {
			// Hands off the stick: the hover hold flares against the drift (nose up to stop, a bank against sideslip).
			double along = v.x * forward().x + v.z * forward().z;
			double across = v.x * right().x + v.z * right().z;
			if (fwdIn == 0) {
				wantPitch = (float) Mth.clamp(-along * 14, -15, 15);
			}
			if (sideIn == 0) {
				wantRoll = (float) Mth.clamp(-across * 14, -12, 12);
			}
		}
		// The body's attitude follows the stick with some weight (and a little more briskly back to level).
		float pr = wantPitch == 0 ? 1.4f : 1.0f;
		float rr = wantRoll == 0 ? 1.6f : 1.2f;
		heliPitch += Mth.clamp(wantPitch - heliPitch, -pr, pr);
		heliRoll += Mth.clamp(wantRoll - heliRoll, -rr, rr);
		// Heading: towards where the pilot looks, at the pedals' rate (faster when moving: the tail fin helps).
		if (pilot != null && powered && !grounded) {
			float want = Mth.wrapDegrees(pilot.getYRot() - getYRot());
			float rate = vtype.pivotTurn * (0.7f + 0.6f * (float) Math.min(1, Math.hypot(v.x, v.z) / vtype.maxSpeed));
			setYRot(getYRot() + Mth.clamp(want * 0.12f, -rate, rate) + heliRoll * 0.02f);
		}
		Vec3 f = forward();
		Vec3 r = right();
		double g = 0.04;
		double vx = v.x;
		double vz = v.z;
		double vy = v.y;
		if (powered) {
			// Tilted thrust pushes it the way it leans.
			double ax = Math.tan(Math.toRadians(heliPitch)) * g * engine;
			double as = Math.tan(Math.toRadians(heliRoll)) * g * engine;
			vx += f.x * ax + r.x * as;
			vz += f.z * ax + r.z * as;
			// Collective: climb / sink, else hold the height (the autopilot trims it).
			double climb = in.jump() ? 0.34 : in.sprint() ? -0.32 : 0;
			double h = Math.hypot(vx, vz);
			// Translational lift: moving, the rotor bites better (it rises a touch unless trimmed).
			double tl = Math.min(1, h / (vtype.maxSpeed * 0.5)) * 0.004;
			// Ground cushion: a little extra lift within a few blocks of the ground.
			double cushion = heightAboveGround() < 3 ? 0.006 : 0;
			vy += (climb - vy) * 0.055 + tl + cushion - (climb == 0 && vy > 0.02 ? 0.004 : 0);
		} else {
			// No power: it autorotates down, the forward speed bleeds off.
			vy = Math.max(vy - 0.028, -0.6);
			heliPitch *= 0.95f;
			heliRoll *= 0.95f;
		}
		// Drag: a little that grows with speed and more with its square, sized so that the nose fully down gives
		// the type's top speed (a Mi-24 about 150 km/h) after some ten seconds of building up.
		double h = Math.hypot(vx, vz);
		double top = Math.max(0.8, vtype.maxSpeed);
		double c1 = 0.003;
		double c2 = Math.max(0.0005, (Math.tan(Math.toRadians(22)) * g - c1 * top) / (top * top));
		double drag = c1 + c2 * h;
		vx -= vx * drag;
		vz -= vz * drag;
		if (grounded) {
			vx *= 0.55;
			vz *= 0.55;
			if (vy < 0) {
				vy = -0.04;
			}
		}
		Vec3 motion = new Vec3(vx, vy, vz);
		setDeltaMovement(motion);
		move(MoverType.SELF, motion);
		if (horizontalCollision) {
			setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
		}
		speed = (float) Math.hypot(vx, vz);
	}

	/** Blocks of air under it (up to 8). */
	private double heightAboveGround() {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= 8; i++) {
			m.set(getBlockX(), getBlockY() - i, getBlockZ());
			if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) {
				return getY() - (m.getY() + 1);
			}
		}
		return 9;
	}

	/** Boats: water right under the waterline. */
	public boolean onWater() {
		BlockPos below = BlockPos.containing(getX(), getY() - 0.3, getZ());
		return level().getFluidState(below).is(net.minecraft.tags.FluidTags.WATER);
	}

	/** Boats: sitting too low (the waterline is under water). */
	private boolean submerged() {
		BlockPos at = BlockPos.containing(getX(), getY() + 0.12, getZ());
		return level().getFluidState(at).is(net.minecraft.tags.FluidTags.WATER);
	}

	/** Burns fuel while it drives (where the vehicle is simulated; the server keeps the count). */
	private void burnFuel(double moved) {
		if (infiniteFuel() || level().isClientSide()) {
			return;
		}
		if (moved < 0.01 || moved > 10) {
			return;
		}
		// A full tank lasts twenty minutes at full speed.
		float perTick = vtype.fuelCapacity() / 24000f * (float) Math.min(1.5, moved / vtype.maxSpeed);
		setFuel(Math.max(0, getFuel() - perTick));
	}

	/** The collision box covers only the middle of the vehicle; this checks the nose (or tail) for walls and trees. */
	private boolean blockedAt(double dir, double ahead) {
		double half = vtype.geometry.length() / 2 - 0.4;
		Vec3 f = forward();
		Vec3 r = right();
		double w = vtype.geometry.width() / 2 - 0.25;
		double baseY = getY() + maxUpStep() + 0.05;
		double topY = getY() + Math.min(2.4, vtype.geometry.height() - 0.4);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (double side : new double[]{-w, 0, w}) {
			double px = getX() + f.x * dir * (half + ahead) + r.x * side;
			double pz = getZ() + f.z * dir * (half + ahead) + r.z * side;
			for (int y = Mth.floor(baseY); y <= Mth.floor(topY); y++) {
				m.set(Mth.floor(px), y, Mth.floor(pz));
				BlockState s = level().getBlockState(m);
				if (s.isAir()) {
					continue;
				}
				VoxelShape shape = s.getCollisionShape(level(), m);
				if (!shape.isEmpty() && y + shape.max(Direction.Axis.Y) > baseY) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------
	// Ticking

	@Override
	public void tick() {
		if (level().isClientSide()) {
			tickBoth();
			return;
		}
		long perf0 = System.nanoTime();
		tickBoth();
		com.stasdoto.airdefense.util.Perf.add(com.stasdoto.airdefense.util.Perf.VEHICLES, System.nanoTime() - perf0);
		com.stasdoto.airdefense.util.Perf.over("vehicle " + getVehicleType().id + " at " + blockPosition().toShortString(), perf0);
	}

	private void tickBoth() {
		if (!anglesInitialised) {
			elevation = elevationO = level().isClientSide() ? getElevationTarget() : elevation;
			turretYaw = turretYawO = level().isClientSide() ? getTurretTarget() : turretYaw;
			anglesInitialised = true;
		}
		super.tick();
		elevationO = elevation;
		turretYawO = turretYaw;
		roofOpenO = roofOpen;
		animateLauncher();
		if (level() instanceof ServerLevel server) {
			if (isAlive()) {
				serverLogic(server);
			}
		} else {
			clientVisuals();
		}
	}

	private void animateLauncher() {
		float elevTarget = getElevationTarget();
		boolean needRoof = vtype.geometry.openParts().length > 0;
		// The roof halves open before the erector rises and close after it is down.
		float roofTarget = needRoof && (elevTarget > 0.5f || elevation > 0.5f) ? 1 : 0;
		roofOpen = Mth.approach(roofOpen, roofTarget, 0.02f);
		float rate = vtype.elevationRate;
		if (rate > 0 && (!needRoof || roofOpen >= 0.99f || elevTarget < elevation)) {
			// Erectors slow down near the ends of their travel, like hydraulics do.
			float d = elevTarget - elevation;
			// Gun mounts track at full speed; the big hydraulic erectors slow down near the end.
			float step = vtype.hasGuns() ? rate : Math.min(rate, Math.max(rate * 0.25f, Math.abs(d) * 0.08f));
			elevation += Mth.clamp(d, -step, step);
		}
		if (vtype.turretRate > 0) {
			float d = Mth.wrapDegrees(getTurretTarget() - turretYaw);
			turretYaw = Mth.wrapDegrees(turretYaw + Mth.clamp(d, -vtype.turretRate, vtype.turretRate));
		}
	}

	/**
	 * Whose vehicle it is (1.25): -1 = the player's (or nobody's: it obeys whoever drives it), else a country of the
	 * world - a town's garrison; {@link #home} is its town, and a garrison does not keep its ground loaded (it only lives
	 * while somebody is near; the rest of the time its town's arsenal stands for it).
	 */
	public int country = -1;
	public int home = -1;
	public boolean garrison;

	private void serverLogic(ServerLevel level) {
		Vec3 pos = position();
		burnFuel(Math.sqrt(Mth.square(pos.x - lastServerPos.x) + Mth.square(pos.z - lastServerPos.z)));
		// Only driving counts as moving: a hop from a nearby blast or settling on the ground does not.
		double moved = Mth.square(pos.x - lastServerPos.x) + Mth.square(pos.z - lastServerPos.z);
		if (moved < 0.0025) {
			stationaryTicks++;
		} else {
			stationaryTicks = 0;
		}
		lastServerPos = pos;
		if (marchTicks > 0) {
			marchTicks--;
		}
		if (!garrison && (tickCount <= 1 || (tickCount + getId()) % 20 == 0)) {
			// Keeps its own ground loaded and running: visible and controllable on the tablet map from anywhere,
			// air defence keeps guarding while the player is far away.
			level.getChunkSource().addTicketWithRadius(ModTickets.VEHICLE, ChunkPos.containing(blockPosition()), 2);
		}
		if (route != null) {
			// On the road by itself: its ground keeps running (only while it drives).
			keepLoaded(level);
		} else if (unloadedTicks >= 0 && ++unloadedTicks % 20 == 0 && getPassengers().isEmpty()) {
			// An unloaded supply lorry drives off (goes away) once nobody is close enough to watch, or after five minutes.
			if (unloadedTicks > 6000 || unloadedTicks > 400 && level.getNearestPlayer(this, 40) == null) {
				discard();
				return;
			}
		}
		if (vtype.isLauncher()) {
			tickLauncher(level);
		} else if (vtype.isRadar()) {
			tickRadar(level);
		} else if (vtype.isAir()) {
			tickAir(level);
		} else if (vtype.isTruck()) {
			tickTruck(level);
		} else if (vtype.isArmed()) {
			tickArmed(level);
		} else {
			tickDefense(level);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Tanks, fighting vehicles, gunboats: the gun follows where the gunner (or a lone driver) looks

	private int gunCooldown;
	private int roundsLeft;
	private int roundTimer;
	private int ammoReload;
	private final List<Round> rounds = new ArrayList<>();

	/** A round on its way: lands after {@code ticks}. */
	private record Round(Vec3 at, @Nullable Entity hit, Weapon weapon, int[] ticks) {
	}

	/** Who aims: the gunner, or the driver when he is alone. */
	@Nullable
	public Player shooter() {
		Player g = getGunner();
		return g != null ? g : getDriver();
	}

	private void tickArmed(ServerLevel level) {
		Weapon w = vtype.weapon;
		setState(DEPLOYED);
		if (gunCooldown > 0) {
			gunCooldown--;
		}
		// Empty: the loader brings up rounds from the reserve.
		if (getAmmo() <= 0 && (isUnlimited() || getReserve() > 0)) {
			if (++ammoReload >= w.reload * 4) {
				ammoReload = 0;
				setAmmo(takeReserve(w.magazine));
			}
		}
		Player p = shooter();
		if (p == null && country >= 0) {
			aiGunner(level, w);
		}
		if (p != null) {
			Vec3 eye = p.getEyePosition();
			Vec3 end = eye.add(p.getLookAngle().scale(400));
			net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, end,
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
			Vec3 point = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
			setTurretTarget(relativeBearing(point));
			Vec3 muzzle = railWorld(0);
			double h = Math.sqrt(Mth.square(point.x - getX()) + Mth.square(point.z - getZ()));
			double base = vtype.geometry.turretPivot()[1] + getY();
			float elev = (float) Math.toDegrees(Math.atan2(point.y - Math.max(base, muzzle.y - 0.5), Math.max(1, h)));
			setElevationTarget(Mth.clamp(elev, -8, w.maxElevation));
		}
		if (roundsLeft > 0 && --roundTimer <= 0) {
			fireRound(level, w);
			roundsLeft--;
			roundTimer = 3;
		}
		for (Iterator<Round> it = rounds.iterator(); it.hasNext(); ) {
			Round r = it.next();
			if (--r.ticks[0] <= 0) {
				it.remove();
				impact(level, r);
			}
		}
	}

	/** Trigger from the shooter's seat. */
	public void armedFire(Player player) {
		if (player != (vtype.isAir() ? getDriver() : shooter()) || !(level() instanceof ServerLevel level) || gunCooldown > 0 || roundsLeft > 0) {
			return;
		}
		Weapon w = vtype.weapon;
		if (getAmmo() <= 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.empty"));
			gunCooldown = 20;
			return;
		}
		roundsLeft = Math.min(w.burst, getAmmo());
		roundTimer = 0;
		gunCooldown = w.reload;
		if (roundsLeft > 0) {
			fireRound(level, w);
			roundsLeft--;
			roundTimer = 3;
		}
	}

	// --- Logistics trucks: carry fuel or ammunition, fill up at a town, unload at another, serve vehicles on the spot ---

	private static final EntityDataAccessor<Integer> DATA_CARGO = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	public static final int TRUCK_SUPPLY = 0;
	public static final int TRUCK_LOAD = 1;
	public static final int TRUCK_UNLOAD = 2;
	/** Loading something else (1.23): oil (fuel truck) or food (supply truck); weapons (supply truck). */
	public static final int TRUCK_LOAD_OTHER = 3;
	public static final int TRUCK_LOAD_ARMS = 4;
	private static final EntityDataAccessor<Integer> DATA_CARGO_KIND = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);

	public int getCargo() {
		return entityData.get(DATA_CARGO);
	}

	/** What the truck carries: a {@link com.stasdoto.airdefense.nation.VillageEconomy} kind (fuel or ammunition unless loaded with something else). */
	public int getCargoKind() {
		int k = entityData.get(DATA_CARGO_KIND);
		return k >= 0 ? k : defaultCargo();
	}

	private int defaultCargo() {
		return vtype.cargo == 1 ? com.stasdoto.airdefense.nation.VillageEconomy.FUEL : com.stasdoto.airdefense.nation.VillageEconomy.AMMO;
	}

	public void setCargoKind(int kind) {
		entityData.set(DATA_CARGO_KIND, kind);
	}

	/** How many modes the truck has: serve, load, unload, load oil (or food), load weapons. */
	public int truckModes() {
		return vtype.cargo == 1 ? 4 : 5;
	}

	/** The language key suffix of the truck's mode. */
	public String truckModeKey() {
		int m = getMode();
		if (m == TRUCK_LOAD_OTHER) {
			return vtype.cargo == 1 ? "mode_3_oil" : "mode_3_food";
		}
		return m == TRUCK_LOAD_ARMS ? "mode_4_arms" : "mode_" + m;
	}

	/** What a loading mode takes on board, -1 if it is not one. */
	private int loadKind(int mode) {
		return switch (mode) {
			case TRUCK_LOAD -> defaultCargo();
			case TRUCK_LOAD_OTHER -> vtype.cargo == 1 ? com.stasdoto.airdefense.nation.VillageEconomy.OIL : com.stasdoto.airdefense.nation.VillageEconomy.FOOD;
			case TRUCK_LOAD_ARMS -> com.stasdoto.airdefense.nation.VillageEconomy.ARMS;
			default -> -1;
		};
	}

	public void setCargo(int amount) {
		entityData.set(DATA_CARGO, Math.max(0, Math.min(vtype.cargoCapacity, amount)));
	}

	private void tickTruck(ServerLevel level) {
		setState(DEPLOYED);
		if ((tickCount + getId()) % 20 != 0) {
			return;
		}
		boolean fuel = vtype.cargo == 1;
		int mode = getMode();
		if (getCargo() <= 0 && entityData.get(DATA_CARGO_KIND) >= 0) {
			setCargoKind(-1);
		}
		if (mode == TRUCK_SUPPLY && getCargoKind() != defaultCargo()) {
			return;
		}
		if (mode == TRUCK_SUPPLY) {
			// Vehicles round the truck get what it carries.
			for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, getBoundingBox().inflate(12, 6, 12), v -> v != this && v.isAlive())) {
				if (getCargo() <= 0) {
					break;
				}
				if (fuel) {
					if (!v.infiniteFuel()) {
						int give = (int) Math.min(Math.min(60, getCargo()), v.vtype.fuelCapacity() - v.getFuel());
						if (give > 0) {
							v.refuel(give);
							setCargo(getCargo() - give);
						}
					}
				} else {
					com.stasdoto.airdefense.nation.VillageEconomy pool = new com.stasdoto.airdefense.nation.VillageEconomy();
					pool.stock[com.stasdoto.airdefense.nation.VillageEconomy.AMMO] = getCargo();
					com.stasdoto.airdefense.nation.Supply.rearm(pool, v);
					setCargo(pool.stock[com.stasdoto.airdefense.nation.VillageEconomy.AMMO]);
				}
			}
			return;
		}
		// Load or unload at a town of a player (any building of it within 40 blocks, or its centre).
		com.stasdoto.airdefense.nation.Politics p = com.stasdoto.airdefense.nation.Politics.get(level.getServer());
		com.stasdoto.airdefense.nation.Settlement town = com.stasdoto.airdefense.nation.Supply.townNear(p, blockPosition());
		if (town == null) {
			return;
		}
		int load = loadKind(mode);
		int kind = load >= 0 ? load : getCargoKind();
		int[] stock = town.eco.stock;
		int rate = fuel ? 250 : 25;
		if (load >= 0) {
			if (getCargo() > 0 && getCargoKind() != load) {
				return;
			}
			int k = Math.min(rate, Math.min(stock[kind], vtype.cargoCapacity - getCargo()));
			if (k > 0) {
				stock[kind] -= k;
				setCargo(getCargo() + k);
				setCargoKind(kind);
				p.setDirty();
			}
		} else {
			int k = Math.min(rate, Math.min(getCargo(), town.eco.capOf(kind) - stock[kind]));
			if (k > 0) {
				stock[kind] += k;
				setCargo(getCargo() - k);
				p.setDirty();
			}
		}
	}

	public void setTruckMode(int mode) {
		if (vtype.isTruck()) {
			setMode(Math.floorMod(mode, truckModes()));
		}
	}

	private void cycleTruckMode(Player player) {
		int mode = (getMode() + 1) % truckModes();
		setMode(mode);
		player.sendOverlayMessage(Component.translatable("message.airdefense.truck." + truckModeKey()));
	}

	// --- Aircraft weapons ---

	private static final EntityDataAccessor<Integer> DATA_ORDNANCE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private int ordnanceLeft;
	private int ordnanceTimer;
	private int ordnanceCooldown;
	private int ordnanceIndex;

	public int getOrdnance() {
		return entityData.get(DATA_ORDNANCE);
	}

	public void addOrdnance(int n) {
		if (vtype.ordnance != null) {
			entityData.set(DATA_ORDNANCE, Math.min(vtype.ordnance.count, getOrdnance() + n));
		}
	}

	/** Where the aircraft's gun and rockets point: helicopters where the pilot looks, planes straight ahead. */
	private Vec3 aimDirection() {
		Player p = getDriver();
		if (vtype.air == VehicleType.HELI && p != null) {
			return p.getLookAngle();
		}
		return Vec3.directionFromRotation(getXRot(), getYRot());
	}

	private Vec3 nose() {
		return position().add(0, vtype.geometry.height() * 0.4, 0).add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(vtype.geometry.length() / 2 + 0.5));
	}

	private void tickAir(ServerLevel level) {
		setState(getDriver() != null ? DEPLOYED : STOWED);
		if (gunCooldown > 0) {
			gunCooldown--;
		}
		if (ordnanceCooldown > 0) {
			ordnanceCooldown--;
		}
		if (vtype.weapon != null && getAmmo() <= 0 && (isUnlimited() || getReserve() > 0) && ++ammoReload >= 200) {
			ammoReload = 0;
			setAmmo(takeReserve(vtype.weapon.magazine));
		}
		if (roundsLeft > 0 && --roundTimer <= 0 && vtype.weapon != null) {
			fireRound(level, vtype.weapon);
			roundsLeft--;
			roundTimer = 3;
		}
		if (ordnanceLeft > 0 && --ordnanceTimer <= 0) {
			releaseOne(level);
			ordnanceLeft--;
			ordnanceTimer = vtype.ordnance.interval;
		}
		for (Iterator<Round> it = rounds.iterator(); it.hasNext(); ) {
			Round r = it.next();
			if (--r.ticks[0] <= 0) {
				it.remove();
				impact(level, r);
			}
		}
	}

	/** R in the pilot's seat (or the left button when there is no gun): rockets, a bomb, a missile. */
	public void releaseOrdnance(Player player) {
		Ordnance o = vtype.ordnance;
		if (o == null || player != getDriver() || ordnanceCooldown > 0 || ordnanceLeft > 0 || !(level() instanceof ServerLevel level)) {
			return;
		}
		if (getOrdnance() <= 0 && !isUnlimited()) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.empty"));
			ordnanceCooldown = 20;
			return;
		}
		if (o == Ordnance.AIM9 && airTarget(player) == null) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.no_lock"));
			ordnanceCooldown = 10;
			return;
		}
		ordnanceLeft = isUnlimited() ? o.salvo : Math.min(o.salvo, getOrdnance());
		ordnanceTimer = 0;
		ordnanceCooldown = o.cooldown;
		releaseOne(level);
		ordnanceLeft--;
		ordnanceTimer = o.interval;
	}

	/** The drone or missile nearest the middle of the pilot's view (within 15 degrees, 300 blocks). */
	@Nullable
	private MissileEntity airTarget(Player p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		MissileEntity best = null;
		double bestCos = Math.cos(Math.toRadians(15));
		for (MissileEntity m : MissileEntity.find(level(), new AABB(eye, eye).inflate(300), m -> m.isAlive() && m.getMissileType().threat)) {
			Vec3 to = m.position().subtract(eye);
			double d = to.length();
			if (d < 5 || d > 300) {
				continue;
			}
			double c = to.scale(1 / d).dot(look);
			if (c > bestCos) {
				bestCos = c;
				best = m;
			}
		}
		return best;
	}

	private void releaseOne(ServerLevel level) {
		Ordnance o = vtype.ordnance;
		if (o == null) {
			return;
		}
		if (!isUnlimited()) {
			if (getOrdnance() <= 0) {
				ordnanceLeft = 0;
				return;
			}
			entityData.set(DATA_ORDNANCE, getOrdnance() - 1);
		}
		Player p = getDriver();
		Vec3 side = right().scale((ordnanceIndex++ % 2 == 0 ? 1 : -1) * (vtype.geometry.width() / 2 + 1.0));
		switch (o) {
			case S8 -> {
				Vec3 dir = aimDirection();
				Vec3 from = position().add(0, vtype.geometry.height() * 0.4, 0).add(side).add(dir.scale(vtype.geometry.length() / 2));
				MissileEntity.launchWithVelocity(level, o.missile, from, dir.scale(2.0).add(getDeltaMovement()), p, this);
				Effects.launchBlast(level, from, o.missile);
			}
			case FAB250 -> {
				Vec3 from = position().add(side.scale(0.5)).add(0, -0.3, 0);
				MissileEntity.launchWithVelocity(level, o.missile, from, getDeltaMovement().add(0, -0.1, 0), p, this);
			}
			case AIM9 -> {
				MissileEntity target = p != null ? airTarget(p) : null;
				Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot());
				Vec3 from = position().add(0, vtype.geometry.height() * 0.4, 0).add(side).add(dir.scale(2));
				MissileEntity.launchInterceptor(level, o.missile, from, dir, target);
				level.playSound(null, from.x, from.y, from.z, ModSounds.RADAR_LOCK, SoundSource.PLAYERS, 1.0f, 1.2f);
			}
		}
	}

	private void fireRound(ServerLevel level, Weapon w) {
		if (getAmmo() <= 0) {
			roundsLeft = 0;
			return;
		}
		setAmmo(getAmmo() - 1);
		Vec3 muzzle = vtype.isAir() ? nose() : railWorld(0);
		Vec3 dir = vtype.isAir() ? aimDirection() : railDirection(0);
		RandomSource r = level.getRandom();
		double spread = w.cannon() ? 0.002 : 0.008;
		dir = dir.add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread).normalize();
		double reach = w.cannon() ? 450 : 300;
		Vec3 end = muzzle.add(dir.scale(reach));
		net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(muzzle, end,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			end = block.getLocation();
		}
		net.minecraft.world.phys.EntityHitResult ent = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, this,
				muzzle, end, new AABB(muzzle, end).inflate(1.0),
				e -> e != this && e.isAlive() && !e.isSpectator() && e.isPickable() && !hasPassenger(e) && !(e instanceof MissileEntity m && !m.getMissileType().threat),
				0.3f);
		Entity hit = null;
		if (ent != null) {
			end = ent.getLocation();
			hit = ent.getEntity();
		}
		double dist = end.distanceTo(muzzle);
		rounds.add(new Round(end, hit, w, new int[]{Math.max(1, (int) Math.round(dist / w.speed))}));
		Effects.tracer(level, muzzle, end, (float) w.speed);
		if (w.cannon() && !vtype.isAir()) {
			com.stasdoto.airdefense.fx.Fx.send(level, com.stasdoto.airdefense.fx.FxPayload.LAUNCH, muzzle.add(dir.scale(0.5)), 1.4f,
					new Vec3(com.stasdoto.airdefense.fx.FxPayload.LAUNCH_SOUND_HEAVY, 0, 0));
			// The recoil rocks the vehicle back a little.
			tiltPitch -= 2.5f;
		} else {
			Effects.gunBurst(level, muzzle);
		}
	}

	private void impact(ServerLevel level, Round r) {
		Weapon w = r.weapon();
		DamageSource source = level.damageSources().explosion(this, shooter());
		Entity hit = r.hit();
		if (hit instanceof VehicleEntity v && v.isAlive()) {
			v.hurtServer(level, source, w.vehicleDamage);
		} else if (hit instanceof MissileEntity m) {
			m.hurtServer(level, level.damageSources().generic(), 2f);
		} else if (hit != null && hit.isAlive()) {
			hit.hurtServer(level, w.blast > 0 ? source : level.damageSources().mobAttack(this), w.livingDamage);
		}
		if (w.blast > 0) {
			Vec3 at = r.at();
			level.explode(this, source, null, at.x, at.y, at.z, w.blast, false,
					w.breaksBlocks ? net.minecraft.world.level.Level.ExplosionInteraction.TNT : net.minecraft.world.level.Level.ExplosionInteraction.NONE,
					ModParticles.GLOW, ModParticles.GLOW, net.minecraft.util.random.WeightedList.of(),
					net.minecraft.core.Holder.direct(ModSounds.SILENT));
			com.stasdoto.airdefense.fx.Fx.send(level, com.stasdoto.airdefense.fx.FxPayload.GROUND_IMPACT, at, w.blast, new Vec3(0, 1, 1));
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Radar stations

	/** Switched on and standing still: the antenna goes up (or starts turning) and the radar reports to the network. */
	private void tickRadar(ServerLevel level) {
		boolean onTheMove = marchTicks > 0 || stationaryTicks < DEPLOY_STILL_TICKS;
		boolean active = getMode() != MODE_OFF && !onTheMove;
		setState(active ? DEPLOYED : STOWED);
		VehicleGeometry.Geometry g = vtype.geometry;
		setElevationTarget(active ? g.deployElevation() : g.fixedElevation());
		if (active && Math.abs(elevation - getElevationTarget()) < 2) {
			RadarNetwork.report(level, getId(), position().add(0, g.height() * 0.85, 0), getYRot(), vtype.radar);
		} else {
			RadarNetwork.remove(level, getId());
		}
	}

	/** Whether this radar is up and working (server). */
	public boolean radarWorking() {
		return vtype.isRadar() && getState() == DEPLOYED && Math.abs(elevation - vtype.geometry.deployElevation()) < 2;
	}

	// ------------------------------------------------------------------------------------------------
	// Strike launchers

	public boolean commandStrike(BlockPos target, @Nullable Player player) {
		return commandStrike(target, player, null);
	}

	/** Launch with a flight task (height, speed, route, how many, camera) for drones and cruise missiles. */
	public boolean commandStrike(BlockPos target, @Nullable Player player, @Nullable com.stasdoto.airdefense.drone.FlightPlan flightPlan) {
		LauncherType type = vtype.launcher;
		if (type == null || !isAlive()) {
			return false;
		}
		if (strikePending || salvoLeft > 0 || cooldown > 0) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.reloading", (cooldown + salvoLeft * type.interval) / 20 + 1));
			}
			return false;
		}
		double dist = Math.sqrt(target.distToCenterSqr(position()));
		if (dist < MIN_STRIKE_DISTANCE) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.too_close", MIN_STRIKE_DISTANCE));
			}
			return false;
		}
		if (dist > type.maxRange) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.too_far", (int) dist, type.maxRange));
			}
			return false;
		}
		if (getLoadedMask() == 0) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.empty"));
			}
			return false;
		}
		strikeTarget = target;
		strikePending = true;
		plan = flightPlan;
		planViewer = flightPlan != null && flightPlan.camera() && player != null ? player.getUUID() : null;
		salvoIndex = 0;
		speed = 0;
		setState(DEPLOYED);
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.deploying", target.getX(), target.getY(), target.getZ(), (int) dist));
		}
		return true;
	}

	private void tickLauncher(ServerLevel level) {
		LauncherType type = vtype.launcher;
		if (cooldown > 0) {
			cooldown--;
		}
		if (strikePending || salvoLeft > 0) {
			keepLoaded(level);
			aimAt(strikeTarget);
			if (strikePending && aimed()) {
				strikePending = false;
				int want = plan != null && plan.count() > 0 ? plan.count() : type.salvo;
				salvoLeft = Math.min(want, Integer.bitCount(getLoadedMask()));
				salvoTimer = 0;
			}
			if (salvoLeft > 0 && --salvoTimer <= 0) {
				fireStrike(level);
				salvoLeft--;
				salvoTimer = type.interval;
				if (salvoLeft == 0) {
					cooldown = type.cooldown;
					stowTimer = 50;
					reloadPending = true;
				}
			}
		} else if (stowTimer > 0) {
			stowTimer--;
		} else if (getState() == DEPLOYED && getElevationTarget() > 0 && marchTicks == 0 && strikeTarget == null) {
			// Manually raised: stays up until folded again.
		} else {
			fold();
		}
		if (reloadPending && cooldown == 0 && isFolded()) {
			reloadPending = false;
			reloadRails();
		} else if (!reloadPending && cooldown == 0 && !strikePending && salvoLeft == 0 && isFolded() && !isUnlimited()
				&& getReserve() > 0 && getLoadedMask() != fullMask() && tickCount % 20 == 0) {
			// Missiles arrived after the salvo: load them.
			reloadRails();
		}
	}

	private void fold() {
		setState(STOWED);
		setElevationTarget(vtype.geometry.fixedElevation());
		setTurretTarget(0);
		strikeTarget = null;
	}

	/** Turret towards the target, launcher up to its firing angle. */
	private void aimAt(@Nullable BlockPos target) {
		VehicleGeometry.Geometry g = vtype.geometry;
		if (target == null) {
			return;
		}
		Vec3 t = Vec3.atCenterOf(target);
		if (g.turret() != null) {
			setTurretTarget(relativeBearing(t));
		}
		if (vtype.elevationRate > 0) {
			float elev = g.deployElevation();
			if (vtype.launcher != null && vtype.launcher.missile.kind == MissileType.Kind.ROCKET) {
				// MLRS: point the pod along the start of the rocket's ballistic arc.
				double d = Math.sqrt(Mth.square(t.x - getX()) + Mth.square(t.z - getZ()));
				double apex = Mth.clamp(d * 0.45, 45, 260);
				elev = (float) Math.toDegrees(Math.atan2((t.y - getY()) + 4 * apex, d));
				elev = Mth.clamp(elev, 20, 60);
			}
			setElevationTarget(elev);
		}
	}

	private boolean aimed() {
		return Math.abs(elevation - getElevationTarget()) < 0.6f
				&& Math.abs(Mth.wrapDegrees(turretYaw - getTurretTarget())) < 1.0f
				&& (vtype.geometry.openParts().length == 0 || roofOpen > 0.99f);
	}

	private int nextLoadedRail() {
		int mask = getLoadedMask();
		for (int i = 0; i < vtype.rails(); i++) {
			if ((mask & (1 << i)) != 0) {
				return i;
			}
		}
		return -1;
	}

	private void fireStrike(ServerLevel level) {
		LauncherType type = vtype.launcher;
		int rail = nextLoadedRail();
		if (rail < 0 || strikeTarget == null) {
			salvoLeft = 0;
			return;
		}
		Vec3 from = railWorld(rail);
		Vec3 dir = railDirection(rail);
		RandomSource r = level.getRandom();
		Vec3 aim = new Vec3(strikeTarget.getX() + 0.5 + r.nextGaussian() * type.spread, strikeTarget.getY() + 1.0,
				strikeTarget.getZ() + 0.5 + r.nextGaussian() * type.spread);
		// A Shahed salvo always has a few Gerbera decoys in it: cheap foam drones meant to soak up air defence.
		MissileType missile = type.missile == MissileType.SHAHED && r.nextFloat() < 0.35f ? MissileType.GERBERA : type.missile;
		// The one the player watches is always a real Shahed.
		boolean watched = plan != null && planViewer != null && salvoIndex == 0;
		if (watched) {
			missile = type.missile;
		}
		MissileEntity m = MissileEntity.launchStrike(level, missile, from, aim, forward(), dir);
		m.setCountry(country);
		MissileType.Kind kind = missile.kind;
		if (plan != null && (kind == MissileType.Kind.DRONE || kind == MissileType.Kind.CRUISE)) {
			m.applyPlan(plan, salvoIndex % 2 == 0);
		}
		if (watched && level.getServer().getPlayerList().getPlayer(planViewer) instanceof net.minecraft.server.level.ServerPlayer viewer) {
			com.stasdoto.airdefense.drone.DroneCam.start(viewer, m);
		}
		salvoIndex++;
		setLoadedMask(getLoadedMask() & ~(1 << rail));
		setAmmo(Math.max(0, getAmmo() - 1));
		Effects.launchBlast(level, from.subtract(dir.scale(2.5)), type.missile);
	}

	private void keepLoaded(ServerLevel level) {
		if (tickCount % 10 == 0) {
			level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(blockPosition()), 3);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Air defence

	private void tickDefense(ServerLevel level) {
		DefenseType type = vtype.defense;
		boolean gun = type.gunOnly();
		boolean hybrid = type.hybrid();
		if (reloadTimer > 0 && --reloadTimer == 0) {
			reloadMagazine();
		}
		if (getAmmo() <= 0 && reloadTimer == 0 && (isUnlimited() || getReserve() > 0)) {
			// Empty, and missiles have arrived: start reloading.
			reloadTimer = type.reload;
		}
		if (fireTimer > 0) {
			fireTimer--;
		}
		if (sirenTimer > 0) {
			sirenTimer--;
		}
		tickShells(level);
		if ((tickCount + getId()) % 20 == 0) {
			radarLinked = !gun && RadarNetwork.linked(level, position());
		}
		boolean onTheMove = marchTicks > 0 || (!gun && stationaryTicks < DEPLOY_STILL_TICKS);
		boolean active = getMode() != MODE_OFF && !onTheMove;
		if (tickCount % 40 == 0 && com.stasdoto.airdefense.missile.MissileStats.debug()) {
			Vec3 r0 = position().add(0, 3.0, 0);
			double rr = type.range;
			List<MissileEntity> near = MissileEntity.find(level, new AABB(r0, r0).inflate(rr), m -> m.isAlive() && m.getMissileType().threat);
			com.stasdoto.airdefense.missile.MissileStats.log("AD {} active={} moving={} still={} march={} mode={} ammo={} fireTimer={} tracked={} threats in range {}{}", vtype, active, onTheMove,
					stationaryTicks, marchTicks, getMode(), getAmmo(), fireTimer, tracked == null ? "-" : tracked.getMissileType(), near.size(),
					near.isEmpty() ? "" : " first engage=" + canEngage(type, near.getFirst(), r0) + " engagedBy=" + near.getFirst().getEngagedBy());
		}
		setState(active ? DEPLOYED : STOWED);
		if (!active) {
			tracked = null;
			burstLeft = 0;
			setElevationTarget(0);
			setTurretTarget(0);
			return;
		}
		VehicleGeometry.Geometry g = vtype.geometry;
		if (!gun && !hybrid) {
			setElevationTarget(g.deployElevation());
		}
		if (getMode() == MODE_MANUAL) {
			tickManual(level, type, gun);
			return;
		}
		Vec3 radar = position().add(0, 3.0, 0);
		// Hold the current target instead of flicking between targets: the guns and turret need time to settle.
		if (tracked != null && !canEngage(type, tracked, radar)) {
			tracked = null;
		}
		// Missile batteries: once enough interceptors are on their way to this one, move on to the next.
		if (tracked != null && !gun && tracked.getEngagedBy() >= type.shotsPerTarget(tracked.getMissileType().kind)) {
			tracked = null;
		}
		// ...and a pure missile battery leaves a target to a gun that is already firing at it.
		if (tracked != null && !gun && !hybrid && tracked.gunEngaged(level.getGameTime())) {
			tracked = null;
		}
		if (tracked == null) {
			burstLeft = 0;
			if ((tickCount + getId()) % 3 == 0) {
				tracked = pickThreat(level, type, radar);
			}
		} else if (gun && (tickCount + getId()) % 10 == 0) {
			// ...unless something much closer turns up (a drone diving at us).
			MissileEntity best = pickThreat(level, type, radar);
			if (best != null && best != tracked && best.distanceToSqr(radar) < tracked.distanceToSqr(radar) * 0.36) {
				tracked = best;
				burstLeft = 0;
			}
		}
		if (tracked == null) {
			if (gun || hybrid) {
				setElevationTarget(10);
			}
			return;
		}
		if (sirenTimer == 0) {
			// The air defence is firing: the towns around sound the air raid alert (the vehicle itself has no siren).
			sirenTimer = 130;
			com.stasdoto.airdefense.siren.Sirens.autoAlert(level, position(), 260);
		}
		if (gun) {
			tickGun(level, type, tracked);
			return;
		}
		if (hybrid) {
			// Close in the guns do the work; further out the missiles - the whole mount points at the target either way.
			double d = tracked.distanceTo(this);
			if (d < type.gunRange && tracked.getMissileType().kind != MissileType.Kind.BALLISTIC) {
				tickGun(level, type, tracked);
				return;
			}
			burstLeft = 0;
			Vec3 muzzle = position().add(0, 2.3, 0);
			Vec3 at = tracked.position();
			double h = Math.sqrt(Mth.square(at.x - getX()) + Mth.square(at.z - getZ()));
			setElevationTarget((float) Mth.clamp(Math.toDegrees(Math.atan2(at.y - muzzle.y, h)), 0, 80));
		}
		if (g.turret() != null) {
			// The launcher turns towards the threat, but missiles do not need it to: they turn by themselves after launch.
			setTurretTarget(relativeBearing(tracked.position()));
		}
		if (getMode() != MODE_AUTO || fireTimer > 0 || getAmmo() <= 0 || elevationLagging()) {
			return;
		}
		fireInterceptor(level, type, tracked);
		// A second interceptor for the same target goes out right after the first (ripple fire), the next target waits.
		boolean another = tracked.getEngagedBy() < type.shotsPerTarget(tracked.getMissileType().kind);
		fireTimer = another ? 6 : type.interval;
		setAmmo(getAmmo() - 1);
		if (getAmmo() <= 0) {
			reloadTimer = type.reload;
		}
	}

	private boolean elevationLagging() {
		return Math.abs(elevation - getElevationTarget()) > (vtype.hasGuns() ? GUN_ELEVATION_TOLERANCE : 1.5f);
	}

	@Nullable
	private MissileEntity pickThreat(ServerLevel level, DefenseType type, Vec3 radar) {
		double range = type.range * (radarLinked ? RADAR_RANGE_BONUS : 1.0);
		AABB box = new AABB(radar.x - range, radar.y - range, radar.z - range, radar.x + range, radar.y + range, radar.z + range);
		long now = level.getGameTime();
		// Targets flying close together are one blip for the batteries: one interceptor goes at the group (its blast
		// may take both); the next one only if something is left (a ballistic missile is always its own target).
		List<MissileEntity> engaged = type.interceptor == null ? List.of()
				: MissileEntity.find(level, box, m -> m.isAlive() && m.getMissileType().threat && m.getEngagedBy() > 0);
		List<MissileEntity> threats = MissileEntity.find(level, box,
				m -> canEngage(type, m, radar)
						&& (type.interceptor == null || m.getEngagedBy() < type.shotsPerTarget(m.getMissileType().kind)
						&& (type.gunOnly() || type.hybrid() || !m.gunEngaged(now))
						&& (m.getMissileType().kind == MissileType.Kind.BALLISTIC || !nearEngaged(m, engaged))));
		return threats.stream()
				.min(Comparator.comparingDouble((MissileEntity m) -> type.priority(m.getMissileType().kind) * 1e6 + m.distanceToSqr(radar)))
				.orElse(null);
	}

	/** Another threat within a few blocks of this one already has an interceptor on its way. */
	private static boolean nearEngaged(MissileEntity m, List<MissileEntity> engaged) {
		for (MissileEntity e : engaged) {
			if (e != m && e.distanceToSqr(m) < 7 * 7) {
				return true;
			}
		}
		return false;
	}

	/** In range, still flying, and worth shooting at right now with this system. */
	private boolean canEngage(DefenseType type, MissileEntity m, Vec3 radar) {
		if (!m.isAlive() || !m.getMissileType().threat || m.getY() <= level().getMinY()) {
			return false;
		}
		// Its own side's missiles fly on (the player's air defence lets his own strikes through, and his towns'
		// air defence does too).
		if (m.country() == country || level() instanceof ServerLevel sl && (m.country() == -1 && playersCountry(sl, country)
				|| country == -1 && playersCountry(sl, m.country()))) {
			return false;
		}
		// A battery linked to a radar station that sees the target gets its track early: it can shoot further out,
		// and the station's better look helps tell decoys apart.
		RadarNetwork.Station station = radarLinked && level() instanceof ServerLevel server ? RadarNetwork.coverage(server, m.position()) : null;
		double range = type.range * (station != null ? RADAR_RANGE_BONUS : 1.0);
		if (m.distanceToSqr(radar) > range * range || isAboutToLeave(m, radar, range)) {
			return false;
		}
		// A decoy: a radar station that sees it tells it apart for sure (the battery never fires at it); the battery's
		// own radar alone sees through some (each decoy has a fixed "how convincing" roll).
		if (m.getMissileType().isDecoy() && (station != null || m.decoyRoll() < type.discrimination)) {
			return false;
		}
		if (type.interceptor == null) {
			// Guns cannot do anything against a ballistic missile coming down at Mach 6.
			return m.getMissileType().kind != MissileType.Kind.BALLISTIC;
		}
		if (type.protectsOnly() && level() instanceof ServerLevel server && !threatensSomething(server, m)) {
			return false;
		}
		// A town's own air defence guards its town (and itself): what flies past to somewhere else is not its business.
		if (garrison && home >= 0 && level() instanceof ServerLevel server && !aimedAtHome(server, m)) {
			return false;
		}
		return worthEngagingNow(m, radar, range);
	}

	/** The missile is aimed at this vehicle's town (or close to the vehicle itself). */
	private boolean aimedAtHome(ServerLevel level, MissileEntity m) {
		Vec3 at = m.getTarget();
		if (at.distanceToSqr(position()) < 120 * 120) {
			return true;
		}
		com.stasdoto.airdefense.nation.Settlement s = com.stasdoto.airdefense.nation.Politics.get(level.getServer()).settlements.get(home);
		if (s == null) {
			return true;
		}
		double r = s.radius + 80;
		return Vec3.atCenterOf(s.center).subtract(at).horizontalDistanceSqr() < r * r;
	}

	private static boolean playersCountry(ServerLevel level, int id) {
		if (id < 0) {
			return false;
		}
		com.stasdoto.airdefense.nation.Country c = com.stasdoto.airdefense.nation.Politics.get(level.getServer()).country(id);
		return c != null && c.owner != null;
	}

	/** Iron Dome: missiles judged to fall in empty country and let go (for the tests). */
	public static final java.util.Set<Integer> IGNORED_HARMLESS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

	/**
	 * Iron Dome's battle management: where will it come down? Only what falls on a town, near people or vehicles,
	 * or near the battery itself is worth an interceptor.
	 */
	private boolean threatensSomething(ServerLevel level, MissileEntity m) {
		Vec3 at = m.getTarget();
		BlockPos p = BlockPos.containing(at);
		com.stasdoto.airdefense.nation.Politics pol = com.stasdoto.airdefense.nation.Politics.get(level.getServer());
		for (com.stasdoto.airdefense.nation.Settlement s : pol.settlements.values()) {
			double r = s.radius + 24;
			if (s.center.distSqr(p) < r * r) {
				return true;
			}
		}
		if (at.distanceToSqr(position()) < 40 * 40) {
			return true;
		}
		AABB box = new AABB(at, at).inflate(25);
		if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box, e -> e.isAlive() && !e.isSpectator()).isEmpty()
				|| !level.getEntitiesOfClass(VehicleEntity.class, box.deflate(5), VehicleEntity::isAlive).isEmpty()) {
			return true;
		}
		if (IGNORED_HARMLESS.add(m.getId()) && IGNORED_HARMLESS.size() > 512) {
			IGNORED_HARMLESS.clear();
		}
		return false;
	}

	/** Ballistic missiles and rockets are engaged on the way down (terminal phase), unless already close. */
	private static boolean worthEngagingNow(MissileEntity m, Vec3 radar, double range) {
		MissileType.Kind kind = m.getMissileType().kind;
		if (kind != MissileType.Kind.BALLISTIC && kind != MissileType.Kind.ROCKET) {
			return true;
		}
		return m.getFlightVelocity().y < 0 || m.distanceToSqr(radar) < range * range * 0.3;
	}

	private static boolean isAboutToLeave(MissileEntity m, Vec3 radar, double range) {
		Vec3 rel = m.position().subtract(radar);
		return rel.lengthSqr() > range * range * 0.64 && rel.dot(m.getFlightVelocity()) > 0;
	}

	private void fireInterceptor(ServerLevel level, DefenseType type, MissileEntity target) {
		int[] missiles = vtype.missileRails();
		int rails = Math.max(1, missiles.length);
		int perRail = Math.max(1, (type.magazine + rails - 1) / rails);
		int fired = type.magazine - getAmmo();
		int rail = missiles.length == 0 ? 0 : missiles[Math.min(rails - 1, fired / perRail)];
		Vec3 from = railWorld(rail);
		Vec3 dir = railDirection(rail);
		MissileEntity.launchInterceptor(level, type.interceptor, from.add(dir.scale(1.0)), dir, target);
		Effects.launchBlast(level, from.subtract(dir.scale(1.5)), type.interceptor);
		if ((fired + 1) % perRail == 0) {
			setLoadedMask(getLoadedMask() & ~(1 << rail));
		}
		level.playSound(null, from.x, from.y, from.z, ModSounds.RADAR_LOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	// --- Gepard: twin 35 mm guns ---

	/** Where to point the guns: the spot the target will be at when a round fired now gets there. */
	private Vec3 leadPoint(MissileEntity target, Vec3 muzzle) {
		Vec3 p = target.position();
		Vec3 v = target.getFlightVelocity();
		Vec3 aim = p;
		for (int i = 0; i < 3; i++) {
			aim = p.add(v.scale(aim.distanceTo(muzzle) / SHELL_SPEED));
		}
		return aim;
	}

	private void tickGun(ServerLevel level, DefenseType type, MissileEntity target) {
		Vec3 muzzle = position().add(0, 2.3, 0);
		Vec3 aim = leadPoint(target, muzzle);
		double h = Math.sqrt(Mth.square(aim.x - getX()) + Mth.square(aim.z - getZ()));
		setElevationTarget((float) Mth.clamp(Math.toDegrees(Math.atan2(aim.y - muzzle.y, h)), -5, 85));
		setTurretTarget(relativeBearing(aim));
		float yawErr = Math.abs(Mth.wrapDegrees(turretYaw - getTurretTarget()));
		float elevErr = Math.abs(elevation - getElevationTarget());
		boolean onTarget = yawErr <= GUN_YAW_TOLERANCE && elevErr <= GUN_ELEVATION_TOLERANCE;
		if (burstLeft > 0) {
			if (onTarget) {
				fireRounds(level, target, aim, yawErr, elevErr);
			} else {
				burstLeft = 0;
			}
			return;
		}
		boolean hybrid = type.hybrid();
		if (getMode() != MODE_AUTO || fireTimer > 0 || (!hybrid && getAmmo() <= 0) || !onTarget) {
			return;
		}
		Effects.gunBurst(level, muzzle);
		burstLeft = 6;
		fireTimer = hybrid ? 8 : type.interval;
		if (!hybrid) {
			// The hybrids' guns have their own (big) ammunition load; the magazine counts the missiles.
			setAmmo(getAmmo() - 1);
			if (getAmmo() <= 0) {
				reloadTimer = type.reload;
			}
		}
		fireRounds(level, target, aim, yawErr, elevErr);
	}

	/** One round from each barrel. The closer the guns are on the lead point, the better the chance to hit. */
	private void fireRounds(ServerLevel level, MissileEntity target, Vec3 aim, float yawErr, float elevErr) {
		// The missile batteries around leave this one to the guns while they are on it.
		target.markGunEngaged(level.getGameTime() + 30);
		RandomSource r = level.getRandom();
		MissileType.Kind kind = target.getMissileType().kind;
		double aimFactor = 1.0 / (1.0 + (yawErr * yawErr + elevErr * elevErr) / 40.0);
		int[] barrels = vtype.barrelRails();
		for (int barrel = 0; barrel < 2 && burstLeft > 0; barrel++, burstLeft--) {
			Vec3 muzzle = railWorld(barrels.length == 0 ? 0 : barrel == 0 ? barrels[0] : barrels[barrels.length - 1]);
			double dist = aim.distanceTo(muzzle);
			double reach = vtype.defense.gunRange > 0 ? vtype.defense.gunRange : vtype.defense.range;
			double chance = DefenseType.gunHitChance(kind) * vtype.defense.gunSkill * (1.0 - 0.45 * dist / reach) * aimFactor;
			boolean hit = r.nextDouble() < chance;
			int flight = Math.max(1, (int) Math.round(dist / SHELL_SPEED));
			Vec3 end;
			if (hit) {
				end = aim.add(r.nextGaussian() * 0.3, r.nextGaussian() * 0.3, r.nextGaussian() * 0.3);
				shells.add(new Shell(target, 1.5f, flight));
			} else {
				// A miss flies on past the target and burns out.
				double spread = 1.2 + dist * 0.035;
				Vec3 off = aim.add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread);
				end = muzzle.add(off.subtract(muzzle).normalize().scale(dist + 24));
			}
			Effects.tracer(level, muzzle, end, (float) SHELL_SPEED);
		}
	}

	// --- Manual mode: the gunner aims with his own eyes ---

	/** The turret and guns follow where the gunner looks; he fires with the left mouse button (see {@link #manualFire}). */
	private void tickManual(ServerLevel level, DefenseType type, boolean gun) {
		tracked = null;
		Player gunner = getGunner();
		VehicleGeometry.Geometry g = vtype.geometry;
		if (gunner == null) {
			// Nobody at the sight: hold fire.
			burstLeft = 0;
			if (gun) {
				setElevationTarget(10);
			}
			return;
		}
		if (gun) {
			// The guns sit a couple of metres from the gunner's eye: pointed parallel to his sight they would miss
			// everything close. So they converge where he looks - at the distance of the threat nearest the middle of
			// the sight, or 60 blocks out when there is none.
			Vec3 eye = gunner.getEyePosition();
			Vec3 look = gunner.getLookAngle();
			MissileEntity seen = sightThreat(eye, look, type.range * 1.3, 8);
			Vec3 point = eye.add(look.scale(seen != null ? seen.position().distanceTo(eye) : 60));
			Vec3 muzzle = position().add(0, 2.3, 0);
			double h = Math.sqrt(Mth.square(point.x - getX()) + Mth.square(point.z - getZ()));
			setElevationTarget((float) Mth.clamp(Math.toDegrees(Math.atan2(point.y - muzzle.y, h)), -5, 85));
			setTurretTarget(relativeBearing(point));
			if (burstLeft > 0) {
				fireManualRounds(level, type);
			}
		} else if (g.turret() != null) {
			setTurretTarget(Mth.wrapDegrees(gunner.getYRot() - getYRot()));
		}
	}

	/** The threat closest to the line of sight from {@code eye} along {@code look}, within {@code coneDeg}. */
	@Nullable
	private MissileEntity sightThreat(Vec3 eye, Vec3 look, double range, double coneDeg) {
		MissileEntity best = null;
		double bestCos = Math.cos(Math.toRadians(coneDeg));
		for (MissileEntity m : MissileEntity.find(level(), new AABB(eye, eye).inflate(range),
				m -> m.isAlive() && m.getMissileType().threat)) {
			Vec3 to = m.position().subtract(eye);
			double d = to.length();
			if (d > range || d < 2) {
				continue;
			}
			double c = to.scale(1 / d).dot(look);
			if (c > bestCos) {
				bestCos = c;
				best = m;
			}
		}
		return best;
	}

	/** Fire button from the gunner's seat: a burst along the barrels, or an interceptor at the threat in the sight. */
	public void manualFire(Player player) {
		if (!vtype.isDefense() || getMode() != MODE_MANUAL || getGunner() != player || getState() != DEPLOYED
				|| !(level() instanceof ServerLevel level)) {
			return;
		}
		DefenseType type = vtype.defense;
		if (fireTimer > 0 || getAmmo() <= 0 || burstLeft > 0) {
			return;
		}
		if (type.interceptor == null) {
			int[] barrels = vtype.barrelRails();
			Effects.gunBurst(level, railWorld(barrels.length == 0 ? 0 : barrels[0]));
			burstLeft = 6;
			fireRounds(type);
			fireManualRounds(level, type);
			return;
		}
		if (elevationLagging()) {
			return;
		}
		MissileEntity lock = manualLock(player, type);
		if (lock == null) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.no_lock"));
			fireTimer = 10;
			return;
		}
		fireInterceptor(level, type, lock);
		fireRounds(type);
	}

	/** Counts one shot (a burst or a missile) against the magazine and starts the reload when it is empty. */
	private void fireRounds(DefenseType type) {
		fireTimer = type.interval;
		setAmmo(getAmmo() - 1);
		if (getAmmo() <= 0) {
			reloadTimer = type.reload;
		}
	}

	/** The threat closest to the middle of the sight, within 8 degrees (what a missile battery's operator locks on). */
	@Nullable
	public MissileEntity manualLock(Player player, DefenseType type) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double range = type.range;
		MissileEntity best = null;
		double bestCos = Math.cos(Math.toRadians(8));
		for (MissileEntity m : MissileEntity.find(level(), new AABB(eye, eye).inflate(range),
				m -> m.isAlive() && m.getMissileType().threat)) {
			Vec3 to = m.position().subtract(eye);
			double d = to.length();
			if (d > range || d < 3) {
				continue;
			}
			double c = to.scale(1 / d).dot(look);
			if (c > bestCos) {
				bestCos = c;
				best = m;
			}
		}
		return best;
	}

	/**
	 * Manual Gepard: two rounds along the barrels each tick of the burst. A round hits the threat whose lead point is
	 * nearest the line of fire, with a chance that falls off quickly with the aiming error; the rest fly on.
	 */
	private void fireManualRounds(ServerLevel level, DefenseType type) {
		RandomSource r = level.getRandom();
		int[] barrels = vtype.barrelRails();
		int first = barrels.length == 0 ? 0 : barrels[0];
		int last = barrels.length == 0 ? vtype.rails() - 1 : barrels[barrels.length - 1];
		Vec3 dir = railDirection(first);
		double range = type.gunRange > 0 ? type.gunRange : type.range;
		// The two guns are harmonised: their fire meets on the line from the middle of the turret, so the aiming
		// error is measured from there (from each barrel the target is a few degrees off at 20-30 blocks).
		Vec3 centre = position().add(0, 2.3, 0);
		List<MissileEntity> threats = MissileEntity.find(level, new AABB(centre, centre).inflate(range),
				m -> m.isAlive() && m.getMissileType().threat);
		for (int barrel = 0; barrel < 2 && burstLeft > 0; barrel++, burstLeft--) {
			Vec3 muzzle = railWorld(barrel == 0 ? first : last);
			MissileEntity best = null;
			Vec3 bestAim = null;
			double bestErr = 8;
			for (MissileEntity m : threats) {
				Vec3 aim = leadPoint(m, centre);
				Vec3 to = aim.subtract(centre);
				double d = to.length();
				if (d > range || d < 1) {
					continue;
				}
				double err = Math.toDegrees(Math.acos(Mth.clamp(to.scale(1 / d).dot(dir), -1, 1)));
				if (err < bestErr) {
					bestErr = err;
					best = m;
					bestAim = aim;
				}
			}
			if (best != null) {
				double dist = bestAim.distanceTo(muzzle);
				double chance = DefenseType.gunHitChance(best.getMissileType().kind) * Math.max(0.6, type.gunSkill) * (1.0 - 0.45 * dist / range)
						/ (1.0 + bestErr * bestErr / 12.0);
				if (r.nextDouble() < chance) {
					Vec3 end = bestAim.add(r.nextGaussian() * 0.3, r.nextGaussian() * 0.3, r.nextGaussian() * 0.3);
					shells.add(new Shell(best, 1.5f, Math.max(1, (int) Math.round(dist / SHELL_SPEED))));
					Effects.tracer(level, muzzle, end, (float) SHELL_SPEED);
					continue;
				}
			}
			Vec3 d = dir.add(r.nextGaussian() * 0.012, r.nextGaussian() * 0.012, r.nextGaussian() * 0.012).normalize();
			Effects.tracer(level, muzzle, muzzle.add(d.scale(range + 20)), (float) SHELL_SPEED);
		}
	}

	/** Rounds in flight: the damage lands when the shell gets there, the same moment its tracer does. */
	private void tickShells(ServerLevel level) {
		for (Iterator<Shell> it = shells.iterator(); it.hasNext(); ) {
			Shell s = it.next();
			if (--s.ticks <= 0) {
				it.remove();
				if (s.target.isAlive()) {
					s.target.hurtServer(level, level.damageSources().generic(), s.damage);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Player actions from the vehicle keys

	public void handleAction(Player player, int action) {
		if (!isAlive() || !hasPassenger(player)) {
			return;
		}
		switch (action) {
			case ACTION_FIRE -> {
				if (vtype.isAir() && vtype.weapon == null) {
					releaseOrdnance(player);
				} else if (vtype.isArmed()) {
					armedFire(player);
				} else {
					manualFire(player);
				}
			}
			case ACTION_SEAT -> switchSeat(player);
			case ACTION_SMOKE -> smoke(player);
			case ACTION_STOW_FOR_MARCH -> {
				if (!isDriver(player)) {
					return;
				}
				if (vtype.isLauncher()) {
					if (!strikePending && salvoLeft == 0) {
						stowTimer = 0;
						marchTicks = 40;
						fold();
						player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.folding"));
					}
				} else {
					marchTicks = 60;
					player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.folding"));
				}
			}
			case ACTION_DEPLOY -> {
				if (vtype.isTruck()) {
					cycleTruckMode(player);
					return;
				}
				if (vtype.isAir()) {
					releaseOrdnance(player);
					return;
				}
				if (vtype.isLauncher()) {
					if (strikePending || salvoLeft > 0) {
						return;
					}
					if (getState() == DEPLOYED) {
						fold();
						player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.folding"));
					} else if (vtype.elevationRate > 0) {
						raiseLauncher();
						player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.raising"));
					}
				} else {
					cycleMode(player);
				}
			}
			case ACTION_MODE -> {
				if (vtype.hasMode()) {
					cycleMode(player);
				}
			}
			default -> {
			}
		}
	}

	/** Raise the launcher to its firing angle without a target (for show, or to be ready). */
	public void raiseLauncher() {
		if (!vtype.isLauncher() || vtype.elevationRate <= 0 || strikePending || salvoLeft > 0) {
			return;
		}
		speed = 0;
		setState(DEPLOYED);
		setElevationTarget(vtype.geometry.deployElevation() > 0 ? vtype.geometry.deployElevation() : 45);
	}

	/** Air defence mode set from the tablet map or the mode key (auto / manual / off). */
	public void setModeByOrder(int mode, @Nullable Player player) {
		if (!vtype.hasMode() || mode < MODE_OFF || mode > MODE_MANUAL) {
			return;
		}
		if (vtype.isRadar()) {
			mode = mode == MODE_OFF ? MODE_OFF : MODE_AUTO;
			setMode(mode);
			if (player != null) {
				player.sendOverlayMessage(Component.translatable(mode == MODE_OFF ? "message.airdefense.radar.off" : "message.airdefense.radar.on"));
			}
			return;
		}
		setMode(mode);
		burstLeft = 0;
		if (player != null) {
			player.sendOverlayMessage(Component.translatable(modeMessage(mode)));
		}
	}

	private static String modeMessage(int mode) {
		return switch (mode) {
			case MODE_AUTO -> "message.airdefense.vehicle.mode_auto";
			case MODE_MANUAL -> "message.airdefense.vehicle.mode_manual";
			default -> "message.airdefense.vehicle.mode_off";
		};
	}

	/** Everything the tablet map shows about this vehicle (server side). */
	public com.stasdoto.airdefense.map.MapStatusPayload.Entry mapEntry() {
		int busy = vtype.isLauncher() ? cooldown : reloadTimer;
		boolean firing = vtype.isLauncher() && (strikePending || salvoLeft > 0);
		BlockPos t = strikeTarget;
		MissileEntity tr = tracked;
		return new com.stasdoto.airdefense.map.MapStatusPayload.Entry(getId(), vtype.ordinal(), (float) getX(), (float) getY(), (float) getZ(),
				getYRot(), getState(), getMode(), getLoadedMask(), getAmmo(), (int) Math.ceil(getHealth() / getMaxHealth() * 100), busy, firing,
				t != null, t != null ? t.getX() : 0, t != null ? t.getY() : 0, t != null ? t.getZ() : 0,
				tr != null && tr.isAlive() ? tr.getId() : -1, entityData.get(DATA_RESERVE));
	}

	/** Auto → manual → off → auto. */
	private void cycleMode(Player player) {
		setModeByOrder(vtype.isRadar() ? (getMode() == MODE_OFF ? MODE_AUTO : MODE_OFF) : nextMode(getMode()), player);
	}

	public static int nextMode(int mode) {
		return switch (mode) {
			case MODE_AUTO -> MODE_MANUAL;
			case MODE_MANUAL -> MODE_OFF;
			default -> MODE_AUTO;
		};
	}

	/** " · reserve 4/8" or " · reserve unlimited". */
	public Component reserveText() {
		return isUnlimited() ? Component.translatable("hud.airdefense.vehicle.reserve_inf")
				: Component.translatable("hud.airdefense.vehicle.reserve", getReserve(), reserveCapacity());
	}

	public Component status() {
		Component name = getType().getDescription();
		int hp = (int) Math.ceil(getHealth() / getMaxHealth() * 100);
		if (vtype.isLauncher()) {
			LauncherType type = vtype.launcher;
			Component ready = salvoLeft > 0 || strikePending
					? Component.translatable("message.airdefense.status.firing", Math.max(1, salvoLeft))
					: cooldown > 0
					? Component.translatable("message.airdefense.status.reload", cooldown / 20 + 1)
					: Component.translatable("message.airdefense.status.ready", Integer.bitCount(getLoadedMask()));
			return Component.translatable("message.airdefense.vehicle.status_launcher", name, hp, ready).append(reserveText());
		}
		if (vtype.isTruck()) {
			return Component.translatable("message.airdefense.vehicle.status_truck", name, hp, getCargo(), vtype.cargoCapacity,
					Component.translatable("message.airdefense.truck." + truckModeKey()))
					.append(" (").append(Component.translatable("nation.airdefense.goods." + getCargoKind())).append(")");
		}
		if (vtype.isArmed()) {
			return Component.translatable("message.airdefense.vehicle.status_armed", name, hp, Math.max(0, getAmmo()), vtype.weapon.caliber,
					(int) getFuel(), vtype.fuelCapacity()).append(reserveText());
		}
		if (vtype.isRadar()) {
			RadarType r = vtype.radar;
			Component state = Component.translatable(getMode() == MODE_OFF ? "message.airdefense.status.off"
					: radarWorking() ? "message.airdefense.radar.working" : "message.airdefense.radar.deploying");
			return Component.translatable("message.airdefense.vehicle.status_radar", name, hp, state, (int) r.range);
		}
		DefenseType type = vtype.defense;
		Component mode = Component.translatable(getMode() == MODE_AUTO ? "message.airdefense.status.on"
				: getMode() == MODE_MANUAL ? "message.airdefense.status.manual" : "message.airdefense.status.off");
		Component ammoText = reloadTimer > 0
				? Component.translatable("message.airdefense.status.reload", reloadTimer / 20 + 1)
				: Component.translatable("message.airdefense.status.ammo", Math.max(getAmmo(), 0), type.magazine);
		return Component.translatable("message.airdefense.vehicle.status_defense", name, hp, mode, ammoText, (int) type.range).append(reserveText());
	}

	// ------------------------------------------------------------------------------------------------
	// Client visuals: wheels, steering, body tilt over uneven ground, spinning radar, wreck fire

	private void clientVisuals() {
		wheelRollO = wheelRoll;
		steerVisO = steerVis;
		tiltPitchO = tiltPitch;
		tiltRollO = tiltRoll;
		liftO = lift;
		radarSpinO = radarSpin;
		Vec3 delta = new Vec3(getX() - xo, 0, getZ() - zo);
		double along = delta.dot(forward());
		wheelRoll += (float) along;
		float yawDelta = Mth.wrapDegrees(getYRot() - yRotO);
		if (isLocalDriverSimulated()) {
			steerVis = steer;
		} else if (Math.abs(along) > 0.01) {
			float wb = Math.max(2.5f, vtype.geometry.wheelbase());
			float target = (float) Math.toDegrees(Math.atan2(Math.toRadians(yawDelta) * wb, Math.abs(along)));
			steerVis += (Mth.clamp(target, -35, 35) - steerVis) * 0.4f;
		} else {
			steerVis *= 0.9f;
		}
		if (vtype.tracked()) {
			steerVis = 0;
		}
		if (vtype.air == VehicleType.HELI) {
			// The rotor: spins up with a pilot aboard.
			engine = Mth.approach(engine, getControllingPassenger() != null ? 1 : 0, 0.008f);
			radarSpin += 0.9f * engine;
		} else if (getState() == DEPLOYED && vtype.geometry.spinner() != null) {
			radarSpin += vtype.isRadar() ? vtype.radar.spin : 0.25f;
		} else if (vtype.isRadar()) {
			// Switched off: the antenna turns back to face forward for the march.
			float rest = Math.round(radarSpin / Mth.TWO_PI) * Mth.TWO_PI;
			radarSpin = Mth.approach(radarSpin, rest, 0.06f);
		}
		updateTilt();
		if (!isAlive()) {
			wreckEffects();
		}
	}

	private boolean isLocalDriverSimulated() {
		return getControllingPassenger() != null && isLocalInstanceAuthoritative();
	}

	private void updateTilt() {
		VehicleGeometry.Geometry g = vtype.geometry;
		if (vtype.isAir()) {
			float yawDelta = Mth.wrapDegrees(getYRot() - yRotO);
			if (vtype.air == VehicleType.PLANE) {
				tiltPitch += (-getXRot() - tiltPitch) * 0.5f;
				if (isLocalDriverSimulated() || !level().isClientSide()) {
					tiltRoll += (-planeRoll - tiltRoll) * 0.5f;
				} else {
					tiltRoll += (Mth.clamp(-yawDelta * 22, -72, 72) - tiltRoll) * 0.15f;
				}
			} else if (isLocalDriverSimulated() || !level().isClientSide()) {
				// The attitude the flight model works with (where it is simulated).
				tiltPitch += (-heliPitch - tiltPitch) * 0.5f;
				tiltRoll += (-heliRoll - tiltRoll) * 0.5f;
			} else {
				Vec3 d = new Vec3(getX() - xo, 0, getZ() - zo);
				double along = d.dot(forward());
				double sideways = d.dot(right());
				tiltPitch += ((float) (-along / vtype.maxSpeed * 22) - tiltPitch) * 0.1f;
				tiltRoll += ((float) (-sideways / vtype.maxSpeed * 20 - yawDelta * 3) - tiltRoll) * 0.1f;
			}
			lift = 0;
			return;
		}
		if (vtype.boat) {
			// Rocking on the waves, the bow up when it speeds.
			float t = (tickCount + getId() * 7) * 0.08f;
			float sp = (float) Math.min(1.0, Math.hypot(getX() - xo, getZ() - zo) / vtype.maxSpeed);
			tiltPitch += (Mth.sin(t) * 1.2f + sp * 4f - tiltPitch) * 0.2f;
			tiltRoll += (Mth.sin(t * 0.7f) * 1.8f - tiltRoll) * 0.2f;
			lift += (Mth.sin(t * 1.3f) * 0.06f - lift) * 0.3f;
			return;
		}
		double halfLen = g.length() * 0.36;
		double halfW = g.width() * 0.38;
		double hf = groundAt(0, halfLen);
		double hr = groundAt(0, -halfLen);
		double hl = groundAt(-halfW, 0);
		double hrt = groundAt(halfW, 0);
		float pitch = (float) Math.toDegrees(Math.atan2(hf - hr, halfLen * 2));
		float roll = (float) Math.toDegrees(Math.atan2(hl - hrt, halfW * 2));
		float up = (float) Mth.clamp((hf + hr) / 2 - getY(), 0, 1.2);
		tiltPitch += (Mth.clamp(pitch, -22, 22) - tiltPitch) * 0.3f;
		tiltRoll += (Mth.clamp(roll, -15, 15) - tiltRoll) * 0.3f;
		lift += (up - lift) * 0.3f;
	}

	/** Height of the ground under a point of the vehicle (vehicle space), searched around the vehicle's level. */
	private double groundAt(double x, double z) {
		Vec3 p = toWorld(x, 0, z);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int top = Mth.floor(getY() + 1.6);
		int bottom = Mth.floor(getY() - 2.5);
		for (int y = top; y >= bottom; y--) {
			m.set(Mth.floor(p.x), y, Mth.floor(p.z));
			BlockState s = level().getBlockState(m);
			if (s.isAir()) {
				continue;
			}
			VoxelShape shape = s.getCollisionShape(level(), m);
			if (!shape.isEmpty()) {
				return y + shape.max(Direction.Axis.Y);
			}
		}
		return getY() - 2.5;
	}

	private void wreckEffects() {
		RandomSource r = random;
		VehicleGeometry.Geometry g = vtype.geometry;
		if (deathTime < 900 && r.nextInt(2) == 0) {
			Vec3 p = toWorld((r.nextDouble() - 0.5) * g.width(), g.height() * (0.5 + r.nextDouble() * 0.4), (r.nextDouble() - 0.5) * g.length());
			level().addParticle(ModParticles.FLAME, true, true, p.x, p.y - 0.5, p.z, 0, 0, 0);
		}
		if (r.nextInt(3) == 0) {
			Vec3 p = toWorld((r.nextDouble() - 0.5) * g.width() * 0.6, g.height(), (r.nextDouble() - 0.5) * g.length() * 0.6);
			level().addParticle(ModParticles.TRAIL_DARK, true, true, p.x, p.y, p.z, 0, 0.12, 0);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Damage and destruction

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!isAlive() || isInvulnerable()) {
			return false;
		}
		boolean smallArms = source.is(com.stasdoto.airdefense.registry.ModDamageTypes.BULLET)
				|| source.is(com.stasdoto.airdefense.registry.ModDamageTypes.SHRAPNEL);
		if (source.isCreativePlayer() && source.getDirectEntity() instanceof Player && !smallArms) {
			ejectPassengers();
			discard();
			return true;
		}
		float k;
		if (smallArms) {
			// Armour: rifle rounds and fragments barely scratch it.
			k = 0.06f;
		} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			k = 1.0f;
		} else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			k = 0.25f;
		} else if (source.is(DamageTypeTags.IS_FIRE)) {
			k = 0.15f;
		} else if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)) {
			k = 0;
		} else {
			k = 0.2f;
		}
		float dmg = amount * k * vtype.armor;
		if (dmg <= 0) {
			return false;
		}
		setHealth(getHealth() - dmg);
		if (getHealth() <= 0) {
			die(source);
		}
		return true;
	}

	@Override
	public void die(DamageSource source) {
		if (isRemoved() || dead) {
			return;
		}
		super.die(source);
		if (level() instanceof ServerLevel server) {
			RadarNetwork.remove(server, getId());
			com.stasdoto.airdefense.nation.Arsenals.destroyed(server, this);
			if (cargoDelivery != 0) {
				com.stasdoto.airdefense.nation.Arsenals.lorryLost(server, this);
			}
			ejectPassengers();
			// The fuel and every missile still on board go up.
			float power = 3.5f + Integer.bitCount(getLoadedMask()) * (vtype.isLauncher() ? 1.2f : 0.4f);
			Effects.vehicleDestroyed(server, this, position().add(0, 1.2, 0), Math.min(power, 9f));
			setLoadedMask(0);
		}
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime >= 1200 && !level().isClientSide()) {
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return isAlive();
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		double d = 256 * getViewScale();
		return distance < d * d;
	}

	// ------------------------------------------------------------------------------------------------
	// Saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("vehicle_state", getState());
		output.putInt("vehicle_mode", getMode());
		output.putInt("vehicle_loaded", getLoadedMask());
		output.putInt("vehicle_ammo", getAmmo());
		output.putInt("vehicle_cooldown", cooldown);
		output.putInt("vehicle_reload", reloadTimer);
		output.putBoolean("vehicle_reload_pending", reloadPending);
		output.putInt("vehicle_reserve", entityData.get(DATA_RESERVE));
		output.putFloat("vehicle_fuel", entityData.get(DATA_FUEL));
		output.putInt("vehicle_ordnance", getOrdnance());
		output.putInt("vehicle_cargo", getCargo());
		output.putInt("vehicle_country", country);
		output.putInt("vehicle_home", home);
		output.putBoolean("vehicle_garrison", garrison);
		output.putInt("vehicle_troops", troops);
		output.putLong("vehicle_delivery", cargoDelivery);
		output.putInt("vehicle_unloaded", unloadedTicks);
		if (troopTarget != null) {
			output.store("vehicle_troop_target", BlockPos.CODEC, troopTarget);
		}
		if (route != null) {
			List<Integer> pts = new ArrayList<>();
			for (int i = routeIndex; i < route.size(); i++) {
				pts.add((int) Math.floor(route.get(i).x));
				pts.add((int) Math.floor(route.get(i).z));
			}
			output.store("vehicle_route", com.mojang.serialization.Codec.INT.listOf(), pts);
			output.putFloat("vehicle_route_speed", routeSpeed);
		}
		output.putInt("vehicle_cargo_kind", entityData.get(DATA_CARGO_KIND));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setMode(input.getIntOr("vehicle_mode", vtype.hasMode() ? MODE_AUTO : MODE_OFF));
		setLoadedMask(input.getIntOr("vehicle_loaded", fullMask()));
		setAmmo(input.getIntOr("vehicle_ammo", vtype.magazine()));
		cooldown = input.getIntOr("vehicle_cooldown", 0);
		reloadTimer = input.getIntOr("vehicle_reload", 0);
		reloadPending = input.getBooleanOr("vehicle_reload_pending", false);
		// Vehicles from before stage 5 have no reserve entry: they keep reloading for free, as they always did.
		setReserve(input.getIntOr("vehicle_reserve", -1));
		entityData.set(DATA_FUEL, input.getFloatOr("vehicle_fuel", -1f));
		entityData.set(DATA_ORDNANCE, input.getIntOr("vehicle_ordnance", vtype.ordnance != null ? vtype.ordnance.count : 0));
		entityData.set(DATA_CARGO, input.getIntOr("vehicle_cargo", 0));
		country = input.getIntOr("vehicle_country", -1);
		home = input.getIntOr("vehicle_home", -1);
		garrison = input.getBooleanOr("vehicle_garrison", false);
		troops = input.getIntOr("vehicle_troops", 0);
		cargoDelivery = input.getLongOr("vehicle_delivery", 0L);
		unloadedTicks = input.getIntOr("vehicle_unloaded", -1);
		troopTarget = input.read("vehicle_troop_target", BlockPos.CODEC).orElse(null);
		input.read("vehicle_route", com.mojang.serialization.Codec.INT.listOf()).ifPresent(pts -> {
			List<Vec3> r = new ArrayList<>();
			for (int i = 0; i + 1 < pts.size(); i += 2) {
				r.add(new Vec3(pts.get(i) + 0.5, 0, pts.get(i + 1) + 0.5));
			}
			if (!r.isEmpty()) {
				route = r;
				routeIndex = 0;
				routeSpeed = input.getFloatOr("vehicle_route_speed", 0.75f);
			}
		});
		entityData.set(DATA_CARGO_KIND, input.getIntOr("vehicle_cargo_kind", -1));
		fold();
	}
}
