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

import com.adeptum.questai.mob.GearEnchant;
import com.adeptum.questai.mob.GearItem;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * One thing that comes out of a finished commission.
 *
 * <p>Kept free of the registry-backed enchantment type in the same way the
 * mob loot tables are: gear is described as a {@link GearItem} plus levelled
 * {@link GearEnchant}s and only becomes a real stack when the forge builds
 * it. A masterwork names no vanilla item at all — the plugin's own item
 * stands behind it — so {@link #supply()} carries only the base material the
 * finished piece is skinned onto.
 *
 * @param kind which of the three shapes this output takes
 * @param gear the gear pattern, or null unless the kind is GEAR
 * @param enchants levels to apply to the gear; empty for the other kinds
 * @param supply the material handed over for SUPPLY and MASTERWORK
 * @param amount how many of it
 * @param displayName the name the finished piece carries, or null for plain
 *     supplies that should keep their vanilla name
 */
public record CommissionOutput(Kind kind, GearItem gear,
	Map<GearEnchant, Integer> enchants, Material supply, int amount,
	String displayName) {

	/** A named, enchanted piece of gear built by the forge. */
	public static CommissionOutput gear(final GearItem item, final String name,
		final Object... enchantPairs) {

		return new CommissionOutput(Kind.GEAR, item, levels(enchantPairs),
			item.getMaterial(), 1, name);
	}

	/** A plain stack of vanilla goods. */
	public static CommissionOutput supply(final Material material,
		final int amount) {

		return new CommissionOutput(Kind.SUPPLY, null, Map.of(), material,
			amount, null);
	}

	/** One of the plugin's own items, skinned onto a vanilla base. */
	public static CommissionOutput masterwork(final Material base,
		final String name) {

		return new CommissionOutput(Kind.MASTERWORK, null, Map.of(), base, 1,
			name);
	}

	private static Map<GearEnchant, Integer> levels(final Object... pairs) {
		final Map<GearEnchant, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((GearEnchant) pairs[i], (Integer) pairs[i + 1]);
		}
		return Map.copyOf(map);
	}

	/** The three shapes a commission's payout can take. */
	public enum Kind {
		GEAR, SUPPLY, MASTERWORK
	}
}
