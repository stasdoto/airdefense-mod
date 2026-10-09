package com.stasdoto.airdefense.gear;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.stasdoto.airdefense.weapon.GunItem;
import com.stasdoto.airdefense.weapon.GunType;

/**
 * 1.27: an ammunition crate. Opened (right click) it hands out rounds for every gun on your belt (the hotbar): three
 * magazines' worth each, rockets and grenades two each.
 */
public class AmmoCrateItem extends Item {
	public AmmoCrateItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack crate = player.getItemInHand(hand);
		Map<Item, Integer> give = new LinkedHashMap<>();
		for (int i = 0; i < 9; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.getItem() instanceof GunItem g) {
				GunType t = g.gun;
				Item ammo = t.ammo();
				if (ammo == null) {
					continue;
				}
				int n = t.rocket() || t.magazine <= 1 ? 2 : Math.min(ammo.getDefaultMaxStackSize(), t.magazine * 3);
				give.merge(ammo, n, Math::max);
			}
		}
		if (give.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.airdefense.ammo_crate.no_guns"));
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			for (Map.Entry<Item, Integer> e : give.entrySet()) {
				ItemStack ammo = new ItemStack(e.getKey(), e.getValue());
				if (!player.getInventory().add(ammo)) {
					player.spawnAtLocation((net.minecraft.server.level.ServerLevel) level, ammo);
				}
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6f, 1.3f);
			player.sendOverlayMessage(Component.translatable("message.airdefense.ammo_crate.opened", give.size()));
		}
		crate.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.airdefense.ammo_crate").withStyle(ChatFormatting.GRAY));
	}
}
