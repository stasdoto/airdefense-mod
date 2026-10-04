package com.stasdoto.airdefense.missile;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.registry.ModEntities;

/**
 * One entity class for every missile, rocket, drone and interceptor. The {@link MissileType} decides how it flies.
 * All guidance runs on the server; the client only draws the model and the exhaust trail.
 */
public class MissileEntity extends Entity {
	private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_MOTOR = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BOOLEAN);

	// --- server-side flight state (missiles are short-lived, so none of this is saved) ---
	private Vec3 target = Vec3.ZERO;
	private Vec3 launchPos = Vec3.ZERO;
	private Vec3 launchDir = new Vec3(0, 1, 0);
	private Vec3 lastVel = Vec3.ZERO;
	private double speed;
	private float health = 1;
	private int life;
	private int phase;
	private int boostTicks;
	private double boostHeight;
	// Ballistic arc: start point, apex height above the start→target line, progress 0..1.
	private Vec3 arcStart = Vec3.ZERO;
	private double arcApex;
	private double arcS;
	// Cruise missiles and drones: altitude they are trying to hold.
	private double desiredY = Double.NaN;
	// Interceptors: what they are chasing; threats: how many interceptors chase them.
	private MissileEntity targetMissile;
	private int engagedBy;
	private boolean detonated;

	private ItemStack displayStack;
	private MissileType displayType;

	public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	// ------------------------------------------------------------------------------------------------
	// Spawning

	/** Launches an attacking missile/rocket/drone from {@code pos} towards {@code target}. */
	public static MissileEntity launchStrike(ServerLevel level, MissileType type, Vec3 pos, Vec3 target, Vec3 forward) {
		MissileEntity m = new MissileEntity(ModEntities.MISSILE, level);
		m.setMissileType(type);
		m.setPos(pos);
		m.launchPos = pos;
		m.target = target;
		m.health = type.health;
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);
		Vec3 horiz = flat.lengthSqr() > 1e-4 ? flat.normalize() : new Vec3(forward.x, 0, forward.z).normalize();

		switch (type.kind) {
			case BALLISTIC -> {
				// Vertical launch, climb, then pitch over onto the ballistic arc.
				m.phase = 0;
				m.boostHeight = 22;
				m.launchDir = new Vec3(0, 1, 0);
				m.speed = 0.25;
			}
			case ROCKET -> {
				m.phase = 1;
				m.startArc(pos);
				m.launchDir = m.arcPoint(0.01).subtract(pos).normalize();
				m.speed = 0.8;
			}
			case CRUISE -> {
				// Booster throws it up at ~50 degrees, then the turbojet takes over.
				m.phase = 0;
				m.boostTicks = 30;
				m.launchDir = horiz.add(0, 1.2, 0).normalize();
				m.speed = 0.6;
			}
			case DRONE -> {
				// Rocket-assisted take-off from the rail.
				m.phase = 0;
				m.boostTicks = 22;
				m.launchDir = horiz.add(0, 0.55, 0).normalize();
				m.speed = 0.7;
			}
			case INTERCEPTOR -> throw new IllegalArgumentException("use launchInterceptor");
		}
		m.lastVel = m.launchDir.scale(m.speed);
		m.updateRotation(m.launchDir);
		m.setMotor(true);
		level.addFreshEntity(m);
		MissileStats.STRIKES_LAUNCHED.incrementAndGet();
		MissileStats.log("launch {} from {} to {}", type, fmt(pos), fmt(target));
		return m;
	}

	/** Launches an interceptor chasing {@code target}. */
	public static MissileEntity launchInterceptor(ServerLevel level, MissileType type, Vec3 pos, Vec3 dir, MissileEntity target) {
		MissileEntity m = new MissileEntity(ModEntities.MISSILE, level);
		m.setMissileType(type);
		m.setPos(pos);
		m.launchPos = pos;
		m.launchDir = dir.normalize();
		m.health = type.health;
		m.speed = type == MissileType.STINGER ? 1.2 : 0.9;
		m.lastVel = m.launchDir.scale(m.speed);
		m.targetMissile = target;
		if (target != null) {
			target.engagedBy++;
			m.target = target.position();
		}
		m.updateRotation(m.launchDir);
		m.setMotor(true);
		level.addFreshEntity(m);
		MissileStats.INTERCEPTORS_LAUNCHED.incrementAndGet();
		MissileStats.log("interceptor {} from {} at {}", type, fmt(pos), target == null ? "-" : target.getMissileType() + "@" + fmt(target.position()));
		return m;
	}

	private static String fmt(Vec3 v) {
		return String.format("(%.0f %.0f %.0f)", v.x, v.y, v.z);
	}

	// ------------------------------------------------------------------------------------------------
	// Data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TYPE, 0);
		builder.define(DATA_MOTOR, true);
	}

	public MissileType getMissileType() {
		return MissileType.byId(this.entityData.get(DATA_TYPE));
	}

	private void setMissileType(MissileType type) {
		this.entityData.set(DATA_TYPE, type.ordinal());
	}

	public boolean isMotorOn() {
		return this.entityData.get(DATA_MOTOR);
	}

	private void setMotor(boolean on) {
		if (this.entityData.get(DATA_MOTOR) != on) {
			this.entityData.set(DATA_MOTOR, on);
		}
	}

	public ItemStack getDisplayStack() {
		MissileType type = getMissileType();
		if (displayStack == null || displayType != type) {
			displayType = type;
			displayStack = new ItemStack(BuiltInRegistries.ITEM.getValue(AirDefense.id(type.itemId)));
		}
		return displayStack;
	}

	public Vec3 getFlightVelocity() {
		return lastVel;
	}

	public int getEngagedBy() {
		return engagedBy;
	}

	public Vec3 getTarget() {
		return target;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	// ------------------------------------------------------------------------------------------------
	// Damage: threats can be shot down by anything (interceptors, Gepard, arrows, a sword...)

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		MissileType type = getMissileType();
		if (!type.threat || isRemoved() || detonated) {
			return false;
		}
		health -= amount;
		if (health <= 0) {
			shotDown();
		}
		return true;
	}

	@Override
	public boolean isPickable() {
		return getMissileType().threat && !isRemoved();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 640 * 640;
	}

	/** Destroyed in the air: warhead blows up where it is, nothing reaches the target. */
	public void shotDown() {
		if (level() instanceof ServerLevel && !detonated) {
			detonate(position(), true);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Ticking

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel serverLevel) {
			serverTick(serverLevel);
		} else {
			clientTick();
		}
	}

	private void serverTick(ServerLevel level) {
		MissileType type = getMissileType();
		life++;
		if (life > type.maxLife) {
			detonate(position(), type.kind == MissileType.Kind.INTERCEPTOR || type.kind == MissileType.Kind.DRONE && getY() > target.y + 4);
			return;
		}
		keepChunksLoaded(level);

		Vec3 from = position();
		Vec3 vel = switch (type.kind) {
			case BALLISTIC, ROCKET -> ballisticStep(type);
			case CRUISE, DRONE -> cruiseStep(level, type);
			case INTERCEPTOR -> interceptorStep(level, type);
		};
		if (isRemoved() || vel == null) {
			return;
		}
		Vec3 to = from.add(vel);

		int safeTicks = type.kind == MissileType.Kind.INTERCEPTOR ? 4 : 8;
		if (life > safeTicks) {
			BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
			if (hit.getType() != HitResult.Type.MISS) {
				setPos(hit.getLocation());
				detonate(hit.getLocation(), false);
				return;
			}
			if (type.threat) {
				EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, from, to,
						getBoundingBox().expandTowards(vel).inflate(1.0),
						e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), 0.4f);
				if (entityHit != null) {
					setPos(entityHit.getLocation());
					detonate(entityHit.getLocation(), false);
					return;
				}
			}
		}

		setPos(to);
		lastVel = vel;
		setDeltaMovement(vel);
		updateRotation(vel);

		if (type.loopSound != null && life % 40 == 2) {
			level.playSound(null, this, type.loopSound, SoundSource.HOSTILE, type.kind == MissileType.Kind.DRONE ? 3.0f : 2.0f, 1.0f);
		}
	}

	// --- Ballistic missiles and MLRS rockets: deterministic parabola that ends exactly on the target -----

	private void startArc(Vec3 from) {
		arcStart = from;
		arcS = 0;
		double d = Math.sqrt(Mth.square(target.x - from.x) + Mth.square(target.z - from.z));
		MissileType type = getMissileType();
		arcApex = type.kind == MissileType.Kind.BALLISTIC
				? Mth.clamp(d * 0.45, 45, 280)
				: Mth.clamp(d * 0.28, 22, 170);
	}

	private Vec3 arcPoint(double s) {
		return new Vec3(
				Mth.lerp(s, arcStart.x, target.x),
				Mth.lerp(s, arcStart.y, target.y) + 4 * arcApex * s * (1 - s),
				Mth.lerp(s, arcStart.z, target.z));
	}

	private Vec3 ballisticStep(MissileType type) {
		if (phase == 0) {
			// Vertical boost out of the launcher.
			speed = Math.min(type.maxSpeed * 0.55, speed + type.accel * 1.6);
			Vec3 v = new Vec3(0, speed, 0);
			setMotor(true);
			if (getY() + speed >= launchPos.y + boostHeight) {
				phase = 1;
				startArc(position().add(v));
			}
			return v;
		}
		if (arcS >= 1.0) {
			// Past the aim point but nothing hit yet (aim point in the air): keep diving straight.
			return currentDir().scale(speed);
		}
		boolean descending = arcS > 0.5;
		double cap = descending ? type.maxSpeed * 1.3 : type.maxSpeed;
		speed = Math.min(cap, speed + type.accel * (descending ? 2.0 : 1.0));
		double d = Math.sqrt(Mth.square(target.x - arcStart.x) + Mth.square(target.z - arcStart.z));
		double dyds = (target.y - arcStart.y) + 4 * arcApex * (1 - 2 * arcS);
		double len = Math.sqrt(d * d + dyds * dyds);
		double next = Math.min(1.0, arcS + speed / Math.max(len, 1e-3));
		Vec3 v = arcPoint(next).subtract(position());
		arcS = next;
		if (next >= 1.0) {
			// Overshoot slightly so the block ray-cast definitely finds the ground at the aim point.
			v = v.add(v.normalize().scale(2.0));
		}
		setMotor(type.kind == MissileType.Kind.ROCKET ? arcS < 0.25 : arcS < 0.35);
		return v;
	}

	// --- Cruise missiles and drones: hold altitude over the terrain, turn towards the target, dive at the end ---

	private Vec3 cruiseStep(ServerLevel level, MissileType type) {
		Vec3 pos = position();
		if (phase == 0) {
			speed = Math.min(type.maxSpeed, speed + type.accel);
			if (--boostTicks <= 0) {
				phase = 1;
			}
			setMotor(true);
			Vec3 dir = turnTowards(currentDir(), new Vec3(launchDir.x, launchDir.y * 0.3, launchDir.z).normalize(), 0.02);
			return dir.scale(Math.max(speed, 0.6));
		}
		setMotor(type.kind == MissileType.Kind.CRUISE);

		double dx = target.x - pos.x;
		double dz = target.z - pos.z;
		double hd = Math.sqrt(dx * dx + dz * dz);
		double height = Math.max(0, pos.y - target.y);
		double diveDistance = type.kind == MissileType.Kind.DRONE ? 10 + height * 0.7 : 16 + height * 0.9;

		if (phase == 2 || hd < diveDistance) {
			phase = 2;
			speed = Math.min(type.maxSpeed * 1.35, speed + type.accel * 2);
			Vec3 desired = target.subtract(pos).normalize();
			return turnTowards(currentDir(), desired, type.turnRate * 3).scale(speed);
		}

		speed = Math.min(type.maxSpeed, speed + type.accel);
		double desiredYaw = Math.atan2(dx, dz);
		if (type.kind == MissileType.Kind.DRONE) {
			desiredYaw += Math.sin(life * 0.045 + getId()) * 0.18;
		}
		Vec3 cur = currentDir();
		double curYaw = Math.atan2(cur.x, cur.z);
		double yaw = curYaw + Mth.clamp(Mth.wrapDegrees((desiredYaw - curYaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, -type.turnRate, type.turnRate);

		if (life % 5 == 0 || Double.isNaN(desiredY)) {
			double ground = Double.NEGATIVE_INFINITY;
			for (int k = 0; k <= 3; k++) {
				BlockPos p = BlockPos.containing(pos.x + Math.sin(yaw) * k * 10, pos.y, pos.z + Math.cos(yaw) * k * 10);
				if (level.hasChunkAt(p)) {
					ground = Math.max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()));
				}
			}
			if (ground > Double.NEGATIVE_INFINITY) {
				desiredY = ground + type.cruiseAltitude();
			}
		}
		double vy = Double.isNaN(desiredY) ? 0 : Mth.clamp((desiredY - pos.y) * 0.08, -0.35, 0.45);
		double h = Math.sqrt(Math.max(0.01, speed * speed - vy * vy));
		return new Vec3(Math.sin(yaw) * h, vy, Math.cos(yaw) * h);
	}

	// --- Interceptors: lead pursuit with a turn-rate limit and a proximity fuse ---

	private Vec3 interceptorStep(ServerLevel level, MissileType type) {
		speed = Math.min(type.maxSpeed, speed + type.accel);
		setMotor(life < type.maxLife * 0.75);
		Vec3 dir = currentDir();
		if (life <= 5) {
			return dir.scale(speed);
		}
		MissileEntity tgt = targetMissile;
		if (tgt == null || tgt.isRemoved() || tgt.detonated) {
			if (tgt != null) {
				tgt.engagedBy = Math.max(0, tgt.engagedBy - 1);
			}
			tgt = findNewTarget(level, dir);
			targetMissile = tgt;
			if (tgt == null) {
				return dir.scale(speed);
			}
			tgt.engagedBy++;
		}

		Vec3 pos = position();
		Vec3 tp = tgt.position();
		Vec3 tv = tgt.getFlightVelocity();
		Vec3 rel = tp.subtract(pos);
		// Closest approach during this tick (both objects move in straight lines for one tick).
		Vec3 relVel = tv.subtract(dir.scale(speed));
		double t = relVel.lengthSqr() > 1e-6 ? Mth.clamp(-rel.dot(relVel) / relVel.lengthSqr(), 0, 1) : 0;
		double closest = rel.add(relVel.scale(t)).length();
		if (closest <= type.proximity) {
			Vec3 at = pos.add(dir.scale(speed * t));
			boolean kill = random.nextDouble() < type.killChance(tgt.getMissileType().kind);
			setPos(at);
			detonate(at, true);
			if (kill) {
				tgt.shotDown();
			} else {
				tgt.health -= 1;
			}
			return null;
		}

		double dist = rel.length();
		double tGo = dist / Math.max(speed, 0.5);
		Vec3 aim = tp.add(tv.scale(Math.min(tGo, 80)));
		target = aim;
		return turnTowards(dir, aim.subtract(pos).normalize(), type.turnRate).scale(speed);
	}

	private MissileEntity findNewTarget(ServerLevel level, Vec3 dir) {
		List<MissileEntity> list = level.getEntitiesOfClass(MissileEntity.class, getBoundingBox().inflate(70),
				m -> m != this && m.getMissileType().threat && !m.detonated && m.position().subtract(position()).normalize().dot(dir) > 0.2);
		return list.stream().min(Comparator.comparingDouble((MissileEntity m) -> m.engagedBy * 400 + m.distanceToSqr(this))).orElse(null);
	}

	// --- Helpers ---

	private Vec3 currentDir() {
		return lastVel.lengthSqr() > 1e-8 ? lastVel.normalize() : launchDir;
	}

	/** Rotates {@code from} towards {@code to} by at most {@code maxAngle} radians (spherical interpolation). */
	static Vec3 turnTowards(Vec3 from, Vec3 to, double maxAngle) {
		double dot = Mth.clamp(from.dot(to), -1, 1);
		double angle = Math.acos(dot);
		if (angle <= maxAngle || angle < 1e-6) {
			return to;
		}
		if (Math.PI - angle < 1e-3) {
			// Exactly opposite: nudge sideways so the interpolation is defined.
			from = from.add(0.01, 0.02, 0.01).normalize();
			angle = Math.acos(Mth.clamp(from.dot(to), -1, 1));
		}
		double t = maxAngle / angle;
		double s = Math.sin(angle);
		double a = Math.sin((1 - t) * angle) / s;
		double b = Math.sin(t * angle) / s;
		return from.scale(a).add(to.scale(b)).normalize();
	}

	private void updateRotation(Vec3 v) {
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
		setXRot((float) (Mth.atan2(v.y, h) * Mth.RAD_TO_DEG));
	}

	private void keepChunksLoaded(ServerLevel level) {
		if (life % 10 != 1) {
			return;
		}
		ChunkPos here = ChunkPos.containing(blockPosition());
		level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, here, 2);
		ChunkPos ahead = ChunkPos.containing(BlockPos.containing(position().add(currentDir().scale(48))));
		if (!ahead.equals(here)) {
			level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ahead, 1);
		}
	}

	private void detonate(Vec3 at, boolean inAir) {
		if (detonated || !(level() instanceof ServerLevel level)) {
			return;
		}
		detonated = true;
		MissileType type = getMissileType();
		if (targetMissile != null) {
			targetMissile.engagedBy = Math.max(0, targetMissile.engagedBy - 1);
		}
		discard();
		if (type.threat) {
			(inAir ? MissileStats.THREATS_SHOT_DOWN : MissileStats.GROUND_IMPACTS).incrementAndGet();
		} else {
			MissileStats.INTERCEPTOR_BURSTS.incrementAndGet();
		}
		MissileStats.log("{} {} at {} after {} ticks (aim {})", type, inAir ? "AIR-BURST" : "IMPACT", fmt(at), life, fmt(target));
		if (inAir) {
			Effects.airBurst(level, this, at, type);
		} else {
			Effects.groundImpact(level, this, at, type);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Client: exhaust trail

	private void clientTick() {
		MissileType type = getMissileType();
		Level level = level();
		Vec3 cur = position();
		Vec3 seg = cur.subtract(xo, yo, zo);
		double len = seg.length();
		Vec3 dir;
		if (len > 1e-4) {
			dir = seg.scale(1 / len);
		} else {
			float yaw = getYRot() * Mth.DEG_TO_RAD;
			float pitch = getXRot() * Mth.DEG_TO_RAD;
			dir = new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		}
		Vec3 nozzle = cur.subtract(dir.scale(1.5 * type.renderScale));
		int steps = Math.max(1, (int) Math.ceil(len / 0.6));
		boolean motor = isMotorOn();

		switch (type.trail) {
			case HEAVY -> {
				if (motor) {
					for (int i = 0; i < steps; i++) {
						Vec3 p = nozzle.subtract(seg.scale((double) i / steps));
						particle(level, ParticleTypes.FLAME, p, dir.scale(-0.25), 0.15);
						particle(level, ParticleTypes.LARGE_SMOKE, p, dir.scale(-0.05), 0.25);
						if (i % 2 == 0) {
							particle(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, p, Vec3.ZERO, 0.3);
						}
					}
				} else if (tickCount % 2 == 0) {
					particle(level, ParticleTypes.SMOKE, nozzle, Vec3.ZERO, 0.1);
				}
			}
			case MEDIUM -> {
				if (motor) {
					for (int i = 0; i < steps; i++) {
						Vec3 p = nozzle.subtract(seg.scale((double) i / steps));
						particle(level, ParticleTypes.FLAME, p, dir.scale(-0.2), 0.1);
						particle(level, ParticleTypes.LARGE_SMOKE, p, Vec3.ZERO, 0.15);
						if (i % 3 == 0) {
							particle(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, p, Vec3.ZERO, 0.2);
						}
					}
				} else {
					particle(level, ParticleTypes.SMOKE, nozzle, Vec3.ZERO, 0.05);
				}
			}
			case WHITE -> {
				if (motor) {
					particle(level, ParticleTypes.FLAME, nozzle, dir.scale(-0.3), 0.05);
					for (int i = 0; i < steps; i++) {
						Vec3 p = nozzle.subtract(seg.scale((double) i / steps));
						particle(level, ParticleTypes.CLOUD, p, Vec3.ZERO, 0.08);
						if (i % 2 == 0) {
							particle(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, p, Vec3.ZERO, 0.1);
						}
					}
				}
			}
			case JET -> {
				particle(level, ParticleTypes.SMALL_FLAME, nozzle, dir.scale(-0.1), 0.02);
				if (tickCount % 2 == 0) {
					particle(level, ParticleTypes.SMOKE, nozzle, Vec3.ZERO, 0.05);
				}
				if (motor && tickCount < 30) {
					for (int i = 0; i < steps; i++) {
						particle(level, ParticleTypes.LARGE_SMOKE, nozzle.subtract(seg.scale((double) i / steps)), Vec3.ZERO, 0.2);
					}
				}
			}
			case NONE -> {
				if (motor && tickCount < 25) {
					particle(level, ParticleTypes.FLAME, nozzle, dir.scale(-0.2), 0.05);
					particle(level, ParticleTypes.LARGE_SMOKE, nozzle, Vec3.ZERO, 0.1);
				} else if (tickCount % 6 == 0) {
					particle(level, ParticleTypes.SMOKE, nozzle, Vec3.ZERO, 0.02);
				}
			}
		}
	}

	private void particle(Level level, ParticleOptions type, Vec3 p, Vec3 v, double jitter) {
		level.addParticle(type, true, true,
				p.x + (random.nextDouble() - 0.5) * jitter, p.y + (random.nextDouble() - 0.5) * jitter, p.z + (random.nextDouble() - 0.5) * jitter,
				v.x + (random.nextDouble() - 0.5) * 0.02, v.y + (random.nextDouble() - 0.5) * 0.02, v.z + (random.nextDouble() - 0.5) * 0.02);
	}
}
