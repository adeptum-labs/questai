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

package com.adeptum.questai.model.world;

import com.adeptum.questai.model.world.quest.Quest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcTest {

	@Test
	void isQuestTrueWhenQuestSet() {
		final Npc npc = Npc.builder()
			.quest(new Quest())
			.timestamp(1000L)
			.build();
		assertTrue(npc.isQuest());
	}

	@Test
	void isQuestFalseWhenQuestNull() {
		final Npc npc = Npc.builder()
			.timestamp(1000L)
			.build();
		assertFalse(npc.isQuest());
	}

	@Test
	void isPhraseTrueWhenPhraseSet() {
		final Npc npc = Npc.builder()
			.nonsensePhrase("Hmm, carrots!")
			.timestamp(1000L)
			.build();
		assertTrue(npc.isPhrase());
	}

	@Test
	void isPhraseFalseWhenPhraseNull() {
		final Npc npc = Npc.builder()
			.timestamp(1000L)
			.build();
		assertFalse(npc.isPhrase());
	}

	@Test
	void builderSetsAllFields() {
		final Quest quest = new Quest();
		final Npc npc = Npc.builder()
			.quest(quest)
			.nonsensePhrase("hello")
			.timestamp(42L)
			.build();

		assertSame(quest, npc.getQuest());
		assertEquals("hello", npc.getNonsensePhrase());
		assertEquals(42L, npc.getTimestamp());
	}
}
