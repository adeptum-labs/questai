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

package com.adeptum.questai.relic;

import com.adeptum.questai.utility.GuiItems;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds and identifies relic ItemStacks. Relics are tagged with a
 * persistent data key rather than matched by display name, so renaming
 * cannot forge or destroy one.
 */
public final class RelicItems {

	private static final NamespacedKey KEY = new NamespacedKey("questai", "relic");

	private RelicItems() {
	}

	public static ItemStack create(final QuestRelic relic) {
		final ItemStack item = GuiItems.item(relic.getMaterial(),
			relic.getDisplayName(), relic.getLore(), relic.getCustomModelData());
		final ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer()
			.set(KEY, PersistentDataType.STRING, relic.getId());
		item.setItemMeta(meta);
		return item;
	}

	/** Resolves the relic an item represents, or null for anything else. */
	public static QuestRelic relicOf(final ItemStack item) {
		if (item == null) {
			return null;
		}
		final ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return null;
		}
		final String id = meta.getPersistentDataContainer()
			.get(KEY, PersistentDataType.STRING);
		return id == null ? null : QuestRelic.fromId(id);
	}

	public static boolean holds(final ItemStack[] contents, final QuestRelic relic) {
		for (final ItemStack item : contents) {
			if (relicOf(item) == relic) {
				return true;
			}
		}
		return false;
	}

	/** All distinct relics present in the given inventory contents. */
	public static Set<QuestRelic> ownedRelics(final ItemStack... contents) {
		final Set<QuestRelic> owned = EnumSet.noneOf(QuestRelic.class);
		for (final ItemStack item : contents) {
			final QuestRelic relic = relicOf(item);
			if (relic != null) {
				owned.add(relic);
			}
		}
		return owned;
	}
}
