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

import static com.adeptum.questai.model.world.quest.QuestObjective.Type.COLLECT;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.FIND_NPC;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.KILL;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.TREASURE;

import com.adeptum.questai.model.world.Npc;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages active quests, NPC data, boss bars, quest log book, and
 * scheduled progress tasks. Supports multiple concurrent quests per player.
 */
@SuppressWarnings("PMD.GodClass")
public class QuestManager {
	private final Map<UUID, List<QuestProgress>> playerQuests = new ConcurrentHashMap<>();
	private final Map<UUID, Npc> npcs = new ConcurrentHashMap<>();
	private final Map<UUID, UUID> villagerIndicators = new ConcurrentHashMap<>();
	private final Map<UUID, BukkitTask> questTasks = new ConcurrentHashMap<>();

	private final JavaPlugin plugin;
	private final Logger logger;
	private final QuestLogBook questLogBook;

	private BiConsumer<Player, Quest> questCleanup;

	public QuestManager(final JavaPlugin plugin) {
		this(plugin, new QuestLogBook());
	}

	QuestManager(final JavaPlugin plugin, final QuestLogBook questLogBook) {
		this.plugin = plugin;
		this.logger = plugin.getLogger();
		this.questLogBook = questLogBook;
	}

	public void setQuestCleanup(final BiConsumer<Player, Quest> questCleanup) {
		this.questCleanup = questCleanup;
	}

	public QuestLogBook getQuestLogBook() {
		return questLogBook;
	}

	// ------------------- Quest lifecycle -------------------

	/**
	 * Assigns a quest to a player, initialises BossBars, and ensures the
	 * player has a quest log book.
	 */
	public void assignQuest(final Player player, final Quest quest) {
		logger.info("[QuestManager] assignQuest() -> Assigning quest '"
			+ quest.getShortTitle() + "' to player: " + player.getName());

		final QuestProgress progress = new QuestProgress(quest);

		if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
			final Location playerLocation = player.getLocation();
			final Location destination = quest.getDestination();
			progress.setMaxDistance(playerLocation.distance(destination));
		}

		final BossBar objectiveBar = Bukkit.createBossBar(
			"Objective Progress", BarColor.BLUE, BarStyle.SOLID);
		progress.setObjectiveBossBar(objectiveBar);
		objectiveBar.addPlayer(player);

		final BossBar timerBar = Bukkit.createBossBar(
			"Time Remaining", BarColor.RED, BarStyle.SOLID);
		progress.setTimerBossBar(timerBar);
		timerBar.addPlayer(player);

		playerQuests.computeIfAbsent(player.getUniqueId(),
			k -> new CopyOnWriteArrayList<>()).add(progress);

		questLogBook.ensure(player);
		startTaskIfNeeded(player);
	}

	private void startTaskIfNeeded(final Player player) {
		final UUID playerId = player.getUniqueId();
		if (questTasks.containsKey(playerId)) {
			return;
		}

		final BukkitTask task = Bukkit.getScheduler()
			.runTaskTimerAsynchronously(plugin,
				() -> updateAllQuests(player), 20L, 20L);
		questTasks.put(playerId, task);
	}

	/**
	 * Returns all active quests for a player (unmodifiable view).
	 */
	public List<QuestProgress> getActiveQuests(final Player player) {
		final List<QuestProgress> quests = playerQuests.get(player.getUniqueId());
		return quests == null ? List.of() : Collections.unmodifiableList(quests);
	}

	public boolean hasActiveQuest(final Player player) {
		final List<QuestProgress> quests = playerQuests.get(player.getUniqueId());
		return quests != null && !quests.isEmpty();
	}

	/**
	 * Finds the first quest matching the given objective type and target.
	 */
	public QuestProgress findQuest(final Player player,
		final QuestObjective.Type type, final String target) {

		final List<QuestProgress> quests = playerQuests.get(player.getUniqueId());
		if (quests == null) {
			return null;
		}

		for (final QuestProgress p : quests) {
			final QuestObjective obj = p.getQuest().getObjective();
			if (obj.getType() == type && obj.getTarget().equalsIgnoreCase(target)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Abandons a specific quest by index. Cleans up boss bars and
	 * spawned quest entities. Removes the quest log book if no quests remain.
	 *
	 * @return the abandoned quest, or null if index is invalid
	 */
	public Quest abandonQuest(final Player player, final int questIndex) {
		final List<QuestProgress> quests = playerQuests.get(player.getUniqueId());
		if (quests == null || questIndex < 0 || questIndex >= quests.size()) {
			return null;
		}

		final QuestProgress progress = quests.remove(questIndex);
		final Quest quest = progress.getQuest();
		removeBossBars(player, progress);

		if (questCleanup != null) {
			questCleanup.accept(player, quest);
		}

		logger.info("[QuestManager] abandonQuest() -> Player "
			+ player.getName() + " abandoned quest: " + quest.getShortTitle());

		if (quests.isEmpty()) {
			playerQuests.remove(player.getUniqueId());
			cancelQuestTask(player);
			questLogBook.remove(player);
		}
		return quest;
	}

	/**
	 * Abandons all active quests for a player. Called when the quest log
	 * book is dropped or destroyed.
	 *
	 * @return list of abandoned quests
	 */
	public List<Quest> abandonAllQuests(final Player player) {
		final List<QuestProgress> quests = playerQuests.remove(player.getUniqueId());
		if (quests == null || quests.isEmpty()) {
			return List.of();
		}

		final List<Quest> abandoned = new ArrayList<>();
		for (final QuestProgress progress : quests) {
			removeBossBars(player, progress);
			final Quest quest = progress.getQuest();
			if (questCleanup != null) {
				questCleanup.accept(player, quest);
			}
			abandoned.add(quest);
		}
		cancelQuestTask(player);

		logger.info("[QuestManager] abandonAllQuests() -> Player "
			+ player.getName() + " abandoned " + abandoned.size() + " quest(s).");
		return abandoned;
	}

	/**
	 * Completes a specific quest, removing it from the player's active list.
	 * Removes the quest log book if no quests remain.
	 */
	public boolean completeQuest(final Player player, final Quest quest) {
		final List<QuestProgress> quests = playerQuests.get(player.getUniqueId());
		if (quests == null) {
			return false;
		}

		final boolean removed = quests.removeIf(
			p -> p.getQuest() == quest);
		if (!removed) {
			return false;
		}

		logger.info("[QuestManager] completeQuest() -> Player "
			+ player.getName() + " completed quest: " + quest.getShortTitle());

		if (quests.isEmpty()) {
			playerQuests.remove(player.getUniqueId());
			cancelQuestTask(player);
			questLogBook.remove(player);
		}
		return true;
	}

	/**
	 * Increments quest progress for the first matching KILL or COLLECT quest.
	 *
	 * @return the QuestProgress if the quest is now complete, null otherwise
	 */
	public QuestProgress incrementProgress(final Player player,
		final QuestObjective.Type type, final String target, final int amount) {

		final QuestProgress progress = findQuest(player, type, target);
		if (progress == null) {
			return null;
		}

		final QuestObjective objective = progress.getQuest().getObjective();
		progress.setCurrent(progress.getCurrent() + amount);
		logger.info("[QuestManager] Player " + player.getName()
			+ " progress updated: " + progress.getCurrent()
			+ "/" + objective.getAmount());

		return progress.getCurrent() >= objective.getAmount() ? progress : null;
	}

	// ------------------- Async progress updates -------------------

	private void updateAllQuests(final Player player) {
		final List<QuestProgress> quests = playerQuests.get(player.getUniqueId());
		if (quests == null || quests.isEmpty()) {
			cancelQuestTask(player);
			return;
		}

		final List<QuestProgress> expired = new ArrayList<>();

		for (final QuestProgress progress : quests) {
			final long elapsed = System.currentTimeMillis() - progress.getStartTime();
			final long sixHoursMillis = 6L * 60 * 60 * 1000;
			final long remaining = sixHoursMillis - elapsed;

			if (remaining <= 0) {
				expired.add(progress);
				continue;
			}

			updateTimerBar(player, progress, remaining, sixHoursMillis);

			final Quest quest = progress.getQuest();
			if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
				updateDistanceProgress(player, progress, quest);
			} else if (EnumSet.of(KILL, COLLECT).contains(quest.getObjective().getType())) {
				updateCountProgress(player, progress);
			}
		}

		if (!expired.isEmpty()) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				for (final QuestProgress progress : expired) {
					final Quest quest = progress.getQuest();
					player.sendMessage("§cYour quest '"
						+ quest.getShortTitle() + "' has expired.");
					removeBossBars(player, progress);
					if (questCleanup != null) {
						questCleanup.accept(player, quest);
					}
					quests.remove(progress);
				}
				if (quests.isEmpty()) {
					playerQuests.remove(player.getUniqueId());
					cancelQuestTask(player);
					questLogBook.remove(player);
				}
			});
		}
	}

	private void updateTimerBar(final Player player, final QuestProgress progress,
		final long remaining, final long sixHoursMillis) {

		final double timerProgress = Math.max(remaining / (double) sixHoursMillis, 0);
		Bukkit.getScheduler().runTask(plugin, () -> {
			final BossBar bar = progress.getTimerBossBar();
			if (bar != null) {
				bar.setProgress(timerProgress);
				bar.setTitle("Time Remaining: " + formatTime(remaining));
			}
		});
	}

	private void updateDistanceProgress(final Player player,
		final QuestProgress progress, final Quest quest) {

		final Location dest = quest.getDestination();
		final Location playerLoc = player.getLocation();
		final double distance = playerLoc.distance(dest);
		final double progressPercent = Math.min(
			Math.max(1.0 - (distance / progress.getMaxDistance()), 0.0), 1.0);

		Bukkit.getScheduler().runTask(plugin, () -> {
			final BossBar bar = progress.getObjectiveBossBar();
			if (bar != null) {
				bar.setProgress(progressPercent);
				bar.setTitle("Distance to Destination: "
					+ String.format("%.2f", distance) + " blocks");
			}
		});
	}

	private void updateCountProgress(final Player player,
		final QuestProgress progress) {

		final int current = progress.getCurrent();
		final int required = progress.getQuest().getObjective().getAmount();
		final double progressPercent = Math.min(
			Math.max((double) current / required, 0.0), 1.0);

		Bukkit.getScheduler().runTask(plugin, () -> {
			final BossBar bar = progress.getObjectiveBossBar();
			if (bar != null) {
				bar.setProgress(progressPercent);
				bar.setTitle("Objective Progress: " + current + "/" + required);
			}
		});
	}

	private String formatTime(final long millis) {
		final long seconds = millis / 1000;
		final long hours = seconds / 3600;
		final long minutes = seconds % 3600 / 60;
		final long secs = seconds % 60;
		return String.format("%02dh %02dm %02ds", hours, minutes, secs);
	}

	public void removeBossBars(final Player player, final QuestProgress progress) {
		if (progress.getObjectiveBossBar() != null) {
			progress.getObjectiveBossBar().removePlayer(player);
		}
		if (progress.getTimerBossBar() != null) {
			progress.getTimerBossBar().removePlayer(player);
		}
	}

	private void cancelQuestTask(final Player player) {
		final UUID playerId = player.getUniqueId();
		final BukkitTask task = questTasks.remove(playerId);
		if (task != null) {
			task.cancel();
		}
	}

	// ------------------- Villager NPC Management -------------------

	public Npc getVillagerData(final UUID villagerId) {
		return npcs.get(villagerId);
	}

	public void setVillagerData(final UUID villagerId, final Npc npc) {
		if (npc == null) {
			npcs.remove(villagerId);
			return;
		}
		npcs.put(villagerId, npc);
	}

	public UUID getIndicator(final UUID villagerId) {
		return villagerIndicators.get(villagerId);
	}

	public void setIndicator(final UUID villagerId, final UUID armorStandId) {
		villagerIndicators.put(villagerId, armorStandId);
	}

	public void removeIndicator(final UUID villagerId) {
		villagerIndicators.remove(villagerId);
	}

	// ------------------- Cleanup -------------------

	/**
	 * Cleans up all active quests, BossBars, and scheduled tasks for all
	 * players. Called on plugin disable.
	 */
	public void cleanupAllQuests() {
		for (final var entry : new HashMap<>(playerQuests).entrySet()) {
			final Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null) {
				for (final QuestProgress progress : entry.getValue()) {
					removeBossBars(player, progress);
				}
				cancelQuestTask(player);
				questLogBook.remove(player);
			}
		}
		playerQuests.clear();
		questTasks.clear();
		npcs.clear();

		for (final UUID standId : villagerIndicators.values()) {
			final var entity = Bukkit.getEntity(standId);
			if (entity != null) {
				entity.remove();
			}
		}
		villagerIndicators.clear();
	}
}
