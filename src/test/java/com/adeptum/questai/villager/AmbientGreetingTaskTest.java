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

package com.adeptum.questai.villager;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmbientGreetingTaskTest {

	private static final long COOLDOWN = 5 * 60 * 1000L;

	@Test
	void greetsOnceThenHonorsCooldown() {
		final AmbientGreetingTask task = new AmbientGreetingTask(null, null);
		final AmbientGreetingTask.PairKey key = new AmbientGreetingTask.PairKey(
			UUID.randomUUID(), UUID.randomUUID());

		assertTrue(task.shouldGreet(key, 1000L));
		assertFalse(task.shouldGreet(key, 1000L + COOLDOWN - 1));
		assertTrue(task.shouldGreet(key, 1000L + COOLDOWN));
	}

	@Test
	void parcelHintUsesItsOwnShorterCooldown() {
		final AmbientGreetingTask task = new AmbientGreetingTask(null, null);
		final AmbientGreetingTask.PairKey key = new AmbientGreetingTask.PairKey(
			UUID.randomUUID(), UUID.randomUUID());

		assertTrue(task.shouldHint(key, 1000L));
		assertFalse(task.shouldHint(key, 1000L + 29_000));
		assertTrue(task.shouldHint(key, 1000L + 30_000));

		// The greeting cooldown is tracked independently of the hint
		assertTrue(task.shouldGreet(key, 1000L + 30_000));
	}

	@Test
	void cooldownsAreIndependentPerPair() {
		final AmbientGreetingTask task = new AmbientGreetingTask(null, null);
		final UUID villager = UUID.randomUUID();
		final AmbientGreetingTask.PairKey first = new AmbientGreetingTask.PairKey(
			villager, UUID.randomUUID());
		final AmbientGreetingTask.PairKey second = new AmbientGreetingTask.PairKey(
			villager, UUID.randomUUID());

		assertTrue(task.shouldGreet(first, 1000L));
		assertTrue(task.shouldGreet(second, 1000L));
		assertFalse(task.shouldGreet(first, 2000L));
	}
}
