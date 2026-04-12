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

import java.util.UUID;
import lombok.Data;
import org.bukkit.Location;

@Data
public class Quest {
	private String shortTitle;
	private String title;
	private QuestObjective objective;
	private String rewardType;   // e.g. "MCMMO"
	private String rewardTarget; // e.g. "MINING"
	private int rewardAmount;
	private Location destination;
	private UUID villagerUuid;
	private String narrative;

	public String prompt() {
		final StringBuilder sb = new StringBuilder(320);

		sb.append("You are a creative quest description generator.\n");
		sb.append("Please provide a short title (max 5 words) and a short, engaging quest description ");
		sb.append("(1-3 sentences).\n");
		sb.append("Quest data:\n");

		final String objectiveLine = switch (objective.getType()) {
			case KILL -> "- Objective: Kill %d %s".formatted(objective.getAmount(), objective.getTarget());
			case COLLECT -> "- Objective: Collect %d %s".formatted(objective.getAmount(), objective.getTarget());
			case TREASURE -> "- Objective: Find a hidden chest in the world.";
			case FIND_NPC -> "- Objective: Locate an NPC.";
		};
		sb.append(objectiveLine).append('\n');

		sb.append("- Reward: %d MCMMO XP in %s\n".formatted(rewardAmount, rewardTarget));
		sb.append("\nProvide the short title and description separated by a newline.");

		return sb.toString();
	}
}
