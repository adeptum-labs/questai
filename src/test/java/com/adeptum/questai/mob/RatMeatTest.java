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

package com.adeptum.questai.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RatMeatTest {

	private ItemStack tagged(final String id) {
		final ItemStack item = mock(ItemStack.class);
		final ItemMeta meta = mock(ItemMeta.class);
		final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
		when(item.getItemMeta()).thenReturn(meta);
		when(meta.getPersistentDataContainer()).thenReturn(pdc);
		when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
			.thenReturn(id);
		return item;
	}

	@Test
	void recognizesGenuineRatMeat() {
		assertTrue(RatMeat.isRatMeat(tagged("rat_meat")));
	}

	@Test
	void rejectsEverythingElse() {
		assertFalse(RatMeat.isRatMeat(null));
		assertFalse(RatMeat.isRatMeat(mock(ItemStack.class)));
		assertFalse(RatMeat.isRatMeat(tagged(null)));
		assertFalse(RatMeat.isRatMeat(tagged("star_fragment")));
	}
}
