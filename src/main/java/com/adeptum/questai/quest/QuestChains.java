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

package com.adeptum.questai.quest;

import com.adeptum.questai.relic.RelicRoll;
import com.adeptum.questai.villager.ChainState;
import java.util.Random;

/**
 * The tunables and pure decisions behind multi-step quest chains: when a
 * chain opens, how long it runs, how step rewards scale and how the
 * narrative context ties a step to the previous one.
 */
public final class QuestChains {

	public static final double OPEN_CHANCE = 0.35;

	/** Total chain length including the trigger quest. */
	public static final int MIN_LENGTH = 2;
	public static final int MAX_LENGTH = 3;

	private static final double STEP_TWO_MULTIPLIER = 1.5;
	private static final double STEP_THREE_MULTIPLIER = 2.0;
	private static final int FINALE_RELIC_FACTOR = 3;

	private QuestChains() {
	}

	/** True when a completed quest opens a chain of follow-ups. */
	public static boolean opens(final Random rng) {
		return rng.nextDouble() < OPEN_CHANCE;
	}

	/** Rolls the total chain length. */
	public static int rollLength(final Random rng) {
		return MIN_LENGTH + rng.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
	}

	/** Reward XP for a chain step; later steps pay better. */
	public static int scaledReward(final int baseXp, final int step) {
		if (step <= 1) {
			return baseXp;
		}
		final double multiplier =
			step == 2 ? STEP_TWO_MULTIPLIER : STEP_THREE_MULTIPLIER;
		return (int) Math.round(baseXp * multiplier);
	}

	/** True when the given step concludes the chain. */
	public static boolean isFinale(final int step, final int length) {
		return step >= length;
	}

	/** The boosted relic chance used when a chain finale completes. */
	public static double finaleRelicChance() {
		return RelicRoll.QUEST_AWARD_CHANCE * FINALE_RELIC_FACTOR;
	}

	/** Narrative prompt fragment tying a chain step to the previous one. */
	public static String context(final ChainState chain) {
		return "The player just completed '" + chain.lastTitle()
			+ "' for you; this task continues that matter."
			+ (isFinale(chain.step(), chain.length())
				? " This is the final favor you will ask of them in this"
					+ " matter, so make it sound like the conclusion."
				: " Hint that the matter is not yet finished.");
	}
}
