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

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobDropsTest {

	@Test
	void missedRollDropsNothing() {
		final Random rng = mock(Random.class);
		when(rng.nextDouble()).thenReturn(0.99);

		assertNull(MobDrops.roll(rng, 0.08));
		verify(rng, never()).nextInt(anyInt());
	}

	@Test
	void hitRollYieldsApplicableEnchantsInRange() {
		for (int seed = 0; seed < 100; seed++) {
			final MobDrops.GearRoll roll =
				MobDrops.roll(new Random(seed), 1.0);

			assertNotNull(roll);
			assertTrue(roll.enchants().size() >= 1
				&& roll.enchants().size() <= 2);
			for (final Map.Entry<GearEnchant, Integer> entry
				: roll.enchants().entrySet()) {
				assertTrue(roll.item().getEnchants().contains(entry.getKey()));
				assertTrue(entry.getValue() >= entry.getKey().getMinLevel());
				assertTrue(entry.getValue() <= entry.getKey().getMaxLevel());
			}
		}
	}

	@Test
	void seededRollsAreDeterministic() {
		assertEquals(MobDrops.roll(new Random(42), 1.0),
			MobDrops.roll(new Random(42), 1.0));
	}
}
