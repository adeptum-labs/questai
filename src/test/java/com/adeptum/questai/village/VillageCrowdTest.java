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

import com.adeptum.questai.fortify.VillageExtent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillageCrowdTest {

	private static final UUID WORLD_ID =
		UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	@Test
	void aCrowdNamesItsOwnMiddle() {
		final List<VillageExtent.Point> crowd = List.of(
			new VillageExtent.Point(130, 150),
			new VillageExtent.Point(137, 161),
			new VillageExtent.Point(150, 170));

		final VillageExtent.Extent extent =
			VillageExtent.measure(crowd, 140, 135);

		assertEquals(137, extent.centreX());
		assertEquals(161, extent.centreZ());
	}

	@Test
	void tooSmallACrowdKeepsTheStoredCentre() {
		final VillageExtent.Extent extent = VillageExtent.measure(
			List.of(new VillageExtent.Point(999, 999)), 140, 135);

		assertEquals(140, extent.centreX());
		assertEquals(135, extent.centreZ());
	}
}
