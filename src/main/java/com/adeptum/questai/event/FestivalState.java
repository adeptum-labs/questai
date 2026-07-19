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
import java.util.UUID;

/**
 * One ongoing festival around a village center, granting its bonus to
 * quests completed within the event radius while it lasts.
 */
public record FestivalState(VillageKey key, StoredLocation center, long endsAt) {

	public boolean active(final long now) {
		return now < endsAt;
	}

	public boolean covers(final UUID worldId, final double x, final double z) {
		return center.worldId().equals(worldId)
			&& center.distanceSquaredXz(x, z)
				<= VillageEvents.EVENT_RADIUS * VillageEvents.EVENT_RADIUS;
	}
}
