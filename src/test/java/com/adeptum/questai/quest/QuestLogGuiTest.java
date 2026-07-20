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

import com.adeptum.questai.resourcepack.ResourcePackManager;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the quest log's appearance. The resource pack only skins an item
 * when the model data rides along, so a slot built without it silently
 * falls back to vanilla art; these run against a real server so the
 * ItemStacks are the ones a player would actually be shown.
 */
class QuestLogGuiTest {

	@BeforeEach
	void setUp() {
		MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private static Integer modelDataOf(final ItemStack stack) {
		return stack.getItemMeta().hasCustomModelData()
			? stack.getItemMeta().getCustomModelData() : null;
	}

	@Test
	void everyBorderSlotCarriesThePanelSkin() {
		final Inventory inv = QuestLogGui.create(List.of());

		for (final int slot : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26}) {
			final ItemStack pane = inv.getItem(slot);
			assertNotNull(pane, "border slot " + slot + " is empty");
			assertEquals(Material.GRAY_STAINED_GLASS_PANE, pane.getType());
			// Without this the client draws a vanilla pane: a bar in a slot
			assertEquals(ResourcePackManager.CMD, modelDataOf(pane),
				"border slot " + slot + " would render as a vanilla pane");
		}
	}

	@Test
	void theEmptyLogNoteIsSkinnedPaper() {
		final ItemStack note = QuestLogGui.create(List.of()).getItem(13);

		assertNotNull(note);
		assertEquals(Material.PAPER, note.getType());
		assertEquals(ResourcePackManager.CMD, modelDataOf(note));
	}

	@Test
	void aNullQuestListStillBuildsTheEmptyLog() {
		final Inventory inv = QuestLogGui.create(null);

		assertNotNull(inv.getItem(13));
		assertNotNull(inv.getItem(0));
	}
}
