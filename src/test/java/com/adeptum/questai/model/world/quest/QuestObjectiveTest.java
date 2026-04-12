package com.adeptum.questai.model.world.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestObjectiveTest {

	@Test
	void settersAndGettersWorkCorrectly() {
		final QuestObjective objective = new QuestObjective();
		objective.setType(QuestObjective.Type.KILL);
		objective.setTarget("ZOMBIE");
		objective.setAmount(5);

		assertEquals(QuestObjective.Type.KILL, objective.getType());
		assertEquals("ZOMBIE", objective.getTarget());
		assertEquals(5, objective.getAmount());
	}

	@Test
	void typeAllEnumValuesExist() {
		final QuestObjective.Type[] values = QuestObjective.Type.values();
		assertEquals(4, values.length);
		assertNotNull(QuestObjective.Type.valueOf("TREASURE"));
		assertNotNull(QuestObjective.Type.valueOf("FIND_NPC"));
		assertNotNull(QuestObjective.Type.valueOf("KILL"));
		assertNotNull(QuestObjective.Type.valueOf("COLLECT"));
	}
}
