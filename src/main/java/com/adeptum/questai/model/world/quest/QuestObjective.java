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

package com.adeptum.questai.model.world.quest;

import lombok.Data;

@Data
public class QuestObjective {
	public enum Type {
		TREASURE, FIND_NPC, KILL, COLLECT;

		/** True for objectives that place something at a destination. */
		public boolean needsDestination() {
			return this == TREASURE || this == FIND_NPC;
		}

		/** True for objectives completed by accumulating a count. */
		public boolean isCountable() {
			return this == KILL || this == COLLECT;
		}
	}

	private Type type;
	private String target;
	private int amount;

	/** Single source of the human-readable objective phrase. */
	public String describe() {
		return switch (type) {
			case KILL -> "Kill %d %s".formatted(amount, target);
			case COLLECT -> "Collect %d %s".formatted(amount, target);
			case TREASURE -> "Find a hidden treasure chest";
			case FIND_NPC -> "Locate a missing person";
		};
	}
}
