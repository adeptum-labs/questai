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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/**
 * Tracks one ongoing raid: which raiders still live and whether anything
 * happened that forfeits a defended outcome. A raid is only DEFENDED when
 * every raider was genuinely killed and no watched villager died.
 */
public final class RaidState {

	public enum Outcome {
		ONGOING, DEFENDED, FAILED
	}

	@Getter
	private final VillageKey key;
	@Getter
	private final StoredLocation center;
	@Getter
	private final long deadline;
	@Getter
	private final int raiderCount;

	private final Set<UUID> raidersAlive = new HashSet<>();
	private boolean raiderEscaped;
	private int villagersLost;

	public RaidState(final VillageKey key, final StoredLocation center,
		final long deadline, final Set<UUID> raiderIds) {

		this.key = key;
		this.center = center;
		this.deadline = deadline;
		this.raiderCount = raiderIds.size();
		this.raidersAlive.addAll(raiderIds);
	}

	/**
	 * Marks a raider as slain.
	 *
	 * @return true when the entity was one of this raid's raiders
	 */
	public boolean onRaiderDeath(final UUID raiderId) {
		return raidersAlive.remove(raiderId);
	}

	/** Marks a raider as gone without being killed (despawn, unload). */
	public void onRaiderVanished(final UUID raiderId) {
		if (raidersAlive.remove(raiderId)) {
			raiderEscaped = true;
		}
	}

	/** Marks one of the watched villagers as lost to the raid. */
	public void onVillagerLost() {
		villagersLost++;
	}

	/** How many villagers this raid has cost so far. */
	public int getVillagersLost() {
		return villagersLost;
	}

	public Set<UUID> aliveRaiders() {
		return Set.copyOf(raidersAlive);
	}

	public Outcome check(final long now) {
		if (raidersAlive.isEmpty()) {
			return raiderEscaped || villagersLost > 0
				? Outcome.FAILED : Outcome.DEFENDED;
		}
		return now > deadline ? Outcome.FAILED : Outcome.ONGOING;
	}
}
