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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestRelicTest {

	@Test
	void idsAreUniqueAndLowercase() {
		final Set<String> ids = new HashSet<>();
		for (final QuestRelic relic : QuestRelic.values()) {
			assertTrue(ids.add(relic.getId()));
			assertEquals(relic.getId().toLowerCase(Locale.ROOT), relic.getId());
		}
	}

	@Test
	void customModelDataValuesAreUniqueAndAboveGuiRange() {
		final Set<Integer> values = new HashSet<>();
		for (final QuestRelic relic : QuestRelic.values()) {
			assertTrue(values.add(relic.getCustomModelData()));
			assertTrue(relic.getCustomModelData() >= 100010);
		}
	}

	@Test
	void materialsAreUnique() {
		final Set<org.bukkit.Material> materials = new HashSet<>();
		for (final QuestRelic relic : QuestRelic.values()) {
			assertTrue(materials.add(relic.getMaterial()));
		}
	}

	@Test
	void fromIdRoundTripsEveryRelic() {
		for (final QuestRelic relic : QuestRelic.values()) {
			assertSame(relic, QuestRelic.fromId(relic.getId()));
		}
		assertNull(QuestRelic.fromId("no_such_relic"));
	}

	@Test
	void everyRelicHasColoredNameAndLore() {
		for (final QuestRelic relic : QuestRelic.values()) {
			assertTrue(relic.getDisplayName().startsWith("\u00a7"));
			assertFalse(relic.getLore().isEmpty());
		}
	}
}
