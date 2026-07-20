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

package com.adeptum.questai.village;

import com.adeptum.questai.event.VillageKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageNamesTest {

	private static final UUID WORLD =
		UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	@Test
	void theBiomeAndSizeReachThePrompt() {
		final String prompt = VillageNames.prompt("savanna", 9, 5);

		assertTrue(prompt.contains("savanna"));
		assertTrue(prompt.contains("9"));
		assertTrue(prompt.contains("5"));
		assertTrue(prompt.contains("Output ONLY"));
	}

	@Test
	void aPlainReplyPassesThrough() {
		assertEquals("Ravenhollow", VillageNames.sanitise("Ravenhollow"));
		assertEquals("Elder Mere", VillageNames.sanitise("  Elder Mere  "));
	}

	@Test
	void labelsQuotesAndTrailingStopsAreStripped() {
		assertEquals("Ravenhollow", VillageNames.sanitise("Name: Ravenhollow"));
		assertEquals("Ravenhollow", VillageNames.sanitise("Village: \"Ravenhollow\""));
		assertEquals("Ravenhollow", VillageNames.sanitise("\"Ravenhollow\""));
		assertEquals("Ravenhollow", VillageNames.sanitise("Ravenhollow."));
	}

	@Test
	void theFirstUsableLineWins() {
		assertEquals("Frostmere", VillageNames.sanitise("\n\n  \nFrostmere\nSome waffle"));
	}

	@Test
	void overlongNamesAreClamped() {
		final String name = VillageNames.sanitise("A".repeat(80));

		assertEquals(VillageNames.MAX_LENGTH, name.length());
	}

	@Test
	void nothingUsableYieldsNull() {
		assertNull(VillageNames.sanitise(null));
		assertNull(VillageNames.sanitise(""));
		assertNull(VillageNames.sanitise("   \n  \n"));
		assertNull(VillageNames.sanitise("Name:"));
	}

	@Test
	void theFallbackIsStableForACellAndDiffersBetweenCells() {
		final VillageKey key = new VillageKey(WORLD, 3, -7);

		assertEquals(VillageNames.fallbackName(key), VillageNames.fallbackName(key));
		assertNotEquals(VillageNames.fallbackName(key),
			VillageNames.fallbackName(new VillageKey(WORLD, 4, -7)));
	}

	@Test
	void theFallbackFitsTheBossBar() {
		for (int x = -40; x < 40; x++) {
			final String name = VillageNames.fallbackName(new VillageKey(WORLD, x, x * 3));
			assertFalse(name.isEmpty());
			assertTrue(name.length() <= VillageNames.MAX_LENGTH, name);
		}
	}
}
