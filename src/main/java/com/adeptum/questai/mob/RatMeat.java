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

package com.adeptum.questai.mob;

import com.adeptum.questai.utility.GuiItems;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * What a rat leaves behind: a persistent-data-tagged morsel with its own
 * sprite, so identity survives renames. Rabbit as the base keeps it
 * cookable in any furnace without further wiring.
 */
public final class RatMeat {

	/** CustomModelData for the meat sprite, above the star fragment. */
	public static final int CMD = 100021;

	private static final NamespacedKey KEY =
		new NamespacedKey("questai", "rat_meat");
	private static final String ID = "rat_meat";

	private RatMeat() {
	}

	public static ItemStack create() {
		final ItemStack item = GuiItems.item(Material.RABBIT,
			"§7Rat Meat", List.of(
				"§7Stringy, but it fills a pan.",
				"§7Best not to ask where it ran."), CMD);
		final ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, ID);
		item.setItemMeta(meta);
		return item;
	}

	/** True only for genuine rat meat. */
	public static boolean isRatMeat(final ItemStack item) {
		if (item == null) {
			return false;
		}
		final ItemMeta meta = item.getItemMeta();
		return meta != null && ID.equals(meta.getPersistentDataContainer()
			.get(KEY, PersistentDataType.STRING));
	}
}
