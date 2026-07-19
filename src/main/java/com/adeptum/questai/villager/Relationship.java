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

import java.util.UUID;

/**
 * A persistent tie from one named villager to another. The other
 * villager's name is denormalized so pure code can phrase the tie without
 * store lookups; names never change and profiles are never deleted.
 */
public record Relationship(Type type, UUID otherId, String otherName) {

	/** Most ties a single villager can hold. */
	public static final int MAX_TIES = 2;

	public enum Type {
		KIN, OLD_FRIEND, RIVAL;

		/** How the tie is phrased, e.g. "your kin Mira Dusk". */
		public String noun() {
			return switch (this) {
				case KIN -> "kin";
				case OLD_FRIEND -> "old friend";
				case RIVAL -> "rival";
			};
		}
	}

	/**
	 * The surname part of a villager name, with the numeric uniqueness
	 * suffix stripped, or null for single-token names.
	 */
	public static String surname(final String name) {
		final String base = name.replaceFirst("_\\d+$", "");
		final int lastSpace = base.lastIndexOf(' ');
		return lastSpace < 0 ? null : base.substring(lastSpace + 1);
	}
}
