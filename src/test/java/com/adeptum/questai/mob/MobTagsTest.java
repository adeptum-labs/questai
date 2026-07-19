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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobTagsTest {

	@Test
	void tagWritesTheVariantId() {
		final PersistentDataContainer pdc = mock(PersistentDataContainer.class);

		MobTags.tag(pdc, MobVariant.GRAVEHULK);

		verify(pdc).set(any(NamespacedKey.class),
			eq(PersistentDataType.STRING), eq("gravehulk"));
	}

	@Test
	void variantOfResolvesEveryVariant() {
		for (final MobVariant variant : MobVariant.values()) {
			final PersistentDataContainer pdc =
				mock(PersistentDataContainer.class);
			when(pdc.get(any(NamespacedKey.class),
				eq(PersistentDataType.STRING))).thenReturn(variant.getId());

			assertSame(variant, MobTags.variantOf(pdc));
		}
	}

	@Test
	void variantOfRejectsUntaggedAndUnknown() {
		final PersistentDataContainer untagged =
			mock(PersistentDataContainer.class);
		assertNull(MobTags.variantOf(untagged));

		final PersistentDataContainer unknown =
			mock(PersistentDataContainer.class);
		when(unknown.get(any(NamespacedKey.class),
			eq(PersistentDataType.STRING))).thenReturn("no_such_mob");
		assertNull(MobTags.variantOf(unknown));
	}
}
