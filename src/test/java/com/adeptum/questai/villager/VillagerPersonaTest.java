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

package com.adeptum.questai.villager;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerPersonaTest {

	@Test
	void parsesFullThreeLineResponse() {
		final VillagerPersona persona = VillagerPersona.parse(
			"Edric Stone\ngruff; superstitious; secretly kind\n"
				+ "Back again? The turnips missed you.");

		assertEquals("Edric Stone", persona.name());
		assertEquals(List.of("gruff", "superstitious", "secretly kind"),
			persona.traits());
		assertEquals("Back again? The turnips missed you.", persona.greeting());
	}

	@Test
	void parsesNameOnlyResponse() {
		final VillagerPersona persona = VillagerPersona.parse("Mira Bloom");

		assertEquals("Mira Bloom", persona.name());
		assertTrue(persona.traits().isEmpty());
		assertNull(persona.greeting());
	}

	@Test
	void skipsBlankLinesAndStripsQuotesAndLabels() {
		final VillagerPersona persona = VillagerPersona.parse(
			"\nName: \"Goran Dusk\"\n\nTraits: brooding; poetic\n"
				+ "Greeting: \"Hmph. You again.\"\n");

		assertEquals("Goran Dusk", persona.name());
		assertEquals(List.of("brooding", "poetic"), persona.traits());
		assertEquals("Hmph. You again.", persona.greeting());
	}

	@Test
	void capsTraitsAtThreeAndAcceptsCommas() {
		final VillagerPersona persona = VillagerPersona.parse(
			"Mira Bloom\ncurious, bookish, shy, loud, brave\nHello!");

		assertEquals(List.of("curious", "bookish", "shy"), persona.traits());
	}

	@Test
	void truncatesOverlongGreeting() {
		final VillagerPersona persona = VillagerPersona.parse(
			"Mira Bloom\nkind\n" + "x".repeat(200));

		assertEquals(80, persona.greeting().length());
	}

	@Test
	void emptyResponseFallsBackToVillager() {
		final VillagerPersona persona = VillagerPersona.parse("  \n \n");

		assertEquals("Villager", persona.name());
		assertTrue(persona.traits().isEmpty());
		assertNull(persona.greeting());
	}

	@Test
	void promptContainsProfessionAndFormatRules() {
		final String prompt = VillagerPersona.prompt(UUID.randomUUID(), "FARMER");

		assertTrue(prompt.contains("FARMER"));
		assertTrue(prompt.contains("three lines"));
		assertTrue(prompt.contains("greeting"));
	}
}
