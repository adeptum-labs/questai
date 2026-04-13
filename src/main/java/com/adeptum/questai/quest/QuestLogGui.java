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
import com.adeptum.questai.model.world.quest.QuestObjective;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Factory for the quest log chest inventory GUI.
 * <p>
 * Layout (27-slot chest):
 * <pre>
 *   Row 0: [F][F][F][F][F][F][F][F][F]         filler
 *   Row 1: [F][Q1][Q2][Q3][Q4][Q5][Q6][Q7][F]  quest items (slots 10-16)
 *   Row 2: [F][A1][A2][A3][A4][A5][A6][A7][F]  abandon buttons (slots 19-25)
 * </pre>
 */
public final class QuestLogGui {

	public static final String TITLE = "\u00a76Quest Log";
	private static final int ROWS = 27;

	/** First quest info slot (row 1, column 1). */
	public static final int QUEST_START_SLOT = 10;
	/** First abandon button slot (row 2, column 1). */
	public static final int ABANDON_START_SLOT = 19;
	/** Maximum number of quests displayed at once. */
	public static final int MAX_DISPLAY = 7;

	private static final int[] BORDER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26};

	private QuestLogGui() {
	}

	/**
	 * Converts a raw inventory slot to a quest index, or -1 if the slot
	 * is not an abandon button.
	 */
	public static int abandonSlotToIndex(final int slot) {
		final int index = slot - ABANDON_START_SLOT;
		return index >= 0 && index < MAX_DISPLAY ? index : -1;
	}

	public static Inventory create(final List<QuestProgress> quests) {
		if (quests == null || quests.isEmpty()) {
			return createEmpty();
		}

		final Inventory inv = Bukkit.createInventory(null, ROWS, TITLE);
		fillBorder(inv);

		final int count = Math.min(quests.size(), MAX_DISPLAY);
		for (int i = 0; i < count; i++) {
			final QuestProgress progress = quests.get(i);
			inv.setItem(QUEST_START_SLOT + i, questItem(progress));
			inv.setItem(ABANDON_START_SLOT + i, abandonButton());
		}
		return inv;
	}
	private static ItemStack questItem(final QuestProgress progress) {
		final Quest quest = progress.getQuest();
		final QuestObjective obj = quest.getObjective();

		final ItemStack item = new ItemStack(materialForType(obj.getType()));
		final ItemMeta meta = item.getItemMeta();
		meta.displayName(Component.text("\u00a76" + quest.getShortTitle()));

		final String objectiveLine = switch (obj.getType()) {
			case KILL -> "Kill " + obj.getTarget() + " x" + obj.getAmount();
			case COLLECT -> "Collect " + obj.getTarget() + " x" + obj.getAmount();
			case TREASURE -> "Find a hidden treasure chest";
			case FIND_NPC -> "Locate a missing person";
		};

		final String progressLine = switch (obj.getType()) {
			case KILL, COLLECT -> progress.getCurrent() + "/" + obj.getAmount();
			case TREASURE, FIND_NPC -> "Follow your quest map";
		};

		meta.setLore(List.of(
			"\u00a77Objective: \u00a7f" + objectiveLine,
			"\u00a77Progress: \u00a7f" + progressLine,
			"\u00a77Reward: \u00a7a" + quest.getRewardAmount()
				+ " MCMMO XP in " + quest.getRewardTarget(),
			"",
			"\u00a77" + formatTimeRemaining(progress)
		));
		item.setItemMeta(meta);
		return item;
	}

	private static Material materialForType(final QuestObjective.Type type) {
		return switch (type) {
			case KILL -> Material.IRON_SWORD;
			case COLLECT -> Material.CHEST;
			case TREASURE -> Material.FILLED_MAP;
			case FIND_NPC -> Material.COMPASS;
		};
	}

	private static ItemStack abandonButton() {
		final ItemStack item = new ItemStack(Material.BARRIER);
		final ItemMeta meta = item.getItemMeta();
		meta.displayName(Component.text("\u00a7c\u00a7lAbandon Quest"));
		meta.setLore(List.of("\u00a77Click to abandon this quest"));
		item.setItemMeta(meta);
		return item;
	}
	private static Inventory createEmpty() {
		final Inventory inv = Bukkit.createInventory(null, ROWS, TITLE);
		fillBorder(inv);

		final ItemStack noQuest = new ItemStack(Material.PAPER);
		final ItemMeta meta = noQuest.getItemMeta();
		meta.displayName(Component.text("\u00a77No active quests"));
		meta.setLore(List.of("\u00a77Talk to villagers to discover quests!"));
		noQuest.setItemMeta(meta);
		inv.setItem(13, noQuest);

		return inv;
	}
	private static void fillBorder(final Inventory inv) {
		final ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
		final ItemMeta meta = filler.getItemMeta();
		meta.displayName(Component.text(" "));
		filler.setItemMeta(meta);
		for (final int slot : BORDER_SLOTS) {
			inv.setItem(slot, filler.clone());
		}
	}

	private static String formatTimeRemaining(final QuestProgress progress) {
		final long elapsed = System.currentTimeMillis() - progress.getStartTime();
		final long sixHours = 6L * 60 * 60 * 1000;
		final long remaining = Math.max(sixHours - elapsed, 0);

		final long seconds = remaining / 1000;
		final long hours = seconds / 3600;
		final long minutes = seconds % 3600 / 60;
		return "Time remaining: %02dh %02dm".formatted(hours, minutes);
	}
}
