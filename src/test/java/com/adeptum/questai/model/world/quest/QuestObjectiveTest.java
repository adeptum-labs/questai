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
