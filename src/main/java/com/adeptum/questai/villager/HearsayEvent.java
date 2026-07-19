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

/**
 * A second-hand memory: something a villager heard happened between the
 * named source villager and a player. As in MemoryEvent, questTitle
 * carries the sender's name for PARCEL_RECEIVED.
 */
public record HearsayEvent(MemoryEvent.Type type, String questTitle,
	String sourceName, long at) {

	/** True when this and other describe the same underlying deed. */
	public boolean sameDeed(final HearsayEvent other) {
		return type == other.type()
			&& questTitle.equals(other.questTitle())
			&& sourceName.equals(other.sourceName());
	}
}
