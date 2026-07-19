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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelicEffectsTest {

	@Test
	void quillBoostsXpByAQuarterWithRounding() {
		assertEquals(100, RelicEffects.questXp(100, false));
		assertEquals(125, RelicEffects.questXp(100, true));
		assertEquals(38, RelicEffects.questXp(30, true));
	}

	@Test
	void charmDoublesTheRareChance() {
		assertEquals(0.15, RelicEffects.treasureRareChance(false));
		assertEquals(0.30, RelicEffects.treasureRareChance(true));
	}

	@Test
	void locketRaisesTheOfferChance() {
		assertEquals(0.3, RelicEffects.questOfferChance(false));
		assertEquals(0.5, RelicEffects.questOfferChance(true));
	}
}
