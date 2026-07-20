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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobFitTest {

	@Test
	void vanillaAndSmallerScalesNeedNoExtraRoom() {
		assertTrue(MobFit.clearanceOffsets(1.0).isEmpty());
		assertTrue(MobFit.clearanceOffsets(
			MobVariant.GRAVELING.getCombat().scale()).isEmpty());
		assertTrue(MobFit.clearanceOffsets(
			MobVariant.CINDERLING.getCombat().scale()).isEmpty());
	}

	@Test
	void gravehulkNeedsFourBlocksOfHeadroomAndItsNeighbours() {
		final List<MobFit.Offset> offsets = MobFit.clearanceOffsets(
			MobVariant.GRAVEHULK.getCombat().scale());

		// 3.9 blocks tall and 1.2 wide, so a 3 x 4 x 3 pocket
		assertEquals(36, offsets.size());
		assertEquals(0, offsets.stream().mapToInt(MobFit.Offset::y).min().orElseThrow());
		assertEquals(3, offsets.stream().mapToInt(MobFit.Offset::y).max().orElseThrow());
		assertEquals(-1, offsets.stream().mapToInt(MobFit.Offset::x).min().orElseThrow());
		assertEquals(1, offsets.stream().mapToInt(MobFit.Offset::x).max().orElseThrow());
	}

	@Test
	void theSpawnBlockItselfIsAlwaysClaimed() {
		assertTrue(MobFit.clearanceOffsets(2.0)
			.contains(new MobFit.Offset(0, 0, 0)));
	}

	@Test
	void aScaleThatStaysWithinOneColumnClaimsOnlyThatColumn() {
		// 1.5 is 0.9 wide, still inside the block, but 2.9 blocks tall
		final List<MobFit.Offset> offsets = MobFit.clearanceOffsets(1.5);

		assertEquals(3, offsets.size());
		assertTrue(offsets.stream().allMatch(o -> o.x() == 0 && o.z() == 0));
	}
}
