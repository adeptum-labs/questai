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

import java.util.Random;

/**
 * The rarity rolls that decide when a natural spawn is forged into a
 * variant. Zombie outcomes partition a single roll so they stay mutually
 * exclusive.
 */
public final class MobRoll {

	public static final double GRAVEHULK_CHANCE = 0.04;
	public static final double GRAVELING_CHANCE = 0.05;
	public static final double CINDERLING_CHANCE = 0.04;

	private static final int MIN_SWARM = 3;
	private static final int MAX_SWARM = 5;

	private MobRoll() {
	}

	/** The variant a natural zombie spawn becomes, or null for none. */
	public static MobVariant rollZombie(final Random rng) {
		final double roll = rng.nextDouble();
		if (roll < GRAVEHULK_CHANCE) {
			return MobVariant.GRAVEHULK;
		}
		return roll < GRAVEHULK_CHANCE + GRAVELING_CHANCE
			? MobVariant.GRAVELING : null;
	}

	/** The variant a natural spider spawn becomes, or null for none. */
	public static MobVariant rollSpider(final Random rng) {
		return rng.nextDouble() < CINDERLING_CHANCE
			? MobVariant.CINDERLING : null;
	}

	/** Total graveling swarm size including the triggering spawn. */
	public static int swarmSize(final Random rng) {
		return MIN_SWARM + rng.nextInt(MAX_SWARM - MIN_SWARM + 1);
	}
}
