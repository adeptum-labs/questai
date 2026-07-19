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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Plans one round of gossip: villagers whose stored locations are close
 * exchange a single first-hand memory as hearsay. Pure logic — the store
 * snapshots its state, applies the returned shares and persists.
 */
public final class GossipSpreader {

	private static final double RANGE_SQUARED = 48.0 * 48.0;
	private static final int MAX_SHARES_PER_RUN = 8;

	/** What one villager knows first-hand, snapshotted under the store lock. */
	public record Snapshot(UUID villagerId, String name, StoredLocation location,
		Map<UUID, List<MemoryEvent>> eventsByPlayer) {
	}

	/** An instruction to store one piece of hearsay on the receiver. */
	public record Share(UUID receiverId, UUID playerId, HearsayEvent hearsay) {
	}

	private GossipSpreader() {
	}

	/**
	 * Pairs up shuffled villagers, walking adjacent pairs so each villager
	 * gossips at most once per round, and emits one share per pair that is
	 * within range. Bounded work: one shuffle plus a linear walk.
	 */
	public static List<Share> plan(final List<Snapshot> snapshots,
		final Random random) {

		final List<Snapshot> shuffled = new ArrayList<>(snapshots);
		Collections.shuffle(shuffled, random);

		final List<Share> shares = new ArrayList<>();
		for (int i = 0; i + 1 < shuffled.size()
			&& shares.size() < MAX_SHARES_PER_RUN; i += 2) {

			final Snapshot first = shuffled.get(i);
			final Snapshot second = shuffled.get(i + 1);
			if (!inRange(first, second)) {
				continue;
			}

			Share share = share(first, second, random);
			if (share == null) {
				share = share(second, first, random);
			}
			if (share != null) {
				shares.add(share);
			}
		}
		return shares;
	}

	private static boolean inRange(final Snapshot first, final Snapshot second) {
		return first.location().worldId().equals(second.location().worldId())
			&& first.location().distanceSquaredXz(
				second.location().x(), second.location().z()) <= RANGE_SQUARED;
	}

	/**
	 * Picks one random first-hand event from the sharer to pass on to the
	 * receiver, or null when the sharer has nothing to tell.
	 */
	private static Share share(final Snapshot sharer, final Snapshot receiver,
		final Random random) {

		final List<Share> possible = new ArrayList<>();
		for (final Map.Entry<UUID, List<MemoryEvent>> entry
			: sharer.eventsByPlayer().entrySet()) {

			for (final MemoryEvent event : entry.getValue()) {
				possible.add(new Share(receiver.villagerId(), entry.getKey(),
					new HearsayEvent(event.type(), event.questTitle(),
						sharer.name(), event.at())));
			}
		}
		return possible.isEmpty() ? null
			: possible.get(random.nextInt(possible.size()));
	}
}
