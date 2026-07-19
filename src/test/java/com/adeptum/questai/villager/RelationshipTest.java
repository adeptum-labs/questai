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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipTest {

	@Test
	void surnameIsTheLastNameToken() {
		assertEquals("Stone", Relationship.surname("Edric Stone"));
		assertEquals("Bloom", Relationship.surname("Mira Rose Bloom"));
	}

	@Test
	void surnameStripsTheUniquenessSuffix() {
		assertEquals("Dusk", Relationship.surname("Goran Dusk_1234"));
	}

	@Test
	void singleTokenNamesHaveNoSurname() {
		assertNull(Relationship.surname("Villager"));
		assertNull(Relationship.surname("Villager_1234"));
	}

	@Test
	void nounsPhraseEveryType() {
		assertEquals("kin", Relationship.Type.KIN.noun());
		assertEquals("old friend", Relationship.Type.OLD_FRIEND.noun());
		assertEquals("rival", Relationship.Type.RIVAL.noun());
	}
}
