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

package com.adeptum.questai.relic;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelicItemsTest {

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
	void relicOfResolvesTaggedItems() {
		assertSame(QuestRelic.ELDERS_QUILL,
			RelicItems.relicOf(tagged("elders_quill")));
	}

	@Test
	void relicOfRejectsUntaggedAndBrokenItems() {
		assertNull(RelicItems.relicOf(null));

		final ItemStack noMeta = mock(ItemStack.class);
		assertNull(RelicItems.relicOf(noMeta));

		assertNull(RelicItems.relicOf(tagged(null)));
		assertNull(RelicItems.relicOf(tagged("unknown_relic")));
	}

	@Test
	void holdsFindsTheRelicAmongOtherItems() {
		final ItemStack[] contents = {
			null, mock(ItemStack.class), tagged("peddlers_bell")
		};

		assertTrue(RelicItems.holds(contents, QuestRelic.PEDDLERS_BELL));
		assertFalse(RelicItems.holds(contents, QuestRelic.ELDERS_QUILL));
	}

	@Test
	void ownedRelicsCollectsDistinctRelics() {
		final ItemStack[] contents = {
			tagged("elders_quill"), null, tagged("elders_quill"),
			mock(ItemStack.class), tagged("whispering_locket")
		};

		final Set<QuestRelic> owned = RelicItems.ownedRelics(contents);
		assertEquals(Set.of(QuestRelic.ELDERS_QUILL,
			QuestRelic.WHISPERING_LOCKET), owned);
	}
}
