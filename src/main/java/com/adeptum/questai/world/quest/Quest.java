package com.adeptum.questai.world.quest;

import java.util.UUID;
import lombok.Data;
import org.bukkit.Location;

@Data
public class Quest {
	private String shortTitle;
	private String title;
	private String category; // "TREASURE", "FIND_NPC", "KILL", "COLLECT"
	private QuestObjective objective;
	private String rewardType;   // e.g. "MCMMO"
	private String rewardTarget; // e.g. "MINING"
	private int rewardAmount;
	private Location destination;
	private UUID villagerUuid;
}
