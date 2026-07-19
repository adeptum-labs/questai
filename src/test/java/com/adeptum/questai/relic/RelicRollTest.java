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

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelicRollTest {

	@Test
	void hitRollAwardsAnUnownedRelic() {
		final Random rng = mock(Random.class);
		when(rng.nextDouble()).thenReturn(0.01);
		when(rng.nextInt(anyInt())).thenReturn(0);

		assertNotNull(RelicRoll.roll(rng,
			RelicRoll.QUEST_AWARD_CHANCE, Set.of()));
	}

	@Test
	void missedRollAwardsNothing() {
		final Random rng = mock(Random.class);
		when(rng.nextDouble()).thenReturn(0.99);

		assertNull(RelicRoll.roll(rng, RelicRoll.QUEST_AWARD_CHANCE, Set.of()));
		verify(rng, never()).nextInt(anyInt());
	}

	@Test
	void allOwnedAwardsNothing() {
		final Random rng = mock(Random.class);
		when(rng.nextDouble()).thenReturn(0.0);

		assertNull(RelicRoll.roll(rng, RelicRoll.QUEST_AWARD_CHANCE,
			EnumSet.allOf(QuestRelic.class)));
	}

	@Test
	void ownedRelicsAreExcludedFromThePick() {
		final Random rng = mock(Random.class);
		when(rng.nextDouble()).thenReturn(0.0);
		when(rng.nextInt(1)).thenReturn(0);

		final Set<QuestRelic> owned = EnumSet.allOf(QuestRelic.class);
		owned.remove(QuestRelic.PEDDLERS_BELL);

		assertSame(QuestRelic.PEDDLERS_BELL,
			RelicRoll.roll(rng, RelicRoll.QUEST_AWARD_CHANCE, owned));
	}
}
