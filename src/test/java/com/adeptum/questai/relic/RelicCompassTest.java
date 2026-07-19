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

import com.adeptum.questai.service.DeliveryRecipientPicker.Candidate;
import com.adeptum.questai.villager.StoredLocation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelicCompassTest {

	private static Candidate candidate(final String name, final double x,
		final double z) {

		return new Candidate(UUID.randomUUID(), name, "FARMER",
			new StoredLocation(UUID.randomUUID(), x, 64, z));
	}

	@Test
	void pointsToTheNearestVillager() {
		final String line = RelicCompass.pointTo(List.of(
			candidate("Far Away", 5000, 0),
			candidate("Mira Bloom", 100, 0)), 0, 0);

		assertNotNull(line);
		assertTrue(line.startsWith("Mira Bloom"));
		assertTrue(line.contains("E"));
		assertTrue(line.contains("100 blocks"));
	}

	@Test
	void emptyCandidatesYieldNull() {
		assertNull(RelicCompass.pointTo(List.of(), 0, 0));
	}

	@Test
	void cardinalCoversAllEightDirections() {
		assertEquals("N", RelicCompass.cardinal(0, -10));
		assertEquals("NE", RelicCompass.cardinal(10, -10));
		assertEquals("E", RelicCompass.cardinal(10, 0));
		assertEquals("SE", RelicCompass.cardinal(10, 10));
		assertEquals("S", RelicCompass.cardinal(0, 10));
		assertEquals("SW", RelicCompass.cardinal(-10, 10));
		assertEquals("W", RelicCompass.cardinal(-10, 0));
		assertEquals("NW", RelicCompass.cardinal(-10, -10));
	}
}
