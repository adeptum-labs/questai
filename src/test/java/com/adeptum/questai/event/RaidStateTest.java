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

package com.adeptum.questai.event;

import com.adeptum.questai.villager.StoredLocation;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaidStateTest {

	private static final long DEADLINE = 10_000L;

	private static RaidState raid(final Set<UUID> raiders) {
		return new RaidState(VillageKey.from(UUID.randomUUID(), 0, 0),
			new StoredLocation(UUID.randomUUID(), 0, 64, 0), DEADLINE, raiders);
	}

	@Test
	void killingAllRaidersDefendsTheVillage() {
		final UUID first = UUID.randomUUID();
		final UUID second = UUID.randomUUID();
		final RaidState raid = raid(Set.of(first, second));

		assertEquals(RaidState.Outcome.ONGOING, raid.check(1000L));
		assertTrue(raid.onRaiderDeath(first));
		assertTrue(raid.onRaiderDeath(second));
		assertEquals(RaidState.Outcome.DEFENDED, raid.check(1000L));
	}

	@Test
	void expiryFailsTheRaid() {
		final RaidState raid = raid(Set.of(UUID.randomUUID()));

		assertEquals(RaidState.Outcome.FAILED, raid.check(DEADLINE + 1));
	}

	@Test
	void escapedRaiderForfeitsTheDefense() {
		final UUID raider = UUID.randomUUID();
		final RaidState raid = raid(Set.of(raider));

		raid.onRaiderVanished(raider);
		assertEquals(RaidState.Outcome.FAILED, raid.check(1000L));
	}

	@Test
	void villagerLossForfeitsTheDefense() {
		final UUID raider = UUID.randomUUID();
		final RaidState raid = raid(Set.of(raider));

		raid.onVillagerLost();
		raid.onRaiderDeath(raider);
		assertEquals(RaidState.Outcome.FAILED, raid.check(1000L));
	}

	@Test
	void unknownEntitiesAreNotRaiders() {
		final RaidState raid = raid(Set.of(UUID.randomUUID()));

		assertFalse(raid.onRaiderDeath(UUID.randomUUID()));
		assertEquals(RaidState.Outcome.ONGOING, raid.check(1000L));
	}

	@Test
	void villagerLossesAreCounted() {
		final UUID raider = UUID.randomUUID();
		final RaidState raid = raid(Set.of(raider));
		raid.onVillagerLost();
		raid.onVillagerLost();
		raid.onRaiderDeath(raider);

		assertEquals(2, raid.getVillagersLost());
		assertEquals(RaidState.Outcome.FAILED, raid.check(1000L));
	}
}
