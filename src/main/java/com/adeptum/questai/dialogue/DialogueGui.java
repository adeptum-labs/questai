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

import static com.adeptum.questai.resourcepack.ResourcePackManager.CMD;
import static com.adeptum.questai.resourcepack.ResourcePackManager.DIALOGUE_BANNER_GLYPH;

import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for 27-slot chest inventory GUIs used in the NPC conversation system.
 *
 * <p>Option buttons use four evenly-spaced slots in the bottom row:
 * <pre>
 *   Row 2: [F][O1][F][O2][F][O3][F][O4][F]
 *           18  19  20  21  22  23  24  25  26
 * </pre>
 * Single-action screens (Continue, Please wait) use the centre slot (22).
 */
public final class DialogueGui {

	/** Plain-text marker substring used to identify dialogue inventories. */
	public static final String DIALOGUE_TITLE_MARKER = "NPC Dialogue";

	private static final Key DIALOGUE_FONT = Key.key("questai", "dialogue");
	private static final int ROWS = 27;

	public static final int OPTION_1_SLOT = 19;
	public static final int OPTION_2_SLOT = 21;
	public static final int OPTION_3_SLOT = 23;
	public static final int OPTION_4_SLOT = 25;
	public static final int CENTER_SLOT = 22;

	private static final int[] FILLER_SLOTS = {
		1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17,
		18, 19, 20, 21, 22, 23, 24, 25, 26
	};

	private DialogueGui() {
	}

	/**
	 * Returns true for inventories whose title contains the dialogue marker —
	 * matches legacy and Component-titled views alike.
	 */
	public static boolean isDialogueInventory(final InventoryView view) {
		final String plain = PlainTextComponentSerializer.plainText()
			.serialize(view.title());
		return plain.contains(DIALOGUE_TITLE_MARKER);
	}

	private static Component buildTitle() {
		return Component.text(DIALOGUE_BANNER_GLYPH).font(DIALOGUE_FONT)
			.append(Component.text("  " + DIALOGUE_TITLE_MARKER,
				NamedTextColor.GOLD));
	}

	private static Inventory createBase(final String npcName, final String profession,
		final String dialogueText) {

		final Inventory inv = Bukkit.createInventory(null, ROWS, buildTitle());

		final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		final ItemMeta headMeta = head.getItemMeta();
		headMeta.displayName(Component.text("\u00a76" + npcName));
		headMeta.setLore(List.of("\u00a77" + profession));
		head.setItemMeta(headMeta);
		inv.setItem(0, head);

		final ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", CMD);
		for (final int slot : FILLER_SLOTS) {
			inv.setItem(slot, filler.clone());
		}

		inv.setItem(10, dialogueItem(npcName, dialogueText));

		return inv;
	}

	private static ItemStack dialogueItem(final String npcName, final String text) {
		final ItemStack paper = new ItemStack(Material.PAPER);
		final ItemMeta meta = paper.getItemMeta();
		meta.displayName(Component.text("\u00a7f" + npcName + " says:"));
		meta.setLore(wordWrap(text, 40));
		meta.setCustomModelData(CMD);
		paper.setItemMeta(meta);
		return paper;
	}

	public static Inventory createGreeting(final String npcName, final String profession,
		final String greetingText) {

		final Inventory inv = createBase(npcName, profession, greetingText);
		inv.setItem(CENTER_SLOT,
			button(Material.GREEN_DYE, "\u00a7a\u00a7lContinue \u2192", CMD + 1));
		return inv;
	}

	public static Inventory createOptions(final String npcName, final String dialogueText,
		final boolean questAvailable, final boolean tradeable) {

		final Inventory inv = createBase(npcName, "", dialogueText);
		inv.setItem(OPTION_1_SLOT,
			button(Material.YELLOW_DYE,
				"\u00a7e\u00a7lWhat's new around here?", CMD));
		if (questAvailable) {
			inv.setItem(OPTION_2_SLOT,
				button(Material.GREEN_DYE,
					"\u00a7a\u00a7lDo you need help with anything?", CMD));
		}
		if (tradeable) {
			inv.setItem(OPTION_3_SLOT,
				button(Material.EMERALD, "\u00a7a\u00a7lLet's trade", CMD));
		}
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_DYE, "\u00a7c\u00a7lI should get going", CMD));
		return inv;
	}

	public static Inventory createQuestOffer(final String npcName,
		final String narrativeText) {

		return createQuestOffer(npcName, "", narrativeText, null);
	}

	public static Inventory createQuestOffer(final String npcName, final String profession,
		final String narrativeText, final Quest quest) {

		final Inventory inv = createBase(npcName, profession, narrativeText);
		inv.setItem(CENTER_SLOT,
			button(Material.GREEN_DYE, "\u00a7a\u00a7lContinue \u2192", CMD + 1));
		return inv;
	}

	public static Inventory createQuestAcceptReject(final String npcName,
		final Quest quest) {

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
			button(Material.GREEN_WOOL, "\u00a7a\u00a7lAccept Quest", CMD));
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_WOOL, "\u00a7c\u00a7lReject Quest", CMD));
		return inv;
	}

	public static Inventory createChatResponse(final String npcName, final String chatText,
		final boolean canOfferHelp) {

		final Inventory inv = createBase(npcName, "", chatText);
		inv.setItem(OPTION_1_SLOT,
			button(Material.YELLOW_DYE, "\u00a7e\u00a7lTell me more", CMD));
		if (canOfferHelp) {
			inv.setItem(OPTION_2_SLOT,
				button(Material.GREEN_DYE,
					"\u00a7a\u00a7lIs there anything I can do?", CMD));
		}
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_DYE, "\u00a7c\u00a7lTake care", CMD));
		return inv;
	}

	public static Inventory createThinking(final String npcName, final String profession) {
		final Inventory inv = createBase(npcName, profession, "...");
		inv.setItem(CENTER_SLOT,
			button(Material.CLOCK, "\u00a77\u00a7lPlease wait...", CMD));
		return inv;
	}

	private static ItemStack button(final Material material, final String displayName,
		final int customModelData) {

		final ItemStack stack = new ItemStack(material);
		final ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(displayName));
		meta.setCustomModelData(customModelData);
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack item(final Material material, final String displayName,
		final int customModelData) {

		final ItemStack stack = new ItemStack(material);
		final ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(displayName));
		meta.setCustomModelData(customModelData);
		stack.setItemMeta(meta);
		return stack;
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
