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

package com.adeptum.questai.service;

import com.adeptum.questai.villager.StoredLocation;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chooses a delivery recipient from known villagers, preferring distant
 * ones: candidates closer than the minimum distance are ignored and the
 * pick always lands in the farther half of what remains.
 */
public final class DeliveryRecipientPicker {

	private static final double MIN_DISTANCE_SQUARED = 50.0 * 50.0;

	public record Candidate(UUID uuid, String name, String profession,
		StoredLocation location) {
	}

	private DeliveryRecipientPicker() {
	}

	/**
	 * Picks a recipient for a delivery starting at the given origin, or
	 * null when no candidate is far enough away.
	 */
	public static Candidate pick(final List<Candidate> candidates,
		final double originX, final double originZ) {

		final List<Candidate> eligible = candidates.stream()
			.filter(c -> c.location().distanceSquaredXz(originX, originZ)
				> MIN_DISTANCE_SQUARED)
			.sorted(Comparator.comparingDouble((Candidate c) ->
				c.location().distanceSquaredXz(originX, originZ)).reversed())
			.toList();

		if (eligible.isEmpty()) {
			return null;
		}
		final int farHalf = (eligible.size() + 1) / 2;
		return eligible.get(ThreadLocalRandom.current().nextInt(farHalf));
	}
}
