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

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelicCooldownsTest {

	private static final long COOLDOWN = 30_000L;

	@Test
	void readyUntilRecordedThenHonorsTheWindow() {
		final RelicCooldowns cooldowns = new RelicCooldowns();
		final UUID player = UUID.randomUUID();

		assertTrue(cooldowns.isReady(player,
			QuestRelic.WAYFARERS_COMPASS, 1000L, COOLDOWN));
		// Checking alone never consumes the cooldown
		assertTrue(cooldowns.isReady(player,
			QuestRelic.WAYFARERS_COMPASS, 1000L, COOLDOWN));

		cooldowns.record(player, QuestRelic.WAYFARERS_COMPASS, 1000L, COOLDOWN);
		assertFalse(cooldowns.isReady(player,
			QuestRelic.WAYFARERS_COMPASS, 1000L + COOLDOWN - 1, COOLDOWN));
		assertTrue(cooldowns.isReady(player,
			QuestRelic.WAYFARERS_COMPASS, 1000L + COOLDOWN, COOLDOWN));
	}

	@Test
	void cooldownsAreIndependentPerPlayerAndRelic() {
		final RelicCooldowns cooldowns = new RelicCooldowns();
		final UUID first = UUID.randomUUID();
		final UUID second = UUID.randomUUID();

		cooldowns.record(first, QuestRelic.PEDDLERS_BELL, 1000L, COOLDOWN);

		assertFalse(cooldowns.isReady(first,
			QuestRelic.PEDDLERS_BELL, 2000L, COOLDOWN));
		assertTrue(cooldowns.isReady(first,
			QuestRelic.WAYFARERS_COMPASS, 2000L, COOLDOWN));
		assertTrue(cooldowns.isReady(second,
			QuestRelic.PEDDLERS_BELL, 2000L, COOLDOWN));
	}
}
