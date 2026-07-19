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

import com.adeptum.questai.service.DeliveryRecipientPicker.Candidate;
import com.adeptum.questai.villager.StoredLocation;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryRecipientPickerTest {

	private static Candidate candidate(final String name, final double x,
		final double z) {

		return new Candidate(UUID.randomUUID(), name, "FARMER",
			new StoredLocation(UUID.randomUUID(), x, 64, z));
	}

	@Test
	void returnsNullWithoutCandidates() {
		assertNull(DeliveryRecipientPicker.pick(List.of(), 0, 0));
	}

	@Test
	void returnsNullWhenAllCandidatesAreTooClose() {
		final List<Candidate> nearby = List.of(
			candidate("Close A", 10, 10), candidate("Close B", -30, 20));

		assertNull(DeliveryRecipientPicker.pick(nearby, 0, 0));
	}

	@Test
	void returnsTheOnlyEligibleCandidate() {
		final Candidate distant = candidate("Distant", 500, 0);
		final List<Candidate> candidates =
			List.of(candidate("Close", 10, 0), distant);

		assertSame(distant, DeliveryRecipientPicker.pick(candidates, 0, 0));
	}

	@Test
	void alwaysPicksFromTheFartherHalf() {
		final List<Candidate> candidates = List.of(
			candidate("Near", 100, 0),
			candidate("Mid", 500, 0),
			candidate("Far", 2000, 0),
			candidate("Farthest", 8000, 0));

		final Set<String> farHalf = Set.of("Far", "Farthest");
		for (int i = 0; i < 200; i++) {
			final Candidate picked =
				DeliveryRecipientPicker.pick(candidates, 0, 0);
			assertTrue(farHalf.contains(picked.name()),
				"Picked " + picked.name() + " outside the farther half");
		}
	}
}
