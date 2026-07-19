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

package com.adeptum.questai.utility;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shared builder for the display-only ItemStacks used in GUI inventories.
 */
public final class GuiItems {

	private GuiItems() {
	}

	public static ItemStack item(final Material material, final String displayName) {
		return item(material, displayName, null, null);
	}

	public static ItemStack item(final Material material, final String displayName,
		final List<String> lore) {

		return item(material, displayName, lore, null);
	}

	public static ItemStack item(final Material material, final String displayName,
		final List<String> lore, final Integer customModelData) {

		final ItemStack stack = new ItemStack(material);
		final ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(displayName));
		if (lore != null) {
			meta.setLore(lore);
		}
		if (customModelData != null) {
			meta.setCustomModelData(customModelData);
		}
		stack.setItemMeta(meta);
		return stack;
	}
}
