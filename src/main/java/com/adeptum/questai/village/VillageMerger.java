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

import com.adeptum.questai.craft.CommissionStore;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.fortify.WorkState;
import com.adeptum.questai.reputation.VillageReputationStore;
import com.adeptum.questai.teleport.TeleportStoneStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Settles what happens when two names turn out to describe one village.
 *
 * <p>The registry knows only geometry and the stores know only their own
 * ledgers; deciding which name a merged village answers to, and seeing the
 * state carried across, belongs to neither. It lives here.
 *
 * <p>Each registry and store call is atomic but a sweep is not, so this must
 * be run from the main thread. A village claimed part way through one would
 * be counted against the registry's size as though it were a row that had
 * been folded away, and a row could be taken in between being listed and
 * being asked whether it is still standing.
 */
public final class VillageMerger {

	/** What a tier of finished works is worth against the other holdings. */
	private static final int WORKS_WEIGHT = 100;
	/** What one player's standing is worth. */
	private static final int STANDING_WEIGHT = 10;
	/** What the village's teleport stone is worth. */
	private static final int STONE_WEIGHT = 5;

	private final VillageRegistry registry;
	private final VillageReputationStore reputation;
	private final VillageWorksStore works;
	private final TeleportStoneStore stones;
	private final CommissionStore commissions;

	public VillageMerger(final VillageRegistry registry,
		final VillageReputationStore reputation, final VillageWorksStore works,
		final TeleportStoneStore stones, final CommissionStore commissions) {

		this.registry = registry;
		this.reputation = reputation;
		this.works = works;
		this.stones = stones;
		this.commissions = commissions;
	}

	/**
	 * Which of two rows keeps its name. The older claim wins outright.
	 * Rows written before discovery was timestamped have no age to compare,
	 * and an arbitrary winner would make a village's name depend on map
	 * iteration order, so the one players have invested in decides it.
	 */
	public NamedVillage survivorOf(final NamedVillage first,
		final NamedVillage second) {

		return rank(first, second) <= 0 ? first : second;
	}

	/**
	 * Orders two rows so the one that keeps its name comes first. Every term
	 * turns around when the two are handed over the other way about, which is
	 * what makes the choice independent of the order they arrive in, and the
	 * id has the last word so that even two rows alike in every other respect
	 * are separated by something that does not move.
	 */
	private int rank(final NamedVillage first, final NamedVillage second) {
		if (first.discoveredAt() != 0L && second.discoveredAt() != 0L
			&& first.discoveredAt() != second.discoveredAt()) {

			return Long.compare(first.discoveredAt(), second.discoveredAt());
		}
		final int weighed =
			Integer.compare(investmentIn(second), investmentIn(first));
		return weighed == 0 ? first.id().compareTo(second.id()) : weighed;
	}

	/** How much a village has had put into it, for breaking an age tie. */
	private int investmentIn(final NamedVillage village) {
		final WorkState state = works.get(village.id());
		return (state == null ? 0 : state.getTier() * WORKS_WEIGHT)
			+ reputation.playerCount(village.id()) * STANDING_WEIGHT
			+ (stones.issued(village.id()) ? STONE_WEIGHT : 0)
			+ commissions.orderCount(village.id());
	}

	/** Folds the absorbed village's state and id into the survivor's. */
	public void mergeInto(final NamedVillage survivor,
		final NamedVillage absorbed) {

		reputation.merge(absorbed.id(), survivor.id());
		works.merge(absorbed.id(), survivor.id());
		stones.merge(absorbed.id(), survivor.id());
		commissions.merge(absorbed.id(), survivor.id());
		registry.absorb(absorbed.id(), survivor.id());
	}

	/**
	 * Merges every row that has come to overlap this one, and answers with
	 * the village that came out of it.
	 *
	 * <p>The name is settled on before anything is moved. Choosing as the
	 * folding went along would weigh each row against a survivor already
	 * carrying what it had taken in, so whichever row came first would
	 * gather the others' ledgers and win on them.
	 *
	 * <p>The rows are also put in a fixed order first, which matters even
	 * with nothing moving. Age settles some pairs and investment others, and
	 * the two rules need not agree on one line: rows can form a ring where
	 * each outranks the next. Working through a ring in pairs lands wherever
	 * it was entered, and the registry lists overlaps in an order that
	 * distance ties leave to the map.
	 */
	public NamedVillage settle(final NamedVillage village) {
		final List<NamedVillage> candidates =
			new ArrayList<>(registry.overlapping(village));
		candidates.add(village);
		candidates.sort(Comparator.comparing(NamedVillage::id));

		NamedVillage survivor = candidates.get(0);
		for (final NamedVillage candidate : candidates) {
			survivor = survivorOf(survivor, candidate);
		}
		for (final NamedVillage candidate : candidates) {
			if (!candidate.id().equals(survivor.id())) {
				mergeInto(survivor, candidate);
			}
		}
		return survivor;
	}

	/**
	 * Reconciles every known row; answers how many were absorbed.
	 *
	 * <p>The rows are walked from a snapshot, so one taken in earlier in the
	 * sweep still turns up here and has to be passed over: it has no state
	 * left to merge and no name left to defend. Its id would still resolve,
	 * to whichever row took it in, which is why living membership is asked
	 * for directly. What the registry lost over the sweep is then the count,
	 * measured rather than predicted from the overlaps.
	 */
	public int sweep() {
		final int before = registry.size();
		for (final NamedVillage village : registry.all()) {
			if (registry.isLive(village.id())) {
				settle(village);
			}
		}
		return before - registry.size();
	}
}
