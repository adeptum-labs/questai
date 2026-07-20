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

import com.adeptum.questai.model.VillageInfo;
import org.bukkit.Location;

/**
 * On-demand village detection, so a player entering an unknown village need
 * not wait out the periodic sweep. {@link #scan} reads tens of thousands of
 * blocks on the calling thread, hence the two cheap guards in front of it.
 */
public interface VillageScanner {

	/** Whether the location is shallow enough for a village to be above. */
	boolean isNearSurface(Location location);

	/** Whether any villager stands nearby, as a cheap gate before a scan. */
	boolean hasVillagersNearby(Location location);

	/** The full block survey around the location. Expensive. */
	VillageInfo scan(Location location);
}
