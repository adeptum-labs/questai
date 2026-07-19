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
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Plans new villager ties when villagers mingle: shared surnames make kin
 * outright, other nearby pairs occasionally bond as old friends or
 * rivals. Pure logic — the store snapshots, applies and persists.
 */
public final class RelationshipFormer {

	private static final double RANGE_SQUARED = 48.0 * 48.0;
	private static final int FORM_CHANCE_IN = 4;
	private static final int FRIEND_WEIGHT = 7;
	private static final int WEIGHT_RANGE = 10;

	/** What one villager's social side looks like, snapshotted. */
	public record Snapshot(UUID villagerId, String name, StoredLocation location,
		int tieCount, Set<UUID> tiedTo) {
	}

	/** An instruction to tie two villagers together. */
	public record Tie(UUID firstId, UUID secondId, Relationship.Type type) {
	}

	private RelationshipFormer() {
	}

	/**
	 * Pairs up shuffled villagers, walking adjacent pairs so each villager
	 * is considered at most once per round, and emits at most one new tie
	 * per eligible pair.
	 */
	public static List<Tie> plan(final List<Snapshot> snapshots,
		final Random random) {

		final List<Snapshot> shuffled = new ArrayList<>(snapshots);
		Collections.shuffle(shuffled, random);

		final List<Tie> ties = new ArrayList<>();
		for (int i = 0; i + 1 < shuffled.size(); i += 2) {
			final Snapshot first = shuffled.get(i);
			final Snapshot second = shuffled.get(i + 1);
			if (eligible(first, second)) {
				final Relationship.Type type = tieType(first, second, random);
				if (type != null) {
					ties.add(new Tie(
						first.villagerId(), second.villagerId(), type));
				}
			}
		}
		return ties;
	}

	private static boolean eligible(final Snapshot first, final Snapshot second) {
		return first.location().worldId().equals(second.location().worldId())
			&& first.location().distanceSquaredXz(
				second.location().x(), second.location().z()) <= RANGE_SQUARED
			&& first.tieCount() < Relationship.MAX_TIES
			&& second.tieCount() < Relationship.MAX_TIES
			&& !first.tiedTo().contains(second.villagerId())
			&& !second.tiedTo().contains(first.villagerId());
	}

	/**
	 * Kinship is a fact of names and needs no luck; other ties form on a
	 * rare roll, mostly friendly.
	 */
	private static Relationship.Type tieType(final Snapshot first,
		final Snapshot second, final Random random) {

		final String surname = Relationship.surname(first.name());
		if (surname != null
			&& surname.equalsIgnoreCase(Relationship.surname(second.name()))) {
			return Relationship.Type.KIN;
		}
		if (random.nextInt(FORM_CHANCE_IN) != 0) {
			return null;
		}
		return random.nextInt(WEIGHT_RANGE) < FRIEND_WEIGHT
			? Relationship.Type.OLD_FRIEND : Relationship.Type.RIVAL;
	}
}
