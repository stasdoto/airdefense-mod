package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.entity.EntityTypeTest;

import com.stasdoto.airdefense.map.MapServer;

/** Server side of the village screen and of the villages and soldiers on the tablet map. */
public final class NationNet {
	private NationNet() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(NationActionPayload.TYPE, NationActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SettlementInfoPayload.TYPE, SettlementInfoPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NationMapPayload.TYPE, NationMapPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VillageEconomyPayload.TYPE, VillageEconomyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NationActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		// Shift + right click on a villager: the village's affairs (trading is a plain right click).
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (entity instanceof Villager villager && !player.isShiftKeyDown() && !player.isSpectator()
					&& Economy.foodPoints(player.getItemInHand(hand)) > 0) {
				if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
					Economy.feed(sl, sp, villager, player.getItemInHand(hand));
				}
				return InteractionResult.SUCCESS;
			}
			if (!(entity instanceof Villager) || !player.isShiftKeyDown() || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
				Settlement s = Politics.get(sl.getServer()).settlementAt(entity.blockPosition());
				if (s == null) {
					sp.sendOverlayMessage(Component.translatable("nation.airdefense.no_village"));
				} else {
					sendInfo(sl, sp, s, true);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void handle(ServerPlayer player, NationActionPayload p) {
		ServerLevel level = player.level();
		Politics politics = Politics.get(level.getServer());
		Settlement s = politics.settlements.get(p.settlement());
		if (s == null) {
			return;
		}
		boolean near = player.blockPosition().distSqr(s.center) < 96 * 96;
		boolean tablet = com.stasdoto.airdefense.item.DesignatorItem.held(player) != null;
		switch (p.action()) {
			case NationActionPayload.INFO -> {
			}
			case NationActionPayload.BUY -> {
				if (near) {
					Nations.buyCharter(level, player, s);
				}
			}
			case NationActionPayload.TAKE -> {
				if (player.getAbilities().instabuild) {
					Nations.takeOver(level, player, s);
				}
			}
			case NationActionPayload.MOBILIZE -> {
				if (near || tablet) {
					Nations.mobilize(level, player, s, Math.max(1, p.a()));
				}
			}
			case NationActionPayload.DEMOBILIZE -> Nations.demobilize(level, player, s);
			case NationActionPayload.ORDER -> {
				if (tablet) {
					Nations.order(level, player, s, MapServer.ground(level, p.x(), p.y(), p.z()));
				}
			}
			case NationActionPayload.RECALL -> Nations.order(level, player, s, null);
			case NationActionPayload.JOB -> {
				if (near || tablet) {
					Economy.assign(level, player, s, p.a(), p.x());
				}
			}
			case NationActionPayload.BUILD -> {
				if (near || tablet) {
					Economy.order(level, player, s, BuildingType.byId(p.a()));
				}
			}
			case NationActionPayload.CANCEL -> Economy.cancel(level, player, s, p.a());
			case NationActionPayload.DONATE -> {
				if (near) {
					Economy.donate(level, player, s);
				} else {
					player.sendOverlayMessage(Component.translatable("nation.airdefense.eco.come_closer"));
				}
			}
			case NationActionPayload.VEHICLE -> {
				if (near || tablet) {
					Economy.orderVehicle(level, player, s, com.stasdoto.airdefense.vehicle.VehicleType.byId(p.a()));
				}
			}
			case NationActionPayload.WORKERS_HOME -> Economy.allHome(level, player, s);
			case NationActionPayload.CALM -> {
				if (near || tablet) {
					Unrest.calm(level, player, s);
				}
			}
			case NationActionPayload.DECLARE_WAR -> {
				if (near || tablet) {
					War.playerDeclares(level, player, s);
				}
			}
			case NationActionPayload.PEACE -> {
				if (near || tablet) {
					War.proposePeace(level, player, s);
				}
			}
			case NationActionPayload.TRIBUTE -> {
				if (near || tablet) {
					War.payTribute(level, player, s);
				}
			}
			case NationActionPayload.REBUILD -> {
				if (near || tablet) {
					Economy.rebuild(level, player, s, p.a(), BuildingType.byId(p.x()));
				}
			}
			case NationActionPayload.MARKET -> {
				if (near || tablet) {
					Market.trade(level, player, s, p.a(), p.x() == 1);
				}
			}
			case NationActionPayload.DEMOLISH -> {
				if (near || tablet) {
					Economy.demolish(level, player, s, p.a());
				}
			}
			case NationActionPayload.OPEN -> {
				if (near || tablet && Economy.owner(politics, s, player)) {
					sendInfo(level, player, s, true);
				}
				return;
			}
			default -> {
				return;
			}
		}
		sendInfo(level, player, s, false);
		if (tablet) {
			sendMap(level, player);
		}
	}

	public static void sendInfo(ServerLevel level, ServerPlayer player, Settlement s, boolean open) {
		Politics p = Politics.get(level.getServer());
		Country c = p.country(s.country);
		boolean mine = c != null && player.getUUID().equals(c.owner);
		s.population = Nations.villagers(level, s).size() + Economy.workers(level, s).size();
		Component problem = Nations.charterProblem(level, p, s, player);
		String elder = "";
		if (s.elder != null && level.getEntity(s.elder) instanceof Villager v && v.getCustomName() != null) {
			elder = v.getCustomName().getString();
		}
		// War with the country that owns this village: 0 = not possible, 1 = can be declared, 2 = at war.
		Country own = p.countryOwnedBy(player.getUUID());
		int war = 0;
		int tribute = 0;
		if (c != null && c.owner == null && !mine) {
			war = own != null && own.atWarWith(c.id) ? 2 : 1;
			tribute = own != null ? War.tribute(own, c) : 0;
		}
		ServerPlayNetworking.send(player, new SettlementInfoPayload(open, s.id, s.name, c == null ? "" : c.name, c == null ? 0 : c.argb(),
				c != null && c.cityState, mine, s.isCity() ? s.citizens : Nations.villagers(level, s).size(), Nations.guards(level, s).size(), Nations.soldiers(level, s).size(),
				mine ? Nations.mobilizable(level, s) : 0, Nations.reputation(level, s, player), Nations.charterPrice(p, s),
				problem == null ? "" : problem.getString(), player.getAbilities().instabuild, elder, war, tribute));
		if (mine) {
			sendEconomy(level, player, s);
		}
	}

	/** The household of one of the player's villages. */
	public static void sendEconomy(ServerLevel level, ServerPlayer player, Settlement s) {
		VillageEconomy e = s.eco;
		List<Integer> jobs = new ArrayList<>();
		for (int n : Economy.jobCounts(level, s)) {
			jobs.add(n);
		}
		List<Integer> built = new ArrayList<>();
		for (BuildingType t : BuildingType.values()) {
			built.add(e.count(t));
		}
		List<Integer> queue = new ArrayList<>();
		for (Building b : e.buildings) {
			if (!b.done) {
				queue.add(b.type.ordinal() * 1000 + b.percent());
			}
		}
		Building active = e.active();
		int builders = active == null ? 0 : Economy.buildersAt(level, s, Economy.siteCenter(active, s));
		List<Integer> hangar = new ArrayList<>();
		for (int code : e.hangar) {
			hangar.add(code % 100);
		}
		Unrest.Mood mood = Unrest.mood(level, Politics.get(level.getServer()), s);
		List<Integer> reasons = new ArrayList<>();
		for (int[] r : mood.reasons()) {
			reasons.add(r[0] * 1000 + r[1] + 500);
		}
		ServerPlayNetworking.send(player, new VillageEconomyPayload(s.id, e.stock[0], e.stock[1], e.stock[2], e.cap(),
				player.getAbilities().instabuild, jobs, Economy.free(level, s).size(), Economy.beds(level, s, false), Economy.beds(level, s, true),
				e.births, built, queue, builders, e.count(BuildingType.HANGAR) > 0, hangar, Economy.hangarPercent(s),
				Economy.birthEvery(level, Politics.get(level.getServer()), s), mood.value(), reasons,
				s.riot ? Unrest.rebels(level, s).size() : 0, Unrest.calmPrice(s),
				extra(Politics.get(level.getServer()), s)));
	}

	/**
	 * The rest of the store for the village screen: oil, fuel, ammunition and their caps; food, weapons and their caps;
	 * food eaten and made a minute, minutes gone hungry; then the market's buy and sell price of every kind.
	 */
	static List<Integer> extra(Politics p, Settlement s) {
		VillageEconomy e = s.eco;
		List<Integer> out = new ArrayList<>(List.of(e.stock[VillageEconomy.OIL], e.stock[VillageEconomy.FUEL], e.stock[VillageEconomy.AMMO],
				e.liquidCap(), e.ammoCap(), e.stock[VillageEconomy.FOOD], e.stock[VillageEconomy.ARMS], e.foodCap(), e.armsCap(),
				Supply.foodNeed(s), Supply.foodMade(s), e.hungry));
		for (int k = 0; k < VillageEconomy.KINDS; k++) {
			int[] pr = Market.price(p, s, k);
			out.add(pr[0]);
			out.add(pr[1]);
		}
		return out;
	}

	/** Villages within 2000 blocks and the soldiers that are loaded within 700. */
	public static void sendMap(ServerLevel level, ServerPlayer player) {
		Politics p = Politics.get(level.getServer());
		Country own = p.countryOwnedBy(player.getUUID());
		List<NationMapPayload.Village> villages = new ArrayList<>();
		for (Settlement s : p.near(player.blockPosition(), 2000)) {
			Country c = p.country(s.country);
			villages.add(new NationMapPayload.Village(s.id, s.name, s.center.getX(), s.center.getY(), s.center.getZ(),
					c == null ? 0xFFE8E8E8 : c.argb(), c == null ? "" : c.name, c != null && own != null && c.id == own.id,
					s.people(), s.guardsAlive, s.soldiers.size(), s.flag.getX(), s.flag.getZ(), c != null && own != null && own.atWarWith(c.id),
					mapBuildings(s), s.radius, 0, c != null && c.capital == s.id));
			if (villages.size() >= 64) {
				break;
			}
		}
		List<NationMapPayload.Man> men = new ArrayList<>();
		for (SoldierEntity e : level.getEntities(EntityTypeTest.forClass(SoldierEntity.class),
				e -> e.isAlive() && e.distanceToSqr(player) < 700 * 700)) {
			Country c = p.country(e.country());
			men.add(new NationMapPayload.Man(e.getId(), (int) Math.floor(e.getX()), (int) Math.floor(e.getZ()), e.role(),
					e.role() == SoldierEntity.BANDIT ? 0xFF303030 : e.role() == SoldierEntity.REBEL ? 0xFFD03030 : c == null ? 0xFFE8E8E8 : c.argb(),
					own != null && e.country() == own.id && e.role() != SoldierEntity.BANDIT && e.role() != SoldierEntity.REBEL, e.home()));
			if (men.size() >= 200) {
				break;
			}
		}
		ServerPlayNetworking.send(player, new NationMapPayload(villages, men));
	}

	/** A village's buildings for the map (see {@link NationMapPayload.Village}). */
	private static List<Integer> mapBuildings(Settlement s) {
		List<Integer> out = new ArrayList<>();
		for (Building b : s.eco.buildings) {
			if (b.type == BuildingType.ROADS || out.size() >= 1500) {
				continue;
			}
			out.add(b.type.ordinal() << 4 | b.facing.get2DDataValue() << 1 | (b.done ? 1 : 0));
			int dx = b.origin.getX() - s.center.getX();
			int dz = b.origin.getZ() - s.center.getZ();
			out.add(dx << 16 | dz & 0xFFFF);
			out.add(s.eco.buildings.indexOf(b));
		}
		return out;
	}

	/** For the map: where a village's soldiers should go (a picked point). */
	public static BlockPos target(int x, int y, int z) {
		return new BlockPos(x, y, z);
	}
}
