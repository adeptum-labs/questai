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

package com.adeptum.questai.dialogue;

import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialoguePromptsTest {

	@Test
	void greetingContainsNpcNameAndProfession() {
		final String prompt = DialoguePrompts.greeting("Edric Stone", "FARMER");

		assertTrue(prompt.contains("Edric Stone"));
		assertTrue(prompt.contains("FARMER"));
		assertTrue(prompt.contains("greeting"));
	}

	@Test
	void questNarrativeContainsQuestDetails() {
		final Quest quest = new Quest();
		final QuestObjective obj = new QuestObjective();
		obj.setType(QuestObjective.Type.KILL);
		obj.setTarget("ZOMBIE");
		obj.setAmount(10);
		quest.setObjective(obj);
		quest.setRewardTarget("MINING");
		quest.setRewardAmount(150);

		final String prompt = DialoguePrompts.questNarrative(
			"Edric Stone", "FARMER", quest);

		assertTrue(prompt.contains("Edric Stone"));
		assertTrue(prompt.contains("ZOMBIE"));
		assertTrue(prompt.contains("10"));
	}

	@Test
	void casualChatContainsNpcName() {
		final String prompt = DialoguePrompts.casualChat("Mira Bloom", "LIBRARIAN");

		assertTrue(prompt.contains("Mira Bloom"));
		assertTrue(prompt.contains("LIBRARIAN"));
	}

	@Test
	void farewellContainsNpcName() {
		final String prompt = DialoguePrompts.farewell("Goran Dusk");

		assertTrue(prompt.contains("Goran Dusk"));
		assertTrue(prompt.contains("goodbye"));
	}
}
