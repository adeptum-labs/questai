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

package com.adeptum.questai.teleport;

import com.adeptum.questai.utility.GuiItems;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds and identifies the Village Teleport Stone: a reward a fully
 * fortified village hands out that carries its holder back to the village.
 *
 * <p>The bound village is stored in a persistent data key rather than
 * matched by display name, so renaming can neither forge a stone nor break
 * the bond. A second key holds the village's name for the lore only.
 */
public final class VillageTeleportStone {

	/** Above the cooked rat meat at 100023; below nothing yet. */
	public static final int CMD = 100024;

	private static final Material MATERIAL = Material.HEART_OF_THE_SEA;
	private static final NamespacedKey VILLAGE_KEY =
		new NamespacedKey("questai", "teleport_village");
	private static final NamespacedKey NAME_KEY =
		new NamespacedKey("questai", "teleport_name");

	private VillageTeleportStone() {
	}

	/**
	 * A stone bound to the village with this row id and display name. The
	 * cooldown is written into the lore rather than left to be discovered:
	 * a stone that refuses without ever having said it would is read as
	 * broken, not as spent.
	 */
	public static ItemStack create(final String rowId, final String villageName,
		final int cooldownSeconds) {

		final ItemStack item = GuiItems.item(MATERIAL,
			"\u00a7b\u00a7lVillage Teleport Stone", List.of(
				"\u00a77Bound to \u00a76" + villageName,
				"\u00a7aRight-click to travel there.",
				"\u00a78Then cools for \u00a77" + duration(cooldownSeconds)
					+ "\u00a78 before it will answer again"), CMD);
		final ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer()
			.set(VILLAGE_KEY, PersistentDataType.STRING, rowId);
		meta.getPersistentDataContainer()
			.set(NAME_KEY, PersistentDataType.STRING, villageName);
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * A span of time as the stone speaks it: {@code 45s}, {@code 2m},
	 * {@code 1m 30s}. Anything under a second still reads as {@code 1s},
	 * because a countdown that ends on nothing looks like a refusal.
	 */
	public static String duration(final long seconds) {
		final long whole = Math.max(1, seconds);
		final long minutes = whole / 60;
		final long rest = whole % 60;
		if (minutes == 0) {
			return rest + "s";
		}
		return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
	}

	/** The village row id this item is bound to, or null for anything else. */
	public static String rowIdOf(final ItemStack item) {
		if (item == null) {
			return null;
		}
		final ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return null;
		}
		return meta.getPersistentDataContainer()
			.get(VILLAGE_KEY, PersistentDataType.STRING);
	}

	/** Whether the item is any village teleport stone. */
	public static boolean isStone(final ItemStack item) {
		return rowIdOf(item) != null;
	}

	/** Whether the item is a stone bound to this exact village. */
	public static boolean bound(final ItemStack item, final String rowId) {
		return rowId != null && rowId.equals(rowIdOf(item));
	}

	/** Whether any of these items is a stone bound to this village. */
	public static boolean holds(final ItemStack[] contents, final String rowId) {
		if (contents == null) {
			return false;
		}
		for (final ItemStack item : contents) {
			if (bound(item, rowId)) {
				return true;
			}
		}
		return false;
	}
}
