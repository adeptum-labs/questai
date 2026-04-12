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

package com.adeptum.questai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageInfoTest {

	@Test
	void accessorsReturnConstructorValues() {
		final VillageInfo info = new VillageInfo(true, 5, 3, 7);

		assertTrue(info.village());
		assertEquals(5, info.bedCount());
		assertEquals(3, info.workstationCount());
		assertEquals(7, info.villagerCount());
	}

	@Test
	void equalsSameValuesAreEqual() {
		final VillageInfo a = new VillageInfo(true, 4, 4, 6);
		final VillageInfo b = new VillageInfo(true, 4, 4, 6);
		assertEquals(a, b);
	}

	@Test
	void equalsDifferentValuesAreNotEqual() {
		final VillageInfo a = new VillageInfo(true, 4, 4, 6);
		final VillageInfo b = new VillageInfo(false, 4, 4, 6);
		assertNotEquals(a, b);
	}

	@Test
	void toStringContainsFieldValues() {
		final VillageInfo info = new VillageInfo(true, 5, 3, 7);
		final String str = info.toString();
		assertTrue(str.contains("5"));
		assertTrue(str.contains("3"));
		assertTrue(str.contains("7"));
	}
}
