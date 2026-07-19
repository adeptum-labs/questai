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

/**
 * The numeric bonuses granted by passive relics, kept pure so both the
 * base values and the boosts live in one place.
 */
public final class RelicEffects {

	private static final double QUILL_XP_MULTIPLIER = 1.25;
	private static final double BASE_RARE_CHANCE = 0.15;
	private static final double CHARM_RARE_CHANCE = 0.30;
	private static final double BASE_OFFER_CHANCE = 0.3;
	private static final double LOCKET_OFFER_CHANCE = 0.5;

	private RelicEffects() {
	}

	/** Quest reward XP, boosted while the Elder's Quill is carried. */
	public static int questXp(final int baseXp, final boolean hasQuill) {
		return hasQuill ? (int) Math.round(baseXp * QUILL_XP_MULTIPLIER) : baseXp;
	}

	/** Treasure rare-tier chance, doubled by the Prospector's Charm. */
	public static double treasureRareChance(final boolean hasCharm) {
		return hasCharm ? CHARM_RARE_CHANCE : BASE_RARE_CHANCE;
	}

	/** Villager quest-offer chance, raised by the Whispering Locket. */
	public static double questOfferChance(final boolean hasLocket) {
		return hasLocket ? LOCKET_OFFER_CHANCE : BASE_OFFER_CHANCE;
	}
}
