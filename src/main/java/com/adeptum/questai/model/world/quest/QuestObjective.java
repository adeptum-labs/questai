package com.adeptum.questai.model.world.quest;

import lombok.Data;

@Data
public class QuestObjective {
	public enum Type {
		TREASURE, FIND_NPC, KILL, COLLECT
	}

	private Type type;
	private String target;
	private int amount;
}
