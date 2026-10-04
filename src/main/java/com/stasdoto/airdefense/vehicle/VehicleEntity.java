package com.stasdoto.airdefense.vehicle;

import java.util.Comparator;
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
import com.stasdoto.airdefense.registry.ModParticles;
import com.stasdoto.airdefense.registry.ModSounds;

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

	private static final int MIN_STRIKE_DISTANCE = 24;
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

	// Air defence state (server).
	private int fireTimer;
	private int reloadTimer;
	private int sirenTimer;
	private int stationaryTicks;
	private int marchTicks;
	@Nullable
	private MissileEntity tracked;
	private Vec3 lastServerPos = Vec3.ZERO;

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
		if (type.isDefense()) {
			v.setMode(MODE_AUTO);
		}
		v.setLoadedMask(v.fullMask());
		v.setAmmo(type.magazine());
		v.fold();
		level.addFreshEntity(v);
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
		return isAlive() && (vtype == VehicleType.GEPARD || isFolded());
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
		if (vtype == VehicleType.GEPARD && seatIndexOf(passenger) == 1) {
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

	@Override
	public void travel(Vec3 ignored) {
		Input in = level().isClientSide() ? clientInput : Input.EMPTY;
		boolean wantsToMove = in.forward() || in.backward() || in.left() || in.right();
		if (!canDrive()) {
			if (wantsToMove && level().isClientSide() && stowRequestCooldown-- <= 0) {
				stowRequester.accept(this);
				stowRequestCooldown = 20;
			}
			in = Input.EMPTY;
		}
		float max = vtype.maxSpeed * (isInWater() ? 0.35f : 1.0f);
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
		if (vtype.tracked()) {
			float sp = Math.abs(speed) / vtype.maxSpeed;
			yawRate = turnInput * vtype.pivotTurn * (1 - 0.45f * sp) * (speed < -0.01f ? -1 : 1);
			steer = turnInput * 20;
		} else {
			float target = turnInput * vtype.maxSteer;
			steer += Mth.clamp(target - steer, -3.5f, 3.5f);
			float wb = Math.max(2.5f, vtype.geometry.wheelbase());
			yawRate = (float) (speed * Math.tan(Math.toRadians(steer)) / wb * Mth.RAD_TO_DEG);
		}
		if (onGround() || isInWater()) {
			setYRot(getYRot() + yawRate);
		}
		yBodyRot = yHeadRot = getYRot();

		Vec3 f = forward();
		double vy = getDeltaMovement().y;
		if (isInWater()) {
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
			float step = Math.min(rate, Math.max(rate * 0.25f, Math.abs(d) * 0.08f));
			elevation += Mth.clamp(d, -step, step);
		}
		if (vtype.turretRate > 0) {
			float d = Mth.wrapDegrees(getTurretTarget() - turretYaw);
			turretYaw = Mth.wrapDegrees(turretYaw + Mth.clamp(d, -vtype.turretRate, vtype.turretRate));
		}
	}

	private void serverLogic(ServerLevel level) {
		Vec3 pos = position();
		if (pos.distanceToSqr(lastServerPos) < 0.0004) {
			stationaryTicks++;
		} else {
			stationaryTicks = 0;
		}
		lastServerPos = pos;
		if (marchTicks > 0) {
			marchTicks--;
		}
		if (vtype.isLauncher()) {
			tickLauncher(level);
		} else {
			tickDefense(level);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Strike launchers

	public boolean commandStrike(BlockPos target, @Nullable Player player) {
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
		if (getLoadedMask() == 0) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.airdefense.vehicle.empty"));
			}
			return false;
		}
		strikeTarget = target;
		strikePending = true;
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
				salvoLeft = Math.min(type.salvo, Integer.bitCount(getLoadedMask()));
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
			setLoadedMask(fullMask());
			setAmmo(vtype.magazine());
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
				double apex = Mth.clamp(d * 0.35, 30, 220);
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
		MissileEntity.launchStrike(level, type.missile, from, aim, forward(), dir);
		setLoadedMask(getLoadedMask() & ~(1 << rail));
		setAmmo(Math.max(0, getAmmo() - 1));
		Effects.launchBlast(level, from.subtract(dir.scale(2.5)), type.missile);
		level.playSound(null, from.x, from.y, from.z, type.sound, SoundSource.BLOCKS, 4.0f, 0.95f + r.nextFloat() * 0.1f);
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
		if (reloadTimer > 0 && --reloadTimer == 0) {
			setAmmo(type.magazine);
			setLoadedMask(fullMask());
		}
		if (fireTimer > 0) {
			fireTimer--;
		}
		if (sirenTimer > 0) {
			sirenTimer--;
		}
		boolean onTheMove = marchTicks > 0 || (vtype != VehicleType.GEPARD && stationaryTicks < 30);
		boolean active = getMode() != MODE_OFF && !onTheMove;
		setState(active ? DEPLOYED : STOWED);
		if (!active) {
			tracked = null;
			setElevationTarget(0);
			setTurretTarget(0);
			return;
		}
		VehicleGeometry.Geometry g = vtype.geometry;
		if (vtype != VehicleType.GEPARD) {
			setElevationTarget(g.deployElevation());
		}
		Vec3 radar = position().add(0, 3.0, 0);
		if ((tickCount + getId()) % 3 == 0) {
			tracked = pickThreat(level, type, radar);
		}
		if (tracked == null || !tracked.isAlive()) {
			tracked = null;
			if (vtype == VehicleType.GEPARD) {
				setElevationTarget(0);
			}
			return;
		}
		if (sirenTimer == 0) {
			level.playSound(null, radar.x, radar.y, radar.z, ModSounds.SIREN, SoundSource.BLOCKS, 3.0f, 1.0f);
			sirenTimer = 130;
		}
		Vec3 aimPoint = tracked.position();
		if (vtype == VehicleType.GEPARD) {
			double flightTicks = tracked.position().distanceTo(radar) / 3.0;
			aimPoint = aimPoint.add(tracked.getFlightVelocity().scale(flightTicks));
			double h = Math.sqrt(Mth.square(aimPoint.x - getX()) + Mth.square(aimPoint.z - getZ()));
			setElevationTarget((float) Mth.clamp(Math.toDegrees(Math.atan2(aimPoint.y - (getY() + 2.3), h)), -5, 85));
		}
		if (g.turret() != null) {
			setTurretTarget(relativeBearing(aimPoint));
		}
		if (getMode() != MODE_AUTO || fireTimer > 0 || getAmmo() <= 0 || elevationLagging()) {
			return;
		}
		float tolerance = vtype == VehicleType.GEPARD ? 4f : 20f;
		if (g.turret() != null && Math.abs(Mth.wrapDegrees(turretYaw - getTurretTarget())) > tolerance) {
			return;
		}
		if (vtype == VehicleType.GEPARD) {
			gunBurst(level, tracked);
		} else {
			fireInterceptor(level, type, tracked);
		}
		fireTimer = type.interval;
		setAmmo(getAmmo() - 1);
		if (getAmmo() <= 0) {
			reloadTimer = type.reload;
		}
	}

	private boolean elevationLagging() {
		return Math.abs(elevation - getElevationTarget()) > (vtype == VehicleType.GEPARD ? 4f : 1.5f);
	}

	@Nullable
	private MissileEntity pickThreat(ServerLevel level, DefenseType type, Vec3 radar) {
		double range = type.range;
		AABB box = new AABB(radar.x - range, radar.y - range, radar.z - range, radar.x + range, radar.y + range, radar.z + range);
		List<MissileEntity> threats = level.getEntitiesOfClass(MissileEntity.class, box,
				m -> m.getMissileType().threat && m.isAlive() && m.distanceToSqr(radar) < range * range);
		if (threats.isEmpty()) {
			return null;
		}
		return threats.stream()
				.filter(m -> type == DefenseType.GEPARD || m == tracked || m.getEngagedBy() < type.shotsPerTarget(m.getMissileType().kind))
				.filter(m -> m.getY() > level.getMinY() && !isAboutToLeave(m, radar, range) && worthEngagingNow(m, radar, range))
				.min(Comparator.comparingDouble((MissileEntity m) -> type.priority(m.getMissileType().kind) * 1e6 + m.distanceToSqr(radar)))
				.orElse(null);
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
		int rails = vtype.rails();
		int perRail = Math.max(1, (type.magazine + rails - 1) / rails);
		int fired = type.magazine - getAmmo();
		int rail = Math.min(rails - 1, fired / perRail);
		Vec3 from = railWorld(rail);
		Vec3 dir = railDirection(rail);
		MissileEntity.launchInterceptor(level, type.interceptor, from.add(dir.scale(1.0)), dir, target);
		Effects.launchBlast(level, from.subtract(dir.scale(1.5)), type.interceptor);
		if ((fired + 1) % perRail == 0) {
			setLoadedMask(getLoadedMask() & ~(1 << rail));
		}
		RandomSource r = level.getRandom();
		level.playSound(null, from.x, from.y, from.z, ModSounds.LAUNCH_LIGHT, SoundSource.BLOCKS, 3.0f, 0.95f + r.nextFloat() * 0.15f);
		level.playSound(null, from.x, from.y, from.z, ModSounds.RADAR_LOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	/** Gepard: a 6-round burst from the twin 35 mm guns with tracers; each round can hit or miss. */
	private void gunBurst(ServerLevel level, MissileEntity target) {
		RandomSource r = level.getRandom();
		MissileType.Kind kind = target.getMissileType().kind;
		Vec3 muzzleL = railWorld(0);
		Vec3 muzzleR = railWorld(vtype.rails() - 1);
		double dist = target.position().distanceTo(muzzleL);
		double baseChance = DefenseType.gunHitChance(kind) * (1.0 - 0.5 * dist / vtype.defense.range);
		Vec3 aim = target.position().add(target.getFlightVelocity().scale(dist / 3.0));
		level.playSound(null, muzzleL.x, muzzleL.y, muzzleL.z, ModSounds.GEPARD_BURST, SoundSource.BLOCKS, 3.0f, 0.95f + r.nextFloat() * 0.1f);
		for (int round = 0; round < 6; round++) {
			boolean hit = r.nextDouble() < baseChance;
			Vec3 end = hit ? aim : aim.add(r.nextGaussian() * 2.5, r.nextGaussian() * 2.5, r.nextGaussian() * 2.5);
			Effects.tracer(level, round % 2 == 0 ? muzzleL : muzzleR, end);
			if (hit && target.isAlive()) {
				target.hurtServer(level, level.damageSources().generic(), 1.5f);
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
			case ACTION_SEAT -> switchSeat(player);
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
				if (vtype.isDefense()) {
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

	private void cycleMode(Player player) {
		int mode = getMode() == MODE_AUTO ? MODE_OFF : MODE_AUTO;
		setMode(mode);
		player.sendOverlayMessage(Component.translatable(mode == MODE_AUTO ? "message.airdefense.vehicle.mode_auto" : "message.airdefense.vehicle.mode_off"));
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
			return Component.translatable("message.airdefense.vehicle.status_launcher", name, hp, ready);
		}
		DefenseType type = vtype.defense;
		Component mode = Component.translatable(getMode() == MODE_AUTO ? "message.airdefense.status.on" : "message.airdefense.status.off");
		Component ammoText = reloadTimer > 0
				? Component.translatable("message.airdefense.status.reload", reloadTimer / 20 + 1)
				: Component.translatable("message.airdefense.status.ammo", Math.max(getAmmo(), 0), type.magazine);
		return Component.translatable("message.airdefense.vehicle.status_defense", name, hp, mode, ammoText, (int) type.range);
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
		if (getState() == DEPLOYED && vtype.geometry.spinner() != null) {
			radarSpin += 0.25f;
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
		if (source.isCreativePlayer() && source.getDirectEntity() instanceof Player) {
			ejectPassengers();
			discard();
			return true;
		}
		float k;
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
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
		float dmg = amount * k;
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setMode(input.getIntOr("vehicle_mode", vtype.isDefense() ? MODE_AUTO : MODE_OFF));
		setLoadedMask(input.getIntOr("vehicle_loaded", fullMask()));
		setAmmo(input.getIntOr("vehicle_ammo", vtype.magazine()));
		cooldown = input.getIntOr("vehicle_cooldown", 0);
		reloadTimer = input.getIntOr("vehicle_reload", 0);
		reloadPending = input.getBooleanOr("vehicle_reload_pending", false);
		fold();
	}
}
