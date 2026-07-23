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

/**
 * Builds the compact personality-and-history context that is appended to
 * dialogue prompts. Bounded in both event count and total length so prompt
 * token usage stays flat.
 */
public final class MemorySummarizer {

	private static final int MAX_EVENT_CLAUSES = 4;
	private static final int MAX_HEARSAY_CLAUSES = 2;
	private static final int MAX_TIE_CLAUSES = 2;
	private static final int MAX_LENGTH = 400;

	private MemorySummarizer() {
	}

	/**
	 * Returns a short prompt fragment describing the villager's personality
	 * and what it remembers about the given player, or an empty string when
	 * there is nothing to tell.
	 */
	public static String context(final VillagerProfile profile, final UUID playerId) {
		if (profile == null) {
			return "";
		}

		final List<String> parts = new ArrayList<>();
		if (!profile.getTraits().isEmpty()) {
			parts.add("Your personality: "
				+ String.join("; ", profile.getTraits()) + ".");
		}
		addTieParts(parts, profile);

		final PlayerMemory memory = profile.getPlayers().get(playerId);
		addRelationshipParts(parts, memory);

		final int baseParts = parts.size();
		parts.addAll(eventClauses(memory));
		// Hearsay goes last so the length cap drops it before first-hand
		// memories
		parts.addAll(hearsayClauses(memory));
		return capLength(parts, baseParts);
	}

	/**
	 * Enforces the length cap by dropping the oldest event clauses first.
	 */
	private static String capLength(final List<String> parts, final int baseParts) {
		while (parts.size() > baseParts
			&& String.join(" ", parts).length() > MAX_LENGTH) {
			parts.remove(parts.size() - 1);
		}
		return String.join(" ", parts);
	}

	/**
	 * Ties to other villagers are identity like traits, so they sit ahead
	 * of the length cap's reach.
	 */
	private static void addTieParts(final List<String> parts,
		final VillagerProfile profile) {

		int added = 0;
		for (final Relationship tie : profile.getRelationships()) {
			if (added >= MAX_TIE_CLAUSES) {
				return;
			}
			parts.add(tie.otherName() + " is your " + tie.type().noun() + ".");
			added++;
		}
	}

	private static void addRelationshipParts(final List<String> parts,
		final PlayerMemory memory) {

		if (memory == null) {
			return;
		}
		if (memory.getConversations() > 0) {
			parts.add("You have talked with this player "
				+ memory.getConversations() + " time(s) before.");
		}
		if (memory.getChain() != null) {
			parts.add("You have unfinished business with this player: after '"
				+ memory.getChain().lastTitle()
				+ "' you promised them more work.");
		}
	}

	private static List<String> eventClauses(final PlayerMemory memory) {
		if (memory == null) {
			return List.of();
		}
		final List<String> clauses = new ArrayList<>();
		final List<MemoryEvent> events = memory.getEvents();
		for (int i = events.size() - 1;
			i >= 0 && clauses.size() < MAX_EVENT_CLAUSES; i--) {
			clauses.add(describe(events.get(i)));
		}
		return clauses;
	}

	private static List<String> hearsayClauses(final PlayerMemory memory) {
		if (memory == null) {
			return List.of();
		}
		final List<String> clauses = new ArrayList<>();
		final List<HearsayEvent> hearsay = memory.getHearsay();
		for (int i = hearsay.size() - 1;
			i >= 0 && clauses.size() < MAX_HEARSAY_CLAUSES; i--) {
			clauses.add(describeHearsay(hearsay.get(i)));
		}
		return clauses;
	}

	// Grows one exhaustive case per memory type by design
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	private static String describeHearsay(final HearsayEvent event) {
		return "Word in the village is that they " + switch (event.type()) {
			case QUEST_COMPLETED -> "completed '" + event.questTitle()
				+ "' for " + event.sourceName() + ".";
			case QUEST_REJECTED ->
				"turned down " + event.sourceName() + "'s request.";
			case QUEST_ACCEPTED -> "agreed to help " + event.sourceName() + ".";
			case PARCEL_RECEIVED ->
				"delivered a parcel for " + event.sourceName() + ".";
			case DEFENDED_VILLAGE ->
				"drove off " + event.questTitle() + " alongside "
					+ event.sourceName() + ".";
			case CLAIMED_STAR -> "sold " + event.sourceName() + " "
				+ event.questTitle() + " from the night sky.";
			case RAISED_WORKS -> "helped raise " + event.questTitle()
				+ " with " + event.sourceName() + ".";
		};
	}

	// Grows one exhaustive case per memory type by design
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	private static String describe(final MemoryEvent event) {
		return switch (event.type()) {
			case QUEST_COMPLETED ->
				"They completed your quest '" + event.questTitle() + "'.";
			case QUEST_REJECTED ->
				"They refused your quest '" + event.questTitle() + "'.";
			case QUEST_ACCEPTED ->
				"They agreed to help with '" + event.questTitle() + "'.";
			case PARCEL_RECEIVED ->
				"They once delivered a parcel to you from "
					+ event.questTitle() + ".";
			case DEFENDED_VILLAGE ->
				"They drove off " + event.questTitle()
					+ " when the village was attacked.";
			case CLAIMED_STAR -> "They once sold you " + event.questTitle()
				+ " that fell from the night sky.";
			case RAISED_WORKS -> "They helped your village raise "
				+ event.questTitle() + ".";
		};
	}
}
