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

import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all player interactions with the quest log book and the
 * quest log GUI: opening, dropping, abandoning quests.
 */
public class QuestLogListener implements Listener {

	private final QuestManager questManager;

	public QuestLogListener(final QuestManager questManager) {
		this.questManager = questManager;
	}

	// ------------------- Right-click book → open GUI -------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(final PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR
			&& event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		final ItemStack item = event.getItem();
		if (!questManager.getQuestLogBook().isQuestLogBook(item)) {
			return;
		}

		event.setCancelled(true);

		final Player player = event.getPlayer();
		final List<QuestProgress> quests = questManager.getActiveQuests(player);
		player.openInventory(QuestLogGui.create(quests));
	}

	// ------------------- Drop book → evaporate + cancel all -------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onDropQuestLogBook(final PlayerDropItemEvent event) {
		if (!questManager.getQuestLogBook().isQuestLogBook(event.getItemDrop().getItemStack())) {
			return;
		}

		event.getItemDrop().remove();

		final Player player = event.getPlayer();
		final var abandoned = questManager.abandonAllQuests(player);
		if (!abandoned.isEmpty()) {
			player.sendMessage("§c§oThe quest log evaporated..."
				+ " All " + abandoned.size() + " active quest(s) have been cancelled.");
			player.playSound(player.getLocation(),
				Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
		}
	}

	// ------------------- Death → remove book from drops, cancel all -------------------

	@EventHandler
	public void onPlayerDeath(final PlayerDeathEvent event) {
		final Player player = event.getEntity();
		if (!questManager.hasActiveQuest(player)) {
			return;
		}

		event.getDrops().removeIf(questManager.getQuestLogBook()::isQuestLogBook);
		questManager.abandonAllQuests(player);
		// Message will be seen on respawn via server log;
		// avoid spamming the death screen.
	}

	// ------------------- Quest log GUI clicks -------------------

	@EventHandler
	public void onInventoryClick(final InventoryClickEvent event) {
		if (!event.getView().getTitle().equals(QuestLogGui.TITLE)) {
			return;
		}

		event.setCancelled(true);

		final ItemStack clicked = event.getCurrentItem();
		if (clicked == null || clicked.getType() != Material.BARRIER) {
			return;
		}

		final int questIndex = QuestLogGui.abandonSlotToIndex(event.getRawSlot());
		if (questIndex < 0) {
			return;
		}

		final Player player = (Player) event.getWhoClicked();
		final var quest = questManager.abandonQuest(player, questIndex);
		if (quest != null) {
			player.sendMessage("\u00a7cYou have abandoned the quest: "
				+ quest.getShortTitle());
			player.playSound(player.getLocation(),
				Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
		}

		// Refresh the GUI or close if no quests remain
		final List<QuestProgress> remaining = questManager.getActiveQuests(player);
		if (remaining.isEmpty()) {
			player.closeInventory();
		} else {
			player.openInventory(QuestLogGui.create(remaining));
		}
	}
}
