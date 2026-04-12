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
