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

import com.adeptum.questai.model.world.quest.Quest;
import org.junit.jupiter.api.Test;
import org.bukkit.boss.BossBar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class QuestProgressTest {

	@Test
	void constructorSetsQuestAndDefaults() {
		final Quest quest = new Quest();
		final long before = System.currentTimeMillis();
		final QuestProgress progress = new QuestProgress(quest);
		final long after = System.currentTimeMillis();

		assertSame(quest, progress.getQuest());
		assertEquals(0, progress.getCurrent());
		assertTrue(progress.getStartTime() >= before);
		assertTrue(progress.getStartTime() <= after);
		assertNull(progress.getObjectiveBossBar());
		assertNull(progress.getTimerBossBar());
		assertEquals(0.0, progress.getMaxDistance());
	}

	@Test
	void setCurrentUpdatesCurrent() {
		final QuestProgress progress = new QuestProgress(new Quest());
		progress.setCurrent(5);
		assertEquals(5, progress.getCurrent());
	}

	@Test
	void bossBarAssignmentWorksCorrectly() {
		final QuestProgress progress = new QuestProgress(new Quest());
		final BossBar objectiveBar = mock(BossBar.class);
		final BossBar timerBar = mock(BossBar.class);

		progress.setObjectiveBossBar(objectiveBar);
		progress.setTimerBossBar(timerBar);

		assertSame(objectiveBar, progress.getObjectiveBossBar());
		assertSame(timerBar, progress.getTimerBossBar());
	}

	@Test
	void setMaxDistanceUpdatesMaxDistance() {
		final QuestProgress progress = new QuestProgress(new Quest());
		progress.setMaxDistance(1500.0);
		assertEquals(1500.0, progress.getMaxDistance());
	}
}
