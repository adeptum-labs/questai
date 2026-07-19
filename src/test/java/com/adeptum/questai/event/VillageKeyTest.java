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

package com.adeptum.questai.event;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageKeyTest {

	private static final UUID WORLD = UUID.randomUUID();

	@Test
	void nearbyLocationsShareACell() {
		assertEquals(VillageKey.from(WORLD, 10, 10),
			VillageKey.from(WORLD, 63, 63));
		assertNotEquals(VillageKey.from(WORLD, 10, 10),
			VillageKey.from(WORLD, 64, 10));
	}

	@Test
	void negativeCoordinatesBucketConsistently() {
		assertEquals(VillageKey.from(WORLD, -1, -1),
			VillageKey.from(WORLD, -64, -64));
		assertNotEquals(VillageKey.from(WORLD, -1, -1),
			VillageKey.from(WORLD, 0, 0));
	}

	@Test
	void differentWorldsNeverMatch() {
		assertNotEquals(VillageKey.from(WORLD, 10, 10),
			VillageKey.from(UUID.randomUUID(), 10, 10));
	}

	@Test
	void neighborhoodContainsNineDistinctCellsIncludingSelf() {
		final VillageKey key = VillageKey.from(WORLD, 100, 100);
		final List<VillageKey> neighborhood = key.neighborhood();

		assertEquals(9, neighborhood.size());
		assertEquals(9, new HashSet<>(neighborhood).size());
		assertTrue(neighborhood.contains(key));
	}
}
