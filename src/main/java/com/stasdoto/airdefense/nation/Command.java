package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.map.MapActionPayload;
import com.stasdoto.airdefense.map.MapServer;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * 2.0: command any country. From the tablet map the player takes command of any country of the world - not only his
 * own - and gives it the orders a country's arsenal takes: put up air defence, launchers and radars (where he clicks,
 * or by themselves near its borders), strike a point with its launchers, go to war with another country or make peace.
 * A strike given "as" another country is that country's: its missiles carry its flag, and the air defence of the
 * country they fly at shoots them down like any enemy's - his own included. In creative it is all free; in survival
 * the vehicles are paid from that country's towns' stores and the strikes from its depots.
 *
 * <p>Who may: everybody in his own world, creative players on a server (operators always); the game rule
 * {@code airdefense:command_any_country} (on by default) turns it off for all but operators.
 */
public final class Command {
	/** The game rule: may players command other countries (operators always may). */
	public static GameRule<Boolean> RULE;
	/** Who commands which country now (not saved: after a restart everybody is back with his own). */
	private static final Map<UUID, Integer> COMMANDING = new HashMap<>();
	/** How far from the country's nearest town a point may be picked for a vehicle (blocks). */
	public static final int REACH = 2500;

	/** For the tests: orders carried out, by kind. */
	public static int taken;
	public static int placed;
	public static int strikes;
	public static int roundsSent;
	public static int wars;
	public static int peaces;
	public static int claimed;

	private Command() {
	}

	public static void init() {
		RULE = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC).buildAndRegister(AirDefense.id("command_any_country"));
		PayloadTypeRegistry.serverboundPlay().register(CommandActionPayload.TYPE, CommandActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CommandStatePayload.TYPE, CommandStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CommandActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		PayloadTypeRegistry.serverboundPlay().register(StrikePlanPayload.TYPE, StrikePlanPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StrikePlanPayload.TYPE, (payload, context) -> launchPlan(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> COMMANDING.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> COMMANDING.clear());
	}

	// ------------------------------------------------------------------------------------------------
	// Who may, and whom he commands

	/** May this player command other countries? */
	public static boolean allowed(ServerPlayer player) {
		if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return true;
		}
		ServerLevel level = player.level();
		if (RULE == null || !level.getGameRules().get(RULE)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		// His own world (not opened to others): anything goes. On a server: players in creative.
		return server.isSingleplayer() && !server.isPublished() || player.getAbilities().instabuild;
	}

	/** The country this player commands now (-1: none, he plays for his own). */
	public static int commanding(ServerPlayer player) {
		Integer id = COMMANDING.get(player.getUUID());
		if (id == null) {
			return -1;
		}
		if (Politics.get(player.level().getServer()).country(id) == null || !allowed(player)) {
			COMMANDING.remove(player.getUUID());
			return -1;
		}
		return id;
	}

	/** Free orders: in creative. */
	private static boolean free(ServerPlayer player) {
		return player.getAbilities().instabuild;
	}

	// ------------------------------------------------------------------------------------------------
	// Orders

	public static void handle(ServerPlayer player, CommandActionPayload a) {
		ServerLevel level = player.level();
		Politics p = Politics.get(level.getServer());
		if (a.action() == CommandActionPayload.STATE) {
			send(level, player);
			return;
		}
		if (!allowed(player)) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.command.not_allowed"));
			COMMANDING.remove(player.getUUID());
			send(level, player);
			return;
		}
		// The orders go from the tablet's map, like every other order on it.
		if (DesignatorItem.held(player) == null) {
			return;
		}
		switch (a.action()) {
			case CommandActionPayload.TAKE -> {
				Country c = p.country(a.country());
				if (c == null) {
					player.sendOverlayMessage(Component.translatable("nation.airdefense.command.no_country"));
				} else {
					COMMANDING.put(player.getUUID(), c.id);
					taken++;
					player.sendSystemMessage(Component.translatable("nation.airdefense.command.taken", c.name));
					AirDefense.LOGGER.info("[airdefense] {} takes command of {}", player.getName().getString(), c.name);
				}
			}
			case CommandActionPayload.RELEASE -> {
				if (COMMANDING.remove(player.getUUID()) != null) {
					player.sendSystemMessage(Component.translatable("nation.airdefense.command.released"));
				}
			}
			default -> {
				int id = commanding(player);
				Country c = p.country(id);
				if (c == null || c.id != a.country()) {
					// An order for a country he does not command (any more): only the state goes back.
					break;
				}
				switch (a.action()) {
					case CommandActionPayload.PLACE -> place(level, p, player, c, a);
					case CommandActionPayload.STRIKE -> strike(level, p, player, c, a);
					case CommandActionPayload.WAR, CommandActionPayload.PEACE -> warOrPeace(level, p, player, c, a);
					default -> {
					}
				}
			}
		}
		send(level, player);
		NationNet.sendMap(level, player);
		Atlas.sendPolitics(level, player, true);
	}

	@Nullable
	private static VehicleType type(int id) {
		VehicleType[] all = VehicleType.values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	/** Puts up a vehicle of the country: at a picked point (in reach of its towns), or by itself near its borders. */
	private static void place(ServerLevel level, Politics p, ServerPlayer player, Country c, CommandActionPayload a) {
		VehicleType t = type(a.a());
		if (t == null || CommandKit.kindOf(t) < 0) {
			return;
		}
		List<Settlement> towns = p.settlementsOf(c.id);
		if (towns.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.command.no_towns", c.name));
			return;
		}
		BlockPos at = null;
		Settlement town;
		if (a.b() == 1) {
			at = MapServer.ground(level, a.x(), MapActionPayload.Y_UNKNOWN, a.z());
			town = nearest(towns, at);
			double d = Math.sqrt(town.center.distSqr(at));
			if (d > REACH) {
				player.sendOverlayMessage(Component.translatable("nation.airdefense.command.too_far", c.name, (int) d, REACH));
				return;
			}
		} else {
			town = borderTown(p, c, towns);
		}
		Arsenals ars = Arsenals.get(level.getServer());
		if (ars.of(p, town).units.size() >= Arsenals.MAX_UNITS) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.command.full", town.name));
			return;
		}
		boolean free = free(player);
		if (!free) {
			int[] cost = Economy.vehicleCost(t);
			Settlement payer = Economy.canPay(town.eco, cost[0], cost[1], cost[2]) ? town : null;
			for (Settlement s : towns) {
				if (payer == null && Economy.canPay(s.eco, cost[0], cost[1], cost[2])) {
					payer = s;
				}
			}
			if (payer == null) {
				player.sendOverlayMessage(Economy.missing(town.eco, cost[0], cost[1], cost[2]));
				return;
			}
			Economy.pay(payer.eco, cost[0], cost[1], cost[2]);
			p.setDirty();
		}
		VehicleEntity v = ars.orderUnit(level, p, town, t, at, free);
		placed++;
		Component name = Component.translatable("entity.airdefense." + t.id);
		player.sendOverlayMessage(Component.translatable(v != null ? "nation.airdefense.command.placed_now" : "nation.airdefense.command.placed",
				c.name, name, town.name));
		level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.3f);
		AirDefense.LOGGER.info("[airdefense] by order: {} puts up {} at {} ({})", c.name, t.id, town.name, at == null ? "its place" : at.toShortString());
	}

	/** The country's town nearest an enemy (its border), else its capital. */
	private static Settlement borderTown(Politics p, Country c, List<Settlement> towns) {
		Settlement best = null;
		double bestD = Double.MAX_VALUE;
		for (int enemy : c.wars) {
			for (Settlement e : p.settlementsOf(enemy)) {
				for (Settlement s : towns) {
					double d = s.center.distSqr(e.center);
					if (d < bestD) {
						bestD = d;
						best = s;
					}
				}
			}
		}
		if (best != null) {
			return best;
		}
		Settlement cap = p.settlements.get(c.capital);
		return cap != null && cap.country == c.id ? cap : towns.getFirst();
	}

	private static Settlement nearest(List<Settlement> towns, BlockPos at) {
		Settlement best = towns.getFirst();
		for (Settlement s : towns) {
			if (s.center.distSqr(at) < best.center.distSqr(at)) {
				best = s;
			}
		}
		return best;
	}

	/** The country's launchers fire at a point. */
	private static void strike(ServerLevel level, Politics p, ServerPlayer player, Country c, CommandActionPayload a) {
		VehicleType kind = a.a() < 0 ? null : type(a.a());
		if (a.a() >= 0 && (kind == null || !CommandKit.canStrikeWith(kind))) {
			return;
		}
		int count = Math.clamp(a.b(), 1, CommandActionPayload.MAX_ROUNDS);
		BlockPos aim = MapServer.ground(level, a.x(), MapActionPayload.Y_UNKNOWN, a.z());
		int sent = Arsenals.get(level.getServer()).orderStrike(level, p, c, kind, count, aim, free(player));
		Settlement target = p.settlementAt(aim);
		Component where = target != null ? Component.literal(target.name) : Component.literal(aim.getX() + ", " + aim.getZ());
		if (sent < 0) {
			player.sendSystemMessage(Component.translatable("nation.airdefense.command.strike_none", c.name, where));
		} else if (sent == 0) {
			player.sendSystemMessage(Component.translatable("nation.airdefense.command.strike_empty", c.name));
		} else {
			strikes++;
			roundsSent += sent;
			player.sendSystemMessage(Component.translatable("nation.airdefense.command.strike", c.name, sent, where));
			AirDefense.LOGGER.info("[airdefense] by order: {} fires {} rounds ({}) at {}", c.name, sent, kind == null ? "any" : kind.id, aim.toShortString());
		}
	}

	private static void warOrPeace(ServerLevel level, Politics p, ServerPlayer player, Country c, CommandActionPayload a) {
		Country other = p.country(a.a());
		if (other == null || other.id == c.id) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.command.pick_other"));
			return;
		}
		if (a.action() == CommandActionPayload.WAR) {
			if (c.atWarWith(other.id)) {
				return;
			}
			War.declare(level, p, c, other, Component.translatable("nation.airdefense.command.why", player.getName()));
			wars++;
			level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 2f, 0.9f);
		} else {
			if (!c.atWarWith(other.id)) {
				player.sendOverlayMessage(Component.translatable("nation.airdefense.command.not_at_war", c.name, other.name));
				return;
			}
			War.peace(level, p, c, other);
			peaces++;
		}
		// The player hears of it even when neither country is his.
		if (!c.isMember(player.getUUID()) && !other.isMember(player.getUUID())) {
			player.sendSystemMessage(Component.translatable(a.action() == CommandActionPayload.WAR ? "nation.airdefense.war.declared"
					: "nation.airdefense.war.peace", c.name, other.name, Component.translatable("nation.airdefense.command.why", player.getName())));
		}
	}

	/** For the tests: planned strikes launched, launchers in them, rounds they sent. */
	public static int plans;
	public static int planLaunchers;
	public static int planRounds;

	/**
	 * 2.0: the strike planner's "Launch": every launcher fires at the point it was given, all at once - each checked
	 * here (it is the commanded country's, it is a launcher, the point is in its reach) and fired the same way as any
	 * other order of the country.
	 */
	public static void launchPlan(ServerPlayer player, StrikePlanPayload plan) {
		ServerLevel level = player.level();
		Politics p = Politics.get(level.getServer());
		if (!allowed(player) || DesignatorItem.held(player) == null) {
			player.sendOverlayMessage(Component.translatable("nation.airdefense.command.not_allowed"));
			return;
		}
		Country c = p.country(commanding(player));
		if (c == null || c.id != plan.country() || plan.shots().size() > StrikePlanPayload.MAX_SHOTS) {
			return;
		}
		Arsenals ars = Arsenals.get(level.getServer());
		boolean free = free(player);
		int launchers = 0;
		int rounds = 0;
		int out = 0;
		java.util.Set<Long> used = new java.util.HashSet<>();
		for (StrikePlanPayload.Shot shot : plan.shots()) {
			Settlement s = p.settlements.get(shot.town());
			if (s == null || s.country != c.id || !used.add(((long) shot.town() << 20) | (shot.index() & 0xFFFFF))) {
				continue;
			}
			Arsenals.Arsenal ar = ars.arsenals.get(s.id);
			if (ar == null || shot.index() < 0 || shot.index() >= ar.units.size()) {
				continue;
			}
			Arsenals.Unit u = ar.units.get(shot.index());
			if (!CommandKit.canStrikeWith(u.type)) {
				continue;
			}
			BlockPos aim = MapServer.ground(level, shot.x(), MapActionPayload.Y_UNKNOWN, shot.z());
			int salvo = shot.salvo() <= 0 ? u.type.launcher.salvo : Math.min(shot.salvo(), CommandActionPayload.MAX_ROUNDS);
			int n = ars.fireUnit(level, p, s, ar, u, p.settlementAt(aim), aim, salvo, free);
			if (n < 0) {
				out++;
			} else if (n > 0) {
				launchers++;
				rounds += n;
			}
		}
		Arsenals.orderedRounds += rounds;
		if (launchers > 0) {
			plans++;
			planLaunchers += launchers;
			planRounds += rounds;
			roundsSent += rounds;
		}
		player.sendSystemMessage(Component.translatable(launchers > 0 ? "nation.airdefense.command.plan_launched" : "nation.airdefense.command.plan_none",
				c.name, launchers, rounds, out));
		AirDefense.LOGGER.info("[airdefense] planned strike of {}: {} launchers, {} rounds, {} out of reach", c.name, launchers, rounds, out);
		send(level, player);
	}

	// ------------------------------------------------------------------------------------------------
	// Vehicles put down by hand

	/**
	 * A vehicle the player put down by hand belongs to the country whose town it stands in, when that is his own country
	 * or the one he commands: its air defence guards that country, its launchers fire under its flag. Elsewhere it stays
	 * his own (-1), as before.
	 */
	public static void claimPlaced(ServerLevel level, @Nullable ServerPlayer player, VehicleEntity v) {
		// Air defence, launchers and radars only: armour and aircraft of a country drive and fly by themselves.
		if (player == null || v.country != -1 || CommandKit.kindOf(v.getVehicleType()) < 0) {
			return;
		}
		Politics p = Politics.get(level.getServer());
		Settlement s = p.settlementAt(v.blockPosition());
		if (s == null) {
			// Just outside a town: its fields and approaches.
			double best = Double.MAX_VALUE;
			for (Settlement o : p.near(v.blockPosition(), 400)) {
				double d = Math.sqrt(o.center.distSqr(v.blockPosition())) - o.radius;
				if (d < 150 && d < best) {
					best = d;
					s = o;
				}
			}
		}
		Country c = s == null ? null : p.country(s.country);
		if (c == null || !c.isMember(player.getUUID()) && commanding(player) != c.id) {
			return;
		}
		v.country = c.id;
		claimed++;
		player.sendOverlayMessage(Component.translatable("nation.airdefense.command.belongs",
				Component.translatable("entity.airdefense." + v.getVehicleType().id), c.name));
	}

	// ------------------------------------------------------------------------------------------------
	// The state for the map

	public static void send(ServerLevel level, ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, CommandStatePayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, state(level, player));
	}

	public static CommandStatePayload state(ServerLevel level, ServerPlayer player) {
		Politics p = Politics.get(level.getServer());
		boolean allowed = allowed(player);
		Country c = p.country(commanding(player));
		if (c == null) {
			return new CommandStatePayload(allowed, -1, free(player), List.of(), 0, 0, 0, List.of());
		}
		Arsenals ars = Arsenals.get(level.getServer());
		int wood = 0;
		int stone = 0;
		int iron = 0;
		List<CommandStatePayload.Unit> units = new ArrayList<>();
		for (Settlement s : p.settlementsOf(c.id)) {
			wood += s.eco.stock[VillageEconomy.WOOD];
			stone += s.eco.stock[VillageEconomy.STONE];
			iron += s.eco.stock[VillageEconomy.IRON];
			Arsenals.Arsenal ar = ars.arsenals.get(s.id);
			if (ar == null) {
				continue;
			}
			for (int i = 0; i < ar.units.size(); i++) {
				Arsenals.Unit u = ar.units.get(i);
				if (u.lost || units.size() >= 240 || CommandKit.kindOf(u.type) < 0) {
					continue;
				}
				VehicleEntity v = u.entity == null ? null : level.getEntity(u.entity) instanceof VehicleEntity ve && ve.isAlive() ? ve : null;
				Vec3 at = Arsenals.where(level, s, u);
				int ammo = v != null ? (u.type.isLauncher() ? v.loadedRounds() : v.getAmmo()) : u.ammo;
				units.add(new CommandStatePayload.Unit(u.type.ordinal(), (int) Math.floor(at.x), (int) Math.floor(at.z), ammo, v != null, s.id, i));
			}
		}
		return new CommandStatePayload(allowed, c.id, free(player), new ArrayList<>(c.wars), wood, stone, iron, units);
	}
}
