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
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generated villager character: unique name, personality traits and one
 * short greeting line. Owns the generation prompt and the tolerant parser
 * for the model's three-line reply.
 */
public record VillagerPersona(String name, List<String> traits, String greeting) {

	private static final int MAX_TRAITS = 3;
	private static final int MAX_GREETING_LENGTH = 80;
	private static final Pattern LINE_LABEL =
		Pattern.compile("(?i)^(name|traits|greeting)\\s*:\\s*");

	public static String prompt(final UUID villagerId, final String profession) {
		return "Create a Minecraft villager character. The villager is a "
			+ profession + " (UUID " + villagerId
			+ ", use it only as inspiration for uniqueness).\n"
			+ "Reply with EXACTLY three lines and no labels or quotes:\n"
			+ "Line 1: first name and surname only\n"
			+ "Line 2: 2-3 short personality traits separated by semicolons\n"
			+ "Line 3: one short greeting line (under 60 characters) the"
			+ " villager would call out to a passing player";
	}

	/**
	 * Parses the model reply leniently: blank lines are skipped, optional
	 * labels and quotes are stripped, and missing lines fall back so a
	 * name-only reply still yields a valid persona.
	 */
	public static VillagerPersona parse(final String response) {
		final List<String> lines = new ArrayList<>();
		for (final String raw : response.split("\n")) {
			final String line = clean(raw);
			if (!line.isEmpty()) {
				lines.add(line);
			}
		}

		final String name = lines.isEmpty() ? "Villager" : lines.get(0);
		final List<String> traits =
			lines.size() > 1 ? parseTraits(lines.get(1)) : List.of();
		return new VillagerPersona(name, traits, parseGreeting(lines));
	}

	private static String parseGreeting(final List<String> lines) {
		if (lines.size() <= 2) {
			return null;
		}
		final String greeting = lines.get(2);
		return greeting.length() > MAX_GREETING_LENGTH
			? greeting.substring(0, MAX_GREETING_LENGTH) : greeting;
	}

	private static String clean(final String raw) {
		String line = LINE_LABEL.matcher(raw.trim()).replaceFirst("");
		if (line.length() >= 2 && line.startsWith("\"") && line.endsWith("\"")) {
			line = line.substring(1, line.length() - 1).trim();
		}
		return line;
	}

	private static List<String> parseTraits(final String line) {
		final String separator = line.contains(";") ? ";" : ",";
		final List<String> traits = new ArrayList<>();
		for (final String part : line.split(Pattern.quote(separator))) {
			final String trait = part.trim();
			if (!trait.isEmpty() && traits.size() < MAX_TRAITS) {
				traits.add(trait);
			}
		}
		return traits;
	}
}
