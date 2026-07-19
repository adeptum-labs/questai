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

import com.adeptum.questai.utility.GuiItems;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The sealed package item carried during delivery quests, identified by its
 * display name like the quest maps are.
 */
public final class DeliveryPackage {

	private static final String NAME_MARKER = "Sealed Package for ";

	private DeliveryPackage() {
	}

	public static ItemStack create(final String recipientName) {
		return GuiItems.item(Material.BUNDLE, "§6" + NAME_MARKER + recipientName,
			List.of("§7Handle with care.", "§7Deliver it unopened."));
	}

	public static boolean matches(final ItemStack item, final String recipientName) {
		if (item == null || item.getType() != Material.BUNDLE) {
			return false;
		}
		final ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return false;
		}
		final Component display = meta.displayName();
		return display != null
			&& display.toString().contains(NAME_MARKER + recipientName);
	}

	/**
	 * Removes one matching package from the inventory.
	 *
	 * @return true when a package was found and removed
	 */
	public static boolean removeOne(final Inventory inventory,
		final String recipientName) {

		for (int i = 0; i < inventory.getSize(); i++) {
			if (matches(inventory.getItem(i), recipientName)) {
				inventory.setItem(i, null);
				return true;
			}
		}
		return false;
	}
}
