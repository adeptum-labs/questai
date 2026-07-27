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

import com.adeptum.questai.craft.Commission;
import com.adeptum.questai.fortify.VillageWork;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.teleport.VillageTeleportStone;
import com.adeptum.questai.utility.GuiItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
	/** The craftsman's own slot, clear of the contested centre. */
	public static final int CRAFT_SLOT = 24;

	private static final int[] FILLER_SLOTS = {
		1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17,
		18, 19, 20, 21, 22, 23, 24, 25, 26
	};

	private DialogueGui() {
	}

	/**
	 * Returns true for inventories created by this factory, identified by
	 * their marker holder rather than by parsing the view title.
	 */
	public static boolean isDialogueInventory(final InventoryView view) {
		return view.getTopInventory().getHolder() instanceof DialogueHolder;
	}

	/** Marker holder identifying dialogue inventories. */
	private static final class DialogueHolder implements InventoryHolder {
		private Inventory inventory;

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	private static Component buildTitle() {
		return Component.text(DIALOGUE_BANNER_GLYPH).font(DIALOGUE_FONT)
			.append(Component.text("  " + DIALOGUE_TITLE_MARKER,
				NamedTextColor.GOLD));
	}

	private static Inventory createBase(final String npcName, final String profession,
		final String dialogueText) {

		final DialogueHolder holder = new DialogueHolder();
		final Inventory inv = Bukkit.createInventory(holder, ROWS, buildTitle());
		holder.inventory = inv;

		inv.setItem(0, GuiItems.item(Material.PLAYER_HEAD,
			"\u00a76" + npcName, List.of("\u00a77" + profession)));

		final ItemStack filler = GuiItems.filler();
		for (final int slot : FILLER_SLOTS) {
			inv.setItem(slot, filler.clone());
		}

		inv.setItem(10, GuiItems.item(Material.PAPER,
			"\u00a7f" + npcName + " says:", wordWrap(dialogueText, 40), CMD));

		return inv;
	}

	public static Inventory createGreeting(final String npcName, final String profession,
		final String greetingText) {

		final Inventory inv = createBase(npcName, profession, greetingText);
		inv.setItem(CENTER_SLOT,
			button(Material.GREEN_DYE, "\u00a7a\u00a7lContinue \u2192", CMD + 1));
		return inv;
	}

	public static Inventory createOptions(final String npcName,
		final String dialogueText, final DialogueOptions options) {

		final Inventory inv = createBase(npcName, "", dialogueText);
		inv.setItem(OPTION_1_SLOT,
			button(Material.YELLOW_DYE,
				"\u00a7e\u00a7lWhat's new around here?", CMD));
		if (options.questAvailable()) {
			inv.setItem(OPTION_2_SLOT,
				button(Material.GREEN_DYE,
					"\u00a7a\u00a7lDo you need help with anything?", CMD));
		}
		if (options.tradeable()) {
			inv.setItem(OPTION_3_SLOT,
				button(Material.EMERALD, "\u00a7a\u00a7lLet's trade", CMD));
		}
		// The works and the stone share the centre slot; the ladder must be
		// finished before a stone is offered, so the two never coincide
		if (options.worksOpen()) {
			inv.setItem(CENTER_SLOT,
				button(Material.BRICKS,
					"\u00a76\u00a7lThe village needs materials", CMD));
		} else if (options.stoneOffer()) {
			inv.setItem(CENTER_SLOT,
				button(Material.HEART_OF_THE_SEA,
					"\u00a7b\u00a7lThe village offers you a way home",
					VillageTeleportStone.CMD));
		}
		setCraftEntry(inv, options);
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_DYE, "\u00a7c\u00a7lI should get going", CMD));
		return inv;
	}

	/**
	 * The craftsman's entry, which keeps its own slot rather than joining
	 * the contest for the centre: the works stay open for most of a
	 * village's life, and a commission behind them would never be seen.
	 */
	private static void setCraftEntry(final Inventory inv,
		final DialogueOptions options) {

		if (options.commissionReady()) {
			inv.setItem(CRAFT_SLOT, button(Material.ANVIL,
				"\u00a7b\u00a7lYour commission is ready", CMD));
		} else if (options.commissionWaiting()) {
			inv.setItem(CRAFT_SLOT, button(Material.CLOCK,
				"\u00a77\u00a7lStill at the workbench", CMD));
		} else if (options.commissionOffer()) {
			inv.setItem(CRAFT_SLOT, button(Material.ANVIL,
				"\u00a76\u00a7lCommission a piece of work", CMD));
		}
	}

	/** What the village still wants, and what the player is carrying. */
	public static Inventory createWorkOffer(final String npcName,
		final VillageWork work, final Map<String, Integer> outstanding,
		final Map<String, Integer> carried) {

		final Inventory inv = createBase(npcName, "",
			"We are raising " + work.getDisplayName() + ".");

		final List<String> lore = new ArrayList<>();
		outstanding.forEach((role, amount) -> lore.add("\u00a77" + role
			+ ": \u00a7f" + amount + " \u00a78(carrying "
			+ carried.getOrDefault(role, 0) + ")"));

		inv.setItem(OPTION_1_SLOT, GuiItems.item(Material.CHEST,
			"\u00a7a\u00a7lDonate what I carry", lore, CMD));
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_DYE, "\u00a7c\u00a7lNot now", CMD));
		return inv;
	}

	/** Offers the finished village's teleport stone for the player to claim. */
	public static Inventory createStoneOffer(final String npcName,
		final String villageName) {

		final Inventory inv = createBase(npcName, "", "Our walls stand "
			+ "finished. Carry this, and you will always find your way "
			+ "back to " + villageName + ".");
		inv.setItem(OPTION_1_SLOT, GuiItems.item(Material.HEART_OF_THE_SEA,
			"\u00a7b\u00a7lAccept the stone",
			List.of("\u00a77A way home to \u00a76" + villageName),
			VillageTeleportStone.CMD));
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_DYE, "\u00a7c\u00a7lNot now", CMD));
		return inv;
	}

	/** What the craftsman wants for the piece, and what the player carries. */
	public static Inventory createCommissionOffer(final String npcName,
		final Commission commission, final Map<String, Integer> cost,
		final Map<String, Integer> carried) {

		final Inventory inv = createBase(npcName, "",
			"I could make you " + commission.getDisplayName()
				+ ", if you bring what it takes.");

		final List<String> lore = new ArrayList<>();
		cost.forEach((role, amount) -> lore.add("§7" + role
			+ ": §f" + amount + " §8(carrying "
			+ carried.getOrDefault(role, 0) + ")"));
		lore.add("§8Ready in about " + commission.getGate().minutes()
			+ " minutes");

		inv.setItem(OPTION_1_SLOT, GuiItems.item(Material.ANVIL,
			"§6§lCommission it", lore, CMD));
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_DYE, "§c§lNot now", CMD));
		return inv;
	}

	/** How much longer the piece on the workbench needs. */
	public static Inventory createCommissionWait(final String npcName,
		final Commission commission, final long remainingMinutes) {

		final Inventory inv = createBase(npcName, "",
			"I am still at " + commission.getDisplayName()
				+ ". Come back and it will be waiting.");
		inv.setItem(CENTER_SLOT, GuiItems.item(Material.CLOCK,
			"§7§lStill at the workbench",
			List.of("§8About " + remainingMinutes + " minutes to go"), CMD));
		return inv;
	}

	/** The finished piece, waiting to be taken. */
	public static Inventory createCommissionCollect(final String npcName,
		final Commission commission) {

		final Inventory inv = createBase(npcName, "",
			"Here it is, " + commission.getDisplayName()
				+ ", as good as these hands can make it.");
		inv.setItem(OPTION_1_SLOT, GuiItems.item(commission.icon(),
			"§b§lTake it", List.of("§7" + commission.getDisplayName()), CMD));
		inv.setItem(OPTION_4_SLOT,
			button(Material.RED_DYE, "§c§lLeave it a while", CMD));
		return inv;
	}

	public static Inventory createQuestOffer(final String npcName,
		final String narrativeText) {

		final Inventory inv = createBase(npcName, "", narrativeText);
		inv.setItem(CENTER_SLOT,
			button(Material.GREEN_DYE, "\u00a7a\u00a7lContinue \u2192", CMD + 1));
		return inv;
	}

	public static Inventory createQuestAcceptReject(final String npcName,
		final Quest quest) {

		String dialogueText = "\u00a77Objective: \u00a7f"
			+ quest.getObjective().describe()
			+ "\n\u00a77Reward: \u00a7a" + quest.getRewardAmount()
			+ " MCMMO XP in " + quest.getRewardTarget();
		if (quest.getChainStep() > 0) {
			dialogueText += "\n\u00a76Part " + quest.getChainStep() + " of "
				+ quest.getChainLength() + " \u00a77(continued)";
		}

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

		return GuiItems.item(material, displayName, null, customModelData);
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
