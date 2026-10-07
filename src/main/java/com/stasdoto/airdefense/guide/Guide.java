package com.stasdoto.airdefense.guide;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * The guide: a book that explains the whole mod in a few short pages (vehicles, tablet, air defence, factory,
 * weapons, villages, building, army, people, wars). Every player gets one the first time he joins a world; more are in
 * the mod's creative tab. The pages are translated, so the book reads in the player's own language.
 */
public final class Guide {
	public static final int PAGES = 23;
	private static final String TAG = "airdefense_guide";

	private Guide() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (!player.entityTags().contains(TAG)) {
				player.addTag(TAG);
				ItemStack book = book();
				if (!player.getInventory().add(book)) {
					player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY() + 0.5,
							player.getZ(), book));
				}
			}
		});
	}

	public static ItemStack book() {
		List<Filterable<Component>> pages = new ArrayList<>();
		for (int i = 1; i <= PAGES; i++) {
			pages.add(Filterable.passThrough(Component.translatable("guide.airdefense.page." + i)));
		}
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("ПВО"), "Stasdoto", 0, pages, true));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("guide.airdefense.title"));
		return stack;
	}
}
