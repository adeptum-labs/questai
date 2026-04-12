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
