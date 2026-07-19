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

import com.adeptum.questai.villager.StoredLocation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FestivalStateTest {

	private static final UUID WORLD = UUID.randomUUID();

	private static FestivalState festival() {
		return new FestivalState(VillageKey.of(WORLD, 0, 0),
			new StoredLocation(WORLD, 0, 64, 0), 10_000L);
	}

	@Test
	void activeUntilTheWindowEnds() {
		assertTrue(festival().active(9_999L));
		assertFalse(festival().active(10_000L));
	}

	@Test
	void coversOnlyTheEventRadiusInTheSameWorld() {
		final FestivalState festival = festival();

		assertTrue(festival.covers(WORLD, 48, 0));
		assertFalse(festival.covers(WORLD, 49, 0));
		assertFalse(festival.covers(UUID.randomUUID(), 0, 0));
	}
}
