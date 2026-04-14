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
import java.util.List;
import java.util.UUID;
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

	@Mock private JavaPlugin plugin;
	@Mock private Player player;
	@Mock private BossBar bossBar1;
	@Mock private BossBar bossBar2;
	@Mock private BossBar bossBar3;
	@Mock private BossBar bossBar4;
	@Mock private BukkitScheduler scheduler;
	@Mock private BukkitTask task;
	@Mock private QuestLogBook questLogBook;

	private MockedStatic<Bukkit> bukkitMock;
	private QuestManager questManager;
	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		bukkitMock = mockStatic(Bukkit.class);

		final Logger logger = Logger.getLogger("test");
		when(plugin.getLogger()).thenReturn(logger);
		when(plugin.getName()).thenReturn("questai");

		// Return alternating boss bars for multiple quests
		bukkitMock.when(() -> Bukkit.createBossBar(
			anyString(), any(BarColor.class), any(BarStyle.class)
		)).thenReturn(bossBar1, bossBar2, bossBar3, bossBar4);

		bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
		when(scheduler.runTaskTimerAsynchronously(
			any(), any(Runnable.class), anyLong(), anyLong()
		)).thenReturn(task);

		final UUID playerId = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("TestPlayer");

		questManager = new QuestManager(plugin, questLogBook);
	}

	@AfterEach
	void tearDown() throws Exception {
		bukkitMock.close();
		mocks.close();
	}
	@Test
	void assignQuestCreatesProgressWithBossBars() {
		final Quest quest = createKillQuest(5, "ZOMBIE");

		questManager.assignQuest(player, quest);

		final List<QuestProgress> quests = questManager.getActiveQuests(player);
		assertEquals(1, quests.size());
		assertSame(quest, quests.get(0).getQuest());
		assertEquals(0, quests.get(0).getCurrent());
		verify(bossBar1).addPlayer(player);
		verify(bossBar2).addPlayer(player);
	}

	@Test
	void assignMultipleQuestsTracksAll() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));
		questManager.assignQuest(player, createKillQuest(3, "SKELETON"));

		final List<QuestProgress> quests = questManager.getActiveQuests(player);
		assertEquals(2, quests.size());
		assertEquals("ZOMBIE", quests.get(0).getQuest().getObjective().getTarget());
		assertEquals("SKELETON", quests.get(1).getQuest().getObjective().getTarget());
	}

	@Test
	void assignQuestFindNpcSetsMaxDistance() {
		final Quest quest = createDestinationQuest(QuestObjective.Type.FIND_NPC);
		final Location playerLoc = mock(Location.class);
		final Location destination = mock(Location.class);
		when(player.getLocation()).thenReturn(playerLoc);
		when(playerLoc.distance(destination)).thenReturn(500.0);
		quest.setDestination(destination);

		questManager.assignQuest(player, quest);

		assertEquals(500.0, questManager.getActiveQuests(player).get(0).getMaxDistance());
	}
	@Test
	void findQuestReturnsMatchingProgress() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));
		questManager.assignQuest(player, createKillQuest(3, "SKELETON"));

		final QuestProgress found = questManager.findQuest(
			player, QuestObjective.Type.KILL, "SKELETON");
		assertNotNull(found);
		assertEquals("SKELETON", found.getQuest().getObjective().getTarget());
	}

	@Test
	void findQuestReturnsNullWhenNoMatch() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));

		assertNull(questManager.findQuest(
			player, QuestObjective.Type.KILL, "CREEPER"));
	}
	@Test
	void incrementProgressReturnsNullWhenNotComplete() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));

		final QuestProgress result = questManager.incrementProgress(
			player, QuestObjective.Type.KILL, "ZOMBIE", 1);

		assertNull(result);
		assertEquals(1, questManager.getActiveQuests(player).get(0).getCurrent());
	}

	@Test
	void incrementProgressReturnsProgressWhenComplete() {
		questManager.assignQuest(player, createKillQuest(3, "ZOMBIE"));

		questManager.incrementProgress(
			player, QuestObjective.Type.KILL, "ZOMBIE", 2);
		final QuestProgress result = questManager.incrementProgress(
			player, QuestObjective.Type.KILL, "ZOMBIE", 1);

		assertNotNull(result);
		assertEquals(3, result.getCurrent());
	}

	@Test
	void incrementProgressReturnsNullWhenNoMatchingQuest() {
		assertNull(questManager.incrementProgress(
			player, QuestObjective.Type.KILL, "ZOMBIE", 1));
	}

	@Test
	void incrementProgressOnlyAffectsMatchingQuest() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));
		questManager.assignQuest(player, createKillQuest(3, "SKELETON"));

		questManager.incrementProgress(
			player, QuestObjective.Type.KILL, "SKELETON", 2);

		final List<QuestProgress> quests = questManager.getActiveQuests(player);
		assertEquals(0, quests.get(0).getCurrent()); // ZOMBIE unchanged
		assertEquals(2, quests.get(1).getCurrent()); // SKELETON incremented
	}
	@Test
	void completeQuestRemovesSpecificQuest() {
		final Quest zombie = createKillQuest(5, "ZOMBIE");
		final Quest skeleton = createKillQuest(3, "SKELETON");
		questManager.assignQuest(player, zombie);
		questManager.assignQuest(player, skeleton);

		assertTrue(questManager.completeQuest(player, zombie));

		final List<QuestProgress> remaining = questManager.getActiveQuests(player);
		assertEquals(1, remaining.size());
		assertSame(skeleton, remaining.get(0).getQuest());
	}

	@Test
	void completeLastQuestRemovesPlayerEntry() {
		final Quest quest = createKillQuest(5, "ZOMBIE");
		questManager.assignQuest(player, quest);

		assertTrue(questManager.completeQuest(player, quest));
		assertFalse(questManager.hasActiveQuest(player));
		assertTrue(questManager.getActiveQuests(player).isEmpty());
	}

	@Test
	void completeQuestReturnsFalseWhenNoActiveQuest() {
		final Quest quest = createKillQuest(5, "ZOMBIE");
		assertFalse(questManager.completeQuest(player, quest));
	}
	@Test
	void abandonQuestByIndexRemovesCorrectQuest() {
		final Quest zombie = createKillQuest(5, "ZOMBIE");
		final Quest skeleton = createKillQuest(3, "SKELETON");
		questManager.assignQuest(player, zombie);
		questManager.assignQuest(player, skeleton);

		final Quest abandoned = questManager.abandonQuest(player, 0);
		assertSame(zombie, abandoned);

		final List<QuestProgress> remaining = questManager.getActiveQuests(player);
		assertEquals(1, remaining.size());
		assertSame(skeleton, remaining.get(0).getQuest());
	}

	@Test
	void abandonLastQuestCleansUpCompletely() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));

		final Quest abandoned = questManager.abandonQuest(player, 0);
		assertNotNull(abandoned);
		assertFalse(questManager.hasActiveQuest(player));
		verify(bossBar1).removePlayer(player);
		verify(bossBar2).removePlayer(player);
	}

	@Test
	void abandonQuestCallsCleanupCallback() {
		@SuppressWarnings("unchecked")
		final java.util.function.BiConsumer<Player, Quest> cleanup = mock(
			java.util.function.BiConsumer.class);
		questManager.setQuestCleanup(cleanup);

		final Quest quest = createKillQuest(5, "ZOMBIE");
		questManager.assignQuest(player, quest);
		questManager.abandonQuest(player, 0);

		verify(cleanup).accept(player, quest);
	}

	@Test
	void abandonQuestReturnsNullForInvalidIndex() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));

		assertNull(questManager.abandonQuest(player, -1));
		assertNull(questManager.abandonQuest(player, 5));
	}
	@Test
	void abandonAllQuestsReturnsAllQuests() {
		final Quest zombie = createKillQuest(5, "ZOMBIE");
		final Quest skeleton = createKillQuest(3, "SKELETON");
		questManager.assignQuest(player, zombie);
		questManager.assignQuest(player, skeleton);

		final List<Quest> abandoned = questManager.abandonAllQuests(player);

		assertEquals(2, abandoned.size());
		assertFalse(questManager.hasActiveQuest(player));
	}

	@Test
	void abandonAllQuestsReturnsEmptyWhenNoQuests() {
		assertTrue(questManager.abandonAllQuests(player).isEmpty());
	}
	@Test
	void hasActiveQuestLifecycle() {
		assertFalse(questManager.hasActiveQuest(player));

		final Quest quest = createKillQuest(5, "ZOMBIE");
		questManager.assignQuest(player, quest);
		assertTrue(questManager.hasActiveQuest(player));

		questManager.completeQuest(player, quest);
		assertFalse(questManager.hasActiveQuest(player));
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
	void indicatorSetAndRemove() {
		final UUID villagerId = UUID.randomUUID();
		final UUID armorStandId = UUID.randomUUID();

		questManager.setIndicator(villagerId, armorStandId);
		assertEquals(armorStandId, questManager.getIndicator(villagerId));

		questManager.removeIndicator(villagerId);
		assertNull(questManager.getIndicator(villagerId));
	}
	@Test
	void cleanupAllQuestsClearsAllState() {
		questManager.assignQuest(player, createKillQuest(5, "ZOMBIE"));

		final UUID villagerId = UUID.randomUUID();
		final UUID armorStandId = UUID.randomUUID();
		questManager.setIndicator(villagerId, armorStandId);

		bukkitMock.when(() -> Bukkit.getPlayer(player.getUniqueId()))
			.thenReturn(player);
		bukkitMock.when(() -> Bukkit.getEntity(armorStandId))
			.thenReturn(null);

		questManager.cleanupAllQuests();

		assertFalse(questManager.hasActiveQuest(player));
		assertNull(questManager.getIndicator(villagerId));
		verify(bossBar1).removePlayer(player);
		verify(bossBar2).removePlayer(player);
	}
	private Quest createKillQuest(final int amount, final String target) {
		final QuestObjective objective = new QuestObjective();
		objective.setType(QuestObjective.Type.KILL);
		objective.setTarget(target);
		objective.setAmount(amount);

		final Quest quest = new Quest();
		quest.setObjective(objective);
		quest.setRewardType("MCMMO");
		quest.setRewardTarget("MINING");
		quest.setRewardAmount(100);
		quest.setShortTitle("Kill " + target);
		return quest;
	}

	/**
	 * Simulates the zombie-quest scenario: progress exceeds the target
	 * but the quest was never completed (e.g. because rewardPlayer threw).
	 * Verifies that the quest is still in the active list and can be
	 * detected and completed on player join.
	 */
	@Test
	void overTargetQuestRemainsActiveUntilExplicitlyCompleted() {
		final Quest quest = createKillQuest(3, "ZOMBIE");
		questManager.assignQuest(player, quest);

		// Increment well past the target (simulates repeated pickup events)
		questManager.incrementProgress(player, QuestObjective.Type.KILL, "ZOMBIE", 10);

		// Quest is still tracked despite being over target
		assertTrue(questManager.hasActiveQuest(player));
		final List<QuestProgress> active = questManager.getActiveQuests(player);
		assertEquals(1, active.size());

		final QuestProgress progress = active.get(0);
		assertTrue(progress.getCurrent() >= quest.getObjective().getAmount(),
			"Progress should be at or above the target");

		// Explicit completion removes it (this is what the join handler does)
		assertTrue(questManager.completeQuest(player, quest));
		assertFalse(questManager.hasActiveQuest(player));
	}

	@Test
	void overTargetQuestCanBeFilteredFromActiveList() {
		questManager.assignQuest(player, createKillQuest(3, "ZOMBIE"));
		questManager.assignQuest(player, createKillQuest(5, "SKELETON"));

		// Only the zombie quest exceeds its target
		questManager.incrementProgress(player, QuestObjective.Type.KILL, "ZOMBIE", 5);

		final List<QuestProgress> completed = questManager.getActiveQuests(player)
			.stream()
			.filter(p -> {
				final QuestObjective obj = p.getQuest().getObjective();
				return p.getCurrent() >= obj.getAmount();
			})
			.toList();

		assertEquals(1, completed.size());
		assertEquals("ZOMBIE", completed.get(0).getQuest().getObjective().getTarget());
	}

	private Quest createDestinationQuest(final QuestObjective.Type type) {
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
