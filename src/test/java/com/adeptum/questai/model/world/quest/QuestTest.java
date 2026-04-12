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

package com.adeptum.questai.model.world.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestTest {

	@Test
	void promptKillQuestContainsObjectiveAndReward() {
		final Quest quest = createQuest(QuestObjective.Type.KILL, "ZOMBIE", 10);
		final String prompt = quest.prompt();

		assertTrue(prompt.contains("Kill"));
		assertTrue(prompt.contains("ZOMBIE"));
		assertTrue(prompt.contains("10"));
		assertTrue(prompt.contains("MINING"));
		assertTrue(prompt.contains("100"));
	}

	@Test
	void promptCollectQuestContainsCollectText() {
		final Quest quest = createQuest(QuestObjective.Type.COLLECT, "WHEAT", 20);
		final String prompt = quest.prompt();

		assertTrue(prompt.contains("Collect"));
		assertTrue(prompt.contains("WHEAT"));
		assertTrue(prompt.contains("20"));
	}

	@Test
	void promptTreasureQuestContainsTreasureText() {
		final Quest quest = createQuest(QuestObjective.Type.TREASURE, "NONE", 1);
		final String prompt = quest.prompt();

		assertTrue(prompt.contains("hidden chest"));
	}

	@Test
	void promptFindNpcQuestContainsNpcText() {
		final Quest quest = createQuest(QuestObjective.Type.FIND_NPC, "NONE", 1);
		final String prompt = quest.prompt();

		assertTrue(prompt.contains("Locate an NPC"));
	}

	private Quest createQuest(QuestObjective.Type type, String target, int amount) {
		final QuestObjective objective = new QuestObjective();
		objective.setType(type);
		objective.setTarget(target);
		objective.setAmount(amount);

		final Quest quest = new Quest();
		quest.setObjective(objective);
		quest.setRewardType("MCMMO");
		quest.setRewardTarget("MINING");
		quest.setRewardAmount(100);
		return quest;
	}
}
