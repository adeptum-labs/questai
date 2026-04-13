/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai.quest;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Manages the physical quest log book item in player inventories.
 */
public class QuestLogBook {

	static final String TITLE = "Quest Log";

	public boolean isQuestLogBook(final ItemStack item) {
		if (item == null || item.getType() != Material.WRITTEN_BOOK) {
			return false;
		}
		final BookMeta meta = (BookMeta) item.getItemMeta();
		return meta != null && TITLE.equals(meta.getTitle());
	}

	public void ensure(final Player player) {
		final Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getSize(); i++) {
			if (isQuestLogBook(inv.getItem(i))) {
				return;
			}
		}

		final ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
		final BookMeta meta = (BookMeta) book.getItemMeta();
		meta.setTitle(TITLE);
		meta.setAuthor("QuestAI");
		meta.setPages(ChatColor.GOLD + "Right-click to open your Quest Log.");
		book.setItemMeta(meta);

		final var leftover = player.getInventory().addItem(book);
		if (leftover.isEmpty()) {
			player.sendMessage("\u00a7aA quest log has been added to your inventory.");
		} else {
			player.getWorld().dropItemNaturally(player.getLocation(), book);
			player.sendMessage("\u00a7cYour inventory is full!"
				+ " The quest log has been dropped at your location.");
		}
	}

	public void remove(final Player player) {
		final Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getSize(); i++) {
			if (isQuestLogBook(inv.getItem(i))) {
				inv.setItem(i, null);
				return;
			}
		}
	}
}
