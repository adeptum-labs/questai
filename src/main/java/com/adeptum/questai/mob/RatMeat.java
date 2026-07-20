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
 * What a rat leaves behind, raw and cooked: persistent-data-tagged morsels
 * with their own sprites, so identity survives renames. Rabbit as the base
 * keeps furnaces accepting the raw form and the cooked form as nourishing
 * as any other meat, with no food-component wiring.
 */
public final class RatMeat {

	/** CustomModelData for the raw sprite, above the star fragment. */
	public static final int CMD = 100021;

	/** CustomModelData for the cooked sprite, above the rat body model. */
	public static final int COOKED_CMD = 100023;

	private static final NamespacedKey KEY =
		new NamespacedKey("questai", "rat_meat");
	private static final String ID = "rat_meat";
	private static final String COOKED_ID = "rat_meat_cooked";

	private RatMeat() {
	}

	public static ItemStack create() {
		return tagged(GuiItems.item(Material.RABBIT,
			"§7Rat Meat", List.of(
				"§7Stringy, but it fills a pan.",
				"§7Best not to ask where it ran."), CMD), ID);
	}

	public static ItemStack createCooked() {
		return tagged(GuiItems.item(Material.COOKED_RABBIT,
			"§7Cooked Rat Meat", List.of(
				"§7Seared past all objection.",
				"§7Tastes almost like chicken."), COOKED_CMD), COOKED_ID);
	}

	/** True only for genuine raw rat meat. */
	public static boolean isRatMeat(final ItemStack item) {
		return ID.equals(idOf(item));
	}

	/** True only for genuine cooked rat meat. */
	public static boolean isCookedRatMeat(final ItemStack item) {
		return COOKED_ID.equals(idOf(item));
	}

	private static ItemStack tagged(final ItemStack item, final String id) {
		final ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
		item.setItemMeta(meta);
		return item;
	}

	private static String idOf(final ItemStack item) {
		if (item == null) {
			return null;
		}
		final ItemMeta meta = item.getItemMeta();
		return meta == null ? null
			: meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
	}
}
