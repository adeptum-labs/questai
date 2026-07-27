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

import com.adeptum.questai.event.VillageKey;
import com.adeptum.questai.villager.StoredLocation;

/**
 * A village that has been discovered and named. The id is what every store
 * files its state under and never changes; the centre is what presence is
 * measured against and is free to move as the village is better surveyed.
 * The key identifies the cell the village was found in.
 *
 * <p>Rows written before discovery was stamped carry a zero, which reads as
 * an age nobody recorded rather than as a village found at the epoch.
 */
public record NamedVillage(String id, VillageKey key, StoredLocation centre,
	String name, long discoveredAt) {
}
