package com.stasdoto.airdefense.factory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.registry.ModBlockEntities;
import com.stasdoto.airdefense.registry.ModTickets;
import com.stasdoto.airdefense.vehicle.VehicleEntity;

/**
 * The factory's control desk: puts the building up by itself (a few blocks every tick), then makes what is ordered,
 * keeps it in stock, and restocks every vehicle standing within {@link #SUPPLY_RANGE} blocks.
 */
public class FactoryBlockEntity extends BlockEntity {
	public static final int SUPPLY_RANGE = 64;
	public static final int MAX_QUEUE = 12;
	private static final int BLOCKS_PER_TICK = 6;
	private static final int CLEAR_PER_TICK = 40;

	private BlockPos origin = BlockPos.ZERO;
	private Direction facing = Direction.NORTH;
	private int buildIndex;
	private boolean built = true;
	private boolean creative;
	private final int[] stock = new int[Product.values().length];
	private final List<Integer> queue = new ArrayList<>();
	private int progress;
	private List<FactoryBlueprint.Placement> blueprint;
	private boolean smoking;

	public FactoryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FACTORY, pos, state);
	}

	/** A factory kit was used: the desk is in place, the building goes up around it. */
	public void startConstruction(BlockPos origin, Direction facing, boolean creative) {
		this.origin = origin;
		this.facing = facing;
		this.creative = creative;
		this.built = false;
		this.buildIndex = 0;
		this.blueprint = null;
		setChanged();
	}

	public boolean isBuilt() {
		return built;
	}

	public boolean isCreative() {
		return creative;
	}

	public int buildPercent() {
		if (built) {
			return 100;
		}
		List<FactoryBlueprint.Placement> bp = blueprint();
		return bp.isEmpty() ? 100 : Math.min(99, buildIndex * 100 / bp.size());
	}

	public int[] stock() {
		return stock;
	}

	public List<Integer> queue() {
		return queue;
	}

	public int progress() {
		return progress;
	}

	private List<FactoryBlueprint.Placement> blueprint() {
		if (blueprint == null) {
			blueprint = FactoryBlueprint.build(origin, facing);
		}
		return blueprint;
	}

	// ------------------------------------------------------------------------------------------------
	// Ticking

	public static void serverTick(Level level, BlockPos pos, BlockState state, FactoryBlockEntity be) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		if (!be.built || !be.queue.isEmpty()) {
			if (server.getGameTime() % 20 == 0) {
				// Keeps working while the player is away (like the vehicles do).
				server.getChunkSource().addTicketWithRadius(ModTickets.VEHICLE, ChunkPos.containing(pos), 2);
			}
		}
		if (!be.built) {
			be.buildStep(server);
			return;
		}
		be.produce(server);
		if ((server.getGameTime() + pos.asLong()) % 100 == 0) {
			be.resupply(server);
		}
	}

	private void buildStep(ServerLevel level) {
		List<FactoryBlueprint.Placement> bp = blueprint();
		int placed = 0;
		int cleared = 0;
		while (buildIndex < bp.size() && placed < BLOCKS_PER_TICK && cleared < CLEAR_PER_TICK) {
			FactoryBlueprint.Placement p = bp.get(buildIndex++);
			BlockState current = level.getBlockState(p.pos());
			if (FactoryBlueprint.protectedBlock(current) || p.pos().equals(worldPosition)) {
				continue;
			}
			if (p.state().isAir()) {
				if (!current.isAir()) {
					level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					cleared++;
				}
				continue;
			}
			if (current.equals(p.state())) {
				continue;
			}
			BlockState s = Block.updateFromNeighbourShapes(p.state(), level, p.pos());
			level.setBlock(p.pos(), s, Block.UPDATE_ALL);
			placed++;
			// Fill hollows under the foundation so the floor does not hang in the air.
			if (p.pos().getY() == origin.getY() - 1) {
				for (int d = 1; d <= 4; d++) {
					BlockPos below = p.pos().below(d);
					BlockState b = level.getBlockState(below);
					if (!b.isAir() && b.getFluidState().isEmpty()) {
						break;
					}
					level.setBlock(below, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			if (placed == 1) {
				Vec3 c = Vec3.atCenterOf(p.pos());
				level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.01);
				if (level.getRandom().nextInt(3) == 0) {
					level.playSound(null, p.pos(), s.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
				}
			}
		}
		if (buildIndex >= bp.size()) {
			built = true;
			blueprint = null;
			level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
			for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 96 * 96)) {
				p.sendOverlayMessage(Component.translatable("message.airdefense.factory.built"));
			}
		}
		setChanged();
	}

	private void produce(ServerLevel level) {
		boolean working = !queue.isEmpty();
		if (working) {
			Product p = Product.byId(queue.getFirst());
			if (++progress >= p.time(creative)) {
				progress = 0;
				queue.removeFirst();
				stock[p.ordinal()]++;
				level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5f, 1.4f);
			}
			if (level.getGameTime() % 7 == 0) {
				level.playSound(null, worldPosition, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.4f, 0.7f + level.getRandom().nextFloat() * 0.3f);
			}
			setChanged();
		}
		if (working != smoking) {
			smoking = working;
			BlockPos top = FactoryBlueprint.chimneyTop(origin, facing);
			BlockState s = level.getBlockState(top);
			if (s.getBlock() instanceof CampfireBlock) {
				level.setBlock(top, s.setValue(CampfireBlock.LIT, working), Block.UPDATE_ALL);
			}
		}
	}

	/** Vehicles nearby take what they fire from the stock. */
	private void resupply(ServerLevel level) {
		AABB box = new AABB(worldPosition).inflate(SUPPLY_RANGE);
		for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, box, VehicleEntity::isAlive)) {
			Product p = Product.forVehicle(v.getVehicleType());
			if (p == null || stock[p.ordinal()] <= 0) {
				continue;
			}
			int needUnits = v.reserveSpace();
			int items = Math.min(stock[p.ordinal()], (needUnits + p.units - 1) / p.units);
			if (items > 0 && needUnits > 0) {
				stock[p.ordinal()] -= items;
				v.addReserve(items * p.units);
				setChanged();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Orders

	/** Takes the resources from the player (not in creative) and queues the order. */
	public Component order(Player player, Product product, int count) {
		if (!built) {
			return Component.translatable("message.airdefense.factory.building", buildPercent());
		}
		int done = 0;
		boolean free = creative || player.getAbilities().instabuild;
		for (int i = 0; i < count && queue.size() < MAX_QUEUE; i++) {
			if (!free && !takeCost(player, product)) {
				break;
			}
			queue.add(product.ordinal());
			done++;
		}
		setChanged();
		if (done == 0) {
			return Component.translatable(queue.size() >= MAX_QUEUE ? "message.airdefense.factory.queue_full" : "message.airdefense.factory.no_resources");
		}
		return Component.translatable("message.airdefense.factory.ordered", done, new ItemStack(product.item()).getHoverName());
	}

	/** Cancels the last order in the queue and gives the resources back. */
	public void cancelLast(Player player) {
		if (queue.isEmpty()) {
			return;
		}
		Product p = Product.byId(queue.removeLast());
		if (queue.isEmpty()) {
			progress = 0;
		}
		if (!creative && !player.getAbilities().instabuild) {
			for (Product.Cost c : p.cost) {
				give(player, new ItemStack(c.item(), c.count()));
			}
		}
		setChanged();
	}

	/** Everything in stock goes into the player's inventory (what does not fit falls at his feet). */
	public int takeAll(Player player) {
		int n = 0;
		for (Product p : Product.values()) {
			int count = stock[p.ordinal()];
			while (count > 0) {
				int k = Math.min(count, p.item().getDefaultMaxStackSize());
				give(player, new ItemStack(p.item(), k));
				count -= k;
				n += k;
			}
			stock[p.ordinal()] = 0;
		}
		setChanged();
		return n;
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack));
		}
	}

	private static boolean takeCost(Player player, Product product) {
		for (Product.Cost c : product.cost) {
			if (count(player, c) < c.count()) {
				return false;
			}
		}
		for (Product.Cost c : product.cost) {
			int left = c.count();
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack s = player.getInventory().getItem(i);
				if (s.is(c.item())) {
					int k = Math.min(left, s.getCount());
					s.shrink(k);
					left -= k;
				}
			}
		}
		return true;
	}

	public static int count(Player player, Product.Cost c) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(c.item())) {
				n += s.getCount();
			}
		}
		return n;
	}

	// ------------------------------------------------------------------------------------------------
	// Saving

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("ox", origin.getX());
		output.putInt("oy", origin.getY());
		output.putInt("oz", origin.getZ());
		output.putInt("facing", facing.get2DDataValue());
		output.putInt("build_index", buildIndex);
		output.putBoolean("built", built);
		output.putBoolean("creative", creative);
		output.putIntArray("stock", stock.clone());
		output.putIntArray("queue", queue.stream().mapToInt(Integer::intValue).toArray());
		output.putInt("progress", progress);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		origin = new BlockPos(input.getIntOr("ox", worldPosition.getX()), input.getIntOr("oy", worldPosition.getY()), input.getIntOr("oz", worldPosition.getZ()));
		facing = Direction.from2DDataValue(input.getIntOr("facing", 2));
		buildIndex = input.getIntOr("build_index", 0);
		built = input.getBooleanOr("built", true);
		creative = input.getBooleanOr("creative", false);
		int[] s = input.getIntArray("stock").orElse(new int[0]);
		System.arraycopy(s, 0, stock, 0, Math.min(s.length, stock.length));
		queue.clear();
		for (int q : input.getIntArray("queue").orElse(new int[0])) {
			queue.add(q);
		}
		progress = input.getIntOr("progress", 0);
		blueprint = null;
	}
}
