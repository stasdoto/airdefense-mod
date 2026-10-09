package com.stasdoto.airdefense.nation;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 1.37: a passer-by in a town's streets. Only the player's own game knows them (they are made, walked and taken away on
 * the client round the player - see client.nation.Pedestrians), so a town full of people costs the server nothing.
 * They walk the pavements, cross at the zebras, are many in the morning and evening rush and few at night, and hurry
 * indoors when the sirens sound.
 */
public class PedestrianEntity extends PathfinderMob {
	/** Who this is (face, skin, hair), his clothes, a child or not. */
	public int look;
	public String outfit = "none;plains";
	public boolean kid;
	/** Where he is going: the block he walks to, his direction (0..3), crossing a street, hurrying. */
	public int toX;
	public int toZ;
	public int dir;
	public boolean crossing;
	public int crossLeft;
	public boolean hurry;
	public int hurryTicks;
	public double speed = 0.08;
	/** Set by the client: walks him one tick. */
	public static java.util.function.Consumer<PedestrianEntity> clientTick;

	public PedestrianEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			// Never on the server (none is ever made there).
			discard();
			return;
		}
		tickCount++;
		setOldPosAndRot();
		yBodyRotO = yBodyRot;
		yHeadRotO = yHeadRot;
		if (clientTick != null) {
			clientTick.accept(this);
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
