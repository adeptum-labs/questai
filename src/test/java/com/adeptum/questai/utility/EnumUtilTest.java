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

package com.adeptum.questai.utility;

import com.adeptum.questai.model.world.quest.QuestObjective;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumUtilTest {

	@Test
	void randomReturnsValidEnumConstant() {
		for (int i = 0; i < 100; i++) {
			final QuestObjective.Type result =
				EnumUtil.random(QuestObjective.Type.class);
			assertNotNull(result);
		}
	}

	@Test
	void randomCoversAllValues() {
		final EnumSet<QuestObjective.Type> seen =
			EnumSet.noneOf(QuestObjective.Type.class);

		for (int i = 0; i < 1000; i++) {
			seen.add(EnumUtil.random(QuestObjective.Type.class));
		}

		assertEquals(
			EnumSet.allOf(QuestObjective.Type.class), seen,
			"All enum values should appear after 1000 random picks"
		);
	}
}
