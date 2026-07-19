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

package com.adeptum.questai.star;

import com.adeptum.questai.utility.GuiItems;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The fallen star's prize: a persistent-data-tagged echo shard with its
 * own sprite, so identity survives renames.
 */
public final class StarFragment {

	/** CustomModelData for the fragment sprite, above the relic range. */
	public static final int CMD = 100020;

	private static final NamespacedKey KEY = new NamespacedKey("questai", "star");
	private static final String ID = "star_fragment";

	private StarFragment() {
	}

	public static ItemStack create() {
		final ItemStack item = GuiItems.item(Material.ECHO_SHARD,
			"\u00a7bStar Fragment", List.of(
				"\u00a77Still warm. It hums with",
				"\u00a77a light that is not fire.",
				"\u00a7aSell it to any named villager."), CMD);
		final ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, ID);
		item.setItemMeta(meta);
		return item;
	}

	/** True only for genuine star fragments. */
	public static boolean isFragment(final ItemStack item) {
		if (item == null) {
			return false;
		}
		final ItemMeta meta = item.getItemMeta();
		return meta != null && ID.equals(meta.getPersistentDataContainer()
			.get(KEY, PersistentDataType.STRING));
	}
}
