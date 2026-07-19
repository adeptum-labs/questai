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

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipFormerTest {

	private static final UUID WORLD = UUID.randomUUID();

	private static RelationshipFormer.Snapshot snapshot(final String name,
		final double x, final UUID worldId, final int tieCount,
		final Set<UUID> tiedTo) {

		return new RelationshipFormer.Snapshot(UUID.randomUUID(), name,
			new StoredLocation(worldId, x, 64, 0), tieCount, tiedTo);
	}

	@Test
	void sharedSurnamesAlwaysMakeKin() {
		final List<RelationshipFormer.Snapshot> pair = List.of(
			snapshot("Edric Dusk", 0, WORLD, 0, Set.of()),
			snapshot("Mira Dusk_1234", 10, WORLD, 0, Set.of()));

		// Every seed yields KIN: kinship needs no roll
		for (int seed = 0; seed < 20; seed++) {
			final List<RelationshipFormer.Tie> ties =
				RelationshipFormer.plan(pair, new Random(seed));
			assertEquals(1, ties.size());
			assertEquals(Relationship.Type.KIN, ties.get(0).type());
		}
	}

	@Test
	void strangersSometimesBondAsFriendsOrRivals() {
		final EnumSet<Relationship.Type> seen =
			EnumSet.noneOf(Relationship.Type.class);
		int formed = 0;

		for (int seed = 0; seed < 400; seed++) {
			final List<RelationshipFormer.Tie> ties = RelationshipFormer.plan(
				List.of(snapshot("Edric Stone", 0, WORLD, 0, Set.of()),
					snapshot("Mira Bloom", 10, WORLD, 0, Set.of())),
				new Random(seed));
			for (final RelationshipFormer.Tie tie : ties) {
				formed++;
				seen.add(tie.type());
			}
		}

		assertTrue(formed > 40 && formed < 200,
			"Roughly a quarter of meetings should bond, got " + formed);
		assertTrue(seen.contains(Relationship.Type.OLD_FRIEND));
		assertTrue(seen.contains(Relationship.Type.RIVAL));
		assertFalse(seen.contains(Relationship.Type.KIN));
	}

	@Test
	void outOfRangeAndForeignWorldsFormNothing() {
		assertTrue(RelationshipFormer.plan(List.of(
			snapshot("Edric Dusk", 0, WORLD, 0, Set.of()),
			snapshot("Mira Dusk", 100, WORLD, 0, Set.of())),
			new Random(1)).isEmpty());

		assertTrue(RelationshipFormer.plan(List.of(
			snapshot("Edric Dusk", 0, WORLD, 0, Set.of()),
			snapshot("Mira Dusk", 0, UUID.randomUUID(), 0, Set.of())),
			new Random(1)).isEmpty());
	}

	@Test
	void cappedAndAlreadyTiedVillagersFormNothing() {
		final RelationshipFormer.Snapshot capped = snapshot(
			"Edric Dusk", 0, WORLD, Relationship.MAX_TIES, Set.of());
		assertTrue(RelationshipFormer.plan(List.of(capped,
			snapshot("Mira Dusk", 10, WORLD, 0, Set.of())),
			new Random(1)).isEmpty());

		final RelationshipFormer.Snapshot first =
			snapshot("Edric Dusk", 0, WORLD, 1, Set.of());
		final RelationshipFormer.Snapshot second =
			new RelationshipFormer.Snapshot(UUID.randomUUID(), "Mira Dusk",
				new StoredLocation(WORLD, 10, 64, 0), 1,
				Set.of(first.villagerId()));

		// A tie recorded on either side blocks re-formation for any order
		for (int seed = 0; seed < 20; seed++) {
			assertTrue(RelationshipFormer.plan(
				List.of(second, first), new Random(seed)).isEmpty());
		}
	}
}
