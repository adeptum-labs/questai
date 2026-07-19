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

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageEventsTest {

	@Test
	void raidsStartOnlyEarlyInTheNight() {
		assertFalse(VillageEvents.canStartRaid(12_999, 0.0));
		assertTrue(VillageEvents.canStartRaid(13_000, 0.0));
		assertTrue(VillageEvents.canStartRaid(19_000, 0.0));
		assertFalse(VillageEvents.canStartRaid(19_001, 0.0));
		assertFalse(VillageEvents.canStartRaid(15_000, 0.25));
	}

	@Test
	void festivalsStartOnlyInDaytime() {
		assertFalse(VillageEvents.canStartFestival(999, 0.0));
		assertTrue(VillageEvents.canStartFestival(1_000, 0.0));
		assertTrue(VillageEvents.canStartFestival(11_000, 0.0));
		assertFalse(VillageEvents.canStartFestival(11_001, 0.0));
		assertFalse(VillageEvents.canStartFestival(5_000, 0.15));
	}

	@Test
	void raiderCountStaysInRange() {
		final Random rng = new Random(42);
		for (int i = 0; i < 200; i++) {
			final int count = VillageEvents.raiderCount(rng);
			assertTrue(count >= 6 && count <= 10);
		}
	}

	@Test
	void spawnRingHasRequestedSizeAndBoundedRadius() {
		final List<VillageEvents.SpawnOffset> ring =
			VillageEvents.spawnRing(8, new Random(42));

		assertEquals(8, ring.size());
		for (final VillageEvents.SpawnOffset offset : ring) {
			final double radius =
				Math.sqrt(offset.x() * offset.x() + offset.z() * offset.z());
			assertTrue(radius >= 26.0 && radius <= 34.0);
		}
	}

	@Test
	void raidTitleNamesTheHordeSize() {
		assertEquals("a horde of 8 walking dead", VillageEvents.raidTitle(8));
	}

	@Test
	void festivalXpAddsHalfWithRounding() {
		assertEquals(150, VillageEvents.festivalXp(100));
		assertEquals(50, VillageEvents.festivalXp(33));
	}
}
