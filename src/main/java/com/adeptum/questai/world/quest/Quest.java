package com.adeptum.questai.world.quest;

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

	public String prompt() {
		final StringBuilder sb = new StringBuilder();

		sb.append("You are a creative quest description generator.\n");
		sb.append("Please provide a short title (max 5 words) and a short, engaging quest description (1-3 sentences).\n");
		sb.append("Quest data:\n");

		sb.append(switch(objective.getType()) {
			case KILL -> "- Objective: Kill %d %s".formatted(objective.getAmount(), objective.getTarget());
			case COLLECT -> "- Objective: Collect %d %s".formatted(objective.getAmount(), objective.getTarget());
			case TREASURE -> "- Objective: Find a hidden chest in the world.";
			case FIND_NPC -> "Objective: Locate an NPC.";
		}).append("\n");

		sb.append("- Reward: %d MCMMO XP in %s\n".formatted(rewardAmount, rewardTarget));
		sb.append("\nProvide the short title and description separated by a newline.");

		return sb.toString();
	}
}
