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

import com.adeptum.questai.fortify.VillageWork;
import com.adeptum.questai.resourcepack.ResourcePackManager;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the works screens' appearance against a real server, the same way
 * the quest log's test does, so the ItemStacks carry the model data the
 * resource pack skins them by.
 */
class DialogueGuiTest {

	@BeforeEach
	void setUp() {
		MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void theOptionsScreenOffersTheWorksOnlyWhenOpen() {
		final Inventory without = DialogueGui.createOptions("Bo", "Hello",
			true, true, false);
		final Inventory with = DialogueGui.createOptions("Bo", "Hello",
			true, true, true);

		// A closed ladder leaves the centre slot as border filler
		assertEquals(Material.GRAY_STAINED_GLASS_PANE,
			without.getItem(DialogueGui.CENTER_SLOT).getType());
		assertEquals(Material.BRICKS,
			with.getItem(DialogueGui.CENTER_SLOT).getType());
	}

	@Test
	void theDonateButtonIsSkinnedAndCancellable() {
		final Inventory inv = DialogueGui.createWorkOffer("Bo",
			VillageWork.WATCHTOWER,
			Map.of("logs", 20), Map.of("logs", 7));

		final ItemStack donate = inv.getItem(DialogueGui.OPTION_1_SLOT);
		assertEquals(Material.CHEST, donate.getType());
		assertTrue(donate.getItemMeta().hasCustomModelData());
		assertEquals(ResourcePackManager.CMD,
			donate.getItemMeta().getCustomModelData());
		assertNotNull(inv.getItem(DialogueGui.OPTION_4_SLOT));
	}
}
