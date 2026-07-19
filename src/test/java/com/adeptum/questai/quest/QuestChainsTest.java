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

package com.adeptum.questai.quest;

import com.adeptum.questai.villager.ChainState;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestChainsTest {

	@Test
	void opensBelowThresholdAndNotAbove() {
		final Random rng = mock(Random.class);

		when(rng.nextDouble()).thenReturn(0.34);
		assertTrue(QuestChains.opens(rng));

		when(rng.nextDouble()).thenReturn(0.36);
		assertFalse(QuestChains.opens(rng));
	}

	@Test
	void rollLengthReturnsOnlyTwoOrThree() {
		final Random rng = new Random(42);
		for (int i = 0; i < 100; i++) {
			final int length = QuestChains.rollLength(rng);
			assertTrue(length >= 2 && length <= 3);
		}
	}

	@Test
	void scaledRewardGrowsPerStep() {
		assertEquals(100, QuestChains.scaledReward(100, 1));
		assertEquals(150, QuestChains.scaledReward(100, 2));
		assertEquals(200, QuestChains.scaledReward(100, 3));
	}

	@Test
	void isFinaleOnlyOnTheLastStep() {
		assertFalse(QuestChains.isFinale(2, 3));
		assertTrue(QuestChains.isFinale(3, 3));
		assertTrue(QuestChains.isFinale(2, 2));
	}

	@Test
	void finaleRelicChanceTriplesTheQuestAwardChance() {
		assertEquals(0.21, QuestChains.finaleRelicChance(), 1e-9);
	}

	@Test
	void contextMentionsLastTitleAndContinuation() {
		final String context =
			QuestChains.context(new ChainState(2, 3, "The Lost Ledger"));

		assertTrue(context.contains("The Lost Ledger"));
		assertTrue(context.contains("continues that matter"));
		assertTrue(context.contains("not yet finished"));
	}

	@Test
	void contextFlagsFinaleWording() {
		final String context =
			QuestChains.context(new ChainState(3, 3, "The Lost Ledger"));

		assertTrue(context.contains("final favor"));
		assertTrue(context.contains("conclusion"));
		assertFalse(context.contains("not yet finished"));
	}
}
