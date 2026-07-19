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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The rarity rolls that decide when a relic is awarded.
 */
public final class RelicRoll {

	public static final double QUEST_AWARD_CHANCE = 0.07;
	public static final double TREASURE_JACKPOT_CHANCE = 0.05;

	private RelicRoll() {
	}

	/**
	 * Rolls for a relic the player does not already own.
	 *
	 * @return the awarded relic, or null when the roll misses or every
	 *     relic is already owned
	 */
	public static QuestRelic roll(final Random rng, final double chance,
		final Set<QuestRelic> owned) {

		if (rng.nextDouble() >= chance) {
			return null;
		}
		final List<QuestRelic> available = new ArrayList<>();
		for (final QuestRelic relic : QuestRelic.values()) {
			if (!owned.contains(relic)) {
				available.add(relic);
			}
		}
		return available.isEmpty() ? null
			: available.get(rng.nextInt(available.size()));
	}
}
