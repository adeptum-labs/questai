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
import java.util.regex.Pattern;

/**
 * The prompt behind a village's name and the tolerant parsing of the reply.
 * Free of Bukkit types so it stays testable; the nameplate calls the model.
 */
public final class VillageNames {

	/** Long names overflow the boss bar, so the reply is clamped. */
	public static final int MAX_LENGTH = 24;

	private static final Pattern LABEL =
		Pattern.compile("(?i)^(name|village|village name)\\s*:\\s*");

	private static final String[] HEADS = {
		"Raven", "Stone", "Elder", "Amber", "Frost", "Thorn", "Ash", "Grey",
		"Oak", "Iron", "Dusk", "Willow", "Bram", "Hazel", "Wolf", "Fern"
	};

	private static final String[] TAILS = {
		"hollow", "mere", "ford", "wick", "stead", "vale", "barrow", "crest",
		"reach", "fell", "march", "glen", "haven", "moor", "gate", "combe"
	};

	private VillageNames() {
	}

	/** Asks for a single settlement name suited to its surroundings. */
	public static String prompt(final String biome, final int doorCount,
		final int villagerCount) {

		return ("Name a small Minecraft village. It sits in a %s biome, has "
			+ "about %d houses and %d villagers. Give one short, evocative "
			+ "settlement name in the style of an old English hamlet. "
			+ "No more than two words and no more than %d characters. "
			+ "Output ONLY the name, no quotes and no explanation.")
			.formatted(biome, doorCount, villagerCount, MAX_LENGTH);
	}

	/**
	 * The usable name in a model reply, or null when nothing survives.
	 * Chatty replies put the name on the first non-empty line, sometimes
	 * behind a label and inside quotes, so all three are stripped.
	 */
	public static String sanitise(final String raw) {
		if (raw == null) {
			return null;
		}
		for (final String line : raw.split("\n")) {
			final String name = clean(line);
			if (!name.isEmpty()) {
				return name.length() > MAX_LENGTH
					? name.substring(0, MAX_LENGTH).trim() : name;
			}
		}
		return null;
	}

	/**
	 * A name derived from the cell alone. Used when the model is unreachable
	 * so the village still gets named once instead of being retried on every
	 * visit, and so the same cell always yields the same name.
	 */
	public static String fallbackName(final VillageKey key) {
		final int hash = Math.abs(key.hashCode());
		return HEADS[hash % HEADS.length]
			+ TAILS[hash / HEADS.length % TAILS.length];
	}

	private static String clean(final String raw) {
		String line = LABEL.matcher(raw.trim()).replaceFirst("");
		while (line.length() >= 2 && line.startsWith("\"") && line.endsWith("\"")) {
			line = line.substring(1, line.length() - 1).trim();
		}
		// A trailing period reads as a sentence fragment on the boss bar
		while (line.endsWith(".")) {
			line = line.substring(0, line.length() - 1).trim();
		}
		return line;
	}
}
