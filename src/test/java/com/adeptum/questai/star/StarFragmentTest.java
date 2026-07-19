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

package com.adeptum.questai.star;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StarFragmentTest {

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
	void recognizesGenuineFragments() {
		assertTrue(StarFragment.isFragment(tagged("star_fragment")));
	}

	@Test
	void rejectsEverythingElse() {
		assertFalse(StarFragment.isFragment(null));
		assertFalse(StarFragment.isFragment(mock(ItemStack.class)));
		assertFalse(StarFragment.isFragment(tagged(null)));
		assertFalse(StarFragment.isFragment(tagged("something_else")));
	}
}
