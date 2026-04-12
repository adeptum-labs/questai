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

import com.adeptum.questai.model.world.Npc;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("PMD.ExcessiveImports")
class QuestManagerTest {

	@Mock
	private JavaPlugin plugin;

	@Mock
	private Player player;

	@Mock
	private BossBar objectiveBar;

	@Mock
	private BossBar timerBar;

	@Mock
	private BukkitScheduler scheduler;

	@Mock
	private BukkitTask task;

	@Mock
	private Consumer<Player> bookRemover;

	private MockedStatic<Bukkit> bukkitMock;
	private QuestManager questManager;
	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		bukkitMock = mockStatic(Bukkit.class);

		final Logger logger = Logger.getLogger("test");
		when(plugin.getLogger()).thenReturn(logger);

		bukkitMock.when(() -> Bukkit.createBossBar(
			anyString(), any(BarColor.class), any(BarStyle.class)
		)).thenReturn(objectiveBar, timerBar);

		bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
		when(scheduler.runTaskTimerAsynchronously(
			any(), any(Runnable.class), anyLong(), anyLong()
		)).thenReturn(task);

		final UUID playerId = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("TestPlayer");

		questManager = new QuestManager(plugin, bookRemover);
	}

	@AfterEach
	void tearDown() throws Exception {
		bukkitMock.close();
		mocks.close();
	}

	@Test
	void assignQuestCreatesProgressWithBossBars() {
		final Quest quest = createKillQuest(5);

		questManager.assignQuest(player, quest);

		final QuestProgress progress = questManager.getQuestProgress(player);
		assertNotNull(progress);
		assertSame(quest, progress.getQuest());
		assertEquals(0, progress.getCurrent());
		assertNotNull(progress.getObjectiveBossBar());
		assertNotNull(progress.getTimerBossBar());

		verify(objectiveBar).addPlayer(player);
		verify(timerBar).addPlayer(player);
	}

	@Test
	void assignQuestFindNpcQuestSetsMaxDistance() {
		final Quest quest = createDestinationQuest(QuestObjective.Type.FIND_NPC);

		final Location playerLoc = mock(Location.class);
		final Location destination = mock(Location.class);

		when(player.getLocation()).thenReturn(playerLoc);
		when(playerLoc.distance(destination)).thenReturn(500.0);
		quest.setDestination(destination);

		questManager.assignQuest(player, quest);

		final QuestProgress progress = questManager.getQuestProgress(player);
		assertEquals(500.0, progress.getMaxDistance());
	}

	@Test
	void incrementProgressKillQuestIncrementsCorrectly() {
		final Quest quest = createKillQuest(5);
		questManager.assignQuest(player, quest);

		final boolean complete = questManager.incrementProgress(player, 1);

		assertFalse(complete);
		final QuestProgress progress = questManager.getQuestProgress(player);
		assertEquals(1, progress.getCurrent());
	}

	@Test
	void incrementProgressKillQuestReturnsTrueWhenComplete() {
		final Quest quest = createKillQuest(3);
		questManager.assignQuest(player, quest);

		questManager.incrementProgress(player, 2);
		final boolean complete = questManager.incrementProgress(player, 1);

		assertTrue(complete);
	}

	@Test
	void incrementProgressNoActiveQuestReturnsFalse() {
		final boolean result = questManager.incrementProgress(player, 1);
		assertFalse(result);
	}

	@Test
	void incrementProgressTreasureQuestReturnsFalse() {
		final Quest quest = createDestinationQuest(QuestObjective.Type.TREASURE);

		final Location playerLoc = mock(Location.class);
		final Location destination = mock(Location.class);
		when(player.getLocation()).thenReturn(playerLoc);
		when(playerLoc.distance(destination)).thenReturn(1000.0);
		quest.setDestination(destination);

		questManager.assignQuest(player, quest);

		final boolean result = questManager.incrementProgress(player, 1);
		assertFalse(result);
	}

	@Test
	void completeQuestRemovesProgressAndReturnsTrue() {
		final Quest quest = createKillQuest(5);
		questManager.assignQuest(player, quest);

		final boolean result = questManager.completeQuest(player);

		assertTrue(result);
		assertNull(questManager.getQuestProgress(player));
	}

	@Test
	void completeQuestReturnsFalseWhenNoActiveQuest() {
		final boolean result = questManager.completeQuest(player);
		assertFalse(result);
	}

	@Test
	void completeQuestReturnsFalseOnSecondCall() {
		final Quest quest = createKillQuest(5);
		questManager.assignQuest(player, quest);

		assertTrue(questManager.completeQuest(player));
		assertFalse(questManager.completeQuest(player));
	}

	@Test
	void getVillagerDataReturnsStoredNpc() {
		final UUID villagerId = UUID.randomUUID();
		final Npc npc = Npc.builder().timestamp(1000L).build();

		questManager.setVillagerData(villagerId, npc);

		assertSame(npc, questManager.getVillagerData(villagerId));
	}

	@Test
	void setVillagerDataNullRemovesEntry() {
		final UUID villagerId = UUID.randomUUID();
		final Npc npc = Npc.builder().timestamp(1000L).build();

		questManager.setVillagerData(villagerId, npc);
		questManager.setVillagerData(villagerId, null);

		assertNull(questManager.getVillagerData(villagerId));
	}

	@Test
	void cleanupAllQuestsClearsAllState() {
		final Quest quest = createKillQuest(5);
		questManager.assignQuest(player, quest);

		final UUID villagerId = UUID.randomUUID();
		final UUID armorStandId = UUID.randomUUID();
		questManager.setIndicator(villagerId, armorStandId);

		bukkitMock.when(() -> Bukkit.getPlayer(player.getUniqueId()))
			.thenReturn(player);
		bukkitMock.when(() -> Bukkit.getEntity(armorStandId))
			.thenReturn(null);

		questManager.cleanupAllQuests();

		assertNull(questManager.getQuestProgress(player));
		assertNull(questManager.getIndicator(villagerId));
		verify(objectiveBar).removePlayer(player);
		verify(timerBar).removePlayer(player);
		verify(bookRemover).accept(player);
	}

	@Test
	void indicatorSetAndRemove() {
		final UUID villagerId = UUID.randomUUID();
		final UUID armorStandId = UUID.randomUUID();

		questManager.setIndicator(villagerId, armorStandId);
		assertEquals(armorStandId, questManager.getIndicator(villagerId));

		questManager.removeIndicator(villagerId);
		assertNull(questManager.getIndicator(villagerId));
	}

	private Quest createKillQuest(int amount) {
		final QuestObjective objective = new QuestObjective();
		objective.setType(QuestObjective.Type.KILL);
		objective.setTarget("ZOMBIE");
		objective.setAmount(amount);

		final Quest quest = new Quest();
		quest.setObjective(objective);
		quest.setRewardType("MCMMO");
		quest.setRewardTarget("MINING");
		quest.setRewardAmount(100);
		quest.setShortTitle("Test Quest");
		return quest;
	}

	private Quest createDestinationQuest(QuestObjective.Type type) {
		final QuestObjective objective = new QuestObjective();
		objective.setType(type);
		objective.setTarget("NONE");
		objective.setAmount(1);

		final Quest quest = new Quest();
		quest.setObjective(objective);
		quest.setRewardType("MCMMO");
		quest.setRewardTarget("MINING");
		quest.setRewardAmount(100);
		quest.setShortTitle("Destination Quest");
		return quest;
	}
}
