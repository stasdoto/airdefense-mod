package com.stasdoto.airdefense.nation;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A villager sent to work: a woodcutter, a stonecutter, an iron miner or a builder. Gatherers walk out to a tree or a
 * rock, work it for a while (the world is not damaged - the village's store simply gets what they bring back) and
 * carry it to the warehouse or the village square. Builders gather at the building going up; the more of them, the
 * faster it goes. Sent home, a worker is the same villager again - same face, trade, homes and memories.
 */
public class WorkerEntity extends PathfinderMob {
	@Override
	public void tick() {
		if (level().isClientSide()) {
			super.tick();
			return;
		}
		long perf0 = System.nanoTime();
		super.tick();
		com.stasdoto.airdefense.util.Perf.add(com.stasdoto.airdefense.util.Perf.WORKERS, System.nanoTime() - perf0);
		com.stasdoto.airdefense.util.Perf.over("worker at " + blockPosition().toShortString(), perf0);
	}

	public static final int WOOD = 0;
	public static final int STONE = 1;
	public static final int IRON = 2;
	public static final int BUILD = 3;
	public static final int JOBS = 4;

	private static final EntityDataAccessor<Integer> DATA_JOB = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> DATA_OUTFIT = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.STRING);

	/** Debug counters read by the automated test: trips started, work done, loads delivered. */
	public static int trips;
	public static int works;
	public static int deliveries;

	private int home = -1;
	/** Debug: what he is doing (for the test log). */
	public String debug = "";
	/** The villager he was, kept whole (trades, experience, gossip, his bed and workplace). */
	@Nullable
	private CompoundTag villager;
	private int carry;

	public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
		super(type, level);
		setDropChance(EquipmentSlot.MAINHAND, 0);
		setDropChance(EquipmentSlot.OFFHAND, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20)
				.add(Attributes.MOVEMENT_SPEED, 0.5)
				.add(Attributes.FOLLOW_RANGE, 48);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_JOB, WOOD);
		builder.define(DATA_LOOK, 0);
		builder.define(DATA_OUTFIT, "none;plains");
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 0.75));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 10f, 0.6, 0.75));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, SoldierEntity.class, 14f, 0.6, 0.75, e -> e instanceof SoldierEntity s && s.role() == SoldierEntity.BANDIT));
		goalSelector.addGoal(3, new WorkGoal(this));
		goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.5));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.4));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	// ------------------------------------------------------------------------------------------------
	// From villager to worker and back

	/** The villager goes to work: he is replaced by a worker with his face and clothes, and his tool for the job. */
	public static WorkerEntity hire(ServerLevel level, Villager v, int home, int job) {
		WorkerEntity w = new WorkerEntity(com.stasdoto.airdefense.registry.ModEntities.WORKER, level);
		w.snapTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), 0);
		w.home = home;
		w.villager = save(level, v);
		VillagerData d = v.getVillagerData();
		String profession = d.profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
		String type = d.type().unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		w.entityData.set(DATA_LOOK, Nations.lookOf(v.getUUID()));
		w.entityData.set(DATA_OUTFIT, profession + ";" + type);
		w.setJob(job);
		w.setPersistenceRequired();
		if (v.getCustomName() != null) {
			w.setCustomName(v.getCustomName());
		}
		v.discard();
		level.addFreshEntity(w);
		return w;
	}

	/** Home from work: the same villager as before, where the worker stands. */
	@Nullable
	public Villager goHome(ServerLevel level) {
		Villager v = null;
		if (villager != null) {
			Entity e = EntityType.loadEntityRecursive(EntityTypes.VILLAGER, villager, level, EntitySpawnReason.CONVERSION,
					net.minecraft.world.entity.EntityProcessor.NOP);
			if (e instanceof Villager loaded) {
				v = loaded;
			}
		}
		if (v == null) {
			v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.CONVERSION);
		}
		if (v == null) {
			return null;
		}
		v.snapTo(getX(), getY(), getZ(), getYRot(), 0);
		v.setHealth(Math.max(1, Math.min(v.getMaxHealth(), getHealth())));
		if (level.getEntity(v.getUUID()) != null) {
			v.setUUID(java.util.UUID.randomUUID());
		}
		discard();
		level.addFreshEntity(v);
		return v;
	}

	static CompoundTag save(ServerLevel level, Villager v) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		v.saveWithoutId(out);
		return out.buildResult();
	}

	// ------------------------------------------------------------------------------------------------
	// Who he is

	public int job() {
		return entityData.get(DATA_JOB);
	}

	public void setJob(int job) {
		entityData.set(DATA_JOB, Mth.clamp(job, 0, JOBS - 1));
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(switch (job()) {
			case WOOD -> Items.IRON_AXE;
			case STONE -> Items.STONE_PICKAXE;
			case IRON -> Items.IRON_PICKAXE;
			default -> Items.IRON_SHOVEL;
		}));
		setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		carry = 0;
	}

	public int look() {
		return entityData.get(DATA_LOOK);
	}

	/** "profession;biome" of the villager he was (for his clothes). */
	public String outfit() {
		return entityData.get(DATA_OUTFIT);
	}

	public int home() {
		return home;
	}

	@Nullable
	Settlement settlement() {
		if (!(level() instanceof ServerLevel sl)) {
			return null;
		}
		return Politics.get(sl.getServer()).settlements.get(home);
	}

	@Override
	public Component getTypeName() {
		return Component.translatable("entity.airdefense.worker." + switch (job()) {
			case WOOD -> "wood";
			case STONE -> "stone";
			case IRON -> "iron";
			default -> "build";
		});
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			// The village's soldiers and guards stand up for their workers.
			for (SoldierEntity s : level.getEntitiesOfClass(SoldierEntity.class, getBoundingBox().inflate(24),
					s -> s.role() != SoldierEntity.BANDIT && s.home() == home && s.getTarget() == null && !s.isFriend(attacker))) {
				s.setTarget(attacker);
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			Settlement s = settlement();
			if (s != null && s.eco.workers.remove(getUUID())) {
				Politics.get(level.getServer()).setDirty();
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return random.nextInt(3) == 0 ? SoundEvents.VILLAGER_AMBIENT : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("job", job());
		output.putInt("look", look());
		output.putString("outfit", outfit());
		output.putInt("home", home);
		output.putInt("carry", carry);
		if (villager != null) {
			output.store("villager", CompoundTag.CODEC, villager);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DATA_JOB, input.getIntOr("job", WOOD));
		entityData.set(DATA_LOOK, input.getIntOr("look", 0));
		entityData.set(DATA_OUTFIT, input.getStringOr("outfit", "none;plains"));
		home = input.getIntOr("home", -1);
		carry = input.getIntOr("carry", 0);
		villager = input.read("villager", CompoundTag.CODEC).orElse(null);
	}

	// ------------------------------------------------------------------------------------------------
	// Work

	/** One blow of the axe or pick: the arm swings, chips fly, a knock. */
	void strike(BlockPos at, BlockState state) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		Vec3 c = Vec3.atCenterOf(at);
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y + 0.3, c.z, 6, 0.25, 0.25, 0.25, 0.05);
			level.playSound(null, at, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.8f, 0.8f + random.nextFloat() * 0.3f);
		} else {
			level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 2, 0.3, 0.2, 0.3, 0.01);
		}
	}

	/** What a gatherer brings back from one trip. */
	static int load(int job) {
		return switch (job) {
			case WOOD, STONE -> 6;
			case IRON -> 3;
			default -> 0;
		};
	}

	/**
	 * Gatherers: out to a tree or rock, work it, back with the load. Builders: to the building going up, and work
	 * around it. With nothing to do (a builder without a building), the goal gives way to strolling about the village.
	 */
	static final class WorkGoal extends Goal {
		private static final int FIND = 0;
		private static final int GO = 1;
		private static final int WORK = 2;
		private static final int RETURN = 3;

		private final WorkerEntity w;
		private int state;
		private int timer;
		private int repath;
		@Nullable
		private BlockPos spot;
		private BlockState spotState = Blocks.AIR.defaultBlockState();

		WorkGoal(WorkerEntity w) {
			this.w = w;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!(w.level() instanceof ServerLevel)) {
				return false;
			}
			Settlement s = w.settlement();
			if (s == null) {
				return false;
			}
			if (w.job() == BUILD) {
				return s.eco.active() != null;
			}
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			repath = 0;
			if (w.carry > 0) {
				state = RETURN;
			} else {
				state = FIND;
			}
		}

		@Override
		public void stop() {
			w.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ServerLevel level = (ServerLevel) w.level();
			Settlement s = w.settlement();
			if (s == null) {
				return;
			}
			if (w.job() == BUILD) {
				build(level, s);
			} else {
				gather(level, s);
			}
		}

		private void build(ServerLevel level, Settlement s) {
			Building b = s.eco.active();
			if (b == null) {
				return;
			}
			BlockPos site = Economy.siteCenter(b, s);
			if (spot == null || --timer <= 0 || spot.distSqr(site) > 40 * 40) {
				spot = Economy.buildSpot(level, b, s, w.getRandom());
				timer = 200 + w.getRandom().nextInt(200);
				repath = 0;
			}
			double d = w.distanceToSqr(Vec3.atBottomCenterOf(spot));
			if (d > 3.0 * 3.0) {
				if (--repath <= 0) {
					repath = 20;
					w.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.65);
				}
			} else {
				w.getNavigation().stop();
				BlockPos look = Economy.lastPlaced(b, site);
				w.getLookControl().setLookAt(look.getX() + 0.5, look.getY() + 0.5, look.getZ() + 0.5);
				if (w.tickCount % 12 == 0) {
					w.strike(look, level.getBlockState(look));
				}
			}
		}

		private void gather(ServerLevel level, Settlement s) {
			switch (state) {
				case FIND -> {
					trips++;
					spot = Economy.workSpot(level, s, w.job(), w.getRandom());
					w.debug = "go " + (spot == null ? "-" : spot.toShortString());
					spotState = spot == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(spot);
					state = GO;
					timer = 0;
					repath = 0;
				}
				case GO -> {
					if (spot == null) {
						state = FIND;
						return;
					}
					timer++;
					Vec3 to = Vec3.atBottomCenterOf(spot);
					if (w.distanceToSqr(to) < 3.2 * 3.2) {
						w.getNavigation().stop();
						state = WORK;
						works++;
						w.debug = "work " + spot.toShortString();
						timer = 140 + w.getRandom().nextInt(80);
						return;
					}
					if (timer > 900) {
						// Cannot get there: try somewhere else.
						state = FIND;
						return;
					}
					if (--repath <= 0) {
						repath = 20;
						w.getNavigation().moveTo(to.x, to.y, to.z, 0.6);
					}
				}
				case WORK -> {
					if (spot == null) {
						state = FIND;
						return;
					}
					w.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5);
					if (timer % 14 == 0) {
						w.strike(spot, spotState);
					}
					if (--timer <= 0) {
						w.carry = load(w.job());
						w.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(switch (w.job()) {
							case WOOD -> Items.OAK_LOG;
							case STONE -> Items.COBBLESTONE;
							default -> Items.RAW_IRON;
						}));
						state = RETURN;
						timer = 0;
						repath = 0;
					}
				}
				default -> {
					BlockPos drop = Economy.dropOff(s);
					timer++;
					Vec3 to = Vec3.atBottomCenterOf(drop);
					if (w.distanceToSqr(to) < 3.5 * 3.5 || timer > 1200) {
						w.getNavigation().stop();
						Economy.deposit(level, s, w.job(), w.carry);
						deliveries++;
						w.debug = "delivered";
						w.carry = 0;
						w.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
						w.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, false);
						level.playSound(null, w.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4f, 0.8f);
						state = FIND;
						return;
					}
					if (--repath <= 0) {
						repath = 20;
						w.getNavigation().moveTo(to.x, to.y, to.z, 0.6);
					}
				}
			}
		}
	}
}
