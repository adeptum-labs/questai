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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GossipSpreaderTest {

	private static final UUID WORLD = UUID.randomUUID();
	private static final UUID PLAYER = UUID.randomUUID();

	private static GossipSpreader.Snapshot snapshot(final String name,
		final double x, final double z, final UUID worldId,
		final MemoryEvent... events) {

		final Map<UUID, List<MemoryEvent>> byPlayer = events.length == 0
			? Map.of() : Map.of(PLAYER, List.of(events));
		return new GossipSpreader.Snapshot(UUID.randomUUID(), name,
			new StoredLocation(worldId, x, 64, z), byPlayer);
	}

	private static MemoryEvent completed(final String title) {
		return new MemoryEvent(MemoryEvent.Type.QUEST_COMPLETED, title, 1000L);
	}

	@Test
	void villagersInRangeExchangeOneMirroredEvent() {
		final GossipSpreader.Snapshot teller = snapshot(
			"Edric Stone", 0, 0, WORLD, completed("Turnip Trouble"));
		final GossipSpreader.Snapshot listener = snapshot(
			"Goran Dusk", 10, 0, WORLD);

		final List<GossipSpreader.Share> shares = GossipSpreader.plan(
			List.of(teller, listener), new Random(42));

		assertEquals(1, shares.size());
		final GossipSpreader.Share share = shares.get(0);
		assertEquals(listener.villagerId(), share.receiverId());
		assertEquals(PLAYER, share.playerId());
		assertEquals("Edric Stone", share.hearsay().sourceName());
		assertEquals("Turnip Trouble", share.hearsay().questTitle());
		assertEquals(MemoryEvent.Type.QUEST_COMPLETED, share.hearsay().type());
	}

	@Test
	void outOfRangeVillagersDoNotShare() {
		final List<GossipSpreader.Share> shares = GossipSpreader.plan(List.of(
			snapshot("Edric Stone", 0, 0, WORLD, completed("Quest")),
			snapshot("Goran Dusk", 100, 0, WORLD)), new Random(42));

		assertTrue(shares.isEmpty());
	}

	@Test
	void differentWorldsDoNotShare() {
		final List<GossipSpreader.Share> shares = GossipSpreader.plan(List.of(
			snapshot("Edric Stone", 0, 0, WORLD, completed("Quest")),
			snapshot("Goran Dusk", 0, 0, UUID.randomUUID())), new Random(42));

		assertTrue(shares.isEmpty());
	}

	@Test
	void eventlessPairYieldsNothing() {
		final List<GossipSpreader.Share> shares = GossipSpreader.plan(List.of(
			snapshot("Edric Stone", 0, 0, WORLD),
			snapshot("Goran Dusk", 10, 0, WORLD)), new Random(42));

		assertTrue(shares.isEmpty());
	}

	@Test
	void eventlessSharerFallsBackToTheOtherDirection() {
		final GossipSpreader.Snapshot silent = snapshot(
			"Goran Dusk", 10, 0, WORLD);
		final GossipSpreader.Snapshot teller = snapshot(
			"Edric Stone", 0, 0, WORLD, completed("Turnip Trouble"));

		// Regardless of shuffle order, the villager with events tells
		for (int seed = 0; seed < 10; seed++) {
			final List<GossipSpreader.Share> shares = GossipSpreader.plan(
				List.of(silent, teller), new Random(seed));
			assertEquals(1, shares.size());
			assertEquals(silent.villagerId(), shares.get(0).receiverId());
		}
	}

	@Test
	void sharesAreCappedPerRunAndReceiversAreUnique() {
		final List<GossipSpreader.Snapshot> crowd =
			new java.util.ArrayList<>();
		for (int i = 0; i < 40; i++) {
			crowd.add(snapshot("Villager " + i, i, 0, WORLD,
				completed("Quest " + i)));
		}

		final List<GossipSpreader.Share> shares =
			GossipSpreader.plan(crowd, new Random(42));

		assertFalse(shares.isEmpty());
		assertTrue(shares.size() <= 8);
		final Set<UUID> receivers = new HashSet<>();
		for (final GossipSpreader.Share share : shares) {
			assertTrue(receivers.add(share.receiverId()),
				"Receiver gossiped twice in one run");
		}
	}
}
