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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobRollTest {

	@Test
	void zombieRollPartitionsIntoHulkSwarmOrNothing() {
		final Random rng = mock(Random.class);

		when(rng.nextDouble()).thenReturn(0.02);
		assertSame(MobVariant.GRAVEHULK, MobRoll.rollZombie(rng));

		when(rng.nextDouble()).thenReturn(0.045);
		assertSame(MobVariant.GRAVELING, MobRoll.rollZombie(rng));

		when(rng.nextDouble()).thenReturn(0.95);
		assertNull(MobRoll.rollZombie(rng));
	}

	@Test
	void spiderRollYieldsCinderlingOrNothing() {
		final Random rng = mock(Random.class);

		when(rng.nextDouble()).thenReturn(0.01);
		assertSame(MobVariant.CINDERLING, MobRoll.rollSpider(rng));

		when(rng.nextDouble()).thenReturn(0.5);
		assertNull(MobRoll.rollSpider(rng));
	}

	@Test
	void swarmSizeStaysBetweenThreeAndFive() {
		final Random rng = mock(Random.class);

		when(rng.nextInt(3)).thenReturn(0);
		assertEquals(3, MobRoll.swarmSize(rng));

		when(rng.nextInt(3)).thenReturn(2);
		assertEquals(5, MobRoll.swarmSize(rng));
	}
}
