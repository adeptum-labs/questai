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

package com.adeptum.questai.dialogue;

import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for 27-slot chest inventory GUIs used in the NPC conversation system.
 */
public final class DialogueGui {

	public static final String DIALOGUE_TITLE = "\u00a76NPC Dialogue";
	private static final int ROWS = 27;
	public static final int OPTION_1_SLOT = 20;
	public static final int OPTION_2_SLOT = 22;
	public static final int OPTION_3_SLOT = 24;

	private static final int[] FILLER_SLOTS = {
		1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 21, 23, 25, 26
	};

	private DialogueGui() {
	}
	private static Inventory createBase(final String npcName, final String profession,
		final String dialogueText) {

		final Inventory inv = Bukkit.createInventory(null, ROWS, DIALOGUE_TITLE);

		// Slot 0: NPC head
		final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		final ItemMeta headMeta = head.getItemMeta();
		headMeta.displayName(Component.text("\u00a76" + npcName));
		headMeta.setLore(List.of("\u00a77" + profession));
		head.setItemMeta(headMeta);
		inv.setItem(0, head);

		// Filler panes
		final ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
		final ItemMeta fillerMeta = filler.getItemMeta();
		fillerMeta.displayName(Component.text(" "));
		filler.setItemMeta(fillerMeta);
		for (final int slot : FILLER_SLOTS) {
			inv.setItem(slot, filler.clone());
		}

		// Slot 10: Dialogue text on paper
		final ItemStack paper = new ItemStack(Material.PAPER);
		final ItemMeta paperMeta = paper.getItemMeta();
		paperMeta.displayName(Component.text("\u00a7f" + npcName + " says:"));
		paperMeta.setLore(wordWrap(dialogueText, 40));
		paper.setItemMeta(paperMeta);
		inv.setItem(10, paper);

		return inv;
	}
	public static Inventory createGreeting(final String npcName, final String profession,
		final String greetingText) {

		final Inventory inv = createBase(npcName, profession, greetingText);
		inv.setItem(OPTION_2_SLOT, button(Material.GREEN_DYE, "\u00a7a\u00a7lContinue \u2192"));
		return inv;
	}

	public static Inventory createOptions(final String npcName, final String dialogueText) {
		final Inventory inv = createBase(npcName, "", dialogueText);
		inv.setItem(OPTION_1_SLOT, button(Material.YELLOW_DYE, "\u00a7e\u00a7lChat"));
		inv.setItem(OPTION_3_SLOT, button(Material.RED_DYE, "\u00a7c\u00a7lGoodbye"));
		return inv;
	}

	public static Inventory createQuestOffer(final String npcName,
		final String narrativeText) {

		return createQuestOffer(npcName, "", narrativeText, null);
	}

	public static Inventory createQuestOffer(final String npcName, final String profession,
		final String narrativeText, final Quest quest) {

		final Inventory inv = createBase(npcName, profession, narrativeText);
		inv.setItem(OPTION_2_SLOT, button(Material.GREEN_DYE, "\u00a7a\u00a7lContinue \u2192"));
		return inv;
	}

	public static Inventory createQuestAcceptReject(final String npcName, final Quest quest) {
		final QuestObjective obj = quest.getObjective();

		final String objectiveLine = switch (obj.getType()) {
			case KILL -> "Kill " + obj.getTarget() + " x" + obj.getAmount();
			case COLLECT -> "Collect " + obj.getTarget() + " x" + obj.getAmount();
			case TREASURE -> "Find a hidden treasure chest";
			case FIND_NPC -> "Locate a missing person";
		};

		final String dialogueText = "\u00a77Objective: \u00a7f" + objectiveLine
			+ "\n\u00a77Reward: \u00a7a" + quest.getRewardAmount()
			+ " MCMMO XP in " + quest.getRewardTarget();

		final Inventory inv = createBase(npcName, "", dialogueText);
		inv.setItem(OPTION_1_SLOT,
			button(Material.GREEN_WOOL, "\u00a7a\u00a7lAccept Quest"));
		inv.setItem(OPTION_3_SLOT,
			button(Material.RED_WOOL, "\u00a7c\u00a7lReject Quest"));
		return inv;
	}

	public static Inventory createChatResponse(final String npcName, final String chatText,
		final boolean canOfferHelp) {

		final Inventory inv = createBase(npcName, "", chatText);

		if (canOfferHelp) {
			inv.setItem(OPTION_1_SLOT,
				button(Material.GREEN_DYE, "\u00a7a\u00a7lOffer to help"));
		}

		inv.setItem(OPTION_2_SLOT,
			button(Material.YELLOW_DYE, "\u00a7e\u00a7lContinue chatting"));
		inv.setItem(OPTION_3_SLOT, button(Material.RED_DYE, "\u00a7c\u00a7lGoodbye"));
		return inv;
	}

	public static Inventory createThinking(final String npcName, final String profession) {
		final Inventory inv = createBase(npcName, profession, "...");
		inv.setItem(OPTION_2_SLOT,
			button(Material.CLOCK, "\u00a77\u00a7lPlease wait..."));
		return inv;
	}
	private static ItemStack button(final Material material, final String displayName) {
		final ItemStack item = new ItemStack(material);
		final ItemMeta meta = item.getItemMeta();
		meta.displayName(Component.text(displayName));
		item.setItemMeta(meta);
		return item;
	}

	private static List<String> wordWrap(final String text, final int maxLength) {
		final List<String> lines = new ArrayList<>();
		final String[] words = text.split(" ");
		final StringBuilder current = new StringBuilder();

		for (final String word : words) {
			if (!current.isEmpty() && current.length() + 1 + word.length() > maxLength) {
				lines.add("\u00a77" + current);
				current.setLength(0);
			}

			if (!current.isEmpty()) {
				current.append(' ');
			}
			current.append(word);
		}

		if (!current.isEmpty()) {
			lines.add("\u00a77" + current);
		}

		return lines;
	}
}
