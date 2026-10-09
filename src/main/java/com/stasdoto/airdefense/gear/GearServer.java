package com.stasdoto.airdefense.gear;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.registry.ModItems;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.weapon.GrenadeEntity;

/**
 * 1.27: what the pouches do. B throws a grenade from a grenade pouch, H dresses a wound from the first-aid kit, magazine
 * pouches make reloading quicker ({@link #reloadFactor}), and a radio tells of missiles and drones heading your way.
 */
public final class GearServer {
	/** Dressing a wound from the kit on the vest: a second and a half (from the hand it takes two). */
	public static final int DRESS_TICKS = 30;
	private static final Map<UUID, Integer> DRESSING = new HashMap<>();
	/** Missiles each player has already been warned about. */
	private static final Map<UUID, Set<Integer>> WARNED = new HashMap<>();

	public static int grenadesThrown;
	public static int woundsDressed;
	public static int radioWarnings;

	private GearServer() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GearServer::tick);
	}

	/** Reload time with magazines at hand: 15% quicker for each magazine pouch, up to three. */
	public static float reloadFactor(net.minecraft.world.entity.LivingEntity e) {
		return 1.0f - 0.15f * Math.min(3, Pouches.worn(e, Pouch.MAG));
	}

	private static int find(Inventory inv, net.minecraft.world.item.Item item) {
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/** B: a grenade out of the pouch and thrown, the gun still in your hands. */
	public static void quickGrenade(ServerPlayer player) {
		if (Pouches.worn(player, Pouch.GRENADE) == 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.pouch.no_grenade_pouch"));
			return;
		}
		ItemStack proto = new ItemStack(ModItems.F1_GRENADE);
		if (player.getCooldowns().isOnCooldown(proto)) {
			return;
		}
		int slot = player.getAbilities().instabuild ? -2 : find(player.getInventory(), ModItems.F1_GRENADE);
		if (slot == -1) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.pouch.no_grenades"));
			return;
		}
		ServerLevel level = player.level();
		GrenadeEntity.throwFrom(level, player, player.isCrouching() ? 0.55f : 1.05f);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GRENADE_PIN, SoundSource.PLAYERS, 0.7f, 1.0f);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.THROW, SoundSource.PLAYERS, 0.6f, 1.0f);
		player.getCooldowns().addCooldown(proto, 24);
		if (slot >= 0) {
			player.getInventory().getItem(slot).shrink(1);
		}
		grenadesThrown++;
	}

	/** H: dress a wound from the first-aid kit on the vest. */
	public static void startDressing(ServerPlayer player) {
		if (Pouches.worn(player, Pouch.MEDKIT) == 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.pouch.no_medkit_pouch"));
			return;
		}
		if (DRESSING.containsKey(player.getUUID())) {
			return;
		}
		if (player.getHealth() >= player.getMaxHealth()) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.pouch.not_hurt"));
			return;
		}
		if (!player.getAbilities().instabuild && find(player.getInventory(), ModItems.MEDKIT) < 0) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.pouch.no_medkits"));
			return;
		}
		DRESSING.put(player.getUUID(), DRESS_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.MEDKIT, SoundSource.PLAYERS, 0.8f, 1.0f);
		player.sendOverlayMessage(Component.translatable("message.airdefense.pouch.dressing"));
	}

	public static boolean dressing(ServerPlayer player) {
		return DRESSING.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (!DRESSING.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Integer>> it = DRESSING.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Integer> e = it.next();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null || !p.isAlive()) {
					it.remove();
					continue;
				}
				int left = e.getValue() - 1;
				if (left > 0) {
					e.setValue(left);
					continue;
				}
				it.remove();
				int slot = p.getAbilities().instabuild ? -2 : find(p.getInventory(), ModItems.MEDKIT);
				if (slot == -1) {
					continue;
				}
				if (slot >= 0) {
					p.getInventory().getItem(slot).shrink(1);
				}
				p.addEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 1));
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
				p.sendOverlayMessage(Component.translatable("message.airdefense.pouch.dressed"));
				woundsDressed++;
			}
		}
		if (server.getTickCount() % 20 == 7) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (Pouches.worn(p, Pouch.RADIO) > 0) {
					radio(p);
				} else {
					WARNED.remove(p.getUUID());
				}
			}
		}
	}

	/** The radio: anything coming down within 250 blocks of you, heard once per missile. */
	private static void radio(ServerPlayer p) {
		ServerLevel level = p.level();
		Set<Integer> warned = WARNED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		AABB box = new AABB(p.getX() - 4000, -64, p.getZ() - 4000, p.getX() + 4000, 1000, p.getZ() + 4000);
		for (MissileEntity m : MissileEntity.find(level, box, m -> m.getMissileType().threat && !m.getMissileType().isDecoy()
				&& m.getMissileType().kind != MissileType.Kind.INTERCEPTOR && !m.hasDetonated())) {
			Vec3 t = m.getTarget();
			if (t == null || warned.contains(m.getId())) {
				continue;
			}
			double dx = t.x - p.getX();
			double dz = t.z - p.getZ();
			if (dx * dx + dz * dz > 250 * 250) {
				continue;
			}
			warned.add(m.getId());
			double speed = Math.max(0.2, m.getFlightVelocity().length());
			int seconds = (int) Math.round(m.position().distanceTo(t) / speed / 20.0);
			String kind = switch (m.getMissileType().kind) {
				case BALLISTIC -> "ballistic";
				case CRUISE -> "cruise";
				case DRONE -> "drone";
				default -> "rocket";
			};
			p.sendSystemMessage(Component.translatable("message.airdefense.radio.incoming",
					Component.translatable("message.airdefense.radio." + kind), seconds).withStyle(ChatFormatting.GOLD));
			level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.RADIO, SoundSource.PLAYERS, 0.7f, 1.0f);
			radioWarnings++;
		}
		if (warned.size() > 200) {
			warned.clear();
		}
	}
}
