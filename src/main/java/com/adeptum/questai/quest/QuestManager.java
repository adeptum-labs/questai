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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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
 * Manages active quests, NPC data, boss bars, and scheduled progress tasks.
 */
public class QuestManager {
	private final Map<UUID, QuestProgress> playerQuests = new ConcurrentHashMap<>();
	private final Map<UUID, Npc> npcs = new ConcurrentHashMap<>();
	private final Map<UUID, UUID> villagerIndicators = new ConcurrentHashMap<>();
	private final Map<UUID, BukkitTask> questTasks = new ConcurrentHashMap<>();

	private final JavaPlugin plugin;
	private final Logger logger;
	private final Consumer<Player> bookRemover;

	public QuestManager(JavaPlugin plugin, Consumer<Player> bookRemover) {
		this.plugin = plugin;
		this.logger = plugin.getLogger();
		this.bookRemover = bookRemover;
	}

	/**
	 * Assigns a quest to a player and initialises BossBars.
	 */
	public void assignQuest(Player player, Quest quest) {
		logger.info("[QuestManager] assignQuest() -> Assigning quest to player: "
			+ player.getName());
		final QuestProgress progress = new QuestProgress(quest);

		if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
			final Location playerLocation = player.getLocation();
			final Location destination = quest.getDestination();
			progress.setMaxDistance(playerLocation.distance(destination));
		}

		final BossBar objectiveBar = Bukkit.createBossBar(
			"Objective Progress", BarColor.BLUE, BarStyle.SOLID
		);
		progress.setObjectiveBossBar(objectiveBar);
		objectiveBar.addPlayer(player);

		final BossBar timerBar = Bukkit.createBossBar(
			"Time Remaining", BarColor.RED, BarStyle.SOLID
		);
		progress.setTimerBossBar(timerBar);
		timerBar.addPlayer(player);

		playerQuests.put(player.getUniqueId(), progress);

		final BukkitTask task = Bukkit.getScheduler()
			.runTaskTimerAsynchronously(plugin, () -> {
				updateQuestProgress(player, progress);
			}, 20L, 20L);

		questTasks.put(player.getUniqueId(), task);
	}

	private void updateQuestProgress(Player player, QuestProgress progress) {
		final Quest quest = progress.getQuest();
		final long elapsedTime = System.currentTimeMillis() - progress.getStartTime();
		final long sixHoursMillis = 6L * 60 * 60 * 1000;
		final long remainingTime = sixHoursMillis - elapsedTime;

		if (remainingTime <= 0) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				player.sendMessage("§cYour quest '"
					+ quest.getShortTitle() + "' has expired.");
				bookRemover.accept(player);
				removeBossBars(player, progress);
				completeQuest(player);
			});
			cancelQuestTask(player);
			return;
		}

		final double timerProgress = Math.max(
			remainingTime / (double) sixHoursMillis, 0
		);
		Bukkit.getScheduler().runTask(plugin, () -> {
			final BossBar bar = progress.getTimerBossBar();
			if (bar != null) {
				bar.setProgress(timerProgress);
				bar.setTitle("Time Remaining: " + formatTime(remainingTime));
			}
		});

		if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
			updateDistanceProgress(player, progress, quest);
		} else if (EnumSet.of(KILL, COLLECT).contains(quest.getObjective().getType())) {
			updateCountProgress(player, progress, quest);
		}
	}

	private void updateDistanceProgress(Player player,
		QuestProgress progress, Quest quest) {

		final Location dest = quest.getDestination();
		final Location playerLoc = player.getLocation();
		final double distance = playerLoc.distance(dest);
		final double progressPercent = Math.min(
			Math.max(1.0 - (distance / progress.getMaxDistance()), 0.0), 1.0
		);
		Bukkit.getScheduler().runTask(plugin, () -> {
			final BossBar bar = progress.getObjectiveBossBar();
			if (bar != null) {
				bar.setProgress(progressPercent);
				bar.setTitle("Distance to Destination: "
					+ String.format("%.2f", distance) + " blocks");
			}
		});

		if (distance <= 10) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				player.sendMessage("§aYou have reached the destination for quest '"
					+ quest.getShortTitle() + "'.");
				bookRemover.accept(player);
				removeBossBars(player, progress);
				completeQuest(player);
			});
			cancelQuestTask(player);
		}
	}

	private void updateCountProgress(Player player,
		QuestProgress progress, Quest quest) {

		final int current = progress.getCurrent();
		final int required = quest.getObjective().getAmount();
		final double progressPercent = Math.min(
			Math.max((double) current / required, 0.0), 1.0
		);
		Bukkit.getScheduler().runTask(plugin, () -> {
			final BossBar bar = progress.getObjectiveBossBar();
			if (bar != null) {
				bar.setProgress(progressPercent);
				bar.setTitle("Objective Progress: " + current + "/" + required);
			}
		});

		if (current >= required) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				player.sendMessage("§aYou have completed the objective for quest '"
					+ quest.getShortTitle() + "'.");
				bookRemover.accept(player);
				removeBossBars(player, progress);
				completeQuest(player);
			});
			cancelQuestTask(player);
		}
	}

	private String formatTime(long millis) {
		final long seconds = millis / 1000;
		final long hours = seconds / 3600;
		final long minutes = seconds % 3600 / 60;
		final long secs = seconds % 60;
		return String.format("%02dh %02dm %02ds", hours, minutes, secs);
	}

	public void removeBossBars(Player player, QuestProgress progress) {
		if (progress.getObjectiveBossBar() != null) {
			progress.getObjectiveBossBar().removePlayer(player);
		}
		if (progress.getTimerBossBar() != null) {
			progress.getTimerBossBar().removePlayer(player);
		}
	}

	public void cancelQuestTask(Player player) {
		final UUID playerId = player.getUniqueId();
		final BukkitTask task = questTasks.get(playerId);
		if (task != null) {
			task.cancel();
			questTasks.remove(playerId);
		}
	}

	public boolean completeQuest(Player player) {
		final QuestProgress removed = playerQuests.remove(player.getUniqueId());
		if (removed == null) {
			return false;
		}
		logger.info("[QuestManager] completeQuest() -> Removing quest for player: "
			+ player.getName());
		cancelQuestTask(player);
		return true;
	}

	public QuestProgress getQuestProgress(Player player) {
		return playerQuests.get(player.getUniqueId());
	}

	public boolean hasActiveQuest(Player player) {
		return playerQuests.containsKey(player.getUniqueId());
	}

	/**
	 * Increments the quest progress for a player.
	 *
	 * @return true if the quest is completed after incrementing.
	 */
	public boolean incrementProgress(Player player, int amount) {
		final QuestProgress progress = playerQuests.get(player.getUniqueId());
		if (progress == null) {
			logger.warning("[QuestManager] Player " + player.getName()
				+ " has no active quest.");
			return false;
		}

		final Quest quest = progress.getQuest();
		final QuestObjective objective = quest.getObjective();

		if (objective.getType() == KILL || objective.getType() == COLLECT) {
			progress.setCurrent(progress.getCurrent() + amount);
			logger.info("[QuestManager] Player " + player.getName()
				+ " progress updated: " + progress.getCurrent()
				+ "/" + objective.getAmount());
			return progress.getCurrent() >= objective.getAmount();
		}

		return false;
	}

	// ------------------- Villager NPC Management -------------------

	public Npc getVillagerData(UUID villagerId) {
		return npcs.get(villagerId);
	}

	public void setVillagerData(UUID villagerId, Npc npc) {
		if (npc == null) {
			npcs.remove(villagerId);
			return;
		}
		npcs.put(villagerId, npc);
	}

	public UUID getIndicator(UUID villagerId) {
		return villagerIndicators.get(villagerId);
	}

	public void setIndicator(UUID villagerId, UUID armorStandId) {
		villagerIndicators.put(villagerId, armorStandId);
	}

	public void removeIndicator(UUID villagerId) {
		villagerIndicators.remove(villagerId);
	}

	// ------------------- Cleanup -------------------

	/**
	 * Cleans up all active quests, BossBars, and scheduled tasks.
	 */
	public void cleanupAllQuests() {
		for (Map.Entry<UUID, QuestProgress> entry : new HashMap<>(playerQuests).entrySet()) {
			final Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null) {
				bookRemover.accept(player);
				removeBossBars(player, entry.getValue());
				cancelQuestTask(player);
			}
		}
		playerQuests.clear();
		questTasks.clear();
		npcs.clear();

		for (UUID standId : villagerIndicators.values()) {
			final var entity = Bukkit.getEntity(standId);
			if (entity != null) {
				entity.remove();
			}
		}
		villagerIndicators.clear();
	}
}
