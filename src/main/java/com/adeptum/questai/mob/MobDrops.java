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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rolls the rare enchanted gear that variant mobs drop. Pure logic — the
 * forge turns a roll into a real ItemStack.
 */
public final class MobDrops {

	private static final int MAX_ENCHANTS = 2;

	/** One rolled piece of gear with its enchantments and levels. */
	public record GearRoll(GearItem item, Map<GearEnchant, Integer> enchants) {
	}

	private MobDrops() {
	}

	/**
	 * Rolls variant gear at the given chance.
	 *
	 * @return the rolled gear, or null when the roll misses
	 */
	public static GearRoll roll(final Random rng, final double chance) {
		if (rng.nextDouble() >= chance) {
			return null;
		}

		final GearItem item =
			GearItem.values()[rng.nextInt(GearItem.values().length)];
		final List<GearEnchant> available = new ArrayList<>(item.getEnchants());
		final int count = 1 + rng.nextInt(MAX_ENCHANTS);

		final Map<GearEnchant, Integer> enchants = new LinkedHashMap<>();
		for (int i = 0; i < count && !available.isEmpty(); i++) {
			final GearEnchant enchant =
				available.remove(rng.nextInt(available.size()));
			enchants.put(enchant, enchant.getMinLevel() + rng.nextInt(
				enchant.getMaxLevel() - enchant.getMinLevel() + 1));
		}
		return new GearRoll(item, enchants);
	}
}
