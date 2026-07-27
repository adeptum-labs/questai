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

package com.adeptum.questai.craft;

import com.adeptum.questai.utility.GuiItems;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds and identifies the Craftsman's Whetstone: the one piece a village
 * toolsmith makes that no vanilla bench can, honing a worn tool or blade
 * back toward new.
 *
 * <p>Identity lives in a persistent data key rather than the display name,
 * so a renamed brick is still a brick.
 */
public final class CraftsmansWhetstone {

	/** Above the teleport stone at 100024; below nothing yet. */
	public static final int CMD = 100025;

	private static final Material MATERIAL = Material.BRICK;
	private static final NamespacedKey KEY =
		new NamespacedKey("questai", "craftsmans_whetstone");

	private CraftsmansWhetstone() {
	}

	/** A fresh stone, good for one honing. */
	public static ItemStack create() {
		final ItemStack item = GuiItems.item(MATERIAL,
			"\u00a7b\u00a7lCraftsman's Whetstone", List.of(
				"\u00a77Hones a worn piece back toward new.",
				"\u00a78Right-click with the worn piece in your off hand"), CMD);
		final ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer()
			.set(KEY, PersistentDataType.BYTE, (byte) 1);
		item.setItemMeta(meta);
		return item;
	}

	/** Whether the item is one of the toolsmith's stones. */
	public static boolean isWhetstone(final ItemStack item) {
		if (item == null || item.getType() != MATERIAL) {
			return false;
		}
		final ItemMeta meta = item.getItemMeta();
		return meta != null && meta.getPersistentDataContainer()
			.has(KEY, PersistentDataType.BYTE);
	}
}
