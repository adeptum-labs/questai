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

package com.adeptum.questai.dialogue;

import com.adeptum.questai.model.world.quest.Quest;

/**
 * Utility class providing prompt templates for NPC dialogue AI generation.
 */
public final class DialoguePrompts {

	private DialoguePrompts() {
	}

	public static String greeting(final String npcName, final String profession,
		final String context) {

		return ("You are %s, a %s in a Minecraft village. A player has approached you. "
			+ "Give a brief, in-character greeting (1-2 sentences). Be warm but match "
			+ "your profession's personality. Output ONLY the greeting text, no quotes.")
				.formatted(npcName, profession) + contextSuffix(context, true);
	}

	public static String questNarrative(final String npcName, final String profession,
		final Quest quest, final String context) {

		final String described = quest.getObjective().describe();
		final String objectiveDesc =
			Character.toLowerCase(described.charAt(0)) + described.substring(1);

		return ("You are %s, a %s in a Minecraft village. You need the player's help to %s. "
			+ "The reward is %d MCMMO XP in %s. Describe this quest conversationally in "
			+ "2-3 sentences as if asking for help. Stay in character. "
			+ "Output ONLY the dialogue text, no quotes.")
				.formatted(npcName, profession, objectiveDesc,
					quest.getRewardAmount(), quest.getRewardTarget())
			+ contextSuffix(context, false);
	}

	public static String casualChat(final String npcName, final String profession,
		final String context) {

		return ("You are %s, a %s in a Minecraft village. The player wants to chat. "
			+ "Share something interesting, funny, or mysterious about your life or "
			+ "the village. 1-2 sentences. Stay in character. "
			+ "Output ONLY the dialogue text, no quotes.")
				.formatted(npcName, profession) + contextSuffix(context, false);
	}

	public static String casualChatWithQuestHint(final String npcName,
		final String profession, final String context) {

		return ("You are %s, a %s in a Minecraft village. The player wants to chat. "
			+ "Share something interesting about your life, but subtly hint that you "
			+ "could use some help with a problem or task. Do NOT describe the task "
			+ "in detail — just hint that something is troubling you or that you need "
			+ "assistance. 1-2 sentences. Stay in character. "
			+ "Output ONLY the dialogue text, no quotes.")
				.formatted(npcName, profession) + contextSuffix(context, false);
	}

	public static String deliveryReaction(final String npcName,
		final String profession, final String giverName, final String context) {

		return ("You are %s, a %s in a Minecraft village. A traveler has just "
			+ "handed you a sealed package sent by %s. React in character to "
			+ "receiving it — surprised, pleased, or curious — in 1-2 sentences. "
			+ "Output ONLY the dialogue text, no quotes.")
				.formatted(npcName, profession, giverName)
			+ contextSuffix(context, false);
	}

	private static String contextSuffix(final String context,
		final boolean acknowledge) {

		if (context == null || context.isBlank()) {
			return "";
		}
		return " Personality and shared history: " + context
			+ (acknowledge
				? " If you have met this player before, acknowledge it naturally."
				: "");
	}
}
