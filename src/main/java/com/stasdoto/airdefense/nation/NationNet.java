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
		ServerPlayNetworking.registerGlobalReceiver(NationActionPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		// Shift + right click on a villager: the village's affairs (trading is a plain right click).
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
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
		s.population = Nations.villagers(level, s).size();
		Component problem = Nations.charterProblem(level, p, s, player);
		String elder = "";
		if (s.elder != null && level.getEntity(s.elder) instanceof Villager v && v.getCustomName() != null) {
			elder = v.getCustomName().getString();
		}
		ServerPlayNetworking.send(player, new SettlementInfoPayload(open, s.id, s.name, c == null ? "" : c.name, c == null ? 0 : c.argb(),
				c != null && c.cityState, mine, Nations.villagers(level, s).size(), Nations.guards(level, s).size(), Nations.soldiers(level, s).size(),
				mine ? Nations.mobilizable(level, s) : 0, Nations.reputation(level, s, player), Nations.charterPrice(p, s),
				problem == null ? "" : problem.getString(), player.getAbilities().instabuild, elder));
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
					s.population, s.guardsAlive, s.soldiers.size(), s.flag.getX(), s.flag.getZ()));
			if (villages.size() >= 64) {
				break;
			}
		}
		List<NationMapPayload.Man> men = new ArrayList<>();
		for (SoldierEntity e : level.getEntities(EntityTypeTest.forClass(SoldierEntity.class),
				e -> e.isAlive() && e.distanceToSqr(player) < 700 * 700)) {
			Country c = p.country(e.country());
			men.add(new NationMapPayload.Man(e.getId(), (int) Math.floor(e.getX()), (int) Math.floor(e.getZ()), e.role(),
					e.role() == SoldierEntity.BANDIT ? 0xFF303030 : c == null ? 0xFFE8E8E8 : c.argb(),
					own != null && e.country() == own.id && e.role() != SoldierEntity.BANDIT, e.home()));
			if (men.size() >= 200) {
				break;
			}
		}
		ServerPlayNetworking.send(player, new NationMapPayload(villages, men));
	}

	/** For the map: where a village's soldiers should go (a picked point). */
	public static BlockPos target(int x, int y, int z) {
		return new BlockPos(x, y, z);
	}
}
